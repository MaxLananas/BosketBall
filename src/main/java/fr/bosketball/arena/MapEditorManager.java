package fr.bosketball.arena;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.BallSkin;
import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * Interactive In-Game Custom Basketball Map Creator & 3D Blueprint Session Manager (/bb map ...)!
 */
public final class MapEditorManager {

    public static final NamespacedKey WAND_MODEL_KEY = new NamespacedKey(BallSkin.NAMESPACE, "court_wand");

    public static final class EditorSession {
        public String arenaId;
        public Vector courtPos1;
        public Vector courtPos2;
        public Double customFloorY;
        public Vector barrierPos1;
        public Vector barrierPos2;
        public boolean customBarrierDefined = false;

        EditorSession(String arenaId) {
            this.arenaId = arenaId.toLowerCase();
        }
    }

    private final BosketBallPlugin plugin;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final Set<UUID> previewViewers = new HashSet<>();

    public MapEditorManager(BosketBallPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isArchitectWand(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && WAND_MODEL_KEY.equals(meta.getItemModel());
    }

    public EditorSession getOrBindSession(Player player, String optionalArenaId) {
        if (optionalArenaId != null && !optionalArenaId.isBlank()) {
            Arena target = plugin.arenaManager().getArena(optionalArenaId);
            if (target != null) {
                EditorSession s = sessions.computeIfAbsent(player.getUniqueId(), k -> new EditorSession(target.id()));
                s.arenaId = target.id();
                return s;
            }
        }
        EditorSession existing = sessions.get(player.getUniqueId());
        if (existing != null && plugin.arenaManager().getArena(existing.arenaId) != null) {
            return existing;
        }
        Arena first = plugin.arenaManager().firstArenaOrNull();
        if (first != null) {
            EditorSession s = new EditorSession(first.id());
            sessions.put(player.getUniqueId(), s);
            return s;
        }
        return null;
    }

    public Arena createOrSelectCustomMap(Player creator, String id, String displayName) {
        String cleanId = id.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (cleanId.isEmpty()) {
            cleanId = "custom_map";
        }
        Arena arena = plugin.arenaManager().getArena(cleanId);
        Location loc = creator.getLocation();
        if (arena == null) {
            double fy = Math.floor(loc.getY());
            CourtGeometry geo = new CourtGeometry(loc.getBlockX() + 0.5, fy, loc.getBlockZ() + 0.5, 14.0, 8.0);
            arena = new Arena(cleanId, displayName, loc.getWorld().getName(), geo);
            arena.setCustomMap(true);
            arena.setJumbotronEnabled(false); // Never clutter custom maps unless requested via /bb map jumbotron here!
            arena.spawnDisplays();
            plugin.arenaManager().registerArena(arena);
        } else {
            arena.setCustomMap(true);
            if (displayName != null && !displayName.isBlank() && !displayName.equalsIgnoreCase(cleanId)) {
                arena.setDisplayName(displayName);
            }
            plugin.arenaManager().save();
        }

        EditorSession session = new EditorSession(arena.id());
        session.customFloorY = arena.geometry().floorY();
        sessions.put(creator.getUniqueId(), session);
        previewViewers.add(creator.getUniqueId());

        giveWand(creator, arena.id());
        return arena;
    }

    public void giveWand(Player player, String arenaId) {
        ItemStack wand = BallSkin.createArchitectWandItem(arenaId);
        player.getInventory().setItemInMainHand(wand);
        Msg.playPersonalPackSound(player, "ui.click", 0.9f, 1.2f);
    }

    public void setCourtCorner(Player player, Location blockOrPlayerLoc, boolean firstCorner) {
        EditorSession session = getOrBindSession(player, null);
        if (session == null) {
            Msg.send(player, Msg.error("Create a map first with <yellow>/bb map create <id> [name]</yellow>."));
            return;
        }
        Arena arena = plugin.arenaManager().getArena(session.arenaId);
        if (arena == null) {
            return;
        }
        arena.setWorldName(blockOrPlayerLoc.getWorld().getName());
        arena.setCustomMap(true);

        double topY = blockOrPlayerLoc.getBlockY() + 1.0;
        Vector pt = new Vector(blockOrPlayerLoc.getBlockX() + 0.5, topY, blockOrPlayerLoc.getBlockZ() + 0.5);
        if (firstCorner) {
            session.courtPos1 = pt;
            session.customFloorY = topY;
            Msg.send(player, Msg.info("Court Corner #1 set at <white>("
                + blockOrPlayerLoc.getBlockX() + ", " + (int) topY + ", " + blockOrPlayerLoc.getBlockZ()
                + ")</white> <dark_gray>•</dark_gray> Floor Y = <gold>" + String.format("%.1f", topY) + "</gold>"));
        } else {
            session.courtPos2 = pt;
            Msg.send(player, Msg.info("Court Corner #2 set at <white>("
                + blockOrPlayerLoc.getBlockX() + ", " + (int) topY + ", " + blockOrPlayerLoc.getBlockZ() + ")</white>"));
        }

        if (session.courtPos1 != null && session.courtPos2 != null) {
            double fy = session.customFloorY != null ? session.customFloorY : topY;
            double minX = Math.min(session.courtPos1.getX(), session.courtPos2.getX()) - 0.5;
            double maxX = Math.max(session.courtPos1.getX(), session.courtPos2.getX()) + 0.5;
            double minZ = Math.min(session.courtPos1.getZ(), session.courtPos2.getZ()) - 0.5;
            double maxZ = Math.max(session.courtPos1.getZ(), session.courtPos2.getZ()) + 0.5;
            arena.geometry().setCourtBounds(minX, minZ, maxX, maxZ, fy);

            if (!session.customBarrierDefined) {
                arena.geometry().setBarrierBounds(minX - 4.5, minZ - 4.5, maxX + 4.5, maxZ + 4.5, fy + 16.0);
            }
            plugin.arenaManager().saveAsync();
            Msg.send(player, Msg.success("Court out-of-bounds updated: <white>"
                + String.format("%.0fx%.0f blocks", (maxX - minX), (maxZ - minZ)) + "</white>."));
        }
        previewViewers.add(player.getUniqueId());
        Msg.playPersonalPackSound(player, "ui.click", 0.85f, firstCorner ? 1.1f : 1.28f);
    }

    public void setBarrierCorner(Player player, Location blockOrPlayerLoc, boolean firstCorner) {
        EditorSession session = getOrBindSession(player, null);
        if (session == null) {
            Msg.send(player, Msg.error("Create a map first with <yellow>/bb map create <id> [name]</yellow>."));
            return;
        }
        Arena arena = plugin.arenaManager().getArena(session.arenaId);
        if (arena == null) {
            return;
        }
        arena.setCustomMap(true);

        Vector pt = new Vector(blockOrPlayerLoc.getBlockX() + 0.5, blockOrPlayerLoc.getY(), blockOrPlayerLoc.getBlockZ() + 0.5);
        if (firstCorner) {
            session.barrierPos1 = pt;
            Msg.send(player, Msg.info("Barrier Corner #1 set at <white>("
                + blockOrPlayerLoc.getBlockX() + ", " + blockOrPlayerLoc.getBlockZ() + ")</white>"));
        } else {
            session.barrierPos2 = pt;
            Msg.send(player, Msg.info("Barrier Corner #2 set at <white>("
                + blockOrPlayerLoc.getBlockX() + ", " + blockOrPlayerLoc.getBlockZ() + ")</white>"));
        }

        if (session.barrierPos1 != null && session.barrierPos2 != null) {
            session.customBarrierDefined = true;
            double minX = Math.min(session.barrierPos1.getX(), session.barrierPos2.getX()) - 0.5;
            double maxX = Math.max(session.barrierPos1.getX(), session.barrierPos2.getX()) + 0.5;
            double minZ = Math.min(session.barrierPos1.getZ(), session.barrierPos2.getZ()) - 0.5;
            double maxZ = Math.max(session.barrierPos1.getZ(), session.barrierPos2.getZ()) + 0.5;
            arena.geometry().setBarrierBounds(minX, minZ, maxX, maxZ, arena.geometry().ceilingY());
            plugin.arenaManager().saveAsync();
            Msg.send(player, Msg.success("Arena barrier cage updated: <white>"
                + String.format("%.0fx%.0f blocks", (maxX - minX), (maxZ - minZ)) + "</white>."));
        }
        previewViewers.add(player.getUniqueId());
        Msg.playPersonalPackSound(player, "ui.click", 0.85f, firstCorner ? 0.95f : 1.18f);
    }

    public boolean togglePreview(Player player) {
        UUID id = player.getUniqueId();
        if (previewViewers.contains(id)) {
            previewViewers.remove(id);
            return false;
        }
        previewViewers.add(id);
        return true;
    }

    public void removePlayer(UUID playerId) {
        previewViewers.remove(playerId);
    }

    /**
     * Called every 10 ticks from BosketBallPlugin to render 3D blueprint particles for active map editors!
     */
    public void tickPreviews() {
        if (previewViewers.isEmpty()) {
            return;
        }
        var it = previewViewers.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                it.remove();
                continue;
            }
            EditorSession s = sessions.get(id);
            Arena arena = s != null ? plugin.arenaManager().getArena(s.arenaId) : plugin.arenaManager().firstArenaOrNull();
            if (arena != null) {
                arena.renderBlueprintPreview(p);
            }
        }
    }
}
