package fr.bosketball.game;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.ArenaBuilder;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Coordinates all active MatchSessions across Arenas, Matchmaking Queues, and 1v1 Duel Challenges.
 */
public final class GameManager {

    public record QueueTicket(UUID playerId, GameModeType mode, long queuedAtMillis) {}

    private final BosketBallPlugin plugin;
    private final Map<String, MatchSession> sessionsByArena = new ConcurrentHashMap<>();
    private final Map<UUID, QueueTicket> matchmakingQueue = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingDuelChallengerByTarget = new ConcurrentHashMap<>();
    private int tickCounter = 0;

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
        List<QueueTicket> tickets = new ArrayList<>(matchmakingQueue.values());
        tickets.sort(Comparator.comparingLong(QueueTicket::queuedAtMillis));
        List<Player> out = new ArrayList<>(tickets.size());
        for (QueueTicket t : tickets) {
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
     * Enters or toggles the matchmaking queue cleanly without forcing a camera lock that could self-cancel.
     */
    public void joinQueue(Player player, GameModeType mode) {
        if (player == null || mode == null) {
            return;
        }
        // Solo modes launch straight onto the court
        if (!mode.competitiveTeamMatch()) {
            leaveQueue(player, false);
            if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                plugin.lockerRoom3dManager().close(player, true);
            }
            quickJoin(player, mode, null);
            return;
        }

        // Toggle off if already queued for the exact same mode
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

        long now = System.currentTimeMillis();
        enqueueSinglePlayer(player, mode, now);

        // Pull online Party members (if party leader) into the same queue
        if (plugin.socialManager() != null) {
            var party = plugin.socialManager().getParty(player.getUniqueId());
            if (party != null && party.isLeader(player.getUniqueId())) {
                for (Player mate : plugin.socialManager().onlinePartyMembers(player.getUniqueId())) {
                    if (!mate.getUniqueId().equals(player.getUniqueId()) && !isInMatch(mate.getUniqueId())) {
                        enqueueSinglePlayer(mate, mode, now);
                    }
                }
            }
        }

        if (plugin.lockerRoom3dManager() != null) {
            plugin.lockerRoom3dManager().refreshAllOpenStages(true);
        }

        tryLaunchReadyMatch(mode, false);
    }

    private void enqueueSinglePlayer(Player p, GameModeType mode, long timestamp) {
        matchmakingQueue.put(p.getUniqueId(), new QueueTicket(p.getUniqueId(), mode, timestamp));
        int cur = countTicketsForMode(mode);
        int target = mode.maxPlayers();

        if (!isInMatch(p.getUniqueId()) && (plugin.lockerRoom3dManager() == null || !plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId()))) {
            p.getInventory().setItem(8, BallSkin.createLeaveQueueItem());
        }
        if (plugin.bossBarHud() != null) {
            plugin.bossBarHud().showQueueStatus(p, mode.smallCapsTitle(), cur, target, 1L);
        }
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().clearPlayerCache(p.getUniqueId());
        }

        Component line = Component.text()
            .append(Msg.prefixed(Msg.success("Joined queue for <white><bold>" + mode.smallCapsTitle() + "</bold></white> <#86EFAC>(" + cur + "/" + target + ")</#86EFAC>  ")))
            .append(Component.text("[▶ Start Now]", Style.style(TextColor.color(0x4ADE80), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<green>Launch immediately with queued players</green>")))
                .clickEvent(ClickEvent.runCommand("/bb forcestart")))
            .append(Component.text("  "))
            .append(Component.text("[✖ Leave]", Style.style(TextColor.color(0xF87171), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<red>Leave matchmaking queue</red>")))
                .clickEvent(ClickEvent.runCommand("/bb leave")))
            .asComponent();
        p.sendMessage(line);
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
            if (!isInMatch(player.getUniqueId())) {
                player.getInventory().setItem(8, null);
                if (plugin.bossBarHud() != null) {
                    plugin.bossBarHud().clearPlayer(player);
                }
            }
            if (plugin.sidebarManager() != null) {
                plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
            }
            if (notify) {
                Msg.send(player, Msg.info("Left the <white>" + removed.mode().smallCapsTitle() + "</white> queue."));
                Msg.playPersonalPackSound(player, "ui.click", 0.8f, 0.85f);
            }
            if (plugin.lockerRoom3dManager() != null) {
                plugin.lockerRoom3dManager().refreshAllOpenStages(true);
            }
            return true;
        }
        return false;
    }

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
                Msg.send(p, Msg.error("All courts are currently busy! An admin can create another with <yellow>/bb arena build <name></yellow>."));
            }
            return false;
        }

        for (Player p : selected) {
            matchmakingQueue.remove(p.getUniqueId());
            if (plugin.bossBarHud() != null) {
                plugin.bossBarHud().clearPlayer(p);
            }
            if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
                plugin.lockerRoom3dManager().close(p, true);
            }
        }

        MatchSession session = new MatchSession(plugin, freeArena, mode);
        sessionsByArena.put(freeArena.id(), session);

        int cap = Math.max(1, mode.playersPerTeam());
        int ignisCount = 0;
        int frostCount = 0;
        Set<UUID> assignedIds = new HashSet<>();
        for (Player p : selected) {
            if (assignedIds.contains(p.getUniqueId())) {
                continue;
            }
            List<Player> group = new ArrayList<>();
            group.add(p);
            if (plugin.socialManager() != null) {
                var party = plugin.socialManager().getParty(p.getUniqueId());
                if (party != null) {
                    for (Player other : selected) {
                        if (!other.getUniqueId().equals(p.getUniqueId())
                            && !assignedIds.contains(other.getUniqueId())
                            && party.members().contains(other.getUniqueId())) {
                            group.add(other);
                        }
                    }
                }
            }
            TeamSide preferred = (ignisCount + group.size() <= cap && ignisCount <= frostCount)
                ? TeamSide.IGNIS
                : (frostCount + group.size() <= cap ? TeamSide.FROST : (ignisCount <= frostCount ? TeamSide.IGNIS : TeamSide.FROST));
            for (Player member : group) {
                TeamSide targetSide = preferred;
                if (targetSide == TeamSide.IGNIS && ignisCount >= cap && frostCount < cap) {
                    targetSide = TeamSide.FROST;
                } else if (targetSide == TeamSide.FROST && frostCount >= cap && ignisCount < cap) {
                    targetSide = TeamSide.IGNIS;
                }
                if (targetSide == TeamSide.IGNIS) {
                    ignisCount++;
                } else {
                    frostCount++;
                }
                assignedIds.add(member.getUniqueId());
                session.addPlayer(member, targetSide);
            }
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

    public void spectateFirstActiveMatch(Player player) {
        spectateFirstActive(player);
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
        Msg.send(player, Msg.warn("No live match is currently running to spectate."));
    }

    public void tickAll() {
        tickCounter++;
        matchmakingQueue.entrySet().removeIf(e -> {
            Player p = Bukkit.getServer() != null ? Bukkit.getPlayer(e.getKey()) : null;
            return p == null || !p.isOnline();
        });

        // Update live Queue BossBars once per second
        if (tickCounter % 20 == 0 && plugin != null && plugin.bossBarHud() != null && Bukkit.getServer() != null) {
            for (QueueTicket ticket : matchmakingQueue.values()) {
                Player p = Bukkit.getPlayer(ticket.playerId());
                if (p != null && p.isOnline() && !isInMatch(p.getUniqueId())) {
                    int cur = countTicketsForMode(ticket.mode());
                    long elapsed = getQueueElapsedSeconds(p.getUniqueId());
                    plugin.bossBarHud().showQueueStatus(p, ticket.mode().smallCapsTitle(), cur, ticket.mode().maxPlayers(), elapsed);
                }
            }
        }

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
            Msg.send(initiator, Msg.info("Generating <white><bold>BosketBall Arena</bold></white>..."));
            freeArena = ArenaBuilder.buildFullStadium("main", "Bosket Center", initiator.getLocation().add(0, -1, 22));
            plugin.arenaManager().registerArena(freeArena);
        }
        return freeArena;
    }

    public void quickJoin(Player player, GameModeType mode, String specificArenaId) {
        leaveQueue(player, false);
        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().close(player, true);
        }

        MatchSession current = getSessionOf(player.getUniqueId());
        if (current != null) {
            current.removePlayer(player, true);
        }

        if (specificArenaId != null) {
            Arena target = plugin.arenaManager().getArena(specificArenaId);
            if (target == null) {
                Msg.send(player, Msg.error("Arena not found: <white>" + specificArenaId + "</white>"));
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

        if (mode.competitiveTeamMatch()) {
            for (MatchSession s : sessionsByArena.values()) {
                if (s.mode() == mode && s.state() == MatchSession.State.WAITING && s.participantCount() < mode.maxPlayers()) {
                    s.addPlayer(player, null);
                    return;
                }
            }
        }

        Arena freeArena = findOrCreateFreeArena(player);
        if (freeArena == null) {
            Msg.send(player, Msg.error("All courts are currently busy! Create another with <yellow>/bb arena build <name></yellow>."));
            return;
        }

        MatchSession session = new MatchSession(plugin, freeArena, mode);
        sessionsByArena.put(freeArena.id(), session);
        session.addPlayer(player, TeamSide.IGNIS);
    }

    public void sendOrAcceptDuel(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            Msg.send(sender, Msg.error("You cannot challenge yourself to a duel."));
            return;
        }

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
        Msg.send(sender, Msg.success("Sent a 1v1 Duel challenge to <white><bold>" + target.getName() + "</bold></white>."));
        Msg.send(target, "<#FBBF24>⚔ <white><bold>" + sender.getName() + "</bold></white> challenged you to a 1v1 Duel! </#FBBF24>"
            + "<click:run_command:'/bb duel " + sender.getName() + "'><hover:show_text:'<green>Click to accept the 1v1 Duel</green>'>"
            + "<#4ADE80><bold>[✔ Accept]</bold></#4ADE80></hover></click>");
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
