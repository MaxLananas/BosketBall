package fr.bosketball.arena.schematic;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * A court file comes from an operator's disk, and a court file can be anything: truncated, saved
 * by an ancient tool, hand-edited, or built to make the server fall over. None of that may cost
 * the pool its other courts, and none of it may surface as an Error instead of a message.
 */
public class SchematicSafetyTest {

    private static byte[] deepDocument(int levels) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(10);       // root compound
            out.writeShort(0);       // empty name
            for (int i = 0; i < levels; i++) {
                out.writeByte(10);   // child compound
                out.writeShort(1);
                out.writeByte('a');
            }
            for (int i = 0; i < levels; i++) {
                out.writeByte(0);    // close each nested compound
            }
            out.writeByte(0);        // close the root
        }
        return bytes.toByteArray();
    }

    private static byte[] hugeSchematic() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(10);
            out.writeShort(0);
            out.writeByte(3);        // TAG_Int: Width
            out.writeShort(5);
            out.writeBytes("Width");
            out.writeInt(100_000);
            out.writeByte(3);        // Height
            out.writeShort(6);
            out.writeBytes("Height");
            out.writeInt(100_000);
            out.writeByte(3);        // Length
            out.writeShort(6);
            out.writeBytes("Length");
            out.writeInt(100_000);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static Path temp(String name, byte[] content) throws IOException {
        Path file = Files.createTempFile("bosket-" + name, ".schem");
        Files.write(file, content);
        file.toFile().deleteOnExit();
        return file;
    }

    @Test
    public void aDeeplyNestedFileIsRejectedWithAMessage() throws Exception {
        Path file = temp("deep", deepDocument(500));
        try {
            CourtSchematic.load(file.toFile());
            fail("500 levels of nesting must not decode");
        } catch (IOException expected) {
            assertTrue("the reason must be readable: " + expected.getMessage(),
                expected.getMessage() != null && expected.getMessage().toLowerCase().contains("nesting"));
        }
    }

    @Test
    public void aShallowDocumentStillReads() throws Exception {
        // Sanity check for the depth limit: a normal, if empty, document is not rejected by it.
        Path file = temp("shallow", deepDocument(4));
        try {
            CourtSchematic.load(file.toFile());
            fail("a document with no Width is not a schematic");
        } catch (IOException expected) {
            assertTrue("the depth limit must not be what stopped it: " + expected.getMessage(),
                expected.getMessage() != null && !expected.getMessage().toLowerCase().contains("nesting"));
        }
    }

    @Test
    public void anImpossiblyLargeSchematicIsRefusedBeforeItIsAllocated() throws Exception {
        Path file = temp("huge", hugeSchematic());
        try {
            CourtSchematic.load(file.toFile());
            fail("100000 x 100000 x 100000 blocks must not be decoded");
        } catch (IOException expected) {
            assertTrue("the reason names the limit: " + expected.getMessage(),
                expected.getMessage() != null && expected.getMessage().contains("too large"));
        }
    }

    @Test
    public void aFileThatIsNotASchematicIsRejectedWithAReason() throws Exception {
        Path file = temp("garbage", new byte[]{0x13, 0x37, 0x00, 0x42, (byte) 0xFF});
        try {
            CourtSchematic.load(file.toFile());
            fail("random bytes are not a schematic");
        } catch (IOException expected) {
            assertTrue("a message, not a stack trace on its own", expected.getMessage() != null
                && !expected.getMessage().isBlank());
        }

        File missing = new File("does-not-exist-" + System.nanoTime() + ".schem");
        try {
            CourtSchematic.load(missing);
            fail("a missing file must be an IOException, not a NullPointerException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not found"));
        }
    }
}
