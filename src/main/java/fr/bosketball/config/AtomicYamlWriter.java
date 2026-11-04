package fr.bosketball.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AtomicYamlWriter {

    private AtomicYamlWriter() {
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public static YamlConfiguration loadSafe(File file, Logger logger) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file == null || !file.exists()) {
            return yaml;
        }
        try {
            yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException ex) {
            File backup = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis() + ".bak");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            if (logger != null) {
                logger.severe("Invalid YAML syntax in " + file.getName() + " (" + ex.getMessage()
                    + "). Backed up original file to " + backup.getName() + "!");
            }
        }
        return yaml;
    }

    public static void writeYamlAtomically(File targetFile, String headerBanner, YamlConfiguration yaml, Logger logger) {
        String body = stripDocumentHeader(yaml.saveToString());
        String fullText = (headerBanner == null || headerBanner.isBlank())
            ? body
            : commentBlock(headerBanner) + body;
        writeTextAtomically(targetFile, fullText, logger);
    }

    public static String stripDocumentHeader(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return "";
        }
        String[] lines = serialized.split("\n", -1);
        int firstContent = 0;
        while (firstContent < lines.length) {
            String line = lines[firstContent];
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                break;
            }
            firstContent++;
        }
        if (firstContent >= lines.length) {
            return serialized;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = firstContent; i < lines.length; i++) {
            if (i > firstContent) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static String commentBlock(String banner) {
        StringBuilder sb = new StringBuilder(banner.length() + 8);
        for (String line : banner.split("\\R", -1)) {
            String trimmed = line.stripTrailing();
            sb.append(trimmed.isEmpty() ? "#" : "# " + trimmed).append('\n');
        }
        return sb.toString();
    }

    public static void writeTextAtomically(File targetFile, String utf8Content, Logger logger) {
        if (targetFile == null) {
            return;
        }
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Path targetPath = targetFile.toPath();
            Path tmpPath = targetPath.resolveSibling(targetFile.getName() + ".tmp");
            Files.writeString(tmpPath, utf8Content, StandardCharsets.UTF_8);
            try {
                Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException fallback) {
                Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Failed to save " + targetFile.getName() + ": " + e.getMessage());
            }
        }
    }
}
