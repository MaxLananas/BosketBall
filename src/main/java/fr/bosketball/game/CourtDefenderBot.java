package fr.bosketball.game;

import fr.bosketball.animation.AnimationKinematics;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.CourtGeometry;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.ui.LockerRoom3dMenuManager;
import fr.bosketball.util.Msg;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Kinematic 6-Bone Articulated 3D Pro Sparring Defender Bot for Solo Practice (/bb bot or Hotbar Slot 4)!
 * Uses the exact same 6-Bone Hooper Rig + Glowing Floor Ring + Framed Overhead Nameplate (image-7.png)
 * as live players — sliding in defensive stance, contesting jumpers, swiping careless dribbles,
 * and getting its ankles broken on well-timed Crossovers!
 */
public final class CourtDefenderBot {

    private final Arena arena;
    private final Vector position;
    private final Vector velocity = new Vector();
    private Vector lastPos = null;

    // 6-Bone Articulated Frost Hooper Rig + image-7.png Floor Ring & Overhead Nameplate
    private ItemDisplay floorRing;
    private TextDisplay overheadNameplate;
    private ItemDisplay headBone;
    private ItemDisplay torsoBone;
    private ItemDisplay rightArmBone;
    private ItemDisplay leftArmBone;
    private ItemDisplay rightLegBone;
    private ItemDisplay leftLegBone;

    private int tickCount = 0;
    private int stumbleTicks = 0;
    private int stealCooldownTicks = 30;
    private double verticalOffset = 0.0;
    private double verticalVel = 0.0;
    private double stridePhaseRad = 0.0;
    private float ringSpinAngle = 0.0f;
    private float currentYawDeg = 0.0f;
    private AnimationKinematics.ActionClip activeClip = AnimationKinematics.ActionClip.NONE;
    private int clipTick = 0;

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

        this.floorRing = w.spawn(loc.clone().add(0, 0.03, 0), ItemDisplay.class, d -> {
            configureDisplay(d);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setItemStack(modelItem(Material.PAPER, "ring_frost"));
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1.46f, 1.0f, 1.46f),
                new Quaternionf()
            ));
        });

        this.overheadNameplate = w.spawn(loc.clone().add(0, 2.28, 0), TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(Color.fromARGB(208, 10, 14, 22));
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setTeleportDuration(2);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1.02f, 1.02f, 1.02f),
                new Quaternionf()
            ));
        });
        refreshOverheadNameplate(false);

        this.headBone = w.spawn(loc.clone().add(0, 1.44, 0), ItemDisplay.class, d -> {
            configureDisplay(d);
            d.setGlowColorOverride(Color.fromRGB(46, 184, 255));
            d.setGlowing(true);
            ItemStack headStack = buildDefenderBotHeadItem();
            if (headStack.getType() == Material.PLAYER_HEAD) {
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                d.setItemStack(headStack);
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0.22f, 0f),
                    new Quaternionf(),
                    new Vector3f(0.96f, 0.96f, 0.96f),
                    new Quaternionf()
                ));
            } else {
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                d.setItemStack(headStack);
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0.22f, 0f),
                    new Quaternionf(),
                    new Vector3f(0.56f, 0.56f, 0.56f),
                    new Quaternionf()
                ));
            }
        });
        this.torsoBone = spawnBone(w, loc, "hooper_torso_frost");
        this.rightArmBone = spawnBone(w, loc, "hooper_arm_frost");
        this.leftArmBone = spawnBone(w, loc, "hooper_arm_frost");
        this.rightLegBone = spawnBone(w, loc, "hooper_leg_frost");
        this.leftLegBone = spawnBone(w, loc, "hooper_leg_frost");
    }

    private ItemDisplay spawnBone(World w, Location loc, String modelId) {
        return w.spawn(loc, ItemDisplay.class, d -> {
            configureDisplay(d);
            d.setGlowColorOverride(Color.fromRGB(46, 184, 255));
            d.setGlowing(true);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setItemStack(modelItem(Material.PAPER, modelId));
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Quaternionf()
            ));
        });
    }

    private static void configureDisplay(ItemDisplay d) {
        d.setPersistent(false);
        d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
        d.setBillboard(Display.Billboard.FIXED);
        d.setTeleportDuration(2);
        d.setInterpolationDuration(2);
        d.setInterpolationDelay(0);
        d.setViewRange(2.2f);
        d.setBrightness(new Display.Brightness(15, 15));
    }

    private static ItemStack buildDefenderBotHeadItem() {
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            skull.setData(DataComponentTypes.PROFILE, LockerRoom3dMenuManager.buildCustomPatchedSkin("DefenderPro", "defender_bot"));
            return skull;
        } catch (LinkageError | RuntimeException ignored) {
            return modelItem(Material.PAPER, "icon_bot_on");
        }
    }

    private void refreshOverheadNameplate(boolean stumbled) {
        if (overheadNameplate == null || !overheadNameplate.isValid()) {
            return;
        }
        TextComponent.Builder b = Component.text();
        if (stumbled) {
            b.append(Msg.parse("<red><bold> 💫 ᴀɴᴋʟᴇꜱ ʙʀᴏᴋᴇɴ ! </bold></red>\n"));
            b.append(Component.text("━━━━━━━━━━", TextColor.color(0xFF4848))).append(Component.newline());
            b.append(ChatTag.ANKLE.pillComponent());
        } else {
            b.append(Component.text(" 🛡 Défenseur Pro ", Style.style(TextColor.color(0x38BCFF), TextDecoration.BOLD)))
                .append(Component.newline());
            b.append(Component.text("━━━━━━━━━━", TextColor.color(0x1EA4FF))).append(Component.newline());
            b.append(ChatTag.LOCKDOWN.pillComponent());
        }
        overheadNameplate.text(b.asComponent());
    }

    public boolean isValid() {
        return torsoBone != null && torsoBone.isValid();
    }

    public Vector position() {
        return position.clone();
    }

    public boolean isStumbled() {
        return stumbleTicks > 0;
    }

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

        refreshOverheadNameplate(true);
        handler.getWorld().spawnParticle(Particle.CLOUD, position.toLocation(handler.getWorld()).add(0, 0.2, 0), 18, 0.3, 0.1, 0.3, 0.06);
        Msg.playPackSound(position.toLocation(handler.getWorld()), "shoe.squeak", 1.0f, 0.82f);
        return true;
    }

    public double computeContestAgainst(Player shooter) {
        if (!isValid() || stumbleTicks > 0) {
            return 0.0;
        }
        double dist = Math.hypot(shooter.getLocation().getX() - position.getX(), shooter.getLocation().getZ() - position.getZ());
        if (dist > 2.85) {
            return 0.0;
        }
        Vector shooterToBot = position.clone().subtract(shooter.getLocation().toVector()).setY(0);
        Vector look = shooter.getLocation().getDirection().setY(0);
        double frontFactor = (shooterToBot.lengthSquared() > 1e-4 && look.lengthSquared() > 1e-4)
            ? Math.max(0.35, shooterToBot.normalize().dot(look.normalize()))
            : 0.7;
        double proximity = (2.85 - dist) / 2.25;
        double jumpBonus = verticalOffset > 0.15 ? 0.16 : 0.0;
        return Math.max(0.0, Math.min(0.82, proximity * 0.68 * frontFactor + jumpBonus));
    }

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
            this.activeClip = AnimationKinematics.ActionClip.BLOCK_REACH;
            this.clipTick = 0;
        }
    }

    public void tick(Player targetPlayer, Vector defendedRim, BasketballEntity ball, boolean chargingShot) {
        if (!isValid()) {
            spawn();
            if (!isValid()) {
                return;
            }
        }
        tickCount++;
        if (stealCooldownTicks > 0) {
            stealCooldownTicks--;
        }

        double prevVert = verticalOffset;
        if (verticalOffset > 0.0 || verticalVel != 0.0) {
            verticalOffset += verticalVel;
            verticalVel -= 0.036;
            if (verticalOffset <= 0.0) {
                verticalOffset = 0.0;
                verticalVel = 0.0;
            }
        }
        double dy = verticalOffset - prevVert;

        if (stumbleTicks > 0) {
            stumbleTicks--;
            position.add(velocity);
            velocity.multiply(0.84);
            clampOnCourt();
            if (stumbleTicks == 0) {
                refreshOverheadNameplate(false);
            }
            syncArticulatedRig(targetPlayer != null ? targetPlayer.getLocation().toVector() : defendedRim, dy, true);
            return;
        }

        if (targetPlayer != null && targetPlayer.isOnline()) {
            Vector pPos = targetPlayer.getLocation().toVector();
            pPos.setY(arena.geometry().floorY());
            Vector rimFloor = new Vector(defendedRim.getX(), arena.geometry().floorY(), defendedRim.getZ());

            Vector handlerToRim = rimFloor.clone().subtract(pPos);
            double distToRim = handlerToRim.length();
            Vector dirToRim = distToRim > 1e-4 ? handlerToRim.clone().multiply(1.0 / distToRim) : new Vector(0, 0, 1);

            double cushion = Math.min(1.95, Math.max(1.05, distToRim - 1.35));
            Vector desired = pPos.clone().add(dirToRim.clone().multiply(cushion));

            Vector delta = desired.subtract(position);
            delta.setY(0);
            double dLen = delta.length();
            double maxSlideSpeed = 0.165;
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
                        this.activeClip = AnimationKinematics.ActionClip.STEAL_SWIPE;
                        this.clipTick = 0;
                        Msg.playPackSound(position.toLocation(targetPlayer.getWorld()), "shoe.squeak", 1.0f, 1.22f);
                        Msg.playPackSound(position.toLocation(targetPlayer.getWorld()), "ball.bounce", 0.95f, 1.25f);
                        targetPlayer.sendActionBar(Msg.parse("<red><bold>💥 ʙᴀʟʟᴏɴ ᴘᴏᴋᴇ ᴘᴀʀ ʟᴇ ᴅᴇꜰᴇɴꜱᴇᴜʀ ! ʀᴇᴄᴜᴘᴇʀᴇᴢ-ʟᴇ !</bold></red>"));
                    }
                }
            }

            syncArticulatedRig(targetPlayer.getLocation().toVector(), dy, false);
        }
    }

    private void syncArticulatedRig(Vector lookAtTarget, double dy, boolean stumbled) {
        World w = arena.world();
        if (w == null || !isValid()) {
            return;
        }
        double dx = lastPos == null ? 0.0 : position.getX() - lastPos.getX();
        double dz = lastPos == null ? 0.0 : position.getZ() - lastPos.getZ();
        this.lastPos = position.clone();

        double horizSpeed = Math.hypot(dx, dz);
        boolean onGround = verticalOffset <= 0.02;
        if (horizSpeed > 0.01 && onGround) {
            stridePhaseRad += horizSpeed * 2.05;
        }

        Vector lookDir = lookAtTarget.clone().subtract(position).setY(0);
        if (lookDir.lengthSquared() > 1e-4) {
            Location tmp = new Location(w, 0, 0, 0);
            tmp.setDirection(lookDir);
            currentYawDeg = tmp.getYaw();
        }
        if (stumbled) {
            currentYawDeg += 18.0f * (float) Math.sin(stumbleTicks * 0.45);
        }

        double clipProgress = 0.0;
        if (activeClip != AnimationKinematics.ActionClip.NONE) {
            clipTick++;
            clipProgress = (double) clipTick / Math.max(1, activeClip.durationTicks());
            if (clipTick >= activeClip.durationTicks()) {
                activeClip = AnimationKinematics.ActionClip.NONE;
                clipTick = 0;
            }
        }

        AnimationKinematics.RigPoseSnapshot snap = AnimationKinematics.evaluate(
            currentYawDeg,
            stumbled ? 24.0f : -4.0f,
            horizSpeed,
            dy,
            onGround,
            stridePhaseRad,
            0.0,
            false,
            false,
            -1.0,
            stumbled ? 4 : 0,
            activeClip,
            clipProgress,
            DunkStyle.TOMAHAWK,
            tickCount
        );

        Vector feet = new Vector(position.getX(), position.getY() + verticalOffset, position.getZ());

        if (floorRing != null && floorRing.isValid()) {
            ringSpinAngle += 0.028f;
            floorRing.teleport(new Location(w, position.getX(), arena.geometry().floorY() + 0.03, position.getZ(), 0f, 0f));
            floorRing.setInterpolationDelay(0);
            floorRing.setInterpolationDuration(2);
            floorRing.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf().rotateY(ringSpinAngle),
                new Vector3f(1.46f, 1.0f, 1.46f),
                new Quaternionf()
            ));
        }

        if (overheadNameplate != null && overheadNameplate.isValid()) {
            overheadNameplate.teleport(new Location(w, feet.getX(), feet.getY() + 2.28, feet.getZ()));
        }

        if (headBone != null && headBone.isValid()) {
            headBone.teleport(new Location(
                w,
                feet.getX() + snap.head().worldOffset().x,
                feet.getY() + snap.head().worldOffset().y,
                feet.getZ() + snap.head().worldOffset().z,
                currentYawDeg + 180.0f + snap.head().headYawOffsetDeg(),
                -snap.head().headPitchDeg()
            ));
        }

        applyBonePose(torsoBone, feet, w, snap.torso());
        applyBonePose(rightArmBone, feet, w, snap.rightArm());
        applyBonePose(leftArmBone, feet, w, snap.leftArm());
        applyBonePose(rightLegBone, feet, w, snap.rightLeg());
        applyBonePose(leftLegBone, feet, w, snap.leftLeg());
    }

    private void applyBonePose(ItemDisplay bone, Vector feet, World w, AnimationKinematics.BonePose pose) {
        if (bone == null || !bone.isValid()) {
            return;
        }
        bone.teleport(new Location(
            w,
            feet.getX() + pose.worldOffset().x,
            feet.getY() + pose.worldOffset().y,
            feet.getZ() + pose.worldOffset().z,
            0f,
            0f
        ));
        bone.setInterpolationDelay(0);
        bone.setInterpolationDuration(2);
        bone.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            pose.rotation(),
            new Vector3f(1.0f, 1.0f, 1.0f),
            new Quaternionf()
        ));
    }

    private void clampOnCourt() {
        CourtGeometry g = arena.geometry();
        position.setX(Math.max(g.minX() + 0.6, Math.min(g.maxX() - 0.6, position.getX())));
        position.setZ(Math.max(g.minZ() + 0.6, Math.min(g.maxZ() - 0.6, position.getZ())));
        position.setY(g.floorY());
    }

    public void remove() {
        List<Display> all = new ArrayList<>();
        if (floorRing != null) all.add(floorRing);
        if (overheadNameplate != null) all.add(overheadNameplate);
        if (headBone != null) all.add(headBone);
        if (torsoBone != null) all.add(torsoBone);
        if (rightArmBone != null) all.add(rightArmBone);
        if (leftArmBone != null) all.add(leftArmBone);
        if (rightLegBone != null) all.add(rightLegBone);
        if (leftLegBone != null) all.add(leftLegBone);
        for (Display d : all) {
            if (d.isValid()) {
                d.remove();
            }
        }
        floorRing = null;
        overheadNameplate = null;
        headBone = torsoBone = rightArmBone = leftArmBone = rightLegBone = leftLegBone = null;
    }

    private static ItemStack modelItem(Material base, String modelId) {
        ItemStack st = new ItemStack(base);
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(BallSkin.NAMESPACE, modelId));
            st.setItemMeta(meta);
        }
        return st;
    }
}
