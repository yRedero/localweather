package redero.localweather;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

import net.neoforged.fml.config.ModConfig;

/**
 * Remembers which preset a config file's values were last filled from, in a small file next to it
 * (e.g. localweather-client.toml.preset). That way a preset picked while the game was closed still applies.
 */
public final class PresetMarker {
    private PresetMarker() {
    }

    /**
     * @return true if the preset changed since it was last applied, so its values should be filled in now.
     * The first time, only a non-default preset counts: the file's values are the defaults then anyway.
     */
    public static boolean changed(ModConfig config, String preset, String defaultPreset) {
        Path marker = config.getFullPath().resolveSibling(config.getFullPath().getFileName() + ".preset");
        String applied = read(marker);
        if (preset.equals(applied)) return false;
        write(marker, preset);
        return applied != null || !preset.equals(defaultPreset);
    }

    @Nullable
    private static String read(Path marker) {
        try {
            return Files.exists(marker) ? Files.readString(marker, StandardCharsets.UTF_8).strip() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(Path marker, String preset) {
        try {
            Files.writeString(marker, preset, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LocalWeather.LOGGER.warn("Could not save the applied preset to {}", marker, e);
        }
    }
}
