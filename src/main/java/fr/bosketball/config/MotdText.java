package fr.bosketball.config;

import fr.bosketball.util.Palette;
import java.util.List;

/**
 * The server-list banner, in one voice.
 *
 * <p>Round 45 rewrote this file rather than editing it. The banner used to be five English
 * marketing lines — "STREET BASKETBALL, PERFECTED", "JOIN THE COURT, OWN THE GAME" — each one bold,
 * inside its own gradient and framed by two ornaments: nine colours asked to shout at once, none of
 * them saying anything a player could use.
 *
 * <p>The community pass keeps the rules and changes the register. A ping payload has two short
 * lines and about 45 visible characters per line; that is enough to say what this place is right
 * now, not enough for a changelog, a patch note or a slogan. So the three rotating lines read like a
 * courtside aside: what is open, what to pick up first, where the other players are. One accent,
 * {@link Palette#GOLD}, the square mark {@code ▪}, and the single dim middot separator.
 *
 * <p>English, like the whole server. No stars, no pipe, no bullet, no generated marketing verbs.
 */
public final class MotdText {

    /** The house mark, in slate, for the offline states. */
    public static final String ORNAMENT_SLATE = "<" + Palette.DIM + ">\u25AA</" + Palette.DIM + ">";
    /** The wordmark's two ornaments, in gold: the only ornament the banner repeats. */
    public static final String ORNAMENT_GOLD = "<" + Palette.GOLD + ">\u25AA</" + Palette.GOLD + ">";
    /** The only separator a banner line may use. */
    public static final String DOT = "<" + Palette.DIM + ">\u00b7</" + Palette.DIM + ">";

    /** {@code ▪ BOSKETBALL ▪} — gold, bold, nothing else. */
    public static final String DEFAULT_HEADER =
        ORNAMENT_GOLD + " <" + Palette.GOLD + "><bold>BOSKETBALL</bold></" + Palette.GOLD + "> " + ORNAMENT_GOLD;

    public static final List<String> DEFAULT_SUBTITLES = List.of(
        "<" + Palette.TEXT + ">1v1, 2v2, 5v5</" + Palette.TEXT + "> " + DOT + " <"
            + Palette.MUTED + ">Court's open</" + Palette.MUTED + ">",
        "<" + Palette.MUTED + ">Pick a ball, find a rim</" + Palette.MUTED + "> " + DOT + " <"
            + Palette.TEXT + ">/practice</" + Palette.TEXT + ">",
        "<" + Palette.MUTED + ">Sideline's open</" + Palette.MUTED + "> " + DOT + " <"
            + Palette.TEXT + ">/party</" + Palette.TEXT + "> " + DOT + " <"
            + Palette.MUTED + ">/discord</" + Palette.MUTED + ">"
    );

    public static final String DEFAULT_MAINTENANCE_HEADER =
        ORNAMENT_SLATE + " <" + Palette.MUTED + "><bold>BOSKETBALL</bold></" + Palette.MUTED + "> " + ORNAMENT_SLATE;

    public static final String DEFAULT_MAINTENANCE_SUBTITLE =
        "<" + Palette.WARN + ">Maintenance</" + Palette.WARN + "> " + DOT + " <"
            + Palette.MUTED + ">Back on the court soon</" + Palette.MUTED + ">";

    public static final String DEFAULT_STARTING_HEADER = DEFAULT_HEADER;

    public static final String DEFAULT_STARTING_SUBTITLE =
        "<" + Palette.WARN + ">Starting up</" + Palette.WARN + "> " + DOT + " <"
            + Palette.MUTED + ">Preparing the courts</" + Palette.MUTED + ">";

    public static final String DEFAULT_CLOSED_HEADER =
        ORNAMENT_SLATE + " <" + Palette.MUTED + "><bold>BOSKETBALL</bold></" + Palette.MUTED + "> " + ORNAMENT_SLATE;

    public static final String DEFAULT_CLOSED_SUBTITLE =
        "<" + Palette.MUTED + ">Restarting</" + Palette.MUTED + "> " + DOT + " <"
            + Palette.MUTED + ">See you in a moment</" + Palette.MUTED + ">";

    /**
     * Round 44 — the same ornament rule as everywhere else, applied to a value that comes from disk.
     *
     * <p>{@code config.yml} of a server installed before the star sweep still carries the old
     * four-pointed stars in {@code motd.header} and in the four state headers: the file is written
     * once and then never touched, so a shipped default is not enough. Every MOTD string read from
     * the config therefore goes through this, and the owner's rule holds for existing installs too.
     */
    public static String withoutStars(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value
            .replace((char) 0x2726, (char) 0x25AA)
            .replace((char) 0x2727, (char) 0x25AA)
            .replace((char) 0x2605, (char) 0x25AA)
            .replace((char) 0x2606, (char) 0x25AA);
    }

    private MotdText() {
    }
}
