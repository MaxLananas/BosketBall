package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import fr.bosketball.util.SmallCaps;
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
 * Custom Paper 26.2 ChatRenderer inspired by top sports servers (image-1.png):
 * Renders: [🏆1] [⭐2] [🏀1] [RANK_PILL] [TEAM_PILL] PlayerName » Message
 * + Full Interactive NBA Scouting Hover Card & Click-to-Duel 1v1!
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

        // 1. Live Server Top-3 Leaderboard Badges ([🏆1] [⭐2] [🏀1]) just like image-1.png!
        if (showTopStatBadges && badges.hasAny()) {
            if (badges.winsPlace() > 0) {
                line.append(Glyphs.bitmap(Glyphs.ICON_TROPHY))
                    .append(Component.text(badges.winsPlace() + " ", Style.style(TextColor.color(0xFFD43B), TextDecoration.BOLD)));
            }
            if (badges.eloPlace() > 0) {
                line.append(Glyphs.bitmap(Glyphs.ICON_STAR))
                    .append(Component.text(badges.eloPlace() + " ", Style.style(TextColor.color(0xC77DFF), TextDecoration.BOLD)));
            }
            if (badges.pointsPlace() > 0) {
                line.append(Glyphs.bitmap(Glyphs.ICON_BALL))
                    .append(Component.text(badges.pointsPlace() + " ", Style.style(TextColor.color(0xFF8C2B), TextDecoration.BOLD)));
            }
        }

        // 2. Win-Streak Fire Emblem when on a 3+ match win streak!
        if (profile.winStreak() >= 3) {
            line.append(Glyphs.bitmap(Glyphs.ICON_FIRE))
                .append(Component.text(profile.winStreak() + " ", TextColor.color(0xFF5A26)));
        }

        // 3. Pixel-Art Rank Pill ([ROOKIE]..[MVP]..[ADMIN])
        line.append(rank.pillComponent()).append(Component.text(" "));

        // 4. Live Match Team Pill ([IGNIS] / [FROST]) if currently on court
        TextColor nameColor = rank.accentColor();
        if (match != null) {
            TeamSide side = match.getTeam(source.getUniqueId());
            if (side != TeamSide.SPECTATOR) {
                line.append(side.pillComponent()).append(Component.text(" "));
                nameColor = side.textColor();
            }
        }

        // 5. Interactive Player Name with Scouting Card Hover + Click-to-Duel!
        Component hoverCard = Msg.parse(
            "<gradient:#FF7A18:#FFD43B><bold>✦ ᴄᴀʀᴛᴇ ᴊᴏᴜᴇᴜʀ • " + source.getName() + " ✦</bold></gradient>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + "<gray>ʀᴀɴɢ:</gray> <!shadow><white>" + rank.pillGlyph() + "</white></!shadow> <gold>(" + profile.elo() + " ᴇʟᴏ)</gold>\n"
                + "<gray>ᴠɪᴄᴛᴏɪʀᴇꜱ:</gray> <green>" + profile.wins() + "</green> <dark_gray>•</dark_gray> <gray>ᴍᴠᴘꜱ:</gray> <yellow>" + profile.mvps() + "</yellow>\n"
                + "<gray>ᴘᴏɪɴᴛꜱ:</gray> <gold>" + profile.points() + "</gold> <dark_gray>•</dark_gray> <gray>ᴅᴜɴᴋꜱ:</gray> <red>" + profile.dunks() + "</red>\n"
                + "<gray>ᴘᴀꜱꜱᴇꜱ ᴅᴇᴄ.:</gray> <aqua>" + profile.assists() + "</aqua> <dark_gray>•</dark_gray> <gray>3ᴘᴛ%:</gray> <light_purple>"
                + String.format("%.1f%%", profile.threePointPercentage()) + "</light_purple>\n"
                + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                + "<yellow>⚡ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴅᴇꜰɪᴇʀ ᴇɴ 1ᴠ1 !</yellow>"
        );

        Component interactiveName = Component.text(source.getName())
            .color(nameColor)
            .hoverEvent(HoverEvent.showText(hoverCard))
            .clickEvent(ClickEvent.suggestCommand("/bb duel " + source.getName()));

        line.append(interactiveName);
        line.append(Component.text(" » ", NamedTextColor.DARK_GRAY));

        // 6. Styled Message Body with automatic hype keyword callout polish
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        line.append(styleMessageBody(plain, rank));

        return line.asComponent();
    }

    private Component styleMessageBody(String plain, Rank senderRank) {
        String upper = plain.trim().toUpperCase();
        for (String kw : highlightKeywords) {
            if (upper.equals(kw)) {
                return Component.text("✦ " + SmallCaps.toSmallCaps(plain.trim()) + " ✦")
                    .color(TextColor.color(0xFFE066))
                    .decorate(TextDecoration.BOLD);
            }
        }
        TextColor msgColor = (senderRank == Rank.ADMIN || senderRank == Rank.MVP || senderRank == Rank.LEGEND)
            ? TextColor.color(0xF5FAFF)
            : TextColor.color(0xDCE6F2);
        return Component.text(plain, msgColor);
    }
}
