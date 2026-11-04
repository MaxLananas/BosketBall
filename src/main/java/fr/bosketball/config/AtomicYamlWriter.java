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

/**
 * Crash-safe atomic YAML writer, corrupted-file backup helper, and clean decimal rounder.
 */
public final class AtomicYamlWriter {

    private AtomicYamlWriter() {
    }

    /**
     * Rounds a coordinate to 2 decimal places so YAML files stay crisp and readable (no IEEE-754 float noise).
     */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Rounds a coordinate or angle to 3 decimal places.
     */
    public static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /**
     * Safely loads a YAML file. If a manual edit introduced a syntax error, backs up the broken file to
     * {@code <name>.corrupt-<timestamp>.bak} so admin/player data is never silently lost!
     */
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

    /**
     * Atomically writes {@code headerBanner + yaml.saveToString()} via a sibling {@code .tmp} file + atomic rename.
     *
     * <p><b>Critical:</b> a {@link YamlConfiguration} that was loaded from a file carries the original
     * comments in its header, and {@code saveToString()} therefore emits them as <em>plain prose</em>
     * above the document. Handing that text to a fresh {@link YamlConfiguration} (done by
     * {@link #loadSafe} on the next boot) fails with "expected {@code <document start>}, but found
     * {@code <block mapping start>}", which is exactly what kept wiping {@code hall-of-fame.yml} and
     * pushing it aside as {@code .corrupt-*.bak} on every restart. The body is stripped of its header
     * before the banner is prepended, so the file always starts with real {@code #} comments.</p>
     */
    public static void writeYamlAtomically(File targetFile, String headerBanner, YamlConfiguration yaml, Logger logger) {
        String body = stripDocumentHeader(yaml.saveToString());
        String fullText = (headerBanner == null || headerBanner.isBlank())
            ? body
            : commentBlock(headerBanner) + body;
        writeTextAtomically(targetFile, fullText, logger);
    }

    /**
     * Removes the comment block {@link YamlConfiguration#saveToString()} copies from a file header.
     *
     * <p>Only a leading run of comment/blank lines is removed, and only when real content follows;
     * a value that legitimately starts with {@code #} is never touched because it is not at the
     * very start of the document.</p>
     */
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

    /**
     * Turns a free-form banner into a real YAML comment block.
     *
     * <p>Saving the banner verbatim made the file start with plain prose and no {@code #}
     * prefix, so the next load failed with "expected {@code <document start>}" (and the
     * file got pushed aside as {@code .corrupt-*.bak}) — that is what silently wiped the
     * guild list and the Hall of Fame pose on every restart.</p>
     */
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
