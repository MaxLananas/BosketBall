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

    private State state = State.OFF_BALL;
    private Vector anchor;
    private int decisionTimer;
    private int actionCooldown;
    private int chatterCooldown = 120;
    private boolean sprinting;
    private boolean ballHandler;
    private int mistakeTicks;

    public BotBrain(Arena arena, BotAvatar avatar, Handler host) {
        this.arena = arena;
        this.avatar = avatar;
        this.host = host;
        this.anchor = avatar.position();
    }

    public BotAvatar avatar() {
        return avatar;
    }

    public State state() {
        return state;
    }

    public void tick() {
        BotDifficulty diff = avatar.profile().difficulty();
        avatar.clampToCourt();
        avatar.tickNameplate();
        if (avatar.location() == null) {
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
        if (mistakeTicks > 0) {
            mistakeTicks--;
            return;
        }

        BasketballEntity ball = host.botBall();
        ballHandler = ball != null
            && ball.holderId() != null
            && ball.holderId().equals(avatar.entityId());

        if (decisionTimer <= 0) {
            decisionTimer = Math.max(1, diff.reactionTicks());
            decide(ball);
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
            double shootRoll = ThreadLocalRandom.current().nextDouble();

            boolean wantShoot = space > 2.1 && shootRoll < diff.shotAccuracy() * 0.55;
            boolean wantDunk = rimDist < 4.2 && space > 1.4
                && shootRoll > 0.55 && ThreadLocalRandom.current().nextDouble() < avatar.profile().driveBias() * 0.5;
            boolean wantPass = host.botPassTargets(avatar).isEmpty()
                ? false
                : ThreadLocalRandom.current().nextDouble() < diff.passTendency() * avatar.profile().passBias();

            if (wantDunk) {
                state = State.DRIVE;
            } else if (wantShoot) {
                state = State.SHOOT;
            } else if (wantPass) {
                actionCooldown = 0;
                state = State.OFF_BALL;
                host.botPass(avatar);
                avatar.swingArm(true);
            } else {
                state = State.DRIVE;
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
        if (carrier != null) {
            return true;
        }
        return false;
    }

    private void execute(BasketballEntity ball, BotDifficulty diff) {
        Vector rimDef = host.botDefensiveRim(avatar);
        Vector rimOff = host.botOffensiveRim(avatar);

        switch (state) {
            case DEFEND -> {
                Player carrier = host.botHumanCarrier();
                Vector guardPoint;
                Vector look;
                if (carrier != null) {
                    guardPoint = guardPointAgainst(carrier.getLocation().toVector(), rimDef, diff);
                    look = carrier.getEyeLocation().toVector();
                    sprinting = true;
                } else {
                    guardPoint = anchor;
                    look = rimDef;
                    sprinting = false;
                }
                avatar.steerTo(guardPoint, sprinting, look, decisionTimer);
                boolean close = carrier != null && horizontal(carrier.getLocation().toVector(), avatar.position()) < 2.6;
                avatar.crouch(close && diff.contestIntensity() > 0.5);

                if (carrier != null && close && actionCooldown <= 0
                    && carrier.getUniqueId().equals(ball != null ? ball.holderId() : null)) {
                    actionCooldown = 45 + diff.reactionTicks() * 4;
                    if (ThreadLocalRandom.current().nextDouble() < diff.stealSuccess()) {
                        host.botDeflect(avatar, rimOff.clone().subtract(avatar.position()).setY(0.2));
                        avatar.swingArm(true);
                        say(defenseBark());
                    } else {
                        avatar.swingArm(true);
                    }
                }
            }
            case LOOSE_BALL -> {
                if (ball != null) {
                    Vector target = ball.position().clone().setY(arena.geometry().floorY());
                    avatar.steerTo(target, true, ball.position(), decisionTimer);
                    if (horizontal(ball.position(), avatar.position()) < 1.5) {
                        host.botTryPickup(avatar);
                    }
                }
            }
            case SPOT_UP -> {
                avatar.steerTo(anchor, false, rimOff, decisionTimer);
                if (ballHandler && actionCooldown <= 0) {
                    actionCooldown = 30;
                    host.botShoot(avatar, rimOff);
                    avatar.swingArm(true);
                    say(shotBark());
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
                    say(dunkBark());
                } else if (ballHandler && rimDist >= 3.4 && rimDist < 7.5 && actionCooldown <= 0
                    && ThreadLocalRandom.current().nextDouble() < 0.5) {
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
                    say(shotBark());
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
            best = Math.min(best, horizontal(ally.position(), pos));
        }
        Player carrier = host.botHumanCarrier();
        if (carrier != null) {
            best = Math.min(best, horizontal(carrier.getLocation().toVector(), pos));
        }
        return best == Double.MAX_VALUE ? 8.0 : best;
    }

    private static double horizontal(Vector a, Vector b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private void say(String text) {
        if (chatterCooldown > 0 || text == null) {
            return;
        }
        chatterCooldown = 260 + ThreadLocalRandom.current().nextInt(200);
        for (Player p : avatar.viewers()) {
            p.sendMessage(Msg.parse("<#94A3B8>[" + avatar.profile().nickname() + "]</#94A3B8> <#F8FAFC>" + text + "</#F8FAFC>"));
        }
    }

    private String shotBark() {
        return switch (ThreadLocalRandom.current().nextInt(5)) {
            case 0 -> "Money!";
            case 1 -> "That's cash.";
            case 2 -> "Too much space.";
            case 3 -> "On my way up!";
            default -> "Bucket.";
        };
    }

    private String dunkBark() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> "Posterised!";
            case 1 -> "Get out of my paint.";
            case 2 -> "Loud in here.";
            default -> "Rim check.";
        };
    }

    private String defenseBark() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> "I got him!";
            case 1 -> "Pick it up!";
            case 2 -> "Ball!";
            default -> "Locked up.";
        };
    }

    public void reset() {
        state = State.OFF_BALL;
        decisionTimer = 0;
        actionCooldown = 0;
        anchor = avatar.position();
    }

}
