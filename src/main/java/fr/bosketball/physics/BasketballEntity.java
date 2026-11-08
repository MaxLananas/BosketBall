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

        /**
         * A shot left the shooter's hands and ended without a basket: floor, rim-out, out of
         * bounds, or picked off mid-air. Without this the on-fire streak only ever grew, because
         * a bucket incremented it and nothing in the game ever took it back.
         */
        default void onShotMissed(UUID shooterId) {
        }
    }

    /**
     * The live ball carries its own tag, next to the court display tag: the arena sweep that
     * deletes orphaned court entities must be able to tell the ball apart and leave it alone.
     */
    public static final String LIVE_BALL_TAG = "bb_live_ball";
    private static final double RENDER_DRIFT_BLOCKS_SQ = 4.0; // A display this far off is a desync.

    private static final float VISUAL_BALL_SCALE = 0.52f;
    /**
     * How often the ball re-declares itself visible to the players around it. The ball keeps no
     * hidden set of its own any more, so on a healthy client this is a cheap no-op; it is there for
     * the hides other subsystems put on the display (a replay camera, a nameplate sweep).
     */
    private static final int VISIBLE_REDECLARE_TICKS = 10;
    /**
     * The client culls a display entity with a box, and that box is built from the display's own
     * width and height - which default to zero. A zero-size box is a single point at the ball's
     * centre: if that point falls outside what the client is drawing, the whole ball goes with it
     * while the server keeps simulating a perfectly valid entity. One real block of box fixes it.
     */
    private static final float VISUAL_BALL_CULL_SIZE = 1.10f;
    /** A ball rebuilt this often is a render loop, not a repair: it gets said out loud, once. */
    private static final int RENDER_REBUILD_STORM = 16;
    private static final long RENDER_REBUILD_WINDOW_MILLIS = 3000L;
    /**
     * A display rebuilt on every tick is never on screen. The client is told to create the entity
     * and to drop it faster than it can draw a frame, so the ball strobes - and a strobe that lands
     * on the wrong frame is an invisible ball with a perfectly normal score. No repair rebuilds the
     * display twice inside this many ticks, whatever is wrong with it.
     */
    private static final int REPAIR_MIN_INTERVAL_TICKS = 4;
    /**
     * A ball in the air re-spawns its display on a rolling clock: one entity every six ticks, which
     * nobody can see, and the only thing that hands the ball back to a client that quietly lost it
     * mid-shot without anybody pressing anything or waiting for a repair condition.
     */
    private static final int FLIGHT_REFRESH_TICKS = 6;
    private static final double WORLD_CONTACT_SLOP = 0.005; // Tiny offset keeps the swept ball outside block faces.
    private static final double IMPACT_SOUND_THRESHOLD = 0.055; // Softer contacts settle instead of chattering.
    private static final double IMPACT_SOUND_VOLUME_SCALE = 2.1; // Converts block/tick impact speed to pack volume.
    private static final double CROSSOVER_DIP_DURATION_TICKS = 8.0; // Matches the eight-tick crossover animation dip.
    private static final double CROSSOVER_DIP_HEIGHT_BLOCKS = 0.18; // Keeps the ball tucked during hand transfer.
    private static final double CROSSOVER_RESET_PHASE = 0.52; // Starts just after the floor-contact phase point.
    private static final double MIN_SHOT_CHARGE_PROGRESS = 0.0; // A valid charged release may begin at zero.
    private static final double MAX_SHOT_CHARGE_PROGRESS = 1.20; // Preserve the existing full-charge grace range.
    private static final double MAX_RECORDED_SHOT_DISTANCE = 40.0; // Beyond a full court, use the probability floor.
    private static final double DEFAULT_LOOSE_POP_UP_SPEED = 0.20; // Safe upward fallback for malformed impulses.
    private static final double DEFAULT_TIPOFF_UPWARD_SPEED = 0.65; // Keeps malformed tip-offs visibly playable.
    private static final double MAX_TIPOFF_UPWARD_SPEED = 2.0; // Prevents corrupted inputs launching the ball away.
    private static final double FALLBACK_ARENA_CENTER_XZ = 0.0; // Recovery anchor when arena geometry is malformed.
    private static final double FALLBACK_FLOOR_Y = 64.0; // Standard safe court height for invalid geometry.
    private static final double TIPOFF_BALL_HEIGHT_OFFSET = 1.35; // Centres the ball above the court floor.
    private static final int MIN_DUNK_FLIGHT_TICKS = 10; // Avoids a zero-duration dunk animation.
    private static final int MAX_DUNK_FLIGHT_TICKS = 48; // Bounds work and timeline arithmetic for virtual dunks.

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
    private boolean shotFlightWasActive = false;
    private boolean missReportedForFlight = false;

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
    private int crossoverCooldownTicksRemaining = 0;
    private double holderShotChargeProgress = -1.0;
    private int renderResyncTicks = 0;
    private int renderRebuildsInWindow = 0;
    private long renderRebuildWindowStartMillis = 0L;
    private boolean renderStormLogged = false;
    private int ticksSinceDisplaySpawn = 0;
    private int ticksSinceHitboxSpawn = 0;
    private int displayRepairsSuppressed = 0;
    private final java.util.Set<String> repairReasonsLogged = new java.util.HashSet<>();
    private boolean renderWarningLogged = false;
    private boolean tickFailureLogged = false;

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
        // The world may legitimately be absent: the harness builds balls headlessly and every
        // world-touching path below already guards on a null world. What cannot be absent is a
        // real arena and a finite spawn -- a NaN position would poison the whole simulation.
        if (arena == null || spawnLoc == null || !MovementMath.isFinite(spawnLoc.toVector())) {
            throw new IllegalArgumentException("BasketballEntity requires a finite spawn inside an arena");
        }
        this.arena = arena;
        this.world = spawnLoc.getWorld();
        this.position = PhysicsMath.boundedVector(spawnLoc.toVector());
        this.gravity = PhysicsMath.sanitizeGravity(gravity);
        this.airDrag = PhysicsMath.sanitizeAirDrag(airDrag);
        this.floorRestitution = PhysicsMath.sanitizeRestitution(floorRestitution, PhysicsMath.FLOOR_RESTITUTION);
        this.backboardRestitution = PhysicsMath.sanitizeRestitution(
            backboardRestitution, PhysicsMath.BACKBOARD_RESTITUTION);
        this.rimRestitution = PhysicsMath.sanitizeRestitution(rimRestitution, PhysicsMath.RIM_RESTITUTION);
        spawnEntities();
    }

    private void spawnEntities() {
        remove();
        if (world == null) {
            return;
        }
        noteRenderRebuild();
        spawnVisualBall();
        spawnHitbox();
        // A brand new entity is never in a player's hidden set, but say it out loud: the shooter
        // who just released a shot gets the ball back on screen on this exact tick.
        revealToEveryone();
    }

    /**
     * Rebuilds the ball on screen without touching the interaction hitbox.
     *
     * <p>The ball is never hidden, so this is not an undo: it is a fresh spawn packet. A display
     * that a client has stopped drawing for any reason - a hide that outlived its reason, a
     * cancelled teleport, a culling box the client computed as empty - is only ever brought back by
     * a new entity, and a new entity is one line of work.
     */
    private void respawnVisualBall() {
        if (world == null) {
            return;
        }
        // Reset on the attempt, not on the success: a spawn that keeps failing must be retried on
        // the repair clock, not on every tick.
        ticksSinceDisplaySpawn = 0;
        if (visualBall != null) {
            visualBall.remove();
            visualBall = null;
        }
        noteRenderRebuild();
        spawnVisualBall();
        revealToEveryone();
        syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
    }

    /**
     * Counts rebuilds so a rebuild loop can be told apart from a rare repair. A ball rebuilt a
     * dozen times inside three seconds is being eaten by something on every tick, and no player
     * sees a ball that is deleted and re-spawned that fast: the log line is the only way that state
     * ever leaves the server.
     */
    private static void logWarning(String message) {
        try {
            Bukkit.getLogger().warning(message);
        } catch (RuntimeException ignored) {
            // No Bukkit server means no logger (the offline harness). A repair that happened must
            // never fail the tick that found it.
        }
    }

    private void noteRenderRebuild() {
        long now = System.currentTimeMillis();
        if (now - renderRebuildWindowStartMillis > RENDER_REBUILD_WINDOW_MILLIS) {
            renderRebuildWindowStartMillis = now;
            renderRebuildsInWindow = 0;
        }
        renderRebuildsInWindow++;
        if (renderRebuildsInWindow >= RENDER_REBUILD_STORM && !renderStormLogged) {
            renderStormLogged = true;
            logWarning("[BosketBall] The ball display was rebuilt "
                + renderRebuildsInWindow + " times in under " + (RENDER_REBUILD_WINDOW_MILLIS / 1000)
                + "s at " + fmtLoc(position) + " (mode " + mode + "). Something is removing it every"
                + " tick; the ball is probably invisible to the players while this lasts.");
        }
    }

    private static String fmtLoc(Vector loc) {
        return String.format(java.util.Locale.ROOT, "%.2f/%.2f/%.2f", loc.getX(), loc.getY(), loc.getZ());
    }

    private void spawnVisualBall() {
        if (world == null) {
            return;
        }
        Location loc = position.toLocation(world);
        normalizeSpin();
        try {
            this.visualBall = world.spawn(loc, ItemDisplay.class, disp -> {
                disp.setPersistent(false);
                disp.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                disp.addScoreboardTag(LIVE_BALL_TAG);
                disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                disp.setBillboard(Display.Billboard.FIXED);
                disp.setTeleportDuration(1);
                disp.setInterpolationDuration(1);
                disp.setBrightness(new Display.Brightness(15, 15));
                disp.setViewRange(2.5f);
                // A box the client can cull with. Zero - the default - is a point, and a point that
                // leaves the frustum takes the whole ball out of the frame with it.
                disp.setDisplayWidth(VISUAL_BALL_CULL_SIZE);
                disp.setDisplayHeight(VISUAL_BALL_CULL_SIZE);
                disp.setItemStack(safeBallItem());
                disp.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(spinRotation),
                    new Vector3f(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE),
                    new Quaternionf()
                ));
            });
            ticksSinceDisplaySpawn = 0;
        } catch (RuntimeException e) {
            this.visualBall = null;
            warnRenderOnce("the ball display could not be spawned", e);
        }
    }

    /**
     * A rotation quaternion has to stay unit length: the client turns it into a matrix, and a
     * drifted one scales the model instead of turning it. A thousand dribbles of float error is
     * not much, but it is free to remove.
     */
    private void normalizeSpin() {
        float lengthSquared = spinRotation.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0e-6f) {
            spinRotation.identity();
            return;
        }
        if (Math.abs(lengthSquared - 1.0f) > 1.0e-4f) {
            spinRotation.normalize();
        }
    }

    /**
     * The ball item comes from a resource-pack skin. A skin that fails to build must never cost the
     * players the ball itself: the classic ball is the fallback, and the operator hears about it
     * once.
     */
    private org.bukkit.inventory.ItemStack safeBallItem() {
        BallSkin safeSkin = skin == null ? BallSkin.CLASSIC : skin;
        try {
            return safeSkin.createBallItem(true);
        } catch (RuntimeException e) {
            warnRenderOnce("the " + safeSkin.name() + " ball item could not be built, using the classic ball", e);
            return BallSkin.CLASSIC.createBallItem(true);
        }
    }

    private void warnRenderOnce(String message, Throwable cause) {
        if (renderWarningLogged) {
            return;
        }
        renderWarningLogged = true;
        String line = "[BosketBall] " + message
            + " - the ball keeps being rebuilt until it sticks.";
        if (cause == null) {
            logWarning(line);
        } else {
            try {
                Bukkit.getLogger().log(java.util.logging.Level.WARNING, line, cause);
            } catch (RuntimeException ignored) {
                // Same reason as logWarning: no server, no logger, no failure.
            }
        }
    }

    private void spawnHitbox() {
        if (world == null) {
            return;
        }
        Location loc = position.toLocation(world);
        // The hitbox box is a block tall and was placed with its centre a radius under the ball, so
        // a ball resting on the floor asked for a hitbox centred *inside* the floor block. Nothing
        // guarantees a spawn there sticks; lift it clear instead.
        Location hitboxLoc = loc.clone().subtract(0, PhysicsMath.BALL_RADIUS, 0);
        double clearOfFloor = arena.geometry().floorY() + 0.02;
        if (hitboxLoc.getY() < clearOfFloor) {
            hitboxLoc.setY(clearOfFloor);
        }
        // Reset on the attempt: a hitbox that cannot be spawned is retried on the repair clock.
        ticksSinceHitboxSpawn = 0;
        try {
            this.hitbox = world.spawn(hitboxLoc, Interaction.class, inter -> {
                inter.setPersistent(false);
                inter.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                inter.addScoreboardTag(LIVE_BALL_TAG);
                inter.setInteractionWidth(0.85f);
                inter.setInteractionHeight(0.85f);
                inter.setResponsive(true);
            });
        } catch (RuntimeException e) {
            this.hitbox = null;
            warnRenderOnce("the ball hitbox could not be spawned", e);
        }
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
        if (entityId == null || loc == null || loc.getWorld() == null || world == null
            || !loc.getWorld().equals(world) || !MovementMath.isFinite(loc.toVector())) {
            return;
        }
        org.bukkit.Location snapshot = loc.clone();
        bindToVirtualHolderTracking(entityId, () -> snapshot, displayName);
    }

    /** Track a moving virtual holder without restarting its dribble every tick. */
    public void bindToVirtualHolderTracking(UUID entityId,
            java.util.function.Supplier<org.bukkit.Location> location, String displayName) {
        java.util.Objects.requireNonNull(location, "location");
        this.holderId = entityId;
        this.virtualHolderName = displayName == null ? "" : displayName;
        this.lastTouchId = entityId;
        this.shooterId = null;
        this.mode = BallMode.DRIBBLING;
        this.dribbleTick = 0;
        this.dribblePhase = 0.15;
        this.holderShotChargeProgress = -1.0;
        this.velocity.zero();
        this.scoredCurrentFlight = false;
        this.missReportedForFlight = false;
        setHitboxActive(false);
        this.virtualHolderLocation = location;
    }

    private org.bukkit.Location resolveHolderLocation() {
        if (virtualHolderLocation != null) {
            return virtualHolderLocation.get();
        }
        Player p = holderId != null ? Bukkit.getPlayer(holderId) : null;
        return isPlayerInBallWorld(p) ? p.getLocation() : null;
    }

    private boolean isPlayerInBallWorld(Player player) {
        if (player == null || !player.isOnline() || world == null || player.getWorld() == null
            || !player.getWorld().equals(world)) {
            return false;
        }
        Location location = player.getLocation();
        return location != null && location.getWorld() != null && location.getWorld().equals(world)
            && MovementMath.isFinite(location.toVector())
            && Float.isFinite(location.getYaw()) && Float.isFinite(location.getPitch());
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
        if (!Double.isFinite(progressOrNegative) || progressOrNegative < 0.0) {
            this.holderShotChargeProgress = -1.0;
            return;
        }
        this.holderShotChargeProgress = Math.clamp(progressOrNegative,
            MIN_SHOT_CHARGE_PROGRESS, MAX_SHOT_CHARGE_PROGRESS);
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
        if (!isPlayerInBallWorld(player)) {
            return;
        }
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
        this.missReportedForFlight = false;
        setHitboxActive(false);
        setSkin(preferredSkin, onFire);
        revealTo(player);
        Msg.playPackSound(player.getLocation(), "ball.bounce", 0.65f, 1.08f);
    }

    public void triggerCrossoverHandSwitch(Player holder) {
        if (!isPlayerInBallWorld(holder)) {
            return;
        }
        this.rightHandDribble = !this.rightHandDribble;
        this.crossoverDipTicks = CROSSOVER_DIP_DURATION_TICKS;
        this.crossoverCooldownTicksRemaining = DribbleCadence.DEFAULT_CROSSOVER_COOLDOWN_TICKS;
        this.dribblePhase = CROSSOVER_RESET_PHASE;
        swingActiveDribbleArm(holder);
        Msg.playPackSound(holder.getLocation(), "shoe.squeak", 0.95f, 1.05f + (float) (Math.random() * 0.2));
        Msg.playPackSound(holder.getLocation(), "ball.bounce", 0.95f, 1.24f);
    }

    /** Shows the live ball to one player, if it can be shown at all. */
    public void revealTo(Player player) {
        if (player == null || !player.isOnline() || visualBall == null || !visualBall.isValid()) {
            return;
        }
        BosketBallPlugin plug = BosketBallPlugin.getInstance();
        if (plug != null) {
            player.showEntity(plug, visualBall);
        }
    }

    /**
     * Re-declares the live ball visible to every player in the arena world.
     *
     * <p>The plugin never hides the ball itself, so on its own account this is redundant. It stays
     * because the display can be hidden by something else - the replay camera hides the live ball
     * from the player watching a cinematic - and because a re-show is the only cheap answer to a
     * client that stopped drawing the ball for a reason no server can observe.
     */
    public void revealToEveryone() {
        if (world == null || visualBall == null || !visualBall.isValid()) {
            return;
        }
        for (Player viewer : world.getPlayers()) {
            try {
                revealTo(viewer);
            } catch (RuntimeException e) {
                // One viewer whose client state refuses the packet must not stop the others from
                // getting their ball back.
                warnRenderOnce("the ball could not be shown to " + viewer.getName(), e);
            }
        }
    }

    /**
     * Puts the freshly rebuilt display where the ball is *now*. Without this the new entity spends
     * one tick at the position the ball had when the hand let go, which reads as a ball that does
     * not leave the hand - the same frame in which the shot is supposed to be on screen.
     */
    private void settleFreshDisplay() {
        syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
    }

    /**
     * Called the moment the ball leaves a hand: a shot, a pass, a dunk, a loose ball.
     *
     * <p>It rebuilds the display instead of trusting it. A fresh entity is rendered from a fresh
     * spawn packet, which is the one thing a client cannot get wrong - no hidden set survives it,
     * no interpolation state carries over, and the shooter sees the ball they just released on
     * every server, on every client, first try.
     */
    private void presentBallForLaunch() {
        if (world == null) {
            return;
        }
        if (visualBall == null || !visualBall.isValid() || hitbox == null || !hitbox.isValid()) {
            // Nothing on screen to rebuild on top of: build the whole ball.
            spawnEntities();
            return;
        }
        respawnVisualBall();
    }

    public void launchPass(Player passer, Vector startPos, Vector passVelocity, boolean isAlleyOopLob) {
        // Rebuilt before the guard: a rejected pass must not leave the passer blind.
        presentBallForLaunch();
        if (!isPlayerInBallWorld(passer) || !MovementMath.isFinite(startPos)
            || !MovementMath.isFinite(passVelocity)) {
            return;
        }
        Vector safeStart = PhysicsMath.boundedVector(startPos);
        Vector safePassVelocity = PhysicsMath.boundedVector(passVelocity);
        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        this.shooterId = passer.getUniqueId();
        this.lastTouchId = passer.getUniqueId();
        this.lastPasserId = passer.getUniqueId();
        this.mode = BallMode.IN_PASS_FLIGHT;
        this.position.copy(clampAboveFloorAndBlocks(safeStart));
        this.velocity.copy(safePassVelocity);

        Vector southRim = arena.geometry().southHoopRim();
        Vector northRim = arena.geometry().northHoopRim();
        Vector projected = safeStart.clone().add(safePassVelocity.clone().multiply(16.0));
        Vector targetRim = projected.distanceSquared(southRim) <= projected.distanceSquared(northRim) ? southRim : northRim;
        this.shotIsThreePointer = arena.geometry().isThreePointShot(passer.getLocation().getX(), passer.getLocation().getZ(), targetRim);
        this.shotDistanceBlocks = safeShotDistance(safeStart.distance(targetRim));
        this.touchedRimOnCurrentShot = false;
        this.touchedBackboardOnCurrentShot = false;

        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 260L;
        this.scoredCurrentFlight = false;
        this.missReportedForFlight = false;
        setHitboxActive(true);
        passer.swingMainHand();
        if (isAlleyOopLob) {
            passer.swingOffHand();
        }
        Msg.playPackSound(passer.getLocation(), "ball.bounce", 0.75f, isAlleyOopLob ? 1.26f : 1.15f);
        settleFreshDisplay();
    }

    public void launchShotVirtual(Vector startPos, Vector shotVelocity, boolean threePointer,
                                  double distanceBlocks, UUID shooterIdVirtual) {
        presentBallForLaunch();
        if (!MovementMath.isFinite(startPos) || !MovementMath.isFinite(shotVelocity)) {
            return;
        }
        Vector safeStart = PhysicsMath.boundedVector(startPos);
        Vector safeShotVelocity = PhysicsMath.boundedVector(shotVelocity);
        this.virtualHolderLocation = null;
        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        this.shooterId = shooterIdVirtual;
        this.lastTouchId = shooterIdVirtual;
        this.mode = BallMode.IN_SHOT_FLIGHT;
        this.shotIsThreePointer = threePointer;
        this.shotDistanceBlocks = safeShotDistance(distanceBlocks);
        this.touchedRimOnCurrentShot = false;
        this.touchedBackboardOnCurrentShot = false;
        this.scoredCurrentFlight = false;
        this.missReportedForFlight = false;
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 340L;
        this.position.copy(clampAboveFloorAndBlocks(safeStart));
        this.velocity.copy(safeShotVelocity);
        setHitboxActive(true);
        if (world != null) {
            Msg.playPackSound(position.toLocation(world), "shoe.squeak", 0.55f, 1.1f);
        }
        settleFreshDisplay();
    }

    public void launchPassVirtual(Vector startPos, Vector passVelocity, UUID passerIdVirtual) {
        presentBallForLaunch();
        if (!MovementMath.isFinite(startPos) || !MovementMath.isFinite(passVelocity)) {
            return;
        }
        Vector safeStart = PhysicsMath.boundedVector(startPos);
        Vector safePassVelocity = PhysicsMath.boundedVector(passVelocity);
        this.virtualHolderLocation = null;
        this.mode = BallMode.IN_PASS_FLIGHT;
        this.holderId = null;
        this.shooterId = null;
        this.lastTouchId = passerIdVirtual;
        this.lastPasserId = passerIdVirtual;
        this.velocity.copy(safePassVelocity);
        this.position.copy(clampAboveFloorAndBlocks(safeStart));
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 180L;
        this.scoredCurrentFlight = false;
        this.missReportedForFlight = false;
        setHitboxActive(true);
        if (world != null) {
            Msg.playPackSound(position.toLocation(world), "ball.bounce", 0.72f, 1.15f);
        }
        settleFreshDisplay();
    }
    public void launchDunkVirtual(UUID dunkerId, Vector targetRim, org.bukkit.Location dunkerLoc, int totalTicks,
                                  boolean success) {
        presentBallForLaunch();
        if (dunkerId == null || !MovementMath.isFinite(targetRim)) {
            return;
        }
        Vector safeRim = PhysicsMath.boundedVector(targetRim);
        Vector safeDunkerStart = dunkerLoc != null && MovementMath.isFinite(dunkerLoc.toVector())
            ? PhysicsMath.boundedVector(dunkerLoc.toVector()) : PhysicsMath.boundedVector(position);
        this.virtualHolderLocation = null;
        this.holderId = dunkerId;
        this.shooterId = dunkerId;
        this.lastTouchId = dunkerId;
        this.mode = BallMode.IN_DUNK_FLIGHT;
        this.activeDunkStyle = DunkStyle.TOMAHAWK;
        this.dunkStartPos = safeDunkerStart;
        this.dunkRimTarget = safeRim;
        this.dunkTotalTicks = Math.clamp(totalTicks, MIN_DUNK_FLIGHT_TICKS, MAX_DUNK_FLIGHT_TICKS);
        this.dunkWillSucceed = success;
        this.dunkTimingLocked = true;
        this.virtualDunkFlight = true;
        this.shotIsThreePointer = false;
        this.shotDistanceBlocks = safeShotDistance(dunkStartPos.distance(safeRim));
        this.scoredCurrentFlight = false;
        this.missReportedForFlight = false;
        setHitboxActive(false);
        settleFreshDisplay();
    }

    public void launchShot(
        Player shooter,
        Vector startPos,
        Vector shotVelocity,
        boolean threePointer,
        double distanceBlocks
    ) {
        // Rebuilt before the guard: the shot the player just released has to be on their screen,
        // even on the paths that reject the launch (bad world, non-finite velocity).
        presentBallForLaunch();
        if (!isPlayerInBallWorld(shooter) || !MovementMath.isFinite(startPos)
            || !MovementMath.isFinite(shotVelocity)) {
            return;
        }
        Vector safeStart = PhysicsMath.boundedVector(startPos);
        Vector safeShotVelocity = PhysicsMath.boundedVector(shotVelocity);
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
        this.shotDistanceBlocks = safeShotDistance(distanceBlocks);
        this.touchedRimOnCurrentShot = false;
        this.touchedBackboardOnCurrentShot = false;
        this.scoredCurrentFlight = false;
        this.missReportedForFlight = false;
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 340L;

        this.position.copy(clampAboveFloorAndBlocks(safeStart));
        this.velocity.copy(safeShotVelocity);
        setHitboxActive(true);

        shooter.swingMainHand();
        Msg.playPackSound(shooter.getLocation(), "shoe.squeak", 0.55f, 1.15f);
        settleFreshDisplay();
    }

    public void startSlamDunk(
        Player dunker,
        Vector targetRim,
        DunkStyle style,
        int totalTicks,
        boolean defaultSuccess
    ) {
        presentBallForLaunch();
        if (!isPlayerInBallWorld(dunker) || !MovementMath.isFinite(targetRim)
            || !MovementMath.isFinite(dunker.getLocation().toVector())) {
            return;
        }
        Vector safeRim = PhysicsMath.boundedVector(targetRim);
        Vector safeDunkerStart = PhysicsMath.boundedVector(dunker.getLocation().toVector());
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
        this.dunkStartPos = safeDunkerStart;
        this.dunkRimTarget = safeRim;
        this.dunkTick = 0;
        this.dunkTotalTicks = Math.clamp(totalTicks, MIN_DUNK_FLIGHT_TICKS, MAX_DUNK_FLIGHT_TICKS);
        this.dunkWillSucceed = defaultSuccess;
        this.dunkTimingLocked = false;
        this.virtualDunkFlight = false;
        this.shotIsThreePointer = false;
        this.shotDistanceBlocks = safeShotDistance(dunkStartPos.distance(safeRim));
        this.scoredCurrentFlight = false;
        this.missReportedForFlight = false;
        setHitboxActive(false);

        Vector rawLook = dunker.getLocation().getDirection();
        Vector horizLook = MovementMath.isFinite(rawLook)
            ? PhysicsMath.boundedVector(rawLook).setY(0.0) : new Vector(0.0, 0.0, 1.0);
        if (horizLook.lengthSquared() > 1.0e-4) {
            horizLook.normalize();
        } else {
            horizLook.setX(0.0).setZ(1.0);
        }
        Vector curVel = MovementMath.isFinite(dunker.getVelocity())
            ? PhysicsMath.boundedVector(dunker.getVelocity()) : new Vector(0.0, 0.0, 0.0);
        // Rim-ward assist: a dunk started a bit far from the hoop still reaches it, so the
        // flight does not end in mid-air and the finish actually happens at the rim.
        Vector towardRim = new Vector(
            safeRim.getX() - safeDunkerStart.getX(), 0.0,
            safeRim.getZ() - safeDunkerStart.getZ()
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

        Msg.playPackSound(dunker.getLocation(), "shoe.squeak", 1.0f, 0.92f);
        settleFreshDisplay();
    }

    public void startSlamDunk(Player dunker, Vector targetRim, DunkStyle style, int totalTicks) {
        startSlamDunk(dunker, targetRim, style, totalTicks, false);
    }

    public void popLoose(Vector impulse, UUID toucherId) {
        Vector safeImpulse = MovementMath.isFinite(impulse)
            ? PhysicsMath.boundedVector(impulse)
            : new Vector(0.0, DEFAULT_LOOSE_POP_UP_SPEED, 0.0);
        this.virtualHolderLocation = null;
        this.virtualHolderName = null;
        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        if (toucherId != null) {
            this.lastTouchId = toucherId;
        }
        this.mode = BallMode.LOOSE;
        this.position.copy(clampAboveFloorAndBlocks(this.position));
        this.velocity.copy(safeImpulse);
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 340L;
        setHitboxActive(true);
        revealToEveryone();
    }

    public void resetToCenterTipOff(Vector centerFloor, double upwardVelocity) {
        var geometry = arena.geometry();
        Vector safeCenter;
        if (MovementMath.isFinite(centerFloor)) {
            safeCenter = PhysicsMath.boundedVector(centerFloor);
        } else {
            double centerX = Double.isFinite(geometry.centerX()) ? geometry.centerX() : FALLBACK_ARENA_CENTER_XZ;
            double centerZ = Double.isFinite(geometry.centerZ()) ? geometry.centerZ() : FALLBACK_ARENA_CENTER_XZ;
            double floorY = Double.isFinite(geometry.floorY()) ? geometry.floorY() : FALLBACK_FLOOR_Y;
            safeCenter = PhysicsMath.boundedVector(new Vector(centerX, floorY, centerZ));
        }
        double safeUpwardVelocity = Double.isFinite(upwardVelocity)
            ? Math.clamp(upwardVelocity, 0.0, MAX_TIPOFF_UPWARD_SPEED)
            : DEFAULT_TIPOFF_UPWARD_SPEED;
        this.holderId = null;
        this.holderShotChargeProgress = -1.0;
        this.shooterId = null;
        this.lastTouchId = null;
        this.lastPasserId = null;
        this.mode = BallMode.LOOSE;
        this.scoredCurrentFlight = false;
        this.missReportedForFlight = false;
        this.position.setX(safeCenter.getX());
        this.position.setY(safeCenter.getY() + TIPOFF_BALL_HEIGHT_OFFSET);
        this.position.setZ(safeCenter.getZ());
        this.velocity.setX(0);
        this.velocity.setY(safeUpwardVelocity);
        this.velocity.setZ(0);
        this.pickupImmunityUntilMillis = System.currentTimeMillis() + 350L;
        setHitboxActive(true);
        revealToEveryone();
        syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
    }

    /**
     * One bad frame must not cost the match its ball. Every physics path below is written to
     * survive bad input, but a world, a plugin hook or a client can still throw inside a tick: the
     * catch puts the ball back on the centre spot and rebuilds its display, which is a visible
     * loose ball instead of an invisible frozen one.
     */
    public void tick(ScoreListener listener) {
        try {
            tickUnsafe(listener);
        } catch (RuntimeException e) {
            handleBrokenTick(e);
        }
    }

    private void handleBrokenTick(RuntimeException e) {
        if (!tickFailureLogged) {
            tickFailureLogged = true;
            Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                "[BosketBall] A ball tick failed; the ball was reset to the centre spot."
                    + " The match keeps running.", e);
        }
        try {
            recoverFromInvalidState();
            spawnEntities();
            syncEntities(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE);
        } catch (RuntimeException second) {
            warnRenderOnce("the ball could not be reset after a failed tick", second);
        }
    }

    private void tickUnsafe(ScoreListener listener) {
        renderResyncTicks++;
        ticksSinceDisplaySpawn++;
        ticksSinceHitboxSpawn++;
        repairRenderIfBroken();
        if (mode != BallMode.DRIBBLING && ticksSinceDisplaySpawn >= FLIGHT_REFRESH_TICKS) {
            // A shot is the one moment the ball has to be on screen, so it is also the one moment
            // no state is trusted: the display is replaced on a rolling clock for as long as the
            // ball is in the air. Same position, same scale, one entity - invisible to the eye,
            // decisive against a client that lost the entity without telling anyone.
            respawnVisualBall();
        }
        // A ball in the air is the ball everybody is watching, so it re-declares itself twice as
        // often as a ball being dribbled. Nothing here hides anything: this is the safety net for
        // whoever else might have.
        int redeclareEvery = mode == BallMode.DRIBBLING
            ? VISIBLE_REDECLARE_TICKS * 3 : VISIBLE_REDECLARE_TICKS;
        if (renderResyncTicks % redeclareEvery == 0) {
            revealToEveryone();
        }

        if (mode == BallMode.DRIBBLING) {
            tickDribblingOrShotGather();
            return;
        }

        // The flight ended on the previous tick without a basket: tell the session once.
        if (shotFlightWasActive && mode != BallMode.IN_SHOT_FLIGHT) {
            shotFlightWasActive = false;
            if (!scoredCurrentFlight && !missReportedForFlight && shooterId != null && listener != null) {
                missReportedForFlight = true;
                listener.onShotMissed(shooterId);
            }
        }
        shotFlightWasActive = mode == BallMode.IN_SHOT_FLIGHT;

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

        int substeps = PhysicsMath.collisionSubsteps(velocity);
        double dt = 1.0 / substeps;
        double subDrag = Math.pow(airDrag, dt);

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
            // Inside a block the raycast answers with the exit face; snapping there teleports the
            // ball through the wall. Step out along the shortest axis and let the next tick fly.
            org.bukkit.block.Block insideBlock = world == null ? null
                : prevPos.toLocation(world).getBlock();
            org.bukkit.util.BoundingBox insideBox = insideBlock == null
                ? null : insideBlock.getBoundingBox();
            if (insideBox != null && insideBox.getVolume() > 0.0 && insideBox.contains(prevPos)) {
                Vector freed = PhysicsMath.escapeFromBox(
                    new Vector(insideBox.getMinX(), insideBox.getMinY(), insideBox.getMinZ()),
                    new Vector(insideBox.getMaxX(), insideBox.getMaxY(), insideBox.getMaxZ()),
                    prevPos,
                    PhysicsMath.BALL_RADIUS);
                position.copy(freed);
            } else if (world != null && moveLen > 1e-4) {
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
                    Vector safePos = rayHit.getHitPosition().add(normal.clone().multiply(
                        PhysicsMath.BALL_RADIUS + WORLD_CONTACT_SLOP));
                    position.copy(safePos);

                    double vn = velocity.dot(normal);
                    if (vn < 0.0) {
                        double impact = -vn;
                        Vector tangent = PhysicsMath.tangentialComponentAfterFriction(
                            velocity, normal, vn, floorRestitution, PhysicsMath.FLOOR_TANGENTIAL_FRICTION);
                        Vector reflectedNormal = normal.clone().multiply(impact * floorRestitution);
                        if (face == BlockFace.UP && impact < IMPACT_SOUND_THRESHOLD) {
                            reflectedNormal.zero();
                        }
                        velocity.copy(tangent.add(reflectedNormal));
                        if (impact > IMPACT_SOUND_THRESHOLD) {
                            float vol = (float) Math.min(1.0, impact * IMPACT_SOUND_VOLUME_SCALE);
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
            // A tip-off or a reset clears the last touch, and the banner then printed
            // "Out of bounds - someone" while the shot that caused it belonged to a named player.
            // Fall back to whoever last shot or passed before giving up on the name.
            UUID responsible = lastTouchId != null ? lastTouchId
                : (shooterId != null ? shooterId : lastPasserId);
            listener.onOutOfBounds(position.clone(), responsible);
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
        double x = Double.isFinite(geometry.centerX()) ? geometry.centerX() : FALLBACK_ARENA_CENTER_XZ;
        double z = Double.isFinite(geometry.centerZ()) ? geometry.centerZ() : FALLBACK_ARENA_CENTER_XZ;
        double floor = Double.isFinite(geometry.floorY()) ? geometry.floorY() : FALLBACK_FLOOR_Y;
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
                        Vector floorNormal = new Vector(0.0, 1.0, 0.0);
                        Vector tangent = PhysicsMath.tangentialComponentAfterFriction(
                            velocity, floorNormal, velocity.getY(), floorRestitution,
                            PhysicsMath.FLOOR_TANGENTIAL_FRICTION);
                        velocity.copy(tangent).setY(impact > IMPACT_SOUND_THRESHOLD
                            ? impact * floorRestitution : 0.0);
                    }
                }
            }
        }

        if (arena.geometry().isInBounds(position.getX(), position.getZ()) && position.getY() < courtFloorTopY) {
            double impact = -velocity.getY();
            position.setY(courtFloorTopY);
            if (velocity.getY() < 0) {
                Vector floorNormal = new Vector(0.0, 1.0, 0.0);
                Vector tangent = PhysicsMath.tangentialComponentAfterFriction(
                    velocity, floorNormal, velocity.getY(), floorRestitution,
                    PhysicsMath.FLOOR_TANGENTIAL_FRICTION);
                velocity.copy(tangent).setY(impact > IMPACT_SOUND_THRESHOLD
                    ? impact * floorRestitution : 0.0);
                if (impact > IMPACT_SOUND_THRESHOLD) {
                    float vol = (float) Math.min(1.0, impact * IMPACT_SOUND_VOLUME_SCALE);
                    if (world != null) {
                        Msg.playPackSound(position.toLocation(world), "ball.bounce", vol, 0.95f + (float) (Math.random() * 0.12));
                    }
                }
                if (mode == BallMode.IN_SHOT_FLIGHT) {
                    mode = BallMode.LOOSE;
                }
            }
        }
    }

    private static double safeShotDistance(double distanceBlocks) {
        return Double.isFinite(distanceBlocks)
            ? Math.clamp(distanceBlocks, 0.0, MAX_RECORDED_SHOT_DISTANCE)
            : MAX_RECORDED_SHOT_DISTANCE;
    }

    private Vector clampAboveFloorAndBlocks(Vector raw) {
        Vector safe = PhysicsMath.boundedVector(raw);
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
        // Cross-world dribbling must pop loose. A null ball world is the headless harness, not a
        // cross-world move: the invariant it protects is vacuously true there.
        if (pLoc == null || !MovementMath.isFinite(pLoc.toVector())
            || (world != null && (pLoc.getWorld() == null || !pLoc.getWorld().equals(world)))) {
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
        if (crossoverCooldownTicksRemaining > 0) {
            crossoverCooldownTicksRemaining--;
        }
        Location pLoc = holder.getLocation();
        double yawRad = Math.toRadians(pLoc.getYaw());
        Vector forward = new Vector(-Math.sin(yawRad), 0.0, Math.cos(yawRad));

        Vector playerRight = new Vector(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));

        boolean activelyDrawingBow = holder.isHandRaised() && holder.getActiveItem().getType() == org.bukkit.Material.BOW;
        if (holderShotChargeProgress >= 0.0 || activelyDrawingBow) {
            double effectiveCharge = holderShotChargeProgress >= 0.0 ? holderShotChargeProgress : 0.08;

            double gatherBlend = Math.min(1.0, effectiveCharge / 0.28);
            boolean mainRight = holder.getMainHand() == MainHand.RIGHT;
            double handSign = mainRight ? 1.0 : -1.0;

            // The gather used to be hidden from the shooter, because a ball held at the eye reads
            // as a ball stuck in the bow. Hiding it is what made a released shot vanish, so the
            // pose moved instead: the ball is cocked at the shooting hand, shoulder height, off to
            // the side of the crosshair, and drawn slightly smaller while the string is pulled.
            // Visible to the shooter, out of the way of the shot, and never hidden from anybody.
            Vector pocketTarget = holder.getLocation().toVector()
                .add(forward.clone().multiply(0.42))
                .add(playerRight.clone().multiply(0.34 * handSign))
                .add(new Vector(0.0, 1.02 + 0.26 * Math.min(1.0, effectiveCharge), 0.0));

            position.multiply(1.0 - 0.55 * gatherBlend).add(pocketTarget.multiply(0.55 * gatherBlend));
            spinRotation.rotateX(-0.14f);

            if (onFireTrail && dribbleTick % 2 == 0) {
                world.spawnParticle(Particle.FLAME, position.toLocation(world), 2, 0.06, 0.06, 0.06, 0.01);
            }
            float gatherScale = (float) (VISUAL_BALL_SCALE * (1.0 - 0.20 * gatherBlend));
            syncEntities(gatherScale, gatherScale, gatherScale);
            return;
        }

        double targetSide = rightHandDribble ? 1.0 : -1.0;
        handSideLerp += (targetSide - handSideLerp) * 0.34;
        if (crossoverDipTicks > 0.0) {
            crossoverDipTicks = Math.max(0.0, crossoverDipTicks - 1.0);
        }

        boolean sprinting = holder.isSprinting();
        boolean protecting = holder.isSneaking();
        Vector holderVelocity = holder.getVelocity();
        double horizontalSpeed = MovementMath.isFinite(holderVelocity)
            ? Math.hypot(holderVelocity.getX(), holderVelocity.getZ()) : 0.0;
        double cooldownFraction = (double) crossoverCooldownTicksRemaining
            / DribbleCadence.DEFAULT_CROSSOVER_COOLDOWN_TICKS;
        double cycleStep = DribbleCadence.cycleStep(horizontalSpeed, protecting, cooldownFraction);
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
            handHeightOffset -= CROSSOVER_DIP_HEIGHT_BLOCKS * (crossoverDipTicks / CROSSOVER_DIP_DURATION_TICKS);
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
                this.missReportedForFlight = false;
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
        // Every other sound and particle in this class guards on the world; this one did not, so a
        // basket in a headless harness threw before the listener ever heard about it.
        if (world != null) {
            Location rimLoc = rimCenter.toLocation(world);
            Msg.playPackSound(rimLoc, "ball.swish", 1.15f, pureSwish ? 1.06f : 0.96f);
            world.spawnParticle(Particle.CLOUD, rimLoc.clone().subtract(0, 0.35, 0), 10, 0.18, 0.15, 0.18, 0.02);
        }

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
            // Three grains, not one: the trail is what a player follows when the shot is faster
            // than the eye, and it keeps the ball readable even on a client that dropped the
            // display for a frame. A flight is a handful of particles per tick, nothing more.
            world.spawnParticle(
                Particle.DUST,
                position.toLocation(world),
                3, 0.09, 0.09, 0.09,
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
        if (player == null || holderId != null || mode == BallMode.IN_DUNK_FLIGHT
            || !MovementMath.isFinite(position)) {
            return false;
        }
        if (System.currentTimeMillis() < pickupImmunityUntilMillis && player.getUniqueId().equals(lastTouchId)) {
            return false;
        }
        Location pLoc = player.getLocation();
        if (pLoc == null || pLoc.getWorld() == null || !MovementMath.isFinite(pLoc.toVector())
            || world == null || !pLoc.getWorld().equals(world)) {
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

    /**
     * Repairs what is actually broken, and only that.
     *
     * <p>The old repair rebuilt the display and the interaction together whenever *either* was
     * missing. That is a trap: an interaction that cannot be spawned - or that something removes on
     * every tick, or that is spawned inside the floor - kept asking for a display that was then
     * deleted one tick after being created. The ball never survived long enough to be drawn, the
     * physics never noticed, and the basket counted on an invisible ball. The two repairs are now
     * independent, and the display repair is rate-limited, because a strobe is not a fix.
     */
    private void repairRenderIfBroken() {
        if (world == null) {
            // Headless (the test harness builds balls with no world): there is no display to repair
            // and no client to repair it for.
            return;
        }
        String displayFault = displayFault();
        String hitboxFault = hitboxFault();
        if (displayFault == null && hitboxFault == null) {
            return;
        }
        boolean rebuiltDisplay = false;
        if (displayFault != null) {
            if (ticksSinceDisplaySpawn >= REPAIR_MIN_INTERVAL_TICKS) {
                respawnVisualBall();
                rebuiltDisplay = true;
            } else {
                displayRepairsSuppressed++;
            }
        }
        if (hitboxFault != null && ticksSinceHitboxSpawn >= REPAIR_MIN_INTERVAL_TICKS) {
            // Never at the cost of the display: a hitbox is how the ball is picked up, not how it
            // is seen. And on the same clock as everything else, so a hitbox that cannot be spawned
            // is not an exception being built twenty times a second.
            spawnHitbox();
            setHitboxActive(holderId == null && mode != BallMode.IN_DUNK_FLIGHT);
        }
        noteRepair(displayFault, hitboxFault, rebuiltDisplay);
    }

    /** What, exactly, is wrong with the display - or null when nothing is. */
    private String displayFault() {
        if (visualBall == null) {
            return "display missing";
        }
        if (!visualBall.isValid()) {
            return "display invalid";
        }
        org.bukkit.Location ballLoc = ballLocOrNull();
        if (ballLoc == null) {
            return "no world";
        }
        org.bukkit.Location shown = visualBall.getLocation();
        if (shown == null || shown.getWorld() == null || !shown.getWorld().equals(world)) {
            return "display in another world";
        }
        if (!renderDriftedBeyond(ballLoc)) {
            return null;
        }
        return String.format(java.util.Locale.ROOT, "display drifted %.2f blocks",
            Math.sqrt(shown.distanceSquared(ballLoc)));
    }

    private String hitboxFault() {
        if (hitbox == null) {
            return "hitbox missing";
        }
        if (!hitbox.isValid()) {
            return "hitbox invalid";
        }
        return null;
    }

    /**
     * One line per distinct fault, the first time it happens. A repair nobody can name is a repair
     * that will be applied to the wrong thing for three rounds in a row, which is exactly how long
     * an invisible ball survived in this code.
     */
    private void noteRepair(String displayFault, String hitboxFault, boolean rebuiltDisplay) {
        String key = (displayFault == null ? "-" : displayFault) + "|" + (hitboxFault == null ? "-" : hitboxFault);
        if (!repairReasonsLogged.add(key)) {
            return;
        }
        logWarning("[BosketBall] Ball render repair at " + fmtLoc(position)
            + " (mode " + mode + "): display " + (displayFault == null ? "ok" : displayFault)
            + ", " + (hitboxFault == null ? "hitbox ok" : hitboxFault)
            + ". Display rebuilt: " + rebuiltDisplay
            + (rebuiltDisplay ? "" : " (rate-limited, " + displayRepairsSuppressed + " held back)")
            + ".");
    }

    private org.bukkit.Location ballLocOrNull() {
        return world == null ? null : position.toLocation(world);
    }

    /** The display is valid but nowhere near the ball the players are being shown. */
    private boolean renderDriftedBeyond(org.bukkit.Location ballLoc) {
        if (visualBall == null || !visualBall.isValid() || ballLoc == null) {
            return false;
        }
        org.bukkit.Location shown = visualBall.getLocation();
        if (shown == null || shown.getWorld() == null || !shown.getWorld().equals(world)) {
            return true;
        }
        return shown.distanceSquared(ballLoc) > RENDER_DRIFT_BLOCKS_SQ;
    }

    private void syncEntities(float sx, float sy, float sz) {
        if (world == null) {
            return;
        }
        normalizeSpin();
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
