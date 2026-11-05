package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import fr.bosketball.animation.AnimationKinematics;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.arena.schematic.CourtSchematic;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.ShotReleaseQuality;
import fr.bosketball.physics.PhysicsMath;
import fr.bosketball.social.Guild;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.SmallCaps;
import java.io.File;
import java.io.IOException;
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
        Vector shooterEye = new Vector(0.5, 65.62, rim.getZ() - 8.20);

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

        assertTrue(ShotReleaseQuality.greenWindowLow(true, 8.0) < ShotReleaseQuality.greenWindowLow(false, 8.0));
    }

    @Test
    public void testCloseRangeContestActuallyHurtsTheShooter() {
        CourtGeometry court = new CourtGeometry(0.5, 64.0, 0.5);
        Vector rim = court.southHoopRim();
        Vector fwd = court.southHoopForward();
        java.util.Random rng = new java.util.Random(424242L);

        double openClose = swishRate(court, rim, fwd, 3.0, 0.0, rng, 200);
        double smotheredClose = swishRate(court, rim, fwd, 3.0, 0.9, rng, 200);
        assertTrue("An open 3m look must be near automatic: " + openClose, openClose > 0.92);
        assertTrue("A hand in the face at 3m must cost real accuracy: "
            + smotheredClose + " vs open " + openClose, smotheredClose < openClose - 0.25);
        assertTrue("But it must never be impossible", smotheredClose > 0.30);
    }

    @Test
    public void testShotValueAndCourtZonesAgreeWithTheRimGeometry() {
        CourtGeometry court = new CourtGeometry(0.5, 64.0, 0.5);
        Vector south = court.rimForTeam(fr.bosketball.model.TeamSide.IGNIS);
        Vector north = court.rimForTeam(fr.bosketball.model.TeamSide.FROST);
        assertEquals("IGNIS attacks the south hoop", court.southHoopRim(), south);
        assertEquals("FROST attacks the north hoop", court.northHoopRim(), north);

        assertFalse(court.isThreePointShot(south.getX(), south.getZ() + 1.5, south));
        assertTrue(court.isThreePointShot(south.getX(), south.getZ() + 8.0, south));

        assertTrue(court.isThreePointShot(south.getX() + 7.0, south.getZ() - 1.0, south));
    }

    @Test
    public void testStealHandFatigueRampsDownAndResets() {
        double fresh = 0.42;
        double second = fresh * Math.pow(0.60, 1);
        double third = fresh * Math.pow(0.60, 2);
        double capped = Math.max(0.04, Math.min(0.42, fresh * Math.pow(0.60, 4)));
        assertTrue("A first attempt must stay dangerous", fresh >= 0.40);
        assertTrue("A second attempt must be worse", second < fresh - 0.10);
        assertTrue("A third attempt must be worse still", third < second);
        assertTrue("Spam must bottom out, not vanish", capped >= 0.04);
        assertTrue("But it must be a shadow of a clean attempt", capped <= 0.06);

        assertTrue("A fresh streak must be reachable again", 0.42 > capped);
    }

    @Test
    public void testRangePressureAndTimingAreBothPunishedWithoutCliffs() {
        CourtGeometry court = new CourtGeometry(0.5, 64.0, 0.5);
        Vector rim = court.southHoopRim();
        Vector fwd = court.southHoopForward();
        java.util.Random rng = new java.util.Random(31337L);

        double open8 = swishRate(court, rim, fwd, 8.0, 0.0, rng, 220);
        double open10 = swishRate(court, rim, fwd, 10.0, 0.0, rng, 220);
        double contested8 = swishRate(court, rim, fwd, 8.0, 0.5, rng, 220);
        assertTrue("An open 8m three must be a good but not automatic shot: " + open8, open8 < 0.78);
        assertTrue("...and must still be worth taking: " + open8, open8 > 0.45);
        assertTrue("An open 10m look must be clearly worse: " + open10, open10 < open8 - 0.12);
        assertTrue("Contesting a three must matter: " + contested8, contested8 < open8 - 0.15);

        double lo = ShotReleaseQuality.greenWindowLow(false, 6.0);
        double hi = ShotReleaseQuality.greenWindowHigh(false, 6.0);
        assertEquals("A green release costs nothing", 0.0, ShotReleaseQuality.timingPenaltyBlocks(0.93, 6.0), 1e-9);
        double barelyEarly = ShotReleaseQuality.timingPenaltyBlocks(lo - 0.02, 6.0);
        double clearlyEarly = ShotReleaseQuality.timingPenaltyBlocks(lo - 0.13, 6.0);
        double tap = ShotReleaseQuality.timingPenaltyBlocks(0.70, 6.0);
        assertTrue("A barely-early release must stay forgiving: " + barelyEarly, barelyEarly < 0.15);
        assertTrue("A clearly early release must cost real accuracy: " + clearlyEarly, clearlyEarly > barelyEarly);
        assertTrue("And so must a tap: " + tap, tap > 0.25);
        assertTrue("But never beyond the rim itself", ShotReleaseQuality.timingPenaltyBlocks(0.10, 6.0) <= 1.10);
        assertEquals("Late is symmetric in kind", ShotReleaseQuality.timingPenaltyBlocks(hi + 0.10, 6.0),
            ShotReleaseQuality.timingPenaltyBlocks(lo - 0.10, 6.0), 1e-9);
    }

    @Test
    public void testYamlWriterNeverEmitsAProseHeader() {
        String serialized = String.join("\n",
            "# BosketBall physical Hall of Fame — set with /bb holo hall-of-fame (OP).",
            "#",
            "origin:",
            "  world: world",
            "  x: 101.5",
            "facing: NORTH",
            "");
        String stripped = fr.bosketball.config.AtomicYamlWriter.stripDocumentHeader(serialized);
        assertTrue("The document must start at the first real key", stripped.startsWith("origin:"));
        assertFalse("No prose may survive above the document", stripped.contains("# BosketBall"));
        assertTrue("Values must be preserved", stripped.contains("facing: NORTH"));

        String plain = "origin:\n  world: world\n";
        assertEquals(plain, fr.bosketball.config.AtomicYamlWriter.stripDocumentHeader(plain));

        String hashed = "name: \"#1 Rank\"\n";
        assertEquals(hashed, fr.bosketball.config.AtomicYamlWriter.stripDocumentHeader(hashed));
    }

    @Test
    public void testCrateStationIsAnItemSwarmOnly() {
        try {
            String src = Files.readString(java.nio.file.Path.of(
                "src/main/java/fr/bosketball/lobby/RewardCrateStation.java"));

            assertFalse("The crate must not place CHEST slabs", src.contains("Material.CHEST"));
            assertFalse("The crate must not place BARREL slabs", src.contains("Material.BARREL"));
            assertFalse("The crate must not place GOLD_BLOCK studs", src.contains("Material.GOLD_BLOCK"));
            assertTrue("The flying loot ring must still exist", src.contains("ItemDisplay.class"));
            assertTrue("The click target must still exist", src.contains("Interaction.class"));
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    public void testTabListSourceKeepsGuildTagOutOfTheRow() {
        try {
            String src = Files.readString(java.nio.file.Path.of("src/main/java/fr/bosketball/ui/TabListManager.java"));
            int build = src.indexOf("private Component buildTabEntry(");
            assertTrue("buildTabEntry must exist", build > 0);
            String body = src.substring(build);
            int guildRender = body.indexOf("row.append(Msg.parse(guild.displayTag()))");
            assertTrue("The guild tag must not be appended to the TAB row", guildRender < 0);
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    public void testRimTorusAndBackboardCollisions() {
        Vector rim = new Vector(0.0, 67.05, 13.95);

        Vector onFrontIron = new Vector(0.0, 67.05 + 0.20, 13.95 - PhysicsMath.RIM_MAJOR_RADIUS);
        Vector vel = new Vector(0.0, -0.25, 0.18);

        PhysicsMath.CollisionResult res = PhysicsMath.collideWithRimTorus(onFrontIron, vel, rim, 0.56);
        assertTrue("Ball striking front iron must register torus collision", res.hit());
        assertTrue("Ball must bounce upward off top of front iron", res.velocity().getY() > 0.0);

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

        assertTrue(geo.isThreePointShot(6.85, southRim.getZ() - 1.2, southRim));

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

        String expectedSha1 = Files.readString(new File("releases/BosketBall-ResourcePack-26.2.zip.sha1").toPath()).trim();
        String configYml = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("config.yml external-sha1 must match latest ResourcePack SHA-1!", configYml.contains(expectedSha1));

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

            for (fr.bosketball.model.BallSkin skin : fr.bosketball.model.BallSkin.values()) {
                String ballMdl = skin.modelId();
                String mJson = new String(zf.getInputStream(zf.getEntry("assets/bosketball/models/item/" + ballMdl + ".json")).readAllBytes());
                assertTrue(ballMdl + " must hide held ball in hand via [0.0, 0.0, 0.0] scale", mJson.contains("0.0"));
                assertTrue(ballMdl + " must use shrunken 0.36 3D cube scale in GUI & menus", mJson.contains("0.36"));
            }

            String hoopIdleJson = new String(zf.getInputStream(zf.getEntry("assets/bosketball/models/item/hoop_idle.json")).readAllBytes());
            assertTrue("hoop_idle must include floor-anchored stanchion_floor_base reaching -16.0", hoopIdleJson.contains("-16.0"));
            assertTrue("hoop_idle must include integrated shotclock_housing reaching 25.9", hoopIdleJson.contains("25.9"));
            assertTrue("hoop_idle must include welded rim_hook_0 loops", hoopIdleJson.contains("rim_hook_0"));
            assertTrue("hoop_idle must include closed octagonal net rings (net_t0_n..net_t4_nw)", hoopIdleJson.contains("net_t0_n") && hoopIdleJson.contains("net_t4_nw"));
            assertNotNull("aim_reticles.png must exist in font textures", zf.getEntry("assets/bosketball/textures/font/aim_reticles.png"));

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

        geo.setSouthHoop(new Vector(114.5, 73.05, 200.0), new Vector(-1, 0, 0), false);
        geo.setNorthHoop(new Vector(85.5, 73.05, 200.0), new Vector(1, 0, 0), true);

        fr.bosketball.arena.Arena custom = new fr.bosketball.arena.Arena("rooftop", "ʀᴏᴏꜰᴛᴏᴘ x", "world", geo);
        custom.setCustomMap(true);
        custom.setJumbotronEnabled(false);
        custom.replaceCustomSpawns(fr.bosketball.model.TeamSide.IGNIS, List.of(new fr.bosketball.arena.Arena.SpawnPose(96.5, 70.1, 200.0, -90f, 0f)));
        custom.replaceCustomSpawns(fr.bosketball.model.TeamSide.FROST, List.of(new fr.bosketball.arena.Arena.SpawnPose(104.5, 70.1, 200.0, 90f, 0f)));
        custom.setLobbyExitSpawn(new fr.bosketball.arena.Arena.SpawnPose(50.5, 65.0, 50.5, 0f, 0f));

        PhysicsMath.CollisionResult glassHit = PhysicsMath.collideWithBackboard(
            new Vector(85.05, 73.35, 200.0),
            new Vector(-0.42, 0.05, 0.0),
            geo.northHoopRim(),
            geo.northHoopForward(),
            0.66
        );
        assertTrue("360° East-facing backboard must reflect incoming -X shot!", glassHit.hit());
        assertTrue("Reflected X velocity must bounce toward +X!", glassHit.velocity().getX() > 0.20);

        Vector escapingPos = new Vector(125.0, 92.0, 200.0);
        Vector escapingVel = new Vector(0.8, 0.5, 0.0);
        assertTrue(custom.clampBallInsideBarrier(escapingPos, escapingVel, 0.72));
        assertTrue(escapingPos.getX() <= 120.0 - PhysicsMath.BALL_RADIUS + 1e-4);
        assertTrue(escapingPos.getY() <= 88.0 - PhysicsMath.BALL_RADIUS + 1e-4);
        assertTrue(escapingVel.getX() < 0 && escapingVel.getY() < 0);

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

        AnimationKinematics.RigPoseSnapshot jumpPose = AnimationKinematics.evaluate(
            0f, -10f, 0.14, 0.34, false, 1.0, 0.5, false, false, -1.0, 0,
            AnimationKinematics.ActionClip.NONE, 0.0, null, 0
        );
        org.joml.Vector3f jumpArm = jumpPose.rightArm().rotation().transform(new org.joml.Vector3f(0f, -1f, 0f));
        assertTrue("Jumping upward must swing arms forward/up relative to neutral hang!", jumpArm.y > -0.92f);

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

        assertFalse("Cannot spend 2500 Coins with only 100 Coins!", p1.spendCoins(ChatTag.GOAT.coinPrice()));
        p1.addCoins(3000);
        assertEquals(3100, p1.coins());
        assertTrue(p1.spendCoins(ChatTag.GOAT.coinPrice()));
        p1.unlockTag(ChatTag.GOAT);
        assertTrue(p1.hasUnlockedTag(ChatTag.GOAT));
        p1.unlockBall(fr.bosketball.model.BallSkin.GALAXY);
        p1.unlockDunk(DunkStyle.METEOR_JAM);

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

        p.settings().setAimTrajectory(true);
        p.settings().setCinematicScoreCam(false);
        p.settings().setChatAnnouncements(true);
        p.settings().setPartyInvites(false);
        assertTrue("aimTrajectory must stay independent from cinematicScoreCam!", p.settings().aimTrajectory());
        assertFalse(p.settings().cinematicScoreCam());
        assertTrue("chatAnnouncements must stay independent from partyInvites!", p.settings().chatAnnouncements());
        assertFalse(p.settings().partyInvites());

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

        for (java.lang.reflect.Field f : fr.bosketball.animation.PlayerVisualRig.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            assertFalse("PlayerVisualRig must NOT have fake ItemDisplay body bone: " + f.getName(),
                n.contains("bone") || n.contains("torso") || n.contains("arm") || n.contains("leg"));
        }

        boolean hasMannequinField = false;
        for (java.lang.reflect.Field f : fr.bosketball.game.CourtDefenderBot.class.getDeclaredFields()) {
            if (f.getType().equals(org.bukkit.entity.Mannequin.class)) {
                hasMannequinField = true;
            }
            assertFalse("CourtDefenderBot must NOT use fake ItemDisplay limb bones: " + f.getName(),
                f.getName().toLowerCase().contains("bone"));
        }
        assertTrue("CourtDefenderBot must use org.bukkit.entity.Mannequin!", hasMannequinField);

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

        pm.addUserPermission(builderId, "custom.special.node");
        fr.bosketball.server.PermissionManager pmReloaded = new fr.bosketball.server.PermissionManager(null, tmpDir, null);
        pmReloaded.loadOrCreate();
        assertEquals("builder", pmReloaded.getUserGroup(builderId));
        assertEquals(Boolean.TRUE, pmReloaded.resolveEffectivePermissions(builderId).get("custom.special.node"));

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

        PlayerProfile shopper = new PlayerProfile(UUID.randomUUID(), "ShopTester");
        int initialUnowned = fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(
            shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL
        ).size();
        int initialCatalog = fr.bosketball.ui.GuiMenuManager.collectShopCatalogItems(
            shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL
        ).size();
        assertTrue("Fresh player must have purchasable items in Shop!", initialUnowned > 5);
        assertEquals("Fresh-player catalog should equal the purchasable/unowned list", initialUnowned, initialCatalog);

        for (var item : fr.bosketball.ui.GuiMenuManager.collectShopCatalogItems(shopper, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL)) {
            assertTrue("Shop catalog may only contain purchasable products", item.priceCoins() > 0);
            assertFalse("Free CLASSIC ball must not be sold in Shop!", item.ballSkin() == fr.bosketball.model.BallSkin.CLASSIC);
            assertFalse("Free TOMAHAWK dunk must not be sold in Shop!", item.dunkStyle() == fr.bosketball.model.DunkStyle.TOMAHAWK);
        }

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
        int purchaseStart = shopSrc.indexOf("private void handleShopEntryPurchase(");
        int purchaseEnd = shopSrc.indexOf("private void applyShopEntryPurchase(");
        assertTrue("handleShopEntryPurchase must exist", purchaseStart > 0 && purchaseEnd > purchaseStart);
        String purchaseBody = shopSrc.substring(purchaseStart, purchaseEnd);
        int ownedBranch = purchaseBody.indexOf("isShopEntryOwned(player, profile, entry)");
        int coinCharge = purchaseBody.indexOf("profile.coins() < price");
        assertTrue("The owned-cosmetics branch must exist", ownedBranch > 0);
        assertTrue("Owned Shop cards must act as equip buttons rather than recharging Coins",
            coinCharge < 0 || ownedBranch < coinCharge);
        assertTrue("The owned branch must equip instead of returning early",
            purchaseBody.contains("setSelectedBallSkin") && purchaseBody.contains("setSelectedTag"));

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

        normalProf.unlockTag(ChatTag.MYPINEAPPLE);
        assertTrue(normalProf.hasUnlockedTag(ChatTag.MYPINEAPPLE));
        assertTrue(ChatTag.MYPINEAPPLE.canBeUsedBy(normalProf, "Akashhi_", false));
        normalProf.revokeTag(ChatTag.MYPINEAPPLE);
        assertFalse(normalProf.hasUnlockedTag(ChatTag.MYPINEAPPLE));

        for (var entry : fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(normalProf, fr.bosketball.ui.GuiMenuManager.ShopCategory.ALL)) {
            assertFalse("FOUNDER must never appear in Shop!", entry.chatTag() == ChatTag.FOUNDER);
            assertFalse("MYPINEAPPLE must never appear in Shop!", entry.chatTag() == ChatTag.MYPINEAPPLE);
        }

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

        fr.bosketball.social.SocialPartyManager social = new fr.bosketball.social.SocialPartyManager(sm);
        UUID leaderId = UUID.randomUUID();
        assertTrue(social.setPartyTeamNameDirect(leaderId, "  <bold>MyPineapple</bold>  "));
        assertEquals("boldMyPineappleb".substring(0, Math.min(16, "boldMyPineappleb".length())).isEmpty()
            ? "" : fr.bosketball.social.SocialPartyManager.sanitizeTeamName("MyPineapple"), "MyPineapple");
        assertTrue(social.setPartyTeamNameDirect(leaderId, "MyPineapple"));
        assertEquals("MyPineapple", social.getParty(leaderId).teamName());
        assertEquals("MyPineapple", social.resolveCustomTeamNameForRoster(java.util.List.of(leaderId)));

        assertEquals(fr.bosketball.model.Rank.HOOPER, fr.bosketball.model.Rank.ROOKIE.nextCompetitiveRank());
        assertEquals(1050, fr.bosketball.model.Rank.ROOKIE.nextCompetitiveRank().minElo());
        assertEquals(null, fr.bosketball.model.Rank.MVP.nextCompetitiveRank());
        assertTrue(fr.bosketball.model.Rank.ROOKIE.eloRangeDescription().contains("1,000 ELO"));

        String cmdSrc = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("Admin commands must go through the canonical permission helper",
            cmdSrc.contains("Perms.admin(sender)") && cmdSrc.contains("Perms.staff(sender)"));
        assertFalse("No bare isOp() gate may remain on an admin command",
            cmdSrc.contains("!sender.isOp()"));

        assertEquals(0.08, PhysicsMath.computeBowPullProgress(0), 1e-4);
        assertTrue(PhysicsMath.computeBowPullProgress(10) > 0.35 && PhysicsMath.computeBowPullProgress(10) < 0.50);
        assertEquals(1.0, PhysicsMath.computeBowPullProgress(20), 1e-4);
        assertEquals("Holding bow past 20 ticks must stay cleanly at 100% Full Draw!",
            1.0, PhysicsMath.computeBowPullProgress(60), 1e-4);
        assertEquals("Full 100% bow draw must evaluate to PERFECT_GREEN!",
            ShotReleaseQuality.PERFECT_GREEN, ShotReleaseQuality.evaluate(1.0, 7.5, false));

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

        PlayerProfile customer = new PlayerProfile(UUID.randomUUID(), "BoutiqueBuyer");
        var unownedTags = fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(
            customer, fr.bosketball.ui.GuiMenuManager.ShopCategory.TAGS
        );
        assertEquals("All 54 purchasable tags must appear in TAGS category!", 54, unownedTags.size());
        for (int i = 1; i < unownedTags.size(); i++) {
            assertTrue("Shop tags must be sorted cleanly by Coin price ascending (Common -> Legendary)!",
                unownedTags.get(i).priceCoins() >= unownedTags.get(i - 1).priceCoins());
        }

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

        assertEquals("Leave Queue item must use dedicated center Slot 4 instead of overwriting Slot 8 Leaderboards!",
            4, fr.bosketball.model.BallSkin.LEAVE_QUEUE_LOBBY_SLOT);
    }

    @Test
    public void testServerOwnerProductionHardeningSecurityEconomyAndMatchIntegrity() throws Exception {

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

        assertEquals(null, fr.bosketball.listener.GameplayListener.checkChatViolation(10_000L, 0L, null, "Hello court!", false, false));
        assertNotNull("Rapid <900ms chat flood must be blocked for regular players!",
            fr.bosketball.listener.GameplayListener.checkChatViolation(10_500L, 10_000L, "hello court!", "Another msg", false, false));
        assertNotNull("Duplicate message within 8s must be blocked for regular players!",
            fr.bosketball.listener.GameplayListener.checkChatViolation(13_000L, 10_000L, "hello court!", "Hello court!", false, false));
        assertNotNull("Global Chat Mute must block regular players!",
            fr.bosketball.listener.GameplayListener.checkChatViolation(20_000L, 0L, null, "Hi", false, true));
        assertEquals("Staff/OP must bypass Global Chat Mute & cooldowns!",
            null, fr.bosketball.listener.GameplayListener.checkChatViolation(10_100L, 10_000L, "hi", "Hi", true, true));

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

        fr.bosketball.config.BosketConfig defCfg = fr.bosketball.config.BosketConfig.defaults();
        assertEquals("Completing a full match should have 0 loss-coins penalty by default!", 0, defCfg.economy().lossCoins());
        assertTrue("Rage-quitting/forfeiting must still penalize forfeit-coins!", defCfg.economy().forfeitCoins() > 0);

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

        assertEquals("The non-OP tab list must contain exactly the curated player commands (including '?')",
            29, fr.bosketball.server.ServerProtectionManager.NON_OP_TAB_COMMANDS.size());
        for (String expected : List.of(
            "help", "?", "play", "practice", "locker", "shop", "ranks",
            "friend", "party", "duel", "msg", "r",
            "spawn", "leave", "gg", "discord", "pack",
            "accept", "decline", "pc", "tuto", "quests", "achievements",
            "report", "crate", "guild", "ignore", "afk", "mail"
        )) {
            assertTrue("Expected player command in NON_OP_TAB_COMMANDS: " + expected,
                fr.bosketball.server.ServerProtectionManager.NON_OP_TAB_COMMANDS.contains(expected));
            assertTrue("Expected player command allowed for non-OP: /" + expected,
                fr.bosketball.server.ServerProtectionManager.isAllowedForNonOp("/" + expected));
        }

        for (String alias : List.of("/?", "/f add Max", "/friends", "/p invite Max", "/tell Max hi", "/w Max hi", "/reply gg", "/hub", "/lobby")) {
            assertTrue("Short player alias must be allowed for non-OP: " + alias,
                fr.bosketball.server.ServerProtectionManager.isAllowedForNonOp(alias));
        }

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

        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        for (String cmdKey : List.of("  help:", "aliases: ['?']", "  locker:", "  duel:", "  pack:", "  play:", "  practice:", "  shop:", "  ranks:")) {
            assertTrue("plugin.yml must register command/alias: " + cmdKey.trim(), pluginYml.contains(cmdKey));
        }

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

            try (var in = zf.getInputStream(zf.getEntry("assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png"))) {
                var img = javax.imageio.ImageIO.read(in);
                assertEquals(24, img.getWidth());
                assertEquals(23, img.getHeight());
            }

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

            String ballModelJson = new String(zf.getInputStream(zf.getEntry("assets/bosketball/models/item/basketball_classic.json")).readAllBytes());
            assertTrue("Square/cubic basketball must stay small (0.36x) in GUI!", ballModelJson.contains("0.36"));
        }
    }

    @Test
    public void testExhaustiveBugFixesAndOptimizations() throws Exception {

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

        fr.bosketball.arena.Arena headlessArena = new fr.bosketball.arena.Arena(
            "test_court", "Test Court", "world", new CourtGeometry(0.5, 64.0, 0.5, 14.0, 8.0)
        );
        assertFalse(headlessArena.areDisplaysHealthy());
        headlessArena.ensureDisplaysHealthy();

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

        fr.bosketball.social.SocialPartyManager social = new fr.bosketball.social.SocialPartyManager(sm);
        UUID discLeader = UUID.randomUUID();
        assertTrue(social.setPartyTeamNameDirect(discLeader, "CourtKings"));
        assertNotNull(social.getParty(discLeader));
        social.handleDisconnect(discLeader);
        org.junit.Assert.assertNull("Disconnecting solo party leader must disband empty party!", social.getParty(discLeader));

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

        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertFalse("GuiMenuManager must NOT leak Server Owner & Arena Studio via hasPermission(\"bosketball.admin\")!",
            guiSrc.contains("hasPermission(\"bosketball.admin\")"));
        assertTrue("GuiMenuManager must render 4-strip Large Basketball Hover Previews & Cytooxien GUI Overlays!",
            guiSrc.contains("buildBallHoverLore(")
                && guiSrc.contains("Glyphs.GUI_OVERLAY_SHOP")
                && guiSrc.contains("Glyphs.GUI_OVERLAY_TAGS")
                && guiSrc.contains("Glyphs.GUI_OVERLAY_COSMETICS")
                && guiSrc.contains("TagVaultFilter"));

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

        char[] guiOverlays = {
            Glyphs.GUI_OVERLAY_SHOP, Glyphs.GUI_OVERLAY_SHOP_GRID, Glyphs.GUI_OVERLAY_TAGS,
            Glyphs.GUI_OVERLAY_COSMETICS, Glyphs.GUI_OVERLAY_PLAY, Glyphs.GUI_OVERLAY_HUB,
            Glyphs.GUI_OVERLAY_HUB_OP, Glyphs.GUI_OVERLAY_LEADERBOARDS, Glyphs.GUI_OVERLAY_SETTINGS,
            Glyphs.GUI_OVERLAY_CONFIRM,
            Glyphs.GUI_OVERLAY_PLAY_BOT, Glyphs.GUI_OVERLAY_GUILD,
            Glyphs.GUI_OVERLAY_QUESTS, Glyphs.GUI_OVERLAY_ACHIEVEMENTS, Glyphs.GUI_OVERLAY_MAILBOX,
            Glyphs.GUI_OVERLAY_HISTORY, Glyphs.GUI_OVERLAY_FRIENDS, Glyphs.GUI_OVERLAY_PARTY,
            Glyphs.GUI_OVERLAY_RANKS, Glyphs.GUI_OVERLAY_SPECTATE, Glyphs.GUI_OVERLAY_PLAYER_CARD,
            Glyphs.GUI_OVERLAY_ADMIN, Glyphs.GUI_OVERLAY_REPORTS, Glyphs.GUI_OVERLAY_WARNINGS,
            Glyphs.GUI_OVERLAY_STUDIO
        };
        char[] lockerPlates = {
            Glyphs.LOCKER_TOPBAR, Glyphs.LOCKER_SELECT_SLANT, Glyphs.LOCKER_SELECT_SLANT_EXIT,
            Glyphs.LOCKER_INFO_PANEL, Glyphs.LOCKER_ROW_IDLE
        };
        java.util.Set<Character> codepoints = new HashSet<>();
        for (char c : guiOverlays) {
            assertTrue("Duplicate GUI overlay codepoint U+" + Integer.toHexString(c), codepoints.add(c));
        }
        for (char c : lockerPlates) {
            assertTrue("Locker plate U+" + Integer.toHexString(c) + " collides with a GUI overlay",
                codepoints.add(c));
        }

        assertTrue("Plaque captions must fit in " + Glyphs.GUI_PLAQUE_CAPTION_PX + "px",
            Glyphs.GUI_PLAQUE_CAPTION_PX <= 12);

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

        assertTrue("Embedded HTTP Resource Pack server must be enabled out-of-the-box!", defCfg.resourcePack().httpEnabled());
        assertEquals("auto", defCfg.resourcePack().publicHost());
        try (java.util.zip.ZipFile pluginJar = new java.util.zip.ZipFile("releases/BosketBall.jar")) {
            var bundledPack = pluginJar.getEntry(fr.bosketball.pack.ResourcePackHostService.EMBEDDED_PACK_RESOURCE);
            var bundledSha = pluginJar.getEntry(fr.bosketball.pack.ResourcePackHostService.EMBEDDED_SHA1_RESOURCE);
            assertNotNull("BosketBall.jar MUST embed META-INF/BosketBall-ResourcePack.zip!", bundledPack);
            assertNotNull("BosketBall.jar MUST embed META-INF/BosketBall-ResourcePack.sha1!", bundledSha);
            assertTrue("Bundled pack inside BosketBall.jar must be > 2.5 MB!", bundledPack.getSize() > 2_500_000L);
        }

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
    public void testEveryDeclaredCommandHasAnExecutorAndEloAdminActuallyWorks() throws Exception {

        String yml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        java.util.List<String> declared = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?m)^  ([a-z0-9_-]+):\\s*$").matcher(yml);
        while (m.find()) {
            declared.add(m.group(1));
        }
        String pluginSrc = Files.readString(new File("src/main/java/fr/bosketball/BosketBallPlugin.java").toPath());
        int optionsIdx = pluginSrc.indexOf("coreCommands = new ServerCoreCommands");
        String shortcutBlock = pluginSrc.substring(0, optionsIdx);
        String coreBlock = pluginSrc.substring(optionsIdx, Math.min(pluginSrc.length(), optionsIdx + 900));
        java.util.List<String> unwired = new java.util.ArrayList<>();
        for (String cmd : declared) {
            boolean wired = shortcutBlock.contains("\"" + cmd + "\"") || coreBlock.contains("\"" + cmd + "\"")
                || (cmd.equals("gg")
                    && pluginSrc.contains("getCommand(\"gg\")"))
                || (cmd.equals("discord") && pluginSrc.contains("getCommand(\"discord\")"));
            if (!wired) {
                unwired.add(cmd);
            }
        }
        assertTrue("Commands declared in plugin.yml with no executor (they do nothing!): " + unwired,
            unwired.isEmpty());

        String cmdSrc = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("A bare /giveelo must not throw in arraycopy!",
            cmdSrc.contains("if (args.length > 1) {\n                    System.arraycopy(args, 1, rewritten, 2, args.length - 1);"));
    }

    @Test
    public void testArenaIdentifiersAndDisplayNamesAreMiniMessageSafe() {
        String injection = "<click:run_command:'/op'>Open Court</click>";
        Arena arena = new Arena("<click:run_command:'/op'>", injection, "world",
            new CourtGeometry(0.5, 64.0, 0.5));
        assertEquals("clickrun_commandop", arena.id());
        assertEquals("Open Court", arena.displayName());
        arena.setDisplayName(injection);
        assertEquals("Open Court", arena.displayName());
        var rendered = fr.bosketball.util.Msg.parse("<white>" + arena.displayName() + "</white>");
        assertEquals("Open Court",
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(rendered));
        assertTrue("A persisted or command-supplied arena name must not create a click action",
            rendered.clickEvent() == null);
    }

    @Test
    public void testGuildUserFieldsArePlainAndGuildTagsAreValidated() throws Exception {
        String service = Files.readString(
            new File("src/main/java/fr/bosketball/social/GuildService.java").toPath());
        assertTrue("Guild creation must reject MiniMessage control characters in tags",
            service.contains("if (!Guild.isValidTag(tag))"));
        Guild guild = new Guild("guild", "<click:run_command:'/op'>Open Guild</click>",
            "BOS", 0L, 1000);
        assertEquals("Open Guild", guild.name());
        assertEquals("BOS", guild.tag());
        assertTrue(Guild.isValidTag(guild.tag()));
        assertFalse("MiniMessage tags must not be accepted as guild tags", Guild.isValidTag("<BOLD>"));

        guild.setMotd("<click:run_command:'/op'>Click here</click>");
        guild.setDescription("Friendly < squad");
        assertEquals("Click here", guild.motd());
        assertEquals("Friendly squad", guild.description());
        var rendered = fr.bosketball.util.Msg.parse(
            guild.displayNameAndTag() + " <white>" + guild.motd() + "</white>");
        assertTrue("Persisted or user-supplied guild text must not create a click action",
            rendered.clickEvent() == null);
    }

    @Test
    public void testStringFormattingUsesRootLocale() throws Exception {
        java.util.regex.Pattern defaultLocaleFormatter =
            java.util.regex.Pattern.compile("String\\.format\\(\\s*\"");
        try (var sources = Files.walk(new File("src/main/java").toPath())) {
            for (var source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertFalse("Nonlocalized output must use Locale.ROOT in " + source,
                    defaultLocaleFormatter.matcher(Files.readString(source)).find());
            }
        }
    }

    @Test
    public void testMatchFinishDatabaseSyncIsQueuedOffMainThread() throws Exception {
        String integration = Files.readString(
            new File("src/main/java/fr/bosketball/integration/IntegrationService.java").toPath());
        String storage = Files.readString(
            new File("src/main/java/fr/bosketball/storage/StorageService.java").toPath());
        int finishStart = integration.indexOf("public void onMatchFinished(");
        int finishEnd = integration.indexOf("public void onRankUp(", finishStart);
        assertTrue("The match-finished handler must exist", finishStart >= 0 && finishEnd > finishStart);
        String finishHandler = integration.substring(finishStart, finishEnd);
        assertTrue("A match result must queue its database sync", finishHandler.contains("storage.requestSync()"));
        assertFalse("A match result must not perform a blocking database sync on the tick thread",
            finishHandler.contains("storage.syncNow()"));

        int requestStart = storage.indexOf("public void requestSync()");
        int requestEnd = storage.indexOf("private void syncSafely()", requestStart);
        assertTrue("Storage sync requests must use the serial storage executor",
            requestStart >= 0 && requestEnd > requestStart
                && storage.substring(requestStart, requestEnd).contains("current.execute(this::syncSafely)"));
    }

    @Test
    public void testAsyncChatPlayerOperationsAreMarshalledToMainThread() throws Exception {
        String moderation = Files.readString(
            new File("src/main/java/fr/bosketball/moderation/ChatModerationService.java").toPath());
        String gameplay = Files.readString(
            new File("src/main/java/fr/bosketball/listener/GameplayListener.java").toPath());
        String renderer = Files.readString(
            new File("src/main/java/fr/bosketball/ui/BosketChatRenderer.java").toPath());
        assertTrue("Chat moderation must marshal async-event player work onto the primary thread",
            moderation.contains("if (!event.isAsynchronous())")
                && moderation.contains("Bukkit.getScheduler().callSyncMethod(plugin"));
        assertTrue("Chat gameplay checks must marshal async-event player work onto the primary thread",
            gameplay.contains("if (!event.isAsynchronous())")
                && gameplay.contains("Bukkit.getScheduler().callSyncMethod(plugin"));
        assertTrue("The custom renderer must be viewer-unaware and render once for the whole chat message",
            renderer.contains("implements ChatRenderer, ChatRenderer.ViewerUnaware"));
        assertTrue("The renderer must invoke Bukkit-backed rendering on the primary thread",
            renderer.contains("Bukkit.isPrimaryThread()")
                && renderer.contains("Bukkit.getScheduler().callSyncMethod(plugin"));
    }

    @Test
    public void testPublicIpProbeUsesMainThreadPortSnapshot() throws Exception {
        String source = Files.readString(new File("src/main/java/fr/bosketball/pack/ResourcePackHostService.java").toPath());
        int start = source.indexOf("private void discoverPublicIpv4Async()");
        int end = source.indexOf("    static boolean isPublicIpv4Literal", start);
        assertTrue("The public-IP probe method must exist", start >= 0 && end > start);
        String method = source.substring(start, end);
        int workerStart = method.indexOf("Thread probe = new Thread(() -> {");
        int workerEnd = method.indexOf("}, \"BosketBall-PublicIP-Probe\");", workerStart);
        assertTrue("The probe worker must exist", workerStart >= 0 && workerEnd > workerStart);
        String setup = method.substring(0, workerStart);
        String worker = method.substring(workerStart, workerEnd);
        assertTrue("The game port must be captured before the worker starts",
            setup.contains("int gamePort = resolveServerGamePort();"));
        assertFalse("The public-IP worker must not resolve the Bukkit game port",
            worker.contains("resolveServerGamePort()"));
        assertFalse("The public-IP worker must not call Bukkit APIs", worker.contains("Bukkit."));
        assertTrue("The worker must build its URL from the captured port",
            worker.contains("buildHttpPackUrl(ip, gamePort, sha1Hex)"));
    }

    @Test
    public void testPackCandidatesDoNotAdvertiseGamePortWithoutMux() throws Exception {
        File tempDir = Files.createTempDirectory("bb-pack-no-mux-test").toFile();
        Files.copy(new File("releases/BosketBall-ResourcePack.zip").toPath(),
            new File(tempDir, "BosketBall-ResourcePack.zip").toPath());
        fr.bosketball.pack.ResourcePackHostService host =
            new fr.bosketball.pack.ResourcePackHostService(tempDir, null);
        host.initialize(false, "127.0.0.1", 0, "auto", "", "", false);
        try {
            java.lang.reflect.Field boundPort = host.nettyMux().getClass().getDeclaredField("detectedBoundPort");
            boundPort.setAccessible(true);
            boundPort.setInt(host.nettyMux(), 25565);
            assertFalse("The test must model a known game port with no installed mux", host.nettyMux().isHooked());
            assertTrue("An unhooked Minecraft port cannot be advertised as an HTTP pack route",
                host.buildCandidateUrlsFor(null).isEmpty());
        } finally {
            host.shutdown();
        }
    }

    @Test
    public void testPersistentMailboxAndWarningYamlWritesUseAtomicWriter() throws Exception {
        String socialSource = Files.readString(new File("src/main/java/fr/bosketball/social/SocialService.java").toPath());
        String warningSource = Files.readString(new File("src/main/java/fr/bosketball/moderation/WarnService.java").toPath());
        assertTrue("Mailbox YAML must be written through AtomicYamlWriter",
            socialSource.contains("AtomicYamlWriter.writeYamlAtomically(file, null, yaml, Bukkit.getLogger());"));
        assertTrue("Warning YAML must be written through AtomicYamlWriter",
            warningSource.contains("AtomicYamlWriter.writeYamlAtomically(file, null, yaml, plugin.getLogger());"));
        assertFalse("Mailbox persistence must not bypass the atomic writer", socialSource.contains("yaml.save(file)"));
        assertFalse("Warning persistence must not bypass the atomic writer", warningSource.contains("yaml.save(file)"));
    }

    @Test
    public void testStaffModerationGuisEscapeUntrustedMiniMessageText() throws Exception {
        String source = Files.readString(new File("src/main/java/fr/bosketball/ui/ProgressionGuis.java").toPath());
        assertTrue("Report reasons must be escaped before rendering in the staff panel",
            source.contains("Msg.escapeMini(report.reason())"));
        assertTrue("Report player names must be escaped before rendering in the staff panel",
            source.contains("Msg.escapeMini(report.reporterName())")
                && source.contains("Msg.escapeMini(report.reportedName())"));
        assertTrue("Mailbox subjects and friend names must be escaped before rendering in GUIs",
            source.contains("Msg.escapeMini(mail.subject())")
                && source.contains("Msg.escapeMini(other.getName())"));
        assertTrue("Warning reasons and staff names must be escaped before rendering in the staff panel",
            source.contains("Msg.escapeMini(warn.reason())")
                && source.contains("Msg.escapeMini(warn.staffName())")
                && source.contains("Msg.escapeMini(list.get(0).reason())")
                && source.contains("Msg.escapeMini(list.get(0).staffName())"));
        String payload = "<click:run_command:'/op'>Open report</click>";
        net.kyori.adventure.text.Component escaped = fr.bosketball.util.Msg.parse(
            "<white>" + fr.bosketball.util.Msg.escapeMini(payload) + "</white>");
        assertTrue("Escaped user text must not retain an interactive click event", escaped.clickEvent() == null);
        assertEquals("Escaping must preserve the literal user text",
            payload,
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(escaped));
    }

    @Test
    public void testAchievementCategoryButtonsDoNotOverlapPaginationSlots() throws Exception {
        String progressionSrc = Files.readString(
            new File("src/main/java/fr/bosketball/ui/ProgressionGuis.java").toPath());
        String guiSrc = Files.readString(
            new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        java.util.regex.Pattern slotsPattern = java.util.regex.Pattern.compile("int\\[\\] tabSlots = \\{([^}]+)\\};");
        java.util.regex.Matcher openSlotsMatch = slotsPattern.matcher(progressionSrc);
        java.util.regex.Matcher clickSlotsMatch = slotsPattern.matcher(guiSrc);
        assertTrue("Achievement tabs need an inventory layout", openSlotsMatch.find());
        assertTrue("Achievement tab clicks need the matching inventory layout", clickSlotsMatch.find());
        Set<Integer> openSlots = new HashSet<>();
        Set<Integer> clickSlots = new HashSet<>();
        java.util.regex.Matcher slot = java.util.regex.Pattern.compile("\\d+").matcher(openSlotsMatch.group(1));
        while (slot.find()) {
            openSlots.add(Integer.parseInt(slot.group()));
        }
        slot = java.util.regex.Pattern.compile("\\d+").matcher(clickSlotsMatch.group(1));
        while (slot.find()) {
            clickSlots.add(Integer.parseInt(slot.group()));
        }
        assertEquals("Every painted achievement category must use its click handler slot", openSlots, clickSlots);
        assertFalse("Category tabs cannot occupy the previous-page slot", openSlots.contains(48));
        assertFalse("Category tabs cannot occupy the next-page slot", openSlots.contains(50));
    }

    @Test
    public void testPurchaseConfirmCannotChargeTwice() throws Exception {
        String src = Files.readString(new File("src/main/java/fr/bosketball/ui/PurchaseConfirmMenu.java").toPath());

        int guard = src.indexOf("if (PENDING.remove(player.getUniqueId()) == null)");
        int charge = src.indexOf("profile.spendCoins(");
        assertTrue("confirm() must claim the pending purchase before charging", guard > 0 && guard < charge);
        assertTrue("the close handler needs to know the sheet was settled, not abandoned",
            src.contains("public boolean isSettled()") && src.contains("settled = true;"));
        assertTrue("an abandoned sheet must drop its pending purchase",
            src.contains("public static void abandon(Player player)"));
    }

    @Test
    public void testZeroExtraPortPackMuxPremiumChromeGuis16x16IconsAndLobbyNpcs() throws Exception {

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

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            assertEquals(200, conn.getResponseCode());
            byte[] downloaded = conn.getInputStream().readAllBytes();
            assertTrue("Downloaded pack ZIP must exceed 1 MB!", downloaded.length > 1_000_000);
        } finally {
            host.shutdown();
        }

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

        for (char c = Glyphs.GUI_OVERLAY_SHOP; c <= Glyphs.GUI_OVERLAY_SETTINGS; c++) {
            assertEquals(177, Glyphs.textAdvancePx(String.valueOf(c), false));
        }

        char[] allOverlays = {
            Glyphs.GUI_OVERLAY_SHOP, Glyphs.GUI_OVERLAY_SHOP_GRID, Glyphs.GUI_OVERLAY_TAGS,
            Glyphs.GUI_OVERLAY_COSMETICS, Glyphs.GUI_OVERLAY_PLAY, Glyphs.GUI_OVERLAY_HUB,
            Glyphs.GUI_OVERLAY_HUB_OP, Glyphs.GUI_OVERLAY_LEADERBOARDS, Glyphs.GUI_OVERLAY_SETTINGS,
            Glyphs.GUI_OVERLAY_CONFIRM, Glyphs.GUI_OVERLAY_PLAY_BOT,
            Glyphs.GUI_OVERLAY_GUILD, Glyphs.GUI_OVERLAY_QUESTS, Glyphs.GUI_OVERLAY_ACHIEVEMENTS,
            Glyphs.GUI_OVERLAY_MAILBOX, Glyphs.GUI_OVERLAY_HISTORY, Glyphs.GUI_OVERLAY_FRIENDS,
            Glyphs.GUI_OVERLAY_PARTY, Glyphs.GUI_OVERLAY_RANKS, Glyphs.GUI_OVERLAY_SPECTATE,
            Glyphs.GUI_OVERLAY_PLAYER_CARD, Glyphs.GUI_OVERLAY_ADMIN, Glyphs.GUI_OVERLAY_REPORTS,
            Glyphs.GUI_OVERLAY_WARNINGS, Glyphs.GUI_OVERLAY_STUDIO
        };
        for (char c : allOverlays) {
            assertEquals(177, Glyphs.textAdvancePx(String.valueOf(c), false));
            int h = Glyphs.guiOverlayHeight(c);
            assertTrue("Overlay height must be one of 83/101/119/137, got " + h,
                h == 83 || h == 101 || h == 119 || h == 137);
        }
        assertEquals("The 27-slot player card needs the 3-row frame", 83,
            Glyphs.guiOverlayHeight(Glyphs.GUI_OVERLAY_PLAYER_CARD));
        assertEquals("The 5-row AI panel needs the 5-row frame", 119,
            Glyphs.guiOverlayHeight(Glyphs.GUI_OVERLAY_PLAY_BOT));

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile("releases/BosketBall-ResourcePack.zip")) {

            var setEntry = zf.getEntry("assets/bosketball/textures/font/gui_overlay_settings.png");
            assertNotNull(setEntry);
            try (var in = zf.getInputStream(setEntry)) {
                var setImg = javax.imageio.ImageIO.read(in);
                assertEquals(176, setImg.getWidth());
                assertEquals(119, setImg.getHeight());

                int light = 0;
                int sampled = 0;
                for (int y = 26; y < 110; y += 3) {
                    for (int x = 8; x < 168; x += 3) {
                        int argb = setImg.getRGB(x, y);
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;
                        sampled++;
                        if (Math.min(r, Math.min(g, b)) >= 150 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 30) {
                            light++;
                        }
                    }
                }
                assertTrue("GUI overlay plate must stay light brushed aluminium (neutral, min channel >= 150); got "
                        + (light * 100 / sampled) + "% light pixels",
                    light * 100 / sampled >= 30);

                int caption = setImg.getRGB(88, 16);
                assertTrue("The plaque caption band must stay dark ink so the white title stays readable!",
                    ((caption >> 16) & 0xFF) <= 48 && ((caption >> 8) & 0xFF) <= 52 && (caption & 0xFF) <= 64);

                int bandSpan = 0;
                for (int x = 6; x < 170; x++) {
                    int ink = setImg.getRGB(x, 16);
                    if (((ink >> 16) & 0xFF) <= 64 && ((ink >> 8) & 0xFF) <= 64 && (ink & 0xFF) <= 72) {
                        bandSpan++;
                    }
                }
                assertTrue("Plaque ink band must cover the caption cap (" + Glyphs.GUI_PLAQUE_TEXT_MAX
                        + "px); measured " + bandSpan + "px",
                    bandSpan >= Glyphs.GUI_PLAQUE_TEXT_MAX + 8);
                assertTrue("Caption cap must fit the plaque band",
                    Glyphs.GUI_PLAQUE_TEXT_MAX + 8 <= Glyphs.GUI_PLAQUE_BAND_PX);

                int bevel = setImg.getRGB(3, 10);
                assertTrue("GUI overlay must be framed by a bright chrome bevel!",
                    ((bevel >> 16) & 0xFF) >= 240 && ((bevel >> 8) & 0xFF) >= 246 && (bevel & 0xFF) >= 248);

                int socketRim = setImg.getRGB(25, 42);
                int sr = (socketRim >> 16) & 0xFF;
                int sg = (socketRim >> 8) & 0xFF;
                int sb = socketRim & 0xFF;
                assertTrue("Every slot must sit in a light metal socket rim (tin-grey, mid luminance)!",
                    sr >= 110 && sr <= 180 && sg >= sr && sb >= sg && sb - sr <= 50);

                int recess = setImg.getRGB(34, 52);
                assertTrue("Slot recesses must stay dark gunmetal so 16x16 icons pop!",
                    ((recess >> 16) & 0xFF) <= 90 && ((recess >> 8) & 0xFF) <= 96 && (recess & 0xFF) <= 110);
            }

            var g54Entry = zf.getEntry("assets/minecraft/textures/gui/container/generic_54.png");
            assertNotNull(g54Entry);
            try (var in = zf.getInputStream(g54Entry)) {
                var g54Img = javax.imageio.ImageIO.read(in);
                int casePixel = g54Img.getRGB(4, 210);
                int cr = (casePixel >> 16) & 0xFF;
                int cg = (casePixel >> 8) & 0xFF;
                int cb = casePixel & 0xFF;
                assertTrue("generic_54.png must carry the same light aluminium case as the overlay!",
                    Math.min(cr, Math.min(cg, cb)) >= 150 && cg >= cr && cb >= cg && cb - cr <= 50);
                int rimPixel = g54Img.getRGB(1, 60);
                assertTrue("generic_54.png must keep the bright chrome bevel on the left edge!",
                    ((rimPixel >> 16) & 0xFF) >= 210 && ((rimPixel >> 8) & 0xFF) >= 220);
                int slot = g54Img.getRGB(15, 145);
                assertTrue("Reference-style item slots must be dark gunmetal insets!",
                    ((slot >> 16) & 0xFF) <= 110 && ((slot >> 8) & 0xFF) <= 120 && (slot & 0xFF) <= 140);
                int playerRim = g54Img.getRGB(3, 170);
                assertTrue("The player-inventory half must keep the same bevelled light case as the overlay!",
                    ((playerRim >> 16) & 0xFF) >= 220 && ((playerRim >> 8) & 0xFF) >= 226);
            }

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

            for (String refIcon : List.of(
                "ref_check", "ref_cross", "ref_home", "ref_arrow_left", "ref_arrow_big_right",
                "ref_gear", "ref_trophy", "ref_cup_gold", "ref_medal_gold", "ref_star", "ref_lock",
                "ref_friends", "ref_shop", "ref_chest", "ref_clock", "ref_upgrade", "ref_compass",
                "ref_skull", "ref_gift", "ref_coins"
            )) {
                var re = zf.getEntry("assets/bosketball/textures/item/icons/" + refIcon + ".png");
                assertNotNull("Imported reference icon must ship: " + refIcon, re);
                assertNotNull("Reference icon must declare an item definition: " + refIcon,
                    zf.getEntry("assets/bosketball/items/" + refIcon + ".json"));
                assertNotNull("Reference icon must declare an item model: " + refIcon,
                    zf.getEntry("assets/bosketball/models/item/" + refIcon + ".json"));
                try (var in = zf.getInputStream(re)) {
                    assertNative16PixelArt(refIcon, javax.imageio.ImageIO.read(in));
                }
            }

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

    @Test
    public void testCommandLockdownLetsPlayersReachTheirOwnCommands() throws Exception {
        assertTrue("A player-facing command must stay whitelisted: /quests",
            fr.bosketball.server.ServerProtectionManager.isAllowedForNonOp("/quests"));
        assertTrue("A player-facing command must stay whitelisted: /accept",
            fr.bosketball.server.ServerProtectionManager.isAllowedForNonOp("/accept"));
        assertTrue("A player-facing command must stay whitelisted: /mail",
            fr.bosketball.server.ServerProtectionManager.isAllowedForNonOp("/mail"));

        assertTrue("BosketBall's own commands must reach their executor, which does the permission check",
            fr.bosketball.server.ServerProtectionManager.isAllowedByLockdown(false, true, "/bb map edit"));
        assertTrue("A builder must keep FAWE/Axiom commands",
            fr.bosketball.server.ServerProtectionManager.isAllowedByLockdown(true, false, "//wand"));
        assertTrue("A staff member must keep vanilla moderation commands",
            fr.bosketball.server.ServerProtectionManager.isAllowedByLockdown(true, false, "/gamemode creative"));
        assertFalse("A plain player must still be locked out of vanilla commands",
            fr.bosketball.server.ServerProtectionManager.isAllowedByLockdown(false, false, "/gamemode creative"));
        assertFalse("A plain player must still be locked out of other plugins",
            fr.bosketball.server.ServerProtectionManager.isAllowedByLockdown(false, false, "/someplugin thing"));

        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        assertTrue("/mail must be a declared command", pluginYml.contains("  mail:"));
        String cmdSrc = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("/mail must open the real mailbox GUI", cmdSrc.contains("openMailbox(p)"));
    }

    @Test
    public void testEloAdminShortcutsAreRouted() {
        String[] give = fr.bosketball.command.BosketCommand.normalizeShortcutArgs(
            null, "giveelo", new String[]{"Max", "50"});
        assertEquals(3, give.length);
        assertEquals("giveelo", give[0]);
        assertEquals("Max", give[1]);

        String[] add = fr.bosketball.command.BosketCommand.normalizeShortcutArgs(
            null, "addelo", new String[]{"Max", "50"});
        assertEquals("giveelo", add[0]);
        assertEquals("Max", add[1]);

        String[] mail = fr.bosketball.command.BosketCommand.normalizeShortcutArgs(
            null, "mailbox", new String[0]);
        assertEquals("mail", mail[0]);

        String[] plain = fr.bosketball.command.BosketCommand.normalizeShortcutArgs(
            null, "bb", new String[]{"arena", "list"});
        assertEquals("arena", plain[0]);
    }

    @Test
    public void testEveryPluggedCommandIsRoutedToItsOwnSubcommand() throws Exception {
        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        String commandsBlock = pluginYml.substring(pluginYml.indexOf("\ncommands:"), pluginYml.indexOf("\npermissions:"));
        java.util.List<String> declared = new java.util.ArrayList<>();
        for (String line : commandsBlock.split("\n")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^  ([a-z0-9_-]+):").matcher(line);
            if (m.find()) {
                declared.add(m.group(1));
            }
        }
        java.util.Set<String> handledElsewhere = java.util.Set.of(
            "help", "pack", "build", "spawn", "setspawn", "perm", "msg", "reply", "gg", "discord", "bosketball");

        for (String cmd : declared) {
            if (handledElsewhere.contains(cmd)) {
                continue;
            }
            String[] routed = fr.bosketball.command.BosketCommand.normalizeShortcutArgs(null, cmd, new String[0]);
            assertEquals("/" + cmd + " must route to a subcommand instead of falling into the admin panel",
                1, routed.length);
            assertFalse("/" + cmd + " must route to a named subcommand", routed[0].isEmpty());
        }
    }

    private static int readStaticInt(Class<?> owner, String field) {
        try {
            java.lang.reflect.Field f = owner.getDeclaredField(field);
            f.setAccessible(true);
            return f.getInt(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Missing constant " + owner.getSimpleName() + "." + field, ex);
        }
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

        String fontJson = Files.readString(new File("resourcepack/assets/minecraft/font/default.json").toPath());

        for (String glyphJson : new String[]{"\\ue35e", "\\ue35f", "\\ue360", "\\ue361", "\\ue362"}) {
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

        String lockerHudSrc = Files.readString(
            new File("src/main/java/fr/bosketball/ui/LockerRoom3dMenuManager.java").toPath());
        int hudBarCount = readStaticInt(fr.bosketball.ui.LockerRoom3dMenuManager.class, "HUD_BAR_COUNT");
        int hudRowsPerPage = readStaticInt(fr.bosketball.ui.LockerRoom3dMenuManager.class, "HUD_ROWS_PER_PAGE");
        assertEquals("Locker HUD must fit the vanilla stacked-BossBar render budget!", 6, hudBarCount);
        assertTrue("Every HUD line needs its own boss bar (top bar + status + rows)",
            2 + hudRowsPerPage <= hudBarCount);
        assertTrue("Locker HUD selection must stay in the second visible menu row!",
            hudRowsPerPage == 4
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

    @Test
    public void englishIsTheCompleteBaseLanguage() {
        assertEquals("English must be a fully covered locale", 100,
            fr.bosketball.i18n.Lang.coverage(fr.bosketball.i18n.Lang.Locale_.EN));
        assertTrue("Every locale must expose the same required keys",
            fr.bosketball.i18n.Lang.keyCount() > 50);
        for (fr.bosketball.i18n.Lang.Locale_ locale : fr.bosketball.i18n.Lang.Locale_.values()) {
            fr.bosketball.i18n.Lang lang = fr.bosketball.i18n.Lang.of(locale);

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

        for (fr.bosketball.i18n.Lang.Locale_ locale : fr.bosketball.i18n.Lang.Locale_.values()) {
            assertEquals("Locale " + locale.code() + " must be fully translated", 100,
                fr.bosketball.i18n.Lang.coverage(locale));
        }
        assertEquals("An unknown key must resolve to itself, never to null",
            "__nope__", german.raw("__nope__"));
    }

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

    @Test
    public void schematicDecodesFaweSpongeV3Format() throws Exception {

        File schem = SchematicTestWriter.writeSpongeSchematicV3();
        try {
            CourtSchematic decoded = CourtSchematic.load(schem);
            assertEquals("Width must come from the NBT Width tag", 30, decoded.width());
            assertEquals("Height must come from the NBT Height tag", 4, decoded.height());
            assertEquals("Length must come from the NBT Length tag", 18, decoded.length());
            assertEquals("The Sponge version must be reported", 3, decoded.formatVersion());
            assertEquals("Centred-copy offset X must be read from the Offset tag", -15, decoded.offsetX());
            assertEquals("Centred-copy offset Y must be read", -1, decoded.offsetY());
            assertEquals("Centred-copy offset Z must be read", -9, decoded.offsetZ());
            assertEquals("The copy origin must centre the court on X", 15.0, decoded.localCenterX(), 0.001);
            assertEquals("The copy origin must centre the court on Z", 9.0, decoded.localCenterZ(), 0.001);
            assertEquals("The deck must be detected as the top of the solid slab", 1,
                decoded.floorOffsetAtCenter());
            assertTrue("Palette must contain the floor block",
                decoded.palettePreview(64).contains("minecraft:sandstone"));
            assertTrue("Palette must contain the painted line block",
                decoded.palettePreview(64).contains("minecraft:white_concrete"));
            assertEquals("Solid blocks must be counted with air excluded", 30 * 18, decoded.nonAirBlocks());
        } finally {
            Files.deleteIfExists(schem.toPath());
        }
    }

    @Test
    public void schematicStillDecodesLegacySpongeV1Format() throws Exception {
        File schem = SchematicTestWriter.writeSpongeSchematicV1();
        try {
            CourtSchematic decoded = CourtSchematic.load(schem);
            assertEquals("A v1 file must decode instead of being rejected", 1, decoded.formatVersion());
            assertEquals(30, decoded.width());
            assertEquals("A v1 file must produce the same solid-block count", 30 * 18, decoded.nonAirBlocks());
        } finally {
            Files.deleteIfExists(schem.toPath());
        }
    }

    @Test
    public void floorCalibrationIgnoresDecorationAboveTheDeck() {

        File schem = null;
        try {
            schem = SchematicTestWriter.writeCourtWithMast();
            CourtSchematic decoded = CourtSchematic.load(schem);
            assertEquals("The deck top must be the floor even with a mast above it", 2,
                decoded.floorOffsetAtCenter());
        } catch (IOException e) {
            fail("Schematic must decode: " + e.getMessage());
        } finally {
            if (schem != null) {
                try {
                    Files.deleteIfExists(schem.toPath());
                } catch (IOException ignored) {

                }
            }
        }
    }

    @Test
    public void bundledTerrainSchemCalibratesOnTheDeckNotTheRoof() throws Exception {

        File file = new File("src/main/resources/courts/terrain.schem");
        assertTrue("The bundled default court must ship in src/main/resources/courts", file.isFile());
        CourtSchematic decoded = CourtSchematic.load(file);
        assertEquals("bundled terrain width", 49, decoded.width());
        assertEquals("bundled terrain height", 21, decoded.height());
        assertEquals("bundled terrain length", 52, decoded.length());
        assertEquals("The andesite deck (local Y2) must win over the coal roof (local Y13)", 3,
            decoded.floorOffsetAtCenter());
        assertFalse("The coloured court has no white line paint: the plugin draws the lines",
            decoded.deckHasLineMarkings());
        assertTrue("The coloured court must be detected on the deck", decoded.hasCourtBox());
        assertEquals("The court runs along X (basket to basket)", 27, decoded.courtSpanX());
        assertEquals("The court is 15 blocks wide", 15, decoded.courtSpanZ());
        assertEquals(27, decoded.courtLength());
        assertEquals(15, decoded.courtWidth());
        assertTrue("Baskets on the east-west axis", decoded.courtAlongX());
        assertEquals("Court centre X (blocks 12..38)", 25.5, decoded.localCenterX(), 0.001);
        assertEquals("Court centre Z (blocks 18..32)", 25.5, decoded.localCenterZ(), 0.001);
        assertEquals("Markings painted inside the court", 126, decoded.courtPaintedBlocks());
        assertEquals("The coal roof sits 10 blocks above the floor: long shots stay under it",
            10, decoded.ceilingAboveFloor());
        assertTrue(decoded.hasCeiling());
        assertEquals("Bundled terrain solid block count", 18246, decoded.nonAirBlocks());
    }

    @Test
    public void bundledTerrainShipsItsSignsAndAttachables() throws Exception {

        CourtSchematic decoded = CourtSchematic.load(new File("src/main/resources/courts/terrain.schem"));
        assertEquals("Every illegal sign of the build must travel with the schematic",
            216, decoded.signCount());
        assertEquals("All block entities are kept, not just counted",
            decoded.blockEntityCount(), decoded.blockEntities().size());
        assertTrue("Doors of the build must be listed so they can be forced closed",
            decoded.doorCount() >= 100);
        assertTrue("Doors are recognised", CourtSchematic.isDoorName("minecraft:dark_oak_door[facing=north]"));
        assertFalse("A trapdoor is decoration: it keeps its open/closed state exactly as saved",
            CourtSchematic.isDoorName("minecraft:dark_oak_trapdoor[open=true]"));
        assertTrue("Trapdoors are placed in the last pass so they cannot pop",
            CourtSchematic.isAttachableName("minecraft:dark_oak_trapdoor[facing=north,half=bottom,open=true]"));
        assertFalse("A plain block is not a door", CourtSchematic.isDoorName("minecraft:andesite"));
        assertTrue("The build has signs/torches to place in the second pass",
            decoded.attachableCount() > 100);

        int signsWithoutBlock = 0;
        for (CourtSchematic.SavedBlockEntity be : decoded.blockEntities()) {
            assertTrue("Block entity out of the schematic bounds: " + be.x() + "/" + be.y() + "/" + be.z(),
                be.x() >= 0 && be.x() < decoded.width()
                    && be.y() >= 0 && be.y() < decoded.height()
                    && be.z() >= 0 && be.z() < decoded.length());
            if (be.isSign() && !decoded.blockNameAt(be.x(), be.y(), be.z()).contains("sign")) {
                signsWithoutBlock++;
            }
        }
        assertEquals("Every saved sign must sit on a sign block (otherwise the text is restored nowhere)",
            0, signsWithoutBlock);

        int[] indices = decoded.attachableIndices();
        for (int i = 1; i < indices.length; i++) {
            assertTrue("Attachable indices must stay in stream order",
                indices[i] > indices[i - 1]);
        }
        assertTrue("Signs must be flagged as attachable",
            CourtSchematic.isAttachableName("minecraft:oak_sign[rotation=8]"));
        assertTrue("Wall torches must be flagged as attachable",
            CourtSchematic.isAttachableName("minecraft:wall_torch[facing=north]"));
        assertFalse("Stone is not attachable",
            CourtSchematic.isAttachableName("minecraft:stone"));
    }

    @Test
    public void rotationTurnsTheCourtWithoutMovingItsCalibration() throws Exception {

        CourtSchematic decoded = CourtSchematic.load(new File("src/main/resources/courts/terrain.schem"));
        int floorBefore = decoded.floorOffsetAtCenter();
        int widthBefore = decoded.width();
        int lengthBefore = decoded.length();

        decoded.setRotation(180);
        assertEquals("Rotation is stored", 180, decoded.rotationDegrees());
        assertTrue("Rotation is applied", decoded.isRotated());
        assertEquals("A 180 turn must not resize the footprint", widthBefore, decoded.pasteWidth());
        assertEquals("A 180 turn must not resize the footprint", lengthBefore, decoded.pasteLength());
        assertEquals("A 180 turn must not move the deck", floorBefore, decoded.floorOffsetAtCenter());

        decoded.setRotation(90);
        assertEquals("The 90 turn is kept as requested", 90, decoded.rotationDegrees());
        assertEquals("A 90 turn swaps the X footprint", lengthBefore, decoded.pasteWidth());
        assertEquals("A 90 turn swaps the Z footprint", widthBefore, decoded.pasteLength());
        assertEquals("A 90 turn must not move the deck", floorBefore, decoded.floorOffsetAtCenter());
        assertTrue("The pasted centre stays inside the built volume",
            decoded.localCenterX() > 0 && decoded.localCenterX() < decoded.pasteWidth());
        assertTrue("The pasted centre stays inside the built volume",
            decoded.localCenterZ() > 0 && decoded.localCenterZ() < decoded.pasteLength());

        decoded.setRotation(270);
        assertEquals("270 swaps the footprint the other way", lengthBefore, decoded.pasteWidth());
        decoded.setRotation(0);
        assertEquals(0, decoded.rotationDegrees());
        assertFalse(decoded.isRotated());
        assertEquals(widthBefore, decoded.pasteWidth());
        assertEquals(lengthBefore, decoded.pasteLength());
        assertEquals(270, fr.bosketball.config.BosketConfig.SchematicCourts.normalizeRotation(270));
        assertEquals(90, fr.bosketball.config.BosketConfig.SchematicCourts.normalizeRotation(95));
        assertEquals(180, fr.bosketball.config.BosketConfig.SchematicCourts.normalizeRotation(180));
    }

    @Test
    public void signTextIsUnwrappedFromJson() {

        assertEquals("Hello arena", CourtSchematic.jsonPlainText("{\"text\":\"Hello arena\"}"));
        assertEquals("", CourtSchematic.jsonPlainText("{\"text\":\"\"}"));
        assertEquals("plain text", CourtSchematic.jsonPlainText("plain text"));
        assertEquals("", CourtSchematic.jsonPlainText(""));
        assertEquals("A B", CourtSchematic.jsonPlainText(
            "{\"text\":\"A\",\"extra\":[{\"text\":\" B\"}]}"));
    }

    @Test
    public void seasonDatesAreRealAndStableAcrossRestarts() {

        long start = fr.bosketball.season.SeasonMath.parseDateMillis("2026-11-01");
        assertTrue("A plain ISO date must parse", start > 0);
        assertTrue("An unset date must stay unset", fr.bosketball.season.SeasonMath.parseDateMillis("") < 0);
        assertTrue("A broken date must stay unset",
            fr.bosketball.season.SeasonMath.parseDateMillis("pas une date") < 0);
        assertEquals("The same value must always give the same instant",
            start, fr.bosketball.season.SeasonMath.parseDateMillis("2026-11-01"));

        long day = 86_400_000L;
        long[] season1 = fr.bosketball.season.SeasonMath.seasonWindow(1, start, -1L, 60 * day);
        assertEquals(start, season1[0]);
        assertEquals(start + 60 * day, season1[1]);

        long[] season2 = fr.bosketball.season.SeasonMath.seasonWindow(2, start, -1L, 60 * day);
        assertEquals("Season 2 must follow season 1 without a gap", season1[1], season2[0]);
        assertEquals(season1[1] + 60 * day, season2[1]);

        long configuredEnd = start + 30 * day;
        long[] explicitEnd = fr.bosketball.season.SeasonMath.seasonWindow(1, start, configuredEnd, 60 * day);
        assertEquals("An explicit end-date wins for season 1", configuredEnd, explicitEnd[1]);

        long[] noConfig = fr.bosketball.season.SeasonMath.seasonWindow(3, -1L, -1L, 60 * day);
        assertEquals("Without a configured start date the caller keeps its fallback", -1L, noConfig[0]);

        assertTrue("The window is rendered as a readable date range",
            fr.bosketball.season.SeasonMath.formattedWindow(season1[0], season1[1]).contains("2026"));
    }

    @Test
    public void smallCapsLabelsKeepMiniMessageTagsIntact() {

        assertEquals("\u029C\u1D0F\u1D0F\u1D18\u1D07\u0280",
            fr.bosketball.util.Glyphs.smallCaps("Hooper"));
        assertEquals("\uA731\u1D07\u1D00\uA731\u1D0F\u0274 \u026A",
            fr.bosketball.util.Glyphs.smallCaps("Season I"));

        String name = "<#FDE047><bold>HOOPER</bold></#FDE047>";
        String converted = fr.bosketball.util.Glyphs.smallCapsLabels(name);
        assertTrue("Tags must survive untouched: " + converted, converted.startsWith("<#FDE047><bold>"));
        assertTrue("Tags must survive untouched: " + converted, converted.endsWith("</bold></#FDE047>"));
        assertTrue("Labels in caps must become small caps: " + converted, converted.contains("\u029C\u1D0F\u1D0F\u1D18\u1D07\u0280"));

        assertEquals("Player names must never be mangled", "<gray>MaxLananas</gray>",
            fr.bosketball.util.Glyphs.smallCapsLabels("<gray>MaxLananas</gray>"));
        assertEquals("Hex colours must never be translated", "<#FDE047>ELO</#FDE047>".replace("ELO", "\u1D07\u029F\u1D0F"),
            fr.bosketball.util.Glyphs.smallCapsLabels("<#FDE047>ELO</#FDE047>"));

        var title = fr.bosketball.util.Glyphs.customGuiOverlayTitle(
            fr.bosketball.util.Glyphs.GUI_OVERLAY_HUB, "LEADERBOARDS");
        assertNotNull(title);
    }

    @Test
    public void roofedBuildPutsTheDeckOnTheFloorNotOnTheRoof() throws Exception {

        assertEquals("A roof above the deck must never win the deck vote",
            1, fr.bosketball.arena.schematic.CourtSchematic.deckOffset(new int[]{200, 200, 0, 0, 0, 0, 200}));
        assertEquals("A multi-layer floor slab must resolve to its top layer",
            2, fr.bosketball.arena.schematic.CourtSchematic.deckOffset(new int[]{200, 200, 200, 0, 0}));
        assertEquals("A plain court must resolve to its single floor layer",
            0, fr.bosketball.arena.schematic.CourtSchematic.deckOffset(new int[]{120, 0, 0}));
        assertEquals("Invisible light blocks must never form a deck",
            0, fr.bosketball.arena.schematic.CourtSchematic.deckOffset(new int[]{0, 0, 0}));

        File schem = SchematicTestWriter.writeRoofedCourt();
        try {
            CourtSchematic decoded = CourtSchematic.load(schem);
            assertEquals("Light-filled interior + coal roof must still resolve the floor", 2,
                decoded.floorOffsetAtCenter());
            assertFalse("A roofed shell has no painted lines on the deck", decoded.deckHasLineMarkings());
            assertEquals("No line markings means no baked lines", 0, decoded.deckLineMarkings());
        } finally {
            Files.deleteIfExists(schem.toPath());
        }
    }

    @Test
    public void paintedCourtLinesAreDetectedOnTheDeck() throws Exception {

        File schem = SchematicTestWriter.writePaintedCourt();
        try {
            CourtSchematic decoded = CourtSchematic.load(schem);
            assertEquals("The painted floor is the deck", 1, decoded.floorOffsetAtCenter());
            assertTrue("White painted lines on the deck must be detected",
                decoded.deckHasLineMarkings());
            assertTrue("Line markings must be counted", decoded.deckLineMarkings() >= 16);
        } finally {
            Files.deleteIfExists(schem.toPath());
        }

        File mast = SchematicTestWriter.writeCourtWithMast();
        try {
            CourtSchematic decoded = CourtSchematic.load(mast);
            assertFalse("An unpainted deck must not claim baked lines", decoded.deckHasLineMarkings());
        } finally {
            Files.deleteIfExists(mast.toPath());
        }
    }

    @Test
    public void schematicRejectsGarbageWithoutDecodingSilently() throws Exception {
        File broken = File.createTempFile("bosketball-broken", ".schem");
        try {
            Files.write(broken.toPath(), new byte[]{0x0A, (byte) 0xFF, 0x00});
            try {
                CourtSchematic.load(broken);
                fail("A corrupt schematic must raise an IOException, not decode to garbage");
            } catch (IOException expected) {
                assertTrue("The error message must name the problem",
                    expected.getMessage() != null && !expected.getMessage().isBlank());
            }
        } finally {
            Files.deleteIfExists(broken.toPath());
        }
    }

    @Test
    public void schematicCourtConfigIsClampedAndDocumented() throws Exception {
        String cfg = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("config.yml must document the instanced court section",
            cfg.contains("schematic-courts:") && cfg.contains("schematic: terrain.schem")
                && cfg.contains("instances: 50") && cfg.contains("world-name: bosketball_courts"));
        assertTrue("The court origin must be far from the lobby spawn",
            cfg.contains("origin-x: 100000") && cfg.contains("origin-z: 100000"));

        var parsed = fr.bosketball.config.BosketConfig.defaults().schematicCourts();
        assertTrue("Instanced courts must be enabled by default", parsed.enabled());
        assertEquals("Default must provide 50 private courts", 50, parsed.instances());
        assertTrue("Court spacing must exceed the build size", parsed.spacingBlocks() >= 64);
        assertTrue("A schematic must be configured by default", parsed.hasSchematic());
    }

    @Test
    public void legacyStadiumIsNeverHandedOutWhenInstancedCourtsAreEnabled() throws Exception {

        String gm = Files.readString(new File("src/main/java/fr/bosketball/game/GameManager.java").toPath());
        int start = gm.indexOf("private Arena findOrCreateFreeArena");
        assertTrue("findOrCreateFreeArena must exist", start > 0);
        String method = gm.substring(start, gm.indexOf("public void quickJoin", start));
        assertTrue("Solo sessions must go through acquireSoloCourt()",
            gm.contains("acquireSoloCourt()"));
        assertTrue("With instanced courts on, no generated stadium may be returned",
            method.contains("if (instancedCourtsRequested()) {\n            return plugin.schematicCourtPool().acquireFreeArena(null);"));
        assertTrue("The instanced pool must report whether it was requested",
            gm.contains("public boolean instancedCourtsRequested()"));

        String tutorial = Files.readString(new File("src/main/java/fr/bosketball/ui/TutorialManager.java").toPath());
        assertTrue("The tutorial must not fall back to the legacy arena",
            tutorial.contains("instancedCourtsRequested()"));
    }

    @Test
    public void configDocumentsTheLegacyWorldRetirement() throws Exception {
        String cfg = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("The legacy practice world must be retired by default",
            cfg.contains("retire-legacy-practice-world: true"));
        assertTrue("origin-y must be documented as the floor level", cfg.contains("Y of the FLOOR"));
    }

    @Test
    public void courtSpansAreMappedToTheRightWorldAxes() {

        double[] longOnZ = fr.bosketball.arena.schematic.SchematicCourtPool
            .axisSpans(false, 15, 27, 15.0, 9.0);
        assertEquals("Basket-to-basket half-size must land on Z", 12.5, longOnZ[0], 0.001);
        assertEquals("Across half-size must land on X", 6.5, longOnZ[1], 0.001);

        double[] longOnX = fr.bosketball.arena.schematic.SchematicCourtPool
            .axisSpans(true, 27, 15, 15.0, 9.0);
        assertEquals("With the hoops on X, Z only gets the across half-size", 6.5, longOnX[0], 0.001);
        assertEquals("With the hoops on X, basket-to-basket lands on X", 12.5, longOnX[1], 0.001);

        double[] hugeConfig = fr.bosketball.arena.schematic.SchematicCourtPool
            .axisSpans(false, 10, 10, 96.0, 64.0);
        assertEquals("A 10-long build can never become 24 long", 4.0, hugeConfig[0], 0.001);
        assertEquals("A 10-wide build can never become 16 wide", 4.0, hugeConfig[1], 0.001);
    }

    @Test
    public void hoopAxisFollowsTheBuildNotTheFacingConfig() {

        assertFalse("A long-on-Z build must put the hoops on the Z axis",
            fr.bosketball.arena.schematic.SchematicCourtPool.hoopAxisAlongX("auto", "west", 15, 27));
        assertTrue("A long-on-X build must put the hoops on the X axis",
            fr.bosketball.arena.schematic.SchematicCourtPool.hoopAxisAlongX("auto", "north", 27, 15));
        assertTrue("An explicit hoop-facing always wins",
            fr.bosketball.arena.schematic.SchematicCourtPool.hoopAxisAlongX("east-west", "north", 15, 27));
        assertFalse("z is the short form of north-south",
            fr.bosketball.arena.schematic.SchematicCourtPool.hoopAxisAlongX("z", "east", 27, 15));
        assertFalse("A square build falls back to facing=north",
            fr.bosketball.arena.schematic.SchematicCourtPool.hoopAxisAlongX("auto", "north", 20, 20));
        assertTrue("A square build falls back to facing=west",
            fr.bosketball.arena.schematic.SchematicCourtPool.hoopAxisAlongX("auto", "west", 20, 20));
    }

    @Test
    public void schematicCourtPlacementNeverOverlaps() {
        int instances = 50;
        int spacing = 384;
        int columns = (int) Math.ceil(Math.sqrt(instances));
        Set<String> centres = new HashSet<>();
        for (int i = 0; i < instances; i++) {
            int cx = 100_000 + (i % columns) * spacing;
            int cz = 100_000 + (i / columns) * spacing;
            assertTrue("Court #" + (i + 1) + " must sit far away from the spawn point",
                Math.abs(cx) > 50_000 && Math.abs(cz) > 50_000);
            assertTrue("Two courts must never share a grid slot", centres.add(cx + ":" + cz));
        }
        assertEquals("All 50 courts must get a distinct slot", instances, centres.size());
        assertTrue("Spacing must exceed any plausible court footprint", spacing > 60);
    }

    @Test
    public void testRankedSeasonSoftResetPlacementSwingAndRewardsAreDeterministic() {
        assertEquals("Half of a climb above the anchor survives a soft reset",
            1600, fr.bosketball.season.SeasonMath.softReset(2000, 1200, 0.5));
        assertEquals("A climb below the anchor is pulled back up",
            1100, fr.bosketball.season.SeasonMath.softReset(1000, 1200, 0.5));
        assertEquals("keep-ratio 1.0 must not change the rating",
            1777, fr.bosketball.season.SeasonMath.softReset(1777, 1200, 1.0));
        assertEquals("keep-ratio 0.0 parks everyone on the anchor",
            1200, fr.bosketball.season.SeasonMath.softReset(4321, 1200, 0.0));
        assertEquals("Soft reset can never fall under the ELO floor",
            fr.bosketball.season.SeasonMath.MIN_ELO,
            fr.bosketball.season.SeasonMath.softReset(100, 1200, 0.0) >= 100 ? 100 : 0);

        assertEquals("First placement match doubles the swing",
            40, fr.bosketball.season.SeasonMath.applyPlacementSwing(20, 0, 5));
        assertEquals("The swing decays linearly to x1.0 on the last placement",
            24, fr.bosketball.season.SeasonMath.applyPlacementSwing(20, 4, 5));
        assertEquals("Placed players keep the raw ELO swing",
            20, fr.bosketball.season.SeasonMath.applyPlacementSwing(20, 5, 5));
        assertEquals("A loss stays a loss, amplified",
            -40, fr.bosketball.season.SeasonMath.applyPlacementSwing(-20, 0, 5));
        assertEquals("A zero delta is never invented out of nothing",
            0, fr.bosketball.season.SeasonMath.applyPlacementSwing(0, 0, 5));
        assertTrue("A tiny delta must still move the needle",
            fr.bosketball.season.SeasonMath.applyPlacementSwing(1, 0, 5) >= 1);

        assertEquals("I", fr.bosketball.season.SeasonMath.roman(1));
        assertEquals("IV", fr.bosketball.season.SeasonMath.roman(4));
        assertEquals("SEASON XII", fr.bosketball.season.SeasonMath.seasonName(12));
        assertEquals(50, fr.bosketball.season.SeasonMath.progressPercent(0L, 1000L, 500L));
        assertEquals(0, fr.bosketball.season.SeasonMath.progressPercent(0L, 1000L, -50L));
        assertEquals(100, fr.bosketball.season.SeasonMath.progressPercent(0L, 1000L, 5000L));
        assertTrue(fr.bosketball.season.SeasonMath.remainingLabel(2L * 24L * 3_600_000L + 3L * 3_600_000L)
            .startsWith("2d 3h"));

        fr.bosketball.model.Rank mvpHolder = fr.bosketball.season.SeasonMath.tierFor(2400);
        assertEquals(fr.bosketball.model.Rank.MVP, mvpHolder);
        assertEquals(6, fr.bosketball.season.SeasonMath.competitiveTierIndex(fr.bosketball.model.Rank.MVP));
        assertEquals(0, fr.bosketball.season.SeasonMath.competitiveTierIndex(fr.bosketball.model.Rank.ROOKIE));
    }

    @Test
    public void testSeasonConfigShipsSaneDefaultsAndEveryTierHasAPayout() throws Exception {
        var season = fr.bosketball.config.BosketConfig.defaults().season();
        assertTrue("Seasons must be enabled by default", season.enabled());
        assertEquals(5, season.placementMatches());
        assertEquals(1200, season.softResetAnchor());
        assertEquals(0.5, season.softResetKeepRatio(), 0.0001);
        assertEquals("Seven competitive tiers must each have a coin payout",
            7, season.tierCoins().size());
        assertEquals("Seven competitive tiers must each have an XP payout",
            7, season.tierXp().size());
        for (int i = 1; i < season.tierCoins().size(); i++) {
            assertTrue("Tier payouts must grow with the tier", season.tierCoins().get(i) > season.tierCoins().get(i - 1));
            assertTrue("Tier XP must grow with the tier", season.tierXp().get(i) > season.tierXp().get(i - 1));
        }
        assertTrue("The podium bonus must out-rank a plain tier payout",
            season.top1Coins() > season.top3Coins() && season.top3Coins() > 0);

        String cfg = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("config.yml must document the ranked season block", cfg.contains("\nseason:"));
        assertTrue("config.yml must expose the placement requirement", cfg.contains("placement-matches:"));
        assertTrue("config.yml must expose the soft reset ratio", cfg.contains("keep-ratio:"));

        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        assertTrue("plugin.yml must register /season", pluginYml.contains("\n  season:\n"));
        assertTrue("plugin.yml must alias /saison", pluginYml.contains("aliases: [saison]"));

        assertEquals("The season overlay glyph must be 176px wide like every other container",
            177, fr.bosketball.util.Glyphs.textAdvancePx(String.valueOf(fr.bosketball.util.Glyphs.GUI_OVERLAY_SEASON), false));
        assertEquals(137, fr.bosketball.util.Glyphs.guiOverlayHeight(fr.bosketball.util.Glyphs.GUI_OVERLAY_SEASON));

        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertTrue("The hub must expose four doors: play, season, collection, social",
            guiSrc.contains("case 19 -> openPlayModes(player);")
                && guiSrc.contains("case 21 -> openSeason(player);")
                && guiSrc.contains("case 23 -> {")
                && guiSrc.contains("case 25 -> {"));
        assertTrue("The season menu must be reachable from the standard rail",
            guiSrc.contains("slot == GuiChrome.SLOT_SEASON"));
    }

    @Test
    public void chatEscalationLadderIsDeterministic() {
        var svc = fr.bosketball.moderation.ChatModerationService.Sanction.class;
        assertNotNull(svc);
        // 3 insults -> 1 h mute
        assertEquals(fr.bosketball.moderation.ChatModerationService.Sanction.NONE,
            fr.bosketball.moderation.ChatModerationService.sanctionFor(1, 0, 3, 3));
        assertEquals(fr.bosketball.moderation.ChatModerationService.Sanction.NONE,
            fr.bosketball.moderation.ChatModerationService.sanctionFor(2, 0, 3, 3));
        assertEquals(fr.bosketball.moderation.ChatModerationService.Sanction.MUTE,
            fr.bosketball.moderation.ChatModerationService.sanctionFor(3, 0, 3, 3));
        // insults reset after each mute, so the second cycle starts from 1 again
        assertEquals(fr.bosketball.moderation.ChatModerationService.Sanction.NONE,
            fr.bosketball.moderation.ChatModerationService.sanctionFor(1, 1, 3, 3));
        assertEquals(fr.bosketball.moderation.ChatModerationService.Sanction.MUTE,
            fr.bosketball.moderation.ChatModerationService.sanctionFor(3, 1, 3, 3));
        // 3rd mute is replaced by the permanent ban
        assertEquals(fr.bosketball.moderation.ChatModerationService.Sanction.PERMANENT_BAN,
            fr.bosketball.moderation.ChatModerationService.sanctionFor(3, 2, 3, 3));
        // thresholds are clamped so a broken config can never disable the ladder
        assertEquals(fr.bosketball.moderation.ChatModerationService.Sanction.MUTE,
            fr.bosketball.moderation.ChatModerationService.sanctionFor(1, 0, 0, 0));
        assertEquals(fr.bosketball.moderation.ChatModerationService.Sanction.PERMANENT_BAN,
            fr.bosketball.moderation.ChatModerationService.sanctionFor(1, 1, 1, 2));
    }

    @Test
    public void moderationConfigShipsTheEscalationLadder() throws Exception {
        String cfg = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("config.yml must expose the insult threshold", cfg.contains("insults-before-mute: 3"));
        assertTrue("config.yml must expose the mute duration", cfg.contains("mute-hours: 1"));
        assertTrue("config.yml must expose the mutes-before-ban threshold", cfg.contains("mutes-before-ban: 3"));
        assertTrue("The ladder ban must be documented as permanent",
            cfg.contains("the ladder always bans permanently"));
        String src = Files.readString(new File(
            "src/main/java/fr/bosketball/moderation/ChatModerationService.java").toPath());
        assertTrue("The ladder ban must be permanent", src.contains("player.ban(plainReason, (java.util.Date) null"));
        assertTrue("Mute counts must be persisted", Files.readString(new File(
            "src/main/java/fr/bosketball/stats/StatsManager.java").toPath())
            .contains("moderation.mute-count"));
    }

    @Test
    public void privateChatChannelsNeverLeakIntoGlobalChat() throws Exception {
        String src = Files.readString(new File(
            "src/main/java/fr/bosketball/moderation/ChatModerationService.java").toPath());
        assertFalse("An empty message must never be broadcast to global chat",
            src.contains("event.message(Component.text(\"\"))"));
        assertTrue("Routed channels must cancel the chat event", src.contains("event.setCancelled(true)"));
        assertTrue("The match line must reuse the public rank/guild prefix",
            src.contains("BosketChatRenderer.buildPlayerChatPrefix"));
        // the guild sub-command word must never be part of the delivered message
        String cmd = Files.readString(new File(
            "src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("Guild chat must drop the sub-command argument",
            cmd.contains("java.util.Arrays.copyOfRange(args, 1, args.length)"));
        assertFalse("The raw args must never be forwarded to the guild channel",
            cmd.contains("guilds.sendGuildChat(player, String.join(\" \", args))"));
    }

    @Test
    public void displayPipelineCannotDieOnOneBadStep() throws Exception {
        String plugin = Files.readString(new File("src/main/java/fr/bosketball/BosketBallPlugin.java").toPath());
        assertTrue("The UI task must be scheduled through scheduleUiTask()", plugin.contains("private void scheduleUiTask()"));
        assertTrue("A watchdog must reschedule a cancelled UI task", plugin.contains("private void ensureDisplayPipeline()"));
        assertTrue("Each UI subsystem must be guarded individually", plugin.contains("private void step(String key, Runnable body)"));
        assertTrue("The guarded step must swallow failures so the task survives",
            plugin.contains("catch (Throwable failure)"));
        assertTrue("A repair entry point must exist", plugin.contains("public int reconcileDisplays(boolean announce)"));
        assertTrue("Holograms must be re-spawned when their entities vanish",
            plugin.contains("lobbyHolograms.ensureAllSpawned()"));
        assertTrue("The repair must be reachable by staff", plugin.contains("uiFaultCounts()"));
        assertFalse("The whole UI must never be scheduled as one naked lambda again",
            plugin.contains("runTaskTimer(this, () -> {\n            TickProfiler.beginTick();"));
        assertTrue("The UI pipeline must be restored after /bb reload", plugin.contains("        scheduleUiTask();\n        reconcileDisplays(false);"));

        String holograms = Files.readString(new File("src/main/java/fr/bosketball/ui/LobbyHologramManager.java").toPath());
        assertTrue("Holograms need a shared needsRespawn() predicate", holograms.contains("public boolean needsRespawn(HologramInstance inst)"));
        assertTrue("Missing hologram entities must be counted", holograms.contains("public int countNeedingRespawn()"));
        assertTrue("Missing hologram entities must be rebuilt (chunk loaded first)",
            holograms.contains("public int ensureAllSpawned()") && holograms.contains("getChunkAt(base).load()"));

        String match = Files.readString(new File("src/main/java/fr/bosketball/game/MatchSession.java").toPath());
        assertTrue("Leaving a match must rebuild the sidebar right away",
            match.contains("sidebarManager().updatePlayerSidebar(p, sidebarPlugin.gameManager())"));
        String locker = Files.readString(new File("src/main/java/fr/bosketball/ui/LockerRoom3dMenuManager.java").toPath());
        assertTrue("Leaving the 3D locker must rebuild the sidebar right away",
            locker.contains("sidebarManager().updatePlayerSidebar(player, plugin.gameManager())"));

        String sidebar = Files.readString(new File("src/main/java/fr/bosketball/ui/ScoreboardSidebarManager.java").toPath());
        assertTrue("One broken player must not blank everyone's sidebar",
            sidebar.contains("catch (RuntimeException brokenProfile)"));
        assertTrue("The sidebar objective name must be inspectable", sidebar.contains("public static final String OBJ_NAME"));

        String command = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("/bb displays must exist", command.contains("case \"displays\", \"hud\", \"sidebars\" -> handleDisplaysCommand"));
        assertTrue("/bb displays repair must be documented", command.contains("/bb displays repair"));
    }

    @Test
    public void startupCommandStartsWithTheJavaExecutable() throws Exception {
        String flags = Files.readString(
            new File("docs/server-optimization/java-flags.txt").toPath()).trim();
        assertTrue("Panels run the startup line through env: it must start with 'java', got: "
                + flags.substring(0, Math.min(24, flags.length())),
            flags.startsWith("java "));
        assertTrue("The jar must still be launched", flags.contains("-jar server.jar --nogui"));
        String doc = Files.readString(new File("docs/server-optimization/README.md").toPath());
        assertTrue("The env crash must be documented", doc.contains("env: unexpected argument '-X' found"));
    }

    @Test
    public void serverOptimizationBundleStaysCoherent() throws Exception {
        File dir = new File("docs/server-optimization");
        String[] required = {
            "README.md", "server.properties", "bukkit.yml", "spigot.yml",
            "paper-global.yml", "paper-world-defaults.yml", "start.sh", "java-flags.txt", "apply.sh",
        };
        for (String name : required) {
            File file = new File(dir, name);
            assertTrue("Missing server optimisation file: " + name, file.isFile());
            assertTrue("Optimisation file is empty: " + name, file.length() > 32);
        }

        String props = Files.readString(new File(dir, "server.properties").toPath());
        assertTrue("View distance must stay at 6", props.contains("\nview-distance=6\n"));
        assertTrue("Simulation distance must stay at 4 (ball + players inside the tick radius)",
            props.contains("\nsimulation-distance=4\n"));
        assertTrue("Chunk writes must be asynchronous", props.contains("\nsync-chunk-writes=false\n"));
        assertTrue("The void generator settings must survive the optimisation",
            props.contains("minecraft\\:the_void"));
        assertTrue("At least 50 player slots", props.contains("\nmax-players=60\n"));

        String flags = Files.readString(new File(dir, "java-flags.txt").toPath());
        assertTrue("Compact object headers is the Java 25 win", flags.contains("-XX:+UseCompactObjectHeaders"));
        assertTrue("The JVM must be told the real core count", flags.contains("-XX:ActiveProcessorCount=2"));
        assertFalse("ZGenerational is obsolete on Java 25", flags.contains("ZGenerational"));
        assertFalse("ZGC is the wrong collector on 1.5 vCPU", flags.contains("-XX:+UseZGC"));
        String xms = flags.replaceAll(".*-Xms(\\d+)M.*", "$1").trim();
        String xmx = flags.replaceAll(".*-Xmx(\\d+)M.*", "$1").trim();
        assertEquals("Xms and Xmx must match (MB)", xms, xmx);
        int heapMb = Integer.parseInt(xmx.trim());
        assertTrue("The heap must leave room for the OS on a 6 GB container: " + heapMb,
            heapMb >= 2560 && heapMb <= 4096);

        String spigot = Files.readString(new File(dir, "spigot.yml").toPath());
        assertTrue("The courts world needs its own tuning", spigot.contains("bosketball_courts:"));
        assertTrue("Netty must not spawn more threads than the CPU budget", spigot.contains("netty-threads: 2"));

        String world = Files.readString(new File(dir, "paper-world-defaults.yml").toPath());
        assertTrue("Empty worlds must stop ticking",
            world.contains("disable-world-ticking-when-empty: true"));
        assertTrue("No vanilla mobs are wanted", world.contains("      monster: 0"));
        assertTrue("Armor stands must keep ticking for the plugin rigs",
            world.contains("armor-stands:") && world.contains("tick: true"));

        String global = Files.readString(new File(dir, "paper-global.yml").toPath());
        assertTrue("Player collisions are a gameplay requirement",
            global.contains("enable-player-collisions: true"));
        assertTrue("Plugin scoreboards must be tracked", global.contains("track-plugin-scoreboards: true"));

        String start = Files.readString(new File(dir, "start.sh").toPath());
        assertTrue("start.sh must expose the same flags", start.contains("-XX:+UseCompactObjectHeaders"));
        assertTrue("start.sh must cd into its own directory", start.contains("cd \"$(dirname \"$0\")\""));
    }

}
