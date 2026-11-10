package fr.bosketball.ui;

import fr.bosketball.util.Palette;
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

public final class CinematicCameraManager {

    private static final float REPLAY_BALL_SCALE = 0.56f;
    /**
     * The replay ball is a display of its own, and it gets the same culling box as the live one.
     * Without it the box is a single point at the ball's centre, and the camera in a replay is
     * moving: the point leaves the frame long before the ball does, and the shot the replay exists
     * to show has no ball in it.
     */
    private static final float REPLAY_BALL_CULL_SIZE = 1.10f;
    /** Far enough that a replay camera on the far stand still gets the ball drawn. */
    private static final float REPLAY_BALL_VIEW_RANGE = 3.0f;

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
        Location savedCourtLocation;
        GameMode savedGameMode;
        boolean savedAllowFlight;
        boolean savedFlying;
        ItemDisplay cameraRig;
        Location orbitEndPose;
        boolean orbitStarted;
        int ticksRemaining;
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
        BasketballEntity liveBallRef;
        ItemDisplay replayBall;
        final Map<UUID, Mannequin> actorMannequins = new HashMap<>();
        List<ReplayFrame> recordedFrames = List.of();
        final List<Entity> spawnedActors = new ArrayList<>();
    }

    private final BosketBallPlugin plugin;
    private final Map<UUID, ViewerCamState> activeViewers = new ConcurrentHashMap<>();
    private boolean replayFailureLogged;

    public CinematicCameraManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isWatching(UUID playerId) {
        return playerId != null && activeViewers.containsKey(playerId);
    }

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
        if (frames == null || frames.size() < 2) {
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
        String playLabel = slamDunk ? " SLAM DUNK" : (threePointer ? " 3PT SPLASH" : " BUCKET");

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

            Vector startFocus = firstFrame.ballPos().clone().multiply(0.55).add(rimPos.clone().multiply(0.45));
            Vector endFocus = rimPos.clone().add(new Vector(0.0, -0.55, 0.0));

            // Wide enough to read the court, not just the rim: the old rig sat six blocks from the
            // iron and filled the frame with backboard, so the play that produced the bucket was
            // never visible in the replay.
            double camDist = threePointer ? 11.0 : (slamDunk ? 8.6 : 9.6);
            Vector camStartPos = rimPos.clone()
                .add(toCenter.clone().multiply(camDist))
                .add(rightVec.clone().multiply(threePointer ? -4.6 : -3.6))
                .add(new Vector(0.0, 2.55, 0.0));
            Vector camEndPos = rimPos.clone()
                .add(toCenter.clone().multiply(Math.max(7.4, camDist - 1.6)))
                .add(rightVec.clone().multiply(3.2))
                .add(new Vector(0.0, 1.95, 0.0));

            if (arena != null) {
                camStartPos = clampInsideBarrier(arena, camStartPos);
                camEndPos = clampInsideBarrier(arena, camEndPos);
            }

            Location startPose = lookAt(w, camStartPos, startFocus);
            Location endPose = lookAt(w, camEndPos, endFocus);
            startPose.getChunk().load();

            ViewerCamState st = new ViewerCamState();
            st.savedCourtLocation = viewer.getLocation().clone();
            st.savedGameMode = viewer.getGameMode();
            st.savedAllowFlight = viewer.getAllowFlight();
            st.savedFlying = viewer.isFlying();
            st.orbitEndPose = endPose;
            st.orbitStarted = false;
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

            st.liveBallRef = liveBall;
            if (liveBall != null && liveBall.visualDisplayEntity() != null && liveBall.visualDisplayEntity().isValid()) {
                st.hiddenLiveBall = liveBall.visualDisplayEntity();
                viewer.hideEntity(plugin, st.hiddenLiveBall);
            }

            // Registered before the first spawn. Everything below creates entities, builds
            // items and teleports a player; any of that failing used to leave the viewer's live
            // ball hidden with no state on any clock to give it back - a ball that vanished at
            // exactly the moment a basket went in, and stayed gone.
            activeViewers.put(viewer.getUniqueId(), st);
            try {
                Location ballLoc0 = firstFrame.ballPos().toLocation(w);
                BallSkin bSkin = firstFrame.ballSkin() != null ? firstFrame.ballSkin() : BallSkin.CLASSIC;
                st.replayBall = w.spawn(ballLoc0, ItemDisplay.class, disp -> {
                    disp.setPersistent(false);
                    disp.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                    disp.setBillboard(Display.Billboard.FIXED);
                    // The live ball is drawn as a three-dimensional item; the default transform is the
                    // flat one an item frame uses, which would make the replay ball a sticker of a ball.
                    disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    disp.setTeleportDuration(1);
                    disp.setInterpolationDuration(1);
                    disp.setBrightness(new Display.Brightness(15, 15));
                    disp.setViewRange(REPLAY_BALL_VIEW_RANGE);
                    disp.setDisplayWidth(REPLAY_BALL_CULL_SIZE);
                    disp.setDisplayHeight(REPLAY_BALL_CULL_SIZE);
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
                        man.customName(Component.text((isScorerActor ? "▪ " : "") + snap.name(), Style.style(c, TextDecoration.BOLD)));
                        man.setCustomNameVisible(true);
                        if (isScorerActor) {
                            man.setDescription(Component.text("⏪ REPLAY", TextColor.color(0xFFD43B)));
                        }
                        if (man.getEquipment() != null) {
                            man.getEquipment().setItem(EquipmentSlot.FEET, BallSkin.createCustomModelItem(
                                Material.LEATHER_BOOTS, "sneakers_air", "<#FBBF24>Air Bosket</#FBBF24>", List.of()
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
            } catch (Throwable failure) {
                // Restores the pose, the game mode, and the ball: a viewer is never left inside a
                // replay that never started, staring at a court with no ball on it.
                warnReplayFailureOnce(failure);
                stopWatching(viewer, true);
            }
        }
    }

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

            // Same framing rule as the celebration cut: step back and lift the rig so the whole
            // half-court action is in frame instead of a close-up of the iron.
            double dist = slamDunk ? 7.8 : 8.8;
            Vector camStartPos = rimPos.clone()
                .add(toCenter.clone().multiply(dist))
                .add(rightVec.clone().multiply(-3.1))
                .add(new Vector(0.0, slamDunk ? 1.35 : 1.75, 0.0));
            Vector camEndPos = rimPos.clone()
                .add(toCenter.clone().multiply(dist + 0.9))
                .add(rightVec.clone().multiply(3.1))
                .add(new Vector(0.0, 2.15, 0.0));

            Location startPose = lookAt(w, camStartPos, focus);
            Location endPose = lookAt(w, camEndPos, focus);
            startPose.getChunk().load();

            ViewerCamState st = new ViewerCamState();
            st.savedCourtLocation = viewer.getLocation().clone();
            st.savedGameMode = viewer.getGameMode();
            st.savedAllowFlight = viewer.getAllowFlight();
            st.savedFlying = viewer.isFlying();
            st.orbitEndPose = endPose;
            st.orbitStarted = false;
            st.ticksRemaining = safeTicks;
            st.startedAtMillis = System.currentTimeMillis();
            st.scorerName = scorer != null ? scorer.getName() : "Scorer";
            st.playTag = slamDunk ? " SLAM DUNK" : " BUCKET";

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
                    man.customName(Component.text((isScorer ? "▪ " : "") + actor.getName(), Style.style(c, TextDecoration.BOLD)));
                    man.setCustomNameVisible(true);
                    if (isScorer) {
                        man.setDescription(Component.text(slamDunk ? " SLAM DUNK!" : " BUCKET!", TextColor.color(0xFFD43B)));
                    }
                    if (man.getEquipment() != null) {
                        man.getEquipment().setItem(EquipmentSlot.FEET, BallSkin.createCustomModelItem(
                            Material.LEATHER_BOOTS, "sneakers_air", "<#FBBF24>Air Bosket</#FBBF24>", List.of()
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

            if (!st.recordedFrames.isEmpty()) {
                stepReplayFrame(p, st);
            }

            if (st.ticksRemaining % 10 == 0 && st.scorerName != null) {
                boolean blink = ((st.ticksRemaining / 20) & 1) == 0;
                String recDot = blink ? "<" + Palette.DANGER + ">●</" + Palette.DANGER + ">" : "<#64748B>●</#64748B>";
                Msg.rail(p, fr.bosketball.util.HudSurface.CINEMATIC, recDot + " <" + Palette.GOLD
                    + "><bold>Replay</bold></" + Palette.GOLD + ">" + fr.bosketball.util.HudCopy.SEP
                    + "<" + Palette.TEXT + "><bold>" + fr.bosketball.util.HudCopy.name(st.scorerName)
                    + "</bold></" + Palette.TEXT + ">" + fr.bosketball.util.HudCopy.SEP
                    + "<" + Palette.GOLD_SOFT + ">" + st.playTag + "</" + Palette.GOLD_SOFT + ">"
                    + fr.bosketball.util.HudCopy.SEP + "<" + Palette.ICE + ">sneak to skip</"
                    + Palette.ICE + ">");
            }

            st.ticksRemaining--;
            if (st.ticksRemaining <= 0) {
                stopWatching(p, true);
            }
        }
    }

    private void stepReplayFrame(Player viewer, ViewerCamState st) {
        World w = viewer.getWorld();
        keepLiveBallHidden(viewer, st);
        int lastFrame = st.recordedFrames.size() - 1;
        int idx = Math.min(st.frameCursor, lastFrame);
        ReplayFrame frame = st.recordedFrames.get(idx);
        // Read before the cursor moves: this is the one question the two blocks below disagree
        // about, and reading it twice from a cursor that has already been stepped is how the ball
        // ended up frozen mid-air at the end of every replay.
        boolean clipHasFramesLeft = st.frameCursor < lastFrame;

        if (clipHasFramesLeft) {
            st.frameCursor++;
        }

        // Only a clip with frames left puts the ball on the recording. Once it runs out the ball
        // falls and comes to rest - the drop below used to be undone by the teleport three lines
        // later, on the same tick, with the trail still being painted where the ball no longer was.
        if (clipHasFramesLeft && st.replayBall != null && st.replayBall.isValid()) {
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
        } else if (st.replayBall != null && st.replayBall.isValid()) {
            Location curB = st.replayBall.getLocation();
            double floorMinY = st.arena != null ? st.arena.geometry().floorY() + 0.26 : curB.getY() - 2.0;
            if (curB.getY() > floorMinY) {
                st.replayBall.teleport(curB.add(0.0, -0.22, 0.0));
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
                if (st.savedAllowFlight || targetGm == GameMode.CREATIVE) {
                    player.setAllowFlight(true);
                    player.setFlying(st.savedFlying);
                }
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

    /**
     * One line the first time a replay cannot be staged. This is the one failure in the cinematic
     * path that costs a player their ball, so it is the one that must never be silent.
     */
    private void warnReplayFailureOnce(Throwable failure) {
        if (replayFailureLogged) {
            return;
        }
        replayFailureLogged = true;
        try {
            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().warning("[BosketBall] The goal replay could not be started, so the"
                    + " player keeps the normal view and their ball: " + failure);
            }
        } catch (RuntimeException ignored) {
            // A logger must never be the thing that fails a cleanup.
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

    /**
     * A replay borrows the ball's place on the screen: the real ball must not be drawn in it.
     *
     * <p>The live ball re-declares itself visible on a timer, because a ball nobody can see is a
     * much worse bug than a ball drawn twice, and a rebuild gives it a new entity besides. So the
     * hide is not a one-off at the start of the cinematic any more: every frame of the replay says
     * "not this ball" again, against whatever display the ball has right now.
     */
    private void keepLiveBallHidden(Player viewer, ViewerCamState st) {
        if (st.liveBallRef != null) {
            ItemDisplay current = st.liveBallRef.visualDisplayEntity();
            if (current != null && current.isValid()
                && (st.hiddenLiveBall == null
                    || !st.hiddenLiveBall.getUniqueId().equals(current.getUniqueId()))) {
                st.hiddenLiveBall = current;
            }
        }
        if (st.hiddenLiveBall != null && st.hiddenLiveBall.isValid()) {
            viewer.hideEntity(plugin, st.hiddenLiveBall);
        }
    }

    private void cleanupEntities(ViewerCamState st, Player viewerOrNull) {
        if (st == null) {
            return;
        }
        // Give back the display the ball has now, which is not necessarily the one the cinematic
        // started with: every launch rebuilds it.
        ItemDisplay toShow = st.hiddenLiveBall;
        if (st.liveBallRef != null) {
            ItemDisplay current = st.liveBallRef.visualDisplayEntity();
            if (current != null && current.isValid()) {
                toShow = current;
            }
        }
        if (viewerOrNull != null && viewerOrNull.isOnline() && toShow != null && toShow.isValid()) {
            viewerOrNull.showEntity(plugin, toShow);
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
        if (arena == null || pos == null) {
            return new Vector(0.0, 0.0, 0.0);
        }
        var g = arena.geometry();
        // Round 42: Math.clamp throws as soon as min > max. A court narrower than 1.8 blocks, or a
        // schematic whose floor sits less than 2.3 blocks under its ceiling, made the cinematic
        // camera throw EVERY TICK — thousands of stack traces per second, from a scheduler task,
        // with no way out but a restart. Guards.clampLoHi keeps the camera at the middle instead.
        return new Vector(
            fr.bosketball.util.Guards.clampLoHi(pos.getX(), g.barrierMinX() + 0.9, g.barrierMaxX() - 0.9),
            fr.bosketball.util.Guards.clampLoHi(pos.getY(), g.floorY() + 1.1, g.ceilingY() - 1.2),
            fr.bosketball.util.Guards.clampLoHi(pos.getZ(), g.barrierMinZ() + 0.9, g.barrierMaxZ() - 0.9)
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
        if (w == null || eye == null || target == null) {
            return new Location(w, 0.0, 0.0, 0.0);
        }
        Vector d = target.clone().subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float pitch = (float) Math.clamp(-Math.toDegrees(Math.atan2(d.getY(), Math.hypot(d.getX(), d.getZ()))), -85.0, 85.0);
        return new Location(w, eye.getX(), eye.getY(), eye.getZ(), yaw, pitch);
    }
}
