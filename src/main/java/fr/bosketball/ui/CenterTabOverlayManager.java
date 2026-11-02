package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 100% Custom Center-Screen Tab Overlay (Requirement #1):
 * Renders a crisp floating broadcast scoreboard dead-center on the player's screen showing:
 *   - Real Inline 8x8 Player Heads (Component.object(ObjectContents.playerHead(uuid)))
 *   - Team Colors (IGNIS Crimson vs FROST Sky-Blue)
 *   - Player Names, Glossy #1 Pills, Live Match / Career Points, Assists & Ping!
 * Toggleable instantly via [F] (Swap Hand) or /bb tab, plus auto-shown at period breaks & match end!
 */
public final class CenterTabOverlayManager {

    private static final class OverlayInstance {
        TextDisplay panel;
        int autoCloseTicks = -1; // -1 = manual toggle, >0 = auto-hide timer
        int renderTick = 0;
    }

    private final BosketBallPlugin plugin;
    private final Map<UUID, OverlayInstance> activeOverlays = new ConcurrentHashMap<>();

    public CenterTabOverlayManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOpen(UUID playerId) {
        return activeOverlays.containsKey(playerId);
    }

    public void toggle(Player player) {
        if (isOpen(player.getUniqueId())) {
            close(player);
            Msg.playPersonalPackSound(player, "ui.click", 0.65f, 0.9f);
        } else {
            open(player, -1);
            Msg.playPersonalPackSound(player, "ui.click", 0.75f, 1.22f);
        }
    }

    public void showTimed(Player player, int durationTicks) {
        open(player, durationTicks);
    }

    public void open(Player player, int autoCloseTicks) {
        close(player);
        World w = player.getWorld();
        if (w == null) {
            return;
        }
        Location anchor = computeCenterScreenLocation(player);
        TextDisplay td = w.spawn(anchor, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setShadowed(true);
            d.setLineWidth(360);
            d.setTeleportDuration(1);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setBackgroundColor(Color.fromARGB(234, 10, 14, 24));
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(0.58f, 0.58f, 0.58f),
                new Quaternionf()
            ));
        });

        // Private HUD: hide from all other players so only this viewer sees their centered Tab!
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                other.hideEntity(plugin, td);
            }
        }

        OverlayInstance inst = new OverlayInstance();
        inst.panel = td;
        inst.autoCloseTicks = autoCloseTicks;
        activeOverlays.put(player.getUniqueId(), inst);
        td.text(buildCenterTabComponent(player));
    }

    public void hideNewPanelFromLateJoiner(Player joiner) {
        for (Map.Entry<UUID, OverlayInstance> e : activeOverlays.entrySet()) {
            if (!e.getKey().equals(joiner.getUniqueId()) && e.getValue().panel != null && e.getValue().panel.isValid()) {
                joiner.hideEntity(plugin, e.getValue().panel);
            }
        }
    }

    public void close(Player player) {
        if (player == null) {
            return;
        }
        closeById(player.getUniqueId());
    }

    public void closeById(UUID playerId) {
        OverlayInstance inst = activeOverlays.remove(playerId);
        if (inst != null && inst.panel != null && inst.panel.isValid()) {
            inst.panel.remove();
        }
    }

    public void closeAll() {
        for (UUID id : new ArrayList<>(activeOverlays.keySet())) {
            closeById(id);
        }
    }

    public void tickAll() {
        for (UUID id : new ArrayList<>(activeOverlays.keySet())) {
            Player p = Bukkit.getPlayer(id);
            OverlayInstance inst = activeOverlays.get(id);
            if (p == null || !p.isOnline() || inst == null || inst.panel == null || !inst.panel.isValid()) {
                closeById(id);
                continue;
            }
            if (inst.autoCloseTicks > 0) {
                inst.autoCloseTicks--;
                if (inst.autoCloseTicks == 0) {
                    closeById(id);
                    continue;
                }
            }
            if (!inst.panel.getWorld().equals(p.getWorld())) {
                closeById(id);
                continue;
            }
            inst.panel.teleport(computeCenterScreenLocation(p));
            inst.renderTick++;
            if (inst.renderTick % 10 == 0) {
                inst.panel.text(buildCenterTabComponent(p));
            }
        }
    }

    private Location computeCenterScreenLocation(Player viewer) {
        Location eye = viewer.getEyeLocation();
        double yawRad = Math.toRadians(eye.getYaw());
        double pitchRad = Math.toRadians(eye.getPitch());
        Vector forward = new Vector(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );
        Vector cameraUp = new Vector(
            -Math.sin(yawRad) * Math.sin(pitchRad),
            Math.cos(pitchRad),
            Math.cos(yawRad) * Math.sin(pitchRad)
        );
        // Shift down along -cameraUp by half the rendered panel height so the panel sits dead-center on crosshair!
        return eye.clone().add(forward.multiply(2.15)).subtract(cameraUp.multiply(0.56));
    }

    public Component buildCenterTabComponent(Player viewer) {
        MatchSession match = plugin.gameManager().getSessionOf(viewer.getUniqueId());
        TextComponent.Builder out = Component.text();

        // Top Emblem + Header
        out.append(Component.text(String.valueOf(Glyphs.LOGO_HEADER), Glyphs.BITMAP_STYLE))
            .append(Component.newline());

        if (match != null) {
            out.append(Glyphs.composeLiveScorebug(
                "ɪɢɴɪꜱ", match.score(TeamSide.IGNIS),
                match.formattedClock(), match.shotClockSeconds(),
                match.score(TeamSide.FROST), "ꜰʀᴏꜱᴛ"
            )).append(Component.newline());

            out.append(Glyphs.composeDividerHeader("Ignis vs Frost")).append(Component.newline());

            List<Player> ignisPlayers = match.onlineParticipants().stream()
                .filter(p -> match.getTeam(p.getUniqueId()) == TeamSide.IGNIS)
                .sorted(Comparator.comparingInt((Player p) -> match.getBoxScore(p.getUniqueId()).points).reversed())
                .toList();
            List<Player> frostPlayers = match.onlineParticipants().stream()
                .filter(p -> match.getTeam(p.getUniqueId()) == TeamSide.FROST)
                .sorted(Comparator.comparingInt((Player p) -> match.getBoxScore(p.getUniqueId()).points).reversed())
                .toList();

            out.append(Msg.parse("<#FF5444><bold>🔥 ÉQUIPE IGNIS (" + match.score(TeamSide.IGNIS) + " PTS)</bold></#FF5444>\n"));
            if (ignisPlayers.isEmpty()) {
                out.append(Msg.parse("<dark_gray>  • ᴀᴜᴄᴜɴ ᴊᴏᴜᴇᴜʀ</dark_gray>\n"));
            } else {
                for (Player p : ignisPlayers) {
                    appendMatchPlayerRow(out, p, TeamSide.IGNIS, match.getBoxScore(p.getUniqueId()));
                }
            }

            out.append(Component.newline());
            out.append(Msg.parse("<#3DB8FF><bold>❄ ÉQUIPE FROST (" + match.score(TeamSide.FROST) + " PTS)</bold></#3DB8FF>\n"));
            if (frostPlayers.isEmpty()) {
                if (match.hasSparringBot()) {
                    out.append(Component.text("  "));
                    out.append(ChatTag.LOCKDOWN.pillComponent()).append(Component.text(" "));
                    out.append(Component.text("🛡 Défenseur Pro (IA)", Style.style(TeamSide.FROST.textColor(), TextDecoration.BOLD)));
                    out.append(Component.text("  •  CONTEST & STEAL ACTIF\n", NamedTextColor.GRAY));
                } else {
                    out.append(Msg.parse("<dark_gray>  • ᴀᴜᴄᴜɴ ᴊᴏᴜᴇᴜʀ (ꜱʟᴏᴛ 5 = ᴅᴇꜰᴇɴꜱᴇᴜʀ ɪᴀ)</dark_gray>\n"));
                }
            } else {
                for (Player p : frostPlayers) {
                    appendMatchPlayerRow(out, p, TeamSide.FROST, match.getBoxScore(p.getUniqueId()));
                }
            }
        } else {
            out.append(Glyphs.composeDividerHeader("Joueurs & Classement")).append(Component.newline());
            List<Player> sortedOnline = new ArrayList<>(Bukkit.getOnlinePlayers());
            sortedOnline.sort(Comparator.comparingInt((Player p) -> plugin.statsManager().getOrCreate(p).points()).reversed());

            int shown = 0;
            for (Player p : sortedOnline) {
                if (shown++ >= 10) {
                    break;
                }
                PlayerProfile prof = plugin.statsManager().getOrCreate(p);
                MatchSession ps = plugin.gameManager().getSessionOf(p.getUniqueId());
                TeamSide side = ps != null ? ps.getTeam(p.getUniqueId()) : null;
                TextColor nameCol = (side != null && side != TeamSide.SPECTATOR) ? side.textColor() : prof.effectiveRank().accentColor();
                int ptsShown = ps != null ? ps.getBoxScore(p.getUniqueId()).points : prof.points();

                out.append(Component.text(" "));
                out.append(Component.object(ObjectContents.playerHead(p.getUniqueId())));
                out.append(Component.text(" "));
                appendTopBadgeIfLeader(out, p.getUniqueId());
                if (prof.selectedTag() != null) {
                    out.append(prof.selectedTag().pillComponent()).append(Component.text(" "));
                }
                if (side != null && side != TeamSide.SPECTATOR) {
                    out.append(side.pillComponent()).append(Component.text(" "));
                } else {
                    out.append(prof.effectiveRank().pillComponent()).append(Component.text(" "));
                }
                out.append(Component.text(p.getName(), Style.style(nameCol, TextDecoration.BOLD)));
                out.append(Component.text("  •  ", NamedTextColor.DARK_GRAY));
                out.append(Component.text(ptsShown + " PTS", Style.style(TextColor.color(0xFFE052), TextDecoration.BOLD)));
                out.append(Component.text("  •  " + prof.elo() + " ELO  •  " + p.getPing() + "ms\n", NamedTextColor.GRAY));
            }
        }

        out.append(Glyphs.composeDividerBottom()).append(Component.newline());
        out.append(Msg.parse("<gray>ᴀᴘᴘᴜʏᴇᴢ ꜱᴜʀ <yellow>[F]</yellow> ᴏᴜ <aqua>/bb tab</aqua> ᴘᴏᴜʀ ꜰᴇʀᴍᴇʀ</gray>"));
        return out.asComponent();
    }

    private void appendMatchPlayerRow(TextComponent.Builder out, Player p, TeamSide side, MatchSession.MatchBoxScore box) {
        PlayerProfile prof = plugin.statsManager().getOrCreate(p);
        out.append(Component.text("  "));
        out.append(Component.object(ObjectContents.playerHead(p.getUniqueId())));
        out.append(Component.text(" "));
        appendTopBadgeIfLeader(out, p.getUniqueId());
        if (prof.selectedTag() != null) {
            out.append(prof.selectedTag().pillComponent()).append(Component.text(" "));
        }
        out.append(Component.text(p.getName(), Style.style(side.textColor(), TextDecoration.BOLD)));
        out.append(Component.text("   ", NamedTextColor.DARK_GRAY));
        out.append(Component.text(box.points + " PTS", Style.style(TextColor.color(0xFFE052), TextDecoration.BOLD)));
        out.append(Component.text("  •  " + box.assists + " AST  •  " + box.rebounds + " REB  •  " + p.getPing() + "ms\n", NamedTextColor.GRAY));
    }

    private void appendTopBadgeIfLeader(TextComponent.Builder out, UUID uuid) {
        StatsManager.LeaderboardBadges b = plugin.statsManager().getBadges(uuid);
        if (b.winsPlace() == 1) {
            out.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.WINS, 1)).append(Component.text(" "));
        } else if (b.eloPlace() == 1) {
            out.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, 1)).append(Component.text(" "));
        } else if (b.pointsPlace() == 1) {
            out.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.POINTS, 1)).append(Component.text(" "));
        }
    }
}
