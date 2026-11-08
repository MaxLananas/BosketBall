package fr.bosketball.util;

/**
 * What the two participants of a play are told.
 *
 * <p>The design rule this codebase learned the hard way: <em>the server only ever told the player
 * who succeeded</em>. An assist was paid and unannounced; a blocked shooter never learned why the
 * ball left their hands; an intercepted passer heard nothing. Every entry in this class therefore
 * comes in pairs — one line for the player who won the play, one for the player who lost it — and
 * each pair says the same thing twice in two different sentences, because losing a ball and taking
 * a ball are not the same experience.
 *
 * <h2>Voice</h2>
 * Never a system naming an event ("Blocked shot!", "Pass released"). A person telling you what
 * happened, in the fewest words a person would use courtside:
 * {@code "Stuffed · Kobe got a hand to it"} — head in the caller's colour, then the house middot,
 * then the detail that makes it make sense. The head is what happened to <em>you</em>; the detail
 * is who did it and why.
 *
 * <p>Everything here is pure and returns MiniMessage, so the whole voice of the HUD is asserted in
 * the unit tests instead of being reviewed by eye.
 */
public final class HudCopy {

    /** The house separator, built from the palette rather than written as raw hex. */
    public static final String SEP = " <" + Palette.DIM + ">\u00b7</" + Palette.DIM + "> ";

    /** Longest nickname the HUD will print inline before it is shortened for a line. */
    public static final int MAX_NAME_CHARS = 16;

    private HudCopy() {
    }

    /**
     * A line that is guaranteed to fit the pack's pill, whatever the nickname in it.
     *
     * <p>The name is the only unbounded part of a HUD sentence, and it is exactly what used to push
     * a toast off the artwork: with a 16-character name, {@code "Stuffed · AAAAAAAAAAAAAAAA at the
     * rim"} is 171 px and the pill tops out at 156. So the copy carries two detail forms and picks
     * one: the full sentence when it fits, a shorter one when it does not, and the head alone as the
     * last resort. The player always gets one language and one pill; only the verbosity changes.
     */
    private static String pillLine(String hex, String head, String detail, String shortDetail, boolean bold) {
        String full = line(hex, head, detail, bold);
        if (HudWidth.fitsPill(full)) {
            return full;
        }
        String shortForm = line(hex, head, shortDetail, bold);
        if (HudWidth.fitsPill(shortForm)) {
            return shortForm;
        }
        return line(hex, head, null, bold);
    }

    private static String line(String hex, String head, String detail, boolean bold) {
        String styled = bold ? "<bold>" + head + "</bold>" : head;
        if (detail == null || detail.isBlank()) {
            return "<" + hex + ">" + styled + "</" + hex + ">";
        }
        return "<" + hex + ">" + styled + "</" + hex + ">" + SEP
            + "<" + Palette.MUTED + ">" + detail + "</" + Palette.MUTED + ">";
    }

    /** A nickname, cut so one long name can never push a HUD line over the width contract. */
    public static String name(String raw) {
        if (raw == null || raw.isBlank()) {
            return "someone";
        }
        return raw.length() <= MAX_NAME_CHARS ? raw : raw.substring(0, MAX_NAME_CHARS) + "\u2026";
    }

    // ---------------------------------------------------------------------------------------
    // Blocks: the shooter is the one who needs the sentence, and never got it.
    // ---------------------------------------------------------------------------------------

    /** Told to the defender who got a hand to it. */
    public static String blockWon(String shooterName, boolean atRim) {
        return pillLine(Palette.ICE, atRim ? "Pinned it" : "Sent it back",
            name(shooterName) + (atRim ? "'s dunk" : "'s shot"),
            name(shooterName) + (atRim ? "'s dunk" : "'s shot"), true);
    }

    /** Told to the shooter whose ball just left their hands. */
    public static String blockLost(String blockerName, boolean atRim) {
        return pillLine(Palette.DANGER, "Stuffed",
            atRim ? name(blockerName) + " at the rim" : name(blockerName) + " got it",
            name(blockerName), true);
    }

    // ---------------------------------------------------------------------------------------
    // Steals and pokes.
    // ---------------------------------------------------------------------------------------

    /** The defender who took the ball cleanly. */
    public static String stealWon(String victimName) {
        return pillLine(Palette.SUCCESS, "Took it", "off " + name(victimName), name(victimName), true);
    }

    /** The player who just lost the ball to a clean steal. */
    public static String stealLost(String thiefName) {
        return pillLine(Palette.DANGER, "Stripped", name(thiefName) + " took it", name(thiefName), true);
    }

    /** The defender who knocked the ball loose without controlling it. */
    public static String pokeWon(String victimName) {
        return pillLine(Palette.ICE, "Poked loose", name(victimName) + " lost it",
            name(victimName), true);
    }

    /** The player whose dribble was knocked away. */
    public static String pokeLost(String defenderName) {
        return pillLine(Palette.DANGER, "Knocked away", name(defenderName) + " got it",
            name(defenderName), true);
    }

    // ---------------------------------------------------------------------------------------
    // Passes.
    // ---------------------------------------------------------------------------------------

    /** The interceptor. */
    public static String interceptWon(String passerName) {
        return pillLine(Palette.SUCCESS, "Read it", name(passerName) + "'s pass", name(passerName), true);
    }

    /** The passer whose ball was read — the player nobody used to tell. */
    public static String interceptLost(String interceptorName) {
        return pillLine(Palette.DANGER, "Picked off", name(interceptorName) + " read it",
            name(interceptorName), true);
    }

    /** The passer, when the pass went where it was aimed. */
    public static String passTo(String receiverName) {
        return pillLine(Palette.ICE, "Pass", "to " + name(receiverName), name(receiverName), false);
    }

    /** The passer, when there was nobody to pass to. What the situation is, not "Pass released". */
    public static String passToNobody() {
        return line(Palette.ICE, "No pass", "nobody in front of you", false);
    }

    // ---------------------------------------------------------------------------------------
    // Ankles.
    // ---------------------------------------------------------------------------------------

    /** The handler who put a defender on the floor. */
    public static String ankleWon(String defenderName) {
        return pillLine(Palette.VIOLET, "Ankles", name(defenderName) + " hit the deck",
            name(defenderName), true);
    }

    /** The defender who was crossed. */
    public static String ankleLost(String handlerName) {
        return pillLine(Palette.DANGER, "Crossed", name(handlerName) + " dropped you",
            name(handlerName), true);
    }

    // ---------------------------------------------------------------------------------------
    // Clock and possession: the same event, told to both benches.
    // ---------------------------------------------------------------------------------------

    /** The team that ran out the shot clock. */
    public static String shotClockLost() {
        return line(Palette.DANGER, "Turnover", "no shot in 24", true);
    }

    /** The team handed the ball by the violation. */
    public static String shotClockWon() {
        return line(Palette.SUCCESS, "Your ball", "they never shot", true);
    }

    /** The team that put the ball out of bounds. */
    public static String outOfBounds(String teamName) {
        return line(Palette.GOLD, "Out of bounds", name(teamName), false);
    }

    // ---------------------------------------------------------------------------------------
    // Scoring helpers and coaching, where the second person is the passer.
    // ---------------------------------------------------------------------------------------

    /** The passer, when their pass turned into points — paid, and now announced. */
    public static String assist(String scorerName) {
        return pillLine(Palette.SUCCESS, "+1 AST", name(scorerName) + " finished it",
            name(scorerName), true);
    }

    /** A dunk attempt from outside the paint: what to do, not what the plugin refused. */
    public static String coachStepInside() {
        return line(Palette.GOLD, "Too far out", "get closer", false);
    }

    /** A dunk attempt facing away from the rim. */
    public static String coachFaceTheRim() {
        return line(Palette.GOLD, "Face the rim", "then slam", false);
    }

    /** A dunk still on cooldown. */
    public static String coachDunkRecharging(String remaining) {
        return line(Palette.GOLD, "Dunk recharging", remaining, false);
    }

    /** A dash still on cooldown. */
    public static String coachDashRecharging(String remaining) {
        return line(Palette.GOLD, "Dash recharging", remaining, false);
    }
}
