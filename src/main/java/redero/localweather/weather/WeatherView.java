package redero.localweather.weather;

import java.util.List;

/** Read access to the storm cells of one dimension, on either side. */
public interface WeatherView {
    List<StormCell> cells();

    /** Ticks to extrapolate cell movement by; non-zero on the client between syncs. */
    default int aheadTicks() {
        return 0;
    }

    /** Overlapping cells combine like independent chances, so two light showers never exceed 1. */
    default float rainAt(double x, double z) {
        float dry = 1.0F;
        for (StormCell cell : cells()) {
            dry *= 1.0F - cell.rainAt(x, z, aheadTicks());
        }
        return 1.0F - dry;
    }

    default float thunderAt(double x, double z) {
        float thunder = 0.0F;
        for (StormCell cell : cells()) {
            thunder = Math.max(thunder, cell.thunderAt(x, z, aheadTicks()));
        }
        return thunder;
    }
}
