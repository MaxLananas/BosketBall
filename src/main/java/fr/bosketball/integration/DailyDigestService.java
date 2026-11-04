package fr.bosketball.integration;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;

public final class DailyDigestService {

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final BosketBallPlugin plugin;

    private int taskId = -1;

    public DailyDigestService(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (plugin.settings() == null || !plugin.settings().discord().dailyReportEnabled()) {
            return;
        }
        int hour = Math.max(0, Math.min(23, plugin.settings().discord().dailyReportHour()));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1L);
        }
        long delayTicks = Math.max(20L, Duration.between(now, next).getSeconds() * 20L);
        long periodTicks = 20L * 60L * 60L * 24L;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin,
            () -> Bukkit.getScheduler().runTaskAsynchronously(plugin, this::sendNow),
            delayTicks, periodTicks).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public boolean sendNow() {
        if (plugin.integration() == null) {
            return false;
        }
        var reporter = plugin.integration().reporter();
        if (reporter == null || !reporter.isEnabled()) {
            return false;
        }
        StatsManager stats = plugin.statsManager();
        if (stats == null) {
            return false;
        }
        int online = Bukkit.getOnlinePlayers().size();
        List<PlayerProfile> profiles = new ArrayList<>(stats.allProfiles());
        long now = System.currentTimeMillis();
        long windowStart = now - DAY_MILLIS;

        int matches24h = 0;
        long coinsInCirculation = 0L;
        for (PlayerProfile profile : profiles) {
            coinsInCirculation += profile.coins();
            for (MatchHistoryEntry entry : profile.matchHistory()) {
                if (entry.timestampEpochMillis() >= windowStart) {
                    matches24h++;
                }
            }
        }

        int openReports = plugin.reportService() != null ? plugin.reportService().openReports().size() : 0;
        int quarantined = stats.quarantinedProfiles();

        List<String> topElo = new ArrayList<>();
        int rank = 1;
        for (PlayerProfile profile : stats.topBy(StatsManager.LeaderboardCategory.ELO, 3)) {
            topElo.add(rank + ". **" + profile.lastKnownName() + "** — " + profile.elo() + " ELO");
            rank++;
        }
        List<String> topWins = new ArrayList<>();
        rank = 1;
        for (PlayerProfile profile : stats.topBy(StatsManager.LeaderboardCategory.WINS, 3)) {
            topWins.add(rank + ". **" + profile.lastKnownName() + "** — " + profile.wins() + "W");
            rank++;
        }

        int tpsRaw = (int) Math.round(Bukkit.getTPS()[0] * 100.0);
        List<String> blocks = new ArrayList<>();
        blocks.add("**Population**\n"
            + "Accounts: `" + profiles.size() + "`\n"
            + "Online now: `" + online + "`\n"
            + "TPS: `" + String.format(Locale.ROOT, "%.2f", tpsRaw / 100.0) + "`");
        blocks.add("**Activity (24h)**\n"
            + "Matches played: `" + matches24h + "`\n"
            + "Coins in circulation: `" + String.format(Locale.ROOT, "%,d", coinsInCirculation) + "`");
        blocks.add("**Top ELO**\n" + (topElo.isEmpty() ? "*no ranked data yet*" : String.join("\n", topElo)));
        blocks.add("**Top Wins**\n" + (topWins.isEmpty() ? "*no matches yet*" : String.join("\n", topWins)));
        blocks.add("**Incidents**\n"
            + "Open reports: `" + openReports + "`\n"
            + "Quarantined profiles: `" + quarantined + "`");

        reporter.sendDailyDigest(blocks);
        return true;
    }
}
