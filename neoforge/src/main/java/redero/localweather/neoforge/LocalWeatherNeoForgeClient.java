package redero.localweather.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import redero.localweather.LocalWeather;
import redero.localweather.client.LocalWeatherClient;

@Mod(value = LocalWeather.MODID, dist = Dist.CLIENT)
public class LocalWeatherNeoForgeClient {
    public LocalWeatherNeoForgeClient(ModContainer container) {
        LocalWeatherClient.init();
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @EventBusSubscriber(modid = LocalWeather.MODID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            LocalWeatherClient.tick();
        }

        @SubscribeEvent
        public static void onEntityJoin(EntityJoinLevelEvent event) {
            LocalWeatherClient.onEntityJoin(event.getEntity());
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            LocalWeatherClient.onLoggingOut();
        }

        @SubscribeEvent
        public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
            LocalWeatherClient.addDebugText(event.getLeft());
        }
    }
}
