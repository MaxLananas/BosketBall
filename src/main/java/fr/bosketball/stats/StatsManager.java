package fr.bosketball.stats;

import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerSettings;
import fr.bosketball.model.Rank;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Thread-safe Career Statistics, ELO Rating, Match History, Friends, Playtime & 6-Category Glossy Top-3 Badge Manager!
 * Powers the live [🏆#1] [👑#1] [⭐#1] [🪽#1] [🏀#1] [🔥#1] chat badges matching image-5.png!
 */
public final class StatsManager {

    public enum LeaderboardCategory {
        ELO("ᴄʟᴀꜱꜱᴇᴍᴇɴᴛ ᴇʟᴏ"),
        WINS("ᴠɪᴄᴛᴏɪʀᴇꜱ"),
        POINTS("ᴘᴏɪɴᴛꜱ ᴍᴀʀǫᴜᴇꜱ"),
        DUNKS("ꜱʟᴀᴍ ᴅᴜɴᴋꜱ"),
        THREE_POINTERS("ᴘᴀɴɪᴇʀꜱ ᴀ 3 ᴘᴛꜱ"),
        MVPS("ᴛʀᴏᴘʜᴇᴇꜱ ᴍᴠᴘ"),
        ASSISTS("ᴘᴀꜱꜱᴇꜱ ᴅᴇᴄɪꜱɪᴠᴇꜱ");

        private final String titleSmallCaps;

        LeaderboardCategory(String titleSmallCaps) {
            this.titleSmallCaps = titleSmallCaps;
        }

        public String titleSmallCaps() {
            return titleSmallCaps;
        }

        public String label() {
            return titleSmallCaps;
        }

        public int extract(PlayerProfile p) {
            return switch (this) {
                case ELO -> p.elo();
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

    private final File storageFile;
    private final Logger logger;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, LeaderboardBadges> badgeCache = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BosketBall-StatsIO");
        t.setDaemon(true);
        return t;
    });

    public StatsManager(File dataFolder, Logger logger) {
        this.storageFile = new File(dataFolder, "players.yml");
        this.logger = logger;
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
        profile.setLastKnownName(player.getName());
        if (isNew) {
            refreshLeaderboardBadges();
        }
        return profile;
    }

    public PlayerProfile getOrCreate(UUID uuid, String fallbackName) {
        return profiles.computeIfAbsent(uuid, id -> new PlayerProfile(id, fallbackName));
    }

    public PlayerProfile getIfPresent(UUID uuid) {
        return profiles.get(uuid);
    }

    public PlayerProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public void saveProfileAsync(PlayerProfile profile) {
        if (profile != null) {
            profiles.put(profile.uuid(), profile);
        }
        saveAsync();
    }

    public void saveAsync() {
        String yamlSnapshot = buildYamlSnapshot().saveToString();
        if (ioExecutor.isShutdown()) {
            writeAtomically(yamlSnapshot);
            return;
        }
        ioExecutor.execute(() -> writeAtomically(yamlSnapshot));
    }

    public PlayerProfile findByNameIgnoreCase(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (PlayerProfile p : profiles.values()) {
            if (p.lastKnownName().equalsIgnoreCase(name.trim())) {
                return p;
            }
        }
        return null;
    }

    public List<PlayerProfile> topBy(LeaderboardCategory category, int limit) {
        Comparator<PlayerProfile> cmp = switch (category) {
            case ELO -> Comparator.comparingInt(PlayerProfile::elo)
                .thenComparingInt(PlayerProfile::wins);
            case WINS -> Comparator.comparingInt(PlayerProfile::wins)
                .thenComparingInt(PlayerProfile::elo);
            case POINTS -> Comparator.comparingInt(PlayerProfile::points)
                .thenComparingInt(PlayerProfile::wins);
            case DUNKS -> Comparator.comparingInt(PlayerProfile::dunks)
                .thenComparingInt(PlayerProfile::points);
            case THREE_POINTERS -> Comparator.comparingInt(PlayerProfile::threePointersMade)
                .thenComparingInt(PlayerProfile::points);
            case MVPS -> Comparator.comparingInt(PlayerProfile::mvps)
                .thenComparingInt(PlayerProfile::wins);
            case ASSISTS -> Comparator.comparingInt(PlayerProfile::assists)
                .thenComparingInt(PlayerProfile::points);
        };
        List<PlayerProfile> sorted = new ArrayList<>(profiles.values());
        sorted.sort(cmp.reversed());
        return sorted.size() <= limit ? sorted : sorted.subList(0, limit);
    }

    /**
     * Rebuilds the fast O(1) Top-3 placement badge cache for all 6 image-5.png categories:
     * Trophy (Wins), Crown (ELO), Star (MVPs), Wings (Assists), Ball (Points), Fire (Dunks)!
     */
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
        if (!storageFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection sec = root.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                PlayerProfile p = new PlayerProfile(uuid, sec.getString("name", "Player"));
                p.setPoints(sec.getInt("points", 0));
                p.setTwoPointersMade(sec.getInt("2pm", 0));
                p.setTwoPointersAttempted(sec.getInt("2pa", 0));
                p.setThreePointersMade(sec.getInt("3pm", 0));
                p.setThreePointersAttempted(sec.getInt("3pa", 0));
                p.setDunks(sec.getInt("dunks", 0));
                p.setAssists(sec.getInt("assists", 0));
                p.setRebounds(sec.getInt("rebounds", 0));
                p.setSteals(sec.getInt("steals", 0));
                p.setBlocks(sec.getInt("blocks", 0));
                p.setAnkleBreakers(sec.getInt("ankle-breakers", 0));
                p.setWins(sec.getInt("wins", 0));
                p.setLosses(sec.getInt("losses", 0));
                p.setMvps(sec.getInt("mvps", 0));
                p.updateThreePointContestBest(sec.getInt("3pt-best", 0));
                p.setElo(sec.getInt("elo", 1000));
                p.setXp(sec.getInt("xp", 0));
                p.setWinStreak(sec.getInt("streak", 0));
                p.setBestWinStreak(sec.getInt("best-streak", 0));
                p.setStaffRankOverride(Rank.byNameOrNull(sec.getString("staff-rank", null)));
                p.setSelectedBallSkin(BallSkin.byNameOrDefault(sec.getString("ball-skin"), BallSkin.CLASSIC));
                p.setSelectedDunkStyle(DunkStyle.byNameOrDefault(sec.getString("dunk-style"), DunkStyle.TOMAHAWK));

                List<String> rawTagList = sec.getStringList("chat-tags");
                if (!rawTagList.isEmpty()) {
                    List<ChatTag> parsedTags = new ArrayList<>();
                    for (String rt : rawTagList) {
                        ChatTag ct = ChatTag.byNameOrNull(rt);
                        if (ct != null) {
                            parsedTags.add(ct);
                        }
                    }
                    p.setEquippedTags(parsedTags);
                } else {
                    String rawTag = sec.getString("chat-tag", "AURA");
                    p.setSelectedTag("NONE".equalsIgnoreCase(rawTag) ? null : ChatTag.byNameOrNull(rawTag));
                }

                p.setPlayTimeSeconds(sec.getLong("playtime-sec", 0L));
                p.setUnreadMailCount(sec.getInt("unread-mail", 1));

                PlayerSettings st = p.settings();
                st.setCustomSounds(sec.getBoolean("settings.sounds", true));
                st.setAimTrajectory(sec.getBoolean("settings.aim-arc", true));
                st.setTeamFloorRings(sec.getBoolean("settings.floor-rings", true));
                st.setOverheadNameplates(sec.getBoolean("settings.nameplates", true));
                st.setSelfBodyRigInF5(sec.getBoolean("settings.self-rig-f5", true));
                st.setCustomAnimations(sec.getBoolean("settings.custom-animations", true));
                st.setChatAnnouncements(sec.getBoolean("settings.announcements", true));
                st.setCinematicScoreCam(sec.getBoolean("settings.cinematic-cam", true));
                st.setPartyInvites(sec.getBoolean("settings.party-invites", true));

                for (String fid : sec.getStringList("friends")) {
                    try {
                        p.addFriend(UUID.fromString(fid));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                List<MatchHistoryEntry> hist = new ArrayList<>();
                for (String rawH : sec.getStringList("history")) {
                    MatchHistoryEntry entry = MatchHistoryEntry.deserializeCompact(rawH);
                    if (entry != null) {
                        hist.add(entry);
                    }
                }
                p.replaceMatchHistory(hist);

                profiles.put(uuid, p);
            } catch (IllegalArgumentException ignored) {
            }
        }
        refreshLeaderboardBadges();
    }

    public void save() {
        writeAtomically(buildYamlSnapshot().saveToString());
    }

    public void shutdownAndSaveSync() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        save();
    }

    private YamlConfiguration buildYamlSnapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerProfile p : profiles.values()) {
            String path = "players." + p.uuid() + ".";
            yaml.set(path + "name", p.lastKnownName());
            yaml.set(path + "points", p.points());
            yaml.set(path + "2pm", p.twoPointersMade());
            yaml.set(path + "2pa", p.twoPointersAttempted());
            yaml.set(path + "3pm", p.threePointersMade());
            yaml.set(path + "3pa", p.threePointersAttempted());
            yaml.set(path + "dunks", p.dunks());
            yaml.set(path + "assists", p.assists());
            yaml.set(path + "rebounds", p.rebounds());
            yaml.set(path + "steals", p.steals());
            yaml.set(path + "blocks", p.blocks());
            yaml.set(path + "ankle-breakers", p.ankleBreakers());
            yaml.set(path + "wins", p.wins());
            yaml.set(path + "losses", p.losses());
            yaml.set(path + "mvps", p.mvps());
            yaml.set(path + "3pt-best", p.threePointContestBest());
            yaml.set(path + "elo", p.elo());
            yaml.set(path + "xp", p.xp());
            yaml.set(path + "streak", p.winStreak());
            yaml.set(path + "best-streak", p.bestWinStreak());
            yaml.set(path + "staff-rank", p.staffRankOverride() == null ? null : p.staffRankOverride().name());
            yaml.set(path + "ball-skin", p.selectedBallSkin().name());
            yaml.set(path + "dunk-style", p.selectedDunkStyle().name());
            yaml.set(path + "chat-tag", p.selectedTag() == null ? "NONE" : p.selectedTag().name());
            yaml.set(path + "chat-tags", p.equippedTags().stream().map(ChatTag::name).toList());
            yaml.set(path + "playtime-sec", p.playTimeSeconds());
            yaml.set(path + "unread-mail", p.unreadMailCount());

            PlayerSettings st = p.settings();
            yaml.set(path + "settings.sounds", st.customSounds());
            yaml.set(path + "settings.aim-arc", st.aimTrajectory());
            yaml.set(path + "settings.floor-rings", st.teamFloorRings());
            yaml.set(path + "settings.nameplates", st.overheadNameplates());
            yaml.set(path + "settings.self-rig-f5", st.selfBodyRigInF5());
            yaml.set(path + "settings.custom-animations", st.customAnimations());
            yaml.set(path + "settings.announcements", st.chatAnnouncements());
            yaml.set(path + "settings.cinematic-cam", st.cinematicScoreCam());
            yaml.set(path + "settings.party-invites", st.partyInvites());

            yaml.set(path + "friends", p.friends().stream().map(UUID::toString).toList());
            yaml.set(path + "history", p.matchHistory().stream().map(MatchHistoryEntry::serializeCompact).toList());
        }
        return yaml;
    }

    private synchronized void writeAtomically(String yamlContent) {
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(storageFile.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), yamlContent, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFallback) {
                Files.move(tmp.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Failed to save players.yml: " + e.getMessage());
            }
        }
    }
}
