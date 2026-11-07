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

    /**
     * Strict reader: throws instead of returning an empty configuration.
     *
     * <p>Round 42 — the difference matters for the player store. {@link #loadSafe} recovers from a
     * broken file by returning an <em>empty</em> YAML, and the profile parser then happily builds a
     * brand-new player: zero coins, zero ELO, no history, saved over the corrupt file a minute
     * later. The data was recoverable, the plugin just never knew it had failed. Callers that own
     * the file (the profile store) now use this and quarantine it instead.
     *
     * @throws IOException                   the file cannot be read
     * @throws InvalidConfigurationException the file exists but is not valid YAML
     */
    public static YamlConfiguration loadStrict(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (file == null) {
            throw new IOException("no file");
        }
        yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        return yaml;
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
            if (trimmed.isEmpty()) {
                sb.append('#');
            } else if (trimmed.startsWith("#")) {
                // Every banner in this plugin is already written with its leading '#': prefixing it
                // again produced "# # ====…" in every saved file.
                sb.append(trimmed);
            } else {
                sb.append("# ").append(trimmed);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Writes a file through a temporary sibling and an atomic move.
     *
     * <p>Round 42 — three fixes, all of them real:
     * <ul>
     *   <li>the temporary file used to have a <b>fixed</b> name ({@code <file>.tmp}). Two writers of
     *       the same profile (the IO thread still flushing while the shutdown path writes, or a
     *       reload racing a save) truncated each other's temp file and one of them then moved a
     *       half-written body into place. The name is now unique per write.</li>
     *   <li>a failed write left the temp file behind forever; it is deleted on every failure.</li>
     *   <li>the failure was logged with the message only. The exception class is logged too: an
     *       {@code AccessDeniedException} and a {@code NoSuchFileException} need different fixes.</li>
     * </ul>
     */
    public static void writeTextAtomically(File targetFile, String utf8Content, Logger logger) {
        if (targetFile == null) {
            return;
        }
        Path tmpPath = null;
        try {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new IOException("cannot create " + parent.getAbsolutePath());
            }
            Path targetPath = targetFile.toPath();
            tmpPath = targetPath.resolveSibling(targetFile.getName() + "."
                + Long.toHexString(Thread.currentThread().getId()) + "."
                + Long.toHexString(System.nanoTime()) + ".tmp");
            Files.writeString(tmpPath, utf8Content, StandardCharsets.UTF_8);
            try {
                Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException fallback) {
                // ATOMIC_MOVE is not supported by every filesystem (some NFS / FUSE mounts).
                Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            tmpPath = null;
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Failed to save " + targetFile.getName() + " ("
                    + e.getClass().getSimpleName() + "): " + e.getMessage());
            }
        } finally {
            if (tmpPath != null) {
                try {
                    Files.deleteIfExists(tmpPath);
                } catch (IOException ignored) {
                    // nothing sensible left to do: the body was never moved into place
                }
            }
        }
    }
}
