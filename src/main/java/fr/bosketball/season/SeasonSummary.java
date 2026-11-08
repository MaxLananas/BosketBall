package fr.bosketball.season;

import fr.bosketball.model.Rank;
import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.List;

/**
 * The end-of-season card a player screenshots.
 *
 * <p>This is the artefact the whole season is for. The broadcast archive card (in
 * {@code SeasonService}) tells the <em>server</em> who won; this card tells <em>one player</em>
 * what their season was, in a form that survives being posted in Discord.
 *
 * <h2>What is on it</h2>
 * Four numbers a player actually wants to keep — peak rating, rank reached, games played, wins —
 * plus the two the reward is made of: the coins the tier paid and the podium bonus. Nothing else.
 * A card that carries a lifetime stat and a season stat side by side is a card nobody can read in
 * a screenshot.
 *
 * <h2>House style</h2>
 * <ul>
 *   <li>The separator is the middot, {@code <#64748B>·</#64748B>}. Never a pipe, never a bullet,
 *       never a dash.</li>
 *   <li>The house mark for a screen title is {@code ▪}.</li>
 *   <li>Colours come from {@link Palette}: the player's name in {@code TEXT}, the headline numbers
 *       in {@code GOLD_SOFT}, the labels in {@code MUTED}, the tier in {@code ICE}, the podium in
 *       {@code GOLD}.</li>
 *   <li>One card. The summary never fires a second title or a second toast — the card is the
 *       whole moment.</li>
 * </ul>
 *
 * <h2>Copy</h2>
 * The voice is the same as the rest of the server: short, active, no system talking. "SEASON III —
 * YOUR SEASON", not "Season 3 results are as follows". The card says what happened; it never
 * congratulates the player on a system operation.
 */
public final class SeasonSummary {

    /** The header every player's season card carries. */
    public static final String CARD_HEADER_SUFFIX = "— YOUR SEASON";

    private final String playerName;
    private final String seasonName;
    private final int seasonIndex;
    private final int place;
    private final int bestElo;
    private final Rank tier;
    private final int matches;
    private final int wins;
    private final int coinsEarned;
    private final int podiumBonus;

    /**
     * Builds a summary.
     *
     * <p>Public because the season service builds one per player at the rollover, and the test
     * builds one directly to assert the card's shape.
     */
    public SeasonSummary(String playerName, String seasonName, int seasonIndex, int place,
                         int bestElo, Rank tier, int matches, int wins, int coinsEarned,
                         int podiumBonus) {
        this.playerName = playerName == null || playerName.isBlank() ? "Player" : playerName;
        this.seasonName = seasonName == null || seasonName.isBlank()
            ? SeasonMath.seasonName(Math.max(1, seasonIndex)) : seasonName;
        this.seasonIndex = Math.max(1, seasonIndex);
        this.place = Math.max(0, place);
        this.bestElo = SeasonMath.clampElo(bestElo);
        this.tier = tier == null ? SeasonMath.tierFor(this.bestElo) : tier;
        this.matches = Math.max(0, matches);
        this.wins = Math.max(0, Math.min(wins, this.matches));
        this.coinsEarned = Math.max(0, coinsEarned);
        this.podiumBonus = Math.max(0, podiumBonus);
    }

    /**
     * Builds the summary of a finished season for one player.
     *
     * @param playerName the player's name
     * @param seasonName the season's name, e.g. {@code SEASON III}
     * @param seasonIndex the season's index
     * @param place the player's place in the overall standings (0 = unplaced)
     * @param bestElo the player's peak rating this season
     * @param matches the ranked matches the player played
     * @param wins the ranked matches the player won
     * @param coinsEarned the coins the player's tier paid
     * @param podiumBonus the extra coins the podium paid
     */
    public static SeasonSummary of(String playerName, String seasonName, int seasonIndex, int place,
                                   int bestElo, Rank tier, int matches, int wins,
                                   int coinsEarned, int podiumBonus) {
        return new SeasonSummary(playerName, seasonName, seasonIndex, place, bestElo, tier,
            matches, wins, coinsEarned, podiumBonus);
    }

    /** The player's name. */
    public String playerName() {
        return playerName;
    }

    /** The season's name. */
    public String seasonName() {
        return seasonName;
    }

    /** The player's place in the standings; 0 means unplaced. */
    public int place() {
        return place;
    }

    /** The player's peak rating this season. */
    public int bestElo() {
        return bestElo;
    }

    /** The tier the peak rating earned. */
    public Rank tier() {
        return tier;
    }

    /** The ranked matches the player played. */
    public int matches() {
        return matches;
    }

    /** The ranked matches the player won. */
    public int wins() {
        return wins;
    }

    /** The ranked matches the player lost. */
    public int losses() {
        return Math.max(0, matches - wins);
    }

    /** The win rate, as a whole percentage. */
    public int winRatePercent() {
        if (matches <= 0) {
            return 0;
        }
        return (int) Math.round(wins * 100.0 / matches);
    }

    /** The coins the tier paid. */
    public int coinsEarned() {
        return coinsEarned;
    }

    /** The extra coins the podium paid. */
    public int podiumBonus() {
        return podiumBonus;
    }

    /** The coins the season paid in total. */
    public int totalCoins() {
        return coinsEarned + podiumBonus;
    }

    /**
     * The card header.
     *
     * <p>{@code SEASON III — YOUR SEASON}. The em dash is the header's own punctuation, not a
     * separator inside a HUD line, so the house separator rule does not apply to it.
     */
    public String header() {
        return seasonName + " " + CARD_HEADER_SUFFIX;
    }

    /**
     * The card's title line: the player's name and the tier they reached.
     */
    public String titleLine() {
        return "<" + Palette.TEXT + "><bold>" + playerName + "</bold></" + Palette.TEXT + "> "
            + "<" + Palette.DIM + ">·</" + Palette.DIM + "> "
            + "<" + Palette.ICE + ">" + tier.plainDisplayName() + "</" + Palette.ICE + ">";
    }

    /**
     * The card body — four lines, each one number the player wants to keep.
     *
     * <p>Every separator in here is the house middot.
     */
    public List<String> body() {
        List<String> lines = new ArrayList<>();

        lines.add("<" + Palette.MUTED + ">Peak rating</" + Palette.MUTED + "> "
            + "<" + Palette.GOLD_SOFT + "><bold>" + bestElo + "</bold></" + Palette.GOLD_SOFT + "> "
            + "<" + Palette.DIM + ">·</" + Palette.DIM + "> "
            + "<" + Palette.ICE + ">" + tier.plainDisplayName() + "</" + Palette.ICE + ">");

        lines.add("<" + Palette.MUTED + ">Games</" + Palette.MUTED + "> "
            + "<" + Palette.TEXT + ">" + matches + "</" + Palette.TEXT + "> "
            + "<" + Palette.DIM + ">·</" + Palette.DIM + "> "
            + "<" + Palette.SUCCESS + ">" + wins + " won</" + Palette.SUCCESS + "> "
            + "<" + Palette.DIM + ">·</" + Palette.DIM + "> "
            + "<" + Palette.DANGER + ">" + losses() + " lost</" + Palette.DANGER + ">");

        lines.add("<" + Palette.MUTED + ">Win rate</" + Palette.MUTED + "> "
            + "<" + Palette.TEXT + ">" + winRatePercent() + "%</" + Palette.TEXT + ">");

        lines.add("<" + Palette.MUTED + ">Reward</" + Palette.MUTED + "> "
            + "<" + Palette.GOLD_SOFT + ">+" + coinsEarned + "</" + Palette.GOLD_SOFT + "> "
            + "<" + Palette.DIM + ">·</" + Palette.DIM + "> "
            + "<" + Palette.MUTED + ">Coins</" + Palette.MUTED + ">"
            + (podiumBonus > 0
                ? " <" + Palette.DIM + ">·</" + Palette.DIM + "> "
                  + "<" + Palette.GOLD + ">+" + podiumBonus + " podium</" + Palette.GOLD + ">"
                : ""));

        return lines;
    }

    /**
     * The line that says where the player finished, when they finished in the top three.
     *
     * <p>Only the podium earns a line — a fourth place is not a moment.
     */
    public String podiumLine() {
        if (place <= 0 || place > 3) {
            return "";
        }
        String medal = switch (place) {
            case 1 -> "<" + Palette.GOLD_SOFT + ">#1</" + Palette.GOLD_SOFT + ">";
            case 2 -> "<" + Palette.MUTED + ">#2</" + Palette.MUTED + ">";
            default -> "<" + Palette.AMBER + ">#3</" + Palette.AMBER + ">";
        };
        return medal + " <" + Palette.TEXT + ">Season podium</" + Palette.TEXT + ">";
    }

    /**
     * The whole card, header included — what a player screenshots.
     */
    public List<String> card() {
        List<String> lines = new ArrayList<>();
        String podium = podiumLine();
        if (!podium.isEmpty()) {
            lines.add(podium);
        }
        lines.add(titleLine());
        lines.addAll(body());
        return lines;
    }

    /**
     * Whether the card carries everything a screenshot needs.
     *
     * <p>Used by the test that guards the card: a summary with no games played, or a summary whose
     * reward is missing, is a card that shows the player nothing.
     */
    public boolean isScreenshotWorthy() {
        if (matches <= 0) {
            return false;
        }
        if (bestElo <= 0) {
            return false;
        }
        return totalCoins() > 0;
    }
}
