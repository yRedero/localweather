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
 * <p>
 * Storm clouds are built from ordinary cloud puffs, each nudged sideways so no two share a face: a column of
 * puffs for a thunderstorm tower, a flat sheet of puffs for its anvil, one extra puff for a rain deck.
 */
public final class CloudGenContext implements WeatherView {
    /** Extra clouds (towers, decks, high layer) may use at most this share of Better Clouds' buffer. */
    private static final float EXTRA_SHARE = 0.6F;
    /** Tallest tower above the cloud deck, in blocks. */
    private static final float TOWER_HEIGHT = 240.0F;
    /** Anvil altitude above the cloud base, in blocks. */
    private static final float ANVIL_HEIGHT = 250.0F;
    /** Storm reach used to skip storm maths for samples far from every cell (overcast plus outline noise). */
    private static final double CELL_REACH = 1.7;
    /** Width of the dithered band between detail levels, in blocks. */
    private static final double LOD_BLEND = 300.0;

    private static final SimplexNoise HIGH_NOISE = new SimplexNoise(new LegacyRandomSource(0x4C57L));
    private static final SimplexNoise STORM_NOISE = new SimplexNoise(new LegacyRandomSource(0x5704L));

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
    private final float cloudCover;
    private final int extraBudget;

    // Per-sample state. A task samples one point at a time on a single thread.
    private int sampleX, sampleZ;
    private int step;
    private boolean highOnly;
    private boolean towerGrid;
    private float highStrength;
    private float rain, thunder, cover, towerShape;
    private int pointsThisSample;
    private final List<CloudCode> pending = new ArrayList<>(24);
    private int extras;
    /** Dev scene only: per detail step (index 1, 2, 4), samples kept, clouds made, dark clouds. */
    private final int[][] stats = new int[5][3];
    private int towerPieces;

    private CloudGenContext(List<StormCell> cells, int aheadTicks, boolean hasWeather, double originX, double originZ,
                            double cameraX, double cameraZ, float otherFactors, Config options) {
        this.cells = cells;
        this.aheadTicks = aheadTicks;
        this.hasWeather = hasWeather && !cells.isEmpty();
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
        this.towers = encode && LocalWeatherClientConfig.STORM_TOWERS.get();
        this.darkness = encode && LocalWeatherClientConfig.STORM_DARKNESS.get();
        this.thick = LocalWeatherClientConfig.THICK_RAIN_CLOUDS.get();
        this.high = encode && LocalWeatherClientConfig.HIGH_LAYER.get();
        this.highHeight = LocalWeatherClientConfig.HIGH_LAYER_HEIGHT.get();
        this.cloudCover = LocalWeatherClientConfig.CLOUD_COVER.get().floatValue();

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
        sampleX = x;
        sampleZ = z;
        step = lodStep(x, z);
        if (step == 0) return 0.0F;

        boolean kept = onGrid(x, z, step);
        boolean highGrid = high && onGrid(x, z, Math.max(3, step));
        if (!kept && !highGrid) return 0.0F;

        stormAt(x, z);
        towerGrid = onGrid(x, z, Math.max(2, step));
        if (highGrid) highStrength = highLayerAt(x, z);

        float value = 0.0F;
        if (kept) {
            stats[step][0]++;
            value = sampler.sample(cloudinessAt());
            if (value > 0.0F) stats[step][1]++;
        }
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

        // Dark where it actually rains, so a dark cloud overhead means rain.
        float dark = darkness ? Mth.clamp(Math.max(0.85F * thunder, 0.75F * smoothstep(0.15F, 0.55F, rain)), 0.0F, 1.0F) : 0.0F;
        if (dark > 0.1F) stats[step][2]++;
        // Rain decks hang a little lower than fair-weather clouds.
        if (thick) y -= 20.0 * rain;
        // Better Clouds' second, lower puff for very dense samples: no extras on top of it.
        if (pointsThisSample > 1) return new CloudCode(x, y, z, y, y, code(size, dark, false));

        if (thick && rain > 0.4F) {
            queue(x, y + sizeY * size * 0.6, z, size, dark * 0.9F, false, 0.35, 1);
        }
        if (towers && towerGrid && thunder > 0.25F) {
            int towerSize = Math.max(2, step);
            double levelStep = sizeY * towerSize * 0.7;
            int levels = (int) (TOWER_HEIGHT * thunder * towerShape / levelStep);
            for (int i = 1; i <= levels; i++) {
                queue(x, y + i * levelStep, z, towerSize, dark * (1.0F - 0.75F * i / levels), false, 0.3, 10 + i);
            }
            // The anvil spreads wider than the towers: any point of the storm core gets a flat puff up there.
            double anvilY = yOffset + ANVIL_HEIGHT + 8.0 * (hash(sampleX, sampleZ, 3) - 0.5);
            queue(x, anvilY, z, towerSize, dark * 0.25F, true, 0.45, 4);
            towerPieces += levels + 1;
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

    /**
     * Rain, thunder and overcast for the current sample, from the same storm shapes the server rains with.
     * Towers grow out of separate convective cells instead of one even plateau.
     */
    private void stormAt(int x, int z) {
        rain = 0.0F;
        thunder = 0.0F;
        cover = 0.0F;
        towerShape = 0.0F;
        if (!hasWeather) return;
        double wx = x + originX;
        double wz = z + originZ;
        if (!nearAnyCell(wx, wz)) return;
        rain = rainAt(wx, wz);
        thunder = thunderAt(wx, wz);
        cover = cloudAt(wx, wz);
        if (thunder > 0.0F) {
            double cellsNoise = STORM_NOISE.getValue(wx / 380.0 + 91.0, wz / 380.0 + 91.0);
            towerShape = (float) (0.3 + 0.7 * Math.max(0.0, cellsNoise));
        }
    }

    private boolean nearAnyCell(double wx, double wz) {
        for (StormCell cell : cells) {
            double reach = cell.radius * CELL_REACH;
            if (Mth.square(cell.centerX(aheadTicks) - wx) + Mth.square(cell.centerZ(aheadTicks) - wz) < reach * reach) return true;
        }
        return false;
    }

    /** A flat high cloud, nudged off the sample grid so the layer doesn't show a checkerboard. */
    private CloudCode highCloud(double x, double z, int size) {
        float weather = Math.max(cover, thunder);
        int highSize = Math.max(size, 2);
        double width = sizeXZ * highSize;
        double px = x + width * 0.45 * (2.0 * hash(sampleX, sampleZ, 21) - 1.0);
        double pz = z + width * 0.45 * (2.0 * hash(sampleX, sampleZ, 22) - 1.0);
        double y = yOffset + highHeight + 20.0 * HIGH_NOISE.getValue(x / 900.0, z / 900.0) + 6.0 * (hash(sampleX, sampleZ, 5) - 0.5);
        return new CloudCode(px, y, pz, y, y, code(highSize, 0.4F * weather, true));
    }

    /** Adds a stacked cloud, nudged sideways by up to {@code jitter} of its width so faces never line up. */
    private void queue(double x, double y, double z, int size, float dark, boolean flat, double jitter, int salt) {
        double width = sizeXZ * size;
        double px = x + width * jitter * (2.0 * hash(sampleX, sampleZ, salt) - 1.0);
        double pz = z + width * jitter * (2.0 * hash(sampleX, sampleZ, salt + 1000) - 1.0);
        pending.add(new CloudCode(px, y, pz, y, y, code(size, dark, flat)));
    }

    private int code(int size, float dark, boolean flat) {
        return encode ? CloudCode.encode(size, dark, flat) : 0;
    }

    /**
     * 1 near the camera, 2 and 4 further out (keep every 2nd/4th point, drawn bigger), 0 past the cloud distance.
     * The boundaries are dithered per point so there's no visible ring where the cloud size changes.
     */
    private int lodStep(int x, int z) {
        if (!lod) return 1;
        double d = Math.sqrt(Mth.square(x - cameraX) + Mth.square(z - cameraZ));
        if (d > blockDistance) return 0;
        d += LOD_BLEND * (hash(gridIndex(x), gridIndex(z), 7) - 0.5);
        if (d > 2.0 * detailDistance) return 4;
        if (d > detailDistance) return 2;
        return 1;
    }

    /** Better Clouds samples at floor(grid * spacing); recover the grid index to thin out points evenly. */
    private boolean onGrid(int x, int z, int step) {
        if (step <= 1) return true;
        return Math.floorMod(gridIndex(x), step) == 0 && Math.floorMod(gridIndex(z), step) == 0;
    }

    private int gridIndex(int sample) {
        return (int) Math.ceil(sample / spacing);
    }

    /** High, flat clouds: patchy in clear weather, a near-solid sheet around storms. */
    private float highLayerAt(int x, int z) {
        double n = 0.65 * HIGH_NOISE.getValue(x / 1600.0, z / 1600.0) + 0.35 * HIGH_NOISE.getValue(x / 420.0 + 100.0, z / 420.0);
        // Storms thicken the high layer, but it should stay patchy rather than become a solid lid.
        float weather = Math.max(cover, thunder);
        double threshold = 0.5 - 0.3 * weather;
        return n > threshold ? (float) Math.min(1.0, (n - threshold) * 3.0) : 0.0F;
    }

    /**
     * Cloudiness for the current sample: the fair-weather cover, raised towards full overcast by storms.
     * The overcast reaches past the rain, so storms look as big as they are.
     */
    private float cloudinessAt() {
        float weather = Math.max(Math.max(0.6F * rain, thunder), 0.85F * cover);
        if (weather > 0.0F) {
            // Some texture inside the storm so it isn't one flat sheet.
            weather *= (float) (0.85 + 0.15 * STORM_NOISE.getValue(sampleX / 250.0 + 11.0, sampleZ / 250.0 + 11.0));
        }
        float clear = cloudCover;
        // Never zero: Better Clouds divides by it.
        return Mth.clamp(otherFactors * (clear + (1.05F - clear) * weather), 0.01F, 1.0F);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Stable 0..1 value per grid point and purpose. */
    private static float hash(int x, int z, int salt) {
        int h = x * 0x27D4EB2D ^ z * 0x165667B1 ^ salt * 0x9E3779B9;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return (h >>> 8) / (float) (1 << 24);
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
