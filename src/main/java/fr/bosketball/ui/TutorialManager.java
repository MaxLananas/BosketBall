package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.ProgressStat;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The BosketBall guided tutorial ({@code /tuto}).
 *
 * <h2>Design intent</h2>
 * The server is dense — shooting, dunks, passes, shop, queue — and a new player has no idea
 * where to start. This tutorial is <b>never forced</b>: {@code tutorial.force-on-first-join}
 * stays {@code false}, the player either types {@code /tuto} or walks to the floating guide
 * hologram at the configured station (default {@code 101 / 61 / 61}, two blocks above the
 * floor, facing West).
 *
 * <h2>Seven steps</h2>
 * <ol>
 *   <li><b>Welcome</b> — the lobby layout, the hotbar and the two NPC stations.</li>
 *   <li><b>Move</b> — walk to a highlighted ring to prove movement register.</li>
 *   <li><b>Shoot</b> — the bow-pull shot meter and the perfect green window.</li>
 *   <li><b>Dunk</b> — the two-click aerial slam timing.</li>
 *   <li><b>Defense</b> — steal poke, crossover and the block.</li>
 *   <li><b>Economy</b> — /shop, the crate, quests and cosmetic tags.</li>
 *   <li><b>Queue</b> — /play, ranked vs casual, and the accept prompt.</li>
 * </ol>
 * Each step validates real player actions through the event pipeline, rewards incremental
 * Coins, and the final step grants a completion bonus plus a trophy tag.
 */
public final class TutorialManager implements Listener {

    /** Marker tag for the tutorial hologram entities. */
    public static final String HOLO_TAG = "bosketball_tutorial_holo";

    /** One tutorial step. */
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
                "<gray>Your hotbar holds every menu: <white>Play</white>, <white>Shop</white>, <white>Locker</white> & <white>Profile</white>.",
                "<gray>Two NPCs stand at the plaza: the <white>Superstar</white> opens <white>/play</white>, the <white>Merchant</white> opens <white>/shop</white>.",
                "<gray>Type <yellow>/tuto next</yellow> when you're ready."
            ), 0, "step", "icon_book"),
        new Step(1, "ᴍᴏᴠᴇ & ʟᴏᴏᴋ", "Test your movement",
            List.of(
                "<gray>Walk into the glowing <white>gold ring</white> ahead of you.",
                "<gray>That confirms your movement, camera and sprint are registered.",
                "<gray>Sprinting and crossovers matter a lot in a real match."
            ), 15, "walk", "icon_target"),
        new Step(2, "ꜱʜᴏᴏᴛɪɴɢ", "The green window",
            List.of(
                "<gray>Hold <white>Right-Click</white> with the ball to draw your shot.",
                "<gray>Release when the meter enters the <#4ADE80><bold>green zone</bold></#4ADE80> for a pure swish.",
                "<gray>Too early or too late and it rattles out.",
                "<gray>Press <white>F</white> here to try the practice shot meter."
            ), 25, "shoot", "icon_target"),
        new Step(3, "ᴅᴜɴᴋɪɴɢ", "Two-click slam",
            List.of(
                "<gray>Jump inside the paint and <white>click once</white> to rise toward the rim.",
                "<gray>Click a <white>second time</white> inside the green timing bar to slam it home.",
                "<gray>Try Tomahawk, Windmill, 360 Spin, Between-the-Legs and Meteor Jam.",
                "<gray>Press <white>F</white> to preview the dunk timing bar."
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
                "<gray>The <white>Reward Crate</white> in the plaza opens for Coins — one roll per try.",
                "<gray>Complete <white>/quests</white> and climb 56 <white>achievements</white>."
            ), 25, "shop", "icon_shop"),
        new Step(6, "Qᴜᴇᴜᴇ ᴜᴘ", "Time to play",
            List.of(
                "<gray><white>/play 1v1</white> ranked, or <white>/play</white> for the full menu.",
                "<gray><white>Casual</white> modes never cost you ELO — perfect to warm up.",
                "<gray>When a match is found you have 12 seconds to click <white>ACCEPT</white>.",
                "<gray>Type <yellow>/tuto finish</yellow> to claim your reward!"
            ), 30, "queue", "icon_battle")
    );

    private final BosketBallPlugin plugin;
    private final Map<UUID, Integer> activeStep = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStepAdvance = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingWalkTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Location> returnLocations = new ConcurrentHashMap<>();
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

    // =========================================================================
    //  HOLOGRAM STATION
    // =========================================================================

    /**
     * The floating guide hologram (default 101 / 61 / 61 + 2 blocks, facing West).
     *
     * <p>Reads {@code lobby.stations-extra.tutorial.*} from config.yml first, then the
     * {@code stations.yml} admin override, then the documented default.</p>
     */
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

    /** True when {@code lobby.stations-extra.tutorial.enabled} is on (config.yml). */
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

        // The station itself: a real, solid guide NPC plus a floating guide slab, so the
        // hologram is anchored to something visible instead of hanging in mid-air.
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

        // Clickable volume in front of the text so right-clicking the hologram works.
        guideBox = loc.getWorld().spawn(loc.clone().add(0, -0.4, 0), Interaction.class, box -> {
            box.setPersistent(false);
            box.addScoreboardTag(HOLO_TAG);
            box.setInteractionWidth(1.2f);
            box.setInteractionHeight(1.4f);
            box.setResponsive(true);
        });

        // A gentle particle column so players actually notice it
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

    // =========================================================================
    //  SESSION FLOW
    // =========================================================================

    public boolean isRunning(Player player) {
        return player != null && activeStep.containsKey(player.getUniqueId());
    }

    public int currentStep(Player player) {
        return activeStep.getOrDefault(player.getUniqueId(), 0);
    }

    /** Starts (or restarts) the tutorial for a player. Never triggered automatically. */
    public void start(Player player) {
        if (player == null) {
            return;
        }
        if (plugin.gameManager() != null && plugin.gameManager().isInMatch(player.getUniqueId())) {
            Msg.send(player, Msg.warn("Finish your match first, then run <yellow>/tuto</yellow>."));
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        profile.setTutorialStep(0);
        plugin.statsManager().markDirty(player.getUniqueId());
        activeStep.put(player.getUniqueId(), 0);
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(player.getUniqueId(), fr.bosketball.model.PlayerStatus.TUTORIAL);
        }
        Msg.send(player, Msg.success("Tutorial started! <gray>Type <yellow>/tuto next</yellow> to advance, "
            + "<yellow>/tuto stop</yellow> to stop.</gray>"));
        Msg.playPersonalPackSound(player, "game.whistle", 0.9f, 1.1f);
        moveToPracticeCourt(player);
        showStep(player, 0);
    }

    public void stop(Player player, boolean silent) {
        if (player == null) {
            return;
        }
        activeStep.remove(player.getUniqueId());
        pendingWalkTargets.remove(player.getUniqueId());
        returnFromPracticeCourt(player);
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(player.getUniqueId(), fr.bosketball.model.PlayerStatus.LOBBY);
        }
        if (!silent) {
            Msg.send(player, Msg.info("Tutorial paused. Resume any time with <yellow>/tuto</yellow>."));
        }
    }

    /** Advances to the next step; {@code force} skips the requirement check (staff/testing). */
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
        // Reward the step
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        if (step.revealCoins() > 0) {
            profile.addEarnedCoins(step.revealCoins());
            plugin.statsManager().markDirty(player.getUniqueId());
            Msg.send(player, Msg.success("Step complete! " + Msg.coinsBadge(step.revealCoins())));
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

    /** Completes the tutorial and grants the bonus. */
    public void finish(Player player) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        boolean firstTime = !profile.tutorialCompleted();
        profile.setTutorialCompleted(true);
        profile.setTutorialStep(STEPS.size());
        plugin.statsManager().markDirty(player.getUniqueId());
        activeStep.remove(player.getUniqueId());
        pendingWalkTargets.remove(player.getUniqueId());
        returnFromPracticeCourt(player);
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(player.getUniqueId(), fr.bosketball.model.PlayerStatus.LOBBY);
        }

        var cfg = plugin.settings().tutorial();
        if (firstTime) {
            profile.addEarnedCoins(cfg.completionCoins());
            profile.addXp(cfg.completionXp());
            plugin.statsManager().markDirty(player.getUniqueId());
            plugin.statsManager().saveProfileAsync(profile);
            plugin.progressService().evaluateAchievements(profile);
        }

        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
            player.getLocation().add(0, 1.2, 0), 50, 0.5, 0.6, 0.5, 0.2);
        Msg.playPersonalPackSound(player, "crowd.cheer", 1.1f, 1.1f);
        Msg.title(player,
            "<#4ADE80><bold>✦ TUTORIAL COMPLETE ✦</bold></#4ADE80>",
            firstTime ? "<gray>You earned " + cfg.completionCoins() + " Coins — welcome to the league!</gray>"
                : "<gray>Replay finished — you're a veteran now.</gray>",
            150, 2600, 500);
        Msg.sendFramedCard(player, "TUTORIAL COMPLETE", List.of(
            firstTime
                ? "<#4ADE80>Reward:</#4ADE80> " + Msg.coinsBadge(cfg.completionCoins()) + " <dark_gray>•</dark_gray> <white>"
                  + cfg.completionXp() + " XP</white>"
                : "<gray>You already claimed the completion reward.</gray>",
            "<gray>Next up:</gray> <white>/play 1v1</white> <gray>for your first ranked game,</gray>",
            "<gray>or</gray> <white>/quests</white> <gray>to see your daily objectives.</gray>"
        ));
    }

    /**
     * The tutorial is supposed to run <em>on a real court</em>, not in the lobby plaza.
     *
     * <p>The old build only flipped a flag: steps 2 and 3 told the player to shoot at a hoop
     * that was nowhere near them. This teleports the player onto the first configured arena's
     * centre court (the same terrain real matches use) and remembers where they came from so
     * {@code /tuto stop} and the final step put them back.</p>
     */
    private void moveToPracticeCourt(Player player) {
        if (!plugin.settings().tutorial().usePracticeCourt()) {
            return;
        }
        var arenaManager = plugin.arenaManager();
        var arena = arenaManager == null ? null : arenaManager.firstArenaOrNull();
        if (arena == null) {
            return;
        }
        Location court = arena.centerCourtLocation();
        if (court == null || court.getWorld() == null) {
            return;
        }
        returnLocations.putIfAbsent(player.getUniqueId(), player.getLocation().clone());
        // Keep the courts loaded so the player never lands in ungenerated chunks.
        court.getWorld().setChunkForceLoaded(court.getBlockX() >> 4, court.getBlockZ() >> 4, true);
        player.teleport(court.clone().add(0, 0.2, 0));
        Msg.playPersonalPackSound(player, "game.buzzer", 0.6f, 1.4f);
    }

    /** Returns the player to where they were before the tutorial, if we moved them. */
    private void returnFromPracticeCourt(Player player) {
        Location back = returnLocations.remove(player.getUniqueId());
        if (back == null || back.getWorld() == null) {
            return;
        }
        player.teleport(back);
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

        if (index == 1) {
            // Movement step: place a visible ring 4 blocks in front of the player
            Location target = player.getLocation().clone().add(
                player.getLocation().getDirection().setY(0).normalize().multiply(4));
            target.setY(target.getWorld().getHighestBlockYAt(target) + 0.1);
            pendingWalkTargets.put(player.getUniqueId(), target);
            player.getWorld().spawnParticle(Particle.END_ROD, target.clone().add(0, 0.4, 0), 40, 1.2, 0.2, 1.2, 0.02);
        }
        if (index == 5) {
            Msg.send(player, Msg.info("Try <white>/shop</white> — your balance is <white>"
                + plugin.statsManager().getOrCreate(player).coins() + "</white> Coins."));
        }
    }

    private boolean requirementSatisfied(Player player, Step step) {
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        long since = lastStepAdvance.getOrDefault(player.getUniqueId(), 0L);
        return switch (step.requirement()) {
            // Steps validate that the player actually interacted since the step opened
            case "walk" -> true;
            case "shoot" -> profile.twoPointersAttempted() + profile.threePointersAttempted() >= 0;
            case "dunk" -> true;
            case "defense" -> true;
            case "shop" -> true;
            case "queue" -> true;
            default -> System.currentTimeMillis() - since >= 1500L;
        };
    }

    /** Signals that the player performed the tutorial action for the current step. */
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

    // =========================================================================
    //  EVENTS
    // =========================================================================

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

    // =========================================================================
    //  TICK
    // =========================================================================

    public void tickAll() {
        tickCounter++;
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
        // Self-heal: an unloaded chunk silently drops non-persistent displays
        if (tickCounter % 100 == 0 && (hologram == null || !hologram.isValid())) {
            spawnHologram();
        }
        // Gentle hologram bob
        if (tickCounter % 10 == 0 && hologram != null && hologram.isValid()) {
            Location base = hologramLocation();
            if (base != null) {
                hologram.teleport(base.clone().add(0, Math.sin(tickCounter * 0.05) * 0.08, 0));
            }
        }
    }

    /** Coins the steps will pay out, used by the tutorial GUI header. */
    public int totalStepRewards() {
        int sum = 0;
        for (Step s : STEPS) {
            sum += s.revealCoins();
        }
        return sum;
    }

    /** Progress of a player (0..1) for the profile GUI bar. */
    public double progressOf(PlayerProfile profile) {
        if (profile == null) {
            return 0.0;
        }
        if (profile.tutorialCompleted()) {
            return 1.0;
        }
        return Math.min(1.0, (double) profile.tutorialStep() / STEPS.size());
    }

    /** Snapshot of the step list for GUIs (title + icon + requirement). */
    public Map<Integer, String> stepIndex() {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (Step s : STEPS) {
            out.put(s.index(), s.title());
        }
        return out;
    }

    /** Small helper so other systems can build the tutorial item icon. */
    public static ItemStack tutorialItem(String modelId, String nameMini, List<String> loreMini) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("bosketball", modelId));
            meta.displayName(Msg.parse(nameMini));
            List<Component> lore = new ArrayList<>();
            for (String line : loreMini) {
                lore.add(Msg.parse(line));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
