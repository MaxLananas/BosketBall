package fr.bosketball.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact string codec for {@link PlayerQuest} rows inside the profile YAML.
 *
 * <p>Format (fields are separated by {@code §}):</p>
 * <pre>id§title§description§stat§target§rewardCoins§rewardXp§icon§progress§claimed§assignedAt§expiresAt</pre>
 *
 * <p>Chosen over a nested YAML map so a profile stays readable and a quest board is a single
 * flat list of strings — cheap to write on every auto-save tick.</p>
 */
public final class QuestCodec {

    private static final String SEP = "§";

    private QuestCodec() {
    }

    public static String encode(PlayerQuest q) {
        if (q == null) {
            return "";
        }
        return String.join(SEP,
            safe(q.id()),
            safe(q.title()),
            safe(q.description()),
            q.stat().name(),
            String.valueOf(q.target()),
            String.valueOf(q.rewardCoins()),
            String.valueOf(q.rewardXp()),
            safe(q.iconModel()),
            String.valueOf(q.progress()),
            q.claimed() ? "1" : "0",
            String.valueOf(q.assignedAtMillis()),
            String.valueOf(q.expiresAtMillis())
        );
    }

    public static PlayerQuest decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(SEP, -1);
        if (parts.length < 12) {
            return null;
        }
        try {
            ProgressStat stat = ProgressStat.valueOf(parts[3]);
            PlayerQuest quest = new PlayerQuest(
                parts[0],
                parts[1],
                parts[2],
                stat,
                Integer.parseInt(parts[4]),
                Integer.parseInt(parts[5]),
                Integer.parseInt(parts[6]),
                parts[7],
                Long.parseLong(parts[10]),
                Long.parseLong(parts[11])
            );
            quest.setProgress(Integer.parseInt(parts[8]));
            // Slot 9 is the single durable "paid out" bit. It used to also be fed into a separate
            // `rewarded` flag that was never encoded, so a quest could look claimed-but-unpaid after
            // a restart (paying twice) or paid-but-unclaimed. One flag, one meaning: claiming IS
            // rewarding, and both are derived from the same persisted value.
            boolean paid = "1".equals(parts[9]);
            quest.setClaimed(paid);
            quest.setRewarded(paid);
            return quest;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static List<String> encodeList(List<PlayerQuest> quests) {
        List<String> out = new ArrayList<>();
        if (quests != null) {
            for (PlayerQuest q : quests) {
                String encoded = encode(q);
                if (!encoded.isBlank()) {
                    out.add(encoded);
                }
            }
        }
        return out;
    }

    public static List<PlayerQuest> decodeList(List<String> rows) {
        List<PlayerQuest> out = new ArrayList<>();
        if (rows != null) {
            for (String row : rows) {
                PlayerQuest q = decode(row);
                if (q != null) {
                    out.add(q);
                }
            }
        }
        return out;
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace(SEP, " ");
    }
}
