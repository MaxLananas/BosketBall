package fr.bosketball.season;

import fr.bosketball.model.Rank;

public final class SeasonMath {

    private static final java.time.format.DateTimeFormatter[] FORMATS = {
        java.time.format.DateTimeFormatter.ISO_INSTANT,
        java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
    };

    /**
     * Parses a real calendar date from the config ({@code 2026-11-01}, {@code 2026-11-01T18:00},
     * {@code 2026-11-01T18:00:00Z}…). Returns {@code -1} when the value is blank or unreadable,
     * so the caller can fall back on a persisted/default window.
     */
    public static long parseDateMillis(String raw) {
        if (raw == null) {
            return -1L;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return -1L;
        }
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.Instant instant = parseInstant(value, zone);
        return instant == null ? -1L : instant.toEpochMilli();
    }

    static java.time.Instant parseInstant(String value, java.time.ZoneId zone) {
        for (java.time.format.DateTimeFormatter format : FORMATS) {
            try {
                if (format == java.time.format.DateTimeFormatter.ISO_INSTANT) {
                    return java.time.Instant.parse(value);
                }
                if (format == java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return java.time.OffsetDateTime.parse(value, format).toInstant();
                }
                if (format == java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME) {
                    return java.time.LocalDateTime.parse(value, format).atZone(zone).toInstant();
                }
                if (format == java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) {
                    return java.time.LocalDate.parse(value, format).atStartOfDay(zone).toInstant();
                }
                return java.time.LocalDateTime.parse(value, format).atZone(zone).toInstant();
            } catch (RuntimeException ignored) {
                // try the next format
            }
        }
        return null;
    }

    /**
     * Calendar window of a season. When the config gives a real start date, every season is
     * derived from it ({@code start + (index-1) * duration}) so a restart can never move it.
     */
    public static long[] seasonWindow(int index, long configuredStart, long configuredEnd,
                                      long durationMillis) {
        long duration = Math.max(60_000L, durationMillis);
        int seasonIndex = Math.max(1, index);
        long start;
        long end;
        if (configuredStart > 0) {
            start = configuredStart + (seasonIndex - 1L) * duration;
            end = start + duration;
            if (seasonIndex == 1 && configuredEnd > start) {
                end = configuredEnd;
            }
        } else {
            end = -1L;
            start = -1L;
        }
        return new long[]{start, end};
    }

    public static String formattedWindow(long startMillis, long endMillis) {
        java.time.format.DateTimeFormatter f =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        return java.time.Instant.ofEpochMilli(startMillis).atZone(zone).format(f)
            + " → " + java.time.Instant.ofEpochMilli(endMillis).atZone(zone).format(f);
    }

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

    /**
     * Placement matches no longer amplify the ELO swing.
     *
     * <p>Round 27: the old formula returned {@code 2.0 - t}, so the first ranked games gave
     * +30 / -10 (and with the pre-Round-26 formula, +32 / -16) instead of the flat +15 / -5 that
     * the server advertises. Placements now only gate the season tier; the rating moves by the
     * same amount as in any other ranked game.
     */
    public static double placementSwingScale(int played, int required) {
        return 1.0;
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

    /** True when the delta the player actually got matches the advertised flat rule. */
    public static boolean isFlatRankedDelta(int appliedDelta, int winGain, int lossPenalty) {
        return appliedDelta == winGain || appliedDelta == -lossPenalty || appliedDelta == 0;
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
