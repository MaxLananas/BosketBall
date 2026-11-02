package fr.bosketball.pack;

import com.sun.net.httpserver.HttpServer;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.util.Msg;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * 100% Zero-Setup Self-Contained Resource Pack Distributor:
 * - Automatically extracts the bundled META-INF/BosketBall-ResourcePack.zip from BosketBall.jar on startup/upgrade
 * - Automatically synchronizes external-sha1 in config.yml
 * - Serves concurrent downloads over a virtual-threaded JDK HttpServer (enabled by default!)
 * - Dynamically resolves each player's connection hostname/IP via Player#getVirtualHost() when public-host is "auto"
 */
public final class ResourcePackHostService {

    public static final String EMBEDDED_PACK_RESOURCE = "META-INF/BosketBall-ResourcePack.zip";
    public static final String EMBEDDED_SHA1_RESOURCE = "META-INF/BosketBall-ResourcePack.sha1";

    private static final UUID PACK_UUID = UUID.nameUUIDFromBytes("BosketBall-ResourcePack".getBytes(StandardCharsets.UTF_8));

    private final Logger logger;
    private final File dataFolder;
    private File packZipFile;
    private volatile byte[] cachedPackBytes;
    private volatile String sha1Hex = "";
    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private volatile String configuredExternalUrl = "";
    private volatile String configuredPublicHost = "auto";
    private volatile int activeHttpPort = 8765;
    private volatile String effectiveDownloadUrl = "";
    private volatile boolean required = false;

    public ResourcePackHostService(File dataFolder, Logger logger) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.packZipFile = resolvePackFile(dataFolder);
    }

    private static File resolvePackFile(File folder) {
        File primary = new File(folder, "BosketBall-ResourcePack.zip");
        if (primary.exists()) {
            return primary;
        }
        File legacy = new File(folder, "BosketBall-ResourcePack-" + "26" + "." + "2.zip");
        return legacy.exists() ? legacy : primary;
    }

    private static String computeSha1Streaming(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] buf = new byte[16 * 1024];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    /**
     * Extracts the official BosketBall-ResourcePack.zip embedded inside BosketBall.jar into plugins/BosketBall/
     * whenever missing or updated, and keeps config.yml's SHA-1 hash automatically synchronized.
     */
    public void ensureBundledPackExtracted() {
        if (dataFolder == null) {
            return;
        }
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            ClassLoader cl = ResourcePackHostService.class.getClassLoader();
            String bundledSha1 = "";
            try (InputStream shaIn = cl.getResourceAsStream(EMBEDDED_SHA1_RESOURCE)) {
                if (shaIn != null) {
                    bundledSha1 = new String(shaIn.readAllBytes(), StandardCharsets.UTF_8).trim().toLowerCase();
                }
            }

            File targetZip = new File(dataFolder, "BosketBall-ResourcePack.zip");
            boolean needsExtract = !targetZip.exists();
            if (!needsExtract && bundledSha1.length() == 40) {
                String currentDiskSha1 = computeSha1Streaming(targetZip);
                needsExtract = !bundledSha1.equalsIgnoreCase(currentDiskSha1);
            }

            if (needsExtract) {
                try (InputStream zipIn = cl.getResourceAsStream(EMBEDDED_PACK_RESOURCE)) {
                    if (zipIn != null) {
                        File tmp = new File(dataFolder, "BosketBall-ResourcePack.zip.tmp");
                        Files.copy(zipIn, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        Files.move(tmp.toPath(), targetZip.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        this.packZipFile = targetZip;
                        if (logger != null) {
                            logger.info("Automatically installed embedded BosketBall-ResourcePack.zip (" + targetZip.length() + " bytes).");
                        }
                    }
                }
            }

            if (targetZip.exists()) {
                String liveSha = bundledSha1.length() == 40 ? bundledSha1 : computeSha1Streaming(targetZip);
                syncConfigSha1IfLocal(liveSha);
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Could not extract embedded BosketBall-ResourcePack.zip: " + e.getMessage());
            }
        }
    }

    private void syncConfigSha1IfLocal(String latestSha1) {
        if (latestSha1 == null || latestSha1.length() != 40 || dataFolder == null) {
            return;
        }
        File cfgFile = new File(dataFolder, "config.yml");
        if (!cfgFile.exists()) {
            return;
        }
        YamlConfiguration yml = AtomicYamlWriter.loadSafe(cfgFile, logger);
        String extUrl = yml.getString("resource-pack.external-url", "").trim();
        if (extUrl.isEmpty() && !latestSha1.equalsIgnoreCase(yml.getString("resource-pack.external-sha1", "").trim())) {
            yml.set("resource-pack.external-sha1", latestSha1);
            AtomicYamlWriter.writeYamlAtomically(cfgFile, null, yml, logger);
        }
    }

    public void initialize(
        boolean startHttp,
        String bindAddress,
        int port,
        String publicHost,
        String externalUrl,
        String externalSha1,
        boolean required
    ) {
        shutdown();
        this.required = required;
        ensureBundledPackExtracted();
        this.packZipFile = resolvePackFile(dataFolder);
        this.configuredExternalUrl = externalUrl != null ? externalUrl.trim() : "";
        this.configuredPublicHost = (publicHost != null && !publicHost.isBlank()) ? publicHost.trim() : "auto";
        this.activeHttpPort = port;

        if (!this.configuredExternalUrl.isEmpty()) {
            this.effectiveDownloadUrl = this.configuredExternalUrl;
            if (externalSha1 != null && externalSha1.length() == 40) {
                this.sha1Hex = externalSha1.toLowerCase();
            } else if (packZipFile.exists()) {
                try {
                    this.sha1Hex = computeSha1Streaming(packZipFile);
                } catch (Exception e) {
                    if (logger != null) {
                        logger.warning("Failed to hash local BosketBall resource pack zip: " + e.getMessage());
                    }
                }
            }
            return;
        }

        if (packZipFile.exists()) {
            try {
                if (startHttp) {
                    byte[] bytes = Files.readAllBytes(packZipFile.toPath());
                    this.cachedPackBytes = bytes;
                    this.sha1Hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
                } else {
                    this.sha1Hex = computeSha1Streaming(packZipFile);
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("Failed to hash local BosketBall resource pack zip: " + e.getMessage());
                }
            }
        }

        byte[] packPayload = this.cachedPackBytes;
        if (startHttp && packPayload != null && packPayload.length > 0) {
            IOException lastBindErr = null;
            for (int candidatePort = port; candidatePort <= Math.min(65535, port + 8); candidatePort++) {
                try {
                    httpServer = HttpServer.create(new InetSocketAddress(bindAddress, candidatePort), 0);
                    this.activeHttpPort = httpServer.getAddress().getPort();
                    lastBindErr = null;
                    break;
                } catch (IOException bindErr) {
                    lastBindErr = bindErr;
                }
            }
            if (httpServer != null) {
                httpServer.createContext("/BosketBall-ResourcePack.zip", exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.getResponseHeaders().add("Cache-Control", "public, max-age=86400, immutable");
                    exchange.sendResponseHeaders(200, packPayload.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(packPayload);
                    }
                });
                this.httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
                httpServer.setExecutor(this.httpExecutor);
                httpServer.start();
                String displayHost = isAutoHost(this.configuredPublicHost) ? resolveFallbackServerHost() : this.configuredPublicHost;
                this.effectiveDownloadUrl = buildHttpPackUrl(displayHost, this.activeHttpPort, this.sha1Hex);
                if (logger != null) {
                    logger.info("Embedded BosketBall ResourcePack HTTP server ready on port " + this.activeHttpPort
                        + " (auto-host enabled, default URL: " + this.effectiveDownloadUrl + ")");
                }
            } else if (lastBindErr != null && logger != null) {
                this.cachedPackBytes = null;
                logger.warning("Could not start embedded ResourcePack HTTP server: " + lastBindErr.getMessage());
            }
        }
    }

    private static boolean isAutoHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String h = host.trim();
        return "auto".equalsIgnoreCase(h) || "0.0.0.0".equals(h) || "127.0.0.1".equals(h);
    }

    private static String resolveFallbackServerHost() {
        try {
            if (Bukkit.getServer() != null) {
                String serverIp = Bukkit.getIp();
                if (serverIp != null && !serverIp.isBlank() && !"0.0.0.0".equals(serverIp)) {
                    return serverIp.trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return "127.0.0.1";
    }

    private static String sanitizeVirtualHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return "";
        }
        String clean = rawHost.trim();
        // Strip Bungee/Forge NUL markers if present
        int nulIdx = clean.indexOf('\0');
        if (nulIdx >= 0) {
            clean = clean.substring(0, nulIdx);
        }
        if (clean.endsWith(".")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        // Keep only safe hostname / IPv4 / bracketed IPv6 characters
        if (clean.isEmpty() || clean.contains("/") || clean.contains(" ") || clean.contains("?")) {
            return "";
        }
        return clean;
    }

    private static String buildHttpPackUrl(String host, int port, String sha1) {
        String formattedHost = (host.contains(":") && !host.startsWith("[")) ? "[" + host + "]" : host;
        String ver = (sha1 != null && sha1.length() >= 8) ? sha1.substring(0, 8) : "latest";
        return "http://" + formattedHost + ":" + port + "/BosketBall-ResourcePack.zip?v=" + ver;
    }

    public String resolveDownloadUrlFor(Player player) {
        if (!configuredExternalUrl.isEmpty()) {
            return configuredExternalUrl;
        }
        if (httpServer == null || sha1Hex.length() != 40) {
            return effectiveDownloadUrl;
        }
        if (!isAutoHost(configuredPublicHost)) {
            return buildHttpPackUrl(configuredPublicHost, activeHttpPort, sha1Hex);
        }
        if (player != null) {
            try {
                InetSocketAddress vh = player.getVirtualHost();
                if (vh != null) {
                    String host = sanitizeVirtualHost(vh.getHostString());
                    if (!host.isEmpty() && !"0.0.0.0".equals(host)) {
                        return buildHttpPackUrl(host, activeHttpPort, sha1Hex);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return buildHttpPackUrl(resolveFallbackServerHost(), activeHttpPort, sha1Hex);
    }

    public boolean isReadyToSend() {
        return !effectiveDownloadUrl.isBlank() && sha1Hex.length() == 40;
    }

    public String effectiveDownloadUrl() {
        return effectiveDownloadUrl;
    }

    public String sha1Hex() {
        return sha1Hex;
    }

    public File packZipFile() {
        return packZipFile;
    }

    public void sendPackTo(Player player) {
        String playerUrl = resolveDownloadUrlFor(player);
        if (playerUrl.isBlank() || sha1Hex.length() != 40) {
            Msg.send(player, Msg.info("Official BosketBall Resource Pack is initializing — try <yellow>/pack</yellow> in a moment."));
            return;
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
            .id(PACK_UUID)
            .uri(URI.create(playerUrl))
            .hash(sha1Hex)
            .build();

        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .required(required)
            .prompt(Msg.parse(
                "<gradient:#FF7A18:#FFD43B><bold>✦ ʙᴏꜱᴋᴇᴛʙᴀʟʟ — ᴏꜰꜰɪᴄɪᴀʟ ʀᴇꜱᴏᴜʀᴄᴇ ᴘᴀᴄᴋ ✦</bold></gradient>\n"
                    + "<gray>Enables custom 3D basketballs, custom GUIs, Cuboide icons, rank badges & court audio.</gray>"
            ))
            .build();

        player.sendResourcePacks(request);
    }

    public void shutdown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
        this.effectiveDownloadUrl = "";
        this.cachedPackBytes = null;
    }
}
