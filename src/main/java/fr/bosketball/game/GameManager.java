package fr.bosketball.game;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.ArenaBuilder;
import fr.bosketball.model.TeamSide;
import fr.bosketball.ui.LockerRoom3dMenuManager;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Coordinates all active MatchSessions across all Stadium Arenas, the Live 3D Waiting-Room
 * Matchmaking Queue (exact 202328.png "• QUEUED <MODE> 0:01" flow!), and 1v1 Duel Invitations!
 */
public final class GameManager {

    public record QueueTicket(UUID playerId, GameModeType mode, long queuedAtMillis) {}

    private final BosketBallPlugin plugin;
    private final Map<String, MatchSession> sessionsByArena = new HashMap<>();
    private final Map<UUID, QueueTicket> matchmakingQueue = new LinkedHashMap<>();
    private final Map<UUID, UUID> pendingDuelChallengerByTarget = new HashMap<>();

    public GameManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public Collection<MatchSession> activeSessions() {
        return sessionsByArena.values();
    }

    public MatchSession getSessionByArena(String arenaId) {
        return arenaId == null ? null : sessionsByArena.get(arenaId.toLowerCase());
    }

    public MatchSession getSessionOf(UUID playerId) {
        for (MatchSession s : sessionsByArena.values()) {
            if (s.hasPlayer(playerId) && s.state() != MatchSession.State.FINISHED) {
                return s;
            }
        }
        return null;
    }

    public QueueTicket getQueueTicket(UUID playerId) {
        return playerId == null ? null : matchmakingQueue.get(playerId);
    }

    public GameModeType getQueuedMode(UUID playerId) {
        QueueTicket t = getQueueTicket(playerId);
        return t != null ? t.mode() : null;
    }

    public long getQueueElapsedSeconds(UUID playerId) {
        QueueTicket t = getQueueTicket(playerId);
        if (t == null) {
            return 0L;
        }
        return Math.max(1L, (System.currentTimeMillis() - t.queuedAtMillis()) / 1000L + 1L);
    }

    public boolean isQueued(UUID playerId) {
        return playerId != null && matchmakingQueue.containsKey(playerId);
    }

    public List<Player> queuedOnlinePlayers(GameModeType filterMode) {
        List<Player> out = new ArrayList<>();
        for (QueueTicket t : matchmakingQueue.values()) {
            if (filterMode != null && t.mode() != filterMode) {
                continue;
            }
            Player p = Bukkit.getServer() != null ? Bukkit.getPlayer(t.playerId()) : null;
            if (p != null && p.isOnline()) {
                out.add(p);
            }
        }
        return out;
    }

    public int activePlayerCount() {
        int sum = 0;
        for (MatchSession s : sessionsByArena.values()) {
            if (s.state() != MatchSession.State.FINISHED) {
                sum += s.participantCount();
            }
        }
        return sum;
    }

    public boolean isInMatch(UUID playerId) {
        return getSessionOf(playerId) != null;
    }

    public int queuedCount() {
        int sum = matchmakingQueue.size();
        for (MatchSession s : sessionsByArena.values()) {
            if (s.state() == MatchSession.State.WAITING) {
                sum += s.participantCount();
            }
        }
        return sum;
    }

    public int queuedCount(GameModeType mode) {
        int sum = 0;
        for (QueueTicket t : matchmakingQueue.values()) {
            if (t.mode() == mode) {
                sum++;
            }
        }
        for (MatchSession s : sessionsByArena.values()) {
            if (s.mode() == mode && s.state() == MatchSession.State.WAITING) {
                sum += s.participantCount();
            }
        }
        return sum;
    }

    public void startPractice(Player player) {
        leaveQueue(player, false);
        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().close(player, true);
        }
        quickJoin(player, GameModeType.PRACTICE, null);
    }

    /**
     * Enters (or toggles) the Live 3D Waiting-Room Matchmaking Queue for competitive team modes
     * (1v1, 2v2, 3v3, 5v5) just like 202328.png ("• QUEUED 2V2 0:01" + green chat line),
     * or launches solo modes (Practice / 3-Point Contest) immediately on court!
     */
    public void joinQueue(Player player, GameModeType mode) {
        if (player == null || mode == null) {
            return;
        }
        // Solo modes go straight to the court!
        if (!mode.competitiveTeamMatch()) {
            leaveQueue(player, false);
            if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                plugin.lockerRoom3dManager().close(player, true);
            }
            quickJoin(player, mode, null);
            return;
        }

        // Toggle off if clicking the exact same mode while already queued for it!
        QueueTicket existingTicket = matchmakingQueue.get(player.getUniqueId());
        if (existingTicket != null && existingTicket.mode() == mode) {
            leaveQueue(player, true);
            return;
        }

        // Leave any active court session first
        MatchSession curSession = getSessionOf(player.getUniqueId());
        if (curSession != null) {
            curSession.removePlayer(player, true);
        }

        // Open the 3D Waiting Stage automatically if the player queued from /bb or /bb play!
        if (plugin.lockerRoom3dManager() != null && !plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().open(player, LockerRoom3dMenuManager.TopTab.PLAY);
        }

        long now = System.currentTimeMillis();
        enqueueSinglePlayer(player, mode, now);

        // If player leads a Party, pull online Party members who aren't in a match into the same Waiting Room queue!
        if (plugin.socialManager() != null) {
            var party = plugin.socialManager().getParty(player.getUniqueId());
            if (party != null && party.isLeader(player.getUniqueId())) {
                for (Player mate : plugin.socialManager().onlinePartyMembers(player.getUniqueId())) {
                    if (!mate.getUniqueId().equals(player.getUniqueId()) && !isInMatch(mate.getUniqueId())) {
                        if (plugin.lockerRoom3dManager() != null && !plugin.lockerRoom3dManager().isIn3dMenu(mate.getUniqueId())) {
                            plugin.lockerRoom3dManager().open(mate, LockerRoom3dMenuManager.TopTab.PLAY);
                        }
                        enqueueSinglePlayer(mate, mode, now);
                    }
                }
            }
        }

        if (plugin.lockerRoom3dManager() != null) {
            plugin.lockerRoom3dManager().refreshAllOpenStages(true);
        }

        // Check if an existing WAITING court session can absorb or if enough players are queued to launch!
        tryLaunchReadyMatch(mode, false);
    }

    private void enqueueSinglePlayer(Player p, GameModeType mode, long timestamp) {
        matchmakingQueue.put(p.getUniqueId(), new QueueTicket(p.getUniqueId(), mode, timestamp));
        int cur = countTicketsForMode(mode);
        int target = mode.maxPlayers();
        p.sendMessage(Msg.parse("<#52FFB8>En file d'attente pour <white><bold>Classé " + mode.id()
            + "</bold></white> <#A8FFB8>(" + cur + "/" + target + ")</#A8FFB8>. "
            + "<gray>/bb leave pour annuler • /bb forcestart pour lancer immédiatement.</gray></#52FFB8>"));
        Msg.playPersonalPackSound(p, "ui.click", 0.9f, 1.25f);
    }

    private int countTicketsForMode(GameModeType mode) {
        int c = 0;
        for (QueueTicket t : matchmakingQueue.values()) {
            if (t.mode() == mode) {
                c++;
            }
        }
        return c;
    }

    public boolean leaveQueue(Player player, boolean notify) {
        if (player == null) {
            return false;
        }
        QueueTicket removed = matchmakingQueue.remove(player.getUniqueId());
        if (removed != null) {
            if (notify) {
                player.sendMessage(Msg.parse("<#FF8A7A>File d'attente <white>" + removed.mode().id() + "</white> annulée.</#FF8A7A>"));
                Msg.playPersonalPackSound(player, "ui.click", 0.8f, 0.85f);
            }
            if (plugin.lockerRoom3dManager() != null) {
                plugin.lockerRoom3dManager().refreshAllOpenStages(true);
            }
            return true;
        }
        return false;
    }

    /**
     * Launches a MatchSession on a free court when the Waiting-Room queue reaches capacity
     * (or immediately with 1+ players when forceLaunch == true, e.g. /bb forcestart or "LANCER MAINTENANT"!).
     */
    public boolean tryLaunchReadyMatch(GameModeType mode, boolean forceLaunch) {
        List<Player> candidates = queuedOnlinePlayers(mode);
        int required = forceLaunch ? 1 : mode.maxPlayers();
        if (candidates.size() < required) {
            return false;
        }

        int takeCount = Math.min(candidates.size(), mode.maxPlayers());
        List<Player> selected = new ArrayList<>(candidates.subList(0, takeCount));

        Arena freeArena = findOrCreateFreeArena(selected.get(0));
        if (freeArena == null) {
            for (Player p : selected) {
                Msg.send(p, "<red>ᴛᴏᴜꜱ ʟᴇꜱ ᴛᴇʀʀᴀɪɴꜱ ꜱᴏɴᴛ ᴀᴄᴛᴜᴇʟʟᴇᴍᴇɴᴛ ᴇɴ ᴍᴀᴛᴄʜ ! ᴄʀᴇᴇᴢ-ᴇɴ ᴜɴ ᴀᴠᴇᴄ <yellow>/ʙʙ ᴀʀᴇɴᴀ ʙᴜɪʟᴅ <ɴᴏᴍ></yellow>.</red>");
            }
            return false;
        }

        // Remove from queue & cleanly exit 3D Waiting Stage (restoring returnLocation BEFORE MatchSession saves it!)
        for (Player p : selected) {
            matchmakingQueue.remove(p.getUniqueId());
            if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
                plugin.lockerRoom3dManager().close(p, true);
            }
        }

        MatchSession session = new MatchSession(plugin, freeArena, mode);
        sessionsByArena.put(freeArena.id(), session);
        for (int i = 0; i < selected.size(); i++) {
            TeamSide side = (i % 2 == 0) ? TeamSide.IGNIS : TeamSide.FROST;
            session.addPlayer(selected.get(i), side);
        }
        if (forceLaunch && session.state() == MatchSession.State.WAITING) {
            session.forceStart();
        }
        if (plugin.lockerRoom3dManager() != null) {
            plugin.lockerRoom3dManager().refreshAllOpenStages(true);
        }
        return true;
    }

    public void leaveAll(Player player, boolean notify) {
        boolean leftQueue = leaveQueue(player, notify);
        MatchSession s = getSessionOf(player.getUniqueId());
        if (s != null) {
            s.removePlayer(player, !notify);
        } else if (!leftQueue && notify && plugin.lockerRoom3dManager() != null
            && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().close(player, true);
        }
    }

    public void spectateFirstActive(Player player) {
        leaveQueue(player, false);
        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().close(player, true);
        }
        for (MatchSession s : sessionsByArena.values()) {
            if (s.state() != MatchSession.State.FINISHED) {
                s.addPlayer(player, TeamSide.SPECTATOR);
                return;
            }
        }
        Msg.send(player, Msg.warn("Aucun match en direct à observer pour l'instant !"));
    }

    public void tickAll() {
        // Clean up any offline tickets in matchmaking queue
        matchmakingQueue.entrySet().removeIf(e -> {
            Player p = Bukkit.getServer() != null ? Bukkit.getPlayer(e.getKey()) : null;
            return p == null || !p.isOnline();
        });

        var it = sessionsByArena.entrySet().iterator();
        while (it.hasNext()) {
            MatchSession s = it.next().getValue();
            if (s.state() == MatchSession.State.FINISHED) {
                it.remove();
                continue;
            }
            s.tick();
        }
    }

    private Arena findOrCreateFreeArena(Player initiator) {
        Arena freeArena = null;
        for (Arena a : plugin.arenaManager().allArenas()) {
            MatchSession s = sessionsByArena.get(a.id());
            if (s == null || s.state() == MatchSession.State.FINISHED) {
                freeArena = a;
                break;
            }
        }
        if (freeArena == null && plugin.arenaManager().allArenas().isEmpty() && initiator != null) {
            Msg.send(initiator, "<yellow>🏗 ɢᴇɴᴇʀᴀᴛɪᴏɴ ᴀᴜᴛᴏᴍᴀᴛɪǫᴜᴇ ᴅᴜ <bold>ꜱᴛᴀᴅᴇ ʙᴏꜱᴋᴇᴛʙᴀʟʟ ᴀʀᴇɴᴀ</bold>...</yellow>");
            freeArena = ArenaBuilder.buildFullStadium("main", "ʙᴏꜱᴋᴇᴛ ᴄᴇɴᴛᴇʀ", initiator.getLocation().add(0, -1, 22));
            plugin.arenaManager().registerArena(freeArena);
        }
        return freeArena;
    }

    /**
     * Joins an existing waiting session for the requested mode, or starts one on a free Arena
     * directly on the court (used for Practice, 3PT Contest, Duels, or explicit /bb join <arena>).
     */
    public void quickJoin(Player player, GameModeType mode, String specificArenaId) {
        leaveQueue(player, false);
        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().close(player, true);
        }

        MatchSession current = getSessionOf(player.getUniqueId());
        if (current != null) {
            current.removePlayer(player, true);
        }

        // 1. Check specific arena if requested
        if (specificArenaId != null) {
            Arena target = plugin.arenaManager().getArena(specificArenaId);
            if (target == null) {
                Msg.send(player, "<red>ᴀʀᴇɴᴇ ɪɴᴛʀᴏᴜᴠᴀʙʟᴇ: <white>" + specificArenaId + "</white></red>");
                return;
            }
            MatchSession existing = sessionsByArena.get(target.id());
            if (existing == null || existing.state() == MatchSession.State.FINISHED) {
                existing = new MatchSession(plugin, target, mode);
                sessionsByArena.put(target.id(), existing);
            }
            existing.addPlayer(player, null);
            return;
        }

        // 2. Join waiting session of same mode
        if (mode.competitiveTeamMatch()) {
            for (MatchSession s : sessionsByArena.values()) {
                if (s.mode() == mode && s.state() == MatchSession.State.WAITING && s.participantCount() < mode.maxPlayers()) {
                    s.addPlayer(player, null);
                    return;
                }
            }
        }

        // 3. Find any free arena
        Arena freeArena = findOrCreateFreeArena(player);
        if (freeArena == null) {
            Msg.send(player, "<red>ᴛᴏᴜꜱ ʟᴇꜱ ᴛᴇʀʀᴀɪɴꜱ ꜱᴏɴᴛ ᴀᴄᴛᴜᴇʟʟᴇᴍᴇɴᴛ ᴇɴ ᴍᴀᴛᴄʜ ! ᴄʀᴇᴇᴢ-ᴇɴ ᴜɴ ᴀᴠᴇᴄ <yellow>/ʙʙ ᴀʀᴇɴᴀ ʙᴜɪʟᴅ <ɴᴏᴍ></yellow>.</red>");
            return;
        }

        MatchSession session = new MatchSession(plugin, freeArena, mode);
        sessionsByArena.put(freeArena.id(), session);
        session.addPlayer(player, TeamSide.IGNIS);
    }

    public void sendOrAcceptDuel(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            Msg.send(sender, "<red>ᴠᴏᴜꜱ ɴᴇ ᴘᴏᴜᴠᴇᴢ ᴘᴀꜱ ᴠᴏᴜꜱ ᴅᴇꜰɪᴇʀ ᴠᴏᴜꜱ-ᴍᴇᴍᴇ !</red>");
            return;
        }

        // Check if sender is accepting an incoming duel from target!
        UUID challengerOfSender = pendingDuelChallengerByTarget.get(sender.getUniqueId());
        if (challengerOfSender != null && challengerOfSender.equals(target.getUniqueId())) {
            pendingDuelChallengerByTarget.remove(sender.getUniqueId());
            quickJoin(target, GameModeType.DUEL_1V1, null);
            MatchSession duelSession = getSessionOf(target.getUniqueId());
            if (duelSession != null) {
                duelSession.addPlayer(sender, TeamSide.FROST);
            }
            return;
        }

        pendingDuelChallengerByTarget.put(target.getUniqueId(), sender.getUniqueId());
        Msg.send(sender, "<green>⚔ ᴅᴇꜰɪ 1ᴠ1 ᴇɴᴠᴏʏᴇ ᴀ <white><bold>" + target.getName() + "</bold></white> !</green>");
        Msg.send(target, "<gold>⚔ <white><bold>" + sender.getName() + "</bold></white> ᴠᴏᴜꜱ ᴅᴇꜰɪᴇ ᴇɴ ᴅᴜᴇʟ 1ᴠ1 !</gold> "
            + "<click:run_command:'/bb duel " + sender.getName() + "'><hover:show_text:'<green>ᴄʟɪǫᴜᴇʀ ᴘᴏᴜʀ ᴀᴄᴄᴇᴘᴛᴇʀ ʟᴇ 1ᴠ1 !</green>'>"
            + "<green><bold>[✓ ᴀᴄᴄᴇᴘᴛᴇʀ ʟᴇ ᴅᴜᴇʟ]</bold></green></hover></click>");
        Msg.playPersonalPackSound(target, "ui.click", 1.0f, 1.25f);
    }

    public void shutdownAll() {
        matchmakingQueue.clear();
        for (MatchSession s : sessionsByArena.values()) {
            s.stopAndCleanup();
        }
        sessionsByArena.clear();
    }
}
