package fr.bosketball.server;

import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.List;

/**
 * The first ten minutes, written down as data instead of being scattered across join listeners.
 *
 * <p>A player arrives knowing nobody, owning nothing and having never seen a basketball plugin.
 * Hypixel answers that with one held compass and a lobby that puts the game selector on the path
 * from spawn; the rest is hidden one level deeper. This script follows the same rule: each beat has
 * a <em>visual cue</em> that makes the next action obvious (the held hotbar slot, the gold ring, the
 * rim marker, a nameplate), and text is only the fallback. Two things are non-negotiable and are
 * asserted by {@link #validate()}: the five beats cover minutes 0 to 10 without a gap, and every
 * beat has a fallback action for a player who skips it, so nobody reaches minute five with nothing
 * to click.
 *
 * <p>All state transitions are pure functions of {@link Progress}, which is read from the player's
 * profile. Skipping the tour does not skip the player: the stage is derived from what they actually
 * did, not from a timer that assumes they followed the script.
 */
public final class OnboardingScript {

    public enum Stage {
        JOIN,
        IDENTITY,
        FIRST_SHOT,
        FIRST_MATCH,
        FIRST_REWARD,
        DONE
    }

    public enum Surface {
        MOTD,
        WELCOME_CARD,
        PRACTICE_COURT,
        MATCH_CARD,
        REWARD_CARD
    }

    /**
     * @param visualCue   what the world or the hotbar shows, before any sentence is read
     * @param primary     the one action the beat is asking for
     * @param fallback    what a player who skips still has, always a real command or a real click
     * @param copy        the single card / rail line for this beat
     * @param skippedCopy the shorter line used when the player skipped the beat
     */
    public record Beat(
        Stage stage,
        int fromMinute,
        int toMinute,
        Surface surface,
        String visualCue,
        String primary,
        String fallback,
        String copy,
        String skippedCopy
    ) {
    }

    /** What the server can actually observe about a player's first ten minutes. */
    public record Progress(
        boolean identitySeen,
        boolean shotTaken,
        boolean matchPlayed,
        boolean rewardClaimed,
        long minutesOnline
    ) {
    }

    public static final int TOTAL_MINUTES = 10;
    /** A player is never left longer than this without an action to take. */
    public static final long DEAD_AIR_LIMIT_MS = 20_000L;
    /** The lobby hotbar's play badge. */
    public static final int PLAY_SLOT = 1;
    /** The lobby hotbar's shop badge. */
    public static final int SHOP_SLOT = 2;

    private static final String SEP = ScreenBounds.separator();

    public static final List<Beat> SCRIPT = List.of(
        new Beat(Stage.JOIN, 0, 1, Surface.MOTD,
            "The gold play badge is held on join",
            "Click the gold play badge",
            "/play practice",
            "<" + Palette.TEXT + ">Court's open</" + Palette.TEXT + "> " + SEP + " "
                + "<" + Palette.MUTED + ">pick a mode</" + Palette.MUTED + ">",
            "<" + Palette.MUTED + ">Skipped the tour?</" + Palette.MUTED + "> " + SEP + " "
                + "<" + Palette.GOLD + ">/play practice</" + Palette.GOLD + ">"),
        new Beat(Stage.IDENTITY, 1, 2, Surface.WELCOME_CARD,
            "Your rank plate sits above your head",
            "Open your player card",
            "/stats",
            "<" + Palette.MUTED + ">You are a</" + Palette.MUTED + "> <" + Palette.TEXT + ">Rookie</"
                + Palette.TEXT + "> " + SEP + " <" + Palette.MUTED + ">your card is</" + Palette.MUTED
                + "> <" + Palette.GOLD + ">/stats</" + Palette.GOLD + ">",
            "<" + Palette.MUTED + ">Your card is one click away</" + Palette.MUTED + "> " + SEP + " "
                + "<" + Palette.GOLD + ">/stats</" + Palette.GOLD + ">"),
        new Beat(Stage.FIRST_SHOT, 2, 5, Surface.PRACTICE_COURT,
            "A gold ring and a rim marker show where to shoot",
            "Hold right-click with the ball, release in the green",
            "/practice",
            "<" + Palette.TEXT + ">Ball in hand</" + Palette.TEXT + "> " + SEP + " "
                + "<" + Palette.MUTED + ">the rim is marked</" + Palette.MUTED + ">",
            "<" + Palette.MUTED + ">Court's free</" + Palette.MUTED + "> " + SEP + " "
                + "<" + Palette.GOLD + ">/practice</" + Palette.GOLD + ">"),
        new Beat(Stage.FIRST_MATCH, 5, 8, Surface.MATCH_CARD,
            "Held slot 1 opens the mode menu",
            "Join the 1v1 queue",
            "/play 1v1",
            "<" + Palette.TEXT + ">Tip-off</" + Palette.TEXT + "> " + SEP + " "
                + "<" + Palette.MUTED + ">your first 1v1 is one click</" + Palette.MUTED + ">",
            "<" + Palette.MUTED + ">A 1v1 is waiting</" + Palette.MUTED + "> " + SEP + " "
                + "<" + Palette.GOLD + ">/play 1v1</" + Palette.GOLD + ">"),
        new Beat(Stage.FIRST_REWARD, 8, 10, Surface.REWARD_CARD,
            "The shop badge lights slot 2",
            "Open the shop",
            "/shop",
            "<" + Palette.GOLD_SOFT + ">First Coins</" + Palette.GOLD_SOFT + "> " + SEP + " "
                + "<" + Palette.MUTED + ">a ball or a dunk is yours</" + Palette.MUTED + ">",
            "<" + Palette.MUTED + ">Still got Coins to spend</" + Palette.MUTED + "> " + SEP + " "
                + "<" + Palette.GOLD + ">/shop</" + Palette.GOLD + ">")
    );

    private OnboardingScript() {
    }

    public static Beat beat(Stage stage) {
        for (Beat beat : SCRIPT) {
            if (beat.stage() == stage) {
                return beat;
            }
        }
        return null;
    }

    /**
     * The first milestone the player has not reached yet. Progress wins over the clock, but the
     * clock is the safety net: a skipped shot cannot keep the player on the practice court past
     * minute five, and a skipped match cannot keep them out of the reward beat past minute eight.
     * That is the "no strand" rule, expressed as a pure function.
     */
    public static Stage stageFor(Progress progress) {
        if (progress == null) {
            return Stage.JOIN;
        }
        if (!progress.identitySeen() && progress.minutesOnline() < 1) {
            return Stage.JOIN;
        }
        if (!progress.identitySeen()) {
            return Stage.IDENTITY;
        }
        if (!progress.shotTaken() && progress.minutesOnline() < 5) {
            return Stage.FIRST_SHOT;
        }
        if (!progress.matchPlayed() && progress.minutesOnline() < 8) {
            return Stage.FIRST_MATCH;
        }
        if (!progress.rewardClaimed()) {
            return Stage.FIRST_REWARD;
        }
        return Stage.DONE;
    }

    /**
     * The next action, in the player's own words. A skip always returns the fallback, and an
     * in-match player is never told to open a lobby menu while the ball is live. There is no state
     * in which this returns a blank string, including {@link Stage#DONE}.
     */
    public static String nextAction(Stage stage, boolean skipped, boolean inMatch) {
        Beat beat = beat(stage);
        if (beat == null) {
            return "/play 1v1";
        }
        if (inMatch && !skipped) {
            return "Hold right-click, release in the green";
        }
        return skipped ? beat.fallback() : beat.primary();
    }

    /** Which hotbar slot should be held so the next action is visible without reading anything. */
    public static int hotbarSlot(Stage stage) {
        return switch (stage) {
            case JOIN, IDENTITY, FIRST_MATCH, DONE -> PLAY_SLOT;
            case FIRST_REWARD -> SHOP_SLOT;
            case FIRST_SHOT -> 0;                     // the ball is already in the hand
        };
    }

    /** All the reasons the shipped script would be rejected. Empty means it is safe to run. */
    public static List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (SCRIPT.size() != 5) {
            problems.add("the script must have five beats");
        }
        int minute = 0;
        for (Beat beat : SCRIPT) {
            if (beat.fromMinute() != minute) {
                problems.add(beat.stage() + " leaves a gap at minute " + minute);
            }
            minute = beat.toMinute();
            if (beat.toMinute() <= beat.fromMinute()) {
                problems.add(beat.stage() + " has no time window");
            }
            if (beat.surface() == null || beat.visualCue().isBlank() || beat.primary().isBlank()
                || beat.fallback().isBlank()) {
                problems.add(beat.stage() + " has a blank surface, cue or action");
            }
            if (!beat.fallback().startsWith("/") && !beat.fallback().startsWith("Right-click")
                && !beat.fallback().startsWith("Hold")) {
                problems.add(beat.stage() + " fallback is not a real action");
            }
            problems.addAll(ScreenBounds.copyViolations(beat.copy()));
            problems.addAll(ScreenBounds.copyViolations(beat.skippedCopy()));
            int slot = hotbarSlot(beat.stage());
            if (slot < 0 || slot > 8) {
                problems.add(beat.stage() + " points at a hotbar slot that does not exist");
            }
            for (boolean skipped : new boolean[]{false, true}) {
                for (boolean inMatch : new boolean[]{false, true}) {
                    String action = nextAction(beat.stage(), skipped, inMatch);
                    if (action == null || action.isBlank() || action.indexOf('|') >= 0
                        || action.indexOf('\u2022') >= 0) {
                        problems.add(beat.stage() + " can strand a player");
                    }
                }
            }
        }
        if (minute != TOTAL_MINUTES) {
            problems.add("the script stops at minute " + minute + ", not " + TOTAL_MINUTES);
        }
        if (nextAction(Stage.DONE, true, false).isBlank()) {
            problems.add("a finished player has no next match to click");
        }
        if (DEAD_AIR_LIMIT_MS <= 1_600L || DEAD_AIR_LIMIT_MS > 30_000L) {
            problems.add("the cue interval would collide with a live toast");
        }
        return problems;
    }
}
