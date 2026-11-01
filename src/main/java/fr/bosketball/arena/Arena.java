package fr.bosketball.arena;

import fr.bosketball.model.TeamSide;
import fr.bosketball.util.Msg;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Represents a BosketBall Stadium Arena with 3D Hoops, Shot Clocks, Spawns & 4-Sided Jumbotron!
 */
public final class Arena {

    private final String id;
    private String displayName;
    private final String worldName;
    private final CourtGeometry geometry;

    private HoopDisplaySet northHoopDisplay;
    private HoopDisplaySet southHoopDisplay;
    private final List<TextDisplay> jumbotronScreens = new ArrayList<>(4);

    public Arena(String id, String displayName, String worldName, CourtGeometry geometry) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.worldName = worldName;
        this.geometry = geometry;
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

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public CourtGeometry geometry() {
        return geometry;
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
        double lateral = ((slotIndex % 3) - 1) * 2.4;
        if (side == TeamSide.IGNIS) {
            return new Location(w, geometry.centerX() + lateral, geometry.floorY() + 0.1, geometry.centerZ() - 5.5, 0f, 0f);
        }
        if (side == TeamSide.FROST) {
            return new Location(w, geometry.centerX() + lateral, geometry.floorY() + 0.1, geometry.centerZ() + 5.5, 180f, 0f);
        }
        // Spectator sideline seat
        return new Location(w, geometry.centerX() + geometry.halfWidth() + 3.5, geometry.floorY() + 2.2, geometry.centerZ(), 90f, 12f);
    }

    public Vector targetRimForTeam(TeamSide offensiveTeam) {
        return offensiveTeam == TeamSide.IGNIS ? geometry.southHoopRim() : geometry.northHoopRim();
    }

    public void spawnDisplays() {
        despawnDisplays();
        World w = world();
        if (w == null) {
            return;
        }
        this.northHoopDisplay = new HoopDisplaySet(w, geometry.northHoopRim(), true);
        this.southHoopDisplay = new HoopDisplaySet(w, geometry.southHoopRim(), false);
        this.northHoopDisplay.spawn();
        this.southHoopDisplay.spawn();

        // Spawn 4-sided Center-Hung Jumbotron screens 9.5 blocks above center court!
        double jY = geometry.floorY() + 9.4;
        float[] yaws = {0f, 90f, 180f, -90f};
        double[][] offsets = {{0, 1.55}, {-1.55, 0}, {0, -1.55}, {1.55, 0}};
        for (int i = 0; i < 4; i++) {
            Location loc = new Location(
                w,
                geometry.centerX() + offsets[i][0],
                jY,
                geometry.centerZ() + offsets[i][1],
                yaws[i],
                12f
            );
            TextDisplay screen = w.spawn(loc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.FIXED);
                td.setBackgroundColor(Color.fromARGB(225, 10, 14, 24));
                td.setShadowed(true);
                td.setViewRange(2.8f);
                td.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf(),
                    new Vector3f(1.55f, 1.55f, 1.55f),
                    new Quaternionf()
                ));
            });
            jumbotronScreens.add(screen);
        }
        updateJumbotron("ɪɢɴɪꜱ", 0, "ᴇɴ ᴀᴛᴛᴇɴᴛᴇ", 24, 0, "ꜰʀᴏꜱᴛ");
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
}
