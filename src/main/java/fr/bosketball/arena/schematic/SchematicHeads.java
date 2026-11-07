package fr.bosketball.arena.schematic;

import com.destroystokyo.paper.profile.PlayerProfile;
import fr.bosketball.config.BosketConfig;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Keeps the custom player heads of an imported build exactly as they were saved.
 *
 * <p>A head is two things: the <b>block</b> ({@code player_head[rotation=11,powered=true]}, which
 * gives it its facing) and its <b>block entity</b> (the skin). The paste only ever wrote the block,
 * so every custom head came back as a plain Steve head — correctly rotated, but wrong. This class
 * rebuilds the skin from the schematic payload:</p>
 *
 * <ol>
 *   <li>the {@code profile.properties.textures} value when the file carries it — no lookup at all,
 *       the exact skin of the build;</li>
 *   <li>otherwise the {@code profile.name}: the server's own player cache ({@code usercache.json})
 *       answers instantly for anyone who already joined — still no network call;</li>
 *   <li>a skin pinned in {@code schematic-courts.head-skins} for that nickname — the escape hatch
 *       for an account that was renamed or deleted, which the client would otherwise draw as
 *       Steve;</li>
 *   <li>otherwise the name is resolved <b>once, in the background</b> (never on the server thread)
 *       — usercache first, then a real profile lookup — and the head is re-skinned by the next
 *       decoration pass.</li>
 * </ol>
 *
 * <p>Round 42: two heads of the imported build carry nothing but a nickname. One of them resolved
 * (a real account), the other stayed Steve forever, because the background pass only ever read the
 * local cache and never asked the session server. It now completes the profile on the web (still
 * off the server thread), and a pinned value wins over everything.
 */
final class SchematicHeads {

    /** Resolved skins, keyed by lowercase player name. */
    private static final ConcurrentMap<String, PlayerProfile> BY_NAME = new ConcurrentHashMap<>();

    /** Names already sent to the background resolver: a name is looked up at most once. */
    private static final java.util.Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

    /** When an unresolved name may be probed again (the watchdog re-applies heads every second). */
    private static final ConcurrentMap<String, Long> NEXT_PROBE = new ConcurrentHashMap<>();

    private static final long PROBE_INTERVAL_MILLIS = 30_000L;

    private static volatile org.bukkit.plugin.Plugin owner;

    /** Skins pinned in the config, keyed by lowercase nickname: {@code name -> value;signature}. */
    private static volatile Map<String, String> PINNED = Map.of();

    private SchematicHeads() {
    }

    /** Gives the resolver a plugin to schedule on, and warms every head name of the pool. */
    static void attach(org.bukkit.plugin.Plugin plugin, Collection<String> names) {
        attach(plugin, names, Map.of());
    }

    /** Same, with the skins pinned in {@code schematic-courts.head-skins}. */
    static void attach(org.bukkit.plugin.Plugin plugin, Collection<String> names, Map<String, String> pinned) {
        owner = plugin;
        Map<String, String> skins = new java.util.HashMap<>();
        if (pinned != null) {
            for (Map.Entry<String, String> entry : pinned.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    skins.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
                }
            }
        }
        PINNED = Map.copyOf(skins);
        BY_NAME.clear();
        for (String name : names) {
            request(name);
        }
    }

    /** {@code "value;signature"} (the signature is optional) split into its two halves. */
    static String[] parsePinned(String raw) {
        return BosketConfig.SchematicCourts.parseSkinValue(raw);
    }

    /** Head names carried by a set of saved block entities (pure: unit-tested offline). */
    static List<String> namesIn(Collection<CourtSchematic.SavedBlockEntity> entities) {
        List<String> names = new ArrayList<>();
        if (entities == null) {
            return names;
        }
        for (CourtSchematic.SavedBlockEntity entity : entities) {
            if (entity == null || !entity.isSkull()) {
                continue;
            }
            String name = skinName(entity.data());
            if (name != null && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Player name of a saved skull payload, or {@code null} (pure: unit-tested offline). */
    static String skinName(Map<String, Object> payload) {
        Map<String, Object> profile = profileOf(payload);
        if (profile == null) {
            return null;
        }
        Object name = profile.get("name");
        if (name instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    /** Texture value of a saved skull payload, or {@code null} (pure: unit-tested offline). */
    static String textureValue(Map<String, Object> payload) {
        Map<String, Object> property = textureProperty(payload);
        return property != null && property.get("value") instanceof String value ? value : null;
    }

    /** Texture signature of a saved skull payload, or {@code null} (pure: unit-tested offline). */
    static String textureSignature(Map<String, Object> payload) {
        Map<String, Object> property = textureProperty(payload);
        return property != null && property.get("signature") instanceof String signature ? signature : null;
    }

    private static Map<String, Object> textureProperty(Map<String, Object> payload) {
        for (Map<String, Object> property : properties(profileOf(payload))) {
            if ("textures".equals(property.get("name"))) {
                return property;
            }
        }
        return null;
    }

    /** The skin that can be applied right now, or {@code null}. Never touches the network. */
    static PlayerProfile resolve(Map<String, Object> payload) {
        String texture = textureValue(payload);
        String name = skinName(payload);
        if (name != null) {
            // A pinned skin always wins: it is the only thing that can fix a renamed account.
            String[] pinned = parsePinned(PINNED.get(name.toLowerCase(Locale.ROOT)));
            if (pinned != null) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(idOf(profileOf(payload)), name);
                    profile.setProperty(new ProfileProperty("textures", pinned[0], pinned[1]));
                    return profile;
                } catch (Throwable ignored) {
                    // fall through to the saved / cached routes
                }
            }
        }
        if (texture != null) {
            try {
                PlayerProfile profile = Bukkit.createProfile(idOf(profileOf(payload)), name);
                profile.setProperty(new ProfileProperty("textures", texture, textureSignature(payload)));
                return profile;
            } catch (Throwable ignored) {
                // fall back to the name / cache route below
            }
        }
        if (name == null) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        PlayerProfile known = BY_NAME.get(key);
        if (known != null) {
            return known;
        }
        long now = System.currentTimeMillis();
        Long nextProbe = NEXT_PROBE.get(key);
        if (nextProbe != null && now < nextProbe) {
            // Already probed a moment ago: do not burn a cache lookup every second of every pass.
            request(name);
            return null;
        }
        NEXT_PROBE.put(key, now + PROBE_INTERVAL_MILLIS);
        try {
            // 1. the server already knows this player (has joined at least once): free, offline.
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            if (cached != null) {
                PlayerProfile profile = cached.getPlayerProfile();
                if (profile != null && profile.hasTextures()) {
                    BY_NAME.put(key, profile);
                    return profile;
                }
            }
            // 2. usercache.json lookup by name: synchronous but local (no web request).
            PlayerProfile probe = Bukkit.createProfile(name);
            if (probe != null && probe.completeFromCache() && probe.hasTextures()) {
                BY_NAME.put(key, probe);
                return probe;
            }
        } catch (Throwable ignored) {
            // API/skin-cache unavailable: the background resolver will try the web once
        }
        request(name);
        return null;
    }

    /** Re-skins a skull block state; {@code false} when the skin is not known yet. */
    static boolean apply(org.bukkit.block.Skull skull, Map<String, Object> payload) {
        PlayerProfile profile = resolve(payload);
        if (profile == null) {
            return false;
        }
        try {
            skull.setProfile(ResolvableProfile.resolvableProfile(profile));
            return skull.update(true, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void request(String name) {
        if (name == null || name.isBlank() || !REQUESTED.add(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        org.bukkit.plugin.Plugin plugin = owner;
        if (plugin == null || !plugin.isEnabled()) {
            REQUESTED.remove(name.toLowerCase(Locale.ROOT));
            return;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
                    PlayerProfile profile = offline == null ? null : offline.getPlayerProfile();
                    if (profile != null && !profile.hasTextures()) {
                        // Only the session server knows a nickname that never joined this server —
                        // a blocking web call, which is exactly why this runs off the main thread.
                        profile.complete();
                    }
                    if (profile != null && profile.hasTextures()) {
                        BY_NAME.put(name.toLowerCase(Locale.ROOT), profile);
                    }
                } catch (Throwable ignored) {
                    // offline server / unknown name: the head keeps its block state, without a skin
                }
            });
        } catch (Throwable ignored) {
            // scheduler unavailable (shutdown): skip, nothing breaks
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> profileOf(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object profile = payload.get("profile");
        if (profile instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Object legacy = payload.get("SkullOwner");
        if (legacy instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> properties(Map<String, Object> profile) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (profile == null) {
            return out;
        }
        if (profile.get("properties") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                }
            }
        }
        return out;
    }

    private static UUID idOf(Map<String, Object> profile) {
        if (profile != null && profile.get("id") instanceof int[] parts && parts.length == 4) {
            return new UUID((long) parts[0] << 32 | (parts[1] & 0xFFFFFFFFL),
                (long) parts[2] << 32 | (parts[3] & 0xFFFFFFFFL));
        }
        // Round 42: a random UUID per paste made the same head a different "player" every time, so
        // the client could not reuse the skin it had already downloaded. The offline-player UUID is
        // the one the server itself uses for a name, so caching works and the skin is stable.
        String name = profile != null && profile.get("name") instanceof String text ? text.trim() : null;
        return name == null || name.isEmpty()
            ? UUID.randomUUID()
            : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
