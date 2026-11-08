package fr.bosketball.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    /** Lenient twin: unknown tags are kept as text instead of throwing, the repair pass handles the rest. */
    private static final MiniMessage LENIENT = MiniMessage.builder().strict(false).build();
    private static final Map<String, Component> PARSE_CACHE = new ConcurrentHashMap<>();
    private static final int PARSE_CACHE_LIMIT = 4096;
    private static final int PARSE_CACHE_MAX_LENGTH = 192;

    public static final String PREFIX = "<!shadow><#F8FAFC>" + Glyphs.SYS_SERVER + "</#F8FAFC></!shadow> ";

    private Msg() {
    }

    /** Brand gradients used across titles, boss bars and cards (MiniMessage {@code <gradient:…>}). */
    public static final String GRAD_BRAND = Palette.WARN + ":" + Palette.GOLD_SOFT;

    public static final String GRAD_GOLD = Palette.GOLD_SOFT + ":" + Palette.AMBER;

    public static final String GRAD_MINT = Palette.SUCCESS + ":" + Palette.ICE;

    public static final String GRAD_ICE = Palette.ICE + ":" + Palette.ICE;

    public static final String GRAD_SUNSET = Palette.WARN + ":" + Palette.VIOLET;

    /** Wraps {@code text} in a two-stop gradient, e.g. {@code grad(GRAD_BRAND, "BOSKETBALL")}. */
    public static String grad(String stops, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return "<gradient:" + stops + ">" + text + "</gradient>";
    }

    /** A gradient that also closes with a trailing reset, handy for inline segments. */
    public static String gradBold(String stops, String text) {
        return grad(stops, "<bold>" + text + "</bold>");
    }

    /**
     * Closing twin of a MiniMessage opening tag, e.g. {@code "<#38BDF8>" -> "</#38BDF8>"} or
     * {@code "<#F87171>" -> "</#F87171>"}.
     *
     * <p>Never build this by hand with {@code "</" + open.substring(1) + ">"}: that yields
     * {@code </#38BDF8>>} and prints a stray {@code >} in game.
     */
    public static String closeTag(String openTag) {
        if (openTag == null || openTag.length() < 3 || openTag.charAt(0) != '<') {
            return "";
        }
        String body = openTag.substring(1);
        if (body.endsWith(">")) {
            body = body.substring(0, body.length() - 1);
        }
        return "</" + body + ">";
    }

    public static String pillTag(char pillGlyph) {
        return "<!shadow><#F8FAFC>" + pillGlyph + "</#F8FAFC></!shadow>";
    }

    public static String escapeMini(String raw) {
        if (raw == null) {
            return "";
        }
        return MM.escapeTags(raw);
    }

    public static Component parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) {
            return Component.empty();
        }
        String source = MiniSyntax.repair(miniMessage);
        Component cached = PARSE_CACHE.get(source);
        if (cached != null) {
            return cached;
        }
        Component parsed;
        try {
            parsed = LENIENT.deserialize(source);
        } catch (RuntimeException | StackOverflowError failure) {
            // Last resort: never let markup reach the client, never let a message disappear.
            parsed = Component.text(MiniSyntax.stripTags(source));
        }
        if (source.length() <= PARSE_CACHE_MAX_LENGTH) {
            if (PARSE_CACHE.size() >= PARSE_CACHE_LIMIT) {
                PARSE_CACHE.clear();
            }
            PARSE_CACHE.put(source, parsed);
        }
        return parsed;
    }

    public static Component prefixed(String miniMessage) {
        return parse(PREFIX + miniMessage);
    }

    public static int parseCacheSize() {
        return PARSE_CACHE.size();
    }

    public static int parseCacheLimit() {
        return PARSE_CACHE_LIMIT;
    }

    public static void send(Audience target, String miniMessage) {

        if (target == null || miniMessage == null) {
            return;
        }
        target.sendMessage(prefixed(miniMessage));
    }

    public static void sendCourt(Audience target, String miniMessage) {
        target.sendMessage(parse(pillTag(Glyphs.SYS_COURT) + " " + miniMessage));
    }

    public static void sendSocial(Audience target, String miniMessage) {
        target.sendMessage(parse(pillTag(Glyphs.SYS_SOCIAL) + " " + miniMessage));
    }

    public static void sendAlert(Audience target, String miniMessage) {
        target.sendMessage(parse(pillTag(Glyphs.SYS_ALERT) + " " + miniMessage));
    }

    public static String info(String body) {
        return "<" + Palette.MUTED + ">" + body + "</" + Palette.MUTED + ">";
    }

    public static String success(String body) {
        return "<" + Palette.SUCCESS + ">✔ </" + Palette.SUCCESS + "><" + Palette.TEXT + ">" + body + "</" + Palette.TEXT + ">";
    }

    public static String warn(String body) {
        return "<" + Palette.GOLD + ">⚠ </" + Palette.GOLD + "><" + Palette.TEXT + ">" + body + "</" + Palette.TEXT + ">";
    }

    public static String error(String body) {
        return "<" + Palette.DANGER + ">✖ </" + Palette.DANGER + "><" + Palette.TEXT + ">" + body + "</" + Palette.TEXT + ">";
    }

    /**
     * A count the way a scoreboard writes it: {@code 12,480}, never {@code 12480}.
     *
     * <p>Round 48. Every number in the plugin was printed raw, so a rich player's balance read
     * {@code 12480} and the eye had to count digits to know whether it was twelve thousand or a
     * hundred and twenty-four thousand. Thousands separators are one character of punctuation that
     * do a lot of work, and no hand-written UI leaves them out.
     */
    public static String count(int value) {
        return String.format(java.util.Locale.US, "%,d", value);
    }

    public static String count(long value) {
        return String.format(java.util.Locale.US, "%,d", value);
    }

    public static String coinsBadge(int amount) {
        return "<!shadow><#F8FAFC>" + Glyphs.CHAT_ICON_COIN + "</#F8FAFC></!shadow> <" + Palette.GOLD_SOFT + "><bold>" + count(amount) + "</bold></" + Palette.GOLD_SOFT + "> <" + Palette.GOLD + ">Coins</" + Palette.GOLD + ">";
    }

    public static void clearChatBuffer(Audience target) {
        Component blank = Component.empty();
        for (int i = 0; i < 32; i++) {
            target.sendMessage(blank);
        }
    }

    public static void sendFramedCard(Audience target, String headerTitle, List<String> bodyMiniMessages) {
        target.sendMessage(Glyphs.composeDividerHeader(Glyphs.smallCapsLabels(headerTitle)));
        for (String line : bodyMiniMessages) {
            target.sendMessage(parse(line));
        }
        target.sendMessage(Glyphs.composeDividerBottom());
    }

    /** One glossy dock pill: clickable artwork, tooltip, no bracket, no emoji. */
    /**
     * The navigation dock: the five main destinations as bevelled artwork pills instead of the old
     * bracketed emoji buttons. Pass any of play, locker, shop, ranks, help, discord.
     */
    public static Component navDock(String... actions) {
        TextComponent.Builder row = Component.text().append(Component.text("  "));
        boolean first = true;
        for (String action : actions) {
            if (action == null || action.isBlank()) {
                continue;
            }
            if (!first) {
                row.append(Component.text(" "));
            }
            first = false;
            row.append(navPill(action.toLowerCase(java.util.Locale.ROOT)));
        }
        return row.asComponent();
    }

    private static Component navPill(String action) {
        return switch (action) {
            case "play" -> Glyphs.bitmap(Glyphs.NAV_PLAY)
                .hoverEvent(HoverEvent.showText(parse("<" + Palette.SUCCESS + "><bold>ᴘʟᴀʏ</bold></" + Palette.SUCCESS + ">\n<#94A3B8>Ranked 1v1–5v5, 3PT Contest, Practice</#94A3B8>")))
                .clickEvent(ClickEvent.runCommand("/play"));
            case "locker", "skins", "cosmetics" -> Glyphs.bitmap(Glyphs.NAV_LOCKER)
                .hoverEvent(HoverEvent.showText(parse("<" + Palette.ICE + "><bold>ꜱᴋɪɴꜱ</bold></" + Palette.ICE + ">\n<#94A3B8>Balls, dunks, jerseys & chat tags</#94A3B8>")))
                .clickEvent(ClickEvent.runCommand("/locker"));
            case "shop" -> Glyphs.bitmap(Glyphs.NAV_SHOP)
                .hoverEvent(HoverEvent.showText(parse("<" + Palette.GOLD + "><bold>ꜱʜᴏᴘ</bold></" + Palette.GOLD + ">\n<#94A3B8>Basketballs, dunks and chat tags</#94A3B8>")))
                .clickEvent(ClickEvent.runCommand("/shop"));
            case "ranks" -> Glyphs.bitmap(Glyphs.NAV_RANKS)
                .hoverEvent(HoverEvent.showText(parse("<" + Palette.GOLD + "><bold>ʀᴀɴᴋꜱ</bold></" + Palette.GOLD + ">\n<#94A3B8>ELO ladder, badges and court controls</#94A3B8>")))
                .clickEvent(ClickEvent.runCommand("/ranks"));
            case "tuto" -> Glyphs.bitmap(Glyphs.NAV_HELP)
                .hoverEvent(HoverEvent.showText(parse("<" + Palette.MUTED + "><bold>ɢᴜɪᴅᴇ</bold></" + Palette.MUTED + ">\n<#94A3B8>New player tutorial & server guide</#94A3B8>")))
                .clickEvent(ClickEvent.runCommand("/tuto"));
            case "discord" -> Glyphs.bitmap(Glyphs.NAV_DISCORD)
                .hoverEvent(HoverEvent.showText(parse("<" + Palette.ICE + "><bold>ᴅɪꜱᴄᴏʀᴅ</bold></" + Palette.ICE + ">\n<#94A3B8>Join the community server</#94A3B8>")))
                .clickEvent(ClickEvent.runCommand("/discord"));
            default -> Glyphs.bitmap(Glyphs.NAV_HELP)
                .hoverEvent(HoverEvent.showText(parse("<" + Palette.MUTED + "><bold>ʜᴇʟᴘ</bold></" + Palette.MUTED + ">\n<#94A3B8>Open the interactive server guide</#94A3B8>")))
                .clickEvent(ClickEvent.runCommand("/help"));
        };
    }

    /**
     * The help card, rewritten in Round 45.
     *
     * <p>It used to be four sections, twelve commands, a wall of English marketing verbs ("Unlock",
     * "Browse", "Challenge") and one colour per command. A player who typed {@code /help} learned
     * that the server has a lot of buttons, not what to do first. It now lists <b>eight</b>
     * commands, in French, in three groups, and the only colour is the gold of the command itself.
     * The rest of the commands still work; they are simply not advertised any more.
     */
    public static void sendInteractiveHelpCard(CommandSender sender) {
        boolean isOp = Perms.admin(sender) || Perms.map(sender) || Perms.build(sender);
        List<String> lines = new ArrayList<>();
        lines.add("  <" + Palette.MUTED + ">Everything works from the 3D hotbar \u2014 or click below.</" + Palette.MUTED + ">");
        lines.add("");

        lines.add("  " + pillTag(Glyphs.SYS_COURT) + " <" + Palette.MUTED + "><bold>ᴘʟᴀʏ</bold></" + Palette.MUTED + ">");
        lines.add(helpLine("/play", "pick a mode or join a queue"));
        lines.add(helpLine("/practice", "instant solo court"));
        lines.add(helpLine("/leave", "leave a match or a queue"));
        lines.add("");

        lines.add("  " + pillTag(Glyphs.SYS_SHOP) + " <" + Palette.MUTED + "><bold>ᴘʀᴏɢʀᴇꜱꜱ</bold></" + Palette.MUTED + ">");
        lines.add(helpLine("/shop", "balls, dunks, chat tags"));
        lines.add(helpLine("/locker", "everything you own"));
        lines.add(helpLine("/stats", "your card: rank, record, career"));
        lines.add("");

        lines.add("  " + pillTag(Glyphs.SYS_SOCIAL) + " <" + Palette.MUTED + "><bold>ᴄᴏᴍᴍᴜɴɪᴛʏ</bold></" + Palette.MUTED + ">");
        lines.add(helpLine("/friend", "friends and invites"));
        lines.add(helpLine("/discord", "the server Discord"));

        if (isOp) {
            lines.add("");
            lines.add("  <" + Palette.DIM + ">Staff:</" + Palette.DIM + "> <" + Palette.MUTED
                + ">/bb admin \u00b7 /bb map \u00b7 /build</" + Palette.MUTED + ">");
        }

        lines.add("");
        sendFramedCard(sender, "BOSKETBALL \u2022 HELP", lines);
        sender.sendMessage(navDock("play", "locker", "shop", "ranks", "discord"));
        sender.sendMessage(Component.empty());
        if (sender instanceof Player p) {
            playPersonalPackSound(p, "ui.click", 0.7f, 1.25f);
        }
    }

    /**
     * A short match notification in the pack's pill, above the hotbar.
     *
     * <p>Round 46: every "Intercepted pass!", "24s shot clock expired", "Ball poked loose" used to
     * be a bare coloured line drifting over the hotbar, each with its own colour and its own icon.
     * They now share one surface — the hand-drawn pill from the resource pack — so the in-match
     * feedback reads as one HUD instead of fourteen unrelated toasts.
     *
     * <p>Round 85: they also share one queue.
     *
     * <p>Round 58 gave toasts a 1.6-second lease and stopped the aim HUD from erasing them. That
     * fixed the symptom: eight subsystems still wrote to the same line directly, so two of them
     * raising a message in the same tick meant the second one silently won, and a subsystem with
     * something more urgent to say had no way to say it. Every action-bar write in the plugin now
     * goes through {@link Hunt}: the surface decides the priority and the minimum display time, the
     * queue decides who gets the line, and a message that arrives while another is being read is
     * merged behind the house separator instead of dropped.
     *
     * <p>{@code rail} keeps its name because two hundred call sites already use it, and its
     * behaviour is unchanged for the common case: a contest message lands in the pill and owns the
     * line for 1.6 s.
     */
    public static void rail(Player player, String miniMessage) {
        rail(player, HudSurface.CONTEST, miniMessage);
    }

    /**
     * Puts a message on a specific surface.
     *
     * <p>The text is measured and fitted before it is queued ({@link HudWidth}), so no caller can
     * widen the HUD by accident, and a message wider than the pack's pill renders as plain text
     * rather than as a pill that runs off the screen.
     */
    public static void rail(Player player, HudSurface surface, String miniMessage) {
        if (player == null || !player.isOnline() || surface == null || miniMessage == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Hunt hunt = HudSurfaceRegistry.hunt(player.getUniqueId());
        hunt.offer(surface, miniMessage, now);
        Hunt.Frame frame = hunt.tick(now);
        if (frame == null) {
            // Another surface owns the line, or this message changed nothing: no packet wasted.
            return;
        }
        player.sendActionBar(composeHudLine(frame.text()));
    }

    /**
     * The live meter of the aim HUD: the compass, the charge bar, the dunk timing window.
     *
     * <p>These are redrawn every tick and are the lowest thing on the HUD, so they live on the
     * {@link HudSurface#SHOT_METER} and {@link HudSurface#AMBIENT} surfaces: they own the line only
     * when nothing else does, they refresh themselves instead of queueing behind themselves, and a
     * toast, a rule decision or a tutorial step wins the line without the meter noticing. This is
     * the replacement for the {@code if (!Msg.railOnScreen(p)) p.sendActionBar(...)} dance that
     * every gameplay tick used to repeat — the queue already knows whether the line is free.
     */
    public static void sendHudMeter(Player player, Component component) {
        if (player == null || !player.isOnline() || component == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Hunt hunt = HudSurfaceRegistry.hunt(player.getUniqueId());
        // Measured through the same pipeline as a toast, so a meter can never widen the HUD either.
        // A meter is a drawing: it is passed through untouched when it fits, and its raw text is
        // handed to the meter hook when it does not — never a half-drawn bar.
        Component safe = HudWidth.fits(component) ? component : HudWidth.fit(component);
        HudSurface surface = HudSurface.SHOT_METER;
        hunt.offerComponent(surface, safe, now);
        Hunt.Frame frame = hunt.tick(now);
        if (frame == null || frame.surface() != surface) {
            // Another surface owns the line: the meter stays queued and is drawn on the next free
            // tick, with the value from this tick, instead of being lost.
            return;
        }
        player.sendActionBar(hunt.component(surface));
    }

    /**
     * Advances one player's queue. Called from the UI tick so a queued message that lost the race in
     * one tick is still delivered in the next, and so the line is released on time.
     */
    public static void pumpActionBar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Hunt hunt = HudSurfaceRegistry.peek(player.getUniqueId());
        if (hunt == null) {
            return;
        }
        Hunt.Frame frame = hunt.tick(System.currentTimeMillis());
        if (frame == null) {
            return;
        }
        player.sendActionBar(composeHudLine(frame.text()));
    }

    /**
     * Wraps a HUD line in the pack's pill when it fits, in plain text when it does not.
     *
     * <p>The pill is 176 px of artwork for at most 156 px of text ({@link HudWidth#MAX_PILL_TEXT_PX}).
     * Measuring the whole markup line once here is what keeps the guard honest: before, the pill was
     * rebuilt from a text advance that ignored bold and shift glyphs, so a bold toast could overflow
     * the artwork it was drawn on.
     */
    private static Component composeHudLine(String miniMessage) {
        String safe = HudWidth.fit(miniMessage);
        Component parsed = parse(safe);
        if (!HudWidth.fitsPill(safe)) {
            return parsed;
        }
        Component pill = Glyphs.composePill(parsed);
        // The artwork is one glyph wider than the text it was sized for; if that pushed the line
        // past the contract, plain text is the honest answer.
        return Glyphs.fitsPillText(pill) ? pill : parsed;
    }

    /**
     * True while any message is still on screen for this player.
     *
     * <p>Round 58. The live aim HUD is re-sent every tick, so any notification that went through the
     * same line was erased fifteen milliseconds later -- the toast was written, the player never saw
     * it. A message now owns the action bar for the minimum display time of its surface; the aim HUD
     * stays quiet and comes back by itself.
     */
    public static boolean railOnScreen(Player player) {
        return HudSurfaceRegistry.busy(player);
    }

    private static String helpLine(String command, String what) {
        return "   <" + Palette.DIM + ">▸</" + Palette.DIM + "> <click:run_command:'" + command
            + "'><hover:show_text:'<" + Palette.MUTED + ">" + what + "</" + Palette.MUTED + ">'>"
            + "<" + Palette.GOLD + ">" + command + "</" + Palette.GOLD + "></hover></click>  <"
            + Palette.MUTED + ">" + what + "</" + Palette.MUTED + ">";
    }

    public static void sendDiscordCard(Audience target, String discordUrl) {
        String url = ensureHttps(discordUrl, "https://discord.gg/pnJhKuU2QK");
        String display = stripScheme(url);
        target.sendMessage(Component.empty());
        target.sendMessage(Component.text()
            .append(Component.text("  "))
            .append(Glyphs.bitmap(Glyphs.CHAT_ICON_DISCORD))
            .append(parse("  " + gradBold(Palette.BLURPLE + ":" + Palette.BLURPLE, Glyphs.smallCapsLabels("Official Discord Server"))))
            .asComponent());
        target.sendMessage(Component.text()
            .append(Component.text("     Join the community, find teammates & follow tournaments:", TextColor.color(0x94A3B8)))
            .asComponent());
        target.sendMessage(Component.text()
            .append(Component.text("     ▸ ", TextColor.color(0x5865F2)))
            .append(Component.text(url, Style.style(TextColor.color(0xE2E8F0), TextDecoration.UNDERLINED))
                .hoverEvent(HoverEvent.showText(parse("<" + Palette.BLURPLE + ">Click to join </" + Palette.BLURPLE + "><#F8FAFC>" + display + "</#F8FAFC>")))
                .clickEvent(ClickEvent.openUrl(url)))
            .asComponent());
        target.sendMessage(Component.empty());
    }

    public static void sendIllustratedWelcomeCard(
        Player player,
        String webUrl,
        String shopUrl,
        String discordUrl,
        String headlineNotice,
        String bodyNotice,
        int unreadMessages
    ) {
        String centerPad = Glyphs.shift(56);
        player.sendMessage(Component.empty());
        for (int i = 0; i < Glyphs.CHAT_LOGO_STRIPS.length; i++) {
            player.sendMessage(Component.text(centerPad + Glyphs.logoStripPair(i), Glyphs.BITMAP_STYLE));
        }
        player.sendMessage(Component.empty());

        if (webUrl != null && !webUrl.isBlank()) {
            String safeWeb = ensureHttps(webUrl, "");
            player.sendMessage(buildLinkLine(48, Glyphs.CHAT_ICON_WEB, stripScheme(safeWeb).toUpperCase(java.util.Locale.ROOT), TextColor.color(0x7DD3FC), safeWeb));
        }
        if (shopUrl != null && !shopUrl.isBlank()) {
            String safeShop = ensureHttps(shopUrl, "");
            player.sendMessage(buildLinkLine(44, Glyphs.CHAT_ICON_SHOP, stripScheme(safeShop).toUpperCase(java.util.Locale.ROOT), TextColor.color(0xFDE68A), safeShop));
        }
        String safeDiscord = ensureHttps(discordUrl, "https://discord.gg/pnJhKuU2QK");
        player.sendMessage(buildLinkLine(38, Glyphs.CHAT_ICON_DISCORD, stripScheme(safeDiscord), TextColor.color(0x818CF8), safeDiscord));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text()
            .append(Component.text("  "))
            .append(Glyphs.bitmap(Glyphs.CHAT_ICON_MEGAPHONE))
            .append(parse(" " + gradBold(GRAD_SUNSET, escapeMini(headlineNotice))))
            .asComponent());
        player.sendMessage(Component.text("     " + bodyNotice, TextColor.color(0xCBD5E1)));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("  ").append(parse("<#64748B>ᴊᴜᴍᴘ ǫᴜɪᴄᴋʟʏ</#64748B>")));
        player.sendMessage(navDock("play", "locker", "shop", "ranks", "tuto"));
        player.sendMessage(Component.empty());
    }

    private static String ensureHttps(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.startsWith("http://") || raw.startsWith("https://") ? raw : "https://" + raw;
    }

    private static String stripScheme(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceFirst("^https?://", "");
    }

    private static Component buildLinkLine(int shiftPx, char iconGlyph, String label, TextColor color, String url) {
        TextComponent.Builder b = Component.text();
        if (shiftPx > 0) {
            b.append(Component.text(Glyphs.shift(shiftPx), Glyphs.BITMAP_STYLE));
        }
        b.append(Glyphs.bitmap(iconGlyph));
        b.append(Component.text("  " + label, color)
            .hoverEvent(HoverEvent.showText(parse("<#94A3B8>Click to open </#94A3B8><#F8FAFC>" + url + "</#F8FAFC>")))
            .clickEvent(ClickEvent.openUrl(url)));
        return b.asComponent();
    }

    public static void title(Audience target, String mainMm, String subMm, int inMs, int stayMs, int outMs) {
        Title.Times times = Title.Times.times(Duration.ofMillis(inMs), Duration.ofMillis(stayMs), Duration.ofMillis(outMs));
        target.showTitle(Title.title(parse(mainMm), parse(subMm), times));
    }

    private static final double VANILLA_AUDIBLE_RANGE = 16.0;

    private static final double PACK_AUDIBLE_RANGE = 48.0;

    public static float soundVolumeOf(Player player) {
        if (player == null) {
            return 0.0f;
        }
        fr.bosketball.BosketBallPlugin plug = fr.bosketball.BosketBallPlugin.getInstance();
        if (plug == null || plug.statsManager() == null) {
            return 1.0f;
        }
        var prof = plug.statsManager().getIfPresent(player.getUniqueId());
        if (prof == null) {
            return 1.0f;
        }
        return prof.settings().uiSoundVolume();
    }

    public static void playPackSound(Location loc, String soundEvent, float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        fr.bosketball.BosketBallPlugin plug = fr.bosketball.BosketBallPlugin.getInstance();
        Key key = Key.key("bosketball", soundEvent);
        double maxRangeSq = PACK_AUDIBLE_RANGE * PACK_AUDIBLE_RANGE;
        for (Player p : world.getPlayers()) {
            if (plug != null) {
                var prof = plug.statsManager() != null ? plug.statsManager().getIfPresent(p.getUniqueId()) : null;
                if (prof != null && !prof.settings().customSounds()) {
                    continue;
                }
            }
            double distSq = p.getLocation().distanceSquared(loc);
            if (distSq > maxRangeSq) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            float attenuation = dist <= VANILLA_AUDIBLE_RANGE
                ? 1.0f
                : (float) (1.0 - (dist - VANILLA_AUDIBLE_RANGE) / (PACK_AUDIBLE_RANGE * 2.0));
            float effective = volume * attenuation * soundVolumeOf(p);
            if (effective <= 0.02f) {
                continue;
            }
            Sound sound = Sound.sound(key, Sound.Source.PLAYER, effective, pitch);
            if (dist <= VANILLA_AUDIBLE_RANGE) {
                p.playSound(sound, loc.getX(), loc.getY(), loc.getZ());
            } else {
                p.playSound(sound, Sound.Emitter.self());
            }
        }
    }

    public static void playPersonalPackSound(Player player, String soundEvent, float volume, float pitch) {
        if (player == null) {
            return;
        }
        fr.bosketball.BosketBallPlugin plug = fr.bosketball.BosketBallPlugin.getInstance();
        if (plug != null && plug.statsManager() != null) {
            var prof = plug.statsManager().getIfPresent(player.getUniqueId());
            if (prof != null && !prof.settings().customSounds()) {
                return;
            }
        }
        float effective = volume * soundVolumeOf(player);
        if (effective <= 0.02f) {
            return;
        }
        Sound sound = Sound.sound(Key.key("bosketball", soundEvent), Sound.Source.PLAYER, effective, pitch);
        player.playSound(sound, Sound.Emitter.self());
    }
}
