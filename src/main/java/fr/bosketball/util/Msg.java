package fr.bosketball.util;

import java.time.Duration;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Clean, Minimalist Server Chat, Welcome Card, Discord Card & Spatial Audio Helper.
 */
public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<#FF8C1A>🏀</#FF8C1A> <dark_gray>│</dark_gray> ";

    private Msg() {
    }

    public static Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static Component prefixed(String miniMessage) {
        return MM.deserialize(PREFIX + miniMessage);
    }

    public static void send(Audience target, String miniMessage) {
        target.sendMessage(prefixed(miniMessage));
    }

    public static String info(String body) {
        return "<#CBD5E1>" + body + "</#CBD5E1>";
    }

    public static String success(String body) {
        return "<#4ADE80>✔ </#4ADE80><#E2E8F0>" + body + "</#E2E8F0>";
    }

    public static String warn(String body) {
        return "<#FBBF24>⚠ </#FBBF24><#E2E8F0>" + body + "</#E2E8F0>";
    }

    public static String error(String body) {
        return "<#F87171>✖ </#F87171><#E2E8F0>" + body + "</#E2E8F0>";
    }

    public static String coinsBadge(int amount) {
        return "<!shadow><white>" + Glyphs.CHAT_ICON_COIN + "</white></!shadow> <#FDE047><bold>" + amount + "</bold></#FDE047> <#FBBF24>Coins</#FBBF24>";
    }

    /**
     * Flushes previous match play-by-play lines from the player's visible chat window when a game ends.
     */
    public static void clearChatBuffer(Audience target) {
        Component blank = Component.empty();
        for (int i = 0; i < 32; i++) {
            target.sendMessage(blank);
        }
    }

    /**
     * Sends a clean framed server card with top & bottom metallic dividers.
     */
    public static void sendFramedCard(Audience target, String headerTitle, List<String> bodyMiniMessages) {
        target.sendMessage(Glyphs.composeDividerHeader(headerTitle));
        for (String line : bodyMiniMessages) {
            target.sendMessage(MM.deserialize(line));
        }
        target.sendMessage(Glyphs.composeDividerBottom());
    }

    /**
     * Sends the official /discord invite card with the custom HiDPI Discord Clyde icon.
     */
    public static void sendDiscordCard(Audience target, String discordUrl) {
        String url = ensureHttps(discordUrl, "https://discord.gg/pnJhKuU2QK");
        String display = stripScheme(url);
        target.sendMessage(Component.empty());
        target.sendMessage(Component.text()
            .append(Component.text("  "))
            .append(Glyphs.bitmap(Glyphs.CHAT_ICON_DISCORD))
            .append(Component.text("  Official Discord Server", Style.style(TextColor.color(0x5865F2), TextDecoration.BOLD)))
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

    /**
     * Sends the server Welcome Card featuring the crowned BOSKETBALL logo, clickable server links & quick actions.
     */
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
        for (char strip : Glyphs.CHAT_LOGO_STRIPS) {
            player.sendMessage(Component.text(centerPad + strip, Glyphs.BITMAP_STYLE));
        }
        player.sendMessage(Component.empty());

        String safeWeb = ensureHttps(webUrl, "https://bosketball.net");
        String safeShop = ensureHttps(shopUrl, "https://store.bosketball.net");
        String safeDiscord = ensureHttps(discordUrl, "https://discord.gg/pnJhKuU2QK");

        player.sendMessage(buildLinkLine(48, Glyphs.CHAT_ICON_WEB, stripScheme(safeWeb).toUpperCase(), TextColor.color(0x7DD3FC), safeWeb));
        player.sendMessage(buildLinkLine(44, Glyphs.CHAT_ICON_SHOP, stripScheme(safeShop).toUpperCase(), TextColor.color(0xFDE68A), safeShop));
        player.sendMessage(buildLinkLine(38, Glyphs.CHAT_ICON_DISCORD, stripScheme(safeDiscord), TextColor.color(0x818CF8), safeDiscord));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text()
            .append(Component.text("  "))
            .append(Glyphs.bitmap(Glyphs.CHAT_ICON_MEGAPHONE))
            .append(Component.text(" " + headlineNotice, Style.style(TextColor.color(0xFBBF24), TextDecoration.BOLD)))
            .asComponent());
        player.sendMessage(Component.text("     " + bodyNotice, TextColor.color(0xCBD5E1)));
        player.sendMessage(Component.empty());

        Component quickRow = Component.text()
            .append(Component.text("     "))
            .append(Component.text("[ ▶ Play ]", Style.style(TextColor.color(0x4ADE80), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(parse("<green>Click to browse game modes</green>")))
                .clickEvent(ClickEvent.runCommand("/bb play")))
            .append(Component.text("   "))
            .append(Component.text("[ 🛒 Shop ]", Style.style(TextColor.color(0xFBBF24), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(parse("<gold>Unlock Tags, Basketballs & Dunks with Coins</gold>")))
                .clickEvent(ClickEvent.runCommand("/bb shop")))
            .append(Component.text("   "))
            .append(Component.text("[ Discord ]", Style.style(TextColor.color(0x818CF8), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(parse("<#818CF8>Join our Discord server</#818CF8>")))
                .clickEvent(ClickEvent.openUrl(safeDiscord)))
            .asComponent();
        player.sendMessage(quickRow);
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
        target.showTitle(Title.title(MM.deserialize(mainMm), MM.deserialize(subMm), times));
    }

    public static void playPackSound(Location loc, String soundEvent, float volume, float pitch) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Sound sound = Sound.sound(Key.key("bosketball", soundEvent), Sound.Source.MASTER, volume, pitch);
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 64.0 * 64.0) {
                p.playSound(sound, loc.getX(), loc.getY(), loc.getZ());
            }
        }
    }

    public static void playPersonalPackSound(Player player, String soundEvent, float volume, float pitch) {
        Sound sound = Sound.sound(Key.key("bosketball", soundEvent), Sound.Source.MASTER, volume, pitch);
        player.playSound(sound, Sound.Emitter.self());
    }
}
