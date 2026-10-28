package redero.localweather.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import redero.localweather.weather.LocalWeatherApi;
import redero.localweather.weather.StormCell;

/** Client side entry points; the loader-specific client classes call these from their events. */
public final class LocalWeatherClient {
    private static final String[] COMPASS = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};

    private LocalWeatherClient() {
    }

    public static void init() {
        LocalWeatherApi.setClientHooks(ClientWeather.INSTANCE);
    }

    /** End of every client tick. */
    public static void tick() {
        ClientWeather.INSTANCE.tick();
        DevSceneClient.tick();
    }

    /** Real lightning bolts light up the clouds above them. */
    public static void onEntityJoin(Entity entity) {
        if (entity.level().isClientSide() && entity instanceof LightningBolt bolt) {
            LightningFlashes.bolt(bolt.getX(), bolt.getZ());
        }
    }

    public static void onLoggingOut() {
        ClientWeather.INSTANCE.reset();
    }

    /** F3 lines: weather at the camera, wind, and the three nearest cells. */
    public static void addDebugText(List<String> lines) {
        ClientWeather weather = ClientWeather.INSTANCE;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) return;
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
}
