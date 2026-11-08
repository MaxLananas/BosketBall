package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.Test;

public class PackModelGuardTest {
    @Test public void missingItemModelAndTextureFailTheGuard() throws Exception {
        Path root = Files.createTempDirectory("pack-model-guard");
        try {
            Path assets = root.resolve("resourcepack/assets/bosketball");
            Path items = Files.createDirectories(assets.resolve("items"));
            Path models = Files.createDirectories(assets.resolve("models/item"));
            Path textures = Files.createDirectories(assets.resolve("textures/item"));
            Path java = Files.createDirectories(root.resolve("src/main/java"));
            Files.writeString(java.resolve("Menu.java"), "class Menu { void x() { customIcon(\"icon_test\"); } }");
            Path item = items.resolve("icon_test.json");
            Path model = models.resolve("icon_test.json");
            Path texture = textures.resolve("icon_test.png");
            Files.writeString(item, "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"bosketball:item/icon_test\"}}");
            Files.writeString(model, "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"bosketball:item/icon_test\"}}");
            Files.write(texture, new byte[] {1});
            assertEquals(0, runGuard(root).exitCode());
            Files.delete(item);
            assertFailure(root, "items reference bosketball:icon_test");
            Files.writeString(item, "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"bosketball:item/icon_test\"}}");
            Files.delete(model);
            assertFailure(root, "models reference bosketball:item/icon_test");
            Files.writeString(model, "{\"textures\":{\"layer0\":\"bosketball:item/icon_test\"}}");
            Files.delete(texture);
            assertFailure(root, "textures reference bosketball:item/icon_test");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void assertFailure(Path root, String expected) throws Exception {
        GuardResult result = runGuard(root);
        assertEquals(result.output(), 1, result.exitCode());
        assertTrue(result.output(), result.output().contains(expected));
    }

    private record GuardResult(int exitCode, String output) {}

    private static GuardResult runGuard(Path root) throws Exception {
        Process process = new ProcessBuilder("python3", "tools/check_pack_models.py", "--root", root.toString())
            .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new GuardResult(process.waitFor(), output);
    }
}
