package fr.bosketball.ui;

import fr.bosketball.util.Palette;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.game.GameManager;
import fr.bosketball.game.GameModeType;
import fr.bosketball.game.MatchSession;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerStatus;
import fr.bosketball.server.OnboardingCues;
import fr.bosketball.server.OnboardingScript;
import fr.bosketball.tutorial.TutorialFlow;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Viewers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Guided new-player tour — rebuilt from scratch in Round 30.
 *
 * <h2>Why the old one felt broken</h2>
 * The previous implementation piled up timers: every chapter silently "validated itself" after 45 s
 * ({@code autoValidated()} returned {@code true} for gameplay chapters), a second timer advanced on
 * top of that, and a third re-printed the same card. The player never got to do anything: the tour
 * scrolled past, the practice court sometimes failed without a word, and the chat received seven
 * messages per chapter. Replaying also paid the chapter coins again — a free-money loop.
 *
 * <h2>How it works now</h2>
 * <ul>
 *   <li><b>One state machine.</b> A chapter is validated by exactly one thing — the player doing it —
 *       or by an explicit click on "skip". Nothing advances behind the player's back.</li>
 *   <li><b>One card per chapter</b> (a single multi-line chat component), one title, one boss bar,
 *       one action bar. No spam, no duplicate lines.</li>
 *   <li><b>Rewards are paid once per chapter, ever.</b> Replaying the tour is free of exploits.</li>
 *   <li><b>Never stuck, never tricked.</b> After {@link #SOFT_SKIP_MS} the chapter starts helping
 *       (a hint aimed at that exact mechanic); after {@link #SKIP_AFTER_MS} it offers a one-click
 *       "passer" button. Only the two pure "reading" chapters advance on their own.</li>
 *   <li><b>The court is optional.</b> No free arena → the tour runs where the player stands, without
 *       blindness and without a teleport, instead of freezing them for six seconds facing nothing.</li>
 *   <li><b>Cleanup is unconditional.</b> Quit, death, world change, stop, finish and plugin disable
 *       all release the markers, the boss bar, the blindness and the walk speed.</li>
 * </ul>
 */
public final class TutorialManager implements Listener {

    public static final String HOLO_TAG = "bosketball_tutorial_holo";
    public static final String INTRO_TAG = "bosketball_tutorial_intro";
    public static final String MARKER_TAG = "bosketball_tutorial_marker";

    /** Radius (squared) inside which walking counts as "entered the ring". */
    private static final double RING_ENTER_RADIUS_SQ = 2.6 * 2.6;

    /**
     * How long a gameplay chapter waits before it starts helping. Round 30: this is a <em>hint</em>
     * delay, never an automatic validation — the old build advanced alone and felt broken.
     */
    private static final long SOFT_SKIP_MS = 60_000L;

    /** After a second delay the chapter offers an explicit one-click skip. */
    private static final long SKIP_AFTER_MS = SOFT_SKIP_MS * 2L;

    /**
     * Pure reading chapters (no in-world action) move on alone after this, so nobody is stuck.
     * Round 32: 12 s instead of 25 s — a fast reader clicks, a slow one is carried on quickly.
     */
    private static final long CARD_AUTO_MS = 12_000L;
    /** The very first card is shorter to read, so it waits even less. */
    private static final long WELCOME_AUTO_MS = 8_000L;

    private static final int INTRO_TOTAL_TICKS = 110;

    /** Distance under which the lobby guide hologram is kept alive / animated. */
    private static final double HOLOGRAM_RANGE = 96.0;

    /**
     * One chapter of the tour.
     *
     * @param requirement the gameplay action that validates it ({@code welcome}, {@code walk},
     *                    {@code shoot}, {@code dunk}, {@code defense}, {@code shop}, {@code queue},
     *                    {@code finish})
     */
    public record Step(
        int index,
        String title,
        String subtitle,
        List<String> instructions,
        int revealCoins,
        String requirement,
        String iconModel
    ) {

        /** One-line objective shown in the boss bar, the action bar and the GUI. */
        public String objective() {
            return switch (requirement) {
                case "walk" -> "Walk into the golden ring";
                case "shoot" -> "Take a shot and release in the green window";
                case "dunk" -> "Dunk: stand in the paint, face the rim, click once";
                case "defense" -> "Defend: sneak, then left-click to steal";
                case "shop" -> "Open the shop with /shop";
                case "queue" -> "Join a queue with /play";
                default -> "Read the card, then click CONTINUE";
            };
        }

        /** True only for the two chapters that ask nothing but reading. */
        public boolean autoValidated() {
            return TutorialFlow.isReadingRequirement(requirement);
        }

        /** True when the chapter only asks the player to read the card. */
        public boolean reading() {
            return TutorialFlow.isReadingRequirement(requirement);
        }

        /** How long a reading chapter waits before moving on alone. */
        public long autoAdvanceMs() {
            return "welcome".equals(requirement) ? WELCOME_AUTO_MS : CARD_AUTO_MS;
        }

        /** Short help sent when the player has been on the chapter for a while. */
        public String hint() {
            return switch (requirement) {
                case "walk" -> "The gold ring is right in front of you — just walk into it.";
                case "shoot" -> "Hold <#F8FAFC>right-click</#F8FAFC> with the ball, release when the meter hits the green.";
                case "dunk" -> "Get close to the rim, look at it and <#F8FAFC>click once</#F8FAFC>.";
                case "defense" -> "Press <#F8FAFC>sneak</#F8FAFC>, then <#F8FAFC>left-click</#F8FAFC> on the ball carrier.";
                case "shop" -> "Type <#F8FAFC>/shop</#F8FAFC> (or use the shop pill in your hotbar).";
                case "queue" -> "Type <#F8FAFC>/play 1v1</#F8FAFC> to enter a ranked queue.";
                default -> "Click <#F8FAFC>CONTINUE</#F8FAFC> below to go on.";
            };
        }
    }

    private static final List<Step> STEPS = List.of(
        new Step(0, "ᴡᴇʟᴄᴏᴍᴇ ᴛᴏ ʙᴏꜱᴋᴇᴛʙᴀʟʟ", "Let's get you on the court",
            List.of(
                "<#94A3B8>BosketBall is <#F8FAFC>3D competitive basketball</#F8FAFC>, played with real physics.",
                "<#94A3B8>This tour has <#F8FAFC>8 short chapters</#F8FAFC> and pays Coins at every step.",
                "<#94A3B8>You never lose control: <#F8FAFC>/tuto menu</#F8FAFC> opens the guide at any time."
            ), 10, "welcome", "ref_medal_gold"),
        new Step(1, "ᴍᴏᴠᴇ & ʟᴏᴏᴋ", "Step into the gold ring",
            List.of(
                "<#94A3B8>A real <" + Palette.GOLD_SOFT + "><bold>golden ring</bold></" + Palette.GOLD_SOFT + "> is waiting just ahead of you.",
                "<#94A3B8><#F8FAFC>Walk into it</#F8FAFC> — it turns green the instant you do.",
                "<#94A3B8>Sprint, strafe and jump: every input is handled by the engine."
            ), 15, "walk", "ref_calendar_check"),
        new Step(2, "ꜱʜᴏᴏᴛɪɴɢ", "The green release window",
            List.of(
                "<#94A3B8>Hold <#F8FAFC>right-click</#F8FAFC> with the ball to draw a shot.",
                "<#94A3B8>Release when the meter crosses the <" + Palette.SUCCESS + "><bold>green zone</bold></" + Palette.SUCCESS + "> — that's a swish.",
                "<#94A3B8>The marker above the rim shows where to aim."
            ), 20, "shoot", "ref_calendar_check"),
        new Step(3, "ᴅᴜɴᴋɪɴɢ", "One click, one slam",
            List.of(
                "<#94A3B8>Stand in the paint, <#F8FAFC>look at the rim</#F8FAFC> and <#F8FAFC>click once</#F8FAFC>.",
                "<#94A3B8>The slam resolves by itself, then a <#F8FAFC>20 s</#F8FAFC> cooldown.",
                "<#94A3B8>Styles unlock in the shop: Tomahawk, Windmill, 360, Meteor Jam."
            ), 20, "dunk", "icon_dunk"),
        new Step(4, "ᴅᴇꜰᴇɴꜱᴇ", "Clamp your matchup",
            List.of(
                "<#94A3B8><#F8FAFC>Sneak</#F8FAFC> performs a V-Crossover; <#F8FAFC>left-click</#F8FAFC> pokes the ball loose.",
                "<#94A3B8>Time it while your opponent dribbles to force a turnover.",
                "<#94A3B8>Steals and blocks both pay Coins on top of the highlight."
            ), 20, "defense", "icon_defense"),
        new Step(5, "ᴇᴄᴏɴᴏᴍʏ", "Coins & cosmetics",
            List.of(
                "<#94A3B8>Buckets, assists, steals, blocks, wins and MVPs all pay Coins.",
                "<#94A3B8>Spend them in <#F8FAFC>/shop</#F8FAFC>: basketballs, dunk styles and chat tags.",
                "<#94A3B8>Every purchase asks for a <#F8FAFC>confirmation</#F8FAFC> before spending a Coin."
            ), 25, "shop", "ref_shop"),
        new Step(6, "Qᴜᴇᴜᴇ ᴜᴘ", "Time to play",
            List.of(
                "<#94A3B8><#F8FAFC>/play</#F8FAFC> opens the full mode menu; <#F8FAFC>/play 1v1</#F8FAFC> jumps straight in.",
                "<#94A3B8><#F8FAFC>Casual</#F8FAFC> modes never cost you ELO — perfect to warm up.",
                "<#94A3B8>Your ELO moves <#F8FAFC>+15 on a win</#F8FAFC> and <#F8FAFC>−5 on a loss</#F8FAFC>."
            ), 30, "queue", "icon_trophy_gold"),
        new Step(7, "ꜰɪɴᴀʟ ʙᴜᴢᴢᴇʀ", "Welcome to the league",
            List.of(
                "<#94A3B8>You know the ring, the shot, the dunk, the steal and the shop.",
                "<#94A3B8>Claim your <#F8FAFC>welcome bonus</#F8FAFC> and step on the court."
            ), 0, "finish", "ref_cup_gold")
    );

    private static final Map<Integer, String> RANK_LADDER = new LinkedHashMap<>();

    static {
        RANK_LADDER.put(0, "ROOKIE");
        RANK_LADDER.put(1050, "HOOPER");
        RANK_LADDER.put(1200, "PRO");
        RANK_LADDER.put(1400, "ALL-STAR");
        RANK_LADDER.put(1650, "SUPERSTAR");
        RANK_LADDER.put(1900, "LEGEND");
        RANK_LADDER.put(2200, "MVP");
    }

    private final BosketBallPlugin plugin;

    // ---------------------------------------------------------------- per-player state
    private final Map<UUID, Integer> activeStep = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stepValidatedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStepAdvance = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHintAt = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hintLevel = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pendingWalkTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Location> returnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> introTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Arena> tutorialArenas = new ConcurrentHashMap<>();
    private final Map<UUID, List<org.bukkit.entity.Entity>> playerMarkers = new ConcurrentHashMap<>();
    /** One entry per gold-ring piece: each keeps its own angle/scale, so the animation can spin it. */
    private final Map<UUID, List<RingPart>> ringParts = new ConcurrentHashMap<>();
    /** In-session ledger: the completion bonus is claimed once, even across a replay or a reconnect. */
    private final java.util.Set<UUID> completionClaims = ConcurrentHashMap.newKeySet();

    /**
     * A single animated piece of the guidance ring.
     *
     * <p>Round 32: the old ring re-applied one shared transformation to every slice on every frame,
     * which wiped the tangential yaw — the "circle" collapsed into a stack of parallel bars. Each
     * piece now carries its own angle, radius and scale, and the animation only animates those.
     */
    private record RingPart(
        BlockDisplay display,
        double angle,
        double radius,
        float scaleX,
        float scaleY,
        float scaleZ,
        double lift,
        boolean accent
    ) {
    }
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, List<org.bukkit.entity.Entity>> introProps = new ConcurrentHashMap<>();

    private TextDisplay hologram;
    private Interaction guideBox;
    private Location hologramCache;
    private long hologramCachedAt;
    private long lastHologramSweep;
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

    /** The ELO brackets in ascending order, used by the guide and the brand sheet. */
    public static Map<Integer, String> rankLadder() {
        return java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(RANK_LADDER));
    }

    // ------------------------------------------------------------------ lobby hologram

    public Location hologramLocation() {
        long now = System.currentTimeMillis();
        if (hologramCache != null && now - hologramCachedAt < 5_000L) {
            return hologramCache.clone();
        }
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
        Location loc = new Location(world, x, y + spec.lift(), z,
            fr.bosketball.lobby.RewardCrateStation.yawFor(yml.getString("tutorial.facing", spec.facing())), 0f);
        hologramCache = loc.clone();
        hologramCachedAt = now;
        return loc;
    }

    /** Drops the cached guide position (called on reload / station edit). */
    public void invalidateHologramCache() {
        hologramCache = null;
        hologramCachedAt = 0L;
    }

    public boolean hologramEnabled() {
        return plugin.settings().lobby().stationsExtra().tutorial().usable();
    }

    public void spawnHologram() {
        // Round 31: a reload or a station move must be visible immediately, not after the 5 s cache.
        invalidateHologramCache();
        forgetHologram();
        if (!plugin.settings().tutorial().enabled() || !plugin.settings().tutorial().hologramEnabled()
            || !hologramEnabled()) {
            return;
        }
        Location loc = hologramLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        hologram = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HOLO_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.5f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setSeeThrough(false);
            d.setLineWidth(240);
            d.text(Msg.parse(
                Msg.gradBold(Msg.GRAD_BRAND, "▪ ɴᴇᴡ ᴘʟᴀʏᴇʀ ɢᴜɪᴅᴇ ▪") + "\n"
                    + "<#F8FAFC>Right-click the guide</#F8FAFC> <#64748B>·</#64748B> <#F8FAFC>/tuto</#F8FAFC>\n"
                    + "<#94A3B8>" + STEPS.size() + " chapters</#94A3B8> <#64748B>·</#64748B> "
                    + "<" + Palette.SUCCESS + ">" + Msg.coinsBadge(totalStepRewardsStatic())
                    + "</" + Palette.SUCCESS + "> <#94A3B8>+ bonus</#94A3B8>"
            ));
        });

        guideBox = loc.getWorld().spawn(loc.clone().add(0, -0.4, 0), Interaction.class, box -> {
            box.setPersistent(false);
            box.addScoreboardTag(HOLO_TAG);
            box.setInteractionWidth(1.2f);
            box.setInteractionHeight(1.4f);
            box.setResponsive(true);
        });

        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, -0.6, 0), 10, 0.25, 0.4, 0.25, 0.01);
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
        // Sweeping every entity of every world is expensive: only do it when we really lost the
        // reference (reload, restart of the guide), not on every respawn attempt.
        lastHologramSweep = System.currentTimeMillis();
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(HOLO_TAG)) {
                    e.remove();
                }
            }
        }
    }

    /** Drops a stale guide hologram without a full world sweep. */
    private void forgetHologram() {
        if (hologram != null) {
            hologram.remove();
            hologram = null;
        }
        if (guideBox != null) {
            guideBox.remove();
            guideBox = null;
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
        return player == null ? 0 : activeStep.getOrDefault(player.getUniqueId(), 0);
    }

    public boolean currentStepValidated(Player player) {
        return player != null && stepValidatedAt.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------ lifecycle

    /** Starts (or resumes) the guided tour. A finished player is simply sent to the guide menu. */
    public void start(Player player) {
        if (player == null) {
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        if (profile.tutorialCompleted()) {
            Msg.send(player, Msg.info("You already finished the tour. <#F8FAFC>/tuto restart</#F8FAFC> replays it, "
                + "<#F8FAFC>/tuto menu</#F8FAFC> shows the guide."));
            return;
        }
        if (isRunning(player)) {
            // Typing /tuto twice must not restart the intro or re-print the whole chapter.
            Msg.send(player, Msg.info("The tour is already running — <#F8FAFC>/tuto show</#F8FAFC> re-reads "
                + "the chapter, <#F8FAFC>/tuto stop</#F8FAFC> pauses it."));
            showCurrentStep(player);
            return;
        }
        int resume = TutorialFlow.resumeIndex(profile.tutorialCompleted(), profile.tutorialStep(), STEPS.size());
        startAt(player, resume, resume == 0);
    }

    /** Full replay from chapter 1, intro included. Chapter rewards are never paid twice. */
    public void restart(Player player) {
        if (player == null) {
            return;
        }
        // The completion flag and the paid-chapter ledger stay: a replay is free, never a payday.
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        profile.setTutorialStep(0);
        plugin.statsManager().markDirty(player.getUniqueId());
        startAt(player, 0, true);
    }

    private void startAt(Player player, int step, boolean withIntro) {
        if (player == null) {
            return;
        }
        GameManager gameManager = plugin.gameManager();
        boolean inMatch = gameManager != null
            && gameManager.isActivelyPlayingCompetitiveMatch(player.getUniqueId());
        boolean queued = gameManager != null && gameManager.isQueued(player.getUniqueId());
        switch (TutorialFlow.startVerdict(plugin.settings().tutorial().enabled(), isRunning(player), inMatch, queued)) {
            case DISABLED -> {
                Msg.send(player, Msg.warn("The tutorial is disabled on this server."));
                return;
            }
            case IN_MATCH -> {
                Msg.send(player, Msg.warn("Finish your match first, then run <#FDE047>/tuto</#FDE047>."));
                return;
            }
            case QUEUED -> {
                Msg.send(player, Msg.warn("You are in a queue right now — <#F8FAFC>/leave</#F8FAFC> first, "
                    + "then <#FDE047>/tuto</#FDE047>."));
                return;
            }
            default -> {
            }
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        int safe = Math.clamp(step, 0, STEPS.size() - 1);
        profile.setTutorialStep(safe);
        plugin.statsManager().markDirty(player.getUniqueId());

        UUID id = player.getUniqueId();
        activeStep.put(id, safe);
        stepValidatedAt.remove(id);
        lastStepAdvance.put(id, System.currentTimeMillis());
        hintLevel.remove(id);
        returnLocations.putIfAbsent(id, player.getLocation().clone());
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(id, PlayerStatus.TUTORIAL);
        }
        showBossBar(player);
        if (withIntro) {
            playIntro(player);
            return;
        }
        if (safe > 0) {
            Msg.send(player, Msg.info("Resuming at chapter <#F8FAFC>" + (safe + 1) + "/" + STEPS.size()
                + "</#F8FAFC> — <#94A3B8>/tuto restart</#94A3B8> to replay from chapter 1."));
        }
        showStep(player, safe);
    }

    /**
     * Quit path: releases every per-player structure without touching the player (they are gone).
     */
    public void clearPlayer(Player player) {
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
        lastHintAt.remove(id);
        hintLevel.remove(id);
        clearMarkers(id);
        hideBossBar(player);
        tutorialArenas.remove(id);
        cleanupIntroProps(id);
    }

    /** Pauses the tour, restoring every side effect (walk speed, blindness, markers, HUD). */
    public void stop(Player player, boolean silent) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        boolean wasIntro = introTicks.remove(id) != null;
        activeStep.remove(id);
        pendingWalkTargets.remove(id);
        // The practice court exit reads the origin, so it must run before the origin is dropped.
        exitPracticeCourt(player);
        returnLocations.remove(id);
        lastStepAdvance.remove(id);
        stepValidatedAt.remove(id);
        lastHintAt.remove(id);
        hintLevel.remove(id);
        clearMarkers(id);
        hideBossBar(player);
        cleanupIntroProps(id);
        player.setWalkSpeed(0.2f);
        if (wasIntro) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        }
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(id, PlayerStatus.LOBBY);
        }
        if (!silent) {
            Msg.send(player, Msg.info("Tour paused — <#F8FAFC>/tuto</#F8FAFC> resumes it, "
                + "<#F8FAFC>/tuto restart</#F8FAFC> replays it."));
        }
    }

    /**
     * Manual advance (/tuto next). Without {@code force}, the chapter must have been validated by the
     * matching action; {@code force} skips it without paying the chapter reward.
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
        Step active = STEPS.get(Math.min(current, STEPS.size() - 1));
        boolean validated = stepValidatedAt.containsKey(player.getUniqueId());
        // Round 32: a reading chapter is *done* the moment the player turns the page. Gating it on
        // the 8/12 s timer is what kept fast readers waiting in front of a card they had finished.
        if (!force && !validated && !active.reading()) {
            long now = System.currentTimeMillis();
            long last = lastHintAt.getOrDefault(player.getUniqueId(), 0L);
            if (now - last > 2500L) {
                lastHintAt.put(player.getUniqueId(), now);
                Msg.send(player, Msg.warn("<#F8FAFC>" + active.objective() + "</#F8FAFC>"));
                Msg.send(player, Msg.info("<#94A3B8>" + active.hint() + "</#94A3B8> <#64748B>·</#64748B> "
                    + "<#FDE047>/tuto skip</#FDE047> <#94A3B8>to pass it.</#94A3B8>"));
            }
            return;
        }
        // A forced skip never pays; a validated chapter and a reading chapter do.
        advanceStep(player, !force && (validated || active.reading()));
    }

    /**
     * Round 32: turns a reading page on any click in the world (or a hit on the card's button).
     *
     * @return true when a reading chapter was actually turned, so the caller cancels the click
     */
    public boolean advanceReadingChapter(Player player) {
        if (player == null || !isRunning(player) || isInIntro(player)) {
            return false;
        }
        if (plugin.gameManager() != null
            && plugin.gameManager().isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            return false;
        }
        Integer current = activeStep.get(player.getUniqueId());
        if (current == null) {
            return false;
        }
        Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
        if (!step.reading()) {
            return false;
        }
        advance(player, false);
        return true;
    }

    /** Jump to a chapter from the guide menu. */
    public void gotoStep(Player player, int index) {
        if (player == null) {
            return;
        }
        if (!isRunning(player)) {
            startAt(player, index, false);
            return;
        }
        if (plugin.gameManager() != null
            && plugin.gameManager().isActivelyPlayingCompetitiveMatch(player.getUniqueId())) {
            stop(player, true);
            Msg.send(player, Msg.warn("You are in a match — the tour resumes after the game."));
            return;
        }
        int target = Math.clamp(index, 0, STEPS.size() - 1);
        // Jumping to a chapter while the cinematic intro is playing must cancel the intro, otherwise
        // the intro would teleport the player and re-print the card a few seconds later.
        if (introTicks.remove(player.getUniqueId()) != null) {
            cleanupIntroProps(player.getUniqueId());
            restoreControl(player);
        }
        activeStep.put(player.getUniqueId(), target);
        stepValidatedAt.remove(player.getUniqueId());
        lastStepAdvance.put(player.getUniqueId(), System.currentTimeMillis());
        hintLevel.remove(player.getUniqueId());
        showBossBar(player);
        showStep(player, target);
    }

    /**
     * Moves to the next chapter.
     *
     * @param payReward true when the chapter was really completed; a forced skip pays nothing and
     *                  a chapter is never paid twice, even across replays
     */
    private void advanceStep(Player player, boolean payReward) {
        UUID id = player.getUniqueId();
        Integer current = activeStep.get(id);
        if (current == null) {
            return;
        }
        Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);

        if (TutorialFlow.paysReward(current, profile.tutorialRewardedStep(), step.revealCoins(), payReward)) {
            profile.setTutorialRewardedStep(current);
            profile.addEarnedCoins(step.revealCoins());
            plugin.statsManager().markDirty(id);
            Msg.send(player, Msg.success("Chapter complete " + Msg.coinsBadge(step.revealCoins())));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.5f);
        } else {
            Msg.send(player, Msg.info("<#94A3B8>Chapter passed.</#94A3B8>"));
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.3f);
        stepValidatedAt.remove(id);
        lastHintAt.remove(id);
        hintLevel.remove(id);
        clearMarkers(id);

        int next = TutorialFlow.nextStep(current, STEPS.size());
        profile.setTutorialStep(next);
        plugin.statsManager().markDirty(id);
        if (next >= STEPS.size()) {
            finish(player);
            return;
        }
        // A player who jumped into a match must not keep a tutorial HUD over the court: the tour
        // ends (last chapters) or pauses (progress saved), never draws over the game.
        if (plugin.gameManager() != null && plugin.gameManager().isActivelyPlayingCompetitiveMatch(id)) {
            if (TutorialFlow.finishesOnMatchStart(next, STEPS.size())) {
                finish(player, false);
            } else {
                stop(player, true);
            }
            return;
        }
        activeStep.put(id, next);
        showStep(player, next);
    }

    /** Ends the tour, pays the completion bonus once and drops the player back at spawn. */
    public void finish(Player player) {
        finish(player, true);
    }

    /**
     * Ends the tour.
     *
     * @param teleportHome false when the tour ends because a match is starting: teleporting the
     *                     player back to spawn at that exact moment would break the match start
     */
    public void finish(Player player, boolean teleportHome) {
        if (player == null) {
            return;
        }
        if (!isRunning(player) && !isInIntro(player)) {
            Msg.send(player, Msg.warn("No tour is running — <#F8FAFC>/tuto start</#F8FAFC> to begin one."));
            return;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        UUID id = player.getUniqueId();
        // Two ledgers, one payout: the persisted flag survives a restart, the in-session set
        // survives two finish() calls in the same tick. Replaying never resets either one.
        boolean claim = TutorialFlow.paysCompletionBonus(
            profile.tutorialCompleted(), completionClaims.contains(id));
        if (claim) {
            claim = completionClaims.add(id);
        }
        final boolean firstTime = claim;
        profile.setTutorialCompleted(true);
        profile.setTutorialStep(STEPS.size());
        plugin.statsManager().markDirty(id);
        OnboardingCues.noteCard(id);
        activeStep.remove(id);
        pendingWalkTargets.remove(id);
        stepValidatedAt.remove(id);
        introTicks.remove(id);
        hintLevel.remove(id);
        clearMarkers(id);
        hideBossBar(player);
        player.setWalkSpeed(0.2f);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);

        var cfg = plugin.settings().tutorial();
        if (firstTime) {
            profile.addEarnedCoins(cfg.completionCoins());
            profile.addXp(cfg.completionXp());
            plugin.statsManager().markDirty(player.getUniqueId());
            plugin.statsManager().saveProfileAsync(profile);
            if (plugin.progressService() != null) {
                plugin.progressService().evaluateAchievements(profile);
            }
        }
        if (teleportHome) {
            returnLocations.remove(player.getUniqueId());   // skip the origin hop, spawn is next
            exitPracticeCourt(player);
            safe("spawn", () -> returnToSpawn(player));
            safe("outro", () -> playOutro(player, firstTime ? cfg.completionCoins() : 0));
        } else {
            tutorialArenas.remove(player.getUniqueId());
            safe("outro", () -> playOutro(player, firstTime ? cfg.completionCoins() : 0));
        }
        if (plugin.socialService() != null) {
            plugin.socialService().setStatus(player.getUniqueId(), PlayerStatus.LOBBY);
        }

        Msg.sendFramedCard(player, "TUTORIAL COMPLETE", List.of(
            firstTime
                ? "<" + Palette.SUCCESS + ">Reward:</" + Palette.SUCCESS + "> " + Msg.coinsBadge(cfg.completionCoins()) + " <#64748B>·</#64748B> <#F8FAFC>"
                  + cfg.completionXp() + " XP</#F8FAFC>"
                : "<#94A3B8>You already claimed the completion reward.</#94A3B8>",
            "<#94A3B8>Next up:</#94A3B8> <#F8FAFC>/play 1v1</#F8FAFC> <#94A3B8>for your first ranked game.</#94A3B8>",
            "<#94A3B8>Daily objectives:</#94A3B8> <#F8FAFC>/quests</#F8FAFC> <#64748B>·</#64748B> "
                + "<#94A3B8>Guide:</#94A3B8> <#F8FAFC>/tuto menu</#F8FAFC>",
            "<#94A3B8>Reporting:</#94A3B8> " + fr.bosketball.moderation.ReportGate.discoverLine()
        ));
    }

    // ------------------------------------------------------------------ cinematic intro

    private void playIntro(Player player) {
        UUID id = player.getUniqueId();
        returnLocations.put(id, player.getLocation().clone());

        Location court = plugin.settings().tutorial().usePracticeCourt() ? findCourtLocation() : null;
        if (court == null) {
            // Round 30: no free court means no freeze. The tour simply starts where the player is.
            showStep(player, currentStep(player));
            return;
        }

        introTicks.put(id, 0);
        player.setWalkSpeed(0.0f);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.BLINDNESS, 110, 1, false, false, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SLOWNESS, 110, 3, false, false, false));
        Msg.title(player, "<black> </black>", "<#94A3B8>Entering the arena…</#94A3B8>", 0, 900, 150);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.7f, 0.6f);
        player.teleportAsync(court);

        // Hard safety net: whatever happens to the intro ticker, control comes back after 7 s.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (introTicks.containsKey(id)) {
                introTicks.remove(id);
                showStep(player, currentStep(player));
            }
            restoreControl(player);
        }, 140L);
    }

    /** Gives the player back their movement and their sight — always safe to call. */
    private void restoreControl(Player player) {
        player.setWalkSpeed(0.2f);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
    }

    /** Read-only: never reserves an arena, never queues a paste. Used by the diagnostics. */
    public boolean courtLikelyAvailable() {
        if (plugin.gameManager() == null) {
            return false;
        }
        if (plugin.schematicCourtPool() != null && plugin.gameManager().instancedCourtsRequested()) {
            return true;
        }
        if (plugin.practicePool() != null) {
            return true;
        }
        return plugin.arenaManager() != null && !plugin.arenaManager().allArenas().isEmpty();
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
                centre.getWorld().spawnParticle(Particle.PORTAL, centre.clone().add(0, 1.2, 0), 60, 1.4, 1.6, 1.4, 0.6);
                centre.getWorld().playSound(centre, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 0.7f);
            }
            case 12 -> {
                spawnSwarmRing(player, centre);
                player.playSound(centre, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.15f);
            }
            case 45 -> player.playSound(centre, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.55f, 1.4f);
            case 70 -> {
                Msg.title(player,
                    Msg.gradBold(Msg.GRAD_BRAND, "ʙᴏꜱᴋᴇᴛʙᴀʟʟ"),
                    "<#F8FAFC>3D competitive basketball</#F8FAFC>",
                    150, 1200, 250);
                player.playSound(centre, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
                centre.getWorld().spawnParticle(Particle.FIREWORK, centre.clone().add(0, 1.4, 0), 40, 0.9, 1.1, 0.9, 0.15);
            }
            case 96 -> {
                restoreControl(player);
                cleanupIntroProps(player.getUniqueId());
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
            animateSwarmRing(player, centre, (96 - tick) / 84.0);
        }
    }

    private void spawnSwarmRing(Player player, Location centre) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        cleanupIntroProps(player.getUniqueId());
        List<org.bukkit.entity.Entity> props = introProps.computeIfAbsent(
            player.getUniqueId(), k -> new ArrayList<>());
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0 / 12.0) * i;
            Location spawn = centre.clone().add(Math.cos(angle) * 4.2, 0.4 + (i % 3) * 0.5, Math.sin(angle) * 4.2);
            Material token = switch (i % 4) {
                case 0 -> Material.GOLD_INGOT;
                case 1 -> Material.ORANGE_DYE;
                case 2 -> Material.WHITE_WOOL;
                default -> Material.AMETHYST_SHARD;
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
            props.add(display);
        }
    }

    private void animateSwarmRing(Player player, Location centre, double closeness) {
        List<org.bukkit.entity.Entity> props = introProps.getOrDefault(player.getUniqueId(), List.of());
        int index = 0;
        int total = props.size();
        float spin = (float) (tickCounter * 0.16);
        for (org.bukkit.entity.Entity entity : props) {
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
        if (tickCounter % 10 == 0) {
            centre.getWorld().spawnParticle(Particle.END_ROD, centre.clone().add(0, 1.2, 0), 3,
                2.2 * closeness + 0.4, 0.7, 2.2 * closeness + 0.4, 0.01);
        }
    }

    /** Removes only this player's intro props: ten newcomers at once must not fight over them. */
    private void cleanupIntroProps(UUID id) {
        List<org.bukkit.entity.Entity> props = introProps.remove(id);
        if (props == null) {
            return;
        }
        for (org.bukkit.entity.Entity entity : props) {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        props.clear();
    }

    private void prepareLiveCourt(Player player) {
        if (plugin.gameManager() == null || !plugin.settings().tutorial().usePracticeCourt()) {
            return;
        }
        GameManager gm = plugin.gameManager();
        MatchSession existing = gm.getSessionOf(player.getUniqueId());
        if (existing != null && existing.mode() == GameModeType.PRACTICE) {
            if (existing.arena() != null) {
                tutorialArenas.put(player.getUniqueId(), existing.arena());
            }
            return;
        }
        if (existing != null) {
            existing.removePlayer(player, true);
        }
        Location before = returnLocations.getOrDefault(player.getUniqueId(), player.getLocation().clone());
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
                Msg.send(player, Msg.warn("The practice court is busy — the tour continues where you are."));
            }
        }, 5L);
    }

    private void exitPracticeCourt(Player player) {
        tutorialArenas.remove(player.getUniqueId());
        if (plugin.gameManager() != null) {
            MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
            if (session != null && session.mode() == GameModeType.PRACTICE) {
                session.removePlayer(player, true);
            }
        }
        returnFromOrigin(player);
    }

    /** Runs one unit of rendering work; a single failure can never kill the tick task. */
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
        if (player == null) {
            return;
        }
        if (!plugin.settings().tutorial().enabled() || !plugin.settings().tutorial().forceOnFirstJoin()) {
            // A server with the tour switched off still owes the newcomer one next action. The
            // director handles the long run; this cue covers the first seconds after the card.
            OnboardingCues.cue(player, OnboardingCues.railLine(
                OnboardingScript.beat(OnboardingScript.Stage.FIRST_SHOT),
                OnboardingScript.nextAction(OnboardingScript.Stage.FIRST_SHOT, true, false)), 0L);
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
                && (plugin.gameManager().isActivelyPlayingCompetitiveMatch(player.getUniqueId())
                    || plugin.gameManager().isQueued(player.getUniqueId()))) {
                return;
            }
            Msg.send(player, Msg.info("Welcome! Starting the <#F8FAFC>new player guide</#F8FAFC> — "
                + "<#94A3B8>/tuto menu</#94A3B8> to look around, <#94A3B8>/tuto stop</#94A3B8> to pause."));
            start(player);
        }, 60L);
    }

    /** Everything the staff needs to understand why a tour looks empty in game. */
    public List<String> diagnostics(Player player) {
        var cfg = plugin.settings().tutorial();
        List<String> out = new ArrayList<>();
        out.add("<#94A3B8>enabled:</#94A3B8> <#F8FAFC>" + cfg.enabled() + "</#F8FAFC> <#64748B>·</#64748B> "
            + "<#94A3B8>force-on-join:</#94A3B8> <#F8FAFC>" + cfg.forceOnFirstJoin() + "</#F8FAFC>"
            + " <#64748B>·</#64748B> <#94A3B8>practice-court:</#94A3B8> <#F8FAFC>" + cfg.usePracticeCourt() + "</#F8FAFC>");
        if (player == null) {
            return out;
        }
        UUID id = player.getUniqueId();
        out.add("<#94A3B8>running:</#94A3B8> <#F8FAFC>" + isRunning(player) + "</#F8FAFC> <#64748B>·</#64748B> "
            + "<#94A3B8>chapter:</#94A3B8> <#F8FAFC>" + (currentStep(player) + 1) + "/" + STEPS.size() + "</#F8FAFC>"
            + " <#64748B>·</#64748B> <#94A3B8>validated:</#94A3B8> <#F8FAFC>" + currentStepValidated(player) + "</#F8FAFC>");
        out.add("<#94A3B8>hologram:</#94A3B8> <#F8FAFC>" + (hologram != null && hologram.isValid()) + "</#F8FAFC>"
            + " <#64748B>·</#64748B> <#94A3B8>markers:</#94A3B8> <#F8FAFC>"
            + playerMarkers.getOrDefault(id, List.of()).size() + "</#F8FAFC>"
            + " <#64748B>·</#64748B> <#94A3B8>bossbar:</#94A3B8> <#F8FAFC>" + bossBars.containsKey(id) + "</#F8FAFC>");
        Location spawn = spawnTarget();
        out.add("<#94A3B8>spawn:</#94A3B8> <#F8FAFC>" + (spawn == null ? "none"
            : spawn.getWorld().getName() + " " + spawn.getBlockX() + " " + spawn.getBlockY() + " "
                + spawn.getBlockZ()) + "</#F8FAFC>");
        // Read-only probe: the diagnostics must never reserve a court or queue a schematic paste.
        out.add("<#94A3B8>court likely available:</#94A3B8> <#F8FAFC>" + courtLikelyAvailable() + "</#F8FAFC>"
            + " <#64748B>·</#64748B> <#94A3B8>arena bound:</#94A3B8> <#F8FAFC>"
            + tutorialArenas.containsKey(id) + "</#F8FAFC>");
        long startedMs = lastStepAdvance.getOrDefault(id, 0L);
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        out.add("<#94A3B8>ledger:</#94A3B8> <#F8FAFC>rewarded up to chapter " + (profile.tutorialRewardedStep() + 1)
            + "</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>completed:</#94A3B8> <#F8FAFC>"
            + profile.tutorialCompleted() + "</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>saved step:</#94A3B8> <#F8FAFC>"
            + profile.tutorialStep() + "</#F8FAFC>");
        out.add("<#94A3B8>on this chapter for:</#94A3B8> <#F8FAFC>"
            + (startedMs == 0 ? 0 : (System.currentTimeMillis() - startedMs) / 1000) + " s</#F8FAFC>"
            + " <#64748B>·</#64748B> <#94A3B8>hints sent:</#94A3B8> <#F8FAFC>"
            + hintLevel.getOrDefault(id, 0) + "</#F8FAFC>");
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
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, centre.clone().add(0, 1.2, 0), 80, 0.7, 1.0, 0.7, 0.25);
            world.spawnParticle(Particle.FIREWORK, centre.clone().add(0, 1.6, 0), 60, 1.1, 1.2, 1.1, 0.18);
            world.playSound(centre, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.2f, 1.0f);
            world.playSound(centre, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
        }
        Msg.playPersonalPackSound(player, "crowd.cheer", 1.2f, 1.1f);
        Msg.title(player,
            Msg.gradBold(Msg.GRAD_MINT, "▪ ᴛᴜᴛᴏʀɪᴀʟ ᴄᴏᴍᴘʟᴇᴛᴇ ▪"),
            coins > 0 ? "<#94A3B8>+<#F8FAFC>" + coins + " Coins</#F8FAFC> welcome bonus</#94A3B8>"
                : "<#94A3B8>Replay finished — you're a veteran now.</#94A3B8>",
            150, 1900, 350);
    }

    // ------------------------------------------------------------------ step presentation

    private void showStep(Player player, int index) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        int safe = Math.clamp(index, 0, STEPS.size() - 1);
        Step step = STEPS.get(safe);
        activeStep.put(id, safe);
        stepValidatedAt.remove(id);
        lastStepAdvance.put(id, System.currentTimeMillis());
        lastHintAt.remove(id);
        hintLevel.remove(id);
        clearMarkers(id);
        restoreControl(player);

        Msg.playPersonalPackSound(player, "ui.click", 0.8f, 1.35f);
        Msg.title(player,
            Msg.gradBold(Msg.GRAD_GOLD, step.title()),
            "<#94A3B8>" + step.subtitle() + "</#94A3B8>", 150, 1100, 250);

        player.sendMessage(buildChapterCard(step, safe));
        OnboardingCues.noteCard(id);   // one card on screen at a time: the director yields to chapters
        safe("decorate", () -> decorateStep(player, step));
        safe("hud", () -> updateHud(player));
    }

    /**
     * The whole chapter as a single chat component: divider header, title, instructions, reward and
     * the clickable row. One message instead of the seven the old build sent.
     */
    private Component buildChapterCard(Step step, int index) {
        TextComponent.Builder card = Component.text();
        card.append(Glyphs.composeDividerHeader(Glyphs.smallCapsLabels(
            "NEW PLAYER GUIDE   " + (index + 1) + "/" + STEPS.size())));
        card.append(Component.newline());

        card.append(Msg.parse(Msg.gradBold(Msg.GRAD_GOLD, step.title())));
        card.append(Msg.parse(" <#64748B>—</#64748B> <#94A3B8>" + step.subtitle() + "</#94A3B8>"));
        card.append(Component.newline());

        card.append(Msg.parse("<" + Palette.GOLD_SOFT + ">▸</" + Palette.GOLD_SOFT + "> <#F8FAFC>" + step.objective() + "</#F8FAFC>"));
        card.append(Component.newline());
        for (String line : step.instructions()) {
            card.append(Msg.parse("<" + Palette.DIM + ">·</" + Palette.DIM + "> " + line));
            card.append(Component.newline());
        }
        if (step.revealCoins() > 0) {
            card.append(Msg.parse("<#64748B>Reward on completion:</#64748B> " + Msg.coinsBadge(step.revealCoins())));
            card.append(Component.newline());
        }

        card.append(Component.text("  "));
        card.append(button("▸ CONTINUE", "/tuto next",
            "Turn the page now — a click anywhere also works", 0x4ADE80));
        card.append(Component.text("   "));
        card.append(button("◆ GUIDE", "/tuto menu", "Open the chapter list", 0xFFD43B));
        card.append(Component.text("   "));
        card.append(button("✖ PAUSE", "/tuto stop", "Pause the tour (progress is saved)", 0xF87171));
        return card.build();
    }

    private static Component button(String label, String command, String hover, int rgb) {
        return Component.text(label, TextColor.color(rgb))
            .decoration(TextDecoration.BOLD, true)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    /** Spawns the in-world objective markers for a chapter. */
    private void decorateStep(Player player, Step step) {
        UUID id = player.getUniqueId();
        switch (step.requirement()) {
            case "walk" -> {
                Location ring = ringLocationAhead(player, 4.5);
                if (ring == null) {
                    ring = player.getLocation().clone();
                }
                pendingWalkTargets.put(id, ring.clone());
                spawnGoldRing(player, ring, "<" + Palette.GOLD_SOFT + "><bold>CHAPTER " + (step.index() + 1) + "/" + STEPS.size()
                    + "</bold></" + Palette.GOLD_SOFT + ">", "<#F8FAFC>Walk into the ring</#F8FAFC>");
                player.playSound(ring, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
            }
            case "shoot" -> {
                Location spot = ringLocationAhead(player, 3.0);
                if (spot == null) {
                    spot = player.getLocation().clone();
                }
                spawnGoldRing(player, spot, "<" + Palette.SUCCESS + "><bold>SHOOT FROM HERE</bold></" + Palette.SUCCESS + ">",
                    "<#94A3B8>Hold right-click, release in the green</#94A3B8>");
                pointAtRim(player, "<" + Palette.SUCCESS + "><bold>SHOOT HERE</bold></" + Palette.SUCCESS + ">");
            }
            case "dunk" -> pointAtRim(player, "<" + Palette.WARN + "><bold>DUNK HERE</bold></" + Palette.WARN + ">");
            case "defense" -> spawnBoard(player, player.getLocation().clone().add(0, 2.3, 0),
                "<" + Palette.ICE + "><bold>DEFENSE</bold></" + Palette.ICE + ">", "<#F8FAFC>Sneak, then left-click</#F8FAFC>");
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
     * The guidance ring: a real circle of gold slices (28 outer, 16 inner), four diamond accents,
     * a slow spin, a breathing pulse and an orbiting particle halo. Pure display entities — the
     * world is never modified.
     */
    private void spawnGoldRing(Player player, Location centre, String title, String hint) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        UUID id = player.getUniqueId();
        List<org.bukkit.entity.Entity> markers = playerMarkers.computeIfAbsent(id, k -> new ArrayList<>());
        List<RingPart> parts = new ArrayList<>();
        Location anchor = centre.clone().add(0, 0.06, 0);

        // Outer ring: 28 slices, each turned tangentially so the seams meet and it reads as one
        // clean circle from any distance.
        spawnRingArc(world, anchor, markers, parts, 28, 1.80, 0.46f, 0.035f, 0.11f, 0.0, goldSlice(), false);
        // Inner track: thinner and one pixel lower, so the ring has depth instead of being a line.
        spawnRingArc(world, anchor, markers, parts, 16, 1.30, 0.18f, 0.022f, 0.05f, 0.02, emeraldSlice(), false);
        // Four diamond accents at the cardinal points, pulsing, so the eye catches the goal.
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI / 2.0) * i;
            parts.add(spawnRingPiece(world, anchor, markers, angle, 1.80, 0.20f, 0.20f, 0.20f, 0.05, goldSlice(), true));
        }

        spawnBoard(player, centre.clone().add(0, 2.15, 0), title, hint);
        ringParts.put(id, parts);
        world.spawnParticle(Particle.END_ROD, anchor.clone().add(0, 0.25, 0), 18, 1.4, 0.15, 1.4, 0.01);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, anchor.clone().add(0, 0.2, 0), 12, 1.1, 0.1, 1.1, 0.02);
        world.playSound(centre, Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.7f);
        world.playSound(centre, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.9f);
    }

    /** Adds one ring made of `slices` tangential pieces. */
    private void spawnRingArc(
        World world, Location anchor, List<org.bukkit.entity.Entity> markers, List<RingPart> parts,
        int slices, double radius, float sx, float sy, float sz, double lift, BlockData block, boolean accent
    ) {
        for (int i = 0; i < slices; i++) {
            double angle = (Math.PI * 2.0 / slices) * i;
            parts.add(spawnRingPiece(world, anchor, markers, angle, radius, sx, sy, sz, lift, block, accent));
        }
    }

    private RingPart spawnRingPiece(
        World world, Location anchor, List<org.bukkit.entity.Entity> markers,
        double angle, double radius, float sx, float sy, float sz, double lift, BlockData block, boolean accent
    ) {
        // Every piece lives at the ring centre and is *translated* into place, so the animation can
        // spin the whole ring without ever teleporting an entity.
        BlockDisplay display = world.spawn(anchor, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(MARKER_TAG);
            d.setBlock(block);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.9f);
            d.setInterpolationDuration(4);
            d.setTransformation(new Transformation(
                new Vector3f((float) (Math.cos(angle) * radius), (float) lift, (float) (Math.sin(angle) * radius)),
                ringRotation(angle, accent),
                new Vector3f(sx, sy, sz),
                new Quaternionf()));
        });
        markers.add(display);
        return new RingPart(display, angle, radius, sx, sy, sz, lift, accent);
    }

    private static Quaternionf ringRotation(double angle, boolean accent) {
        Quaternionf rotation = new Quaternionf().rotateY((float) -angle);
        if (accent) {
            rotation.rotateX((float) (Math.PI / 4));   // a cube turned into a diamond
        }
        return rotation;
    }

    private TextDisplay spawnBoard(Player player, Location at, String title, String hint) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        TextDisplay board = world.spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(MARKER_TAG);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(1.8f);
            d.setSeeThrough(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setLineWidth(200);
            d.text(Msg.parse(title + "\n" + hint));
        });
        playerMarkers.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(board);
        return board;
    }

    private void pointAtRim(Player player, String label) {
        MatchSession session = plugin.gameManager() == null ? null : plugin.gameManager().getSessionOf(player.getUniqueId());
        if (session == null || session.arena() == null) {
            return;
        }
        Vector rim = session.arena().geometry().southHoopRim();
        Location at = rim.toLocation(player.getWorld()).add(0, 0.9, 0);
        spawnBoard(player, at, label, "<#64748B>aim here</#64748B>");
    }

    private void clearMarkers(UUID id) {
        ringParts.remove(id);
        List<org.bukkit.entity.Entity> markers = playerMarkers.remove(id);
        if (markers != null) {
            for (org.bukkit.entity.Entity marker : markers) {
                if (marker != null && marker.isValid()) {
                    marker.remove();
                }
            }
            markers.clear();
        }
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
        ringParts.clear();
        introProps.clear();
        pendingWalkTargets.clear();
        return removed;
    }

    // ------------------------------------------------------------------ HUD

    private void showBossBar(Player player) {
        BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), k ->
            BossBar.bossBar(Msg.parse(Msg.gradBold(Msg.GRAD_GOLD, "ɴᴇᴡ ᴘʟᴀʏᴇʀ ɢᴜɪᴅᴇ")), 0f,
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
            bar.name(Msg.parse("<" + Palette.GOLD_SOFT + "><bold>" + (current + 1) + "/" + STEPS.size() + "</bold></" + Palette.GOLD_SOFT + ">"
                + " <#64748B>·</#64748B> <#F8FAFC>"
                + (validated ? "✔ done" : hudLabel(step, id, false)) + "</#F8FAFC>"));
            bar.progress(TutorialFlow.bossBarProgress(current, validated, STEPS.size()));
            bar.color(validated ? BossBar.Color.GREEN : BossBar.Color.YELLOW);
        }
        Msg.rail(player, "<" + Palette.GOLD_SOFT + ">◆</" + Palette.GOLD_SOFT + "> <#F8FAFC>" + (current + 1) + "/" + STEPS.size()
            + "</#F8FAFC> <#64748B>\u00b7</#64748B> <#94A3B8>"
            + (validated ? "✔ validated" : hudLabel(step, id, true)) + "</#94A3B8>");;
    }

    /**
     * What the boss bar / action bar says about the current chapter. A reading chapter shows a live
     * countdown and the fact that a simple click is enough, so nobody thinks the tour is frozen.
     */
    private String hudLabel(Step step, UUID id, boolean actionBar) {
        if (!step.reading()) {
            return actionBar ? step.objective() : step.objective().toLowerCase(Locale.ROOT);
        }
        long waited = System.currentTimeMillis() - lastStepAdvance.getOrDefault(id, System.currentTimeMillis());
        long left = Math.max(0L, step.autoAdvanceMs() - waited);
        long seconds = (left + 999L) / 1000L;
        return actionBar
            ? "<#F8FAFC>CLICK ANYWHERE</#F8FAFC> to continue <#64748B>·</#64748B> " + seconds + "s"
            : "click anywhere to continue · " + seconds + "s";
    }

    // ------------------------------------------------------------------ validation triggers

    /** Called by the game engine / the GUI when the player performs the action of the chapter. */
    public void notifyAction(Player player, String action, fr.bosketball.model.ProgressStat unusedStat) {
        if (player == null || action == null || !isRunning(player)) {
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
        Msg.send(player, Msg.success("<" + Palette.SUCCESS + "><bold>✔ " + step.subtitle() + "</bold></" + Palette.SUCCESS + ">"));
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
        }, 20L);
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
        Player player = event.getPlayer();
        if (!isRunning(player) || currentStepValidated(player)) {
            pendingWalkTargets.remove(id);
            return;
        }
        if (to.distanceSquared(target) <= RING_ENTER_RADIUS_SQ) {
            pendingWalkTargets.remove(id);
            turnRingGreen(player, target);
            completeStep(player, "walk");
        }
    }

    private void turnRingGreen(Player player, Location centre) {
        List<RingPart> parts = ringParts.get(player.getUniqueId());
        if (parts != null) {
            for (RingPart part : parts) {
                BlockDisplay display = part.display();
                if (display != null && display.isValid()) {
                    display.setBlock(emeraldSlice());
                }
            }
        }
        World world = centre.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.HAPPY_VILLAGER, centre.clone().add(0, 0.4, 0), 26, 1.4, 0.35, 1.4, 0.02);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, centre.clone().add(0, 0.6, 0), 10, 0.8, 0.4, 0.8, 0.05);
        world.playSound(centre, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
    }

    /** Sneak is the crossover input; it validates the defense chapter on its own. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && isRunning(event.getPlayer())) {
            notifyAction(event.getPlayer(), "defense", null);
        }
    }

    /** A death during the tour must not leave a boss bar, markers or blindness behind. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isRunning(player) || isInIntro(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    stop(player, true);
                    Msg.send(player, Msg.info("Tour paused — <#F8FAFC>/tuto</#F8FAFC> resumes it where you were."));
                }
            });
        }
    }

    /** Markers live in a single world: a world change respawns them where the player landed. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!isRunning(player) || isInIntro(player)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !isRunning(player)) {
                return;
            }
            safe("decorate", () -> {
                clearMarkers(player.getUniqueId());
                decorateStep(player, STEPS.get(Math.min(currentStep(player), STEPS.size() - 1)));
            });
        });
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
            if (tickCounter % 20 == 0 && TutorialFlow.shouldPauseForMatch(true,
                plugin.gameManager() != null && plugin.gameManager().isActivelyPlayingCompetitiveMatch(id))) {
                // A player who queued up finished the tour: the match must not eat the bonus.
                boolean endTour = TutorialFlow.finishesOnMatchStart(currentStep(player), STEPS.size());
                safe("match-guard", () -> {
                    if (endTour) {
                        finish(player, false);
                    } else {
                        stop(player, true);
                    }
                });
                continue;
            }
            if (tickCounter % 4 == 0) {
                safe("markers", () -> animateMarkers(player));
            }
            if (tickCounter % 20 == 0) {
                safe("hud", () -> updateHud(player));
            }
            if (tickCounter % 20 == 0) {
                safe("watchdog", () -> softSkipWatchdog(player));
            }
            if (tickCounter % 100 == 0) {
                safe("refresh", () -> refreshMarkersIfFar(player));
            }
        }

        if (tickCounter % 100 == 0 && (hologram == null || !hologram.isValid())) {
            safe("hologram", this::spawnHologram);
        }
        if (tickCounter % 1200 == 0 && System.currentTimeMillis() - lastHologramSweep > 60_000L) {
            // Once a minute, drop duplicated guide holograms left behind by a crash.
            safe("hologram-sweep", this::sweepDuplicateHolograms);
        }
        if (tickCounter % 10 == 0 && hologram != null && hologram.isValid()) {
            Location base = hologramCache;
            if (base != null && Viewers.anyWithin(base, HOLOGRAM_RANGE)) {
                hologram.teleport(base.clone().add(0, Math.sin(tickCounter * 0.05) * 0.08, 0));
            }
        }
    }

    /** Removes any guide hologram that is not the one we track (dup after a hard restart). */
    private void sweepDuplicateHolograms() {
        if (hologram == null || !hologram.isValid()) {
            return;
        }
        lastHologramSweep = System.currentTimeMillis();
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(hologram.getUniqueId())
                    || entity.getUniqueId().equals(guideBox == null ? null : guideBox.getUniqueId())) {
                    continue;
                }
                if (entity.getScoreboardTags().contains(HOLO_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Self-heal: after a /leave, a staff teleport or a practice court that ended, the gold ring can
     * be dozens of blocks away. Moving it back under the player beats walking to a ghost marker.
     */
    private void refreshMarkersIfFar(Player player) {
        UUID id = player.getUniqueId();
        Location ring = pendingWalkTargets.get(id);
        if (ring == null || ring.getWorld() == null) {
            return;
        }
        if (!ring.getWorld().equals(player.getWorld())
            || ring.distanceSquared(player.getLocation()) > 40.0 * 40.0) {
            clearMarkers(id);
            decorateStep(player, STEPS.get(Math.min(currentStep(player), STEPS.size() - 1)));
        }
    }

    private void animateMarkers(Player player) {
        UUID id = player.getUniqueId();
        List<RingPart> parts = ringParts.get(id);
        if (parts != null && !parts.isEmpty()) {
            double time = tickCounter * 0.05;
            float spin = (float) (tickCounter * 0.010);          // one full turn every ~31 s
            float pulse = (float) (1.0 + 0.07 * Math.sin(time * 2.2));
            float breath = (float) (Math.sin(time * 1.7) * 0.035);
            for (RingPart part : parts) {
                BlockDisplay display = part.display();
                if (display == null || !display.isValid()) {
                    continue;
                }
                double angle = part.angle() + spin;
                float x = (float) (Math.cos(angle) * part.radius());
                float z = (float) (Math.sin(angle) * part.radius());
                float y = (float) (part.lift() + (part.accent() ? breath * 1.8 : breath * 0.5));
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(4);
                display.setTransformation(new Transformation(
                    new Vector3f(x, y, z),
                    ringRotation(angle, part.accent()),
                    new Vector3f(part.scaleX() * pulse, part.scaleY(), part.scaleZ() * pulse),
                    new Quaternionf()));
            }
        }
        Location ring = pendingWalkTargets.get(id);
        if (ring == null || ring.getWorld() == null || tickCounter % 20 != 0) {
            return;
        }
        double phase = tickCounter * 0.16;
        for (int i = 0; i < 3; i++) {
            double angle = phase + (Math.PI * 2.0 / 3.0) * i;
            Location spark = ring.clone().add(Math.cos(angle) * 1.8,
                0.35 + 0.15 * Math.sin(phase * 1.3 + i), Math.sin(angle) * 1.8);
            ring.getWorld().spawnParticle(Particle.END_ROD, spark, 1, 0.02, 0.02, 0.02, 0.0);
            ring.getWorld().spawnParticle(Particle.DUST, spark, 1, 0.05, 0.05, 0.05, 0.0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFD43B), 0.9f));
        }
        // A thin rising column marks the centre from far away.
        ring.getWorld().spawnParticle(Particle.DUST, ring.clone().add(0, 1.4, 0), 2, 0.25, 0.6, 0.25, 0.0,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(0xFFE9A8), 0.7f));
        // A discreet dotted line from the player to the objective.
        Vector from = player.getLocation().add(0, 1.2, 0).toVector();
        Vector to = ring.clone().add(0, 0.6, 0).toVector();
        for (double t = 0.25; t < 0.95; t += 0.22) {
            Vector point = from.clone().add(to.clone().subtract(from).multiply(t));
            player.getWorld().spawnParticle(Particle.GLOW, point.toLocation(player.getWorld()), 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /**
     * Round 30 — the chapter never validates itself behind the player's back.
     *
     * <p>After {@link #SOFT_SKIP_MS} the player gets a hint aimed at the exact mechanic of the
     * chapter; after {@link #SKIP_AFTER_MS} the skip becomes available with one click. Only the two
     * "reading" chapters move on alone, and they never pay the chapter reward for it.
     */
    private void softSkipWatchdog(Player player) {
        UUID id = player.getUniqueId();
        Integer current = activeStep.get(id);
        if (current == null || stepValidatedAt.containsKey(id) || isInIntro(player)) {
            return;
        }
        Step step = STEPS.get(Math.min(current, STEPS.size() - 1));
        long started = lastStepAdvance.getOrDefault(id, System.currentTimeMillis());
        long age = System.currentTimeMillis() - started;
        int level = hintLevel.getOrDefault(id, 0);

        // Chapters that need a ball and have no court can never be done: pass them honestly.
        if (TutorialFlow.needsLiveCourt(step.requirement()) && !hasLiveBall(player)) {
            if (age >= 6_000L) {
                Msg.send(player, Msg.info("<#94A3B8>No practice court is free right now, so the "
                    + "<#F8FAFC>" + step.requirement() + "</#F8FAFC> chapter is skipped for you.</#94A3B8>"));
                completeStep(player, step.requirement());
            }
            return;
        }

        switch (TutorialFlow.hintStage(step.autoValidated(), age, level, SOFT_SKIP_MS, SKIP_AFTER_MS,
            step.autoAdvanceMs())) {
            case AUTO_COMPLETE -> completeStep(player, step.requirement());
            case OFFER_SKIP -> {
                hintLevel.put(id, 2);
                Msg.send(player, Msg.info("Still stuck? <#F8FAFC>" + step.hint() + "</#F8FAFC>"));
                Msg.send(player, Msg.warn("You can pass this chapter with <#FDE047>"
                    + "<click:run_command:'/tuto skip'>[SKIP THIS CHAPTER]</click></#FDE047> "
                    + "<#94A3B8>(no reward).</#94A3B8>"));
                safe("decorate", () -> decorateStep(player, step));
            }
            case HINT -> {
                hintLevel.put(id, 1);
                lastHintAt.put(id, System.currentTimeMillis());
                Msg.send(player, Msg.info("<#94A3B8>Tip:</#94A3B8> <#F8FAFC>" + step.hint() + "</#F8FAFC>"));
            }
            default -> {
            }
        }
    }

    /** True when the player is on a practice court and actually holds a ball. */
    private boolean hasLiveBall(Player player) {
        if (plugin.gameManager() == null) {
            return false;
        }
        MatchSession session = plugin.gameManager().getSessionOf(player.getUniqueId());
        return session != null && session.mode() == GameModeType.PRACTICE;
    }

    /** Re-prints the current chapter card (used by /tuto show and the guide button). */
    public void showCurrentStep(Player player) {
        if (player == null || !isRunning(player)) {
            return;
        }
        if (isInIntro(player)) {
            Msg.send(player, Msg.info("The intro is playing — one second."));
            return;
        }
        showStep(player, Math.min(currentStep(player), STEPS.size() - 1));
    }

    // ------------------------------------------------------------------ progress helpers

    private static int totalStepRewardsStatic() {
        int sum = 0;
        for (Step s : STEPS) {
            sum += s.revealCoins();
        }
        return sum;
    }

    public int totalStepRewards() {
        return totalStepRewardsStatic();
    }

}
