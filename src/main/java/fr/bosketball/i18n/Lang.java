package fr.bosketball.i18n;

import fr.bosketball.util.Palette;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Lang {

    /**
     * The four languages the catalogue ships.
     *
     * <p>Each used to carry a flag string, and all four were empty: every place that printed
     * {@code flag + " " + displayName} therefore showed two spaces ("Current:  Français"). The flag
     * is gone rather than filled in, because a regional-indicator pair has no glyph in the vanilla
     * font and would render as the letters "FR" on most clients.
     */
    public enum Locale_ {
        EN("en", "English"),
        FR("fr", "Français"),
        ES("es", "Español"),
        DE("de", "Deutsch");

        private final String code;
        private final String displayName;

        Locale_(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String code() {
            return code;
        }

        public String displayName() {
            return displayName;
        }

        public static Locale_ byCode(String raw) {
            if (raw == null) {
                return EN;
            }
            // Round 42: the client sends "fr_fr" / "en_US" / "es_mx" with an UNDERSCORE, and some
            // proxies send a three-letter tag ("fra"). The old code only split on '-' and therefore
            // answered EN to every French and Spanish client — the player's language was ignored.
            String norm = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            int cut = norm.indexOf('-');
            if (cut > 0) {
                norm = norm.substring(0, cut);
            }
            if (norm.isEmpty()) {
                return EN;
            }
            for (Locale_ l : values()) {
                if (l.code.equals(norm)) {
                    return l;
                }
            }
            for (Locale_ l : values()) {
                if (norm.startsWith(l.code) && norm.length() <= 3) {
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

        EN.put("generic.on", "<" + Palette.SUCCESS + ">ON</" + Palette.SUCCESS + ">");
        EN.put("generic.off", "<" + Palette.DANGER + ">OFF</" + Palette.DANGER + ">");
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

        EN.put("queue.joined", "You joined the <#F8FAFC>{mode}</#F8FAFC> queue ({queue}).");
        EN.put("queue.left", "You left the queue.");
        EN.put("queue.casual", "Casual");
        EN.put("queue.ranked", "Ranked");
        EN.put("queue.casual_hint", "Casual matches never change your ELO and pay fewer Coins.");
        EN.put("queue.estimate", "Estimated wait: {seconds}s");
        EN.put("queue.full", "That queue is full right now.");
        EN.put("queue.already_in", "You are already in a queue.");

        EN.put("match.found", "<" + Palette.SUCCESS + "><bold>MATCH FOUND!</bold></" + Palette.SUCCESS + "> <#94A3B8>Accept in {seconds}s.</#94A3B8>");
        EN.put("match.found_detail", "{mode} \u00b7 {players} players \u00b7 {arena}");
        EN.put("match.accept", "<" + Palette.SUCCESS + "><bold>[ ACCEPT ]</bold></" + Palette.SUCCESS + ">");
        EN.put("match.decline", "<" + Palette.DANGER + "><bold>[ DECLINE ]</bold></" + Palette.DANGER + ">");
        EN.put("match.accepted", "Match accepted — good luck!");
        EN.put("match.declined", "You declined the match. Queue penalty: {seconds}s.");
        EN.put("match.expired", "The match proposal expired because someone did not accept.");
        EN.put("match.dodge_penalty", "You cannot queue again for {seconds}s (dodge penalty).");
        EN.put("match.remake", "<" + Palette.GOLD + "><bold>REMAKE</bold></" + Palette.GOLD + "> — match canceled, no ELO lost.");
        EN.put("match.requeue", "Re-queue now?");

        EN.put("match.started", "Match started: {mode} on {arena}");
        EN.put("match.won", "<" + Palette.SUCCESS + "><bold>VICTORY</bold></" + Palette.SUCCESS + "> — {coins} Coins");
        EN.put("match.lost", "<" + Palette.DANGER + "><bold>DEFEAT</bold></" + Palette.DANGER + "> — better luck next time");
        EN.put("match.mvp", "<" + Palette.GOLD_SOFT + "><bold>MVP:</bold></" + Palette.GOLD_SOFT + "> {player}");
        EN.put("match.elo_delta", "ELO {sign}{delta}");
        EN.put("match.forfeit", "{player} forfeited the match.");

        EN.put("coins.received", "You received <" + Palette.GOLD_SOFT + ">{amount} Coins</" + Palette.GOLD_SOFT + ">.");
        EN.put("coins.spent", "You spent <" + Palette.GOLD_SOFT + ">{amount} Coins</" + Palette.GOLD_SOFT + ">.");
        EN.put("coins.balance", "Balance: <" + Palette.GOLD_SOFT + ">{amount} Coins</" + Palette.GOLD_SOFT + ">");

        EN.put("quests.title", "Quest Board");
        EN.put("quests.daily", "Daily");
        EN.put("quests.weekly", "Weekly");
        EN.put("quests.contract", "Contract");
        EN.put("quests.progress", "Progress: {current}/{target}");
        EN.put("quests.complete", "<" + Palette.SUCCESS + ">COMPLETE — claim your reward!</" + Palette.SUCCESS + ">");
        EN.put("quests.claimed", "<" + Palette.DIM + ">Already claimed</" + Palette.DIM + ">");
        EN.put("quests.claim_all", "Claim all");
        EN.put("quests.nothing_to_claim", "No completed quest to claim yet — keep playing!");
        EN.put("quests.reroll", "Reroll contract");
        EN.put("quests.expires_in", "Expires in {time}");

        EN.put("achievements.title", "Achievements");
        EN.put("achievements.unlocked", "Unlocked {count}/{total}");
        EN.put("achievements.locked", "Locked");
        EN.put("achievements.reward", "Reward: {coins} Coins");

        EN.put("tutorial.start", "<" + Palette.SUCCESS + "><bold>Tutorial</bold></" + Palette.SUCCESS + "> — step {step}/{total}");
        EN.put("tutorial.next", "Next step");
        EN.put("tutorial.skip", "Skip");
        EN.put("tutorial.pause", "Pause");
        EN.put("tutorial.finished", "<" + Palette.SUCCESS + "><bold>Tutorial complete!</bold></" + Palette.SUCCESS + "> +{coins} Coins");
        EN.put("tutorial.optional", "This tutorial is optional — you can restart it anytime with /tuto.");

        EN.put("crate.title", "Reward Crate");
        EN.put("crate.cost", "Cost: {coins}");
        EN.put("crate.opened", "You opened a reward crate!");
        EN.put("crate.reward_coins", "+{amount} Coins");
        EN.put("crate.reward_unlock", "Unlocked: {name}");
        EN.put("crate.daily_limit", "Daily limit reached — come back tomorrow.");
        EN.put("crate.cooldown", "The crate needs a moment to recharge ({seconds}s).");

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

        EN.put("chat.warning", "<" + Palette.DANGER + "><bold>⚠ Insults are not tolerated here.</bold></" + Palette.DANGER + "> Warning {count}/{max}.");
        EN.put("chat.warning_last", "<" + Palette.DANGER + "><bold>⚠ Final warning!</bold></" + Palette.DANGER + "> One more and you are banned.");
        EN.put("chat.muted", "You are muted for {time}.");
        EN.put("chat.ban_reason", "Insults repeated after {count} warnings — appeal on our Discord: {discord}");
        EN.put("chat.censored", "Your message was censored for insulting language.");
        EN.put("report.sent", "Report sent to the staff team. Thank you.");
        EN.put("report.usage", "Usage: /report <player> <reason>");
        EN.put("report.rate_limited", "You are reporting too fast — please wait {seconds}s.");
        EN.put("report.not_found", "Unknown player: {player}");

        EN.put("leaderboard.title", "{category} Leaderboard");
        EN.put("leaderboard.empty", "No data yet — play a match to appear here!");
        EN.put("leaderboard.place", "#{place} {player} — {value}");
        EN.put("leaderboard.updated", "Updated {seconds}s ago");

        EN.put("admin.only", "That requires operator permissions.");
        EN.put("admin.saved", "Saved.");
        EN.put("admin.reloaded", "Configuration reloaded.");
        EN.put("storage.backup", "Backup written: {file}");

        EN.put("gui.title.hub", "LOBBY");
        EN.put("gui.title.play", "PLAY MODES");
        EN.put("gui.title.shop", "SHOP");
        EN.put("gui.title.confirm", "CONFIRM PURCHASE");
        EN.put("gui.title.control", "SERVER CONTROL");
        EN.put("gui.title.studio", "ARENA STUDIO");
        EN.put("gui.title.party", "PARTY");
        EN.put("gui.title.locker", "LOCKER ROOM");
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

        Map<String, String> fr = new LinkedHashMap<>();
        fr.put("generic.on", "<" + Palette.SUCCESS + ">ACTIVÉ</" + Palette.SUCCESS + ">");
        fr.put("generic.off", "<" + Palette.DANGER + ">DÉSACTIVÉ</" + Palette.DANGER + ">");
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

        fr.put("queue.joined", "Vous avez rejoint la file <#F8FAFC>{mode}</#F8FAFC> ({queue}).");
        fr.put("queue.left", "Vous avez quitté la file d'attente.");
        fr.put("queue.casual", "Détente");
        fr.put("queue.ranked", "Classé");
        fr.put("queue.casual_hint", "Les matchs détente ne changent jamais votre ELO et rapportent moins de pièces.");
        fr.put("queue.estimate", "Attente estimée : {seconds}s");
        fr.put("queue.full", "Cette file est complète pour le moment.");
        fr.put("queue.already_in", "Vous êtes déjà dans une file d'attente.");

        fr.put("match.found", "<" + Palette.SUCCESS + "><bold>MATCH TROUVÉ !</bold></" + Palette.SUCCESS + "> <#94A3B8>Acceptez en {seconds}s.</#94A3B8>");
        fr.put("match.found_detail", "{mode} \u00b7 {players} joueurs \u00b7 {arena}");
        fr.put("match.accept", "<" + Palette.SUCCESS + "><bold>[ ACCEPTER ]</bold></" + Palette.SUCCESS + ">");
        fr.put("match.decline", "<" + Palette.DANGER + "><bold>[ REFUSER ]</bold></" + Palette.DANGER + ">");
        fr.put("match.accepted", "Match accepté — bonne chance !");
        fr.put("match.declined", "Vous avez refusé le match. Pénalité de file : {seconds}s.");
        fr.put("match.expired", "La proposition de match a expiré car quelqu'un n'a pas accepté.");
        fr.put("match.dodge_penalty", "Vous ne pouvez pas relancer une file avant {seconds}s (pénalité d'esquive).");
        fr.put("match.remake", "<" + Palette.GOLD + "><bold>REMAKE</bold></" + Palette.GOLD + "> — match annulé, aucun ELO perdu.");
        fr.put("match.requeue", "Relancer la recherche ?");

        fr.put("match.started", "Match lancé : {mode} sur {arena}");
        fr.put("match.won", "<" + Palette.SUCCESS + "><bold>VICTOIRE</bold></" + Palette.SUCCESS + "> — {coins} pièces");
        fr.put("match.lost", "<" + Palette.DANGER + "><bold>DÉFAITE</bold></" + Palette.DANGER + "> — ce sera pour la prochaine fois");
        fr.put("match.mvp", "<" + Palette.GOLD_SOFT + "><bold>MVP :</bold></" + Palette.GOLD_SOFT + "> {player}");
        fr.put("match.elo_delta", "ELO {sign}{delta}");
        fr.put("match.forfeit", "{player} a abandonné le match.");

        fr.put("coins.received", "Vous avez reçu <" + Palette.GOLD_SOFT + ">{amount} pièces</" + Palette.GOLD_SOFT + ">.");
        fr.put("coins.spent", "Vous avez dépensé <" + Palette.GOLD_SOFT + ">{amount} pièces</" + Palette.GOLD_SOFT + ">.");
        fr.put("coins.balance", "Solde : <" + Palette.GOLD_SOFT + ">{amount} pièces</" + Palette.GOLD_SOFT + ">");

        fr.put("quests.title", "Tableau des quêtes");
        fr.put("quests.daily", "Quotidienne");
        fr.put("quests.weekly", "Hebdomadaire");
        fr.put("quests.contract", "Contrat");
        fr.put("quests.progress", "Progression : {current}/{target}");
        fr.put("quests.complete", "<" + Palette.SUCCESS + ">TERMINÉE — réclamez votre récompense !</" + Palette.SUCCESS + ">");
        fr.put("quests.claimed", "<" + Palette.DIM + ">Déjà réclamée</" + Palette.DIM + ">");
        fr.put("quests.claim_all", "Tout réclamer");
        fr.put("quests.nothing_to_claim", "Aucune quête terminée à réclamer — continuez à jouer !");
        fr.put("quests.reroll", "Relancer le contrat");
        fr.put("quests.expires_in", "Expire dans {time}");

        fr.put("achievements.title", "Hauts faits");
        fr.put("achievements.unlocked", "Débloqués {count}/{total}");
        fr.put("achievements.locked", "Verrouillé");
        fr.put("achievements.reward", "Récompense : {coins} pièces");

        fr.put("tutorial.start", "<" + Palette.SUCCESS + "><bold>Tutoriel</bold></" + Palette.SUCCESS + "> — étape {step}/{total}");
        fr.put("tutorial.next", "Étape suivante");
        fr.put("tutorial.skip", "Passer");
        fr.put("tutorial.pause", "Pause");
        fr.put("tutorial.finished", "<" + Palette.SUCCESS + "><bold>Tutoriel terminé !</bold></" + Palette.SUCCESS + "> +{coins} pièces");
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
        fr.put("social.status_locker", "Vestiaire");
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

        fr.put("chat.warning", "<" + Palette.DANGER + "><bold>⚠ Les insultes ne sont pas tolérées ici.</bold></" + Palette.DANGER + "> Avertissement {count}/{max}.");
        fr.put("chat.warning_last", "<" + Palette.DANGER + "><bold>⚠ Dernier avertissement !</bold></" + Palette.DANGER + "> Encore un et c'est le bannissement.");
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
        fr.put("gui.title.confirm", "CONFIRMER L'ACHAT");
        fr.put("gui.title.control", "CONTRÔLE SERVEUR");
        fr.put("gui.title.studio", "STUDIO D'ARÈNE");
        fr.put("gui.title.party", "ÉQUIPE");
        fr.put("gui.title.locker", "VESTIAIRE");
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

        Map<String, String> es = new LinkedHashMap<>();
        es.put("generic.yes", "Sí");
        es.put("generic.no", "No");
        es.put("generic.back", "Volver");
        es.put("generic.close", "Cerrar");
        es.put("generic.coins", "{amount} monedas");
        es.put("generic.player_only", "Este comando es solo para jugadores.");
        es.put("generic.no_permission", "No tienes permiso para hacer eso.");
        es.put("generic.not_in_match", "No estás en una partida ahora mismo.");
        es.put("queue.joined", "Te has unido a la cola <#F8FAFC>{mode}</#F8FAFC> ({queue}).");
        es.put("queue.left", "Has salido de la cola.");
        es.put("queue.casual", "Casual");
        es.put("queue.ranked", "Clasificatoria");
        es.put("queue.casual_hint", "Las partidas casuales no cambian tu ELO y dan menos monedas.");
        es.put("match.found", "<" + Palette.SUCCESS + "><bold>¡PARTIDA ENCONTRADA!</bold></" + Palette.SUCCESS + "> <#94A3B8>Acepta en {seconds}s.</#94A3B8>");
        es.put("match.accept", "<" + Palette.SUCCESS + "><bold>[ ACEPTAR ]</bold></" + Palette.SUCCESS + ">");
        es.put("match.decline", "<" + Palette.DANGER + "><bold>[ RECHAZAR ]</bold></" + Palette.DANGER + ">");
        es.put("match.accepted", "Partida aceptada — ¡buena suerte!");
        es.put("match.declined", "Rechazaste la partida. Penalización: {seconds}s.");
        es.put("match.won", "<" + Palette.SUCCESS + "><bold>VICTORIA</bold></" + Palette.SUCCESS + "> — {coins} monedas");
        es.put("match.lost", "<" + Palette.DANGER + "><bold>DERROTA</bold></" + Palette.DANGER + "> — suerte la próxima vez");
        es.put("match.mvp", "<" + Palette.GOLD_SOFT + "><bold>MVP:</bold></" + Palette.GOLD_SOFT + "> {player}");
        es.put("quests.title", "Tablero de misiones");
        es.put("quests.daily", "Diaria");
        es.put("quests.weekly", "Semanal");
        es.put("quests.progress", "Progreso: {current}/{target}");
        es.put("quests.claim_all", "Reclamar todo");
        es.put("achievements.title", "Logros");
        es.put("tutorial.start", "<" + Palette.SUCCESS + "><bold>Tutorial</bold></" + Palette.SUCCESS + "> — paso {step}/{total}");
        es.put("crate.title", "Caja de recompensa");
        es.put("friends.title", "Amigos");
        es.put("guild.title", "Gremio");
        es.put("chat.warning", "<" + Palette.DANGER + "><bold>⚠ Los insultos no están permitidos.</bold></" + Palette.DANGER + "> Advertencia {count}/{max}.");
        es.put("report.sent", "Reporte enviado al equipo. ¡Gracias!");
        es.put("leaderboard.title", "Clasificación {category}");
        es.put("leaderboard.empty", "Sin datos todavía — ¡juega una partida!");

        es.put("gui.title.hub", "LOBBY");
        es.put("gui.title.play", "MODOS DE JUEGO");
        es.put("gui.title.shop", "TIENDA");
        es.put("gui.title.confirm", "CONFIRMAR COMPRA");
        es.put("gui.title.control", "CONTROL DEL SERVIDOR");
        es.put("gui.title.studio", "ESTUDIO DE ARENA");
        es.put("gui.title.party", "GRUPO");
        es.put("gui.title.locker", "VESTUARIO");
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
        es.put("generic.on", "<" + Palette.SUCCESS + ">ACTIVADO</" + Palette.SUCCESS + ">");
        es.put("generic.off", "<" + Palette.DANGER + ">DESACTIVADO</" + Palette.DANGER + ">");
        es.put("generic.none", "Ninguno");
        es.put("generic.elo", "{amount} ELO");
        es.put("generic.loading", "Cargando…");
        es.put("generic.unknown", "Desconocido");
        es.put("generic.not_enough_coins", "Necesitas {amount} monedas para eso.");
        es.put("queue.estimate", "Espera estimada: {seconds}s");
        es.put("queue.full", "Esa cola está llena ahora mismo.");
        es.put("queue.already_in", "Ya estás en una cola.");
        es.put("match.found_detail", "{mode} \u00b7 {players} jugadores \u00b7 {arena}");
        es.put("match.expired", "La propuesta de partida expiró porque alguien no aceptó.");
        es.put("match.dodge_penalty", "No puedes volver a la cola durante {seconds}s (penalización por abandonar).");
        es.put("match.remake", "<" + Palette.GOLD + "><bold>REPETIR</bold></" + Palette.GOLD + "> — partida cancelada, sin pérdida de ELO.");
        es.put("match.requeue", "¿Volver a la cola ahora?");
        es.put("match.started", "Partida iniciada: {mode} en {arena}");
        es.put("match.elo_delta", "ELO {sign}{delta}");
        es.put("match.forfeit", "{player} abandonó la partida.");
        es.put("coins.received", "Has recibido <" + Palette.GOLD_SOFT + ">{amount} monedas</" + Palette.GOLD_SOFT + ">.");
        es.put("coins.spent", "Has gastado <" + Palette.GOLD_SOFT + ">{amount} monedas</" + Palette.GOLD_SOFT + ">.");
        es.put("coins.balance", "Saldo: <" + Palette.GOLD_SOFT + ">{amount} monedas</" + Palette.GOLD_SOFT + ">");
        es.put("quests.contract", "Contrato");
        es.put("quests.complete", "<" + Palette.SUCCESS + ">COMPLETADA — ¡reclama tu recompensa!</" + Palette.SUCCESS + ">");
        es.put("quests.claimed", "<" + Palette.DIM + ">Ya reclamada</" + Palette.DIM + ">");
        es.put("quests.nothing_to_claim", "Aún no hay ninguna misión completada — ¡sigue jugando!");
        es.put("quests.reroll", "Cambiar contrato");
        es.put("quests.expires_in", "Expira en {time}");
        es.put("achievements.unlocked", "Desbloqueados {count}/{total}");
        es.put("achievements.locked", "Bloqueado");
        es.put("achievements.reward", "Recompensa: {coins} monedas");
        es.put("tutorial.next", "Siguiente paso");
        es.put("tutorial.skip", "Saltar");
        es.put("tutorial.pause", "Pausar");
        es.put("tutorial.finished", "<" + Palette.SUCCESS + "><bold>¡Tutorial completado!</bold></" + Palette.SUCCESS + "> +{coins} monedas");
        es.put("tutorial.optional", "Este tutorial es opcional — puedes reiniciarlo cuando quieras con /tuto.");
        es.put("crate.cost", "Coste: {coins}");
        es.put("crate.opened", "¡Has abierto un cofre de recompensas!");
        es.put("crate.reward_coins", "+{amount} monedas");
        es.put("crate.reward_unlock", "Desbloqueado: {name}");
        es.put("crate.daily_limit", "Límite diario alcanzado — vuelve mañana.");
        es.put("crate.cooldown", "El cofre necesita recargarse ({seconds}s).");
        es.put("friends.added", "{player} ahora es tu amigo.");
        es.put("friends.removed", "{player} fue eliminado de tus amigos.");
        es.put("friends.request_sent", "Solicitud de amistad enviada a {player}.");
        es.put("friends.online", "En línea: {count}");
        es.put("social.status_lobby", "En el vestíbulo");
        es.put("social.status_queue", "En cola");
        es.put("social.status_match", "En la cancha");
        es.put("social.status_spectating", "Espectando");
        es.put("social.status_locker", "Vestuario");
        es.put("social.status_tutorial", "Tutorial");
        es.put("social.status_afk", "Ausente");
        es.put("social.status_offline", "Desconectado");
        es.put("party.created", "Grupo creado — invita a tus amigos con /party invite <player>.");
        es.put("party.joined", "{player} se unió a tu grupo.");
        es.put("party.left", "{player} abandonó el grupo.");
        es.put("party.chat_on", "Chat de grupo activado — solo tu grupo ve tus mensajes.");
        es.put("party.chat_off", "Chat de grupo desactivado.");
        es.put("guild.created", "¡Gremio {name} creado!");
        es.put("guild.joined", "Te has unido a {name}.");
        es.put("guild.left", "Has abandonado el gremio.");
        es.put("guild.invited", "Invitación enviada a {player}.");
        es.put("guild.disbanded", "Gremio disuelto.");
        es.put("guild.promoted", "{player} ahora es {role}.");
        es.put("guild.no_guild", "No estás en ningún gremio.");
        es.put("guild.elo", "ELO del gremio: {amount}");
        es.put("guild.rank", "Puesto #{place}");
        es.put("chat.warning_last", "<" + Palette.DANGER + "><bold>⚠ ¡Aviso final!</bold></" + Palette.DANGER + "> Uno más y serás expulsado.");
        es.put("chat.muted", "Estás silenciado durante {time}.");
        es.put("chat.ban_reason", "Insultos repetidos tras {count} avisos — apela en nuestro Discord: {discord}");
        es.put("chat.censored", "Tu mensaje fue censurado por lenguaje insultante.");
        es.put("report.usage", "Uso: /report <player> <reason>");
        es.put("report.rate_limited", "Estás reportando demasiado rápido — espera {seconds}s.");
        es.put("report.not_found", "Jugador desconocido: {player}");
        es.put("leaderboard.place", "#{place} {player} — {value}");
        es.put("leaderboard.updated", "Actualizado hace {seconds}s");
        es.put("admin.only", "Eso requiere permisos de operador.");
        es.put("admin.saved", "Guardado.");
        es.put("admin.reloaded", "Configuración recargada.");
        es.put("storage.backup", "Copia de seguridad escrita: {file}");
        es.put("gui.title.bots", "ENTRENO E IA");
        es.put("gui.title.reports", "REPORTES");
        es.put("gui.title.warnings", "ADVERTENCIAS");
        es.put("gui.title.history", "HISTORIAL");
        es.put("gui.title.ranks", "GUÍA DE RANGOS");
        es.put("gui.title.admin", "CONTROL DEL SERVIDOR");
        es.put("gui.title.spectate", "ESPECTADOR");
        es.put("gui.language", "Idioma");
        es.put("gui.language.hint", "Haz clic para cambiar: {locale}");

        Map<String, String> de = new LinkedHashMap<>();
        de.put("generic.yes", "Ja");
        de.put("generic.no", "Nein");
        de.put("generic.back", "Zurück");
        de.put("generic.close", "Schließen");
        de.put("generic.coins", "{amount} Münzen");
        de.put("generic.player_only", "Dieser Befehl ist nur für Spieler.");
        de.put("generic.no_permission", "Dafür hast du keine Berechtigung.");
        de.put("queue.joined", "Du bist der <#F8FAFC>{mode}</#F8FAFC>-Warteschlange beigetreten ({queue}).");
        de.put("queue.left", "Du hast die Warteschlange verlassen.");
        de.put("queue.casual", "Casual");
        de.put("queue.ranked", "Rangliste");
        de.put("queue.casual_hint", "Casual-Spiele ändern deine ELO nicht und geben weniger Münzen.");
        de.put("match.found", "<" + Palette.SUCCESS + "><bold>SPIEL GEFUNDEN!</bold></" + Palette.SUCCESS + "> <#94A3B8>Akzeptiere in {seconds}s.</#94A3B8>");
        de.put("match.accept", "<" + Palette.SUCCESS + "><bold>[ ANNEHMEN ]</bold></" + Palette.SUCCESS + ">");
        de.put("match.decline", "<" + Palette.DANGER + "><bold>[ ABLEHNEN ]</bold></" + Palette.DANGER + ">");
        de.put("match.won", "<" + Palette.SUCCESS + "><bold>SIEG</bold></" + Palette.SUCCESS + "> — {coins} Münzen");
        de.put("match.lost", "<" + Palette.DANGER + "><bold>NIEDERLAGE</bold></" + Palette.DANGER + "> — nächstes Mal klappt es");
        de.put("match.mvp", "<" + Palette.GOLD_SOFT + "><bold>MVP:</bold></" + Palette.GOLD_SOFT + "> {player}");
        de.put("quests.title", "Questtafel");
        de.put("quests.daily", "Täglich");
        de.put("quests.weekly", "Wöchentlich");
        de.put("quests.claim_all", "Alles abholen");
        de.put("achievements.title", "Erfolge");
        de.put("tutorial.start", "<" + Palette.SUCCESS + "><bold>Tutorial</bold></" + Palette.SUCCESS + "> — Schritt {step}/{total}");
        de.put("crate.title", "Belohnungskiste");
        de.put("friends.title", "Freunde");
        de.put("guild.title", "Gilde");
        de.put("chat.warning", "<" + Palette.DANGER + "><bold>⚠ Beleidigungen sind hier nicht erlaubt.</bold></" + Palette.DANGER + "> Verwarnung {count}/{max}.");
        de.put("report.sent", "Meldung an das Team gesendet. Danke!");
        de.put("leaderboard.title", "Rangliste {category}");
        de.put("leaderboard.empty", "Noch keine Daten — spiele ein Spiel!");

        de.put("gui.title.hub", "LOBBY");
        de.put("gui.title.play", "SPIELMODI");
        de.put("gui.title.shop", "SHOP");
        de.put("gui.title.confirm", "KAUF BESTÄTIGEN");
        de.put("gui.title.control", "SERVERSTEUERUNG");
        de.put("gui.title.studio", "ARENA-STUDIO");
        de.put("gui.title.party", "GRUPPE");
        de.put("gui.title.locker", "UMKLEIDEKABINE");
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
        de.put("generic.not_in_match", "Du bist gerade in keinem Spiel.");
        de.put("match.accepted", "Spiel angenommen — viel Glück!");
        de.put("match.declined", "Du hast das Spiel abgelehnt. Warteschlangen-Strafe: {seconds}s.");
        de.put("quests.progress", "Fortschritt: {current}/{target}");
        de.put("generic.on", "<" + Palette.SUCCESS + ">AN</" + Palette.SUCCESS + ">");
        de.put("generic.off", "<" + Palette.DANGER + ">AUS</" + Palette.DANGER + ">");
        de.put("generic.none", "Keine");
        de.put("generic.elo", "{amount} ELO");
        de.put("generic.loading", "Lädt…");
        de.put("generic.unknown", "Unbekannt");
        de.put("generic.not_enough_coins", "Dafür brauchst du {amount} Münzen.");
        de.put("queue.estimate", "Geschätzte Wartezeit: {seconds}s");
        de.put("queue.full", "Diese Warteschlange ist gerade voll.");
        de.put("queue.already_in", "Du bist bereits in einer Warteschlange.");
        de.put("match.found_detail", "{mode} \u00b7 {players} Spieler \u00b7 {arena}");
        de.put("match.expired", "Der Spielvorschlag ist abgelaufen, weil jemand nicht angenommen hat.");
        de.put("match.dodge_penalty", "Du kannst {seconds}s lang nicht erneut suchen (Abbrecher-Strafe).");
        de.put("match.remake", "<" + Palette.GOLD + "><bold>NEUSTART</bold></" + Palette.GOLD + "> — Spiel abgebrochen, kein ELO verloren.");
        de.put("match.requeue", "Jetzt erneut suchen?");
        de.put("match.started", "Spiel gestartet: {mode} auf {arena}");
        de.put("match.elo_delta", "ELO {sign}{delta}");
        de.put("match.forfeit", "{player} hat das Spiel aufgegeben.");
        de.put("coins.received", "Du hast <" + Palette.GOLD_SOFT + ">{amount} Münzen</" + Palette.GOLD_SOFT + "> erhalten.");
        de.put("coins.spent", "Du hast <" + Palette.GOLD_SOFT + ">{amount} Münzen</" + Palette.GOLD_SOFT + "> ausgegeben.");
        de.put("coins.balance", "Guthaben: <" + Palette.GOLD_SOFT + ">{amount} Münzen</" + Palette.GOLD_SOFT + ">");
        de.put("quests.contract", "Auftrag");
        de.put("quests.complete", "<" + Palette.SUCCESS + ">ERLEDIGT — Belohnung abholen!</" + Palette.SUCCESS + ">");
        de.put("quests.claimed", "<" + Palette.DIM + ">Bereits abgeholt</" + Palette.DIM + ">");
        de.put("quests.nothing_to_claim", "Noch keine Quest abgeschlossen — spiel weiter!");
        de.put("quests.reroll", "Auftrag tauschen");
        de.put("quests.expires_in", "Läuft ab in {time}");
        de.put("achievements.unlocked", "Freigeschaltet {count}/{total}");
        de.put("achievements.locked", "Gesperrt");
        de.put("achievements.reward", "Belohnung: {coins} Münzen");
        de.put("tutorial.next", "Nächster Schritt");
        de.put("tutorial.skip", "Überspringen");
        de.put("tutorial.pause", "Pause");
        de.put("tutorial.finished", "<" + Palette.SUCCESS + "><bold>Tutorial abgeschlossen!</bold></" + Palette.SUCCESS + "> +{coins} Münzen");
        de.put("tutorial.optional", "Dieses Tutorial ist optional — du kannst es jederzeit mit /tuto neu starten.");
        de.put("crate.cost", "Kosten: {coins}");
        de.put("crate.opened", "Du hast eine Belohnungskiste geöffnet!");
        de.put("crate.reward_coins", "+{amount} Münzen");
        de.put("crate.reward_unlock", "Freigeschaltet: {name}");
        de.put("crate.daily_limit", "Tageslimit erreicht — komm morgen wieder.");
        de.put("crate.cooldown", "Die Kiste muss sich kurz aufladen ({seconds}s).");
        de.put("friends.added", "{player} ist jetzt dein Freund.");
        de.put("friends.removed", "{player} wurde aus deinen Freunden entfernt.");
        de.put("friends.request_sent", "Freundschaftsanfrage an {player} gesendet.");
        de.put("friends.online", "Online: {count}");
        de.put("social.status_lobby", "In der Lobby");
        de.put("social.status_queue", "In der Warteschlange");
        de.put("social.status_match", "Auf dem Court");
        de.put("social.status_spectating", "Zuschauer");
        de.put("social.status_locker", "Umkleide");
        de.put("social.status_tutorial", "Tutorial");
        de.put("social.status_afk", "Abwesend");
        de.put("social.status_offline", "Offline");
        de.put("party.created", "Gruppe erstellt — lade Freunde mit /party invite <player> ein.");
        de.put("party.joined", "{player} ist deiner Gruppe beigetreten.");
        de.put("party.left", "{player} hat die Gruppe verlassen.");
        de.put("party.chat_on", "Gruppenchat aktiviert — nur deine Gruppe sieht deine Nachrichten.");
        de.put("party.chat_off", "Gruppenchat deaktiviert.");
        de.put("guild.created", "Gilde {name} erstellt!");
        de.put("guild.joined", "Du bist {name} beigetreten.");
        de.put("guild.left", "Du hast die Gilde verlassen.");
        de.put("guild.invited", "Einladung an {player} gesendet.");
        de.put("guild.disbanded", "Gilde aufgelöst.");
        de.put("guild.promoted", "{player} ist jetzt {role}.");
        de.put("guild.no_guild", "Du bist in keiner Gilde.");
        de.put("guild.elo", "Gilden-ELO: {amount}");
        de.put("guild.rank", "Platz #{place}");
        de.put("chat.warning_last", "<" + Palette.DANGER + "><bold>⚠ Letzte Warnung!</bold></" + Palette.DANGER + "> Noch eine und du wirst gebannt.");
        de.put("chat.muted", "Du bist für {time} stummgeschaltet.");
        de.put("chat.ban_reason", "Wiederholte Beleidigungen nach {count} Warnungen — Einspruch auf unserem Discord: {discord}");
        de.put("chat.censored", "Deine Nachricht wurde wegen beleidigender Sprache zensiert.");
        de.put("report.usage", "Benutzung: /report <player> <reason>");
        de.put("report.rate_limited", "Du meldest zu schnell — bitte warte {seconds}s.");
        de.put("report.not_found", "Unbekannter Spieler: {player}");
        de.put("leaderboard.place", "#{place} {player} — {value}");
        de.put("leaderboard.updated", "Vor {seconds}s aktualisiert");
        de.put("admin.only", "Dafür sind Operator-Rechte nötig.");
        de.put("admin.saved", "Gespeichert.");
        de.put("admin.reloaded", "Konfiguration neu geladen.");
        de.put("storage.backup", "Backup geschrieben: {file}");
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

    /**
     * One immutable instance per locale, built after the tables above.
     *
     * <p>Round 42: {@code Lang.of(...)} used to copy the whole English map — several hundred
     * entries — on <b>every single message</b>. A busy chat produced hundreds of thousands of map
     * entries per minute for nothing. The resolved maps never change at runtime, so they are built
     * once and shared; {@code Lang} is immutable from then on.
     */
    private static final Map<Locale_, Lang> INSTANCES = buildInstances();

    private static Map<Locale_, Lang> buildInstances() {
        Map<Locale_, Lang> out = new java.util.EnumMap<>(Locale_.class);
        for (Locale_ locale : Locale_.values()) {
            out.put(locale, new Lang(locale));
        }
        return Collections.unmodifiableMap(out);
    }

    private static final java.util.regex.Pattern PLACEHOLDER =
        java.util.regex.Pattern.compile("\\{([A-Za-z0-9_.-]+)}");

    private final Map<String, String> resolved;
    private final Locale_ locale;

    private Lang(Locale_ locale) {
        this.locale = locale;
        Map<String, String> merged = new LinkedHashMap<>(EN.size() + 32);
        merged.putAll(EN);
        Map<String, String> overrides = OVERRIDES.get(locale);
        if (overrides != null) {
            merged.putAll(overrides);
        }
        this.resolved = Collections.unmodifiableMap(merged);
    }

    public static Lang of(String localeCode) {
        return of(Locale_.byCode(localeCode));
    }

    public static Lang of(Locale_ locale) {
        return INSTANCES.get(locale == null ? Locale_.EN : locale);
    }

    public Locale_ locale() {
        return locale;
    }

    public String raw(String key) {
        String value = resolved.get(key);
        return value != null ? value : key;
    }

    public String get(String key, Map<String, String> placeholders) {
        String template = raw(key);
        if (placeholders == null || placeholders.isEmpty() || template.indexOf('{') < 0) {
            return template;
        }
        // Single pass over the TEMPLATE only. The previous loop did a string replace per entry, in
        // map order, so a value that itself contained "{something}" was re-expanded by the next
        // entry: a player or guild named "{amount}" could rewrite the message around it. Values are
        // now inserted verbatim and never scanned again.
        java.util.regex.Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 16);
        while (matcher.find()) {
            String value = placeholders.get(matcher.group(1));
            matcher.appendReplacement(out,
                java.util.regex.Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public String get(String key) {
        return get(key, null);
    }

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

}
