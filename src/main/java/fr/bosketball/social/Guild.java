package fr.bosketball.social;

import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Guild {

    public static final int MAX_OFFICERS = 3;

    public static final List<Integer> PALETTE = List.of(
        0xFF8C1A,
        0x38BDF8,
        0x4ADE80,
        0xA855F7,
        0xFDE047,
        0xF87171,
        0x22D3EE,
        0xF472B6
    );

    public enum Role {
        LEADER("ʟᴇᴀᴅᴇʀ", 0xFDBA74, 3),
        OFFICER("ᴏꜰꜰɪᴄᴇʀ", 0xFBBF24, 2),
        MEMBER("ᴍᴇᴍʙᴇʀ", 0x9CA7BD, 1);

        private final String smallCapsTitle;
        private final int colorRgb;
        private final int weight;

        Role(String smallCapsTitle, int colorRgb, int weight) {
            this.smallCapsTitle = smallCapsTitle;
            this.colorRgb = colorRgb;
            this.weight = weight;
        }

        public String smallCapsTitle() {
            return smallCapsTitle;
        }

        public int colorRgb() {
            return colorRgb;
        }

        public int weight() {
            return weight;
        }

        public String coloredTitle() {
            return String.format(java.util.Locale.ROOT, "<#%06X>%s</#%06X>", colorRgb, smallCapsTitle, colorRgb);
        }

        public boolean canInvite() {
            return this == LEADER || this == OFFICER;
        }

        public boolean canKick() {
            return this == LEADER || this == OFFICER;
        }

        public boolean canEditSettings() {
            return this == LEADER || this == OFFICER;
        }

        public boolean isLeader() {
            return this == LEADER;
        }
    }

    public static final class Member {
        private final UUID uuid;
        private String name;
        private Role role;
        private final long joinedAtMillis;
        private int contributedElo;
        private int matchesPlayed;
        private long lastSeenMillis;

        public Member(UUID uuid, String name, Role role, long joinedAtMillis, int contributedElo) {
            this.uuid = uuid;
            this.name = name == null ? "Unknown" : name;
            this.role = role == null ? Role.MEMBER : role;
            this.joinedAtMillis = joinedAtMillis;
            this.contributedElo = contributedElo;
        }

        public UUID uuid() {
            return uuid;
        }

        public String name() {
            return name;
        }

        public void setName(String name) {
            if (name != null && !name.isBlank()) {
                this.name = name;
            }
        }

        public Role role() {
            return role;
        }

        public void setRole(Role role) {
            if (role != null) {
                this.role = role;
            }
        }

        public long joinedAtMillis() {
            return joinedAtMillis;
        }

        public int contributedElo() {
            return contributedElo;
        }

        public void addContributedElo(int delta) {
            this.contributedElo += delta;
        }

        public int matchesPlayed() {
            return matchesPlayed;
        }

        public void setMatchesPlayed(int matchesPlayed) {
            this.matchesPlayed = Math.max(0, matchesPlayed);
        }

        public void addMatchPlayed() {
            this.matchesPlayed++;
        }

        public long lastSeenMillis() {
            return lastSeenMillis;
        }

        public void setLastSeenMillis(long lastSeenMillis) {
            this.lastSeenMillis = lastSeenMillis;
        }
    }

    private final String id;
    private String name;
    private String tag;
    private final long createdAtMillis;
    private final List<Member> members = new ArrayList<>();

    private int elo;
    private int matchesPlayed;
    private int matchesWon;
    private int trophies;
    private String motd = "";
    private String description = "";
    private int colorRgb = PALETTE.getFirst();
    private boolean openJoin = false;

    public Guild(String id, String name, String tag, long createdAtMillis, int startingElo) {
        this.id = id;
        this.name = sanitizeName(name);
        this.tag = sanitizeTag(tag, this.name);
        this.createdAtMillis = createdAtMillis;
        this.elo = Math.max(0, startingElo);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = sanitizeName(name);
        }
    }

    public String tag() {
        return tag;
    }

    public void setTag(String tag) {
        if (tag != null && !tag.isBlank()) {
            this.tag = sanitizeTag(tag, name);
        }
    }

    public static boolean isValidTag(String tag) {
        return tag != null && tag.matches("[A-Z0-9]+");
    }

    private static String sanitizeName(String raw) {
        String clean = plainText(raw).replaceAll("[^\\p{L}\\p{N} _'’.-]", "")
            .replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? "Guild" : clean;
    }

    private static String sanitizeTag(String raw, String fallbackName) {
        String clean = plainText(raw).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (clean.length() > 12) {
            clean = clean.substring(0, 12);
        }
        if (clean.length() >= 2) {
            return clean;
        }
        String fallback = fallbackName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (fallback.length() > 12) {
            fallback = fallback.substring(0, 12);
        }
        return fallback.length() >= 2 ? fallback : "BB";
    }

    private static String plainText(String raw) {
        if (raw == null) {
            return "";
        }
        return Msg.MM.stripTags(raw)
            .replace('<', ' ')
            .replace('>', ' ')
            .replaceAll("\\p{Cc}+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public List<Member> members() {
        return members;
    }

    public int elo() {
        return elo;
    }

    public void setElo(int elo) {
        this.elo = Math.max(0, elo);
    }

    public void adjustElo(int delta) {
        this.elo = Math.max(0, this.elo + delta);
    }

    public int matchesPlayed() {
        return matchesPlayed;
    }

    public void setMatchesPlayed(int matchesPlayed) {
        this.matchesPlayed = Math.max(0, matchesPlayed);
    }

    public int matchesWon() {
        return matchesWon;
    }

    public void setMatchesWon(int matchesWon) {
        this.matchesWon = Math.max(0, matchesWon);
    }

    public int trophies() {
        return trophies;
    }

    public void setTrophies(int trophies) {
        this.trophies = Math.max(0, trophies);
    }

    public String motd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = plainText(motd);
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = plainText(description);
    }

    public boolean openJoin() {
        return openJoin;
    }

    public void setOpenJoin(boolean openJoin) {
        this.openJoin = openJoin;
    }

    public int colorRgb() {
        return colorRgb;
    }

    public void setColorRgb(int colorRgb) {
        // Round 42: a colour loaded from a legacy file could carry alpha (0xFF38BDF8) or be
        // negative. `%06X` then produced "#FF38BDF8" / "#FFFFFFF…", and MiniMessage threw on
        // "<#FF38BDF8>" — the guild chat line disappeared instead of the colour being wrong.
        this.colorRgb = colorRgb & 0xFFFFFF;
        this.colorRgbIndex = Math.max(0, PALETTE.indexOf(this.colorRgb));
        this.hexColor = null;
    }

    private int colorRgbIndex = 0;

    /** Lazily formatted {@code #RRGGBB}; invalidated by every colour change. */
    private String hexColor;

    public int colorRgbIndex() {
        return colorRgbIndex;
    }

    public void setColorRgbIndex(int index) {
        this.colorRgbIndex = Math.floorMod(index, PALETTE.size());
        this.colorRgb = PALETTE.get(this.colorRgbIndex) & 0xFFFFFF;
        this.hexColor = null;
    }

    public void cycleColor(int step) {
        setColorRgbIndex(colorRgbIndex + step);
    }

    public String hexColor() {
        String cached = hexColor;
        if (cached == null) {
            cached = String.format(java.util.Locale.ROOT, "#%06X", colorRgb & 0xFFFFFF);
            hexColor = cached;
        }
        return cached;
    }

    /** Colour tags are format-checked in one place, so no caller can build a broken tag. */
    public String chatTag() {
        return "<" + hexColor() + ">";
    }

    /** Closes the colour tag opened by {@link #chatTag()}. */
    public String chatTagClose() {
        return "</" + hexColor() + ">";
    }

    public Member member(UUID uuid) {
        for (Member m : members) {
            if (m.uuid().equals(uuid)) {
                return m;
            }
        }
        return null;
    }

    public boolean hasMember(UUID uuid) {
        return member(uuid) != null;
    }

    public Member byName(String playerName) {
        if (playerName == null) {
            return null;
        }
        for (Member m : members) {
            if (m.name().equalsIgnoreCase(playerName.trim())) {
                return m;
            }
        }
        return null;
    }

    public Member leader() {
        for (Member m : members) {
            if (m.role() == Role.LEADER) {
                return m;
            }
        }
        return members.isEmpty() ? null : members.getFirst();
    }

    public String leaderName() {
        Member leader = leader();
        return leader == null ? "—" : leader.name();
    }

    public Role roleOf(UUID uuid) {
        Member m = member(uuid);
        return m == null ? null : m.role();
    }

    public boolean isLeader(UUID uuid) {
        return roleOf(uuid) == Role.LEADER;
    }

    public boolean canManageRoster(UUID uuid) {
        Role r = roleOf(uuid);
        return r != null && r.canInvite();
    }

    public long officerCount() {
        return members.stream().filter(m -> m.role() == Role.OFFICER).count();
    }

    public boolean hasOfficerSlot() {
        return officerCount() < MAX_OFFICERS;
    }

    public void addMember(UUID uuid, String name, Role role) {
        if (uuid != null && !hasMember(uuid)) {
            members.add(new Member(uuid, name, role, System.currentTimeMillis(), 0));
        }
    }

    public boolean removeMember(UUID uuid) {
        return members.removeIf(m -> m.uuid().equals(uuid));
    }

    public void promote(Member target) {
        if (target == null) {
            return;
        }
        if (target.role() == Role.MEMBER) {
            target.setRole(Role.OFFICER);
            return;
        }
        for (Member m : members) {
            if (m.role() == Role.LEADER && !m.uuid().equals(target.uuid())) {
                m.setRole(Role.OFFICER);
            }
        }
        target.setRole(Role.LEADER);
    }

    public void demote(Member target) {
        if (target == null || target.role() == Role.MEMBER) {
            return;
        }
        if (target.role() == Role.LEADER) {
            return;
        }
        target.setRole(Role.MEMBER);
    }

    public List<Member> sortedMembers() {
        List<Member> copy = new ArrayList<>(members);
        copy.sort(Comparator
            .comparingInt((Member m) -> -m.role().weight())
            .thenComparing(Comparator.comparingInt(Member::contributedElo).reversed())
            .thenComparing(Member::name, String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    public double winRate() {
        return matchesPlayed <= 0 ? 0.0 : (double) matchesWon / matchesPlayed;
    }

    public int losses() {
        return Math.max(0, matchesPlayed - matchesWon);
    }

    public String displayTag() {
        return "<" + hexColor() + ">[" + tag + "]</" + hexColor() + ">";
    }

    public String displayName() {
        return "<" + hexColor() + "><bold>" + name + "</bold></" + hexColor() + ">";
    }

    public String displayNameAndTag() {
        return displayName() + " " + displayTag();
    }
}
