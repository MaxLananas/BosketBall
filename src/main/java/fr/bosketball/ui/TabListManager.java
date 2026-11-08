package fr.bosketball.ui;

import fr.bosketball.util.Palette;
import fr.bosketball.game.GameManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TabListManager {

    private static final Component STATIC_LOGO_BLOCK = buildStaticLogoBlock();

    private static Component buildStaticLogoBlock() {
        TextComponent.Builder b = Component.text();
        b.append(Component.newline());
        for (int i = 0; i < Glyphs.CHAT_LOGO_STRIPS.length; i++) {
            b.append(Glyphs.logoStripComponent(i)).append(Component.newline());
        }
        b.append(Component.newline());
        return b.asComponent();
    }

    private final StatsManager statsManager;
    private final String serverDomain;
    private final Map<UUID, String> lastHeaderFooterSig = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastEntrySig = new ConcurrentHashMap<>();

    /**
     * Round 34 — why the rows swapped between two players every second.
     *
     * <p>Every tab-list entry carries a {@code list order} (Minecraft 1.21.2+), exposed by Paper as
     * {@link Player#setPlayerListOrder(int)}. Everybody defaulted to {@code 0}, i.e. every row was
     * tied with every other one, and the client is then free to lay tied rows out in whatever order
     * its internal map hands them over — exactly what the owner saw: two nicknames trading places
     * twice a second. We now assign one explicit, total order and only push the number when it
     * really changed.
     */
    private final Map<UUID, Integer> lastListOrder = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------------------------
    // Round 40 — the [TAB] card.
    //
    // The golden nine-sliced frame is gone for good. In 26.2 the client never drew it: the owner got
    // a grey rectangle with a gold outline instead of the panel, twice. What replaces it is a wide,
    // aligned text card — plain Minecraft text and the pack's own icons, nothing that can fail to
    // render. Every line is padded to exactly CARD_PX font pixels with Glyphs.shift(), which is what
    // turns the client's line-by-line centring into one clean left-aligned block, the way Hypixel
    // Skyblock's tab reads.
    //
    // Two rules keep it honest: no bold anywhere (bold adds 1 px per glyph and would drift the
    // columns), and nothing the sidebar already shows — except the coins, rank and playtime the
    // owner asked to see in the tab too.
    // -------------------------------------------------------------------------------------------

    /** Width of the [TAB] card, in font pixels (≈ 50 default-font characters). */
    public static final int CARD_PX = 300;

    /**
     * Round 40: the season label is gone from the tab. It lives on the sidebar, and the owner's rule
     * is that the tab only carries what the sidebar does not already show.
     */
    public TabListManager(StatsManager statsManager, String serverDomain) {
        this.statsManager = statsManager;
        this.serverDomain = serverDomain;
    }

    public void clearPlayerCache(UUID playerId) {
        if (playerId != null) {
            lastHeaderFooterSig.remove(playerId);
            lastEntrySig.remove(playerId);
            lastListOrder.remove(playerId);
        }
    }

    private java.util.function.Function<UUID, fr.bosketball.model.PlayerStatus> presenceProvider;

    private java.util.function.Function<UUID, fr.bosketball.social.Guild> guildProvider;

    public void attachGuildProvider(java.util.function.Function<UUID, fr.bosketball.social.Guild> provider) {
        this.guildProvider = provider;
        this.lastEntrySig.clear();
    }

    private fr.bosketball.social.Guild guildOf(Player p) {
        if (guildProvider == null) {
            return null;
        }
        try {
            return guildProvider.apply(p.getUniqueId());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Component parseMultiline(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return Component.empty();
        }
        String normalized = fr.bosketball.util.Glyphs.smallCapsLabels(miniMessage).replace("\\n", "\n");
        if (normalized.indexOf('\n') < 0) {
            return Msg.parse(normalized);
        }
        java.util.List<Component> parts = new java.util.ArrayList<>();
        String[] chunks = normalized.split("\n", -1);
        for (int i = 0; i < chunks.length; i++) {
            if (i > 0) {
                parts.add(Component.newline());
            }
            if (!chunks[i].isEmpty()) {
                parts.add(Msg.parse(chunks[i]));
            }
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.noSeparators(), parts);
    }

    public void attachPresenceProvider(java.util.function.Function<UUID, fr.bosketball.model.PlayerStatus> provider) {
        this.presenceProvider = provider;
    }

    private fr.bosketball.model.PlayerStatus statusOf(Player p) {
        if (presenceProvider == null) {
            return fr.bosketball.model.PlayerStatus.LOBBY;
        }
        try {
            fr.bosketball.model.PlayerStatus st = presenceProvider.apply(p.getUniqueId());
            return st == null ? fr.bosketball.model.PlayerStatus.LOBBY : st;
        } catch (RuntimeException ex) {
            return fr.bosketball.model.PlayerStatus.LOBBY;
        }
    }

    /** Label column of the card: wide enough for "Playtime". */
    private static final int LABEL_PX = 62;
    /** First value column: wide enough for {@code 128 W · 54 L · 70%}. */
    private static final int VALUE_PX = 120;
    /** Second label column ("Rank", "XP"...). */
    private static final int SUB_LABEL_PX = 40;
    /** Right-hand value column: from the sub-label to the card's right edge. */
    private static final int RIGHT_VALUE_PX = CARD_PX - LABEL_PX - VALUE_PX - SUB_LABEL_PX;
    /** The dim dot between two pieces of information. */
    private static final String DOT = " <#64748B>\u00b7</#64748B> ";
    /** Below this, fitting a label would leave a single ellipsis: leave the text alone. */
    private static final int TAB_MIN_LABEL_PX = 30;

    /**
     * Round 36 (kept in Round 40) - keeps a header line inside the card.
     *
     * <p>Party team names go up to 16 characters and the score next to them is unbounded, so the
     * centred header lines can grow wider than the card and spill out on both sides. Anything that
     * does not fit is ellipsised instead.
     */
    private static String fitLine(String miniMessage, int fixedPx, boolean bold) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return "";
        }
        String plain = fr.bosketball.util.MiniSyntax.stripTags(miniMessage);
        int budget = Math.max(TAB_MIN_LABEL_PX, CARD_PX - Math.max(0, fixedPx));
        String fitted = Glyphs.fitTextToWidth(plain, budget, bold);
        return fitted.equals(plain) ? miniMessage : fitted;
    }

    // -------------------------------------------------------------------------------------
    // The card: measuring, padding and the profile block itself. All of it is pure so the
    // whole layout is asserted headlessly in the JUnit suite.
    // -------------------------------------------------------------------------------------

    /**
     * Advance, in font pixels, of a finished card line.
     *
     * <p>Measured on exactly what the client receives: tags stripped and uppercase runs
     * small-capped, because {@link #parseMultiline(String)} small-caps the labels before sending.
     */
    public static int cardWidthPx(String miniMessage) {
        String plain = Glyphs.smallCapsLabels(
            fr.bosketball.util.MiniSyntax.stripTags(miniMessage == null ? "" : miniMessage));
        int total = 0;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            // Padding glyphs carry their own signed advance and must never fall back to 6 px.
            int shift = Glyphs.shiftAdvancePx(c);
            if (shift != 0) {
                total += shift;
            } else {
                total += Glyphs.textAdvancePx(String.valueOf(c), false);
            }
        }
        return total;
    }

    /** Appends invisible shift glyphs so the line is exactly {@code targetPx} wide. */
    private static String padTo(String miniMessage, int targetPx) {
        int missing = targetPx - cardWidthPx(miniMessage);
        if (missing <= 0) {
            return miniMessage;
        }
        return miniMessage + "<!shadow><#F8FAFC>" + Glyphs.shift(missing) + "</#F8FAFC></!shadow>";
    }

    /** Pads a shorter line on both sides so it stays centred inside the card. */
    private static String centre(String miniMessage) {
        int missing = CARD_PX - cardWidthPx(miniMessage);
        if (missing <= 0) {
            return miniMessage;
        }
        int left = missing / 2;
        return "<!shadow><#F8FAFC>" + Glyphs.shift(left) + "</#F8FAFC></!shadow>" + miniMessage
            + padTo("", missing - left);
    }

    /**
     * A slim, centred divider between two sections of the card.
     *
     * <p>Round 43 — the star in the middle is gone. Every star of the plugin (in the card, the
     * sidebar banners, the chat announcements) has been replaced by a plainer mark: a squared-off
     * ornament read as generated art to the owner. The rule is now a dark line with a short warm
     * segment in its centre, which does the same job without a glyph borrowed from a font.
     */
    public static String cardRule() {
        return centre("<#64748B>─────────</#64748B><" + Palette.GOLD + ">──────</" + Palette.GOLD + "><#64748B>─────────</#64748B>");
    }

    private static String labelCell(String label, int widthPx) {
        return padTo("<" + Palette.DIM + ">" + label + "</" + Palette.DIM + "> ", widthPx);
    }

    private static String valueCell(String value, int widthPx) {
        return padTo(value + " ", widthPx);
    }

    /**
     * {@code Label  value} on the left and, when given, a second {@code Label  value} on the right.
     *
     * <p>Round 43 — every cell is now a fixed width and every value is ellipsised to it. Before, a
     * record like {@code 128 W · 54 L · 70%} was wider than the value cell (92 px) and simply pushed
     * the right-hand column out: that row ended up wider than the card, so the client re-centred it
     * and it sat a few pixels off all the others. The two columns are aligned on every row and the
     * line is always exactly {@link #CARD_PX} wide.
     */
    private static String row(String leftLabel, String leftValue, String rightLabel, String rightValue) {
        StringBuilder line = new StringBuilder();
        line.append(labelCell(leftLabel, LABEL_PX));
        line.append(valueCell(fitToCell(leftValue, VALUE_PX), VALUE_PX));
        if (rightLabel != null && rightValue != null) {
            line.append(labelCell(rightLabel, SUB_LABEL_PX));
            line.append(rightCell(rightValue, RIGHT_VALUE_PX));
        }
        return padTo(line.toString(), CARD_PX);
    }

    /**
     * A value cell whose text hugs the right edge.
     *
     * <p>The card is a two-column table and the numbers of the right column (a rank, an XP total,
     * a streak) have wildly different widths. Left-aligning them left the right edge ragged on every
     * row; right-aligning them makes the block read as a column even when a player's numbers grow.
     */
    private static String rightCell(String value, int cellPx) {
        String fitted = fitToCell(value, cellPx);
        String body = fitted + " ";
        int free = cellPx - cardWidthPx(body);
        if (free > 0) {
            body = "<!shadow><#F8FAFC>" + Glyphs.shift(free) + "</#F8FAFC></!shadow>" + body;
        }
        return padTo(body, cellPx);
    }

    /** Ellipsises a MiniMessage value that would not fit its cell, so the columns never move. */
    private static String fitToCell(String value, int cellPx) {
        if (value == null || value.isEmpty() || cardWidthPx(value + " ") <= cellPx) {
            return value;
        }
        String plain = fr.bosketball.util.MiniSyntax.stripTags(value);
        return "<" + Palette.MUTED + ">" + Glyphs.fitTextToWidth(plain, Math.max(8, cellPx - 4), false) + "</" + Palette.MUTED + ">";
    }

    /**
     * {@code Label  value} where the value owns the whole remaining width. Extra parts are dropped
     * from the end until the line fits, so a decorated player can never push the card out of shape.
     */
    private static String wideRow(String label, java.util.List<String> parts) {
        java.util.List<String> kept = new java.util.ArrayList<>(parts);
        String value = String.join(DOT, kept);
        while (kept.size() > 1 && cardWidthPx(labelCell(label, LABEL_PX) + value) > CARD_PX) {
            kept.remove(kept.size() - 1);
            value = String.join(DOT, kept);
        }
        if (cardWidthPx(labelCell(label, LABEL_PX) + value) > CARD_PX) {
            // One long value (a 16-character guild name with its tag, say) still has to fit: the
            // plain text is ellipsised rather than allowed to push the card out of shape.
            String plain = fr.bosketball.util.MiniSyntax.stripTags(value);
            int budget = Math.max(TAB_MIN_LABEL_PX, CARD_PX - LABEL_PX);
            value = "<" + Palette.MUTED + ">" + Glyphs.fitTextToWidth(plain, budget, false) + "</" + Palette.MUTED + ">";
        }
        return padTo(labelCell(label, LABEL_PX) + value, CARD_PX);
    }

    private static String wideRow(String label, String value) {
        return wideRow(label, java.util.List.of(value));
    }

    private static String thousands(int value) {
        return Msg.count(value);
    }

    /** The live counters line under the wordmark (English, like the rest of the HUD). */
    public static String headerCounters(int online, int inMatch, int inQueue) {
        return "<" + Palette.SUCCESS + ">\u25cf</" + Palette.SUCCESS + "> <#F8FAFC>" + online + "</#F8FAFC> <" + Palette.MUTED + ">online</" + Palette.MUTED + ">"
            + DOT + "<" + Palette.GOLD + ">\u25cf</" + Palette.GOLD + "> <#F8FAFC>" + inMatch + "</#F8FAFC> <" + Palette.MUTED + ">in match</" + Palette.MUTED + ">"
            + DOT + "<" + Palette.ICE + ">\u25cf</" + Palette.ICE + "> <#F8FAFC>" + inQueue + "</#F8FAFC> <" + Palette.MUTED + ">in queue</" + Palette.MUTED + ">";
    }

    /**
     * Round 40 — the profile card the footer is built from.
     *
     * <p>Everything that is not already on the sidebar: record and win rate, win streak, career
     * totals, guild, leaderboard podiums, friends, unread mail, ping. Plus — because the owner asked
     * for them by name — coins, rank and playtime. Round 42: the whole card is back in English, so
     * the tab matches the sidebar and the rest of the HUD.
     *
     * <p>Pure on purpose: no {@code Player}, no Bukkit, so the whole block is unit-tested.
     */
    public static java.util.List<String> cardLines(
        PlayerProfile profile,
        StatsManager.LeaderboardBadges badges,
        fr.bosketball.social.Guild guild,
        int pingMillis,
        String domain
    ) {
        java.util.List<String> lines = new java.util.ArrayList<>(11);
        Rank rank = profile.effectiveRank();

        lines.add(cardRule());
        lines.add(row("Coins", "<" + Palette.GOLD_SOFT + ">" + thousands(profile.coins()) + "</" + Palette.GOLD_SOFT + ">",
            "Rank", "<!shadow><#F8FAFC>" + rank.pillGlyph() + "</#F8FAFC></!shadow> <#F8FAFC>"
                + rank.plainDisplayName() + "</#F8FAFC>"));
        lines.add(row("Playtime", "<" + Palette.ICE + ">" + profile.formattedPlayTime() + "</" + Palette.ICE + ">",
            "XP", "<" + Palette.VIOLET + ">" + thousands(profile.xp()) + "</" + Palette.VIOLET + ">"));

        lines.add(cardRule());
        lines.add(row("Record",
            "<" + Palette.SUCCESS + ">" + profile.wins() + " W</" + Palette.SUCCESS + ">" + DOT
                + "<" + Palette.DANGER + ">" + profile.losses() + " L</" + Palette.DANGER + ">" + DOT
                + "<#F8FAFC>" + Math.round(profile.winRatePercent()) + "%</#F8FAFC>",
            "Streak",
            "<" + Palette.GOLD + ">" + profile.winStreak() + "</" + Palette.GOLD + "> <" + Palette.DIM + ">(top "
                + profile.bestWinStreak() + ")</" + Palette.DIM + ">"));
        // Round 43 — three labelled rows instead of one labelled row and one orphan line. The
        // second line used to have an empty label, so it started in the label column while every
        // value of the card started in the value column: the card looked broken on screen.
        lines.add(wideRow("Career", java.util.List.of(
            "<#F8FAFC>" + thousands(profile.points()) + "</#F8FAFC> <" + Palette.MUTED + ">PTS</" + Palette.MUTED + ">",
            "<#F8FAFC>" + profile.dunks() + "</#F8FAFC> <" + Palette.MUTED + ">DUNKS</" + Palette.MUTED + ">",
            "<#F8FAFC>" + profile.assists() + "</#F8FAFC> <" + Palette.MUTED + ">ASSISTS</" + Palette.MUTED + ">")));
        lines.add(wideRow("Shooting", java.util.List.of(
            "<#F8FAFC>" + profile.threePointers() + "</#F8FAFC> <" + Palette.MUTED + ">3PTS</" + Palette.MUTED + ">",
            "<#F8FAFC>" + Math.round(profile.fieldGoalPercentage()) + "%</#F8FAFC> <" + Palette.MUTED + ">FG</" + Palette.MUTED + ">")));
        lines.add(wideRow("Defense", java.util.List.of(
            "<#F8FAFC>" + profile.steals() + "</#F8FAFC> <" + Palette.MUTED + ">STEALS</" + Palette.MUTED + ">",
            "<#F8FAFC>" + profile.blocks() + "</#F8FAFC> <" + Palette.MUTED + ">BLOCKS</" + Palette.MUTED + ">")));

        lines.add(cardRule());
        lines.add(wideRow("Guild", guildLine(guild)));
        lines.add(wideRow("Badges", badgeParts(badges, profile)));
        lines.add(centre(pingLine(pingMillis, domain)));
        return lines;
    }

    /** {@code ping 38ms · bosketball.net · /stats} — the domain is fitted, never allowed to spill. */
    private static String pingLine(int pingMillis, String domain) {
        String tail = DOT + "<" + Palette.ICE + ">/stats</" + Palette.ICE + ">";
        String head = "<" + Palette.MUTED + ">ping</" + Palette.MUTED + "> <#F8FAFC>" + pingMillis + "ms</#F8FAFC>";
        String host = "";
        if (domain != null && !domain.isBlank()) {
            int budget = Math.max(24, CARD_PX - cardWidthPx(head) - cardWidthPx(tail) - cardWidthPx(DOT));
            String fitted = Glyphs.fitTextToWidth(domain, budget, false);
            host = DOT + "<#F8FAFC>" + fitted + "</#F8FAFC>";
        }
        return head + host + tail;
    }

    private static String guildLine(fr.bosketball.social.Guild guild) {
        if (guild == null) {
            return "<" + Palette.DIM + ">none</" + Palette.DIM + ">" + DOT + "<" + Palette.ICE + ">/guild create</" + Palette.ICE + ">";
        }
        String name = guild.displayName() == null ? "Guild" : guild.displayName();
        String tag = guild.displayTag() == null || guild.displayTag().isBlank() ? "" : " " + guild.displayTag();
        return "<#F8FAFC>" + name + tag + "</#F8FAFC>" + DOT + "<" + Palette.GOLD + ">" + thousands(guild.elo())
            + "</" + Palette.GOLD + "> <" + Palette.MUTED + ">ELO</" + Palette.MUTED + ">";
    }

    private static java.util.List<String> badgeParts(
        StatsManager.LeaderboardBadges badges,
        PlayerProfile profile
    ) {
        java.util.List<String> parts = new java.util.ArrayList<>(4);
        if (badges != null) {
            addPlace(parts, badges.eloPlace(), "ELO");
            addPlace(parts, badges.winsPlace(), "WINS");
            addPlace(parts, badges.mvpPlace(), "MVP");
            addPlace(parts, badges.pointsPlace(), "PTS");
            addPlace(parts, badges.dunksPlace(), "DUNKS");
            addPlace(parts, badges.assistsPlace(), "ASSISTS");
        }
        if (parts.isEmpty()) {
            parts.add("<" + Palette.DIM + ">no top 3</" + Palette.DIM + ">");
        }
        parts.add("<#F8FAFC>" + profile.friends().size() + "</#F8FAFC> <" + Palette.MUTED + ">FRIENDS</" + Palette.MUTED + ">");
        if (profile.unreadMailCount() > 0) {
            parts.add("<" + Palette.GOLD + ">" + profile.unreadMailCount() + "</" + Palette.GOLD + "> <" + Palette.MUTED + ">MAIL</" + Palette.MUTED + ">");
        }
        return parts;
    }

    private static void addPlace(java.util.List<String> parts, int place, String label) {
        if (place >= 1 && place <= 3) {
            parts.add("<" + Palette.GOLD + ">#" + place + "</" + Palette.GOLD + "> <" + Palette.MUTED + ">" + label + "</" + Palette.MUTED + ">");
        }
    }

    public void updateAll(GameManager gameManager) {
        int online = Bukkit.getOnlinePlayers().size();
        int inMatch = gameManager.activePlayerCount();
        int inQueue = gameManager.queuedCount();

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID pid = p.getUniqueId();
            PlayerProfile prof = statsManager.getOrCreate(p);
            MatchSession mySession = gameManager.getSessionOf(pid);

            int pingDisplay = p.getPing();
            int pingBucket = (pingDisplay / 5) * 5;
            Rank effRank = prof.effectiveRank();
            fr.bosketball.model.PlayerStatus presence = statusOf(p);

            StatsManager.LeaderboardBadges badges = statsManager.getBadges(pid);
            fr.bosketball.social.Guild guild = guildOf(p);
            java.util.List<String> card = cardLines(prof, badges, guild, pingBucket, serverDomain);

            StringBuilder signature = new StringBuilder(mySession != null ? "M" : "L");
            signature.append('|').append(online).append('|').append(inMatch).append('|').append(inQueue);
            if (mySession != null) {
                signature.append('|').append(mySession.teamDisplayName(TeamSide.IGNIS))
                    .append('|').append(mySession.score(TeamSide.IGNIS))
                    .append('|').append(mySession.score(TeamSide.FROST))
                    .append('|').append(mySession.teamDisplayName(TeamSide.FROST))
                    .append('|').append(mySession.formattedClock());
            }
            for (String cardLine : card) {
                signature.append('|').append(cardLine);
            }
            String hfSig = signature.toString();

            if (!hfSig.equals(lastHeaderFooterSig.get(pid))) {
                lastHeaderFooterSig.put(pid, hfSig);
                // Header: the wordmark, then either the live counters or the match scoreboard.
                TextComponent.Builder hb = Component.text();
                hb.append(STATIC_LOGO_BLOCK);
                if (mySession != null) {
                    // Round 36 - two 16-character party names plus the score and the clock used to be
                    // wider than the panel; each name now gets half of what is left.
                    String headerFixed = mySession.score(TeamSide.IGNIS) + "  \u2014  "
                        + mySession.score(TeamSide.FROST) + "  \u2022  " + mySession.formattedClock() + "   ";
                    int nameBudget = Math.max(TAB_MIN_LABEL_PX,
                        (CARD_PX - Glyphs.textAdvancePx(headerFixed, true)) / 2);
                    String ignisName = Glyphs.fitTextToWidth(
                        mySession.teamDisplayName(TeamSide.IGNIS).toUpperCase(java.util.Locale.ROOT), nameBudget, true);
                    String frostName = Glyphs.fitTextToWidth(
                        mySession.teamDisplayName(TeamSide.FROST).toUpperCase(java.util.Locale.ROOT), nameBudget, true);
                    hb.append(parseMultiline(
                        Msg.gradBold(Palette.DANGER + ":" + Palette.WARN, ignisName + " " + mySession.score(TeamSide.IGNIS))
                            + "  <#64748B>—</#64748B>  "
                            + "<" + Palette.ICE + "><bold>" + mySession.score(TeamSide.FROST) + " " + frostName + "</bold></" + Palette.ICE + ">"
                            + "  <#64748B>•</#64748B>  <#F8FAFC>" + mySession.formattedClock() + "</#F8FAFC>\n"
                    ));
                } else {
                    hb.append(parseMultiline(headerCounters(online, inMatch, inQueue) + "\n"));
                }

                // Footer: the profile card. Every line is padded to exactly CARD_PX, which is what
                // makes the client's per-line centring stack into one left-aligned block.
                TextComponent.Builder fb = Component.text();
                fb.append(Component.newline());
                for (String cardLine : card) {
                    fb.append(parseMultiline(cardLine)).append(Component.newline());
                }
                p.sendPlayerListHeaderAndFooter(hb.asComponent(), fb.asComponent());
            }

            fr.bosketball.model.ChatTag tag = prof.selectedTag();
            boolean tagAllowed = tag != null && tag.canBeUsedBy(prof, p.getName(),
                fr.bosketball.util.Perms.has(p, fr.bosketball.util.Perms.TAGS_ALL),
                fr.bosketball.util.Perms.has(p, fr.bosketball.util.Perms.STAFF));
            String entryKey = (mySession == null ? "L" : ("M:" + mySession.getTeam(pid) + ":" + mySession.getBoxScore(pid).points))
                + "|" + effRank.ordinal() + "|" + badges.eloPlace() + "|" + (tagAllowed ? tag.ordinal() : -1)
                + "|" + p.getName() + "|" + prof.elo() + "|" + presence.ordinal();
            if (!entryKey.equals(lastEntrySig.get(pid))) {
                lastEntrySig.put(pid, entryKey);
                p.playerListName(buildTabEntry(p, prof, mySession, badges, tag, tagAllowed));
            }
        }
        enforceTabOrder(gameManager);
    }

    /** Applies that total order to the player list (one packet per row that actually moved). */
    private void enforceTabOrder(GameManager gameManager) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        if (online.size() > 1) {
            // Round 36 — getSessionOf() falls back to a linear scan of every court when the player
            // is not indexed, and the comparator called it twice per comparison (~1 300 lookups for
            // 100 players). One lookup per player is enough: sort with the snapshot.
            Map<UUID, MatchSession> sessionOf = new java.util.HashMap<>(online.size() * 2);
            for (Player p : online) {
                sessionOf.put(p.getUniqueId(), gameManager.getSessionOf(p.getUniqueId()));
            }
            online.sort(tabComparator(sessionOf));
        }
        for (int i = 0; i < online.size(); i++) {
            Player p = online.get(i);
            Integer previous = lastListOrder.get(p.getUniqueId());
            if (previous != null && previous == i) {
                continue;
            }
            lastListOrder.put(p.getUniqueId(), i);
            p.setPlayerListOrder(i);
        }
    }

    /** Total order: no tie is ever possible, so the list can never oscillate between two layouts. */
    private Comparator<Player> tabComparator(Map<UUID, MatchSession> sessionOf) {
        return (a, b) -> {
            MatchSession sa = sessionOf.get(a.getUniqueId());
            MatchSession sb = sessionOf.get(b.getUniqueId());
            int rankA = sa == null ? 2 : teamRank(sa.getTeam(a.getUniqueId()));
            int rankB = sb == null ? 2 : teamRank(sb.getTeam(b.getUniqueId()));
            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }
            if (sa != null && sb != null) {
                int ptsA = sa.getBoxScore(a.getUniqueId()).points;
                int ptsB = sb.getBoxScore(b.getUniqueId()).points;
                if (ptsA != ptsB) {
                    return Integer.compare(ptsB, ptsA);
                }
            }
            int byName = a.getName().compareToIgnoreCase(b.getName());
            if (byName != 0) {
                return byName;
            }
            return a.getUniqueId().toString().compareTo(b.getUniqueId().toString());
        };
    }

    private static int teamRank(TeamSide side) {
        if (side == TeamSide.IGNIS) {
            return 0;
        }
        if (side == TeamSide.FROST) {
            return 1;
        }
        return 2;
    }

    private Component buildTabEntry(
        Player player,
        PlayerProfile profile,
        MatchSession session,
        StatsManager.LeaderboardBadges badges,
        fr.bosketball.model.ChatTag tag,
        boolean tagAllowed
    ) {
        TextComponent.Builder row = Component.text();
        String gap = Glyphs.shift(2);

        if (session != null) {
            TeamSide side = session.getTeam(player.getUniqueId());
            if (side != TeamSide.SPECTATOR) {
                row.append(side.pillComponent()).append(Component.text(gap, Glyphs.BITMAP_STYLE));
            }
            if (badges.eloPlace() >= 1 && badges.eloPlace() <= 3) {
                row.append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, badges.eloPlace()))
                    .append(Component.text(gap, Glyphs.BITMAP_STYLE));
            }
            if (tagAllowed) {
                row.append(tag.pillComponent()).append(Component.text(gap, Glyphs.BITMAP_STYLE));
            }
            if (side == TeamSide.SPECTATOR) {
                row.append(Component.text(" " + player.getName(), TextColor.color(0x94A3B8)));
                row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
                row.append(Component.text(" SPECTATING", TextColor.color(0x38BDF8)));
                return row.asComponent();
            }
            int matchPts = session.getBoxScore(player.getUniqueId()).points;
            row.append(Component.text(" " + player.getName(), side.textColor()));
            row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            row.append(Component.text(matchPts + " PTS", Style.style(TextColor.color(0xFDE047), TextDecoration.BOLD)));
            return row.asComponent();
        }

        Rank rank = profile.effectiveRank();
        row.append(rank.pillComponent());

        if (badges.eloPlace() >= 1 && badges.eloPlace() <= 3) {
            row.append(Component.text(gap, Glyphs.BITMAP_STYLE))
                .append(Glyphs.topBadgePill(Glyphs.TopBadgeKind.ELO, badges.eloPlace()));
        }
        if (tagAllowed) {
            row.append(Component.text(gap, Glyphs.BITMAP_STYLE))
                .append(tag.pillComponent());
        }

        fr.bosketball.social.Guild guild = guildOf(player);
        Component nameComponent = Component.text(" " + player.getName(), TextColor.color(0xF1F5F9))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Msg.parse(
                "<" + Palette.WARN + "><bold>" + player.getName() + "</bold></" + Palette.WARN + ">\n"
                    + "<#64748B>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</#64748B>\n"
                    + "<#94A3B8>Guild:</#94A3B8> " + (guild == null
                        ? "<#64748B>None</#64748B>"
                        : guild.displayName() + " " + guild.displayTag()
                            + " <#64748B>(" + guild.elo() + " ELO)</#64748B>") + "\n"
                    + "<#94A3B8>Rank:</#94A3B8> <#F8FAFC>" + rank.plainDisplayName() + "</#F8FAFC>\n"
                    + "<#94A3B8>ELO:</#94A3B8> <" + Palette.GOLD + ">" + profile.elo() + "</" + Palette.GOLD + ">\n"
                    + "<#94A3B8>Record:</#94A3B8> <" + Palette.SUCCESS + ">" + profile.wins() + "W</" + Palette.SUCCESS + ">"
                    + "<#94A3B8>/</#94A3B8><" + Palette.DANGER + ">" + profile.losses() + "L</" + Palette.DANGER + ">\n"
                    + "<" + Palette.ICE + ">▸ Click to challenge</" + Palette.ICE + ">")));
        row.append(nameComponent);
        row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        row.append(Component.text(profile.elo() + " ELO", TextColor.color(0xFBBF24)));

        boolean showStatus = true;
        var prefProfile = statsManager.getIfPresent(player.getUniqueId());
        if (prefProfile != null && prefProfile.settings() != null) {
            showStatus = prefProfile.settings().showStatusInTab();
        }
        fr.bosketball.model.PlayerStatus presence = showStatus
            ? statusOf(player)
            : fr.bosketball.model.PlayerStatus.LOBBY;
        if (presence != fr.bosketball.model.PlayerStatus.LOBBY) {
            row.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
            row.append(Component.text(presence.symbol() + " " + presence.smallCapsTitle(),
                TextColor.color(presence.colorRgb())));
        }
        return row.asComponent();
    }
}
