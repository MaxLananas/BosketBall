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
import fr.bosketball.util.SmallCaps;
import io.papermc.paper.chat.ChatRenderer;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Custom Paper 26.2 ChatRenderer inspired by image-5.png & image-6.png:
 * Renders: [🏆#1] [👑#1] [⭐#1] [🪽#1] [AURA_TAG] [RANK] PlayerName: message
 * + Interactive Scouting Hover Card (with inline Player Head!) & Click-to-Duel!
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

        // 1. Glossy 3D-Beveled Top-#1/#2/#3 Leaderboard Pills (exact image-5.png: 🏆#1 👑#1 ⭐#1 🪽#1 🏀#1 🔥#1!)
        if (showTopStatBadges && badges.hasAny()) {
            appendTopPill(line, Glyphs.TopBadgeKind.WINS, badges.winsPlace());
            appendTopPill(line, Glyphs.TopBadgeKind.ELO, badges.eloPlace());
            appendTopPill(line, Glyphs.TopBadgeKind.MVP, badges.mvpPlace());
            appendTopPill(line, Glyphs.TopBadgeKind.ASSISTS, badges.assistsPlace());
            appendTopPill(line, Glyphs.TopBadgeKind.POINTS, badges.pointsPlace());
            appendTopPill(line, Glyphs.TopBadgeKind.DUNKS, badges.dunksPlace());
        }

        // 2. Equipped Glossy Beveled Aura / Prestige Tags (up to 3 simultaneous tags out of 36 in /bb tags!)
        for (ChatTag equippedTag : profile.equippedTags()) {
            line.append(equippedTag.pillComponent()).append(Component.text(" "));
        }

        // 3. Tier Bracket / Pixel-Art Rank Pill + Team Color
        TextColor nameColor = TextColor.color(0xDDE8F8);
        TextColor msgColor = TextColor.color(0x52B8FF);
        if (match != null) {
            TeamSide side = match.getTeam(source.getUniqueId());
            if (side != TeamSide.SPECTATOR) {
                line.append(side.pillComponent()).append(Component.text(" "));
                nameColor = side.textColor();
                msgColor = side == TeamSide.IGNIS ? TextColor.color(0xFF8C7A) : TextColor.color(0x58C4FF);
            } else {
                line.append(rank.pillComponent()).append(Component.text(" "));
            }
        } else {
            // Tier bracket [E]/[S]/[N] + Rank pill just like image-5.png!
            String tierCode = switch (rank) {
                case ADMIN, MVP -> "S";
                case LEGEND, SUPERSTAR -> "E";
                case ALL_STAR, PRO -> "A";
                default -> "N";
            };
            TextColor bracketCol = rank.accentColor();
            line.append(Component.text("[" + tierCode + "]", bracketCol));
        }

        // 4. Interactive Player Name with Inline Player Head Scouting Card Hover + Click-to-Duel!
        Component hoverCard = Component.text()
            .append(CenterTabOverlayManager.buildInlinePlayerHead(source))
            .append(Component.text(" "))
            .append(Msg.parse(
                "<gradient:#FF7A18:#FFD43B><bold>" + source.getName() + "</bold></gradient> "
                    + "<!shadow><white>" + rank.pillGlyph() + "</white></!shadow>\n"
                    + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                    + "<gray>ᴇʟᴏ:</gray> <gold>" + profile.elo() + "</gold> <dark_gray>•</dark_gray> "
                    + "<gray>ʙɪʟᴀɴ:</gray> <green>" + profile.wins() + "ᴠ</green><gray>/</gray><red>" + profile.losses() + "ᴅ</red>\n"
                    + "<gray>ᴘᴏɪɴᴛꜱ:</gray> <yellow>" + profile.points() + "</yellow> <dark_gray>•</dark_gray> "
                    + "<gray>ᴘᴀꜱꜱᴇꜱ:</gray> <aqua>" + profile.assists() + "</aqua> <dark_gray>•</dark_gray> "
                    + "<gray>ᴅᴜɴᴋꜱ:</gray> <red>" + profile.dunks() + "</red>\n"
                    + "<gray>ᴛᴇᴍᴘꜱ ᴅᴇ ᴊᴇᴜ:</gray> <green>" + profile.formattedPlayTimeFr() + "</green>\n"
                    + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                    + "<yellow>⚡ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴅᴇꜰɪᴇʀ ᴇɴ 1ᴠ1 !</yellow>"
            ))
            .asComponent();

        Component interactiveName = Component.text(source.getName())
            .color(nameColor)
            .hoverEvent(HoverEvent.showText(hoverCard))
            .clickEvent(ClickEvent.suggestCommand("/bb duel " + source.getName()));

        line.append(interactiveName);
        line.append(Component.text(": ", NamedTextColor.GRAY));

        // 5. Styled Message Body (sky-blue / rank-tinted clean chat like image-5.png!)
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        line.append(styleMessageBody(plain, rank, msgColor));

        return line.asComponent();
    }

    private static void appendTopPill(TextComponent.Builder line, Glyphs.TopBadgeKind kind, int place) {
        if (place >= 1 && place <= 3) {
            line.append(Glyphs.topBadgePill(kind, place)).append(Component.text(" "));
        }
    }

    private Component styleMessageBody(String plain, Rank senderRank, TextColor defaultMsgColor) {
        String upper = plain.trim().toUpperCase();
        for (String kw : highlightKeywords) {
            if (upper.equals(kw)) {
                return Component.text("✦ " + SmallCaps.toSmallCaps(plain.trim()) + " ✦")
                    .color(TextColor.color(0xFFE066))
                    .decorate(TextDecoration.BOLD);
            }
        }
        if (senderRank == Rank.ADMIN) {
            return Component.text(plain, TextColor.color(0xFFE878));
        }
        return Component.text(plain, defaultMsgColor);
    }
}
