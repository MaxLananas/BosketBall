package fr.bosketball.game;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.DunkStyle;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.model.ShotReleaseQuality;
import fr.bosketball.model.TeamSide;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.physics.PhysicsMath;
import fr.bosketball.stats.StatsManager;
import fr.bosketball.ui.BossBarHudManager;
import fr.bosketball.util.Glyphs;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Complete Live Basketball Match & Solo Challenge Session Controller!
 * Powers:
 *  - 1v1 / 2v2 / 3v3 / 5v5 Competitive Matches (Quarters, 24s/14s Shot Clock, Tip-Off, Overtime, ELO & MVP Ceremony)
 *  - NBA All-Star 3-Point Contest (5 Racks x 5 Balls incl. 2-Point Money Balls, 60s timer)
 *  - Free Practice / Dunk & Shootaround Lab
 *  - NBA 2K Green-Window Shot Meter, 5-Style Slam Dunks, Crossovers, Alley-Oops, Steals & Mid-Air Blocks!
 */
public final class MatchSession implements BasketballEntity.ScoreListener {

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

        public double mvpScore() {
            return points * 1.0 + assists * 1.6 + rebounds * 1.2 + steals * 2.1 + blocks * 2.3 + dunks * 0.4;
        }
    }

    private static final class ShotChargeState {
        double progress = 0.06;
        int ticksActive = 0;
        int ticksSinceLastHoldPing = 0;
        boolean usingConsumableHold = false;
    }

    private static final class SavedPlayerState {
        final ItemStack[] contents;
        final ItemStack[] armor;
        final Location returnLocation;
        final GameMode gameMode;

        SavedPlayerState(Player p) {
            this.contents = p.getInventory().getContents().clone();
            this.armor = p.getInventory().getArmorContents().clone();
            this.returnLocation = p.getLocation().clone();
            this.gameMode = p.getGameMode();
        }

        void restore(Player p) {
            p.getInventory().setContents(contents);
            p.getInventory().setArmorContents(armor);
            p.setGameMode(gameMode);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.10);
            }
            if (returnLocation.getWorld() != null) {
                p.teleport(returnLocation);
            }
        }
    }

    private final BosketBallPlugin plugin;
    private final Arena arena;
    private final GameModeType mode;
    private final BossBarHudManager hudManager;
    private final StatsManager statsManager;
    private final Random rng = new Random();

    private final Map<UUID, TeamSide> participants = new LinkedHashMap<>();
    private final Map<UUID, SavedPlayerState> savedStates = new HashMap<>();
    private final Map<UUID, MatchBoxScore> boxScores = new HashMap<>();
    private final Map<TeamSide, Integer> scores = new EnumMap<>(TeamSide.class);

    private final Map<UUID, ShotChargeState> activeShotCharges = new HashMap<>();
    private final Map<UUID, Long> stealCooldowns = new HashMap<>();
    private final Map<UUID, Long> crossoverCooldowns = new HashMap<>();
    private final Map<UUID, Long> blockCooldowns = new HashMap<>();

    private State state = State.WAITING;
    private BasketballEntity ball;

    private int currentQuarter = 1;
    private final int totalQuarters;
    private int ticksRemainingInPeriod;
    private int shotClockTicks;
    private int stateTimerTicks;
    private int warmupBannerTicks = 0;
    private int tickCounter = 0;
    private UUID lastSyncedHolderId = null;
    private BallSkin lastSyncedHolderSkin = null;

    // Celebration Banner Cache (matches image-1.png!)
    private String celebScorerName = "";
    private String celebRoleTag = "";
    private Glyphs.BannerVariant celebVariant = Glyphs.BannerVariant.REGULAR;
    private String celebSubLabel = "";
    private String celebSubValue = "";
    private TeamSide nextInboundTeam = TeamSide.IGNIS;

    // 3-Point Contest state
    private int contestRackIndex = 0; // 0..4
    private int contestBallInRack = 0; // 0..4 (index 4 = Money Ball worth 2 pts!)
    private boolean currentShotIsMoneyBall = false;
    private int contestShotSequence = 0;

    public MatchSession(BosketBallPlugin plugin, Arena arena, GameModeType mode) {
        this.plugin = plugin;
        this.arena = arena;
        this.mode = mode;
        this.hudManager = plugin.bossBarHud();
        this.statsManager = plugin.statsManager();
        this.totalQuarters = mode == GameModeType.DUEL_1V1 ? 2 : plugin.getConfig().getInt("match.quarters", 4);
        scores.put(TeamSide.IGNIS, 0);
        scores.put(TeamSide.FROST, 0);
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
        return participants.getOrDefault(playerId, TeamSide.SPECTATOR);
    }

    public MatchBoxScore getBoxScore(UUID playerId) {
        return boxScores.computeIfAbsent(playerId, k -> new MatchBoxScore());
    }

    public int participantCount() {
        return participants.size();
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
        if (state == State.WAITING) {
            return plugin.getConfig().getInt("match.shot-clock-seconds", 24);
        }
        return Math.max(0, (int) Math.ceil(shotClockTicks / 20.0));
    }

    public String formattedClock() {
        if (mode == GameModeType.PRACTICE) {
            return "ʟɪʙʀᴇ";
        }
        if (state == State.WAITING) {
            return "ᴀᴛᴛᴇɴᴛᴇ " + activeCompetitorCount() + "/" + mode.maxPlayers();
        }
        if (state == State.COUNTDOWN) {
            return "ᴅᴇᴘᴀʀᴛ " + Math.max(1, (int) Math.ceil(stateTimerTicks / 20.0)) + "ꜱ";
        }
        int totalSec = Math.max(0, ticksRemainingInPeriod / 20);
        int mins = totalSec / 60;
        int secs = totalSec % 60;
        String qPrefix = mode == GameModeType.THREE_POINT_CONTEST
            ? "3ᴘᴛ "
            : (currentQuarter > totalQuarters ? "OT " : "Q" + currentQuarter + " ");
        return String.format("%s%02d:%02d", qPrefix, mins, secs);
    }

    public String currentBallCarrierName() {
        if (ball == null || ball.holderId() == null) {
            return "ʟɪʙʀᴇ";
        }
        Player holder = Bukkit.getPlayer(ball.holderId());
        return holder != null ? holder.getName() : "ʟɪʙʀᴇ";
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

        savedStates.put(player.getUniqueId(), new SavedPlayerState(player));
        participants.put(player.getUniqueId(), assigned);
        boxScores.putIfAbsent(player.getUniqueId(), new MatchBoxScore());

        equipCourtKit(player, assigned);
        player.teleport(arena.teamSpawn(assigned, countTeam(assigned) - 1));
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.15f);

        broadcast("<#" + Integer.toHexString(assigned.textColor().value()) + "><bold>" + player.getName()
            + "</bold></#" + Integer.toHexString(assigned.textColor().value())
            + "> <gray>ᴀ ʀᴇᴊᴏɪɴᴛ ʟᴇ ᴛᴇʀʀᴀɪɴ</gray> <gold>" + arena.displayName() + "</gold> "
            + "<dark_gray>(" + activeCompetitorCount() + "/" + mode.maxPlayers() + ")</dark_gray>");

        if (state == State.WAITING && activeCompetitorCount() >= mode.minTotalPlayersToStart()) {
            beginCountdown();
        } else if (state == State.WAITING && assigned != TeamSide.SPECTATOR) {
            // Spawn interactive Warmup Basketball so waiting players can immediately shoot & dunk!
            if (ball == null) {
                spawnOrResetBallEntity();
                PlayerProfile prof = statsManager.getOrCreate(player);
                ball.attachToHolder(player, prof.selectedBallSkin(), false);
            }
            syncBallHotbarItems(true);
            String adminHint = player.hasPermission("bosketball.admin")
                ? " <click:run_command:'/bb forcestart'><hover:show_text:'<gold>ᴅᴇᴍᴀʀʀᴇʀ ʟᴇ ᴍᴀᴛᴄʜ ᴍᴀɪɴᴛᴇɴᴀɴᴛ</gold>'><gold><bold>[⚡ ꜰᴏʀᴄᴇꜱᴛᴀʀᴛ]</bold></gold></hover></click>"
                : "";
            Msg.send(player, "<yellow>🏀 <bold>ᴇᴄʜᴀᴜꜰꜰᴇᴍᴇɴᴛ ʟɪʙʀᴇ</bold> ᴇɴ ᴀᴛᴛᴇɴᴛᴇ ᴅ'ᴀᴅᴠᴇʀꜱᴀɪʀᴇꜱ !</yellow> "
                + "<click:run_command:'/bb practice'><hover:show_text:'<green>ᴘᴀꜱꜱᴇʀ ᴇɴ ᴇɴᴛʀᴀɪɴᴇᴍᴇɴᴛ ꜱᴏʟᴏ</green>'><aqua><bold>[▶ ᴍᴏᴅᴇ ꜱᴏʟᴏ]</bold></aqua></hover></click> "
                + "<click:run_command:'/bb 3pt'><hover:show_text:'<yellow>ʟᴀɴᴄᴇʀ ʟᴇ ᴄᴏɴᴄᴏᴜʀꜱ ᴀ 3 ᴘᴏɪɴᴛꜱ</yellow>'><yellow><bold>[🎯 ᴄᴏɴᴄᴏᴜʀꜱ 3-ᴘᴛꜱ]</bold></yellow></hover></click>"
                + adminHint);
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
        participants.remove(id);
        activeShotCharges.remove(id);
        hudManager.clearPlayer(player);

        if (ball != null && id.equals(ball.holderId())) {
            ball.popLoose(new Vector(0, 0.22, 0), null);
        }

        SavedPlayerState saved = savedStates.remove(id);
        if (saved != null) {
            saved.restore(player);
        }

        if (!silent) {
            broadcast("<gray>" + player.getName() + " ᴀ ǫᴜɪᴛᴛᴇ ʟᴇ ᴍᴀᴛᴄʜ.</gray>");
        }

        if (activeCompetitorCount() == 0 && state != State.FINISHED) {
            stopAndCleanup();
        } else if (mode.competitiveTeamMatch() && (state == State.LIVE || state == State.CELEBRATION)) {
            if (countTeam(TeamSide.IGNIS) == 0 || countTeam(TeamSide.FROST) == 0) {
                finishMatchAndStartMvpCeremony();
            }
        }
    }

    public void forceStart() {
        if (state == State.WAITING && activeCompetitorCount() >= 1) {
            beginCountdown();
        }
    }

    private void beginCountdown() {
        if (!mode.competitiveTeamMatch()) {
            // Instant start for Practice & 3PT Contest!
            startLivePlay();
            return;
        }
        this.state = State.COUNTDOWN;
        this.stateTimerTicks = plugin.getConfig().getInt("match.countdown-seconds", 5) * 20;
        broadcast("<yellow>ʟᴇ ᴍᴀᴛᴄʜ ᴄᴏᴍᴍᴇɴᴄᴇ ᴅᴀɴꜱ <bold>" + (stateTimerTicks / 20) + " ꜱᴇᴄᴏɴᴅᴇꜱ</bold> !</yellow>");
    }

    private void startLivePlay() {
        this.state = State.LIVE;
        this.currentQuarter = 1;
        this.warmupBannerTicks = 0;
        scores.put(TeamSide.IGNIS, 0);
        scores.put(TeamSide.FROST, 0);
        boxScores.clear();
        activeShotCharges.clear();

        spawnOrResetBallEntity();
        syncBallHotbarItems(true);

        if (mode == GameModeType.PRACTICE) {
            this.ticksRemainingInPeriod = 20 * 3600;
            this.shotClockTicks = 24 * 20;
            Player solo = onlineParticipants().stream().findFirst().orElse(null);
            if (solo != null) {
                solo.teleport(arena.teamSpawn(TeamSide.IGNIS, 0));
                PlayerProfile prof = statsManager.getOrCreate(solo);
                ball.attachToHolder(solo, prof.selectedBallSkin(), false);
                Msg.title(
                    solo,
                    "<gradient:#FF7A18:#FFD43B><bold>🏀 ᴇɴᴛʀᴀɪɴᴇᴍᴇɴᴛ ʟɪʙʀᴇ</bold></gradient>",
                    "<gray>ᴍᴀɪɴᴛᴇɴᴇᴢ ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴘᴏᴜʀ ᴛɪʀᴇʀ • ꜱᴘʀɪɴᴛ + ꜱᴀᴜᴛ ᴘᴏᴜʀ ᴅᴜɴᴋᴇʀ !</gray>",
                    200, 2400, 400
                );
            }
            return;
        }

        if (mode == GameModeType.THREE_POINT_CONTEST) {
            this.ticksRemainingInPeriod = 60 * 20; // 60 seconds for 25 shots!
            this.shotClockTicks = 60 * 20;
            this.contestRackIndex = 0;
            this.contestBallInRack = 0;
            Player shooter = onlineParticipants().stream().findFirst().orElse(null);
            if (shooter != null) {
                advanceContestShot(shooter, true);
                Msg.title(
                    shooter,
                    "<gradient:#00F0FF:#FFD43B><bold>🎯 ᴄᴏɴᴄᴏᴜʀꜱ ᴀ 3 ᴘᴏɪɴᴛꜱ</bold></gradient>",
                    "<gray>5 ʀᴀᴄᴋꜱ • 25 ʙᴀʟʟᴏɴꜱ • 60 ꜱᴇᴄᴏɴᴅᴇꜱ • ᴍᴏɴᴇʏ ʙᴀʟʟ = 2 ᴘᴛꜱ !</gray>",
                    200, 2400, 400
                );
                Msg.playPackSound(shooter.getLocation(), "game.whistle", 1.0f, 1.05f);
            }
            return;
        }

        // Competitive Match Tip-Off!
        int qSec = plugin.getConfig().getInt("match.quarter-seconds", 90);
        this.ticksRemainingInPeriod = qSec * 20;
        resetShotClock(false);

        int ignisIdx = 0;
        int frostIdx = 0;
        for (Player p : onlineParticipants()) {
            TeamSide side = getTeam(p.getUniqueId());
            if (side == TeamSide.IGNIS) {
                p.teleport(arena.teamSpawn(TeamSide.IGNIS, ignisIdx++));
            } else if (side == TeamSide.FROST) {
                p.teleport(arena.teamSpawn(TeamSide.FROST, frostIdx++));
            }
            Msg.title(
                p,
                "<gradient:#FF4D3D:#FFD43B:#3DB8FF><bold>⚡ ᴇɴᴛʀᴇ-ᴅᴇᴜx !</bold></gradient>",
                "<white>ɪɢɴɪꜱ</white> <dark_gray>ᴠꜱ</dark_gray> <white>ꜰʀᴏꜱᴛ</white> <dark_gray>•</dark_gray> <gold>" + mode.smallCapsTitle() + "</gold>",
                150, 1600, 350
            );
        }

        Msg.playPackSound(arena.centerCourtLocation(), "game.whistle", 1.2f, 1.0f);
        ball.resetToCenterTipOff(
            new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ()),
            0.46
        );
    }

    private void spawnOrResetBallEntity() {
        if (ball != null) {
            ball.remove();
        }
        this.ball = new BasketballEntity(
            arena,
            arena.centerCourtLocation().add(0, 1.2, 0),
            plugin.getConfig().getDouble("physics.gravity", 0.046),
            plugin.getConfig().getDouble("physics.air-drag", 0.991),
            plugin.getConfig().getDouble("physics.floor-restitution", 0.76),
            plugin.getConfig().getDouble("physics.backboard-restitution", 0.66),
            plugin.getConfig().getDouble("physics.rim-restitution", 0.56)
        );
    }

    /**
     * Main 20Hz Server Tick for this MatchSession!
     */
    public void tick() {
        tickCounter++;
        arena.tickDisplays(shotClockSeconds());

        if (state == State.WAITING) {
            if (ball != null) {
                ball.tick(this);
                if (ball.holderId() == null) {
                    checkPlayerBallInterceptionAndRebounds();
                }
            }
            tickShotCharges();
            syncBallHotbarItems(false);

            if (warmupBannerTicks > 0) {
                warmupBannerTicks--;
                for (Player p : onlineParticipants()) {
                    hudManager.showScorerCelebration(p, celebScorerName, celebRoleTag, celebVariant, celebSubLabel, celebSubValue);
                }
            } else if (tickCounter % 10 == 0) {
                String waitLabel = "ᴀᴛᴛᴇɴᴛᴇ " + activeCompetitorCount() + "/" + mode.maxPlayers();
                for (Player p : onlineParticipants()) {
                    hudManager.showLiveScorebug(p, "IGNIS", score(TeamSide.IGNIS), waitLabel, 24, score(TeamSide.FROST), "FROST");
                }
                arena.updateJumbotron("ɪɢɴɪꜱ", score(TeamSide.IGNIS), "ᴇɴ ᴀᴛᴛᴇɴᴛᴇ (" + activeCompetitorCount() + "/" + mode.maxPlayers() + ")", 24, score(TeamSide.FROST), "ꜰʀᴏꜱᴛ");
            }
            return;
        }

        if (state == State.COUNTDOWN) {
            activeShotCharges.clear();
            stateTimerTicks--;
            if (stateTimerTicks > 0 && stateTimerTicks % 20 == 0) {
                int sec = stateTimerTicks / 20;
                for (Player p : onlineParticipants()) {
                    Msg.title(p, "<gold><bold>" + sec + "</bold></gold>", "<gray>ᴘʀᴇᴘᴀʀᴇᴢ-ᴠᴏᴜꜱ ᴀᴜ ᴄᴏᴜᴘ ᴅ'ᴇɴᴠᴏɪ !</gray>", 50, 800, 150);
                    Msg.playPersonalPackSound(p, "ui.click", 0.9f, 1.0f + (5 - sec) * 0.08f);
                    hudManager.showLiveScorebug(p, "IGNIS", 0, "ᴅᴇᴘᴀʀᴛ " + sec + "ꜱ", 24, 0, "FROST");
                }
                arena.updateJumbotron("ɪɢɴɪꜱ", 0, "ᴄᴏᴜᴘ ᴅ'ᴇɴᴠᴏɪ " + sec + "ꜱ", 24, 0, "ꜰʀᴏꜱᴛ");
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
            if (stateTimerTicks <= 0) {
                stopAndCleanup();
            }
            return;
        }

        // Advance 3D Basketball Physics & Loose-Ball Auto-Pickup!
        if (ball != null) {
            ball.tick(state == State.LIVE ? this : null);
            if (state == State.LIVE && ball.holderId() == null) {
                checkPlayerBallInterceptionAndRebounds();
            }
        }

        // Advance active NBA 2K Shot Meters & keep Slot 0 synced with actual ball carrier!
        tickShotCharges();
        syncBallHotbarItems(false);

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
        }
    }

    private void tickLiveClocksAndHud() {
        if (mode != GameModeType.PRACTICE) {
            ticksRemainingInPeriod--;
        }

        // Shot Clock ticks whenever a team holds the ball or a pass is in flight (pauses once shot reaches rim!)
        boolean possessionClockActive = ball != null && (
            ball.holderId() != null
                || ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT
                || ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT
        );
        if (mode.competitiveTeamMatch() && possessionClockActive) {
            shotClockTicks--;
            if (shotClockTicks > 0 && shotClockTicks <= 100 && shotClockTicks % 20 == 0) {
                for (Player p : onlineParticipants()) {
                    Msg.playPersonalPackSound(p, "ui.click", 0.65f, 1.35f);
                }
            }
            if (shotClockTicks <= 0) {
                handleShotClockViolation();
                return;
            }
        }

        // Refresh Live Broadcast Scorebug every 5 ticks (4x/sec), or show brief Practice Celebration Banner!
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

            for (Player p : onlineParticipants()) {
                hudManager.showLiveScorebug(p, "IGNIS", sIgnis, clockStr, sc, sFrost, "FROST");
            }
            arena.updateJumbotron("ɪɢɴɪꜱ", sIgnis, clockStr, sc, sFrost, "ꜰʀᴏꜱᴛ");
        }

        // Period / Quarter End Check!
        if (mode != GameModeType.PRACTICE && ticksRemainingInPeriod <= 0) {
            handlePeriodEndBuzzer();
        }
    }

    private void checkPlayerBallInterceptionAndRebounds() {
        for (Player p : onlineParticipants()) {
            if (getTeam(p.getUniqueId()) == TeamSide.SPECTATOR) {
                continue;
            }
            // Mid-air Alley-Oop Finish: jumping near the rim while a teammate's pass arrives!
            if (ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT && !p.isOnGround()) {
                TeamSide myTeam = getTeam(p.getUniqueId());
                Vector targetRim = arena.targetRimForTeam(myTeam);
                if (p.getLocation().toVector().distanceSquared(targetRim) <= 4.2 * 4.2
                    && p.getEyeLocation().toVector().distanceSquared(ball.position()) <= 2.1 * 2.1) {
                    PlayerProfile prof = statsManager.getOrCreate(p);
                    ball.startSlamDunk(p, targetRim, prof.selectedDunkStyle(), 12);
                    Msg.title(p, "<gradient:#FF7A18:#FFE259><bold>🔥 ᴀʟʟᴇʏ-ᴏᴏᴘ !</bold></gradient>", "", 50, 900, 200);
                    return;
                }
            }

            if (ball.canBePickedUpBy(p)) {
                UUID prevHolder = ball.lastTouchId();
                boolean wasMissedShot = (prevHolder != null && !prevHolder.equals(p.getUniqueId()));
                PlayerProfile prof = statsManager.getOrCreate(p);
                MatchBoxScore box = getBoxScore(p.getUniqueId());

                if (wasMissedShot && ball.position().getY() > arena.geometry().floorY() + 0.65) {
                    box.rebounds++;
                    prof.incrementRebounds();
                    // Offensive rebound resets shot clock to 14s, defensive rebound to 24s!
                    TeamSide prevTeam = getTeam(prevHolder);
                    boolean offensiveRebound = (prevTeam == getTeam(p.getUniqueId()));
                    resetShotClock(offensiveRebound);
                } else if (prevHolder != null && getTeam(prevHolder) != getTeam(p.getUniqueId())) {
                    resetShotClock(false);
                }

                boolean onFire = box.hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3);
                ball.attachToHolder(p, prof.selectedBallSkin(), onFire);
                activeShotCharges.remove(p.getUniqueId());

                // In 3PT Contest, if a shot missed and touched floor, automatically feed next ball from rack!
                if (mode == GameModeType.THREE_POINT_CONTEST) {
                    advanceContestShot(p, false);
                }
                return;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //   PLAYER COURT MECHANICS: SHOOT, SLAM DUNK, PASS/LOB, CROSSOVER, STEAL, BLOCK
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isPlayableState() {
        return state == State.LIVE || state == State.WAITING;
    }

    /**
     * Triggered when the ball-handler right-clicks (starts or continues holding the NBA 2K Shot Meter,
     * OR triggers an immediate 3D Slam Dunk if sprinting inside the paint/dunk zone!).
     *
     * @return true if starting/continuing a Jump-Shot Charge (so Consumable right-click hold stays active!)
     */
    public boolean handleBallHandlerPrimaryUse(Player player, boolean fromBallSlot) {
        if (!isPlayableState() || ball == null || !player.getUniqueId().equals(ball.holderId())) {
            return false;
        }
        TeamSide team = getTeam(player.getUniqueId());
        Vector targetRim = (mode == GameModeType.THREE_POINT_CONTEST || mode == GameModeType.PRACTICE || state == State.WAITING)
            ? selectClosestOrOffensiveRim(player, team)
            : arena.targetRimForTeam(team);

        // 1. Check 3D Slam Dunk trigger: Sprinting inside Dunk Zone (disabled in 3PT Contest)!
        if (mode != GameModeType.THREE_POINT_CONTEST
            && player.isSprinting()
            && arena.geometry().isInDunkZone(player.getLocation().getX(), player.getLocation().getZ(), targetRim)) {
            activeShotCharges.remove(player.getUniqueId());
            PlayerProfile prof = statsManager.getOrCreate(player);
            int flightTicks = Math.max(11, Math.min(18, (int) Math.round(player.getLocation().toVector().distance(targetRim) * 3.4)));
            ball.startSlamDunk(player, targetRim, prof.selectedDunkStyle(), flightTicks);
            return false;
        }

        // 2. Start or refresh Hold-and-Release NBA 2K Shot Meter!
        ShotChargeState charge = activeShotCharges.get(player.getUniqueId());
        if (charge == null) {
            charge = new ShotChargeState();
            charge.usingConsumableHold = fromBallSlot;
            activeShotCharges.put(player.getUniqueId(), charge);
            Msg.playPersonalPackSound(player, "ui.click", 0.55f, 0.95f);
        } else {
            charge.ticksSinceLastHoldPing = 0;
            if (fromBallSlot) {
                charge.usingConsumableHold = true;
            }
        }
        return fromBallSlot;
    }

    /**
     * Allows crisp 1-tick exact shot release via PlayerStopUsingItemEvent (Right-Click release) or Left-Click!
     */
    public boolean tryReleaseChargedShotNow(Player player) {
        ShotChargeState charge = activeShotCharges.remove(player.getUniqueId());
        if (charge == null) {
            return false;
        }
        executeJumpShotRelease(player, charge.progress);
        return true;
    }

    private void tickShotCharges() {
        if (activeShotCharges.isEmpty()) {
            return;
        }
        var it = activeShotCharges.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID playerId = entry.getKey();
            ShotChargeState st = entry.getValue();
            Player p = Bukkit.getPlayer(playerId);

            if (p == null || !p.isOnline() || ball == null || !playerId.equals(ball.holderId())) {
                it.remove();
                continue;
            }

            st.ticksActive++;
            st.progress += 0.058; // Reaches 0.92 sweet spot in ~14.8 ticks (~0.74s)!
            st.ticksSinceLastHoldPing++;

            p.sendActionBar(Glyphs.composeShotMeter(st.progress, false, null));

            // 1-tick exact release when right-click is let go (via Consumable isHandRaised), fallback ping timeout, or max!
            boolean releasedConsumable = st.usingConsumableHold && st.ticksActive >= 2 && !p.isHandRaised();
            boolean releasedFallback = !st.usingConsumableHold && st.progress >= 0.36 && st.ticksSinceLastHoldPing >= 5;
            boolean overcharged = (st.progress >= 1.18);
            if (releasedConsumable || releasedFallback || overcharged) {
                double finalCharge = st.progress;
                it.remove();
                if (p.isHandRaised()) {
                    p.clearActiveItem();
                }
                executeJumpShotRelease(p, finalCharge);
            }
        }
    }

    private void executeJumpShotRelease(Player shooter, double chargeProgress) {
        if (ball == null || !shooter.getUniqueId().equals(ball.holderId())) {
            return;
        }
        TeamSide team = getTeam(shooter.getUniqueId());
        Vector targetRim = (mode == GameModeType.THREE_POINT_CONTEST || mode == GameModeType.PRACTICE || state == State.WAITING)
            ? selectClosestOrOffensiveRim(shooter, team)
            : arena.targetRimForTeam(team);

        Location loc = shooter.getLocation();
        double dist = Math.hypot(loc.getX() - targetRim.getX(), loc.getZ() - targetRim.getZ());
        boolean isThree = arena.geometry().isThreePointShot(loc.getX(), loc.getZ(), targetRim);
        MatchBoxScore box = getBoxScore(shooter.getUniqueId());
        boolean onFire = box.hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3);

        ShotReleaseQuality quality = ShotReleaseQuality.evaluate(chargeProgress, dist, onFire);
        shooter.sendActionBar(Glyphs.composeShotMeter(chargeProgress, true, quality));

        if (quality == ShotReleaseQuality.PERFECT_GREEN) {
            Msg.playPersonalPackSound(shooter, "game.green_release", 1.0f, 1.05f);
            shooter.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                shooter.getLocation().add(0, 2.05, 0),
                12, 0.28, 0.18, 0.28, 0.02
            );
        }

        // Small realistic hop when releasing a jumper if grounded
        if (shooter.isOnGround()) {
            Vector v = shooter.getVelocity();
            shooter.setVelocity(new Vector(v.getX() * 0.65, 0.28, v.getZ() * 0.65));
        }

        Vector start = shooter.getEyeLocation().toVector().add(shooter.getLocation().getDirection().multiply(0.45));
        Vector velocity = PhysicsMath.solveBallisticArc(
            start,
            targetRim,
            plugin.getConfig().getDouble("physics.gravity", 0.046),
            plugin.getConfig().getDouble("physics.air-drag", 0.991),
            quality,
            rng
        );

        if (state == State.LIVE) {
            PlayerProfile prof = statsManager.getOrCreate(shooter);
            // Track attempt immediately (made flag updated on score!)
            if (isThree) {
                prof.setThreePointersAttempted(prof.threePointersAttempted() + 1);
            } else {
                prof.setTwoPointersAttempted(prof.twoPointersAttempted() + 1);
            }
        }

        ball.launchShot(shooter, targetRim, velocity, isThree, dist);

        // In 3PT Contest, schedule auto-feed of next rack ball 30 ticks after release ONLY if same shot is still loose!
        if (mode == GameModeType.THREE_POINT_CONTEST) {
            int expectedSeq = this.contestShotSequence;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == State.LIVE
                    && this.contestShotSequence == expectedSeq
                    && shooter.isOnline()
                    && ball != null
                    && ball.holderId() == null) {
                    advanceContestShot(shooter, false);
                }
            }, 32L);
        }
    }

    /**
     * Laser Pass or High Alley-Oop Lob (Press Q / Drop Item or Click Slot 1)!
     */
    public void handlePassOrAlleyOop(Player passer) {
        if (!isPlayableState() || ball == null || !passer.getUniqueId().equals(ball.holderId())) {
            return;
        }
        activeShotCharges.remove(passer.getUniqueId());
        if (passer.isHandRaised()) {
            passer.clearActiveItem();
        }
        TeamSide myTeam = getTeam(passer.getUniqueId());

        // Find best teammate in look cone!
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

        boolean alleyOop = passer.getLocation().getPitch() < -22.0f;
        Vector start = passer.getEyeLocation().toVector().add(eyeDir.clone().multiply(0.55));
        Vector passVel;

        if (alleyOop) {
            Vector rimBase = (mode == GameModeType.PRACTICE || state == State.WAITING)
                ? selectClosestOrOffensiveRim(passer, myTeam)
                : arena.targetRimForTeam(myTeam);
            Vector rim = rimBase.clone().add(new Vector(0, 0.55, 0));
            passVel = PhysicsMath.solveBallisticArc(
                start, rim,
                plugin.getConfig().getDouble("physics.gravity", 0.046),
                plugin.getConfig().getDouble("physics.air-drag", 0.991),
                ShotReleaseQuality.PERFECT_GREEN, null
            );
            passer.sendActionBar(Msg.parse("<gradient:#FF7A18:#FFE259><bold>🚀 ʟᴏʙ ᴀʟʟᴇʏ-ᴏᴏᴘ ᴇɴᴠᴏʏᴇ !</bold></gradient>"));
        } else if (targetMate != null) {
            Vector aimChest = targetMate.getLocation().toVector().add(new Vector(0, 1.15, 0)).add(targetMate.getVelocity().multiply(6.0));
            Vector delta = aimChest.subtract(start);
            double speed = Math.min(1.35, Math.max(0.78, delta.length() * 0.14));
            passVel = delta.normalize().multiply(speed).add(new Vector(0, 0.08, 0));
            passer.sendActionBar(Msg.parse("<aqua>⚡ ᴘᴀꜱꜱᴇ ᴠᴇʀꜱ <bold>" + targetMate.getName() + "</bold> !</aqua>"));
        } else {
            passVel = eyeDir.clone().multiply(0.95).add(new Vector(0, 0.10, 0));
        }

        ball.launchPass(passer, start, passVel, alleyOop);
    }

    /**
     * Ankle-Breaker Crossover (Sneak toggle or Slot 2 right-click)!
     */
    public void handleCrossoverMove(Player handler) {
        if (!isPlayableState() || ball == null || !handler.getUniqueId().equals(ball.holderId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("physics.crossover-cooldown-ms", 2200L);
        if (now - crossoverCooldowns.getOrDefault(handler.getUniqueId(), 0L) < cd) {
            return;
        }
        crossoverCooldowns.put(handler.getUniqueId(), now);

        ball.triggerCrossoverHandSwitch(handler);

        // Quick lateral burst!
        double yawRad = Math.toRadians(handler.getLocation().getYaw());
        Vector forward = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vector lateral = new Vector(Math.cos(yawRad), 0, Math.sin(yawRad)).multiply(rng.nextBoolean() ? 0.62 : -0.62);
        handler.setVelocity(forward.multiply(0.52).add(lateral).setY(0.16));

        // Check if an opposing defender within 2.9m gets their ankles broken!
        TeamSide myTeam = getTeam(handler.getUniqueId());
        for (Player def : onlineParticipants()) {
            if (getTeam(def.getUniqueId()) == myTeam.opposite()
                && def.getLocation().distanceSquared(handler.getLocation()) <= 2.9 * 2.9) {
                def.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 32, 2, false, false, true));
                def.getWorld().spawnParticle(Particle.CLOUD, def.getLocation().add(0, 0.2, 0), 16, 0.3, 0.1, 0.3, 0.06);
                Msg.title(def, "<red><bold>💫 ᴀɴᴋʟᴇꜱ ʙʀᴏᴋᴇɴ !</bold></red>", "<gray>ᴄʀᴏꜱꜱᴏᴠᴇʀ ᴅᴇ " + handler.getName() + "</gray>", 50, 750, 200);
                handler.sendActionBar(Msg.parse("<light_purple><bold>⚡ ᴀɴᴋʟᴇ-ʙʀᴇᴀᴋᴇʀ ꜱᴜʀ " + def.getName() + " !</bold></light_purple>"));
                if (state == State.LIVE) {
                    statsManager.getOrCreate(handler).incrementAnkleBreakers();
                }
            }
        }
    }

    /**
     * Defensive Active Steal or Mid-Air Shot Block (Left-Click)!
     */
    public void handleDefensiveAction(Player defender) {
        if (!isPlayableState() || ball == null) {
            return;
        }
        // First: if defender is the ball-handler charging a shot, left-click locks in the shot release!
        if (tryReleaseChargedShotNow(defender)) {
            if (defender.isHandRaised()) {
                defender.clearActiveItem();
            }
            return;
        }

        TeamSide defTeam = getTeam(defender.getUniqueId());
        if (defTeam == TeamSide.SPECTATOR) {
            return;
        }
        long now = System.currentTimeMillis();

        // Case A: Mid-Air Shot Block when ball is in flight and defender jumps near it!
        if ((ball.mode() == BasketballEntity.BallMode.IN_SHOT_FLIGHT || ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT)
            && !defender.isOnGround()
            && defender.getEyeLocation().toVector().distanceSquared(ball.position()) <= 2.35 * 2.35) {

            long blkCd = plugin.getConfig().getLong("physics.block-cooldown-ms", 950L);
            if (now - blockCooldowns.getOrDefault(defender.getUniqueId(), 0L) < blkCd) {
                return;
            }
            blockCooldowns.put(defender.getUniqueId(), now);

            // Protect against defensive goaltending when ball is already falling right above cylinder
            Vector defRim = arena.targetRimForTeam(defTeam.opposite());
            if (ball.velocity().getY() < -0.04 && ball.position().distanceSquared(defRim) <= 1.15 * 1.15) {
                defender.sendActionBar(Msg.parse("<red>⚠ ᴄᴏɴᴛʀᴇ ɪʟʟᴇɢᴀʟ (ɢᴏᴀʟᴛᴇɴᴅɪɴɢ) ᴇᴠɪᴛᴇ !</red>"));
                return;
            }

            Vector swat = defender.getEyeLocation().getDirection().normalize().multiply(0.85).setY(0.28);
            ball.popLoose(swat, defender.getUniqueId());
            Msg.playPackSound(ball.position().toLocation(defender.getWorld()), "defense.block", 1.25f, 0.95f);
            defender.getWorld().spawnParticle(Particle.SWEEP_ATTACK, ball.position().toLocation(defender.getWorld()), 2, 0.1, 0.1, 0.1, 0);
            defender.getWorld().spawnParticle(Particle.CRIT, ball.position().toLocation(defender.getWorld()), 18, 0.25, 0.25, 0.25, 0.15);

            getBoxScore(defender.getUniqueId()).blocks++;
            statsManager.getOrCreate(defender).incrementBlocks();
            broadcast("<aqua><bold>⛔ ᴄᴏɴᴛʀᴇ ᴍᴏɴꜱᴛʀᴜᴇᴜx</bold></aqua> <gray>ᴅᴇ</gray> <white>" + defender.getName() + "</white> <gray>!</gray>");
            return;
        }

        // Case B: Active On-Ball Steal!
        UUID holderId = ball.holderId();
        if (holderId != null && !holderId.equals(defender.getUniqueId()) && ball.mode() == BasketballEntity.BallMode.DRIBBLING) {
            if (mode.competitiveTeamMatch() && getTeam(holderId) == defTeam) {
                return;
            }
            Player holder = Bukkit.getPlayer(holderId);
            if (holder == null || defender.getLocation().distanceSquared(holder.getLocation()) > 2.65 * 2.65) {
                return;
            }
            long stlCd = plugin.getConfig().getLong("physics.steal-cooldown-ms", 1400L);
            if (now - stealCooldowns.getOrDefault(defender.getUniqueId(), 0L) < stlCd) {
                return;
            }
            stealCooldowns.put(defender.getUniqueId(), now);

            // Base 38% steal chance (higher if ball-handler is sprinting carelessly, lower if crouching/protecting!)
            double chance = 0.38 + (holder.isSprinting() ? 0.14 : 0.0) - (holder.isSneaking() ? 0.18 : 0.0);
            if (rng.nextDouble() <= chance) {
                activeShotCharges.remove(holderId);
                PlayerProfile defProf = statsManager.getOrCreate(defender);
                MatchBoxScore defBox = getBoxScore(defender.getUniqueId());
                defBox.steals++;
                defProf.incrementSteals();
                getBoxScore(holderId).hotStreak = 0;

                boolean onFire = defBox.hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3);
                ball.attachToHolder(defender, defProf.selectedBallSkin(), onFire);
                resetShotClock(false);

                Msg.playPackSound(defender.getLocation(), "shoe.squeak", 1.0f, 1.2f);
                broadcast("<green><bold>⚡ ɪɴᴛᴇʀᴄᴇᴘᴛɪᴏɴ</bold></green> <gray>ᴅᴇ</gray> <white>" + defender.getName()
                    + "</white> <gray>ꜱᴜʀ " + holder.getName() + " !</gray>");
            } else {
                // Reach-in lunge recovery
                defender.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 16, 1, false, false, false));
                defender.sendActionBar(Msg.parse("<yellow>ᴛᴇɴᴛᴀᴛɪᴠᴇ ᴅ'ɪɴᴛᴇʀᴄᴇᴘᴛɪᴏɴ ᴍᴀɴǫᴜᴇᴇ !</yellow>"));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //   BASKET SCORED, CELEBRATION BANNERS (image-1.png), VIOLATIONS & MVP CEREMONY
    // ═══════════════════════════════════════════════════════════════════════════

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
        Player shooter = shooterId != null ? Bukkit.getPlayer(shooterId) : null;
        String scorerName = shooter != null ? shooter.getName() : "BosketPlayer";

        // 1. Special handling for NBA All-Star 3-Point Contest!
        if (mode == GameModeType.THREE_POINT_CONTEST) {
            int pts = currentShotIsMoneyBall ? 2 : 1;
            scores.put(TeamSide.IGNIS, score(TeamSide.IGNIS) + pts);
            if (shooter != null) {
                Msg.playPackSound(shooter.getLocation(), "crowd.cheer", 0.7f, currentShotIsMoneyBall ? 1.2f : 1.05f);
                shooter.sendActionBar(Msg.parse(currentShotIsMoneyBall
                    ? "<gradient:#3DB8FF:#FFFFFF:#FF4D4D><bold>★ ᴍᴏɴᴇʏ ʙᴀʟʟ +2 ᴘᴛꜱ ! (ᴛᴏᴛᴀʟ: " + score(TeamSide.IGNIS) + ")</bold></gradient>"
                    : "<green><bold>✓ ꜱᴘʟᴀꜱʜ +1 ᴘᴛ ! (ᴛᴏᴛᴀʟ: " + score(TeamSide.IGNIS) + ")</bold></green>"));
                advanceContestShot(shooter, false);
            }
            return;
        }

        // 2. Credit basket to the hoop's scoring team (protects against own-goals in competitive matches)!
        TeamSide creditedTeam = hoopScoringSide;
        int pts = threePointer ? 3 : 2;
        scores.put(creditedTeam, score(creditedTeam) + pts);

        boolean isOwnGoal = (mode.competitiveTeamMatch() && state == State.LIVE && shooterId != null && getTeam(shooterId) != creditedTeam);
        MatchBoxScore scorerBox = (shooterId != null && !isOwnGoal) ? getBoxScore(shooterId) : new MatchBoxScore();
        if (!isOwnGoal) {
            scorerBox.points += pts;
            scorerBox.hotStreak++;
            if (slamDunk) {
                scorerBox.dunks++;
            }
            if (threePointer) {
                scorerBox.threes++;
            }
        }

        // Reset opposing team's hot streaks!
        for (Map.Entry<UUID, TeamSide> e : participants.entrySet()) {
            if (e.getValue() == creditedTeam.opposite()) {
                getBoxScore(e.getKey()).hotStreak = 0;
            }
        }

        if (shooter != null && state == State.LIVE && !isOwnGoal) {
            PlayerProfile prof = statsManager.getOrCreate(shooter);
            Rank beforeRank = prof.effectiveRank();
            prof.addPoints(pts);
            if (threePointer) {
                prof.setThreePointersMade(prof.threePointersMade() + 1);
            } else {
                prof.setTwoPointersMade(prof.twoPointersMade() + 1);
            }
            if (slamDunk) {
                prof.incrementDunks();
            }
            prof.addXp(pts * 12 + (slamDunk ? 15 : 0) + (pureSwish ? 10 : 0));
            checkRankPromotion(shooter, beforeRank, prof.effectiveRank());
        }

        Player assister = (assisterId != null && !assisterId.equals(shooterId) && !isOwnGoal) ? Bukkit.getPlayer(assisterId) : null;
        if (assister != null && getTeam(assisterId) == creditedTeam) {
            getBoxScore(assisterId).assists++;
            if (state == State.LIVE) {
                PlayerProfile aProf = statsManager.getOrCreate(assister);
                aProf.incrementAssists();
                aProf.addXp(18);
            }
        } else {
            assister = null;
        }

        if (state == State.LIVE) {
            statsManager.refreshLeaderboardBadges();
        }

        // 3. Configure the Dual-Layer Broadcast Banner (exact image-1.png aesthetic + elevated variants!)
        this.celebScorerName = scorerName;
        if (slamDunk) {
            this.celebVariant = Glyphs.BannerVariant.SLAM_DUNK;
            this.celebRoleTag = "ꜱʟᴀᴍ ᴅᴜɴᴋ +" + pts;
        } else if (threePointer) {
            this.celebVariant = Glyphs.BannerVariant.THREE_POINTER;
            this.celebRoleTag = "ꜱᴘʟᴀꜱʜ 3 ᴘᴏɪɴᴛꜱ";
        } else {
            this.celebVariant = Glyphs.BannerVariant.REGULAR;
            this.celebRoleTag = "ʙᴀꜱᴋᴇᴛ ꜱᴄᴏʀᴇʀ +" + pts;
        }

        if (assister != null) {
            this.celebSubLabel = "ᴀꜱꜱɪꜱᴛ";
            this.celebSubValue = assister.getName();
        } else {
            this.celebSubLabel = pureSwish ? "ꜱᴡɪꜱʜ ᴘᴜʀ" : (bankShot ? "ᴘʟᴀɴᴄʜᴇ" : "ᴅɪꜱᴛᴀɴᴄᴇ");
            this.celebSubValue = String.format("%.1fᴍ", shotDistanceBlocks);
        }

        // Stadium Crowd Roar + Rim Celebration Particles!
        Vector rimVec = hoopScoringSide == TeamSide.IGNIS ? arena.geometry().southHoopRim() : arena.geometry().northHoopRim();
        World w = arena.world();
        if (w != null) {
            Location rimLoc = rimVec.toLocation(w);
            Msg.playPackSound(rimLoc, "crowd.cheer", 1.15f, threePointer || slamDunk ? 1.06f : 1.0f);
            if (scorerBox.hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3)) {
                Msg.playPackSound(rimLoc, "game.on_fire", 1.15f, 1.0f);
            }
            w.spawnParticle(
                Particle.DUST,
                rimLoc.clone().add(0, 0.4, 0),
                36, 0.55, 0.35, 0.55,
                new Particle.DustOptions(creditedTeam.bukkitColor(), 1.6f)
            );
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, rimLoc, 22, 0.35, 0.25, 0.35, 0.18);
        }

        // Broadcast stylized small-caps play-by-play line!
        String badge = slamDunk ? "<gold><bold>⚡ ꜱʟᴀᴍ ᴅᴜɴᴋ</bold></gold>"
            : (threePointer ? "<aqua><bold>🎯 ꜱᴘʟᴀꜱʜ 3-ᴘᴛꜱ</bold></aqua>" : "<yellow><bold>🏀 ᴘᴀɴɪᴇʀ +2</bold></yellow>");
        String assistNote = assister != null ? " <dark_gray>(</dark_gray><gray>ᴀꜱꜱɪꜱᴛ:</gray> <white>" + assister.getName() + "</white><dark_gray>)</dark_gray>" : "";
        String fireNote = scorerBox.hotStreak >= 3 ? " <gradient:#FF3B00:#FFD43B><bold>🔥 ᴏɴ ꜰɪʀᴇ x" + scorerBox.hotStreak + " !</bold></gradient>" : "";
        broadcast(badge + " <gray>ᴘᴀʀ</gray> <white><bold>" + scorerName + "</bold></white>" + assistNote
            + " <dark_gray>•</dark_gray> <gray>" + String.format("%.1fᴍ", shotDistanceBlocks) + "</gray>" + fireNote);

        if (mode == GameModeType.PRACTICE || state == State.WAITING) {
            this.warmupBannerTicks = 65; // Show Broadcast Scorer Banner for 3.25s then return to Live Scorebug!
            for (Player p : onlineParticipants()) {
                hudManager.showScorerCelebration(p, celebScorerName, celebRoleTag, celebVariant, celebSubLabel, celebSubValue);
            }
            return;
        }

        // Check 1v1 Target Score (21 pts win-by-2)!
        int target1v1 = plugin.getConfig().getInt("match.target-score-1v1", 21);
        if (mode == GameModeType.DUEL_1V1
            && score(creditedTeam) >= target1v1
            && score(creditedTeam) - score(creditedTeam.opposite()) >= 2) {
            finishMatchAndStartMvpCeremony();
            return;
        }

        this.nextInboundTeam = creditedTeam.opposite();
        this.state = State.CELEBRATION;
        this.stateTimerTicks = plugin.getConfig().getInt("match.celebration-ticks", 80);
    }

    @Override
    public void onOutOfBounds(Vector lastPos, UUID lastTouchPlayerId) {
        if (mode != GameModeType.PRACTICE && mode.competitiveTeamMatch() && state == State.LIVE) {
            TeamSide lastTeam = lastTouchPlayerId != null ? getTeam(lastTouchPlayerId) : TeamSide.IGNIS;
            TeamSide inboundSide = lastTeam.opposite() == TeamSide.SPECTATOR ? TeamSide.IGNIS : lastTeam.opposite();

            Msg.playPackSound(lastPos.toLocation(arena.world()), "game.whistle", 1.0f, 1.08f);
            broadcast("<yellow>⚑ ꜱᴏʀᴛɪᴇ ᴅᴇ ʙᴀʟʟᴇ !</yellow> <gray>ʀᴇᴍɪꜱᴇ ᴇɴ ᴊᴇᴜ ᴘᴏᴜʀ</gray> "
                + "<#" + String.format("%06X", inboundSide.textColor().value() & 0xFFFFFF) + "><bold>" + inboundSide.smallCapsName() + "</bold>");
            awardPossessionToTeam(inboundSide, false);
        } else if (mode == GameModeType.PRACTICE || state == State.WAITING) {
            Player solo = onlineParticipants().stream().findFirst().orElse(null);
            if (solo != null && ball != null) {
                PlayerProfile prof = statsManager.getOrCreate(solo);
                ball.attachToHolder(solo, prof.selectedBallSkin(), getBoxScore(solo.getUniqueId()).hotStreak >= 3);
                syncBallHotbarItems(true);
            }
        }
    }

    @Override
    public void onRimContact() {
        if (mode.competitiveTeamMatch() && state == State.LIVE) {
            // Once a live shot hits the iron, reset shot clock to 14s minimum so a long rebound never buzzes out mid-air!
            int min14 = plugin.getConfig().getInt("match.offensive-rebound-shot-clock", 14) * 20;
            if (shotClockTicks < min14) {
                shotClockTicks = min14;
            }
        }
    }

    private void resumeAfterCelebration() {
        this.state = State.LIVE;
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
        broadcast("<red><bold>⏱ ᴠɪᴏʟᴀᴛɪᴏɴ ᴅᴇꜱ 24 ꜱᴇᴄᴏɴᴅᴇꜱ !</bold></red> <gray>ʙᴀʟʟᴇ ʀᴇɴᴅᴜᴇ ᴀ</gray> <white>" + awarded.smallCapsName() + "</white>");
        resetShotClock(false);
        awardPossessionToTeam(awarded, false);
    }

    private void handlePeriodEndBuzzer() {
        Msg.playPackSound(arena.centerCourtLocation(), "game.buzzer", 1.35f, 0.94f);

        if (mode == GameModeType.THREE_POINT_CONTEST) {
            finishThreePointContest();
            return;
        }

        if (currentQuarter < totalQuarters) {
            currentQuarter++;
            this.ticksRemainingInPeriod = plugin.getConfig().getInt("match.quarter-seconds", 90) * 20;
            resetShotClock(false);
            TeamSide possess = (currentQuarter % 2 == 0) ? TeamSide.FROST : TeamSide.IGNIS;
            broadcast("<gold><bold>🔔 ᴅᴇʙᴜᴛ ᴅᴜ ǫᴜᴀʀᴛ-ᴛᴇᴍᴘꜱ " + currentQuarter + "/" + totalQuarters + " !</bold></gold>");
            awardPossessionToTeam(possess, true);
            return;
        }

        // End of regulation: check tie -> Overtime!
        if (score(TeamSide.IGNIS) == score(TeamSide.FROST)) {
            currentQuarter++;
            this.ticksRemainingInPeriod = plugin.getConfig().getInt("match.overtime-seconds", 45) * 20;
            resetShotClock(false);
            broadcast("<gradient:#FF3B3B:#FFD43B><bold>⚡ ᴘʀᴏʟᴏɴɢᴀᴛɪᴏɴ (ᴏᴠᴇʀᴛɪᴍᴇ) !</bold></gradient>");
            if (ball != null) {
                ball.resetToCenterTipOff(
                    new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ()),
                    0.46
                );
            }
            return;
        }

        finishMatchAndStartMvpCeremony();
    }

    private void finishThreePointContest() {
        Player shooter = onlineParticipants().stream().findFirst().orElse(null);
        int finalScore = score(TeamSide.IGNIS);
        if (shooter != null) {
            PlayerProfile prof = statsManager.getOrCreate(shooter);
            boolean record = finalScore > prof.threePointContestBest();
            prof.updateThreePointContestBest(finalScore);
            prof.addXp(finalScore * 10);
            statsManager.save();

            Msg.title(
                shooter,
                "<gradient:#00F0FF:#FFD43B><bold>🎯 ꜱᴄᴏʀᴇ ꜰɪɴᴀʟ: " + finalScore + "/30</bold></gradient>",
                record ? "<green><bold>✦ ɴᴏᴜᴠᴇᴀᴜ ʀᴇᴄᴏʀᴅ ᴘᴇʀꜱᴏɴɴᴇʟ ! ✦</bold></green>" : "<gray>ʀᴇᴄᴏʀᴅ: " + prof.threePointContestBest() + "/30</gray>",
                200, 3200, 600
            );
            Msg.playPackSound(shooter.getLocation(), "crowd.cheer", 1.2f, 1.0f);
        }
        this.state = State.MVP_CEREMONY;
        this.stateTimerTicks = 90;
    }

    private void finishMatchAndStartMvpCeremony() {
        this.state = State.MVP_CEREMONY;
        this.stateTimerTicks = 130; // 6.5s MVP Ceremony!

        int sIgnis = score(TeamSide.IGNIS);
        int sFrost = score(TeamSide.FROST);
        TeamSide winner = sIgnis >= sFrost ? TeamSide.IGNIS : TeamSide.FROST;
        TeamSide loser = winner.opposite();

        // Find Match MVP!
        UUID mvpId = participants.entrySet().stream()
            .filter(e -> e.getValue() != TeamSide.SPECTATOR)
            .map(Map.Entry::getKey)
            .max(Comparator.comparingDouble(id -> getBoxScore(id).mvpScore() + (getTeam(id) == winner ? 3.5 : 0.0)))
            .orElse(null);

        Player mvpPlayer = mvpId != null ? Bukkit.getPlayer(mvpId) : null;
        String mvpName = mvpPlayer != null ? mvpPlayer.getName() : "MVP";

        // Update ELO, Wins/Losses, Streaks & MVPs!
        double winAvgElo = averageTeamElo(winner);
        double loseAvgElo = averageTeamElo(loser);
        int eloDelta = StatsManager.computeEloDelta(winAvgElo, loseAvgElo);

        for (Map.Entry<UUID, TeamSide> entry : participants.entrySet()) {
            TeamSide side = entry.getValue();
            if (side == TeamSide.SPECTATOR) {
                continue;
            }
            Player p = Bukkit.getPlayer(entry.getKey());
            PlayerProfile prof = statsManager.getOrCreate(entry.getKey(), p != null ? p.getName() : "Player");
            Rank oldRank = prof.effectiveRank();

            boolean won = (side == winner);
            prof.recordMatchOutcome(won);
            prof.adjustElo(won ? +eloDelta : -Math.max(6, (int) (eloDelta * 0.8)));
            prof.addXp(won ? 120 : 45);
            if (entry.getKey().equals(mvpId)) {
                prof.incrementMvps();
                prof.addXp(80);
            }

            if (p != null) {
                checkRankPromotion(p, oldRank, prof.effectiveRank());
            }
        }
        statsManager.refreshLeaderboardBadges();
        statsManager.save();

        // Equip 3D Golden Championship Trophy to Match MVP!
        if (mvpPlayer != null) {
            mvpPlayer.teleport(arena.centerCourtLocation().add(0, 0.15, 0));
            ItemStack trophy = BallSkin.createCustomModelItem(
                Material.GOLDEN_CARROT,
                "trophy_mvp",
                "<gradient:#FFD43B:#FFF6A3><bold>🏆 ᴛʀᴏᴘʜᴇᴇ ᴍᴠᴘ ᴅᴜ ᴍᴀᴛᴄʜ</bold></gradient>",
                List.of("<gray>ᴅᴇᴄᴇʀɴᴇ ᴀ " + mvpName + " !</gray>")
            );
            mvpPlayer.getInventory().setItem(0, trophy);
            mvpPlayer.getInventory().setHeldItemSlot(0);
            mvpPlayer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, mvpPlayer.getLocation().add(0, 1.5, 0), 65, 0.7, 0.8, 0.7, 0.25);
        }

        Msg.playPackSound(arena.centerCourtLocation(), "crowd.cheer", 1.35f, 1.0f);

        MatchBoxScore mvpBox = mvpId != null ? getBoxScore(mvpId) : new MatchBoxScore();
        String winHex = String.format("%06X", winner.textColor().value() & 0xFFFFFF);
        for (Player p : onlineParticipants()) {
            Msg.title(
                p,
                "<#" + winHex + "><bold>🏆 ᴠɪᴄᴛᴏɪʀᴇ " + winner.smallCapsName() + " !</bold>",
                "<gold>★ ᴍᴠᴘ: <white>" + mvpName + "</white> (" + mvpBox.points + "ᴘᴛꜱ • " + mvpBox.assists + "ᴀꜱᴛ)</gold>",
                200, 4200, 600
            );
            hudManager.showScorerCelebration(
                p,
                mvpName,
                "ᴍᴠᴘ ᴅᴜ ᴍᴀᴛᴄʜ",
                Glyphs.BannerVariant.SLAM_DUNK,
                "ꜱᴄᴏʀᴇ ꜰɪɴᴀʟ",
                sIgnis + " - " + sFrost
            );
        }

        broadcast("<gradient:#FFD43B:#FF8A28><bold>✦ ꜰɪɴ ᴅᴜ ᴍᴀᴛᴄʜ ✦</bold></gradient> "
            + "<#FF4D3D>ɪɢɴɪꜱ " + sIgnis + "</#FF4D3D> <dark_gray>—</dark_gray> <#3DB8FF>" + sFrost + " ꜰʀᴏꜱᴛ</#3DB8FF> "
            + "<dark_gray>•</dark_gray> <yellow>ᴍᴠᴘ: <bold>" + mvpName + "</bold> (+" + eloDelta + " ᴇʟᴏ)</yellow> "
            + "<gray>• ᴛᴀᴘᴇᴢ <gold>/ɢɢ</gold> !</gray>");
    }

    private void checkRankPromotion(Player player, Rank oldRank, Rank newRank) {
        if (oldRank != newRank && !newRank.staffOnly() && newRank.minElo() > oldRank.minElo()) {
            Msg.title(
                player,
                "<gradient:#FFD43B:#48FFB2><bold>✦ ᴘʀᴏᴍᴏᴛɪᴏɴ ᴅᴇ ʀᴀɴɢ ! ✦</bold></gradient>",
                "<!shadow><white>" + oldRank.pillGlyph() + "</white></!shadow> <gray>➔</gray> <!shadow><white>" + newRank.pillGlyph() + "</white></!shadow>",
                200, 2800, 500
            );
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 45, 0.5, 0.6, 0.5, 0.2);
            broadcast("<gold><bold>✦ ʀᴀɴᴋ ᴜᴘ !</bold></gold> <white>" + player.getName() + "</white> <gray>ᴀᴛᴛᴇɪɴᴛ ʟᴇ ʀᴀɴɢ</gray> <!shadow><white>"
                + newRank.pillGlyph() + "</white></!shadow> <gray>!</gray>");
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
        BallSkin ballSkin = currentShotIsMoneyBall ? BallSkin.MONEY : BallSkin.CLASSIC;
        ball.attachToHolder(shooter, ballSkin, false);
        syncBallHotbarItems(true);
        shooter.sendActionBar(Msg.parse(
            "<aqua>ʀᴀᴄᴋ " + (contestRackIndex + 1) + "/5</aqua> <dark_gray>•</dark_gray> "
                + (currentShotIsMoneyBall
                ? "<gradient:#3DB8FF:#FFFFFF:#FF4D4D><bold>★ ᴍᴏɴᴇʏ ʙᴀʟʟ (2 ᴘᴛꜱ) !</bold></gradient>"
                : "<yellow>ʙᴀʟʟᴏɴ " + (contestBallInRack + 1) + "/5</yellow>")
        ));
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
            boolean onFire = getBoxScore(handler.getUniqueId()).hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3);
            ball.attachToHolder(handler, prof.selectedBallSkin(), onFire);
        }
    }

    private void resetShotClock(boolean offensiveRebound14s) {
        int secs = offensiveRebound14s
            ? plugin.getConfig().getInt("match.offensive-rebound-shot-clock", 14)
            : plugin.getConfig().getInt("match.shot-clock-seconds", 24);
        this.shotClockTicks = secs * 20;
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
        // Right under a rim (< 3.4m): always dunk/layup on that basket
        if (distN < 3.4 && distN < distS) {
            return n;
        }
        if (distS < 3.4 && distS < distN) {
            return s;
        }
        // Otherwise shoot toward whichever hoop the player is facing (enables half-court & full-court shots!)
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

    /**
     * Dynamically ensures ONLY the current ball-handler holds the 3D Square Basketball in Slot 0,
     * while off-ball players carry the Defensive Stance item (and NEVER overwrites Slot 0 mid-shot-charge!).
     */
    private void syncBallHotbarItems(boolean force) {
        if (state == State.MVP_CEREMONY || state == State.FINISHED) {
            return;
        }
        UUID holderId = (ball != null) ? ball.holderId() : null;
        BallSkin activeSkin = null;
        if (holderId != null) {
            if (mode == GameModeType.THREE_POINT_CONTEST && currentShotIsMoneyBall) {
                activeSkin = BallSkin.MONEY;
            } else if (getBoxScore(holderId).hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3)) {
                activeSkin = BallSkin.FIRE;
            } else {
                Player holderPlayer = Bukkit.getPlayer(holderId);
                activeSkin = holderPlayer != null ? statsManager.getOrCreate(holderPlayer).selectedBallSkin() : BallSkin.CLASSIC;
            }
        }

        if (!force && Objects.equals(holderId, lastSyncedHolderId) && activeSkin == lastSyncedHolderSkin) {
            return;
        }
        lastSyncedHolderId = holderId;
        lastSyncedHolderSkin = activeSkin;

        ItemStack defenseItem = BallSkin.createDefensiveStanceItem();
        for (Player p : onlineParticipants()) {
            if (getTeam(p.getUniqueId()) == TeamSide.SPECTATOR) {
                continue;
            }
            if (p.getUniqueId().equals(holderId) && activeSkin != null) {
                p.getInventory().setItem(0, activeSkin.createBallItem(false));
            } else {
                p.getInventory().setItem(0, defenseItem);
            }
        }
    }

    private void equipCourtKit(Player player, TeamSide side) {
        player.setGameMode(side == TeamSide.SPECTATOR ? GameMode.SPECTATOR : GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.getInventory().clear();

        if (side == TeamSide.SPECTATOR) {
            return;
        }

        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.118); // Crisp athletic court footwork speed!
        }

        String hex = String.format("%06X", side.textColor().value() & 0xFFFFFF);
        // Dyed Pro Jersey + Shorts + 3D Air Bosket Sneakers!
        player.getInventory().setChestplate(dyedLeather(Material.LEATHER_CHESTPLATE, side.bukkitColor(),
            "<#" + hex + "><bold>🎽 ᴍᴀɪʟʟᴏᴛ " + side.smallCapsName() + "</bold>"));
        player.getInventory().setLeggings(dyedLeather(Material.LEATHER_LEGGINGS, side.bukkitColor(),
            "<#" + hex + "><bold>🩳 ꜱʜᴏʀᴛ " + side.smallCapsName() + "</bold>"));
        player.getInventory().setBoots(BallSkin.createCustomModelItem(
            Material.LEATHER_BOOTS,
            "sneakers_air",
            "<gradient:#FF4D3D:#FFFFFF><bold>👟 ᴀɪʀ ʙᴏꜱᴋᴇᴛ ᴘʀᴏ 26</bold></gradient>",
            List.of("<gray>ᴀᴅʜᴇʀᴇɴᴄᴇ ᴘᴀʀǫᴜᴇᴛ ʜᴀᴜᴛᴇ ᴘᴇʀꜰᴏʀᴍᴀɴᴄᴇ</gray>")
        ));

        PlayerProfile prof = statsManager.getOrCreate(player);
        player.getInventory().setItem(0, BallSkin.createDefensiveStanceItem());
        player.getInventory().setItem(1, namedTool(Material.FEATHER,
            "<aqua><bold>⚡ ᴘᴀꜱꜱᴇ / ᴀʟʟᴇʏ-ᴏᴏᴘ</bold></aqua>",
            List.of("<gray>ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴏᴜ Q: ᴘᴀꜱꜱᴇ ʟᴀꜱᴇʀ • ᴠɪꜱᴇʀ ʜᴀᴜᴛ = ʟᴏʙ</gray>")));
        player.getInventory().setItem(2, namedTool(Material.BLAZE_POWDER,
            "<light_purple><bold>🌀 ᴄʀᴏꜱꜱᴏᴠᴇʀ ᴀɴᴋʟᴇ-ʙʀᴇᴀᴋᴇʀ</bold></light_purple>",
            List.of("<gray>ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴏᴜ ꜱʜɪꜰᴛ: ᴅʀɪʙʙʟᴇ ᴇᴄʟᴀɪʀ</gray>")));
        player.getInventory().setItem(3, namedTool(Material.FIRE_CHARGE,
            "<gold><bold>🔥 ꜱᴛʏʟᴇ: " + prof.selectedDunkStyle().displayName() + "</bold></gold>",
            List.of("<gray>ꜱᴘʀɪɴᴛ + ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴘʀᴇꜱ ᴅᴜ ᴘᴀɴɪᴇʀ = ᴅᴜɴᴋ 3ᴅ !</gray>")));
        player.getInventory().setItem(8, namedTool(Material.RED_BED,
            "<red><bold>🚪 ǫᴜɪᴛᴛᴇʀ ʟᴇ ᴛᴇʀʀᴀɪɴ</bold></red>",
            List.of("<gray>ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴘᴏᴜʀ ʀᴇᴛᴏᴜʀɴᴇʀ ᴀᴜ ʟᴏʙʙʏ</gray>")));
        player.getInventory().setHeldItemSlot(0);
    }

    private ItemStack dyedLeather(Material mat, Color color, String nameMm) {
        ItemStack stack = new ItemStack(mat);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            meta.displayName(Msg.parse(nameMm));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE, ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack namedTool(Material mat, String nameMm, List<String> loreMm) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.parse(nameMm));
            meta.lore(loreMm.stream().map(Msg::parse).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
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

    public void stopAndCleanup() {
        this.state = State.FINISHED;
        if (ball != null) {
            ball.remove();
            ball = null;
        }
        List<UUID> ids = new ArrayList<>(participants.keySet());
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                hudManager.clearPlayer(p);
                SavedPlayerState st = savedStates.remove(id);
                if (st != null) {
                    st.restore(p);
                }
            }
        }
        participants.clear();
        activeShotCharges.clear();
        arena.updateJumbotron("ɪɢɴɪꜱ", 0, "ᴛᴇʀʀᴀɪɴ ʟɪʙʀᴇ", 24, 0, "ꜰʀᴏꜱᴛ");
    }
}
