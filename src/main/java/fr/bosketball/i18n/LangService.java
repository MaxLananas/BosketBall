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

public final class LangService {

    private static volatile LangService instance;

    private final BosketBallPlugin plugin;
    private final Map<UUID, Lang> cache = new ConcurrentHashMap<>();

    public LangService(BosketBallPlugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static LangService get() {
        return instance;
    }

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

    public String text(Player player, String key) {
        return forPlayer(player).get(key);
    }

    public String caption(Player player, String key, String fallback) {
        String value = text(player, key);
        if (value == null || value.isBlank() || value.equals(key)) {
            value = fallback;
        }
        return value.toUpperCase(java.util.Locale.ROOT);
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        Msg.send(player, forPlayer(player).get(key, placeholders));
    }

    public void send(Player player, String key) {
        send(player, key, null);
    }

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

    public void sendTo(Audience audience, String key, Map<String, String> placeholders) {
        Msg.send(audience, Lang.of(Lang.Locale_.EN).get(key, placeholders));
    }

    public Lang nextLanguage(Player player) {
        Lang current = forPlayer(player);
        return setLanguage(player, current.locale().next().code());
    }
}
