package fr.bosketball.social;

import fr.bosketball.util.Palette;
import fr.bosketball.config.AtomicYamlWriter;
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

public final class SocialService {

    public static final int MAX_MAIL = 30;

    private final StatsManager statsManager;
    private final Map<UUID, PlayerStatus> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivityMillis = new ConcurrentHashMap<>();

    private final java.util.Set<UUID> manualAfk = ConcurrentHashMap.newKeySet();

    private static final long AFK_THRESHOLD_MILLIS = 5L * 60L * 1000L;
    private final Map<UUID, List<OfflineMail>> mailboxes = new ConcurrentHashMap<>();

    private final java.io.File mailboxDir;

    public SocialService(StatsManager statsManager) {
        this(statsManager, null);
    }

    public SocialService(StatsManager statsManager, java.io.File dataFolder) {
        this.statsManager = statsManager;
        this.mailboxDir = dataFolder == null ? null : new java.io.File(dataFolder, "mailbox");
        if (mailboxDir != null && !mailboxDir.exists() && !mailboxDir.mkdirs()) {

            Bukkit.getLogger().warning("[BosketBall] Could not create the mailbox folder — offline mail will not persist.");
        }
    }

    public void setStatus(UUID playerId, PlayerStatus status) {
        if (playerId == null || status == null) {
            return;
        }

        statuses.put(playerId, status);
    }

    public void touch(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastActivityMillis.put(playerId, System.currentTimeMillis());
        manualAfk.remove(playerId);
    }

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

    public PlayerStatus statusOf(UUID playerId) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return PlayerStatus.OFFLINE;
        }
        PlayerStatus base = statuses.getOrDefault(playerId, PlayerStatus.LOBBY);

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

    public boolean isAfk(UUID playerId) {
        return statusOf(playerId) == PlayerStatus.AFK;
    }

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

    /**
     * Round 33 — {@code /ignore} stored the UUIDs, this method knew how to answer, and nothing
     * asked it: {@code GameplayListener} now drops the ignored author from the message's viewers.
     */
    public boolean hidesChatFrom(UUID recipientId, UUID authorId) {
        if (recipientId == null || authorId == null) {
            return false;
        }
        PlayerProfile recipient = statsManager.getIfPresent(recipientId);
        return recipient != null && recipient.isIgnoring(authorId);
    }

    private java.io.File mailboxFile(UUID playerId) {
        return mailboxDir == null ? null : new java.io.File(mailboxDir, playerId + ".yml");
    }

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
        // Round 42: loadConfiguration() returns an EMPTY config for a broken file, and the next
        // persist() then wrote that emptiness back — a corrupt mailbox erased every message it
        // held. The strict read keeps the bytes (as a .corrupt- backup) and starts a fresh box.
        YamlConfiguration yaml;
        try {
            yaml = AtomicYamlWriter.loadStrict(file);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException broken) {
            java.io.File backup = new java.io.File(file.getParentFile(),
                file.getName() + ".corrupt-" + System.currentTimeMillis() + ".bak");
            try {
                java.nio.file.Files.copy(file.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException ignored) {
                // the original file stays where it is: nothing is lost either way
            }
            Bukkit.getLogger().warning("[Mailbox] " + file.getName() + " is unreadable ("
                + broken.getClass().getSimpleName() + "); kept as " + backup.getName()
                + " and starting an empty mailbox.");
            return loaded;
        }
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
        AtomicYamlWriter.writeYamlAtomically(file, null, yaml, Bukkit.getLogger());
        if (profile != null) {

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
            Msg.send(online, "<" + Palette.ICE + "><bold> New mail</bold></" + Palette.ICE + "> <#64748B>·</#64748B> <#F8FAFC>"
                + mail.subject() + "</#F8FAFC> <#94A3B8>from " + mail.senderName() + "</#94A3B8> <#64748B>·</#64748B> "
                + "<#FDE047>/mail</#FDE047>");
            Msg.playPersonalPackSound(online, "ui.click", 0.85f, 1.3f);
        }
    }

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
