package fr.bosketball.ui;

import com.destroystokyo.paper.SkinParts;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * NBA Broadcast Cinematic Basket / Slam-Dunk Camera ("Caméra Panier" — cinematicScoreCam)!
 *   - Spawns live 3D Mannequin doubles of all court players (wearing their exact skin, crown/hat SkinParts & sneakers!)
 *     because Spectator camera-lock hides vanilla player bodies!
 *   - Smoothly glides an interpolated ItemDisplay Camera Rig around the hoop & celebrating scorer Mannequin
 *     via player.setSpectatorTarget(cameraRig)!
 *   - Viewers can press [SHIFT] anytime to skip back to gameplay immediately.
 */
public final class CinematicCameraManager {

    private static final class ViewerCamState {
        UUID playerId;
        Location savedCourtLocation;
        GameMode savedGameMode;
        ItemDisplay cameraRig;
        Location orbitEndPose;
        boolean orbitStarted;
        int ticksRemaining;
        long startedAtMillis;
        final List<Entity> spawnedActors = new ArrayList<>();
    }

    private final BosketBallPlugin plugin;
    private final Map<UUID, ViewerCamState> activeViewers = new ConcurrentHashMap<>();

    public CinematicCameraManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isWatching(UUID playerId) {
        return playerId != null && activeViewers.containsKey(playerId);
    }

    /**
     * Triggers a smooth Broadcast Rim / Scorer Celebration Cutaway for all match participants
     * who have "Caméra Panier" (cinematicScoreCam) enabled in /bb settings!
     */
    public void playBasketCelebrationCut(
        Collection<Player> matchPlayers,
        Player scorer,
        TeamSide scoringSide,
        Vector rimPos,
        Vector courtCenter,
        boolean slamDunk,
        int durationTicks
    ) {
        if (matchPlayers == null || matchPlayers.isEmpty() || rimPos == null) {
            return;
        }
        int safeTicks = Math.clamp(durationTicks, 24, 70);

        for (Player viewer : matchPlayers) {
            if (viewer == null || !viewer.isOnline() || viewer.getWorld() == null) {
                continue;
            }
            PlayerProfile vProf = plugin.statsManager().getOrCreate(viewer);
            if (!vProf.settings().cinematicScoreCam()) {
                continue;
            }
            stopWatching(viewer, false);

            World w = viewer.getWorld();
            Vector toCenter = new Vector(courtCenter.getX() - rimPos.getX(), 0.0, courtCenter.getZ() - rimPos.getZ());
            if (toCenter.lengthSquared() < 1e-4) {
                toCenter = new Vector(0.0, 0.0, 1.0);
            } else {
                toCenter.normalize();
            }
            Vector rightVec = new Vector(-toCenter.getZ(), 0.0, toCenter.getX());

            // Heroic low-angle broadcast start & smooth dolly-orbit end pose framing both the rim AND the scorer Mannequin!
            Vector focus = rimPos.clone().add(new Vector(0.0, -0.85, 0.0));
            if (scorer != null && scorer.getWorld().equals(w)) {
                Vector sEye = scorer.getLocation().toVector().add(new Vector(0.0, 1.35, 0.0));
                focus = rimPos.clone().multiply(0.55).add(sEye.multiply(0.45));
            }

            double dist = slamDunk ? 4.35 : 5.15;
            Vector camStartPos = rimPos.clone()
                .add(toCenter.clone().multiply(dist))
                .add(rightVec.clone().multiply(-2.15))
                .add(new Vector(0.0, slamDunk ? -0.55 : -0.35, 0.0));
            Vector camEndPos = rimPos.clone()
                .add(toCenter.clone().multiply(dist + 0.45))
                .add(rightVec.clone().multiply(2.15))
                .add(new Vector(0.0, 0.25, 0.0));

            Location startPose = lookAt(w, camStartPos, focus);
            Location endPose = lookAt(w, camEndPos, focus);
            startPose.getChunk().load();

            ViewerCamState st = new ViewerCamState();
            st.playerId = viewer.getUniqueId();
            st.savedCourtLocation = viewer.getLocation().clone();
            st.savedGameMode = viewer.getGameMode();
            st.orbitEndPose = endPose;
            st.orbitStarted = false;
            st.ticksRemaining = safeTicks;
            st.startedAtMillis = System.currentTimeMillis();

            // 1. Spawn private 3D Mannequin doubles of all court players so viewer sees real bodies + crowns celebrating!
            for (Player actor : matchPlayers) {
                if (actor == null || !actor.isOnline() || !actor.getWorld().equals(w)) {
                    continue;
                }
                if (actor.getGameMode() == GameMode.SPECTATOR && !actor.getUniqueId().equals(viewer.getUniqueId())
                    && !isWatching(actor.getUniqueId())) {
                    continue;
                }
                Location actorLoc = activeViewers.containsKey(actor.getUniqueId())
                    ? activeViewers.get(actor.getUniqueId()).savedCourtLocation.clone()
                    : actor.getLocation().clone();
                boolean isScorer = scorer != null && actor.getUniqueId().equals(scorer.getUniqueId());
                if (isScorer) {
                    // Turn scorer Mannequin heroically toward the broadcast camera!
                    Vector toCam = camStartPos.clone().subtract(actorLoc.toVector());
                    actorLoc.setYaw((float) Math.toDegrees(Math.atan2(-toCam.getX(), toCam.getZ())));
                    actorLoc.setPitch(-12.0f);
                }

                PlayerProfile aStats = plugin.statsManager().getOrCreate(actor);
                BallSkin aSkin = aStats.selectedBallSkin();
                ResolvableProfile resProf = buildPlayerSkinProfile(actor);

                Mannequin m = w.spawn(actorLoc, Mannequin.class, man -> {
                    man.setPersistent(false);
                    man.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                    man.setImmovable(false);
                    man.setGravity(true);
                    man.setInvulnerable(true);
                    man.setSilent(true);
                    man.setCollidable(false);
                    try {
                        man.setSkinParts(SkinParts.allParts());
                    } catch (Throwable ignored) {
                    }
                    man.setProfile(resProf);
                    TextColor c = scoringSide == TeamSide.FROST ? TextColor.color(0x38BCFF) : TextColor.color(0xFF4C4C);
                    man.customName(Component.text((isScorer ? "★ " : "") + actor.getName(), Style.style(c, TextDecoration.BOLD)));
                    man.setCustomNameVisible(true);
                    if (isScorer) {
                        man.setDescription(Component.text(slamDunk ? "🔥 SLAM DUNK !" : "🏀 BUCKET !", TextColor.color(0xFFD43B)));
                    }
                    if (man.getEquipment() != null) {
                        man.getEquipment().setItem(EquipmentSlot.FEET, BallSkin.createCustomModelItem(
                            Material.LEATHER_BOOTS, "sneakers_air", "<gold>Air Bosket</gold>", List.of()
                        ));
                        if (isScorer) {
                            man.getEquipment().setItem(EquipmentSlot.HAND, aSkin.createMannequinHeldBallItem());
                        }
                    }
                });
                if (isScorer) {
                    m.setVelocity(new Vector(0.0, 0.32, 0.0));
                    m.swingMainHand();
                }
                hideFromAllExcept(viewer, m);
                st.spawnedActors.add(m);
            }

            // 2. Spawn smooth-interpolated ItemDisplay Camera Rig & lock viewer via setSpectatorTarget!
            ItemStack invisible = new ItemStack(Material.PAPER);
            ItemMeta im = invisible.getItemMeta();
            if (im != null) {
                im.setItemModel(new NamespacedKey("bosketball", "ui_invisible"));
                invisible.setItemMeta(im);
            }
            st.cameraRig = w.spawn(startPose, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                d.setBillboard(Display.Billboard.FIXED);
                d.setItemStack(invisible);
                d.setTeleportDuration(Math.min(58, safeTicks));
            });
            hideFromAllExcept(viewer, st.cameraRig);

            viewer.teleport(startPose);
            viewer.setGameMode(GameMode.SPECTATOR);
            viewer.setSpectatorTarget(st.cameraRig);
            activeViewers.put(viewer.getUniqueId(), st);
        }
    }

    public void tickAll() {
        for (UUID id : new ArrayList<>(activeViewers.keySet())) {
            ViewerCamState st = activeViewers.get(id);
            Player p = Bukkit.getPlayer(id);
            if (st == null || p == null || !p.isOnline()) {
                cleanupEntities(st);
                activeViewers.remove(id);
                continue;
            }

            // Start the smooth interpolated dolly-orbit on tick 2 once the client locks onto cameraRig!
            if (!st.orbitStarted && st.cameraRig != null && st.cameraRig.isValid()) {
                st.orbitStarted = true;
                st.cameraRig.teleport(st.orbitEndPose);
                for (Entity e : st.spawnedActors) {
                    if (e instanceof Mannequin m && m.isValid()) {
                        m.swingMainHand();
                    }
                }
            }

            if (st.cameraRig != null && st.cameraRig.isValid() && p.getSpectatorTarget() != st.cameraRig) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
                p.setSpectatorTarget(st.cameraRig);
            }

            st.ticksRemaining--;
            if (st.ticksRemaining <= 0) {
                stopWatching(p, true);
            }
        }
    }

    public boolean handleSpectateStopAttempt(Player player) {
        ViewerCamState st = activeViewers.get(player.getUniqueId());
        if (st == null) {
            return false;
        }
        // Ignore involuntary packet jitter in the first 250ms; after 250ms, [SHIFT] skips the replay cleanly!
        if (System.currentTimeMillis() - st.startedAtMillis < 250L) {
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> stopWatching(player, true));
        return true;
    }

    public void stopWatching(Player player, boolean restorePose) {
        if (player == null) {
            return;
        }
        ViewerCamState st = activeViewers.remove(player.getUniqueId());
        if (st == null) {
            return;
        }
        if (player.isOnline()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
            if (restorePose) {
                GameMode targetGm = st.savedGameMode != null ? st.savedGameMode : GameMode.ADVENTURE;
                player.setGameMode(targetGm);
                if (st.savedCourtLocation != null && st.savedCourtLocation.getWorld() != null) {
                    player.teleport(st.savedCourtLocation);
                }
            }
        }
        cleanupEntities(st);
    }

    public void stopAll() {
        for (UUID id : new ArrayList<>(activeViewers.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                stopWatching(p, true);
            } else {
                cleanupEntities(activeViewers.remove(id));
            }
        }
    }

    public void hideActiveFromLateJoiner(Player joiner) {
        for (Map.Entry<UUID, ViewerCamState> e : activeViewers.entrySet()) {
            if (e.getKey().equals(joiner.getUniqueId())) {
                continue;
            }
            ViewerCamState st = e.getValue();
            if (st.cameraRig != null && st.cameraRig.isValid()) {
                joiner.hideEntity(plugin, st.cameraRig);
            }
            for (Entity a : st.spawnedActors) {
                if (a != null && a.isValid()) {
                    joiner.hideEntity(plugin, a);
                }
            }
        }
    }

    private void cleanupEntities(ViewerCamState st) {
        if (st == null) {
            return;
        }
        if (st.cameraRig != null && st.cameraRig.isValid()) {
            st.cameraRig.remove();
        }
        for (Entity e : st.spawnedActors) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        st.spawnedActors.clear();
    }

    private void hideFromAllExcept(Player viewer, Entity entity) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                other.hideEntity(plugin, entity);
            }
        }
    }

    private static ResolvableProfile buildPlayerSkinProfile(Player p) {
        var paperProf = p.getPlayerProfile();
        if (!paperProf.getProperties().isEmpty()) {
            return ResolvableProfile.resolvableProfile(paperProf);
        }
        return ResolvableProfile.resolvableProfile().name(p.getName()).build();
    }

    private static Location lookAt(World w, Vector eye, Vector target) {
        Vector d = target.clone().subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float pitch = (float) Math.clamp(-Math.toDegrees(Math.atan2(d.getY(), Math.hypot(d.getX(), d.getZ()))), -85.0, 85.0);
        return new Location(w, eye.getX(), eye.getY(), eye.getZ(), yaw, pitch);
    }
}
