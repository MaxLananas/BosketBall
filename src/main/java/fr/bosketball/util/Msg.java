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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Adventure 5.2.0 MiniMessage, Framed Divider Cards (image-4), Illustrated Welcome Banner (image-9) & 3D Sound Helper.
 */
public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#FF7A18:#FFD43B><bold>✦ ʙᴏꜱᴋᴇᴛʙᴀʟʟ</bold></gradient> <dark_gray>»</dark_gray> ";

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
        return "<aqua>" + body + "</aqua>";
    }

    public static String success(String body) {
        return "<#52FFB8>✔ " + body + "</#52FFB8>";
    }

    public static String warn(String body) {
        return "<yellow>⚠ " + body + "</yellow>";
    }

    public static String error(String body) {
        return "<#FF5C5C>✖ " + body + "</#FF5C5C>";
    }

    /**
     * Sends a framed chat card with top [ Header ] metallic divider & bottom metallic rule (exact image-4.png!).
     */
    public static void sendFramedCard(Audience target, String headerTitle, List<String> bodyMiniMessages) {
        target.sendMessage(Glyphs.composeDividerHeader(headerTitle));
        for (String line : bodyMiniMessages) {
            target.sendMessage(MM.deserialize(line));
        }
        target.sendMessage(Glyphs.composeDividerBottom());
    }

    /**
     * Sends the giant 5-strip full-color illustrated BOSKETBALL comic banner + links + megaphone + [Poste] [Ouvrir] (exact image-9.png!).
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

        String cleanWeb = stripScheme(webUrl);
        String cleanShop = stripScheme(shopUrl);
        String cleanDiscord = stripScheme(discordUrl);
        // Centered Web / Shop / Discord links with custom pixel-art icons
        player.sendMessage(buildLinkLine(48, Glyphs.CHAT_ICON_WEB, cleanWeb, TextColor.color(0xA8F0FF), ensureHttps(webUrl)));
        player.sendMessage(buildLinkLine(44, Glyphs.CHAT_ICON_SHOP, cleanShop, TextColor.color(0xFFF0C2), ensureHttps(shopUrl)));
        player.sendMessage(buildLinkLine(38, Glyphs.CHAT_ICON_DISCORD, cleanDiscord, TextColor.color(0x5874FF), ensureHttps(discordUrl)));
        player.sendMessage(Component.empty());

        // Megaphone Announcement Block (exact image-9.png!)
        player.sendMessage(Component.text()
            .append(Glyphs.bitmap(Glyphs.CHAT_ICON_MEGAPHONE))
            .append(Component.text(" " + headlineNotice, TextColor.color(0xFF3E3E)))
            .asComponent());
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text(bodyNotice, TextColor.color(0xFFA868)));
        player.sendMessage(buildLinkLine(4, Glyphs.CHAT_ICON_DISCORD, cleanDiscord, TextColor.color(0x5874FF), ensureHttps(discordUrl)));

        if (unreadMessages > 0) {
            player.sendMessage(Component.empty());
            String label = unreadMessages == 1 ? "Tu as un message non lu. " : ("Tu as " + unreadMessages + " messages non lus. ");
            Component mailLine = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("Poste", TextColor.color(0xFFE03B)))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(label, NamedTextColor.GRAY))
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("Ouvrir", TextColor.color(0x38F848))
                    .hoverEvent(HoverEvent.showText(parse("<green>✦ ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ʟɪʀᴇ ᴠᴏꜱ ᴍᴇꜱꜱᴀɢᴇꜱ & ᴘᴀᴛᴄʜ ɴᴏᴛᴇꜱ</green>")))
                    .clickEvent(ClickEvent.runCommand("/bb mail")))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .asComponent();
            player.sendMessage(mailLine);
        }
    }

    private static String ensureHttps(String raw) {
        if (raw == null || raw.isBlank()) {
            return "https://bosketball.fr";
        }
        return raw.startsWith("http://") || raw.startsWith("https://") ? raw : "https://" + raw.toLowerCase();
    }

    private static String stripScheme(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceFirst("^https?://", "").toUpperCase();
    }

    private static Component buildLinkLine(int shiftPx, char iconGlyph, String label, TextColor color, String url) {
        TextComponent.Builder b = Component.text();
        if (shiftPx > 0) {
            b.append(Component.text(Glyphs.shift(shiftPx), Glyphs.BITMAP_STYLE));
        }
        b.append(Glyphs.bitmap(iconGlyph));
        b.append(Component.text(" " + label, color)
            .hoverEvent(HoverEvent.showText(parse("<aqua>▸ ᴏᴜᴠʀɪʀ " + label + "</aqua>")))
            .clickEvent(ClickEvent.openUrl(url)));
        return b.asComponent();
    }

    public static void title(Audience target, String mainMm, String subMm, int inMs, int stayMs, int outMs) {
        Title.Times times = Title.Times.times(Duration.ofMillis(inMs), Duration.ofMillis(stayMs), Duration.ofMillis(outMs));
        target.showTitle(Title.title(MM.deserialize(mainMm), MM.deserialize(subMm), times));
    }

    /**
     * Plays a custom spatialized 3D .ogg sound from assets/bosketball/sounds.json to all nearby players!
     */
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
