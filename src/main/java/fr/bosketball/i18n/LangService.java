package fr.bosketball.i18n;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.PlayerSettings;
import fr.bosketball.util.Msg;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;

/**
 * Per-player language resolution with a tiny hot cache.
 *
 * <p>Resolving a language on every message would mean touching the profile store hundreds of
 * times per second, so the chosen locale is cached per UUID and invalidated whenever the
 * player switches language or a profile reloads.</p>
 */
public final class LangService {

    private final BosketBallPlugin plugin;
    private final Map<UUID, Lang> cache = new ConcurrentHashMap<>();

    public LangService(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    /** Language of a player, falling back to the server default (English). */
    public Lang forPlayer(Player player) {
        if (player == null) {
            return Lang.of(plugin.settings().i18n().defaultLocale());
        }
        return cache.computeIfAbsent(player.getUniqueId(), id -> {
            PlayerProfile profile = plugin.statsManager().getIfPresent(id);
            String code = profile != null && profile.settings() != null
                ? profile.settings().language()
                : plugin.settings().i18n().defaultLocale();
            return Lang.of(code);
        });
    }

    public Lang forId(UUID id) {
        Player player = id == null ? null : plugin.getServer().getPlayer(id);
        return player != null ? forPlayer(player) : Lang.of(plugin.settings().i18n().defaultLocale());
    }

    /** Switches a player's language and persists it immediately. */
    public Lang setLanguage(Player player, String code) {
        if (player == null) {
            return Lang.of(Lang.Locale_.EN);
        }
        PlayerProfile profile = plugin.statsManager().getOrCreate(player);
        PlayerSettings settings = profile.settings();
        settings.setLanguage(Lang.Locale_.byCode(code).code());
        plugin.statsManager().saveProfileAsync(profile);
        Lang lang = Lang.of(settings.language());
        cache.put(player.getUniqueId(), lang);
        return lang;
    }

    public void invalidate(UUID id) {
        if (id != null) {
            cache.remove(id);
        }
    }

    public void clear() {
        cache.clear();
    }

    // =========================================================================
    //  SEND HELPERS
    // =========================================================================

    /** Sends a translated, prefixed message to one player. */
    public void send(Player player, String key, Map<String, String> placeholders) {
        Msg.send(player, forPlayer(player).get(key, placeholders));
    }

    public void send(Player player, String key) {
        send(player, key, null);
    }

    /** Sends a translated framed card. */
    public void sendCard(Player player, String titleKey, java.util.List<String> bodyKeys) {
        Lang lang = forPlayer(player);
        java.util.List<String> body = new java.util.ArrayList<>(bodyKeys.size());
        for (String key : bodyKeys) {
            body.add(lang.get(key));
        }
        Msg.sendFramedCard(player, lang.raw(titleKey), body);
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            send(p, key, placeholders);
        }
    }

    /** Convenience for non-player audiences (console) — always English. */
    public void sendTo(Audience audience, String key, Map<String, String> placeholders) {
        Msg.send(audience, Lang.of(Lang.Locale_.EN).get(key, placeholders));
    }

    /** Cycles to the next locale — used by the settings GUI button. */
    public Lang nextLanguage(Player player) {
        Lang current = forPlayer(player);
        return setLanguage(player, current.locale().next().code());
    }
}
