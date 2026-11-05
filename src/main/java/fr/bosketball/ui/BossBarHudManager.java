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

public final class BossBarHudManager {

    private final Map<UUID, BossBar> primaryBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> secondaryBars = new ConcurrentHashMap<>();
    private final Map<UUID, String> primarySignatures = new ConcurrentHashMap<>();
    private final Map<UUID, String> secondarySignatures = new ConcurrentHashMap<>();

    public void showQueueStatus(Player player, String modeTitle, int current, int max, long elapsedSec) {
        UUID pid = player.getUniqueId();
        String sig = "Q|" + modeTitle + "|" + current + "|" + max + "|" + elapsedSec;
        BossBar existing = primaryBars.get(pid);
        if (existing != null && sig.equals(primarySignatures.get(pid))) {
            hideSecondary(player);
            return;
        }
        String timer = String.format(java.util.Locale.ROOT, "%d:%02d", elapsedSec / 60L, elapsedSec % 60L);
        Component title = Msg.parse(fr.bosketball.util.Glyphs.smallCapsLabels(
            "<#4ADE80>●</#4ADE80> " + Msg.gradBold(Msg.GRAD_MINT, "Queued for " + modeTitle)
                + " <gray>(" + current + "/" + max + ")</gray>  <dark_gray>•</dark_gray>  "
                + "<#FDE047>" + timer + "</#FDE047>"
        ));
        primarySignatures.put(pid, sig);
        if (existing == null) {
            BossBar b = BossBar.bossBar(title, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            primaryBars.put(pid, b);
            player.showBossBar(b);
        } else {
            existing.name(title);
        }
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
        UUID pid = player.getUniqueId();
        String sig = "SB|" + homeTag + "|" + homeScore + "|" + clockText + "|" + shotClock + "|" + awayScore + "|" + awayTag;
        BossBar existing = primaryBars.get(pid);
        if (existing != null && sig.equals(primarySignatures.get(pid))) {
            hideSecondary(player);
            return;
        }
        Component title = Glyphs.composeLiveScorebug(homeTag, homeScore, clockText, shotClock, awayScore, awayTag);
        primarySignatures.put(pid, sig);
        if (existing == null) {
            BossBar b = BossBar.bossBar(title, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            primaryBars.put(pid, b);
            player.showBossBar(b);
        } else {
            existing.name(title);
        }
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
        UUID pid = player.getUniqueId();
        String pSig = "CEL1|" + scorerName + "|" + roleSmallCaps + "|" + variant.ordinal();
        BossBar existingTop = primaryBars.get(pid);
        if (existingTop == null || !pSig.equals(primarySignatures.get(pid))) {
            Component mainComp = Glyphs.composeScorerBanner(scorerName, roleSmallCaps, variant);
            primarySignatures.put(pid, pSig);
            if (existingTop == null) {
                BossBar b = BossBar.bossBar(mainComp, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                primaryBars.put(pid, b);
                player.showBossBar(b);
            } else {
                existingTop.name(mainComp);
            }
        }

        String sSig = "CEL2|" + subLabelSmallCaps + "|" + subValueText;
        BossBar existingSub = secondaryBars.get(pid);
        if (existingSub == null || !sSig.equals(secondarySignatures.get(pid))) {
            Component subComp = Glyphs.composeAssistBanner(subLabelSmallCaps, subValueText);
            secondarySignatures.put(pid, sSig);
            if (existingSub == null) {
                BossBar b = BossBar.bossBar(subComp, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                secondaryBars.put(pid, b);
                player.showBossBar(b);
            } else {
                existingSub.name(subComp);
            }
        }
    }

    public void hideSecondary(Player player) {
        UUID pid = player.getUniqueId();
        secondarySignatures.remove(pid);
        BossBar sub = secondaryBars.remove(pid);
        if (sub != null) {
            player.hideBossBar(sub);
        }
    }

    public void clearPlayer(Player player) {
        UUID pid = player.getUniqueId();
        primarySignatures.remove(pid);
        BossBar top = primaryBars.remove(pid);
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
        primarySignatures.clear();
        secondarySignatures.clear();
    }
}
