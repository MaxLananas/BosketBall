package fr.bosketball.moderation;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerStatus;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BosketChatRenderer;
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

    /** What the deterministic chat ladder asks for after an insult. */
    public enum Sanction { NONE, MUTE, PERMANENT_BAN }

    /**
     * Sentinel handed back by {@link #handle}: the message is consumed by a private channel
     * (match, party, mute…) and must NOT be broadcast to the public chat at all.
     */
    public static final String CANCEL = "\u0000BOSKETBALL_CANCEL";

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
        Player eventPlayer = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!event.isAsynchronous()) {
            handle(eventPlayer, raw, cleaned -> {
                if (CANCEL.equals(cleaned)) {
                    event.setCancelled(true);
                } else if (cleaned != null) {
                    event.message(Component.text(cleaned));
                }
            });
            return;
        }

        java.util.UUID playerId = eventPlayer.getUniqueId();
        java.util.concurrent.atomic.AtomicReference<String> cleaned = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            boolean processed = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    return false;
                }
                handle(player, raw, cleaned::set);
                return true;
            }).get(2L, java.util.concurrent.TimeUnit.SECONDS);
            if (!processed) {
                event.setCancelled(true);
                return;
            }
            String message = cleaned.get();
            if (CANCEL.equals(message)) {
                event.setCancelled(true);
            } else if (message != null) {
                event.message(Component.text(message));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            event.setCancelled(true);
        } catch (java.util.concurrent.TimeoutException
                 | java.util.concurrent.ExecutionException | RuntimeException ex) {
            event.setCancelled(true);
        }
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
            applyCleaned.accept(CANCEL);
            return;
        }

        if (cfg.antiSpamEnabled() && !isStaff(player)) {
            if (isSpamming(player, raw, cfg, now)) {
                profile.setChatMuteUntilMillis(now + cfg.antiSpamMuteSeconds() * 1000L);
                statsManager.markDirty(player.getUniqueId());
                Msg.send(player, Msg.error("Slow down! You are muted for <white><bold>"
                    + cfg.antiSpamMuteSeconds() + "s</bold></white>."));
                Msg.playPersonalPackSound(player, "ui.click", 0.8f, 0.7f);
                applyCleaned.accept(CANCEL);
                return;
            }
        }

        // 1. Profanity is evaluated first so every channel below gets the censored text and the
        //    deterministic ladder (3 insults -> 1 h mute, 3 mutes -> permanent ban) applies everywhere.
        String outbound = raw;
        boolean exempt = cfg.exemptStaff() && isStaff(player);
        if (cfg.profanityEnabled() && !exempt && profile.settings().profanityFilter()) {
            ChatFilter.Result result = ChatFilter.scan(raw);
            if (result.altered()) {
                int insults = profile.incrementChatWarnPoints();
                statsManager.markDirty(player.getUniqueId());
                Sanction sanction = sanctionFor(insults, profile.chatMuteCount(), cfg);
                if (sanction != Sanction.NONE) {
                    applyCleaned.accept(CANCEL);
                    if (sanction == Sanction.PERMANENT_BAN) {
                        permanentBan(player, profile, cfg, result);
                    } else {
                        ladderMute(player, profile, cfg, result, insults);
                    }
                    return;
                }
                outbound = result.cleaned();
                if (cfg.notifyAuthor()) {
                    Msg.send(player, Msg.warn("Watch your language — <white>" + result.censoredSummary()
                        + "</white> was censored. <gray>Insult <white><bold>" + insults + "/"
                        + cfg.effectiveInsultsBeforeMute() + "</bold></white> — next step is a <white>"
                        + cfg.muteHours() + " h</white> mute.</gray>"));
                    Msg.playPersonalPackSound(player, "ui.click", 0.75f, 0.8f);
                }
            }
        }

        // 2. Channel routing: match > party > guild > public. Routed channels always cancel the
        //    public broadcast, so an empty line can never reach the global chat.
        if (routeMatchChatIfInGame(player, outbound, applyCleaned)) {
            return;
        }

        if (plugin.gameManager() != null && plugin.gameManager().isInPartyChat(player.getUniqueId())) {
            applyCleaned.accept(CANCEL);
            routeToParty(player, outbound);
            return;
        }

        // "/g" toggles guild chat: while it is on, the message must never reach the public chat.
        if (plugin.guildService() != null && plugin.guildService().isGuildChatOn(player.getUniqueId())) {
            applyCleaned.accept(CANCEL);
            plugin.guildService().sendGuildChat(player, outbound);
            return;
        }

        applyCleaned.accept(outbound);
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

    /**
     * Deterministic escalation ladder.
     *
     * <p>{@code insultsAfter} already includes the message being processed; {@code mutes} is the
     * number of automatic mutes already served. Every {@code insultsBeforeMute} insults trigger a
     * mute, and the {@code mutesBeforeBan}-th mute is replaced by a permanent ban.</p>
     */
    public static Sanction sanctionFor(int insultsAfter, int mutes, fr.bosketball.config.BosketConfig.Moderation cfg) {
        return sanctionFor(insultsAfter, mutes, cfg.effectiveInsultsBeforeMute(), cfg.effectiveMutesBeforeBan());
    }

    /** Pure ladder used by {@link #sanctionFor} and by the tests. */
    public static Sanction sanctionFor(int insultsAfter, int mutes, int insultsBeforeMute, int mutesBeforeBan) {
        int step = Math.max(1, insultsBeforeMute);
        int cap = Math.max(2, mutesBeforeBan);
        if (insultsAfter < step) {
            return Sanction.NONE;
        }
        return mutes + 1 >= cap ? Sanction.PERMANENT_BAN : Sanction.MUTE;
    }

    private void ladderMute(Player player, PlayerProfile profile, fr.bosketball.config.BosketConfig.Moderation cfg,
                            ChatFilter.Result result, int insults) {
        int mutes = profile.incrementChatMuteCount();
        profile.setChatWarnPoints(0);
        long until = System.currentTimeMillis() + cfg.effectiveMuteMillis();
        profile.setChatMuteUntilMillis(until);
        statsManager.markDirty(player.getUniqueId());
        statsManager.saveProfileAsync(profile);

        Msg.send(player, Msg.error("You are muted for <white><bold>" + cfg.muteHours()
            + " h</bold></white> — <white>" + insults + "/"
            + cfg.effectiveInsultsBeforeMute() + "</white> insults reached."
            + " <gray>Mute <white>" + mutes + "/" + cfg.effectiveMutesBeforeBan()
            + "</white> — the next one is a permanent ban.</gray>"));
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 0.7f);

        if (plugin.reportService() != null) {
            plugin.reportService().logSanction(player.getName(), "AUTO-MUTE",
                cfg.banReason() + " [" + result.censoredSummary() + "]", cfg.muteHours() * 60L);
        }
        if (plugin.integration() != null) {
            plugin.integration().onModeration(player.getName(), "mute", cfg.banReason(), mutes);
        }
        notifyStaff(player, "\u23f8 Auto-mute", cfg.muteHours() + " h — mute " + mutes + "/"
            + cfg.effectiveMutesBeforeBan());
    }

    private void permanentBan(Player player, PlayerProfile profile,
                              fr.bosketball.config.BosketConfig.Moderation cfg, ChatFilter.Result result) {
        String discord = plugin.settings().links().discord();
        String plainReason = cfg.banReason() + " — Appeal: " + discord;

        profile.setChatWarnPoints(0);
        statsManager.markDirty(player.getUniqueId());
        statsManager.saveProfileAsync(profile);

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.kick(Msg.parse(
                "<#F87171><bold>\u26d4 BANNED — BOSKETBALL</bold></#F87171>\n\n"
                    + "<gray>Reason:</gray> <white>" + cfg.banReason() + "</white>\n"
                    + "<gray>Duration:</gray> <white>permanent</white>\n\n"
                    + "<#38BDF8><bold>Appeal on Discord:</bold></#38BDF8>\n"
                    + "<white><click:open_url:'" + discord + "'>" + discord + "</click></white>"
            ));
            player.ban(plainReason, (java.util.Date) null, "BosketBall-ChatFilter");
        });

        if (plugin.integration() != null) {
            plugin.integration().onModeration(player.getName(), "ban", cfg.banReason(), -1);
        }
        if (plugin.reportService() != null) {
            plugin.reportService().logSanction(player.getName(), "AUTO-BAN",
                cfg.banReason() + " [" + result.censoredSummary() + "]", -1L);
        }
        notifyStaff(player, "\u26d4 Auto-ban (permanent)", cfg.effectiveMutesBeforeBan()
            + " mutes reached — " + result.censoredSummary());
    }

    private void notifyStaff(Player player, String label, String detail) {
        Bukkit.getOnlinePlayers().stream()
            .filter(this::isStaff)
            .forEach(staff -> Msg.send(staff, "<#F87171><bold>" + label + "</bold></#F87171> <white>"
                + player.getName() + "</white> <gray>— " + Msg.escapeMini(detail) + "</gray>"));
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

        applyCleaned.accept(CANCEL);

        String sanitized = ChatFilter.scan(raw).cleaned();
        var side = session.getTeam(player.getUniqueId());
        String prefixColor = side == null ? "#94A3B8" : String.format(java.util.Locale.ROOT, "#%06X", side.textColor().value());
        // Same rank / tag / guild pills as the public chat: the match channel must not strip them.
        Component line = Msg.parse("<" + prefixColor + "><bold>[MATCH]</bold></" + prefixColor + "> ")
            .append(BosketChatRenderer.buildPlayerChatPrefix(
                player, statsManager, plugin.gameManager(), plugin.guildService(),
                plugin.settings().chat().showTopStatBadges()))
            .append(Msg.parse("<#E2E8F0>" + Msg.escapeMini(sanitized) + "</#E2E8F0>"));

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
        Component line = Msg.parse("<#A855F7><bold>[PARTY]</bold></#A855F7> ")
            .append(BosketChatRenderer.buildPlayerChatPrefix(
                sender, statsManager, plugin.gameManager(), plugin.guildService(),
                plugin.settings().chat().showTopStatBadges()))
            .append(Msg.parse("<#E2E8F0>" + Msg.escapeMini(raw) + "</#E2E8F0>"));
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

    /** Live view of a player's chat ladder, for staff commands. */
    public record ChatRecord(int insults, int muteCount, int insultsBeforeMute, int mutesBeforeBan,
                             int muteHours, long mutedUntilMillis) {
        public boolean muted(long now) {
            return mutedUntilMillis > now;
        }
    }

    public ChatRecord recordOf(PlayerProfile profile) {
        var cfg = plugin.settings().moderation();
        return new ChatRecord(profile.chatWarnPoints(), profile.chatMuteCount(),
            cfg.effectiveInsultsBeforeMute(), cfg.effectiveMutesBeforeBan(), cfg.muteHours(),
            profile.chatMuteUntilMillis());
    }

    /** Resets the insult counter; {@code alsoMutes} also wipes the mute history and unmutes. */
    public void resetLadder(PlayerProfile profile, boolean alsoMutes) {
        profile.setChatWarnPoints(0);
        if (alsoMutes) {
            profile.setChatMuteCount(0);
            profile.setChatMuteUntilMillis(0L);
        }
        statsManager.markDirty(profile.uuid());
        statsManager.saveProfileAsync(profile);
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
