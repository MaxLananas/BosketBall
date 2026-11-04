package fr.bosketball.social;

import java.util.ArrayList;
import java.util.List;

/**
 * One offline notification delivered to a player's in-game mailbox.
 *
 * <p>Used for friend requests, party invites sent while offline, crate/quest rewards,
 * moderation notices, guild invites and tournament reminders. The mailbox is capped so a
 * profile YAML never grows unbounded.</p>
 */
public final class OfflineMail {

    public enum Kind {
        FRIEND_REQUEST("ꜰʀɪᴇɴᴅ", "#4ADE80", "icon_friend_add"),
        PARTY_INVITE("ᴘᴀʀᴛʏ", "#FBBF24", "icon_party_invites"),
        REWARD("ʀᴇᴡᴀʀᴅ", "#FDE047", "icon_chest"),
        MODERATION("ᴍᴏᴅ", "#F87171", "icon_star_shield"),
        GUILD("ɢᴜɪʟᴅ", "#A855F7", "icon_team"),
        SYSTEM("ꜱʏꜱᴛᴇᴍ", "#38BDF8", "icon_message");

        private final String smallCapsTitle;
        private final String hexColor;
        private final String iconModel;

        Kind(String smallCapsTitle, String hexColor, String iconModel) {
            this.smallCapsTitle = smallCapsTitle;
            this.hexColor = hexColor;
            this.iconModel = iconModel;
        }

        public String smallCapsTitle() {
            return smallCapsTitle;
        }

        public String hexColor() {
            return hexColor;
        }

        public String iconModel() {
            return iconModel;
        }
    }

    private final Kind kind;
    private final String senderName;
    private final String subject;
    private final List<String> body;
    private final long sentAtMillis;
    private final String actionCommand;
    private boolean read;

    public OfflineMail(Kind kind, String senderName, String subject, List<String> body, long sentAtMillis, String actionCommand) {
        this.kind = kind;
        this.senderName = senderName == null ? "Server" : senderName;
        this.subject = subject == null ? "" : subject;
        this.body = body == null ? List.of() : new ArrayList<>(body);
        this.sentAtMillis = sentAtMillis;
        this.actionCommand = actionCommand;
    }

    public Kind kind() {
        return kind;
    }

    public String senderName() {
        return senderName;
    }

    public String subject() {
        return subject;
    }

    public List<String> body() {
        return body;
    }

    public long sentAtMillis() {
        return sentAtMillis;
    }

    public String actionCommand() {
        return actionCommand;
    }

    public boolean hasAction() {
        return actionCommand != null && !actionCommand.isBlank();
    }

    public boolean read() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }

    public String ageLabel(long nowMillis) {
        long diff = Math.max(0L, nowMillis - sentAtMillis) / 1000L;
        if (diff < 60) {
            return diff + "s ago";
        }
        if (diff < 3600) {
            return (diff / 60) + "m ago";
        }
        if (diff < 86_400) {
            return (diff / 3600) + "h ago";
        }
        return (diff / 86_400) + "d ago";
    }
}
