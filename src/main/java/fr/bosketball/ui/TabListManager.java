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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Custom Animated 26.2 TabList Header, Footer & Ranked Player Name Formatter!
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

            Component header = Msg.parse(
                "\n<!shadow><white>" + Glyphs.LOGO_HEADER + "</white></!shadow>\n"
                    + accent + "<bold>✦ " + seasonName + " ✦</bold></gradient>\n"
                    + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                    + "<gray>ᴊᴏᴜᴇᴜʀꜱ:</gray> <green>" + online + "</green>  <dark_gray>•</dark_gray>  "
                    + "<gray>ᴇɴ ᴍᴀᴛᴄʜ:</gray> <gold>" + inMatch + "</gold>  <dark_gray>•</dark_gray>  "
                    + "<gray>ᴘɪɴɢ:</gray> <aqua>" + ping + "ᴍꜱ</aqua>\n"
            );

            Component footer = Msg.parse(
                "\n<gray>ᴠᴏᴛʀᴇ ʀᴀɴɢ:</gray> <!shadow><white>" + prof.effectiveRank().pillGlyph() + "</white></!shadow> "
                    + "<yellow>" + prof.elo() + " ᴇʟᴏ</yellow> <dark_gray>•</dark_gray> <gold>" + prof.points() + " ᴘᴛꜱ</gold>\n"
                    + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                    + "<gradient:#FF8A28:#FFE259><bold>" + serverDomain + "</bold></gradient>\n"
            );

            p.sendPlayerListHeaderAndFooter(header, footer);
            p.playerListName(buildTabEntry(p, prof, gameManager.getSessionOf(p.getUniqueId())));
        }
    }

    private Component buildTabEntry(Player player, PlayerProfile profile, MatchSession session) {
        TextComponent.Builder b = Component.text();
        StatsManager.LeaderboardBadges badges = statsManager.getBadges(player.getUniqueId());
        if (badges.winsPlace() == 1) {
            b.append(Glyphs.bitmap(Glyphs.ICON_TROPHY))
                .append(Component.text("1 ", TextColor.color(0xFFD43B)));
        }
        Rank rank = profile.effectiveRank();
        b.append(rank.pillComponent()).append(Component.text(" "));

        if (session != null) {
            TeamSide side = session.getTeam(player.getUniqueId());
            if (side != TeamSide.SPECTATOR) {
                b.append(side.pillComponent()).append(Component.text(" "));
            }
            b.append(Component.text(player.getName(), side.textColor()));
        } else {
            b.append(Component.text(player.getName(), rank.accentColor()));
        }
        b.append(Component.text(" • " + profile.elo(), NamedTextColor.DARK_GRAY));
        return b.build();
    }
}
