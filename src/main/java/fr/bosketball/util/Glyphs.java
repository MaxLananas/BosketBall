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

/**
 * Pixel-exact Custom Font, Negative-Space & Broadcast HUD Compositor for Minecraft 26.2.
 * Uses Adventure 5.2.0 ShadowColor.none() so custom bitmap banners & badges render with zero drop-shadow artifacts.
 */
public final class Glyphs {

    // Broadcast Banners & HUD Backgrounds
    public static final char BANNER_SCORER = '\uE100'; // 196px wide
    public static final char BANNER_THREE  = '\uE101'; // 196px wide
    public static final char BANNER_DUNK   = '\uE102'; // 196px wide
    public static final char BANNER_ASSIST = '\uE103'; // 136px wide
    public static final char SCOREBUG_BG   = '\uE104'; // 228px wide
    public static final char LOGO_HEADER   = '\uE105'; // 156px wide

    public static final int BANNER_MAIN_WIDTH   = 196;
    public static final int BANNER_ASSIST_WIDTH = 136;
    public static final int SCOREBUG_WIDTH      = 228;

    // Chat Stat Icons (9x9px)
    public static final char ICON_TROPHY = '\uE200';
    public static final char ICON_STAR   = '\uE201';
    public static final char ICON_BALL   = '\uE202';
    public static final char ICON_FIRE   = '\uE203';
    public static final char ICON_CROWN  = '\uE204';
    public static final char ICON_SHIELD = '\uE205';
    public static final char ICON_BOLT   = '\uE206';

    // Rank Pill Badges (height 9px)
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

    // Shot Meter Segments (8px wide each)
    public static final char METER_CAP_L      = '\uE300';
    public static final char METER_EMPTY      = '\uE301';
    public static final char METER_FILL_WARM  = '\uE302';
    public static final char METER_GREEN_ZONE = '\uE303';
    public static final char METER_GREEN_HIT  = '\uE304';
    public static final char METER_LATE_RED   = '\uE305';
    public static final char METER_CAP_R      = '\uE306';

    // Custom Aiming Reticles (11x11px)
    public static final char RETICLE_GREEN = '\uE310';
    public static final char RETICLE_CYAN  = '\uE311';
    public static final char RETICLE_AMBER = '\uE312';
    public static final char RETICLE_RED   = '\uE313';

    public static final Style BITMAP_STYLE = Style.style()
        .color(NamedTextColor.WHITE)
        .shadowColor(ShadowColor.none())
        .decoration(TextDecoration.ITALIC, false)
        .decoration(TextDecoration.BOLD, false)
        .build();

    private static final Map<Character, Integer> CHAR_ADVANCE = new HashMap<>();

    static {
        // Standard Minecraft default.json advance widths (glyph pixels + 1px inter-char gap)
        putWidth(" !.,:;i|¡'", 2);
        putWidth("`l", 3);
        putWidth("\"()*I[]t{}ɪ", 4);
        putWidth("<>fk", 5);
        // Most standard ASCII letters, digits & small-caps default to 6px advance (5px glyph + 1px gap)
        putWidth("@~", 7);
        putWidth("ᴍᴡ", 6);
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

    /**
     * Decomposes any signed pixel shift [-511..+511] into exact binary space provider characters (\uE001..\uE019).
     */
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
     * Computes exact advance width in pixels for plain text rendered in Minecraft's default + unifont providers.
     */
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

    public static Component bitmap(char glyph) {
        return Component.text(String.valueOf(glyph), BITMAP_STYLE);
    }

    /**
     * Composes the Top Scorer Banner (inspired by image-1.png) with exact 196px pinned total width!
     * Left [0..28] holds the 3D basketball icon; Right [28..192] (center x=110) centers Scorer + Role label!
     */
    public static Component composeScorerBanner(String scorerName, String tagSmallCaps, BannerVariant variant) {
        String headline = scorerName + " • " + tagSmallCaps;
        int textAdv = textAdvancePx(scorerName, true) + textAdvancePx(" • ", false) + textAdvancePx(tagSmallCaps, false);
        int panelCenterX = 110;
        int startX = Math.max(30, panelCenterX - textAdv / 2);

        int cursorAfterBg = BANNER_MAIN_WIDTH + 1; // 196px glyph + 1px inter-char advance = 197
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

    /**
     * Composes the Secondary Sub-Banner right beneath the Scorer Banner (e.g. "ᴀꜱꜱɪꜱᴛ: A_Koop" or "ꜱᴡɪꜱʜ • 8.4ᴍ")
     * with exact 136px pinned total width so both BossBars stack symmetrically!
     */
    public static Component composeAssistBanner(String labelSmallCaps, String valueText) {
        String sep = ": ";
        int wLabel = textAdvancePx(labelSmallCaps, false);
        int wSep = textAdvancePx(sep, false);
        int wVal = textAdvancePx(valueText, false);
        int totalTextAdv = wLabel + wSep + wVal;

        int centerX = BANNER_ASSIST_WIDTH / 2; // 68
        int startX = Math.max(6, centerX - totalTextAdv / 2);
        int cursorAfterBg = BANNER_ASSIST_WIDTH + 1; // 137
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

    /**
     * Composes the 5-Column ESPN/NBA Live Match Scorebug with exact 228px pinned width:
     * [ Home (0..56, c=28) | HScore (56..82, c=69) | Clock/Shot (82..146, c=114) | AScore (146..172, c=159) | Away (172..228, c=200) ]
     */
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
        int cursor = SCOREBUG_WIDTH + 1; // 229 after drawing 228px background

        // Column 1: Home Team (center 28)
        int x1 = 28 - wHome / 2;
        b.append(Component.text(String.valueOf(SCOREBUG_BG) + shift(x1 - cursor), BITMAP_STYLE));
        b.append(Component.text(homeLabel, Style.style(TextColor.color(0xFFF2F2), TextDecoration.BOLD)));
        cursor = x1 + wHome;

        // Column 2: Home Score (center 69)
        int x2 = 69 - wHScore / 2;
        b.append(Component.text(shift(x2 - cursor), BITMAP_STYLE));
        b.append(Component.text(sHome, Style.style(TextColor.color(0xFFE25C), TextDecoration.BOLD)));
        cursor = x2 + wHScore;

        // Column 3: Quarter + Clock + Shot Clock (center 114)
        int x3 = 114 - wCenter / 2;
        b.append(Component.text(shift(x3 - cursor), BITMAP_STYLE));
        b.append(Component.text(clockText + " ", TextColor.color(0xFFFFFF)));
        b.append(Component.text(shotClock + "ꜱ", shotColor));
        cursor = x3 + wCenter;

        // Column 4: Away Score (center 159)
        int x4 = 159 - wAScore / 2;
        b.append(Component.text(shift(x4 - cursor), BITMAP_STYLE));
        b.append(Component.text(sAway, Style.style(TextColor.color(0xFFE25C), TextDecoration.BOLD)));
        cursor = x4 + wAScore;

        // Column 5: Away Team (center 200)
        int x5 = 200 - wAway / 2;
        b.append(Component.text(shift(x5 - cursor), BITMAP_STYLE));
        b.append(Component.text(awayLabel, Style.style(TextColor.color(0xEFF9FF), TextDecoration.BOLD)));
        cursor = x5 + wAway;

        // Pin exact total width to 228px so scorebug never jitters on screen!
        b.append(Component.text(shift(SCOREBUG_WIDTH - cursor), BITMAP_STYLE));
        return b.asComponent();
    }

    /**
     * Composes the ActionBar NBA 2K Green-Window Shot Meter (12 segments; segments 9 & 10 are the Green Zone!).
     */
    public static Component composeShotMeter(double progress, boolean released, ShotReleaseQuality quality) {
        int totalCells = 12;
        int greenStart = 9; // ~0.78..0.95 sweet spot
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
            out.append(Component.text(pct + "% " + SmallCaps.toSmallCaps("jauge de tir"), Style.style(c, TextDecoration.BOLD)));
        } else if (quality != null) {
            out.append(Component.text(quality.displayLabel(), Style.style(quality.color(), TextDecoration.BOLD)));
        }
        return out.asComponent();
    }

    /**
     * Composes the sleek Pro Aiming Reticle + Horizontal Rim Lock Compass + NBA 2K Shot Meter + Live Contest Telemetry!
     */
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

        // 1. Custom Reticle Glyph + Dynamic 9-slot Rim Lock Compass
        if (!aimingAtHoop) {
            out.append(Component.text(String.valueOf(RETICLE_RED), BITMAP_STYLE));
            out.append(Component.text(" " + SmallCaps.toSmallCaps("tir libre") + "  ", Style.style(TextColor.color(0xFF7A88), TextDecoration.BOLD)));
        } else {
            double absErr = Math.abs(yawErrorDeg);
            boolean inGreenTiming = progress >= 0.84 && progress <= 1.00;
            char reticle = (absErr <= 2.6 && inGreenTiming && contestRatio < 0.35)
                ? RETICLE_GREEN
                : (absErr <= 4.2 ? RETICLE_CYAN : (absErr <= 10.5 ? RETICLE_AMBER : RETICLE_RED));
            out.append(Component.text(String.valueOf(reticle), BITMAP_STYLE));
            out.append(Component.text(" "));
            out.append(buildCompassWidget(yawErrorDeg));
            out.append(Component.text("  "));
        }

        // 2. Shot Meter Bar (green zone narrows visually under heavy contest!)
        int totalCells = 12;
        int greenStart = contestRatio >= 0.55 ? 10 : 9;
        int greenEnd = 10;
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.15, progress)) * 10.5);

        StringBuilder bar = new StringBuilder(totalCells + 2);
        bar.append(METER_CAP_L);
        for (int i = 1; i <= totalCells; i++) {
            boolean isGreenSlot = aimingAtHoop && (i >= greenStart && i <= greenEnd);
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
        out.append(Component.text(bar.toString(), BITMAP_STYLE));
        out.append(Component.text("  "));

        // 3. Release Quality OR Live Distance + Contest Badge
        if (released && quality != null) {
            if (!aimingAtHoop) {
                out.append(Component.text(SmallCaps.toSmallCaps("passe / lancer libre"), Style.style(TextColor.color(0x8CD8FF), TextDecoration.BOLD)));
            } else {
                out.append(Component.text(quality.displayLabel(), Style.style(quality.color(), TextDecoration.BOLD)));
            }
        } else if (aimingAtHoop) {
            String ptsTag = isThree ? "3ᴘᴛ" : "2ᴘᴛ";
            out.append(Component.text(String.format("%.1fᴍ %s", distanceBlocks, ptsTag), Style.style(TextColor.color(isThree ? 0x5CEEFF : 0xFFE27A), TextDecoration.BOLD)));
            out.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            appendContestBadge(out, contestRatio);
        } else {
            int pct = (int) Math.min(100, Math.round((progress / 0.92) * 100.0));
            out.append(Component.text(pct + "% " + SmallCaps.toSmallCaps("force"), Style.style(TextColor.color(0xFFD24C), TextDecoration.BOLD)));
        }
        return out.asComponent();
    }

    /**
     * Subtle live Court Aiming & Range-Finder HUD shown while dribbling so players always feel real directional aim!
     */
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
            out.append(Component.text(" " + SmallCaps.toSmallCaps("visez l'arceau pour tirer"), TextColor.color(0x9BA9BE)));
            out.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            out.append(Component.text("Q " + SmallCaps.toSmallCaps("passe"), TextColor.color(0x64D8FF)));
            return out.asComponent();
        }

        double absErr = Math.abs(yawErrorDeg);
        char reticle = absErr <= 3.0 ? RETICLE_GREEN : (absErr <= 7.5 ? RETICLE_CYAN : RETICLE_AMBER);
        out.append(Component.text(String.valueOf(reticle), BITMAP_STYLE));
        out.append(Component.text(" "));
        out.append(buildCompassWidget(yawErrorDeg));
        out.append(Component.text("  "));

        if (inDunkLane) {
            out.append(Component.text("⚡ " + SmallCaps.toSmallCaps("zone dunk"), Style.style(TextColor.color(0xFF9636), TextDecoration.BOLD)));
        } else {
            String ptsTag = isThree ? "3ᴘᴛ" : "2ᴘᴛ";
            out.append(Component.text(String.format("%.1fᴍ (%s)", distanceBlocks, ptsTag), Style.style(TextColor.color(isThree ? 0x5CEEFF : 0xFFE27A), TextDecoration.BOLD)));
        }
        out.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        appendContestBadge(out, contestRatio);
        return out.asComponent();
    }

    /**
     * Aerial Slam Dunk Timing Meter (press/release near the green rim zone to hammer it down!).
     */
    public static Component composeDunkTimingHud(double dunkProgress, boolean lockedIn, boolean goodFinish) {
        int totalCells = 10;
        int greenStart = 7;
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
        out.append(Component.text(String.valueOf(goodFinish || (dunkProgress >= 0.68 && dunkProgress <= 0.96) ? RETICLE_GREEN : RETICLE_AMBER), BITMAP_STYLE));
        out.append(Component.text(" "));
        out.append(Component.text(bar.toString(), BITMAP_STYLE));
        out.append(Component.text("  "));
        if (lockedIn) {
            out.append(Component.text(
                goodFinish ? "💥 " + SmallCaps.toSmallCaps("poster slam verrouille !") : "⚠ " + SmallCaps.toSmallCaps("timing dunk risque !"),
                Style.style(TextColor.color(goodFinish ? 0x4CFF88 : 0xFF6B4A), TextDecoration.BOLD)
            ));
        } else {
            out.append(Component.text("⚡ " + SmallCaps.toSmallCaps("cliquez dans le vert au cercle !"), Style.style(TextColor.color(0xFFD43B), TextDecoration.BOLD)));
        }
        return out.asComponent();
    }

    private static Component buildCompassWidget(double yawErrorDeg) {
        // Maps [-18° .. +18°] onto 9 slots [0..8], with slot 4 = dead-center on the rim!
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
            out.append(Component.text(SmallCaps.toSmallCaps("ouvert"), Style.style(TextColor.color(0x4CFF88), TextDecoration.BOLD)));
        } else if (pct < 48) {
            out.append(Component.text(SmallCaps.toSmallCaps("conteste ") + pct + "%", Style.style(TextColor.color(0xFFD24C), TextDecoration.BOLD)));
        } else {
            out.append(Component.text(SmallCaps.toSmallCaps("etouffe ") + pct + "%", Style.style(TextColor.color(0xFF5252), TextDecoration.BOLD)));
        }
    }
}
