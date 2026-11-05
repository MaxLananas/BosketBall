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

        String sigTag = "-";
        StringBuilder badge = new StringBuilder(64);
        if (guild != null) {
            String col = "<" + guild.hexColor() + ">";
            String close = "</" + guild.hexColor() + ">";
            badge.append(col).append("<bold>").append(Glyphs.ICON_SHIELD).append(' ')
                .append(escape(guild.tag())).append("</bold>").append(close);
            sigTag = guild.tag() + guild.hexColor();
        } else {
            ChatTag tag = profile.selectedTag();
            boolean allowed = tag != null && tag.canBeUsedBy(profile, target.getName(),
                fr.bosketball.util.Perms.has(target, fr.bosketball.util.Perms.TAGS_ALL),
                fr.bosketball.util.Perms.has(target, fr.bosketball.util.Perms.STAFF));
            if (allowed) {
                badge.append("<white><bold>").append(escape(tag.displayName())).append("</bold></white>");
                sigTag = "T:" + tag.name();
            } else {
                Rank rank = profile.effectiveRank();
                badge.append(colouredBold(hexPlain(rank.primaryColor()),
                    rank.smallCapsName().toUpperCase(Locale.ROOT)));
                sigTag = "R:" + rank.name();
            }
        }
        String line1 = assemblePrefix(badge.toString(), onCourt, side);

        StringBuilder line2 = new StringBuilder(64);
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

    /**
     * Builds the team prefix shown in front of the player name: the badge (rank, chat tag or
     * guild), the team pill when the player is on court, and <strong>exactly one</strong>
     * separating space before the name.
     *
     * <p>The space matters: the prefix used to end with a literal {@code >} produced by a
     * mismatched colour tag, which is why the lobby showed {@code ROOKIE>friandise}. Removing
     * the stray character without adding a real gap would render {@code ROOKIEfriandise}.
     */
    public static String assemblePrefix(String badge, boolean onCourt, TeamSide side) {
        StringBuilder prefix = new StringBuilder(96);
        if (badge != null && !badge.isEmpty()) {
            prefix.append(badge);
        }
        if (onCourt) {
            if (prefix.length() > 0) {
                prefix.append(GAP);
            }
            boolean ignis = side == TeamSide.IGNIS;
            prefix.append(colouredBold(ignis ? "#FF6B4A" : "#38BDF8", ignis ? "IGNIS" : "FROST"));
        }
        if (prefix.length() > 0) {
            prefix.append(GAP);
        }
        return prefix.toString();
    }

    /** {@code #RRGGBB} for a colour, without the surrounding MiniMessage angle brackets. */
    static String hexPlain(TextColor color) {
        return String.format(Locale.ROOT, "#%06X", color == null ? 0xFFFFFF : color.value());
    }

    /**
     * Bold label wrapped in a single hex colour pair.
     *
     * <p>The closing tag is built from the same {@code #RRGGBB} string, so the pair can never be
     * mismatched: the historical bug here was {@code "</" + "<#FF8C1A>".substring(1) + ">"} which
     * produced {@code </#FF8C1A>>} and printed a stray {@code >} between the rank and the name.
     */
    public static String colouredBold(String hexRgb, String label) {
        String h = hexRgb == null || hexRgb.isEmpty() ? "#FFFFFF" : hexRgb;
        if (h.charAt(0) != '#') {
            h = "#" + h;
        }
        return "<" + h + "><bold>" + label + "</bold></" + h + ">";
    }

    private static String escape(String raw) {
        return raw == null ? "" : raw.replace("<", "").replace(">", "");
    }
}
