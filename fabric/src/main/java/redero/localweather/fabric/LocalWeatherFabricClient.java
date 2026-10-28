package redero.localweather.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import redero.localweather.LocalWeather;
import redero.localweather.client.ClientWeather;
import redero.localweather.client.LocalWeatherClient;
import redero.localweather.net.WeatherSyncPayload;

/** The F3 lines come from DebugScreenOverlayMixin, Fabric API has no event for them. */
public final class LocalWeatherFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LocalWeatherClient.init();
        // Shown in Mod Menu.
        ConfigScreenFactoryRegistry.INSTANCE.register(LocalWeather.MODID, ConfigurationScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(WeatherSyncPayload.TYPE, (payload, context) -> ClientWeather.INSTANCE.accept(payload));
        ClientTickEvents.END_CLIENT_TICK.register(client -> LocalWeatherClient.tick());
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> LocalWeatherClient.onEntityJoin(entity));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LocalWeatherClient.onLoggingOut());
    }
}
