package fr.bosketball.physics;

import fr.bosketball.model.ShotReleaseQuality;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.util.Vector;

/** Pure, finite-safe math shared by live ball physics and headless tests. */
public final class PhysicsMath {

    /** Visual basketball radius in block/metre-equivalent court units. */
    public static final double BALL_RADIUS = 0.285;

    /** Regulation hoop-ring radius represented in court units. */
    public static final double RIM_RADIUS = 0.45;
    public static final double RIM_MAJOR_RADIUS = RIM_RADIUS;

    /** Tube radius of the rendered/collidable hoop ring. */
    public static final double RIM_TUBE_RADIUS = 0.042;

    /** Scoring aperture centre radius, leaving a small margin inside the physical rim. */
    public static final double SCORING_CYLINDER_RADIUS = 0.39;

    /** Rim normal speed response; midpoint proxy from FIBA ring-support energy absorption. */
    public static final double RIM_RESTITUTION = 0.76;

    /** Backboard normal speed response; representative of measured 0.80–0.90 ratios. */
    public static final double BACKBOARD_RESTITUTION = 0.84;

    /** Court-floor normal speed response; within measured maple and FIBA regulation ranges. */
    public static final double FLOOR_RESTITUTION = 0.83;

    /** Rim slip friction, guided by measured ball/rim contact values around 0.5. */
    public static final double RIM_TANGENTIAL_FRICTION = 0.50;

    /** Backboard slip friction, guided by measured ball/tempered-glass contact values around 0.6. */
    public static final double BACKBOARD_TANGENTIAL_FRICTION = 0.60;

    /** Floor tangential friction used to prevent a bounced ball from skating indefinitely. */
    public static final double FLOOR_TANGENTIAL_FRICTION = 0.55;

    /** Gravity matches the shipped basketball arc tuning in blocks per tick squared. */
    public static final double DEFAULT_GRAVITY = 0.046;

    /** Air drag matches the shipped basketball arc tuning per server tick. */
    public static final double DEFAULT_AIR_DRAG = 0.991;

    /** Maximum displacement of one live collision substep; 40 m/s is two blocks per tick. */
    public static final double MAX_COLLISION_SUBSTEP_BLOCKS = 0.20;

    /** Hoop backboard plane offset from the rim centre along its forward axis. */
    public static final double BACKBOARD_OFFSET = 0.650;

    /** Half-width of the playable backboard collision area. */
    public static final double BACKBOARD_HALF_WIDTH = 0.92;

    /** Backboard collision vertical bounds relative to the rim. */
    public static final double BACKBOARD_MIN_Y_REL = -0.15;
    public static final double BACKBOARD_MAX_Y_REL = 1.15;

    /** Maximum horizontal aim error still considered an intentional hoop attempt. */
    public static final double MAX_HOOP_AIM_YAW_DEG = 22.0;

    // Low-speed integration retains four intervals; the cap bounds work on corrupted/high-speed states.
    private static final int MIN_COLLISION_SUBSTEPS = 4;
    private static final int MAX_COLLISION_SUBSTEPS = 64;

    // Preview and exact-arc integration use the same four samples per server tick for matching paths.
    private static final int PREVIEW_STEPS_PER_TICK = 4;
    private static final int MAX_PREVIEW_TICKS = 300;
    private static final int MAX_ARC_TICKS = 48;
    private static final int MIN_ARC_TICKS = 15;

    // Numeric guards keep finite-but-absurd vectors from overflowing intermediate Bukkit vector math.
    private static final double MAX_VECTOR_COMPONENT = 10_000.0;
    private static final double MIN_VECTOR_LENGTH = 1.0e-8;
    private static final double AIM_HORIZONTAL_EPSILON_SQUARED = 1.0e-5;

    // Aim calibration preserves the existing close-range forgiveness and court-valid shot envelope.
    private static final double AIM_CLOSE_RANGE_BLOCKS = 0.35;
    private static final double INVALID_AIM_ERROR_DEGREES = 180.0;
    private static final double VERTICAL_LOOK_ERROR_DEGREES = 90.0;
    private static final double MAX_AIM_PITCH_ERROR_DEGREES = 90.0;
    private static final double MAX_DISTANCE_BLOCKS = 40.0;
    private static final double MAX_AIM_RANGE_BLOCKS = 34.0;
    private static final double HORIZONTAL_SHOT_MIN_BLOCKS = 0.5;
    private static final double PITCH_AIM_WEIGHT = 0.65;
    private static final double IDEAL_PITCH_BASE_DEG = 9.5;
    private static final double IDEAL_PITCH_PER_BLOCK = 1.35;
    private static final double MAX_IDEAL_PITCH_ABS_DEG = 34.0;
    private static final double MIN_VALID_PITCH_DEG = -68.0;
    private static final double MAX_VALID_PITCH_DEG = 22.0;
    private static final double STANDARD_SHOT_MAX_FLIGHT_TICKS = 44.0;

    // Charge conversion stays at twenty game ticks per second and retains the old tap/full bounds.
    private static final double BOW_PULL_TICKS_PER_SECOND = 20.0;
    private static final double BOW_PULL_MINIMUM = 0.08;
    private static final double BOW_PULL_MAXIMUM = 1.0;

    // The shot speeds and forgiveness values retain the established throw feel while the model owns accuracy.
    private static final double MIN_SKILL_SHOT_CHARGE = 0.15;
    private static final double MAX_SKILL_SHOT_CHARGE = 1.20;
    private static final double OFF_AIM_THROW_BASE_SPEED = 0.56;
    private static final double OFF_AIM_THROW_CHARGE_SPEED = 0.92;
    private static final double OFF_AIM_THROW_LIFT = 0.11;
    private static final double OPEN_SHOT_CONTEST_THRESHOLD = 0.30;
    private static final double OPEN_GREEN_AIM_FORGIVENESS = 0.56;
    private static final double OTHER_AIM_FORGIVENESS = 0.84;
    private static final double MAX_AIM_LATERAL_OFFSET = 2.6;
    private static final double TIMING_LATERAL_SCATTER_RATIO = 0.45;
    private static final double TIMING_DEPTH_SCATTER_RATIO = 0.30;
    private static final double AIM_PITCH_DEPTH_THRESHOLD_DEG = 12.0;
    private static final double AIM_PITCH_DEPTH_PER_DEGREE = 0.028;
    private static final double AIM_PITCH_DEPTH_SIGN = -1.0;
    private static final double AIMED_TARGET_HEIGHT_OFFSET = 0.06;
    private static final double BALLISTIC_TARGET_HEIGHT_OFFSET = 0.06;
    private static final double SHOT_FLIGHT_BASE_TICKS = 13.5;
    private static final double SHOT_FLIGHT_TICKS_PER_BLOCK = 1.48;
    private static final double LOW_ARC_PITCH_THRESHOLD_DEG = -8.0;
    private static final double MAX_PITCH_FLIGHT_EXTENSION_TICKS = 6.0;
    private static final double PITCH_FLIGHT_EXTENSION_PER_DEGREE = 0.25;

    // Standing eye/rim geometry distinguishes an airborne jumper without inspecting server-side player state.
    private static final double SHOT_START_FORWARD_OFFSET = 0.45;
    private static final double REGULATION_RIM_HEIGHT = 3.05;
    private static final double STANDING_EYE_HEIGHT = 1.62;
    private static final double AIRBORNE_EYE_HEIGHT_THRESHOLD = 0.12;

    // Ballistic scatter and contact tolerances are tuned to avoid exact-edge numerical pinning.
    private static final double BALLISTIC_SCATTER_MULTIPLIER = 3.8;
    private static final double BALLISTIC_MIN_SCATTER_RATIO = 0.38;
    private static final double RIM_AXIS_EPSILON = 1.0e-5;
    private static final double RIM_NORMAL_EPSILON = 1.0e-8;
    private static final double RIM_CONTACT_SLOP = 0.002;
    private static final double RIM_APPROACH_EPSILON = 0.015;
    private static final double MIN_RIM_ESCAPE_SPEED = 0.025;
    private static final double BACKBOARD_CONTACT_DEPTH = 0.42;
    private static final double BACKBOARD_CONTACT_SLOP = 0.002;
    private static final double NORMAL_SPEED_EPSILON = 1.0e-9;

    // Configured gravity and drag are bounded to keep all contact and trajectory calculations finite.
    private static final double MIN_GRAVITY = 0.0;
    private static final double MAX_GRAVITY = 0.20;
    private static final double MIN_AIR_DRAG = 0.50;
    private static final double MAX_AIR_DRAG = 1.0;

    private PhysicsMath() {
    }

    /** Collision output defensively owns finite vector snapshots. */
    public record CollisionResult(boolean hit, Vector position, Vector velocity, double impactSpeed) {
        public CollisionResult {
            position = finiteVectorOrZero(position);
            velocity = finiteVectorOrZero(velocity);
            impactSpeed = Double.isFinite(impactSpeed) ? Math.max(0.0, impactSpeed) : 0.0;
        }

        @Override
        public Vector position() {
            return position.clone();
        }

        @Override
        public Vector velocity() {
            return velocity.clone();
        }
    }

    /** Finite aim metrics; a non-finite direct construction is marked as not aimed at the hoop. */
    public record AimEvaluation(
        boolean aimingAtHoop,
        double yawErrorDeg,
        double pitchErrorDeg,
        double horizontalDist
    ) {
        public AimEvaluation {
            boolean valid = Double.isFinite(yawErrorDeg)
                && Double.isFinite(pitchErrorDeg)
                && Double.isFinite(horizontalDist)
                && horizontalDist >= 0.0;
            if (!valid) {
                aimingAtHoop = false;
                yawErrorDeg = INVALID_AIM_ERROR_DEGREES;
                pitchErrorDeg = INVALID_AIM_ERROR_DEGREES;
                horizontalDist = MAX_DISTANCE_BLOCKS;
            } else {
                yawErrorDeg = Math.clamp(yawErrorDeg, -INVALID_AIM_ERROR_DEGREES, INVALID_AIM_ERROR_DEGREES);
                pitchErrorDeg = Math.clamp(pitchErrorDeg, -INVALID_AIM_ERROR_DEGREES, INVALID_AIM_ERROR_DEGREES);
                horizontalDist = Math.clamp(horizontalDist, 0.0, MAX_DISTANCE_BLOCKS);
                aimingAtHoop = aimingAtHoop
                    && Math.abs(yawErrorDeg) <= MAX_HOOP_AIM_YAW_DEG
                    && Math.abs(pitchErrorDeg) <= MAX_AIM_PITCH_ERROR_DEGREES
                    && horizontalDist <= MAX_AIM_RANGE_BLOCKS;
            }
        }
    }

    /** Counts enough bounded movement intervals for a block/tick velocity. */
    public static int collisionSubsteps(Vector velocity) {
        if (!isFinite(velocity)) {
            return MAX_COLLISION_SUBSTEPS;
        }
        double speed = robustLength(velocity);
        if (!Double.isFinite(speed)) {
            return MAX_COLLISION_SUBSTEPS;
        }
        double requested = Math.ceil(speed / MAX_COLLISION_SUBSTEP_BLOCKS);
        if (!Double.isFinite(requested) || requested >= MAX_COLLISION_SUBSTEPS) {
            return MAX_COLLISION_SUBSTEPS;
        }
        return Math.max(MIN_COLLISION_SUBSTEPS, (int) requested);
    }

    public static AimEvaluation evaluateShooterAim(Vector eyePos, Vector lookDir, Vector targetRim) {
        if (!isFinite(eyePos) || !isFinite(lookDir) || !isFinite(targetRim)) {
            return invalidAim();
        }
        Vector eye = boundedVector(eyePos);
        Vector rim = boundedVector(targetRim);
        Vector direction = normalizedOr(lookDir, new Vector(0.0, 0.0, 1.0));

        double dx = rim.getX() - eye.getX();
        double dz = rim.getZ() - eye.getZ();
        double rawHorizontalDistance = Math.hypot(dx, dz);
        if (!Double.isFinite(rawHorizontalDistance)) {
            return invalidAim();
        }
        double horizontalDistance = Math.min(MAX_DISTANCE_BLOCKS, rawHorizontalDistance);
        if (rawHorizontalDistance < AIM_CLOSE_RANGE_BLOCKS) {
            return new AimEvaluation(true, 0.0, 0.0, horizontalDistance);
        }

        Vector toRimHorizontal = new Vector(dx / rawHorizontalDistance, 0.0, dz / rawHorizontalDistance);
        Vector lookHorizontal = new Vector(direction.getX(), 0.0, direction.getZ());
        if (lookHorizontal.lengthSquared() < AIM_HORIZONTAL_EPSILON_SQUARED) {
            return new AimEvaluation(false, VERTICAL_LOOK_ERROR_DEGREES,
                -VERTICAL_LOOK_ERROR_DEGREES, horizontalDistance);
        }
        lookHorizontal = normalizedOr(lookHorizontal, new Vector(0.0, 0.0, 1.0));

        Vector shooterRight = new Vector(-toRimHorizontal.getZ(), 0.0, toRimHorizontal.getX());
        double sinYaw = lookHorizontal.dot(shooterRight);
        double cosYaw = Math.clamp(lookHorizontal.dot(toRimHorizontal), -1.0, 1.0);
        double yawErrorDeg = Math.toDegrees(Math.atan2(sinYaw, cosYaw));

        double pitchDeg = Math.toDegrees(-Math.asin(Math.clamp(direction.getY(), -1.0, 1.0)));
        double idealPitchDeg = -Math.min(MAX_IDEAL_PITCH_ABS_DEG,
            IDEAL_PITCH_BASE_DEG + horizontalDistance * IDEAL_PITCH_PER_BLOCK);
        double pitchErrorDeg = pitchDeg - idealPitchDeg;

        boolean validYaw = Math.abs(yawErrorDeg) <= MAX_HOOP_AIM_YAW_DEG;
        boolean validPitch = pitchDeg <= MAX_VALID_PITCH_DEG && pitchDeg >= MIN_VALID_PITCH_DEG;
        boolean validRange = rawHorizontalDistance <= MAX_AIM_RANGE_BLOCKS;
        return new AimEvaluation(validYaw && validPitch && validRange, yawErrorDeg, pitchErrorDeg, horizontalDistance);
    }

    public static double computeBowPullProgress(int ticksUsed) {
        if (ticksUsed <= 0) {
            return BOW_PULL_MINIMUM;
        }
        double fraction = ticksUsed / BOW_PULL_TICKS_PER_SECOND;
        double pull = (fraction * fraction + fraction * 2.0) / 3.0;
        return Math.clamp(pull, BOW_PULL_MINIMUM, BOW_PULL_MAXIMUM);
    }

    /**
     * Compatibility entry point for the match-session shot path. That path has already folded
     * session fatigue into charge and contest, so this adapter does not apply it a second time.
     */
    public static Vector solveSkillShotVelocity(
        Vector start,
        Vector lookDir,
        Vector rimCenter,
        Vector hoopForward,
        AimEvaluation aim,
        double chargeProgress,
        double gravity,
        double dragPerTick,
        ShotReleaseQuality quality,
        double contestRatio,
        Random rng
    ) {
        boolean airborne = inferAirborne(start, lookDir, rimCenter);
        return solveSkillShotVelocity(start, lookDir, rimCenter, hoopForward, aim, chargeProgress,
            gravity, dragPerTick, quality, contestRatio, 0.0, airborne, rng);
    }

    /** Explicit-factor overload for callers that own an unadjusted fatigue value. */
    public static Vector solveSkillShotVelocity(
        Vector start,
        Vector lookDir,
        Vector rimCenter,
        Vector hoopForward,
        AimEvaluation aim,
        double chargeProgress,
        double gravity,
        double dragPerTick,
        ShotReleaseQuality quality,
        double contestRatio,
        double fatigue,
        boolean airborne,
        Random rng
    ) {
        if (!isFinite(start) || !isFinite(rimCenter)) {
            return new Vector(0.0, 0.0, 0.0);
        }
        Vector safeStart = boundedVector(start);
        Vector safeRim = boundedVector(rimCenter);
        Vector direction = normalizedOr(lookDir, new Vector(0.0, 0.0, 1.0));
        Vector forward = normalizedHorizontalOr(hoopForward, new Vector(0.0, 0.0, 1.0));
        AimEvaluation safeAim = aim == null ? invalidAim() : aim;
        ShotReleaseQuality safeQuality = quality == null ? ShotReleaseQuality.VERY_EARLY : quality;
        double safeContest = finiteClamp(contestRatio, 0.0, 1.0, 1.0);
        double safeFatigue = finiteClamp(fatigue, 0.0, 1.0, 1.0);
        double safeGravity = sanitizeGravity(gravity);
        double safeDrag = sanitizeAirDrag(dragPerTick);
        double safeCharge = finiteClamp(chargeProgress, 0.0, MAX_SKILL_SHOT_CHARGE, 0.0);

        double dx = safeRim.getX() - safeStart.getX();
        double dz = safeRim.getZ() - safeStart.getZ();
        double rawDistance = Math.max(HORIZONTAL_SHOT_MIN_BLOCKS, Math.hypot(dx, dz));
        double distance = Math.min(MAX_DISTANCE_BLOCKS, rawDistance);
        double aimError = Math.hypot(safeAim.yawErrorDeg(), safeAim.pitchErrorDeg() * PITCH_AIM_WEIGHT);
        ShotModel.Input modelInput = new ShotModel.Input(distance, safeContest, safeFatigue,
            safeQuality, airborne, aimError);
        double makeProbability = ShotModel.makeProbability(modelInput);

        if (!safeAim.aimingAtHoop()) {
            double throwCharge = Math.max(MIN_SKILL_SHOT_CHARGE, safeCharge);
            double throwSpeed = OFF_AIM_THROW_BASE_SPEED + throwCharge * OFF_AIM_THROW_CHARGE_SPEED;
            return direction.multiply(throwSpeed).add(new Vector(0.0, OFF_AIM_THROW_LIFT, 0.0));
        }

        Vector toRimUnit = new Vector(dx / rawDistance, 0.0, dz / rawDistance);
        Vector shooterRight = new Vector(-toRimUnit.getZ(), 0.0, toRimUnit.getX());

        double aimForgiveness = safeQuality == ShotReleaseQuality.PERFECT_GREEN
            && safeContest < OPEN_SHOT_CONTEST_THRESHOLD
            ? OPEN_GREEN_AIM_FORGIVENESS : OTHER_AIM_FORGIVENESS;
        double lateralOffset = distance * Math.tan(Math.toRadians(safeAim.yawErrorDeg() * aimForgiveness));
        lateralOffset = Math.clamp(lateralOffset, -MAX_AIM_LATERAL_OFFSET, MAX_AIM_LATERAL_OFFSET);

        double windowLow = ShotReleaseQuality.greenWindowLow(false, distance);
        double windowHigh = ShotReleaseQuality.greenWindowHigh(false, distance);
        double timingPenalty = ShotReleaseQuality.timingPenaltyBlocks(safeCharge, distance);
        double timingSign = safeCharge > windowHigh ? 1.0 : (safeCharge < windowLow ? -1.0 : 0.0);
        double depthOffset = timingSign * timingPenalty * 0.25;
        if (timingSign != 0.0 && rng != null) {
            lateralOffset += triangularRandom(rng) * timingPenalty * TIMING_LATERAL_SCATTER_RATIO;
            depthOffset += triangularRandom(rng) * timingPenalty * TIMING_DEPTH_SCATTER_RATIO;
        }

        if (Math.abs(safeAim.pitchErrorDeg()) > AIM_PITCH_DEPTH_THRESHOLD_DEG) {
            double excessPitch = safeAim.pitchErrorDeg()
                - Math.copySign(AIM_PITCH_DEPTH_THRESHOLD_DEG, safeAim.pitchErrorDeg());
            depthOffset += AIM_PITCH_DEPTH_SIGN * excessPitch * AIM_PITCH_DEPTH_PER_DEGREE;
        }

        // Probability and all accuracy factors share one dispersion description; the caller seed replays it.
        if (rng != null) {
            ShotModel.Dispersion dispersion = ShotModel.dispersion(modelInput, makeProbability);
            if (dispersion.pressureBlocks() > 0.0) {
                lateralOffset += triangularRandom(rng) * dispersion.pressureBlocks();
                depthOffset += triangularRandom(rng) * dispersion.pressureBlocks()
                    * ShotModel.RANGE_DEPTH_SCATTER_RATIO;
            }
            if (dispersion.contestJitterBlocks() > 0.0) {
                lateralOffset += (rng.nextDouble() - 0.5)
                    * ShotModel.CONTEST_JITTER_DIAMETER_FACTOR * dispersion.contestJitterBlocks();
                depthOffset += (rng.nextDouble() - 0.5)
                    * ShotModel.CONTEST_JITTER_DIAMETER_FACTOR * dispersion.contestJitterBlocks();
            } else if (dispersion.microScatterBlocks() > 0.0) {
                lateralOffset += (rng.nextDouble() - 0.5) * dispersion.microScatterBlocks();
            }
            if (dispersion.releaseSpreadBlocks() > 0.0) {
                lateralOffset += triangularRandom(rng) * dispersion.releaseSpreadBlocks();
                depthOffset += triangularRandom(rng) * dispersion.releaseSpreadBlocks()
                    * ShotModel.RELEASE_DEPTH_SCATTER_RATIO;
            }
        }

        Vector aimedTarget = safeRim.clone()
            .add(shooterRight.multiply(lateralOffset))
            .add(toRimUnit.multiply(depthOffset))
            .add(new Vector(0.0, AIMED_TARGET_HEIGHT_OFFSET, 0.0));

        double flightTicks = Math.clamp(SHOT_FLIGHT_BASE_TICKS + distance * SHOT_FLIGHT_TICKS_PER_BLOCK,
            MIN_ARC_TICKS, STANDARD_SHOT_MAX_FLIGHT_TICKS);
        if (safeAim.pitchErrorDeg() < LOW_ARC_PITCH_THRESHOLD_DEG) {
            double extension = Math.min(MAX_PITCH_FLIGHT_EXTENSION_TICKS,
                (-safeAim.pitchErrorDeg() + LOW_ARC_PITCH_THRESHOLD_DEG) * PITCH_FLIGHT_EXTENSION_PER_DEGREE);
            flightTicks = Math.min(MAX_ARC_TICKS, flightTicks + extension);
        }

        return solveExactArcToPoint(safeStart, aimedTarget, flightTicks, safeGravity, safeDrag);
    }

    public static List<Vector> simulatePreviewArc(
        Vector start,
        Vector initialVel,
        double gravity,
        double dragPerTick,
        double floorY,
        int maxTicks
    ) {
        if (!isFinite(start) || !isFinite(initialVel)) {
            return List.of();
        }
        Vector position = boundedVector(start);
        Vector velocity = boundedVector(initialVel);
        int ticks = Math.clamp(maxTicks, 0, MAX_PREVIEW_TICKS);
        double dt = 1.0 / PREVIEW_STEPS_PER_TICK;
        double subDrag = Math.pow(sanitizeAirDrag(dragPerTick), dt);
        double safeGravity = sanitizeGravity(gravity);
        double safeFloor = Double.isFinite(floorY) ? Math.clamp(floorY, -MAX_VECTOR_COMPONENT, MAX_VECTOR_COMPONENT)
            : -MAX_VECTOR_COMPONENT;

        List<Vector> points = new ArrayList<>(ticks);
        for (int tick = 0; tick < ticks; tick++) {
            for (int step = 0; step < PREVIEW_STEPS_PER_TICK; step++) {
                velocity.setY(velocity.getY() - safeGravity * dt);
                velocity.multiply(subDrag);
                position.add(velocity.clone().multiply(dt));
            }
            if (!isFinite(position) || !isFinite(velocity)) {
                break;
            }
            points.add(position.clone());
            if (position.getY() <= safeFloor + BALL_RADIUS) {
                break;
            }
        }
        return points;
    }

    /**
     * The point just outside an axis-aligned box, moved along the axis of least penetration.
     *
     * <p>Used when the ball is already inside geometry (a wall, a backboard, a carpet). A world
     * raycast cannot answer from inside a block: it reports the exit face, and snapping the ball
     * to that exit face throws it clean across the obstacle. In play that reads as a shot that
     * never leaves the player's hands and a ball that is suddenly at the other end of the court.
     */
    public static Vector escapeFromBox(Vector boxMin, Vector boxMax, Vector point, double radius) {
        if (boxMin == null || boxMax == null || !isFinite(point) || !(radius > 0.0)) {
            return boundedVector(point == null ? new Vector() : point);
        }
        Vector safeMin = boundedVector(boxMin);
        Vector safeMax = boundedVector(boxMax);
        double outMinX = point.getX() - (safeMin.getX() - radius);
        double outMaxX = (safeMax.getX() + radius) - point.getX();
        double outMinY = point.getY() - (safeMin.getY() - radius);
        double outMaxY = (safeMax.getY() + radius) - point.getY();
        double outMinZ = point.getZ() - (safeMin.getZ() - radius);
        double outMaxZ = (safeMax.getZ() + radius) - point.getZ();

        Vector result = point.clone();
        double shortest = outMinX;
        result.setX(safeMin.getX() - radius);
        if (outMaxX < shortest) {
            shortest = outMaxX;
            result.setX(safeMax.getX() + radius);
        }
        if (outMinY < shortest) {
            shortest = outMinY;
            result.setY(safeMin.getY() - radius);
            result.setX(point.getX());
        }
        if (outMaxY < shortest) {
            shortest = outMaxY;
            result.setY(safeMax.getY() + radius);
            result.setX(point.getX());
        }
        if (outMinZ < shortest) {
            shortest = outMinZ;
            result.setZ(safeMin.getZ() - radius);
            result.setX(point.getX());
            result.setY(point.getY());
        }
        shortest = Math.min(shortest, outMaxZ);
        if (outMaxZ <= shortest) {
            result.setZ(safeMax.getZ() + radius);
            result.setX(point.getX());
            result.setY(point.getY());
        }
        return boundedVector(result);
    }

    public static Vector solveBallisticArc(
        Vector start,
        Vector rimCenter,
        double gravity,
        double dragPerTick,
        ShotReleaseQuality quality,
        Random rng
    ) {
        if (!isFinite(start) || !isFinite(rimCenter)) {
            return new Vector(0.0, 0.0, 0.0);
        }
        Vector safeStart = boundedVector(start);
        Vector target = boundedVector(rimCenter).add(new Vector(0.0, BALLISTIC_TARGET_HEIGHT_OFFSET, 0.0));
        ShotReleaseQuality safeQuality = quality == null ? ShotReleaseQuality.VERY_EARLY : quality;

        if (safeQuality != ShotReleaseQuality.PERFECT_GREEN && rng != null) {
            double maxScatter = safeQuality.maxLateralScatterBlocks() * BALLISTIC_SCATTER_MULTIPLIER;
            double minScatter = maxScatter * BALLISTIC_MIN_SCATTER_RATIO;
            double radius = minScatter + rng.nextDouble() * (maxScatter - minScatter);
            double angle = rng.nextDouble() * Math.PI * 2.0;
            target.add(new Vector(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
        }

        double horizontalDistance = Math.hypot(target.getX() - safeStart.getX(), target.getZ() - safeStart.getZ());
        double flightTicks = Math.clamp(SHOT_FLIGHT_BASE_TICKS + horizontalDistance * SHOT_FLIGHT_TICKS_PER_BLOCK,
            MIN_ARC_TICKS, STANDARD_SHOT_MAX_FLIGHT_TICKS);
        return solveExactArcToPoint(safeStart, target, flightTicks, sanitizeGravity(gravity), sanitizeAirDrag(dragPerTick));
    }

    private static Vector solveExactArcToPoint(
        Vector start,
        Vector target,
        double flightTicks,
        double gravity,
        double dragPerTick
    ) {
        if (!isFinite(start) || !isFinite(target)) {
            return new Vector(0.0, 0.0, 0.0);
        }
        Vector safeStart = boundedVector(start);
        Vector safeTarget = boundedVector(target);
        double safeFlightTicks = finiteClamp(flightTicks, MIN_ARC_TICKS, MAX_ARC_TICKS, MIN_ARC_TICKS);
        double safeGravity = sanitizeGravity(gravity);
        double safeDrag = sanitizeAirDrag(dragPerTick);
        int substeps = (int) Math.round(safeFlightTicks * PREVIEW_STEPS_PER_TICK);
        double dt = 1.0 / PREVIEW_STEPS_PER_TICK;
        double subDrag = Math.pow(safeDrag, dt);

        double sumDrag = 0.0;
        double gravityDisplacementY = 0.0;
        double currentVelocityFactor = 1.0;
        double currentGravityVelocity = 0.0;
        for (int step = 0; step < substeps; step++) {
            currentGravityVelocity = (currentGravityVelocity - safeGravity * dt) * subDrag;
            currentVelocityFactor *= subDrag;
            sumDrag += currentVelocityFactor * dt;
            gravityDisplacementY += currentGravityVelocity * dt;
        }
        if (!Double.isFinite(sumDrag) || sumDrag <= NORMAL_SPEED_EPSILON) {
            return new Vector(0.0, 0.0, 0.0);
        }

        double vx = (safeTarget.getX() - safeStart.getX()) / sumDrag;
        double vz = (safeTarget.getZ() - safeStart.getZ()) / sumDrag;
        double vy = (safeTarget.getY() - safeStart.getY() - gravityDisplacementY) / sumDrag;
        return boundedVector(new Vector(vx, vy, vz));
    }

    public static CollisionResult collideWithRimTorus(
        Vector ballPos,
        Vector ballVel,
        Vector rimCenter,
        double restitution
    ) {
        if (!isFinite(ballPos) || !isFinite(ballVel) || !isFinite(rimCenter)) {
            return collisionMiss(ballPos, ballVel);
        }
        Vector position = boundedVector(ballPos);
        Vector velocity = boundedVector(ballVel);
        Vector rim = boundedVector(rimCenter);

        double dx = position.getX() - rim.getX();
        double dy = position.getY() - rim.getY();
        double dz = position.getZ() - rim.getZ();
        double horizontalDistance = Math.hypot(dx, dz);

        double closestRingX;
        double closestRingZ;
        if (horizontalDistance > RIM_AXIS_EPSILON) {
            closestRingX = dx / horizontalDistance * RIM_RADIUS;
            closestRingZ = dz / horizontalDistance * RIM_RADIUS;
        } else {
            closestRingX = RIM_RADIUS;
            closestRingZ = 0.0;
        }

        double nx = dx - closestRingX;
        double ny = dy;
        double nz = dz - closestRingZ;
        double distanceToTube = robustLength(nx, ny, nz);
        double minimumSeparation = BALL_RADIUS + RIM_TUBE_RADIUS;
        if (!Double.isFinite(distanceToTube) || distanceToTube >= minimumSeparation) {
            return new CollisionResult(false, position, velocity, 0.0);
        }

        Vector normal;
        if (distanceToTube > RIM_NORMAL_EPSILON) {
            normal = new Vector(nx / distanceToTube, ny / distanceToTube, nz / distanceToTube);
        } else {
            // The exact tube centreline has no unique normal; use its deterministic outward radial axis.
            double ringLength = Math.hypot(closestRingX, closestRingZ);
            normal = ringLength > RIM_NORMAL_EPSILON
                ? new Vector(closestRingX / ringLength, 0.0, closestRingZ / ringLength)
                : new Vector(1.0, 0.0, 0.0);
        }

        double restitutionSafe = sanitizeRestitution(restitution, RIM_RESTITUTION);
        double vn = velocity.dot(normal);
        double approachSpeed = Math.max(0.0, -vn);
        Vector pushedPosition = position.clone().add(normal.clone().multiply(
            minimumSeparation - distanceToTube + RIM_CONTACT_SLOP));
        Vector response;

        if (vn < -RIM_APPROACH_EPSILON) {
            Vector tangent = tangentialComponentAfterFriction(velocity, normal, vn,
                restitutionSafe, RIM_TANGENTIAL_FRICTION);
            response = tangent.add(normal.clone().multiply(approachSpeed * restitutionSafe));
        } else if (vn <= RIM_APPROACH_EPSILON) {
            Vector tangent = velocity.clone().subtract(normal.clone().multiply(vn));
            if (approachSpeed > NORMAL_SPEED_EPSILON) {
                tangent = tangentialComponentAfterFriction(velocity, normal, vn,
                    restitutionSafe, RIM_TANGENTIAL_FRICTION);
            }
            // A near-stationary overlap is nudged outward so numerical contact cannot pin the ball to iron.
            response = tangent.add(normal.clone().multiply(MIN_RIM_ESCAPE_SPEED));
        } else {
            // Already separating: depenetrate without reversing or reducing the outward velocity.
            response = velocity;
        }

        return new CollisionResult(true, pushedPosition, response, approachSpeed);
    }

    public static CollisionResult collideWithBackboard(
        Vector ballPos,
        Vector ballVel,
        Vector rimCenter,
        int normalSignZ,
        double restitution
    ) {
        Vector forward = new Vector(0.0, 0.0, normalSignZ >= 0 ? 1.0 : -1.0);
        return collideWithBackboard(ballPos, ballVel, rimCenter, forward, restitution);
    }

    public static CollisionResult collideWithBackboard(
        Vector ballPos,
        Vector ballVel,
        Vector rimCenter,
        Vector hoopForward,
        double restitution
    ) {
        if (!isFinite(ballPos) || !isFinite(ballVel) || !isFinite(rimCenter)) {
            return collisionMiss(ballPos, ballVel);
        }
        Vector position = boundedVector(ballPos);
        Vector velocity = boundedVector(ballVel);
        Vector rim = boundedVector(rimCenter);
        Vector forward = normalizedHorizontalOr(hoopForward, new Vector(0.0, 0.0, 1.0));
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());

        Vector relative = position.clone().subtract(rim);
        double relativeRight = relative.dot(right);
        double relativeY = relative.getY();
        if (Math.abs(relativeRight) > BACKBOARD_HALF_WIDTH + BALL_RADIUS * 0.5
            || relativeY < BACKBOARD_MIN_Y_REL || relativeY > BACKBOARD_MAX_Y_REL) {
            return new CollisionResult(false, position, velocity, 0.0);
        }

        double backboardPlane = -BACKBOARD_OFFSET;
        double relativeForward = relative.dot(forward);
        double forwardVelocity = velocity.dot(forward);
        if (forwardVelocity < 0.0
            && relativeForward - BALL_RADIUS <= backboardPlane
            && relativeForward >= backboardPlane - BACKBOARD_CONTACT_DEPTH) {
            double pushDelta = backboardPlane + BALL_RADIUS + BACKBOARD_CONTACT_SLOP - relativeForward;
            Vector pushed = position.clone().add(forward.clone().multiply(pushDelta));
            double safeRestitution = sanitizeRestitution(restitution, BACKBOARD_RESTITUTION);
            Vector tangent = tangentialComponentAfterFriction(velocity, forward, forwardVelocity,
                safeRestitution, BACKBOARD_TANGENTIAL_FRICTION);
            Vector reflected = tangent.add(forward.clone().multiply(-forwardVelocity * safeRestitution));
            return new CollisionResult(true, pushed, reflected, -forwardVelocity);
        }
        return new CollisionResult(false, position, velocity, 0.0);
    }

    public static boolean didScoreDownwardThroughHoop(Vector prevPos, Vector currPos, Vector rimCenter) {
        if (!isFinite(prevPos) || !isFinite(currPos) || !isFinite(rimCenter)) {
            return false;
        }
        Vector previous = boundedVector(prevPos);
        Vector current = boundedVector(currPos);
        Vector rim = boundedVector(rimCenter);
        if (previous.getY() < rim.getY() || current.getY() >= rim.getY()) {
            return false;
        }
        double denominator = previous.getY() - current.getY();
        double t = denominator > NORMAL_SPEED_EPSILON
            ? (previous.getY() - rim.getY()) / denominator : 0.5;
        double crossX = previous.getX() + (current.getX() - previous.getX()) * t;
        double crossZ = previous.getZ() + (current.getZ() - previous.getZ()) * t;
        double radial = Math.hypot(crossX - rim.getX(), crossZ - rim.getZ());
        return Double.isFinite(radial) && radial <= SCORING_CYLINDER_RADIUS;
    }

    /** Package-level restitution sanitizer shared with the live entity's configuration values. */
    static double sanitizeRestitution(double restitution, double fallback) {
        return Double.isFinite(restitution) ? Math.clamp(restitution, 0.0, 1.0) : fallback;
    }

    /** Package-level Coulomb-style tangent impulse used for block and floor contacts. */
    static Vector tangentialComponentAfterFriction(Vector velocity, Vector normal, double normalVelocity,
                                                   double restitution, double friction) {
        if (!isFinite(velocity) || !isFinite(normal) || !Double.isFinite(normalVelocity)) {
            return new Vector(0.0, 0.0, 0.0);
        }
        Vector tangent = boundedVector(velocity).subtract(boundedVector(normal).multiply(normalVelocity));
        double tangentSpeed = robustLength(tangent);
        if (!Double.isFinite(tangentSpeed) || tangentSpeed <= NORMAL_SPEED_EPSILON) {
            return new Vector(0.0, 0.0, 0.0);
        }
        double safeRestitution = sanitizeRestitution(restitution, FLOOR_RESTITUTION);
        double safeFriction = finiteClamp(friction, 0.0, 1.0, 0.0);
        double frictionImpulse = safeFriction * (1.0 + safeRestitution) * Math.max(0.0, -normalVelocity);
        double retainedSpeed = Math.max(0.0, tangentSpeed - Math.min(tangentSpeed, frictionImpulse));
        return tangent.multiply(retainedSpeed / tangentSpeed);
    }

    private static boolean inferAirborne(Vector start, Vector lookDir, Vector rimCenter) {
        if (!isFinite(start) || !isFinite(lookDir) || !isFinite(rimCenter)) {
            return false;
        }
        Vector direction = normalizedOr(lookDir, new Vector(0.0, 0.0, 1.0));
        double eyeY = start.getY() - direction.getY() * SHOT_START_FORWARD_OFFSET;
        double standingEyeY = rimCenter.getY() - REGULATION_RIM_HEIGHT + STANDING_EYE_HEIGHT;
        return eyeY > standingEyeY + AIRBORNE_EYE_HEIGHT_THRESHOLD;
    }

    private static AimEvaluation invalidAim() {
        return new AimEvaluation(false, INVALID_AIM_ERROR_DEGREES, INVALID_AIM_ERROR_DEGREES, MAX_DISTANCE_BLOCKS);
    }

    private static CollisionResult collisionMiss(Vector position, Vector velocity) {
        return new CollisionResult(false, finiteVectorOrZero(position), finiteVectorOrZero(velocity), 0.0);
    }

    private static double triangularRandom(Random random) {
        // The sum of two uniform draws gives a centred triangular error without extra distribution objects.
        return random.nextDouble() + random.nextDouble() - 1.0;
    }

    private static Vector normalizedOr(Vector vector, Vector fallback) {
        if (!isFinite(vector)) {
            return fallback.clone();
        }
        Vector safe = boundedVector(vector);
        double scale = Math.max(Math.abs(safe.getX()), Math.max(Math.abs(safe.getY()), Math.abs(safe.getZ())));
        if (scale <= MIN_VECTOR_LENGTH) {
            return fallback.clone();
        }
        double x = safe.getX() / scale;
        double y = safe.getY() / scale;
        double z = safe.getZ() / scale;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (!Double.isFinite(length) || length <= MIN_VECTOR_LENGTH) {
            return fallback.clone();
        }
        return new Vector(x / length, y / length, z / length);
    }

    private static Vector normalizedHorizontalOr(Vector vector, Vector fallback) {
        if (!isFinite(vector)) {
            return fallback.clone();
        }
        Vector flat = new Vector(vector.getX(), 0.0, vector.getZ());
        return normalizedOr(flat, fallback);
    }

    static Vector boundedVector(Vector vector) {
        if (!isFinite(vector)) {
            return new Vector(0.0, 0.0, 0.0);
        }
        return new Vector(
            Math.clamp(vector.getX(), -MAX_VECTOR_COMPONENT, MAX_VECTOR_COMPONENT),
            Math.clamp(vector.getY(), -MAX_VECTOR_COMPONENT, MAX_VECTOR_COMPONENT),
            Math.clamp(vector.getZ(), -MAX_VECTOR_COMPONENT, MAX_VECTOR_COMPONENT)
        );
    }

    private static Vector finiteVectorOrZero(Vector vector) {
        return isFinite(vector) ? boundedVector(vector) : new Vector(0.0, 0.0, 0.0);
    }

    private static boolean isFinite(Vector vector) {
        return vector != null
            && Double.isFinite(vector.getX())
            && Double.isFinite(vector.getY())
            && Double.isFinite(vector.getZ());
    }

    private static double robustLength(Vector vector) {
        return robustLength(vector.getX(), vector.getY(), vector.getZ());
    }

    private static double robustLength(double x, double y, double z) {
        return Math.hypot(Math.hypot(x, y), z);
    }

    private static double finiteClamp(double value, double minimum, double maximum, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
    }

    static double sanitizeGravity(double gravity) {
        return finiteClamp(gravity, MIN_GRAVITY, MAX_GRAVITY, DEFAULT_GRAVITY);
    }

    static double sanitizeAirDrag(double dragPerTick) {
        return finiteClamp(dragPerTick, MIN_AIR_DRAG, MAX_AIR_DRAG, DEFAULT_AIR_DRAG);
    }
}
