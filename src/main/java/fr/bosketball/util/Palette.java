package fr.bosketball.util;

/**
 * The only place in the plugin where a colour is written down.
 *
 * <p>Before Round 45 the sources carried 47 different hexadecimal colours, including four yellows,
 * six reds and eight greys that were meant to be the same yellow, the same red and the same grey.
 * Nothing looked wrong line by line, but every screen drifted a little: the coin yellow of the
 * sidebar, the coin yellow of the tab card and the coin yellow of the shop were three different
 * values. That is what makes an interface read as assembled rather than designed.
 *
 * <p>So there are exactly thirteen colours now, each with a job, and {@code PaletteTest} refuses any
 * new {@code #RRGGBB} literal anywhere else in {@code src/main/java}. If a screen needs a nuance it
 * does not have, the answer is not a fifteenth colour: it is a different token, or less colour.
 *
 * <h2>The three neutrals</h2>
 * Every screen is built from {@link #TEXT}, {@link #MUTED} and {@link #DIM} plus, at most,
 * <em>one</em> accent. A screen that needs two accents is a screen with no hierarchy.
 *
 * <h2>The accents</h2>
 * {@link #GOLD} is reserved for money, rank and the one thing the player is being asked to look at.
 * {@link #SUCCESS}, {@link #DANGER}, {@link #WARN} and {@link #ICE} are semantics, never decoration:
 * an online player, a lost match, a maintenance banner, the frost team. {@link #EMBER} is the ignis
 * team. {@link #VIOLET} is cosmetics. {@link #BLURPLE} is Discord's own colour and is never reused.
 */
public final class Palette {

    /** Primary text: names, values, anything the player must read. */
    public static final String TEXT = "#F8FAFC";
    /** Secondary text: labels, hints, the quiet half of a sentence. */
    public static final String MUTED = "#94A3B8";
    /** Tertiary: separators, orphan rows, text that is present but should disappear. */
    public static final String DIM = "#64748B";

    /** The single accent of a screen. Money, rank, the call to action. */
    public static final String GOLD = "#FBBF24";
    /** A brighter gold, only for the number the player came for (a leaderboard value, a balance). */
    public static final String GOLD_SOFT = "#FDE047";
    /** Deep gold: borders, pressed states, the low stop of a gold gradient. */
    public static final String AMBER = "#D97706";
    /** Warm warning: maintenance, a starting server, a burning streak. */
    public static final String WARN = "#FB923C";

    /** Positive: an online friend, a win, an open queue. */
    public static final String SUCCESS = "#4ADE80";
    /** Negative: an offline friend, a loss, an error. */
    public static final String DANGER = "#F87171";
    /** The ignis team. */
    public static final String EMBER = "#FF5C4D";
    /** Information and the frost team. */
    public static final String ICE = "#38BDF8";
    /** Cosmetics, collectibles, rarity. */
    public static final String VIOLET = "#C084FC";

    /** Discord's brand colour. Only ever used to label Discord itself. */
    public static final String BLURPLE = "#5865F2";

    /** The palette as shipped, for the test that keeps {@code config.yml} honest. */
    public static final String[] ALL = {
        TEXT, MUTED, DIM, GOLD, GOLD_SOFT, AMBER, WARN, SUCCESS, DANGER, EMBER, ICE, VIOLET, BLURPLE
    };

    /**
     * The same colour, for the places that take a real {@code org.bukkit.Color} instead of a
     * MiniMessage tag: the aim arc, the rim halo, the particle dust.
     */
    public static org.bukkit.Color rgb(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        int rgb = Integer.parseInt(clean, 16);
        return org.bukkit.Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /** Wraps {@code text} in the colour, e.g. {@code Palette.of(Palette.GOLD, "1,420")}. */
    public static String of(String hex, String text) {
        return "<" + hex + ">" + text + "</" + hex + ">";
    }

    private Palette() {
    }
}
