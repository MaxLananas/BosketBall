package fr.bosketball;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The match copy is the product: a player reads a broadcast line, a rail pill and a title card far
 * more often than they read code. Every letter of it is a house decision, so the mechanical ones
 * are locked here instead of being re-discovered by hand on the next pass.
 *
 * <p>Rules: the separator is the muted middot and never a raw glyph, no star ever reaches a score
 * line, a count is never written with a lazy {@code (s)}, player copy never assumes the gender of
 * the player it is about, and the input is spelled one way.
 */
public class GameplayCopyRulesTest {

    private static final Path GAME = Path.of("src/main/java/fr/bosketball/game");
    private static final Path HUD_COPY = Path.of("src/main/java/fr/bosketball/util/HudCopy.java");
    private static final String MIDDOT = "\u00b7";
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static List<Path> gameplaySources() throws Exception {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(GAME)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }
        sources.add(HUD_COPY);
        return sources;
    }

    private static List<String> copyLiterals(Path source) throws Exception {
        List<String> out = new ArrayList<>();
        Matcher m = LITERAL.matcher(Files.readString(source));
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    public void noRawSeparatorGlyphShipsInTheJavaSources() throws Exception {
        for (Path source : gameplaySources()) {
            String body = Files.readString(source);
            assertFalse("raw middot in " + source + ": write \\u00b7, so the source stays ASCII",
                body.contains(MIDDOT));
        }
    }

    @Test
    public void theSeparatorIsAlwaysTheMutedHouseColour() throws Exception {
        String escape = "\\" + "u00b7";
        for (Path source : List.of(
            Path.of("src/main/java/fr/bosketball/game/MatchSession.java"),
            Path.of("src/main/java/fr/bosketball/game/GameManager.java"),
            HUD_COPY)) {
            for (String line : Files.readString(source).split("\n")) {
                // A card header is flat text appended verbatim: markup there would print as
                // characters, so those two lives with the bare escape and no colour.
                if (line.contains("sendFramedCard(")) {
                    continue;
                }
                int i = 0;
                while ((i = line.indexOf(escape, i)) >= 0) {
                    String before = line.substring(Math.max(0, i - 12), i);
                    assertTrue("the separator must open with the muted colour: " + source + ": " + line.trim(),
                        before.contains("<#64748B>"));
                    i += escape.length();
                }
            }
        }
    }

    @Test
    public void noStarEverReachesPlayerCopy() throws Exception {
        for (Path source : gameplaySources()) {
            for (String literal : copyLiterals(source)) {
                assertFalse("a star in " + source + ": " + literal,
                    literal.contains("\u2726") || literal.contains("\u2605")
                        || literal.contains("\u2727") || literal.contains("\u2606"));
            }
        }
    }

    @Test
    public void aCountIsNeverWrittenWithALazyPlural() throws Exception {
        for (Path source : gameplaySources()) {
            for (String literal : copyLiterals(source)) {
                assertFalse("(s) plural in " + source + ": " + literal, literal.contains("(s)"));
            }
        }
    }

    @Test
    public void playerCopyNeverAssumesAGender() throws Exception {
        Pattern gendered = Pattern.compile("\\b(he|him|his|she|her|hers)\\b");
        for (Path source : gameplaySources()) {
            for (String literal : copyLiterals(source)) {
                assertFalse("gendered pronoun in " + source + ": " + literal,
                    gendered.matcher(literal).find());
            }
        }
    }

    @Test
    public void theInputIsSpelledTheSameWayEverywhere() throws Exception {
        for (Path source : gameplaySources()) {
            for (String literal : copyLiterals(source)) {
                assertFalse("Right-Click is not the house spelling: " + literal,
                    literal.contains("Right-Click"));
            }
        }
    }

    @Test
    public void aCardHeaderIsFlatTextAndNeverPrintsMarkup() {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(fr.bosketball.util.Glyphs.composeDividerHeader("Final Score <#64748B>\u00b7</#64748B> RANKED"));
        assertFalse("a tag in a card header would print as characters: " + plain, plain.contains("<"));
        assertFalse("a colour in a card header would print as characters: " + plain, plain.contains("#"));
        assertFalse("the header lost its separator: " + plain, plain.isEmpty());
    }

    @Test
    public void theCopyFixedOnThisPassStaysFixed() throws Exception {
        String session = Files.readString(Path.of("src/main/java/fr/bosketball/game/MatchSession.java"));
        assertTrue("a demotion is one sentence, not a fragment in brackets",
            session.contains("</#F8FAFC>. Win a game to climb back."));
        assertTrue("the placement note names what unlocks the tier",
            session.contains("your seasonal tier unlocks once placements are done."));
        assertTrue("one bot is one player",
            session.contains("(squad.size() == 1 ? \"player\" : \"players\")"));
        assertTrue("the contest subtitle is a sentence, not a caption",
            session.contains("money ball is worth 2 pts"));
        assertTrue("the lob help says what to do",
            session.contains("look up at the rim for a lob"));
        assertTrue("a contested dunk is not a man",
            session.contains("a body met you at the rim"));
        assertTrue("the scoreline keeps the house separator between the bucket and the score",
            session.contains("</\" + Palette.GOLD_SOFT + \">\"\n"
                + "                + \" <#64748B>"));
        assertFalse("the scoreline no longer hides the score in brackets",
            session.contains("\"  <#64748B>(\""));
    }
}
