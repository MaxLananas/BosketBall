package fr.bosketball.pack;

import com.sun.net.httpserver.HttpServer;
import fr.bosketball.util.Msg;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;

/**
 * Self-Contained Resource Pack Distributor:
 * - Computes SHA-1 of BosketBall-ResourcePack.zip (with automatic fallback to legacy zip name if present)
 * - Optionally serves the zip over a zero-dependency embedded JDK HttpServer
 * - Pushes via Adventure Audience#sendResourcePacks(ResourcePackRequest) on join or /bb pack
 */
public final class ResourcePackHostService {

    private static final UUID PACK_UUID = UUID.nameUUIDFromBytes("BosketBall-ResourcePack".getBytes());

    private final Logger logger;
    private final File dataFolder;
    private File packZipFile;
    private byte[] cachedPackBytes;
    private String sha1Hex = "";
    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private String effectiveDownloadUrl = "";
    private boolean required = false;

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
        this.packZipFile = resolvePackFile(dataFolder);

        if (packZipFile.exists()) {
            try {
                this.cachedPackBytes = Files.readAllBytes(packZipFile.toPath());
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                this.sha1Hex = HexFormat.of().formatHex(md.digest(cachedPackBytes));
            } catch (Exception e) {
                logger.warning("Failed to hash local BosketBall resource pack zip: " + e.getMessage());
            }
        }

        if (externalUrl != null && !externalUrl.isBlank()) {
            this.effectiveDownloadUrl = externalUrl.trim();
            if (externalSha1 != null && externalSha1.length() == 40) {
                this.sha1Hex = externalSha1.toLowerCase();
            }
            return;
        }

        if (startHttp && cachedPackBytes != null && cachedPackBytes.length > 0) {
            try {
                httpServer = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
                httpServer.createContext("/BosketBall-ResourcePack.zip", exchange -> {
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, cachedPackBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(cachedPackBytes);
                    }
                });
                this.httpExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "BosketBall-PackHttp");
                    t.setDaemon(true);
                    return t;
                });
                httpServer.setExecutor(this.httpExecutor);
                httpServer.start();
                this.effectiveDownloadUrl = "http://" + publicHost + ":" + port + "/BosketBall-ResourcePack.zip?v=" + sha1Hex.substring(0, 8);
                logger.info("Embedded BosketBall ResourcePack HTTP server listening on " + effectiveDownloadUrl);
            } catch (IOException e) {
                logger.warning("Could not start embedded ResourcePack HTTP server: " + e.getMessage());
            }
        }
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
        if (!isReadyToSend()) {
            Msg.send(player, Msg.info("Place <white>BosketBall-ResourcePack.zip</white> inside <yellow>plugins/BosketBall/</yellow> or configure <yellow>resource-pack.external-url</yellow>."));
            return;
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
            .id(PACK_UUID)
            .uri(URI.create(effectiveDownloadUrl))
            .hash(sha1Hex)
            .build();

        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .required(required)
            .prompt(Msg.parse(
                "<gradient:#FF7A18:#FFD43B><bold>✦ ʙᴏꜱᴋᴇᴛʙᴀʟʟ — ᴏꜰꜰɪᴄɪᴀʟ ʀᴇꜱᴏᴜʀᴄᴇ ᴘᴀᴄᴋ ✦</bold></gradient>\n"
                    + "<gray>Enables custom basketballs, hoops, HUD icons, rank badges & court audio.</gray>"
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
    }
}
