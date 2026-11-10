package fr.bosketball;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import fr.bosketball.util.Msg;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * A number and its noun agree, in player copy.
 *
 * <p>Every line of the plugin used to write "Coins" and "court(s)" whatever the number: a single
 * coin read as "1 Coins" on the hub item, a single court as "1 court(s)" after a re-paste. Numbers
 * here come from the economy and from the court pool, where one is an ordinary value, so the label
 * has to follow the number. Console log lines keep the "N block(s)" shorthand: they are read by the
 * operator in a log, not by a player in the UI.</p>
 */
public final class CopyPluralTest {

    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern PAIR_FORM = Pattern.compile("\\(s\\)|\\(es\\)");

    @Test
    public void oneIsSingularAndTheRestIsPlural() {
        assertEquals("Coin", Msg.plural(1, "Coin", "Coins"));
        assertEquals("Coins", Msg.plural(0, "Coin", "Coins"));
        assertEquals("Coins", Msg.plural(2, "Coin", "Coins"));
        assertEquals("player", Msg.plural(1, "player", "players"));
        assertEquals("courts", Msg.plural(11, "court", "courts"));
    }

    @Test
    public void theCoinBadgeSaysCoinForOne() {
        String one = Msg.coinsBadge(1);
        String many = Msg.coinsBadge(2500);
        assertTrue("one coin must not read as plural: " + one, one.contains("Coin") && !one.contains("Coins"));
        assertTrue("many coins must read as plural: " + many, many.contains("Coins"));
        assertTrue("the badge still groups thousands: " + many, many.contains("2,500"));
    }

    @Test
    public void noPlayerFacingLineUsesThePairForm() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (var walk = Files.walk(new File("src/main/java").toPath())) {
            for (var file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                String[] lines = source.split("\n", -1);
                int offset = 0;
                for (int i = 0; i < lines.length; i++) {
                    // One line at a time: a whole-file match on a file full of long literals
                    // recurses in the regex engine and overflows the stack.
                    Matcher m = LITERAL.matcher(lines[i]);
                    while (m.find()) {
                        if (PAIR_FORM.matcher(m.group(1)).find()) {
                            int at = offset + m.start();
                            String statement = source.substring(Math.max(0, source.lastIndexOf(';', at)), at);
                            if (statement.contains("Msg.") || statement.contains("put(")) {
                                offenders.add(file.getFileName() + ":" + (i + 1) + "  " + m.group(1));
                            }
                        }
                    }
                    offset += lines[i].length() + 1;
                }
            }
        }
        assertTrue("player copy must choose a number and a noun, not \"(s)\": " + offenders,
            offenders.isEmpty());
    }

}
