package fr.bosketball.ui;

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
    /** First value column. */
    private static final int VALUE_PX = 92;
    /** Second label column ("Rank", "XP"...). */
    private static final int SUB_LABEL_PX = 40;
    /** The dim dot between two pieces of information. */
    private static final String DOT = " <dark_gray>\u00b7</dark_gray> ";
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
        return miniMessage + "<!shadow><white>" + Glyphs.shift(missing) + "</white></!shadow>";
    }

    /** Pads a shorter line on both sides so it stays centred inside the card. */
    private static String centre(String miniMessage) {
        int missing = CARD_PX - cardWidthPx(miniMessage);
        if (missing <= 0) {
            return miniMessage;
        }
        int left = missing / 2;
        return "<!shadow><white>" + Glyphs.shift(left) + "</white></!shadow>" + miniMessage
            + padTo("", missing - left);
    }

    /** A slim, centred divider between two sections of the card. */
    public static String cardRule() {
        return centre("<dark_gray>──────────</dark_gray> <#FBBF24>\u2726</#FBBF24> <dark_gray>──────────</dark_gray>");
    }

    private static String labelCell(String label, int widthPx) {
        return padTo("<#64748B>" + label + "</#64748B> ", widthPx);
    }

    private static String valueCell(String value, int widthPx) {
        return padTo(value + " ", widthPx);
    }

    /** {@code Label  value} on the left and, when given, a second {@code Label  value} on the right. */
    private static String row(String leftLabel, String leftValue, String rightLabel, String rightValue) {
        StringBuilder line = new StringBuilder();
        line.append(labelCell(leftLabel, LABEL_PX)).append(valueCell(leftValue, VALUE_PX));
        if (rightLabel != null) {
            line.append(labelCell(rightLabel, SUB_LABEL_PX)).append(rightValue).append(' ');
        }
        return padTo(line.toString(), CARD_PX);
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
            value = "<#94A3B8>" + Glyphs.fitTextToWidth(plain, budget, false) + "</#94A3B8>";
        }
        return padTo(labelCell(label, LABEL_PX) + value, CARD_PX);
    }

    private static String wideRow(String label, String value) {
        return wideRow(label, java.util.List.of(value));
    }

    private static String thousands(int value) {
        return String.format(java.util.Locale.US, "%,d", value);
    }

    /** The live counters line under the wordmark (English, like the rest of the HUD). */
    public static String headerCounters(int online, int inMatch, int inQueue) {
        return "<#4ADE80>\u25cf</#4ADE80> <white>" + online + "</white> <#94A3B8>online</#94A3B8>"
            + DOT + "<#FBBF24>\u25cf</#FBBF24> <white>" + inMatch + "</white> <#94A3B8>in match</#94A3B8>"
            + DOT + "<#38BDF8>\u25cf</#38BDF8> <white>" + inQueue + "</white> <#94A3B8>in queue</#94A3B8>";
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
        lines.add(row("Coins", "<#FDE047>" + thousands(profile.coins()) + "</#FDE047>",
            "Rank", "<!shadow><white>" + rank.pillGlyph() + "</white></!shadow> <white>"
                + rank.plainDisplayName() + "</white>"));
        lines.add(row("Playtime", "<#38BDF8>" + profile.formattedPlayTime() + "</#38BDF8>",
            "XP", "<#A855F7>" + thousands(profile.xp()) + "</#A855F7>"));

        lines.add(cardRule());
        lines.add(row("Record",
            "<#4ADE80>" + profile.wins() + " V</#4ADE80>" + DOT
                + "<#F87171>" + profile.losses() + " D</#F87171>" + DOT
                + "<white>" + Math.round(profile.winRatePercent()) + "%</white>",
            "Streak",
            "<#FBBF24>" + profile.winStreak() + "</#FBBF24> <#64748B>(top "
                + profile.bestWinStreak() + ")</#64748B>"));
        lines.add(wideRow("Career", java.util.List.of(
            "<white>" + thousands(profile.points()) + "</white> <#94A3B8>PTS</#94A3B8>",
            "<white>" + profile.dunks() + "</white> <#94A3B8>DUNKS</#94A3B8>",
            "<white>" + profile.assists() + "</white> <#94A3B8>ASSISTS</#94A3B8>")));
        lines.add(wideRow("", java.util.List.of(
            "<white>" + profile.steals() + "</white> <#94A3B8>STEALS</#94A3B8>",
            "<white>" + profile.blocks() + "</white> <#94A3B8>BLOCKS</#94A3B8>",
            "<white>" + profile.threePointers() + "</white> <#94A3B8>3PTS</#94A3B8>",
            "<white>" + Math.round(profile.fieldGoalPercentage()) + "%</white> <#94A3B8>FG</#94A3B8>")));

        lines.add(cardRule());
        lines.add(wideRow("Guild", guildLine(guild)));
        lines.add(wideRow("Badges", badgeParts(badges, profile)));
        lines.add(centre(pingLine(pingMillis, domain)));
        return lines;
    }

    /** {@code ping 38ms · bosketball.net · /stats} — the domain is fitted, never allowed to spill. */
    private static String pingLine(int pingMillis, String domain) {
        String tail = DOT + "<#818CF8>/stats</#818CF8>";
        String head = "<#94A3B8>ping</#94A3B8> <white>" + pingMillis + "ms</white>";
        String host = "";
        if (domain != null && !domain.isBlank()) {
            int budget = Math.max(24, CARD_PX - cardWidthPx(head) - cardWidthPx(tail) - cardWidthPx(DOT));
            String fitted = Glyphs.fitTextToWidth(domain, budget, false);
            host = DOT + "<white>" + fitted + "</white>";
        }
        return head + host + tail;
    }

    private static String guildLine(fr.bosketball.social.Guild guild) {
        if (guild == null) {
            return "<#64748B>none</#64748B>" + DOT + "<#818CF8>/guild create</#818CF8>";
        }
        String name = guild.displayName() == null ? "Guild" : guild.displayName();
        String tag = guild.displayTag() == null || guild.displayTag().isBlank() ? "" : " " + guild.displayTag();
        return "<white>" + name + tag + "</white>" + DOT + "<#FBBF24>" + thousands(guild.elo())
            + "</#FBBF24> <#94A3B8>ELO</#94A3B8>";
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
            parts.add("<#64748B>no top 3</#64748B>");
        }
        parts.add("<white>" + profile.friends().size() + "</white> <#94A3B8>FRIENDS</#94A3B8>");
        if (profile.unreadMailCount() > 0) {
            parts.add("<#FBBF24>" + profile.unreadMailCount() + "</#FBBF24> <#94A3B8>MAIL</#94A3B8>");
        }
        return parts;
    }

    private static void addPlace(java.util.List<String> parts, int place, String label) {
        if (place >= 1 && place <= 3) {
            parts.add("<#FBBF24>#" + place + "</#FBBF24> <#94A3B8>" + label + "</#94A3B8>");
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
                        Msg.gradBold("#FF6B4A:#FFB088", ignisName + " " + mySession.score(TeamSide.IGNIS))
                            + "  <dark_gray>—</dark_gray>  "
                            + "<#38BDF8><bold>" + mySession.score(TeamSide.FROST) + " " + frostName + "</bold></#38BDF8>"
                            + "  <dark_gray>•</dark_gray>  <white>" + mySession.formattedClock() + "</white>\n"
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
                row.append(Component.text("👁 SPECTATING", TextColor.color(0x38BDF8)));
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
                "<#FF8C1A><bold>" + player.getName() + "</bold></#FF8C1A>\n"
                    + "<dark_gray>⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯</dark_gray>\n"
                    + "<gray>Guild:</gray> " + (guild == null
                        ? "<dark_gray>None</dark_gray>"
                        : guild.displayName() + " " + guild.displayTag()
                            + " <dark_gray>(" + guild.elo() + " ELO)</dark_gray>") + "\n"
                    + "<gray>Rank:</gray> <white>" + rank.plainDisplayName() + "</white>\n"
                    + "<gray>ELO:</gray> <#FBBF24>" + profile.elo() + "</#FBBF24>\n"
                    + "<gray>Record:</gray> <#4ADE80>" + profile.wins() + "W</#4ADE80>"
                    + "<gray>/</gray><#F87171>" + profile.losses() + "L</#F87171>\n"
                    + "<#38BDF8>▸ Click to challenge</#38BDF8>")));
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
