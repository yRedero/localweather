package redero.localweather.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import redero.localweather.LocalWeather;

/**
 * Before 0.1.21 some client settings sat in other sections (lightning under "storms", the high layer in a section of
 * its own, the cloud height under "clouds"). The loader would throw those values away and write the defaults, so
 * before the config is registered this moves every known setting into its current section, keeping its value as
 * written. The old file is kept next to it as .old.
 */
public final class ClientConfigMigration {
    private ClientConfigMigration() {
    }

    public static void migrate(Path file) {
        if (!Files.isRegularFile(file)) return;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String migrated = migrate(lines);
            if (migrated == null) return;
            Files.copy(file, file.resolveSibling(file.getFileName() + ".old"), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file, migrated, StandardCharsets.UTF_8);
            LocalWeather.LOGGER.info("Moved Local Weather client settings to their new sections (old file kept as {}.old)",
                    file.getFileName());
        } catch (IOException | RuntimeException e) {
            LocalWeather.LOGGER.warn("Couldn't move old Local Weather client settings in {}", file, e);
        }
    }

    /** The file rewritten with every setting in its current section, or null if nothing needs moving. */
    public static String migrate(List<String> lines) {
        Map<String, String> sections = LocalWeatherClientConfig.SECTIONS;
        Map<String, String> values = new LinkedHashMap<>();
        String preset = null;
        String section = "";
        boolean moved = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) continue;
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (section.isEmpty() && key.equals("preset")) {
                preset = value;
            } else if (sections.containsKey(key) && !values.containsKey(key)) {
                values.put(key, value);
                moved |= !sections.get(key).equals(section);
            }
        }
        if (!moved) return null;

        StringBuilder out = new StringBuilder();
        if (preset != null) out.append("preset = ").append(preset).append("\n\n");
        List<String> order = new ArrayList<>(new LinkedHashSet<>(sections.values()));
        for (String name : order) {
            boolean header = false;
            for (Map.Entry<String, String> setting : sections.entrySet()) {
                if (!setting.getValue().equals(name) || !values.containsKey(setting.getKey())) continue;
                if (!header) {
                    out.append('[').append(name).append("]\n");
                    header = true;
                }
                out.append('\t').append(setting.getKey()).append(" = ").append(values.get(setting.getKey())).append('\n');
            }
            if (header) out.append('\n');
        }
        return out.toString();
    }
}
