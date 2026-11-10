package fr.bosketball.game;

/**
 * The two clocks on the scorebug, rounded the same way.
 *
 * <p>The period clock used to floor and the shot clock used to round up, so a fifth of a second
 * after the ball was handed over the same scorebug read {@code 1:59} next to {@code 24}, and the
 * period clock reached {@code 0:00} while almost a full second of play was still on it — which is
 * the one second of a basketball game everybody watches. Both now round up: a clock only reads
 * {@code 0:00} when there is genuinely nothing left to run.
 */
public final class MatchClock {

    /** Ticks in one second of game time. */
    public static final int TICKS_PER_SECOND = 20;

    private MatchClock() {
    }

    /**
     * Whole seconds left for a number of ticks, rounded up.
     *
     * <p>One tick left is {@code 0:01}, not {@code 0:00}: the period is still running and a
     * released shot still counts.
     */
    public static int seconds(int ticksLeft) {
        if (ticksLeft <= 0) {
            return 0;
        }
        return (ticksLeft + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    /** {@code mm:ss} for a whole number of seconds, never negative. */
    public static String mmss(int seconds) {
        int safe = Math.max(0, seconds);
        String s = Integer.toString(safe % 60);
        String m = Integer.toString(safe / 60);
        return (m.length() < 2 ? "0" + m : m) + ":" + (s.length() < 2 ? "0" + s : s);
    }

    /** The clock the scorebug shows for a period of {@code ticksLeft}. */
    public static String period(int ticksLeft) {
        return mmss(seconds(ticksLeft));
    }
}
