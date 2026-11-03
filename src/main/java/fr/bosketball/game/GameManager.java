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

    public record QueueTicket(
        UUID playerId,
        GameModeType mode,
        fr.bosketball.model.GameQueueMode queueMode,
        long queuedAtMillis
    ) {
        public QueueTicket(UUID playerId, GameModeType mode, long queuedAtMillis) {
            this(playerId, mode, mode != null ? mode.queueMode() : fr.bosketball.model.GameQueueMode.RANKED, queuedAtMillis);
        }
    }

    private record DuelInvite(UUID challengerId, long expiresAtMillis) {}

    private static final long DUEL_INVITE_TTL_MS = 60_000L;

    private final BosketBallPlugin plugin;
    private final Map<String, MatchSession> sessionsByArena = new ConcurrentHashMap<>();
    private final Map<UUID, MatchSession> sessionByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, QueueTicket> matchmakingQueue = new ConcurrentHashMap<>();
    private final Map<UUID, DuelInvite> pendingDuelByTarget = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong completedMatchEpoch = new java.util.concurrent.atomic.AtomicLong(0L);
    private volatile long lastMatchCompletedAtMillis = 0L;
    private final Map<UUID, Long> lastClaimedGgEpoch = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGgChatMs = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private final MatchmakingEngine matchmakingEngine;

    public MatchmakingEngine matchmaking() {
        return matchmakingEngine;
    }

    /** Queue tickets for a mode as an ordered id → queued-at map (used by the ELO engine). */
    private java.util.Map<UUID, Long> queueTimesFor(GameModeType mode, fr.bosketball.model.GameQueueMode queueMode) {
        java.util.Map<UUID, Long> out = new java.util.LinkedHashMap<>();
        java.util.List<QueueTicket> tickets = new ArrayList<>(matchmakingQueue.values());
        tickets.sort(Comparator.comparingLong(QueueTicket::queuedAtMillis));
        for (QueueTicket t : tickets) {
            if (t.mode() == mode && t.queueMode() == queueMode) {
                out.put(t.playerId(), t.queuedAtMillis());
            }
        }
        return out;
    }

    public int queuedCount(GameModeType mode, fr.bosketball.model.GameQueueMode queueMode) {
        int n = 0;
        for (QueueTicket t : matchmakingQueue.values()) {
            if (t.mode() == mode && t.queueMode() == queueMode) {
                n++;
            }
        }
        return n;
    }

    /** Toggles the party/team chat channel of a player. */
    public boolean togglePartyChat(Player player) {
        var profile = plugin.statsManager().getOrCreate(player);
        boolean next = !profile.partyChatEnabled();
        profile.setPartyChatEnabled(next);
        plugin.statsManager().markDirty(player.getUniqueId());
        return next;
    }

    /** True when the player's messages should go to their party instead of global chat. */
    public boolean isInPartyChat(UUID playerId) {
        var profile = plugin.statsManager().getIfPresent(playerId);
        return profile != null && profile.partyChatEnabled();
    }


    public GameManager(BosketBallPlugin plugin) {
        this.matchmakingEngine = new MatchmakingEngine(plugin);
        this.plugin = plugin;
    }

    public Collection<MatchSession> activeSessions() {
        return sessionsByArena.values();
    }

    public void indexPlayerSession(UUID playerId, MatchSession session) {
        if (playerId != null && session != null) {
            sessionByPlayer.put(playerId, session);
        }
    }

    public void unindexPlayerSession(UUID playerId, MatchSession session) {
        if (playerId != null) {
            if (session == null) {
                sessionByPlayer.remove(playerId);
            } else {
                sessionByPlayer.remove(playerId, session);
            }
        }
    }

    public void handleDisconnect(UUID playerId) {
        matchmakingEngine.handleQuit(playerId);
        if (playerId == null) {
            return;
        }
        pendingDuelByTarget.remove(playerId);
        pendingDuelByTarget.values().removeIf(inv -> inv.challengerId().equals(playerId));
        lastGgChatMs.remove(playerId);
        if ((System.currentTimeMillis() - lastMatchCompletedAtMillis) > 60_000L) {
            lastClaimedGgEpoch.remove(playerId);
        }
    }

    public MatchSession getSessionByArena(String arenaId) {
        return arenaId == null ? null : sessionsByArena.get(arenaId.toLowerCase());
    }

    public MatchSession getSessionOf(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        MatchSession indexed = sessionByPlayer.get(playerId);
        if (indexed != null) {
            if (indexed.state() != MatchSession.State.FINISHED && indexed.hasPlayer(playerId)) {
                return indexed;
            }
            sessionByPlayer.remove(playerId, indexed);
        }
        for (MatchSession s : sessionsByArena.values()) {
            if (s.hasPlayer(playerId) && s.state() != MatchSession.State.FINISHED) {
                sessionByPlayer.put(playerId, s);
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

    public boolean isActivelyPlayingCompetitiveMatch(UUID playerId) {
        MatchSession s = getSessionOf(playerId);
        return s != null
            && s.mode().competitiveTeamMatch()
            && s.state() != MatchSession.State.WAITING
            && s.state() != MatchSession.State.FINISHED
            && s.getTeam(playerId) != TeamSide.SPECTATOR;
    }

    public void startPractice(Player player) {
        if (player != null && isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            Msg.sendAlert(player, "You are in a live ranked match! Use <yellow>/leave</yellow> if you want to forfeit.");
            return;
        }
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
        if (isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            Msg.sendAlert(player, "You are in a live ranked match! Use <yellow>/leave</yellow> if you want to forfeit.");
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
        fr.bosketball.model.GameQueueMode queueMode = mode.queueMode();
        enqueueSinglePlayer(player, mode, queueMode, now);

        // Pull online Party members (if party leader) into the same queue
        if (plugin.socialManager() != null) {
            var party = plugin.socialManager().getParty(player.getUniqueId());
            if (party != null && party.isLeader(player.getUniqueId())) {
                for (Player mate : plugin.socialManager().onlinePartyMembers(player.getUniqueId())) {
                    if (!mate.getUniqueId().equals(player.getUniqueId()) && !isInMatch(mate.getUniqueId())) {
                        enqueueSinglePlayer(mate, mode, queueMode, now);
                    }
                }
            }
        }

        if (plugin.lockerRoom3dManager() != null) {
            plugin.lockerRoom3dManager().refreshAllOpenStages(true);
        }

        tryLaunchReadyMatch(mode, false);
    }

    private void enqueueSinglePlayer(Player p, GameModeType mode, fr.bosketball.model.GameQueueMode queueMode, long timestamp) {
        matchmakingQueue.put(p.getUniqueId(), new QueueTicket(p.getUniqueId(), mode, queueMode, timestamp));
        int cur = countTicketsForMode(mode);
        int target = mode.maxPlayers();

        if (!isInMatch(p.getUniqueId()) && (plugin.lockerRoom3dManager() == null || !plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId()))) {
            p.getInventory().setItem(BallSkin.LEAVE_QUEUE_LOBBY_SLOT, BallSkin.createLeaveQueueItem(mode.smallCapsTitle()));
        }
        if (plugin.bossBarHud() != null) {
            plugin.bossBarHud().showQueueStatus(p, mode.smallCapsTitle(), cur, target, 1L);
        }
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().clearPlayerCache(p.getUniqueId());
        }

        var line = Component.text()
            .append(Msg.parse(Msg.pillTag(fr.bosketball.util.Glyphs.SYS_COURT) + " "
                + Msg.success("Joined queue for <white><bold>" + mode.smallCapsTitle() + "</bold></white> <#86EFAC>(" + cur + "/" + target + ")</#86EFAC>  ")));
        if (p.isOp()) {
            line.append(Component.text("[▶ Start Now]", Style.style(TextColor.color(0x4ADE80), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<green>Force-launch immediately (OP)</green>")))
                .clickEvent(ClickEvent.runCommand("/bb forcestart")))
                .append(Component.text("  "));
        }
        line.append(Component.text("[✖ Leave]", Style.style(TextColor.color(0xF87171), TextDecoration.BOLD))
            .hoverEvent(HoverEvent.showText(Msg.parse("<red>Leave matchmaking queue</red>")))
            .clickEvent(ClickEvent.runCommand("/leave")));
        p.sendMessage(line.asComponent());
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
        if (plugin.socialService() != null && !isInMatch(player.getUniqueId())) {
            plugin.socialService().setStatus(player.getUniqueId(), fr.bosketball.model.PlayerStatus.LOBBY);
        }
        if (removed != null) {
            if (!isInMatch(player.getUniqueId())) {
                if (plugin.lockerRoom3dManager() == null || !plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                    player.getInventory().setItem(BallSkin.LEAVE_QUEUE_LOBBY_SLOT, null);
                }
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
        return tryLaunchReadyMatch(mode, mode.queueMode(), forceLaunch);
    }

    /**
     * ELO-weighted queue resolution. Picks the closest-skilled group through
     * {@link MatchmakingEngine}, then either opens an accept prompt or launches directly
     * when the prompt is disabled.
     */
    public boolean tryLaunchReadyMatch(GameModeType mode, fr.bosketball.model.GameQueueMode queueMode, boolean forceLaunch) {
        java.util.Map<UUID, Long> queueTimes = queueTimesFor(mode, queueMode);
        int required = forceLaunch ? 1 : mode.maxPlayers();
        if (queueTimes.size() < required) {
            return false;
        }

        List<UUID> chosenIds = matchmakingEngine.selectGroup(mode, forceLaunch, queueTimes);
        if (chosenIds.isEmpty()) {
            // Nobody inside the elastic window yet — optionally backfill with an AI opponent
            maybeBackfillWithBot(mode, queueMode, queueTimes);
            return false;
        }

        if (!forceLaunch && matchmakingEngine.proposeMatch(mode, queueMode, chosenIds)) {
            return true;
        }
        return launchConfirmedMatch(mode, queueMode, chosenIds);
    }

    /**
     * Launches a fully confirmed roster onto a free arena. Used by both the accept-prompt
     * path and the instant-launch path.
     */
    public boolean launchConfirmedMatch(GameModeType mode, fr.bosketball.model.GameQueueMode queueMode, List<UUID> chosenIds) {
        List<Player> selected = new ArrayList<>();
        for (UUID id : chosenIds) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                selected.add(p);
            }
        }
        if (selected.isEmpty()) {
            return false;
        }

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

        freeArena.ensureDisplaysHealthy();
        MatchSession session = new MatchSession(plugin, freeArena, mode);
        session.setQueueMode(queueMode);
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
        // ── AI backfill: never leave a paying player on an empty court ──────────
        if (mode.competitiveTeamMatch() && selected.size() < mode.maxPlayers()) {
            var bots = plugin.settings().matchmaking();
            if (bots.fillBotsOnLaunch()) {
                session.fillWithBots(fr.bosketball.game.bot.BotDifficulty.byNameOrNull(bots.botDifficulty()) != null
                    ? fr.bosketball.game.bot.BotDifficulty.byNameOrNull(bots.botDifficulty())
                    : fr.bosketball.game.bot.BotDifficulty.NORMAL);
            }
        }
        if (selected.size() < mode.maxPlayers() && session.state() == MatchSession.State.WAITING) {
            session.forceStart();
        }
        if (plugin.integration() != null && selected.size() >= 2 && mode.competitiveTeamMatch()) {
            plugin.integration().onMatchLaunched(session);
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

    public void notifyMatchCompleted() {
        notifyMatchCompletedAt(System.currentTimeMillis());
    }

    public void notifyMatchCompletedAt(long nowMillis) {
        completedMatchEpoch.incrementAndGet();
        this.lastMatchCompletedAtMillis = nowMillis;
    }

    public boolean canClaimPostMatchGgReward(UUID playerId, long nowMillis) {
        if (playerId == null || lastMatchCompletedAtMillis <= 0L) {
            return false;
        }
        if ((nowMillis - lastMatchCompletedAtMillis) > 45_000L) {
            return false;
        }
        long curEpoch = completedMatchEpoch.get();
        return curEpoch > 0L && lastClaimedGgEpoch.getOrDefault(playerId, 0L) < curEpoch;
    }

    public boolean markPostMatchGgClaimed(UUID playerId, long nowMillis) {
        if (!canClaimPostMatchGgReward(playerId, nowMillis)) {
            return false;
        }
        lastClaimedGgEpoch.put(playerId, completedMatchEpoch.get());
        return true;
    }

    public void triggerGg(Player player) {
        fr.bosketball.command.GgCommand.executeGg(plugin, player);
    }

    public List<MatchSession> spectatableSessions() {
        List<MatchSession> list = new ArrayList<>();
        for (MatchSession s : sessionsByArena.values()) {
            if (s.state() != MatchSession.State.FINISHED && s.mode() != GameModeType.PRACTICE) {
                list.add(s);
            }
        }
        if (list.isEmpty()) {
            for (MatchSession s : sessionsByArena.values()) {
                if (s.state() != MatchSession.State.FINISHED) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    public void spectateSession(Player player, MatchSession targetSession) {
        if (player == null || targetSession == null || targetSession.state() == MatchSession.State.FINISHED) {
            Msg.send(player, Msg.warn("That match has already finished."));
            return;
        }
        if (isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            Msg.sendAlert(player, "You cannot spectate another match while playing a live ranked game.");
            return;
        }
        leaveQueue(player, false);
        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().close(player, true);
        }
        MatchSession prev = getSessionOf(player.getUniqueId());
        if (prev != null && prev != targetSession) {
            prev.removePlayer(player, true);
        }
        targetSession.addPlayer(player, TeamSide.SPECTATOR);
    }

    public void spectateFirstActiveMatch(Player player) {
        List<MatchSession> live = spectatableSessions();
        if (live.size() > 1 && plugin.guiMenu() != null) {
            plugin.guiMenu().openSpectateBrowser(player);
            return;
        }
        spectateFirstActive(player);
    }

    public void spectateFirstActive(Player player) {
        List<MatchSession> live = spectatableSessions();
        if (!live.isEmpty()) {
            spectateSession(player, live.get(0));
            return;
        }
        Msg.send(player, Msg.warn("No live match is currently running to spectate."));
    }

    public void tickAll() {
        tickCounter++;
        matchmakingQueue.entrySet().removeIf(e -> {
            Player p = Bukkit.getServer() != null ? Bukkit.getPlayer(e.getKey()) : null;
            return p == null || !p.isOnline();
        });

        // Resolve accept prompts & expire timed-out lobbies once per second
        if (tickCounter % 20 == 0) {
            matchmakingEngine.tick();
            // Periodically retry every queued mode so ELO windows can expand over time
            Set<GameModeType> modes = new HashSet<>();
            Set<fr.bosketball.model.GameQueueMode> disciplines = new HashSet<>();
            for (QueueTicket t : matchmakingQueue.values()) {
                modes.add(t.mode());
                disciplines.add(t.queueMode());
            }
            for (GameModeType m : modes) {
                for (fr.bosketball.model.GameQueueMode d : disciplines) {
                    if (!matchmakingEngine.pendingPlayers().isEmpty()) {
                        break;
                    }
                    tryLaunchReadyMatch(m, d, false);
                }
            }
        }

        // Update live Queue BossBars once per second
        if (tickCounter % 20 == 0 && plugin != null && plugin.bossBarHud() != null && Bukkit.getServer() != null) {
            for (QueueTicket ticket : matchmakingQueue.values()) {
                Player p = Bukkit.getPlayer(ticket.playerId());
                // The full-screen Locker Room HUD owns the BossBar area — queue status is shown inside it instead.
                if (p != null && p.isOnline() && !isInMatch(p.getUniqueId()) && !matchmakingEngine.pendingPlayers().contains(p.getUniqueId())
                    && (plugin.lockerRoom3dManager() == null || !plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId()))) {
                    int cur = countTicketsForMode(ticket.mode());
                    long elapsed = getQueueElapsedSeconds(p.getUniqueId());
                    plugin.bossBarHud().showQueueStatus(p, ticket.mode().smallCapsTitle(), cur, ticket.mode().maxPlayers(), elapsed);
                }
            }
            // Social presence refresh (queue / match states decay back to lobby automatically)
            if (plugin.socialService() != null && plugin.gameManager() != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (isInMatch(id)) {
                        MatchSession s = getSessionOf(id);
                        plugin.socialService().setStatus(id, s != null && s.getTeam(id) == TeamSide.SPECTATOR
                            ? fr.bosketball.model.PlayerStatus.SPECTATING
                            : fr.bosketball.model.PlayerStatus.MATCH);
                    } else if (matchmakingQueue.containsKey(id)) {
                        plugin.socialService().setStatus(id, fr.bosketball.model.PlayerStatus.QUEUE);
                    } else if (plugin.socialService().statusOf(id) == fr.bosketball.model.PlayerStatus.QUEUE
                        || plugin.socialService().statusOf(id) == fr.bosketball.model.PlayerStatus.MATCH
                        || plugin.socialService().statusOf(id) == fr.bosketball.model.PlayerStatus.SPECTATING) {
                        plugin.socialService().setStatus(id, fr.bosketball.model.PlayerStatus.LOBBY);
                    }
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

    /**
     * Offers a player a one-click way back into the queue after a remake, without silently
     * re-queuing them (players hate being teleported into a new match unexpectedly).
     */
    public void requeueOffer(Player player, GameModeType mode, fr.bosketball.model.GameQueueMode queueMode) {
        if (player == null || !player.isOnline() || mode == null) {
            return;
        }
        var line = Component.text()
            .append(Msg.parse(Msg.pillTag(fr.bosketball.util.Glyphs.SYS_COURT) + " "))
            .append(Msg.parse("<gray>The match was remade. Re-queue for</gray> <white>"
                + mode.smallCapsTitle() + "</white>?  "))
            .append(Component.text("[YES]", Style.style(TextColor.color(0x4ADE80), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<green>Rejoin the queue</green>")))
                .clickEvent(ClickEvent.runCommand("/play " + mode.id())))
            .append(Component.text("  "))
            .append(Component.text("[NO]", Style.style(TextColor.color(0xF87171), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<red>Stay in the lobby</red>")))
                .clickEvent(ClickEvent.runCommand("/spawn")));
        player.sendMessage(line.build());
    }

    /**
     * Puts a player back into the queue after a dodge/decline without losing their accrued
     * wait time, keeping the accept-prompt flow coherent.
     */
    public void requeueAfterDodge(Player player, GameModeType mode, fr.bosketball.model.GameQueueMode queueMode) {
        if (player == null || !player.isOnline() || mode == null) {
            return;
        }
        long now = System.currentTimeMillis();
        matchmakingQueue.put(player.getUniqueId(), new QueueTicket(
            player.getUniqueId(), mode,
            queueMode != null ? queueMode : mode.queueMode(),
            now
        ));
        if (plugin.bossBarHud() != null) {
            plugin.bossBarHud().showQueueStatus(player, mode.smallCapsTitle(),
                countTicketsForMode(mode), mode.maxPlayers(), 1L);
        }
    }

    /**
     * When nobody fits the window after {@code matchmaking.backfill-bot-after-seconds}, the
     * longest-waiting player is given a solo court against the Pro Sparring Bot instead of
     * waiting forever.
     */
    private void maybeBackfillWithBot(GameModeType mode, fr.bosketball.model.GameQueueMode queueMode, java.util.Map<UUID, Long> queueTimes) {
        int afterSeconds = plugin.settings().matchmaking().backfillBotAfterSeconds();
        if (afterSeconds <= 0 || !mode.competitiveTeamMatch()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<UUID, Long> entry : queueTimes.entrySet()) {
            long waited = (now - entry.getValue()) / 1000L;
            if (waited < afterSeconds) {
                continue;
            }
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                continue;
            }
            matchmakingQueue.remove(p.getUniqueId());
            if (plugin.bossBarHud() != null) {
                plugin.bossBarHud().clearPlayer(p);
            }
            Msg.send(p, Msg.info("No human opponent inside your ELO window — dropping you onto a court against the "
                + "<white><bold>Pro Sparring AI</bold></white>. <gray>No ELO is at stake.</gray>"));
            quickJoin(p, mode, null);
            MatchSession s = getSessionOf(p.getUniqueId());
            if (s != null && s.state() == MatchSession.State.WAITING) {
                s.toggleSparringBot(p);
                s.forceStart();
            }
            return;
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
            org.bukkit.Location safeOrigin = new org.bukkit.Location(initiator.getWorld(), 512.5, 64.0, 512.5);
            freeArena = ArenaBuilder.buildFullStadium("main", "Bosket Center", safeOrigin);
            plugin.arenaManager().registerArena(freeArena);
        }
        return freeArena;
    }

    public void quickJoin(Player player, GameModeType mode, String specificArenaId) {
        if (player == null || mode == null) {
            return;
        }
        if (isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            Msg.sendAlert(player, "You are in a live ranked match! Use <yellow>/leave</yellow> if you want to forfeit.");
            return;
        }
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
            target.ensureDisplaysHealthy();
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

        Arena freeArena = !mode.competitiveTeamMatch() && plugin.practicePool() != null
            ? plugin.practicePool().acquireFreePracticeArena(this::getSessionByArena)
            : null;
        if (freeArena == null) {
            freeArena = findOrCreateFreeArena(player);
        }
        if (freeArena == null) {
            Msg.send(player, Msg.error("All courts are currently busy! Create another with <yellow>/bb arena build <name></yellow>."));
            return;
        }

        freeArena.ensureDisplaysHealthy();
        MatchSession session = new MatchSession(plugin, freeArena, mode);
        session.setQueueMode(mode.queueMode());
        sessionsByArena.put(freeArena.id(), session);
        session.addPlayer(player, TeamSide.IGNIS);
    }

    private boolean isInLiveCompetitiveMatch(UUID playerId) {
        MatchSession s = getSessionOf(playerId);
        return s != null
            && s.mode().competitiveTeamMatch()
            && s.getTeam(playerId) != TeamSide.SPECTATOR
            && (s.state() == MatchSession.State.COUNTDOWN
                || s.state() == MatchSession.State.LIVE
                || s.state() == MatchSession.State.CELEBRATION);
    }

    public void sendOrAcceptDuel(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            Msg.send(sender, Msg.error("You cannot challenge yourself to a duel."));
            return;
        }
        if (isInLiveCompetitiveMatch(sender.getUniqueId())) {
            Msg.sendAlert(sender, "Finish your current competitive match before starting a 1v1 Duel!");
            return;
        }
        if (isInLiveCompetitiveMatch(target.getUniqueId())) {
            Msg.sendAlert(sender, "<white>" + target.getName() + "</white> is currently in a live competitive match!");
            return;
        }

        long now = System.currentTimeMillis();
        DuelInvite inviteToSender = pendingDuelByTarget.get(sender.getUniqueId());
        if (inviteToSender != null && inviteToSender.challengerId().equals(target.getUniqueId())) {
            pendingDuelByTarget.remove(sender.getUniqueId());
            if (now > inviteToSender.expiresAtMillis()) {
                Msg.sendAlert(sender, "That 1v1 Duel challenge from <white>" + target.getName() + "</white> expired. Send a new one!");
                return;
            }
            leaveQueue(sender, false);
            if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(sender.getUniqueId())) {
                plugin.lockerRoom3dManager().close(sender, true);
            }
            MatchSession senderPrev = getSessionOf(sender.getUniqueId());
            if (senderPrev != null) {
                senderPrev.removePlayer(sender, true);
            }
            quickJoin(target, GameModeType.DUEL_1V1, null);
            MatchSession duelSession = getSessionOf(target.getUniqueId());
            if (duelSession != null) {
                duelSession.addPlayer(sender, TeamSide.FROST);
            }
            return;
        }

        pendingDuelByTarget.put(target.getUniqueId(), new DuelInvite(sender.getUniqueId(), now + DUEL_INVITE_TTL_MS));
        Msg.sendSocial(sender, Msg.success("Sent a 1v1 Duel challenge to <white><bold>" + target.getName() + "</bold></white> <gray>(expires in 60s)</gray>."));
        Msg.sendSocial(target, "<#FBBF24>⚔ <white><bold>" + sender.getName() + "</bold></white> challenged you to a 1v1 Duel! </#FBBF24>"
            + "<click:run_command:'/duel " + sender.getName() + "'><hover:show_text:'<green>Click to accept the 1v1 Duel</green>'>"
            + "<#4ADE80><bold>[✔ Accept]</bold></#4ADE80></hover></click>");
        Msg.playPersonalPackSound(target, "ui.click", 1.0f, 1.25f);
    }

    public void shutdownAll() {
        matchmakingEngine.clear();
        matchmakingQueue.clear();
        sessionByPlayer.clear();
        pendingDuelByTarget.clear();
        for (MatchSession s : sessionsByArena.values()) {
            s.stopAndCleanup();
        }
        sessionsByArena.clear();
    }
}
