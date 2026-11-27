package redero.localweather.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import redero.localweather.LocalWeather;
import redero.localweather.PresetMarker;

/**
 * Client settings (config/localweather-client.toml): how Local Weather draws the sky. Editable in the mod list's
 * config screen. The code reads them through {@link ClientSettings}, never from here.
 *
 * <p>A preset resets every setting to its default and then applies its own differences; afterwards each setting
 * can still be tweaked. Files from before 0.1.21 kept some settings in other sections, see ClientConfigMigration.
 */
public final class LocalWeatherClientConfig {
    public static final ModConfigSpec SPEC;
    /** Every setting below the preset, so a preset can reset them all. */
    private static final List<ModConfigSpec.ConfigValue<?>> ALL = new ArrayList<>();

    public static final ModConfigSpec.EnumValue<Preset> PRESET;

    public static final ModConfigSpec.DoubleValue CLOUD_COVER;
    public static final ModConfigSpec.BooleanValue FAR_CLOUDS;
    public static final ModConfigSpec.DoubleValue CLOUD_RESOLUTION;
    public static final ModConfigSpec.IntValue CLOUD_DISTANCE;
    public static final ModConfigSpec.IntValue DETAIL_DISTANCE;
    public static final ModConfigSpec.BooleanValue HIGH_LAYER;
    public static final ModConfigSpec.IntValue HIGH_LAYER_HEIGHT;
    public static final ModConfigSpec.BooleanValue CUMULUS_CLOUDS;

    public static final ModConfigSpec.BooleanValue FOLLOW_SERVER_CLOUD_HEIGHT;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT_OFFSET;

    public static final ModConfigSpec.BooleanValue STORM_TOWERS;
    public static final ModConfigSpec.IntValue TOWER_HEIGHT;
    public static final ModConfigSpec.BooleanValue STORM_DARKNESS;
    public static final ModConfigSpec.DoubleValue DARKNESS_STRENGTH;
    public static final ModConfigSpec.BooleanValue THICK_RAIN_CLOUDS;

    public static final ModConfigSpec.BooleanValue NO_RAIN_ABOVE_CLOUDS;
    public static final ModConfigSpec.BooleanValue RAIN_SHAFTS;
    public static final ModConfigSpec.DoubleValue RAIN_SHAFT_STRENGTH;
    public static final ModConfigSpec.IntValue RAIN_SHAFT_DISTANCE;
    public static final ModConfigSpec.DoubleValue RAIN_HAZE;
    public static final ModConfigSpec.DoubleValue RAIN_FOG;

    public static final ModConfigSpec.BooleanValue LIGHTNING_FLASHES;
    public static final ModConfigSpec.BooleanValue LIGHTNING_BOLTS;
    public static final ModConfigSpec.BooleanValue DISTANT_THUNDER;
    public static final ModConfigSpec.BooleanValue THUNDER_FOLLOWS_RENDER_DISTANCE;
    public static final ModConfigSpec.IntValue THUNDER_DISTANCE;
    public static final ModConfigSpec.DoubleValue THUNDER_VOLUME;

    public static final ModConfigSpec.DoubleValue SKY_GLOOM;
    public static final ModConfigSpec.DoubleValue GLOBAL_TINT;
    public static final ModConfigSpec.BooleanValue OVERCAST_SKY;
    public static final ModConfigSpec.BooleanValue CLOUDS_HIDE_SKY;

    /** Section of every setting, in file order (ClientConfigMigration moves old files to these). */
    public static final Map<String, String> SECTIONS = new LinkedHashMap<>();

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        PRESET = b.comment("Resets every setting below to its default and applies the preset's own changes. Afterwards each",
                        "setting can still be tweaked. DEFAULT: balanced. PERFORMANCE: shorter view, fewer extras.",
                        "CINEMATIC: far view, tall storms. VOXY_TECTONIC: for very tall terrain (Tectonic) and extreme render",
                        "distances (Voxy, Distant Horizons): drawn out to 6 km, taller storms; pair it with the world config's",
                        "heightPreset AUTO and weatherPreset HUGE_STORMS. CUSTOM: never changes anything.")
                .defineEnum("preset", Preset.DEFAULT);

        section(b, "clouds", "The cloud layer. Everything here needs Better Clouds installed.");
        CLOUD_COVER = add(b, "clouds", b.comment("How cloudy fair weather is, used when the world's weather regions are off (with them the",
                        "regions decide). Better Clouds' own value is 0.5; storms add on top.")
                .defineInRange("cloudCover", 0.62, 0.2, 0.95));
        FAR_CLOUDS = add(b, "clouds", b.comment("Draw clouds further than Better Clouds allows, with less detail far away.")
                .define("farClouds", true));
        CLOUD_RESOLUTION = add(b, "clouds", b.comment("Resolution clouds are drawn at, as a share of the screen's (on top of Better Clouds' own",
                        "upscale setting). Clouds are soft, so 0.6-0.75 is hard to tell apart and saves a lot of GPU time.")
                .defineInRange("cloudResolution", 0.75, 0.25, 1.0));
        CLOUD_DISTANCE = add(b, "clouds", b.comment("How far clouds are drawn, in blocks. Better Clouds' own limit is 4x your render distance.",
                        "Higher values cost generation time and GPU time.")
                .defineInRange("cloudDistance", 2560, 512, 8192));
        DETAIL_DISTANCE = add(b, "clouds", b.comment("Full detail up to this distance, in blocks. Beyond it every 2nd cloud is drawn at twice",
                        "the size, and beyond twice this distance every 4th cloud at four times the size.")
                .defineInRange("detailDistance", 1536, 256, 4096));
        HIGH_LAYER = add(b, "clouds", b.comment("A second, flat cloud layer high above the normal clouds. It gets denser near storms.")
                .define("highLayer", true));
        HIGH_LAYER_HEIGHT = add(b, "clouds", b.comment("How far above the normal clouds the high layer floats, in blocks.")
                .defineInRange("highLayerHeight", 160, 40, 1000));
        CUMULUS_CLOUDS = add(b, "clouds", b.comment("Now and then a big white cumulus heap builds up in clear to fair weather, grows and falls",
                        "apart again over about half a day.")
                .define("cumulusClouds", true));
        b.pop();

        section(b, "cloudHeight", "Where the clouds sit.");
        FOLLOW_SERVER_CLOUD_HEIGHT = add(b, "cloudHeight", b.comment("On a world or server with Local Weather, put the clouds where its config says (they mark",
                        "where its rain starts). Turn off to use cloudHeightOffset below everywhere.")
                .define("followServerCloudHeight", true));
        CLOUD_HEIGHT_OFFSET = add(b, "cloudHeight", b.comment("Moves all clouds up (or down) from the dimension's cloud height (192 in the overworld), in",
                        "blocks. Used on servers without Local Weather, or with followServerCloudHeight off.")
                .defineInRange("cloudHeightOffset", 0, -128, 1024));
        b.pop();

        section(b, "storms", "How storm clouds look.");
        STORM_TOWERS = add(b, "storms", b.comment("Thunderstorms build tall towers of cloud with a flat anvil on top.")
                .define("stormTowers", true));
        TOWER_HEIGHT = add(b, "storms", b.comment("Height of the tallest thunderstorm towers above the cloud layer, in blocks. The anvil sits on top.")
                .defineInRange("towerHeight", 240, 40, 800));
        STORM_DARKNESS = add(b, "storms", b.comment("Clouds inside storms are darker, so you can see storms from far away.")
                .define("stormDarkness", true));
        DARKNESS_STRENGTH = add(b, "storms", b.comment("How dark storm clouds get, 0 (not at all) to 1 (full).")
                .defineInRange("darknessStrength", 1.0, 0.0, 1.0));
        THICK_RAIN_CLOUDS = add(b, "storms", b.comment("Rain makes the cloud layer thicker, lumpier underneath and hang a little lower.")
                .define("thickRainClouds", true));
        b.pop();

        section(b, "rain", "Rain near and far.");
        NO_RAIN_ABOVE_CLOUDS = add(b, "rain", b.comment("No rain, rain sound or storm darkness while you are above the cloud base.",
                        "Only affects what you see and hear: the world still counts it as raining up there.")
                .define("noRainAboveClouds", true));
        RAIN_SHAFTS = add(b, "rain", b.comment("Dark veils of rain hang under storms further away, so you can see where it rains hard.")
                .define("rainShafts", true));
        RAIN_SHAFT_STRENGTH = add(b, "rain", b.comment("How dense the rain shafts look, 0.2 (faint) to 2 (heavy).")
                .defineInRange("rainShaftStrength", 1.0, 0.2, 2.0));
        RAIN_SHAFT_DISTANCE = add(b, "rain", b.comment("How far away rain shafts and haze are drawn, in blocks (never further than the clouds).")
                .defineInRange("rainShaftDistance", 2560, 512, 8192));
        RAIN_HAZE = add(b, "rain", b.comment("Grey air below the clouds wherever it rains, thicker in heavy rain and thunder.",
                        "0 turns it off, 1 is normal, 2 is thick.")
                .defineInRange("rainHaze", 1.0, 0.0, 2.0));
        RAIN_FOG = add(b, "rain", b.comment("Fog closes in around you while you stand in rain, more in heavy rain. 0 turns it off, 1 is thickest.")
                .defineInRange("rainFog", 0.6, 0.0, 1.0));
        b.pop();

        section(b, "lightning", "Lightning and thunder.");
        LIGHTNING_FLASHES = add(b, "lightning", b.comment("Lightning lights up the storm clouds around it, and thunderstorms flash inside their clouds",
                        "now and then, so you can see far storms flicker.")
                .define("lightningFlashes", true));
        LIGHTNING_BOLTS = add(b, "lightning", b.comment("Lightning is drawn as Local Weather's jagged, branching bolts (real strikes and some of the",
                        "flashes in far storms). Off: vanilla's bolt, and none in far storms.")
                .define("lightningBolts", true));
        DISTANT_THUNDER = add(b, "lightning", b.comment("Thunder rumbles after flashes in far storms, from their direction, later and quieter the",
                        "further away they are.")
                .define("distantThunder", true));
        THUNDER_FOLLOWS_RENDER_DISTANCE = add(b, "lightning", b.comment("Hear thunder only from storms within your render distance (Voxy's when it is",
                        "installed, never past the clouds), so storms you can't see stay quiet. Off: thunderDistance below.")
                .define("thunderFollowsRenderDistance", true));
        THUNDER_DISTANCE = add(b, "lightning", b.comment("How far away thunder is heard, in blocks, when thunderFollowsRenderDistance is off.")
                .defineInRange("thunderDistance", 2048, 256, 8192));
        THUNDER_VOLUME = add(b, "lightning", b.comment("Loudness of distant thunder, 0 to 1. Near lightning keeps vanilla's thunder.")
                .defineInRange("thunderVolume", 0.8, 0.0, 1.0));
        b.pop();

        section(b, "sky", "Sky and light.");
        SKY_GLOOM = add(b, "sky", b.comment("How much the sky and fog grey over as a storm's clouds build up, 0 to 1.")
                .defineInRange("skyGloom", 1.0, 0.0, 1.0));
        GLOBAL_TINT = add(b, "sky", b.comment("Better Clouds darkens every cloud by the rain around you. Storm clouds are darkened one by one",
                        "instead, so only this share of that is kept, 0 to 1. Better Clouds' own behaviour is 1.")
                .defineInRange("globalRainTint", 0.35, 0.0, 1.0));
        OVERCAST_SKY = add(b, "sky", b.comment("Under overcast weather regions the sky greys over and daylight dims a little.")
                .define("overcastSky", true));
        CLOUDS_HIDE_SKY = add(b, "sky", b.comment("Thick cloud hides the sun, moon and stars; clear skies show them fully.")
                .define("cloudsHideSky", true));
        b.pop();

        SPEC = b.build();
    }

    private static void section(ModConfigSpec.Builder b, String name, String comment) {
        b.comment(comment).push(name);
    }

    private static <T extends ModConfigSpec.ConfigValue<?>> T add(ModConfigSpec.Builder b, String section, T value) {
        ALL.add(value);
        List<String> path = value.getPath();
        SECTIONS.put(path.get(path.size() - 1), section);
        return value;
    }

    private LocalWeatherClientConfig() {
    }

    /** Called when the config file is loaded or changed: fills in the preset's values if the preset changed. */
    public static void sync(ModConfig config) {
        Preset preset = PRESET.get();
        if (PresetMarker.changed(config, "preset", preset.name(), Preset.DEFAULT.name()) && preset != Preset.CUSTOM) {
            preset.apply();
            SPEC.save();
            LocalWeather.LOGGER.info("Applied client preset {}", preset);
        }
        ClientSettings.refresh();
    }

    public enum Preset {
        DEFAULT, PERFORMANCE, CINEMATIC, VOXY_TECTONIC, CUSTOM;

        @SuppressWarnings("unchecked")
        void apply() {
            for (ModConfigSpec.ConfigValue<?> value : ALL) {
                ((ModConfigSpec.ConfigValue<Object>) value).set(value.getDefault());
            }
            changes(this).forEach((key, value) -> ((ModConfigSpec.ConfigValue<Object>) key).set(value));
        }
    }

    /**
     * What each preset changes from the defaults. Built on demand: the config values don't exist yet while the enum
     * is first loaded. Public for the preset game test.
     */
    public static Map<ModConfigSpec.ConfigValue<?>, Object> changes(Preset preset) {
        return switch (preset) {
            case DEFAULT, CUSTOM -> Map.of();
            case PERFORMANCE -> Map.of(
                    CLOUD_RESOLUTION, 0.5, CLOUD_COVER, 0.6, CLOUD_DISTANCE, 1536, DETAIL_DISTANCE, 768,
                    TOWER_HEIGHT, 160, THICK_RAIN_CLOUDS, false, HIGH_LAYER, false, RAIN_SHAFT_DISTANCE, 1536);
            case CINEMATIC -> Map.of(
                    CLOUD_RESOLUTION, 1.0, CLOUD_COVER, 0.66, CLOUD_DISTANCE, 4096, DETAIL_DISTANCE, 2048,
                    TOWER_HEIGHT, 320, HIGH_LAYER_HEIGHT, 200, RAIN_SHAFT_DISTANCE, 4096, GLOBAL_TINT, 0.3);
            // Tectonic's mountains reach towards the 320 build limit, so clouds start above them.
            case VOXY_TECTONIC -> Map.of(
                    CLOUD_COVER, 0.64, CLOUD_HEIGHT_OFFSET, 160, CLOUD_DISTANCE, 6144, DETAIL_DISTANCE, 2048,
                    TOWER_HEIGHT, 360, HIGH_LAYER_HEIGHT, 280, RAIN_SHAFT_DISTANCE, 6144, GLOBAL_TINT, 0.3);
        };
    }

    /** Every setting below the preset (for tests). */
    public static List<ModConfigSpec.ConfigValue<?>> all() {
        return List.copyOf(ALL);
    }
}
