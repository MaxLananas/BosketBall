package fr.bosketball.stats;

import fr.bosketball.model.Rank;

/**
 * How far a player is from the next rank, in games.
 *
 * <h2>Why this exists</h2>
 * The server celebrates a rank-up with a title, a particle burst and a server-wide announcement.
 * It never says the thing a player actually wants to know: <em>how close am I?</em> A player at
 * 1035 ELO has no way to tell whether the next rank is one game away or fifty.
 *
 * <p>The research on progression signalling is consistent on this point: a visible next milestone
 * is what turns a grind into a sequence. The ladder on top servers does not exist to be read —
 * it exists so the player can see the gap and decide whether to queue again tonight. A player
 * who knows they are two games from a promotion plays two more games. A player who does not know
 * goes to bed.
 *
 * <h2>How the number is computed</h2>
 * The ladder moves by a flat {@code +15} for a win and {@code -5} for a loss, so the fastest way
 * up is a win. The distance to the next rank is therefore
 * {@code ceil((nextRankMinElo - currentElo) / eloPerWin)} wins — and the honest number to show a
 * player is not "you need N wins" but <b>"N games"</b>, because a game can be won or lost, and
 * the player is the one deciding which.
 *
 * <p>The one thing that must never happen is a promise. The number says "at best, N games", and
 * the copy says "at best" out loud, because a player who loses five in a row has not been
 * cheated — they have been told the truth.
 */
public final class RankProgress {

    /** How many wins a player needs to climb one tier, when a win is worth this much. */
    public static final int ELO_PER_WIN = 15;

    /** The maximum number of games the signal will claim — past this, the gap is a project. */
    public static final int MAX_GAMES_SHOWN = 10;

    private RankProgress() {
    }

    /**
     * The number of games a player needs at best to reach the next rank.
     *
     * @param elo the player's current rating
     * @return the number of wins needed, or {@code 0} when the player is already at the top
     */
    public static int gamesToNextRank(int elo) {
        Rank current = Rank.fromElo(elo);
        Rank next = current.nextCompetitiveRank();
        if (next == null) {
            return 0;
        }
        int gap = next.minElo() - elo;
        if (gap <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) gap / (double) ELO_PER_WIN);
    }

    /**
     * Whether the next rank is close enough to be worth telling the player about.
     *
     * <p>Three games is the threshold: at that range the player can realistically get there
     * tonight, which is the whole point of the signal. Beyond ten games the gap stops being a
     * goal and starts being a fact of life, and the number stops motivating.
     */
    public static boolean isCloseEnough(int elo) {
        int games = gamesToNextRank(elo);
        return games > 0 && games <= MAX_GAMES_SHOWN;
    }

    /**
     * The ELO the player needs to reach the next rank.
     *
     * @return the target rating, or the player's own rating at the top rank
     */
    public static int nextRankTarget(int elo) {
        Rank current = Rank.fromElo(elo);
        Rank next = current.nextCompetitiveRank();
        return next == null ? elo : next.minElo();
    }

    /**
     * The number of ELO points still needed.
     */
    public static int eloGap(int elo) {
        int target = nextRankTarget(elo);
        return Math.max(0, target - elo);
    }

    /**
     * The progress towards the next rank, as a fraction.
     *
     * <p>Measured from the bottom of the current rank, so it is 0.0 on the first point of a tier
     * and 1.0 on the last — the same way a progress bar reads to a player.
     */
    public static double progressRatio(int elo) {
        Rank current = Rank.fromElo(elo);
        Rank next = current.nextCompetitiveRank();
        if (next == null) {
            return 1.0;
        }
        int floor = current.minElo();
        int ceiling = next.minElo();
        if (ceiling <= floor) {
            return 1.0;
        }
        double ratio = (double) (elo - floor) / (double) (ceiling - floor);
        return Math.max(0.0, Math.min(1.0, ratio));
    }

    /**
     * The signal line, or an empty string when the player is not close.
     *
     * <p>The copy is the server's voice: short, active, no system talking. Not "You are 2 wins
     * away from promotion" but "2 more games to All-Star". And it says <em>at best</em>, because
     * a game can be lost and the server does not make promises it cannot keep.
     *
     * @param elo the player's current rating
     * @return the line, or {@code ""} when there is nothing worth saying
     */
    public static String signal(int elo) {
        if (!isCloseEnough(elo)) {
            return "";
        }
        Rank current = Rank.fromElo(elo);
        Rank next = current.nextCompetitiveRank();
        if (next == null) {
            return "";
        }
        int games = gamesToNextRank(elo);
        String label = games == 1 ? "game" : "games";
        return "<" + fr.bosketball.util.Palette.GOLD_SOFT + "><bold>" + games + "</bold></"
            + fr.bosketball.util.Palette.GOLD_SOFT + "> more " + label + " to "
            + "<" + fr.bosketball.util.Palette.ICE + ">" + next.plainDisplayName()
            + "</" + fr.bosketball.util.Palette.ICE + "> "
            + "<" + fr.bosketball.util.Palette.MUTED + ">(at best)</" + fr.bosketball.util.Palette.MUTED + ">";
    }

    /**
     * A compact form for a HUD line — the target rating and the gap, no sentence.
     */
    public static String compact(int elo) {
        Rank current = Rank.fromElo(elo);
        Rank next = current.nextCompetitiveRank();
        if (next == null) {
            return "";
        }
        int games = gamesToNextRank(elo);
        if (games <= 0 || games > MAX_GAMES_SHOWN) {
            return "";
        }
        return "<" + fr.bosketball.util.Palette.MUTED + ">" + games + " to "
            + next.plainDisplayName() + "</" + fr.bosketball.util.Palette.MUTED + ">";
    }
}
