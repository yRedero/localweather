package redero.localweather.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import redero.localweather.LocalWeather;
import redero.localweather.weather.LocalWeatherApi;
import redero.localweather.weather.StormCell;

@EventBusSubscriber(modid = LocalWeather.MODID, value = Dist.CLIENT)
public final class LocalWeatherClient {
    private static final String[] COMPASS = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};

    private LocalWeatherClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientWeather.INSTANCE.tick();
        DevSceneClient.tick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientWeather.INSTANCE.reset();
    }

    /** F3 lines: weather at the camera, wind, and the three nearest cells. */
    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        ClientWeather weather = ClientWeather.INSTANCE;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) return;
        List<String> lines = event.getLeft();
        lines.add("");
        if (!weather.active()) {
            lines.add("[Local Weather] inactive (vanilla weather)");
            return;
        }
        double windSpeed = Math.sqrt(weather.windX() * weather.windX() + weather.windZ() * weather.windZ()) * 20.0;
        lines.add(String.format(Locale.ROOT, "[Local Weather] rain %.2f thunder %.2f | %d cells | wind %.1f b/s %s",
                weather.cameraRain(), weather.cameraThunder(), weather.cells().size(),
                windSpeed, compass(weather.windX(), weather.windZ())));

        int ahead = weather.aheadTicks();
        weather.cells().stream()
                .sorted(Comparator.comparingDouble(c -> distance(c, camera, ahead)))
                .limit(3)
                .forEach(c -> lines.add(String.format(Locale.ROOT, "  #%d %s %s %.0fb %s r=%.0f rain here %.2f",
                        c.id, c.type, c.stage(), distance(c, camera, ahead),
                        compass(c.centerX(ahead) - camera.getX(), c.centerZ(ahead) - camera.getZ()),
                        c.radius, c.rainAt(camera.getX(), camera.getZ(), ahead))));
    }

    private static double distance(StormCell cell, Entity camera, int ahead) {
        return Math.sqrt(Mth.square(cell.centerX(ahead) - camera.getX()) + Mth.square(cell.centerZ(ahead) - camera.getZ()));
    }

    /** Minecraft's +X is east and +Z is south. */
    private static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        return COMPASS[Math.floorMod((int) Math.round(angle / 45.0), 8)];
    }

    @EventBusSubscriber(modid = LocalWeather.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LocalWeatherApi.setClientHooks(ClientWeather.INSTANCE);
            ModList.get().getModContainerById(LocalWeather.MODID).ifPresent(container ->
                    container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
        }
    }
}
