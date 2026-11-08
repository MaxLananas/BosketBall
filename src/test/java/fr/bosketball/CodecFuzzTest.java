package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerQuest;
import fr.bosketball.model.ProgressStat;
import fr.bosketball.model.QuestCodec;
import fr.bosketball.model.TeamSide;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class CodecFuzzTest {

    private final Random rng = new Random(20261104L);

    private static final String[] NASTY_PAYLOADS = {
        "",
        " ",
        "|",
        "||||||||||||||",
        ";;;;;;;;;;;;;;;;;;;;",
        "::::::::::::::::::::",
        "----",
        "99999999999999999999999999999999999999",
        "-99999999999999999999999999999999999999",
        "a|b|c|NOT_A_STAT|1|2|3|4|5|6|7|8",
        "a|b|c|POINTS|x|y|z|4|5|6|7|8",
        "a|b|c|POINTS|1|2|3|4|5|6|7",
        "\u0000\u0001\u0002|\u0003|\u0004|POINTS|1|2|3|4|0|0|0",
        "éàü|🎯|🏀|POINTS|1|2|3|icon|0|0|0|0",
        "a|b|c|POINTS|2147483648|2|3|4|0|0|0|0",
        "a|b|c|POINTS|-2147483649|2|3|4|0|0|0|0",
        "a|b|c|POINTS|1|2|3|4|0|9223372036854775807|0",
    };

    private static PlayerQuest sampleQuest() {
        return new PlayerQuest(
            "daily-scoring",
            "Score 20 points",
            "Drop 20 buckets in any mode",
            ProgressStat.values()[0],
            20,
            250,
            120,
            "icon_ball_classic",
            1_700_000_000_000L,
            1_700_086_400_000L
        );
    }

    private static MatchHistoryEntry sampleMatch() {
        return new MatchHistoryEntry(
            1_700_000_000_000L,
            "2V2",
            "Bosket Arena",
            TeamSide.IGNIS,
            21,
            17,
            true,
            14,
            5,
            3,
            2,
            1,
            18,
            "MaxLananas"
        );
    }

    private String randomGarbage() {
        int length = rng.nextInt(48);
        StringBuilder sb = new StringBuilder(length);
        String alphabet = "|;:abcXYZ0123456789-_ é🎯\u0000";
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    @Test
    public void questDecodeNeverThrowsOnGarbage() {
        for (String payload : NASTY_PAYLOADS) {
            QuestCodec.decode(payload);
            QuestCodec.decodeList(List.of(payload));
        }
        for (int i = 0; i < 2_000; i++) {
            String payload = randomGarbage();
            PlayerQuest decoded = QuestCodec.decode(payload);
            if (decoded != null) {
                assertNotNull(decoded.stat());
                assertNotNull(decoded.id());
            }
        }
        assertNull(QuestCodec.decode(null));
        assertNull(QuestCodec.decode("   "));
        assertTrue(QuestCodec.decodeList(null).isEmpty());
        assertTrue(QuestCodec.decodeList(List.of()).isEmpty());
    }

    @Test
    public void questDecodeSurvivesTruncationAndMutation() {
        String encoded = QuestCodec.encode(sampleQuest());
        for (int cut = 0; cut <= encoded.length(); cut++) {
            QuestCodec.decode(encoded.substring(0, cut));
        }
        char[] chars = encoded.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char original = chars[i];
            chars[i] = original == '|' ? ';' : '|';
            QuestCodec.decode(new String(chars));
            chars[i] = original;
        }
    }

    @Test
    public void questRoundTripIsStable() {
        PlayerQuest original = sampleQuest();
        PlayerQuest decoded = QuestCodec.decode(QuestCodec.encode(original));
        assertNotNull(decoded);
        assertEquals(original.id(), decoded.id());
        assertEquals(original.stat(), decoded.stat());
        assertEquals(original.target(), decoded.target());
        assertEquals(original.rewardCoins(), decoded.rewardCoins());
        assertEquals(original.expiresAtMillis(), decoded.expiresAtMillis());

        List<String> rows = QuestCodec.encodeList(List.of(original, sampleQuest()));
        assertEquals(2, rows.size());
        assertEquals(2, QuestCodec.decodeList(rows).size());
    }

    @Test
    public void matchHistoryDecodeNeverThrowsOnGarbage() {
        for (String payload : NASTY_PAYLOADS) {
            MatchHistoryEntry.deserializeCompact(payload);
            MatchHistoryEntry.deserializeCompact(payload.replace('|', ';'));
        }
        for (int i = 0; i < 2_000; i++) {
            MatchHistoryEntry.deserializeCompact(randomGarbage());
        }
        assertNull(MatchHistoryEntry.deserializeCompact(null));
        assertNull(MatchHistoryEntry.deserializeCompact(""));
    }

    @Test
    public void matchHistoryDecodeSurvivesTruncationAndMutation() {
        String encoded = sampleMatch().serializeCompact();
        for (int cut = 0; cut <= encoded.length(); cut++) {
            MatchHistoryEntry.deserializeCompact(encoded.substring(0, cut));
        }
        char[] chars = encoded.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char original = chars[i];
            chars[i] = original == '|' ? ';' : '|';
            MatchHistoryEntry.deserializeCompact(new String(chars));
            chars[i] = original;
        }
    }

    @Test
    public void matchHistoryRoundTripIsStable() {
        MatchHistoryEntry original = sampleMatch();
        MatchHistoryEntry decoded = MatchHistoryEntry.deserializeCompact(original.serializeCompact());
        assertNotNull(decoded);
        assertEquals(original.timestampEpochMillis(), decoded.timestampEpochMillis());
        assertEquals(original.teamSide(), decoded.teamSide());
        assertEquals(original.ignisScore(), decoded.ignisScore());
        assertEquals(original.frostScore(), decoded.frostScore());
        assertEquals(original.points(), decoded.points());
        assertEquals(original.eloDelta(), decoded.eloDelta());
    }

    @Test
    public void matchHistoryYamlMapNeverThrowsOnCorruptValues() {
        List<Map<String, Object>> corrupt = new ArrayList<>();
        corrupt.add(null);
        corrupt.add(new HashMap<>());
        Map<String, Object> wrongTypes = new HashMap<>();
        wrongTypes.put("timestamp", "not-a-number");
        wrongTypes.put("score", "ab-cd");
        wrongTypes.put("points", List.of(1, 2));
        wrongTypes.put("team", 42);
        wrongTypes.put("result", new Object());
        corrupt.add(wrongTypes);
        Map<String, Object> insaneScore = new HashMap<>();
        insaneScore.put("score", "99999999999999999999-1");
        insaneScore.put("timestamp", Double.NaN);
        corrupt.add(insaneScore);
        Map<String, Object> nulls = new HashMap<>();
        nulls.put("timestamp", null);
        nulls.put("mode", null);
        nulls.put("arena", null);
        nulls.put("mvp", null);
        corrupt.add(nulls);

        for (Map<String, Object> map : corrupt) {
            MatchHistoryEntry.fromYamlMap(map);
        }

        Map<String, Object> good = sampleMatch().toYamlMap();
        MatchHistoryEntry restored = MatchHistoryEntry.fromYamlMap(good);
        assertNotNull(restored);
        assertEquals(sampleMatch().points(), restored.points());
        assertEquals(sampleMatch().teamSide(), restored.teamSide());
    }
}
