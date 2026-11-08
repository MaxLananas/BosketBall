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
    public void everySkinShipsOneModelAtTheRadiusTheSimulationCollidesAt() throws Exception {
        assertEquals("The catalogue is eight skins and stays eight: the tests, the glyph block "
            + "and the shop page all assume that count", 8, BallSkin.values().length);
        try (ZipFile pack = new ZipFile(PACK)) {
            for (BallSkin skin : BallSkin.values()) {
                JsonObject model = model(pack, skin.modelId());
                assertTrue("A model with no elements is an invisible ball: " + skin.modelId(),
                    model.getAsJsonArray("elements").size() > 100);

                double[] bounds = bounds(model);
                double centreX = (bounds[0] + bounds[1]) / 2.0;
                double centreY = (bounds[2] + bounds[3]) / 2.0;
                double centreZ = (bounds[4] + bounds[5]) / 2.0;
                assertEquals("The ball must be centred on x=8, or it orbits its own spin pivot: "
                    + skin.modelId(), 8.0, centreX, 0.75);
                assertEquals("The ball must be centred on y=8: " + skin.modelId(), 8.0, centreY, 0.75);
                assertEquals("The ball must be centred on z=8: " + skin.modelId(), 8.0, centreZ, 0.75);

                // Half-extent of the widest axis is the worn radius: the leather sphere plus the
                // overlap that keeps neighbouring boxes from z-fighting, plus the channel ribs
                // standing proud of the leather. Both are fractions of the radius in
                // tools/ball_model.py, so the whole silhouette scales as one object.
                double radius = Math.max((bounds[1] - bounds[0]), Math.max(bounds[3] - bounds[2],
                    bounds[5] - bounds[4])) / 2.0;
                assertEquals("The ball must be authored at the simulated radius, not the old "
                    + "toy-sized 0.598-block cube, which is 18.4 units and a third too big: "
                    + skin.modelId(),
                    EXPECTED_RADIUS_UNITS, radius, 1.0);
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
                assertEquals("Every skin must place its seams in the same place as the Game Ball: "
                    + skin.modelId(), reference.subList(0, Math.min(64, reference.size())),
                    other.subList(0, Math.min(64, other.size())));
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

    /** One 16 px palette atlas per skin, and the animated skin is the only one that needs a strip. */
    @Test
    public void oneTexelAtlasPerSkinAndOnlyFireAnimates() throws Exception {
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
                assertTrue("Every skin needs its own palette atlas: " + atlas, textures.contains(atlas));
                assertFalse("The six cube faces are gone; shipping one again means two sources "
                    + "of truth for the same ball: " + skin.modelId(),
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
     * The channel network sits on the sphere, not beside it.
     *
     * <p>Every element is authored in absolute 0..16 space, but the three great circles are
     * generated around the origin — so forgetting the +8 translation puts the whole rib network
     * eight units off the ball it is supposed to lie on. That bug is invisible in a diff, easy
     * to miss on a hero render with the seams cropped, and glaring in game. Asserting the radial
     * distance of every rib box is the only check that catches it.
     */
    @Test
    public void channelRibsSitOnTheSphere() throws Exception {
        // tools/ball_model.py CHANNEL_LIFT_RATIO: ribs stand 5% of the radius proud.
        double lift = EXPECTED_RADIUS_UNITS * 1.05;
        try (ZipFile pack = new ZipFile(PACK)) {
            JsonObject model = model(pack, BallSkin.CLASSIC.modelId());
            int ribs = 0;
            for (com.google.gson.JsonElement element : model.getAsJsonArray("elements")) {
                JsonObject box = element.getAsJsonObject();
                if (!box.has("from") || !box.get("name").getAsString().startsWith("seam_")) {
                    continue;
                }
                ribs++;
                double radial = radialDistance(box, 8.0);
                assertEquals("A channel rib must lie on the leather sphere it cuts, not float "
                    + "off it: " + box.get("name").getAsString(), lift, radial, 0.6);
            }
            assertTrue("The three great circles are 96 ribs each; a shorter chain leaves a gap "
                + "a player can see through: found " + ribs, ribs >= 256);
            assertEquals("Three great circles cut eight panels, so the rib count is a multiple "
                + "of three", 0, ribs % 3);
        }
    }

    /**
     * Two tones split into halves, and no other skin pretends to.
     *
     * <p>The Two-Tone skin is the only one whose panels read two different colours; it earns
     * that by pointing half of them at a different palette row. If the split stops being a
     * half, or leaks into a skin that is meant to be one colour, the pack ships a ball nobody
     * designed.
     */
    @Test
    public void onlyTwoToneSplitsItsPanelsInHalf() throws Exception {
        try (ZipFile pack = new ZipFile(PACK)) {
            for (BallSkin skin : BallSkin.values()) {
                JsonObject model = model(pack, skin.modelId());
                int rowTwo = 0;
                int panels = 0;
                for (com.google.gson.JsonElement element : model.getAsJsonArray("elements")) {
                    JsonObject box = element.getAsJsonObject();
                    if (!box.has("from") || !box.get("name").getAsString().startsWith("panel_")) {
                        continue;
                    }
                    panels++;
                    // Every face of a box carries the same palette texel, so any face will
                    // do; the accent ramp lives on palette row 4 (row 0-1 leather, 2 channel,
                    // 3-4 accent). v is a whole texel here, so it compares exactly.
                    JsonObject faces = box.getAsJsonObject("faces");
                    double v = faces.getAsJsonObject(faces.keySet().iterator().next())
                        .getAsJsonArray("uv").get(1).getAsDouble();
                    if (v >= 3.0) {
                        rowTwo++;
                    }
                }
                assertTrue("A skin with no panels is an invisible ball: " + skin.modelId(),
                    panels > 100);
                if (skin == BallSkin.TWO_TONE) {
                    double half = rowTwo / (double) panels;
                    assertTrue("Two-Tone must split its panels into two halves, not colour a "
                        + "few of them: " + rowTwo + " of " + panels,
                        half > 0.35 && half < 0.65);
                } else {
                    assertEquals("Only Two-Tone splits its panels between two colours: "
                        + skin.modelId(), 0, rowTwo);
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Distance from a box's centre to the model's own centre. */
    private static double radialDistance(JsonObject box, double centre) {
        double sum = 0.0;
        for (int axis = 0; axis < 3; axis++) {
            double mid = (box.getAsJsonArray("from").get(axis).getAsDouble()
                + box.getAsJsonArray("to").get(axis).getAsDouble()) / 2.0 - centre;
            sum += mid * mid;
        }
        return Math.sqrt(sum);
    }

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
