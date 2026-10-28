package redero.localweather.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import redero.localweather.server.WeatherSavedData;
import redero.localweather.server.WeatherSimulator;
import redero.localweather.weather.CellType;
import redero.localweather.weather.LocalWeatherApi;
import redero.localweather.weather.StormCell;

/**
 * The game tests, registered by each loader's own test class (the annotations differ). Run with
 * ./gradlew :neoforge:runGameTestServer or :fabric:runGametest. Global rain stays at zero, so anything that gets
 * wet does so locally.
 */
public final class LocalWeatherTests {
    private LocalWeatherTests() {
    }

    public static void rainOnlyUnderCells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WeatherSavedData data = WeatherSavedData.get(level);
        data.cells().clear();

        BlockPos cauldronRel = new BlockPos(1, 1, 1);
        helper.setBlock(cauldronRel, Blocks.CAULDRON);
        BlockPos cauldron = helper.absolutePos(cauldronRel);
        BlockPos above = cauldron.above();

        helper.assertFalse(level.isRaining(), "global rain should be off in the test world");
        helper.assertFalse(level.isRainingAt(above), "rain with no storm cells");
        for (int i = 0; i < 400; i++) level.tickPrecipitation(cauldron);
        helper.assertBlockPresent(Blocks.CAULDRON, cauldronRel);

        StormCell cell = WeatherSimulator.spawn(data, CellType.RAIN, above.getX(), above.getZ(), 100.0F, level.random);
        cell.intensity = 1.0F;
        cell.age = cell.lifetime / 2;

        helper.assertTrue(level.isRainingAt(above), "no rain under a mature cell");
        helper.assertFalse(level.isRainingAt(above.east(150)), "rain outside the cell radius");
        helper.assertTrue(data.thunderAt(above.getX(), above.getZ()) < 0.5F, "plain rain cell should not allow lightning");
        // Rain fills an empty cauldron with a 5% chance per call.
        for (int i = 0; i < 400; i++) level.tickPrecipitation(cauldron);
        helper.assertBlockPresent(Blocks.WATER_CAULDRON, cauldronRel);

        data.cells().clear();
        helper.succeed();
    }

    /** Above the cloud base (192 + the world's offset, 0 by default) it's dry; below it rains. */
    public static void dryAboveTheClouds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WeatherSavedData data = WeatherSavedData.get(level);
        data.cells().clear();
        BlockPos column = helper.absolutePos(new BlockPos(1, 1, 1));
        StormCell cell = WeatherSimulator.spawn(data, CellType.RAIN, column.getX(), column.getZ(), 100.0F, level.random);
        cell.intensity = 1.0F;
        cell.age = cell.lifetime / 2;

        helper.assertTrue(level.isRainingAt(column.atY(150)), "no rain below the cloud base");
        helper.assertFalse(level.isRainingAt(column.atY(200)), "rain above the cloud base");

        data.cells().clear();
        helper.succeed();
    }

    /** Clouds and rain come from the same storm shape: wherever it rains, the sky above is heavily overcast. */
    public static void cloudsCoverTheRain(GameTestHelper helper) {
        WeatherSavedData data = new WeatherSavedData();
        StormCell cell = WeatherSimulator.spawn(data, CellType.RAIN, 0.0, 0.0, 600.0F, helper.getLevel().random);
        cell.intensity = 1.0F;
        cell.age = cell.lifetime / 2;

        int raining = 0;
        for (int i = 0; i < 2000; i++) {
            double x = (helper.getLevel().random.nextDouble() * 2.0 - 1.0) * 1100.0;
            double z = (helper.getLevel().random.nextDouble() * 2.0 - 1.0) * 1100.0;
            if (data.rainAt(x, z) > LocalWeatherApi.RAIN_THRESHOLD) {
                raining++;
                float cloud = data.cloudAt(x, z);
                helper.assertTrue(cloud > 0.5F, "rain at " + (int) x + ", " + (int) z + " under thin cloud " + cloud);
            }
        }
        helper.assertTrue(raining > 100, "too few raining samples: " + raining);
        helper.succeed();
    }
}
