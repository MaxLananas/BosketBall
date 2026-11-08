package fr.bosketball.social;

import fr.bosketball.server.ScreenBounds;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.List;

/**
 * Copy for the four social surfaces a player meets after the first ten minutes.
 *
 * <p>An empty state has two jobs: tell the truth ("No party yet", never "0 friends online!") and
 * hand back one next action. A reason has one job: say why this surface is worth opening
 * <em>inside a match</em>, not just in a lobby menu. And because this codebase learned the hard way
 * that the server used to tell only the player who succeeded, every social action returns two lines:
 * one for the actor and one for the other participant, in that person's own words.
 *
 * <p>Everything here is pure data so the house style can be asserted without a server: one
 * separator, palette colours only, English only, no system voice.
 */
public final class SocialSurfaces {

    public enum Surface {
        PARTY,
        FRIEND,
        INVITE,
        SPECTATE
    }

    /** An honest empty state and the one action that gets the player out of it. */
    public record EmptyState(String line, String nextAction) {
    }

    /** The two people an action touches. Both lines are written for the person reading them. */
    public record TwoSided(String actorLine, String targetLine) {
    }

    private static final String SEP = ScreenBounds.separator();

    private SocialSurfaces() {
    }

    private static String muted(String value) {
        return "<" + Palette.MUTED + ">" + value + "</" + Palette.MUTED + ">";
    }

    private static String text(String value) {
        return "<" + Palette.TEXT + ">" + value + "</" + Palette.TEXT + ">";
    }

    private static String ice(String value) {
        return "<" + Palette.ICE + ">" + value + "</" + Palette.ICE + ">";
    }

    private static String gold(String value) {
        return "<" + Palette.GOLD + ">" + value + "</" + Palette.GOLD + ">";
    }

    public static EmptyState empty(Surface surface) {
        return switch (surface) {
            case PARTY -> new EmptyState(
                muted("No party yet") + " " + SEP + " " + text("bring someone from the court"),
                "Right-click a player to invite them");
            case FRIEND -> new EmptyState(
                muted("Nobody on your list yet") + " " + SEP + " " + text("add someone you just played"),
                "Right-click a player to add them");
            case INVITE -> new EmptyState(
                muted("No invites waiting") + " " + SEP + " " + text("send one after the next point"),
                "/party invite");
            case SPECTATE -> new EmptyState(
                muted("No live court to watch") + " " + SEP + " " + text("queue for the next one"),
                "/play 1v1");
        };
    }

    /** Why open this surface while the ball is live, in one short line. */
    public static String reasonInMatch(Surface surface) {
        return switch (surface) {
            case PARTY -> muted("A party puts your whole roster in one 2v2");
            case FRIEND -> muted("Add the player who just passed you the ball");
            case INVITE -> muted("Bring a teammate in before the next tip-off");
            case SPECTATE -> muted("Stay after a loss and watch the next run");
        };
    }

    /**
     * Both participants, in their own words. Names are escaped before they reach MiniMessage: a
     * nickname is untrusted input, and a stray tag in a name used to break the whole social card.
     */
    public static TwoSided lines(Surface surface, String actor, String target) {
        String a = Msg.escapeMini(actor == null ? "Someone" : actor);
        String t = Msg.escapeMini(target == null ? "someone" : target);
        return switch (surface) {
            case PARTY -> new TwoSided(
                muted(t + " is in your party") + " " + SEP + " " + text("say hi with /pc"),
                muted(a + " is on your bench") + " " + SEP + " " + text("say hi with /pc"));
            case FRIEND -> new TwoSided(
                muted(t + " is on your list") + " " + SEP + " " + text("say hi with /msg"),
                muted(a + " added you") + " " + SEP + " " + text("say hi with /msg"));
            case INVITE -> new TwoSided(
                muted("Invite out") + " " + SEP + " " + text(t + " has one click to join"),
                muted(a + " wants you in the next 2v2") + " " + SEP + " "
                    + ice("<click:run_command:'/party accept'>▸ JOIN</click>"));
            case SPECTATE -> new TwoSided(
                muted("You are watching " + t + " on the wing"),
                muted(a + " is on your sideline") + " " + SEP + " " + text("watching your lane"));
        };
    }

    /**
     * A friend request, told to both people. The actor hears that the request is out; the target
     * hears who asked and gets the one click that answers it. No bracketed system buttons.
     */
    public static TwoSided requestLines(String actor, String target) {
        String a = Msg.escapeMini(actor == null ? "Someone" : actor);
        String t = Msg.escapeMini(target == null ? "someone" : target);
        return new TwoSided(
            muted("Request out") + " " + SEP + " " + text(t + " has one click to add you back"),
            muted(a + " wants you on their list") + " " + SEP + " "
                + ice("<click:run_command:'/friend add " + a + "'>▸ ADD</click>") + " " + SEP + " "
                + "<" + Palette.DANGER + "><click:run_command:'/friend decline " + a
                + "'>PASS</click></" + Palette.DANGER + ">");
    }

    /**
     * Passing on a request or an invite is also a two-person action. The person who passed hears
     * what they did and where to go next; the person who asked hears it in their own words, with a
     * next action too. No "Declined party invitation." system line.
     */
    public static TwoSided declined(Surface surface, String actor, String target) {
        String a = Msg.escapeMini(actor == null ? "Someone" : actor);
        String t = Msg.escapeMini(target == null ? "someone" : target);
        return switch (surface) {
            case FRIEND -> new TwoSided(
                muted("Passed on " + t + "'s request") + " " + SEP + " " + gold("/play 1v1"),
                muted(a + " passed on your request") + " " + SEP + " " + gold("/play 1v1"));
            case INVITE -> new TwoSided(
                muted("Passed on " + t + "'s invite") + " " + SEP + " " + gold("/play 1v1"),
                muted(a + " passed on your invite") + " " + SEP + " " + gold("/party invite"));
            case PARTY -> new TwoSided(
                muted("Passed on " + t + "'s bench") + " " + SEP + " " + gold("/party"),
                muted(a + " passed on your bench") + " " + SEP + " " + gold("/party invite"));
            case SPECTATE -> new TwoSided(
                muted("Left " + t + "'s sideline") + " " + SEP + " " + gold("/play 1v1"),
                muted(a + " left your sideline") + " " + SEP + " " + gold("/play 1v1"));
        };
    }

    /** Off the list or off the bench: both people hear it, with one thing to do afterwards. */
    public static TwoSided removed(Surface surface, String actor, String target) {
        String a = Msg.escapeMini(actor == null ? "Someone" : actor);
        String t = Msg.escapeMini(target == null ? "someone" : target);
        return switch (surface) {
            case FRIEND -> new TwoSided(
                muted(t + " is off your list") + " " + SEP + " " + gold("/friends"),
                muted(a + " took you off their list") + " " + SEP + " " + gold("/friends"));
            case PARTY -> new TwoSided(
                muted(t + " is off the bench") + " " + SEP + " " + gold("/party"),
                muted(a + " took you off the bench") + " " + SEP + " " + gold("/play 1v1"));
            case INVITE -> new TwoSided(
                muted(t + "'s invite is gone") + " " + SEP + " " + gold("/party invite"),
                muted(a + " let your invite go") + " " + SEP + " " + gold("/play 1v1"));
            case SPECTATE -> new TwoSided(
                muted(t + " is off your sideline") + " " + SEP + " " + gold("/play 1v1"),
                muted(a + " is off your sideline") + " " + SEP + " " + gold("/play 1v1"));
        };
    }

    /** A click on a request or invite that has already been taken, answered or expired. */
    public static String staleAction(Surface surface) {
        String next = switch (surface) {
            case FRIEND -> "/friends";
            case INVITE -> "/party invite";
            default -> "/play 1v1";
        };
        return muted("That one is gone") + " " + SEP + " " + gold(next);
    }

    /** One line that combines an honest empty state with its next action, for chat surfaces. */
    public static String emptyLine(Surface surface) {
        EmptyState state = empty(surface);
        return state.line() + " " + SEP + " " + gold(state.nextAction());
    }

    /** Every shipped line, for the style / MiniMessage test. */
    public static List<String> allCopy() {
        List<String> out = new ArrayList<>();
        for (Surface surface : Surface.values()) {
            out.add(empty(surface).line());
            out.add(emptyLine(surface));
            out.add(reasonInMatch(surface));
            out.add(staleAction(surface));
            TwoSided sides = lines(surface, "Kobe", "Jalen");
            out.add(sides.actorLine());
            out.add(sides.targetLine());
            TwoSided passed = declined(surface, "Kobe", "Jalen");
            out.add(passed.actorLine());
            out.add(passed.targetLine());
            TwoSided gone = removed(surface, "Kobe", "Jalen");
            out.add(gone.actorLine());
            out.add(gone.targetLine());
        }
        TwoSided request = requestLines("Kobe", "Jalen");
        out.add(request.actorLine());
        out.add(request.targetLine());
        return out;
    }
}
