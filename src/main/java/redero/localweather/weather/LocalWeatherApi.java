package redero.localweather.weather;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import redero.localweather.server.LocalWeatherServerConfig;
import redero.localweather.server.WeatherSavedData;

/** Entry point the mixins use to ask about local weather without touching client classes on a server. */
public final class LocalWeatherApi {
    /** Rain counts as "raining" above this, matching vanilla's isRaining() threshold. */
    public static final float RAIN_THRESHOLD = 0.2F;
    /** Lightning can strike where local thunder is above this. */
    public static final float THUNDER_THRESHOLD = 0.5F;

    /** The overworld's cloud height; the server can't ask the client-only DimensionSpecialEffects. */
    public static final int VANILLA_CLOUD_HEIGHT = 192;

    private static ClientHooks client = ClientHooks.NONE;

    private LocalWeatherApi() {
    }

    public static void setClientHooks(ClientHooks hooks) {
        client = hooks;
    }

    public static ClientHooks client() {
        return client;
    }

    /** Overworld-like dimensions: sky light and no ceiling, the same test vanilla uses for thunder. */
    public static boolean managed(Level level) {
        DimensionType type = level.dimensionType();
        return type.hasSkyLight() && !type.hasCeiling();
    }

    /** True if the world's settings make it dry at this height: above the cloud base nothing falls. */
    public static boolean aboveCloudBase(int y) {
        return LocalWeatherServerConfig.dryAboveClouds()
                && y >= VANILLA_CLOUD_HEIGHT + LocalWeatherServerConfig.cloudHeightOffset();
    }

    /** The weather for this level, or null when vanilla weather should apply. */
    @Nullable
    public static WeatherView view(Level level) {
        if (!managed(level)) return null;
        if (level instanceof ServerLevel serverLevel) return WeatherSavedData.get(serverLevel);
        return level.isClientSide() ? client.view(level) : null;
    }

    public interface ClientHooks {
        ClientHooks NONE = new ClientHooks() {
            @Nullable
            @Override
            public WeatherView view(Level level) {
                return null;
            }

            @Override
            public float rainLevel(Level level, float partialTick) {
                return Float.NaN;
            }

            @Override
            public float thunderLevel(Level level, float partialTick) {
                return Float.NaN;
            }
        };

        /** Null while the server has not sent any local weather. */
        @Nullable
        WeatherView view(Level level);

        /** Smoothed rain at the camera, or NaN to keep vanilla. */
        float rainLevel(Level level, float partialTick);

        /** Smoothed thunder at the camera (already multiplied by rain, like vanilla), or NaN. */
        float thunderLevel(Level level, float partialTick);
    }
}
