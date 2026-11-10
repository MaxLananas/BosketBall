package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import fr.bosketball.util.MiniSyntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

/**
 * The self-healing layer has to survive its own output.
 *
 * <p>{@link MiniSyntax#repair(String)} exists so that a player never reads raw markup and a
 * malformed line never disappears. It is not a one-shot pass: the same text is repaired again on
 * the next render (a sidebar row, a cached lore line, a message rebuilt every tick), so the one
 * property that has to hold is that repairing twice changes nothing.
 *
 * <p>Round 85 — it did change. A line ending in a backslash inside a coloured scope broke both
 * promises at once: {@code "<#F8FAFC>X\" + "</#F8FAFC>"} was repaired into a string that printed
 * the literal characters {@code </#F8FAFC>} to the player at the end of their sentence, and the
 * panel grew one extra closing tag per render, without bound.
 */
public class MiniSyntaxRepairTest {

    private static final String[] TRICKY = {
        "<#F8FAFC>X\\</#F8FAFC>",
        "<red>gg\\</red>",
        "<red>trailing\\",
        "<b><red>deep\\</red></b>",
        "\\<red>literal",
        "<red>a\\b</red>",
        "plain text",
        "",
        "<",
        ">",
        "</>",
        "<unknown>tag</unknown>",
        "<gradient:red:blue>shade",
        "</red>stray",
        "<#FF0000>",
        "trailing backslash \\",
        "\\\\<red>tag",
    };

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    public void repairIsIdempotentOnTheCasesThatUsedToGrow() {
        for (String raw : TRICKY) {
            String once = MiniSyntax.repair(raw);
            String twice = MiniSyntax.repair(once);
            assertEquals("repair must be a fixed point for [" + raw + "]", once, twice);
            assertEquals("and a third pass must agree too [" + raw + "]", once,
                MiniSyntax.repair(twice));
        }
    }

    @Test
    public void aBackslashBeforeThePluginsOwnCloserDoesNotLeakMarkup() {
        // What a plugin actually builds: a coloured scope, a player-typed string that happens to
        // end in a backslash, and the closing tag the code appended itself.
        String repaired = MiniSyntax.repair("<#F8FAFC>silver\\</#F8FAFC>");
        String shown = plain(MiniMessage.miniMessage().deserialize(repaired));
        assertEquals("the player must read their backslash and nothing else", "silver\\", shown);
        assertFalse("no tag may reach the player: " + shown, shown.contains("<"));
        assertEquals("no closer may be appended twice", 1, countOccurrences(repaired, "</#F8FAFC>"));
    }

    @Test
    public void theDeliberateEscapeStillRendersALiteralTag() {
        String repaired = MiniSyntax.repair("\\<red>literal");
        assertEquals("an escaped tag is text, and stays text", "<red>literal",
            plain(MiniMessage.miniMessage().deserialize(repaired)));
    }

    @Test
    public void anEscapedBackslashIsOneCharacterToTheReader() {
        assertEquals("one backslash, not two", "a\\b", MiniSyntax.visibleText("<red>a\\\\b</red>"));
        assertEquals("and the same through the parser", "a\\b",
            plain(MiniMessage.miniMessage().deserialize(MiniSyntax.repair("<red>a\\\\b</red>"))));
    }

    @Test
    public void repairNeverLetsAdventureThrow() {
        // Random markup fragments are how players and guild names reach the message layer. A
        // ParsingException here is a message that silently disappears.
        Random rng = new Random(20261104L);
        String alphabet = "<>/#:!-_ abZ09{}\\\"'é—\uE000\uE001%$";
        String[] fragments = {"<red>", "</red>", "<#FF0000>", "</#FF0000>", "<bold>", "</bold>",
            "<gradient:red:blue>", "</gradient>", "<shadow>", "</shadow>", "</", "<", ">",
            "\\", "\\\\", "\\<", "<player>", "<2.7m>", "</unknown>", "<font:x>"};
        List<String> samples = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            StringBuilder sb = new StringBuilder();
            int parts = 1 + rng.nextInt(5);
            for (int p = 0; p < parts; p++) {
                if (rng.nextBoolean()) {
                    sb.append(fragments[rng.nextInt(fragments.length)]);
                } else {
                    for (int k = rng.nextInt(5); k > 0; k--) {
                        sb.append(alphabet.charAt(rng.nextInt(alphabet.length())));
                    }
                }
            }
            samples.add(sb.toString());
        }
        for (String raw : samples) {
            String repaired = MiniSyntax.repair(raw);
            assertEquals("repair must be a fixed point for [" + raw + "]", repaired,
                MiniSyntax.repair(repaired));
            try {
                MiniMessage.miniMessage().deserialize(repaired);
            } catch (RuntimeException | StackOverflowError failure) {
                throw new AssertionError("repair let a parse failure through for [" + raw + "]: "
                    + repaired, failure);
            }
        }
    }

    /**
     * A tag MiniMessage only accepts with an argument must not be treated as one when it arrives
     * bare: the parser prints it, and repair would then close it too, so the line ends with a
     * literal {@code </color>} the player reads.
     */
    @Test
    public void aTagThatNeedsAnArgumentIsNotATagWithoutOne() {
        String shownBare = plain(MiniMessage.miniMessage().deserialize(MiniSyntax.repair("<color>x")));
        assertFalse("a bare argument tag must not leak its closer: " + shownBare,
            shownBare.contains("</"));

        // ...while the argument form keeps working exactly as before.
        assertEquals("silver", plain(MiniMessage.miniMessage()
            .deserialize(MiniSyntax.repair("<color:red>silver</color>"))));
        assertEquals("silver", plain(MiniMessage.miniMessage()
            .deserialize(MiniSyntax.repair("<click:run_command:'/x'>silver</click>"))));
        assertEquals("silver", plain(MiniMessage.miniMessage()
            .deserialize(MiniSyntax.repair("<shadow:#123456>silver</shadow>"))));
    }

    @Test
    public void theNegationFormOfATagIsLeftAlone() {
        // "!shadow" is the form every flourish in this pack is written with: it takes no argument
        // and it is valid as it stands, so escaping it would put "<!shadow>" in front of players.
        String flourish = "<!shadow><#F8FAFC>line</#F8FAFC></!shadow>";
        assertEquals("flourish must survive repair untouched", flourish, MiniSyntax.repair(flourish));
        assertEquals("line", plain(MiniMessage.miniMessage().deserialize(MiniSyntax.repair(flourish))));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
