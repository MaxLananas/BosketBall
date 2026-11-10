package fr.bosketball.animation;

import fr.bosketball.model.DunkStyle;
import fr.bosketball.util.Guards;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class AnimationKinematics {

    public enum ActionClip {
        NONE(0),
        THROW_SHOT(14),
        THROW_PASS(10),
        CROSSOVER(11),
        STEAL_SWIPE(9),
        BLOCK_REACH(12),
        SLAM_DUNK(16);

        private final int durationTicks;

        ActionClip(int durationTicks) {
            this.durationTicks = durationTicks;
        }

        public int durationTicks() {
            return durationTicks;
        }
    }

    public record BonePose(Vector3f worldOffset, Quaternionf rotation, float headPitchDeg, float headYawOffsetDeg) {
    }

    public record RigPoseSnapshot(
        BonePose head,
        BonePose torso,
        BonePose rightArm,
        BonePose leftArm,
        BonePose rightLeg,
        BonePose leftLeg,
        String activeStateName
    ) {
    }

    private AnimationKinematics() {
    }

    public static RigPoseSnapshot evaluate(
        float bodyYawDeg,
        float lookPitchDeg,
        double horizSpeedBpt,
        double verticalVelBpt,
        boolean onGround,
        double stridePhaseRad,
        double dribbleWave01,
        boolean holdingBall,
        boolean offHandDribble,
        double shotChargeProgress,
        int landingTicks,
        ActionClip activeClip,
        double clipProgress01,
        DunkStyle dunkStyle,
        int idleTick
    ) {
        return evaluate(
            bodyYawDeg,
            lookPitchDeg,
            horizSpeedBpt,
            verticalVelBpt,
            onGround,
            false,
            stridePhaseRad,
            dribbleWave01,
            holdingBall,
            offHandDribble,
            shotChargeProgress,
            landingTicks,
            activeClip,
            clipProgress01,
            dunkStyle,
            idleTick
        );
    }

    public static RigPoseSnapshot evaluate(
        float bodyYawDeg,
        float lookPitchDeg,
        double horizSpeedBpt,
        double verticalVelBpt,
        boolean onGround,
        boolean sneaking,
        double stridePhaseRad,
        double dribbleWave01,
        boolean holdingBall,
        boolean offHandDribble,
        double shotChargeProgress,
        int landingTicks,
        ActionClip activeClip,
        double clipProgress01,
        DunkStyle dunkStyle,
        int idleTick
    ) {
        // Round 42 — the whole rig is driven by these five numbers, and every one of them comes
        // from a live entity: a NaN velocity (division by zero somewhere upstream, a corrupted
        // Vector) used to build NaN quaternions, which the client rejects for as long as it lasts.
        // Sanitising at the boundary makes one bad tick a one-frame glitch instead of a dead rig.
        horizSpeedBpt = Guards.finite(horizSpeedBpt, 0.0);
        verticalVelBpt = Guards.finite(verticalVelBpt, 0.0);
        stridePhaseRad = Guards.finite(stridePhaseRad, 0.0);
        dribbleWave01 = Guards.finite(dribbleWave01, 0.0);
        shotChargeProgress = Guards.finite(shotChargeProgress, -1.0);
        bodyYawDeg = Guards.finite(bodyYawDeg, 0.0f);
        lookPitchDeg = Guards.finite(lookPitchDeg, 0.0f);
        clipProgress01 = Guards.finite(clipProgress01, 0.0);

        double speedNorm = Guards.clampLoHi(horizSpeedBpt / 0.155, 0.0, 1.38);
        boolean moving = horizSpeedBpt > 0.016;
        boolean airborne = !onGround || Math.abs(verticalVelBpt) > 0.085;

        float crouchDrop = 0.0f;
        float strideBob = 0.0f;
        float torsoLeanForwardRad = 0.0f;
        float torsoTwistYawRad = 0.0f;
        float torsoRollRad = 0.0f;

        float rArmPitch = 0.10f, rArmYaw = 0.0f, rArmRoll = 0.10f;
        float lArmPitch = 0.10f, lArmYaw = 0.0f, lArmRoll = -0.10f;
        float rLegPitch = 0.0f, rLegRoll = 0.04f;
        float lLegPitch = 0.0f, lLegRoll = -0.04f;
        String stateName = "IDLE";

        if (airborne) {
            stateName = "JUMP";
            float riseFactor = (float) Math.max(-1.0, Math.min(1.0, verticalVelBpt / 0.34));
            torsoLeanForwardRad = -0.12f * riseFactor + 0.08f;

            rLegPitch = 0.56f * riseFactor - 0.16f;
            lLegPitch = -0.42f - 0.18f * riseFactor;
            rLegRoll = 0.09f;
            lLegRoll = -0.09f;

            rArmPitch = 0.92f + 0.48f * Math.max(0f, riseFactor);
            lArmPitch = 0.84f + 0.44f * Math.max(0f, riseFactor);
            rArmRoll = 0.28f;
            lArmRoll = -0.28f;
        } else if (moving) {
            stateName = horizSpeedBpt > 0.165 ? "SPRINT" : "WALK";
            float sinS = (float) Math.sin(stridePhaseRad);
            float cosS = (float) Math.cos(stridePhaseRad);
            float legAmp = (float) (Math.toRadians(48.0) * speedNorm);
            strideBob = (float) (Math.abs(cosS) * 0.068 * Math.min(1.0, speedNorm));
            torsoLeanForwardRad = (float) (0.13 * speedNorm);
            torsoTwistYawRad = sinS * 0.15f * (float) Math.min(1.0, speedNorm);

            rLegPitch = sinS * legAmp;
            lLegPitch = -sinS * legAmp;

            float armAmp = (float) (Math.toRadians(42.0) * speedNorm);
            rArmPitch = -sinS * armAmp + 0.14f;
            lArmPitch = sinS * armAmp + 0.14f;
            rArmRoll = 0.13f;
            lArmRoll = -0.13f;
        } else {

            float breath = (float) Math.sin(idleTick * 0.12);
            strideBob = breath * 0.020f;
            torsoLeanForwardRad = 0.06f;
            rArmPitch = 0.18f + breath * 0.045f;
            lArmPitch = 0.18f - breath * 0.045f;
            rArmRoll = 0.14f;
            lArmRoll = -0.14f;
            rLegRoll = 0.06f;
            lLegRoll = -0.06f;
        }

        if (!airborne && sneaking) {
            stateName = moving ? "DEFENSIVE_SLIDE" : "DEFENSIVE_CROUCH";
            crouchDrop += 0.22f;
            torsoLeanForwardRad += 0.26f;
            rLegPitch += 0.24f;
            lLegPitch -= 0.20f;
            rLegRoll = 0.18f;
            lLegRoll = -0.18f;
            if (!holdingBall) {

                rArmPitch = 0.68f;
                lArmPitch = 0.68f;
                rArmRoll = 0.48f;
                lArmRoll = -0.48f;
            }
        }

        if (!airborne && landingTicks > 0) {
            float land01 = Guards.clampLoHi(landingTicks, 0, 6) / 6.0f;
            crouchDrop += 0.11f * land01;
            torsoLeanForwardRad += 0.14f * land01;
            rLegPitch += 0.22f * land01;
            lLegPitch -= 0.18f * land01;
        }

        if (holdingBall && shotChargeProgress < 0.0 && activeClip == ActionClip.NONE) {
            if (!airborne) {
                stateName = moving ? "DRIBBLE_RUN" : "DRIBBLE_STANCE";
            }

            float pumpPitch = (float) Math.toRadians(36.0 + 44.0 * Math.max(0.0, Math.min(1.0, dribbleWave01)));
            if (!offHandDribble) {
                rArmPitch = pumpPitch;
                rArmRoll = 0.14f;

                lArmPitch = 0.52f;
                lArmRoll = -0.34f;
            } else {
                lArmPitch = pumpPitch;
                lArmRoll = -0.14f;
                rArmPitch = 0.52f;
                rArmRoll = 0.34f;
            }
        }

        if (shotChargeProgress >= 0.0) {
            stateName = "SHOT_GATHER";
            float g = (float) Math.max(0.0, Math.min(1.0, shotChargeProgress));

            float coil = (float) Math.sin(Math.min(1.0, g * 1.35) * Math.PI);
            if (!airborne) {
                crouchDrop += 0.11f * coil * (1.0f - 0.5f * g);
            }
            torsoLeanForwardRad = 0.08f * (1.0f - g) - 0.05f * g;

            rArmPitch = (float) Math.toRadians(55.0 + 68.0 * g);
            rArmYaw = -0.18f * g;
            rArmRoll = 0.08f;

            lArmPitch = (float) Math.toRadians(50.0 + 58.0 * g);
            lArmYaw = 0.26f * g;
            lArmRoll = -0.06f;
        }

        if (activeClip != null && activeClip != ActionClip.NONE) {
            float t = (float) Math.max(0.0, Math.min(1.0, clipProgress01));
            float easeOut = 1.0f - (1.0f - t) * (1.0f - t);
            float bell = (float) Math.sin(t * Math.PI);

            switch (activeClip) {
                case THROW_SHOT -> {
                    stateName = "THROW_SHOT";

                    float holdHigh = t < 0.65f ? (t / 0.65f) : (1.0f - (t - 0.65f) / 0.35f * 0.55f);
                    rArmPitch = (float) Math.toRadians(96.0 + 58.0 * Math.min(1.0f, holdHigh * 1.25f));
                    rArmYaw = -0.08f;
                    rArmRoll = 0.06f;

                    lArmPitch = (float) Math.toRadians(92.0 - 42.0 * easeOut);
                    lArmRoll = -0.26f;
                    torsoLeanForwardRad = -0.06f * bell;
                }
                case THROW_PASS -> {
                    stateName = "THROW_PASS";

                    rArmPitch = (float) Math.toRadians(45.0 + 52.0 * bell);
                    lArmPitch = (float) Math.toRadians(45.0 + 52.0 * bell);
                    rArmRoll = 0.28f * easeOut + 0.08f;
                    lArmRoll = -0.28f * easeOut - 0.08f;
                    torsoLeanForwardRad += 0.22f * bell;
                }
                case CROSSOVER -> {
                    stateName = "CROSSOVER";

                    float dir = offHandDribble ? -1.0f : 1.0f;
                    crouchDrop += 0.12f * bell;
                    torsoRollRad = 0.24f * bell * dir;
                    torsoLeanForwardRad += 0.20f * bell;
                    rArmPitch = (float) Math.toRadians(62.0 * bell + 24.0);
                    lArmPitch = (float) Math.toRadians(62.0 * bell + 24.0);
                    rArmYaw = -0.45f * bell;
                    lArmYaw = 0.45f * bell;
                }
                case STEAL_SWIPE -> {
                    stateName = "STEAL_SWIPE";

                    torsoLeanForwardRad += 0.24f * bell;
                    torsoTwistYawRad = 0.34f * bell;
                    rArmPitch = (float) Math.toRadians(88.0 * bell + 20.0);
                    rArmYaw = -0.52f * bell;
                    rArmRoll = 0.18f;
                }
                case BLOCK_REACH -> {
                    stateName = "BLOCK_REACH";

                    rArmPitch = (float) Math.toRadians(110.0 + 58.0 * bell);
                    lArmPitch = (float) Math.toRadians(95.0 + 48.0 * bell);
                    rArmRoll = 0.10f;
                    lArmRoll = -0.14f;
                }
                case SLAM_DUNK -> {
                    stateName = "SLAM_DUNK";
                    DunkStyle ds = dunkStyle == null ? DunkStyle.TOMAHAWK : dunkStyle;
                    if (ds == DunkStyle.WINDMILL) {

                        rArmPitch = (float) (t * Math.PI * 2.0 + Math.toRadians(95.0));
                        lArmPitch = 0.65f;
                        lArmRoll = -0.35f;
                    } else if (ds == DunkStyle.SPIN_360) {
                        torsoTwistYawRad = (float) (t * Math.PI * 2.0);
                        rArmPitch = (float) Math.toRadians(135.0 - 40.0 * easeOut);
                        lArmPitch = (float) Math.toRadians(135.0 - 40.0 * easeOut);
                    } else {

                        float dunkCurve = t < 0.48f
                            ? (float) Math.toRadians(115.0 + 52.0 * (t / 0.48f))
                            : (float) Math.toRadians(167.0 - 72.0 * ((t - 0.48f) / 0.52f));
                        rArmPitch = dunkCurve;
                        lArmPitch = ds == DunkStyle.METEOR_JAM ? dunkCurve : 0.72f;
                        torsoLeanForwardRad = t < 0.48f ? -0.18f : 0.22f;
                    }
                    rLegPitch = -0.45f * bell;
                    lLegPitch = 0.32f * bell;
                }
                default -> {
                }
            }
        }

        float baseYawRad = (float) -Math.toRadians(bodyYawDeg);
        float totalTorsoYaw = baseYawRad + torsoTwistYawRad;

        float pelvisY = 0.73f - crouchDrop + strideBob;
        float torsoCenterY = 1.09f - crouchDrop + strideBob;
        float shoulderY = 1.38f - crouchDrop + strideBob;
        float headY = 1.44f - crouchDrop + strideBob;

        Quaternionf torsoQuat = new Quaternionf()
            .rotateY(totalTorsoYaw)
            .rotateX(torsoLeanForwardRad)
            .rotateZ(torsoRollRad);

        Vector3f rShoulderOff = rotateYaw(new Vector3f(-0.34f, shoulderY, 0.0f), bodyYawDeg);
        Vector3f lShoulderOff = rotateYaw(new Vector3f(+0.34f, shoulderY, 0.0f), bodyYawDeg);
        Vector3f rHipOff = rotateYaw(new Vector3f(-0.13f, pelvisY, 0.0f), bodyYawDeg);
        Vector3f lHipOff = rotateYaw(new Vector3f(+0.13f, pelvisY, 0.0f), bodyYawDeg);
        Vector3f torsoOff = rotateYaw(new Vector3f(0.0f, torsoCenterY, 0.0f), bodyYawDeg);
        Vector3f headOff = rotateYaw(new Vector3f(0.0f, headY, (float) Math.sin(torsoLeanForwardRad) * 0.08f), bodyYawDeg);

        Quaternionf rArmQuat = new Quaternionf()
            .rotateY(totalTorsoYaw + rArmYaw)
            .rotateX(-rArmPitch)
            .rotateZ(rArmRoll);

        Quaternionf lArmQuat = new Quaternionf()
            .rotateY(totalTorsoYaw + lArmYaw)
            .rotateX(-lArmPitch)
            .rotateZ(lArmRoll);

        Quaternionf rLegQuat = new Quaternionf()
            .rotateY(baseYawRad)
            .rotateX(-rLegPitch)
            .rotateZ(rLegRoll);

        Quaternionf lLegQuat = new Quaternionf()
            .rotateY(baseYawRad)
            .rotateX(-lLegPitch)
            .rotateZ(lLegRoll);

        float clampedHeadPitch = Math.max(-55.0f, Math.min(55.0f, lookPitchDeg));
        return new RigPoseSnapshot(
            new BonePose(headOff, new Quaternionf(), clampedHeadPitch, (float) Math.toDegrees(torsoTwistYawRad * 0.35)),
            new BonePose(torsoOff, torsoQuat, 0f, 0f),
            new BonePose(rShoulderOff, rArmQuat, 0f, 0f),
            new BonePose(lShoulderOff, lArmQuat, 0f, 0f),
            new BonePose(rHipOff, rLegQuat, 0f, 0f),
            new BonePose(lHipOff, lLegQuat, 0f, 0f),
            stateName
        );
    }

    public static Vector3f rotateYaw(Vector3f local, float yawDeg) {
        if (local == null) {
            return new Vector3f();
        }
        if (!Float.isFinite(yawDeg)) {
            yawDeg = 0.0f;
        }
        double rad = Math.toRadians(yawDeg);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);
        float wx = local.x * cos - local.z * sin;
        float wz = local.x * sin + local.z * cos;
        return new Vector3f(wx, local.y, wz);
    }
}
