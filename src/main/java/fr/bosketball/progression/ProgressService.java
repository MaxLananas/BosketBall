package fr.bosketball.progression;

import fr.bosketball.util.Palette;
import fr.bosketball.model.Achievement;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerQuest;
import fr.bosketball.model.ProgressStat;
import fr.bosketball.progression.QuestCatalog.Board;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public final class ProgressService {

    public static final int DAILY_QUEST_COUNT = 3;
    /**
     * One weekly quest, not three.
     *
     * <p>Round 84: the board shipped three weeklies on top of three dailies and two contracts —
     * eight repeatable claims per cycle. The time-gating research is unambiguous that the daily
     * set has to be completable in one sitting, and eight claims is not one sitting. The weekly is
     * the catch-up valve for a player who missed a Tuesday, and one is enough.
     */
    public static final int WEEKLY_QUEST_COUNT = 1;
    /**
     * One sprint, not two.
     *
     * <p>The sprint is the burst end of the ladder — one hard objective on a six-hour clock. Two
     * of them was a second daily board wearing a different hat.
     */
    public static final int CONTRACT_COUNT = 1;
    public static final long DAILY_BOARD_MILLIS = 24L * 60L * 60L * 1000L;
    public static final long WEEKLY_BOARD_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    public static final long CONTRACT_BOARD_MILLIS = 6L * 60L * 60L * 1000L;
    public static final long CONTRACT_REROLL_COST = 75L;

    /** Fallback used when the config lists no streak reward (kept in sync with config.yml). */
    private static final java.util.List<Integer> DEFAULT_STREAK_REWARDS =
        java.util.List.of(40, 55, 70, 90, 110, 140, 200);

    private final StatsManager statsManager;

    /**
     * The ledger that makes a quest payout happen once.
     *
     * <p>Round 84: the {@code claimed} flag on a quest is persisted, but it lives on an object
     * that gets replaced whenever the board rolls over, and a corrupted profile can carry a
     * completed quest with the flag back at false. Both claim paths — the "Claim all" button and
     * the per-quest click — now go through this ledger, so neither can pay twice.
     */
    private final QuestRewardGuard questRewards = new QuestRewardGuard();

    public ProgressService(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    /** The ledger that makes a quest payout happen once. */
    public QuestRewardGuard questRewardGuard() {
        return questRewards;
    }

    public boolean ensureBoards(PlayerProfile profile, long nowMillis) {
        if (profile == null) {
            return false;
        }
        boolean changed = false;
        long seedBase = profile.uuid().getMostSignificantBits() ^ profile.uuid().getLeastSignificantBits();

        if (profile.dailyQuests().isEmpty()
            || nowMillis - profile.dailyBoardRefreshMillis() >= DAILY_BOARD_MILLIS
            || profile.dailyQuests().stream().allMatch(q -> q.isExpired(nowMillis))) {
            long period = nowMillis / DAILY_BOARD_MILLIS;
            profile.replaceQuestBoard(
                profile.dailyQuests(),
                QuestCatalog.roll(Board.DAILY, DAILY_QUEST_COUNT,
                    seedBase * 31L + period, nowMillis, nowMillis + DAILY_BOARD_MILLIS)
            );
            profile.setDailyBoardRefreshMillis(nowMillis);
            changed = true;
        }

        if (profile.weeklyQuests().isEmpty()
            || nowMillis - profile.weeklyBoardRefreshMillis() >= WEEKLY_BOARD_MILLIS
            || profile.weeklyQuests().stream().allMatch(q -> q.isExpired(nowMillis))) {
            long period = nowMillis / WEEKLY_BOARD_MILLIS;
            profile.replaceQuestBoard(
                profile.weeklyQuests(),
                QuestCatalog.roll(Board.WEEKLY, WEEKLY_QUEST_COUNT,
                    seedBase * 17L + period, nowMillis, nowMillis + WEEKLY_BOARD_MILLIS)
            );
            profile.setWeeklyBoardRefreshMillis(nowMillis);
            changed = true;
        }

        if (profile.contracts().isEmpty()
            || nowMillis - profile.contractBoardRefreshMillis() >= CONTRACT_BOARD_MILLIS) {
            long period = nowMillis / CONTRACT_BOARD_MILLIS;
            profile.replaceQuestBoard(
                profile.contracts(),
                QuestCatalog.roll(Board.CONTRACT, CONTRACT_COUNT,
                    seedBase * 13L + period, nowMillis, nowMillis + CONTRACT_BOARD_MILLIS)
            );
            profile.setContractBoardRefreshMillis(nowMillis);
            changed = true;
        }

        if (changed) {
            statsManager.markDirty(profile.uuid());
        }
        return changed;
    }

    public PlayerQuest rerollDailyQuest(PlayerProfile profile, long nowMillis) {
        if (profile == null) {
            return null;
        }
        ensureBoards(profile, nowMillis);
        int index = -1;
        for (int i = 0; i < profile.dailyQuests().size(); i++) {
            PlayerQuest quest = profile.dailyQuests().get(i);
            if (!quest.isComplete()) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return null;
        }
        if (!profile.spendCoins((int) CONTRACT_REROLL_COST)) {
            return null;
        }
        Set<String> existing = new LinkedHashSet<>();
        for (PlayerQuest q : profile.dailyQuests()) {
            existing.add(q.id());
        }
        List<PlayerQuest> rolled = QuestCatalog.roll(
            QuestCatalog.Board.DAILY,
            Math.max(1, DAILY_QUEST_COUNT),
            System.nanoTime() ^ profile.uuid().getMostSignificantBits(),
            nowMillis,
            nowMillis + DAILY_BOARD_MILLIS
        );
        PlayerQuest fresh = null;
        for (PlayerQuest candidate : rolled) {
            if (!existing.contains(candidate.id())) {
                fresh = candidate;
                break;
            }
        }
        if (fresh == null) {
            return null;
        }
        profile.dailyQuests().set(index, fresh);
        statsManager.markDirty(profile.uuid());
        return fresh;
    }

    public PlayerQuest rerollContract(PlayerProfile profile, long nowMillis) {
        if (profile == null) {
            return null;
        }
        ensureBoards(profile, nowMillis);
        if (!profile.spendCoins((int) CONTRACT_REROLL_COST)) {
            return null;
        }
        Set<String> existing = new LinkedHashSet<>();
        for (PlayerQuest q : profile.contracts()) {
            existing.add(q.id());
        }
        PlayerQuest fresh = QuestCatalog.rerollContract(
            existing,
            System.nanoTime() ^ profile.uuid().getLeastSignificantBits(),
            nowMillis,
            nowMillis + CONTRACT_BOARD_MILLIS
        );
        if (!profile.contracts().isEmpty()) {
            profile.contracts().set(0, fresh);
        } else {
            profile.contracts().add(fresh);
        }
        statsManager.markDirty(profile.uuid());
        return fresh;
    }

    public void record(PlayerProfile profile, ProgressStat stat, int amount) {
        if (profile == null || stat == null || amount <= 0) {
            return;
        }
        ensureBoards(profile, System.currentTimeMillis());
        boolean touched = false;
        for (PlayerQuest quest : profile.allActiveQuests()) {
            if (quest.stat() == stat && !quest.isComplete()) {
                quest.addProgress(amount);
                touched = true;
                if (quest.isComplete()) {
                    notifyQuestComplete(profile, quest);
                }
            }
        }
        if (touched) {
            statsManager.markDirty(profile.uuid());
        }
        evaluateAchievements(profile);
    }

    public long careerValue(PlayerProfile p, ProgressStat stat) {
        return switch (stat) {
            case MATCHES_PLAYED -> (long) p.wins() + p.losses();
            case MATCHES_WON -> p.wins();
            case POINTS_SCORED -> p.points();
            case TWO_POINTERS -> p.twoPointersMade();
            case THREE_POINTERS -> p.threePointersMade();
            case DUNKS -> p.dunks();
            case ASSISTS -> p.assists();
            case REBOUNDS -> p.rebounds();
            case STEALS -> p.steals();
            case BLOCKS -> p.blocks();
            case ANKLE_BREAKERS -> p.ankleBreakers();
            case MVPS -> p.mvps();
            case COINS_EARNED -> p.totalCoinsEarned();
            case THREE_POINT_CONTESTS -> p.threePointContestBest() > 0 ? 1 : 0;
            case THREE_POINT_CONTEST_SCORE -> p.threePointContestBest();

            case QUEUE_JOINS -> p.dailyCratesOpened();
            case CRATES_OPENED -> p.totalCratesOpened();
            case TUTORIAL_FINISHED -> p.tutorialCompleted() ? 1 : 0;
            case FRIENDS_ADDED -> p.friends().size();
            case PARTY_MATCHES -> p.partyMatches();

            case CASUAL_MATCHES -> p.casualMatches();
            case RANKED_MATCHES -> p.rankedMatches();
            case WIN_STREAK_BEST -> p.bestWinStreak();
        };
    }

    public List<Achievement> evaluateAchievements(PlayerProfile profile) {
        return evaluateAchievements(profile, false);
    }

    public List<Achievement> evaluateAchievements(PlayerProfile profile, boolean force) {
        if (profile == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        if (!force && now - profile.achievementEvaluationLockMillis() < 2_000L) {
            return List.of();
        }
        profile.setAchievementEvaluationLockMillis(now);

        List<Achievement> freshlyUnlocked = new ArrayList<>();
        for (Achievement a : Achievement.values()) {
            if (profile.hasAchievement(a)) {
                continue;
            }
            if (a.isUnlockedBy(careerValue(profile, a.stat()))) {
                profile.unlockAchievement(a);
                if (a.rewardCoins() > 0) {
                    profile.addEarnedCoins(a.rewardCoins());
                }
                freshlyUnlocked.add(a);
            }
        }
        if (!freshlyUnlocked.isEmpty()) {
            statsManager.markDirty(profile.uuid());
            Player online = Bukkit.getPlayer(profile.uuid());
            if (online != null) {
                for (Achievement a : freshlyUnlocked) {
                    announceAchievement(online, a);
                }
            }
        }
        return freshlyUnlocked;
    }

    /**
     * Claims every completed quest the player has.
     *
     * <p>Round 84: this used to add the coins itself, in a loop, guarded only by the quest's
     * {@code claimed} flag. Both this path and the per-quest click in the GUI pay the same quest,
     * and the flag is the only thing keeping them apart. The payout now goes through
     * {@link QuestRewardGuard}, so a quest pays once even if the flag is lost.
     *
     * @return the coins paid
     */
    public int claimCompletedQuests(Player player, PlayerProfile profile) {
        if (player == null || profile == null) {
            return 0;
        }
        int coins = 0;
        boolean paidAnything = false;
        List<String> titles = new ArrayList<>();
        for (PlayerQuest quest : profile.allActiveQuests()) {
            if (quest.isComplete() && !quest.claimed()) {
                // The ledger pays the coins AND the XP, and pays neither a second time. It
                // returns 0 when this quest was already paid, which is the case the ledger
                // exists for.
                int paid = questRewards.pay(profile, quest);
                if (paid <= 0 && !questRewards.hasPaid(profile, quest)) {
                    // A quest with no reward at all: nothing to pay, nothing to announce.
                    continue;
                }
                quest.setClaimed(true);
                quest.setRewarded(true);
                coins += paid;
                paidAnything = true;
                titles.add(quest.title());
            }
        }
        if (paidAnything) {
            statsManager.markDirty(profile.uuid());
            statsManager.saveProfileAsync(profile);
            Msg.playPersonalPackSound(player, "ui.click", 1.0f, 1.45f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 30, 0.4, 0.5, 0.4, 0.15);
        }
        return coins;
    }

    /**
     * Claims one quest.
     *
     * <p>The single-quest path the quest board's per-item click uses. It shares
     * {@link QuestRewardGuard} with {@link #claimCompletedQuests(Player, PlayerProfile)}, so
     * clicking a quest that "Claim all" already paid pays nothing, and clicking the same quest
     * twice pays nothing.
     *
     * @return the coins paid — {@code 0} when the quest was incomplete or already paid
     */
    public int claimQuest(PlayerProfile profile, PlayerQuest quest) {
        if (profile == null || quest == null) {
            return 0;
        }
        if (!quest.isComplete() || quest.claimed()) {
            return 0;
        }
        int paid = questRewards.pay(profile, quest);
        if (paid <= 0 && !questRewards.hasPaid(profile, quest)) {
            // Nothing to pay and nothing was ever paid for this quest — leave it open rather
            // than marking it claimed, so it does not disappear from the board for no reason.
            return 0;
        }
        quest.setClaimed(true);
        quest.setRewarded(true);
        statsManager.markDirty(profile.uuid());
        return paid;
    }

    /**
     * Round 33 — this method existed, was fully implemented… and was never called by anybody, so the
     * {@code progression.daily-login} block of config.yml (and the persisted streak counter on every
     * profile) did nothing. It is now wired to the join in {@code GameplayListener}, and it reads the
     * configured streak rewards instead of a hardcoded array that silently ignored them.
     *
     * @return the coins granted, or 0 when the streak was already claimed today
     */
    public int claimDailyLogin(Player player, PlayerProfile profile, java.util.List<Integer> rewards) {
        if (player == null || profile == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long day = now / DAILY_BOARD_MILLIS;
        long lastDay = profile.lastDailyStreakClaimMillis() / DAILY_BOARD_MILLIS;
        if (profile.lastDailyStreakClaimMillis() > 0 && lastDay == day) {
            return 0;
        }
        boolean consecutive = profile.lastDailyStreakClaimMillis() > 0 && day - lastDay == 1;
        int streak = consecutive ? Math.min(7, profile.dailyLoginStreak() + 1) : 1;
        profile.setDailyLoginStreak(streak);
        profile.setLastDailyStreakClaimMillis(now);

        java.util.List<Integer> table = rewards == null || rewards.isEmpty()
            ? DEFAULT_STREAK_REWARDS : rewards;
        int reward = Math.max(0, table.get((streak - 1) % table.size()));
        profile.addEarnedCoins(reward);
        profile.addXp(reward / 2);
        statsManager.markDirty(profile.uuid());
        statsManager.saveProfileAsync(profile);

        Msg.send(player, Msg.success("Daily login <#F8FAFC><bold>Day " + streak + "</bold></#F8FAFC> "
            + Msg.coinsBadge(reward)));
        if (streak >= 7) {
            Msg.send(player, "<" + Palette.GOLD_SOFT + "><bold>▪ PERFECT WEEK!</bold></" + Palette.GOLD_SOFT + "> <#94A3B8>Your streak is maxed — the "
                + "<" + Palette.GOLD_SOFT + ">" + reward + " Coin</" + Palette.GOLD_SOFT + "> jackpot is yours every 7 days.</#94A3B8>");
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.35f);
        return reward;
    }

    private void notifyQuestComplete(PlayerProfile profile, PlayerQuest quest) {
        Player player = Bukkit.getPlayer(profile.uuid());
        if (player == null) {
            return;
        }

        if (profile.settings() != null && !profile.settings().showQuestNotifications()) {
            return;
        }
        Msg.rail(player, fr.bosketball.util.HudSurface.PROGRESS,
            "<" + Palette.SUCCESS + "><bold>Quest done</bold></" + Palette.SUCCESS + ">"
                + fr.bosketball.util.HudCopy.SEP + "<" + Palette.TEXT + ">" + quest.title() + "</"
                + Palette.TEXT + ">" + fr.bosketball.util.HudCopy.SEP
                + Msg.coinsBadge(quest.rewardCoins()));
        Msg.playPersonalPackSound(player, "game.green_release", 0.85f, 1.25f);
    }

    private void announceAchievement(Player player, Achievement achievement) {
        Msg.playPersonalPackSound(player, "crowd.cheer", 0.9f, 1.2f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.4, 0), 25, 0.4, 0.5, 0.4, 0.2);
        Msg.send(player, "<" + Palette.GOLD_SOFT + "><bold> ACHIEVEMENT UNLOCKED</bold></" + Palette.GOLD_SOFT + "> <#64748B>\u00b7</#64748B> <#F8FAFC><bold>"
            + achievement.displayName() + "</bold></#F8FAFC> <#94A3B8>(" + achievement.category().title() + ")</#94A3B8> "
            + Msg.coinsBadge(achievement.rewardCoins()));
        Msg.title(
            player,
            Msg.gradBold(Msg.GRAD_GOLD, " " + achievement.displayName()),
            "<#94A3B8>" + achievement.description() + "</#94A3B8>",
            150, 1800, 400
        );
    }

    public static Component pipMeter(double ratio, int slots) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * slots);
        TextColor color = ratio >= 1.0 ? TextColor.color(0x4ADE80) : TextColor.color(0xFBBF24);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            sb.append(i < filled ? '▰' : '▱');
        }
        return Component.text(sb.toString(), color).decoration(TextDecoration.BOLD, false);
    }

}
