package fr.bosketball;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Warmup is a playground, not a match.
 *
 * <p>Two things leaked out of it. A basket made before the tip-off moved the scorebug — and then
 * vanished when {@code startLivePlay} reset the board to 0-0 — while points were already gated on
 * live play, and the steals, blocks and rebounds taken during warmup were written to the career
 * profile for good. A player waiting alone on a court could farm STEALS and BLOCKS quest progress
 * without an opponent on the floor.
 *
 * <p>{@code MatchSession} has no seam to unit-test with, so this locks the shape of the source the
 * same way the house-style test does: every career counter must sit behind a live-play guard, and
 * the scoring entry point must refuse a warmup basket before it touches the board.
 */
public class WarmupRulesTest {

    private static final Path SESSION =
        Path.of("src/main/java/fr/bosketball/game/MatchSession.java");

    private static String source() throws Exception {
        return Files.readString(SESSION);
    }

    @Test
    public void aWarmupBasketNeverMovesTheBoard() throws Exception {
        String session = source();
        int method = session.indexOf("public void onBasketScored(");
        assertTrue("the scoring entry point must still exist", method > 0);
        int guard = session.indexOf("if (state == State.WAITING)", method);
        int board = session.indexOf("scores.put(creditedTeam", method);
        assertTrue("the warmup guard must exist", guard > 0);
        assertTrue("a warmup basket must be refused before the board moves", board < 0 || guard < board);
        int reset = session.indexOf("resetShotClock(false);", method);
        assertTrue("a warmup basket must not disturb the shot clock either", reset < 0 || guard < reset);
    }

    @Test
    public void everyCareerCounterRunsInLivePlayOnly() throws Exception {
        String session = source();
        List<String> unguarded = new ArrayList<>();
        for (String counter : List.of(".incrementSteals()", ".incrementBlocks()",
            ".incrementRebounds()", ".incrementAnkleBreakers()")) {
            int i = 0;
            while ((i = session.indexOf(counter, i)) >= 0) {
                String before = session.substring(Math.max(0, i - 400), i);
                if (!before.contains("State.LIVE")) {
                    unguarded.add(counter + " @" + i);
                }
                i += counter.length();
            }
        }
        assertTrue("a career counter outside live play can be farmed during warmup: " + unguarded,
            unguarded.isEmpty());
    }

    @Test
    public void theProfileCountersAndThePointsAgree() throws Exception {
        String session = source();
        int method = session.indexOf("public void onBasketScored(");
        int points = session.indexOf("prof.addPoints(pts);", method);
        assertTrue("points are the reference: they only count in live play", points > 0);
        String before = session.substring(Math.max(0, points - 300), points);
        assertTrue("the reference gate is state == State.LIVE", before.contains("state == State.LIVE"));
    }
}
