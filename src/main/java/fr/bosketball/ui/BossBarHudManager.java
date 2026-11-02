package fr.bosketball.ui;

import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Dual-Layer Transparent BossBar Broadcast & Matchmaking Queue HUD Manager.
 */
public final class BossBarHudManager {

    private final Map<UUID, BossBar> primaryBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> secondaryBars = new ConcurrentHashMap<>();

    public void showQueueStatus(Player player, String modeTitle, int current, int max, long elapsedSec) {
        String timer = String.format("%d:%02d", elapsedSec / 60L, elapsedSec % 60L);
        Component title = Msg.parse(
            "<#4ADE80>●</#4ADE80> <white><bold>Queued for " + modeTitle + "</bold></white>"
                + " <gray>(" + current + "/" + max + ")</gray>  <dark_gray>•</dark_gray>  "
                + "<#FDE047>" + timer + "</#FDE047>"
        );
        BossBar top = primaryBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar b = BossBar.bossBar(title, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            player.showBossBar(b);
            return b;
        });
        top.name(title);
        hideSecondary(player);
    }

    public void showLiveScorebug(
        Player player,
        String homeTag,
        int homeScore,
        String clockText,
        int shotClock,
        int awayScore,
        String awayTag
    ) {
        Component title = Glyphs.composeLiveScorebug(homeTag, homeScore, clockText, shotClock, awayScore, awayTag);
        BossBar top = primaryBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar b = BossBar.bossBar(title, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            player.showBossBar(b);
            return b;
        });
        top.name(title);
        hideSecondary(player);
    }

    public void showScorerCelebration(
        Player player,
        String scorerName,
        String roleSmallCaps,
        Glyphs.BannerVariant variant,
        String subLabelSmallCaps,
        String subValueText
    ) {
        Component mainComp = Glyphs.composeScorerBanner(scorerName, roleSmallCaps, variant);
        BossBar top = primaryBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar b = BossBar.bossBar(mainComp, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            player.showBossBar(b);
            return b;
        });
        top.name(mainComp);

        Component subComp = Glyphs.composeAssistBanner(subLabelSmallCaps, subValueText);
        BossBar sub = secondaryBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar b = BossBar.bossBar(subComp, 1.0f, BossBar.Overlay.PROGRESS != null ? BossBar.Color.WHITE : BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            player.showBossBar(b);
            return b;
        });
        sub.name(subComp);
    }

    public void hideSecondary(Player player) {
        BossBar sub = secondaryBars.remove(player.getUniqueId());
        if (sub != null) {
            player.hideBossBar(sub);
        }
    }

    public void clearPlayer(Player player) {
        BossBar top = primaryBars.remove(player.getUniqueId());
        if (top != null) {
            player.hideBossBar(top);
        }
        hideSecondary(player);
    }

    public void clearAll() {
        for (Map.Entry<UUID, BossBar> e : primaryBars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        for (Map.Entry<UUID, BossBar> e : secondaryBars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                p.hideBossBar(e.getValue());
            }
        }
        primaryBars.clear();
        secondaryBars.clear();
    }
}
