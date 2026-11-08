package fr.bosketball.util;

/**
 * The surfaces of the HUD, and who may speak on each one.
 *
 * <p>The action bar is one line. Every subsystem in the plugin wants it — the match, the arena, the
 * tutorial, the progression layer, the camera — and until now they all wrote to it directly, last
 * writer wins. This enum is the missing part of that sentence: a surface has an owner, a minimum
 * display time and a priority, so "who gets the line" is a decision the queue makes once instead of
 * an accident of which code path ran last in the tick.
 *
 * <p>{@link #priority} orders two messages that want the line at the same moment: the higher number
 * wins, and the loser is merged into the winner rather than dropped (see {@link Hunt} — the queue
 * that applies this). {@link #minDisplayMs} is the floor: once a message is on screen it keeps the
 * line for that long, so a 24-second violation and the shot clock cannot overwrite each other inside
 * the same 1.6 seconds. {@link #identity} is the anti-overwrite key: two messages with the same
 * identity refresh each other, and nothing else can slip between them.
 *
 * <p>Length is capped by {@link #maxChars}: an action-bar line stops being readable long before it
 * stops fitting, and a toast is read at a sprint.
 */
public enum HudSurface {

    /** A rule decision the referee would announce: violation, out of bounds, dead ball. */
    RULE("rule", 90, 2000, 40),

    /** A contest outcome: block, steal, dunk, ankle. Both participants hear it. */
    CONTEST("contest", 80, 1600, 40),

    /** A shot or dunk meter that is redrawn every tick while it matters. */
    SHOT_METER("shot", 70, 120, 44),

    /** Progress toward a goal: assists, streaks, quests. */
    PROGRESS("progress", 60, 1600, 36),

    /** A coaching prompt: what to do next, never what the plugin just did. */
    COACH("coach", 50, 1400, 40),

    /**
     * The always-on compass when nothing else is happening.
     *
     * <p>No lease: the compass is redrawn on the ticks nobody else wants, so it may be replaced on
     * any tick. Every other surface outranks it, which is what makes it ambient rather than a
     * message — it fills the line, it never takes it.
     */
    AMBIENT("ambient", 10, 0, 44),

    /** Small talk from the lobby layer: AFK, welcome back, practice toggles. */
    LOBBY("lobby", 30, 1600, 40),

    /** A tutorial step. The tutorial owns the line for as long as its step lasts. */
    TUTORIAL("tutorial", 85, 2200, 40),

    /** A cinematic shot: the camera owns the line, and no gameplay noise may interrupt it. */
    CINEMATIC("cinematic", 100, 2500, 40);

    private final String id;
    private final int priority;
    private final int minDisplayMs;
    private final int maxChars;

    HudSurface(String id, int priority, int minDisplayMs, int maxChars) {
        this.id = id;
        this.priority = priority;
        this.minDisplayMs = minDisplayMs;
        this.maxChars = maxChars;
    }

    /** The short name used on screen diagnostics and in tests. */
    public String id() {
        return id;
    }

    /** Higher wins when two surfaces want the line at the same instant. */
    public int priority() {
        return priority;
    }

    /** How long a message on this surface owns the line once it is shown, in milliseconds. */
    public int minDisplayMs() {
        return minDisplayMs;
    }

    /** Longest copy this surface accepts, in visible characters. */
    public int maxChars() {
        return maxChars;
    }

    /** The anti-overwrite key: two messages of the same surface refresh each other. */
    public String identity() {
        return "hud." + id;
    }

    /**
     * True when this surface is allowed to interrupt {@code current}.
     *
     * <p>Strictly higher priority only. A rule at 90 interrupts a contest at 80; the contest cannot
     * interrupt the rule, however important it feels to the code path that raised it.
     */
    public boolean outranks(HudSurface current) {
        return current == null || priority > current.priority;
    }

    /** The surface matching {@link #id()}, or the lobby surface for anything unknown. */
    public static HudSurface byId(String id) {
        if (id != null) {
            for (HudSurface surface : values()) {
                if (surface.id.equalsIgnoreCase(id)) {
                    return surface;
                }
            }
        }
        return LOBBY;
    }
}
