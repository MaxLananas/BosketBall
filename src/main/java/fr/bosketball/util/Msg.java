package fr.bosketball.util;

import java.time.Duration;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Adventure 5.2.0 MiniMessage, SmallCaps, Title & Custom 3D Resource-Pack Sound helper.
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
