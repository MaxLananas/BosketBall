package fr.bosketball.model;

public enum PlayerStatus {

    LOBBY("ʟᴏʙʙʏ", "In the Lobby", "icon_home", 0x9CA7BD, "●"),
    QUEUE("ǫᴜᴇᴜᴇ", "In Queue", "icon_target", 0xFBBF24, "◔"),
    MATCH("ᴍᴀᴛᴄʜ", "On Court", "icon_battle", 0xFF5C4D, "⚔"),
    SPECTATING("ꜱᴘᴇᴄ", "Spectating", "icon_spectate", 0x38BDF8, "◉"),
    LOCKER("ʟᴏᴄᴋᴇʀ", "Locker Room", "icon_shop", 0xA855F7, "◈"),
    TUTORIAL("ᴛᴜᴛᴏ", "Tutorial", "icon_book", 0x4ADE80, "✎"),
    AFK("ᴀꜰᴋ", "Away From Keyboard", "icon_history", 0x64748B, "◌"),
    OFFLINE("ᴏꜰꜰʟɪɴᴇ", "Offline", "icon_leave", 0x475569, "○");

    private final String smallCapsTitle;
    private final String description;
    private final String iconModel;
    private final int colorRgb;
    private final String symbol;

    PlayerStatus(String smallCapsTitle, String description, String iconModel, int colorRgb, String symbol) {
        this.smallCapsTitle = smallCapsTitle;
        this.description = description;
        this.iconModel = iconModel;
        this.colorRgb = colorRgb;
        this.symbol = symbol;
    }

    public String smallCapsTitle() {
        return smallCapsTitle;
    }

    public String description() {
        return description;
    }

    public String iconModel() {
        return iconModel;
    }

    public int colorRgb() {
        return colorRgb;
    }

    public String symbol() {
        return symbol;
    }

    public String coloredSymbol() {
        return String.format(java.util.Locale.ROOT, "<#%06X>%s</#%06X>", colorRgb, symbol, colorRgb);
    }

    public String coloredLabel() {
        return String.format(java.util.Locale.ROOT, "<#%06X>%s</#%06X>", colorRgb, smallCapsTitle, colorRgb);
    }

    public boolean isAvailable() {
        return this == LOBBY || this == LOCKER;
    }
}
