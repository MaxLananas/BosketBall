package fr.bosketball.stats;

import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.model.Achievement;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerSettings;
import fr.bosketball.model.Rank;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class StatsManager {

    public enum LeaderboardCategory {
        ELO("ELO Rating"),
        COINS("Coins"),
        WINS("Wins"),
        POINTS("Points"),
        DUNKS("Slam Dunks"),
        THREE_POINTERS("Three-Pointers"),
        MVPS("MVP Awards"),
        ASSISTS("Assists");

        private final String title;

        LeaderboardCategory(String title) {
            this.title = title;
        }

        public String titleSmallCaps() {
            return title;
        }

        public String label() {
            return title;
        }

        public int extract(PlayerProfile p) {
            return switch (this) {
                case ELO -> p.elo();
                case COINS -> p.coins();
                case WINS -> p.wins();
                case POINTS -> p.points();
                case DUNKS -> p.dunks();
                case THREE_POINTERS -> p.threePointers();
                case MVPS -> p.mvps();
                case ASSISTS -> p.assists();
            };
        }
    }

    public record LeaderboardBadges(
        int winsPlace,
        int eloPlace,
        int mvpPlace,
        int assistsPlace,
        int pointsPlace,
        int dunksPlace
    ) {
        public static final LeaderboardBadges NONE = new LeaderboardBadges(0, 0, 0, 0, 0, 0);

        public boolean hasAny() {
            return winsPlace > 0 || eloPlace > 0 || mvpPlace > 0 || assistsPlace > 0 || pointsPlace > 0 || dunksPlace > 0;
        }
    }

    private record PlayerSnapshot(
        UUID uuid,
        String username,
        String skinTextureValue,
        String skinTextureSignature,
        long skinTextureUpdatedAtMillis,
        String staffRank,
        String formattedPlaytime,
        long playTimeSeconds,
        int coins,
        int elo,
        int xp,
        String equippedTag,
        List<String> equippedTags,
        String equippedBall,
        String equippedDunk,
        List<String> unlockedTags,
        List<String> unlockedBalls,
        List<String> unlockedDunks,
        int wins,
        int losses,
        int winStreak,
        int bestWinStreak,
        int mvps,
        int points,
        int dunks,
        int assists,
        int rebounds,
        int steals,
        int blocks,
        int ankleBreakers,
        int twoMade,
        int twoAttempted,
        int threeMade,
        int threeAttempted,
        int threePointContestBest,
        boolean customSounds,
        boolean aimTrajectory,
        boolean floorRings,
        boolean overheadNameplates,
        boolean selfRigF5,
        boolean customAnimations,
        boolean chatAnnouncements,
        boolean cinematicReplayCam,
        boolean partyInvites,
        int unreadMail,
        List<String> friends,
        List<MatchHistoryEntry> recentMatches,
        List<String> ignoredPlayers,
        boolean tutorialCompleted,
        int tutorialStep,
        List<String> dailyQuests,
        List<String> weeklyQuests,
        List<String> contracts,
        long dailyBoardRefresh,
        long weeklyBoardRefresh,
        long contractBoardRefresh,
        List<String> achievements,
        int dailyLoginStreak,
        long lastDailyClaim,
        long totalCoinsEarned,
        int rankedMatches,
        int casualMatches,
        int partyMatches,
        int chatWarnPoints,
        long chatMuteUntil,
        int dailyCratesOpened,
        long crateDayEpoch,
        String guildId,
        boolean partyChatEnabled,
        long matchmakingBanUntil,
        int dodgeCount,
        String language,
        boolean profanityFilter,
        boolean partyChatPref,
        boolean showStatusInTab,
        boolean showQuestNotifications
    ) {
        static PlayerSnapshot capture(PlayerProfile p) {
            PlayerSettings st = p.settings();
            return new PlayerSnapshot(
                p.uuid(),
                p.lastKnownName(),
                p.skinTextureValue(),
                p.skinTextureSignature(),
                p.skinTextureUpdatedAtMillis(),
                p.staffRankOverride() == null ? null : p.staffRankOverride().name(),
                p.formattedPlayTime(),
                p.playTimeSeconds(),
                p.coins(),
                p.elo(),
                p.xp(),
                p.selectedTag() == null ? "NONE" : p.selectedTag().name(),
                p.equippedTags().stream().map(ChatTag::name).toList(),
                p.selectedBallSkin().name(),
                p.selectedDunkStyle().name(),
                p.unlockedTags().stream().map(ChatTag::name).sorted().toList(),
                p.unlockedBalls().stream().map(BallSkin::name).sorted().toList(),
                p.unlockedDunks().stream().map(DunkStyle::name).sorted().toList(),
                p.wins(),
                p.losses(),
                p.winStreak(),
                p.bestWinStreak(),
                p.mvps(),
                p.points(),
                p.dunks(),
                p.assists(),
                p.rebounds(),
                p.steals(),
                p.blocks(),
                p.ankleBreakers(),
                p.twoPointersMade(),
                p.twoPointersAttempted(),
                p.threePointersMade(),
                p.threePointersAttempted(),
                p.threePointContestBest(),
                st.customSounds(),
                st.aimTrajectory(),
                st.teamFloorRings(),
                st.overheadNameplates(),
                st.selfBodyRigInF5(),
                st.customAnimations(),
                st.chatAnnouncements(),
                st.cinematicScoreCam(),
                st.partyInvites(),
                p.unreadMailCount(),
                p.friends().stream().map(UUID::toString).sorted().toList(),
                List.copyOf(p.matchHistory()),
                p.ignoredPlayers().stream().map(UUID::toString).sorted().toList(),
                p.tutorialCompleted(),
                p.tutorialStep(),
                fr.bosketball.model.QuestCodec.encodeList(p.dailyQuests()),
                fr.bosketball.model.QuestCodec.encodeList(p.weeklyQuests()),
                fr.bosketball.model.QuestCodec.encodeList(p.contracts()),
                p.dailyBoardRefreshMillis(),
                p.weeklyBoardRefreshMillis(),
                p.contractBoardRefreshMillis(),
                p.unlockedAchievements().stream().map(Enum::name).sorted().toList(),
                p.dailyLoginStreak(),
                p.lastDailyStreakClaimMillis(),
                p.totalCoinsEarned(),
                p.rankedMatches(),
                p.casualMatches(),
                p.partyMatches(),
                p.chatWarnPoints(),
                p.chatMuteUntilMillis(),
                p.dailyCratesOpened(),
                p.crateDayEpoch(),
                p.guildId(),
                p.partyChatEnabled(),
                p.matchmakingBanUntilMillis(),
                p.dodgeCount(),
                st.language(),
                st.profanityFilter(),
                st.partyChat(),
                st.showStatusInTab(),
                st.showQuestNotifications()
            );
        }
    }

    private final File playersDir;
    private final File nameIndexFile;
    private final File legacyMonolithicFile;
    private final Logger logger;

    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameToUuidIndex = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyProfiles = ConcurrentHashMap.newKeySet();
    private final Map<UUID, LeaderboardBadges> badgeCache = new ConcurrentHashMap<>();
    private volatile LeaderboardIndex leaderboardIndex;
    private volatile boolean indexDirty = false;

    private static final Comparator<PlayerProfile> CMP_ELO = Comparator.comparingInt(PlayerProfile::elo).thenComparingInt(PlayerProfile::wins);
    private static final Comparator<PlayerProfile> CMP_COINS = Comparator.comparingInt(PlayerProfile::coins).thenComparingInt(PlayerProfile::wins);
    private static final Comparator<PlayerProfile> CMP_WINS = Comparator.comparingInt(PlayerProfile::wins).thenComparingInt(PlayerProfile::elo);
    private static final Comparator<PlayerProfile> CMP_POINTS = Comparator.comparingInt(PlayerProfile::points).thenComparingInt(PlayerProfile::wins);
    private static final Comparator<PlayerProfile> CMP_DUNKS = Comparator.comparingInt(PlayerProfile::dunks).thenComparingInt(PlayerProfile::points);
    private static final Comparator<PlayerProfile> CMP_THREES = Comparator.comparingInt(PlayerProfile::threePointersMade).thenComparingInt(PlayerProfile::points);
    private static final Comparator<PlayerProfile> CMP_MVPS = Comparator.comparingInt(PlayerProfile::mvps).thenComparingInt(PlayerProfile::wins);
    private static final Comparator<PlayerProfile> CMP_ASSISTS = Comparator.comparingInt(PlayerProfile::assists).thenComparingInt(PlayerProfile::points);

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BosketBall-StatsIO");
        t.setDaemon(true);
        return t;
    });

    public StatsManager(File dataFolder, Logger logger) {
        this.playersDir = new File(dataFolder, "players");
        this.nameIndexFile = new File(this.playersDir, "_name_index.yml");
        this.legacyMonolithicFile = new File(dataFolder, "players.yml");
        this.logger = logger;
    }

    public File playersDirectory() {
        return playersDir;
    }

    public LeaderboardIndex attachLeaderboardIndex(int cacheSize, int refreshSeconds) {
        this.leaderboardIndex = new LeaderboardIndex(this);
        this.leaderboardIndex.configure(cacheSize, refreshSeconds);
        this.leaderboardIndex.rebuildAll();
        this.leaderboardIndex.start();
        return this.leaderboardIndex;
    }

    public LeaderboardIndex leaderboardIndex() {
        return leaderboardIndex;
    }

    public Collection<PlayerProfile> allProfiles() {
        return profiles.values();
    }

    public PlayerProfile getOrCreate(Player player) {
        boolean isNew = !profiles.containsKey(player.getUniqueId());
        PlayerProfile profile = profiles.computeIfAbsent(
            player.getUniqueId(),
            id -> new PlayerProfile(id, player.getName())
        );
        if (!player.getName().equals(profile.lastKnownName())) {
            if (profile.lastKnownName() != null) {
                nameToUuidIndex.remove(profile.lastKnownName().toLowerCase(java.util.Locale.ROOT), profile.uuid());
            }
            profile.setLastKnownName(player.getName());
            indexDirty = true;
            dirtyProfiles.add(profile.uuid());
        }
        if (captureLiveSkinTexture(player, profile)) {
            dirtyProfiles.add(profile.uuid());
        }
        nameToUuidIndex.put(player.getName().toLowerCase(java.util.Locale.ROOT), profile.uuid());
        if (isNew) {
            indexDirty = true;
            dirtyProfiles.add(profile.uuid());
            refreshLeaderboardBadges();
        }
        return profile;
    }

    public PlayerProfile getOrCreate(UUID uuid, String fallbackName) {
        boolean isNew = !profiles.containsKey(uuid);
        PlayerProfile profile = profiles.computeIfAbsent(uuid, id -> new PlayerProfile(id, fallbackName));
        if (profile.lastKnownName() != null && !profile.lastKnownName().isBlank()) {
            nameToUuidIndex.put(profile.lastKnownName().toLowerCase(java.util.Locale.ROOT), uuid);
        }
        if (isNew) {
            indexDirty = true;
            dirtyProfiles.add(uuid);
        }
        return profile;
    }

    public PlayerProfile getIfPresent(UUID uuid) {
        return profiles.get(uuid);
    }

    public PlayerProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public ProfileProperty cachedSkinTexture(UUID uuid) {
        PlayerProfile profile = uuid != null ? profiles.get(uuid) : null;
        if (profile == null || !profile.hasSkinTexture()) {
            return null;
        }
        return new ProfileProperty("textures", profile.skinTextureValue(), profile.skinTextureSignature());
    }

    public boolean storeSkinTexture(UUID uuid, String fallbackName, ProfileProperty property) {
        if (uuid == null || property == null || !"textures".equals(property.getName())) {
            return false;
        }
        PlayerProfile profile = profiles.computeIfAbsent(uuid,
            id -> new PlayerProfile(id, fallbackName == null || fallbackName.isBlank() ? "Player" : fallbackName));
        if (fallbackName != null && !fallbackName.isBlank()
            && (profile.lastKnownName() == null || profile.lastKnownName().isBlank() || "Player".equals(profile.lastKnownName()))) {
            profile.setLastKnownName(fallbackName);
        }
        boolean changed = profile.updateSkinTexture(property.getValue(), property.getSignature());
        if (changed) {
            dirtyProfiles.add(uuid);
            saveProfileAsync(profile);
        }
        return changed;
    }

    public static ProfileProperty texturesProperty(com.destroystokyo.paper.profile.PlayerProfile profile) {
        if (profile == null) {
            return null;
        }
        try {
            for (ProfileProperty property : profile.getProperties()) {
                if ("textures".equals(property.getName())
                    && property.getValue() != null && !property.getValue().isBlank()) {
                    return property;
                }
            }
        } catch (Throwable ignored) {

        }
        return null;
    }

    private static boolean captureLiveSkinTexture(Player player, PlayerProfile profile) {
        if (player == null || profile == null) {
            return false;
        }
        try {
            ProfileProperty property = texturesProperty(player.getPlayerProfile());
            return property != null && profile.updateSkinTexture(property.getValue(), property.getSignature());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void markDirty(UUID uuid) {
        if (uuid != null) {
            dirtyProfiles.add(uuid);
        }
    }

    public int dirtyCount() {
        return dirtyProfiles.size();
    }

    public void saveProfileAsync(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        profiles.put(profile.uuid(), profile);
        dirtyProfiles.remove(profile.uuid());
        PlayerSnapshot snap = PlayerSnapshot.capture(profile);
        boolean writeIndex = indexDirty;
        Map<String, String> indexSnap = writeIndex ? captureNameIndexSnapshot() : null;
        if (writeIndex) {
            indexDirty = false;
        }

        Runnable task = () -> {
            writePlayerSnapshotToDisk(snap);
            if (indexSnap != null) {
                writeNameIndexToDisk(indexSnap);
            }
        };
        if (ioExecutor.isShutdown()) {
            task.run();
        } else {
            ioExecutor.execute(task);
        }
    }

    public void saveAsync() {
        flushDirtyAsync(true);
    }

    public void flushDirtyAsync(boolean fallbackAllIfNoneMarked) {
        List<PlayerSnapshot> batch = new ArrayList<>();
        if (!dirtyProfiles.isEmpty()) {
            for (UUID id : new ArrayList<>(dirtyProfiles)) {
                if (dirtyProfiles.remove(id)) {
                    PlayerProfile p = profiles.get(id);
                    if (p != null) {
                        batch.add(PlayerSnapshot.capture(p));
                    }
                }
            }
        } else if (fallbackAllIfNoneMarked) {
            for (PlayerProfile p : profiles.values()) {
                batch.add(PlayerSnapshot.capture(p));
            }
        }

        if (batch.isEmpty() && !indexDirty) {
            return;
        }

        Map<String, String> indexSnap = captureNameIndexSnapshot();
        indexDirty = false;

        Runnable task = () -> {
            for (PlayerSnapshot snap : batch) {
                writePlayerSnapshotToDisk(snap);
            }
            writeNameIndexToDisk(indexSnap);
        };
        if (ioExecutor.isShutdown()) {
            task.run();
        } else {
            ioExecutor.execute(task);
        }
    }

    public void save() {
        dirtyProfiles.clear();
        indexDirty = false;
        for (PlayerProfile p : profiles.values()) {
            writePlayerSnapshotToDisk(PlayerSnapshot.capture(p));
        }
        writeNameIndexToDisk(captureNameIndexSnapshot());
    }

    public void shutdownAndSaveSync() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        save();
    }

    public PlayerProfile findByNameIgnoreCase(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        UUID indexedId = nameToUuidIndex.get(trimmed.toLowerCase(java.util.Locale.ROOT));
        if (indexedId != null) {
            PlayerProfile fast = profiles.get(indexedId);
            if (fast != null && fast.lastKnownName() != null && fast.lastKnownName().equalsIgnoreCase(trimmed)) {
                return fast;
            }
        }
        for (PlayerProfile p : profiles.values()) {
            if (p.lastKnownName() != null && p.lastKnownName().equalsIgnoreCase(trimmed)) {
                nameToUuidIndex.put(trimmed.toLowerCase(java.util.Locale.ROOT), p.uuid());
                return p;
            }
        }
        return null;
    }

    public List<PlayerProfile> topBy(LeaderboardCategory category, int limit) {
        if (limit <= 0 || profiles.isEmpty()) {
            return List.of();
        }
        LeaderboardIndex index = this.leaderboardIndex;
        if (index != null && limit <= index.cacheSize()) {
            List<PlayerProfile> snapshot = index.top(category);
            if (!snapshot.isEmpty()) {
                return snapshot.size() <= limit ? snapshot : snapshot.subList(0, limit);
            }
        }
        return topByUncached(category, limit);
    }

    public List<PlayerProfile> topByUncached(LeaderboardCategory category, int limit) {
        if (limit <= 0 || profiles.isEmpty()) {
            return List.of();
        }
        Comparator<PlayerProfile> cmp = switch (category) {
            case ELO -> CMP_ELO;
            case COINS -> CMP_COINS;
            case WINS -> CMP_WINS;
            case POINTS -> CMP_POINTS;
            case DUNKS -> CMP_DUNKS;
            case THREE_POINTERS -> CMP_THREES;
            case MVPS -> CMP_MVPS;
            case ASSISTS -> CMP_ASSISTS;
        };

        int total = profiles.size();

        if (limit <= 20 && total > limit * 2) {
            java.util.PriorityQueue<PlayerProfile> minHeap = new java.util.PriorityQueue<>(limit + 1, cmp);
            for (PlayerProfile p : profiles.values()) {
                if (minHeap.size() < limit) {
                    minHeap.offer(p);
                } else if (cmp.compare(p, minHeap.peek()) > 0) {
                    minHeap.poll();
                    minHeap.offer(p);
                }
            }
            List<PlayerProfile> top = new ArrayList<>(minHeap);
            top.sort(cmp.reversed());
            return top;
        }

        List<PlayerProfile> sorted = new ArrayList<>(profiles.values());
        sorted.sort(cmp.reversed());
        return sorted.size() <= limit ? sorted : sorted.subList(0, limit);
    }

    public void refreshLeaderboardBadges() {
        List<PlayerProfile> topWins = topBy(LeaderboardCategory.WINS, 3);
        List<PlayerProfile> topElo = topBy(LeaderboardCategory.ELO, 3);
        List<PlayerProfile> topMvp = topBy(LeaderboardCategory.MVPS, 3);
        List<PlayerProfile> topAst = topBy(LeaderboardCategory.ASSISTS, 3);
        List<PlayerProfile> topPts = topBy(LeaderboardCategory.POINTS, 3);
        List<PlayerProfile> topDnk = topBy(LeaderboardCategory.DUNKS, 3);

        Map<UUID, Integer> wMap = new ConcurrentHashMap<>();
        Map<UUID, Integer> eMap = new ConcurrentHashMap<>();
        Map<UUID, Integer> mMap = new ConcurrentHashMap<>();
        Map<UUID, Integer> aMap = new ConcurrentHashMap<>();
        Map<UUID, Integer> pMap = new ConcurrentHashMap<>();
        Map<UUID, Integer> dMap = new ConcurrentHashMap<>();

        for (int i = 0; i < topWins.size(); i++) {
            if (topWins.get(i).wins() > 0) {
                wMap.put(topWins.get(i).uuid(), i + 1);
            }
        }
        for (int i = 0; i < topElo.size(); i++) {
            if (topElo.get(i).elo() >= 1000 || topElo.get(i).wins() > 0 || topElo.get(i).points() > 0) {
                eMap.put(topElo.get(i).uuid(), i + 1);
            }
        }
        for (int i = 0; i < topMvp.size(); i++) {
            if (topMvp.get(i).mvps() > 0) {
                mMap.put(topMvp.get(i).uuid(), i + 1);
            }
        }
        for (int i = 0; i < topAst.size(); i++) {
            if (topAst.get(i).assists() > 0) {
                aMap.put(topAst.get(i).uuid(), i + 1);
            }
        }
        for (int i = 0; i < topPts.size(); i++) {
            if (topPts.get(i).points() > 0) {
                pMap.put(topPts.get(i).uuid(), i + 1);
            }
        }
        for (int i = 0; i < topDnk.size(); i++) {
            if (topDnk.get(i).dunks() > 0) {
                dMap.put(topDnk.get(i).uuid(), i + 1);
            }
        }

        badgeCache.clear();
        for (UUID uuid : profiles.keySet()) {
            LeaderboardBadges b = new LeaderboardBadges(
                wMap.getOrDefault(uuid, 0),
                eMap.getOrDefault(uuid, 0),
                mMap.getOrDefault(uuid, 0),
                aMap.getOrDefault(uuid, 0),
                pMap.getOrDefault(uuid, 0),
                dMap.getOrDefault(uuid, 0)
            );
            if (b.hasAny()) {
                badgeCache.put(uuid, b);
            }
        }
    }

    public LeaderboardBadges getBadges(UUID uuid) {
        return badgeCache.getOrDefault(uuid, LeaderboardBadges.NONE);
    }

    public static int computeEloDelta(double winnerAvgElo, double loserAvgElo) {
        double expectedWin = 1.0 / (1.0 + Math.pow(10.0, (loserAvgElo - winnerAvgElo) / 400.0));
        return Math.max(8, (int) Math.round(32.0 * (1.0 - expectedWin)));
    }

    public void load() {
        profiles.clear();
        dirtyProfiles.clear();

        if (legacyMonolithicFile.exists()) {
            migrateLegacyMonolithicFile();
        }

        if (playersDir.exists() && playersDir.isDirectory()) {
            File[] files = playersDir.listFiles((dir, name) ->
                name.endsWith(".yml") && !name.startsWith("_") && !name.endsWith(".tmp"));
            if (files != null) {
                for (File f : files) {
                    String rawUuid = f.getName().substring(0, f.getName().length() - 4);
                    try {
                        UUID uuid = UUID.fromString(rawUuid);
                        YamlConfiguration yml = AtomicYamlWriter.loadSafe(f, logger);
                        PlayerProfile loaded = parsePlayerYaml(uuid, yml);
                        if (loaded != null) {
                            profiles.put(uuid, loaded);
                            if (loaded.lastKnownName() != null && !loaded.lastKnownName().isBlank()) {
                                nameToUuidIndex.put(loaded.lastKnownName().toLowerCase(java.util.Locale.ROOT), uuid);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        refreshLeaderboardBadges();
    }

    private void migrateLegacyMonolithicFile() {
        YamlConfiguration yaml = AtomicYamlWriter.loadSafe(legacyMonolithicFile, logger);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        int migrated = 0;
        if (root != null) {
            for (String key : root.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection sec = root.getConfigurationSection(key);
                    if (sec != null) {
                        PlayerProfile p = parsePlayerYaml(uuid, sec);
                        if (p != null) {
                            profiles.put(uuid, p);
                            writePlayerSnapshotToDisk(PlayerSnapshot.capture(p));
                            migrated++;
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        writeNameIndexToDisk(captureNameIndexSnapshot());
        File backup = new File(legacyMonolithicFile.getParentFile(), "players.yml.migrated.bak");
        try {
            Files.move(legacyMonolithicFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (logger != null) {
                logger.info("Migrated " + migrated + " player profiles from legacy players.yml into players/<uuid>.yml!");
            }
        } catch (IOException ignored) {
        }
    }

    private static PlayerProfile parsePlayerYaml(UUID uuid, ConfigurationSection sec) {
        String name = sec.getString("identity.username", sec.getString("name", "Player"));
        PlayerProfile p = new PlayerProfile(uuid, name);

        String staffRaw = sec.getString("identity.staff-rank", sec.getString("staff-rank", null));
        p.setStaffRankOverride(Rank.byNameOrNull(staffRaw));
        p.setPlayTimeSeconds(sec.getLong("identity.playtime-seconds", sec.getLong("playtime-sec", 0L)));
        p.setSkinTexture(
            sec.getString("identity.skin-texture-value", null),
            sec.getString("identity.skin-texture-signature", null),
            sec.getLong("identity.skin-texture-updated-at", 0L)
        );

        p.setCoins(sec.getInt("economy.coins", sec.getInt("coins", 100)));
        p.setElo(sec.getInt("economy.elo", sec.getInt("elo", 1000)));
        p.setXp(sec.getInt("economy.xp", sec.getInt("xp", 0)));

        p.setWins(sec.getInt("stats.wins", sec.getInt("wins", 0)));
        p.setLosses(sec.getInt("stats.losses", sec.getInt("losses", 0)));
        p.setWinStreak(sec.getInt("stats.win-streak", sec.getInt("streak", 0)));
        p.setBestWinStreak(sec.getInt("stats.best-win-streak", sec.getInt("best-streak", 0)));
        p.setMvps(sec.getInt("stats.mvps", sec.getInt("mvps", 0)));
        p.setPoints(sec.getInt("stats.points", sec.getInt("points", 0)));
        p.setDunks(sec.getInt("stats.dunks", sec.getInt("dunks", 0)));
        p.setAssists(sec.getInt("stats.assists", sec.getInt("assists", 0)));
        p.setRebounds(sec.getInt("stats.rebounds", sec.getInt("rebounds", 0)));
        p.setSteals(sec.getInt("stats.steals", sec.getInt("steals", 0)));
        p.setBlocks(sec.getInt("stats.blocks", sec.getInt("blocks", 0)));
        p.setAnkleBreakers(sec.getInt("stats.ankle-breakers", sec.getInt("ankle-breakers", 0)));
        p.setTwoPointersMade(sec.getInt("stats.two-pointers.made", sec.getInt("2pm", 0)));
        p.setTwoPointersAttempted(sec.getInt("stats.two-pointers.attempted", sec.getInt("2pa", 0)));
        p.setThreePointersMade(sec.getInt("stats.three-pointers.made", sec.getInt("3pm", 0)));
        p.setThreePointersAttempted(sec.getInt("stats.three-pointers.attempted", sec.getInt("3pa", 0)));
        p.updateThreePointContestBest(sec.getInt("stats.three-point-contest-best", sec.getInt("3pt-best", 0)));

        String ballRaw = sec.getString("cosmetics.equipped-ball", sec.getString("ball-skin", "CLASSIC"));
        String dunkRaw = sec.getString("cosmetics.equipped-dunk", sec.getString("dunk-style", "TOMAHAWK"));
        p.setSelectedBallSkin(BallSkin.byNameOrDefault(ballRaw, BallSkin.CLASSIC));
        p.setSelectedDunkStyle(DunkStyle.byNameOrDefault(dunkRaw, DunkStyle.TOMAHAWK));

        List<String> rawUnlockedTags = sec.contains("cosmetics.unlocked-tags")
            ? sec.getStringList("cosmetics.unlocked-tags")
            : sec.getStringList("unlocked-tags");
        List<ChatTag> unlockedT = new ArrayList<>();
        for (String ut : rawUnlockedTags) {
            ChatTag ct = ChatTag.byNameOrNull(ut);
            if (ct != null) unlockedT.add(ct);
        }
        p.setUnlockedTags(unlockedT);

        List<String> rawUnlockedBalls = sec.contains("cosmetics.unlocked-balls")
            ? sec.getStringList("cosmetics.unlocked-balls")
            : sec.getStringList("unlocked-balls");
        List<BallSkin> unlockedB = new ArrayList<>();
        for (String ub : rawUnlockedBalls) {
            unlockedB.add(BallSkin.byNameOrDefault(ub, BallSkin.CLASSIC));
        }
        p.setUnlockedBalls(unlockedB);
        p.unlockBall(p.selectedBallSkin());

        List<String> rawUnlockedDunks = sec.contains("cosmetics.unlocked-dunks")
            ? sec.getStringList("cosmetics.unlocked-dunks")
            : sec.getStringList("unlocked-dunks");
        List<DunkStyle> unlockedD = new ArrayList<>();
        for (String ud : rawUnlockedDunks) {
            unlockedD.add(DunkStyle.byNameOrDefault(ud, DunkStyle.TOMAHAWK));
        }
        p.setUnlockedDunks(unlockedD);
        p.unlockDunk(p.selectedDunkStyle());

        String equippedTagRaw = sec.getString("cosmetics.equipped-tag", null);
        if (equippedTagRaw != null) {
            ChatTag ct = "NONE".equalsIgnoreCase(equippedTagRaw) ? null : ChatTag.byNameOrNull(equippedTagRaw);
            p.setSelectedTag((ct != null && p.hasUnlockedTag(ct)) ? ct : null);
        } else {
            List<String> rawTagList = sec.getStringList("chat-tags");
            if (!rawTagList.isEmpty()) {
                List<ChatTag> parsedTags = new ArrayList<>();
                for (String rt : rawTagList) {
                    ChatTag ct = ChatTag.byNameOrNull(rt);
                    if (ct != null && p.hasUnlockedTag(ct)) {
                        parsedTags.add(ct);
                    }
                }
                p.setEquippedTags(parsedTags);
            } else {
                String rawTag = sec.getString("chat-tag", "NONE");
                ChatTag single = "NONE".equalsIgnoreCase(rawTag) ? null : ChatTag.byNameOrNull(rawTag);
                p.setSelectedTag((single != null && p.hasUnlockedTag(single)) ? single : null);
            }
        }

        PlayerSettings st = p.settings();
        st.setCustomSounds(sec.getBoolean("settings.custom-sounds", sec.getBoolean("settings.sounds", true)));
        st.setAimTrajectory(sec.getBoolean("settings.aim-trajectory", sec.getBoolean("settings.aim-arc", true)));
        st.setTeamFloorRings(sec.getBoolean("settings.floor-rings", true));
        st.setOverheadNameplates(sec.getBoolean("settings.overhead-nameplates", sec.getBoolean("settings.nameplates", true)));
        st.setSelfBodyRigInF5(sec.getBoolean("settings.self-rig-f5", true));
        st.setCustomAnimations(sec.getBoolean("settings.custom-animations", true));
        st.setChatAnnouncements(sec.getBoolean("settings.chat-announcements", sec.getBoolean("settings.announcements", true)));
        st.setCinematicScoreCam(sec.getBoolean("settings.cinematic-replay-cam", sec.getBoolean("settings.cinematic-cam", true)));
        st.setPartyInvites(sec.getBoolean("settings.party-invites", true));

        p.setUnreadMailCount(sec.getInt("social.unread-mail", sec.getInt("unread-mail", 0)));
        List<String> rawFriends = sec.contains("social.friends")
            ? sec.getStringList("social.friends")
            : sec.getStringList("friends");
        for (String fid : rawFriends) {
            try {
                p.addFriend(UUID.fromString(fid));
            } catch (IllegalArgumentException ignored) {
            }
        }

        for (String iid : sec.getStringList("social.ignored")) {
            try {
                p.addIgnored(UUID.fromString(iid));
            } catch (IllegalArgumentException ignored) {
            }
        }
        p.setGuildId(sec.getString("social.guild", null));

        boolean partyChat = sec.contains("social.party-chat")
            ? sec.getBoolean("social.party-chat", false)
            : sec.getBoolean("settings.party-chat", false);
        p.setPartyChatEnabled(partyChat);

        st.setLanguage(sec.getString("settings.language", "en"));
        st.setProfanityFilter(sec.getBoolean("settings.profanity-filter", true));
        st.setPartyChat(partyChat);
        st.setShowStatusInTab(sec.getBoolean("settings.show-status-in-tab", true));
        st.setShowQuestNotifications(sec.getBoolean("settings.show-quest-notifications", true));

        p.setTutorialCompleted(sec.getBoolean("progression.tutorial-completed", false));
        p.setTutorialStep(sec.getInt("progression.tutorial-step", 0));
        p.setDailyBoardRefreshMillis(sec.getLong("progression.daily-board-refresh", 0L));
        p.setWeeklyBoardRefreshMillis(sec.getLong("progression.weekly-board-refresh", 0L));
        p.setContractBoardRefreshMillis(sec.getLong("progression.contract-board-refresh", 0L));
        p.setRankedMatches(sec.getInt("progression.ranked-matches", 0));
        p.setCasualMatches(sec.getInt("progression.casual-matches", 0));
        p.setPartyMatches(sec.getInt("progression.party-matches", 0));

        p.replaceQuestBoard(p.dailyQuests(), fr.bosketball.model.QuestCodec.decodeList(
            sec.getStringList("progression.daily-quests")));
        p.replaceQuestBoard(p.weeklyQuests(), fr.bosketball.model.QuestCodec.decodeList(
            sec.getStringList("progression.weekly-quests")));
        p.replaceQuestBoard(p.contracts(), fr.bosketball.model.QuestCodec.decodeList(
            sec.getStringList("progression.contracts")));

        List<Achievement> unlockedAchievements = new ArrayList<>();
        for (String an : sec.getStringList("progression.achievements")) {
            Achievement a = Achievement.byNameOrNull(an);
            if (a != null) {
                unlockedAchievements.add(a);
            }
        }
        p.setUnlockedAchievements(unlockedAchievements);
        p.setDailyLoginStreak(sec.getInt("progression.daily-login-streak", 0));
        p.setLastDailyStreakClaimMillis(sec.getLong("progression.last-daily-claim", 0L));
        p.setTotalCoinsEarned(sec.getLong("progression.total-coins-earned", 0L));

        p.setChatWarnPoints(sec.getInt("moderation.warn-points", 0));
        p.setChatMuteUntilMillis(sec.getLong("moderation.muted-until", 0L));
        p.setDodgeCount(sec.getInt("moderation.dodge-count", 0));
        p.setMatchmakingBanUntilMillis(sec.getLong("moderation.matchmaking-ban-until", 0L));
        p.setDailyCratesOpened(sec.getInt("economy.daily-crates-opened", 0));
        p.setCrateDayEpoch(sec.getLong("economy.crate-day-epoch", 0L));

        List<MatchHistoryEntry> hist = new ArrayList<>();
        List<Map<?, ?>> mapList = sec.getMapList("recent-matches");
        if (!mapList.isEmpty()) {
            for (Map<?, ?> m : mapList) {
                MatchHistoryEntry entry = MatchHistoryEntry.fromYamlMap(m);
                if (entry != null) {
                    hist.add(entry);
                }
            }
        } else {
            for (String rawH : sec.getStringList("history")) {
                MatchHistoryEntry entry = MatchHistoryEntry.deserializeCompact(rawH);
                if (entry != null) {
                    hist.add(entry);
                }
            }
        }
        p.replaceMatchHistory(hist);
        return p;
    }

    private void writePlayerSnapshotToDisk(PlayerSnapshot s) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("schema-version", 2);

        yml.set("identity.username", s.username());
        if (s.staffRank() != null) {
            yml.set("identity.staff-rank", s.staffRank());
        }
        yml.set("identity.playtime", s.formattedPlaytime());
        yml.set("identity.playtime-seconds", s.playTimeSeconds());
        if (s.skinTextureValue() != null && !s.skinTextureValue().isBlank()) {
            yml.set("identity.skin-texture-value", s.skinTextureValue());
            if (s.skinTextureSignature() != null && !s.skinTextureSignature().isBlank()) {
                yml.set("identity.skin-texture-signature", s.skinTextureSignature());
            }
            yml.set("identity.skin-texture-updated-at", s.skinTextureUpdatedAtMillis());
        }

        yml.set("economy.coins", s.coins());
        yml.set("economy.elo", s.elo());
        yml.set("economy.xp", s.xp());

        yml.set("cosmetics.equipped-tag", s.equippedTag());
        yml.set("cosmetics.equipped-ball", s.equippedBall());
        yml.set("cosmetics.equipped-dunk", s.equippedDunk());
        yml.set("cosmetics.unlocked-tags", s.unlockedTags());
        yml.set("cosmetics.unlocked-balls", s.unlockedBalls());
        yml.set("cosmetics.unlocked-dunks", s.unlockedDunks());

        yml.set("stats.wins", s.wins());
        yml.set("stats.losses", s.losses());
        yml.set("stats.win-streak", s.winStreak());
        yml.set("stats.best-win-streak", s.bestWinStreak());
        yml.set("stats.mvps", s.mvps());
        yml.set("stats.points", s.points());
        yml.set("stats.dunks", s.dunks());
        yml.set("stats.assists", s.assists());
        yml.set("stats.rebounds", s.rebounds());
        yml.set("stats.steals", s.steals());
        yml.set("stats.blocks", s.blocks());
        yml.set("stats.ankle-breakers", s.ankleBreakers());
        yml.set("stats.two-pointers.made", s.twoMade());
        yml.set("stats.two-pointers.attempted", s.twoAttempted());
        yml.set("stats.three-pointers.made", s.threeMade());
        yml.set("stats.three-pointers.attempted", s.threeAttempted());
        yml.set("stats.three-point-contest-best", s.threePointContestBest());

        yml.set("settings.custom-sounds", s.customSounds());
        yml.set("settings.aim-trajectory", s.aimTrajectory());
        yml.set("settings.floor-rings", s.floorRings());
        yml.set("settings.overhead-nameplates", s.overheadNameplates());
        yml.set("settings.self-rig-f5", s.selfRigF5());
        yml.set("settings.custom-animations", s.customAnimations());
        yml.set("settings.chat-announcements", s.chatAnnouncements());
        yml.set("settings.cinematic-replay-cam", s.cinematicReplayCam());
        yml.set("settings.party-invites", s.partyInvites());

        yml.set("social.unread-mail", s.unreadMail());
        yml.set("social.friends", s.friends());
        yml.set("social.ignored", s.ignoredPlayers());
        if (s.guildId() != null && !s.guildId().isBlank()) {
            yml.set("social.guild", s.guildId());
        }
        yml.set("social.party-chat", s.partyChatEnabled());

        yml.set("settings.language", s.language());
        yml.set("settings.profanity-filter", s.profanityFilter());

        yml.set("settings.party-chat", s.partyChatEnabled());
        yml.set("settings.show-status-in-tab", s.showStatusInTab());
        yml.set("settings.show-quest-notifications", s.showQuestNotifications());

        yml.set("progression.tutorial-completed", s.tutorialCompleted());
        yml.set("progression.tutorial-step", s.tutorialStep());
        yml.set("progression.daily-board-refresh", s.dailyBoardRefresh());
        yml.set("progression.weekly-board-refresh", s.weeklyBoardRefresh());
        yml.set("progression.contract-board-refresh", s.contractBoardRefresh());
        yml.set("progression.daily-quests", s.dailyQuests());
        yml.set("progression.weekly-quests", s.weeklyQuests());
        yml.set("progression.contracts", s.contracts());
        yml.set("progression.achievements", s.achievements());
        yml.set("progression.daily-login-streak", s.dailyLoginStreak());
        yml.set("progression.last-daily-claim", s.lastDailyClaim());
        yml.set("progression.total-coins-earned", s.totalCoinsEarned());

        yml.set("progression.ranked-matches", s.rankedMatches());
        yml.set("progression.casual-matches", s.casualMatches());
        yml.set("progression.party-matches", s.partyMatches());

        yml.set("moderation.warn-points", s.chatWarnPoints());
        yml.set("moderation.muted-until", s.chatMuteUntil());
        yml.set("moderation.dodge-count", s.dodgeCount());
        yml.set("moderation.matchmaking-ban-until", s.matchmakingBanUntil());
        yml.set("economy.daily-crates-opened", s.dailyCratesOpened());
        yml.set("economy.crate-day-epoch", s.crateDayEpoch());

        List<Map<String, Object>> historyMaps = new ArrayList<>(s.recentMatches().size());
        for (MatchHistoryEntry entry : s.recentMatches()) {
            historyMaps.add(entry.toYamlMap());
        }
        yml.set("recent-matches", historyMaps);

        String header = "# ===========================================================================\n"
            + "#   BosketBall Player Profile — " + s.username() + "\n"
            + "#   UUID: " + s.uuid() + "\n"
            + "# ===========================================================================\n";
        File target = new File(playersDir, s.uuid() + ".yml");
        AtomicYamlWriter.writeYamlAtomically(target, header, yml, logger);
    }

    private Map<String, String> captureNameIndexSnapshot() {
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (PlayerProfile p : profiles.values()) {
            sorted.put(p.lastKnownName(), p.uuid().toString());
        }
        return sorted;
    }

    private void writeNameIndexToDisk(Map<String, String> sortedIndex) {
        if (sortedIndex == null) {
            return;
        }

        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, String> e : sortedIndex.entrySet()) {
            yml.set("players." + e.getKey(), e.getValue());
        }
        String header = "# ===========================================================================\n"
            + "#   BosketBall — Player Username -> UUID Directory Index\n"
            + "#   Use this file to quickly locate which <uuid>.yml belongs to a player.\n"
            + "# ===========================================================================\n";
        AtomicYamlWriter.writeYamlAtomically(nameIndexFile, header, yml, logger);
    }
}
