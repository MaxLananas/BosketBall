package fr.bosketball.social;

import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Persistent Friends & 4-Player Party System.
 */
public final class SocialPartyManager {

    public static final int MAX_PARTY_SIZE = 4;

    public static final class Party {
        private volatile UUID leaderId;
        private final Set<UUID> members = new CopyOnWriteArraySet<>();

        public Party(UUID leaderId) {
            this.leaderId = leaderId;
            this.members.add(leaderId);
        }

        public UUID leaderId() {
            return leaderId;
        }

        public void setLeaderId(UUID leaderId) {
            this.leaderId = leaderId;
        }

        public Set<UUID> members() {
            return Collections.unmodifiableSet(members);
        }

        public int size() {
            return members.size();
        }

        public boolean isLeader(UUID playerId) {
            return leaderId != null && leaderId.equals(playerId);
        }
    }

    private final StatsManager statsManager;
    private final Map<UUID, Set<UUID>> pendingFriendRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Party> playerParties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingPartyInvites = new ConcurrentHashMap<>();

    public SocialPartyManager(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    public int countOnlineFriends(UUID playerId) {
        PlayerProfile p = statsManager.getIfPresent(playerId);
        if (p == null || Bukkit.getServer() == null) {
            return 0;
        }
        int online = 0;
        for (UUID fid : p.friends()) {
            Player fp = Bukkit.getPlayer(fid);
            if (fp != null && fp.isOnline()) {
                online++;
            }
        }
        return online;
    }

    public void sendOrAcceptFriendRequest(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            Msg.send(sender, Msg.error("You cannot add yourself as a friend."));
            return;
        }
        PlayerProfile sProf = statsManager.getOrCreate(sender);
        PlayerProfile tProf = statsManager.getOrCreate(target);
        if (sProf.isFriend(target.getUniqueId())) {
            Msg.send(sender, Msg.info("<white>" + target.getName() + "</white> is already on your friends list."));
            return;
        }

        Set<UUID> incomingToSender = pendingFriendRequests.getOrDefault(sender.getUniqueId(), Set.of());
        if (incomingToSender.contains(target.getUniqueId())) {
            incomingToSender.remove(target.getUniqueId());
            sProf.addFriend(target.getUniqueId());
            tProf.addFriend(sender.getUniqueId());
            statsManager.saveProfileAsync(sProf);
            statsManager.saveProfileAsync(tProf);

            Msg.send(sender, Msg.success("You are now friends with <white><bold>" + target.getName() + "</bold></white>."));
            Msg.send(target, Msg.success("<white><bold>" + sender.getName() + "</bold></white> accepted your friend request."));
            Msg.playPersonalPackSound(sender, "game.green_release", 0.85f, 1.15f);
            Msg.playPersonalPackSound(target, "game.green_release", 0.85f, 1.15f);
            return;
        }

        pendingFriendRequests.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
            .add(sender.getUniqueId());
        Msg.send(sender, Msg.success("Friend request sent to <white>" + target.getName() + "</white>."));
        Msg.send(target, "<white><bold>" + sender.getName() + "</bold></white> <#CBD5E1>sent you a friend request! </#CBD5E1>"
            + "<click:run_command:'/bb friend add " + sender.getName() + "'><hover:show_text:'<green>Accept friend request</green>'><#4ADE80><bold>[✔ Accept]</bold></#4ADE80></hover></click>");
        Msg.playPersonalPackSound(target, "ui.click", 0.9f, 1.2f);
    }

    public void removeFriend(Player player, String targetName) {
        PlayerProfile found = statsManager.findByNameIgnoreCase(targetName);
        if (found == null) {
            Msg.send(player, Msg.error("Player not found: " + targetName));
            return;
        }
        removeFriend(player, found.uuid(), found.lastKnownName());
    }

    public void removeFriend(Player player, UUID targetId, String targetName) {
        PlayerProfile pProf = statsManager.getOrCreate(player);
        boolean removed = pProf.removeFriend(targetId);
        PlayerProfile tProf = statsManager.getIfPresent(targetId);
        if (tProf != null) {
            tProf.removeFriend(player.getUniqueId());
        }
        if (removed) {
            statsManager.saveProfileAsync(pProf);
            if (tProf != null) {
                statsManager.saveProfileAsync(tProf);
            }
            Msg.send(player, Msg.info("Removed <white>" + targetName + "</white> from your friends list."));
        } else {
            Msg.send(player, Msg.error("That player is not on your friends list."));
        }
    }

    public Party getParty(UUID playerId) {
        return playerParties.get(playerId);
    }

    public int partySizeOrOne(UUID playerId) {
        Party p = playerParties.get(playerId);
        return p == null ? 1 : p.size();
    }

    public List<Player> onlinePartyMembers(UUID playerId) {
        Party party = playerParties.get(playerId);
        if (party == null) {
            Player solo = Bukkit.getServer() != null ? Bukkit.getPlayer(playerId) : null;
            return solo != null ? List.of(solo) : List.of();
        }
        List<Player> list = new ArrayList<>(party.size());
        for (UUID id : party.members) {
            Player m = Bukkit.getServer() != null ? Bukkit.getPlayer(id) : null;
            if (m != null && m.isOnline()) {
                list.add(m);
            }
        }
        return list;
    }

    public List<Player> onlinePartyMembers(Player player) {
        Party party = playerParties.get(player.getUniqueId());
        if (party == null) {
            return List.of(player);
        }
        List<Player> list = new ArrayList<>(party.size());
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.isOnline()) {
                list.add(m);
            }
        }
        return list.isEmpty() ? List.of(player) : list;
    }

    public void inviteToParty(Player leader, Player target) {
        if (leader.getUniqueId().equals(target.getUniqueId())) {
            Msg.send(leader, Msg.error("You are already in your own party."));
            return;
        }
        Party party = playerParties.computeIfAbsent(leader.getUniqueId(), Party::new);
        if (!party.leaderId().equals(leader.getUniqueId())) {
            Msg.send(leader, Msg.error("Only the party leader can invite players."));
            return;
        }
        if (party.size() >= MAX_PARTY_SIZE) {
            Msg.send(leader, Msg.error("Your party is full (" + MAX_PARTY_SIZE + "/" + MAX_PARTY_SIZE + ")."));
            return;
        }
        PlayerProfile targetProfile = statsManager.getOrCreate(target);
        if (!targetProfile.settings().partyInvites()) {
            Msg.send(leader, Msg.error("<white>" + target.getName() + "</white> has party invites disabled."));
            return;
        }
        pendingPartyInvites.put(target.getUniqueId(), leader.getUniqueId());
        Msg.send(leader, Msg.success("Invited <white>" + target.getName() + "</white> to your party."));
        Msg.send(target, "<#38BDF8><bold>" + leader.getName() + "</bold></#38BDF8> <#CBD5E1>invited you to their party (" + party.size() + "/" + MAX_PARTY_SIZE + ")! </#CBD5E1>"
            + "<click:run_command:'/bb party join " + leader.getName() + "'><hover:show_text:'<green>Join party</green>'><#4ADE80><bold>[▶ Join Party]</bold></#4ADE80></hover></click>");
        Msg.playPersonalPackSound(target, "ui.click", 0.9f, 1.25f);
    }

    public void acceptPartyInvite(Player player, String leaderNameOrNull) {
        Player leader = null;
        if (leaderNameOrNull != null && !leaderNameOrNull.isBlank()) {
            leader = Bukkit.getPlayer(leaderNameOrNull);
        } else {
            UUID pendingLeader = pendingPartyInvites.get(player.getUniqueId());
            if (pendingLeader != null) {
                leader = Bukkit.getPlayer(pendingLeader);
            }
        }
        if (leader == null) {
            Msg.send(player, Msg.error("No active party invite found."));
            return;
        }
        acceptPartyInvite(player, leader);
    }

    public void acceptPartyInvite(Player player, Player leader) {
        UUID expectedLeader = pendingPartyInvites.remove(player.getUniqueId());
        if (expectedLeader == null || !expectedLeader.equals(leader.getUniqueId())) {
            Msg.send(player, Msg.error("No pending party invite from <white>" + leader.getName() + "</white>."));
            return;
        }
        leaveParty(player, true);
        Party party = playerParties.computeIfAbsent(leader.getUniqueId(), Party::new);
        if (party.size() >= MAX_PARTY_SIZE) {
            Msg.send(player, Msg.error("That party is now full."));
            return;
        }
        party.members.add(player.getUniqueId());
        playerParties.put(player.getUniqueId(), party);
        broadcastParty(party, Msg.info("<white><bold>" + player.getName() + "</bold></white> joined the party (" + party.size() + "/" + MAX_PARTY_SIZE + ")."));
    }

    public void leaveParty(Player player) {
        leaveParty(player, false);
    }

    public void handleDisconnect(UUID playerId) {
        pendingPartyInvites.remove(playerId);
        Party party = playerParties.remove(playerId);
        if (party != null) {
            party.members.remove(playerId);
            if (party.members.size() == 1) {
                UUID last = party.members.iterator().next();
                playerParties.remove(last);
            } else if (!party.members.isEmpty() && party.isLeader(playerId)) {
                party.setLeaderId(party.members.iterator().next());
            }
        }
    }

    public void leaveParty(Player player, boolean silent) {
        Party party = playerParties.remove(player.getUniqueId());
        if (party == null) {
            if (!silent) {
                Msg.send(player, Msg.info("You are not in a party."));
            }
            return;
        }
        party.members.remove(player.getUniqueId());
        if (party.members.isEmpty()) {
            return;
        }
        if (party.leaderId().equals(player.getUniqueId())) {
            UUID nextLeader = party.members.iterator().next();
            party.setLeaderId(nextLeader);
        }
        if (party.members.size() == 1) {
            UUID last = party.members.iterator().next();
            playerParties.remove(last);
        }
        if (!silent) {
            Msg.send(player, Msg.info("You left the party."));
            broadcastParty(party, Msg.info("<white>" + player.getName() + "</white> left the party."));
        }
    }

    private void broadcastParty(Party party, String mm) {
        if (Bukkit.getServer() == null) {
            return;
        }
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null && m.isOnline()) {
                Msg.send(m, mm);
            }
        }
    }
}
