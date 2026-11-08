package fr.bosketball.server;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.util.Msg;
import fr.bosketball.util.Palette;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The surface layer of the first ten minutes: one action-bar line, one card, one held hotbar slot.
 *
 * <p>Round 58 gave every toast a 1.6 s hold on the action bar so the aim HUD, which repaints every
 * tick, cannot erase it. Onboarding has to respect that hold in both directions: it must not write
 * over a shot-clock toast, and its own line must be written through {@link Msg#rail} so the aim HUD
 * cannot erase it in the same tick. {@link RailGuard} is the pure decision behind both rules.
 *
 * <p>The director is deliberately quiet while the guided tour is running — the tour owns the boss
 * bar, the title and the chapter card. It only speaks into dead air: when a stage is unresolved, no
 * tour is running, no rail is on screen and the cue interval has elapsed. That is what makes a
 * skipped tutorial, a busy practice court or a long queue harmless: there is always one next action.
 */
public final class OnboardingCues {

    /** A card stays on screen long enough that a second card would read as spam. */
    public static final long CARD_GAP_MS = 90_000L;
    /** The one card title onboarding ever shows. Player-facing, so it is style-tested. */
    public static final String CARD_TITLE = "FIRST TEN MINUTES";
    private static final Map<UUID, Long> LAST_CARD = new ConcurrentHashMap<>();

    private OnboardingCues() {
    }

    /** Pure decision: may onboarding take the action bar right now? */
    public static final class RailGuard {
        private RailGuard() {
        }

        /**
         * @param railOnScreen true while a shot-clock / HUD toast still owns the action bar
         * @param minGapMs     minimum time between two onboarding cues
         */
        public static boolean mayCue(boolean railOnScreen, long nowMs, long lastCueMs, long minGapMs) {
            if (railOnScreen) {
                return false;
            }
            if (minGapMs <= 0L) {
                return false;
            }
            return lastCueMs <= 0L || nowMs - lastCueMs >= minGapMs;
        }

        /** True when a card was shown recently enough that a new one would stack on top of it. */
        public static boolean cardIsRecent(long nowMs, long lastCardMs, long gapMs) {
            return lastCardMs > 0L && nowMs - lastCardMs < gapMs;
        }
    }

    /** Records that a card was just shown (join card, chapter card, completion card). */
    public static void noteCard(UUID playerId) {
        if (playerId != null) {
            LAST_CARD.put(playerId, System.currentTimeMillis());
        }
    }

    /** Forgets a player who left; the map must not grow with every join. */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            LAST_CARD.remove(playerId);
        }
    }

    /**
     * Sends one guarded action-bar line. Yields to a live toast, and uses {@link Msg#rail} so the
     * line keeps the rail for 1.6 s against the per-tick aim HUD.
     *
     * @return true when the line was actually shown
     */
    public static boolean cue(Player player, String miniMessage, long lastCueMs) {
        if (player == null || miniMessage == null || miniMessage.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (!RailGuard.mayCue(Msg.railOnScreen(player), now, lastCueMs, OnboardingScript.DEAD_AIR_LIMIT_MS)) {
            return false;
        }
        Msg.rail(player, miniMessage);
        return true;
    }

    /** The one-line rail copy for a beat: what is happening, then what to do next. */
    public static String railLine(OnboardingScript.Beat beat, String action) {
        return "<" + Palette.GOLD_SOFT + ">▸</" + Palette.GOLD_SOFT + "> " + beat.copy()
            + " " + ScreenBounds.separator() + " <" + Palette.TEXT + ">" + action + "</" + Palette.TEXT + ">";
    }

    /** One card per stage change, never stacked on the join card or the completion card. */
    public static void card(Player player, OnboardingScript.Stage stage,
                            OnboardingScript.Beat beat, String action) {
        if (player == null || beat == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("<" + Palette.MUTED + ">" + stage.name().replace('_', ' ') + "</" + Palette.MUTED + "> "
            + ScreenBounds.separator() + " " + beat.copy());
        lines.add("<" + Palette.GOLD_SOFT + ">▸ " + action + "</" + Palette.GOLD_SOFT + ">"
            + " " + ScreenBounds.separator() + " <" + Palette.MUTED + ">" + beat.visualCue()
            + "</" + Palette.MUTED + ">");
        if (stage == OnboardingScript.Stage.FIRST_MATCH) {
            // Every social surface gets its reason at the moment it is useful: before the first
            // tip-off, not buried in a lobby menu. One card, four short lines.
            for (fr.bosketball.social.SocialSurfaces.Surface surface
                : fr.bosketball.social.SocialSurfaces.Surface.values()) {
                lines.add(fr.bosketball.social.SocialSurfaces.reasonInMatch(surface) + " "
                    + ScreenBounds.separator() + " <" + Palette.GOLD + ">"
                    + fr.bosketball.social.SocialSurfaces.empty(surface).nextAction()
                    + "</" + Palette.GOLD + ">");
            }
        }
        Msg.sendFramedCard(player, CARD_TITLE, lines);
        noteCard(player.getUniqueId());
    }

    /**
     * Every line onboarding can put on screen, for the house-style test: the card title, the stage
     * labels and the rail line for each beat in each of its four states (normal, skipped, in match).
     */
    public static List<String> allCopy() {
        List<String> out = new ArrayList<>();
        out.add(CARD_TITLE);
        for (OnboardingScript.Stage stage : OnboardingScript.Stage.values()) {
            out.add(stage.name().replace('_', ' '));
        }
        for (OnboardingScript.Beat beat : OnboardingScript.SCRIPT) {
            for (boolean skipped : new boolean[]{false, true}) {
                for (boolean inMatch : new boolean[]{false, true}) {
                    out.add(railLine(beat, OnboardingScript.nextAction(beat.stage(), skipped, inMatch)));
                }
            }
        }
        return out;
    }

    /**
     * Drives the script from real profile state. Instantiated once by the join listener; the tick
     * only walks players whose first ten minutes are still unresolved.
     */
    public static final class Director {
        private final BosketBallPlugin plugin;
        private final Map<UUID, State> tracked = new ConcurrentHashMap<>();

        private static final class State {
            private final long joinedAt = System.currentTimeMillis();
            private long lastCueAt;
            private OnboardingScript.Stage stage;
            private boolean identitySeen;
        }

        public Director(BosketBallPlugin plugin) {
            this.plugin = plugin;
        }

        /**
         * One repeating task for the whole server, once a second. Players who are already online
         * when the listener is built (a reload) are seeded too, otherwise they would spend the rest
         * of their session with no director at all. A player who already finished the tour keeps
         * the whole court and is left alone.
         */
        public void start() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerProfile profile = plugin.statsManager() == null ? null
                    : plugin.statsManager().getIfPresent(player.getUniqueId());
                if (profile != null && profile.tutorialCompleted()) {
                    continue;
                }
                State state = new State();
                state.identitySeen = profile != null;
                tracked.put(player.getUniqueId(), state);
            }
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }

        public void onJoin(Player player) {
            if (player == null) {
                return;
            }
            State state = new State();
            tracked.put(player.getUniqueId(), state);
            applyVisualCue(player, OnboardingScript.Stage.JOIN);
            noteCard(player.getUniqueId());   // the illustrated welcome card is the join surface
        }

        /** Called once the welcome card has actually been delivered, not when it was scheduled. */
        public void markIdentitySeen(UUID playerId) {
            State state = tracked.get(playerId);
            if (state != null) {
                state.identitySeen = true;
            }
        }

        public void onQuit(UUID playerId) {
            tracked.remove(playerId);
            forget(playerId);
        }

        public void tick() {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, State> entry : tracked.entrySet()) {
                UUID id = entry.getKey();
                Player player = Bukkit.getPlayer(id);
                if (player == null || !player.isOnline()) {
                    tracked.remove(id);
                    forget(id);
                    continue;
                }
                State state = entry.getValue();
                if (now - state.joinedAt > OnboardingScript.TOTAL_MINUTES * 60_000L) {
                    tracked.remove(id);
                    forget(id);
                    continue;
                }
                OnboardingScript.Stage stage = OnboardingScript.stageFor(progressOf(player, state, now));
                if (stage == OnboardingScript.Stage.DONE) {
                    tracked.remove(id);
                    continue;
                }
                boolean changed = stage != state.stage;
                if (changed) {
                    state.stage = stage;
                    state.lastCueAt = 0L;
                    applyVisualCue(player, stage);
                }
                boolean tourRunning = plugin.tutorialManager() != null
                    && plugin.tutorialManager().isRunning(player);
                boolean inMatch = plugin.gameManager() != null
                    && plugin.gameManager().isInMatch(id);
                if (stage == OnboardingScript.Stage.JOIN || tourRunning || inMatch) {
                    continue;                   // the MOTD, the tour or the court owns the screen
                }
                OnboardingScript.Beat beat = OnboardingScript.beat(stage);
                boolean skipped = now - state.joinedAt >= beat.fromMinute() * 60_000L;
                String action = OnboardingScript.nextAction(stage, skipped, false);
                if (cue(player, railLine(beat, action), state.lastCueAt)) {
                    state.lastCueAt = now;
                }
                if (changed && !RailGuard.cardIsRecent(now,
                    LAST_CARD.getOrDefault(id, 0L), CARD_GAP_MS)) {
                    card(player, stage, beat, action);
                }
            }
        }

        private OnboardingScript.Progress progressOf(Player player, State state, long now) {
            PlayerProfile profile = plugin.statsManager() == null
                ? null : plugin.statsManager().getIfPresent(player.getUniqueId());
            boolean shot = profile != null && (profile.twoPointersAttempted() > 0
                || profile.threePointersAttempted() > 0 || profile.dunks() > 0);
            boolean match = profile != null
                && (profile.rankedMatches() > 0 || profile.casualMatches() > 0);
            int start = plugin.settings() == null ? 0 : plugin.settings().general().startingCoins();
            boolean reward = profile != null && (profile.coins() > start || profile.tutorialCompleted());
            return new OnboardingScript.Progress(state.identitySeen, shot, match, reward,
                (now - state.joinedAt) / 60_000L);
        }

        /** Teaching by design: the held slot is the next action, no sentence required. */
        private void applyVisualCue(Player player, OnboardingScript.Stage stage) {
            if (plugin.gameManager() != null && plugin.gameManager().isInMatch(player.getUniqueId())) {
                return;
            }
            int slot = OnboardingScript.hotbarSlot(stage);
            if (slot >= 0 && slot <= 8 && player.getInventory().getHeldItemSlot() != slot) {
                player.getInventory().setHeldItemSlot(slot);
            }
        }
    }
}
