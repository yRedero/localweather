package redero.localweather;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.server.DevScene;
import redero.localweather.server.LocalWeatherServerConfig;
import redero.localweather.server.WeatherSimulator;

/** Shared entry points; the NeoForge and Fabric entry classes call these from their loader's events. */
public final class LocalWeather {
    public static final String MODID = "localweather";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Version of the sync payload. 2 added the cloud height offset. */
    public static final String PROTOCOL = "2";

    private LocalWeather() {
    }

    /** A changed preset (config screen, file edit, or edited while the game was closed) fills in its values. */
    public static void onConfigLoaded(ModConfig config) {
        if (config.getSpec() == LocalWeatherClientConfig.SPEC) LocalWeatherClientConfig.sync(config);
        if (config.getSpec() == LocalWeatherServerConfig.SPEC) LocalWeatherServerConfig.sync(config);
    }

    /** Sync right away so a fresh client doesn't show vanilla weather for up to a second. */
    public static void onLoggedIn(ServerPlayer player) {
        if (DevScene.ENABLED) DevScene.setUp(player);
        WeatherSimulator.sync(player);
    }

    /** Dimension change or respawn: the player needs the weather of where they are now. */
    public static void onChangedLevel(ServerPlayer player) {
        WeatherSimulator.sync(player);
    }
}
