package redero.localweather.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import redero.localweather.LocalWeather;
import redero.localweather.weather.CellType;

/**
 * Development only, enabled with -Dlocalweather.devscene=true (./gradlew runClient -PdevScene): puts storms
 * around the joining player and freezes time and weather, so the client can take comparable screenshots.
 */
public final class DevScene {
    private static final String MODE = System.getProperty("localweather.devscene");
    public static final boolean ENABLED = MODE != null;
    /** -PdevScene=inside: stand inside a storm, with the weather running instead of frozen. */
    public static final boolean INSIDE = "inside".equals(MODE);
    /** -PdevScene=above: the normal storms, seen from high above the clouds (spectator), looking down. */
    public static final boolean ABOVE = "above".equals(MODE);
    /** -PdevScene=ground: the normal storms, standing on the ground wherever the player was. */
    public static final boolean GROUND = "ground".equals(MODE);
    /**
     * -PdevScene=strafe: keeps the world's own weather, the player's position and view, and walks left and right
     * while saving a frame every tick (run/screenshots/strafe_NNN.png), to find flicker. See tools/flicker.py.
     */
    public static final boolean STRAFE = "strafe".equals(MODE);

    private DevScene() {
    }

    public static void setUp(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        WeatherSavedData data = WeatherSavedData.get(level);
        if (!STRAFE) data.cells.clear();
        double x = player.getX();
        double z = player.getZ();
        var server = level.getServer();
        if (STRAFE) {
            // Same picture every run: freeze the world's weather and time, keep everything else as saved.
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            WeatherSimulator.syncAll(level);
            LocalWeather.LOGGER.info("Dev scene: strafing with the world's own weather");
            return;
        }
        if (INSIDE) {
            // Rain core a few hundred blocks east: the player stands in the storm's drizzle and gloom.
            place(level, data, CellType.THUNDERSTORM, x + 700, z, 1100);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setDayTime(6000);
            WeatherSimulator.syncAll(level);
            LocalWeather.LOGGER.info("Dev scene: inside a thunderstorm, weather running");
            return;
        }
        place(level, data, CellType.THUNDERSTORM, x + 1800, z, 1200);
        place(level, data, CellType.RAIN, x, z - 2600, 2200);
        place(level, data, CellType.SHOWER, x - 900, z + 300, 500);
        // A closer one to judge the clouds up close.
        place(level, data, CellType.THUNDERSTORM, x + 100, z + 1000, 600);

        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000);
        if (GROUND) {
            // Load the chunk first: an unloaded chunk reports the bottom of the world as its surface.
            level.getChunkAt(BlockPos.containing(x, 0, z));
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            player.teleportTo(x, top + 1.0, z);
        }
        if (ABOVE) {
            // Above even raised clouds (Voxy + Tectonic preset: base ~352, towers to ~720).
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(x, 760.0, z);
        }
        WeatherSimulator.syncAll(level);
        LocalWeather.LOGGER.info("Dev scene: thunderstorms east and south, rain front north, shower west{}", ABOVE ? ", seen from above" : "");
    }

    private static void place(ServerLevel level, WeatherSavedData data, CellType type, double x, double z, float radius) {
        // Scaled like natural storms, so presets with bigger storms show their real cost.
        float scaled = (float) (radius * LocalWeatherServerConfig.stormSize());
        var cell = WeatherSimulator.spawn(data, type, x, z, scaled, level.random);
        cell.intensity = 1.0F;
        if (type == CellType.THUNDERSTORM) cell.thunder = 1.0F;
        // Middle of its life: fully formed and nowhere near fading.
        cell.age = cell.lifetime / 2;
    }
}
