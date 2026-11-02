package fr.bosketball.animation;

import fr.bosketball.model.DunkStyle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure 6-Bone Basketball Character Kinematics Engine (Head, Torso, RightArm, LeftArm, RightLeg, LeftLeg).
 * Drives genuine custom Walk, Sprint-Dribble, Crossover, Jump/Rebound, Shot-Gather, Gooseneck Throw,
 * Laser Pass, Steal-Swipe, Block-Reject, and Signature Slam Dunk animations!
 *
 * Coordinate convention (verified for ItemDisplayTransform.NONE where model -Z = character forward):
 *   - forwardPitchRad > 0 swings a hanging limb FORWARD (and UP above shoulder when > +90°!)
 *   - forwardPitchRad < 0 swings a hanging limb BACKWARD
 *   - outwardRollRad > 0 splays Right limb right / Left limb left
 */
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

    /**
     * Evaluates the full 6-bone articulated pose for the current frame.
     *
     * @param bodyYawDeg         character horizontal facing yaw (Minecraft degrees: 0=+Z, 90=-X, 180=-Z, -90=+X)
     * @param lookPitchDeg       player camera pitch (-90 up .. +90 down)
     * @param horizSpeedBpt      horizontal speed in blocks/tick (walk ~0.11, sprint ~0.22+)
     * @param verticalVelBpt     vertical velocity in blocks/tick
     * @param onGround           true when feet are on court
     * @param stridePhaseRad     accumulated locomotion cycle phase in radians
     * @param dribbleWave01      [0..1] live basketball bounce height wave (1 = hand height, 0 = floor bounce)
     * @param holdingBall        true when player currently possesses the ball
     * @param offHandDribble     true when dribbling in left hand (toggles on Crossover!)
     * @param shotChargeProgress [-1 if not charging, else 0.0..1.15]
     * @param landingTicks       [0..6] cushioned knee-bend ticks right after landing from a jump
     * @param activeClip         active transient clip (Throw, Pass, Crossover, Dunk, Steal, Block)
     * @param clipProgress01     [0..1] normalized progress through activeClip
     * @param dunkStyle          selected signature dunk style when activeClip == SLAM_DUNK
     * @param idleTick           monotonic tick counter for subtle idle breathing
     */
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
        double speedNorm = Math.min(1.35, horizSpeedBpt / 0.16);
        boolean moving = horizSpeedBpt > 0.018;
        boolean airborne = !onGround || Math.abs(verticalVelBpt) > 0.09;

        // Anatomical joint heights above player feet (in blocks)
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

        // 1. Base Locomotion: Idle vs Walk / Sprint Stride Cycle
        if (airborne) {
            stateName = "JUMP";
            float riseFactor = (float) Math.max(-1.0, Math.min(1.0, verticalVelBpt / 0.35));
            torsoLeanForwardRad = -0.10f * riseFactor + 0.06f;
            // Athletic mid-air scissor-tuck leg pose
            rLegPitch = 0.42f * riseFactor - 0.18f;
            lLegPitch = -0.34f - 0.16f * riseFactor;
            rLegRoll = 0.08f;
            lLegRoll = -0.08f;
            // Aerial balance / rebound reach arms
            rArmPitch = 0.85f + 0.45f * Math.max(0f, riseFactor);
            lArmPitch = 0.78f + 0.42f * Math.max(0f, riseFactor);
            rArmRoll = 0.26f;
            lArmRoll = -0.26f;
        } else if (moving) {
            stateName = horizSpeedBpt > 0.165 ? "SPRINT" : "WALK";
            float sinS = (float) Math.sin(stridePhaseRad);
            float cosS = (float) Math.cos(stridePhaseRad);
            float legAmp = (float) (Math.toRadians(44.0) * speedNorm);
            strideBob = (float) (Math.abs(cosS) * 0.055 * Math.min(1.0, speedNorm));
            torsoLeanForwardRad = (float) (0.11 * speedNorm);
            torsoTwistYawRad = sinS * 0.12f * (float) Math.min(1.0, speedNorm);

            rLegPitch = sinS * legAmp;
            lLegPitch = -sinS * legAmp;

            float armAmp = (float) (Math.toRadians(38.0) * speedNorm);
            rArmPitch = -sinS * armAmp + 0.12f;
            lArmPitch = sinS * armAmp + 0.12f;
            rArmRoll = 0.12f;
            lArmRoll = -0.12f;
        } else {
            // Subtle court-ready idle breathing
            float breath = (float) Math.sin(idleTick * 0.12);
            strideBob = breath * 0.018f;
            torsoLeanForwardRad = 0.06f;
            rArmPitch = 0.18f + breath * 0.04f;
            lArmPitch = 0.18f - breath * 0.04f;
            rArmRoll = 0.14f;
            lArmRoll = -0.14f;
            rLegRoll = 0.06f;
            lLegRoll = -0.06f;
        }

        // Landing knee-absorption cushion
        if (!airborne && landingTicks > 0) {
            float land01 = landingTicks / 6.0f;
            crouchDrop += 0.11f * land01;
            torsoLeanForwardRad += 0.14f * land01;
            rLegPitch += 0.22f * land01;
            lLegPitch -= 0.18f * land01;
        }

        // 2. Live Ball-Handler Dribble Pump (phase-locked to physical 3D basketball bounce!)
        if (holdingBall && shotChargeProgress < 0.0 && activeClip == ActionClip.NONE) {
            if (!airborne) {
                stateName = moving ? "DRIBBLE_RUN" : "DRIBBLE_STANCE";
            }
            // Pump arm from +78° (hand high over ball) down to +36° (pushing ball toward floor)
            float pumpPitch = (float) Math.toRadians(36.0 + 44.0 * Math.max(0.0, Math.min(1.0, dribbleWave01)));
            if (!offHandDribble) {
                rArmPitch = pumpPitch;
                rArmRoll = 0.14f;
                // Off-arm guards ball wide against steals
                lArmPitch = 0.52f;
                lArmRoll = -0.34f;
            } else {
                lArmPitch = pumpPitch;
                lArmRoll = -0.14f;
                rArmPitch = 0.52f;
                rArmRoll = 0.34f;
            }
        }

        // 3. Jump-Shot Gather & Forehead Set-Point (while holding right-click shot meter!)
        if (shotChargeProgress >= 0.0) {
            stateName = "SHOT_GATHER";
            float g = (float) Math.max(0.0, Math.min(1.0, shotChargeProgress));
            // Early gather coils knees, late gather rises toward release!
            float coil = (float) Math.sin(Math.min(1.0, g * 1.35) * Math.PI);
            if (!airborne) {
                crouchDrop += 0.11f * coil * (1.0f - 0.5f * g);
            }
            torsoLeanForwardRad = 0.08f * (1.0f - g) - 0.05f * g;
            // Shooting arm rises smoothly from waist (+55°) up to forehead set-point (+122°)!
            rArmPitch = (float) Math.toRadians(55.0 + 68.0 * g);
            rArmYaw = -0.18f * g;
            rArmRoll = 0.08f;
            // Guide hand cradles side of cubic basketball (+50° -> +108°)
            lArmPitch = (float) Math.toRadians(50.0 + 58.0 * g);
            lArmYaw = 0.26f * g;
            lArmRoll = -0.06f;
        }

        // 4. High-Priority Action Clips: Throw / Pass / Crossover / Dunk / Steal / Block!
        if (activeClip != null && activeClip != ActionClip.NONE) {
            float t = (float) Math.max(0.0, Math.min(1.0, clipProgress01));
            float easeOut = 1.0f - (1.0f - t) * (1.0f - t);
            float bell = (float) Math.sin(t * Math.PI);

            switch (activeClip) {
                case THROW_SHOT -> {
                    stateName = "THROW_SHOT";
                    // Iconic high NBA Gooseneck Follow-Through (+122° -> +154° high extension, then graceful drop)
                    float holdHigh = t < 0.65f ? (t / 0.65f) : (1.0f - (t - 0.65f) / 0.35f * 0.55f);
                    rArmPitch = (float) Math.toRadians(96.0 + 58.0 * Math.min(1.0f, holdHigh * 1.25f));
                    rArmYaw = -0.08f;
                    rArmRoll = 0.06f;
                    // Off-hand guide drops cleanly out to the side
                    lArmPitch = (float) Math.toRadians(92.0 - 42.0 * easeOut);
                    lArmRoll = -0.26f;
                    torsoLeanForwardRad = -0.06f * bell;
                }
                case THROW_PASS -> {
                    stateName = "THROW_PASS";
                    // Two-hand explosive chest-pass snap (+92° forward extension + splayed wrists + torso lunge!)
                    rArmPitch = (float) Math.toRadians(45.0 + 52.0 * bell);
                    lArmPitch = (float) Math.toRadians(45.0 + 52.0 * bell);
                    rArmRoll = 0.28f * easeOut + 0.08f;
                    lArmRoll = -0.28f * easeOut - 0.08f;
                    torsoLeanForwardRad += 0.22f * bell;
                }
                case CROSSOVER -> {
                    stateName = "CROSSOVER";
                    // Sharp ankle-breaker lateral hip & shoulder shift + low cross-body dribble whip!
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
                    // Fast perimeter reach-in swipe across chest height
                    torsoLeanForwardRad += 0.24f * bell;
                    torsoTwistYawRad = 0.34f * bell;
                    rArmPitch = (float) Math.toRadians(88.0 * bell + 20.0);
                    rArmYaw = -0.52f * bell;
                    rArmRoll = 0.18f;
                }
                case BLOCK_REACH -> {
                    stateName = "BLOCK_REACH";
                    // Towering vertical shot-rejection extension (+168° straight up toward rim!)
                    rArmPitch = (float) Math.toRadians(110.0 + 58.0 * bell);
                    lArmPitch = (float) Math.toRadians(95.0 + 48.0 * bell);
                    rArmRoll = 0.10f;
                    lArmRoll = -0.14f;
                }
                case SLAM_DUNK -> {
                    stateName = "SLAM_DUNK";
                    DunkStyle ds = dunkStyle == null ? DunkStyle.TOMAHAWK : dunkStyle;
                    if (ds == DunkStyle.WINDMILL) {
                        // Full 360° windmill arm circle ending in rim smash!
                        rArmPitch = (float) (t * Math.PI * 2.0 + Math.toRadians(95.0));
                        lArmPitch = 0.65f;
                        lArmRoll = -0.35f;
                    } else if (ds == DunkStyle.SPIN_360) {
                        torsoTwistYawRad = (float) (t * Math.PI * 2.0);
                        rArmPitch = (float) Math.toRadians(135.0 - 40.0 * easeOut);
                        lArmPitch = (float) Math.toRadians(135.0 - 40.0 * easeOut);
                    } else {
                        // Cock back behind head (-25°..+165°) -> thunderous rim slam (+95°)!
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

        // 5. Assemble Final World-Space Offsets & Quaternions around bodyYawDeg!
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

        // Lateral offsets in character local space (+X = character's left in MC yaw=0(+Z), -X = character's right)
        Vector3f rShoulderOff = rotateYaw(new Vector3f(-0.34f, shoulderY, 0.0f), bodyYawDeg);
        Vector3f lShoulderOff = rotateYaw(new Vector3f(+0.34f, shoulderY, 0.0f), bodyYawDeg);
        Vector3f rHipOff = rotateYaw(new Vector3f(-0.13f, pelvisY, 0.0f), bodyYawDeg);
        Vector3f lHipOff = rotateYaw(new Vector3f(+0.13f, pelvisY, 0.0f), bodyYawDeg);
        Vector3f torsoOff = rotateYaw(new Vector3f(0.0f, torsoCenterY, 0.0f), bodyYawDeg);
        Vector3f headOff = rotateYaw(new Vector3f(0.0f, headY, (float) Math.sin(torsoLeanForwardRad) * 0.08f), bodyYawDeg);

        // Note: limbs hang along -Y from shoulder/hip pivots, so rotateX(-pitch) swings them FORWARD (+Z at yaw=0) and UP!
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

    /**
     * Rotates a character-local offset (local +Z = forward, local -X = right shoulder, local +X = left shoulder)
     * into Minecraft world space for a given entity yaw (0° = +Z, 90° = -X, 180° = -Z, -90° = +X).
     */
    public static Vector3f rotateYaw(Vector3f local, float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);
        float wx = local.x * cos - local.z * sin;
        float wz = local.x * sin + local.z * cos;
        return new Vector3f(wx, local.y, wz);
    }
}
