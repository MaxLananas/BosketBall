package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.game.GameManager;
import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.ProgressStat;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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

public final class TutorialManager implements Listener {

    public static final String HOLO_TAG = "bosketball_tutorial_holo";

    public static final String INTRO_TAG = "bosketball_tutorial_intro";

    public record Step(
        int index,
        String title,
        String subtitle,
        List<String> instructions,
        int revealCoins,
        String requirement,
        String iconModel
    ) {
    }

    private static final List<Step> STEPS = List.of(
        new Step(0, "ᴡᴇʟᴄᴏᴍᴇ ᴛᴏ ʙᴏꜱᴋᴇᴛʙᴀʟʟ", "Let's get you on the court",
            List.of(
                "<gray>BosketBall is a full <white>3D competitive basketball</white> server.",
                "<gray>You are standing on a <white>real practice court</white> right now.",
                "<gray>Your hotbar holds every menu: <white>Play</white>, <white>Shop</white>, <white>Locker</white> & <white>Profile</white>.",
                "<gray>Type <yellow>/tuto next</yellow> when you're ready."
            ), 0, "step", "ref_medal_gold"),
        new Step(1, "ᴍᴏᴠᴇ & ʟᴏᴏᴋ", "Test your movement",
            List.of(
                "<gray>Walk into the glowing <white>gold ring</white> ahead of you.",
                "<gray>That confirms your movement, camera and sprint are registered.",
                "<gray>Sprinting and crossovers matter a lot in a real match."
            ), 15, "walk", "ref_calendar_check"),
        new Step(2, "ꜱʜᴏᴏᴛɪɴɢ", "The green window",
            List.of(
                "<gray>Hold <white>Right-Click</white> with the ball to draw your shot.",
                "<gray>Release when the meter enters the <#4ADE80><bold>green zone</bold></#4ADE80> for a pure swish.",
                "<gray>Too early or too late and it rattles out.",
                "<gray>Take a real shot at the rim in front of you."
            ), 25, "shoot", "ref_calendar_check"),
        new Step(3, "ᴅᴜɴᴋɪɴɢ", "Two-click slam",
            List.of(
                "<gray>Jump inside the paint and <white>click once</white> to rise toward the rim.",
                "<gray>Click a <white>second time</white> inside the green timing bar to slam it home.",
                "<gray>Try Tomahawk, Windmill, 360 Spin, Between-the-Legs and Meteor Jam."
            ), 25, "dunk", "icon_dunk"),
        new Step(4, "ᴅᴇꜰᴇɴꜱᴇ", "Clamp your matchup",
            List.of(
                "<gray><white>Sneak</white>, <white>F</white> or <white>Slot 2</white> performs a V-Crossover.",
                "<gray><white>Left-Click</white> on defense pokes the ball loose or blocks the shot.",
                "<gray>Breaking ankles earns bonus Coins and a highlight banner."
            ), 20, "defense", "icon_defense"),
        new Step(5, "ᴇᴄᴏɴᴏᴍʏ", "Coins & cosmetics",
            List.of(
                "<gray>You earn Coins for buckets, assists, steals, blocks, wins and MVPs.",
                "<gray>Spend them in <white>/shop</white> on basketballs, dunk styles and 56 chat tags.",
                "<gray>Every purchase now asks you to <white>confirm</white> before anything is spent.",
                "<gray>Complete <white>/quests</white> and climb the achievements."
            ), 25, "shop", "ref_shop"),
        new Step(6, "Qᴜᴇᴜᴇ ᴜᴘ", "Time to play",
            List.of(
                "<gray><white>/play 1v1</white> ranked, or <white>/play</white> for the full menu.",
                "<gray><white>Casual</white> modes never cost you ELO — perfect to warm up.",
                "<gray>Finish this step to claim your welcome bonus."
            ), 40, "queue", "icon_play")
    );

    private static final int INTRO_TOTAL_TICKS = 130;

    private final BosketBallPlugin plugin;
    private final Map<UUID, Integer> activeStep = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingWalkTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Location> returnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStepAdvance = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> introTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Arena> tutorialArenas = new ConcurrentHashMap<>();
    private final List<org.bukkit.entity.Entity> introProps = new ArrayList<>();
    private TextDisplay hologram;
    private Interaction guideBox;
    private int tickCounter = 0;

    public TutorialManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public static List<Step> steps() {
        return STEPS;
    }

    public static int stepCount() {
        return STEPS.size();
    }

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
                "<#FFD43B><bold>✦ ɴᴇᴡ ᴘʟᴀʏᴇʀ ɢᴜɪᴅᴇ ✦</bold></#FFD43B>\n"
                    + "<white>Right-click the guide or type <bold>/tuto</bold></white>\n"
                    + "<gray>7 quick steps • free Coins</gray>"
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

    public boolean isRunning(Player player) {
        return player != null && activeStep.containsKey(player.getUniqueId());
    }

    public boolean isInIntro(Player player) {
        return player != null && introTicks.containsKey(player.getUniqueId());
    }

    public int currentStep(Player player) {
        return activeStep.getOrDefault(player.getUniqueId(), 0);
    }

    public void start(Player player) {
        if (player == null) {
            return;
        }
        if (plugin.gameManager() != null && plugin.gameManager().isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            Msg.send(player, Msg.warn("Finish your match first, then run <yellow>/tuto</yellow>."));
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        profile.setTutorialStep(0);
        plugin.statsManager().markDirty(player.getUniqueId());
        activeStep.put(player.getUniqueId(), 0);
        returnLocations.putIfAbsent(player.getUniqueId(), player.getLocation().clone());
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(player.getUniqueId(), fr.bosketball.model.PlayerStatus.TUTORIAL);
        }
        playIntro(player);
    }

    public void stop(Player player, boolean silent) {
        if (player == null) {
            return;
        }
        activeStep.remove(player.getUniqueId());
        pendingWalkTargets.remove(player.getUniqueId());
        introTicks.remove(player.getUniqueId());
        cleanupIntroProps();
        player.setWalkSpeed(0.2f);
        exitPracticeCourt(player);
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(player.getUniqueId(), fr.bosketball.model.PlayerStatus.LOBBY);
        }
        if (!silent) {
            Msg.send(player, Msg.info("Tutorial paused. Resume any time with <yellow>/tuto</yellow>."));
        }
    }

    public void advance(Player player, boolean force) {
        if (player == null) {
            return;
        }
        Integer current = activeStep.get(player.getUniqueId());
        if (current == null) {
            start(player);
            return;
        }
        Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
        if (!force && !requirementSatisfied(player, step)) {
            Msg.send(player, Msg.warn("Complete this step first: <white>" + step.subtitle() + "</white>"));
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        if (step.revealCoins() > 0) {
            profile.addEarnedCoins(step.revealCoins());
            plugin.statsManager().markDirty(player.getUniqueId());
            Msg.send(player, Msg.success("Step complete! " + Msg.coinsBadge(step.revealCoins())));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.5f);
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.3f);

        int next = current + 1;
        profile.setTutorialStep(next);
        plugin.statsManager().markDirty(player.getUniqueId());
        if (next >= STEPS.size()) {
            finish(player);
            return;
        }
        activeStep.put(player.getUniqueId(), next);
        showStep(player, next);
    }

    public void finish(Player player) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        boolean firstTime = !profile.tutorialCompleted();
        profile.setTutorialCompleted(true);
        profile.setTutorialStep(STEPS.size());
        plugin.statsManager().markDirty(player.getUniqueId());
        activeStep.remove(player.getUniqueId());
        pendingWalkTargets.remove(player.getUniqueId());

        var cfg = plugin.settings().tutorial();
        if (firstTime) {
            profile.addEarnedCoins(cfg.completionCoins());
            profile.addXp(cfg.completionXp());
            plugin.statsManager().markDirty(player.getUniqueId());
            plugin.statsManager().saveProfileAsync(profile);
            plugin.progressService().evaluateAchievements(profile);
        }
        playOutro(player, firstTime ? cfg.completionCoins() : 0);
        exitPracticeCourt(player);
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
                    "<#FF9E2C><bold>ʙᴏꜱᴋᴇᴛʙᴀʟʟ</bold></#FF9E2C>",
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
            centre.getWorld().spawnParticle(Particle.END_ROD, centre.clone().add(0, 1.2, 0), 4, 2.2 * closeness + 0.4, 0.7, 2.2 * closeness + 0.4, 0.01);
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
            "<#4ADE80><bold>✦ ᴛᴜᴛᴏʀɪᴀʟ ᴄᴏᴍᴘʟᴇᴛᴇ ✦</bold></#4ADE80>",
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

    private void showStep(Player player, int index) {
        Step step = STEPS.get(index);
        lastStepAdvance.put(player.getUniqueId(), System.currentTimeMillis());

        List<String> body = new ArrayList<>();
        body.add("<dark_gray>Step <white>" + (index + 1) + "/" + STEPS.size() + "</white></dark_gray>");
        body.addAll(step.instructions());
        if (step.revealCoins() > 0) {
            body.add("<dark_gray>Reward on completion:</dark_gray> " + Msg.coinsBadge(step.revealCoins()));
        }
        Msg.sendFramedCard(player, step.title(), body);

        player.sendMessage(Component.text()
            .append(Msg.parse("<gray>▸ </gray>"))
            .append(Component.text("[CONTINUE]", net.kyori.adventure.text.format.Style.style(
                    TextColor.color(0x4ADE80), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<green>Advance to the next step</green>")))
                .clickEvent(ClickEvent.runCommand("/tuto next")))
            .append(Component.text("  "))
            .append(Component.text("[STOP]", net.kyori.adventure.text.format.Style.style(
                    TextColor.color(0xF87171), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<red>Pause the tutorial</red>")))
                .clickEvent(ClickEvent.runCommand("/tuto stop")))
            .append(Component.text("  "))
            .append(Component.text("[HELP]", net.kyori.adventure.text.format.Style.style(
                    TextColor.color(0x38BDF8), TextDecoration.BOLD))
                .hoverEvent(HoverEvent.showText(Msg.parse("<aqua>Show this step again</aqua>")))
                .clickEvent(ClickEvent.runCommand("/tuto show")))
            .build());

        switch (index) {
            case 1 -> {
                Location target = player.getLocation().clone().add(
                    player.getLocation().getDirection().setY(0).normalize().multiply(4));
                target.setY(target.getWorld().getHighestBlockYAt(target) + 0.1);
                pendingWalkTargets.put(player.getUniqueId(), target);
                player.getWorld().spawnParticle(Particle.END_ROD, target.clone().add(0, 0.4, 0), 40, 1.2, 0.2, 1.2, 0.02);
            }
            case 2 -> pointAtRim(player, "<#4ADE80><bold>SHOOT HERE</bold></#4ADE80>");
            case 3 -> pointAtRim(player, "<#FF9636><bold>DUNK HERE</bold></#FF9636>");
            case 5 -> Msg.send(player, Msg.info("Try <white>/shop</white> — your balance is <white>"
                + plugin.statsManager().getOrCreate(player).coins() + "</white> Coins."));
            default -> {
            }
        }
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
            d.addScoreboardTag(INTRO_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(2.0f);
            d.setSeeThrough(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.text(Msg.parse(label));
        });
        introProps.add(marker);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (marker.isValid()) {
                marker.remove();
            }
            introProps.remove(marker);
        }, 200L);
    }

    private boolean requirementSatisfied(Player player, Step step) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        long since = lastStepAdvance.getOrDefault(player.getUniqueId(), 0L);
        return switch (step.requirement()) {
            case "walk" -> !pendingWalkTargets.containsKey(player.getUniqueId());
            case "shoot" -> profile.twoPointersAttempted() + profile.threePointersAttempted() > 0;

            case "dunk", "defense", "shop", "queue" -> System.currentTimeMillis() - since >= 2500L;
            default -> System.currentTimeMillis() - since >= 1500L;
        };
    }

    public void showCurrentStep(Player player) {
        if (player == null || !isRunning(player)) {
            return;
        }
        showStep(player, Math.min(currentStep(player), STEPS.size() - 1));
    }

    public void notifyAction(Player player, String action, ProgressStat unusedStat) {
        if (player == null || !isRunning(player)) {
            return;
        }
        Step step = STEPS.get(Math.min(currentStep(player), STEPS.size() - 1));
        if (step.requirement().equals(action)) {
            Msg.send(player, Msg.success("Nice! Type <yellow>/tuto next</yellow> to continue."));
            Msg.playPersonalPackSound(player, "game.green_release", 0.8f, 1.35f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Location target = pendingWalkTargets.get(id);
        if (target == null) {
            return;
        }
        Location to = event.getTo();
        if (to.getWorld().equals(target.getWorld())
            && to.distanceSquared(target) <= 2.25
            && currentStep(event.getPlayer()) == 1) {
            pendingWalkTargets.remove(id);
            Msg.send(event.getPlayer(), Msg.success("Movement confirmed! Type <yellow>/tuto next</yellow>."));
            Msg.playPersonalPackSound(event.getPlayer(), "game.green_release", 0.85f, 1.3f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && isRunning(event.getPlayer()) && currentStep(event.getPlayer()) == 4) {
            notifyAction(event.getPlayer(), "defense", null);
        }
    }

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
            tickIntro(player, tick);
            if (introTicks.containsKey(entry.getKey())) {
                introTicks.put(entry.getKey(), tick + 1);
            }
        }

        if (tickCounter % 40 == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isRunning(player)) {
                    continue;
                }
                Location target = pendingWalkTargets.get(player.getUniqueId());
                if (target != null) {
                    player.getWorld().spawnParticle(Particle.END_ROD,
                        target.clone().add(0, 0.4, 0), 6, 0.6, 0.1, 0.6, 0.01);
                }
            }
        }
        if (tickCounter % 100 == 0 && (hologram == null || !hologram.isValid())) {
            spawnHologram();
        }
        if (tickCounter % 10 == 0 && hologram != null && hologram.isValid()) {
            Location base = hologramLocation();
            if (base != null && fr.bosketball.util.Viewers.anyWithin(base, 96.0)) {
                hologram.teleport(base.clone().add(0, Math.sin(tickCounter * 0.05) * 0.08, 0));
            }
        }
    }

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
