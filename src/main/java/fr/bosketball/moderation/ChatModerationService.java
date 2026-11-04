package fr.bosketball.moderation;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerStatus;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.util.Msg;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ChatModerationService implements Listener {

    private final BosketBallPlugin plugin;
    private final StatsManager statsManager;
    private final Map<UUID, Deque<Long>> messageWindows = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> announceCooldown = new ConcurrentHashMap<>();

    public ChatModerationService(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.statsManager = plugin.statsManager();
    }

    private boolean isStaff(Player player) {
        return player.isOp() || player.hasPermission("bosketball.staff") || player.hasPermission("bosketball.admin");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        handle(player, plain, cleaned -> {
            if (cleaned == null) {
                return;
            }
            event.message(Component.text(cleaned));
        });
    }

    public void handle(Player player, String raw, java.util.function.Consumer<String> applyCleaned) {
        if (player == null || raw == null || raw.isBlank()) {
            return;
        }
        var cfg = plugin.settings().moderation();
        PlayerProfile profile = statsManager.getOrCreate(player);
        long now = System.currentTimeMillis();

        if (profile.isChatMuted(now)) {
            long left = (profile.chatMuteUntilMillis() - now) / 1000L;
            Msg.send(player, Msg.error("You are muted for another <white><bold>" + left + "s</bold></white>."));
            applyCleaned.accept("");
            return;
        }

        if (cfg.antiSpamEnabled() && !isStaff(player)) {
            if (isSpamming(player, raw, cfg, now)) {
                profile.setChatMuteUntilMillis(now + cfg.antiSpamMuteSeconds() * 1000L);
                statsManager.markDirty(player.getUniqueId());
                Msg.send(player, Msg.error("Slow down! You are muted for <white><bold>"
                    + cfg.antiSpamMuteSeconds() + "s</bold></white>."));
                Msg.playPersonalPackSound(player, "ui.click", 0.8f, 0.7f);
                applyCleaned.accept("");
                return;
            }
        }

        if (routeMatchChatIfInGame(player, raw, applyCleaned)) {
            return;
        }

        if (plugin.gameManager() != null && plugin.gameManager().isInPartyChat(player.getUniqueId())) {
            applyCleaned.accept("");
            routeToParty(player, raw);
            return;
        }

        boolean exempt = cfg.exemptStaff() && isStaff(player);
        if (cfg.profanityEnabled() && !exempt && profile.settings().profanityFilter()) {
            ChatFilter.Result result = ChatFilter.scan(raw);
            if (result.altered()) {
                applyCleaned.accept(result.cleaned());
                int points = result.worstSeverity() == ChatFilter.Severity.SEVERE
                    ? cfg.severeWarnPoints()
                    : cfg.warnPoints();
                int total = profile.chatWarnPoints() + points;
                profile.setChatWarnPoints(total);
                statsManager.markDirty(player.getUniqueId());

                if (cfg.notifyAuthor()) {
                    Msg.send(player, Msg.warn("Watch your language — <white>" + result.censoredSummary()
                        + "</white> was censored. <gray>Warning <white><bold>" + total + "/"
                        + cfg.autoBanAtWarnings() + "</bold></white></gray>"));
                    Msg.playPersonalPackSound(player, "ui.click", 0.75f, 0.8f);
                }
                if (total >= cfg.autoBanAtWarnings()) {
                    autoBan(player, profile, cfg);
                }
                return;
            }
        }
        applyCleaned.accept(raw);
    }

    private boolean isSpamming(Player player, String raw, fr.bosketball.config.BosketConfig.Moderation cfg, long now) {
        Deque<Long> window = messageWindows.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(now);
            long horizon = now - cfg.windowSeconds() * 1000L;
            while (!window.isEmpty() && window.peekFirst() < horizon) {
                window.removeFirst();
            }
            if (cfg.blockRepeatedMessages()) {
                String previous = lastMessage.put(player.getUniqueId(), raw.toLowerCase(java.util.Locale.ROOT));
                if (previous != null && previous.equals(raw.toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
            if (window.size() > cfg.messagesPerWindow()) {
                return true;
            }
        }
        if (cfg.minSecondsBetweenMessages() > 0) {
            Long last = announceCooldown.put(player.getUniqueId(), now);
            if (last != null && now - last < cfg.minSecondsBetweenMessages() * 1000L && window.size() >= cfg.messagesPerWindow()) {
                return true;
            }
        }
        return false;
    }

    private void autoBan(Player player, PlayerProfile profile, fr.bosketball.config.BosketConfig.Moderation cfg) {
        String discord = plugin.settings().links().discord();
        String reason = cfg.banReason() + "\n\n<gray>Appeal on our Discord:</gray> <#38BDF8>" + discord + "</#38BDF8>";
        String plainReason = cfg.banReason() + " — Appeal: " + discord;

        profile.setChatWarnPoints(0);
        statsManager.markDirty(player.getUniqueId());
        statsManager.saveProfileAsync(profile);

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.kick(Msg.parse(
                "<#F87171><bold>⛔ BANNED — BOSKETBALL</bold></#F87171>\n\n"
                    + "<gray>Reason:</gray> <white>" + cfg.banReason() + "</white>\n"
                    + "<gray>Duration:</gray> <white>" + cfg.banDurationHours() + " hours</white>\n\n"
                    + "<#38BDF8><bold>Appeal on Discord:</bold></#38BDF8>\n"
                    + "<white><click:open_url:'" + discord + "'>" + discord + "</click></white>"
            ));
            player.ban(plainReason, java.time.Duration.ofHours(cfg.banDurationHours()), "BosketBall-ChatFilter");
        });

        if (plugin.integration() != null) {
            plugin.integration().onModeration(player.getName(), "ban", cfg.banReason(), cfg.autoBanAtWarnings());
        }
        if (plugin.reportService() != null) {
            plugin.reportService().logSanction(player.getName(), "AUTO-BAN", cfg.banReason(), cfg.banDurationHours() * 60L);
        }
        Bukkit.getOnlinePlayers().stream()
            .filter(this::isStaff)
            .forEach(staff -> Msg.send(staff, "<#F87171><bold>⛔ Auto-ban</bold></#F87171> <white>"
                + player.getName() + "</white> <gray>— " + cfg.banReason() + "</gray>"));
    }

    private boolean routeMatchChatIfInGame(Player player, String raw,
                                           java.util.function.Consumer<String> applyCleaned) {
        if (plugin.gameManager() == null) {
            return false;
        }
        var session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (!plugin.settings().chat().matchChatIsolated()) {
            return false;
        }

        applyCleaned.accept("");

        String sanitized = ChatFilter.scan(raw).cleaned();
        var side = session.getTeam(player.getUniqueId());
        String prefixColor = side == null ? "#94A3B8" : String.format("#%06X", side.textColor().value());
        Component line = Msg.parse(
            "<" + prefixColor + "><bold>[MATCH]</bold></" + prefixColor + "> <white>" + player.getName()
                + "</white> <dark_gray>»</dark_gray> <#E2E8F0>" + Msg.escapeMini(sanitized) + "</#E2E8F0>");

        int delivered = 0;
        for (Player viewer : session.onlineParticipants()) {
            viewer.sendMessage(line);
            delivered++;
        }
        if (delivered <= 1) {
            Msg.send(player, "<gray>You are the only one on court — this message stays in the match.</gray>");
        }
        return true;
    }

    private void routeToParty(Player sender, String raw) {
        Component line = Msg.parse("<#A855F7><bold>[PARTY]</bold></#A855F7> <white>" + sender.getName()
            + "</white> <dark_gray>»</dark_gray> <#E2E8F0>" + Msg.escapeMini(raw) + "</#E2E8F0>");
        int sent = 0;
        if (plugin.socialManager() != null) {
            for (Player mate : plugin.socialManager().onlinePartyMembers(sender)) {
                mate.sendMessage(line);
                sent++;
            }
            if (sent == 0) {
                sender.sendMessage(line);
                sent++;
            }
        }

        if (plugin.gameManager() != null) {
            var session = plugin.gameManager().getSessionOf(sender.getUniqueId());
            if (session != null) {
                var side = session.getTeam(sender.getUniqueId());
                for (Player mate : session.onlineParticipants()) {
                    if (mate.equals(sender) || session.getTeam(mate.getUniqueId()) != side) {
                        continue;
                    }
                    if (plugin.socialManager() != null
                        && plugin.socialManager().getParty(sender.getUniqueId()) != null
                        && plugin.socialManager().onlinePartyMembers(sender).contains(mate)) {
                        continue;
                    }
                    mate.sendMessage(line);
                    sent++;
                }
            }
        }
        if (sent <= 1) {
            Msg.send(sender, "<gray>No party members online — only you can see this channel. "
                + "Use <yellow>/pc</yellow> to switch back to global chat.</gray>");
        }
    }

    public boolean mutePlayer(Player target, long durationMinutes, String reason) {
        PlayerProfile profile = statsManager.getOrCreate(target);
        long until = System.currentTimeMillis() + Math.max(1, durationMinutes) * 60_000L;
        profile.setChatMuteUntilMillis(until);
        profile.setChatWarnPoints(0);
        statsManager.markDirty(target.getUniqueId());
        statsManager.saveProfileAsync(profile);
        Msg.send(target, Msg.warn("You were muted for <white><bold>" + durationMinutes
            + " minutes</bold></white>. <gray>" + Msg.escapeMini(reason == null ? "" : reason) + "</gray>"));
        if (plugin.reportService() != null) {
            plugin.reportService().logSanction(target.getName(), "MUTE", reason, durationMinutes);
        }
        if (plugin.integration() != null) {
            plugin.integration().onModeration(target.getName(), "mute", reason, profile.chatWarnPoints());
        }
        return true;
    }

    public void unmutePlayer(Player target) {
        PlayerProfile profile = statsManager.getOrCreate(target);
        profile.setChatMuteUntilMillis(0L);
        profile.setChatWarnPoints(0);
        statsManager.markDirty(target.getUniqueId());
        statsManager.saveProfileAsync(profile);
        Msg.send(target, Msg.success("You were unmuted."));
    }

    public void clearWarnings(Player target) {
        PlayerProfile profile = statsManager.getOrCreate(target);
        profile.setChatWarnPoints(0);
        statsManager.markDirty(target.getUniqueId());
        Msg.send(target, Msg.success("Your chat warnings were cleared."));
    }

    public int warnPoints(Player player) {
        return statsManager.getOrCreate(player).chatWarnPoints();
    }

    public void onQuit(UUID playerId) {
        messageWindows.remove(playerId);
        lastMessage.remove(playerId);
        announceCooldown.remove(playerId);
    }

    public void markActivity(Player player) {
        if (plugin.socialService() != null) {
            plugin.socialService().touch(player.getUniqueId());
            if (plugin.socialService().statusOf(player.getUniqueId()) == PlayerStatus.AFK) {
                plugin.socialService().setStatus(player.getUniqueId(), PlayerStatus.LOBBY);
            }
        }
    }
}
