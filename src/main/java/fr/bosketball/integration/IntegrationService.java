package fr.bosketball.integration;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.GameQueueMode;
import fr.bosketball.model.TeamSide;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class IntegrationService {

    private final BosketBallPlugin plugin;
    private final DiscordWebhook webhook;
    private final MatchReporter reporter;
    private final fr.bosketball.storage.StorageService storage;

    private final Map<String, Map<UUID, Integer>> pendingEloDeltas = new HashMap<>();
    /** When each pending session was last written to; used to prune the ones that never finished. */
    private final Map<String, Long> pendingEloSince = new HashMap<>();

    /** A session that has not finished after 6 h cannot still be running: drop its deltas. */
    private static final long PENDING_ELO_MAX_AGE_MILLIS = 6L * 60L * 60L * 1000L;

    public IntegrationService(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.webhook = new DiscordWebhook(plugin.getLogger());
        this.reporter = new MatchReporter(webhook, plugin.statsManager());
        this.storage = new fr.bosketball.storage.StorageService(
            plugin.getDataFolder(), plugin.statsManager(), plugin.getLogger());
    }

    public DiscordWebhook webhook() {
        return webhook;
    }

    public MatchReporter reporter() {
        return reporter;
    }

    public fr.bosketball.storage.StorageService storage() {
        return storage;
    }

    public void reload() {
        var discord = plugin.settings().discord();
        webhook.configure(discord.enabled(), discord.webhookUrl());
        storage.start(plugin.settings().storage());
    }

    public void shutdown() {
        storage.stop();
        webhook.shutdown();
    }

    public void onMatchLaunched(MatchSession session) {
        if (session == null) {
            return;
        }
        pendingEloDeltas.remove(sessionKey(session));
        pendingEloSince.remove(sessionKey(session));
        prunePendingDeltas();
        if (!plugin.settings().discord().postMatchStart()) {
            return;
        }
        reporter.matchStarted(session, session.queueMode());
    }

    public void onScoreUpdate(MatchSession session, String headline) {
        if (session == null || !plugin.settings().discord().postScoreUpdates()) {
            return;
        }
        reporter.scoreUpdate(session, headline);
    }

    public void rememberEloDelta(MatchSession session, UUID playerId, int delta) {
        if (session == null || playerId == null) {
            return;
        }
        pendingEloDeltas
            .computeIfAbsent(sessionKey(session), k -> new java.util.LinkedHashMap<>())
            .put(playerId, delta);
        pendingEloSince.put(sessionKey(session), System.currentTimeMillis());
    }

    public void onMatchFinished(MatchSession session, TeamSide winner, UUID mvpId, GameQueueMode queueMode) {
        if (session == null) {
            return;
        }
        Map<UUID, Integer> deltas = pendingEloDeltas.remove(sessionKey(session));
        pendingEloSince.remove(sessionKey(session));
        boolean showElo = queueMode != GameQueueMode.CASUAL
            && plugin.settings().discord().postMatchResults();
        reporter.matchFinished(session, winner, mvpId, queueMode,
            showElo ? deltas : Map.of(), session.elapsedSeconds(), showElo);

        storage.requestSync();
    }

    public void onRankUp(Player player, String oldRank, String newRank, int elo) {
        if (player == null || !plugin.settings().discord().postRankUps()) {
            return;
        }
        reporter.rankUp(player.getName(), oldRank, newRank, elo);
    }

    public void onRankDemotion(Player player, String oldRank, String newRank, int elo) {
        if (player == null || !plugin.settings().discord().postRankUps()) {
            return;
        }
        reporter.rankDown(player.getName(), oldRank, newRank, elo);
    }

    public void onCrateOpened(Player player, String rewardLabel, String rarity, int cost, int valueCoins) {
        if (player == null || !plugin.settings().discord().postCrateOpenings()) {
            return;
        }
        reporter.crateOpened(player.getName(), rewardLabel, rarity, cost, valueCoins);
    }

    public void onModeration(String playerName, String action, String reason, int warnPoints) {
        if (!plugin.settings().discord().postModerationActions()) {
            return;
        }
        reporter.moderationAction(playerName, action, reason, warnPoints);
    }

    public void onReport(String reporterName, String reportedName, String reason) {
        if (!plugin.settings().discord().postReports()) {
            return;
        }
        reporter.reportFiled(reporterName, reportedName, reason,
            plugin.settings().general().serverDomain());
    }

    /** Keeps the pending-ELO map from leaking sessions that never finished (crash/reload). */
    /**
     * Drops the ELO deltas of sessions that will never finish (crash, reload, abandoned court).
     *
     * <p>Round 42: the old guard cleared the <b>whole</b> map as soon as it held 64 entries, which
     * also threw away the deltas of the matches still being played — their Discord recap then
     * announced a 0 ELO swing. Entries are now dropped by age, so a live match always keeps its
     * numbers, and an empty per-session map is removed immediately.
     */
    private void prunePendingDeltas() {
        if (pendingEloDeltas.isEmpty()) {
            pendingEloSince.clear();
            return;
        }
        long now = System.currentTimeMillis();
        pendingEloDeltas.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
        pendingEloSince.entrySet().removeIf(e -> {
            long since = e.getValue() == null ? 0L : e.getValue();
            boolean stale = since <= 0L || now - since > PENDING_ELO_MAX_AGE_MILLIS;
            if (stale) {
                pendingEloDeltas.remove(e.getKey());
            }
            return stale;
        });
        // A session id present in one map but not the other (defensive): keep both in sync.
        pendingEloDeltas.keySet().removeIf(id -> !pendingEloSince.containsKey(id));
    }

    private String sessionKey(MatchSession session) {
        // Round 27: identity hashes collide and can never be logged; use the stable match id.
        return session.matchId();
    }
}
