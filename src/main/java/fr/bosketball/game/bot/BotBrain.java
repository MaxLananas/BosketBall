package fr.bosketball.game.bot;

import fr.bosketball.arena.Arena;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.util.Msg;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class BotBrain {

    public record PassTarget(java.util.UUID id, Vector position) { }

    public interface Handler {

        boolean botTryPickup(BotAvatar avatar);

        void botRespawned(BotAvatar avatar, java.util.UUID previousId, fr.bosketball.model.TeamSide team);

        void botShoot(BotAvatar avatar, Vector targetRim, double charge, Vector direction);

        void botDunk(BotAvatar avatar, Vector targetRim);

        void botPass(BotAvatar avatar, java.util.UUID targetId);

        void botDeflect(BotAvatar avatar, Vector direction);

        List<BotAvatar> botAllies(BotAvatar avatar);

        List<PassTarget> botPassTargets(BotAvatar avatar);

        Vector botOffensiveRim(BotAvatar avatar);

        Vector botDefensiveRim(BotAvatar avatar);

        BasketballEntity botBall();

        Player botHumanCarrier();

        fr.bosketball.model.TeamSide botTeam(BotAvatar avatar);

        /** True when the shot clock is about to expire, so a heave beats a turnover. */
        default boolean botShotClockPressure(BotAvatar avatar) {
            return false;
        }
    }

    private enum State {
        OFF_BALL,
        SPOT_UP,
        CUT,
        DRIVE,
        SHOOT,
        DEFEND,
        LOOSE_BALL,
        CELEBRATE
    }

    private final Arena arena;
    private final BotAvatar avatar;
    private final Handler host;
    private final BotChatter chatter;

    private State state = State.OFF_BALL;
    private Vector anchor;
    private int decisionTimer;
    private int actionCooldown;
    private int chatterCooldown = 120;
    private boolean ballHandler;
    private boolean hadBall;
    private final BotReaction possessionReaction = new BotReaction();
    private int shotTicks;
    private int plannedRelease;
    private Vector shotFootwork = new Vector();
    private final BotReaction ballReaction = new BotReaction();
    private final BotReaction heldBallReaction = new BotReaction();
    private final BotReaction carrierReaction = new BotReaction();
    private boolean ballReady;
    private boolean heldBallReady;
    private java.util.UUID seenBallHolder;
    private boolean carrierReady;
    private java.util.UUID seenCarrier;
    private final BotReaction possessionAwareness = new BotReaction();
    private java.util.UUID observedPossessor;
    private Boolean knownTeamPossession;
    private final java.util.Map<java.util.UUID, BotReaction> playerReactions = new java.util.HashMap<>();
    private final java.util.Set<java.util.UUID> visiblePlayers = new java.util.HashSet<>();

    public BotBrain(Arena arena, BotAvatar avatar, Handler host, BotChatter chatter) {
        this.arena = arena;
        this.avatar = avatar;
        this.host = host;
        this.chatter = chatter;
        this.chatterCooldown += Math.floorMod(avatar.profile().nickname().hashCode(), 100);
        this.anchor = avatar.position();
    }

    public BotAvatar avatar() {
        return avatar;
    }

    public State state() {
        return state;
    }

    private static final double STUCK_EPSILON = 0.02;
    private static final int STUCK_LIMIT = 45;
    private final BotOscillation oscillation = new BotOscillation();
    private Vector lastSeenPosition;
    private int stuckTicks;
    private Vector recoveryTarget;
    private int recoveryTicks;

    /** Check for lack of movement only while the bot is trying to travel. */
    private void tickStuckWatchdog() {
        Vector now = avatar.position();
        Vector target = avatar.movementTarget();
        if (target == null || horizontal(now, target) < 0.5) {
            stuckTicks = 0;
        } else if (lastSeenPosition != null
            && now.distanceSquared(lastSeenPosition) < STUCK_EPSILON * STUCK_EPSILON) {
            if (++stuckTicks >= STUCK_LIMIT) {
                stuckTicks = 0;
                startRecovery();
            }
        } else {
            stuckTicks = 0;
            lastSeenPosition = now.clone();
        }
        if (lastSeenPosition == null) {
            lastSeenPosition = now.clone();
        }
    }

    /** Try a short clear court-level route; never teleport through scenery. */
    private void startRecovery() {
        var location = avatar.location();
        if (location == null) {
            return;
        }
        Vector here = avatar.position();
        Vector candidate = BotRecovery.choose(here, avatar.profile().nickname().hashCode(), this::clampCourt,
            target -> recoveryPathClear(location, here, target));
        if (candidate != null) {
            recoveryTarget = candidate;
            recoveryTicks = 24;
            ballReaction.reset();
            heldBallReaction.reset();
            heldBallReady = false;
            seenBallHolder = null;
            carrierReaction.reset();
            possessionAwareness.reset();
            playerReactions.clear();
            visiblePlayers.clear();
            observedPossessor = null;
            ballReady = false;
            carrierReady = false;
            seenCarrier = null;
            oscillation.reset();
        }
    }

    private boolean recoveryPathClear(org.bukkit.Location location, Vector here, Vector candidate) {
        Vector offset = candidate.clone().subtract(here).setY(0);
        double length = offset.length();
        Vector direction = offset.multiply(1.0 / length);
        Vector right = new Vector(-direction.getZ(), 0, direction.getX());
        for (double side : new double[]{-0.3, 0, 0.3}) {
            for (double height : new double[]{0.4, 1.4}) {
                var origin = location.clone().add(right.clone().multiply(side)).add(0, height, 0);
                if (location.getWorld().rayTraceBlocks(origin, direction, length,
                    org.bukkit.FluidCollisionMode.NEVER, true) != null) return false;
            }
        }
        return true;
    }

    public void tick() {
        BotDifficulty diff = avatar.profile().difficulty();
        avatar.clampToCourt();
        avatar.tickNameplate();
        if (avatar.location() == null) {
            return;
        }
        tickStuckWatchdog();
        if (decisionTimer > 0) {
            decisionTimer--;
        }
        if (actionCooldown > 0) {
            actionCooldown--;
        }
        if (chatterCooldown > 0) {
            chatterCooldown--;
        }

        updateVisiblePlayers(diff);
        BasketballEntity ball = host.botBall();
        ballHandler = ball != null && ball.mode() == BasketballEntity.BallMode.DRIBBLING
            && ball.holderId() != null
            && ball.holderId().equals(avatar.entityId());

        if (ballHandler && !hadBall) {
            state = State.OFF_BALL;
            decisionTimer = 0;
            shotTicks = 0;
            knownTeamPossession = true;
        }
        hadBall = ballHandler;
        boolean possessionReady = possessionReaction.observe(ballHandler, diff.reactionTicks());
        if (ballHandler && !possessionReady) {
            avatar.steerTo(avatar.position(), false, host.botOffensiveRim(avatar), decisionTimer);
            return;
        }

        boolean seesLooseBall = ball != null && ball.holderId() == null
            && ball.mode() != BasketballEntity.BallMode.IN_SHOT_FLIGHT
            && ball.mode() != BasketballEntity.BallMode.IN_DUNK_FLIGHT
            && BotVision.canSee(avatar.mannequin().getEyeLocation(), ball.position());
        boolean wasBallReady = ballReady;
        ballReady = ballReaction.observe(seesLooseBall, diff.reactionTicks());
        java.util.UUID currentHolder = ball == null ? null : ball.holderId();
        if (!java.util.Objects.equals(currentHolder, seenBallHolder)) {
            heldBallReaction.reset();
            seenBallHolder = currentHolder;
        }
        heldBallReady = heldBallReaction.observe(currentHolder != null && !ballHandler
            && BotVision.canSee(avatar.mannequin().getEyeLocation(), ball.position()), diff.reactionTicks());
        Player seen = host.botHumanCarrier();
        BotAvatar seenBot = ball == null ? null : botCarrier(ball.holderId());
        java.util.UUID observedId = null;
        if (seen != null && avatar.teamOf(seen) != null
            && avatar.teamOf(seen) != host.botTeam(avatar) && seen.getWorld().equals(avatar.location().getWorld())
            && BotVision.canSee(avatar.mannequin().getEyeLocation(), seen.getEyeLocation().toVector())) {
            observedId = seen.getUniqueId();
        } else if (seenBot != null && host.botTeam(seenBot) != host.botTeam(avatar)
            && BotVision.canSee(avatar.mannequin().getEyeLocation(), seenBot.mannequin().getEyeLocation().toVector())) {
            observedId = seenBot.entityId();
        }
        java.util.UUID visibleHolder = null;
        fr.bosketball.model.TeamSide holderTeam = null;
        if (seen != null && avatar.teamOf(seen) != null && seen.getWorld().equals(avatar.location().getWorld())
            && BotVision.canSee(avatar.mannequin().getEyeLocation(), seen.getEyeLocation().toVector())) {
            visibleHolder = seen.getUniqueId();
            holderTeam = avatar.teamOf(seen);
        } else if (seenBot != null && seenBot != avatar && host.botTeam(seenBot) != null
            && BotVision.canSee(avatar.mannequin().getEyeLocation(), seenBot.mannequin().getEyeLocation().toVector())) {
            visibleHolder = seenBot.entityId();
            holderTeam = host.botTeam(seenBot);
        }
        if (visibleHolder == null || !visibleHolder.equals(observedPossessor)) {
            possessionAwareness.reset();
            observedPossessor = visibleHolder;
        }
        boolean possessionObserved = possessionAwareness.observe(visibleHolder != null, diff.reactionTicks());
        boolean newlyRecognizedPossession = false;
        if (possessionObserved) {
            boolean friendly = holderTeam == host.botTeam(avatar);
            newlyRecognizedPossession = knownTeamPossession == null || friendly != knownTeamPossession;
            if (newlyRecognizedPossession) decisionTimer = 0;
            knownTeamPossession = friendly;
        }

        boolean wasCarrierReady = carrierReady;
        if (observedId == null || !observedId.equals(seenCarrier)) {
            carrierReaction.reset();
            wasCarrierReady = false;
            seenCarrier = observedId;
        }
        carrierReady = carrierReaction.observe(observedId != null, diff.reactionTicks());
        // Newly perceived events must not wait through a second decision interval.
        if (!ballHandler && ((!wasBallReady && ballReady) || (!wasCarrierReady && carrierReady))) {
            decisionTimer = 0;
        }

        if (recoveryTicks > 0 && recoveryTarget != null) {
            // A visible play or possession interrupts the detour; recovery is not blindness.
            if (ballHandler || newlyRecognizedPossession || (!wasBallReady && ballReady)
                || (!wasCarrierReady && carrierReady)) {
                recoveryTicks = 0;
            } else {
                recoveryTicks--;
                avatar.steerTo(recoveryTarget, false, recoveryTarget, recoveryTicks);
                if (horizontal(avatar.position(), recoveryTarget) < 0.4) recoveryTicks = 0;
                return;
            }
        }

        if (decisionTimer <= 0) {
            decisionTimer = Math.max(1, diff.reactionTicks());
            decide(ball);
            if (!ballHandler && (state == State.OFF_BALL || state == State.SPOT_UP || state == State.CUT)) {
                if (oscillation.observe(state.name(), avatar.position())) {
                    startRecovery();
                    if (recoveryTicks > 0) return;
                }
            } else {
                oscillation.reset();
            }
        }

        execute(ball, diff);
    }

    private void decide(BasketballEntity ball) {
        BotDifficulty diff = avatar.profile().difficulty();
        Vector rim = host.botOffensiveRim(avatar);
        Vector myPos = avatar.position();

        if (!ballHandler && ballReady && ball != null && ball.holderId() == null) {
            double dist = horizontal(ball.position(), myPos);
            state = dist < 7.0 ? State.LOOSE_BALL : State.OFF_BALL;
            return;
        }
        if (!ballHandler && knownTeamPossession == null) {
            state = State.OFF_BALL;
            return;
        }
        if (!ballHandler && !knownTeamPossession) {
            state = State.DEFEND;
            return;
        }
        if (ballHandler) {

            double space = nearestDefenderDistance(myPos);
            double rimDist = horizontal(rim, myPos);
            PassTarget recipient = null;
            double closest = Double.MAX_VALUE;
            for (PassTarget target : host.botPassTargets(avatar)) {
                double distance = horizontal(target.position(), myPos);
                if (distance < closest && visiblePlayers.contains(target.id())
                    && BotVision.canSee(avatar.mannequin().getEyeLocation(),
                        target.position().clone().add(new Vector(0, 1, 0)))) {
                    recipient = target;
                    closest = distance;
                }
            }
            boolean canPass = recipient != null;
            BotTactics.Style style = BotTactics.style(diff);
            BotTactics.Action action = BotTactics.choose(style, avatar.profile().shootBias(),
                avatar.profile().passBias(), avatar.profile().driveBias(), space, rimDist, canPass,
                host.botShotClockPressure(avatar));
            decisionTimer = Math.max(decisionTimer, style.commitmentTicks());
            switch (action) {
                case SHOOT -> prepareShot(space);
                case PASS -> {
                    actionCooldown = 0;
                    state = State.OFF_BALL;
                    host.botPass(avatar, recipient.id());
                    avatar.swingArm(true);
                }
                case DRIVE -> state = State.DRIVE;
            }
            return;
        }

        double rimDist = horizontal(rim, myPos);
        State next;
        if (rimDist > 9.0 && avatar.profile().shootBias() >= avatar.profile().driveBias()) {
            next = State.SPOT_UP;
        } else if (rimDist > 4.0 && avatar.profile().driveBias() > avatar.profile().passBias()) {
            next = State.CUT;
        } else {
            next = State.OFF_BALL;
        }
        if (state != next) {
            anchor = switch (next) {
                case SPOT_UP -> spotUpPoint(rim);
                case CUT -> cutPoint(rim);
                default -> spacingPoint(rim);
            };
            state = next;
        }
        decisionTimer = Math.max(decisionTimer, BotTactics.style(diff).commitmentTicks() + 15);
    }

    private BotAvatar botCarrier(java.util.UUID holder) {
        if (holder == null) return null;
        for (BotAvatar other : host.botAllies(avatar)) {
            if (other.isValid() && holder.equals(other.entityId())) return other;
        }
        return null;
    }

    private void execute(BasketballEntity ball, BotDifficulty diff) {
        Vector rimDef = host.botDefensiveRim(avatar);
        Vector rimOff = host.botOffensiveRim(avatar);

        switch (state) {
            case DEFEND -> {
                Player human = carrierReady ? host.botHumanCarrier() : null;
                BotAvatar bot = carrierReady && ball != null
                    ? botCarrier(ball.holderId()) : null;
                Vector carrierPos = null;
                Vector carrierEye = null;
                if (human != null && human.getUniqueId().equals(seenCarrier)) {
                    carrierPos = human.getLocation().toVector();
                    carrierEye = human.getEyeLocation().toVector();
                } else if (bot != null && bot.entityId().equals(seenCarrier)) {
                    carrierPos = bot.position();
                    carrierEye = bot.mannequin().getEyeLocation().toVector();
                }
                Vector guardPoint = carrierPos == null ? anchor : guardPointAgainst(carrierPos, rimDef, diff);
                avatar.steerTo(guardPoint, carrierPos != null, carrierEye == null ? rimDef : carrierEye, decisionTimer);
                boolean close = carrierPos != null && horizontal(carrierPos, avatar.position()) < 2.6;
                avatar.crouch(close && diff.contestIntensity() > 0.5);

                if (close && actionCooldown <= 0 && heldBallReady && ball != null
                    && ball.mode() == BasketballEntity.BallMode.DRIBBLING && seenCarrier.equals(ball.holderId())
                    && BotVision.canSee(avatar.mannequin().getEyeLocation(), ball.position())) {
                    actionCooldown = 45 + diff.reactionTicks() * 4;
                    avatar.swingArm(true);
                    double reach = 1.1 + diff.stealSuccess() * 1.2;
                    if (horizontal(carrierPos, avatar.position()) < reach
                        && horizontal(ball.position(), avatar.position()) < 1.7) {
                        host.botDeflect(avatar, rimOff.clone().subtract(avatar.position()).setY(0.2));
                        say(BotChatter.Moment.DEFENSE);
                    }
                }
            }
            case LOOSE_BALL -> {
                if (ball != null && ball.holderId() == null && ballReady) {
                    Vector target = ball.position().clone().setY(arena.geometry().floorY());
                    avatar.steerTo(target, true, ball.position(), decisionTimer);
                    if (horizontal(ball.position(), avatar.position()) < 1.5) {
                        host.botTryPickup(avatar);
                    }
                } else {
                    avatar.steerTo(anchor, false, rimOff, decisionTimer);
                }
            }
            case SPOT_UP -> avatar.steerTo(anchor, false, rimOff, decisionTimer);
            case CUT -> avatar.steerTo(anchor, true, rimOff, decisionTimer);
            case DRIVE -> {
                Vector lane = driveLanePoint(rimOff);
                avatar.steerTo(lane, true, rimOff, decisionTimer);
                double rimDist = horizontal(rimOff, avatar.position());
                if (ballHandler && rimDist < 2.2 && actionCooldown <= 0) {
                    double space = nearestDefenderDistance(avatar.position());
                    if (space < 1.5) {
                        prepareShot(space);
                    } else {
                        actionCooldown = 40;
                        host.botDunk(avatar, rimOff);
                        avatar.swingArm(true);
                        say(BotChatter.Moment.DUNK);
                    }
                }
            }
            case SHOOT -> {
                avatar.steerTo(avatar.position(), false, rimOff, decisionTimer);
                if (ballHandler && actionCooldown <= 0) {
                    shotTicks++;
                    if (shotTicks == plannedRelease - 3) avatar.jump(0.32);
                    if (shotTicks >= plannedRelease) {
                        actionCooldown = 45;
                        Vector direction = BotShot.releaseDirection(avatar.mannequin().getEyeLocation().getDirection(),
                            shotFootwork, shotTicks, avatar.profile().nickname().hashCode());
                        host.botShoot(avatar, rimOff, BotShot.charge(shotTicks), direction);
                        avatar.swingArm(true);
                        say(BotChatter.Moment.SHOT);
                    }
                }
            }
            case CELEBRATE -> avatar.celebrate();
            default -> avatar.steerTo(anchor, false, rimOff, decisionTimer);
        }
    }

    private void prepareShot(double space) {
        state = State.SHOOT;
        shotTicks = 0;
        Vector velocity = avatar.mannequin().getVelocity();
        shotFootwork = velocity.clone();
        double speed = Math.hypot(velocity.getX(), velocity.getZ());
        plannedRelease = BotShot.releaseTicks(avatar.profile().difficulty(), space, speed,
            avatar.profile().nickname().hashCode());
        decisionTimer = Math.max(decisionTimer, plannedRelease + 1);
    }

    private Vector guardPointAgainst(Vector carrier, Vector defendedRim, BotDifficulty diff) {
        Vector toRim = defendedRim.clone().setY(0).subtract(carrier.clone().setY(0));
        double len = toRim.length();
        if (len < 1e-4) {
            return carrier.clone().setY(arena.geometry().floorY());
        }

        double cushion = 2.3 - diff.contestIntensity() * 1.1;
        Vector point = carrier.clone().add(toRim.multiply(cushion / len));
        point.setY(arena.geometry().floorY());
        return point;
    }

    private Vector spotUpPoint(Vector rim) {
        double radius = 7.9;
        double angle = Math.atan2(avatar.position().getZ() - rim.getZ(), avatar.position().getX() - rim.getX());
        angle += laneSide() * 0.25;
        Vector p = rim.clone().add(new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        p.setY(arena.geometry().floorY());
        return clampCourt(p);
    }

    private Vector cutPoint(Vector rim) {
        double angle = Math.atan2(avatar.position().getZ() - rim.getZ(), avatar.position().getX() - rim.getX())
            + laneSide() * 0.45;
        double radius = 2.4;
        Vector p = rim.clone().add(new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        p.setY(arena.geometry().floorY());
        return clampCourt(p);
    }

    private Vector spacingPoint(Vector rim) {
        double angle = Math.atan2(avatar.position().getZ() - rim.getZ(), avatar.position().getX() - rim.getX())
            + laneSide() * 0.35;
        double radius = 5.5 + (avatar.profile().jerseyNumber() % 3) * 0.5;
        Vector p = rim.clone().add(new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        p.setY(arena.geometry().floorY());
        return clampCourt(p);
    }

    private int laneSide() {
        return (avatar.profile().jerseyNumber() & 1) == 0 ? 1 : -1;
    }

    private Vector driveLanePoint(Vector rim) {
        Vector toRim = rim.clone().setY(0).subtract(avatar.position().setY(0));
        double len = toRim.length();
        Vector dir = len < 1e-4 ? new Vector(0, 0, 1) : toRim.multiply(1.0 / len);
        Vector p = avatar.position().clone().add(dir.multiply(2.2));
        p.setY(arena.geometry().floorY());
        return clampCourt(p);
    }

    private Vector clampCourt(Vector p) {
        var g = arena.geometry();
        return new Vector(
            Math.clamp(p.getX(), g.minX() + 0.8, g.maxX() - 0.8),
            g.floorY(),
            Math.clamp(p.getZ(), g.minZ() + 0.8, g.maxZ() - 0.8));
    }

    private void updateVisiblePlayers(BotDifficulty difficulty) {
        java.util.Set<java.util.UUID> present = new java.util.HashSet<>();
        visiblePlayers.clear();
        for (BotAvatar other : host.botAllies(avatar)) {
            if (other == avatar || !other.isValid()) continue;
            java.util.UUID id = other.entityId();
            present.add(id);
            boolean visible = BotVision.canSee(avatar.mannequin().getEyeLocation(),
                other.mannequin().getEyeLocation().toVector());
            if (playerReactions.computeIfAbsent(id, ignored -> new BotReaction())
                .observe(visible, difficulty.reactionTicks())) visiblePlayers.add(id);
        }
        for (Player player : avatar.viewers()) {
            java.util.UUID id = player.getUniqueId();
            present.add(id);
            boolean visible = player.getWorld().equals(avatar.location().getWorld())
                && BotVision.canSee(avatar.mannequin().getEyeLocation(), player.getEyeLocation().toVector());
            if (playerReactions.computeIfAbsent(id, ignored -> new BotReaction())
                .observe(visible, difficulty.reactionTicks())) visiblePlayers.add(id);
        }
        playerReactions.keySet().retainAll(present);
    }

    private double nearestDefenderDistance(Vector pos) {
        double best = Double.MAX_VALUE;
        for (BotAvatar ally : host.botAllies(avatar)) {
            if (!ally.isValid() || host.botTeam(ally) == host.botTeam(avatar)) {
                continue;
            }
            if (ally.isValid() && visiblePlayers.contains(ally.entityId())
                && BotVision.canSee(avatar.mannequin().getEyeLocation(),
                ally.mannequin().getEyeLocation().toVector())) {
                best = Math.min(best, horizontal(ally.position(), pos));
            }
        }
        for (Player player : avatar.viewers()) {
            var side = avatar.teamOf(player);
            if (side != null && side != fr.bosketball.model.TeamSide.SPECTATOR
                && visiblePlayers.contains(player.getUniqueId()) && side != host.botTeam(avatar) && player.getWorld().equals(avatar.location().getWorld())
                && BotVision.canSee(avatar.mannequin().getEyeLocation(), player.getEyeLocation().toVector())) {
                best = Math.min(best, horizontal(player.getLocation().toVector(), pos));
            }
        }
        return best == Double.MAX_VALUE ? 8.0 : best;
    }

    private static double horizontal(Vector a, Vector b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private void say(BotChatter.Moment moment) {
        if (chatterCooldown > 0 || !chatter.ready() || avatar.viewers().isEmpty()) {
            return;
        }
        String text = chatter.choose(avatar.profile().nickname(), avatar.profile().difficulty(), moment);
        if (text == null) {
            return;
        }
        chatter.spoken();
        chatterCooldown = 300 + Math.floorMod(avatar.profile().nickname().hashCode(), 120);
        for (Player player : avatar.viewers()) {
            player.sendMessage(Msg.parse(
                fr.bosketball.util.Palette.of(fr.bosketball.util.Palette.MUTED, avatar.profile().nickname())
                + " " + fr.bosketball.util.Palette.of(fr.bosketball.util.Palette.DIM, "\u00b7") + " "
                + fr.bosketball.util.Palette.of(fr.bosketball.util.Palette.TEXT, text)));
        }
    }

    public void reset() {
        state = State.OFF_BALL;
        decisionTimer = 0;
        actionCooldown = 0;
        ballReaction.reset();
        heldBallReaction.reset();
        heldBallReady = false;
        seenBallHolder = null;
        carrierReaction.reset();
        possessionAwareness.reset();
        observedPossessor = null;
        knownTeamPossession = null;
        possessionReaction.reset();
        playerReactions.clear();
        visiblePlayers.clear();
        hadBall = false;
        shotTicks = 0;
        ballReady = false;
        carrierReady = false;
        seenCarrier = null;
        oscillation.reset();
        lastSeenPosition = null;
        stuckTicks = 0;
        recoveryTarget = null;
        recoveryTicks = 0;
        anchor = avatar.position();
    }

}
