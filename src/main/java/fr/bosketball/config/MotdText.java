package fr.bosketball.config;

import fr.bosketball.util.Palette;
import java.util.List;

/**
 * The server-list banner, in one voice.
 *
 * <p>Round 45 rewrote this file rather than editing it. The banner used to be five English
 * marketing lines — "STREET BASKETBALL, PERFECTED", "JOIN THE COURT, OWN THE GAME" — each one bold,
 * inside its own gradient and framed by two ornaments: nine colours asked to shout at once, none of
 * them saying anything a player could use. On a French server that is also the most obvious sign
 * that the copy was generated rather than written.
 *
 * <p>The rules now:
 * <ul>
 *   <li>one accent, {@link Palette#GOLD}, for the wordmark and for the state word;</li>
 *   <li>everything else is {@link Palette#TEXT} or {@link Palette#MUTED}, no gradient, no bold
 *       outside the wordmark;</li>
 *   <li>the subtitle says something true and useful — what you can play, what season it is, where
 *       the players are — never a slogan;</li>
 *   <li>French, like the rest of the lobby. The {@code [TAB]} card is the only English surface.</li>
 * </ul>
 */
public final class MotdText {

    /** Slate diamond used to frame the banner lines. */
    public static final String ORNAMENT_SLATE = "<" + Palette.DIM + ">\u25C6</" + Palette.DIM + ">";
    /** The wordmark's two ornaments, in gold: the only ornament the banner is allowed to repeat. */
    public static final String ORNAMENT_GOLD = "<" + Palette.GOLD + ">\u25AA</" + Palette.GOLD + ">";

    /** {@code ▪ BOSKETBALL ▪} — gold, bold, nothing else. */
    public static final String DEFAULT_HEADER =
        ORNAMENT_GOLD + " <" + Palette.GOLD + "><bold>BOSKETBALL</bold></" + Palette.GOLD + "> " + ORNAMENT_GOLD;

    public static final List<String> DEFAULT_SUBTITLES = List.of(
        "<" + Palette.TEXT + ">Ranked 1v1 \u00b7 2v2 \u00b7 3v3 \u00b7 5v5</" + Palette.TEXT + "> <"
            + Palette.DIM + ">\u00b7</" + Palette.DIM + "> <" + Palette.MUTED + ">Practice libre</" + Palette.MUTED + ">",
        "<" + Palette.MUTED + ">Dunks, tirs \u00e0 3 points, saison class\u00e9e</" + Palette.MUTED + ">",
        "<" + Palette.MUTED + ">Discord</" + Palette.MUTED + "> <" + Palette.DIM + ">\u00b7</" + Palette.DIM + "> <"
            + Palette.TEXT + ">/discord</" + Palette.TEXT + "> <" + Palette.DIM + ">\u00b7</" + Palette.DIM + "> <"
            + Palette.MUTED + ">stats</" + Palette.MUTED + "> <" + Palette.TEXT + ">/stats</" + Palette.TEXT + ">"
    );

    public static final String DEFAULT_MAINTENANCE_HEADER =
        ORNAMENT_SLATE + " <" + Palette.MUTED + "><bold>BOSKETBALL</bold></" + Palette.MUTED + "> " + ORNAMENT_SLATE;

    public static final String DEFAULT_MAINTENANCE_SUBTITLE =
        "<" + Palette.WARN + ">Maintenance</" + Palette.WARN + "> <" + Palette.DIM + ">\u00b7</" + Palette.DIM
            + "> <" + Palette.MUTED + ">On revient tr\u00e8s vite</" + Palette.MUTED + ">";

    public static final String DEFAULT_STARTING_HEADER = DEFAULT_HEADER;

    public static final String DEFAULT_STARTING_SUBTITLE =
        "<" + Palette.WARN + ">D\u00e9marrage</" + Palette.WARN + "> <" + Palette.DIM + ">\u00b7</" + Palette.DIM
            + "> <" + Palette.MUTED + ">Les terrains se pr\u00e9parent</" + Palette.MUTED + ">";

    public static final String DEFAULT_CLOSED_HEADER =
        ORNAMENT_SLATE + " <" + Palette.MUTED + "><bold>BOSKETBALL</bold></" + Palette.MUTED + "> " + ORNAMENT_SLATE;

    public static final String DEFAULT_CLOSED_SUBTITLE =
        "<" + Palette.MUTED + ">Red\u00e9marrage</" + Palette.MUTED + "> <" + Palette.DIM + ">\u00b7</" + Palette.DIM
            + "> <" + Palette.MUTED + "\u00c0 tout de suite</" + Palette.MUTED + ">";

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
