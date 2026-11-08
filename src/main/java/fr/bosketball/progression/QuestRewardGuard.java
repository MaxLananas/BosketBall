package fr.bosketball.progression;

import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerQuest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a quest payout happen exactly once.
 *
 * <h2>Why the {@code claimed} flag is not enough</h2>
 * {@link PlayerQuest#claimed()} is persisted with the profile, so it survives a restart — but it
 * is a boolean on an object that gets <em>replaced</em> whenever the board rolls over, and a
 * corrupted or hand-edited profile can carry a completed quest with the flag back at false. A
 * player who then clicks "Claim all" gets paid twice for the same objective.
 *
 * <p>The mission asks for a test that <em>no reward path pays twice</em>. A flag protects one
 * path at a time; a ledger protects all of them at once. This is the ledger for quests, and it is
 * keyed by the quest's <em>identity</em> — its id plus the board period it was assigned in — so
 * a quest that rolls over tomorrow is a different key and pays again, which is exactly right.
 *
 * <h2>Why the key carries the period</h2>
 * {@code d_points_20} is a template id, and the same template comes back every day. Without the
 * period in the key, the second day's "Scoring Touch" would be refused because the first day's
 * was paid. The period is the board's refresh timestamp, which changes exactly when the board
 * changes.
 */
public final class QuestRewardGuard {

    /** One payout, remembered. */
    public record Key(UUID player, String questId, long period) {
    }

    private final Map<Key, Long> paid = new ConcurrentHashMap<>();

    /**
     * Pays a quest's reward, once.
     *
     * @param profile the player being paid
     * @param quest the quest being claimed
     * @return the coins actually paid — {@code 0} when this quest was already paid
     */
    public int pay(PlayerProfile profile, PlayerQuest quest) {
        if (profile == null || quest == null) {
            return 0;
        }
        int coins = quest.rewardCoins();
        int xp = quest.rewardXp();
        if (coins <= 0 && xp <= 0) {
            return 0;
        }
        Key key = keyFor(profile, quest);
        if (paid.containsKey(key)) {
            return 0;
        }
        paid.put(key, System.currentTimeMillis());
        if (coins > 0) {
            profile.addEarnedCoins(coins);
        }
        if (xp > 0) {
            profile.addXp(xp);
        }
        return coins;
    }

    /** Whether this quest has already been paid. */
    public boolean hasPaid(PlayerProfile profile, PlayerQuest quest) {
        if (profile == null || quest == null) {
            return false;
        }
        return paid.containsKey(keyFor(profile, quest));
    }

    /**
     * The identity of a quest payout.
     *
     * <p>The period is the quest's assignment time, so the same template on two different days is
     * two different payouts.
     */
    public static Key keyFor(PlayerProfile profile, PlayerQuest quest) {
        long period = quest.assignedAtMillis();
        if (period <= 0L) {
            period = quest.expiresAtMillis();
        }
        return new Key(profile.uuid(), quest.id(), period);
    }

    /** Forgets a player's payouts — used when a board is replaced wholesale. */
    public void forget(UUID uuid) {
        paid.keySet().removeIf(key -> key.player().equals(uuid));
    }

    /** Forgets every payout. */
    public void clear() {
        paid.clear();
    }

    /** How many payouts this guard has recorded. */
    public int recordedCount() {
        return paid.size();
    }
}
