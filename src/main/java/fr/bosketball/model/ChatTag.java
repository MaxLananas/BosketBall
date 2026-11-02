package fr.bosketball.model;

import fr.bosketball.util.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * 20 Glossy 3D-Beveled Aura & Prestige Chat Tags (inspired by image-5.png!).
 * Each tag renders a dedicated pixel-art beveled pill sprite (\uE240..\uE253) from the 26.2 resource pack!
 */
public enum ChatTag {
    AURA("aura", '\uE240', "ᴀᴜʀᴀ", "ʟ'ᴀᴜʀᴀ ᴘᴜʀᴇ ᴅᴜ ᴘᴀʀǫᴜᴇᴛ", 0, TextColor.color(0x2AA8F8)),
    BALLER("baller", '\uE248', "ʙᴀʟʟᴇʀ", "ꜱᴛʏʟᴇ ᴘʟᴀʏɢʀᴏᴜɴᴅ ᴀᴜᴛʜᴇɴᴛɪǫᴜᴇ", 0, TextColor.color(0xF28420)),
    STREET("street", '\uE252', "ꜱᴛʀᴇᴇᴛ", "ʀᴏɪ ᴅᴜ ʙɪᴛᴜᴍᴇ & ᴅᴜ 2ᴠ2", 0, TextColor.color(0x76D42A)),
    SPLASH("splash", '\uE246', "ꜱᴘʟᴀꜱʜ", "ꜰɪʟᴇᴛ ᴘᴜʀ ᴀ 3 ᴘᴏɪɴᴛꜱ", 0, TextColor.color(0x18C6E8)),
    CYBER("cyber", '\uE250', "ᴄʏʙᴇʀ", "ᴇᴅɪᴛɪᴏɴ ɴᴇᴏɴ 26.2", 0, TextColor.color(0x1AD49E)),
    SAKURA("sakura", '\uE24F', "ꜱᴀᴋᴜʀᴀ", "ꜰʟᴇᴜʀ ᴅᴇ ᴄᴇʀɪꜱɪᴇʀ", 0, TextColor.color(0xF26CAC)),
    ACE("ace", '\uE24B', "ᴀᴄᴇ", "ꜰɪɴɪꜱꜱᴇᴜʀ ᴅ'ᴇʟɪᴛᴇ", 1050, TextColor.color(0xEC306C)),
    FLASH("flash", '\uE24C', "ꜰʟᴀꜱʜ", "ᴠɪᴛᴇꜱꜱᴇ ᴇᴄʟᴀɪʀ ᴇɴ ᴄᴏɴᴛʀᴇ-ᴀᴛᴛᴀǫᴜᴇ", 1100, TextColor.color(0xE8C41C)),
    GLACIER("glacier", '\uE24D', "ɢʟᴀᴄɪᴇʀ", "ꜱᴀɴɢ-ꜰʀᴏɪᴅ ᴀʙꜱᴏʟᴜ", 1150, TextColor.color(0x3AB8FF)),
    SNIPER("sniper", '\uE243', "ꜱɴɪᴘᴇʀ", "ᴄʜɪʀᴜʀɢɪᴇɴ ᴅᴇʀʀɪᴇʀᴇ ʟ'ᴀʀᴄ", 1200, TextColor.color(0x22C470)),
    ANKLE("ankle", '\uE244', "ᴀɴᴋʟᴇ", "ʙʀɪꜱᴇᴜʀ ᴅᴇ ᴄʜᴇᴠɪʟʟᴇꜱ (ᴄʀᴏꜱꜱᴏᴠᴇʀ)", 1250, TextColor.color(0x9C44F2)),
    LOCKDOWN("lockdown", '\uE247', "ʟᴏᴄᴋᴅᴏᴡɴ", "ᴍᴜʀᴀɪʟʟᴇ ᴅᴇꜰᴇɴꜱɪᴠᴇ", 1300, TextColor.color(0x6B88B8)),
    POSTER("poster", '\uE245', "ᴘᴏꜱᴛᴇʀ", "ᴅᴜɴᴋᴇᴜʀ ꜱᴀɴꜱ ᴘɪᴛɪᴇ", 1350, TextColor.color(0xEE5422)),
    INFERNO("inferno", '\uE24E', "ɪɴꜰᴇʀɴᴏ", "ᴇɴ ꜰᴇᴜ ꜱᴜʀ ᴛᴏᴜᴛ ʟᴇ ᴛᴇʀʀᴀɪɴ", 1400, TextColor.color(0xE43420)),
    PHANTOM("phantom", '\uE249', "ᴘʜᴀɴᴛᴏᴍ", "ɪɴꜱᴀɪꜱɪꜱꜱᴀʙʟᴇ ᴅᴀɴꜱ ʟᴀ ʀᴀǫᴜᴇᴛᴛᴇ", 1500, TextColor.color(0x8844E2)),
    CLUTCH("clutch", '\uE242', "ᴄʟᴜᴛᴄʜ", "ᴛᴜᴇᴜʀ ᴀᴜ ʙᴜᴢᴢᴇʀ-ʙᴇᴀᴛᴇʀ", 1600, TextColor.color(0xE0303E)),
    TITAN("titan", '\uE251', "ᴛɪᴛᴀɴ", "ᴅᴏᴍɪɴᴀᴛɪᴏɴ ᴘʜʏꜱɪǫᴜᴇ ᴛᴏᴛᴀʟᴇ", 1700, TextColor.color(0xC8823A)),
    KING("king", '\uE24A', "ᴋɪɴɢ", "ꜱᴏᴜᴠᴇʀᴀɪɴ ᴅᴇ ʟ'ᴀʀᴇɴᴇ", 1850, TextColor.color(0xE4A218)),
    CHAMPION("champion", '\uE253', "ᴄʜᴀᴍᴘɪᴏɴ", "ᴄʜᴀᴍᴘɪᴏɴ ᴏꜰꜰɪᴄɪᴇʟ ʙᴏꜱᴋᴇᴛʙᴀʟʟ", 2000, TextColor.color(0xF4BA2A)),
    GOAT("goat", '\uE241', "ɢᴏᴀᴛ", "ʟᴇ ᴘʟᴜꜱ ɢʀᴀɴᴅ ᴅᴇ ᴛᴏᴜꜱ ʟᴇꜱ ᴛᴇᴍᴘꜱ", 2200, TextColor.color(0xEEB21C));

    private final String id;
    private final char glyph;
    private final String smallCapsLabel;
    private final String descriptionSmallCaps;
    private final int unlockElo;
    private final TextColor accentColor;

    ChatTag(String id, char glyph, String smallCapsLabel, String descriptionSmallCaps, int unlockElo, TextColor accentColor) {
        this.id = id;
        this.glyph = glyph;
        this.smallCapsLabel = smallCapsLabel;
        this.descriptionSmallCaps = descriptionSmallCaps;
        this.unlockElo = unlockElo;
        this.accentColor = accentColor;
    }

    public String id() {
        return id;
    }

    public char glyph() {
        return glyph;
    }

    public String smallCapsLabel() {
        return smallCapsLabel;
    }

    public String descriptionSmallCaps() {
        return descriptionSmallCaps;
    }

    public int unlockElo() {
        return unlockElo;
    }

    public TextColor accentColor() {
        return accentColor;
    }

    public Component pillComponent() {
        return Glyphs.bitmap(glyph);
    }

    public String displayName() {
        return smallCapsLabel;
    }

    public String loreHint() {
        return descriptionSmallCaps;
    }

    public Rank requiredRank() {
        return Rank.fromElo(unlockElo);
    }

    public boolean isUnlocked(PlayerProfile profile) {
        return profile != null && profile.elo() >= unlockElo;
    }

    public static ChatTag fromIdOrNull(String raw) {
        return byNameOrNull(raw);
    }

    public static ChatTag byNameOrNull(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("aucun")) {
            return null;
        }
        for (ChatTag tag : values()) {
            if (tag.name().equalsIgnoreCase(raw.trim()) || tag.id.equalsIgnoreCase(raw.trim())) {
                return tag;
            }
        }
        return null;
    }
}
