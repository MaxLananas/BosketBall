package fr.bosketball.model;

/**
 * A daily / weekly quest (or a rerollable 6h contract).
 *
 * <p>Quests are the retention fuel of the server: every player gets a personal rotating
 * board of objectives worth Coins (and occasionally bonus cosmetic tokens). Progress is
 * driven exclusively by {@link ProgressStat} deltas reported from gameplay, so a quest
 * can never desync from the real career statistics.</p>
 */
public final class PlayerQuest {

    private final String id;
    private final String title;
    private final String description;
    private final ProgressStat stat;
    private final int target;
    private final int rewardCoins;
    private final int rewardXp;
    private final String iconModel;

    private int progress;
    private boolean claimed;
    private boolean rewarded;
    private long assignedAtMillis;
    private long expiresAtMillis;

    public PlayerQuest(
        String id,
        String title,
        String description,
        ProgressStat stat,
        int target,
        int rewardCoins,
        int rewardXp,
        String iconModel,
        long assignedAtMillis,
        long expiresAtMillis
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.stat = stat;
        this.target = Math.max(1, target);
        this.rewardCoins = Math.max(0, rewardCoins);
        this.rewardXp = Math.max(0, rewardXp);
        this.iconModel = iconModel;
        this.assignedAtMillis = assignedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public ProgressStat stat() {
        return stat;
    }

    public int target() {
        return target;
    }

    public int rewardCoins() {
        return rewardCoins;
    }

    public int rewardXp() {
        return rewardXp;
    }

    public String iconModel() {
        return iconModel;
    }

    public int progress() {
        return Math.min(progress, target);
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(progress, target));
    }

    public void addProgress(int delta) {
        if (delta > 0) {
            setProgress(this.progress + delta);
        }
    }

    public boolean isComplete() {
        return progress >= target;
    }

    public boolean claimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public boolean rewarded() {
        return rewarded;
    }

    public void setRewarded(boolean rewarded) {
        this.rewarded = rewarded;
    }

    public long assignedAtMillis() {
        return assignedAtMillis;
    }

    public void setAssignedAtMillis(long assignedAtMillis) {
        this.assignedAtMillis = assignedAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public void setExpiresAtMillis(long expiresAtMillis) {
        this.expiresAtMillis = expiresAtMillis;
    }

    public boolean isExpired(long nowMillis) {
        return expiresAtMillis > 0 && nowMillis >= expiresAtMillis;
    }

    public long remainingMillis(long nowMillis) {
        return expiresAtMillis <= 0 ? 0L : Math.max(0L, expiresAtMillis - nowMillis);
    }

    public double completionRatio() {
        return Math.min(1.0, (double) progress / (double) target);
    }

    /** Compact 0-10 pip meter used in GUI lore, e.g. {@code ▰▰▰▰▱▱▱▱▱▱}. */
    public String pipMeter() {
        int filled = (int) Math.round(completionRatio() * 10.0);
        StringBuilder sb = new StringBuilder();
        sb.append(filled >= target ? "<#4ADE80>" : "<#FBBF24>");
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? '▰' : '▱');
        }
        sb.append("</").append(filled >= target ? "#4ADE80" : "#FBBF24").append('>');
        return sb.toString();
    }
}
