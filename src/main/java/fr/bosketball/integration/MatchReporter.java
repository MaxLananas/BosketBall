package fr.bosketball.integration;

import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.GameQueueMode;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class MatchReporter {

    public static final int COLOR_IGNIS = 0xFF5C4D;
    public static final int COLOR_FROST = 0x38BDF8;
    public static final int COLOR_GOLD = 0xFFD43B;
    public static final int COLOR_GREEN = 0x4ADE80;
    public static final int COLOR_RED = 0xF87171;
    public static final int COLOR_NEUTRAL = 0xFF8C1A;

    private final DiscordWebhook webhook;
    private final StatsManager statsManager;

    public MatchReporter(DiscordWebhook webhook, StatsManager statsManager) {
        this.webhook = webhook;
        this.statsManager = statsManager;
    }

    public boolean isEnabled() {
        return webhook != null && webhook.isEnabled();
    }

    public void matchStarted(MatchSession session, GameQueueMode queueMode) {
        if (!isEnabled() || session == null) {
            return;
        }
        GameModeType mode = session.mode();
        String queueLabel = queueMode != null && queueMode != GameQueueMode.RANKED ? " · CASUAL" : " · RANKED";

        StringBuilder rosters = new StringBuilder();
        rosters.append("**🔥 IGNIS**\n");
        for (UUID id : sortedRoster(session, TeamSide.IGNIS)) {
            rosters.append(rosterLine(id)).append('\n');
        }
        rosters.append("\n**❄ FROST**\n");
        for (UUID id : sortedRoster(session, TeamSide.FROST)) {
            rosters.append(rosterLine(id)).append('\n');
        }
        if (session.participantCount() < 2) {
            rosters.append("\n*Solo session*");
        }

        List<String> blocks = new ArrayList<>();
        blocks.add("## 🏀 MATCH STARTED · " + mode.id().toUpperCase(java.util.Locale.ROOT) + queueLabel
            + "\nA new game just tipped off on **" + escape(session.arena().displayName()) + "**");
        blocks.add("**Mode** " + mode.plainName() + queueLabel
            + "\n**Players** " + session.participantCount()
            + "\n**Average ELO** IGNIS " + avgElo(session, TeamSide.IGNIS)
            + " · FROST " + avgElo(session, TeamSide.FROST)
            + "\n**Match** `" + shortId(session) + "`");
        blocks.add(rosters.toString());
        webhook.sendCard(COLOR_NEUTRAL, null, blocks, "BosketBall · live", null);
    }

    public void scoreUpdate(MatchSession session, String headline) {
        if (!isEnabled() || session == null) {
            return;
        }
        int ignis = session.score(TeamSide.IGNIS);
        int frost = session.score(TeamSide.FROST);
        DiscordWebhook.Embed embed = DiscordWebhook.Embed.of(
            headline != null ? headline : "📊 Score Update",
            "**" + session.teamDisplayName(TeamSide.IGNIS) + " " + ignis + " — " + frost + " "
                + session.teamDisplayName(TeamSide.FROST) + "**\n"
                + "Q" + currentQuarterLabel(session) + " · " + session.formattedClock() + " · `" + shortId(session) + "`",
            ignis >= frost ? COLOR_IGNIS : COLOR_FROST
        ).footer("BosketBall live feed").timestampNow();
        webhook.sendEmbed(embed);
    }

    public void matchFinished(
        MatchSession session,
        TeamSide winner,
        UUID mvpId,
        GameQueueMode queueMode,
        Map<UUID, Integer> eloDeltas,
        int durationSeconds
    ) {
        if (!isEnabled() || session == null || winner == null) {
            return;
        }
        int sIgnis = session.score(TeamSide.IGNIS);
        int sFrost = session.score(TeamSide.FROST);
        String winnerName = session.teamDisplayName(winner);
        Player mvp = mvpId != null ? Bukkit.getPlayer(mvpId) : null;
        String mvpName = mvp != null ? mvp.getName() : nameOf(mvpId);

        List<UUID> ordered = new ArrayList<>();
        for (Map.Entry<UUID, TeamSide> e : session.participants().entrySet()) {
            if (e.getValue() != TeamSide.SPECTATOR) {
                ordered.add(e.getKey());
            }
        }
        ordered.sort(Comparator.comparingDouble((UUID id) -> -session.getBoxScore(id).mvpScore()));

        StringBuilder box = new StringBuilder("```\n");
        box.append(String.format("%-16s %3s %3s %3s %3s %3s%n", "PLAYER", "PTS", "AST", "REB", "STL", "BLK"));
        for (UUID id : ordered) {
            MatchSession.MatchBoxScore b = session.getBoxScore(id);
            box.append(String.format("%-16s %3d %3d %3d %3d %3d%n",
                truncateName(nameOf(id)), b.points, b.assists, b.rebounds, b.steals, b.blocks));
        }
        box.append("```");

        String forfeitTag = session.forfeitVictory() ? " · FORFEIT" : "";
        String outcome = sIgnis == sFrost ? "FINAL" : (winner == TeamSide.IGNIS
            ? "**" + escape(session.teamDisplayName(TeamSide.IGNIS)) + "** takes it"
            : "**" + escape(session.teamDisplayName(TeamSide.FROST)) + "** takes it");
        List<String> blocks = new ArrayList<>();
        blocks.add("## " + winnerName.toUpperCase(java.util.Locale.ROOT) + " WINS · " + session.mode().id().toUpperCase(java.util.Locale.ROOT) + forfeitTag
            + "\n**" + escape(session.teamDisplayName(TeamSide.IGNIS)) + " " + sIgnis + " — " + sFrost + " "
            + escape(session.teamDisplayName(TeamSide.FROST)) + "**\n"
            + outcome + " · " + (queueMode == GameQueueMode.CASUAL ? "Casual" : "Ranked") + " · " + formatDuration(durationSeconds));
        blocks.add("**★ MVP** " + escape(mvpName != null ? mvpName : "—")
            + "\n**Arena** " + escape(session.arena().displayName())
            + "\n**Match** `" + shortId(session) + "`");
        blocks.add(box.toString());
        blocks.add(eloSummary(eloDeltas));

        webhook.sendCard(
            winner == TeamSide.IGNIS ? COLOR_IGNIS : COLOR_FROST,
            null,
            blocks,
            "BosketBall · Competitive Basketball",
            null
        );
    }

    public void reportFiled(String reporterName, String reportedName, String reason, String serverContext) {
        if (!isEnabled()) {
            return;
        }
        DiscordWebhook.Embed embed = DiscordWebhook.Embed.of(
            "🚩 NEW PLAYER REPORT",
            "A player report needs staff review.",
            COLOR_RED
        )
            .field("Reporter", reporterName, true)
            .field("Reported", reportedName, true)
            .field("Server", serverContext == null ? "BosketBall" : serverContext, true)
            .field("Reason", reason == null || reason.isBlank() ? "*no reason given*" : reason, false)
            .field("Action", "`/bb reports` then click **Open** to inspect and sanction.", false)
            .footer("BosketBall moderation")
            .timestampNow();
        webhook.sendEmbed(embed);
    }

    public void moderationAction(String playerName, String action, String reason, int warnPoints) {
        if (!isEnabled()) {
            return;
        }
        DiscordWebhook.Embed embed = DiscordWebhook.Embed.of(
            "🛡 MODERATION · " + action.toUpperCase(java.util.Locale.ROOT),
            "**" + escape(playerName) + "** · " + warnPoints + "/3 warnings",
            COLOR_RED
        )
            .field("Reason", reason == null ? "—" : reason, false)
            .footer("BosketBall moderation")
            .timestampNow();
        webhook.sendEmbed(embed);
    }

    public void rankUp(String playerName, String oldRank, String newRank, int elo) {
        if (!isEnabled()) {
            return;
        }
        webhook.sendEmbed(DiscordWebhook.Embed.of(
            "✦ RANK UP",
            "**" + escape(playerName) + "** promoted from **" + oldRank + "** to **" + newRank + "** (" + elo + " ELO).",
            COLOR_GOLD
        ).timestampNow());
    }

    public void crateOpened(String playerName, String rewardLabel, String rarity, int cost, int valueCoins) {
        if (!isEnabled()) {
            return;
        }
        int accent = switch (rarity == null ? "" : rarity) {
            case "ʟᴇɢᴇɴᴅᴀʀʏ" -> 0xF0A500;
            case "ᴇᴘɪᴄ" -> 0xA855F7;
            case "ʀᴀʀᴇ" -> 0x38BDF8;
            default -> 0x94A3B8;
        };
        List<String> blocks = new ArrayList<>();
        blocks.add("## 🎁 " + rarity + "\n**" + escape(playerName) + "** revealed **" + escape(rewardLabel) + "**");
        if (valueCoins > 0) {
            int net = valueCoins - cost;
            blocks.add("**Cost** " + cost + " Coins\n"
                + "**Value** " + valueCoins + " Coins\n"
                + "**Net** " + (net >= 0 ? "+" : "") + net + " Coins "
                + (net >= 0 ? "📈" : "📉"));
        } else {
            blocks.add("**Cost** " + cost + " Coins\n**Value** *cosmetic unlock*");
        }
        webhook.sendCard(accent, null, blocks, "BosketBall · Reward Crate · " + playerName, null);
    }

    private List<UUID> sortedRoster(MatchSession session, TeamSide side) {
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, TeamSide> e : session.participants().entrySet()) {
            if (e.getValue() == side) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    private String rosterLine(UUID id) {
        PlayerProfile prof = statsManager.getIfPresent(id);
        String name = escape(nameOf(id));
        if (prof == null) {
            return "• " + name;
        }
        return "• **" + name + "** · " + prof.elo() + " ELO · " + prof.effectiveRank().plainDisplayName();
    }

    private String nameOf(UUID id) {
        if (id == null) {
            return "Unknown";
        }
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        PlayerProfile prof = statsManager.getIfPresent(id);
        return prof != null ? prof.lastKnownName() : "Unknown";
    }

    private String avgElo(MatchSession session, TeamSide side) {
        int sum = 0;
        int count = 0;
        for (Map.Entry<UUID, TeamSide> e : session.participants().entrySet()) {
            if (e.getValue() != side) {
                continue;
            }
            PlayerProfile prof = statsManager.getIfPresent(e.getKey());
            if (prof != null) {
                sum += prof.elo();
                count++;
            }
        }
        return count == 0 ? "—" : String.valueOf(Math.round((double) sum / count));
    }

    private String eloSummary(Map<UUID, Integer> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return "*Casual match — no ELO change*";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<UUID, Integer> e : deltas.entrySet()) {
            int d = e.getValue();
            sb.append("**").append(escape(nameOf(e.getKey()))).append("** ")
                .append(d >= 0 ? "+" : "").append(d).append(" ELO\n");
        }
        return sb.toString();
    }

    private String currentQuarterLabel(MatchSession session) {
        return String.valueOf(session.currentQuarter());
    }

    private static String shortId(MatchSession session) {
        return Integer.toHexString(System.identityHashCode(session)).toUpperCase(java.util.Locale.ROOT);
    }

    public static String formatDuration(int seconds) {
        if (seconds <= 0) {
            return "—";
        }
        int m = seconds / 60;
        int s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    private static String truncateName(String name) {
        if (name == null) {
            return "?";
        }
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("`", "\\`")
            .replace("~", "\\~")
            .replace("|", "\\|")
            .replace(">", "\\>");
    }
}
