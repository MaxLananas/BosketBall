package fr.bosketball.game;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.GameQueueMode;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
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
 * ELO-weighted matchmaking with an elastic search window, a live accept/decline prompt and
 * dodge penalties.
 *
 * <h2>Why this replaces the old FIFO queue</h2>
 * The previous engine sorted tickets by {@code queuedAtMillis} only, so the very first
 * player in queue was always matched with whoever came next regardless of skill. A Rookie
 * could be fed to the server MVP six games in a row and quit on day one.
 *
 * <h2>How the window works</h2>
 * <pre>
 *   window(t) = min(eloWindowMax, eloWindowStart + floor(t / expandEvery) * eloWindowGrowth)
 * </pre>
 * A candidate is only considered when {@code |candidateElo - groupElo| <= window}. After
 * {@code maxWaitSeconds} the window is forced fully open so nobody waits forever on a
 * low-population server.
 *
 * <h2>Accept prompt ("dodge")</h2>
 * When a full lobby is found, every selected player receives a clickable accept prompt with
 * a countdown. All must accept within {@code accept-timeout-seconds}; if one declines (or
 * lets it time out) the found match is dissolved, the remaining players return to the queue
 * at the front, and the declining player receives an escalating queue lockout.
 */
public final class MatchmakingEngine {

    /** A found-but-unconfirmed match waiting for accepts. */
    public static final class FoundMatch {
        private final String id;
        private final GameModeType mode;
        private final GameQueueMode queueMode;
        private final List<UUID> members;
        private final Set<UUID> accepted = ConcurrentHashMap.newKeySet();
        private final Set<UUID> declined = ConcurrentHashMap.newKeySet();
        private final long createdAtMillis = System.currentTimeMillis();
        private final long expiresAtMillis;

        FoundMatch(String id, GameModeType mode, GameQueueMode queueMode, List<UUID> members, long timeoutMillis) {
            this.id = id;
            this.mode = mode;
            this.queueMode = queueMode;
            this.members = List.copyOf(members);
            this.expiresAtMillis = createdAtMillis + timeoutMillis;
        }

        public String id() {
            return id;
        }

        public GameModeType mode() {
            return mode;
        }

        public GameQueueMode queueMode() {
            return queueMode;
        }

        public List<UUID> members() {
            return members;
        }

        public boolean isAcceptedBy(UUID id) {
            return accepted.contains(id);
        }

        public boolean isDeclined(UUID id) {
            return declined.contains(id);
        }

        public boolean isComplete() {
            return accepted.containsAll(members);
        }

        public boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }

        public long remainingSeconds(long nowMillis) {
            return Math.max(0L, (expiresAtMillis - nowMillis + 999L) / 1000L);
        }

        public int acceptedCount() {
            return accepted.size();
        }
    }

    private final BosketBallPlugin plugin;
    private final Map<UUID, FoundMatch> pendingByPlayer = new ConcurrentHashMap<>();
    private final Map<String, FoundMatch> pendingById = new ConcurrentHashMap<>();
    private long nextId = 1L;

    public MatchmakingEngine(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    //  SELECTION
    // =========================================================================

    /** Elastic window for a ticket that has been waiting {@code waitedSeconds}. */
    public int windowFor(long waitedSeconds) {
        var cfg = plugin.settings().matchmaking();
        if (!cfg.eloWeighted()) {
            return Integer.MAX_VALUE;
        }
        if (waitedSeconds >= cfg.maxWaitSeconds()) {
            return Integer.MAX_VALUE;
        }
        long steps = Math.max(0L, waitedSeconds / Math.max(1, cfg.expandEverySeconds()));
        long window = cfg.eloWindowStart() + steps * cfg.eloWindowGrowth();
        return (int) Math.min(cfg.eloWindowMax(), window);
    }

    /**
     * Picks the best group of tickets for a mode.
     *
     * <p>Selection is party-aware: a party leader's ticket and their members' tickets are
     * pulled together so a squad is never split. The group whose average ELO is closest to
     * the anchor player's ELO wins, then the longest-waiting anchor is used as tie-break.</p>
     *
     * @return an ordered list of player ids ready to play, or an empty list when nobody fits.
     */
    public List<UUID> selectGroup(GameModeType mode, boolean forceLaunch, Map<UUID, Long> queuedAtMillis) {
        int required = mode.maxPlayers();
        List<UUID> tickets = new ArrayList<>(queuedAtMillis.keySet());
        tickets.sort(Comparator.comparingLong(queuedAtMillis::get));

        List<UUID> best = List.of();
        double bestScore = Double.MAX_VALUE;

        for (UUID anchorId : tickets) {
            Player anchor = Bukkit.getPlayer(anchorId);
            if (anchor == null || !anchor.isOnline()) {
                continue;
            }
            PlayerProfile anchorProfile = plugin.statsManager().getOrCreate(anchor);
            long waited = Math.max(0L, (System.currentTimeMillis() - queuedAtMillis.getOrDefault(anchorId, System.currentTimeMillis())) / 1000L);
            int window = windowFor(waited);

            List<UUID> group = new ArrayList<>();
            List<UUID> partyBlock = partyBlockOf(anchorId, queuedAtMillis);
            if (partyBlock.size() > required) {
                continue;
            }
            group.addAll(partyBlock);

            List<UUID> candidates = new ArrayList<>();
            for (UUID other : tickets) {
                if (group.contains(other) || partyBlock.contains(other)) {
                    continue;
                }
                Player p = Bukkit.getPlayer(other);
                if (p != null && p.isOnline()) {
                    candidates.add(other);
                }
            }
            candidates.sort(Comparator.comparingInt(id -> {
                PlayerProfile prof = plugin.statsManager().getIfPresent(id);
                return prof == null ? 0 : Math.abs(prof.elo() - anchorProfile.elo());
            }));

            for (UUID candidate : candidates) {
                if (group.size() >= required) {
                    break;
                }
                PlayerProfile prof = plugin.statsManager().getIfPresent(candidate);
                int elo = prof == null ? 1000 : prof.elo();
                if (window != Integer.MAX_VALUE && Math.abs(elo - anchorProfile.elo()) > window) {
                    continue;
                }
                List<UUID> sub = partyBlockOf(candidate, queuedAtMillis);
                if (group.size() + sub.size() > required) {
                    continue;
                }
                for (UUID id : sub) {
                    if (!group.contains(id)) {
                        group.add(id);
                    }
                }
            }

            if (group.size() >= required) {
                double score = averageEloGap(group);
                if (score < bestScore) {
                    bestScore = score;
                    best = List.copyOf(group.subList(0, required));
                }
            } else if (forceLaunch && group.size() >= Math.max(1, mode.minTotalPlayersToStart())) {
                return List.copyOf(group);
            }
        }
        return best;
    }

    private double averageEloGap(List<UUID> group) {
        double sum = 0.0;
        int n = 0;
        for (UUID id : group) {
            PlayerProfile p = plugin.statsManager().getIfPresent(id);
            if (p != null) {
                sum += p.elo();
                n++;
            }
        }
        double avg = n == 0 ? 1000.0 : sum / n;
        double gap = 0.0;
        for (UUID id : group) {
            PlayerProfile p = plugin.statsManager().getIfPresent(id);
            if (p != null) {
                gap += Math.abs(p.elo() - avg);
            }
        }
        return n == 0 ? 0.0 : gap / n;
    }

    /** The queued party block (leader + members) an id belongs to, capped to what is queued. */
    private List<UUID> partyBlockOf(UUID id, Map<UUID, Long> queuedAtMillis) {
        List<UUID> block = new ArrayList<>();
        block.add(id);
        if (plugin.socialManager() == null) {
            return block;
        }
        var party = plugin.socialManager().getParty(id);
        if (party == null) {
            return block;
        }
        for (UUID member : party.members()) {
            if (!member.equals(id) && queuedAtMillis.containsKey(member) && !block.contains(member)) {
                block.add(member);
            }
        }
        return block;
    }

    // =========================================================================
    //  ACCEPT PROMPT
    // =========================================================================

    /**
     * Opens an accept prompt for a found group. Returns {@code false} when the prompt is
     * disabled (the caller should launch immediately).
     */
    public boolean proposeMatch(GameModeType mode, GameQueueMode queueMode, List<UUID> members) {
        var cfg = plugin.settings().matchmaking();
        if (!cfg.acceptPromptEnabled() || members.size() < 2) {
            return false;
        }
        String id = "m" + (nextId++);
        FoundMatch found = new FoundMatch(id, mode, queueMode, members, cfg.acceptTimeoutSeconds() * 1000L);
        pendingById.put(id, found);
        for (UUID member : members) {
            pendingByPlayer.put(member, found);
        }

        for (UUID member : members) {
            Player p = Bukkit.getPlayer(member);
            if (p == null || !p.isOnline()) {
                continue;
            }
            int elo = plugin.statsManager().getOrCreate(p).elo();
            String modeLabel = queueMode == GameQueueMode.CASUAL ? "CASUAL" : "RANKED";
            p.sendMessage(Component.text()
                .append(Msg.parse(Msg.pillTag(fr.bosketball.util.Glyphs.SYS_COURT) + " "))
                .append(Msg.parse("<#4ADE80><bold>✔ MATCH FOUND</bold></#4ADE80> <dark_gray>•</dark_gray> <white><bold>"
                    + mode.smallCapsTitle() + "</bold></white> <gray>(" + modeLabel + " • " + elo + " ELO)</gray>"))
                .append(Component.text("  "))
                .append(Component.text("[✔ ACCEPT]", Style.style(TextColor.color(0x4ADE80), TextDecoration.BOLD))
                    .hoverEvent(HoverEvent.showText(Msg.parse("<green>Accept and enter the court</green>")))
                    .clickEvent(ClickEvent.runCommand("/accept")))
                .append(Component.text("  "))
                .append(Component.text("[✖ DECLINE]", Style.style(TextColor.color(0xF87171), TextDecoration.BOLD))
                    .hoverEvent(HoverEvent.showText(Msg.parse("<red>Decline (-" + cfg.dodgePenaltySeconds() + "s queue lockout)</red>")))
                    .clickEvent(ClickEvent.runCommand("/decline")))
                .build());
            Msg.title(p, "<#4ADE80><bold>✔ MATCH FOUND</bold></#4ADE80>",
                "<gray>Accept within <white>" + cfg.acceptTimeoutSeconds() + "s</white> <dark_gray>•</dark_gray> "
                    + members.size() + " players", 100, 1600, 300);
            Msg.playPersonalPackSound(p, "game.whistle", 1.0f, 1.15f);
        }
        return true;
    }

    /** Handles {@code /accept}. */
    public void accept(Player player) {
        FoundMatch found = pendingByPlayer.get(player.getUniqueId());
        if (found == null) {
            Msg.send(player, Msg.warn("No pending match to accept."));
            return;
        }
        found.accepted.add(player.getUniqueId());
        Msg.send(player, Msg.success("Match accepted <white>(" + found.acceptedCount() + "/" + found.members().size() + ")</white>."));
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.35f);
        if (found.isComplete()) {
            launch(found);
        }
    }

    /** Handles {@code /decline} — dissolves the match and penalises the declining player. */
    public void decline(Player player) {
        FoundMatch found = pendingByPlayer.get(player.getUniqueId());
        if (found == null) {
            Msg.send(player, Msg.warn("No pending match to decline."));
            return;
        }
        found.declined.add(player.getUniqueId());
        applyDodgePenalty(player);
        dissolve(found, player.getUniqueId());
    }

    private void applyDodgePenalty(Player player) {
        var cfg = plugin.settings().matchmaking();
        long now = System.currentTimeMillis();
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        long lastDecline = profile.matchmakingBanUntilMillis();
        // Escalating penalty: each dodge within the reset window multiplies the lockout.
        int count = profile.dodgeCount();
        if (lastDecline > 0 && now - lastDecline > cfg.dodgeCounterResetMinutes() * 60_000L) {
            count = 0;
        }
        int multiplier = (int) Math.pow(Math.max(1, cfg.dodgePenaltyMultiplier()), Math.min(4, count));
        long penaltyMs = Math.max(0, cfg.dodgePenaltySeconds()) * 1000L * Math.max(1, multiplier);
        profile.setDodgeCount(count + 1);
        profile.setMatchmakingBanUntilMillis(now + penaltyMs);
        plugin.statsManager().markDirty(player.getUniqueId());

        plugin.gameManager().leaveQueue(player, false);
        Msg.send(player, Msg.error("You declined the match. Queue lockout: <white><bold>"
            + (penaltyMs / 1000L) + "s</bold></white> <gray>(dodge " + (count + 1) + ")</gray>."));
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 0.7f);
    }

    /** Player disconnected / went offline while a match was pending. */
    public void handleQuit(UUID playerId) {
        FoundMatch found = pendingByPlayer.get(playerId);
        if (found != null) {
            found.declined.add(playerId);
            dissolve(found, playerId);
        }
    }

    private void dissolve(FoundMatch found, UUID offender) {
        pendingById.remove(found.id());
        for (UUID member : found.members()) {
            pendingByPlayer.remove(member);
            if (!member.equals(offender)) {
                Player p = Bukkit.getPlayer(member);
                if (p != null && p.isOnline()) {
                    Msg.send(p, Msg.warn("The match was cancelled — returning you to the front of the queue."));
                    // Put them back at the head of the queue without losing their wait time
                    plugin.gameManager().requeueAfterDodge(p, found.mode(), found.queueMode());
                }
            }
        }
    }

    /** Launches the confirmed match. */
    private void launch(FoundMatch found) {
        pendingById.remove(found.id());
        for (UUID member : found.members()) {
            pendingByPlayer.remove(member);
        }
        plugin.gameManager().launchConfirmedMatch(found.mode(), found.queueMode(), found.members());
    }

    /** Ticked once per second to expire prompts. */
    public void tick() {
        if (pendingById.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (FoundMatch found : new ArrayList<>(pendingById.values())) {
            if (!found.isExpired(now)) {
                continue;
            }
            int accepted = found.acceptedCount();
            pendingById.remove(found.id());
            List<UUID> timeoutIds = new ArrayList<>();
            for (UUID member : found.members()) {
                pendingByPlayer.remove(member);
                if (!found.accepted.contains(member)) {
                    timeoutIds.add(member);
                }
            }
            for (UUID id : found.members()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    Msg.send(p, Msg.error("Match accept timed out (" + accepted + "/" + found.members().size()
                        + " accepted). Returning to queue."));
                }
            }
            for (UUID id : timeoutIds) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    applyDodgePenalty(p);
                }
            }
            for (UUID id : found.members()) {
                if (found.accepted.contains(id)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        plugin.gameManager().requeueAfterDodge(p, found.mode(), found.queueMode());
                    }
                }
            }
        }
    }

    /** All players with an open prompt (used by the queue HUD). */
    public Set<UUID> pendingPlayers() {
        return new HashSet<>(pendingByPlayer.keySet());
    }

    public FoundMatch pendingOf(UUID playerId) {
        return playerId == null ? null : pendingByPlayer.get(playerId);
    }

    public void clear() {
        pendingByPlayer.clear();
        pendingById.clear();
    }
}
