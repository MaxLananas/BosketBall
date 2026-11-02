package fr.bosketball.game;

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
import fr.bosketball.ui.GuiMenuManager;
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
 *  - 1v1 / 2v2 / 3v3 / 5v5 Competitive Matches (Quarters, Live 24s/14s Shot Clock, Tip-Off, Overtime, ELO & MVP Ceremony)
 *  - NBA All-Star 3-Point Contest (5 Racks x 5 Balls incl. 2-Point Money Balls, 60s timer)
 *  - Free Practice / Dunk & Shootaround Lab (with Live 24s Shot Clock & Toggleable 3D Pro Sparring Defender Bot!)
 *  - True Skill Directional Aiming + Live 3D Trajectory Ribbon & Reticle HUD, Skill Dunk Meter, Steals, Pokes & Blocks!
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
            p.setGameMode(gameMode);
            p.setAllowFlight(allowFlight);
            p.setFlying(allowFlight && flying);
            p.setExp(exp);
            p.setLevel(level);
            p.setHealth(Math.max(1.0, Math.min(20.0, health)));
            p.setFoodLevel(foodLevel);
            p.setSaturation(saturation);
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

            AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.10);
            }
            Location dest = (customLobbyExit != null && customLobbyExit.getWorld() != null) ? customLobbyExit : returnLocation;
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

    private final Map<UUID, ShotChargeState> activeShotCharges = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerVisualRig> visualRigs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stealCooldowns = new HashMap<>();
    private final Map<UUID, Long> crossoverCooldowns = new HashMap<>();
    private final Map<UUID, Long> blockCooldowns = new HashMap<>();
    private final Map<UUID, Long> hustleDiveCooldowns = new HashMap<>();

    private State state = State.WAITING;
    private BasketballEntity ball;
    private CourtDefenderBot sparringBot;

    private int currentQuarter = 1;
    private final int totalQuarters;
    private int ticksRemainingInPeriod;
    private int shotClockTicks;
    private int stateTimerTicks;
    private int warmupBannerTicks = 0;
    private int actionBarNoticeTicks = 0;
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
        this.shotClockTicks = plugin.getConfig().getInt("match.shot-clock-seconds", 24) * 20;
        scores.put(TeamSide.IGNIS, 0);
        scores.put(TeamSide.FROST, 0);
    }

    private long lastPracticeCineCamMillis = 0L;

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

    public boolean hasSparringBot() {
        return sparringBot != null;
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
        if (mode == GameModeType.THREE_POINT_CONTEST && state == State.LIVE) {
            return Math.max(0, (int) Math.ceil(ticksRemainingInPeriod / 20.0));
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

        plugin.clearLobbyRig(player.getUniqueId(), false);
        savedStates.putIfAbsent(player.getUniqueId(), new SavedPlayerState(player));
        participants.put(player.getUniqueId(), assigned);
        boxScores.putIfAbsent(player.getUniqueId(), new MatchBoxScore());

        equipCourtKit(player, assigned);
        player.teleport(arena.teamSpawn(assigned, countTeam(assigned) - 1));
        PlayerVisualRig oldRig = visualRigs.remove(player.getUniqueId());
        if (oldRig != null) {
            oldRig.despawn();
        }
        if (assigned != TeamSide.SPECTATOR) {
            visualRigs.put(player.getUniqueId(), new PlayerVisualRig(plugin, player, assigned));
        }
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.15f);

        String joinHex = String.format("%06X", assigned.textColor().value() & 0xFFFFFF);
        broadcast("<#" + joinHex + "><bold>" + player.getName()
            + "</bold></#" + joinHex
            + "> <gray>ᴀ ʀᴇᴊᴏɪɴᴛ ʟᴇ ᴛᴇʀʀᴀɪɴ</gray> <gold>" + arena.displayName() + "</gold> "
            + "<dark_gray>(" + activeCompetitorCount() + "/" + mode.maxPlayers() + ")</dark_gray>");

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
        if (plugin.cinematicCamera() != null) {
            plugin.cinematicCamera().stopWatching(player, false);
        }
        participants.remove(id);
        activeShotCharges.remove(id);
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
            boolean autoHotbar = plugin.getConfig().getBoolean("general.auto-equip-lobby-playbook", true);
            saved.restore(player, arena.lobbyExitLocationOrNull(), autoHotbar);
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

    public void toggleSparringBot(Player requester) {
        if (mode != GameModeType.PRACTICE && state != State.WAITING) {
            Msg.send(requester, "<red>ʟᴇ ᴅᴇꜰᴇɴꜱᴇᴜʀ ɪᴀ ᴇꜱᴛ ʀᴇꜱᴇʀᴠᴇ ᴀᴜ ᴍᴏᴅᴇ ᴇɴᴛʀᴀɪɴᴇᴍᴇɴᴛ (/ʙʙ ᴘʀᴀᴄᴛɪᴄᴇ) !</red>");
            return;
        }
        if (sparringBot != null) {
            sparringBot.remove();
            sparringBot = null;
            Msg.send(requester, "<yellow>🤖 ᴅᴇꜰᴇɴꜱᴇᴜʀ ɪᴀ <red><bold>ʀᴇᴛɪʀᴇ</bold></red> ᴅᴜ ᴛᴇʀʀᴀɪɴ.</yellow>");
        } else {
            Vector rim = selectClosestOrOffensiveRim(requester, getTeam(requester.getUniqueId()));
            Vector startPos = requester.getLocation().toVector().multiply(0.55).add(rim.clone().multiply(0.45));
            startPos.setY(arena.geometry().floorY());
            sparringBot = new CourtDefenderBot(arena, startPos);
            Msg.send(requester, "<green>🤖 ᴅᴇꜰᴇɴꜱᴇᴜʀ ᴘʀᴏ <bold>ᴀᴄᴛɪᴠᴇ</bold> ! ᴛᴇꜱᴛᴇᴢ ᴠᴏꜱ ᴄʀᴏꜱꜱᴏᴠᴇʀꜱ (ꜱʜɪꜰᴛ), ᴛɪʀꜱ ᴄᴏɴᴛᴇꜱᴛᴇꜱ & ᴅᴜɴᴋꜱ !</green>");
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

    private void beginCountdown() {
        if (!mode.competitiveTeamMatch()) {
            startLivePlay();
            return;
        }
        if (sparringBot != null) {
            sparringBot.remove();
            sparringBot = null;
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
            resetShotClock(false);
            Player solo = onlineParticipants().stream().findFirst().orElse(null);
            if (solo != null) {
                solo.teleport(arena.teamSpawn(TeamSide.IGNIS, 0));
                PlayerProfile prof = statsManager.getOrCreate(solo);
                ball.attachToHolder(solo, prof.selectedBallSkin(), false);
                solo.getInventory().setItem(4, BallSkin.createSparringBotItem(sparringBot != null));
                Msg.title(
                    solo,
                    "<gradient:#FF7A18:#FFD43B><bold>🏀 ᴇɴᴛʀᴀɪɴᴇᴍᴇɴᴛ ʟɪʙʀᴇ</bold></gradient>",
                    "<gray>ᴠɪꜱᴇᴢ ʟᴇ ᴄᴇʀᴄʟᴇ + ᴄʟɪᴄ-ᴅʀᴏɪᴛ • ꜱʟᴏᴛ 5 = ᴅᴇꜰᴇɴꜱᴇᴜʀ ɪᴀ !</gray>",
                    200, 2400, 400
                );
            }
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
        for (Player p : onlineParticipants()) {
            Msg.sendFramedCard(p, "Coup d'Envoi • " + mode.id().toUpperCase(), List.of(
                "<#FF5444><bold>IGNIS</bold></#FF5444> <gray>contre</gray> <#3DB8FF><bold>FROST</bold></#3DB8FF> <dark_gray>•</dark_gray> <gold>" + arena.displayName() + "</gold>",
                "<gray>Horloge des tirs: <yellow>24s</yellow> • Astuce: <aqua>[F]</aqua> pour le Tab Central 3D !</gray>"
            ));
        }
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
        if (actionBarNoticeTicks > 0) {
            actionBarNoticeTicks--;
        }
        arena.tickDisplays(shotClockSeconds());
        tickPlayerVisualRigs();

        // Enforce player map barrier containment every 4 ticks!
        if (state != State.FINISHED && tickCounter % 4 == 0) {
            for (Player p : onlineParticipants()) {
                TeamSide s = getTeam(p.getUniqueId());
                if (s != TeamSide.SPECTATOR) {
                    arena.enforcePlayerContainment(p, s);
                }
            }
        }

        // Tick Practice AI Defender Bot if enabled!
        if (sparringBot != null) {
            Player solo = onlineParticipants().stream().findFirst().orElse(null);
            if (solo != null) {
                Vector rim = selectClosestOrOffensiveRim(solo, getTeam(solo.getUniqueId()));
                boolean charging = activeShotCharges.containsKey(solo.getUniqueId());
                sparringBot.tick(solo, rim, ball, charging);
            }
        }

        if (state == State.WAITING) {
            if (ball != null) {
                ball.tick(this);
                if (ball.holderId() == null) {
                    checkPlayerBallInterceptionAndRebounds();
                }
            }
            tickPossessionShotClock(false);
            tickShotChargesAndLiveAimHud();
            syncBallHotbarItems(false);

            if (warmupBannerTicks > 0) {
                warmupBannerTicks--;
                for (Player p : onlineParticipants()) {
                    hudManager.showScorerCelebration(p, celebScorerName, celebRoleTag, celebVariant, celebSubLabel, celebSubValue);
                }
            } else if (tickCounter % 5 == 0) {
                String waitLabel = "ᴀᴛᴛᴇɴᴛᴇ " + activeCompetitorCount() + "/" + mode.maxPlayers();
                int sc = shotClockSeconds();
                for (Player p : onlineParticipants()) {
                    hudManager.showLiveScorebug(p, "IGNIS", score(TeamSide.IGNIS), waitLabel, sc, score(TeamSide.FROST), "FROST");
                }
                arena.updateJumbotron("ɪɢɴɪꜱ", score(TeamSide.IGNIS), "ᴇɴ ᴀᴛᴛᴇɴᴛᴇ (" + activeCompetitorCount() + "/" + mode.maxPlayers() + ")", sc, score(TeamSide.FROST), "ꜰʀᴏꜱᴛ");
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

        tickShotChargesAndLiveAimHud();
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

        if (mode != GameModeType.THREE_POINT_CONTEST) {
            if (tickPossessionShotClock(mode.competitiveTeamMatch())) {
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

        if (mode != GameModeType.PRACTICE && ticksRemainingInPeriod <= 0) {
            handlePeriodEndBuzzer();
        }
    }

    /**
     * Ticks the 24-second Shot Clock in BOTH Competitive Matches AND Practice / Warmup!
     *
     * @return true if a competitive turnover occurred this tick
     */
    private boolean tickPossessionShotClock(boolean competitiveTurnoverOnExpire) {
        boolean possessionClockActive = ball != null && (
            ball.holderId() != null
                || ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT
                || ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT
        );
        if (!possessionClockActive) {
            return false;
        }

        shotClockTicks--;
        if (shotClockTicks > 0 && shotClockTicks <= 100 && shotClockTicks % 20 == 0) {
            for (Player p : onlineParticipants()) {
                Msg.playPersonalPackSound(p, "ui.click", 0.68f, 1.38f);
            }
        }
        if (shotClockTicks <= 0) {
            if (competitiveTurnoverOnExpire) {
                handleShotClockViolation();
                return true;
            }
            // Practice / Warmup 24s Buzzer: sounds buzzer, notifies handler, resets hot streak & restarts fresh 24s!
            Msg.playPackSound(arena.centerCourtLocation(), "game.buzzer", 0.95f, 1.02f);
            this.actionBarNoticeTicks = 28;
            for (Player p : onlineParticipants()) {
                getBoxScore(p.getUniqueId()).hotStreak = 0;
                p.sendActionBar(Msg.parse("<red><bold>⏱ 24ꜱ ᴇᴄᴏᴜʟᴇᴇꜱ — ɴᴏᴜᴠᴇʟʟᴇ ᴘᴏꜱꜱᴇꜱꜱɪᴏɴ (24ꜱ) !</bold></red>"));
            }
            resetShotClock(false);
        }
        return false;
    }

    private void checkPlayerBallInterceptionAndRebounds() {
        UUID passerOrShooter = ball.lastTouchId();
        TeamSide lastTouchTeam = passerOrShooter != null ? getTeam(passerOrShooter) : null;

        for (Player p : onlineParticipants()) {
            TeamSide myTeam = getTeam(p.getUniqueId());
            if (myTeam == TeamSide.SPECTATOR) {
                continue;
            }

            // 1. Passing-Lane Interception by an opposing defender!
            if (ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT
                && lastTouchTeam != null
                && myTeam == lastTouchTeam.opposite()
                && p.getLocation().clone().add(0, 1.0, 0).toVector().distanceSquared(ball.position()) <= 1.55 * 1.55) {
                PlayerProfile defProf = statsManager.getOrCreate(p);
                MatchBoxScore defBox = getBoxScore(p.getUniqueId());
                defBox.steals++;
                defProf.incrementSteals();
                resetShotClock(false);
                ball.attachToHolder(p, defProf.selectedBallSkin(), defBox.hotStreak >= 3);
                Msg.playPackSound(p.getLocation(), "shoe.squeak", 1.0f, 1.24f);
                broadcast("<green><bold>⚡ ᴘᴀꜱꜱᴇ ɪɴᴛᴇʀᴄᴇᴘᴛᴇᴇ</bold></green> <gray>ᴘᴀʀ</gray> <white>" + p.getName() + "</white> <gray>!</gray>");
                return;
            }

            // 2. Mid-air Alley-Oop Finish: jumping near the rim while a teammate's pass arrives!
            if (ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT && !p.isOnGround()) {
                Vector targetRim = (mode == GameModeType.PRACTICE || state == State.WAITING)
                    ? selectClosestOrOffensiveRim(p, myTeam)
                    : arena.targetRimForTeam(myTeam);
                if (p.getLocation().toVector().distanceSquared(targetRim) <= 4.0 * 4.0
                    && p.getEyeLocation().toVector().distanceSquared(ball.position()) <= 2.1 * 2.1) {
                    PlayerProfile prof = statsManager.getOrCreate(p);
                    ball.startSlamDunk(p, targetRim, prof.selectedDunkStyle(), 12, true);
                    triggerRigAction(p.getUniqueId(), AnimationKinematics.ActionClip.SLAM_DUNK, prof.selectedDunkStyle());
                    Msg.title(p, "<gradient:#FF7A18:#FFE259><bold>🔥 ᴀʟʟᴇʏ-ᴏᴏᴘ !</bold></gradient>", "", 50, 900, 200);
                    return;
                }
            }

            // 3. Rebound / Loose-Ball Pickup!
            if (ball.canBePickedUpBy(p)) {
                UUID prevHolder = ball.lastTouchId();
                boolean wasMissedShot = (prevHolder != null && !prevHolder.equals(p.getUniqueId()));
                PlayerProfile prof = statsManager.getOrCreate(p);
                MatchBoxScore box = getBoxScore(p.getUniqueId());

                if (wasMissedShot && ball.position().getY() > arena.geometry().floorY() + 0.65) {
                    box.rebounds++;
                    prof.incrementRebounds();
                    TeamSide prevTeam = getTeam(prevHolder);
                    boolean offensiveRebound = (prevTeam == myTeam);
                    resetShotClock(offensiveRebound);
                } else if (prevHolder != null && getTeam(prevHolder) != myTeam) {
                    resetShotClock(false);
                }

                boolean onFire = box.hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3);
                ball.attachToHolder(p, prof.selectedBallSkin(), onFire);
                activeShotCharges.remove(p.getUniqueId());

                if (mode == GameModeType.THREE_POINT_CONTEST) {
                    advanceContestShot(p, false);
                }
                return;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //   SKILL AIMING, 3D TRAJECTORY RIBBON, DUNK TIMING, STEALS, POKES & BLOCKS
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isPlayableState() {
        return state == State.LIVE || state == State.WAITING;
    }

    public boolean handleBallHandlerPrimaryUse(Player player, boolean fromBallSlot) {
        if (!isPlayableState() || ball == null) {
            return false;
        }

        // If player is mid-air in a Slam Dunk, clicking locks in their aerial Dunk Timing!
        if (ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT && player.getUniqueId().equals(ball.holderId())) {
            lockPlayerDunkTiming(player);
            return false;
        }

        if (!player.getUniqueId().equals(ball.holderId())) {
            return false;
        }

        TeamSide team = getTeam(player.getUniqueId());
        Vector targetRim = resolveTargetRim(player, team);
        PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(
            player.getEyeLocation().toVector(),
            player.getEyeLocation().getDirection(),
            targetRim
        );

        // 1. Skill-Based 3D Slam Dunk Trigger (Sprinting + Aimed at Rim + Open Driving Lane inside 3.45m)!
        if (mode != GameModeType.THREE_POINT_CONTEST
            && player.isSprinting()
            && arena.geometry().isInDunkZone(player.getLocation().getX(), player.getLocation().getZ(), targetRim)
            && Math.abs(aim.yawErrorDeg()) <= 26.0
            && player.getLocation().getPitch() <= 30.0f) {

            Vector toRimHoriz = targetRim.clone().subtract(player.getLocation().toVector()).setY(0);
            if (toRimHoriz.lengthSquared() > 1e-4) {
                toRimHoriz.normalize();
            }
            // Check if a set defender is cutting off the driving lane!
            if (isDunkLaneBlockedByDefender(player, team, targetRim)) {
                this.actionBarNoticeTicks = 22;
                Msg.playPersonalPackSound(player, "shoe.squeak", 0.9f, 0.85f);
                player.sendActionBar(Msg.parse(
                    "<red><bold>⛔ ᴀᴄᴄᴇꜱ ᴀᴜ ᴄᴇʀᴄʟᴇ ꜰᴇʀᴍᴇ !</bold></red> <yellow>ᴄʀᴏꜱꜱᴏᴠᴇʀ (ꜱʜɪꜰᴛ) ᴏᴜ ᴛɪʀᴇᴢ !</yellow>"
                ));
                return false;
            }

            activeShotCharges.remove(player.getUniqueId());
            ball.setHolderShotChargeProgress(-1.0);
            PlayerProfile prof = statsManager.getOrCreate(player);
            double dist = player.getLocation().toVector().distance(targetRim);
            int flightTicks = Math.max(12, Math.min(18, (int) Math.round(dist * 3.8)));

            double contest = computeLiveContestRatio(player, team);
            // Contested or long-takeoff dunks require hitting the aerial Dunk Timing Window!
            boolean defaultMake = (contest < 0.22 && dist <= 2.55);
            ball.startSlamDunk(player, targetRim, prof.selectedDunkStyle(), flightTicks, defaultMake);
            triggerRigAction(player.getUniqueId(), AnimationKinematics.ActionClip.SLAM_DUNK, prof.selectedDunkStyle());
            return false;
        }

        // 2. Start or refresh Hold-and-Release NBA 2K Shot Meter + Overhead Gather Pose!
        ShotChargeState charge = activeShotCharges.get(player.getUniqueId());
        if (charge == null) {
            charge = new ShotChargeState();
            charge.usingConsumableHold = fromBallSlot;
            activeShotCharges.put(player.getUniqueId(), charge);
            ball.setHolderShotChargeProgress(charge.progress);
            Msg.playPersonalPackSound(player, "ui.click", 0.55f, 0.95f);
        } else {
            charge.ticksSinceLastHoldPing = 0;
            if (fromBallSlot) {
                charge.usingConsumableHold = true;
            }
        }
        return fromBallSlot;
    }

    private void lockPlayerDunkTiming(Player dunker) {
        if (ball == null || ball.isDunkTimingLocked()) {
            return;
        }
        double prog = ball.dunkProgress();
        double contest = computeLiveContestRatio(dunker, getTeam(dunker.getUniqueId()));
        double minGreen = contest >= 0.40 ? 0.66 : 0.54;
        boolean goodTiming = prog >= minGreen && prog <= 0.97;
        ball.lockDunkTiming(goodTiming);
        dunker.sendActionBar(Glyphs.composeDunkTimingHud(prog, true, goodTiming));
        if (goodTiming) {
            Msg.playPersonalPackSound(dunker, "game.green_release", 0.9f, 1.12f);
        } else {
            Msg.playPersonalPackSound(dunker, "ui.click", 0.8f, 0.72f);
        }
    }

    public boolean tryReleaseChargedShotNow(Player player) {
        ShotChargeState charge = activeShotCharges.remove(player.getUniqueId());
        if (charge == null) {
            return false;
        }
        if (ball != null) {
            ball.setHolderShotChargeProgress(-1.0);
        }
        executeJumpShotRelease(player, charge.progress);
        return true;
    }

    private void tickShotChargesAndLiveAimHud() {
        // 1. Aerial Dunk Timing Meter HUD while mid-flight!
        if (ball != null && ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT && ball.holderId() != null) {
            Player dunker = Bukkit.getPlayer(ball.holderId());
            if (dunker != null && dunker.isOnline()) {
                dunker.sendActionBar(Glyphs.composeDunkTimingHud(
                    ball.dunkProgress(), ball.isDunkTimingLocked(), ball.willDunkSucceed()
                ));
            }
        }

        // 2. Live Idle Aiming Compass + Rim Lock Indicator while dribbling (every 3 ticks)
        if (ball != null && ball.mode() == BasketballEntity.BallMode.DRIBBLING && ball.holderId() != null
            && !activeShotCharges.containsKey(ball.holderId())
            && actionBarNoticeTicks <= 0
            && tickCounter % 3 == 0) {
            Player holder = Bukkit.getPlayer(ball.holderId());
            if (holder != null && holder.isOnline()) {
                TeamSide team = getTeam(holder.getUniqueId());
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

                holder.sendActionBar(Glyphs.composeIdleAimCompass(
                    aim.aimingAtHoop(), aim.yawErrorDeg(), aim.horizontalDist(), isThree, inDunk, contest
                ));

                // Subtle personal rim-lock halo when crosshair aligns with the basket!
                if (aim.aimingAtHoop() && Math.abs(aim.yawErrorDeg()) <= 5.5) {
                    spawnPersonalRimHalo(holder, targetRim, Math.abs(aim.yawErrorDeg()) <= 2.6
                        ? Color.fromRGB(76, 255, 136)
                        : Color.fromRGB(92, 232, 255));
                }
            }
        }

        // 3. Active Shot Charge + Live 3D Trajectory Ribbon Preview!
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
                if (ball != null) {
                    ball.setHolderShotChargeProgress(-1.0);
                }
                continue;
            }

            st.ticksActive++;
            st.progress += 0.058; // Reaches 0.92 sweet spot in ~14.8 ticks (~0.74s)!
            st.ticksSinceLastHoldPing++;
            ball.setHolderShotChargeProgress(st.progress);

            TeamSide team = getTeam(playerId);
            Vector targetRim = resolveTargetRim(p, team);
            Vector lookDir = p.getEyeLocation().getDirection();
            PhysicsMath.AimEvaluation aim = PhysicsMath.evaluateShooterAim(
                p.getEyeLocation().toVector(), lookDir, targetRim
            );
            boolean isThree = arena.geometry().isThreePointShot(p.getLocation().getX(), p.getLocation().getZ(), targetRim);
            double contest = computeLiveContestRatio(p, team);

            p.sendActionBar(Glyphs.composeProAimHud(
                st.progress, false, null, aim.aimingAtHoop(), aim.yawErrorDeg(), aim.horizontalDist(), isThree, contest
            ));

            // Render sleek client-only 3D Curved Trajectory Ribbon every 2 ticks so shooter sees their exact arc!
            if (st.ticksActive % 2 == 1) {
                renderPersonalAimArcRibbon(p, targetRim, lookDir, aim, st.progress, contest);
            }

            boolean releasedConsumable = st.usingConsumableHold && st.ticksActive >= 2 && !p.isHandRaised();
            boolean releasedFallback = !st.usingConsumableHold && st.progress >= 0.36 && st.ticksSinceLastHoldPing >= 5;
            boolean overcharged = (st.progress >= 1.18);
            if (releasedConsumable || releasedFallback || overcharged) {
                double finalCharge = st.progress;
                it.remove();
                ball.setHolderShotChargeProgress(-1.0);
                if (p.isHandRaised()) {
                    p.clearActiveItem();
                }
                executeJumpShotRelease(p, finalCharge);
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
        double g = plugin.getConfig().getDouble("physics.gravity", 0.046);
        double drag = plugin.getConfig().getDouble("physics.air-drag", 0.991);
        Vector start = shooter.getEyeLocation().toVector().add(lookDir.clone().normalize().multiply(0.45));
        MatchBoxScore box = getBoxScore(shooter.getUniqueId());
        boolean onFire = box.hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3);
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
            arcColor = Color.fromRGB(255, 92, 108); // Free directional throw (bleachers/pass)
        } else if (previewQuality == ShotReleaseQuality.PERFECT_GREEN && Math.abs(aim.yawErrorDeg()) <= 2.8 && contestRatio < 0.35) {
            arcColor = Color.fromRGB(64, 255, 142); // Deadeye Green Swish Lock!
        } else if (Math.abs(aim.yawErrorDeg()) <= 4.8 && contestRatio < 0.45) {
            arcColor = Color.fromRGB(78, 226, 255); // Clean Cyan Cylinder Line
        } else {
            arcColor = Color.fromRGB(255, 178, 56); // Angled / Contested Warning
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
        if (ball == null || !shooter.getUniqueId().equals(ball.holderId())) {
            return;
        }
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
        boolean onFire = box.hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3);
        double contest = computeLiveContestRatio(shooter, team);

        // Heavy contest shrinks the effective Green Window unless release timing is pinpoint!
        double adjustedCharge = chargeProgress;
        if (contest >= 0.45 && Math.abs(chargeProgress - 0.92) > 0.035) {
            adjustedCharge += (chargeProgress >= 0.92 ? 0.07 : -0.07) * contest;
        }

        ShotReleaseQuality quality = ShotReleaseQuality.evaluate(adjustedCharge, dist, onFire);
        // Aiming wide off the cylinder cannot be a "Green Swish"
        if (!aim.aimingAtHoop() || Math.abs(aim.yawErrorDeg()) > 5.2) {
            if (quality == ShotReleaseQuality.PERFECT_GREEN) {
                quality = ShotReleaseQuality.SLIGHTLY_LATE;
            }
        }

        this.actionBarNoticeTicks = 24;
        shooter.sendActionBar(Glyphs.composeProAimHud(
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

        // Athletic jump-shot hop on release
        if (shooter.isOnGround()) {
            Vector v = shooter.getVelocity();
            shooter.setVelocity(new Vector(v.getX() * 0.62, 0.29, v.getZ() * 0.62));
        }

        Vector start = shooter.getEyeLocation().toVector().add(lookDir.clone().normalize().multiply(0.45));
        Vector velocity = PhysicsMath.solveSkillShotVelocity(
            start,
            lookDir,
            targetRim,
            arena.geometry().hoopForwardForRim(targetRim),
            aim,
            chargeProgress,
            plugin.getConfig().getDouble("physics.gravity", 0.046),
            plugin.getConfig().getDouble("physics.air-drag", 0.991),
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
        }

        ball.launchShot(shooter, start, velocity, isThree, dist);
        triggerRigAction(shooter.getUniqueId(), AnimationKinematics.ActionClip.THROW_SHOT, null);

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
            }, 34L);
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
        // Alley-Oop Lob ONLY when looking high AND toward the hoop!
        boolean alleyOop = passer.getLocation().getPitch() < -22.0f && Math.abs(aim.yawErrorDeg()) <= 28.0;
        Vector start = passer.getEyeLocation().toVector().add(eyeDir.clone().multiply(0.55));
        Vector passVel;

        this.actionBarNoticeTicks = 20;
        if (alleyOop) {
            Vector lobTarget = targetRim.clone().add(new Vector(0, 0.55, 0));
            passVel = PhysicsMath.solveBallisticArc(
                start, lobTarget,
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
            passer.sendActionBar(Msg.parse("<aqua>⚡ ᴘᴀꜱꜱᴇ ᴅɪʀᴇᴄᴛɪᴏɴɴᴇʟʟᴇ !</aqua>"));
        }

        ball.launchPass(passer, start, passVel, alleyOop);
        triggerRigAction(passer.getUniqueId(), AnimationKinematics.ActionClip.THROW_PASS, null);
    }

    /**
     * Ankle-Breaker Crossover (Sneak toggle, F swap-hand, or Slot 2 right-click)!
     */
    public void handleCrossoverMove(Player handler) {
        if (!isPlayableState() || ball == null || !handler.getUniqueId().equals(ball.holderId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("physics.crossover-cooldown-ms", 1650L);
        if (now - crossoverCooldowns.getOrDefault(handler.getUniqueId(), 0L) < cd) {
            return;
        }
        crossoverCooldowns.put(handler.getUniqueId(), now);

        ball.triggerCrossoverHandSwitch(handler);
        triggerRigAction(handler.getUniqueId(), AnimationKinematics.ActionClip.CROSSOVER, null);

        // Quick lateral burst TOWARD the newly switched dribble hand!
        double yawRad = Math.toRadians(handler.getLocation().getYaw());
        Vector forward = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vector playerRight = new Vector(-Math.cos(yawRad), 0, -Math.sin(yawRad));
        double burstSign = ball.isRightHandDribble() ? 0.58 : -0.58;
        handler.setVelocity(forward.multiply(0.48).add(playerRight.multiply(burstSign)).setY(0.15));

        // Check Practice AI Defender Bot Ankle-Breaker!
        if (sparringBot != null && sparringBot.tryBreakAnkles(handler)) {
            this.actionBarNoticeTicks = 26;
            handler.sendActionBar(Msg.parse("<light_purple><bold>⚡ ᴀɴᴋʟᴇ-ʙʀᴇᴀᴋᴇʀ ꜱᴜʀ ʟᴇ ᴅᴇꜰᴇɴꜱᴇᴜʀ ɪᴀ ! ᴠᴏɪᴇ ʟɪʙʀᴇ !</bold></light_purple>"));
        }

        // Check PvP opposing defenders within 3.0m!
        TeamSide myTeam = getTeam(handler.getUniqueId());
        for (Player def : onlineParticipants()) {
            if (getTeam(def.getUniqueId()) == myTeam.opposite()
                && def.getLocation().distanceSquared(handler.getLocation()) <= 3.0 * 3.0) {
                def.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 32, 2, false, false, true));
                def.getWorld().spawnParticle(Particle.CLOUD, def.getLocation().add(0, 0.2, 0), 16, 0.3, 0.1, 0.3, 0.06);
                Msg.title(def, "<red><bold>💫 ᴀɴᴋʟᴇꜱ ʙʀᴏᴋᴇɴ !</bold></red>", "<gray>ᴄʀᴏꜱꜱᴏᴠᴇʀ ᴅᴇ " + handler.getName() + "</gray>", 50, 750, 200);
                this.actionBarNoticeTicks = 26;
                handler.sendActionBar(Msg.parse("<light_purple><bold>⚡ ᴀɴᴋʟᴇ-ʙʀᴇᴀᴋᴇʀ ꜱᴜʀ " + def.getName() + " !</bold></light_purple>"));
                if (state == State.LIVE) {
                    statsManager.getOrCreate(handler).incrementAnkleBreakers();
                }
            }
        }
    }

    /**
     * Active Defense: On-Ball Steal, Poke-Loose Strip, Mid-Air Shot/Pass/Dunk Block & Loose-Ball Hustle Dive!
     */
    public void handleDefensiveAction(Player defender) {
        if (!isPlayableState() || ball == null) {
            return;
        }
        // 1. If ball-handler is mid-dunk, Left-Click locks in their Dunk Timing!
        if (ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT && defender.getUniqueId().equals(ball.holderId())) {
            lockPlayerDunkTiming(defender);
            return;
        }
        // 2. If ball-handler is charging a shot, Left-Click locks in the Shot Release!
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
        defender.swingMainHand();

        // 3. Mid-Air Shot / Pass / Poster-Dunk Block when jumping near the ball (never self-block your own shot/lob!)
        boolean isOwnBall = defender.getUniqueId().equals(ball.lastTouchId())
            || defender.getUniqueId().equals(ball.shooterId())
            || defender.getUniqueId().equals(ball.holderId());
        boolean blockableFlight = !isOwnBall && (
            ball.mode() == BasketballEntity.BallMode.IN_SHOT_FLIGHT
                || ball.mode() == BasketballEntity.BallMode.IN_PASS_FLIGHT
                || ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT
        );

        if (blockableFlight
            && !defender.isOnGround()
            && defender.getEyeLocation().toVector().distanceSquared(ball.position()) <= 2.45 * 2.45) {

            if (mode.competitiveTeamMatch() && ball.lastTouchId() != null && getTeam(ball.lastTouchId()) == defTeam) {
                return;
            }
            long blkCd = plugin.getConfig().getLong("physics.block-cooldown-ms", 900L);
            if (now - blockCooldowns.getOrDefault(defender.getUniqueId(), 0L) < blkCd) {
                return;
            }
            blockCooldowns.put(defender.getUniqueId(), now);

            // Goaltending protection when shot is falling into cylinder
            if (ball.mode() == BasketballEntity.BallMode.IN_SHOT_FLIGHT) {
                Vector defRim = arena.targetRimForTeam(defTeam.opposite());
                if (ball.velocity().getY() < -0.04 && ball.position().distanceSquared(defRim) <= 1.15 * 1.15) {
                    defender.sendActionBar(Msg.parse("<red>⚠ ᴄᴏɴᴛʀᴇ ɪʟʟᴇɢᴀʟ (ɢᴏᴀʟᴛᴇɴᴅɪɴɢ) ᴇᴠɪᴛᴇ !</red>"));
                    return;
                }
            }

            boolean stuffedDunk = (ball.mode() == BasketballEntity.BallMode.IN_DUNK_FLIGHT);
            triggerRigAction(defender.getUniqueId(), AnimationKinematics.ActionClip.BLOCK_REACH, null);
            Vector swat = defender.getEyeLocation().getDirection().normalize().multiply(0.86).setY(0.30);
            ball.popLoose(swat, defender.getUniqueId());
            Msg.playPackSound(ball.position().toLocation(defender.getWorld()), "defense.block", 1.3f, stuffedDunk ? 0.86f : 0.96f);
            defender.getWorld().spawnParticle(Particle.SWEEP_ATTACK, ball.position().toLocation(defender.getWorld()), 2, 0.1, 0.1, 0.1, 0);
            defender.getWorld().spawnParticle(Particle.CRIT, ball.position().toLocation(defender.getWorld()), 22, 0.25, 0.25, 0.25, 0.16);

            getBoxScore(defender.getUniqueId()).blocks++;
            statsManager.getOrCreate(defender).incrementBlocks();
            broadcast(stuffedDunk
                ? "<aqua><bold>⛔ ᴄᴏɴᴛʀᴇ ᴍᴏɴꜱᴛʀᴜᴇᴜx ᴀᴜ ᴄᴇʀᴄʟᴇ (ᴅᴜɴᴋ ꜱᴛᴏᴘᴘᴇ)</bold></aqua> <gray>ᴘᴀʀ</gray> <white>" + defender.getName() + "</white> <gray>!</gray>"
                : "<aqua><bold>⛔ ᴄᴏɴᴛʀᴇ ᴍᴏɴꜱᴛʀᴜᴇᴜx</bold></aqua> <gray>ᴅᴇ</gray> <white>" + defender.getName() + "</white> <gray>!</gray>");
            return;
        }

        // 4. Loose-Ball Hustle Dive / Snatch when ball is free on the court!
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
                    defender.sendActionBar(Msg.parse("<green><bold>⚡ ᴘʟᴏɴɢᴇᴏɴ ᴅᴇᴄɪꜱɪꜰ — ʙᴀʟʟᴏɴ ʀᴇᴄᴜᴘᴇʀᴇ !</bold></green>"));
                }
            }
            return;
        }

        // 5. Skill On-Ball Steal & Poke-Loose Strip!
        UUID holderId = ball.holderId();
        if (holderId != null && !holderId.equals(defender.getUniqueId()) && ball.mode() == BasketballEntity.BallMode.DRIBBLING) {
            if (mode.competitiveTeamMatch() && getTeam(holderId) == defTeam) {
                return;
            }
            Player holder = Bukkit.getPlayer(holderId);
            if (holder == null) {
                return;
            }
            double playerDist = defender.getLocation().distance(holder.getLocation());
            double ballDist = defender.getEyeLocation().toVector().distance(ball.position());
            if (playerDist > 2.95 && ballDist > 2.55) {
                return;
            }

            long stlCd = plugin.getConfig().getLong("physics.steal-cooldown-ms", 1150L);
            if (now - stealCooldowns.getOrDefault(defender.getUniqueId(), 0L) < stlCd) {
                return;
            }
            stealCooldowns.put(defender.getUniqueId(), now);
            triggerRigAction(defender.getUniqueId(), AnimationKinematics.ActionClip.STEAL_SWIPE, null);

            // Evaluate directional hand-side advantage: attacking the exposed dribble hand boosts strip chance by +22%!
            double yawRad = Math.toRadians(holder.getLocation().getYaw());
            Vector holderRight = new Vector(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
            Vector holderToDef = defender.getLocation().toVector().subtract(holder.getLocation().toVector()).setY(0);
            boolean attackingExposedSide = holderToDef.lengthSquared() > 1e-4
                && ((holderToDef.normalize().dot(holderRight) >= 0.15) == ball.isRightHandDribble());

            double baseStrip = 0.42
                + (attackingExposedSide ? 0.22 : -0.05)
                + (holder.isSprinting() ? 0.16 : 0.0)
                + (activeShotCharges.containsKey(holderId) ? 0.12 : 0.0)
                - (holder.isSneaking() ? 0.22 : 0.0);
            baseStrip = Math.max(0.14, Math.min(0.85, baseStrip));

            double roll = rng.nextDouble();
            if (roll <= baseStrip * 0.58) {
                // CLEAN RIP STEAL -> directly into defender's hands!
                activeShotCharges.remove(holderId);
                ball.setHolderShotChargeProgress(-1.0);
                if (holder.isHandRaised()) {
                    holder.clearActiveItem();
                }

                PlayerProfile defProf = statsManager.getOrCreate(defender);
                MatchBoxScore defBox = getBoxScore(defender.getUniqueId());
                defBox.steals++;
                defProf.incrementSteals();
                getBoxScore(holderId).hotStreak = 0;

                boolean onFire = defBox.hotStreak >= plugin.getConfig().getInt("match.on-fire-streak-threshold", 3);
                ball.attachToHolder(defender, defProf.selectedBallSkin(), onFire);
                resetShotClock(false);

                Msg.playPackSound(defender.getLocation(), "shoe.squeak", 1.0f, 1.22f);
                holder.sendActionBar(Msg.parse("<red><bold>⚡ ʙᴀʟʟᴏɴ ɪɴᴛᴇʀᴄᴇᴘᴛᴇ ᴘᴀʀ " + defender.getName() + " !</bold></red>"));
                broadcast("<green><bold>⚡ ɪɴᴛᴇʀᴄᴇᴘᴛɪᴏɴ ᴘʀᴏᴘʀᴇ</bold></green> <gray>ᴅᴇ</gray> <white>" + defender.getName()
                    + "</white> <gray>ꜱᴜʀ " + holder.getName() + " !</gray>");
            } else if (roll <= baseStrip) {
                // POKE-LOOSE STRIP -> knocks ball free onto the court for a scramble!
                activeShotCharges.remove(holderId);
                ball.setHolderShotChargeProgress(-1.0);
                if (holder.isHandRaised()) {
                    holder.clearActiveItem();
                }

                Vector pokeDir = defender.getLocation().getDirection().setY(0).normalize()
                    .multiply(0.38).add(new Vector((rng.nextDouble() - 0.5) * 0.25, 0.25, (rng.nextDouble() - 0.5) * 0.25));
                ball.popLoose(pokeDir, defender.getUniqueId());
                getBoxScore(defender.getUniqueId()).steals++;
                statsManager.getOrCreate(defender).incrementSteals();
                getBoxScore(holderId).hotStreak = 0;

                Msg.playPackSound(ball.position().toLocation(defender.getWorld()), "ball.bounce", 1.0f, 1.26f);
                defender.getWorld().spawnParticle(Particle.CRIT, ball.position().toLocation(defender.getWorld()), 12, 0.2, 0.2, 0.2, 0.1);
                broadcast("<aqua><bold>💥 ʙᴀʟʟᴏɴ ᴘᴏᴋᴇ ᴀᴜ ꜱᴏʟ</bold></aqua> <gray>ᴘᴀʀ</gray> <white>" + defender.getName() + "</white> <gray>!</gray>");
            } else {
                // Reach-in lunge recovery
                defender.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 14, 1, false, false, false));
                defender.sendActionBar(Msg.parse(
                    holder.isSneaking()
                        ? "<yellow>🛡 ᴘᴏʀᴛᴇᴜʀ ᴇɴ ᴘʀᴏᴛᴇᴄᴛɪᴏɴ — ᴀᴛᴛᴀǫᴜᴇᴢ ᴄᴏᴛᴇ ᴍᴀɪɴ ᴅᴇ ᴅʀɪʙʙʟᴇ !</yellow>"
                        : "<yellow>ᴛᴇɴᴛᴀᴛɪᴠᴇ ᴅ'ɪɴᴛᴇʀᴄᴇᴘᴛɪᴏɴ ᴍᴀɴǫᴜᴇᴇ !</yellow>"
                ));
            }
        }
    }

    private double computeLiveContestRatio(Player shooter, TeamSide shooterTeam) {
        double maxContest = 0.0;
        if (sparringBot != null) {
            maxContest = Math.max(maxContest, sparringBot.computeContestAgainst(shooter));
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
            double c = ((2.85 - dist) / 2.25) * 0.68 * frontFactor + (!other.isOnGround() ? 0.22 : 0.0);
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
                continue; // Ankle-broken defender cannot wall up!
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
        UUID effectiveShooterId = shooterId != null ? shooterId : (ball != null ? ball.lastTouchId() : null);
        Player shooter = effectiveShooterId != null ? Bukkit.getPlayer(effectiveShooterId) : null;
        String scorerName;
        if (shooter != null) {
            scorerName = shooter.getName();
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
                shooter.sendActionBar(Msg.parse(currentShotIsMoneyBall
                    ? "<gradient:#3DB8FF:#FFFFFF:#FF4D4D><bold>★ ᴍᴏɴᴇʏ ʙᴀʟʟ +2 ᴘᴛꜱ ! (ᴛᴏᴛᴀʟ: " + score(TeamSide.IGNIS) + ")</bold></gradient>"
                    : "<green><bold>✓ ꜱᴘʟᴀꜱʜ +1 ᴘᴛ ! (ᴛᴏᴛᴀʟ: " + score(TeamSide.IGNIS) + ")</bold></green>"));
                advanceContestShot(shooter, false);
            }
            return;
        }

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

        String badge = slamDunk ? "<gold><bold>⚡ ꜱʟᴀᴍ ᴅᴜɴᴋ</bold></gold>"
            : (threePointer ? "<aqua><bold>🎯 ꜱᴘʟᴀꜱʜ 3-ᴘᴛꜱ</bold></aqua>" : "<yellow><bold>🏀 ᴘᴀɴɪᴇʀ +2</bold></yellow>");
        String assistNote = assister != null ? " <dark_gray>(</dark_gray><gray>ᴀꜱꜱɪꜱᴛ:</gray> <white>" + assister.getName() + "</white><dark_gray>)</dark_gray>" : "";
        String fireNote = scorerBox.hotStreak >= 3 ? " <gradient:#FF3B00:#FFD43B><bold>🔥 ᴏɴ ꜰɪʀᴇ x" + scorerBox.hotStreak + " !</bold></gradient>" : "";
        broadcast(badge + " <gray>ᴘᴀʀ</gray> <white><bold>" + scorerName + "</bold></white>" + assistNote
            + " <dark_gray>•</dark_gray> <gray>" + String.format("%.1fᴍ", shotDistanceBlocks) + "</gray>" + fireNote);

        if (mode == GameModeType.PRACTICE || state == State.WAITING) {
            this.warmupBannerTicks = 65;
            for (Player p : onlineParticipants()) {
                hudManager.showScorerCelebration(p, celebScorerName, celebRoleTag, celebVariant, celebSubLabel, celebSubValue);
            }
            // Trigger Mannequin Broadcast Camera on Slam Dunks & 3-Point Splashes in Practice when Caméra Panier is enabled!
            long nowMs = System.currentTimeMillis();
            if ((slamDunk || threePointer) && plugin.cinematicCamera() != null && nowMs - lastPracticeCineCamMillis >= 5500L) {
                lastPracticeCineCamMillis = nowMs;
                Vector courtMid = new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ());
                plugin.cinematicCamera().playBasketCelebrationCut(
                    onlineParticipants(),
                    shooter,
                    creditedTeam,
                    rimVec,
                    courtMid,
                    slamDunk,
                    38
                );
            }
            return;
        }

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
        if (plugin.cinematicCamera() != null) {
            Vector courtMid = new Vector(arena.geometry().centerX(), arena.geometry().floorY(), arena.geometry().centerZ());
            plugin.cinematicCamera().playBasketCelebrationCut(
                onlineParticipants(),
                shooter,
                creditedTeam,
                rimVec,
                courtMid,
                slamDunk,
                Math.max(36, Math.min(58, this.stateTimerTicks - 14))
            );
        }
    }

    @Override
    public void onOutOfBounds(Vector lastPos, UUID lastTouchPlayerId) {
        if (mode != GameModeType.PRACTICE && mode.competitiveTeamMatch() && state == State.LIVE) {
            TeamSide lastTeam = lastTouchPlayerId != null ? getTeam(lastTouchPlayerId) : TeamSide.IGNIS;
            TeamSide inboundSide = lastTeam.opposite() == TeamSide.SPECTATOR ? TeamSide.IGNIS : lastTeam.opposite();

            Msg.playPackSound(lastPos.toLocation(arena.world()), "game.whistle", 1.0f, 1.08f);
            broadcast("<yellow>⚑ ꜱᴏʀᴛɪᴇ ᴅᴇ ʙᴀʟʟᴇ !</yellow> <gray>ʀᴇᴍɪꜱᴇ ᴇɴ ᴊᴇᴜ ᴘᴏᴜʀ</gray> "
                + "<#" + String.format("%06X", inboundSide.textColor().value() & 0xFFFFFF) + "><bold>" + inboundSide.smallCapsName() + "</bold>");
            resetShotClock(false);
            awardPossessionToTeam(inboundSide, false);
        } else if (mode == GameModeType.PRACTICE || state == State.WAITING) {
            Player solo = onlineParticipants().stream().findFirst().orElse(null);
            if (solo != null && ball != null) {
                resetShotClock(false);
                PlayerProfile prof = statsManager.getOrCreate(solo);
                ball.attachToHolder(solo, prof.selectedBallSkin(), getBoxScore(solo.getUniqueId()).hotStreak >= 3);
                syncBallHotbarItems(true);
                this.actionBarNoticeTicks = 18;
                solo.sendActionBar(Msg.parse("<yellow>⚑ ꜱᴏʀᴛɪᴇ ᴅᴇ ʙᴀʟʟᴇ — ʙᴀʟʟᴏɴ ʀᴇɴᴅᴜ (24ꜱ) !</yellow>"));
            }
        }
    }

    @Override
    public void onRimContact() {
        // Resets shot clock to 14s minimum when a shot hits the iron in BOTH Competitive AND Practice/Warmup!
        int min14 = plugin.getConfig().getInt("match.offensive-rebound-shot-clock", 14) * 20;
        if (shotClockTicks < min14) {
            shotClockTicks = min14;
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
            for (Player p : onlineParticipants()) {
                Msg.sendFramedCard(p, "Quart-Temps " + currentQuarter + "/" + totalQuarters, List.of(
                    "<#FF5444><bold>Ignis " + score(TeamSide.IGNIS) + "</bold></#FF5444> <dark_gray>—</dark_gray> <#3DB8FF><bold>" + score(TeamSide.FROST) + " Frost</bold></#3DB8FF>",
                    "<gray>Remise en jeu pour <white><bold>" + possess.smallCapsName() + "</bold></white></gray>"
                ));
                plugin.centerTabManager().showTimed(p, 70);
            }
            awardPossessionToTeam(possess, true);
            return;
        }

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
        this.stateTimerTicks = 130;

        int sIgnis = score(TeamSide.IGNIS);
        int sFrost = score(TeamSide.FROST);
        TeamSide winner = sIgnis >= sFrost ? TeamSide.IGNIS : TeamSide.FROST;
        TeamSide loser = winner.opposite();

        UUID mvpId = participants.entrySet().stream()
            .filter(e -> e.getValue() != TeamSide.SPECTATOR)
            .map(Map.Entry::getKey)
            .max(Comparator.comparingDouble(id -> getBoxScore(id).mvpScore() + (getTeam(id) == winner ? 3.5 : 0.0)))
            .orElse(null);

        Player mvpPlayer = mvpId != null ? Bukkit.getPlayer(mvpId) : null;
        String mvpName = mvpPlayer != null ? mvpPlayer.getName() : "MVP";

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
            int appliedDelta = won ? +eloDelta : -Math.max(6, (int) (eloDelta * 0.8));
            prof.recordMatchOutcome(won);
            prof.adjustElo(appliedDelta);
            prof.addXp(won ? 120 : 45);
            if (entry.getKey().equals(mvpId)) {
                prof.incrementMvps();
                prof.addXp(80);
            }

            MatchBoxScore myBox = getBoxScore(entry.getKey());
            prof.recordMatch(new MatchHistoryEntry(
                System.currentTimeMillis(),
                mode.id().toUpperCase(),
                side.name(),
                sIgnis,
                sFrost,
                won,
                myBox.points,
                myBox.assists,
                myBox.rebounds,
                appliedDelta
            ));

            if (p != null) {
                checkRankPromotion(p, oldRank, prof.effectiveRank());
            }
        }
        statsManager.refreshLeaderboardBadges();
        statsManager.saveAsync();

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

        for (Player p : onlineParticipants()) {
            Msg.sendFramedCard(p, "Score Final • " + mode.id().toUpperCase(), List.of(
                "<#FF4D3D><bold>Ignis " + sIgnis + "</bold></#FF4D3D> <dark_gray>—</dark_gray> <#3DB8FF><bold>" + sFrost + " Frost</bold></#3DB8FF>",
                "<gold>🏆 MVP du Match: <white><bold>" + mvpName + "</bold></white> (" + mvpBox.points + " PTS • " + mvpBox.assists + " AST)</gold>",
                "<gray>Variation ELO: <green>+" + eloDelta + "</green> • Tapez <gold>/gg</gold> !</gray>"
            ));
            plugin.centerTabManager().showTimed(p, 110);
        }
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
        this.actionBarNoticeTicks = 24;
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
            speed.setBaseValue(0.118);
        }

        // Keep chestplate & leggings empty so the player's REAL Minecraft skin + Team Glow Outline (202456.png) shine cleanly!
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(BallSkin.createCustomModelItem(
            Material.LEATHER_BOOTS,
            "sneakers_air",
            "<gradient:#FF4D3D:#FFFFFF><bold>👟 ᴀɪʀ ʙᴏꜱᴋᴇᴛ ᴘʀᴏ 26</bold></gradient>",
            List.of("<gray>ᴀᴅʜᴇʀᴇɴᴄᴇ ᴘᴀʀǫᴜᴇᴛ ʜᴀᴜᴛᴇ ᴘᴇʀꜰᴏʀᴍᴀɴᴄᴇ</gray>")
        ));

        PlayerProfile prof = statsManager.getOrCreate(player);
        player.getInventory().setItem(0, BallSkin.createDefensiveStanceItem());
        player.getInventory().setItem(1, GuiMenuManager.customIcon("icon_pass",
            "<aqua><bold>⚡ ᴘᴀꜱꜱᴇ / ᴀʟʟᴇʏ-ᴏᴏᴘ</bold></aqua>",
            List.of("<gray>ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴏᴜ Q: ᴘᴀꜱꜱᴇ ʟᴀꜱᴇʀ • ᴠɪꜱᴇʀ ʜᴀᴜᴛ = ʟᴏʙ</gray>")));
        player.getInventory().setItem(2, GuiMenuManager.customIcon("icon_crossover",
            "<light_purple><bold>🌀 ᴄʀᴏꜱꜱᴏᴠᴇʀ ᴀɴᴋʟᴇ-ʙʀᴇᴀᴋᴇʀ</bold></light_purple>",
            List.of("<gray>ᴄʟɪᴄ-ᴅʀᴏɪᴛ ᴏᴜ ꜱʜɪꜰᴛ: ᴅʀɪʙʙʟᴇ ᴇᴄʟᴀɪʀ</gray>")));
        player.getInventory().setItem(3, GuiMenuManager.customIcon("icon_dunk",
            "<gold><bold>🔥 ꜱᴛʏʟᴇ: " + prof.selectedDunkStyle().displayName() + "</bold></gold>",
            List.of("<gray>ꜱᴘʀɪɴᴛ + ᴠɪꜱᴇʀ ʟᴇ ᴄᴇʀᴄʟᴇ + ᴄʟɪᴄ-ᴅʀᴏɪᴛ = ᴅᴜɴᴋ 3ᴅ !</gray>")));
        if (mode == GameModeType.PRACTICE) {
            player.getInventory().setItem(4, BallSkin.createSparringBotItem(sparringBot != null));
        }
        player.getInventory().setItem(8, GuiMenuManager.customIcon("icon_leave",
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

    public void hideRigEntitiesFromNewJoiner(Player joiner) {
        for (PlayerVisualRig rig : visualRigs.values()) {
            rig.hideFirstPersonRigIfOwner(joiner);
        }
    }

    public void stopAndCleanup() {
        this.state = State.FINISHED;
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
        Location lobbyExit = arena.lobbyExitLocationOrNull();
        boolean autoHotbar = plugin.getConfig().getBoolean("general.auto-equip-lobby-playbook", true);
        List<UUID> ids = new ArrayList<>(participants.keySet());
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
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
        participants.clear();
        activeShotCharges.clear();
        arena.updateJumbotron("ɪɢɴɪꜱ", 0, "ᴛᴇʀʀᴀɪɴ ʟɪʙʀᴇ", 24, 0, "ꜰʀᴏꜱᴛ");
    }
}
