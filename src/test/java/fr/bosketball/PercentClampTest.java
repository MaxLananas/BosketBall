package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.season.SeasonStanding;
import fr.bosketball.season.SeasonSummary;
import java.util.UUID;
import org.junit.Test;

/**
 * Percentages are the numbers a player compares themselves with, and every one of them comes out
 * of a file that can be edited, merged or restored. A profile with more makes than attempts, or a
 * standings file with more wins than matches, used to print numbers no percentage can be.
 */
public class PercentClampTest {

    @Test
    public void aProfileCannotShowMoreThanAHundredPercent() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Clamp");
        profile.setTwoPointersAttempted(10);
        profile.setThreePointersAttempted(2);
        profile.setTwoPointersMade(12);
        profile.setThreePointersMade(9);

        assertTrue("field goal: " + profile.fieldGoalPercentage(),
            profile.fieldGoalPercentage() <= 100.0 && profile.fieldGoalPercentage() >= 0.0);
        assertTrue("three point: " + profile.threePointPercentage(),
            profile.threePointPercentage() <= 100.0 && profile.threePointPercentage() >= 0.0);
    }

    @Test
    public void anEmptyProfileShowsZeroNotNaN() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Fresh");
        assertEquals(0.0, profile.fieldGoalPercentage(), 0.0);
        assertEquals(0.0, profile.threePointPercentage(), 0.0);
        assertEquals(0.0, profile.winRatePercent(), 0.0);
        assertTrue(Double.isFinite(profile.fieldGoalPercentage()));
    }

    @Test
    public void aNormalProfileKeepsItsRealNumbers() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID(), "Shooter");
        profile.setTwoPointersAttempted(8);
        profile.setTwoPointersMade(4);
        profile.setThreePointersAttempted(4);
        profile.setThreePointersMade(1);
        assertEquals("5 of 12 is the real field goal rate", 41.67, profile.fieldGoalPercentage(), 0.02);

        profile.setWins(3);
        profile.setLosses(1);
        assertEquals(75.0, profile.winRatePercent(), 1e-9);
    }

    @Test
    public void aCorruptedStandingCannotOutrunItsMatches() {
        SeasonStanding standing = new SeasonStanding(
            1, java.util.UUID.randomUUID(), "Clamp", 1400, 20, 30, Rank.ROOKIE, false);
        assertTrue("wins above matches is a corrupted file: " + standing.winRatePercent(),
            standing.winRatePercent() <= 100.0);

        SeasonStanding empty = new SeasonStanding(
            2, java.util.UUID.randomUUID(), "Empty", 1200, 0, 0, Rank.ROOKIE, true);
        assertEquals(0.0, empty.winRatePercent(), 0.0);
    }

    @Test
    public void aSeasonSummaryCannotPrintAnImpossibleRate() {
        SeasonSummary summary = new SeasonSummary("Clamp", "Season 1", 1, 1, 1500, Rank.ROOKIE, 10, 25, 0, 0);
        assertEquals(100, summary.winRatePercent());
        SeasonSummary empty = new SeasonSummary("Empty", "Season 1", 1, 1, 1200, Rank.ROOKIE, 0, 0, 0, 0);
        assertEquals(0, empty.winRatePercent());
    }
}
