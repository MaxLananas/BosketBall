package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import io.papermc.paper.chat.ChatRenderer;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Clean, Single-Line Server Chat Renderer (Cytooxien-style):
 *   [Rank Pill] [Active Tag] PlayerName » message
 *   - Tight 2px badge spacing on a single readable line!
 *   - Clean Player Stats hover card + Click to challenge to a 1v1 Duel!
 */
public final class BosketChatRenderer implements ChatRenderer {

    private final StatsManager statsManager;
    private final GameManager gameManager;
    private final fr.bosketball.social.GuildService guildService;
    private volatile List<String> highlightKeywords;
    private volatile boolean showTopStatBadges;

    public BosketChatRenderer(
        StatsManager statsManager,
        GameManager gameManager,
        fr.bosketball.social.GuildService guildService,
        List<String> highlightKeywords,
        boolean showTopStatBadges
    ) {
        this.statsManager = statsManager;
        this.gameManager = gameManager;
        this.guildService = guildService;
        this.highlightKeywords = List.copyOf(highlightKeywords);
        this.showTopStatBadges = showTopStatBadges;
    }

    public void updateConfig(List<String> highlightKeywords, boolean showTopStatBadges) {
        this.highlightKeywords = List.copyOf(highlightKeywords);
        this.showTopStatBadges = showTopStatBadges;
    }

    /**
     * Builds the unified player prefix + interactive hoverable player name shared by
     * both regular chat and {@code /gg}, ensuring ranks, #1/#2/#3 ELO badges, and equipped
     * custom tags (including Founder & MyPineapple) are 100% identical everywhere.
     */
    public static Component buildPlayerChatPrefix(
        Player source,
        StatsManager statsManager,
        GameManager gameManager,
        boolean showTopStatBadges
    ) {
        return buildPlayerChatPrefix(source, statsManager, gameManager, null, showTopStatBadges);
    }

    /** Same prefix, with the guild service so the hover card can advertise the player's guild. */
    public static Component buildPlayerChatPrefix(
        Player source,
        StatsManager statsManager,
        GameManager gameManager,
        fr.bosketball.social.GuildService guildService,
        boolean showTopStatBadges
    ) {
        PlayerProfile profile = statsManager.getOrCreate(source);
        Rank rank = profile.effectiveRank();
        StatsManager.LeaderboardBadges badges = statsManager.getBadges(source.getUniqueId());
        MatchSession match = gameManager != null ? gameManager.getSessionOf(source.getUniqueId()) : null;

        TextComponent.Builder line = Component.text();
        String tightGap = Glyphs.shift(2);

        // 1. Primary Rank or Active Match Team Badge
        TextColor nameColor = TextColor.color(0xF1F5F9);
        if (match != null) {
            TeamSide side = match.getTeam(source.getUniqueId());
            if (side != TeamSide.SPECTATOR) {
                line.append(side.pillComponent());
                nameColor = side.textColor();
            } else {
                line.append(rank.pillComponent());
            }
        } else {
            line.append(rank.pillComponent());
        }

        // 2. #1 / #2 / #3 Server ELO Badges (Gold, Silver, Bronze)
        if (showTopStatBadges && badges.eloPlace() >= 1 && badges.eloPlace() <= 3) {
            line.append(Component.text(tightGap, Glyphs.BITMAP_STYLE))
                .append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, badges.eloPlace()));
        }

        // 3. Single Active Cosmetic Tag (enforcing Founder / MyPineapple authorization)
        ChatTag activeTag = profile.selectedTag();
        if (activeTag != null && activeTag.canBeUsedBy(profile, source.getName(), source.hasPermission("bosketball.tags.all"))) {
            line.append(Component.text(tightGap, Glyphs.BITMAP_STYLE))
                .append(activeTag.pillComponent());
        }

        Rank nextRank = rank.nextCompetitiveRank();
        String eloNextHint = nextRank != null
            ? " <dark_gray>(" + profile.elo() + "/" + nextRank.minElo() + " → " + nextRank.plainDisplayName() + ")</dark_gray>"
            : "";

        int totalGames = profile.wins() + profile.losses();
        int winPct = totalGames > 0 ? (int) Math.round(profile.wins() * 100.0 / totalGames) : 0;

        // Guild line: every player hover advertises the guild the player belongs to.
        String guildLine = "";
        if (guildService != null) {
            var guild = guildService.guildOf(source.getUniqueId());
            guildLine = guild == null
                ? "<gray>Guild:</gray> <dark_gray>None</dark_gray>\n"
                : "<gray>Guild:</gray> " + guild.displayName() + " " + guild.displayTag()
                    + " <dark_gray>(" + guild.elo() + " ELO • " + guild.members().size()
                    + " members • #" + guildService.placementOf(guild) + ")</dark_gray>\n";
        }

        // 4. Clean Modern Player Hover Card + Click-to-Duel (/duel <player>)
        Component hoverCard = Msg.parse(
            "<#FF8C1A><bold>" + source.getName() + "</bold></#FF8C1A> <!shadow><white>" + rank.pillGlyph() + "</white></!shadow>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + guildLine
                + "<gray>Rank:</gray> <white>" + rank.plainDisplayName() + "</white>" + eloNextHint + "\n"
                + "<gray>ELO:</gray> <#FBBF24>" + profile.elo() + "</#FBBF24> <dark_gray>•</dark_gray> "
                + "<gray>Coins:</gray> <#FDE047>" + profile.coins() + "</#FDE047>\n"
                + "<gray>Record:</gray> <#4ADE80>" + profile.wins() + "W</#4ADE80><gray>/</gray><#F87171>" + profile.losses() + "L</#F87171> <dark_gray>(" + winPct + "%)</dark_gray> <dark_gray>•</dark_gray> "
                + "<gray>PTS:</gray> <white>" + profile.points() + "</white>\n"
                + "<gray>Assists:</gray> <white>" + profile.assists() + "</white> <dark_gray>•</dark_gray> "
                + "<gray>Dunks:</gray> <white>" + profile.dunks() + "</white>\n"
                + "<gray>Playtime:</gray> <white>" + profile.formattedPlayTime() + "</white>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + "<#38BDF8>▸ Click to challenge to a 1v1 Duel</#38BDF8>"
        );

        Component interactiveName = Component.text(" " + source.getName(), nameColor)
            .hoverEvent(HoverEvent.showText(hoverCard))
            .clickEvent(ClickEvent.runCommand("/duel " + source.getName()));

        line.append(interactiveName);
        line.append(Component.text(" » ", NamedTextColor.DARK_GRAY));
        return line.asComponent();
    }

    @Override
    public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
        TextComponent.Builder line = Component.text();
        line.append(buildPlayerChatPrefix(source, statsManager, gameManager, guildService, showTopStatBadges));
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        line.append(styleMessageContent(plain));
        return line.asComponent();
    }

    private Component styleMessageContent(String plain) {
        if (highlightKeywords != null && !highlightKeywords.isEmpty()) {
            String upper = plain.toUpperCase(java.util.Locale.ROOT);
            for (String kw : highlightKeywords) {
                if (kw != null && !kw.isBlank() && upper.equals(kw.trim().toUpperCase(java.util.Locale.ROOT))) {
                    return Component.text(plain, Style.style(TextColor.color(0xFDE047), TextDecoration.BOLD));
                }
            }
        }
        // Highlight @PlayerName mentions cleanly in sky-blue!
        if (plain.contains("@")) {
            TextComponent.Builder mb = Component.text();
            String[] tokens = plain.split(" ", -1);
            for (int i = 0; i < tokens.length; i++) {
                if (i > 0) {
                    mb.append(Component.text(" ", TextColor.color(0xE2E8F0)));
                }
                String tok = tokens[i];
                if (tok.startsWith("@") && tok.length() >= 3) {
                    mb.append(Component.text(tok, Style.style(TextColor.color(0x38BDF8), TextDecoration.BOLD)));
                } else {
                    mb.append(Component.text(tok, TextColor.color(0xE2E8F0)));
                }
            }
            return mb.asComponent();
        }
        return Component.text(plain, TextColor.color(0xE2E8F0));
    }
}
