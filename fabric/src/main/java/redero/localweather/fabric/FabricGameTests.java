package redero.localweather.fabric;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import redero.localweather.gametest.LocalWeatherTests;

/** Registers the shared game tests with Fabric (the fabric-gametest entrypoint), see LocalWeatherTests. */
public final class FabricGameTests {
    private static final String EMPTY = "localweather:empty";

    @GameTest(template = EMPTY, skyAccess = true)
    public void rainOnlyUnderCells(GameTestHelper helper) {
        LocalWeatherTests.rainOnlyUnderCells(helper);
    }

    @GameTest(template = EMPTY, skyAccess = true)
    public void dryAboveTheClouds(GameTestHelper helper) {
        LocalWeatherTests.dryAboveTheClouds(helper);
    }

    @GameTest(template = EMPTY)
    public void cloudsCoverTheRain(GameTestHelper helper) {
        LocalWeatherTests.cloudsCoverTheRain(helper);
    }
}
