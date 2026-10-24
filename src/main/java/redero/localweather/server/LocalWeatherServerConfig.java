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
            case DEFAULT -> Map.of(STORM_SIZE, 1.0, STORM_LIFETIME, 1.0, MAX_STORMS_NEARBY, 2, STORM_FREQUENCY, 1.0,
                    SHOWER_CHANCE, 0.35, THUNDERSTORM_CHANCE, 0.15, WIND_MIN, 0.6, WIND_MAX, 2.4);
            case CALM -> Map.of(STORM_SIZE, 0.8, STORM_LIFETIME, 0.8, MAX_STORMS_NEARBY, 1, STORM_FREQUENCY, 0.5,
                    SHOWER_CHANCE, 0.5, THUNDERSTORM_CHANCE, 0.05, WIND_MIN, 0.4, WIND_MAX, 1.6);
            case STORMY -> Map.of(STORM_SIZE, 1.1, STORM_LIFETIME, 1.0, MAX_STORMS_NEARBY, 3, STORM_FREQUENCY, 2.0,
                    SHOWER_CHANCE, 0.3, THUNDERSTORM_CHANCE, 0.35, WIND_MIN, 1.0, WIND_MAX, 3.5);
            case VOXY_TECTONIC -> Map.of(STORM_SIZE, 1.6, STORM_LIFETIME, 1.5, MAX_STORMS_NEARBY, 3, STORM_FREQUENCY, 1.0,
                    SHOWER_CHANCE, 0.3, THUNDERSTORM_CHANCE, 0.2, WIND_MIN, 1.0, WIND_MAX, 3.0);
            case CUSTOM -> Map.of();
        };
    }
}
