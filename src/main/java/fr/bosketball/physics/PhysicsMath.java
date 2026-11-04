package fr.bosketball.physics;

import fr.bosketball.model.ShotReleaseQuality;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.util.Vector;

/**
 * Pure 3D Rigid-Body & Skill-Based Ballistic Basketball Physics Math (Unit-tested standalone without a live server).
 * Supports 360° hoop orientations, true directional aiming (zero magnetic bleacher auto-shots!), and trajectory previews.
 */
public final class PhysicsMath {

    public static final double BALL_RADIUS = 0.285; // Calibrated to 3D rotated corner envelope of sculpted 0.52f cube!
    public static final double RIM_RADIUS = 0.45;   // Regulation inner ring radius in blocks
    public static final double RIM_MAJOR_RADIUS = RIM_RADIUS;
    public static final double RIM_TUBE_RADIUS = 0.042;
    public static final double SCORING_CYLINDER_RADIUS = 0.39;

    // Backboard dimensions relative to rimCenter along -hoopForward
    public static final double BACKBOARD_OFFSET = 0.650;
    public static final double BACKBOARD_HALF_WIDTH = 0.92;
    public static final double BACKBOARD_MIN_Y_REL = -0.15;
    public static final double BACKBOARD_MAX_Y_REL = 1.15;

    /** Maximum horizontal angle (degrees) from the rim within which a shot targets the hoop arc instead of free-throwing. */
    public static final double MAX_HOOP_AIM_YAW_DEG = 22.0;

    private PhysicsMath() {
    }

    public record CollisionResult(boolean hit, Vector position, Vector velocity, double impactSpeed) {
    }

    /**
     * Complete telemetry of where a player's crosshair is aiming relative to a basketball hoop!
     *
     * @param aimingAtHoop   true ONLY when aiming within ±22° horizontally and comfortable pitch toward the basket
     * @param yawErrorDeg    signed horizontal aim error in degrees (- = left of rim, 0 = dead center, + = right of rim)
     * @param pitchErrorDeg  signed vertical pitch delta vs ideal shooting elevation
     * @param horizontalDist horizontal distance in blocks from eye to rim center
     */
    public record AimEvaluation(
        boolean aimingAtHoop,
        double yawErrorDeg,
        double pitchErrorDeg,
        double horizontalDist
    ) {
    }

    /**
     * Evaluates the shooter's real 3D look direction relative to a target rim.
     * Aiming at the bleachers, sidelines, floor, or opposite court sets {@code aimingAtHoop = false}!
     */
    public static AimEvaluation evaluateShooterAim(Vector eyePos, Vector lookDir, Vector targetRim) {
        Vector dir = lookDir.clone();
        if (dir.lengthSquared() < 1e-6) {
            dir = new Vector(0, 0, 1);
        } else {
            dir.normalize();
        }

        double dx = targetRim.getX() - eyePos.getX();
        double dz = targetRim.getZ() - eyePos.getZ();
        double horizDist = Math.hypot(dx, dz);
        if (horizDist < 0.35) {
            return new AimEvaluation(true, 0.0, 0.0, horizDist);
        }

        Vector toRimH = new Vector(dx / horizDist, 0.0, dz / horizDist);
        Vector lookH = new Vector(dir.getX(), 0.0, dir.getZ());
        if (lookH.lengthSquared() < 1e-5) {
            return new AimEvaluation(false, 90.0, -90.0, horizDist);
        }
        lookH.normalize();

        // Signed horizontal angle from toRimH to lookH (positive = aiming to shooter's right of the rim!)
        // In Minecraft's right-handed world (Y up): right of forward (fx, 0, fz) is (-fz, 0, fx).
        Vector shooterRight = new Vector(-toRimH.getZ(), 0.0, toRimH.getX());
        double sinYaw = lookH.dot(shooterRight);
        double cosYaw = Math.max(-1.0, Math.min(1.0, lookH.dot(toRimH)));
        double yawErrorDeg = Math.toDegrees(Math.atan2(sinYaw, cosYaw));

        // Pitch in Minecraft degrees: negative = looking upward, positive = looking downward
        double pitchDeg = Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, dir.getY()))));
        double idealPitchDeg = -Math.min(34.0, 9.5 + horizDist * 1.35);
        double pitchErrorDeg = pitchDeg - idealPitchDeg;

        boolean validYaw = Math.abs(yawErrorDeg) <= MAX_HOOP_AIM_YAW_DEG;
        boolean validPitch = pitchDeg <= 22.0 && pitchDeg >= -68.0;
        boolean validRange = horizDist <= 34.0;
        return new AimEvaluation(validYaw && validPitch && validRange, yawErrorDeg, pitchErrorDeg, horizDist);
    }

    /**
     * Exact Minecraft Bow pullback curve (0 ticks -> 0.08, 18 ticks -> 0.87, 20+ ticks -> 1.00 Full Draw).
     */
    public static double computeBowPullProgress(int ticksUsed) {
        if (ticksUsed <= 0) {
            return 0.08;
        }
        double f = ticksUsed / 20.0;
        double pull = (f * f + f * 2.0) / 3.0;
        return Math.max(0.08, Math.min(1.0, pull));
    }

    /**
     * Solves a true skill-based shot velocity:
     *  - If NOT aiming at the hoop (e.g. aiming at the bleachers/sidelines), throws strictly in the player's look direction!
     *  - If aiming at the hoop, uses real horizontal crosshair offset + charge timing + pitch + defender contest!
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
        Vector dir = lookDir.clone().normalize();

        // 1. Aiming away from the basket (bleachers, walls, sidelines, full-court heave away): pure directional throw!
        if (!aim.aimingAtHoop()) {
            double clampedCharge = Math.max(0.15, Math.min(1.20, chargeProgress));
            double throwSpeed = 0.56 + clampedCharge * 0.92;
            return dir.multiply(throwSpeed).add(new Vector(0.0, 0.11, 0.0));
        }

        // 2. Aiming within the hoop cone: real crosshair yaw + timing depth + pitch + contest!
        double dx = rimCenter.getX() - start.getX();
        double dz = rimCenter.getZ() - start.getZ();
        double dist = Math.max(0.5, Math.hypot(dx, dz));
        Vector toRimUnit = new Vector(dx / dist, 0.0, dz / dist);
        Vector shooterRight = new Vector(-toRimUnit.getZ(), 0.0, toRimUnit.getX());

        // Real horizontal aim displacement on the rim plane!
        double aimForgiveness = (quality == ShotReleaseQuality.PERFECT_GREEN && contestRatio < 0.30) ? 0.56 : 0.84;
        double lateralOffset = dist * Math.tan(Math.toRadians(aim.yawErrorDeg() * aimForgiveness));
        lateralOffset = Math.max(-2.6, Math.min(2.6, lateralOffset));

        // Depth displacement from Bow Draw power. The "dead centre" is the middle of the release
        // window rather than a hard-coded 0.84..1.02 band, so the perfect release and the validated
        // release window can never drift apart (they used to, and 0.81-0.84 releases flew long).
        double windowLow = ShotReleaseQuality.greenWindowLow(false, dist);
        double windowHigh = ShotReleaseQuality.greenWindowHigh(false, dist);
        // Timing error, expressed directly in blocks of rim-plane error (shared helper, so the HUD,
        // the solver and the balance study can never disagree). Most of it goes into the depth and
        // the rest into a lateral spread: a mistimed shot is uncertain, not merely long or short.
        double timingPenalty = ShotReleaseQuality.timingPenaltyBlocks(chargeProgress, dist);
        double timingSign = chargeProgress > windowHigh ? 1.0 : (chargeProgress < windowLow ? -1.0 : 0.0);
        double depthOffset = timingSign * timingPenalty * 0.25;
        if (timingSign != 0.0 && rng != null) {
            lateralOffset += (rng.nextDouble() + rng.nextDouble() - 1.0) * timingPenalty * 0.45;
            depthOffset += (rng.nextDouble() + rng.nextDouble() - 1.0) * timingPenalty * 0.30;
        }
        if (Math.abs(aim.pitchErrorDeg()) > 12.0) {
            // Looking too low (>0) leaves shot short; looking super steep (<0) sends shot slightly deeper/higher
            double excessPitch = aim.pitchErrorDeg() - Math.copySign(12.0, aim.pitchErrorDeg());
            depthOffset -= excessPitch * 0.028;
        }

        // Range + contest miss pressure. A wide-open look from the paint must go in, a deep
        // contested three must not: the scatter grows with distance and with the closest defender.
        //
        // This used to apply ONLY to green releases, which meant a mistimed shot flew perfectly
        // straight while a green one wobbled — a late release was measurably MORE accurate than a
        // green one at range. Every release carries the scatter now; greens still get the smaller
        // aim factor and the tighter jitter below.
        if (rng != null) {
            // Measured with tools/match_study.py: a defender in the paint used to cost almost
            // nothing (a hand in the face at 4m still went in 71%), so contest now grows tighter
            // the closer the shot is, where a body genuinely blocks the release.
            // Distance pressure used to be linear and tiny (+0.075/block past 5 m), so an OPEN three
            // from 8 m went in 99% of the time — the most obviously abusable shot in the game. It is
            // now a super-linear ramp, and a mistimed release carries 30% more of it than a green:
            //   open green  2.6 m 100% · 6 m 96% · 7.25 m 76% · 8 m 61% · 10 m 36% · 12 m 20%
            //   contested   2.6 m  86% · 6 m 53% · 7.25 m 37% · 8 m 32% · 10 m 20%
            double rangePressure = 0.050 * Math.pow(Math.max(0.0, dist - 2.6), 1.30)
                * (quality == ShotReleaseQuality.PERFECT_GREEN ? 1.0 : 1.30);
            double pressure = rangePressure
                + contestRatio * (0.50 + Math.max(0.0, 6.0 - dist) * 0.12);
            if (pressure > 0.0) {
                lateralOffset += (rng.nextDouble() + rng.nextDouble() - 1.0) * pressure;
                depthOffset += (rng.nextDouble() + rng.nextDouble() - 1.0) * pressure * 0.7;
            }
        }

        // Defender contest pressure jitter
        if (contestRatio > 0.08 && rng != null) {
            double jitterScale = contestRatio * (quality == ShotReleaseQuality.PERFECT_GREEN ? 0.14 : 0.34);
            lateralOffset += (rng.nextDouble() - 0.5) * 2.0 * jitterScale;
            depthOffset += (rng.nextDouble() - 0.5) * 2.0 * jitterScale;
        } else if (quality != ShotReleaseQuality.PERFECT_GREEN && rng != null) {
            double micro = Math.min(0.10, quality.maxLateralScatterBlocks() * 0.65);
            lateralOffset += (rng.nextDouble() - 0.5) * micro;
        }

        // A release outside the window always costs a little accuracy on top of the power penalty:
        // a slightly-late shot that lands dead centre was the "it does not matter how I time it"
        // bug. Green stays pixel-perfect, everything else carries a real, distance-scaled spread.
        if (quality != ShotReleaseQuality.PERFECT_GREEN && rng != null) {
            double spread = (quality.maxLateralScatterBlocks() * 2.3 + 0.05) * (1.0 + dist * 0.05);
            lateralOffset += (rng.nextDouble() + rng.nextDouble() - 1.0) * spread;
            depthOffset += (rng.nextDouble() + rng.nextDouble() - 1.0) * spread * 0.8;
        }

        Vector aimedTarget = rimCenter.clone()
            .add(shooterRight.multiply(lateralOffset))
            .add(toRimUnit.multiply(depthOffset))
            .add(new Vector(0.0, 0.06, 0.0));

        double flightTicks = Math.max(15.0, Math.min(44.0, 13.5 + dist * 1.48));
        if (aim.pitchErrorDeg() < -8.0) {
            // Shooter deliberately aimed higher -> higher rainbow arc!
            flightTicks = Math.min(48.0, flightTicks + Math.min(6.0, (-aim.pitchErrorDeg() - 8.0) * 0.25));
        }

        return solveExactArcToPoint(start, aimedTarget, flightTicks, gravity, dragPerTick);
    }

    /**
     * Simulates an exact preview curve (matching BasketballEntity's 4-substep 80Hz integrator) for our 3D HUD Aim Ribbon!
     */
    public static List<Vector> simulatePreviewArc(
        Vector start,
        Vector initialVel,
        double gravity,
        double dragPerTick,
        double floorY,
        int maxTicks
    ) {
        List<Vector> points = new ArrayList<>(maxTicks);
        Vector pos = start.clone();
        Vector vel = initialVel.clone();
        double dt = 0.25;
        double subDrag = Math.pow(dragPerTick, dt);

        for (int t = 0; t < maxTicks; t++) {
            for (int s = 0; s < 4; s++) {
                vel.setY(vel.getY() - gravity * dt);
                vel.multiply(subDrag);
                pos.add(vel.clone().multiply(dt));
            }
            points.add(pos.clone());
            if (pos.getY() <= floorY + BALL_RADIUS) {
                break;
            }
        }
        return points;
    }

    /**
     * Solves exact initial velocity vector (accounting for per-tick drag & gravity) for Passes, Alley-Oops & Ideal Shots.
     */
    public static Vector solveBallisticArc(
        Vector start,
        Vector rimCenter,
        double gravity,
        double dragPerTick,
        ShotReleaseQuality quality,
        Random rng
    ) {
        Vector target = rimCenter.clone().add(new Vector(0.0, 0.06, 0.0));

        if (quality != ShotReleaseQuality.PERFECT_GREEN && rng != null) {
            double maxScatter = quality.maxLateralScatterBlocks() * 3.8;
            double minScatter = maxScatter * 0.38;
            double radius = minScatter + rng.nextDouble() * (maxScatter - minScatter);
            double angle = rng.nextDouble() * Math.PI * 2.0;
            target.add(new Vector(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
        }

        double horizDist = Math.hypot(target.getX() - start.getX(), target.getZ() - start.getZ());
        double flightTicks = Math.max(15.0, Math.min(44.0, 13.5 + horizDist * 1.48));
        return solveExactArcToPoint(start, target, flightTicks, gravity, dragPerTick);
    }

    private static Vector solveExactArcToPoint(
        Vector start,
        Vector target,
        double flightTicks,
        double gravity,
        double dragPerTick
    ) {
        int substeps = (int) Math.round(flightTicks * 4.0);
        double dt = 0.25;
        double subDrag = Math.pow(dragPerTick, dt);

        double sumDrag = 0.0;
        double gravDispY = 0.0;
        double curVelFactor = 1.0;
        double curGravVel = 0.0;
        for (int i = 0; i < substeps; i++) {
            curGravVel = (curGravVel - gravity * dt) * subDrag;
            curVelFactor *= subDrag;
            sumDrag += curVelFactor * dt;
            gravDispY += curGravVel * dt;
        }

        double vx = (target.getX() - start.getX()) / sumDrag;
        double vz = (target.getZ() - start.getZ()) / sumDrag;
        double vy = (target.getY() - start.getY() - gravDispY) / sumDrag;
        return new Vector(vx, vy, vz);
    }

    /**
     * 3D Torus (Circle of Tube) Rim Collision: models the real circular steel ring!
     */
    public static CollisionResult collideWithRimTorus(
        Vector ballPos,
        Vector ballVel,
        Vector rimCenter,
        double restitution
    ) {
        double dx = ballPos.getX() - rimCenter.getX();
        double dy = ballPos.getY() - rimCenter.getY();
        double dz = ballPos.getZ() - rimCenter.getZ();

        double horizDist = Math.hypot(dx, dz);
        double closestRingX;
        double closestRingZ;
        if (horizDist > 1e-5) {
            closestRingX = (dx / horizDist) * RIM_RADIUS;
            closestRingZ = (dz / horizDist) * RIM_RADIUS;
        } else {
            closestRingX = RIM_RADIUS;
            closestRingZ = 0.0;
        }

        double nx = dx - closestRingX;
        double ny = dy;
        double nz = dz - closestRingZ;
        double distToTube = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double minSep = BALL_RADIUS + RIM_TUBE_RADIUS;

        if (distToTube >= minSep || distToTube < 1e-6) {
            return new CollisionResult(false, ballPos, ballVel, 0.0);
        }

        Vector normal = new Vector(nx / distToTube, ny / distToTube, nz / distToTube);
        Vector pushedPos = ballPos.clone().add(normal.clone().multiply(minSep - distToTube));

        double vn = ballVel.dot(normal);
        if (vn >= 0.0) {
            return new CollisionResult(false, pushedPos, ballVel, 0.0);
        }

        Vector reflected = ballVel.clone().subtract(normal.clone().multiply((1.0 + restitution) * vn));
        reflected.setX(reflected.getX() * 0.94);
        reflected.setZ(reflected.getZ() * 0.94);

        return new CollisionResult(true, pushedPos, reflected, -vn);
    }

    /**
     * Legacy Z-aligned backboard helper (delegates directly to 360° vector backboard collision).
     */
    public static CollisionResult collideWithBackboard(
        Vector ballPos,
        Vector ballVel,
        Vector rimCenter,
        int normalSignZ,
        double restitution
    ) {
        return collideWithBackboard(ballPos, ballVel, rimCenter, new Vector(0, 0, normalSignZ >= 0 ? 1.0 : -1.0), restitution);
    }

    /**
     * 360° Tempered-Glass Backboard Planar Box Collision (works for North, South, East, West, or diagonal hoops!).
     */
    public static CollisionResult collideWithBackboard(
        Vector ballPos,
        Vector ballVel,
        Vector rimCenter,
        Vector hoopForward,
        double restitution
    ) {
        Vector fwd = new Vector(hoopForward.getX(), 0.0, hoopForward.getZ());
        if (fwd.lengthSquared() < 1e-6) {
            fwd = new Vector(0.0, 0.0, 1.0);
        } else {
            fwd.normalize();
        }
        Vector right = new Vector(-fwd.getZ(), 0.0, fwd.getX());

        Vector rel = ballPos.clone().subtract(rimCenter);
        double relRight = rel.dot(right);
        double relY = rel.getY();
        if (Math.abs(relRight) > BACKBOARD_HALF_WIDTH + BALL_RADIUS * 0.5) {
            return new CollisionResult(false, ballPos, ballVel, 0.0);
        }
        if (relY < BACKBOARD_MIN_Y_REL || relY > BACKBOARD_MAX_Y_REL) {
            return new CollisionResult(false, ballPos, ballVel, 0.0);
        }

        double planeFwd = -BACKBOARD_OFFSET;
        double relFwd = rel.dot(fwd);
        double velFwd = ballVel.dot(fwd);

        // Moving INTO the front face of the glass (velFwd < 0)
        if (velFwd < 0.0 && (relFwd - BALL_RADIUS) <= planeFwd && relFwd >= planeFwd - 0.42) {
            double pushDelta = (planeFwd + BALL_RADIUS) - relFwd;
            Vector pushed = ballPos.clone().add(fwd.clone().multiply(pushDelta));

            // Decompose velocity into normal (fwd) and tangential (right, Y) components
            double vRight = ballVel.dot(right) * 0.92;
            double vY = ballVel.getY() * 0.92;
            double newVFwd = -velFwd * restitution;

            Vector newVel = fwd.clone().multiply(newVFwd)
                .add(right.clone().multiply(vRight))
                .add(new Vector(0.0, vY, 0.0));
            return new CollisionResult(true, pushed, newVel, -velFwd);
        }

        return new CollisionResult(false, ballPos, ballVel, 0.0);
    }

    /**
     * Detects a legal downward basket crossing through the horizontal rim plane.
     */
    public static boolean didScoreDownwardThroughHoop(Vector prevPos, Vector currPos, Vector rimCenter) {
        double rimY = rimCenter.getY();
        if (prevPos.getY() < rimY || currPos.getY() >= rimY) {
            return false;
        }
        double denom = prevPos.getY() - currPos.getY();
        double t = denom > 1e-6 ? (prevPos.getY() - rimY) / denom : 0.5;
        double crossX = prevPos.getX() + (currPos.getX() - prevPos.getX()) * t;
        double crossZ = prevPos.getZ() + (currPos.getZ() - prevPos.getZ()) * t;

        double radial = Math.hypot(crossX - rimCenter.getX(), crossZ - rimCenter.getZ());
        return radial <= SCORING_CYLINDER_RADIUS;
    }
}
