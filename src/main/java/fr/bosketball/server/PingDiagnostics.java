package fr.bosketball.server;

import fr.bosketball.game.GameManager;
import fr.bosketball.pack.ResourcePackHostService;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Per-player latency surveillance.
 *
 * <p>Minecraft measures latency with the keep-alive round trip, which is exactly what a player
 * feels as "ping". One sample per second per player is kept in a small ring buffer so a staff
 * member can ask <em>why one specific player lags while everybody else is fine</em> and get an
 * answer instead of a guess: current/average/min/max ping, jitter, client brand, client view
 * distance, whether a resource-pack download is still running (a 3.4 MB pack on a slow ADSL line
 * looks exactly like "ping de malade"), and the same numbers for everybody else online.
 *
 * <p>When a player stays above {@link #HIGH_PING_MS} for {@link #SUSTAINED_SAMPLES} consecutive
 * seconds the console gets one warning line (at most one per {@link #ALERT_COOLDOWN_MS}) so the
 * problem is traceable after the fact even if nobody typed a command at the time.
 *
 * <p>Cost: 30 ints per player, one {@code getPing()} call per player per second — no packets are
 * sent, nothing is allocated per tick.
 */
public final class PingDiagnostics {

    /** Samples kept per player (one per second). */
    private static final int WINDOW = 30;
    /** Latency above which a connection is considered degraded (ms). */
    public static final int HIGH_PING_MS = 250;
    /** Consecutive seconds above {@link #HIGH_PING_MS} before the console is warned. */
    private static final int SUSTAINED_SAMPLES = 6;
    private static final long ALERT_COOLDOWN_MS = 5L * 60L * 1000L;

    private final Map<UUID, Series> series = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();
    private final Logger logger;
    private final ResourcePackHostService packHost;
    private final GameManager gameManager;

    public PingDiagnostics(Logger logger, ResourcePackHostService packHost, GameManager gameManager) {
        this.logger = logger;
        this.packHost = packHost;
        this.gameManager = gameManager;
    }

    /** Ring buffer of the last {@link #WINDOW} latency samples of one player. */
    public static final class Series {
        private final int[] samples = new int[WINDOW];
        private int cursor;
        private int count;

        public void add(int ping) {
            samples[cursor] = Math.max(0, ping);
            cursor = (cursor + 1) % WINDOW;
            if (count < WINDOW) {
                count++;
            }
        }

        public int count() {
            return count;
        }

        /** Oldest first. */
        public List<Integer> ordered() {
            List<Integer> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                out.add(samples[(cursor - count + i + WINDOW * 2) % WINDOW]);
            }
            return out;
        }

        public int last() {
            return count == 0 ? 0 : samples[(cursor - 1 + WINDOW) % WINDOW];
        }

        public int average() {
            if (count == 0) {
                return 0;
            }
            int sum = 0;
            for (int value : ordered()) {
                sum += value;
            }
            return Math.round((float) sum / count);
        }

        public int min() {
            int best = Integer.MAX_VALUE;
            for (int value : ordered()) {
                best = Math.min(best, value);
            }
            return best == Integer.MAX_VALUE ? 0 : best;
        }

        public int max() {
            int worst = 0;
            for (int value : ordered()) {
                worst = Math.max(worst, value);
            }
            return worst;
        }

        /** Mean absolute difference between consecutive samples — the "rubber band" factor. */
        public int jitter() {
            List<Integer> ordered = ordered();
            if (ordered.size() < 2) {
                return 0;
            }
            long total = 0;
            for (int i = 1; i < ordered.size(); i++) {
                total += Math.abs(ordered.get(i) - ordered.get(i - 1));
            }
            return Math.round((float) total / (ordered.size() - 1));
        }

        public boolean sustainedAbove(int threshold, int consecutive) {
            List<Integer> ordered = ordered();
            if (ordered.size() < consecutive) {
                return false;
            }
            for (int i = ordered.size() - consecutive; i < ordered.size(); i++) {
                if (ordered.get(i) < threshold) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Called once per second from the UI pipeline. */
    public void sample() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Series buffer = series.computeIfAbsent(player.getUniqueId(), id -> new Series());
            buffer.add(player.getPing());
            if (buffer.sustainedAbove(HIGH_PING_MS, SUSTAINED_SAMPLES)) {
                warnOnce(player, buffer);
            }
        }
        series.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        lastAlert.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void warnOnce(Player player, Series buffer) {
        long now = System.currentTimeMillis();
        Long previous = lastAlert.get(player.getUniqueId());
        if (previous != null && now - previous < ALERT_COOLDOWN_MS) {
            return;
        }
        lastAlert.put(player.getUniqueId(), now);
        if (logger == null) {
            return;
        }
        logger.warning(String.format(Locale.ROOT,
            "[Net] %s has been above %d ms for %ds — now %d ms (avg %d, max %d, jitter %d ms)."
                + " Client %s, view %d/%d, pack %s. Compare with /bb net %s.",
            player.getName(), HIGH_PING_MS, SUSTAINED_SAMPLES, buffer.last(), buffer.average(),
            buffer.max(), buffer.jitter(), clientBrand(player), player.getClientViewDistance(),
            player.getSimulationDistance(), packState(player), player.getName()));
    }

    /** Forgets a player that left the server. */
    public void forget(UUID playerId) {
        series.remove(playerId);
        lastAlert.remove(playerId);
    }

    /** The debug card shown by {@code /bb net [player]}. */
    public List<String> card(Player target) {
        Series buffer = series.get(target.getUniqueId());
        int live = target.getPing();
        List<String> lines = new ArrayList<>();
        if (buffer == null || buffer.count() == 0) {
            lines.add(" <gray>▸ Latency:</gray> " + pingTag(live)
                + " <dark_gray>(collecting samples — one per second)</dark_gray>");
        } else {
            lines.add(" <gray>▸ Latency:</gray> " + pingTag(buffer.last())
                + " <dark_gray>•</dark_gray> <gray>avg</gray> <white>" + buffer.average() + " ms</white>"
                + " <dark_gray>•</dark_gray> <gray>min</gray> <white>" + buffer.min() + " ms</white>"
                + " <dark_gray>•</dark_gray> <gray>max</gray> <white>" + buffer.max() + " ms</white>"
                + " <dark_gray>•</dark_gray> <gray>jitter</gray> " + jitterTag(buffer.jitter()));
        }
        lines.add(" <gray>▸ Client:</gray> <white>" + clientBrand(target) + "</white>"
            + " <dark_gray>•</dark_gray> <gray>view</gray> <white>" + target.getClientViewDistance() + "</white>"
            + " <dark_gray>/</dark_gray> <white>" + target.getSimulationDistance() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>pack</gray> " + packState(target));
        lines.add(" <gray>▸ Server:</gray> <white>TPS " + String.format(Locale.ROOT, "%.2f", Bukkit.getTPS()[0])
            + "</white> <dark_gray>•</dark_gray> <gray>MSPT</gray> <white>"
            + String.format(Locale.ROOT, "%.2f ms", fr.bosketball.util.TickProfiler.lastTickNanos() / 1_000_000.0) + "</white>"
            + " <dark_gray>•</dark_gray> <gray>online</gray> <white>" + Bukkit.getOnlinePlayers().size() + "</white>"
            + " <dark_gray>•</dark_gray> <gray>view-distance</gray> <white>"
            + Bukkit.getViewDistance() + "</white>");
        lines.add(" <gray>▸ Everyone:</gray> " + comparisonLine(target));
        lines.add(" <gray>▸ Verdict:</gray> " + verdict(target, buffer));
        if (gameManager != null) {
            var session = gameManager.getSessionOf(target.getUniqueId());
            if (session != null) {
                lines.add(" <dark_gray>Match in progress — rubber-banding is amplified by packet loss,"
                    + " not by the tick rate (" + String.format(Locale.ROOT, "%.2f", Bukkit.getTPS()[0]) + " TPS).</dark_gray>");
            }
        }
        return lines;
    }

    private String comparisonLine(Player target) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparingInt(Player::getPing).reversed());
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Player other : online) {
            if (shown++ > 0) {
                sb.append(" <dark_gray>•</dark_gray> ");
            }
            if (shown > 6) {
                sb.append("<gray>+").append(online.size() - 5).append(" more</gray>");
                break;
            }
            boolean isTarget = other.getUniqueId().equals(target.getUniqueId());
            sb.append(isTarget ? "<#FFD43B>" : "<gray>").append(other.getName()).append(' ')
                .append(other.getPing()).append(" ms").append(isTarget ? "</#FFD43B>" : "</gray>");
        }
        return sb.toString();
    }

    /**
     * Turns the numbers into one sentence a staff member can act on. The interesting case is
     * "one player is bad, everybody else is fine" — that is never a server problem.
     */
    String verdict(Player target, Series buffer) {
        int own = buffer == null || buffer.count() == 0 ? target.getPing() : buffer.average();
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        int othersAbove = 0;
        int others = 0;
        long othersSum = 0;
        for (Player other : online) {
            if (other.getUniqueId().equals(target.getUniqueId())) {
                continue;
            }
            others++;
            othersSum += other.getPing();
            if (other.getPing() >= HIGH_PING_MS) {
                othersAbove++;
            }
        }
        int othersAverage = others == 0 ? 0 : Math.round((float) othersSum / others);
        boolean packPending = packHost != null && !"idle".equals(packHost.attemptSummary(target.getUniqueId()));
        if (packPending) {
            return "<#FBBF24>Resource pack still downloading</#FBBF24> — the 3.4 MB transfer competes with"
                + " gameplay traffic on this line; the ping drops back by itself once it is cached.";
        }
        if (own >= HIGH_PING_MS && othersAbove == 0) {
            return "<#F87171>Isolated to this connection</#F87171> — " + target.getName() + " averages "
                + own + " ms while the " + others + " other player(s) average " + othersAverage
                + " ms. Look at the player's side (Wi-Fi/4G, VPN, ISP peering), not at the server.";
        }
        if (own >= HIGH_PING_MS) {
            return "<#FBBF24>Shared degradation</#FBBF24> — " + othersAbove + "/" + others
                + " players are also above " + HIGH_PING_MS + " ms (average " + othersAverage
                + " ms): check the host link, not one player.";
        }
        return "<#4ADE80>Healthy</#4ADE80> — " + own + " ms average, jitter "
            + (buffer == null ? 0 : buffer.jitter()) + " ms.";
    }

    public String pingTag(int ping) {
        String colour = ping < 80 ? "#4ADE80" : ping < 150 ? "#FDE047" : ping < HIGH_PING_MS ? "#FB923C" : "#F87171";
        return "<" + colour + "><bold>" + ping + " ms</bold></" + colour + ">";
    }

    private String jitterTag(int jitter) {
        String colour = jitter < 20 ? "#4ADE80" : jitter < 60 ? "#FDE047" : "#F87171";
        return "<" + colour + ">" + jitter + " ms</" + colour + ">";
    }

    private String packState(Player player) {
        if (packHost == null) {
            return "<dark_gray>unknown</dark_gray>";
        }
        String attempt = packHost.attemptSummary(player.getUniqueId());
        if ("idle".equals(attempt)) {
            return "<#4ADE80>loaded</#4ADE80>";
        }
        return "<#FBBF24>downloading</#FBBF24> <dark_gray>(" + Msg.escapeMini(attempt) + ")</dark_gray>";
    }

    private static String clientBrand(Player player) {
        try {
            String brand = player.getClientBrandName();
            return brand == null || brand.isBlank() ? "unknown" : brand;
        } catch (RuntimeException failure) {
            return "unknown";
        }
    }
}
