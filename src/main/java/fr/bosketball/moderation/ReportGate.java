package fr.bosketball.moderation;

import fr.bosketball.server.ScreenBounds;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure rules behind the two-click report path and the one-click staff action.
 *
 * <p>Filing a report used to be a command a player had to know by heart, and the only spam guard
 * was an in-memory 30-second cooldown that vanished on restart. The path is now: sneak + right-click
 * the player (one click), then click one of four reasons (the second click). The staff broadcast
 * carries a {@code [TAKE]} button that claims the report in that single click.
 *
 * <p>Every rejection is a short line in a person's voice ("Staff already have this one", never
 * "Operation failed"). All decisions are pure so the spam cases can be driven in a unit test
 * without Bukkit, a clock or a database.
 */
public final class ReportGate {

    /** One report every 30 seconds, three open at a time, one duplicate per target. */
    public static final long COOLDOWN_MS = 30_000L;
    public static final int MAX_OPEN_REPORTS = 3;
    public static final int MAX_REASON_CHARS = 120;

    /** The four reasons a player can click; each one maps to exactly one {@code /report} command. */
    public static final List<String> REASONS = List.of(
        "Cheating", "Chat abuse", "AFK in match", "Griefing");

    /** Player-facing constants of the card and the staff broadcast. All of them are style-tested. */
    public static final String CARD_TITLE = "REPORT";
    public static final String TAKE_BUTTON = "▸ TAKE";
    public static final String PANEL_BUTTON = "PANEL";
    public static final String PANEL_COMMAND = "/bb reports";

    public enum Verdict {
        ACCEPT,
        SELF_REPORT,
        BAD_TARGET,
        BAD_REASON,
        COOLDOWN,
        DUPLICATE,
        TOO_MANY
    }

    /** The verdict and the line the reporter reads. */
    public record Decision(Verdict verdict, String line) {
    }

    /** What each of the two participants hears when staff take a report. */
    public record Handling(String staffLine, String reporterLine) {
    }

    private static final String SEP = ScreenBounds.separator();

    private ReportGate() {
    }

    private static String muted(String value) {
        return "<" + Palette.MUTED + ">" + value + "</" + Palette.MUTED + ">";
    }

    private static String text(String value) {
        return "<" + Palette.TEXT + ">" + value + "</" + Palette.TEXT + ">";
    }

    private static String gold(String value) {
        return "<" + Palette.GOLD + ">" + value + "</" + Palette.GOLD + ">";
    }

    private static String danger(String value) {
        return "<" + Palette.DANGER + ">" + value + "</" + Palette.DANGER + ">";
    }

    /**
     * The whole spam guard. {@code lastReportMs} is the reporter's last accepted report,
     * {@code duplicateOpen} means an OPEN report from this reporter about this target already
     * exists, and {@code openReports} is the reporter's total number of OPEN reports.
     */
    public static Decision decide(long nowMs, long lastReportMs, boolean selfReport,
                                  boolean duplicateOpen, int openReports,
                                  String targetName, String reason) {
        String who = text(Msg.escapeMini(targetName == null ? "that player" : targetName));
        if (selfReport) {
            return new Decision(Verdict.SELF_REPORT, danger("That's your own name."));
        }
        if (targetName == null || targetName.isBlank()) {
            return new Decision(Verdict.BAD_TARGET, muted("Name the player you saw."));
        }
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_CHARS) {
            return new Decision(Verdict.BAD_REASON, muted("Add one line about what happened."));
        }
        if (lastReportMs > 0L && nowMs - lastReportMs < COOLDOWN_MS) {
            return new Decision(Verdict.COOLDOWN, muted("Staff already have your last report."));
        }
        if (duplicateOpen) {
            return new Decision(Verdict.DUPLICATE, muted("Staff already have this one about ") + who);
        }
        if (openReports >= MAX_OPEN_REPORTS) {
            return new Decision(Verdict.TOO_MANY,
                muted("Three reports are open") + " " + SEP + " " + gold("give staff a minute"));
        }
        return new Decision(Verdict.ACCEPT,
            muted("Report on ") + who + " " + SEP + " " + text("staff have it"));
    }

    /** The command a reason button runs. Names are escaped before they reach MiniMessage. */
    public static String reportCommand(String targetName, String reason) {
        return "/report " + Msg.escapeMini(targetName) + " " + reason;
    }

    /** Four clickable reason lines, one click each, for the card opened by the first click. */
    public static List<String> reasonLines(String targetName) {
        List<String> out = new ArrayList<>();
        for (String reason : REASONS) {
            out.add("<" + Palette.GOLD_SOFT + ">▸ <click:run_command:'"
                + reportCommand(targetName, reason) + "'>" + reason + "</click></" + Palette.GOLD_SOFT + ">");
        }
        return out;
    }

    /** The card's one-line explanation of how the path is found. */
    public static String discoverLine() {
        return muted("Sneak + right-click a player to report them");
    }

    /** The card's header: who is being reported, with the name escaped. */
    public static String cardHeader(String targetName) {
        return muted("Reporting") + " " + SEP + " "
            + text(Msg.escapeMini(targetName == null ? "that player" : targetName));
    }

    /** The staff broadcast, including the one-click TAKE button, as data for the style test. */
    public static String broadcastLine(long reportId, String reporterName, String reportedName, String reason) {
        String reporter = Msg.escapeMini(reporterName == null ? "a player" : reporterName);
        String reported = Msg.escapeMini(reportedName == null ? "that player" : reportedName);
        String why = Msg.escapeMini(reason == null ? "" : reason);
        return "<" + Palette.DANGER + "><bold>REPORT #" + reportId + "</bold></" + Palette.DANGER + "> "
            + "<" + Palette.TEXT + ">" + reporter + "</" + Palette.TEXT + "> "
            + muted("on") + " <" + Palette.TEXT + ">" + reported + "</" + Palette.TEXT + "> "
            + SEP + " " + muted(why.isBlank() ? "no reason given" : why) + " "
            + "<" + Palette.ICE + "><click:run_command:'" + staffCommand(reportId) + "'>"
            + TAKE_BUTTON + "</click></" + Palette.ICE + "> " + SEP + " "
            + "<" + Palette.DIM + "><click:run_command:'" + PANEL_COMMAND + "'>"
            + PANEL_BUTTON + "</click></" + Palette.DIM + ">";
    }

    /** The command the staff button runs: one click claims the report. */
    public static String staffCommand(long reportId) {
        return "/reports " + reportId;
    }

    /** A stale TAKE button, or a mistyped report number, gets an honest answer. */
    public static String noOpenReportLine() {
        return muted("No open report with that number");
    }

    /** Both participants hear what happened, in their own words. */
    public static Handling handling(String staffName, String reporterName, String reportedName) {
        String staff = Msg.escapeMini(staffName == null ? "Staff" : staffName);
        String reporter = Msg.escapeMini(reporterName == null ? "the reporter" : reporterName);
        String reported = Msg.escapeMini(reportedName == null ? "that player" : reportedName);
        return new Handling(
            text(staff + " took the report") + " " + SEP + " " + muted("it is yours now"),
            muted(staff + " picked up your report on ") + text(reported));
    }

    /** Every shipped line, for the style / MiniMessage test. */
    public static List<String> allCopy() {
        List<String> out = new ArrayList<>();
        out.add(CARD_TITLE);
        out.add(TAKE_BUTTON);
        out.add(PANEL_BUTTON);
        out.add(discoverLine());
        out.add(noOpenReportLine());
        out.add(cardHeader("Kobe"));
        out.add(broadcastLine(12L, "Kobe", "Shaq", "Cheating"));
        out.addAll(reasonLines("Kobe"));
        out.add(handling("Jalen", "Kobe", "Shaq").staffLine());
        out.add(handling("Jalen", "Kobe", "Shaq").reporterLine());
        for (Verdict verdict : Verdict.values()) {
            Decision sample = decide(0L, 0L, verdict == Verdict.SELF_REPORT,
                verdict == Verdict.DUPLICATE, verdict == Verdict.TOO_MANY ? MAX_OPEN_REPORTS : 0,
                verdict == Verdict.BAD_TARGET ? "" : "Kobe",
                verdict == Verdict.BAD_REASON ? "" : "Cheating");
            if (sample.verdict() == Verdict.COOLDOWN) {
                sample = decide(COOLDOWN_MS - 1L, 1L, false, false, 0, "Kobe", "Cheating");
            }
            out.add(sample.line());
        }
        return out;
    }
}
