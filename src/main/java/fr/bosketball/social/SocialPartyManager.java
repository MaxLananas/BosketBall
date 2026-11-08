package fr.bosketball.social;

import fr.bosketball.util.Palette;
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

public final class SocialPartyManager {

    public static final int MAX_PARTY_SIZE = 4;
    public static final int MAX_TEAM_NAME_LENGTH = 16;

    public enum ChatPromptType {
        PARTY_TEAM_NAME,
        ADD_FRIEND_NAME,
        GUILD_CREATE,
        GUILD_INVITE,
        GUILD_MOTD,
        GUILD_DESCRIPTION,
        GUILD_TAG
    }

    public static final class Party {
        private volatile UUID leaderId;
        private volatile String teamName = "";
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

        public String teamName() {
            return teamName;
        }

        public boolean hasCustomTeamName() {
            return teamName != null && !teamName.isBlank();
        }

        public void setTeamName(String sanitized) {
            this.teamName = sanitized == null ? "" : sanitized;
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
    private final Map<UUID, ChatPromptType> pendingChatPrompts = new ConcurrentHashMap<>();

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

            clearPendingFriendRequest(sender.getUniqueId(), target.getUniqueId());
            clearPendingFriendRequest(target.getUniqueId(), sender.getUniqueId());
            Msg.send(sender, Msg.info("<#F8FAFC>" + target.getName() + "</#F8FAFC> is already on your friends list."));
            return;
        }

        Set<UUID> incomingToSender = pendingFriendRequests.getOrDefault(sender.getUniqueId(), Set.of());
        if (incomingToSender.contains(target.getUniqueId())) {
            incomingToSender.remove(target.getUniqueId());
            clearPendingFriendRequest(target.getUniqueId(), sender.getUniqueId());
            sProf.addFriend(target.getUniqueId());
            tProf.addFriend(sender.getUniqueId());
            statsManager.saveProfileAsync(sProf);
            statsManager.saveProfileAsync(tProf);

            Msg.send(sender, Msg.success("You are now friends with <#F8FAFC><bold>" + target.getName() + "</bold></#F8FAFC>."));
            Msg.send(target, Msg.success("<#F8FAFC><bold>" + sender.getName() + "</bold></#F8FAFC> accepted your friend request."));
            Msg.playPersonalPackSound(sender, "game.green_release", 0.85f, 1.15f);
            Msg.playPersonalPackSound(target, "game.green_release", 0.85f, 1.15f);
            return;
        }

        pendingFriendRequests.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
            .add(sender.getUniqueId());
        mailIfOffline(target.getUniqueId(), __ -> new OfflineMail(
            OfflineMail.Kind.FRIEND_REQUEST,
            sender.getName(),
            sender.getName() + " wants to be your friend",
            List.of(
                "<#94A3B8><#F8FAFC>" + sender.getName() + "</#F8FAFC> sent you a friend request while you were away.</#94A3B8>",
                "<#94A3B8>Use <#FDE047>/friend add " + sender.getName() + "</#FDE047> to accept.</#94A3B8>"),
            System.currentTimeMillis(),
            "friend add " + sender.getName()));
        Msg.sendSocial(sender, Msg.success("Friend request sent to <#F8FAFC>" + target.getName() + "</#F8FAFC>."));
        Msg.sendSocial(target, "<#F8FAFC><bold>" + sender.getName() + "</bold></#F8FAFC> <" + Palette.MUTED + ">sent you a friend request! </" + Palette.MUTED + ">"
            + "<click:run_command:'/friend add " + sender.getName() + "'><hover:show_text:'<#4ADE80>Click to accept friend request</#4ADE80>'><" + Palette.SUCCESS + "><bold>[✔ Accept]</bold></" + Palette.SUCCESS + "></hover></click> "
            + "<click:run_command:'/friend decline " + sender.getName() + "'><hover:show_text:'<#F87171>Click to decline</#F87171>'><" + Palette.DANGER + "><bold>[✖ Decline]</bold></" + Palette.DANGER + "></hover></click>");
        Msg.playPersonalPackSound(target, "ui.click", 0.9f, 1.2f);
    }

    private void mailIfOffline(UUID targetId, java.util.function.Function<UUID, OfflineMail> factory) {
        if (targetId == null || org.bukkit.Bukkit.getPlayer(targetId) != null) {
            return;
        }
        var service = fr.bosketball.BosketBallPlugin.getInstance().socialService();
        if (service == null) {
            return;
        }
        var profile = statsManager.getProfile(targetId);
        if (profile == null) {
            return;
        }
        service.deliver(profile, factory.apply(targetId));
    }

    public Set<UUID> incomingFriendRequests(UUID playerId) {
        Set<UUID> set = pendingFriendRequests.get(playerId);
        if (set == null || set.isEmpty()) {
            return Set.of();
        }
        PlayerProfile profile = statsManager.getProfile(playerId);
        if (profile == null) {
            return Collections.unmodifiableSet(set);
        }
        boolean purged = false;
        for (UUID requester : new java.util.ArrayList<>(set)) {
            if (profile.isFriend(requester)) {
                set.remove(requester);
                purged = true;
            }
        }
        if (purged && set.isEmpty()) {
            pendingFriendRequests.remove(playerId);
        }
        return set.isEmpty() ? Set.of() : Collections.unmodifiableSet(set);
    }

    public void acceptFriendRequest(Player player, Player requester) {
        if (player == null || requester == null) {
            return;
        }
        PlayerProfile pProf = statsManager.getOrCreate(player);
        PlayerProfile rProf = statsManager.getOrCreate(requester);
        clearPendingFriendRequest(player.getUniqueId(), requester.getUniqueId());
        clearPendingFriendRequest(requester.getUniqueId(), player.getUniqueId());
        if (pProf.isFriend(requester.getUniqueId())) {
            Msg.send(player, Msg.info("<#F8FAFC>" + requester.getName() + "</#F8FAFC> is already on your friends list."));
            return;
        }
        pProf.addFriend(requester.getUniqueId());
        rProf.addFriend(player.getUniqueId());
        statsManager.saveProfileAsync(pProf);
        statsManager.saveProfileAsync(rProf);
        Msg.send(player, Msg.success("You are now friends with <#F8FAFC><bold>" + requester.getName() + "</bold></#F8FAFC>."));
        Msg.send(requester, Msg.success("<#F8FAFC><bold>" + player.getName() + "</bold></#F8FAFC> accepted your friend request."));
        Msg.playPersonalPackSound(player, "game.green_release", 0.85f, 1.15f);
        Msg.playPersonalPackSound(requester, "game.green_release", 0.85f, 1.15f);
    }

    private void clearPendingFriendRequest(UUID receiverId, UUID requesterId) {
        Set<UUID> inbox = pendingFriendRequests.get(receiverId);
        if (inbox != null) {
            inbox.remove(requesterId);
            if (inbox.isEmpty()) {
                pendingFriendRequests.remove(receiverId);
            }
        }
    }

    public void declineFriendRequest(Player player, String requesterName) {
        PlayerProfile found = statsManager.findByNameIgnoreCase(requesterName);
        if (found == null) {
            Msg.send(player, Msg.error("No pending friend request from: " + requesterName));
            return;
        }
        declineFriendRequest(player, found.uuid(), found.lastKnownName());
    }

    public void declineFriendRequest(Player player, UUID requesterId, String requesterName) {
        Set<UUID> set = pendingFriendRequests.get(player.getUniqueId());
        if (set != null && set.remove(requesterId)) {
            Msg.send(player, Msg.info("Declined friend request from <#F8FAFC>" + requesterName + "</#F8FAFC>."));
        } else {
            Msg.send(player, Msg.error("No pending friend request from <#F8FAFC>" + requesterName + "</#F8FAFC>."));
        }
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
            Msg.send(player, Msg.info("Removed <#F8FAFC>" + targetName + "</#F8FAFC> from your friends list."));
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

    public void sendPartyChat(Player sender, String rawMessage) {
        Party party = playerParties.get(sender.getUniqueId());
        if (party == null) {
            Msg.send(sender, Msg.warn("You are not in a party — <#F8FAFC>/pc</#F8FAFC> is for party members only."));
            return;
        }
        String clean = Msg.escapeMini(rawMessage == null ? "" : rawMessage).trim();
        if (clean.isEmpty()) {
            return;
        }
        String line = "<" + Palette.ICE + ">ᴘᴀʀᴛʏ</" + Palette.ICE + "> <#64748B>│</#64748B> <#F8FAFC>" + sender.getName()
            + "</#F8FAFC><#64748B>:</#64748B> <" + Palette.TEXT + ">" + clean + "</" + Palette.TEXT + ">";
        List<Player> recipients = onlinePartyMembers(sender.getUniqueId());
        for (Player member : recipients) {
            Msg.sendSocial(member, line);
        }
        // Round 27: staff must be able to read every channel, party chat included.
        fr.bosketball.util.ChatSpy.relay(
            "<" + Palette.ICE + ">ᴘᴀʀᴛʏ</" + Palette.ICE + ">", sender.getName(), clean,
            fr.bosketball.util.ChatSpy.seenBy(recipients));
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
            Msg.send(leader, Msg.error("<#F8FAFC>" + target.getName() + "</#F8FAFC> has party invites disabled."));
            return;
        }
        pendingPartyInvites.put(target.getUniqueId(), leader.getUniqueId());
        mailIfOffline(target.getUniqueId(), __ -> new OfflineMail(
            OfflineMail.Kind.PARTY_INVITE,
            leader.getName(),
            leader.getName() + " invited you to a party",
            List.of(
                "<#94A3B8><#F8FAFC>" + leader.getName() + "</#F8FAFC> invited you to their party ("
                    + party.size() + "/" + MAX_PARTY_SIZE + ").</#94A3B8>",
                "<#94A3B8>Use <#FDE047>/party join " + leader.getName() + "</#FDE047> to join.</#94A3B8>"),
            System.currentTimeMillis(),
            "party join " + leader.getName()));
        Msg.sendSocial(leader, Msg.success("Invited <#F8FAFC>" + target.getName() + "</#F8FAFC> to your party."));
        Msg.sendSocial(target, "<" + Palette.ICE + "><bold>" + leader.getName() + "</bold></" + Palette.ICE + "> <" + Palette.MUTED + ">invited you to their party (" + party.size() + "/" + MAX_PARTY_SIZE + ")! </" + Palette.MUTED + ">"
            + "<click:run_command:'/party join " + leader.getName() + "'><hover:show_text:'<#4ADE80>Join party</#4ADE80>'><" + Palette.SUCCESS + "><bold>[▶ Join Party]</bold></" + Palette.SUCCESS + "></hover></click>");
        Msg.playPersonalPackSound(target, "ui.click", 0.9f, 1.25f);
    }

    public UUID pendingPartyInviteLeader(UUID targetId) {
        return targetId != null ? pendingPartyInvites.get(targetId) : null;
    }

    public void declinePartyInvite(Player player) {
        if (player == null) {
            return;
        }
        UUID removed = pendingPartyInvites.remove(player.getUniqueId());
        if (removed != null) {
            Msg.send(player, Msg.info("Declined party invitation."));
            Msg.playPersonalPackSound(player, "ui.click", 0.75f, 0.9f);
        }
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
            Msg.send(player, Msg.error("No pending party invite from <#F8FAFC>" + leader.getName() + "</#F8FAFC>."));
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
        broadcastParty(party, Msg.info("<#F8FAFC><bold>" + player.getName() + "</bold></#F8FAFC> joined the party (" + party.size() + "/" + MAX_PARTY_SIZE + ")."));
    }

    public void leaveParty(Player player) {
        leaveParty(player, false);
    }

    public static String sanitizeTeamName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '\'') {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_TEAM_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_TEAM_NAME_LENGTH).trim();
        }
        return cleaned;
    }

    public boolean setPartyTeamNameDirect(UUID playerId, String rawName) {
        Party party = playerParties.computeIfAbsent(playerId, Party::new);
        if (!party.isLeader(playerId)) {
            return false;
        }
        if (rawName != null && (rawName.equalsIgnoreCase("clear") || rawName.equalsIgnoreCase("reset") || rawName.equalsIgnoreCase("off"))) {
            party.setTeamName("");
            return true;
        }
        String clean = sanitizeTeamName(rawName);
        if (clean.length() < 2) {
            return false;
        }
        party.setTeamName(clean);
        return true;
    }

    public void setPartyTeamName(Player player, String rawName) {
        Party existing = playerParties.get(player.getUniqueId());
        if (existing != null && !existing.isLeader(player.getUniqueId())) {
            Msg.send(player, Msg.error("Only the party leader can rename the team."));
            return;
        }
        if (rawName != null && (rawName.equalsIgnoreCase("clear") || rawName.equalsIgnoreCase("reset"))) {
            setPartyTeamNameDirect(player.getUniqueId(), "reset");
            Msg.send(player, Msg.info("Party team name reset to default."));
            return;
        }
        String clean = sanitizeTeamName(rawName);
        if (clean.length() < 2) {
            Msg.send(player, Msg.error("Team name must be between <#F8FAFC>2</#F8FAFC> and <#F8FAFC>" + MAX_TEAM_NAME_LENGTH + "</#F8FAFC> letters/numbers."));
            return;
        }
        Party party = playerParties.computeIfAbsent(player.getUniqueId(), Party::new);
        party.setTeamName(clean);
        broadcastParty(party, Msg.success("Party Team Name set to <" + Palette.GOLD + "><bold>" + clean + "</bold></" + Palette.GOLD + ">!"));
        Msg.playPersonalPackSound(player, "game.green_release", 0.85f, 1.2f);
    }

    public String resolveCustomTeamNameForRoster(java.util.Collection<UUID> roster) {
        if (roster == null || roster.isEmpty()) {
            return null;
        }
        for (UUID id : roster) {
            Party p = playerParties.get(id);
            if (p != null && p.hasCustomTeamName()) {
                return p.teamName();
            }
        }
        return null;
    }

    public void kickFromParty(Player leader, UUID targetId) {
        Party party = playerParties.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            Msg.send(leader, Msg.error("Only the party leader can remove members."));
            return;
        }
        if (leader.getUniqueId().equals(targetId) || !party.members.contains(targetId)) {
            return;
        }
        party.members.remove(targetId);
        playerParties.remove(targetId);
        Player target = Bukkit.getPlayer(targetId);
        String targetName = target != null ? target.getName() : "Player";
        if (target != null && target.isOnline()) {
            Msg.send(target, Msg.warn("You were removed from the party."));
        }
        broadcastParty(party, Msg.info("<#F8FAFC>" + targetName + "</#F8FAFC> was removed from the party."));
    }

    public void promotePartyLeader(Player leader, UUID newLeaderId) {
        Party party = playerParties.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId()) || !party.members.contains(newLeaderId)) {
            return;
        }
        party.setLeaderId(newLeaderId);
        Player promoted = Bukkit.getPlayer(newLeaderId);
        String name = promoted != null ? promoted.getName() : "Player";
        broadcastParty(party, Msg.success("<" + Palette.GOLD + "><bold>" + name + "</bold></" + Palette.GOLD + "> is now the Party Leader!"));
    }

    public void beginGuildNamePrompt(Player player) {
        beginChatPrompt(player, ChatPromptType.GUILD_CREATE);
    }

    public void beginGuildInvitePrompt(Player player) {
        beginChatPrompt(player, ChatPromptType.GUILD_INVITE);
    }

    public void beginGuildMotdPrompt(Player player) {
        beginChatPrompt(player, ChatPromptType.GUILD_MOTD);
    }

    public void beginGuildDescriptionPrompt(Player player) {
        beginChatPrompt(player, ChatPromptType.GUILD_DESCRIPTION);
    }

    public void beginGuildTagPrompt(Player player) {
        beginChatPrompt(player, ChatPromptType.GUILD_TAG);
    }

    public boolean hasPendingChatPrompt(UUID playerId) {
        return playerId != null && pendingChatPrompts.containsKey(playerId);
    }

    public void beginChatPrompt(Player player, ChatPromptType type) {
        pendingChatPrompts.put(player.getUniqueId(), type);
        player.closeInventory();
        if (type == ChatPromptType.PARTY_TEAM_NAME) {
            Msg.send(player, Msg.info("Type your new <" + Palette.GOLD + "><bold>Party Team Name</bold></" + Palette.GOLD + "> in chat (2–16 chars), or type <#F8FAFC>cancel</#F8FAFC>:"));
        } else if (type == ChatPromptType.ADD_FRIEND_NAME) {
            Msg.send(player, Msg.info("Type the <" + Palette.SUCCESS + "><bold>Player Name</bold></" + Palette.SUCCESS + "> you want to add as a friend, or type <#F8FAFC>cancel</#F8FAFC>:"));
        } else if (type == ChatPromptType.GUILD_CREATE) {
            Msg.send(player, Msg.info("Type your <" + Palette.GOLD + "><bold>Guild Name</bold></" + Palette.GOLD + "> in chat (3–16 chars), or type <#F8FAFC>cancel</#F8FAFC>:"));
        } else if (type == ChatPromptType.GUILD_INVITE) {
            Msg.send(player, Msg.info("Type the <" + Palette.SUCCESS + "><bold>Player Name</bold></" + Palette.SUCCESS + "> to invite to your guild, or type <#F8FAFC>cancel</#F8FAFC>:"));
        } else if (type == ChatPromptType.GUILD_MOTD) {
            Msg.send(player, Msg.info("Type the new <" + Palette.ICE + "><bold>Guild MOTD</bold></" + Palette.ICE + "> (max 64 chars), or type <#F8FAFC>cancel</#F8FAFC>:"));
        } else if (type == ChatPromptType.GUILD_DESCRIPTION) {
            Msg.send(player, Msg.info("Type a short <" + Palette.VIOLET + "><bold>Guild Description</bold></" + Palette.VIOLET + "> (max 96 chars), or type <#F8FAFC>cancel</#F8FAFC>:"));
        } else if (type == ChatPromptType.GUILD_TAG) {
            Msg.send(player, Msg.info("Type the new <" + Palette.GOLD + "><bold>Guild Tag</bold></" + Palette.GOLD + "> (2-6 letters, A-Z), or type <#F8FAFC>cancel</#F8FAFC>:"));
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.85f, 1.25f);
    }

    public boolean tryConsumeChatPrompt(Player player, String rawChat) {
        ChatPromptType type = pendingChatPrompts.remove(player.getUniqueId());
        if (type == null) {
            return false;
        }
        String trimmed = rawChat == null ? "" : rawChat.trim();
        if (trimmed.equalsIgnoreCase("cancel") || trimmed.equalsIgnoreCase("exit")) {
            Msg.send(player, Msg.info("Canceled."));
            return true;
        }
        fr.bosketball.BosketBallPlugin plug = fr.bosketball.BosketBallPlugin.getInstance();
        if (type == ChatPromptType.PARTY_TEAM_NAME) {
            setPartyTeamName(player, trimmed);
            if (plug != null && plug.guiMenu() != null) {
                plug.guiMenu().openPartyMenu(player);
            }
            return true;
        }
        if (type == ChatPromptType.GUILD_CREATE) {
            if (plug != null && plug.guildService() != null) {
                var result = plug.guildService().create(player, trimmed, null);
                Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            }
            if (plug != null && plug.guiMenu() != null) {
                plug.guiMenu().openGuildMenu(player);
            }
            return true;
        }
        if (type == ChatPromptType.GUILD_INVITE) {
            if (plug != null && plug.guildService() != null) {
                var result = plug.guildService().invite(player, trimmed);
                Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            }
            if (plug != null && plug.guiMenu() != null) {
                plug.guiMenu().openGuildMenu(player);
            }
            return true;
        }
        if (type == ChatPromptType.GUILD_MOTD) {
            if (plug != null && plug.guildService() != null) {
                var result = plug.guildService().setMotd(player, trimmed);
                Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            }
            if (plug != null && plug.guiMenu() != null) {
                plug.guiMenu().openGuildMenu(player);
            }
            return true;
        }
        if (type == ChatPromptType.GUILD_DESCRIPTION) {
            if (plug != null && plug.guildService() != null) {
                var result = plug.guildService().setDescription(player, trimmed);
                Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            }
            if (plug != null && plug.guiMenu() != null) {
                plug.guiMenu().openGuildMenu(player);
            }
            return true;
        }
        if (type == ChatPromptType.GUILD_TAG) {
            if (plug != null && plug.guildService() != null) {
                var result = plug.guildService().renameTag(player, trimmed);
                Msg.send(player, result.success() ? Msg.success(result.message()) : Msg.error(result.message()));
            }
            if (plug != null && plug.guiMenu() != null) {
                plug.guiMenu().openGuildMenu(player);
            }
            return true;
        }
        if (type == ChatPromptType.ADD_FRIEND_NAME) {
            Player target = Bukkit.getPlayerExact(trimmed);
            if (target == null) {
                target = Bukkit.getPlayer(trimmed);
            }
            if (target == null || !target.isOnline()) {
                Msg.send(player, Msg.error("Player <#F8FAFC>" + trimmed + "</#F8FAFC> is not online right now."));
            } else {
                sendOrAcceptFriendRequest(player, target);
            }
            if (plug != null && plug.guiMenu() != null) {
                plug.guiMenu().openFriendsMenu(player);
            }
            return true;
        }
        return true;
    }

    public void handleDisconnect(UUID playerId) {
        pendingPartyInvites.remove(playerId);
        pendingPartyInvites.values().removeIf(id -> id.equals(playerId));
        pendingFriendRequests.remove(playerId);
        for (Set<UUID> set : pendingFriendRequests.values()) {
            set.remove(playerId);
        }
        pendingChatPrompts.remove(playerId);
        Party party = playerParties.remove(playerId);
        if (party != null) {
            party.members.remove(playerId);
            if (party.members.size() == 1 && !party.hasCustomTeamName()) {
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
            broadcastParty(party, Msg.info("<#F8FAFC>" + player.getName() + "</#F8FAFC> left the party."));
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
