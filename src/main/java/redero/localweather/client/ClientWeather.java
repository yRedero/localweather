package redero.localweather.client;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import redero.localweather.LocalWeather;
import redero.localweather.net.WeatherSyncPayload;
import redero.localweather.weather.LocalWeatherApi;
import redero.localweather.weather.StormCell;
import redero.localweather.weather.WeatherView;

/**
 * The storm cells the server last sent, moved forward along their velocity between syncs,
 * plus rain and thunder levels at the camera that ease in and out like vanilla's.
 */
public final class ClientWeather implements WeatherView, LocalWeatherApi.ClientHooks {
    public static final ClientWeather INSTANCE = new ClientWeather();

    /** Fall back to vanilla if the server stops syncing, e.g. after the mod is removed from it. */
    private static final int STALE_TICKS = 200;
    /** Per-tick change of the camera levels; a full fade takes four seconds. */
    private static final float FADE_STEP = 0.0125F;

    private List<StormCell> cells = List.of();
    private float windX, windZ;
    private int ticksSinceSync;
    private boolean active;
    /** Snap the camera levels on the first sync so joining mid-storm does not fade in. */
    private boolean snapLevels;
    @Nullable
    private Level level;

    private float rain, oRain, thunder, oThunder;
    /** Overcast at the camera, eased like rain: dims sky and fog before the rain arrives. */
    private float gloom, oGloom;
    /** How far the storm wind has carried the clouds; Better Clouds' clouds drift by this. */
    private double windOffsetX, windOffsetZ, oWindOffsetX, oWindOffsetZ;

    private ClientWeather() {
    }

    public void accept(WeatherSyncPayload payload) {
        cells = payload.cells();
        windX = payload.windX();
        windZ = payload.windZ();
        ticksSinceSync = 0;
        if (!active) {
            snapLevels = true;
            LocalWeather.LOGGER.info("Local weather active, {} storm cells nearby", cells.size());
        }
        active = true;
        level = Minecraft.getInstance().level;
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            // New world or dimension: wait for the next sync before taking over again.
            reset();
            level = mc.level;
            return;
        }
        if (!active) return;
        if (++ticksSinceSync > STALE_TICKS) {
            reset();
            return;
        }

        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        float targetRain = rainAt(camera.getX(), camera.getZ());
        float targetThunder = thunderAt(camera.getX(), camera.getZ());
        float targetGloom = cloudAt(camera.getX(), camera.getZ());
        if (snapLevels) {
            rain = targetRain;
            thunder = targetThunder;
            gloom = targetGloom;
            snapLevels = false;
        }
        oRain = rain;
        oThunder = thunder;
        oGloom = gloom;
        rain = rain + Mth.clamp(targetRain - rain, -FADE_STEP, FADE_STEP);
        thunder = thunder + Mth.clamp(targetThunder - thunder, -FADE_STEP, FADE_STEP);
        gloom = gloom + Mth.clamp(targetGloom - gloom, -FADE_STEP, FADE_STEP);

        oWindOffsetX = windOffsetX;
        oWindOffsetZ = windOffsetZ;
        windOffsetX += windX;
        windOffsetZ += windZ;
    }

    public void reset() {
        active = false;
        cells = List.of();
        ticksSinceSync = 0;
        rain = oRain = thunder = oThunder = 0.0F;
        gloom = oGloom = 0.0F;
    }

    public boolean active() {
        return active;
    }

    public float windX() {
        return windX;
    }

    public float windZ() {
        return windZ;
    }

    public float cameraRain() {
        return rain;
    }

    public float cameraThunder() {
        return thunder;
    }

    /** Overcast at the camera, 0..1, or 0 without local weather. */
    public float gloom(float partialTick) {
        if (!active) return 0.0F;
        float strength = LocalWeatherClientConfig.SPEC.isLoaded() ? LocalWeatherClientConfig.SKY_GLOOM.get().floatValue() : 1.0F;
        return strength * Mth.lerp(partialTick, oGloom, gloom);
    }

    /** Greys and dims a sky or fog colour by the overcast, short of what rain itself does. */
    public static Vec3 gloomy(Vec3 color, float gloom) {
        if (gloom <= 0.0F) return color;
        double grey = (color.x * 0.3 + color.y * 0.59 + color.z * 0.11) * 0.8;
        double t = 0.45 * gloom;
        double dim = 1.0 - 0.15 * gloom;
        return new Vec3(Mth.lerp(t, color.x, grey) * dim, Mth.lerp(t, color.y, grey) * dim, Mth.lerp(t, color.z, grey) * dim);
    }

    public double windOffsetX(float partialTick) {
        return Mth.lerp(partialTick, oWindOffsetX, windOffsetX);
    }

    public double windOffsetZ(float partialTick) {
        return Mth.lerp(partialTick, oWindOffsetZ, windOffsetZ);
    }

    @Override
    public List<StormCell> cells() {
        return cells;
    }

    @Override
    public int aheadTicks() {
        return ticksSinceSync;
    }

    @Nullable
    @Override
    public WeatherView view(Level level) {
        return active && level == this.level ? this : null;
    }

    @Override
    public float rainLevel(Level level, float partialTick) {
        if (!active || level != this.level) return Float.NaN;
        return Mth.lerp(partialTick, oRain, rain);
    }

    @Override
    public float thunderLevel(Level level, float partialTick) {
        if (!active || level != this.level) return Float.NaN;
        return Mth.lerp(partialTick, oThunder, thunder) * Mth.lerp(partialTick, oRain, rain);
    }
}
