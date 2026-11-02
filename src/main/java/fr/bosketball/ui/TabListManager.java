package fr.bosketball.ui;

import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Custom Animated 26.2 TabList with Inline Player Heads, Team Colors, Glossy #1 Badges & Live Points!
 */
public final class TabListManager {

    private final StatsManager statsManager;
    private final String seasonName;
    private final String serverDomain;
    private int animPhase = 0;

    public TabListManager(StatsManager statsManager, String seasonName, String serverDomain) {
        this.statsManager = statsManager;
        this.seasonName = seasonName;
        this.serverDomain = serverDomain;
    }

    public void updateAll(GameManager gameManager) {
        animPhase = (animPhase + 1) % 3;
        String accent = switch (animPhase) {
            case 0 -> "<gradient:#FF7A18:#FFD43B>";
            case 1 -> "<gradient:#FFD43B:#48EAFF>";
            default -> "<gradient:#48EAFF:#FF7A18>";
        };

        int online = Bukkit.getOnlinePlayers().size();
        int inMatch = gameManager.activePlayerCount();

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProfile prof = statsManager.getOrCreate(p);
            int ping = p.getPing();

            Component header = Component.text()
                .append(Msg.parse("\n<!shadow><white>" + Glyphs.LOGO_HEADER + "</white></!shadow>\n"
                    + accent + "<bold>✦ " + seasonName + " ✦</bold></gradient>\n"))
                .append(Glyphs.composeDividerBottom())
                .append(Msg.parse("\n<gray>ᴊᴏᴜᴇᴜʀꜱ:</gray> <green>" + online + "</green>  <dark_gray>•</dark_gray>  "
                    + "<gray>ᴇɴ ᴍᴀᴛᴄʜ:</gray> <gold>" + inMatch + "</gold>  <dark_gray>•</dark_gray>  "
                    + "<gray>ᴘɪɴɢ:</gray> <aqua>" + ping + "ᴍꜱ</aqua>\n"))
                .asComponent();

            Component footer = Component.text()
                .append(Msg.parse("\n<gray>ʀᴀɴɢ:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow> "
                    + "<yellow>" + prof.elo() + " ᴇʟᴏ</yellow> <dark_gray>•</dark_gray> <gold>" + prof.points() + " ᴘᴛꜱ</gold>\n"
                    + "<gray>ᴀꜱᴛᴜᴄᴇ:</gray> <aqua>/bb tab</aqua> <gray>ᴏᴜ</gray> <yellow>[F]</yellow> <gray>ᴘᴏᴜʀ ʟᴇ ᴛᴀʙ ᴄᴇɴᴛʀᴀʟ 3ᴅ</gray>\n"))
                .append(Glyphs.composeDividerBottom())
                .append(Msg.parse("\n<gradient:#FF8A28:#FFE259><bold>" + serverDomain + "</bold></gradient>\n"))
                .asComponent();

            p.sendPlayerListHeaderAndFooter(header, footer);
            p.playerListName(buildTabEntry(p, prof, gameManager.getSessionOf(p.getUniqueId())));
        }
    }

    private Component buildTabEntry(Player player, PlayerProfile profile, MatchSession session) {
        TextComponent.Builder b = Component.text();
        // Real 8x8 Inline Player Head!
        b.append(Component.object(ObjectContents.playerHead(player.getUniqueId()))).append(Component.text(" "));

        StatsManager.LeaderboardBadges badges = statsManager.getBadges(player.getUniqueId());
        if (badges.winsPlace() == 1) {
            b.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.WINS, 1)).append(Component.text(" "));
        } else if (badges.eloPlace() == 1) {
            b.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, 1)).append(Component.text(" "));
        }

        Rank rank = profile.effectiveRank();
        if (session != null) {
            TeamSide side = session.getTeam(player.getUniqueId());
            if (side != TeamSide.SPECTATOR) {
                b.append(side.pillComponent()).append(Component.text(" "));
            }
            int matchPts = session.getBoxScore(player.getUniqueId()).points;
            b.append(Component.text(player.getName(), side.textColor()));
            b.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            b.append(Component.text(matchPts + " PTS", TextColor.color(0xFFE052)));
        } else {
            b.append(rank.pillComponent()).append(Component.text(" "));
            b.append(Component.text(player.getName(), rank.accentColor()));
            b.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            b.append(Component.text(profile.points() + " PTS", TextColor.color(0xFFE052)));
        }
        return b.asComponent();
    }
}
