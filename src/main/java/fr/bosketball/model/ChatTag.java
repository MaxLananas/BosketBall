package fr.bosketball.model;

import fr.bosketball.util.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * 45 Glossy 3D-Beveled Aura & Prestige Chat Tags with inline mini icons (inspired by image-5.png!).
 * Combined with the 18 Top-#1/#2/#3 leaderboard stat pills, BosketBall ships 63 HiDPI beveled pill badges!
 */
public enum ChatTag {
    AURA("aura", '\uE240', "ᴀᴜʀᴀ", "ʟ'ᴀᴜʀᴀ ᴘᴜʀᴇ ᴅᴜ ᴘᴀʀǫᴜᴇᴛ", 0, TextColor.color(0x2AA8F8)),
    BALLER("baller", '\uE248', "ʙᴀʟʟᴇʀ", "ꜱᴛʏʟᴇ ᴘʟᴀʏɢʀᴏᴜɴᴅ ᴀᴜᴛʜᴇɴᴛɪǫᴜᴇ", 0, TextColor.color(0xF28420)),
    HOOPER("hooper", '\uE257', "ʜᴏᴏᴘᴇʀ", "ᴠʀᴀɪ ᴊᴏᴜᴇᴜʀ ᴅᴇ ᴘᴀʀǫᴜᴇᴛ", 0, TextColor.color(0x349CF8)),
    STREET("street", '\uE252', "ꜱᴛʀᴇᴇᴛ", "ʀᴏɪ ᴅᴜ ʙɪᴛᴜᴍᴇ & ᴅᴜ 2ᴠ2", 0, TextColor.color(0x76D42A)),
    SPLASH("splash", '\uE246', "ꜱᴘʟᴀꜱʜ", "ꜰɪʟᴇᴛ ᴘᴜʀ ᴀ 3 ᴘᴏɪɴᴛꜱ", 0, TextColor.color(0x18C6E8)),
    SWISH("swish", '\uE256', "ꜱᴡɪꜱʜ", "ꜰɪᴄᴇʟʟᴇ 100% ꜱᴀɴꜱ ᴛᴏᴜᴄʜᴇʀ ʟ'ᴀʀᴄᴇᴀᴜ", 0, TextColor.color(0x24D4B6)),
    CYBER("cyber", '\uE250', "ᴄʏʙᴇʀ", "ᴇᴅɪᴛɪᴏɴ ɴᴇᴏɴ 26.2", 0, TextColor.color(0x1AD49E)),
    SAKURA("sakura", '\uE24F', "ꜱᴀᴋᴜʀᴀ", "ꜰʟᴇᴜʀ ᴅᴇ ᴄᴇʀɪꜱɪᴇʀ", 0, TextColor.color(0xF26CAC)),
    VIPER("viper", '\uE284', "ᴠɪᴘᴇʀ", "ᴅᴇᴛᴇɴᴛᴇ ᴇᴛ ʀᴇꜰʟᴇxᴇꜱ ꜰᴏᴜᴅʀᴏʏᴀɴᴛꜱ", 0, TextColor.color(0x26D45C)),
    BLITZ("blitz", '\uE285', "ʙʟɪᴛᴢ", "ᴘʀᴇꜱꜱɪɴɢ ᴛᴏᴜᴛ-ᴛᴇʀʀᴀɪɴ ɪɴꜱᴛᴀɴᴛᴀɴᴇ", 0, TextColor.color(0xF8B218)),
    NOVA("nova", '\uE28B', "ɴᴏᴠᴀ", "ᴇᴛᴏɪʟᴇ ᴍᴏɴᴛᴀɴᴛᴇ ᴅᴜ ᴘᴀʀǫᴜᴇᴛ", 0, TextColor.color(0xF87E24)),
    VENOM("venom", '\uE288', "ᴠᴇɴᴏᴍ", "ꜰɪɴɪᴛɪᴏɴ ᴍᴏʀᴛᴇʟʟᴇ ᴇɴ ᴄᴏɴᴛʀᴇ-ᴀᴛᴛᴀǫᴜᴇ", 0, TextColor.color(0x1CC254)),
    DIME("dime", '\uE259', "ᴅɪᴍᴇ", "ᴍᴀɪᴛʀᴇ ᴅᴇ ʟᴀ ᴘᴀꜱꜱᴇ ᴀᴠᴇᴜɢʟᴇ", 1025, TextColor.color(0xA85CFA)),
    ACE("ace", '\uE24B', "ᴀᴄᴇ", "ꜰɪɴɪꜱꜱᴇᴜʀ ᴅ'ᴇʟɪᴛᴇ", 1050, TextColor.color(0xEC306C)),
    BUCKETS("buckets", '\uE25A', "ʙᴜᴄᴋᴇᴛꜱ", "ᴍᴀᴄʜɪɴᴇ ᴀ ᴘᴀɴɪᴇʀꜱ", 1075, TextColor.color(0xF2661C)),
    FLASH("flash", '\uE24C', "ꜰʟᴀꜱʜ", "ᴠɪᴛᴇꜱꜱᴇ ᴇᴄʟᴀɪʀ ᴇɴ ᴄᴏɴᴛʀᴇ-ᴀᴛᴛᴀǫᴜᴇ", 1100, TextColor.color(0xE8C41C)),
    ROYAL("royal", '\uE280', "ʀᴏʏᴀʟ", "ɴᴏʙʟᴇꜱꜱᴇ ᴅᴇꜱ ᴘᴀʀǫᴜᴇᴛꜱ", 1125, TextColor.color(0x8048F6)),
    GLACIER("glacier", '\uE24D', "ɢʟᴀᴄɪᴇʀ", "ꜱᴀɴɢ-ꜰʀᴏɪᴅ ᴀʙꜱᴏʟᴜ", 1150, TextColor.color(0x3AB8FF)),
    CLAMP("clamp", '\uE258', "ᴄʟᴀᴍᴘ", "ᴄᴀᴅᴇɴᴀꜱ ᴅᴇꜰᴇɴꜱɪꜰ ɪɴꜰʀᴀɴᴄʜɪꜱꜱᴀʙʟᴇ", 1175, TextColor.color(0xD82C56)),
    SNIPER("sniper", '\uE243', "ꜱɴɪᴘᴇʀ", "ᴄʜɪʀᴜʀɢɪᴇɴ ᴅᴇʀʀɪᴇʀᴇ ʟ'ᴀʀᴄ", 1200, TextColor.color(0x22C470)),
    FROSTBITE("frostbite", '\uE286', "ꜰʀᴏꜱᴛʙɪᴛᴇ", "ᴠᴇɪɴᴇꜱ ᴅᴇ ɢʟᴀᴄᴇ ᴇɴ ꜰɪɴ ᴅᴇ ᴍᴀᴛᴄʜ", 1225, TextColor.color(0x30CEF4)),
    ANKLE("ankle", '\uE244', "ᴀɴᴋʟᴇ", "ʙʀɪꜱᴇᴜʀ ᴅᴇ ᴄʜᴇᴠɪʟʟᴇꜱ (ᴄʀᴏꜱꜱᴏᴠᴇʀ)", 1250, TextColor.color(0x9C44F2)),
    RONIN("ronin", '\uE28C', "ʀᴏɴɪɴ", "ɢᴜᴇʀʀɪᴇʀ ꜱᴏʟɪᴛᴀɪʀᴇ ᴅᴜ 1ᴠ1", 1275, TextColor.color(0xD02C44)),
    PRODIGY("prodigy", '\uE291', "ᴘʀᴏᴅɪɢʏ", "ᴊᴇᴜɴᴇ ᴘʀᴏᴅɪɢᴇ ᴅᴜ ᴘᴀʀǫᴜᴇᴛ", 950, TextColor.color(0x8E44FA)),
    ICEVEINS("iceveins", '\uE297', "ɪᴄᴇᴠᴇɪɴꜱ", "ꜱᴀɴɢ-ꜰʀᴏɪᴅ ɢʟᴀᴄɪᴀʟ ᴀᴜx ʟᴀɴᴄᴇʀꜱ", 980, TextColor.color(0x1CCEE8)),
    LOCKDOWN("lockdown", '\uE247', "ʟᴏᴄᴋᴅᴏᴡɴ", "ᴍᴜʀᴀɪʟʟᴇ ᴅᴇꜰᴇɴꜱɪᴠᴇ", 1300, TextColor.color(0x6B88B8)),
    GALAXY("galaxy", '\uE283', "ɢᴀʟᴀxʏ", "ᴘᴏʀᴛᴇᴇ ᴄᴏꜱᴍɪǫᴜᴇ ᴅᴜ ʟᴏɢᴏ", 1325, TextColor.color(0x603EE4)),
    POSTER("poster", '\uE245', "ᴘᴏꜱᴛᴇʀ", "ᴅᴜɴᴋᴇᴜʀ ꜱᴀɴꜱ ᴘɪᴛɪᴇ", 1350, TextColor.color(0xEE5422)),
    ECLIPSE("eclipse", '\uE28A', "ᴇᴄʟɪᴘꜱᴇ", "ᴄᴏɴᴛʀᴇ ꜱᴏʟᴀɪʀᴇ ᴀᴜ ᴄᴇʀᴄʟᴇ", 1375, TextColor.color(0x5C40BA)),
    INFERNO("inferno", '\uE24E', "ɪɴꜰᴇʀɴᴏ", "ᴇɴ ꜰᴇᴜ ꜱᴜʀ ᴛᴏᴜᴛ ʟᴇ ᴛᴇʀʀᴀɪɴ", 1400, TextColor.color(0xE43420)),
    CHEF("chef", '\uE28F', "ᴄʜᴇꜰ", "ᴄᴜɪꜱɪɴᴇ ᴀ 3 ᴘᴏɪɴᴛꜱ ᴅᴜ ᴍɪʟɪᴇᴜ ᴅᴇ ᴛᴇʀʀᴀɪɴ", 1425, TextColor.color(0x2492F6)),
    CHAOS("chaos", '\uE25B', "ᴄʜᴀᴏꜱ", "ᴇᴅɪᴛɪᴏɴ ᴄʜᴀᴏꜱ ᴄᴜʙᴇᴅ 26.2", 1450, TextColor.color(0xEA2A8E)),
    ZENITH("zenith", '\uE289', "ᴢᴇɴɪᴛʜ", "ᴀᴘᴏɢᴇᴇ ᴅᴇ ʟᴀ ᴅᴇᴛᴇɴᴛᴇ ᴠᴇʀᴛɪᴄᴀʟᴇ", 1475, TextColor.color(0x34A8FA)),
    PHANTOM("phantom", '\uE249', "ᴘʜᴀɴᴛᴏᴍ", "ɪɴꜱᴀɪꜱɪꜱꜱᴀʙʟᴇ ᴅᴀɴꜱ ʟᴀ ʀᴀǫᴜᴇᴛᴛᴇ", 1500, TextColor.color(0x8844E2)),
    SKYHOOK("skyhook", '\uE295', "ꜱᴋʏʜᴏᴏᴋ", "ʙʀᴀꜱ-ʀᴏᴜʟᴇ ᴄᴇʟᴇꜱᴛᴇ ɪɴᴄᴏɴᴛʀᴀʙʟᴇ", 1525, TextColor.color(0x2CB2F8)),
    ALPHA("alpha", '\uE281', "ᴀʟᴘʜᴀ", "ᴍᴇɴᴇᴜʀ ᴅᴏᴍɪɴᴀɴᴛ ᴅᴜ 5 ᴍᴀᴊᴇᴜʀ", 1550, TextColor.color(0xEC3838)),
    DEADEYE("deadeye", '\uE294', "ᴅᴇᴀᴅᴇʏᴇ", "ᴘʀᴇᴄɪꜱɪᴏɴ ᴄʜɪʀᴜʀɢɪᴄᴀʟᴇ 100% ɢʀᴇᴇɴ", 1575, TextColor.color(0xDA2640)),
    CLUTCH("clutch", '\uE242', "ᴄʟᴜᴛᴄʜ", "ᴛᴜᴇᴜʀ ᴀᴜ ʙᴜᴢᴢᴇʀ-ʙᴇᴀᴛᴇʀ", 1600, TextColor.color(0xE0303E)),
    CROSSGOD("crossgod", '\uE296', "ᴄʀᴏꜱꜱɢᴏᴅ", "ᴍᴀɪᴛʀᴇ ᴀʙꜱᴏʟᴜ ᴅᴇꜱ ᴄʜᴇᴠɪʟʟᴇꜱ ʙʀɪꜱᴇᴇꜱ", 1625, TextColor.color(0xD834C0)),
    OMEGA("omega", '\uE282', "ᴏᴍᴇɢᴀ", "ʟᴇ ᴅᴇʀɴɪᴇʀ ᴍᴏᴛ ᴀᴜ ʙᴜᴢᴢᴇʀ", 1650, TextColor.color(0x24AEF6)),
    SHOWTIME("showtime", '\uE298', "ꜱʜᴏᴡᴛɪᴍᴇ", "ꜱᴘᴇᴄᴛᴀᴄʟᴇ ʜᴏʟʟʏᴡᴏᴏᴅɪᴇɴ ᴇɴ ᴄᴏɴᴛʀᴇ-ᴀᴛᴛᴀǫᴜᴇ", 1675, TextColor.color(0xF4B026)),
    TITAN("titan", '\uE251', "ᴛɪᴛᴀɴ", "ᴅᴏᴍɪɴᴀᴛɪᴏɴ ᴘʜʏꜱɪǫᴜᴇ ᴛᴏᴛᴀʟᴇ", 1700, TextColor.color(0xC8823A)),
    AIR("air", '\uE28E', "ᴀɪʀ", "ᴇɴᴠᴏʟ ʟᴇɢᴇɴᴅᴀɪʀᴇ ᴅᴇᴘᴜɪꜱ ʟᴇꜱ ʟᴀɴᴄᴇʀꜱ", 1740, TextColor.color(0xE83030)),
    MVP("mvp", '\uE254', "ᴍᴠᴘ", "ᴍᴇɪʟʟᴇᴜʀ ᴊᴏᴜᴇᴜʀ ᴅᴜ ᴘᴀʀǫᴜᴇᴛ", 1775, TextColor.color(0xFA9218)),
    MAMBA("mamba", '\uE28D', "ᴍᴀᴍʙᴀ", "ᴍᴇɴᴛᴀʟɪᴛᴇ ᴅ'ᴀᴄɪᴇʀ ɪɴᴇʙʀᴀɴʟᴀʙʟᴇ", 1815, TextColor.color(0x9A3AEC)),
    KING("king", '\uE24A', "ᴋɪɴɢ", "ꜱᴏᴜᴠᴇʀᴀɪɴ ᴅᴇ ʟ'ᴀʀᴇɴᴇ", 1850, TextColor.color(0xE4A218)),
    DYNASTY("dynasty", '\uE292', "ᴅʏɴᴀꜱᴛʏ", "ʀᴇɢɴᴇ ɪᴍᴘᴇʀɪᴀʟ ꜱᴜʀ ʟᴀ ʟɪɢᴜᴇ", 1890, TextColor.color(0xE0981C)),
    LEGEND("legend", '\uE255', "ʟᴇɢᴇɴᴅ", "ɪᴄᴏɴᴇ ɪᴍᴍᴏʀᴛᴇʟʟᴇ ᴅᴜ ꜱᴛᴀᴅᴇ", 1925, TextColor.color(0xBA34EE)),
    UNSTOPPABLE("unstoppable", '\uE299', "ᴜɴꜱᴛᴏᴘᴘᴀʙʟᴇ", "ꜰᴏʀᴄᴇ ɪɴᴀʀʀᴇᴛᴀʙʟᴇ ᴅᴀɴꜱ ʟᴀ ʀᴀǫᴜᴇᴛᴛᴇ", 1950, TextColor.color(0xEC3820)),
    SUPERNOVA("supernova", '\uE287', "ꜱᴜᴘᴇʀɴᴏᴠᴀ", "ᴇxᴘʟᴏꜱɪᴏɴ ꜱᴛᴇʟʟᴀɪʀᴇ 26.2", 1975, TextColor.color(0xF64E80)),
    BALLGOD("ballgod", '\uE293', "ʙᴀʟʟɢᴏᴅ", "ᴅɪᴠɪɴɪᴛᴇ ᴅᴜ ʙᴀʟʟᴏɴ ᴄᴀʀʀᴇ 3ᴅ", 2015, TextColor.color(0xF86C1A)),
    CHAMPION("champion", '\uE253', "ᴄʜᴀᴍᴘɪᴏɴ", "ᴄʜᴀᴍᴘɪᴏɴ ᴏꜰꜰɪᴄɪᴇʟ ʙᴏꜱᴋᴇᴛʙᴀʟʟ", 2050, TextColor.color(0xF4BA2A)),
    IMMORTAL("immortal", '\uE290', "ɪᴍᴍᴏʀᴛᴀʟ", "ᴘᴀɴᴛʜᴇᴏɴ ᴇᴛᴇʀɴᴇʟ ᴅᴜ ꜱɪᴇᴄʟᴇ", 2125, TextColor.color(0xF6BC20)),
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
