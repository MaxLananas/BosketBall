package fr.bosketball.physics;

import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Live 3D Basketball Entity powered by Minecraft 26.2 ItemDisplay + Interaction Hitbox!
 * Handles:
 *  - Real-time 80Hz sub-stepped flight, torus rim collision, backboard bank-shots & nylon net drag
 *  - Smooth Quaternionf 3D backspin & sidespin interpolation
 *  - Dynamic right/left hand floor dribble & ankle-breaker crossover switch
 *  - 5-style 3D Slam Dunk aerial choreography
 *  - Automatic NBA Jam "ON FIRE" animated skin swap + flame particle trail!
 */
public final class BasketballEntity {

    public enum BallMode {
        FREE,
        DRIBBLING,
        IN_SHOT_FLIGHT,
        IN_PASS_FLIGHT,
        IN_DUNK_FLIGHT
    }

    public interface ScoreListener {
        void onBasketScored(
            TeamSide scoringSide,
            UUID shooterId,
            UUID assisterId,
            boolean threePointer,
            boolean slamDunk,
            boolean pureSwish,
            boolean bankShot,
            double shotDistanceBlocks
        );

        void onOutOfBounds(Vector lastPos, UUID lastTouchPlayerId);

        default void onRimContact() {
        }
    }

    private static final float VISUAL_BALL_SCALE = 0.52f; // Exact 1.85:1 NBA rim-to-ball ratio!
    private static final int SUBSTEPS = 4;

    private final World world;
    private final Arena arena;
    private ItemDisplay itemDisplay;
    private Interaction interaction;

    private Vector position;
    private Vector velocity = new Vector(0, 0, 0);
    private final Quaternionf rotation = new Quaternionf();

    private BallMode mode = BallMode.FREE;
    private BallSkin currentVisualSkin = BallSkin.CLASSIC;
    private boolean onFireVisual = false;

    private UUID holderId;
    private boolean rightHandDribble = true;
    private int dribbleTick = 0;

    private UUID lastShooterId;
    private UUID lastPasserId;
    private long lastPassArrivalMillis;
    private UUID lastTouchId;
    private boolean currentShotThreePointer;
    private double currentShotDistance;
    private Vector shotTargetRim;
    private int rimTouchesThisShot;
    private int backboardTouchesThisShot;
    private int pickupImmunityTicks;
    private int scoreCooldownTicks;

    // Dunk choreography state
    private DunkStyle activeDunkStyle = DunkStyle.TOMAHAWK;
    private Vector dunkStartPos;
    private Vector dunkTargetRim;
    private int dunkTick;
    private int dunkTotalTicks;

    private final double gravity;
    private final double airDrag;
    private final double floorRestitution;
    private final double backboardRestitution;
    private final double rimRestitution;

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
        if (world == null) {
            return;
        }
        Location loc = position.toLocation(world);
        this.itemDisplay = world.spawn(loc, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            display.setItemStack(currentVisualSkin.createBallItem(true));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setBillboard(Display.Billboard.FIXED);
            display.setInterpolationDuration(1);
            display.setTeleportDuration(1);
            display.setShadowRadius(0.32f);
            display.setShadowStrength(0.85f);
            display.setViewRange(2.0f);
            display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE),
                new Quaternionf()
            ));
        });

        this.interaction = world.spawn(loc, Interaction.class, inter -> {
            inter.setPersistent(false);
            inter.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            inter.setInteractionWidth(0.62f);
            inter.setInteractionHeight(0.62f);
            inter.setResponsive(true);
        });
    }

    public boolean isValid() {
        return itemDisplay != null && itemDisplay.isValid();
    }

    public void remove() {
        if (itemDisplay != null) {
            itemDisplay.remove();
            itemDisplay = null;
        }
        if (interaction != null) {
            interaction.remove();
            interaction = null;
        }
    }

    public Interaction interactionEntity() {
        return interaction;
    }

    public Vector position() {
        return position.clone();
    }

    public Vector velocity() {
        return velocity.clone();
    }

    public BallMode mode() {
        return mode;
    }

    public UUID holderId() {
        return holderId;
    }

    public UUID lastTouchId() {
        return lastTouchId;
    }

    public void setSkin(BallSkin skin, boolean onFire) {
        BallSkin effective = onFire ? BallSkin.FIRE : (skin == null ? BallSkin.CLASSIC : skin);
        this.onFireVisual = onFire;
        if (this.currentVisualSkin != effective && itemDisplay != null && itemDisplay.isValid()) {
            this.currentVisualSkin = effective;
            itemDisplay.setItemStack(effective.createBallItem(true));
        }
        if (itemDisplay != null && itemDisplay.isValid()) {
            itemDisplay.setGlowing(onFire);
            if (onFire) {
                itemDisplay.setGlowColorOverride(Color.fromRGB(255, 110, 20));
            }
        }
    }

    public void attachToHolder(Player player, BallSkin preferredSkin, boolean onFire) {
        UUID prevHolder = this.holderId;
        if (mode == BallMode.IN_PASS_FLIGHT && lastPasserId != null && !lastPasserId.equals(player.getUniqueId())) {
            this.lastPassArrivalMillis = System.currentTimeMillis();
        } else if (!player.getUniqueId().equals(prevHolder)) {
            this.lastPasserId = null;
        }
        this.holderId = player.getUniqueId();
        this.lastTouchId = player.getUniqueId();
        this.mode = BallMode.DRIBBLING;
        this.velocity.zero();
        this.dribbleTick = 0;
        setSkin(preferredSkin, onFire);
        Msg.playPackSound(player.getLocation(), "ball.bounce", 0.75f, 1.12f);
    }

    public void triggerCrossoverHandSwitch(Player player) {
        this.rightHandDribble = !this.rightHandDribble;
        Msg.playPackSound(player.getLocation(), "shoe.squeak", 0.9f, 1.15f);
        Msg.playPackSound(player.getLocation(), "ball.bounce", 0.95f, 1.25f);
        world.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.15, 0), 10, 0.25, 0.05, 0.25, 0.04);
    }

    public void launchShot(
        Player shooter,
        Vector targetRim,
        Vector launchVelocity,
        boolean threePointer,
        double shotDistance
    ) {
        // Check if shooter received an assist pass within the last 4.5 seconds!
        UUID creditedAssister = (lastPasserId != null
            && !lastPasserId.equals(shooter.getUniqueId())
            && (System.currentTimeMillis() - lastPassArrivalMillis) <= 4500L) ? lastPasserId : null;

        this.holderId = null;
        this.lastShooterId = shooter.getUniqueId();
        this.lastPasserId = creditedAssister;
        this.lastTouchId = shooter.getUniqueId();
        this.currentShotThreePointer = threePointer;
        this.currentShotDistance = shotDistance;
        this.shotTargetRim = targetRim.clone();
        this.rimTouchesThisShot = 0;
        this.backboardTouchesThisShot = 0;
        this.pickupImmunityTicks = 10;

        this.position = shooter.getEyeLocation().toVector().add(shooter.getLocation().getDirection().multiply(0.45));
        this.velocity = launchVelocity.clone();
        this.mode = BallMode.IN_SHOT_FLIGHT;
    }

    public void launchPass(Player passer, Vector start, Vector passVelocity, boolean alleyOopLob) {
        this.holderId = null;
        this.lastPasserId = passer.getUniqueId();
        this.lastTouchId = passer.getUniqueId();
        this.pickupImmunityTicks = 6;
        this.position = start.clone();
        this.velocity = passVelocity.clone();
        this.mode = BallMode.IN_PASS_FLIGHT;
        Msg.playPackSound(passer.getLocation(), "ball.bounce", 0.8f, alleyOopLob ? 1.3f : 1.05f);
    }

    public void startSlamDunk(Player dunker, Vector targetRim, DunkStyle style, int durationTicks) {
        UUID creditedAssister = (lastPasserId != null
            && !lastPasserId.equals(dunker.getUniqueId())
            && (System.currentTimeMillis() - lastPassArrivalMillis) <= 4500L) ? lastPasserId : null;

        this.holderId = dunker.getUniqueId();
        this.lastShooterId = dunker.getUniqueId();
        this.lastPasserId = creditedAssister;
        this.lastTouchId = dunker.getUniqueId();
        this.activeDunkStyle = style == null ? DunkStyle.TOMAHAWK : style;
        this.dunkStartPos = dunker.getLocation().toVector();
        this.dunkTargetRim = targetRim.clone();
        this.dunkTick = 0;
        this.dunkTotalTicks = Math.max(10, durationTicks);
        this.mode = BallMode.IN_DUNK_FLIGHT;
        Msg.playPackSound(dunker.getLocation(), "shoe.squeak", 1.0f, 0.92f);
    }

    public void resetToCenterTipOff(Vector centerFloor, double upwardTipSpeed) {
        this.holderId = null;
        this.lastShooterId = null;
        this.lastPasserId = null;
        this.mode = BallMode.FREE;
        this.pickupImmunityTicks = 12;
        this.position = centerFloor.clone().add(new Vector(0, 1.35, 0));
        this.velocity = new Vector(0, upwardTipSpeed, 0);
        syncVisuals();
    }

    public void popLoose(Vector impulse, UUID forcedByPlayerId) {
        this.holderId = null;
        this.lastTouchId = forcedByPlayerId;
        this.mode = BallMode.FREE;
        this.pickupImmunityTicks = 8;
        this.velocity = impulse.clone();
    }

    /**
     * Advances 1 server tick (with 4 internal sub-steps = 80Hz physics!).
     */
    public void tick(ScoreListener listener) {
        if (!isValid()) {
            spawnEntities();
            if (!isValid()) {
                return;
            }
        }
        if (pickupImmunityTicks > 0) {
            pickupImmunityTicks--;
        }
        if (scoreCooldownTicks > 0) {
            scoreCooldownTicks--;
        }

        if (mode == BallMode.DRIBBLING) {
            tickDribbling();
            syncVisuals();
            return;
        }

        if (mode == BallMode.IN_DUNK_FLIGHT) {
            tickDunkFlight(listener);
            syncVisuals();
            return;
        }

        // Free / Shot / Pass 80Hz sub-stepped simulation
        double dt = 1.0 / SUBSTEPS;
        double subDrag = Math.pow(airDrag, dt);
        double floorY = arena.geometry().floorY() + PhysicsMath.BALL_RADIUS;

        Vector northRim = arena.geometry().northHoopRim();
        Vector southRim = arena.geometry().southHoopRim();

        for (int step = 0; step < SUBSTEPS; step++) {
            Vector prevPos = position.clone();

            velocity.setY(velocity.getY() - gravity * dt);
            velocity.multiply(subDrag);
            position.add(velocity.clone().multiply(dt));

            // Floor bounce
            if (position.getY() <= floorY) {
                position.setY(floorY);
                if (velocity.getY() < -0.04) {
                    double impact = Math.min(1.0, Math.abs(velocity.getY()) * 2.2);
                    velocity.setY(-velocity.getY() * floorRestitution);
                    velocity.setX(velocity.getX() * 0.90);
                    velocity.setZ(velocity.getZ() * 0.90);
                    Msg.playPackSound(position.toLocation(world), "ball.bounce", (float) (0.25 + 0.75 * impact), 0.96f + (float) (impact * 0.14));
                } else {
                    velocity.setY(0.0);
                    velocity.setX(velocity.getX() * 0.85);
                    velocity.setZ(velocity.getZ() * 0.85);
                }
                if (mode == BallMode.IN_SHOT_FLIGHT || mode == BallMode.IN_PASS_FLIGHT) {
                    mode = BallMode.FREE;
                }
            }

            // Collide with both North (-Z, faces +Z => sign +1) and South (+Z, faces -Z => sign -1) Hoops!
            handleHoopCollisionsAndScoring(prevPos, northRim, +1, TeamSide.FROST, arena.northHoopDisplay(), listener);
            handleHoopCollisionsAndScoring(prevPos, southRim, -1, TeamSide.IGNIS, arena.southHoopDisplay(), listener);
        }

        // Update realistic 3D rotation quaternion from velocity (backspin in flight, rolling spin near ground!)
        double speed = velocity.length();
        if (speed > 0.008) {
            Vector horiz = new Vector(velocity.getX(), 0, velocity.getZ());
            if (horiz.lengthSquared() > 1e-5) {
                Vector spinAxis = new Vector(-horiz.getZ(), 0, horiz.getX()).normalize();
                float spinAngle = (mode == BallMode.IN_SHOT_FLIGHT)
                    ? -0.26f // Crisp NBA shooter's backspin!
                    : (float) (speed / PhysicsMath.BALL_RADIUS * 0.55);
                Quaternionf deltaRot = new Quaternionf().rotateAxis(spinAngle, (float) spinAxis.getX(), 0f, (float) spinAxis.getZ());
                deltaRot.mul(rotation, rotation).normalize();
            }
        }

        spawnFlightParticles();

        // Out-of-bounds check when ball touches floor outside sidelines/baselines
        if (listener != null && position.getY() <= floorY + 0.25 && !arena.geometry().isInBounds(position.getX(), position.getZ())) {
            listener.onOutOfBounds(position.clone(), lastTouchId);
        }

        syncVisuals();
    }

    public boolean canBePickedUpBy(Player player) {
        if (mode == BallMode.DRIBBLING || mode == BallMode.IN_DUNK_FLIGHT) {
            return false;
        }
        if (pickupImmunityTicks > 0 && player.getUniqueId().equals(lastTouchId)) {
            return false;
        }
        // Goaltending & cylinder protection: never auto-catch an untouched live shot above 2.15m!
        if (mode == BallMode.IN_SHOT_FLIGHT
            && rimTouchesThisShot == 0
            && backboardTouchesThisShot == 0
            && position.getY() > arena.geometry().floorY() + 2.15) {
            return false;
        }
        Location eye = player.getLocation().add(0, 1.0, 0);
        return eye.toVector().distanceSquared(position) <= 1.55 * 1.55;
    }

    private void handleHoopCollisionsAndScoring(
        Vector prevPos,
        Vector rimCenter,
        int normalSignZ,
        TeamSide scoringSideForThisHoop,
        HoopDisplaySet hoopVisuals,
        ScoreListener listener
    ) {
        if (position.distanceSquared(rimCenter) > 3.5 * 3.5) {
            return;
        }

        PhysicsMath.CollisionResult glass = PhysicsMath.collideWithBackboard(
            position, velocity, rimCenter, normalSignZ, backboardRestitution
        );
        if (glass.hit()) {
            position = glass.position();
            velocity = glass.velocity();
            backboardTouchesThisShot++;
            Msg.playPackSound(rimCenter.toLocation(world), "ball.backboard", 0.85f, 1.0f);
            world.spawnParticle(Particle.CRIT, position.toLocation(world), 5, 0.08, 0.08, 0.08, 0.02);
        }

        PhysicsMath.CollisionResult rim = PhysicsMath.collideWithRimTorus(
            position, velocity, rimCenter, rimRestitution
        );
        if (rim.hit()) {
            position = rim.position();
            velocity = rim.velocity();
            rimTouchesThisShot++;
            if (listener != null) {
                listener.onRimContact();
            }
            Msg.playPackSound(rimCenter.toLocation(world), "ball.rim_hit", 0.90f, 0.95f + (rimTouchesThisShot * 0.04f));
            world.spawnParticle(Particle.ELECTRIC_SPARK, position.toLocation(world), 6, 0.08, 0.05, 0.08, 0.03);
        }

        // Check downward cylinder score (with cooldown so rim-rattle never double-scores)!
        if (scoreCooldownTicks == 0 && PhysicsMath.didScoreDownwardThroughHoop(prevPos, position, rimCenter)) {
            scoreCooldownTicks = 25;
            // Nylon net drag slows horizontal speed cleanly so the ball drops straight down through the twine!
            velocity.setX(velocity.getX() * 0.22);
            velocity.setZ(velocity.getZ() * 0.22);
            velocity.setY(Math.max(-0.18, velocity.getY() * 0.52));

            boolean pureSwish = (rimTouchesThisShot == 0 && backboardTouchesThisShot == 0);
            boolean bankShot = (backboardTouchesThisShot > 0 && rimTouchesThisShot == 0);

            Msg.playPackSound(rimCenter.toLocation(world), "ball.swish", 1.15f, pureSwish ? 1.08f : 0.98f);
            if (hoopVisuals != null) {
                hoopVisuals.triggerSwishAnimation();
            }

            if (listener != null && mode != BallMode.DRIBBLING) {
                UUID scorer = lastShooterId != null ? lastShooterId : lastTouchId;
                mode = BallMode.FREE;
                pickupImmunityTicks = 25;
                listener.onBasketScored(
                    scoringSideForThisHoop,
                    scorer,
                    lastPasserId,
                    currentShotThreePointer,
                    false,
                    pureSwish,
                    bankShot,
                    currentShotDistance
                );
            }
        }
    }

    private void tickDribbling() {
        Player holder = holderId == null ? null : Bukkit.getPlayer(holderId);
        if (holder == null || !holder.isOnline()) {
            holderId = null;
            mode = BallMode.FREE;
            return;
        }
        dribbleTick++;
        Location pLoc = holder.getLocation();
        double yawRad = Math.toRadians(pLoc.getYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);

        boolean sprinting = holder.isSprinting();
        double sideSign = rightHandDribble ? -0.42 : 0.42; // Player's right/left hand
        double forwardDist = sprinting ? 0.62 : 0.48;

        double cycleSpeed = sprinting ? 0.58 : 0.40;
        double wave = Math.abs(Math.sin(dribbleTick * cycleSpeed));
        double floorBallY = arena.geometry().floorY() + PhysicsMath.BALL_RADIUS;
        double handY = pLoc.getY() + (holder.isSneaking() ? 0.68 : 0.98);
        double ballY = floorBallY + (handY - floorBallY) * wave;

        // Play crisp floor bounce sound at bottom of dribble cycle!
        double prevWave = Math.abs(Math.sin((dribbleTick - 1) * cycleSpeed));
        if (wave < 0.18 && prevWave >= 0.18) {
            Msg.playPackSound(pLoc, "ball.bounce", sprinting ? 0.65f : 0.48f, sprinting ? 1.12f : 1.0f);
            if (onFireVisual) {
                world.spawnParticle(Particle.FLAME, position.toLocation(world), 4, 0.08, 0.02, 0.08, 0.02);
            }
        }

        position = new Vector(
            pLoc.getX() + fx * forwardDist + rx * sideSign,
            ballY,
            pLoc.getZ() + fz * forwardDist + rz * sideSign
        );
        rotation.rotateX(0.18f).normalize();
    }

    private void tickDunkFlight(ScoreListener listener) {
        Player dunker = holderId == null ? null : Bukkit.getPlayer(holderId);
        if (dunker == null || !dunker.isOnline() || dunkTargetRim == null) {
            holderId = null;
            mode = BallMode.FREE;
            return;
        }
        dunkTick++;
        double t = Math.min(1.0, (double) dunkTick / dunkTotalTicks);

        // Parabolic aerial trajectory guiding player smoothly right to the front of the rim!
        Vector horizToRim = dunkTargetRim.clone().subtract(dunkStartPos).setY(0);
        Vector dunkEndBody = dunkTargetRim.clone().subtract(
            horizToRim.lengthSquared() > 1e-4 ? horizToRim.clone().normalize().multiply(0.55) : new Vector(0, 0, 0)
        );
        dunkEndBody.setY(dunkTargetRim.getY() - 1.35);

        double arcLift = Math.sin(t * Math.PI) * (activeDunkStyle == DunkStyle.METEOR_JAM ? 2.15 : 1.45);
        Vector desiredBody = dunkStartPos.clone().multiply(1.0 - t).add(dunkEndBody.clone().multiply(t));
        desiredBody.setY(desiredBody.getY() + arcLift);

        Vector stepVel = desiredBody.clone().subtract(dunker.getLocation().toVector());
        dunker.setVelocity(stepVel.multiply(0.72));
        dunker.setFallDistance(0f);

        double yawRad = Math.toRadians(dunker.getLocation().getYaw());
        Vector ballOffset = activeDunkStyle.computeBallOffset(t, yawRad);
        this.position = dunker.getLocation().toVector().add(ballOffset);
        rotation.rotateX(0.42f).rotateY(0.18f).normalize();

        // Signature Dunk Aerial Trail!
        Location bLoc = position.toLocation(world);
        switch (activeDunkStyle) {
            case METEOR_JAM -> {
                world.spawnParticle(Particle.FLAME, bLoc, 6, 0.12, 0.12, 0.12, 0.02);
                world.spawnParticle(Particle.LAVA, bLoc, 2, 0.05, 0.05, 0.05, 0.0);
            }
            case SPIN_360 -> world.spawnParticle(Particle.SOUL_FIRE_FLAME, bLoc, 5, 0.12, 0.12, 0.12, 0.01);
            case WINDMILL -> world.spawnParticle(Particle.END_ROD, bLoc, 4, 0.08, 0.08, 0.08, 0.01);
            default -> world.spawnParticle(Particle.DUST, bLoc, 5, 0.1, 0.1, 0.1, new Particle.DustOptions(Color.fromRGB(255, 145, 35), 1.3f));
        }

        if (dunkTick >= dunkTotalTicks) {
            // SLAM IMPACT AT THE RIM!
            this.holderId = null;
            this.mode = BallMode.FREE;
            this.position = dunkTargetRim.clone().add(new Vector(0, -0.22, 0));
            this.velocity = new Vector(0, -0.34, 0);
            this.pickupImmunityTicks = 28;

            Location rimLoc = dunkTargetRim.toLocation(world);
            Msg.playPackSound(rimLoc, "game.dunk_boom", 1.35f, 0.96f);
            Msg.playPackSound(rimLoc, "ball.swish", 1.10f, 0.92f);
            world.spawnParticle(Particle.EXPLOSION, rimLoc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLAME, rimLoc, 28, 0.35, 0.25, 0.35, 0.08);
            world.spawnParticle(Particle.ELECTRIC_SPARK, rimLoc, 24, 0.40, 0.25, 0.40, 0.12);

            boolean isSouth = dunkTargetRim.distanceSquared(arena.geometry().southHoopRim()) < 2.0;
            HoopDisplaySet targetHoop = isSouth ? arena.southHoopDisplay() : arena.northHoopDisplay();
            if (targetHoop != null) {
                targetHoop.triggerDunkFlexAnimation();
            }

            // Posterizer knockback wave on nearby defenders under the rim!
            for (Player nearby : world.getPlayers()) {
                if (!nearby.getUniqueId().equals(dunker.getUniqueId())
                    && nearby.getLocation().toVector().distanceSquared(dunkTargetRim) <= 3.6 * 3.6) {
                    Vector push = nearby.getLocation().toVector().subtract(dunkTargetRim).setY(0);
                    if (push.lengthSquared() < 1e-4) {
                        push = new Vector(0.4, 0, 0.4);
                    } else {
                        push.normalize().multiply(0.85);
                    }
                    push.setY(0.36);
                    nearby.setVelocity(push);
                }
            }

            if (listener != null) {
                TeamSide scoringSide = isSouth ? TeamSide.IGNIS : TeamSide.FROST;
                listener.onBasketScored(
                    scoringSide,
                    dunker.getUniqueId(),
                    lastPasserId,
                    false,
                    true,
                    true,
                    false,
                    dunkStartPos.distance(dunkTargetRim)
                );
            }
        }
    }

    private void spawnFlightParticles() {
        if (mode != BallMode.IN_SHOT_FLIGHT && mode != BallMode.IN_PASS_FLIGHT) {
            return;
        }
        Location loc = position.toLocation(world);
        if (onFireVisual) {
            world.spawnParticle(Particle.FLAME, loc, 3, 0.06, 0.06, 0.06, 0.01);
            world.spawnParticle(Particle.SMOKE, loc, 2, 0.05, 0.05, 0.05, 0.005);
        } else if (currentShotThreePointer && mode == BallMode.IN_SHOT_FLIGHT) {
            world.spawnParticle(
                Particle.DUST,
                loc,
                2,
                0.04, 0.04, 0.04,
                new Particle.DustOptions(Color.fromRGB(80, 235, 255), 1.0f)
            );
        } else {
            world.spawnParticle(
                Particle.DUST,
                loc,
                1,
                0.02, 0.02, 0.02,
                new Particle.DustOptions(Color.fromRGB(255, 165, 65), 0.85f)
            );
        }
    }

    private void syncVisuals() {
        Location loc = position.toLocation(world);
        if (itemDisplay != null && itemDisplay.isValid()) {
            itemDisplay.teleport(loc);
            itemDisplay.setInterpolationDelay(0);
            itemDisplay.setInterpolationDuration(1);
            itemDisplay.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(rotation),
                new Vector3f(VISUAL_BALL_SCALE, VISUAL_BALL_SCALE, VISUAL_BALL_SCALE),
                new Quaternionf()
            ));
        }
        if (interaction != null && interaction.isValid()) {
            interaction.teleport(loc.clone().subtract(0, 0.30, 0));
        }
    }
}
