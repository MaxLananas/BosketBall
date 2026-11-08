package fr.bosketball.listener;

import fr.bosketball.util.Palette;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.arena.MapEditorManager;
import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.server.OnboardingCues;
import fr.bosketball.social.SocialSurfaces;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GameplayListener implements Listener {

    private static final NamespacedKey ICON_ARENA_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_arrow_big_right");
    private static final NamespacedKey ICON_PORTAL_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_portal");
    private static final NamespacedKey ICON_SHIELD_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_star_shield");
    private static final NamespacedKey ICON_CHEST_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_chest");
    private static final NamespacedKey ICON_TAGS_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_bag");
    private static final NamespacedKey ICON_FRIENDS_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_friends");
    private static final NamespacedKey ICON_BOOK_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_medal_gold");
    private static final NamespacedKey ICON_PODIUM_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_cup_gold");
    private static final NamespacedKey ICON_LEAVE_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_exit");
    private static final NamespacedKey LEGACY_PLAYBOOK_KEY = new NamespacedKey(BallSkin.NAMESPACE, "tactics_playbook");

    // Lobby hotbar doors, taken from the pack's own icon library (167 ref_* sprites) so the
    // toolbar, the hub and the menus all speak the same visual language.
    // Order matches BallSkin.LOBBY_HOTBAR_MODELS.
    private static final NamespacedKey BADGE_MODES_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_play");
    private static final NamespacedKey BADGE_SHOP_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_shop");
    private static final NamespacedKey BADGE_LOCKER_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_clothing");
    private static final NamespacedKey BADGE_MENU_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_catalogue");
    private static final NamespacedKey BADGE_FRIENDS_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_member");
    private static final NamespacedKey BADGE_RANKS_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_medal_gold");
    private static final NamespacedKey BADGE_TOP_KEY = new NamespacedKey(BallSkin.NAMESPACE, "ref_cup_gold");

    private static final NamespacedKey PROP_PLAY_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_play_emblem");
    private static final NamespacedKey PROP_LOCKER_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_locker_jersey");
    private static final NamespacedKey PROP_SHOP_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_shop_crate");
    private static final NamespacedKey PROP_TAG_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_tag_plaque");
    private static final NamespacedKey PROP_SOCIAL_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_social_megaphone");
    private static final NamespacedKey PROP_GUIDE_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_guide_clipboard");
    private static final NamespacedKey PROP_PODIUM_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_podium_cup");

    private final BosketBallPlugin plugin;
    private final BosketChatRenderer chatRenderer;
    /** The first ten minutes: one held slot, one guarded rail, one card at a time. */
    private final OnboardingCues.Director onboarding;
    private final java.util.Map<java.util.UUID, Long> lastLobbyHotbarClickMs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastChatMessageMs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, String> lastChatMessageText = new java.util.concurrent.ConcurrentHashMap<>();

    private record ChatDecision(boolean cancelled, boolean useRenderer) {
    }

    public GameplayListener(BosketBallPlugin plugin, BosketChatRenderer chatRenderer) {
        this.plugin = plugin;
        this.chatRenderer = chatRenderer;
        this.onboarding = new OnboardingCues.Director(plugin);
        this.onboarding.start();
    }

    public static String checkChatViolation(
        long nowMs,
        long prevMs,
        String prevNormalized,
        String currentRaw,
        boolean staffBypass,
        boolean globalChatMuted
    ) {
        if (globalChatMuted && !staffBypass) {
            return "Chat is currently muted by a server administrator.";
        }
        if (staffBypass) {
            return null;
        }
        if (prevMs > 0L && (nowMs - prevMs) < 900L) {
            return "Please slow down slightly before sending another message!";
        }
        String norm = currentRaw == null ? "" : currentRaw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!norm.isEmpty() && norm.length() > 3 && norm.equals(prevNormalized) && (nowMs - prevMs) < 8_000L) {
            return "Please avoid repeating the exact same message.";
        }
        return null;
    }

    // Round 27: ChatModerationService (LOW) already routed/cancelled private channels. Without
    // ignoreCancelled this handler re-sent party & guild lines a second time (and spied them twice).
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player eventPlayer = event.getPlayer();
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(event.message());
        ChatDecision decision;
        if (!event.isAsynchronous()) {
            decision = processChat(eventPlayer, plain);
        } else {
            java.util.UUID playerId = eventPlayer.getUniqueId();
            try {
                decision = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    return player == null ? new ChatDecision(true, false) : processChat(player, plain);
                }).get(2L, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                event.setCancelled(true);
                return;
            } catch (java.util.concurrent.TimeoutException ex) {
                event.setCancelled(true);
                Msg.send(eventPlayer, Msg.warn("Chat is lagging — message not sent. Try again."));
                return;
            } catch (java.util.concurrent.ExecutionException | RuntimeException ex) {
                event.setCancelled(true);
                return;
            }
        }
        applyChatDecision(event, decision);
    }

    private void applyChatDecision(AsyncChatEvent event, ChatDecision decision) {
        if (decision == null || decision.cancelled()) {
            event.setCancelled(true);
        } else if (decision.useRenderer()) {
            event.renderer(chatRenderer);
        }
        if (!event.isCancelled()) {
            hideIgnoredViewers(event);
        }
    }

    /**
     * Round 33 — {@code /ignore} stored the ignored UUIDs and {@code SocialService.hidesChatFrom}
     * knew how to answer, but nobody asked: an ignored player's messages stayed on screen. Instead
     * of making the renderer viewer-aware (one render per player, see {@link BosketChatRenderer}),
     * the ignored author is simply removed from this event's viewer set.
     */
    private void hideIgnoredViewers(AsyncChatEvent event) {
        if (plugin.socialService() == null || event.viewers().isEmpty()) {
            return;
        }
        java.util.UUID authorId = event.getPlayer().getUniqueId();
        event.viewers().removeIf(viewer -> viewer instanceof Player watcher
            && !watcher.getUniqueId().equals(authorId)
            && plugin.socialService().hidesChatFrom(watcher.getUniqueId(), authorId));
    }

    private ChatDecision processChat(Player player, String plain) {
        java.util.UUID playerId = player.getUniqueId();
        if (plugin.socialManager() != null && plugin.socialManager().hasPendingChatPrompt(playerId)) {
            plugin.socialManager().tryConsumeChatPrompt(player, plain);
            return new ChatDecision(true, false);
        }

        boolean staffBypass = Perms.staff(player);
        long now = System.currentTimeMillis();
        long prevMs = lastChatMessageMs.getOrDefault(playerId, 0L);
        String prevNorm = lastChatMessageText.get(playerId);
        String violation = checkChatViolation(now, prevMs, prevNorm, plain, staffBypass, plugin.isGlobalChatMuted());
        if (violation != null) {
            Msg.send(player, Msg.warn(violation));
            return new ChatDecision(true, false);
        }
        lastChatMessageMs.put(playerId, now);
        lastChatMessageText.put(playerId, plain.trim().toLowerCase(java.util.Locale.ROOT));

        if (plugin.guildService() != null && plugin.guildService().isGuildChatOn(playerId)) {
            plugin.guildService().sendGuildChat(player, plain);
            return new ChatDecision(true, false);
        }

        if (plugin.gameManager().isInPartyChat(playerId)) {
            if (plugin.socialManager() != null) {
                plugin.socialManager().sendPartyChat(player, plain);
            }
            return new ChatDecision(true, false);
        }

        boolean useRenderer = plugin.settings().chat().enabled();
        if (plain.contains("@")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String lower = plain.toLowerCase(java.util.Locale.ROOT);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getUniqueId().equals(playerId)
                        && lower.contains("@" + online.getName().toLowerCase(java.util.Locale.ROOT))) {
                        Msg.playPersonalPackSound(online, "ui.click", 0.55f, 1.6f);
                    }
                }
            });
        }
        return new ChatDecision(false, useRenderer);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.permissionManager() != null) {
            plugin.permissionManager().applyPermissions(player);
        }
        if (plugin.lang() != null) {
            plugin.lang().applyClientLocale(player);
        }

        var reconnectSession = plugin.gameManager().getSessionOf(player.getUniqueId());
        boolean restoredToMatch = reconnectSession != null
            && reconnectSession.cancelDisconnectGrace(player.getUniqueId());
        if (restoredToMatch) {

            player.setFireTicks(0);
            player.setFallDistance(0f);
            Msg.send(player, Msg.info("Welcome back — your match is still running."));
        } else {
            plugin.sanitizeJoinerToCleanLobbyState(player);
        }

        boolean firstJoin = plugin.statsManager().getIfPresent(player.getUniqueId()) == null;
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        profile.syncExclusiveFounderState();
        if (firstJoin) {
            profile.setCoins(plugin.settings().general().startingCoins());
        }
        plugin.statsManager().saveProfileAsync(profile);
        plugin.statsManager().refreshLeaderboardBadges();

        // Round 33: the daily-login streak was configurable, persisted, and never triggered.
        var progression = plugin.settings().progression();
        if (progression != null && progression.enabled() && progression.dailyLoginEnabled()
            && plugin.progressService() != null) {
            plugin.progressService().claimDailyLogin(player, profile, progression.streakRewards());
        }
        if (plugin.seasonService() != null) {
            plugin.seasonService().claimPending(player);
        }

        if (plugin.settings().chat().joinQuitMessages()) {
            event.joinMessage(Msg.parse(
                Msg.pillTag(fr.bosketball.util.Glyphs.SYS_JOIN) + " "
                    + Msg.pillTag(profile.effectiveRank().pillGlyph()) + " "
                    + "<" + Palette.TEXT + ">" + player.getName() + "</" + Palette.TEXT + ">"
            ));
        }

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            PlayerProfile otherProf = plugin.statsManager().getIfPresent(other.getUniqueId());
            if (otherProf != null && otherProf.isFriend(player.getUniqueId())) {
                Msg.sendSocial(other, "Your friend <#F8FAFC><bold>" + player.getName() + "</bold></#F8FAFC> <" + Palette.SUCCESS + ">is now online!</" + Palette.SUCCESS + "> "
                    + "<click:run_command:'/party invite " + player.getName() + "'><hover:show_text:'<#38BDF8>Invite to Party</#38BDF8>'><" + Palette.ICE + "><bold>[ Invite]</bold></" + Palette.ICE + "></hover></click> "
                    + "<click:run_command:'/duel " + player.getName() + "'><hover:show_text:'<#FBBF24>Challenge to 1v1 Duel</#FBBF24>'><" + Palette.WARN + "><bold>[ Duel]</bold></" + Palette.WARN + "></hover></click>");
                Msg.playPersonalPackSound(other, "ui.click", 0.55f, 1.55f);
            }
        }

        plugin.cinematicCamera().hideActiveFromLateJoiner(player);
        for (MatchSession s : plugin.gameManager().activeSessions()) {
            s.syncSpectatorInvisibility();
        }
        if (plugin.nameplateService() != null) {
            // Round 37: the rank plates are entities of their own, so a fresh joiner has to be told
            // who they may see. Doing it here means the very first frame is already correct.
            plugin.nameplateService().refreshAll();
        }

        if (plugin.settings().general().autoEquipLobbyPlaybook()) {
            BallSkin.equipLobbyHotbarIfEmpty(player, false);
        }
        // The held slot is the first lesson: no sentence is needed to see where to click.
        onboarding.onJoin(player);
        if (firstJoin && plugin.socialManager() != null
            && plugin.socialManager().countOnlineFriends(player.getUniqueId()) == 0) {
            Msg.sendSocial(player, SocialSurfaces.emptyLine(SocialSurfaces.Surface.FRIEND));
        }
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().updatePlayerSidebar(player, plugin.gameManager());
        }
        if (plugin.tabListManager() != null) {
            plugin.tabListManager().updateAll(plugin.gameManager());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            var welcome = plugin.settings().welcome();
            var links = plugin.settings().links();
            if (welcome.enabled()) {
                Msg.sendIllustratedWelcomeCard(
                    player,
                    links.website(),
                    links.shop(),
                    links.discord(),
                    welcome.headline(),
                    welcome.subtitle(),
                    profile.unreadMailCount()
                );
            }
            // Identity is delivered by the card, or by the nameplate when cards are off.
            onboarding.markIdentitySeen(player.getUniqueId());
        }, 12L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.tutorialManager() != null) {
                plugin.tutorialManager().maybeStartOnJoin(player);
            }
        }, 40L);

        if (plugin.settings().resourcePack().autoSendOnJoin()
            && plugin.resourcePackHost().isReadyToSend()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.resourcePackHost().sendPackTo(player);
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Per-player state first: managers below may already need it gone.
        plugin.cleanupPlayerState(player);
        lastLobbyHotbarClickMs.remove(player.getUniqueId());
        lastChatMessageMs.remove(player.getUniqueId());
        lastChatMessageText.remove(player.getUniqueId());
        if (plugin.settings().chat().joinQuitMessages()) {
            event.quitMessage(Msg.parse(
                Msg.pillTag(fr.bosketball.util.Glyphs.SYS_QUIT) + " <" + Palette.MUTED + ">" + player.getName() + "</" + Palette.MUTED + ">"
            ));
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                PlayerProfile otherProf = plugin.statsManager().getIfPresent(other.getUniqueId());
                if (otherProf != null && otherProf.isFriend(player.getUniqueId())) {
                    Msg.sendSocial(other, "Your friend <#F8FAFC>" + player.getName() + "</#F8FAFC> <" + Palette.MUTED + ">went offline.</" + Palette.MUTED + ">");
                }
            }
        }
        if (plugin.permissionManager() != null) {
            plugin.permissionManager().removePlayer(player);
        }
        if (plugin.protectionManager() != null) {
            plugin.protectionManager().clearBuildMode(player.getUniqueId());
        }
        if (plugin.coreCommands() != null) {
            plugin.coreCommands().clearPlayerState(player.getUniqueId());
        }
        plugin.clearLobbyRig(player.getUniqueId(), false);
        plugin.mapEditor().removePlayer(player.getUniqueId());
        plugin.cinematicCamera().stopWatching(player, true);
        plugin.gameManager().leaveQueue(player, false);
        plugin.gameManager().handleDisconnect(player.getUniqueId());
        plugin.socialManager().handleDisconnect(player.getUniqueId());

        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null) {
            session.removePlayer(player, false);
        }
        PlayerProfile quittingProf = plugin.statsManager().getIfPresent(player.getUniqueId());
        if (quittingProf != null) {
            plugin.statsManager().saveProfileAsync(quittingProf);
        }
        plugin.bossBarHud().clearPlayer(player);
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
        }
        if (plugin.tabListManager() != null) {
            plugin.tabListManager().clearPlayerCache(player.getUniqueId());
        }
        if (plugin.resourcePackHost() != null) {
            plugin.resourcePackHost().clearPlayerState(player.getUniqueId());
        }
        onboarding.onQuit(player.getUniqueId());
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        boolean retrying = plugin.resourcePackHost() != null
            && plugin.resourcePackHost().handlePackStatus(
                player,
                status.name(),
                task -> Bukkit.getScheduler().runTaskLater(plugin, task, 10L)
            );
        switch (status) {
            case SUCCESSFULLY_LOADED -> {
                Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.35f);
                if (plugin.sidebarManager() != null) {
                    plugin.sidebarManager().clearPlayerCache(player.getUniqueId());
                }
                if (plugin.tabListManager() != null) {
                    plugin.tabListManager().clearPlayerCache(player.getUniqueId());
                    plugin.tabListManager().updateAll(plugin.gameManager());
                }
            }
            case DECLINED -> {
                if (!plugin.settings().resourcePack().required()) {
                    Msg.sendFramedCard(player, "Official Resource Pack Recommended", List.of(
                        "<#94A3B8>BosketBall uses a lightweight official pack for</#94A3B8> <#F8FAFC>3D Basketballs,</#F8FAFC>",
                        "<#F8FAFC>Hoops, Scoreboards, Collector Tags, Custom GUIs & Court Audio</#F8FAFC><#94A3B8>!</#94A3B8>",
                        "<click:run_command:'/pack'><hover:show_text:'<#4ADE80>Click to load the BosketBall pack</#4ADE80>'><" + Palette.SUCCESS + "><bold>[ Click Here to Load Official Pack]</bold></" + Palette.SUCCESS + "></hover></click>"
                    ));
                }
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                if (!retrying) {
                    Msg.sendAlert(player, "Resource Pack download interrupted. "
                        + "<click:run_command:'/pack'><hover:show_text:'<#FDE047>Retry downloading pack</#FDE047>'><" + Palette.GOLD + "><bold>[↻ Retry Download]</bold></" + Palette.GOLD + "></hover></click>");
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiGuard(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiMenuManager.BosketHolder) {
            return;
        }
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null) {
            event.setCancelled(true);
            return;
        }

        boolean creativeBypass = player.getGameMode() == GameMode.CREATIVE
            && player.isSneaking()
            && player.hasPermission("bosketball.admin");
        if (!creativeBypass) {
            ItemStack hotbarSwapped = event.getHotbarButton() >= 0
                ? player.getInventory().getItem(event.getHotbarButton())
                : null;
            boolean offhandSwap = event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && isBosketItem(player.getInventory().getItemInOffHand());
            if (isBosketItem(event.getCurrentItem())
                || isBosketItem(event.getCursor())
                || isBosketItem(hotbarSwapped)
                || offhandSwap) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDragGuard(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
            || isBosketItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId())) {
            event.setCancelled(true);
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                plugin.cinematicCamera().skipIfWatching(player);
            }
            return;
        }


        // Round 32: a chapter that only asks for reading turns on ANY click. The owner kept being
        // stuck in front of a card he had finished because only the chat button was wired.
        if (plugin.tutorialManager() != null && plugin.tutorialManager().advanceReadingChapter(player)) {
            event.setCancelled(true);
            return;
        }

        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {

            if (MapEditorManager.isArchitectWand(event.getItem())) {
                event.setCancelled(true);
                if (!Perms.map(player)) {
                    Msg.send(player, Msg.error("Arena creation requires <#F8FAFC>bosketball.map</#F8FAFC>."));
                    return;
                }
                if (action == Action.RIGHT_CLICK_AIR && player.isSneaking()) {
                    plugin.guiMenu().openMapStudio(player);
                    return;
                }
                if (event.getClickedBlock() != null && (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)) {
                    boolean first = (action == Action.LEFT_CLICK_BLOCK);
                    if (player.isSneaking()) {
                        plugin.mapEditor().setBarrierCorner(player, event.getClickedBlock().getLocation(), first);
                    } else {
                        plugin.mapEditor().setCourtCorner(player, event.getClickedBlock().getLocation(), first);
                    }
                    return;
                }
                return;
            }

            NamespacedKey modelKey = getItemModelKey(event.getItem());
            if (modelKey != null && BallSkin.NAMESPACE.equals(modelKey.getNamespace())) {
                event.setCancelled(true);
                boolean rightClick = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
                boolean leftClick = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
                if (rightClick || leftClick) {
                    long now = System.currentTimeMillis();
                    Long prev = lastLobbyHotbarClickMs.put(player.getUniqueId(), now);
                    if (prev != null && (now - prev) < 150L) {
                        return;
                    }
                    if (BADGE_MODES_KEY.equals(modelKey)) {
                        plugin.guiMenu().openPlayModes(player);
                    } else if (BADGE_SHOP_KEY.equals(modelKey)) {
                        if (rightClick) {
                            plugin.guiMenu().openCosmetics(player);
                        } else {
                            plugin.guiMenu().openShop(player);
                        }
                    } else if (BADGE_LOCKER_KEY.equals(modelKey)) {
                        if (rightClick) {
                            plugin.guiMenu().openTagsVault(player, 0);
                        } else {
                            plugin.guiMenu().openLockerRoom(player);
                        }
                    } else if (BADGE_MENU_KEY.equals(modelKey)) {
                        plugin.guiMenu().openMainHub(player);
                    } else if (BADGE_FRIENDS_KEY.equals(modelKey)) {
                        if (player.isSneaking()) {
                            plugin.guiMenu().openGuildMenu(player);
                        } else if (rightClick) {
                            plugin.guiMenu().openPartyMenu(player);
                        } else {
                            plugin.guiMenu().openFriendsMenu(player);
                        }
                    } else if (BADGE_RANKS_KEY.equals(modelKey)) {
                        plugin.guiMenu().openRanksGuide(player);
                    } else if (BADGE_TOP_KEY.equals(modelKey)) {
                        plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.ELO);
                    } else if (PROP_PLAY_KEY.equals(modelKey) || ICON_ARENA_KEY.equals(modelKey) || ICON_SHIELD_KEY.equals(modelKey) || LEGACY_PLAYBOOK_KEY.equals(modelKey)) {
                        plugin.guiMenu().openPlayModes(player);
                    } else if (PROP_LOCKER_KEY.equals(modelKey) || ICON_PORTAL_KEY.equals(modelKey)) {
                        plugin.guiMenu().openLockerRoom(player);
                    } else if (PROP_SHOP_KEY.equals(modelKey) || ICON_CHEST_KEY.equals(modelKey)) {
                        plugin.guiMenu().openShop(player);
                    } else if (PROP_TAG_KEY.equals(modelKey) || ICON_TAGS_KEY.equals(modelKey)) {
                        if (rightClick) {
                            plugin.guiMenu().openTagsVault(player, 0);
                        } else {
                            plugin.guiMenu().openCosmetics(player);
                        }
                    } else if (PROP_SOCIAL_KEY.equals(modelKey) || ICON_FRIENDS_KEY.equals(modelKey)) {
                        if (rightClick) {
                            plugin.guiMenu().openPartyMenu(player);
                        } else {
                            plugin.guiMenu().openFriendsMenu(player);
                        }
                    } else if (PROP_GUIDE_KEY.equals(modelKey) || ICON_BOOK_KEY.equals(modelKey)) {
                        if (rightClick) {
                            plugin.guiMenu().openRanksGuide(player);
                        } else {
                            plugin.guiMenu().openMainHub(player);
                        }
                    } else if (PROP_PODIUM_KEY.equals(modelKey) || ICON_PODIUM_KEY.equals(modelKey)) {
                        plugin.guiMenu().openLeaderboards(player, StatsManager.LeaderboardCategory.ELO);
                    } else if (ICON_LEAVE_KEY.equals(modelKey)) {
                        plugin.gameManager().leaveQueue(player, true);
                    }
                }
            }
            return;
        }

        int slot = player.getInventory().getHeldItemSlot();
        boolean isClick = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK
            || action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);

        if (session.isCeremonyState()) {
            event.setCancelled(true);
            if (isClick) {
                long now = System.currentTimeMillis();
                Long prev = lastLobbyHotbarClickMs.put(player.getUniqueId(), now);
                if (prev == null || (now - prev) >= 180L) {
                    session.handleCeremonyHotbarAction(player, getItemModelKey(event.getItem()));
                }
            }
            return;
        }

        if (session.getTeam(player.getUniqueId()) == TeamSide.SPECTATOR) {
            event.setCancelled(true);
            if (isClick) {
                long now = System.currentTimeMillis();
                Long prev = lastLobbyHotbarClickMs.put(player.getUniqueId(), now);
                if (prev == null || (now - prev) >= 180L) {
                    session.handleSpectatorHotbarAction(player, slot);
                }
            }
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            switch (slot) {
                case 0 -> {
                    // A live charge must never be cancelled by an interaction packet: cancelling
                    // the use would abort the vanilla bow draw and drop the bar mid-charge.
                    if (session.canBeginBowDraw(player) || session.isChargingShot(player)) {
                        event.setUseInteractedBlock(Event.Result.DENY);
                        event.setUseItemInHand(Event.Result.ALLOW);
                    } else {
                        event.setCancelled(true);
                        session.tryLockDunkOnRightClick(player);
                    }
                }
                case 1 -> {
                    event.setCancelled(true);
                    session.handlePassOrAlleyOop(player);
                }
                case 2 -> {
                    event.setCancelled(true);
                    session.handleCrossoverMove(player);
                }
                case 3 -> {
                    event.setCancelled(true);
                    if (player.isSneaking()) {
                        cycleOwnedDunkStyle(player);
                    } else {
                        session.handleBallHandlerPrimaryUse(player, false, true);
                    }
                }
                case 4 -> {
                    event.setCancelled(true);
                    session.toggleSparringBot(player);
                }
                case 8 -> {
                    event.setCancelled(true);
                    session.removePlayer(player, false);
                }
                default -> event.setCancelled(true);
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            session.handleDefensiveAction(player);
        } else {
            event.setCancelled(true);
        }
    }

    private void cycleOwnedDunkStyle(Player player) {
        PlayerProfile prof = plugin.statsManager().getOrCreate(player);
        List<DunkStyle> owned = new ArrayList<>(prof.unlockedDunks());
        if (owned.size() <= 1) {
            Msg.rail(player, fr.bosketball.util.HudSurface.LOBBY, "<" + Palette.GOLD
                + "><bold>One dunk style</bold></" + Palette.GOLD + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.MUTED + ">more in /shop</" + Palette.MUTED + ">");
            return;
        }
        int cur = owned.indexOf(prof.selectedDunkStyle());
        DunkStyle next = owned.get((cur + 1) % owned.size());
        prof.setSelectedDunkStyle(next);
        plugin.statsManager().saveProfileAsync(prof);
        player.getInventory().setItem(3, GuiMenuManager.customIcon(next.iconModelId(),
            "<" + Palette.GOLD + "><bold>Dunk Style: " + next.displayName() + "</bold></" + Palette.GOLD + ">",
            List.of("<#94A3B8>Inside the paint, face the rim, then click once to SLAM (20s cd)</#94A3B8>")));
        Msg.rail(player, fr.bosketball.util.HudSurface.LOBBY, "<" + Palette.GOLD + "><bold>"
            + next.displayName() + "</bold></" + Palette.GOLD + ">" + fr.bosketball.util.HudCopy.SEP
            + "<" + Palette.MUTED + ">dunk style set</" + Palette.MUTED + ">");
        Msg.playPersonalPackSound(player, "ui.click", 0.7f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            if (isBosketItem(event.getBow())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        session.handleBowShotRelease(player, event.getForce());
    }

    @EventHandler
    public void onItemConsumeGuard(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null || isBosketItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        // Click one of two: sneak + right-click a player opens the report card. The card's reason
        // buttons are click two. A plain right-click still opens the player's profile menu.
        if (player.isSneaking() && plugin.reportService() != null) {
            Player reported = resolveProfileClickTarget(player, event.getRightClicked());
            if (reported != null && player.canSee(reported)) {
                event.setCancelled(true);
                plugin.reportService().openReportCard(player, reported);
                return;
            }
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {

            if (plugin.lobbyHolograms() != null
                && plugin.lobbyHolograms().handlePossibleStationClick(player, event.getRightClicked())) {
                event.setCancelled(true);
                return;
            }

            if (plugin.crateStation() != null && plugin.crateStation().isCrateEntity(event.getRightClicked())) {
                event.setCancelled(true);
                plugin.crateStation().handleClick(player);
                return;
            }

            if (plugin.tutorialManager() != null
                && plugin.tutorialManager().isTutorialEntity(event.getRightClicked())) {
                event.setCancelled(true);
                plugin.tutorialManager().start(player);
                return;
            }

            // Round 26: a plain right-click on somebody (or on their floating nameplate, which is
            // what the crosshair actually hits) opens their profile, with the friend/duel/party
            // shortcuts inside. Sneaking is no longer required.
            Player profileTarget = resolveProfileClickTarget(player, event.getRightClicked());
            if (profileTarget != null && player.canSee(profileTarget)) {
                event.setCancelled(true);
                if (plugin.gameManager().isInMatch(profileTarget.getUniqueId())) {
                    // Spectate has a reason inside a match: one click on a player on the court puts
                    // you on their sideline. Both the watcher and the watched are told.
                    MatchSession live = plugin.gameManager().getSessionOf(profileTarget.getUniqueId());
                    if (live != null && live.mode() != GameModeType.PRACTICE) {
                        plugin.gameManager().spectateSession(player, live);
                        SocialSurfaces.TwoSided watching = SocialSurfaces.lines(
                            SocialSurfaces.Surface.SPECTATE, player.getName(), profileTarget.getName());
                        Msg.sendSocial(player, watching.actorLine());
                        Msg.sendSocial(profileTarget, watching.targetLine());
                    } else {
                        Msg.sendSocial(player, SocialSurfaces.emptyLine(SocialSurfaces.Surface.SPECTATE));
                    }
                    return;
                }
                plugin.guiMenu().openPlayerInteractMenu(player, profileTarget);
                return;
            }
            if (isBosketItem(player.getInventory().getItemInMainHand()) || isBosketItem(player.getInventory().getItemInOffHand())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getRightClicked() instanceof ArmorStand) {
            event.setCancelled(true);
        } else if (event.getRightClicked() instanceof Interaction) {
            if (!session.canBeginBowDraw(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)
            || plugin.gameManager().getSessionOf(event.getPlayer().getUniqueId()) != null
            || isBosketItem(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Maps a right-clicked entity to the player it visually represents: the player itself, or the
     * nearest player under a floating display (overhead nameplate, floor ring…).
     */
    private Player resolveProfileClickTarget(Player viewer, org.bukkit.entity.Entity clicked) {
        if (clicked == null || viewer == null) {
            return null;
        }
        if (clicked instanceof Player target) {
            return target.getUniqueId().equals(viewer.getUniqueId()) ? null : target;
        }
        if (!(clicked instanceof org.bukkit.entity.Display)
            && !(clicked instanceof org.bukkit.entity.Interaction)) {
            return null;
        }
        if (!clicked.getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)) {
            return null;
        }
        org.bukkit.Location hit = clicked.getLocation();
        if (hit.getWorld() == null) {
            return null;
        }
        Player best = null;
        double bestDist = 3.2 * 3.2;
        for (Player other : hit.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            double d = other.getLocation().distanceSquared(hit);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private static NamespacedKey getItemModelKey(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getItemModel() : null;
    }

    private static boolean isBosketItem(ItemStack item) {
        NamespacedKey key = getItemModelKey(item);
        return key != null && BallSkin.NAMESPACE.equals(key.getNamespace());
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId())) {
                event.setCancelled(true);
                plugin.cinematicCamera().skipIfWatching(player);
                return;
            }
            if (plugin.lobbyHolograms() != null
                && plugin.lobbyHolograms().handlePossibleStationClick(player, event.getEntity())) {
                event.setCancelled(true);
                return;
            }
            MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
            if (session == null && plugin.crateStation() != null
                && plugin.crateStation().isCrateEntity(event.getEntity())) {
                event.setCancelled(true);
                plugin.crateStation().handleClick(player);
                return;
            }
            if (session == null && plugin.tutorialManager() != null
                && plugin.tutorialManager().isTutorialEntity(event.getEntity())) {
                event.setCancelled(true);
                plugin.tutorialManager().start(player);
                return;
            }
            if (session != null) {
                event.setCancelled(true);
                if (session.getTeam(player.getUniqueId()) != TeamSide.SPECTATOR && !session.isCeremonyState()) {
                    session.handleDefensiveAction(player);
                }
            }
        }
        if (event.getEntity().getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)
            || event.getEntity().getScoreboardTags().contains(fr.bosketball.ui.LobbyHologramManager.HOLO_ENTITY_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null) {
            event.setCancelled(true);
            if (session.getTeam(player.getUniqueId()) != TeamSide.SPECTATOR && !session.isCeremonyState()) {
                session.handlePassOrAlleyOop(player);
            }
            return;
        }
        if (isBosketItem(event.getItemDrop().getItemStack())
            && !(player.getGameMode() == GameMode.CREATIVE && player.isSneaking() && player.hasPermission("bosketball.admin"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            if (isBosketItem(event.getMainHandItem()) || isBosketItem(event.getOffHandItem())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (session.getTeam(player.getUniqueId()) == TeamSide.SPECTATOR || session.isCeremonyState()) {
            return;
        }
        boolean holdingBall = session.ball() != null && player.getUniqueId().equals(session.ball().holderId());
        if (holdingBall) {
            session.handleCrossoverMove(player);
        }
    }

    @EventHandler
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId())) {
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null && !session.isCeremonyState() && !player.isHandRaised()
            && session.getTeam(player.getUniqueId()) != TeamSide.SPECTATOR
            && session.ball() != null && player.getUniqueId().equals(session.ball().holderId())) {
            session.handleCrossoverMove(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
            && plugin.gameManager().getSessionOf(player.getUniqueId()) != null) {
            event.setCancelled(true);
        } else if (event.getEntity().getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)
            || event.getEntity().getScoreboardTags().contains(fr.bosketball.ui.LobbyHologramManager.HOLO_ENTITY_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
            && plugin.gameManager().getSessionOf(player.getUniqueId()) != null) {
            event.setCancelled(true);
            event.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.cinematicCamera().isWatching(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.cinematicCamera().handleSpectateStopAttempt(player);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null
            || isBosketItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        event.getDrops().removeIf(GameplayListener::isBosketItem);
        MatchSession session = plugin.gameManager().getSessionOf(p.getUniqueId());
        if (session != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            session.removePlayer(p, true);
        }
        plugin.clearLobbyRig(p.getUniqueId(), false);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (plugin.settings().general().autoEquipLobbyPlaybook()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !plugin.gameManager().isInMatch(p.getUniqueId())) {
                    BallSkin.equipLobbyHotbarIfEmpty(p, false);
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player p = event.getPlayer();
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        String loadedName = event.getWorld().getName();
        for (Arena arena : plugin.arenaManager().allArenas()) {
            if (loadedName.equalsIgnoreCase(arena.worldName())) {
                arena.spawnDisplays();
            }
        }
    }
}
