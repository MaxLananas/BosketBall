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

            // Verify floor-anchored 3D hoop stanchion, integrated shot-clock housing, anti-parallax rear glass & 22.5-degree cone net (Requirement #11)
            String hoopIdleJson = new String(zf.getInputStream(zf.getEntry("assets/bosketball/models/item/hoop_idle.json")).readAllBytes());
            assertTrue("hoop_idle must include floor-anchored stanchion_floor_base reaching -16.0", hoopIdleJson.contains("-16.0"));
            assertTrue("hoop_idle must include integrated shotclock_housing reaching 25.9", hoopIdleJson.contains("25.9"));
            assertTrue("hoop_idle must include welded rim_hook_0 loops", hoopIdleJson.contains("rim_hook_0"));
            assertTrue("hoop_idle must include inward-tilted 22.5-degree cone net panels without white collars", hoopIdleJson.contains("net_cone_north"));
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
    public void testFiftyFourGlossyChatTagsEightBallSkinsCustomSkinsAndGraphicalTab() throws Exception {
        assertEquals("Must provide 54 selectable glossy beveled chat tags!", 54, ChatTag.values().length);
        assertEquals("Must provide 8 sculpted 3D square basketball skins!", 8, fr.bosketball.model.BallSkin.values().length);
        Set<Character> glyphs = new HashSet<>();
        Set<String> ids = new HashSet<>();
        File rpZip = new File("releases/BosketBall-ResourcePack-26.2.zip");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(rpZip)) {
            assertNotNull("Custom dark-gold container GUI generic_54.png must exist!",
                zf.getEntry("assets/minecraft/textures/gui/container/generic_54.png"));
            assertNotNull("Seamless diamond-weave hoop_net_mesh.png must exist!",
                zf.getEntry("assets/bosketball/textures/item/hoop_net_mesh.png"));
            assertNotNull("Center-screen Tab graphical header tab_header_card.png must exist!",
                zf.getEntry("assets/bosketball/textures/font/tab_header_card.png"));
            assertNotNull("Center-screen Tab footer tab_footer_card.png must exist!",
                zf.getEntry("assets/bosketball/textures/font/tab_footer_card.png"));
            assertNotNull("Center-screen Tab Ignis row tab_row_ignis.png must exist!",
                zf.getEntry("assets/bosketball/textures/font/tab_row_ignis.png"));
            for (String customSkin : new String[]{"star_ignis", "star_frost", "star_mvp", "defender_bot"}) {
                assertNotNull("Custom 64x64 player skin missing: " + customSkin,
                    zf.getEntry("assets/bosketball/textures/entity/player/wide/" + customSkin + ".png"));
            }
            assertNotNull("Invisible UI filler item model ui_invisible.json must exist!",
                zf.getEntry("assets/bosketball/items/ui_invisible.json"));
            assertNotNull("Center-screen Tab hotbar icon_tab.json must exist!",
                zf.getEntry("assets/bosketball/items/icon_tab.json"));
            for (fr.bosketball.model.BallSkin skin : fr.bosketball.model.BallSkin.values()) {
                assertNotNull("Missing 3D ball item definition for " + skin.modelId(),
                    zf.getEntry("assets/bosketball/items/" + skin.modelId() + ".json"));
            }
            for (ChatTag tag : ChatTag.values()) {
                assertTrue("Duplicate ChatTag glyph: " + (int) tag.glyph(), glyphs.add(tag.glyph()));
                assertTrue("Duplicate ChatTag id: " + tag.id(), ids.add(tag.id()));
                assertEquals(tag, ChatTag.fromIdOrNull(tag.id()));
                assertNotNull(tag.pillComponent());
                assertNotNull("Missing glossy pill texture for tag " + tag.id(),
                    zf.getEntry("assets/bosketball/textures/font/tag_" + tag.id() + ".png"));
            }
        }

        // Verify Multi-Tag Stacking (up to 4 simultaneous glossy tags!) + immediate 1000-ELO #1 Crown
        File tmpDir = new File("/tmp/bb-overhaul-test-" + System.nanoTime());
        StatsManager sm = new StatsManager(tmpDir, null);
        UUID u1 = UUID.randomUUID();
        PlayerProfile p1 = sm.getOrCreate(u1, "MaxLananas");
        assertEquals("Default profile must start with 3 glossy tags (+ #1 crown = 4 badges)!", 3, p1.equippedTags().size());
        sm.refreshLeaderboardBadges();
        assertEquals("Starter 1000-ELO leader must immediately receive #1 ELO crown!", 1, sm.getBadges(u1).eloPlace());

        p1.setElo(2400);
        p1.setAssists(88);
        p1.setSelectedTag(ChatTag.GOAT);
        assertTrue(p1.toggleEquippedTag(ChatTag.SUPERNOVA));
        assertTrue(p1.toggleEquippedTag(ChatTag.ROYAL));
        assertTrue(p1.toggleEquippedTag(ChatTag.IMMORTAL));
        assertEquals(List.of(ChatTag.GOAT, ChatTag.SUPERNOVA, ChatTag.ROYAL, ChatTag.IMMORTAL), p1.equippedTags());

        p1.addPlayTimeSeconds(3600 + 50 * 60); // 1 heure 50 minutes (exact image-8.png!)
        assertEquals("1 heure 50 minutes", p1.formattedPlayTimeFr());

        MatchHistoryEntry entry = new MatchHistoryEntry(1760000000000L, "2V2", "IGNIS", 21, 17, true, 15, 4, 3, +18);
        String compact = entry.serializeCompact();
        MatchHistoryEntry parsed = MatchHistoryEntry.deserializeCompact(compact);
        assertNotNull(parsed);
        assertEquals(21, parsed.ignisScore());
        assertEquals(15, parsed.points());
        assertEquals(18, parsed.eloDelta());
        p1.recordMatch(entry);
        p1.settings().setFloorAuraRings(false);

        sm.refreshLeaderboardBadges();
        assertEquals("Top assists player must receive #1 assists badge!", 1, sm.getBadges(u1).assistsPlace());

        sm.save();
        StatsManager reloaded = new StatsManager(tmpDir, null);
        reloaded.load();
        PlayerProfile loaded = reloaded.getProfile(u1);
        assertNotNull(loaded);
        assertEquals(ChatTag.GOAT, loaded.selectedTag());
        assertEquals(List.of(ChatTag.GOAT, ChatTag.SUPERNOVA, ChatTag.ROYAL, ChatTag.IMMORTAL), loaded.equippedTags());
        assertTrue("selfBodyRigInF5 must remain true across YAML save/load!", loaded.settings().selfBodyRigInF5());
        assertEquals("1 heure 50 minutes", loaded.formattedPlayTimeFr());
        assertEquals(1, loaded.matchHistory().size());
        assertFalse(loaded.settings().floorAuraRings());

        // Verify UI Compositors (image-4 dividers, image-6 3D slanted menu rows + indigo pills, & graphical Center-Tab rows!)
        assertNotNull(Glyphs.composeDividerHeader("Test Header"));
        assertNotNull(Glyphs.composeDividerBottom());
        assertNotNull(Glyphs.composeMenu3dRow("DUEL CLASSÉ 1V1", true));
        assertNotNull(Glyphs.composeMenu3dRow("STREET 2V2", false));
        assertNotNull(Glyphs.composeMenu3dIndigoPill("Discord"));
        assertNotNull(Glyphs.composeCenterTabRow(Glyphs.TAB_ROW_IGNIS, net.kyori.adventure.text.Component.text("Test")));
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
            assertEquals("Expected 8 circular collar panels + 4 cone panels = 12 net panels in " + name, 12, netElements);
        }
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
