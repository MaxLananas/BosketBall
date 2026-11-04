package fr.bosketball.integration;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.GameQueueMode;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class IntegrationService {

    private final BosketBallPlugin plugin;
    private final DiscordWebhook webhook;
    private final MatchReporter reporter;
    private final fr.bosketball.storage.StorageService storage;

    private final Map<String, Map<UUID, Integer>> pendingEloDeltas = new HashMap<>();

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
    }

    public void onMatchFinished(MatchSession session, TeamSide winner, UUID mvpId, GameQueueMode queueMode) {
        if (session == null) {
            return;
        }
        Map<UUID, Integer> deltas = pendingEloDeltas.remove(sessionKey(session));
        if (queueMode == GameQueueMode.CASUAL || !plugin.settings().discord().postMatchResults()) {
            deltas = Map.of();
        }
        reporter.matchFinished(session, winner, mvpId, queueMode, deltas, session.elapsedSeconds());

        storage.requestSync();
    }

    public void onRankUp(Player player, String oldRank, String newRank, int elo) {
        if (player == null || !plugin.settings().discord().postRankUps()) {
            return;
        }
        reporter.rankUp(player.getName(), oldRank, newRank, elo);
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

    private String sessionKey(MatchSession session) {
        return Integer.toHexString(System.identityHashCode(session));
    }
}
