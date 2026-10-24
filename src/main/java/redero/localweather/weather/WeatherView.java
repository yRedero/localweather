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

    /** How overcast the sky is, 0..1. Reaches past the rain. */
    default float cloudAt(double x, double z) {
        float cloud = 0.0F;
        for (StormCell cell : cells()) {
            cloud = Math.max(cloud, cell.cloudAt(x, z, aheadTicks()));
        }
        return cloud;
    }

    /**
     * Rain, thunder and cloud cover at once, for callers that need all three per point (cloud generation).
     * Looks the outline noise up once instead of once per cell and value.
     *
     * @param out receives rain, thunder, cloud
     */
    default void sample(double x, double z, float[] out) {
        float dry = 1.0F;
        float thunder = 0.0F;
        float cloud = 0.0F;
        boolean looked = false;
        double noiseX = 0.0, noiseZ = 0.0;
        for (StormCell cell : cells()) {
            if (!cell.reaches(x, z, aheadTicks())) continue;
            if (!looked) {
                noiseX = StormCell.shapeNoiseX(x, z);
                noiseZ = StormCell.shapeNoiseZ(x, z);
                looked = true;
            }
            double d = cell.relativeDistance(x, z, aheadTicks(), noiseX, noiseZ);
            dry *= 1.0F - cell.rainAtDistance(d, aheadTicks());
            thunder = Math.max(thunder, cell.thunderAtDistance(d, aheadTicks()));
            cloud = Math.max(cloud, cell.cloudAtDistance(d, aheadTicks()));
        }
        out[0] = 1.0F - dry;
        out[1] = thunder;
        out[2] = cloud;
    }

    default float thunderAt(double x, double z) {
        float thunder = 0.0F;
        for (StormCell cell : cells()) {
            thunder = Math.max(thunder, cell.thunderAt(x, z, aheadTicks()));
        }
        return thunder;
    }
}
