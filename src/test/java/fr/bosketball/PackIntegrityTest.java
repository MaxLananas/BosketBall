package fr.bosketball;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The resource pack is code as much as the Java is: an icon id that names a model the pack does
 * not ship renders as the purple-and-black missing texture, which is exactly what a beta tester
 * reported on an achievement.
 *
 * <p>This walks the pack the same way the client does — every model must resolve to a texture file,
 * and every id the plugin hands to {@code setItemModel} or {@code createCustomModelItem} must name a
 * model that exists. It fails the build instead of shipping a hole in a GUI.
 */
public class PackIntegrityTest {

    private static final Path PACK = Path.of("resourcepack", "assets", "bosketball");
    private static final Path SOURCES = Path.of("src", "main", "java");

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** Every model in the pack must point at textures that exist. */
    @Test
    public void everyModelResolvesToRealTextures() throws IOException {
        Path models = PACK.resolve("models").resolve("item");
        if (!Files.isDirectory(models)) {
            return;
        }
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(models)) {
            for (Path model : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String json = Files.readString(model, StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"(bosketball:[^\"]+)\"").matcher(json);
                while (m.find()) {
                    String ref = m.group(1).substring("bosketball:".length());
                    Path texture = PACK.resolve("textures").resolve(ref + ".png");
                    if (!Files.isRegularFile(texture)) {
                        offenders.add(model.getFileName() + " -> " + m.group(1));
                    }
                }
            }
        }
        assertTrue("A model points at a texture the pack does not ship: " + offenders,
            offenders.isEmpty());
    }

    /** Every icon id named by the plugin must exist, or the GUI slot renders as missing texture. */
    @Test
    public void everyIconThePluginAsksForExistsInThePack() throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        // Only keys that end up on an item model count: the same constructor is also used for
        // persistent data tags, which have nothing to do with the pack.
        Pattern keyed = Pattern.compile(
            "(?:setItemModel|MODEL).*new NamespacedKey\\(\\s*\"bosketball\"\\s*,\\s*\"([a-z0-9_]+)\"");
        Pattern custom = Pattern.compile("createCustomModelItem\\(\\s*Material\\.[A-Z_]+\\s*,\\s*\"([a-z0-9_]+)\"");
        Pattern setModel = Pattern.compile("setItemModel\\(\\s*\"([a-z0-9_]+)\"");
        Pattern quotedIcon = Pattern.compile("\"((?:icon|ref)_[a-z0-9_]+)\"");

        for (Path source : javaSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            for (Pattern p : List.of(keyed, custom, setModel, quotedIcon)) {
                Matcher m = p.matcher(text);
                while (m.find()) {
                    ids.add(m.group(1));
                }
            }
        }

        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            if (!Files.isRegularFile(PACK.resolve("models").resolve("item").resolve(id + ".json"))) {
                missing.add(id);
            }
        }
        assertTrue("the scan must actually find the icons, found " + ids.size(), ids.size() > 40);
        assertTrue("The plugin asks for models the pack does not ship: " + missing, missing.isEmpty());
    }
}
