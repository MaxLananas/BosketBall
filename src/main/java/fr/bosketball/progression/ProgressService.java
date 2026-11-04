package fr.bosketball.progression;

import fr.bosketball.model.Achievement;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerQuest;
import fr.bosketball.model.ProgressStat;
import fr.bosketball.progression.QuestCatalog.Board;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Central progression engine: rotating quest boards, achievement evaluation, daily login
 * streaks and reward claiming.
 *
 * <p>Gameplay code only ever calls {@link #record(PlayerProfile, ProgressStat, int)}; the
 * service fans the delta out to every active quest and re-evaluates the achievement
 * catalogue against career statistics. Everything is idempotent, so replays, double
 * callbacks or a server restart can never duplicate a reward.</p>
 */
public final class ProgressService {

    public static final int DAILY_QUEST_COUNT = 3;
    public static final int WEEKLY_QUEST_COUNT = 3;
    public static final int CONTRACT_COUNT = 2;
    public static final long DAILY_BOARD_MILLIS = 24L * 60L * 60L * 1000L;
    public static final long WEEKLY_BOARD_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    public static final long CONTRACT_BOARD_MILLIS = 6L * 60L * 60L * 1000L;
    public static final long CONTRACT_REROLL_COST = 75L;

    /** Daily login streak rewards for day 1..7 (then it loops). */
    private static final int[] DAILY_STREAK_REWARDS = {40, 55, 70, 90, 110, 140, 200};

    private final StatsManager statsManager;

    public ProgressService(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    // =========================================================================
    //  BOARD ROTATION
    // =========================================================================

    /**
     * Ensures the player's daily / weekly / contract boards are current, rolling freshly
     * seeded ones whenever a period boundary has passed. Safe to call on every join &amp; tick.
     */
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

    /** Rerolls one contract slot, charging Coins. Returns the new quest or {@code null}. */
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

    // =========================================================================
    //  PROGRESS DISPATCH
    // =========================================================================

    /**
     * Records a gameplay delta for a player: advances every matching quest then re-evaluates
     * achievements. Never throws, never blocks; safe from any thread that already owns the
     * profile (main thread).
     */
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

    /** Career-stat projection used as the source of truth for achievement thresholds. */
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
            // QUEUE_JOINS is the crate-opener counter (its only writer records it when a crate is
            // opened) and PARTY_MATCHES used to read bestWinStreak(), so "Chemistry: play 10
            // matches with a Party" unlocked on a solo win streak instead.
            case QUEUE_JOINS -> p.dailyCratesOpened();
            case FRIENDS_ADDED -> p.friends().size();
            case PARTY_MATCHES -> p.partyMatches();
            // These two both returned wins+losses, so a casual player completed "Ranked Grinder".
            case CASUAL_MATCHES -> p.casualMatches();
            case RANKED_MATCHES -> p.rankedMatches();
            case WIN_STREAK_BEST -> p.bestWinStreak();
        };
    }

    /**
     * Re-evaluates the whole achievement catalogue against career statistics and unlocks
     * anything newly earned. Rate-limited to once every 2 seconds per profile to stay
     * cheap inside hot gameplay loops (markDirty still records the stat changes).
     */
    public List<Achievement> evaluateAchievements(PlayerProfile profile) {
        return evaluateAchievements(profile, false);
    }

    /**
     * @param force bypass the 2 s rate limit. Needed at the END of a match: the final stat writes
     *              happen milliseconds after the in-play writes, so the (rate-limited) call was
     *              dropped and the achievement only appeared on the player's *next* session.
     */
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

    // =========================================================================
    //  CLAIMING
    // =========================================================================

    /** Claims every completed, unclaimed quest. Returns the Coins awarded. */
    public int claimCompletedQuests(Player player, PlayerProfile profile) {
        if (player == null || profile == null) {
            return 0;
        }
        int coins = 0;
        int xp = 0;
        List<String> titles = new ArrayList<>();
        for (PlayerQuest quest : profile.allActiveQuests()) {
            if (quest.isComplete() && !quest.claimed()) {
                quest.setClaimed(true);
                quest.setRewarded(true);
                coins += quest.rewardCoins();
                xp += quest.rewardXp();
                titles.add(quest.title());
            }
        }
        if (coins > 0 || xp > 0) {
            profile.addEarnedCoins(coins);
            profile.addXp(xp);
            statsManager.markDirty(profile.uuid());
            statsManager.saveProfileAsync(profile);
            Msg.playPersonalPackSound(player, "ui.click", 1.0f, 1.45f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 30, 0.4, 0.5, 0.4, 0.15);
        }
        return coins;
    }

    /** Number of completed-but-unclaimed quests (drives the lobby badge). */
    public int claimableCount(PlayerProfile profile) {
        return profile == null ? 0 : profile.unclaimedQuestCount();
    }

    // =========================================================================
    //  DAILY LOGIN STREAK
    // =========================================================================

    /**
     * Grants the daily login reward once per UTC day, keeping a 7-day rolling streak.
     * Returns the coins granted (0 when nothing was due).
     */
    public int claimDailyLogin(Player player, PlayerProfile profile) {
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

        int reward = DAILY_STREAK_REWARDS[(streak - 1) % DAILY_STREAK_REWARDS.length];
        profile.addEarnedCoins(reward);
        profile.addXp(reward / 2);
        statsManager.markDirty(profile.uuid());
        statsManager.saveProfileAsync(profile);

        Msg.send(player, Msg.success("Daily login <white><bold>Day " + streak + "</bold></white> "
            + Msg.coinsBadge(reward)));
        if (streak >= 7) {
            Msg.send(player, "<#FDE047><bold>✦ PERFECT WEEK!</bold></#FDE047> <gray>Your streak is maxed — keep it rolling for the "
                + "<#FDE047>200 Coin</#FDE047> jackpot every 7 days.</gray>");
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.35f);
        return reward;
    }

    // =========================================================================
    //  NOTIFICATIONS
    // =========================================================================

    private void notifyQuestComplete(PlayerProfile profile, PlayerQuest quest) {
        Player player = Bukkit.getPlayer(profile.uuid());
        if (player == null) {
            return;
        }
        // "Quest Notifications" preference: persisted since day one but never consulted.
        if (profile.settings() != null && !profile.settings().showQuestNotifications()) {
            return;
        }
        player.sendActionBar(Msg.parse(
            "<#4ADE80><bold>✔ QUEST COMPLETE</bold></#4ADE80> <dark_gray>•</dark_gray> <white>"
                + quest.title() + "</white> <gray>—</gray> " + Msg.coinsBadge(quest.rewardCoins())
        ));
        Msg.playPersonalPackSound(player, "game.green_release", 0.85f, 1.25f);
    }

    private void announceAchievement(Player player, Achievement achievement) {
        Msg.playPersonalPackSound(player, "crowd.cheer", 0.9f, 1.2f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.4, 0), 25, 0.4, 0.5, 0.4, 0.2);
        Msg.send(player, "<#FDE047><bold>🏆 ACHIEVEMENT UNLOCKED</bold></#FDE047> <dark_gray>•</dark_gray> <white><bold>"
            + achievement.displayName() + "</bold></white> <gray>(" + achievement.category().title() + ")</gray> "
            + Msg.coinsBadge(achievement.rewardCoins()));
        Msg.title(
            player,
            "<#FDE047><bold>🏆 " + achievement.displayName() + "</bold></#FDE047>",
            "<gray>" + achievement.description() + "</gray>",
            150, 1800, 400
        );
    }

    /** Small coloured component used by GUIs to render the achievement pip meter. */
    public static Component pipMeter(double ratio, int slots) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * slots);
        TextColor color = ratio >= 1.0 ? TextColor.color(0x4ADE80) : TextColor.color(0xFBBF24);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            sb.append(i < filled ? '▰' : '▱');
        }
        return Component.text(sb.toString(), color).decoration(TextDecoration.BOLD, false);
    }

    /** Quick lookup of every stat a profile currently satisfies (used by the admin panel). */
    public Map<ProgressStat, Long> careerSnapshot(PlayerProfile profile) {
        Map<ProgressStat, Long> out = new EnumMap<>(ProgressStat.class);
        for (ProgressStat stat : ProgressStat.values()) {
            out.put(stat, careerValue(profile, stat));
        }
        return out;
    }

    public Set<Achievement> nextAchievements(PlayerProfile profile, int limit) {
        List<Achievement> locked = new ArrayList<>();
        for (Achievement a : Achievement.values()) {
            if (!profile.hasAchievement(a)) {
                locked.add(a);
            }
        }
        locked.sort((a, b) -> Double.compare(
            b.completionRatio(careerValue(profile, b.stat())),
            a.completionRatio(careerValue(profile, a.stat()))
        ));
        return new LinkedHashSet<>(locked.subList(0, Math.min(limit, locked.size())));
    }

    /** Exposed for unit tests / diagnostics. */
    public UUID sampleUuid(UUID uuid) {
        return uuid;
    }
}
