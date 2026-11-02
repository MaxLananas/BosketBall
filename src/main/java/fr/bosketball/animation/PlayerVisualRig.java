package fr.bosketball.animation;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Per-Player On-Court Visual Suite combining:
 *   1. image-7.png Concentric Glowing Team Floor Aura Ring (ring_ignis / ring_frost / ring_gold)
 *   2. image-7.png Framed Overhead Team Nameplate (bold team-colored name + glowing underline bar + Aura Tag!)
 *   3. True 6-Bone Articulated 3D Hooper Rig (Head w/ real player skin + Torso + Arms + Legs + Sneakers)
 *      executing smooth interpolated Walk, Dribble, Crossover, Jump, Shot-Gather, Gooseneck Throw & Slam Dunk animations!
 */
public final class PlayerVisualRig {

    private final BosketBallPlugin plugin;
    private final Player owner;
    private final boolean courtMatchMode;
    private TeamSide teamSide;

    // image-7.png Floor Ring & Overhead Nameplate
    private ItemDisplay floorRingDisplay;
    private TextDisplay overheadNameplate;
    private String currentRingModel = "";
    private float ringSpinAngle = 0f;

    // 6-Bone Articulated 3D Hooper Rig
    private ItemDisplay headBone;
    private ItemDisplay torsoBone;
    private ItemDisplay rightArmBone;
    private ItemDisplay leftArmBone;
    private ItemDisplay rightLegBone;
    private ItemDisplay leftLegBone;
    private ItemStack ownerSkullItem;

    // Locomotion & Clip State
    private Vector lastPos = null;
    private boolean wasOnGround = true;
    private double stridePhaseRad = 0.0;
    private int landingTicks = 0;
    private boolean offHandDribble = false;
    private AnimationKinematics.ActionClip activeClip = AnimationKinematics.ActionClip.NONE;
    private int clipTick = 0;
    private DunkStyle activeDunkStyle = DunkStyle.TOMAHAWK;
    private int tickCounter = 0;
    private boolean lastSelfBodyVisible = false;
    private boolean lastTorsoVisibleToOwner = true;
    private ChatTag lastRenderedTag = null;
    private int lastHotStreak = -1;
    private boolean lastTeamGlowState = false;

    public PlayerVisualRig(BosketBallPlugin plugin, Player owner, TeamSide teamSide) {
        this(plugin, owner, teamSide, true);
    }

    public PlayerVisualRig(BosketBallPlugin plugin, Player owner, TeamSide teamSide, boolean courtMatchMode) {
        this.plugin = plugin;
        this.owner = owner;
        this.courtMatchMode = courtMatchMode;
        this.teamSide = teamSide == TeamSide.SPECTATOR ? TeamSide.IGNIS : teamSide;
        spawnAll();
    }

    public void setTeamSide(TeamSide side) {
        if (side != TeamSide.SPECTATOR && side != this.teamSide) {
            this.teamSide = side;
            despawnAll();
            spawnAll();
        }
    }

    public void triggerAction(AnimationKinematics.ActionClip clip, DunkStyle dunkStyle) {
        if (clip == null) {
            return;
        }
        this.activeClip = clip;
        this.clipTick = 0;
        if (dunkStyle != null) {
            this.activeDunkStyle = dunkStyle;
        }
        if (clip == AnimationKinematics.ActionClip.CROSSOVER) {
            this.offHandDribble = !this.offHandDribble;
        }
    }

    public boolean isOffHandDribble() {
        return offHandDribble;
    }

    private void spawnAll() {
        if (owner == null || !owner.isOnline() || owner.getWorld() == null) {
            return;
        }
        World w = owner.getWorld();
        Location base = owner.getLocation();
        String kit = teamSide == TeamSide.FROST ? "frost" : "ignis";

        // Apply clean ambient particle-free invisibility so the 6-bone 3D articulated rig replaces rigid vanilla limbs!
        owner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false, false));

        if (courtMatchMode) {
            // 1. Concentric Glowing Team Floor Ring (image-7.png)
            String ringModel = teamSide == TeamSide.FROST ? "ring_frost" : "ring_ignis";
            this.currentRingModel = ringModel;
            this.floorRingDisplay = w.spawn(base, ItemDisplay.class, d -> {
                configureDisplay(d);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setItemStack(modelItem(Material.PAPER, ringModel));
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(1.48f, 1.0f, 1.48f),
                    new Quaternionf()
                ));
            });

            // 2. Framed Overhead Team Nameplate (image-7.png)
            this.overheadNameplate = w.spawn(base.clone().add(0, 2.28, 0), TextDisplay.class, td -> {
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
                    new Vector3f(1.06f, 1.06f, 1.06f),
                    new Quaternionf()
                ));
            });
            owner.hideEntity(plugin, overheadNameplate); // Owner never needs their own floating overhead plate in 1P!
            refreshOverheadText(0);
        }

        // 3. 6-Bone Articulated 3D Hooper Rig
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta sm) {
            sm.setPlayerProfile(owner.getPlayerProfile());
            skull.setItemMeta(sm);
        }
        this.ownerSkullItem = skull;

        this.headBone = w.spawn(base.clone().add(0, 1.44, 0), ItemDisplay.class, d -> {
            configureDisplay(d);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            d.setItemStack(skull);
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0.22f, 0f),
                new Quaternionf(),
                new Vector3f(0.96f, 0.96f, 0.96f),
                new Quaternionf()
            ));
        });
        // Hide headBone ONLY from owner so 1P view at eye=1.62m is 100% clear, while owner sees their own head in F5 via sendEquipmentChange!
        owner.hideEntity(plugin, headBone);
        owner.sendEquipmentChange(owner, EquipmentSlot.HEAD, ownerSkullItem);

        this.torsoBone = spawnBone(w, base, "hooper_torso_" + kit);
        this.rightArmBone = spawnBone(w, base, "hooper_arm_" + kit);
        this.leftArmBone = spawnBone(w, base, "hooper_arm_" + kit);
        this.rightLegBone = spawnBone(w, base, "hooper_leg_" + kit);
        this.leftLegBone = spawnBone(w, base, "hooper_leg_" + kit);

        PlayerProfile initProf = plugin.statsManager().getOrCreate(owner);
        boolean wantGlow = courtMatchMode && initProf.settings().teamFloorRings();
        this.lastTeamGlowState = !wantGlow;
        syncBoneTeamGlow(wantGlow, 0);

        boolean wantSelf = initProf.settings().selfBodyRigInF5() && initProf.settings().customAnimations();
        this.lastSelfBodyVisible = !wantSelf; // Force initial visibility sync
        this.lastTorsoVisibleToOwner = !wantSelf;
        syncOwnerFirstPersonCulling(wantSelf, false);
    }

    private ItemDisplay spawnBone(World w, Location loc, String modelId) {
        return w.spawn(loc, ItemDisplay.class, d -> {
            configureDisplay(d);
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

    public void tick(double floorY, boolean holdingBall, double dribbleWave01, double shotChargeProgress, int hotStreak) {
        if (owner == null || !owner.isOnline()) {
            return;
        }
        Location loc = owner.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        if (headBone == null || !headBone.isValid() || headBone.getWorld() != loc.getWorld()) {
            despawnAll();
            spawnAll();
            if (headBone == null || !headBone.isValid()) {
                return;
            }
        }
        tickCounter++;
        PlayerProfile profile = plugin.statsManager().getOrCreate(owner);

        // Keep ambient invisibility active while custom 6-bone rig is enabled
        boolean animActive = profile.settings().customAnimations();
        if (animActive) {
            if (tickCounter % 40 == 0 && !owner.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false, false));
            }
        } else if (owner.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            owner.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        Vector curPos = loc.toVector();
        double dx = lastPos == null ? 0.0 : curPos.getX() - lastPos.getX();
        double dy = lastPos == null ? 0.0 : curPos.getY() - lastPos.getY();
        double dz = lastPos == null ? 0.0 : curPos.getZ() - lastPos.getZ();
        this.lastPos = curPos.clone();

        double horizSpeed = Math.hypot(dx, dz);
        boolean onGround = owner.isOnGround() || Math.abs(curPos.getY() - floorY) < 0.08;
        if (!wasOnGround && onGround) {
            this.landingTicks = 5;
        } else if (landingTicks > 0) {
            landingTicks--;
        }
        this.wasOnGround = onGround;

        if (horizSpeed > 0.015 && onGround) {
            this.stridePhaseRad += horizSpeed * 1.85;
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
            loc.getYaw(),
            loc.getPitch(),
            horizSpeed,
            dy,
            onGround,
            owner.isSneaking(),
            stridePhaseRad,
            dribbleWave01,
            holdingBall,
            offHandDribble,
            shotChargeProgress,
            landingTicks,
            activeClip,
            clipProgress,
            activeDunkStyle,
            tickCounter
        );

        boolean showArmsIn1P = shotChargeProgress >= 0.0 || activeClip != AnimationKinematics.ActionClip.NONE;
        syncOwnerFirstPersonCulling(profile.settings().selfBodyRigInF5(), showArmsIn1P);

        // 1. Update Floor Aura Ring (image-7.png)
        if (floorRingDisplay != null && floorRingDisplay.isValid()) {
            String targetRing = hotStreak >= 3 ? "ring_gold" : (teamSide == TeamSide.FROST ? "ring_frost" : "ring_ignis");
            if (!targetRing.equals(currentRingModel)) {
                currentRingModel = targetRing;
                floorRingDisplay.setItemStack(modelItem(Material.PAPER, targetRing));
            }
            ringSpinAngle += holdingBall ? 0.055f : 0.022f;
            double ringY = Math.min(curPos.getY(), floorY) + 0.03;
            floorRingDisplay.teleport(new Location(loc.getWorld(), curPos.getX(), ringY, curPos.getZ(), 0f, 0f));
            float ringScale = profile.settings().teamFloorRings() ? (holdingBall ? 1.58f : 1.44f) : 0.0f;
            floorRingDisplay.setInterpolationDelay(0);
            floorRingDisplay.setInterpolationDuration(2);
            floorRingDisplay.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf().rotateY(ringSpinAngle),
                new Vector3f(ringScale, 1.0f, ringScale),
                new Quaternionf()
            ));
        }

        // 2. Update Overhead Team Nameplate (image-7.png)
        if (overheadNameplate != null && overheadNameplate.isValid()) {
            overheadNameplate.teleport(new Location(loc.getWorld(), curPos.getX(), curPos.getY() + 2.26, curPos.getZ()));
            if (profile.selectedTag() != lastRenderedTag || hotStreak != lastHotStreak) {
                refreshOverheadText(hotStreak);
            }
        }

        // Sync 3D Limb/Body Team Silhouette Glow Outline (image-7.png!)
        boolean wantGlow = courtMatchMode && animActive && profile.settings().teamFloorRings();
        if (wantGlow != lastTeamGlowState || hotStreak != lastHotStreak) {
            lastTeamGlowState = wantGlow;
            syncBoneTeamGlow(wantGlow, hotStreak);
        }

        // 3. Update All 6 Articulated Hooper Rig Bones!
        float boneVisScale = animActive ? 1.0f : 0.0f;
        Location headLoc = new Location(
            loc.getWorld(),
            curPos.getX() + snap.head().worldOffset().x,
            curPos.getY() + snap.head().worldOffset().y,
            curPos.getZ() + snap.head().worldOffset().z,
            loc.getYaw() + snap.head().headYawOffsetDeg(),
            snap.head().headPitchDeg()
        );
        headBone.teleport(headLoc);
        headBone.setTransformation(new Transformation(
            new Vector3f(0f, 0.22f, 0f),
            new Quaternionf(),
            new Vector3f(0.96f * boneVisScale, 0.96f * boneVisScale, 0.96f * boneVisScale),
            new Quaternionf()
        ));

        applyBonePose(torsoBone, curPos, loc.getWorld(), snap.torso(), boneVisScale);
        applyBonePose(rightArmBone, curPos, loc.getWorld(), snap.rightArm(), boneVisScale);
        applyBonePose(leftArmBone, curPos, loc.getWorld(), snap.leftArm(), boneVisScale);
        applyBonePose(rightLegBone, curPos, loc.getWorld(), snap.rightLeg(), boneVisScale);
        applyBonePose(leftLegBone, curPos, loc.getWorld(), snap.leftLeg(), boneVisScale);
    }

    private void syncBoneTeamGlow(boolean glow, int hotStreak) {
        Color glowCol = hotStreak >= 3
            ? Color.fromRGB(255, 208, 42)
            : (teamSide == TeamSide.FROST ? Color.fromRGB(46, 184, 255) : Color.fromRGB(255, 62, 54));
        for (ItemDisplay bone : List.of(headBone, torsoBone, rightArmBone, leftArmBone, rightLegBone, leftLegBone)) {
            if (bone != null && bone.isValid()) {
                bone.setGlowColorOverride(glowCol);
                bone.setGlowing(glow);
            }
        }
    }

    private void applyBonePose(ItemDisplay bone, Vector playerFeet, World w, AnimationKinematics.BonePose pose, float scale) {
        if (bone == null || !bone.isValid()) {
            return;
        }
        bone.teleport(new Location(
            w,
            playerFeet.getX() + pose.worldOffset().x,
            playerFeet.getY() + pose.worldOffset().y,
            playerFeet.getZ() + pose.worldOffset().z,
            0f,
            0f
        ));
        bone.setInterpolationDelay(0);
        bone.setInterpolationDuration(2);
        bone.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            pose.rotation(),
            new Vector3f(scale, scale, scale),
            new Quaternionf()
        ));
    }

    private void refreshOverheadText(int hotStreak) {
        if (overheadNameplate == null || !overheadNameplate.isValid()) {
            return;
        }
        PlayerProfile prof = plugin.statsManager().getOrCreate(owner);
        this.lastRenderedTag = prof.selectedTag();
        this.lastHotStreak = hotStreak;

        TextColor nameCol = teamSide == TeamSide.FROST ? TextColor.color(0x38BCFF) : TextColor.color(0xFF4C4C);
        TextColor barCol = teamSide == TeamSide.FROST ? TextColor.color(0x1EA4FF) : TextColor.color(0xE83232);

        TextComponent.Builder b = Component.text();
        b.append(Component.text(" " + owner.getName() + " ", Style.style(nameCol, TextDecoration.BOLD)));
        if (hotStreak >= 3) {
            b.append(Msg.parse("<gold>🔥</gold>"));
        }
        b.append(Component.newline());
        // Glowing team-colored underline bar directly beneath name (exact image-7.png!)
        int barLen = Math.max(5, Math.min(12, owner.getName().length()));
        b.append(Component.text("━".repeat(barLen), barCol));

        if (prof.selectedTag() != null) {
            b.append(Component.newline());
            b.append(prof.selectedTag().pillComponent());
        }
        overheadNameplate.text(b.asComponent());
    }

    private void syncOwnerFirstPersonCulling(boolean selfBodyInF5, boolean showFirstPersonActionArms) {
        if (owner == null || !owner.isOnline()) {
            return;
        }
        boolean animEnabled = plugin.statsManager().getOrCreate(owner).settings().customAnimations();
        boolean effectiveSelfBody = selfBodyInF5 && animEnabled;

        // Refresh client-only F5 head helmet packet every 20 ticks so inventory updates never wipe it
        if (tickCounter % 20 == 1) {
            owner.sendEquipmentChange(owner, EquipmentSlot.HEAD,
                effectiveSelfBody && ownerSkullItem != null ? ownerSkullItem : owner.getInventory().getHelmet());
        }

        if (effectiveSelfBody != lastSelfBodyVisible) {
            lastSelfBodyVisible = effectiveSelfBody;
            owner.sendEquipmentChange(owner, EquipmentSlot.HEAD,
                effectiveSelfBody && ownerSkullItem != null ? ownerSkullItem : owner.getInventory().getHelmet());
            for (ItemDisplay part : List.of(rightLegBone, leftLegBone)) {
                if (part != null && part.isValid()) {
                    if (effectiveSelfBody) {
                        owner.showEntity(plugin, part);
                    } else {
                        owner.hideEntity(plugin, part);
                    }
                }
            }
        }

        // Hide torso from owner ONLY while sneaking (eye=1.27m) so 1P view stays 100% clear even when crouched!
        boolean showTorso = effectiveSelfBody && !owner.isSneaking();
        if (showTorso != lastTorsoVisibleToOwner && torsoBone != null && torsoBone.isValid()) {
            lastTorsoVisibleToOwner = showTorso;
            if (showTorso) {
                owner.showEntity(plugin, torsoBone);
            } else {
                owner.hideEntity(plugin, torsoBone);
            }
        }

        boolean showArms = effectiveSelfBody || (animEnabled && showFirstPersonActionArms);
        for (ItemDisplay arm : List.of(rightArmBone, leftArmBone)) {
            if (arm != null && arm.isValid()) {
                if (showArms) {
                    owner.showEntity(plugin, arm);
                } else {
                    owner.hideEntity(plugin, arm);
                }
            }
        }
    }

    public void despawn() {
        despawnAll();
    }

    public void hideFirstPersonRigIfOwner(Player joiner) {
        if (joiner != null && owner != null && joiner.getUniqueId().equals(owner.getUniqueId())) {
            if (overheadNameplate != null) {
                joiner.hideEntity(plugin, overheadNameplate);
            }
            if (headBone != null) {
                joiner.hideEntity(plugin, headBone);
            }
        }
    }

    public void despawnAll() {
        if (owner != null && owner.isOnline()) {
            owner.removePotionEffect(PotionEffectType.INVISIBILITY);
            owner.sendEquipmentChange(owner, EquipmentSlot.HEAD, owner.getInventory().getHelmet());
        }
        despawnDisplaysOnly();
    }

    public void despawnDisplaysOnly() {
        List<Display> list = new ArrayList<>();
        if (floorRingDisplay != null) list.add(floorRingDisplay);
        if (overheadNameplate != null) list.add(overheadNameplate);
        if (headBone != null) list.add(headBone);
        if (torsoBone != null) list.add(torsoBone);
        if (rightArmBone != null) list.add(rightArmBone);
        if (leftArmBone != null) list.add(leftArmBone);
        if (rightLegBone != null) list.add(rightLegBone);
        if (leftLegBone != null) list.add(leftLegBone);
        for (Display d : list) {
            if (d.isValid()) {
                d.remove();
            }
        }
        floorRingDisplay = null;
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
