package fr.bosketball.social;

import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.List;

public final class OfflineMail {

    public enum Kind {
        FRIEND_REQUEST("ꜰʀɪᴇɴᴅ", Palette.SUCCESS, "icon_friend_add"),
        PARTY_INVITE("ᴘᴀʀᴛʏ", Palette.GOLD, "icon_party_invites"),
        REWARD("ʀᴇᴡᴀʀᴅ", Palette.GOLD_SOFT, "ref_chest"),
        MODERATION("ᴍᴏᴅ", Palette.DANGER, "icon_star_shield"),
        GUILD("ɢᴜɪʟᴅ", Palette.VIOLET, "icon_team"),
        SYSTEM("ꜱʏꜱᴛᴇᴍ", Palette.ICE, "icon_message");

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
