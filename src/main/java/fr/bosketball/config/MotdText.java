package fr.bosketball.config;

import java.util.List;

/**
 * Round 29 — the single source of truth for the server-list banner copy.
 *
 * <p>Design rules that make the banner read as "premium" instead of "rainbow":
 * <ul>
 *   <li>the domain is never printed: the name alone carries the brand, the icon already shows it;</li>
 *   <li>one saturated gradient per line at most, everything else stays slate/gold so the two lines
 *       never fight for attention;</li>
 *   <li>the same tiny diamond ornament frames every line, at 40 % opacity, so the eye lands on the
 *       headline and not on the decoration;</li>
 *   <li>four states — open, starting, maintenance, restarting — each with its own tone: warm gold,
 *       amber "lights coming on", amber "works in progress", cold slate "back in a moment".</li>
 *   <li>every line stays well under the 268 px MOTD canvas so the pixel centring never clips.</li>
 * </ul>
 */
public final class MotdText {

    /** Slate diamond used to frame the banner lines. */
    public static final String ORNAMENT_SLATE = "<#64748B>\u25C6</#64748B>";
    /** Amber diamond used by the maintenance banner. */
    public static final String ORNAMENT_AMBER = "<#F59E0B>\u25C6</#F59E0B>";

    public static final String DEFAULT_HEADER =
        "<gradient:#FFE9A8:#FFC53D:#FF8C1A><bold>\u2726 BOSKETBALL \u2726</bold></gradient>";

    public static final List<String> DEFAULT_SUBTITLES = List.of(
        ORNAMENT_SLATE + " <gradient:#F8FAFC:#CBD5E1><bold>STREET BASKETBALL, PERFECTED</bold></gradient> " + ORNAMENT_SLATE,
        ORNAMENT_SLATE + " <gradient:#FFD43B:#FF8C1A><bold>RANKED 1V1 \u00B7 2V2 \u00B7 3V3 \u00B7 5V5</bold></gradient> " + ORNAMENT_SLATE,
        ORNAMENT_SLATE + " <gradient:#7DD3FC:#818CF8><bold>DUNKS \u00B7 REPLAYS \u00B7 COINS SHOP</bold></gradient> " + ORNAMENT_SLATE,
        ORNAMENT_SLATE + " <gradient:#4ADE80:#22D3EE><bold>A FRESH SEASON, A FRESH COURT</bold></gradient> " + ORNAMENT_SLATE,
        ORNAMENT_SLATE + " <gradient:#FDE68A:#FF9E2C><bold>JOIN THE COURT, OWN THE GAME</bold></gradient> " + ORNAMENT_SLATE
    );

    public static final String DEFAULT_MAINTENANCE_HEADER =
        "<gradient:#F8FAFC:#94A3B8><bold>\u2726 BOSKETBALL \u2726</bold></gradient>";

    public static final String DEFAULT_MAINTENANCE_SUBTITLE =
        ORNAMENT_AMBER + " <gradient:#FDE68A:#F59E0B><bold>COURT UNDER REFIT \u00B7 BACK SOON</bold></gradient> " + ORNAMENT_AMBER;

    /** Cold-amber diamond used by the starting banner. */
    public static final String ORNAMENT_GOLD = "<#D97706>\u25C6</#D97706>";

    public static final String DEFAULT_STARTING_HEADER =
        "<gradient:#FDE68A:#F59E0B><bold>\u2726 BOSKETBALL \u2726</bold></gradient>";

    public static final String DEFAULT_STARTING_SUBTITLE =
        ORNAMENT_GOLD + " <gradient:#FDE68A:#F59E0B><bold>WARMING UP \u00B7 ALMOST READY</bold></gradient> "
            + ORNAMENT_GOLD;

    public static final String DEFAULT_CLOSED_HEADER =
        "<gradient:#E2E8F0:#64748B><bold>\u2726 BOSKETBALL \u2726</bold></gradient>";

    public static final String DEFAULT_CLOSED_SUBTITLE =
        ORNAMENT_SLATE + " <gradient:#CBD5E1:#64748B><bold>RESTARTING \u00B7 SEE YOU IN A MOMENT</bold></gradient> " + ORNAMENT_SLATE;

    private MotdText() {
    }
}
