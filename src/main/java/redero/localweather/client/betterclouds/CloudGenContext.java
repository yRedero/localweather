package redero.localweather.client.betterclouds;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.qendolin.betterclouds.config.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.AABB;
import redero.localweather.client.ClientWeather;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.weather.LocalWeatherApi;
import redero.localweather.weather.StormCell;
import redero.localweather.weather.WeatherView;

/**
 * Everything one Better Clouds generator task needs, frozen when the task is queued: storm cells, settings
 * and the camera. The task runs on a worker thread, so nothing here may read live game state.
 * <p>
 * Coordinates passed in are Better Clouds' cloud space: world position minus the drifting cloud origin.
 * Heights are relative to the dimension's cloud height.
 */
public final class CloudGenContext implements WeatherView {
    /** Better Clouds' own weather term is 0.5 + 0.3 * weather; storms push coverage further than that. */
    private static final float STORM_GAIN = 0.5F;
    /** Extra clouds (towers, thick decks, high layer) may use at most this share of Better Clouds' buffer. */
    private static final float EXTRA_SHARE = 0.6F;
    private static final SimplexNoise HIGH_NOISE = new SimplexNoise(new LegacyRandomSource(0x4C57L));

    private final List<StormCell> cells;
    private final int aheadTicks;
    private final boolean hasWeather;
    private final double originX, originZ;
    private final double cameraX, cameraZ;
    /** Whatever Better Clouds multiplies on top of weather (season compat), recovered from its global value. */
    private final float otherFactors;
    private final float spacing, sizeXZ, sizeY, yOffset;
    private final int blockDistance, detailDistance;
    private final boolean encode, lod, towers, darkness, thick, high;
    private final float highHeight;
    private final int extraBudget;

    // Per-sample state. A task samples one point at a time on a single thread.
    private int step;
    private boolean highOnly;
    private boolean towerGrid;
    private float highStrength;
    private int pointsThisSample;
    private final List<CloudCode> pending = new ArrayList<>(8);
    private int extras;
    /** Dev scene only: per detail step (index 1, 2, 4), samples kept, clouds made, dark clouds. */
    private final int[][] stats = new int[5][3];
    private int towerPieces;

    private CloudGenContext(List<StormCell> cells, int aheadTicks, boolean hasWeather, double originX, double originZ,
                            double cameraX, double cameraZ, float otherFactors, Config options) {
        this.cells = cells;
        this.aheadTicks = aheadTicks;
        this.hasWeather = hasWeather;
        this.originX = originX;
        this.originZ = originZ;
        this.cameraX = cameraX - originX;
        this.cameraZ = cameraZ - originZ;
        this.otherFactors = otherFactors;
        this.spacing = options.spacing;
        this.sizeXZ = options.sizeXZ;
        this.sizeY = options.sizeY;
        this.yOffset = options.yOffset;
        this.blockDistance = options.blockDistance();

        // Without the shader patch, sizes and darkness can't be drawn: keep Better Clouds' plain look.
        this.encode = BetterCloudsShaderPatch.active();
        this.lod = encode && LocalWeatherClientConfig.FAR_CLOUDS.get();
        this.detailDistance = LocalWeatherClientConfig.DETAIL_DISTANCE.get();
        this.towers = LocalWeatherClientConfig.STORM_TOWERS.get();
        this.darkness = encode && LocalWeatherClientConfig.STORM_DARKNESS.get();
        this.thick = LocalWeatherClientConfig.THICK_RAIN_CLOUDS.get();
        this.high = encode && LocalWeatherClientConfig.HIGH_LAYER.get();
        this.highHeight = LocalWeatherClientConfig.HIGH_LAYER_HEIGHT.get();

        // Same formula as Better Clouds' ChunkedGenerator#calcBufferSize: the buffer holds size * size clouds.
        int size = Mth.floor(blockDistance / spacing) + Mth.ceil(blockDistance / spacing);
        size = (size > 0 ? size : 128) * (options.bottomSparsity > 0.0F ? 2 : 1);
        this.extraBudget = (int) ((long) size * size * EXTRA_SHARE);
    }

    /** Null when the client config isn't loaded yet; Better Clouds then generates as usual. */
    @Nullable
    public static CloudGenContext capture(double originX, double originZ, Config options, float globalCloudiness) {
        if (!LocalWeatherClientConfig.SPEC.isLoaded()) return null;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity camera = mc.getCameraEntity();
        if (level == null || camera == null) return null;

        ClientWeather weather = ClientWeather.INSTANCE;
        boolean hasWeather = weather.active() && LocalWeatherApi.managed(level);
        List<StormCell> inView = hasWeather
                ? new ArrayList<>(BetterCloudsCompat.cellsInView(camera.getX(), camera.getZ(), options.blockDistance()))
                : List.of();
        // Same weather term as Better Clouds' CloudinessProvider, at the camera.
        float cameraWeather = Math.max(0.6F * level.getRainLevel(1.0F), level.getThunderLevel(1.0F));
        float otherFactors = globalCloudiness / (0.3F * cameraWeather + 0.5F);
        return new CloudGenContext(inView, weather.aheadTicks(), hasWeather, originX, originZ,
                camera.getX(), camera.getZ(), otherFactors, options);
    }

    /** Wraps Better Clouds' sampler call: picks the level of detail and the local cloudiness for one point. */
    public float sample(int x, int z, float cloudiness, Sampler sampler) {
        pending.clear();
        pointsThisSample = 0;
        highOnly = false;
        highStrength = 0.0F;
        step = lodStep(x, z);
        if (step == 0) return 0.0F;

        if (high && onGrid(x, z, Math.max(2, step))) highStrength = highLayerAt(x, z);
        // Towers on every 2nd kept point are enough to read as one solid column.
        towerGrid = onGrid(x, z, step * 2);
        boolean kept = onGrid(x, z, step);
        if (kept) stats[step][0]++;
        float value = kept ? sampler.sample(hasWeather ? cloudinessAt(x, z) : cloudiness) : 0.0F;
        if (value > 0.0F) stats[step][1]++;
        if (value <= 0.0F && highStrength > 0.0F) {
            // A high cloud with nothing below it: let Better Clouds emit one point, which point() moves up.
            highOnly = true;
            return 0.05F;
        }
        return value;
    }

    /** Replaces the point Better Clouds is about to add, and queues the clouds stacked on it. */
    public CloudCode point(double x, double y, double z) {
        pointsThisSample++;
        int size = step;
        if (highOnly) return highCloud(x, z, size);

        double wx = x + originX;
        double wz = z + originZ;
        float rain = hasWeather ? rainAt(wx, wz) : 0.0F;
        float thunder = hasWeather ? thunderAt(wx, wz) : 0.0F;
        float dark = darkness ? Mth.clamp(Math.max(0.85F * thunder, 0.6F * rain), 0.0F, 1.0F) : 0.0F;
        if (dark > 0.1F) stats[step][2]++;
        // Rain decks hang lower than fair-weather clouds.
        if (thick) y -= 25.0 * rain;
        // Better Clouds' second, lower puff for very dense samples: no extras on top of it.
        if (pointsThisSample > 1) return new CloudCode(x, y, z, y, y, code(size, dark, false));

        float unit = sizeY * size;
        if (thick && rain > 0.25F) {
            queue(x, y - unit * 0.7, z, size, dark, false);
            if (rain > 0.55F) queue(x, y + unit * 0.8, z, size, dark * 0.8F, false);
        }
        if (towers && thunder > 0.3F && towerGrid) {
            int towerSize = Math.max(size, 3);
            float towerUnit = sizeY * towerSize;
            int levels = 3 + Math.round(thunder * 9.0F);
            for (int i = 1; i <= levels; i++) {
                queue(x, y + i * towerUnit, z, towerSize, dark * (1.0F - 0.6F * i / levels), false);
            }
            // Flat anvil spreading out from the top of the tower.
            double anvilY = y + (levels + 0.6) * towerUnit;
            double spread = sizeXZ * 2.5;
            queue(x, anvilY, z, 4, dark * 0.3F, true);
            queue(x + spread, anvilY, z, 4, dark * 0.25F, true);
            queue(x - spread, anvilY, z, 4, dark * 0.25F, true);
            queue(x, anvilY, z + spread, 4, dark * 0.25F, true);
            queue(x, anvilY, z - spread, 4, dark * 0.25F, true);
            towerPieces += levels + 5;
        }
        if (highStrength > 0.0F) pending.add(highCloud(x, z, size));

        double minY = y, maxY = y;
        for (CloudCode extra : pending) {
            minY = Math.min(minY, extra.renderY);
            maxY = Math.max(maxY, extra.renderY);
        }
        return new CloudCode(x, y, z, minY, maxY, code(size, dark, false));
    }

    /** Called after Better Clouds added a point; adds the queued stacked clouds while the budget lasts. */
    public void flushExtras(Consumer<CloudCode> add) {
        for (CloudCode extra : pending) {
            if (extras >= extraBudget) break;
            add.accept(extra);
            extras++;
        }
        pending.clear();
    }

    public String stats() {
        return String.format("cells %d, distance %d, detail %d | step1 kept %d clouds %d dark %d | step2 kept %d clouds %d dark %d"
                        + " | step4 kept %d clouds %d dark %d | tower pieces %d, extras added %d of %d",
                cells.size(), blockDistance, detailDistance, stats[1][0], stats[1][1], stats[1][2],
                stats[2][0], stats[2][1], stats[2][2], stats[4][0], stats[4][1], stats[4][2], towerPieces, extras, extraBudget);
    }

    /** The y Better Clouds uploads for a point. */
    public static float uploadY(AABB point) {
        return point instanceof CloudCode cloud ? cloud.encodedY() : (float) point.minY;
    }

    private CloudCode highCloud(double x, double z, int size) {
        double wx = x + originX;
        double wz = z + originZ;
        float weather = hasWeather ? Math.max(0.6F * rainAt(wx, wz), thunderAt(wx, wz)) : 0.0F;
        double y = yOffset + highHeight + 20.0 * HIGH_NOISE.getValue(x / 900.0, z / 900.0);
        return new CloudCode(x, y, z, y, y, code(Math.max(size, 2), 0.4F * weather, true));
    }

    private void queue(double x, double y, double z, int size, float dark, boolean flat) {
        pending.add(new CloudCode(x, y, z, y, y, code(size, dark, flat)));
    }

    private int code(int size, float dark, boolean flat) {
        return encode ? CloudCode.encode(size, dark, flat) : 0;
    }

    /** 1 near the camera, 2 and 4 further out (keep every 2nd/4th point, drawn bigger), 0 past the cloud distance. */
    private int lodStep(int x, int z) {
        if (!lod) return 1;
        double d = Math.sqrt(Mth.square(x - cameraX) + Mth.square(z - cameraZ));
        if (d > blockDistance) return 0;
        if (d > 2.0 * detailDistance) return 4;
        if (d > detailDistance) return 2;
        return 1;
    }

    /** Better Clouds samples at floor(grid * spacing); recover the grid index to thin out points evenly. */
    private boolean onGrid(int x, int z, int step) {
        if (step <= 1) return true;
        int gx = (int) Math.ceil(x / spacing);
        int gz = (int) Math.ceil(z / spacing);
        return Math.floorMod(gx, step) == 0 && Math.floorMod(gz, step) == 0;
    }

    /** High, flat clouds: patchy in clear weather, a near-solid sheet around storms. */
    private float highLayerAt(int x, int z) {
        double n = 0.65 * HIGH_NOISE.getValue(x / 1600.0, z / 1600.0) + 0.35 * HIGH_NOISE.getValue(x / 420.0 + 100.0, z / 420.0);
        float weather = 0.0F;
        if (hasWeather) {
            double wx = x + originX;
            double wz = z + originZ;
            weather = Math.max(0.6F * rainAt(wx, wz), thunderAt(wx, wz));
        }
        double threshold = 0.45 - 0.5 * weather;
        return n > threshold ? (float) Math.min(1.0, (n - threshold) * 3.0) : 0.0F;
    }

    /** Cloudiness for one sample: Better Clouds' clear-sky value, raised by the storms over that point. */
    private float cloudinessAt(int x, int z) {
        double wx = x + originX;
        double wz = z + originZ;
        float weather = Math.max(0.6F * rainAt(wx, wz), thunderAt(wx, wz));
        // Never zero: Better Clouds divides by it.
        return Mth.clamp(otherFactors * (0.5F + STORM_GAIN * weather), 0.01F, 1.0F);
    }

    @Override
    public List<StormCell> cells() {
        return cells;
    }

    @Override
    public int aheadTicks() {
        return aheadTicks;
    }

    /** Better Clouds' sampler with everything but the cloudiness already bound. */
    @FunctionalInterface
    public interface Sampler {
        float sample(float cloudiness);
    }
}
