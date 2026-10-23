package redero.localweather.client.betterclouds;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import redero.localweather.client.ClientWeather;
import redero.localweather.weather.StormCell;
import redero.localweather.weather.WeatherView;

/**
 * Storm cells frozen at the moment Better Clouds queues a generator task. The task samples it from a
 * worker thread, so it must not read the live {@link ClientWeather} state.
 */
public final class LocalCloudField implements WeatherView {
    /** Better Clouds' own weather term is 0.5 + 0.3 * weather; storms push coverage further than that. */
    private static final float STORM_GAIN = 0.5F;

    private final List<StormCell> cells;
    private final int aheadTicks;
    private final double originX, originZ;
    /** Whatever Better Clouds multiplies on top of weather (season compat), recovered from its global value. */
    private final float otherFactors;

    private LocalCloudField(List<StormCell> cells, int aheadTicks, double originX, double originZ, float otherFactors) {
        this.cells = cells;
        this.aheadTicks = aheadTicks;
        this.originX = originX;
        this.originZ = originZ;
        this.otherFactors = otherFactors;
    }

    /** Null when there is no local weather, so Better Clouds keeps its global cloudiness. */
    @Nullable
    public static LocalCloudField capture(double originX, double originZ, float globalCloudiness, int blockDistance) {
        ClientWeather weather = ClientWeather.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity camera = mc.getCameraEntity();
        if (!weather.active() || level == null || camera == null) return null;

        List<StormCell> inView = BetterCloudsCompat.cellsInView(camera.getX(), camera.getZ(), blockDistance);
        // Same weather term as Better Clouds' CloudinessProvider, at the camera.
        float cameraWeather = Math.max(0.6F * level.getRainLevel(1.0F), level.getThunderLevel(1.0F));
        float otherFactors = globalCloudiness / (0.3F * cameraWeather + 0.5F);
        return new LocalCloudField(new ArrayList<>(inView), weather.aheadTicks(), originX, originZ, otherFactors);
    }

    /** Cloudiness for one Better Clouds sample; sample coordinates are world coordinates minus the cloud origin. */
    public float cloudinessAt(int sampleX, int sampleZ) {
        double x = sampleX + originX;
        double z = sampleZ + originZ;
        float weather = Math.max(0.6F * rainAt(x, z), thunderAt(x, z));
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
}
