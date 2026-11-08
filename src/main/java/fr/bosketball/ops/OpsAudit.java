package fr.bosketball.ops;

import fr.bosketball.util.Palette;
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
        // Round 42: every check below dereferences the snapshot. /bb audit is a command: an empty
        // snapshot (a reload raced the caller) must print "nothing to fix", not a NPE.
        List<String> findings = new ArrayList<>();
        if (s == null) {
            return findings;
        }

        if (s.pasteBlocksPerTick() > 4000) {
            findings.add("<" + Palette.DANGER + ">schematic-courts.max-blocks-per-tick = " + s.pasteBlocksPerTick()
                + "</" + Palette.DANGER + "> <#94A3B8>- au-dela de 4000 une tranche depasse 20 ms (mesure : 214 ms a 40 000)."
                + " Le plugin le ramene a 4000 et s'auto-regule a 2 ms/tranche, mais baisse la valeur"
                + " dans config.yml pour que le fichier reste coherent.</#94A3B8>");
        }
        if (s.viewDistance() > 6) {
            findings.add("<" + Palette.GOLD + ">view-distance = " + s.viewDistance() + "</" + Palette.GOLD + "> <#94A3B8>- les courts sont"
                + " espaces de 384 blocs : une vue de 6+ charge des chunks vides. Passer a"
                + " <#F8FAFC>5</#F8FAFC> dans server.properties.</#94A3B8>");
        }
        if (s.simulationDistance() > 6) {
            findings.add("<" + Palette.GOLD + ">simulation-distance = " + s.simulationDistance() + "</" + Palette.GOLD + "> <#94A3B8>-"
                + " passer a <#F8FAFC>4</#F8FAFC> : au-dela, des entites tickent hors de vue pour rien.</#94A3B8>");
        }
        if (s.maxHeapMb() > 0 && s.maxHeapMb() < 2048) {
            findings.add("<" + Palette.GOLD + ">Xmx = " + s.maxHeapMb() + " Mo</" + Palette.GOLD + "> <#94A3B8>- trop bas pour 50 joueurs"
                + " (cible 3,5 Go). Voir docs/server-optimization/java-flags.txt.</#94A3B8>");
        }
        if (s.loadedChunks() > 600) {
            findings.add("<" + Palette.GOLD + ">" + s.loadedChunks() + " chunks charges</" + Palette.GOLD + "> <#94A3B8>- verifier"
                + " <#F8FAFC>schematic-courts.instances</#F8FAFC> et le pre-chauffage : 4 courts suffisent"
                + " pour le premier flux de joueurs.</#94A3B8>");
        }
        if (s.entities() > 600) {
            findings.add("<" + Palette.GOLD + ">" + s.entities() + " entites</" + Palette.GOLD + "> <#94A3B8>- les display entities"
                + " coutent ~1,5 microseconde/tick chacune (mesure : 600 entites = +0,7 ms). Les"
                + " hologrammes et les courts non utilises doivent rester despawnes.</#94A3B8>");
        }
        // A freshly started server reports NaN for a few ticks (no tick average yet); NaN compares
        // false everywhere, so the old code silently skipped the check — which is the right outcome,
        // but it now says so explicitly instead of relying on a comparison accident.
        if (Double.isFinite(s.tps()) && s.tps() < 19.5) {
            findings.add("<" + Palette.DANGER + ">TPS " + String.format(Locale.ROOT, "%.2f", s.tps()) + "</" + Palette.DANGER + "> <#94A3B8>-"
                + " lancer <#F8FAFC>/bb stress 50</#F8FAFC> puis <#F8FAFC>/bb perf</#F8FAFC> pour trouver"
                + " l'etape fautive.</#94A3B8>");
        } else if (Double.isFinite(s.averageTickMs()) && s.averageTickMs() > 15.0) {
            findings.add("<" + Palette.GOLD + ">MSPT " + String.format(Locale.ROOT, "%.1f", s.averageTickMs())
                + " ms</" + Palette.GOLD + "> <#94A3B8>- au-dela de 15 ms la marge avant le tick de 50 ms se reduit.</#94A3B8>");
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
        return "<#94A3B8>" + names + " : conserves (choix du serveur). BosketBall ne s'en sert pas"
            + " pour ses courts ; leur cout eventuel se lit dans <#F8FAFC>/spark</#F8FAFC>.</#94A3B8>";
    }

    /**
     * One line of cache pressure. A per-player cache that never shrinks is the slowest kind of
     * leak: it looks fine for weeks and then eats the heap. Pure, unit-tested.
     */
    public static String cacheLine(int msgEntries, int msgLimit, int ggEntries, int ggLimit) {
        return "<#94A3B8>caches:</#94A3B8> <#F8FAFC>msg.parse " + msgEntries + "/" + msgLimit
            + "</#F8FAFC> <#64748B>\u00b7</#64748B> <#F8FAFC>gg " + ggEntries + "/" + ggLimit
            + "</#F8FAFC>" + (msgEntries >= msgLimit || ggEntries >= ggLimit
                ? " <" + Palette.DANGER + ">\u26a0 saturado</" + Palette.DANGER + ">" : "");
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
