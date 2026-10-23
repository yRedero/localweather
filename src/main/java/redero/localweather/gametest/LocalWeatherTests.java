package redero.localweather.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import redero.localweather.LocalWeather;
import redero.localweather.server.WeatherSavedData;
import redero.localweather.server.WeatherSimulator;
import redero.localweather.weather.CellType;
import redero.localweather.weather.StormCell;

/** Run with ./gradlew runGameTestServer. Global rain stays at zero, so anything that gets wet does so locally. */
@GameTestHolder(LocalWeather.MODID)
@PrefixGameTestTemplate(false)
public final class LocalWeatherTests {
    private LocalWeatherTests() {
    }

    @GameTest(template = "empty", skyAccess = true)
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
}
