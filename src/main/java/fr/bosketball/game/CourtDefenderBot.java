package fr.bosketball.game;

import com.destroystokyo.paper.SkinParts;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.util.Msg;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CourtDefenderBot {

    private final Arena arena;
    private final Vector position;
    private final Vector slideVelocity = new Vector();

    private Mannequin mannequin;
    private ItemDisplay floorRing;

    private int stumbleTicks = 0;
    private int stealCooldownTicks = 30;
    private int jumpCooldownTicks = 0;
    private float ringSpinAngle = 0.0f;

    public CourtDefenderBot(Arena arena, Vector initialPos) {
        this.arena = arena;
        this.position = initialPos.clone();
        spawn();
    }

    private void spawn() {
        World w = arena.world();
        if (w == null) {
            return;
        }
        Location loc = position.toLocation(w);

        this.mannequin = w.spawn(loc, Mannequin.class, man -> {
            man.setPersistent(false);
            man.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            man.setImmovable(false);
            man.setGravity(true);
            man.setInvulnerable(true);
            man.setSilent(true);
            man.setCollidable(false);
            man.setGlowing(true);
            try {
                man.setSkinParts(SkinParts.allParts());
            } catch (Throwable ignored) {
            }
            man.setProfile(fr.bosketball.util.Skins.buildCustomPatchedSkin("DefenderPro", "defender_bot"));
            if (man.getEquipment() != null) {
                man.getEquipment().setItem(EquipmentSlot.FEET, BallSkin.createCustomModelItem(
                    Material.LEATHER_BOOTS,
                    "sneakers_air",
                    "<aqua><bold>Court Sneakers</bold></aqua>",
                    List.of()
                ));
            }
        });
        refreshNameplate(false);

        this.floorRing = w.spawn(loc.clone().add(0, 0.03, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setInterpolationDelay(0);
            d.setViewRange(2.4f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setItemStack(modelItem(Material.PAPER, "ring_frost"));
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1.46f, 1.0f, 1.46f),
                new Quaternionf()
            ));
        });
    }

    public UUID entityUniqueId() {
        return mannequin != null ? mannequin.getUniqueId() : null;
    }

    private void refreshNameplate(boolean stumbled) {
        if (mannequin == null || !mannequin.isValid()) {
            return;
        }
        if (stumbled) {
            mannequin.customName(Msg.parse("<#F87171><bold>💫 ANKLES BROKEN!</bold></#F87171>"));
            mannequin.setCustomNameVisible(true);
            mannequin.setDescription(ChatTag.ANKLE.pillComponent());
        } else {
            mannequin.customName(Component.text("🛡 AI Defender", Style.style(TextColor.color(0x38BCFF), TextDecoration.BOLD)));
            mannequin.setCustomNameVisible(true);
            mannequin.setDescription(ChatTag.LOCKDOWN.pillComponent());
        }
    }

    public boolean isValid() {
        return mannequin != null && mannequin.isValid();
    }

    public Vector position() {
        if (mannequin != null && mannequin.isValid()) {
            position.copy(mannequin.getLocation().toVector());
        }
        return position.clone();
    }

    public boolean tryBreakAnkles(Player handler) {
        if (!isValid() || stumbleTicks > 0) {
            return false;
        }
        Vector botPos = position();
        if (handler.getLocation().toVector().distanceSquared(botPos) > 3.25 * 3.25) {
            return false;
        }
        this.stumbleTicks = 38;
        double yawRad = Math.toRadians(handler.getLocation().getYaw());
        Vector sideSlide = new Vector(Math.cos(yawRad), 0.14, Math.sin(yawRad)).multiply(Math.random() < 0.5 ? 0.42 : -0.42);
        this.slideVelocity.copy(sideSlide);
        mannequin.setVelocity(sideSlide);
        try {
            mannequin.setPose(Pose.SWIMMING, true);
        } catch (Throwable ignored) {
        }
        mannequin.swingOffHand();
        refreshNameplate(true);

        handler.getWorld().spawnParticle(Particle.CLOUD, botPos.toLocation(handler.getWorld()).add(0, 0.2, 0), 20, 0.35, 0.1, 0.35, 0.06);
        Msg.playPackSound(botPos.toLocation(handler.getWorld()), "shoe.squeak", 1.0f, 0.82f);
        return true;
    }

    public double computeContestAgainst(Player shooter) {
        if (!isValid() || stumbleTicks > 0) {
            return 0.0;
        }
        Vector botPos = position();
        double dist = Math.hypot(shooter.getLocation().getX() - botPos.getX(), shooter.getLocation().getZ() - botPos.getZ());
        if (dist > 2.85) {
            return 0.0;
        }
        Vector shooterToBot = botPos.clone().subtract(shooter.getLocation().toVector()).setY(0);
        Vector look = shooter.getLocation().getDirection().setY(0);
        double frontFactor = (shooterToBot.lengthSquared() > 1e-4 && look.lengthSquared() > 1e-4)
            ? Math.max(0.35, shooterToBot.normalize().dot(look.normalize()))
            : 0.7;
        double proximity = (2.85 - dist) / 2.25;
        boolean airborne = !mannequin.isOnGround() || botPos.getY() > arena.geometry().floorY() + 0.18;
        double jumpBonus = airborne ? 0.18 : 0.0;
        return Math.max(0.0, Math.min(0.82, proximity * 0.68 * frontFactor + jumpBonus));
    }

    public boolean isCuttingOffDunkLane(Player driver, Vector targetRim) {
        if (!isValid() || stumbleTicks > 0) {
            return false;
        }
        Vector p = driver.getLocation().toVector().setY(0);
        Vector r = targetRim.clone().setY(0);
        Vector b = position().setY(0);

        if (p.distance(b) > 1.55) {
            return false;
        }
        Vector toRim = r.clone().subtract(p);
        Vector toBot = b.clone().subtract(p);
        if (toRim.lengthSquared() < 1e-4 || toBot.lengthSquared() < 1e-4) {
            return false;
        }
        return toRim.normalize().dot(toBot.normalize()) >= 0.68 && b.distance(r) < p.distance(r);
    }

    public void triggerContestJump() {
        if (!isValid() || stumbleTicks > 0 || jumpCooldownTicks > 0) {
            return;
        }
        if (mannequin.isOnGround() || position().getY() <= arena.geometry().floorY() + 0.12) {
            this.jumpCooldownTicks = 24;
            try {
                mannequin.setPose(Pose.STANDING, false);
            } catch (Throwable ignored) {
            }
            Vector v = mannequin.getVelocity();
            mannequin.setVelocity(new Vector(v.getX() * 0.5, 0.42, v.getZ() * 0.5));
            mannequin.swingMainHand();
            Msg.playPackSound(mannequin.getLocation(), "shoe.squeak", 0.75f, 1.16f);
        }
    }

    public void tick(Player targetPlayer, Vector defendedRim, BasketballEntity ball, boolean chargingShot) {
        if (!isValid()) {
            spawn();
            if (!isValid()) {
                return;
            }
        }
        if (stealCooldownTicks > 0) {
            stealCooldownTicks--;
        }
        if (jumpCooldownTicks > 0) {
            jumpCooldownTicks--;
        }

        position.copy(mannequin.getLocation().toVector());
        clampMannequinOnCourt();

        if (stumbleTicks > 0) {
            stumbleTicks--;
            if (stumbleTicks == 0) {
                try {
                    mannequin.setPose(Pose.STANDING, false);
                } catch (Throwable ignored) {
                }
                refreshNameplate(false);
            }
            updateFloorRing();
            return;
        }

        if (targetPlayer != null && targetPlayer.isOnline()) {
            double floorY = arena.geometry().floorY();
            Vector pPos = targetPlayer.getLocation().toVector();
            Vector pFloor = new Vector(pPos.getX(), floorY, pPos.getZ());
            Vector rimFloor = new Vector(defendedRim.getX(), floorY, defendedRim.getZ());

            Vector handlerToRim = rimFloor.clone().subtract(pFloor);
            double distToRim = handlerToRim.length();
            Vector dirToRim = distToRim > 1e-4 ? handlerToRim.clone().multiply(1.0 / distToRim) : new Vector(0, 0, 1);

            double cushion = Math.min(1.95, Math.max(1.05, distToRim - 1.35));
            Vector desired = pFloor.clone().add(dirToRim.clone().multiply(cushion));

            Vector delta = new Vector(desired.getX() - position.getX(), 0.0, desired.getZ() - position.getZ());
            double dLen = delta.length();
            double curVy = mannequin.getVelocity().getY();
            if (dLen > 0.06) {
                double speed = Math.min(0.24, dLen * 0.45);
                Vector moveVel = delta.multiply(speed / dLen);
                mannequin.setVelocity(new Vector(moveVel.getX(), curVy, moveVel.getZ()));
            } else {
                mannequin.setVelocity(new Vector(0.0, curVy, 0.0));
            }

            Vector toHandler = targetPlayer.getEyeLocation().toVector().subtract(mannequin.getEyeLocation().toVector());
            float targetYaw = (float) Math.toDegrees(Math.atan2(-toHandler.getX(), toHandler.getZ()));
            float targetPitch = (float) Math.clamp(-Math.toDegrees(Math.atan2(toHandler.getY(), Math.hypot(toHandler.getX(), toHandler.getZ()))), -35.0, 35.0);
            mannequin.setRotation(targetYaw, targetPitch);

            boolean closeGuard = pFloor.distance(new Vector(position.getX(), floorY, position.getZ())) <= 3.6
                && mannequin.isOnGround() && jumpCooldownTicks <= 12;
            try {
                mannequin.setPose(closeGuard && !chargingShot ? Pose.SNEAKING : Pose.STANDING, false);
            } catch (Throwable ignored) {
            }

            if (chargingShot && computeContestAgainst(targetPlayer) >= 0.22) {
                triggerContestJump();
            }

            if (ball != null
                && targetPlayer.getUniqueId().equals(ball.holderId())
                && ball.mode() == BasketballEntity.BallMode.DRIBBLING
                && !chargingShot
                && stealCooldownTicks <= 0) {
                double ballToBot = Math.hypot(ball.position().getX() - position.getX(), ball.position().getZ() - position.getZ());
                if (ballToBot <= 1.22 && !targetPlayer.isSneaking()) {
                    stealCooldownTicks = 55;
                    mannequin.swingMainHand();
                    double pokeChance = targetPlayer.isSprinting() ? 0.58 : 0.34;
                    if (Math.random() < pokeChance) {
                        Vector pokeImpulse = dirToRim.clone().multiply(0.36)
                            .add(new Vector((Math.random() - 0.5) * 0.35, 0.24, (Math.random() - 0.5) * 0.35));
                        ball.popLoose(pokeImpulse, null);
                        Msg.playPackSound(mannequin.getLocation(), "shoe.squeak", 1.0f, 1.22f);
                        Msg.playPackSound(mannequin.getLocation(), "ball.bounce", 0.95f, 1.25f);
                        targetPlayer.sendActionBar(Msg.parse("<#F87171><bold>Ball poked loose! Recover it!</bold></#F87171>"));
                    }
                }
            }
        }
        updateFloorRing();
    }

    private void clampMannequinOnCourt() {
        CourtGeometry g = arena.geometry();
        double cx = Math.clamp(position.getX(), g.minX() + 0.6, g.maxX() - 0.6);
        double cz = Math.clamp(position.getZ(), g.minZ() + 0.6, g.maxZ() - 0.6);
        double cy = Math.max(g.floorY(), position.getY());
        if (Math.abs(cx - position.getX()) > 0.02 || Math.abs(cz - position.getZ()) > 0.02 || cy > position.getY() + 0.02) {
            Location cur = mannequin.getLocation();
            mannequin.teleport(new Location(cur.getWorld(), cx, cy, cz, cur.getYaw(), cur.getPitch()));
            position.setX(cx).setY(cy).setZ(cz);
        }
    }

    private void updateFloorRing() {
        if (floorRing != null && floorRing.isValid() && mannequin != null && mannequin.isValid()) {
            ringSpinAngle += 0.032f;
            Location mLoc = mannequin.getLocation();
            double ry = Math.min(mLoc.getY(), arena.geometry().floorY()) + 0.03;
            floorRing.teleport(new Location(mLoc.getWorld(), mLoc.getX(), ry, mLoc.getZ(), 0f, 0f));
            floorRing.setInterpolationDelay(0);
            floorRing.setInterpolationDuration(2);
            floorRing.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf().rotateY(ringSpinAngle),
                new Vector3f(1.46f, 1.0f, 1.46f),
                new Quaternionf()
            ));
        }
    }

    public void remove() {
        if (mannequin != null) {
            mannequin.remove();
            mannequin = null;
        }
        if (floorRing != null) {
            floorRing.remove();
            floorRing = null;
        }
    }

    private static ItemStack modelItem(Material mat, String modelId) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", modelId));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
