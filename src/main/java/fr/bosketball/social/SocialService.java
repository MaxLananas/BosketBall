package fr.bosketball.social;

import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerStatus;
import fr.bosketball.social.OfflineMail.Kind;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Social presence &amp; mailbox service.
 *
 * <p>Owns three things the server was missing:</p>
 * <ul>
 *   <li><b>Rich presence</b> — a live {@link PlayerStatus} per player (lobby / queue / match /
 *       spectating / locker room / tutorial / AFK) pushed to the [TAB] list, the sidebar and
 *       every Friends GUI.</li>
 *   <li><b>Offline mailbox</b> — anything addressed to an offline player is stored in their
 *       profile and surfaced in a real GUI on next join.</li>
 *   <li><b>Ignore list</b> — per-player chat and invitation blocking.</li>
 * </ul>
 */
public final class SocialService {

    /** Max mails kept per profile. */
    public static final int MAX_MAIL = 30;

    private final StatsManager statsManager;
    private final Map<UUID, PlayerStatus> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivityMillis = new ConcurrentHashMap<>();
    /** Players who typed {@code /afk} on purpose; cleared by the next real input. */
    private final java.util.Set<UUID> manualAfk = ConcurrentHashMap.newKeySet();
    /** Inactivity needed before a lobby player is shown as AFK. */
    private static final long AFK_THRESHOLD_MILLIS = 5L * 60L * 1000L;
    private final Map<UUID, List<OfflineMail>> mailboxes = new ConcurrentHashMap<>();

    public SocialService(StatsManager statsManager) {
        this.statsManager = statsManager;
    }

    // =========================================================================
    //  PRESENCE
    // =========================================================================

    public void setStatus(UUID playerId, PlayerStatus status) {
        if (playerId == null || status == null) {
            return;
        }
        // Deliberately does NOT refresh the activity timer. The presence tick re-asserts LOBBY /
        // QUEUE / MATCH for every online player once a second, so refreshing activity here meant
        // idle time was reset 20 times per second and nobody could ever be detected as AFK.
        statuses.put(playerId, status);
    }

    /** Records genuine player input (movement, chat, clicks, commands); clears a manual AFK flag. */
    public void touch(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastActivityMillis.put(playerId, System.currentTimeMillis());
        manualAfk.remove(playerId);
    }

    /** Explicit {@code /afk} toggle. Returns the new AFK state. */
    public boolean toggleAfk(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        if (manualAfk.remove(playerId)) {
            lastActivityMillis.put(playerId, System.currentTimeMillis());
            return false;
        }
        manualAfk.add(playerId);
        return true;
    }

    public boolean isManuallyAfk(UUID playerId) {
        return playerId != null && manualAfk.contains(playerId);
    }

    public long idleMillis(UUID playerId) {
        Long last = playerId == null ? null : lastActivityMillis.get(playerId);
        return last == null ? 0L : System.currentTimeMillis() - last;
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            statuses.remove(playerId);
            lastActivityMillis.remove(playerId);
            manualAfk.remove(playerId);
        }
    }

    /** Resets the idle timer of everyone online (used on reload / world change resets). */
    public void touchAll() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            lastActivityMillis.put(p.getUniqueId(), now);
        }
    }

    /**
     * Resolves the live status, auto-detecting AFK from inactivity (5 min) while preserving
     * stronger states such as MATCH / QUEUE / SPECTATING.
     */
    public PlayerStatus statusOf(UUID playerId) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return PlayerStatus.OFFLINE;
        }
        PlayerStatus base = statuses.getOrDefault(playerId, PlayerStatus.LOBBY);
        // A deliberate /afk always wins, and the states that prove the player is busy (on court,
        // queued, spectating, browsing the 3D locker room, in the tutorial) never decay to AFK.
        if (manualAfk.contains(playerId)) {
            return PlayerStatus.AFK;
        }
        if (base == PlayerStatus.MATCH || base == PlayerStatus.QUEUE || base == PlayerStatus.SPECTATING
            || base == PlayerStatus.TUTORIAL || base == PlayerStatus.LOCKER) {
            return base;
        }
        if (idleMillis(playerId) >= AFK_THRESHOLD_MILLIS) {
            return PlayerStatus.AFK;
        }
        return base;
    }

    public String statusLabel(UUID playerId) {
        return statusOf(playerId).coloredLabel();
    }

    public boolean isAfk(UUID playerId) {
        return statusOf(playerId) == PlayerStatus.AFK;
    }

    // =========================================================================
    //  IGNORE LIST
    // =========================================================================

    public boolean toggleIgnore(PlayerProfile owner, UUID targetId) {
        if (owner == null || targetId == null) {
            return false;
        }
        if (owner.isIgnoring(targetId)) {
            owner.removeIgnored(targetId);
            statsManager.markDirty(owner.uuid());
            return false;
        }
        owner.addIgnored(targetId);
        statsManager.markDirty(owner.uuid());
        return true;
    }

    /** True when {@code recipient} must not see messages from {@code author}. */
    public boolean hidesChatFrom(UUID recipientId, UUID authorId) {
        if (recipientId == null || authorId == null) {
            return false;
        }
        PlayerProfile recipient = statsManager.getIfPresent(recipientId);
        return recipient != null && recipient.isIgnoring(authorId);
    }

    // =========================================================================
    //  MAILBOX
    // =========================================================================

    private List<OfflineMail> box(UUID playerId, PlayerProfile profile) {
        List<OfflineMail> live = mailboxes.computeIfAbsent(playerId, id -> new ArrayList<>());
        if (live.isEmpty() && profile != null && profile.unreadMailCount() > 0) {
            // A previous session left the counter set but the in-memory cache is cold:
            // surface a single "you have pending notifications" card so it is never lost.
            live.add(new OfflineMail(
                Kind.SYSTEM, "Server", "Pending notifications",
                List.of("<gray>You received <white>" + profile.unreadMailCount()
                    + "</white> notification(s) while offline.</gray>"),
                System.currentTimeMillis(), null
            ));
        }
        return live;
    }

    public void deliver(PlayerProfile recipient, OfflineMail mail) {
        if (recipient == null || mail == null) {
            return;
        }
        if (recipient.uuid() != null && recipient.isIgnoring(senderUuidOf(mail))) {
            return;
        }
        List<OfflineMail> box = box(recipient.uuid(), recipient);
        box.add(0, mail);
        while (box.size() > MAX_MAIL) {
            box.remove(box.size() - 1);
        }
        recipient.setUnreadMailCount(box.size());
        statsManager.markDirty(recipient.uuid());

        Player online = Bukkit.getPlayer(recipient.uuid());
        if (online != null) {
            Msg.send(online, "<#38BDF8><bold>📩 New mail</bold></#38BDF8> <dark_gray>•</dark_gray> <white>"
                + mail.subject() + "</white> <gray>from " + mail.senderName() + "</gray> <dark_gray>•</dark_gray> "
                + "<yellow>/mail</yellow>");
            Msg.playPersonalPackSound(online, "ui.click", 0.85f, 1.3f);
        }
    }

    /** Sends a mail to a named player, online or offline (name resolved through the index). */
    public boolean deliverToName(String recipientName, OfflineMail mail) {
        PlayerProfile profile = statsManager.findByNameIgnoreCase(recipientName);
        if (profile == null) {
            Player online = Bukkit.getPlayerExact(recipientName);
            if (online != null) {
                profile = statsManager.getOrCreate(online);
            }
        }
        if (profile == null) {
            return false;
        }
        deliver(profile, mail);
        return true;
    }

    private UUID senderUuidOf(OfflineMail mail) {
        Player sender = Bukkit.getPlayerExact(mail.senderName());
        return sender != null ? sender.getUniqueId() : null;
    }

    public List<OfflineMail> mails(PlayerProfile profile) {
        if (profile == null) {
            return List.of();
        }
        return List.copyOf(box(profile.uuid(), profile));
    }

    public int unreadCount(PlayerProfile profile) {
        if (profile == null) {
            return 0;
        }
        int n = 0;
        for (OfflineMail m : box(profile.uuid(), profile)) {
            if (!m.read()) {
                n++;
            }
        }
        return n;
    }

    public void markAllRead(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        for (OfflineMail m : box(profile.uuid(), profile)) {
            m.markRead();
        }
        profile.setUnreadMailCount(0);
        statsManager.markDirty(profile.uuid());
    }

    public void clearMail(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        box(profile.uuid(), profile).clear();
        profile.setUnreadMailCount(0);
        statsManager.markDirty(profile.uuid());
    }

    /** Evicts cached mailbox state for a player that left. */
    public void onQuit(UUID playerId) {
        clear(playerId);
    }
}
