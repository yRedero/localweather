package redero.localweather.neoforge;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import redero.localweather.LocalWeather;
import redero.localweather.client.LocalWeatherClient;
import redero.localweather.client.RainHaze;
import redero.localweather.client.FarWeather;

@Mod(value = LocalWeather.MODID, dist = Dist.CLIENT)
public class LocalWeatherNeoForgeClient {
    public LocalWeatherNeoForgeClient(IEventBus modBus, ModContainer container) {
        LocalWeatherClient.init();
        modBus.addListener((RegisterShadersEvent event) -> {
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), FarWeather.SHADER,
                        DefaultVertexFormat.POSITION_COLOR), FarWeather::setShader);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), RainHaze.SHADER,
                        DefaultVertexFormat.POSITION), RainHaze::setShader);
            } catch (IOException e) {
                LocalWeather.LOGGER.error("Could not load the rain shaft shader", e);
            }
        });
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
        public static void onRenderStage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
                LocalWeatherClient.renderAfterWeather(event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        }

        @SubscribeEvent
        public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
            LocalWeatherClient.addDebugText(event.getLeft());
        }
    }
}
