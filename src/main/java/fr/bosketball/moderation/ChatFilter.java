package fr.bosketball.moderation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * BosketBall chat profanity filter — French &amp; English, evasion-resistant.
 *
 * <p>Detection is layered so real words are never punished:</p>
 * <ol>
 *   <li><b>Normalisation</b>: unicode accent folding, lower-casing, leet-speak substitution
 *       ({@code 0→o, 1→i, 3→e, 4→a, 5→s, 7→t, @→a, $→s}), stretched-letter collapsing
 *       ({@code saaalaaape → salape}) and separator stripping ({@code s.a.l.o.p.e},
 *       {@code sa-lope}, {@code s a l o p e} all collapse to {@code salope}).</li>
 *   <li><b>Whole-token matching</b> for short words such as {@code con}, {@code cul}, {@code pd}
 *       — so {@code concert}, {@code concept}, {@code culture}, {@code class} or {@code compass}
 *       are never flagged.</li>
 *   <li><b>Distinctive-substring matching</b> only for long unambiguous stems
 *       ({@code encule}, {@code connard}, {@code salope}, {@code fdp}, {@code ntm}…).</li>
 *   <li><b>Phrase matching</b> on the fully collapsed message for multi-word insults
 *       ({@code fils de pute}, {@code ta gueule}, {@code nique ta mère}).</li>
 *   <li><b>Explicit allow-list</b> protects legitimate words that happen to contain a stem
 *       ({@code assassin}, {@code assist}, {@code bassin}, {@code culotte}… no — see
 *       {@code ALLOWED_TOKENS}).</li>
 * </ol>
 *
 * <p>The filter never blocks the message: the offending words are replaced with
 * {@code ███} blocks (keeping the length so the sentence stays readable), the author gets a
 * private warning, and three warnings lead to an automatic temporary ban whose reason carries
 * the Discord invite.</p>
 */
public final class ChatFilter {

    /** How severe a matched term is. Slurs earn double warn points. */
    public enum Severity {
        /** Very strong insult or hate slur — immediately censored, 2 warn points. */
        SEVERE,
        /** Common insult — censored, 1 warn point. */
        INSULT
    }

    /** Outcome of a scan. */
    public record Result(
        String cleaned,
        Set<String> matchedTerms,
        Severity worstSeverity,
        boolean altered
    ) {
        public boolean shouldInformAuthor() {
            return altered;
        }

        public int warnPoints() {
            if (matchedTerms.isEmpty()) {
                return 0;
            }
            return worstSeverity == Severity.SEVERE ? 2 : 1;
        }

        public String censoredSummary() {
            if (matchedTerms.isEmpty()) {
                return "";
            }
            return String.join(", ", matchedTerms);
        }
    }

    private static final String CENSOR_BLOCK = "█";

    /**
     * Short terms that are only matched as a COMPLETE token (never as a substring),
     * which keeps {@code con}/ {@code cul}/ {@code pd} from firing inside real words.
     */
    private static final Set<String> BANNED_TOKENS = Set.of(
        "con", "conne", "connes", "cons", "cul", "culs",
        "merde", "merdes", "merdeux", "merdique", "merdiques", "putain",
        "putains", "pute", "putes", "putasse", "salope", "salopes",
        "salaud", "salauds", "connard", "connards", "connasse", "connasses",
        "conasse", "encule", "encules", "enculer", "enculee", "enculé",
        "enculés", "enculée", "nique", "niquer", "niqueur", "niq",
        "fdp", "ntm", "tg", "ftg", "vtml", "vtff",
        "batard", "batards", "batar", "bta", "abruti", "abrutis",
        "debilos", "debilo", "couilles", "couillon", "couillonne", "grognasse",
        "pouffiasse", "tarlouze", "tarlouz", "pede", "pederaste", "tapette",
        "tapettes", "tantouze", "negre", "negres", "negro", "bicot",
        "bicots", "youpin", "youpins", "raton", "ratons", "vaquer",
        "niquetamere", "connerie", "conneries", "conard", "fuck", "fucks",
        "fucked", "fucker", "fuckers", "fucking", "fuk", "fck",
        "fuq", "shit", "shits", "shitty", "shitting", "bullshit",
        "bitch", "bitches", "bitching", "asshole", "assholes", "dumbshit",
        "dipshit", "bastard", "bastards", "dick", "dicks", "dickhead",
        "cock", "cocks", "cocksucker", "pussy", "pussies", "cunt",
        "cunts", "whore", "whores", "slut", "sluts", "faggot",
        "faggots", "fag", "fags", "dyke", "nigger", "niggers",
        "nigga", "niggas", "niga", "stfu", "gtfo", "kys",
        "lmfao"
    );

    /**
     * Distinctive stems long enough to be matched as a substring of a collapsed token.
     * Anything that could hide inside a normal word is deliberately absent.
     */
    private static final List<String> BANNED_STEMS = List.of(
        "encule", "connard", "connasse", "conasse", "salope", "salaud",
        "niqueta", "niquetamere", "niquetamerr", "niquetamera",
        "pouffiasse", "grognasse", "tarlouze", "pederaste", "pederas",
        "enculer", "enculat", "enculade",
        "fuckyour", "fuckyou", "motherfuck", "motherfuk",
        "asshole", "dickhead", "cocksuck", "bullshit",
        "nigger", "nigga", "faggot", "bicot", "youpin",
        "killyourself", "kys", "grosfdp", "filsdepute", "fisdepute"
    );

    /** Multi-word insults matched on the fully collapsed message. */
    private static final List<String> BANNED_PHRASES = List.of(
        "filsdepute", "fillesdepute", "fisdepute", "fdputain",
        "tagueule", "taguelle", "fermetagueule", "niquetamere", "niquetesmort", "fermela",
        "niquetafamille", "niquetafam", "vtff", "vattefairefoutre",
        "suckmydick", "eatshit", "killyourself",
        "gofuckyourself"
    );

    /**
     * Real words that would otherwise trip a stem/token rule. Compared after normalisation.
     * Kept deliberately short: whole-token matching already protects most of them.
     */
    private static final Set<String> ALLOWED_TOKENS = Set.of(
        "class", "classes", "classic", "classique", "assist", "assists",
        "assiste", "assistances", "pass", "passes", "passer", "passe",
        "passed", "passing", "bass", "bassin", "glass", "grass",
        "mass", "massive", "compass", "cassette", "assassin", "assassins",
        "assume", "assure", "content", "concert", "concept", "contre",
        "controle", "connexion", "conseil", "conclure", "condition", "congrats",
        "connect", "connexions", "contact", "concours", "conduite", "culture",
        "cultive", "cuisine", "cuillere", "culotte", "culpabilite", "retard",
        "retards", "retardataire", "retarder", "fagot", "fagots", "fagoting",
        "easy", "nob", "dickens", "shittake", "gueule", "gueules",
        "ferme", "fermeture", "fermer", "degage", "degager", "degagement",
        "bite", "bites", "chi", "pd", "pds", "pdb",
        "salopette", "salopettes", "bordel", "bordelique", "bordelais", "chiotte",
        "chiottes", "zob", "zgeg", "noob", "noobs", "trashy",
        "ez", "retardtastic", "assistant", "assistance", "classement", "classe",
        "classer", "passion", "passif", "compte", "dispute", "depute",
        "repartie"
    );

    /** Extra protection: tokens that contain "pd" but are legit. */
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{2,}");
    /** Runs of two or more identical characters, collapsed to one (evasion resistance). */
    private static final Pattern SQUEEZE = Pattern.compile("(.)\\1{1,}");

    private static final java.util.Map<Character, Character> LEET = java.util.Map.ofEntries(
        java.util.Map.entry('0', 'o'), java.util.Map.entry('1', 'i'), java.util.Map.entry('3', 'e'),
        java.util.Map.entry('4', 'a'), java.util.Map.entry('5', 's'), java.util.Map.entry('7', 't'),
        java.util.Map.entry('8', 'b'), java.util.Map.entry('@', 'a'), java.util.Map.entry('$', 's'),
        java.util.Map.entry('!', 'i'), java.util.Map.entry('|', 'i'), java.util.Map.entry('+', 't')
    );

    private ChatFilter() {
    }

    /** Collapses accents, leet-speak, padding characters and stretched letters. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = Character.toLowerCase(decomposed.charAt(i));
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            Character mapped = LEET.get(c);
            sb.append(mapped != null ? mapped : c);
        }
        String collapsed = REPEATED.matcher(sb.toString()).replaceAll("$1$1");
        return MULTI_SPACE.matcher(collapsed).replaceAll(" ").trim();
    }

    /** Removes every separator so {@code s.a.l.o.p.e} becomes {@code salope}. */
    public static String collapse(String normalized) {
        return NON_ALNUM.matcher(normalized).replaceAll("");
    }

    /**
     * Scans a chat line, returning the censored version plus diagnostics.
     * The original message is always kept readable apart from the masked words.
     */
    public static Result scan(String message) {
        if (message == null || message.isBlank()) {
            return new Result(message == null ? "" : message, Set.of(), Severity.INSULT, false);
        }
        Set<String> hits = new LinkedHashSet<>();
        Severity worst = Severity.INSULT;
        boolean altered = false;
        boolean wordLevelHit = false;

        String[] words = message.split(" ", -1);
        StringBuilder rebuilt = new StringBuilder(message.length() + 8);
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                rebuilt.append(' ');
            }
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            String norm = collapse(normalize(word));
            if (norm.isEmpty()) {
                rebuilt.append(word);
                continue;
            }
            Severity sev = classifyToken(norm);
            if (sev != null) {
                hits.add(norm);
                if (sev == Severity.SEVERE) {
                    worst = Severity.SEVERE;
                }
                altered = true;
                wordLevelHit = true;
                rebuilt.append(CENSOR_BLOCK.repeat(Math.max(3, Math.min(12, word.length()))));
            } else {
                rebuilt.append(word);
            }
        }

        // Spelled-out evasion: "f u c k" / "s a l o p e" — every token is 1-2 chars.
        // Joined and scanned once, so the rebuilt message masks the whole group.
        if (words.length >= 3 && java.util.Arrays.stream(words).allMatch(w -> w.length() <= 2)) {
            String joined = collapse(normalize(message));
            Severity sev = classifyToken(joined);
            if (sev != null) {
                hits.add(joined);
                if (sev == Severity.SEVERE) {
                    worst = Severity.SEVERE;
                }
                altered = true;
                wordLevelHit = true;
                rebuilt.setLength(0);
                rebuilt.append(CENSOR_BLOCK.repeat(Math.max(3, Math.min(12, message.length()))));
            }
        }

        // Phrase-level pass on the fully collapsed line (catches "fils de pute", "s a l o p e"…).
        // A phrase hit used to be recorded without ever touching `rebuilt`, so the message went out
        // verbatim while the author was still handed 2 warn points and told it "was censored" —
        // three of those auto-banned somebody for nothing. `phraseHit` now drives the masking.
        String wholeLine = collapse(normalize(message));
        String squeezedLine = squeeze(wholeLine);
        boolean phraseHit = false;
        for (String phrase : BANNED_PHRASES) {
            String collapsedPhrase = collapse(normalize(phrase));
            String squeezedPhrase = squeeze(collapsedPhrase);
            if (collapsedPhrase.isEmpty()) {
                continue;
            }
            // Never fire on a phrase that is itself an allow-listed everyday word.
            if (ALLOWED_TOKENS.contains(collapsedPhrase) || ALLOWED_TOKENS.contains(squeezedPhrase)
                // "ta gueule" is a real phrase, but "ta" alone is allow-listed; the guard must only
                // skip phrases whose FULL text is a genuine everyday word ("gueule" used to be in
                // the phrase list and silenced "ferme ta gueule"; the short-word allow-list is not
                // the place to decide that).
                || isBenignShortWord(collapsedPhrase)) {
                continue;
            }
            if (wholeLine.contains(collapsedPhrase) || squeezedLine.contains(squeezedPhrase)) {
                hits.add(phrase);
                worst = Severity.SEVERE;
                altered = true;
                phraseHit = true;
            }
        }

        String cleaned = altered ? rebuilt.toString() : message;
        if (phraseHit && !wordLevelHit) {
            // Phrase-only hit: mask the line, otherwise the word pass left it untouched.
            cleaned = CENSOR_BLOCK.repeat(Math.max(3, Math.min(32, message.length())));
        }
        return new Result(cleaned, hits, worst, altered);
    }

    /** True only for allow-listed everyday words that must never be treated as an insult alone. */
    private static boolean isBenignShortWord(String collapsedPhrase) {
        return collapsedPhrase.length() <= 6 && ALLOWED_TOKENS.contains(collapsedPhrase);
    }

    /** Returns the severity of a normalised token, or {@code null} when it is clean. */
    private static Severity classifyToken(String norm) {
        if (norm.isEmpty()) {
            return null;
        }
        if (ALLOWED_TOKENS.contains(norm)) {
            return null;
        }
        if (BANNED_TOKENS.contains(norm)) {
            return severityOf(norm);
        }
        // Long, unambiguous stems only — protects every real word listed in ALLOWED_TOKENS
        for (String stem : BANNED_STEMS) {
            if (norm.contains(stem)) {
                return severityOf(stem);
            }
        }
        // Stretched / padded variants: "saaaloooope" collapses to "salope".
        String squeezed = squeeze(norm);
        if (!squeezed.equals(norm)) {
            if (BANNED_TOKENS.contains(squeezed)) {
                return severityOf(squeezed);
            }
            for (String stem : BANNED_STEMS) {
                if (squeezed.contains(stem)) {
                    return severityOf(stem);
                }
            }
        }
        return null;
    }

    /**
     * Collapses any run of three or more identical letters to two, repeatedly, until stable.
     *
     * <p>{@code fuuuck → fuck}, {@code saaaloooope → salope}. Two letters are kept rather than
     * one so legitimate double letters survive ({@code assist}, {@code class}, {@code bass}),
     * and every result is re-checked against the allow-list before being flagged.</p>
     */
    public static String squeeze(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        String current = token;
        for (int guard = 0; guard < 6; guard++) {
            String next = SQUEEZE.matcher(current).replaceAll("$1");
            if (next.equals(current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    private static Severity severityOf(String term) {
        return switch (term) {
            case "negre", "negres", "negro", "bicot", "bicots", "youpin", "youpins", "raton", "ratons",
                 "pede", "pederaste", "tarlouze", "tantouze",
                 "nigger", "niggers", "nigga", "niggas", "niga", "faggot", "faggots", "fag", "fags", "dyke",
                 "niquetamere", "filsdepute", "fisdepute", "kys", "killyourself" -> Severity.SEVERE;
            default -> Severity.INSULT;
        };
    }

    /** True when the raw line contains at least one censored term. */
    public static boolean isOffensive(String message) {
        return scan(message).altered();
    }

    /** Debug helper used by tests: every dictionary term must be detected on its own. */
    public static List<String> dictionarySample() {
        List<String> out = new ArrayList<>(BANNED_TOKENS);
        out.sort(String::compareTo);
        return out;
    }
}
