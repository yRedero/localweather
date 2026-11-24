package redero.localweather.client;

import static redero.localweather.client.LocalWeatherClientConfig.*;

import java.util.function.Function;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client settings as one immutable snapshot: what the rest of the code reads instead of the config values. It
 * holds the defaults until the config file is loaded and is rebuilt whenever the file is loaded or changed, so
 * nothing else has to check whether the config is there yet.
 */
public record ClientSettings(
        double cloudCover, boolean farClouds, double cloudResolution, int cloudDistance, int detailDistance,
        boolean highLayer, int highLayerHeight, boolean cumulusClouds,
        boolean followServerCloudHeight, int cloudHeightOffset,
        boolean stormTowers, int towerHeight, boolean stormDarkness, double darknessStrength, boolean thickRainClouds,
        boolean noRainAboveClouds, boolean rainShafts, double rainShaftStrength, int rainShaftDistance,
        double rainHaze, double rainFog,
        boolean lightningFlashes, boolean lightningBolts, boolean distantThunder,
        double skyGloom, double globalRainTint, boolean overcastSky, boolean cloudsHideSky) {

    private static volatile ClientSettings current = read(ModConfigSpec.ConfigValue::getDefault);

    public static ClientSettings get() {
        return current;
    }

    /** Re-reads the config; called when the file is loaded or changed. */
    static void refresh() {
        if (SPEC.isLoaded()) current = read(ModConfigSpec.ConfigValue::get);
    }

    private static ClientSettings read(Function<ModConfigSpec.ConfigValue<?>, Object> value) {
        return new ClientSettings(
                (Double) value.apply(CLOUD_COVER), (Boolean) value.apply(FAR_CLOUDS), (Double) value.apply(CLOUD_RESOLUTION),
                (Integer) value.apply(CLOUD_DISTANCE), (Integer) value.apply(DETAIL_DISTANCE),
                (Boolean) value.apply(HIGH_LAYER), (Integer) value.apply(HIGH_LAYER_HEIGHT), (Boolean) value.apply(CUMULUS_CLOUDS),
                (Boolean) value.apply(FOLLOW_SERVER_CLOUD_HEIGHT), (Integer) value.apply(CLOUD_HEIGHT_OFFSET),
                (Boolean) value.apply(STORM_TOWERS), (Integer) value.apply(TOWER_HEIGHT), (Boolean) value.apply(STORM_DARKNESS),
                (Double) value.apply(DARKNESS_STRENGTH), (Boolean) value.apply(THICK_RAIN_CLOUDS),
                (Boolean) value.apply(NO_RAIN_ABOVE_CLOUDS), (Boolean) value.apply(RAIN_SHAFTS), (Double) value.apply(RAIN_SHAFT_STRENGTH),
                (Integer) value.apply(RAIN_SHAFT_DISTANCE), (Double) value.apply(RAIN_HAZE), (Double) value.apply(RAIN_FOG),
                (Boolean) value.apply(LIGHTNING_FLASHES), (Boolean) value.apply(LIGHTNING_BOLTS), (Boolean) value.apply(DISTANT_THUNDER),
                (Double) value.apply(SKY_GLOOM), (Double) value.apply(GLOBAL_TINT), (Boolean) value.apply(OVERCAST_SKY),
                (Boolean) value.apply(CLOUDS_HIDE_SKY));
    }

    /** How far rain shafts and haze reach: their own distance, never past the clouds. */
    public int weatherDistance() {
        return Math.min(rainShaftDistance, cloudDistance);
    }

    /** Hash of everything that changes cloud geometry, so changing one rebuilds the clouds. */
    public int geometryHash() {
        int h = Boolean.hashCode(farClouds);
        h = 31 * h + Double.hashCode(cloudCover);
        h = 31 * h + cloudDistance;
        h = 31 * h + detailDistance;
        h = 31 * h + Boolean.hashCode(highLayer);
        h = 31 * h + highLayerHeight;
        h = 31 * h + Boolean.hashCode(cumulusClouds);
        h = 31 * h + Boolean.hashCode(stormTowers);
        h = 31 * h + towerHeight;
        h = 31 * h + Boolean.hashCode(stormDarkness);
        h = 31 * h + Double.hashCode(darknessStrength);
        h = 31 * h + Boolean.hashCode(thickRainClouds);
        return h;
    }
}
