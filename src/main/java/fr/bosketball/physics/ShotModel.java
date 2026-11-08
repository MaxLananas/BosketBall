package fr.bosketball.physics;

import fr.bosketball.model.ShotReleaseQuality;

/**
 * One auditable probability model for a jump shot. It intentionally returns an analytical
 * probability rather than making Bukkit or match-session decisions; the physics solver uses the
 * same score to scale its deterministic, seeded shot scatter.
 */
public final class ShotModel {

    /** Absolute lower bound: even a hopeless attempt retains a small, visible chance to fall. */
    public static final double MIN_PROBABILITY = 0.02;

    /** Absolute upper bound: no input combination makes a shot mathematically automatic. */
    public static final double MAX_PROBABILITY = 0.99;

    // Open close-range calibration; the polynomial that follows adds range-dependent difficulty.
    private static final double BASE_CLOSE_RANGE_PROBABILITY = 0.96;
    private static final double DISTANCE_REFERENCE_BLOCKS = 2.0;

    // The linear and quadratic terms make deep attempts lose probability faster than short looks.
    private static final double DISTANCE_LINEAR_PENALTY_PER_BLOCK = 0.018;
    private static final double DISTANCE_QUADRATIC_PENALTY_PER_BLOCK_SQUARED = 0.003;
    private static final double MAX_DISTANCE_BLOCKS = 40.0;

    // Contest is a nonlinear penalty so the first defender matters without making weak contests binary.
    private static final double CONTEST_PENALTY = 0.36;
    private static final double CONTEST_EXPONENT = 1.15;

    // Fatigue is deliberately convex: light tiredness is recoverable, while a full match load matters.
    private static final double FATIGUE_PENALTY = 0.18;
    private static final double FATIGUE_EXPONENT = 1.35;

    // An airborne release is a modest balance penalty; dunking uses its separate, positive airborne rule.
    private static final double AIRBORNE_PENALTY = 0.025;

    // The 2K timing tests show a substantial but setup-dependent cost for an early offset.
    private static final double SLIGHTLY_EARLY_PENALTY = 0.20;
    private static final double SLIGHTLY_LATE_PENALTY = 0.21;
    private static final double VERY_EARLY_PENALTY = 0.34;
    private static final double VERY_LATE_PENALTY = 0.36;

    // Aim error grows quadratically; the cap prevents extreme input from overflowing the score.
    private static final double AIM_ERROR_PENALTY_PER_DEGREE_SQUARED = 0.0015;
    private static final double MAX_AIM_ERROR_DEGREES = 30.0;
    private static final double MAX_AIM_ERROR_PENALTY = 0.45;

    // These terms retain the established, bounded physical spread while routing it through this model.
    private static final double RANGE_PRESSURE_SCALE = 0.050;
    private static final double RANGE_PRESSURE_START_BLOCKS = 2.6;
    private static final double RANGE_PRESSURE_EXPONENT = 1.30;
    private static final double NON_GREEN_RANGE_MULTIPLIER = 1.30;
    private static final double CONTEST_PRESSURE_BASE = 0.50;
    private static final double CONTEST_CLOSE_RANGE_BLOCKS = 6.0;
    private static final double CONTEST_CLOSE_RANGE_GAIN = 0.12;
    private static final double CONTEST_JITTER_THRESHOLD = 0.08;
    private static final double GREEN_CONTEST_JITTER = 0.14;
    private static final double OTHER_CONTEST_JITTER = 0.34;
    private static final double MICRO_SCATTER_CAP_BLOCKS = 0.10;
    private static final double MICRO_SCATTER_RELEASE_SCALE = 0.65;
    private static final double RELEASE_SPREAD_MULTIPLIER = 2.3;
    private static final double RELEASE_SPREAD_BASE_BLOCKS = 0.05;
    private static final double RELEASE_SPREAD_PER_BLOCK = 0.05;
    static final double CONTEST_JITTER_DIAMETER_FACTOR = 2.0;
    static final double RANGE_DEPTH_SCATTER_RATIO = 0.70;
    static final double RELEASE_DEPTH_SCATTER_RATIO = 0.80;
    private static final double MODEL_PROBABILITY_SCATTER_GAIN = 0.25;

    // SplitMix64 constants give stable, independent replay seeds for numbered shot events.
    private static final long SEED_SEQUENCE_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long SEED_MIX_MULTIPLIER_ONE = 0xBF58476D1CE4E5B9L;
    private static final long SEED_MIX_MULTIPLIER_TWO = 0x94D049BB133111EBL;
    private static final int SEED_MIX_SHIFT_ONE = 30;
    private static final int SEED_MIX_SHIFT_TWO = 27;
    private static final int SEED_MIX_SHIFT_THREE = 31;

    private ShotModel() {
    }

    /**
     * All factors used by the common probability model. Invalid numeric inputs are converted to
     * conservative values at construction so no downstream caller can inject NaN or infinity.
     */
    public record Input(
        double distanceBlocks,
        double contest,
        double fatigue,
        ShotReleaseQuality releaseQuality,
        boolean airborne,
        double aimErrorDegrees
    ) {
        public Input {
            distanceBlocks = finiteOr(distanceBlocks, MAX_DISTANCE_BLOCKS);
            distanceBlocks = Math.clamp(distanceBlocks, 0.0, MAX_DISTANCE_BLOCKS);
            contest = Math.clamp(finiteOr(contest, 1.0), 0.0, 1.0);
            fatigue = Math.clamp(finiteOr(fatigue, 1.0), 0.0, 1.0);
            releaseQuality = releaseQuality == null ? ShotReleaseQuality.VERY_EARLY : releaseQuality;
            aimErrorDegrees = Math.abs(finiteOr(aimErrorDegrees, MAX_AIM_ERROR_DEGREES));
            aimErrorDegrees = Math.min(MAX_AIM_ERROR_DEGREES, aimErrorDegrees);
        }
    }

    /** Returns a make probability in the closed interval [0.02, 0.99] for every input. */
    public static double makeProbability(Input input) {
        Input safe = input == null
            ? new Input(MAX_DISTANCE_BLOCKS, 1.0, 1.0, ShotReleaseQuality.VERY_EARLY, true, MAX_AIM_ERROR_DEGREES)
            : input;

        double range = Math.max(0.0, safe.distanceBlocks() - DISTANCE_REFERENCE_BLOCKS);
        double probability = BASE_CLOSE_RANGE_PROBABILITY
            - DISTANCE_LINEAR_PENALTY_PER_BLOCK * range
            - DISTANCE_QUADRATIC_PENALTY_PER_BLOCK_SQUARED * range * range;

        probability -= CONTEST_PENALTY * Math.pow(safe.contest(), CONTEST_EXPONENT);
        probability -= FATIGUE_PENALTY * Math.pow(safe.fatigue(), FATIGUE_EXPONENT);
        probability -= releasePenalty(safe.releaseQuality());
        probability -= Math.min(MAX_AIM_ERROR_PENALTY,
            AIM_ERROR_PENALTY_PER_DEGREE_SQUARED * safe.aimErrorDegrees() * safe.aimErrorDegrees());
        if (safe.airborne()) {
            probability -= AIRBORNE_PENALTY;
        }
        return clampProbability(probability);
    }

    /**
     * Applies the mandatory probability bounds. NaN is treated as a failed estimate; positive
     * infinity is bounded at the ceiling and negative infinity at the floor.
     */
    public static double clampProbability(double probability) {
        if (Double.isNaN(probability) || probability == Double.NEGATIVE_INFINITY) {
            return MIN_PROBABILITY;
        }
        if (probability == Double.POSITIVE_INFINITY) {
            return MAX_PROBABILITY;
        }
        return Math.clamp(probability, MIN_PROBABILITY, MAX_PROBABILITY);
    }

    /**
     * Stable event seed for replaying shot number {@code shotOrdinal} from the same match seed.
     * This does not depend on wall-clock time, object identity or global random state.
     */
    public static long replaySeed(long matchSeed, long shotOrdinal) {
        long value = matchSeed + SEED_SEQUENCE_GAMMA * (shotOrdinal + 1L);
        value = (value ^ (value >>> SEED_MIX_SHIFT_ONE)) * SEED_MIX_MULTIPLIER_ONE;
        value = (value ^ (value >>> SEED_MIX_SHIFT_TWO)) * SEED_MIX_MULTIPLIER_TWO;
        return value ^ (value >>> SEED_MIX_SHIFT_THREE);
    }

    /** Internal random-spread components, kept next to the probability rules rather than the call site. */
    record Dispersion(double pressureBlocks, double contestJitterBlocks,
                      double microScatterBlocks, double releaseSpreadBlocks) {
    }

    /** Returns deterministic dispersion components derived from the same factors as make probability. */
    static Dispersion dispersion(Input input, double makeProbability) {
        Input safe = input == null
            ? new Input(MAX_DISTANCE_BLOCKS, 1.0, 1.0, ShotReleaseQuality.VERY_EARLY, true, MAX_AIM_ERROR_DEGREES)
            : input;
        boolean perfect = safe.releaseQuality() == ShotReleaseQuality.PERFECT_GREEN;
        double rangePressure = RANGE_PRESSURE_SCALE
            * Math.pow(Math.max(0.0, safe.distanceBlocks() - RANGE_PRESSURE_START_BLOCKS), RANGE_PRESSURE_EXPONENT)
            * (perfect ? 1.0 : NON_GREEN_RANGE_MULTIPLIER);
        double nearRimContest = Math.max(0.0, CONTEST_CLOSE_RANGE_BLOCKS - safe.distanceBlocks());
        double contestPressure = safe.contest()
            * (CONTEST_PRESSURE_BASE + nearRimContest * CONTEST_CLOSE_RANGE_GAIN);
        double probabilityPressure = (1.0 - clampProbability(makeProbability)) * MODEL_PROBABILITY_SCATTER_GAIN;
        double pressure = rangePressure + contestPressure + probabilityPressure;

        double contestJitter = safe.contest() > CONTEST_JITTER_THRESHOLD
            ? safe.contest() * (perfect ? GREEN_CONTEST_JITTER : OTHER_CONTEST_JITTER) : 0.0;
        double microScatter = !perfect && safe.contest() <= CONTEST_JITTER_THRESHOLD
            ? Math.min(MICRO_SCATTER_CAP_BLOCKS,
                safe.releaseQuality().maxLateralScatterBlocks() * MICRO_SCATTER_RELEASE_SCALE)
            : 0.0;
        double releaseSpread = perfect ? 0.0
            : (safe.releaseQuality().maxLateralScatterBlocks() * RELEASE_SPREAD_MULTIPLIER
                + RELEASE_SPREAD_BASE_BLOCKS) * (1.0 + safe.distanceBlocks() * RELEASE_SPREAD_PER_BLOCK);
        return new Dispersion(pressure, contestJitter, microScatter, releaseSpread);
    }

    private static double releasePenalty(ShotReleaseQuality quality) {
        return switch (quality) {
            case PERFECT_GREEN -> 0.0;
            case SLIGHTLY_EARLY -> SLIGHTLY_EARLY_PENALTY;
            case SLIGHTLY_LATE -> SLIGHTLY_LATE_PENALTY;
            case VERY_EARLY -> VERY_EARLY_PENALTY;
            case VERY_LATE -> VERY_LATE_PENALTY;
        };
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
