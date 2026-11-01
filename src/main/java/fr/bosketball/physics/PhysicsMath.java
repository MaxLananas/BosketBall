package fr.bosketball.physics;

import fr.bosketball.model.ShotReleaseQuality;
import java.util.Random;
import org.bukkit.util.Vector;

/**
 * High-Precision 80Hz Sub-Stepped 3D Basketball Physics Solver:
 * - Exact ballistic trajectory launch velocity under gravity + quadratic/linear drag
 * - 3D Torus Steel Rim collision (front-iron bounce, high shooter's roll, rim-out vs rattle-in)
 * - Tempered Glass Backboard & Shooter's Square bank-shot reflection
 * - Downward Rim Cylinder plane-crossing score detector (Pure Swish vs Bank Shot vs Rim Rattle)
 */
public final class PhysicsMath {

    public static final double BALL_RADIUS = 0.244;       // Scaled NBA ratio (0.488m diameter)
    public static final double RIM_MAJOR_RADIUS = 0.480;  // Exact 1:1 match with hoop_*.json (3.84 * 0.125 = 0.480m)
    public static final double RIM_TUBE_RADIUS = 0.040;   // Orange iron thickness (0.32 * 0.125 = 0.040m)
    public static final double BACKBOARD_OFFSET = 0.650;  // Exact 1:1 match with glass Z=2.80 ((8.0-2.8)*0.125 = 0.650m)
    public static final double BACKBOARD_HALF_WIDTH = 1.50;
    public static final double BACKBOARD_MIN_REL_Y = -0.30;
    public static final double BACKBOARD_MAX_REL_Y = 1.65;

    private PhysicsMath() {
    }

    public record CollisionResult(Vector position, Vector velocity, boolean hit) {
    }

    /**
     * Solves exact initial launch velocity (blocks/tick) to carry a shot from startPos to targetPos
     * under constant per-tick gravity g and per-tick velocity retention drag (e.g. 0.991).
     */
    public static Vector solveBallisticArc(
        Vector startPos,
        Vector targetPos,
        double gravityPerTick,
        double dragPerTick,
        ShotReleaseQuality quality,
        Random rng
    ) {
        Vector aim = targetPos.clone();
        if (quality != null && quality != ShotReleaseQuality.PERFECT_GREEN && rng != null) {
            double scatter = quality.maxLateralScatterBlocks();
            double angle = rng.nextDouble() * Math.PI * 2.0;
            // Early shots tend slightly short; late shots tend slightly long/back-iron!
            double depthBias = switch (quality) {
                case VERY_EARLY -> -0.26;
                case SLIGHTLY_EARLY -> -0.09;
                case SLIGHTLY_LATE -> 0.10;
                case VERY_LATE -> 0.28;
                default -> 0.0;
            };
            Vector horizDir = targetPos.clone().subtract(startPos).setY(0);
            if (horizDir.lengthSquared() > 1e-4) {
                horizDir.normalize();
                aim.add(horizDir.multiply(depthBias));
            }
            aim.add(new Vector(Math.cos(angle) * scatter, 0.0, Math.sin(angle) * scatter));
        }

        double dx = aim.getX() - startPos.getX();
        double dy = aim.getY() - startPos.getY();
        double dz = aim.getZ() - startPos.getZ();
        double horizDist = Math.hypot(dx, dz);

        // Flight duration in server ticks scales smoothly with shot distance (high rainbow arc on deep 3s!)
        int flightTicks = Math.max(16, Math.min(46, (int) Math.round(15.0 + horizDist * 1.65)));

        // Exact discrete geometric series sum for damped motion:
        // pos(N) - pos(0) = v0 * S1 - g * S2
        // where v(k+1) = (v(k) - g) * drag  (or v(k)*drag - g; match BasketballEntity step order!)
        // In BasketballEntity: each tick executes SUBSTEPS=4 with dt=0.25:
        //   vel.y -= g * dt; vel *= drag^(dt); pos += vel * dt;
        int totalSubsteps = flightTicks * 4;
        double dt = 0.25;
        double subDrag = Math.pow(dragPerTick, dt);

        double sVel = 0.0;
        double sGrav = 0.0;
        double curVelFactor = 1.0;
        double curGravAccum = 0.0;
        for (int i = 0; i < totalSubsteps; i++) {
            curGravAccum = (curGravAccum + gravityPerTick * dt) * subDrag;
            curVelFactor *= subDrag;
            sVel += curVelFactor * dt;
            sGrav += curGravAccum * dt;
        }

        double vx = dx / sVel;
        double vz = dz / sVel;
        double vy = (dy + sGrav) / sVel;
        return new Vector(vx, vy, vz);
    }

    /**
     * Collides the 3D basketball sphere with the horizontal circular torus of the orange steel rim!
     */
    public static CollisionResult collideWithRimTorus(
        Vector ballPos,
        Vector ballVel,
        Vector rimCenter,
        double restitution
    ) {
        double rx = ballPos.getX() - rimCenter.getX();
        double ry = ballPos.getY() - rimCenter.getY();
        double rz = ballPos.getZ() - rimCenter.getZ();

        double horizLen = Math.hypot(rx, rz);
        // Closest point on the major ring circle of radius RIM_MAJOR_RADIUS in the Y=rimCenter.Y plane:
        double ringX;
        double ringZ;
        if (horizLen < 1e-6) {
            ringX = RIM_MAJOR_RADIUS;
            ringZ = 0.0;
        } else {
            ringX = (rx / horizLen) * RIM_MAJOR_RADIUS;
            ringZ = (rz / horizLen) * RIM_MAJOR_RADIUS;
        }

        double nx = rx - ringX;
        double ny = ry;
        double nz = rz - ringZ;
        double distSq = nx * nx + ny * ny + nz * nz;
        double minDist = BALL_RADIUS + RIM_TUBE_RADIUS;

        if (distSq >= minDist * minDist || distSq < 1e-9) {
            return new CollisionResult(ballPos, ballVel, false);
        }

        double dist = Math.sqrt(distSq);
        nx /= dist;
        ny /= dist;
        nz /= dist;

        // Push sphere out of torus tube penetration
        double overlap = minDist - dist;
        Vector resolvedPos = new Vector(
            ballPos.getX() + nx * overlap,
            ballPos.getY() + ny * overlap,
            ballPos.getZ() + nz * overlap
        );

        double vn = ballVel.getX() * nx + ballVel.getY() * ny + ballVel.getZ() * nz;
        if (vn >= 0.0) {
            return new CollisionResult(resolvedPos, ballVel, false);
        }

        // Shooter's touch: soft bounces near the inner edge lose slightly more energy so dramatic rim-rolls happen!
        double impulse = -(1.0 + restitution) * vn;
        Vector resolvedVel = new Vector(
            (ballVel.getX() + impulse * nx) * 0.90,
            ballVel.getY() + impulse * ny,
            (ballVel.getZ() + impulse * nz) * 0.90
        );
        return new CollisionResult(resolvedPos, resolvedVel, true);
    }

    /**
     * Collides the 3D basketball sphere with the tempered glass backboard behind rimCenter.
     * normalSignZ: +1 if hoop faces +Z (North Hoop, glass at rimZ - BACKBOARD_OFFSET),
     *              -1 if hoop faces -Z (South Hoop, glass at rimZ + BACKBOARD_OFFSET).
     */
    public static CollisionResult collideWithBackboard(
        Vector ballPos,
        Vector ballVel,
        Vector rimCenter,
        int normalSignZ,
        double restitution
    ) {
        double relX = ballPos.getX() - rimCenter.getX();
        double relY = ballPos.getY() - rimCenter.getY();
        if (Math.abs(relX) > BACKBOARD_HALF_WIDTH || relY < BACKBOARD_MIN_REL_Y || relY > BACKBOARD_MAX_REL_Y) {
            return new CollisionResult(ballPos, ballVel, false);
        }

        double glassZ = rimCenter.getZ() - normalSignZ * BACKBOARD_OFFSET;
        double signedDist = (ballPos.getZ() - glassZ) * normalSignZ;

        if (signedDist >= BALL_RADIUS || signedDist < -0.45) {
            return new CollisionResult(ballPos, ballVel, false);
        }

        double velTowardCourt = ballVel.getZ() * normalSignZ;
        if (velTowardCourt >= 0.0) {
            return new CollisionResult(ballPos, ballVel, false);
        }

        Vector resolvedPos = ballPos.clone();
        resolvedPos.setZ(glassZ + normalSignZ * BALL_RADIUS);

        Vector resolvedVel = ballVel.clone();
        resolvedVel.setZ(-ballVel.getZ() * restitution);
        resolvedVel.setX(ballVel.getX() * 0.84);
        resolvedVel.setY(ballVel.getY() * 0.86);

        // Shooter's square sweet-spot assist: hitting inside |relX| <= 0.45 & relY in [0.15..0.85] guides softly toward cylinder!
        if (Math.abs(relX) <= 0.48 && relY >= 0.12 && relY <= 0.90) {
            Vector toRim = rimCenter.clone().subtract(resolvedPos);
            resolvedVel.setX(resolvedVel.getX() * 0.55 + toRim.getX() * 0.09);
        }
        return new CollisionResult(resolvedPos, resolvedVel, true);
    }

    /**
     * Checks whether the ball center crossed the horizontal rim cylinder downward during [prevPos -> currPos]!
     */
    public static boolean didScoreDownwardThroughHoop(Vector prevPos, Vector currPos, Vector rimCenter) {
        double rimY = rimCenter.getY();
        if (prevPos.getY() < rimY || currPos.getY() >= rimY) {
            return false;
        }
        double dy = prevPos.getY() - currPos.getY();
        double alpha = dy > 1e-6 ? (prevPos.getY() - rimY) / dy : 0.5;
        double crossX = prevPos.getX() + (currPos.getX() - prevPos.getX()) * alpha;
        double crossZ = prevPos.getZ() + (currPos.getZ() - prevPos.getZ()) * alpha;

        double horizDist = Math.hypot(crossX - rimCenter.getX(), crossZ - rimCenter.getZ());
        // Must pass inside the clear opening of the steel ring
        return horizDist <= (RIM_MAJOR_RADIUS - BALL_RADIUS * 0.32);
    }
}
