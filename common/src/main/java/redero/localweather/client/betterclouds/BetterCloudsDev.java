package redero.localweather.client.betterclouds;

import com.qendolin.betterclouds.clouds.Debug;

/** Dev scene only: turns on Better Clouds' own GPU/CPU timer, which logs averages every so many frames. */
public final class BetterCloudsDev {
    private BetterCloudsDev() {
    }

    public static void enableProfiling(int frames) {
        Debug.profileInterval = frames;
    }
}
