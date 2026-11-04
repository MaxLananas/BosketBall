package fr.bosketball.moderation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ChatFilter {

    public enum Severity {

        SEVERE,

        INSULT
    }

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

    private static final List<String> BANNED_PHRASES = List.of(
        "filsdepute", "fillesdepute", "fisdepute", "fdputain",
        "tagueule", "taguelle", "fermetagueule", "niquetamere", "niquetesmort", "fermela",
        "niquetafamille", "niquetafam", "vtff", "vattefairefoutre",
        "suckmydick", "eatshit", "killyourself",
        "gofuckyourself"
    );

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

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");
    private static final Pattern REPEATED = Pattern.compile("(.)\\1{2,}");

    private static final Pattern SQUEEZE = Pattern.compile("(.)\\1{1,}");

    private static final java.util.Map<Character, Character> LEET = java.util.Map.ofEntries(
        java.util.Map.entry('0', 'o'), java.util.Map.entry('1', 'i'), java.util.Map.entry('3', 'e'),
        java.util.Map.entry('4', 'a'), java.util.Map.entry('5', 's'), java.util.Map.entry('7', 't'),
        java.util.Map.entry('8', 'b'), java.util.Map.entry('@', 'a'), java.util.Map.entry('$', 's'),
        java.util.Map.entry('!', 'i'), java.util.Map.entry('|', 'i'), java.util.Map.entry('+', 't')
    );

    private ChatFilter() {
    }

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

    public static String collapse(String normalized) {
        return NON_ALNUM.matcher(normalized).replaceAll("");
    }

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

        String wholeLine = collapse(normalize(message));
        String squeezedLine = squeeze(wholeLine);
        boolean phraseHit = false;
        for (String phrase : BANNED_PHRASES) {
            String collapsedPhrase = collapse(normalize(phrase));
            String squeezedPhrase = squeeze(collapsedPhrase);
            if (collapsedPhrase.isEmpty()) {
                continue;
            }

            if (ALLOWED_TOKENS.contains(collapsedPhrase) || ALLOWED_TOKENS.contains(squeezedPhrase)

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

            cleaned = CENSOR_BLOCK.repeat(Math.max(3, Math.min(32, message.length())));
        }
        return new Result(cleaned, hits, worst, altered);
    }

    private static boolean isBenignShortWord(String collapsedPhrase) {
        return collapsedPhrase.length() <= 6 && ALLOWED_TOKENS.contains(collapsedPhrase);
    }

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

        for (String stem : BANNED_STEMS) {
            if (norm.contains(stem)) {
                return severityOf(stem);
            }
        }

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

    public static boolean isOffensive(String message) {
        return scan(message).altered();
    }

    public static List<String> dictionarySample() {
        List<String> out = new ArrayList<>(BANNED_TOKENS);
        out.sort(String::compareTo);
        return out;
    }
}
