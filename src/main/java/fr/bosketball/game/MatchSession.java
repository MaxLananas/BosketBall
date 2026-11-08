package fr.bosketball.game;

import fr.bosketball.util.Palette;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.animation.AnimationKinematics;
import fr.bosketball.animation.PlayerVisualRig;
import fr.bosketball.arena.Arena;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.MatchHistoryEntry;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.ShotReleaseQuality;
import fr.bosketball.model.TeamSide;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.physics.PhysicsMath;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BossBarHudManager;
import fr.bosketball.ui.CinematicCameraManager;
import fr.bosketball.ui.GuiMenuManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.GroundCheck;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Perms;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class MatchSession implements BasketballEntity.ScoreListener {

    /** Longest make of the match, kept for the final card. Round 76, item 96. */
    private double longestMakeBlocks;
    private String longestMakeName;
    private boolean longestMakeWasSlam;


    /** Widest name that still leaves the stat columns room on one line. */
    private static final int CARD_NAME_WIDTH = 12;

    /**
     * Shortens a name so a card line cannot overflow.
     *
     * <p>Round 74. Names reach sixteen characters, and a five-on-five box score put three of them
     * on one line with separators and three stat columns. The line wrapped mid-column and turned
     * the only readable summary in the match into soup.
     */
    private static String cardName(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.length() <= CARD_NAME_WIDTH ? name : name.substring(0, CARD_NAME_WIDTH - 1) + "\u2026";
    }


    public enum State {
        WAITING,
        COUNTDOWN,
        LIVE,
        CELEBRATION,
        MVP_CEREMONY,
        FINISHED
    }

    public static final class MatchBoxScore {
        public int points;
        public int assists;
        public int rebounds;
        public int steals;
        public int blocks;
        public int dunks;
        public int threes;
        public int hotStreak;
        public int coinsDelta;

        public double mvpScore() {
            return points * 1.0 + assists * 1.6 + rebounds * 1.2 + steals * 2.1 + blocks * 2.3 + dunks * 0.4;
        }
    }

    private static final class ShotChargeState {
        double progress = 0.08;
        int ticksActive = 0;
        boolean playedFullDrawSound = false;
        /** Wall-clock start of the charge: the bar is driven by this, never by the client hand state. */
        final long startedAtMs = System.currentTimeMillis();
        /** Consecutive ticks where the vanilla bow draw was lost while the charge was alive. */
        int lostHandTicks = 0;

        long heldMs(long nowMs) {
            return Math.max(0L, nowMs - startedAtMs);
        }
    }

    private static final class SavedPlayerState {
        final ItemStack[] contents;
        final ItemStack[] armor;
        final ItemStack offHand;
        final int heldSlot;
        final Location returnLocation;
        final GameMode gameMode;
        final boolean allowFlight;
        final boolean flying;
        final float exp;
        final int level;
        final double health;
        final int foodLevel;
        final float saturation;

        final double walkSpeed;

        final double flySpeed;

        SavedPlayerState(Player p) {
            this.contents = deepCloneArray(p.getInventory().getStorageContents());
            this.armor = deepCloneArray(p.getInventory().getArmorContents());
            ItemStack off = p.getInventory().getItemInOffHand();
            this.offHand = off != null ? off.clone() : null;
            this.heldSlot = p.getInventory().getHeldItemSlot();
            this.returnLocation = p.getLocation().clone();
            this.gameMode = p.getGameMode();
            this.allowFlight = p.getAllowFlight();
            this.flying = p.isFlying();
            this.exp = p.getExp();
            this.level = p.getLevel();
            this.health = Math.max(1.0, Math.min(20.0, p.getHealth()));
            this.foodLevel = p.getFoodLevel();
            AttributeInstance mv = p.getAttribute(Attribute.MOVEMENT_SPEED);
            this.walkSpeed = mv != null ? mv.getBaseValue() : 0.10;
            AttributeInstance fly = p.getAttribute(Attribute.FLYING_SPEED);
            this.flySpeed = fly != null ? fly.getBaseValue() : 0.05;
            this.saturation = p.getSaturation();
        }

        private static ItemStack[] deepCloneArray(ItemStack[] src) {
            ItemStack[] copy = new ItemStack[src.length];
            for (int i = 0; i < src.length; i++) {
                copy[i] = src[i] != null ? src[i].clone() : null;
            }
            return copy;
        }

        void restore(Player p, Location customLobbyExit, boolean autoEquipLobbyHotbar) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.setFallDistance(0f);

            p.getInventory().setStorageContents(deepCloneArray(contents));
            p.getInventory().setArmorContents(deepCloneArray(armor));
            p.getInventory().setItemInOffHand(offHand != null ? offHand.clone() : null);
            p.getInventory().setHeldItemSlot(Math.clamp(heldSlot, 0, 8));
            GameMode safeMode = (gameMode == GameMode.SPECTATOR) ? GameMode.ADVENTURE : gameMode;
            p.setGameMode(safeMode);
            p.setAllowFlight(allowFlight && safeMode != GameMode.ADVENTURE);
            p.setFlying(p.getAllowFlight() && flying);
            p.setExp(exp);
            p.setLevel(level);
            p.setHealth(Math.max(1.0, Math.min(20.0, health)));
            p.setFoodLevel(foodLevel);
            p.setSaturation(saturation);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            // Resetting the board to the main one wipes the sidebar: rebuild it immediately
            // instead of waiting for the next UI tick (which used to be skipped by the cache).
            BosketBallPlugin sidebarPlugin = BosketBallPlugin.getInstance();
            if (sidebarPlugin != null && sidebarPlugin.sidebarManager() != null) {
                sidebarPlugin.sidebarManager().clearPlayerCache(p.getUniqueId());
                sidebarPlugin.sidebarManager().updatePlayerSidebar(p, sidebarPlugin.gameManager());
                if (sidebarPlugin.tabListManager() != null) {
                    sidebarPlugin.tabListManager().clearPlayerCache(p.getUniqueId());
                }
            }

            AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(walkSpeed);
            }
            AttributeInstance flySpeedAttr = p.getAttribute(Attribute.FLYING_SPEED);
            if (flySpeedAttr != null) {
                flySpeedAttr.setBaseValue(flySpeed);
            }
            Location dest = (customLobbyExit != null && customLobbyExit.getWorld() != null) ? customLobbyExit : returnLocation;
            BosketBallPlugin plug = BosketBallPlugin.getInstance();
            if (plug != null && plug.protectionManager() != null) {
                boolean isBuilder = plug.protectionManager().isInBuildMode(p.getUniqueId());
                boolean inPracticeWorld = dest != null && dest.getWorld() != null
                    && dest.getWorld().getName().equalsIgnoreCase(plug.settings().practice().worldName());
                if (inPracticeWorld || (customLobbyExit == null && !isBuilder && plug.protectionManager().hasCustomLobbySpawn())) {
                    Location mainSpawn = plug.protectionManager().resolveLobbySpawn(null);
                    if (mainSpawn != null && mainSpawn.getWorld() != null) {
                        dest = mainSpawn;
                    }
                }
            }
            if (dest != null && dest.getWorld() != null) {
                p.teleport(dest);
            }
            if (autoEquipLobbyHotbar) {
                BallSkin.equipLobbyHotbarIfEmpty(p, false);
            }
        }
    }

    private final BosketBallPlugin plugin;
    private final Arena arena;
    private final GameModeType mode;
    private final BossBarHudManager hudManager;
    private final StatsManager statsManager;
    private final Random rng = new Random();

    private final Map<UUID, TeamSide> participants = new ConcurrentHashMap<>();
    private final Map<UUID, SavedPlayerState> savedStates = new ConcurrentHashMap<>();
    private final Map<UUID, MatchBoxScore> boxScores = new ConcurrentHashMap<>();
    private final Map<TeamSide, Integer> scores = new EnumMap<>(TeamSide.class);

    private static final int SHOT_LOCK_TICKS = 4;
    private int shotLockTicks = 0;

    private final Map<UUID, ShotChargeState> activeShotCharges = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerVisualRig> visualRigs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stealCooldowns = new HashMap<>();
    private final Map<UUID, Long> crossoverCooldowns = new HashMap<>();
    private final Map<UUID, Long> dunkReadyAtMillis = new HashMap<>();
    /** Horizontal speed of the directional dash impulse (blocks/tick). */
    private static final double DASH_STRENGTH = 0.74;
    private final Map<UUID, Long> blockCooldowns = new HashMap<>();
    private final Map<UUID, Long> hustleDiveCooldowns = new HashMap<>();
    private final CinematicCameraManager.ReplayRingBuffer replayBuffer = new CinematicCameraManager.ReplayRingBuffer(72);
    private final java.util.Set<UUID> swungThisTick = ConcurrentHashMap.newKeySet();

    private final java.util.Set<UUID> previousTickSwingers = ConcurrentHashMap.newKeySet();

    private State state = State.WAITING;
    private BasketballEntity ball;
    private CourtDefenderBot sparringBot;
    private fr.bosketball.game.bot.BotSquad botSquad;
    private fr.bosketball.game.bot.BotDifficulty botDifficulty =
        fr.bosketball.game.bot.BotDifficulty.NORMAL;

    private fr.bosketball.model.GameQueueMode queueMode = fr.bosketball.model.GameQueueMode.RANKED;

    private final long startedAtMillis = System.currentTimeMillis();
    /** Stable, human readable match id (replaces the old System.identityHashCode hack). */
    private final String matchId = String.format(java.util.Locale.ROOT, "%06X", rng.nextInt(0x1000000));
    /** Round 27: a tied score is a draw — it used to hand the win (and the +15 ELO) to IGNIS. */
    private boolean matchDrawn;

    private boolean matchReportSent = false;

    private final Map<UUID, Integer> appliedEloDeltas = new ConcurrentHashMap<>();
    private final Map<UUID, fr.bosketball.season.SeasonResult> seasonResults = new ConcurrentHashMap<>();

    private final Map<UUID, Integer> stealStreak = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStealAttemptAt = new ConcurrentHashMap<>();

    private final Map<UUID, TeamSide> recordedSides = new ConcurrentHashMap<>();

    private boolean resultsApplied = false;

    private boolean ceremonyStarted = false;

    // ---------------------------------------------------------------------------------------
    // Direction (match director): the phase document, the tension curve and the play of the match.
    // ---------------------------------------------------------------------------------------

    /**
     * The ledger every phase transition goes through. {@code state} is only ever assigned inside
     * {@link #enterState}, which always asks this ledger first and always commits a surface, so a
     * state cannot be reached with nothing on screen. See {@link MatchDirector}.
     */
    private final MatchDirector director = new MatchDirector();

    /** True while the match is inside its last two minutes with one possession in it. */
    private boolean clutchTime = false;
    /** The crowd bed of the clutch run has already been played once for this match. */
    private boolean clutchBedPlayed = false;
    /** Unanswered points by the run, and by whom, for the momentum timeout. */
    private int runPoints = 0;
    private TeamSide runTeam = null;
    /** One automatic timeout per quarter: the beat is drama, not a tax. */
    private boolean timeoutUsedThisQuarter = false;
    /** Six unanswered points is the run a real coach stops; it is the trigger, not a threshold. */
    private static final int RUN_TIMEOUT_POINTS = 6;

    /** Inside two minutes of the last period, inside one possession. */
    private static final int CLUTCH_WINDOW_SECONDS = 120;

    /** The single most decisive play of the match, for the ceremony. */
    private double bestPlayLeverage = 0.0;
    private String bestPlayLine = null;
    /** Distance of the most recent basket, for the play-of-the-match wording. */
    private double lastBasketDistanceBlocks = 0.0;
    /** Who will get the outro camera, and which of them watch cinematics at all. */
    private final java.util.Set<UUID> ceremonyOutroViewers = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> ceremonyOutroCinematic = ConcurrentHashMap.newKeySet();

    /**
     * Leverage of one basket: what the shot was worth, weighted by the state of the game.
     *
     * <p>A routine make in the second quarter is worth its points. The same make inside the last
     * two minutes of a one-possession game is worth several times that, and a make at the horn of
     * the last period is worth more again. This is the "best play of the match" ranking, and it is
     * the reason a 30-block heave at the buzzer outranks a wide-open dunk in the first quarter.
     * Pure and static so the suite can pin the slope without a server.
     */
    public static double playLeverage(int points, boolean finalPeriod, int secondsLeft, int marginAfter) {
        int pts = Math.max(0, points);
        double leverage = pts;
        if (finalPeriod) {
            leverage *= 1.6;
            if (secondsLeft <= 120) {
                leverage *= 1.5;
            }
            if (marginAfter <= 2) {
                leverage *= 1.35;
            }
            // The ladder inside the last ten seconds, so "a five-second look" and "the horn" are
            // not the same moment with the same weight.
            if (secondsLeft <= 10) {
                leverage *= 1.2;
            }
            if (secondsLeft <= 2) {
                leverage *= 1.4;
            }
        }
        return leverage;
    }

    /** The phase on screen, as declared by the phase document. {@code null} before the tip-off. */
    public MatchPhase phase() {
        return director.current();
    }

    /** The phase ledger, for the audit and for the suite. */
    public MatchDirector director() {
        return director;
    }

    public boolean clutchTime() {
        return clutchTime;
    }

    /** The best play of the match, worded for the ceremony card. {@code null} until one exists. */
    public String bestPlayLine() {
        return bestPlayLine;
    }

    private boolean forfeitVictory = false;

    private int overtimesPlayed = 0;
    /**
     * Round 56: a shot released before the horn is still a shot.
     *
     * <p>The period used to end on the exact tick the clock hit zero, so a ball already in the air
     * simply stopped counting -- the most exciting moment in basketball, silently cancelled. The
     * buzzer now waits for the ball to come down, for at most a second and a half.
     */
    private int buzzerGraceTicks = 0;
    /** Round 59: the pack ships a crowd cheer and nothing ever played it. */
    private long lastCrowdCheerMs = 0L;
    private static final long CROWD_CHEER_GAP_MS = 2500L;

    private void cheerCrowd(double volume, double pitch) {
        long now = System.currentTimeMillis();
        if (now - lastCrowdCheerMs < CROWD_CHEER_GAP_MS) {
            return;
        }
        lastCrowdCheerMs = now;
        Msg.playPackSound(arena.centerCourtLocation(), "crowd.cheer", (float) volume, (float) pitch);
    }
    private static final int BUZZER_GRACE_TICKS = 30;
    private static final int MAX_OVERTIMES = 3;

    private final Map<UUID, Integer> disconnectGrace = new ConcurrentHashMap<>();

    private final Map<UUID, TeamSide> disconnectSides = new ConcurrentHashMap<>();
    private static final int DISCONNECT_GRACE_TICKS = 45 * 20;
    /** ELO taken from a player who abandons a ranked match (same as the leave button). */
    private static final int FORFEIT_ELO_LOSS = 15;

    private int currentQuarter = 1;
    private final int totalQuarters;
    private int ticksRemainingInPeriod;
    private static final double FATIGUE_SPRINT_GAIN = 0.0018;

    private static final double FATIGUE_RECOVERY = 0.0012;

    private static final double FATIGUE_MAX_CHARGE_PENALTY = 0.16;

    private static final double FATIGUE_CONTEST_WEIGHT = 0.18;

    private static final int STUCK_BALL_TICKS = 60;

    private static final double STUCK_BALL_RADIUS_SQ = 0.25;

    private final java.util.Map<UUID, Double> fatigue = new java.util.HashMap<>();

    private final java.util.Set<UUID> fatigueWarned = new java.util.HashSet<>();

    private int stuckBallTicks;

    private Vector stuckBallAnchor;

    private int shotClockTicks;
    private int stateTimerTicks;
    private int warmupBannerTicks = 0;
    private int tickCounter = 0;
    private UUID lastSyncedHolderId = null;
    private BallSkin lastSyncedHolderSkin = null;

    private String celebScorerName = "";
    private String celebRoleTag = "";
    private Glyphs.BannerVariant celebVariant = Glyphs.BannerVariant.REGULAR;
    private String celebSubLabel = "";
    private String celebSubValue = "";
    private TeamSide nextInboundTeam = TeamSide.IGNIS;

    private int contestRackIndex = 0;
    private int contestBallInRack = 0;
    private boolean currentShotIsMoneyBall = false;
    private int contestShotSequence = 0;

    private final Map<UUID, Location> lastObservedPose = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> idleTicksByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> spectatorTargetCursor = new ConcurrentHashMap<>();

    public String matchId() {
        return matchId;
    }

    public MatchSession(BosketBallPlugin plugin, Arena arena, GameModeType mode) {
        this.plugin = plugin;
        this.arena = arena;
        this.mode = mode;
        // Round 27: a session created through /bb play <arena> never called setQueueMode(), so the
        // field fell back to RANKED and a casual game silently moved ELO. Derive it from the mode.
        this.queueMode = mode != null ? mode.queueMode() : fr.bosketball.model.GameQueueMode.CASUAL;
        this.hudManager = plugin.bossBarHud();
        this.statsManager = plugin.statsManager();
        this.totalQuarters = mode == GameModeType.DUEL_1V1 ? 2 : plugin.settings().match().quarters();
        this.shotClockTicks = plugin.settings().match().shotClockSeconds() * 20;
        scores.put(TeamSide.IGNIS, 0);
        scores.put(TeamSide.FROST, 0);
    }

    private long lastPracticeCineCamMillis = 0L;
    private boolean finishMatchAfterCelebration = false;

    public UUID sparringBotEntityId() {
        return sparringBot != null ? sparringBot.entityUniqueId() : null;
    }

    public Arena arena() {
        return arena;
    }

    public GameModeType mode() {
        return mode;
    }

    public State state() {
        return state;
    }

    public BasketballEntity ball() {
        return ball;
    }

    public int score(TeamSide side) {
        return scores.getOrDefault(side, 0);
    }

    public TeamSide getTeam(UUID playerId) {
        if (playerId == null) return TeamSide.SPECTATOR;
        TeamSide human = participants.get(playerId);
        if (human != null) return human;
        return botTeamMap.getOrDefault(playerId, TeamSide.SPECTATOR);
    }

    public fr.bosketball.season.SeasonResult seasonResult(UUID playerId) {
        return playerId == null ? null : seasonResults.get(playerId);
    }

    public MatchBoxScore getBoxScore(UUID playerId) {
        return boxScores.computeIfAbsent(playerId, k -> new MatchBoxScore());
    }

    public int participantCount() {
        return participants.size();
    }

    public Map<UUID, TeamSide> participants() {
        return java.util.Collections.unmodifiableMap(participants);
    }

    public int currentQuarter() {
        return currentQuarter;
    }

    public fr.bosketball.model.GameQueueMode queueMode() {
        return queueMode;
    }

    public void setQueueMode(fr.bosketball.model.GameQueueMode queueMode) {
        this.queueMode = queueMode == null ? fr.bosketball.model.GameQueueMode.RANKED : queueMode;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public int elapsedSeconds() {
        return (int) Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 1000L);
    }

    public boolean isInsideRemakeWindow() {
        if (state != State.LIVE && state != State.COUNTDOWN) {
            return false;
        }
        long windowMs = plugin.settings().match().remakeWindowSeconds() * 1000L;
        return windowMs > 0 && System.currentTimeMillis() - startedAtMillis < windowMs;
    }

    public boolean hasPlayer(UUID playerId) {
        return participants.containsKey(playerId);
    }

    public List<Player> onlineParticipants() {
        List<Player> list = new ArrayList<>(participants.size());
        for (UUID id : participants.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                list.add(p);
            }
        }
        return list;
    }

    public int shotClockSeconds() {
        if (mode == GameModeType.THREE_POINT_CONTEST && state == State.LIVE) {
            return Math.max(0, (int) Math.ceil(ticksRemainingInPeriod / 20.0));
        }
        return Math.max(0, (int) Math.ceil(shotClockTicks / 20.0));
    }

    public String formattedClock() {
        if (mode == GameModeType.PRACTICE) {
            return "PRACTICE";
        }
        if (state == State.WAITING) {
            return "WAITING " + activeCompetitorCount() + "/" + mode.maxPlayers();
        }
        if (state == State.COUNTDOWN) {
            return "START " + Math.max(1, (int) Math.ceil(stateTimerTicks / 20.0)) + "S";
        }
        int totalSec = Math.max(0, ticksRemainingInPeriod / 20);
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        String qPrefix = mode == GameModeType.THREE_POINT_CONTEST
            ? "3PT "
            : (currentQuarter > totalQuarters ? "OT " : "Q" + currentQuarter + " ");
        return String.format(java.util.Locale.ROOT, "%s%02d:%02d", qPrefix, mins, secs);
    }

    public boolean addPlayer(Player player, TeamSide preferredSide) {
        if (state == State.MVP_CEREMONY || state == State.FINISHED) {
            return false;
        }
        if (participants.size() >= mode.maxPlayers() && preferredSide != TeamSide.SPECTATOR) {
            preferredSide = TeamSide.SPECTATOR;
        }

        TeamSide assigned = preferredSide;
        if (assigned == null || (assigned != TeamSide.SPECTATOR && countTeam(assigned) >= mode.playersPerTeam())) {
            if (!mode.competitiveTeamMatch()) {
                assigned = TeamSide.IGNIS;
            } else {
                assigned = countTeam(TeamSide.IGNIS) <= countTeam(TeamSide.FROST) ? TeamSide.IGNIS : TeamSide.FROST;
            }
        }

        plugin.clearLobbyRig(player.getUniqueId(), false);
        savedStates.putIfAbsent(player.getUniqueId(), new SavedPlayerState(player));
        participants.put(player.getUniqueId(), assigned);
        if (plugin.gameManager() != null) {
            plugin.gameManager().indexPlayerSession(player.getUniqueId(), this);
        }
        boxScores.putIfAbsent(player.getUniqueId(), new MatchBoxScore());

        equipCourtKit(player, assigned);
        if (assigned == TeamSide.SPECTATOR) {
            player.teleport(arena.centerCourtLocation().add(0, 3.2, 0));
            syncSpectatorInvisibility();
            Msg.send(player, Msg.info("Spectating <#F8FAFC><bold>" + arena.displayName() + "</bold></#F8FAFC> <#64748B>\u00b7</#64748B> <#94A3B8>hotbar to switch view</#94A3B8>"));
        } else {
            player.teleport(arena.teamSpawn(assigned, countTeam(assigned) - 1));
            syncSpectatorInvisibility();
        }
        PlayerVisualRig oldRig = visualRigs.remove(player.getUniqueId());
        if (oldRig != null) {
            oldRig.despawn();
        }
        if (assigned != TeamSide.SPECTATOR) {
            visualRigs.put(player.getUniqueId(), new PlayerVisualRig(plugin, player, assigned));
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.15f);

        String joinHex = String.format(java.util.Locale.ROOT, "%06X", assigned.textColor().value() & 0xFFFFFF);
        if (mode.competitiveTeamMatch()) {
            broadcast("<#" + joinHex + ">" + player.getName()
                + "</#" + joinHex + "> <#94A3B8>joined</#94A3B8> <#F8FAFC>" + arena.displayName() + "</#F8FAFC> "
                + "<#64748B>(" + activeCompetitorCount() + "/" + mode.maxPlayers() + ")</#64748B>");
        }

        if (state == State.WAITING) {
            // The warm-up room is a phase: the scorebug is already up and empty, so the eye has
            // found where the score will be before the first possession happens.
            enterState(State.WAITING, MatchPhase.WAITING, MatchPhase.Surface.SCOREBUG);
        }
        if (state == State.WAITING && activeCompetitorCount() >= mode.minTotalPlayersToStart()) {
            beginCountdown();
        } else if (state == State.WAITING && assigned != TeamSide.SPECTATOR) {
            if (ball == null) {
                spawnOrResetBallEntity();
                resetShotClock(false);
                PlayerProfile prof = statsManager.getOrCreate(player);
                ball.attachToHolder(player, prof.selectedBallSkin(), false);
            }
            syncBallHotbarItems(true);
            String opForceBtn = Perms.staff(player)
                ? " <click:run_command:'/bb forcestart'><hover:show_text:'<#4ADE80>Start match now (OP)</#4ADE80>'><" + Palette.SUCCESS + ">[▶ Start Now]</" + Palette.SUCCESS + "></hover></click>"
                : "";
            Msg.sendCourt(player, Msg.info("Warmup active while waiting for opponents." + opForceBtn));
        } else {
            syncBallHotbarItems(true);
        }
        return true;
    }

    public void removePlayer(Player player, boolean silent) {
        UUID id = player.getUniqueId();
        if (!participants.containsKey(id)) {
            return;
        }
        TeamSide leavingSide = participants.get(id);
        boolean wasForfeit = mode.competitiveTeamMatch()
            && (state == State.LIVE || state == State.CELEBRATION)
            && leavingSide != null
            && leavingSide != TeamSide.SPECTATOR
            && countTeam(leavingSide.opposite()) > 0;

        if (plugin.cinematicCamera() != null) {
            plugin.cinematicCamera().stopWatching(player, false);
        }

        // Round 35 — a ranked disconnect used to be handled *after* the roster removal: the player
        // was deleted from the roster at once, so "you have 45 s to come back" was a lie (they came
        // back as a spectator) and never coming back cost nothing at all (the forfeit branch below
        // was unreachable: we had already returned). The slot is now kept for the grace window.
        if (wasForfeit && !player.isOnline() && isRankedMatch() && !disconnectSides.containsKey(id)) {
            activeShotCharges.remove(id);
            lastObservedPose.remove(id);
            idleTicksByPlayer.remove(id);
            spectatorTargetCursor.remove(id);
            PlayerVisualRig parkedRig = visualRigs.remove(id);
            if (parkedRig != null) {
                parkedRig.despawn();
            }
            hudManager.clearPlayer(player);
            if (ball != null && id.equals(ball.holderId())) {
                ball.popLoose(new Vector(0, 0.22, 0), null);
            }
            beginDisconnectGrace(id);
            return;
        }

        participants.remove(id);
        if (plugin.gameManager() != null) {
            plugin.gameManager().unindexPlayerSession(id, this);
        }
        activeShotCharges.remove(id);
        lastObservedPose.remove(id);
        idleTicksByPlayer.remove(id);
        spectatorTargetCursor.remove(id);
        plugin.resyncVisibilityFor(player);
        PlayerVisualRig rig = visualRigs.remove(id);
        if (rig != null) {
            rig.despawn();
        }
        hudManager.clearPlayer(player);

        if (ball != null && id.equals(ball.holderId())) {
            ball.popLoose(new Vector(0, 0.22, 0), null);
        }

        SavedPlayerState saved = savedStates.remove(id);
        if (saved != null) {
            boolean autoHotbar = plugin.settings().general().autoEquipLobbyPlaybook();
            saved.restore(player, arena.lobbyExitLocationOrNull(), autoHotbar);
        }

        if (player.isOnline() && plugin.settings().chat().clearAfterMatch()) {
            Msg.clearChatBuffer(player);
        }

        if (wasForfeit && isInsideRemakeWindow()) {
            boolean ranked = isRankedMatch();
            cancelAsRemake(player, leavingSide, ranked);
            return;
        }

        if (wasForfeit && !isRankedMatch()) {

            PlayerProfile casualProfile = statsManager.getOrCreate(player);
            casualProfile.recordMatchOutcome(false);
            statsManager.markDirty(id);
            statsManager.saveProfileAsync(casualProfile);
            broadcast("<" + Palette.GOLD + ">⚠ " + player.getName() + " left the casual match — no ELO change.</" + Palette.GOLD + ">");
        } else if (wasForfeit) {
            PlayerProfile prof = statsManager.getOrCreate(player);
            int lost = registerForfeit(id, leavingSide, prof);
            if (player.isOnline()) {
                Msg.send(player, Msg.warn("You forfeited the match (<" + Palette.DANGER + ">-" + FORFEIT_ELO_LOSS + " ELO</" + Palette.DANGER + ">"
                    + (lost > 0 ? ", <" + Palette.DANGER + ">-" + lost + " Coins</" + Palette.DANGER + ">" : "") + ")."));
            }
        } else if (!silent && player.isOnline()) {
            Msg.send(player, Msg.info("Returned to the lobby."));
        }

        if (!silent && !participants.isEmpty()) {
            broadcast("<#94A3B8>" + player.getName() + " left the match.</#94A3B8>");
        }

        if (activeCompetitorCount() == 0 && state != State.FINISHED) {
            stopAndCleanup();
        } else if (mode.competitiveTeamMatch() && (state == State.LIVE || state == State.CELEBRATION)) {
            if (countTeam(TeamSide.IGNIS) == 0) {
                finishMatchAndStartMvpCeremony(TeamSide.FROST);
            } else if (countTeam(TeamSide.FROST) == 0) {
                finishMatchAndStartMvpCeremony(TeamSide.IGNIS);
            }
        }
    }

    public void toggleSparringBot(Player requester) {
        if (mode != GameModeType.PRACTICE && state != State.WAITING) {
            Msg.sendAlert(requester, "The practice defender only exists in Practice (<#FDE047>/practice</#FDE047>).");
            return;
        }
        if (sparringBot != null) {
            sparringBot.remove();
            sparringBot = null;
            Msg.rail(requester, fr.bosketball.util.HudSurface.LOBBY, "<" + Palette.DANGER
                + "><bold>Defender off</bold></" + Palette.DANGER + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.MUTED + ">practice is solo again</" + Palette.MUTED + ">");
        } else {
            Vector rim = selectClosestOrOffensiveRim(requester, getTeam(requester.getUniqueId()));
            Vector startPos = requester.getLocation().toVector().multiply(0.55).add(rim.clone().multiply(0.45));
            startPos.setY(arena.geometry().floorY());
            sparringBot = new CourtDefenderBot(arena, startPos);
            Msg.rail(requester, fr.bosketball.util.HudSurface.LOBBY, "<" + Palette.SUCCESS
                + "><bold>Defender on</bold></" + Palette.SUCCESS + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.MUTED + ">practice is live</" + Palette.MUTED + ">");
            Msg.playPersonalPackSound(requester, "game.whistle", 0.75f, 1.15f);
        }
        if (mode == GameModeType.PRACTICE) {
            requester.getInventory().setItem(4, BallSkin.createSparringBotItem(sparringBot != null));
        }
    }

    public void forceStart() {
        if (state == State.WAITING && activeCompetitorCount() >= 1) {
            beginCountdown();
        }
    }

    /**
     * The only place a {@code state} is written.
     *
     * <p>Every branch below opens a step in {@link MatchDirector}, and every branch then puts
     * something in front of the player <em>synchronously</em> and commits it. That is the whole
     * dead-air rule: the transition and its cue happen inside the same call, so the gap between
     * them is zero ticks, not "whenever the next scheduled task runs".
     *
     * <p>A transition the table refuses is <b>not applied</b>. The old text said the state would be
     * set anyway "so the match keeps running"; that was a lie the audit could not see, because it
     * would have invented a phase the ledger had already rejected. A refusal returns {@code false}
     * and the caller either corrects itself (a ceremony that already ran asks again from a later
     * path) or does nothing (a one-way door that is already shut), and the refusal is counted.
     *
     * @return true when the state and the phase both moved.
     */
    private boolean enterState(State next, MatchPhase phase, MatchPhase.Surface surface) {
        MatchDirector.Step step = director.enter(phase, surface, director.current(), System.currentTimeMillis());
        if (step == null) {
            // Refused: the phase already on screen stays on screen. Never set the state anyway --
            // that is how a match would end up in MVP_CEREMONY with the ledger still saying LIVE.
            return false;
        }
        State from = this.state;
        this.state = next;
        cuePhase(phase, from);
        director.commit(step, System.currentTimeMillis());
        return true;
    }

    /**
     * What each phase says to the arena the moment it begins.
     *
     * <p>Three of these are new and they are the ones the match was missing: the period break had a
     * line in chat and no horn, the celebration never told the bench that had just been scored on
     * what had happened to it, and coming back from a replay never said the ball was live again.
     */
    private void cuePhase(MatchPhase phase, State from) {
        switch (phase) {
            case LIVE -> {
                if (from == State.CELEBRATION) {
                    cueBackInPlay();
                }
            }
            case QUARTER_BREAK -> cueQuarterBreak();
            case DEAD_BALL -> cueDeadBall();
            case CLUTCH -> cueClutchBed();
            // TIP_OFF, WAITING, TIMEOUT, FINAL_POSSESSION, CEREMONY and FINAL are surfaced by the
            // call site that enters them, in the same tick, with their own detail.
            default -> {
            }
        }
    }

    /**
     * The horn between periods: the title, the whistle and the jumbotron, in the same tick.
     *
     * <p>This is what the phase buys over the old code. A quarter break used to be a {@code Msg.title}
     * buried in the buzzer handler and a chat line -- two surfaces, neither of them a beat. It is now
     * a phase, and both benches are told which way the next possession goes before it happens.
     */
    private void cueQuarterBreak() {
        String label = currentQuarter >= totalQuarters ? "END OF REGULATION" : "END OF Q" + currentQuarter;
        int ignis = score(TeamSide.IGNIS);
        int frost = score(TeamSide.FROST);
        for (Player p : onlineParticipants()) {
            if (getTeam(p.getUniqueId()) == TeamSide.SPECTATOR) {
                continue;
            }
            Msg.title(p, "<" + Palette.GOLD + "><bold>" + ignis + " - " + frost + "</bold></" + Palette.GOLD + ">",
                "<#94A3B8>" + label + "</#94A3B8>", 10, 50, 20);
        }
        Msg.playPackSound(arena.centerCourtLocation(), "game.whistle", 1.05f, 1.02f);
        arena.updateJumbotron(
            teamUpperLabel(TeamSide.IGNIS), ignis, "END OF Q" + currentQuarter,
            Math.max(0, shotClockSeconds()), frost, teamUpperLabel(TeamSide.FROST)
        );
    }

    /**
     * A whistle that stops play without ending anything: out of bounds, a shot-clock violation, a
     * rim reset. Two benches, two lines — the phase exists so the call site cannot forget.
     */
    private void cueDeadBall() {
        if (nextInboundTeam == null || nextInboundTeam == TeamSide.SPECTATOR) {
            return;
        }
        for (Player p : onlineParticipants()) {
            TeamSide mine = getTeam(p.getUniqueId());
            if (mine == TeamSide.SPECTATOR) {
                continue;
            }
            if (mine == nextInboundTeam) {
                Msg.rail(p, "<" + Palette.SUCCESS + "><bold>Your ball</bold></" + Palette.SUCCESS
                    + "> <#64748B>\u00b7</#64748B> <#94A3B8>inbound it</#94A3B8>");
            } else {
                Msg.rail(p, "<#94A3B8>Their ball</#94A3B8> <#64748B>\u00b7</#64748B> <#94A3B8>get set</#94A3B8>");
            }
        }
    }

    /**
     * The tension curve, audible half: the crowd comes in closer and higher for the only two
     * minutes of the match that are allowed to sound different. It plays once per match, so the
     * bed marks the moment instead of nagging through it.
     */
    private void cueClutchBed() {
        if (clutchBedPlayed) {
            return;
        }
        clutchBedPlayed = true;
        Msg.playPackSound(arena.centerCourtLocation(), "crowd.cheer", 1.05f, 1.14f);
    }

    /** Back from a replay into live play: the player is told the ball is live again. */
    private void cueBackInPlay() {
        for (Player p : onlineParticipants()) {
            if (getTeam(p.getUniqueId()) == TeamSide.SPECTATOR) {
                continue;
            }
            boolean mine = getTeam(p.getUniqueId()) == nextInboundTeam;
            Msg.playPersonalPackSound(p, "game.whistle", 0.55f, mine ? 1.18f : 1.0f);
            if (mine && clutchTime) {
                // The clock changes voice: in the last two minutes of a one-possession game the
                // inbound names the time left, once per possession, instead of staying quiet.
                int left = Math.max(0, ticksRemainingInPeriod / 20);
                Msg.rail(p, "<" + Palette.SUCCESS + "><bold>Your ball</bold></" + Palette.SUCCESS
                    + "> <#64748B>\u00b7</#64748B> <#F8FAFC>"
                    + String.format(java.util.Locale.ROOT, "%d:%02d", left / 60, left % 60)
                    + "</#F8FAFC> <#94A3B8>left</#94A3B8>");
            } else if (mine) {
                Msg.rail(p, "<" + Palette.SUCCESS + "><bold>Your ball</bold></" + Palette.SUCCESS
                    + "> <#64748B>\u00b7</#64748B> <#94A3B8>inbound it</#94A3B8>");
            }
        }
    }

    private void beginCountdown() {
        if (!mode.competitiveTeamMatch()) {
            startLivePlay();
            return;
        }
        if (sparringBot != null) {
            sparringBot.remove();
            sparringBot = null;
        }
        if (botSquad != null && !botSquad.isEmpty()) {
            clearBots();
        }
        this.stateTimerTicks = plugin.settings().match().countdownSeconds() * 20;
        enterState(State.COUNTDOWN, MatchPhase.TIP_OFF, MatchPhase.Surface.TITLE);
        broadcast(Msg.info("Match starting in <#F8FAFC><bold>" + (stateTimerTicks / 20) + "s</bold></#F8FAFC>."));
    }

    private void startLivePlay() {
        this.stateTimerTicks = 0;
        this.currentQuarter = 1;
        this.clutchTime = false;
        this.clutchBedPlayed = false;
        this.runPoints = 0;
        this.runTeam = null;
        this.timeoutUsedThisQuarter = false;
        this.bestPlayLeverage = 0.0;
        this.bestPlayLine = null;
        this.warmupBannerTicks = 0;
        scores.put(TeamSide.IGNIS, 0);
        scores.put(TeamSide.FROST, 0);
        boxScores.clear();
        activeShotCharges.clear();

        spawnOrResetBallEntity();
        syncBallHotbarItems(true);

        if (mode == GameModeType.PRACTICE) {
            this.ticksRemainingInPeriod = 20 * 3600;
            resetShotClock(false);
            Player solo = onlineParticipants().stream().findFirst().orElse(null);
            if (solo != null) {
                solo.teleport(arena.teamSpawn(TeamSide.IGNIS, 0));
                PlayerProfile prof = statsManager.getOrCreate(solo);
                ball.attachToHolder(solo, prof.selectedBallSkin(), false);
                solo.getInventory().setItem(4, BallSkin.createSparringBotItem(sparringBot != null));
                Msg.title(
                    solo,
                    Msg.gradBold(Msg.GRAD_BRAND, "Practice Court"),
                    "<#F8FAFC>Right-click</#F8FAFC> <#64748B>\u00b7</#64748B> <#94A3B8>hold, release to shoot</#94A3B8>",
                    200, 2000, 350
                );
            }
            enterState(State.LIVE, MatchPhase.LIVE, MatchPhase.Surface.TITLE);
            stageCeremonyScreens();
            return;
        }

        if (mode == GameModeType.THREE_POINT_CONTEST) {
            this.ticksRemainingInPeriod = 60 * 20;
            this.shotClockTicks = 60 * 20;
            this.contestRackIndex = 0;
            this.contestBallInRack = 0;
            Player shooter = onlineParticipants().stream().findFirst().orElse(null);
            if (shooter != null) {
                advanceContestShot(shooter, true);
                Msg.title(
                    shooter,
                    Msg.gradBold(Msg.GRAD_ICE, "3-Point Contest"),
                    "<#94A3B8>5 Racks · 25 Balls · 60 Seconds · Money Ball = 2 PTS</#94A3B8>",
                    200, 2000, 350
                );
                Msg.playPackSound(shooter.getLocation(), "game.whistle", 1.0f, 1.05f);
            }
            enterState(State.LIVE, MatchPhase.LIVE, MatchPhase.Surface.TITLE);
            stageCeremonyScreens();
            return;
        }

        int qSec = plugin.settings().match().quarterSeconds();
        this.ticksRemainingInPeriod = qSec * 20;
        resetShotClock(false);

        int ignisIdx = 0;
        int frostIdx = 0;
        String ignisTitle = teamDisplayName(TeamSide.IGNIS);
        String frostTitle = teamDisplayName(TeamSide.FROST);
        for (Player p : onlineParticipants()) {
            TeamSide side = getTeam(p.getUniqueId());
            if (side == TeamSide.IGNIS) {
                p.teleport(arena.teamSpawn(TeamSide.IGNIS, ignisIdx++));
            } else if (side == TeamSide.FROST) {
                p.teleport(arena.teamSpawn(TeamSide.FROST, frostIdx++));
            }
            Msg.title(
                p,
                Msg.gradBold(Msg.GRAD_GOLD, "Tip-Off!"),
                "<" + Palette.EMBER + ">" + ignisTitle + "</" + Palette.EMBER + "> <#64748B>vs</#64748B> <" + Palette.ICE + ">" + frostTitle + "</" + Palette.ICE + "> <#64748B>\u00b7</#64748B> <#F8FAFC>" + mode.smallCapsTitle() + "</#F8FAFC>",
                150, 1400, 300
            );
            Msg.playPersonalPackSound(p, "game.whistle", 0.85f, 1.12f);
            Msg.send(p, "<" + Palette.EMBER + "><bold>" + ignisTitle + "</bold></" + Palette.EMBER + "> <#94A3B8>vs</#94A3B8> <" + Palette.ICE + "><bold>" + frostTitle + "</bold></" + Palette.ICE + "> <#64748B>\u00b7</#64748B> <#F8FAFC>" + mode.smallCapsTitle() + "</#F8FAFC> <#94A3B8>(24s Shot Clock)</#94A3B8>");
        }

        Msg.playPackSound(arena.centerCourtLocation(), "game.whistle", 1.2f, 1.0f);
        ball.resetToCenterTipOff(
            new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ()),
            0.46
        );
        enterState(State.LIVE, MatchPhase.LIVE, MatchPhase.Surface.TITLE);
        stageCeremonyScreens();
    }

    /** Ticks before the hand-off at which the outro camera fires, inside the 120-tick ceremony. */
    private static final int CEREMONY_OUTRO_AT_TICKS = 50;

    /**
     * Beat four of the ceremony: the slow cut around centre court, for everyone who has cinematics
     * on. One call per player, once per match — the beat is a bow, not a loop.
     */
    private void playOutroCameras() {
        if (plugin.cinematicCamera() == null || ceremonyOutroViewers.isEmpty()) {
            return;
        }
        java.util.List<Player> audience = new ArrayList<>(ceremonyOutroViewers.size());
        java.util.List<Player> cinematic = new ArrayList<>(ceremonyOutroCinematic.size());
        for (UUID id : ceremonyOutroViewers) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                continue;
            }
            if (plugin.cinematicCamera().isWatching(id)) {
                continue;
            }
            audience.add(p);
            if (ceremonyOutroCinematic.contains(id)) {
                cinematic.add(p);
            }
        }
        ceremonyOutroViewers.clear();
        ceremonyOutroCinematic.clear();
        if (audience.isEmpty() || cinematic.isEmpty()) {
            return;
        }
        Vector centre = new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ());
        Vector rim = arena.geometry().southHoopRim();
        int length = Math.max(24, CEREMONY_OUTRO_AT_TICKS - 6);
        for (Player p : cinematic) {
            Msg.playPersonalPackSound(p, "game.whistle", 0.7f, 0.92f);
        }
        plugin.cinematicCamera().playBasketCelebrationCut(audience, null, TeamSide.IGNIS, rim, centre, false, length);
    }

    /**
     * The outro camera, armed per player while the session is still live.
     *
     * <p>{@link CinematicCameraManager} defers viewers whose {@code cinematicScoreCam} setting is
     * off, so deciding this at play time keeps the ceremony loop cheap; {@link #playOutroCameras()}
     * then fires it at the last beat, where the players are already standing at centre court.
     */
    private void stageCeremonyScreens() {
        for (Player p : onlineParticipants()) {
            PlayerProfile prof = statsManager.getOrCreate(p);
            ceremonyOutroViewers.add(p.getUniqueId());
            if (prof.settings().cinematicScoreCam()) {
                ceremonyOutroCinematic.add(p.getUniqueId());
            }
        }
    }

    private void spawnOrResetBallEntity() {
        if (ball != null) {
            ball.remove();
        }
        var phys = plugin.settings().physics();
        this.ball = new BasketballEntity(
            arena,
            arena.centerCourtLocation().add(0, 1.2, 0),
            phys.gravity(),
            phys.airDrag(),
            phys.floorRestitution(),
            phys.backboardRestitution(),
            phys.rimRestitution()
        );
    }

    public void tick() {
        tickCounter++;
        tickDisconnectGrace();

        previousTickSwingers.clear();
        previousTickSwingers.addAll(swungThisTick);
        swungThisTick.clear();
        arena.tickDisplays(shotClockSeconds());
        tickPlayerVisualRigs();

        if (state != State.FINISHED && tickCounter % 4 == 0) {
            for (Player p : onlineParticipants()) {
                TeamSide s = getTeam(p.getUniqueId());
                if (s != TeamSide.SPECTATOR) {
                    arena.enforcePlayerContainment(p, s);
                }
            }
        }

        if (state == State.WAITING || state == State.LIVE) {
            tickShotChargesAndLiveAimHud();
        }

        if (hasBots() && (state == State.LIVE || state == State.WAITING)) {
            long botBudgetStarted = fr.bosketball.util.TickProfiler.beginBudget();
            try {
                botSquad.tick();
            } finally {
                fr.bosketball.util.TickProfiler.endBudget("bots", botBudgetStarted);
            }
        }

        if (sparringBot != null) {
            Player solo = onlineParticipants().stream().findFirst().orElse(null);
            if (solo != null) {
                long botBudgetStarted = fr.bosketball.util.TickProfiler.beginBudget();
                try {
                    Vector rim = selectClosestOrOffensiveRim(solo, getTeam(solo.getUniqueId()));
                    boolean charging = activeShotCharges.containsKey(solo.getUniqueId())
                        || (solo.isHandRaised() && solo.getActiveItem().getType() == Material.BOW);
                    sparringBot.tick(solo, rim, ball, charging);
                } finally {
                    fr.bosketball.util.TickProfiler.endBudget("bots", botBudgetStarted);
                }
            }
        }

        if (state == State.WAITING) {
            if (ball != null) {
                recordReplayTick();
                ball.tick(this);
                if (ball.holderId() == null) {
                    checkPlayerBallInterceptionAndRebounds();
                }
            }
            tickPossessionShotClock(false);
            syncBallHotbarItems(false);

            if (warmupBannerTicks > 0) {
                warmupBannerTicks--;
                for (Player p : onlineParticipants()) {
                    hudManager.showScorerCelebration(p, celebScorerName, celebRoleTag, celebVariant, celebSubLabel, celebSubValue);
                }
            } else if (tickCounter % 5 == 0) {
                String waitLabel = "WAITING " + activeCompetitorCount() + "/" + mode.maxPlayers();
                int sc = shotClockSeconds();
                String ignisLbl = teamUpperLabel(TeamSide.IGNIS);
                String frostLbl = teamUpperLabel(TeamSide.FROST);
                for (Player p : onlineParticipants()) {
                    hudManager.showLiveScorebug(p, ignisLbl, score(TeamSide.IGNIS), waitLabel, sc, score(TeamSide.FROST), frostLbl);
                }
                arena.updateJumbotron(ignisLbl, score(TeamSide.IGNIS), "WAITING (" + activeCompetitorCount() + "/" + mode.maxPlayers() + ")", sc, score(TeamSide.FROST), frostLbl);
            }
            return;
        }

        if (state == State.COUNTDOWN) {
            activeShotCharges.clear();
            stateTimerTicks--;
            if (stateTimerTicks > 0 && stateTimerTicks % 20 == 0) {
                int sec = stateTimerTicks / 20;
                String ignisLbl = teamUpperLabel(TeamSide.IGNIS);
                String frostLbl = teamUpperLabel(TeamSide.FROST);
                for (Player p : onlineParticipants()) {
                    Msg.title(p, "<" + Palette.GOLD + "><bold>" + sec + "</bold></" + Palette.GOLD + ">", "<#94A3B8>Starts the match</#94A3B8>", 50, 800, 150);
                    Msg.playPersonalPackSound(p, "ui.click", 0.9f, 1.0f + (5 - sec) * 0.08f);
                    hudManager.showLiveScorebug(p, ignisLbl, 0, "START " + sec + "S", 24, 0, frostLbl);
                }
                arena.updateJumbotron(ignisLbl, 0, "TIP-OFF " + sec + "S", 24, 0, frostLbl);
            }
            if (stateTimerTicks <= 0) {
                startLivePlay();
            }
            return;
        }

        if (state == State.MVP_CEREMONY) {
            stateTimerTicks--;
            if (ball != null) {
                ball.tick(null);
            }
            // Beat four of four: the outro camera. It is armed late in the ceremony, after the
            // result the players are reading has had time to land, and it is the last thing that
            // happens before the hand-off, so nothing can be mid-shot when the lobby takes over.
            if (stateTimerTicks == CEREMONY_OUTRO_AT_TICKS) {
                playOutroCameras();
            }
            if (stateTimerTicks <= 0) {
                stopAndCleanup();
            }
            return;
        }

        if (ball != null) {
            if (state == State.LIVE) {
                recordReplayTick();
            }
            ball.tick(state == State.LIVE ? this : null);
            if (state == State.LIVE && ball.holderId() == null) {
                checkPlayerBallInterceptionAndRebounds();
            }
            if (state == State.LIVE || state == State.WAITING) {
                checkStuckBall();
            }
        }

        if (state == State.LIVE || state == State.WAITING) {
            tickFatigue();
        }

        if (state != State.LIVE && state != State.WAITING) {
            tickShotChargesAndLiveAimHud();
        }

        if (tickCounter % 4 == 0 || shotLockTicks == SHOT_LOCK_TICKS) {
            syncBallHotbarItems(false);
        }

        if (state == State.CELEBRATION) {
            stateTimerTicks--;
            for (Player p : onlineParticipants()) {
                hudManager.showScorerCelebration(p, celebScorerName, celebRoleTag, celebVariant, celebSubLabel, celebSubValue);
            }
            if (stateTimerTicks <= 0) {
                resumeAfterCelebration();
            }
            return;
        }

        if (state == State.LIVE) {
            tickLiveClocksAndHud();
            if (mode.competitiveTeamMatch() && tickCounter % 20 == 0) {
                tickCompetitiveAfkWatchdog();
            }
        }
    }

    private void tickCompetitiveAfkWatchdog() {
        List<Player> afkRemovals = null;
        for (Player p : onlineParticipants()) {
            UUID pid = p.getUniqueId();
            if (getTeam(pid) == TeamSide.SPECTATOR) {
                continue;
            }
            Location nowLoc = p.getLocation();

            Location prevLoc = lastObservedPose.get(pid);
            boolean moved = prevLoc == null
                || !Objects.equals(prevLoc.getWorld(), nowLoc.getWorld())
                || prevLoc.distanceSquared(nowLoc) > 0.0016
                || Math.abs(prevLoc.getYaw() - nowLoc.getYaw()) > 1.5f
                || Math.abs(prevLoc.getPitch() - nowLoc.getPitch()) > 1.5f
                || p.isHandRaised();
            if (moved) {
                lastObservedPose.put(pid, nowLoc.clone());
                idleTicksByPlayer.put(pid, 0);
                continue;
            }
            int idle = idleTicksByPlayer.getOrDefault(pid, 0) + 20;
            idleTicksByPlayer.put(pid, idle);

            if (idle >= 900 && idle < 1200) {
                Msg.send(p, Msg.warn("<bold>AFK</bold> <#64748B>\u00b7</#64748B> move within <#F8FAFC>15s</#F8FAFC> or you are out"));
                Msg.rail(p, fr.bosketball.util.HudSurface.LOBBY, "<" + Palette.DANGER
                + "><bold>AFK</bold></" + Palette.DANGER + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.MUTED + ">move within 15s</" + Palette.MUTED + ">");
                Msg.playPersonalPackSound(p, "game.buzzer", 0.7f, 1.35f);
            } else if (idle >= 1200) {
                if (afkRemovals == null) {
                    afkRemovals = new ArrayList<>(2);
                }
                afkRemovals.add(p);
            }
        }
        if (afkRemovals != null) {
            for (Player afk : afkRemovals) {
                Msg.send(afk, Msg.error("Removed from match due to 60 seconds of inactivity (AFK)."));
                removePlayer(afk, false);
            }
        }
    }

    public void syncSpectatorInvisibility() {
        List<Player> all = onlineParticipants();
        for (Player spec : all) {
            if (getTeam(spec.getUniqueId()) != TeamSide.SPECTATOR) {
                continue;
            }
            for (Player other : all) {
                if (!other.getUniqueId().equals(spec.getUniqueId()) && getTeam(other.getUniqueId()) != TeamSide.SPECTATOR) {
                    other.hideEntity(plugin, spec);
                }
            }
        }
    }

    public boolean isCeremonyState() {
        return state == State.MVP_CEREMONY;
    }

    public void handleSpectatorHotbarAction(Player spectator, int slot) {
        if (spectator == null || getTeam(spectator.getUniqueId()) != TeamSide.SPECTATOR) {
            return;
        }
        if (slot == 0) {
            List<Player> competitors = onlineParticipants().stream()
                .filter(p -> getTeam(p.getUniqueId()) != TeamSide.SPECTATOR)
                .toList();
            if (competitors.isEmpty()) {
                spectator.teleport(arena.centerCourtLocation().add(0, 3.0, 0));
                return;
            }
            int nextIdx = Math.floorMod(spectatorTargetCursor.getOrDefault(spectator.getUniqueId(), -1) + 1, competitors.size());
            spectatorTargetCursor.put(spectator.getUniqueId(), nextIdx);
            Player target = competitors.get(nextIdx);
            spectator.teleport(target.getLocation().clone().add(0.0, 2.2, 0.0));
            spectator.setFlying(true);
            Msg.rail(spectator, fr.bosketball.util.HudSurface.LOBBY, "<" + Palette.ICE
                + "><bold>Following</bold></" + Palette.ICE + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.TEXT + "><bold>" + cardName(target.getName()) + "</bold></" + Palette.TEXT + ">"
                + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED
                + ">" + getBoxScore(target.getUniqueId()).points + " PTS</" + Palette.MUTED + ">");
            Msg.playPersonalPackSound(spectator, "ui.click", 0.8f, 1.25f);
        } else if (slot == 4) {
            List<String> lines = new ArrayList<>();
            lines.add("<" + Palette.EMBER + "><bold>" + teamDisplayName(TeamSide.IGNIS) + " " + score(TeamSide.IGNIS) + "</bold></" + Palette.EMBER + "> <#64748B>-</#64748B> <" + Palette.ICE + "><bold>" + score(TeamSide.FROST) + " " + teamDisplayName(TeamSide.FROST) + "</bold></" + Palette.ICE + "> <#94A3B8>(" + formattedClock() + ")</#94A3B8>");
            for (Player p : onlineParticipants()) {
                TeamSide s = getTeam(p.getUniqueId());
                if (s == TeamSide.SPECTATOR) {
                    continue;
                }
                MatchBoxScore b = getBoxScore(p.getUniqueId());
                String col = s == TeamSide.FROST ? "<" + Palette.ICE + ">" : "<" + Palette.DANGER + ">";
                lines.add(" " + col + "· " + cardName(p.getName()) + Msg.closeTag(col) + " <#94A3B8>—</#94A3B8> <#FDE047>" + b.points + " PTS</#FDE047> <#64748B>·</#64748B> <#38BDF8>" + b.assists + " AST</#38BDF8> <#64748B>·</#64748B> <#4ADE80>" + b.rebounds + " REB</#4ADE80>");
            }
            Msg.sendFramedCard(spectator, "Live Box Score \u00b7 " + arena.displayName(), lines);
            Msg.playPersonalPackSound(spectator, "ui.click", 0.8f, 1.2f);
        } else if (slot == 8) {
            removePlayer(spectator, false);
        }
    }

    public boolean handleCeremonyHotbarAction(Player player, org.bukkit.NamespacedKey modelKey) {
        if (state != State.MVP_CEREMONY || player == null || modelKey == null) {
            return false;
        }
        String k = modelKey.getKey();
        if ("icon_star_shield".equals(k)) {
            GameModeType replayMode = this.mode;
            removePlayer(player, true);
            plugin.gameManager().joinQueue(player, replayMode);
            return true;
        }
        if ("ref_coins".equals(k)) {
            plugin.gameManager().triggerGg(player);
            return true;
        }
        if ("ref_exit".equals(k)) {
            removePlayer(player, false);
            return true;
        }
        return false;
    }

    private void recordReplayTick() {
        if (ball == null) {
            return;
        }
        if (plugin.cinematicCamera() != null) {
            for (Player p : onlineParticipants()) {
                if (getTeam(p.getUniqueId()) != TeamSide.SPECTATOR
                    && plugin.cinematicCamera().isWatching(p.getUniqueId())) {
                    return;
                }
            }
        }
        List<CinematicCameraManager.ActorSnapshot> actorSnaps = new ArrayList<>(participants.size());
        for (Player p : onlineParticipants()) {
            TeamSide side = getTeam(p.getUniqueId());
            if (side == TeamSide.SPECTATOR) {
                continue;
            }
            Location l = p.getLocation();
            boolean swung = previousTickSwingers.contains(p.getUniqueId())
                || swungThisTick.contains(p.getUniqueId());
            actorSnaps.add(new CinematicCameraManager.ActorSnapshot(
                p.getUniqueId(),
                p.getName(),
                side,
                l.getX(),
                l.getY(),
                l.getZ(),
                l.getYaw(),
                l.getPitch(),
                p.isSneaking(),
                swung
            ));
        }
        replayBuffer.record(new CinematicCameraManager.ReplayFrame(
            ball.position(),
            ball.spinRotationSnapshot(),
            ball.skin(),
            ball.isOnFireTrail(),
            actorSnaps
        ));
    }

    private void tickLiveClocksAndHud() {
        if (mode != GameModeType.PRACTICE) {
            ticksRemainingInPeriod--;
        }

        if (mode != GameModeType.THREE_POINT_CONTEST) {
            if (tickPossessionShotClock(mode.competitiveTeamMatch())) {
                return;
            }
        }

        if (warmupBannerTicks > 0) {
            warmupBannerTicks--;
            for (Player p : onlineParticipants()) {
                hudManager.showScorerCelebration(p, celebScorerName, celebRoleTag, celebVariant, celebSubLabel, celebSubValue);
            }
        } else if (tickCounter % 5 == 0) {
            String clockStr = formattedClock();
            int sc = shotClockSeconds();
            int sIgnis = score(TeamSide.IGNIS);
            int sFrost = score(TeamSide.FROST);
            String ignisLbl = teamUpperLabel(TeamSide.IGNIS);
            String frostLbl = teamUpperLabel(TeamSide.FROST);

            for (Player p : onlineParticipants()) {
                hudManager.showLiveScorebug(p, ignisLbl, sIgnis, clockStr, sc, sFrost, frostLbl);
            }
            arena.updateJumbotron(
                hasCustomTeamName(TeamSide.IGNIS) ? ignisLbl : "ɪɢɴɪꜱ",
                sIgnis, clockStr, sc, sFrost,
                hasCustomTeamName(TeamSide.FROST) ? frostLbl : "ꜰʀᴏꜱᴛ"
            );
        }

        if (mode != GameModeType.PRACTICE && ticksRemainingInPeriod <= 0) {
            if (ball != null && (ball.mode() == BasketballEntity.BallMode.IN_SHOT_FLIGHT
                    || ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT)
                && buzzerGraceTicks == 0) {
                buzzerGraceTicks = BUZZER_GRACE_TICKS;
                broadcast("<#F8FAFC><bold>Shot in the air</bold></#F8FAFC> <#64748B>\u00b7</#64748B> <#94A3B8>the horn waits</#94A3B8>");
            }
            if (buzzerGraceTicks > 0) {
                buzzerGraceTicks--;
            } else {
                handlePeriodEndBuzzer();
            }
        }
    }

    public double fatigueOf(UUID playerId) {
        if (playerId == null) {
            return 0.0;
        }
        return fatigue.getOrDefault(playerId, 0.0);
    }

    private void tickFatigue() {
        for (Player p : onlineParticipants()) {
            UUID id = p.getUniqueId();
            double current = fatigue.getOrDefault(id, 0.0);
            boolean sprinting = p.isSprinting() && !p.isFlying()
                && p.getVelocity().lengthSquared() > 0.02;
            current = sprinting ? current + FATIGUE_SPRINT_GAIN : current - FATIGUE_RECOVERY;
            if (current <= 0.0) {
                fatigue.remove(id);
                fatigueWarned.remove(id);
                continue;
            }
            current = Math.min(1.0, current);
            fatigue.put(id, current);
            if (current >= 0.55 && fatigueWarned.add(id)) {
                Msg.rail(p, fr.bosketball.util.HudSurface.COACH, "<" + Palette.GOLD
                + "><bold>Gassed</bold></" + Palette.GOLD + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.MUTED + ">slow down to shoot</" + Palette.MUTED + ">");
            } else if (current < 0.35) {
                fatigueWarned.remove(id);
            }
        }
    }

    private void checkStuckBall() {
        if (ball == null || ball.holderId() != null) {
            stuckBallTicks = 0;
            stuckBallAnchor = null;
            return;
        }
        Vector position = ball.position();
        if (stuckBallAnchor == null || stuckBallAnchor.distanceSquared(position) > STUCK_BALL_RADIUS_SQ) {
            stuckBallAnchor = position.clone();
            stuckBallTicks = 0;
            return;
        }
        stuckBallTicks++;
        boolean sankBelowFloor = position.getY() < arena.geometry().floorY() - 1.5;
        if (stuckBallTicks < STUCK_BALL_TICKS && !sankBelowFloor) {
            return;
        }
        stuckBallTicks = 0;
        stuckBallAnchor = null;
        ball.resetToCenterTipOff(
            new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ()),
            0.46
        );
        resetShotClock(false);
        Msg.playPackSound(arena.centerCourtLocation(), "game.whistle", 0.9f, 1.18f);
        for (Player p : onlineParticipants()) {
            Msg.rail(p, fr.bosketball.util.HudSurface.RULE, "<" + Palette.GOLD + "><bold>Dead ball</bold></"
                + Palette.GOLD + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.MUTED + ">back to centre</" + Palette.MUTED + ">");
        }
    }

    private boolean tickPossessionShotClock(boolean competitiveTurnoverOnExpire) {
        boolean possessionClockActive = ball != null && (
            ball.holderId() != null
                || ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT
                || ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT
        );
        if (!possessionClockActive) {
            return false;
        }

        int previousShotClock = shotClockTicks;
        shotClockTicks--;
        if (shotClockTicks > 0 && shotClockTicks <= 100 && shotClockTicks % 20 == 0) {
            int secondsLeft = shotClockTicks / 20;
            float pitch = 1.12f + (5 - secondsLeft) * 0.09f;
            float volume = Math.min(1.0f, 0.82f + (5 - secondsLeft) * 0.035f);
            for (Player p : onlineParticipants()) {
                Msg.playPersonalPackSound(p, "ui.click", volume, pitch);
            }
        }
        if (previousShotClock > 20 && shotClockTicks == 20) {
            Msg.playPackSound(arena.centerCourtLocation(), "game.whistle", 0.55f, 1.25f);
        }
        if (shotClockTicks <= 0) {
            if (competitiveTurnoverOnExpire) {
                handleShotClockViolation();
                return true;
            }

            Msg.playPackSound(arena.centerCourtLocation(), "game.buzzer", 0.95f, 1.02f);
            for (Player p : onlineParticipants()) {
                getBoxScore(p.getUniqueId()).hotStreak = 0;
                Msg.rail(p, fr.bosketball.util.HudSurface.RULE, fr.bosketball.util.HudCopy.shotClockLost());
            }
            resetShotClock(false);
        }
        return false;
    }

    /**
     * True when nothing solid stands between the defender and the ball.
     *
     * <p>Round 69. The steal scan used to be pure distance, so a defender parked behind a
     * bleacher, a stanchion or the scorer's table scooped up passes it could not see. A steal
     * through a wall reads as the game cheating, which is the one thing a player never forgives.
     */
    private boolean hasClearPath(Player p, Vector ballPos) {
        World w = p.getWorld();
        if (w == null || ballPos == null) {
            return true;
        }
        Location eye = p.getEyeLocation();
        Vector to = ballPos.clone();
        double dist = eye.toVector().distance(to);
        if (dist < 0.35) {
            return true;
        }
        org.bukkit.util.RayTraceResult hit = w.rayTraceBlocks(
            eye, to.clone().subtract(eye.toVector()).normalize(), Math.max(0.1, dist - 0.30),
            org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }


    private void checkPlayerBallInterceptionAndRebounds() {
        UUID passerOrShooter = ball.lastTouchId();
        TeamSide lastTouchTeam = passerOrShooter != null ? getTeam(passerOrShooter) : null;

        for (Player p : onlineParticipants()) {
            TeamSide myTeam = getTeam(p.getUniqueId());
            if (myTeam == TeamSide.SPECTATOR) {
                continue;
            }

            if (ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT
                && lastTouchTeam != null
                && myTeam == lastTouchTeam.opposite()
                && p.getLocation().clone().add(0, 1.0, 0).toVector().distanceSquared(ball.position()) <= 1.55 * 1.55
                && hasClearPath(p, ball.position())) {
                PlayerProfile defProf = statsManager.getOrCreate(p);
                MatchBoxScore defBox = getBoxScore(p.getUniqueId());
                defBox.steals++;
                defProf.incrementSteals();
                if (plugin.progressService() != null) {
                    plugin.progressService().record(defProf, fr.bosketball.model.ProgressStat.STEALS, 1);
                }
                if (plugin.tutorialManager() != null) {
                    plugin.tutorialManager().notifyAction(p, "defense", null);
                }
                if (mode.competitiveTeamMatch() && state == State.LIVE) {
                    defProf.addEarnedCoins(4);
                    defBox.coinsDelta += 4;
                }
                resetShotClock(false);
                ball.attachToHolder(p, defProf.selectedBallSkin(), defBox.hotStreak >= 3);
                Msg.playPackSound(p.getLocation(), "shoe.squeak", 1.0f, 1.24f);
                Player interceptedPasser = ball.lastPasserId() != null
                    ? Bukkit.getPlayer(ball.lastPasserId()) : null;
                Msg.rail(p, fr.bosketball.util.HudSurface.CONTEST, fr.bosketball.util.HudCopy.interceptWon(
                    interceptedPasser != null ? interceptedPasser.getName() : null));
                Player passerOfRecord = ball.lastPasserId() != null ? Bukkit.getPlayer(ball.lastPasserId()) : null;
                if (passerOfRecord != null && passerOfRecord.isOnline() && !passerOfRecord.equals(p)) {
                    // Round 66: only the interceptor was told. The player whose ball it was heard
                    // nothing -- the one person who needs to know the pass was read.
                    Msg.rail(passerOfRecord, fr.bosketball.util.HudSurface.CONTEST,
                        fr.bosketball.util.HudCopy.interceptLost(p.getName()));
                }
                return;
            }

            if (ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT && !GroundCheck.isStanding(p)
                && !p.getUniqueId().equals(ball.lastTouchId())) {
                Vector targetRim = (mode == GameModeType.PRACTICE || state == State.WAITING)
                    ? selectClosestOrOffensiveRim(p, myTeam)
                    : arena.targetRimForTeam(myTeam);
                if (p.getLocation().toVector().distanceSquared(targetRim) <= 2.9 * 2.9
                    && p.getEyeLocation().toVector().distanceSquared(ball.position()) <= 1.85 * 1.85) {
                    PlayerProfile prof = statsManager.getOrCreate(p);
                    MatchBoxScore box = getBoxScore(p.getUniqueId());
                    ball.attachToHolder(p, prof.selectedBallSkin(), box.hotStreak >= 3);
                    syncBallHotbarItems(true);
                    Msg.rail(p, fr.bosketball.util.HudSurface.CONTEST, "<" + Palette.WARN
                    + "><bold>Caught it</bold></" + Palette.WARN + ">" + fr.bosketball.util.HudCopy.SEP
                    + "<" + Palette.MUTED + ">aim at the rim and slam</" + Palette.MUTED + ">");
                    Msg.playPackSound(p.getLocation(), "ball.bounce", 0.9f, 1.28f);
                    return;
                }
            }

            if (ball.canBePickedUpBy(p)) {
                UUID prevHolder = ball.lastTouchId();
                boolean wasMissedShot = (prevHolder != null && !prevHolder.equals(p.getUniqueId()));
                // Catching a pass is not a rebound. In pass flight the ball is at chest height,
                // which used to pass the "in the air" test, so every completed pass counted as a
                // board for the receiver and reset the shot clock to fourteen.
                boolean fromPassFlight = ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT;
                PlayerProfile prof = statsManager.getOrCreate(p);
                MatchBoxScore box = getBoxScore(p.getUniqueId());

                if (wasMissedShot && !fromPassFlight
                    && ball.position().getY() > arena.geometry().floorY() + 0.65) {
                    box.rebounds++;
                    prof.incrementRebounds();
                    TeamSide prevTeam = getTeam(prevHolder);
                    boolean offensiveRebound = (prevTeam == myTeam);
                    resetShotClock(offensiveRebound);
                    // Round 53: the board was counted and paid for, but the player who took it was
                    // never told. A rebound is the one stat nobody notices they earned.
                    Msg.rail(p, fr.bosketball.util.HudSurface.CONTEST,
                    (offensiveRebound
                        ? "<" + Palette.TEXT + "><bold>Your board</bold></" + Palette.TEXT + ">"
                            + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED + ">second chance</"
                            + Palette.MUTED + ">"
                        : "<" + Palette.TEXT + "><bold>Rebound</bold></" + Palette.TEXT + ">"
                            + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED + ">ball is yours</"
                            + Palette.MUTED + ">")
                        + fr.bosketball.util.HudCopy.SEP + "<" + Palette.SUCCESS + ">+1 REB</"
                        + Palette.SUCCESS + ">");
                } else if (wasMissedShot && !fromPassFlight
                    && prevHolder != null && getTeam(prevHolder) == myTeam) {
                    // A loose ball scooped off the floor by the team that missed it. The shot
                    // clock used to freeze at the moment of the release, so this pickup inherited
                    // whatever fraction of a second was left and buzzed instantly.
                    resetShotClock(true);
                } else if (prevHolder != null && getTeam(prevHolder) != myTeam) {
                    // Round 78: the loose ball told the taker and nobody else. Half the steals
                    // in this game are a loose ball scooped up, and the victim never heard it.
                    Player scooped = Bukkit.getPlayer(prevHolder);
                    if (scooped != null && scooped.isOnline() && !scooped.equals(p)) {
                        Msg.rail(scooped, fr.bosketball.util.HudSurface.CONTEST, "<" + Palette.WARN
                            + "><bold>Ball lost</bold></" + Palette.WARN + ">"
                            + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED + ">"
                            + fr.bosketball.util.HudCopy.name(p.getName()) + " took it</"
                            + Palette.MUTED + ">");
                    }
                    resetShotClock(false);
                }

                boolean onFire = box.hotStreak >= plugin.settings().match().onFireStreakThreshold();
                ball.attachToHolder(p, prof.selectedBallSkin(), onFire);
                activeShotCharges.remove(p.getUniqueId());

                if (mode == GameModeType.THREE_POINT_CONTEST) {
                    advanceContestShot(p, false);
                }
                return;
            }
        }
    }

    private boolean isPlayableState() {
        return state == State.LIVE || state == State.WAITING;
    }

    public boolean hasCustomTeamName(TeamSide side) {
        return resolveCustomTeamName(side) != null;
    }

    public String teamDisplayName(TeamSide side) {
        String custom = resolveCustomTeamName(side);
        if (custom != null) {
            return custom;
        }
        return side == TeamSide.FROST ? "Frost" : "Ignis";
    }

    public String teamUpperLabel(TeamSide side) {
        String custom = resolveCustomTeamName(side);
        if (custom != null) {
            return custom.toUpperCase(java.util.Locale.ROOT);
        }
        return side == TeamSide.FROST ? "FROST" : "IGNIS";
    }

    private String resolveCustomTeamName(TeamSide side) {
        if (plugin.socialManager() == null || side == null || side == TeamSide.SPECTATOR) {
            return null;
        }
        List<UUID> sideRoster = new ArrayList<>();
        for (var e : participants.entrySet()) {
            if (e.getValue() == side) {
                sideRoster.add(e.getKey());
            }
        }
        return plugin.socialManager().resolveCustomTeamNameForRoster(sideRoster);
    }

    /** True while the plugin owns a live jump-shot charge for this player. */
    public boolean isChargingShot(Player player) {
        return player != null && activeShotCharges.containsKey(player.getUniqueId());
    }

    /** Cancel a live charge (used when the ball leaves the player or the state changes). */
    public void cancelShotCharge(Player player) {
        if (player == null) {
            return;
        }
        activeShotCharges.remove(player.getUniqueId());
        if (ball != null) {
            ball.setHolderShotChargeProgress(-1.0);
        }
        if (player.isHandRaised()) {
            player.clearActiveItem();
        }
    }

    public boolean canBeginBowDraw(Player player) {
        return isPlayableState()
            && ball != null
            && ball.mode() == BasketballEntity.BallMode.DRIBBLING
            && player.getUniqueId().equals(ball.holderId())
            && player.getInventory().getHeldItemSlot() == 0;
    }

    public void tryLockDunkOnRightClick(Player player) {
        if (ball != null
            && ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT
            && player.getUniqueId().equals(ball.holderId())
            && ball.dunkProgress() >= 0.22) {
            lockPlayerDunkTiming(player);
        }
    }

    public boolean handleBowShotRelease(Player player, double bowForce) {
        if (!isPlayableState() || ball == null || !player.getUniqueId().equals(ball.holderId())) {
            activeShotCharges.remove(player.getUniqueId());
            return false;
        }
        if (ball.mode() != BasketballEntity.BallMode.DRIBBLING) {
            activeShotCharges.remove(player.getUniqueId());
            return false;
        }
        ShotChargeState tracked = activeShotCharges.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        double timerPower = tracked != null
            ? fr.bosketball.physics.ShotChargeRules.progressForMs(tracked.heldMs(now))
            : 0.0;

        if (shotLockTicks > 0) {
            // The release lands inside the post-action lock: keep the charge alive instead of
            // silently eating the shot the player just charged.
            if (tracked != null) {
                tracked.progress = Math.max(tracked.progress, timerPower);
            }
            return false;
        }

        activeShotCharges.remove(player.getUniqueId());
        ball.setHolderShotChargeProgress(-1.0);
        if (!swungThisTick.add(player.getUniqueId())) {
            return false;
        }
        double trackedPower = Math.max(tracked != null ? tracked.progress : 0.0, timerPower);
        double clampedPower = fr.bosketball.physics.ShotChargeRules.resolvePower(
            tracked != null || timerPower > 0.0, trackedPower, bowForce
        );
        executeJumpShotRelease(player, clampedPower);
        return true;
    }

    public boolean handleBallHandlerPrimaryUse(Player player, boolean fromBallSlot) {
        return handleBallHandlerPrimaryUse(player, fromBallSlot, false);
    }

    public boolean handleBallHandlerPrimaryUse(Player player, boolean fromBallSlot, boolean dedicatedDunkSlot) {
        if (!isPlayableState() || ball == null) {
            return false;
        }

        if (ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT && player.getUniqueId().equals(ball.holderId())) {
            if (ball.dunkProgress() >= 0.22) {
                lockPlayerDunkTiming(player);
            }
            return false;
        }

        if (!player.getUniqueId().equals(ball.holderId())) {
            return false;
        }

        if (dedicatedDunkSlot) {
            TeamSide team = getTeam(player.getUniqueId());
            Vector targetRim = resolveTargetRim(player, team);
            PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(
                player.getEyeLocation().toVector(),
                player.getEyeLocation().getDirection(),
                targetRim
            );

            Location dunkLoc = player.getLocation();
            boolean inPaintRange = mode != GameModeType.THREE_POINT_CONTEST
                && arena.geometry().isInDunkZone(
                    dunkLoc.getX(), dunkLoc.getZ(), targetRim, fr.bosketball.game.DunkRules.PAINT_RADIUS_BONUS);
            boolean aimedAtRim = fr.bosketball.game.DunkRules.aimedAtRim(aim.yawErrorDeg());

            if (!inPaintRange) {
                Msg.rail(player, fr.bosketball.util.HudSurface.COACH, fr.bosketball.util.HudCopy.coachStepInside());
                return false;
            }
            if (!aimedAtRim) {
                Msg.rail(player, fr.bosketball.util.HudSurface.COACH, fr.bosketball.util.HudCopy.coachFaceTheRim());
                return false;
            }

            long dunkNow = System.currentTimeMillis();
            long dunkCooldownLeft = fr.bosketball.game.DunkRules.cooldownRemainingMs(
                dunkNow, dunkReadyAtMillis.getOrDefault(player.getUniqueId(), 0L));
            if (dunkCooldownLeft > 0L) {
                Msg.rail(player, fr.bosketball.util.HudSurface.COACH, fr.bosketball.util.HudCopy.coachDunkRecharging(
                    fr.bosketball.game.DunkRules.cooldownLabel(dunkCooldownLeft)));
                return false;
            }

            // Single click, decided now: no green-zone mini-game, no jump requirement.
            dunkReadyAtMillis.put(player.getUniqueId(), dunkNow + fr.bosketball.game.DunkRules.COOLDOWN_MS);
            cancelShotCharge(player);
            PlayerProfile prof = statsManager.getOrCreate(player);
            double dist = Math.hypot(dunkLoc.getX() - targetRim.getX(), dunkLoc.getZ() - targetRim.getZ());
            double dunkContest = computeLiveContestRatio(player, team);
            if (isDunkLaneBlockedByDefender(player, team, targetRim)) {
                dunkContest = Math.min(1.0, dunkContest + 0.35);
            }
            boolean dunkSuccess = rng.nextDouble() < fr.bosketball.game.DunkRules.successChance(
                dist, dunkContest, fatigueOf(player.getUniqueId()), !GroundCheck.isStanding(player));
            swungThisTick.add(player.getUniqueId());

            ball.startSlamDunk(
                player, targetRim, prof.selectedDunkStyle(),
                fr.bosketball.game.DunkRules.flightTicks(dist), dunkSuccess);
            ball.lockDunkTiming(dunkSuccess);
            triggerRigAction(player.getUniqueId(), AnimationKinematics.ActionClip.SLAM_DUNK, prof.selectedDunkStyle());
            Msg.playPersonalPackSound(player, dunkSuccess ? "game.dunk_boom" : "ui.click",
                dunkSuccess ? 1.1f : 0.7f, dunkSuccess ? 1.0f : 0.8f);
            Msg.rail(player, fr.bosketball.util.HudSurface.CONTEST, dunkSuccess
                ? "<" + Palette.SUCCESS + "><bold>" + prof.selectedDunkStyle().displayName()
                    + "</bold></" + Palette.SUCCESS + ">" + fr.bosketball.util.HudCopy.SEP
                    + "<" + Palette.MUTED + ">threw it down</" + Palette.MUTED + ">"
                : "<" + Palette.GOLD + "><bold>" + prof.selectedDunkStyle().displayName()
                    + " contested</bold></" + Palette.GOLD + ">" + fr.bosketball.util.HudCopy.SEP
                    + "<" + Palette.MUTED + ">he met you in the air</" + Palette.MUTED + ">");
            return false;
        }

        return canBeginBowDraw(player) && fromBallSlot;
    }

    private void lockPlayerDunkTiming(Player dunker) {
        if (ball == null || ball.isDunkTimingLocked()) {
            return;
        }
        double prog = ball.dunkProgress();
        TeamSide team = getTeam(dunker.getUniqueId());
        Vector targetRim = resolveTargetRim(dunker, team);
        PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(
            dunker.getEyeLocation().toVector(),
            dunker.getEyeLocation().getDirection(),
            targetRim
        );
        double contest = computeLiveContestRatio(dunker, team);
        double minGreen = contest >= 0.45 ? 0.63 : 0.56;
        double maxGreen = contest >= 0.45 ? 0.86 : 0.91;
        boolean stillAimedAtRim = Math.abs(aim.yawErrorDeg()) <= 26.0;
        boolean goodTiming = prog >= minGreen && prog <= maxGreen && stillAimedAtRim;
        ball.lockDunkTiming(goodTiming);
        Msg.sendHudMeter(dunker, Glyphs.composeDunkTimingHud(prog, true, goodTiming));
        if (goodTiming) {
            Msg.playPersonalPackSound(dunker, "game.green_release", 0.9f, 1.12f);
        } else {
            Msg.playPersonalPackSound(dunker, "ui.click", 0.8f, 0.72f);
        }
    }

    public boolean tryReleaseChargedShotNow(Player player) {
        if (shotLockTicks > 0 || ball == null || !player.getUniqueId().equals(ball.holderId())) {
            return false;
        }
        if (ball != null && ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT
            && player.getUniqueId().equals(ball.holderId())
            && ball.dunkProgress() >= 0.22) {
            lockPlayerDunkTiming(player);
            return true;
        }
        ShotChargeState charge = activeShotCharges.remove(player.getUniqueId());
        if (charge == null) {
            return false;
        }
        if (ball != null) {
            ball.setHolderShotChargeProgress(-1.0);
        }
        swungThisTick.add(player.getUniqueId());
        executeJumpShotRelease(player, charge.progress);
        return true;
    }

    private void tickShotChargesAndLiveAimHud() {
        long hudBudgetStarted = fr.bosketball.util.TickProfiler.beginBudget();
        try {
            tickShotChargesAndLiveAimHudMeasured();
        } finally {
            fr.bosketball.util.TickProfiler.endBudget("hud", hudBudgetStarted);
        }
    }

    private void tickShotChargesAndLiveAimHudMeasured() {
        if (shotLockTicks > 0) {
            shotLockTicks--;
        }

        if (ball != null && ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT && ball.holderId() != null) {
            Player dunker = Bukkit.getPlayer(ball.holderId());
            if (dunker != null && dunker.isOnline()) {
                Msg.sendHudMeter(dunker, Glyphs.composeDunkSlamHud(
                    ball.dunkProgress(), ball.willDunkSucceed()
                ));
            }
        }

        if (ball != null && ball.mode() == BasketballEntity.BallMode.DRIBBLING && ball.holderId() != null) {
            UUID holderId = ball.holderId();
            Player holder = Bukkit.getPlayer(holderId);
            boolean activelyDrawingBow = holder != null
                && holder.isOnline()
                && holder.isHandRaised()
                && holder.getInventory().getHeldItemSlot() == 0
                && holder.getActiveItem().getType() == Material.BOW;

            boolean ballBowInHand = holder != null
                && holder.isOnline()
                && holder.getInventory().getHeldItemSlot() == 0
                && holder.getInventory().getItemInMainHand().getType() == Material.BOW;

            ShotChargeState liveCharge = activeShotCharges.get(holderId);
            if (liveCharge != null) {
                liveCharge.lostHandTicks = activelyDrawingBow ? 0 : liveCharge.lostHandTicks + 1;
            }

            // The charge belongs to the plugin, not to the vanilla bow draw: losing the draw
            // (item sync, cancelled packet, arena teleport...) only opens a short grace window
            // instead of dropping the bar the player is still charging.
            boolean holdingFullDraw = !activelyDrawingBow
                && liveCharge != null
                && ballBowInHand
                && liveCharge.lostHandTicks <= fr.bosketball.physics.ShotChargeRules.RELEASE_GRACE_TICKS;

            if (activelyDrawingBow || holdingFullDraw) {
                if (shotLockTicks > 0) {

                    return;
                }
                ShotChargeState st = activeShotCharges.computeIfAbsent(holderId, k -> {
                    Msg.playPersonalPackSound(holder, "ui.click", 0.55f, 0.95f);
                    return new ShotChargeState();
                });
                int usedTicks = Math.max(1, Math.max(
                    activelyDrawingBow ? holder.getHandRaisedTime() : 0,
                    fr.bosketball.physics.ShotChargeRules.heldTicks(st.heldMs(System.currentTimeMillis()))
                ));
                double bowPower = fr.bosketball.physics.ShotChargeRules.progressForTicks(usedTicks);
                st.ticksActive++;
                st.progress = bowPower;
                ball.setHolderShotChargeProgress(bowPower);

                if (!st.playedFullDrawSound && bowPower >= 0.86) {
                    st.playedFullDrawSound = true;
                    Msg.playPersonalPackSound(holder, "ui.click", 0.55f, 1.38f);
                }

                TeamSide team = getTeam(holderId);
                Vector targetRim = resolveTargetRim(holder, team);
                Vector lookDir = holder.getEyeLocation().getDirection();
                PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(
                    holder.getEyeLocation().toVector(), lookDir, targetRim
                );
                boolean isThree = arena.geometry().isThreePointShot(holder.getLocation().getX(), holder.getLocation().getZ(), targetRim);
                double contest = computeLiveContestRatio(holder, team);

                Msg.sendHudMeter(holder, Glyphs.composeProAimHud(
                    bowPower, false, null, aim.aimingAtHoop(), aim.yawErrorDeg(), aim.horizontalDist(), isThree, contest
                ));

                if (st.ticksActive % 2 == 1 && statsManager.getOrCreate(holder).settings().aimTrajectory()) {
                    renderPersonalAimArcRibbon(holder, targetRim, lookDir, aim, bowPower, contest);
                }
                return;
            }

            if (!activeShotCharges.isEmpty()) {
                activeShotCharges.clear();
                ball.setHolderShotChargeProgress(-1.0);
            }

            if (holder != null && holder.isOnline() && tickCounter % 3 == 0) {
                TeamSide team = getTeam(holderId);
                Vector targetRim = resolveTargetRim(holder, team);
                PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(
                    holder.getEyeLocation().toVector(),
                    holder.getEyeLocation().getDirection(),
                    targetRim
                );
                boolean isThree = arena.geometry().isThreePointShot(holder.getLocation().getX(), holder.getLocation().getZ(), targetRim);
                boolean inDunk = mode != GameModeType.THREE_POINT_CONTEST
                    && arena.geometry().isInDunkZone(holder.getLocation().getX(), holder.getLocation().getZ(), targetRim);
                double contest = computeLiveContestRatio(holder, team);

                Msg.sendHudMeter(holder, fr.bosketball.util.HudSurface.AMBIENT, Glyphs.composeIdleAimCompass(
                    aim.aimingAtHoop(), aim.yawErrorDeg(), aim.horizontalDist(), isThree, inDunk, contest
                ));

                if (aim.aimingAtHoop() && Math.abs(aim.yawErrorDeg()) <= 5.5
                    && statsManager.getOrCreate(holder).settings().aimTrajectory()) {
                    spawnPersonalRimHalo(holder, targetRim, Math.abs(aim.yawErrorDeg()) <= 2.6
                        ? Palette.rgb(Palette.SUCCESS)
                        : Palette.rgb(Palette.ICE));
                }
            }
            return;
        }

        if (!activeShotCharges.isEmpty()) {
            activeShotCharges.clear();
            if (ball != null) {
                ball.setHolderShotChargeProgress(-1.0);
            }
        }
    }

    private void renderPersonalAimArcRibbon(
        Player shooter,
        Vector targetRim,
        Vector lookDir,
        PhysicsMath.AimEvaluation aim,
        double chargeProgress,
        double contestRatio
    ) {
        var phys = plugin.settings().physics();
        double g = phys.gravity();
        double drag = phys.airDrag();
        Vector start = releasePoint(shooter, lookDir, 0.45);
        MatchBoxScore box = getBoxScore(shooter.getUniqueId());
        boolean onFire = box.hotStreak >= plugin.settings().match().onFireStreakThreshold();
        ShotReleaseQuality previewQuality = ShotReleaseQuality.evaluate(chargeProgress, aim.horizontalDist(), onFire);

        Vector previewVel = PhysicsMath.solveSkillShotVelocity(
            start,
            lookDir,
            targetRim,
            arena.geometry().hoopForwardForRim(targetRim),
            aim,
            chargeProgress,
            g,
            drag,
            previewQuality,
            contestRatio,
            null
        );

        List<Vector> curve = PhysicsMath.simulatePreviewArc(
            start, previewVel, g, drag, arena.geometry().floorY(), 26
        );

        Color arcColor;
        if (!aim.aimingAtHoop()) {
            arcColor = Palette.rgb(Palette.DANGER);
        } else if (previewQuality == ShotReleaseQuality.PERFECT_GREEN && Math.abs(aim.yawErrorDeg()) <= 2.8 && contestRatio < 0.35) {
            arcColor = Palette.rgb(Palette.SUCCESS);
        } else if (Math.abs(aim.yawErrorDeg()) <= 4.8 && contestRatio < 0.45) {
            arcColor = Palette.rgb(Palette.ICE);
        } else {
            arcColor = Palette.rgb(Palette.WARN);
        }

        Particle.DustOptions dust = new Particle.DustOptions(arcColor, 0.72f);
        for (int i = 1; i < curve.size(); i += 2) {
            Vector pt = curve.get(i);
            shooter.spawnParticle(Particle.DUST, pt.getX(), pt.getY(), pt.getZ(), 1, 0, 0, 0, 0, dust);
        }
        if (aim.aimingAtHoop()) {
            spawnPersonalRimHalo(shooter, targetRim, arcColor);
        }
    }

    private void spawnPersonalRimHalo(Player viewer, Vector rim, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.85f);
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0 * i) / 8.0 + (tickCounter * 0.14);
            viewer.spawnParticle(
                Particle.DUST,
                rim.getX() + Math.cos(angle) * PhysicsMath.RIM_RADIUS,
                rim.getY() + 0.04,
                rim.getZ() + Math.sin(angle) * PhysicsMath.RIM_RADIUS,
                1, 0, 0, 0, 0, dust
            );
        }
    }

    private void executeJumpShotRelease(Player shooter, double chargeProgress) {

        if (ball == null
            || !shooter.getUniqueId().equals(ball.holderId())
            || shotLockTicks > 0
            || ball.mode() != BasketballEntity.BallMode.DRIBBLING) {
            return;
        }
        shotLockTicks = SHOT_LOCK_TICKS;
        TeamSide team = getTeam(shooter.getUniqueId());
        Vector targetRim = resolveTargetRim(shooter, team);
        Vector lookDir = shooter.getEyeLocation().getDirection();

        Location loc = shooter.getLocation();
        PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(
            shooter.getEyeLocation().toVector(), lookDir, targetRim
        );
        double dist = aim.horizontalDist();
        boolean isThree = arena.geometry().isThreePointShot(loc.getX(), loc.getZ(), targetRim);
        MatchBoxScore box = getBoxScore(shooter.getUniqueId());
        boolean onFire = box.hotStreak >= plugin.settings().match().onFireStreakThreshold();
        double fatigue = fatigueOf(shooter.getUniqueId());
        double contest = Math.min(1.0, computeLiveContestRatio(shooter, team) + fatigue * FATIGUE_CONTEST_WEIGHT);

        double adjustedCharge = chargeProgress;
        if (fatigue > 0.0) {
            adjustedCharge -= FATIGUE_MAX_CHARGE_PENALTY * fatigue;
        }
        if (contest >= 0.45 && chargeProgress < 0.88) {
            adjustedCharge -= 0.07 * contest;
        }

        ShotReleaseQuality quality = ShotReleaseQuality.evaluate(adjustedCharge, dist, onFire);

        if (!aim.aimingAtHoop() || Math.abs(aim.yawErrorDeg()) > 5.2) {
            if (quality == ShotReleaseQuality.PERFECT_GREEN) {
                quality = ShotReleaseQuality.SLIGHTLY_LATE;
            }
        }

        Msg.sendHudMeter(shooter, Glyphs.composeProAimHud(
            chargeProgress, true, quality, aim.aimingAtHoop(), aim.yawErrorDeg(), dist, isThree, contest
        ));

        if (aim.aimingAtHoop() && quality == ShotReleaseQuality.PERFECT_GREEN && Math.abs(aim.yawErrorDeg()) <= 2.8) {
            Msg.playPersonalPackSound(shooter, "game.green_release", 1.0f, 1.05f);
            shooter.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                shooter.getLocation().add(0, 2.05, 0),
                12, 0.28, 0.18, 0.28, 0.02
            );
        }

        if (GroundCheck.isStanding(shooter)) {
            Vector v = shooter.getVelocity();
            shooter.setVelocity(new Vector(v.getX() * 0.62, 0.29, v.getZ() * 0.62));
        }

        var phys = plugin.settings().physics();
        Vector start = releasePoint(shooter, lookDir, 0.45);
        Vector velocity = PhysicsMath.solveSkillShotVelocity(
            start,
            lookDir,
            targetRim,
            arena.geometry().hoopForwardForRim(targetRim),
            aim,
            chargeProgress,
            phys.gravity(),
            phys.airDrag(),
            quality,
            contest,
            rng
        );

        if (state == State.LIVE && aim.aimingAtHoop()) {
            PlayerProfile prof = statsManager.getOrCreate(shooter);
            if (isThree) {
                prof.setThreePointersAttempted(prof.threePointersAttempted() + 1);
            } else {
                prof.setTwoPointersAttempted(prof.twoPointersAttempted() + 1);
            }
            statsManager.markDirty(shooter.getUniqueId());
        }

        ball.launchShot(shooter, start, velocity, isThree, dist);
        triggerRigAction(shooter.getUniqueId(), AnimationKinematics.ActionClip.THROW_SHOT, null);

        if (mode == GameModeType.THREE_POINT_CONTEST) {
            int expectedSeq = this.contestShotSequence;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Wait out the whole flight, not most of it: a deep ball can stay in the air for
                // over two seconds, and the old 34-tick retry handed the shooter the next ball
                // while the previous one was still on its way to the rim.
                if (state == State.LIVE
                    && this.contestShotSequence == expectedSeq
                    && shooter.isOnline()
                    && ball != null
                    && ball.holderId() == null
                    && ball.mode() != BasketballEntity.BallMode.IN_SHOT_FLIGHT) {
                    advanceContestShot(shooter, false);
                }
            }, 60L);
        }
    }

    public void handlePassOrAlleyOop(Player passer) {
        if (!isPlayableState() || ball == null || !passer.getUniqueId().equals(ball.holderId())) {
            return;
        }
        activeShotCharges.remove(passer.getUniqueId());
        ball.setHolderShotChargeProgress(-1.0);
        if (passer.isHandRaised()) {
            passer.clearActiveItem();
        }
        TeamSide myTeam = getTeam(passer.getUniqueId());

        Player targetMate = null;
        double bestDot = 0.55;
        Vector eyeDir = passer.getEyeLocation().getDirection().normalize();

        for (Player mate : onlineParticipants()) {
            if (mate.getUniqueId().equals(passer.getUniqueId()) || getTeam(mate.getUniqueId()) != myTeam) {
                continue;
            }
            Vector toMate = mate.getEyeLocation().toVector().subtract(passer.getEyeLocation().toVector());
            if (toMate.lengthSquared() < 0.5) {
                continue;
            }
            double dot = toMate.clone().normalize().dot(eyeDir);
            if (dot > bestDot) {
                bestDot = dot;
                targetMate = mate;
            }
        }

        Vector targetRim = resolveTargetRim(passer, myTeam);
        PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(
            passer.getEyeLocation().toVector(), eyeDir, targetRim
        );

        // A lob is only a lob past half court. Thrown from your own half it was a free pass over
        // every defender that landed on the rim, and on an open-air court with no roof over the
        // play there was nothing that could ever stop it.
        boolean alleyOop = passer.getLocation().getPitch() < -22.0f
            && Math.abs(aim.yawErrorDeg()) <= 28.0
            && (!isLiveTeamPlay() || isAttackingHalf(passer, targetRim));
        if (!alleyOop && isLiveTeamPlay() && passer.getLocation().getPitch() < -22.0f
            && Math.abs(aim.yawErrorDeg()) <= 28.0) {
            Msg.rail(passer, fr.bosketball.util.HudSurface.PROGRESS, "<" + Palette.MUTED
                + ">Lob needs the attacking half</" + Palette.MUTED + ">");
        }
        Vector start = releasePoint(passer, eyeDir, 0.55);
        Vector passVel;

        if (alleyOop) {
            var phys = plugin.settings().physics();
            Vector lobTarget = targetRim.clone().add(new Vector(0, 0.55, 0));
            // Aim still matters: a sloppy lob drifts off the iron instead of being solved exactly.
            double aimError = Math.abs(aim.yawErrorDeg());
            if (aimError > 6.0) {
                Vector toRim = targetRim.clone().subtract(start);
                toRim.setY(0);
                Vector across = toRim.lengthSquared() > 1e-6
                    ? new Vector(-toRim.getZ(), 0, toRim.getX()).normalize()
                    : new Vector(1, 0, 0);
                double drift = Math.min(1.15, (aimError - 6.0) / 22.0 * 1.15);
                double side = (passer.getUniqueId().hashCode() & 1) == 0 ? 1.0 : -1.0;
                lobTarget = lobTarget.add(across.multiply(drift * side));
            }
            passVel = PhysicsMath.solveBallisticArc(
                start, lobTarget,
                phys.gravity(),
                phys.airDrag(),
                ShotReleaseQuality.PERFECT_GREEN, null
            );
            Msg.rail(passer, fr.bosketball.util.HudSurface.PROGRESS, "<" + Palette.GOLD
                + "><bold>Lob up</bold></" + Palette.GOLD + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.MUTED + ">jump and slam</" + Palette.MUTED + ">");
            if (targetMate != null && !targetMate.equals(passer)) {
                Msg.rail(targetMate, fr.bosketball.util.HudSurface.PROGRESS, "<" + Palette.GOLD
                    + "><bold>Lob from " + fr.bosketball.util.HudCopy.name(passer.getName())
                    + "</bold></" + Palette.GOLD + ">" + fr.bosketball.util.HudCopy.SEP
                    + "<" + Palette.MUTED + ">jump and slam</" + Palette.MUTED + ">");
            }
        } else if (targetMate != null) {
            Vector aimChest = targetMate.getLocation().toVector().add(new Vector(0, 1.15, 0)).add(targetMate.getVelocity().multiply(6.0));
            Vector delta = aimChest.subtract(start);
            double speed = Math.min(1.35, Math.max(0.78, delta.length() * 0.14));
            passVel = delta.normalize().multiply(speed).add(new Vector(0, 0.08, 0));
            Msg.rail(passer, fr.bosketball.util.HudSurface.PROGRESS,
                fr.bosketball.util.HudCopy.passTo(targetMate.getName()));
        } else {
            passVel = eyeDir.clone().multiply(0.95).add(new Vector(0, 0.10, 0));
            Msg.rail(passer, fr.bosketball.util.HudSurface.PROGRESS, fr.bosketball.util.HudCopy.passToNobody());
        }

        ball.launchPass(passer, start, passVel, alleyOop);
        triggerRigAction(passer.getUniqueId(), AnimationKinematics.ActionClip.THROW_PASS, null);
    }

    public void handleCrossoverMove(Player handler) {
        if (!isPlayableState() || ball == null || !handler.getUniqueId().equals(ball.holderId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long cd = plugin.settings().physics().crossoverCooldownMs();
        long sinceLast = now - crossoverCooldowns.getOrDefault(handler.getUniqueId(), 0L);
        if (sinceLast < cd) {
            Msg.rail(handler, fr.bosketball.util.HudSurface.COACH, fr.bosketball.util.HudCopy.coachDashRecharging(
                fr.bosketball.game.DunkRules.cooldownLabel(cd - sinceLast)));
            return;
        }
        crossoverCooldowns.put(handler.getUniqueId(), now);

        ball.triggerCrossoverHandSwitch(handler);
        triggerRigAction(handler.getUniqueId(), AnimationKinematics.ActionClip.CROSSOVER, null);

        // Directional dash: the burst follows the steering input (WASD) when the player is
        // moving, and the crosshair otherwise — no more fixed forward-plus-side burst.
        Vector dashDir = fr.bosketball.physics.MovementMath.dashDirection(
            handler.getVelocity(), handler.getLocation().getDirection());
        if (dashDir.lengthSquared() < 1.0e-6) {
            double yawRad = Math.toRadians(handler.getLocation().getYaw());
            dashDir = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
        }
        handler.setVelocity(fr.bosketball.physics.MovementMath.dashVelocity(dashDir, DASH_STRENGTH, 0.15));

        if (sparringBot != null && sparringBot.tryBreakAnkles(handler)) {
            Msg.rail(handler, fr.bosketball.util.HudSurface.CONTEST,
                fr.bosketball.util.HudCopy.ankleWon(null));
        }

        TeamSide myTeam = getTeam(handler.getUniqueId());
        for (Player def : onlineParticipants()) {
            if (getTeam(def.getUniqueId()) == myTeam.opposite()
                && def.getLocation().distanceSquared(handler.getLocation()) <= 3.0 * 3.0) {
                def.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 32, 2, false, false, true));
                def.getWorld().spawnParticle(Particle.CLOUD, def.getLocation().add(0, 0.2, 0), 16, 0.3, 0.1, 0.3, 0.06);
                Msg.rail(def, fr.bosketball.util.HudSurface.CONTEST,
                    fr.bosketball.util.HudCopy.ankleLost(handler.getName()));
                Msg.rail(handler, fr.bosketball.util.HudSurface.CONTEST,
                    fr.bosketball.util.HudCopy.ankleWon(def.getName()));
                if (state == State.LIVE) {
                    statsManager.getOrCreate(handler).incrementAnkleBreakers();
                }
            }
        }
    }

    public void handleDefensiveAction(Player defender) {
        if (!isPlayableState() || ball == null) {
            return;
        }

        if (ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT && defender.getUniqueId().equals(ball.holderId())) {
            lockPlayerDunkTiming(defender);
            return;
        }

        if (tryReleaseChargedShotNow(defender)) {
            if (defender.isHandRaised()) {
                defender.clearActiveItem();
            }
            return;
        }

        if (ball.mode() == BasketballEntity.BallMode.DRIBBLING
            && defender.getUniqueId().equals(ball.holderId())
            && !GroundCheck.isStanding(defender)
            && mode != GameModeType.THREE_POINT_CONTEST) {
            TeamSide handlerTeam = getTeam(defender.getUniqueId());
            Vector targetRim = resolveTargetRim(defender, handlerTeam);
            Location loc = defender.getLocation();
            if (arena.geometry().isInDunkZone(loc.getX(), loc.getZ(), targetRim)) {
                defender.swingMainHand();
                handleBallHandlerPrimaryUse(defender, false, true);
                return;
            }
        }

        TeamSide defTeam = getTeam(defender.getUniqueId());
        if (defTeam == TeamSide.SPECTATOR) {
            return;
        }
        long now = System.currentTimeMillis();
        defender.swingMainHand();

        boolean isOwnBall = defender.getUniqueId().equals(ball.lastTouchId())
            || defender.getUniqueId().equals(ball.shooterId())
            || defender.getUniqueId().equals(ball.holderId());
        boolean blockableFlight = !isOwnBall && (
            ball.mode() == BasketballEntity.BallMode.IN_SHOT_FLIGHT
                || ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT
                || ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT
        );

        if (blockableFlight
            && !GroundCheck.isStanding(defender)
            && defender.getEyeLocation().toVector().distanceSquared(ball.position()) <= 2.45 * 2.45) {

            if (mode.competitiveTeamMatch() && ball.lastTouchId() != null && getTeam(ball.lastTouchId()) == defTeam) {
                return;
            }
            long blkCd = plugin.settings().physics().blockCooldownMs();
            if (now - blockCooldowns.getOrDefault(defender.getUniqueId(), 0L) < blkCd) {
                return;
            }
            blockCooldowns.put(defender.getUniqueId(), now);

            if (ball.mode() == BasketballEntity.BallMode.IN_SHOT_FLIGHT) {
                Vector defRim = arena.targetRimForTeam(defTeam.opposite());
                if (ball.velocity().getY() < -0.04 && ball.position().distanceSquared(defRim) <= 1.15 * 1.15) {
                    Msg.rail(defender, fr.bosketball.util.HudSurface.RULE, "<" + Palette.DANGER
                + "><bold>No goaltending</bold></" + Palette.DANGER + ">" + fr.bosketball.util.HudCopy.SEP
                + "<" + Palette.MUTED + ">the ball is on its way down</" + Palette.MUTED + ">");
                    return;
                }
            }

            boolean stuffedDunk = (ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT);
            // Swatting a pass out of the air is a deflection, not a blocked shot. It used to count
            // as a block, which inflated the stat line and the MVP score of every defender who
            // jumped at a cross-court pass.
            boolean stuffedShot = ball.mode() == BasketballEntity.BallMode.IN_SHOT_FLIGHT || stuffedDunk;
            triggerRigAction(defender.getUniqueId(), AnimationKinematics.ActionClip.BLOCK_REACH, null);
            UUID stuffedShooterId = ball.lastTouchId();
            Player stuffedShooterPlayer = stuffedShooterId != null ? Bukkit.getPlayer(stuffedShooterId) : null;
            String stuffedShooterName = stuffedShooterPlayer != null ? stuffedShooterPlayer.getName() : "their shot";
            Vector swat = defender.getEyeLocation().getDirection().normalize().multiply(0.86).setY(0.30);
            ball.popLoose(swat, defender.getUniqueId());
            Msg.playPackSound(ball.position().toLocation(defender.getWorld()), "defense.block", 1.3f, stuffedDunk ? 0.86f : 0.96f);
            defender.getWorld().spawnParticle(Particle.SWEEP_ATTACK, ball.position().toLocation(defender.getWorld()), 2, 0.1, 0.1, 0.1, 0);
            defender.getWorld().spawnParticle(Particle.CRIT, ball.position().toLocation(defender.getWorld()), 22, 0.25, 0.25, 0.25, 0.16);

            MatchBoxScore defBox = getBoxScore(defender.getUniqueId());
            PlayerProfile defProf = statsManager.getOrCreate(defender);
            if (stuffedShot) {
                defBox.blocks++;
                defProf.incrementBlocks();
                if (plugin.progressService() != null) {
                    plugin.progressService().record(defProf, fr.bosketball.model.ProgressStat.BLOCKS, 1);
                }
            }
            if (stuffedShot && mode.competitiveTeamMatch() && state == State.LIVE) {
                int blkCoins = plugin.settings().economy().blockCoins();
                defProf.addEarnedCoins(blkCoins);
                defBox.coinsDelta += blkCoins;
            }
            statsManager.markDirty(defender.getUniqueId());
            Msg.rail(defender, fr.bosketball.util.HudSurface.CONTEST, stuffedShot
                ? fr.bosketball.util.HudCopy.blockWon(stuffedShooterName, stuffedDunk)
                : "<" + Palette.ICE + "><bold>Deflected</bold></" + Palette.ICE + ">"
                    + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED
                    + ">pass broken up</" + Palette.MUTED + ">");
            broadcast("<" + Palette.ICE + ">" + (stuffedShot ? "▪ Block" : "▪ Deflection") + "</" + Palette.ICE + "> <#94A3B8>by</#94A3B8> <#F8FAFC>" + defender.getName() + "</#F8FAFC>");
            // Round 77: a stuffed shot told the defender and the arena, never the shooter --
            // the one player who needs to know why the ball left their hands.
            Player stuffedShooter = stuffedShooterId != null ? Bukkit.getPlayer(stuffedShooterId) : null;
            if (stuffedShooter != null && stuffedShooter.isOnline() && !stuffedShooter.equals(defender)) {
                Msg.rail(stuffedShooter, fr.bosketball.util.HudSurface.CONTEST,
                    fr.bosketball.util.HudCopy.blockLost(defender.getName(), stuffedDunk));
            }
            return;
        }

        if (ball.holderId() == null && ball.mode() == BasketballEntity.BallMode.LOOSE) {
            double distToBall = defender.getLocation().toVector().distance(ball.position());
            if (distToBall <= 2.45 && now - hustleDiveCooldowns.getOrDefault(defender.getUniqueId(), 0L) >= 1300L) {
                hustleDiveCooldowns.put(defender.getUniqueId(), now);
                PlayerVisualRig diveRig = visualRigs.get(defender.getUniqueId());
                if (diveRig != null) {
                    diveRig.triggerHustleDive();
                }
                Vector lunge = ball.position().clone().subtract(defender.getLocation().toVector()).setY(0);
                if (lunge.lengthSquared() > 1e-4) {
                    lunge.normalize().multiply(0.58).setY(0.16);
                    defender.setVelocity(lunge);
                }
                Msg.playPackSound(defender.getLocation(), "shoe.squeak", 0.85f, 1.18f);
                if (distToBall <= 1.75) {
                    PlayerProfile prof = statsManager.getOrCreate(defender);
                    ball.attachToHolder(defender, prof.selectedBallSkin(), getBoxScore(defender.getUniqueId()).hotStreak >= 3);
                    Msg.rail(defender, fr.bosketball.util.HudSurface.CONTEST,
                        "<" + Palette.SUCCESS + "><bold>Dove on it</bold></" + Palette.SUCCESS + ">"
                            + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED + ">ball is yours</"
                            + Palette.MUTED + ">");
                }
            }
            return;
        }

        UUID holderId = ball.holderId();
        if (holderId != null && !holderId.equals(defender.getUniqueId()) && ball.mode() == BasketballEntity.BallMode.DRIBBLING) {
            UUID defenderId = defender.getUniqueId();
            TeamSide holderTeam = getTeam(holderId);
            // Round 27: a team-mate can never be stolen from, in casual modes too (before this was
            // only blocked for competitive matches, so casual 2v2 let you rob your own team-mate).
            if (holderTeam != null && holderTeam != TeamSide.SPECTATOR && holderTeam == defTeam) {
                return;
            }
            Player holder = Bukkit.getPlayer(holderId);
            if (holder == null) {
                return;
            }

            double playerDist = defender.getLocation().distance(holder.getLocation());
            double ballDist = defender.getEyeLocation().toVector().distance(ball.position());
            // Reach gate (StealRules): the old one used "playerDist > 2.95 && ballDist > 2.55",
            // so a defender six blocks away could strip the ball whenever the ball was close.
            if (!fr.bosketball.physics.StealRules.inReach(playerDist, ballDist)) {
                return;
            }

            // Consecutive clean steals decay after 6 s without a new one (the old code reset the
            // streak after 3 s of *no attempt at all* and incremented it on every attempt —
            // including misses — so spamming right-click permanently destroyed your own odds).
            int streak = fr.bosketball.physics.StealRules.decayStreak(
                stealStreak.getOrDefault(defenderId, 0),
                now - lastStealAttemptAt.getOrDefault(defenderId, 0L));
            stealStreak.put(defenderId, streak);
            long stlCd = fr.bosketball.physics.StealRules.cooldownMs(
                plugin.settings().physics().stealCooldownMs(), streak);
            if (now - stealCooldowns.getOrDefault(defenderId, 0L) < stlCd) {
                return;
            }
            stealCooldowns.put(defenderId, now);
            triggerRigAction(defenderId, AnimationKinematics.ActionClip.STEAL_SWIPE, null);

            double yawRad = Math.toRadians(holder.getLocation().getYaw());
            Vector holderRight = new Vector(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
            Vector holderToDef = defender.getLocation().toVector().subtract(holder.getLocation().toVector()).setY(0);
            boolean attackingExposedSide = holderToDef.lengthSquared() > 1e-4
                && ((holderToDef.normalize().dot(holderRight) >= 0.15) == ball.isRightHandDribble());

            // Single, well defined success chance (no more double clamping nor the extra x0.58).
            double chance = fr.bosketball.physics.StealRules.cleanChance(
                attackingExposedSide,
                holder.isSprinting(),
                activeShotCharges.containsKey(holderId),
                holder.isSneaking(),
                streak);
            double pokeChance = fr.bosketball.physics.StealRules.pokeChance(chance);

            int stlCoins = plugin.settings().economy().stealCoins();
            double roll = rng.nextDouble();
            fr.bosketball.physics.StealRules.Outcome outcome =
                fr.bosketball.physics.StealRules.resolve(roll, chance, pokeChance);
            if (outcome == fr.bosketball.physics.StealRules.Outcome.CLEAN_STEAL) {
                activeShotCharges.remove(holderId);
                ball.setHolderShotChargeProgress(-1.0);
                if (holder.isHandRaised()) {
                    holder.clearActiveItem();
                }

                PlayerProfile defProf = statsManager.getOrCreate(defender);
                MatchBoxScore defBox = getBoxScore(defenderId);
                defBox.steals++;
                defProf.incrementSteals();
                if (plugin.progressService() != null) {
                    plugin.progressService().record(defProf, fr.bosketball.model.ProgressStat.STEALS, 1);
                }
                if (mode.competitiveTeamMatch() && state == State.LIVE) {
                    defProf.addEarnedCoins(stlCoins);
                    defBox.coinsDelta += stlCoins;
                }
                statsManager.markDirty(defenderId);
                getBoxScore(holderId).hotStreak = 0;
                stealStreak.put(defenderId, Math.min(fr.bosketball.physics.StealRules.MAX_STREAK, streak + 1));
                lastStealAttemptAt.put(defenderId, now);
                // The player who just lost the ball cannot steal it straight back.
                stealCooldowns.put(holderId, now + 700L);

                boolean onFire = defBox.hotStreak >= plugin.settings().match().onFireStreakThreshold();
                ball.attachToHolder(defender, defProf.selectedBallSkin(), onFire);
                resetShotClock(false);

                Msg.playPackSound(defender.getLocation(), "shoe.squeak", 1.0f, 1.22f);
                Msg.rail(holder, fr.bosketball.util.HudSurface.CONTEST,
                    fr.bosketball.util.HudCopy.stealLost(defender.getName()));
                Msg.rail(defender, fr.bosketball.util.HudSurface.CONTEST,
                    fr.bosketball.util.HudCopy.stealWon(holder.getName()));
            } else if (outcome == fr.bosketball.physics.StealRules.Outcome.POKED_LOOSE) {
                activeShotCharges.remove(holderId);
                ball.setHolderShotChargeProgress(-1.0);
                if (holder.isHandRaised()) {
                    holder.clearActiveItem();
                }

                Vector pokeDir = defender.getLocation().getDirection().setY(0).normalize()
                    .multiply(0.38).add(new Vector((rng.nextDouble() - 0.5) * 0.25, 0.25, (rng.nextDouble() - 0.5) * 0.25));
                ball.popLoose(pokeDir, defenderId);
                // Round 27: a deflection is NOT a steal. It no longer inflates steals stats nor
                // pays steal coins (before, both branches counted as a steal).
                getBoxScore(holderId).hotStreak = 0;
                lastStealAttemptAt.put(defenderId, now);
                stealCooldowns.put(holderId, now + 500L);

                Msg.playPackSound(ball.position().toLocation(defender.getWorld()), "ball.bounce", 1.0f, 1.26f);
                defender.getWorld().spawnParticle(Particle.CRIT, ball.position().toLocation(defender.getWorld()), 12, 0.2, 0.2, 0.2, 0.1);
                Msg.rail(holder, fr.bosketball.util.HudSurface.CONTEST,
                    fr.bosketball.util.HudCopy.pokeLost(defender.getName()));
                Msg.rail(defender, fr.bosketball.util.HudSurface.CONTEST,
                    fr.bosketball.util.HudCopy.pokeWon(holder.getName()));
            } else {
                if (rng.nextDouble() < 0.4) {
                    defender.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 0, false, false, false));
                }
                Msg.rail(defender, fr.bosketball.util.HudSurface.COACH,
                    holder.isSneaking()
                        ? "<" + Palette.GOLD + "><bold>Ball is covered</bold></" + Palette.GOLD + ">"
                            + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED + ">strip it</"
                            + Palette.MUTED + ">"
                        : "<" + Palette.GOLD + "><bold>No steal</bold></" + Palette.GOLD + ">"
                            + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED + ">stay in front of him</"
                            + Palette.MUTED + ">");
            }
        }
    }

    private int resolveShotValue(UUID shooterId, boolean ballFlag, TeamSide creditedTeam) {
        if (ball != null && ball.isSlamDunkFlight()) {
            return 2;
        }
        if (shooterId == null || creditedTeam == null) {
            return ballFlag ? 3 : 2;
        }
        Player shooter = Bukkit.getPlayer(shooterId);
        if (shooter == null) {
            return ballFlag ? 3 : 2;
        }
        // The rim that credited the side is that side's own rim. Measuring against the other
        // team's hoop put the "shooter" twenty-eight blocks from the iron, so every basket,
        // including a layup, came out worth three points.
        return arena.geometry().shotValueAt(
            shooter.getLocation().getX(), shooter.getLocation().getZ(), creditedTeam);
    }

    private double computeLiveContestRatio(Player shooter, TeamSide shooterTeam) {
        double maxContest = 0.0;
        if (sparringBot != null) {
            maxContest = Math.max(maxContest, sparringBot.computeContestAgainst(shooter));
        }

        if (botSquad != null && !botSquad.isEmpty()) {
            Vector shooterPos = shooter.getLocation().toVector();
            for (var avatar : botSquad.avatars()) {
                TeamSide botSide = botTeamMap.get(avatar.entityId());
                if (botSide == null || (mode.competitiveTeamMatch() && botSide == shooterTeam)) {
                    continue;
                }
                Vector bp = avatar.position();
                double d = Math.hypot(bp.getX() - shooterPos.getX(), bp.getZ() - shooterPos.getZ());
                if (d > 2.9) {
                    continue;
                }
                double c = ((2.9 - d) / 2.3) * 0.7 * avatar.profile().difficulty().contestIntensity();
                if (!avatar.mannequin().isOnGround()) {
                    c += 0.18;
                }
                maxContest = Math.max(maxContest, Math.min(0.88, c));
            }
        }
        Vector sPos = shooter.getLocation().toVector();
        Vector sLook = shooter.getLocation().getDirection().setY(0);
        for (Player other : onlineParticipants()) {
            if (other.getUniqueId().equals(shooter.getUniqueId())) {
                continue;
            }
            TeamSide oTeam = getTeam(other.getUniqueId());
            if (oTeam == TeamSide.SPECTATOR || (mode.competitiveTeamMatch() && oTeam == shooterTeam)) {
                continue;
            }
            double dist = Math.hypot(other.getLocation().getX() - sPos.getX(), other.getLocation().getZ() - sPos.getZ());
            if (dist > 2.85) {
                continue;
            }
            Vector toDef = other.getLocation().toVector().subtract(sPos).setY(0);
            double frontFactor = (toDef.lengthSquared() > 1e-4 && sLook.lengthSquared() > 1e-4)
                ? Math.max(0.35, toDef.normalize().dot(sLook.normalize()))
                : 0.7;
            double c = ((2.85 - dist) / 2.25) * 0.68 * frontFactor + (!GroundCheck.isStanding(other) ? 0.22 : 0.0);
            maxContest = Math.max(maxContest, Math.min(0.88, c));
        }
        return maxContest;
    }

    private boolean isDunkLaneBlockedByDefender(Player driver, TeamSide driverTeam, Vector targetRim) {
        if (sparringBot != null && sparringBot.isCuttingOffDunkLane(driver, targetRim)) {
            return true;
        }
        Vector p = driver.getLocation().toVector().setY(0);
        Vector r = targetRim.clone().setY(0);
        Vector toRim = r.clone().subtract(p);
        if (toRim.lengthSquared() < 1e-4) {
            return false;
        }
        Vector toRimDir = toRim.clone().normalize();

        for (Player def : onlineParticipants()) {
            if (def.getUniqueId().equals(driver.getUniqueId())) {
                continue;
            }
            TeamSide dTeam = getTeam(def.getUniqueId());
            if (dTeam == TeamSide.SPECTATOR || (mode.competitiveTeamMatch() && dTeam == driverTeam)) {
                continue;
            }
            if (def.hasPotionEffect(PotionEffectType.SLOWNESS)) {
                continue;
            }
            Vector d = def.getLocation().toVector().setY(0);
            if (p.distance(d) <= 1.50 && d.distance(r) < p.distance(r)) {
                Vector toDef = d.subtract(p);
                if (toDef.lengthSquared() > 1e-4 && toRimDir.dot(toDef.normalize()) >= 0.68) {
                    return true;
                }
            }
        }
        return false;
    }

    private Vector resolveTargetRim(Player player, TeamSide team) {
        return (mode == GameModeType.THREE_POINT_CONTEST || mode == GameModeType.PRACTICE || state == State.WAITING)
            ? selectClosestOrOffensiveRim(player, team)
            : arena.targetRimForTeam(team);
    }

    @Override
    public void onBasketScored(
        TeamSide hoopScoringSide,
        UUID shooterId,
        UUID assisterId,
        boolean threePointer,
        boolean slamDunk,
        boolean pureSwish,
        boolean bankShot,
        double shotDistanceBlocks
    ) {
        UUID effectiveShooterId = shooterId != null ? shooterId : (ball != null ? ball.lastTouchId() : null);
        Player shooter = effectiveShooterId != null ? Bukkit.getPlayer(effectiveShooterId) : null;
        String scorerName;
        if (shooter != null) {
            scorerName = shooter.getName();
        } else if (effectiveShooterId != null && botSquad != null && botSquad.avatarById(effectiveShooterId) != null) {

            scorerName = botSquad.avatarById(effectiveShooterId).profile().nickname();
        } else if (effectiveShooterId != null && statsManager.getProfile(effectiveShooterId) != null) {
            scorerName = statsManager.getProfile(effectiveShooterId).lastKnownName();
        } else {
            List<Player> online = onlineParticipants();
            scorerName = !online.isEmpty() ? online.getFirst().getName() : "MVP";
        }
        shooterId = effectiveShooterId;

        resetShotClock(false);

        if (mode == GameModeType.THREE_POINT_CONTEST) {
            int pts = currentShotIsMoneyBall ? 2 : 1;
            scores.put(TeamSide.IGNIS, score(TeamSide.IGNIS) + pts);
            if (shooter != null) {
                Msg.playPackSound(shooter.getLocation(), "crowd.cheer", 0.7f, currentShotIsMoneyBall ? 1.2f : 1.05f);
                Msg.rail(shooter, fr.bosketball.util.HudSurface.PROGRESS, currentShotIsMoneyBall
                    ? "<" + Palette.GOLD_SOFT + "><bold>Money ball</bold></" + Palette.GOLD_SOFT + ">"
                        + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED + ">2 points, "
                        + score(TeamSide.IGNIS) + " on the board</" + Palette.MUTED + ">"
                    : "<" + Palette.SUCCESS + "><bold>Bucket</bold></" + Palette.SUCCESS + ">"
                        + fr.bosketball.util.HudCopy.SEP + "<" + Palette.MUTED + ">"
                        + score(TeamSide.IGNIS) + " on the board</" + Palette.MUTED + ">");
                advanceContestShot(shooter, false);
            }
            return;
        }

        // Round 34: a basket ALWAYS belongs to the hoop it went through, in every mode. Practice
        // and training used to overwrite this with the shooter's own team, so shooting at your own
        // basket gave *you* the points — the exact opposite of the rule the game shows everywhere
        // else. The hoop decides; the shooter's team only decides whether it is an own basket.
        TeamSide creditedTeam = hoopScoringSide;
        TeamSide shooterTeam = shooterId != null ? getTeam(shooterId) : null;
        boolean shooterHasTeam = shooterTeam != null && shooterTeam != TeamSide.SPECTATOR;

        int pts = resolveShotValue(effectiveShooterId, threePointer, creditedTeam);

        // ---- direction: the run, the best play of the match, and the horn -------------------
        // Read the board *before* it moves: the momentum run is measured on unanswered points, the
        // play-of-the-match ranking needs the pre-shot margin to know whether this shot took the
        // lead, and the buzzer beater needs to say "won it" or "tied" and nothing vaguer.
        boolean finalPeriod = currentQuarter >= totalQuarters;
        boolean atHorn = finalPeriod && ticksRemainingInPeriod <= 1;
        int scoringBefore = score(creditedTeam);
        int otherBefore = score(creditedTeam.opposite());
        if (state == State.LIVE && !(shooterHasTeam && shooterTeam != creditedTeam)) {
            runPoints = creditedTeam == runTeam ? runPoints + pts : pts;
            runTeam = creditedTeam;
        }
        lastBasketDistanceBlocks = shotDistanceBlocks;
        if (state == State.LIVE) {
            rankPlayOfTheMatch(pts, creditedTeam, scorerName, slamDunk, atHorn, otherBefore, scoringBefore);
        }
        // ------------------------------------------------------------------------------------

        scores.put(creditedTeam, score(creditedTeam) + pts);

        boolean isOwnGoal = shooterHasTeam && shooterTeam != creditedTeam;
        MatchBoxScore scorerBox = (shooterId != null && !isOwnGoal) ? getBoxScore(shooterId) : new MatchBoxScore();
        if (!isOwnGoal) {
            scorerBox.points += pts;
            scorerBox.hotStreak++;
            if (slamDunk) {
                scorerBox.dunks++;
            }
            if (pts == 3) {
                scorerBox.threes++;
            }
        }

        for (Map.Entry<UUID, TeamSide> e : participants.entrySet()) {
            if (e.getValue() == creditedTeam.opposite()) {
                getBoxScore(e.getKey()).hotStreak = 0;
            }
        }

        var econ = plugin.settings().economy();
        int bucketCoins = 0;
        if (shooter != null && state == State.LIVE && !isOwnGoal) {
            PlayerProfile prof = statsManager.getOrCreate(shooter);
            prof.addPoints(pts);
            if (pts == 3) {
                prof.setThreePointersMade(prof.threePointersMade() + 1);
            } else {
                prof.setTwoPointersMade(prof.twoPointersMade() + 1);
            }
            if (slamDunk) {
                prof.incrementDunks();
            }
            prof.addXp(pts * 12 + (slamDunk ? 15 : 0) + (pureSwish ? 10 : 0));
            if (mode.competitiveTeamMatch()) {
                bucketCoins = (pts == 3 ? econ.threePointCoins() : econ.twoPointCoins())
                    + (slamDunk ? econ.dunkBonusCoins() : 0);
                if (!isRankedMatch()) {
                    bucketCoins = (int) Math.round(bucketCoins * Math.max(0.0, econ.casualRewardMultiplier()));
                }
                prof.addEarnedCoins(bucketCoins);
                scorerBox.coinsDelta += bucketCoins;
                if (plugin.sidebarManager() != null) {
                    plugin.sidebarManager().clearPlayerCache(shooter.getUniqueId());
                }
            }
            statsManager.markDirty(shooter.getUniqueId());

            if (plugin.progressService() != null) {
                var progress = plugin.progressService();

                progress.record(prof, pts == 3
                    ? fr.bosketball.model.ProgressStat.THREE_POINTERS
                    : fr.bosketball.model.ProgressStat.TWO_POINTERS, 1);
                if (plugin.tutorialManager() != null) {
                    plugin.tutorialManager().notifyAction(shooter, "shoot", null);
                    if (slamDunk) {
                        plugin.tutorialManager().notifyAction(shooter, "dunk", null);
                    }
                }
            }
        }

        Player assister = (assisterId != null && !assisterId.equals(shooterId) && !isOwnGoal) ? Bukkit.getPlayer(assisterId) : null;
        if (assister != null && getTeam(assisterId) == creditedTeam) {
            MatchBoxScore astBox = getBoxScore(assisterId);
            astBox.assists++;
            if (state == State.LIVE) {
                PlayerProfile aProf = statsManager.getOrCreate(assister);
                aProf.incrementAssists();
                // Round 83: the box score counted the assist, the coins were paid, and the
                // passer -- who does not score and therefore gets no celebration -- was told
                // nothing at all about the best pass they made all game.
                Msg.rail(assister, fr.bosketball.util.HudSurface.PROGRESS,
                    fr.bosketball.util.HudCopy.assist(scorerName));
                aProf.addXp(18);
                if (plugin.progressService() != null) {
                    plugin.progressService().record(aProf, fr.bosketball.model.ProgressStat.ASSISTS, 1);
                }
                if (mode.competitiveTeamMatch()) {
                    int astCoins = econ.assistCoins();
                    aProf.addEarnedCoins(astCoins);
                    astBox.coinsDelta += astCoins;
                    if (plugin.sidebarManager() != null) {
                        plugin.sidebarManager().clearPlayerCache(assister.getUniqueId());
                    }
                }
                statsManager.markDirty(assister.getUniqueId());
            }
        } else {
            assister = null;
        }

        if (state == State.LIVE) {
            statsManager.refreshLeaderboardBadges();
        }

        this.celebScorerName = scorerName;
        if (isOwnGoal) {
            // Loud and unmistakable: the points went to the other team, and the banner says so.
            this.celebVariant = Glyphs.BannerVariant.REGULAR;
            this.celebRoleTag = "OWN BASKET +" + pts;
        } else if (slamDunk) {
            this.celebVariant = Glyphs.BannerVariant.SLAM_DUNK;
            this.celebRoleTag = "SLAM DUNK +" + pts;
        } else if (pts == 3) {
            this.celebVariant = Glyphs.BannerVariant.THREE_POINTER;
            this.celebRoleTag = "THREE-POINTER +3";
        } else {
            this.celebVariant = Glyphs.BannerVariant.REGULAR;
            this.celebRoleTag = "BASKET +" + pts;
        }

        if (isOwnGoal) {
            this.celebSubLabel = "FOR";
            this.celebSubValue = teamDisplayName(creditedTeam).toUpperCase(java.util.Locale.ROOT);
        } else if (assister != null) {
            this.celebSubLabel = "ASSIST";
            this.celebSubValue = assister.getName();
        } else {
            this.celebSubLabel = pureSwish ? "SWISH" : (bankShot ? "BANK SHOT" : "DISTANCE");
            this.celebSubValue = String.format(java.util.Locale.ROOT, "%.1fm", shotDistanceBlocks);
            if (shotDistanceBlocks > longestMakeBlocks) {
                longestMakeBlocks = shotDistanceBlocks;
                longestMakeName = scorerName;
                longestMakeWasSlam = slamDunk;
            }
        }

        Vector rimVec = hoopScoringSide == TeamSide.IGNIS ? arena.geometry().southHoopRim() : arena.geometry().northHoopRim();
        World w = arena.world();
        if (w != null) {
            Location rimLoc = rimVec.toLocation(w);
            cheerCrowd(1.15, pts == 3 || slamDunk ? 1.06 : 1.0);
            if (scorerBox.hotStreak >= plugin.settings().match().onFireStreakThreshold()) {
                Msg.playPackSound(rimLoc, "game.on_fire", 1.15f, 1.0f);
            }
            w.spawnParticle(
                Particle.DUST,
                rimLoc.clone().add(0, 0.4, 0),
                28, 0.55, 0.35, 0.55,
                new Particle.DustOptions(creditedTeam.bukkitColor(), 1.5f)
            );
        }

        if (mode.competitiveTeamMatch()) {
            String shotType = slamDunk ? "Slam Dunk" : (pts == 3 ? "3PT" : "2PT");
            String assistNote = assister != null ? " <#94A3B8>(ast: " + assister.getName() + ")</#94A3B8>" : "";
            String coinNote = bucketCoins > 0 ? " <#64748B>· +" + bucketCoins + " Coins</#64748B>" : "";
            String ownGoalNote = isOwnGoal
                ? " <" + Palette.DANGER + "><bold>OWN BASKET</bold></" + Palette.DANGER + "> <#94A3B8>→ points for " + teamUpperLabel(creditedTeam) + "</#94A3B8>"
                : "";
            broadcast("<!shadow><#F8FAFC>" + creditedTeam.pillGlyph() + "</#F8FAFC></!shadow> <#F8FAFC>" + scorerName + "</#F8FAFC> "
                + "<" + Palette.GOLD_SOFT + ">+" + pts + " " + shotType + "</" + Palette.GOLD_SOFT + ">" + assistNote
                + "  <#64748B>(</#64748B><" + Palette.EMBER + ">" + score(TeamSide.IGNIS) + "</" + Palette.EMBER + "><#94A3B8>–</#94A3B8><" + Palette.ICE + ">" + score(TeamSide.FROST) + "</" + Palette.ICE + "><#64748B>)</#64748B>"
                + coinNote + ownGoalNote);
        }

        recordReplayTick();
        List<CinematicCameraManager.ReplayFrame> replayClip = replayBuffer.snapshotTail(62);

        if (mode == GameModeType.PRACTICE || state == State.WAITING) {
            this.warmupBannerTicks = 65;
            for (Player p : onlineParticipants()) {
                hudManager.showScorerCelebration(p, celebScorerName, celebRoleTag, celebVariant, celebSubLabel, celebSubValue);
            }
            long nowMs = System.currentTimeMillis();
            boolean alreadyReplaying = shooter != null && plugin.cinematicCamera() != null
                && plugin.cinematicCamera().isWatching(shooter.getUniqueId());
            if (plugin.cinematicCamera() != null && !alreadyReplaying && nowMs - lastPracticeCineCamMillis >= 900L) {
                lastPracticeCineCamMillis = nowMs;
                replayBuffer.clear();
                Vector courtMid = new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ());
                plugin.cinematicCamera().playInstantGoalReplay(
                    arena,
                    ball,
                    replayClip,
                    onlineParticipants(),
                    shooter,
                    creditedTeam,
                    rimVec,
                    courtMid,
                    slamDunk,
                    pts == 3,
                    48
                );
            }
            return;
        }

        int target1v1 = plugin.settings().match().targetScore1v1();
        if (mode == GameModeType.DUEL_1V1
            && score(creditedTeam) >= target1v1
            && score(creditedTeam) - score(creditedTeam.opposite()) >= 2) {
            this.finishMatchAfterCelebration = true;
        }

        this.nextInboundTeam = creditedTeam.opposite();
        this.stateTimerTicks = plugin.settings().match().celebrationTicks();
        enterState(State.CELEBRATION, MatchPhase.LIVE, MatchPhase.Surface.CARD);
        // Both benches are told, in their own words. The scoring side gets its banner from the
        // boss bar; the side that was scored on had, until now, only the chat line to work it out
        // from -- and "they scored" is not the same sentence as "that was your man".
        scoreBothBenches(creditedTeam, scorerName, pts);
        advanceTensionCurve();
        replayBuffer.clear();
        if (plugin.cinematicCamera() != null) {
            Vector courtMid = new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ());
            plugin.cinematicCamera().playInstantGoalReplay(
                arena,
                ball,
                replayClip,
                onlineParticipants(),
                shooter,
                creditedTeam,
                rimVec,
                courtMid,
                slamDunk,
                pts == 3,
                Math.max(40, Math.min(68, this.stateTimerTicks - 12))
            );
        }
    }

    /**
     * The one sentence the bench that was scored on never heard.
     *
     * <p>Three bugs in this codebase came from the same shape: the server told the player who
     * succeeded. A basket has two participants — the side that scored and the side that conceded —
     * and the scoring side already gets a banner, a chat line and a replay. This gives the other
     * five players the shortest honest version of what just happened to them, and it never reuses
     * the scoring side's line.
     */
    private void scoreBothBenches(TeamSide creditedTeam, String scorerName, int pts) {
        String who = scorerName == null || scorerName.isBlank() ? "They" : scorerName;
        for (Player p : onlineParticipants()) {
            TeamSide mine = getTeam(p.getUniqueId());
            if (mine == TeamSide.SPECTATOR || mine == creditedTeam) {
                continue;
            }
            boolean clutchNote = clutchTime;
            Msg.rail(p,
                "<" + Palette.DANGER + "><bold>Down " + pts + "</bold></" + Palette.DANGER + "> <#64748B>\u00b7</#64748B> "
                    + "<#94A3B8>" + who + " has it going" + (clutchNote ? ", and the clock is thin" : "") + "</#94A3B8>");
        }
    }

    /**
     * The tension curve. Called after every basket, because tension is a function of the clock and
     * the score, and the score only moves on a basket.
     *
     * <p>"The last two minutes of a close game" is not a mood, it is three measurable things:
     * <ul>
     *   <li>the clock changes voice -- the inbound rail names the time left in clutch instead of
     *       a bare "inbound it", and the jumbotron reads CLUTCH where it read the quarter;</li>
     *   <li>the crowd bed changes -- the tight cheer plays once when clutch time opens;</li>
     *   <li>the HUD weighting changes -- the scorebug stays exactly as it was, because the reader
     *       is dribbling, and the gold CLUTCH line carries the margin and the clock instead.</li>
     * </ul>
     * No new chrome: all three are surfaces the match already has.
     */
    private void advanceTensionCurve() {
        boolean finalPeriod = currentQuarter >= totalQuarters;
        int margin = Math.abs(score(TeamSide.IGNIS) - score(TeamSide.FROST));
        int remainingSec = Math.max(0, ticksRemainingInPeriod / 20);
        boolean nowClutch = mode.competitiveTeamMatch()
            && state == State.LIVE
            && finalPeriod
            && remainingSec <= CLUTCH_WINDOW_SECONDS
            && margin <= 3;
        if (nowClutch && !clutchTime) {
            clutchTime = true;
            enterState(State.LIVE, MatchPhase.CLUTCH, MatchPhase.Surface.RAIL);
            broadcast("<" + Palette.GOLD + "><bold>\u25aa CLUTCH</bold></" + Palette.GOLD + "> <#64748B>\u00b7</#64748B> "
                + "<#F8FAFC>" + margin + "</#F8FAFC> <#94A3B8>point game with</#94A3B8> <#F8FAFC>"
                + String.format(java.util.Locale.ROOT, "%d:%02d", remainingSec / 60, remainingSec % 60)
                + "</#F8FAFC> <#94A3B8>left</#94A3B8>");
            arena.updateJumbotron(
                teamUpperLabel(TeamSide.IGNIS), score(TeamSide.IGNIS), "CLUTCH",
                Math.max(0, shotClockSeconds()), score(TeamSide.FROST), teamUpperLabel(TeamSide.FROST)
            );
        } else if (clutchTime && remainingSec > CLUTCH_WINDOW_SECONDS) {
            clutchTime = false;
        }
        maybeCallMomentumTimeout();
    }

    /**
     * The director's own timeout: six unanswered points, once per quarter, two seconds.
     *
     * <p>This is the cheapest drama in basketball and it was the one beat the match did not have.
     * A coach calls it to break a run (the measured trigger in the NBA literature is a 6-0 run); it
     * is deliberately capped at one per quarter and two seconds, because a realistic timeout is
     * also the reason real endings take nine minutes.
     */
    private void maybeCallMomentumTimeout() {
        if (runTeam == null || timeoutUsedThisQuarter || state != State.LIVE || clutchTime) {
            return;
        }
        if (runPoints < RUN_TIMEOUT_POINTS || !mode.competitiveTeamMatch()) {
            return;
        }
        timeoutUsedThisQuarter = true;
        runPoints = 0;
        TeamSide runSide = runTeam;
        runTeam = null;
        enterState(State.LIVE, MatchPhase.TIMEOUT, MatchPhase.Surface.RAIL);
        Msg.playPackSound(arena.centerCourtLocation(), "game.whistle", 1.0f, 1.06f);
        broadcast("<" + Palette.GOLD + "><bold>\u25aa TIMEOUT</bold></" + Palette.GOLD + "> <#64748B>\u00b7</#64748B> "
            + "<#94A3B8>they have gone quiet, and the scoreboard has not</#94A3B8>");
        for (Player p : onlineParticipants()) {
            TeamSide mine = getTeam(p.getUniqueId());
            if (mine == TeamSide.SPECTATOR) {
                continue;
            }
            if (mine == runSide) {
                Msg.rail(p, "<" + Palette.SUCCESS + "><bold>Run of " + RUN_TIMEOUT_POINTS
                    + "</bold></" + Palette.SUCCESS + "> <#64748B>\u00b7</#64748B> <#94A3B8>do not let them breathe</#94A3B8>");
            } else {
                Msg.rail(p, "<" + Palette.DANGER + "><bold>Stop the bleeding</bold></" + Palette.DANGER
                    + "> <#64748B>\u00b7</#64748B> <#94A3B8>next bucket is yours</#94A3B8>");
            }
        }
    }

    /**
     * Ranks one basket against everything already played, and keeps the winner's wording.
     *
     * <p>Called before the score is applied, so the margin it sees is the margin the shot is
     * about to create. The line it stores is the ceremony's "play of the match", and it is written
     * as a person would say it, not as an event id.
     */
    private void rankPlayOfTheMatch(int pts, TeamSide creditedTeam, String scorerName,
                                    boolean slamDunk, boolean atHorn, int otherBefore, int scoringBefore) {
        int scoringAfter = scoringBefore + pts;
        int marginAfter = Math.abs(scoringAfter - otherBefore);
        boolean finalPeriod = currentQuarter >= totalQuarters;
        int secondsLeft = Math.max(0, ticksRemainingInPeriod / 20);
        double leverage = playLeverage(pts, finalPeriod, secondsLeft, marginAfter);
        if (atHorn) {
            leverage *= 1.25;
        }
        if (leverage <= bestPlayLeverage || scorerName == null || scorerName.isBlank()) {
            return;
        }
        bestPlayLeverage = leverage;
        boolean tookLead = scoringBefore <= otherBefore && scoringAfter > otherBefore;
        String shape = slamDunk ? "slam" : (pts == 3 ? "three" : "bucket");
        String tail;
        if (atHorn && tookLead) {
            tail = "at the horn, and the lead with it";
        } else if (atHorn) {
            tail = "at the horn";
        } else if (tookLead) {
            tail = "to take the lead";
        } else if (clutchTime) {
            tail = "with the game on the line";
        } else {
            tail = "from " + String.format(java.util.Locale.ROOT, "%.1fm", lastBasketDistanceBlocks);
        }
        bestPlayLine = "<#F8FAFC>" + cardName(scorerName) + "</#F8FAFC> <#64748B>\u00b7</#64748B> <"
            + Palette.GOLD_SOFT + ">" + shape + " " + tail + "</" + Palette.GOLD_SOFT + ">";
    }

    @Override
    public void onOutOfBounds(Vector lastPos, UUID lastTouchPlayerId) {
        if (mode != GameModeType.PRACTICE && mode.competitiveTeamMatch() && state == State.LIVE) {
            TeamSide lastTeam = lastTouchPlayerId != null ? getTeam(lastTouchPlayerId) : TeamSide.IGNIS;
            TeamSide inboundSide = lastTeam.opposite() == TeamSide.SPECTATOR ? TeamSide.IGNIS : lastTeam.opposite();

            Msg.playPackSound(lastPos.toLocation(arena.world()), "game.whistle", 1.0f, 1.08f);
            broadcast("<" + Palette.GOLD + ">Out of bounds</" + Palette.GOLD + "> <#94A3B8>— inbound for</#94A3B8> <#F8FAFC>" + inboundSide.name() + "</#F8FAFC>");
            this.nextInboundTeam = inboundSide;
            resetShotClock(false);
            awardPossessionToTeam(inboundSide, false);
            enterState(State.LIVE, MatchPhase.DEAD_BALL, MatchPhase.Surface.RAIL);
        } else if (mode == GameModeType.PRACTICE || state == State.WAITING) {

            Player solo = onlineParticipants().stream()
                .filter(p -> getTeam(p.getUniqueId()) != TeamSide.SPECTATOR)
                .findFirst().orElse(null);
            if (solo != null && ball != null) {
                resetShotClock(false);
                PlayerProfile prof = statsManager.getOrCreate(solo);
                ball.attachToHolder(solo, prof.selectedBallSkin(), getBoxScore(solo.getUniqueId()).hotStreak >= 3);
                syncBallHotbarItems(true);
                Msg.rail(solo, fr.bosketball.util.HudSurface.RULE,
                    fr.bosketball.util.HudCopy.outOfBounds(null));
            }
        }
    }

    @Override
    public void onShotMissed(UUID shooterId) {
        if (shooterId == null) {
            return;
        }
        MatchBoxScore box = getBoxScore(shooterId);
        int threshold = plugin.settings().match().onFireStreakThreshold();
        boolean wasOnFire = box.hotStreak >= threshold;
        box.hotStreak = 0;
        if (wasOnFire) {
            Player shooter = Bukkit.getPlayer(shooterId);
            if (shooter != null) {
                Msg.rail(shooter, fr.bosketball.util.HudSurface.CONTEST, "<" + Palette.MUTED
                    + ">Heat check over</" + Palette.MUTED + ">");
            }
        }
    }

    @Override
    public void onRimContact() {
        int min14 = plugin.settings().match().offensiveReboundShotClock() * 20;
        if (shotClockTicks < min14) {
            shotClockTicks = min14;
        }
    }

    private void stopReplayCamerasForParticipants() {
        if (plugin.cinematicCamera() == null) {
            return;
        }
        for (Player p : onlineParticipants()) {
            plugin.cinematicCamera().stopWatching(p, true);
        }
    }

    private void resumeAfterCelebration() {
        stopReplayCamerasForParticipants();
        if (finishMatchAfterCelebration) {
            finishMatchAfterCelebration = false;
            finishMatchAndStartMvpCeremony();
            return;
        }
        // Back on the floor: the ledger decides whether this is ordinary play or clutch time, and
        // the player is told the ball is live (cueBackInPlay) before the inbound is handed over.
        enterState(State.LIVE, clutchTime ? MatchPhase.CLUTCH : MatchPhase.LIVE, MatchPhase.Surface.RAIL);
        advanceTensionCurve();
        resetShotClock(false);
        awardPossessionToTeam(nextInboundTeam, true);
    }

    private void handleShotClockViolation() {
        UUID touchId = ball != null ? (ball.holderId() != null ? ball.holderId() : ball.lastTouchId()) : null;
        TeamSide violating = touchId != null ? getTeam(touchId) : TeamSide.IGNIS;
        if (violating == TeamSide.SPECTATOR) {
            violating = TeamSide.IGNIS;
        }
        TeamSide awarded = violating.opposite();
        Msg.playPackSound(arena.centerCourtLocation(), "game.buzzer", 1.15f, 1.0f);
        broadcast("<" + Palette.DANGER + ">24s Shot Clock Violation</" + Palette.DANGER + "> <#94A3B8>— ball awarded to</#94A3B8> <#F8FAFC>" + awarded.name() + "</#F8FAFC>");
        // Round 84: one neutral line for both benches. The team that lost the ball was not told
        // it had lost it, and the team that was just handed it was not told it had it.
        for (Player p : onlineParticipants()) {
            TeamSide mine = getTeam(p.getUniqueId());
            if (mine == TeamSide.SPECTATOR) {
                continue;
            }
            Msg.rail(p, fr.bosketball.util.HudSurface.RULE, mine == violating
                ? fr.bosketball.util.HudCopy.shotClockLost()
                : fr.bosketball.util.HudCopy.shotClockWon());
        }
        this.nextInboundTeam = awarded;
        resetShotClock(false);
        awardPossessionToTeam(awarded, false);
        enterState(State.LIVE, MatchPhase.DEAD_BALL, MatchPhase.Surface.RAIL);
    }

    private void handlePeriodEndBuzzer() {
        buzzerGraceTicks = 0;
        Msg.playPackSound(arena.centerCourtLocation(), "game.buzzer", 1.35f, 0.94f);

        // Round 79 put the period score in a title here; the horn still rang for nobody. The break
        // is now a phase: title, whistle and jumbotron in one tick, and two benches addressed.
        if (mode != GameModeType.THREE_POINT_CONTEST && state == State.LIVE) {
            enterState(State.LIVE, MatchPhase.QUARTER_BREAK, MatchPhase.Surface.TITLE);
            timeoutUsedThisQuarter = false;
            runPoints = 0;
            runTeam = null;
        }

        if (mode == GameModeType.THREE_POINT_CONTEST) {
            finishThreePointContest();
            return;
        }

        if (currentQuarter < totalQuarters) {
            currentQuarter++;
            this.ticksRemainingInPeriod = plugin.settings().match().quarterSeconds() * 20;
            resetShotClock(false);
            TeamSide possess = (currentQuarter % 2 == 0) ? TeamSide.FROST : TeamSide.IGNIS;
            broadcast("<#F8FAFC><bold>Quarter " + currentQuarter + "/" + totalQuarters + "</bold></#F8FAFC> <#64748B>\u00b7</#64748B> "
                + "<" + Palette.EMBER + ">Ignis " + score(TeamSide.IGNIS) + "</" + Palette.EMBER + "> <#64748B>-</#64748B> <" + Palette.ICE + ">" + score(TeamSide.FROST) + " Frost</" + Palette.ICE + ">");
            if (plugin.integration() != null) {
                plugin.integration().onScoreUpdate(this, " Quarter " + currentQuarter + " Tip-Off");
            }
            awardPossessionToTeam(possess, true);
            // The break ends the moment the next quarter's ball is live. Say so, or phase() would
            // keep reporting a break that finished three seconds ago.
            enterState(State.LIVE, MatchPhase.LIVE, MatchPhase.Surface.SCOREBUG);
            return;
        }

        if (score(TeamSide.IGNIS) == score(TeamSide.FROST)) {
            stopReplayCamerasForParticipants();

            this.overtimesPlayed++;
            if (overtimesPlayed > MAX_OVERTIMES) {
                broadcast("<" + Palette.GOLD + "><bold> " + MAX_OVERTIMES + " overtimes played · decided on box score</bold></" + Palette.GOLD + ">");
                finishMatchAndStartMvpCeremony(decideByMvpScore());
                return;
            }
            currentQuarter++;
            this.ticksRemainingInPeriod = plugin.settings().match().overtimeSeconds() * 20;
            resetShotClock(false);
            broadcast("<" + Palette.GOLD + "><bold> Overtime!</bold></" + Palette.GOLD + "> <#94A3B8>Tied at " + score(TeamSide.IGNIS) + " — " + score(TeamSide.FROST) + "!</#94A3B8>");
            int iIdx = 0;
            int fIdx = 0;
            for (Player p : onlineParticipants()) {
                TeamSide s = getTeam(p.getUniqueId());
                if (s == TeamSide.IGNIS) {
                    p.teleport(arena.teamSpawn(TeamSide.IGNIS, iIdx++));
                } else if (s == TeamSide.FROST) {
                    p.teleport(arena.teamSpawn(TeamSide.FROST, fIdx++));
                }
                Msg.title(p, Msg.gradBold(Msg.GRAD_GOLD, " OVERTIME "), "<#94A3B8>Center court</#94A3B8>", 150, 1600, 300);
            }
            Msg.playPackSound(arena.centerCourtLocation(), "game.whistle", 1.2f, 1.05f);
            if (ball != null) {
                ball.resetToCenterTipOff(
                    new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ()),
                    0.46
                );
            }
            syncBallHotbarItems(true);
            enterState(State.LIVE, MatchPhase.LIVE, MatchPhase.Surface.SCOREBUG);
            return;
        }

        finishMatchAndStartMvpCeremony(null);
    }

    private TeamSide decideByMvpScore() {
        double ignis = 0.0;
        double frost = 0.0;
        for (Map.Entry<UUID, TeamSide> e : participants.entrySet()) {
            double score = getBoxScore(e.getKey()).mvpScore();
            if (e.getValue() == TeamSide.IGNIS) {
                ignis += score;
            } else if (e.getValue() == TeamSide.FROST) {
                frost += score;
            }
        }
        if (Math.abs(frost - ignis) < 1.0e-9) {

            return java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                ? TeamSide.IGNIS : TeamSide.FROST;
        }
        return frost > ignis ? TeamSide.FROST : TeamSide.IGNIS;
    }

    private void finishThreePointContest() {
        stopReplayCamerasForParticipants();
        plugin.gameManager().notifyMatchCompleted();

        List<Player> shooters = onlineParticipants();
        Player shooter = shooters.isEmpty() ? null : shooters.get(0);
        int finalScore = score(TeamSide.IGNIS);
        if (shooter != null) {
            PlayerProfile prof = statsManager.getOrCreate(shooter);
            boolean record = finalScore > prof.threePointContestBest();
            prof.updateThreePointContestBest(finalScore);
            prof.addXp(finalScore * 10);
            int earnedCoins = Math.min(plugin.settings().economy().threePointContestMaxCoins(), Math.max(0, finalScore));

            prof.addEarnedCoins(earnedCoins);
            statsManager.saveProfileAsync(prof);
            if (plugin.sidebarManager() != null) {
                plugin.sidebarManager().clearPlayerCache(shooter.getUniqueId());
            }

            if (plugin.settings().chat().clearAfterMatch()) {
                Msg.clearChatBuffer(shooter);
            }
            Msg.send(shooter, "<" + Palette.ICE + "><bold>3-Point Contest Finished</bold></" + Palette.ICE + "> <#64748B>·</#64748B> <#F8FAFC>Score: <bold>" + finalScore + "/30</bold></#F8FAFC> <#64748B>·</#64748B> <" + Palette.GOLD_SOFT + ">+" + earnedCoins + " Coins</" + Palette.GOLD_SOFT + ">");
            Msg.title(
                shooter,
                "<" + Palette.ICE + "><bold>Final Score: " + finalScore + "/30</bold></" + Palette.ICE + ">",
                record ? "<" + Palette.SUCCESS + "><bold>▪ New Personal Best! (+" + earnedCoins + " Coins) ▪</bold></" + Palette.SUCCESS + ">" : "<#94A3B8>Best: " + prof.threePointContestBest() + "/30 · +" + earnedCoins + " Coins</#94A3B8>",
                200, 3000, 500
            );
            Msg.playPackSound(shooter.getLocation(), "crowd.cheer", 1.2f, 1.0f);
            for (Player extra : shooters) {
                if (extra.equals(shooter)) {
                    continue;
                }

                PlayerProfile extraProf = statsManager.getOrCreate(extra);
                boolean extraRecord = finalScore > extraProf.threePointContestBest();
                extraProf.updateThreePointContestBest(finalScore);
                extraProf.addXp(finalScore * 10);
                extraProf.addEarnedCoins(earnedCoins);
                statsManager.markDirty(extra.getUniqueId());
                statsManager.saveProfileAsync(extraProf);
                if (plugin.sidebarManager() != null) {
                    plugin.sidebarManager().clearPlayerCache(extra.getUniqueId());
                }
                Msg.send(extra, "<" + Palette.ICE + "><bold>3-Point Contest Finished</bold></" + Palette.ICE + "> <#64748B>·</#64748B> <#F8FAFC>Score: <bold>"
                    + finalScore + "/30</bold></#F8FAFC> <#64748B>·</#64748B> <" + Palette.GOLD_SOFT + ">+" + earnedCoins + " Coins</" + Palette.GOLD_SOFT + ">"
                    + (extraRecord ? " <" + Palette.SUCCESS + ">(New Personal Best!)</" + Palette.SUCCESS + ">" : ""));
            }
            equipCeremonyHotbar(shooter, false);
        }
        if (!enterState(State.MVP_CEREMONY, MatchPhase.CEREMONY, MatchPhase.Surface.CARD)) {
            return;
        }
        this.stateTimerTicks = 100;
    }

    private void finishMatchAndStartMvpCeremony() {
        finishMatchAndStartMvpCeremony(null);
    }

    private void finishMatchAndStartMvpCeremony(TeamSide forcedWinner) {

        // The one-way door, and it is now two doors. The flag answers "have we begun?"; the ledger
        // answers "did we ever begin?", which is the question that matters after a cleanup path
        // has reset fields. A match can be finished by a buzzer, a forfeit, a remake or an empty
        // court, and the ceremony pays ELO: starting it twice pays twice.
        if (ceremonyStarted) {
            return;
        }
        if (director.hasVisited(MatchPhase.CEREMONY)) {
            return;
        }
        // Claim the door *before* anything is paid, then let the ledger arbitrate. If it refuses --
        // the only way it can is that a ceremony already ran -- the claim stays made: a refused
        // ceremony must stay refused, and the match pays nothing twice.
        ceremonyStarted = true;
        if (!enterState(State.MVP_CEREMONY, MatchPhase.CEREMONY, MatchPhase.Surface.CARD)) {
            return;
        }
        stopReplayCamerasForParticipants();
        plugin.gameManager().notifyMatchCompleted();
        this.stateTimerTicks = 120;

        int sIgnis = score(TeamSide.IGNIS);
        int sFrost = score(TeamSide.FROST);
        this.forfeitVictory = forcedWinner != null;
        this.matchDrawn = forcedWinner == null && sIgnis == sFrost;
        TeamSide winner = forcedWinner != null ? forcedWinner : (sIgnis >= sFrost ? TeamSide.IGNIS : TeamSide.FROST);
        TeamSide loser = winner.opposite();

        UUID mvpId = participants.entrySet().stream()
            .filter(e -> e.getValue() == winner)
            .map(Map.Entry::getKey)
            .sorted(Comparator
                .comparingDouble((UUID id) -> -getBoxScore(id).mvpScore())
                .thenComparingInt(id -> -getBoxScore(id).points)
                .thenComparingInt(id -> -getBoxScore(id).assists)
                .thenComparing(id -> {
                    Player mvpCandidate = Bukkit.getPlayer(id);
                    return mvpCandidate != null ? mvpCandidate.getName() : id.toString();
                }))
            .findFirst()
            .orElse(null);

        if (matchDrawn) {
            mvpId = null;
        }
        Player mvpPlayer = mvpId != null ? Bukkit.getPlayer(mvpId) : null;
        String mvpName = mvpPlayer != null ? mvpPlayer.getName() : "None";

        if (resultsApplied) {

            return;
        }
        resultsApplied = true;

        double winAvgElo = averageTeamElo(winner);
        double loseAvgElo = averageTeamElo(loser);
        int winEloGain = StatsManager.computeEloGain(winAvgElo, loseAvgElo);
        int lossEloPenalty = StatsManager.computeEloLoss(winAvgElo, loseAvgElo);
        var econ = plugin.settings().economy();
        int winCoins = econ.winCoins();
        int lossCoins = econ.lossCoins();
        int mvpBonusCoins = econ.mvpBonusCoins();
        // Round 84 — the bot backfill is on by default (matchmaking.fill-bots-on-launch), so a
        // ranked match against one CPU player was paying the full ranked reward: 40 for the win,
        // 15 for the MVP, the whole on-court economy and +15 ELO. No opponent, no risk, full
        // reward — the cleanest farm on the server. A match with a bot on either side now pays
        // half its coins and no ELO at all.
        boolean hadBots = hasBots();

        for (Map.Entry<UUID, TeamSide> entry : participants.entrySet()) {
            TeamSide side = entry.getValue();
            if (side == TeamSide.SPECTATOR) {
                continue;
            }
            Player p = Bukkit.getPlayer(entry.getKey());
            PlayerProfile prof = statsManager.getOrCreate(entry.getKey(), p != null ? p.getName() : "Player");
            Rank oldRank = prof.effectiveRank();
            MatchBoxScore myBox = getBoxScore(entry.getKey());

            boolean drawn = matchDrawn;
            boolean won = !drawn && (side == winner);
            boolean ranked = !drawn && (queueMode == null || queueMode.affectsElo());
            double rewardScale = fr.bosketball.economy.EconomyBalance.matchMultiplier(ranked, hadBots);
            int appliedDelta = 0;
            if (ranked) {
                int rawDelta = won ? +winEloGain : -lossEloPenalty;
                fr.bosketball.season.SeasonService season = plugin.seasonService();
                int swingDelta = season != null ? season.scaleRankedDelta(prof, rawDelta) : rawDelta;
                if (swingDelta == 0 && rawDelta != 0) {
                    swingDelta = rawDelta > 0 ? 1 : -1;
                }
                // A bot match moves no rating at all — the multiplier is 0, not a smaller swing.
                if (hadBots) {
                    swingDelta = 0;
                }
                int before = prof.elo();
                prof.adjustElo(swingDelta);

                appliedDelta = prof.elo() - before;
                if (season != null && season.enabled()) {
                    fr.bosketball.season.SeasonResult seasonResult = season.onRankedMatch(prof, before, prof.elo(), won);
                    if (seasonResult != null) {
                        seasonResults.put(entry.getKey(), seasonResult);
                    }
                }
                plugin.getLogger().info("[ELO] " + prof.lastKnownName() + " " + (won ? "WIN" : "LOSS")
                    + " " + before + " -> " + prof.elo() + " (" + (appliedDelta >= 0 ? "+" : "") + appliedDelta + ")"
                    + " in " + mode.id() + " vs avg " + (int) loseAvgElo + "/" + (int) winAvgElo);
            }
            appliedEloDeltas.put(entry.getKey(), appliedDelta);
            recordedSides.put(entry.getKey(), side);
            if (!drawn) {
                prof.recordMatchOutcome(won);
            }
            prof.addXp(drawn ? 80 : (won ? 120 : 45));

            int scaledWinCoins = (int) Math.round(winCoins * rewardScale);
            int scaledMvpCoins = (int) Math.round(mvpBonusCoins * rewardScale);
            int scaledLossCoins = (int) Math.round(lossCoins * rewardScale);
            if (drawn) {
                // A draw pays the win bonus to nobody and takes the loss penalty from nobody.
                // Round 84: this used to set `rewardScale = 0.0`, which was dead — the scaled
                // amounts had already been computed above it, so the reset changed nothing. The
                // amounts are now zeroed directly, which is what the comment always claimed.
                scaledWinCoins = 0;
                scaledMvpCoins = 0;
                scaledLossCoins = 0;
            } else if (won) {
                prof.addEarnedCoins(scaledWinCoins);
                myBox.coinsDelta += scaledWinCoins;
            } else {
                // Round 84 — a loss PAYS. It used to subtract coins from the loser, and with
                // economy.loss-coins at 0 that meant a loss paid nothing at all. The server only
                // ever told the player who succeeded. The loser is paid their floor now: enough
                // that a bad game is a bad game and not a punishment.
                if (scaledLossCoins > 0) {
                    prof.addEarnedCoins(scaledLossCoins);
                    myBox.coinsDelta += scaledLossCoins;
                }
            }

            if (entry.getKey().equals(mvpId)) {
                prof.incrementMvps();
                prof.addXp(80);
                prof.addEarnedCoins(scaledMvpCoins);
                myBox.coinsDelta += scaledMvpCoins;
            }

            prof.recordMatch(new MatchHistoryEntry(
                System.currentTimeMillis(),
                mode.id().toUpperCase(java.util.Locale.ROOT),
                arena.displayName(),
                side,
                sIgnis,
                sFrost,
                won,
                myBox.points,
                myBox.assists,
                myBox.rebounds,
                myBox.steals,
                myBox.blocks,
                appliedDelta,
                mvpName
            ));
            statsManager.markDirty(entry.getKey());

            if (plugin.sidebarManager() != null) {
                plugin.sidebarManager().clearPlayerCache(entry.getKey());
            }
            if (p != null) {
                checkRankPromotion(p, oldRank, prof.effectiveRank());
                checkRankDemotion(p, oldRank, prof.effectiveRank());
            }

            var progress = plugin.progressService();
            if (progress != null) {
                progress.record(prof, fr.bosketball.model.ProgressStat.MATCHES_PLAYED, 1);
                if (won) {
                    progress.record(prof, fr.bosketball.model.ProgressStat.MATCHES_WON, 1);
                }
                progress.record(prof, fr.bosketball.model.ProgressStat.POINTS_SCORED, myBox.points);
                progress.record(prof, fr.bosketball.model.ProgressStat.DUNKS, myBox.dunks);
                progress.record(prof, fr.bosketball.model.ProgressStat.ASSISTS, myBox.assists);
                progress.record(prof, fr.bosketball.model.ProgressStat.REBOUNDS, myBox.rebounds);
                progress.record(prof, fr.bosketball.model.ProgressStat.STEALS, myBox.steals);
                progress.record(prof, fr.bosketball.model.ProgressStat.BLOCKS, myBox.blocks);
                progress.record(prof, fr.bosketball.model.ProgressStat.THREE_POINTERS, myBox.threes);

                if (ranked) {
                    prof.setRankedMatches(prof.rankedMatches() + 1);
                } else {
                    prof.setCasualMatches(prof.casualMatches() + 1);
                }
                if (plugin.socialManager() != null && plugin.socialManager().partySizeOrOne(entry.getKey()) > 1) {
                    prof.setPartyMatches(prof.partyMatches() + 1);
                }
                progress.record(prof, ranked
                    ? fr.bosketball.model.ProgressStat.RANKED_MATCHES
                    : fr.bosketball.model.ProgressStat.CASUAL_MATCHES, 1);
                if (plugin.socialManager() != null && plugin.socialManager().partySizeOrOne(entry.getKey()) > 1) {
                    progress.record(prof, fr.bosketball.model.ProgressStat.PARTY_MATCHES, 1);
                }
                if (myBox.coinsDelta > 0) {
                    progress.record(prof, fr.bosketball.model.ProgressStat.COINS_EARNED, myBox.coinsDelta);
                }

                progress.evaluateAchievements(prof, true);
            }

            if (plugin.integration() != null && ranked) {
                plugin.integration().rememberEloDelta(this, entry.getKey(), appliedDelta);
            }
        }

        if (plugin.guildService() != null && isRankedMatch()) {
            java.util.List<UUID> winners = new ArrayList<>();
            java.util.List<UUID> losers = new ArrayList<>();
            for (Map.Entry<UUID, TeamSide> e : participants.entrySet()) {
                if (e.getValue() == TeamSide.SPECTATOR) {
                    continue;
                }
                if (e.getValue() == winner) {
                    winners.add(e.getKey());
                } else {
                    losers.add(e.getKey());
                }
            }
            plugin.guildService().onMatchFinished(winners, losers,
                18);
        }
        statsManager.refreshLeaderboardBadges();
        statsManager.flushDirtyAsync(false);

        if (mvpPlayer != null) {
            mvpPlayer.teleport(arena.centerCourtLocation().add(0, 0.15, 0));
            ItemStack trophy = BallSkin.createCustomModelItem(
                Material.GOLDEN_CARROT,
                "trophy_mvp",
                "<" + Palette.GOLD_SOFT + "><bold> Match MVP Trophy</bold></" + Palette.GOLD_SOFT + ">",
                List.of("<#94A3B8>Awarded to " + mvpName + "</#94A3B8>")
            );
            mvpPlayer.getInventory().setItem(0, trophy);
            mvpPlayer.getInventory().setHeldItemSlot(0);
            mvpPlayer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, mvpPlayer.getLocation().add(0, 1.5, 0), 50, 0.7, 0.8, 0.7, 0.22);
        }

        Msg.playPackSound(arena.centerCourtLocation(), "crowd.cheer", 1.35f, 1.0f);

        if (plugin.integration() != null && !matchReportSent) {
            matchReportSent = true;
            plugin.integration().onMatchFinished(this, winner, mvpId, queueMode);
        }

        MatchBoxScore mvpBox = mvpId != null ? getBoxScore(mvpId) : new MatchBoxScore();
        String winHex = String.format(java.util.Locale.ROOT, "%06X", winner.textColor().value() & 0xFFFFFF);
        String winnerName = teamDisplayName(winner);
        String ignisTitle = teamDisplayName(TeamSide.IGNIS);
        String frostTitle = teamDisplayName(TeamSide.FROST);
        for (Player p : onlineParticipants()) {
            boolean isMvp = mvpPlayer != null && p.getUniqueId().equals(mvpPlayer.getUniqueId());
            equipCeremonyHotbar(p, isMvp);

            if (plugin.settings().chat().clearAfterMatch()) {
                Msg.clearChatBuffer(p);
            }

            Msg.title(
                p,
                Msg.gradBold(winHex + ":" + Palette.GOLD_SOFT + "", " " + winnerName.toUpperCase(java.util.Locale.ROOT) + " WINS!"),
                "<" + Palette.GOLD_SOFT + ">▪ MVP: <#F8FAFC>" + mvpName + "</#F8FAFC> (" + mvpBox.points + " PTS \u00b7 " + mvpBox.assists + " AST)</" + Palette.GOLD_SOFT + ">",
                200, 4000, 600
            );
            hudManager.showScorerCelebration(
                p,
                mvpName,
                "MATCH MVP",
                Glyphs.BannerVariant.SLAM_DUNK,
                "FINAL SCORE",
                sIgnis + " - " + sFrost
            );

            MatchBoxScore pBox = getBoxScore(p.getUniqueId());
            TeamSide pSide = getTeam(p.getUniqueId());

            int pElo = appliedEloDeltas.getOrDefault(p.getUniqueId(), 0);
            String eloStr = pElo >= 0 ? "<" + Palette.SUCCESS + ">+" + pElo + " ELO</" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">" + pElo + " ELO</" + Palette.DANGER + ">";
            String coinStr = pBox.coinsDelta >= 0
                ? "<" + Palette.GOLD_SOFT + ">+" + pBox.coinsDelta + " coins</" + Palette.GOLD_SOFT + ">"
                : "<" + Palette.DANGER + ">" + pBox.coinsDelta + " coins</" + Palette.DANGER + ">";

            // Round 84 (economy) — "you are N games from the next rank". The server celebrates a
            // rank-up loudly but never says how close the next one is, so a player at 1035 ELO has
            // no way to tell whether the promotion is one game away or fifty. The signal rides
            // the final-score card as one extra line: no second card, no second title.
            java.util.List<String> finalCardLines = new java.util.ArrayList<>(List.of(
                "<" + Palette.EMBER + "><bold>" + ignisTitle + " " + sIgnis + "</bold></" + Palette.EMBER + "> <#64748B>-</#64748B> <" + Palette.ICE + "><bold>" + sFrost + " " + frostTitle + "</bold></" + Palette.ICE + ">",
                "<" + Palette.GOLD_SOFT + "> Match MVP:</" + Palette.GOLD_SOFT + "> <#F8FAFC><bold>" + mvpName + "</bold></#F8FAFC> <#94A3B8>(" + mvpBox.points + " PTS \u00b7 " + mvpBox.assists + " AST)</#94A3B8>",
                // Best play of the match: the one basket whose leverage was highest, not the one
                // that was furthest away. A 30-block heave at the horn outranks a wide-open dunk,
                // and a routine make in the second quarter never wins the line at all.
                bestPlayLine != null
                    ? "<#94A3B8>Play of the match:</#94A3B8> " + bestPlayLine
                    : (longestMakeBlocks >= 6.0 && longestMakeName != null
                        ? "<#94A3B8>Best play:</#94A3B8> <#F8FAFC>" + cardName(longestMakeName) + "</#F8FAFC> <#64748B>\u00b7</#64748B> <" + Palette.ICE + ">"
                            + String.format(java.util.Locale.ROOT, "%.1fm", longestMakeBlocks) + (longestMakeWasSlam ? " slam" : "") + "</" + Palette.ICE + ">"
                        : "<#94A3B8>Nobody put a stamp on this one.</#94A3B8>"),
                pSide == TeamSide.SPECTATOR
                    ? "<#94A3B8><#FDE047>/gg</#FDE047> rewards +5 coins</#94A3B8>"
                    : "<#94A3B8>Rewards:</#94A3B8> " + eloStr + " <#64748B>·</#64748B> " + coinStr + " <#64748B>·</#64748B> <#94A3B8>Type <#FDE047>/gg</#FDE047></#94A3B8>"
            ));
            if (pSide != TeamSide.SPECTATOR) {
                String rankSignal = fr.bosketball.stats.RankProgress.signal(
                    statsManager.getOrCreate(p).elo());
                if (!rankSignal.isEmpty()) {
                    finalCardLines.add(rankSignal);
                }
            }
            Msg.sendFramedCard(p, "Final Score · " + mode.id().toUpperCase(java.util.Locale.ROOT),
                finalCardLines);

            fr.bosketball.season.SeasonResult seasonCard = seasonResult(p.getUniqueId());
            fr.bosketball.season.SeasonService seasonService = plugin.seasonService();
            if (seasonCard != null && seasonService != null) {
                Msg.sendFramedCard(p, seasonService.state().name(), seasonCardLines(seasonCard));
                if (seasonCard.justCompletedPlacements()) {
                    Msg.title(p, "<" + Palette.GOLD + "><bold>PLACEMENTS COMPLETE</bold></" + Palette.GOLD + ">",
                        "<#94A3B8>Your seasonal tier is locked in: <#F8FAFC>" + seasonCard.tier().plainDisplayName() + "</#F8FAFC></#94A3B8>",
                        150, 2400, 400);
                    Msg.playPersonalPackSound(p, "game.whistle", 0.85f, 1.25f);
                }
            }
        }
    }

    private java.util.List<String> seasonCardLines(fr.bosketball.season.SeasonResult result) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String tierName = result.tier() == null ? "Rookie" : result.tier().plainDisplayName();
        lines.add("<#94A3B8>Peak this season:</#94A3B8> <" + Palette.ICE + "><bold>" + result.bestElo() + " ELO</bold></" + Palette.ICE + ">"
            + " <#64748B>·</#64748B> <#F8FAFC>" + tierName + "</#F8FAFC>");
        lines.add("<#94A3B8>Season record:</#94A3B8> <" + Palette.SUCCESS + ">" + result.wins() + "W</" + Palette.SUCCESS + "> <#64748B>/</#64748B> <" + Palette.DANGER + ">"
            + Math.max(0, result.matches() - result.wins()) + "L</" + Palette.DANGER + ">"
            + (result.newBest() ? " <" + Palette.GOLD_SOFT + ">· new peak!</" + Palette.GOLD_SOFT + ">" : ""));
        if (result.provisional()) {
            lines.add("<" + Palette.GOLD + ">Placements:</" + Palette.GOLD + "> <#F8FAFC>" + result.placementsDone() + "/" + result.placementsRequired()
                + "</#F8FAFC> <#94A3B8>— your seasonal tier unlocks once they are done. "
                + "ELO stays <#F8FAFC>+15 / -5</#F8FAFC>.</#94A3B8>");
        } else {
            lines.add("<#94A3B8>Ranked tier locked in. Climb before the season ends for a bigger payout.</#94A3B8>");
        }
        return lines;
    }


    private void cancelAsRemake(Player leaver, TeamSide leavingSide, boolean ranked) {
        this.stateTimerTicks = 0;
        enterState(State.FINISHED, MatchPhase.FINAL, MatchPhase.Surface.LOBBY);
        broadcast("<" + Palette.GOLD + "><bold>⚠ REMAKE</bold></" + Palette.GOLD + "> <#94A3B8>— the match was canceled inside the "
            + plugin.settings().match().remakeWindowSeconds() + "s window. <#F8FAFC>No ELO change, no loss recorded.</#F8FAFC></#94A3B8>");
        for (Player p : onlineParticipants()) {
            if (p.getUniqueId().equals(leaver.getUniqueId())) {
                continue;
            }
            Msg.title(p,
                "<" + Palette.GOLD + "><bold>⚠ REMAKE</bold></" + Palette.GOLD + ">",
                "<#94A3B8>Match canceled early — nothing was lost.</#94A3B8>",
                100, 1400, 300);
            if (plugin.gameManager() != null) {
                plugin.gameManager().requeueOffer(p, mode, queueMode);
            }
        }
        if (plugin.statsManager() != null) {
            plugin.statsManager().markDirty(leaver.getUniqueId());
        }
    }

    public void beginDisconnectGrace(UUID playerId) {
        TeamSide side = participants.get(playerId);
        if (side == null || side == TeamSide.SPECTATOR) {
            return;
        }
        disconnectGrace.put(playerId, tickCounter + DISCONNECT_GRACE_TICKS);
        disconnectSides.put(playerId, side);
        // Round 35: the broadcast used to print the team label instead of the player name, so nobody
        // could tell who actually dropped.
        broadcast("<" + Palette.GOLD + "> <bold>RECONNECTING</bold></" + Palette.GOLD + "> <#94A3B8>—</#94A3B8> <#F8FAFC>" + nameOfId(playerId)
            + "</#F8FAFC> <#94A3B8>(" + side.chatLabel() + ") has <#F8FAFC>" + (DISCONNECT_GRACE_TICKS / 20)
            + "s</#F8FAFC> to come back. The match keeps running.</#94A3B8>");
    }

    public boolean cancelDisconnectGrace(UUID playerId) {
        if (disconnectGrace.remove(playerId) == null) {
            return false;
        }
        TeamSide side = disconnectSides.remove(playerId);
        // Round 35: coming back must really put the player back on the court (kit, spawn, rig).
        // It used to only print "BACK ON COURT" while the roster had already dropped them.
        Player back = Bukkit.getPlayer(playerId);
        if (back != null && back.isOnline() && side != null && side != TeamSide.SPECTATOR
            && participants.get(playerId) == side && state != State.FINISHED) {
            equipCourtKit(back, side);
            back.setFireTicks(0);
            back.setFallDistance(0f);
            back.teleport(arena.teamSpawn(side, Math.max(0, countTeam(side) - 1)));
            PlayerVisualRig rig = visualRigs.get(playerId);
            if (rig == null) {
                visualRigs.put(playerId, new PlayerVisualRig(plugin, back, side));
            }
            syncSpectatorInvisibility();
            syncBallHotbarItems(heldByMe(back));
        }
        broadcast("<" + Palette.SUCCESS + ">✔ <bold>BACK ON COURT</bold></" + Palette.SUCCESS + "> <#94A3B8>—</#94A3B8> <#F8FAFC>" + nameOfId(playerId) + "</#F8FAFC>");
        return true;
    }

    /** True when the ball sits in that player's hands (used to restore the held-ball kit). */
    private boolean heldByMe(Player player) {
        return ball != null && player != null && player.getUniqueId().equals(ball.holderId());
    }

    private void tickDisconnectGrace() {
        if (disconnectGrace.isEmpty()) {
            return;
        }
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : disconnectGrace.entrySet()) {
            if (tickCounter >= e.getValue()) {
                expired.add(e.getKey());
            }
        }
        for (UUID id : expired) {
            disconnectGrace.remove(id);
            TeamSide side = disconnectSides.remove(id);
            if (side == null || state == State.FINISHED) {
                continue;
            }

            Player gone = Bukkit.getPlayer(id);
            if (gone != null && gone.isOnline()) {
                continue;
            }
            Msg.send(Bukkit.getConsoleSender(), Msg.warn("[ELO] disconnect grace expired for " + nameOfId(id)));
            broadcast("<" + Palette.DANGER + ">⚠ " + nameOfId(id) + " did not reconnect — match forfeited.</" + Palette.DANGER + ">");

            // Round 35: the disconnected player now really pays for the abandonment, exactly like
            // someone who clicks "leave", and their roster slot is released so the team counts (and
            // the end-of-match logic below) are correct.
            PlayerProfile quitter = statsManager.getIfPresent(id);
            if (quitter != null) {
                registerForfeit(id, side, quitter);
            }
            participants.remove(id);
            if (plugin.gameManager() != null) {
                plugin.gameManager().unindexPlayerSession(id, this);
            }
            PlayerVisualRig deadRig = visualRigs.remove(id);
            if (deadRig != null) {
                deadRig.despawn();
            }
            savedStates.remove(id);

            if (activeCompetitorCount() == 0) {
                stopAndCleanup();
                continue;
            }
            if (countTeam(side) == 0) {
                finishMatchAndStartMvpCeremony(side.opposite());
            } else if (countTeam(side.opposite()) == 0) {
                finishMatchAndStartMvpCeremony(side);
            }
        }
    }

    /**
     * Shared forfeit bookkeeping (leave button, disconnect grace expired). Returns the coins taken.
     */
    private int registerForfeit(UUID id, TeamSide leavingSide, PlayerProfile prof) {
        int forfeitEloLoss = FORFEIT_ELO_LOSS;
        int lost = prof.removeCoins(plugin.settings().economy().forfeitCoins());
        prof.recordMatchOutcome(false);
        prof.adjustElo(-forfeitEloLoss);
        MatchBoxScore fBox = getBoxScore(id);
        prof.recordMatch(new MatchHistoryEntry(
            System.currentTimeMillis(),
            mode.id().toUpperCase(java.util.Locale.ROOT) + " (FF)",
            arena.displayName(),
            leavingSide,
            score(TeamSide.IGNIS),
            score(TeamSide.FROST),
            false,
            fBox.points,
            fBox.assists,
            fBox.rebounds,
            fBox.steals,
            fBox.blocks,
            -forfeitEloLoss,
            "Forfeit"
        ));
        statsManager.saveProfileAsync(prof);
        statsManager.refreshLeaderboardBadges();
        if (plugin.sidebarManager() != null) {
            plugin.sidebarManager().clearPlayerCache(id);
        }
        return lost;
    }

    private String nameOfId(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            return p.getName();
        }
        PlayerProfile prof = statsManager.getIfPresent(id);
        return prof != null && prof.lastKnownName() != null ? prof.lastKnownName() : "Player";
    }

    public boolean forfeitVictory() {
        return forfeitVictory;
    }

    public boolean isRankedMatch() {
        return queueMode == null || queueMode.affectsElo();
    }

    private void checkRankPromotion(Player player, Rank oldRank, Rank newRank) {
        if (oldRank != newRank && !newRank.staffOnly() && newRank.minElo() > oldRank.minElo()) {
            Msg.title(
                player,
                "<" + Palette.GOLD_SOFT + "><bold>▪ RANK UP! ▪</bold></" + Palette.GOLD_SOFT + ">",
                "<!shadow><#F8FAFC>" + oldRank.pillGlyph() + "</#F8FAFC></!shadow> <#94A3B8>»</#94A3B8> <!shadow><#F8FAFC>" + newRank.pillGlyph() + "</#F8FAFC></!shadow>",
                200, 2800, 500
            );
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 40, 0.5, 0.6, 0.5, 0.2);
            broadcast("<" + Palette.GOLD_SOFT + "><bold>▪ Rank Up!</bold></" + Palette.GOLD_SOFT + "> <#F8FAFC>" + player.getName() + "</#F8FAFC> <#94A3B8>reached</#94A3B8> <!shadow><#F8FAFC>"
                + newRank.pillGlyph() + "</#F8FAFC></!shadow>");
            if (plugin.integration() != null) {
                plugin.integration().onRankUp(player, oldRank.plainDisplayName(), newRank.plainDisplayName(),
                    plugin.statsManager().getOrCreate(player).elo());
            }
        }
    }

    /** Round 27: dropping a tier was completely silent, players only noticed the lost pill. */
    private void checkRankDemotion(Player player, Rank oldRank, Rank newRank) {
        if (oldRank != newRank && !newRank.staffOnly() && newRank.minElo() < oldRank.minElo()) {
            Msg.send(player, Msg.warn("Rank down — you are now <#F8FAFC>" + newRank.plainDisplayName()
                + "</#F8FAFC> (<#F8FAFC>" + player.getName() + "</#F8FAFC> lost a tier, win a game to climb back)."));
            if (plugin.integration() != null) {
                plugin.integration().onRankDemotion(player, oldRank.plainDisplayName(),
                    newRank.plainDisplayName(), plugin.statsManager().getOrCreate(player).elo());
            }
        }
    }

    private void advanceContestShot(Player shooter, boolean initial) {
        contestShotSequence++;
        if (!initial) {
            contestBallInRack++;
            if (contestBallInRack >= 5) {
                contestBallInRack = 0;
                contestRackIndex++;
            }
        }
        if (contestRackIndex >= 5) {
            finishThreePointContest();
            return;
        }

        List<Vector> racks = arena.geometry().threePointContestRacks();
        Vector spot = racks.get(contestRackIndex);
        Vector southRim = arena.geometry().southHoopRim();
        Vector dir = southRim.clone().subtract(spot);
        Location tp = spot.toLocation(arena.world());
        tp.setDirection(dir);

        if (contestBallInRack == 0) {
            shooter.teleport(tp);
            Msg.playPersonalPackSound(shooter, "ui.click", 0.85f, 1.2f);
        }

        this.currentShotIsMoneyBall = (contestBallInRack == 4);
        // The contest used to hand out the stock ball and a hard-coded two-tone money ball, so the
        // skin a player had equipped and paid for never showed up in the one mode where the ball is
        // the whole show. Keep their skin; the money ball is already announced in the rail.
        BallSkin ballSkin = statsManager.getOrCreate(shooter).selectedBallSkin();
        ball.attachToHolder(shooter, ballSkin, false);
        syncBallHotbarItems(true);
        Msg.rail(shooter, fr.bosketball.util.HudSurface.PROGRESS,
            "<" + Palette.ICE + "><bold>Rack " + (contestRackIndex + 1) + " of 5</bold></" + Palette.ICE + ">"
                + fr.bosketball.util.HudCopy.SEP
                + (currentShotIsMoneyBall
                ? "<" + Palette.GOLD_SOFT + "><bold>Money ball next</bold></" + Palette.GOLD_SOFT + ">"
                : "<" + Palette.MUTED + ">ball " + (contestBallInRack + 1) + " of 5</" + Palette.MUTED + ">"));
    }

    private void awardPossessionToTeam(TeamSide team, boolean resetPlayerPositions) {
        if (resetPlayerPositions) {
            int iSlot = 0;
            int fSlot = 0;
            for (Player p : onlineParticipants()) {
                TeamSide s = getTeam(p.getUniqueId());
                if (s == TeamSide.IGNIS) {
                    p.teleport(arena.teamSpawn(TeamSide.IGNIS, iSlot++));
                } else if (s == TeamSide.FROST) {
                    p.teleport(arena.teamSpawn(TeamSide.FROST, fSlot++));
                }
            }
        }

        Player handler = onlineParticipants().stream()
            .filter(p -> getTeam(p.getUniqueId()) == team)
            .findFirst()
            .orElse(null);

        if (handler != null && ball != null) {
            PlayerProfile prof = statsManager.getOrCreate(handler);
            boolean onFire = getBoxScore(handler.getUniqueId()).hotStreak >= plugin.settings().match().onFireStreakThreshold();
            ball.attachToHolder(handler, prof.selectedBallSkin(), onFire);
            syncBallHotbarItems(true);
        }
    }

    private void resetShotClock(boolean offensiveRebound14s) {
        int secs = offensiveRebound14s
            ? plugin.settings().match().offensiveReboundShotClock()
            : plugin.settings().match().shotClockSeconds();
        this.shotClockTicks = secs * 20;
    }

    public fr.bosketball.game.bot.BotSquad botSquad() {
        if (botSquad == null) {
            botSquad = new fr.bosketball.game.bot.BotSquad(arena, this);
            botSquad.attach(new BotSessionHost());
        }
        return botSquad;
    }

    public fr.bosketball.game.bot.BotDifficulty botDifficulty() {
        return botDifficulty;
    }

    public void setBotDifficulty(fr.bosketball.game.bot.BotDifficulty difficulty) {
        var next = difficulty == null ? fr.bosketball.game.bot.BotDifficulty.NORMAL : difficulty;
        boolean changed = next != this.botDifficulty;
        this.botDifficulty = next;
        if (changed && !respawnBotsGuard && hasBots()) {
            respawnBotsGuard = true;
            try {
                clearBots();
                fillWithBots(next);
            } finally {
                respawnBotsGuard = false;
            }
        }
    }

    private boolean respawnBotsGuard = false;

    public boolean hasBots() {
        return botSquad != null && !botSquad.isEmpty();
    }

    public void fillWithBots(fr.bosketball.game.bot.BotDifficulty difficulty) {
        setBotDifficulty(difficulty);
        var squad = botSquad();
        int perTeam = Math.max(1, mode.maxPlayers() / 2);
        for (TeamSide side : new TeamSide[]{TeamSide.IGNIS, TeamSide.FROST}) {
            int humans = countTeam(side);
            int bots = Math.max(0, perTeam - humans);
            for (int i = 0; i < bots; i++) {
                var avatar = squad.spawn(botDifficulty, side, humans + i);
                if (avatar != null) {
                    botTeamMap.put(avatar.entityId(), side);
                }
            }
        }
        squad.announce(onlineParticipants());
        if (!squad.isEmpty()) {
            broadcast("<" + Palette.ICE + "> <#F8FAFC>" + squad.size() + "</#F8FAFC> AI player(s) joined — difficulty <#F8FAFC>"
                + botDifficulty.displayName() + "</#F8FAFC>. <#64748B>(/bb bot difficulty)</#64748B>");
        }
    }

    public void clearBots() {
        if (botSquad != null) {
            for (var avatar : botSquad.avatars()) {
                botTeamMap.remove(avatar.entityId());
            }
            botSquad.clear();
        }
    }

    private final java.util.Map<UUID, TeamSide> botTeamMap = new java.util.concurrent.ConcurrentHashMap<>();

    private final class BotSessionHost implements fr.bosketball.game.bot.BotBrain.Handler {

        @Override
        public void botRespawned(fr.bosketball.game.bot.BotAvatar avatar, UUID previousId, TeamSide team) {
            if (previousId != null) {
                botTeamMap.remove(previousId);
            }
            if (avatar.entityId() != null && team != null) {
                botTeamMap.put(avatar.entityId(), team);
            }
        }

        @Override
        public boolean botShotClockPressure(fr.bosketball.game.bot.BotAvatar avatar) {
            return mode != GameModeType.PRACTICE && shotClockTicks <= 5 * 20;
        }

        @Override
        public boolean botTryPickup(fr.bosketball.game.bot.BotAvatar avatar) {
            if (ball == null || !avatar.isValid() || state != State.LIVE
                || !ball.canBePickedUpByVirtual(avatar.entityId(), avatar.location())) {
                return false;
            }
            botSquad.bindBall(ball, avatar);
            return true;
        }

        @Override
        public void botShoot(fr.bosketball.game.bot.BotAvatar avatar, Vector targetRim, double charge, Vector direction) {
            if (ball == null || !avatar.isValid() || !avatar.entityId().equals(ball.holderId())
                || ball.mode() != BasketballEntity.BallMode.DRIBBLING) {
                return;
            }
            Location eye = avatar.mannequin().getEyeLocation();
            Vector start = eye.toVector();
            Vector dir = direction.clone().normalize();
            boolean isThree = arena.geometry().isThreePointShot(
                avatar.location().getX(), avatar.location().getZ(), targetRim);
            double dist = Math.hypot(
                targetRim.getX() - avatar.location().getX(),
                targetRim.getZ() - avatar.location().getZ());

            var phys = plugin.settings().physics();
            double contest = computeBotContest(avatar);
            var aim = fr.bosketball.physics.PhysicsMath.evaluateShooterAim(start, dir, targetRim);
            var quality = ShotReleaseQuality.evaluate(
                charge, dist, false);
            Vector velocity = fr.bosketball.physics.PhysicsMath.solveSkillShotVelocity(
                start, dir, targetRim, arena.geometry().hoopForwardForRim(targetRim),
                aim, charge, phys.gravity(), phys.airDrag(),
                quality, contest, null);

            ball.launchShotVirtual(start, velocity, isThree, dist, avatar.entityId());
            avatar.swingArm(true);
            triggerRigAction(avatar.entityId(), AnimationKinematics.ActionClip.THROW_SHOT, null);
        }

        @Override
        public void botDunk(fr.bosketball.game.bot.BotAvatar avatar, Vector targetRim) {
            if (ball == null || !avatar.isValid() || !avatar.entityId().equals(ball.holderId())
                || ball.mode() != BasketballEntity.BallMode.DRIBBLING) {
                return;
            }
            ball.launchDunkVirtual(avatar.entityId(), targetRim, avatar.location(), 22,
                computeBotContest(avatar) < 0.45);
            avatar.jump(0.42);
            avatar.swingArm(true);
        }

        @Override
        public void botPass(fr.bosketball.game.bot.BotAvatar avatar, UUID targetId) {
            if (ball == null || !avatar.isValid() || !avatar.entityId().equals(ball.holderId())
                || ball.mode() != BasketballEntity.BallMode.DRIBBLING
                || targetId == null || getTeam(targetId) != botTeamMap.get(avatar.entityId())) {
                return;
            }
            Player human = Bukkit.getPlayer(targetId);
            var mate = botSquad.avatarById(targetId);
            Location destination = human != null && human.isOnline() ? human.getLocation()
                : mate != null && mate.isValid() ? mate.location() : null;
            if (destination == null || !destination.getWorld().equals(avatar.location().getWorld())) return;
            Vector from = avatar.location().add(0, 1.3, 0).toVector();
            Vector to = destination.toVector().add(new Vector(0, 1.1, 0));
            Vector velocity = to.subtract(from);
            double distance = velocity.length();
            if (distance < 0.01) return;
            ball.launchPassVirtual(from, velocity.multiply(Math.min(1.05, 0.32 + distance * 0.075) / distance),
                avatar.entityId());
        }

        @Override
        public void botDeflect(fr.bosketball.game.bot.BotAvatar avatar, Vector direction) {
            if (ball == null || ball.mode() != BasketballEntity.BallMode.DRIBBLING) return;
            UUID previousHolder = ball.holderId();
            Player holder = previousHolder == null ? null : Bukkit.getPlayer(previousHolder);
            if (holder != null) {
                activeShotCharges.remove(previousHolder);
                if (holder.isHandRaised()) holder.clearActiveItem();
            }
            Vector impulse = direction.clone().setY(0.24);
            if (impulse.lengthSquared() > 1e-4) impulse.normalize().multiply(0.42);
            ball.popLoose(impulse, avatar.entityId());
            Msg.playPackSound(avatar.location(), "ball.bounce", 0.9f, 1.2f);
            if (holder != null && holder.isOnline()) {
                Msg.send(holder, Palette.of(Palette.DANGER, avatar.profile().nickname() + " knocked it loose."));
            }
        }

        @Override
        public List<fr.bosketball.game.bot.BotAvatar> botAllies(fr.bosketball.game.bot.BotAvatar avatar) {
            return botSquad == null ? List.of() : botSquad.avatars();
        }

        @Override
        public List<fr.bosketball.game.bot.BotBrain.PassTarget> botPassTargets(fr.bosketball.game.bot.BotAvatar avatar) {
            TeamSide side = botTeamMap.get(avatar.entityId());
            List<fr.bosketball.game.bot.BotBrain.PassTarget> out = new java.util.ArrayList<>();
            for (Player p : onlineParticipants()) {
                if (getTeam(p.getUniqueId()) == side && p.getWorld().equals(avatar.location().getWorld())) {
                    out.add(new fr.bosketball.game.bot.BotBrain.PassTarget(p.getUniqueId(), p.getLocation().toVector()));
                }
            }
            if (botSquad != null) {
                for (var a : botSquad.avatars()) {
                    if (a.isValid() && !a.entityId().equals(avatar.entityId()) && botTeamMap.get(a.entityId()) == side) {
                        out.add(new fr.bosketball.game.bot.BotBrain.PassTarget(a.entityId(), a.position()));
                    }
                }
            }
            return out;
        }

        @Override
        public Vector botOffensiveRim(fr.bosketball.game.bot.BotAvatar avatar) {
            return arena.targetRimForTeam(botTeamMap.getOrDefault(avatar.entityId(), TeamSide.IGNIS));
        }

        @Override
        public Vector botDefensiveRim(fr.bosketball.game.bot.BotAvatar avatar) {
            return arena.targetRimForTeam(
                botTeamMap.getOrDefault(avatar.entityId(), TeamSide.IGNIS).opposite());
        }

        @Override
        public BasketballEntity botBall() {
            return ball;
        }

        @Override
        public Player botHumanCarrier() {
            if (ball == null || ball.holderId() == null || ball.hasVirtualHolder()) {
                return null;
            }
            Player carrier = Bukkit.getPlayer(ball.holderId());
            if (carrier == null || !participants.containsKey(carrier.getUniqueId())) {
                return null;
            }
            TeamSide side = getTeam(carrier.getUniqueId());
            return side == TeamSide.SPECTATOR ? null : carrier;
        }

        @Override
        public TeamSide botTeam(fr.bosketball.game.bot.BotAvatar avatar) {
            return botTeamMap.get(avatar.entityId());
        }
    }

    private double computeBotContest(fr.bosketball.game.bot.BotAvatar shooter) {
        TeamSide side = botTeamMap.get(shooter.entityId());
        double best = 0.0;
        for (Player p : onlineParticipants()) {
            if (getTeam(p.getUniqueId()) == side || !p.getWorld().equals(shooter.location().getWorld())) {
                continue;
            }
            double d = p.getLocation().distance(shooter.location());
            if (d < 3.2) {
                best = Math.max(best, (3.2 - d) / 3.2 * 0.7);
            }
        }
        if (botSquad != null) {
            for (var a : botSquad.avatars()) {
                if (!a.isValid() || botTeamMap.get(a.entityId()) == side) {
                    continue;
                }
                double d = a.position().distance(shooter.position());
                if (d < 3.2) {
                    best = Math.max(best, (3.2 - d) / 3.2 * 0.7);
                }
            }
        }
        return Math.min(0.85, best);
    }

    /**
     * Where the ball actually leaves the hands.
     *
     * <p>The old code always pushed the release point 0.45 blocks in front of the eyes. A player
     * standing against a wall, the backboard or the arena barrier therefore released the ball
     * inside a solid block, and the first tick of flight shoved it out through the far face: the
     * player never saw the ball leave and it reappeared across the court. Walk the point back
     * toward the eyes until it sits in free air.
     */
    private static Vector releasePoint(Player player, Vector direction, double reach) {
        org.bukkit.Location eye = player.getEyeLocation();
        Vector eyeVec = eye.toVector();
        Vector dir = direction == null ? new Vector() : direction.clone();
        if (dir.lengthSquared() < 1e-6) {
            dir = eye.getDirection();
        }
        if (dir.lengthSquared() < 1e-6) {
            return eyeVec;
        }
        dir.normalize();
        org.bukkit.World world = eye.getWorld();
        for (double distance : new double[]{reach, reach * 0.5, 0.0}) {
            Vector candidate = eyeVec.clone().add(dir.clone().multiply(distance));
            if (world == null || isFreeAir(world, candidate)) {
                return candidate;
            }
        }
        return eyeVec;
    }

    private static boolean isFreeAir(org.bukkit.World world, Vector point) {
        double r = fr.bosketball.physics.PhysicsMath.BALL_RADIUS;
        for (Vector probe : new Vector[]{point, point.clone().add(new Vector(r, 0, 0)),
            point.clone().add(new Vector(-r, 0, 0)), point.clone().add(new Vector(0, r, 0)),
            point.clone().add(new Vector(0, -r, 0)), point.clone().add(new Vector(0, 0, r)),
            point.clone().add(new Vector(0, 0, -r))}) {
            org.bukkit.block.Block block = probe.toLocation(world).getBlock();
            org.bukkit.util.BoundingBox box = block.getBoundingBox();
            if (box.getVolume() > 0.0 && box.contains(probe)) {
                return false;
            }
        }
        return true;
    }

    /** True while a team match is being played, where the lob restriction applies. */
    private boolean isLiveTeamPlay() {
        return state == State.LIVE
            && mode != GameModeType.THREE_POINT_CONTEST
            && mode != GameModeType.PRACTICE;
    }

    /** True when the player is already past half court on the side of the rim they attack. */
    private boolean isAttackingHalf(Player player, Vector targetRim) {
        Vector north = arena.geometry().northHoopRim();
        Vector south = arena.geometry().southHoopRim();
        Vector mid = north.add(south).multiply(0.5);
        Vector towardRim = targetRim.clone().subtract(mid);
        Vector fromMid = player.getLocation().toVector().subtract(mid);
        towardRim.setY(0);
        fromMid.setY(0);
        if (towardRim.lengthSquared() < 1e-6) {
            return true;
        }
        return towardRim.dot(fromMid) > 0.5;
    }

    private Vector selectClosestOrOffensiveRim(Player player, TeamSide team) {
        if (mode == GameModeType.THREE_POINT_CONTEST) {
            return arena.geometry().southHoopRim();
        }
        Vector n = arena.geometry().northHoopRim();
        Vector s = arena.geometry().southHoopRim();
        Vector eye = player.getEyeLocation().toVector();
        double distN = eye.distance(n);
        double distS = eye.distance(s);
        if (distN < 3.4 && distN < distS) {
            return n;
        }
        if (distS < 3.4 && distS < distN) {
            return s;
        }
        Vector look = player.getEyeLocation().getDirection();
        Vector horizLook = new Vector(look.getX(), 0, look.getZ());
        if (horizLook.lengthSquared() > 1e-4) {
            horizLook.normalize();
            Vector toN = new Vector(n.getX() - eye.getX(), 0, n.getZ() - eye.getZ()).normalize();
            Vector toS = new Vector(s.getX() - eye.getX(), 0, s.getZ() - eye.getZ()).normalize();
            double dotN = horizLook.dot(toN);
            double dotS = horizLook.dot(toS);
            if (Math.abs(dotN - dotS) > 0.15) {
                return dotN > dotS ? n : s;
            }
        }
        return distN < distS ? n : s;
    }

    private void syncBallHotbarItems(boolean force) {
        if (state == State.MVP_CEREMONY || state == State.FINISHED) {
            return;
        }
        UUID holderId = (ball != null) ? ball.holderId() : null;
        BallSkin activeSkin = null;
        if (holderId != null) {
            if (mode == GameModeType.THREE_POINT_CONTEST && currentShotIsMoneyBall) {
                activeSkin = BallSkin.TWO_TONE;
            } else if (getBoxScore(holderId).hotStreak >= plugin.settings().match().onFireStreakThreshold()) {
                activeSkin = BallSkin.FIRE;
            } else {
                Player holderPlayer = Bukkit.getPlayer(holderId);
                activeSkin = holderPlayer != null ? statsManager.getOrCreate(holderPlayer).selectedBallSkin() : BallSkin.CLASSIC;
            }
        }

        boolean holderChanged = !Objects.equals(holderId, lastSyncedHolderId) || activeSkin != lastSyncedHolderSkin;
        if (!force && !holderChanged) {

            if (holderId != null && activeSkin != null) {
                Player hp = Bukkit.getPlayer(holderId);
                if (hp != null && hp.isOnline() && getTeam(holderId) != TeamSide.SPECTATOR) {
                    ItemStack slot0 = hp.getInventory().getItem(0);
                    boolean ballItemUpToDate = slot0 != null
                        && slot0.getType() == Material.BOW
                        && slot0.hasItemMeta()
                        && slot0.getItemMeta() != null
                        && activeSkin.namespacedKey().equals(slot0.getItemMeta().getItemModel());
                    // Refresh the skin when needed, but never while a charge is live: replacing the
                    // held stack would abort the vanilla bow draw mid-charge.
                    if (!ballItemUpToDate && !isChargingShot(hp)) {
                        hp.getInventory().setItem(0, activeSkin.createBallItem(false));
                    }
                    ItemStack slot9 = hp.getInventory().getItem(BallSkin.HIDDEN_BOW_ARROW_SLOT);
                    if (slot9 == null || slot9.getType() != Material.ARROW) {
                        hp.getInventory().setItem(BallSkin.HIDDEN_BOW_ARROW_SLOT, BallSkin.createHiddenBowArrowItem());
                    }
                }
            }
            return;
        }
        boolean gainedNewHolder = holderId != null && !Objects.equals(holderId, lastSyncedHolderId);
        lastSyncedHolderId = holderId;
        lastSyncedHolderSkin = activeSkin;

        ItemStack defenseItem = BallSkin.createDefensiveStanceItem();
        for (Player p : onlineParticipants()) {
            if (getTeam(p.getUniqueId()) == TeamSide.SPECTATOR) {
                continue;
            }
            if (p.getUniqueId().equals(holderId) && activeSkin != null) {
                ItemStack existingS0 = p.getInventory().getItem(0);

                boolean holdingDraw = p.isHandRaised() || isChargingShot(p);
                if (!(holdingDraw && existingS0 != null && existingS0.getType() == Material.BOW)) {
                    if (!holdingDraw) {
                        p.getInventory().setItem(0, activeSkin.createBallItem(false));
                    }
                }
                ItemStack existingS9 = p.getInventory().getItem(BallSkin.HIDDEN_BOW_ARROW_SLOT);
                if (existingS9 == null || existingS9.getType() != Material.ARROW) {
                    p.getInventory().setItem(BallSkin.HIDDEN_BOW_ARROW_SLOT, BallSkin.createHiddenBowArrowItem());
                }
                if (gainedNewHolder && p.getInventory().getHeldItemSlot() != 0) {
                    p.getInventory().setHeldItemSlot(0);
                }
            } else {
                if (p.isHandRaised()) {
                    p.clearActiveItem();
                }
                p.getInventory().setItem(0, defenseItem);
                p.getInventory().setItem(BallSkin.HIDDEN_BOW_ARROW_SLOT, null);
            }
        }
    }

    private void equipCeremonyHotbar(Player player, boolean isMvp) {
        if (player.isHandRaised()) {
            player.clearActiveItem();
        }
        var inv = player.getInventory();
        inv.setItem(BallSkin.HIDDEN_BOW_ARROW_SLOT, null);
        for (int i = (isMvp ? 1 : 0); i < 9; i++) {
            inv.setItem(i, null);
        }
        int playAgainSlot = isMvp ? 1 : 0;
        inv.setItem(playAgainSlot, GuiMenuManager.customIcon(
            "icon_star_shield",
            "<" + Palette.SUCCESS + ">Play again</" + Palette.SUCCESS + "> <#64748B>\u00b7</#64748B> <#94A3B8>"
                + mode.smallCapsTitle() + "</#94A3B8>",
            List.of("<#94A3B8>Queue another match</#94A3B8>")
        ));
        int ggCoins = plugin.settings().economy().ggRewardCoins();
        inv.setItem(4, GuiMenuManager.customIcon(
            "ref_coins",
            "<" + Palette.GOLD + ">GG</" + Palette.GOLD + "> <#64748B>\u00b7</#64748B> <#94A3B8>"
                + (ggCoins > 0 ? "+" + ggCoins + " coins" : "say it in chat") + "</#94A3B8>",
            List.of("<#94A3B8>Claim the bonus</#94A3B8>")
        ));
        inv.setItem(8, GuiMenuManager.customIcon(
            "ref_exit",
            "<" + Palette.MUTED + ">Return to lobby</" + Palette.MUTED + ">",
            List.of("<#94A3B8>Back to the lobby</#94A3B8>")
        ));
    }

    private void equipCourtKit(Player player, TeamSide side) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.getInventory().clear();

        if (side == TeamSide.SPECTATOR) {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.getInventory().setItem(0, GuiMenuManager.customIcon(
                "icon_portal",
                "<" + Palette.ICE + "><bold> Follow Next Player</bold></" + Palette.ICE + ">",
                List.of("<#94A3B8>Click to cycle players</#94A3B8>")
            ));
            player.getInventory().setItem(4, GuiMenuManager.customIcon(
                "ref_medal_gold",
                "<" + Palette.GOLD + "><bold> Live Box Score</bold></" + Palette.GOLD + ">",
                List.of("<#94A3B8>Click to view live points, assists & rebounds</#94A3B8>")
            ));
            player.getInventory().setItem(8, GuiMenuManager.customIcon(
                "ref_exit",
                "<" + Palette.DANGER + "><bold> Stop Spectating</bold></" + Palette.DANGER + ">",
                List.of("<#94A3B8>Click to return to the Lobby</#94A3B8>")
            ));
            player.getInventory().setHeldItemSlot(0);
            return;
        }

        player.setAllowFlight(false);
        player.setFlying(false);

        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.118);
        }

        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(BallSkin.createCustomModelItem(
            Material.LEATHER_BOOTS,
            "sneakers_air",
            "<" + Palette.DANGER + "><bold>Court Sneakers</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>High-traction hardwood grip</#94A3B8>")
        ));

        PlayerProfile prof = statsManager.getOrCreate(player);
        player.getInventory().setItem(0, BallSkin.createDefensiveStanceItem());
        player.getInventory().setItem(1, GuiMenuManager.customIcon("ref_trophy",
            "<" + Palette.ICE + "><bold>Pass / Alley-Oop</bold></" + Palette.ICE + ">",
            List.of("<#94A3B8>Right-Click or Q: Pass · Look up at hoop = Lob</#94A3B8>")));
        player.getInventory().setItem(2, GuiMenuManager.customIcon("icon_crossover",
            "<" + Palette.VIOLET + "><bold>Crossover</bold></" + Palette.VIOLET + ">",
            List.of("<#94A3B8>Right-Click / Shift: directional dash (steer with WASD)</#94A3B8>")));
        player.getInventory().setItem(3, GuiMenuManager.customIcon(prof.selectedDunkStyle().iconModelId(),
            "<" + Palette.GOLD + "><bold>Dunk Style: " + prof.selectedDunkStyle().displayName() + "</bold></" + Palette.GOLD + ">",
            List.of(
                "<#94A3B8>1) Get inside the paint, facing the rim</#94A3B8>",
                "<#94A3B8>2) Click once to SLAM (20s cooldown)</#94A3B8>"
            )));
        if (mode == GameModeType.PRACTICE) {
            player.getInventory().setItem(4, BallSkin.createSparringBotItem(sparringBot != null));
        }
        player.getInventory().setItem(8, GuiMenuManager.customIcon("ref_exit",
            "<" + Palette.DANGER + "><bold>Leave Match</bold></" + Palette.DANGER + ">",
            List.of("<#94A3B8>Right-Click to return to the lobby</#94A3B8>")));
        player.getInventory().setHeldItemSlot(0);
    }

    
    
    private int countTeam(TeamSide side) {
        int c = 0;
        for (TeamSide s : participants.values()) {
            if (s == side) {
                c++;
            }
        }
        return c;
    }

    private int activeCompetitorCount() {
        return countTeam(TeamSide.IGNIS) + countTeam(TeamSide.FROST);
    }

    private double averageTeamElo(TeamSide side) {
        double sum = 0;
        int n = 0;
        for (Map.Entry<UUID, TeamSide> e : participants.entrySet()) {
            if (e.getValue() == side) {
                PlayerProfile prof = statsManager.getIfPresent(e.getKey());
                sum += (prof != null ? prof.elo() : 1000);
                n++;
            }
        }
        return n == 0 ? 1000.0 : sum / n;
    }

    public void broadcast(String miniMessage) {
        for (Player p : onlineParticipants()) {
            Msg.send(p, miniMessage);
        }
    }

    private void tickPlayerVisualRigs() {
        double floorY = arena.geometry().floorY();
        UUID holderId = ball != null ? ball.holderId() : null;
        double dribbleWave = (ball != null && ball.mode() == BasketballEntity.BallMode.DRIBBLING) ? ball.dribbleWave01() : 0.5;

        var it = visualRigs.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID pid = e.getKey();
            Player p = Bukkit.getPlayer(pid);
            if (p == null || !p.isOnline() || !participants.containsKey(pid) || getTeam(pid) == TeamSide.SPECTATOR) {
                e.getValue().despawn();
                it.remove();
                continue;
            }
            boolean holding = pid.equals(holderId);
            ShotChargeState ch = activeShotCharges.get(pid);
            double chargeProg = ch != null ? ch.progress : -1.0;
            int streak = getBoxScore(pid).hotStreak;
            e.getValue().tick(floorY, holding, dribbleWave, chargeProg, streak);
        }
    }

    private void triggerRigAction(UUID playerId, AnimationKinematics.ActionClip clip, fr.bosketball.model.DunkStyle dunkStyle) {
        PlayerVisualRig rig = visualRigs.get(playerId);
        if (rig != null) {
            rig.triggerAction(clip, dunkStyle);
        }
    }

    public void stopAndCleanup() {
        // The hand-off. The one-way door is the ledger, and the open step is closed before the
        // roster is torn down so the audit never reports the last 100 ms of a match as dead air.
        if (!enterState(State.FINISHED, MatchPhase.FINAL, MatchPhase.Surface.LOBBY)) {
            // The hand-off already happened (or was refused): the lobby still has to be reached.
            this.state = State.FINISHED;
        }
        for (Player p : onlineParticipants()) {
            if (p.isOnline()) {
                Msg.playPersonalPackSound(p, "ui.confirm", 0.6f, 1.05f);
            }
        }
        for (PlayerVisualRig rig : visualRigs.values()) {
            rig.despawn();
        }
        visualRigs.clear();
        if (ball != null) {
            ball.remove();
            ball = null;
        }
        if (sparringBot != null) {
            sparringBot.remove();
            sparringBot = null;
        }
        if (botSquad != null && !botSquad.isEmpty()) {
            clearBots();
        }
        Location lobbyExit = arena.lobbyExitLocationOrNull();
        boolean autoHotbar = plugin.settings().general().autoEquipLobbyPlaybook();
        List<UUID> ids = new ArrayList<>(participants.keySet());
        participants.clear();
        for (UUID id : ids) {
            if (plugin.gameManager() != null) {
                plugin.gameManager().unindexPlayerSession(id, this);
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.resyncVisibilityFor(p);
                if (plugin.cinematicCamera() != null) {
                    plugin.cinematicCamera().stopWatching(p, false);
                }
                hudManager.clearPlayer(p);
                SavedPlayerState st = savedStates.remove(id);
                if (st != null) {
                    st.restore(p, lobbyExit, autoHotbar);
                }
            }
        }
        savedStates.clear();
        activeShotCharges.clear();
        lastObservedPose.clear();
        idleTicksByPlayer.clear();
        spectatorTargetCursor.clear();
        arena.updateJumbotron("IGNIS", 0, "OPEN COURT", 24, 0, "FROST");
    }
}
