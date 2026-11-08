package fr.bosketball.server;

import fr.bosketball.util.Palette;
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
                out.add(at(i));
            }
            return out;
        }

        /**
         * Sample {@code i} of the window, oldest first, without building the list.
         *
         * <p>Round 42: {@code average()}, {@code min()} and {@code max()} all called
         * {@code ordered()} — a fresh {@code ArrayList} on a per-player, per-second path, for a sum
         * that needs no list at all. They now walk the ring directly.
         */
        private int at(int i) {
            return samples[Math.floorMod(cursor - count + i, WINDOW)];
        }

        public int last() {
            return count == 0 ? 0 : samples[(cursor - 1 + WINDOW) % WINDOW];
        }

        public int average() {
            if (count == 0) {
                return 0;
            }
            long sum = 0L;
            for (int i = 0; i < count; i++) {
                sum += at(i);
            }
            return (int) Math.round((double) sum / count);
        }

        public int min() {
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                best = Math.min(best, at(i));
            }
            return best == Integer.MAX_VALUE ? 0 : best;
        }

        public int max() {
            int worst = 0;
            for (int i = 0; i < count; i++) {
                worst = Math.max(worst, at(i));
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
        lines.add(" <gray>▸ Host TCP:</gray> " + hostTcpLine());
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

    /**
     * What the box actually does with a transatlantic TCP flow. Cubic + no MTU probing is the
     * classic reason a European player sees smooth pings but rubber-banding and slow chunks:
     * every lost packet halves the window instead of being probed around. Read straight from
     * /proc so the answer is a fact, not a guess, and can be pasted into a host ticket.
     */
    private static String hostTcpLine() {
        String cc = readProc("/proc/sys/net/ipv4/tcp_congestion_control", 24);
        if (cc.isEmpty()) {
            return "<dark_gray>not readable on this host (no /proc) - ask the provider to enable BBR</dark_gray>";
        }
        String qdisc = readProc("/proc/sys/net/core/default_qdisc", 24);
        String rmem = readProc("/proc/sys/net/core/rmem_max", 16);
        String mtuProbe = readProc("/proc/sys/net/ipv4/tcp_mtu_probing", 4);
        boolean bbr = cc.toLowerCase(Locale.ROOT).contains("bbr");
        String colour = bbr ? Palette.SUCCESS : Palette.DANGER;
        return "<" + colour + ">" + Msg.escapeMini(cc) + "</" + colour + ">"
            + " <dark_gray>•</dark_gray> <gray>qdisc</gray> <white>" + text(qdisc) + "</white>"
            + " <dark_gray>•</dark_gray> <gray>rmem_max</gray> <white>" + text(rmem) + "</white>"
            + " <dark_gray>•</dark_gray> <gray>mtu-probing</gray> <white>" + text(mtuProbe) + "</white>"
            + " <dark_gray>" + (bbr
                ? "(long-haul friendly)"
                : "(CUBIC: ask the host to switch to BBR, see docs/ping-sans-carte.md)")
            + "</dark_gray>";
    }

    private static String text(String value) {
        return value == null || value.isEmpty() ? "?" : Msg.escapeMini(value);
    }

    private static String readProc(String path, int maxChars) {
        try {
            java.nio.file.Path file = java.nio.file.Paths.get(path);
            if (!java.nio.file.Files.isReadable(file)) {
                return "";
            }
            String raw = new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8).trim();
            return fr.bosketball.util.Guards.truncate(raw, maxChars);
        } catch (Throwable ignored) {
            return "";
        }
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
            sb.append(isTarget ? "<" + Palette.GOLD_SOFT + ">" : "<gray>").append(other.getName()).append(' ')
                .append(other.getPing()).append(" ms").append(isTarget ? "</" + Palette.GOLD_SOFT + ">" : "</gray>");
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
            return "<" + Palette.GOLD + ">Resource pack still downloading</" + Palette.GOLD + "> — the 3.4 MB transfer competes with"
                + " gameplay traffic on this line; the ping drops back by itself once it is cached.";
        }
        if (own >= HIGH_PING_MS && othersAbove == 0) {
            return "<" + Palette.DANGER + ">Isolated to this connection</" + Palette.DANGER + "> — " + target.getName() + " averages "
                + own + " ms while the " + others + " other player(s) average " + othersAverage
                + " ms. Look at the player's side (Wi-Fi/4G, VPN, ISP peering), not at the server.";
        }
        if (own >= HIGH_PING_MS) {
            return "<" + Palette.GOLD + ">Shared degradation</" + Palette.GOLD + "> — " + othersAbove + "/" + others
                + " players are also above " + HIGH_PING_MS + " ms (average " + othersAverage
                + " ms): check the host link, not one player.";
        }
        return "<" + Palette.SUCCESS + ">Healthy</" + Palette.SUCCESS + "> — " + own + " ms average, jitter "
            + (buffer == null ? 0 : buffer.jitter()) + " ms.";
    }

    public String pingTag(int ping) {
        String colour = ping < 80 ? Palette.SUCCESS : ping < 150 ? Palette.GOLD_SOFT : ping < HIGH_PING_MS ? Palette.WARN : Palette.DANGER;
        return "<" + colour + "><bold>" + ping + " ms</bold></" + colour + ">";
    }

    private String jitterTag(int jitter) {
        String colour = jitter < 20 ? Palette.SUCCESS : jitter < 60 ? Palette.GOLD_SOFT : Palette.DANGER;
        return "<" + colour + ">" + jitter + " ms</" + colour + ">";
    }

    private String packState(Player player) {
        if (packHost == null) {
            return "<dark_gray>unknown</dark_gray>";
        }
        String attempt = packHost.attemptSummary(player.getUniqueId());
        if ("idle".equals(attempt)) {
            return "<" + Palette.SUCCESS + ">loaded</" + Palette.SUCCESS + ">";
        }
        return "<" + Palette.GOLD + ">downloading</" + Palette.GOLD + "> <dark_gray>(" + Msg.escapeMini(attempt) + ")</dark_gray>";
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
