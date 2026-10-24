package redero.localweather.server;

import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;
import redero.localweather.LocalWeather;
import redero.localweather.PresetMarker;

/**
 * Per-world weather settings (serverconfig/localweather-server.toml). Picking a preset fills in every value
 * below it; they can be tweaked afterwards.
 */
public final class LocalWeatherServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<Preset> PRESET;
    public static final ModConfigSpec.IntValue CLOUD_HEIGHT_OFFSET;
    public static final ModConfigSpec.BooleanValue DRY_ABOVE_CLOUDS;
    public static final ModConfigSpec.DoubleValue STORM_SIZE;
    public static final ModConfigSpec.DoubleValue STORM_LIFETIME;
    public static final ModConfigSpec.IntValue MAX_STORMS_NEARBY;
    public static final ModConfigSpec.DoubleValue STORM_FREQUENCY;
    public static final ModConfigSpec.DoubleValue SHOWER_CHANCE;
    public static final ModConfigSpec.DoubleValue THUNDERSTORM_CHANCE;
    public static final ModConfigSpec.DoubleValue WIND_MIN;
    public static final ModConfigSpec.DoubleValue WIND_MAX;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        PRESET = b.comment("Fills in all settings below when changed. Afterwards each setting can still be tweaked.",
                        "DEFAULT: balanced. CALM: fewer, smaller storms. STORMY: frequent storms and more thunder.",
                        "VOXY_TECTONIC: huge storms for huge views, pairs with the client preset of the same name.",
                        "CUSTOM: never changes anything.")
                .defineEnum("preset", Preset.DEFAULT);

        b.comment("Cloud height. Sent to every player, so everyone sees clouds at the same height as the world's weather.").push("clouds");
        CLOUD_HEIGHT_OFFSET = b.comment("Moves the clouds up (or down) from the overworld's cloud height of 192, in blocks.",
                        "Raise it for terrain mods with tall mountains, e.g. +160 for Tectonic. Players follow this unless they",
                        "turn off 'Follow server cloud height' in their own config.")
                .defineInRange("cloudHeightOffset", 0, -128, 1024);
        DRY_ABOVE_CLOUDS = b.comment("Above the cloud base it doesn't rain: players stay dry, snow doesn't pile up, cauldrons",
                        "don't fill and lightning doesn't strike there.")
                .define("dryAboveClouds", true);
        b.pop();

        b.comment("Storm cells.").push("storms");
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

        b.comment("Wind. It wanders between these speeds and carries every storm and cloud.").push("wind");
        WIND_MIN = b.comment("Slowest wind, in blocks per second.")
                .defineInRange("windMin", 0.6, 0.0, 20.0);
        WIND_MAX = b.comment("Fastest wind, in blocks per second.")
                .defineInRange("windMax", 2.4, 0.0, 20.0);
        b.pop();

        SPEC = b.build();
    }

    private LocalWeatherServerConfig() {
    }

    /** Called when the config file is loaded or changed: fills in the preset's values if the preset changed. */
    public static void sync(ModConfig config) {
        Preset preset = PRESET.get();
        if (!PresetMarker.changed(config, preset.name(), Preset.DEFAULT.name()) || preset == Preset.CUSTOM) return;
        preset.apply();
        SPEC.save();
        LocalWeather.LOGGER.info("Applied server preset {}", preset);
    }

    // Getters fall back to the defaults when no world is loaded (the server config only exists with a world).

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

    public enum Preset {
        DEFAULT, CALM, STORMY, VOXY_TECTONIC, CUSTOM;

        @SuppressWarnings("unchecked")
        void apply() {
            presetValues(this).forEach((key, value) -> ((ModConfigSpec.ConfigValue<Object>) key).set(value));
        }
    }

    /** Built on demand: the config values don't exist yet while the enum is first loaded. */
    private static Map<ModConfigSpec.ConfigValue<?>, Object> presetValues(Preset preset) {
        return switch (preset) {
            case DEFAULT -> Map.ofEntries(
                    Map.entry(STORM_SIZE, 1.0), Map.entry(STORM_LIFETIME, 1.0), Map.entry(MAX_STORMS_NEARBY, 2), Map.entry(STORM_FREQUENCY, 1.0),
                    Map.entry(SHOWER_CHANCE, 0.35), Map.entry(THUNDERSTORM_CHANCE, 0.15), Map.entry(WIND_MIN, 0.6), Map.entry(WIND_MAX, 2.4),
                    Map.entry(CLOUD_HEIGHT_OFFSET, 0), Map.entry(DRY_ABOVE_CLOUDS, true));
            case CALM -> Map.ofEntries(
                    Map.entry(STORM_SIZE, 0.8), Map.entry(STORM_LIFETIME, 0.8), Map.entry(MAX_STORMS_NEARBY, 1), Map.entry(STORM_FREQUENCY, 0.5),
                    Map.entry(SHOWER_CHANCE, 0.5), Map.entry(THUNDERSTORM_CHANCE, 0.05), Map.entry(WIND_MIN, 0.4), Map.entry(WIND_MAX, 1.6),
                    Map.entry(CLOUD_HEIGHT_OFFSET, 0), Map.entry(DRY_ABOVE_CLOUDS, true));
            case STORMY -> Map.ofEntries(
                    Map.entry(STORM_SIZE, 1.1), Map.entry(STORM_LIFETIME, 1.0), Map.entry(MAX_STORMS_NEARBY, 3), Map.entry(STORM_FREQUENCY, 2.0),
                    Map.entry(SHOWER_CHANCE, 0.3), Map.entry(THUNDERSTORM_CHANCE, 0.35), Map.entry(WIND_MIN, 1.0), Map.entry(WIND_MAX, 3.5),
                    Map.entry(CLOUD_HEIGHT_OFFSET, 0), Map.entry(DRY_ABOVE_CLOUDS, true));
            case VOXY_TECTONIC -> Map.ofEntries(
                    Map.entry(STORM_SIZE, 1.6), Map.entry(STORM_LIFETIME, 1.5), Map.entry(MAX_STORMS_NEARBY, 3), Map.entry(STORM_FREQUENCY, 1.0),
                    Map.entry(SHOWER_CHANCE, 0.3), Map.entry(THUNDERSTORM_CHANCE, 0.2), Map.entry(WIND_MIN, 1.0), Map.entry(WIND_MAX, 3.0),
                    Map.entry(CLOUD_HEIGHT_OFFSET, 160), Map.entry(DRY_ABOVE_CLOUDS, true));
            case CUSTOM -> Map.of();
        };
    }
}
