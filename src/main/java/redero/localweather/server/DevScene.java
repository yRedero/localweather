package redero.localweather.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import redero.localweather.LocalWeather;
import redero.localweather.weather.CellType;

/**
 * Development only, enabled with -Dlocalweather.devscene=true (./gradlew runClient -PdevScene): puts storms
 * around the joining player and freezes time and weather, so the client can take comparable screenshots.
 */
public final class DevScene {
    public static final boolean ENABLED = Boolean.getBoolean("localweather.devscene");

    private DevScene() {
    }

    public static void setUp(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        WeatherSavedData data = WeatherSavedData.get(level);
        data.cells.clear();
        double x = player.getX();
        double z = player.getZ();
        place(level, data, CellType.THUNDERSTORM, x + 1800, z, 1200);
        place(level, data, CellType.RAIN, x, z - 2600, 2200);
        place(level, data, CellType.SHOWER, x - 900, z + 300, 500);
        // A closer one to judge the clouds up close.
        place(level, data, CellType.THUNDERSTORM, x + 100, z + 1000, 600);

        var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000);
        WeatherSimulator.syncAll(level);
        LocalWeather.LOGGER.info("Dev scene: thunderstorms east and south, rain front north, shower west");
    }

    private static void place(ServerLevel level, WeatherSavedData data, CellType type, double x, double z, float radius) {
        var cell = WeatherSimulator.spawn(data, type, x, z, radius, level.random);
        cell.intensity = 1.0F;
        if (type == CellType.THUNDERSTORM) cell.thunder = 1.0F;
        // Middle of its life: fully formed and nowhere near fading.
        cell.age = cell.lifetime / 2;
    }
}
