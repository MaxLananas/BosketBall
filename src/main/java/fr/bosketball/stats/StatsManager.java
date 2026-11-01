package fr.bosketball.stats;

import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Thread-safe Career Statistics, ELO Rating & Real-Time Leaderboard Placement Manager.
 * Powers the live [🏆#1] [⭐#2] [🏀#1] chat badges inspired by top competitive sports servers!
 */
public final class StatsManager {

    public enum LeaderboardCategory {
        ELO("ᴄʟᴀꜱꜱᴇᴍᴇɴᴛ ᴇʟᴏ"),
        WINS("ᴠɪᴄᴛᴏɪʀᴇꜱ"),
        POINTS("ᴘᴏɪɴᴛꜱ ᴍᴀʀǫᴜᴇꜱ"),
        DUNKS("ꜱʟᴀᴍ ᴅᴜɴᴋꜱ"),
        THREE_POINTERS("ᴘᴀɴɪᴇʀꜱ ᴀ 3 ᴘᴛꜱ");

        private final String titleSmallCaps;

        LeaderboardCategory(String titleSmallCaps) {
            this.titleSmallCaps = titleSmallCaps;
        }

        public String titleSmallCaps() {
            return titleSmallCaps;
        }
    }

    public record LeaderboardBadges(int winsPlace, int eloPlace, int pointsPlace) {
        public boolean hasAny() {
            return winsPlace > 0 || eloPlace > 0 || pointsPlace > 0;
        }
    }

    private final File storageFile;
    private final Logger logger;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, LeaderboardBadges> badgeCache = new ConcurrentHashMap<>();

    public StatsManager(File dataFolder, Logger logger) {
        this.storageFile = new File(dataFolder, "players.yml");
        this.logger = logger;
    }

    public PlayerProfile getOrCreate(Player player) {
        PlayerProfile profile = profiles.computeIfAbsent(
            player.getUniqueId(),
            id -> new PlayerProfile(id, player.getName())
        );
        profile.setLastKnownName(player.getName());
        return profile;
    }

    public PlayerProfile getOrCreate(UUID uuid, String fallbackName) {
        return profiles.computeIfAbsent(uuid, id -> new PlayerProfile(id, fallbackName));
    }

    public PlayerProfile getIfPresent(UUID uuid) {
        return profiles.get(uuid);
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
        };
        List<PlayerProfile> sorted = new ArrayList<>(profiles.values());
        sorted.sort(cmp.reversed());
        return sorted.size() <= limit ? sorted : sorted.subList(0, limit);
    }

    /**
     * Rebuilds the fast O(1) Top-3 placement badge cache for Wins (🏆), ELO (⭐) and Points (🏀).
     */
    public void refreshLeaderboardBadges() {
        List<PlayerProfile> topWins = topBy(LeaderboardCategory.WINS, 3);
        List<PlayerProfile> topElo = topBy(LeaderboardCategory.ELO, 3);
        List<PlayerProfile> topPts = topBy(LeaderboardCategory.POINTS, 3);

        Map<UUID, Integer> wMap = new ConcurrentHashMap<>();
        Map<UUID, Integer> eMap = new ConcurrentHashMap<>();
        Map<UUID, Integer> pMap = new ConcurrentHashMap<>();

        for (int i = 0; i < topWins.size(); i++) {
            if (topWins.get(i).wins() > 0) {
                wMap.put(topWins.get(i).uuid(), i + 1);
            }
        }
        for (int i = 0; i < topElo.size(); i++) {
            if (topElo.get(i).elo() > 1000 || topElo.get(i).wins() > 0) {
                eMap.put(topElo.get(i).uuid(), i + 1);
            }
        }
        for (int i = 0; i < topPts.size(); i++) {
            if (topPts.get(i).points() > 0) {
                pMap.put(topPts.get(i).uuid(), i + 1);
            }
        }

        badgeCache.clear();
        for (UUID uuid : profiles.keySet()) {
            int w = wMap.getOrDefault(uuid, 0);
            int e = eMap.getOrDefault(uuid, 0);
            int p = pMap.getOrDefault(uuid, 0);
            if (w > 0 || e > 0 || p > 0) {
                badgeCache.put(uuid, new LeaderboardBadges(w, e, p));
            }
        }
    }

    public LeaderboardBadges getBadges(UUID uuid) {
        return badgeCache.getOrDefault(uuid, new LeaderboardBadges(0, 0, 0));
    }

    /**
     * Standard Chess/Esports ELO delta (K=32) for competitive match outcomes.
     */
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
                profiles.put(uuid, p);
            } catch (IllegalArgumentException ignored) {
            }
        }
        refreshLeaderboardBadges();
    }

    public void save() {
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
        }
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(storageFile);
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("Failed to save players.yml: " + e.getMessage());
            }
        }
    }
}
