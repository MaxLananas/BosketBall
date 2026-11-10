package fr.bosketball.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import fr.bosketball.ui.LobbyHologramManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * What Tab offers must be what the command accepts.
 *
 * <p>Completion was answering nothing at all to a player — the whole subcommand list is staff-only,
 * so the lobby's own commands were invisible — and, for staff, it offered {@code holofame}, which no
 * branch of the dispatch ever handled. These tests pin both halves of the contract.</p>
 */
public final class BosketCommandCompletionTest {

    @Test
    public void everySubcommandOfferedToPlayersIsARealOne() {
        List<String> players = BosketCommand.playerSubcommands();
        assertFalse("a player must be offered something after /bb", players.isEmpty());
        for (String sub : players) {
            assertTrue(sub + " is offered to players but is not a subcommand",
                BosketCommand.SUBCOMMANDS.contains(sub));
        }
    }

    @Test
    public void theStaffListAndThePlayerListHaveNoDuplicatesOrOddities() {
        for (List<String> list : List.of(BosketCommand.SUBCOMMANDS, BosketCommand.playerSubcommands())) {
            Set<String> seen = new HashSet<>();
            for (String sub : list) {
                assertTrue(sub + " is not lower case", sub.equals(sub.toLowerCase(java.util.Locale.ROOT)));
                assertTrue(sub + " is listed twice", seen.add(sub));
            }
        }
    }

    @Test
    public void theHologramActionsCoverEveryKindAndEveryVerb() {
        List<String> actions = BosketCommand.holoActions();
        for (LobbyHologramManager.StationKind kind : LobbyHologramManager.StationKind.values()) {
            assertTrue("the usage line forgot " + kind.id(), actions.contains(kind.id()));
        }
        for (String verb : List.of("hall-of-fame", "list", "remove", "clear")) {
            assertTrue("the usage line forgot " + verb, actions.contains(verb));
        }
        assertEquals(new ArrayList<>(new java.util.LinkedHashSet<>(actions)), actions);
    }
}
