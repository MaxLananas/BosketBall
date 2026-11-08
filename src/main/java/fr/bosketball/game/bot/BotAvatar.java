package fr.bosketball.game.bot;

import com.destroystokyo.paper.SkinParts;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.game.MatchSession;
import fr.bosketball.util.Msg;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

public final class BotAvatar {

    public static final String ENTITY_TAG = "bosketball_ai_player";

    private final MatchSession session;
    private final Arena arena;
    private final BotProfile profile;

    private Mannequin mannequin;
    private Vector movementTarget;

    public BotAvatar(MatchSession session, Arena arena, BotProfile profile) {
        this.session = session;
        this.arena = arena;
        this.profile = profile;
    }

    public void spawn(Location at) {
        if (at == null) {
            return;
        }
        World world = at.getWorld() != null ? at.getWorld() : arena.world();
        if (world == null) {
            return;
        }
        despawn();

        Location spawnLoc = at.clone();
        spawnLoc.setYaw(profile.difficulty() == BotDifficulty.PRO ? 180f : 0f);

        this.mannequin = world.spawn(spawnLoc, Mannequin.class, man -> {
            man.setPersistent(false);
            man.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            man.addScoreboardTag(ENTITY_TAG);
            man.setImmovable(false);
            man.setGravity(true);
            man.setInvulnerable(true);
            man.setSilent(true);
            man.setCollidable(false);
            man.setGlowing(false);
            try {
                man.setSkinParts(SkinParts.allParts());
            } catch (Throwable ignored) {

            }
            man.setProfile(fr.bosketball.util.Skins.buildCustomPatchedSkin(
                profile.nickname(), profile.skinTexture()));
            man.customName(profile.nameplate());
            man.setCustomNameVisible(true);
            try {
                man.setDescription(profile.subtitle());
            } catch (Throwable ignored) {

            }
            if (man.getEquipment() != null) {
                man.getEquipment().setItem(EquipmentSlot.FEET, modelItem(
                    Material.LEATHER_BOOTS, "sneakers_air", "Court Sneakers"));
            }
        });
    }

    public void despawn() {
        if (mannequin != null) {
            if (mannequin.isValid()) {
                mannequin.remove();
            }
            mannequin = null;
        }
    }

    public boolean isValid() {
        return mannequin != null && mannequin.isValid() && !mannequin.isDead();
    }

    public Mannequin mannequin() {
        return mannequin;
    }

    public BotProfile profile() {
        return profile;
    }

    public UUID entityId() {
        return mannequin != null ? mannequin.getUniqueId() : null;
    }

    public Vector position() {
        return isValid() ? mannequin.getLocation().toVector()
            : new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ());
    }

    public Location location() {
        return isValid() ? mannequin.getLocation() : null;
    }

    public void steerTo(Vector target, boolean sprint, Vector lookAt, int ticks) {
        if (!isValid()) {
            return;
        }
        movementTarget = target.clone();
        Vector here = position();
        Vector flat = new Vector(target.getX() - here.getX(), 0, target.getZ() - here.getZ());
        double dist = flat.length();
        double maxStep = sprint ? 0.34 : 0.22;

        if (dist > 0.08) {
            Vector step = flat.clone().multiply(Math.min(maxStep, dist * 0.42) / dist);
            Vector current = mannequin.getVelocity();
            mannequin.setVelocity(new Vector(step.getX(), current.getY(), step.getZ()));
            if (sprint && mannequin.isOnGround() && ticks % 9 == 0) {
                Msg.playPackSound(mannequin.getLocation(), "shoe.squeak", 0.42f, 1.0f + (profile.jerseyNumber() % 8) * 0.02f);
            }
        } else {
            Vector current = mannequin.getVelocity();
            mannequin.setVelocity(new Vector(current.getX() * 0.4, current.getY(), current.getZ() * 0.4));
        }

        facePoint(lookAt);
        setPose(sprint && dist > 1.4 ? Pose.STANDING : Pose.STANDING);
    }

    public Vector movementTarget() {
        return movementTarget == null ? null : movementTarget.clone();
    }

    public void facePoint(Vector lookAt) {
        if (!isValid() || lookAt == null) {
            return;
        }
        Vector eye = mannequin.getEyeLocation().toVector();
        Vector dir = lookAt.clone().subtract(eye);
        if (dir.lengthSquared() < 1e-4) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        float pitch = (float) Math.clamp(
            -Math.toDegrees(Math.atan2(dir.getY(), Math.hypot(dir.getX(), dir.getZ()))), -40.0, 40.0);
        Location current = mannequin.getLocation();
        float nextYaw = BotVision.turnYaw(current.getYaw(), yaw, 12f);
        float nextPitch = current.getPitch() + Math.clamp(pitch - current.getPitch(), -8f, 8f);
        mannequin.setRotation(nextYaw, nextPitch);
    }

    public void setPose(Pose pose) {
        if (!isValid()) {
            return;
        }
        try {
            mannequin.setPose(pose, false);
        } catch (Throwable ignored) {

        }
    }

    public void crouch(boolean active) {
        setPose(active ? Pose.SNEAKING : Pose.STANDING);
    }

    public void jump(double power) {
        if (!isValid() || !mannequin.isOnGround()) {
            return;
        }
        Vector v = mannequin.getVelocity();
        mannequin.setVelocity(new Vector(v.getX() * 0.55, power, v.getZ() * 0.55));
        Msg.playPackSound(mannequin.getLocation(), "shoe.squeak", 0.7f, 1.12f);
    }

    public void swingArm(boolean mainHand) {
        if (!isValid()) {
            return;
        }
        if (mainHand) {
            mannequin.swingMainHand();
        } else {
            mannequin.swingOffHand();
        }
    }

    public void celebrate() {
        if (!isValid()) {
            return;
        }
        jump(0.44);
        swingArm(true);
        swingArm(false);
        Location loc = mannequin.getLocation().add(0, 2.1, 0);
        mannequin.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 14, 0.3, 0.2, 0.3, 0.02);
    }

    public void reactToMistake() {
        setPose(Pose.SWIMMING);
        Bukkit.getScheduler().runTaskLater(
            fr.bosketball.BosketBallPlugin.getInstance(),
            () -> setPose(Pose.STANDING),
            26L);
    }

    public void clampToCourt() {
        if (!isValid()) {
            return;
        }
        var g = arena.geometry();
        Location loc = mannequin.getLocation();
        double cx = Math.clamp(loc.getX(), g.minX() + 0.7, g.maxX() - 0.7);
        double cz = Math.clamp(loc.getZ(), g.minZ() + 0.7, g.maxZ() - 0.7);
        double cy = Math.max(g.floorY(), loc.getY() - 0.02);
        if (Math.abs(cx - loc.getX()) > 0.02 || Math.abs(cz - loc.getZ()) > 0.02 || cy > loc.getY() + 0.02) {
            mannequin.teleport(new Location(loc.getWorld(), cx, cy, cz, loc.getYaw(), loc.getPitch()));
        }
    }

    public void tickNameplate() {
        if (!isValid()) {
            return;
        }
        Component name = profile.nameplate();
        if (!name.equals(mannequin.customName())) {
            mannequin.customName(name);
        }
        mannequin.setCustomNameVisible(true);
    }

    public fr.bosketball.model.TeamSide teamOf(Player player) {
        return session == null || player == null ? null : session.getTeam(player.getUniqueId());
    }

    public List<Player> viewers() {
        if (!isValid()) {
            return List.of();
        }
        return session == null ? List.of() : session.onlineParticipants();
    }

    private static ItemStack modelItem(Material mat, String modelId, String displayName) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", modelId));
            meta.displayName(Msg.parse("<#F8FAFC>" + displayName + "</#F8FAFC>"));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
