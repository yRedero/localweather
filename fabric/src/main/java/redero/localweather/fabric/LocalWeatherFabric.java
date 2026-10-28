package redero.localweather.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.neoforged.fml.config.ModConfig;
import redero.localweather.LocalWeather;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.net.WeatherSyncPayload;
import redero.localweather.server.LocalWeatherCommand;
import redero.localweather.server.LocalWeatherServerConfig;

public final class LocalWeatherFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Forge Config API Port gives Fabric the same config files (and per world serverconfig/) as NeoForge.
        NeoForgeModConfigEvents.loading(LocalWeather.MODID).register(LocalWeather::onConfigLoaded);
        NeoForgeModConfigEvents.reloading(LocalWeather.MODID).register(LocalWeather::onConfigLoaded);
        NeoForgeConfigRegistry.INSTANCE.register(LocalWeather.MODID, ModConfig.Type.CLIENT, LocalWeatherClientConfig.SPEC);
        NeoForgeConfigRegistry.INSTANCE.register(LocalWeather.MODID, ModConfig.Type.SERVER, LocalWeatherServerConfig.SPEC);

        // Fabric channels are optional by nature: players without the mod just never get the payload.
        PayloadTypeRegistry.playS2C().register(WeatherSyncPayload.TYPE, WeatherSyncPayload.STREAM_CODEC);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> LocalWeatherCommand.register(dispatcher));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LocalWeather.onLoggedIn(handler.getPlayer()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> LocalWeather.onChangedLevel(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> LocalWeather.onChangedLevel(newPlayer));
    }
}
