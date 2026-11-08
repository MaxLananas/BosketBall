package fr.bosketball.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one queue the action bar has.
 *
 * <p>Round 58 gave the toasts a 1.6-second lease on the action bar ({@code Msg.railOnScreen}) and it
 * fixed the symptom without touching the cause: eight subsystems still called
 * {@code player.sendActionBar} directly, each with its own idea of how long a message should stay,
 * and the last write in a tick won. A block, a shot clock, a tutorial step, a cinematic and the
 * idle compass could all be true at the same instant and only one of them was ever read.
 *
 * <p>This queue is the cause fixed. Every message names a {@link HudSurface}; the surface carries
 * the priority, the minimum display time and the copy budget; and the queue makes exactly one
 * decision per tick:
 *
 * <ol>
 *   <li><b>A message on screen owns the line until its minimum display time expires.</b> Nothing
 *       interrupts it, so a 24-second violation cannot be erased 15 ms later by the shot meter.</li>
 *   <li><b>A higher priority supersedes a lower one.</b> A rule decision may cut a contest outcome
 *       short; the reverse never happens. Within one tick — before anything has actually been sent —
 *       the winner is simply the higher priority, so the player never sees a flicker of the loser.</li>
 *   <li><b>A lower priority arriving while the line is held is merged, not dropped.</b> Two things
 *       really did happen; the second one is appended behind the house separator, and only if the
 *       result stays inside the {@link HudWidth} contract. A merge that would overflow is dropped,
 *       because a truncated sentence is worse than the message the player already has.</li>
 *   <li><b>The same surface refreshes itself.</b> A meter redrawn every tick, a countdown that
 *       ticks down, a prompt that changed — they replace their own message instead of queueing
 *       behind it.</li>
 * </ol>
 *
 * <p>The class is pure: no Bukkit, no clock of its own. Timestamps are passed in, which is what
 * makes "two messages in the same tick" a unit test rather than a video recording.
 */
public final class Hunt {

    /** How long a merged clause keeps the line alive past the moment it was appended. */
    public static final int MERGE_GRACE_MS = 900;

    /** The separator between a held message and a merged one: the house middot, always. */
    private static final String SEPARATOR = " <" + Palette.DIM + ">\u00b7</" + Palette.DIM + "> ";

    /** What the action bar should show, and until when. */
    public record Frame(HudSurface surface, String text, long shownAtMs, long expiresAtMs, int merges) {

        /** True while this frame still owns the line at {@code now}. */
        public boolean holds(long now) {
            return now < expiresAtMs;
        }

        /** Visible characters on the line. */
        public int chars() {
            return HudWidth.visibleChars(text);
        }

        /** Width of the line in font pixels. */
        public int widthPx() {
            return HudWidth.measure(text);
        }
    }

    private final Map<HudSurface, String> pending = new LinkedHashMap<>();
    private final Map<HudSurface, net.kyori.adventure.text.Component> pendingComponents =
        new java.util.EnumMap<>(HudSurface.class);
    private Frame current;
    private long lastSentShownAtMs = Long.MIN_VALUE;
    private String lastSentText;
    private int mergedTotal;
    private int droppedTotal;
    private int supersededTotal;

    /**
     * Offers a message for the action bar.
     *
     * @return the frame that owns the line after this call, which is {@code null} only when the
     *         message was dropped for length before it could be shown
     */
    public Frame offer(HudSurface surface, String text) {
        return offer(surface, text, System.currentTimeMillis());
    }

    /**
     * Offers a message at an explicit instant (the unit-tested entry point).
     *
     * <p>The text is fitted to the surface's copy budget and to the {@link HudWidth} contract
     * before it is queued, so a caller can never widen the HUD by accident.
     */
    public Frame offer(HudSurface surface, String text, long now) {
        if (surface == null || text == null || text.isBlank()) {
            return current;
        }
        String fitted = fitToSurface(surface, text);
        if (fitted.isEmpty()) {
            droppedTotal++;
            return current;
        }
        if (current != null && current.holds(now) && current.surface() == surface) {
            // The surface talking to itself: the newest value is the truth, and the lease restarts
            // from now so a meter that keeps refreshing cannot be starved by its own refresh.
            current = new Frame(surface, fitted, now, now + Math.max(surface.minDisplayMs(), MERGE_GRACE_MS),
                current.merges());
            lastSentText = null;
            return current;
        }
        // Everything else waits: offer never touches a frame that a player is reading. A higher
        // priority takes the line at the next tick (and is sent instead of a lower message that was
        // only queued this tick, so the player never sees a one-frame flash of the loser).
        pending.put(surface, fitted);
        return current;
    }

    /**
     * Folds the message that arrived while the last frame was on screen into the next one.
     *
     * <p>Only a genuinely lower priority merges, only once per frame, and only when the result is
     * still a line a person can read: the two budgets are the stricter of the pair and the
     * {@link HudWidth} ceiling. Anything else keeps the newer message alone — a truncated sentence is
     * worse than the message the player was about to read.
     */
    private Frame mergeExpired(Frame expired, HudSurface winner, String text, long now) {
        if (expired.surface() == winner || winner.outranks(expired.surface())) {
            return null;
        }
        String candidate = expired.text() + SEPARATOR + text;
        // One reading budget for a merged line, not the stricter of the two surfaces: the point of
        // the merge is that both events are told, and losing one to an arbitrary half of the pair
        // would put the player back where this round started.
        if (!HudWidth.fits(candidate) || HudWidth.visibleChars(candidate) > HudWidth.MERGE_MAX_CHARS) {
            droppedTotal++;
            return null;
        }
        mergedTotal++;
        return new Frame(winner, candidate, now, now + winner.minDisplayMs(), expired.merges() + 1);
    }

    /**
     * Advances the queue one tick and returns the frame to send, or {@code null} when the line did
     * not change (nothing to send is a packet saved, not a message lost).
     */
    public Frame tick(long now) {
        if (current != null && current.holds(now)) {
            return sendIfChanged(current);
        }
        if (pending.isEmpty()) {
            current = null;
            return null;
        }
        HudSurface winner = null;
        for (HudSurface candidate : pending.keySet()) {
            if (winner == null || candidate.priority() > winner.priority()) {
                winner = candidate;
            }
        }
        String text = pending.remove(winner);
        pendingComponents.remove(winner);

        if (current != null) {
            // The previous frame just expired. A lower-priority message that arrived while it was on
            // screen is merged behind it rather than replacing it silently: two things did happen.
            Frame merged = mergeExpired(current, winner, text, now);
            if (merged != null) {
                current = merged;
                return sendIfChanged(current);
            }
        }
        current = new Frame(winner, text, now, now + winner.minDisplayMs(), 0);
        return sendIfChanged(current);
    }

    private Frame sendIfChanged(Frame frame) {
        if (frame.shownAtMs() == lastSentShownAtMs && frame.text().equals(lastSentText)) {
            return null;
        }
        lastSentShownAtMs = frame.shownAtMs();
        lastSentText = frame.text();
        return frame;
    }

    /**
     * Offers an already-built component (a meter) on a surface.
     *
     * <p>The artwork travels beside the queue rather than through it: the queue still owns the
     * decision, its keys and its leases, but a measurement of a drawing is not a sentence, so the
     * component is kept verbatim and sent as it was built.
     */
    public void offerComponent(HudSurface surface, net.kyori.adventure.text.Component component, long now) {
        if (surface == null || component == null) {
            return;
        }
        pendingComponents.put(surface, component);
        // The queue is asked about the surface, not about the artwork: a drawing still competes for
        // the line on its surface's priority, and still refreshes itself instead of queueing.
        offer(surface, "\u0000" + surface.id(), now);
    }

    /** The component most recently offered on a surface, or {@code null} for a text surface. */
    public net.kyori.adventure.text.Component component(HudSurface surface) {
        return pendingComponents.get(surface);
    }

    /** The frame currently owning the line, or {@code null} when the line is free. */
    public Frame current(long now) {
        return current != null && current.holds(now) ? current : null;
    }

    /** True when {@code surface} is the one on screen right now. */
    public boolean onScreen(HudSurface surface, long now) {
        Frame frame = current(now);
        return frame != null && frame.surface() == surface;
    }

    /** True while any message owns the line — what the aim HUD asks instead of guessing. */
    public boolean busy(long now) {
        return current(now) != null;
    }

    /** Forgets everything: the quit path, a reload, a new match. */
    public void clear() {
        pending.clear();
        pendingComponents.clear();
        current = null;
        lastSentText = null;
        lastSentShownAtMs = Long.MIN_VALUE;
    }

    /** Counters for /bb displays: merges keep two events on one line, drops lose one. */
    public int mergedTotal() {
        return mergedTotal;
    }

    public int droppedTotal() {
        return droppedTotal;
    }

    public int supersededTotal() {
        return supersededTotal;
    }

    /** Surfaces waiting for the line, in insertion order (diagnostics and tests). */
    public List<HudSurface> waiting() {
        return new ArrayList<>(pending.keySet());
    }

    private static String fitToSurface(HudSurface surface, String text) {
        String fitted = HudWidth.fits(text) ? text : HudWidth.fit(text);
        int maxChars = surface.maxChars();
        if (HudWidth.visibleChars(fitted) > maxChars) {
            fitted = HudWidth.fit(fitted, HudWidth.MAX_LINE_PX);
            if (HudWidth.visibleChars(fitted) > maxChars) {
                // Word-budget cut: the measurement fits, the reading does not.
                fitted = Palette.of(Palette.TEXT, cutToChars(fitted, maxChars));
            }
        }
        return fitted;
    }

    private static String cutToChars(String miniMessage, int maxChars) {
        String plain = MiniSyntax.visibleText(miniMessage);
        if (plain.length() <= maxChars) {
            return plain;
        }
        int cut = Math.min(plain.length() - 1, Math.max(0, maxChars - 1));
        int space = plain.lastIndexOf(' ', cut);
        return (space > 0 ? plain.substring(0, space) : plain.substring(0, cut)) + "\u2026";
    }
}
