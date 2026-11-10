package fr.bosketball.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A click inside a match belongs to the match.
 *
 * <p>The tour used to take a click for a reading page whenever the player was not in a <em>ranked</em>
 * game, so a player who opened a reading chapter and then joined a practice or friendly session had
 * every click cancelled by {@code GameplayListener#onInteract} before the shot handler ran: he could
 * not shoot for the whole game. The decision is now one pure call, and these four cases pin it.</p>
 */
public final class TourClickTest {

    @Test
    public void aReadingPageInTheLobbyTakesTheClick() {
        assertTrue(TutorialManager.mayTakeClick(false, true));
    }

    @Test
    public void aClickInsideAnySessionIsLeftToTheMatch() {
        assertFalse("a reading page must not eat a click thrown in a practice game",
            TutorialManager.mayTakeClick(true, true));
    }

    @Test
    public void aChapterThatAsksForAPlayIsNeverTurnedByAClick() {
        assertFalse(TutorialManager.mayTakeClick(false, false));
        assertFalse(TutorialManager.mayTakeClick(true, false));
    }
}
