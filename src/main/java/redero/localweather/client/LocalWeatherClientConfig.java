package redero.localweather.client;

import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import redero.localweather.LocalWeather;
import redero.localweather.PresetMarker;

/**
 * Client settings for how Local Weather changes Better Clouds. Editable in the mod list's config screen.
 * Picking a preset fills in every value below it; they can be tweaked afterwards.
 */
public final class LocalWeatherClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<Preset> PRESET;

    public static final ModConfigSpec.DoubleValue CLOUD_COVER;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT_OFFSET;
    public static final ModConfigSpec.BooleanValue FAR_CLOUDS;
    public static final ModConfigSpec.IntValue CLOUD_DISTANCE;
    public static final ModConfigSpec.IntValue DETAIL_DISTANCE;

    public static final ModConfigSpec.BooleanValue STORM_TOWERS;
    public static final ModConfigSpec.IntValue TOWER_HEIGHT;
    public static final ModConfigSpec.BooleanValue STORM_DARKNESS;
    public static final ModConfigSpec.DoubleValue DARKNESS_STRENGTH;
    public static final ModConfigSpec.BooleanValue THICK_RAIN_CLOUDS;

    public static final ModConfigSpec.BooleanValue HIGH_LAYER;
    public static final ModConfigSpec.IntValue HIGH_LAYER_HEIGHT;

    public static final ModConfigSpec.BooleanValue NO_RAIN_ABOVE_CLOUDS;
    public static final ModConfigSpec.DoubleValue SKY_GLOOM;
    public static final ModConfigSpec.DoubleValue GLOBAL_TINT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        PRESET = b.comment("Fills in all settings below when changed. Afterwards each setting can still be tweaked.",
                        "DEFAULT: balanced. PERFORMANCE: shorter view, fewer extras. CINEMATIC: far view, tall storms.",
                        "VOXY_TECTONIC: for very tall terrain (Tectonic) and extreme render distances (Voxy, Distant Horizons):",
                        "clouds ~160 blocks higher, drawn out to 6 km, taller storms. CUSTOM: never changes anything.")
                .defineEnum("preset", Preset.DEFAULT);

        b.comment("The cloud layer. Everything here needs Better Clouds installed.").push("clouds");
        CLOUD_COVER = b.comment("How cloudy the sky is in fair weather. Better Clouds' own value is 0.5; storms add on top.")
                .defineInRange("cloudCover", 0.62, 0.2, 0.95);
        CLOUD_HEIGHT_OFFSET = b.comment("Moves all clouds up (or down) from the dimension's cloud height (192 in the overworld), in blocks.",
                        "Raise it for terrain mods with tall mountains, e.g. +160 for Tectonic.")
                .defineInRange("cloudHeightOffset", 0, -128, 1024);
        FAR_CLOUDS = b.comment("Draw clouds further than Better Clouds allows, with less detail far away.")
                .define("farClouds", true);
        CLOUD_DISTANCE = b.comment("How far clouds are drawn, in blocks. Better Clouds' own limit is 4x your render distance.",
                        "Higher values cost generation time and GPU time.")
                .defineInRange("cloudDistance", 2560, 512, 8192);
        DETAIL_DISTANCE = b.comment("Clouds are drawn at full detail up to this distance. Beyond it every 2nd cloud is drawn",
                        "at twice the size, and beyond twice this distance every 4th cloud at four times the size.")
                .defineInRange("detailDistance", 1536, 256, 4096);
        b.pop();

        b.comment("How storms look.").push("storms");
        STORM_TOWERS = b.comment("Thunderstorms build tall towers of cloud with a flat anvil on top.")
                .define("stormTowers", true);
        TOWER_HEIGHT = b.comment("Height of the tallest thunderstorm towers above the cloud layer, in blocks. The anvil sits on top.")
                .defineInRange("towerHeight", 240, 40, 800);
        STORM_DARKNESS = b.comment("Clouds inside storms are darker, so you can see storms from far away.")
                .define("stormDarkness", true);
        DARKNESS_STRENGTH = b.comment("How dark storm clouds get, 0 (not at all) to 1 (full).")
                .defineInRange("darknessStrength", 1.0, 0.0, 1.0);
        THICK_RAIN_CLOUDS = b.comment("Rain makes the cloud layer thicker and hang a little lower.")
                .define("thickRainClouds", true);
        b.pop();

        b.comment("A second, flat cloud layer high above the normal clouds.").push("highLayer");
        HIGH_LAYER = b.comment("Draw the high layer. It gets denser near storms.")
                .define("highLayer", true);
        HIGH_LAYER_HEIGHT = b.comment("How far above the normal clouds the high layer floats, in blocks.")
                .defineInRange("highLayerHeight", 160, 40, 1000);
        b.pop();

        b.comment("Rain.").push("rain");
        NO_RAIN_ABOVE_CLOUDS = b.comment("No rain, rain sound or storm darkness while you are above the cloud base.",
                        "Only affects what you see and hear: the world still counts it as raining up there.")
                .define("noRainAboveClouds", true);
        b.pop();

        b.comment("Sky and lighting.").push("sky");
        SKY_GLOOM = b.comment("How much the sky and fog grey over as a storm's clouds build up, 0 to 1.")
                .defineInRange("skyGloom", 1.0, 0.0, 1.0);
        GLOBAL_TINT = b.comment("Better Clouds darkens every cloud by the rain where you stand. Storm clouds are darkened",
                        "one by one instead, so only this share of that is kept, 0 to 1. Better Clouds' own behaviour is 1.")
                .defineInRange("globalRainTint", 0.35, 0.0, 1.0);
        b.pop();

        SPEC = b.build();
    }

    private LocalWeatherClientConfig() {
    }

    /** Called when the config file is loaded or changed: fills in the preset's values if the preset changed. */
    public static void sync(ModConfig config) {
        Preset preset = PRESET.get();
        if (!PresetMarker.changed(config, preset.name(), Preset.DEFAULT.name()) || preset == Preset.CUSTOM) return;
        preset.apply();
        SPEC.save();
        LocalWeather.LOGGER.info("Applied client preset {}", preset);
    }

    /** Hash of every setting that changes cloud geometry, so a change triggers a rebuild. */
    public static int geometryHash() {
        if (!SPEC.isLoaded()) return 0;
        int h = Boolean.hashCode(FAR_CLOUDS.get());
        h = 31 * h + Double.hashCode(CLOUD_COVER.get());
        h = 31 * h + CLOUD_DISTANCE.get();
        h = 31 * h + DETAIL_DISTANCE.get();
        h = 31 * h + Boolean.hashCode(STORM_TOWERS.get());
        h = 31 * h + TOWER_HEIGHT.get();
        h = 31 * h + Boolean.hashCode(STORM_DARKNESS.get());
        h = 31 * h + Double.hashCode(DARKNESS_STRENGTH.get());
        h = 31 * h + Boolean.hashCode(THICK_RAIN_CLOUDS.get());
        h = 31 * h + Boolean.hashCode(HIGH_LAYER.get());
        h = 31 * h + HIGH_LAYER_HEIGHT.get();
        return h;
    }

    public enum Preset {
        DEFAULT, PERFORMANCE, CINEMATIC, VOXY_TECTONIC, CUSTOM;

        @SuppressWarnings("unchecked")
        void apply() {
            presetValues(this).forEach((key, value) -> ((ModConfigSpec.ConfigValue<Object>) key).set(value));
        }
    }

    /** Built on demand: the config values don't exist yet while the enum is first loaded. */
    private static Map<ModConfigSpec.ConfigValue<?>, Object> presetValues(Preset preset) {
        return switch (preset) {
            case DEFAULT -> Map.ofEntries(
                    Map.entry(CLOUD_COVER, 0.62), Map.entry(CLOUD_HEIGHT_OFFSET, 0), Map.entry(FAR_CLOUDS, true),
                    Map.entry(CLOUD_DISTANCE, 2560), Map.entry(DETAIL_DISTANCE, 1536),
                    Map.entry(STORM_TOWERS, true), Map.entry(TOWER_HEIGHT, 240), Map.entry(STORM_DARKNESS, true),
                    Map.entry(DARKNESS_STRENGTH, 1.0), Map.entry(THICK_RAIN_CLOUDS, true),
                    Map.entry(HIGH_LAYER, true), Map.entry(HIGH_LAYER_HEIGHT, 160),
                    Map.entry(NO_RAIN_ABOVE_CLOUDS, true), Map.entry(SKY_GLOOM, 1.0), Map.entry(GLOBAL_TINT, 0.35));
            case PERFORMANCE -> Map.ofEntries(
                    Map.entry(CLOUD_COVER, 0.6), Map.entry(CLOUD_HEIGHT_OFFSET, 0), Map.entry(FAR_CLOUDS, true),
                    Map.entry(CLOUD_DISTANCE, 1536), Map.entry(DETAIL_DISTANCE, 768),
                    Map.entry(STORM_TOWERS, true), Map.entry(TOWER_HEIGHT, 160), Map.entry(STORM_DARKNESS, true),
                    Map.entry(DARKNESS_STRENGTH, 1.0), Map.entry(THICK_RAIN_CLOUDS, false),
                    Map.entry(HIGH_LAYER, false), Map.entry(HIGH_LAYER_HEIGHT, 160),
                    Map.entry(NO_RAIN_ABOVE_CLOUDS, true), Map.entry(SKY_GLOOM, 1.0), Map.entry(GLOBAL_TINT, 0.35));
            case CINEMATIC -> Map.ofEntries(
                    Map.entry(CLOUD_COVER, 0.66), Map.entry(CLOUD_HEIGHT_OFFSET, 0), Map.entry(FAR_CLOUDS, true),
                    Map.entry(CLOUD_DISTANCE, 4096), Map.entry(DETAIL_DISTANCE, 2048),
                    Map.entry(STORM_TOWERS, true), Map.entry(TOWER_HEIGHT, 320), Map.entry(STORM_DARKNESS, true),
                    Map.entry(DARKNESS_STRENGTH, 1.0), Map.entry(THICK_RAIN_CLOUDS, true),
                    Map.entry(HIGH_LAYER, true), Map.entry(HIGH_LAYER_HEIGHT, 200),
                    Map.entry(NO_RAIN_ABOVE_CLOUDS, true), Map.entry(SKY_GLOOM, 1.0), Map.entry(GLOBAL_TINT, 0.3));
            // Tectonic's mountains reach towards the 320 build limit, so clouds start above them.
            case VOXY_TECTONIC -> Map.ofEntries(
                    Map.entry(CLOUD_COVER, 0.64), Map.entry(CLOUD_HEIGHT_OFFSET, 160), Map.entry(FAR_CLOUDS, true),
                    Map.entry(CLOUD_DISTANCE, 6144), Map.entry(DETAIL_DISTANCE, 2048),
                    Map.entry(STORM_TOWERS, true), Map.entry(TOWER_HEIGHT, 360), Map.entry(STORM_DARKNESS, true),
                    Map.entry(DARKNESS_STRENGTH, 1.0), Map.entry(THICK_RAIN_CLOUDS, true),
                    Map.entry(HIGH_LAYER, true), Map.entry(HIGH_LAYER_HEIGHT, 280),
                    Map.entry(NO_RAIN_ABOVE_CLOUDS, true), Map.entry(SKY_GLOOM, 1.0), Map.entry(GLOBAL_TINT, 0.3));
            case CUSTOM -> Map.of();
        };
    }
}
