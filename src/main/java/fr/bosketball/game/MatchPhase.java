package fr.bosketball.game;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The match as a directed sequence: every beat, what it puts on screen, what it sounds like, how
 * long it is allowed to hold, and where it may go next.
 *
 * <h2>Why this exists next to {@link MatchSession.State}</h2>
 * {@code State} is the coarse lifecycle the rest of the server reasons about — a court pool asks
 * "is this session {@code FINISHED}", a queue asks "is it {@code WAITING}". It is not a description
 * of what the player is watching: a run of six unanswered points, the last two minutes of a close
 * game and the inbound after an out-of-bounds all leave {@code State} at {@code LIVE} while being
 * completely different moments on screen. Those are phases, not states, and they are declared here.
 *
 * <h2>The two rules a phase has to satisfy</h2>
 * <ol>
 *   <li><b>No dead air.</b> Every phase names a {@link Surface} — the thing on screen — and the
 *       budget inside which it appears ({@link #DEAD_AIR_BUDGET_MS}). A phase without one cannot
 *       be declared.</li>
 *   <li><b>No skipping and no repeats.</b> {@link #canFollow(MatchPhase)} is the legality table and
 *       {@link #repeatable()} says whether a phase may come back. {@link PhaseLedger} enforces both
 *       at runtime; {@code MatchDirectorTest} pins them.</li>
 * </ol>
 *
 * <p>The whole table is data, so the test suite can walk it. Nothing here touches Bukkit: a phase
 * document that cannot be read without a running server is a document nobody audits.
 */
public enum MatchPhase {

    /** The warm-up room: the scorebug is already up, empty, so the eye finds the score first. */
    WAITING(Surface.SCOREBUG, Bed.SILENCE, -1),

    /** The horn. Title, whistle, and the jumbotron says TIP-OFF before the first possession. */
    TIP_OFF(Surface.TITLE, Bed.WHISTLE, 100),

    /** Play. The clock is a background instrument. */
    LIVE(Surface.SCOREBUG, Bed.CROWD, -1),

    /** A whistle: out of bounds, a shot-clock violation, a rim reset. Two benches, two lines. */
    DEAD_BALL(Surface.RAIL, Bed.WHISTLE, 15),

    /** A run of six or more unanswered points. Short by design: two seconds buys the beat. */
    TIMEOUT(Surface.RAIL, Bed.WHISTLE, 40),

    /** Inside two minutes, inside one possession. Same chrome, different voice. */
    CLUTCH(Surface.RAIL, Bed.CROWD_TIGHT, -1),

    /** The horn between quarters: the score on screen, the next possession announced. */
    QUARTER_BREAK(Surface.RAIL, Bed.CROWD, 60),

    /** The ball, the clock and the game all end together. */
    FINAL_POSSESSION(Surface.RAIL, Bed.CROWD_TIGHT, -1),

    /** Result, MVP, play of the match, outro camera. Fixed order, fixed lengths. */
    CEREMONY(Surface.CARD, Bed.CROWD, 120),

    /** The hand-off: the player is handed back to the lobby, and the session is done. */
    FINAL(Surface.LOBBY, Bed.SILENCE, 0);

    /**
     * How long a decisive event may leave the player looking at nothing, in milliseconds.
     *
     * <p>The two-second rule of session design says "never nothing for more than two seconds". A
     * tick is 50 ms and the client renders a chat line, a title or a bar on the tick after it is
     * sent, so a cue that is not already on screen within fifteen ticks was late: 750 ms is the
     * budget, and the cue is emitted synchronously inside the transition rather than scheduled.
     */
    public static final long DEAD_AIR_BUDGET_MS = 750L;

    /** Where a phase happens, on screen. Every phase has exactly one. */
    public enum Surface {
        /** A title and its subtitle: the horn, the tip, the winner. */
        TITLE("title"),
        /** The boss-bar scorebug: home, score, clock, shot clock, away. */
        SCOREBUG("scorebug"),
        /** One action-bar pill line, 1.6 s, the player's own words. */
        RAIL("rail"),
        /** A framed chat card: the final score, the MVP, the season line. */
        CARD("card"),
        /** No HUD at all, because the player is being handed back to the lobby hotbar and board. */
        LOBBY("lobby");

        private final String label;

        Surface(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** What the phase sounds like. The pack ships exactly these beds. */
    public enum Bed {
        /** Nothing: the match is over and the lobby has its own music. */
        SILENCE,
        /** A whistle: the beat that says "stop and look". */
        WHISTLE,
        /** The arena at rest: the crowd cheer, used sparingly. */
        CROWD,
        /** The same crowd, pitched up and closer: only ever for the last two minutes. */
        CROWD_TIGHT
    }

    private final Surface surface;
    private final Bed bed;
    private final int holdTicks;

    MatchPhase(Surface surface, Bed bed, int holdTicks) {
        this.surface = surface;
        this.bed = bed;
        this.holdTicks = holdTicks;
    }

    public Surface surface() {
        return surface;
    }

    public Bed bed() {
        return bed;
    }

    /**
     * How long this phase's own surface holds before the phase itself must move on, in ticks.
     * {@code -1} means "until the phase's own exit condition", which is what live play is.
     */
    public int holdTicks() {
        return holdTicks;
    }

    /** The longest any phase may hold its own surface, expressed in the dead-air budget. */
    public long surfaceBudgetMs() {
        return DEAD_AIR_BUDGET_MS;
    }

    /**
     * May this phase be entered more than once in one match?
     *
     * <p>The tip-off happens once. The ceremony happens once — that is the guard the whole end of
     * the match hangs on. The hand-off happens once. Everything else is a beat that recurs: any
     * quarter can have a run timeout, any period break, any close finish a clutch, any whistle a
     * dead ball.
     */
    public boolean repeatable() {
        return this != TIP_OFF && this != CEREMONY && this != FINAL;
    }

    /**
     * The legality table. A transition not listed here is a bug in the direction of the match, not
     * a rule to work around.
     *
     * <p>{@link #FINAL} is reachable from everywhere on purpose: a match can be cancelled, a court
     * can be shut down and a player can be the last one in the session. That is the abort edge, and
     * it is the only edge that is allowed to skip forward.
     */
    public boolean canFollow(MatchPhase from) {
        if (this == FINAL) {
            // The abort edge, from anywhere except itself: the hand-off ends the match.
            return from != FINAL;
        }
        if (from == null) {
            return this == WAITING;
        }
        return switch (from) {
            case WAITING -> this == TIP_OFF || this == LIVE || this == WAITING;
            case TIP_OFF -> this == LIVE;
            case LIVE -> this == DEAD_BALL || this == TIMEOUT || this == CLUTCH
                || this == QUARTER_BREAK || this == FINAL_POSSESSION || this == CEREMONY
                || this == LIVE;
            case DEAD_BALL -> this == LIVE || this == TIMEOUT || this == CLUTCH
                || this == FINAL_POSSESSION || this == CEREMONY;
            case TIMEOUT -> this == LIVE || this == CLUTCH || this == DEAD_BALL
                || this == FINAL_POSSESSION || this == CEREMONY;
            case CLUTCH -> this == LIVE || this == DEAD_BALL || this == TIMEOUT
                || this == FINAL_POSSESSION || this == QUARTER_BREAK || this == CEREMONY;
            case QUARTER_BREAK -> this == LIVE || this == CLUTCH || this == CEREMONY;
            case FINAL_POSSESSION -> this == LIVE || this == DEAD_BALL || this == TIMEOUT
                || this == CEREMONY;
            case CEREMONY -> false;
            case FINAL -> false;
        };
    }

    /** Every phase that may legally follow this one, for the audit and for the tests. */
    public Set<MatchPhase> successors() {
        Set<MatchPhase> legal = EnumSet.noneOf(MatchPhase.class);
        for (MatchPhase candidate : values()) {
            if (candidate.canFollow(this)) {
                legal.add(candidate);
            }
        }
        return Collections.unmodifiableSet(legal);
    }

    /** The suite walks the table; this keeps the audit honest about what it is walking. */
    public static boolean everyPhaseHasASurface() {
        for (MatchPhase phase : values()) {
            if (phase.surface == null || phase.bed == null
                || (phase.surface == Surface.LOBBY && phase != FINAL)) {
                return false;
            }
        }
        return true;
    }
}
