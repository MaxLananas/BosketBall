package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.ShotReleaseQuality;
import fr.bosketball.physics.PhysicsMath;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.SmallCaps;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.bukkit.util.Vector;
import org.junit.Test;

public class BosketBallEngineTest {

    @Test
    public void testSmallCapsAlphabetAndFrenchAccentsAndMiniMessagePreservation() {
        String alphabet = SmallCaps.toSmallCaps("abcdefghijklmnopqrstuvwxyz");
        assertEquals("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ", alphabet);

        String french = SmallCaps.toSmallCaps("<gradient:#FF7A18:#FFD43B>Équipe Prête à Dunker !</gradient>");
        assertEquals("<gradient:#FF7A18:#FFD43B>ᴇǫᴜɪᴘᴇ ᴘʀᴇᴛᴇ ᴀ ᴅᴜɴᴋᴇʀ !</gradient>", french);
    }

    @Test
    public void testNegativeAndPositiveSpaceShiftExactPixelSum() {
        for (int target = -320; target <= 320; target++) {
            String encoded = Glyphs.shift(target);
            int decoded = decodeShiftAdvance(encoded);
            assertEquals("Mismatch at pixel shift " + target, target, decoded);
        }
    }

    @Test
    public void testBallisticPerfectGreenThreePointerSwishesCleanlyThroughRim() {
        CourtGeometry court = new CourtGeometry(0.5, 64.0, 0.5);
        Vector rim = court.southHoopRim();
        Vector shooterEye = new Vector(0.5, 65.62, rim.getZ() - 8.20); // Top-of-the-key 3-pointer!

        assertTrue(court.isThreePointShot(shooterEye.getX(), shooterEye.getZ(), rim));

        double g = 0.046;
        double drag = 0.991;
        Vector vel = PhysicsMath.solveBallisticArc(
            shooterEye, rim, g, drag, ShotReleaseQuality.PERFECT_GREEN, null
        );

        Vector pos = shooterEye.clone();
        double dt = 0.25;
        double subDrag = Math.pow(drag, dt);
        boolean scored = false;
        int rimHits = 0;

        for (int tick = 0; tick < 60 && !scored; tick++) {
            for (int step = 0; step < 4; step++) {
                Vector prev = pos.clone();
                vel.setY(vel.getY() - g * dt);
                vel.multiply(subDrag);
                pos.add(vel.clone().multiply(dt));

                PhysicsMath.CollisionResult rimCol = PhysicsMath.collideWithRimTorus(pos, vel, rim, 0.56);
                if (rimCol.hit()) {
                    pos = rimCol.position();
                    vel = rimCol.velocity();
                    rimHits++;
                }

                if (PhysicsMath.didScoreDownwardThroughHoop(prev, pos, rim)) {
                    scored = true;
                    break;
                }
            }
        }

        assertTrue("Expected PERFECT_GREEN 3-pointer to swish downward through rim!", scored);
        assertEquals("Expected pure swish with 0 rim hits on PERFECT_GREEN release!", 0, rimHits);
    }

    @Test
    public void testRimTorusAndBackboardCollisions() {
        Vector rim = new Vector(0.0, 67.05, 13.95);
        // Ball grazing front iron moving downward and forward (+Z)
        Vector onFrontIron = new Vector(0.0, 67.05 + 0.20, 13.95 - PhysicsMath.RIM_MAJOR_RADIUS);
        Vector vel = new Vector(0.0, -0.25, 0.18);

        PhysicsMath.CollisionResult res = PhysicsMath.collideWithRimTorus(onFrontIron, vel, rim, 0.56);
        assertTrue("Ball striking front iron must register torus collision", res.hit());
        assertTrue("Ball must bounce upward off top of front iron", res.velocity().getY() > 0.0);

        // Ball striking South Backboard (normalSignZ = -1, glass at rimZ + 0.63)
        Vector nearGlass = new Vector(0.15, 67.45, 13.95 + PhysicsMath.BACKBOARD_OFFSET - 0.15);
        Vector velIntoGlass = new Vector(0.02, -0.05, 0.35);
        PhysicsMath.CollisionResult glassRes = PhysicsMath.collideWithBackboard(nearGlass, velIntoGlass, rim, -1, 0.66);
        assertTrue("Ball hitting glass must reflect", glassRes.hit());
        assertTrue("Reflected velocity Z must rebound back toward court (-Z)", glassRes.velocity().getZ() < 0.0);
    }

    @Test
    public void testCourtZonesAndThreePointContestRacks() {
        CourtGeometry geo = new CourtGeometry(0.0, 64.0, 0.0);
        Vector southRim = geo.southHoopRim();

        // Corner 3 check
        assertTrue(geo.isThreePointShot(6.85, southRim.getZ() - 1.2, southRim));
        // Paint 2-pointer check
        assertFalse(geo.isThreePointShot(1.2, southRim.getZ() - 2.5, southRim));
        assertTrue(geo.isInDunkZone(1.2, southRim.getZ() - 2.5, southRim));

        List<Vector> racks = geo.threePointContestRacks();
        assertEquals(5, racks.size());
        for (Vector r : racks) {
            assertTrue("Every 3PT contest rack must be behind the 3PT line", geo.isThreePointShot(r.getX(), r.getZ(), southRim));
        }
    }

    @Test
    public void testDunkStylesAndLeaderboardBadgesAndRanks() {
        for (DunkStyle style : DunkStyle.values()) {
            Vector start = style.computeBallOffset(0.0, 0.0);
            Vector mid = style.computeBallOffset(0.5, 0.0);
            Vector end = style.computeBallOffset(1.0, 0.0);
            assertNotNull(start);
            assertNotNull(mid);
            assertNotNull(end);
            assertTrue(end.getY() > 1.0);
        }

        assertEquals(Rank.ROOKIE, Rank.fromElo(1000));
        assertEquals(Rank.ALL_STAR, Rank.fromElo(1450));
        assertEquals(Rank.MVP, Rank.fromElo(2350));

        StatsManager sm = new StatsManager(new File("/tmp/bb-test-" + System.nanoTime()), null);
        PlayerProfile a = sm.getOrCreate(UUID.randomUUID(), "Refortorr");
        a.setWins(25);
        a.setElo(1850);
        a.setPoints(420);

        PlayerProfile b = sm.getOrCreate(UUID.randomUUID(), "A_Koop");
        b.setWins(18);
        b.setElo(2250);
        b.setPoints(310);

        sm.refreshLeaderboardBadges();
        StatsManager.LeaderboardBadges badgesA = sm.getBadges(a.uuid());
        StatsManager.LeaderboardBadges badgesB = sm.getBadges(b.uuid());

        assertEquals(1, badgesA.winsPlace());
        assertEquals(2, badgesA.eloPlace());
        assertEquals(1, badgesA.pointsPlace());
        assertEquals(1, badgesB.eloPlace());
    }

    @Test
    public void testResourcePack262Format88Integrity() throws Exception {
        File mcmeta = new File("resourcepack/pack.mcmeta");
        assertTrue("resourcepack/pack.mcmeta must exist", mcmeta.exists());
        String raw = Files.readString(mcmeta.toPath());
        assertTrue(raw.contains("\"min_format\": 65"));
        assertTrue(raw.contains("\"max_format\": 88"));
        assertFalse("26.2 forbids legacy supported_formats when min_format >= 65", raw.contains("supported_formats"));

        File zipFile = new File("releases/BosketBall-ResourcePack-26.2.zip");
        assertTrue("Packaged BosketBall-ResourcePack-26.2.zip must exist", zipFile.exists() && zipFile.length() > 100_000);

        // Verify SHA-1 in config.yml matches BosketBall-ResourcePack-26.2.zip.sha1!
        String expectedSha1 = Files.readString(new File("releases/BosketBall-ResourcePack-26.2.zip.sha1").toPath()).trim();
        String configYml = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("config.yml external-sha1 must match latest ResourcePack SHA-1!", configYml.contains(expectedSha1));

        // Verify EVERY sound & custom model referenced across Java source files exists inside the ZIP!
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
            var soundsEntry = zf.getEntry("assets/bosketball/sounds.json");
            assertNotNull("sounds.json must exist in pack ZIP", soundsEntry);
            String soundsJson = new String(zf.getInputStream(soundsEntry).readAllBytes());

            String[] requiredSounds = {
                "ball.bounce", "ball.swish", "ball.rim_hit", "ball.backboard",
                "game.dunk_boom", "game.buzzer", "game.whistle", "game.green_release",
                "crowd.cheer", "shoe.squeak", "ui.click", "defense.block", "empty"
            };
            for (String snd : requiredSounds) {
                assertTrue("sounds.json must define sound key: " + snd, soundsJson.contains("\"" + snd + "\""));
            }

            String[] requiredModels = {
                "basketball_classic", "basketball_money", "basketball_neon", "basketball_fire",
                "hoop_idle", "hoop_swish_1", "hoop_swish_2", "hoop_dunk_flex",
                "trophy_mvp", "referee_whistle", "sneakers_air", "tactics_playbook"
            };
            for (String mdl : requiredModels) {
                assertNotNull("items/" + mdl + ".json must exist in ZIP", zf.getEntry("assets/bosketball/items/" + mdl + ".json"));
                assertNotNull("models/item/" + mdl + ".json must exist in ZIP", zf.getEntry("assets/bosketball/models/item/" + mdl + ".json"));
            }

            // Also scan ALL .java source files and assert EVERY literal sound & custom model ID exists in the ZIP!
            var soundPat = java.util.regex.Pattern.compile("(?:playPackSound|playPersonalPackSound)\\([^,]+,\\s*\"([^\"]+)\"");
            var modelPat = java.util.regex.Pattern.compile("createCustomModelItem\\([^,]+,\\s*\"([^\"]+)\"");
            try (var paths = Files.walk(new File("src/main/java").toPath())) {
                for (var p : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(p);
                    var sm = soundPat.matcher(src);
                    while (sm.find()) {
                        String key = sm.group(1);
                        assertTrue("Java file " + p.getFileName() + " uses undefined sound '" + key + "'",
                            soundsJson.contains("\"" + key + "\""));
                    }
                    var mm = modelPat.matcher(src);
                    while (mm.find()) {
                        String mdl = mm.group(1);
                        assertNotNull("Java file " + p.getFileName() + " uses undefined item model '" + mdl + "'",
                            zf.getEntry("assets/bosketball/items/" + mdl + ".json"));
                    }
                }
            }
        }
    }

    private static int decodeShiftAdvance(String s) {
        int sum = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\uE001' && c <= '\uE009') {
                sum -= (1 << (c - '\uE001'));
            } else if (c >= '\uE011' && c <= '\uE019') {
                sum += (1 << (c - '\uE011'));
            }
        }
        return sum;
    }
}
