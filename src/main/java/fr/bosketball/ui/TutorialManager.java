package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.game.GameManager;
import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.ProgressStat;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Guided new-player tutorial.
 *
 * <p>Every objective is materialised in the world: a real animated gold ring built out of
 * {@link BlockDisplay} gold blocks, a floating instruction board and a particle beacon. Steps are
 * validated by what the player actually does (walking into the ring, shooting, dunking, sneaking,
 * opening the shop, queueing) and the tutorial advances itself — no command is required to finish
 * it. A boss bar and the action bar always show the current step, the objective and the progress.
 */
public final class TutorialManager implements Listener {

    public static final String HOLO_TAG = "bosketball_tutorial_holo";

    public static final String INTRO_TAG = "bosketball_tutorial_intro";

    public static final String MARKER_TAG = "bosketball_tutorial_marker";

    /** Radius (squared) inside which walking counts as "entered the ring". */
    private static final double RING_ENTER_RADIUS_SQ = 2.6 * 2.6;

    /** Steps without an in-world action auto-skip after this delay, so nobody is ever stuck. */
    private static final long SOFT_SKIP_MS = 45_000L;

    public record Step(
        int index,
        String title,
        String subtitle,
        List<String> instructions,
        int revealCoins,
        String requirement,
        String iconModel
    ) {
        /** One-line objective shown in the boss bar and the action bar. */
        public String objective() {
            return switch (requirement) {
                case "walk" -> "Walk into the golden ring";
                case "shoot" -> "Shoot at the hoop — release in the green window";
                case "dunk" -> "Dunk — stand in the paint, face the rim and click once";
                case "defense" -> "Defend — sneak to crossover, left-click to steal";
                case "shop" -> "Open the shop (/shop)";
                case "queue" -> "Queue up with /play";
                default -> "Read the card below, then click CONTINUE";
            };
        }

        /** Every chapter can move on by itself, so a player is never stuck on a card. */
        public boolean autoValidated() {
            return true;
        }

        /** How long the step waits before moving on alone. */
        public long autoAdvanceMs() {
            return "welcome".equals(requirement) ? 20_000L : SOFT_SKIP_MS;
        }
    }

    private static final List<Step> STEPS = List.of(
        new Step(0, "ᴡᴇʟᴄᴏᴍᴇ ᴛᴏ ʙᴏꜱᴋᴇᴛʙᴀʟʟ", "Let's get you on the court",
            List.of(
                "<gray>BosketBall is a full <white>3D competitive basketball</white> server.",
                "<gray>You are standing on a <white>live practice court</white> right now.",
                "<gray>Everything is driven by your hotbar: <white>Play</white>, <white>Shop</white>, <white>Locker</white> & <white>Profile</white>.",
                "<gray>The tutorial <white>advances by itself</white> — just follow the golden marker."
            ), 10, "welcome", "ref_medal_gold"),
        new Step(1, "ᴍᴏᴠᴇ & ʟᴏᴏᴋ", "Step into the gold ring",
            List.of(
                "<gray>A real <#FFD43B><bold>golden ring</bold></#FFD43B> is glowing right in front of you.",
                "<gray><white>Walk into it</white> — the ring turns green and the step validates instantly.",
                "<gray>Sprint, strafe and jump: every motion input is registered by the engine."
            ), 15, "walk", "ref_calendar_check"),
        new Step(2, "ꜱʜᴏᴏᴛɪɴɢ", "The green release window",
            List.of(
                "<gray>Take the ball (<white>slot 1</white>) and <white>hold right-click</white> to draw a shot.",
                "<gray>Release when the meter enters the <#4ADE80><bold>green zone</bold></#4ADE80> for a pure swish.",
                "<gray>The marker above the rim shows exactly where to aim.",
                "<gray>Any shot attempt validates this step."
            ), 25, "shoot", "ref_calendar_check"),
        new Step(3, "ᴅᴜɴᴋɪɴɢ", "One-click slam",
            List.of(
                "<gray>Get inside the paint, face the rim and <white>click once</white> to take off.",
                "<gray>The slam resolves by itself — no second click, then a <white>20 s</white> cooldown.",
                "<gray>Styles: Tomahawk, Windmill, 360 Spin, Between-the-Legs and Meteor Jam."
            ), 25, "dunk", "icon_dunk"),
        new Step(4, "ᴅᴇꜰᴇɴꜱᴇ", "Clamp your matchup",
            List.of(
                "<gray><white>Sneak</white>, <white>F</white> or <white>slot 2</white> performs a V-Crossover.",
                "<gray><white>Left-Click</white> on defense pokes the ball loose or blocks the shot.",
                "<gray>Breaking ankles earns bonus Coins and a highlight banner."
            ), 20, "defense", "icon_defense"),
        new Step(5, "ᴇᴄᴏɴᴏᴍʏ", "Coins & cosmetics",
            List.of(
                "<gray>You earn Coins for buckets, assists, steals, blocks, wins and MVPs.",
                "<gray>Spend them in <white>/shop</white> on basketballs, dunk styles and chat tags.",
                "<gray>Every purchase asks for a <white>confirmation</white> before anything is spent.",
                "<gray>Open the shop once to validate this step."
            ), 25, "shop", "ref_shop"),
        new Step(6, "Qᴜᴇᴜᴇ ᴜᴘ", "Time to play",
            List.of(
                "<gray><white>/play 1v1</white> ranked, or <white>/play</white> for the full menu.",
                "<gray><white>Casual</white> modes never cost you ELO — perfect to warm up.",
                "<gray>Joining any queue validates this step."
            ), 40, "queue", "icon_trophy_gold"),
        new Step(7, "ᴄᴏᴍᴘʟᴇᴛᴇ", "Welcome to the league",
            List.of(
                "<gray>You know the court, the shot, the dunk and the shop.",
                "<gray>Finish to claim your <white>welcome bonus</white> and enter the lobby."
            ), 50, "finish", "ref_cup_gold")
    );

    private static final int INTRO_TOTAL_TICKS = 130;

    private final BosketBallPlugin plugin;
    private final Map<UUID, Integer> activeStep = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingWalkTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Location> returnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStepAdvance = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stepValidatedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBlockedHint = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> introTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Arena> tutorialArenas = new ConcurrentHashMap<>();
    private final Map<UUID, List<org.bukkit.entity.Entity>> playerMarkers = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final List<org.bukkit.entity.Entity> introProps = new ArrayList<>();
    private TextDisplay hologram;
    private Interaction guideBox;
    private int tickCounter = 0;

    /** Lazily resolved: touching Bukkit block data during class initialisation breaks unit tests. */
    private static BlockData goldSlice() {
        return Material.GOLD_BLOCK.createBlockData();
    }

    private static BlockData emeraldSlice() {
        return Material.EMERALD_BLOCK.createBlockData();
    }

    public TutorialManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public static List<Step> steps() {
        return STEPS;
    }

    public static int stepCount() {
        return STEPS.size();
    }

    // ------------------------------------------------------------------ lobby hologram

    public Location hologramLocation() {
        var spec = plugin.settings().lobby().stationsExtra().tutorial();
        var yml = fr.bosketball.config.AtomicYamlWriter.loadSafe(
            new java.io.File(plugin.getDataFolder(), "stations.yml"), plugin.getLogger());
        World world = Bukkit.getWorld(yml.getString("tutorial.world", spec.world()));
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return null;
        }
        double x = yml.getDouble("tutorial.x", spec.x());
        double y = yml.getDouble("tutorial.y", spec.y());
        double z = yml.getDouble("tutorial.z", spec.z());
        return new Location(world, x, y + spec.lift(), z, fr.bosketball.lobby.RewardCrateStation.yawFor(
            yml.getString("tutorial.facing", spec.facing())), 0f);
    }

    public boolean hologramEnabled() {
        return plugin.settings().lobby().stationsExtra().tutorial().usable();
    }

    public void spawnHologram() {
        despawnHologram();
        if (!plugin.settings().tutorial().enabled() || !plugin.settings().tutorial().hologramEnabled()
            || !hologramEnabled()) {
            return;
        }
        Location loc = hologramLocation();
        if (loc == null) {
            return;
        }

        hologram = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HOLO_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.5f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setSeeThrough(false);
            d.setLineWidth(220);
            d.text(Msg.parse(
                Msg.gradBold(Msg.GRAD_GOLD, "✦ ɴᴇᴡ ᴘʟᴀʏᴇʀ ɢᴜɪᴅᴇ ✦") + "\n"
                    + "<white>Right-click the guide, or type <bold>/tuto</bold></white>\n"
                    + "<gray>" + STEPS.size() + " guided steps • free Coins</gray>"
            ));
        });

        guideBox = loc.getWorld().spawn(loc.clone().add(0, -0.4, 0), Interaction.class, box -> {
            box.setPersistent(false);
            box.addScoreboardTag(HOLO_TAG);
            box.setInteractionWidth(1.2f);
            box.setInteractionHeight(1.4f);
            box.setResponsive(true);
        });

        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, -0.6, 0), 12, 0.25, 0.4, 0.25, 0.01);
    }

    public void despawnHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
        if (guideBox != null) {
            guideBox.remove();
            guideBox = null;
        }
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(HOLO_TAG)) {
                    e.remove();
                }
            }
        }
    }

    public boolean isTutorialEntity(org.bukkit.entity.Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(HOLO_TAG);
    }

    // ------------------------------------------------------------------ queries

    public boolean isRunning(Player player) {
        return player != null && activeStep.containsKey(player.getUniqueId());
    }

    public boolean isInIntro(Player player) {
        return player != null && introTicks.containsKey(player.getUniqueId());
    }

    public int currentStep(Player player) {
        return activeStep.getOrDefault(player.getUniqueId(), 0);
    }

    public boolean currentStepValidated(Player player) {
        return player != null && stepValidatedAt.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ lifecycle

    /** Starts (or resumes) the guided tour. The cinematic intro only plays on a fresh start. */
    public void start(Player player) {
        if (player == null) {
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int resume = profile.tutorialCompleted() ? 0 : Math.clamp(profile.tutorialStep(), 0, STEPS.size() - 1);
        startAt(player, resume, resume == 0);
    }

    /** Full replay from chapter 1, intro included. */
    public void restart(Player player) {
        startAt(player, 0, true);
    }

    private void startAt(Player player, int step, boolean withIntro) {
        if (player == null) {
            return;
        }
        if (!plugin.settings().tutorial().enabled()) {
            Msg.send(player, Msg.warn("The tutorial is disabled on this server."));
            return;
        }
        if (plugin.gameManager() != null && plugin.gameManager().isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            Msg.send(player, Msg.warn("Finish your match first, then run <yellow>/tuto</yellow>."));
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int safe = Math.clamp(step, 0, STEPS.size() - 1);
        profile.setTutorialStep(safe);
        plugin.statsManager().markDirty(player.getUniqueId());
        activeStep.put(player.getUniqueId(), safe);
        stepValidatedAt.remove(player.getUniqueId());
        lastStepAdvance.put(player.getUniqueId(), System.currentTimeMillis());
        returnLocations.putIfAbsent(player.getUniqueId(), player.getLocation().clone());
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(player.getUniqueId(), fr.bosketball.model.PlayerStatus.TUTORIAL);
        }
        showBossBar(player);
        if (withIntro) {
            playIntro(player);
            return;
        }
        if (safe > 0) {
            Msg.send(player, Msg.info("Resuming your tour at <white>step " + (safe + 1) + "/" + STEPS.size()
                + "</white> — <gray>/tuto restart</gray> to play it again from the start."));
        }
        showStep(player, safe);
    }

    /**
     * Disconnect path: removes every per-player entry, the markers and the boss bar, without touching
     * the player (no walk speed, no teleport: they are already gone).
     */
    public void clearPlayer(Player player) {
        if (player == null) {
            return;
        }
        java.util.UUID id = player.getUniqueId();
        activeStep.remove(id);
        pendingWalkTargets.remove(id);
        introTicks.remove(id);
        returnLocations.remove(id);
        lastStepAdvance.remove(id);
        stepValidatedAt.remove(id);
        lastBlockedHint.remove(id);
        clearMarkers(id);
        hideBossBar(player);
        tutorialArenas.remove(id);
        // The intro props are shared: only take them down when nobody is still in the tutorial.
        if (!activeStep.isEmpty()) {
            return;
        }
        cleanupIntroProps();
    }

    public void stop(Player player, boolean silent) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        activeStep.remove(id);
        pendingWalkTargets.remove(id);
        introTicks.remove(id);
        returnLocations.remove(id);
        lastStepAdvance.remove(id);
        stepValidatedAt.remove(id);
        lastBlockedHint.remove(id);
        clearMarkers(id);
        hideBossBar(player);
        cleanupIntroProps();
        player.setWalkSpeed(0.2f);
        exitPracticeCourt(player);
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(id, fr.bosketball.model.PlayerStatus.LOBBY);
        }
        if (!silent) {
            Msg.send(player, Msg.info("Tutorial paused. Resume any time with <yellow>/tuto</yellow>."));
        }
    }

    /**
     * Manual advance (/tuto next). {@code force} skips without validation; otherwise the step must
     * have been validated by the matching in-world action.
     */
    public void advance(Player player, boolean force) {
        if (player == null) {
            return;
        }
        Integer current = activeStep.get(player.getUniqueId());
        if (current == null) {
            start(player);
            return;
        }
        if (!force && !stepValidatedAt.containsKey(player.getUniqueId())) {
            long now = System.currentTimeMillis();
            long last = lastBlockedHint.getOrDefault(player.getUniqueId(), 0L);
            if (now - last > 2500L) {
                lastBlockedHint.put(player.getUniqueId(), now);
                Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
                Msg.send(player, Msg.warn("<white>" + step.objective() + "</white> <dark_gray>•</dark_gray> "
                    + "<gray>or use <yellow>/tuto skip</yellow> to move on.</gray>"));
            }
            return;
        }
        advanceStep(player);
    }

    /** Jump to a step from the tutorial menu (replay chapter). */
    public void gotoStep(Player player, int index) {
        if (player == null) {
            return;
        }
        int target = Math.clamp(index, 0, STEPS.size() - 1);
        activeStep.put(player.getUniqueId(), target);
        stepValidatedAt.remove(player.getUniqueId());
        lastStepAdvance.put(player.getUniqueId(), System.currentTimeMillis());
        showBossBar(player);
        showStep(player, target);
    }

    private void advanceStep(Player player) {
        UUID id = player.getUniqueId();
        Integer current = activeStep.get(id);
        if (current == null) {
            return;
        }
        Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        if (step.revealCoins() > 0) {
            profile.addEarnedCoins(step.revealCoins());
            plugin.statsManager().markDirty(id);
            Msg.send(player, Msg.success("Step complete! " + Msg.coinsBadge(step.revealCoins())));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.5f);
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.3f);
        stepValidatedAt.remove(id);
        clearMarkers(id);

        int next = current + 1;
        profile.setTutorialStep(next);
        plugin.statsManager().markDirty(id);
        if (next >= STEPS.size()) {
            finish(player);
            return;
        }
        activeStep.put(id, next);
        showStep(player, next);
    }

    public void finish(Player player) {
        if (player == null) {
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        boolean firstTime = !profile.tutorialCompleted();
        profile.setTutorialCompleted(true);
        profile.setTutorialStep(STEPS.size());
        plugin.statsManager().markDirty(player.getUniqueId());
        activeStep.remove(player.getUniqueId());
        pendingWalkTargets.remove(player.getUniqueId());
        stepValidatedAt.remove(player.getUniqueId());
        clearMarkers(player.getUniqueId());
        hideBossBar(player);

        var cfg = plugin.settings().tutorial();
        if (firstTime) {
            profile.addEarnedCoins(cfg.completionCoins());
            profile.addXp(cfg.completionXp());
            plugin.statsManager().markDirty(player.getUniqueId());
            plugin.statsManager().saveProfileAsync(profile);
            plugin.progressService().evaluateAchievements(profile);
        }
        exitPracticeCourt(player);
        safe("spawn", () -> returnToSpawn(player));
        safe("outro", () -> playOutro(player, firstTime ? cfg.completionCoins() : 0));
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(player.getUniqueId(), fr.bosketball.model.PlayerStatus.LOBBY);
        }

        Msg.sendFramedCard(player, "TUTORIAL COMPLETE", List.of(
            firstTime
                ? "<#4ADE80>Reward:</#4ADE80> " + Msg.coinsBadge(cfg.completionCoins()) + " <dark_gray>•</dark_gray> <white>"
                  + cfg.completionXp() + " XP</white>"
                : "<gray>You already claimed the completion reward.</gray>",
            "<gray>Next up:</gray> <white>/play 1v1</white> <gray>for your first ranked game,</gray>",
            "<gray>or</gray> <white>/quests</white> <gray>to see your daily objectives.</gray>"
        ));
    }

    // ------------------------------------------------------------------ cinematic intro

    private void playIntro(Player player) {
        UUID id = player.getUniqueId();
        Location startLocation = player.getLocation().clone();
        returnLocations.put(id, startLocation);

        introTicks.put(id, 0);
        player.setWalkSpeed(0.0f);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.BLINDNESS, 110, 1, false, false, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SLOWNESS, 110, 3, false, false, false));
        Msg.title(player, "<black> </black>", "<gray>Entering the arena…</gray>", 0, 40, 10);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.7f, 0.6f);

        Location court = findCourtLocation();
        if (court != null) {
            player.teleportAsync(court);
        }
    }

    private Location findCourtLocation() {
        Arena court = plugin.gameManager() != null ? plugin.gameManager().acquireSoloCourt() : null;
        if (court == null && plugin.practicePool() != null
            && (plugin.gameManager() == null || !plugin.gameManager().instancedCourtsRequested())) {
            court = plugin.practicePool().acquireFreePracticeArena(
                arenaId -> plugin.gameManager() == null ? null : plugin.gameManager().getSessionByArena(arenaId));
        }

        if (court == null && plugin.arenaManager() != null
            && (plugin.gameManager() == null || !plugin.gameManager().instancedCourtsRequested())) {
            court = plugin.arenaManager().firstArenaOrNull();
        }
        if (court == null) {
            return null;
        }
        Location destination = court.centerCourtLocation();
        if (destination == null || destination.getWorld() == null) {
            return null;
        }
        destination.getWorld().setChunkForceLoaded(
            destination.getBlockX() >> 4, destination.getBlockZ() >> 4, true);
        return destination.clone().add(0, 0.2, 0);
    }

    private void tickIntro(Player player, int tick) {
        Location centre = player.getLocation().clone();
        switch (tick) {
            case 0 -> {
                centre.getWorld().spawnParticle(Particle.PORTAL, centre.clone().add(0, 1.2, 0), 80, 1.4, 1.6, 1.4, 0.6);
                centre.getWorld().playSound(centre, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.7f);
            }
            case 12 -> {
                spawnSwarmRing(centre);
                player.playSound(centre, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.15f);
            }
            case 45 -> player.playSound(centre, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.55f, 1.4f);
            case 70 -> {
                Msg.title(player,
                    Msg.gradBold(Msg.GRAD_BRAND, "ʙᴏꜱᴋᴇᴛʙᴀʟʟ"),
                    "<white>3D competitive basketball</white>",
                    10, 55, 20);
                player.playSound(centre, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
                player.playSound(centre, Sound.ITEM_TRIDENT_THUNDER, 0.35f, 1.4f);
                centre.getWorld().spawnParticle(Particle.FIREWORK, centre.clone().add(0, 1.4, 0), 60, 0.9, 1.1, 0.9, 0.15);
            }
            case 96 -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
                player.setWalkSpeed(0.2f);
                cleanupIntroProps();

                prepareLiveCourt(player);
            }
            case INTRO_TOTAL_TICKS -> {
                introTicks.remove(player.getUniqueId());
                showStep(player, currentStep(player));
            }
            default -> {
            }
        }
        if (tick >= 12 && tick <= 96 && tick % 2 == 0) {
            animateSwarmRing(centre, (96 - tick) / 84.0);
        }
    }

    private void spawnSwarmRing(Location centre) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < 14; i++) {
            double angle = (Math.PI * 2.0 / 14.0) * i;
            Location spawn = centre.clone().add(Math.cos(angle) * 4.2, 0.4 + (i % 3) * 0.5, Math.sin(angle) * 4.2);
            Material token = switch (i % 5) {
                case 0 -> Material.GOLD_INGOT;
                case 1 -> Material.ORANGE_DYE;
                case 2 -> Material.WHITE_WOOL;
                case 3 -> Material.AMETHYST_SHARD;
                default -> Material.FIREWORK_ROCKET;
            };
            ItemDisplay display = world.spawn(spawn, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(INTRO_TAG);
                d.setBillboard(Display.Billboard.CENTER);
                d.setInterpolationDuration(2);
                d.setViewRange(2.0f);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setItemStack(new ItemStack(token));
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f), new Quaternionf(),
                    new Vector3f(0.42f, 0.42f, 0.42f), new Quaternionf()));
            });
            introProps.add(display);
        }
    }

    private void animateSwarmRing(Location centre, double closeness) {
        int index = 0;
        int total = introProps.size();
        float spin = (float) (tickCounter * 0.16);
        for (org.bukkit.entity.Entity entity : introProps) {
            if (!(entity instanceof ItemDisplay display) || !display.isValid()) {
                index++;
                continue;
            }
            double angle = (Math.PI * 2.0 / Math.max(1, total)) * index + spin;
            double radius = 0.6 + 3.6 * closeness;
            double height = 0.5 + closeness * 1.4 + Math.sin(tickCounter * 0.12 + index) * 0.18;
            display.teleport(centre.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius));
            float scale = (float) (0.18 + 0.42 * closeness);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(3);
            display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf().rotateY(spin * 2f + index),
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
            index++;
        }
        if (tickCounter % 6 == 0) {
            centre.getWorld().spawnParticle(Particle.END_ROD, centre.clone().add(0, 1.2, 0), 4,
                2.2 * closeness + 0.4, 0.7, 2.2 * closeness + 0.4, 0.01);
        }
    }

    private void cleanupIntroProps() {
        for (org.bukkit.entity.Entity entity : introProps) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        introProps.clear();
    }

    private void prepareLiveCourt(Player player) {
        if (plugin.gameManager() == null) {
            return;
        }
        GameManager gm = plugin.gameManager();
        MatchSession existing = gm.getSessionOf(player.getUniqueId());
        if (existing != null && existing.mode() == GameModeType.PRACTICE) {
            return;
        }
        if (existing != null) {
            existing.removePlayer(player, true);
        }
        Location before = player.getLocation().clone();
        gm.startPractice(player);
        MatchSession session = gm.getSessionOf(player.getUniqueId());
        if (session != null && session.arena() != null) {
            tutorialArenas.put(player.getUniqueId(), session.arena());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (gm.getSessionOf(player.getUniqueId()) == null) {
                player.teleport(before);
                Msg.send(player, Msg.warn("The practice court is busy — the tutorial continues where you are."));
            }
        }, 5L);
    }

    private void exitPracticeCourt(Player player) {
        tutorialArenas.remove(player.getUniqueId());
        if (plugin.gameManager() == null) {
            returnFromOrigin(player);
            return;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session != null && session.mode() == GameModeType.PRACTICE) {
            session.removePlayer(player, true);
        }
        returnFromOrigin(player);
    }

    /** Runs one rendering step; a single failure can never kill the tick task or abort a step. */
    private void safe(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("[tutorial] " + what + " failed: " + failure);
        }
    }

    /** Where a finished tour drops the player: the configured lobby spawn, never the practice court. */
    private Location spawnTarget() {
        if (plugin.protectionManager() != null) {
            Location spawn = plugin.protectionManager().spawnLocation();
            if (spawn != null && spawn.getWorld() != null) {
                return spawn.clone().add(0, 0.2, 0);
            }
        }
        var worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return null;
        }
        return worlds.get(0).getSpawnLocation().add(0.5, 0.2, 0.5);
    }

    /** Teleports the player back to the lobby spawn (used at the very end of the tour). */
    public void returnToSpawn(Player player) {
        if (player == null) {
            return;
        }
        Location spawn = spawnTarget();
        if (spawn == null || spawn.getWorld() == null) {
            returnFromOrigin(player);
            return;
        }
        returnLocations.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.teleportAsync(spawn);
            }
        });
    }

    /**
     * First-join onboarding: starts the guided tour by itself when the server asks for it
     * ({@code tutorial.force-on-first-join}). Players who already finished it are left alone.
     */
    public void maybeStartOnJoin(Player player) {
        if (player == null || !plugin.settings().tutorial().enabled()
            || !plugin.settings().tutorial().forceOnFirstJoin()) {
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        if (profile.tutorialCompleted() || isRunning(player)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || isRunning(player)) {
                return;
            }
            if (plugin.gameManager() != null
                && plugin.gameManager().isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
                return;
            }
            Msg.send(player, Msg.info("Welcome! Starting the <white>new player guide</white> — "
                + "<gray>/tuto skip</gray> to stop it any time."));
            start(player);
        }, 60L);
    }

    /** Everything the staff needs to understand why a tutorial looks empty in game. */
    public List<String> diagnostics(Player player) {
        var cfg = plugin.settings().tutorial();
        List<String> out = new ArrayList<>();
        out.add("<gray>enabled:</gray> <white>" + cfg.enabled() + "</white> <dark_gray>•</dark_gray> "
            + "<gray>force-on-join:</gray> <white>" + cfg.forceOnFirstJoin() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>practice-court:</gray> <white>" + cfg.usePracticeCourt() + "</white>");
        out.add("<gray>running:</gray> <white>" + isRunning(player) + "</white> <dark_gray>•</dark_gray> "
            + "<gray>step:</gray> <white>" + (currentStep(player) + 1) + "/" + STEPS.size() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>tick:</gray> <white>" + tickCounter + "</white>");
        out.add("<gray>hologram:</gray> <white>" + (hologram != null && hologram.isValid()) + "</white>"
            + " <dark_gray>•</dark_gray> <gray>markers:</gray> <white>" + playerMarkers.size() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>bossbar:</gray> <white>"
            + bossBars.containsKey(player.getUniqueId()) + "</white>");
        Location spawn = spawnTarget();
        out.add("<gray>spawn:</gray> <white>" + (spawn == null ? "none"
            : spawn.getWorld().getName() + " " + spawn.getBlockX() + " " + spawn.getBlockY() + " "
                + spawn.getBlockZ()) + "</white>");
        out.add("<gray>court available:</gray> <white>" + (findCourtLocation() != null) + "</white>"
            + " <dark_gray>•</dark_gray> <gray>arena bound:</gray> <white>"
            + tutorialArenas.containsKey(player.getUniqueId()) + "</white>");
        return out;
    }

    private void returnFromOrigin(Player player) {
        Location back = returnLocations.remove(player.getUniqueId());
        if (back == null || back.getWorld() == null) {
            return;
        }
        player.teleport(back);
    }

    private void playOutro(Player player, int coins) {
        Location centre = player.getLocation().clone();
        World world = centre.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, centre.clone().add(0, 1.2, 0), 90, 0.7, 1.0, 0.7, 0.25);
            world.spawnParticle(Particle.FIREWORK, centre.clone().add(0, 1.6, 0), 80, 1.1, 1.2, 1.1, 0.18);
            world.playSound(centre, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 1.0f);
            world.playSound(centre, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
        }
        Msg.playPersonalPackSound(player, "crowd.cheer", 1.2f, 1.1f);
        Msg.title(player,
            Msg.gradBold(Msg.GRAD_MINT, "✦ ᴛᴜᴛᴏʀɪᴀʟ ᴄᴏᴍᴘʟᴇᴛᴇ ✦"),
            coins > 0 ? "<gray>+<white>" + coins + " Coins</white> welcome bonus</gray>"
                : "<gray>Replay finished — you're a veteran now.</gray>",
            150, 2600, 500);

        for (int delay = 0; delay < 40; delay += 4) {
            final int d = delay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.getWorld() == null) {
                    return;
                }
                Location at = player.getLocation().add(
                    (Math.random() - 0.5) * 2.0, 2.4 + (d / 40.0), (Math.random() - 0.5) * 2.0);
                player.getWorld().spawnParticle(Particle.WAX_ON, at, 1, 0.05, 0.05, 0.05, 0.0);
            }, delay);
        }
    }

    // ------------------------------------------------------------------ step presentation

    private void showStep(Player player, int index) {
        if (player == null) {
            return;
        }
        int safe = Math.clamp(index, 0, STEPS.size() - 1);
        Step step = STEPS.get(safe);
        activeStep.put(player.getUniqueId(), safe);
        stepValidatedAt.remove(player.getUniqueId());
        lastStepAdvance.put(player.getUniqueId(), System.currentTimeMillis());
        lastBlockedHint.remove(player.getUniqueId());
        clearMarkers(player.getUniqueId());

        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        player.setWalkSpeed(0.2f);
        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.35f);
        Msg.title(player,
            Msg.gradBold(Msg.GRAD_GOLD, step.title()),
            "<gray>" + step.subtitle() + "</gray>", 120, 900, 200);

        List<String> body = new ArrayList<>();
        body.add("<dark_gray>Step <white>" + (safe + 1) + "/" + STEPS.size() + "</white> "
            + "<dark_gray>•</dark_gray> <#FFD43B>" + step.objective() + "</#FFD43B></dark_gray>");
        body.addAll(step.instructions());
        if (step.revealCoins() > 0) {
            body.add("<dark_gray>Reward on completion:</dark_gray> " + Msg.coinsBadge(step.revealCoins()));
        }
        Msg.sendFramedCard(player, step.title(), body);

        player.sendMessage(Component.text()
            .append(Msg.parse("<gray>▸ </gray>"))
            .append(Component.text("[ᴄᴏɴᴛɪɴᴜᴇ]", Style.style(TextColor.color(0x4ADE80), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<green>Advance to the next step</green>")))
                .clickEvent(ClickEvent.runCommand("/tuto next")))
            .append(Component.text("  "))
            .append(Component.text("[ᴍᴇɴᴜ]", Style.style(TextColor.color(0xFFD43B), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<yellow>Open the tutorial menu</yellow>")))
                .clickEvent(ClickEvent.runCommand("/tuto menu")))
            .append(Component.text("  "))
            .append(Component.text("[ꜱᴛᴏᴘ]", Style.style(TextColor.color(0xF87171), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<red>Pause the tutorial</red>")))
                .clickEvent(ClickEvent.runCommand("/tuto stop")))
            .build());

        safe("decorate", () -> decorateStep(player, step));
        safe("hud", () -> updateHud(player));
    }

    /** Spawns the in-world objective markers for a step. */
    private void decorateStep(Player player, Step step) {
        UUID id = player.getUniqueId();
        switch (step.requirement()) {
            case "walk" -> {
                Location ring = ringLocationAhead(player, 4.5);
                if (ring == null) {
                    ring = player.getLocation().clone();
                }
                if (ring != null) {
                    pendingWalkTargets.put(id, ring.clone());
                    spawnGoldRing(player, ring, "<#FFD43B><bold>STEP " + (step.index() + 1) + "/" + STEPS.size()
                        + "</bold></#FFD43B>", "<white>Walk into the ring</white>");
                    player.playSound(ring, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                }
            }
            case "shoot" -> {
                Location spot = ringLocationAhead(player, 3.0);
                if (spot == null) {
                    spot = player.getLocation().clone();
                }
                if (spot != null) {
                    spawnGoldRing(player, spot, "<#4ADE80><bold>SHOOT FROM HERE</bold></#4ADE80>",
                        "<gray>Hold right-click, release in the green</gray>");
                }
                pointAtRim(player, "<#4ADE80><bold>SHOOT HERE</bold></#4ADE80>");
            }
            case "dunk" -> pointAtRim(player, "<#FF9636><bold>DUNK HERE</bold></#FF9636>");
            case "shop" -> Msg.send(player, Msg.info("Balance: <white>"
                + plugin.statsManager().getOrCreate(player).coins() + "</white> Coins — open <yellow>/shop</yellow>."));
            default -> {
            }
        }
    }

    /** A flat, safe anchor a few blocks in front of the player (never inside a wall). */
    private Location ringLocationAhead(Player player, double distance) {
        Location base = player.getLocation().clone();
        Vector dir = base.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Location target = base.clone().add(dir.multiply(distance));
        target.setYaw(base.getYaw());
        World world = target.getWorld();
        if (world == null) {
            return null;
        }
        try {
            Location highest = world.getHighestBlockAt(target).getLocation();
            double delta = highest.getY() - base.getY();
            if (delta >= -2 && delta <= 3) {
                target.setY(highest.getY() + 1.0);
            } else {
                target.setY(base.getY());
            }
        } catch (Throwable ignored) {
            target.setY(base.getY());
        }
        return target;
    }

    /**
     * The gold ring: sixteen {@link BlockDisplay} gold slices laid out on a circle, a floating
     * instruction board and a light beam. Pure display entities — the world is never modified.
     */
    private void spawnGoldRing(Player player, Location centre, String title, String hint) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        List<org.bukkit.entity.Entity> markers = playerMarkers.computeIfAbsent(
            player.getUniqueId(), k -> new ArrayList<>());
        int slices = 16;
        double radius = 1.45;
        for (int i = 0; i < slices; i++) {
            double angle = (Math.PI * 2.0 / slices) * i;
            Location at = centre.clone().add(Math.cos(angle) * radius, 0.06, Math.sin(angle) * radius);
            BlockDisplay slice = world.spawn(at, BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(MARKER_TAG);
                d.setBlock(goldSlice());
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(1.6f);
                d.setInterpolationDuration(4);
                d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf().rotateY((float) -angle),
                    new Vector3f(0.34f, 0.05f, 0.14f),
                    new Quaternionf()));
            });
            markers.add(slice);
        }
        TextDisplay board = world.spawn(centre.clone().add(0, 1.9, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(MARKER_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.8f);
            d.setSeeThrough(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setLineWidth(200);
            d.text(Msg.parse(title + "\n" + hint));
        });
        markers.add(board);
        world.spawnParticle(Particle.END_ROD, centre.clone().add(0, 0.2, 0), 18, 1.3, 0.15, 1.3, 0.01);
        world.playSound(centre, Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.7f);
    }

    private void pointAtRim(Player player, String label) {
        MatchSession session = plugin.gameManager() == null ? null : plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null || session.arena() == null) {
            return;
        }
        Vector rim = session.arena().geometry().southHoopRim();
        Location at = rim.toLocation(player.getWorld()).add(0, 0.9, 0);
        TextDisplay marker = player.getWorld().spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(MARKER_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(2.0f);
            d.setSeeThrough(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.text(Msg.parse(label));
        });
        playerMarkers.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(marker);
    }

    private void clearMarkers(UUID id) {
        List<org.bukkit.entity.Entity> markers = playerMarkers.remove(id);
        if (markers == null) {
            return;
        }
        for (org.bukkit.entity.Entity marker : markers) {
            if (marker != null && marker.isValid()) {
                marker.remove();
            }
        }
        markers.clear();
        pendingWalkTargets.remove(id);
    }

    /** Removes every tutorial marker in every world (used by /bb displays repair and on reload). */
    public int purgeMarkers() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(MARKER_TAG)
                    || entity.getScoreboardTags().contains(INTRO_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        playerMarkers.clear();
        introProps.clear();
        pendingWalkTargets.clear();
        return removed;
    }

    // ------------------------------------------------------------------ HUD

    private void showBossBar(Player player) {
        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), k ->
            BossBar.bossBar(Msg.parse("<#FFD43B><bold>TUTORIAL</bold></#FFD43B>"), 0f,
                BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
        player.showBossBar(bar);
    }

    private void hideBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void updateHud(Player player) {
        UUID id = player.getUniqueId();
        Integer current = activeStep.get(id);
        if (current == null) {
            return;
        }
        Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
        boolean validated = stepValidatedAt.containsKey(id);
        BossBar bar = bossBars.get(id);
        if (bar == null) {
            showBossBar(player);
            bar = bossBars.get(id);
        }
        if (bar != null) {
            bar.name(Msg.parse("<#FFD43B><bold>" + step.title() + "</bold></#FFD43B> <dark_gray>•</dark_gray> <white>"
                + (validated ? "✔ validated — moving on…" : step.objective()) + "</white>"));
            bar.progress(Math.clamp((current + (validated ? 1f : 0.5f)) / STEPS.size(), 0f, 1f));
            bar.color(validated ? BossBar.Color.GREEN : BossBar.Color.YELLOW);
        }
        player.sendActionBar(Msg.parse("<#FFD43B>◆</#FFD43B> <white>" + (current + 1) + "/" + STEPS.size()
            + "</white> <dark_gray>|</dark_gray> <gray>" + (validated ? "validated ✔" : step.objective()) + "</gray>"));
    }

    // ------------------------------------------------------------------ validation triggers

    /** Called by the game engine when the player performs the action of the current step. */
    public void notifyAction(Player player, String action, ProgressStat unusedStat) {
        if (player == null || !isRunning(player)) {
            return;
        }
        Step step = STEPS.get(Math.min(currentStep(player), STEPS.size() - 1));
        if (!step.requirement().equals(action)) {
            return;
        }
        completeStep(player, action);
    }

    private void completeStep(Player player, String action) {
        UUID id = player.getUniqueId();
        Integer current = activeStep.get(id);
        if (current == null || stepValidatedAt.containsKey(id)) {
            return;
        }
        Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
        stepValidatedAt.put(id, System.currentTimeMillis());
        Msg.send(player, Msg.success("<#4ADE80><bold>✔ " + step.subtitle() + "</bold></#4ADE80>"));
        Msg.playPersonalPackSound(player, "game.green_release", 0.85f, 1.35f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.7f);
        updateHud(player);
        clearMarkers(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!Integer.valueOf(current).equals(activeStep.get(id))) {
                return;
            }
            advance(player, true);
        }, 25L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Location target = pendingWalkTargets.get(id);
        if (target == null) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !to.getWorld().equals(target.getWorld())) {
            return;
        }
        if (currentStep(event.getPlayer()) != 1) {
            pendingWalkTargets.remove(id);
            return;
        }
        if (to.distanceSquared(target) <= RING_ENTER_RADIUS_SQ) {
            Player player = event.getPlayer();
            pendingWalkTargets.remove(id);
            turnRingGreen(player, target);
            completeStep(player, "walk");
        }
    }

    private void turnRingGreen(Player player, Location centre) {
        List<org.bukkit.entity.Entity> markers = playerMarkers.get(player.getUniqueId());
        if (markers != null) {
            for (org.bukkit.entity.Entity marker : markers) {
                if (marker instanceof BlockDisplay display && display.isValid()) {
                    display.setBlock(emeraldSlice());
                }
            }
        }
        centre.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, centre.clone().add(0, 0.4, 0), 24, 1.2, 0.3, 1.2, 0.02);
        centre.getWorld().playSound(centre, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && isRunning(event.getPlayer()) && currentStep(event.getPlayer()) == 4) {
            notifyAction(event.getPlayer(), "defense", null);
        }
    }

    // ------------------------------------------------------------------ ticking

    public void tickAll() {
        tickCounter++;

        if (introTicks.isEmpty() && Bukkit.getOnlinePlayers().isEmpty()) {
            return;   // nobody online: no intro to drive, no hologram to bob
        }

        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(introTicks.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                introTicks.remove(entry.getKey());
                continue;
            }
            int tick = entry.getValue();
            safe("intro", () -> tickIntro(player, tick));
            if (introTicks.containsKey(entry.getKey())) {
                introTicks.put(entry.getKey(), tick + 1);
            }
        }

        for (UUID id : new ArrayList<>(activeStep.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                continue;
            }
            safe("markers", () -> animateMarkers(player));
            if (tickCounter % 20 == 0) {
                safe("hud", () -> updateHud(player));
            }
            if (tickCounter % 60 == 0) {
                safe("watchdog", () -> softSkipWatchdog(player));
            }
        }

        if (tickCounter % 100 == 0 && (hologram == null || !hologram.isValid())) {
            safe("hologram", this::spawnHologram);
        }
        if (tickCounter % 10 == 0 && hologram != null && hologram.isValid()) {
            Location base = hologramLocation();
            if (base != null && fr.bosketball.util.Viewers.anyWithin(base, 96.0)) {
                hologram.teleport(base.clone().add(0, Math.sin(tickCounter * 0.05) * 0.08, 0));
            }
        }
    }

    private void animateMarkers(Player player) {
        UUID id = player.getUniqueId();
        List<org.bukkit.entity.Entity> markers = playerMarkers.get(id);
        float spin = tickCounter * 0.05f;
        Location ring = pendingWalkTargets.get(id);
        if (markers != null) {
            for (org.bukkit.entity.Entity marker : markers) {
                if (!(marker instanceof BlockDisplay display) || !display.isValid()) {
                    continue;
                }
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(3);
                display.setTransformation(new Transformation(
                    new Vector3f(0f, (float) (Math.sin(tickCounter * 0.08) * 0.03), 0f),
                    new Quaternionf().rotateY(spin),
                    new Vector3f(0.34f, 0.05f, 0.14f),
                    new Quaternionf()));
            }
        }
        if (ring != null && tickCounter % 8 == 0) {
            ring.getWorld().spawnParticle(Particle.END_ROD, ring.clone().add(0, 0.15, 0), 3, 1.1, 0.05, 1.1, 0.005);
            ring.getWorld().spawnParticle(Particle.FLAME, ring.clone().add(0, 1.0, 0), 2, 0.15, 0.4, 0.15, 0.0);
        }
        // Guide line of particles from the player to the current objective.
        if (ring != null && tickCounter % 5 == 0) {
            Vector from = player.getLocation().add(0, 1.0, 0).toVector();
            Vector to = ring.clone().add(0, 0.6, 0).toVector();
            for (double t = 0.15; t < 0.95; t += 0.16) {
                Vector point = from.clone().add(to.clone().subtract(from).multiply(t));
                player.getWorld().spawnParticle(Particle.GLOW, point.toLocation(player.getWorld()), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    /** Nobody can ever be stuck: after the soft delay the step validates itself with a hint. */
    private void softSkipWatchdog(Player player) {
        UUID id = player.getUniqueId();
        Integer current = activeStep.get(id);
        if (current == null || stepValidatedAt.containsKey(id) || isInIntro(player)) {
            return;
        }
        Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
        if (!step.autoValidated()) {
            return;
        }
        long started = lastStepAdvance.getOrDefault(id, System.currentTimeMillis());
        if (System.currentTimeMillis() - started < step.autoAdvanceMs()) {
            return;
        }
        Msg.send(player, Msg.info("No worries — moving on. <gray>You can replay any chapter with</gray> <yellow>/tuto menu</yellow>."));
        completeStep(player, "timeout");
    }

    /** Re-prints the current step card (used by /tuto show and the help button). */
    public void showCurrentStep(Player player) {
        if (player == null || !isRunning(player)) {
            return;
        }
        showStep(player, Math.min(currentStep(player), STEPS.size() - 1));
    }

    // ------------------------------------------------------------------ progress helpers

    public int totalStepRewards() {
        int sum = 0;
        for (Step s : STEPS) {
            sum += s.revealCoins();
        }
        return sum;
    }

    public double progressOf(PlayerProfile profile) {
        if (profile == null) {
            return 0.0;
        }
        if (profile.tutorialCompleted()) {
            return 1.0;
        }
        return Math.min(1.0, (double) profile.tutorialStep() / STEPS.size());
    }

    public Map<Integer, String> stepIndex() {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (Step s : STEPS) {
            out.put(s.index(), s.title());
        }
        return out;
    }

    public static String requirementLabel(String requirement) {
        for (Step step : STEPS) {
            if (step.requirement().equals(requirement)) {
                return step.objective();
            }
        }
        return requirement == null ? "" : requirement.toLowerCase(Locale.ROOT);
    }

    public static ItemStack tutorialItem(String modelId, String nameMini, List<String> loreMini) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", modelId));
            meta.displayName(Msg.parse(nameMini));
            List<Component> lore = new ArrayList<>();
            for (String line : loreMini) {
                lore.add(Msg.parse(fr.bosketball.util.Glyphs.smallCapsLabels(line)));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
