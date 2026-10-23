package redero.localweather;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.net.WeatherSyncPayload;
import redero.localweather.server.DevScene;
import redero.localweather.server.LocalWeatherCommand;
import redero.localweather.server.WeatherSimulator;

@Mod(LocalWeather.MODID)
public class LocalWeather {
    public static final String MODID = "localweather";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LocalWeather(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, LocalWeatherClientConfig.SPEC);
        modBus.addListener(LocalWeather::registerPayloads);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> LocalWeatherCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(LocalWeather::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(LocalWeather::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(LocalWeather::onRespawn);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Optional, so either side can join the other without the mod and fall back to vanilla weather.
        event.registrar("1").optional().playToClient(WeatherSyncPayload.TYPE, WeatherSyncPayload.STREAM_CODEC,
                (payload, context) -> redero.localweather.client.ClientWeather.INSTANCE.accept(payload));
    }

    // Sync right away so a fresh client doesn't show vanilla weather for up to a second.
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (DevScene.ENABLED) DevScene.setUp(player);
        WeatherSimulator.sync(player);
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WeatherSimulator.sync(player);
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WeatherSimulator.sync(player);
    }
}
