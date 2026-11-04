package fr.bosketball.pack;

import com.sun.net.httpserver.HttpServer;
import fr.bosketball.config.AtomicYamlWriter;
import fr.bosketball.util.Msg;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class ResourcePackHostService {

    public static final String EMBEDDED_PACK_RESOURCE = "META-INF/BosketBall-ResourcePack.zip";
    public static final String EMBEDDED_SHA1_RESOURCE = "META-INF/BosketBall-ResourcePack.sha1";

    private static final UUID PACK_UUID = UUID.nameUUIDFromBytes("BosketBall-ResourcePack".getBytes(StandardCharsets.UTF_8));

    private static final class PlayerPackAttempt {
        final List<String> candidates;
        int index;
        UUID activePackUuid;

        PlayerPackAttempt(List<String> candidates, int index, UUID activePackUuid) {
            this.candidates = candidates;
            this.index = index;
            this.activePackUuid = activePackUuid;
        }
    }

    private final Logger logger;
    private final File dataFolder;
    private final NettyGamePortPackMux nettyMux;
    private final Map<String, String> dnsIpv4Cache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerPackAttempt> activeAttempts = new ConcurrentHashMap<>();

    private File packZipFile;
    private volatile byte[] cachedPackBytes;
    private volatile String sha1Hex = "";
    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private volatile String configuredExternalUrl = "";
    private volatile String configuredPublicHost = "auto";
    private volatile String detectedPublicIpv4 = "";
    private volatile int activeHttpPort = -1;
    private volatile String effectiveDownloadUrl = "";
    private volatile boolean required = false;
    private volatile boolean hostingEnabled = true;

    public ResourcePackHostService(File dataFolder, Logger logger) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.packZipFile = resolvePackFile(dataFolder);
        this.nettyMux = new NettyGamePortPackMux(() -> this.cachedPackBytes, logger);
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
                    bundledSha1 = new String(shaIn.readAllBytes(), StandardCharsets.UTF_8).trim().toLowerCase(java.util.Locale.ROOT);
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
                        Files.move(tmp.toPath(), targetZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
        this.hostingEnabled = startHttp;
        ensureBundledPackExtracted();
        this.packZipFile = resolvePackFile(dataFolder);
        this.configuredExternalUrl = externalUrl != null ? externalUrl.trim() : "";
        this.configuredPublicHost = (publicHost != null && !publicHost.isBlank()) ? publicHost.trim() : "auto";
        this.activeHttpPort = -1;

        if (!this.configuredExternalUrl.isEmpty()) {
            this.effectiveDownloadUrl = this.configuredExternalUrl;
            if (externalSha1 != null && externalSha1.length() == 40) {
                this.sha1Hex = externalSha1.toLowerCase(java.util.Locale.ROOT);
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
                byte[] bytes = Files.readAllBytes(packZipFile.toPath());
                this.cachedPackBytes = bytes;
                this.sha1Hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("Failed to read/hash BosketBall resource pack zip: " + e.getMessage());
                }
            }
        }

        byte[] packPayload = this.cachedPackBytes;
        if (startHttp && packPayload != null && packPayload.length > 0) {

            nettyMux.ensureHooked();
            discoverPublicIpv4Async();

            for (int candidatePort = port; candidatePort <= Math.min(65535, port + 8); candidatePort++) {
                try {
                    httpServer = HttpServer.create(new InetSocketAddress(bindAddress, candidatePort), 0);
                    this.activeHttpPort = httpServer.getAddress().getPort();
                    break;
                } catch (IOException ignored) {
                }
            }
            if (httpServer != null) {
                httpServer.createContext("/BosketBall-ResourcePack.zip", exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.getResponseHeaders().set("Content-Length", Integer.toString(packPayload.length));
                    exchange.getResponseHeaders().set("Accept-Ranges", "none");
                    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
                    if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(200, -1);
                        exchange.close();
                        return;
                    }
                    exchange.sendResponseHeaders(200, packPayload.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(packPayload);
                    }
                });
                this.httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
                httpServer.setExecutor(this.httpExecutor);
                httpServer.start();
            }

            int gamePort = resolveServerGamePort();
            String displayHost = isAutoHost(this.configuredPublicHost) ? resolveFallbackServerHost() : this.configuredPublicHost;
            int defaultPort = (gamePort > 0) ? gamePort : (this.activeHttpPort > 0 ? this.activeHttpPort : port);
            this.effectiveDownloadUrl = buildHttpPackUrl(displayHost, defaultPort, this.sha1Hex);
            if (logger != null) {
                logger.info("BosketBall ResourcePack ready (SHA-1: " + this.sha1Hex.substring(0, 12) + "..., "
                    + packPayload.length + " bytes, game-port mux=" + gamePort
                    + (this.activeHttpPort > 0 ? ", fallback-http=" + this.activeHttpPort : "") + ").");
            }
        }
    }

    public void ensureNettyMuxHooked() {
        if (hostingEnabled && configuredExternalUrl.isEmpty() && cachedPackBytes != null && cachedPackBytes.length > 0) {
            nettyMux.ensureHooked();
        }
    }

    public NettyGamePortPackMux nettyMux() {
        return nettyMux;
    }

    private void discoverPublicIpv4Async() {
        String configuredIp = resolveFallbackServerHost();
        if (isPublicIpv4Literal(configuredIp)) {
            this.detectedPublicIpv4 = configuredIp;
            return;
        }
        Thread probe = new Thread(() -> {
            String[] endpoints = {"http://checkip.amazonaws.com", "http://api.ipify.org"};
            for (String endpoint : endpoints) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
                    conn.setConnectTimeout(1800);
                    conn.setReadTimeout(1800);
                    conn.setRequestProperty("User-Agent", "BosketBall-PackHost/1.0");
                    if (conn.getResponseCode() == 200) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.US_ASCII))) {
                            String line = br.readLine();
                            if (line != null) {
                                String ip = line.trim();
                                if (isPublicIpv4Literal(ip)) {
                                    this.detectedPublicIpv4 = ip;
                                    if (isAutoHost(this.configuredPublicHost)) {
                                        int gp = resolveServerGamePort();
                                        if (gp > 0 && sha1Hex.length() == 40) {
                                            this.effectiveDownloadUrl = buildHttpPackUrl(ip, gp, sha1Hex);
                                        }
                                    }
                                    if (logger != null) {
                                        logger.info("Detected public server IPv4 for ResourcePack delivery: " + ip);
                                    }
                                    return;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "BosketBall-PublicIP-Probe");
        probe.setDaemon(true);
        probe.start();
    }

    static boolean isPublicIpv4Literal(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] oct = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                oct[i] = Integer.parseInt(parts[i]);
                if (oct[i] < 0 || oct[i] > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (oct[0] == 0 || oct[0] == 10 || oct[0] == 127) {
            return false;
        }
        if (oct[0] == 192 && oct[1] == 168) {
            return false;
        }
        if (oct[0] == 172 && oct[1] >= 16 && oct[1] <= 31) {
            return false;
        }
        if (oct[0] == 169 && oct[1] == 254) {
            return false;
        }
        return oct[0] < 224;
    }

    private static boolean isLoopbackOrPrivateAddress(InetSocketAddress addr) {
        if (addr == null) {
            return false;
        }
        InetAddress ia = addr.getAddress();
        if (ia == null) {
            return false;
        }
        return ia.isLoopbackAddress() || ia.isSiteLocalAddress() || ia.isLinkLocalAddress();
    }

    private String resolveDnsIpv4Cached(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        String cached = dnsIpv4Cache.get(host);
        if (cached != null) {
            return cached;
        }

        Thread resolver = new Thread(() -> {
            try {
                InetAddress[] all = InetAddress.getAllByName(host);
                for (InetAddress a : all) {
                    if (a instanceof Inet4Address) {
                        dnsIpv4Cache.put(host, a.getHostAddress());
                        return;
                    }
                }
                if (all.length > 0) {
                    dnsIpv4Cache.put(host, all[0].getHostAddress());
                    return;
                }
            } catch (Throwable ignored) {
            }
            dnsIpv4Cache.put(host, "UNRESOLVED");
        }, "BosketBall-DNS-" + host);
        resolver.setDaemon(true);
        resolver.start();
        try {
            resolver.join(180L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return dnsIpv4Cache.getOrDefault(host, "");
    }

    private int resolveServerGamePort() {
        int muxPort = nettyMux.detectedBoundPort();
        if (muxPort > 0) {
            return muxPort;
        }
        try {
            if (Bukkit.getServer() != null && Bukkit.getPort() > 0) {
                return Bukkit.getPort();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static boolean isAutoHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String h = host.trim();
        return "auto".equalsIgnoreCase(h) || "0.0.0.0".equals(h) || "127.0.0.1".equals(h);
    }

    private String resolveFallbackServerHost() {
        if (!detectedPublicIpv4.isBlank()) {
            return detectedPublicIpv4;
        }
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

    static String sanitizeVirtualHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return "";
        }
        String clean = rawHost.trim();
        int nulIdx = clean.indexOf('\0');
        if (nulIdx >= 0) {
            clean = clean.substring(0, nulIdx);
        }
        if (clean.endsWith(".")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.isEmpty() || clean.contains("/") || clean.contains(" ") || clean.contains("?")) {
            return "";
        }
        return clean;
    }

    public static String buildHttpPackUrl(String host, int port, String sha1) {
        String formattedHost = (host.contains(":") && !host.startsWith("[")) ? "[" + host + "]" : host;
        String ver = (sha1 != null && sha1.length() >= 8) ? sha1.substring(0, 8) : "latest";
        return "http://" + formattedHost + ":" + port + "/BosketBall-ResourcePack.zip?v=" + ver;
    }

    public List<String> buildCandidateUrlsFor(Player player) {
        if (!configuredExternalUrl.isEmpty()) {
            return List.of(configuredExternalUrl);
        }
        if (sha1Hex.length() != 40 || cachedPackBytes == null || cachedPackBytes.length == 0) {
            return effectiveDownloadUrl.isBlank() ? List.of() : List.of(effectiveDownloadUrl);
        }

        ensureNettyMuxHooked();

        int boundGamePort = resolveServerGamePort();
        boolean muxReady = nettyMux.isHooked() && boundGamePort > 0;
        Set<String> urls = new LinkedHashSet<>();

        if (!isAutoHost(configuredPublicHost)) {
            if (muxReady) {
                urls.add(buildHttpPackUrl(configuredPublicHost, boundGamePort, sha1Hex));
            }
            if (activeHttpPort > 0) {
                urls.add(buildHttpPackUrl(configuredPublicHost, activeHttpPort, sha1Hex));
            }
            return List.copyOf(urls);
        }

        String vhHost = "";
        int vhPort = -1;
        boolean playerIsLocalOrLan = false;
        if (player != null) {
            try {
                playerIsLocalOrLan = isLoopbackOrPrivateAddress(player.getAddress());
            } catch (Throwable ignored) {
            }
            try {
                InetSocketAddress vh = player.getVirtualHost();
                if (vh != null) {
                    vhHost = sanitizeVirtualHost(vh.getHostString());
                    if ("0.0.0.0".equals(vhHost)) {
                        vhHost = "";
                    }
                    if (vh.getPort() > 0 && vh.getPort() <= 65535) {
                        vhPort = vh.getPort();
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        List<Integer> gamePorts = new ArrayList<>(2);
        if (muxReady) {
            if (vhPort > 0 && vhPort != 80 && vhPort != 443) {
                gamePorts.add(vhPort);
            }
            if (boundGamePort > 0 && !gamePorts.contains(boundGamePort)) {
                gamePorts.add(boundGamePort);
            }
        } else if (boundGamePort > 0 && activeHttpPort <= 0) {
            gamePorts.add(boundGamePort);
        }

        List<String> orderedHosts = new ArrayList<>(3);
        String pubIp = this.detectedPublicIpv4;
        if (playerIsLocalOrLan) {
            if (!vhHost.isEmpty()) {
                orderedHosts.add(vhHost);
            }
            orderedHosts.add("127.0.0.1");
        } else {
            boolean preferPublicIpFirst = false;
            if (!pubIp.isBlank() && !vhHost.isEmpty() && !pubIp.equals(vhHost)) {
                if (!isPublicIpv4Literal(vhHost)) {
                    String resolvedVhIp = resolveDnsIpv4Cached(vhHost);
                    if ("UNRESOLVED".equals(resolvedVhIp) || (!resolvedVhIp.isEmpty() && !resolvedVhIp.equals(pubIp))) {
                        preferPublicIpFirst = true;
                    }
                }
            }
            if (preferPublicIpFirst) {
                orderedHosts.add(pubIp);
                orderedHosts.add(vhHost);
            } else {
                if (!vhHost.isEmpty()) {
                    orderedHosts.add(vhHost);
                }
                if (!pubIp.isBlank() && !orderedHosts.contains(pubIp)) {
                    orderedHosts.add(pubIp);
                }
            }
            String fallbackHost = resolveFallbackServerHost();
            if (!fallbackHost.isBlank() && !orderedHosts.contains(fallbackHost)) {
                orderedHosts.add(fallbackHost);
            }
        }

        for (String h : orderedHosts) {
            for (int gp : gamePorts) {
                urls.add(buildHttpPackUrl(h, gp, sha1Hex));
            }
        }
        if (activeHttpPort > 0) {
            for (String h : orderedHosts) {
                urls.add(buildHttpPackUrl(h, activeHttpPort, sha1Hex));
            }
        }
        return List.copyOf(urls);
    }

    public String resolveDownloadUrlFor(Player player) {
        List<String> candidates = buildCandidateUrlsFor(player);
        return candidates.isEmpty() ? effectiveDownloadUrl : candidates.get(0);
    }

    public boolean isReadyToSend() {
        return (!effectiveDownloadUrl.isBlank() || cachedPackBytes != null) && sha1Hex.length() == 40;
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
        if (player == null) {
            return;
        }
        List<String> candidates = buildCandidateUrlsFor(player);
        if (candidates.isEmpty() || sha1Hex.length() != 40) {
            Msg.send(player, Msg.info("Official BosketBall Resource Pack is initializing — try <yellow>/pack</yellow> in a moment."));
            return;
        }
        sendCandidateAtIndex(player, candidates, 0);
    }

    private void sendCandidateAtIndex(Player player, List<String> candidates, int index) {
        if (player == null || !player.isOnline() || index < 0 || index >= candidates.size()) {
            return;
        }
        String url = candidates.get(index);
        UUID packUuid = (index == 0)
            ? PACK_UUID
            : UUID.nameUUIDFromBytes(("BosketBall-ResourcePack-fallback-" + index).getBytes(StandardCharsets.UTF_8));

        PlayerPackAttempt prev = activeAttempts.get(player.getUniqueId());
        if (prev != null && prev.activePackUuid != null && !prev.activePackUuid.equals(packUuid)) {
            try {
                player.removeResourcePacks(prev.activePackUuid);
            } catch (Throwable ignored) {
            }
        }

        activeAttempts.put(player.getUniqueId(), new PlayerPackAttempt(candidates, index, packUuid));

        if (logger != null) {
            logger.info("Sending BosketBall ResourcePack to " + player.getName()
                + " [attempt " + (index + 1) + "/" + candidates.size() + "]: " + url);
        }

        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
            .id(packUuid)
            .uri(URI.create(url))
            .hash(sha1Hex)
            .build();

        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .required(required && index == candidates.size() - 1)
            .prompt(Msg.parse(
                "<gradient:#FF7A18:#FFD43B><bold>✦ ʙᴏꜱᴋᴇᴛʙᴀʟʟ — ᴏꜰꜰɪᴄɪᴀʟ ʀᴇꜱᴏᴜʀᴄᴇ ᴘᴀᴄᴋ ✦</bold></gradient>\n"
                    + "<gray>Enables custom 3D basketballs, custom GUIs, BuiltByBit icons, rank badges & court audio.</gray>"
            ))
            .build();

        player.sendResourcePacks(request);
    }

    public boolean handlePackStatus(Player player, String statusName, java.util.function.Consumer<Runnable> delayedMainThreadExecutor) {
        if (player == null || statusName == null) {
            return false;
        }
        if ("SUCCESSFULLY_LOADED".equals(statusName)) {
            activeAttempts.remove(player.getUniqueId());
            if (logger != null) {
                logger.info("BosketBall ResourcePack successfully loaded for " + player.getName() + ".");
            }
            return false;
        }
        if ("DECLINED".equals(statusName)) {
            activeAttempts.remove(player.getUniqueId());
            return false;
        }
        if ("FAILED_DOWNLOAD".equals(statusName) || "INVALID_URL".equals(statusName)) {
            PlayerPackAttempt attempt = activeAttempts.get(player.getUniqueId());
            if (attempt != null && (attempt.index + 1) < attempt.candidates.size()) {
                int nextIdx = attempt.index + 1;
                List<String> candidates = attempt.candidates;
                if (logger != null) {
                    logger.warning("ResourcePack route #" + (attempt.index + 1) + " reported " + statusName
                        + " for " + player.getName() + " — automatically retrying route #" + (nextIdx + 1)
                        + " (" + candidates.get(nextIdx) + ")");
                }
                Runnable retryTask = () -> sendCandidateAtIndex(player, candidates, nextIdx);
                if (delayedMainThreadExecutor != null) {
                    delayedMainThreadExecutor.accept(retryTask);
                } else {
                    retryTask.run();
                }
                return true;
            }
            activeAttempts.remove(player.getUniqueId());
            if (logger != null) {
                logger.warning("All ResourcePack routes exhausted for " + player.getName() + " (last status: " + statusName + ").");
            }
        }
        return false;
    }

    public void clearPlayerState(UUID playerId) {
        if (playerId != null) {
            activeAttempts.remove(playerId);
        }
    }

    public void shutdown() {
        nettyMux.unhookAll();
        activeAttempts.clear();
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
        this.activeHttpPort = -1;
        this.effectiveDownloadUrl = "";
        this.cachedPackBytes = null;
    }
}
