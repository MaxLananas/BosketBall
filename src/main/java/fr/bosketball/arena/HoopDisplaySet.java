package fr.bosketball.arena;

import fr.bosketball.model.BallSkin;
import fr.bosketball.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Manages a 3D Custom Hoop ItemDisplay + Animated Net Swish / Dunk Rim-Flex States
 * + Floating Digital LED Shot-Clock TextDisplay above the backboard!
 */
public final class HoopDisplaySet {

    public static final String DISPLAY_TAG = "bosketball_display";
    private static final float HOOP_SCALE = 2.00f; // 0.125 blocks per model unit; [8,8,8] = exact rimCenter!

    private final World world;
    private final Vector rimCenter;
    private final boolean facesSouth; // true = North Hoop facing +Z; false = South Hoop facing -Z

    private ItemDisplay hoopDisplay;
    private TextDisplay shotClockDisplay;

    private String currentModelId = "hoop_idle";
    private int animTicksRemaining = 0;
    private boolean dunkAnimation = false;

    public HoopDisplaySet(World world, Vector rimCenter, boolean facesSouth) {
        this.world = world;
        this.rimCenter = rimCenter.clone();
        this.facesSouth = facesSouth;
    }

    public void spawn() {
        despawn();
        if (world == null) {
            return;
        }
        Location rimLoc = new Location(world, rimCenter.getX(), rimCenter.getY(), rimCenter.getZ(), 0f, 0f);
        // Minecraft ItemDisplayRenderer applies an intrinsic 180° Y-flip on ItemDisplayTransform.NONE:
        //   rotY = PI  -> backboard/stanchion go to -Z (North), rim faces +Z (South, into court) => North Hoop!
        //   rotY = 0   -> backboard/stanchion go to +Z (South), rim faces -Z (North, into court) => South Hoop!
        float rotY = facesSouth ? (float) Math.PI : 0.0f;

        this.hoopDisplay = world.spawn(rimLoc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(DISPLAY_TAG);
            d.setItemStack(createHoopItem("hoop_idle"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(2.5f);
            d.setShadowRadius(0.0f);
            d.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Quaternionf().rotateY(rotY),
                new Vector3f(HOOP_SCALE, HOOP_SCALE, HOOP_SCALE),
                new Quaternionf()
            ));
        });

        // Position digital LED 24s Shot-Clock flush on front of shot_clock_housing above backboard!
        double clockZ = rimCenter.getZ() + (facesSouth ? -0.59 : 0.59);
        Location clockLoc = new Location(world, rimCenter.getX(), rimCenter.getY() + 1.68, clockZ, facesSouth ? 0f : 180f, 0f);
        this.shotClockDisplay = world.spawn(clockLoc, TextDisplay.class, td -> {
            td.setPersistent(false);
            td.addScoreboardTag(DISPLAY_TAG);
            td.setBillboard(Display.Billboard.FIXED);
            td.setBackgroundColor(Color.fromARGB(240, 14, 16, 24));
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setViewRange(2.2f);
            td.text(Msg.parse("<gold><bold>24</bold></gold>"));
            td.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(1.05f, 1.05f, 1.05f),
                new Quaternionf()
            ));
        });
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

    public void triggerSwishAnimation() {
        this.dunkAnimation = false;
        this.animTicksRemaining = 12;
        setModelState("hoop_swish_1");
    }

    public void triggerDunkFlexAnimation() {
        this.dunkAnimation = true;
        this.animTicksRemaining = 16;
        setModelState("hoop_dunk_flex");
    }

    public void tickAnimation() {
        if (hoopDisplay == null || !hoopDisplay.isValid()) {
            return;
        }
        if (animTicksRemaining <= 0) {
            if (!"hoop_idle".equals(currentModelId)) {
                setModelState("hoop_idle");
            }
            return;
        }
        animTicksRemaining--;
        if (dunkAnimation) {
            if (animTicksRemaining == 9) {
                setModelState("hoop_swish_1");
            } else if (animTicksRemaining == 4) {
                setModelState("hoop_swish_2");
            } else if (animTicksRemaining == 0) {
                setModelState("hoop_idle");
            }
        } else {
            if (animTicksRemaining == 6) {
                setModelState("hoop_swish_2");
            } else if (animTicksRemaining == 0) {
                setModelState("hoop_idle");
            }
        }
    }

    public void updateShotClock(int seconds) {
        if (shotClockDisplay == null || !shotClockDisplay.isValid()) {
            return;
        }
        String formatted = String.format("%02d", Math.max(0, seconds));
        String color = seconds <= 5 ? "<#FF3B3B>" : (seconds <= 10 ? "<#FFD43B>" : "<#FF9F2E>");
        shotClockDisplay.text(Msg.parse(color + "<bold>" + formatted + "</bold>"));
    }

    private void setModelState(String modelId) {
        if (hoopDisplay != null && hoopDisplay.isValid() && !modelId.equals(currentModelId)) {
            this.currentModelId = modelId;
            hoopDisplay.setItemStack(createHoopItem(modelId));
        }
    }

    private static ItemStack createHoopItem(String modelId) {
        return BallSkin.createCustomModelItem(Material.IRON_BARS, modelId, null, null);
    }
}
