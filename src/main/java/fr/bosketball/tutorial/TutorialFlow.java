package fr.bosketball.tutorial;

/**
 * Round 31 — every decision of the new-player tour, extracted as pure functions.
 *
 * <p>{@code TutorialManager} owns the Bukkit side (entities, boss bar, teleports); this class owns
 * the rules. Being pure, it can be driven through complete player journeys in unit tests — first
 * join, replay, quit mid-chapter, reconnect during a replay, skip spam, a match starting in the
 * middle of the tour — without a server. That is how the "it advances by itself" class of bug is
 * kept out for good: the transitions are asserted, not hoped for.
 */
public final class TutorialFlow {

    /** Why {@code /tuto} did (or did not) start a tour. */
    public enum StartVerdict {
        START,
        ALREADY_RUNNING,
        DISABLED,
        IN_MATCH,
        QUEUED
    }

    /** What the watchdog is allowed to do when a chapter has been open for a while. */
    public enum HintStage {
        WAIT,
        HINT,
        OFFER_SKIP,
        AUTO_COMPLETE
    }

    private TutorialFlow() {
    }

    /**
     * Decides whether a tour may start.
     *
     * @param queued true when the player sits in a matchmaking queue: starting a tour would
     *               teleport them to a practice court while they are waiting for a real match
     */
    public static StartVerdict startVerdict(boolean enabled, boolean running, boolean inCompetitiveMatch,
                                            boolean queued) {
        if (!enabled) {
            return StartVerdict.DISABLED;
        }
        if (running) {
            return StartVerdict.ALREADY_RUNNING;
        }
        if (inCompetitiveMatch) {
            return StartVerdict.IN_MATCH;
        }
        if (queued) {
            return StartVerdict.QUEUED;
        }
        return StartVerdict.START;
    }

    /**
     * Where a tour resumes. A finished player never resumes mid-way: a plain {@code /tuto} must not
     * silently replay the whole tour (that was one of the "it does whatever it wants" complaints).
     */
    public static int resumeIndex(boolean completed, int storedStep, int stepCount) {
        if (stepCount <= 0) {
            return 0;
        }
        if (completed) {
            return 0;
        }
        return Math.clamp(storedStep, 0, stepCount - 1);
    }

    /**
     * The pay-once rule.
     *
     * @param validated   true when the chapter was really completed (a forced skip pays nothing)
     * @param rewardedStep highest chapter ever rewarded for this player, persisted in the profile —
     *                     so replaying, quitting, reconnecting or restarting can never pay twice
     */
    public static boolean paysReward(int step, int rewardedStep, int reward, boolean validated) {
        return validated && reward > 0 && step > rewardedStep;
    }

    /** The chapter that follows {@code current}; equal to {@code stepCount} means "tour finished". */
    public static int nextStep(int current, int stepCount) {
        return Math.min(current + 1, stepCount);
    }

    /**
     * The watchdog decision.
     *
     * @param readingChapter chapters that only ask to read (welcome / final) may move on alone
     * @param ageMs          how long the chapter has been open
     * @param hintLevel      how many times the watchdog already helped on this chapter
     */
    public static HintStage hintStage(boolean readingChapter, long ageMs, int hintLevel,
                                      long softSkipMs, long skipAfterMs, long cardAutoMs) {
        if (readingChapter) {
            return ageMs >= cardAutoMs ? HintStage.AUTO_COMPLETE : HintStage.WAIT;
        }
        if (ageMs >= skipAfterMs && hintLevel < 2) {
            return HintStage.OFFER_SKIP;
        }
        if (ageMs >= softSkipMs && hintLevel < 1) {
            return HintStage.HINT;
        }
        return HintStage.WAIT;
    }

    /** Boss bar fill: the current chapter contributes a third until it is validated, then a full one. */
    public static float bossBarProgress(int current, boolean validated, int stepCount) {
        if (stepCount <= 0) {
            return 0f;
        }
        float partial = validated ? 1f : 0.35f;
        return Math.clamp((current + partial) / stepCount, 0f, 1f);
    }

    /**
     * A match starting during the tour ends it (instead of pausing it) once the player is on the
     * last two chapters: they joined a queue, so they finished the tour, and the completion bonus
     * must not be lost because the game started two seconds early.
     */
    public static boolean finishesOnMatchStart(int currentStep, int stepCount) {
        return currentStep >= stepCount - 2;
    }

    /** A live competitive match always wins over the tour: no markers, no boss bar, no card. */
    public static boolean shouldPauseForMatch(boolean running, boolean inCompetitiveMatch) {
        return running && inCompetitiveMatch;
    }

    /**
     * Chapters that need a ball can only be done on a practice court. When none could be opened the
     * manager passes them politely instead of leaving the player in front of an impossible task.
     */
    public static boolean needsLiveCourt(String requirement) {
        return "shoot".equals(requirement) || "dunk".equals(requirement);
    }

    /**
     * A chapter that asks nothing but reading must never make the player wait.
     *
     * <p>Round 32: the owner reported "I finished reading the card ages ago and I still cannot
     * continue". The card's CONTINUE button was gated by the same validation timer as a gameplay
     * chapter, so a fast reader had to sit through 20 s of nothing. Reading chapters now turn on
     * the first click (chat button or a click in the world) and only fall back to the timer.
     */
    public static boolean isReadingRequirement(String requirement) {
        return "welcome".equals(requirement) || "finish".equals(requirement)
            || "card".equals(requirement) || "read".equals(requirement);
    }
}
