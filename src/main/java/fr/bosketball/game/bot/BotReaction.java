package fr.bosketball.game.bot;

/** Counts uninterrupted observation; losing sight cancels the accumulated reaction. */
public final class BotReaction {
    private int ticks;

    public boolean observe(boolean visible, int requiredTicks) {
        int required = Math.max(1, requiredTicks);
        ticks = visible ? Math.min(required, ticks + 1) : 0;
        return ticks >= required;
    }

    public void reset() {
        ticks = 0;
    }
}
