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

    public static final String PREFIX = "<!shadow><white>" + Glyphs.SYS_SERVER + "</white></!shadow> ";

    private Msg() {
    }

    /** Brand gradients used across titles, boss bars and cards (MiniMessage {@code <gradient:…>}). */
    public static final String GRAD_BRAND = "#FF9E2C:#FFD43B";

    public static final String GRAD_GOLD = "#FFD43B:#F59E0B";

    public static final String GRAD_MINT = "#4ADE80:#22D3EE";

    public static final String GRAD_ICE = "#38BDF8:#818CF8";

    public static final String GRAD_SUNSET = "#FF8C1A:#FF5EA8";

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
     * {@code "<red>" -> "</red>"}.
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
        return "<!shadow><white>" + pillGlyph + "</white></!shadow>";
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
        return "<#CBD5E1>" + body + "</#CBD5E1>";
    }

    public static String success(String body) {
        return "<#4ADE80>✔ </#4ADE80><#F1F5F9>" + body + "</#F1F5F9>";
    }

    public static String warn(String body) {
        return "<#FBBF24>⚠ </#FBBF24><#F1F5F9>" + body + "</#F1F5F9>";
    }

    public static String error(String body) {
        return "<#F87171>✖ </#F87171><#F1F5F9>" + body + "</#F1F5F9>";
    }

    public static String coinsBadge(int amount) {
        return "<!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow> <#FDE047><bold>" + amount + "</bold></#FDE047> <#FBBF24>Coins</#FBBF24>";
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
                .hoverEvent(HoverEvent.showText(parse("<#4ADE80><bold>ᴘʟᴀʏ</bold></#4ADE80>\n<gray>Ranked 1v1–5v5, 3PT Contest, Practice</gray>")))
                .clickEvent(ClickEvent.runCommand("/play"));
            case "locker", "skins", "cosmetics" -> Glyphs.bitmap(Glyphs.NAV_LOCKER)
                .hoverEvent(HoverEvent.showText(parse("<#38BDF8><bold>ꜱᴋɪɴꜱ</bold></#38BDF8>\n<gray>Balls, dunks, jerseys & chat tags</gray>")))
                .clickEvent(ClickEvent.runCommand("/locker"));
            case "shop" -> Glyphs.bitmap(Glyphs.NAV_SHOP)
                .hoverEvent(HoverEvent.showText(parse("<#FBBF24><bold>ꜱʜᴏᴘ</bold></#FBBF24>\n<gray>Basketballs, dunks and chat tags</gray>")))
                .clickEvent(ClickEvent.runCommand("/shop"));
            case "ranks" -> Glyphs.bitmap(Glyphs.NAV_RANKS)
                .hoverEvent(HoverEvent.showText(parse("<#FACC15><bold>ʀᴀɴᴋꜱ</bold></#FACC15>\n<gray>ELO ladder, badges and court controls</gray>")))
                .clickEvent(ClickEvent.runCommand("/ranks"));
            case "tuto" -> Glyphs.bitmap(Glyphs.NAV_HELP)
                .hoverEvent(HoverEvent.showText(parse("<#94A3B8><bold>ɢᴜɪᴅᴇ</bold></#94A3B8>\n<gray>New player tutorial & server guide</gray>")))
                .clickEvent(ClickEvent.runCommand("/tuto"));
            case "discord" -> Glyphs.bitmap(Glyphs.NAV_DISCORD)
                .hoverEvent(HoverEvent.showText(parse("<#818CF8><bold>ᴅɪꜱᴄᴏʀᴅ</bold></#818CF8>\n<gray>Join the community server</gray>")))
                .clickEvent(ClickEvent.runCommand("/discord"));
            default -> Glyphs.bitmap(Glyphs.NAV_HELP)
                .hoverEvent(HoverEvent.showText(parse("<#94A3B8><bold>ʜᴇʟᴘ</bold></#94A3B8>\n<gray>Open the interactive server guide</gray>")))
                .clickEvent(ClickEvent.runCommand("/help"));
        };
    }

    public static void sendInteractiveHelpCard(CommandSender sender) {
        boolean isOp = Perms.admin(sender) || Perms.map(sender) || Perms.build(sender);
        List<String> lines = new ArrayList<>();
        lines.add("  " + pillTag(Glyphs.SYS_GUIDE) + " <#94A3B8>Everything works visually via your <white>3D Hotbar</white> — or click below:</#94A3B8>");
        lines.add("");

        lines.add("  " + pillTag(Glyphs.SYS_COURT) + " <#FF8C1A><bold>ɢᴀᴍᴇᴘʟᴀʏ & ᴍᴏᴅᴇꜱ</bold></#FF8C1A>");
        lines.add("   <dark_gray>▸</dark_gray> <click:run_command:'/play'><hover:show_text:'<green>Open Play Modes (1v1, 2v2, 3v3, 5v5, 3PT & Spectate)</green>'><#4ADE80><bold>/play</bold></#4ADE80></hover></click> <dark_gray>•</dark_gray> <gray>Queue Ranked 1v1–5v5, 3PT Contest or Spectate</gray>");
        lines.add("   <dark_gray>▸</dark_gray> <click:run_command:'/practice'><hover:show_text:'<aqua>Teleport onto an instant Solo Practice Court</aqua>'><#38BDF8><bold>/practice</bold></#38BDF8></hover></click> <dark_gray>•</dark_gray> <gray>Solo Warm-Up Court & Pro AI Defender Bot</gray>");
        lines.add("   <dark_gray>▸</dark_gray> <click:run_command:'/locker'><hover:show_text:'<aqua>Open the cosmetics locker</aqua>'><#38BDF8><bold>/locker</bold></#38BDF8></hover></click> <dark_gray>•</dark_gray> <gray>Balls, dunk styles, jerseys & chat tags</gray>");
        lines.add("   <dark_gray>▸</dark_gray> <click:run_command:'/spawn'><hover:show_text:'<white>Return to the Lobby Spawn</white>'><white>/spawn</white></hover></click> <dark_gray>│</dark_gray> <click:run_command:'/leave'><hover:show_text:'<red>Leave current match, spectator cam or queue</red>'><#F87171>/leave</#F87171></hover></click> <dark_gray>•</dark_gray> <gray>Return to Hub or exit current court</gray>");
        lines.add("");

        lines.add("  " + pillTag(Glyphs.SYS_SHOP) + " <#FBBF24><bold>ᴘʀᴏɢʀᴇꜱꜱɪᴏɴ & ꜱʜᴏᴘ</bold></#FBBF24>");
        lines.add("   <dark_gray>▸</dark_gray> <click:run_command:'/shop'><hover:show_text:'<gold>Browse Basketballs, Signature Dunks & Collector Tags</gold>'><#FDE047><bold>/shop</bold></#FDE047></hover></click> <dark_gray>•</dark_gray> <gray>Unlock Basketballs, Signature Dunks & Collector Tags</gray>");
        lines.add("   <dark_gray>▸</dark_gray> <click:run_command:'/ranks'><hover:show_text:'<yellow>View ELO Ranks (Rookie → MVP), #1/#2/#3 Badges & Controls</yellow>'><#FBBF24><bold>/ranks</bold></#FBBF24></hover></click> <dark_gray>•</dark_gray> <gray>ELO Ladder, #1/#2/#3 Badges & Court Controls</gray>");
        lines.add("");

        lines.add("  " + pillTag(Glyphs.SYS_SOCIAL) + " <#38BDF8><bold>ꜱᴏᴄɪᴀʟ, ᴅᴜᴇʟꜱ & ᴄʜᴀᴛ</bold></#38BDF8>");
        lines.add("   <dark_gray>▸</dark_gray> <click:run_command:'/friend'><hover:show_text:'<green>Open Friends List (/f add \\<player>)</green>'><#4ADE80><bold>/friend</bold></#4ADE80></hover></click> <dark_gray>&</dark_gray> <click:run_command:'/party'><hover:show_text:'<aqua>Open Party Menu (/p invite \\<player> | /p name \\<team>)</aqua>'><#38BDF8><bold>/party</bold></#38BDF8></hover></click> <dark_gray>•</dark_gray> <gray>Friends, Squads & Custom Team Names</gray>");
        lines.add("   <dark_gray>▸</dark_gray> <click:suggest_command:'/duel '><hover:show_text:'<gold>Tip: You can also Shift + Right-Click any player in Lobby!</gold>'><#FF8C1A><bold>/duel \\<player></bold></#FF8C1A></hover></click> <dark_gray>•</dark_gray> <gray>Challenge to 1v1 <dark_gray>(or Shift+Right-Click player)</dark_gray></gray>");
        lines.add("   <dark_gray>▸</dark_gray> <click:suggest_command:'/msg '><hover:show_text:'<light_purple>Send a private message</light_purple>'><#C084FC>/msg</#C084FC></hover></click> <dark_gray>│</dark_gray> <click:suggest_command:'/r '><hover:show_text:'<light_purple>Reply to last private message</light_purple>'><#C084FC>/r</#C084FC></hover></click> <dark_gray>│</dark_gray> <click:run_command:'/gg'><hover:show_text:'<yellow>Say GG after a match (+Coins)</yellow>'><#FDE047>/gg</#FDE047></hover></click> <dark_gray>│</dark_gray> <click:run_command:'/discord'><hover:show_text:'<blue>Official Discord Link</blue>'><#818CF8>/discord</#818CF8></hover></click> <dark_gray>│</dark_gray> <click:run_command:'/pack'><hover:show_text:'<green>Reload official BosketBall Resource Pack</green>'><#34D399>/pack</#34D399></hover></click> <dark_gray>│</dark_gray> <click:run_command:'/afk'><hover:show_text:'<gray>Step away — tells others you are away from keyboard</gray>'><#94A3B8>/afk</#94A3B8></hover></click> <dark_gray>│</dark_gray> <click:run_command:'/mail'><hover:show_text:'<gold>Mailbox: offline friend requests & party invites</gold>'><#FBBF24>/mail</#FBBF24></hover></click>");

        if (isOp) {
            lines.add("");
            lines.add("  " + pillTag(Glyphs.SYS_SERVER) + " <#F87171><bold>ᴏᴘᴇʀᴀᴛᴏʀ ᴄᴏɴᴛʀᴏʟꜱ (ᴏᴘ ᴏɴʟʏ)</bold></#F87171>");
            lines.add("   <dark_gray>▸</dark_gray> <click:run_command:'/bb admin'><hover:show_text:'<gold>Open Visual Server Control Center GUI</gold>'><#FBBF24><bold>/bb admin</bold></#FBBF24></hover></click> <dark_gray>│</dark_gray> "
                + "<click:run_command:'/bb map'><hover:show_text:'<aqua>Open 1-Click Visual Arena Studio</aqua>'><#38BDF8><bold>/bb map</bold></#38BDF8></hover></click> <dark_gray>│</dark_gray> "
                + "<click:suggest_command:'/bb holo '><hover:show_text:'<light_purple>Place 3D Rotating Leaderboard & Station Holograms</light_purple>'><#E879F9><bold>/bb holo</bold></#E879F9></hover></click> <dark_gray>│</dark_gray> "
                + "<click:run_command:'/build'><#4ADE80>/build</#4ADE80></click> <dark_gray>│</dark_gray> "
                + "<click:run_command:'/setspawn'><white>/setspawn</white></click>");
        }

        lines.add("");

        sendFramedCard(sender, "BOSKETBALL • SERVER HELP", lines);
        sender.sendMessage(Component.text("  ").append(parse("<dark_gray>ᴊᴜᴍᴘ ǫᴜɪᴄᴋʟʏ</dark_gray>")));
        sender.sendMessage(navDock("play", "locker", "shop", "ranks", "discord"));
        sender.sendMessage(Component.empty());
        if (sender instanceof Player p) {
            playPersonalPackSound(p, "ui.click", 0.7f, 1.25f);
        }
    }

    public static void sendDiscordCard(Audience target, String discordUrl) {
        String url = ensureHttps(discordUrl, "https://discord.gg/pnJhKuU2QK");
        String display = stripScheme(url);
        target.sendMessage(Component.empty());
        target.sendMessage(Component.text()
            .append(Component.text("  "))
            .append(Glyphs.bitmap(Glyphs.CHAT_ICON_DISCORD))
            .append(parse("  " + gradBold("#8B9BFF:#5865F2", Glyphs.smallCapsLabels("Official Discord Server"))))
            .asComponent());
        target.sendMessage(Component.text()
            .append(Component.text("     Join the community, find teammates & follow tournaments:", TextColor.color(0x94A3B8)))
            .asComponent());
        target.sendMessage(Component.text()
            .append(Component.text("     ▸ ", TextColor.color(0x5865F2)))
            .append(Component.text(url, Style.style(TextColor.color(0xE2E8F0), TextDecoration.UNDERLINED))
                .hoverEvent(HoverEvent.showText(parse("<#5865F2>Click to join </#5865F2><white>" + display + "</white>")))
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

        player.sendMessage(Component.text("  ").append(parse("<dark_gray>ᴊᴜᴍᴘ ǫᴜɪᴄᴋʟʏ</dark_gray>")));
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
            .hoverEvent(HoverEvent.showText(parse("<gray>Click to open </gray><white>" + url + "</white>")))
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
