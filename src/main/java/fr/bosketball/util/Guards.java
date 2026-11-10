package fr.bosketball.util;

/**
 * Defensive helpers shared by every subsystem hardened in the "to the bone" pass.
 *
 * <p>Every method here exists because a real failure was found in this codebase or in a plugin of
 * the same shape, and every one of them is pure: no Bukkit, no state, unit-testable.
 *
 * <h2>Why a whole class</h2>
 *
 * <p>Java hides three families of crashes that a Minecraft plugin meets constantly:
 *
 * <ul>
 *   <li><b>{@code Math.clamp}</b> throws {@link IllegalArgumentException} when the minimum is above
 *       the maximum. A geometry read from a schematic, a config edited by hand, or a world whose
 *       border was shrunk all produce that case — and the throw happens <em>every tick</em>, inside
 *       a scheduler task, which is how a plugin fills its log in a second.</li>
 *   <li><b>NaN / infinity</b> propagate silently. {@code NaN} compares false against everything, so
 *       a single bad velocity turns every downstream branch into the wrong one, permanently, until
 *       the entity is removed. Physics, animation and camera code must sanitise at the boundary.</li>
 *   <li><b>Arithmetic overflow</b> on {@code int} and {@code long} wraps around instead of throwing,
 *       so a corrupt or hostile value becomes a negative number that then indexes an array.</li>
 * </ul>
 */
public final class Guards {

    private Guards() {
    }

    /** {@code true} only for a real, finite number: rejects NaN, ±infinity and {@code null}. */
    public static boolean isFinite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    /** {@code value} when it is finite, {@code fallback} otherwise (also when it is NaN). */
    public static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    /** {@code value} when it is finite, {@code fallback} otherwise. */
    public static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    /**
     * Clamp that never throws when the bounds are crossed.
     *
     * <p>{@link Math#clamp(double, double, double)} throws
     * {@link IllegalArgumentException} as soon as {@code min > max}. That happens with real data:
     * a 1-block-wide arena gives {@code barrierMinX + 0.9 > barrierMaxX - 0.9}, a hand-edited config
     * gives {@code max < min}, and a schematic with an inverted selection gives both. Returning the
     * midpoint keeps the caller alive and visibly wrong instead of dead and silent.
     */
    public static double clampLoHi(double value, double min, double max) {
        double safeValue = finite(value, (min + max) * 0.5);
        if (min > max) {
            return (min + max) * 0.5;
        }
        return Math.max(min, Math.min(max, safeValue));
    }

    /** Integer flavour of {@link #clampLoHi(double, double, double)}. */
    public static int clampLoHi(int value, int min, int max) {
        if (min > max) {
            return (int) (((long) min + (long) max) / 2L);
        }
        return Math.max(min, Math.min(max, value));
    }

    /** Long flavour of {@link #clampLoHi(double, double, double)}. */
    public static long clampLoHi(long value, long min, long max) {
        if (min > max) {
            return min / 2L + max / 2L;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * {@code value} brought into {@code [0, 1]}, NaN mapped to {@code fallback}.
     *
     * <p>Used by every easing / progress helper: a single NaN progress used to make a whole rig
     * blink out of existence because the rotation quaternion became invalid.
     */
    public static double unit(double value, double fallback) {
        double safe = finite(value, fallback);
        return safe < 0.0 ? 0.0 : (safe > 1.0 ? 1.0 : safe);
    }

    /** Float flavour of {@link #unit(double, double)}. */
    public static float unit(float value, float fallback) {
        float safe = finite(value, fallback);
        return safe < 0.0f ? 0.0f : (safe > 1.0f ? 1.0f : safe);
    }

    /**
     * {@code base + addend * scale} without overflowing {@code long}.
     *
     * <p>Season windows are computed as {@code start + (index - 1) * duration}; with a hostile or
     * corrupt index that multiplication wraps into a date in 1793 and every display downstream
     * shows nonsense. Saturating is better than wrapping.
     */
    public static long saturatingMultiplyAdd(long base, long addend, long scale) {
        if (addend == 0L || scale == 0L) {
            return base;
        }
        long product;
        try {
            product = Math.multiplyExact(addend, scale);
        } catch (ArithmeticException overflow) {
            return (addend > 0) == (scale > 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        try {
            return Math.addExact(base, product);
        } catch (ArithmeticException overflow) {
            return product > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    /**
     * Truncates to {@code maxChars} without splitting a surrogate pair.
     *
     * <p>Names, tags and chat lines are user input: cutting between the two halves of an emoji
     * produces an unpaired surrogate, which MiniMessage serialises as {@code ?} and some clients
     * render as a box. Rare, ugly, trivially avoidable.
     */
    public static String truncate(String value, int maxChars) {
        if (value == null || maxChars <= 0) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        int cut = maxChars;
        if (cut > 0 && Character.isHighSurrogate(value.charAt(cut - 1))) {
            cut--;
        }
        return value.substring(0, cut);
    }
}
