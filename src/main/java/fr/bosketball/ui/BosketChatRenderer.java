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
    private final List<String> highlightKeywords;
    private final boolean showTopStatBadges;

    public BosketChatRenderer(
        StatsManager statsManager,
        GameManager gameManager,
        List<String> highlightKeywords,
        boolean showTopStatBadges
    ) {
        this.statsManager = statsManager;
        this.gameManager = gameManager;
        this.highlightKeywords = highlightKeywords;
        this.showTopStatBadges = showTopStatBadges;
    }

    @Override
    public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
        PlayerProfile profile = statsManager.getOrCreate(source);
        Rank rank = profile.effectiveRank();
        StatsManager.LeaderboardBadges badges = statsManager.getBadges(source.getUniqueId());
        MatchSession match = gameManager.getSessionOf(source.getUniqueId());

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

        // 2. Optional #1 ELO Crown (at most 1 compact leaderboard badge so chat never bloats!)
        if (showTopStatBadges && badges.eloPlace() == 1) {
            line.append(Component.text(tightGap, Glyphs.BITMAP_STYLE))
                .append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, 1));
        }

        // 3. Single Active Cosmetic Tag (purchased in Shop)
        ChatTag activeTag = profile.selectedTag();
        if (activeTag != null) {
            line.append(Component.text(tightGap, Glyphs.BITMAP_STYLE))
                .append(activeTag.pillComponent());
        }

        // 4. Clean English Player Hover Card + Click-to-Duel
        Component hoverCard = Msg.parse(
            "<#FF8C1A><bold>" + source.getName() + "</bold></#FF8C1A> <!shadow><white>" + rank.pillGlyph() + "</white></!shadow>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + "<gray>ELO:</gray> <#FBBF24>" + profile.elo() + "</#FBBF24> <dark_gray>•</dark_gray> "
                + "<gray>Coins:</gray> <#FDE047>" + profile.coins() + "</#FDE047>\n"
                + "<gray>Record:</gray> <#4ADE80>" + profile.wins() + "W</#4ADE80><gray>/</gray><#F87171>" + profile.losses() + "L</#F87171> <dark_gray>•</dark_gray> "
                + "<gray>Points:</gray> <white>" + profile.points() + "</white>\n"
                + "<gray>Assists:</gray> <white>" + profile.assists() + "</white> <dark_gray>•</dark_gray> "
                + "<gray>Dunks:</gray> <white>" + profile.dunks() + "</white>\n"
                + "<gray>Playtime:</gray> <white>" + profile.formattedPlayTime() + "</white>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + "<#38BDF8>▸ Click to challenge to a 1v1 Duel</#38BDF8>"
        );

        Component interactiveName = Component.text(" " + source.getName(), nameColor)
            .hoverEvent(HoverEvent.showText(hoverCard))
            .clickEvent(ClickEvent.runCommand("/bb duel " + source.getName()));

        line.append(interactiveName);
        line.append(Component.text(" » ", NamedTextColor.DARK_GRAY));

        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        line.append(styleMessageContent(plain));
        return line.asComponent();
    }

    private Component styleMessageContent(String plain) {
        if (highlightKeywords == null || highlightKeywords.isEmpty()) {
            return Component.text(plain, TextColor.color(0xE2E8F0));
        }
        String upper = plain.toUpperCase();
        for (String kw : highlightKeywords) {
            if (kw != null && !kw.isBlank() && upper.equals(kw.trim().toUpperCase())) {
                return Component.text(plain, Style.style(TextColor.color(0xFDE047), TextDecoration.BOLD));
            }
        }
        return Component.text(plain, TextColor.color(0xE2E8F0));
    }
}
