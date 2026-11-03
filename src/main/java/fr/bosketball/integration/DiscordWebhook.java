package fr.bosketball.integration;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Zero-dependency Discord webhook dispatcher (rich embeds, no external library).
 *
 * <p>Every call is queued on a single daemon thread with a bounded retry, so a slow or
 * unreachable Discord can never lag the server tick loop. All payloads are JSON built
 * here (only the pieces Discord accepts) — embeds, fields, colors, footers.</p>
 */
public final class DiscordWebhook {

    /** One embed field. */
    public record Field(String name, String value, boolean inline) {
    }

    /** A rich embed. */
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

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            if (title != null && !title.isBlank()) {
                sb.append("\"title\":\"").append(esc(title)).append('"');
                first = false;
            }
            if (description != null && !description.isBlank()) {
                if (!first) sb.append(',');
                sb.append("\"description\":\"").append(esc(description)).append('"');
                first = false;
            }
            if (url != null && !url.isBlank()) {
                if (!first) sb.append(',');
                sb.append("\"url\":\"").append(esc(url)).append('"');
                first = false;
            }
            if (!first) sb.append(',');
            sb.append("\"color\":").append(color);
            if (authorName != null && !authorName.isBlank()) {
                sb.append(",\"author\":{\"name\":\"").append(esc(authorName)).append("\"}");
            }
            if (!fields.isEmpty()) {
                sb.append(",\"fields\":[");
                for (int i = 0; i < fields.size(); i++) {
                    Field f = fields.get(i);
                    if (i > 0) sb.append(',');
                    sb.append("{\"name\":\"").append(esc(truncate(f.name(), 256)))
                        .append("\",\"value\":\"").append(esc(truncate(f.value(), 1024)))
                        .append("\",\"inline\":").append(f.inline()).append('}');
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
                sb.append(",\"timestamp\":\"").append(timestamp).append('"');
            }
            sb.append('}');
            return sb.toString();
        }
    }

    /** Discord message flag that enables components v2 (containers, text displays). */
    public static final int FLAG_COMPONENTS_V2 = 1 << 15;
    private static final int TYPE_CONTAINER = 17;
    private static final int TYPE_TEXT = 10;
    private static final int TYPE_SECTION = 9;
    private static final int TYPE_THUMBNAIL = 11;

    private final Logger logger;
    private volatile String webhookUrl;
    private volatile boolean enabled;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BosketBall-Discord");
        t.setDaemon(true);
        return t;
    });

    public DiscordWebhook(Logger logger) {
        this.logger = logger;
    }

    public void configure(boolean enabled, String webhookUrl) {
        this.enabled = enabled;
        this.webhookUrl = webhookUrl == null ? null : webhookUrl.trim();
    }

    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    /** Sends a plain content message. */
    public void sendContent(String content) {
        if (!isEnabled() || content == null || content.isBlank()) {
            return;
        }
        String payload = "{\"content\":\"" + esc(truncate(content, 1900)) + "\"}";
        dispatch(payload, null);
    }

    /** Sends one rich embed. */
    public void sendEmbed(Embed embed) {
        if (!isEnabled() || embed == null) {
            return;
        }
        dispatch("{\"embeds\":[" + embed.toJson() + "],\"allowed_mentions\":{\"parse\":[]}}", null);
    }

    // =========================================================================
    //  COMPONENTS v2 (container + text display)
    // =========================================================================

    /**
     * Rich "components v2" message: a colour-accented container with a header line, a set of
     * text blocks and an optional footer, all rendered edge-to-edge in Discord.
     *
     * <p>Discord refuses {@code content}/embeds alongside components, so a v2 message is sent
     * on its own. This is what gives the crate and match cards their neon look instead of the
     * default embed chrome.</p>
     */
    public void sendCard(int accentColor, String header, List<String> blocks, String footer, String thumbnailUrl) {
        if (!isEnabled()) {
            return;
        }
        StringBuilder json = new StringBuilder(512);
        json.append("{\"flags\":").append(FLAG_COMPONENTS_V2).append(",\"components\":[{");
        json.append("\"type\":").append(TYPE_CONTAINER);
        json.append(",\"accent_color\":").append(accentColor);
        json.append(",\"components\":[");
        List<String> parts = new ArrayList<>();
        if (header != null && !header.isBlank()) {
            parts.add(textBlock(header));
        }
        if (blocks != null) {
            for (String block : blocks) {
                if (block != null && !block.isBlank()) {
                    parts.add(textBlock(block));
                }
            }
        }
        if (footer != null && !footer.isBlank()) {
            parts.add(textBlock("-# " + truncate(footer, 2000)));
        }
        json.append(String.join(",", parts));
        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            json.append(",{\"type\":").append(TYPE_SECTION)
                .append(",\"components\":[{\"type\":").append(TYPE_TEXT)
                .append(",\"content\":\" \"}],")
                .append("\"accessory\":{\"type\":").append(TYPE_THUMBNAIL)
                .append(",\"media\":{\"url\":\"").append(esc(thumbnailUrl)).append("\"}}}");
        }
        json.append("]}]}");
        dispatch(json.toString(), null);
    }

    /** Sends a content line plus embeds in the same message. */
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
                if (i > 0) sb.append(',');
                sb.append(embeds.get(i).toJson());
            }
        }
        sb.append("],\"allowed_mentions\":{\"parse\":[]}}");
        dispatch(sb.toString(), null);
    }

    private void dispatch(String payload, String unusedUserAgent) {
        String url = this.webhookUrl;
        if (url == null || url.isBlank()) {
            return;
        }
        executor.submit(() -> post(url, payload));
    }

    private void post(String url, String payload) {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("User-Agent", "BosketBall/3.7 (+https://bosketball.net)");
                byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(body);
                }
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    return;
                }
                if (code == 429 || code >= 500) {
                    Thread.sleep(600L);
                    continue;
                }
                if (logger != null) {
                    logger.fine("Discord webhook returned HTTP " + code);
                }
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                if (attempt == 1 && logger != null) {
                    logger.fine("Discord webhook delivery failed: " + ex.getMessage());
                }
                try {
                    Thread.sleep(400L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
    }

    /** Flushes and stops the dispatcher. */
    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String textBlock(String content) {
        return "{\"type\":" + TYPE_TEXT + ",\"content\":\"" + esc(truncate(content, 3900)) + "\"}";
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Minimal JSON string escaping (quotes, backslash, control chars, newlines). */
    static String esc(String raw) {
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
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
