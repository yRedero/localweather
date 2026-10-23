package redero.localweather.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for how Local Weather changes Better Clouds. Editable in the mod list's config screen. */
public final class LocalWeatherClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue CLOUD_COVER;
    public static final ModConfigSpec.BooleanValue FAR_CLOUDS;
    public static final ModConfigSpec.IntValue CLOUD_DISTANCE;
    public static final ModConfigSpec.IntValue DETAIL_DISTANCE;
    public static final ModConfigSpec.BooleanValue STORM_TOWERS;
    public static final ModConfigSpec.BooleanValue STORM_DARKNESS;
    public static final ModConfigSpec.BooleanValue THICK_RAIN_CLOUDS;
    public static final ModConfigSpec.BooleanValue HIGH_LAYER;
    public static final ModConfigSpec.IntValue HIGH_LAYER_HEIGHT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Changes to Better Clouds. Everything here needs Better Clouds installed.").push("betterclouds");

        CLOUD_COVER = b.comment("How cloudy the sky is in fair weather. Better Clouds' own value is 0.5; storms add on top.")
                .defineInRange("cloudCover", 0.62, 0.2, 0.95);
        FAR_CLOUDS = b.comment("Draw clouds further than Better Clouds allows, with less detail far away.")
                .define("farClouds", true);
        CLOUD_DISTANCE = b.comment("How far clouds are drawn, in blocks. Better Clouds' own limit is 4x your render distance.",
                        "Higher values cost generation time and GPU time.")
                .defineInRange("cloudDistance", 2560, 512, 6144);
        DETAIL_DISTANCE = b.comment("Clouds are drawn at full detail up to this distance. Beyond it every 2nd cloud is drawn",
                        "at twice the size, and beyond twice this distance every 4th cloud at four times the size.")
                .defineInRange("detailDistance", 1536, 256, 4096);

        STORM_TOWERS = b.comment("Thunderstorms build tall towers of cloud with a flat anvil on top.")
                .define("stormTowers", true);
        STORM_DARKNESS = b.comment("Clouds inside storms are darker, so you can see storms from far away.")
                .define("stormDarkness", true);
        THICK_RAIN_CLOUDS = b.comment("Rain makes the cloud layer thicker.")
                .define("thickRainClouds", true);
        HIGH_LAYER = b.comment("A second, high and flat cloud layer above the normal clouds. It gets denser near storms.")
                .define("highLayer", true);
        HIGH_LAYER_HEIGHT = b.comment("How far above the normal clouds the high layer floats, in blocks.")
                .defineInRange("highLayerHeight", 160, 40, 600);

        b.pop();
        SPEC = b.build();
    }

    private LocalWeatherClientConfig() {
    }

    /** Hash of every setting that changes cloud geometry, so a change triggers a rebuild. */
    public static int geometryHash() {
        if (!SPEC.isLoaded()) return 0;
        int h = Boolean.hashCode(FAR_CLOUDS.get());
        h = 31 * h + Double.hashCode(CLOUD_COVER.get());
        h = 31 * h + CLOUD_DISTANCE.get();
        h = 31 * h + DETAIL_DISTANCE.get();
        h = 31 * h + Boolean.hashCode(STORM_TOWERS.get());
        h = 31 * h + Boolean.hashCode(STORM_DARKNESS.get());
        h = 31 * h + Boolean.hashCode(THICK_RAIN_CLOUDS.get());
        h = 31 * h + Boolean.hashCode(HIGH_LAYER.get());
        h = 31 * h + HIGH_LAYER_HEIGHT.get();
        return h;
    }
}
