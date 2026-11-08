package fr.bosketball.arena;

import fr.bosketball.util.Palette;
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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Arena {

    public enum CourtLinesPreset {
        FULL,
        STREET,
        MINIMAL;

        public static CourtLinesPreset fromString(String raw) {
            if (raw == null) {
                return FULL;
            }
            return switch (raw.trim().toUpperCase( java.util.Locale.ROOT)) {
                case "STREET", "HALF", "SIMPLE" -> STREET;
                case "MINIMAL", "PERIMETER", "OUTLINE" -> MINIMAL;
                default -> FULL;
            };
        }
    }

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

    private boolean courtLinesEnabled = true;
    private CourtLinesPreset courtLinesPreset = CourtLinesPreset.FULL;
    /**
     * The playing surface itself: one flat display covering the whole in-bounds rectangle.
     *
     * <p>A court used to be lines only, so a custom map made of sand showed white tape lying on
     * sand and the floor read as "not a court". The surface is drawn first and the paint on top of
     * it, so the whole playable area is the court colour and the lines have something to belong to.
     */
    /** {@code null} = auto: paint only when the map brings no painted court of its own. */
    private Boolean courtFloorOverride = null;
    private Material courtFloorMaterial = Material.BLUE_CONCRETE;
    /** Lift above the block surface, in blocks: the floor sits 1.5 mm up, the paint 4 mm up. */
    private static final float COURT_FLOOR_LIFT = 0.0015f;
    private static final float COURT_FLOOR_THICKNESS = 0.0015f;
    /** The paint sits above the floor slab so the two never fight for the same pixel. */
    private static final double COURT_PAINT_LIFT = 0.0045;

    private Material courtLinePrimaryMaterial = Material.WHITE_CONCRETE;
    private Material courtLineAccentMaterial = Material.YELLOW_CONCRETE;
    private double courtLineWidth = 0.095;

    private final Map<TeamSide, List<SpawnPose>> customSpawns = new EnumMap<>(TeamSide.class);
    private SpawnPose lobbyExitSpawn = null;

    private HoopDisplaySet northHoopDisplay;
    private HoopDisplaySet southHoopDisplay;
    private final List<TextDisplay> jumbotronScreens = new ArrayList<>(4);
    private final List<BlockDisplay> courtSurfaceDisplays = new ArrayList<>();
    private String lastJumbotronContent = "";

    public Arena(String id, String displayName, String worldName, CourtGeometry geometry) {
        this.id = sanitizeId(id);
        this.displayName = sanitizeDisplayName(displayName, this.id);
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
        this.displayName = sanitizeDisplayName(displayName, id);
    }

    private static String sanitizeId(String raw) {
        if (raw == null) {
            return "arena";
        }
        String clean = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return clean.isEmpty() ? "arena" : clean;
    }

    private static String sanitizeDisplayName(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String plain = Msg.MM.stripTags(raw)
            .replace('<', ' ')
            .replace('>', ' ')
            .replaceAll("\\p{Cc}+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return plain.isEmpty() ? fallback : plain;
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
        if (customMap && this.courtLineAccentMaterial == Material.YELLOW_CONCRETE) {

            this.courtLineAccentMaterial = Material.WHITE_CONCRETE;
        }
    }

    public boolean isCourtLinesEnabled() {
        return courtLinesEnabled;
    }

    public void setCourtLinesEnabled(boolean courtLinesEnabled) {
        this.courtLinesEnabled = courtLinesEnabled;
    }

    public CourtLinesPreset courtLinesPreset() {
        return courtLinesPreset;
    }

    public void setCourtLinesPreset(CourtLinesPreset preset) {
        this.courtLinesPreset = preset != null ? preset : CourtLinesPreset.FULL;
    }

    public static Material resolveBlockMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String norm = raw.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_').replace("MINECRAFT:", "");
        try {
            Material byEnum = Material.valueOf(norm);
            if (isUsableBlockMaterial(byEnum)) {
                return byEnum;
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            Material matched = Material.matchMaterial(raw.trim());
            if (isUsableBlockMaterial(matched)) {
                return matched;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public static boolean isUsableBlockMaterial(Material mat) {
        if (mat == null || mat == Material.AIR) {
            return false;
        }
        try {
            return mat.isBlock();
        } catch (Throwable ignored) {
            return !mat.name().endsWith("_SWORD") && !mat.name().endsWith("_INGOT") && !mat.name().equals("PAPER");
        }
    }

    public Material courtLinePrimaryMaterial() {
        return courtLinePrimaryMaterial;
    }

    public void setCourtLinePrimaryMaterial(Material mat) {
        if (isUsableBlockMaterial(mat)) {
            this.courtLinePrimaryMaterial = mat;
        }
    }

    public Material courtLineAccentMaterial() {
        return courtLineAccentMaterial;
    }

    public void setCourtLineAccentMaterial(Material mat) {
        if (isUsableBlockMaterial(mat)) {
            this.courtLineAccentMaterial = mat;
        }
    }

    public boolean isCourtFloorEnabled() {
        if (courtFloorOverride != null) {
            return courtFloorOverride;
        }
        // Auto: an arena we build ourselves already has a deck of its own, and a schematic map that
        // ships a painted court keeps it. Everything else - a sand deck with tape on it - gets the
        // blue floor, because that is the one court where the surface is the problem.
        return customMap && !geometry.hasPaintedCourt();
    }

    public boolean isCourtFloorAuto() {
        return courtFloorOverride == null;
    }

    public void setCourtFloorEnabled(boolean enabled) {
        this.courtFloorOverride = enabled;
    }

    public void setCourtFloorAuto() {
        this.courtFloorOverride = null;
    }

    public Material courtFloorMaterial() {
        return courtFloorMaterial;
    }

    public void setCourtFloorMaterial(Material mat) {
        if (isUsableBlockMaterial(mat)) {
            this.courtFloorMaterial = mat;
        }
    }

    public double courtLineWidth() {
        return courtLineWidth;
    }

    public void setCourtLineWidth(double width) {
        this.courtLineWidth = Math.clamp(width, 0.04, 0.30);
    }

    public void refreshCourtLines() {
        for (BlockDisplay bd : courtSurfaceDisplays) {
            if (bd != null) {
                bd.remove();
            }
        }
        courtSurfaceDisplays.clear();
        World w = world();
        if (w == null) {
            return;
        }
        spawnCourtSurface(w);
    }

    /**
     * The whole surface, floor first then paint.
     *
     * <p>With our own floor down, our own lines go on top of it even on a map that baked its own:
     * a solid court colour with no lines on it would be worse than the map's own paint.
     */
    private void spawnCourtSurface(World w) {
        boolean floor = isCourtFloorEnabled();
        if (floor) {
            spawnCourtFloor(w);
        }
        if (courtLinesEnabled && (!geometry.bakedCourtLines() || floor)) {
            spawnFlushCourtLines(w);
        }
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

    public boolean areDisplaysHealthy() {
        if (northHoopDisplay == null || !northHoopDisplay.isHealthy()) {
            return false;
        }
        if (southHoopDisplay == null || !southHoopDisplay.isHealthy()) {
            return false;
        }
        if (jumbotronEnabled) {
            if (jumbotronScreens.isEmpty()) {
                return false;
            }
            for (TextDisplay td : jumbotronScreens) {
                if (td == null || !td.isValid()) {
                    return false;
                }
            }
        }
        if (isCourtFloorEnabled() || (courtLinesEnabled && !geometry.bakedCourtLines())) {
            if (courtSurfaceDisplays.isEmpty()) {
                return false;
            }
            for (BlockDisplay bd : courtSurfaceDisplays) {
                if (bd == null || !bd.isValid()) {
                    return false;
                }
            }
        }
        return true;
    }

    public void ensureDisplaysHealthy() {
        if (!areDisplaysHealthy()) {
            spawnDisplays();
        }
    }

    public Location centerCourtLocation() {
        World w = world();
        return new Location(w, geometry.centerX(), geometry.floorY() + 0.05, geometry.centerZ(), 0f, 0f);
    }

    public Location center() {
        return centerCourtLocation();
    }

    public Location teamSpawn(TeamSide side, int slotIndex) {
        World w = world();
        List<SpawnPose> configured = customSpawns.get(side);
        if (configured != null && !configured.isEmpty()) {
            SpawnPose pose = configured.get(Math.floorMod(slotIndex, configured.size()));
            return pose.toLocation(w);
        }

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

        return new Location(w, geometry.maxX() + 2.8, geometry.floorY() + 2.2, geometry.centerZ(), 90f, 12f);
    }

    public Vector targetRimForTeam(TeamSide offensiveTeam) {
        return offensiveTeam == TeamSide.IGNIS ? geometry.southHoopRim() : geometry.northHoopRim();
    }

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

        if (loc.getY() < minY
            || loc.getX() < minX - 3.0 || loc.getX() > maxX + 3.0
            || loc.getZ() < minZ - 3.0 || loc.getZ() > maxZ + 3.0) {
            player.teleport(teamSpawn(side, 0));
            player.setFallDistance(0f);
            Msg.playPersonalPackSound(player, "ui.click", 0.7f, 0.85f);
            Msg.rail(player, fr.bosketball.util.HudSurface.RULE, "<" + Palette.ICE
                + "><bold>Back in bounds</bold></" + Palette.ICE + ">");
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
            double overflow = Math.max(
                Math.max(minX - loc.getX(), loc.getX() - maxX),
                Math.max(minZ - loc.getZ(), loc.getZ() - maxZ)
            );
            Location safe = new Location(loc.getWorld(), nx, loc.getY(), nz, loc.getYaw(), loc.getPitch());

            if (!player.isHandRaised() || overflow >= 1.35) {
                player.teleport(safe);
            }
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

        if (!customMap) {
            ArenaBuilder.ensureHoopAndJumbotronStructures(this);
        }
        spawnCourtSurface(w);

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
            updateJumbotron("ɪɢɴɪꜱ", 0, "ᴏᴘᴇɴ ᴄᴏᴜʀᴛ", 24, 0, "ꜰʀᴏꜱᴛ");
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
            // The live ball is a display entity inside the court too. Sweeping it away mid-flight
            // is a ball that vanishes in the air, so it is never an orphan.
            if (ent.getScoreboardTags().contains(fr.bosketball.physics.BasketballEntity.LIVE_BALL_TAG)) {
                continue;
            }
            if (ent.getScoreboardTags().contains(HoopDisplaySet.DISPLAY_TAG)) {
                ent.remove();
            } else if (!customMap && (ent instanceof Display || ent instanceof org.bukkit.entity.Interaction)) {
                ent.remove();
            }
        }
    }

    public void despawnDisplays() {
        lastJumbotronContent = "";
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
        for (BlockDisplay bd : courtSurfaceDisplays) {
            if (bd != null) {
                bd.remove();
            }
        }
        courtSurfaceDisplays.clear();
    }

    /**
     * One display for the whole court floor: the in-bounds rectangle, exactly.
     *
     * <p>It is one entity, not a block per square, so a 30 x 18 court costs one draw call. The
     * rectangle is {@link CourtGeometry#minX()}..{@link CourtGeometry#maxX()} by
     * {@link CourtGeometry#minZ()}..{@link CourtGeometry#maxZ()} — the same numbers the
     * out-of-bounds rule reads — so the visible floor edge is the real boundary, and the sand
     * stops where the court stops.
     */
    private void spawnCourtFloor(World w) {
        double x0 = geometry.minX();
        double x1 = geometry.maxX();
        double z0 = geometry.minZ();
        double z1 = geometry.maxZ();
        float width = (float) Math.abs(x1 - x0);
        float length = (float) Math.abs(z1 - z0);
        if (width < 1.0f || length < 1.0f) {
            return;
        }
        Location loc = new Location(w, (x0 + x1) * 0.5, geometry.floorY() + COURT_FLOOR_LIFT,
            (z0 + z1) * 0.5, 0f, 0f);
        BlockDisplay bd = w.spawn(loc, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBlock(courtFloorMaterial.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(6.0f);
            d.setTransformation(new Transformation(
                new Vector3f(-width * 0.5f, 0f, -length * 0.5f),
                new Quaternionf(),
                new Vector3f(width, COURT_FLOOR_THICKNESS, length),
                new Quaternionf()
            ));
        });
        courtSurfaceDisplays.add(bd);
    }

    private void spawnFlushCourtLines(World w) {
        double cx = geometry.centerX();
        double fy = geometry.floorY() + COURT_PAINT_LIFT;
        double cz = geometry.centerZ();
        double lw = courtLineWidth;
        Material primaryMat = courtLinePrimaryMaterial;
        Material accentMat = courtLineAccentMaterial;

        Vector nRim = geometry.northHoopRim();
        Vector sRim = geometry.southHoopRim();
        boolean eastWest = Math.abs(sRim.getX() - nRim.getX()) > Math.abs(sRim.getZ() - nRim.getZ());

        Vector uAxis = eastWest ? new Vector(0, 0, 1) : new Vector(1, 0, 0);
        Vector vAxis = eastWest ? new Vector(1, 0, 0) : new Vector(0, 0, 1);
        double halfU = eastWest ? geometry.halfLength() : geometry.halfWidth();
        double halfV = eastWest ? geometry.halfWidth() : geometry.halfLength();
        // The perimeter is the out-of-bounds line, not a decorative box 0.5 blocks inside it: the
        // playable rectangle is halfWidth + 0.5 either side, which is where the floor ends.
        double edgeU = halfU + 0.5;
        double edgeV = halfV + 0.5;

        spawnBasisTape(w, cx, cz, uAxis, vAxis, -edgeU, -edgeV, +edgeU, -edgeV, fy, lw, primaryMat);
        spawnBasisTape(w, cx, cz, uAxis, vAxis, -edgeU, +edgeV, +edgeU, +edgeV, fy, lw, primaryMat);
        spawnBasisTape(w, cx, cz, uAxis, vAxis, -edgeU, -edgeV, -edgeU, +edgeV, fy, lw, primaryMat);
        spawnBasisTape(w, cx, cz, uAxis, vAxis, +edgeU, -edgeV, +edgeU, +edgeV, fy, lw, primaryMat);
        spawnBasisTape(w, cx, cz, uAxis, vAxis, -edgeU, 0.0, +edgeU, 0.0, fy, lw, primaryMat);

        if (courtLinesPreset != CourtLinesPreset.MINIMAL) {
            double cRad = Math.min(2.35, Math.min(halfU, halfV) * 0.28);
            for (int i = 0; i < 18; i++) {
                double a0 = (Math.PI * 2.0 * i) / 18.0;
                double a1 = (Math.PI * 2.0 * (i + 1)) / 18.0;
                spawnBasisTape(
                    w, cx, cz, uAxis, vAxis,
                    Math.cos(a0) * cRad, Math.sin(a0) * cRad,
                    Math.cos(a1) * cRad, Math.sin(a1) * cRad,
                    fy, lw, accentMat
                );
            }
        }

        double nV = (nRim.getX() - cx) * vAxis.getX() + (nRim.getZ() - cz) * vAxis.getZ();
        Vector negRim = nV <= 0 ? nRim : sRim;
        Vector posRim = nV <= 0 ? sRim : nRim;
        double negRimU = (negRim.getX() - cx) * uAxis.getX() + (negRim.getZ() - cz) * uAxis.getZ();
        double negRimV = (negRim.getX() - cx) * vAxis.getX() + (negRim.getZ() - cz) * vAxis.getZ();
        double posRimU = (posRim.getX() - cx) * uAxis.getX() + (posRim.getZ() - cz) * uAxis.getZ();
        double posRimV = (posRim.getX() - cx) * vAxis.getX() + (posRim.getZ() - cz) * vAxis.getZ();

        double keyHalfW = Math.min(2.5, halfU * 0.34);
        double keyDepth = Math.min(5.5, halfV * 0.42);

        if (courtLinesPreset != CourtLinesPreset.MINIMAL) {

            spawnBasisTape(w, cx, cz, uAxis, vAxis, negRimU - keyHalfW, -edgeV, negRimU - keyHalfW, -edgeV + keyDepth, fy, lw, primaryMat);
            spawnBasisTape(w, cx, cz, uAxis, vAxis, negRimU + keyHalfW, -edgeV, negRimU + keyHalfW, -edgeV + keyDepth, fy, lw, primaryMat);
            spawnBasisTape(w, cx, cz, uAxis, vAxis, negRimU - keyHalfW, -edgeV + keyDepth, negRimU + keyHalfW, -edgeV + keyDepth, fy, lw, primaryMat);

            spawnBasisTape(w, cx, cz, uAxis, vAxis, posRimU - keyHalfW, +edgeV, posRimU - keyHalfW, +edgeV - keyDepth, fy, lw, primaryMat);
            spawnBasisTape(w, cx, cz, uAxis, vAxis, posRimU + keyHalfW, +edgeV, posRimU + keyHalfW, +edgeV - keyDepth, fy, lw, primaryMat);
            spawnBasisTape(w, cx, cz, uAxis, vAxis, posRimU - keyHalfW, +edgeV - keyDepth, posRimU + keyHalfW, +edgeV - keyDepth, fy, lw, primaryMat);
        }

        spawnOrientedThreePointArc(w, cx, cz, uAxis, vAxis, negRimU, negRimV, +1.0, -edgeV, fy, lw, primaryMat);
        spawnOrientedThreePointArc(w, cx, cz, uAxis, vAxis, posRimU, posRimV, -1.0, +edgeV, fy, lw, primaryMat);

        if (courtLinesPreset == CourtLinesPreset.FULL) {
            spawnOrientedPaintDetails(w, cx, cz, uAxis, vAxis, negRimU, negRimV, +1.0, -edgeV + keyDepth, keyHalfW, fy, lw, primaryMat, accentMat);
            spawnOrientedPaintDetails(w, cx, cz, uAxis, vAxis, posRimU, posRimV, -1.0, +edgeV - keyDepth, keyHalfW, fy, lw, primaryMat, accentMat);
        }
    }

    private void spawnBasisTape(
        World w,
        double cx,
        double cz,
        Vector uAxis,
        Vector vAxis,
        double u0,
        double v0,
        double u1,
        double v1,
        double fy,
        double lw,
        Material mat
    ) {
        double x0 = cx + uAxis.getX() * u0 + vAxis.getX() * v0;
        double z0 = cz + uAxis.getZ() * u0 + vAxis.getZ() * v0;
        double x1 = cx + uAxis.getX() * u1 + vAxis.getX() * v1;
        double z1 = cz + uAxis.getZ() * u1 + vAxis.getZ() * v1;
        spawnTapeSegment(w, x0, z0, x1, z1, fy, lw, mat);
    }

    private void spawnOrientedPaintDetails(
        World w,
        double cx,
        double cz,
        Vector uAxis,
        Vector vAxis,
        double rimU,
        double rimV,
        double intoCourtSignV,
        double ftLineV,
        double keyHalfW,
        double fy,
        double lw,
        Material primaryMat,
        Material accentMat
    ) {
        // The finishing line, drawn from the numbers the dunk test uses: a straight shoulder each
        // side of the rim, then the arc that closes them. It used to be a decorative circle at
        // 1.22 blocks, which told the player a finish was out of reach from a spot the game
        // actually allowed it.
        double finishRadius = CourtGeometry.DUNK_PAINT_RADIUS;
        double shoulder = geometry.dunkZoneHalfWidth();
        double shoulderDepth = geometry.dunkZoneShoulderDepth();
        spawnBasisTape(w, cx, cz, uAxis, vAxis,
            rimU - shoulder, rimV, rimU - shoulder, rimV + shoulderDepth * intoCourtSignV,
            fy, lw * 0.85, accentMat);
        spawnBasisTape(w, cx, cz, uAxis, vAxis,
            rimU + shoulder, rimV, rimU + shoulder, rimV + shoulderDepth * intoCourtSignV,
            fy, lw * 0.85, accentMat);

        int rSteps = 14;
        double finishAngle = Math.asin(Math.min(0.99, shoulder / finishRadius));
        for (int i = 0; i < rSteps; i++) {
            double t0 = -finishAngle + (2.0 * finishAngle * i) / rSteps;
            double t1 = -finishAngle + (2.0 * finishAngle * (i + 1)) / rSteps;
            spawnBasisTape(
                w, cx, cz, uAxis, vAxis,
                rimU + Math.sin(t0) * finishRadius, rimV + Math.cos(t0) * finishRadius * intoCourtSignV,
                rimU + Math.sin(t1) * finishRadius, rimV + Math.cos(t1) * finishRadius * intoCourtSignV,
                fy, lw * 0.85, accentMat
            );
        }

        double ftr = Math.min(1.80, keyHalfW * 0.72);
        int ftSteps = 12;
        for (int i = 0; i < ftSteps; i++) {
            double t0 = -Math.PI * 0.5 + (Math.PI * i) / ftSteps;
            double t1 = -Math.PI * 0.5 + (Math.PI * (i + 1)) / ftSteps;
            spawnBasisTape(
                w, cx, cz, uAxis, vAxis,
                rimU + Math.sin(t0) * ftr, ftLineV + Math.cos(t0) * ftr * intoCourtSignV,
                rimU + Math.sin(t1) * ftr, ftLineV + Math.cos(t1) * ftr * intoCourtSignV,
                fy, lw * 0.85, primaryMat
            );
        }

        for (double distFromFt : new double[]{1.2, 2.3, 3.4}) {
            double hv = ftLineV - distFromFt * intoCourtSignV;
            if (Math.abs(hv) >= Math.abs(ftLineV) + 0.2) {
                spawnBasisTape(w, cx, cz, uAxis, vAxis, rimU - keyHalfW - 0.32, hv, rimU - keyHalfW, hv, fy, lw * 0.85, primaryMat);
                spawnBasisTape(w, cx, cz, uAxis, vAxis, rimU + keyHalfW, hv, rimU + keyHalfW + 0.32, hv, fy, lw * 0.85, primaryMat);
            }
        }
    }

    private void spawnOrientedThreePointArc(
        World w,
        double cx,
        double cz,
        Vector uAxis,
        Vector vAxis,
        double rimU,
        double rimV,
        double intoCourtSignV,
        double baselineV,
        double fy,
        double lw,
        Material primaryMat
    ) {
        // The paint reads the same two numbers as the scoring rule. A corner line drawn 0.4
        // blocks away from the line the rule honours is a line the player can see and the
        // server cannot explain.
        double maxArcRadius = geometry.threePointArcRadius();
        double cornerHalfU = geometry.threePointCornerLateral();
        double maxAngle = Math.asin(Math.min(0.98, cornerHalfU / maxArcRadius));
        double joinDv = Math.cos(maxAngle) * maxArcRadius * intoCourtSignV;
        double joinV = rimV + joinDv;

        spawnBasisTape(w, cx, cz, uAxis, vAxis, rimU - cornerHalfU, baselineV, rimU - cornerHalfU, joinV, fy, lw, primaryMat);
        spawnBasisTape(w, cx, cz, uAxis, vAxis, rimU + cornerHalfU, baselineV, rimU + cornerHalfU, joinV, fy, lw, primaryMat);

        int steps = 20;
        for (int i = 0; i < steps; i++) {
            double t0 = -maxAngle + (2.0 * maxAngle * i) / steps;
            double t1 = -maxAngle + (2.0 * maxAngle * (i + 1)) / steps;
            spawnBasisTape(
                w, cx, cz, uAxis, vAxis,
                rimU + Math.sin(t0) * maxArcRadius, rimV + Math.cos(t0) * maxArcRadius * intoCourtSignV,
                rimU + Math.sin(t1) * maxArcRadius, rimV + Math.cos(t1) * maxArcRadius * intoCourtSignV,
                fy, lw, primaryMat
            );
        }
    }

    private void spawnTapeSegment(World w, double x0, double z0, double x1, double z1, double y, double width, Material mat) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        double len = Math.hypot(dx, dz);
        if (len < 1e-3) {
            return;
        }
        double mx = (x0 + x1) * 0.5;
        double mz = (z0 + z1) * 0.5;
        float yawRad = (float) Math.atan2(dx, dz);
        float actualLen = (float) (len + width * 0.20);

        Location loc = new Location(w, mx, y, mz, 0f, 0f);
        Quaternionf rot = new Quaternionf(new AxisAngle4f(yawRad, 0f, 1f, 0f));

        Vector3f localOffset = new Vector3f((float) (-width * 0.5), 0f, -actualLen * 0.5f);
        rot.transform(localOffset);

        BlockDisplay bd = w.spawn(loc, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(HoopDisplaySet.DISPLAY_TAG);
            d.setBlock(mat.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2.4f);
            d.setTransformation(new Transformation(
                localOffset,
                rot,
                new Vector3f((float) width, 0.008f, actualLen),
                new Quaternionf()
            ));
        });
        courtSurfaceDisplays.add(bd);
    }

    public void tickDisplays(int shotClockSeconds) {
        long started = fr.bosketball.util.TickProfiler.beginBudget();
        try {
            tickDisplaysMeasured(shotClockSeconds);
        } finally {
            fr.bosketball.util.TickProfiler.endBudget("hud", started);
        }
    }

    private void tickDisplaysMeasured(int shotClockSeconds) {
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
        long started = fr.bosketball.util.TickProfiler.beginBudget();
        try {
            updateJumbotronMeasured(home, homeScore, statusSmallCaps, shotClock, awayScore, away);
        } finally {
            fr.bosketball.util.TickProfiler.endBudget("hud", started);
        }
    }

    private void updateJumbotronMeasured(String home, int homeScore, String statusSmallCaps, int shotClock, int awayScore, String away) {
        if (jumbotronScreens.isEmpty()) {
            return;
        }
        String content = "<gradient:" + Palette.WARN + ":" + Palette.GOLD_SOFT + "><bold>▪ " + displayName + " ▪</bold></gradient>\n"
            + "<" + Palette.EMBER + "><bold>" + home + "</bold></" + Palette.EMBER + "> <#FDE047><bold>" + homeScore + "</bold></#FDE047>"
            + " <#64748B>—</#64748B> "
            + "<#FDE047><bold>" + awayScore + "</bold></#FDE047> <" + Palette.ICE + "><bold>" + away + "</bold></" + Palette.ICE + ">\n"
            + "<#F8FAFC>" + statusSmallCaps + "</#F8FAFC> <#64748B>\u00b7</#64748B> <#FBBF24>" + shotClock + "ꜱ</#FBBF24>";
        if (content.equals(lastJumbotronContent)) {
            return;
        }
        lastJumbotronContent = content;
        var parsed = Msg.parse(content);
        for (TextDisplay td : jumbotronScreens) {
            if (td != null && td.isValid()) {
                td.text(parsed);
            }
        }
    }

    public void renderBlueprintPreview(Player viewer) {
        if (viewer == null || !viewer.isOnline() || viewer.getWorld() == null || !viewer.getWorld().getName().equals(worldName)) {
            return;
        }
        Particle.DustOptions goldLine = new Particle.DustOptions(Color.fromRGB(255, 212, 59), 1.1f);
        Particle.DustOptions aquaBarrier = new Particle.DustOptions(Color.fromRGB(58, 224, 255), 1.0f);
        Particle.DustOptions ignisDust = new Particle.DustOptions(Color.fromRGB(255, 77, 61), 1.35f);
        Particle.DustOptions frostDust = new Particle.DustOptions(Color.fromRGB(61, 184, 255), 1.35f);

        double fy = geometry.floorY() + 0.08;

        for (double x = geometry.minX(); x <= geometry.maxX(); x += 1.5) {
            viewer.spawnParticle(Particle.DUST, x, fy, geometry.minZ(), 1, goldLine);
            viewer.spawnParticle(Particle.DUST, x, fy, geometry.maxZ(), 1, goldLine);
        }
        for (double z = geometry.minZ(); z <= geometry.maxZ(); z += 1.5) {
            viewer.spawnParticle(Particle.DUST, geometry.minX(), fy, z, 1, goldLine);
            viewer.spawnParticle(Particle.DUST, geometry.maxX(), fy, z, 1, goldLine);
        }

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
