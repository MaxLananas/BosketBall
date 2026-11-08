package fr.bosketball.integration;

import fr.bosketball.util.Palette;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discord webhook client (rebuilt in Round 27).
 *
 * <p>Why it was rewritten: the previous version only logged every failure at {@code FINE} (i.e.
 * invisible), posted Components&nbsp;V2 payloads that Discord silently rejected for the important
 * cards, had no rate-limit budget and no bounded queue. This version:
 *
 * <ul>
 *   <li>validates the webhook URL and says so once at {@code WARNING};</li>
 *   <li>always sends {@code allowed_mentions: {parse: []}} so a player name can never ping
 *       {@code @everyone};</li>
 *   <li>respects Discord's per-webhook budget (30 requests / 60 s) with a local limiter;</li>
 *   <li>honours {@code Retry-After} on HTTP 429 and retries 5xx with exponential backoff;</li>
 *   <li>logs real failures (HTTP 400/401/403/404/429/5xx) at {@code WARNING} with the API error
 *       body, so the owner can fix the webhook instead of guessing;</li>
 *   <li>keeps a bounded queue (drop-oldest) plus counters exposed by {@link #statusLine()};</li>
 *   <li>sends simple, valid {@code embeds} payloads — no Components V2 experiment.</li>
 * </ul>
 */
public final class DiscordWebhook {

    public record Field(String name, String value, boolean inline) {
    }

    public static final class Embed {
        private String title;
        private String description;
        private String url;
        private int color = 0xFF8C1A;
        private final List<Field> fields = new ArrayList<>();
        private String footer;
        private String authorName;
        private String thumbnailUrl;
        private String imageUrl;
        private String timestamp;

        public static Embed of(String title, String description, int color) {
            Embed e = new Embed();
            e.title = title;
            e.description = description;
            e.color = color;
            return e;
        }

        public Embed field(String name, String value, boolean inline) {
            if (name != null && value != null && !value.isBlank()) {
                fields.add(new Field(name, value, inline));
            }
            return this;
        }

        public Embed footer(String footer) {
            this.footer = footer;
            return this;
        }

        public Embed author(String authorName) {
            this.authorName = authorName;
            return this;
        }

        public Embed thumbnail(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public Embed image(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Embed timestampNow() {
            this.timestamp = java.time.Instant.now().toString();
            return this;
        }

        public Embed url(String url) {
            this.url = url;
            return this;
        }

        /** Appends text to the description (used to build long cards without exceeding limits). */
        String toJson() {
            StringBuilder sb = new StringBuilder(256);
            sb.append('{');
            boolean first = true;
            if (title != null && !title.isBlank()) {
                sb.append("\"title\":\"").append(esc(truncate(title, 256))).append('"');
                first = false;
            }
            if (description != null && !description.isBlank()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append("\"description\":\"").append(esc(truncate(description, 4096))).append('"');
                first = false;
            }
            if (url != null && !url.isBlank()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append("\"url\":\"").append(esc(url)).append('"');
                first = false;
            }
            if (!first) {
                sb.append(',');
            }
            sb.append("\"color\":").append(color);
            if (authorName != null && !authorName.isBlank()) {
                sb.append(",\"author\":{\"name\":\"").append(esc(truncate(authorName, 256))).append("\"}");
            }
            if (!fields.isEmpty()) {
                sb.append(",\"fields\":[");
                int written = 0;
                for (Field f : fields) {
                    if (written >= 25) {
                        break;
                    }
                    if (written > 0) {
                        sb.append(',');
                    }
                    sb.append("{\"name\":\"").append(esc(truncate(f.name(), 256)))
                        .append("\",\"value\":\"").append(esc(truncate(f.value(), 1024)))
                        .append("\",\"inline\":").append(f.inline()).append('}');
                    written++;
                }
                sb.append(']');
            }
            if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
                sb.append(",\"thumbnail\":{\"url\":\"").append(esc(thumbnailUrl)).append("\"}");
            }
            if (imageUrl != null && !imageUrl.isBlank()) {
                sb.append(",\"image\":{\"url\":\"").append(esc(imageUrl)).append("\"}");
            }
            if (footer != null && !footer.isBlank()) {
                sb.append(",\"footer\":{\"text\":\"").append(esc(truncate(footer, 2048))).append("\"}");
            }
            if (timestamp != null) {
                sb.append(",\"timestamp\":\"").append(esc(timestamp)).append('"');
            }
            sb.append('}');
            return sb.toString();
        }
    }

    /** Discord caps a webhook at 30 requests per 60 s; we stay a little under it. */
    private static final int RATE_LIMIT_MAX = 24;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000L;
    private static final int MAX_ATTEMPTS = 3;
    private static final int QUEUE_CAPACITY = 256;

    private final Logger logger;

    private volatile String webhookUrl;
    private volatile boolean enabled;
    private volatile String lastError;
    private volatile long lastPostAt;

    private final AtomicLong queued = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final Deque<Long> recentPosts = new ArrayDeque<>();

    private final ThreadPoolExecutor executor;

    public DiscordWebhook(Logger logger) {
        this.logger = logger;
        this.executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "BosketBall-Discord");
                t.setDaemon(true);
                return t;
            },
            (task, pool) -> {
                // Drop the oldest pending post instead of silently piling up forever.
                Runnable oldest = pool.getQueue().poll();
                if (oldest != null) {
                    dropped.incrementAndGet();
                }
                try {
                    pool.execute(task);
                } catch (RejectedExecutionException ignored) {
                    dropped.incrementAndGet();
                }
            }
        );
    }

    public void configure(boolean enabled, String webhookUrl) {
        this.enabled = enabled;
        String trimmed = webhookUrl == null ? null : webhookUrl.trim();
        this.webhookUrl = trimmed;
        if (enabled && trimmed != null && !trimmed.isEmpty()) {
            String problem = validateUrl(trimmed);
            if (problem != null && logger != null) {
                logger.warning("[Discord] Webhook disabled: " + problem
                    + " — expected https://discord.com/api/webhooks/<id>/<token>");
                this.enabled = false;
            } else if (logger != null) {
                logger.info("[Discord] Webhook configured (" + trimmed.replaceAll("/[^/]*$", "/…") + ")");
            }
        }
    }

    private static String validateUrl(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("https://")) {
            return "URL must use https";
        }
        boolean host = lower.startsWith("https://discord.com/api/webhooks/")
            || lower.startsWith("https://discordapp.com/api/webhooks/")
            || lower.startsWith("https://canary.discord.com/api/webhooks/")
            || lower.startsWith("https://ptb.discord.com/api/webhooks/");
        if (!host) {
            return "unknown webhook host";
        }
        String path = lower.substring(lower.indexOf("webhooks/") + "webhooks/".length());
        if (path.split("/").length < 2) {
            return "missing webhook token";
        }
        return null;
    }

    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    public String statusLine() {
        return (isEnabled() ? "<" + Palette.SUCCESS + ">ON</" + Palette.SUCCESS + ">" : "<" + Palette.DANGER + ">OFF</" + Palette.DANGER + ">")
            + " <#64748B>•</#64748B> sent <#F8FAFC>" + sent.get() + "</#F8FAFC>"
            + " <#64748B>•</#64748B> failed <#F8FAFC>" + failed.get() + "</#F8FAFC>"
            + " <#64748B>•</#64748B> dropped <#F8FAFC>" + dropped.get() + "</#F8FAFC>"
            + (lastError != null ? " <#64748B>•</#64748B> last error <" + Palette.DANGER + ">"
                + fr.bosketball.util.Msg.escapeMini(lastError) + "</" + Palette.DANGER + ">" : "");
    }

    public void sendEmbed(Embed embed) {
        if (!isEnabled() || embed == null) {
            return;
        }
        dispatch("{\"embeds\":[" + embed.toJson() + "],\"allowed_mentions\":{\"parse\":[]}}");
    }

    /**
     * Sends a "card" (header + markdown blocks + footer).
     *
     * <p>Round 27: implemented with a normal embed. The previous Components&nbsp;V2 payload was
     * rejected by Discord often enough that match results never showed up.
     */
    public void sendCard(int accentColor, String header, List<String> blocks, String footer, String thumbnailUrl) {
        if (!isEnabled()) {
            return;
        }
        StringBuilder description = new StringBuilder(256);
        if (header != null && !header.isBlank()) {
            description.append(header.trim());
        }
        if (blocks != null) {
            for (String block : blocks) {
                if (block == null || block.isBlank()) {
                    continue;
                }
                if (description.length() > 0) {
                    description.append("\n\n");
                }
                description.append(block.trim());
            }
        }
        Embed embed = new Embed();
        embed.color = accentColor;
        embed.description = truncate(description.toString(), 4_000);
        if (footer != null && !footer.isBlank()) {
            embed.footer(truncate(footer, 2048));
        }
        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            embed.thumbnail(thumbnailUrl);
        }
        embed.timestampNow();
        sendEmbed(embed);
    }

    public void send(String content, List<Embed> embeds) {
        if (!isEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder("{");
        if (content != null && !content.isBlank()) {
            sb.append("\"content\":\"").append(esc(truncate(content, 1900))).append("\",");
        }
        sb.append("\"embeds\":[");
        if (embeds != null) {
            for (int i = 0; i < Math.min(10, embeds.size()); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(embeds.get(i).toJson());
            }
        }
        sb.append("],\"allowed_mentions\":{\"parse\":[]}}");
        dispatch(sb.toString());
    }

    private void dispatch(String payload) {
        String url = this.webhookUrl;
        if (url == null || url.isBlank() || payload == null) {
            return;
        }
        queued.incrementAndGet();
        try {
            executor.execute(() -> post(url, payload));
        } catch (RejectedExecutionException ex) {
            dropped.incrementAndGet();
        }
    }

    private void post(String url, String payload) {
        awaitBudget();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpURLConnection conn = null;
            try {
                String target = url.contains("?") ? url : url + "?wait=true";
                conn = (HttpURLConnection) URI.create(target).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(6_000);
                conn.setReadTimeout(8_000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("User-Agent", "BosketBall/3.8 (+https://bosketball.net)");
                byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body);
                }
                int code = conn.getResponseCode();
                lastPostAt = System.currentTimeMillis();
                markPost();
                if (code >= 200 && code < 300) {
                    sent.incrementAndGet();
                    drainQuietly(conn.getInputStream());
                    return;
                }
                String errorBody = truncate(readBody(conn), 400);
                if (code == 429) {
                    long retryMs = retryAfterMillis(conn, errorBody);
                    lastError = "HTTP 429 rate limited";
                    if (logger != null) {
                        logger.log(Level.WARNING, "[Discord] rate limited, retrying in " + retryMs + " ms");
                    }
                    sleep(retryMs);
                    continue;
                }
                if (code >= 500 && attempt < MAX_ATTEMPTS) {
                    lastError = "HTTP " + code;
                    if (logger != null) {
                        logger.log(Level.WARNING, "[Discord] HTTP " + code + " (attempt " + attempt + "), retrying");
                    }
                    sleep(500L * attempt);
                    continue;
                }
                failed.incrementAndGet();
                lastError = "HTTP " + code + (errorBody.isBlank() ? "" : " — " + errorBody);
                if (logger != null) {
                    logger.log(Level.WARNING, "[Discord] webhook rejected the payload: HTTP " + code
                        + (errorBody.isBlank() ? "" : " — " + errorBody));
                }
                return;
            } catch (Exception ex) {
                lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                if (attempt == MAX_ATTEMPTS) {
                    failed.incrementAndGet();
                    if (logger != null) {
                        logger.log(Level.WARNING, "[Discord] delivery failed after " + attempt
                            + " attempts: " + lastError);
                    }
                    return;
                }
                sleep(400L * attempt);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }

    /** Keeps the local request rate under Discord's per-webhook budget (30 / 60 s). */
    private void awaitBudget() {
        synchronized (recentPosts) {
            long now = System.currentTimeMillis();
            while (!recentPosts.isEmpty() && now - recentPosts.peekFirst() > RATE_LIMIT_WINDOW_MS) {
                recentPosts.pollFirst();
            }
            if (recentPosts.size() >= RATE_LIMIT_MAX) {
                long waitMs = RATE_LIMIT_WINDOW_MS - (now - recentPosts.peekFirst()) + 25L;
                sleep(Math.max(25L, waitMs));
                now = System.currentTimeMillis();
                while (!recentPosts.isEmpty() && now - recentPosts.peekFirst() > RATE_LIMIT_WINDOW_MS) {
                    recentPosts.pollFirst();
                }
            }
            recentPosts.addLast(now);
        }
    }

    private void markPost() {
        lastPostAt = System.currentTimeMillis();
    }

    public long lastPostAt() {
        return lastPostAt;
    }

    private static long retryAfterMillis(HttpURLConnection conn, String errorBody) {
        try {
            String header = conn.getHeaderField("Retry-After");
            if (header != null && !header.isBlank()) {
                return Math.min(15_000L, Math.max(250L, (long) (Double.parseDouble(header.trim()) * 1000.0)));
            }
        } catch (NumberFormatException ignored) {
            // fall through to the JSON body
        }
        int idx = errorBody == null ? -1 : errorBody.indexOf("\"retry_after\"");
        if (idx >= 0) {
            int colon = errorBody.indexOf(':', idx);
            if (colon > 0) {
                StringBuilder digits = new StringBuilder();
                for (int i = colon + 1; i < errorBody.length() && digits.length() < 12; i++) {
                    char c = errorBody.charAt(i);
                    if (Character.isDigit(c) || c == '.') {
                        digits.append(c);
                    } else if (digits.length() > 0) {
                        break;
                    }
                }
                try {
                    return Math.min(15_000L, Math.max(250L, (long) (Double.parseDouble(digits.toString()) * 1000.0)));
                } catch (NumberFormatException ignored) {
                    // default below
                }
            }
        }
        return 1_000L;
    }

    private static String readBody(HttpURLConnection conn) {
        try {
            InputStream stream = conn.getErrorStream();
            if (stream == null) {
                stream = conn.getInputStream();
            }
            if (stream == null) {
                return "";
            }
            try (InputStream in = stream) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            return "";
        }
    }

    private static void drainQuietly(InputStream stream) {
        try (InputStream in = stream) {
            in.readAllBytes();
        } catch (Exception ignored) {
            // nothing to do
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** JSON string escaper (public so it can be unit-tested). */
    public static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c)) {
                        // keep valid surrogate pairs, drop lone halves so Discord never gets invalid UTF-16
                        if (i + 1 < raw.length() && Character.isLowSurrogate(raw.charAt(i + 1))) {
                            sb.append(c).append(raw.charAt(++i));
                        }
                    } else if (!Character.isLowSurrogate(c)) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
