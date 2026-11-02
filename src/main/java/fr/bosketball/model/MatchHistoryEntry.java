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

    private static final DateTimeFormatter ISO_READABLE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    public java.util.Map<String, Object> toYamlMap() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("date", ISO_READABLE.format(Instant.ofEpochMilli(timestampEpochMillis)));
        m.put("timestamp", timestampEpochMillis);
        m.put("mode", modeName == null ? "2V2" : modeName);
        m.put("arena", arenaName == null ? "Bosket Arena" : arenaName);
        m.put("team", teamSide == null ? TeamSide.IGNIS.name() : teamSide.name());
        m.put("result", won ? "WIN" : "LOSS");
        m.put("score", ignisScore + "-" + frostScore);
        m.put("points", points);
        m.put("assists", assists);
        m.put("rebounds", rebounds);
        m.put("steals", steals);
        m.put("blocks", blocks);
        m.put("elo-change", eloDelta);
        if (mvpName != null && !mvpName.isBlank()) {
            m.put("mvp", mvpName);
        }
        return m;
    }

    public static MatchHistoryEntry fromYamlMap(java.util.Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            long ts = asLong(map.get("timestamp"), System.currentTimeMillis());
            String mode = asStr(map.get("mode"), "2V2");
            String arena = asStr(map.get("arena"), "Bosket Arena");
            TeamSide side = parseSideSafe(asStr(map.get("team"), "IGNIS"));
            boolean won = "WIN".equalsIgnoreCase(asStr(map.get("result"), "LOSS"))
                || Boolean.TRUE.equals(map.get("won"));
            int ignis = 0;
            int frost = 0;
            String scoreStr = asStr(map.get("score"), "");
            int dash = scoreStr.indexOf('-');
            if (dash > 0) {
                ignis = Integer.parseInt(scoreStr.substring(0, dash).trim());
                frost = Integer.parseInt(scoreStr.substring(dash + 1).trim());
            } else {
                ignis = asInt(map.get("ignis-score"), 0);
                frost = asInt(map.get("frost-score"), 0);
            }
            return new MatchHistoryEntry(
                ts,
                mode,
                arena,
                side,
                ignis,
                frost,
                won,
                asInt(map.get("points"), 0),
                asInt(map.get("assists"), 0),
                asInt(map.get("rebounds"), 0),
                asInt(map.get("steals"), 0),
                asInt(map.get("blocks"), 0),
                asInt(map.get("elo-change"), 0),
                asStr(map.get("mvp"), "")
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String asStr(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long asLong(Object o, long def) {
        if (o instanceof Number n) return n.longValue();
        if (o == null) return def;
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replace("|", "/");
    }
}
