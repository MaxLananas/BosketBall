package fr.bosketball.game.bot;

import fr.bosketball.arena.Arena;
import fr.bosketball.physics.BasketballEntity;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class BotSquad {

    private final Arena arena;
    private final List<BotAvatar> avatars = new ArrayList<>();
    private final List<BotBrain> brains = new ArrayList<>();
    private BotBrain.Handler handler;
    private final BotChatter chatter = new BotChatter();
    private fr.bosketball.model.TeamSide possessionTeam;

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
        BotBrain brain = new BotBrain(arena, avatar, handler, chatter);
        brains.add(brain);
        return avatar;
    }

    public void clear() {
        for (BotAvatar a : avatars) {
            a.despawn();
        }
        avatars.clear();
        brains.clear();
        chatter.newPossession();
        possessionTeam = null;
    }

    public void tick() {
        chatter.tick();
        var ball = handler == null ? null : handler.botBall();
        fr.bosketball.model.TeamSide team = null;
        if (ball != null && ball.holderId() != null) {
            BotAvatar carrier = avatarById(ball.holderId());
            if (carrier != null) {
                team = handler.botTeam(carrier);
            } else {
                var human = handler.botHumanCarrier();
                var match = session();
                if (human != null && match != null) {
                    team = match.getTeam(human.getUniqueId());
                }
            }
        }
        if (team != null && team != possessionTeam) {
            chatter.newPossession();
            possessionTeam = team;
        }
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

    public void announce(List<Player> audience) {
        if (avatars.isEmpty()) {
            return;
        }
        for (Player p : audience) {
            if (p == null || !p.isOnline()) {
                continue;
            }
            for (BotAvatar a : avatars) {
                Msg.send(p, "<#94A3B8> <#F8FAFC>" + a.profile().nickname() + "</#F8FAFC> <#64748B>\u00b7</#64748B> "
                    + "<" + a.profile().difficulty().hexColor() + ">"
                    + a.profile().difficulty().displayName() + "</" + a.profile().difficulty().hexColor() + ">"
                    + " <#64748B>\u00b7</#64748B> <#94A3B8>" + a.profile().archetype() + "</#94A3B8>");
            }
        }
    }

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
        var plugin = fr.bosketball.BosketBallPlugin.getInstance();
        return plugin == null || plugin.gameManager() == null
            ? null : plugin.gameManager().getSessionByArena(arena.id());
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
