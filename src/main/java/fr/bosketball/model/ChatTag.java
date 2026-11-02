package fr.bosketball.model;

import fr.bosketball.util.Glyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * Unlockable Cosmetic Chat & Nameplate Tags (purchasable in the Shop with Coins).
 */
public enum ChatTag {
    AURA("aura", '\uE240', "AURA", "Pure court presence", 250, TextColor.color(0x2AA8F8)),
    BALLER("baller", '\uE248', "BALLER", "Authentic streetball style", 250, TextColor.color(0xF28420)),
    HOOPER("hooper", '\uE257', "HOOPER", "Dedicated hardwood competitor", 250, TextColor.color(0x349CF8)),
    STREET("street", '\uE252', "STREET", "2v2 blacktop specialist", 250, TextColor.color(0x76D42A)),
    SPLASH("splash", '\uE246', "SPLASH", "Deep three-point shooter", 250, TextColor.color(0x18C6E8)),
    SWISH("swish", '\uE256', "SWISH", "Nothing but net", 250, TextColor.color(0x24D4B6)),
    VIPER("viper", '\uE284', "VIPER", "Lightning-fast reflexes", 250, TextColor.color(0x26D45C)),
    BLITZ("blitz", '\uE285', "BLITZ", "Relentless full-court press", 250, TextColor.color(0xF8B218)),
    NOVA("nova", '\uE28B', "NOVA", "Rising star on the court", 250, TextColor.color(0xF87E24)),
    VENOM("venom", '\uE288', "VENOM", "Lethal fast-break finisher", 250, TextColor.color(0x1CC254)),
    PRODIGY("prodigy", '\uE291', "PRODIGY", "Natural court talent", 250, TextColor.color(0x8E44FA)),
    ICEVEINS("iceveins", '\uE297', "ICEVEINS", "Calm under pressure", 250, TextColor.color(0x1CCEE8)),

    CYBER("cyber", '\uE250', "CYBER", "Electric neon pulse", 500, TextColor.color(0x1AD49E)),
    SAKURA("sakura", '\uE24F', "SAKURA", "Cherry blossom edition", 500, TextColor.color(0xF26CAC)),
    DIME("dime", '\uE259', "DIME", "No-look playmaker", 500, TextColor.color(0xA85CFA)),
    ACE("ace", '\uE24B', "ACE", "Go-to scorer", 500, TextColor.color(0xEC306C)),
    BUCKETS("buckets", '\uE25A', "BUCKETS", "Automatic scoring machine", 500, TextColor.color(0xF2661C)),
    FLASH("flash", '\uE24C', "FLASH", "Blazing transition speed", 500, TextColor.color(0xE8C41C)),
    ROYAL("royal", '\uE280', "ROYAL", "Hardwood royalty", 500, TextColor.color(0x8048F6)),
    GLACIER("glacier", '\uE24D', "GLACIER", "Sub-zero composure", 500, TextColor.color(0x3AB8FF)),
    CLAMP("clamp", '\uE258', "CLAMP", "Perimeter lockdown defender", 500, TextColor.color(0xD82C56)),
    SNIPER("sniper", '\uE243', "SNIPER", "Deadly beyond the arc", 500, TextColor.color(0x22C470)),
    FROSTBITE("frostbite", '\uE286', "FROSTBITE", "Cold-blooded fourth quarter", 500, TextColor.color(0x30CEF4)),
    ANKLE("ankle", '\uE244', "ANKLE", "Crossover specialist", 500, TextColor.color(0x9C44F2)),
    RONIN("ronin", '\uE28C', "RONIN", "Fearless 1v1 challenger", 500, TextColor.color(0xD02C44)),

    LOCKDOWN("lockdown", '\uE247', "LOCKDOWN", "Impenetrable paint defense", 1000, TextColor.color(0x6B88B8)),
    GALAXY("galaxy", '\uE283', "GALAXY", "Logo-range sharpshooter", 1000, TextColor.color(0x603EE4)),
    POSTER("poster", '\uE245', "POSTER", "High-flying rim shaker", 1000, TextColor.color(0xEE5422)),
    ECLIPSE("eclipse", '\uE28A', "ECLIPSE", "Rim-protecting shot blocker", 1000, TextColor.color(0x5C40BA)),
    INFERNO("inferno", '\uE24E', "INFERNO", "Unstoppable hot streak", 1000, TextColor.color(0xE43420)),
    CHEF("chef", '\uE28F', "CHEF", "Cooking from half-court", 1000, TextColor.color(0x2492F6)),
    CHAOS("chaos", '\uE25B', "CHAOS", "Unpredictable offensive Tempo", 1000, TextColor.color(0xEA2A8E)),
    ZENITH("zenith", '\uE289', "ZENITH", "Peak vertical athleticism", 1000, TextColor.color(0x34A8FA)),
    PHANTOM("phantom", '\uE249', "PHANTOM", "Untouchable in the lane", 1000, TextColor.color(0x8844E2)),
    SKYHOOK("skyhook", '\uE295', "SKYHOOK", "Unblockable high release", 1000, TextColor.color(0x2CB2F8)),
    ALPHA("alpha", '\uE281', "ALPHA", "Franchise floor general", 1000, TextColor.color(0xEC3838)),
    DEADEYE("deadeye", '\uE294', "DEADEYE", "Pure green-release shooter", 1000, TextColor.color(0xDA2640)),
    CLUTCH("clutch", '\uE242', "CLUTCH", "Buzzer-beater closer", 1000, TextColor.color(0xE0303E)),
    CROSSGOD("crossgod", '\uE296', "CROSSGOD", "Master of ankle-breakers", 1000, TextColor.color(0xD834C0)),

    OMEGA("omega", '\uE282', "OMEGA", "The final word at the buzzer", 2000, TextColor.color(0x24AEF6)),
    SHOWTIME("showtime", '\uE298', "SHOWTIME", "Highlight-reel fast breaks", 2000, TextColor.color(0xF4B026)),
    TITAN("titan", '\uE251', "TITAN", "Total paint dominance", 2000, TextColor.color(0xC8823A)),
    AIR("air", '\uE28E', "AIR", "Free-throw line flight", 2000, TextColor.color(0xE83030)),
    MVP("mvp", '\uE254', "MVP", "Most Valuable Player", 2000, TextColor.color(0xFA9218)),
    MAMBA("mamba", '\uE28D', "MAMBA", "Relentless winning mentality", 2000, TextColor.color(0x9A3AEC)),
    KING("king", '\uE24A', "KING", "Ruler of the arena", 2000, TextColor.color(0xE4A218)),
    DYNASTY("dynasty", '\uE292', "DYNASTY", "Multi-champion legacy", 2000, TextColor.color(0xE0981C)),
    LEGEND("legend", '\uE255', "LEGEND", "Hall of Fame icon", 2000, TextColor.color(0xBA34EE)),
    UNSTOPPABLE("unstoppable", '\uE299', "UNSTOPPABLE", "Unchecked scoring force", 2000, TextColor.color(0xEC3820)),
    SUPERNOVA("supernova", '\uE287', "SUPERNOVA", "Explosive superstar aura", 2000, TextColor.color(0xF64E80)),
    BALLGOD("ballgod", '\uE293', "BALLGOD", "Master of the hardwood", 2000, TextColor.color(0xF86C1A)),
    CHAMPION("champion", '\uE253', "CHAMPION", "Official Season Champion", 2000, TextColor.color(0xF4BA2A)),
    IMMORTAL("immortal", '\uE290', "IMMORTAL", "Timeless arena legend", 2000, TextColor.color(0xF6BC20)),
    GOAT("goat", '\uE241', "GOAT", "Greatest of All Time", 2500, TextColor.color(0xEEB21C));

    private final String id;
    private final char glyph;
    private final String label;
    private final String description;
    private final int priceCoins;
    private final TextColor accentColor;

    ChatTag(String id, char glyph, String label, String description, int priceCoins, TextColor accentColor) {
        this.id = id;
        this.glyph = glyph;
        this.label = label;
        this.description = description;
        this.priceCoins = priceCoins;
        this.accentColor = accentColor;
    }

    public String id() {
        return id;
    }

    public char glyph() {
        return glyph;
    }

    public String smallCapsLabel() {
        return label;
    }

    public String descriptionSmallCaps() {
        return description;
    }

    public int defaultPriceCoins() {
        return priceCoins;
    }

    public int priceCoins() {
        return fr.bosketball.config.ShopConfig.priceOf(this, priceCoins);
    }

    public int coinPrice() {
        return priceCoins();
    }

    public int unlockElo() {
        return 0;
    }

    public TextColor accentColor() {
        return accentColor;
    }

    public String tierLabel() {
        if (priceCoins >= 2000) return "<gold>Legendary</gold>";
        if (priceCoins >= 1000) return "<light_purple>Epic</light_purple>";
        if (priceCoins >= 500) return "<aqua>Rare</aqua>";
        return "<green>Common</green>";
    }

    public Component pillComponent() {
        return Glyphs.bitmap(glyph);
    }

    public String displayName() {
        return label;
    }

    public String loreHint() {
        return description;
    }

    public boolean isUnlocked(PlayerProfile profile) {
        return profile != null && profile.hasUnlockedTag(this);
    }

    public static ChatTag fromIdOrNull(String raw) {
        return byNameOrNull(raw);
    }

    public static ChatTag byNameOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toLowerCase();
        for (ChatTag t : values()) {
            if (t.id.equals(norm) || t.name().equalsIgnoreCase(norm)) {
                return t;
            }
        }
        return null;
    }
}
