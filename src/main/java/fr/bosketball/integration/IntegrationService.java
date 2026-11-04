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

/**
 * Facade wiring the plugin to the outside world: Discord feeds, the SQL mirror and the
 * rotating backups. Keeping this in one place means {@code MatchSession} and
 * {@code GameManager} only ever call three high-level methods.
 */
public final class IntegrationService {

    private final BosketBallPlugin plugin;
    private final DiscordWebhook webhook;
    private final MatchReporter reporter;
    private final fr.bosketball.storage.StorageService storage;

    /** Tracks per-match ELO deltas so the final Discord embed can show them. */
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

    /** Applies the {@code discord:} &amp; {@code storage:} config sections. */
    public void reload() {
        var discord = plugin.settings().discord();
        webhook.configure(discord.enabled(), discord.webhookUrl());
        storage.start(plugin.settings().storage());
    }

    public void shutdown() {
        storage.stop();
        webhook.shutdown();
    }

    // =========================================================================
    //  MATCH LIFECYCLE
    // =========================================================================

    /** Called when a confirmed roster lands on a court. */
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

    /** Called once per quarter / on big swings. */
    public void onScoreUpdate(MatchSession session, String headline) {
        if (session == null || !plugin.settings().discord().postScoreUpdates()) {
            return;
        }
        reporter.scoreUpdate(session, headline);
    }

    /** Records the ELO deltas computed by the session so the final report is complete. */
    public void rememberEloDelta(MatchSession session, UUID playerId, int delta) {
        if (session == null || playerId == null) {
            return;
        }
        pendingEloDeltas
            .computeIfAbsent(sessionKey(session), k -> new java.util.LinkedHashMap<>())
            .put(playerId, delta);
    }

    /** Called when a match is fully over. */
    public void onMatchFinished(MatchSession session, TeamSide winner, UUID mvpId, GameQueueMode queueMode) {
        if (session == null) {
            return;
        }
        Map<UUID, Integer> deltas = pendingEloDeltas.remove(sessionKey(session));
        if (queueMode == GameQueueMode.CASUAL || !plugin.settings().discord().postMatchResults()) {
            deltas = Map.of();
        }
        reporter.matchFinished(session, winner, mvpId, queueMode, deltas, session.elapsedSeconds());
        // Mirror to SQL right after a match so external dashboards are always fresh
        try {
            storage.syncNow();
        } catch (Throwable ignored) {
        }
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
