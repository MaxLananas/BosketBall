package fr.bosketball.i18n;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Translation engine. English is the base language: every key is defined here in English,
 * and other locales only override what they translate. A missing key therefore never renders
 * as {@code <missing>} — it silently falls back to English, which is what a player expects
 * when a server adds a new feature before its volunteers finish the translation.
 *
 * <p>Messages are MiniMessage strings so colour, gradients and glyph pills keep working in
 * every language. Placeholders use {@code {curly}} syntax: {@code lang.get(player, "match.win",
 * Map.of("coins", "40"))}.</p>
 */
public final class Lang {

    /** Supported locale codes. {@code en} is mandatory and always complete. */
    public enum Locale_ {
        EN("en", "English", "🇬🇧"),
        FR("fr", "Français", "🇫🇷"),
        ES("es", "Español", "🇪🇸"),
        DE("de", "Deutsch", "🇩🇪");

        private final String code;
        private final String displayName;
        private final String flag;

        Locale_(String code, String displayName, String flag) {
            this.code = code;
            this.displayName = displayName;
            this.flag = flag;
        }

        public String code() {
            return code;
        }

        public String displayName() {
            return displayName;
        }

        public String flag() {
            return flag;
        }

        public static Locale_ byCode(String raw) {
            if (raw == null) {
                return EN;
            }
            String norm = raw.trim().toLowerCase(Locale.ROOT);
            if (norm.contains("-")) {
                norm = norm.substring(0, norm.indexOf('-'));
            }
            for (Locale_ l : values()) {
                if (l.code.equals(norm)) {
                    return l;
                }
            }
            return EN;
        }

        public Locale_ next() {
            Locale_[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private static final Map<String, String> EN = new LinkedHashMap<>();
    private static final Map<Locale_, Map<String, String>> OVERRIDES = new LinkedHashMap<>();

    static {
        // ─────────────────────────────────────────────────────────────────────
        //  ENGLISH — the reference locale, always complete.
        // ─────────────────────────────────────────────────────────────────────
        // Generic
        EN.put("generic.on", "<#4ADE80>ON</#4ADE80>");
        EN.put("generic.off", "<#F87171>OFF</#F87171>");
        EN.put("generic.yes", "Yes");
        EN.put("generic.no", "No");
        EN.put("generic.none", "None");
        EN.put("generic.back", "Back");
        EN.put("generic.close", "Close");
        EN.put("generic.coins", "{amount} Coins");
        EN.put("generic.elo", "{amount} ELO");
        EN.put("generic.loading", "Loading…");
        EN.put("generic.unknown", "Unknown");
        EN.put("generic.player_only", "This command is for players only.");
        EN.put("generic.no_permission", "You do not have permission to do that.");
        EN.put("generic.not_in_match", "You are not in a match right now.");
        EN.put("generic.not_enough_coins", "You need {amount} Coins for that.");

        // Matchmaking
        EN.put("queue.joined", "You joined the <white>{mode}</white> queue ({queue}).");
        EN.put("queue.left", "You left the queue.");
        EN.put("queue.casual", "Casual");
        EN.put("queue.ranked", "Ranked");
        EN.put("queue.casual_hint", "Casual matches never change your ELO and pay fewer Coins.");
        EN.put("queue.estimate", "Estimated wait: {seconds}s");
        EN.put("queue.full", "That queue is full right now.");
        EN.put("queue.already_in", "You are already in a queue.");

        EN.put("match.found", "<#4ADE80><bold>MATCH FOUND!</bold></#4ADE80> <gray>Accept in {seconds}s.</gray>");
        EN.put("match.found_detail", "{mode} • {players} players • {arena}");
        EN.put("match.accept", "<#4ADE80><bold>[ ACCEPT ]</bold></#4ADE80>");
        EN.put("match.decline", "<#F87171><bold>[ DECLINE ]</bold></#F87171>");
        EN.put("match.accepted", "Match accepted — good luck!");
        EN.put("match.declined", "You declined the match. Queue penalty: {seconds}s.");
        EN.put("match.expired", "The match proposal expired because someone did not accept.");
        EN.put("match.dodge_penalty", "You cannot queue again for {seconds}s (dodge penalty).");
        EN.put("match.remake", "<#FBBF24><bold>REMAKE</bold></#FBBF24> — match cancelled, no ELO lost.");
        EN.put("match.requeue", "Re-queue now?");

        EN.put("match.started", "Match started: {mode} on {arena}");
        EN.put("match.won", "<#4ADE80><bold>VICTORY</bold></#4ADE80> — {coins} Coins");
        EN.put("match.lost", "<#F87171><bold>DEFEAT</bold></#F87171> — better luck next time");
        EN.put("match.mvp", "<#FFD43B><bold>MVP:</bold></#FFD43B> {player}");
        EN.put("match.elo_delta", "ELO {sign}{delta}");
        EN.put("match.forfeit", "{player} forfeited the match.");

        // Economy
        EN.put("coins.received", "You received <#FDE047>{amount} Coins</#FDE047>.");
        EN.put("coins.spent", "You spent <#FDE047>{amount} Coins</#FDE047>.");
        EN.put("coins.balance", "Balance: <#FDE047>{amount} Coins</#FDE047>");

        // Progression
        EN.put("quests.title", "Quest Board");
        EN.put("quests.daily", "Daily");
        EN.put("quests.weekly", "Weekly");
        EN.put("quests.contract", "Contract");
        EN.put("quests.progress", "Progress: {current}/{target}");
        EN.put("quests.complete", "<#4ADE80>COMPLETE — claim your reward!</#4ADE80>");
        EN.put("quests.claimed", "<#64748B>Already claimed</#64748B>");
        EN.put("quests.claim_all", "Claim all");
        EN.put("quests.nothing_to_claim", "No completed quest to claim yet — keep playing!");
        EN.put("quests.claimed_count", "Claimed {count} quest(s) for {coins} Coins.");
        EN.put("quests.reroll", "Reroll contract");
        EN.put("quests.expires_in", "Expires in {time}");

        EN.put("achievements.title", "Achievements");
        EN.put("achievements.unlocked", "Unlocked {count}/{total}");
        EN.put("achievements.locked", "Locked");
        EN.put("achievements.reward", "Reward: {coins} Coins");

        EN.put("tutorial.start", "<#4ADE80><bold>Tutorial</bold></#4ADE80> — step {step}/{total}");
        EN.put("tutorial.next", "Next step");
        EN.put("tutorial.skip", "Skip");
        EN.put("tutorial.pause", "Pause");
        EN.put("tutorial.finished", "<#4ADE80><bold>Tutorial complete!</bold></#4ADE80> +{coins} Coins");
        EN.put("tutorial.optional", "This tutorial is optional — you can restart it anytime with /tuto.");

        // Crates
        EN.put("crate.title", "Reward Crate");
        EN.put("crate.cost", "Cost: {coins}");
        EN.put("crate.opened", "You opened a reward crate!");
        EN.put("crate.reward_coins", "+{amount} Coins");
        EN.put("crate.reward_unlock", "Unlocked: {name}");
        EN.put("crate.daily_limit", "Daily limit reached — come back tomorrow.");
        EN.put("crate.cooldown", "The crate needs a moment to recharge ({seconds}s).");

        // Social
        EN.put("friends.title", "Friends");
        EN.put("friends.added", "{player} is now your friend.");
        EN.put("friends.removed", "{player} was removed from your friends.");
        EN.put("friends.request_sent", "Friend request sent to {player}.");
        EN.put("friends.online", "Online: {count}");
        EN.put("social.status_lobby", "In the Lobby");
        EN.put("social.status_queue", "In Queue");
        EN.put("social.status_match", "On Court");
        EN.put("social.status_spectating", "Spectating");
        EN.put("social.status_locker", "Locker Room");
        EN.put("social.status_tutorial", "Tutorial");
        EN.put("social.status_afk", "Away From Keyboard");
        EN.put("social.status_offline", "Offline");

        EN.put("party.created", "Party created — invite friends with /party invite <player>.");
        EN.put("party.joined", "{player} joined your party.");
        EN.put("party.left", "{player} left the party.");
        EN.put("party.chat_on", "Party chat enabled — only your party sees your messages.");
        EN.put("party.chat_off", "Party chat disabled.");

        // Guilds
        EN.put("guild.title", "Guild");
        EN.put("guild.created", "Guild {name} created!");
        EN.put("guild.joined", "You joined {name}.");
        EN.put("guild.left", "You left the guild.");
        EN.put("guild.invited", "Invitation sent to {player}.");
        EN.put("guild.disbanded", "Guild disbanded.");
        EN.put("guild.promoted", "{player} is now {role}.");
        EN.put("guild.no_guild", "You are not in a guild.");
        EN.put("guild.elo", "Guild ELO: {amount}");
        EN.put("guild.rank", "Rank #{place}");

        // Moderation
        EN.put("chat.warning", "<#F87171><bold>⚠ Insults are not tolerated here.</bold></#F87171> Warning {count}/{max}.");
        EN.put("chat.warning_last", "<#F87171><bold>⚠ Final warning!</bold></#F87171> One more and you are banned.");
        EN.put("chat.muted", "You are muted for {time}.");
        EN.put("chat.ban_reason", "Insults repeated after {count} warnings — appeal on our Discord: {discord}");
        EN.put("chat.censored", "Your message was censored for insulting language.");
        EN.put("report.sent", "Report sent to the staff team. Thank you.");
        EN.put("report.usage", "Usage: /report <player> <reason>");
        EN.put("report.rate_limited", "You are reporting too fast — please wait {seconds}s.");
        EN.put("report.not_found", "Unknown player: {player}");

        // Leaderboards
        EN.put("leaderboard.title", "{category} Leaderboard");
        EN.put("leaderboard.empty", "No data yet — play a match to appear here!");
        EN.put("leaderboard.place", "#{place} {player} — {value}");
        EN.put("leaderboard.updated", "Updated {seconds}s ago");

        // Admin / misc
        EN.put("admin.only", "That requires operator permissions.");
        EN.put("admin.saved", "Saved.");
        EN.put("admin.reloaded", "Configuration reloaded.");
        EN.put("storage.backup", "Backup written: {file}");


        // ── GUI banner captions (rendered inside every cream plaque) ───────────
        EN.put("gui.title.hub", "LOBBY");
        EN.put("gui.title.play", "PLAY MODES");
        EN.put("gui.title.shop", "SHOP");
        EN.put("gui.title.shop.all", "SHOP");
        EN.put("gui.title.shop.balls", "BASKETBALLS");
        EN.put("gui.title.shop.dunks", "SIGNATURE DUNKS");
        EN.put("gui.title.shop.tags", "CHAT TAGS");
        EN.put("gui.title.cosmetics", "COSMETICS");
        EN.put("gui.title.tags", "CHAT TAGS");
        EN.put("gui.title.leaderboards", "LEADERBOARDS");
        EN.put("gui.title.settings", "SETTINGS");
        EN.put("gui.title.quests", "QUESTS");
        EN.put("gui.title.achievements", "ACHIEVEMENTS");
        EN.put("gui.title.inbox", "MAILBOX");
        EN.put("gui.title.friends", "FRIENDS");
        EN.put("gui.title.guilds", "GUILDS");
        EN.put("gui.guild.roster", "ROSTER");
        EN.put("gui.guild.invites", "INVITATIONS");
        EN.put("gui.guild.settings", "GUILD SETTINGS");
        EN.put("gui.guild.ladder", "GUILD LADDER");
        EN.put("gui.guild.open", "OPEN GUILDS");
        EN.put("gui.title.bots", "TRAINING & AI");
        EN.put("gui.title.reports", "REPORTS");
        EN.put("gui.title.warnings", "WARNINGS");
        EN.put("gui.title.history", "MATCH HISTORY");
        EN.put("gui.title.ranks", "RANKS GUIDE");
        EN.put("gui.title.admin", "SERVER CONTROL");
        EN.put("gui.title.spectate", "SPECTATE");
        EN.put("gui.language", "Language");
        EN.put("gui.language.hint", "Click to switch: {locale}");

        // ─────────────────────────────────────────────────────────────────────
        //  FRENCH
        // ─────────────────────────────────────────────────────────────────────
        Map<String, String> fr = new LinkedHashMap<>();
        fr.put("generic.on", "<#4ADE80>ACTIVÉ</#4ADE80>");
        fr.put("generic.off", "<#F87171>DÉSACTIVÉ</#F87171>");
        fr.put("generic.yes", "Oui");
        fr.put("generic.no", "Non");
        fr.put("generic.none", "Aucun");
        fr.put("generic.back", "Retour");
        fr.put("generic.close", "Fermer");
        fr.put("generic.coins", "{amount} pièces");
        fr.put("generic.elo", "{amount} ELO");
        fr.put("generic.loading", "Chargement…");
        fr.put("generic.unknown", "Inconnu");
        fr.put("generic.player_only", "Cette commande est réservée aux joueurs.");
        fr.put("generic.no_permission", "Vous n'avez pas la permission de faire ça.");
        fr.put("generic.not_in_match", "Vous n'êtes pas dans un match actuellement.");
        fr.put("generic.not_enough_coins", "Il vous faut {amount} pièces pour cela.");

        fr.put("queue.joined", "Vous avez rejoint la file <white>{mode}</white> ({queue}).");
        fr.put("queue.left", "Vous avez quitté la file d'attente.");
        fr.put("queue.casual", "Détente");
        fr.put("queue.ranked", "Classé");
        fr.put("queue.casual_hint", "Les matchs détente ne changent jamais votre ELO et rapportent moins de pièces.");
        fr.put("queue.estimate", "Attente estimée : {seconds}s");
        fr.put("queue.full", "Cette file est complète pour le moment.");
        fr.put("queue.already_in", "Vous êtes déjà dans une file d'attente.");

        fr.put("match.found", "<#4ADE80><bold>MATCH TROUVÉ !</bold></#4ADE80> <gray>Acceptez en {seconds}s.</gray>");
        fr.put("match.found_detail", "{mode} • {players} joueurs • {arena}");
        fr.put("match.accept", "<#4ADE80><bold>[ ACCEPTER ]</bold></#4ADE80>");
        fr.put("match.decline", "<#F87171><bold>[ REFUSER ]</bold></#F87171>");
        fr.put("match.accepted", "Match accepté — bonne chance !");
        fr.put("match.declined", "Vous avez refusé le match. Pénalité de file : {seconds}s.");
        fr.put("match.expired", "La proposition de match a expiré car quelqu'un n'a pas accepté.");
        fr.put("match.dodge_penalty", "Vous ne pouvez pas relancer une file avant {seconds}s (pénalité d'esquive).");
        fr.put("match.remake", "<#FBBF24><bold>REMAKE</bold></#FBBF24> — match annulé, aucun ELO perdu.");
        fr.put("match.requeue", "Relancer la recherche ?");

        fr.put("match.started", "Match lancé : {mode} sur {arena}");
        fr.put("match.won", "<#4ADE80><bold>VICTOIRE</bold></#4ADE80> — {coins} pièces");
        fr.put("match.lost", "<#F87171><bold>DÉFAITE</bold></#F87171> — ce sera pour la prochaine fois");
        fr.put("match.mvp", "<#FFD43B><bold>MVP :</bold></#FFD43B> {player}");
        fr.put("match.elo_delta", "ELO {sign}{delta}");
        fr.put("match.forfeit", "{player} a abandonné le match.");

        fr.put("coins.received", "Vous avez reçu <#FDE047>{amount} pièces</#FDE047>.");
        fr.put("coins.spent", "Vous avez dépensé <#FDE047>{amount} pièces</#FDE047>.");
        fr.put("coins.balance", "Solde : <#FDE047>{amount} pièces</#FDE047>");

        fr.put("quests.title", "Tableau des quêtes");
        fr.put("quests.daily", "Quotidienne");
        fr.put("quests.weekly", "Hebdomadaire");
        fr.put("quests.contract", "Contrat");
        fr.put("quests.progress", "Progression : {current}/{target}");
        fr.put("quests.complete", "<#4ADE80>TERMINÉE — réclamez votre récompense !</#4ADE80>");
        fr.put("quests.claimed", "<#64748B>Déjà réclamée</#64748B>");
        fr.put("quests.claim_all", "Tout réclamer");
        fr.put("quests.nothing_to_claim", "Aucune quête terminée à réclamer — continuez à jouer !");
        fr.put("quests.claimed_count", "{count} quête(s) réclamée(s) pour {coins} pièces.");
        fr.put("quests.reroll", "Relancer le contrat");
        fr.put("quests.expires_in", "Expire dans {time}");

        fr.put("achievements.title", "Hauts faits");
        fr.put("achievements.unlocked", "Débloqués {count}/{total}");
        fr.put("achievements.locked", "Verrouillé");
        fr.put("achievements.reward", "Récompense : {coins} pièces");

        fr.put("tutorial.start", "<#4ADE80><bold>Tutoriel</bold></#4ADE80> — étape {step}/{total}");
        fr.put("tutorial.next", "Étape suivante");
        fr.put("tutorial.skip", "Passer");
        fr.put("tutorial.pause", "Pause");
        fr.put("tutorial.finished", "<#4ADE80><bold>Tutoriel terminé !</bold></#4ADE80> +{coins} pièces");
        fr.put("tutorial.optional", "Ce tutoriel est facultatif — relancez-le quand vous voulez avec /tuto.");

        fr.put("crate.title", "Caisse de récompense");
        fr.put("crate.cost", "Coût : {coins}");
        fr.put("crate.opened", "Vous avez ouvert une caisse de récompense !");
        fr.put("crate.reward_coins", "+{amount} pièces");
        fr.put("crate.reward_unlock", "Débloqué : {name}");
        fr.put("crate.daily_limit", "Limite journalière atteinte — revenez demain.");
        fr.put("crate.cooldown", "La caisse doit se recharger ({seconds}s).");

        fr.put("friends.title", "Amis");
        fr.put("friends.added", "{player} est maintenant votre ami.");
        fr.put("friends.removed", "{player} a été retiré de vos amis.");
        fr.put("friends.request_sent", "Demande d'ami envoyée à {player}.");
        fr.put("friends.online", "En ligne : {count}");
        fr.put("social.status_lobby", "Dans le lobby");
        fr.put("social.status_queue", "En file d'attente");
        fr.put("social.status_match", "Sur le terrain");
        fr.put("social.status_spectating", "En spectateur");
        fr.put("social.status_locker", "Vestiaire 3D");
        fr.put("social.status_tutorial", "Tutoriel");
        fr.put("social.status_afk", "Absent (AFK)");
        fr.put("social.status_offline", "Hors ligne");

        fr.put("party.created", "Groupe créé — invitez vos amis avec /party invite <joueur>.");
        fr.put("party.joined", "{player} a rejoint votre groupe.");
        fr.put("party.left", "{player} a quitté le groupe.");
        fr.put("party.chat_on", "Chat de groupe activé — seul votre groupe voit vos messages.");
        fr.put("party.chat_off", "Chat de groupe désactivé.");

        fr.put("guild.title", "Guilde");
        fr.put("guild.created", "Guilde {name} créée !");
        fr.put("guild.joined", "Vous avez rejoint {name}.");
        fr.put("guild.left", "Vous avez quitté la guilde.");
        fr.put("guild.invited", "Invitation envoyée à {player}.");
        fr.put("guild.disbanded", "Guilde dissoute.");
        fr.put("guild.promoted", "{player} est maintenant {role}.");
        fr.put("guild.no_guild", "Vous n'êtes pas dans une guilde.");
        fr.put("guild.elo", "ELO de guilde : {amount}");
        fr.put("guild.rank", "Rang #{place}");

        fr.put("chat.warning", "<#F87171><bold>⚠ Les insultes ne sont pas tolérées ici.</bold></#F87171> Avertissement {count}/{max}.");
        fr.put("chat.warning_last", "<#F87171><bold>⚠ Dernier avertissement !</bold></#F87171> Encore un et c'est le bannissement.");
        fr.put("chat.muted", "Vous êtes réduit au silence pendant {time}.");
        fr.put("chat.ban_reason", "Insultes répétées après {count} avertissements — recours sur notre Discord : {discord}");
        fr.put("chat.censored", "Votre message a été censuré pour langage insultant.");
        fr.put("report.sent", "Signalement transmis à l'équipe. Merci.");
        fr.put("report.usage", "Utilisation : /report <joueur> <raison>");
        fr.put("report.rate_limited", "Vous signalez trop vite — attendez {seconds}s.");
        fr.put("report.not_found", "Joueur inconnu : {player}");

        fr.put("leaderboard.title", "Classement {category}");
        fr.put("leaderboard.empty", "Aucune donnée — jouez un match pour apparaître ici !");
        fr.put("leaderboard.place", "#{place} {player} — {value}");
        fr.put("leaderboard.updated", "Mis à jour il y a {seconds}s");

        fr.put("admin.only", "Cela nécessite les permissions opérateur.");
        fr.put("admin.saved", "Enregistré.");
        fr.put("admin.reloaded", "Configuration rechargée.");
        fr.put("storage.backup", "Sauvegarde écrite : {file}");


        fr.put("gui.title.hub", "LOBBY");
        fr.put("gui.title.play", "MODES DE JEU");
        fr.put("gui.title.shop", "BOUTIQUE");
        fr.put("gui.title.shop.all", "BOUTIQUE");
        fr.put("gui.title.shop.balls", "BALLONS");
        fr.put("gui.title.shop.dunks", "DUNKS SIGNATURE");
        fr.put("gui.title.shop.tags", "TAGS DE CHAT");
        fr.put("gui.title.cosmetics", "COSMÉTIQUES");
        fr.put("gui.title.tags", "TAGS DE CHAT");
        fr.put("gui.title.leaderboards", "CLASSEMENTS");
        fr.put("gui.title.settings", "PARAMÈTRES");
        fr.put("gui.title.quests", "QUÊTES");
        fr.put("gui.title.achievements", "HAUTS FAITS");
        fr.put("gui.title.inbox", "BOÎTE AUX LETTRES");
        fr.put("gui.title.friends", "AMIS");
        fr.put("gui.title.guilds", "GUILDES");
        fr.put("gui.guild.roster", "EFFECTIF");
        fr.put("gui.guild.invites", "INVITATIONS");
        fr.put("gui.guild.settings", "RÉGLAGES DE GUILDE");
        fr.put("gui.guild.ladder", "CLASSEMENT DES GUILDES");
        fr.put("gui.guild.open", "GUILDES OUVERTES");
        fr.put("gui.title.bots", "ENTRAÎNEMENT & IA");
        fr.put("gui.title.reports", "SIGNALEMENTS");
        fr.put("gui.title.warnings", "AVERTISSEMENTS");
        fr.put("gui.title.history", "HISTORIQUE");
        fr.put("gui.title.ranks", "GUIDE DES RANGS");
        fr.put("gui.title.admin", "CONTRÔLE SERVEUR");
        fr.put("gui.title.spectate", "SPECTATEUR");
        fr.put("gui.language", "Langue");
        fr.put("gui.language.hint", "Cliquez pour changer : {locale}");

        // ─────────────────────────────────────────────────────────────────────
        //  SPANISH
        // ─────────────────────────────────────────────────────────────────────
        Map<String, String> es = new LinkedHashMap<>();
        es.put("generic.yes", "Sí");
        es.put("generic.no", "No");
        es.put("generic.back", "Volver");
        es.put("generic.close", "Cerrar");
        es.put("generic.coins", "{amount} monedas");
        es.put("generic.player_only", "Este comando es solo para jugadores.");
        es.put("generic.no_permission", "No tienes permiso para hacer eso.");
        es.put("generic.not_in_match", "No estás en una partida ahora mismo.");
        es.put("queue.joined", "Te has unido a la cola <white>{mode}</white> ({queue}).");
        es.put("queue.left", "Has salido de la cola.");
        es.put("queue.casual", "Casual");
        es.put("queue.ranked", "Clasificatoria");
        es.put("queue.casual_hint", "Las partidas casuales no cambian tu ELO y dan menos monedas.");
        es.put("match.found", "<#4ADE80><bold>¡PARTIDA ENCONTRADA!</bold></#4ADE80> <gray>Acepta en {seconds}s.</gray>");
        es.put("match.accept", "<#4ADE80><bold>[ ACEPTAR ]</bold></#4ADE80>");
        es.put("match.decline", "<#F87171><bold>[ RECHAZAR ]</bold></#F87171>");
        es.put("match.accepted", "Partida aceptada — ¡buena suerte!");
        es.put("match.declined", "Rechazaste la partida. Penalización: {seconds}s.");
        es.put("match.won", "<#4ADE80><bold>VICTORIA</bold></#4ADE80> — {coins} monedas");
        es.put("match.lost", "<#F87171><bold>DERROTA</bold></#F87171> — suerte la próxima vez");
        es.put("match.mvp", "<#FFD43B><bold>MVP:</bold></#FFD43B> {player}");
        es.put("quests.title", "Tablero de misiones");
        es.put("quests.daily", "Diaria");
        es.put("quests.weekly", "Semanal");
        es.put("quests.progress", "Progreso: {current}/{target}");
        es.put("quests.claim_all", "Reclamar todo");
        es.put("achievements.title", "Logros");
        es.put("tutorial.start", "<#4ADE80><bold>Tutorial</bold></#4ADE80> — paso {step}/{total}");
        es.put("crate.title", "Caja de recompensa");
        es.put("friends.title", "Amigos");
        es.put("guild.title", "Gremio");
        es.put("chat.warning", "<#F87171><bold>⚠ Los insultos no están permitidos.</bold></#F87171> Advertencia {count}/{max}.");
        es.put("report.sent", "Reporte enviado al equipo. ¡Gracias!");
        es.put("leaderboard.title", "Clasificación {category}");
        es.put("leaderboard.empty", "Sin datos todavía — ¡juega una partida!");


        es.put("gui.title.hub", "LOBBY");
        es.put("gui.title.play", "MODOS DE JUEGO");
        es.put("gui.title.shop", "TIENDA");
        es.put("gui.title.shop.all", "TIENDA");
        es.put("gui.title.shop.balls", "BALONES");
        es.put("gui.title.shop.dunks", "MATEOS");
        es.put("gui.title.shop.tags", "ETIQUETAS");
        es.put("gui.title.cosmetics", "COSMÉTICOS");
        es.put("gui.title.tags", "ETIQUETAS");
        es.put("gui.title.leaderboards", "CLASIFICACIÓN");
        es.put("gui.title.settings", "AJUSTES");
        es.put("gui.title.quests", "MISIONES");
        es.put("gui.title.achievements", "LOGROS");
        es.put("gui.title.inbox", "CORREO");
        es.put("gui.title.friends", "AMIGOS");
        es.put("gui.title.guilds", "GREMIOS");
        es.put("gui.guild.roster", "PLANTILLA");
        es.put("gui.guild.invites", "INVITACIONES");
        es.put("gui.guild.settings", "AJUSTES DEL GREMIO");
        es.put("gui.guild.ladder", "CLASIFICACIÓN DE GREMIOS");
        es.put("gui.guild.open", "GREMIOS ABIERTOS");
        es.put("gui.title.bots", "ENTRENO E IA");
        es.put("gui.title.reports", "REPORTES");
        es.put("gui.title.warnings", "ADVERTENCIAS");
        es.put("gui.title.history", "HISTORIAL");
        es.put("gui.title.ranks", "GUÍA DE RANGOS");
        es.put("gui.title.admin", "CONTROL DEL SERVIDOR");
        es.put("gui.title.spectate", "ESPECTADOR");
        es.put("gui.language", "Idioma");
        es.put("gui.language.hint", "Haz clic para cambiar: {locale}");

        // ─────────────────────────────────────────────────────────────────────
        //  GERMAN
        // ─────────────────────────────────────────────────────────────────────
        Map<String, String> de = new LinkedHashMap<>();
        de.put("generic.yes", "Ja");
        de.put("generic.no", "Nein");
        de.put("generic.back", "Zurück");
        de.put("generic.close", "Schließen");
        de.put("generic.coins", "{amount} Münzen");
        de.put("generic.player_only", "Dieser Befehl ist nur für Spieler.");
        de.put("generic.no_permission", "Dafür hast du keine Berechtigung.");
        de.put("queue.joined", "Du bist der <white>{mode}</white>-Warteschlange beigetreten ({queue}).");
        de.put("queue.left", "Du hast die Warteschlange verlassen.");
        de.put("queue.casual", "Casual");
        de.put("queue.ranked", "Rangliste");
        de.put("queue.casual_hint", "Casual-Spiele ändern deine ELO nicht und geben weniger Münzen.");
        de.put("match.found", "<#4ADE80><bold>SPIEL GEFUNDEN!</bold></#4ADE80> <gray>Akzeptiere in {seconds}s.</gray>");
        de.put("match.accept", "<#4ADE80><bold>[ ANNEHMEN ]</bold></#4ADE80>");
        de.put("match.decline", "<#F87171><bold>[ ABLEHNEN ]</bold></#F87171>");
        de.put("match.won", "<#4ADE80><bold>SIEG</bold></#4ADE80> — {coins} Münzen");
        de.put("match.lost", "<#F87171><bold>NIEDERLAGE</bold></#F87171> — nächstes Mal klappt es");
        de.put("match.mvp", "<#FFD43B><bold>MVP:</bold></#FFD43B> {player}");
        de.put("quests.title", "Questtafel");
        de.put("quests.daily", "Täglich");
        de.put("quests.weekly", "Wöchentlich");
        de.put("quests.claim_all", "Alles abholen");
        de.put("achievements.title", "Erfolge");
        de.put("tutorial.start", "<#4ADE80><bold>Tutorial</bold></#4ADE80> — Schritt {step}/{total}");
        de.put("crate.title", "Belohnungskiste");
        de.put("friends.title", "Freunde");
        de.put("guild.title", "Gilde");
        de.put("chat.warning", "<#F87171><bold>⚠ Beleidigungen sind hier nicht erlaubt.</bold></#F87171> Verwarnung {count}/{max}.");
        de.put("report.sent", "Meldung an das Team gesendet. Danke!");
        de.put("leaderboard.title", "Rangliste {category}");
        de.put("leaderboard.empty", "Noch keine Daten — spiele ein Spiel!");


        de.put("gui.title.hub", "LOBBY");
        de.put("gui.title.play", "SPIELMODI");
        de.put("gui.title.shop", "SHOP");
        de.put("gui.title.shop.all", "SHOP");
        de.put("gui.title.shop.balls", "BÄLLE");
        de.put("gui.title.shop.dunks", "DUNKS");
        de.put("gui.title.shop.tags", "CHAT-TAGS");
        de.put("gui.title.cosmetics", "KOSMETIK");
        de.put("gui.title.tags", "CHAT-TAGS");
        de.put("gui.title.leaderboards", "RANGLISTEN");
        de.put("gui.title.settings", "EINSTELLUNGEN");
        de.put("gui.title.quests", "QUESTS");
        de.put("gui.title.achievements", "ERFOLGE");
        de.put("gui.title.inbox", "POSTFACH");
        de.put("gui.title.friends", "FREUNDE");
        de.put("gui.title.guilds", "GILDEN");
        de.put("gui.guild.roster", "MITGLIEDER");
        de.put("gui.guild.invites", "EINLADUNGEN");
        de.put("gui.guild.settings", "GILDEN-EINSTELLUNGEN");
        de.put("gui.guild.ladder", "GILDEN-RANGLISTE");
        de.put("gui.guild.open", "OFFENE GILDEN");
        de.put("gui.title.bots", "TRAINING & KI");
        de.put("gui.title.reports", "MELDUNGEN");
        de.put("gui.title.warnings", "VERWARNUNGEN");
        de.put("gui.title.history", "VERLAUF");
        de.put("gui.title.ranks", "RANG-GUIDE");
        de.put("gui.title.admin", "SERVER-STEUERUNG");
        de.put("gui.title.spectate", "ZUSCHAUEN");
        de.put("gui.language", "Sprache");
        de.put("gui.language.hint", "Klicken zum Wechseln: {locale}");

        OVERRIDES.put(Locale_.FR, Collections.unmodifiableMap(fr));
        OVERRIDES.put(Locale_.ES, Collections.unmodifiableMap(es));
        OVERRIDES.put(Locale_.DE, Collections.unmodifiableMap(de));
    }

    private final Map<String, String> resolved = new LinkedHashMap<>();
    private final Locale_ locale;

    private Lang(Locale_ locale) {
        this.locale = locale;
        resolved.putAll(EN);
        Map<String, String> overrides = OVERRIDES.get(locale);
        if (overrides != null) {
            resolved.putAll(overrides);
        }
    }

    /** Builds (and caches internally per call) the table for a locale. */
    public static Lang of(String localeCode) {
        return new Lang(Locale_.byCode(localeCode));
    }

    public static Lang of(Locale_ locale) {
        return new Lang(locale == null ? Locale_.EN : locale);
    }

    public Locale_ locale() {
        return locale;
    }

    /** Raw MiniMessage string, or the English one when untranslated. */
    public String raw(String key) {
        String value = resolved.get(key);
        return value != null ? value : key;
    }

    /** MiniMessage string with {@code {placeholder}} substitution. */
    public String get(String key, Map<String, String> placeholders) {
        String template = raw(key);
        if (placeholders == null || placeholders.isEmpty()) {
            return template;
        }
        String out = template;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public String get(String key) {
        return get(key, null);
    }

    /** Percentage of keys translated for a locale (English is always 100%). */
    public static int coverage(Locale_ locale) {
        if (locale == null || locale == Locale_.EN) {
            return 100;
        }
        Map<String, String> overrides = OVERRIDES.get(locale);
        if (overrides == null || EN.isEmpty()) {
            return 0;
        }
        return (int) Math.round(100.0 * overrides.size() / EN.size());
    }

    public static int keyCount() {
        return EN.size();
    }

    /** All keys, for the admin translation coverage display. */
    public static Map<String, String> baseTable() {
        return Collections.unmodifiableMap(EN);
    }
}
