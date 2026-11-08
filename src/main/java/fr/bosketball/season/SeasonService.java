package fr.bosketball.season;

import fr.bosketball.util.Palette;
import fr.bosketball.BosketBallPlugin;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.config.BosketConfig;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.util.Msg;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class SeasonService {

    private static final String FILE_NAME = "current.yml";
    private static final String ARCHIVE_DIR = "archive";

    public record PendingReward(int coins, int xp, String label) {
        PendingReward merge(int moreCoins, int moreXp, String moreLabel) {
            String merged = label == null || label.isBlank() ? moreLabel : label;
            return new PendingReward(coins + Math.max(0, moreCoins), xp + Math.max(0, moreXp), merged);
        }
    }

    public record SeasonArchive(
        int index,
        String name,
        long startedAtMillis,
        long endedAtMillis,
        int participants,
        List<SeasonStanding> standings
    ) {
    }

    private static final Comparator<SeasonRecord> STANDING_ORDER = Comparator
        .comparingInt(SeasonRecord::bestElo).reversed()
        .thenComparing(Comparator.comparingInt(SeasonRecord::wins).reversed())
        .thenComparingInt(SeasonRecord::matches)
        .thenComparing(SeasonRecord::name, String.CASE_INSENSITIVE_ORDER);

    private final BosketBallPlugin plugin;
    private final Logger logger;
    private final File seasonsDir;
    private final File currentFile;
    private final File archiveDir;

    private final Map<UUID, SeasonRecord> records = new ConcurrentHashMap<>();
    private final Map<UUID, PendingReward> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> trophies = new ConcurrentHashMap<>();
    /**
     * The end-of-season card of a player who was offline when the season closed.
     *
     * <p>Rebuilt from the record rather than persisted as text, so a card that is delivered weeks
     * later still says what the season actually was.
     */
    private final Map<UUID, SeasonSummary> pendingSummaries = new ConcurrentHashMap<>();

    private volatile SeasonState state;
    private volatile boolean dirty;
    private BukkitTask ticker;
    private BukkitTask autosave;
    private volatile boolean rollingOver;

    public SeasonService(BosketBallPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.seasonsDir = new File(plugin.getDataFolder(), "seasons");
        this.currentFile = new File(seasonsDir, FILE_NAME);
        this.archiveDir = new File(seasonsDir, ARCHIVE_DIR);
    }

    private BosketConfig.Season settings() {
        BosketConfig cfg = plugin.settings();
        return cfg == null ? BosketConfig.defaults().season() : cfg.season();
    }

    public boolean enabled() {
        return settings().enabled();
    }

    public SeasonState state() {
        return state;
    }

    public int placementsRequired() {
        return Math.max(0, settings().placementMatches());
    }

    public void load() {
        if (!seasonsDir.exists() && !seasonsDir.mkdirs()) {
            logger.warning("[Season] Could not create " + seasonsDir.getAbsolutePath() + " — season data will not persist.");
        }
        if (!archiveDir.exists() && !archiveDir.mkdirs()) {
            logger.warning("[Season] Could not create " + archiveDir.getAbsolutePath() + ".");
        }
        records.clear();
        pending.clear();
        trophies.clear();
        pendingSummaries.clear();

        YamlConfiguration yml = currentFile.exists()
            ? AtomicYamlWriter.loadSafe(currentFile, logger)
            : new YamlConfiguration();

        long now = System.currentTimeMillis();
        int index = Math.max(1, yml.getInt("season.index", 1));
        String name = yml.getString("season.name", SeasonMath.seasonName(index));

        // Real calendar dates, in this order of precedence:
        //   1. the dates already persisted in season.yml  (a restart never moves them),
        //   2. season.start-date / season.end-date from config.yml,
        //   3. now / now + duration-days as a last resort.
        long persistedStart = yml.getLong("season.started-at", -1L);
        long persistedEnd = yml.getLong("season.ends-at", -1L);
        long startedAt;
        long endsAt;
        if (persistedStart > 0 && persistedEnd > persistedStart) {
            startedAt = persistedStart;
            endsAt = persistedEnd;
        } else {
            long[] window = SeasonMath.seasonWindow(index,
                SeasonMath.parseDateMillis(settings().startDate()),
                SeasonMath.parseDateMillis(settings().endDate()),
                durationMillis());
            if (window[0] > 0 && window[1] > window[0]) {
                startedAt = window[0];
                endsAt = window[1];
            } else {
                startedAt = now;
                endsAt = now + durationMillis();
            }
        }
        if (endsAt <= startedAt) {
            endsAt = startedAt + Math.max(1L, durationMillis());
        }
        state = new SeasonState(index, name == null || name.isBlank() ? SeasonMath.seasonName(index) : name, startedAt, endsAt);

        ConfigurationSection playersSection = yml.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                ConfigurationSection s = playersSection.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                SeasonRecord rec = new SeasonRecord(uuid, s.getString("name", "Player"), s.getInt("best-elo", 1000));
                rec.setMatches(s.getInt("matches", 0));
                rec.setWins(s.getInt("wins", 0));
                rec.setPlacementsDone(Math.max(
                    s.getInt("placements", 0),
                    Math.min(rec.matches(), placementsRequired())));
                rec.addCoinsEarned(s.getInt("coins-earned", 0));
                rec.addXpEarned(s.getInt("xp-earned", 0));
                records.put(uuid, rec);
            }
        }

        ConfigurationSection pendingSection = yml.getConfigurationSection("pending");
        if (pendingSection != null) {
            for (String key : pendingSection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                ConfigurationSection s = pendingSection.getConfigurationSection(key);
                if (uuid == null || s == null) {
                    continue;
                }
                pending.put(uuid, new PendingReward(
                    Math.max(0, s.getInt("coins", 0)),
                    Math.max(0, s.getInt("xp", 0)),
                    s.getString("label", "Season rewards")
                ));
            }
        }

        ConfigurationSection trophySection = yml.getConfigurationSection("trophies");
        if (trophySection != null) {
            for (String key : trophySection.getKeys(false)) {
                UUID uuid = parseUuid(key);
                if (uuid != null) {
                    trophies.put(uuid, Math.max(0, trophySection.getInt(key, 0)));
                }
            }
        }

        // Write the window back immediately: without this a season without a saved date restarted
        // the countdown on every server boot.
        boolean windowChanged = persistedStart != startedAt || persistedEnd != endsAt;
        if (state.isOver()) {
            logger.info("[Season] " + state.name() + " ended while the server was offline — resolving now.");
            rollover("offline");
            saveNow();
        } else {
            if (windowChanged) {
                saveNow();
            }
            logger.info("[Season] " + state.name() + " loaded · "
                + SeasonMath.formattedWindow(startedAt, endsAt) + " ("
                + state.remainingLabel() + " left) · " + records.size() + " ranked players · "
                + placementsRequired() + " placement matches");
        }
    }

    public void start() {
        if (ticker != null) {
            ticker.cancel();
        }
        if (autosave != null) {
            autosave.cancel();
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 30L, 20L * 60L);
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAsync, 20L * 60L * 5L, 20L * 60L * 5L);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (autosave != null) {
            autosave.cancel();
            autosave = null;
        }
        saveNow();
    }

    public void reload() {
        configTouched();
    }

    private void configTouched() {
        dirty = true;
    }

    private long durationMillis() {
        return SeasonMath.daysToMillis(settings().durationDays());
    }

    public void tick() {
        SeasonState current = state;
        if (current == null || rollingOver) {
            return;
        }
        if (!settings().enabled()) {
            return;
        }
        if (current.isOver()) {
            rollover("scheduled");
        }
    }

    public SeasonRecord record(UUID uuid) {
        return uuid == null ? null : records.get(uuid);
    }

    public int bestElo(UUID uuid) {
        SeasonRecord rec = record(uuid);
        return rec == null ? 0 : rec.bestElo();
    }

    public int trophies(UUID uuid) {
        return uuid == null ? 0 : trophies.getOrDefault(uuid, 0);
    }

    public PendingReward pendingReward(UUID uuid) {
        return uuid == null ? null : pending.get(uuid);
    }

    public boolean isProvisional(UUID uuid) {
        int required = placementsRequired();
        if (required <= 0) {
            return false;
        }
        SeasonRecord rec = record(uuid);
        return rec == null || rec.placementsDone() < required;
    }

    public int placementsRemaining(UUID uuid) {
        SeasonRecord rec = record(uuid);
        int done = rec == null ? 0 : Math.min(rec.placementsDone(), placementsRequired());
        return Math.max(0, placementsRequired() - done);
    }

    /**
     * Ranked ELO deltas are <b>flat</b> (Round 27): +15 for a win, -5 for a loss, placements
     * included. This hook stays so season code keeps a single place to reason about the rating.
     */
    public int scaleRankedDelta(PlayerProfile profile, int rawDelta) {
        if (!settings().enabled() || rawDelta == 0) {
            return rawDelta;
        }
        SeasonRecord rec = profile == null ? null : record(profile.uuid());
        int done = rec == null ? 0 : rec.placementsDone();
        return SeasonMath.applyPlacementSwing(rawDelta, done, placementsRequired());
    }

    public SeasonResult onRankedMatch(PlayerProfile profile, int beforeElo, int afterElo, boolean won) {
        if (profile == null || !settings().enabled()) {
            return null;
        }
        SeasonRecord rec = records.computeIfAbsent(
            profile.uuid(),
            id -> new SeasonRecord(id, profile.lastKnownName(), SeasonMath.clampElo(beforeElo))
        );
        rec.setName(profile.lastKnownName());
        Rank tierBefore = SeasonMath.tierFor(rec.bestElo());
        int bestBefore = rec.bestElo();
        rec.noteMatch(won, afterElo);
        Rank tierAfter = SeasonMath.tierFor(rec.bestElo());
        dirty = true;
        return new SeasonResult(
            Math.min(rec.placementsDone(), placementsRequired()),
            placementsRequired(),
            rec.matches(),
            rec.wins(),
            rec.bestElo(),
            tierAfter,
            rec.placementsDone() < placementsRequired(),
            tierAfter != tierBefore,
            rec.bestElo() > bestBefore
        );
    }

    public List<SeasonStanding> standings(int limit) {
        List<SeasonRecord> played = new ArrayList<>();
        for (SeasonRecord rec : records.values()) {
            if (rec.hasPlayed()) {
                played.add(rec);
            }
        }
        played.sort(STANDING_ORDER);
        int size = limit <= 0 ? played.size() : Math.min(limit, played.size());
        List<SeasonStanding> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            SeasonRecord rec = played.get(i);
            out.add(new SeasonStanding(
                i + 1,
                rec.uuid(),
                rec.name(),
                rec.bestElo(),
                rec.matches(),
                rec.wins(),
                SeasonMath.tierFor(rec.bestElo()),
                rec.placementsDone() < placementsRequired()
            ));
        }
        return out;
    }

    public String seasonStatusLine(PlayerProfile profile) {
        SeasonState current = state;
        if (current == null || profile == null) {
            return "";
        }
        if (isProvisional(profile.uuid())) {
            return "<" + Palette.GOLD + ">Placements " + Math.min(placementsRequired(), placementsDone(profile.uuid()))
                + "/" + placementsRequired() + "</" + Palette.GOLD + ">";
        }
        SeasonRecord rec = record(profile.uuid());
        int best = rec == null ? profile.elo() : rec.bestElo();
        return "<" + Palette.SUCCESS + ">" + SeasonMath.tierFor(best).plainDisplayName() + "</" + Palette.SUCCESS + "> <#94A3B8>· " + best + " peak</#94A3B8>";
    }

    private int placementsDone(UUID uuid) {
        SeasonRecord rec = record(uuid);
        return rec == null ? 0 : rec.placementsDone();
    }

    public String footerLabel() {
        SeasonState current = state;
        if (current == null) {
            return "";
        }
        return current.name() + " · " + current.remainingLabel();
    }

    public synchronized SeasonArchive rollover(String reason) {
        SeasonState closing = state;
        if (closing == null || rollingOver) {
            return null;
        }
        rollingOver = true;
        try {
            long endedAt = System.currentTimeMillis();
            List<SeasonRecord> snapshot = new ArrayList<>();
            for (SeasonRecord rec : records.values()) {
                if (rec.hasPlayed()) {
                    snapshot.add(rec);
                }
            }
            snapshot.sort(STANDING_ORDER);

            Map<UUID, Integer> placeByUuid = new LinkedHashMap<>();
            List<SeasonStanding> standings = new ArrayList<>();
            for (int i = 0; i < snapshot.size(); i++) {
                SeasonRecord rec = snapshot.get(i);
                placeByUuid.put(rec.uuid(), i + 1);
                if (i < 10) {
                    standings.add(new SeasonStanding(
                        i + 1,
                        rec.uuid(),
                        rec.name(),
                        rec.bestElo(),
                        rec.matches(),
                        rec.wins(),
                        SeasonMath.tierFor(rec.bestElo()),
                        rec.placementsDone() < placementsRequired()
                    ));
                }
            }

            SeasonArchive archive = new SeasonArchive(
                closing.index(),
                closing.name(),
                closing.startedAtMillis(),
                endedAt,
                snapshot.size(),
                standings
            );
            writeArchive(archive, reason);

            for (SeasonRecord rec : snapshot) {
                int place = placeByUuid.getOrDefault(rec.uuid(), 0);
                Rank tier = SeasonMath.tierFor(rec.bestElo());
                int tierIndex = SeasonMath.competitiveTierIndex(tier);
                int coins = tierCoins(tierIndex) + topBonus(place);
                int xp = tierXp(tierIndex) + topBonusXp(place);
                if (coins > 0 || xp > 0) {
                    grant(rec.uuid(), rec.name(), coins, xp, closing.name() + " · " + tier.plainDisplayName()
                        + (place > 0 && place <= 3 ? " · #" + place : ""));
                }
                if (place > 0 && place <= 3) {
                    trophies.merge(rec.uuid(), 1, Integer::sum);
                }
                // Round 84 (economy) — the screenshot card. The broadcast archive tells the server
                // who won; this tells ONE player what their season was, in a form that survives
                // being posted in Discord. It is the whole season's artefact, so it goes to every
                // player who played, podium or not.
                sendSeasonSummary(rec, place, tier, tierIndex, closing);
            }

            int anchor = settings().softResetAnchor();
            double keep = settings().softResetKeepRatio();
            for (PlayerProfile profile : plugin.statsManager().allProfiles()) {
                int reset = SeasonMath.softReset(profile.elo(), anchor, keep);
                profile.setElo(reset);
                plugin.statsManager().markDirty(profile.uuid());
                SeasonRecord rec = records.get(profile.uuid());
                if (rec != null) {
                    rec.resetForNewSeason(reset);
                }
                if (plugin.sidebarManager() != null) {
                    plugin.sidebarManager().clearPlayerCache(profile.uuid());
                }
                if (plugin.tabListManager() != null) {
                    plugin.tabListManager().clearPlayerCache(profile.uuid());
                }
            }
            // Round 27: a soft reset moves everyone's ELO, so the top-3 pills must be recomputed.
            plugin.statsManager().refreshLeaderboardBadges();

            int nextIndex = closing.index() + 1;
            long[] window = SeasonMath.seasonWindow(nextIndex,
                SeasonMath.parseDateMillis(settings().startDate()),
                SeasonMath.parseDateMillis(settings().endDate()),
                durationMillis());
            state = window[0] > 0 && window[1] > window[0]
                ? new SeasonState(nextIndex, SeasonMath.seasonName(nextIndex), window[0], window[1])
                : SeasonState.fresh(nextIndex, endedAt, durationMillis());
            dirty = true;
            saveNow();

            logger.info("[Season] " + closing.name() + " closed (" + reason + ") with " + snapshot.size()
                + " ranked players — " + state.name() + " is live until " + new java.util.Date(state.endsAtMillis()));

            broadcastArchive(archive, closing);
            return archive;
        } finally {
            rollingOver = false;
        }
    }

    public synchronized void restartSeason(int index) {
        records.clear();
        pending.clear();
        pendingSummaries.clear();
        long now = System.currentTimeMillis();
        int seasonIndex = Math.max(1, index);
        long[] window = SeasonMath.seasonWindow(seasonIndex,
            SeasonMath.parseDateMillis(settings().startDate()),
            SeasonMath.parseDateMillis(settings().endDate()),
            durationMillis());
        state = window[0] > 0 && window[1] > window[0]
            ? new SeasonState(seasonIndex, SeasonMath.seasonName(seasonIndex), window[0], window[1])
            : SeasonState.fresh(seasonIndex, now, durationMillis());
        dirty = true;
        saveNow();
    }

    public void setEndsAt(long endsAtMillis) {
        SeasonState current = state;
        if (current == null) {
            return;
        }
        state = new SeasonState(current.index(), current.name(), current.startedAtMillis(),
            Math.max(current.startedAtMillis() + 60_000L, endsAtMillis));
        dirty = true;
        saveNow();
    }

    private int tierCoins(int tierIndex) {
        return pick(settings().tierCoins(), tierIndex, 0);
    }

    private int tierXp(int tierIndex) {
        return pick(settings().tierXp(), tierIndex, 0);
    }

    private int topBonus(int place) {
        BosketConfig.Season s = settings();
        return switch (place) {
            case 1 -> Math.max(0, s.top1Coins());
            case 2 -> Math.max(0, s.top2Coins());
            case 3 -> Math.max(0, s.top3Coins());
            default -> 0;
        };
    }

    private int topBonusXp(int place) {
        return topBonus(place) / 3;
    }

    private static int pick(List<Integer> values, int index, int fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        int safe = Math.clamp(index, 0, values.size() - 1);
        Integer value = values.get(safe);
        return value == null ? fallback : Math.max(0, value);
    }

    /**
     * Delivers the end-of-season card to one player.
     *
     * <p>Round 84 (economy): the season used to end with a server-wide broadcast — "SEASON I CLOSED",
     * the top three, "soft reset applied". Nothing told an individual player what their season
     * was. The card does that: one framed card, no second title, no second toast, so the moment
     * belongs to the card and the card can be screenshotted.
     *
     * <p>An offline player gets the reward in their pending mailbox and the card when they next
     * claim it — the summary is rebuilt from the record rather than stored, so a season that ended
     * while the player was away still produces the right card.
     */
    private void sendSeasonSummary(SeasonRecord rec, int place, Rank tier, int tierIndex, SeasonState closing) {
        if (rec == null) {
            return;
        }
        Player online = Bukkit.getPlayer(rec.uuid());
        if (online == null || !online.isOnline()) {
            // Offline: the reward waits in the mailbox and the card is rebuilt on claim.
            pendingSummaries.put(rec.uuid(), new SeasonSummary(
                rec.name(), closing.name(), closing.index(), place, rec.bestElo(), tier,
                rec.matches(), rec.wins(), tierCoins(tierIndex), topBonus(place)));
            return;
        }
        SeasonSummary summary = new SeasonSummary(
            rec.name(), closing.name(), closing.index(), place, rec.bestElo(), tier,
            rec.matches(), rec.wins(), tierCoins(tierIndex), topBonus(place));
        deliverSummary(online, summary);
    }

    private void deliverSummary(Player player, SeasonSummary summary) {
        if (player == null || summary == null || !summary.isScreenshotWorthy()) {
            return;
        }
        Msg.sendFramedCard(player, summary.header(), summary.card());
    }

    private void grant(UUID uuid, String name, int coins, int xp, String label) {
        PlayerProfile online = plugin.statsManager().getIfPresent(uuid);
        if (online != null) {
            online.addEarnedCoins(coins);
            online.addXp(xp);
            plugin.statsManager().markDirty(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Msg.send(player, Msg.success("Season reward paid: <" + Palette.GOLD_SOFT + ">+" + coins + " Coins</" + Palette.GOLD_SOFT + "> <#64748B>·</#64748B> <" + Palette.ICE + ">+" + xp + " XP</" + Palette.ICE + "> <#94A3B8>(" + label + ")</#94A3B8>"));
            }
            return;
        }
        pending.merge(uuid, new PendingReward(Math.max(0, coins), Math.max(0, xp), label),
            (oldValue, newValue) -> oldValue.merge(newValue.coins(), newValue.xp(), newValue.label()));
        dirty = true;
    }

    public int claimPending(Player player) {
        if (player == null) {
            return 0;
        }
        PendingReward reward = pending.remove(player.getUniqueId());
        if (reward == null) {
            return 0;
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        profile.addEarnedCoins(reward.coins());
        profile.addXp(reward.xp());
        plugin.statsManager().markDirty(player.getUniqueId());
        dirty = true;
        saveAsync();
        Msg.sendFramedCard(player, "Season Rewards", List.of(
            "<#94A3B8>Your <#F8FAFC>" + (reward.label() == null ? "season" : reward.label()) + "</#F8FAFC> payout is in:</#94A3B8>",
            "<" + Palette.GOLD_SOFT + ">+" + reward.coins() + " Coins</" + Palette.GOLD_SOFT + "> <#64748B>·</#64748B> <" + Palette.ICE + ">+" + reward.xp() + " XP</" + Palette.ICE + ">",
            "<#94A3B8>Say <#FDE047>/season</#FDE047> to see the new ladder.</#94A3B8>"
        ));
        // The screenshot card, for a player whose season ended while they were away.
        SeasonSummary summary = pendingSummaries.remove(player.getUniqueId());
        if (summary != null) {
            deliverSummary(player, summary);
        }
        return reward.coins();
    }

    private void broadcastArchive(SeasonArchive archive, SeasonState closing) {
        List<String> lines = new ArrayList<>();
        if (archive.standings().isEmpty()) {
            lines.add("<#94A3B8>No ranked matches were played this season.</#94A3B8>");
        } else {
            String[] medal = {"<" + Palette.GOLD_SOFT + ">#1</" + Palette.GOLD_SOFT + ">", "<" + Palette.MUTED + ">#2</" + Palette.MUTED + ">", "<" + Palette.AMBER + ">#3</" + Palette.AMBER + ">"};
            for (int i = 0; i < Math.min(3, archive.standings().size()); i++) {
                SeasonStanding standing = archive.standings().get(i);
                lines.add(medal[i] + " <#F8FAFC><bold>" + standing.name() + "</bold></#F8FAFC> <#94A3B8>·</#94A3B8> <" + Palette.ICE + ">"
                    + standing.bestElo() + " peak</" + Palette.ICE + "> <#64748B>·</#64748B> <#94A3B8>"
                    + standing.matches() + " games</#94A3B8>");
            }
            lines.add("");
            lines.add("<#94A3B8>Participants:</#94A3B8> <#F8FAFC>" + archive.participants() + "</#F8FAFC> <#64748B>·</#64748B> <#94A3B8>Soft reset applied — placements reopen.</#94A3B8>");
        }
        String header = closing.name() + " — FINAL WHISTLE";
        for (Player player : Bukkit.getOnlinePlayers()) {
            Msg.sendFramedCard(player, header, lines);
            Msg.title(player, "<" + Palette.WARN + "><bold>" + closing.name() + " CLOSED</bold></" + Palette.WARN + ">",
                "<#94A3B8>" + state.name() + " is live — placements reset.</#94A3B8>", 200, 3200, 500);
            Msg.playPackSound(player.getLocation(), "crowd.cheer", 1.1f, 1.0f);
        }
        if (plugin.integration() != null && plugin.integration().webhook() != null) {
            try {
                plugin.integration().webhook().sendCard(0xFF8C1A, header, lines, "BosketBall Ranked", null);
            } catch (Throwable ignored) {
                logger.fine("[Season] Discord archive post skipped.");
            }
        }
    }

    private void writeArchive(SeasonArchive archive, String reason) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("index", archive.index());
        yml.set("name", archive.name());
        yml.set("started-at", archive.startedAtMillis());
        yml.set("ended-at", archive.endedAtMillis());
        yml.set("reason", reason);
        yml.set("participants", archive.participants());
        yml.set("soft-reset.anchor", settings().softResetAnchor());
        yml.set("soft-reset.keep-ratio", settings().softResetKeepRatio());
        for (SeasonStanding standing : archive.standings()) {
            String base = "standings." + standing.place();
            yml.set(base + ".name", standing.name());
            yml.set(base + ".best-elo", standing.bestElo());
            yml.set(base + ".matches", standing.matches());
            yml.set(base + ".wins", standing.wins());
            yml.set(base + ".tier", standing.tier().name());
            yml.set(base + ".provisional", standing.provisional());
        }
        File target = new File(archiveDir, String.format(Locale.ROOT, "season-%02d.yml", archive.index()));
        String header = "# BosketBall ranked season archive — written automatically, never edit by hand\n";
        AtomicYamlWriter.writeYamlAtomically(target, header, yml, logger);
    }

    public void saveAsync() {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration yml = buildYaml();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AtomicYamlWriter.writeYamlAtomically(currentFile, HEADER, yml, logger);
        });
    }

    public void saveNow() {
        dirty = false;
        AtomicYamlWriter.writeYamlAtomically(currentFile, HEADER, buildYaml(), logger);
    }

    private static final String HEADER = "# BosketBall ranked season state — managed by SeasonService, do not edit while the server runs\n";

    private YamlConfiguration buildYaml() {
        SeasonState current = state;
        YamlConfiguration yml = new YamlConfiguration();
        if (current != null) {
            yml.set("season.index", current.index());
            yml.set("season.name", current.name());
            yml.set("season.started-at", current.startedAtMillis());
            yml.set("season.ends-at", current.endsAtMillis());
        }
        yml.set("season.placements-required", placementsRequired());
        for (Map.Entry<UUID, SeasonRecord> entry : records.entrySet()) {
            SeasonRecord rec = entry.getValue();
            String base = "players." + entry.getKey();
            yml.set(base + ".name", rec.name());
            yml.set(base + ".matches", rec.matches());
            yml.set(base + ".wins", rec.wins());
            yml.set(base + ".best-elo", rec.bestElo());
            yml.set(base + ".placements", rec.placementsDone());
            yml.set(base + ".coins-earned", rec.coinsEarned());
            yml.set(base + ".xp-earned", rec.xpEarned());
        }
        for (Map.Entry<UUID, PendingReward> entry : pending.entrySet()) {
            String base = "pending." + entry.getKey();
            yml.set(base + ".coins", entry.getValue().coins());
            yml.set(base + ".xp", entry.getValue().xp());
            yml.set(base + ".label", entry.getValue().label());
        }
        for (Map.Entry<UUID, Integer> entry : trophies.entrySet()) {
            yml.set("trophies." + entry.getKey(), entry.getValue());
        }
        return yml;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
