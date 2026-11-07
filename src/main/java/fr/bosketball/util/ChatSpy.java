package fr.bosketball.util;

import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Staff chat spy (Round 27).
 *
 * <p>Every restricted channel (party, guild, private message, match/team notice) is relayed to
 * the staff with a clearly labelled line, so moderators can read <b>everything</b> that is said on
 * the server without having to be a member of the conversation.
 *
 * <p>The relay never duplicates a line for a staff member who already receives the channel
 * (a staff member in the party gets the normal line only).
 */
public final class ChatSpy {

    /** Label used in front of relayed lines. */
    public static final String SPY_MARK = "<#F59E0B><bold>ꜱᴘʏ</bold></#F59E0B>";

    private ChatSpy() {
    }

    public static boolean isSpy(Player player) {
        return player != null && (player.isOp()
            || Perms.staff(player)
            || Perms.has(player, Perms.ADMIN));
    }

    /**
     * Relays a restricted chat line to every staff member.
     *
     * @param channelLabel short channel tag, e.g. {@code "<#38BDF8>ᴘᴀʀᴛʏ</#38BDF8>"}
     * @param senderName   raw sender name
     * @param body         raw (already mini-message safe) message body
     * @param alreadySeen  players who already received the line through their own channel
     */
    public static void relay(String channelLabel, String senderName, String body, Set<UUID> alreadySeen) {
        if (body == null || body.isBlank()) {
            return;
        }
        String line = channelLabel + " " + SPY_MARK + " <dark_gray>│</dark_gray> <white>"
            + Msg.escapeMini(senderName == null ? "?" : senderName)
            + "</white><dark_gray>:</dark_gray> <#E2E8F0>" + body + "</#E2E8F0>";
        relay(line, alreadySeen);
    }

    /** Relays an already formatted mini-message line to every staff member not in {@code alreadySeen}. */
    public static void relay(String miniMessageLine, Set<UUID> alreadySeen) {
        if (miniMessageLine == null || miniMessageLine.isBlank()) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!isSpy(online)) {
                continue;
            }
            if (alreadySeen != null && alreadySeen.contains(online.getUniqueId())) {
                continue;
            }
            Msg.sendSocial(online, miniMessageLine);
        }
    }

    /** Convenience overload collecting the members of a party/guild as "already seen". */
    public static Set<UUID> seenBy(java.util.Collection<? extends Player> players) {
        Set<UUID> ids = new java.util.HashSet<>();
        if (players != null) {
            for (Player p : players) {
                if (p != null) {
                    ids.add(p.getUniqueId());
                }
            }
        }
        return ids;
    }
}
