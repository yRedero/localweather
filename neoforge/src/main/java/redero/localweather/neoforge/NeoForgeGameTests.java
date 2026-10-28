package redero.localweather.neoforge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import redero.localweather.LocalWeather;
import redero.localweather.gametest.LocalWeatherTests;

/** Registers the shared game tests with NeoForge, see LocalWeatherTests. */
@GameTestHolder(LocalWeather.MODID)
@PrefixGameTestTemplate(false)
public final class NeoForgeGameTests {
    private NeoForgeGameTests() {
    }

    @GameTest(template = "empty", skyAccess = true)
    public static void rainOnlyUnderCells(GameTestHelper helper) {
        LocalWeatherTests.rainOnlyUnderCells(helper);
    }

    @GameTest(template = "empty", skyAccess = true)
    public static void dryAboveTheClouds(GameTestHelper helper) {
        LocalWeatherTests.dryAboveTheClouds(helper);
    }

    @GameTest(template = "empty")
    public static void cloudsCoverTheRain(GameTestHelper helper) {
        LocalWeatherTests.cloudsCoverTheRain(helper);
    }
}
