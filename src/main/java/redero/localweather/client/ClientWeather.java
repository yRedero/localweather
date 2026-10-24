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
 * The storm cells the server last sent, moved forward along their velocity between syncs, plus the weather
 * levels at the camera. Rain and thunder follow the camera quickly, so rain falls where the clouds are; the sky
 * (colour, light, fog) follows a slow average over the surroundings, so small clouds don't make it flicker.
 */
public final class ClientWeather implements WeatherView, LocalWeatherApi.ClientHooks {
    public static final ClientWeather INSTANCE = new ClientWeather();

    /** Fall back to vanilla if the server stops syncing, e.g. after the mod is removed from it. */
    private static final int STALE_TICKS = 200;
    /** Per-tick change of rain and thunder at the camera; a full fade takes one second. */
    private static final float FAST_STEP = 0.05F;
    /** Per-tick change of the sky's weather; a full fade takes about fourteen seconds. */
    private static final float SLOW_STEP = 0.0035F;
    /** The sky averages the weather over the camera and four points this far around it. */
    private static final double SKY_RADIUS = 160.0;

    private List<StormCell> cells = List.of();
    private float windX, windZ;
    /** The world's cloud height offset, from the server. */
    private int serverCloudHeightOffset;
    private int ticksSinceSync;
    private boolean active;
    /** Snap the camera levels on the first sync so joining mid-storm does not fade in. */
    private boolean snapLevels;
    @Nullable
    private Level level;

    private float rain, oRain, thunder, oThunder;
    /** The sky's view of the weather: slower and averaged over the surroundings. */
    private float skyRain, oSkyRain, skyThunder, oSkyThunder;
    /** Overcast around the camera: dims sky and fog before the rain arrives. */
    private float gloom, oGloom;
    /** 1 below the cloud base, 0 above it (if rain above the clouds is off). */
    private float belowClouds = 1.0F;
    private final float[] sample = new float[3];
    /** How far the storm wind has carried the clouds; Better Clouds' clouds drift by this. */
    private double windOffsetX, windOffsetZ, oWindOffsetX, oWindOffsetZ;

    private ClientWeather() {
    }

    public void accept(WeatherSyncPayload payload) {
        cells = payload.cells();
        windX = payload.windX();
        windZ = payload.windZ();
        serverCloudHeightOffset = payload.cloudHeightOffset();
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
        if (mc.isPaused()) {
            // Singleplayer pause (menus): the server stops too, so hold everything still instead of drifting
            // storms and timing out for lack of syncs.
            holdLevels();
            return;
        }
        if (++ticksSinceSync > STALE_TICKS) {
            reset();
            return;
        }

        Entity camera = mc.getCameraEntity();
        if (camera == null) return;
        float targetBelow = belowCloudsAt(camera);
        sample(camera.getX(), camera.getZ(), sample);
        float targetRain = sample[0] * targetBelow;
        float targetThunder = sample[1] * targetBelow;

        float skyRainSum = 0.0F, skyThunderSum = 0.0F, cloudSum = 0.0F;
        for (int i = 0; i < 5; i++) {
            double dx = i == 1 ? SKY_RADIUS : i == 2 ? -SKY_RADIUS : 0.0;
            double dz = i == 3 ? SKY_RADIUS : i == 4 ? -SKY_RADIUS : 0.0;
            sample(camera.getX() + dx, camera.getZ() + dz, sample);
            skyRainSum += sample[0];
            skyThunderSum += sample[1];
            cloudSum += sample[2];
        }
        float targetSkyRain = skyRainSum / 5.0F * targetBelow;
        float targetSkyThunder = skyThunderSum / 5.0F * targetBelow;
        float targetGloom = cloudSum / 5.0F * targetBelow;

        if (snapLevels) {
            rain = targetRain;
            thunder = targetThunder;
            skyRain = targetSkyRain;
            skyThunder = targetSkyThunder;
            gloom = targetGloom;
            belowClouds = targetBelow;
            snapLevels = false;
        }
        holdLevels();
        rain = approach(rain, targetRain, FAST_STEP);
        thunder = approach(thunder, targetThunder, FAST_STEP);
        skyRain = approach(skyRain, targetSkyRain, SLOW_STEP);
        skyThunder = approach(skyThunder, targetSkyThunder, SLOW_STEP);
        gloom = approach(gloom, targetGloom, SLOW_STEP);
        belowClouds = targetBelow;

        windOffsetX += windX;
        windOffsetZ += windZ;
    }

    private void holdLevels() {
        oRain = rain;
        oThunder = thunder;
        oSkyRain = skyRain;
        oSkyThunder = skyThunder;
        oGloom = gloom;
        oWindOffsetX = windOffsetX;
        oWindOffsetZ = windOffsetZ;
    }

    private static float approach(float from, float to, float maxStep) {
        return from + Mth.clamp(to - from, -maxStep, maxStep);
    }

    /**
     * 1 below the cloud base, fading to 0 just above it, when rain above the clouds is turned off. The cloud base
     * is the dimension's cloud height plus the configured offset, so it matches where Better Clouds draws them.
     */
    private static float belowCloudsAt(Entity camera) {
        if (!LocalWeatherClientConfig.SPEC.isLoaded() || !LocalWeatherClientConfig.NO_RAIN_ABOVE_CLOUDS.get()) return 1.0F;
        if (!(camera.level() instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return 1.0F;
        float cloudHeight = clientLevel.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight)) return 1.0F;
        double base = cloudHeight + INSTANCE.cloudHeightOffset();
        double t = Mth.clamp((camera.getEyeY() - (base - 8.0)) / 32.0, 0.0, 1.0);
        return (float) (1.0 - t * t * (3.0 - 2.0 * t));
    }

    public void reset() {
        active = false;
        cells = List.of();
        ticksSinceSync = 0;
        rain = oRain = thunder = oThunder = 0.0F;
        skyRain = oSkyRain = skyThunder = oSkyThunder = 0.0F;
        gloom = oGloom = 0.0F;
        belowClouds = 1.0F;
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

    /** Rain as the sky sees it (slow, averaged), or NaN without local weather. */
    public float skyRainLevel(float partialTick) {
        return active ? Mth.lerp(partialTick, oSkyRain, skyRain) : Float.NaN;
    }

    /** Thunder as the sky sees it, already multiplied by its rain like vanilla's, or NaN without local weather. */
    public float skyThunderLevel(float partialTick) {
        return active ? Mth.lerp(partialTick, oSkyThunder, skyThunder) * Mth.lerp(partialTick, oSkyRain, skyRain) : Float.NaN;
    }

    /**
     * How far clouds are moved from the dimension's cloud height: the world's setting when connected to a server
     * with local weather (so everyone sees the same clouds the weather follows), otherwise the client's own.
     */
    public int cloudHeightOffset() {
        if (!LocalWeatherClientConfig.SPEC.isLoaded()) return active ? serverCloudHeightOffset : 0;
        boolean follow = LocalWeatherClientConfig.FOLLOW_SERVER_CLOUD_HEIGHT.get();
        return active && follow ? serverCloudHeightOffset : LocalWeatherClientConfig.CLOUD_HEIGHT_OFFSET.get();
    }

    public boolean belowClouds() {
        return belowClouds > 0.5F;
    }

    /** Overcast around the camera, 0..1, or 0 without local weather. */
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
