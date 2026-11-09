package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.bosketball.model.BallSkin;
import fr.bosketball.util.Glyphs;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;

/**
 * The ball, asserted against the bytes that ship.
 *
 * <p>Every other ball check in this suite looks at the Java side of the item. This one reads
 * the packed model JSON and the packed textures, because that is where a ball redesign can
 * actually go wrong: a skin whose model is missing, a seam that moved between two skins, a
 * ball authored at the wrong radius, a preview strip that no skin addresses. None of those
 * are visible from the enum.
 *
 * <p>The numbers here are the contract, not a snapshot of today's code. If one of them has
 * to change, that is a design decision and it belongs in the same commit as the constant.
 */
public class BallModelContractTest {

    private static final String MODEL_DIR = "assets/bosketball/models/item/";
    private static final String TEXTURE_DIR = "assets/bosketball/textures/item/";
    private static final File PACK = new File("releases/BosketBall-ResourcePack.zip");

    /** `tools/ball_model.py`: SPHERE_RADIUS_BLOCKS (0.285) / VISUAL_BALL_SCALE (0.52) * 16. */
    private static final double EXPECTED_RADIUS_UNITS = 0.285 / 0.52 * 16.0;

    @Test
    public void everySkinShipsOneCubeAtTheRadiusTheSimulationCollidesAt() throws Exception {
        assertEquals("The catalogue is eight skins and stays eight: the tests, the glyph block "
            + "and the shop page all assume that count", 8, BallSkin.values().length);
        try (ZipFile pack = new ZipFile(PACK)) {
            for (BallSkin skin : BallSkin.values()) {
                JsonObject model = model(pack, skin.modelId());
                // The ball is one cube and its six painted faces. An element count here means
                // the silhouette went back to being assembled out of parts, which is what made
                // the ball both heavy and fragile.
                assertEquals("The ball is one cube: " + skin.modelId(), 1,
                    model.getAsJsonArray("elements").size());

                double[] bounds = bounds(model);
                double centreX = (bounds[0] + bounds[1]) / 2.0;
                double centreY = (bounds[2] + bounds[3]) / 2.0;
                double centreZ = (bounds[4] + bounds[5]) / 2.0;
                assertEquals("The ball must be centred on x=8, or it orbits its own spin pivot: "
                    + skin.modelId(), 8.0, centreX, 0.75);
                assertEquals("The ball must be centred on y=8: " + skin.modelId(), 8.0, centreY, 0.75);
                assertEquals("The ball must be centred on z=8: " + skin.modelId(), 8.0, centreZ, 0.75);

                // The cube is inscribed in the radius the simulation collides at: its flat
                // faces sit exactly where the physics ball's surface is, and only its corners
                // reach past it. That is the whole point of the cube, so the half-extent is
                // the number to assert, not the corner distance.
                double half = Math.max((bounds[1] - bounds[0]), Math.max(bounds[3] - bounds[2],
                    bounds[5] - bounds[4])) / 2.0;
                assertEquals("The cube must be authored at the simulated radius, not the old "
                    + "toy-sized 0.598-block cube, which is 18.4 units and a third too big: "
                    + skin.modelId(),
                    EXPECTED_RADIUS_UNITS, half, 1.0);
            }
        }
    }

    /**
     * One silhouette for eight skins.
     *
     * <p>Two skins that disagree about where a seam is cannot both be right, and a redesign
     * that nudges the geometry per skin is invisible on a hero render and obvious the moment
     * two players hold different balls. So the element boxes are compared byte for byte; only
     * the palette UVs and the light emission are allowed to differ.
     */
    @Test
    public void allEightSkinsShareOneSilhouette() throws Exception {
        try (ZipFile pack = new ZipFile(PACK)) {
            List<String> reference = geometry(pack, BallSkin.CLASSIC.modelId());
            assertFalse("The silhouette must not be empty", reference.isEmpty());
            for (BallSkin skin : BallSkin.values()) {
                List<String> other = geometry(pack, skin.modelId());
                assertEquals("Every skin must ship the same element count as the Game Ball: "
                    + skin.modelId(), reference.size(), other.size());
                assertEquals("One element, or the skins are assembled differently", 1, other.size());
                assertEquals("Every skin must place its cube in the same place as the Game Ball: "
                    + skin.modelId(), reference, other);
            }
        }
    }

    @Test
    public void displayTransformsKeepTheBallOutOfTheHandAndUprightInTheWorld() throws Exception {
        try (ZipFile pack = new ZipFile(PACK)) {
            for (BallSkin skin : BallSkin.values()) {
                JsonObject display = model(pack, skin.modelId()).getAsJsonObject("display");
                assertNotNull("A model without display transforms stretches across the world: "
                    + skin.modelId(), display);
                for (String hand : new String[]{"firstperson_righthand", "firstperson_lefthand",
                    "thirdperson_righthand", "thirdperson_lefthand"}) {
                    assertEquals("The ball is the projectile, not a held item; it must be invisible "
                        + "in " + hand + ": " + skin.modelId(), 0.0,
                        display.getAsJsonObject(hand).getAsJsonArray("scale").get(0).getAsDouble(), 1e-6);
                }
                JsonObject fixed = display.getAsJsonObject("fixed");
                assertEquals("An ItemDisplay ball must be drawn at full size: " + skin.modelId(),
                    1.0, fixed.getAsJsonArray("scale").get(0).getAsDouble(), 1e-6);
                assertEquals("An ItemDisplay ball must rest on a seam corner, not a panel face, "
                    + "or the pattern disappears at the default network angle: " + skin.modelId(),
                    45.0, fixed.getAsJsonArray("rotation").get(1).getAsDouble(), 1e-6);
                JsonObject gui = display.getAsJsonObject("gui");
                assertTrue("An inventory cell draws a model at roughly half scale; anything else "
                    + "overflows the slot: " + skin.modelId(),
                    gui.getAsJsonArray("scale").get(0).getAsDouble() > 0.3
                        && gui.getAsJsonArray("scale").get(0).getAsDouble() < 0.6);
            }
        }
    }

    /** One painted atlas per skin, and the animated skin is the only one that needs a strip. */
    @Test
    public void oneAtlasPerSkinAndOnlyFireAnimates() throws Exception {
        try (ZipFile pack = new ZipFile(PACK)) {
            Set<String> textures = new HashSet<>();
            for (ZipEntry entry : java.util.Collections.list(pack.entries())) {
                String name = entry.getName();
                int slash = name.lastIndexOf('/');
                if (name.startsWith(TEXTURE_DIR) && slash > 0) {
                    String file = name.substring(slash + 1);
                    // `ball_` only: ball_classic is ours, baller and basketball icons are not.
                    if (file.matches("ball_[a-z_]+(\\.png)?(\\.mcmeta)?")) {
                        textures.add(file);
                    }
                }
            }
            for (BallSkin skin : BallSkin.values()) {
                // ball_classic.png, not basketball_classic.png: the model ids and the texture
                // names are deliberately different keys, and the model's own `textures` block
                // is what maps one to the other.
                String name = skin.name().toLowerCase(java.util.Locale.ROOT);
                String atlas = "ball_" + name + ".png";
                assertTrue("Every skin needs its own painted atlas: " + atlas, textures.contains(atlas));
                assertFalse("The six faces live in one atlas; shipping one again means two "
                    + "sources of truth for the same ball: " + skin.modelId(),
                    textures.contains("ball_" + name + "_north.png"));
                assertFalse(textures.contains("ball_" + name + "_south.png"));
                assertFalse(textures.contains("ball_" + name + "_up.png"));
                assertFalse(textures.contains("ball_" + name + "_down.png"));
                assertFalse(textures.contains("ball_" + name + "_east.png"));
                assertFalse(textures.contains("ball_" + name + "_west.png"));
            }
            assertTrue("Fire is the animated skin and ships a strip plus its .mcmeta",
                textures.contains("ball_fire.png.mcmeta"));
            for (BallSkin skin : BallSkin.values()) {
                if (skin != BallSkin.FIRE) {
                    assertFalse("Only Fire animates: " + skin.modelId(),
                        textures.contains("ball_" + skin.name().toLowerCase(
                            java.util.Locale.ROOT) + ".png.mcmeta"));
                }
            }
            assertEquals("The pack must ship exactly eight ball atlases", 8,
                textures.stream().filter(n -> n.endsWith(".png")).count());
        }
    }

    /**
     * The glyph block, the preview strips and the skin list are one list wearing three hats.
     *
     * <p>Codepoints are assigned by index, so a skin inserted in the middle shifts every strip
     * after it and each skin silently shows its neighbour's portrait. That is a bug nobody
     * reports, because the wrong picture still looks like a basketball.
     */
    @Test
    public void previewStripsFollowTheSkinOrderAndExistInThePack() throws Exception {
        int expected = 0xE320;
        Set<Character> codepoints = new LinkedHashSet<>();
        try (ZipFile pack = new ZipFile(PACK)) {
            for (BallSkin skin : BallSkin.values()) {
                for (int row = 0; row < 4; row++) {
                    char glyph = Glyphs.ballPreviewStrip(skin, row);
                    assertEquals("Preview-strip codepoints are assigned by catalogue index: "
                        + skin.modelId() + " row " + row, expected, glyph);
                    assertTrue("Two skins must never share a preview glyph", codepoints.add(glyph));
                    String strip = "assets/bosketball/textures/font/ball_preview_"
                        + skin.modelId().substring("basketball_".length()) + "_" + row + ".png";
                    assertNotNull("The font declares a glyph that no texture backs, which renders "
                        + "as a missing-character box: " + strip, pack.getEntry(strip));
                    expected++;
                }
            }
        }
        assertEquals("The ball owns exactly 32 preview codepoints", 0xE340, expected);
        assertEquals("A null skin must fall back to the Game Ball, not to a random strip",
            Glyphs.ballPreviewStrip(null, 0), codepoints.iterator().next().charValue());
    }

    /**
     * Shop lore is the same sentence as the generated contact sheet.
     *
     * <p>`BallSkin` feeds the item tooltip and `tools/ball_model.py` feeds the preview image.
     * When they disagree the tooltip promises a ball the player never sees, and it is the kind
     * of drift that survives reviews because both halves look fine on their own.
     */
    @Test
    public void shopLoreMatchesTheStringsThePreviewGeneratorDraws() throws Exception {
        String python = Files.readString(new File("tools/ball_model.py").toPath());
        for (BallSkin skin : BallSkin.values()) {
            String key = skin.name().toLowerCase(java.util.Locale.ROOT);
            String block = skinBlock(python, key);
            assertNotNull("tools/ball_model.py has no SKINS entry for " + key, block);
            Matcher describe = Pattern.compile("\"describe\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
            assertTrue("The SKINS entry for " + key + " must carry a describe string", describe.find());
            assertEquals("Shop lore and preview copy must be the same sentence: " + key,
                skin.loreLine(), describe.group(1));

            Matcher label = Pattern.compile("\"label\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
            assertTrue(label.find());
            String plain = skin.displayMiniMessage().replaceAll("<[^>]*>", "");
            assertEquals("The shop name and the card title must agree: " + key,
                label.group(1), plain);
        }
    }

    /** Price is player-facing: a skin must never be free by accident, and the order is the ramp. */
    @Test
    public void cataloguePricesRampWithNoDeadTier() {
        int previous = -1;
        for (BallSkin skin : BallSkin.values()) {
            assertTrue("Every ball skin is either earned or priced: " + skin.modelId(),
                skin.defaultPriceCoins() >= 0);
            assertTrue("The catalogue must read as a ramp, cheapest first: " + skin.modelId()
                + " is priced below the skin before it", skin.defaultPriceCoins() >= previous);
            previous = skin.defaultPriceCoins();
            assertEquals("The model id is the enum name in lower case, and the resource pack, "
                + "the glyph block and the item defs all key off it: " + skin.name(),
                "basketball_" + skin.name().toLowerCase(java.util.Locale.ROOT), skin.modelId());
        }
        assertEquals("The Game Ball is the free starting ball", 0, BallSkin.CLASSIC.defaultPriceCoins());
        assertTrue("The animated ball is the top of the catalogue",
            BallSkin.FIRE.defaultPriceCoins() > BallSkin.TWO_TONE.defaultPriceCoins());
    }

    /**
     * The six tiles tile the atlas, and they are addressed the way the client reads UVs.
     *
     * <p>Minecraft maps every texture onto a **0..16** UV grid whatever its pixel size, and the
     * wiki calls anything outside that range "inconsistent"; `texture_size` looks like it
     * changes this but is a Blockbench hint the client never reads. So authoring the tiles in
     * atlas pixels (0..384) is not a cosmetic slip — it samples the top-left 16 pixels of the
     * atlas on all six faces and ships a solid-colour ball. That is what this test is for: the
     * rectangles are read back out of the shipped model, scaled to the shipped image, and
     * required to be six disjoint rectangles that cover it exactly.
     */
    @Test
    public void theSixFaceTilesTileTheAtlasWithNothingLeftOver() throws Exception {
        try (ZipFile pack = new ZipFile(PACK)) {
            for (BallSkin skin : BallSkin.values()) {
                JsonObject model = model(pack, skin.modelId());
                assertFalse("texture_size is a Blockbench hint, not a client field: shipping "
                    + "it invites someone to author UVs outside 0..16 and get one flat colour",
                    model.has("texture_size"));

                String atlasName = "assets/bosketball/textures/item/ball_"
                    + skin.name().toLowerCase(java.util.Locale.ROOT) + ".png";
                ZipEntry atlasEntry = pack.getEntry(atlasName);
                assertNotNull("Missing atlas for " + skin.modelId(), atlasEntry);
                java.awt.image.BufferedImage atlas;
                try (InputStream in = pack.getInputStream(atlasEntry)) {
                    atlas = javax.imageio.ImageIO.read(in);
                }
                assertNotNull("Unreadable atlas: " + atlasName, atlas);
                // Fire ships a vertical strip: one frame of the atlas per 256 rows.
                int frameHeight = skin == BallSkin.FIRE ? atlas.getHeight() / 4 : atlas.getHeight();
                assertEquals("A three-by-two atlas is three tiles wide: " + skin.modelId(),
                    atlas.getWidth(), frameHeight / 2 * 3);

                JsonObject element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
                JsonObject faces = element.getAsJsonObject("faces");
                assertEquals("A cube has six faces and every one is painted: " + skin.modelId(),
                    6, faces.size());

                double scaleX = atlas.getWidth() / 16.0;
                double scaleY = frameHeight / 16.0;
                List<int[]> rects = new ArrayList<>();
                for (String face : faces.keySet()) {
                    JsonArray uv = faces.getAsJsonObject(face).getAsJsonArray("uv");
                    double u1 = uv.get(0).getAsDouble();
                    double v1 = uv.get(1).getAsDouble();
                    double u2 = uv.get(2).getAsDouble();
                    double v2 = uv.get(3).getAsDouble();
                    assertTrue("Face " + face + " of " + skin.modelId() + " is addressed outside "
                        + "the 0..16 UV grid the client reads: " + uv,
                        u1 >= 0 && v1 >= 0 && u2 <= 16 && v2 <= 16 && u2 > u1 && v2 > v1);
                    rects.add(new int[]{(int) Math.round(u1 * scaleX), (int) Math.round(v1 * scaleY),
                        (int) Math.round(u2 * scaleX), (int) Math.round(v2 * scaleY)});
                }
                long area = 0;
                for (int[] a : rects) {
                    area += (long) (a[2] - a[0]) * (a[3] - a[1]);
                    for (int[] b : rects) {
                        if (a == b) {
                            continue;
                        }
                        boolean overlap = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
                        assertFalse("Two faces of " + skin.modelId() + " sample the same pixels: "
                            + java.util.Arrays.toString(a) + " and " + java.util.Arrays.toString(b),
                            overlap);
                    }
                }
                assertEquals("The six tiles must cover the atlas exactly, or the pack ships "
                    + "pixels no face ever samples: " + skin.modelId(),
                    (long) atlas.getWidth() * frameHeight, area);
            }
        }
    }

    /**
     * Two tones split into halves, and no other skin pretends to.
     *
     * <p>The Two-Tone skin is the only one whose leather reads two different colours. On a
     * cube that split is painted, not modelled: it is the `split_axis` of the SKINS entry, so
     * that is what this reads. If a second skin grows one, or Two-Tone loses its own, the
     * pack ships a ball nobody designed — and the contact sheet is the only place it shows.
     */
    @Test
    public void onlyTwoToneSplitsItsLeatherInHalf() throws Exception {
        String python = Files.readString(new File("tools/ball_model.py").toPath());
        List<String> splitters = new ArrayList<>();
        for (BallSkin skin : BallSkin.values()) {
            String key = skin.name().toLowerCase(java.util.Locale.ROOT);
            String block = skinBlock(python, key);
            assertNotNull("tools/ball_model.py has no SKINS entry for " + key, block);
            // `"split_axis": None` on every skin but Two-Tone, whose entry names an axis by
            // index; both forms are read here so a number cannot slip past the check.
            Matcher split = Pattern.compile("\"split_axis\"\\s*:\\s*([^,\\n}]+)").matcher(block);
            assertTrue("The SKINS entry for " + key + " must declare split_axis", split.find());
            if (!split.group(1).trim().equals("None")) {
                splitters.add(key);
            }
        }
        assertEquals("Exactly one skin splits its leather between two colours, and it is "
            + "Two-Tone: " + splitters, java.util.List.of("two_tone"), splitters);
    }

    // ---------------------------------------------------------------- helpers

    private static JsonObject model(ZipFile pack, String modelId) throws Exception {
        ZipEntry entry = pack.getEntry(MODEL_DIR + modelId + ".json");
        assertNotNull("Missing ball model: " + modelId, entry);
        try (InputStream in = pack.getInputStream(entry)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    /** minX, maxX, minY, maxY, minZ, maxZ over every element. */
    private static double[] bounds(JsonObject model) {
        double[] b = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
            Double.MAX_VALUE, -Double.MAX_VALUE};
        for (com.google.gson.JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject box = element.getAsJsonObject();
            if (!box.has("from")) {
                continue;
            }
            JsonArray from = box.getAsJsonArray("from");
            JsonArray to = box.getAsJsonArray("to");
            b[0] = Math.min(b[0], from.get(0).getAsDouble());
            b[1] = Math.max(b[1], to.get(0).getAsDouble());
            b[2] = Math.min(b[2], from.get(1).getAsDouble());
            b[3] = Math.max(b[3], to.get(1).getAsDouble());
            b[4] = Math.min(b[4], from.get(2).getAsDouble());
            b[5] = Math.max(b[5], to.get(2).getAsDouble());
        }
        return b;
    }

    /** Box positions only — no UVs, no light emission, no names. */
    private static List<String> geometry(ZipFile pack, String modelId) throws Exception {
        List<String> out = new ArrayList<>();
        for (com.google.gson.JsonElement element : model(pack, modelId).getAsJsonArray("elements")) {
            JsonObject box = element.getAsJsonObject();
            if (!box.has("from")) {
                continue;
            }
            out.add(box.getAsJsonArray("from") + "->" + box.getAsJsonArray("to"));
        }
        return out;
    }

    /** The `"key": { ... }` object for one skin, sliced out by brace counting. */
    private static String skinBlock(String source, String key) {
        Matcher start = Pattern.compile("\"" + key + "\"\\s*:\\s*\\{").matcher(source);
        if (!start.find()) {
            return null;
        }
        int depth = 0;
        for (int i = start.end() - 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start.end(), i);
                }
            }
        }
        return null;
    }
}
