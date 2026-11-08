package fr.bosketball.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * The action bar of every player online, one {@link Hunt} each.
 *
 * <p>This is the "is my message still on screen" registry the HUD needed: a subsystem asks
 * {@link #onScreen(Player, HudSurface)} instead of re-deriving a lease of its own, and the answer
 * comes from the same object that decided to send the message.
 *
 * <p>Everything here is keyed by {@link UUID} and released on quit ({@code forget}). The map is a
 * {@link ConcurrentHashMap} because the aim HUD is read from the match tick while a toast can be
 * raised from an async callback (a webhook reply, a purchase confirmation).
 */
public final class HudSurfaceRegistry {

    private static final Map<UUID, Hunt> HUNTS = new ConcurrentHashMap<>();

    private HudSurfaceRegistry() {
    }

    /** The queue of one player, created on first use. */
    public static Hunt hunt(UUID playerId) {
        return HUNTS.computeIfAbsent(playerId, id -> new Hunt());
    }

    /** The queue of one player, or {@code null} when they have never been sent anything. */
    public static Hunt peek(UUID playerId) {
        return playerId == null ? null : HUNTS.get(playerId);
    }

    /** True when the player's action bar is held by a message — any message. */
    public static boolean busy(Player player) {
        if (player == null) {
            return false;
        }
        Hunt hunt = HUNTS.get(player.getUniqueId());
        return hunt != null && hunt.busy(System.currentTimeMillis());
    }

    /** True when {@code surface} is the message the player is reading right now. */
    public static boolean onScreen(Player player, HudSurface surface) {
        if (player == null || surface == null) {
            return false;
        }
        Hunt hunt = HUNTS.get(player.getUniqueId());
        return hunt != null && hunt.onScreen(surface, System.currentTimeMillis());
    }

    /** The frame on screen for one player, or {@code null}. */
    public static Hunt.Frame frameOf(Player player) {
        if (player == null) {
            return null;
        }
        Hunt hunt = HUNTS.get(player.getUniqueId());
        return hunt == null ? null : hunt.current(System.currentTimeMillis());
    }

    /** Releases a player's queue. Called from the single quit path. */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            HUNTS.remove(playerId);
        }
    }

    /** How many players are tracked (bounded-cache check in the tests). */
    public static int tracked() {
        return HUNTS.size();
    }

    /** Drops every queue: plugin disable. */
    public static void clearAll() {
        HUNTS.clear();
    }
}
