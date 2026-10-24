package redero.localweather.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.ModList;
import redero.localweather.LocalWeather;
import redero.localweather.client.betterclouds.BetterCloudsDev;
import redero.localweather.server.DevScene;

/**
 * Client half of {@link DevScene}: once local weather is active, looks in fixed directions, saves a screenshot
 * of each to run/screenshots/localweather_*.png, then quits the game.
 */
final class DevSceneClient {
    /** Name, yaw (0 south, -90 east, 180 north, 90 west), pitch (negative looks up). */
    private static final Object[][] VIEWS = {
            {"east_thunderstorm", -90.0F, -10.0F},
            {"north_rain_front", 180.0F, -10.0F},
            {"west_shower", 90.0F, -10.0F},
            {"south_near_thunderstorm", 0.0F, -12.0F},
            {"up", -60.0F, -70.0F},
    };
    /** Time for Better Clouds to finish generating before the first shot. */
    private static final int WARMUP_TICKS = 300;
    private static final int TICKS_PER_VIEW = 100;

    private static int ticks = -1;
    private static boolean vsync;
    private static int framerateLimit;

    private DevSceneClient() {
    }

    static void tick() {
        if (!DevScene.ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ClientWeather.INSTANCE.active()) return;
        if (ticks < 0) {
            ticks = 0;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            // Uncapped frame rate so the logged fps show rendering cost; restored before quitting.
            vsync = mc.options.enableVsync().get();
            framerateLimit = mc.options.framerateLimit().get();
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            if (ModList.get().isLoaded("betterclouds")) BetterCloudsDev.enableProfiling(200);
        }
        // The window may lose focus while the scene runs; a pause menu would end up in the screenshots.
        if (mc.screen != null) mc.setScreen(null);
        ticks++;

        int t = ticks - WARMUP_TICKS;
        if (t < 0) {
            look(player, VIEWS[0]);
            return;
        }
        int view = t / TICKS_PER_VIEW;
        if (view >= VIEWS.length) {
            if (view == VIEWS.length && t % TICKS_PER_VIEW == 20) {
                LocalWeather.LOGGER.info("Dev scene: camera rain {} thunder {} gloom {}, cloud height offset {}", ClientWeather.INSTANCE.cameraRain(),
                        ClientWeather.INSTANCE.cameraThunder(), ClientWeather.INSTANCE.gloom(1.0F), ClientWeather.INSTANCE.cloudHeightOffset());
                LocalWeather.LOGGER.info("Dev scene: screenshots done, quitting");
                mc.options.enableVsync().set(vsync);
                mc.options.framerateLimit().set(framerateLimit);
                mc.stop();
            }
            return;
        }
        look(player, VIEWS[view]);
        // Turn first, shoot near the end of the slot so the frame shows the new direction.
        if (t % TICKS_PER_VIEW == TICKS_PER_VIEW - 10) {
            String file = "localweather_" + VIEWS[view][0] + ".png";
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                    message -> LocalWeather.LOGGER.info("Dev scene: saved {} at {} fps", file, mc.getFps()));
        }
    }

    private static void look(LocalPlayer player, Object[] view) {
        float yaw = (Float) view[1];
        // From above the clouds, look down at them instead of up.
        float pitch = DevScene.ABOVE ? 30.0F : (Float) view[2];
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        player.xRotO = pitch;
    }
}
