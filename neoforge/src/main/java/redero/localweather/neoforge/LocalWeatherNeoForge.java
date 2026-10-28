package redero.localweather.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import redero.localweather.LocalWeather;
import redero.localweather.client.ClientWeather;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.net.WeatherSyncPayload;
import redero.localweather.server.LocalWeatherCommand;
import redero.localweather.server.LocalWeatherServerConfig;

@Mod(LocalWeather.MODID)
public class LocalWeatherNeoForge {
    public LocalWeatherNeoForge(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, LocalWeatherClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, LocalWeatherServerConfig.SPEC);
        modBus.addListener((ModConfigEvent.Loading event) -> LocalWeather.onConfigLoaded(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> LocalWeather.onConfigLoaded(event.getConfig()));
        modBus.addListener(LocalWeatherNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> LocalWeatherCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) LocalWeather.onLoggedIn(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) LocalWeather.onChangedLevel(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) LocalWeather.onChangedLevel(player);
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Optional, so either side can join the other without the mod and fall back to vanilla weather.
        event.registrar(LocalWeather.PROTOCOL).optional().playToClient(WeatherSyncPayload.TYPE, WeatherSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientWeather.INSTANCE.accept(payload));
    }
}
