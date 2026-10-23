package redero.localweather.client;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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
        if (snapLevels) {
            rain = targetRain;
            thunder = targetThunder;
            snapLevels = false;
        }
        oRain = rain;
        oThunder = thunder;
        rain = rain + Mth.clamp(targetRain - rain, -FADE_STEP, FADE_STEP);
        thunder = thunder + Mth.clamp(targetThunder - thunder, -FADE_STEP, FADE_STEP);
    }

    public void reset() {
        active = false;
        cells = List.of();
        ticksSinceSync = 0;
        rain = oRain = thunder = oThunder = 0.0F;
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
