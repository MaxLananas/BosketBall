package fr.bosketball.game;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The proof that a match was directed, not merely simulated.
 *
 * <p>{@link MatchSession} enters its phases through {@link #enter} and nothing else. The ledger does
 * three things, and all three are things a human reviewer cannot reliably do by reading a
 * four-thousand-line file:
 *
 * <ol>
 *   <li><b>It refuses an illegal edge.</b> {@link MatchPhase#canFollow} is applied to every
 *       transition, so a quarter break can never slide straight into the ceremony and the tip-off
 *       can never happen twice.</li>
 *   <li><b>It proves the surface.</b> {@link #enter} requires the caller to declare which surface
 *       the player actually got. The transition is timestamped, and the session must commit the
 *       cue within {@link MatchPhase#DEAD_AIR_BUDGET_MS}. A transition that is entered and never
 *       committed is reported as dead air — the exact bug this pass exists to prevent.</li>
 *   <li><b>It keeps the one-way doors.</b> The ceremony and the hand-off to the lobby may be
 *       entered once per match. A second attempt is not an exception to throw into a tick loop:
 *       it is refused, recorded, and the first ceremony keeps running.</li>
 * </ol>
 *
 * <p>Not thread-safe on purpose: every call happens on the server thread, inside a tick or inside
 * the event that produced the tick. Guarding it would hide the day it stops being true.
 */
public final class MatchDirector {

    /** One transition, as it happened. */
    public static final class Step {
        private final MatchPhase from;
        private final MatchPhase to;
        private final MatchPhase.Surface surface;
        private final long enteredAtMs;
        private long committedAtMs = -1L;

        private Step(MatchPhase from, MatchPhase to, MatchPhase.Surface surface, long enteredAtMs) {
            this.from = from;
            this.to = to;
            this.surface = surface;
            this.enteredAtMs = enteredAtMs;
        }

        public MatchPhase from() {
            return from;
        }

        public MatchPhase to() {
            return to;
        }

        public MatchPhase.Surface surface() {
            return surface;
        }

        public long enteredAtMs() {
            return enteredAtMs;
        }

        /** {@code -1} until the cue was actually handed to the players. */
        public long committedAtMs() {
            return committedAtMs;
        }

        /** Milliseconds between the transition and its player-facing consequence. */
        public long latencyMs() {
            return committedAtMs < 0 ? Long.MAX_VALUE : committedAtMs - enteredAtMs;
        }

        public boolean committed() {
            return committedAtMs >= 0;
        }

        /** Dead air: the phase was entered, the player got nothing, and the budget is spent. */
        public boolean isDeadAir(long nowMs) {
            return !committed() && nowMs - enteredAtMs > MatchPhase.DEAD_AIR_BUDGET_MS;
        }
    }

    /** Every step of one match, oldest first. Capped so a long session cannot grow a log forever. */
    private static final int MAX_STEPS = 256;

    private final List<Step> steps = new ArrayList<>();
    private final Set<MatchPhase> visited = EnumSet.noneOf(MatchPhase.class);
    private int refused = 0;
    private Step open = null;

    /**
     * Opens a transition onto {@code to}.
     *
     * @return the step that was opened, or {@code null} when the edge is illegal, when the phase is
     *     a one-way door already used, or when the same phase is already open. A {@code null} return
     *     is a refusal, never a crash: the caller keeps running and the audit keeps the evidence.
     */
    public Step enter(MatchPhase to, MatchPhase.Surface surface, MatchPhase from, long nowMs) {
        if (to == null || surface == null) {
            refused++;
            return null;
        }
        if (!to.canFollow(from)) {
            refused++;
            return null;
        }
        if (open != null && open.to() == to && to.repeatable()) {
            // Re-entering a repeatable phase that is already on screen is not a transition. The
            // caller wanted a cue, not a new beat: let it commit against the step already open.
            return open;
        }
        if (!to.repeatable() && visited.contains(to)) {
            // A one-way door is refused outright, even when it is the phase already on screen.
            refused++;
            return null;
        }
        Step step = new Step(from, to, surface, nowMs);
        if (steps.size() >= MAX_STEPS) {
            steps.remove(0);
        }
        steps.add(step);
        visited.add(to);
        open = step;
        return step;
    }

    /** Records that the surface reached the players. */
    public void commit(Step step, long nowMs) {
        if (step == null || step.committed()) {
            return;
        }
        step.committedAtMs = nowMs;
    }

    /** The transition currently on screen, or {@code null} before the first one. */
    public Step openStep() {
        return open;
    }

    /** The phase currently on screen; {@code null} only before the match has been directed at all. */
    public MatchPhase current() {
        return open == null ? null : open.to();
    }

    public MatchPhase previous() {
        return open == null ? null : open.from();
    }

    public boolean hasVisited(MatchPhase phase) {
        return visited.contains(phase);
    }

    public int refusedTransitions() {
        return refused;
    }

    public List<Step> steps() {
        return java.util.Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public int stepCount() {
        return steps.size();
    }

    /**
     * Every step that was opened, never committed, and has burnt its budget.
     *
     * <p>This is the audit the mission asks for: a phase may be entered with nothing on screen for
     * a moment — a title fades in — but not for longer than {@link MatchPhase#DEAD_AIR_BUDGET_MS}.
     */
    public List<Step> deadAir(long nowMs) {
        List<Step> dead = new ArrayList<>();
        for (Step step : steps) {
            if (step.isDeadAir(nowMs)) {
                dead.add(step);
            }
        }
        return dead;
    }

    /** True when the phase on screen has a surface that reached the players. */
    public boolean surfaceIsLive() {
        return open != null && open.committed();
    }
}
