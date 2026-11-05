package fr.bosketball.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

/**
 * Turns the numbers measured on the live server (spark + {@code /bb stress}) into a short list of
 * concrete actions. Pure data in, text out: every check is unit-testable without a running server.
 */
public final class OpsAudit {

    /** Everything the audit looks at. Filled by {@link #snapshotOf()} on a live server. */
    public record Snapshot(
        boolean axiomInstalled,
        boolean worldEditInstalled,
        int viewDistance,
        int simulationDistance,
        int pasteBlocksPerTick,
        int maxHeapMb,
        int processors,
        int loadedChunks,
        int entities,
        double tps,
        double averageTickMs
    ) {
    }

    private OpsAudit() {
    }

    /** Reads the live server. Cheap enough for /bb audit; never called from the tick loop. */
    public static Snapshot snapshotOf(fr.bosketball.config.BosketConfig settings) {
        int chunks = 0;
        int entities = 0;
        for (World world : Bukkit.getWorlds()) {
            Chunk[] loaded = world.getLoadedChunks();
            chunks += loaded.length;
            for (Chunk chunk : loaded) {
                entities += chunk.getEntities().length;
            }
        }
        int pasteBudget = settings != null && settings.schematicCourts() != null
            ? settings.schematicCourts().maxBlocksPerTick()
            : 0;
        return new Snapshot(
            Bukkit.getPluginManager().getPlugin("AxiomPaper") != null,
            Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("WorldEdit") != null,
            Bukkit.getViewDistance(),
            Bukkit.getSimulationDistance(),
            pasteBudget,
            (int) (Runtime.getRuntime().maxMemory() / (1024L * 1024L)),
            Runtime.getRuntime().availableProcessors(),
            chunks,
            entities,
            Bukkit.getTPS()[0],
            Bukkit.getAverageTickTime()
        );
    }

    /**
     * @return one line per finding, worst first; empty when nothing needs to change.
     */
    public static List<String> audit(Snapshot s) {
        List<String> findings = new ArrayList<>();

        if (s.pasteBlocksPerTick() > 4000) {
            findings.add("<#F87171>schematic-courts.max-blocks-per-tick = " + s.pasteBlocksPerTick()
                + "</#F87171> <gray>- au-dela de 4000 une tranche depasse 20 ms (mesure : 214 ms a 40 000)."
                + " Le plugin le ramene a 4000 et s'auto-regule a 2 ms/tranche, mais baisse la valeur"
                + " dans config.yml pour que le fichier reste coherent.</gray>");
        }
        if (s.viewDistance() > 6) {
            findings.add("<#FBBF24>view-distance = " + s.viewDistance() + "</#FBBF24> <gray>- les courts sont"
                + " espaces de 384 blocs : une vue de 6+ charge des chunks vides. Passer a"
                + " <white>5</white> dans server.properties.</gray>");
        }
        if (s.simulationDistance() > 6) {
            findings.add("<#FBBF24>simulation-distance = " + s.simulationDistance() + "</#FBBF24> <gray>-"
                + " passer a <white>4</white> : au-dela, des entites tickent hors de vue pour rien.</gray>");
        }
        if (s.maxHeapMb() > 0 && s.maxHeapMb() < 2048) {
            findings.add("<#FBBF24>Xmx = " + s.maxHeapMb() + " Mo</#FBBF24> <gray>- trop bas pour 50 joueurs"
                + " (cible 3,5 Go). Voir docs/server-optimization/java-flags.txt.</gray>");
        }
        if (s.loadedChunks() > 600) {
            findings.add("<#FBBF24>" + s.loadedChunks() + " chunks charges</#FBBF24> <gray>- verifier"
                + " <white>schematic-courts.instances</white> et le pre-chauffage : 4 courts suffisent"
                + " pour le premier flux de joueurs.</gray>");
        }
        if (s.entities() > 600) {
            findings.add("<#FBBF24>" + s.entities() + " entites</#FBBF24> <gray>- les display entities"
                + " coutent ~1,5 microseconde/tick chacune (mesure : 600 entites = +0,7 ms). Les"
                + " hologrammes et les courts non utilises doivent rester despawnes.</gray>");
        }
        if (s.tps() < 19.5) {
            findings.add("<#F87171>TPS " + String.format(Locale.ROOT, "%.2f", s.tps()) + "</#F87171> <gray>-"
                + " lancer <white>/bb stress 50</white> puis <white>/bb perf</white> pour trouver"
                + " l'etape fautive.</gray>");
        } else if (s.averageTickMs() > 15.0) {
            findings.add("<#FBBF24>MSPT " + String.format(Locale.ROOT, "%.1f", s.averageTickMs())
                + " ms</#FBBF24> <gray>- au-dela de 15 ms la marge avant le tick de 50 ms se reduit.</gray>");
        }
        return findings;
    }

    /**
     * Neutral, non-actionable note about world-edit plugins: the operator keeps them on purpose.
     * They are reported, never prescribed.
     */
    public static String thirdPartyNote(Snapshot s) {
        boolean axiom = s.axiomInstalled();
        boolean we = s.worldEditInstalled();
        if (!axiom && !we) {
            return null;
        }
        String names = axiom && we ? "AxiomPaper + FastAsyncWorldEdit"
            : (axiom ? "AxiomPaper" : "FastAsyncWorldEdit");
        return "<gray>" + names + " : conserves (choix du serveur). BosketBall ne s'en sert pas"
            + " pour ses courts ; leur cout eventuel se lit dans <white>/spark</white>.</gray>";
    }

    /**
     * One line of cache pressure. A per-player cache that never shrinks is the slowest kind of
     * leak: it looks fine for weeks and then eats the heap. Pure, unit-tested.
     */
    public static String cacheLine(int msgEntries, int msgLimit, int ggEntries, int ggLimit) {
        return "<gray>caches:</gray> <white>msg.parse " + msgEntries + "/" + msgLimit
            + "</white> <dark_gray>\u00b7</dark_gray> <white>gg " + ggEntries + "/" + ggLimit
            + "</white>" + (msgEntries >= msgLimit || ggEntries >= ggLimit
                ? " <#F87171>\u26a0 saturado</#F87171>" : "");
    }

    /** Compact one-line summary for the console log. */
    public static String summary(Snapshot s) {
        List<String> findings = audit(s);
        if (findings.isEmpty()) {
            return "ras - rien a optimiser";
        }
        return findings.size() + " point(s) a optimiser";
    }
}
