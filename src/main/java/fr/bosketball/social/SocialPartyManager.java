package fr.bosketball.social;

import fr.bosketball.model.PlayerProfile;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Persistent Friends & Live Group/Party System (powers image-6.png "Group X/4" & image-8.png "AMIS X/Y"!).
 */
public final class SocialPartyManager {

    public static final int MAX_PARTY_SIZE = 4;

    public static final class Party {
        private UUID leaderId;
        private final Set<UUID> members = new LinkedHashSet<>();

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
    private final Map<UUID, Set<UUID>> pendingFriendRequests = new ConcurrentHashMap<>(); // target -> senders
    private final Map<UUID, Party> playerParties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingPartyInvites = new ConcurrentHashMap<>(); // invitee -> leader

    public SocialPartyManager(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //   FRIENDS SYSTEM
    // ═══════════════════════════════════════════════════════════════════════════

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
            Msg.send(sender, "<red>ᴠᴏᴜꜱ ɴᴇ ᴘᴏᴜᴠᴇᴢ ᴘᴀꜱ ᴠᴏᴜꜱ ᴀᴊᴏᴜᴛᴇʀ ᴠᴏᴜꜱ-ᴍᴇᴍᴇ !</red>");
            return;
        }
        PlayerProfile sProf = statsManager.getOrCreate(sender);
        PlayerProfile tProf = statsManager.getOrCreate(target);
        if (sProf.isFriend(target.getUniqueId())) {
            Msg.send(sender, "<yellow><white>" + target.getName() + "</white> ᴇꜱᴛ ᴅᴇᴊᴀ ᴅᴀɴꜱ ᴠᴏᴛʀᴇ ʟɪꜱᴛᴇ ᴅ'ᴀᴍɪꜱ !</yellow>");
            return;
        }

        Set<UUID> incomingToSender = pendingFriendRequests.getOrDefault(sender.getUniqueId(), Set.of());
        if (incomingToSender.contains(target.getUniqueId())) {
            incomingToSender.remove(target.getUniqueId());
            sProf.addFriend(target.getUniqueId());
            tProf.addFriend(sender.getUniqueId());
            statsManager.save();

            Msg.sendFramedCard(sender, "Amis", List.of(
                "<green>✓ ᴠᴏᴜꜱ ᴇᴛᴇꜱ ᴍᴀɪɴᴛᴇɴᴀɴᴛ ᴀᴍɪ ᴀᴠᴇᴄ <white><bold>" + target.getName() + "</bold></white> !</green>"
            ));
            Msg.sendFramedCard(target, "Amis", List.of(
                "<green>✓ <white><bold>" + sender.getName() + "</bold></white> ᴀ ᴀᴄᴄᴇᴘᴛᴇ ᴠᴏᴛʀᴇ ᴅᴇᴍᴀɴᴅᴇ ᴅ'ᴀᴍɪ !</green>"
            ));
            Msg.playPersonalPackSound(sender, "game.green_release", 0.85f, 1.15f);
            Msg.playPersonalPackSound(target, "game.green_release", 0.85f, 1.15f);
            return;
        }

        pendingFriendRequests.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
            .add(sender.getUniqueId());
        Msg.send(sender, "<green>✓ ᴅᴇᴍᴀɴᴅᴇ ᴅ'ᴀᴍɪ ᴇɴᴠᴏʏᴇᴇ ᴀ <white>" + target.getName() + "</white> !</green>");
        Msg.sendFramedCard(target, "Demande d'Ami", List.of(
            "<white><bold>" + sender.getName() + "</bold></white> <gray>ꜱᴏᴜʜᴀɪᴛᴇ ᴅᴇᴠᴇɴɪʀ ᴠᴏᴛʀᴇ ᴀᴍɪ !</gray> "
                + "<click:run_command:'/bb friend add " + sender.getName() + "'><hover:show_text:'<green>ᴀᴄᴄᴇᴘᴛᴇʀ</green>'><green><bold>[✓ Accepter]</bold></green></hover></click>"
        ));
        Msg.playPersonalPackSound(target, "ui.click", 0.9f, 1.2f);
    }

    public void removeFriend(Player player, String targetName) {
        PlayerProfile found = statsManager.findByNameIgnoreCase(targetName);
        if (found == null) {
            Msg.send(player, Msg.error("Joueur introuvable : " + targetName));
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
            statsManager.save();
            Msg.send(player, "<yellow>✓ <white>" + targetName + "</white> ᴀ ᴇᴛᴇ ʀᴇᴛɪʀᴇ ᴅᴇ ᴠᴏꜱ ᴀᴍɪꜱ.</yellow>");
        } else {
            Msg.send(player, "<red>ᴄᴇ ᴊᴏᴜᴇᴜʀ ɴ'ᴇꜱᴛ ᴘᴀꜱ ᴅᴀɴꜱ ᴠᴏꜱ ᴀᴍɪꜱ.</red>");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //   GROUP / PARTY SYSTEM (Group X/4 in 3D Locker-Room & Team Matchmaking!)
    // ═══════════════════════════════════════════════════════════════════════════

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
            Msg.send(leader, "<red>ᴠᴏᴜꜱ ᴇᴛᴇꜱ ᴅᴇᴊᴀ ᴅᴀɴꜱ ᴠᴏᴛʀᴇ ɢʀᴏᴜᴘᴇ !</red>");
            return;
        }
        Party party = playerParties.computeIfAbsent(leader.getUniqueId(), Party::new);
        if (!party.leaderId().equals(leader.getUniqueId())) {
            Msg.send(leader, "<red>ꜱᴇᴜʟ ʟᴇ ᴄʜᴇꜰ ᴅᴜ ɢʀᴏᴜᴘᴇ ᴘᴇᴜᴛ ɪɴᴠɪᴛᴇʀ ᴅᴇꜱ ᴊᴏᴜᴇᴜʀꜱ !</red>");
            return;
        }
        if (party.size() >= MAX_PARTY_SIZE) {
            Msg.send(leader, "<red>ᴠᴏᴛʀᴇ ɢʀᴏᴜᴘᴇ ᴇꜱᴛ ᴄᴏᴍᴘʟᴇᴛ (" + MAX_PARTY_SIZE + "/" + MAX_PARTY_SIZE + ") !</red>");
            return;
        }
        pendingPartyInvites.put(target.getUniqueId(), leader.getUniqueId());
        Msg.send(leader, "<aqua>✓ ɪɴᴠɪᴛᴀᴛɪᴏɴ ᴅᴇ ɢʀᴏᴜᴘᴇ ᴇɴᴠᴏʏᴇᴇ ᴀ <white>" + target.getName() + "</white> !</aqua>");
        Msg.sendFramedCard(target, "Groupe BosketBall", List.of(
            "<aqua><bold>" + leader.getName() + "</bold></aqua> <gray>ᴠᴏᴜꜱ ɪɴᴠɪᴛᴇ ᴅᴀɴꜱ ꜱᴏɴ ᴠᴇꜱᴛɪᴀɪʀᴇ 3ᴅ (" + party.size() + "/" + MAX_PARTY_SIZE + ") !</gray> "
                + "<click:run_command:'/bb party join " + leader.getName() + "'><hover:show_text:'<green>ʀᴇᴊᴏɪɴᴅʀᴇ ʟᴇ ɢʀᴏᴜᴘᴇ</green>'><green><bold>[▶ Rejoindre]</bold></green></hover></click>"
        ));
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
            Msg.send(player, Msg.error("Aucune invitation de groupe active trouvée."));
            return;
        }
        acceptPartyInvite(player, leader);
    }

    public void acceptPartyInvite(Player player, Player leader) {
        UUID expectedLeader = pendingPartyInvites.remove(player.getUniqueId());
        if (expectedLeader == null || !expectedLeader.equals(leader.getUniqueId())) {
            Msg.send(player, "<red>ᴀᴜᴄᴜɴᴇ ɪɴᴠɪᴛᴀᴛɪᴏɴ ᴇɴ ᴀᴛᴛᴇɴᴛᴇ ᴅᴇ <white>" + leader.getName() + "</white>.</red>");
            return;
        }
        leaveParty(player, true);
        Party party = playerParties.computeIfAbsent(leader.getUniqueId(), Party::new);
        if (party.size() >= MAX_PARTY_SIZE) {
            Msg.send(player, "<red>ᴄᴇ ɢʀᴏᴜᴘᴇ ᴇꜱᴛ ᴍᴀɪɴᴛᴇɴᴀɴᴛ ᴄᴏᴍᴘʟᴇᴛ !</red>");
            return;
        }
        party.members.add(player.getUniqueId());
        playerParties.put(player.getUniqueId(), party);
        broadcastParty(party, "<aqua>✦ <white><bold>" + player.getName() + "</bold></white> ᴀ ʀᴇᴊᴏɪɴᴛ ʟᴇ ɢʀᴏᴜᴘᴇ (" + party.size() + "/" + MAX_PARTY_SIZE + ") !</aqua>");
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
                Msg.send(player, "<gray>ᴠᴏᴜꜱ ɴ'ᴇᴛᴇꜱ ᴅᴀɴꜱ ᴀᴜᴄᴜɴ ɢʀᴏᴜᴘᴇ.</gray>");
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
            Msg.send(player, "<yellow>✓ ᴠᴏᴜꜱ ᴀᴠᴇᴢ ǫᴜɪᴛᴛᴇ ʟᴇ ɢʀᴏᴜᴘᴇ.</yellow>");
            broadcastParty(party, "<yellow>" + player.getName() + " ᴀ ǫᴜɪᴛᴛᴇ ʟᴇ ɢʀᴏᴜᴘᴇ.</yellow>");
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
