package fr.bosketball.arena;

import fr.bosketball.model.BallSkin;
import fr.bosketball.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spawns and animates a 3D Tempered-Glass Backboard, Breakaway Spring Rim, 12-Loop Animated Nylon Net & Digital 24s Shot Clock!
 * Supports ANY horizontal orientation (North, South, East, West, or arbitrary 360° custom map angles).
 */
public final class HoopDisplaySet {

    public static final String DISPLAY_TAG = "bb_court_display";
    private static final float HOOP_SCALE = 2.00f;

    private final World world;
    private final Vector rimCenter;
    private final Vector hoopForward;
    private final boolean spawn3dHoopModel;

    private ItemDisplay hoopDisplay;
    private TextDisplay shotClockDisplay;

    private String currentModelId = "hoop_idle";
    private int animTicksRemaining = 0;
    private String animPhaseTwoModel = null;
    private int lastShotClockShown = -999;

    public HoopDisplaySet(World world, Vector rimCenter, boolean facesSouth) {
        this(world, rimCenter, new Vector(0, 0, facesSouth ? 1.0 : -1.0), true);
    }

    public HoopDisplaySet(World world, Vector rimCenter, Vector hoopForward, boolean spawn3dHoopModel) {
        this.world = world;
        this.rimCenter = rimCenter.clone();
        Vector fwd = new Vector(hoopForward.getX(), 0, hoopForward.getZ());
        this.hoopForward = fwd.lengthSquared() > 1e-6 ? fwd.normalize() : new Vector(0, 0, 1);
        this.spawn3dHoopModel = spawn3dHoopModel;
    }

    public void spawn() {
        despawn();
        if (world == null) {
            return;
        }

        // Exact ItemDisplay Y-rotation accounting for Minecraft's intrinsic 180° ItemDisplayTransform.NONE Z-flip:
        //   faces +Z (South): atan2(0, 1) - pi = -pi (== +pi)
        //   faces -Z (North): atan2(0, -1) - pi = 0
        //   faces +X (East):  atan2(1, 0) - pi = -pi/2
        //   faces -X (West):  atan2(-1, 0) - pi = +pi/2
        float rotY = (float) (Math.atan2(hoopForward.getX(), hoopForward.getZ()) - Math.PI);

        if (spawn3dHoopModel) {
            Location loc = new Location(world, rimCenter.getX(), rimCenter.getY(), rimCenter.getZ(), 0f, 0f);
            this.hoopDisplay = world.spawn(loc, ItemDisplay.class, disp -> {
                disp.setPersistent(false);
                disp.addScoreboardTag(DISPLAY_TAG);
                disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                disp.setBillboard(Display.Billboard.FIXED);
                disp.setBrightness(new Display.Brightness(15, 15));
                disp.setViewRange(2.5f);
                disp.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf().rotateY(rotY),
                    new Vector3f(HOOP_SCALE, HOOP_SCALE, HOOP_SCALE),
                    new Quaternionf()
                ));
                disp.setItemStack(buildHoopItem("hoop_idle"));
            });
        }

        // Digital 24-second LED Shot Clock mounted flush inside the 3D Shot-Clock Housing bezel above the backboard!
        float entityYaw = (float) Math.toDegrees(Math.atan2(-hoopForward.getX(), hoopForward.getZ()));
        double clockBackOffset = spawn3dHoopModel ? 0.542 : 0.59;
        double clockHeightOffset = spawn3dHoopModel ? 1.83 : 1.68;
        Location clockLoc = new Location(
            world,
            rimCenter.getX() - hoopForward.getX() * clockBackOffset,
            rimCenter.getY() + clockHeightOffset,
            rimCenter.getZ() - hoopForward.getZ() * clockBackOffset,
            entityYaw,
            0f
        );
        this.shotClockDisplay = world.spawn(clockLoc, TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(DISPLAY_TAG);
            td.setBillboard(Display.Billboard.FIXED);
            td.setBackgroundColor(spawn3dHoopModel ? Color.fromARGB(0, 0, 0, 0) : Color.fromARGB(230, 12, 14, 22));
            td.setBrightness(new Display.Brightness(15, 15));
            td.setShadowed(true);
            td.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(1.22f, 1.22f, 1.22f),
                new Quaternionf()
            ));
        });
        this.lastShotClockShown = -999;
        updateShotClock(24);
    }

    public void triggerSwishNetAnimation() {
        setModel("hoop_swish_1");
        this.animPhaseTwoModel = "hoop_swish_2";
        this.animTicksRemaining = 12;
    }

    public void triggerDunkFlexAnimation() {
        setModel("hoop_dunk_flex");
        this.animPhaseTwoModel = "hoop_swish_1";
        this.animTicksRemaining = 16;
    }

    public void tickAnimation() {
        if (animTicksRemaining <= 0) {
            return;
        }
        animTicksRemaining--;
        if (animTicksRemaining == 6 && animPhaseTwoModel != null) {
            setModel(animPhaseTwoModel);
            animPhaseTwoModel = null;
        } else if (animTicksRemaining == 0) {
            setModel("hoop_idle");
        }
    }

    public void updateShotClock(int secondsRemaining) {
        if (shotClockDisplay == null || !shotClockDisplay.isValid()) {
            return;
        }
        if (secondsRemaining == lastShotClockShown) {
            return;
        }
        lastShotClockShown = secondsRemaining;
        String hex = secondsRemaining <= 5 ? "#FF3B3B" : (secondsRemaining <= 10 ? "#FFD43B" : "#FF9F2E");
        String digits = String.format("%02d", Math.max(0, secondsRemaining));
        shotClockDisplay.text(Msg.parse("<" + hex + "><bold>" + digits + "</bold></" + hex + ">"));
    }

    private void setModel(String modelId) {
        if (hoopDisplay == null || !hoopDisplay.isValid() || modelId.equals(currentModelId)) {
            return;
        }
        this.currentModelId = modelId;
        hoopDisplay.setItemStack(buildHoopItem(modelId));
    }

    private static ItemStack buildHoopItem(String modelId) {
        ItemStack stack = new ItemStack(Material.IRON_BARS);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(BallSkin.NAMESPACE, modelId));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void despawn() {
        if (hoopDisplay != null) {
            hoopDisplay.remove();
            hoopDisplay = null;
        }
        if (shotClockDisplay != null) {
            shotClockDisplay.remove();
            shotClockDisplay = null;
        }
    }
}
