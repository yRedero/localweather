package redero.localweather;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.neoforged.fml.config.ModConfig;

/**
 * Remembers which preset each preset setting of a config file was last applied from, in a small file next to it
 * (e.g. localweather-server.toml.preset). That way a preset picked while the game was closed still applies.
 */
public final class PresetMarker {
    private PresetMarker() {
    }

    /**
     * @param key the preset setting's name, e.g. "weatherPreset"
     * @return true if that preset changed since it was last applied, so its values should be filled in now. The
     * first time, only a non-default preset counts: the file's values are the defaults then anyway.
     */
    public static boolean changed(ModConfig config, String key, String preset, String defaultPreset) {
        Path marker = config.getFullPath().resolveSibling(config.getFullPath().getFileName() + ".preset");
        Properties applied = read(marker);
        String last = applied.getProperty(key);
        if (preset.equals(last)) return false;
        applied.setProperty(key, preset);
        write(marker, applied);
        return last != null || !preset.equals(defaultPreset);
    }

    /** Older versions wrote a single bare preset name; that counts as nothing recorded. */
    private static Properties read(Path marker) {
        Properties properties = new Properties();
        if (!Files.exists(marker)) return properties;
        try (Reader in = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
            properties.load(in);
            properties.entrySet().removeIf(entry -> entry.getValue().toString().isEmpty());
        } catch (IOException | IllegalArgumentException e) {
            properties.clear();
        }
        return properties;
    }

    private static void write(Path marker, Properties properties) {
        try (Writer out = Files.newBufferedWriter(marker, StandardCharsets.UTF_8)) {
            properties.store(out, "Local Weather: presets last applied to the config next to this file");
        } catch (IOException e) {
            LocalWeather.LOGGER.warn("Could not save the applied presets to {}", marker, e);
        }
    }
}
