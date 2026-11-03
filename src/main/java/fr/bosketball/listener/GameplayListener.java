package fr.bosketball.listener;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.arena.MapEditorManager;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BosketChatRenderer;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.ui.LockerRoom3dMenuManager;
import fr.bosketball.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
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

/**
 * Event Bridge connecting Player Inputs, Lobby Hotbar, Court Controls, Anti-Duplication Guards & Welcome Card.
 */
public final class GameplayListener implements Listener {

    private static final NamespacedKey ICON_ARENA_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_arena");
    private static final NamespacedKey ICON_PORTAL_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_portal");
    private static final NamespacedKey ICON_SHIELD_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_star_shield");
    private static final NamespacedKey ICON_CHEST_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_chest");
    private static final NamespacedKey ICON_TAGS_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_tags");
    private static final NamespacedKey ICON_FRIENDS_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_friends");
    private static final NamespacedKey ICON_BOOK_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_book");
    private static final NamespacedKey ICON_PODIUM_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_podium");
    private static final NamespacedKey ICON_LEAVE_KEY = new NamespacedKey(BallSkin.NAMESPACE, "icon_leave");
    private static final NamespacedKey LEGACY_PLAYBOOK_KEY = new NamespacedKey(BallSkin.NAMESPACE, "tactics_playbook");

    // True 3D Blockbench-Grade Lobby Hotbar Props
    private static final NamespacedKey PROP_PLAY_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_play_emblem");
    private static final NamespacedKey PROP_LOCKER_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_locker_jersey");
    private static final NamespacedKey PROP_SHOP_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_shop_crate");
    private static final NamespacedKey PROP_TAG_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_tag_plaque");
    private static final NamespacedKey PROP_SOCIAL_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_social_megaphone");
    private static final NamespacedKey PROP_GUIDE_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_guide_clipboard");
    private static final NamespacedKey PROP_PODIUM_KEY = new NamespacedKey(BallSkin.NAMESPACE, "prop_podium_cup");

    private final BosketBallPlugin plugin;
    private final BosketChatRenderer chatRenderer;
    private final java.util.Map<java.util.UUID, Long> lastLobbyHotbarClickMs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastChatMessageMs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, String> lastChatMessageText = new java.util.concurrent.ConcurrentHashMap<>();

    public GameplayListener(BosketBallPlugin plugin, BosketChatRenderer chatRenderer) {
        this.plugin = plugin;
        this.chatRenderer = chatRenderer;
    }

    /**
     * Pure helper for JUnit verification of chat anti-spam &amp; duplicate-flood protection.
     */
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.socialManager() != null && plugin.socialManager().hasPendingChatPrompt(player.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    plugin.socialManager().tryConsumeChatPrompt(player, plain);
                }
            });
            return;
        }

        boolean staffBypass = player.isOp()
            || player.hasPermission("bosketball.admin")
            || player.hasPermission("bosketball.staff");
        long now = System.currentTimeMillis();
        long prevMs = lastChatMessageMs.getOrDefault(player.getUniqueId(), 0L);
        String prevNorm = lastChatMessageText.get(player.getUniqueId());
        String violation = checkChatViolation(now, prevMs, prevNorm, plain, staffBypass, plugin.isGlobalChatMuted());
        if (violation != null) {
            event.setCancelled(true);
            Msg.send(player, Msg.warn(violation));
            return;
        }
        lastChatMessageMs.put(player.getUniqueId(), now);
        lastChatMessageText.put(player.getUniqueId(), plain.trim().toLowerCase(java.util.Locale.ROOT));

        if (plugin.settings().chat().enabled()) {
            event.renderer(chatRenderer);
        }

        // Subtle @Mention audio ping for online players mentioned in chat
        if (plain.contains("@")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String lower = plain.toLowerCase(java.util.Locale.ROOT);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getUniqueId().equals(player.getUniqueId())
                        && lower.contains("@" + online.getName().toLowerCase(java.util.Locale.ROOT))) {
                        Msg.playPersonalPackSound(online, "ui.click", 0.55f, 1.6f);
                    }
                }
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.permissionManager() != null) {
            plugin.permissionManager().applyPermissions(player);
        }

        plugin.sanitizeJoinerToCleanLobbyState(player);

        // A player who crashed or timed out gets his seat back if he returns inside the grace
        // window: cancel the timer before anything else touches his session.
        var reconnectSession = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (reconnectSession != null && reconnectSession.cancelDisconnectGrace(player.getUniqueId())) {
            Msg.send(player, Msg.info("Welcome back — your match is still running."));
        }

        boolean firstJoin = plugin.statsManager().getIfPresent(player.getUniqueId()) == null;
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        profile.syncExclusiveFounderState();
        if (firstJoin) {
            profile.setCoins(plugin.settings().general().startingCoins());
        }
        plugin.statsManager().saveProfileAsync(profile);
        plugin.statsManager().refreshLeaderboardBadges();

        if (plugin.settings().chat().joinQuitMessages()) {
            event.joinMessage(Msg.parse(
                Msg.pillTag(fr.bosketball.util.Glyphs.SYS_JOIN) + " "
                    + Msg.pillTag(profile.effectiveRank().pillGlyph()) + " "
                    + "<#F1F5F9>" + player.getName() + "</#F1F5F9>"
            ));
        }

        // MCC Island-style Friend Online Notification + subtle chime for online friends!
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            PlayerProfile otherProf = plugin.statsManager().getIfPresent(other.getUniqueId());
            if (otherProf != null && otherProf.isFriend(player.getUniqueId())) {
                Msg.sendSocial(other, "Your friend <white><bold>" + player.getName() + "</bold></white> <#4ADE80>is now online!</#4ADE80> "
                    + "<click:run_command:'/party invite " + player.getName() + "'><hover:show_text:'<aqua>Invite to Party</aqua>'><#38BDF8><bold>[🎉 Invite]</bold></#38BDF8></hover></click> "
                    + "<click:run_command:'/duel " + player.getName() + "'><hover:show_text:'<gold>Challenge to 1v1 Duel</gold>'><#FF8C1A><bold>[⚔ Duel]</bold></#FF8C1A></hover></click>");
                Msg.playPersonalPackSound(other, "ui.click", 0.55f, 1.55f);
            }
        }

        // Hide private per-viewer HUD, camera & court spectator entities
        plugin.lockerRoom3dManager().hideActivePropsFromJoiner(player);
        plugin.cinematicCamera().hideActiveFromLateJoiner(player);
        for (MatchSession s : plugin.gameManager().activeSessions()) {
            s.hideRigEntitiesFromNewJoiner(player);
            s.syncSpectatorInvisibility();
        }

        if (plugin.settings().general().autoEquipLobbyPlaybook()) {
            BallSkin.equipLobbyHotbarIfEmpty(player, false);
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
        }, 12L);

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
        lastLobbyHotbarClickMs.remove(player.getUniqueId());
        lastChatMessageMs.remove(player.getUniqueId());
        lastChatMessageText.remove(player.getUniqueId());
        if (plugin.settings().chat().joinQuitMessages()) {
            event.quitMessage(Msg.parse(
                Msg.pillTag(fr.bosketball.util.Glyphs.SYS_QUIT) + " <#94A3B8>" + player.getName() + "</#94A3B8>"
            ));
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                PlayerProfile otherProf = plugin.statsManager().getIfPresent(other.getUniqueId());
                if (otherProf != null && otherProf.isFriend(player.getUniqueId())) {
                    Msg.sendSocial(other, "Your friend <white>" + player.getName() + "</white> <#94A3B8>went offline.</#94A3B8>");
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
        plugin.lockerRoom3dManager().close(player, true);
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
                        "<gray>BosketBall uses a lightweight official pack for</gray> <white>3D Basketballs,</white>",
                        "<white>Hoops, Scoreboards, Collector Tags, Custom GUIs & Court Audio</white><gray>!</gray>",
                        "<click:run_command:'/pack'><hover:show_text:'<green>Click to load the BosketBall pack</green>'><#4ADE80><bold>[📦 Click Here to Load Official Pack]</bold></#4ADE80></hover></click>"
                    ));
                }
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                if (!retrying) {
                    Msg.sendAlert(player, "Resource Pack download interrupted. "
                        + "<click:run_command:'/pack'><hover:show_text:'<yellow>Retry downloading pack</yellow>'><#FBBF24><bold>[↻ Retry Download]</bold></#FBBF24></hover></click>");
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().handleInput(player, event.getInput());
        }
    }

    /**
     * Strict anti-duplication guard: blocks moving BosketBall court/lobby UI items into chests, hoppers,
     * crafting grids, offhand slots, or player storage slots.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiGuard(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiMenuManager.BosketHolder) {
            return; // Handled by GuiMenuManager
        }
        if (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Prevent stashing/duplicating custom bosketball:* lobby hotbar icons in chests or survival inventory
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
            || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())
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

        // Inside Goal Replay Camera: Left-Click skips cleanly, all court item triggers suppressed!
        if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId())) {
            event.setCancelled(true);
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                plugin.cinematicCamera().skipIfWatching(player);
            }
            return;
        }

        // Inside the frozen full-screen Locker Room menu: keyboard-only (Forward/Back, Left/Right,
        // Jump = select, Sneak = exit). Mouse clicks do nothing and are always swallowed.
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            // 1. Custom Map Architect Wand (/bb map wand — strictly OP only!)
            if (MapEditorManager.isArchitectWand(event.getItem())) {
                event.setCancelled(true);
                if (!player.isOp()) {
                    Msg.send(player, Msg.error("Arena creation tools are strictly reserved for Server Operators (OP)."));
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

            // 2. Lobby Hotbar Items (respond cleanly to BOTH Left-Click and Right-Click — zero commands required!)
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
                    if (PROP_PLAY_KEY.equals(modelKey) || ICON_ARENA_KEY.equals(modelKey) || ICON_SHIELD_KEY.equals(modelKey) || LEGACY_PLAYBOOK_KEY.equals(modelKey)) {
                        plugin.guiMenu().openPlayModes(player);
                    } else if (PROP_LOCKER_KEY.equals(modelKey) || ICON_PORTAL_KEY.equals(modelKey)) {
                        plugin.lockerRoom3dManager().open(player, LockerRoom3dMenuManager.TopTab.PLAY);
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

        // Post-Match Ceremony Hotbar (⚡ Play Again, 👏 Say GG, 🚪 Return to Lobby)
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

        // Spectator 1-Click Hotbar (🔭 Next Player, 📊 Box Score, 🚪 Stop Spectating)
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
                    if (session.canBeginBowDraw(player)) {
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
            player.sendActionBar(Msg.parse("<#FBBF24>Unlock more Dunk Styles in /shop!</#FBBF24>"));
            return;
        }
        int cur = owned.indexOf(prof.selectedDunkStyle());
        DunkStyle next = owned.get((cur + 1) % owned.size());
        prof.setSelectedDunkStyle(next);
        plugin.statsManager().saveProfileAsync(prof);
        player.getInventory().setItem(3, GuiMenuManager.customIcon(next.iconModelId(),
            "<#FBBF24><bold>Dunk Style: " + next.displayName() + "</bold></#FBBF24>",
            List.of("<gray>Jump inside paint + Click, then Click in Green Zone!</gray>")));
        player.sendActionBar(Msg.parse("<#FBBF24>Dunk Style: <bold>" + next.displayName() + "</bold></#FBBF24>"));
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
                event.setConsumeItem(false);
            }
            return;
        }
        event.setCancelled(true);
        event.setConsumeItem(false);
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
    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        // Vanilla BowItem skips EntityShootBowEvent when held < 3 ticks (force < 0.1f).
        // Handle ultra-quick 2-tick tap shots cleanly while ignoring slot-switches!
        int ticksHeld = event.getTicksHeldFor();
        if (ticksHeld >= 2 && ticksHeld < 3 && player.getInventory().getHeldItemSlot() == 0) {
            session.handleBowShotRelease(player, fr.bosketball.physics.PhysicsMath.computeBowPullProgress(ticksHeld));
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.lockerRoom3dManager().onClickConfirm(player, true);
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null) {
            // Interactive 3D Lobby Hologram Station Right-Click!
            if (plugin.lobbyHolograms() != null
                && plugin.lobbyHolograms().handlePossibleStationClick(player, event.getRightClicked())) {
                event.setCancelled(true);
                return;
            }
            // Physical Reward Crate (120 62 61, facing West)
            if (plugin.crateStation() != null && plugin.crateStation().isCrateEntity(event.getRightClicked())) {
                event.setCancelled(true);
                plugin.crateStation().handleClick(player);
                return;
            }
            // Tutorial hologram (101 61 61, raised) — right-click starts the guided tour
            if (plugin.tutorialManager() != null
                && plugin.tutorialManager().isTutorialEntity(event.getRightClicked())) {
                event.setCancelled(true);
                plugin.tutorialManager().start(player);
                return;
            }
            // Cytooxien-style Shift + Right-Click any Player in Lobby to Duel / Invite to Party / Add Friend!
            if (event.getRightClicked() instanceof Player targetPlayer && player.isSneaking()
                && !targetPlayer.getUniqueId().equals(player.getUniqueId())
                && !plugin.gameManager().isInMatch(targetPlayer.getUniqueId())
                && !plugin.lockerRoom3dManager().isIn3dMenu(targetPlayer.getUniqueId())
                && player.canSee(targetPlayer)) {
                event.setCancelled(true);
                plugin.guiMenu().openPlayerInteractMenu(player, targetPlayer);
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
            if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                event.setCancelled(true);
                plugin.lockerRoom3dManager().onClickConfirm(player, false);
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
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())
            || (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId()))) {
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
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())
            || (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(player.getUniqueId()))) {
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
            && (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
                || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId()))) {
            event.setCancelled(true);
        } else if (event.getEntity().getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)
            || event.getEntity().getScoreboardTags().contains(fr.bosketball.ui.LobbyHologramManager.HOLO_ENTITY_TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
            && (plugin.gameManager().getSessionOf(player.getUniqueId()) != null
                || plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId()))) {
            event.setCancelled(true);
            event.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.lockerRoom3dManager().handleSpectateStopAttempt(player);
            return;
        }
        if (plugin.cinematicCamera().isWatching(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.cinematicCamera().handleSpectateStopAttempt(player);
        }
    }

    @EventHandler
    public void onPlayerSwingIn3dStage(PlayerAnimationEvent event) {
        if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            Player player = event.getPlayer();
            if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onScrollHotbarIn3dStage(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            int prev = event.getPreviousSlot();
            int next = event.getNewSlot();
            int diff = next - prev;
            if (diff > 4) diff -= 9;
            if (diff < -4) diff += 9;
            plugin.lockerRoom3dManager().scrollSelection(player, diff > 0 ? 1 : -1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())
            || isBosketItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        event.getDrops().removeIf(GameplayListener::isBosketItem);
        if (plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
            plugin.lockerRoom3dManager().close(p, true);
        }
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
        if (plugin.gameManager().getSessionOf(p.getUniqueId()) != null
            || plugin.lockerRoom3dManager().isIn3dMenu(p.getUniqueId())) {
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
