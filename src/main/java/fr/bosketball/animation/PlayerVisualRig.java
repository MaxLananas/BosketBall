package fr.bosketball.animation;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
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
    private String currentRingModel = "";
    private float ringSpinAngle = 0f;

    private boolean offHandDribble = false;
    private AnimationKinematics.ActionClip activeClip = AnimationKinematics.ActionClip.NONE;
    private int clipTick = 0;
    private DunkStyle activeDunkStyle = DunkStyle.TOMAHAWK;
    private int diveTicksRemaining = 0;
    private boolean lastPlayerGlow = false;

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
        if (floorRingDisplay == null || !floorRingDisplay.isValid() || floorRingDisplay.getWorld() != loc.getWorld()) {
            despawnDisplaysOnly();
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

    }

    private fr.bosketball.model.PlayerProfile profileOrNull(org.bukkit.entity.Player player) {
        return plugin.statsManager().getIfPresent(player.getUniqueId());
    }

    public void despawn() {
        despawnAll();
    }

    public void despawnDisplaysOnly() {
        if (floorRingDisplay != null) {
            floorRingDisplay.remove();
            floorRingDisplay = null;
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
