package fr.bosketball.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerProfile {

    private static final int MAX_HISTORY = 15;
    public static final int MAX_EQUIPPED_TAGS = 1;

    private final UUID uuid;
    private String lastKnownName;

    private volatile String skinTextureValue;
    private volatile String skinTextureSignature;
    private volatile long skinTextureUpdatedAtMillis;

    private int points;
    private int twoPointersMade;
    private int twoPointersAttempted;
    private int threePointersMade;
    private int threePointersAttempted;
    private int dunks;
    private int assists;
    private int rebounds;
    private int steals;
    private int blocks;
    private int ankleBreakers;
    private int wins;
    private int losses;
    private int mvps;
    private int threePointContestBest;

    private int elo = 1000;
    private int coins = 100;
    private int xp = 0;
    private int winStreak = 0;
    private int bestWinStreak = 0;

    private Rank staffRankOverride = null;
    private BallSkin selectedBallSkin = BallSkin.CLASSIC;
    private DunkStyle selectedDunkStyle = DunkStyle.TOMAHAWK;
    private final List<ChatTag> equippedTags = new ArrayList<>();
    private final EnumSet<ChatTag> unlockedTags = EnumSet.noneOf(ChatTag.class);
    private final EnumSet<BallSkin> unlockedBalls = EnumSet.of(BallSkin.CLASSIC);
    private final EnumSet<DunkStyle> unlockedDunks = EnumSet.of(DunkStyle.TOMAHAWK);

    private long playTimeSeconds = 0L;
    private int unreadMailCount = 0;
    private final PlayerSettings settings = new PlayerSettings();
    private final Set<UUID> friends = new LinkedHashSet<>();
    private final Set<UUID> ignoredPlayers = new LinkedHashSet<>();
    private final List<MatchHistoryEntry> matchHistory = new ArrayList<>();

    private boolean tutorialCompleted = false;
    private int tutorialStep = 0;
    private final List<PlayerQuest> dailyQuests = new ArrayList<>();
    private final List<PlayerQuest> weeklyQuests = new ArrayList<>();
    private final List<PlayerQuest> contracts = new ArrayList<>();
    private long dailyBoardRefreshMillis = 0L;
    private long weeklyBoardRefreshMillis = 0L;
    private long contractBoardRefreshMillis = 0L;
    private final java.util.EnumSet<Achievement> unlockedAchievements = java.util.EnumSet.noneOf(Achievement.class);
    private long achievementEvaluationLockMillis = 0L;
    private int dailyLoginStreak = 0;
    private long lastDailyStreakClaimMillis = 0L;
    private long totalCoinsEarned = 0L;

    private int chatWarnPoints = 0;
    private long chatMuteUntilMillis = 0L;
    private int dailyCratesOpened = 0;
    private long crateDayEpoch = 0L;
    private String guildId = null;
    private boolean partyChatEnabled = false;
    private long matchmakingBanUntilMillis = 0L;
    private int dodgeCount = 0;

    public PlayerProfile(UUID uuid, String lastKnownName) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName == null ? "Player" : lastKnownName;
        syncExclusiveFounderState();
    }

    public UUID uuid() {
        return uuid;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String name) {
        if (name != null && !name.isBlank()) {
            this.lastKnownName = name;
            syncExclusiveFounderState();
        }
    }

    public String skinTextureValue() {
        return skinTextureValue;
    }

    public String skinTextureSignature() {
        return skinTextureSignature;
    }

    public long skinTextureUpdatedAtMillis() {
        return skinTextureUpdatedAtMillis;
    }

    public boolean hasSkinTexture() {
        return skinTextureValue != null && !skinTextureValue.isBlank();
    }

    public void setSkinTexture(String value, String signature, long updatedAtMillis) {
        if (value == null || value.isBlank() || value.length() > Short.MAX_VALUE) {
            this.skinTextureValue = null;
            this.skinTextureSignature = null;
            this.skinTextureUpdatedAtMillis = 0L;
            return;
        }
        this.skinTextureValue = value;
        this.skinTextureSignature = signature == null || signature.length() <= 1024 ? signature : null;
        this.skinTextureUpdatedAtMillis = Math.max(0L, updatedAtMillis);
    }

    public boolean updateSkinTexture(String value, String signature) {
        if (value == null || value.isBlank() || value.length() > Short.MAX_VALUE) {
            return false;
        }
        String safeSignature = signature == null || signature.length() <= 1024 ? signature : null;
        if (value.equals(skinTextureValue)) {

            if (safeSignature == null || safeSignature.equals(skinTextureSignature)) {
                return false;
            }
        }
        this.skinTextureValue = value;
        this.skinTextureSignature = safeSignature;
        this.skinTextureUpdatedAtMillis = System.currentTimeMillis();
        return true;
    }

    public void syncExclusiveFounderState() {
        if (ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(this.lastKnownName)) {
            unlockedTags.add(ChatTag.FOUNDER);
            unlockedTags.add(ChatTag.MYPINEAPPLE);
            if (equippedTags.isEmpty()) {
                equippedTags.add(ChatTag.FOUNDER);
            }
        } else {
            unlockedTags.remove(ChatTag.FOUNDER);
            equippedTags.remove(ChatTag.FOUNDER);
        }
    }

    public Rank effectiveRank() {
        if (staffRankOverride != null) {
            return staffRankOverride;
        }
        return Rank.fromElo(elo);
    }

    public Rank competitiveRank() {
        return Rank.fromElo(elo);
    }

    public Rank staffRankOverride() {
        return staffRankOverride;
    }

    public void setStaffRankOverride(Rank staffRankOverride) {
        this.staffRankOverride = staffRankOverride;
    }

    public int points() {
        return points;
    }

    public void addPoints(int pts) {
        if (pts > 0) {
            this.points = (int) Math.min(999_999_999L, (long) this.points + pts);
        }
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public int twoPointersMade() {
        return twoPointersMade;
    }

    public void setTwoPointersMade(int v) {
        this.twoPointersMade = Math.max(0, v);
    }

    public int twoPointersAttempted() {
        return twoPointersAttempted;
    }

    public void setTwoPointersAttempted(int v) {
        this.twoPointersAttempted = Math.max(0, v);
    }

    public int threePointersMade() {
        return threePointersMade;
    }

    public void setThreePointersMade(int v) {
        this.threePointersMade = Math.max(0, v);
    }

    public int threePointersAttempted() {
        return threePointersAttempted;
    }

    public void setThreePointersAttempted(int v) {
        this.threePointersAttempted = Math.max(0, v);
    }

    public void recordShot(boolean threePointer, boolean made) {
        if (threePointer) {
            threePointersAttempted++;
            if (made) {
                threePointersMade++;
                points += 3;
            }
        } else {
            twoPointersAttempted++;
            if (made) {
                twoPointersMade++;
                points += 2;
            }
        }
    }

    public double fieldGoalPercentage() {
        int made = twoPointersMade + threePointersMade;
        int att = twoPointersAttempted + threePointersAttempted;
        return att == 0 ? 0.0 : (made * 100.0) / att;
    }

    public double threePointPercentage() {
        return threePointersAttempted == 0 ? 0.0 : (threePointersMade * 100.0) / threePointersAttempted;
    }

    public int dunks() {
        return dunks;
    }

    public void incrementDunks() {
        this.dunks++;
    }

    public void setDunks(int dunks) {
        this.dunks = Math.max(0, dunks);
    }

    public int assists() {
        return assists;
    }

    public void incrementAssists() {
        this.assists++;
    }

    public void setAssists(int assists) {
        this.assists = Math.max(0, assists);
    }

    public int rebounds() {
        return rebounds;
    }

    public void incrementRebounds() {
        this.rebounds++;
    }

    public void setRebounds(int rebounds) {
        this.rebounds = Math.max(0, rebounds);
    }

    public int steals() {
        return steals;
    }

    public void incrementSteals() {
        this.steals++;
    }

    public void setSteals(int steals) {
        this.steals = Math.max(0, steals);
    }

    public int blocks() {
        return blocks;
    }

    public void incrementBlocks() {
        this.blocks++;
    }

    public void setBlocks(int blocks) {
        this.blocks = Math.max(0, blocks);
    }

    public int ankleBreakers() {
        return ankleBreakers;
    }

    public void incrementAnkleBreakers() {
        this.ankleBreakers++;
    }

    public void setAnkleBreakers(int ankleBreakers) {
        this.ankleBreakers = Math.max(0, ankleBreakers);
    }

    public int wins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = Math.max(0, wins);
    }

    public int losses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = Math.max(0, losses);
    }

    public void recordMatchOutcome(boolean won) {
        if (won) {
            wins++;
            winStreak++;
            if (winStreak > bestWinStreak) {
                bestWinStreak = winStreak;
            }
        } else {
            losses++;
            winStreak = 0;
        }
    }

    private int rankedMatches = 0;
    private int casualMatches = 0;
    private int partyMatches = 0;

    public int rankedMatches() {
        return rankedMatches;
    }

    public void setRankedMatches(int value) {
        this.rankedMatches = Math.max(0, value);
    }

    public int casualMatches() {
        return casualMatches;
    }

    public void setCasualMatches(int value) {
        this.casualMatches = Math.max(0, value);
    }

    public int partyMatches() {
        return partyMatches;
    }

    public void setPartyMatches(int value) {
        this.partyMatches = Math.max(0, value);
    }

    public int mvps() {
        return mvps;
    }

    public void incrementMvps() {
        this.mvps++;
    }

    public void setMvps(int mvps) {
        this.mvps = Math.max(0, mvps);
    }

    public int threePointContestBest() {
        return threePointContestBest;
    }

    public void updateThreePointContestBest(int score) {
        if (score > threePointContestBest) {
            this.threePointContestBest = score;
        }
    }

    public int elo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = Math.max(100, Math.min(99_999, elo));
    }

    public void adjustElo(int delta) {
        long next = (long) this.elo + delta;
        this.elo = (int) Math.max(100L, Math.min(99_999L, next));
    }

    public int xp() {
        return xp;
    }

    public int coins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = Math.max(0, Math.min(999_999_999, coins));
    }

    public void addCoins(int amount) {
        if (amount > 0) {
            this.coins = (int) Math.min(999_999_999L, (long) this.coins + amount);
        }
    }

    public int removeCoins(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int taken = Math.min(this.coins, amount);
        this.coins -= taken;
        return taken;
    }

    public boolean spendCoins(int cost) {
        if (cost < 0) {
            return false;
        }
        if (cost == 0) {
            return true;
        }
        if (this.coins < cost) {
            return false;
        }
        this.coins -= cost;
        return true;
    }

    public boolean hasUnlockedTag(ChatTag tag) {
        if (tag == null) {
            return false;
        }
        if (tag == ChatTag.FOUNDER) {
            return ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(lastKnownName);
        }
        if (tag.isSpecialUnpurchasable()) {
            return ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(lastKnownName) || unlockedTags.contains(tag);
        }
        return tag.priceCoins() == 0 || unlockedTags.contains(tag);
    }

    public void unlockTag(ChatTag tag) {
        if (tag == null) {
            return;
        }
        if (tag == ChatTag.FOUNDER && !ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(lastKnownName)) {
            return;
        }
        unlockedTags.add(tag);
    }

    public void revokeTag(ChatTag tag) {
        if (tag == null) {
            return;
        }
        unlockedTags.remove(tag);
        equippedTags.remove(tag);
    }

    public Set<ChatTag> unlockedTags() {
        return Collections.unmodifiableSet(unlockedTags);
    }

    public void setUnlockedTags(Iterable<ChatTag> tags) {
        unlockedTags.clear();
        if (tags != null) {
            for (ChatTag t : tags) {
                if (t == null) continue;
                if (t == ChatTag.FOUNDER && !ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(lastKnownName)) continue;
                unlockedTags.add(t);
            }
        }
        syncExclusiveFounderState();
    }

    public boolean hasUnlockedBall(BallSkin skin) {
        return skin == BallSkin.CLASSIC || (skin != null && (skin.priceCoins() <= 0 || unlockedBalls.contains(skin)));
    }

    public void unlockBall(BallSkin skin) {
        if (skin != null) {
            unlockedBalls.add(skin);
        }
    }

    public Set<BallSkin> unlockedBalls() {
        return Collections.unmodifiableSet(unlockedBalls);
    }

    public void setUnlockedBalls(Iterable<BallSkin> balls) {
        unlockedBalls.clear();
        unlockedBalls.add(BallSkin.CLASSIC);
        if (balls != null) {
            for (BallSkin b : balls) {
                if (b != null) unlockedBalls.add(b);
            }
        }
    }

    public boolean hasUnlockedDunk(DunkStyle style) {
        return style == DunkStyle.TOMAHAWK || (style != null && (style.priceCoins() <= 0 || unlockedDunks.contains(style)));
    }

    public void unlockDunk(DunkStyle style) {
        if (style != null) {
            unlockedDunks.add(style);
        }
    }

    public Set<DunkStyle> unlockedDunks() {
        return Collections.unmodifiableSet(unlockedDunks);
    }

    public void setUnlockedDunks(Iterable<DunkStyle> dunks) {
        unlockedDunks.clear();
        unlockedDunks.add(DunkStyle.TOMAHAWK);
        if (dunks != null) {
            for (DunkStyle d : dunks) {
                if (d != null) unlockedDunks.add(d);
            }
        }
    }

    public void addXp(int amount) {
        if (amount > 0) {
            this.xp = (int) Math.min(999_999_999L, (long) this.xp + amount);
        }
    }

    public void setXp(int xp) {
        this.xp = Math.max(0, xp);
    }

    public int winStreak() {
        return winStreak;
    }

    public void setWinStreak(int winStreak) {
        this.winStreak = Math.max(0, winStreak);
    }

    public int bestWinStreak() {
        return bestWinStreak;
    }

    public void setBestWinStreak(int bestWinStreak) {
        this.bestWinStreak = Math.max(0, bestWinStreak);
    }

    public BallSkin selectedBallSkin() {
        return selectedBallSkin;
    }

    public void setSelectedBallSkin(BallSkin selectedBallSkin) {
        if (selectedBallSkin != null) {
            this.selectedBallSkin = selectedBallSkin;
        }
    }

    public DunkStyle selectedDunkStyle() {
        return selectedDunkStyle;
    }

    public void setSelectedDunkStyle(DunkStyle selectedDunkStyle) {
        if (selectedDunkStyle != null) {
            this.selectedDunkStyle = selectedDunkStyle;
        }
    }

    public ChatTag selectedTag() {
        if (equippedTags.isEmpty()) {
            return null;
        }
        ChatTag candidate = equippedTags.get(0);
        if (candidate == ChatTag.FOUNDER && !ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(lastKnownName)) {
            equippedTags.remove(ChatTag.FOUNDER);
            return equippedTags.isEmpty() ? null : equippedTags.get(0);
        }
        return candidate;
    }

    public void setSelectedTag(ChatTag selectedTag) {
        equippedTags.clear();
        if (selectedTag != null) {
            if (selectedTag == ChatTag.FOUNDER && !ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(lastKnownName)) {
                return;
            }
            equippedTags.add(selectedTag);
        }
    }

    public List<ChatTag> equippedTags() {
        return Collections.unmodifiableList(equippedTags);
    }

    public boolean isTagEquipped(ChatTag tag) {
        return tag != null && equippedTags.contains(tag);
    }

    public synchronized void clearEquippedTags() {
        this.equippedTags.clear();
    }

    public void setEquippedTags(List<ChatTag> tags) {
        equippedTags.clear();
        if (tags != null) {
            for (ChatTag tag : tags) {
                if (tag == null) continue;
                if (tag == ChatTag.FOUNDER && !ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(lastKnownName)) continue;
                if (!equippedTags.contains(tag) && equippedTags.size() < MAX_EQUIPPED_TAGS) {
                    equippedTags.add(tag);
                }
            }
        }
    }

    public boolean toggleEquippedTag(ChatTag tag) {
        if (tag == null) {
            equippedTags.clear();
            return false;
        }
        if (tag == ChatTag.FOUNDER && !ChatTag.FOUNDER_USERNAME.equalsIgnoreCase(lastKnownName)) {
            return false;
        }
        if (equippedTags.remove(tag)) {
            return false;
        }
        if (equippedTags.size() >= MAX_EQUIPPED_TAGS) {
            equippedTags.remove(0);
        }
        equippedTags.add(tag);
        return true;
    }

    public long playTimeSeconds() {
        return playTimeSeconds;
    }

    public void setPlayTimeSeconds(long playTimeSeconds) {
        this.playTimeSeconds = Math.max(0L, playTimeSeconds);
    }

    public void addPlayTimeSeconds(long deltaSeconds) {
        if (deltaSeconds > 0) {
            this.playTimeSeconds += deltaSeconds;
        }
    }

    public String formattedPlayTimeFr() {
        return formattedPlayTime();
    }

    public String formattedPlayTimeEn() {
        return formattedPlayTime();
    }

    public String formattedPlayTime() {
        long hours = playTimeSeconds / 3600L;
        long mins = (playTimeSeconds % 3600L) / 60L;
        if (hours <= 0) {
            return mins + "m";
        }
        return hours + "h " + mins + "m";
    }

    public int unreadMailCount() {
        return unreadMailCount;
    }

    public void setUnreadMailCount(int unreadMailCount) {
        this.unreadMailCount = Math.max(0, unreadMailCount);
    }

    public PlayerSettings settings() {
        return settings;
    }

    public Set<UUID> friends() {
        return Collections.unmodifiableSet(friends);
    }

    public void addFriend(UUID friendId) {
        if (friendId != null && !friendId.equals(uuid)) {
            friends.add(friendId);
        }
    }

    public boolean removeFriend(UUID friendId) {
        return friends.remove(friendId);
    }

    public boolean isFriend(UUID other) {
        return other != null && friends.contains(other);
    }

    public List<MatchHistoryEntry> matchHistory() {
        return Collections.unmodifiableList(matchHistory);
    }

    public void recordMatchHistory(MatchHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        matchHistory.add(0, entry);
        while (matchHistory.size() > MAX_HISTORY) {
            matchHistory.remove(matchHistory.size() - 1);
        }
    }

    public void recordMatch(MatchHistoryEntry entry) {
        recordMatchHistory(entry);
    }

    public int threePointers() {
        return threePointersMade;
    }

    public double winRatePercent() {
        int total = wins + losses;
        return total == 0 ? 0.0 : (100.0 * wins) / total;
    }

    public void replaceMatchHistory(List<MatchHistoryEntry> entries) {
        matchHistory.clear();
        if (entries != null) {
            for (int i = 0; i < Math.min(MAX_HISTORY, entries.size()); i++) {
                if (entries.get(i) != null) {
                    matchHistory.add(entries.get(i));
                }
            }
        }
    }

    public Set<UUID> ignoredPlayers() {
        return Collections.unmodifiableSet(ignoredPlayers);
    }

    public void addIgnored(UUID playerId) {
        if (playerId != null && !playerId.equals(uuid)) {
            ignoredPlayers.add(playerId);
        }
    }

    public boolean removeIgnored(UUID playerId) {
        return ignoredPlayers.remove(playerId);
    }

    public boolean isIgnoring(UUID playerId) {
        return playerId != null && ignoredPlayers.contains(playerId);
    }

    public void setIgnoredPlayers(Iterable<UUID> ids) {
        ignoredPlayers.clear();
        if (ids != null) {
            for (UUID id : ids) {
                addIgnored(id);
            }
        }
    }

    public boolean tutorialCompleted() {
        return tutorialCompleted;
    }

    public void setTutorialCompleted(boolean tutorialCompleted) {
        this.tutorialCompleted = tutorialCompleted;
    }

    public int tutorialStep() {
        return tutorialStep;
    }

    public void setTutorialStep(int step) {
        this.tutorialStep = Math.max(0, step);
    }

    public List<PlayerQuest> dailyQuests() {
        return dailyQuests;
    }

    public List<PlayerQuest> weeklyQuests() {
        return weeklyQuests;
    }

    public List<PlayerQuest> contracts() {
        return contracts;
    }

    public long dailyBoardRefreshMillis() {
        return dailyBoardRefreshMillis;
    }

    public void setDailyBoardRefreshMillis(long millis) {
        this.dailyBoardRefreshMillis = millis;
    }

    public long weeklyBoardRefreshMillis() {
        return weeklyBoardRefreshMillis;
    }

    public void setWeeklyBoardRefreshMillis(long millis) {
        this.weeklyBoardRefreshMillis = millis;
    }

    public long contractBoardRefreshMillis() {
        return contractBoardRefreshMillis;
    }

    public void setContractBoardRefreshMillis(long millis) {
        this.contractBoardRefreshMillis = millis;
    }

    public List<PlayerQuest> allActiveQuests() {
        List<PlayerQuest> all = new ArrayList<>(dailyQuests.size() + weeklyQuests.size() + contracts.size());
        all.addAll(dailyQuests);
        all.addAll(weeklyQuests);
        all.addAll(contracts);
        return all;
    }

    public int unclaimedQuestCount() {
        int n = 0;
        for (PlayerQuest q : allActiveQuests()) {
            if (q.isComplete() && !q.claimed()) {
                n++;
            }
        }
        return n;
    }

    public void replaceQuestBoard(List<PlayerQuest> target, List<PlayerQuest> replacement) {
        if (target == null) {
            return;
        }
        target.clear();
        if (replacement != null) {
            target.addAll(replacement);
        }
    }

    public Set<Achievement> unlockedAchievements() {
        return Collections.unmodifiableSet(unlockedAchievements);
    }

    public boolean hasAchievement(Achievement achievement) {
        return achievement != null && unlockedAchievements.contains(achievement);
    }

    public boolean unlockAchievement(Achievement achievement) {
        return achievement != null && unlockedAchievements.add(achievement);
    }

    public void setUnlockedAchievements(Iterable<Achievement> achievements) {
        unlockedAchievements.clear();
        if (achievements != null) {
            for (Achievement a : achievements) {
                if (a != null) {
                    unlockedAchievements.add(a);
                }
            }
        }
    }

    public long achievementEvaluationLockMillis() {
        return achievementEvaluationLockMillis;
    }

    public void setAchievementEvaluationLockMillis(long millis) {
        this.achievementEvaluationLockMillis = millis;
    }

    public int dailyLoginStreak() {
        return dailyLoginStreak;
    }

    public void setDailyLoginStreak(int streak) {
        this.dailyLoginStreak = Math.max(0, streak);
    }

    public long lastDailyStreakClaimMillis() {
        return lastDailyStreakClaimMillis;
    }

    public void setLastDailyStreakClaimMillis(long millis) {
        this.lastDailyStreakClaimMillis = millis;
    }

    public long totalCoinsEarned() {
        return totalCoinsEarned;
    }

    public void setTotalCoinsEarned(long total) {
        this.totalCoinsEarned = Math.max(0L, total);
    }

    public void addEarnedCoins(int amount) {
        if (amount > 0) {
            addCoins(amount);
            this.totalCoinsEarned = Math.min(Long.MAX_VALUE, this.totalCoinsEarned + amount);
        }
    }

    public int chatWarnPoints() {
        return chatWarnPoints;
    }

    public void setChatWarnPoints(int points) {
        this.chatWarnPoints = Math.max(0, points);
    }

    public int incrementChatWarnPoints() {
        this.chatWarnPoints++;
        return this.chatWarnPoints;
    }

    public long chatMuteUntilMillis() {
        return chatMuteUntilMillis;
    }

    public void setChatMuteUntilMillis(long millis) {
        this.chatMuteUntilMillis = Math.max(0L, millis);
    }

    public boolean isChatMuted(long nowMillis) {
        return chatMuteUntilMillis > nowMillis;
    }

    public int dailyCratesOpened() {
        return dailyCratesOpened;
    }

    public void setDailyCratesOpened(int count) {
        this.dailyCratesOpened = Math.max(0, count);
    }

    public long crateDayEpoch() {
        return crateDayEpoch;
    }

    public void setCrateDayEpoch(long epochDay) {
        this.crateDayEpoch = epochDay;
    }

    public long matchmakingBanUntilMillis() {
        return matchmakingBanUntilMillis;
    }

    public void setMatchmakingBanUntilMillis(long millis) {
        this.matchmakingBanUntilMillis = Math.max(0L, millis);
    }

    public boolean isMatchmakingBanned(long nowMillis) {
        return matchmakingBanUntilMillis > nowMillis;
    }

    public int dodgeCount() {
        return dodgeCount;
    }

    public void setDodgeCount(int dodgeCount) {
        this.dodgeCount = Math.max(0, dodgeCount);
    }

    public String guildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public boolean partyChatEnabled() {
        return partyChatEnabled;
    }

    public void setPartyChatEnabled(boolean partyChatEnabled) {
        this.partyChatEnabled = partyChatEnabled;
    }
}
