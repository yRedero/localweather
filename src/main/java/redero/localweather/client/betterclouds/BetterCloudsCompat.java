package redero.localweather.client.betterclouds;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Mth;
import redero.localweather.client.ClientWeather;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.weather.StormCell;

/**
 * Decides when Better Clouds has to rebuild its clouds. It only regenerates when the camera moves to a
 * new cloud chunk or the global cloudiness changes, so moving storms need an extra nudge.
 */
public final class BetterCloudsCompat {
    /** Rebuild once a storm has shifted this many blocks relative to the drifting clouds. */
    private static final double MOVE_STEP = 32.0;
    /** ...or its strength has changed by this much. */
    private static final float STRENGTH_STEPS = 25.0F;

    private static long lastSignature;

    private BetterCloudsCompat() {
    }

    /** True when clouds are generated past Better Clouds' own distance (see ConfigMixin). */
    public static boolean farCloudsActive() {
        return LocalWeatherClientConfig.SPEC.isLoaded() && LocalWeatherClientConfig.FAR_CLOUDS.get()
                && BetterCloudsShaderPatch.active();
    }

    /** Called once per frame from ChunkedGenerator#update, on the render thread. */
    public static boolean fieldChanged(double cameraX, double cameraZ, double originX, double originZ, int blockDistance) {
        // Settings and the shader patch change geometry too.
        long signature = 31L * LocalWeatherClientConfig.geometryHash() + Boolean.hashCode(BetterCloudsShaderPatch.active());
        ClientWeather weather = ClientWeather.INSTANCE;
        if (weather.active()) {
            int ahead = weather.aheadTicks();
            for (StormCell cell : cellsInView(cameraX, cameraZ, blockDistance)) {
                float envelope = cell.envelope(ahead);
                signature = 31 * signature + cell.id;
                signature = 31 * signature + Mth.floor((cell.centerX(ahead) - originX) / MOVE_STEP);
                signature = 31 * signature + Mth.floor((cell.centerZ(ahead) - originZ) / MOVE_STEP);
                signature = 31 * signature + Math.round(cell.intensity * envelope * STRENGTH_STEPS);
                signature = 31 * signature + Math.round(cell.thunder * envelope * STRENGTH_STEPS);
            }
        }
        boolean changed = signature != lastSignature;
        lastSignature = signature;
        return changed;
    }

    /** Cells that can reach into the cloud render distance and haven't faded out. */
    static List<StormCell> cellsInView(double cameraX, double cameraZ, int blockDistance) {
        ClientWeather weather = ClientWeather.INSTANCE;
        int ahead = weather.aheadTicks();
        List<StormCell> result = new ArrayList<>();
        for (StormCell cell : weather.cells()) {
            // A storm's cloud build-up and outline noise reach about 3.3 radii from the centre.
            double reach = blockDistance + cell.radius * 3.3;
            double dx = cell.centerX(ahead) - cameraX;
            double dz = cell.centerZ(ahead) - cameraZ;
            if (dx * dx + dz * dz < reach * reach && cell.envelope(ahead) > 0.0F) result.add(cell);
        }
        return result;
    }
}
