package fr.bosketball.game.bot;

import fr.bosketball.arena.Arena;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Owns every AI player inside one match: spawns them on their team spawns, ticks their
 * brains at 20 Hz, and exposes the read-only helpers the {@code MatchSession} needs to keep
 * bots inside the normal basketball rules (pickups, shots, dunks, passes, deflections).
 *
 * <p>Bots are intentionally <b>not</b> {@link Player} objects: faking a {@code Player} would
 * fight Paper's authentication, tab list, statistics and scoreboard systems. Instead the
 * session maps a bot's mannequin UUID to its team, and the ball simply uses that UUID as a
 * holder id so every existing physics path keeps working untouched.</p>
 */
public final class BotSquad {

    private final Arena arena;
    private final List<BotAvatar> avatars = new ArrayList<>();
    private final List<BotBrain> brains = new ArrayList<>();
    private BotBrain.Handler handler;

    public BotSquad(Arena arena) {
        this.arena = arena;
    }

    public void attach(BotBrain.Handler handler) {
        this.handler = handler;
    }

    public boolean isEmpty() {
        return avatars.isEmpty();
    }

    public int size() {
        return avatars.size();
    }

    public List<BotAvatar> avatars() {
        return avatars;
    }

    public List<BotBrain> brains() {
        return brains;
    }

    /** Adds a bot of the given difficulty on the given team spawn index. */
    public BotAvatar spawn(BotDifficulty difficulty, fr.bosketball.model.TeamSide team, int slotIndex) {
        if (handler == null) {
            return null;
        }
        BotProfile profile = BotProfile.pick(difficulty, usedNicknames());
        BotAvatar avatar = new BotAvatar(session(), arena, profile);
        var spawnLoc = arena.teamSpawn(team, slotIndex);
        avatar.spawn(spawnLoc);
        if (!avatar.isValid()) {
            return null;
        }
        avatars.add(avatar);
        BotBrain brain = new BotBrain(arena, avatar, handler);
        brains.add(brain);
        return avatar;
    }

    /** Removes every bot (match over, or /bb bot off). */
    public void clear() {
        for (BotAvatar a : avatars) {
            a.despawn();
        }
        avatars.clear();
        brains.clear();
    }

    public void tick() {
        for (int i = 0; i < brains.size(); i++) {
            BotAvatar avatar = avatars.get(i);
            if (avatar == null) {
                continue;
            }
            if (!avatar.isValid()) {
                avatar.spawn(arena.teamSpawn(fr.bosketball.model.TeamSide.IGNIS, 0));
                continue;
            }
            brains.get(i).tick();
        }
    }

    public BotAvatar avatarById(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        for (BotAvatar a : avatars) {
            if (entityId.equals(a.entityId())) {
                return a;
            }
        }
        return null;
    }

    public BotBrain brainOf(BotAvatar avatar) {
        int idx = avatars.indexOf(avatar);
        return idx >= 0 && idx < brains.size() ? brains.get(idx) : null;
    }

    public boolean isBotEntity(UUID entityId) {
        return avatarById(entityId) != null;
    }

    /** Broadcasts a short introduction so players know who they are up against. */
    public void announce(List<Player> audience) {
        if (avatars.isEmpty()) {
            return;
        }
        for (Player p : audience) {
            if (p == null || !p.isOnline()) {
                continue;
            }
            for (BotAvatar a : avatars) {
                Msg.send(p, "<gray>🤖 <white>" + a.profile().nickname() + "</white> <dark_gray>|</dark_gray> "
                    + "<" + a.profile().difficulty().hexColor() + ">"
                    + a.profile().difficulty().displayName() + "</" + a.profile().difficulty().hexColor() + ">"
                    + " <dark_gray>|</dark_gray> <gray>" + a.profile().archetype() + "</gray>");
            }
        }
    }

    /** Convenience: every bot position, for spacing maths in the brain. */
    public List<Vector> positions() {
        List<Vector> out = new ArrayList<>(avatars.size());
        for (BotAvatar a : avatars) {
            out.add(a.position());
        }
        return out;
    }

    /**
     * Rebinds the ball to a bot body. Kept here so the session never has to know about the
     * mannequin API: the ball only ever needs the UUID.
     */
    public void bindBall(BasketballEntity ball, BotAvatar avatar) {
        if (ball == null || avatar == null || !avatar.isValid()) {
            return;
        }
        ball.bindToVirtualHolder(avatar.entityId(), avatar.location(), avatar.profile().nickname());
        Msg.playPackSound(avatar.location(), "ball.bounce", 0.7f, 1.06f);
    }

    private java.util.Set<String> usedNicknames() {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (BotAvatar a : avatars) {
            used.add(a.profile().nickname());
        }
        return used;
    }

    private fr.bosketball.game.MatchSession session() {
        return null;
    }

    public void remove(BotAvatar avatar) {
        int idx = avatars.indexOf(avatar);
        if (idx >= 0) {
            avatars.remove(idx);
            if (idx < brains.size()) {
                brains.remove(idx);
            }
        }
        avatar.despawn();
    }
}
