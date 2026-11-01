package fr.bosketball.game;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.ArenaBuilder;
import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Coordinates all active MatchSessions across all Stadium Arenas + 1v1 Duel Invitations!
 */
public final class GameManager {

    private final BosketBallPlugin plugin;
    private final Map<String, MatchSession> sessionsByArena = new HashMap<>();
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

    public int activePlayerCount() {
        int sum = 0;
        for (MatchSession s : sessionsByArena.values()) {
            if (s.state() != MatchSession.State.FINISHED) {
                sum += s.participantCount();
            }
        }
        return sum;
    }

    public void tickAll() {
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
     * Joins an existing waiting session for the requested mode, or starts one on a free Arena
     * (auto-building a default Stadium if the server has zero arenas configured yet!).
     */
    public void quickJoin(Player player, GameModeType mode, String specificArenaId) {
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
        Arena freeArena = null;
        for (Arena a : plugin.arenaManager().allArenas()) {
            MatchSession s = sessionsByArena.get(a.id());
            if (s == null || s.state() == MatchSession.State.FINISHED) {
                freeArena = a;
                break;
            }
        }

        // Auto-build main stadium if admin hasn't built one yet and player has permission/op!
        if (freeArena == null && plugin.arenaManager().allArenas().isEmpty()) {
            Msg.send(player, "<yellow>🏗 ɢᴇɴᴇʀᴀᴛɪᴏɴ ᴀᴜᴛᴏᴍᴀᴛɪǫᴜᴇ ᴅᴜ <bold>ꜱᴛᴀᴅᴇ ʙᴏꜱᴋᴇᴛʙᴀʟʟ ᴀʀᴇɴᴀ</bold>...</yellow>");
            freeArena = ArenaBuilder.buildFullStadium("main", "ʙᴏꜱᴋᴇᴛ ᴄᴇɴᴛᴇʀ", player.getLocation().add(0, -1, 22));
            plugin.arenaManager().registerArena(freeArena);
        }

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
        for (MatchSession s : sessionsByArena.values()) {
            s.stopAndCleanup();
        }
        sessionsByArena.clear();
    }
}
