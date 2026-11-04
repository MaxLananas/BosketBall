package fr.bosketball.social;

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
            return String.format("<#%06X>%s</#%06X>", colorRgb, smallCapsTitle, colorRgb);
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
        this.name = name;
        this.tag = tag;
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
            this.name = name;
        }
    }

    public String tag() {
        return tag;
    }

    public void setTag(String tag) {
        if (tag != null && !tag.isBlank()) {
            this.tag = tag.toUpperCase(Locale.ROOT);
        }
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
        this.motd = motd == null ? "" : motd;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
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
        this.colorRgb = colorRgb;
        this.colorRgbIndex = Math.max(0, PALETTE.indexOf(colorRgb));
    }

    private int colorRgbIndex = 0;

    public int colorRgbIndex() {
        return colorRgbIndex;
    }

    public void setColorRgbIndex(int index) {
        this.colorRgbIndex = Math.floorMod(index, PALETTE.size());
        this.colorRgb = PALETTE.get(this.colorRgbIndex);
    }

    public void cycleColor(int step) {
        setColorRgbIndex(colorRgbIndex + step);
    }

    public String hexColor() {
        return String.format("#%06X", colorRgb);
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

    public int averageMemberElo() {
        if (members.isEmpty()) {
            return elo;
        }
        int total = 0;
        for (Member m : members) {
            total += m.contributedElo();
        }
        return total / members.size();
    }

    public Member mostValuable() {
        Member best = null;
        for (Member m : members) {
            if (best == null || m.contributedElo() > best.contributedElo()) {
                best = m;
            }
        }
        return best;
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
