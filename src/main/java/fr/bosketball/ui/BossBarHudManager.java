package fr.bosketball.ui;

import fr.bosketball.util.Palette;
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

    /** Every entry point shares this guard: a boss bar update for a player who just left is a NPE. */
    private static boolean usable(Player player) {
        return player != null && player.isOnline();
    }

    public void showQueueStatus(Player player, String modeTitle, int current, int max, long elapsedSec) {
        long started = fr.bosketball.util.TickProfiler.beginBudget();
        try {
            showQueueStatusMeasured(player, modeTitle, current, max, elapsedSec);
        } finally {
            fr.bosketball.util.TickProfiler.endBudget("hud", started);
        }
    }

    private void showQueueStatusMeasured(Player player, String modeTitle, int current, int max, long elapsedSec) {
        if (!usable(player)) {
            return;
        }
        UUID pid = player.getUniqueId();
        String sig = "Q|" + modeTitle + "|" + current + "|" + max + "|" + elapsedSec;
        BossBar existing = primaryBars.get(pid);
        if (existing != null && sig.equals(primarySignatures.get(pid))) {
            hideSecondary(player);
            return;
        }
        String timer = String.format(java.util.Locale.ROOT, "%d:%02d", elapsedSec / 60L, elapsedSec % 60L);
        Component title = Msg.parse(fr.bosketball.util.Glyphs.smallCapsLabels(
            "<" + Palette.SUCCESS + ">●</" + Palette.SUCCESS + "> " + Msg.gradBold(Msg.GRAD_MINT, "Queued for " + modeTitle)
                + " <#94A3B8>(" + current + "/" + max + ")</#94A3B8>  <#64748B>\u00b7</#64748B>  "
                + "<" + Palette.GOLD_SOFT + ">" + timer + "</" + Palette.GOLD_SOFT + ">"
        ));
        primarySignatures.put(pid, sig);
        if (existing == null) {
            BossBar b = BossBar.bossBar(title, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
            primaryBars.put(pid, b);
            player.showBossBar(b);
        } else {
            existing.name(title);
            // Round 42: the bar object is cached per UUID, but a boss bar is shown per ENTITY. After
            // a relog the cached bar came back with the bar never shown to the new player, so the
            // queue timer silently disappeared until the entry was cleared. showBossBar is
            // idempotent and cheap.
            player.showBossBar(existing);
        }
        hideSecondary(player);
    }

    public void showLiveScorebug(
        Player player, String homeTag, int homeScore, String clockText, int shotClock, int awayScore, String awayTag
    ) {
        long started = fr.bosketball.util.TickProfiler.beginBudget();
        try {
            showLiveScorebugMeasured(player, homeTag, homeScore, clockText, shotClock, awayScore, awayTag);
        } finally {
            fr.bosketball.util.TickProfiler.endBudget("hud", started);
        }
    }

    private void showLiveScorebugMeasured(
        Player player, String homeTag, int homeScore, String clockText, int shotClock, int awayScore, String awayTag
    ) {
        if (!usable(player)) {
            return;
        }
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
            player.showBossBar(existing);
        }
        hideSecondary(player);
    }

    public void showScorerCelebration(
        Player player, String scorerName, String roleSmallCaps, Glyphs.BannerVariant variant,
        String subLabelSmallCaps, String subValueText
    ) {
        long started = fr.bosketball.util.TickProfiler.beginBudget();
        try {
            showScorerCelebrationMeasured(player, scorerName, roleSmallCaps, variant, subLabelSmallCaps, subValueText);
        } finally {
            fr.bosketball.util.TickProfiler.endBudget("hud", started);
        }
    }

    private void showScorerCelebrationMeasured(
        Player player, String scorerName, String roleSmallCaps, Glyphs.BannerVariant variant,
        String subLabelSmallCaps, String subValueText
    ) {
        if (!usable(player)) {
            return;
        }
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
                player.showBossBar(existingTop);
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
                player.showBossBar(existingSub);
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
