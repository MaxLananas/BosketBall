package fr.bosketball;

import fr.bosketball.game.bot.BotDifficulty;
import fr.bosketball.game.bot.BotTactics;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Bots used to treat a twenty-five block heave as a normal shot: the range penalty was a flat
 * 0.4 that any decent shoot bias outvoted, so bot teams hoisted from their own half and the human
 * never got to defend a possession.
 */
public class BotRangeTest {

    private static final BotTactics.Style NORMAL = BotTactics.style(BotDifficulty.NORMAL);

    @Test
    public void aBotInsideTheArcShootsWhenItIsOpen() {
        assertEquals(BotTactics.Action.SHOOT,
            BotTactics.choose(NORMAL, 1.4, 1.0, 1.0, 3.0, 6.0, false, false));
    }

    @Test
    public void aBotFromItsOwnHalfDoesNotHoistTheBall() {
        for (double distance = 12.0; distance <= 30.0; distance += 1.0) {
            BotTactics.Action action = BotTactics.choose(NORMAL, 1.0, 1.0, 1.0, 3.0, distance, false, false);
            assertEquals("a bot must not shoot from " + distance + " blocks for no reason",
                BotTactics.Action.DRIVE, action);
        }
    }

    @Test
    public void withTheClockDyingTheHeaveBecomesTheBestOption() {
        assertEquals("with the shot clock gone, a deep shot beats a turnover",
            BotTactics.Action.SHOOT,
            BotTactics.choose(NORMAL, 1.0, 1.0, 1.0, 3.0, 22.0, false, true));
    }

    @Test
    public void deepButNotDesperateStillPrefersAPass() {
        assertEquals(BotTactics.Action.PASS,
            BotTactics.choose(NORMAL, 1.0, 3.0, 1.0, 1.0, 9.0, true, false));
    }
}
