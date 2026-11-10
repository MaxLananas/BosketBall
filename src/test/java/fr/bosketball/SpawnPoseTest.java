package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import fr.bosketball.arena.Arena;
import org.junit.Test;

/**
 * A spawn is read from an operator-editable list, from the map editor and from a command. Its
 * angles are always safe; its position either is a place in the world or the spawn does not exist.
 */
public class SpawnPoseTest {

    @Test
    public void aYawThatIsNotAnAngleBecomesZero() {
        Arena.SpawnPose pose = new Arena.SpawnPose(10, 64, -3, Float.NaN, 0f);
        assertEquals(0f, pose.yaw(), 1e-6f);
        assertEquals(10.0, pose.x(), 1e-9);
    }

    @Test
    public void yawIsWrappedAndPitchIsClamped() {
        assertEquals(10f, new Arena.SpawnPose(0, 0, 0, 370f, 0f).yaw(), 1e-4f);
        assertEquals(350f, new Arena.SpawnPose(0, 0, 0, -10f, 0f).yaw(), 1e-4f);
        assertEquals(0f, new Arena.SpawnPose(0, 0, 0, 720f, 0f).yaw(), 1e-4f);
        assertEquals(90f, new Arena.SpawnPose(0, 0, 0, 0f, 200f).pitch(), 1e-4f);
        assertEquals(-90f, new Arena.SpawnPose(0, 0, 0, 0f, -200f).pitch(), 1e-4f);
        assertEquals(0f, new Arena.SpawnPose(0, 0, 0, 0f, Float.NaN).pitch(), 1e-6f);
        assertEquals(0f, new Arena.SpawnPose(0, 0, 0, 0f, Float.POSITIVE_INFINITY).pitch(), 1e-6f);
    }

    @Test
    public void aPoseWhosePositionIsNotAPointCannotBecomeALocation() {
        Arena.SpawnPose broken = new Arena.SpawnPose(Double.NaN, 64, 1, 0f, 0f);
        assertTrue(!broken.hasRealPosition());
        assertNull(broken.toLocation(null));

        Arena.SpawnPose infinite = new Arena.SpawnPose(0, Double.POSITIVE_INFINITY, 0, 0f, 0f);
        assertTrue(!infinite.hasRealPosition());
        assertNull(infinite.toLocation(null));

        Arena.SpawnPose fine = new Arena.SpawnPose(0.5, 64, 0.5, 90f, 0f);
        assertTrue(fine.hasRealPosition());
        Object loc = fine.toLocation(null);
        assertNotNull(loc);
    }
}
