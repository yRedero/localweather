package redero.localweather.client.betterclouds;

import java.util.ArrayList;
import java.util.List;

import com.qendolin.betterclouds.config.Config;

import net.minecraft.util.Mth;
import redero.localweather.client.ClientWeather;
import redero.localweather.client.LocalWeatherClientConfig;
import redero.localweather.weather.StormCell;

/**
 * Decides when Better Clouds has to rebuild its clouds. It only regenerates when the camera moves to a
 * new cloud chunk or the global cloudiness changes, so moving storms need an extra nudge.
 */
public final class BetterCloudsCompat {
    /**
     * Better Clouds caches generated chunks. Cached chunks keep the level of detail they were made with, so the
     * cache is also cleared every time the camera crosses a square this big.
     */
    private static final double DETAIL_REFRESH = 1024.0;
    /** Rebuild once a storm has shifted this many blocks relative to the drifting clouds. */
    private static final double MOVE_STEP = 32.0;
    /** ...or its strength has changed by this much. */
    private static final float STRENGTH_STEPS = 10.0F;

    private static long lastSignature;

    private BetterCloudsCompat() {
    }

    /** Extra clouds (towers, decks, high layer) per base cloud the buffer leaves room for. */
    private static final double EXTRA_ROOM = 1.5;

    /**
     * Better Clouds sizes its buffer for one cloud on every grid point out to the cloud distance. With far
     * clouds most far points are skipped, so at 6 km that would reserve ~5x the memory ever used. This sizes it
     * for the points the level of detail can keep, plus room for stacked storm clouds.
     *
     * @param original Better Clouds' size before its bottom-sparsity doubling (the buffer holds size * size)
     */
    public static int bufferSize(Config options, int original) {
        int doubling = options.bottomSparsity > 0.0F ? 2 : 1;
        if (!farCloudsActive()) return (original > 0 ? original : 128) * doubling;
        double distance = options.blockDistance();
        double detail = Math.min(LocalWeatherClientConfig.DETAIL_DISTANCE.get(), distance);
        double coarse = Math.min(2.0 * detail, distance);
        double spacing = options.spacing;
        double points = Math.PI / (spacing * spacing)
                * (detail * detail + (coarse * coarse - detail * detail) / 4.0 + (distance * distance - coarse * coarse) / 16.0);
        // The dithered detail boundary keeps a few extra points; 10% covers it.
        int size = (int) Math.ceil(Math.sqrt(points * 1.1 * (1.0 + EXTRA_ROOM)));
        return Math.min(size, original > 0 ? original * 2 : 128) * doubling;
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
        if (farCloudsActive()) {
            signature = 31 * signature + Mth.floor(cameraX / DETAIL_REFRESH);
            signature = 31 * signature + Mth.floor(cameraZ / DETAIL_REFRESH);
        }
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
