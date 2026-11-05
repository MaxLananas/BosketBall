package fr.bosketball.util;

import fr.bosketball.model.ShotReleaseQuality;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class Glyphs {

    public static final char BANNER_SCORER = '\uE100';
    public static final char BANNER_THREE  = '\uE101';
    public static final char BANNER_DUNK   = '\uE102';
    public static final char BANNER_ASSIST = '\uE103';
    public static final char SCOREBUG_BG   = '\uE104';
    public static final char LOGO_HEADER   = '\uE105';

    public static final int BANNER_MAIN_WIDTH   = 196;
    public static final int BANNER_ASSIST_WIDTH = 136;
    public static final int SCOREBUG_WIDTH      = 228;

    public static final char ICON_TROPHY = '\uE200';
    public static final char ICON_STAR   = '\uE201';
    public static final char ICON_BALL   = '\uE202';
    public static final char ICON_FIRE   = '\uE203';
    public static final char ICON_CROWN  = '\uE204';
    public static final char ICON_SHIELD = '\uE205';
    public static final char ICON_BOLT   = '\uE206';

    public static final char RANK_ROOKIE    = '\uE210';
    public static final char RANK_HOOPER    = '\uE211';
    public static final char RANK_PRO       = '\uE212';
    public static final char RANK_ALLSTAR   = '\uE213';
    public static final char RANK_SUPERSTAR = '\uE214';
    public static final char RANK_LEGEND    = '\uE215';
    public static final char RANK_MVP       = '\uE216';
    public static final char RANK_REFEREE   = '\uE217';
    public static final char RANK_ADMIN     = '\uE218';
    public static final char TEAM_IGNIS     = '\uE219';
    public static final char TEAM_FROST     = '\uE21A';

    public static final char METER_CAP_L      = '\uE300';
    public static final char METER_EMPTY      = '\uE301';
    public static final char METER_FILL_WARM  = '\uE302';
    public static final char METER_GREEN_ZONE = '\uE303';
    public static final char METER_GREEN_HIT  = '\uE304';
    public static final char METER_LATE_RED   = '\uE305';
    public static final char METER_CAP_R      = '\uE306';

    public static final char RETICLE_GREEN = '\uE310';
    public static final char RETICLE_CYAN  = '\uE311';
    public static final char RETICLE_AMBER = '\uE312';
    public static final char RETICLE_RED   = '\uE313';

    public static final char DIVIDER_SEG   = '\uE110';
    public static final char DIVIDER_CAP_L = '\uE111';
    public static final char DIVIDER_CAP_R = '\uE112';

    /**
     * Glossy navigation dock pills used by /help and the welcome card. They replace the old
     * bracketed emoji buttons with the same beveled artwork as the chat tags.
     */
    public static final char NAV_PLAY    = '\uE2B0';
    public static final char NAV_LOCKER  = '\uE2B1';
    public static final char NAV_SHOP    = '\uE2B2';
    public static final char NAV_RANKS   = '\uE2B3';
    public static final char NAV_HELP    = '\uE2B4';
    public static final char NAV_DISCORD = '\uE2B5';

    public static final char[] CHAT_LOGO_STRIPS = {'\uE120', '\uE121', '\uE122', '\uE123', '\uE124'};
    public static final char[] CHAT_LOGO_RIGHT_STRIPS = {'\uE125', '\uE126', '\uE127', '\uE128', '\uE129'};

    public static String logoStripPair(int row) {
        int clamped = Math.max(0, Math.min(CHAT_LOGO_STRIPS.length - 1, row));
        return String.valueOf(CHAT_LOGO_STRIPS[clamped]) + '\uE001' + CHAT_LOGO_RIGHT_STRIPS[clamped];
    }

    public static Component logoStripComponent(int row) {
        return Component.text(logoStripPair(row), BITMAP_STYLE);
    }

    public static final char MENU3D_SELECT_BAR = '\uE130';
    public static final char MENU3D_PATCH_PILL = '\uE131';
    public static final int MENU3D_BAR_WIDTH   = 128;

    public static final char TAB_HEADER_CARD = '\uE140';
    public static final char TAB_ROW_IGNIS   = '\uE141';
    public static final char TAB_ROW_FROST   = '\uE142';
    public static final char TAB_ROW_GOLD    = '\uE143';
    public static final char TAB_FOOTER_CARD = '\uE144';
    public static final int TAB_ROW_WIDTH    = 224;

    /**
     * Tab-list backdrop: the gold frame from the owner's `tablist.zip`, nine-sliced so the corner
     * ornaments stay 1:1 while the side rails stretch. One glyph per row bucket, sized so the frame
     * always wraps the header, the player rows and the footer.
     */
    public static final char[] TAB_FRAME = {'\uE370', '\uE371', '\uE372', '\uE373'};
    public static final int[] TAB_FRAME_MAX_ROWS = {4, 10, 15, 20};
    public static final int TAB_FRAME_WIDTH = 256;
    public static final int CHAT_DIVIDER_WIDTH_PX = 288;

    public static final char SB_FLOURISH = '\uE260';
    public static final char SB_PLAYER   = '\uE261';
    public static final char SB_ELO      = '\uE262';
    public static final char SB_POINTS   = '\uE263';
    public static final char SB_PLAYTIME = '\uE264';
    public static final char SB_FRIENDS  = '\uE265';
    public static final char SB_MATCH    = '\uE266';
    public static final char SB_CLOCK    = '\uE267';
    public static final char SB_STATS    = '\uE268';
    public static final char SB_BALL     = '\uE269';
    public static final char SB_STREAK   = '\uE26A';
    public static final char SB_COINS    = '\uE26B';

    public static final char CHAT_ICON_WEB       = '\uE270';
    public static final char CHAT_ICON_SHOP      = '\uE271';
    public static final char CHAT_ICON_DISCORD   = '\uE272';
    public static final char CHAT_ICON_MEGAPHONE = '\uE273';
    public static final char CHAT_ICON_MAIL      = '\uE274';
    public static final char CHAT_ICON_COIN      = '\uE275';

    public static final char SYS_SERVER = '\uE2A0';
    public static final char SYS_COURT  = '\uE2A1';
    public static final char SYS_SOCIAL = '\uE2A2';
    public static final char SYS_SHOP   = '\uE2A3';
    public static final char SYS_ALERT  = '\uE2A4';
    public static final char SYS_JOIN   = '\uE2A5';
    public static final char SYS_QUIT   = '\uE2A6';
    public static final char SYS_PM     = '\uE2A7';
    public static final char SYS_GUIDE  = '\uE2A8';

    public static final char BALL_PREVIEW_BASE = '\uE320';

    public static final char GUI_OVERLAY_SHOP         = '\uE350';
    public static final char GUI_OVERLAY_SHOP_GRID    = '\uE351';
    public static final char GUI_OVERLAY_TAGS         = '\uE352';
    public static final char GUI_OVERLAY_COSMETICS    = '\uE353';
    public static final char GUI_OVERLAY_PLAY         = '\uE354';
    public static final char GUI_OVERLAY_HUB          = '\uE355';
    public static final char GUI_OVERLAY_HUB_OP       = '\uE356';
    public static final char GUI_OVERLAY_LEADERBOARDS = '\uE357';
    public static final char GUI_OVERLAY_SETTINGS     = '\uE358';

    public static final char GUI_OVERLAY_CONFIRM      = '\uE359';

    public static final char GUI_OVERLAY_PLAY_BOT     = '\uE35A';
    public static final char GUI_OVERLAY_GUILD        = '\uE35C';
    public static final char GUI_OVERLAY_QUESTS       = '\uE35D';
    public static final char GUI_OVERLAY_ACHIEVEMENTS = '\uE340';
    public static final char GUI_OVERLAY_MAILBOX      = '\uE341';
    public static final char GUI_OVERLAY_HISTORY      = '\uE342';
    public static final char GUI_OVERLAY_FRIENDS      = '\uE343';
    public static final char GUI_OVERLAY_PARTY        = '\uE344';
    public static final char GUI_OVERLAY_RANKS        = '\uE345';
    public static final char GUI_OVERLAY_SPECTATE     = '\uE346';
    public static final char GUI_OVERLAY_PLAYER_CARD  = '\uE347';
    public static final char GUI_OVERLAY_ADMIN        = '\uE348';
    public static final char GUI_OVERLAY_REPORTS      = '\uE349';
    public static final char GUI_OVERLAY_WARNINGS     = '\uE34A';
    public static final char GUI_OVERLAY_STUDIO       = '\uE34B';
    public static final char GUI_OVERLAY_SEASON       = '\uE34C';


    public static char ballPreviewStrip(fr.bosketball.model.BallSkin skin, int row) {
        int skinIndex = switch (skin != null ? skin : fr.bosketball.model.BallSkin.CLASSIC) {
            case CLASSIC -> 0;
            case STREET  -> 1;
            case NEON    -> 2;
            case GOLD    -> 3;
            case SAKURA  -> 4;
            case MONEY   -> 5;
            case GALAXY  -> 6;
            case FIRE    -> 7;
        };
        int r = Math.max(0, Math.min(3, row));
        return (char) (BALL_PREVIEW_BASE + skinIndex * 4 + r);
    }

    private static final String SMALL_CAPS_ASCII = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String SMALL_CAPS_UNICODE =
        "\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

    public static String smallCaps(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetter(c) && c < 128) {
                char upper = Character.toUpperCase(c);
                int index = SMALL_CAPS_ASCII.indexOf(upper);
                out.append(index >= 0 ? SMALL_CAPS_UNICODE.charAt(index) : c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String smallCapsLabels(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '<') {
                int close = raw.indexOf('>', i);
                if (close < 0) {
                    out.append(raw, i, raw.length());
                    break;
                }
                out.append(raw, i, close + 1);
                i = close + 1;
                continue;
            }
            if (c >= 'A' && c <= 'Z') {
                int end = i;
                while (end < raw.length() && raw.charAt(end) >= 'A' && raw.charAt(end) <= 'Z') {
                    end++;
                }
                if (end - i >= 2) {
                    out.append(smallCaps(raw.substring(i, end)));
                } else {
                    out.append(raw, i, end);
                }
                i = end;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    public static final int GUI_PLAQUE_WIDTH = 176;

    public static final int GUI_PLAQUE_TEXT_MAX = 124;
    public static final int GUI_PLAQUE_BAND_PX = 140;

    public static final TextColor GUI_INK = TextColor.color(0xF2F6FF);

    public static Component customGuiOverlayTitle(char overlayGlyph) {
        return Component.text(shift(-8) + overlayGlyph + shift(-176), BITMAP_STYLE);
    }

    public static int guiOverlayHeight(char overlayGlyph) {
        return switch (overlayGlyph) {
            case GUI_OVERLAY_PLAYER_CARD -> 83;
            case GUI_OVERLAY_SETTINGS, GUI_OVERLAY_PLAY_BOT -> 119;
            default -> 137;
        };
    }

    public static final int GUI_PLAQUE_CAPTION_PX = 12;

    public static Component customGuiOverlayTitle(char overlayGlyph, String caption) {
        String label = smallCapsLabels(caption == null ? "" : caption.trim());
        if (label.isEmpty()) {
            return customGuiOverlayTitle(overlayGlyph);
        }

        int maxCaptionPx = GUI_PLAQUE_TEXT_MAX;
        int textPx = textAdvancePx(label, true);
        if (textPx > maxCaptionPx) {
            label = fitTextToWidth(label, maxCaptionPx, true);
            textPx = textAdvancePx(label, true);
        }

        int back = -((GUI_PLAQUE_WIDTH - 8) / 2 + textPx / 2);
        int tail = -(GUI_PLAQUE_WIDTH / 2 + 8 + textPx / 2);

        return Component.text(shift(-8) + overlayGlyph + shift(back), BITMAP_STYLE)
            .append(Component.text(label, Style.style()
                .color(GUI_INK)
                .decoration(TextDecoration.BOLD, true)
                .shadowColor(ShadowColor.none())
                .build()))
            .append(Component.text(shift(tail), BITMAP_STYLE));
    }

    public enum TopBadgeKind {
        WINS(0xE220),
        ELO(0xE223),
        MVP(0xE226),
        ASSISTS(0xE229),
        POINTS(0xE22C),
        DUNKS(0xE22F);

        private final int baseCodepoint;

        TopBadgeKind(int baseCodepoint) {
            this.baseCodepoint = baseCodepoint;
        }

        public char glyphForPlace(int place) {
            int clamped = Math.max(1, Math.min(3, place));
            return (char) (baseCodepoint + clamped - 1);
        }
    }

    public static char topBadgeChar(TopBadgeKind kind, int place) {
        return kind.glyphForPlace(place);
    }

    public static final Style BITMAP_STYLE = Style.style()
        .color(NamedTextColor.WHITE)
        .shadowColor(ShadowColor.none())
        .decoration(TextDecoration.ITALIC, false)
        .decoration(TextDecoration.BOLD, false)
        .build();

    private static final Map<Character, Integer> CHAR_ADVANCE = new HashMap<>();

    static {

        putWidth(" !.,:;i|¡'", 2);
        putWidth("`l", 3);
        putWidth("\"()*I[]t{}ɪ", 4);
        putWidth("<>fk", 5);

        putWidth("@~", 7);
        putWidth("ᴍᴡ", 6);
        CHAR_ADVANCE.put(SYS_SERVER, 42);
        CHAR_ADVANCE.put(SYS_COURT, 38);
        CHAR_ADVANCE.put(SYS_SOCIAL, 41);
        CHAR_ADVANCE.put(SYS_SHOP, 34);
        CHAR_ADVANCE.put(SYS_ALERT, 36);
        CHAR_ADVANCE.put(SYS_JOIN, 11);
        CHAR_ADVANCE.put(SYS_QUIT, 11);
        CHAR_ADVANCE.put(SYS_PM, 26);
        CHAR_ADVANCE.put(SYS_GUIDE, 36);
        CHAR_ADVANCE.put(RANK_ROOKIE, 44);
        CHAR_ADVANCE.put(RANK_HOOPER, 44);
        CHAR_ADVANCE.put(RANK_PRO, 31);
        CHAR_ADVANCE.put(RANK_ALLSTAR, 50);
        CHAR_ADVANCE.put(RANK_SUPERSTAR, 58);
        CHAR_ADVANCE.put(RANK_LEGEND, 41);
        CHAR_ADVANCE.put(RANK_MVP, 31);
        CHAR_ADVANCE.put(RANK_REFEREE, 46);
        CHAR_ADVANCE.put(RANK_ADMIN, 39);
        CHAR_ADVANCE.put(TEAM_IGNIS, 38);
        CHAR_ADVANCE.put(TEAM_FROST, 39);
        for (char c = BALL_PREVIEW_BASE; c <= '\uE33F'; c++) {
            CHAR_ADVANCE.put(c, 45);
        }
        for (char c = GUI_OVERLAY_SHOP; c <= GUI_OVERLAY_SETTINGS; c++) {
            CHAR_ADVANCE.put(c, 177);
        }

        for (char c : new char[] {
            GUI_OVERLAY_CONFIRM, GUI_OVERLAY_PLAY_BOT, GUI_OVERLAY_GUILD,
            GUI_OVERLAY_QUESTS, GUI_OVERLAY_ACHIEVEMENTS, GUI_OVERLAY_MAILBOX, GUI_OVERLAY_HISTORY,
            GUI_OVERLAY_FRIENDS, GUI_OVERLAY_PARTY, GUI_OVERLAY_RANKS, GUI_OVERLAY_SPECTATE,
            GUI_OVERLAY_PLAYER_CARD, GUI_OVERLAY_ADMIN, GUI_OVERLAY_REPORTS, GUI_OVERLAY_WARNINGS,
            GUI_OVERLAY_STUDIO, GUI_OVERLAY_SEASON
        }) {
            CHAR_ADVANCE.put(c, 177);
        }
    }

    private static void putWidth(String chars, int advance) {
        for (int i = 0; i < chars.length(); i++) {
            CHAR_ADVANCE.put(chars.charAt(i), advance);
        }
    }

    private Glyphs() {
    }

    public enum BannerVariant {
        REGULAR(BANNER_SCORER, TextColor.color(0x56E0FF), TextColor.color(0xFFC842)),
        THREE_POINTER(BANNER_THREE, TextColor.color(0x48FFB2), TextColor.color(0x7CF4FF)),
        SLAM_DUNK(BANNER_DUNK, TextColor.color(0xFF8A2B), TextColor.color(0xFFE45E));

        private final char glyph;
        private final TextColor primaryColor;
        private final TextColor tagColor;

        BannerVariant(char glyph, TextColor primaryColor, TextColor tagColor) {
            this.glyph = glyph;
            this.primaryColor = primaryColor;
            this.tagColor = tagColor;
        }

        public char glyph() {
            return glyph;
        }

        public TextColor primaryColor() {
            return primaryColor;
        }

        public TextColor tagColor() {
            return tagColor;
        }
    }

    public static String shift(int pixels) {
        if (pixels == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(8);
        int remaining = Math.abs(pixels);
        char base = pixels < 0 ? '\uE001' : '\uE011';
        for (int bit = 8; bit >= 0; bit--) {
            int val = 1 << bit;
            while (remaining >= val) {
                sb.append((char) (base + bit));
                remaining -= val;
            }
        }
        return sb.toString();
    }

    /**
     * Advance of a MiniMessage string in pixels, shift glyphs included.
     *
     * <p>{@link #textAdvancePx(String, boolean)} treats the negative/positive space glyphs as
     * ordinary characters, which is why the leaderboard rows could never be padded to an exact
     * width. This helper understands both ranges, so a layout can be proven to be
     * {@code N} pixels wide instead of hoped to be.
     */
    public static int markupAdvancePx(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return 0;
        }
        int total = 0;
        boolean bold = false;
        int i = 0;
        while (i < miniMessage.length()) {
            char c = miniMessage.charAt(i);
            if (c == '<') {
                int close = miniMessage.indexOf('>', i);
                if (close < 0) {
                    break;
                }
                String tag = miniMessage.substring(i + 1, close);
                if (tag.equals("bold") || tag.startsWith("bold:")) {
                    bold = true;
                } else if (tag.equals("/bold")) {
                    bold = false;
                }
                i = close + 1;
                continue;
            }
            int shifted = shiftAdvancePx(c);
            if (shifted != 0) {
                total += shifted;
            } else {
                total += (c == ' ' ? 4 : CHAR_ADVANCE.getOrDefault(c, 6)) + (bold ? 1 : 0);
            }
            i++;
        }
        return total;
    }

    /** Pixels carried by a space glyph ({@code 0} for a normal character). */
    public static int shiftAdvancePx(char c) {
        if (c >= '\uE001' && c <= '\uE009') {
            return -(1 << (c - '\uE001'));
        }
        if (c >= '\uE011' && c <= '\uE019') {
            return 1 << (c - '\uE011');
        }
        return 0;
    }

    public static int textAdvancePx(String plain, boolean bold) {
        if (plain == null || plain.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c == ' ') {
                total += 4;
                continue;
            }
            int adv = CHAR_ADVANCE.getOrDefault(c, 6);
            if (bold) {
                adv += 1;
            }
            total += adv;
        }
        return total;
    }

    public static String fitTextToWidth(String text, int maxPx, boolean bold) {
        if (text == null || text.isEmpty() || maxPx <= 0) {
            return "";
        }
        if (textAdvancePx(text, bold) <= maxPx) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisPx = textAdvancePx(ellipsis, bold);
        if (maxPx < ellipsisPx) {
            return "";
        }
        StringBuilder fitted = new StringBuilder(text.length() + 1);
        int usedPx = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            String glyph = Character.toString(codePoint);
            int glyphPx = textAdvancePx(glyph, bold);
            if (usedPx + glyphPx + ellipsisPx > maxPx) {
                break;
            }
            fitted.append(glyph);
            usedPx += glyphPx;
            i += Character.charCount(codePoint);
        }
        return fitted.append(ellipsis).toString();
    }

    public static Component bitmap(char glyph) {
        return Component.text(String.valueOf(glyph), BITMAP_STYLE);
    }

    public static int advanceOf(char glyph) {
        return textAdvancePx(String.valueOf(glyph), false);
    }

    public static Component topBadgePill(TopBadgeKind kind, int place) {
        if (place < 1 || place > 3) {
            return Component.empty();
        }
        return bitmap(kind.glyphForPlace(place));
    }

    public static String seamlessRuleString(int segments) {
        if (segments <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(segments * 2);
        for (int i = 0; i < segments; i++) {
            if (i > 0) {
                sb.append('\uE001');
            }
            sb.append(DIVIDER_SEG);
        }
        return sb.toString();
    }

    public static String seamlessRuleExactPx(int targetPx) {
        int px = Math.max(16, targetPx);
        int fullTiles = px / 16;
        int rem = px % 16;
        StringBuilder sb = new StringBuilder(fullTiles * 2 + 8);
        for (int i = 0; i < fullTiles; i++) {
            if (i > 0) {
                sb.append('\uE001');
            }
            sb.append(DIVIDER_SEG);
        }
        if (rem > 0) {

            sb.append(shift(-(17 - rem))).append(DIVIDER_SEG);
        }
        return sb.toString();
    }

    public static Component composeDividerHeader(String headerTitle) {
        int titlePx = textAdvancePx(headerTitle, false);
        int centerBlockPx = 7 + 4 + titlePx + 4 + 7;
        int remainingPx = Math.max(32, CHAT_DIVIDER_WIDTH_PX - centerBlockPx);
        int leftPx = Math.max(16, remainingPx / 2);
        int rightPx = Math.max(16, remainingPx - leftPx);

        TextComponent.Builder b = Component.text();
        b.append(Component.text(seamlessRuleExactPx(leftPx) + '\uE001' + DIVIDER_CAP_L, BITMAP_STYLE));
        b.append(Component.text(" " + headerTitle + " ", TextColor.color(0xFFF258)));
        b.append(Component.text(String.valueOf(DIVIDER_CAP_R) + '\uE001' + seamlessRuleExactPx(rightPx), BITMAP_STYLE));
        return b.asComponent();
    }

    public static Component composeDividerBottom() {
        return Component.text(seamlessRuleExactPx(CHAT_DIVIDER_WIDTH_PX), BITMAP_STYLE);
    }

    public static Component composeMenu3dRow(String label, boolean selected) {
        if (!selected) {
            return Component.text("  " + label, TextColor.color(0xF2F7FF));
        }
        int textPx = textAdvancePx(label, true);
        int shiftBack = -(MENU3D_BAR_WIDTH + 1) + 6;
        int shiftTail = Math.max(0, MENU3D_BAR_WIDTH - (6 + textPx));
        Style darkSelectedStyle = Style.style()
            .color(TextColor.color(0x061E14))
            .shadowColor(ShadowColor.none())
            .decoration(TextDecoration.BOLD, true)
            .build();
        return Component.text()
            .append(Component.text(String.valueOf(MENU3D_SELECT_BAR) + shift(shiftBack), BITMAP_STYLE))
            .append(Component.text(label, darkSelectedStyle))
            .append(Component.text(shift(shiftTail), BITMAP_STYLE))
            .asComponent();
    }

    public static Component composeMenu3dIndigoPill(String label) {
        int pillWidth = 84;
        int textPx = textAdvancePx(label, false);
        int startX = Math.max(4, (pillWidth - textPx) / 2);
        int shiftBack = -(pillWidth + 1) + startX;
        int shiftTail = Math.max(0, pillWidth - (startX + textPx));
        return Component.text()
            .append(Component.text(String.valueOf(MENU3D_PATCH_PILL) + shift(shiftBack), BITMAP_STYLE))
            .append(Component.text(label, TextColor.color(0xF4F7FF)))
            .append(Component.text(shift(shiftTail), BITMAP_STYLE))
            .asComponent();
    }

    /** Picks the frame size that wraps `rows` player rows; the frame is drawn on the header's first line. */
    public static Component tabFrameComponent(int rows) {
        int clamped = Math.max(1, Math.min(20, rows));
        int index = TAB_FRAME.length - 1;
        for (int i = 0; i < TAB_FRAME_MAX_ROWS.length; i++) {
            if (clamped <= TAB_FRAME_MAX_ROWS[i]) {
                index = i;
                break;
            }
        }
        return Component.text(String.valueOf(TAB_FRAME[index]), BITMAP_STYLE);
    }

    public static Component composeCenterTabRow(char rowGlyph, Component innerContent) {
        int shiftIntoBar = -(TAB_ROW_WIDTH + 1) + 7;
        return Component.text()
            .append(Component.text(String.valueOf(rowGlyph) + shift(shiftIntoBar), BITMAP_STYLE))
            .append(innerContent)
            .asComponent();
    }

    public static Component composeScorerBanner(String scorerName, String tagSmallCaps, BannerVariant variant) {
        int textAdv = textAdvancePx(scorerName, true) + textAdvancePx(" • ", false) + textAdvancePx(tagSmallCaps, false);
        int panelCenterX = 110;
        int startX = Math.max(30, panelCenterX - textAdv / 2);

        int cursorAfterBg = BANNER_MAIN_WIDTH + 1;
        int shiftToText = startX - cursorAfterBg;
        int cursorAfterText = startX + textAdv;
        int shiftToPinEnd = BANNER_MAIN_WIDTH - cursorAfterText;

        TextComponent.Builder b = Component.text();
        b.append(Component.text(String.valueOf(variant.glyph()) + shift(shiftToText), BITMAP_STYLE));
        b.append(Component.text(scorerName, Style.style(variant.primaryColor(), TextDecoration.BOLD)));
        b.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        b.append(Component.text(tagSmallCaps, Style.style(variant.tagColor())));
        b.append(Component.text(shift(shiftToPinEnd), BITMAP_STYLE));
        return b.asComponent();
    }

    public static Component composeAssistBanner(String labelSmallCaps, String valueText) {
        String sep = ": ";
        int wLabel = textAdvancePx(labelSmallCaps, false);
        int wSep = textAdvancePx(sep, false);
        int wVal = textAdvancePx(valueText, false);
        int totalTextAdv = wLabel + wSep + wVal;

        int centerX = BANNER_ASSIST_WIDTH / 2;
        int startX = Math.max(6, centerX - totalTextAdv / 2);
        int cursorAfterBg = BANNER_ASSIST_WIDTH + 1;
        int shiftToText = startX - cursorAfterBg;
        int shiftToPinEnd = BANNER_ASSIST_WIDTH - (startX + totalTextAdv);

        return Component.text()
            .append(Component.text(String.valueOf(BANNER_ASSIST) + shift(shiftToText), BITMAP_STYLE))
            .append(Component.text(labelSmallCaps, TextColor.color(0x9BB8E8)))
            .append(Component.text(sep, NamedTextColor.DARK_GRAY))
            .append(Component.text(valueText, TextColor.color(0xEAF6FF)))
            .append(Component.text(shift(shiftToPinEnd), BITMAP_STYLE))
            .asComponent();
    }

    public static Component composeLiveScorebug(
        String homeLabel,
        int homeScore,
        String clockText,
        int shotClock,
        int awayScore,
        String awayLabel
    ) {
        String sHome = String.valueOf(homeScore);
        String sAway = String.valueOf(awayScore);
        String center = clockText + " " + shotClock + "ꜱ";

        int wHome = textAdvancePx(homeLabel, true);
        int wHScore = textAdvancePx(sHome, true);
        int wCenter = textAdvancePx(center, false);
        int wAScore = textAdvancePx(sAway, true);
        int wAway = textAdvancePx(awayLabel, true);

        TextColor shotColor = shotClock <= 5
            ? TextColor.color(0xFF4B4B)
            : (shotClock <= 10 ? TextColor.color(0xFFD24C) : TextColor.color(0xE8F2FF));

        TextComponent.Builder b = Component.text();
        int cursor = SCOREBUG_WIDTH + 1;

        int x1 = 28 - wHome / 2;
        b.append(Component.text(String.valueOf(SCOREBUG_BG) + shift(x1 - cursor), BITMAP_STYLE));
        b.append(Component.text(homeLabel, Style.style(TextColor.color(0xFFF2F2), TextDecoration.BOLD)));
        cursor = x1 + wHome;

        int x2 = 69 - wHScore / 2;
        b.append(Component.text(shift(x2 - cursor), BITMAP_STYLE));
        b.append(Component.text(sHome, Style.style(TextColor.color(0xFFE25C), TextDecoration.BOLD)));
        cursor = x2 + wHScore;

        int x3 = 114 - wCenter / 2;
        b.append(Component.text(shift(x3 - cursor), BITMAP_STYLE));
        b.append(Component.text(clockText + " ", TextColor.color(0xFFFFFF)));
        b.append(Component.text(shotClock + "ꜱ", shotColor));
        cursor = x3 + wCenter;

        int x4 = 159 - wAScore / 2;
        b.append(Component.text(shift(x4 - cursor), BITMAP_STYLE));
        b.append(Component.text(sAway, Style.style(TextColor.color(0xFFE25C), TextDecoration.BOLD)));
        cursor = x4 + wAScore;

        int x5 = 200 - wAway / 2;
        b.append(Component.text(shift(x5 - cursor), BITMAP_STYLE));
        b.append(Component.text(awayLabel, Style.style(TextColor.color(0xEFF9FF), TextDecoration.BOLD)));
        cursor = x5 + wAway;

        b.append(Component.text(shift(SCOREBUG_WIDTH - cursor), BITMAP_STYLE));
        return b.asComponent();
    }

    public static Component composeShotMeter(double progress, boolean released, ShotReleaseQuality quality) {
        int totalCells = 12;
        int greenStart = 9;
        int greenEnd = 10;
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.15, progress)) * 10.5);

        StringBuilder bar = new StringBuilder(totalCells + 2);
        bar.append(METER_CAP_L);
        for (int i = 1; i <= totalCells; i++) {
            boolean isGreenSlot = (i >= greenStart && i <= greenEnd);
            if (i <= filled) {
                if (i > greenEnd) {
                    bar.append(METER_LATE_RED);
                } else if (isGreenSlot) {
                    bar.append(METER_GREEN_HIT);
                } else {
                    bar.append(METER_FILL_WARM);
                }
            } else {
                bar.append(isGreenSlot ? METER_GREEN_ZONE : METER_EMPTY);
            }
        }
        bar.append(METER_CAP_R);

        TextComponent.Builder out = Component.text();
        out.append(Component.text(bar.toString(), BITMAP_STYLE));
        out.append(Component.text("  "));
        if (!released) {
            int pct = (int) Math.min(100, Math.round((progress / 0.92) * 100.0));
            TextColor c = (progress >= 0.84 && progress <= 1.00)
                ? TextColor.color(0x4CFF88)
                : (progress > 1.00 ? TextColor.color(0xFF5252) : TextColor.color(0xFFD24C));
            out.append(Component.text(pct + "% SHOT", Style.style(c, TextDecoration.BOLD)));
        } else if (quality != null) {
            out.append(Component.text(quality.displayLabel(), Style.style(quality.color(), TextDecoration.BOLD)));
        }
        return out.asComponent();
    }

    public static Component composeProAimHud(
        double progress,
        boolean released,
        ShotReleaseQuality quality,
        boolean aimingAtHoop,
        double yawErrorDeg,
        double distanceBlocks,
        boolean isThree,
        double contestRatio
    ) {
        TextComponent.Builder out = Component.text();

        if (!aimingAtHoop) {
            out.append(Component.text(String.valueOf(RETICLE_RED), BITMAP_STYLE));
            out.append(Component.text(" FREE THROW  ", Style.style(TextColor.color(0xFF7A88), TextDecoration.BOLD)));
        } else {
            double absErr = Math.abs(yawErrorDeg);
            boolean inGreenTiming = progress >= 0.80 && progress <= 1.00;
            char reticle = (absErr <= 2.6 && inGreenTiming && contestRatio < 0.35)
                ? RETICLE_GREEN
                : (absErr <= 4.2 ? RETICLE_CYAN : (absErr <= 10.5 ? RETICLE_AMBER : RETICLE_RED));
            out.append(Component.text(String.valueOf(reticle), BITMAP_STYLE));
            out.append(Component.text(" "));
            out.append(buildCompassWidget(yawErrorDeg));
            out.append(Component.text("  "));
        }

        double clamped = Math.max(0.0, Math.min(1.0, progress));
        int totalCells = 12;
        int greenStart = contestRatio >= 0.55 ? 11 : 10;
        int filled = (int) Math.round(clamped * totalCells);

        StringBuilder bar = new StringBuilder(totalCells + 2);
        bar.append(METER_CAP_L);
        for (int i = 1; i <= totalCells; i++) {
            boolean isGreenSlot = i >= greenStart;
            if (i <= filled) {
                bar.append(isGreenSlot ? METER_GREEN_HIT : METER_FILL_WARM);
            } else {
                bar.append(isGreenSlot ? METER_GREEN_ZONE : METER_EMPTY);
            }
        }
        bar.append(METER_CAP_R);
        out.append(Component.text(bar.toString(), BITMAP_STYLE));
        out.append(Component.text("  "));

        int pct = (int) Math.round(clamped * 100.0);
        if (released && quality != null) {
            if (!aimingAtHoop) {
                out.append(Component.text("LOB / THROW (" + pct + "%)", Style.style(TextColor.color(0x8CD8FF), TextDecoration.BOLD)));
            } else {
                out.append(Component.text(quality.displayLabel(), Style.style(quality.color(), TextDecoration.BOLD)));
            }
        } else if (aimingAtHoop) {
            String ptsTag = isThree ? "3PT" : "2PT";
            TextColor powerColor = pct >= 85 ? TextColor.color(0x4CFF88) : TextColor.color(0xFFD24C);
            out.append(Component.text(pct + "% ", Style.style(powerColor, TextDecoration.BOLD)));
            out.append(Component.text(String.format(java.util.Locale.ROOT, "%.1fm %s", distanceBlocks, ptsTag), Style.style(TextColor.color(isThree ? 0x5CEEFF : 0xFFE27A), TextDecoration.BOLD)));
            out.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            appendContestBadge(out, contestRatio);
        } else {
            TextColor powerColor = pct >= 85 ? TextColor.color(0x4CFF88) : TextColor.color(0xFFD24C);
            out.append(Component.text(pct + "% DRAW POWER — Release Right-Click", Style.style(powerColor, TextDecoration.BOLD)));
        }
        return out.asComponent();
    }

    public static Component composeIdleAimCompass(
        boolean aimingAtHoop,
        double yawErrorDeg,
        double distanceBlocks,
        boolean isThree,
        boolean inDunkLane,
        double contestRatio
    ) {
        TextComponent.Builder out = Component.text();
        if (!aimingAtHoop) {
            out.append(Component.text(String.valueOf(RETICLE_RED), BITMAP_STYLE));
            out.append(Component.text(" Hold Right-Click to Draw Shot", TextColor.color(0x9BA9BE)));
            out.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            out.append(Component.text("Q Pass", TextColor.color(0x64D8FF)));
            return out.asComponent();
        }

        double absErr = Math.abs(yawErrorDeg);
        char reticle = absErr <= 3.0 ? RETICLE_GREEN : (absErr <= 7.5 ? RETICLE_CYAN : RETICLE_AMBER);
        out.append(Component.text(String.valueOf(reticle), BITMAP_STYLE));
        out.append(Component.text(" "));
        out.append(buildCompassWidget(yawErrorDeg));
        out.append(Component.text("  "));

        if (inDunkLane) {
            out.append(Component.text("⚡ DUNK LANE", Style.style(TextColor.color(0xFF9636), TextDecoration.BOLD)));
        } else {
            String ptsTag = isThree ? "3PT" : "2PT";
            out.append(Component.text(String.format(java.util.Locale.ROOT, "%.1fm (%s)", distanceBlocks, ptsTag), Style.style(TextColor.color(isThree ? 0x5CEEFF : 0xFFE27A), TextDecoration.BOLD)));
        }
        out.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        appendContestBadge(out, contestRatio);
        return out.asComponent();
    }

    public static Component composeDunkTimingHud(double dunkProgress, boolean lockedIn, boolean goodFinish) {
        int totalCells = 10;
        int greenStart = 6;
        int greenEnd = 9;
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, dunkProgress)) * totalCells);

        StringBuilder bar = new StringBuilder(totalCells + 2);
        bar.append(METER_CAP_L);
        for (int i = 1; i <= totalCells; i++) {
            boolean green = (i >= greenStart && i <= greenEnd);
            if (i <= filled) {
                bar.append(green ? METER_GREEN_HIT : METER_FILL_WARM);
            } else {
                bar.append(green ? METER_GREEN_ZONE : METER_EMPTY);
            }
        }
        bar.append(METER_CAP_R);

        TextComponent.Builder out = Component.text();
        boolean inGreenNow = dunkProgress >= 0.56 && dunkProgress <= 0.90;
        out.append(Component.text(String.valueOf(goodFinish || inGreenNow ? RETICLE_GREEN : RETICLE_AMBER), BITMAP_STYLE));
        out.append(Component.text(" "));
        out.append(Component.text(bar.toString(), BITMAP_STYLE));
        out.append(Component.text("  "));
        if (lockedIn) {
            out.append(Component.text(
                goodFinish ? "💥 SLAMMED HOME!" : "✖ MISSED TIMING!",
                Style.style(TextColor.color(goodFinish ? 0x4CFF88 : 0xFF6B4A), TextDecoration.BOLD)
            ));
        } else if (inGreenNow) {
            out.append(Component.text("🔥 CLICK NOW TO SLAM!", Style.style(TextColor.color(0x4CFF88), TextDecoration.BOLD)));
        } else {
            out.append(Component.text("⚡ Wait for Green & Click!", Style.style(TextColor.color(0xFFD43B), TextDecoration.BOLD)));
        }
        return out.asComponent();
    }

    private static Component buildCompassWidget(double yawErrorDeg) {
        int idx = 4 + (int) Math.round(Math.max(-18.0, Math.min(18.0, yawErrorDeg)) / 4.2);
        idx = Math.max(0, Math.min(8, idx));
        double absErr = Math.abs(yawErrorDeg);
        TextColor markerColor = absErr <= 2.8
            ? TextColor.color(0x4CFF88)
            : (absErr <= 6.5 ? TextColor.color(0x5CEEFF) : (absErr <= 11.5 ? TextColor.color(0xFFB838) : TextColor.color(0xFF5666)));

        TextComponent.Builder c = Component.text();
        c.append(Component.text("⟦", TextColor.color(0x58667D)));
        for (int i = 0; i < 9; i++) {
            if (i == idx) {
                c.append(Component.text(i == 4 ? "◈" : "◆", Style.style(markerColor, TextDecoration.BOLD)));
            } else if (i == 4) {
                c.append(Component.text("┼", TextColor.color(0x8899B2)));
            } else {
                c.append(Component.text("·", TextColor.color(0x424E63)));
            }
        }
        c.append(Component.text("⟧", TextColor.color(0x58667D)));
        return c.asComponent();
    }

    private static void appendContestBadge(TextComponent.Builder out, double contestRatio) {
        int pct = (int) Math.round(Math.max(0.0, Math.min(1.0, contestRatio)) * 100.0);
        if (pct < 12) {
            out.append(Component.text("OPEN", Style.style(TextColor.color(0x4CFF88), TextDecoration.BOLD)));
        } else if (pct < 48) {
            out.append(Component.text("CONTESTED " + pct + "%", Style.style(TextColor.color(0xFFD24C), TextDecoration.BOLD)));
        } else {
            out.append(Component.text("SMOTHERED " + pct + "%", Style.style(TextColor.color(0xFF5252), TextDecoration.BOLD)));
        }
    }
}
