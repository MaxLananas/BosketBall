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
                "trophy_mvp", "referee_whistle", "sneakers_air", "tactics_playbook", "court_wand"
            };
            for (String mdl : requiredModels) {
                assertNotNull("items/" + mdl + ".json must exist in ZIP", zf.getEntry("assets/bosketball/items/" + mdl + ".json"));
                assertNotNull("models/item/" + mdl + ".json must exist in ZIP", zf.getEntry("assets/bosketball/models/item/" + mdl + ".json"));
            }

            // Verify zero hand-glued duplicate ball in 1st & 3rd person while preserving GUI hotbar 3D cube!
            for (String ballMdl : new String[]{"basketball_classic", "basketball_money", "basketball_neon", "basketball_fire"}) {
                String mJson = new String(zf.getInputStream(zf.getEntry("assets/bosketball/models/item/" + ballMdl + ".json")).readAllBytes());
                assertTrue(ballMdl + " must hide held ball in hand via [0.0, 0.0, 0.0] scale", mJson.contains("0.0"));
                assertTrue(ballMdl + " must preserve 0.98 3D cube scale in GUI hotbar", mJson.contains("0.98"));
            }
            assertNotNull("aim_reticles.png must exist in font textures", zf.getEntry("assets/bosketball/textures/font/aim_reticles.png"));

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

    @Test
    public void testSkillDirectionalAimingNeverAutoScoresWhenAimingAtBleachers() {
        CourtGeometry court = new CourtGeometry(0.5, 64.0, 0.5);
        Vector rim = court.southHoopRim();
        Vector eye = new Vector(0.5, 65.62, rim.getZ() - 7.80);
        double g = 0.046;
        double drag = 0.991;

        // 1. Aiming 60° sideways toward the bleachers with PERFECT_GREEN timing MUST throw into the stands, NOT the hoop!
        Vector bleacherLook = new Vector(Math.sin(Math.toRadians(60)), 0.18, Math.cos(Math.toRadians(60))).normalize();
        PhysicsMath.AimEvaluation offAim = PhysicsMath.evaluateShooterAim(eye, bleacherLook, rim);
        assertFalse("Looking 60° toward bleachers must NOT lock onto hoop!", offAim.aimingAtHoop());

        Vector bleacherVel = PhysicsMath.solveSkillShotVelocity(
            eye, bleacherLook, rim, court.southHoopForward(), offAim, 0.92, g, drag, ShotReleaseQuality.PERFECT_GREEN, 0.0, null
        );
        List<Vector> bleacherArc = PhysicsMath.simulatePreviewArc(eye, bleacherVel, g, drag, court.floorY(), 40);
        for (int i = 1; i < bleacherArc.size(); i++) {
            assertFalse("Shot aimed at bleachers must NEVER enter hoop!",
                PhysicsMath.didScoreDownwardThroughHoop(bleacherArc.get(i - 1), bleacherArc.get(i), rim));
        }
        assertTrue("Shot aimed +X into bleachers must travel strongly toward +X!", bleacherArc.get(bleacherArc.size() - 1).getX() > 5.0);

        // 2. Aiming 9° off-center left of the rim MUST miss left (no heat-seeking magnet!)
        Vector toRim = rim.clone().subtract(eye).setY(0).normalize();
        double baseAngle = Math.atan2(toRim.getX(), toRim.getZ());
        double leftAngle = baseAngle + Math.toRadians(9.0);
        Vector wideLeftLook = new Vector(Math.sin(leftAngle), 0.32, Math.cos(leftAngle)).normalize();
        PhysicsMath.AimEvaluation wideAim = PhysicsMath.evaluateShooterAim(eye, wideLeftLook, rim);
        assertTrue(wideAim.aimingAtHoop());
        Vector wideVel = PhysicsMath.solveSkillShotVelocity(
            eye, wideLeftLook, rim, court.southHoopForward(), wideAim, 0.80, g, drag, ShotReleaseQuality.SLIGHTLY_EARLY, 0.0, null
        );
        List<Vector> wideArc = PhysicsMath.simulatePreviewArc(eye, wideVel, g, drag, court.floorY(), 45);
        boolean wideScored = false;
        for (int i = 1; i < wideArc.size(); i++) {
            if (PhysicsMath.didScoreDownwardThroughHoop(wideArc.get(i - 1), wideArc.get(i), rim)) {
                wideScored = true;
            }
        }
        assertFalse("Aiming 9° wide with non-green release must NOT magnetically score!", wideScored);

        // 3. Deadeye center aim (0° yaw error) + PERFECT_GREEN (0.92) + Open (0% contest) MUST pure-swish!
        Vector deadeyeLook = new Vector(toRim.getX(), 0.35, toRim.getZ()).normalize();
        PhysicsMath.AimEvaluation deadAim = PhysicsMath.evaluateShooterAim(eye, deadeyeLook, rim);
        assertTrue(deadAim.aimingAtHoop());
        assertEquals(0.0, deadAim.yawErrorDeg(), 0.05);
        Vector swishVel = PhysicsMath.solveSkillShotVelocity(
            eye, deadeyeLook, rim, court.southHoopForward(), deadAim, 0.92, g, drag, ShotReleaseQuality.PERFECT_GREEN, 0.0, null
        );
        Vector pos = eye.clone();
        Vector vel = swishVel.clone();
        boolean swished = false;
        double subDrag = Math.pow(drag, 0.25);
        for (int t = 0; t < 55 && !swished; t++) {
            for (int s = 0; s < 4; s++) {
                Vector prev = pos.clone();
                vel.setY(vel.getY() - g * 0.25);
                vel.multiply(subDrag);
                pos.add(vel.clone().multiply(0.25));
                if (PhysicsMath.didScoreDownwardThroughHoop(prev, pos, rim)) {
                    swished = true;
                    break;
                }
            }
        }
        assertTrue("Deadeye center aim + PERFECT_GREEN must swish cleanly!", swished);
    }

    @Test
    public void testCustomMapBoundsEastWestHoopsBarriersAndYamlPersistence() {
        File tmpDir = new File("/tmp/bb-map-test-" + System.nanoTime());
        fr.bosketball.arena.ArenaManager mgr = new fr.bosketball.arena.ArenaManager(tmpDir, null);

        CourtGeometry geo = new CourtGeometry(100.5, 70.0, 200.5);
        geo.setCourtBounds(84.0, 191.0, 116.0, 209.0, 70.0);
        geo.setBarrierBounds(80.0, 187.0, 120.0, 213.0, 88.0);
        // Custom East-West oriented hoops!
        geo.setSouthHoop(new Vector(114.5, 73.05, 200.0), new Vector(-1, 0, 0), false);
        geo.setNorthHoop(new Vector(85.5, 73.05, 200.0), new Vector(1, 0, 0), true);

        fr.bosketball.arena.Arena custom = new fr.bosketball.arena.Arena("rooftop", "ʀᴏᴏꜰᴛᴏᴘ x", "world", geo);
        custom.setCustomMap(true);
        custom.setJumbotronEnabled(false);
        custom.replaceCustomSpawns(fr.bosketball.model.TeamSide.IGNIS, List.of(new fr.bosketball.arena.Arena.SpawnPose(96.5, 70.1, 200.0, -90f, 0f)));
        custom.replaceCustomSpawns(fr.bosketball.model.TeamSide.FROST, List.of(new fr.bosketball.arena.Arena.SpawnPose(104.5, 70.1, 200.0, 90f, 0f)));
        custom.setLobbyExitSpawn(new fr.bosketball.arena.Arena.SpawnPose(50.5, 65.0, 50.5, 0f, 0f));

        // Verify 360° East-facing backboard collision on North/West hoop (forward = +X, glass at X = 85.5 - 0.65 = 84.85)!
        PhysicsMath.CollisionResult glassHit = PhysicsMath.collideWithBackboard(
            new Vector(85.05, 73.35, 200.0),
            new Vector(-0.42, 0.05, 0.0),
            geo.northHoopRim(),
            geo.northHoopForward(),
            0.66
        );
        assertTrue("360° East-facing backboard must reflect incoming -X shot!", glassHit.hit());
        assertTrue("Reflected X velocity must bounce toward +X!", glassHit.velocity().getX() > 0.20);

        // Verify ball containment cage bounces escaping ball back inside!
        Vector escapingPos = new Vector(125.0, 92.0, 200.0);
        Vector escapingVel = new Vector(0.8, 0.5, 0.0);
        assertTrue(custom.clampBallInsideBarrier(escapingPos, escapingVel, 0.72));
        assertTrue(escapingPos.getX() <= 120.0 - PhysicsMath.BALL_RADIUS + 1e-4);
        assertTrue(escapingPos.getY() <= 88.0 - PhysicsMath.BALL_RADIUS + 1e-4);
        assertTrue(escapingVel.getX() < 0 && escapingVel.getY() < 0);

        // Save to YAML & reload into a fresh ArenaManager (world not loaded in unit test so spawnDisplays safely no-ops)!
        mgr.registerArena(custom);
        fr.bosketball.arena.ArenaManager reloadedMgr = new fr.bosketball.arena.ArenaManager(tmpDir, null);
        reloadedMgr.load();
        fr.bosketball.arena.Arena loaded = reloadedMgr.getArena("rooftop");
        assertNotNull(loaded);
        assertTrue(loaded.isCustomMap());
        assertFalse(loaded.isJumbotronEnabled());
        assertEquals(80.0, loaded.geometry().barrierMinX(), 1e-3);
        assertEquals(120.0, loaded.geometry().barrierMaxX(), 1e-3);
        assertEquals(-1.0, loaded.geometry().southHoopForward().getX(), 1e-3);
        assertFalse(loaded.geometry().southSpawn3dHoop());
        assertEquals(1, loaded.allCustomSpawns().get(fr.bosketball.model.TeamSide.IGNIS).size());
        assertNotNull(loaded.lobbyExitSpawn());
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
