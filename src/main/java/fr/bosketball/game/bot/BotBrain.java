package fr.bosketball.game.bot;

import fr.bosketball.arena.Arena;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.util.Msg;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class BotBrain {

    public interface Handler {

        boolean botTryPickup(BotAvatar avatar);

        void botShoot(BotAvatar avatar, Vector targetRim);

        void botDunk(BotAvatar avatar, Vector targetRim);

        void botPass(BotAvatar avatar);

        void botDeflect(BotAvatar avatar, Vector direction);

        List<BotAvatar> botAllies(BotAvatar avatar);

        List<Vector> botPassTargets(BotAvatar avatar);

        Vector botOffensiveRim(BotAvatar avatar);

        Vector botDefensiveRim(BotAvatar avatar);

        BasketballEntity botBall();

        Player botHumanCarrier();

        fr.bosketball.model.TeamSide botTeam(BotAvatar avatar);
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
    private final BotReaction ballReaction = new BotReaction();
    private final BotReaction carrierReaction = new BotReaction();
    private boolean ballReady;
    private boolean carrierReady;
    private java.util.UUID seenCarrier;

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
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            Vector candidate = clampCourt(here.clone().add(new Vector(Math.cos(angle), 0, Math.sin(angle)).multiply(1.5)));
            Vector offset = candidate.clone().subtract(here).setY(0);
            double length = offset.length();
            if (length < 0.7) {
                continue;
            }
            Vector direction = offset.multiply(1.0 / length);
            boolean clear = true;
            for (double height : new double[]{0.4, 1.4}) {
                var origin = location.clone().add(0, height, 0);
                if (location.getWorld().rayTraceBlocks(origin, direction, length,
                    org.bukkit.FluidCollisionMode.NEVER, true) != null) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                recoveryTarget = candidate;
                recoveryTicks = 24;
                ballReaction.reset();
                carrierReaction.reset();
                ballReady = false;
                carrierReady = false;
                seenCarrier = null;
                oscillation.reset();
                return;
            }
        }
    }

    public void tick() {
        BotDifficulty diff = avatar.profile().difficulty();
        avatar.clampToCourt();
        avatar.tickNameplate();
        if (avatar.location() == null) {
            return;
        }
        tickStuckWatchdog();
        if (recoveryTicks > 0 && recoveryTarget != null) {
            recoveryTicks--;
            avatar.steerTo(recoveryTarget, false, recoveryTarget, recoveryTicks);
            if (horizontal(avatar.position(), recoveryTarget) < 0.4) {
                recoveryTicks = 0;
            }
            return;
        }

        if (decisionTimer > 0) {
            decisionTimer--;
        }
        if (actionCooldown > 0) {
            actionCooldown--;
        }
        if (chatterCooldown > 0) {
            chatterCooldown--;
        }

        BasketballEntity ball = host.botBall();
        ballHandler = ball != null
            && ball.holderId() != null
            && ball.holderId().equals(avatar.entityId());

        boolean seesLooseBall = ball != null && ball.holderId() == null
            && ball.mode() != BasketballEntity.BallMode.IN_SHOT_FLIGHT
            && ball.mode() != BasketballEntity.BallMode.IN_DUNK_FLIGHT
            && BotVision.canSee(avatar.mannequin().getEyeLocation(), ball.position());
        ballReady = ballReaction.observe(seesLooseBall, diff.reactionTicks());
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
        if (observedId == null || !observedId.equals(seenCarrier)) {
            carrierReaction.reset();
            seenCarrier = observedId;
        }
        carrierReady = carrierReaction.observe(observedId != null, diff.reactionTicks());

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

        boolean teamHasBall = ball != null && ball.holderId() != null && host.botTeam(avatar) != null
            && isTeamPossession(ball);
        boolean loose = ball != null && ball.holderId() == null
            && ball.mode() != BasketballEntity.BallMode.IN_SHOT_FLIGHT
            && ball.mode() != BasketballEntity.BallMode.IN_DUNK_FLIGHT;

        if (loose) {
            if (!ballReady) {
                state = State.OFF_BALL;
                return;
            }
            double dist = horizontal(ball.position(), myPos);
            state = dist < 7.0 ? State.LOOSE_BALL : State.OFF_BALL;
            return;
        }
        if (!teamHasBall) {
            state = State.DEFEND;
            return;
        }
        if (ballHandler) {

            double space = nearestDefenderDistance(myPos);
            double rimDist = horizontal(rim, myPos);
            List<Vector> targets = host.botPassTargets(avatar);
            // The host chooses the recipient. Do not ask it to pass unless every possible
            // recipient is visible; otherwise it could select someone behind a wall.
            boolean canPass = !targets.isEmpty();
            for (Vector target : targets) {
                if (!BotVision.canSee(avatar.mannequin().getEyeLocation(), target.clone().add(new Vector(0, 1, 0)))) {
                    canPass = false;
                    break;
                }
            }
            BotTactics.Style style = BotTactics.style(diff);
            BotTactics.Action action = BotTactics.choose(style, avatar.profile().shootBias(),
                avatar.profile().passBias(), avatar.profile().driveBias(), space, rimDist, canPass,
                ThreadLocalRandom.current().nextDouble());
            decisionTimer = Math.max(decisionTimer, style.commitmentTicks());
            switch (action) {
                case SHOOT -> state = State.SHOOT;
                case PASS -> {
                    actionCooldown = 0;
                    state = State.OFF_BALL;
                    host.botPass(avatar);
                    avatar.swingArm(true);
                }
                case DRIVE -> state = State.DRIVE;
            }
            return;
        }

        double rimDist = horizontal(rim, myPos);
        if (rimDist > 9.0 && ThreadLocalRandom.current().nextDouble() < diff.threePointTendency()) {
            state = State.SPOT_UP;
            anchor = spotUpPoint(rim);
        } else if (ThreadLocalRandom.current().nextDouble() < 0.44) {
            state = State.CUT;
            anchor = cutPoint(rim);
        } else {
            state = State.OFF_BALL;
            anchor = spacingPoint(rim);
        }
    }

    private BotAvatar botCarrier(java.util.UUID holder) {
        if (holder == null) return null;
        for (BotAvatar other : host.botAllies(avatar)) {
            if (other.isValid() && holder.equals(other.entityId())) return other;
        }
        return null;
    }

    private boolean isTeamPossession(BasketballEntity ball) {
        var team = host.botTeam(avatar);
        if (team == null) {
            return false;
        }

        for (BotAvatar ally : host.botAllies(avatar)) {
            if (ally.entityId() != null && ally.entityId().equals(ball.holderId())
                && host.botTeam(ally) == team) {
                return true;
            }
        }
        Player carrier = host.botHumanCarrier();
        return carrier != null && avatar.teamOf(carrier) == team;
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

                if (close && actionCooldown <= 0 && ball != null && seenCarrier.equals(ball.holderId())) {
                    actionCooldown = 45 + diff.reactionTicks() * 4;
                    avatar.swingArm(true);
                    if (ThreadLocalRandom.current().nextDouble() < diff.stealSuccess()) {
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
            case SPOT_UP -> {
                avatar.steerTo(anchor, false, rimOff, decisionTimer);
                if (ballHandler && actionCooldown <= 0) {
                    actionCooldown = 30;
                    host.botShoot(avatar, rimOff);
                    avatar.swingArm(true);
                    say(BotChatter.Moment.SHOT);
                }
            }
            case CUT -> avatar.steerTo(anchor, true, rimOff, decisionTimer);
            case DRIVE -> {
                Vector lane = driveLanePoint(rimOff);
                avatar.steerTo(lane, true, rimOff, decisionTimer);
                double rimDist = horizontal(rimOff, avatar.position());
                if (ballHandler && rimDist < 3.4 && actionCooldown <= 0) {
                    actionCooldown = 40;
                    host.botDunk(avatar, rimOff);
                    avatar.swingArm(true);
                    say(BotChatter.Moment.DUNK);
                } else if (ballHandler && rimDist >= 3.4 && rimDist < 7.5 && actionCooldown <= 0) {
                    actionCooldown = 55;
                    host.botShoot(avatar, rimOff);
                    avatar.swingArm(true);
                }
            }
            case SHOOT -> {
                avatar.steerTo(anchor, false, rimOff, decisionTimer);
                if (ballHandler && actionCooldown <= 0) {
                    actionCooldown = 45;
                    host.botShoot(avatar, rimOff);
                    avatar.swingArm(true);
                    say(BotChatter.Moment.SHOT);
                }
            }
            case CELEBRATE -> avatar.celebrate();
            default -> avatar.steerTo(anchor, false, rimOff, decisionTimer);
        }
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
        angle += (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.7;
        Vector p = rim.clone().add(new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        p.setY(arena.geometry().floorY());
        return clampCourt(p);
    }

    private Vector cutPoint(Vector rim) {
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        double radius = 1.6 + ThreadLocalRandom.current().nextDouble() * 1.8;
        Vector p = rim.clone().add(new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        p.setY(arena.geometry().floorY());
        return clampCourt(p);
    }

    private Vector spacingPoint(Vector rim) {
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        double radius = 4.8 + ThreadLocalRandom.current().nextDouble() * 2.6;
        Vector p = rim.clone().add(new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
        p.setY(arena.geometry().floorY());
        return clampCourt(p);
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

    private double nearestDefenderDistance(Vector pos) {
        double best = Double.MAX_VALUE;
        for (BotAvatar ally : host.botAllies(avatar)) {
            if (host.botTeam(ally) == host.botTeam(avatar)) {
                continue;
            }
            if (ally.isValid() && BotVision.canSee(avatar.mannequin().getEyeLocation(),
                ally.mannequin().getEyeLocation().toVector())) {
                best = Math.min(best, horizontal(ally.position(), pos));
            }
        }
        for (Player player : avatar.viewers()) {
            var side = avatar.teamOf(player);
            if (side != null && side != fr.bosketball.model.TeamSide.SPECTATOR
                && side != host.botTeam(avatar) && player.getWorld().equals(avatar.location().getWorld())
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
        if (chatterCooldown > 0 || avatar.viewers().isEmpty()) {
            return;
        }
        String text = chatter.choose(avatar.profile().nickname(), avatar.profile().difficulty(), moment);
        if (text == null) {
            return;
        }
        chatterCooldown = 300 + Math.floorMod(avatar.profile().nickname().hashCode(), 120);
        for (Player player : avatar.viewers()) {
            player.sendMessage(Msg.parse(
                fr.bosketball.util.Palette.of(fr.bosketball.util.Palette.MUTED, avatar.profile().nickname())
                + " " + fr.bosketball.util.Palette.of(fr.bosketball.util.Palette.DIM, "·") + " "
                + fr.bosketball.util.Palette.of(fr.bosketball.util.Palette.TEXT, text)));
        }
    }

    public void reset() {
        state = State.OFF_BALL;
        decisionTimer = 0;
        actionCooldown = 0;
        ballReaction.reset();
        carrierReaction.reset();
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
