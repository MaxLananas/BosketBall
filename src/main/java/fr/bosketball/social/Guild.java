package fr.bosketball.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A persistent team: unlike a party (which dies when the last member disconnects), a guild
 * survives sessions, carries a tag shown in chat, and owns its own ELO so it can be ranked
 * against other guilds on a dedicated leaderboard.
 */
public final class Guild {

    /** Role ladder. Officers can invite, the leader can also disband and kick officers. */
    public enum Role {
        LEADER("ʟᴇᴀᴅᴇʀ", 0xFDBA74),
        OFFICER("ᴏꜰꜰɪᴄᴇʀ", 0xFBBF24),
        MEMBER("ᴍᴇᴍʙᴇʀ", 0x9CA7BD);

        private final String smallCapsTitle;
        private final int colorRgb;

        Role(String smallCapsTitle, int colorRgb) {
            this.smallCapsTitle = smallCapsTitle;
            this.colorRgb = colorRgb;
        }

        public String smallCapsTitle() {
            return smallCapsTitle;
        }

        public int colorRgb() {
            return colorRgb;
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
    }

    /** One roster entry. */
    public static final class Member {
        private final UUID uuid;
        private String name;
        private Role role;
        private long joinedAtMillis;
        private int contributedElo;

        public Member(UUID uuid, String name, Role role, long joinedAtMillis, int contributedElo) {
            this.uuid = uuid;
            this.name = name;
            this.role = role;
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
            this.name = name;
        }

        public Role role() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
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
    private int colorRgb = 0xFF8C1A;
    private boolean openJoin = false;

    public Guild(String id, String name, String tag, long createdAtMillis, int startingElo) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.createdAtMillis = createdAtMillis;
        this.elo = startingElo;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String tag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
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

    /** When {@code true}, any player may join without an invitation. */
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

    public Member leader() {
        for (Member m : members) {
            if (m.role() == Role.LEADER) {
                return m;
            }
        }
        return members.isEmpty() ? null : members.getFirst();
    }

    public Role roleOf(UUID uuid) {
        Member m = member(uuid);
        return m == null ? null : m.role();
    }

    public void addMember(UUID uuid, String name, Role role) {
        if (!hasMember(uuid)) {
            members.add(new Member(uuid, name, role, System.currentTimeMillis(), 0));
        }
    }

    public boolean removeMember(UUID uuid) {
        return members.removeIf(m -> m.uuid().equals(uuid));
    }

    public double winRate() {
        return matchesPlayed <= 0 ? 0.0 : (double) matchesWon / matchesPlayed;
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

    public String displayTag() {
        return "<" + hexColor() + ">[" + tag + "]</" + hexColor() + ">";
    }

    public String displayName() {
        return "<" + hexColor() + "><bold>" + name + "</bold></" + hexColor() + ">";
    }
}
