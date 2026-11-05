package fr.bosketball.game;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.ArenaBuilder;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
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

    private final Map<UUID, Integer> pendingCourtRetries = new ConcurrentHashMap<>();

    private int tickCounter = 0;
    private final MatchmakingEngine matchmakingEngine;

    public MatchmakingEngine matchmaking() {
        return matchmakingEngine;
    }

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

    public boolean togglePartyChat(Player player) {
        var profile = plugin.statsManager().getOrCreate(player);
        boolean next = !profile.partyChatEnabled();
        profile.setPartyChatEnabled(next);
        plugin.statsManager().markDirty(player.getUniqueId());
        return next;
    }

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

        matchmakingQueue.remove(playerId);
        sessionByPlayer.remove(playerId);
        lastClaimedGgEpoch.remove(playerId);
        if (lastClaimedGgEpoch.size() > 4096) {
            lastClaimedGgEpoch.clear();
        }
    }

    public MatchSession getSessionByArena(String arenaId) {
        return arenaId == null ? null : sessionsByArena.get(arenaId.toLowerCase(java.util.Locale.ROOT));
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

    public boolean instancedCourtsRequested() {
        return plugin.schematicCourtPool() != null && plugin.schematicCourtPool().enabled();
    }

    public boolean instancedCourtsActive() {
        return plugin.schematicCourtPool() != null && plugin.schematicCourtPool().ready();
    }

    public String instancedCourtsError() {
        var pool = plugin.schematicCourtPool();
        if (pool == null) {
            return Msg.error("Instanced courts are not initialised — check the console.");
        }
        if (pool.loadError() != null) {
            return Msg.error("<red>terrain.schem could not be read:</red> <gray>" + pool.loadError()
                + "</gray> <dark_gray>•</dark_gray> <yellow>/bb arena schematic info</yellow>");
        }
        return Msg.warn("Your <white>terrain.schem</white> court is still being pasted — try again in a second.");
    }

    public Arena acquireSoloCourt() {
        var pool = plugin.schematicCourtPool();
        if (instancedCourtsRequested()) {
            return pool.acquireFreePracticeArena();
        }
        if (pool != null) {
            Arena instanced = pool.acquireFreePracticeArena();
            if (instanced != null) {
                return instanced;
            }
        }
        if (plugin.practicePool() != null) {
            Arena legacy = plugin.practicePool().acquireFreePracticeArena(this::getSessionByArena);
            if (legacy != null) {
                return legacy;
            }
        }

        for (Arena a : plugin.arenaManager().allArenas()) {
            if (pool != null && pool.isSchematicCourt(a.id())) {
                continue;
            }
            MatchSession s = sessionsByArena.get(a.id());
            if (s == null || s.state() == MatchSession.State.FINISHED) {
                return a;
            }
        }
        return null;
    }

    public void startPractice(Player player) {
        if (player != null && isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            Msg.sendAlert(player, "You are in a live ranked match! Use <yellow>/leave</yellow> if you want to forfeit.");
            return;
        }
        leaveQueue(player, false);
        quickJoin(player, GameModeType.PRACTICE, null);
    }

    public void joinQueue(Player player, GameModeType mode) {
        if (player == null || mode == null) {
            return;
        }
        if (isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            Msg.sendAlert(player, "You are in a live ranked match! Use <yellow>/leave</yellow> if you want to forfeit.");
            return;
        }

        var profileForLockout = plugin.statsManager().getOrCreate(player);
        long lockoutUntil = profileForLockout.matchmakingBanUntilMillis();
        long nowMillis = System.currentTimeMillis();
        if (lockoutUntil > nowMillis) {
            long secondsLeft = Math.max(1L, (lockoutUntil - nowMillis + 999L) / 1000L);
            Msg.sendAlert(player, "You declined a match — the queue is locked for another <white><bold>"
                + secondsLeft + "s</bold></white>.");
            return;
        }

        if (!mode.competitiveTeamMatch()) {
            leaveQueue(player, false);
            quickJoin(player, mode, null);
            return;
        }

        QueueTicket existingTicket = matchmakingQueue.get(player.getUniqueId());
        if (existingTicket != null && existingTicket.mode() == mode) {
            leaveQueue(player, true);
            return;
        }

        MatchSession curSession = getSessionOf(player.getUniqueId());
        if (curSession != null) {
            curSession.removePlayer(player, true);
        }

        long now = System.currentTimeMillis();
        fr.bosketball.model.GameQueueMode queueMode = mode.queueMode();
        enqueueSinglePlayer(player, mode, queueMode, now);

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


        tryLaunchReadyMatch(mode, false);
    }

    private void enqueueSinglePlayer(Player p, GameModeType mode, fr.bosketball.model.GameQueueMode queueMode, long timestamp) {
        matchmakingQueue.put(p.getUniqueId(), new QueueTicket(p.getUniqueId(), mode, queueMode, timestamp));
        if (plugin.tutorialManager() != null) {
            plugin.tutorialManager().notifyAction(p, "queue", null);
        }
        int cur = countTicketsForMode(mode);
        int target = mode.maxPlayers();

        if (!isInMatch(p.getUniqueId())) {
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
        if (Perms.staff(p)) {
            line.append(Msg.parse("<#4ADE80>▶</#4ADE80> <bold>[ꜱᴛᴀʀᴛ ɴᴏᴡ]</bold>")
                .hoverEvent(HoverEvent.showText(Msg.parse("<green>Force-launch immediately (OP)</green>")))
                .clickEvent(ClickEvent.runCommand("/bb forcestart")))
                .append(Component.text("  "));
        }
        line.append(Component.text("[✖ ʟᴇᴀᴠᴇ]", Style.style(TextColor.color(0xF87171), TextDecoration.BOLD))
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
                player.getInventory().setItem(BallSkin.LEAVE_QUEUE_LOBBY_SLOT, null);
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
            return true;
        }
        return false;
    }

    public boolean tryLaunchReadyMatch(GameModeType mode, boolean forceLaunch) {
        return tryLaunchReadyMatch(mode, mode.queueMode(), forceLaunch);
    }

    public boolean tryLaunchReadyMatch(GameModeType mode, fr.bosketball.model.GameQueueMode queueMode, boolean forceLaunch) {
        java.util.Map<UUID, Long> queueTimes = queueTimesFor(mode, queueMode);
        int required = forceLaunch ? 1 : mode.maxPlayers();
        if (queueTimes.isEmpty()) {
            return false;
        }

        var mmCfg = plugin.settings().matchmaking();
        boolean aiFill = mode.competitiveTeamMatch() && mmCfg.fillBotsOnLaunch();
        int needed = required;
        if (!forceLaunch && aiFill) {
            needed = Math.min(required, Math.max(2, mode.minTotalPlayersToStart()));
        }
        if (queueTimes.size() < needed) {

            maybeBackfillWithBot(mode, queueMode, queueTimes);
            return false;
        }

        List<UUID> chosenIds = matchmakingEngine.selectGroup(mode, forceLaunch, queueTimes);
        if (chosenIds.isEmpty()) {

            maybeBackfillWithBot(mode, queueMode, queueTimes);
            return false;
        }

        if (!forceLaunch && matchmakingEngine.proposeMatch(mode, queueMode, chosenIds)) {
            return true;
        }
        return launchConfirmedMatch(mode, queueMode, chosenIds);
    }

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
                Msg.send(p, instancedCourtsRequested()
                    ? instancedCourtsError()
                    : Msg.error("All courts are currently busy! An admin can create another with <yellow>/bb arena build <name></yellow>."));
            }
            return false;
        }

        for (Player p : selected) {
            matchmakingQueue.remove(p.getUniqueId());
            if (plugin.bossBarHud() != null) {
                plugin.bossBarHud().clearPlayer(p);
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
        return true;
    }

    public void leaveAll(Player player, boolean notify) {
        boolean leftQueue = leaveQueue(player, notify);
        MatchSession s = getSessionOf(player.getUniqueId());
        if (s != null) {
            s.removePlayer(player, !notify);
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

        if (tickCounter % 20 == 0) {
            matchmakingEngine.tick();

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

        if (tickCounter % 20 == 0 && plugin != null && plugin.bossBarHud() != null && Bukkit.getServer() != null) {
            for (QueueTicket ticket : matchmakingQueue.values()) {
                Player p = Bukkit.getPlayer(ticket.playerId());

                if (p != null && p.isOnline() && !isInMatch(p.getUniqueId())
                    && !matchmakingEngine.pendingPlayers().contains(p.getUniqueId())) {
                    int cur = countTicketsForMode(ticket.mode());
                    long elapsed = getQueueElapsedSeconds(p.getUniqueId());
                    plugin.bossBarHud().showQueueStatus(p, ticket.mode().smallCapsTitle(), cur, ticket.mode().maxPlayers(), elapsed);
                }
            }

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

        if (instancedCourtsRequested()) {
            return plugin.schematicCourtPool().acquireFreeArena(null);
        }
        Arena freeArena = null;
        for (Arena a : plugin.arenaManager().allArenas()) {
            MatchSession s = sessionsByArena.get(a.id());
            if (s == null || s.state() == MatchSession.State.FINISHED) {

                if (plugin.schematicCourtPool() != null && plugin.schematicCourtPool().isSchematicCourt(a.id())) {
                    continue;
                }
                freeArena = a;
                break;
            }
        }
        if (freeArena == null && initiator != null && plugin.schematicCourtPool() != null) {
            freeArena = plugin.schematicCourtPool().acquireFreeArena(null);
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

        Arena freeArena = !mode.competitiveTeamMatch() ? acquireSoloCourt() : null;
        if (freeArena == null) {
            freeArena = findOrCreateFreeArena(player);
        }
        if (freeArena == null) {

            if (instancedCourtsRequested() && instancedCourtsActive() && retryCourtLater(player, mode)) {
                Msg.send(player, Msg.info("Preparing your court…"));
                return;
            }
            Msg.send(player, instancedCourtsRequested()
                ? instancedCourtsError()
                : Msg.error("All courts are currently busy! Create another with <yellow>/bb arena build <name></yellow>."));
            return;
        }
        pendingCourtRetries.remove(player.getUniqueId());

        freeArena.ensureDisplaysHealthy();
        MatchSession session = new MatchSession(plugin, freeArena, mode);
        session.setQueueMode(mode.queueMode());
        sessionsByArena.put(freeArena.id(), session);
        session.addPlayer(player, TeamSide.IGNIS);
    }

    private static final int MAX_COURT_RETRIES = 30;

    private boolean retryCourtLater(Player player, GameModeType mode) {
        UUID id = player.getUniqueId();
        int attempt = pendingCourtRetries.getOrDefault(id, 0);
        if (attempt >= MAX_COURT_RETRIES) {
            return false;
        }
        pendingCourtRetries.put(id, attempt + 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                pendingCourtRetries.remove(id);
                return;
            }
            if (getSessionOf(id) != null) {
                pendingCourtRetries.remove(id);
                return;
            }
            quickJoin(p, mode, null);
        }, 10L);
        return true;
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

            Msg.send(sender, Msg.success("Duel accepted — <white>" + target.getName()
                + "</white>. Starting your private court…"));
            Msg.playPersonalPackSound(sender, "game.whistle", 1.0f, 1.15f);
            Msg.playPersonalPackSound(target, "game.whistle", 1.0f, 1.15f);
            Bukkit.getScheduler().runTask(plugin, () -> launchPrivateDuel(sender, target));
            return;
        }

        pendingDuelByTarget.put(target.getUniqueId(), new DuelInvite(sender.getUniqueId(), now + DUEL_INVITE_TTL_MS));
        Msg.sendSocial(sender, Msg.success("Sent a 1v1 Duel challenge to <white><bold>" + target.getName() + "</bold></white> <gray>(expires in 60s)</gray>."));
        Msg.sendSocial(target, "<#FBBF24>⚔ <white><bold>" + sender.getName() + "</bold></white> challenged you to a 1v1 Duel! </#FBBF24>"
            + "<click:run_command:'/duel " + sender.getName() + "'><hover:show_text:'<green>Click to accept the 1v1 Duel</green>'>"
            + "<#4ADE80><bold>[✔ Accept]</bold></#4ADE80></hover></click>");
        Msg.playPersonalPackSound(target, "ui.click", 1.0f, 1.25f);
    }

    public boolean launchPrivateDuel(Player a, Player b) {
        if (a == null || b == null || !a.isOnline() || !b.isOnline()) {
            return false;
        }
        leaveQueue(a, false);
        leaveQueue(b, false);
        for (Player p : new Player[]{a, b}) {
            MatchSession prev = getSessionOf(p.getUniqueId());
            if (prev != null) {
                prev.removePlayer(p, true);
            }
            if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(p.getUniqueId())) {
                plugin.cinematicCamera().skipIfWatching(p);
            }
        }

        Arena arena = findOrCreateFreeArena(a);
        if (arena == null) {
            Msg.send(a, Msg.error("No free court is available for the duel right now."));
            Msg.send(b, Msg.error("No free court is available for the duel right now."));
            return false;
        }
        arena.ensureDisplaysHealthy();
        MatchSession session = new MatchSession(plugin, arena, GameModeType.DUEL_1V1);
        session.setQueueMode(fr.bosketball.model.GameQueueMode.CASUAL);
        sessionsByArena.put(arena.id(), session);
        session.addPlayer(a, TeamSide.IGNIS);
        session.addPlayer(b, TeamSide.FROST);
        session.forceStart();

        Msg.sendFramedCard(a, "1V1 DUEL", java.util.List.of(
            "<gray>Opponent:</gray> <white>" + b.getName() + "</white>",
            "<gray>Court:</gray> <white>" + arena.displayName() + "</white>",
            "<gray>Mode:</gray> <white>CASUAL — no ELO at stake</white>",
            "",
            "<#4ADE80>Tip-off now. Good luck!</#4ADE80>"));
        Msg.sendFramedCard(b, "1V1 DUEL", java.util.List.of(
            "<gray>Opponent:</gray> <white>" + a.getName() + "</white>",
            "<gray>Court:</gray> <white>" + arena.displayName() + "</white>",
            "<gray>Mode:</gray> <white>CASUAL — no ELO at stake</white>",
            "",
            "<#4ADE80>Tip-off now. Good luck!</#4ADE80>"));
        if (plugin.integration() != null) {
            plugin.integration().onMatchLaunched(session);
        }
        return true;
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
