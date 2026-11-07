package fr.bosketball.animation;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.ChatTag;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
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
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PlayerVisualRig {

    private final BosketBallPlugin plugin;
    private final Player owner;
    private final boolean courtMatchMode;
    private TeamSide teamSide;

    private ItemDisplay floorRingDisplay;
    private TextDisplay overheadNameplate;
    private String currentRingModel = "";
    private Vector lastPlatePosition;
    private float ringSpinAngle = 0f;

    private boolean offHandDribble = false;
    private AnimationKinematics.ActionClip activeClip = AnimationKinematics.ActionClip.NONE;
    private int clipTick = 0;
    private DunkStyle activeDunkStyle = DunkStyle.TOMAHAWK;
    private int diveTicksRemaining = 0;
    private int lastRenderedTagsHash = Integer.MIN_VALUE;
    private int lastHotStreak = -1;
    private boolean lastPlayerGlow = false;
    private float lastPlateScale = -1.0f;

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

    public void triggerAction(AnimationKinematics.ActionClip clip, DunkStyle dunkStyle) {
        if (clip == null || owner == null || !owner.isOnline()) {
            return;
        }
        this.activeClip = clip;
        this.clipTick = 0;
        if (dunkStyle != null) {
            this.activeDunkStyle = dunkStyle;
        }

        var animProfile = profileOrNull(owner);
        boolean animOn = animProfile == null || animProfile.settings().customAnimations();
        switch (clip) {
            case CROSSOVER -> {
                this.offHandDribble = !this.offHandDribble;
                if (animOn) {
                    if (offHandDribble) {
                        owner.swingOffHand();
                    } else {
                        owner.swingMainHand();
                    }
                    owner.getWorld().spawnParticle(Particle.CLOUD, owner.getLocation().add(0, 0.08, 0), 8, 0.22, 0.03, 0.22, 0.04);
                }
            }
            case THROW_PASS -> {
                owner.swingMainHand();
                if (animOn) {
                    owner.swingOffHand();
                }
            }
            case THROW_SHOT -> owner.swingMainHand();
            case SLAM_DUNK -> {
                if (animOn && (activeDunkStyle == DunkStyle.SPIN_360 || activeDunkStyle == DunkStyle.WINDMILL)) {

                    try {
                        owner.startRiptideAttack(13, 0.0f, null);
                    } catch (Throwable ignored) {
                    }
                }
                owner.swingOffHand();
                owner.swingMainHand();
            }
            case BLOCK_REACH, STEAL_SWIPE -> owner.swingMainHand();
            default -> {
            }
        }
    }

    public void triggerHustleDive() {
        if (owner == null || !owner.isOnline()) {
            return;
        }
        var diveProfile = profileOrNull(owner);
        if (diveProfile == null || diveProfile.settings().customAnimations()) {
            try {
                owner.setPose(Pose.SWIMMING, true);
                this.diveTicksRemaining = 11;
            } catch (Throwable ignored) {
            }
        }
        owner.swingMainHand();
        owner.swingOffHand();
    }

    private void spawnAll() {
        if (owner == null || !owner.isOnline() || owner.getWorld() == null) {
            return;
        }

        owner.removePotionEffect(PotionEffectType.INVISIBILITY);

        if (!courtMatchMode) {
            return;
        }
        World w = owner.getWorld();
        Location base = owner.getLocation();

        String ringModel = teamSide == TeamSide.FROST ? "ring_frost" : "ring_ignis";
        this.currentRingModel = ringModel;
        this.floorRingDisplay = w.spawn(base, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setInterpolationDelay(0);
            d.setViewRange(2.4f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setItemStack(modelItem(Material.PAPER, ringModel));
            d.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1.48f, 1.0f, 1.48f),
                new Quaternionf()
            ));
        });

        this.overheadNameplate = w.spawn(base.clone().add(0, 2.24, 0), TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(Color.fromARGB(208, 10, 14, 22));
            td.setShadowed(true);
            td.setSeeThrough(false);
            // Round 26: no interpolated teleport -> the plate can no longer trail/jitter behind
            // the player, and it is invisible by default so the wearer never sees their own name.
            td.setTeleportDuration(0);
            td.setVisibleByDefault(false);
            td.setBrightness(new Display.Brightness(15, 15));
            td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1.02f, 1.02f, 1.02f),
                new Quaternionf()
            ));
        });
        applyNameplateVisibility();
        refreshOverheadText(0);
    }

    public void tick(double floorY, boolean holdingBall, double dribbleWave01, double shotChargeProgress, int hotStreak) {
        if (owner == null || !owner.isOnline()) {
            return;
        }
        Location loc = owner.getLocation();
        if (loc.getWorld() == null) {
            return;
        }
        if (diveTicksRemaining > 0) {
            diveTicksRemaining--;
            if (diveTicksRemaining == 0) {
                try {
                    owner.setPose(Pose.STANDING, false);
                } catch (Throwable ignored) {
                }
            }
        }

        if (activeClip != AnimationKinematics.ActionClip.NONE) {
            clipTick++;
            if (activeClip == AnimationKinematics.ActionClip.SLAM_DUNK && clipTick == 10) {
                owner.swingMainHand();
                owner.swingOffHand();
            }
            if (clipTick >= activeClip.durationTicks()) {
                activeClip = AnimationKinematics.ActionClip.NONE;
                clipTick = 0;
            }
        }

        if (!courtMatchMode) {
            return;
        }
        if (floorRingDisplay == null || !floorRingDisplay.isValid() || floorRingDisplay.getWorld() != loc.getWorld()
            || overheadNameplate == null || !overheadNameplate.isValid()) {
            despawnDisplaysOnly();
            this.lastPlateScale = -1.0f;
            spawnAll();
            if (floorRingDisplay == null || !floorRingDisplay.isValid()) {
                return;
            }
        }

        PlayerProfile profile = profileOrNull(owner);
        if (profile == null) {
            return;
        }
        boolean hideDuringCineCam = plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(owner.getUniqueId());
        Vector curPos = loc.toVector();

        boolean wantGlow = !hideDuringCineCam && profile.settings().teamFloorRings();
        if (wantGlow != lastPlayerGlow) {
            lastPlayerGlow = wantGlow;
            owner.setGlowing(wantGlow);
        }

        String targetRing = hotStreak >= 3 ? "ring_gold" : (teamSide == TeamSide.FROST ? "ring_frost" : "ring_ignis");
        if (!targetRing.equals(currentRingModel)) {
            currentRingModel = targetRing;
            floorRingDisplay.setItemStack(modelItem(Material.PAPER, targetRing));
        }
        ringSpinAngle += holdingBall ? 0.055f : 0.022f;
        double ringY = Math.min(curPos.getY(), floorY) + 0.03;
        floorRingDisplay.teleport(new Location(loc.getWorld(), curPos.getX(), ringY, curPos.getZ(), 0f, 0f));
        float ringScale = (!hideDuringCineCam && profile.settings().teamFloorRings())
            ? (holdingBall ? 1.56f : 1.42f)
            : 0.0f;
        floorRingDisplay.setInterpolationDelay(0);
        floorRingDisplay.setInterpolationDuration(2);
        floorRingDisplay.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            new Quaternionf().rotateY(ringSpinAngle),
            new Vector3f(ringScale, 1.0f, ringScale),
            new Quaternionf()
        ));

        if (overheadNameplate != null && overheadNameplate.isValid()) {
            float plateScale = (!hideDuringCineCam && profile.settings().overheadNameplates()) ? 1.02f : 0.0f;
            Vector platePos = new Vector(curPos.getX(), curPos.getY() + 2.24, curPos.getZ());
            boolean moved = lastPlatePosition == null || lastPlatePosition.distanceSquared(platePos) > 2.5e-3;
            if (moved) {
                lastPlatePosition = platePos;
                overheadNameplate.teleport(new Location(loc.getWorld(), platePos.getX(), platePos.getY(), platePos.getZ()));
            }
            if (Math.abs(plateScale - lastPlateScale) > 0.001f) {
                lastPlateScale = plateScale;
                overheadNameplate.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(plateScale, plateScale, plateScale),
                    new Quaternionf()
                ));
            }
            int tagsHash = profile.equippedTags().hashCode();
            if (tagsHash != lastRenderedTagsHash || hotStreak != lastHotStreak) {
                refreshOverheadText(hotStreak);
            }
        }
    }

    private fr.bosketball.model.PlayerProfile profileOrNull(org.bukkit.entity.Player player) {
        return plugin.statsManager().getIfPresent(player.getUniqueId());
    }

    private void refreshOverheadText(int hotStreak) {
        if (overheadNameplate == null || !overheadNameplate.isValid()) {
            return;
        }
        PlayerProfile prof = profileOrNull(owner);
        if (prof == null) {
            return;
        }
        this.lastRenderedTagsHash = prof.equippedTags().hashCode();
        this.lastHotStreak = hotStreak;

        TextColor nameCol = teamSide == TeamSide.FROST ? TextColor.color(0x38BCFF) : TextColor.color(0xFF4C4C);
        TextColor barCol = teamSide == TeamSide.FROST ? TextColor.color(0x1EA4FF) : TextColor.color(0xE83232);

        TextComponent.Builder b = Component.text();
        b.append(Component.text(" " + owner.getName() + " ", Style.style(nameCol, TextDecoration.BOLD)));
        if (hotStreak >= 3) {
            b.append(Msg.parse("<gold>🔥</gold>"));
        }
        b.append(Component.newline());
        int barLen = Math.max(5, Math.min(12, owner.getName().length()));
        b.append(Component.text("━".repeat(barLen), barCol));

        List<ChatTag> tags = prof.equippedTags();
        if (!tags.isEmpty()) {
            b.append(Component.newline());
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    b.append(Component.text(" "));
                }
                b.append(tags.get(i).pillComponent());
            }
        }
        overheadNameplate.text(b.asComponent());
    }

    /**
     * Shows the overhead plate to everybody except its owner: the wearer must never see their
     * own nickname floating in front of the camera in first person.
     */
    private void applyNameplateVisibility() {
        if (overheadNameplate == null || !overheadNameplate.isValid() || owner == null) {
            return;
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getUniqueId().equals(owner.getUniqueId())) {
                online.hideEntity(plugin, overheadNameplate);
            } else {
                online.showEntity(plugin, overheadNameplate);
            }
        }
        loadPlatePosition();
    }

    private void loadPlatePosition() {
        if (overheadNameplate != null && overheadNameplate.isValid()) {
            lastPlatePosition = overheadNameplate.getLocation().toVector();
        }
    }

    public void hideFirstPersonRigIfOwner(Player joiner) {
        if (joiner == null || owner == null || overheadNameplate == null || !overheadNameplate.isValid()) {
            return;
        }
        if (joiner.getUniqueId().equals(owner.getUniqueId())) {
            joiner.hideEntity(plugin, overheadNameplate);
        } else {
            joiner.showEntity(plugin, overheadNameplate);
        }
    }

    public void despawn() {
        despawnAll();
    }

    public void despawnDisplaysOnly() {
        if (floorRingDisplay != null) {
            floorRingDisplay.remove();
            floorRingDisplay = null;
        }
        if (overheadNameplate != null) {
            overheadNameplate.remove();
            overheadNameplate = null;
        }
    }

    public void despawnAll() {
        despawnDisplaysOnly();
        if (owner != null && owner.isOnline()) {
            owner.setGlowing(false);
            lastPlayerGlow = false;
            owner.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (diveTicksRemaining > 0) {
                diveTicksRemaining = 0;
                try {
                    owner.setPose(Pose.STANDING, false);
                } catch (Throwable ignored) {
                }
            }
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
