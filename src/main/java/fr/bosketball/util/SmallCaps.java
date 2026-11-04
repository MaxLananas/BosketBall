package fr.bosketball.util;

import java.util.HashMap;
import java.util.Map;

public final class SmallCaps {

    private static final Map<Character, String> CHAR_MAP = new HashMap<>();

    static {
        char[] ascii = "abcdefghijklmnopqrstuvwxyz".toCharArray();
        String[] small = {
            "ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ",
            "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "x", "ʏ", "ᴢ"
        };
        for (int i = 0; i < ascii.length; i++) {
            CHAR_MAP.put(ascii[i], small[i]);
            CHAR_MAP.put(Character.toUpperCase(ascii[i]), small[i]);
        }

        putAll("àâäáãåÀÂÄÁÃÅ", "ᴀ");
        putAll("éèêëÉÈÊË", "ᴇ");
        putAll("îïíìÎÏÍÌ", "ɪ");
        putAll("ôöóòõÔÖÓÒÕ", "ᴏ");
        putAll("ùûüúÙÛÜÚ", "ᴜ");
        putAll("çÇ", "ᴄ");
        putAll("ñÑ", "ɴ");
        putAll("ÿýŸÝ", "ʏ");
        CHAR_MAP.put('œ', "ᴏᴇ");
        CHAR_MAP.put('Œ', "ᴏᴇ");
        CHAR_MAP.put('æ', "ᴀᴇ");
        CHAR_MAP.put('Æ', "ᴀᴇ");
    }

    private static void putAll(String chars, String target) {
        for (int i = 0; i < chars.length(); i++) {
            CHAR_MAP.put(chars.charAt(i), target);
        }
    }

    private SmallCaps() {
    }

    public static String toSmallCaps(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        boolean insideTag = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '<') {
                insideTag = true;
                out.append(c);
                continue;
            }
            if (insideTag) {
                out.append(c);
                if (c == '>') {
                    insideTag = false;
                }
                continue;
            }

            if (c == '§' && i + 1 < input.length()) {
                out.append(c).append(input.charAt(++i));
                continue;
            }
            String mapped = CHAR_MAP.get(c);
            if (mapped != null) {
                out.append(mapped);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
