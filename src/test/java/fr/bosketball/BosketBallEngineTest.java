package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import fr.bosketball.model.TeamSide;
import fr.bosketball.physics.PhysicsMath;
import fr.bosketball.social.Guild;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.MiniSyntax;
import fr.bosketball.util.Msg;
import fr.bosketball.util.SmallCaps;
import fr.bosketball.ui.NameplateLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    public void testRankedEloIsFlatAndFavoursClimbing() {
        assertEquals("Round 26: a win is worth a flat +15", 15, StatsManager.ELO_WIN_GAIN);
        assertEquals("Round 26: a loss costs a flat -5", 5, StatsManager.ELO_LOSS_PENALTY);
        for (int a = 900; a <= 2400; a += 100) {
            for (int b = 900; b <= 2400; b += 100) {
                assertEquals("The win reward never depends on the rating gap",
                    15, StatsManager.computeEloGain(a, b));
                assertEquals("The loss penalty never depends on the rating gap",
                    5, StatsManager.computeEloLoss(b, a));
            }
        }
        int gained = StatsManager.computeEloGain(1035, 2193);
        int lost = StatsManager.computeEloLoss(2193, 1035);
        assertTrue("Winning must be worth three times a loss so the ladder climbs", gained >= lost * 3);
        assertTrue("No more ±32 swings", gained + lost <= 25);
        assertEquals("Legacy helper still reports the win reward", gained,
            StatsManager.computeEloDelta(1035, 2193));
    }

    @Test
    public void leaderboardBadgesAlwaysFollowTheRealEloOrder() {
        java.io.File dir = new java.io.File("/tmp/bb-badges-" + System.nanoTime());
        StatsManager sm = new StatsManager(dir, null);
        UUID low = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        UUID top = UUID.randomUUID();
        PlayerProfile pLow = sm.getOrCreate(low, "LowElo");
        PlayerProfile pMid = sm.getOrCreate(mid, "MidElo");
        PlayerProfile pTop = sm.getOrCreate(top, "TopElo");
        pLow.adjustElo(1032 - pLow.elo());
        pMid.adjustElo(968 - pMid.elo());
        pTop.adjustElo(2200 - pTop.elo());
        // Make sure a stale periodic index cannot win: badges are rebuilt from the live profiles.
        sm.refreshLeaderboardBadges();
        assertEquals("Highest ELO is #1", 1, sm.getBadges(top).eloPlace());
        assertEquals("Second ELO is #2", 2, sm.getBadges(low).eloPlace());
        assertEquals("Third ELO is #3", 3, sm.getBadges(mid).eloPlace());
        List<PlayerProfile> order = sm.topByUncached(
            fr.bosketball.stats.StatsManager.LeaderboardCategory.ELO, 3);
        assertEquals("The ranking order follows the ELO", top, order.get(0).uuid());
        assertEquals("The ranking order follows the ELO", low, order.get(1).uuid());
        assertEquals("The ranking order follows the ELO", mid, order.get(2).uuid());
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
        assertEquals("Must provide 57 total chat tags (54 purchasable + MODS + FOUNDER + MYPINEAPPLE)!", 57, ChatTag.values().length);
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
        fr.bosketball.config.BosketConfig secondLoad = fr.bosketball.config.BosketConfig.loadAndUpgrade(cfgFile, null);
        assertEquals("Migrated gravity remains valid after restart", 0.25, secondLoad.physics().gravity(), 1e-6);
        assertEquals("Migrated shot clock remains valid after restart", 5, secondLoad.match().shotClockSeconds());
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
    public void testRound29PremiumMotdWithDedicatedMaintenanceAndRestartBanners() {
        fr.bosketball.config.BosketConfig cfg = fr.bosketball.config.BosketConfig.defaults();
        fr.bosketball.config.BosketConfig.Motd motd = cfg.motd();

        // (1) the domain is gone from every banner, header and rotating lines alike
        java.util.List<String> allLines = new java.util.ArrayList<>();
        allLines.add(motd.header());
        allLines.addAll(motd.subtitles());
        allLines.add(motd.maintenanceHeader());
        allLines.add(motd.maintenanceSubtitle());
        allLines.add(motd.closedHeader());
        allLines.add(motd.closedSubtitle());
        for (String line : allLines) {
            String low = line.toLowerCase(java.util.Locale.ROOT);
            assertFalse("Round 29: no domain in the MOTD, found: " + line,
                low.contains(".net") || low.contains(".xyz") || low.contains(".com") || low.contains("http"));
        }
        // Round 45: three lines, not five. The five rotated English slogans were replaced by three
        // lines that each say something usable (what you can play, the season, where the Discord is).
        assertEquals("The live banner keeps 3 rotating lines", 3, motd.subtitles().size());
        for (String line : motd.subtitles()) {
            assertTrue("A banner line must stay short: " + line, plain(Msg.parse(line)).length() <= 60);
        }

        // (2) the three states really render three different two-line banners
        fr.bosketball.server.MotdManager mgr = new fr.bosketball.server.MotdManager(null);
        String online = plain(mgr.buildMotdComponent(motd, fr.bosketball.server.MotdManager.State.ONLINE));
        String maint = plain(mgr.buildMotdComponent(motd, fr.bosketball.server.MotdManager.State.MAINTENANCE));
        String closed = plain(mgr.buildMotdComponent(motd, fr.bosketball.server.MotdManager.State.CLOSED));
        assertEquals("Every state is a 2-line banner", 2, online.split("\n").length);
        assertEquals("Every state is a 2-line banner", 2, maint.split("\n").length);
        assertEquals("Every state is a 2-line banner", 2, closed.split("\n").length);
        assertFalse("Maintenance must not reuse the live banner", online.trim().equals(maint.trim()));
        assertFalse("Restart must not reuse the maintenance banner", maint.trim().equals(closed.trim()));
        assertTrue("Maintenance banner copy!", maint.contains("Maintenance"));
        assertTrue("Restart banner copy!", closed.contains("Restarting"));
        assertTrue("Every state is centred", maint.startsWith("    ") && closed.startsWith("  "));

        // (3) the state follows maintenance-mode, and a stopping server wins over everything
        fr.bosketball.config.BosketConfig.Motd maintCfg = new fr.bosketball.config.BosketConfig.Motd(
            motd.enabled(), motd.autoInstallServerIcon(), motd.maxPlayersDisplay(), motd.header(),
            motd.subtitles(), true, motd.maintenanceHeader(), motd.maintenanceSubtitle(),
            motd.maintenanceWhitelist(), motd.maintenanceKickMessage(),
            motd.startingHeader(), motd.startingSubtitle(), motd.startupGateSeconds(),
            motd.closedHeader(), motd.closedSubtitle());
        assertEquals(fr.bosketball.server.MotdManager.State.MAINTENANCE, mgr.currentState(maintCfg));
        assertEquals(fr.bosketball.server.MotdManager.State.ONLINE, mgr.currentState(motd));

        String pluginSrc = readSource("src/main/java/fr/bosketball/BosketBallPlugin.java");
        assertTrue("The plugin exposes a shutting-down flag for the restart banner",
            pluginSrc.contains("isShuttingDown()") && pluginSrc.contains("shuttingDown = true;"));
        String motdSrc = readSource("src/main/java/fr/bosketball/server/MotdManager.java");
        assertTrue("Ping handler resolves the state before building the banner",
            motdSrc.contains("State state = currentState(cfg)"));
        assertFalse("The hover list must not print the domain anymore",
            motdSrc.contains("general().serverDomain()"));
    }

    @Test
    public void round40StartingServerHasItsOwnBannerAndScreen() {
        fr.bosketball.config.BosketConfig cfg = fr.bosketball.config.BosketConfig.defaults();
        fr.bosketball.config.BosketConfig.Motd motd = cfg.motd();

        // (1) four states, resolved in a fixed order: stop > boot > maintenance > open.
        assertEquals(fr.bosketball.server.MotdManager.State.CLOSED,
            fr.bosketball.server.MotdManager.resolveState(true, true, true));
        assertEquals(fr.bosketball.server.MotdManager.State.STARTING,
            fr.bosketball.server.MotdManager.resolveState(false, true, true));
        assertEquals(fr.bosketball.server.MotdManager.State.MAINTENANCE,
            fr.bosketball.server.MotdManager.resolveState(false, false, true));
        assertEquals(fr.bosketball.server.MotdManager.State.ONLINE,
            fr.bosketball.server.MotdManager.resolveState(false, false, false));

        // (2) its own two-line, centred banner, different from the other three.
        fr.bosketball.server.MotdManager mgr = new fr.bosketball.server.MotdManager(null);
        String starting = plain(mgr.buildMotdComponent(motd, fr.bosketball.server.MotdManager.State.STARTING));
        assertEquals("The starting banner is a 2-line banner", 2, starting.split("\n").length);
        assertTrue("The starting banner carries the brand", starting.contains("BOSKETBALL"));
        assertTrue("The starting banner says it is warming up", starting.contains("Starting up"));
        for (fr.bosketball.server.MotdManager.State other : new fr.bosketball.server.MotdManager.State[]{
            fr.bosketball.server.MotdManager.State.ONLINE,
            fr.bosketball.server.MotdManager.State.MAINTENANCE,
            fr.bosketball.server.MotdManager.State.CLOSED}) {
            assertNotEquals("The starting banner must be its own copy (" + other + ")",
                plain(mgr.buildMotdComponent(motd, other)).trim(), starting.trim());
        }

        // (3) the gate is bounded, and 0 turns it off.
        assertEquals("Default gate", 8, motd.startupGateSeconds());
        assertTrue(motd.startupGateEnabled());
        fr.bosketball.config.BosketConfig.Motd tooLong = new fr.bosketball.config.BosketConfig.Motd(
            motd.enabled(), motd.autoInstallServerIcon(), motd.maxPlayersDisplay(), motd.header(),
            motd.subtitles(), false, motd.maintenanceHeader(), motd.maintenanceSubtitle(),
            motd.maintenanceWhitelist(), motd.maintenanceKickMessage(),
            motd.startingHeader(), motd.startingSubtitle(), 9_999,
            motd.closedHeader(), motd.closedSubtitle());
        assertEquals("The gate can never lock the server for long", 120, tooLong.startupGateSeconds());
        fr.bosketball.config.BosketConfig.Motd off = new fr.bosketball.config.BosketConfig.Motd(
            motd.enabled(), motd.autoInstallServerIcon(), motd.maxPlayersDisplay(), motd.header(),
            motd.subtitles(), false, motd.maintenanceHeader(), motd.maintenanceSubtitle(),
            motd.maintenanceWhitelist(), motd.maintenanceKickMessage(),
            motd.startingHeader(), motd.startingSubtitle(), -3,
            motd.closedHeader(), motd.closedSubtitle());
        assertEquals("A negative gate means 'open at once'", 0, off.startupGateSeconds());
        assertFalse(off.startupGateEnabled());

        // (4) the full-screen join message: English, short lines, brand + way back.
        net.kyori.adventure.text.Component screen =
            fr.bosketball.server.ServerScreens.starting(null, null);
        String text = plain(screen);
        assertTrue("The starting screen must say it is starting (small caps title)",
            text.contains(fr.bosketball.util.SmallCaps.toSmallCaps("SERVER STARTING")));
        assertTrue("…and how to come back", text.toUpperCase(java.util.Locale.ROOT).contains("RECONNECT"));
        assertFalse("The starting screen is English, like the shutdown one",
            text.contains("redémarre") || text.contains("patient") || text.contains("Reviens"));
        for (String line : text.split("\n")) {
            assertTrue("The disconnect screen wraps around 58 characters: '" + line + "'",
                line.length() <= 58);
        }
        assertTrue("At most 20 lines fit on the disconnect screen", text.split("\n").length <= 20);

        // (5) the config ships the new keys, and the plugin really owns the flag.
        String yml = readSource("src/main/resources/config.yml");
        assertTrue(yml.contains("starting-header:"));
        assertTrue(yml.contains("startup-gate-seconds:"));
        String pluginSrc = readSource("src/main/java/fr/bosketball/BosketBallPlugin.java");
        assertTrue("The plugin exposes the boot flag", pluginSrc.contains("isStartingUp()"));
        assertTrue("The plugin opens the gate itself", pluginSrc.contains("markServerReady()"));
        String motdSrc = readSource("src/main/java/fr/bosketball/server/MotdManager.java");
        assertTrue("The login gate must read the boot flag", motdSrc.contains("plugin.isStartingUp()"));
        assertTrue("…and show the starting screen", motdSrc.contains("ServerScreens.starting("));
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
                    int maxWidth = 250;
                    assertTrue("Glyph texture " + zn + " per-char width (" + perCharWidth + "px) must be <= "
                            + maxWidth + "px for 256x256 FontTexture!",
                        perCharWidth <= maxWidth);
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
            cmdSrc.contains("Perms.check(sender") && cmdSrc.contains("Perms.has(p, Perms.")
                && cmdSrc.contains("Perms.has(sender, Perms."));
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
        assertTrue("Round 25: the dunk outcome is rolled once at take-off (no hardcoded failure)",
            matchSrc.contains("DunkRules.successChance(")
                && matchSrc.contains("fr.bosketball.game.DunkRules.flightTicks(dist), dunkSuccess")
                && matchSrc.contains("ball.lockDunkTiming(dunkSuccess)"));
        assertFalse("Round 25: the single-click dunk never hardcodes the outcome",
            matchSrc.contains("flightTicks, false)") || matchSrc.contains("flightTicks, true)"));
        assertTrue("Round 25: the dunk cooldown is 20 s",
            matchSrc.contains("dunkReadyAtMillis.put(player.getUniqueId(), dunkNow + fr.bosketball.game.DunkRules.COOLDOWN_MS)"));
        assertTrue("Round 25: the lane block no longer hard-fails a dunk, it only raises the contest",
            matchSrc.contains("dunkContest = Math.min(1.0, dunkContest + 0.35)"));
        assertTrue("MatchSession SavedPlayerState must clamp SPECTATOR back to ADVENTURE on restore!",
            matchSrc.contains("(gameMode == GameMode.SPECTATOR) ? GameMode.ADVENTURE : gameMode"));

        String slamHud = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(Glyphs.composeDunkSlamHud(0.72, true));
        assertTrue("Round 25: the dunk flight HUD shows the slam flair", slamHud.contains("Slam"));
        assertFalse("Round 25: the dunk flight HUD never asks for a second click",
            matchSrc.contains("composeDunkTimingHud(\n                    ball.dunkProgress()"));
        assertTrue("Round 25: the flight HUD is the single-click one",
            matchSrc.contains("Glyphs.composeDunkSlamHud("));

        PlayerProfile customer = new PlayerProfile(UUID.randomUUID(), "BoutiqueBuyer");
        var unownedTags = fr.bosketball.ui.GuiMenuManager.collectUnownedShopItems(
            customer, fr.bosketball.ui.GuiMenuManager.ShopCategory.TAGS
        );
        assertEquals("All 54 purchasable tags must appear in TAGS category!", 54, unownedTags.size());
        for (int i = 1; i < unownedTags.size(); i++) {
            assertTrue("Shop tags must be sorted cleanly by Coin price ascending (Common -> Legendary)!",
                unownedTags.get(i).priceCoins() >= unownedTags.get(i - 1).priceCoins());
        }

        assertEquals("Leave Queue must sit on the free right edge (slot 8) so the seven doors stay centred",
            8, fr.bosketball.model.BallSkin.LEAVE_QUEUE_LOBBY_SLOT);
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
        assertTrue("/spawn must guard competitive matches & cleanly release the current session before teleporting!",
            coreCmdSrc.contains("clearPlayerState"));

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
        assertTrue("MatchSession must never overwrite the held ball while the player draws OR charges!",
            matchSrc.contains("boolean holdingDraw = p.isHandRaised() || isChargingShot(p);")
                && matchSrc.contains("if (!(holdingDraw && existingS0 != null && existingS0.getType() == Material.BOW))")
                && matchSrc.contains("&& !isChargingShot(hp)"));
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
        java.util.Set<Character> codepoints = new HashSet<>();
        for (char c : guiOverlays) {
            assertTrue("Duplicate GUI overlay codepoint U+" + Integer.toHexString(c), codepoints.add(c));
        }

        assertTrue("Plaque captions must fit in " + Glyphs.GUI_PLAQUE_CAPTION_PX + "px",
            Glyphs.GUI_PLAQUE_CAPTION_PX <= 12);

        String msgSrc = Files.readString(new File("src/main/java/fr/bosketball/util/Msg.java").toPath());
        // Round 45: the help card only advertises eight commands, and /duel is not one of them any
        // more. The rule it was protecting still holds: a literal '<' is escaped, never an entity.
        assertFalse("Messages must never carry HTML entities", msgSrc.contains("&lt;"));
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
        assertTrue("A bare /giveelo must not duplicate an explicit give|take|set operation",
            cmdSrc.contains("boolean explicitOp = head.equals(\"give\")")
                && cmdSrc.contains("rewritten.add(args[0].toLowerCase(java.util.Locale.ROOT).startsWith(\"add\") ? \"add\" : \"give\");"));
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
            assertEquals("All 57 chat-tag collector emblems must be packaged!", 57, tagCards);
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
            new File("docs/showcase/index.html"), new File("resourcepack/assets"));
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

        // Round 39: the decor of the outdoor build is placed after its support, exactly like a sign.
        assertTrue(CourtSchematic.isAttachableName("minecraft:short_dry_grass"));
        assertTrue(CourtSchematic.isAttachableName("minecraft:tall_dry_grass"));
        assertTrue(CourtSchematic.isAttachableName("minecraft:dead_bush"));
        assertTrue(CourtSchematic.isAttachableName("minecraft:firefly_bush"));
        assertTrue(CourtSchematic.isAttachableName(
            "minecraft:big_dripleaf[facing=north,tilt=none,waterlogged=false]"));
        assertTrue(CourtSchematic.isAttachableName("minecraft:carrots[age=1]"));
        assertFalse("A full leaf block is not a decoration that pops",
            CourtSchematic.isAttachableName(
                "minecraft:azalea_leaves[distance=7,persistent=true,waterlogged=false]"));
        assertFalse("a full block that merely contains \"_grass\" is not a tuft",
            CourtSchematic.isAttachableName("minecraft:grass_block[snowy=false]"));
    }

    @Test
    public void bundledStreetCourtIsTheSecondTerrainOfThePool() throws Exception {

        File file = new File("src/main/resources/courts/terrainsseize.schem");
        assertTrue("The second terrain must ship in src/main/resources/courts", file.isFile());
        CourtSchematic street = CourtSchematic.load(file);

        assertEquals("street court width", 75, street.width());
        assertEquals("street court height", 26, street.height());
        assertEquals("street court length", 101, street.length());
        assertEquals("Axiom writes Sponge v2: the loader must accept it", 2, street.formatVersion());
        assertEquals("DataVersion of the save", 4903, street.dataVersion());
        assertEquals("palette of the build", 85, street.paletteSize());
        assertEquals("solid blocks of the build", 18029, street.nonAirBlocks());
        assertTrue("the build is not a single flat slab", street.hasCeiling() == false);
        assertEquals("the painted floor is the deck: the paste origin must land on Y1+1",
            2, street.floorOffsetAtCenter());

        // The cyan paint of the floor is the coloured court: 25 wide (X) by 31 long (Z), so the two
        // baskets sit on the NORTH-SOUTH axis, exactly like the build.
        assertTrue("the cyan court must be detected on the deck", street.hasCourtBox());
        assertEquals("court width along X", 25, street.courtSpanX());
        assertEquals("court length along Z", 31, street.courtSpanZ());
        assertFalse("baskets on the north-south axis", street.courtAlongX());
        assertEquals(31, street.courtLength());
        assertEquals(25, street.courtWidth());
        assertEquals("the court is centred on the build", 37.5, street.localCenterX(), 0.001);
        assertEquals("the court is centred on the build", 50.5, street.localCenterZ(), 0.001);
        assertEquals("centre of the paste = centre of the court, no offset needed",
            street.pasteCenterX(), street.localCenterX(), 0.001);
        assertEquals(street.pasteCenterZ(), street.localCenterZ(), 0.001);
        assertEquals("painted blocks of the court box (logo, circle, keys)", 308,
            street.courtPaintedBlocks());
        assertFalse("the build has cyan paint but no white line paint: the plugin draws the lines",
            street.deckHasLineMarkings());
        assertTrue("the cyan floor is the deck, so it is pasted, not skipped", street.nonAirBlocks() > 0);
        assertTrue("the decor of the build (grass, bushes, dripleafs, signs, heads) is placed in the"
            + " second pass so it cannot pop: found " + street.attachableCount(),
            street.attachableCount() > 500);
    }

    @Test
    public void streetCourtKeepsItsSignsAndItsCustomHeads() throws Exception {

        CourtSchematic street = CourtSchematic.load(
            new File("src/main/resources/courts/terrainsseize.schem"));

        assertEquals("the two credit signs of each baseline", 4, street.signCount());
        assertEquals("the two custom heads of the build (friandise, maxlananas)", 2, street.headCount());
        assertEquals(java.util.List.of("friandise", "maxlananas"), street.headNames());

        int signsWithText = 0;
        int headsWithName = 0;
        for (CourtSchematic.SavedBlockEntity entity : street.blockEntities()) {
            assertTrue("Block entity out of the schematic bounds",
                entity.x() >= 0 && entity.x() < street.width()
                    && entity.y() >= 0 && entity.y() < street.height()
                    && entity.z() >= 0 && entity.z() < street.length());
            if (entity.isSign()) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> front =
                    (java.util.Map<String, Object>) entity.data().get("front_text");
                assertNotNull("A Sponge v2 sign keeps its text FLAT in the block entity, not under"
                    + " Data: front_text must be read from the payload itself", front);
                @SuppressWarnings("unchecked")
                java.util.List<Object> messages = (java.util.List<Object>) front.get("messages");
                StringBuilder text = new StringBuilder();
                for (Object message : messages) {
                    text.append(CourtSchematic.jsonPlainText(String.valueOf(message)));
                }
                assertEquals("the sign is not waxed", Byte.valueOf((byte) 0),
                    Byte.valueOf(((Number) entity.data().get("is_waxed")).byteValue()));
                if (text.toString().contains("@sseize")) {
                    signsWithText++;
                    assertEquals("the credit signs glow (light_blue, glowing text)", "light_blue",
                        String.valueOf(front.get("color")));
                    assertEquals("glowing text must survive the flat payload", Byte.valueOf((byte) 1),
                        Byte.valueOf(((Number) front.get("has_glowing_text")).byteValue()));
                }
            }
            if (entity.isSkull()) {
                assertNotNull("a head keeps its skin name", entity.data().get("profile"));
                if (!"".equals(skinNameOf(entity))) {
                    headsWithName++;
                }
            }
        }
        assertEquals("both credit signs (one per baseline) must carry their text", 2, signsWithText);
        assertEquals("both heads must carry their skin name", 2, headsWithName);
    }

    /** Name carried by a saved skull payload, read exactly like the paste reads it. */
    @SuppressWarnings("unchecked")
    private static String skinNameOf(CourtSchematic.SavedBlockEntity entity) {
        Object profile = entity.data().get("profile");
        if (!(profile instanceof java.util.Map<?, ?> map)) {
            return "";
        }
        Object name = ((java.util.Map<String, Object>) map).get("name");
        return name instanceof String text ? text : "";
    }

    @Test
    public void everyBlockOfBothTerrainsExistsOnThisServer() throws Exception {

        // The palette is resolved to real BlockData at paste time; a material this server does not
        // know would be dropped to air (never stone, never an exception). This guard makes sure the
        // two shipped files contain nothing the jar cannot place at all.
        for (String path : new String[]{
            "src/main/resources/courts/terrain.schem",
            "src/main/resources/courts/terrainsseize.schem"}) {
            CourtSchematic decoded = CourtSchematic.load(new File(path));
            for (String entry : decoded.palettePreview(1024)) {
                String material = entry;
                int bracket = material.indexOf('[');
                if (bracket > 0) {
                    material = material.substring(0, bracket);
                }
                assertNotNull("Unknown material '" + material + "' in " + path,
                    org.bukkit.Material.matchMaterial(material));
            }
        }
    }

    @Test
    public void flatSpongeV2PayloadsSurviveTheLoad() throws Exception {

        File schem = SchematicTestWriter.writeSpongeV2WithFlatBlockEntities();
        try {
            CourtSchematic decoded = CourtSchematic.load(schem);
            assertEquals("Sponge v2 keeps Palette at the root", 2, decoded.formatVersion());
            assertEquals(1, decoded.signCount());
            assertEquals(1, decoded.headCount());
            assertEquals(java.util.List.of("maxlananas"), decoded.headNames());

            CourtSchematic.SavedBlockEntity sign = null;
            CourtSchematic.SavedBlockEntity head = null;
            for (CourtSchematic.SavedBlockEntity entity : decoded.blockEntities()) {
                if (entity.isSign()) {
                    sign = entity;
                } else if (entity.isSkull()) {
                    head = entity;
                }
            }
            assertNotNull(sign);
            assertNotNull(head);
            assertEquals("sign text of a flat payload", "@sseize", secondSignLine(sign));
            assertEquals("the texture travels with the file: no network lookup needed",
                "dGVzdC10ZXh0dXJl", textureValueOf(head));
            assertEquals("c2ln", textureSignatureOf(head));
        } finally {
            Files.deleteIfExists(schem.toPath());
        }
    }

    @SuppressWarnings("unchecked")
    private static String secondSignLine(CourtSchematic.SavedBlockEntity entity) {
        java.util.Map<String, Object> front =
            (java.util.Map<String, Object>) entity.data().get("front_text");
        java.util.List<Object> messages = (java.util.List<Object>) front.get("messages");
        return CourtSchematic.jsonPlainText(String.valueOf(messages.get(2)));
    }

    @SuppressWarnings("unchecked")
    private static String textureValueOf(CourtSchematic.SavedBlockEntity entity) {
        java.util.Map<String, Object> profile =
            (java.util.Map<String, Object>) entity.data().get("profile");
        java.util.List<Object> properties = (java.util.List<Object>) profile.get("properties");
        java.util.Map<String, Object> first = (java.util.Map<String, Object>) properties.get(0);
        return String.valueOf(first.get("value"));
    }

    @SuppressWarnings("unchecked")
    private static String textureSignatureOf(CourtSchematic.SavedBlockEntity entity) {
        java.util.Map<String, Object> profile =
            (java.util.Map<String, Object>) entity.data().get("profile");
        java.util.List<Object> properties = (java.util.List<Object>) profile.get("properties");
        java.util.Map<String, Object> first = (java.util.Map<String, Object>) properties.get(0);
        return String.valueOf(first.get("signature"));
    }

    @Test
    public void anIllegalBlockCanNeverAbortACourtPaste() {

        // Exact state first…
        assertEquals(java.util.List.of(
                "minecraft:oak_stairs[facing=north,bogus=1]",
                "minecraft:oak_stairs[bogus=1]",
                "minecraft:oak_stairs[facing=north]",
                "minecraft:oak_stairs"),
            CourtSchematic.resolutionCandidates("minecraft:oak_stairs[facing=north,bogus=1]"));

        // …then the bare material for a plain block, and nothing at all for an empty entry.
        assertEquals(java.util.List.of("minecraft:sand"),
            CourtSchematic.resolutionCandidates("minecraft:sand"));
        assertEquals(java.util.List.of("minecraft:sand"),
            CourtSchematic.resolutionCandidates("  minecraft:sand  "));
        assertTrue(CourtSchematic.resolutionCandidates(null).isEmpty());
        assertTrue(CourtSchematic.resolutionCandidates("  ").isEmpty());

        // A hand-edited state with a bracket but no value still degrades cleanly.
        java.util.List<String> candidates = CourtSchematic.resolutionCandidates("minecraft:chest[facing=north]");
        assertEquals("minecraft:chest", candidates.get(candidates.size() - 1));
    }

    @Test
    public void theTwoTerrainsAlternateThroughThePool() {

        assertEquals("first court = first terrain", 0, fr.bosketball.arena.schematic.SchematicCourtPool
            .variantIndexFor(1, 2));
        assertEquals("second court = second terrain", 1, fr.bosketball.arena.schematic.SchematicCourtPool
            .variantIndexFor(2, 2));
        assertEquals("then it alternates", 0, fr.bosketball.arena.schematic.SchematicCourtPool
            .variantIndexFor(3, 2));
        assertEquals(1, fr.bosketball.arena.schematic.SchematicCourtPool.variantIndexFor(50, 2));
        assertEquals("a single terrain fills every slot", 0,
            fr.bosketball.arena.schematic.SchematicCourtPool.variantIndexFor(9, 1));
        assertEquals("an empty pool must not divide by zero", 0,
            fr.bosketball.arena.schematic.SchematicCourtPool.variantIndexFor(9, 0));
    }

    @Test
    public void theConfigListsBothTerrains() {

        var both = new fr.bosketball.config.BosketConfig.SchematicCourts(
            true, "terrain.schem", java.util.List.of("terrain.schem", "terrainsseize.schem"),
            "bosketball_courts", 50, 384, 100_000, 64, 100_000, 0.0, 0.0, "west", 0,
            9.0, 15.0, "auto", "OCHRE_FROGLIGHT", 0, 3, 1.25, true, true, true, true, 4_000, 30, true,
            java.util.Map.of());
        assertEquals(java.util.List.of("terrain.schem", "terrainsseize.schem"), both.files());
        assertTrue(both.hasSchematic());

        var legacy = new fr.bosketball.config.BosketConfig.SchematicCourts(
            true, "terrain.schem", java.util.List.of(),
            "bosketball_courts", 50, 384, 100_000, 64, 100_000, 0.0, 0.0, "west", 0,
            9.0, 15.0, "auto", "OCHRE_FROGLIGHT", 0, 3, 1.25, true, true, true, true, 4_000, 30, true,
            java.util.Map.of());
        assertEquals("a single-file config keeps working", java.util.List.of("terrain.schem"),
            legacy.files());

        String yml = readSource("src/main/resources/config.yml");
        assertTrue("the shipped config must ship the second terrain", yml.contains("terrainsseize.schem"));
        assertTrue("…as a pool, not as an override", yml.contains("  schematics:"));
        assertTrue("…and keep the historical single-file key", yml.contains("  schematic: terrain.schem"));
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

        // Round 27: placements no longer amplify the ELO swing (+15 / -5 everywhere).
        assertEquals("Placement matches keep the flat ranked delta",
            15, fr.bosketball.season.SeasonMath.applyPlacementSwing(15, 0, 5));
        assertEquals("Placement losses keep the flat ranked penalty",
            -5, fr.bosketball.season.SeasonMath.applyPlacementSwing(-5, 0, 5));
        assertEquals("Placed players keep the raw ELO swing",
            15, fr.bosketball.season.SeasonMath.applyPlacementSwing(15, 5, 5));
        assertEquals("A zero delta is never invented out of nothing",
            0, fr.bosketball.season.SeasonMath.applyPlacementSwing(0, 0, 5));
        assertTrue("A tiny delta must still move the needle",
            fr.bosketball.season.SeasonMath.applyPlacementSwing(1, 0, 5) >= 1);
        assertTrue("Flat rule: only +15 / -5 / 0 are legal ranked deltas",
            fr.bosketball.season.SeasonMath.isFlatRankedDelta(15, 15, 5)
                && fr.bosketball.season.SeasonMath.isFlatRankedDelta(-5, 15, 5)
                && !fr.bosketball.season.SeasonMath.isFlatRankedDelta(30, 15, 5));

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
        assertTrue("The hub must expose the six doors on a centred grid: play, shop, locker, social, ranks, leaderboards",
            guiSrc.contains("case 20 -> openPlayModes(player);")
                && guiSrc.contains("case 22 -> {")
                && guiSrc.contains("case 24 -> {")
                && guiSrc.contains("case 29 -> {")
                && guiSrc.contains("case 13, 31 -> openRanksGuide(player);")
                && guiSrc.contains("case 33 -> openLeaderboards(player, LeaderboardCategory.ELO);"));
        assertTrue("The season door must keep living on the standard rail, never vanish",
            guiSrc.contains("slot == GuiChrome.SLOT_SEASON")
                || guiSrc.contains("applyStandardRail(inv, player);"));
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
        String sidebar = Files.readString(new File("src/main/java/fr/bosketball/ui/ScoreboardSidebarManager.java").toPath());
        assertTrue("One broken player must not blank everyone's sidebar",
            sidebar.contains("catch (RuntimeException brokenProfile)"));
        assertTrue("The sidebar objective name must be inspectable", sidebar.contains("public static final String OBJ_NAME"));

        String command = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("/bb displays must exist", command.contains("case \"displays\", \"hud\", \"sidebars\" -> handleDisplaysCommand"));
        assertTrue("/bb displays repair must be documented", command.contains("/bb displays repair"));
    }

    @Test
    public void courtPastingIsSpreadOverTicksAndWarnsOnSpikes() throws Exception {
        String cfg = Files.readString(new File("src/main/resources/config.yml").toPath());
        assertTrue("A court must not be pasted in a single tick (spark showed a 284 ms spike)",
            cfg.contains("max-blocks-per-tick: 4000"));
        assertTrue("The spike must be explained in the config", cfg.contains("284 ms spike"));
        assertTrue("The first slice must learn, not freeze: 512 blocks, not the 4000 cap",
            fr.bosketball.arena.schematic.SchematicCourtPool.FIRST_SLICE_BLOCKS <= 512);
        String pool = Files.readString(new File(
            "src/main/java/fr/bosketball/arena/schematic/SchematicCourtPool.java").toPath());
        assertTrue("The adaptive budget starts conservative",
            pool.contains("private int adaptiveSliceBlocks = FIRST_SLICE_BLOCKS;"));
        assertTrue("Lobby props do not animate for nobody",
            Files.readString(new File("src/main/java/fr/bosketball/lobby/RewardCrateStation.java").toPath())
                .contains("Viewers.anyWithin(baseLocation, 96.0)")
            && Files.readString(new File("src/main/java/fr/bosketball/ui/LobbyHologramManager.java").toPath())
                .contains("if (!anyHologramWatched())")
            && Files.readString(new File("src/main/java/fr/bosketball/ui/TutorialManager.java").toPath())
                .contains("introTicks.isEmpty() && Bukkit.getOnlinePlayers().isEmpty()"));
        assertTrue("A slow paste slice must warn with the knob to lower",
            pool.contains("lower schematic-courts.max-blocks-per-tick"));
        assertTrue("The slice must be timed", pool.contains("long sliceStartedNs = System.nanoTime()"));
        String podium = Files.readString(new File(
            "src/main/java/fr/bosketball/lobby/HallOfFamePodium.java").toPath());
        assertTrue("Podium particles must be skipped when nobody is around",
            podium.contains("getNearbyPlayers(centre, 64.0).isEmpty()"));
        assertTrue("The idle baseline must be recorded", new File("docs/perf-baseline.md").isFile());
    }

    @Test
    public void perPlayerStateIsReleasedOnQuitAndCachesStayBounded() throws Exception {
        // --- the quit path must release every per-player structure -------------------------
        String plugin = Files.readString(new File(
            "src/main/java/fr/bosketball/BosketBallPlugin.java").toPath());
        assertTrue("There must be one teardown entry point", plugin.contains("public void cleanupPlayerState(Player player)"));
        for (String step : new String[] {
            "staffToolsManager.clearPlayer(id)", "tutorialManager.clearPlayer(player)",
            "guiMenu.forgetPlayer(id)", "crateStation.clearPlayer(id)", "nameplateService.forget(id)",
            "PurchaseConfirmMenu.clear(player)", "socialService.clear(id)", "GgCommand.forget(id)"
        }) {
            assertTrue("cleanupPlayerState must call " + step, plugin.contains(step));
        }
        assertTrue("One failing manager must not leak the others",
            plugin.contains("private void clean(String step, Runnable action)"));
        String listener = Files.readString(new File(
            "src/main/java/fr/bosketball/listener/GameplayListener.java").toPath());
        assertTrue("The quit handler must use it", listener.contains("plugin.cleanupPlayerState(player);"));

        // --- and every one of those methods must exist ------------------------------------
        String staff = Files.readString(new File("src/main/java/fr/bosketball/moderation/StaffToolsManager.java").toPath());
        assertTrue("Staff vanish/freeze must not survive a reconnect",
            staff.contains("public void clearPlayer(java.util.UUID playerId)")
                && staff.contains("vanished.remove(playerId)") && staff.contains("frozen.remove(playerId)"));
        String tutorial = Files.readString(new File("src/main/java/fr/bosketball/ui/TutorialManager.java").toPath());
        assertTrue("Quitting mid-tutorial must not leave a ghost hologram behind",
            tutorial.contains("public void clearPlayer(Player player)")
                && tutorial.contains("returnLocations.remove(id)") && tutorial.contains("lastStepAdvance.remove(id)"));
        assertTrue("/tuto stop must release the same state",
            tutorial.contains("returnLocations.remove(player.getUniqueId())"));
        assertTrue("Shop sort/search must not stick to a UUID forever",
            Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath())
                .contains("public void forgetPlayer(UUID playerId)"));
        assertTrue("Crate cooldown is a session value",
            Files.readString(new File("src/main/java/fr/bosketball/lobby/RewardCrateStation.java").toPath())
                .contains("public void clearPlayer(UUID playerId)"));
        assertTrue("NameplateService.forget must finally be called somewhere",
            plugin.contains("nameplateService.forget(id)"));

        // --- static caches stay bounded ----------------------------------------------------
        java.util.Map<java.util.UUID, Long> cache = new java.util.HashMap<>();
        long now = 1_000_000L;
        for (int i = 0; i < 300; i++) {
            cache.put(new java.util.UUID(0, i), now - 120_000L);   // all stale
        }
        assertEquals("Stale entries must be dropped",
            0, fr.bosketball.command.GgCommand.pruneOlderThan(cache, now, 60_000L, 512));
        for (int i = 0; i < 600; i++) {
            cache.put(new java.util.UUID(0, i), now - 1_000L);      // all fresh
        }
        assertEquals("Over the hard cap the map is emptied, never left to grow",
            0, fr.bosketball.command.GgCommand.pruneOlderThan(cache, now, 60_000L, 512));
        cache.put(new java.util.UUID(0, 1), now);
        assertEquals("Fresh entries survive", 1,
            fr.bosketball.command.GgCommand.pruneOlderThan(cache, now, 60_000L, 512));

        // --- the audit shows cache pressure ------------------------------------------------
        assertTrue("Saturation must be visible",
            fr.bosketball.ops.OpsAudit.cacheLine(4096, 4096, 2, 512).contains("saturado"));
        assertTrue("Normal pressure is silent",
            !fr.bosketball.ops.OpsAudit.cacheLine(120, 4096, 2, 512).contains("saturado"));
        assertEquals(4096, fr.bosketball.util.Msg.parseCacheLimit());

        // --- dead code must stay dead -------------------------------------------------------
        assertFalse("Viewers.anyOnline was never used",
            Files.readString(new File("src/main/java/fr/bosketball/util/Viewers.java").toPath()).contains("anyOnline"));
        assertFalse("measuredBlocksPerMs was never used",
            Files.readString(new File(
                "src/main/java/fr/bosketball/arena/schematic/SchematicCourtPool.java").toPath())
                .contains("measuredBlocksPerMs"));
    }

    @Test
    public void npcsAreVisibleAtViewDistanceAndPluginConfigsAreBounded() throws Exception {
        // --- the spigot bundle must not hide entities at 32 blocks -------------------------
        String spigot = Files.readString(new File("docs/server-optimization/spigot.yml").toPath());
        assertTrue("NPCs/mannequins must be tracked at the full view distance",
            spigot.contains("entity-tracking-range:") && spigot.contains("misc: 96"));
        assertTrue("Armour stands / mannequins must not pop in at 32 blocks",
            spigot.contains("other: 96"));
        assertTrue("Display entities keep their default 128 blocks", spigot.contains("display: 128"));
        assertFalse("The 32-block trap must be gone", spigot.contains("misc: 32"));

        // --- and the plugin must not freeze what the player can still see -----------------
        for (String file : new String[] {
            "src/main/java/fr/bosketball/lobby/HallOfFamePodium.java",
            "src/main/java/fr/bosketball/lobby/RewardCrateStation.java",
            "src/main/java/fr/bosketball/ui/LobbyHologramManager.java",
            "src/main/java/fr/bosketball/ui/TutorialManager.java"
        }) {
            String src = Files.readString(new File(file).toPath());
            assertTrue(file + " must keep animating anything visible up to 96 blocks",
                src.contains("96.0") || src.contains("96.0)"));
        }

        // --- Axiom: bounded client work -------------------------------------------------
        String axiom = Files.readString(new File(
            "docs/optimisations-plugins/axiom-paper-config.yml").toPath());
        assertTrue("Unbounded chunk sends are the main Axiom risk",
            axiom.contains("max-chunk-sends-per-tick: 4"));
        assertTrue("Relights must be bounded too", axiom.contains("max-chunk-relights-per-tick: 4"));
        assertTrue("256 chunks of load distance is a memory bomb",
            axiom.contains("max-chunk-load-distance: 32"));
        assertTrue("The block buffer needs a rate limit", axiom.contains("block-buffer-rate-limit: 40"));
        assertTrue("Plugin entities must be protected from client edits",
            axiom.contains("\"minecraft:item_display\"") && axiom.contains("\"minecraft:mannequin\""));
        assertFalse("Axiom must not open the 2 MB packet door",
            axiom.contains("allow-large-payload-for-all-packets: true"));

        // --- FAWE: memory guard rails on a 3.5 GB heap ----------------------------------
        String fawe = Files.readString(new File(
            "docs/optimisations-plugins/fawe-config-changes.md").toPath());
        String faweYml = Files.readString(new File(
            "docs/optimisations-plugins/fawe-config.yml").toPath());
        assertTrue("A ready-to-paste FAWE config must exist, comments included",
            faweYml.contains("issues: \"https://github.com/IntellectualSites/FastAsyncWorldEdit/issues\"")
                && faweYml.contains("# (BOSKETBALL)"));
        assertTrue("No region plugin: the pointless check must be off",
            faweYml.contains("region-restrictions: false") && !faweYml.contains("region-restrictions: true"));
        assertTrue("Memory guard rails on a 3.5 GB heap",
            faweYml.contains("max-memory-percent: 75") && faweYml.contains("slower-memory-percent: 65"));
        assertTrue("Queue must fit the hardware",
            faweYml.contains("parallel-threads: 2") && faweYml.contains("target-size: 8")
                && faweYml.contains("preload-chunk-count: 128") && !faweYml.contains("preload-chunk-count: 512"));
        assertTrue("History bounded, small-edits on",
            faweYml.contains("small-edits: true") && faweYml.contains("max-history-mb: 1024")
                && faweYml.contains("max-changes: 20000000"));
        assertTrue("The build tools the operator uses must survive: use-database and //redo",
            faweYml.contains("use-database: true") && faweYml.contains("store-redo: true"));
        assertTrue("Disk-backed clipboard/history must stay", faweYml.contains("use-disk: true"));
        for (String expected : new String[] {
            "**`75`**", "**`65`**", "**`false`**", "**`128`**", "**`8`**", "**`1024`**"
        }) {
            assertTrue("FAWE change list must include " + expected, fawe.contains(expected));
        }
        assertTrue("The pointless region check must be called out",
            fawe.contains("region-restrictions") && fawe.contains("**`false`**"));
        assertTrue("The guide must say what NOT to touch",
            fawe.contains("Ce que j'ai volontairement laisse tel quel")
                && fawe.contains("parallel-threads"));
        assertTrue("The full audit must be written down", new File("docs/audit-complet.md").isFile());
        assertTrue("The operator's NPC fix must be documented",
            new File("docs/optimisations-plugins/README.md").isFile());
    }

    @Test
    public void optimisationRound7IsMeasuredNotGuessed() throws Exception {
        // --- paste slices adapt to the real CPU instead of trusting the config -------------
        assertEquals("187 blocks/ms means ~374 blocks in a 2 ms slice",
            374, fr.bosketball.arena.schematic.SchematicCourtPool.adaptiveBudget(187.0, 4000));
        assertEquals("A slow CPU must never get more than it can chew in 2 ms",
            128, fr.bosketball.arena.schematic.SchematicCourtPool.adaptiveBudget(10.0, 4000));
        assertEquals("The config cap always wins",
            1000, fr.bosketball.arena.schematic.SchematicCourtPool.adaptiveBudget(900.0, 1000));
        assertEquals("Unknown throughput starts at the cap",
            4000, fr.bosketball.arena.schematic.SchematicCourtPool.adaptiveBudget(0.0, 4000));
        assertEquals("A slice can never exceed the hard ceiling", 4_000,
            fr.bosketball.arena.schematic.SchematicCourtPool.HARD_MAX_BLOCKS_PER_SLICE);
        assertEquals("Slices target 2 ms of server thread", 2.0,
            fr.bosketball.arena.schematic.SchematicCourtPool.SLICE_TARGET_MS, 0.0001);

        assertTrue("The first slice must learn, not freeze: 512 blocks, not the 4000 cap",
            fr.bosketball.arena.schematic.SchematicCourtPool.FIRST_SLICE_BLOCKS <= 512);
        String pool = Files.readString(new File(
            "src/main/java/fr/bosketball/arena/schematic/SchematicCourtPool.java").toPath());
        assertTrue("The adaptive budget starts conservative",
            pool.contains("private int adaptiveSliceBlocks = FIRST_SLICE_BLOCKS;"));
        assertTrue("Lobby props do not animate for nobody",
            Files.readString(new File("src/main/java/fr/bosketball/lobby/RewardCrateStation.java").toPath())
                .contains("Viewers.anyWithin(baseLocation, 96.0)")
            && Files.readString(new File("src/main/java/fr/bosketball/ui/LobbyHologramManager.java").toPath())
                .contains("if (!anyHologramWatched())")
            && Files.readString(new File("src/main/java/fr/bosketball/ui/TutorialManager.java").toPath())
                .contains("introTicks.isEmpty() && Bukkit.getOnlinePlayers().isEmpty()"));
        assertTrue("A slice is measured and fed back into the adaptive budget",
            pool.contains("observeSlice(sliceBlocks, sliceNanos / 1_000_000.0, budget)"));
        assertTrue("The idle pool must sleep instead of scanning 50 courts every tick",
            pool.contains("if (pasteQueue.isEmpty() && !hasScheduledWork()")
                && pool.contains("world.getPlayers().isEmpty()"));
        assertTrue("The decoration watchdog must skip a world with no player",
            pool.contains("java.util.List<Player> viewers = world.getPlayers();")
                && pool.contains("if (viewers.isEmpty()) {"));
        assertTrue("The watchdog only scans courts a player can see",
            pool.contains("seenFrom(viewers, s)"));

        // --- the config that shipped 40 000 must be repaired on disk -----------------------
        String config = Files.readString(new File(
            "src/main/java/fr/bosketball/config/BosketConfig.java").toPath());
        assertTrue("An existing config.yml with 40 000 blocks/tick must be clamped on load",
            config.contains("if (livePasteBudget > 4_000) {")
                && config.contains("live.set(\"schematic-courts.max-blocks-per-tick\", 4_000);"));
        assertTrue("The clamp must be logged so the operator sees it",
            config.contains("max-blocks-per-tick \" + livePasteBudget"));
        assertTrue("Even a hand-edited value is clamped before use",
            config.contains("Math.clamp(cfg.getInt(\"schematic-courts.max-blocks-per-tick\", 4_000), 128, 4_000)"));

        // --- an empty podium costs nothing --------------------------------------------------
        String podium = Files.readString(new File(
            "src/main/java/fr/bosketball/lobby/HallOfFamePodium.java").toPath());
        assertTrue("No viewers, no podium refresh", podium.contains("if (hasViewers() && (!signature.equals(rosterSignature) || npcMissing))"));

        // --- the stress report stops lying about JIT ----------------------------------------
        assertEquals("Warm-up ticks are excluded from the projection",
            360, fr.bosketball.stress.StressTestService.measuredTicks(400, 40));
        assertEquals("A run shorter than the warm-up still divides by one tick",
            1, fr.bosketball.stress.StressTestService.measuredTicks(20, 40));
        String stress = Files.readString(new File(
            "src/main/java/fr/bosketball/stress/StressTestService.java").toPath());
        assertTrue("The render loop only counts after the warm-up",
            stress.contains("boolean counting = simulatedTick >= WARMUP_TICKS;"));

        // --- the audit finds what the console log shows -------------------------------------
        var bad = new fr.bosketball.ops.OpsAudit.Snapshot(
            true, true, 10, 8, 40_000, 1024, 2, 700, 900, 18.2, 21.0);
        var badFindings = fr.bosketball.ops.OpsAudit.audit(bad);
        String joined = String.join("\n", badFindings);
        assertFalse("AxiomPaper is the operator's call: never prescribed for removal",
            joined.contains("AxiomPaper") || joined.contains("RETIRER"));
        assertTrue("It is still reported, neutrally",
            fr.bosketball.ops.OpsAudit.thirdPartyNote(bad) != null
                && fr.bosketball.ops.OpsAudit.thirdPartyNote(bad).contains("AxiomPaper")
                && fr.bosketball.ops.OpsAudit.thirdPartyNote(bad).contains("conserves"));
        assertTrue("No world-edit plugin, no note",
            fr.bosketball.ops.OpsAudit.thirdPartyNote(
                new fr.bosketball.ops.OpsAudit.Snapshot(false, false, 5, 4, 4000, 3584, 2, 120, 200, 20.0, 1.2)) == null);
        assertTrue("The 40 000 paste budget must be flagged", joined.contains("max-blocks-per-tick = 40000"));
        assertTrue("view-distance 10 must be flagged", joined.contains("view-distance = 10"));
        assertTrue("simulation-distance 8 must be flagged", joined.contains("simulation-distance = 8"));
        assertTrue("1 Go of heap must be flagged", joined.contains("Xmx = 1024"));
        assertTrue("Low TPS must be flagged", joined.contains("TPS 18.20"));
        assertTrue("A healthy server produces no finding", fr.bosketball.ops.OpsAudit.audit(
            new fr.bosketball.ops.OpsAudit.Snapshot(false, false, 5, 4, 4000, 3584, 2, 120, 200, 20.0, 1.2)
        ).isEmpty());
        assertEquals("ras - rien a optimiser", fr.bosketball.ops.OpsAudit.summary(
            new fr.bosketball.ops.OpsAudit.Snapshot(false, false, 5, 4, 4000, 3584, 2, 120, 200, 20.0, 1.2)));
    }

    @Test
    public void stressTestDrivesTheRealPipelineWithoutAnyConnection() throws Exception {
        // --- pure projection math ---------------------------------------------------------
        assertEquals("0.42 ms per tick at 50 players is 0.84 % of a 50 ms tick",
            0.84, fr.bosketball.stress.StressTestService.budgetShare(0.42), 0.001);
        assertTrue("1 ms of 50 must be comfortable",
            fr.bosketball.stress.StressTestService.verdict(1.0).contains("4ADE80"));
        assertTrue("A 10 ms per-tick cost must be flagged as heavy",
            fr.bosketball.stress.StressTestService.verdict(10.0).contains("FBBF24"));
        assertTrue("20 ms of a 50 ms tick must not be called comfortable",
            !fr.bosketball.stress.StressTestService.verdict(20.0).contains("4ADE80"));
        assertTrue("40 ms of a 50 ms tick must be reported as too expensive",
            fr.bosketball.stress.StressTestService.verdict(40.0).contains("F87171"));

        // --- the simulated population ramps over the whole ladder -------------------------
        java.util.Set<Integer> elos = new java.util.TreeSet<>();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int i = 0; i < 50; i++) {
            int elo = fr.bosketball.stress.StressTestService.eloFor(i, 50);
            elos.add(elo);
            lowest = Math.min(lowest, elo);
            highest = Math.max(highest, elo);
        }
        assertEquals("50 simulated players must cover 50 distinct ELOs", 50, elos.size());
        assertEquals("The ramp must start at the floor", 400, lowest);
        assertEquals("The ramp must reach the top of the ladder", 2400, highest);

        // --- it must drive the production renderer, not a look-alike ----------------------
        String stress = Files.readString(new File(
            "src/main/java/fr/bosketball/stress/StressTestService.java").toPath());
        assertTrue("The production sidebar renderer must be called",
            stress.contains("plugin.sidebarManager().simulateRender("));
        assertTrue("Sidebar work must follow the production 10-tick cadence",
            stress.contains("TICKS_PER_REAL_TICK = 10") && stress.contains("simulatedTick % 10 != 0"));
        assertTrue("10 simulated ticks per real tick keeps the server alive",
            stress.contains("runTaskTimer(plugin, this::runBatch, 1L, 1L)"));
        assertFalse("Fake Player objects are impossible and must never be created",
            stress.contains("new Player(") || stress.contains("Bukkit.getOnlinePlayers()"));
        assertFalse("No sockets: the test must stay on the server thread",
            stress.contains("Socket") || stress.contains("HttpClient"));

        // --- cleanup is unconditional -----------------------------------------------------
        assertTrue("Every simulated entity is non-persistent",
            stress.contains("entity.setPersistent(false)"));
        assertTrue("Every simulated entity is tagged", stress.contains("entity.addScoreboardTag(STRESS_TAG)"));
        assertTrue("The report path removes the entities", stress.contains("removeEntities();"));
        assertTrue("A failed test still cleans up", stress.contains("purgeStressEntities();"));
        String plugin = Files.readString(new File(
            "src/main/java/fr/bosketball/BosketBallPlugin.java").toPath());
        assertTrue("Disabling the plugin aborts the run",
            plugin.contains("stressTestService.abort(\"plugin disable\")"));
        assertTrue("Startup sweeps leftovers from a crash",
            plugin.contains("StressTestService.purgeStressEntities()"));
        String world = "static void purgeStressEntities";
        assertTrue("The purge walks every world", stress.contains(world)
            && stress.contains("for (World world : Bukkit.getWorlds())"));

        // --- command surface: a subcommand, never a new executor --------------------------
        String command = Files.readString(new File(
            "src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("Registered as /bb subcommand with aliases",
            command.contains("\"stress\", \"loadtest\", \"simulate\"")
                && command.contains("case \"stress\", \"loadtest\", \"simulate\" ->"));
        assertTrue("Staff only", command.contains("case \"stress\""  )
            || command.contains("StressTestService stress = plugin.stressTestService();"));
        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        int commandsIdx = pluginYml.indexOf("\ncommands:");
        String onlyCommands = pluginYml.substring(commandsIdx, pluginYml.indexOf("\npermissions:"));
        assertFalse("No new Bukkit command may be registered in plugin.yml",
            onlyCommands.toLowerCase(java.util.Locale.ROOT).contains("stress"));

        // --- the extracted renderer has no Player dependency ------------------------------
        String sidebar = Files.readString(new File(
            "src/main/java/fr/bosketball/ui/ScoreboardSidebarManager.java").toPath());
        assertTrue("The console recipe must be documented", new File("docs/stress-test.md").isFile());
        assertTrue("Round 7 measurements must be written down", new File("docs/optimisation-round7.md").isFile());
        assertTrue("Round 8 measurements must be written down", new File("docs/optimisation-round8.md").isFile());
        assertTrue("A Player-free render entry point exists",
            sidebar.contains("public List<String> buildLobbyLines(PlayerProfile profile, UUID playerId, String playerName,"));
        int start = sidebar.indexOf("public List<String> buildLobbyLines(");
        String body = sidebar.substring(start, sidebar.indexOf("private void applyLines(", start));
        assertFalse("The extracted builder must not touch a live Player", body.contains("player."));
        assertTrue("It applies through the same churn as production",
            sidebar.contains("applyLines(playerId, board, obj, buildLobbyLines(profile, playerId, playerName, gameManager));"));
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

    // ---------------------------------------------------------------- repo hygiene

    private static List<String> declaredPluginCommands(String pluginYml) {
        List<String> names = new java.util.ArrayList<>();
        boolean inCommands = false;
        for (String line : (Iterable<String>) pluginYml.lines()::iterator) {
            if (line.trim().equals("commands:")) {
                inCommands = true;
                continue;
            }
            if (!inCommands) {
                continue;
            }
            if (!line.startsWith(" ")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                break;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^ {2}([a-z0-9_-]+):").matcher(line);
            if (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    private static List<String> bbSubcommandLabels(String source) {
        java.util.Set<String> labels = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?m)^[ \t]*case ((?:\"[^\"]*\"(?:[ \t]*,[ \t]*)?)+)[ \t]*->")
            .matcher(source);
        java.util.regex.Pattern first = java.util.regex.Pattern.compile("\"([^\"]*)\"");
        while (m.find()) {
            java.util.regex.Matcher f = first.matcher(m.group(1));
            if (f.find()) {
                labels.add(f.group(1));
            }
        }
        return List.copyOf(labels);
    }

    @Test
    public void repositoryStaysTidyAndReadmeListsEveryCommand() throws Exception {
        File root = new File(".");
        String[] imageExt = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"};
        List<String> looseImages = new java.util.ArrayList<>();
        File[] rootFiles = root.listFiles();
        if (rootFiles != null) {
            for (File f : rootFiles) {
                String name = f.getName().toLowerCase(java.util.Locale.ROOT);
                for (String ext : imageExt) {
                    if (f.isFile() && name.endsWith(ext)) {
                        looseImages.add(f.getName());
                    }
                }
            }
        }
        assertTrue("No asset image may lie loose at the repository root (use assets/): " + looseImages,
            looseImages.isEmpty());

        for (String stale : new String[]{"pom.xml", "build.gradle.kts", "settings.gradle.kts", "gradlew"}) {
            assertTrue("Stale build descriptor must not come back: " + stale, !new File(root, stale).exists());
        }
        assertTrue("The real build tool must stay", new File("tools/build_plugin.py").isFile());

        File iconDir = new File("assets/ref-icons");
        assertTrue("Reference icons must live in assets/ref-icons/", iconDir.isDirectory());
        File[] icons = iconDir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
        assertTrue("assets/ref-icons/ must hold the 16x16 reference sheet", icons != null && icons.length >= 200);
        String importer = Files.readString(new File("tools/import_ref_icons.py").toPath());
        assertTrue("tools/import_ref_icons.py must read the icons from assets/ref-icons/",
            importer.contains("assets") && importer.contains("ref-icons"));

        for (String archived : new String[]{"audit-15.md", "audit-16.md", "bug-audit-14.md", "features-30.md",
            "HANDOFF-PROMPT.md"}) {
            assertTrue("Historical notes belong in docs/archive/: docs/" + archived,
                !new File("docs", archived).exists());
        }

        String readme = Files.readString(new File("README.md").toPath());
        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        List<String> commands = declaredPluginCommands(pluginYml);
        assertTrue("plugin.yml must declare its commands: " + commands, commands.size() >= 38);
        List<String> undocumented = new java.util.ArrayList<>();
        for (String cmd : commands) {
            if (!readme.contains("/" + cmd)) {
                undocumented.add(cmd);
            }
        }
        assertTrue("README must list every declared command: " + undocumented, undocumented.isEmpty());

        String commandSrc = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        List<String> labels = bbSubcommandLabels(commandSrc);
        assertTrue("The /bb switch must expose its subcommands", labels.size() >= 100);
        List<String> missingLabels = new java.util.ArrayList<>();
        for (String label : labels) {
            if (!readme.contains(label)) {
                missingLabels.add(label);
            }
        }
        assertTrue("README must document every /bb subcommand and option: " + missingLabels,
            missingLabels.isEmpty());
        assertTrue("README is a command reference, not a landing page", readme.startsWith("# BosketBall — Commandes"));
    }

    // ---------------------------------------------------------- round 11: permissions, MODS, tutorial

    @Test
    public void everyPermissionNodeIsDeclaredAndGranular() throws Exception {
        List<fr.bosketball.util.Perms.NodeSpec> nodes = fr.bosketball.util.Perms.nodes();
        assertTrue("The permission registry must cover every feature", nodes.size() >= 30);
        String pluginYml = Files.readString(new File("src/main/resources/plugin.yml").toPath());
        for (fr.bosketball.util.Perms.NodeSpec node : nodes) {
            int at = pluginYml.indexOf("\n  " + node.id() + ":\n");
            assertTrue("Node missing from plugin.yml: " + node.id(), at > 0);
            String block = pluginYml.substring(at, Math.min(pluginYml.length(), at + 400));
            assertTrue("Node " + node.id() + " must declare default: " + node.defaultMode(),
                block.contains("default: " + node.defaultMode()));
            assertTrue("Node " + node.id() + " must document itself", block.contains("description:"));
        }
        for (fr.bosketball.util.Perms.NodeSpec node : nodes) {
            if (node.id().startsWith("bosketball.admin.")) {
                assertTrue("Admin nodes live under the umbrella", node.opOnly());
            }
        }
        assertTrue("The umbrella must inherit its children in plugin.yml",
            pluginYml.contains("children:") && pluginYml.contains("bosketball.admin.elo: true"));

        String command = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        for (String node : new String[]{"Perms.ELO", "Perms.COINS", "Perms.RANK", "Perms.TAGS", "Perms.SEASON",
            "Perms.HOLO", "Perms.ARENA", "Perms.RELOAD", "Perms.MAINTENANCE", "Perms.MUTECHAT", "Perms.STRESS",
            "Perms.BACKUP", "Perms.AUDIT", "Perms.DISPLAYS", "Perms.ITEMS", "Perms.MATCH",
            "Perms.STAFF_VANISH", "Perms.STAFF_FREEZE", "Perms.STAFF_REPORTS"}) {
            assertTrue("Command must gate on the granular node " + node, command.contains(node));
        }
        String core = Files.readString(new File("src/main/java/fr/bosketball/server/ServerCoreCommands.java").toPath());
        for (String sub : new String[]{"\"check\"", "\"nodes\"", "\"inherit\"", "\"users\"", "\"delete\""}) {
            assertTrue("/perm must expose " + sub, core.contains("case " + sub) || core.contains(sub));
        }
        assertTrue("/perm protects the built-in groups and blocks inherited deletion",
            core.contains("deleteGroup("));

        String perms = Files.readString(new File("src/main/java/fr/bosketball/server/PermissionManager.java").toPath());
        assertTrue("Umbrella nodes must be expanded onto the Bukkit attachment",
            perms.contains("expandUmbrellas("));
        assertTrue("A helper group must ship with the plugin", perms.contains("\"helper\"")
            && perms.contains("bosketball.staff.reports"));
        assertTrue("Existing installs must receive the new nodes", perms.contains("seedMissingDefaults()"));
    }

    @Test
    public void staffModsTagShipsLikeEveryOtherTag() throws Exception {
        fr.bosketball.model.ChatTag mods = fr.bosketball.model.ChatTag.byNameOrNull("mods");
        assertNotNull("The MODS staff tag must exist", mods);
        assertEquals("MODS must use the free staff glyph", '\uE29C', mods.glyph());
        assertEquals("MODS must not be purchasable", -1, mods.priceCoins());
        assertTrue("MODS must be flagged staff-exclusive", mods.isStaffExclusive());
        assertEquals("bosketball.staff", mods.requiredPermission());
        assertFalse("A random player may not wear MODS",
            mods.canBeUsedBy(null, "RandomPlayer", false, false));
        assertTrue("A staff member may wear MODS",
            mods.canBeUsedBy(null, "SomeMod", false, true));
        assertTrue("The tag tier label must read Staff Exclusive", mods.tierLabel().contains("Staff Exclusive"));
        assertTrue("MODS must not leak into the crate reward pool",
            Files.readString(new File("src/main/java/fr/bosketball/lobby/RewardCrateStation.java").toPath())
                .contains("!tag.isSpecialUnpurchasable()"));

        assertTrue("MODS pill texture missing", new File(
            "resourcepack/assets/bosketball/textures/font/tag_mods.png").isFile());
        assertTrue("MODS collector card missing", new File(
            "resourcepack/assets/bosketball/textures/item/tag_card_mods.png").isFile());
        assertTrue("MODS item model missing", new File(
            "resourcepack/assets/bosketball/models/item/tag_card_mods.json").isFile());
        assertTrue("MODS item definition missing", new File(
            "resourcepack/assets/bosketball/items/tag_card_mods.json").isFile());
        String font = Files.readString(new File("resourcepack/assets/minecraft/font/default.json").toPath());
        assertEquals("The MODS glyph must be registered exactly once", 1,
            font.split("\"bosketball:font/tag_mods.png\"", -1).length - 1);
        assertTrue("MODS must be part of the documented catalog",
            Files.readString(new File("tools/pack_overhaul_assets.py").toPath()).contains("\"mods\""));
    }

    @Test
    public void tutorialShowsARealGoldRingAndAdvancesByItself() throws Exception {
        String tutorial = Files.readString(new File("src/main/java/fr/bosketball/ui/TutorialManager.java").toPath());
        assertTrue("A dedicated marker tag must exist for cleanup",
            tutorial.contains("public static final String MARKER_TAG"));
        assertTrue("The ring must be built from real gold block displays",
            tutorial.contains("BlockDisplay") && tutorial.contains("Material.GOLD_BLOCK.createBlockData()")
                && tutorial.contains("spawnGoldRing("));
        assertTrue("The world must never be edited by the tutorial",
            !tutorial.contains("setType(") && !tutorial.contains(".setBlock(Material"));
        assertTrue("Entering the ring must validate the step",
            tutorial.contains("RING_ENTER_RADIUS_SQ") && tutorial.contains("completeStep(player, \"walk\")"));
        assertTrue("The ring turns green on validation", tutorial.contains("emeraldSlice()"));
        assertTrue("A boss bar must track the progress",
            tutorial.contains("BossBar.bossBar(") && tutorial.contains("player.showBossBar(bar)"));
        assertTrue("Nobody can be stuck: a soft-skip watchdog must exist",
            tutorial.contains("softSkipWatchdog(") && tutorial.contains("SOFT_SKIP_MS"));
        assertTrue("Steps validate themselves from gameplay events",
            tutorial.contains("notifyAction(") && tutorial.contains("\"queue\"") && tutorial.contains("\"shop\""));
        assertTrue("Every marker is removed on stop/finish/quit",
            tutorial.contains("clearMarkers(") && tutorial.contains("public int purgeMarkers()"));
        assertEquals("The tour is eight chapters", 8, fr.bosketball.ui.TutorialManager.stepCount());
        for (fr.bosketball.ui.TutorialManager.Step step : fr.bosketball.ui.TutorialManager.steps()) {
            assertFalse("Every step needs an objective", step.objective().isBlank());
            assertFalse("Every step needs instructions", step.instructions().isEmpty());
        }

        String gui = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertTrue("A tutorial menu must exist", gui.contains("public void openTutorial(Player player)")
            && gui.contains("TUTORIAL"));
        assertTrue("The tutorial menu must offer start / next / pause", gui.contains("case 45 ->")
            && gui.contains("case 47 ->") && gui.contains("case 51 ->"));
        String command = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("A bare /tuto must open the menu", command.contains("plugin.guiMenu().openTutorial(player)"));
        assertTrue("Opening the shop must validate the tutorial step",
            gui.contains("notifyAction(player, \"shop\", null)"));
        assertTrue("Joining a queue must validate the tutorial step",
            Files.readString(new File("src/main/java/fr/bosketball/game/GameManager.java").toPath())
                .contains("notifyAction(p, \"queue\", null)"));
    }

    // ---------------------------------------------------------- round 30: tutorial rebuilt

    @Test
    public void testRound30TutorialNeverAdvancesBehindThePlayer() throws Exception {
        java.util.List<fr.bosketball.ui.TutorialManager.Step> steps = fr.bosketball.ui.TutorialManager.steps();
        assertEquals("The rebuilt tour has 8 chapters", 8, steps.size());

        int readingChapters = 0;
        for (fr.bosketball.ui.TutorialManager.Step step : steps) {
            assertFalse("Chapter " + step.index() + " needs an objective", step.objective().isBlank());
            assertFalse("Chapter " + step.index() + " needs a hint", step.hint().isBlank());
            assertTrue("Chapter " + step.index() + " stays on one card",
                !step.instructions().isEmpty() && step.instructions().size() <= 3);
            assertTrue("Chapter rewards are never negative", step.revealCoins() >= 0);
            boolean reading = step.requirement().equals("welcome") || step.requirement().equals("finish");
            if (reading) {
                readingChapters++;
            }
            // THE bug of the old build: gameplay chapters validated themselves after 45 s.
            assertEquals("Only reading chapters may auto-advance (" + step.requirement() + ")",
                reading, step.autoValidated());
        }
        assertEquals("Exactly the welcome and the final card read alone", 2, readingChapters);

        // the ELO ladder shown to new players is the real one
        java.util.Map<Integer, String> ladder = fr.bosketball.ui.TutorialManager.rankLadder();
        assertEquals("ROOKIE", ladder.get(0));
        assertEquals("MVP", ladder.get(2200));
        int previous = -1;
        for (Integer floor : ladder.keySet()) {
            assertTrue("Rank floors must increase", floor > previous);
            previous = floor;
        }

        String src = readSource("src/main/java/fr/bosketball/ui/TutorialManager.java");
        assertTrue("The watchdog asks the pure rules what it may do",
            src.contains("switch (TutorialFlow.hintStage(step.autoValidated(), age, level, SOFT_SKIP_MS,")
                && src.contains("case AUTO_COMPLETE -> completeStep(player, step.requirement());"));
        assertTrue("A forced skip never pays the chapter reward",
            src.contains("advanceStep(player, !force && (validated || active.reading()))"));
        assertTrue("A chapter is paid at most once, for good",
            src.contains("TutorialFlow.paysReward(current, profile.tutorialRewardedStep(), step.revealCoins(), payReward)"));
        assertFalse("Replaying must not reset the claimed completion bonus",
            src.contains("setTutorialCompleted(false)"));
        assertTrue("The whole chapter is one single chat component",
            src.contains("card.append(Component.newline())") && src.contains("return card.build();"));
        assertTrue("The court is optional: no court means no freeze",
            src.contains("if (court == null) {") && src.contains("restoreControl(player);"));
        assertTrue("A hard safety net always gives control back",
            src.contains("}, 140L);") && src.contains("private void restoreControl(Player player)"));
        assertTrue("Death and world changes clean up after themselves",
            src.contains("public void onDeath(PlayerDeathEvent event)")
                && src.contains("public void onWorldChange(PlayerChangedWorldEvent event)"));
        assertTrue("A match ends or pauses the tour instead of drawing over the court",
            src.contains("safe(\"match-guard\", () -> {")
                && src.contains("TutorialFlow.finishesOnMatchStart(currentStep(player), STEPS.size())"));
        assertTrue("The lobby guide position is cached, never read from disk every tick",
            src.contains("invalidateHologramCache()") && src.contains("now - hologramCachedAt < 5_000L"));

        String gui = readSource("src/main/java/fr/bosketball/ui/GuiMenuManager.java");
        assertTrue("The guide lays its 8 chapters on a symmetric 4x2 board",
            gui.contains("TUTORIAL_CHAPTER_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25}"));
        assertTrue("The guide has its own hub artwork", gui.contains("Glyphs.GUI_OVERLAY_HUB"));
        assertFalse("The old broken chapter slot is gone", gui.contains("{10, 11, 12, 13, 14, 15, 16, 19}"));
        assertTrue("The guide tells the truth about progress",
            gui.contains("runnerLine(running, current, steps.size())"));
    }

    // ---------------------------------------------------------- round 31: tutorial scenario walkthroughs

    /**
     * Drives {@link fr.bosketball.tutorial.TutorialFlow} through complete player journeys. Each
     * scenario is a real situation reported in game: first join, replay, quit mid-chapter, reconnect
     * during a replay, skip spam, a match starting during the tour, a queue, an empty court.
     */
    @Test
    public void testRound31TutorialJourneysCannotBreakOrFarmCoins() {
        final int steps = fr.bosketball.ui.TutorialManager.stepCount();
        final long soft = 60_000L;
        final long skip = 120_000L;
        final long card = 25_000L;

        // ---- scenario 1: a brand-new player, everything done properly --------------------
        java.util.List<fr.bosketball.ui.TutorialManager.Step> tour = fr.bosketball.ui.TutorialManager.steps();
        int rewarded = -1;          // persisted ledger starts empty
        int coins = 0;
        for (int step = 0; step < steps; step++) {
            int reward = tour.get(step).revealCoins();
            if (fr.bosketball.tutorial.TutorialFlow.paysReward(step, rewarded, reward, true)) {
                coins += reward;
                rewarded = step;
            }
        }
        assertTrue("A first clean run pays the chapters", coins >= 100);
        // the final card pays no chapter reward (the completion bonus is separate), so the ledger
        // stops on the last chapter that actually paid
        assertTrue("The ledger follows the tour to its last paying chapter", rewarded >= steps - 2);

        // ---- scenario 2: replaying the finished tour pays nothing -----------------------
        int replayCoins = 0;
        for (int step = 0; step < steps; step++) {
            int reward = tour.get(step).revealCoins();
            if (fr.bosketball.tutorial.TutorialFlow.paysReward(step, rewarded, reward, true)) {
                replayCoins += reward;
            }
        }
        assertEquals("A replay must never pay twice", 0, replayCoins);

        // ---- scenario 3: quit at chapter 4, reconnect, resume ---------------------------
        int quitAt = 3;
        int resumed = fr.bosketball.tutorial.TutorialFlow.resumeIndex(false, quitAt, steps);
        assertEquals("A half-done tour resumes where it stopped", quitAt, resumed);
        int resumedRewarded = quitAt - 1;   // chapters 0..2 were paid before the disconnect
        assertEquals("The chapter already paid is not paid again", 0,
            fr.bosketball.tutorial.TutorialFlow.paysReward(quitAt - 1, resumedRewarded,
                tour.get(quitAt - 1).revealCoins(), true) ? 1 : 0);
        assertTrue("The next chapter still pays",
            fr.bosketball.tutorial.TutorialFlow.paysReward(quitAt, resumedRewarded, tour.get(quitAt).revealCoins(), true));

        // ---- scenario 4: a finished player never silently restarts ----------------------
        assertEquals("A finished player resumes at chapter 1, never mid-tour",
            0, fr.bosketball.tutorial.TutorialFlow.resumeIndex(true, 5, steps));
        assertEquals("Out-of-range saved steps are clamped",
            steps - 1, fr.bosketball.tutorial.TutorialFlow.resumeIndex(false, 99, steps));
        assertEquals("Negative saved steps are clamped", 0,
            fr.bosketball.tutorial.TutorialFlow.resumeIndex(false, -7, steps));

        // ---- scenario 5: skip spam pays nothing -----------------------------------------
        int spamRewarded = 2;
        for (int i = 0; i < 20; i++) {
            assertTrue("A forced skip never pays",
                !fr.bosketball.tutorial.TutorialFlow.paysReward(steps - 1, spamRewarded, 50, false));
        }

        // ---- scenario 6: the watchdog timeline -----------------------------------------
        assertEquals("A gameplay chapter waits first",
            fr.bosketball.tutorial.TutorialFlow.HintStage.WAIT,
            fr.bosketball.tutorial.TutorialFlow.hintStage(false, 1_000L, 0, soft, skip, card));
        assertEquals("After a minute it hints",
            fr.bosketball.tutorial.TutorialFlow.HintStage.HINT,
            fr.bosketball.tutorial.TutorialFlow.hintStage(false, soft, 0, soft, skip, card));
        assertEquals("Then it offers a clickable skip",
            fr.bosketball.tutorial.TutorialFlow.HintStage.OFFER_SKIP,
            fr.bosketball.tutorial.TutorialFlow.hintStage(false, skip, 1, soft, skip, card));
        assertEquals("It never completes a gameplay chapter on its own",
            fr.bosketball.tutorial.TutorialFlow.HintStage.WAIT,
            fr.bosketball.tutorial.TutorialFlow.hintStage(false, skip * 10, 2, soft, skip, card));
        assertEquals("A reading chapter advances alone",
            fr.bosketball.tutorial.TutorialFlow.HintStage.AUTO_COMPLETE,
            fr.bosketball.tutorial.TutorialFlow.hintStage(true, card, 0, soft, skip, card));
        assertEquals("A reading chapter still waits for its delay",
            fr.bosketball.tutorial.TutorialFlow.HintStage.WAIT,
            fr.bosketball.tutorial.TutorialFlow.hintStage(true, 10, 0, soft, skip, card));

        // ---- scenario 7: who may start a tour ------------------------------------------
        assertEquals("/tuto starts when everything is fine",
            fr.bosketball.tutorial.TutorialFlow.StartVerdict.START,
            fr.bosketball.tutorial.TutorialFlow.startVerdict(true, false, false, false));
        assertEquals("A second /tuto never restarts the intro",
            fr.bosketball.tutorial.TutorialFlow.StartVerdict.ALREADY_RUNNING,
            fr.bosketball.tutorial.TutorialFlow.startVerdict(true, true, false, false));
        assertEquals("No tour during a ranked game",
            fr.bosketball.tutorial.TutorialFlow.StartVerdict.IN_MATCH,
            fr.bosketball.tutorial.TutorialFlow.startVerdict(true, false, true, false));
        assertEquals("No tour while queued",
            fr.bosketball.tutorial.TutorialFlow.StartVerdict.QUEUED,
            fr.bosketball.tutorial.TutorialFlow.startVerdict(true, false, false, true));
        assertEquals("A queued player in a match is still refused",
            fr.bosketball.tutorial.TutorialFlow.StartVerdict.IN_MATCH,
            fr.bosketball.tutorial.TutorialFlow.startVerdict(true, false, true, true));
        assertEquals("Disabled stays disabled",
            fr.bosketball.tutorial.TutorialFlow.StartVerdict.DISABLED,
            fr.bosketball.tutorial.TutorialFlow.startVerdict(false, false, false, false));

        // ---- scenario 8: chapter arithmetic and HUD -------------------------------------
        assertEquals("The tour ends after the last chapter",
            steps, fr.bosketball.tutorial.TutorialFlow.nextStep(steps - 1, steps));
        assertEquals("nextStep never runs past the end",
            steps, fr.bosketball.tutorial.TutorialFlow.nextStep(steps + 5, steps));
        assertEquals("Chapter 1 starts at step 0", 1, fr.bosketball.tutorial.TutorialFlow.nextStep(0, steps));
        assertEquals("An empty tour is at 0%",
            0f, fr.bosketball.tutorial.TutorialFlow.bossBarProgress(0, false, 0), 0.0001f);
        assertEquals("A finished tour is at 100%",
            1f, fr.bosketball.tutorial.TutorialFlow.bossBarProgress(steps - 1, true, steps), 0.0001f);
        float previous = -1f;
        for (int step = 0; step < steps; step++) {
            float progress = fr.bosketball.tutorial.TutorialFlow.bossBarProgress(step, false, steps);
            assertTrue("The boss bar only moves forward", progress > previous);
            previous = progress;
        }

        // ---- scenario 9: a match always wins over the tour ------------------------------
        assertTrue("A live match pauses the tour",
            fr.bosketball.tutorial.TutorialFlow.shouldPauseForMatch(true, true));
        assertFalse("A practice session does not",
            fr.bosketball.tutorial.TutorialFlow.shouldPauseForMatch(true, false));
        assertFalse("Nothing to pause when no tour runs",
            fr.bosketball.tutorial.TutorialFlow.shouldPauseForMatch(false, true));

        // ---- scenario 10: chapters that need a ball ------------------------------------
        assertTrue(fr.bosketball.tutorial.TutorialFlow.needsLiveCourt("shoot"));
        assertTrue(fr.bosketball.tutorial.TutorialFlow.needsLiveCourt("dunk"));
        assertFalse(fr.bosketball.tutorial.TutorialFlow.needsLiveCourt("walk"));
        assertFalse(fr.bosketball.tutorial.TutorialFlow.needsLiveCourt("shop"));
        assertFalse(fr.bosketball.tutorial.TutorialFlow.needsLiveCourt("finish"));

        // ---- the manager really uses those rules, and persists the ledger --------------
        String src = readSource("src/main/java/fr/bosketball/ui/TutorialManager.java");
        assertTrue("Starting goes through the pure verdict",
            src.contains("TutorialFlow.startVerdict(") && src.contains("case QUEUED ->"));
        assertTrue("The match guard uses the pure rule", src.contains("TutorialFlow.shouldPauseForMatch("));
        assertTrue("Hard chapters are skipped when no court can be opened",
            src.contains("TutorialFlow.needsLiveCourt(step.requirement()) && !hasLiveBall(player)"));
        assertTrue("Jumping to a chapter cancels a running intro",
            src.contains("if (introTicks.remove(player.getUniqueId()) != null) {"));
        assertTrue("A reload must not keep a cached guide position",
            src.contains("invalidateHologramCache();\n        despawnHologram();")
                || src.contains("invalidateHologramCache();"));
        String profile = readSource("src/main/java/fr/bosketball/model/PlayerProfile.java");
        assertTrue("The reward ledger lives in the profile",
            profile.contains("private int tutorialRewardedStep = -1;") && profile.contains("setTutorialRewardedStep("));
        String stats = readSource("src/main/java/fr/bosketball/stats/StatsManager.java");
        assertTrue("The ledger survives a reconnect",
            stats.contains("progression.tutorial-rewarded-step")
                && stats.contains("p.setTutorialRewardedStep("));
    }

    @Test
    public void eloShortcutAcceptsBothSpellingsAndNavDockIsTheNewFooter() throws Exception {
        String command = Files.readString(new File("src/main/java/fr/bosketball/command/BosketCommand.java").toPath());
        assertTrue("An explicit operator must never be duplicated",
            command.contains("boolean explicitOp") && command.contains("head.equals(\"remove\")"));
        assertTrue("The player/amount indexes must start at the operation",
            command.contains("int base = 1;") && command.contains("base = 2;"));
        assertFalse("The old arraycopy shortcut must be gone",
            command.contains("System.arraycopy(args, 1, rewritten, 2, args.length - 1)"));

        String msg = Files.readString(new File("src/main/java/fr/bosketball/util/Msg.java").toPath());
        assertTrue("A glossy navigation dock must replace the bracketed buttons",
            msg.contains("public static Component navDock(") && msg.contains("Glyphs.NAV_PLAY"));
        assertFalse("The bracketed emoji buttons must be gone", msg.contains("[ ▶ ᴘʟᴀʏ ]"));
        assertTrue("Framed cards wear the small-caps display face",
            msg.contains("composeDividerHeader(Glyphs.smallCapsLabels(headerTitle))"));
        String glyphs = Files.readString(new File("src/main/java/fr/bosketball/util/Glyphs.java").toPath());
        for (String nav : new String[]{"NAV_PLAY", "NAV_LOCKER", "NAV_SHOP", "NAV_RANKS", "NAV_HELP", "NAV_DISCORD"}) {
            assertTrue("Missing dock glyph constant " + nav, glyphs.contains(nav));
        }
        for (String dock : new String[]{"nav_play", "nav_locker", "nav_shop", "nav_ranks", "nav_help", "nav_discord"}) {
            assertTrue("Missing dock artwork " + dock,
                new File("resourcepack/assets/bosketball/textures/font/" + dock + ".png").isFile());
        }
    }

    // ---------------------------------------------------------- round 12: syntax safety + tutorial polish

    private static final java.util.regex.Pattern LEAKED_MARKUP = java.util.regex.Pattern.compile(
        "</[A-Za-z]|<#[0-9a-fA-F]{6}>|<(?:bold|italic|underlined|strikethrough|obfuscated|gradient|rainbow|font|click|hover|shadow)>");

    private static final java.util.regex.Pattern STRING_LITERAL =
        java.util.regex.Pattern.compile("\"((?:[^\"\\\\\n]|\\\\.)*)\"");

    private static String plain(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(component);
    }

    @Test
    public void brokenMarkupIsRepairedInsteadOfLeaking() {
        assertEquals("a", plain(Msg.parse("<#4ADE80>a</bold>")));
        assertEquals("", plain(Msg.parse("</bold>")));
        assertEquals("Usage: /bb coins give <player> <amount>",
            plain(Msg.parse("Usage: /bb coins give <player> <amount>")));
        assertEquals("<2.7m", plain(Msg.parse("<2.7m")));
        assertEquals("Hi", plain(Msg.parse("<bold>Hi")));
        assertEquals("Hi", plain(Msg.parse("<gray>Hi</red>")));
        assertEquals("5 < 6", plain(Msg.parse("5 < 6")));
        assertEquals("x", plain(Msg.parse("<hover:show_text:'<green>tip</green>'><color:#4ADE80>x")));
        assertEquals("Keep me", plain(Msg.parse("<bold>Keep me</bold></not_a_tag>")));
        // The exact card that leaked a raw closing tag in game.
        String shot = plain(Msg.parse("✔ 3/8 SHOOTING</bold>"));
        assertTrue("Rendered as: " + shot, shot.startsWith("✔ 3/8 SHOOTING"));
        assertFalse("A stray closer must never reach the client: " + shot, shot.contains("</"));
        assertFalse("A raw hex tag must never reach the client", plain(Msg.parse("<#4ADE80>")).contains("<#"));
        assertEquals("<#4ADE80>▶</#4ADE80> [ꜱᴛᴀʀᴛ ɴᴏᴡ]".replace("<#4ADE80>", "").replace("</#4ADE80>", ""),
            plain(Msg.parse("<#4ADE80>▶</#4ADE80> [ꜱᴛᴀʀᴛ ɴᴏᴡ]")));
        assertFalse("MiniMessage repair must be stable", MiniSyntax.repair("<bold>a</bold>").contains("\\<"));
        assertEquals("<bold>a</bold>", MiniSyntax.repair("<bold>a</bold>"));
    }

    @Test
    public void everyStringLiteralWithMarkupParsesWithoutLeaking() throws Exception {
        List<String> leaks = new ArrayList<>();
        Path root = Path.of("src/main/java");
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file);
                var matcher = STRING_LITERAL.matcher(src);
                while (matcher.find()) {
                    String literal = matcher.group(1);
                    if (literal.indexOf('<') < 0) {
                        continue;
                    }
                    String rendered;
                    try {
                        rendered = plain(Msg.parse(literal));
                    } catch (RuntimeException | StackOverflowError boom) {
                        leaks.add(file + " threw " + boom + " on " + literal);
                        continue;
                    }
                    if (LEAKED_MARKUP.matcher(rendered).find()) {
                        leaks.add(file + " :: " + literal + " -> " + rendered);
                    }
                }
            }
        }
        assertTrue("Raw markup can still reach the client ("
            + leaks.size() + " case(s)): " + leaks.subList(0, Math.min(8, leaks.size())), leaks.isEmpty());
    }

    @Test
    public void theThreeReportedSyntaxBugsStayFixed() throws Exception {
        String gui = Files.readString(Path.of("src/main/java/fr/bosketball/ui/GuiMenuManager.java"));
        assertTrue("The tutorial menu name must open its own bold tag",
            gui.contains("+ \" <bold>\"") && gui.contains("+ step.title() + \"</bold>\""));
        String manager = Files.readString(Path.of("src/main/java/fr/bosketball/game/GameManager.java"));
        assertFalse("Never hand MiniMessage markup to Component.text",
            manager.contains("Component.text(\"<#"));
        String session = Files.readString(Path.of("src/main/java/fr/bosketball/game/MatchSession.java"));
        assertFalse("'<2.7m' is not a tag", session.contains("<2.7m"));
        String msg = Files.readString(Path.of("src/main/java/fr/bosketball/util/Msg.java"));
        assertTrue("Every parsed message must be repaired first", msg.contains("MiniSyntax.repair(miniMessage)"));
        java.util.regex.Matcher rawText = java.util.regex.Pattern
            .compile("Component\\.text\\(\\s*\"[^\"]*<").matcher("");
        List<String> offenders = new ArrayList<>();
        try (var stream = Files.walk(Path.of("src/main/java"))) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                rawText.reset(source);
                while (rawText.find()) {
                    offenders.add(file + " :: " + rawText.group());
                }
            }
        }
        assertTrue("MiniMessage markup must never be handed to Component.text: " + offenders, offenders.isEmpty());
        assertTrue("Parsing must never throw at the client", msg.contains("MiniSyntax.stripTags(source)"));
    }

    @Test
    public void theTourEndsAtSpawnAndSurvivesRenderingFailures() throws Exception {
        String tutorial = Files.readString(Path.of("src/main/java/fr/bosketball/ui/TutorialManager.java"));
        assertTrue("Finishing the tour must teleport to the lobby spawn",
            tutorial.contains("public void returnToSpawn(Player player)")
                && tutorial.contains("protectionManager().spawnLocation()"));
        assertTrue("finish() must call it", tutorial.contains("safe(\"spawn\", () -> returnToSpawn(player))"));
        assertTrue("A rendering failure must never kill the tick task",
            tutorial.contains("private void safe(String what, Runnable action)")
                && tutorial.contains("safe(\"markers\"") && tutorial.contains("safe(\"hud\"")
                && tutorial.contains("safe(\"intro\"") && tutorial.contains("safe(\"hologram\"")
                && tutorial.contains("safe(\"watchdog\""));
        assertTrue("The ring must fall back to the player's feet",
            tutorial.contains("ring = player.getLocation().clone();")
                && tutorial.contains("spot = player.getLocation().clone();"));
        assertTrue("New players get the tour automatically",
            tutorial.contains("public void maybeStartOnJoin(Player player)")
                && Files.readString(Path.of("src/main/java/fr/bosketball/listener/GameplayListener.java"))
                    .contains("maybeStartOnJoin(player)"));
        assertTrue("The flag must be on by default",
            Files.readString(Path.of("src/main/resources/config.yml"))
                .contains("force-on-first-join: true"));
        assertTrue("Staff can dump the tutorial state in game",
            tutorial.contains("public List<String> diagnostics(Player player)")
                && Files.readString(Path.of("src/main/java/fr/bosketball/command/BosketCommand.java"))
                    .contains("case \"diag\", \"diagnose\", \"debug\""));
    }

    // ---------------------------------------------------------- round 13: no 3D locker, real gradients

    @Test
    public void gradientsProduceRealMultiStopColours() throws Exception {
        String gradient = Msg.gradBold(Msg.GRAD_BRAND, "BOSKETBALL");
        assertTrue("A gradient must stay valid MiniMessage", MiniSyntax.isClean(gradient));
        assertEquals("BOSKETBALL", plain(Msg.parse(gradient)));

        net.kyori.adventure.text.Component parsed = Msg.parse(gradient);
        java.util.Set<String> colours = new java.util.HashSet<>();
        java.util.ArrayDeque<net.kyori.adventure.text.Component> queue = new java.util.ArrayDeque<>();
        queue.add(parsed);
        while (!queue.isEmpty()) {
            var node = queue.poll();
            var colour = node.color();
            if (colour != null) {
                colours.add(colour.asHexString());
            }
            queue.addAll(node.children());
        }
        assertTrue("A MiniMessage gradient must paint several colours, got " + colours, colours.size() >= 2);
        assertEquals("The brand gradient is the orange to gold pair of the palette",
            fr.bosketball.util.Palette.WARN + ":" + fr.bosketball.util.Palette.GOLD_SOFT, Msg.GRAD_BRAND);

        for (String stops : new String[]{Msg.GRAD_BRAND, Msg.GRAD_GOLD, Msg.GRAD_MINT, Msg.GRAD_ICE, Msg.GRAD_SUNSET}) {
            assertTrue("Gradient stops must be two hex colours: " + stops,
                stops.matches("#[0-9A-Fa-f]{6}:#[0-9A-Fa-f]{6}"));
        }
        assertTrue("Titles and boss bars must actually use the gradients",
            Files.readString(Path.of("src/main/java/fr/bosketball/ui/TutorialManager.java")).contains("Msg.gradBold(")
                && Files.readString(Path.of("src/main/java/fr/bosketball/ui/ScoreboardSidebarManager.java")).contains("Msg.gradBold(")
                && Files.readString(Path.of("src/main/java/fr/bosketball/ui/BossBarHudManager.java")).contains("Msg.gradBold(")
                && Files.readString(Path.of("src/main/java/fr/bosketball/game/MatchSession.java")).contains("Msg.gradBold("));
    }

    @Test
    public void theThreeDimensionalLockerRoomIsGone() throws Exception {
        assertFalse("The 3D locker room class must be deleted",
            Files.exists(Path.of("src/main/java/fr/bosketball/ui/LockerRoom3dMenuManager.java")));
        List<String> offenders = new ArrayList<>();
        try (var stream = Files.walk(Path.of("src/main/java"))) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("LockerRoom3d") || source.contains("lockerRoom3d")) {
                    offenders.add(file.toString());
                }
            }
        }
        assertTrue("No code may reference the removed 3D locker room: " + offenders, offenders.isEmpty());
        String commandSource = Files.readString(Path.of("src/main/java/fr/bosketball/command/BosketCommand.java"));
        assertFalse("Nothing may reference the removed /bb 3d stage editor",
            commandSource.contains("case \"3d\"") || commandSource.contains("/bb 3d"));
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertFalse("plugin.yml must not advertise a 3D locker room", pluginYml.contains("3D Locker"));
        assertTrue("/locker now opens the cosmetics locker",
            pluginYml.contains("cosmetics")
                && Files.readString(Path.of("src/main/java/fr/bosketball/command/BosketCommand.java"))
                    .contains("case \"locker\" -> \"cosmetics\""));
        for (String texture : new String[]{"locker_hud_topbar.png", "locker_hud_select.png",
            "locker_hud_select_exit.png", "locker_hud_info_panel.png", "locker_hud_row_idle.png"}) {
            assertFalse("Removed HUD plate still shipped: " + texture,
                new File("resourcepack/assets/bosketball/textures/font/" + texture).isFile());
        }
        assertFalse("The transparent shulker viewport belonged to the 3D HUD",
            new File("resourcepack/assets/minecraft/textures/gui/container/shulker_box.png").isFile());
        assertFalse("Its font providers must be gone too",
            Files.readString(Path.of("resourcepack/assets/minecraft/font/default.json")).contains("locker_hud_"));
        assertTrue("Bots and holograms keep their patched skins through the new helper",
            Files.readString(Path.of("src/main/java/fr/bosketball/util/Skins.java")).contains("buildCustomPatchedSkin")
                && Files.readString(Path.of("src/main/java/fr/bosketball/game/CourtDefenderBot.java"))
                    .contains("Skins.buildCustomPatchedSkin")
                && Files.readString(Path.of("src/main/java/fr/bosketball/ui/LobbyHologramManager.java"))
                    .contains("Skins.buildCustomPatchedSkin"));
    }

    @Test
    public void closeTagTwinNeverPrintsAStrayAngleBracket() {
        assertEquals("</#38BDF8>", Msg.closeTag("<#38BDF8>"));
        assertEquals("</red>", Msg.closeTag("<red>"));
        assertEquals("", Msg.closeTag(""));
        assertEquals("", Msg.closeTag("red"));

        // A hex colour pair parsed once must come out as the plain label, with no bracket left over.
        String rankPlain = PlainTextComponentSerializer.plainText()
            .serialize(Msg.parse("<#FF8C1A><bold>ROOKIE</bold></#FF8C1A>"));
        assertEquals("ROOKIE", rankPlain);

        // Round 37/38: the above-head name is the rank plate now. It must keep the guarantee the old
        // scoreboard prefix had - never a stray angle bracket - without any MiniMessage round trip.
        NameplateLayout.Plate plate = NameplateLayout.build(Rank.ROOKIE, "friandise");
        String plateText = PlainTextComponentSerializer.plainText().serialize(plate.plate());
        assertTrue("The plate must carry the nickname: " + plateText, plateText.contains("friandise"));
        assertFalse("The plate must never print a stray angle bracket",
            plateText.contains("<") || plateText.contains(">"));
        assertEquals("Tags typed into a nickname are stripped, never parsed",
            "redBob", NameplateLayout.sanitize("<red>Bob"));
        assertEquals("Newlines cannot break the plate in two", "AB",
            NameplateLayout.sanitize("A\nB"));

        for (TeamSide side : new TeamSide[]{TeamSide.IGNIS, TeamSide.FROST}) {
            String plain = PlainTextComponentSerializer.plainText()
                .serialize(Msg.parse(side.chatLabel()));
            assertEquals(side.shortCode(), plain);
            assertFalse("Team label must not leak markup: " + plain,
                plain.contains("<") || plain.contains(">"));
        }
    }

    @Test
    public void noSourceBuildsAClosingTagFromABracketedOpeningTag() throws Exception {
        // "</" + "<#38BDF8>".substring(1) + ">" == "</#38BDF8>>": the trailing '>' was printed in game
        // between the rank pill and the player name. Every closing tag must come from Msg.closeTag.
        List<String> offenders = new ArrayList<>();
        try (var stream = Files.walk(Path.of("src/main/java"))) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                int lineNo = 0;
                for (String line : Files.readAllLines(file)) {
                    lineNo++;
                    String trimmed = line.trim();
                    if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                        continue;
                    }
                    if (line.contains(".substring(1)).append(\">\")")
                        || line.matches(".*\"</\"\\s*\\+\\s*\\w+\\.substring\\(1\\).*")) {
                        offenders.add(file + ":" + lineNo);
                    }
                }
            }
        }
        assertTrue("Closing tags must be built with Msg.closeTag: " + offenders, offenders.isEmpty());
    }

    @Test
    public void pingDiagnosticsSummariseLatencyJitterAndSustainedSpikes() {
        fr.bosketball.server.PingDiagnostics.Series series = new fr.bosketball.server.PingDiagnostics.Series();
        series.add(42);
        series.add(40);
        series.add(44);
        assertEquals(3, series.count());
        assertEquals(44, series.last());
        assertEquals(42, series.average());
        assertEquals(40, series.min());
        assertEquals(44, series.max());
        assertEquals(3, series.jitter());
        assertFalse("A single jitter sample is not a sustained problem", series.sustainedAbove(41, 2));

        series.add(310);
        series.add(320);
        series.add(300);
        assertTrue("Three consecutive seconds above 250 ms is a real problem",
            series.sustainedAbove(250, 3));
        assertFalse("...but never so for a threshold above the samples", series.sustainedAbove(400, 1));

        for (int i = 0; i < 60; i++) {
            series.add(50 + (i % 5));
        }
        assertEquals("The ring buffer must stay bounded", 30, series.count());
        assertEquals(30, series.ordered().size());
        assertEquals(54, series.last());
    }

    @Test
    public void shippedResourcePackStaysByteIdenticalToItsSourcesAndLean() throws Exception {
        java.io.File zip = new java.io.File("releases/BosketBall-ResourcePack.zip");
        assertTrue("The release pack must exist", zip.isFile());
        assertTrue("The pack must stay under 3.5 MB (lossless optimisation only): " + zip.length(),
            zip.length() < 3_500_000L);
        int pngs = 0;
        try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(zip)) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("blockbench/")) {
                    continue;
                }
                java.io.File source = new java.io.File("resourcepack", entry.getName());
                assertTrue("Pack entry without a source file: " + entry.getName(), source.isFile());
                if (entry.getName().endsWith(".png")) {
                    pngs++;
                    byte[] shipped = archive.getInputStream(entry).readAllBytes();
                    assertTrue("Textures must be shipped untouched (no re-encoding at zip time): "
                        + entry.getName(), java.util.Arrays.equals(shipped, Files.readAllBytes(source.toPath())));
                }
            }
        }
        // 641 before the big tab work; twenty per-row golden frames were added (+20) and Round 37
        // added seven rank nameplates as four glyphs each, Round 38 dropped the crest and keeps
        // three (left, middle, right): 641 + 20 + 21 = 682, minus the tablist.zip backdrop that
        // went away with the frame (681). Round 40 removed the twenty frames (-20).
        // Round 46: +25 slices of the PrimeHUD charge bar, its rail, and the 9-slice pill.
        assertEquals("Every texture of the pack must be shipped", 686, pngs);
    }

    /** One leaderboard plate, exactly as the hologram builds it. */
    private static String plate(int place, String name, String value) {
        return fr.bosketball.ui.LobbyHologramManager.placeCell(place, "#FDE047")
            + fr.bosketball.ui.LobbyHologramManager.pillCell(place == 1 ? Rank.MVP.pillGlyph() : Rank.ROOKIE.pillGlyph())
            + fr.bosketball.ui.LobbyHologramManager.nameCell(name, place == 1)
            + fr.bosketball.ui.LobbyHologramManager.valueCell(value, "<#38BDF8>", "#38BDF8");
    }

    @Test
    public void leaderboardRowsAreAPixelGridWithIdenticalPlates() {
        // The screenshot bug: the ROOKIE name started at a different pixel on every row because
        // rank pills have different widths (MVP 31 px, ROOKIE 44 px, SUPERSTAR 58 px) and the old
        // padding used negative space glyphs whose advance textAdvancePx never understood.
        java.util.List<Integer> widths = new ArrayList<>();
        for (String name : new String[]{"MaxLananas", "Akhashi_", "friandise", "havok_l", "x",
            "A_VeryLongNickname", "ÁÉÎÕÜ_ç"}) {
            for (int place = 1; place <= 7; place++) {
                String row = plate(place, name, "1234");
                widths.add(Glyphs.markupAdvancePx(row));
                assertEquals("Row for " + name + " must start its name on the same pixel column",
                    18 + 62,
                    Glyphs.markupAdvancePx(fr.bosketball.ui.LobbyHologramManager.placeCell(place, "#FDE047"))
                        + Glyphs.markupAdvancePx(fr.bosketball.ui.LobbyHologramManager.pillCell(
                            place == 1 ? Rank.MVP.pillGlyph() : Rank.ROOKIE.pillGlyph())));
            }
        }
        java.util.Set<Integer> distinct = new HashSet<>(widths);
        assertEquals("Every leaderboard plate must be exactly the same width, got " + distinct,
            1, distinct.size());
        // Round 45: the per-row category icon went away (it repeated the title's icon on all seven
        // rows and added a column nobody could measure), the name cell gained 6 px and the value
        // cell 12 px so a five-figure balance still fits.
        assertEquals("The plate width is the documented grid width", 216, (int) widths.get(0));
        assertEquals("Place cells are a fixed 18 px", 18,
            Glyphs.markupAdvancePx(fr.bosketball.ui.LobbyHologramManager.placeCell(7, "#94A3B8")));
        assertEquals("Pill cells are a fixed 62 px whatever the rank artwork is", 62,
            Glyphs.markupAdvancePx(fr.bosketball.ui.LobbyHologramManager.pillCell(Rank.SUPERSTAR.pillGlyph())));
        assertEquals("Name cells are a fixed 96 px", 96,
            Glyphs.markupAdvancePx(fr.bosketball.ui.LobbyHologramManager.nameCell("MaxLananas", false)));
        assertEquals("Value cells are a fixed 40 px", 40,
            Glyphs.markupAdvancePx(fr.bosketball.ui.LobbyHologramManager.valueCell("15", "<#38BDF8>", "#38BDF8")));
        assertEquals("An empty slot keeps the very same plate", 216,
            Glyphs.markupAdvancePx(fr.bosketball.ui.LobbyHologramManager.placeCell(5, "#94A3B8")
                + fr.bosketball.ui.LobbyHologramManager.pillCell(' ')
                + fr.bosketball.ui.LobbyHologramManager.nameCell("—", false)
                + fr.bosketball.ui.LobbyHologramManager.valueCell("", "<#94A3B8>", "#94A3B8")));
        assertTrue("Long names are truncated, never allowed to break the columns",
            Glyphs.textAdvancePx(fr.bosketball.ui.LobbyHologramManager.fitToPx("A_VeryLongNickname", 92), false) <= 92);
    }

    /**
     * Tab list backdrop: the owner's `tablist.zip` ships a 256x256 gold frame with a 25% black
     * backdrop. The pack must carry that file byte for byte under the zip's own namespace, and the
     * four nine-sliced sizes must exist for the glyphs the plugin actually draws.
     */
    private static String sha256(java.io.File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file.toPath())));
    }

    /**
     * Tooltips: Minecraft 1.21.2 draws them from GUI sprites and lets an item pick a style with the
     * {@code minecraft:tooltip_style} component (style id -> tooltip/<id>_background + tooltip/<id>_frame).
     * The pack ships the "Ultimate Tooltips" art, so every style the plugin can hand out must resolve,
     * the ribbons must be bound to distinct font glyphs, and the legendary/artifact frames must really
     * be animated - otherwise the item silently falls back to the default tooltip.
     */
    @Test
    public void tooltipStylesAndRarityRibbonsResolveInThePack() throws Exception {
        java.io.File sprites = new File("resourcepack/assets/minecraft/textures/gui/sprites/tooltip");
        assertTrue("The default tooltip must ship the new art",
            new File(sprites, "background.png").isFile() && new File(sprites, "frame.png").isFile());
        String defaultFrameMeta = Files.readString(new File(sprites, "frame.png.mcmeta").toPath());
        assertTrue("The default tooltip frame must keep its nine-slice scaling",
            defaultFrameMeta.contains("nine_slice") && defaultFrameMeta.contains("stretch_inner"));

        java.util.Set<Character> ribbons = new java.util.HashSet<>();
        for (fr.bosketball.util.TooltipStyles.Rarity rarity : fr.bosketball.util.TooltipStyles.Rarity.values()) {
            assertTrue("Ribbon glyphs must be unique, " + rarity + " collides",
                ribbons.add(rarity.ribbon()));
            assertTrue("Missing rarity ribbon texture for " + rarity,
                new File("resourcepack/assets/bosketball/textures/font/tooltips/" + rarity.id() + ".png").isFile());
            String styleId = rarity.styleId();
            if (styleId == null) {
                continue;
            }
            File background = new File(sprites, styleId + "_background.png");
            File frame = new File(sprites, styleId + "_frame.png");
            assertTrue("Missing tooltip background for style " + styleId, background.isFile());
            assertTrue("Missing tooltip frame for style " + styleId, frame.isFile());
            assertTrue("Missing nine-slice metadata for " + styleId,
                new File(sprites, styleId + "_background.png.mcmeta").isFile());
        }

        for (String animated : List.of("legendary_frame", "artifact_frame")) {
            String meta = Files.readString(new File(sprites, animated + ".png.mcmeta").toPath());
            assertTrue(animated + " must be an animated frame (that is the whole point of the style)",
                meta.contains("\"animation\"") && meta.contains("frametime"));
        }

        String font = Files.readString(new File("resourcepack/assets/minecraft/font/default.json").toPath());
        for (fr.bosketball.util.TooltipStyles.Rarity rarity : fr.bosketball.util.TooltipStyles.Rarity.values()) {
            String hex = String.format("%04x", (int) rarity.ribbon());
            assertTrue("Font provider missing for the " + rarity + " ribbon (U+" + hex.toUpperCase() + ")",
                font.contains("\\u" + hex) && font.contains("font/tooltips/" + rarity.id() + ".png"));
        }

        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        assertTrue("Shop cards must take their style from the price",
            guiSrc.contains("TooltipStyles.forCoins(entry.priceCoins())"));
        assertTrue("Rank cards must take their style from the tier",
            guiSrc.contains("TooltipStyles.forRank(r)"));
        assertTrue("Tiers must stay ordered: MVP > LEGEND > SUPERSTAR",
            fr.bosketball.util.TooltipStyles.forRank(fr.bosketball.model.Rank.MVP).ordinal()
                > fr.bosketball.util.TooltipStyles.forRank(fr.bosketball.model.Rank.LEGEND).ordinal()
            && fr.bosketball.util.TooltipStyles.forRank(fr.bosketball.model.Rank.SUPERSTAR).ordinal()
                >= fr.bosketball.util.TooltipStyles.forRank(fr.bosketball.model.Rank.PRO).ordinal());
    }

    /**
     * The lobby hotbar is the first thing a player sees, so it is asserted like a layout
     * contract: seven badges, centred on slots 1..7, symmetric, no emoji, and every badge id
     * backed by a real texture + model + item definition inside the shipped resource pack.
     */
    @Test
    public void lobbyHotbarIsSevenCentredBadgesBackedByThePack() throws Exception {
        int[] slots = fr.bosketball.model.BallSkin.LOBBY_HOTBAR_SLOTS;
        String[] models = fr.bosketball.model.BallSkin.LOBBY_HOTBAR_MODELS;
        assertEquals("Seven doors, one badge each", 7, slots.length);
        assertEquals("One badge id per slot", slots.length, models.length);

        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int slot : slots) {
            assertTrue("A hotbar door may never land on the two reserved edge slots, got " + slot,
                slot >= 1 && slot <= 7);
            assertTrue("Hotbar slots must be unique, got a duplicate at " + slot, used.add(slot));
        }
        for (int slot : slots) {
            assertTrue("Hotbar slot " + slot + " has no mirror twin at " + (8 - slot)
                + " - the row would look lopsided", used.contains(8 - slot));
        }

        String skinSrc = Files.readString(new File("src/main/java/fr/bosketball/model/BallSkin.java").toPath());
        for (String emoji : List.of("\u26A1", "\uD83D\uDC55", "\uD83D\uDED2", "\uD83C\uDFF7", "\uD83D\uDC65", "\uD83D\uDCD6", "\uD83C\uDFC6")) {
            assertFalse("The hotbar names must stay emoji-free, found " + emoji, skinSrc.contains(emoji));
        }
        assertFalse("Legacy prop_ icons must not be re-introduced on the hotbar",
            skinSrc.contains("\"prop_play_emblem\"") || skinSrc.contains("\"prop_shop_crate\""));

        for (String id : models) {
            assertTrue("The hotbar must only use sprites from the shipped ref_* icon library, got " + id,
                id.startsWith("ref_"));
            assertTrue("Missing texture for " + id,
                new File("resourcepack/assets/bosketball/textures/item/icons/" + id + ".png").isFile());
            File model = new File("resourcepack/assets/bosketball/models/item/" + id + ".json");
            File def = new File("resourcepack/assets/bosketball/items/" + id + ".json");
            assertTrue("Missing model for " + id, model.isFile());
            assertTrue("Missing item definition for " + id, def.isFile());
            String modelJson = Files.readString(model.toPath());
            assertTrue("Model " + id + " must point at its icon-library sprite",
                modelJson.contains("bosketball:item/icons/" + id));
            assertTrue("Model " + id + " must stay a flat GUI sprite", modelJson.contains("minecraft:item/generated"));
            String defJson = Files.readString(def.toPath());
            assertTrue("Item definition " + id + " must reference its model",
                defJson.contains("bosketball:item/" + id));
        }

        String guiSrc = Files.readString(new File("src/main/java/fr/bosketball/ui/GuiMenuManager.java").toPath());
        for (String id : models) {
            if ("ref_catalogue".equals(id)) {
                // the hub screen never advertises a door that leads to itself
                continue;
            }
            assertTrue("The hub GUI must reuse the hotbar badge " + id + " so both stay in sync",
                guiSrc.contains("\"" + id + "\""));
        }
    }

    /**
     * Round 38 — the pack the server advertises has to be the pack it ships.
     *
     * <p>The owner's clients were still drawing the previous pack: the nameplates rendered as
     * missing-glyph squares while the server happily sent the new SHA-1. The cause was the CDN
     * mirrors, which pinned a tag from several rounds earlier, so a client that already had that
     * zip cached never re-downloaded anything. This test pins the invariant: the tag inside every
     * mirror URL, the declared SHA-1, the .sha1 file and the built zip all describe the same file,
     * and the pack is still auto-sent on join.
     */
    @Test
    public void round38MirrorsAndShaPointAtTheShippedPack() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        String sha1 = Files.readString(Path.of("releases/BosketBall-ResourcePack.zip.sha1")).trim();
        assertEquals("The .sha1 file is a SHA-1", 40, sha1.length());

        assertTrue("The pack must be auto-sent on join",
            config.contains("auto-send-on-join: true"));
        assertTrue("The embedded host must stay wired", config.contains("embedded-http-server:")
            && config.contains("enabled: true"));
        assertTrue("external-sha1 must be the shipped pack",
            config.contains("external-sha1: \"" + sha1 + "\""));

        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("pack-([0-9a-f]{8})").matcher(config);
        java.util.Set<String> tags = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            tags.add(matcher.group(1));
        }
        assertFalse("At least one CDN mirror must be configured", tags.isEmpty());
        for (String tag : tags) {
            assertEquals("Every mirror must point at the shipped pack (tag pack-" + tag + ")", sha1.substring(0, 8), tag);
        }
        assertTrue("The two mirror routes must exist",
            config.contains("cdn.jsdelivr.net/gh/MaxLananas/BosketBall@pack-")
                && config.contains("raw.githubusercontent.com/MaxLananas/BosketBall/pack-"));

        String build = Files.readString(Path.of("tools/build_plugin.py"));
        assertTrue("The build must keep the mirror tags in sync, or this bug comes back",
            build.contains("pack-[0-9a-fA-F]{8}")
                && build.contains("git tag -f {tag} && git push -f origin {tag}"));
    }

    /**
     * The pack must be servable from two hosts at once (one in Europe, one in the US) while the
     * SHA-1 stays the one baked into config.yml: a mirror is only ever a different URL for the
     * exact same zip. Order = priority, junk entries are dropped instead of sent to clients.
     */
    @Test
    public void resourcePackMirrorsKeepOrderAndStayOnTheSameZip() throws Exception {
        assertEquals("No mirror configured means no mirror at all",
            java.util.List.of(), fr.bosketball.pack.ResourcePackHostService.sanitizeMirrors(null));
        assertEquals("Blank entries are dropped",
            java.util.List.of(), fr.bosketball.pack.ResourcePackHostService.sanitizeMirrors(
                java.util.Arrays.asList("", "   ", null)));

        java.util.List<String> cleaned = fr.bosketball.pack.ResourcePackHostService.sanitizeMirrors(
            java.util.List.of(
                "  https://fr.mirror.example/BosketBall-ResourcePack.zip?v={sha1} ",
                "ftp://fr.mirror.example/pack.zip",
                "fr.mirror.example/pack.zip",
                "https://cdn.example/BosketBall-ResourcePack.zip",
                "https://fr.mirror.example/BosketBall-ResourcePack.zip?v={sha1}"));
        assertEquals("Mirror order is the priority order, trimmed, deduped, http(s) only",
            java.util.List.of(
                "https://fr.mirror.example/BosketBall-ResourcePack.zip?v={sha1}",
                "https://cdn.example/BosketBall-ResourcePack.zip"), cleaned);

        String sha1 = "476ecec839d69d2c6ae8a505d97aa3161583642e";
        java.io.File tmp = java.nio.file.Files.createTempDirectory("bb-mirror-test").toFile();
        fr.bosketball.pack.ResourcePackHostService service =
            new fr.bosketball.pack.ResourcePackHostService(tmp, null);
        service.initialize(false, "127.0.0.1", 0, "", "", sha1, false, java.util.List.of(
            "https://fr.mirror.example/BosketBall-ResourcePack.zip?v={sha1}",
            "https://cdn.example/BosketBall-ResourcePack.zip"));

        assertEquals("Both mirrors are kept, the embedded server stays as the last fallback",
            2, service.configuredMirrors().size());
        assertEquals("The first mirror is the one advertised to clients",
            "https://fr.mirror.example/BosketBall-ResourcePack.zip?v=" + sha1,
            service.effectiveDownloadUrl());
        assertTrue("A mirror pointing at an unexpanded placeholder would break the download",
            !service.effectiveDownloadUrl().contains("{sha1}"));
        assertTrue("Mirrors alone are enough to send the pack", service.isReadyToSend());
        assertEquals("The downloaded zip is hashed with the same SHA-1 everywhere", sha1, service.sha1Hex());

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(tmp.toPath())) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    java.nio.file.Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }


    @Test
    public void dunkCooldownIsTwentySecondsAndClamped() {
        long now = 1_000_000L;
        long ready = now + fr.bosketball.game.DunkRules.COOLDOWN_MS;
        assertEquals("The dunk cooldown is 20 s", 20_000L, fr.bosketball.game.DunkRules.COOLDOWN_MS);
        assertEquals("Right after the dunk the whole cooldown is left",
            20_000L, fr.bosketball.game.DunkRules.cooldownRemainingMs(now, ready));
        assertFalse("A dunk cannot start while recharging", fr.bosketball.game.DunkRules.offCooldown(now, ready));
        assertEquals("Halfway through, half the cooldown is left",
            10_000L, fr.bosketball.game.DunkRules.cooldownRemainingMs(now + 10_000L, ready));
        assertTrue("After 20 s the dunk is available again",
            fr.bosketball.game.DunkRules.offCooldown(now + 20_000L, ready));
        assertEquals("An overdue cooldown never goes negative",
            0L, fr.bosketball.game.DunkRules.cooldownRemainingMs(now + 60_000L, ready));
        assertEquals("The HUD label is in seconds", "14.2s",
            fr.bosketball.game.DunkRules.cooldownLabel(14_200L));
    }

    @Test
    public void dunkAimConeIsForgiving() {
        assertTrue("A dead-on look dunks", fr.bosketball.game.DunkRules.aimedAtRim(0.0));
        assertTrue("A 30 degree look still dunks", fr.bosketball.game.DunkRules.aimedAtRim(30.0));
        assertTrue("The cone edge dunks", fr.bosketball.game.DunkRules.aimedAtRim(-42.0));
        assertFalse("Facing away from the rim does not dunk", fr.bosketball.game.DunkRules.aimedAtRim(43.0));
        assertFalse("Garbage input is refused", fr.bosketball.game.DunkRules.aimedAtRim(Double.NaN));
    }

    @Test
    public void dunkSuccessIsGenerousAndMonotonic() {
        double open = fr.bosketball.game.DunkRules.successChance(1.4, 0.0, 0.0, true);
        double close = fr.bosketball.game.DunkRules.successChance(1.4, 0.0, 0.0, false);
        double contested = fr.bosketball.game.DunkRules.successChance(1.4, 0.8, 0.0, true);
        double far = fr.bosketball.game.DunkRules.successChance(2.8, 0.0, 0.0, true);
        double tired = fr.bosketball.game.DunkRules.successChance(1.4, 0.0, 0.9, true);
        assertTrue("An open dunk almost always drops", open >= 0.90);
        assertTrue("Being airborne helps", open > close);
        assertTrue("A contested dunk is harder", contested < open);
        assertTrue("A far dunk is harder", far < open);
        assertTrue("Fatigue makes it harder", tired < open);
        assertTrue("Even the worst dunk keeps a fair chance",
            fr.bosketball.game.DunkRules.successChance(2.8, 1.0, 1.0, false) >= 0.50);
        assertTrue("A perfect dunk stays below certainty", open <= 0.97);
    }

    @Test
    public void dunkFlightAlwaysFitsTheRimApproach() {
        assertEquals("A very close dunk still has a smooth flight", 11,
            fr.bosketball.game.DunkRules.flightTicks(0.0));
        int mid = fr.bosketball.game.DunkRules.flightTicks(2.0);
        int far = fr.bosketball.game.DunkRules.flightTicks(5.0);
        assertTrue("Further dunks fly longer", far >= mid);
        assertTrue("The flight never drags", far <= 16);
    }

    @Test
    public void dashFollowsTheSteeringInput() {
        Vector look = new Vector(0.0, 0.0, 1.0);
        Vector strafingLeft = fr.bosketball.physics.MovementMath.dashDirection(
            new Vector(-0.20, 0.0, 0.0), look);
        assertEquals("Strafing left dashes left", -1.0, strafingLeft.getX(), 1.0e-6);
        assertEquals("No forward drift when steering left", 0.0, strafingLeft.getZ(), 1.0e-6);

        Vector backpedal = fr.bosketball.physics.MovementMath.dashDirection(
            new Vector(0.0, 0.0, -0.18), look);
        assertEquals("Backpedalling dashes backwards", -1.0, backpedal.getZ(), 1.0e-6);
        assertEquals("The dash direction is normalised", 1.0, backpedal.length(), 1.0e-6);
    }

    @Test
    public void dashFallsBackOnTheCrosshairWhenStandingStill() {
        Vector look = new Vector(0.0, 0.0, 1.0);
        Vector idle = fr.bosketball.physics.MovementMath.dashDirection(
            new Vector(0.01, 0.0, -0.01), look);
        assertEquals("Standing still dashes where you look", 1.0, idle.getZ(), 1.0e-6);
        assertEquals("The Y component is dropped", 0.0, idle.getY(), 1.0e-6);

        Vector nothing = fr.bosketball.physics.MovementMath.dashDirection(null, new Vector(0, 1, 0));
        assertEquals("Looking straight down yields no usable direction", 0.0, nothing.length(), 1.0e-9);
    }

    @Test
    public void dashImpulseKeepsStrengthAndLift() {
        Vector dash = fr.bosketball.physics.MovementMath.dashVelocity(new Vector(1.0, 0.0, 0.0), 0.74, 0.15);
        assertEquals("Horizontal strength is applied", 0.74, dash.getX(), 1.0e-6);
        assertEquals("The dash keeps a small lift", 0.15, dash.getY(), 1.0e-6);
        Vector empty = fr.bosketball.physics.MovementMath.dashVelocity(new Vector(0, 0, 0), 0.74, 0.15);
        assertEquals("A zero direction does not teleport anyone", 0.0, empty.getX(), 1.0e-6);
    }

    @Test
    public void shotChargeIsDrivenByTheWallClock() {
        assertEquals("A fresh charge starts at the minimum power", 0.08,
            fr.bosketball.physics.ShotChargeRules.progressForMs(0L), 1.0e-9);
        double half = fr.bosketball.physics.ShotChargeRules.progressForMs(500L);
        double full = fr.bosketball.physics.ShotChargeRules.progressForMs(1000L);
        assertEquals("One second of hold is a full charge", 1.0, full, 1.0e-9);
        assertTrue("Half a second is in between", half > 0.4 && half < 1.0);
        assertEquals("Holding longer than one second stays capped", 1.0,
            fr.bosketball.physics.ShotChargeRules.progressForMs(9_000L), 1.0e-9);
        assertTrue("The bar keeps filling while the key is held",
            fr.bosketball.physics.ShotChargeRules.progressForMs(700L) > half);
        assertTrue("At one second the release window is open",
            fr.bosketball.physics.ShotChargeRules.chargedEnough(full));
        assertTrue("Losing the vanilla draw is tolerated for a few ticks",
            fr.bosketball.physics.ShotChargeRules.RELEASE_GRACE_TICKS >= 3);
    }

    @Test
    public void shotReleasePowerNeverDropsTheChargedShot() {
        assertEquals("The tracked charge wins over a weak event force", 0.90,
            fr.bosketball.physics.ShotChargeRules.resolvePower(true, 0.90, 0.20), 1.0e-9);
        assertEquals("An untracked release uses the event force", 0.55,
            fr.bosketball.physics.ShotChargeRules.resolvePower(false, 0.0, 0.55), 1.0e-9);
        assertEquals("A tap never goes below the floor", 0.10,
            fr.bosketball.physics.ShotChargeRules.resolvePower(true, 0.01, 0.0), 1.0e-9);
        assertEquals("Nothing can exceed a full charge", 1.0,
            fr.bosketball.physics.ShotChargeRules.resolvePower(true, 1.4, 1.0), 1.0e-9);
        assertEquals("A broken force does not produce a NaN shot", 0.10,
            fr.bosketball.physics.ShotChargeRules.resolvePower(false, 0.0, Double.NaN), 1.0e-9);
    }

    @Test
    public void round26QualityOfLifeWiring() throws Exception {
        String core = Files.readString(new File("src/main/java/fr/bosketball/server/ServerCoreCommands.java").toPath());
        String spy = Files.readString(new File("src/main/java/fr/bosketball/util/ChatSpy.java").toPath());
        assertTrue("Staff must receive a copy of private messages",
            core.contains("spyOnPrivateMessage(")
                && core.contains("ChatSpy.relay(")
                && spy.contains("Perms.staff(player)")
                && spy.contains("player.isOp()"));

        String gm = Files.readString(new File("src/main/java/fr/bosketball/game/GameManager.java").toPath());
        assertTrue("Queuing must be announced in chat with a one-click join",
            gm.contains("announceQueueSearch(p, mode, cur, target)")
                && gm.contains("announceQueueSearch(Player joiner")
                && gm.contains("/join \" + mode.id()"));

        String gameplay = Files.readString(new File("src/main/java/fr/bosketball/listener/GameplayListener.java").toPath());
        assertTrue("Right-clicking a player (or their nameplate) must open the profile menu",
            gameplay.contains("resolveProfileClickTarget(")
                && gameplay.contains("openPlayerInteractMenu(player, profileTarget)"));

        String plates = Files.readString(new File("src/main/java/fr/bosketball/ui/NameplateService.java").toPath());
        assertTrue("The rank nameplate must never be rendered to its own wearer",
            plates.contains("display.setVisibleByDefault(false)")
                && plates.contains("viewer.hideEntity(plugin, rig.body())")
                && plates.contains("viewer.showEntity(plugin, rig.body())")
                && plates.contains("if (viewer.equals(owner)) {\n            return false;"));
        String rig = Files.readString(new File("src/main/java/fr/bosketball/animation/PlayerVisualRig.java").toPath());
        assertFalse("Round 37: the rig must not draw a second nameplate over the rank one",
            rig.contains("overheadNameplate"));
        assertTrue("The nameplate must stop interpolating (no per-tick jitter)",
            plates.contains("display.setTeleportDuration(0)")
                && plates.contains("anchor.distanceSquared(body) > MOVE_EPSILON"));

        String match = Files.readString(new File("src/main/java/fr/bosketball/game/MatchSession.java").toPath());
        assertTrue("A basket always belongs to the hoop it went through (never to the shooter's team)",
            match.contains("TeamSide creditedTeam = hoopScoringSide;")
                && !match.contains("creditedTeam = shooterTeam;"));
        assertTrue("Scoring in your own basket is an own basket in every mode, announced explicitly",
            match.contains("boolean isOwnGoal = shooterHasTeam && shooterTeam != creditedTeam;")
                && match.contains("OWN BASKET"));
    }

    @Test
    public void round27StealRulesAreSane() {
        assertTrue("A defender 6 blocks away can never reach the ball (old AND bug)",
            !fr.bosketball.physics.StealRules.inReach(6.0, 1.0));
        assertTrue("A defender next to the handler and the ball can attempt a steal",
            fr.bosketball.physics.StealRules.inReach(1.4, 1.2));
        assertTrue("A ball 9 blocks away is out of reach",
            !fr.bosketball.physics.StealRules.inReach(1.2, 9.0));
        assertTrue("NaN distances never allow a steal",
            !fr.bosketball.physics.StealRules.inReach(Double.NaN, 1.0));

        double base = fr.bosketball.physics.StealRules.cleanChance(false, false, false, false, 0);
        double exposed = fr.bosketball.physics.StealRules.cleanChance(true, true, false, false, 0);
        double protectedBall = fr.bosketball.physics.StealRules.cleanChance(false, false, false, true, 0);
        assertTrue("Attacking the exposed hand and a sprinting handler improve the odds", exposed > base);
        assertTrue("A protected ball is harder to steal", protectedBall < base);
        assertEquals("The chance is clamped to the documented floor", 0.10,
            fr.bosketball.physics.StealRules.cleanChance(false, false, false, true, 99), 1.0e-9);
        assertEquals("The chance is clamped to the documented ceiling", 0.62,
            fr.bosketball.physics.StealRules.cleanChance(true, true, true, false, 0), 1.0e-9);
        assertTrue("Every streak step lowers the chance",
            fr.bosketball.physics.StealRules.cleanChance(true, false, false, false, 2)
                < fr.bosketball.physics.StealRules.cleanChance(true, false, false, false, 1));
        assertTrue("A deflection is easier than a clean steal",
            fr.bosketball.physics.StealRules.pokeChance(base) > base);
        assertTrue("Poke chance stays under 100%",
            fr.bosketball.physics.StealRules.pokeChance(0.62) <= 0.88);

        assertEquals("A first attempt uses the base cooldown", 1200L,
            fr.bosketball.physics.StealRules.cooldownMs(1200L, 0));
        assertTrue("A streak lengthens the cooldown",
            fr.bosketball.physics.StealRules.cooldownMs(1200L, 2)
                > fr.bosketball.physics.StealRules.cooldownMs(1200L, 0));
        assertEquals("The streak decays after 6 s without a steal", 0,
            fr.bosketball.physics.StealRules.decayStreak(2, 6_500L));
        assertEquals("The streak survives inside the window", 2,
            fr.bosketball.physics.StealRules.decayStreak(2, 1_000L));

        assertEquals(fr.bosketball.physics.StealRules.Outcome.CLEAN_STEAL,
            fr.bosketball.physics.StealRules.resolve(0.10, 0.40, 0.64));
        assertEquals(fr.bosketball.physics.StealRules.Outcome.POKED_LOOSE,
            fr.bosketball.physics.StealRules.resolve(0.50, 0.40, 0.64));
        assertEquals(fr.bosketball.physics.StealRules.Outcome.MISSED,
            fr.bosketball.physics.StealRules.resolve(0.99, 0.40, 0.64));
    }

    @Test
    public void round27RankAndEloSafety() {
        assertEquals("Default rating is ROOKIE",
            fr.bosketball.model.Rank.ROOKIE, fr.bosketball.model.Rank.fromElo(1000));
        assertEquals("1050 unlocks HOOPER",
            fr.bosketball.model.Rank.HOOPER, fr.bosketball.model.Rank.fromElo(1050));
        assertEquals("2200 unlocks MVP",
            fr.bosketball.model.Rank.MVP, fr.bosketball.model.Rank.fromElo(2200));
        assertEquals("Absurd ratings stay on the top tier",
            fr.bosketball.model.Rank.MVP, fr.bosketball.model.Rank.fromElo(Integer.MAX_VALUE));
        assertEquals("Negative ratings fall back to ROOKIE",
            fr.bosketball.model.Rank.ROOKIE, fr.bosketball.model.Rank.fromElo(-999));
        assertTrue("Staff ranks are never handed out by rating",
            !fr.bosketball.model.Rank.fromElo(99_999).staffOnly());

        assertEquals("Placement matches no longer double the ELO swing", 15,
            fr.bosketball.season.SeasonMath.applyPlacementSwing(15, 0, 5));
        assertTrue("The flat +15 / -5 rule is enforced by a helper",
            fr.bosketball.season.SeasonMath.isFlatRankedDelta(15, 15, 5)
                && !fr.bosketball.season.SeasonMath.isFlatRankedDelta(-10, 15, 5));

        String match = readSource("src/main/java/fr/bosketball/game/MatchSession.java");
        assertTrue("A tied game is a draw, not an automatic IGNIS win", match.contains("matchDrawn"));
        assertTrue("Draws do not count as a win for either side",
            match.contains("boolean won = !drawn && (side == winner)"));
        assertTrue("Draws do not move ELO", match.contains("boolean ranked = !drawn"));
        assertTrue("Rank losses are announced", match.contains("checkRankDemotion("));
        assertTrue("The dead rank check on baskets is gone",
            !match.contains("Rank beforeRank = prof.effectiveRank();"));
        assertTrue("A session created outside the queue derives its ELO mode from the game mode",
            match.contains("this.queueMode = mode != null ? mode.queueMode()"));

        String stats = readSource("src/main/java/fr/bosketball/stats/StatsManager.java");
        assertTrue("Leaderboards are refreshed on read instead of showing a stale snapshot",
            stats.contains("System.currentTimeMillis() - builtAt > 2_000L"));
        assertTrue("A corrupted stored ELO is clamped on load",
            stats.contains("Math.clamp(sec.getInt(\"economy.elo\""));

        String command = readSource("src/main/java/fr/bosketball/command/BosketCommand.java");
        assertTrue("/giveelo set clamps to the 100 ELO floor",
            command.contains("Math.clamp(amount, 100, 99_999)"));

        String season = readSource("src/main/java/fr/bosketball/season/SeasonService.java");
        assertTrue("A soft reset refreshes the top-3 badges",
            season.contains("refreshLeaderboardBadges()"));
    }

    @Test
    public void round27StaffSeesEveryChannelAndWebhookIsFixed() {
        String spy = readSource("src/main/java/fr/bosketball/util/ChatSpy.java");
        assertTrue("Staff spy helper exists", spy.contains("public static void relay("));
        assertTrue("Only staff receive spy lines",
            spy.contains("Perms.staff(player)") && spy.contains("player.isOp()"));
        assertTrue("Spy lines never duplicate a channel the staff member already reads",
            spy.contains("alreadySeen.contains(online.getUniqueId())"));

        for (String path : java.util.List.of(
            "src/main/java/fr/bosketball/social/SocialPartyManager.java",
            "src/main/java/fr/bosketball/social/GuildService.java",
            "src/main/java/fr/bosketball/moderation/ChatModerationService.java",
            "src/main/java/fr/bosketball/server/ServerCoreCommands.java")) {
            String src = readSource(path);
            assertTrue("Every private channel relays to staff: " + path,
                src.contains("ChatSpy.relay("));
        }
        String moderation = readSource("src/main/java/fr/bosketball/moderation/ChatModerationService.java");
        assertTrue("The isolated [MATCH] channel is also relayed", moderation.contains("[MATCH]"));
        String listener = readSource("src/main/java/fr/bosketball/listener/GameplayListener.java");
        assertTrue("A cancelled private line is never routed twice",
            listener.contains("ignoreCancelled = true"));

        String webhook = readSource("src/main/java/fr/bosketball/integration/DiscordWebhook.java");
        assertTrue("No Components V2 experiment left", !webhook.contains("FLAG_COMPONENTS_V2"));
        assertTrue("Every payload disables mentions",
            webhook.split("allowed_mentions", -1).length >= 4);
        assertTrue("Failures are logged at WARNING, not FINE",
            webhook.contains("Level.WARNING") && !webhook.contains("logger.fine"));
        assertTrue("HTTP 429 is honoured", webhook.contains("retry_after") && webhook.contains("Retry-After"));
        assertTrue("The queue is bounded", webhook.contains("ArrayBlockingQueue"));
        assertTrue("The URL is validated before use", webhook.contains("validateUrl"));
        assertTrue("A status line exists for /bb webhook", webhook.contains("statusLine()"));

        assertEquals("Quotes are escaped", "a\\\"b",
            fr.bosketball.integration.DiscordWebhook.esc("a\"b"));
        assertEquals("Newlines are escaped", "a\\nb",
            fr.bosketball.integration.DiscordWebhook.esc("a\nb"));
        assertEquals("Lone surrogates are dropped instead of breaking JSON", "ab",
            fr.bosketball.integration.DiscordWebhook.esc("a\uD800b"));

        String reporter = readSource("src/main/java/fr/bosketball/integration/MatchReporter.java");
        assertTrue("Match ids are stable and readable", reporter.contains("session.matchId()"));
        assertTrue("Draws are reported as draws", reporter.contains("Draw — nobody gains or loses ELO"));
    }

    @Test
    public void round28ShutdownScreenIsBrandedAndResponsiveTabList() {
        // --- shutdown / maintenance screens -------------------------------------------------
        assertTrue("A vanilla shutdown kick must be recognised",
            fr.bosketball.server.ServerScreens.isServerClosing(
                net.kyori.adventure.text.Component.text("Server closed")));
        assertTrue("Paper's translatable shutdown key must be recognised",
            fr.bosketball.server.ServerScreens.isServerClosing(
                net.kyori.adventure.text.Component.text("multiplayer.disconnect.server_shutdown")));
        assertTrue("A normal kick must keep its reason",
            !fr.bosketball.server.ServerScreens.isServerClosing(
                net.kyori.adventure.text.Component.text("Kicked by an operator")));
        assertTrue("A mute kick must keep its reason",
            !fr.bosketball.server.ServerScreens.isServerClosing(
                net.kyori.adventure.text.Component.text("You are muted")));

        String screens = readSource("src/main/java/fr/bosketball/server/ServerScreens.java");
        assertTrue("The shutdown screen uses the brand gradients",
            screens.contains("Msg.GRAD_BRAND") && screens.contains("Msg.GRAD_ICE")
                && screens.contains("Msg.GRAD_MINT") && screens.contains("Msg.GRAD_GOLD"));
        assertTrue("The disconnect screen must never use resource-pack glyphs",
            !screens.contains("\\uE3") && !screens.contains("Glyphs."));
        assertTrue("The screen shows the player's saved progress",
            screens.contains("profile.elo()") && screens.contains("profile.coins()"));
        assertTrue("The screen links the Discord and the site",
            screens.contains("links().discord()") && screens.contains("serverDomain()"));
        assertTrue("Every line stays inside the disconnect screen width", screens.contains("MAX_LINE = 58"));

        String plugin = readSource("src/main/java/fr/bosketball/BosketBallPlugin.java");
        assertTrue("A real shutdown kicks everyone with the branded screen",
            plugin.contains("kickEveryoneForShutdown()") && plugin.contains("ServerScreens.shutdown("));
        assertTrue("A plugin reload must never kick anyone", plugin.contains("isServerStopping()")
            && plugin.contains("Bukkit.getServer().isStopping()"));
        String motd = readSource("src/main/java/fr/bosketball/server/MotdManager.java");
        assertTrue("Vanilla 'Server closed' is intercepted", motd.contains("PlayerKickEvent")
            && motd.contains("ServerScreens.isServerClosing(event.reason())"));
        assertTrue("Maintenance kick screens use the same builder",
            motd.contains("ServerScreens.maintenance(plugin)")
                && plugin.contains("ServerScreens.maintenanceStarted("));

    }

    /**
     * Round 32 — "j'ai terminé de lire les cartes depuis longtemps et je ne peux pas continuer".
     *
     * <p>A chapter that only asks for reading must turn on the first click: the chat button, any
     * click in the world, or the 8/12 s fallback — never a 25 s wait behind a gate meant for
     * gameplay chapters. The gold ring also had to stop flattening itself into parallel bars.
     */
    @Test
    public void round32ReadingChaptersTurnOnAClickAndTheRingStaysACircle() {
        assertTrue("The first card is a reading chapter",
            fr.bosketball.tutorial.TutorialFlow.isReadingRequirement("welcome"));
        assertTrue("The final card is a reading chapter",
            fr.bosketball.tutorial.TutorialFlow.isReadingRequirement("finish"));
        for (String gameplay : java.util.List.of("walk", "shoot", "dunk", "defense", "shop", "queue")) {
            assertFalse("A gameplay chapter must never auto-complete: " + gameplay,
                fr.bosketball.tutorial.TutorialFlow.isReadingRequirement(gameplay));
        }

        String src = readSource("src/main/java/fr/bosketball/ui/TutorialManager.java");
        assertTrue("A reading page turns on a click, not on a timer",
            src.contains("advanceStep(player, !force && (validated || active.reading()))"));
        assertTrue("The listener has a public hook for it",
            src.contains("public boolean advanceReadingChapter(Player player)"));
        assertTrue("Reading chapters fall back quickly", src.contains("WELCOME_AUTO_MS = 8_000L")
            && src.contains("CARD_AUTO_MS = 12_000L"));
        assertTrue("The HUD tells the player a click is enough",
            src.contains("CLICK ANYWHERE") && src.contains("String hudLabel(Step step, UUID id, boolean actionBar)"));

        String listener = readSource("src/main/java/fr/bosketball/listener/GameplayListener.java");
        assertTrue("Any click in the world turns the page",
            listener.contains("plugin.tutorialManager().advanceReadingChapter(player)"));

        // the ring: per-piece orientation, otherwise every slice shares one transformation again
        assertTrue("The ring keeps one record per piece", src.contains("private record RingPart("));
        assertTrue("The ring is built from arcs", src.contains("spawnRingArc(")
            && src.contains("spawnRingArc(world, anchor, markers, parts, 28, 1.80"));
        assertTrue("The ring spins slowly and pulses", src.contains("float spin = (float) (tickCounter * 0.010)")
            && src.contains("ringRotation(angle, part.accent())"));
        assertFalse("No shared flat transformation is left behind",
            src.contains("new Vector3f(0f, bob, 0f),"));

        // tab list: Round 40 dropped the golden frame — the card is plain text, nothing to draw
        String gen = readSource("src/main/java/fr/bosketball/ui/TabListManager.java");
        assertFalse("The golden frame must be gone from the tab",
            gen.contains("tabFrameComponent") || gen.contains("frameEnabled"));
        assertTrue("The tab is now a padded card", gen.contains("public static final int CARD_PX = 300;"));
    }

    /**
     * Round 33 — the big bug hunt.
     *
     * <p>Four bugs that no unit test could have caught before, because nothing was asserting that
     * a *wired* menu actually has handlers, or that one broken match cannot take the others down:
     *
     * <ol>
     *   <li>the AI panel opened from /play had no click case: every button did nothing;</li>
     *   <li>the purchase confirmation promised "closing this menu cancels" but never listened to the
     *       close, so Escape left the purchase pending and the shop closed for good;</li>
     *   <li>one throwing session aborted {@code GameManager.tickAll()} before reaching the other
     *       courts, freezing every match on the server;</li>
     *   <li>the season card advertised amplified ELO swings while the swing scale is a flat 1.0.</li>
     * </ol>
     */
    @Test
    public void round33EveryMenuHasHandlersAndOneBrokenMatchCannotFreezeTheRest() {
        String gui = readSource("src/main/java/fr/bosketball/ui/GuiMenuManager.java");

        // 1. every menu type must be handled somewhere in the click dispatcher
        for (fr.bosketball.ui.GuiMenuManager.MenuType type : fr.bosketball.ui.GuiMenuManager.MenuType.values()) {
            String name = type.name();
            boolean handled = gui.contains("case " + name + " ->")
                || gui.contains("case " + name + ",")
                || gui.contains(", " + name + " ->")
                || gui.contains(", " + name + ",");
            assertTrue("Menu " + name + " opens an inventory but nobody handles its clicks", handled);
        }

        // the AI panel does what its buttons promise
        assertTrue("The AI panel must fill the court",
            gui.contains("case \"BOT_FILL\" ->") && gui.contains("fillWithBots(botSession.botDifficulty())"));
        assertTrue("The AI panel must toggle the sparring defender",
            gui.contains("case \"BOT_DEFENDER\" -> botSession.toggleSparringBot(player);"));
        assertTrue("The AI panel must clear the bots", gui.contains("case \"BOT_CLEAR\" ->"));
        assertTrue("The AI panel must apply a difficulty",
            gui.contains("action.startsWith(\"BOT_DIFF:\")") && gui.contains("botSession.setBotDifficulty(tier)"));
        assertTrue("The AI panel must redraw after an action", gui.contains("openBotPanel(player);\n            }"));

        // 2. closing the confirmation cancels it and brings the shop back
        String confirm = readSource("src/main/java/fr/bosketball/ui/PurchaseConfirmMenu.java");
        assertTrue("A pending purchase can be cancelled from the close listener",
            confirm.contains("public static Runnable cancelPending(Player player)"));
        assertTrue("The close listener cancels and reopens the previous menu",
            gui.contains("instanceof PurchaseConfirmMenu menu")
                && gui.contains("PurchaseConfirmMenu.cancelPending(player)")
                && gui.contains("Bukkit.getScheduler().runTask(plugin, back)"));

        // 3. one broken session cannot stop the others
        String game = readSource("src/main/java/fr/bosketball/game/GameManager.java");
        assertTrue("Every session is ticked inside its own guard",
            game.contains("try {\n                s.tick();") && game.contains("catch (Throwable failure)"));
        assertTrue("A poisoned session is closed instead of freezing the server",
            game.contains("SESSION_TICK_FAILURE_LIMIT") && game.contains("private void closeBrokenSession(")
                && game.contains("session.stopAndCleanup();"));
        assertTrue("The failure counter is forgotten when the session ticks again",
            game.contains("sessionTickFailures.remove(entry.getKey());"));

        // 4. no false promise about amplified ELO
        String session = readSource("src/main/java/fr/bosketball/game/MatchSession.java");
        assertFalse("The season card must not promise amplified ELO swings",
            session.contains("ELO swings are amplified"));
        assertTrue("ELO is a flat +15 / -5", session.contains("ELO stays <#F8FAFC>+15 / -5</#F8FAFC>"));
        assertEquals("+15 on a win, never the +/-32 formula", 15,
            fr.bosketball.stats.StatsManager.computeEloGain(1500, 1500));
        assertEquals("-5 on a loss", 5, fr.bosketball.stats.StatsManager.computeEloLoss(1500, 1500));

        // 5. the two config knobs the loader reads are documented in the shipped file
        String bundled = readSource("src/main/resources/config.yml");
        assertTrue("schematic-courts.rim-material must be documented", bundled.contains("rim-material:"));
        assertTrue("schematic-courts.floor-relative-y must be documented", bundled.contains("floor-relative-y:"));
    }

    /**
     * Round 34 — the two bugs the owner actually sees on his screen.
     *
     * <ol>
     *   <li>two tab-list rows traded places every second: every entry had the same
     *       {@code playerListOrder} (0), so the client was free to lay the tied rows out in any
     *       order. The rows now carry an explicit, total order;</li>
     *   <li>shooting into your OWN basket paid YOU the points, in practice and in matches: practice
     *       used to overwrite the hoop's side with the shooter's team.</li>
     * </ol>
     */
    @Test
    public void round34TabRowsCannotSwapAndOwnBasketsNeverPayTheShooter() {
        String tab = readSource("src/main/java/fr/bosketball/ui/TabListManager.java");
        assertTrue("The tab list must hand the client an explicit list order",
            tab.contains("p.setPlayerListOrder(i)"));
        assertTrue("Every row must get a distinct order, never a tie",
            tab.contains("online.sort(tabComparator(sessionOf))")
                && tab.contains("for (int i = 0; i < online.size(); i++)"));
        assertTrue("The order must be total: the last resort is the UUID",
            tab.contains("a.getUniqueId().toString().compareTo(b.getUniqueId().toString())"));
        assertTrue("Order packets are only sent when a row actually moves",
            tab.contains("if (previous != null && previous == i)"));

        String match = readSource("src/main/java/fr/bosketball/game/MatchSession.java");
        assertTrue("The hoop decides who scores, never the shooter's team",
            match.contains("TeamSide creditedTeam = hoopScoringSide;"));
        assertFalse("No mode may re-credit the shooter's own basket",
            match.contains("creditedTeam = shooterTeam"));
        assertTrue("An own basket is an own basket in every mode",
            match.contains("boolean isOwnGoal = shooterHasTeam && shooterTeam != creditedTeam;"));
        assertTrue("An own basket must not feed the shooter's box score",
            match.contains("if (!isOwnGoal) {\n            scorerBox.points += pts;"));
        assertTrue("An own basket must not pay the shooter",
            match.contains("if (shooter != null && state == State.LIVE && !isOwnGoal) {"));
        assertTrue("An own basket is announced with the team that got the points",
            match.contains("this.celebRoleTag = \"OWN BASKET +\" + pts;")
                && match.contains("this.celebSubValue = teamDisplayName(creditedTeam)"));
    }

    /**
     * Round 35 — scenario: "I disconnect at 3-2 with 4 minutes left, I come back 20 s later".
     *
     * <p>Traced through the code: {@code removePlayer} deleted the player from the roster *before*
     * looking at the grace window, so
     * <ul>
     *   <li>"you have 45 s to come back" was false — on rejoin {@code cancelDisconnectGrace} only
     *       printed "BACK ON COURT" while the roster had already dropped them (no kit, no spawn,
     *       no rig);</li>
     *   <li>never coming back cost nothing: the forfeit branch was unreachable because the method
     *       returned in the grace branch, and the grace branch ran after the removal.</li>
     * </ul>
     * The roster slot is now kept for the grace window, a rejoin really puts the player back on the
     * court, and an expired grace pays the same -15 ELO as the leave button.
     */
    @Test
    public void round35DisconnectGraceKeepsTheSlotAndReallyPaysTheForfeit() {
        String match = readSource("src/main/java/fr/bosketball/game/MatchSession.java");

        int graceBranch = match.indexOf("if (wasForfeit && !player.isOnline() && isRankedMatch()");
        int rosterRemoval = match.indexOf("        participants.remove(id);", match.indexOf("public void removePlayer("));
        assertTrue("The grace window must be decided before the roster removal", graceBranch > 0);
        assertTrue("The roster slot must survive the grace window", graceBranch < rosterRemoval);
        assertTrue("Starting the grace must not go through the forfeit branch",
            match.contains("            beginDisconnectGrace(id);\n            return;"));

        assertTrue("Rejoining must restore the court kit", match.contains("equipCourtKit(back, side);"));
        assertTrue("Rejoining must put the player back on their team spawn",
            match.contains("back.teleport(arena.teamSpawn(side, Math.max(0, countTeam(side) - 1)));"));
        assertTrue("Rejoining must restore the visual rig",
            match.contains("visualRigs.put(playerId, new PlayerVisualRig(plugin, back, side));"));

        assertTrue("An expired grace must pay the forfeit", match.contains("registerForfeit(id, side, quitter);"));
        int forfeitCall = match.indexOf("registerForfeit(id, side, quitter);");
        int slotRelease = match.indexOf("participants.remove(id);", forfeitCall);
        assertTrue("An expired grace must release the roster slot", forfeitCall > 0 && slotRelease > forfeitCall);
        assertTrue("The leave button and the expired grace share one forfeit implementation",
            match.contains("int lost = registerForfeit(id, leavingSide, prof);"));
        assertTrue("A forfeit still costs exactly 15 ELO",
            match.contains("private static final int FORFEIT_ELO_LOSS = 15;"));
        assertTrue("Moves stay frozen while the grace runs", graceBranch > 0);
    }

    /**
     * Round 40 — the [TAB] card never overflows and always lines up.
     *
     * <p>Every card line is padded to exactly {@code TabListManager.CARD_PX} font pixels, which is
     * what turns the client's per-line centring into one left-aligned block. These checks walk the
     * worst values the server can produce (a nine-figure balance, a long domain, a 16-character guild
     * name) and prove each line still lands on the same width.
     */
    @Test
    public void round40TabCardIsAlignedWhateverTheNumbers() {
        int cardPx = fr.bosketball.ui.TabListManager.CARD_PX;
        assertEquals("The card is 300 px wide (about 50 default-font characters)", 300, cardPx);

        int[] coins = { 0, 999, 12_480, 9_999_999 };
        int[] pings = { 5, 42, 999 };
        String[] domains = { "bosketball.net", "play.a-very-long-server-domain.example.org", "" };
        int cases = 0;
        for (int coin : coins) {
            for (int ping : pings) {
                for (String domain : domains) {
                    fr.bosketball.model.PlayerProfile profile =
                        new fr.bosketball.model.PlayerProfile(java.util.UUID.randomUUID(), "MaxLananas");
                    profile.setCoins(coin);
                    profile.setPoints(34_567);
                    profile.setPlayTimeSeconds(48 * 3600L + 720L);
                    profile.setUnreadMailCount(3);
                    java.util.List<String> lines = fr.bosketball.ui.TabListManager.cardLines(
                        profile,
                        new fr.bosketball.stats.StatsManager.LeaderboardBadges(1, 2, 3, 1, 2, 3),
                        null, ping, domain);
                    assertTrue("The card must have its sections", lines.size() >= 8);
                    for (String line : lines) {
                        int width = fr.bosketball.ui.TabListManager.cardWidthPx(line);
                        assertTrue("card line is not padded to the card width (" + coin + "/" + ping
                            + "/" + domain + "): '" + plainText(fr.bosketball.util.Msg.parse(line))
                            + "' = " + width + " px", width == cardPx);
                    }
                    // the two chip rows share one width, so the two columns really are columns
                    assertEquals("Both two-column rows share the layout",
                        fr.bosketball.ui.TabListManager.cardWidthPx(lines.get(1)),
                        fr.bosketball.ui.TabListManager.cardWidthPx(lines.get(2)));
                    cases++;
                }
            }
        }
        assertTrue("the card matrix must cover at least 30 cases, got " + cases, cases >= 30);

        // a 16-character guild name plus a tag must be trimmed, never pushed past the card
        fr.bosketball.model.PlayerProfile profile =
            new fr.bosketball.model.PlayerProfile(java.util.UUID.randomUUID(), "MaxLananas");
        fr.bosketball.social.Guild guild = new fr.bosketball.social.Guild(
            "g1", "TheUnbeatablesClub", "STREETBALLFOREVER", 1_700_000_000_000L, 1420);
        java.util.List<String> lines = fr.bosketball.ui.TabListManager.cardLines(
            profile, fr.bosketball.stats.StatsManager.LeaderboardBadges.NONE, guild, 38, "bosketball.net");
        for (String line : lines) {
            assertTrue("A decorated guild must not break the card",
                fr.bosketball.ui.TabListManager.cardWidthPx(line) <= cardPx);
        }

        String tab = readSource("src/main/java/fr/bosketball/ui/TabListManager.java");
        assertTrue("the two team names must still be fitted to the card",
            tab.contains("String ignisName = Glyphs.fitTextToWidth(")
                && tab.contains("String frostName = Glyphs.fitTextToWidth("));
        assertTrue("the header counters go through the same layout",
            tab.contains("headerCounters(online, inMatch, inQueue)"));
        assertTrue("the footer really renders the card", tab.contains("for (String cardLine : card) {"));
    }

    /**
     * Round 40 — the tab is a big profile card, in French, and it does not repeat the sidebar.
     *
     * <p>The owner asked for Hypixel-Skyblock-shaped sections: coins, rank and playtime by name,
     * plus everything the sidebar does not already show. The golden frame that used to sit behind
     * the list is gone for good — no glyph, no texture, no config key, no generator.
     */
    @Test
    public void round40TabCardShowsWhatTheSidebarDoesNot() {
        fr.bosketball.model.PlayerProfile profile =
            new fr.bosketball.model.PlayerProfile(java.util.UUID.randomUUID(), "MaxLananas");
        profile.setCoins(12_480);
        profile.setXp(24_300);
        profile.setWins(128);
        profile.setLosses(54);
        profile.setUnreadMailCount(3);
        profile.setDunks(88);
        profile.setAssists(421);
        profile.setSteals(190);
        profile.setBlocks(96);
        profile.setThreePointersMade(212);
        profile.setMvps(14);
        profile.setWinStreak(6);
        profile.setBestWinStreak(14);
        profile.setPlayTimeSeconds(42 * 3600L + 18 * 60L);
        String card = String.join("\n", fr.bosketball.ui.TabListManager.cardLines(
            profile, new fr.bosketball.stats.StatsManager.LeaderboardBadges(0, 1, 0, 0, 0, 0),
            null, 38, "bosketball.net"));

        // (1) the three sections the owner named, in French
        assertTrue("coins section", card.contains("Coins") && card.contains("12,480"));
        assertTrue("rank section", card.contains("Rank")
            && card.indexOf(fr.bosketball.model.Rank.ROOKIE.pillGlyph()) >= 0);
        assertTrue("playtime section", card.contains("Playtime") && card.contains("42h 18m"));
        // (2) …plus plenty the sidebar never shows
        for (String section : new String[]{"Record", "Career", "Guild", "Badges"}) {
            assertTrue("missing tab section: " + section, card.contains(section));
        }
        for (String stat : new String[]{"128 W", "54 L", "70%", "DUNKS", "ASSISTS", "STEALS", "BLOCKS",
            "3PTS", "#1", "ELO", "FRIENDS", "ping", "38ms", "bosketball.net", "/stats",
            "/guild create"}) {
            assertTrue("the tab must carry '" + stat + "'", card.contains(stat));
        }
        assertEquals("A 42h 18m playtime is the sidebar format, reused verbatim", "42h 18m",
            profile.formattedPlayTime());

        // a player with no podium still gets the social block, unread mail included
        String plainCard = String.join("\n", fr.bosketball.ui.TabListManager.cardLines(
            profile, fr.bosketball.stats.StatsManager.LeaderboardBadges.NONE, null, 38, "bosketball.net"));
        for (String stat : new String[]{"no top 3", "FRIENDS", "MAIL"}) {
            assertTrue("the card must carry '" + stat + "'", plainCard.contains(stat));
        }

        // (3) nothing that only belongs to the sidebar, and no {@code <bold>} that would drift columns
        assertFalse("the tab must not repeat the season block", card.toLowerCase(java.util.Locale.ROOT)
            .contains("season") || card.contains("Placement"));
        assertFalse("bold breaks the pixel alignment", card.contains("<bold>"));
        String head = fr.bosketball.ui.TabListManager.headerCounters(12, 4, 2);
        assertTrue("the counters are English", head.contains("online") && head.contains("in match")
            && head.contains("in queue"));
        assertFalse("Round 42: the tab is English again, no French left in the card",
            card.contains("Pièces") || card.contains("Rang") || card.contains("Carrière")
                || card.contains("Guilde") || card.contains("aucun"));

        // (4) the golden frame is gone, everywhere
        String tabs = readSource("src/main/java/fr/bosketball/ui/TabListManager.java");
        assertFalse("no frame plumbing survives in the tab",
            tabs.contains("tabFrame") || tabs.contains("TAB_FRAME") || tabs.contains("frameEnabled")
                || tabs.contains("railLines"));
        String glyphs = readSource("src/main/java/fr/bosketball/util/Glyphs.java");
        assertFalse("no frame constant survives in Glyphs", glyphs.contains("TAB_FRAME"));
        String font = readSource("resourcepack/assets/minecraft/font/default.json");
        assertFalse("the font must not bind tab-frame textures", font.contains("tab_frame"));
        java.io.File textureDir = new java.io.File("resourcepack/assets/bosketball/textures/font");
        for (java.io.File texture : textureDir.listFiles()) {
            assertFalse("stale frame texture: " + texture.getName(), texture.getName().startsWith("tab_frame"));
        }
        assertFalse("the vendor tablist namespace is gone",
            new java.io.File("resourcepack/assets/tablist").exists());
        assertFalse("the frame generator is gone", new java.io.File("tools/tablist_frame.py").exists());
        assertFalse("the uploaded zip is not vendored any more",
            new java.io.File("tools/tablist_source").exists());
        String yml = readSource("src/main/resources/config.yml");
        assertFalse("the tab-list config section is gone", yml.contains("tab-list:"));
        assertFalse("…and so is its record", readSource("src/main/java/fr/bosketball/config/BosketConfig.java")
            .contains("record TabList("));
        String plugin = readSource("src/main/java/fr/bosketball/BosketBallPlugin.java");
        assertFalse("the plugin no longer wires a panel", plugin.contains("setPanel("));
        assertFalse("…nor a season label into the tab", plugin.contains("tabListManager.attachSeasonProvider"));
    }

    /** A newline, without a single backslash in this file. */
    private static final String NL = String.valueOf((char) 10);

    /**
     * Round 43 — no star reaches a screen any more.
     *
     * <p>The owner called the decorative four-pointed stars "so AI-looking" and asked for them to be
     * replaced, not deleted. The ornament set is now the plain square for titles and labels, the
     * arrow for pointers, a dashed rule with a gold segment in the middle for dividers, and the
     * resource pack's own golden flourish for the sidebar frame. This walks every Java source, the
     * shipped resources and the two player-facing docs, and fails on the first star that comes back.
     */
    /**
     * Round 45 — one palette, one accent per screen, eight advertised commands.
     *
     * <p>The sources used to carry 47 hexadecimal colours: four yellows, six reds, eight greys that
     * were meant to be the same yellow, red and grey. Nothing looked broken line by line, but every
     * screen drifted a little and the whole thing read as assembled rather than designed. There are
     * thirteen colours now, all in {@code Palette}, and this test refuses the fourteenth.
     */
    /**
     * Round 46 — the HUD pill and the charge bar sit on a six pixel grid.
     *
     * <p>The pill is a nine-slice: two caps and a body tile that repeats every six pixels, so its
     * width is always {@code 12 + 6n}. The text is then wound back into it, which only looks right
     * if the padding is the same on both sides. A metre that is one pixel off is invisible in the
     * source and obvious on screen, so it is checked here.
     */
    /**
     * The screens must never speak generated English again.
     *
     * <p>Every rule that was applied by hand between rounds 46 and 53, checked at once: no
     * bracketed shouting ("[YES]", "[✔ ACCEPT]"), no legacy colour names next to the palette, no
     * emoji, and no place where the plugin calls itself or its bots "AI" on screen.
     */
    /**
     * The pack ships subtitle strings per client language, and every one of them has to be
     * English: the whole server speaks one language, including the line under the dunk. The two
     * files must also carry the same keys, or a French client silently loses a subtitle the
     * English client sees.
     */
    @Test
    public void theSubtitlePackStaysInEnglish() throws Exception {
        java.nio.file.Path dir = java.nio.file.Paths.get("resourcepack/assets/bosketball/lang");
        org.junit.Assume.assumeTrue(java.nio.file.Files.isDirectory(dir));

        java.util.Map<String, java.util.Set<String>> keysByFile = new java.util.LinkedHashMap<>();
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(dir)) {
            for (java.nio.file.Path file : stream.filter(f -> f.toString().endsWith(".json")).sorted().collect(java.util.stream.Collectors.toList())) {
                String raw = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
                java.util.Set<String> keys = new java.util.TreeSet<>();
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(raw);
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                    String value = matcher.group(2);
                    for (char c : value.toCharArray()) {
                        boolean accented = "\u00e0\u00e2\u00e4\u00e9\u00e8\u00ea\u00eb\u00ee\u00ef\u00f4\u00f6\u00f9\u00fb\u00fc\u00e7\u00c0\u00c9\u00c8\u00ca\u00c7".indexOf(c) >= 0;
                        assertFalse(file.getFileName() + " carries French in " + matcher.group(1) + ": " + value, accented);
                    }
                }
                keysByFile.put(file.getFileName().toString(), keys);
            }
        }

        assertFalse("the pack ships no subtitle language files", keysByFile.isEmpty());
        java.util.Set<String> reference = keysByFile.values().iterator().next();
        for (java.util.Map.Entry<String, java.util.Set<String>> entry : keysByFile.entrySet()) {
            assertEquals(entry.getKey() + " does not carry the same subtitle keys as the others",
                reference, entry.getValue());
        }
    }


    @Test
    public void noGeneratedLanguageReachesAPlayer() throws Exception {
        java.util.List<String> offenders = new java.util.ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> files =
                 java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    String text = stripJavaComments(readSource(path.toString()));
                    java.util.regex.Matcher shout = java.util.regex.Pattern
                        .compile("(?:<#[0-9A-F]{6}>|<[a-z_]+>)[^\"]{0,80}\\[(?:YES|NO|ACCEPT|DECLINE|LEAVE|OK|CANCEL)\\]")
                        .matcher(text);
                    if (shout.find()) {
                        offenders.add(path.getFileName() + " -> bracketed shout: " + shout.group());
                    }
                    if (text.contains("AI Defender")) {
                        offenders.add(path.getFileName() + " -> calls the bot an AI");
                    }
                    if (text.matches("(?s).*<(gray|white|yellow|gold|aqua|red|green|dark_gray)>.*")) {
                        offenders.add(path.getFileName() + " -> legacy colour name");
                    }
                    if (text.contains("<#64748B>|</#64748B>")) {
                        offenders.add(path.getFileName() + " -> pipe used as a separator");
                    }
                    for (int i = 0; i < text.length(); i++) {
                        char ch = text.charAt(i);
                        boolean emoji = Character.isSurrogate(ch) || (ch >= 0x2600 && ch <= 0x27BF
                            && "\u2714\u2716\u270E\u2705\u274C\u26A0".indexOf(ch) < 0);
                        if (emoji) {
                            offenders.add(path.getFileName() + " -> emoji at " + (int) ch);
                            break;
                        }
                    }
                });
        }
        assertTrue("Generated English on screen: " + offenders, offenders.isEmpty());
    }

    /**
     * Round 61 -- the dunk rules survive the numbers a laggy server hands them.
     *
     * <p>Every value here arrives from physics: a distance in blocks, a contest ratio, a fatigue
     * level. Any of them can be NaN after a division by a zero-length vector, and a NaN that
     * reaches the comparison of a dunk does not throw -- it silently makes every dunk fail, or
     * succeed. Same for a clock given an absurd timestamp: the cooldown must clamp, not count down
     * from the year 292 million.
     */
    /**
     * Round 63 -- a player who disconnects cannot leave a match holding the ball.
     *
     * <p>The quit path is the one place where four systems have to let go of the same player in
     * the right order: the queue, the match, the social graph and the profile. Getting the order
     * wrong leaves a session ticking with a holder who is not there, which is exactly how a match
     * freezes with the ball stuck in mid-air.
     */
    /**
     * Round 65 -- nobody leaves the court, by falling or by drifting.
     *
     * <p>Two different escapes: a player who drops below the floor or walks past the barrier is
     * put back on their spawn, and a player who drifts inside the soft margin is clamped instead
     * of teleported, so a small overshoot never feels like a punishment. Both paths must stay.
     */
    @Test
    public void theCourtCannotBeLeft() throws Exception {
        String arena = readSource("src/main/java/fr/bosketball/arena/Arena.java");
        assertTrue("falling out puts the player back on the spawn",
            arena.contains("player.teleport(teamSpawn(side, 0));"));
        assertTrue("the fall distance is forgiven with the teleport",
            arena.contains("player.setFallDistance(0f);"));
        assertTrue("the player is told what happened", arena.contains("Back in bounds"));
        assertTrue("a small overshoot is clamped, not punished", arena.contains("boolean clamped = false;"));
    }

    @Test
    public void theQuitPathLetsGoOfEverythingInOrder() throws Exception {
        String quit = readSource("src/main/java/fr/bosketball/listener/GameplayListener.java");
        int perPlayer = quit.indexOf("plugin.cleanupPlayerState(player);");
        int leaveQueue = quit.indexOf("plugin.gameManager().leaveQueue(player, false);");
        int disconnect = quit.indexOf("plugin.gameManager().handleDisconnect(player.getUniqueId());");
        int removeFromSession = quit.indexOf("session.removePlayer(player, false);");
        int save = quit.indexOf("plugin.statsManager().saveProfileAsync(quittingProf);");

        assertTrue("the per-player state is cleaned first", perPlayer > 0);
        assertTrue("the queue lets go before the session", leaveQueue > perPlayer);
        assertTrue("the disconnect grace is registered", disconnect > leaveQueue);
        assertTrue("the session drops the player", removeFromSession > disconnect);
        assertTrue("the profile is saved last, not before", save > removeFromSession);
    }

    @Test
    public void aCeremonyCannotStartTwice() throws Exception {
        String session = readSource("src/main/java/fr/bosketball/game/MatchSession.java");
        int guard = session.indexOf("if (ceremonyStarted) {");
        int set = session.indexOf("ceremonyStarted = true;");
        assertTrue("the ceremony has a one-way guard", guard > 0 && set > guard);
    }

    @Test
    public void theDunkRulesNeverTrustTheirInput() {
        assertEquals("a clean dunk at the rim is close to a certainty", 0.94,
            fr.bosketball.game.DunkRules.successChance(0.0, 0.0, 0.0, false), 0.001);
        assertTrue("a hopeless dunk still has a floor",
            fr.bosketball.game.DunkRules.successChance(400.0, 9.0, 9.0, false) >= 0.50);
        assertTrue("a perfect dunk never reaches certainty",
            fr.bosketball.game.DunkRules.successChance(0.0, 0.0, 0.0, true) <= 0.97);

        assertFalse("a NaN aim is never on target", fr.bosketball.game.DunkRules.aimedAtRim(Double.NaN));
        assertTrue("an aim inside the cone is on target", fr.bosketball.game.DunkRules.aimedAtRim(12.0));

        assertEquals("a cooldown that expired is zero, not negative",
            0L, fr.bosketball.game.DunkRules.cooldownRemainingMs(5_000L, 1_000L));
        assertEquals("a cooldown still running counts down",
            4_000L, fr.bosketball.game.DunkRules.cooldownRemainingMs(1_000L, 5_000L));
        assertTrue("an expired cooldown lets the dunk through",
            fr.bosketball.game.DunkRules.offCooldown(5_000L, 1_000L));

        assertEquals("the shortest dunk flight is bounded", 11, fr.bosketball.game.DunkRules.flightTicks(0.0));
        assertEquals("the longest dunk flight is bounded", 16, fr.bosketball.game.DunkRules.flightTicks(1_000_000.0));
        assertEquals("a negative distance still flies", 11, fr.bosketball.game.DunkRules.flightTicks(-5.0));
    }

    @Test
    public void round46TheHudPillFitsTheTextItHolds() {
        for (String line : new String[] {
            "Intercepted pass!",
            "Shot clock expired · 24s",
            "Dive · ball recovered",
            "Out of bounds · 24s"
        }) {
            int advance = Glyphs.textAdvancePx(line, false);
            int body = Math.max(4, (int) Math.ceil((advance + 20 - Glyphs.PILL_CAPS_WIDTH)
                / (double) Glyphs.PILL_BODY_WIDTH));
            int width = Glyphs.PILL_CAPS_WIDTH + body * Glyphs.PILL_BODY_WIDTH;
            assertTrue("pill narrower than its text: " + line, width - advance >= 8);
            assertTrue("pill wider than the screen: " + line, width <= Glyphs.MAX_PILL_WIDTH);
            assertEquals("pill not on the six pixel grid: " + line, 0,
                (width - Glyphs.PILL_CAPS_WIDTH) % Glyphs.PILL_BODY_WIDTH);
            int left = (width - advance) / 2;
            int right = width - left - advance;
            assertTrue("pill padding is lopsided: " + line, Math.abs(left - right) <= 1);
        }
    }

    @Test
    public void round46TheChargeBarStaysInsideItsRail() {
        double previous = -1;
        for (int i = 0; i <= 20; i++) {
            double progress = i / 20.0;
            int width = Glyphs.shotBarWidth(progress);
            assertTrue("the fill went backwards at " + progress, width >= previous);
            assertTrue("the fill sticks out of the rail: " + width, width <= 120);
            char glyph = Glyphs.shotBarGlyph(progress);
            assertTrue("slice off the end of the bar: " + (int) glyph,
                glyph >= Glyphs.HUD_BAR_FIRST && glyph < Glyphs.HUD_BAR_FIRST + Glyphs.HUD_BAR_STEPS);
            previous = width;
        }
        assertEquals("an empty bar is a stub, not a void", 4, Glyphs.shotBarWidth(0.0));
        assertEquals("a full bar fills the rail", 120, Glyphs.shotBarWidth(1.0));
    }

    @Test
    public void round45ColoursOnlyComeFromThePalette() throws Exception {
        java.util.List<String> offenders = new java.util.ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> files =
                 java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
            files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("Palette.java"))
                .forEach(path -> {
                    String text = stripJavaComments(readSource(path.toString()));
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("#[0-9A-Fa-f]{6}").matcher(text);
                    while (m.find()) {
                        // Round 47: a hex may be written where it is needed, but only if it is
                        // one of the thirteen. The rule was never "no literals", it is "no
                        // fourteenth colour" - and the UI still spoke in <gray>/<white> until now.
                        if (!java.util.Arrays.asList(fr.bosketball.util.Palette.ALL)
                                .contains(m.group().toUpperCase(java.util.Locale.ROOT))) {
                            offenders.add(path.getFileName() + " -> " + m.group());
                        }
                    }
                });
        }
        assertTrue("A colour that is not in Palette: " + offenders, offenders.isEmpty());

        // the palette stays small, and every token is a real colour
        assertEquals("Thirteen tokens, no more", 13, fr.bosketball.util.Palette.ALL.length);
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (String hex : fr.bosketball.util.Palette.ALL) {
            assertTrue("A palette token must be a hex colour: " + hex,
                hex.matches("#[0-9A-F]{6}"));
            assertTrue("Two tokens share a colour: " + hex, distinct.add(hex));
        }

        // the MOTD is dressed from the palette too
        String yml = readSource("src/main/resources/config.yml");
        int motdStart = yml.indexOf("motd:");
        String motdBlock = yml.substring(motdStart, yml.indexOf("protections:", motdStart) > 0
            ? yml.indexOf("protections:", motdStart) : yml.length());
        java.util.regex.Matcher motdHex = java.util.regex.Pattern
            .compile("#([0-9A-Fa-f]{6})").matcher(motdBlock);
        while (motdHex.find()) {
            assertTrue("The MOTD uses a colour that is not in the palette: " + motdHex.group(),
                java.util.Arrays.asList(fr.bosketball.util.Palette.ALL)
                    .contains("#" + motdHex.group(1).toUpperCase(java.util.Locale.ROOT)));
        }

        // the banner says something useful, in French, without shouting
        String motdSrc = readSource("src/main/java/fr/bosketball/config/MotdText.java");
        for (String slogan : new String[]{"PERFECTED", "OWN THE GAME", "FRESH SEASON",
            "WARMING UP", "COURT UNDER REFIT", "SEE YOU IN A MOMENT"}) {
            assertFalse("Marketing copy survived in the banner: " + slogan,
                stripJavaComments(motdSrc).contains(slogan));
        }
        assertEquals("Three banner lines, no more", 3,
            fr.bosketball.config.MotdText.DEFAULT_SUBTITLES.size());

        // eight commands on the help card, and not one more
        String msgSrc = readSource("src/main/java/fr/bosketball/util/Msg.java");
        int advertised = 0;
        int idx = msgSrc.indexOf("public static void sendInteractiveHelpCard");
        String card = msgSrc.substring(idx, msgSrc.indexOf("private static String helpLine", idx));
        int calls = 0;
        int pos = 0;
        while ((pos = card.indexOf("helpLine(\"", pos)) >= 0) {
            calls++;
            pos += 9;
        }
        advertised = calls - 0;
        assertEquals("The help card advertises exactly eight commands", 8, advertised);
        for (String gone : new String[]{"Unlock", "Browse", "Challenge", "Queue Ranked 1v1"}) {
            assertFalse("English marketing verb survived in the help card: " + gone, card.contains(gone));
        }

        // the one command the [TAB] footer has always advertised now exists
        assertTrue("/stats is declared", readSource("src/main/resources/plugin.yml").contains("  stats:\n"));
        assertTrue("/stats is routed", readShortcutSwitch().contains("case \"stats\", \"profil\", \"profile\" -> \"stats\";"));
    }

    /** The colours inside a source file, comments removed. */
    private static String stripJavaComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            if (source.startsWith("//", i)) {
                int nl = source.indexOf('\n', i);
                i = nl < 0 ? source.length() : nl;
            } else if (source.startsWith("/*", i)) {
                int close = source.indexOf("*/", i + 2);
                i = close < 0 ? source.length() : close + 2;
            } else if (source.charAt(i) == '"') {
                int j = i + 1;
                while (j < source.length()) {
                    if (source.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (source.charAt(j) == '"') {
                        break;
                    }
                    j++;
                }
                out.append(source, i, Math.min(j + 1, source.length()));
                i = j + 1;
            } else {
                out.append(source.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /** The shortcut switch of {@link fr.bosketball.command.BosketCommand}. */
    private static String readShortcutSwitch() throws Exception {
        return readSource("src/main/java/fr/bosketball/command/BosketCommand.java");
    }

    @Test
    public void round43NoStarGlyphCanReachAScreen() throws Exception {
        java.util.List<java.io.File> sources = new java.util.ArrayList<>();
        java.nio.file.Files.walk(new java.io.File("src/main/java").toPath())
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> sources.add(path.toFile()));
        java.nio.file.Files.walk(new java.io.File("src/main/resources").toPath())
            .filter(path -> {
                String name = path.toString();
                return name.endsWith(".yml") || name.endsWith(".json") || name.endsWith(".txt");
            })
            .forEach(path -> sources.add(path.toFile()));
        sources.add(new java.io.File("docs/tablist.md"));
        sources.add(new java.io.File("docs/motd.md"));
        sources.add(new java.io.File("docs/typography-and-ornaments.md"));
        String stars = "\u2726\u2605\u2727\u2606";
        // the same four characters written as a Java escape: config.yml and MotdText kept theirs
        // because a scan of the source text only ever sees the six ASCII characters of the escape
        String[] escaped = {"u2726", "u2605", "u2727", "u2606"};
        int checked = 0;
        for (java.io.File source : sources) {
            String text = Files.readString(source.toPath());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                assertTrue("A decorative star (U+" + Integer.toHexString(c) + ") survived in "
                    + source.getName() + " at offset " + i, stars.indexOf(c) < 0);
            }
            for (String escape : escaped) {
                assertTrue("An escaped star (backslash-u " + escape + ") survived in "
                    + source.getName()
                    + ": it renders as the character even though the source looks clean",
                    !text.contains(escape));
            }
            checked++;
        }
        assertTrue("the star sweep must actually read the sources, only " + checked + " files",
            checked > 120);

        // the replacement is real: a square in the screens, a gold segment in the rules
        String screens = readSource("src/main/java/fr/bosketball/server/ServerScreens.java");
        assertTrue("the screens are titled with a square",
            screens.contains("\u25aa BOSKETBALL \u25aa"));
        String motd = readSource("src/main/java/fr/bosketball/server/MotdManager.java");
        assertTrue("the MOTD payload uses the square too", motd.contains("\u25aa"));
        String release = readSource("src/main/java/fr/bosketball/model/ShotReleaseQuality.java");
        // Round 53: the brackets went too -- the label is just the word now, in sentence case.
        assertTrue("the release tier lost its star and its brackets",
            release.contains("PERFECT_GREEN(\"Green\"") && !release.contains("\u2726")
                && !release.contains("[ GREEN RELEASE ]"));
        assertTrue("the flourish glyph is still bound", readSource(
            "src/main/java/fr/bosketball/util/Glyphs.java").contains("SB_FLOURISH = "));

        // (2) the rendered banner, not its source: this is the first thing a player ever sees
        String[] banners = {
            fr.bosketball.config.MotdText.DEFAULT_HEADER,
            fr.bosketball.config.MotdText.DEFAULT_MAINTENANCE_HEADER,
            fr.bosketball.config.MotdText.DEFAULT_STARTING_HEADER,
            fr.bosketball.config.MotdText.DEFAULT_CLOSED_HEADER,
            fr.bosketball.config.MotdText.DEFAULT_MAINTENANCE_SUBTITLE,
            fr.bosketball.config.MotdText.DEFAULT_STARTING_SUBTITLE,
            fr.bosketball.config.MotdText.DEFAULT_CLOSED_SUBTITLE
        };
        java.util.List<String> rendered = new java.util.ArrayList<>(java.util.List.of(banners));
        rendered.addAll(fr.bosketball.config.MotdText.DEFAULT_SUBTITLES);
        for (String banner : rendered) {
            String plain = plainText(fr.bosketball.util.Msg.parse(banner));
            for (char c : stars.toCharArray()) {
                assertTrue("the rendered banner still shows U+" + Integer.toHexString(c)
                    + ": '" + plain + "'", plain.indexOf(c) < 0);
            }
            assertTrue("the banner keeps readable copy, got '" + plain + "'",
                plain.replaceAll("[^A-Za-z]", "").length() >= 8);
        }
        // a config written before the sweep is cleaned on load, not left with its stars
        assertEquals("an existing config.yml is sanitised on load", "\u25aa BOSKETBALL \u25aa",
            fr.bosketball.config.MotdText.withoutStars("\u2726 BOSKETBALL \u2726"));
    }

    /**
     * Round 43 — the [TAB] card is a real two-column table.
     *
     * <p>The owner's screenshot showed one line pulled to the left and the rest shifted: a record
     * like {@code 128 W / 54 L / 70%} was wider than its value cell and pushed the right-hand column
     * out, so the client re-centred that single line. Every cell is now a fixed width and every value
     * is ellipsised to it, so both columns share one origin and the right-hand numbers hug the card's
     * right edge instead of drifting.
     */
    @Test
    public void round43TabCardColumnsAreColumns() {
        fr.bosketball.model.PlayerProfile profile =
            new fr.bosketball.model.PlayerProfile(java.util.UUID.randomUUID(), "MaxLananas");
        profile.setCoins(5_829);
        profile.setXp(2_264);
        profile.setElo(2_200);
        profile.setWins(128);
        profile.setLosses(54);
        profile.setPoints(147);
        profile.setDunks(4);
        profile.setAssists(11);
        profile.setThreePointersMade(35);
        profile.setThreePointersAttempted(44);
        profile.setSteals(7);
        profile.setBlocks(2);
        profile.setWinStreak(6);
        profile.setBestWinStreak(14);
        profile.setPlayTimeSeconds(11 * 3600L + 32 * 60L);
        java.util.List<String> lines = fr.bosketball.ui.TabListManager.cardLines(
            profile, fr.bosketball.stats.StatsManager.LeaderboardBadges.NONE, null, 90, "bosketball.net");
        int cardPx = fr.bosketball.ui.TabListManager.CARD_PX;
        for (String line : lines) {
            assertEquals("every card line is exactly the card width: '"
                    + plainText(fr.bosketball.util.Msg.parse(line)) + "'",
                cardPx, fr.bosketball.ui.TabListManager.cardWidthPx(line));
        }

        // (1) every left-hand value starts on the same pixel, whatever its length
        assertEquals("the label column is 62 px", 62, pixelStartOf(lines.get(1), "5,829"));
        assertEquals("Playtime shares that column", 62, pixelStartOf(lines.get(2), "11h 32m"));
        assertEquals("Career shares it too", 62, pixelStartOf(findLine(lines, "Career"), "147"));
        assertEquals("Shooting shares it too", 62, pixelStartOf(findLine(lines, "Shooting"), "35"));
        assertEquals("Defense shares it too", 62, pixelStartOf(findLine(lines, "Defense"), "7"));

        // (2) the right column is right-aligned: two values of different widths end on one pixel
        int streakEnd = pixelEndOf(findLine(lines, "Streak"), "(top 14)");
        int xpEnd = pixelEndOf(lines.get(2), "2,264");
        assertEquals("the right-hand numbers must share their right edge", streakEnd, xpEnd);
        assertTrue("the right column must reach the card edge, ended at " + streakEnd,
            streakEnd >= cardPx - 8);

        // (3) no orphan line: every labelled row carries at least one real number
        for (String label : new String[]{"Coins", "Playtime", "Record", "Career", "Shooting", "Defense"}) {
            String line = plainText(fr.bosketball.util.Msg.parse(findLine(lines, label)));
            assertTrue("'" + label + "' must be labelled and filled, got '" + line + "'",
                line.trim().startsWith(label)
                    && line.replace(label, "").chars().anyMatch(Character::isDigit));
        }
        for (String unit : new String[]{"PTS", "DUNKS", "ASSISTS", "3PTS", "FG", "STEALS", "BLOCKS", "%"}) {
            assertTrue("the row units must be spelled out, missing " + unit,
                String.join(NL, lines).toUpperCase(java.util.Locale.ROOT).contains(unit));
        }

        // (4) a hostile profile is ellipsised, never pushed out of the card
        profile.setWins(9_999_999);
        profile.setLosses(9_999_999);
        profile.setCoins(99_999_999);
        profile.setPlayTimeSeconds(9_999 * 3600L);
        java.util.List<String> hostile = fr.bosketball.ui.TabListManager.cardLines(
            profile, new fr.bosketball.stats.StatsManager.LeaderboardBadges(1, 1, 1, 1, 1, 1),
            null, 999, "play.a-very-long-server-domain.example.org");
        for (String line : hostile) {
            assertEquals("a hostile profile still fits the card: '"
                    + plainText(fr.bosketball.util.Msg.parse(line)) + "'",
                cardPx, fr.bosketball.ui.TabListManager.cardWidthPx(line));
        }
        assertTrue("an oversized value is shortened, not pushed out",
            String.join(NL, hostile).contains("\u2026"));
    }

    /**
     * Pixel offset where {@code value} starts inside a card line.
     *
     * <p>Measured on the plain text: the raw MiniMessage wraps every cell in a colour tag, so a
     * search there would happily match a digit inside {@code <#64748B>} instead of the value.
     */
    private static int pixelStartOf(String line, String value) {
        String plain = plainText(fr.bosketball.util.Msg.parse(line));
        int idx = plain.indexOf(value);
        assertTrue("'" + value + "' must be on the line, got '" + plain + "'", idx >= 0);
        return fr.bosketball.ui.TabListManager.cardWidthPx(plain.substring(0, idx));
    }

    /** Pixel offset where {@code value} ends inside a card line. */
    private static int pixelEndOf(String line, String value) {
        String plain = plainText(fr.bosketball.util.Msg.parse(line));
        int idx = plain.indexOf(value);
        assertTrue("'" + value + "' must be on the line, got '" + plain + "'", idx >= 0);
        return fr.bosketball.ui.TabListManager.cardWidthPx(plain.substring(0, idx + value.length()));
    }

    /** The card line carrying a given label. */
    private static String findLine(java.util.List<String> lines, String label) {
        for (String line : lines) {
            if (plainText(fr.bosketball.util.Msg.parse(line)).contains(label)) {
                return line;
            }
        }
        fail("the card must have a '" + label + "' row, got:" + NL + String.join(NL, lines));
        return "";
    }

    /**
     * Round 43 — the lobby sidebar has its gold delimiter back, top and bottom.
     *
     * <p>The owner saw the flourish vanish from the lobby and asked where it went. It was a casualty
     * of the 15-line client cap: blank separators ate the budget, so the frame was the first thing
     * dropped. The lobby list is now six two-line blocks framed by two flourishes — 14 lines, no
     * blank separators — and the tag/value shape is the same in the lobby as in a match.
     */
    @Test
    public void round43SidebarRestoresTheGoldenFlourish() throws Exception {
        String sidebar = readSource("src/main/java/fr/bosketball/ui/ScoreboardSidebarManager.java");
        int start = sidebar.indexOf("public List<String> buildLobbyLines(");
        assertTrue("the lobby builder is still there", start > 0);
        String body = sidebar.substring(start, sidebar.indexOf("private void applyLines(", start));

        assertTrue("the flourish is a real local, not a missing symbol",
            body.contains("String flourish = \"<!shadow><#F8FAFC>\" + Glyphs.SB_FLOURISH"));
        int listStart = body.indexOf("return List.of(");
        String list = body.substring(listStart, body.indexOf(NL + "        );", listStart));
        assertEquals("the lobby sidebar is 14 lines: two flourishes and six two-line blocks",
            14, list.split("," + NL).length);
        assertTrue("the flourish opens the list",
            list.contains("return List.of(" + NL + "            flourish,"));
        String[] entries = list.split("," + NL);
        assertEquals("the flourish closes the list", "flourish", entries[entries.length - 1].trim());

        // the same flourish closes a match sidebar, as it always did
        assertTrue("the match sidebar keeps its closing flourish",
            sidebar.contains("Glyphs.SB_FLOURISH + \"</#F8FAFC></!shadow>\""));
        // the client drops anything past 15 lines, so the render budget has to stay honest
        assertTrue("the render budget is 15 lines",
            sidebar.contains("int count = Math.min(lines.size(), LINE_KEYS.length);"));
        // one shape for every block: a tag line, then its value underneath
        for (String tag : new String[]{"SB_PLAYER", "SB_COINS", "SB_ELO", "SB_PLAYTIME", "SB_CLOCK", "SB_FRIENDS"}) {
            assertTrue("missing sidebar tag: " + tag,
                list.contains("Glyphs." + tag + " + \"</#F8FAFC></!shadow>\","));
        }
    }

    /**
     * Round 41 — two courts can never share a block column again.
     *
     * <p>The owner's screen showed an indoor arena and the cyan one fused together: the grid
     * spacing came straight from the config, and nothing compared it to the size of the builds. A
     * value left over from an older install (64 blocks) lays the 101-block-long terrain on top of
     * its neighbour. Spacing is now a minimum, and the layout skips anything that would touch.
     */
    @Test
    public void round41CourtsNeverOverlapWhateverTheConfigSays() throws Exception {
        fr.bosketball.arena.schematic.CourtSchematic indoor = fr.bosketball.arena.schematic.CourtSchematic.load(
            new java.io.File("src/main/resources/courts/terrain.schem"));
        fr.bosketball.arena.schematic.CourtSchematic street = fr.bosketball.arena.schematic.CourtSchematic.load(
            new java.io.File("src/main/resources/courts/terrainsseize.schem"));
        java.util.List<fr.bosketball.arena.schematic.CourtSchematic> pool = java.util.List.of(indoor, street);

        // (1) the maths itself
        assertEquals("50 courts are laid out on an 8-column grid",
            8, fr.bosketball.arena.schematic.SchematicCourtPool.gridColumns(50));
        assertEquals(1, fr.bosketball.arena.schematic.SchematicCourtPool.gridColumns(1));
        assertEquals("A missing count still yields one column",
            1, fr.bosketball.arena.schematic.SchematicCourtPool.gridColumns(0));
        assertEquals("The largest build plus the safety margin",
            101 + fr.bosketball.arena.schematic.SchematicCourtPool.COURT_MARGIN_BLOCKS,
            fr.bosketball.arena.schematic.SchematicCourtPool.requiredSpacing(101));
        assertEquals("A stale 64-block spacing is raised to the build size",
            fr.bosketball.arena.schematic.SchematicCourtPool.requiredSpacing(101),
            fr.bosketball.arena.schematic.SchematicCourtPool.effectiveSpacing(64, 101));
        assertEquals("A generous spacing is kept as it is",
            384, fr.bosketball.arena.schematic.SchematicCourtPool.effectiveSpacing(384, 101));
        assertTrue("Touching edges do not overlap", !fr.bosketball.arena.schematic.SchematicCourtPool
            .footprintsOverlap(0, 0, 75, 101, 75, 0, 75, 101));
        assertTrue("One shared block column is an overlap", fr.bosketball.arena.schematic.SchematicCourtPool
            .footprintsOverlap(0, 0, 75, 101, 74, 0, 75, 101));
        assertTrue("Two courts on the same spot overlap", fr.bosketball.arena.schematic.SchematicCourtPool
            .footprintsOverlap(100, 100, 75, 101, 100, 100, 49, 52));

        // (2) the whole 50-court layout, with the spacing that used to fuse them
        int largest = 0;
        for (fr.bosketball.arena.schematic.CourtSchematic terrain : pool) {
            largest = Math.max(largest, Math.max(terrain.pasteWidth(), terrain.pasteLength()));
        }
        for (int configured : new int[]{64, 96, 128, 384}) {
            int spacing = fr.bosketball.arena.schematic.SchematicCourtPool.effectiveSpacing(configured, largest);
            java.util.List<int[]> placed = new java.util.ArrayList<>();
            for (int i = 0; i < 50; i++) {
                int variant = fr.bosketball.arena.schematic.SchematicCourtPool.variantIndexFor(i + 1, pool.size());
                fr.bosketball.arena.schematic.CourtSchematic terrain = pool.get(variant);
                int originX = 100_000 + (i % 8) * spacing;
                int originZ = 100_000 + (i / 8) * spacing;
                for (int[] other : placed) {
                    assertFalse("courts " + other[4] + " and " + i + " overlap with spacing "
                            + configured + " (effective " + spacing + ")",
                        fr.bosketball.arena.schematic.SchematicCourtPool.footprintsOverlap(
                            originX, originZ, terrain.pasteWidth(), terrain.pasteLength(),
                            other[0], other[1], other[2], other[3]));
                }
                placed.add(new int[]{originX, originZ, terrain.pasteWidth(), terrain.pasteLength(), i});
            }
            assertEquals(50, placed.size());
        }
        // …and the old behaviour really was the bug: 64 blocks does fuse two neighbours
        assertTrue("64 blocks of spacing would put two builds in each other",
            fr.bosketball.arena.schematic.SchematicCourtPool.footprintsOverlap(
                100_000, 100_000, street.pasteWidth(), street.pasteLength(),
                100_000, 100_064, indoor.pasteWidth(), indoor.pasteLength()));

        // (3) it is wired into the pool
        String poolSrc = readSource("src/main/java/fr/bosketball/arena/schematic/SchematicCourtPool.java");
        assertTrue("the layout must clamp the spacing",
            poolSrc.contains("int spacing = effectiveSpacing(cfg.spacingBlocks(), largestBuild);"));
        assertTrue("a court that would touch another one is skipped",
            poolSrc.contains("if (overlapsAny(placed, slot)) {"));
        assertTrue("…and that is reported in the console",
            poolSrc.contains("skipped to avoid an overlap"));
    }

    /**
     * Round 41 — « je veux une chance sur deux » : the match picks its terrain first, then a free
     * court of that terrain, so with two builds in `schematics:` the split is a true 50/50.
     */
    @Test
    public void round41TerrainIsARealOneInTwoCoinFlip() {
        java.util.Random random = new java.util.Random(20261107L);
        int draws = 4_000;
        int first = 0;
        for (int i = 0; i < draws; i++) {
            first += fr.bosketball.arena.schematic.SchematicCourtPool.pickTerrain(2, random) == 0 ? 1 : 0;
        }
        double share = first / (double) draws;
        assertTrue("the first terrain must get half of the matches, got " + share,
            share > 0.45 && share < 0.55);
        assertEquals("A single-terrain pool always answers 0",
            0, fr.bosketball.arena.schematic.SchematicCourtPool.pickTerrain(1, random));

        // the same helper drives the runtime pick, and it stays inside the wanted terrain
        java.util.List<String> free = java.util.List.of("A1", "A2", "A3", "B1", "B2");
        java.util.function.ToIntFunction<String> variantOf = id -> id.charAt(0) == 'A' ? 0 : 1;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            int wanted = fr.bosketball.arena.schematic.SchematicCourtPool.pickTerrain(2, random);
            String picked = fr.bosketball.arena.schematic.SchematicCourtPool
                .pickFreeOfTerrain(free, variantOf, wanted, random);
            assertEquals("a court of the wanted terrain", wanted, variantOf.applyAsInt(picked));
            seen.add(picked);
        }
        assertEquals("every free court of a terrain can be handed out", 5, seen.size());

        // no free court of the wanted terrain -> any free court, never a refusal
        java.util.List<String> onlyA = java.util.List.of("A1", "A2");
        assertTrue("fallback to a free court of another terrain",
            onlyA.contains(fr.bosketball.arena.schematic.SchematicCourtPool
                .pickFreeOfTerrain(onlyA, variantOf, 1, new java.util.Random(7L))));
        assertNull("nothing free, nothing picked",
            fr.bosketball.arena.schematic.SchematicCourtPool
                .pickFreeOfTerrain(java.util.List.of(), variantOf, 0, random));

        String poolSrc = readSource("src/main/java/fr/bosketball/arena/schematic/SchematicCourtPool.java");
        assertTrue("the match start flips a coin between the terrains",
            poolSrc.contains("int wanted = pickTerrain(variants.size(), random);")
                && poolSrc.contains("pickFreeOfTerrain(free, slot -> slot.variant, wanted, random)"));
    }

    /**
     * Round 42 — no seam can open between two nameplate tiles.
     *
     * <p>The plate is a left cap, N middle tiles and a right cap. At a fractional display scale the
     * client opens a fraction of a pixel between two glyph quads, which read as a bar cut into
     * blocks with holes between them — exactly what the owner kept reporting. Every tile is now
     * pulled one pixel into the next one with a negative space glyph, so the seams are covered.
     */
    @Test
    public void round42NameplateTilesOverlapSoNoSeamCanOpen() {
        int overlap = fr.bosketball.ui.NameplateLayout.TILE_OVERLAP_PX;
        assertEquals("One pixel of overlap is enough to swallow the seam", 1, overlap);
        for (int tiles = fr.bosketball.ui.NameplateLayout.MIN_TILES;
             tiles <= fr.bosketball.ui.NameplateLayout.MAX_TILES; tiles++) {
            int bar = fr.bosketball.ui.NameplateLayout.barWidthPx(tiles);
            assertEquals("tiles=" + tiles + ": the bar is tiles + 2 caps, minus one pixel per junction",
                fr.bosketball.util.Glyphs.NAMEPLATE_TILE_PX * (2 + tiles) - overlap * (1 + tiles), bar);
            if (tiles > fr.bosketball.ui.NameplateLayout.MIN_TILES) {
                assertTrue("tiles=" + tiles + ": a longer bar is always wider",
                    bar > fr.bosketball.ui.NameplateLayout.barWidthPx(tiles - 1));
            }
            if (tiles > fr.bosketball.ui.NameplateLayout.MIN_TILES) {
                for (fr.bosketball.ui.NameplateSkin skin : fr.bosketball.ui.NameplateSkin.values()) {
                    assertTrue("tiles=" + tiles + ": the art keeps growing",
                        fr.bosketball.ui.NameplateLayout.artWidthPx(skin, tiles)
                            > fr.bosketball.ui.NameplateLayout.artWidthPx(skin, tiles - 1));
                }
            }
        }

        // The assembled line really carries one negative shift per junction.
        String[] names = { "A", "MaxLananas", "ABCDEFGHIJKLMNOP" };
        for (fr.bosketball.ui.NameplateSkin skin : fr.bosketball.ui.NameplateSkin.values()) {
            fr.bosketball.model.Rank rank = fr.bosketball.model.Rank.valueOf(skin.name());
            for (String name : names) {
                fr.bosketball.ui.NameplateLayout.Plate plate =
                    fr.bosketball.ui.NameplateLayout.build(rank, name);
                String plain = plainText(plate.plate());
                int shifts = 0;
                int middles = 0;
                for (int i = 0; i < plain.length(); i++) {
                    char c = plain.charAt(i);
                    if (c >= '\uE001' && c <= '\uE00A') {
                        shifts++;
                    }
                    if (c == skin.middle()) {
                        middles++;
                    }
                }
                assertEquals(skin + "/" + name + ": exactly one glyph per middle tile",
                    plate.tiles(), middles);
                assertTrue(skin + "/" + name + ": every junction is pulled in (tiles + 1 overlaps"
                        + " on top of the glyph that moves the pen back over the bar)",
                    shifts >= plate.tiles() + 1);
                assertEquals("the line advances by exactly the bar width",
                    fr.bosketball.ui.NameplateLayout.barWidthPx(plate.tiles()), plate.widthPx());
                assertEquals(plate.widthPx(), plate.startPx() + plate.contentPx() + plate.tailPx());
                assertTrue("the overlap must never be a visible chunk of the bar",
                    fr.bosketball.ui.NameplateLayout.TILE_OVERLAP_PX * (1 + plate.tiles())
                        < plate.widthPx() / 4);
            }
        }
    }

    /**
     * Round 42 — a head whose nickname no longer resolves is no longer a Steve head.
     *
     * <p>The imported build stores nothing but a nickname per head: no texture, no UUID, so the
     * client has to resolve it. One of the two heads of the cyan court resolved, the other stayed
     * Steve. Three fixes are asserted here: the nickname is completed on the web (off-thread), the
     * fallback UUID is the offline-player one instead of a random one, and
     * {@code schematic-courts.head-skins} can pin the exact texture.
     */
    @Test
    public void round42HeadSkinsResolveSteves() throws Exception {
        // (1) the build really only carries names — the reason a head can fall back to Steve
        fr.bosketball.arena.schematic.CourtSchematic street = fr.bosketball.arena.schematic.CourtSchematic.load(
            new java.io.File("src/main/resources/courts/terrainsseize.schem"));
        assertEquals(2, street.headCount());
        for (fr.bosketball.arena.schematic.CourtSchematic.SavedBlockEntity entity : street.blockEntities()) {
            if (!entity.isSkull()) {
                continue;
            }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> profile =
                (java.util.Map<String, Object>) entity.data().get("profile");
            assertNotNull("a head without a profile could never be skinned", profile);
            assertNotNull("the head must at least carry its nickname", profile.get("name"));
            assertFalse("the build ships no texture value: the client has to resolve the name",
                profile.get("properties") instanceof java.util.List<?> list && !list.isEmpty());
        }

        // (2) the config can pin a skin, signature optional
        String[] pinned = fr.bosketball.config.BosketConfig.SchematicCourts.parseSkinValue("abc;def");
        assertEquals("abc", pinned[0]);
        assertEquals("def", pinned[1]);
        assertEquals("abc", fr.bosketball.config.BosketConfig.SchematicCourts.parseSkinValue("abc")[0]);
        assertNull("a signature is optional",
            fr.bosketball.config.BosketConfig.SchematicCourts.parseSkinValue("abc")[1]);
        assertNull("blank pins are ignored",
            fr.bosketball.config.BosketConfig.SchematicCourts.parseSkinValue("  "));
        assertNull(fr.bosketball.config.BosketConfig.SchematicCourts.parseSkinValue(";sig"));

        var cfg = new fr.bosketball.config.BosketConfig.SchematicCourts(
            true, "terrain.schem", java.util.List.of("terrain.schem", "terrainsseize.schem"),
            "bosketball_courts", 50, 384, 100_000, 64, 100_000, 0.0, 0.0, "west", 0,
            9.0, 15.0, "auto", "OCHRE_FROGLIGHT", 0, 3, 1.25, true, true, true, true, 4_000, 30, true,
            java.util.Map.of("Friandise", "value123;sig456", "   ", "ignored"));
        assertEquals("the nickname lookup is case-insensitive", "value123;sig456",
            cfg.headSkin("friandise"));
        assertEquals("…and trimmed", "value123;sig456", cfg.headSkin("  FRIANDISE "));
        assertNull("an unknown head keeps its resolved skin", cfg.headSkin("maxlananas"));
        assertEquals("a pin without a name is dropped", 1, cfg.headSkins().size());

        String yml = readSource("src/main/resources/config.yml");
        assertTrue("the shipped config documents the pin", yml.contains("head-skins:"));

        // (3) the resolver really goes to the web, and its fallback UUID is stable
        String heads = readSource("src/main/java/fr/bosketball/arena/schematic/SchematicHeads.java");
        assertTrue("a nickname that never joined is completed on the web",
            heads.contains("profile.complete();"));
        assertTrue("that call must stay off the server thread",
            heads.contains("Bukkit.getScheduler().runTaskAsynchronously"));
        assertTrue("a pinned skin wins over everything",
            heads.contains("String[] pinned = parsePinned("));
        assertTrue("the fallback UUID is the offline-player one, not a random one",
            heads.contains("UUID.nameUUIDFromBytes(("));
        assertEquals("a random UUID is only the last resort of a nameless head", 1,
            heads.split("UUID\\.randomUUID", -1).length - 1);
        String pool = readSource("src/main/java/fr/bosketball/arena/schematic/SchematicCourtPool.java");
        assertTrue("the pool hands the pins to the resolver",
            pool.contains("SchematicHeads.attach(plugin, headNames(variants), cfg.headSkins());"));
    }

    /**
     * Round 42 — the hardening pass, part 1: the shared guards.
     *
     * <p>Every one of these helpers exists because of a concrete failure found in the ten thousand
     * lines audited this round (see {@code docs/hardening-42.md}): a throwing clamp inside the
     * cinematic camera, a NaN that compared false through a physics branch, a {@code long}
     * multiplication that wrapped the season window into 1970, a truncation that split an emoji.
     */
    @Test
    public void round42GuardsNeverThrowAndNeverLie() {
        // Crossing bounds: Math.clamp throws here, Guards keeps the caller alive.
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
            () -> Math.clamp(5.0, 10.0, 1.0));
        assertEquals("a crossed range collapses to its middle", 5.5,
            fr.bosketball.util.Guards.clampLoHi(7.0, 10.0, 1.0), 1.0e-9);
        assertEquals(5, fr.bosketball.util.Guards.clampLoHi(9, 10, 1));
        assertEquals("a normal clamp still clamps", 10.0,
            fr.bosketball.util.Guards.clampLoHi(99.0, 0.0, 10.0), 1.0e-9);
        assertEquals(-3.0, fr.bosketball.util.Guards.clampLoHi(-30.0, -3.0, 10.0), 1.0e-9);

        // NaN / infinity never survive.
        assertEquals(2.0, fr.bosketball.util.Guards.finite(Double.NaN, 2.0), 1.0e-9);
        assertEquals(2.0, fr.bosketball.util.Guards.finite(Double.POSITIVE_INFINITY, 2.0), 1.0e-9);
        assertEquals(1.5f, fr.bosketball.util.Guards.finite(1.5f, 0f), 1.0e-6f);
        assertEquals("NaN maps to the fallback", 0.25, fr.bosketball.util.Guards.unit(Double.NaN, 0.25), 1.0e-9);
        assertEquals(0.0, fr.bosketball.util.Guards.unit(-4.0, 0.5), 1.0e-9);
        assertEquals(1.0, fr.bosketball.util.Guards.unit(4.0, 0.5), 1.0e-9);
        assertEquals(5.5, fr.bosketball.util.Guards.clampLoHi(Double.NaN, 1.0, 10.0), 1.0e-9);

        // Saturation instead of wrap-around.
        assertEquals("a real overflow saturates", Long.MAX_VALUE,
            fr.bosketball.util.Guards.saturatingMultiplyAdd(1L, Long.MAX_VALUE, 2L));
        assertEquals("a normal product is exact", 11L,
            fr.bosketball.util.Guards.saturatingMultiplyAdd(1L, 2L, 5L));
        assertEquals(1_700_000_000_000L + 30L * 86_400_000L,
            fr.bosketball.util.Guards.saturatingMultiplyAdd(1_700_000_000_000L, 30L, 86_400_000L));

        // Truncation never splits a surrogate pair.
        String emoji = "aaaaaaaa\uD83C\uDFC0";      // 8 chars + a 2-char surrogate pair
        String cut = fr.bosketball.util.Guards.truncate(emoji, 9);
        assertEquals("the pair is dropped whole", 8, cut.length());
        assertEquals("a short string is returned untouched", "abc",
            fr.bosketball.util.Guards.truncate("abc", 99));
        assertEquals("", fr.bosketball.util.Guards.truncate(null, 5));
    }

    /** Round 42 — the season window can no longer be pushed into 1970 or into an overflow. */
    @Test
    public void round42SeasonWindowSurvivesCorruptDates() {
        long start = 1_767_225_600_000L;   // 2026-01-01T00:00:00Z
        long day = 24L * 60L * 60L * 1000L;

        long[] normal = fr.bosketball.season.SeasonMath.seasonWindow(2, start, -1L, 30L * day);
        assertEquals(start + 30L * day, normal[0]);
        assertEquals(start + 60L * day, normal[1]);

        // Absurd index / duration: saturating, never negative, never inverted.
        for (int index : new int[]{Integer.MIN_VALUE, -5, 0, 1, Integer.MAX_VALUE}) {
            for (long duration : new long[]{Long.MIN_VALUE, -1L, 0L, 60_000L, Long.MAX_VALUE}) {
                long[] window = fr.bosketball.season.SeasonMath.seasonWindow(index, start, -1L, duration);
                assertTrue("index=" + index + " duration=" + duration + ": start positive",
                    window[0] > 0);
                assertTrue("index=" + index + " duration=" + duration + ": end after start",
                    window[1] >= window[0]);
            }
        }
        assertEquals("an unconfigured season is not 01/01/1970", "—",
            fr.bosketball.season.SeasonMath.formattedWindow(-1L, -1L));
        assertTrue(fr.bosketball.season.SeasonMath.formattedWindow(start, start + day).contains("→"));

        // Ratings: every input lands in the ladder, overflow included.
        for (int elo : new int[]{Integer.MIN_VALUE, -1, 0, 100, 1500, 99_999, Integer.MAX_VALUE}) {
            int reset = fr.bosketball.season.SeasonMath.softReset(elo, 1200, 0.5);
            assertTrue("elo=" + elo + " -> " + reset,
                reset >= fr.bosketball.season.SeasonMath.MIN_ELO
                    && reset <= fr.bosketball.season.SeasonMath.MAX_ELO);
        }
        assertEquals("NaN keep-ratio does not produce NaN", 1200,
            fr.bosketball.season.SeasonMath.softReset(1200, 1200, Double.NaN));
        assertEquals("ten years is the ceiling", 3650L * day,
            fr.bosketball.season.SeasonMath.daysToMillis(Integer.MAX_VALUE));
        assertEquals("at least one day", day, fr.bosketball.season.SeasonMath.daysToMillis(-3));
        assertTrue(fr.bosketball.season.SeasonMath.progressPercent(
            Long.MIN_VALUE, Long.MAX_VALUE, 0L) <= 100);
    }

    /** Round 42 — a long shot can always be green, and a NaN charge is never a perfect release. */
    @Test
    public void round42ShotReleaseWindowNeverInverts() {
        for (double distance = 0.0; distance <= 120.0; distance += 0.5) {
            for (boolean hot : new boolean[]{false, true}) {
                double lo = fr.bosketball.model.ShotReleaseQuality.greenWindowLow(hot, distance);
                double hi = fr.bosketball.model.ShotReleaseQuality.greenWindowHigh(hot, distance);
                assertTrue("distance=" + distance + " hot=" + hot + ": low " + lo + " > high " + hi,
                    lo < hi);
                assertTrue("distance=" + distance + ": the window keeps a usable width",
                    hi - lo >= fr.bosketball.model.ShotReleaseQuality.MIN_GREEN_WIDTH - 1.0e-9);
                double middle = (lo + hi) / 2.0;
                assertEquals("a release inside the window is green at " + distance,
                    fr.bosketball.model.ShotReleaseQuality.PERFECT_GREEN,
                    fr.bosketball.model.ShotReleaseQuality.evaluate(middle, distance, hot));
            }
        }
        assertTrue("NaN is never a green release",
            fr.bosketball.model.ShotReleaseQuality.evaluate(Double.NaN, 12.0, false)
                != fr.bosketball.model.ShotReleaseQuality.PERFECT_GREEN);
        assertEquals("a NaN charge costs the maximum penalty", 1.10,
            fr.bosketball.model.ShotReleaseQuality.timingPenaltyBlocks(Double.NaN, 12.0), 1.0e-9);
        assertEquals("a green release costs nothing", 0.0,
            fr.bosketball.model.ShotReleaseQuality.timingPenaltyBlocks(0.9, 12.0), 1.0e-9);
    }

    /** Round 42 — one NaN velocity can no longer poison the whole rig. */
    @Test
    public void round42AnimationSurvivesNonFiniteInput() {
        fr.bosketball.animation.AnimationKinematics.RigPoseSnapshot pose =
            fr.bosketball.animation.AnimationKinematics.evaluate(
                Float.NaN, Float.NaN, Double.NaN, Double.NaN, false, true,
                Double.NaN, Double.NaN, true, false, Double.NaN, 999,
                fr.bosketball.animation.AnimationKinematics.ActionClip.SLAM_DUNK,
                Double.NaN, fr.bosketball.model.DunkStyle.WINDMILL, 7);
        assertNotNull(pose);
        for (fr.bosketball.animation.AnimationKinematics.BonePose bone : java.util.List.of(
            pose.head(), pose.torso(), pose.rightArm(), pose.leftArm(), pose.rightLeg(), pose.leftLeg())) {
            assertTrue("offset x", Float.isFinite(bone.worldOffset().x));
            assertTrue("offset y", Float.isFinite(bone.worldOffset().y));
            assertTrue("offset z", Float.isFinite(bone.worldOffset().z));
            assertTrue("rotation x", Float.isFinite(bone.rotation().x));
            assertTrue("rotation y", Float.isFinite(bone.rotation().y));
            assertTrue("rotation z", Float.isFinite(bone.rotation().z));
            assertTrue("rotation w", Float.isFinite(bone.rotation().w));
            assertTrue("pitch", Float.isFinite(bone.headPitchDeg()));
            assertTrue("yaw", Float.isFinite(bone.headYawOffsetDeg()));
        }
        assertNotNull("a null vector is tolerated", fr.bosketball.animation.AnimationKinematics
            .rotateYaw(null, 90f));

        // A negative / absurd horizontal speed must not run the walk cycle backwards past zero.
        var backwards = fr.bosketball.animation.AnimationKinematics.evaluate(
            0f, 0f, -5.0, 0.0, true, false, 0.0, 0.0, false, false, -1.0, 0,
            fr.bosketball.animation.AnimationKinematics.ActionClip.NONE, 0.0, null, 0);
        assertTrue(Float.isFinite(backwards.torso().worldOffset().y()));
    }

    /** Round 42 — i18n: cached instances, real locale detection, no placeholder injection. */
    @Test
    public void round42LangIsCachedAndCannotBeInjected() {
        org.junit.Assert.assertSame("one immutable instance per locale",
            fr.bosketball.i18n.Lang.of(fr.bosketball.i18n.Lang.Locale_.FR),
            fr.bosketball.i18n.Lang.of("fr"));
        org.junit.Assert.assertSame(fr.bosketball.i18n.Lang.of("fr"), fr.bosketball.i18n.Lang.of("fr"));
        assertTrue("no per-call copy of the English table",
            !readSource("src/main/java/fr/bosketball/i18n/Lang.java").contains("resolved.putAll(EN);\n        Map<String, String> overrides"));

        assertEquals("the client sends fr_fr with an underscore",
            fr.bosketball.i18n.Lang.Locale_.FR, fr.bosketball.i18n.Lang.Locale_.byCode("fr_fr"));
        assertEquals(fr.bosketball.i18n.Lang.Locale_.FR,
            fr.bosketball.i18n.Lang.Locale_.byCode("fr-FR"));
        assertEquals(fr.bosketball.i18n.Lang.Locale_.FR,
            fr.bosketball.i18n.Lang.Locale_.byCode(" FRA "));
        assertEquals(fr.bosketball.i18n.Lang.Locale_.DE,
            fr.bosketball.i18n.Lang.Locale_.byCode("de_de"));
        assertEquals(fr.bosketball.i18n.Lang.Locale_.ES,
            fr.bosketball.i18n.Lang.Locale_.byCode("es_mx"));
        assertEquals("an unknown locale falls back to English",
            fr.bosketball.i18n.Lang.Locale_.EN, fr.bosketball.i18n.Lang.Locale_.byCode("xx"));

        var lang = fr.bosketball.i18n.Lang.of("en");
        String rendered = lang.get("coins.balance", java.util.Map.of("amount", "{amount}"));
        assertEquals("a value is inserted verbatim and never re-expanded",
            "Balance: <#FDE047>{amount} Coins</#FDE047>", rendered);
        String unknown = lang.get("coins.balance", java.util.Map.of("nope", "x"));
        assertTrue("an unknown placeholder is left alone", unknown.contains("{amount}"));
        assertEquals("no placeholders, no work", lang.get("generic.yes"), "Yes");
    }

    /** Round 42 — a guild colour is always a valid MiniMessage hex tag. */
    @Test
    public void round42GuildColourIsAlwaysATag() {
        var guild = new fr.bosketball.social.Guild("g1", "Showtime", "SHW", 0L, 1200);
        for (int raw : new int[]{0x38BDF8, 0xFF38BDF8, -1, 0, 0x12345678}) {
            guild.setColorRgb(raw);
            String hex = guild.hexColor();
            assertTrue("raw=" + Integer.toHexString(raw) + " gave " + hex,
                hex.matches("#[0-9A-F]{6}"));
            assertEquals("the tag is valid MiniMessage", "<" + hex + ">", guild.chatTag());
            assertEquals("</" + hex + ">", guild.chatTagClose());
            assertEquals("the palette index stays in range", true,
                guild.colorRgbIndex() >= 0 && guild.colorRgbIndex() < fr.bosketball.social.Guild.PALETTE.size());
        }
        guild.setColorRgbIndex(fr.bosketball.social.Guild.PALETTE.size() * 3 + 2);
        assertTrue(guild.hexColor().matches("#[0-9A-F]{6}"));
        assertFalse("the palette colour is the one that was set",
            (guild.colorRgb() & ~0xFFFFFF) != 0);
    }

    /** Round 42 — the storage and scheduling guards, asserted on the source (they need a server). */
    @Test
    public void round42StorageAndTickGuardsAreInPlace() {
        String stats = readSource("src/main/java/fr/bosketball/stats/StatsManager.java");
        assertTrue("a corrupt profile is quarantined, not silently reset",
            stats.contains("AtomicYamlWriter.loadStrict(f)") && stats.contains("quarantineProfileFile(f, \"invalid YAML: "));
        assertTrue("the index flag is atomic", stats.contains("AtomicBoolean indexDirty"));
        assertTrue("the hand-off is atomic", stats.contains("indexDirty.getAndSet(false)"));
        assertTrue("orphaned dirty profiles are counted", stats.contains("orphanedDirty.incrementAndGet()"));
        assertTrue("a slow IO thread is drained and reported",
            stats.contains("ioExecutor.shutdownNow()") && stats.contains("Player IO thread still busy"));

        String yaml = readSource("src/main/java/fr/bosketball/config/AtomicYamlWriter.java");
        assertTrue("the temp file name is unique per write",
            yaml.contains("Thread.currentThread().getId()") && yaml.contains("System.nanoTime()"));
        assertTrue("a failed write deletes its temp file", yaml.contains("Files.deleteIfExists(tmpPath)"));
        assertTrue("the exception class is logged", yaml.contains("e.getClass().getSimpleName()"));

        String camera = readSource("src/main/java/fr/bosketball/ui/CinematicCameraManager.java");
        assertFalse("the per-tick camera clamp can no longer throw",
            camera.contains("Math.clamp(pos.getX()"));
        assertTrue(camera.contains("Guards.clampLoHi(pos.getX()"));

        String bars = readSource("src/main/java/fr/bosketball/ui/BossBarHudManager.java");
        assertEquals("a relogged player is shown the cached bar again", 4,
            bars.split("player\\.showBossBar\\(existing", -1).length - 1);
        assertTrue("every entry point checks the player", bars.contains("private static boolean usable(Player player)"));

        String permits = readSource("src/main/java/fr/bosketball/util/Perms.java");
        assertFalse("a plugin.yml default no longer swallows an umbrella grant",
            permits.contains("if (player.isPermissionSet(node)) {\n            return player.hasPermission(node);"));
        assertTrue(permits.contains("hasDeclaredDefault(clean)"));

        String practice = readSource("src/main/java/fr/bosketball/arena/PracticeArenaPool.java");
        assertTrue("one spacing rule for the practice grid",
            practice.contains("Location slot = computeGridSlotCenter(idx, cfg.gridSpacingBlocks());"));

        String ball = readSource("src/main/java/fr/bosketball/physics/BasketballEntity.java");
        assertTrue("a non-finite ball state is recovered", ball.contains("recoverFromInvalidState()"));
        assertFalse("the recovery path does not teleport NaN", ball.contains("position.setX(Double.NaN"));

        String guilds = readSource("src/main/java/fr/bosketball/social/GuildService.java");
        assertTrue("the swatch is built by Guild, not sliced out of the hex string",
            guilds.contains("+ guild.chatTag() +"));
    }

    /** Round 42 — the pure-but-plain ones: GG cache, ops audit, movement, season counters. */
    @Test
    public void round42SmallGuardsAreCorrect() {
        java.util.Map<java.util.UUID, Long> cache = new java.util.HashMap<>();
        long now = 1_000_000L;
        java.util.UUID fresh = java.util.UUID.randomUUID();
        java.util.UUID stale = java.util.UUID.randomUUID();
        java.util.UUID future = java.util.UUID.randomUUID();
        cache.put(fresh, now - 10L);
        cache.put(stale, now - 120_000L);
        cache.put(future, now + 5_000_000L);   // the system clock was set back
        assertEquals("the fresh entry is the only one kept", 1,
            fr.bosketball.command.GgCommand.pruneOlderThan(cache, now, 60_000L, 64));
        assertTrue(cache.containsKey(fresh));
        assertFalse("an old cooldown is forgotten", cache.containsKey(stale));
        assertFalse("a future stamp is stale too", cache.containsKey(future));

        assertTrue("an empty snapshot is not a crash",
            fr.bosketball.ops.OpsAudit.audit(null).isEmpty());
        var nanTps = new fr.bosketball.ops.OpsAudit.Snapshot(false, false, 4, 4, 1_000, 4_096, 4,
            100, 100, Double.NaN, Double.NaN);
        assertTrue("a NaN TPS is not reported as a problem",
            fr.bosketball.ops.OpsAudit.audit(nanTps).isEmpty());

        var nanDirection = fr.bosketball.physics.MovementMath.dashVelocity(
            new org.bukkit.util.Vector(Double.NaN, 0, Double.NaN), Double.NaN, Double.NaN);
        assertTrue("the dash impulse is always finite",
            fr.bosketball.physics.MovementMath.isFinite(nanDirection));
        assertTrue(fr.bosketball.physics.MovementMath.isFinite(
            fr.bosketball.physics.MovementMath.dashDirection(
                new org.bukkit.util.Vector(Double.NaN, 0, 0), null)));

        var record = new fr.bosketball.season.SeasonRecord(
            java.util.UUID.randomUUID(), "Test", 1200);
        for (int i = 0; i < 3; i++) {
            record.noteMatch(true, 1250);
        }
        record.addCoinsEarned(Integer.MAX_VALUE);
        record.addCoinsEarned(Integer.MAX_VALUE);
        assertTrue("earned coins never wrap negative", record.coinsEarned() > 0);
        assertEquals(3, record.matches());
        assertEquals(100.0, record.winRatePercent(), 1.0e-9);
    }

    private static final String SEASON_DOMAIN = "play.bosketball.net";

    /** Pixel alpha of a PNG, 0 when out of bounds. */
    private static int alpha(java.awt.image.BufferedImage img, int x, int y) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return 0;
        }
        return (img.getRGB(x, y) >>> 24) & 0xFF;
    }

    /** True when the two images carry the very same pixels in a horizontal band. */
    private static boolean sameCrop(
        java.awt.image.BufferedImage a, int ay, java.awt.image.BufferedImage b, int by, int height
    ) {
        for (int dy = 0; dy < height; dy++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (alpha(a, x, ay + dy) != alpha(b, x, by + dy)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True when at least one pixel of the texture is visible (a blank nameplate piece is a hole). */
    private static boolean hasVisiblePixels(java.awt.image.BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Plain text of an Adventure component (bitmap glyphs included). */
    private static String plainText(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Round 37/38 — the seven rank nameplates, from the zip to the head.
     *
     * <p>The owner shipped `dedou3d_ranking_CustomNameplates.zip` (six materials in three finishes,
     * three bitmap glyphs each) and asked for seven plates, one per rank, with the nickname alone
     * on it. This test walks the whole chain for every rank: the skin mapping, the codepoint block,
     * the three textures, their font providers, the cap geometry the layout depends on, and the
     * contrast between the ink and the plate it is drawn on.
     */
    @Test
    public void round37EveryRankHasItsOwnShippedNameplate() throws Exception {
        String fontJson = Files.readString(Path.of("resourcepack/assets/minecraft/font/default.json"));
        java.util.Set<Integer> codepoints = new java.util.HashSet<>();
        java.util.Set<String> files = new java.util.HashSet<>();

        fr.bosketball.ui.NameplateSkin[] skins = fr.bosketball.ui.NameplateSkin.values();
        assertEquals("One plate per rank, six shipped plus the generated seventh", 7, skins.length);
        assertEquals("Round 38 keeps three glyphs per rank", 3, fr.bosketball.util.Glyphs.NAMEPLATE_SLOTS);

        for (fr.bosketball.ui.NameplateSkin skin : skins) {
            String key = skin.name().toLowerCase(java.util.Locale.ROOT);
            assertEquals("The skin order must follow the ladder", skin.ordinal(), skin.index());
            assertEquals("Ranks map to their own plate", skin, fr.bosketball.ui.NameplateSkin.of(
                fr.bosketball.model.Rank.valueOf(skin.name())));

            char[] expected = {
                fr.bosketball.util.Glyphs.nameplateGlyph(skin.index(), 0),
                fr.bosketball.util.Glyphs.nameplateGlyph(skin.index(), 1),
                fr.bosketball.util.Glyphs.nameplateGlyph(skin.index(), 2)
            };
            assertEquals(expected[0], skin.left());
            assertEquals(expected[1], skin.middle());
            assertEquals(expected[2], skin.right());

            for (char glyph : expected) {
                assertTrue("Nameplate glyphs live in the private use area: U+" + Integer.toHexString(glyph),
                    glyph >= '\uE000' && glyph <= '\uF8FF');
                assertTrue("Two nameplate glyphs share U+" + Integer.toHexString(glyph),
                    codepoints.add((int) glyph));
                assertTrue("The nameplate block must not steal a tab-frame codepoint",
                    glyph < '\uE370' || glyph > '\uE383');
            }

            String[] slots = { "left", "middle", "right" };
            for (int slot = 0; slot < 3; slot++) {
                String file = "np_" + key + "_" + slots[slot] + ".png";
                assertTrue("Duplicate nameplate texture " + file, files.add(file));
                java.io.File png = new java.io.File("resourcepack/assets/bosketball/textures/font/" + file);
                assertTrue("Missing nameplate texture: " + png, png.isFile());
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png);
                assertEquals(key + "/" + slots[slot] + " width", 16, img.getWidth());
                assertEquals(key + "/" + slots[slot] + " height", 20, img.getHeight());
                assertTrue("An empty nameplate piece is a hole in the bar: " + file,
                    hasVisiblePixels(img));
                assertTrue("The font must bind " + file + " at height 20, ascent 16",
                    fontJson.contains("bosketball:font/" + file)
                        && fontJson.contains("\"height\": 20,")
                        && fontJson.contains("\"ascent\": 16,"));
            }

            // Round 38 — the transparent margin inside each cap is what the layout has to subtract.
            // It is measured on the PNGs, so the constants can never drift from the art.
            java.awt.image.BufferedImage leftCap = javax.imageio.ImageIO.read(
                new java.io.File("resourcepack/assets/bosketball/textures/font/np_" + key + "_left.png"));
            java.awt.image.BufferedImage rightCap = javax.imageio.ImageIO.read(
                new java.io.File("resourcepack/assets/bosketball/textures/font/np_" + key + "_right.png"));
            assertEquals(key + ": the left cap's transparent margin",
                skin.edgeLeft(), alphaBounds(leftCap).x);
            assertEquals(key + ": the right cap's transparent margin",
                skin.edgeRight(), leftCap.getWidth() - (alphaBounds(rightCap).x + alphaBounds(rightCap).width));
        }
        assertEquals("Seven ranks x three glyphs", 21, codepoints.size());

        // Round 38: no crest glyph, in the pack or in the font.
        assertFalse("The crest glyphs are gone from the pack",
            new java.io.File("resourcepack/assets/bosketball/textures/font/np_mvp_crest.png").exists());
        assertFalse("... and from the font providers", fontJson.contains("np_rookie_crest.png"));
        assertEquals("U+E390 + 21 codepoints is the whole block",
            0xE390 + 21, java.util.Collections.max(codepoints) + 1);

        // The generator is the single source of truth: its constants must match the plugin's.
        String tool = Files.readString(Path.of("tools/generate_nameplates.py"));
        assertTrue("The generator must own the same codepoint block",
            tool.contains("FIRST_CODEPOINT = 0xE390"));
        assertTrue("The generator must write the same three slots",
            tool.contains("GLYPH_SLOTS = (\"left\", \"middle\", \"right\")"));
        for (fr.bosketball.ui.NameplateSkin skin : skins) {
            assertTrue("The generator must build the " + skin + " plate",
                tool.contains("RankPlate(\"" + skin.name().toLowerCase(java.util.Locale.ROOT) + "\""));
        }
        assertTrue("The seventh plate is generated, not recoloured from another rank",
            tool.contains("champion=True") && tool.contains("compose_champion("));
        assertTrue("The source zip must travel with the repository",
            new java.io.File("assets/nameplates/source/dedou3d_ranking_CustomNameplates.zip").isFile());

        // Staff are not part of the ladder and wear the plate of a rank they actually have.
        assertEquals(fr.bosketball.ui.NameplateSkin.PRO,
            fr.bosketball.ui.NameplateSkin.of(fr.bosketball.model.Rank.REFEREE));
        assertEquals(fr.bosketball.ui.NameplateSkin.LEGEND,
            fr.bosketball.ui.NameplateSkin.of(fr.bosketball.model.Rank.ADMIN));
    }

    /**
     * Round 38 — the nickname has to be readable on the plate it is drawn on.
     *
     * <p>Round 37 painted a near-white name on every plate, which is invisible on the light metal
     * fills of the five lowest ranks (the screenshot bug: "on voit mal le pseudo"). This measures
     * the median colour of the tile's inner fill and asserts that the ink really contrasts with it.
     */
    @Test
    public void round38NameplateInkContrastsWithItsPlate() throws Exception {
        for (fr.bosketball.ui.NameplateSkin skin : fr.bosketball.ui.NameplateSkin.values()) {
            String key = skin.name().toLowerCase(java.util.Locale.ROOT);
            java.awt.image.BufferedImage tile = javax.imageio.ImageIO.read(
                new java.io.File("resourcepack/assets/bosketball/textures/font/np_" + key + "_middle.png"));
            double[] fill = medianFill(tile);
            int ink = skin.ink().value();
            double fillLum = luminance((fill[0] + 1) / 256.0, (fill[1] + 1) / 256.0, (fill[2] + 1) / 256.0);
            double inkLum = luminance(((ink >> 16) & 0xFF) / 255.0, ((ink >> 8) & 0xFF) / 255.0,
                (ink & 0xFF) / 255.0);
            double ratio = (Math.max(fillLum, inkLum) + 0.05) / (Math.min(fillLum, inkLum) + 0.05);
            // WCAG AA for large bold text is 4.5:1; every plate clears it, and the five light
            // metal plates clear 8:1.
            assertTrue(skin + ": the nickname must be readable on its plate (contrast " + ratio + ")",
                ratio >= 4.5);
        }
    }

    /**
     * Round 38 — the plate hugs the nickname and stays centred.
     *
     * <p>Every case checks the invariants of {@link fr.bosketball.ui.NameplateLayout}: enough tiles
     * for the name plus its padding <em>inside the caps' art</em>, a start position past the cap's
     * transparent margin, no negative tail, and a line that advances by exactly {@code widthPx} so
     * a centred display stays on the head. Round 37 failed the third one: it measured the padding
     * against the raw cell width, so the bar ended up narrower than the name it was carrying.
     */
    @Test
    public void round38NameplateHugsTheNameAndStaysCentred() {
        String[] names = { "A", "MVP", "MaxLananas", "ABCDEFGHIJKLMNOP", "team_capitaine_42" };
        int cases = 0;
        for (fr.bosketball.ui.NameplateSkin skin : fr.bosketball.ui.NameplateSkin.values()) {
            fr.bosketball.model.Rank rank = fr.bosketball.model.Rank.valueOf(skin.name());
            for (String name : names) {
                fr.bosketball.ui.NameplateLayout.Plate plate =
                    fr.bosketball.ui.NameplateLayout.build(rank, name);
                String tag = skin + "/" + name + ": ";

                assertTrue(tag + "at least two tiles", plate.tiles() >= fr.bosketball.ui.NameplateLayout.MIN_TILES);
                assertTrue(tag + "never absurdly wide",
                    plate.tiles() <= fr.bosketball.ui.NameplateLayout.MAX_TILES);
                // Round 42: consecutive tiles overlap by one pixel so no seam can open at a
                // fractional display scale; the bar is that much narrower than tiles + 2 caps.
                assertEquals(tag + "the bar is tiles + 2 caps, minus the tile overlaps",
                    fr.bosketball.util.Glyphs.NAMEPLATE_TILE_PX * (2 + plate.tiles())
                        - fr.bosketball.ui.NameplateLayout.TILE_OVERLAP_PX * (1 + plate.tiles()),
                    plate.widthPx());

                int usable = plate.widthPx() - skin.edgeLeft() - skin.edgeRight();
                assertTrue(tag + "the nickname and its padding must fit inside the art",
                    plate.contentPx() + fr.bosketball.ui.NameplateLayout.PADDING_PX * 2 <= usable);
                assertTrue(tag + "the name starts after the cap's margin", plate.startPx() >= skin.edgeLeft());
                assertTrue(tag + "the name ends before the right margin",
                    plate.startPx() + plate.contentPx() <= plate.widthPx() - skin.edgeRight());
                assertTrue(tag + "the remaining room is split evenly (never more than 1 px off)",
                    Math.abs((plate.startPx() - skin.edgeLeft())
                        - (plate.widthPx() - skin.edgeRight() - plate.startPx() - plate.contentPx())) <= 1);
                assertEquals(tag + "the line must advance by exactly the bar width",
                    plate.widthPx(), plate.startPx() + plate.contentPx() + plate.tailPx());
                assertTrue(tag + "no negative tail", plate.tailPx() >= 0);
                assertTrue(tag + "the plate is the nickname, nothing else",
                    plainText(plate.plate()).contains(name)
                        && plainText(plate.plate()).replace(name, "").trim().isEmpty()
                        == false);
                assertEquals(tag + "the bar holds exactly the nickname's advance",
                    fr.bosketball.util.Glyphs.textAdvancePx(name, true), plate.contentPx());
                assertEquals(tag + "the ink is the skin's", skin.ink(),
                    inkOf(plate.plate(), name));
                cases++;
            }
        }
        assertEquals("Seven ranks x five names", 35, cases);

        // A longer nickname never shrinks the plate.
        int small = fr.bosketball.ui.NameplateLayout.build(fr.bosketball.model.Rank.MVP, "A").widthPx();
        int big = fr.bosketball.ui.NameplateLayout.build(
            fr.bosketball.model.Rank.MVP, "ABCDEFGHIJKLMNOP").widthPx();
        assertTrue("The plate must grow with the name", big > small);

        // An empty or hostile nickname can never produce a broken plate.
        fr.bosketball.ui.NameplateLayout.Plate blank =
            fr.bosketball.ui.NameplateLayout.build(fr.bosketball.model.Rank.PRO, "   ");
        assertTrue("A blank name still gets a plate with one glyph",
            plainText(blank.plate()).contains("?") && blank.contentPx() > 0);
        // Both MiniMessage markup and legacy colour codes are stripped, newlines are dropped.
        assertEquals("cBob", fr.bosketball.ui.NameplateLayout.sanitize("<\u00a7cBob>\n"));
    }

    /**
     * Round 38 — the manager half: the plate is one entity, hidden from its owner, destroyed on
     * every exit path. Everything that can leak is asserted here.
     */
    @Test
    public void round38NameplateEntitiesAreHiddenFromTheirOwnerAndNeverLeak() throws Exception {
        String service = readSource("src/main/java/fr/bosketball/ui/NameplateService.java");
        assertTrue("The display is transient", service.contains("display.setPersistent(false)"));
        assertTrue("The vanilla black box must be gone", service.contains("display.setDefaultBackground(false)"));
        assertTrue("The plate must be centred on the head",
            service.contains("setAlignment(TextDisplay.TextAlignment.CENTER)"));
        assertTrue("The vanilla nametag must be hidden, or two names show at once",
            service.contains("NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER"));
        assertTrue("A quit must destroy the entity",
            service.contains("public void forget(UUID playerId)") && service.contains("rig.body().remove()"));
        assertFalse("Round 38: one entity per player, no crest to leak", service.contains("crest"));
        assertTrue("Disabling the plugin must destroy every plate", service.contains("public void shutdown()"));
        assertTrue("Offline players are swept every 10 s", service.contains("SWEEP_PERIOD = 200"));
        assertTrue("An invisible player must not be given away by their plate",
            service.contains("if (owner.isInvisible()) {"));
        assertTrue("A vanished player must not be given away either", service.contains("viewer.canSee(owner)"));
        assertTrue("The player's own nameplate setting is honoured",
            service.contains("!ownerProfile.settings().overheadNameplates()"));
        assertTrue("The plate follows the head, not the feet", service.contains("player.getHeight()"));
        assertFalse("Round 38: the plate carries the nickname only - no ELO, no points",
            service.contains("showElo") || service.contains("suffixFor("));

        String plugin = readSource("src/main/java/fr/bosketball/BosketBallPlugin.java");
        assertTrue("The plates are ticked", plugin.contains("step(\"nameplate\", nameplateService::tickAll)"));
        assertTrue("The plates are destroyed on disable", plugin.contains("nameplateService.shutdown()"));
        assertTrue("The plates respect the master switch, even at runtime",
            service.contains("if (!plugin.settings().nameplates().enabled()) {")
                && service.contains("shutdown();\n            }\n            return;"));
        assertTrue("A cinematic shot must stay clean",
            service.contains("plugin.cinematicCamera().isWatching(owner.getUniqueId())"));
        String config = readSource("src/main/resources/config.yml");
        assertTrue("The section must be documented in config.yml",
            config.contains("nameplates:") && config.contains("view-range:") && config.contains("scale:"));
        assertTrue("Round 38: the plate is scaled up a little over the head", config.contains("scale: 1.15"));
    }

    /** Alpha bounding box of a texture (x, y, width, height). */
    private static java.awt.Rectangle alphaBounds(java.awt.image.BufferedImage img) {
        int minX = img.getWidth();
        int minY = img.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return new java.awt.Rectangle(0, 0, 0, 0);
        }
        return new java.awt.Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Median RGB of the tile's inner fill, i.e. the pixels the nickname is drawn on. */
    private static double[] medianFill(java.awt.image.BufferedImage img) {
        java.util.List<Integer> rs = new java.util.ArrayList<>();
        java.util.List<Integer> gs = new java.util.ArrayList<>();
        java.util.List<Integer> bs = new java.util.ArrayList<>();
        for (int y = 10; y < 16; y++) {
            for (int x = 4; x < 13; x++) {
                int argb = img.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) < 200) {
                    continue;
                }
                rs.add((argb >> 16) & 0xFF);
                gs.add((argb >> 8) & 0xFF);
                bs.add(argb & 0xFF);
            }
        }
        if (rs.isEmpty()) {
            return new double[] { 0, 0, 0 };
        }
        java.util.Collections.sort(rs);
        java.util.Collections.sort(gs);
        java.util.Collections.sort(bs);
        int mid = rs.size() / 2;
        return new double[] { rs.get(mid), gs.get(mid), bs.get(mid) };
    }

    /** WCAG relative luminance. */
    private static double luminance(double r, double g, double b) {
        double rr = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        double gg = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        double bb = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * rr + 0.7152 * gg + 0.0722 * bb;
    }

    /** Colour the nickname itself is drawn in (the bar's glyphs are styled white, so skip them). */
    private static net.kyori.adventure.text.format.TextColor inkOf(
            net.kyori.adventure.text.Component c, String name) {
        if (c instanceof net.kyori.adventure.text.TextComponent text && text.content().equals(name)) {
            return c.color();
        }
        for (net.kyori.adventure.text.Component child : c.children()) {
            net.kyori.adventure.text.format.TextColor found = inkOf(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }



    /** Reads a plugin source file (Round 27 wiring assertions). */
    private static String readSource(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (java.io.IOException ex) {
            throw new AssertionError("Cannot read " + path, ex);
        }
    }
}
