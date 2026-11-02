package fr.bosketball.game;

import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.TeamSide;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/**
 * Kinematic 3D Pro Sparring Defender Bot for Solo Practice (/bb bot or Hotbar Slot 4)!
 * Slides in defensive stance, contests jumpers, blocks careless dunks, pokes exposed dribbles loose,
 * and gets its ankles broken on well-timed Crossovers!
 */
public final class CourtDefenderBot {

    private final Arena arena;
    private ArmorStand stand;
    private final Vector position;
    private final Vector velocity = new Vector();
    private int tickCount = 0;
    private int stumbleTicks = 0;
    private int stealCooldownTicks = 30;
    private double verticalOffset = 0.0;
    private double verticalVel = 0.0;

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
        this.stand = w.spawn(loc, ArmorStand.class, as -> {
            as.setPersistent(false);
            as.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            as.setGravity(false);
            as.setBasePlate(false);
            as.setArms(true);
            as.setInvulnerable(true);
            as.setCanPickupItems(false);
            as.customName(Msg.parse("<#3DB8FF><bold>🛡 ᴅᴇꜰᴇɴꜱᴇᴜʀ ᴘʀᴏ</bold></#3DB8FF>"));
            as.setCustomNameVisible(true);

            EntityEquipment eq = as.getEquipment();
            if (eq != null) {
                eq.setHelmet(dyedLeather(Material.LEATHER_HELMET, Color.fromRGB(36, 46, 66)));
                eq.setChestplate(dyedLeather(Material.LEATHER_CHESTPLATE, TeamSide.FROST.bukkitColor()));
                eq.setLeggings(dyedLeather(Material.LEATHER_LEGGINGS, TeamSide.FROST.bukkitColor()));
                eq.setBoots(BallSkin.createCustomModelItem(Material.LEATHER_BOOTS, "sneakers_air", null, null));
            }
        });
    }

    public boolean isValid() {
        return stand != null && stand.isValid();
    }

    public Vector position() {
        return position.clone();
    }

    public boolean isStumbled() {
        return stumbleTicks > 0;
    }

    /**
     * Triggered when the player executes a Crossover within range of this defender!
     */
    public boolean tryBreakAnkles(Player handler) {
        if (!isValid() || stumbleTicks > 0) {
            return false;
        }
        if (handler.getLocation().toVector().distanceSquared(position) > 3.2 * 3.2) {
            return false;
        }
        this.stumbleTicks = 36;
        double yawRad = Math.toRadians(handler.getLocation().getYaw());
        Vector sideSlide = new Vector(Math.cos(yawRad), 0, Math.sin(yawRad)).multiply(Math.random() < 0.5 ? 0.28 : -0.28);
        this.velocity.copy(sideSlide);

        stand.customName(Msg.parse("<red><bold>💫 ᴀɴᴋʟᴇꜱ ʙʀᴏᴋᴇɴ !</bold></red>"));
        handler.getWorld().spawnParticle(Particle.CLOUD, position.toLocation(handler.getWorld()).add(0, 0.2, 0), 18, 0.3, 0.1, 0.3, 0.06);
        Msg.playPackSound(position.toLocation(handler.getWorld()), "shoe.squeak", 1.0f, 0.82f);
        return true;
    }

    /**
     * Computes live defensive contest [0.0 .. 0.80] against a shooter!
     */
    public double computeContestAgainst(Player shooter) {
        if (!isValid() || stumbleTicks > 0) {
            return 0.0;
        }
        double dist = Math.hypot(shooter.getLocation().getX() - position.getX(), shooter.getLocation().getZ() - position.getZ());
        if (dist > 2.85) {
            return 0.0;
        }
        // Check that defender is roughly in front of shooter rather than way behind them
        Vector shooterToBot = position.clone().subtract(shooter.getLocation().toVector()).setY(0);
        Vector look = shooter.getLocation().getDirection().setY(0);
        double frontFactor = (shooterToBot.lengthSquared() > 1e-4 && look.lengthSquared() > 1e-4)
            ? Math.max(0.35, shooterToBot.normalize().dot(look.normalize()))
            : 0.7;
        double proximity = (2.85 - dist) / 2.25;
        double jumpBonus = verticalOffset > 0.15 ? 0.16 : 0.0;
        return Math.max(0.0, Math.min(0.82, proximity * 0.68 * frontFactor + jumpBonus));
    }

    /**
     * Checks if the defender is cutting off a direct driving dunk lane between driver and target rim!
     */
    public boolean isCuttingOffDunkLane(Player driver, Vector targetRim) {
        if (!isValid() || stumbleTicks > 0) {
            return false;
        }
        Vector p = driver.getLocation().toVector().setY(0);
        Vector r = targetRim.clone().setY(0);
        Vector b = position.clone().setY(0);

        double distToBot = p.distance(b);
        if (distToBot > 1.55) {
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
        if (verticalOffset <= 0.04 && stumbleTicks <= 0) {
            this.verticalVel = 0.24;
        }
    }

    public void tick(Player targetPlayer, Vector defendedRim, BasketballEntity ball, boolean chargingShot) {
        if (stand == null || !stand.isValid()) {
            spawn();
            if (stand == null) {
                return;
            }
        }
        tickCount++;
        if (stealCooldownTicks > 0) {
            stealCooldownTicks--;
        }

        // Vertical hop physics when contesting a jumper
        if (verticalOffset > 0.0 || verticalVel != 0.0) {
            verticalOffset += verticalVel;
            verticalVel -= 0.036;
            if (verticalOffset <= 0.0) {
                verticalOffset = 0.0;
                verticalVel = 0.0;
            }
        }

        if (stumbleTicks > 0) {
            stumbleTicks--;
            position.add(velocity);
            velocity.multiply(0.84);
            clampOnCourt();
            if (stumbleTicks == 0) {
                stand.customName(Msg.parse("<#3DB8FF><bold>🛡 ᴅᴇꜰᴇɴꜱᴇᴜʀ ᴘʀᴏ</bold></#3DB8FF>"));
            }
            // Stumbled off-balance pose!
            stand.setHeadPose(new EulerAngle(Math.toRadians(22), 0, Math.toRadians(16)));
            stand.setRightArmPose(new EulerAngle(Math.toRadians(-35), 0, Math.toRadians(65)));
            stand.setLeftArmPose(new EulerAngle(Math.toRadians(-35), 0, Math.toRadians(-65)));
            stand.setRightLegPose(new EulerAngle(Math.toRadians(-24), 0, Math.toRadians(18)));
            stand.setLeftLegPose(new EulerAngle(Math.toRadians(22), 0, Math.toRadians(-18)));
            syncStandTransform(targetPlayer != null ? targetPlayer.getLocation().toVector() : defendedRim);
            return;
        }

        if (targetPlayer != null && targetPlayer.isOnline()) {
            Vector pPos = targetPlayer.getLocation().toVector();
            pPos.setY(arena.geometry().floorY());
            Vector rimFloor = new Vector(defendedRim.getX(), arena.geometry().floorY(), defendedRim.getZ());

            // Ideal defensive shading spot: 1.95m off the ball-handler along the line to the basket!
            Vector handlerToRim = rimFloor.clone().subtract(pPos);
            double distToRim = handlerToRim.length();
            Vector dirToRim = distToRim > 1e-4 ? handlerToRim.clone().multiply(1.0 / distToRim) : new Vector(0, 0, 1);

            double cushion = Math.min(1.95, Math.max(1.05, distToRim - 1.35));
            Vector desired = pPos.clone().add(dirToRim.clone().multiply(cushion));

            Vector delta = desired.subtract(position);
            delta.setY(0);
            double dLen = delta.length();
            double maxSlideSpeed = 0.165; // Fast athletic lateral slide, but beatable with sprint + crossover!
            if (dLen > 0.05) {
                if (dLen > maxSlideSpeed) {
                    delta.multiply(maxSlideSpeed / dLen);
                }
                position.add(delta);
            }
            clampOnCourt();

            if (chargingShot && computeContestAgainst(targetPlayer) >= 0.25) {
                triggerContestJump();
            }

            // Active Poke-Steal check if ball-handler dribbles carelessly right into defender's chest without protecting!
            if (ball != null
                && targetPlayer.getUniqueId().equals(ball.holderId())
                && ball.mode() == BasketballEntity.BallMode.DRIBBLING
                && !chargingShot
                && stealCooldownTicks <= 0) {
                double ballToBot = Math.hypot(ball.position().getX() - position.getX(), ball.position().getZ() - position.getZ());
                if (ballToBot <= 1.18 && !targetPlayer.isSneaking()) {
                    stealCooldownTicks = 55;
                    double pokeChance = targetPlayer.isSprinting() ? 0.58 : 0.34;
                    if (Math.random() < pokeChance) {
                        Vector pokeImpulse = dirToRim.clone().multiply(0.36)
                            .add(new Vector((Math.random() - 0.5) * 0.35, 0.24, (Math.random() - 0.5) * 0.35));
                        ball.popLoose(pokeImpulse, null);
                        Msg.playPackSound(position.toLocation(targetPlayer.getWorld()), "shoe.squeak", 1.0f, 1.22f);
                        Msg.playPackSound(position.toLocation(targetPlayer.getWorld()), "ball.bounce", 0.95f, 1.25f);
                        targetPlayer.sendActionBar(Msg.parse("<red><bold>💥 ʙᴀʟʟᴏɴ ᴘᴏᴋᴇ ᴘᴀʀ ʟᴇ ᴅᴇꜰᴇɴꜱᴇᴜʀ ! ʀᴇᴄᴜᴘᴇʀᴇᴢ-ʟᴇ !</bold></red>"));
                        stand.setRightArmPose(new EulerAngle(Math.toRadians(-95), Math.toRadians(-20), 0));
                    }
                }
            }

            // Animate defensive stance arms & footwork!
            double bob = Math.sin(tickCount * 0.35) * 6.0;
            if (chargingShot || verticalOffset > 0.05) {
                // High vertical contest hand-in-face pose!
                stand.setRightArmPose(new EulerAngle(Math.toRadians(-162), 0, Math.toRadians(12)));
                stand.setLeftArmPose(new EulerAngle(Math.toRadians(-155), 0, Math.toRadians(-12)));
            } else {
                // Wide perimeter clamp stance!
                stand.setRightArmPose(new EulerAngle(Math.toRadians(-52 + bob), 0, Math.toRadians(48)));
                stand.setLeftArmPose(new EulerAngle(Math.toRadians(-52 - bob), 0, Math.toRadians(-48)));
            }
            stand.setRightLegPose(new EulerAngle(Math.toRadians(-12 + bob * 0.6), 0, Math.toRadians(12)));
            stand.setLeftLegPose(new EulerAngle(Math.toRadians(12 - bob * 0.6), 0, Math.toRadians(-12)));
            stand.setHeadPose(EulerAngle.ZERO);

            syncStandTransform(targetPlayer.getLocation().toVector());
        }
    }

    private void clampOnCourt() {
        CourtGeometry g = arena.geometry();
        position.setX(Math.max(g.minX() + 0.6, Math.min(g.maxX() - 0.6, position.getX())));
        position.setZ(Math.max(g.minZ() + 0.6, Math.min(g.maxZ() - 0.6, position.getZ())));
        position.setY(g.floorY());
    }

    private void syncStandTransform(Vector lookAtTarget) {
        World w = arena.world();
        if (w == null || stand == null || !stand.isValid()) {
            return;
        }
        Location loc = new Location(w, position.getX(), position.getY() + verticalOffset, position.getZ());
        Vector dir = lookAtTarget.clone().subtract(position).setY(0);
        if (dir.lengthSquared() > 1e-4) {
            loc.setDirection(dir);
        }
        stand.teleport(loc);
    }

    public void remove() {
        if (stand != null) {
            stand.remove();
            stand = null;
        }
    }

    private static ItemStack dyedLeather(Material mat, Color color) {
        ItemStack stack = new ItemStack(mat);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE, ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
