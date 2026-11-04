package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.social.Guild;
import fr.bosketball.social.GuildService;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class NametagManager {

    private static final String TEAM_PREFIX = "bb_nt_";

    private static final int MAX_SLOTS = 12;
    private static final String GAP = "<gray> </gray>";

    private record Style(String signature, String line1, String line2) {}

    private final StatsManager statsManager;
    private final GuildService guildService;
    private final GameManager gameManager;
    private final Map<UUID, String> lastSignature = new ConcurrentHashMap<>();

    public NametagManager(StatsManager statsManager, GuildService guildService, GameManager gameManager) {
        this.statsManager = statsManager;
        this.guildService = guildService;
        this.gameManager = gameManager;
    }

    public void forget(UUID playerId) {
        lastSignature.remove(playerId);
        Player p = Bukkit.getPlayer(playerId);
        if (p != null && p.getScoreboard() != null) {
            for (int slot = 0; slot < MAX_SLOTS; slot++) {
                Team t = p.getScoreboard().getTeam(TEAM_PREFIX + slot);
                if (t != null) {
                    t.unregister();
                }
            }
        }
    }

    public void updateAll() {
        List<? extends Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }

        Map<UUID, Style> styles = new LinkedHashMap<>();
        for (Player target : online) {
            styles.put(target.getUniqueId(), styleFor(target));
        }
        for (Player viewer : online) {
            applyFor(viewer, online, styles);
        }
    }

    private void applyFor(Player viewer, List<? extends Player> online, Map<UUID, Style> styles) {
        Scoreboard board = viewer.getScoreboard();
        if (board == null) {
            return;
        }
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            Team team = board.getTeam(TEAM_PREFIX + slot);
            Player target = slot < online.size() ? online.get(slot) : null;
            if (target == null) {
                if (team != null) {
                    team.unregister();
                }
                continue;
            }
            Style style = styles.get(target.getUniqueId());
            if (team == null || !team.hasEntry(target.getName())) {
                if (team != null) {
                    team.unregister();
                }
                team = board.registerNewTeam(TEAM_PREFIX + slot);
                team.color(NamedTextColor.WHITE);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                team.addEntry(target.getName());
            }
            if (style != null && !style.signature().equals(lastSignature.get(target.getUniqueId()))) {
                lastSignature.put(target.getUniqueId(), style.signature());
                team.prefix(Msg.parse(style.line1()));
                team.suffix(Msg.parse(style.line2()));
            }
        }

        lastSignature.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private Style styleFor(Player target) {
        UUID id = target.getUniqueId();
        PlayerProfile profile = statsManager.getOrCreate(target);
        Guild guild = guildService == null ? null : guildService.guildOf(id);
        MatchSession session = gameManager == null ? null : gameManager.getSessionOf(id);
        TeamSide side = session == null ? TeamSide.SPECTATOR : session.getTeam(id);
        boolean onCourt = session != null && side != TeamSide.SPECTATOR;

        StringBuilder line1 = new StringBuilder(64);
        String sigTag = "-";
        if (guild != null) {
            String col = "<" + guild.hexColor() + ">";
            String close = "</" + guild.hexColor() + ">";
            line1.append(col).append("<bold>").append(Glyphs.ICON_SHIELD).append(' ')
                .append(escape(guild.tag())).append("</bold>").append(close);
            sigTag = guild.tag() + guild.hexColor();
        } else {
            ChatTag tag = profile.selectedTag();
            boolean allowed = tag != null && tag.canBeUsedBy(profile, target.getName(), target.hasPermission("bosketball.tags.all"));
            if (allowed) {
                line1.append("<white><bold>").append(escape(tag.displayName())).append("</bold></white>");
                sigTag = "T:" + tag.name();
            } else {
                Rank rank = profile.effectiveRank();
                String col = hex(rank.primaryColor());
                line1.append(col).append("<bold>").append(rank.smallCapsName().toUpperCase(Locale.ROOT))
                    .append("</bold>").append("</").append(col.substring(1)).append(">");
                sigTag = "R:" + rank.name();
            }
        }
        line1.append(GAP);
        if (onCourt) {
            String teamCol = side == TeamSide.IGNIS ? "#FF6B4A" : "#38BDF8";
            line1.append(teamCol).append("<bold>").append(side == TeamSide.IGNIS ? "IGNIS" : "FROST").append("</bold>")
                .append("</").append(teamCol.substring(1)).append(">");
        } else {
            line1.append("<gray>LOBBY</gray>");
        }

        StringBuilder line2 = new StringBuilder(64);
        line2.append("<white><bold>").append(escape(target.getName())).append("</bold></white>");
        StringBuilder sig = new StringBuilder(64);
        sig.append(sigTag).append('|').append(target.getName());
        if (onCourt) {
            MatchSession.MatchBoxScore box = session.getBoxScore(id);
            sig.append('|').append(side).append('|').append(box.points).append('|').append(box.hotStreak);
            if (box.points > 0) {
                line2.append(" <#FDE047><bold>").append(box.points).append("</bold></#FDE047><gray>PTS</gray>");
            }
            if (box.hotStreak >= 3) {
                line2.append(" <#FF6B35><bold>🔥").append(box.hotStreak).append("</bold></#FF6B35>");
            }
        } else {
            sig.append('|').append(profile.elo());
            line2.append(" <#FBBF24>").append(profile.elo()).append(" ELO</#FBBF24>");
        }
        return new Style(sig.toString(), line1.toString(), line2.toString());
    }

    private static String hex(TextColor color) {
        return String.format(Locale.ROOT, "<#%06X>", color == null ? 0xFFFFFF : color.value());
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "");
    }
}
