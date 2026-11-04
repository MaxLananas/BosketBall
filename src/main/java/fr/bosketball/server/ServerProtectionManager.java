package fr.bosketball.server;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.BallSkin;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.io.File;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;

public final class ServerProtectionManager implements Listener {

    private static final Set<Material> ILLEGAL_ITEMS_AND_BLOCKS = buildIllegalMaterialSet();

    private final BosketBallPlugin plugin;
    private final File spawnFile;
    private final Set<UUID> activeBuilders = ConcurrentHashMap.newKeySet();
    private volatile Location lobbySpawn;

    private volatile Location enforcedLobbySpawnCache;

    public ServerProtectionManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.spawnFile = plugin != null ? new File(plugin.getDataFolder(), "spawn.yml") : null;
        loadSpawn();
    }

    private static Set<Material> buildIllegalMaterialSet() {
        Set<Material> s = EnumSet.noneOf(Material.class);
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.contains("COMMAND_BLOCK")
                || n.endsWith("_SPAWN_EGG")
                || n.equals("TNT")
                || n.equals("TNT_MINECART")
                || n.equals("END_CRYSTAL")
                || n.equals("RESPAWN_ANCHOR")
                || n.equals("LAVA")
                || n.equals("LAVA_BUCKET")
                || n.equals("FLINT_AND_STEEL")
                || n.equals("FIRE_CHARGE")
                || n.equals("FIRE")
                || n.equals("SOUL_FIRE")
                || n.equals("SPAWNER")
                || n.equals("TRIAL_SPAWNER")
                || n.equals("JIGSAW")
                || n.equals("STRUCTURE_BLOCK")
                || n.equals("WRITTEN_BOOK")
                || n.equals("WRITABLE_BOOK")) {
                s.add(m);
            }
        }
        return s;
    }

    public static boolean isIllegalMaterial(Material mat) {
        return mat != null && ILLEGAL_ITEMS_AND_BLOCKS.contains(mat);
    }

    public static boolean isGravityOrFragileBlock(Material mat) {
        if (mat == null) {
            return false;
        }
        try {
            if (mat.hasGravity()) {
                return true;
            }
        } catch (Throwable ignored) {
            String gn = mat.name();
            if (gn.endsWith("SAND") || gn.endsWith("GRAVEL") || gn.endsWith("_CONCRETE_POWDER")
                || gn.endsWith("ANVIL") || gn.equals("DRAGON_EGG")) {
                return true;
            }
        }
        String n = mat.name();
        return n.endsWith("_CARPET")
            || n.contains("CORAL")
            || n.endsWith("_BANNER")
            || n.endsWith("_SIGN")
            || n.contains("TORCH")
            || n.contains("LANTERN")
            || n.contains("CANDLE")
            || n.equals("LADDER")
            || n.equals("VINE")
            || n.equals("SNOW")
            || n.equals("SCAFFOLDING")
            || n.equals("POINTED_DRIPSTONE")
            || n.endsWith("_AMETHYST_BUD")
            || n.equals("AMETHYST_CLUSTER");
    }

    public boolean isInBuildMode(UUID playerId) {
        return playerId != null && activeBuilders.contains(playerId);
    }

    public void clearBuildMode(UUID playerId) {
        if (playerId != null) {
            activeBuilders.remove(playerId);
        }
    }

    public boolean hasSpawnLocation() {
        return lobbySpawn != null && lobbySpawn.getWorld() != null;
    }

    public Location spawnLocation() {
        return lobbySpawn != null ? lobbySpawn.clone() : null;
    }

    public boolean canBypassProtection(Player player) {
        if (player == null) {
            return false;
        }

        if (plugin.gameManager() != null && plugin.gameManager().isInMatch(player.getUniqueId())) {
            return false;
        }
        if (activeBuilders.contains(player.getUniqueId()) && Perms.build(player)) {
            return true;
        }

        if (plugin.settings().protection().allowFaweAndAxiomBypass()
            && player.getGameMode() == GameMode.CREATIVE
            && (player.hasPermission("axiom.use") || player.hasPermission("axiom.*") || player.hasPermission("fawe.admin"))
            && !isHoldingBosketItem(player)) {
            return true;
        }
        return false;
    }

    public boolean toggleBuildMode(Player target) {
        if (target == null) {
            return false;
        }
        if (activeBuilders.remove(target.getUniqueId())) {
            if (target.getGameMode() == GameMode.CREATIVE && !plugin.gameManager().isInMatch(target.getUniqueId())) {
                target.setGameMode(GameMode.ADVENTURE);
                if (plugin.settings().general().autoEquipLobbyPlaybook()) {
                    BallSkin.equipLobbyHotbarIfEmpty(target, true);
                }
            }
            return false;
        } else {
            activeBuilders.add(target.getUniqueId());
            if (!plugin.gameManager().isInMatch(target.getUniqueId())) {
                target.setGameMode(GameMode.CREATIVE);
            }
            return true;
        }
    }

    public void setLobbySpawn(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        this.lobbySpawn = loc.clone();
        try {
            loc.getWorld().setSpawnLocation(loc);
        } catch (Throwable ignored) {
        }
        if (spawnFile != null && plugin != null) {
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("spawn.world", loc.getWorld().getName());
            yml.set("spawn.x", AtomicYamlWriter.round2(loc.getX()));
            yml.set("spawn.y", AtomicYamlWriter.round2(loc.getY()));
            yml.set("spawn.z", AtomicYamlWriter.round2(loc.getZ()));
            yml.set("spawn.yaw", AtomicYamlWriter.round2(loc.getYaw()));
            yml.set("spawn.pitch", AtomicYamlWriter.round2(loc.getPitch()));
            String hdr = "# ===========================================================================\n"
                + "#   BOSKETBALL — LOBBY SPAWN LOCATION (spawn.yml)\n"
                + "#   Update in-game with: /setspawn\n"
                + "# ===========================================================================\n";
            AtomicYamlWriter.writeYamlAtomically(spawnFile, hdr, yml, plugin.getLogger());
        }
    }

    public void loadSpawn() {
        if (spawnFile == null || !spawnFile.exists() || Bukkit.getServer() == null) {
            return;
        }
        YamlConfiguration yml = AtomicYamlWriter.loadSafe(spawnFile, plugin != null ? plugin.getLogger() : null);
        String worldName = yml.getString("spawn.world");
        if (worldName == null) {
            return;
        }
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            return;
        }
        this.lobbySpawn = new Location(
            w,
            yml.getDouble("spawn.x"),
            yml.getDouble("spawn.y"),
            yml.getDouble("spawn.z"),
            (float) yml.getDouble("spawn.yaw"),
            (float) yml.getDouble("spawn.pitch")
        );
    }

    public boolean hasCustomLobbySpawn() {
        return lobbySpawn != null && lobbySpawn.getWorld() != null;
    }

    public Location enforcedLobbySpawn() {
        if (plugin == null || plugin.settings() == null || Bukkit.getServer() == null) {
            return null;
        }
        BosketConfig.Lobby lobby = plugin.settings().lobby();
        if (lobby == null || !lobby.enforceSpawn() || lobby.spawn() == null) {
            return null;
        }
        BosketConfig.LobbyPoint p = lobby.spawn();
        World world = Bukkit.getWorld(p.world());
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }
        return new Location(world, p.x(), p.y(), p.z(), p.yaw(), p.pitch());
    }

    public boolean hasForcedLobbySpawn() {
        return enforcedLobbySpawn() != null;
    }

    public void applyEnforcedSpawnToWorld() {
        Location enforced = enforcedLobbySpawn();
        this.enforcedLobbySpawnCache = enforced != null ? enforced.clone() : null;
        if (enforced != null && enforced.getWorld() != null) {
            try {
                enforced.getWorld().setSpawnLocation(enforced);
            } catch (Throwable ignored) {
            }
        }
    }

    private Location cachedEnforcedLobbySpawn() {
        Location cached = this.enforcedLobbySpawnCache;
        return cached != null ? cached.clone() : null;
    }

    @EventHandler
    public void onAsyncPlayerSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        Location enforced = cachedEnforcedLobbySpawn();
        if (enforced != null && enforced.getWorld() != null) {
            event.setSpawnLocation(enforced);
        }
    }

    public Location resolveLobbySpawn(Player fallbackPlayer) {
        Location enforced = enforcedLobbySpawn();
        if (enforced != null) {
            return enforced;
        }
        if (lobbySpawn != null && lobbySpawn.getWorld() != null) {
            return lobbySpawn.clone();
        }
        if (plugin != null && plugin.arenaManager() != null) {
            Arena primary = plugin.arenaManager().primaryArena();
            if (primary != null) {
                Location exit = primary.lobbyExitLocationOrNull();
                if (exit != null) {
                    return exit;
                }
                if (primary.world() != null) {
                    return primary.world().getSpawnLocation().add(0.5, 0.1, 0.5);
                }
            }
        }
        if (Bukkit.getServer() != null && !Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 0.1, 0.5);
        }
        return fallbackPlayer != null ? fallbackPlayer.getLocation() : null;
    }

    public void teleportToSpawn(Player player) {
        if (player == null) {
            return;
        }
        if (plugin.gameManager().isInMatch(player.getUniqueId()) || plugin.gameManager().isQueued(player.getUniqueId())) {
            plugin.gameManager().leaveAll(player, false);
        }
        if (plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            plugin.lockerRoom3dManager().close(player, false);
        }
        Location target = resolveLobbySpawn(player);
        if (target != null && target.getWorld() != null) {
            player.setFallDistance(0f);
            player.teleport(target);
            if (!isInBuildMode(player.getUniqueId()) && plugin.settings().general().autoEquipLobbyPlaybook()) {
                BallSkin.equipLobbyHotbarIfEmpty(player, false);
            }
            Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.15f);
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    public void enforceAllWorldRules() {
        if (Bukkit.getServer() == null || !plugin.settings().protection().enabled()) {
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            applySafeWorldRules(w);
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    public void applySafeWorldRules(World world) {
        if (world == null || !plugin.settings().protection().enabled()) {
            return;
        }
        try {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
            world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.DISABLE_RAIDS, true);
            world.setGameRule(GameRule.DO_INSOMNIA, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.FALL_DAMAGE, false);
            world.setGameRule(GameRule.FIRE_DAMAGE, false);
            world.setGameRule(GameRule.DROWNING_DAMAGE, false);
            world.setGameRule(GameRule.FREEZE_DAMAGE, false);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
            world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false);
        } catch (Throwable ignored) {
        }
    }

    public static final Set<String> NON_OP_TAB_COMMANDS = Set.of(
        "help", "?", "play", "practice", "locker", "shop", "ranks",
        "friend", "party", "duel", "msg", "r",
        "spawn", "leave", "gg", "discord", "pack",
        "accept", "decline", "pc", "tuto", "quests", "achievements",
        "report", "crate", "guild", "ignore", "afk", "mail"
    );

    public static final Set<String> NON_OP_ALLOWED_COMMANDS = Set.of(
        "help", "?",
        "play", "practice", "locker", "shop", "ranks",
        "friend", "friends", "f",
        "party", "p",
        "duel",
        "msg", "tell", "w",
        "r", "reply",
        "spawn", "hub", "lobby",
        "leave", "gg", "discord", "pack",
        "accept", "decline", "pc", "pchat",
        "tuto", "tutorial",
        "quests", "quest", "objectives",
        "achievements", "ach",
        "report", "crate",
        "guild", "team",
        "ignore", "afk",
        "mail", "inbox", "mailbox"
    );

    private static String extractRootCommand(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return "";
        }
        String cmd = rawLabel.trim().toLowerCase(java.util.Locale.ROOT);
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        int space = cmd.indexOf(' ');
        if (space >= 0) {
            cmd = cmd.substring(0, space);
        }
        return cmd;
    }

    public static boolean isAllowedForNonOp(String rawCommandLine) {
        String cmd = extractRootCommand(rawCommandLine);
        if (cmd.isEmpty() || cmd.startsWith("/") || cmd.contains(":")) {
            return false;
        }
        return NON_OP_ALLOWED_COMMANDS.contains(cmd);
    }

    public static boolean isAllowedByLockdown(boolean trusted, boolean ownPluginCommand, String rawCommandLine) {
        return trusted || ownPluginCommand || isAllowedForNonOp(rawCommandLine);
    }

    private boolean hasUnrestrictedCommands(Player player) {
        return Perms.staff(player) || Perms.map(player) || Perms.build(player);
    }

    private boolean isOwnPluginCommand(String rawCommandLine) {
        String cmd = extractRootCommand(rawCommandLine);
        if (cmd.isEmpty() || cmd.contains(":")) {
            return false;
        }
        PluginCommand command = Bukkit.getPluginCommand(cmd);
        return command != null && command.getPlugin() == plugin;
    }

    private boolean isCommandAllowed(Player player, String rawCommandLine) {
        return isAllowedByLockdown(hasUnrestrictedCommands(player), isOwnPluginCommand(rawCommandLine), rawCommandLine);
    }

    public static boolean isBlockedSystemCommand(String rawLabel) {
        String cmd = extractRootCommand(rawLabel);
        if (cmd.isEmpty()) {
            return false;
        }
        if (isHelpAlias(rawLabel) && !cmd.contains(":")) {
            return false;
        }
        return !isAllowedForNonOp(rawLabel);
    }

    public static boolean isHelpAlias(String rawLabel) {
        String cmd = extractRootCommand(rawLabel);
        return cmd.equals("help")
            || cmd.equals("?")
            || cmd.equals("bukkit:help")
            || cmd.equals("bukkit:?")
            || cmd.equals("minecraft:help");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (hasUnrestrictedCommands(player)) {
            return;
        }
        event.getCommands().removeIf(cmd -> !NON_OP_TAB_COMMANDS.contains(cmd.toLowerCase(java.util.Locale.ROOT))
            && !isOwnPluginCommand(cmd));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage();
        if (isHelpAlias(raw)) {
            event.setCancelled(true);
            Msg.sendInteractiveHelpCard(player);
            return;
        }
        if (isCommandAllowed(player, raw)) {
            return;
        }
        if (!isAllowedForNonOp(raw)) {
            event.setCancelled(true);
            Msg.sendAlert(player, "This command is disabled. Click "
                + "<click:run_command:'/help'><hover:show_text:'<aqua>Open BosketBall Help & Commands</aqua>'>"
                + "<#38BDF8><bold>[📖 /help]</bold></#38BDF8></hover></click> to view available commands.");
            Msg.playPersonalPackSound(player, "ui.click", 0.6f, 0.85f);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applySafeWorldRules(event.getWorld());
        loadSpawn();
        applyEnforcedSpawnToWorld();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeBuilders.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingBlockChange(EntityChangeBlockEvent event) {
        BosketConfig.Protection cfg = plugin.settings().protection();
        if (!cfg.enabled()) {
            return;
        }
        if (event.getEntityType() == EntityType.FALLING_BLOCK && cfg.disableBlockGravity()) {
            event.setCancelled(true);
            event.getBlock().getState().update(false, false);
            return;
        }

        if (!(event.getEntity() instanceof Player p && canBypassProtection(p))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        BosketConfig.Protection cfg = plugin.settings().protection();
        if (!cfg.enabled() || !cfg.disableBlockGravity()) {
            return;
        }
        if (isGravityOrFragileBlock(event.getBlock().getType()) || isGravityOrFragileBlock(event.getChangedType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        BosketConfig.Protection cfg = plugin.settings().protection();
        if (!cfg.enabled() || !cfg.disableFireAndSpread()) {
            return;
        }
        Player p = event.getPlayer();
        if (p != null && canBypassProtection(p) && p.hasPermission("bosketball.admin") && !cfg.blockIllegalItems()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableFireAndSpread()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableFireAndSpread()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableExplosions()) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableExplosions()) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (plugin.settings().protection().enabled()
            && plugin.settings().protection().disableWeatherAndDecay()
            && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (plugin.settings().protection().enabled()
            && plugin.settings().protection().disableWeatherAndDecay()
            && event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableWeatherAndDecay()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableWeatherAndDecay()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableWeatherAndDecay()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableWeatherAndDecay()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (plugin.settings().protection().enabled() && plugin.settings().protection().disableWeatherAndDecay()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent event) {
        if (plugin.settings().protection().enabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.settings().protection().enabled() || !plugin.settings().protection().disableWeatherAndDecay()) {
            return;
        }
        CreatureSpawnEvent.SpawnReason r = event.getSpawnReason();
        if (r != CreatureSpawnEvent.SpawnReason.CUSTOM && r != CreatureSpawnEvent.SpawnReason.COMMAND) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        BosketConfig.Protection cfg = plugin.settings().protection();
        if (!cfg.enabled()) {
            return;
        }
        Player player = event.getPlayer();
        Material placed = event.getBlockPlaced().getType();

        if (isHoldingBosketItem(player)) {
            event.setCancelled(true);
            return;
        }
        if (cfg.blockIllegalItems() && isIllegalMaterial(placed)) {
            event.setCancelled(true);
            Msg.send(player, Msg.error("That block (<white>" + placed.name() + "</white>) is blocked by server protection."));
            return;
        }
        if (!canBypassProtection(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        if (!plugin.settings().protection().enabled()) {
            return;
        }
        if (!canBypassProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.settings().protection().enabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (isHoldingBosketItem(player) || !canBypassProtection(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        BosketConfig.Protection cfg = plugin.settings().protection();
        if (!cfg.enabled()) {
            return;
        }
        if (cfg.blockIllegalItems() && isIllegalMaterial(event.getBucket())) {
            event.setCancelled(true);
            Msg.send(event.getPlayer(), Msg.error("Lava buckets are disabled by server protection."));
            return;
        }
        if (!canBypassProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (plugin.settings().protection().enabled() && !canBypassProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        BosketConfig.Protection cfg = plugin.settings().protection();
        if (cfg.enabled() && cfg.blockIllegalItems() && isIllegalMaterial(event.getItem().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreativeIllegalItem(InventoryCreativeEvent event) {
        BosketConfig.Protection cfg = plugin.settings().protection();
        if (!cfg.enabled() || !cfg.blockIllegalItems()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && isIllegalMaterial(cursor.getType()) && !player.hasPermission("bosketball.admin")) {
            event.setCancelled(true);
            Msg.send(player, Msg.error("Illegal item blocked: <white>" + cursor.getType().name() + "</white>."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProtectedInteract(PlayerInteractEvent event) {
        BosketConfig.Protection cfg = plugin.settings().protection();
        if (!cfg.enabled()) {
            return;
        }
        Player player = event.getPlayer();

        ItemStack hand = event.getItem();
        if (cfg.blockIllegalItems() && hand != null && isIllegalMaterial(hand.getType())) {
            event.setCancelled(true);
            Msg.send(player, Msg.error("That item (<white>" + hand.getType().name() + "</white>) is disabled on this server."));
            return;
        }

        if (event.getAction() == Action.PHYSICAL) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && (clicked.getType() == Material.FARMLAND || clicked.getType() == Material.TURTLE_EGG)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && !canBypassProtection(player)) {
            Material bType = event.getClickedBlock().getType();
            if (isProtectedInteractiveBlock(bType)) {
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            }
        }
    }

    private static boolean isProtectedInteractiveBlock(Material m) {
        if (m == null) {
            return false;
        }
        if (Tag.TRAPDOORS.isTagged(m)
            || Tag.FENCE_GATES.isTagged(m)
            || Tag.ANVIL.isTagged(m)
            || Tag.BEDS.isTagged(m)
            || Tag.SHULKER_BOXES.isTagged(m)
            || Tag.FLOWER_POTS.isTagged(m)
            || Tag.CANDLES.isTagged(m)
            || Tag.ALL_SIGNS.isTagged(m)) {
            return true;
        }
        return switch (m) {
            case CHEST, TRAPPED_CHEST, BARREL, HOPPER, DISPENSER, DROPPER,
                 FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND, BEACON,
                 LECTERN, CHISELED_BOOKSHELF, JUKEBOX, NOTE_BLOCK,
                 REPEATER, COMPARATOR, DAYLIGHT_DETECTOR, LEVER,
                 DRAGON_EGG, CAKE, RESPAWN_ANCHOR, SWEET_BERRY_BUSH,
                 CAVE_VINES, CAVE_VINES_PLANT, DECORATED_POT, CRAFTER -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!plugin.settings().protection().enabled()) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent byEnt && byEnt.getRemover() instanceof Player p && canBypassProtection(p)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (plugin.settings().protection().enabled() && !canBypassProtection(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityFrameInteract(PlayerInteractEntityEvent event) {
        if (!plugin.settings().protection().enabled()) {
            return;
        }
        Player p = event.getPlayer();
        Entity target = event.getRightClicked();
        if (target instanceof Mannequin && plugin.gameManager() != null) {
            MatchSession s = plugin.gameManager().getSessionOf(p.getUniqueId());
            if (s != null && s.canBeginBowDraw(p)) {
                return;
            }
        }
        if ((target instanceof ItemFrame || target instanceof Hanging || target instanceof ArmorStand || target instanceof Mannequin)
            && !canBypassProtection(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!plugin.settings().protection().enabled()) {
            return;
        }
        if (!(event.getAttacker() instanceof Player p && canBypassProtection(p))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.settings().protection().enabled()) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim.getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)
            || victim.getScoreboardTags().contains(fr.bosketball.ui.LobbyHologramManager.HOLO_ENTITY_TAG)) {
            event.setCancelled(true);
            return;
        }
        if (victim instanceof ItemFrame || victim instanceof Hanging || victim instanceof ArmorStand || victim instanceof Mannequin) {
            if (!(event.getDamager() instanceof Player p && canBypassProtection(p))) {
                event.setCancelled(true);
            }
            return;
        }
        if (victim instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnyPlayerDamage(EntityDamageEvent event) {
        if (!plugin.settings().protection().enabled()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
                player.setFireTicks(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (plugin.settings().protection().enabled() && event.getEntity() instanceof Player) {
            event.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (plugin.settings().protection().enabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (plugin.settings().protection().enabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        if (plugin.settings().protection().enabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (plugin.lockerRoom3dManager() != null && plugin.lockerRoom3dManager().isIn3dMenu(player.getUniqueId())) {
            Location lockPose = plugin.lockerRoom3dManager().lockedCameraPose(player.getUniqueId());
            Location to = event.getTo();
            if (lockPose != null && to != null && lockPose.getWorld() != null && lockPose.getWorld().equals(to.getWorld())) {
                if (to.distanceSquared(lockPose) > 0.0025
                    || Math.abs(to.getYaw() - lockPose.getYaw()) > 0.25f
                    || Math.abs(to.getPitch() - lockPose.getPitch()) > 0.25f) {
                    event.setTo(lockPose.clone());
                }
            }
            return;
        }

        Location to = event.getTo();
        if (to != null && plugin.settings().protection().enabled() && to.getY() < plugin.settings().protection().voidRescueY()) {
            MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
            if (session != null) {
                player.setFallDistance(0f);
                player.teleport(session.arena().centerCourtLocation().add(0, 0.5, 0));
            } else if (!isInBuildMode(player.getUniqueId())) {
                teleportToSpawn(player);
            }
        }
    }

    private static boolean isHoldingBosketItem(Player player) {
        if (player == null) {
            return false;
        }
        return isSingleBosketStack(player.getInventory().getItemInMainHand())
            || isSingleBosketStack(player.getInventory().getItemInOffHand());
    }

    private static boolean isSingleBosketStack(ItemStack item) {
        return item != null && !item.getType().isAir()
            && item.getItemMeta() != null && item.getItemMeta().getItemModel() != null
            && BallSkin.NAMESPACE.equals(item.getItemMeta().getItemModel().getNamespace());
    }
}
