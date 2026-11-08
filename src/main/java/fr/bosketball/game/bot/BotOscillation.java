package fr.bosketball.game.bot;

import org.bukkit.util.Vector;

/** Detects repeated A-B-A-B decisions made without making court progress. */
public final class BotOscillation {
    private String first;
    private String second;
    private String third;
    private Vector start;

    public boolean observe(String decision, Vector position) {
        if (decision == null || position == null) {
            reset();
            return false;
        }
        boolean oscillating = first != null && second != null && third != null
            && first.equals(third) && second.equals(decision) && !first.equals(second)
            && start.distanceSquared(position) < 1.0;
        first = second;
        second = third;
        third = decision;
        if (start == null || oscillating || start.distanceSquared(position) >= 1.0) {
            start = position.clone();
        }
        if (oscillating) {
            reset();
        }
        return oscillating;
    }

    public void reset() {
        first = null;
        second = null;
        third = null;
        start = null;
    }
}
