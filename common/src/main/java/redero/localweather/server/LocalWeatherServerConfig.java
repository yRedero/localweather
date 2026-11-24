package redero.localweather.server;

import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import redero.localweather.LocalWeather;
import redero.localweather.PresetMarker;

/**
 * World weather settings (config/localweather-server.toml, or per world in serverconfig/). Two presets, each
 * filling in its own section when changed: the weather preset (storms and wind) and the height preset (where the
 * clouds sit). Values can be tweaked afterwards.
 */
public final class LocalWeatherServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<HeightPreset> HEIGHT_PRESET;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT_OFFSET;
    public static final ModConfigSpec.BooleanValue DRY_ABOVE_CLOUDS;
    public static final ModConfigSpec.EnumValue<WeatherPreset> WEATHER_PRESET;
    public static final ModConfigSpec.DoubleValue STORM_SIZE;
    public static final ModConfigSpec.DoubleValue STORM_LIFETIME;
    public static final ModConfigSpec.IntValue MAX_STORMS_NEARBY;
    public static final ModConfigSpec.DoubleValue STORM_FREQUENCY;
    public static final ModConfigSpec.DoubleValue SHOWER_CHANCE;
    public static final ModConfigSpec.DoubleValue THUNDERSTORM_CHANCE;
    public static final ModConfigSpec.DoubleValue WIND_MIN;
    public static final ModConfigSpec.DoubleValue WIND_MAX;
    public static final ModConfigSpec.BooleanValue WEATHER_REGIONS;
    public static final ModConfigSpec.EnumValue<SkyBalance> SKY_BALANCE;
    public static final ModConfigSpec.BooleanValue STORMS_FOLLOW_WEATHER;
    public static final ModConfigSpec.BooleanValue BIOME_WEATHER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Cloud height. Sent to every player, so everyone sees clouds at the same height as the world's weather.").push("clouds");
        HEIGHT_PRESET = b.comment("AUTO: the clouds go just above the world's real mountains, measured from the world generator",
                        "once per dimension (redo with /localweather cloudheight recompute). cloudHeightOffset is ignored.",
                        "VANILLA: clouds at 192. TECTONIC: +160, above default Tectonic's mountains. TALL_TERRAIN: +320.",
                        "CUSTOM: uses cloudHeightOffset below as it is.")
                .defineEnum("heightPreset", HeightPreset.AUTO);
        CLOUD_HEIGHT_OFFSET = b.comment("Moves the clouds up (or down) from the overworld's cloud height of 192, in blocks.",
                        "Players follow this unless they turn off 'Follow server cloud height' in their own config.")
                .defineInRange("cloudHeightOffset", 0, -128, 1024);
        DRY_ABOVE_CLOUDS = b.comment("Above the cloud base it doesn't rain: players stay dry, snow doesn't pile up, cauldrons",
                        "don't fill and lightning doesn't strike there.")
                .define("dryAboveClouds", true);
        b.pop();

        b.comment("Storm cells.").push("storms");
        WEATHER_PRESET = b.comment("Fills in the storm and wind settings when changed.",
                        "DEFAULT: balanced. CALM: fewer, smaller storms. STORMY: frequent storms and more thunder.",
                        "HUGE_STORMS: 1.6x larger, longer storms for very far views (Voxy, Distant Horizons).",
                        "CUSTOM: never changes anything.")
                .defineEnum("weatherPreset", WeatherPreset.DEFAULT);
        STORM_SIZE = b.comment("Multiplies every storm's radius. 1 is 0.9-1.6 km showers, 2.6-4.8 km rain fronts, 1.4-2.4 km thunderstorms.")
                .defineInRange("stormSize", 1.0, 0.25, 4.0);
        STORM_LIFETIME = b.comment("Multiplies how long storms last.")
                .defineInRange("stormLifetime", 1.0, 0.25, 4.0);
        MAX_STORMS_NEARBY = b.comment("No new storms form near a player while this many are already within ~9 km of them.")
                .defineInRange("maxStormsNearby", 2, 0, 8);
        STORM_FREQUENCY = b.comment("How often new storms try to form. 1 is about every 2.5 minutes per player while below the maximum.")
                .defineInRange("stormFrequency", 1.0, 0.0, 10.0);
        SHOWER_CHANCE = b.comment("Share of new storms that are short showers.")
                .defineInRange("showerChance", 0.35, 0.0, 1.0);
        THUNDERSTORM_CHANCE = b.comment("Share of new storms that are thunderstorms (warm biomes only, otherwise rain). The rest are rain fronts.")
                .defineInRange("thunderstormChance", 0.15, 0.0, 1.0);
        b.pop();

        b.comment("Wind. It wanders between these speeds and carries every storm and cloud. Set by the weather preset.").push("wind");
        WIND_MIN = b.comment("Slowest wind, in blocks per second.")
                .defineInRange("windMin", 0.6, 0.0, 20.0);
        WIND_MAX = b.comment("Fastest wind, in blocks per second.")
                .defineInRange("windMax", 2.4, 0.0, 20.0);
        b.pop();

        b.comment("Weather regions: between the storms the sky varies from clear to overcast in areas several km wide that",
                "drift with the wind and change over a day or two.").push("regions");
        WEATHER_REGIONS = b.comment("Turn weather regions on. Off: the same fair-weather cover everywhere, as before.")
                .define("weatherRegions", true);
        SKY_BALANCE = b.comment("How cloudy the world is between storms. BALANCED: about a third clear, a third fair, a third cloudy.",
                        "MOSTLY_FAIR: clear and fair skies most of the time. MOODY: cloudy and overcast skies more often.")
                .defineEnum("skyBalance", SkyBalance.BALANCED);
        STORMS_FOLLOW_WEATHER = b.comment("Storms form mostly where the sky is cloudy, rarely under clear skies.")
                .define("stormsFollowWeather", true);
        BIOME_WEATHER = b.comment("Biomes change the weather: wet biomes (jungles, swamps, oceans) cloudier and stormier,",
                        "dry ones (deserts, badlands) clearer, with fewer storms. Affects where storms form.")
                .define("biomeWeather", false);
        b.pop();

        SPEC = b.build();
    }

    private LocalWeatherServerConfig() {
    }

    /** Called when the config file is loaded or changed: fills in each preset's values if that preset changed. */
    public static void sync(ModConfig config) {
        boolean changed = false;
        HeightPreset height = HEIGHT_PRESET.get();
        if (PresetMarker.changed(config, "heightPreset", height.name(), HeightPreset.AUTO.name()) && height != HeightPreset.CUSTOM) {
            apply(heightValues(height));
            LocalWeather.LOGGER.info("Applied height preset {}", height);
            changed = true;
        }
        WeatherPreset weather = WEATHER_PRESET.get();
        if (PresetMarker.changed(config, "weatherPreset", weather.name(), WeatherPreset.DEFAULT.name()) && weather != WeatherPreset.CUSTOM) {
            apply(weatherValues(weather));
            LocalWeather.LOGGER.info("Applied weather preset {}", weather);
            changed = true;
        }
        if (changed) SPEC.save();
    }

    @SuppressWarnings("unchecked")
    private static void apply(Map<ModConfigSpec.ConfigValue<?>, Object> values) {
        values.forEach((key, value) -> ((ModConfigSpec.ConfigValue<Object>) key).set(value));
    }

    // Getters fall back to the defaults when no world is loaded (the server config only exists with a world).

    public static boolean autoHeight() {
        return !SPEC.isLoaded() || HEIGHT_PRESET.get() == HeightPreset.AUTO;
    }

    public static int cloudHeightOffset() {
        return SPEC.isLoaded() ? CLOUD_HEIGHT_OFFSET.get() : 0;
    }

    public static boolean dryAboveClouds() {
        return !SPEC.isLoaded() || DRY_ABOVE_CLOUDS.get();
    }

    public static double stormSize() {
        return SPEC.isLoaded() ? STORM_SIZE.get() : 1.0;
    }

    public static double stormLifetime() {
        return SPEC.isLoaded() ? STORM_LIFETIME.get() : 1.0;
    }

    public static int maxStormsNearby() {
        return SPEC.isLoaded() ? MAX_STORMS_NEARBY.get() : 2;
    }

    public static double stormFrequency() {
        return SPEC.isLoaded() ? STORM_FREQUENCY.get() : 1.0;
    }

    public static double showerChance() {
        return SPEC.isLoaded() ? SHOWER_CHANCE.get() : 0.35;
    }

    public static double thunderstormChance() {
        return SPEC.isLoaded() ? THUNDERSTORM_CHANCE.get() : 0.15;
    }

    /** Blocks per tick. */
    public static float windMin() {
        return (float) (SPEC.isLoaded() ? Math.min(WIND_MIN.get(), WIND_MAX.get()) : 0.6) / 20.0F;
    }

    /** Blocks per tick. */
    public static float windMax() {
        return (float) (SPEC.isLoaded() ? Math.max(WIND_MIN.get(), WIND_MAX.get()) : 2.4) / 20.0F;
    }

    public enum HeightPreset { AUTO, VANILLA, TECTONIC, TALL_TERRAIN, CUSTOM }

    public enum WeatherPreset { DEFAULT, CALM, STORMY, HUGE_STORMS, CUSTOM }

    /** How cloudy the weather regions are on average: shifts their cloudiness down or up. */
    public enum SkyBalance {
        MOSTLY_FAIR(-0.12F), BALANCED(0.0F), MOODY(0.12F);

        public final float shift;

        SkyBalance(float shift) {
            this.shift = shift;
        }
    }

    public static boolean weatherRegions() {
        return !SPEC.isLoaded() || WEATHER_REGIONS.get();
    }

    public static float skyBalance() {
        return SPEC.isLoaded() ? SKY_BALANCE.get().shift : 0.0F;
    }

    public static boolean stormsFollowWeather() {
        return !SPEC.isLoaded() || STORMS_FOLLOW_WEATHER.get();
    }

    public static boolean biomeWeather() {
        return SPEC.isLoaded() && BIOME_WEATHER.get();
    }

    // Built on demand: the config values don't exist yet while the enums are first loaded. A preset resets the
    // settings it covers to their defaults, then applies its own changes from those.

    private static Map<ModConfigSpec.ConfigValue<?>, Object> heightValues(HeightPreset preset) {
        Map<ModConfigSpec.ConfigValue<?>, Object> values = defaults(CLOUD_HEIGHT_OFFSET, DRY_ABOVE_CLOUDS);
        switch (preset) {
            case TECTONIC -> values.put(CLOUD_HEIGHT_OFFSET, 160);
            case TALL_TERRAIN -> values.put(CLOUD_HEIGHT_OFFSET, 320);
            case CUSTOM -> values.clear();
            default -> {
            }
        }
        return values;
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> weatherValues(WeatherPreset preset) {
        Map<ModConfigSpec.ConfigValue<?>, Object> values = defaults(STORM_SIZE, STORM_LIFETIME, MAX_STORMS_NEARBY,
                STORM_FREQUENCY, SHOWER_CHANCE, THUNDERSTORM_CHANCE, WIND_MIN, WIND_MAX);
        switch (preset) {
            case CALM -> values.putAll(Map.of(STORM_SIZE, 0.8, STORM_LIFETIME, 0.8, MAX_STORMS_NEARBY, 1, STORM_FREQUENCY, 0.5,
                    SHOWER_CHANCE, 0.5, THUNDERSTORM_CHANCE, 0.05, WIND_MIN, 0.4, WIND_MAX, 1.6));
            case STORMY -> values.putAll(Map.of(STORM_SIZE, 1.1, MAX_STORMS_NEARBY, 3, STORM_FREQUENCY, 2.0,
                    SHOWER_CHANCE, 0.3, THUNDERSTORM_CHANCE, 0.35, WIND_MIN, 1.0, WIND_MAX, 3.5));
            case HUGE_STORMS -> values.putAll(Map.of(STORM_SIZE, 1.6, STORM_LIFETIME, 1.5, MAX_STORMS_NEARBY, 3,
                    SHOWER_CHANCE, 0.3, THUNDERSTORM_CHANCE, 0.2, WIND_MIN, 1.0, WIND_MAX, 3.0));
            case CUSTOM -> values.clear();
            default -> {
            }
        }
        return values;
    }

    private static Map<ModConfigSpec.ConfigValue<?>, Object> defaults(ModConfigSpec.ConfigValue<?>... settings) {
        Map<ModConfigSpec.ConfigValue<?>, Object> values = new java.util.LinkedHashMap<>();
        for (ModConfigSpec.ConfigValue<?> setting : settings) values.put(setting, setting.getDefault());
        return values;
    }
}
