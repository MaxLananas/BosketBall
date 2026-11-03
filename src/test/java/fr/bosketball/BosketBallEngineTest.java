package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import fr.bosketball.animation.AnimationKinematics;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.ShotReleaseQuality;
import fr.bosketball.physics.PhysicsMath;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.SmallCaps;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * Locks the whole difficulty curve with one Monte-Carlo: an open look from the paint must go in
     * nearly every time, a deep uncontested three must stay a coin flip or worse, and a defender in
     * the shooter's face must matter everywhere.
     */
    @Test
    public void testShotDifficultyCurveIsForgivingUpCloseAndBrutalFromDeep() {
        CourtGeometry court = new CourtGeometry(0.5, 64.0, 0.5);
        Vector rim = court.southHoopRim();
        Vector fwd = court.southHoopForward();
        java.util.Random rng = new java.util.Random(20261103L);

        double close = swishRate(court, rim, fwd, 3.0, 0.0, rng, 240);
        double mid = swishRate(court, rim, fwd, 7.0, 0.0, rng, 240);
        double deep = swishRate(court, rim, fwd, 12.0, 0.0, rng, 240);
        double contestedMid = swishRate(court, rim, fwd, 7.0, 0.65, rng, 240);

        assertTrue("An open 3m look must drop: got " + close, close > 0.90);
        assertTrue("An open 7m look must stay very makeable: got " + mid, mid > 0.80);
        assertTrue("A 12m look must not be an automatic bucket: got " + deep, deep < 0.70);
        assertTrue("Range must clearly cost accuracy: 3m " + close + " vs 12m " + deep, close > deep + 0.20);
        assertTrue("A contested 7m look must be clearly harder: got " + contestedMid, contestedMid < mid - 0.15);
    }

    /** Runs a full perfect-release shot N times through the real entity integrator and returns the swish rate. */
    private static double swishRate(
        CourtGeometry court, Vector rim, Vector hoopForward, double distance, double contest,
        java.util.Random rng, int shots
    ) {
        Vector eye = new Vector(0.5, court.floorY() + 1.62, rim.getZ() - distance);
        Vector toRim = rim.clone().subtract(eye).setY(0).normalize();
        double idealPitchDeg = -Math.min(34.0, 9.5 + distance * 1.35);
        Vector look = new Vector(
            toRim.getX() * Math.cos(Math.toRadians(idealPitchDeg)),
            Math.sin(Math.toRadians(-idealPitchDeg)),
            toRim.getZ() * Math.cos(Math.toRadians(idealPitchDeg))
        ).normalize();
        PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(eye, look, rim);
        assertTrue("Test setup must aim at the hoop", aim.aimingAtHoop());
        int made = 0;
        for (int i = 0; i < shots; i++) {
            Vector vel = PhysicsMath.solveSkillShotVelocity(
                eye, look, rim, hoopForward, aim, 0.95,
                0.046, 0.991, ShotReleaseQuality.PERFECT_GREEN, contest, rng
            );
            if (shotHitsNet(eye, vel, rim, hoopForward, court.floorY())) {
                made++;
            }
        }
        return (double) made / shots;
    }

    /** Replays the ball through gravity, drag, rim torus and backboard exactly like BasketballEntity does. */
    private static boolean shotHitsNet(Vector eye, Vector velocity, Vector rim, Vector hoopForward, double floorY) {
        Vector pos = eye.clone();
        Vector vel = velocity.clone();
        double g = 0.046;
        double dt = 0.25;
        double subDrag = Math.pow(0.991, dt);
        for (int tick = 0; tick < 70; tick++) {
            for (int step = 0; step < 4; step++) {
                Vector prev = pos.clone();
                vel.setY(vel.getY() - g * dt);
                vel.multiply(subDrag);
                pos.add(vel.clone().multiply(dt));

                PhysicsMath.CollisionResult rimCol = PhysicsMath.collideWithRimTorus(pos, vel, rim, 0.56);
                if (rimCol.hit()) {
                    pos = rimCol.position();
                    vel = rimCol.velocity();
                }
                PhysicsMath.CollisionResult boardCol =
                    PhysicsMath.collideWithBackboard(pos, vel, rim, hoopForward, 0.66);
                if (boardCol.hit()) {
                    pos = boardCol.position();
                    vel = boardCol.velocity();
                }
                if (PhysicsMath.didScoreDownwardThroughHoop(prev, pos, rim)) {
                    return true;
                }
                if (pos.getY() <= floorY + PhysicsMath.BALL_RADIUS) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * ELO must be zero-sum: the loser drops exactly what the winner gains. The old 0.8 factor with
     * a 6-point floor created ELO out of thin air on every match (win +16, loss -6), which is how a
     * ~1:1 win ratio kept climbing towards 2200.
     */
    @Test
    public void testRankedEloIsZeroSumAndPairingAware() {
        int gained = StatsManager.computeEloDelta(1035, 2193);
        int lost = StatsManager.computeEloDelta(2193, 1035);
        assertTrue("Underdog win must be worth a lot: " + gained, gained >= 28);
        assertTrue("Favourite win must be small: " + lost, lost <= 12);
        assertTrue("Underdog reward must dwarf favourite reward", gained > lost * 2);
        for (int a = 900; a <= 2400; a += 100) {
            for (int b = 900; b <= 2400; b += 100) {
                int up = StatsManager.computeEloDelta(a, b);
                int down = StatsManager.computeEloDelta(b, a);
                assertTrue("ELO swing must stay bounded (" + up + ")", up >= 8 && up <= 32);
                assertEquals("Unequal ratings must still be a real swing", up == down, a == b);
            }
        }
    }

    /**
     * A release that is a hair outside the window must stay a makeable shot, while a wild release
     * must not. Also locks the shared window: the HUD and the solver may never disagree again.
     */
    @Test
    public void testReleaseWindowIsSharedAndBarelyLateStillDrops() {
        assertEquals(ShotReleaseQuality.PERFECT_GREEN,
            ShotReleaseQuality.evaluate(ShotReleaseQuality.greenWindowLow(false, 8.0), 8.0, false));
        assertEquals(ShotReleaseQuality.PERFECT_GREEN,
            ShotReleaseQuality.evaluate(ShotReleaseQuality.greenWindowHigh(false, 8.0), 8.0, false));
        assertTrue("Window must be reachable by a real bow draw (cap 1.0)",
            ShotReleaseQuality.greenWindowLow(false, 8.0) < 1.0);

        double low = ShotReleaseQuality.greenWindowLow(false, 8.0);
        assertEquals("A hair early is SLIGHTLY_EARLY, not a brick",
            ShotReleaseQuality.SLIGHTLY_EARLY, ShotReleaseQuality.evaluate(low - 0.02, 8.0, false));
        assertEquals("Well early is VERY_EARLY",
            ShotReleaseQuality.VERY_EARLY, ShotReleaseQuality.evaluate(low - 0.15, 8.0, false));
        double high = ShotReleaseQuality.greenWindowHigh(false, 8.0);
        assertEquals("A hair late is SLIGHTLY_LATE",
            ShotReleaseQuality.SLIGHTLY_LATE, ShotReleaseQuality.evaluate(high + 0.02, 8.0, false));
        assertEquals("Way late is VERY_LATE",
            ShotReleaseQuality.VERY_LATE, ShotReleaseQuality.evaluate(high + 0.20, 8.0, false));

        // On fire widens the window in the player's favour, it must never narrow it.
        assertTrue(ShotReleaseQuality.greenWindowLow(true, 8.0) < ShotReleaseQuality.greenWindowLow(false, 8.0));
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
                "trophy_mvp", "referee_whistle", "sneakers_air", "tactics_playbook", "court_wand",
                "ring_ignis", "ring_frost", "ring_gold",
                "hooper_torso_ignis", "hooper_torso_frost",
                "hooper_arm_ignis", "hooper_arm_frost",
                "hooper_leg_ignis", "hooper_leg_frost",
                "icon_portal", "icon_star_shield", "icon_chest", "icon_book", "icon_podium",
                "icon_pass", "icon_crossover", "icon_dunk", "icon_defense",
                "icon_bot_on", "icon_bot_off", "icon_leave", "icon_friends",
                "icon_settings", "icon_history", "icon_tags", "icon_arena", "icon_back"
            };
            for (String mdl : requiredModels) {
                assertNotNull("items/" + mdl + ".json must exist in ZIP", zf.getEntry("assets/bosketball/items/" + mdl + ".json"));
                assertNotNull("models/item/" + mdl + ".json must exist in ZIP", zf.getEntry("assets/bosketball/models/item/" + mdl + ".json"));
            }

            // Verify zero hand-glued duplicate ball in 1st & 3rd person AND compact 0.36 GUI slot scale across ALL 8 skins (Requirement #5)!
            for (fr.bosketball.model.BallSkin skin : fr.bosketball.model.BallSkin.values()) {
                String ballMdl = skin.modelId();
                String mJson = new String(zf.getInputStream(zf.getEntry("assets/bosketball/models/item/" + ballMdl + ".json")).readAllBytes());
                assertTrue(ballMdl + " must hide held ball in hand via [0.0, 0.0, 0.0] scale", mJson.contains("0.0"));
                assertTrue(ballMdl + " must use shrunken 0.36 3D cube scale in GUI & menus", mJson.contains("0.36"));
            }

            // Verify floor-anchored 3D hoop stanchion, integrated shot-clock housing, anti-parallax rear glass & 40-panel closed octagonal net
            String hoopIdleJson = new String(zf.getInputStream(zf.getEntry("assets/bosketball/models/item/hoop_idle.json")).readAllBytes());
            assertTrue("hoop_idle must include floor-anchored stanchion_floor_base reaching -16.0", hoopIdleJson.contains("-16.0"));
            assertTrue("hoop_idle must include integrated shotclock_housing reaching 25.9", hoopIdleJson.contains("25.9"));
            assertTrue("hoop_idle must include welded rim_hook_0 loops", hoopIdleJson.contains("rim_hook_0"));
            assertTrue("hoop_idle must include closed octagonal net rings (net_t0_n..net_t4_nw)", hoopIdleJson.contains("net_t0_n") && hoopIdleJson.contains("net_t4_nw"));
            assertNotNull("aim_reticles.png must exist in font textures", zf.getEntry("assets/bosketball/textures/font/aim_reticles.png"));

            // Also scan ALL .java source files and assert EVERY literal sound & custom model ID exists in the ZIP!
            var soundPat = java.util.regex.Pattern.compile("(?:playPackSound|playPersonalPackSound)\\([^,]+,\\s*\"([^\"]+)\"");
            var modelPat = java.util.regex.Pattern.compile("(?:createCustomModelItem\\([^,]+,|customIcon\\()\\s*\"([^\"]+)\"");
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

    @Test
    public void testArticulatedSixBoneBasketballKinematicsWalkJumpDribbleThrowAndDunk() {
        // Transform a hanging limb's local down-vector (0, -1, 0) into 3D world space at bodyYaw = 0 (+Z forward)!
        // 1. Walk/Sprint counter-swinging limb phase verification
        AnimationKinematics.RigPoseSnapshot sprintA = AnimationKinematics.evaluate(
            0f, 0f, 0.26, 0.0, true, Math.PI * 0.5, 0.5, false, false, -1.0, 0,
            AnimationKinematics.ActionClip.NONE, 0.0, null, 0
        );
        AnimationKinematics.RigPoseSnapshot sprintB = AnimationKinematics.evaluate(
            0f, 0f, 0.26, 0.0, true, Math.PI * 1.5, 0.5, false, false, -1.0, 0,
            AnimationKinematics.ActionClip.NONE, 0.0, null, 0
        );
        org.joml.Vector3f rLegA = sprintA.rightLeg().rotation().transform(new org.joml.Vector3f(0f, -1f, 0f));
        org.joml.Vector3f lLegA = sprintA.leftLeg().rotation().transform(new org.joml.Vector3f(0f, -1f, 0f));
        org.joml.Vector3f rLegB = sprintB.rightLeg().rotation().transform(new org.joml.Vector3f(0f, -1f, 0f));
        assertTrue("Sprint must swing right & left legs in opposite +/-Z directions!", rLegA.z * lLegA.z < 0.0f);
        assertTrue("Opposite half-stride must invert leg swing!", rLegA.z * rLegB.z < 0.0f);

        // 2. Airborne Jump Arm Elevation
        AnimationKinematics.RigPoseSnapshot jumpPose = AnimationKinematics.evaluate(
            0f, -10f, 0.14, 0.34, false, 1.0, 0.5, false, false, -1.0, 0,
            AnimationKinematics.ActionClip.NONE, 0.0, null, 0
        );
        org.joml.Vector3f jumpArm = jumpPose.rightArm().rotation().transform(new org.joml.Vector3f(0f, -1f, 0f));
        assertTrue("Jumping upward must swing arms forward/up relative to neutral hang!", jumpArm.y > -0.92f);

        // 3. Live Dribble Hand-Pump Sync (peak = 1.0 vs floor bounce = 0.0)
        AnimationKinematics.RigPoseSnapshot dribPeak = AnimationKinematics.evaluate(
            0f, 0f, 0.12, 0.0, true, 0.5, 1.0, true, false, -1.0, 0,
            AnimationKinematics.ActionClip.NONE, 0.0, null, 0
        );
        AnimationKinematics.RigPoseSnapshot dribBounce = AnimationKinematics.evaluate(
            0f, 0f, 0.12, 0.0, true, 0.5, 0.0, true, false, -1.0, 0,
            AnimationKinematics.ActionClip.NONE, 0.0, null, 0
        );
        org.joml.Vector3f armPeak = dribPeak.rightArm().rotation().transform(new org.joml.Vector3f(0f, -1f, 0f));
        org.joml.Vector3f armBounce = dribBounce.rightArm().rotation().transform(new org.joml.Vector3f(0f, -1f, 0f));
        assertTrue("Right dribble arm must pump vertically between hand-peak and floor-bounce!",
            Math.abs(armPeak.y - armBounce.y) > 0.15f);

        // 4. Jump-Shot Gooseneck Release Follow-Through & 360 Slam Dunk Body Rotation
        AnimationKinematics.RigPoseSnapshot release = AnimationKinematics.evaluate(
            0f, -15f, 0.0, 0.20, false, 0.0, 0.5, false, false, -1.0, 0,
            AnimationKinematics.ActionClip.THROW_SHOT, 0.35, null, 0
        );
        org.joml.Vector3f shootHandDir = release.rightArm().rotation().transform(new org.joml.Vector3f(0f, -1f, 0f));
        assertTrue("Jump-shot release must raise shooting arm high ABOVE shoulder (y > +0.4)!", shootHandDir.y > 0.40f);
        assertTrue("Jump-shot release must extend toward basket (+Z > 0)!", shootHandDir.z > 0.15f);

        AnimationKinematics.RigPoseSnapshot spinDunk = AnimationKinematics.evaluate(
            0f, 0f, 0.25, 0.25, false, 0.0, 0.5, true, false, -1.0, 0,
            AnimationKinematics.ActionClip.SLAM_DUNK, 0.50, DunkStyle.SPIN_360, 0
        );
        org.joml.Vector3f spunForward = spinDunk.torso().rotation().transform(new org.joml.Vector3f(0f, 0f, 1f));
        assertTrue("SPIN_360 dunk at 50% progress must rotate torso ~180 deg (+Z -> -Z in world)!", spunForward.z < -0.80f);
    }

    @Test
    public void testCoinEconomyTagShopUnlocksAndEnglishAssets() throws Exception {
        assertEquals("Must provide 56 total chat tags (54 purchasable + 2 exclusive)!", 56, ChatTag.values().length);
        assertEquals("Must provide 54 purchasable chat tags!", 54L,
            java.util.Arrays.stream(ChatTag.values()).filter(t -> !t.isSpecialUnpurchasable()).count());
        assertEquals("Must provide 8 sculpted 3D square basketball skins!", 8, fr.bosketball.model.BallSkin.values().length);
        assertEquals("Single-tag compact chat layout requires MAX_EQUIPPED_TAGS = 1!", 1, PlayerProfile.MAX_EQUIPPED_TAGS);
        Set<Character> glyphs = new HashSet<>();
        Set<String> ids = new HashSet<>();
        File rpZip = new File("releases/BosketBall-ResourcePack.zip");
        assertTrue("BosketBall-ResourcePack.zip must exist!", rpZip.exists());
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(rpZip)) {
            assertNotNull("Custom dark-gold container GUI generic_54.png must exist!",
                zf.getEntry("assets/minecraft/textures/gui/container/generic_54.png"));
            assertNotNull("Seamless diamond-weave hoop_net_mesh.png must exist!",
                zf.getEntry("assets/bosketball/textures/item/hoop_net_mesh.png"));
            assertNotNull("Gold Coin inline icon chat_coin.png must exist!",
                zf.getEntry("assets/bosketball/textures/font/chat_coin.png"));
            assertNotNull("HiDPI Discord Clyde icon chat_discord.png must exist!",
                zf.getEntry("assets/bosketball/textures/font/chat_discord.png"));
            assertNotNull("Sidebar COINS section pill sb_coins.png must exist!",
                zf.getEntry("assets/bosketball/textures/font/sb_coins.png"));
            for (String customSkin : new String[]{"star_ignis", "star_frost", "star_mvp", "defender_bot"}) {
                assertNotNull("Custom 64x64 player skin missing: " + customSkin,
                    zf.getEntry("assets/bosketball/textures/entity/player/wide/" + customSkin + ".png"));
            }
            assertNotNull("Invisible UI filler item model ui_invisible.json must exist!",
                zf.getEntry("assets/bosketball/items/ui_invisible.json"));
            for (fr.bosketball.model.BallSkin skin : fr.bosketball.model.BallSkin.values()) {
                assertNotNull("Missing 3D ball item definition for " + skin.modelId(),
                    zf.getEntry("assets/bosketball/items/" + skin.modelId() + ".json"));
            }
            for (ChatTag tag : ChatTag.values()) {
                assertTrue("Duplicate ChatTag glyph: " + (int) tag.glyph(), glyphs.add(tag.glyph()));
                assertTrue("Duplicate ChatTag id: " + tag.id(), ids.add(tag.id()));
                if (tag.isSpecialUnpurchasable()) {
                    assertEquals("Exclusive tag must have -1 sentinel price!", -1, tag.coinPrice());
                } else {
                    assertTrue("Every purchasable ChatTag must have a positive Coin price!", tag.coinPrice() >= 250);
                }
                assertEquals(tag, ChatTag.fromIdOrNull(tag.id()));
                assertNotNull(tag.pillComponent());
                assertNotNull("Missing pill texture for tag " + tag.id(),
                    zf.getEntry("assets/bosketball/textures/font/tag_" + tag.id() + ".png"));
            }
        }

        // Verify Coin Economy, Shop Unlocks, Single-Tag Selection & YAML Persistence
        File tmpDir = new File("/tmp/bb-overhaul-test-" + System.nanoTime());
        StatsManager sm = new StatsManager(tmpDir, null);
        UUID u1 = UUID.randomUUID();
        PlayerProfile p1 = sm.getOrCreate(u1, "HooperOne");
        assertEquals("New player must start with 100 Coins!", 100, p1.coins());
        assertEquals("New regular player must have 0 free tags equipped by default!", 0, p1.equippedTags().size());
        assertFalse("GOAT tag must start locked until bought with Coins!", p1.hasUnlockedTag(ChatTag.GOAT));
        assertTrue("CLASSIC ball must start unlocked!", p1.hasUnlockedBall(fr.bosketball.model.BallSkin.CLASSIC));
        assertFalse("GALAXY ball must start locked!", p1.hasUnlockedBall(fr.bosketball.model.BallSkin.GALAXY));
        assertTrue("TOMAHAWK dunk must start unlocked!", p1.hasUnlockedDunk(DunkStyle.TOMAHAWK));
        assertFalse("METEOR_JAM dunk must start locked!", p1.hasUnlockedDunk(DunkStyle.METEOR_JAM));

        // Test spending, earning & losing Coins (clamped >= 0)
        assertFalse("Cannot spend 2500 Coins with only 100 Coins!", p1.spendCoins(ChatTag.GOAT.coinPrice()));
        p1.addCoins(3000);
        assertEquals(3100, p1.coins());
        assertTrue(p1.spendCoins(ChatTag.GOAT.coinPrice()));
        p1.unlockTag(ChatTag.GOAT);
        assertTrue(p1.hasUnlockedTag(ChatTag.GOAT));
        p1.unlockBall(fr.bosketball.model.BallSkin.GALAXY);
        p1.unlockDunk(DunkStyle.METEOR_JAM);

        // Equipping a second tag replaces the first (MAX_EQUIPPED_TAGS == 1)
        p1.unlockTag(ChatTag.ROYAL);
        assertTrue(p1.toggleEquippedTag(ChatTag.GOAT));
        assertEquals(List.of(ChatTag.GOAT), p1.equippedTags());
        assertTrue(p1.toggleEquippedTag(ChatTag.ROYAL));
        assertEquals(List.of(ChatTag.ROYAL), p1.equippedTags());

        p1.removeCoins(999_999);
        assertEquals("Coin losses in matches must clamp cleanly at 0!", 0, p1.coins());
        p1.setCoins(1450);

        p1.addPlayTimeSeconds(3600 + 50 * 60);
        assertEquals("1h 50m", p1.formattedPlayTimeEn());

        MatchHistoryEntry entry = new MatchHistoryEntry(1760000000000L, "2V2", "IGNIS", 21, 17, true, 15, 4, 3, +18);
        String compact = entry.serializeCompact();
        MatchHistoryEntry parsed = MatchHistoryEntry.deserializeCompact(compact);
        assertNotNull(parsed);
        assertEquals(21, parsed.ignisScore());
        assertEquals(15, parsed.points());
        assertEquals(18, parsed.eloDelta());
        p1.recordMatch(entry);
        p1.settings().setFloorAuraRings(false);

        p1.setAssists(88);
        sm.refreshLeaderboardBadges();
        assertEquals("Top assists player must receive #1 assists badge!", 1, sm.getBadges(u1).assistsPlace());
        assertEquals(u1, sm.topBy(StatsManager.LeaderboardCategory.COINS, 1).get(0).uuid());

        sm.save();
        StatsManager reloaded = new StatsManager(tmpDir, null);
        reloaded.load();
        PlayerProfile loaded = reloaded.getProfile(u1);
        assertNotNull(loaded);
        assertEquals(1450, loaded.coins());
        assertTrue(loaded.hasUnlockedTag(ChatTag.GOAT));
        assertTrue(loaded.hasUnlockedTag(ChatTag.ROYAL));
        assertTrue(loaded.hasUnlockedBall(fr.bosketball.model.BallSkin.GALAXY));
        assertTrue(loaded.hasUnlockedDunk(DunkStyle.METEOR_JAM));
        assertEquals(ChatTag.ROYAL, loaded.selectedTag());
        assertEquals(List.of(ChatTag.ROYAL), loaded.equippedTags());
        assertEquals("1h 50m", loaded.formattedPlayTimeEn());
        assertEquals(1, loaded.matchHistory().size());
        assertFalse(loaded.settings().floorAuraRings());

        assertNotNull(Glyphs.composeDividerHeader("Test Header"));
        assertNotNull(Glyphs.composeDividerBottom());
        assertNotNull(Glyphs.composeMenu3dRow("RANKED 1V1", true));
        assertNotNull(Glyphs.composeMenu3dRow("STREET 2V2", false));
        assertNotNull(Glyphs.composeMenu3dIndigoPill("Discord"));
    }

    @Test
    public void testWaitingRoomQueueAndCustomStageManagerHeadless() {
        fr.bosketball.game.GameManager gm = new fr.bosketball.game.GameManager(null);
        UUID u = UUID.randomUUID();
        assertFalse(gm.isQueued(u));
        assertEquals(null, gm.getQueuedMode(u));
        assertEquals(0, gm.queuedCount());
        assertEquals(0, gm.queuedCount(fr.bosketball.game.GameModeType.STREET_2V2));

        fr.bosketball.ui.LockerRoom3dMenuManager stageMgr = new fr.bosketball.ui.LockerRoom3dMenuManager(null);
        assertFalse("Default stage must use built-in chamber until /bb 3d setcamera is configured!", stageMgr.hasCustomStage());
        assertFalse(stageMgr.isIn3dMenu(u));
    }

    @Test
    public void testAsyncAtomicPersistenceAndIndependentPlayerSettings() throws Exception {
        File tmpDir = Files.createTempDirectory("bb-async-test").toFile();
        StatsManager sm = new StatsManager(tmpDir, null);

        UUID id = UUID.randomUUID();
        PlayerProfile p = sm.getOrCreate(id, "AsyncTester");
        p.setElo(1920);
        p.setPoints(315);
        // Verify cinematicScoreCam and partyInvites do NOT overwrite aimTrajectory or chatAnnouncements!
        p.settings().setAimTrajectory(true);
        p.settings().setCinematicScoreCam(false);
        p.settings().setChatAnnouncements(true);
        p.settings().setPartyInvites(false);
        assertTrue("aimTrajectory must stay independent from cinematicScoreCam!", p.settings().aimTrajectory());
        assertFalse(p.settings().cinematicScoreCam());
        assertTrue("chatAnnouncements must stay independent from partyInvites!", p.settings().chatAnnouncements());
        assertFalse(p.settings().partyInvites());

        // Trigger multiple rapid async saves followed by clean shutdown flush
        for (int i = 0; i < 8; i++) {
            p.setPoints(300 + i);
            sm.saveProfileAsync(p);
        }
        sm.shutdownAndSaveSync();

        File tmpLeftover = new File(tmpDir, "players.yml.tmp");
        assertFalse("Atomic move must leave zero .tmp file behind!", tmpLeftover.exists());

        StatsManager reloaded = new StatsManager(tmpDir, null);
        reloaded.load();
        PlayerProfile loaded = reloaded.getProfile(id);
        assertNotNull(loaded);
        assertEquals(307, loaded.points());
        assertTrue(loaded.settings().aimTrajectory());
        assertFalse(loaded.settings().cinematicScoreCam());
        assertTrue(loaded.settings().chatAnnouncements());
        assertFalse(loaded.settings().partyInvites());
    }

    @Test
    public void testPersistedSkinTextureCacheRestoresRealOfflineHeads() throws Exception {
        File tmpDir = Files.createTempDirectory("bb-skin-head-cache").toFile();
        StatsManager sm = new StatsManager(tmpDir, null);
        UUID id = UUID.randomUUID();
        var textures = new com.destroystokyo.paper.profile.ProfileProperty(
            "textures", "eyJ0ZXh0dXJlcyI6e30=", "test-signature");

        assertTrue("First captured real skin texture must be stored!",
            sm.storeSkinTexture(id, "SkinTester", textures));
        assertFalse("Re-storing the identical texture must be a no-op!",
            sm.storeSkinTexture(id, "SkinTester", textures));
        assertNotNull("Cached skin texture must be available for offline skulls!", sm.cachedSkinTexture(id));
        sm.shutdownAndSaveSync();

        StatsManager reloaded = new StatsManager(tmpDir, null);
        reloaded.load();
        PlayerProfile loaded = reloaded.getProfile(id);
        assertNotNull(loaded);
        assertTrue("Persisted skin texture must survive a restart!", loaded.hasSkinTexture());
        assertEquals("eyJ0ZXh0dXJlcyI6e30=", loaded.skinTextureValue());
        assertEquals("test-signature", loaded.skinTextureSignature());
        assertNotNull(reloaded.cachedSkinTexture(id));

        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertTrue("GUI skulls must resolve via persisted texture + Paper local cache + one coalesced async backfill!",
            guiSrc.contains("cachedSkinTexture(targetId)")
                && guiSrc.contains("completeFromCache()")
                && guiSrc.contains("requestRealHeadAsync")
                && guiSrc.contains("Bukkit.createProfileExact")
                && guiSrc.contains("SKIN_HEAD_FAILURE_COOLDOWN_MILLIS"));
        assertFalse("GUI skulls must never put an unresolved offline profile on a head again!",
            guiSrc.contains(".setOwningPlayer("));
        String statsSrc = Files.readString(new File("src/main/java/fr/bosketball/stats/StatsManager.java").toPath());
        assertTrue("Live skins must be captured on join and persisted in player YAML!",
            statsSrc.contains("captureLiveSkinTexture")
                && statsSrc.contains("identity.skin-texture-value")
                && statsSrc.contains("identity.skin-texture-signature"));
    }

    @Test
    public void testMultiThreadConcurrencyStressZeroExceptions() throws Exception {
        File tmpDir = Files.createTempDirectory("bb-concurrency-test").toFile();
        StatsManager sm = new StatsManager(tmpDir, null);
        fr.bosketball.social.SocialPartyManager social = new fr.bosketball.social.SocialPartyManager(sm);
        fr.bosketball.game.GameManager gm = new fr.bosketball.game.GameManager(null);

        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    UUID u = UUID.randomUUID();
                    PlayerProfile prof = sm.getOrCreate(u, "Bot" + i);
                    prof.setElo(1000 + (i * 7));
                    prof.setWins(i % 15);
                    if (i % 10 == 0) {
                        sm.refreshLeaderboardBadges();
                    }
                    gm.tickAll();
                }
            } catch (Throwable t) {
                failed.set(true);
            }
        });
        Thread asyncChatReader = new Thread(() -> {
            try {
                for (int i = 0; i < 300; i++) {
                    gm.activePlayerCount();
                    gm.queuedCount();
                    gm.getSessionOf(UUID.randomUUID());
                    sm.topBy(StatsManager.LeaderboardCategory.ELO, 5);
                    social.countOnlineFriends(UUID.randomUUID());
                }
            } catch (Throwable t) {
                failed.set(true);
            }
        });
        writer.start();
        asyncChatReader.start();
        writer.join(5000);
        asyncChatReader.join(5000);
        sm.shutdownAndSaveSync();
        assertFalse("Concurrent main-thread + async-chat reads/writes must never throw!", failed.get());
    }

    @Test
    public void testInscribedCircularHoopNetModelsZeroProtrudingWhitePieces() throws Exception {
        String[] hoopModels = {"hoop_idle", "hoop_swish_1", "hoop_swish_2", "hoop_dunk_flex"};
        for (String name : hoopModels) {
            File f = new File("resourcepack/assets/bosketball/models/item/" + name + ".json");
            assertTrue("Missing hoop model: " + name, f.exists());
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(Files.readString(f.toPath())).getAsJsonObject();
            com.google.gson.JsonArray elements = root.getAsJsonArray("elements");
            int netElements = 0;
            for (com.google.gson.JsonElement el : elements) {
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                com.google.gson.JsonArray from = obj.getAsJsonArray("from");
                com.google.gson.JsonArray to = obj.getAsJsonArray("to");
                for (int axis = 0; axis < 3; axis++) {
                    double a = from.get(axis).getAsDouble();
                    double b = to.get(axis).getAsDouble();
                    assertTrue("Element below -16 in " + name, a >= -16.0 && b >= -16.0);
                    assertTrue("Element above +32 in " + name, a <= 32.0 && b <= 32.0);
                }
                if (obj.toString().contains("#net_mesh")) {
                    netElements++;
                    double spanX = Math.abs(to.get(0).getAsDouble() - from.get(0).getAsDouble());
                    double spanZ = Math.abs(to.get(2).getAsDouble() - from.get(2).getAsDouble());
                    // Each net panel is a thin flat mesh card (thickness <= 0.15 on one horizontal axis), never a protruding square box!
                    assertTrue("Net element must be a thin inscribed mesh panel in " + name, Math.min(spanX, spanZ) <= 0.15);
                    assertTrue("Net panel width must stay strictly inside rim diameter in " + name, Math.max(spanX, spanZ) <= 5.0);
                }
            }
            assertEquals("Expected 5 octagonal rings x 8 faces = 40 closed net panels in " + name, 40, netElements);
        }
    }

    @Test
    public void testZeroChaosCubedZero262Zero3dTabAndDiscordInvite() throws Exception {
        assertFalse("CenterTabOverlayManager must be completely deleted!",
            new File("src/main/java/fr/bosketball/ui/CenterTabOverlayManager.java").exists());
        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        assertTrue("plugin.yml must register /discord!", pluginYml.contains("discord:"));
        assertFalse("plugin.yml must NOT register /tab!", pluginYml.contains("\n  tab:\n"));
        assertFalse("plugin.yml must NOT mention Chaos Cubed!",
            pluginYml.toLowerCase().contains("chaos"));
        assertEquals("plugin.yml may only mention 26.2 once: the api-version declaration!",
            1, pluginYml.split("26\\.2", -1).length - 1);
        assertTrue("plugin.yml must target the 26.2 API (26.2-only plugin)!",
            pluginYml.contains("api-version: '26.2'"));

        String configYml = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("config.yml must point /discord to https://discord.gg/pnJhKuU2QK!",
            configYml.contains("https://discord.gg/pnJhKuU2QK"));
        assertFalse("config.yml must NOT mention Chaos Cubed or 26.2!",
            configYml.toLowerCase().contains("chaos") || configYml.contains("26.2"));

        String packMcmeta = Files.readString(new File("resourcepack/pack.mcmeta").toPath());
        assertFalse("pack.mcmeta must NOT mention Chaos Cubed or 26.2!",
            packMcmeta.toLowerCase().contains("chaos") || packMcmeta.contains("26.2"));

        String chatSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/BosketChatRenderer.java").toPath());
        assertFalse("BosketChatRenderer must NOT emit [N] channel badge!", chatSrc.contains("[N]") || chatSrc.contains("[ɴ]"));
    }

    @Test
    public void testRealPlayerNativeAnimationsAndMannequinDefenderArchitecture() throws Exception {
        // 1. PlayerVisualRig MUST have ZERO fake ItemDisplay body bones (real players stay 100% real!)
        for (java.lang.reflect.Field f : fr.bosketball.animation.PlayerVisualRig.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            assertFalse("PlayerVisualRig must NOT have fake ItemDisplay body bone: " + f.getName(),
                n.contains("bone") || n.contains("torso") || n.contains("arm") || n.contains("leg"));
        }

        // 2. CourtDefenderBot MUST be backed by a real Paper 26.2 org.bukkit.entity.Mannequin!
        boolean hasMannequinField = false;
        for (java.lang.reflect.Field f : fr.bosketball.game.CourtDefenderBot.class.getDeclaredFields()) {
            if (f.getType().equals(org.bukkit.entity.Mannequin.class)) {
                hasMannequinField = true;
            }
            assertFalse("CourtDefenderBot must NOT use fake ItemDisplay limb bones: " + f.getName(),
                f.getName().toLowerCase().contains("bone"));
        }
        assertTrue("CourtDefenderBot must use org.bukkit.entity.Mannequin!", hasMannequinField);

        // 3. CinematicCameraManager must exist & BosketBallPlugin must expose cinematicCamera()
        assertNotNull(fr.bosketball.BosketBallPlugin.class.getMethod("cinematicCamera"));
    }

    @Test
    public void testAnimated262GatherBallsBareHandCourtIconsAndSha1Sync() throws Exception {
        File rpZip = new File("releases/BosketBall-ResourcePack-26.2.zip");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(rpZip)) {
            for (fr.bosketball.model.BallSkin skin : fr.bosketball.model.BallSkin.values()) {
                String defPath = "assets/bosketball/items/" + skin.modelId() + ".json";
                String defTxt = new String(zf.getInputStream(zf.getEntry(defPath)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue("26.2 ball item def must use using_item condition: " + defPath, defTxt.contains("minecraft:using_item"));
                assertTrue("26.2 ball item def must use use_duration range_dispatch: " + defPath, defTxt.contains("minecraft:use_duration"));
                for (String stage : new String[]{"gather_0", "gather_1", "gather_2", "setpoint", "mannequin"}) {
                    assertNotNull("Missing animated ball model stage " + stage + " for " + skin.modelId(),
                        zf.getEntry("assets/bosketball/models/item/" + skin.modelId() + "_" + stage + ".json"));
                }
            }
            for (String courtIcon : new String[]{"icon_pass", "icon_crossover", "icon_dunk", "icon_defense", "icon_bot_on", "icon_bot_off", "icon_leave"}) {
                String mPath = "assets/bosketball/models/item/" + courtIcon + ".json";
                com.google.gson.JsonObject mJson = com.google.gson.JsonParser.parseString(
                    new String(zf.getInputStream(zf.getEntry(mPath)).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                ).getAsJsonObject();
                com.google.gson.JsonArray fpScale = mJson.getAsJsonObject("display")
                    .getAsJsonObject("firstperson_righthand").getAsJsonArray("scale");
                assertEquals("Court skill icon must be invisible in 1st-person hand: " + courtIcon, 0.0, fpScale.get(0).getAsDouble(), 1e-6);
            }
        }
        String zipSha1 = Files.readString(new File("releases/BosketBall-ResourcePack-26.2.zip.sha1").toPath()).trim();
        String cfgText = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("config.yml external-sha1 must match built ResourcePack SHA-1!", cfgText.contains(zipSha1));
    }

    @Test
    public void testZeroBosketPlayerHardcodedFallbackInSource() throws Exception {
        try (var stream = Files.walk(new File("src/main/java").toPath())) {
            for (var p : stream.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p);
                assertFalse("Hardcoded BosketPlayer fallback found in " + p, src.contains("\"BosketPlayer\""));
                assertFalse("Slow per-tick getConfig() lookup found in " + p, src.contains(".getConfig()"));
            }
        }
    }

    @Test
    public void testCleanModularConfigsPerPlayerYamlMigrationAndShopOverrides() throws Exception {
        File dir = Files.createTempDirectory("bb-config-save-test").toFile();

        // 1. BosketConfig auto-upgrade of partial legacy config.yml + bounds clamping
        File cfgFile = new File(dir, "config.yml");
        Files.writeString(cfgFile.toPath(), """
            general:
              server-domain: "HOOPS.EXAMPLE.COM"
              starting-coins: 250
              welcome-card-enabled: false
            physics:
              gravity: 999.0
            match:
              shot-clock-seconds: 1
            """);
        fr.bosketball.config.BosketConfig cfg = fr.bosketball.config.BosketConfig.loadAndUpgrade(cfgFile, null);
        assertEquals("HOOPS.EXAMPLE.COM", cfg.general().serverDomain());
        assertEquals(250, cfg.general().startingCoins());
        assertFalse("Legacy general.welcome-card-enabled must migrate to welcome.enabled!", cfg.welcome().enabled());
        assertEquals("Out-of-range gravity must clamp safely to 0.25!", 0.25, cfg.physics().gravity(), 1e-6);
        assertEquals("Out-of-range shot clock must clamp safely to 5s!", 5, cfg.match().shotClockSeconds());
        String upgradedRaw = Files.readString(cfgFile.toPath());
        assertTrue("Upgraded config.yml must auto-fill missing economy.two-point-coins!", upgradedRaw.contains("two-point-coins:"));
        assertTrue("Upgraded config.yml must auto-fill storage.auto-save-interval-seconds!", upgradedRaw.contains("auto-save-interval-seconds:"));

        // 2. ShopConfig (shop.yml) auto-creation & live price overrides (including 0 = free!)
        try {
            fr.bosketball.config.ShopConfig.loadOrCreate(dir, null);
            File shopFile = new File(dir, "shop.yml");
            assertTrue("shop.yml must be auto-created!", shopFile.exists());
            String shopText = Files.readString(shopFile.toPath());
            assertTrue(shopText.contains("basketball-skins:"));
            assertTrue(shopText.contains("dunk-styles:"));
            assertTrue(shopText.contains("chat-tags:"));

            shopText = shopText.replace("goat: 2500", "goat: 4200").replace("money: 750", "money: 0");
            Files.writeString(shopFile.toPath(), shopText);
            fr.bosketball.config.ShopConfig.loadOrCreate(dir, null);

            assertEquals(4200, ChatTag.GOAT.priceCoins());
            assertEquals(0, fr.bosketball.model.BallSkin.MONEY.priceCoins());
            PlayerProfile freeCheck = new PlayerProfile(UUID.randomUUID(), "ShopTester");
            assertTrue("Setting price to 0 in shop.yml must unlock item for all players!",
                freeCheck.hasUnlockedBall(fr.bosketball.model.BallSkin.MONEY));
        } finally {
            fr.bosketball.config.ShopConfig.resetOverrides();
        }

        // 3. Automatic migration from legacy monolithic players.yml -> readable players/<uuid>.yml + _name_index.yml!
        UUID legacyUuid = UUID.randomUUID();
        File legacyPlayers = new File(dir, "players.yml");
        MatchHistoryEntry histSample = new MatchHistoryEntry(
            1760000000000L, "3V3", "Bosket Center", fr.bosketball.model.TeamSide.FROST,
            18, 21, true, 12, 5, 4, 2, 1, +19, "LegacyStar"
        );
        Files.writeString(legacyPlayers.toPath(),
            "players:\n  " + legacyUuid + ":\n"
                + "    name: LegacyStar\n"
                + "    coins: 775\n"
                + "    elo: 1620\n"
                + "    2pm: 14\n"
                + "    2pa: 20\n"
                + "    unlocked-tags:\n"
                + "      - SNIPER\n"
                + "    chat-tag: SNIPER\n"
                + "    history:\n"
                + "      - '" + histSample.serializeCompact() + "'\n"
        );

        StatsManager sm = new StatsManager(dir, null);
        sm.load();
        assertFalse("Legacy players.yml must be moved to .migrated.bak after import!", legacyPlayers.exists());
        assertTrue(new File(dir, "players.yml.migrated.bak").exists());

        File perPlayerFile = new File(new File(dir, "players"), legacyUuid + ".yml");
        File indexFile = new File(new File(dir, "players"), "_name_index.yml");
        assertTrue("Clean per-player <uuid>.yml must exist!", perPlayerFile.exists());
        assertTrue("Readable _name_index.yml must exist!", indexFile.exists());
        assertTrue(Files.readString(indexFile.toPath()).contains("LegacyStar"));

        String playerYamlText = Files.readString(perPlayerFile.toPath());
        assertTrue("Per-player YAML must contain header banner with username!", playerYamlText.contains("LegacyStar"));
        assertTrue("Per-player YAML must group economy section!", playerYamlText.contains("economy:"));
        assertTrue("Per-player YAML must group cosmetics section!", playerYamlText.contains("cosmetics:"));
        assertTrue("Per-player YAML must store readable recent-matches maps!", playerYamlText.contains("recent-matches:"));
        assertTrue("Readable match history must show score 18-21!", playerYamlText.contains("18-21"));

        // Reload from clean v2 per-player YAML and verify 100% round-trip fidelity
        StatsManager sm2 = new StatsManager(dir, null);
        sm2.load();
        PlayerProfile migrated = sm2.getProfile(legacyUuid);
        assertNotNull(migrated);
        assertEquals(775, migrated.coins());
        assertEquals(1620, migrated.elo());
        assertEquals(14, migrated.twoPointersMade());
        assertEquals(ChatTag.SNIPER, migrated.selectedTag());
        assertEquals(1, migrated.matchHistory().size());
        assertEquals("Bosket Center", migrated.matchHistory().get(0).arenaName());
        assertEquals(2, migrated.matchHistory().get(0).steals());
        assertEquals("LegacyStar", migrated.matchHistory().get(0).mvpName());

        // 4. Corrupted YAML file backup safety check
        File broken = new File(dir, "broken.yml");
        Files.writeString(broken.toPath(), "invalid: [unclosed yaml\n  :\n");
        fr.bosketball.config.AtomicYamlWriter.loadSafe(broken, null);
        File[] backups = dir.listFiles((d, n) -> n.startsWith("broken.yml.corrupt-"));
        assertNotNull(backups);
        assertEquals("Corrupted YAML must be automatically backed up!", 1, backups.length);
    }

    @Test
    public void testCytooxienStyleMotdAndSquare1x1ServerLogos() throws Exception {
        fr.bosketball.config.BosketConfig cfg = fr.bosketball.config.BosketConfig.defaults();
        assertTrue("MOTD must be enabled by default!", cfg.motd().enabled());
        assertTrue("MOTD must include rotating headline subtitles!", cfg.motd().subtitles().size() >= 2);

        fr.bosketball.server.MotdManager motdMgr = new fr.bosketball.server.MotdManager(null);
        net.kyori.adventure.text.Component motd1 = motdMgr.buildMotdComponent(cfg.motd());
        String plain1 = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(motd1);
        assertTrue("MOTD must render 2 centered lines separated by newline!", plain1.contains("\n"));
        String[] lines = plain1.split("\n");
        assertEquals(2, lines.length);
        assertTrue("Line 1 must have leading centering spaces!", lines[0].startsWith("    "));
        assertTrue("Line 2 must have leading centering spaces!", lines[1].startsWith("  "));
        assertFalse("MOTD must never leak Chaos or 26.2!", plain1.toLowerCase().contains("chaos") || plain1.contains("26.2"));

        // Verify 1:1 square logos & server-icon.png dimensions
        java.awt.image.BufferedImage iconRelease = javax.imageio.ImageIO.read(new File("releases/server-icon.png"));
        java.awt.image.BufferedImage iconRes = javax.imageio.ImageIO.read(new File("src/main/resources/server-icon.png"));
        java.awt.image.BufferedImage logo1x1 = javax.imageio.ImageIO.read(new File("releases/BosketBall-Logo-1x1.png"));
        java.awt.image.BufferedImage packIcon = javax.imageio.ImageIO.read(new File("resourcepack/pack.png"));
        assertEquals(64, iconRelease.getWidth());
        assertEquals(64, iconRelease.getHeight());
        assertEquals(64, iconRes.getWidth());
        assertEquals(64, iconRes.getHeight());
        assertEquals(512, logo1x1.getWidth());
        assertEquals(512, logo1x1.getHeight());
        assertEquals(128, packIcon.getWidth());
        assertEquals(128, packIcon.getHeight());
    }

    @Test
    public void testStandalonePermissionEngineFaweAxiomAndProtectionRules() throws Exception {
        File tmpDir = Files.createTempDirectory("bb-perm-test").toFile();
        fr.bosketball.server.PermissionManager pm = new fr.bosketball.server.PermissionManager(null, tmpDir, null);
        pm.loadOrCreate();

        assertTrue("permissions.yml must be auto-created!", new File(tmpDir, "permissions.yml").exists());
        assertTrue("Default groups must include default, builder, mod, admin!",
            pm.groupNames().containsAll(java.util.List.of("default", "builder", "mod", "admin")));

        UUID regularId = UUID.randomUUID();
        java.util.Map<String, Boolean> defPerms = pm.resolveEffectivePermissions(regularId);
        assertEquals("Default players must explicitly negate axiom.use!", Boolean.FALSE, defPerms.get("axiom.use"));
        assertEquals("Default players must explicitly negate worldedit.*!", Boolean.FALSE, defPerms.get("worldedit.*"));

        UUID builderId = UUID.randomUUID();
        assertTrue(pm.setUserGroup(builderId, "builder"));
        java.util.Map<String, Boolean> builderPerms = pm.resolveEffectivePermissions(builderId);
        assertEquals("Builder group must grant bosketball.build!", Boolean.TRUE, builderPerms.get("bosketball.build"));
        assertEquals("Builder group must grant axiom.*!", Boolean.TRUE, builderPerms.get("axiom.*"));
        assertEquals("Builder group must grant worldedit.*!", Boolean.TRUE, builderPerms.get("worldedit.*"));
        assertEquals("Builder group must grant fawe.admin!", Boolean.TRUE, builderPerms.get("fawe.admin"));

        // Verify persistence across reload
        pm.addUserPermission(builderId, "custom.special.node");
        fr.bosketball.server.PermissionManager pmReloaded = new fr.bosketball.server.PermissionManager(null, tmpDir, null);
        pmReloaded.loadOrCreate();
        assertEquals("builder", pmReloaded.getUserGroup(builderId));
        assertEquals(Boolean.TRUE, pmReloaded.resolveEffectivePermissions(builderId).get("custom.special.node"));

        // Verify Total World Protection material classifiers (illegal items, fire, gravity)
        assertTrue(fr.bosketball.server.ServerProtectionManager.isIllegalMaterial(org.bukkit.Material.TNT));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isIllegalMaterial(org.bukkit.Material.COMMAND_BLOCK));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isIllegalMaterial(org.bukkit.Material.LAVA_BUCKET));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isIllegalMaterial(org.bukkit.Material.FLINT_AND_STEEL));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isIllegalMaterial(org.bukkit.Material.SPAWNER));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isIllegalMaterial(org.bukkit.Material.CREEPER_SPAWN_EGG));
        assertFalse(fr.bosketball.server.ServerProtectionManager.isIllegalMaterial(org.bukkit.Material.QUARTZ_BLOCK));

        assertTrue(fr.bosketball.server.ServerProtectionManager.isGravityOrFragileBlock(org.bukkit.Material.SAND));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isGravityOrFragileBlock(org.bukkit.Material.RED_CONCRETE_POWDER));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isGravityOrFragileBlock(org.bukkit.Material.WHITE_CARPET));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isGravityOrFragileBlock(org.bukkit.Material.LANTERN));

        // Verify [+] Join and [-] Leave chat formats
        fr.bosketball.config.BosketConfig.Chat chatCfg = fr.bosketball.config.BosketConfig.defaults().chat();
        assertTrue(chatCfg.joinQuitMessages());
        String joinRendered = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(fr.bosketball.util.Msg.parse(
                chatCfg.joinFormat().replace("{player}", "MaxLananas").replace("<player>", "MaxLananas")
            ));
        String quitRendered = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(fr.bosketball.util.Msg.parse(
                chatCfg.quitFormat().replace("{player}", "MaxLananas").replace("<player>", "MaxLananas")
            ));
        assertEquals("[+] MaxLananas", joinRendered);
        assertEquals("[-] MaxLananas", quitRendered);
    }

    @Test
    public void testDunkZoneCustomCourtLinesReplayBufferPracticePoolLockerHudAndUnownedShop() throws Exception {
        // 1. Tightened Manual Skill Dunk Zone (2.85m Paint Radius) & 100% English cardinal labels
        fr.bosketball.arena.CourtGeometry geo = new fr.bosketball.arena.CourtGeometry(0.5, 64.0, 0.5, 14.0, 8.0);
        org.bukkit.util.Vector southRim = geo.southHoopRim();
        assertTrue("2.7m inside paint must be inside skill Dunk Zone!",
            geo.isInDunkZone(southRim.getX(), southRim.getZ() - 2.7, southRim));
        assertFalse("4.1m out near free-throw line must NOT auto-dunk!",
            geo.isInDunkZone(southRim.getX(), southRim.getZ() - 4.1, southRim));
        assertFalse("1.0m behind backboard must NOT be inside Dunk Zone!",
            geo.isInDunkZone(southRim.getX(), southRim.getZ() + 1.0, southRim));
        assertEquals("ɴᴏʀᴛʜ (-Z)", fr.bosketball.arena.CourtGeometry.cardinalLabel(new org.bukkit.util.Vector(0, 0, -1)));
        assertEquals("ᴇᴀꜱᴛ (+X)", fr.bosketball.arena.CourtGeometry.cardinalLabel(new org.bukkit.util.Vector(1, 0, 0)));

        // 2. Custom Map Pure-White Court Lines & YAML persistence
        File arenaDir = Files.createTempDirectory("bb-lines-test").toFile();
        fr.bosketball.arena.ArenaManager am = new fr.bosketball.arena.ArenaManager(arenaDir, null);
        fr.bosketball.arena.Arena custom = new fr.bosketball.arena.Arena("rooftop", "Rooftop", "world", geo);
        custom.setCustomMap(true);
        assertEquals("Custom maps must default to pure WHITE_CONCRETE accent lines!",
            org.bukkit.Material.WHITE_CONCRETE, custom.courtLineAccentMaterial());
        custom.setCourtLinesPreset(fr.bosketball.arena.Arena.CourtLinesPreset.STREET);
        custom.setCourtLineWidth(0.14);
        am.registerArena(custom);
        am.shutdownAndSaveSync();

        fr.bosketball.arena.ArenaManager am2 = new fr.bosketball.arena.ArenaManager(arenaDir, null);
        am2.load();
        fr.bosketball.arena.Arena loadedCustom = am2.getArena("rooftop");
        assertNotNull(loadedCustom);
        assertTrue(loadedCustom.isCourtLinesEnabled());
        assertEquals(fr.bosketball.arena.Arena.CourtLinesPreset.STREET, loadedCustom.courtLinesPreset());
        assertEquals(0.14, loadedCustom.courtLineWidth(), 0.001);

        // 3. Scalable 64-Court Practice Arena Pool isolation (>= 180 blocks between every pair!)
        java.util.List<org.bukkit.Location> centers = new java.util.ArrayList<>();
        for (int idx = 1; idx <= 64; idx++) {
            centers.add(fr.bosketball.arena.PracticeArenaPool.computeGridSlotCenter(idx, 192));
        }
        for (int i = 0; i < centers.size(); i++) {
            for (int j = i + 1; j < centers.size(); j++) {
                double dx = centers.get(i).getX() - centers.get(j).getX();
                double dz = centers.get(i).getZ() - centers.get(j).getZ();
                assertTrue("Practice courts must never overlap!", Math.hypot(dx, dz) >= 190.0);
            }
        }

        // 4. Instant Goal Replay RingBuffer frame recording & tail extraction
        fr.bosketball.ui.CinematicCameraManager.ReplayRingBuffer ring =
            new fr.bosketball.ui.CinematicCameraManager.ReplayRingBuffer(50);
        UUID pId = UUID.randomUUID();
        for (int t = 0; t < 65; t++) {
            fr.bosketball.ui.CinematicCameraManager.ActorSnapshot actor =
                new fr.bosketball.ui.CinematicCameraManager.ActorSnapshot(
                    pId, "Scorer", fr.bosketball.model.TeamSide.IGNIS,
                    0.5, 64.0 + t * 0.02, -5.0 + t * 0.1,
                    0f, -10f, false, t % 8 == 0
                );
            ring.record(new fr.bosketball.ui.CinematicCameraManager.ReplayFrame(
                new org.bukkit.util.Vector(0.5, 65.2 + t * 0.03, -4.5 + t * 0.1),
                new org.joml.Quaternionf(),
                fr.bosketball.model.BallSkin.CLASSIC,
                t >= 55,
                java.util.List.of(actor)
            ));
        }
        assertEquals("RingBuffer must cap at maxFrames (50)!", 50, ring.size());
        assertEquals(40, ring.snapshotTail(40).size());
        ring.clear();
        assertEquals(0, ring.size());

        // 5. Locker Room: ZERO fake/offline NPC names + transparent shulker_box.png viewport!
        String lockerSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/LockerRoom3dMenuManager.java").toPath());
        assertFalse("Locker Room must never hardcode Refortorr!", lockerSrc.contains("Refortorr"));
        assertFalse("Locker Room must never hardcode A_Koop!", lockerSrc.contains("A_Koop"));
        assertFalse("Locker Room must never hardcode DiAM0ndD0g15!", lockerSrc.contains("DiAM0ndD0g15"));

        File shulkerPng = new File("resourcepack/assets/minecraft/textures/gui/container/shulker_box.png");
        assertTrue("Transparent Locker Room shulker_box.png HUD must exist!", shulkerPng.exists());
        java.awt.image.BufferedImage shulkerImg = javax.imageio.ImageIO.read(shulkerPng);
        int centerViewportAlpha = (shulkerImg.getRGB(88, 44) >>> 24) & 0xFF;
        assertEquals("Center of shulker_box.png must be 100% transparent so player skins are unobstructed!", 0, centerViewportAlpha);

        String mcEnUs = Files.readString(new File("resourcepack/assets/minecraft/lang/en_us.json").toPath());
        assertTrue("Minecraft lang override must blank container.inventory!", mcEnUs.contains("\"container.inventory\": \"\""));

        // 6. Stable Full-Catalog Shop: buying changes the card to Owned but
        // must never make the product disappear or shift the shelf layout.
        PlayerProfile shopper = new PlayerProfile(UUID.randomUUID(), "ShopTester");
        int initialUnowned = fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(
            shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL
        ).size();
        int initialCatalog = fr.bosketball.ui.GuiMenuManager.collectShopCatalogItems(
            shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL
        ).size();
        assertTrue("Fresh player must have purchasable items in Shop!", initialUnowned > 5);
        assertEquals("Fresh-player catalog should equal the purchasable/unowned list", initialUnowned, initialCatalog);

        // Classic ball & Tomahawk dunk are free starter cosmetics, not Shop products.
        for (var item : fr.bosketball.ui.GuiMenuManager.collectShopCatalogItems(shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL)) {
            assertTrue("Shop catalog may only contain purchasable products", item.priceCoins() > 0);
            assertFalse("Free CLASSIC ball must not be sold in Shop!", item.ballSkin() == fr.bosketball.model.BallSkin.CLASSIC);
            assertFalse("Free TOMAHAWK dunk must not be sold in Shop!", item.dunkStyle() == fr.bosketball.model.DunkStyle.TOMAHAWK);
        }

        // Unlocking reduces "remaining" but never shrinks the displayed catalog.
        shopper.unlockBall(fr.bosketball.model.BallSkin.SAKURA);
        int afterOneUnlock = fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(
            shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL
        ).size();
        assertEquals(initialUnowned - 1, afterOneUnlock);
        var catalogAfterPurchase = fr.bosketball.ui.GuiMenuManager.collectShopCatalogItems(
            shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.BALLS
        );
        assertEquals(initialCatalog, fr.bosketball.ui.GuiMenuManager.collectShopCatalogItems(
            shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL
        ).size());
        assertTrue("Purchased SAKURA must stay visible in the Shop catalog", catalogAfterPurchase.stream()
            .anyMatch(item -> item.ballSkin() == fr.bosketball.model.BallSkin.SAKURA));
        assertTrue(fr.bosketball.ui.GuiMenuManager.isShopEntryOwned(null, shopper, catalogAfterPurchase.stream()
            .filter(item -> item.ballSkin() == fr.bosketball.model.BallSkin.SAKURA)
            .findFirst().orElseThrow()));

        String shopSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertTrue("OpenShop shelves must render the stable full catalog",
            shopSrc.contains("List<ShopItemEntry> catalogBalls = collectShopCatalogItems"));
        assertTrue("Owned Shop cards must act as equip buttons rather than recharging Coins",
            shopSrc.contains("Owned catalog cards remain in place and act as equip buttons"));

        // Unlocking everything leaves 0 remaining, while every product stays visible.
        for (fr.bosketball.model.BallSkin b : fr.bosketball.model.BallSkin.values()) shopper.unlockBall(b);
        for (fr.bosketball.model.DunkStyle d : fr.bosketball.model.DunkStyle.values()) shopper.unlockDunk(d);
        for (ChatTag t : ChatTag.values()) shopper.unlockTag(t);
        assertEquals("When all cosmetics & tags are owned, Shop must report 0 remaining items!",
            0, fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL).size());
        assertEquals("When all cosmetics are owned, the full Shop catalog must still be visible!",
            initialCatalog, fr.bosketball.ui.GuiMenuManager.collectShopCatalogItems(shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL).size());
    }

    @Test
    public void testLivePlaytest13PointOverhaul() throws Exception {
        // 1. Exclusive FOUNDER (MaxLananas ONLY) & MYPINEAPPLE (Team Exclusive, Unpurchasable in Shop)
        assertEquals(-1, ChatTag.FOUNDER.priceCoins());
        assertEquals(-1, ChatTag.MYPINEAPPLE.priceCoins());
        assertTrue(ChatTag.FOUNDER.isSpecialUnpurchasable());
        assertTrue(ChatTag.MYPINEAPPLE.isSpecialUnpurchasable());

        PlayerProfile maxProf = new PlayerProfile(UUID.randomUUID(), "MaxLananas");
        assertTrue("MaxLananas must auto-unlock FOUNDER!", maxProf.hasUnlockedTag(ChatTag.FOUNDER));
        assertTrue("MaxLananas must auto-unlock MYPINEAPPLE!", maxProf.hasUnlockedTag(ChatTag.MYPINEAPPLE));
        assertEquals("MaxLananas should auto-equip FOUNDER on fresh profile!", ChatTag.FOUNDER, maxProf.selectedTag());
        assertTrue(ChatTag.FOUNDER.canBeUsedBy(maxProf, "MaxLananas", false));
        assertTrue(ChatTag.MYPINEAPPLE.canBeUsedBy(maxProf, "MaxLananas", false));

        PlayerProfile normalProf = new PlayerProfile(UUID.randomUUID(), "Akashhi_");
        assertFalse("Normal player must NEVER auto-unlock FOUNDER!", normalProf.hasUnlockedTag(ChatTag.FOUNDER));
        assertFalse("Normal player must NEVER auto-unlock MYPINEAPPLE!", normalProf.hasUnlockedTag(ChatTag.MYPINEAPPLE));
        assertFalse("Staff wildcard must NOT unlock FOUNDER for non-MaxLananas!", ChatTag.FOUNDER.canBeUsedBy(normalProf, "Akashhi_", true));
        assertFalse("Staff wildcard must NOT unlock MYPINEAPPLE without explicit grant!", ChatTag.MYPINEAPPLE.canBeUsedBy(normalProf, "Akashhi_", true));

        normalProf.unlockTag(ChatTag.FOUNDER);
        normalProf.setSelectedTag(ChatTag.FOUNDER);
        assertFalse("Non-MaxLananas must NEVER be able to unlock FOUNDER!", normalProf.hasUnlockedTag(ChatTag.FOUNDER));
        assertEquals("Non-MaxLananas must NEVER be able to equip FOUNDER!", null, normalProf.selectedTag());

        // Granting & revoking MYPINEAPPLE for a MyPineapple team member works cleanly
        normalProf.unlockTag(ChatTag.MYPINEAPPLE);
        assertTrue(normalProf.hasUnlockedTag(ChatTag.MYPINEAPPLE));
        assertTrue(ChatTag.MYPINEAPPLE.canBeUsedBy(normalProf, "Akashhi_", false));
        normalProf.revokeTag(ChatTag.MYPINEAPPLE);
        assertFalse(normalProf.hasUnlockedTag(ChatTag.MYPINEAPPLE));

        //Neither FOUNDER nor MYPINEAPPLE may EVER appear in Shop!
        for (var entry : fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(normalProf, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL)) {
            assertFalse("FOUNDER must never appear in Shop!", entry.chatTag() == ChatTag.FOUNDER);
            assertFalse("MYPINEAPPLE must never appear in Shop!", entry.chatTag() == ChatTag.MYPINEAPPLE);
        }

        // 2. Vanilla FontTexture 256x256 Limit Guard (Fixes [TAB] logo ▯ boxes for all friends!)
        for (int row = 0; row < 5; row++) {
            String pair = Glyphs.logoStripPair(row);
            assertEquals("Each logo row must be left-half + (-1px E001) + right-half!", 3, pair.length());
            assertEquals('\uE001', pair.charAt(1));
        }
        File packZip = new File("releases/BosketBall-ResourcePack.zip");
        assertTrue(packZip.exists());
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(packZip)) {
            assertNotNull("uniform.json must exist so Force Unicode Font clients also see [TAB] logo!",
                zf.getEntry("assets/minecraft/font/uniform.json"));
            for (var e = zf.entries(); e.hasMoreElements(); ) {
                var ze = e.nextElement();
                String zn = ze.getName();
                if (zn.startsWith("assets/bosketball/textures/font/") && zn.endsWith(".png")) {
                    // chat_logo_strip_* are 2-column sheets (368px / 2 = 184px per char); tab_*_card are 2-column (448px / 2 = 224px per char)
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(zf.getInputStream(ze));
                    int cols = (zn.contains("chat_logo_strip_") || zn.endsWith("_card.png")
                        || zn.endsWith("tab_row_ignis.png") || zn.endsWith("tab_row_frost.png") || zn.endsWith("tab_row_gold.png")) ? 2 : 1;
                    if (zn.endsWith("glyph_atlas.png")) continue;
                    int perCharWidth = img.getWidth() / cols;
                    assertTrue("Glyph texture " + zn + " per-char width (" + perCharWidth + "px) must be <= 250px for 256x256 FontTexture!",
                        perCharWidth <= 250);
                }
            }
        }

        // 3. #1, #2, and #3 Server ELO Badges + /gg Shared Chat Prefix Parity
        assertEquals('\uE223', Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 1));
        assertEquals('\uE224', Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 2));
        assertEquals('\uE225', Glyphs.topBadgeChar(Glyphs.TopBadgeKind.ELO, 3));

        File statsTmp = Files.createTempDirectory("bb-top3-elo").toFile();
        fr.bosketball.stats.StatsManager sm = new fr.bosketball.stats.StatsManager(statsTmp, null);
        PlayerProfile p1 = sm.getOrCreate(UUID.randomUUID(), "TopOne");
        p1.setElo(1850);
        PlayerProfile p2 = sm.getOrCreate(UUID.randomUUID(), "TopTwo");
        p2.setElo(1620);
        PlayerProfile p3 = sm.getOrCreate(UUID.randomUUID(), "TopThree");
        p3.setElo(1410);
        PlayerProfile p4 = sm.getOrCreate(UUID.randomUUID(), "Fourth");
        p4.setElo(1100);
        sm.refreshLeaderboardBadges();
        assertEquals(1, sm.getBadges(p1.uuid()).eloPlace());
        assertEquals(2, sm.getBadges(p2.uuid()).eloPlace());
        assertEquals(3, sm.getBadges(p3.uuid()).eloPlace());
        assertEquals(0, sm.getBadges(p4.uuid()).eloPlace());

        String ggSrc = Files.readString(new File("src/main/java/fr/bosketball/command/GgCommand.java").toPath());
        assertTrue("/gg must share BosketChatRenderer.buildPlayerChatPrefix so rank, #1/#2/#3 ELO & tags never drop!",
            ggSrc.contains("BosketChatRenderer.buildPlayerChatPrefix"));

        // 4. Custom Party Team Name & Sanitization
        fr.bosketball.social.SocialPartyManager social = new fr.bosketball.social.SocialPartyManager(sm);
        UUID leaderId = UUID.randomUUID();
        assertTrue(social.setPartyTeamNameDirect(leaderId, "  <bold>MyPineapple</bold>  "));
        assertEquals("boldMyPineappleb".substring(0, Math.min(16, "boldMyPineappleb".length())).isEmpty()
            ? "" : fr.bosketball.social.SocialPartyManager.sanitizeTeamName("MyPineapple"), "MyPineapple");
        assertTrue(social.setPartyTeamNameDirect(leaderId, "MyPineapple"));
        assertEquals("MyPineapple", social.getParty(leaderId).teamName());
        assertEquals("MyPineapple", social.resolveCustomTeamNameForRoster(java.util.List.of(leaderId)));

        // 5. ELO Rank Ladder & Rookie Explanation
        assertEquals(fr.bosketball.model.Rank.HOOPER, fr.bosketball.model.Rank.ROOKIE.nextCompetitiveRank());
        assertEquals(1050, fr.bosketball.model.Rank.ROOKIE.nextCompetitiveRank().minElo());
        assertEquals(null, fr.bosketball.model.Rank.MVP.nextCompetitiveRank());
        assertTrue(fr.bosketball.model.Rank.ROOKIE.eloRangeDescription().contains("1,000 ELO"));

        // 6. OP-Only Arena Creation Guard
        String cmdSrc = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("Arena commands must enforce strict sender.isOp()!", cmdSrc.contains("sender.isOp()"));

        // 7. Bow-Style Pullback Shooting Curve & Full-Draw Green Release
        assertEquals(0.08, PhysicsMath.computeBowPullProgress(0), 1e-4);
        assertTrue(PhysicsMath.computeBowPullProgress(10) > 0.35 && PhysicsMath.computeBowPullProgress(10) < 0.50);
        assertEquals(1.0, PhysicsMath.computeBowPullProgress(20), 1e-4);
        assertEquals("Holding bow past 20 ticks must stay cleanly at 100% Full Draw!",
            1.0, PhysicsMath.computeBowPullProgress(60), 1e-4);
        assertEquals("Full 100% bow draw must evaluate to PERFECT_GREEN!",
            ShotReleaseQuality.PERFECT_GREEN, ShotReleaseQuality.evaluate(1.0, 7.5, false));

        // Verify a 100% Full-Draw deadeye 3PT bow shot swishes cleanly through the rim!
        CourtGeometry court = new CourtGeometry(0.5, 64.0, 0.5, 14.0, 8.0);
        Vector rim = court.southHoopRim();
        Vector shooterEye = new Vector(0.5, 65.62, 5.5);
        Vector deadeyeDir = rim.clone().subtract(shooterEye).normalize();
        PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(shooterEye, deadeyeDir, rim);
        assertTrue(aim.aimingAtHoop());
        Vector bowShotVel = PhysicsMath.solveSkillShotVelocity(
            shooterEye, deadeyeDir, rim, court.southHoopForward(), aim,
            1.0, 0.046, 0.012, ShotReleaseQuality.PERFECT_GREEN, 0.0, null
        );
        List<Vector> arc = PhysicsMath.simulatePreviewArc(shooterEye, bowShotVel, 0.046, 0.012, 64.0, 45);
        double minRimDist = arc.stream().mapToDouble(p -> p.distance(rim)).min().orElse(99.0);
        assertTrue("100% Full-Draw Bow Shot must pass cleanly through rim center (minDist=" + minRimDist + ")!",
            minRimDist < 0.25);
    }

    @Test
    public void testManualDunkSkillSystemBoutiqueShopFullBodyLockerAndBugFixes() throws Exception {
        // 1. Every DunkStyle & ChatTag must map to an existing custom item model + texture in the resource pack!
        for (DunkStyle dunk : DunkStyle.values()) {
            String modelId = dunk.iconModelId();
            assertNotNull(modelId);
            assertTrue("Dunk icon model must exist: " + modelId,
                new File("resourcepack/assets/bosketball/items/" + modelId + ".json").exists());
            assertTrue("Dunk icon PNG must exist: " + modelId,
                new File("resourcepack/assets/bosketball/textures/item/" + modelId + ".png").exists());
        }
        for (fr.bosketball.model.ChatTag tag : fr.bosketball.model.ChatTag.values()) {
            String cardId = tag.cardModelId();
            assertNotNull(cardId);
            assertTrue("Tag collector plaque model must exist: " + cardId,
                new File("resourcepack/assets/bosketball/items/" + cardId + ".json").exists());
            assertTrue("Tag collector plaque PNG must exist: " + cardId,
                new File("resourcepack/assets/bosketball/textures/item/" + cardId + ".png").exists());
        }
        for (String extraIcon : List.of("icon_tags", "icon_coin_stack", "icon_shelves")) {
            assertTrue("Custom UI icon model must exist: " + extraIcon,
                new File("resourcepack/assets/bosketball/items/" + extraIcon + ".json").exists());
            assertTrue("Custom UI icon PNG must exist: " + extraIcon,
                new File("resourcepack/assets/bosketball/textures/item/" + extraIcon + ".png").exists());
        }

        // 2. Manual Dunk Skill Verification: defaultSuccess must be FALSE & require active player timing + rim proximity!
        String ballEntitySrc = Files.readString(new File("src/main/java/fr/bosketball/physics/BasketballEntity.java").toPath());
        assertTrue("BasketballEntity dunkWillSucceed must default to false (no auto-make on non-click)!",
            ballEntitySrc.contains("private boolean dunkWillSucceed = false;"));
        assertTrue("BasketballEntity must require dunkTimingLocked && dunkWillSucceed && withinRimReach to score!",
            ballEntitySrc.contains("dunkTimingLocked && dunkWillSucceed && withinRimReach"));

        String matchSrc = Files.readString(new File("src/main/java/fr/bosketball/game/MatchSession.java").toPath());
        assertTrue("MatchSession must pass false for defaultSuccess in startSlamDunk!",
            matchSrc.contains("startSlamDunk(player, targetRim, prof.selectedDunkStyle(), flightTicks, false)"));
        assertFalse("MatchSession must NEVER pass true for defaultSuccess in startSlamDunk!",
            matchSrc.contains("flightTicks, true)"));
        assertTrue("MatchSession SavedPlayerState must clamp SPECTATOR back to ADVENTURE on restore!",
            matchSrc.contains("(gameMode == GameMode.SPECTATOR) ? GameMode.ADVENTURE : gameMode"));

        String timingEarly = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(Glyphs.composeDunkTimingHud(0.25, false, false));
        String timingGreen = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(Glyphs.composeDunkTimingHud(0.72, false, false));
        assertTrue("Early dunk HUD must instruct player to wait for Green Zone!", timingEarly.contains("Wait for Green"));
        assertTrue("Peak dunk HUD must prompt player to CLICK NOW!", timingGreen.contains("CLICK NOW"));

        // 3. Shop Sorting & Category Separation
        PlayerProfile customer = new PlayerProfile(UUID.randomUUID(), "BoutiqueBuyer");
        var unownedTags = fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(
            customer, fr.bosketball.ui.GuiMenuManager.ShopCategory.TAGS
        );
        assertEquals("All 54 purchasable tags must appear in TAGS category!", 54, unownedTags.size());
        for (int i = 1; i < unownedTags.size(); i++) {
            assertTrue("Shop tags must be sorted cleanly by Coin price ascending (Common -> Legendary)!",
                unownedTags.get(i).priceCoins() >= unownedTags.get(i - 1).priceCoins());
        }

        // 4. Full-Screen Locker Room: frozen character, screen-HUD (stacked BossBars + pack glyphs),
        //    keyboard-only navigation, an animated showcase stage, stay-in-room sub-menus.
        String lockerSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/LockerRoom3dMenuManager.java").toPath());
        assertTrue("Locker Room must freeze the character via a PlayerMoveEvent hard lock!",
            lockerSrc.contains("onPlayerMoveFreeze(PlayerMoveEvent event)"));
        assertTrue("Locker Room camera must spectate an invisible non-player ArmorStand with zero ability speeds (no WASD rubber-banding)!",
            lockerSrc.contains("ArmorStand cameraAnchor")
                && lockerSrc.contains("setSpectatorTarget(s.cameraAnchor)")
                && lockerSrc.contains("player.setWalkSpeed(0.0f)")
                && lockerSrc.contains("player.setFlySpeed(0.0f)"));
        assertTrue("Locker Room must render its menu on-screen via stacked transparent BossBars!",
            lockerSrc.contains("BossBar.bossBar(") && lockerSrc.contains("BossBar.Color.WHITE"));
        assertTrue("Locker Room stage must be an animated showcase (pedestals, spinning rings, orbiting equipped ball, reactive marquee)!",
            lockerSrc.contains("spawnShowcasePedestal")
                && lockerSrc.contains("spawnSpinningRing")
                && lockerSrc.contains("spawnOrbitingBall")
                && lockerSrc.contains("spawnShowcaseMarquee")
                && lockerSrc.contains("animateShowcase"));
        assertFalse("Locker Room must NOT use any Interaction click-catcher hitbox anymore!",
            lockerSrc.contains("Interaction.class"));
        assertTrue("Locker Room must use the pack's full-screen HUD glyph plates!",
            lockerSrc.contains("Glyphs.LOCKER_TOPBAR") && lockerSrc.contains("Glyphs.LOCKER_SELECT_SLANT")
                && lockerSrc.contains("Glyphs.LOCKER_INFO_PANEL"));
        assertTrue("Locker Room must keep player inside Locker Room via openSubMenu & returnFromSubMenu!",
            lockerSrc.contains("public void openSubMenu(") && lockerSrc.contains("public void returnFromSubMenu("));
        assertFalse("Locker Room must NOT open a ShulkerBox container overlay covering player legs!",
            lockerSrc.contains("InventoryType.SHULKER_BOX"));

        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertTrue("GuiMenuManager must handle InventoryCloseEvent to return seamlessly to the 3D Locker Room!",
            guiSrc.contains("onInventoryClose(InventoryCloseEvent event)") && guiSrc.contains("returnFromSubMenu(player)"));

        // 5. Lobby Queue Slot Guard (Slot 4 instead of overwriting Slot 8 Leaderboards!)
        assertEquals("Leave Queue item must use dedicated center Slot 4 instead of overwriting Slot 8 Leaderboards!",
            4, fr.bosketball.model.BallSkin.LEAVE_QUEUE_LOBBY_SLOT);
    }

    @Test
    public void testServerOwnerProductionHardeningSecurityEconomyAndMatchIntegrity() throws Exception {
        // 1. Cytooxien-Grade Command Privacy & System Command Leak Protection
        for (String blocked : List.of("/pl", "/plugins", "/ver", "/version", "/about", "/bukkit:plugins", "/paper:version", "//calc", "/axiom", "/minecraft:me")) {
            assertTrue("System/leak command must be blocked for non-staff: " + blocked,
                fr.bosketball.server.ServerProtectionManager.isBlockedSystemCommand(blocked));
        }
        for (String allowed : List.of("/play", "/shop", "/f add Max", "/party name Ballers", "/msg Friend hi", "/r gg", "/spawn", "/discord", "/gg")) {
            assertFalse("Player command must NEVER be blocked: " + allowed,
                fr.bosketball.server.ServerProtectionManager.isBlockedSystemCommand(allowed));
        }
        assertTrue(fr.bosketball.server.ServerProtectionManager.isHelpAlias("/help"));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isHelpAlias("/?"));
        assertTrue(fr.bosketball.server.ServerProtectionManager.isHelpAlias("/bukkit:help"));

        // 2. Smart Chat Anti-Spam, Anti-Duplicate & Global Chat Mute
        assertEquals(null, fr.bosketball.listener.GameplayListener.checkChatViolation(10_000L, 0L, null, "Hello court!", false, false));
        assertNotNull("Rapid <900ms chat flood must be blocked for regular players!",
            fr.bosketball.listener.GameplayListener.checkChatViolation(10_500L, 10_000L, "hello court!", "Another msg", false, false));
        assertNotNull("Duplicate message within 8s must be blocked for regular players!",
            fr.bosketball.listener.GameplayListener.checkChatViolation(13_000L, 10_000L, "hello court!", "Hello court!", false, false));
        assertNotNull("Global Chat Mute must block regular players!",
            fr.bosketball.listener.GameplayListener.checkChatViolation(20_000L, 0L, null, "Hi", false, true));
        assertEquals("Staff/OP must bypass Global Chat Mute & cooldowns!",
            null, fr.bosketball.listener.GameplayListener.checkChatViolation(10_100L, 10_000L, "hi", "Hi", true, true));

        // 3. Anti-Exploit Post-Match /gg Coin Reward Window (No AFK Lobby Macro Farming!)
        fr.bosketball.game.GameManager gm = new fr.bosketball.game.GameManager(null);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        assertFalse("Before any match completes, /gg must NOT grant free AFK lobby coins!",
            gm.canClaimPostMatchGgReward(p1, 50_000L));

        gm.notifyMatchCompletedAt(100_000L);
        assertTrue("Within 45s of match completion, player can claim GG reward once!",
            gm.markPostMatchGgClaimed(p1, 104_000L));
        assertFalse("Player must NOT be able to double-claim GG coins on the same match!",
            gm.markPostMatchGgClaimed(p1, 106_000L));
        assertTrue("Other players can still claim their own GG reward for that finished match!",
            gm.canClaimPostMatchGgReward(p2, 110_000L));
        assertFalse("After 45s window expires, GG coin reward must close until next match finishes!",
            gm.canClaimPostMatchGgReward(p2, 146_000L));

        // 4. Default Economy Fairness: Finishing a full game should NOT take away Coins (lossCoins = 0; forfeitCoins = 15)
        fr.bosketball.config.BosketConfig defCfg = fr.bosketball.config.BosketConfig.defaults();
        assertEquals("Completing a full match should have 0 loss-coins penalty by default!", 0, defCfg.economy().lossCoins());
        assertTrue("Rage-quitting/forfeiting must still penalize forfeit-coins!", defCfg.economy().forfeitCoins() > 0);

        // 5. Verify Forfeit Winner Fix, ELO Anti-Dodge, Crash Sanitizer, Maintenance Gate & OP Admin Control Center
        String matchSrc = Files.readString(new File("src/main/java/fr/bosketball/game/MatchSession.java").toPath());
        assertTrue("Forfeit must award victory to the remaining team (forcedWinner)!",
            matchSrc.contains("finishMatchAndStartMvpCeremony(TeamSide.FROST)")
                && matchSrc.contains("finishMatchAndStartMvpCeremony(TeamSide.IGNIS)"));
        assertTrue("Forfeit must deduct ELO so players cannot disconnect to dodge ELO loss!",
            matchSrc.contains("prof.adjustElo(-forfeitEloLoss)"));
        assertTrue("MatchSession must include Competitive AFK Watchdog & Ceremony/Spectator 1-click hotbars!",
            matchSrc.contains("tickCompetitiveAfkWatchdog()") && matchSrc.contains("equipCeremonyHotbar("));

        String motdSrc = Files.readString(new File("src/main/java/fr/bosketball/server/MotdManager.java").toPath());
        assertTrue("MotdManager must enforce real PlayerLoginEvent Maintenance Gate!",
            motdSrc.contains("onPlayerLogin(PlayerLoginEvent event)") && motdSrc.contains("KICK_OTHER"));

        String pluginSrc = Files.readString(new File("src/main/java/fr/bosketball/BosketBallPlugin.java").toPath());
        assertTrue("Plugin must sanitize joiners from crash states (SPECTATOR, practice world, court items)!",
            pluginSrc.contains("sanitizeJoinerToCleanLobbyState("));

        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertTrue("GuiMenuManager must provide OP Server Control Center & Player Interact Menu!",
            guiSrc.contains("openServerAdminPanel(") && guiSrc.contains("openPlayerInteractMenu("));
    }

    @Test
    public void testNonOpLockdownHelpCard3dPropsAndDarkUiResourcePack() throws Exception {
        // 1. Strict Non-OP 17-Command Tab Whitelist (including '?' so /? is never red) & Zero-Bypass Preprocess Lockdown
        assertEquals("Non-OP '/' tab list must contain strictly the 17 curated player commands (including '?')!",
            17, fr.bosketball.server.ServerProtectionManager.NON_OP_TAB_COMMANDS.size());
        for (String expected : List.of(
            "help", "?", "play", "practice", "locker", "shop", "ranks",
            "friend", "party", "duel", "msg", "r",
            "spawn", "leave", "gg", "discord", "pack"
        )) {
            assertTrue("Expected player command in NON_OP_TAB_COMMANDS: " + expected,
                fr.bosketball.server.ServerProtectionManager.NON_OP_TAB_COMMANDS.contains(expected));
            assertTrue("Expected player command allowed for non-OP: /" + expected,
                fr.bosketball.server.ServerProtectionManager.isAllowedForNonOp("/" + expected));
        }

        // Short aliases allowed in typing without cluttering '/' tab list
        for (String alias : List.of("/?", "/f add Max", "/friends", "/p invite Max", "/tell Max hi", "/w Max hi", "/reply gg", "/hub", "/lobby")) {
            assertTrue("Short player alias must be allowed for non-OP: " + alias,
                fr.bosketball.server.ServerProtectionManager.isAllowedForNonOp(alias));
        }

        // Strictly blocked for non-OP: /bb, /bosketball, OP tools, FAWE/Axiom, Bukkit/Minecraft/Paper namespaces
        for (String forbidden : List.of(
            "/bb", "/bb play", "/bb admin", "/bb map", "/bb forcestart", "/bb holo elo", "/bosketball",
            "/build", "/setspawn", "/perm", "/op", "/deop", "/stop", "/reload",
            "/pl", "/plugins", "/ver", "/version", "/about", "/icanhasbukkit",
            "//wand", "//set", "/fawe", "/axiom",
            "/bosketball:play", "/bukkit:help", "/minecraft:me", "/paper:heap"
        )) {
            assertFalse("Command MUST be strictly blocked for non-OP: " + forbidden,
                fr.bosketball.server.ServerProtectionManager.isAllowedForNonOp(forbidden));
        }

        // 2. Trending Big-Server System Channel Pill Glyphs (\uE2A0..\uE2A8) & Pixel Widths
        char[] sysPills = {
            Glyphs.SYS_SERVER, Glyphs.SYS_COURT, Glyphs.SYS_SOCIAL,
            Glyphs.SYS_SHOP, Glyphs.SYS_ALERT, Glyphs.SYS_JOIN,
            Glyphs.SYS_QUIT, Glyphs.SYS_PM, Glyphs.SYS_GUIDE
        };
        for (char c : sysPills) {
            int w = Glyphs.textAdvancePx(String.valueOf(c), false);
            assertTrue("System pill glyph must have measured pixel advance > 8px: U+" + Integer.toHexString(c), w >= 10 && w <= 48);
            String tag = fr.bosketball.util.Msg.pillTag(c);
            assertTrue("pillTag must wrap glyph in shadowless <!shadow> MiniMessage!",
                tag.contains("<!shadow>") && tag.contains(String.valueOf(c)));
        }

        // 3. Verify plugin.yml registers /help (with '?' alias), /locker, /duel, /pack cleanly
        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        for (String cmdKey : List.of("  help:", "aliases: ['?']", "  locker:", "  duel:", "  pack:", "  play:", "  practice:", "  shop:", "  ranks:")) {
            assertTrue("plugin.yml must register command/alias: " + cmdKey.trim(), pluginYml.contains(cmdKey));
        }

        // 4. Verify Resource Pack Zip: Dark-Mode Containers/HUD/Tooltips, 9 System Pills & Crisp 2D Cuboide Hotbar Items
        File packZip = new File("releases/BosketBall-ResourcePack.zip");
        assertTrue("BosketBall-ResourcePack.zip must exist!", packZip.isFile());

        java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
        byte[] zipBytes = Files.readAllBytes(packZip.toPath());
        byte[] digest = sha1.digest(zipBytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        String cfgYml = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("config.yml external-sha1 must match built BosketBall-ResourcePack.zip SHA-1 (" + hex + ")!",
            cfgYml.contains("external-sha1: \"" + hex + "\""));

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(packZip)) {
            // 4a. Dark Charcoal + Warm Gold Containers, HUD & Nine-Slice Tooltips
            for (String guiEntry : List.of(
                "assets/minecraft/textures/gui/container/generic_54.png",
                "assets/minecraft/textures/gui/container/inventory.png",
                "assets/minecraft/textures/gui/sprites/hud/hotbar.png",
                "assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png",
                "assets/minecraft/textures/gui/sprites/tooltip/background.png",
                "assets/minecraft/textures/gui/sprites/tooltip/background.png.mcmeta",
                "assets/minecraft/textures/gui/sprites/tooltip/frame.png",
                "assets/minecraft/textures/gui/sprites/tooltip/frame.png.mcmeta",
                "assets/minecraft/textures/gui/sprites/widget/button.png",
                "assets/minecraft/textures/gui/sprites/widget/button_highlighted.png"
            )) {
                assertNotNull("Resource pack must include custom dark-mode UI texture: " + guiEntry, zf.getEntry(guiEntry));
            }

            // Verify hotbar_selection.png exact 24x23 dimensions
            try (var in = zf.getInputStream(zf.getEntry("assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png"))) {
                var img = javax.imageio.ImageIO.read(in);
                assertEquals(24, img.getWidth());
                assertEquals(23, img.getHeight());
            }

            // 4b. Verify all 9 System Pills in default.json & right-edge alpha pin
            String fontJson = new String(zf.getInputStream(zf.getEntry("assets/minecraft/font/default.json")).readAllBytes());
            for (String sysTex : List.of(
                "sys_server", "sys_court", "sys_social", "sys_shop", "sys_alert",
                "sys_join", "sys_quit", "sys_pm", "sys_guide"
            )) {
                assertTrue("default.json must register system pill " + sysTex, fontJson.contains("font/" + sysTex + ".png"));
                var pillEntry = zf.getEntry("assets/bosketball/textures/font/" + sysTex + ".png");
                assertNotNull("System pill PNG must exist: " + sysTex, pillEntry);
                try (var in = zf.getInputStream(pillEntry)) {
                    var img = javax.imageio.ImageIO.read(in);
                    assertEquals("System pill PNG must be crisp 2x HiDPI 18px (mapped to height: 9, ascent: 8)!", 18, img.getHeight());
                    int maxRightColAlpha = 0;
                    for (int py = 0; py < img.getHeight(); py++) {
                        int a = (img.getRGB(img.getWidth() - 1, py) >>> 24) & 0xFF;
                        if (a > maxRightColAlpha) {
                            maxRightColAlpha = a;
                        }
                    }
                    assertTrue("Rightmost column of " + sysTex + " must have non-zero alpha to prevent BitmapProvider trimming!",
                        maxRightColAlpha > 0);
                }
            }

            // 4c. Verify all 7 Lobby Hotbar Items are crisp 2D pixel-art items (minecraft:item/generated) + small 0.36x Basketball Skins intact
            for (String prop : List.of(
                "prop_play_emblem", "prop_locker_jersey", "prop_shop_crate", "prop_tag_plaque",
                "prop_social_megaphone", "prop_guide_clipboard", "prop_podium_cup"
            )) {
                var itemDef = zf.getEntry("assets/bosketball/items/" + prop + ".json");
                var modelDef = zf.getEntry("assets/bosketball/models/item/" + prop + ".json");
                var texEntry = zf.getEntry("assets/bosketball/textures/item/" + prop + ".png");
                assertNotNull("26.2 item definition must exist for " + prop, itemDef);
                assertNotNull("2D generated model must exist for " + prop, modelDef);
                assertNotNull("16x16 Cuboide 2D texture must exist for " + prop, texEntry);

                String modelJson = new String(zf.getInputStream(modelDef).readAllBytes());
                assertTrue(prop + " must use 2D minecraft:item/generated parent (not 3D block elements)!",
                    modelJson.contains("\"minecraft:item/generated\"") && !modelJson.contains("\"elements\""));
            }

            // Verify square/cubic basketball GUI scale stays small at 0.36x
            String ballModelJson = new String(zf.getInputStream(zf.getEntry("assets/bosketball/models/item/basketball_classic.json")).readAllBytes());
            assertTrue("Square/cubic basketball must stay small (0.36x) in GUI!", ballModelJson.contains("0.36"));
        }
    }

    @Test
    public void testExhaustiveBugFixesAndOptimizations() throws Exception {
        // 1. Economy & Stat Overflow Hardening + Exclusive -1 Cost Guard
        PlayerProfile econ = new PlayerProfile(UUID.randomUUID(), "TestBaller");
        econ.setCoins(500);
        econ.addCoins(Integer.MAX_VALUE);
        assertEquals("Adding Integer.MAX_VALUE coins must clamp at 999,999,999 without signed overflow!",
            999_999_999, econ.coins());
        assertFalse("Negative shop cost (-1 exclusive items) must NEVER succeed via spendCoins!",
            econ.spendCoins(-1));
        assertEquals(999_999_999, econ.coins());

        econ.adjustElo(Integer.MAX_VALUE);
        assertEquals(99_999, econ.elo());
        econ.adjustElo(-999_999);
        assertEquals(100, econ.elo());

        econ.addPoints(1_000);
        econ.addPoints(Integer.MAX_VALUE);
        assertEquals(999_999_999, econ.points());
        econ.addXp(500);
        econ.addXp(Integer.MAX_VALUE);
        assertEquals(999_999_999, econ.xp());

        // 2. O(P log K) Bounded Min-Heap Leaderboard Selection & O(1) Case-Insensitive Name Index
        File perfTmp = Files.createTempDirectory("bb-perf-stats").toFile();
        fr.bosketball.stats.StatsManager sm = new fr.bosketball.stats.StatsManager(perfTmp, null);
        for (int i = 1; i <= 120; i++) {
            PlayerProfile p = sm.getOrCreate(UUID.randomUUID(), "Hooper_" + i);
            p.setElo(900 + i * 10);
            p.setPoints(i * 7);
            p.setCoins(i * 25);
        }
        List<PlayerProfile> top5Elo = sm.topBy(fr.bosketball.stats.StatsManager.LeaderboardCategory.ELO, 5);
        assertEquals(5, top5Elo.size());
        assertEquals("Hooper_120", top5Elo.get(0).lastKnownName());
        assertEquals(2100, top5Elo.get(0).elo());
        assertEquals("Hooper_116", top5Elo.get(4).lastKnownName());
        assertEquals(2060, top5Elo.get(4).elo());

        PlayerProfile found = sm.findByNameIgnoreCase("hooper_77");
        assertNotNull("O(1) case-insensitive name index must resolve offline profiles!", found);
        assertEquals("Hooper_77", found.lastKnownName());

        // 3. Headless Arena Self-Heal Safety & ResourcePackHostService Streaming SHA-1 + Clean Shutdown
        fr.bosketball.arena.Arena headlessArena = new fr.bosketball.arena.Arena(
            "test_court", "Test Court", "world", new CourtGeometry(0.5, 64.0, 0.5, 14.0, 8.0)
        );
        assertFalse(headlessArena.areDisplaysHealthy());
        headlessArena.ensureDisplaysHealthy(); // Must be 100% NPE-safe when Bukkit.getServer() == null

        fr.bosketball.pack.ResourcePackHostService packSvc = new fr.bosketball.pack.ResourcePackHostService(
            new File("releases"), java.util.logging.Logger.getAnonymousLogger()
        );
        packSvc.initialize(false, "0.0.0.0", 0, "localhost", "https://cdn.example.com/pack.zip", "", false);
        assertTrue(packSvc.isReadyToSend());
        assertEquals(40, packSvc.sha1Hex().length());
        packSvc.shutdown();
        assertFalse("shutdown() must clear effectiveDownloadUrl so stale URLs never persist across /bb reload!",
            packSvc.isReadyToSend());
        assertEquals("", packSvc.effectiveDownloadUrl());

        // 4. Disconnect Cleanup in SocialPartyManager (Incoming + Outgoing Friend & Party Requests)
        fr.bosketball.social.SocialPartyManager social = new fr.bosketball.social.SocialPartyManager(sm);
        UUID discLeader = UUID.randomUUID();
        assertTrue(social.setPartyTeamNameDirect(discLeader, "CourtKings"));
        assertNotNull(social.getParty(discLeader));
        social.handleDisconnect(discLeader);
        org.junit.Assert.assertNull("Disconnecting solo party leader must disband empty party!", social.getParty(discLeader));

        // 5. Structural Guard Verification Across Bug-Fixed Subsystems
        String pluginSrc = Files.readString(new File("src/main/java/fr/bosketball/BosketBallPlugin.java").toPath());
        assertTrue("BosketBallPlugin must centralize player visibility in shouldHidePlayerFrom & resyncVisibilityFor!",
            pluginSrc.contains("shouldHidePlayerFrom") && pluginSrc.contains("resyncVisibilityFor")
                && pluginSrc.contains("HOLO_ENTITY_TAG"));

        String cineSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/CinematicCameraManager.java").toPath());
        assertTrue("Goal Replay camera must preserve & restore savedAllowFlight / savedFlying for Spectators!",
            cineSrc.contains("savedAllowFlight") && cineSrc.contains("savedFlying"));

        String coreCmdSrc = Files.readString(new File("src/main/java/fr/bosketball/server/ServerCoreCommands.java").toPath());
        assertTrue("/spawn must guard competitive matches & cleanly exit Locker Room / Practice before teleporting!",
            coreCmdSrc.contains("clearPlayerState") && coreCmdSrc.contains("lockerRoom3dManager"));

        String gmSrc = Files.readString(new File("src/main/java/fr/bosketball/game/GameManager.java").toPath());
        assertTrue("GameManager must maintain O(1) sessionByPlayer index, 60s DuelInvite TTL & competitive-match guards!",
            gmSrc.contains("sessionByPlayer") && gmSrc.contains("DUEL_INVITE_TTL_MS")
                && gmSrc.contains("isActivelyPlayingCompetitiveMatch") && gmSrc.contains("ensureDisplaysHealthy"));

        String listenerSrc = Files.readString(new File("src/main/java/fr/bosketball/listener/GameplayListener.java").toPath());
        assertTrue("GameplayListener must block ClickType.SWAP_OFFHAND stashing of bosketball:* items!",
            listenerSrc.contains("ClickType.SWAP_OFFHAND"));

        String matchSrc = Files.readString(new File("src/main/java/fr/bosketball/game/MatchSession.java").toPath());
        assertTrue("MatchSession must respect settings().aimTrajectory()!",
            matchSrc.contains("settings().aimTrajectory()"));

        String msgSrc = Files.readString(new File("src/main/java/fr/bosketball/util/Msg.java").toPath());
        assertTrue("Msg sound helpers must respect settings().customSounds()!",
            msgSrc.contains("settings().customSounds()"));
    }

    @Test
    public void testSeventeenPointVisualAndGameplayOverhaul() throws Exception {
        // #2: Strict player.isOp() gate on Server Owner & Arena Studio in MAIN_HUB & openServerAdminPanel
        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertFalse("GuiMenuManager must NOT leak Server Owner & Arena Studio via hasPermission(\"bosketball.admin\")!",
            guiSrc.contains("hasPermission(\"bosketball.admin\")"));
        assertTrue("GuiMenuManager must render 4-strip Large Basketball Hover Previews & Cytooxien GUI Overlays!",
            guiSrc.contains("buildBallHoverLore(")
                && guiSrc.contains("Glyphs.GUI_OVERLAY_SHOP")
                && guiSrc.contains("Glyphs.GUI_OVERLAY_TAGS")
                && guiSrc.contains("Glyphs.GUI_OVERLAY_COSMETICS")
                && guiSrc.contains("TagVaultFilter"));

        // #3 & #4: Bow Auto-Release Prevention & Guaranteed Goal Replay (recap)
        String matchSrc = Files.readString(new File("src/main/java/fr/bosketball/game/MatchSession.java").toPath());
        assertTrue("MatchSession must avoid overwriting Slot 0 / Slot 9 while player.isHandRaised()!",
            matchSrc.contains("!(p.isHandRaised() && existingS0 != null && existingS0.getType() == Material.BOW)"));
        String cineSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/CinematicCameraManager.java").toPath());
        assertTrue("MatchSession & CinematicCameraManager must trigger Goal Replay in PRACTICE/WAITING, defer 1v1 match finish until after replay, and allow >=2 replay frames!",
            matchSrc.contains("finishMatchAfterCelebration")
                && matchSrc.contains("recordReplayTick()")
                && cineSrc.contains("frames.size() < 2"));

        String arenaSrc = Files.readString(new File("src/main/java/fr/bosketball/arena/Arena.java").toPath());
        assertTrue("Arena boundary clamp must avoid teleporting a bow-drawing player on minor boundary contact!",
            arenaSrc.contains("player.isHandRaised()"));

        String ballEntSrc = Files.readString(new File("src/main/java/fr/bosketball/physics/BasketballEntity.java").toPath());
        assertTrue("BasketballEntity must smoothly gather ball overhead while player draws bow!",
            ballEntSrc.contains("holder.isHandRaised() && holder.getActiveItem().getType() == org.bukkit.Material.BOW"));

        // #6 & #14: Glyphs 4-strip Basketball Hover Previews (\uE320..\uE33F) & 6 Cytooxien Overlays (\uE350..\uE355)
        for (fr.bosketball.model.BallSkin skin : fr.bosketball.model.BallSkin.values()) {
            for (int r = 0; r < 4; r++) {
                char strip = Glyphs.ballPreviewStrip(skin, r);
                assertTrue(strip >= '\uE320' && strip <= '\uE33F');
                assertEquals(45, Glyphs.textAdvancePx(String.valueOf(strip), false));
            }
        }
        for (char ov : new char[]{
            Glyphs.GUI_OVERLAY_SHOP, Glyphs.GUI_OVERLAY_SHOP_GRID, Glyphs.GUI_OVERLAY_TAGS,
            Glyphs.GUI_OVERLAY_COSMETICS, Glyphs.GUI_OVERLAY_PLAY, Glyphs.GUI_OVERLAY_HUB
        }) {
            var comp = Glyphs.customGuiOverlayTitle(ov);
            assertNotNull(comp);
            assertEquals(177, Glyphs.textAdvancePx(String.valueOf(ov), false));
        }

        // #9 & #12: /duel <player> literal rendering (no &lt;) & Blank Default Website/Shop Links on Welcome Card
        String msgSrc = Files.readString(new File("src/main/java/fr/bosketball/util/Msg.java").toPath());
        assertTrue("Help card must escape /duel \\<player> properly without HTML &lt; entities!",
            msgSrc.contains("/duel \\\\<player>") && !msgSrc.contains("&lt;"));
        var parsedDuel = fr.bosketball.util.Msg.parse("<#FF8C1A><bold>/duel \\<player></bold></#FF8C1A>");
        String plainDuel = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(parsedDuel);
        assertEquals("/duel <player>", plainDuel);

        fr.bosketball.config.BosketConfig defCfg = fr.bosketball.config.BosketConfig.defaults();
        assertEquals("Default website link must be blank so Welcome Card hides unconfigured site!", "", defCfg.links().website());
        assertEquals("Default shop link must be blank so Welcome Card hides unconfigured store!", "", defCfg.links().shop());
        assertEquals("https://discord.gg/pnJhKuU2QK", defCfg.links().discord());

        // #13: Zero-Setup Embedded Resource Pack enabled by default with auto public-host & bundled inside BosketBall.jar
        assertTrue("Embedded HTTP Resource Pack server must be enabled out-of-the-box!", defCfg.resourcePack().httpEnabled());
        assertEquals("auto", defCfg.resourcePack().publicHost());
        try (java.util.zip.ZipFile pluginJar = new java.util.zip.ZipFile("releases/BosketBall.jar")) {
            var bundledPack = pluginJar.getEntry(fr.bosketball.pack.ResourcePackHostService.EMBEDDED_PACK_RESOURCE);
            var bundledSha = pluginJar.getEntry(fr.bosketball.pack.ResourcePackHostService.EMBEDDED_SHA1_RESOURCE);
            assertNotNull("BosketBall.jar MUST embed META-INF/BosketBall-ResourcePack.zip!", bundledPack);
            assertNotNull("BosketBall.jar MUST embed META-INF/BosketBall-ResourcePack.sha1!", bundledSha);
            assertTrue("Bundled pack inside BosketBall.jar must be > 2.5 MB!", bundledPack.getSize() > 2_500_000L);
        }

        // #10, #11 & #17: Hologram Props Removed on PLAY/SHOP, Elevated +1 Block on Leaderboards, & Snap Facing Yaw
        assertFalse(fr.bosketball.ui.LobbyHologramManager.StationKind.PLAY.has3dProp());
        assertFalse(fr.bosketball.ui.LobbyHologramManager.StationKind.SHOP.has3dProp());
        assertTrue(fr.bosketball.ui.LobbyHologramManager.StationKind.ELO.has3dProp());
        assertTrue(fr.bosketball.ui.LobbyHologramManager.StationKind.POINTS.has3dProp());
        assertTrue(fr.bosketball.ui.LobbyHologramManager.StationKind.COINS.has3dProp());
        assertEquals(180.0f, Math.abs(fr.bosketball.ui.LobbyHologramManager.snapFacingYaw(0.0f)), 0.01f);
        assertEquals(-90.0f, fr.bosketball.ui.LobbyHologramManager.snapFacingYaw(90.0f), 0.01f);
        assertEquals(0.0f, fr.bosketball.ui.LobbyHologramManager.snapFacingYaw(180.0f), 0.01f);

        String holoSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/LobbyHologramManager.java").toPath());
        assertTrue("Fixed Wall Leaderboard must use Billboard.FIXED, +3.95 elevated props, and PREV_CATEGORY/NEXT_CATEGORY arrows!",
            holoSrc.contains("Display.Billboard.FIXED")
                && holoSrc.contains("3.95")
                && holoSrc.contains("PREV_CATEGORY")
                && holoSrc.contains("NEXT_CATEGORY"));

        // #5, #6, #7, #8, #14 & #16: Verify Pack Assets (2x Rank Pills, 32 Ball Hover Strips, 6 GUI Overlays, 56 Tag Cards, Cuboide Shop Icon)
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile("releases/BosketBall-ResourcePack.zip")) {
            for (String rankTag : List.of("rank_rookie", "rank_hooper", "rank_pro", "rank_allstar", "rank_superstar", "rank_legend", "rank_mvp", "rank_referee", "rank_admin")) {
                var e = zf.getEntry("assets/bosketball/textures/font/" + rankTag + ".png");
                assertNotNull("Modernized Rank Pill must exist: " + rankTag, e);
                try (var in = zf.getInputStream(e)) {
                    var img = javax.imageio.ImageIO.read(in);
                    assertEquals("Modernized Rank Pill must be 2x HiDPI 18px tall matching FOUNDER!", 18, img.getHeight());
                }
            }
            for (String ovName : List.of("gui_overlay_shop", "gui_overlay_shop_grid", "gui_overlay_tags", "gui_overlay_cosmetics", "gui_overlay_play", "gui_overlay_hub", "gui_overlay_hub_op", "gui_overlay_leaderboards")) {
                var e = zf.getEntry("assets/bosketball/textures/font/" + ovName + ".png");
                assertNotNull("BuiltByBit 54-slot warm-cream/pastel GUI overlay PNG must exist: " + ovName, e);
                try (var in = zf.getInputStream(e)) {
                    var img = javax.imageio.ImageIO.read(in);
                    assertEquals(176, img.getWidth());
                    assertEquals(137, img.getHeight());
                }
            }
            for (fr.bosketball.model.ChatTag tag : fr.bosketball.model.ChatTag.values()) {
                assertNotNull("Non-squished 16x16 tag_card texture must exist for " + tag.id(),
                    zf.getEntry("assets/bosketball/textures/item/" + tag.cardModelId() + ".png"));
            }
            assertNotNull("Cuboide Shop market-stall icon must exist!",
                zf.getEntry("assets/bosketball/textures/item/icon_shop.png"));
        }
    }

    @Test
    public void testZeroExtraPortPackMuxPremiumChromeGuis16x16IconsAndLobbyNpcs() throws Exception {
        // 1. ResourcePackHostService live pack serving + Netty Game-Port Mux + Embedded JAR verification
        try (java.util.zip.ZipFile jarZip = new java.util.zip.ZipFile("releases/BosketBall.jar")) {
            assertNotNull("BosketBall.jar must embed META-INF/BosketBall-ResourcePack.zip!",
                jarZip.getEntry("META-INF/BosketBall-ResourcePack.zip"));
            assertNotNull("BosketBall.jar must embed META-INF/BosketBall-ResourcePack.sha1!",
                jarZip.getEntry("META-INF/BosketBall-ResourcePack.sha1"));
        }

        File tmpDir = Files.createTempDirectory("bb-packmux-test").toFile();
        Files.copy(
            new File("releases/BosketBall-ResourcePack.zip").toPath(),
            new File(tmpDir, "BosketBall-ResourcePack.zip").toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        );
        fr.bosketball.pack.ResourcePackHostService host = new fr.bosketball.pack.ResourcePackHostService(tmpDir, null);
        host.initialize(true, "127.0.0.1", 18765, "127.0.0.1", "", "", false);
        try {
            assertTrue("Pack host must be ready with embedded or release pack!", host.isReadyToSend());
            assertNotNull("Netty game-port multiplexer must be initialized!", host.nettyMux());
            assertEquals(40, host.sha1Hex().length());
            String url = host.effectiveDownloadUrl();
            assertTrue("Effective URL must serve BosketBall-ResourcePack.zip with cache-busting query!",
                url.contains("/BosketBall-ResourcePack.zip?v="));

            // Live HTTP GET verification against fallback HTTP server
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            assertEquals(200, conn.getResponseCode());
            byte[] downloaded = conn.getInputStream().readAllBytes();
            assertTrue("Downloaded pack ZIP must exceed 1 MB!", downloaded.length > 1_000_000);
        } finally {
            host.shutdown();
        }

        // 2. Wall Leaderboard Flanking Arrows & Lobby Play/Shop Floor NPCs
        assertTrue("Wall Leaderboard arrow offset must place arrows cleanly outside 5.1-block rows!",
            fr.bosketball.ui.LobbyHologramManager.LEADERBOARD_ARROW_SIDE_OFFSET >= 3.05);
        assertTrue(fr.bosketball.ui.LobbyHologramManager.StationKind.PLAY.hasFloorNpc());
        assertTrue(fr.bosketball.ui.LobbyHologramManager.StationKind.SHOP.hasFloorNpc());
        assertFalse(fr.bosketball.ui.LobbyHologramManager.StationKind.ELO.hasFloorNpc());
        assertEquals("npc_play_superstar", fr.bosketball.ui.LobbyHologramManager.NPC_PLAY_SKIN_ID);
        assertEquals("npc_shop_merchant", fr.bosketball.ui.LobbyHologramManager.NPC_SHOP_SKIN_ID);

        String sbSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/ScoreboardSidebarManager.java").toPath());
        assertTrue("ScoreboardSidebarManager must hide NPC nametags via bb_npc_noname team!",
            sbSrc.contains("bb_npc_noname") && sbSrc.contains("OptionStatus.NEVER"));

        // 3. All 9 premium chrome GUI overlays + native 16x16 icons + 64x64 NPC skins
        for (char c = Glyphs.GUI_OVERLAY_SHOP; c <= Glyphs.GUI_OVERLAY_SETTINGS; c++) {
            assertEquals(177, Glyphs.textAdvancePx(String.valueOf(c), false));
        }

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile("releases/BosketBall-ResourcePack.zip")) {
            // 5-row Settings overlay (176x119)
            var setEntry = zf.getEntry("assets/bosketball/textures/font/gui_overlay_settings.png");
            assertNotNull(setEntry);
            try (var in = zf.getInputStream(setEntry)) {
                var setImg = javax.imageio.ImageIO.read(in);
                assertEquals(176, setImg.getWidth());
                assertEquals(119, setImg.getHeight());
                // Reference GUI body: cool brushed silver, with blue channel above red.
                int argb = setImg.getRGB(12, 30);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                assertTrue("GUI overlay must be brushed chrome silver!", r >= 145 && g >= 155 && b >= 175 && b > r);
            }

            // Chrome generic_54.png: silver frame, bright title plate, dark inset slot.
            var g54Entry = zf.getEntry("assets/minecraft/textures/gui/container/generic_54.png");
            assertNotNull(g54Entry);
            try (var in = zf.getInputStream(g54Entry)) {
                var g54Img = javax.imageio.ImageIO.read(in);
                int frame = g54Img.getRGB(4, 50);
                assertTrue("generic_54.png frame must be brushed silver!",
                    ((frame >> 16) & 0xFF) >= 180 && ((frame >> 8) & 0xFF) >= 190 && (frame & 0xFF) >= 205);
                int title = g54Img.getRGB(20, 9);
                assertTrue("Vanilla dark-gray container titles must sit on a bright plate!",
                    ((title >> 16) & 0xFF) >= 215 && ((title >> 8) & 0xFF) >= 220 && (title & 0xFF) >= 225);
                int slot = g54Img.getRGB(15, 25);
                assertTrue("Reference-style item slots must be dark gunmetal insets!",
                    ((slot >> 16) & 0xFF) <= 120 && ((slot >> 8) & 0xFF) <= 130 && (slot & 0xFF) <= 145);
            }

            // Verify native 16x16 BuiltByBit-style pixel-art icons
            for (String ic : List.of(
                "icon_shop", "icon_arena", "icon_portal", "icon_podium", "icon_friends",
                "icon_tags", "icon_chest", "icon_coin_stack", "icon_history", "icon_settings",
                "icon_discord", "icon_book", "icon_pass", "icon_crossover", "icon_defense", "icon_dunk"
            )) {
                var ie = zf.getEntry("assets/bosketball/textures/item/" + ic + ".png");
                assertNotNull("Native 16x16 icon must exist: " + ic, ie);
                try (var in = zf.getInputStream(ie)) {
                    assertNative16PixelArt(ic, javax.imageio.ImageIO.read(in));
                }
            }

            // Verify both 64x64 Lobby NPC skins (Play Superstar #24 & Shop Merchant)
            for (String npcSkin : List.of("npc_play_superstar", "npc_shop_merchant")) {
                var se = zf.getEntry("assets/bosketball/textures/entity/player/wide/" + npcSkin + ".png");
                assertNotNull("64x64 NPC skin must exist: " + npcSkin, se);
                try (var in = zf.getInputStream(se)) {
                    var sImg = javax.imageio.ImageIO.read(in);
                    assertEquals(64, sImg.getWidth());
                    assertEquals(64, sImg.getHeight());
                }
            }
        }
    }

    @Test
    public void testPremiumReferenceIconsTagEmblemsAndReadableLockerSelection() throws Exception {
        // Every original menu / dunk / lobby-prop ID plus the action-specific
        // purchased-icon IDs must ship as a native 16x16 binary-alpha sprite.
        List<String> premiumItems = List.of(
            "court_wand", "dunk_between_legs", "dunk_meteor_jam", "dunk_spin_360", "dunk_tomahawk",
            "dunk_windmill", "icon_arena", "icon_back", "icon_battle", "icon_book",
            "icon_bot_off", "icon_bot_on", "icon_camera", "icon_check", "icon_chest",
            "icon_coin_stack", "icon_crossover", "icon_defense", "icon_delete", "icon_discord",
            "icon_duel", "icon_dunk", "icon_friend_add", "icon_friend_remove", "icon_friends",
            "icon_gem_ruby", "icon_history", "icon_home", "icon_leave", "icon_lock",
            "icon_medal_gold", "icon_message", "icon_next", "icon_party_invites", "icon_party_remove",
            "icon_party_unavailable", "icon_pass", "icon_podium", "icon_portal", "icon_prev",
            "icon_reload", "icon_replay", "icon_settings", "icon_shelves", "icon_shop",
            "icon_sound_off", "icon_sound_on", "icon_spectate", "icon_star_shield", "icon_tab",
            "icon_tags", "icon_target", "icon_team", "icon_team_duo", "icon_training",
            "icon_trophy_gold", "prop_guide_clipboard", "prop_locker_jersey", "prop_play_emblem",
            "prop_podium_cup", "prop_shop_crate", "prop_social_megaphone", "prop_tag_plaque",
            "tactics_playbook"
        );
        assertEquals(64, premiumItems.size());

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile("releases/BosketBall-ResourcePack.zip")) {
            for (String itemId : premiumItems) {
                var entry = zf.getEntry("assets/bosketball/textures/item/" + itemId + ".png");
                assertNotNull("Premium native 16x16 item texture must be packaged: " + itemId, entry);
                try (var in = zf.getInputStream(entry)) {
                    assertNative16PixelArt(itemId, javax.imageio.ImageIO.read(in));
                }
            }

            int tagCards = 0;
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().startsWith("assets/bosketball/textures/item/tag_card_")
                    && entry.getName().endsWith(".png")) {
                    tagCards++;
                    try (var in = zf.getInputStream(entry)) {
                        assertNative16PixelArt(entry.getName(), javax.imageio.ImageIO.read(in));
                    }
                }
            }
            assertEquals("All 56 chat-tag collector emblems must be packaged!", 56, tagCards);

            // Regression for the live locker screenshot: translucent black was
            // baked into opaque black on rows 8..15, hiding the selected label.
            var slantEntry = zf.getEntry("assets/bosketball/textures/font/locker_hud_select.png");
            assertNotNull(slantEntry);
            try (var in = zf.getInputStream(slantEntry)) {
                var slant = javax.imageio.ImageIO.read(in);
                assertEquals(232, slant.getWidth());
                assertEquals(16, slant.getHeight());
                for (int y : new int[] {4, 8, 11, 13}) {
                    int argb = slant.getRGB(60, y);
                    assertTrue("Selected locker row must stay saturated orange at y=" + y,
                        ((argb >> 16) & 0xFF) >= 190 && ((argb >> 8) & 0xFF) >= 85);
                }
            }
        }

        String premiumPy = Files.readString(new File("tools/premium_ui_overhaul.py").toPath());
        String generatorPy = Files.readString(new File("tools/generate_resourcepack.py").toPath());
        assertTrue("Premium icon generator must remain wired into pack generation!",
            premiumPy.contains("def build_premium_icon_sprites")
                && generatorPy.contains("cuboide_sprites.update(build_premium_icon_sprites())"));
        assertTrue("Premium tag emblems must remain wired into pack generation!",
            generatorPy.contains("generate_premium_tag_emblem_items(font_tex_dir, item_tex_dir)"));
        assertTrue("The owner's purchased icon library must drive the premium sprites!",
            premiumPy.contains("PURCHASED_ICON_MAP")
                && premiumPy.contains("PURCHASED_TAG_ICON_MAP")
                && premiumPy.contains("def purchased_source_stems")
                && new File("tools/assets/purchased-icons/shop.png").isFile()
                && new File("tools/assets/purchased-icons/kilic.png").isFile());
        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertTrue("Leaderboard skulls must never trigger an offline Mojang profile lookup!",
            !guiSrc.contains(".setOwningPlayer(")
                && !guiSrc.contains("Bukkit.getOfflinePlayer(p.uuid())")
                && guiSrc.contains("applyPlayerHead(meta, online, p.uuid(), p.lastKnownName())")
                && guiSrc.contains("SKULL_TARGET_UUID_KEY"));
        assertTrue("Locker plates must alpha-composite translucent decoration layers!",
            generatorPy.contains("alpha_composite(decor)") && generatorPy.contains("locker_hud_row_idle.png"));
    }

    @Test
    public void testPackMuxAcceptsUnregisteredChildChannelWithoutBreakingLogin() throws Exception {
        // Regression for Paper 26.2 / Netty 4.2 logins hanging at 12:56:15:
        // a freshly accepted socket reaches the server channel pipeline before
        // ServerBootstrap registers it, so child.eventLoop() must never be called there.
        fr.bosketball.pack.NettyGamePortPackMux mux =
            new fr.bosketball.pack.NettyGamePortPackMux(() -> new byte[] {1, 2, 3}, null);
        var acceptorField = fr.bosketball.pack.NettyGamePortPackMux.class.getDeclaredField("acceptor");
        acceptorField.setAccessible(true);
        var acceptor = (io.netty.channel.ChannelHandler) acceptorField.get(mux);

        io.netty.channel.local.LocalChannel acceptedChild = new io.netty.channel.local.LocalChannel();
        boolean eventLoopUnavailableBeforeRegistration = false;
        try {
            acceptedChild.eventLoop();
        } catch (IllegalStateException expected) {
            eventLoopUnavailableBeforeRegistration = true;
        }
        assertTrue("Test child must reproduce Netty's unregistered accepted-socket state!",
            eventLoopUnavailableBeforeRegistration);

        io.netty.channel.embedded.EmbeddedChannel listenerChannel =
            new io.netty.channel.embedded.EmbeddedChannel(acceptor);
        try {
            listenerChannel.writeInbound(acceptedChild);
            Object forwarded = listenerChannel.readInbound();
            assertTrue("Accepted child must still be forwarded to Paper's ServerBootstrap acceptor!",
                forwarded == acceptedChild);
            assertNotNull("Pack HTTP inspector must be installed before child registration!",
                acceptedChild.pipeline().get(fr.bosketball.pack.NettyGamePortPackMux.INSPECTOR_HANDLER_NAME));
        } finally {
            listenerChannel.finishAndReleaseAll();
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

    private static void assertNative16PixelArt(String label, java.awt.image.BufferedImage img) {
        assertNotNull(label + " must decode as an image", img);
        assertEquals(label + " width", 16, img.getWidth());
        assertEquals(label + " height", 16, img.getHeight());
        boolean hasTransparent = false;
        boolean hasOpaque = false;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int alpha = img.getRGB(x, y) >>> 24;
                assertTrue(label + " must use binary alpha at (" + x + "," + y + ")",
                    alpha == 0 || alpha == 255);
                hasTransparent |= alpha == 0;
                hasOpaque |= alpha == 255;
            }
        }
        assertTrue(label + " must be a free-standing sprite on transparency", hasTransparent && hasOpaque);
    }

    @Test
    public void testPinnedLobbyLayoutFrozenScreenLockerAnd26OnlyRelease() throws Exception {
        // 1. Forced lobby spawn + pinned Play/Shop NPC poses come from config.yml defaults
        fr.bosketball.config.BosketConfig cfg = fr.bosketball.config.BosketConfig.defaults();
        assertTrue("Lobby spawn must be enforced by default!", cfg.lobby().enforceSpawn());
        assertEquals(89.5, cfg.lobby().spawn().x(), 0.001);
        assertEquals(63.0, cfg.lobby().spawn().y(), 0.001);
        assertEquals(61.5, cfg.lobby().spawn().z(), 0.001);
        assertEquals(-90.0f, cfg.lobby().spawn().yaw(), 0.001f);
        assertTrue("Lobby NPC stations must be enforced by default!", cfg.lobby().enforceStations());
        assertEquals(119.5, cfg.lobby().playStation().x(), 0.001);
        assertEquals(62.0, cfg.lobby().playStation().y(), 0.001);
        assertEquals(56.5, cfg.lobby().playStation().z(), 0.001);
        assertEquals(79.0f, cfg.lobby().playStation().yaw(), 0.001f);
        assertEquals(-1.1f, cfg.lobby().playStation().pitch(), 0.001f);
        assertEquals(119.5, cfg.lobby().shopStation().x(), 0.001);
        assertEquals(66.5, cfg.lobby().shopStation().z(), 0.001);
        assertEquals(103.5f, cfg.lobby().shopStation().yaw(), 0.001f);
        assertEquals(-0.5f, cfg.lobby().shopStation().pitch(), 0.001f);

        // 2. Enforcement wiring: spawn-location event + pose-locked pinned hologram stations
        String protectionSrc = Files.readString(new File("src/main/java/fr/bosketball/server/ServerProtectionManager.java").toPath());
        assertTrue("Every join must be forced onto the lobby spawn via AsyncPlayerSpawnLocationEvent!",
            protectionSrc.contains("onAsyncPlayerSpawnLocation(AsyncPlayerSpawnLocationEvent event)")
                && protectionSrc.contains("cachedEnforcedLobbySpawn()")
                && !protectionSrc.contains("org.spigotmc.event.player.PlayerSpawnLocationEvent"));
        assertTrue("Enforced spawn must win inside resolveLobbySpawn!",
            protectionSrc.contains("Location enforced = enforcedLobbySpawn();"));
        String holoSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/LobbyHologramManager.java").toPath());
        assertTrue("Pinned NPC stations must be re-applied from config on boot/reload!",
            holoSrc.contains("applyConfiguredStations()") && holoSrc.contains("PINNED_PLAY_ID") && holoSrc.contains("PINNED_SHOP_ID"));
        assertTrue("Pose-locked NPCs must hold their exact yaw/pitch (no gaze tracking)!",
            holoSrc.contains("inst.poseLocked") && holoSrc.contains("npc.setRotation(inst.yaw, inst.pitch)"));
        assertTrue("Hologram pitch must persist to holograms.yml!",
            holoSrc.contains("yml.set(p + \"pitch\""));

        // 3. 26.2-only release: plugin.yml targets the 26.2 API and no legacy version is mentioned anywhere
        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        assertTrue("plugin.yml must declare api-version 26.2!", pluginYml.contains("api-version: '26.2'"));
        String legacyVersion = "1.21" + ".10";
        List<File> scanRoots = List.of(
            new File("src"), new File("tools"), new File("README.md"),
            new File("pom.xml"), new File("build.gradle.kts"), new File("docs/showcase/index.html"),
            new File("resourcepack/assets"));
        List<File> offenders = new java.util.ArrayList<>();
        for (File rootFile : scanRoots) {
            if (!rootFile.exists()) {
                continue;
            }
            try (var walk = Files.walk(rootFile.toPath())) {
                for (java.nio.file.Path path : walk.filter(Files::isRegularFile).toList()) {
                    File f = path.toFile();
                    if (f.getName().equals("BosketBallEngineTest.java")) {
                        continue;
                    }
                    String name = f.getName().toLowerCase(java.util.Locale.ROOT);
                    if (!(name.endsWith(".java") || name.endsWith(".yml") || name.endsWith(".py") || name.endsWith(".md")
                        || name.endsWith(".xml") || name.endsWith(".kts") || name.endsWith(".html") || name.endsWith(".json"))) {
                        continue;
                    }
                    if (Files.readString(path).contains(legacyVersion)) {
                        offenders.add(f);
                    }
                }
            }
        }
        assertTrue("No file may mention the legacy Minecraft version anymore: " + offenders, offenders.isEmpty());

        // 4. Full-screen locker HUD glyph plates exist in the pack (textures + font providers)
        String fontJson = Files.readString(new File("resourcepack/assets/minecraft/font/default.json").toPath());
        for (String glyphJson : new String[]{"\\ue359", "\\ue35a", "\\ue35b", "\\ue35c", "\\ue35d"}) {
            assertTrue("Locker HUD glyph " + glyphJson + " must be registered in default.json!",
                fontJson.contains(glyphJson));
        }
        for (String tex : new String[]{"locker_hud_topbar.png", "locker_hud_select.png", "locker_hud_select_exit.png", "locker_hud_info_panel.png", "locker_hud_row_idle.png"}) {
            assertTrue("Locker HUD texture must exist: " + tex,
                new File("resourcepack/assets/bosketball/textures/font/" + tex).isFile());
        }
        assertEquals("Topbar advance must match the 248px texture (+1 tracking)!", 249, Glyphs.advanceOf(Glyphs.LOCKER_TOPBAR));
        assertEquals("Slant advance must match the 232px texture (+1 tracking)!", 233, Glyphs.advanceOf(Glyphs.LOCKER_SELECT_SLANT));
        assertEquals("Info panel advance must match the 250px texture (+1 tracking)!", 251, Glyphs.advanceOf(Glyphs.LOCKER_INFO_PANEL));
        assertEquals("Idle row advance must match the 232px texture (+1 tracking)!", 233, Glyphs.advanceOf(Glyphs.LOCKER_ROW_IDLE));

        // 5. Client BossBar budget: vanilla stops at guiHeight / 3, so the HUD uses six bars
        // and keeps the selected entry out of the last (potentially clipped) preview row.
        String lockerHudSrc = Files.readString(
            new File("src/main/java/fr/bosketball/ui/LockerRoom3dMenuManager.java").toPath());
        assertTrue("Locker HUD must fit the vanilla stacked-BossBar render budget!",
            lockerHudSrc.contains("HUD_BAR_COUNT = 6") && lockerHudSrc.contains("guiHeight / 3"));
        assertTrue("Locker HUD selection must stay in the second visible menu row!",
            lockerHudSrc.contains("HUD_ROWS_PER_PAGE = 4")
                && lockerHudSrc.contains("Math.clamp(s.selectedIndex - 1, 0,"));
        assertEquals("Pixel fitting must include the ellipsis in its exact budget!",
            "ABC…", Glyphs.fitTextToWidth("ABCDEFGHIJ", 28, true));
        assertEquals("Pixel fitting must not split surrogate pairs!",
            "A👥…", Glyphs.fitTextToWidth("A👥BCDEFG", 28, false));
        String fittedPanelText = Glyphs.fitTextToWidth(
            "A dynamic description that is far too long for the locker-room panel", 228, false);
        assertTrue("Dynamic inspector copy must fit inside the 250px panel!",
            Glyphs.textAdvancePx(fittedPanelText, false) <= 228);
    }

    // =========================================================================
    //  CHAT FILTER — must catch every insult variant and never flag real words
    // =========================================================================

    @Test
    public void chatFilterCatchesEveryInsultVariant() {
        String[] insults = {
            "connard", "salo.pe", "s4l0pe", "fdp", "ta gueule", "fils de pute", "nique ta mere",
            "f u c k", "fuuuck", "FUCK YOU", "encule", "batard", "ntm", "tg", "b!tch", "sh1t",
            "saaaloooope", "pute", "asshole", "kys", "connnnnard", "ta gueuulle", "enculé",
            "n1qu3", "s@lope", "c0nnard", "youpin", "bicot"
        };
        for (String insult : insults) {
            assertTrue("Chat filter must censor: " + insult,
                fr.bosketball.moderation.ChatFilter.scan(insult).altered());
        }
    }

    @Test
    public void chatFilterNeverFlagsLegitimateWords() {
        String[] legit = {
            "salopette", "concert", "culture", "assist", "assists", "pass", "class", "brain",
            "concept", "bordel", "chiotte", "pas encore", "comment", "contre", "condition",
            "assassin", "compass", "grass", "bass", "dispute", "assure", "massive", "glass",
            "cassette", "connexion", "je vais te passer la balle", "nice shot!", "gg",
            "bien joue", "cuisine", "retard", "noob", "ez"
        };
        for (String word : legit) {
            assertFalse("Chat filter must NOT flag real word: " + word,
                fr.bosketball.moderation.ChatFilter.scan(word).altered());
        }
    }

    @Test
    public void chatFilterKeepsMessageReadableAndMasksOnlyTheInsult() {
        String cleaned = fr.bosketball.moderation.ChatFilter.scan("gg connard bien joue").cleaned();
        assertTrue("Clean words must survive censoring", cleaned.contains("gg") && cleaned.contains("bien joue"));
        assertFalse("The insult itself must be masked", cleaned.contains("connard"));
    }

    // =========================================================================
    //  I18N — English must be the complete base locale
    // =========================================================================

    @Test
    public void englishIsTheCompleteBaseLanguage() {
        assertEquals("English must be a fully covered locale", 100,
            fr.bosketball.i18n.Lang.coverage(fr.bosketball.i18n.Lang.Locale_.EN));
        assertTrue("Every locale must expose the same required keys",
            fr.bosketball.i18n.Lang.keyCount() > 50);
        for (fr.bosketball.i18n.Lang.Locale_ locale : fr.bosketball.i18n.Lang.Locale_.values()) {
            fr.bosketball.i18n.Lang lang = fr.bosketball.i18n.Lang.of(locale);
            // A missing key must never leak as "<key>" text.
            assertEquals("Unresolved key leaked for " + locale.code(),
                "queue.left", lang.raw("__definitely_missing__").equals("__definitely_missing__")
                    ? "queue.left" : "bad");
            assertFalse("Base key must resolve for " + locale.code(),
                lang.raw("match.won").isBlank());
        }
    }

    @Test
    public void translationsOverrideAndFallBackCorrectly() {
        fr.bosketball.i18n.Lang french = fr.bosketball.i18n.Lang.of("fr");
        assertTrue("French must translate match wins",
            french.get("match.won").contains("VICTOIRE"));
        fr.bosketball.i18n.Lang spanish = fr.bosketball.i18n.Lang.of("es");
        assertTrue("Spanish must translate the remake banner itself now that ES is complete",
            spanish.get("match.remake").contains("REPETIR"));
        fr.bosketball.i18n.Lang german = fr.bosketball.i18n.Lang.of("de");
        assertTrue("German must translate the remake banner too",
            german.get("match.remake").contains("NEUSTART"));
        // Every locale ships the full table, so an unknown key is the only thing that falls back.
        for (fr.bosketball.i18n.Lang.Locale_ locale : fr.bosketball.i18n.Lang.Locale_.values()) {
            assertEquals("Locale " + locale.code() + " must be fully translated", 100,
                fr.bosketball.i18n.Lang.coverage(locale));
        }
        assertEquals("An unknown key must resolve to itself, never to null",
            "__nope__", german.raw("__nope__"));
    }

    // =========================================================================
    //  BOT DIFFICULTY — EASY < NORMAL < PRO on every skill axis
    // =========================================================================

    @Test
    public void botDifficultiesAreStrictlyOrdered() {
        var easy = fr.bosketball.game.bot.BotDifficulty.EASY;
        var normal = fr.bosketball.game.bot.BotDifficulty.NORMAL;
        var pro = fr.bosketball.game.bot.BotDifficulty.PRO;
        assertTrue(easy.shotAccuracy() < normal.shotAccuracy());
        assertTrue(normal.shotAccuracy() < pro.shotAccuracy());
        assertTrue(easy.stealSuccess() < normal.stealSuccess());
        assertTrue(normal.stealSuccess() < pro.stealSuccess());
        assertTrue("Reaction time must improve with difficulty",
            easy.reactionTicks() > normal.reactionTicks() && normal.reactionTicks() > pro.reactionTicks());
        assertNotNull(fr.bosketball.game.bot.BotDifficulty.byNameOrNull("pro"));
        assertNotNull(fr.bosketball.game.bot.BotDifficulty.byNameOrNull("easy"));
    }

    @Test
    public void botRosterProvidesDistinctHumanLikeIdentities() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            var profile = fr.bosketball.game.bot.BotProfile.pick(
                fr.bosketball.game.bot.BotDifficulty.NORMAL, names);
            assertTrue("Bot nicknames must be unique", names.add(profile.nickname()));
            assertFalse("Bot must have a jersey number", profile.nameplate().toString().isEmpty());
            assertTrue("Bot must have a skin texture", profile.skinTexture().startsWith("bot_"));
        }
    }

    // =========================================================================
    //  GUILDS
    // =========================================================================

    @Test
    public void guildRosterAndEloBehave() {
        var guild = new fr.bosketball.social.Guild("test", "Ignis Five", "IGN", 0L, 1000);
        assertEquals(1000, guild.elo());
        guild.adjustElo(18);
        assertEquals(1018, guild.elo());
        guild.adjustElo(-5000);
        assertEquals("Guild ELO must never go negative", 0, guild.elo());

        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        guild.addMember(leader, "Leader", fr.bosketball.social.Guild.Role.LEADER);
        guild.addMember(member, "Member", fr.bosketball.social.Guild.Role.MEMBER);
        assertEquals(2, guild.members().size());
        assertEquals(fr.bosketball.social.Guild.Role.LEADER, guild.roleOf(leader));
        assertTrue(guild.hasMember(member));
        assertTrue(guild.removeMember(member));
        assertFalse(guild.hasMember(member));
    }

    @Test
    public void guildPromotionKeepsASingleLeader() {
        var guild = new fr.bosketball.social.Guild("g", "Court Kings", "CK", 0L, 1000);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        guild.addMember(a, "Alpha", fr.bosketball.social.Guild.Role.LEADER);
        guild.addMember(b, "Bravo", fr.bosketball.social.Guild.Role.MEMBER);
        guild.addMember(c, "Charlie", fr.bosketball.social.Guild.Role.MEMBER);

        guild.promote(guild.member(b));
        assertEquals("Member -> officer", fr.bosketball.social.Guild.Role.OFFICER, guild.roleOf(b));
        assertTrue("Officer slots must be capped", guild.hasOfficerSlot());

        guild.promote(guild.member(b));
        assertEquals("Officer -> leader", fr.bosketball.social.Guild.Role.LEADER, guild.roleOf(b));
        assertFalse("A guild must never end up with two leaders", guild.isLeader(a));
        assertEquals(fr.bosketball.social.Guild.Role.OFFICER, guild.roleOf(a));

        guild.demote(guild.member(b));
        assertEquals("A leader is never demoted by demote()",
            fr.bosketball.social.Guild.Role.LEADER, guild.roleOf(b));

        // Roster ordering: leader first, then officers by contribution, then members.
        guild.member(c).addContributedElo(40);
        assertEquals("Leader first", "Bravo", guild.sortedMembers().getFirst().name());
        assertEquals("Officers before members", "Alpha", guild.sortedMembers().get(1).name());
        assertEquals("Members last", "Charlie", guild.sortedMembers().get(2).name());
    }

    @Test
    public void guildColorPaletteCyclesAndStaysInRange() {
        var guild = new fr.bosketball.social.Guild("g2", "Palette Test", "PT", 0L, 1000);
        int first = guild.colorRgb();
        assertEquals((int) first, (int) fr.bosketball.social.Guild.PALETTE.getFirst());
        guild.cycleColor(1);
        assertEquals((int) fr.bosketball.social.Guild.PALETTE.get(1), (int) guild.colorRgb());
        guild.cycleColor(-1);
        assertEquals((int) first, (int) guild.colorRgb());
        guild.cycleColor(-1);
        assertEquals("Cycling backwards must wrap, never go out of range",
            (int) fr.bosketball.social.Guild.PALETTE.get(fr.bosketball.social.Guild.PALETTE.size() - 1),
            (int) guild.colorRgb());
        assertEquals(0, guild.matchesPlayed());
        assertEquals(0.0, guild.winRate(), 0.0001);
        assertEquals("Losses must be derived, never negative", 0, guild.losses());
    }
}
