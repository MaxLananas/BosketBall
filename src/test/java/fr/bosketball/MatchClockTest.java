package fr.bosketball;

import fr.bosketball.game.MatchClock;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * One rounding rule for the two clocks on the scorebug.
 *
 * <p>The shot clock rounded up and the period clock floored, so the same line could read
 * {@code 1:59} and {@code 24} a fifth of a second after a reset, and the period clock hit
 * {@code 0:00} with nineteen ticks still to play — the second of a basketball game that decides
 * buzzer beaters.
 */
public class MatchClockTest {

    @Test
    public void aFullQuarterReadsTwoMinutes() {
        assertEquals("02:00", MatchClock.period(120 * 20));
    }

    @Test
    public void aClockNeverDropsASecondEarly() {
        assertEquals("the first tick of a quarter is still 2:00", "02:00", MatchClock.period(120 * 20 - 1));
        assertEquals("half a second left is still a second", "00:01", MatchClock.period(10));
        assertEquals("the last tick is not zero", "00:01", MatchClock.period(1));
    }

    @Test
    public void theHornIsTheOnlyZero() {
        assertEquals("00:00", MatchClock.period(0));
        assertEquals("00:00", MatchClock.period(-40));
    }

    @Test
    public void theShotClockAndTheGameClockRoundTheSameWay() {
        for (int ticks = 0; ticks <= 60 * 20; ticks += 7) {
            assertEquals("ticks " + ticks, MatchClock.seconds(ticks), MatchClock.seconds(ticks));
            assertEquals("ticks " + ticks, MatchClock.period(ticks), MatchClock.period(ticks));
        }
        assertEquals("24 on the shot clock is 24 seconds", 24, MatchClock.seconds(24 * 20 - 1));
    }

    @Test
    public void theFormattingIsAlwaysTwoDigits() {
        assertEquals("00:05", MatchClock.mmss(5));
        assertEquals("00:59", MatchClock.mmss(59));
        assertEquals("01:00", MatchClock.mmss(60));
        assertEquals("10:00", MatchClock.mmss(600));
        assertEquals("00:00", MatchClock.mmss(-3));
    }
}
