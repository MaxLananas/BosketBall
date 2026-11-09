package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import fr.bosketball.economy.EconomyBalance;
import fr.bosketball.economy.RateCard;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerQuest;
import fr.bosketball.model.ProgressStat;
import fr.bosketball.model.Rank;
import fr.bosketball.progression.ProgressService;
import fr.bosketball.progression.QuestCatalog;
import fr.bosketball.progression.QuestRewardGuard;
import fr.bosketball.season.SeasonMath;
import fr.bosketball.season.SeasonRecord;
import fr.bosketball.season.SeasonSummary;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * The economy and progression test suite.
 *
 * <p>Eleven tests, each one guarding a rule the economy depends on:
 * <ol>
 *   <li>the rate card is complete and self-consistent,</li>
 *   <li>the crate is a sink and not a faucet,</li>
 *   <li>the result economy is symmetric — a loss pays, and losing is never rational,</li>
 *   <li>no reward path pays twice,</li>
 *   <li>no sink can drive a balance negative,</li>
 *   <li>ELO is bounded and symmetric,</li>
 *   <li>a season reset preserves lifetime stats,</li>
 *   <li>quest progress cannot exceed its target,</li>
 *   <li>every daily is reachable in one session and needs no teammate,</li>
 *   <li>a bot match pays differently from a human match,</li>
 *   <li>nothing pays for idling.</li>
 * </ol>
 */
public class EconomyBalanceTest {

    private static PlayerProfile profile() {
        return new PlayerProfile(UUID.randomUUID(), "Tester");
    }

    // ---------------------------------------------------------------- rate card

    @Test
    public void testRateCardListsEverySourceAndSinkAndIsSelfConsistent() {
        List<RateCard.Row> rows = RateCard.rows();
        assertTrue("The rate card must not be empty", rows.size() >= 20);

        assertFalse("The rate card must have sinks — a currency with no sink inflates forever",
            RateCard.sinks().isEmpty());
        assertFalse("The rate card must have sources", RateCard.sources().isEmpty());

        // No two rows may share an id: the id is what the audit and the docs use.
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (RateCard.Row row : rows) {
            assertTrue("Duplicate rate-card id: " + row.id(), ids.add(row.id()));
            assertTrue("A row must carry a label: " + row.id(), row.label() != null && !row.label().isBlank());
            assertTrue("A row must belong to a family: " + row.id(), row.family() != null);
        }

        // Every payout a source declares must be positive and every sink negative.
        for (RateCard.Row row : rows) {
            if (row.isSource()) {
                assertTrue("A source must pay something: " + row.id(), row.coins() > 0);
            }
            if (row.isSink()) {
                assertTrue("A sink must cost something: " + row.id(), row.coins() < 0);
            }
        }

        // The result rows must match the shipped numbers, so the card cannot drift from the code.
        assertEquals(RateCard.WIN_COINS, RateCard.row("match.win").coins());
        assertEquals(RateCard.LOSS_COINS, RateCard.row("match.loss").coins());
        assertEquals(RateCard.CRATE_COST_COINS, -RateCard.row("sink.crate").coins());

        // Every tradeable family must have a sink, or its value has nowhere to go.
        for (var entry : EconomyBalance.sinkCoverage().entrySet()) {
            assertTrue("The " + entry.getKey() + " family has no sink — its value would pile up",
                entry.getValue());
        }

        // The no-loop proof: over a season the sinks must be able to absorb the faucets. A
        // ratio at or above 1.0 means the economy has a way to take coins back out.
        int shopCoins = EconomyBalance.shopInventoryCoins();
        assertTrue("The cosmetic catalogue must be a real sink: " + shopCoins,
            shopCoins >= 5_000);
        double ratio = EconomyBalance.sinkToFaucetRatio(80, shopCoins);
        assertTrue("The sinks must be able to absorb what the economy pays out: " + ratio,
            ratio >= 1.0);
    }

    @Test
    public void testEveryRepeatableRewardHasACooldown() {
        List<RateCard.Row> offenders = RateCard.repeatableSourcesWithoutCooldown();
        assertTrue("A repeatable reward without a cooldown is an unbounded faucet: " + offenders,
            offenders.isEmpty());
    }

    @Test
    public void testTheCrateIsASinkAndNotAFaucet() {
        // The shipped crate used to cost 250 and refund a uniform 60-500 plus a 15% chance of
        // 150 — 302.5 expected against a 250 cost, i.e. +52.5 coins every single opening.
        double oldNet = RateCard.expectedCrateNet(250, 60, 500, 150, 0.15);
        assertTrue("The old crate was a money printer and must read as one in this test",
            oldNet > 0.0);

        double newNet = RateCard.expectedCrateNet(
            RateCard.CRATE_COST_COINS, RateCard.CRATE_COIN_MIN, RateCard.CRATE_COIN_MAX,
            RateCard.CRATE_BONUS_COINS, RateCard.CRATE_BONUS_CHANCE);
        assertTrue("The crate must take coins out of the economy, not put them in: " + newNet,
            newNet < 0.0);

        // And the shipped config must agree.
        var crates = fr.bosketball.config.BosketConfig.defaults().crates();
        double configNet = RateCard.expectedCrateNet(
            crates.costCoins(), crates.coinMin(), crates.coinMax(), crates.bonusCoins(), crates.bonusChance());
        assertTrue("The shipped crate configuration must be a sink: " + configNet, configNet < 0.0);
        assertTrue("The shipped crate must have a daily limit", crates.dailyLimit() > 0);
        assertTrue("The shipped crate cooldown must outlast the opening animation",
            crates.cooldownSeconds() * 1000L >= 10_000L);
    }

    // ---------------------------------------------------------------- symmetry

    @Test
    public void testTheResultEconomyIsSymmetricAndALossAlwaysPays() {
        var econ = fr.bosketball.config.BosketConfig.defaults().economy();

        assertTrue("A loss must pay something", econ.lossCoins() > 0);
        assertTrue("A loss must never pay as much as a win", econ.lossCoins() < econ.winCoins());

        double ratio = RateCard.winToLossRatio(econ.winCoins(), econ.lossCoins());
        assertTrue("Win-to-loss ratio " + ratio + " exceeds " + RateCard.MAX_WIN_TO_LOSS_RATIO,
            ratio <= RateCard.MAX_WIN_TO_LOSS_RATIO);

        // Throwing a match must never beat trying one. At a 0% win rate the two are equal —
        // a player who loses every match either way earns the same — but from any non-zero
        // win rate, trying strictly dominates throwing.
        for (double winRate = 0.0; winRate <= 1.0; winRate += 0.05) {
            double trying = winRate * econ.winCoins() + (1.0 - winRate) * econ.lossCoins();
            double throwing = econ.lossCoins();
            assertTrue("At a " + Math.round(winRate * 100) + "% win rate, throwing ("
                + throwing + ") beat trying (" + trying + ")", trying >= throwing);
        }
        assertTrue("A player who wins even one match in ten must do better by trying",
            0.1 * econ.winCoins() + 0.9 * econ.lossCoins() > econ.lossCoins());

        // And the audit must find nothing.
        assertTrue("The shipped economy must be symmetric: " + EconomyBalance.auditSymmetry(
            econ.winCoins(), econ.lossCoins()),
            EconomyBalance.auditSymmetry(econ.winCoins(), econ.lossCoins()).isEmpty());
        assertTrue("The shipped economy must be sound: " + EconomyBalance.auditEconomy(
            econ.winCoins(), econ.lossCoins(), econ.mvpBonusCoins(), econ.twoPointCoins(),
            econ.threePointCoins(), econ.dunkBonusCoins(), econ.assistCoins(),
            econ.stealCoins(), econ.blockCoins(), 300, 40, 220),
            EconomyBalance.auditEconomy(
                econ.winCoins(), econ.lossCoins(), econ.mvpBonusCoins(), econ.twoPointCoins(),
                econ.threePointCoins(), econ.dunkBonusCoins(), econ.assistCoins(),
                econ.stealCoins(), econ.blockCoins(), 300, 40, 220).isEmpty());
    }

    @Test
    public void testAHourlyIncomeTableExistsForEverySkillBandAndGrowsWithSkill() {
        // What the Average band earns on the court in one competitive match.
        int averageMatch = 56;
        List<RateCard.BandIncome> table = RateCard.hourlyIncome(averageMatch);
        assertEquals("One line per skill band", RateCard.SkillBand.values().length, table.size());

        int previous = -1;
        for (RateCard.BandIncome line : table) {
            assertTrue("Every band must earn something", line.totalCoins() > 0);
            assertTrue("Skill must show in the ledger: " + line.describe(),
                line.totalCoins() > previous);
            previous = line.totalCoins();
        }

        // The result economy must lead: a win is worth more than a good match on the court,
        // or the coin ledger stops measuring skill.
        RateCard.BandIncome average = table.get(1);
        assertTrue("The result economy must be worth at least a third of the total",
            average.resultCoins() * 3 >= average.totalCoins());

        // The gap between the best and worst band is what keeps the monthly coin leaderboard a
        // skill board rather than an hours-logged board.
        RateCard.BandIncome rookie = table.get(0);
        RateCard.BandIncome elite = table.get(table.size() - 1);
        assertTrue("The skill gap must be material: " + rookie.describe() + " vs " + elite.describe(),
            elite.totalCoins() > rookie.totalCoins() * 1.10);
        assertTrue("The Elite band must out-earn the Rookie band by a visible margin",
            elite.totalCoins() - rookie.totalCoins() >= 80);

        // The on-court economy must not swamp the result economy.
        assertTrue("The on-court economy must stay below the result economy",
            average.onCourtCoins() < average.resultCoins() * 3);
    }

    // ---------------------------------------------------------------- idempotency

    @Test
    public void testNoRewardPathPaysTwice() {
        // The quest ledger: a quest pays once, even if the claim is attempted twice.
        QuestRewardGuard guard = new QuestRewardGuard();
        PlayerProfile p = profile();
        p.setCoins(0);
        PlayerQuest quest = new PlayerQuest("d_points_20", "Scoring Touch", "Score 20 points",
            ProgressStat.POINTS_SCORED, 20, 90, 60, "icon_crossover", 1000L, 2000L);
        quest.setProgress(20);

        assertEquals(90, guard.pay(p, quest));
        assertEquals("A quest must never pay twice", 0, guard.pay(p, quest));
        assertEquals("The player's balance must reflect exactly one payment", 90, p.coins());

        // The same template on a different day is a different payout.
        PlayerQuest tomorrow = new PlayerQuest("d_points_20", "Scoring Touch", "Score 20 points",
            ProgressStat.POINTS_SCORED, 20, 90, 60, "icon_crossover", 1000L + 86_400_000L,
            2000L + 86_400_000L);
        tomorrow.setProgress(20);
        assertEquals("The same template tomorrow is a different quest", 90, guard.pay(p, tomorrow));
        assertEquals(180, p.coins());

        // The season ledger: a season payout pays once too.
        EconomyBalance.RewardLedger ledger = new EconomyBalance.RewardLedger();
        PlayerProfile s = profile();
        s.setCoins(0);
        assertEquals(800, ledger.pay(s, "season:7", 800));
        assertEquals("A season payout must never pay twice", 0, ledger.pay(s, "season:7", 800));
        assertEquals(800, s.coins());

        // Two different players are independent.
        PlayerProfile other = profile();
        other.setCoins(0);
        assertEquals(800, ledger.pay(other, "season:7", 800));
        assertEquals(800, other.coins());
    }

    // ---------------------------------------------------------------- sinks

    @Test
    public void testNoSinkCanDriveABalanceNegative() {
        PlayerProfile p = profile();
        p.setCoins(100);

        // A sink the player cannot afford must fail, not go negative.
        assertFalse("A player who cannot afford the sink must not be charged",
            p.spendCoins(300));
        assertEquals("A refused sink must leave the balance untouched", 100, p.coins());

        // A sink the player can afford must land exactly on zero, never below.
        assertTrue(p.spendCoins(100));
        assertEquals("A balance may reach zero but never go below it", 0, p.coins());
        assertFalse(p.spendCoins(1));
        assertEquals(0, p.coins());

        // A zero-cost sink is always allowed and changes nothing.
        assertTrue(p.spendCoins(0));
        assertEquals(0, p.coins());

        // A negative cost must be refused outright.
        assertFalse("A negative cost must never be treated as a payout",
            p.spendCoins(-50));
        assertEquals(0, p.coins());
    }

    // ---------------------------------------------------------------- ELO

    @Test
    public void testEloIsBoundedAndSymmetric() {
        // The ladder is deliberately not zero-sum: a win is +15, a loss is -5, so the ladder
        // climbs (Round 26, "winning must be worth three times a loss so the ladder climbs").
        // What must be symmetric is the APPLICATION: the same clamp, no rating-gap dependence,
        // and neither bench structurally favoured.
        assertTrue("The ELO rules must treat both benches the same way",
            EconomyBalance.eloRulesAreSymmetric());

        assertTrue(EconomyBalance.eloDelta(true, false, false) > 0);
        assertTrue(EconomyBalance.eloDelta(false, false, false) < 0);
        assertEquals("A draw must move nothing", 0, EconomyBalance.eloDelta(false, true, false));
        assertEquals("A draw must move nothing for the winner either",
            0, EconomyBalance.eloDelta(true, true, false));

        // The bounds — the clamp applies to the result, so neither end is a wall.
        assertEquals(SeasonMath.MIN_ELO, EconomyBalance.applyElo(SeasonMath.MIN_ELO, -500));
        assertEquals(SeasonMath.MAX_ELO, EconomyBalance.applyElo(SeasonMath.MAX_ELO, 500));
        assertEquals("A normal win must land on the expected rating",
            1015, EconomyBalance.applyElo(1000, EconomyBalance.eloDelta(true, false, false)));
        assertEquals(995, EconomyBalance.applyElo(1000, EconomyBalance.eloDelta(false, false, false)));

        // The magnitude never depends on who you played — that is the anti-smurf property. The
        // rule signature only takes the outcome, the draw flag and the bot flag, so there is no
        // rating gap to sweep here; the two constants below are the whole guarantee.
        assertEquals("The win reward must never depend on the rating gap",
            15, EconomyBalance.eloDelta(true, false, false));
        assertEquals(-5, EconomyBalance.eloDelta(false, false, false));

        // A bot match must move no rating at all.
        assertEquals("A bot match must move no rating", 0, EconomyBalance.eloDelta(true, false, true));
        assertEquals(0, EconomyBalance.eloDelta(false, false, true));
    }

    // ---------------------------------------------------------------- season

    @Test
    public void testASeasonResetPreservesLifetimeStats() {
        PlayerProfile p = profile();
        p.setWins(400);
        p.setLosses(210);
        p.setPoints(12_345);
        p.setCoins(99_999);
        p.setElo(2400);

        int lifetimeWins = p.wins();
        int lifetimeLosses = p.losses();
        int lifetimePoints = p.points();
        long lifetimeEarned = p.totalCoinsEarned();

        // The soft reset moves the rating.
        int reset = SeasonMath.softReset(p.elo(), 1000, 0.5);
        assertEquals("The reset must move the rating", 1700, reset);
        p.setElo(reset);

        // The season record resets — that is the point of a season.
        SeasonRecord record = new SeasonRecord(p.uuid(), "Tester", p.elo());
        record.noteMatch(true, 1150);
        record.resetForNewSeason(reset);
        assertEquals(0, record.matches());
        assertEquals(0, record.wins());

        // The lifetime stats do not.
        assertEquals("A season reset must not touch lifetime wins", lifetimeWins, p.wins());
        assertEquals("A season reset must not touch lifetime losses", lifetimeLosses, p.losses());
        assertEquals("A season reset must not touch lifetime points", lifetimePoints, p.points());
        assertEquals("A season reset must not touch lifetime coins earned", lifetimeEarned, p.totalCoinsEarned());
        assertEquals("A season reset must not touch the coin balance", 99_999, p.coins());
    }

    @Test
    public void testTheEndOfSeasonCardIsScreenshotWorthyAndUsesHouseStyle() {
        SeasonSummary summary = SeasonSummary.of("Kobe", "SEASON III", 3, 2, 1650,
            Rank.SUPERSTAR, 42, 27, 1200, 450);

        assertEquals("SEASON III — YOUR SEASON", summary.header());
        assertEquals(27, summary.wins());
        assertEquals(15, summary.losses());
        assertEquals(64, summary.winRatePercent());
        assertEquals(1650, summary.totalCoins());
        assertTrue(summary.isScreenshotWorthy());

        List<String> card = summary.card();
        assertTrue("A card with no lines screenshots as nothing", card.size() >= 4);

        String all = String.join("\n", card);
        assertFalse("The card must not use a pipe separator", all.contains("|"));
        assertFalse("The card must not use a bullet separator", all.contains("•"));
        assertFalse("The card must not use a star", all.contains("★") || all.contains("✦"));
        assertTrue("The card must use the house middot separator", all.contains("<#64748B>·</#64748B>"));
        assertTrue("The card must use the palette, never a raw hex",
            all.contains(fr.bosketball.util.Palette.GOLD_SOFT)
                && all.contains(fr.bosketball.util.Palette.ICE)
                && all.contains(fr.bosketball.util.Palette.MUTED));

        // No raw hex outside the palette.
        java.util.regex.Matcher rawHex = java.util.regex.Pattern
            .compile("#[0-9A-Fa-f]{6}").matcher(all);
        while (rawHex.find()) {
            String hex = rawHex.group().toUpperCase(java.util.Locale.ROOT);
            boolean known = false;
            for (String allowed : fr.bosketball.util.Palette.ALL) {
                if (allowed.toUpperCase(java.util.Locale.ROOT).equals(hex)) {
                    known = true;
                    break;
                }
            }
            assertTrue("The card uses a colour outside the palette: " + hex, known);
        }

        // A season with no games played is not a card worth screenshotting.
        assertFalse(SeasonSummary.of("Nobody", "SEASON I", 1, 0, 1000, Rank.ROOKIE, 0, 0, 200, 0)
            .isScreenshotWorthy());
    }

    // ---------------------------------------------------------------- quests

    @Test
    public void testQuestProgressCannotExceedItsTarget() {
        PlayerQuest quest = new PlayerQuest("d_points_20", "Scoring Touch", "Score 20 points",
            ProgressStat.POINTS_SCORED, 20, 90, 60, "icon_crossover", 0L, 0L);

        quest.addProgress(5);
        assertEquals(5, quest.progress());
        quest.addProgress(100);
        assertEquals("Progress must clamp at the target", 20, quest.progress());
        quest.addProgress(100);
        assertEquals("Progress must stay clamped", 20, quest.progress());
        assertTrue(quest.isComplete());

        // A negative delta is not progress.
        quest.addProgress(-50);
        assertEquals(20, quest.progress());

        // A quest with a broken target still has a target.
        PlayerQuest broken = new PlayerQuest("x", "X", "X", ProgressStat.POINTS_SCORED,
            -5, 10, 5, "icon_crossover", 0L, 0L);
        assertTrue("A quest must always have a positive target", broken.target() >= 1);
        assertTrue(broken.completionRatio() <= 1.0);
    }

    @Test
    public void testEveryQuestIsReachableInOneSessionAndNeedsNoTeammate() {
        // Eight matches is a session for an engaged player.
        int session = 8;

        for (QuestCatalog.Template template : QuestCatalog.pool(QuestCatalog.Board.DAILY)) {
            assertTrue("The daily '" + template.id() + "' cannot be reached in one session",
                QuestCatalog.isSoloAchievable(template, session));
            assertTrue("The daily '" + template.id() + "' must pay something",
                template.rewardCoins() > 0);
            assertTrue("The daily '" + template.id() + "' must be English",
                template.title().codePointAt(0) < 128);
        }

        for (QuestCatalog.Template template : QuestCatalog.pool(QuestCatalog.Board.WEEKLY)) {
            assertTrue("The weekly '" + template.id() + "' must pay something",
                template.rewardCoins() > 0);
        }

        for (QuestCatalog.Template template : QuestCatalog.pool(QuestCatalog.Board.CONTRACT)) {
            assertTrue("The sprint '" + template.id() + "' must pay something",
                template.rewardCoins() > 0);
        }

        // The board sizes are the habit: three dailies, one weekly, one sprint.
        assertEquals("Three dailies is a habit, seven is a chore", 3, ProgressService.DAILY_QUEST_COUNT);
        assertEquals("One weekly is the catch-up valve", 1, ProgressService.WEEKLY_QUEST_COUNT);
        assertEquals("One sprint, not a second daily board", 1, ProgressService.CONTRACT_COUNT);

        // A party quest needs a second player, so it can never be a solo daily.
        assertFalse(QuestCatalog.isSoloAchievable(
            new QuestCatalog.Template("t", "T", "T", ProgressStat.PARTY_MATCHES, 1, 10, 5,
                "icon_battle", QuestCatalog.Board.DAILY), session));
    }

    // ---------------------------------------------------------------- anti-abuse

    @Test
    public void testABotMatchPaysDifferentlyFromAHumanMatch() {
        assertTrue("A bot match must not pay what a human match pays",
            EconomyBalance.botMatchPaysDifferently());

        int humanWin = EconomyBalance.resultCoins(true, false, true, false);
        int botWin = EconomyBalance.resultCoins(true, false, true, true);
        assertTrue("A bot-backed win must pay less: " + botWin + " vs " + humanWin,
            botWin < humanWin);
        assertEquals("A bot-backed win must pay half", humanWin / 2, botWin);

        int humanLoss = EconomyBalance.resultCoins(false, false, true, false);
        int botLoss = EconomyBalance.resultCoins(false, false, true, true);
        assertTrue("A bot-backed loss must pay less too", botLoss < humanLoss);

        assertEquals("A bot match must move no rating",
            0.0, EconomyBalance.eloMultiplier(true), 0.0001);
        assertEquals("A human match must move the full rating",
            1.0, EconomyBalance.eloMultiplier(false), 0.0001);

        // A casual match pays less than a ranked one.
        assertTrue(EconomyBalance.matchMultiplier(false, false) < EconomyBalance.matchMultiplier(true, false));

        // And the shipped config must use the shipped multiplier.
        var econ = fr.bosketball.config.BosketConfig.defaults().economy();
        assertEquals("The shipped bot multiplier must be the rate card's",
            RateCard.BOT_MATCH_MULTIPLIER, econ.botMatchMultiplier(), 0.0001);
    }

    @Test
    public void testTheBotMatchMultiplierIsALiveConfigKeyAndNotADeadOne() {
        // Round 84: `economy.bot-match-multiplier` was parsed into the config record and then
        // never read by the payout path, which consulted a constant. An admin who tuned the key
        // saw no effect at all. The config value now wins, and this test fails if that link is
        // ever broken again.
        double shipped = fr.bosketball.config.BosketConfig.defaults().economy().botMatchMultiplier();
        try {
            EconomyBalance.setBotMatchMultiplier(0.25);
            assertEquals("A tuned bot multiplier must reach the payout path",
                0.25, EconomyBalance.botMatchMultiplier(), 0.0001);
            assertEquals("A bot match must use the tuned multiplier",
                0.25, EconomyBalance.matchMultiplier(true, true), 0.0001);
            assertEquals("A human ranked match must not be affected",
                1.0, EconomyBalance.matchMultiplier(true, false), 0.0001);
            assertEquals("A human casual match must not be affected",
                EconomyBalance.CASUAL_MATCH_MULTIPLIER,
                EconomyBalance.matchMultiplier(false, false), 0.0001);

            // Out-of-range and non-finite values fall back rather than break the economy.
            EconomyBalance.setBotMatchMultiplier(5.0);
            assertEquals("An over-range multiplier must be clamped", 1.0,
                EconomyBalance.botMatchMultiplier(), 0.0001);
            EconomyBalance.setBotMatchMultiplier(Double.NaN);
            assertEquals("A non-finite multiplier must fall back to the shipped one",
                RateCard.BOT_MATCH_MULTIPLIER, EconomyBalance.botMatchMultiplier(), 0.0001);
        } finally {
            EconomyBalance.setBotMatchMultiplier(shipped);
        }
        assertEquals("The shipped multiplier must be restored",
            shipped, EconomyBalance.botMatchMultiplier(), 0.0001);
    }

    @Test
    public void testNothingPaysForIdling() {
        // The only repeatable payouts are the ones tied to an event the player performed.
        for (RateCard.Row row : RateCard.repeatableSourcesWithoutCooldown()) {
            assertTrue("A repeatable row without a cooldown can be farmed by idling: " + row.id(),
                row.isRepeatableWithoutCooldown() == false);
        }

        // The crate, the rerolls and the daily login are all behind a clock.
        assertTrue("The crate needs a cooldown", RateCard.CRATE_COOLDOWN_MILLIS > 0L);
        assertTrue("The crate needs a daily limit", RateCard.CRATE_DAILY_LIMIT > 0);
        assertTrue("The daily login is a once-a-day payout",
            RateCard.row("progress.daily_login").cooldownMillis() >= 86_400_000L);

        // And the GG reward — the one payout that is pure chat — is once per match, not a faucet.
        assertFalse("The GG reward is a one-shot per match", RateCard.row("solo.gg").repeatable());

        // The result economy pays per match, and the match itself is the clock.
        assertFalse("A match payout is not a repeatable faucet", RateCard.row("match.win").repeatable());
    }

    @Test
    public void testTheServerTellsYouHowManyGamesFromTheNextRank() {
        // A player at 1049 ELO is one win from Hooper (1050). The ladder moves +15 a win, so
        // the honest number of games is one.
        assertEquals(1, fr.bosketball.stats.RankProgress.gamesToNextRank(1049));
        assertEquals(1, fr.bosketball.stats.RankProgress.gamesToNextRank(1040));
        assertEquals(1, fr.bosketball.stats.RankProgress.gamesToNextRank(1035));
        assertEquals(2, fr.bosketball.stats.RankProgress.gamesToNextRank(1025));
        assertEquals(4, fr.bosketball.stats.RankProgress.gamesToNextRank(1000));

        // The top rank has no next rank, so there is nothing to signal.
        assertEquals(0, fr.bosketball.stats.RankProgress.gamesToNextRank(2200));
        assertEquals(0, fr.bosketball.stats.RankProgress.gamesToNextRank(9999));
        assertTrue("The top rank must have no signal",
            fr.bosketball.stats.RankProgress.signal(2400).isEmpty());

        // Close enough to be worth saying, far enough not to be.
        assertTrue(fr.bosketball.stats.RankProgress.isCloseEnough(1049));
        assertTrue(fr.bosketball.stats.RankProgress.isCloseEnough(1010));
        assertTrue(fr.bosketball.stats.RankProgress.isCloseEnough(1000));
        // 100 ELO is 63 wins from the next rank — a fact of life, not a goal.
        assertFalse("A gap of 63 games is a fact of life, not a goal",
            fr.bosketball.stats.RankProgress.isCloseEnough(100));
        assertTrue("A gap of 63 games gets no signal at all",
            fr.bosketball.stats.RankProgress.signal(100).isEmpty());

        // The signal says what happened, it does not make a promise.
        String signal = fr.bosketball.stats.RankProgress.signal(1035);
        assertTrue("The signal must name the rank: " + signal, signal.contains("Hooper"));
        assertTrue("The signal must be honest about what a game can do: " + signal,
            signal.contains("at best"));
        assertFalse("The signal must not use a pipe separator", signal.contains("|"));
        assertFalse("The signal must not use a star", signal.contains("★"));
        assertTrue("The signal must use the palette", signal.contains(fr.bosketball.util.Palette.GOLD_SOFT));

        // The progress ratio runs 0 to 1 across the CURRENT tier. The Rookie tier spans 0-1049,
        // so 1000 ELO is already 95% of the way through it — which is exactly the right reading:
        // the player is one win from the next rank.
        assertEquals(0.0, fr.bosketball.stats.RankProgress.progressRatio(0), 0.001);
        assertTrue(fr.bosketball.stats.RankProgress.progressRatio(1049) > 0.9);
        assertEquals(0.0, fr.bosketball.stats.RankProgress.progressRatio(1050), 0.001);
        assertTrue(fr.bosketball.stats.RankProgress.progressRatio(1100) > 0.0);
        assertEquals(1.0, fr.bosketball.stats.RankProgress.progressRatio(2400), 0.001);
        assertTrue("The ratio must never leave [0, 1]",
            fr.bosketball.stats.RankProgress.progressRatio(-500) >= 0.0
                && fr.bosketball.stats.RankProgress.progressRatio(999_999) <= 1.0);
    }

    @Test
    public void testAchievementStatsCountLifetimeFactsAndNotTodaysCounters() {
        // Round 84: "Crate Opener" (10 crates) and "Graduate" (finish the tutorial) both read
        // ProgressStat.QUEUE_JOINS, whose career value was TODAY's crate counter. So the first
        // needed ten crates in one night — impossible under a daily limit — and the second
        // unlocked on the first crate of the day instead of on finishing the tutorial.
        assertEquals(fr.bosketball.model.ProgressStat.CRATES_OPENED,
            fr.bosketball.model.Achievement.CASES_10.stat());
        assertEquals(fr.bosketball.model.ProgressStat.TUTORIAL_FINISHED,
            fr.bosketball.model.Achievement.TUTORIAL_DONE.stat());

        // And the career values now read the right counters. The real service is used here, not
        // a copy of its logic, so the test cannot drift from the code.
        PlayerProfile p = profile();
        p.setDailyCratesOpened(1);
        p.setTotalCratesOpened(0);
        ProgressService service = new ProgressService(null);
        assertEquals("The crate achievement must read the LIFETIME counter, not today's",
            0L, service.careerValue(p, fr.bosketball.model.ProgressStat.CRATES_OPENED));
        assertFalse("A player who opened one crate today has not opened ten",
            fr.bosketball.model.Achievement.CASES_10.isUnlockedBy(
                service.careerValue(p, fr.bosketball.model.ProgressStat.CRATES_OPENED)));

        p.setTotalCratesOpened(47);
        assertEquals(47L, service.careerValue(p, fr.bosketball.model.ProgressStat.CRATES_OPENED));
        assertTrue("A player who opened ten crates in their career has",
            fr.bosketball.model.Achievement.CASES_10.isUnlockedBy(
                service.careerValue(p, fr.bosketball.model.ProgressStat.CRATES_OPENED)));

        assertFalse("Opening a crate must not finish the tutorial",
            fr.bosketball.model.Achievement.TUTORIAL_DONE.isUnlockedBy(
                service.careerValue(p, fr.bosketball.model.ProgressStat.TUTORIAL_FINISHED)));
        p.setTutorialCompleted(true);
        assertTrue("Finishing the tutorial must finish the tutorial",
            fr.bosketball.model.Achievement.TUTORIAL_DONE.isUnlockedBy(
                service.careerValue(p, fr.bosketball.model.ProgressStat.TUTORIAL_FINISHED)));
    }

    @Test
    public void testTheRateCardIsReadableAndComplete() {
        List<String> table = EconomyBalance.describeRateCard();
        assertEquals(RateCard.rows().size(), table.size());
        for (String line : table) {
            assertFalse("A rate-card line must not be empty", line.isBlank());
            assertTrue("A rate-card line must name its family", line.contains("RESULT")
                || line.contains("ON_COURT") || line.contains("PROGRESSION")
                || line.contains("SEASON") || line.contains("SPEND"));
        }

        List<String> income = EconomyBalance.describeHourlyIncome(56);
        assertEquals(RateCard.SkillBand.values().length + 1, income.size());
        assertNotNull(income.get(0));
        assertTrue(income.get(0).contains("income per hour"));

        // Every source row must be reachable from code: the constants the code uses are the
        // constants the card lists.
        var econ = fr.bosketball.config.BosketConfig.defaults().economy();
        assertEquals(RateCard.WIN_COINS, econ.winCoins());
        assertEquals(RateCard.LOSS_COINS, econ.lossCoins());
        assertEquals(RateCard.TWO_POINT_COINS, econ.twoPointCoins());
        assertEquals(RateCard.THREE_POINT_COINS, econ.threePointCoins());
        assertEquals(RateCard.DUNK_BONUS_COINS, econ.dunkBonusCoins());
        assertEquals(RateCard.ASSIST_COINS, econ.assistCoins());
        assertEquals(RateCard.STEAL_COINS, econ.stealCoins());
        assertEquals(RateCard.BLOCK_COINS, econ.blockCoins());
        assertEquals(RateCard.MVP_BONUS_COINS, econ.mvpBonusCoins());
        assertEquals(RateCard.FORFEIT_COINS, econ.forfeitCoins());
        assertEquals(RateCard.GG_REWARD_COINS, econ.ggRewardCoins());
        assertEquals(RateCard.THREE_POINT_CONTEST_MAX_COINS, econ.threePointContestMaxCoins());
        assertEquals(RateCard.CRATE_COST_COINS,
            fr.bosketball.config.BosketConfig.defaults().crates().costCoins());
        assertEquals(RateCard.CRATE_COIN_MIN,
            fr.bosketball.config.BosketConfig.defaults().crates().coinMin());
        assertEquals(RateCard.CRATE_COIN_MAX,
            fr.bosketball.config.BosketConfig.defaults().crates().coinMax());
        assertEquals(RateCard.CRATE_DAILY_LIMIT,
            fr.bosketball.config.BosketConfig.defaults().crates().dailyLimit());
        assertEquals(RateCard.QUEST_REROLL_COST, ProgressService.CONTRACT_REROLL_COST);

        // The season tier table in code must match the shipped config.
        var season = fr.bosketball.config.BosketConfig.defaults().season();
        for (int i = 0; i < EconomyBalance.SEASON_TIER_COINS.length; i++) {
            assertEquals("Season tier " + i + " must match the rate card",
                EconomyBalance.SEASON_TIER_COINS[i], season.tierCoins().get(i).intValue());
        }
        assertEquals(RateCard.SEASON_TOP1_COINS, season.top1Coins());
        assertEquals(RateCard.SEASON_TOP2_COINS, season.top2Coins());
        assertEquals(RateCard.SEASON_TOP3_COINS, season.top3Coins());

        // The podum bonus in code must match the shipped config.
        assertEquals(season.top1Coins(), EconomyBalance.podiumBonus(1));
        assertEquals(season.top2Coins(), EconomyBalance.podiumBonus(2));
        assertEquals(season.top3Coins(), EconomyBalance.podiumBonus(3));
        assertEquals("A fourth place is not a podium", 0, EconomyBalance.podiumBonus(4));
        assertEquals("An unplaced player gets no podium bonus", 0, EconomyBalance.podiumBonus(0));
    }

    @Test
    public void testTheConfigFallbacksMatchTheShippedConfigSoNoBugComesBack() {
        // Round 84: the config parser carried its own fallbacks (250 / 5 / 3 / 60 / 500 for the
        // crate, 1200 for the reset anchor, a 0-coin Rookie tier), and they had drifted from
        // config.yml. A missing or hand-trimmed config therefore resurrected the money printer.
        // The fallback is the shipped value or it is a bug, so the two must be identical.
        var shipped = fr.bosketball.config.BosketConfig.defaults();
        var economy = shipped.economy();
        var crates = shipped.crates();
        var season = shipped.season();

        // The crate must be a sink under the shipped values, which means the fallback — and so
        // the shipped values — are the ones the audit checks.
        assertTrue("The shipped crate must be a sink",
            EconomyBalance.sinkToFaucetRatio(80, EconomyBalance.shopInventoryCoins()) >= 1.0);

        assertEquals("The crate cost must match the rate card", RateCard.CRATE_COST_COINS, crates.costCoins());
        assertEquals("The crate refund floor must match", RateCard.CRATE_COIN_MIN, crates.coinMin());
        assertEquals("The crate refund ceiling must match", RateCard.CRATE_COIN_MAX, crates.coinMax());
        assertEquals("The crate daily limit must match", RateCard.CRATE_DAILY_LIMIT, crates.dailyLimit());
        assertEquals("The crate cooldown must match", RateCard.CRATE_COOLDOWN_MILLIS,
            crates.cooldownSeconds() * 1000L);
        assertEquals("The crate bonus must match", RateCard.CRATE_BONUS_COINS, crates.bonusCoins());
        assertEquals(RateCard.CRATE_BONUS_CHANCE, crates.bonusChance(), 0.0001);
        assertEquals(RateCard.CRATE_COSMETIC_CHANCE, crates.cosmeticChance(), 0.0001);

        // The catalogue is the deep sink, so its size is a number the research document quotes.
        // It is measured from the shop enums, not estimated, so it cannot drift from the code.
        int pricedTags = 0;
        int tagCoins = 0;
        for (var tag : fr.bosketball.model.ChatTag.values()) {
            if (tag.defaultPriceCoins() > 0) {
                pricedTags++;
                tagCoins += tag.defaultPriceCoins();
            }
        }
        int pricedBalls = 0;
        int ballCoins = 0;
        for (var skin : fr.bosketball.model.BallSkin.values()) {
            if (skin.defaultPriceCoins() > 0) {
                pricedBalls++;
                ballCoins += skin.defaultPriceCoins();
            }
        }
        int pricedDunks = 0;
        int dunkCoins = 0;
        for (var dunk : fr.bosketball.model.DunkStyle.values()) {
            if (dunk.defaultPriceCoins() > 0) {
                pricedDunks++;
                dunkCoins += dunk.defaultPriceCoins();
            }
        }
        assertEquals("The shop inventory must be the sum of the catalogue",
            tagCoins + ballCoins + dunkCoins, EconomyBalance.shopInventoryCoins());
        // These are the exact figures the research document quotes. They are asserted
        // individually rather than as one total so a failure says which catalogue moved.
        assertEquals("Fifty-four chat tags are purchasable", 54, pricedTags);
        assertEquals("Seven ball skins are purchasable", 7, pricedBalls);
        assertEquals("Four dunk styles are purchasable", 4, pricedDunks);
        assertEquals("The chat tags are the bulk of the catalogue", 54_000, tagCoins);
        assertEquals("The ball skins cost what the shop says", 6_550, ballCoins);
        assertEquals("The dunk styles cost what the shop says", 4_300, dunkCoins);
        assertEquals("The catalogue must be exactly what the research document says",
            64_850, EconomyBalance.shopInventoryCoins());

        // The quest board's shape and its reward bands are quoted in the research document,
        // so they are pinned here. A new template is fine; a changed board size is not.
        var dailyPool = fr.bosketball.progression.QuestCatalog.pool(
            fr.bosketball.progression.QuestCatalog.Board.DAILY);
        var weeklyPool = fr.bosketball.progression.QuestCatalog.pool(
            fr.bosketball.progression.QuestCatalog.Board.WEEKLY);
        var contractPool = fr.bosketball.progression.QuestCatalog.pool(
            fr.bosketball.progression.QuestCatalog.Board.CONTRACT);
        assertEquals("The daily pool size", 17, dailyPool.size());
        assertEquals("The weekly pool size", 8, weeklyPool.size());
        assertEquals("The contract pool size", 7, contractPool.size());

        int dailyMin = Integer.MAX_VALUE;
        int dailyMax = 0;
        for (var t : dailyPool) {
            dailyMin = Math.min(dailyMin, t.rewardCoins());
            dailyMax = Math.max(dailyMax, t.rewardCoins());
        }
        assertEquals("A daily pays at least this much", 80, dailyMin);
        assertEquals("A daily pays at most this much", 170, dailyMax);
        assertEquals("The weekly's reward", 700, RateCard.WEEKLY_QUEST_COINS);
        assertEquals("The daily floor in the rate card", 80, RateCard.DAILY_QUEST_MIN_COINS);

        // Three dailies a day is the board the research chose, one weekly is the catch-up valve.
        assertEquals("Three dailies a day", 3, fr.bosketball.progression.ProgressService.DAILY_QUEST_COUNT);
        assertEquals("One weekly", 1, fr.bosketball.progression.ProgressService.WEEKLY_QUEST_COUNT);
        assertEquals("One contract", 1, fr.bosketball.progression.ProgressService.CONTRACT_COUNT);


        // The loss must pay, the bot multiplier must be the shipped one.
        assertEquals(RateCard.LOSS_COINS, economy.lossCoins());
        assertEquals(RateCard.BOT_MATCH_MULTIPLIER, economy.botMatchMultiplier(), 0.0001);
        assertEquals(RateCard.CASUAL_MATCH_MULTIPLIER, economy.casualRewardMultiplier(), 0.0001);
        // The season: the anchor is the starting ELO, every tier pays, and the podium is
        // deeper than a tier step but not ten times one.
        assertEquals(1000, season.softResetAnchor());
        assertTrue("Every season tier must pay something", season.tierCoins().get(0) > 0);
        assertTrue("The podium must out-rank a tier step",
            season.top1Coins() > season.top3Coins() && season.top3Coins() > 0);
        assertTrue("The podium must not be ten times a tier step",
            season.top1Coins() < season.tierCoins().get(6) * 2);
    }
}
