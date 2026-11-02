package fr.bosketball.ui;

import com.destroystokyo.paper.SkinParts;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.arena.Arena;
import fr.bosketball.arena.HoopDisplaySet;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.TeamSide;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.util.Msg;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Broadcast Instant Goal Replay &amp; Camera Engine!
 *   - Continuously records a lightweight rolling ring buffer of 3D basketball + court player frames.
 *   - On every competitive goal (2PT, 3PT Splash, Slam Dunk, Buzzer Beater), re-enacts the actual scoring play
 *     frame-by-frame using Mannequin actors + an interpolated 3D Replay Basketball + dynamic broadcast camera!
 *   - Viewers can press [SHIFT] at any time to skip straight back to court inbound play.
 */
public final class CinematicCameraManager {

    private static final float REPLAY_BALL_SCALE = 0.56f;

    public record ActorSnapshot(
        UUID playerId,
        String name,
        TeamSide side,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean sneaking,
        boolean swingMainHand
    ) {
    }

    public record ReplayFrame(
        Vector ballPos,
        Quaternionf ballRot,
        BallSkin ballSkin,
        boolean ballOnFire,
        List<ActorSnapshot> actors
    ) {
    }

    /**
     * Bounded rolling frame buffer owned per MatchSession (zero allocation churn once warmed).
     */
    public static final class ReplayRingBuffer {
        private final int maxFrames;
        private final Deque<ReplayFrame> frames;

        public ReplayRingBuffer(int maxFrames) {
            this.maxFrames = Math.max(20, maxFrames);
            this.frames = new ArrayDeque<>(this.maxFrames + 2);
        }

        public void record(ReplayFrame frame) {
            if (frame == null) {
                return;
            }
            if (frames.size() >= maxFrames) {
                frames.removeFirst();
            }
            frames.addLast(frame);
        }

        public List<ReplayFrame> snapshotTail(int maxCount) {
            if (frames.isEmpty()) {
                return List.of();
            }
            List<ReplayFrame> all = new ArrayList<>(frames);
            int from = Math.max(0, all.size() - Math.max(1, maxCount));
            return List.copyOf(all.subList(from, all.size()));
        }

        public void clear() {
            frames.clear();
        }

        public int size() {
            return frames.size();
        }
    }

    private static final class ViewerCamState {
        UUID playerId;
        Location savedCourtLocation;
        GameMode savedGameMode;
        ItemDisplay cameraRig;
        Location orbitEndPose;
        boolean orbitStarted;
        int ticksRemaining;
        int totalTicks;
        int frameCursor;
        int climaxFrameIndex;
        boolean climaxTriggered;
        long startedAtMillis;

        Arena arena;
        boolean northHoopScored;
        boolean slamDunk;
        String scorerName;
        String playTag;

        ItemDisplay hiddenLiveBall;
        ItemDisplay replayBall;
        final Map<UUID, Mannequin> actorMannequins = new HashMap<>();
        List<ReplayFrame> recordedFrames = List.of();
        final List<Entity> spawnedActors = new ArrayList<>();
    }

    private final BosketBallPlugin plugin;
    private final Map<UUID, ViewerCamState> activeViewers = new ConcurrentHashMap<>();

    public CinematicCameraManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isWatching(UUID playerId) {
        return playerId != null && activeViewers.containsKey(playerId);
    }

    /**
     * Plays a true frame-by-frame Instant Goal Replay of the scoring sequence for all match participants
     * who have Score Replay Camera enabled in {@code /bb settings}!
     */
    public void playInstantGoalReplay(
        Arena arena,
        BasketballEntity liveBall,
        List<ReplayFrame> frames,
        Collection<Player> matchPlayers,
        Player scorer,
        TeamSide scoringSide,
        Vector rimPos,
        Vector courtCenter,
        boolean slamDunk,
        boolean threePointer,
        int durationTicks
    ) {
        if (plugin != null && !plugin.settings().match().goalReplayEnabled()) {
            return;
        }
        if (frames == null || frames.size() < 6) {
            playBasketCelebrationCut(matchPlayers, scorer, scoringSide, rimPos, courtCenter, slamDunk, durationTicks);
            return;
        }
        if (matchPlayers == null || matchPlayers.isEmpty() || rimPos == null) {
            return;
        }

        int holdTailTicks = 12;
        int targetPlayFrames = Math.clamp(durationTicks - holdTailTicks, 28, 68);
        List<ReplayFrame> clip = frames.size() > targetPlayFrames
            ? frames.subList(frames.size() - targetPlayFrames, frames.size())
            : frames;
        int safeTotalTicks = clip.size() + holdTailTicks;

        ReplayFrame firstFrame = clip.get(0);
        ReplayFrame lastFrame = clip.get(clip.size() - 1);
        boolean isNorthHoop = arena != null
            && rimPos.distanceSquared(arena.geometry().northHoopRim()) < rimPos.distanceSquared(arena.geometry().southHoopRim());

        String sName = scorer != null ? scorer.getName() : (scoringSide == TeamSide.FROST ? "Frost" : "Ignis");
        String playLabel = slamDunk ? "🔥 SLAM DUNK" : (threePointer ? "🎯 3PT SPLASH" : "🏀 BUCKET");

        // Cache skin profiles of online players once
        Map<UUID, ResolvableProfile> skinCache = new HashMap<>();
        for (Player p : matchPlayers) {
            if (p != null && p.isOnline()) {
                skinCache.put(p.getUniqueId(), buildPlayerSkinProfile(p));
            }
        }

        for (Player viewer : matchPlayers) {
            if (viewer == null || !viewer.isOnline() || viewer.getWorld() == null) {
                continue;
            }
            PlayerProfile vProf = plugin.statsManager().getOrCreate(viewer);
            if (!vProf.settings().cinematicScoreCam()) {
                continue;
            }
            stopWatching(viewer, false);

            World w = viewer.getWorld();
            Vector toCenter = new Vector(courtCenter.getX() - rimPos.getX(), 0.0, courtCenter.getZ() - rimPos.getZ());
            if (toCenter.lengthSquared() < 1e-4) {
                toCenter = new Vector(0.0, 0.0, 1.0);
            } else {
                toCenter.normalize();
            }
            Vector rightVec = new Vector(-toCenter.getZ(), 0.0, toCenter.getX());

            // Frame camera so BOTH the shooter's takeoff/release AND the rim are clearly in view!
            Vector startFocus = firstFrame.ballPos().clone().multiply(0.55).add(rimPos.clone().multiply(0.45));
            Vector endFocus = rimPos.clone().add(new Vector(0.0, -0.55, 0.0));

            double camDist = threePointer ? 7.4 : (slamDunk ? 5.2 : 6.2);
            Vector camStartPos = rimPos.clone()
                .add(toCenter.clone().multiply(camDist))
                .add(rightVec.clone().multiply(threePointer ? -3.4 : -2.6))
                .add(new Vector(0.0, 0.35, 0.0));
            Vector camEndPos = rimPos.clone()
                .add(toCenter.clone().multiply(Math.max(4.4, camDist - 1.1)))
                .add(rightVec.clone().multiply(2.4))
                .add(new Vector(0.0, 0.15, 0.0));

            // Keep broadcast camera safely inside arena barrier bounds
            if (arena != null) {
                camStartPos = clampInsideBarrier(arena, camStartPos);
                camEndPos = clampInsideBarrier(arena, camEndPos);
            }

            Location startPose = lookAt(w, camStartPos, startFocus);
            Location endPose = lookAt(w, camEndPos, endFocus);
            startPose.getChunk().load();

            ViewerCamState st = new ViewerCamState();
            st.playerId = viewer.getUniqueId();
            st.savedCourtLocation = viewer.getLocation().clone();
            st.savedGameMode = viewer.getGameMode();
            st.orbitEndPose = endPose;
            st.orbitStarted = false;
            st.totalTicks = safeTotalTicks;
            st.ticksRemaining = safeTotalTicks;
            st.frameCursor = 0;
            st.climaxFrameIndex = clip.size() - 1;
            st.climaxTriggered = false;
            st.startedAtMillis = System.currentTimeMillis();
            st.recordedFrames = clip;
            st.arena = arena;
            st.northHoopScored = isNorthHoop;
            st.slamDunk = slamDunk;
            st.scorerName = sName;
            st.playTag = playLabel;

            // Hide live court ball during replay so viewer only sees the recorded replay ball
            if (liveBall != null && liveBall.visualDisplayEntity() != null && liveBall.visualDisplayEntity().isValid()) {
                st.hiddenLiveBall = liveBall.visualDisplayEntity();
                viewer.hideEntity(plugin, st.hiddenLiveBall);
            }

            // 1. Spawn Replay 3D Basketball at frame 0
            Location ballLoc0 = firstFrame.ballPos().toLocation(w);
            BallSkin bSkin = firstFrame.ballSkin() != null ? firstFrame.ballSkin() : BallSkin.CLASSIC;
            st.replayBall = w.spawn(ballLoc0, ItemDisplay.class, disp -> {
                disp.setPersistent(false);
                disp.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                disp.setBillboard(Display.Billboard.FIXED);
                disp.setTeleportDuration(1);
                disp.setInterpolationDuration(1);
                disp.setBrightness(new Display.Brightness(15, 15));
                disp.setItemStack(bSkin.createBallItem(true));
                disp.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(firstFrame.ballRot()),
                    new Vector3f(REPLAY_BALL_SCALE, REPLAY_BALL_SCALE, REPLAY_BALL_SCALE),
                    new Quaternionf()
                ));
            });
            hideFromAllExcept(viewer, st.replayBall);
            st.spawnedActors.add(st.replayBall);

            // 2. Spawn Mannequin actors for every player captured in the replay
            UUID scorerUuid = scorer != null ? scorer.getUniqueId() : null;
            for (ActorSnapshot snap : firstFrame.actors()) {
                Location aLoc = new Location(w, snap.x(), snap.y(), snap.z(), snap.yaw(), snap.pitch());
                boolean isScorerActor = scorerUuid != null && scorerUuid.equals(snap.playerId());
                ResolvableProfile skinProf = skinCache.computeIfAbsent(
                    snap.playerId(),
                    id -> ResolvableProfile.resolvableProfile().name(snap.name()).build()
                );

                Mannequin m = w.spawn(aLoc, Mannequin.class, man -> {
                    man.setPersistent(false);
                    man.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                    man.setImmovable(false);
                    man.setGravity(false);
                    man.setInvulnerable(true);
                    man.setSilent(true);
                    man.setCollidable(false);
                    try {
                        man.setSkinParts(SkinParts.allParts());
                    } catch (Throwable ignored) {
                    }
                    man.setProfile(skinProf);
                    TextColor c = snap.side() == TeamSide.FROST ? TextColor.color(0x38BCFF) : TextColor.color(0xFF4C4C);
                    man.customName(Component.text((isScorerActor ? "★ " : "") + snap.name(), Style.style(c, TextDecoration.BOLD)));
                    man.setCustomNameVisible(true);
                    if (isScorerActor) {
                        man.setDescription(Component.text("⏪ REPLAY", TextColor.color(0xFFD43B)));
                    }
                    if (man.getEquipment() != null) {
                        man.getEquipment().setItem(EquipmentSlot.FEET, BallSkin.createCustomModelItem(
                            Material.LEATHER_BOOTS, "sneakers_air", "<gold>Air Bosket</gold>", List.of()
                        ));
                    }
                    if (snap.sneaking()) {
                        man.setPose(Pose.SNEAKING);
                    }
                });
                hideFromAllExcept(viewer, m);
                st.actorMannequins.put(snap.playerId(), m);
                st.spawnedActors.add(m);
            }

            // Also ensure any player who entered mid-clip in lastFrame has an actor
            for (ActorSnapshot snap : lastFrame.actors()) {
                if (!st.actorMannequins.containsKey(snap.playerId())) {
                    Location aLoc = new Location(w, snap.x(), snap.y(), snap.z(), snap.yaw(), snap.pitch());
                    ResolvableProfile skinProf = skinCache.computeIfAbsent(
                        snap.playerId(),
                        id -> ResolvableProfile.resolvableProfile().name(snap.name()).build()
                    );
                    Mannequin m = w.spawn(aLoc, Mannequin.class, man -> {
                        man.setPersistent(false);
                        man.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                        man.setImmovable(false);
                        man.setGravity(false);
                        man.setInvulnerable(true);
                        man.setSilent(true);
                        man.setCollidable(false);
                        man.setProfile(skinProf);
                    });
                    hideFromAllExcept(viewer, m);
                    st.actorMannequins.put(snap.playerId(), m);
                    st.spawnedActors.add(m);
                }
            }

            // 3. Spawn smooth Broadcast Camera Rig
            ItemStack invisible = new ItemStack(Material.PAPER);
            ItemMeta im = invisible.getItemMeta();
            if (im != null) {
                im.setItemModel(new NamespacedKey("bosketball", "ui_invisible"));
                invisible.setItemMeta(im);
            }
            st.cameraRig = w.spawn(startPose, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                d.setBillboard(Display.Billboard.FIXED);
                d.setItemStack(invisible);
                d.setTeleportDuration(Math.min(58, safeTotalTicks));
            });
            hideFromAllExcept(viewer, st.cameraRig);

            viewer.teleport(startPose);
            viewer.setGameMode(GameMode.SPECTATOR);
            viewer.setSpectatorTarget(st.cameraRig);
            activeViewers.put(viewer.getUniqueId(), st);
        }
    }

    /**
     * Fallback static celebration cut when fewer than 6 frames were buffered.
     */
    public void playBasketCelebrationCut(
        Collection<Player> matchPlayers,
        Player scorer,
        TeamSide scoringSide,
        Vector rimPos,
        Vector courtCenter,
        boolean slamDunk,
        int durationTicks
    ) {
        if (matchPlayers == null || matchPlayers.isEmpty() || rimPos == null) {
            return;
        }
        int safeTicks = Math.clamp(durationTicks, 24, 70);

        for (Player viewer : matchPlayers) {
            if (viewer == null || !viewer.isOnline() || viewer.getWorld() == null) {
                continue;
            }
            PlayerProfile vProf = plugin.statsManager().getOrCreate(viewer);
            if (!vProf.settings().cinematicScoreCam()) {
                continue;
            }
            stopWatching(viewer, false);

            World w = viewer.getWorld();
            Vector toCenter = new Vector(courtCenter.getX() - rimPos.getX(), 0.0, courtCenter.getZ() - rimPos.getZ());
            if (toCenter.lengthSquared() < 1e-4) {
                toCenter = new Vector(0.0, 0.0, 1.0);
            } else {
                toCenter.normalize();
            }
            Vector rightVec = new Vector(-toCenter.getZ(), 0.0, toCenter.getX());

            Vector focus = rimPos.clone().add(new Vector(0.0, -0.85, 0.0));
            if (scorer != null && scorer.getWorld().equals(w)) {
                Vector sEye = scorer.getLocation().toVector().add(new Vector(0.0, 1.35, 0.0));
                focus = rimPos.clone().multiply(0.55).add(sEye.multiply(0.45));
            }

            double dist = slamDunk ? 4.35 : 5.15;
            Vector camStartPos = rimPos.clone()
                .add(toCenter.clone().multiply(dist))
                .add(rightVec.clone().multiply(-2.15))
                .add(new Vector(0.0, slamDunk ? -0.55 : -0.35, 0.0));
            Vector camEndPos = rimPos.clone()
                .add(toCenter.clone().multiply(dist + 0.45))
                .add(rightVec.clone().multiply(2.15))
                .add(new Vector(0.0, 0.25, 0.0));

            Location startPose = lookAt(w, camStartPos, focus);
            Location endPose = lookAt(w, camEndPos, focus);
            startPose.getChunk().load();

            ViewerCamState st = new ViewerCamState();
            st.playerId = viewer.getUniqueId();
            st.savedCourtLocation = viewer.getLocation().clone();
            st.savedGameMode = viewer.getGameMode();
            st.orbitEndPose = endPose;
            st.orbitStarted = false;
            st.totalTicks = safeTicks;
            st.ticksRemaining = safeTicks;
            st.startedAtMillis = System.currentTimeMillis();
            st.scorerName = scorer != null ? scorer.getName() : "Scorer";
            st.playTag = slamDunk ? "🔥 SLAM DUNK" : "🏀 BUCKET";

            for (Player actor : matchPlayers) {
                if (actor == null || !actor.isOnline() || !actor.getWorld().equals(w)) {
                    continue;
                }
                Location actorLoc = activeViewers.containsKey(actor.getUniqueId())
                    ? activeViewers.get(actor.getUniqueId()).savedCourtLocation.clone()
                    : actor.getLocation().clone();
                boolean isScorer = scorer != null && actor.getUniqueId().equals(scorer.getUniqueId());
                if (isScorer) {
                    Vector toCam = camStartPos.clone().subtract(actorLoc.toVector());
                    actorLoc.setYaw((float) Math.toDegrees(Math.atan2(-toCam.getX(), toCam.getZ())));
                    actorLoc.setPitch(-12.0f);
                }

                PlayerProfile aStats = plugin.statsManager().getOrCreate(actor);
                BallSkin aSkin = aStats.selectedBallSkin();
                ResolvableProfile resProf = buildPlayerSkinProfile(actor);

                Mannequin m = w.spawn(actorLoc, Mannequin.class, man -> {
                    man.setPersistent(false);
                    man.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                    man.setImmovable(false);
                    man.setGravity(true);
                    man.setInvulnerable(true);
                    man.setSilent(true);
                    man.setCollidable(false);
                    try {
                        man.setSkinParts(SkinParts.allParts());
                    } catch (Throwable ignored) {
                    }
                    man.setProfile(resProf);
                    TextColor c = scoringSide == TeamSide.FROST ? TextColor.color(0x38BCFF) : TextColor.color(0xFF4C4C);
                    man.customName(Component.text((isScorer ? "★ " : "") + actor.getName(), Style.style(c, TextDecoration.BOLD)));
                    man.setCustomNameVisible(true);
                    if (isScorer) {
                        man.setDescription(Component.text(slamDunk ? "🔥 SLAM DUNK!" : "🏀 BUCKET!", TextColor.color(0xFFD43B)));
                    }
                    if (man.getEquipment() != null) {
                        man.getEquipment().setItem(EquipmentSlot.FEET, BallSkin.createCustomModelItem(
                            Material.LEATHER_BOOTS, "sneakers_air", "<gold>Air Bosket</gold>", List.of()
                        ));
                        if (isScorer) {
                            man.getEquipment().setItem(EquipmentSlot.HAND, aSkin.createMannequinHeldBallItem());
                        }
                    }
                });
                if (isScorer) {
                    m.setVelocity(new Vector(0.0, 0.32, 0.0));
                    m.swingMainHand();
                }
                hideFromAllExcept(viewer, m);
                st.spawnedActors.add(m);
            }

            ItemStack invisible = new ItemStack(Material.PAPER);
            ItemMeta im = invisible.getItemMeta();
            if (im != null) {
                im.setItemModel(new NamespacedKey("bosketball", "ui_invisible"));
                invisible.setItemMeta(im);
            }
            st.cameraRig = w.spawn(startPose, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                d.setBillboard(Display.Billboard.FIXED);
                d.setItemStack(invisible);
                d.setTeleportDuration(Math.min(58, safeTicks));
            });
            hideFromAllExcept(viewer, st.cameraRig);

            viewer.teleport(startPose);
            viewer.setGameMode(GameMode.SPECTATOR);
            viewer.setSpectatorTarget(st.cameraRig);
            activeViewers.put(viewer.getUniqueId(), st);
        }
    }

    public void tickAll() {
        for (UUID id : new ArrayList<>(activeViewers.keySet())) {
            ViewerCamState st = activeViewers.get(id);
            Player p = Bukkit.getPlayer(id);
            if (st == null || p == null || !p.isOnline()) {
                cleanupEntities(st, null);
                activeViewers.remove(id);
                continue;
            }

            if (!st.orbitStarted && st.cameraRig != null && st.cameraRig.isValid()) {
                st.orbitStarted = true;
                st.cameraRig.teleport(st.orbitEndPose);
            }

            if (st.cameraRig != null && st.cameraRig.isValid() && p.getSpectatorTarget() != st.cameraRig) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
                p.setSpectatorTarget(st.cameraRig);
            }

            // Advance frame-by-frame Instant Goal Replay!
            if (!st.recordedFrames.isEmpty()) {
                stepReplayFrame(p, st);
            }

            if (st.ticksRemaining % 5 == 0 && st.scorerName != null) {
                boolean blink = ((st.ticksRemaining / 8) & 1) == 0;
                String recDot = blink ? "<#FF4D4D>●</#FF4D4D>" : "<dark_gray>●</dark_gray>";
                p.sendActionBar(Msg.parse(
                    recDot + " <gold><bold>ɪɴꜱᴛᴀɴᴛ ʀᴇᴘʟᴀʏ</bold></gold> <dark_gray>•</dark_gray> "
                        + "<white><bold>" + st.scorerName + "</bold></white> <#FDE047>(" + st.playTag + ")</#FDE047> "
                        + "<dark_gray>•</dark_gray> <aqua>[ꜱɴᴇᴀᴋ ᴛᴏ ꜱᴋɪᴘ]</aqua>"
                ));
            }

            st.ticksRemaining--;
            if (st.ticksRemaining <= 0) {
                stopWatching(p, true);
            }
        }
    }

    private void stepReplayFrame(Player viewer, ViewerCamState st) {
        World w = viewer.getWorld();
        int idx = Math.min(st.frameCursor, st.recordedFrames.size() - 1);
        ReplayFrame frame = st.recordedFrames.get(idx);

        if (st.frameCursor < st.recordedFrames.size() - 1) {
            st.frameCursor++;
        } else if (st.replayBall != null && st.replayBall.isValid()) {
            // Post-climax gravity drop through the net!
            Location curB = st.replayBall.getLocation();
            double floorMinY = st.arena != null ? st.arena.geometry().floorY() + 0.26 : curB.getY() - 2.0;
            if (curB.getY() > floorMinY) {
                st.replayBall.teleport(curB.add(0.0, -0.22, 0.0));
            }
        }

        if (st.frameCursor <= st.recordedFrames.size() && st.replayBall != null && st.replayBall.isValid()) {
            Location bLoc = frame.ballPos().toLocation(w);
            st.replayBall.teleport(bLoc);
            st.replayBall.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(frame.ballRot()),
                new Vector3f(REPLAY_BALL_SCALE, REPLAY_BALL_SCALE, REPLAY_BALL_SCALE),
                new Quaternionf()
            ));
            if (frame.ballOnFire()) {
                viewer.spawnParticle(Particle.FLAME, bLoc, 2, 0.08, 0.08, 0.08, 0.01);
            } else {
                viewer.spawnParticle(
                    Particle.DUST,
                    bLoc,
                    1, 0.03, 0.03, 0.03,
                    new Particle.DustOptions(Color.fromRGB(255, 196, 82), 0.75f)
                );
            }
        }

        for (ActorSnapshot snap : frame.actors()) {
            Mannequin m = st.actorMannequins.get(snap.playerId());
            if (m == null || !m.isValid()) {
                continue;
            }
            m.teleport(new Location(w, snap.x(), snap.y(), snap.z(), snap.yaw(), snap.pitch()));
            Pose targetPose = snap.sneaking() ? Pose.SNEAKING : Pose.STANDING;
            if (m.getPose() != targetPose) {
                m.setPose(targetPose);
            }
            if (snap.swingMainHand()) {
                m.swingMainHand();
            }
        }

        // Trigger replay climax swish/dunk sound & net ripple right at the scoring frame!
        if (!st.climaxTriggered && idx >= st.climaxFrameIndex) {
            st.climaxTriggered = true;
            if (st.arena != null) {
                HoopDisplaySet hoop = st.northHoopScored ? st.arena.northHoopDisplay() : st.arena.southHoopDisplay();
                if (hoop != null) {
                    if (st.slamDunk) {
                        hoop.triggerDunkFlexAnimation();
                    } else {
                            hoop.triggerSwishNetAnimation();
                    }
                }
            }
            Msg.playPersonalPackSound(viewer, st.slamDunk ? "game.dunk_boom" : "ball.swish", 1.0f, 1.0f);
            if (st.replayBall != null && st.replayBall.isValid()) {
                viewer.spawnParticle(Particle.FIREWORK, st.replayBall.getLocation(), 16, 0.25, 0.25, 0.25, 0.12);
            }
        }
    }

    public boolean handleSpectateStopAttempt(Player player) {
        ViewerCamState st = activeViewers.get(player.getUniqueId());
        if (st == null) {
            return false;
        }
        if (System.currentTimeMillis() - st.startedAtMillis < 250L) {
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> stopWatching(player, true));
        return true;
    }

    public boolean skipIfWatching(Player player) {
        if (player == null || !isWatching(player.getUniqueId())) {
            return false;
        }
        return handleSpectateStopAttempt(player);
    }

    public void stopWatching(Player player, boolean restorePose) {
        if (player == null) {
            return;
        }
        ViewerCamState st = activeViewers.remove(player.getUniqueId());
        if (st == null) {
            return;
        }
        if (player.isOnline()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
            if (restorePose) {
                GameMode targetGm = (st.savedGameMode != null && st.savedGameMode != GameMode.SPECTATOR)
                    ? st.savedGameMode
                    : GameMode.ADVENTURE;
                player.setGameMode(targetGm);
                if (st.savedCourtLocation != null && st.savedCourtLocation.getWorld() != null) {
                    player.teleport(st.savedCourtLocation);
                }
            }
        }
        cleanupEntities(st, player);
    }

    public void stopAll() {
        for (UUID id : new ArrayList<>(activeViewers.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                stopWatching(p, true);
            } else {
                cleanupEntities(activeViewers.remove(id), null);
            }
        }
    }

    public void hideActiveFromLateJoiner(Player joiner) {
        for (Map.Entry<UUID, ViewerCamState> e : activeViewers.entrySet()) {
            if (e.getKey().equals(joiner.getUniqueId())) {
                continue;
            }
            ViewerCamState st = e.getValue();
            if (st.cameraRig != null && st.cameraRig.isValid()) {
                joiner.hideEntity(plugin, st.cameraRig);
            }
            for (Entity a : st.spawnedActors) {
                if (a != null && a.isValid()) {
                    joiner.hideEntity(plugin, a);
                }
            }
        }
    }

    private void cleanupEntities(ViewerCamState st, Player viewerOrNull) {
        if (st == null) {
            return;
        }
        if (viewerOrNull != null && viewerOrNull.isOnline() && st.hiddenLiveBall != null && st.hiddenLiveBall.isValid()) {
            viewerOrNull.showEntity(plugin, st.hiddenLiveBall);
        }
        if (st.cameraRig != null && st.cameraRig.isValid()) {
            st.cameraRig.remove();
        }
        for (Entity e : st.spawnedActors) {
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        st.spawnedActors.clear();
        st.actorMannequins.clear();
    }

    private void hideFromAllExcept(Player viewer, Entity entity) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                other.hideEntity(plugin, entity);
            }
        }
    }

    private static Vector clampInsideBarrier(Arena arena, Vector pos) {
        var g = arena.geometry();
        return new Vector(
            Math.clamp(pos.getX(), g.barrierMinX() + 0.9, g.barrierMaxX() - 0.9),
            Math.clamp(pos.getY(), g.floorY() + 1.1, g.ceilingY() - 1.2),
            Math.clamp(pos.getZ(), g.barrierMinZ() + 0.9, g.barrierMaxZ() - 0.9)
        );
    }

    private static ResolvableProfile buildPlayerSkinProfile(Player p) {
        var paperProf = p.getPlayerProfile();
        if (!paperProf.getProperties().isEmpty()) {
            return ResolvableProfile.resolvableProfile(paperProf);
        }
        return ResolvableProfile.resolvableProfile().name(p.getName()).build();
    }

    private static Location lookAt(World w, Vector eye, Vector target) {
        Vector d = target.clone().subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float pitch = (float) Math.clamp(-Math.toDegrees(Math.atan2(d.getY(), Math.hypot(d.getX(), d.getZ()))), -85.0, 85.0);
        return new Location(w, eye.getX(), eye.getY(), eye.getZ(), yaw, pitch);
    }
}
