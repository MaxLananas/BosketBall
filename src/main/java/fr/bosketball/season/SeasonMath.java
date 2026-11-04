package fr.bosketball.season;

import fr.bosketball.model.Rank;

public final class SeasonMath {

    public static final int MIN_ELO = 100;
    public static final int MAX_ELO = 99_999;

    private static final String[] ROMAN = {
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
        "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"
    };

    private SeasonMath() {
    }

    public static int softReset(int elo, int anchor, double keepRatio) {
        int safeAnchor = (int) Math.clamp(anchor, MIN_ELO, MAX_ELO);
        double ratio = Math.clamp(keepRatio, 0.0, 1.0);
        long next = Math.round(safeAnchor + (elo - safeAnchor) * ratio);
        return (int) Math.clamp(next, (long) MIN_ELO, (long) MAX_ELO);
    }

    public static double placementSwingScale(int played, int required) {
        if (required <= 0) {
            return 1.0;
        }
        double t = Math.clamp((double) Math.max(0, played) / (double) required, 0.0, 1.0);
        return 2.0 - t;
    }

    public static int applyPlacementSwing(int rawDelta, int played, int required) {
        if (rawDelta == 0) {
            return 0;
        }
        int scaled = (int) Math.round(rawDelta * placementSwingScale(played, required));
        if (scaled == 0) {
            scaled = rawDelta > 0 ? 1 : -1;
        }
        return scaled;
    }

    public static Rank tierFor(int elo) {
        return Rank.fromElo(Math.clamp(elo, MIN_ELO, MAX_ELO));
    }

    public static int competitiveTierIndex(Rank rank) {
        Rank safe = rank == null ? Rank.ROOKIE : rank;
        return switch (safe) {
            case ROOKIE -> 0;
            case HOOPER -> 1;
            case PRO -> 2;
            case ALL_STAR -> 3;
            case SUPERSTAR -> 4;
            case LEGEND -> 5;
            case MVP -> 6;
            default -> 0;
        };
    }

    public static int progressPercent(long startMillis, long endMillis, long nowMillis) {
        long span = endMillis - startMillis;
        if (span <= 0) {
            return 100;
        }
        long elapsed = Math.clamp(nowMillis - startMillis, 0L, span);
        return (int) Math.clamp(Math.round(elapsed * 100.0 / span), 0L, 100L);
    }

    public static String remainingLabel(long remainingMillis) {
        long millis = Math.max(0L, remainingMillis);
        long totalMinutes = millis / 60_000L;
        long days = totalMinutes / (60L * 24L);
        long hours = (totalMinutes / 60L) % 24L;
        long minutes = totalMinutes % 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
    }

    public static String roman(int index) {
        int safe = Math.max(1, index);
        if (safe <= ROMAN.length) {
            return ROMAN[safe - 1];
        }
        return String.valueOf(safe);
    }

    public static String seasonName(int index) {
        return "SEASON " + roman(index);
    }

    public static int eloToNextTier(int elo) {
        Rank next = tierFor(elo).nextCompetitiveRank();
        if (next == null) {
            return 0;
        }
        return Math.max(0, next.minElo() - elo);
    }

    public static int tierFloor(Rank rank) {
        return rank == null ? 0 : rank.minElo();
    }

    public static int tierCeiling(Rank rank) {
        Rank next = rank == null ? null : rank.nextCompetitiveRank();
        return next == null ? MAX_ELO : Math.max(rank.minElo() + 1, next.minElo() - 1);
    }

    public static int clampElo(int elo) {
        return Math.clamp(elo, MIN_ELO, MAX_ELO);
    }

    public static long daysToMillis(int days) {
        return Math.max(1, days) * 24L * 60L * 60L * 1000L;
    }
}
