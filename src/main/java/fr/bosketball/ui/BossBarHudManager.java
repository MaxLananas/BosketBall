package fr.bosketball.ui;

import fr.bosketball.util.Glyphs;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Dual-Layer Transparent BossBar Broadcast HUD Manager:
 * - Primary Bar: Live 5-Column ESPN Scorebug OR Top Scorer Banner (inspired by image-1.png)
 * - Secondary Bar: Assist / Shot-Detail Sub-Banner stacked directly underneath during celebrations!
 * Note: resourcepack overrides white_background.png & white_progress.png to 100% transparent pixels,
 * so BossBar.Color.WHITE renders ONLY our pixel-exact custom HUD bitmaps and text!
 */
public final class BossBarHudManager {

    private final Map<UUID, BossBar> primaryBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> secondaryBars = new ConcurrentHashMap<>();

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
            BossBar b = BossBar.bossBar(subComp, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
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
