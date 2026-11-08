package fr.bosketball.physics;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BasketballEntity {

    public enum BallMode {
        LOOSE,
        DRIBBLING,
        IN_PASS_FLIGHT,
        IN_SHOT_FLIGHT,
        IN_DUNK_FLIGHT
    }

    public interface ScoreListener {
        void onBasketScored(
            TeamSide hoopScoringSide,
            UUID shooterId,
            UUID assisterId,
            boolean threePointer,
            boolean slamDunk,
            boolean pureSwish,
            boolean bankShot,
            double shotDistanceBlocks
        );

        void onOutOfBounds(Vector lastPos, UUID lastTouchPlayerId);

        void onRimContact();
    }

    private static final float VISUAL_BALL_SCALE = 0.52f;

    private final Arena arena;
    private final World world;
    private final double gravity;
    private final double airDrag;
    private final double floorRestitution;
    private final double backboardRestitution;
    private final double rimRestitution;

    private ItemDisplay visualBall;
    private Interaction hitbox;

    private final Vector position;
    private final Vector velocity = new Vector(0, 0, 0);
    private final Quaternionf spinRotation = new Quaternionf();

    private BallMode mode = BallMode.LOOSE;
    private BallSkin skin = BallSkin.CLASSIC;
    private boolean onFireTrail = false;

    private UUID holderId;

    private java.util.function.Supplier<org.bukkit.Location> virtualHolderLocation;
    private String virtualHolderName;
    private UUID lastTouchId;
    private UUID shooterId;
    private UUID lastPasserId;

    /** Who threw the ball last: used to tell a passer their pass was picked off. */
    public UUID lastPasserId() {
        return lastPasserId;
    }
    private long lastPassArrivalMillis;
    private long pickupImmunityUntilMillis;

    private boolean shotIsThreePointer;
    private double shotDistanceBlocks;
    private boolean touchedRimOnCurrentShot;
    private boolean touchedBackboardOnCurrentShot;
    private boolean scoredCurrentFlight;

    private int dribbleTick = 0;
    private double dribblePhase = 0.0;
    private boolean rightHandDribble = true;
    private double handSideLerp = 1.0;
    private double crossoverDipTicks = 0.0;
    private double holderShotChargeProgress = -1.0;
    private UUID hiddenGatherViewerId = null;

    private DunkStyle activeDunkStyle = DunkStyle.TOMAHAWK;
    private Vector dunkStartPos;
    private Vector dunkRimTarget;
    private int dunkTick = 0;
    private int dunkTotalTicks = 14;
    private boolean dunkWillSucceed = false;
    private boolean dunkTimingLocked = false;
    private boolean virtualDunkFlight;

    public BasketballEntity(
        Arena arena,
        Location spawnLoc,
        double gravity,
        double airDrag,
        double floorRestitution,
        double backboardRestitution,
        double rimRestitution
    ) {
        this.arena = arena;
        this.world = spawnLoc.getWorld();
        this.position = spawnLoc.toVector();
        this.gravity = gravity;
        this.airDrag = airDrag;
        this.floorRestitution = floorRestitution;
        this.backboardRestitution = backboardRestitution;
        this.rimRestitution = rimRestitution;
        spawnEntities();
    }

    private void spawnEntities() {
        remove();
        if (world == null) {
            return;
        }
        Location loc = position.toLocation(world);
        this.visualBall = world.spawn(loc, ItemDisplay.class, disp -> {
            disp.setPersistent(false);
            disp.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            disp.setBillboard(Display.Billboard.FIXED);
            disp.setTeleportDuration(1);
            disp.setInterpolationDuration(1);
            disp.setBrightness(new Display.Brightness(15, 15));
            disp.setViewRange(2.5f);
            disp.setItemStack(skin.createBallItem(true));
            disp.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(spinRotation),
                new Vector3f(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE),
                new Quaternionf()
            ));
        });

        this.hitbox = world.spawn(loc.clone().subtract(0, PhysicsMath.BALL_RADIUS, 0), Interaction.class, inter -> {
            inter.setPersistent(false);
            inter.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            inter.setInteractionWidth(0.85f);
            inter.setInteractionHeight(0.85f);
            inter.setResponsive(true);
        });
    }

    public BallMode mode() {
        return mode;
    }

    public Vector position() {
        return position.clone();
    }

    public Vector velocity() {
        return velocity.clone();
    }

    public boolean hasVirtualHolder() {
        return virtualHolderLocation != null;
    }

    public String virtualHolderName() {
        return virtualHolderName;
    }

    public void bindToVirtualHolder(UUID entityId, org.bukkit.Location loc, String displayName) {
        org.bukkit.Location snapshot = loc != null ? loc.clone() : null;
        bindToVirtualHolderTracking(entityId, () -> snapshot, displayName);
    }

    /** Track a moving virtual holder without restarting its dribble every tick. */
    public void bindToVirtualHolderTracking(UUID entityId,
            java.util.function.Supplier<org.bukkit.Location> location, String displayName) {
        java.util.Objects.requireNonNull(location, "location");
        this.holderId = entityId;
        this.virtualHolderName = displayName;
        this.lastTouchId = entityId;
        this.shooterId = null;
        this.mode = BallMode.DRIBBLING;
        this.dribbleTick = 0;
        this.dribblePhase = 0.15;
        this.holderShotChargeProgress = -1.0;
        this.velocity.zero();
        this.scoredCurrentFlight = false;
        setHitboxActive(false);
        this.virtualHolderLocation = location;
    }

    private org.bukkit.Location resolveHolderLocation() {
        if (virtualHolderLocation != null) {
            return virtualHolderLocation.get();
        }
        Player p = holderId != null ? Bukkit.getPlayer(holderId) : null;
        return p != null && p.isOnline() ? p.getLocation() : null;
    }

    public UUID holderId() {
        return holderId;
    }

    public UUID lastTouchId() {
        return lastTouchId;
    }

    public boolean isSlamDunkFlight() {
        return mode == BallMode.IN_DUNK_FLIGHT;
    }

    public UUID shooterId() {
        return shooterId;
    }

    public BallSkin skin() {
        return skin;
    }

    public boolean isOnFireTrail() {
        return onFireTrail;
    }

    public Quaternionf spinRotationSnapshot() {
        return new Quaternionf(spinRotation);
    }

    public ItemDisplay visualDisplayEntity() {
        return visualBall;
    }

    public boolean isRightHandDribble() {
        return rightHandDribble;
    }

    public double dribbleWave01() {
        return 0.5 + 0.5 * Math.cos(dribblePhase * Math.PI * 2.0);
    }

    public void setHolderShotChargeProgress(double progressOrNegative) {
        this.holderShotChargeProgress = progressOrNegative;
    }

    public double dunkProgress() {
        if (mode != BallMode.IN_DUNK_FLIGHT || dunkTotalTicks <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) dunkTick / (double) dunkTotalTicks);
    }

    public boolean isDunkTimingLocked() {
        return dunkTimingLocked;
    }

    public boolean willDunkSucceed() {
        return dunkWillSucceed;
    }

    public void lockDunkTiming(boolean success) {
        this.dunkTimingLocked = true;
        this.dunkWillSucceed = success;
    }

    public void setSkin(BallSkin newSkin, boolean onFire) {
        this.onFireTrail = onFire;
        BallSkin effective = onFire ? BallSkin.FIRE : (newSkin != null ? newSkin : BallSkin.CLASSIC);
        if (this.skin != effective) {
            this.skin = effective;
            if (visualBall != null && visualBall.isValid()) {
                visualBall.setItemStack(effective.createBallItem(true));
            }
        }
    }

    public void attachToHolder(Player player, BallSkin preferredSkin, boolean onFire) {
        if (this.holderId != null && !this.holderId.equals(player.getUniqueId()) && mode == BallMode.IN_PASS_FLIGHT) {
            this.lastPasserId = this.lastTouchId;
            this.lastPassArrivalMillis = System.currentTimeMillis();
        } else if (mode != BallMode.IN_PASS_FLIGHT) {
            this.lastPasserId = null;
        }

        this.holderId = player.getUniqueId();
        this.lastTouchId = player.getUniqueId();
        this.shooterId = null;
        this.mode = BallMode.DRIBBLING;
        this.dribbleTick = 0;
        this.dribblePhase = 0.15;
        this.holderShotChargeProgress = -1.0;
        this.velocity.zero();
        this.scoredCurrentFlight = false;
        setHitboxActive(false);
        setSkin(preferredSkin, onFire);
        Msg.playPackSound(player.getLocation(), "ball.bounce", 0.65f, 1.08f);
    }

    public void triggerCrossoverHandSwitch(Player holder) {
        this.rightHandDribble = !this.rightHandDribble;
        this.crossoverDipTicks = 8.0;
        this.dribblePhase = 0.52;
        swingActiveDribbleArm(holder);
        Msg.playPackSound(holder.getLocation(), "shoe.squeak", 0.95f, 1.05f + (float) (Math.random() * 0.2));
        Msg.playPackSound(holder.getLocation(), "ball.bounce", 0.95f, 1.24f);
    }

    private void clearGatherBallHide() {
        if (hiddenGatherViewerId != null) {
            Player prev = Bukkit.getPlayer(hiddenGatherViewerId);
            BosketBallPlugin plug = BosketBallPlugin.getInstance();
            if (prev != null && prev.isOnline() && plug != null && visualBall != null && visualBall.isValid()) {
                prev.showEntity(plug, visualBall);
            }
            hiddenGatherViewerId = null;
        }
    }

    public void launchPass(Player passer, Vector startPos, Vector passVelocity, boolean isAlleyOopLob) {
        clearGatherBallHide();
        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        this.shooterId = passer.getUniqueId();
        this.lastTouchId = passer.getUniqueId();
        this.lastPasserId = passer.getUniqueId();
        this.mode = BallMode.IN_PASS_FLIGHT;
        this.position.copy(clampAboveFloorAndBlocks(startPos));
        this.velocity.copy(passVelocity);

        Vector southRim = arena.geometry().southHoopRim();
        Vector northRim = arena.geometry().northHoopRim();
        Vector projected = startPos.clone().add(passVelocity.clone().multiply(16.0));
        Vector targetRim = projected.distanceSquared(southRim) <= projected.distanceSquared(northRim) ? southRim : northRim;
        this.shotIsThreePointer = arena.geometry().isThreePointShot(passer.getLocation().getX(), passer.getLocation().getZ(), targetRim);
        this.shotDistanceBlocks = startPos.distance(targetRim);
        this.touchedRimOnCurrentShot = false;
        this.touchedBackboardOnCurrentShot = false;

        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 260L;
        this.scoredCurrentFlight = false;
        setHitboxActive(true);
        passer.swingMainHand();
        if (isAlleyOopLob) {
            passer.swingOffHand();
        }
        Msg.playPackSound(passer.getLocation(), "ball.bounce", 0.75f, isAlleyOopLob ? 1.26f : 1.15f);
    }

    public void launchShotVirtual(Vector startPos, Vector shotVelocity, boolean threePointer,
                                  double distanceBlocks, UUID shooterIdVirtual) {
        clearGatherBallHide();
        this.virtualHolderLocation = null;
        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        this.shooterId = shooterIdVirtual;
        this.lastTouchId = shooterIdVirtual;
        this.mode = BallMode.IN_SHOT_FLIGHT;
        this.shotIsThreePointer = threePointer;
        this.shotDistanceBlocks = distanceBlocks;
        this.touchedRimOnCurrentShot = false;
        this.touchedBackboardOnCurrentShot = false;
        this.scoredCurrentFlight = false;
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 340L;
        this.position.copy(clampAboveFloorAndBlocks(startPos));
        this.velocity.copy(shotVelocity);
        setHitboxActive(true);
        if (world != null) {
            Msg.playPackSound(position.toLocation(world), "shoe.squeak", 0.55f, 1.1f);
        }
    }

    public void launchPassVirtual(Vector startPos, Vector passVelocity, UUID passerIdVirtual) {
        clearGatherBallHide();
        this.virtualHolderLocation = null;
        this.mode = BallMode.IN_PASS_FLIGHT;
        this.holderId = null;
        this.shooterId = null;
        this.lastTouchId = passerIdVirtual;
        this.lastPasserId = passerIdVirtual;
        this.velocity.copy(passVelocity);
        this.position.copy(clampAboveFloorAndBlocks(startPos));
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 180L;
        this.scoredCurrentFlight = false;
        setHitboxActive(true);
        if (world != null) {
            Msg.playPackSound(position.toLocation(world), "ball.bounce", 0.72f, 1.15f);
        }
    }

    public void launchDunkVirtual(UUID dunkerId, Vector targetRim, org.bukkit.Location dunkerLoc, int totalTicks,
                                  boolean success) {
        clearGatherBallHide();
        this.virtualHolderLocation = null;
        this.holderId = dunkerId;
        this.shooterId = dunkerId;
        this.lastTouchId = dunkerId;
        this.mode = BallMode.IN_DUNK_FLIGHT;
        this.activeDunkStyle = DunkStyle.TOMAHAWK;
        this.dunkStartPos = dunkerLoc != null ? dunkerLoc.toVector() : position.clone();
        this.dunkRimTarget = targetRim.clone();
        this.dunkTick = 0;
        this.dunkTotalTicks = Math.max(10, totalTicks);
        this.dunkWillSucceed = success;
        this.dunkTimingLocked = true;
        this.virtualDunkFlight = true;
        this.shotIsThreePointer = false;
        this.shotDistanceBlocks = dunkStartPos.distance(targetRim);
        this.scoredCurrentFlight = false;
        setHitboxActive(false);
    }

    public void launchShot(
        Player shooter,
        Vector startPos,
        Vector shotVelocity,
        boolean threePointer,
        double distanceBlocks
    ) {
        clearGatherBallHide();
        UUID activeAssister = null;
        if (lastPasserId != null
            && !lastPasserId.equals(shooter.getUniqueId())
            && (System.currentTimeMillis() - lastPassArrivalMillis) <= 5500L) {
            activeAssister = lastPasserId;
        }
        this.lastPasserId = activeAssister;

        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        this.shooterId = shooter.getUniqueId();
        this.lastTouchId = shooter.getUniqueId();
        this.mode = BallMode.IN_SHOT_FLIGHT;
        this.shotIsThreePointer = threePointer;
        this.shotDistanceBlocks = distanceBlocks;
        this.touchedRimOnCurrentShot = false;
        this.touchedBackboardOnCurrentShot = false;
        this.scoredCurrentFlight = false;
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 340L;

        this.position.copy(clampAboveFloorAndBlocks(startPos));
        this.velocity.copy(shotVelocity);
        setHitboxActive(true);

        shooter.swingMainHand();
        Msg.playPackSound(shooter.getLocation(), "shoe.squeak", 0.55f, 1.15f);
    }

    public void startSlamDunk(
        Player dunker,
        Vector targetRim,
        DunkStyle style,
        int totalTicks,
        boolean defaultSuccess
    ) {
        clearGatherBallHide();
        UUID activeAssister = null;
        if (lastPasserId != null
            && !lastPasserId.equals(dunker.getUniqueId())
            && (System.currentTimeMillis() - lastPassArrivalMillis) <= 5500L) {
            activeAssister = lastPasserId;
        }
        this.lastPasserId = activeAssister;

        this.holderId = dunker.getUniqueId();
        this.holderShotChargeProgress = -1.0;
        this.shooterId = dunker.getUniqueId();
        this.lastTouchId = dunker.getUniqueId();
        this.mode = BallMode.IN_DUNK_FLIGHT;
        this.activeDunkStyle = style != null ? style : DunkStyle.TOMAHAWK;
        this.dunkStartPos = dunker.getLocation().toVector();
        this.dunkRimTarget = targetRim.clone();
        this.dunkTick = 0;
        this.dunkTotalTicks = Math.max(10, totalTicks);
        this.dunkWillSucceed = defaultSuccess;
        this.dunkTimingLocked = false;
        this.virtualDunkFlight = false;
        this.shotIsThreePointer = false;
        this.shotDistanceBlocks = dunkStartPos.distance(targetRim);
        this.scoredCurrentFlight = false;
        setHitboxActive(false);

        Vector horizLook = dunker.getLocation().getDirection().setY(0.0);
        if (horizLook.lengthSquared() > 1e-4) {
            horizLook.normalize();
        }
        Vector curVel = dunker.getVelocity();
        // Rim-ward assist: a dunk started a bit far from the hoop still reaches it, so the
        // flight does not end in mid-air and the finish actually happens at the rim.
        Vector towardRim = new Vector(
            targetRim.getX() - dunker.getLocation().getX(), 0.0,
            targetRim.getZ() - dunker.getLocation().getZ()
        );
        double takeoffDist = towardRim.length();
        if (towardRim.lengthSquared() > 1.0e-4) {
            towardRim.normalize().multiply(Math.min(0.34, 0.10 + takeoffDist * 0.07));
        }
        Vector leapVel = new Vector(
            curVel.getX() * 0.40 + horizLook.getX() * 0.42 + towardRim.getX(),
            Math.max(curVel.getY() + 0.24, 0.60),
            curVel.getZ() * 0.40 + horizLook.getZ() * 0.42 + towardRim.getZ()
        );
        dunker.setVelocity(leapVel);
        dunker.setFallDistance(0f);

        BosketBallPlugin plug = BosketBallPlugin.getInstance();
        if (plug != null && visualBall != null && visualBall.isValid()) {
            dunker.showEntity(plug, visualBall);
        }
        Msg.playPackSound(dunker.getLocation(), "shoe.squeak", 1.0f, 0.92f);
    }

    public void startSlamDunk(Player dunker, Vector targetRim, DunkStyle style, int totalTicks) {
        startSlamDunk(dunker, targetRim, style, totalTicks, false);
    }

    public void popLoose(Vector impulse, UUID toucherId) {
        clearGatherBallHide();
        this.virtualHolderLocation = null;
        this.virtualHolderName = null;
        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        if (toucherId != null) {
            this.lastTouchId = toucherId;
        }
        this.mode = BallMode.LOOSE;
        this.position.copy(clampAboveFloorAndBlocks(this.position));
        this.velocity.copy(impulse);
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 340L;
        setHitboxActive(true);
    }

    public void resetToCenterTipOff(Vector centerFloor, double upwardVelocity) {
        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        this.shooterId = null;
        this.lastTouchId = null;
        this.lastPasserId = null;
        this.mode = BallMode.LOOSE;
        this.scoredCurrentFlight = false;
        this.position.setX(centerFloor.getX());
        this.position.setY(centerFloor.getY() + 1.35);
        this.position.setZ(centerFloor.getZ());
        this.velocity.setX(0);
        this.velocity.setY(upwardVelocity);
        this.velocity.setZ(0);
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 350L;
        setHitboxActive(true);
        syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
    }

    public void tick(ScoreListener listener) {
        if (visualBall == null || !visualBall.isValid() || hitbox == null || !hitbox.isValid()) {
            spawnEntities();
            setHitboxActive(holderId == null && mode != BallMode.IN_DUNK_FLIGHT);
        }

        if (mode == BallMode.DRIBBLING) {
            tickDribblingOrShotGather();
            return;
        }

        if (mode == BallMode.IN_DUNK_FLIGHT) {
            tickSlamDunk(listener);
            return;
        }

        // Round 42: a non-finite state is unrecoverable once it reaches the entity. `Vector.length()`
        // of a NaN vector is NaN, every comparison below turns false, and the ball stops colliding
        // with anything while `teleport(NaN)` spams the console. One check per tick is enough.
        if (!isStateFinite()) {
            recoverFromInvalidState();
            return;
        }

        int substeps = 4;
        double dt = 1.0 / substeps;
        // A NaN / infinite airDrag (config typo) would poison every substep; 0.995 is the default.
        double subDrag = Math.pow(Double.isFinite(airDrag) ? Math.min(1.0, Math.max(0.5, airDrag)) : 0.995,
            dt);

        Vector northRim = arena.geometry().northHoopRim();
        Vector northFwd = arena.geometry().northHoopForward();
        Vector southRim = arena.geometry().southHoopRim();
        Vector southFwd = arena.geometry().southHoopForward();
        double courtFloorTopY = arena.geometry().floorY() + PhysicsMath.BALL_RADIUS;

        for (int step = 0; step < substeps; step++) {
            Vector prevPos = position.clone();

            velocity.setY(velocity.getY() - gravity * dt);
            velocity.multiply(subDrag);
            Vector stepDelta = velocity.clone().multiply(dt);

            boolean hitWorldVoxel = false;
            double moveLen = stepDelta.length();
            if (world != null && moveLen > 1e-4) {
                Vector dir = stepDelta.clone().multiply(1.0 / moveLen);
                RayTraceResult rayHit = world.rayTraceBlocks(
                    prevPos.toLocation(world),
                    dir,
                    moveLen + PhysicsMath.BALL_RADIUS,
                    FluidCollisionMode.NEVER,
                    true
                );
                if (rayHit != null && rayHit.getHitBlock() != null && rayHit.getHitBlockFace() != null) {
                    BlockFace face = rayHit.getHitBlockFace();
                    Vector normal = face.getDirection();
                    Vector safePos = rayHit.getHitPosition().add(normal.clone().multiply(PhysicsMath.BALL_RADIUS + 0.005));
                    position.copy(safePos);

                    double vn = velocity.dot(normal);
                    if (vn < 0.0) {
                        double impact = -vn;

                        Vector vnVec = normal.clone().multiply(vn);
                        Vector vtVec = velocity.clone().subtract(vnVec).multiply(0.89);
                        Vector reflectedNormal = normal.clone().multiply(-vn * floorRestitution);
                        if (face == BlockFace.UP && impact < 0.055) {
                            reflectedNormal.zero();
                        }
                        velocity.copy(vtVec.add(reflectedNormal));
                        if (impact > 0.055) {
                            float vol = (float) Math.min(1.0, impact * 2.1);
                            Msg.playPackSound(position.toLocation(world), "ball.bounce", vol, 0.94f + (float) (Math.random() * 0.14));
                        }
                        if (face == BlockFace.UP && mode == BallMode.IN_SHOT_FLIGHT) {
                            mode = BallMode.LOOSE;
                        }
                    }
                    hitWorldVoxel = true;
                }
            }

            if (!hitWorldVoxel) {
                position.add(stepDelta);
            }

            clampRestingFloorCollision(courtFloorTopY);

            arena.clampBallInsideBarrier(position, velocity, 0.72);

            collideBackboardForHoop(northRim, northFwd);
            collideBackboardForHoop(southRim, southFwd);

            collideRimForHoop(northRim, listener);
            collideRimForHoop(southRim, listener);

            if (!scoredCurrentFlight && listener != null) {
                if (PhysicsMath.didScoreDownwardThroughHoop(prevPos, position, northRim)) {
                    handleScoredThroughHoop(TeamSide.FROST, arena.northHoopDisplay(), northRim, listener);
                } else if (PhysicsMath.didScoreDownwardThroughHoop(prevPos, position, southRim)) {
                    handleScoredThroughHoop(TeamSide.IGNIS, arena.southHoopDisplay(), southRim, listener);
                }
            }
        }

        if (listener != null && !arena.geometry().isInBounds(position.getX(), position.getZ())
            && position.getY() <= arena.geometry().floorY() + PhysicsMath.BALL_RADIUS + 0.35) {
            listener.onOutOfBounds(position.clone(), lastTouchId);
            return;
        }

        double speed = velocity.length();
        if (speed > 0.008) {
            Vector horizAxis = new Vector(-velocity.getZ(), 0, velocity.getX());
            if (horizAxis.lengthSquared() > 1e-5) {
                horizAxis.normalize();
                spinRotation.rotateAxis(
                    (float) (speed * 1.42),
                    (float) horizAxis.getX(),
                    0f,
                    (float) horizAxis.getZ()
                );
            } else {
                spinRotation.rotateX((float) (speed * 0.9));
            }
        }

        spawnFlightTrailParticles();
        syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
    }

    /**
     * {@code true} when the position, the velocity and the spin are all usable numbers.
     *
     * <p>Checked once at the top of {@link #tick}: cheaper than trusting every downstream
     * comparison, and the only way to keep a physics entity alive after a bad frame.
     */
    public boolean isStateFinite() {
        return Double.isFinite(position.getX()) && Double.isFinite(position.getY())
            && Double.isFinite(position.getZ())
            && Double.isFinite(velocity.getX()) && Double.isFinite(velocity.getY())
            && Double.isFinite(velocity.getZ());
    }

    /**
     * Puts the ball back on the centre spot after a non-finite state, and logs nothing: this is a
     * recovery path, not an error the operator has to act on.
     */
    private void recoverFromInvalidState() {
        var geometry = arena.geometry();
        double x = Double.isFinite(geometry.centerX()) ? geometry.centerX() : 0.0;
        double z = Double.isFinite(geometry.centerZ()) ? geometry.centerZ() : 0.0;
        double floor = Double.isFinite(geometry.floorY()) ? geometry.floorY() : 64.0;
        position.setX(x);
        position.setY(floor + 1.35);
        position.setZ(z);
        velocity.setX(0.0);
        velocity.setY(0.0);
        velocity.setZ(0.0);
        pickupImmunityUntilMillis = System.currentTimeMillis() + 350L;
        syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
    }

    private void clampRestingFloorCollision(double courtFloorTopY) {

        if (world != null) {
            Block below = world.getBlockAt(
                (int) Math.floor(position.getX()),
                (int) Math.floor(position.getY() - PhysicsMath.BALL_RADIUS - 0.03),
                (int) Math.floor(position.getZ())
            );
            if (!below.isPassable()) {
                BoundingBox box = below.getBoundingBox();
                double solidTop = box.getMaxY() + PhysicsMath.BALL_RADIUS;
                if (position.getY() < solidTop && position.getY() >= box.getMaxY() - 0.45) {
                    position.setY(solidTop);
                    if (velocity.getY() < 0) {
                        double impact = -velocity.getY();
                        velocity.setY(impact > 0.055 ? impact * floorRestitution : 0.0);
                        velocity.setX(velocity.getX() * 0.89);
                        velocity.setZ(velocity.getZ() * 0.89);
                    }
                }
            }
        }

        if (arena.geometry().isInBounds(position.getX(), position.getZ()) && position.getY() < courtFloorTopY) {
            double impact = -velocity.getY();
            position.setY(courtFloorTopY);
            if (velocity.getY() < 0) {
                if (impact > 0.055) {
                    velocity.setY(impact * floorRestitution);
                    float vol = (float) Math.min(1.0, impact * 2.1);
                    if (world != null) {
                        Msg.playPackSound(position.toLocation(world), "ball.bounce", vol, 0.95f + (float) (Math.random() * 0.12));
                    }
                } else {
                    velocity.setY(0.0);
                }
                velocity.setX(velocity.getX() * 0.89);
                velocity.setZ(velocity.getZ() * 0.89);
                if (mode == BallMode.IN_SHOT_FLIGHT) {
                    mode = BallMode.LOOSE;
                }
            }
        }
    }

    private Vector clampAboveFloorAndBlocks(Vector raw) {
        Vector safe = raw.clone();
        double minCourtY = arena.geometry().floorY() + PhysicsMath.BALL_RADIUS + 0.02;
        if (arena.geometry().isInBounds(safe.getX(), safe.getZ()) && safe.getY() < minCourtY) {
            safe.setY(minCourtY);
        }
        if (world != null) {
            Block b = world.getBlockAt(safe.getBlockX(), safe.getBlockY(), safe.getBlockZ());
            if (!b.isPassable()) {
                safe.setY(Math.max(safe.getY(), b.getBoundingBox().getMaxY() + PhysicsMath.BALL_RADIUS + 0.02));
            }
        }
        return safe;
    }

    private void tickAIDribble() {
        Location pLoc = resolveHolderLocation();
        if (pLoc == null || (world != null && !pLoc.getWorld().equals(world))) {
            popLoose(new Vector(0, 0.2, 0), null);
            return;
        }
        dribbleTick++;
        double yawRad = Math.toRadians(pLoc.getYaw());
        Vector forward = new Vector(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vector right = new Vector(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));

        double targetSide = rightHandDribble ? 1.0 : -1.0;
        handSideLerp += (targetSide - handSideLerp) * 0.30;
        double prevPhase = dribblePhase;
        dribblePhase = (dribblePhase + 0.105) % 1.0;
        if (prevPhase < 0.50 && dribblePhase >= 0.50 && world != null) {
            Msg.playPackSound(position.toLocation(world), "ball.bounce", 0.52f, 1.02f);
        }
        if (dribbleTick % 34 == 0) {
            rightHandDribble = !rightHandDribble;
        }

        double distFromFloorContact = Math.abs(dribblePhase - 0.5) * 2.0;
        double parabola = 1.0 - Math.pow(1.0 - distFromFloorContact, 2.0);
        double floorY = Math.max(arena.geometry().floorY(), pLoc.getY());
        double minBounceY = floorY + PhysicsMath.BALL_RADIUS;
        double maxHandY = pLoc.getY() + 0.94;
        double y = minBounceY + parabola * Math.max(0.18, maxHandY - minBounceY);

        double lateral = 0.32 * handSideLerp;
        position.setX(pLoc.getX() + forward.getX() * 0.42 + right.getX() * lateral);
        position.setY(y);
        position.setZ(pLoc.getZ() + forward.getZ() * 0.42 + right.getZ() * lateral);
        spinRotation.rotateX(-0.10f);
        syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
    }

    private void tickDribblingOrShotGather() {
        if (virtualHolderLocation != null) {
            tickAIDribble();
            return;
        }
        Player holder = holderId != null ? Bukkit.getPlayer(holderId) : null;
        if (holder == null || !holder.isOnline() || !holder.getWorld().equals(world)) {
            popLoose(new Vector(0, 0.2, 0), null);
            return;
        }

        dribbleTick++;
        Location pLoc = holder.getLocation();
        double yawRad = Math.toRadians(pLoc.getYaw());
        Vector forward = new Vector(-Math.sin(yawRad), 0.0, Math.cos(yawRad));

        Vector playerRight = new Vector(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));

        boolean activelyDrawingBow = holder.isHandRaised() && holder.getActiveItem().getType() == org.bukkit.Material.BOW;
        if (holderShotChargeProgress >= 0.0 || activelyDrawingBow) {
            double effectiveCharge = holderShotChargeProgress >= 0.0 ? holderShotChargeProgress : 0.08;

            if (!holder.getUniqueId().equals(hiddenGatherViewerId) && visualBall != null && visualBall.isValid()) {
                clearGatherBallHide();
                BosketBallPlugin plug = BosketBallPlugin.getInstance();
                if (plug != null) {
                    holder.hideEntity(plug, visualBall);
                    hiddenGatherViewerId = holder.getUniqueId();
                }
            }
            double gatherBlend = Math.min(1.0, effectiveCharge / 0.28);
            boolean mainRight = holder.getMainHand() == MainHand.RIGHT;
            double handSign = mainRight ? 1.0 : -1.0;

            Vector eye = holder.getEyeLocation().toVector();
            Vector pocketTarget = eye.clone()
                .add(forward.clone().multiply(0.38))
                .add(playerRight.clone().multiply(0.16 * handSign))
                .add(new Vector(0.0, 0.05 + 0.20 * Math.min(1.0, effectiveCharge), 0.0));

            position.multiply(1.0 - 0.55 * gatherBlend).add(pocketTarget.multiply(0.55 * gatherBlend));
            spinRotation.rotateX(-0.14f);

            if (onFireTrail && dribbleTick % 2 == 0) {
                world.spawnParticle(Particle.FLAME, position.toLocation(world), 2, 0.06, 0.06, 0.06, 0.01);
            }
            syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
            return;
        }
        clearGatherBallHide();

        double targetSide = rightHandDribble ? 1.0 : -1.0;
        handSideLerp += (targetSide - handSideLerp) * 0.34;
        if (crossoverDipTicks > 0.0) {
            crossoverDipTicks = Math.max(0.0, crossoverDipTicks - 1.0);
        }

        boolean sprinting = holder.isSprinting();
        boolean protecting = holder.isSneaking();
        double cycleStep = sprinting ? 0.135 : (protecting ? 0.115 : 0.095);
        double prevPhase = dribblePhase;
        dribblePhase = (dribblePhase + cycleStep) % 1.0;

        if (prevPhase < 0.18 && dribblePhase >= 0.18) {
            swingActiveDribbleArm(holder);
        }

        if (prevPhase < 0.50 && dribblePhase >= 0.50) {
            Msg.playPackSound(
                position.toLocation(world),
                "ball.bounce",
                sprinting ? 0.62f : 0.44f,
                sprinting ? 1.12f : (protecting ? 1.18f : 1.00f)
            );
            if (sprinting && dribbleTick % 12 == 0) {
                Msg.playPackSound(pLoc, "shoe.squeak", 0.42f, 1.0f + (float) (Math.random() * 0.18));
            }
        }

        double distFromFloorContact = Math.abs(dribblePhase - 0.5) * 2.0;
        double parabola = 1.0 - Math.pow(1.0 - distFromFloorContact, 2.0);

        double localFloorY = Math.max(arena.geometry().floorY(), pLoc.getY());
        double minBounceY = localFloorY + PhysicsMath.BALL_RADIUS;
        double handHeightOffset = protecting ? 0.64 : (sprinting ? 0.86 : 0.92);
        if (crossoverDipTicks > 0.0) {
            handHeightOffset -= 0.18 * (crossoverDipTicks / 8.0);
        }
        double maxHandY = pLoc.getY() + handHeightOffset;
        double y = minBounceY + parabola * Math.max(0.18, maxHandY - minBounceY);

        double lateralDist = 0.34 * handSideLerp;
        double forwardDist = sprinting ? 0.54 : (protecting ? 0.34 : 0.44);
        Vector velLead = holder.getVelocity().clone().setY(0.0).multiply(0.45);

        position.setX(pLoc.getX() + forward.getX() * forwardDist + playerRight.getX() * lateralDist + velLead.getX());
        position.setY(y);
        position.setZ(pLoc.getZ() + forward.getZ() * forwardDist + playerRight.getZ() * lateralDist + velLead.getZ());

        float sx = VISUAL_BALL_SCALE;
        float sy = VISUAL_BALL_SCALE;
        if (distFromFloorContact < 0.16) {
            float squash = (float) ((0.16 - distFromFloorContact) / 0.16);
            sy = VISUAL_BALL_SCALE * (1.0f - 0.20f * squash);
            sx = VISUAL_BALL_SCALE * (1.0f + 0.11f * squash);
        }

        spinRotation.rotateX(sprinting ? 0.34f : 0.20f);
        if (Math.abs(targetSide - handSideLerp) > 0.08) {
            spinRotation.rotateZ((float) ((targetSide - handSideLerp) * 0.35));
        }

        if (onFireTrail) {
            world.spawnParticle(Particle.FLAME, position.toLocation(world), 2, 0.08, 0.08, 0.08, 0.01);
        }
        syncEntities(sx, sy, sx);
    }

    private void swingActiveDribbleArm(Player holder) {
        boolean mainIsRight = holder.getMainHand() == MainHand.RIGHT;
        if (rightHandDribble == mainIsRight) {
            holder.swingMainHand();
        } else {
            holder.swingOffHand();
        }
    }

    private void tickSlamDunk(ScoreListener listener) {
        org.bukkit.entity.Entity entity = holderId != null && Bukkit.getServer() != null
            ? Bukkit.getPlayer(holderId) : null;
        if (entity == null && holderId != null && Bukkit.getServer() != null) {
            entity = Bukkit.getEntity(holderId);
        }
        if (!(entity instanceof org.bukkit.entity.LivingEntity dunker) || !dunker.isValid()
            || (dunker instanceof Player player && !player.isOnline())) {
            popLoose(new Vector(0, -0.2, 0), null);
            return;
        }

        dunkTick++;
        double t = Math.min(1.0, (double) dunkTick / dunkTotalTicks);
        Vector hoopFwd = arena.geometry().hoopForwardForRim(dunkRimTarget);

        Location dLoc = dunker.getLocation();
        Vector curVel = dunker.getVelocity();
        double dxRim = dLoc.getX() - dunkRimTarget.getX();
        double dzRim = dLoc.getZ() - dunkRimTarget.getZ();
        double fwdFromRim = dxRim * hoopFwd.getX() + dzRim * hoopFwd.getZ();
        if (fwdFromRim < 0.48) {

            double pushOut = (0.48 - fwdFromRim) * 0.45;
            curVel.add(hoopFwd.clone().multiply(pushOut));
        }
        double horizDistNow = Math.hypot(dxRim, dzRim);
        if (horizDistNow > 1.75) {
            // Magnetic finish: glide the dunker in so the slam lands instead of hanging short.
            Vector pull = new Vector(-dxRim, 0.0, -dzRim).normalize()
                .multiply(Math.min(0.20, (horizDistNow - 1.75) * 0.11));
            curVel.add(pull);
        }
        if (t < 0.62 && curVel.getY() > -0.15) {
            curVel.setY(Math.min(0.62, curVel.getY() + 0.032));
        }
        dunker.setVelocity(curVel);
        dunker.setFallDistance(0f);

        double yawRad = Math.toRadians(dLoc.getYaw());
        if (activeDunkStyle == DunkStyle.SPIN_360) {
            yawRad += t * Math.PI * 2.0;
        }
        Vector ballOffset = activeDunkStyle.computeBallOffset(t, yawRad);
        Vector handBallPos = dLoc.toVector().add(ballOffset);
        double horizDistToRim = Math.hypot(dxRim, dzRim);

        if (dunkTimingLocked && dunkWillSucceed && horizDistToRim <= 2.90 && t > 0.55) {
            double flushBlend = Math.min(1.0, (t - 0.55) / 0.35);
            Vector flushSpot = dunkRimTarget.clone().add(new Vector(0.0, 0.16 * (1.0 - flushBlend), 0.0));
            handBallPos = handBallPos.multiply(1.0 - flushBlend).add(flushSpot.multiply(flushBlend));
        }

        position.copy(handBallPos);
        spinRotation.rotateX(0.48f);

        world.spawnParticle(
            Particle.DUST,
            position.toLocation(world),
            4, 0.12, 0.12, 0.12,
            new Particle.DustOptions(Color.fromRGB(255, 130, 36), 1.3f)
        );
        if (activeDunkStyle == DunkStyle.METEOR_JAM || onFireTrail) {
            world.spawnParticle(Particle.FLAME, position.toLocation(world), 5, 0.15, 0.15, 0.15, 0.03);
        }

        boolean readyToFinishNow = (dunkTimingLocked && dunkTick >= Math.max(7, (int) (dunkTotalTicks * 0.72)))
            || dunkTick >= dunkTotalTicks;

        if (readyToFinishNow) {

            boolean withinRimReach = horizDistToRim <= 2.90 && (dLoc.getY() + 1.40) >= (dunkRimTarget.getY() - 1.15);
            boolean makeDunk = dunkTimingLocked && dunkWillSucceed && withinRimReach;

            dunker.swingMainHand();
            dunker.swingOffHand();
            dunker.setFallDistance(0f);

            boolean isNorthRim = dunkRimTarget.distanceSquared(arena.geometry().northHoopRim())
                < dunkRimTarget.distanceSquared(arena.geometry().southHoopRim());
            HoopDisplaySet hoopDisp = isNorthRim ? arena.northHoopDisplay() : arena.southHoopDisplay();
            if (hoopDisp != null && makeDunk) {
                hoopDisp.triggerDunkFlexAnimation();
            }

            if (makeDunk) {

                this.holderId = null;
                this.mode = BallMode.LOOSE;
                this.position.copy(dunkRimTarget.clone().subtract(new Vector(0, 0.25, 0)));
                this.velocity.setX((Math.random() - 0.5) * 0.05);
                this.velocity.setY(-0.34);
                this.velocity.setZ((Math.random() - 0.5) * 0.05);
                this.scoredCurrentFlight = true;
                this.pickupImmunityUntilMillis = System.currentTimeMillis() + 700L;
                setHitboxActive(true);

                TeamSide hoopScoringSide = isNorthRim ? TeamSide.FROST : TeamSide.IGNIS;
                Msg.playPackSound(dunkRimTarget.toLocation(world), "game.dunk_boom", 1.35f, 1.0f);
                world.spawnParticle(Particle.EXPLOSION, dunkRimTarget.toLocation(world), 2, 0.2, 0.1, 0.2, 0.0);
                world.spawnParticle(Particle.FIREWORK, dunkRimTarget.toLocation(world), 28, 0.35, 0.35, 0.35, 0.18);

                if (listener != null) {
                    listener.onBasketScored(
                        hoopScoringSide,
                        shooterId,
                        lastPasserId,
                        false,
                        true,
                        true,
                        false,
                        shotDistanceBlocks
                    );
                }
            } else {

                this.holderId = null;
                this.mode = BallMode.LOOSE;
                if (virtualDunkFlight) {
                    // A missed bot finish leaves the hand where it actually was, not at the rim.
                    this.position.copy(handBallPos);
                    this.velocity.copy(hoopFwd.clone().multiply(0.28).add(new Vector(0, 0.22, 0)));
                } else {
                    this.position.copy(dunkRimTarget.clone().add(new Vector(0, 0.32, 0)));
                    this.velocity.copy(
                        hoopFwd.clone().multiply(0.28 + Math.random() * 0.14)
                            .add(new Vector((Math.random() - 0.5) * 0.24, 0.46, (Math.random() - 0.5) * 0.24))
                    );
                }
                this.scoredCurrentFlight = false;
                this.pickupImmunityUntilMillis = System.currentTimeMillis() + 520L;
                setHitboxActive(true);

                if (!virtualDunkFlight || handBallPos.distanceSquared(dunkRimTarget) < 1.0) {
                    Msg.playPackSound(dunkRimTarget.toLocation(world), "ball.rim_hit", 1.25f, 0.82f);
                    world.spawnParticle(Particle.CRIT, dunkRimTarget.toLocation(world), 22, 0.25, 0.2, 0.25, 0.16);
                    if (listener != null) listener.onRimContact();
                }
            }
            virtualDunkFlight = false;
        }

        syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
    }

    private void collideBackboardForHoop(Vector rim, Vector hoopForward) {
        PhysicsMath.CollisionResult res = PhysicsMath.collideWithBackboard(position, velocity, rim, hoopForward, backboardRestitution);
        if (res.hit()) {
            position.copy(res.position());
            velocity.copy(res.velocity());
            touchedBackboardOnCurrentShot = true;
            if (res.impactSpeed() > 0.04) {
                Msg.playPackSound(position.toLocation(world), "ball.backboard", (float) Math.min(1.0, res.impactSpeed() * 2.2), 1.0f);
            }
        }
    }

    private void collideRimForHoop(Vector rim, ScoreListener listener) {
        PhysicsMath.CollisionResult res = PhysicsMath.collideWithRimTorus(position, velocity, rim, rimRestitution);
        if (res.hit()) {
            position.copy(res.position());
            velocity.copy(res.velocity());
            touchedRimOnCurrentShot = true;
            if (listener != null) {
                listener.onRimContact();
            }
            if (res.impactSpeed() > 0.03) {
                Msg.playPackSound(
                    position.toLocation(world),
                    "ball.rim_hit",
                    (float) Math.min(1.0, res.impactSpeed() * 2.4),
                    0.95f + (float) (Math.random() * 0.12)
                );
                world.spawnParticle(Particle.CRIT, position.toLocation(world), 6, 0.1, 0.1, 0.1, 0.08);
            }
        }
    }

    private void handleScoredThroughHoop(
        TeamSide hoopScoringSide,
        HoopDisplaySet hoopDisplay,
        Vector rimCenter,
        ScoreListener listener
    ) {
        this.scoredCurrentFlight = true;
        boolean pureSwish = !touchedRimOnCurrentShot;
        boolean bank = touchedBackboardOnCurrentShot;

        velocity.setX(velocity.getX() * 0.28);
        velocity.setZ(velocity.getZ() * 0.28);
        velocity.setY(Math.max(velocity.getY(), -0.26));
        this.mode = BallMode.LOOSE;
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 550L;

        if (hoopDisplay != null) {
            hoopDisplay.triggerSwishNetAnimation();
        }
        Msg.playPackSound(rimCenter.toLocation(world), "ball.swish", 1.15f, pureSwish ? 1.06f : 0.96f);
        world.spawnParticle(Particle.CLOUD, rimCenter.toLocation(world).subtract(0, 0.35, 0), 10, 0.18, 0.15, 0.18, 0.02);

        listener.onBasketScored(
            hoopScoringSide,
            shooterId,
            lastPasserId,
            shotIsThreePointer,
            false,
            pureSwish,
            bank,
            shotDistanceBlocks
        );
    }

    private void spawnFlightTrailParticles() {
        if (world == null) {
            return;
        }
        if (onFireTrail) {
            world.spawnParticle(Particle.FLAME, position.toLocation(world), 3, 0.07, 0.07, 0.07, 0.01);
            world.spawnParticle(Particle.SMOKE, position.toLocation(world), 1, 0.05, 0.05, 0.05, 0.0);
        } else if (mode == BallMode.IN_SHOT_FLIGHT || mode == BallMode.IN_PASS_FLIGHT) {
            world.spawnParticle(
                Particle.DUST,
                position.toLocation(world),
                1, 0.02, 0.02, 0.02,
                new Particle.DustOptions(Color.fromRGB(255, 198, 92), 0.72f)
            );
        }
    }

    /** Apply the same pickup geometry and last-touch immunity to a virtual player. */
    public boolean canBePickedUpByVirtual(UUID playerId, Location location) {
        if (playerId == null || location == null || holderId != null || mode == BallMode.IN_DUNK_FLIGHT
            || !java.util.Objects.equals(location.getWorld(), world)) return false;
        if (System.currentTimeMillis() < pickupImmunityUntilMillis && playerId.equals(lastTouchId)) return false;
        double dx = location.getX() - position.getX();
        double dz = location.getZ() - position.getZ();
        double dy = location.getY() + 0.95 - position.getY();
        return dx * dx + dz * dz <= 1.42 * 1.42 && Math.abs(dy) <= 1.48;
    }

    public boolean canBePickedUpBy(Player player) {
        if (holderId != null || mode == BallMode.IN_DUNK_FLIGHT) {
            return false;
        }
        if (System.currentTimeMillis() < pickupImmunityUntilMillis && player.getUniqueId().equals(lastTouchId)) {
            return false;
        }
        Location pLoc = player.getLocation();
        if (!pLoc.getWorld().equals(world)) {
            return false;
        }
        double dx = pLoc.getX() - position.getX();
        double dz = pLoc.getZ() - position.getZ();
        double dy = (pLoc.getY() + 0.95) - position.getY();
        return (dx * dx + dz * dz) <= (1.42 * 1.42) && Math.abs(dy) <= 1.48;
    }

    private void setHitboxActive(boolean active) {
        if (hitbox != null && hitbox.isValid()) {
            float size = active ? 0.85f : 0.0f;
            hitbox.setInteractionWidth(size);
            hitbox.setInteractionHeight(size);
        }
    }

    private void syncEntities(float sx, float sy, float sz) {
        if (world == null) {
            return;
        }
        Location ballLoc = position.toLocation(world);
        if (visualBall != null && visualBall.isValid()) {
            visualBall.teleport(ballLoc);
            visualBall.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(spinRotation),
                new Vector3f(sx, sy, sz),
                new Quaternionf()
            ));
        }
        if (hitbox != null && hitbox.isValid()) {
            hitbox.teleport(ballLoc.clone().subtract(0, PhysicsMath.BALL_RADIUS, 0));
        }
    }

    public void remove() {
        clearGatherBallHide();
        if (visualBall != null) {
            visualBall.remove();
            visualBall = null;
        }
        if (hitbox != null) {
            hitbox.remove();
            hitbox = null;
        }
    }
}
