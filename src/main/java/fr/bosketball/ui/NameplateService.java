package fr.bosketball.ui;

import fr.bosketball.BosketBallPlugin;
import fr.bosketball.model.PlayerProfile;
import fr.bosketball.model.Rank;
import fr.bosketball.stats.StatsManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Round 37/38 — the rank nameplates shown above every player's head.
 *
 * <p>Why this replaces the scoreboard prefix: a vanilla nametag always paints its own translucent
 * black box behind the text, and there is no API to turn that box off (the only team option is
 * {@code NAME_TAG_VISIBILITY}). The owner's plates are pixel art with their own rim and shadow, so
 * the box has to go: every player's vanilla tag is hidden ({@code NAME_TAG_VISIBILITY = NEVER}) and
 * the plate is drawn by a {@link TextDisplay} that hangs above the head, exactly like the
 * CustomNameplates reference implementation does.
 *
 * <p>Round 38 — one entity per player, not two, and nothing but the nickname on it. The rank used
 * to be painted inside the plate next to the ELO; on a real head that made the line longer than the
 * bar, so the bar stopped covering the name. The rank is still visible in the tab list and the
 * sidebar, so the plate keeps the nickname alone.
 *
 * <p>The display is not persistent, never saved with the world, and removed the moment a player
 * leaves. A player never sees their own plate (that is the "invisible first person nametag" rule),
 * and a plate is hidden from any viewer who cannot see the owner (vanish, invisibility, death).
 */
public final class NameplateService {

    /** Height above the player's head, in blocks, where the plate hangs. */
    private static final double PLATE_LIFT = 0.30;
    /** Scoreboard team that hides the vanilla tags. */
    private static final String HIDE_TEAM = "bb_hidden_tag";
    /** How often (in ticks) the per-viewer visibility is re-applied. */
    private static final int VISIBILITY_PERIOD = 20;
    /** Distance squared below which a plate does not need a new teleport packet. */
    private static final double MOVE_EPSILON = 0.0016;
    /** How often (in ticks) plates of offline players are swept away. */
    private static final int SWEEP_PERIOD = 200;

    private record PlateRig(TextDisplay body, String signature, UUID worldId, double scale) {
    }

    private final BosketBallPlugin plugin;
    private final StatsManager statsManager;
    private final Map<UUID, PlateRig> rigs = new ConcurrentHashMap<>();
    private int tickCounter;
    private boolean warnedSpawn;

    public NameplateService(BosketBallPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    /** Called from the main tick loop; cheap when nothing changed. */
    public void tickAll() {
        if (!plugin.settings().nameplates().enabled()) {
            if (!rigs.isEmpty()) {
                shutdown();
            }
            return;
        }
        tickCounter++;
        boolean refreshVisibility = tickCounter % VISIBILITY_PERIOD == 0;
        boolean sweep = tickCounter % SWEEP_PERIOD == 0;

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        hideVanillaTags(online);

        for (Player player : online) {
            PlateRig rig = rigs.get(player.getUniqueId());
            if (rig != null && (!isUsable(rig, player))) {
                remove(player.getUniqueId());
                rig = null;
            }
            if (rig == null) {
                rig = spawn(player);
                if (rig == null) {
                    continue;
                }
            }
            updateText(player, rig);
            move(player, rig);
            if (refreshVisibility) {
                refreshVisibility(player, rig);
            }
        }
        if (sweep) {
            Set<UUID> onlineIds = new HashSet<>();
            for (Player p : online) {
                onlineIds.add(p.getUniqueId());
            }
            for (UUID id : new ArrayList<>(rigs.keySet())) {
                if (!onlineIds.contains(id)) {
                    remove(id);
                }
            }
        }
    }

    /** Removes the entity of one player (quit, kick, shutdown). */
    public void forget(UUID playerId) {
        if (playerId == null) {
            return;
        }
        remove(playerId);
    }

    /** Removes every plate and every hiding team; called on disable. */
    public void shutdown() {
        for (UUID id : new ArrayList<>(rigs.keySet())) {
            remove(id);
        }
        rigs.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            cleanHideTeam(player);
        }
    }

    /** Re-applies visibility for everyone (a player joined: they must not see their own plate). */
    public void refreshAll() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            PlateRig rig = rigs.get(player.getUniqueId());
            if (rig != null) {
                refreshVisibility(player, rig);
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // entity
    // -----------------------------------------------------------------------------------------
    private PlateRig spawn(Player player) {
        Location base = anchorFor(player);
        if (base == null || base.getWorld() == null) {
            return null;
        }
        double scale = scale();
        NameplateLayout.Plate layout = layoutFor(player);
        try {
            TextDisplay body = base.getWorld().spawn(base, TextDisplay.class, display -> configure(display, scale));
            body.text(layout.plate());
            PlateRig rig = new PlateRig(body, layout.signature(), base.getWorld().getUID(), scale);
            rigs.put(player.getUniqueId(), rig);
            refreshVisibility(player, rig);
            return rig;
        } catch (RuntimeException ex) {
            if (!warnedSpawn) {
                warnedSpawn = true;
                plugin.getLogger().warning("Could not spawn a nameplate: " + ex.getMessage());
            }
            return null;
        }
    }

    private void configure(TextDisplay display, double scale) {
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setLineWidth(1024);
        display.setSeeThrough(false);
        // No vanilla shadow and no vanilla backdrop: the plate's own rim is the background, and a
        // grey drop shadow under the ink would smear the pixel art.
        display.setShadowed(false);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setViewRange((float) Math.max(0.3, Math.min(4.0, plugin.settings().nameplates().viewRange())));
        display.setBrightness(new Display.Brightness(15, 15));
        display.setTeleportDuration(0);
        display.setTransformation(new Transformation(
            new Vector3f(0f, 0f, 0f),
            new Quaternionf(),
            new Vector3f((float) scale, (float) scale, (float) scale),
            new Quaternionf()
        ));
        display.setVisibleByDefault(false);
    }

    private boolean isUsable(PlateRig rig, Player player) {
        return rig.body() != null && rig.body().isValid()
            && Math.abs(rig.scale() - scale()) < 0.001
            && player.getWorld() != null && rig.worldId().equals(player.getWorld().getUID());
    }

    private void move(Player player, PlateRig rig) {
        Location anchor = anchorFor(player);
        if (anchor == null) {
            return;
        }
        Location body = rig.body().getLocation();
        if (!anchor.getWorld().equals(body.getWorld())
            || anchor.distanceSquared(body) > MOVE_EPSILON
            || tickCounter % 20 == 0) {
            rig.body().teleport(anchor);
        }
    }

    private Location anchorFor(Player player) {
        Location loc = player.getLocation();
        double eye = player.getEyeHeight();
        double height = Math.max(eye + 0.25, player.getHeight());
        return loc.clone().add(0, height + PLATE_LIFT, 0);
    }

    private void updateText(Player player, PlateRig rig) {
        NameplateLayout.Plate layout = layoutFor(player);
        if (layout.signature().equals(rig.signature())) {
            return;
        }
        rig.body().text(layout.plate());
        rigs.put(player.getUniqueId(), new PlateRig(rig.body(), layout.signature(), rig.worldId(), rig.scale()));
    }

    private NameplateLayout.Plate layoutFor(Player player) {
        PlayerProfile profile = statsManager.getIfPresent(player.getUniqueId());
        if (profile == null) {
            profile = statsManager.getOrCreate(player);
        }
        Rank rank = profile.effectiveRank();
        return NameplateLayout.build(rank, player.getName());
    }

    private double scale() {
        return Math.max(0.25, Math.min(3.0, plugin.settings().nameplates().scale()));
    }

    // -----------------------------------------------------------------------------------------
    // visibility
    // -----------------------------------------------------------------------------------------
    private void refreshVisibility(Player owner, PlateRig rig) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (shouldSee(viewer, owner, rig)) {
                viewer.showEntity(plugin, rig.body());
            } else {
                viewer.hideEntity(plugin, rig.body());
            }
        }
    }

    /** The owner never sees their own plate; nobody sees the plate of a hidden or dead player. */
    private boolean shouldSee(Player viewer, Player owner, PlateRig rig) {
        if (viewer.equals(owner)) {
            return false;
        }
        if (owner.isDead() || !owner.isOnline() || owner.getWorld() == null) {
            return false;
        }
        if (!viewer.getWorld().equals(owner.getWorld())) {
            return false;
        }
        if (owner.isInvisible()) {
            return false;
        }
        if (plugin.cinematicCamera() != null && plugin.cinematicCamera().isWatching(owner.getUniqueId())) {
            // A cinematic shot must stay clean.
            return false;
        }
        PlayerProfile ownerProfile = statsManager.getIfPresent(owner.getUniqueId());
        if (ownerProfile != null && ownerProfile.settings() != null
            && !ownerProfile.settings().overheadNameplates()) {
            return false;
        }
        try {
            if (!viewer.canSee(owner)) {
                return false;
            }
        } catch (RuntimeException ignored) {
            // canSee() is not available on every platform build; a plate is never worth an error.
        }
        return rig.body() != null && rig.body().isValid();
    }

    // -----------------------------------------------------------------------------------------
    // vanilla tags
    // -----------------------------------------------------------------------------------------
    /**
     * Hides every vanilla nametag. One team per scoreboard carries every online player with
     * {@code NAME_TAG_VISIBILITY = NEVER}: without this, the client would draw its own black box
     * and its own white name behind our plate.
     */
    private void hideVanillaTags(List<Player> online) {
        Set<Scoreboard> boards = new HashSet<>();
        for (Player player : online) {
            Scoreboard board = player.getScoreboard();
            if (board != null) {
                boards.add(board);
            }
        }
        for (Scoreboard board : boards) {
            Team team = board.getTeam(HIDE_TEAM);
            if (team == null) {
                try {
                    team = board.registerNewTeam(HIDE_TEAM);
                } catch (RuntimeException ex) {
                    continue;
                }
            }
            if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != Team.OptionStatus.NEVER) {
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
            for (Player player : online) {
                String name = player.getName();
                if (!team.hasEntry(name)) {
                    team.addEntry(name);
                }
            }
        }
    }

    private void cleanHideTeam(Player player) {
        Scoreboard board = player.getScoreboard();
        if (board == null) {
            return;
        }
        Team team = board.getTeam(HIDE_TEAM);
        if (team != null) {
            try {
                team.unregister();
            } catch (RuntimeException ignored) {
                // the board is being torn down anyway
            }
        }
    }

    private void remove(UUID playerId) {
        PlateRig rig = rigs.remove(playerId);
        if (rig == null) {
            return;
        }
        if (rig.body() != null && !rig.body().isDead()) {
            rig.body().remove();
        }
    }
}
