package fr.bosketball.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Immutable Career Match History Record (stored up to 15 recent games per player!).
 */
public record MatchHistoryEntry(
    long timestampEpochMillis,
    String modeName,
    String arenaName,
    TeamSide teamSide,
    int ignisScore,
    int frostScore,
    boolean won,
    int points,
    int assists,
    int rebounds,
    int steals,
    int blocks,
    int eloDelta,
    String mvpName
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
        .withZone(ZoneId.systemDefault());

    public MatchHistoryEntry(
        long timestampEpochMillis,
        String modeName,
        String sideName,
        int ignisScore,
        int frostScore,
        boolean won,
        int points,
        int assists,
        int rebounds,
        int eloDelta
    ) {
        this(
            timestampEpochMillis,
            modeName,
            "Bosket Arena",
            parseSideSafe(sideName),
            ignisScore,
            frostScore,
            won,
            points,
            assists,
            rebounds,
            0,
            0,
            eloDelta,
            ""
        );
    }

    private static TeamSide parseSideSafe(String raw) {
        try {
            return raw != null ? TeamSide.valueOf(raw) : TeamSide.IGNIS;
        } catch (IllegalArgumentException e) {
            return TeamSide.IGNIS;
        }
    }

    public String modeKey() {
        return modeName;
    }

    public String formattedDate() {
        return FMT.format(Instant.ofEpochMilli(timestampEpochMillis));
    }

    public String serializeCompact() {
        return timestampEpochMillis + "|"
            + sanitize(modeName) + "|"
            + sanitize(arenaName) + "|"
            + teamSide.name() + "|"
            + ignisScore + "|"
            + frostScore + "|"
            + (won ? "1" : "0") + "|"
            + points + "|"
            + assists + "|"
            + rebounds + "|"
            + steals + "|"
            + blocks + "|"
            + eloDelta + "|"
            + sanitize(mvpName);
    }

    public static MatchHistoryEntry deserializeCompact(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 14) {
            return null;
        }
        try {
            return new MatchHistoryEntry(
                Long.parseLong(parts[0]),
                parts[1],
                parts[2],
                TeamSide.valueOf(parts[3]),
                Integer.parseInt(parts[4]),
                Integer.parseInt(parts[5]),
                "1".equals(parts[6]),
                Integer.parseInt(parts[7]),
                Integer.parseInt(parts[8]),
                Integer.parseInt(parts[9]),
                Integer.parseInt(parts[10]),
                Integer.parseInt(parts[11]),
                Integer.parseInt(parts[12]),
                parts[13]
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replace("|", "/");
    }
}
