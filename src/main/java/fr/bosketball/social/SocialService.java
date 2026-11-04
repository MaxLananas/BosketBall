package fr.bosketball.social;

import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerStatus;
import fr.bosketball.social.OfflineMail.Kind;
import org.bukkit.configuration.file.YamlConfiguration;
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
    /** Root folder for {@code mailbox/<uuid>.yml}; null disables persistence (unit tests). */
    private final java.io.File mailboxDir;

    public SocialService(StatsManager statsManager) {
        this(statsManager, null);
    }

    public SocialService(StatsManager statsManager, java.io.File dataFolder) {
        this.statsManager = statsManager;
        this.mailboxDir = dataFolder == null ? null : new java.io.File(dataFolder, "mailbox");
        if (mailboxDir != null && !mailboxDir.exists() && !mailboxDir.mkdirs()) {
            // Non-fatal: mail then simply lives for the uptime of the server, as before.
            Bukkit.getLogger().warning("[BosketBall] Could not create the mailbox folder — offline mail will not persist.");
        }
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

    private java.io.File mailboxFile(UUID playerId) {
        return mailboxDir == null ? null : new java.io.File(mailboxDir, playerId + ".yml");
    }

    /** Loads (once) the on-disk mailbox for a player. Must be called on the main thread. */
    private List<OfflineMail> box(UUID playerId, PlayerProfile profile) {
        List<OfflineMail> live = mailboxes.get(playerId);
        if (live != null) {
            return live;
        }
        return mailboxes.computeIfAbsent(playerId, this::loadMailbox);
    }

    private List<OfflineMail> loadMailbox(UUID playerId) {
        List<OfflineMail> loaded = new ArrayList<>();
        java.io.File file = mailboxFile(playerId);
        if (file == null || !file.isFile()) {
            return loaded;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> raw = yaml.getList("mail");
        if (raw == null) {
            return loaded;
        }
        for (Object entry : raw) {
            if (!(entry instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            Kind kind;
            try {
                kind = Kind.valueOf(String.valueOf(map.get("kind")));
            } catch (IllegalArgumentException e) {
                kind = Kind.SYSTEM;
            }
            List<String> body = new ArrayList<>();
            Object rawBody = map.get("body");
            if (rawBody instanceof List<?> list) {
                for (Object line : list) {
                    body.add(String.valueOf(line));
                }
            }
            OfflineMail mail = new OfflineMail(
                kind,
                map.get("sender") == null ? "Server" : String.valueOf(map.get("sender")),
                map.get("subject") == null ? "" : String.valueOf(map.get("subject")),
                body,
                map.get("sent") instanceof Number n ? n.longValue() : System.currentTimeMillis(),
                map.get("action") == null ? null : String.valueOf(map.get("action"))
            );
            if (Boolean.parseBoolean(String.valueOf(map.get("read")))) {
                mail.markRead();
            }
            loaded.add(mail);
        }
        return loaded;
    }

    /** Writes one player's mailbox to disk. Cheap call, but only invoked on mutations. */
    private void persistMailbox(UUID playerId, List<OfflineMail> box, PlayerProfile profile) {
        java.io.File file = mailboxFile(playerId);
        if (file == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        List<java.util.Map<String, Object>> rows = new ArrayList<>();
        for (OfflineMail m : box) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("kind", m.kind().name());
            row.put("sender", m.senderName());
            row.put("subject", m.subject());
            row.put("body", new ArrayList<>(m.body()));
            row.put("sent", m.sentAtMillis());
            row.put("read", m.read());
            row.put("action", m.actionCommand());
            rows.add(row);
        }
        yaml.set("mail", rows);
        try {
            yaml.save(file);
        } catch (java.io.IOException e) {
            Bukkit.getLogger().warning("[BosketBall] Failed to save mailbox for " + playerId + ": " + e.getMessage());
        }
        if (profile != null) {
            // The durable counter must reflect the *unread* count, not the total: the badge showed
            // "30" for a full but fully-read inbox.
            profile.setUnreadMailCount(unreadCount(box));
        }
    }

    private static int unreadCount(List<OfflineMail> box) {
        int n = 0;
        for (OfflineMail m : box) {
            if (!m.read()) {
                n++;
            }
        }
        return n;
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
        recipient.setUnreadMailCount(unreadCount(box));
        persistMailbox(recipient.uuid(), box, recipient);
        statsManager.markDirty(recipient.uuid());
        statsManager.saveProfileAsync(recipient);

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
        return unreadCount(box(profile.uuid(), profile));
    }

    /**
     * Marks ONE message as read and keeps the durable unread counter (and the on-disk mailbox) in
     * sync. The mailbox GUI used to flip the in-memory flag and save the profile only, so the badge
     * never dropped and the read state was lost on the next restart.
     */
    public boolean markRead(PlayerProfile profile, int index) {
        if (profile == null) {
            return false;
        }
        List<OfflineMail> box = box(profile.uuid(), profile);
        if (index < 0 || index >= box.size()) {
            return false;
        }
        OfflineMail mail = box.get(index);
        if (mail.read()) {
            return true;
        }
        mail.markRead();
        profile.setUnreadMailCount(unreadCount(box));
        persistMailbox(profile.uuid(), box, profile);
        statsManager.markDirty(profile.uuid());
        statsManager.saveProfileAsync(profile);
        return true;
    }

    public void markAllRead(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        List<OfflineMail> box = box(profile.uuid(), profile);
        for (OfflineMail m : box) {
            m.markRead();
        }
        profile.setUnreadMailCount(0);
        persistMailbox(profile.uuid(), box, profile);
        statsManager.markDirty(profile.uuid());
        statsManager.saveProfileAsync(profile);
    }

    public void clearMail(PlayerProfile profile) {
        if (profile == null) {
            return;
        }
        List<OfflineMail> box = box(profile.uuid(), profile);
        box.clear();
        profile.setUnreadMailCount(0);
        persistMailbox(profile.uuid(), box, profile);
        statsManager.markDirty(profile.uuid());
        statsManager.saveProfileAsync(profile);
    }

    /**
     * Evicts cached presence state for a player that left. The mailbox cache is flushed to disk
     * first: it used to be dropped (and earlier even cleared) on quit, which silently destroyed
     * every message the player had not opened yet.
     */
    public void onQuit(UUID playerId) {
        if (playerId != null) {
            List<OfflineMail> live = mailboxes.remove(playerId);
            if (live != null) {
                PlayerProfile profile = statsManager.getIfPresent(playerId);
                persistMailbox(playerId, live, profile);
                if (profile != null) {
                    statsManager.markDirty(playerId);
                    statsManager.saveProfileAsync(profile);
                }
            }
        }
        clear(playerId);
    }
}
