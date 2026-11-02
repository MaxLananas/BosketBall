package fr.bosketball.arena;

import fr.bosketball.model.TeamSide;
import fr.bosketball.physics.PhysicsMath;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Live Basketball Arena Instance (Supports 1-Command Generated Pro Stadiums AND 100% Custom User-Built Maps!).
 */
public final class Arena {

    public record SpawnPose(double x, double y, double z, float yaw, float pitch) {
        public static SpawnPose fromLocation(Location loc) {
            return new SpawnPose(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        }

        public Location toLocation(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    private final String id;
    private String displayName;
    private String worldName;
    private final CourtGeometry geometry;

    private boolean customMap = false;
    private boolean jumbotronEnabled = true;
    private Vector customJumbotronCenter = null;

    private final Map<TeamSide, List<SpawnPose>> customSpawns = new EnumMap<>(TeamSide.class);
    private SpawnPose lobbyExitSpawn = null;

    private HoopDisplaySet northHoopDisplay;
    private HoopDisplaySet southHoopDisplay;
    private final List<TextDisplay> jumbotronScreens = new ArrayList<>(4);

    public Arena(String id, String displayName, String worldName, CourtGeometry geometry) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.worldName = worldName;
        this.geometry = geometry;
        for (TeamSide side : TeamSide.values()) {
            customSpawns.put(side, new ArrayList<>());
        }
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String worldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public World world() {
        return Bukkit.getServer() != null ? Bukkit.getWorld(worldName) : null;
    }

    public CourtGeometry geometry() {
        return geometry;
    }

    public boolean isCustomMap() {
        return customMap;
    }

    public void setCustomMap(boolean customMap) {
        this.customMap = customMap;
    }

    public boolean isJumbotronEnabled() {
        return jumbotronEnabled;
    }

    public void setJumbotronEnabled(boolean jumbotronEnabled) {
        this.jumbotronEnabled = jumbotronEnabled;
    }

    public Vector customJumbotronCenter() {
        return customJumbotronCenter != null ? customJumbotronCenter.clone() : null;
    }

    public void setCustomJumbotronCenter(Vector pos) {
        this.customJumbotronCenter = pos != null ? pos.clone() : null;
    }

    public Map<TeamSide, List<SpawnPose>> allCustomSpawns() {
        return Collections.unmodifiableMap(customSpawns);
    }

    public void setCustomSpawn(TeamSide side, int slotIndex, Location loc) {
        List<SpawnPose> list = customSpawns.computeIfAbsent(side, k -> new ArrayList<>());
        int idx = Math.max(0, Math.min(9, slotIndex));
        while (list.size() <= idx) {
            list.add(SpawnPose.fromLocation(loc));
        }
        list.set(idx, SpawnPose.fromLocation(loc));
    }

    public void replaceCustomSpawns(TeamSide side, List<SpawnPose> poses) {
        List<SpawnPose> list = customSpawns.computeIfAbsent(side, k -> new ArrayList<>());
        list.clear();
        if (poses != null) {
            list.addAll(poses);
        }
    }

    public SpawnPose lobbyExitSpawn() {
        return lobbyExitSpawn;
    }

    public void setLobbyExitSpawn(SpawnPose pose) {
        this.lobbyExitSpawn = pose;
    }

    public Location lobbyExitLocationOrNull() {
        World w = world();
        if (w == null || lobbyExitSpawn == null) {
            return null;
        }
        return lobbyExitSpawn.toLocation(w);
    }

    public HoopDisplaySet northHoopDisplay() {
        return northHoopDisplay;
    }

    public HoopDisplaySet southHoopDisplay() {
        return southHoopDisplay;
    }

    public Location centerCourtLocation() {
        World w = world();
        return new Location(w, geometry.centerX(), geometry.floorY() + 0.05, geometry.centerZ(), 0f, 0f);
    }

    public Location teamSpawn(TeamSide side, int slotIndex) {
        World w = world();
        List<SpawnPose> configured = customSpawns.get(side);
        if (configured != null && !configured.isEmpty()) {
            SpawnPose pose = configured.get(Math.floorMod(slotIndex, configured.size()));
            return pose.toLocation(w);
        }

        // Smart default spawns oriented toward each team's target basket!
        Vector targetRim = targetRimForTeam(side == TeamSide.SPECTATOR ? TeamSide.IGNIS : side);
        double lateral = ((slotIndex % 3) - 1) * 2.2;
        if (side == TeamSide.IGNIS || side == TeamSide.FROST) {
            Vector center = new Vector(geometry.centerX(), geometry.floorY() + 0.1, geometry.centerZ());
            Vector towardOwnHalf = center.clone().subtract(new Vector(targetRim.getX(), center.getY(), targetRim.getZ()));
            if (towardOwnHalf.lengthSquared() > 1e-4) {
                towardOwnHalf.normalize();
            } else {
                towardOwnHalf = new Vector(0, 0, side == TeamSide.IGNIS ? -1 : 1);
            }
            Vector right = new Vector(-towardOwnHalf.getZ(), 0, towardOwnHalf.getX());
            Vector spawnVec = center.add(towardOwnHalf.multiply(Math.min(5.5, geometry.halfLength() * 0.36)))
                .add(right.multiply(lateral));
            Location loc = spawnVec.toLocation(w);
            loc.setDirection(targetRim.clone().subtract(spawnVec).setY(0));
            return loc;
        }
        // Spectator sideline seat
        return new Location(w, geometry.maxX() + 2.8, geometry.floorY() + 2.2, geometry.centerZ(), 90f, 12f);
    }

    public Vector targetRimForTeam(TeamSide offensiveTeam) {
        return offensiveTeam == TeamSide.IGNIS ? geometry.southHoopRim() : geometry.northHoopRim();
    }

    /**
     * Keeps the 3D basketball physically inside the map's containment barrier cage (bouncing cleanly off walls & ceiling!).
     */
    public boolean clampBallInsideBarrier(Vector pos, Vector vel, double restitution) {
        double r = PhysicsMath.BALL_RADIUS;
        double minX = geometry.barrierMinX() + r;
        double maxX = geometry.barrierMaxX() - r;
        double minZ = geometry.barrierMinZ() + r;
        double maxZ = geometry.barrierMaxZ() - r;
        double maxY = geometry.ceilingY() - r;

        boolean bounced = false;
        if (pos.getX() < minX) {
            pos.setX(minX);
            if (vel.getX() < 0) {
                vel.setX(-vel.getX() * restitution);
            }
            bounced = true;
        } else if (pos.getX() > maxX) {
            pos.setX(maxX);
            if (vel.getX() > 0) {
                vel.setX(-vel.getX() * restitution);
            }
            bounced = true;
        }

        if (pos.getZ() < minZ) {
            pos.setZ(minZ);
            if (vel.getZ() < 0) {
                vel.setZ(-vel.getZ() * restitution);
            }
            bounced = true;
        } else if (pos.getZ() > maxZ) {
            pos.setZ(maxZ);
            if (vel.getZ() > 0) {
                vel.setZ(-vel.getZ() * restitution);
            }
            bounced = true;
        }

        if (pos.getY() > maxY) {
            pos.setY(maxY);
            if (vel.getY() > 0) {
                vel.setY(-vel.getY() * restitution);
            }
            bounced = true;
        }

        if (bounced) {
            World w = world();
            if (w != null && vel.lengthSquared() > 0.02) {
                w.spawnParticle(Particle.ELECTRIC_SPARK, pos.toLocation(w), 5, 0.08, 0.08, 0.08, 0.02);
            }
        }
        return bounced;
    }

    /**
     * Keeps active players bounded inside the arena's containment barrier (pushing back at walls or safety-TPing if fallen!).
     */
    public void enforcePlayerContainment(Player player, TeamSide side) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            player.teleport(teamSpawn(side, 0));
            return;
        }

        double margin = 0.42;
        double minX = geometry.barrierMinX() + margin;
        double maxX = geometry.barrierMaxX() - margin;
        double minZ = geometry.barrierMinZ() + margin;
        double maxZ = geometry.barrierMaxZ() - margin;
        double minY = geometry.floorY() - 2.2;

        // Fallen under the map or wandered way outside -> clean instant teleport back to team spawn!
        if (loc.getY() < minY
            || loc.getX() < minX - 3.0 || loc.getX() > maxX + 3.0
            || loc.getZ() < minZ - 3.0 || loc.getZ() > maxZ + 3.0) {
            player.teleport(teamSpawn(side, 0));
            player.setFallDistance(0f);
            Msg.playPersonalPackSound(player, "ui.click", 0.7f, 0.85f);
            player.sendActionBar(Msg.parse("<aqua>🛡 ʀᴇᴛᴏᴜʀ ᴅᴀɴꜱ ʟ'ᴇɴᴄᴇɪɴᴛᴇ ᴅᴜ ᴛᴇʀʀᴀɪɴ !</aqua>"));
            return;
        }

        boolean clamped = false;
        double nx = loc.getX();
        double nz = loc.getZ();
        Vector v = player.getVelocity();
        if (nx < minX) {
            nx = minX + 0.12;
            v.setX(Math.max(0.08, v.getX()));
            clamped = true;
        } else if (nx > maxX) {
            nx = maxX - 0.12;
            v.setX(Math.min(-0.08, v.getX()));
            clamped = true;
        }
        if (nz < minZ) {
            nz = minZ + 0.12;
            v.setZ(Math.max(0.08, v.getZ()));
            clamped = true;
        } else if (nz > maxZ) {
            nz = maxZ - 0.12;
            v.setZ(Math.min(-0.08, v.getZ()));
            clamped = true;
        }

        if (clamped) {
            Location safe = new Location(loc.getWorld(), nx, loc.getY(), nz, loc.getYaw(), loc.getPitch());
            player.teleport(safe);
            player.setVelocity(v);
            player.spawnParticle(Particle.ELECTRIC_SPARK, safe.clone().add(0, 1.0, 0), 6, 0.18, 0.35, 0.18, 0.01);
        }
    }

    public void spawnDisplays() {
        despawnDisplays();
        World w = world();
        if (w == null) {
            return;
        }
        purgeOrphanedCourtEntities(w);

        // Only place physical Jumbotron & Stanchion blocks on auto-generated stadiums — NEVER overwrite custom maps!
        if (!customMap) {
            ArenaBuilder.ensureHoopAndJumbotronStructures(this);
        }

        this.northHoopDisplay = new HoopDisplaySet(
            w, geometry.northHoopRim(), geometry.northHoopForward(), geometry.northSpawn3dHoop()
        );
        this.southHoopDisplay = new HoopDisplaySet(
            w, geometry.southHoopRim(), geometry.southHoopForward(), geometry.southSpawn3dHoop()
        );
        this.northHoopDisplay.spawn();
        this.southHoopDisplay.spawn();

        if (jumbotronEnabled) {
            Vector jCenter = customJumbotronCenter != null
                ? customJumbotronCenter
                : new Vector(geometry.centerX(), geometry.floorY() + 9.75, geometry.centerZ());
            float[] yaws = {0f, 90f, 180f, -90f};
            double[][] offsets = {{0.0, 2.54}, {-2.54, 0.0}, {0.0, -2.54}, {2.54, 0.0}};
            for (int i = 0; i < 4; i++) {
                Location loc = new Location(
                    w,
                    jCenter.getX() + offsets[i][0],
                    jCenter.getY(),
                    jCenter.getZ() + offsets[i][1],
                    yaws[i],
                    0f
                );
                TextDisplay screen = w.spawn(loc, TextDisplay.class, td -> {
                    td.setPersistent(false);
                    td.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
                    td.setBillboard(Display.Billboard.FIXED);
                    td.setLineWidth(165);
                    td.setBackgroundColor(Color.fromARGB(240, 10, 14, 24));
                    td.setShadowed(true);
                    td.setSeeThrough(false);
                    td.setViewRange(2.8f);
                    td.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new Quaternionf(),
                        new Vector3f(0.95f, 0.95f, 0.95f),
                        new Quaternionf()
                    ));
                });
                jumbotronScreens.add(screen);
            }
            updateJumbotron("ɪɢɴɪꜱ", 0, "ᴛᴇʀʀᴀɪɴ ʟɪʙʀᴇ", 24, 0, "ꜰʀᴏꜱᴛ");
        }
    }

    private void purgeOrphanedCourtEntities(World w) {
        Location center = new Location(w, geometry.centerX(), geometry.floorY() + 5.0, geometry.centerZ());
        double rx = Math.max(geometry.halfWidth() + 6.0, (geometry.barrierMaxX() - geometry.barrierMinX()) * 0.55);
        double rz = Math.max(geometry.halfLength() + 6.0, (geometry.barrierMaxZ() - geometry.barrierMinZ()) * 0.55);
        int minChunkX = ((int) Math.floor(geometry.centerX() - rx)) >> 4;
        int maxChunkX = ((int) Math.floor(geometry.centerX() + rx)) >> 4;
        int minChunkZ = ((int) Math.floor(geometry.centerZ() - rz)) >> 4;
        int maxChunkZ = ((int) Math.floor(geometry.centerZ() + rz)) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                w.getChunkAt(cx, cz);
            }
        }
        for (org.bukkit.entity.Entity ent : w.getNearbyEntities(center, rx, 16.0, rz)) {
            if (ent.getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)) {
                ent.remove();
            } else if (!customMap && (ent instanceof Display || ent instanceof org.bukkit.entity.Interaction)) {
                ent.remove();
            }
        }
    }

    public void despawnDisplays() {
        if (northHoopDisplay != null) {
            northHoopDisplay.despawn();
            northHoopDisplay = null;
        }
        if (southHoopDisplay != null) {
            southHoopDisplay.despawn();
            southHoopDisplay = null;
        }
        for (TextDisplay td : jumbotronScreens) {
            if (td != null) {
                td.remove();
            }
        }
        jumbotronScreens.clear();
    }

    public void tickDisplays(int shotClockSeconds) {
        if (northHoopDisplay != null) {
            northHoopDisplay.tickAnimation();
            northHoopDisplay.updateShotClock(shotClockSeconds);
        }
        if (southHoopDisplay != null) {
            southHoopDisplay.tickAnimation();
            southHoopDisplay.updateShotClock(shotClockSeconds);
        }
    }

    public void updateJumbotron(String home, int homeScore, String statusSmallCaps, int shotClock, int awayScore, String away) {
        if (jumbotronScreens.isEmpty()) {
            return;
        }
        String content = "<gradient:#FF7A18:#FFD43B><bold>✦ " + displayName + " ✦</bold></gradient>\n"
            + "<#FF4D3D><bold>" + home + "</bold></#FF4D3D> <yellow><bold>" + homeScore + "</bold></yellow>"
            + " <dark_gray>—</dark_gray> "
            + "<yellow><bold>" + awayScore + "</bold></yellow> <#3DB8FF><bold>" + away + "</bold></#3DB8FF>\n"
            + "<white>" + statusSmallCaps + "</white> <dark_gray>•</dark_gray> <gold>" + shotClock + "ꜱ</gold>";
        var parsed = Msg.parse(content);
        for (TextDisplay td : jumbotronScreens) {
            if (td != null && td.isValid()) {
                td.text(parsed);
            }
        }
    }

    /**
     * Renders a crisp 3D holographic particle blueprint of court out-of-bounds, barrier walls, hoops & spawns for map makers!
     */
    public void renderBlueprintPreview(Player viewer) {
        if (viewer == null || !viewer.isOnline() || viewer.getWorld() == null || !viewer.getWorld().getName().equals(worldName)) {
            return;
        }
        Particle.DustOptions goldLine = new Particle.DustOptions(Color.fromRGB(255, 212, 59), 1.1f);
        Particle.DustOptions aquaBarrier = new Particle.DustOptions(Color.fromRGB(58, 224, 255), 1.0f);
        Particle.DustOptions ignisDust = new Particle.DustOptions(Color.fromRGB(255, 77, 61), 1.35f);
        Particle.DustOptions frostDust = new Particle.DustOptions(Color.fromRGB(61, 184, 255), 1.35f);

        double fy = geometry.floorY() + 0.08;
        // 1. Gold Court Out-of-Bounds Perimeter
        for (double x = geometry.minX(); x <= geometry.maxX(); x += 1.5) {
            viewer.spawnParticle(Particle.DUST, x, fy, geometry.minZ(), 1, goldLine);
            viewer.spawnParticle(Particle.DUST, x, fy, geometry.maxZ(), 1, goldLine);
        }
        for (double z = geometry.minZ(); z <= geometry.maxZ(); z += 1.5) {
            viewer.spawnParticle(Particle.DUST, geometry.minX(), fy, z, 1, goldLine);
            viewer.spawnParticle(Particle.DUST, geometry.maxX(), fy, z, 1, goldLine);
        }

        // 2. Aqua Containment Barrier Corners & Top Frame
        double[][] bCorners = {
            {geometry.barrierMinX(), geometry.barrierMinZ()},
            {geometry.barrierMaxX(), geometry.barrierMinZ()},
            {geometry.barrierMinX(), geometry.barrierMaxZ()},
            {geometry.barrierMaxX(), geometry.barrierMaxZ()}
        };
        for (double[] c : bCorners) {
            for (double y = fy; y <= Math.min(fy + 10.0, geometry.ceilingY()); y += 1.4) {
                viewer.spawnParticle(Particle.DUST, c[0], y, c[1], 1, aquaBarrier);
            }
        }
        for (double x = geometry.barrierMinX(); x <= geometry.barrierMaxX(); x += 2.2) {
            viewer.spawnParticle(Particle.DUST, x, fy + 0.25, geometry.barrierMinZ(), 1, aquaBarrier);
            viewer.spawnParticle(Particle.DUST, x, fy + 0.25, geometry.barrierMaxZ(), 1, aquaBarrier);
        }
        for (double z = geometry.barrierMinZ(); z <= geometry.barrierMaxZ(); z += 2.2) {
            viewer.spawnParticle(Particle.DUST, geometry.barrierMinX(), fy + 0.25, z, 1, aquaBarrier);
            viewer.spawnParticle(Particle.DUST, geometry.barrierMaxX(), fy + 0.25, z, 1, aquaBarrier);
        }

        // 3. Hoop Rings + Facing Direction Arrows + Team Spawns
        drawRimRingPreview(viewer, geometry.southHoopRim(), geometry.southHoopForward(), ignisDust);
        drawRimRingPreview(viewer, geometry.northHoopRim(), geometry.northHoopForward(), frostDust);

        Location iSpawn = teamSpawn(TeamSide.IGNIS, 0);
        Location fSpawn = teamSpawn(TeamSide.FROST, 0);
        viewer.spawnParticle(Particle.DUST, iSpawn.getX(), iSpawn.getY() + 0.3, iSpawn.getZ(), 6, 0.25, 0.2, 0.25, ignisDust);
        viewer.spawnParticle(Particle.DUST, fSpawn.getX(), fSpawn.getY() + 0.3, fSpawn.getZ(), 6, 0.25, 0.2, 0.25, frostDust);
    }

    private void drawRimRingPreview(Player viewer, Vector rim, Vector fwd, Particle.DustOptions dust) {
        for (int i = 0; i < 12; i++) {
            double a = (Math.PI * 2.0 * i) / 12.0;
            viewer.spawnParticle(
                Particle.DUST,
                rim.getX() + Math.cos(a) * PhysicsMath.RIM_RADIUS,
                rim.getY(),
                rim.getZ() + Math.sin(a) * PhysicsMath.RIM_RADIUS,
                1, dust
            );
        }
        for (double d = 0.4; d <= 1.8; d += 0.45) {
            viewer.spawnParticle(
                Particle.DUST,
                rim.getX() + fwd.getX() * d,
                rim.getY(),
                rim.getZ() + fwd.getZ() * d,
                1, dust
            );
        }
    }
}
