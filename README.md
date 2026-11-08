# BosketBall — Commandes

Référence complète des commandes du plugin **BosketBall** (Paper 26.2, API `26.2`).
En console le `/` initial est facultatif : `bb perf` = `/bb perf`.

- `<...>` = argument obligatoire, `[...]` = facultatif, `a|b` = valeurs possibles.
- Commande racine : `/bosketball` (alias `/bb`) — toutes les sous-commandes des sections 4 à 7.
- `bosketball.play` (défaut) · `bosketball.staff` · `bosketball.map` · `bosketball.build` · `bosketball.admin`.

---

## 1. Commandes joueur

| Commande | Alias | Effet |
|---|---|---|
| `/help` | `?` | Carte d'aide interactive (jeu, boutique, social, opérateur). |
| `/play [1v1\|2v2\|3v3\|5v5\|3pt\|practice]` | — | File de match classée ou aperçu des modes. |
| `/practice` | — | Court d'entraînement solo instantané. |
| `/locker` | — | Locker cosmétiques (tenues, ballons, dunks, tags). |
| `/leave` | — | Quitter match, file ou caméra spectateur. |
| `/spawn` | `hub`, `lobby` | Retour au spawn du lobby. |
| `/shop [all\|balls\|dunks\|tags]` | — | Boutique : ballons, dunks signature, tags. |
| `/ranks` | — | Échelle ELO, badges #1/#2/#3, contrôles du court. |
| `/stats [joueur]` | `profil`, `profile` | Ta fiche : rang, record, carrière, adresse. |
| `/friend [add\|remove\|decline] [joueur]` | `f`, `friends` | Liste d'amis. |
| `/party [nom\|invite\|join\|leave] [valeur]` | `p` | Groupe : nom d'équipe, invitations. |
| `/duel <joueur>` | — | Défier un joueur en 1v1. |
| `/msg <joueur> <message>` | `tell`, `w` | Message privé. |
| `/reply <message>` | `r` | Répondre au dernier message privé. |
| `/gg` | — | « GG » de fin de match (+coins). |
| `/discord` | — | Lien du Discord officiel. |
| `/pack` | — | Renvoyer le resource pack officiel. |
| `/accept` | — | Accepter un duel / une invitation. |
| `/decline` | — | Refuser un duel / une invitation. |
| `/pc` | `pchat` | Chat de groupe. |
| `/tuto [menu\|start\|restart\|next\|skip\|stop\|show\|finish\|diag]` | `tutorial` | Tutoriel guidé (`menu\|gui` ouvre le menu, `start\|begin\|go` reprend, `restart\|replay\|reset` recommence, `next\|continue\|ok`, `show\|again`, `skip`, `stop\|pause`, `finish\|done` (retour au spawn), `diag\|diagnose\|debug`). |
| `/quests` | `quest`, `objectives` | Objectifs quotidiens. |
| `/achievements` | `ach` | Succès débloqués. |
| `/season [top\|claim\|days]` | `saison`, `rankedseason` | Saison classée : classement, récompenses, durée. |
| `/report <joueur> <raison>` | — | Signaler un joueur. |
| `/crate [open]` | — | Ouvrir une caisse de récompense. |
| `/mail` | `inbox`, `mailbox` | Boîte aux lettres (demandes hors ligne). |
| `/guild <create\|invite\|join\|decline\|leave\|info>` | `team`, `g`, `gc` | Guilde (`create\|new`, `decline\|deny`). |
| `/ignore <joueur>` | — | Masquer un joueur dans le chat. |
| `/afk` | — | Se déclarer absent. |
| `/status` | — | État du serveur (TPS, joueurs, files). |

## 1bis. Contrôles en jeu

| Action | Commande / touche | Détail |
|---|---|---|
| Tir en suspension | Maintenir **clic droit** (ballon en main) | La barre monte en ~1 s, relâcher pour tirer. La charge est pilotée par le plugin : elle ne retombe plus toute seule si la touche reste enfoncée. |
| Passe / alley-oop | Touche **Q** ou clic droit sur l'emplacement 2 | Viser le cercle pour lober. |
| Dash directionnel | **Maj** ou clic droit sur l'emplacement 3 | Le dash suit la direction **WASD** ; à l'arrêt il suit le réticule. |
| Dunk | Clic droit sur l'emplacement 4, dans la raquette | Viser le cercle : **un seul clic** suffit (plus de zone verte), recharge **20 s**. |
| Défense / interception | **Clic gauche** | — |
| Quitter la file ou le match | `/leave`, `/quit` ou le bouton `[✖ ʟᴇᴀᴠᴇ]` du chat | — |
| Profil / ami / duel | Clic droit sur un joueur (ou sur son pseudo flottant) dans le lobby | Ouvre sa fiche : ajout en ami, duel 1v1, invitation de groupe. |

> **Classement (Round 27)** : une victoire rapporte **+15 ELO**, une défaite coûte **−5 ELO** —
> **aussi pendant les placements** (le multiplicateur ×2 des 5 premiers matchs a été supprimé) —
> et un match nul (score identique) ne change **rien** pour personne au lieu d'offrir la victoire
> à IGNIS. Une baisse de rang est désormais annoncée, `/giveelo set` ne peut plus descendre sous
> 100 ELO, et les classements (`/ranks`, hall of fame, hologrammes) ne sont plus figés : ils sont
> recalculés à la lecture. Les badges #1/#2/#3 suivent l'ELO réel.
>
> **Tab list (Round 28)** : le cadre doré n'a plus 4 tailles « à peu près » mais **une taille par
> nombre de joueurs** (139 px pour 1 joueur, +9 px par joueur, 310 px pour 20). La bande vide qui
> restait sous le pied de page a disparu, le logo remonte et le bas du cadre colle au contenu.
>
> **Écran de fermeture** : quand le serveur s'arrête (stop / restart), l'écran rouge « Server closed »
> est remplacé par un écran BosketBall (titre en dégradé, petit-caps, tes stats sauvegardées, lien
> Discord et site). L'écran de maintenance utilise le même gabarit.
>
> **Staff** : *tous* les messages privés (`/msg`), de groupe (`/pc`), de guilde et du canal
> `[MATCH]` sont recopiés aux staffs/OP avec le préfixe `ꜱᴘʏ` — sans doublon pour un staff déjà
> membre du canal. Chaque entrée en file est annoncée dans le chat avec un bouton `[ᴊᴏɪɴ]`.
> En match classé, un panier dans son propre panier donne les points à l'adversaire (règle
> officielle) et le chat l'indique explicitement ; en entraînement, le panier compte toujours
> pour le joueur qui l'a mis.

## 2. Commandes staff

| Commande | Alias | Effet |
|---|---|---|
| `/reports` | — | File des signalements. |
| `/warn <joueur> [raison]` | — | Avertir un joueur. |
| `/unwarn <joueur> [all]` | — | Retirer un (ou tous les) avertissement(s). |
| `/warnings` | `warns` | Avertissements d'un joueur. |
| `/giveelo <joueur> <montant>` | — | Ajouter des points ELO. |
| `/backup` | — | Sauvegarde immédiate des données. |

## 3. Commandes administration / build

| Commande | Alias | Effet |
|---|---|---|
| `/build [joueur]` | — | Mode build (vol, créatif, protection). |
| `/setspawn` | — | Définir le spawn du lobby. |
| `/perm list` | `groups` | Groupes, parents, nombre de nœuds et de membres. |
| `/perm nodes [filtre]` | — | Tous les nœuds BosketBall et leur défaut. |
| `/perm check <joueur> <nœud>` | `why` | Explique pourquoi un nœud est accordé ou refusé. |
| `/perm user <joueur> <info\|nodes\|group\|add\|remove> ...` | — | Groupe principal et nœuds personnels. |
| `/perm group <groupe> <create\|info\|add\|remove\|inherit\|users\|delete> ...` | — | Gestion complète des groupes. |
| `/perm reload` | — | Recharge `permissions.yml`. |

---

## 4. `/bb` — jeu et progression

| Commande | Alias | Effet |
|---|---|---|
| `/bb help` | — | Carte d'aide. |
| `/bb play [mode]` | `join` | Rejoindre la file d'un mode. |
| `/bb practice` | — | Court d'entraînement solo. |
| `/bb leave` | `quit` | Quitter le match courant. |
| `/bb accept` | `ready` | Accepter / se déclarer prêt. |
| `/bb decline` | `dodge` | Refuser. |
| `/bb duel <joueur>` | — | Défi 1v1. |
| `/bb 3pt` | `contest` | Concours à 3 points. |
| `/bb defender` | `sparring` | Défenseur IA d'entraînement. |
| `/bb bot <easy\|normal\|pro\|fill\|add\|off>` | `bots`, `ai` | Bots de remplissage. |
| `/bb locker` | `cosmetics` | Cosmétiques / locker. |
| `/bb skin <ballon>` | — | Équiper un ballon. |
| `/bb dunk <style>` | — | Équiper un dunk. |
| `/bb tags` | `tag` | Tags de collectionneur. |
| `/bb shop [balls\|dunks\|tags\|cosmetics]` | `store` | Boutique (`balls\|basketballs`). |
| `/bb coins [joueur]` | `coin`, `balance`, `bal` | Solde de coins. |
| `/bb ranks` | `elo`, `guide` | Rangs ELO. |
| `/bb stats [joueur]` | `profile` | Statistiques. |
| `/bb history [joueur]` | — | Historique des matchs. |
| `/bb top [elo\|coins\|wins]` | `leaderboard` | Classements. |
| `/bb friends` | `friend`, `f` | Liste d'amis. |
| `/bb party` | `group`, `p` | Groupe. |
| `/bb pc` | `pchat`, `party chat` | Chat de groupe. |
| `/bb mail` | `inbox`, `mailbox` | Boîte aux lettres. |
| `/bb guild <create\|invite\|join\|decline\|leave\|kick\|promote\|demote\|transfer\|disband\|open\|close\|motd\|top>` | `team` | Guilde (`create\|new`, `decline\|deny`). |
| `/bb gchat <message>` | `gc`, `g` | Chat de guilde. |
| `/bb tuto <menu\|start\|restart\|next\|skip\|show\|stop\|finish\|diag>` | `tutorial` | Tutoriel (`menu\|gui` = menu des étapes, `start\|begin\|go`, `restart\|replay\|reset`, `next\|continue\|ok`, `show\|again`, `stop\|pause`, `finish\|done` = téléportation au spawn, `diag`). |
| `/bb quests` | `quest`, `objectives` | Objectifs. |
| `/bb achievements` | `ach` | Succès. |
| `/bb season <top\|standings\|claim\|days\|start\|reset\|end\|close>` | `saison`, `rankedseason` | Saison classée (`days\|set-days`). |
| `/bb crate [open]` | — | Caisses. |
| `/bb report <joueur> <raison>` | — | Signaler. |
| `/bb ignore <joueur>` | — | Ignorer. |
| `/bb welcome` | — | Message de bienvenue. |
| `/bb hotbar` | — | Barre d'action 3D. |
| `/bb settings` | `options` | Préférences. |
| `/bb pack [push [joueur\|all]]` | — | Resource pack (envoi forcé pour les admins). |
| `/bb discord` | — | Discord officiel. |
| `/bb afk` | — | Absent. |
| `/bb status` | — | État du serveur. |

## 5. `/bb` — modération et staff

| Commande | Alias | Effet |
|---|---|---|
| `/bb vanish` | — | Se rendre invisible. |
| `/bb freeze <joueur>` / `/bb unfreeze <joueur>` | — | Immobiliser / libérer. |
| `/bb freezematch` | — | Geler le match courant. |
| `/bb warn <joueur> [raison]` | — | Avertir. |
| `/bb unwarn <joueur> [all]` | — | Retirer un avertissement. |
| `/bb warnings [joueur]` | `warns` | Avertissements. |
| `/bb reports` | — | Signalements. |
| `/bb mutechat [durée]` | — | Couper le chat global. |
| `/bb maintenance <on\|true\|enable \| off\|false\|disable>` | `maint` | Mode maintenance. |
| `/bb audit` | `diagnose` | Diagnostic : TPS, MSPT, caches, entités. |
| `/bb displays` | `hud`, `sidebars` | État des HUD / sidebars. |
| `/bb reload` | — | Recharger la configuration. |
| `/bb backup` | — | Sauvegarde immédiate. |
| `/bb giveelo [give\|take\|set] <joueur> <montant>` | `addelo` | Modifier l'ELO (sans opérateur explicite = ajout). |
| `/bb giveball [ballon]` | — | Donner les items admin + baguette d'architecte. |
| `/bb webhook [test]` | `discordtest` | État réel du webhook Discord (envoyés / échecs / erreur) et envoi d'un message de test. |

## 6. `/bb` — administration serveur

| Commande | Alias | Effet |
|---|---|---|
| `/bb admin` | `panel` | Centre de contrôle visuel. |
| `/bb map` | — | Voir section 7 (studio d'arène). |
| `/bb arena <build\|create\|edit\|list\|tp\|goto\|delete\|reload\|repaste\|info>` | — | Arènes schématiques (`reload\|rescan`, `repaste\|refresh`). |
| `/bb rank <give\|take>` | — | Rangs de permission. |
| `/bb holo <elo\|points\|coins\|play\|shop\|remove\|clear>` | `hologram` | Hologrammes / NPC. |
| `/bb profiler <on\|off\|reset\|cache>` | `perf`, `ticks` | Profiler interne. |
| `/bb pack test` | — | Télécharge le pack depuis le serveur par chaque route (mux port de jeu, port 8765, URL externe) et affiche le débit mesuré. |
| `/bb net [joueur]` | `ping`, `latency` | Diagnostic réseau joueur : latence actuelle/moyenne/min/max, gigue, client, view-distance, état du pack, comparaison avec les autres joueurs et verdict (isolement de la connexion vs serveur). |
| `/bb stress [joueurs] [ticks]` / `/bb stress stop` | `loadtest`, `simulate` | Test de charge interne. |
| `/bb forcestart` | — | Lancer immédiatement la file / le match. |
| `/bb stop` | — | Arrêter le match courant. |
| `/bb save` | — | Sauvegarder toutes les données. |

## 7. `/bb map` — studio d'arène

| Commande | Effet |
|---|---|
| `/bb map auto` | `autosetup`, `quick` : création automatique du court. |
| `/bb map create` | `edit` : créer / éditer un court. |
| `/bb map wand` | Donner la baguette de sélection. |
| `/bb map pos1` / `/bb map pos2` | Coins de la zone. |
| `/bb map barrier1` / `/bb map barrier2` | Coins de la barrière. |
| `/bb map ceiling` | Hauteur du plafond. |
| `/bb map hoop` | `sethoop` : panier. |
| `/bb map north` / `south` / `east` / `west` | Orientation du court. |
| `/bb map setspawn` | `spawn` : point d'apparition. |
| `/bb map ignis` / `frost` / `spectator` | Zones / spawn spectateur. |
| `/bb map lines [on\|off]` (alias `courtlines`) | Lignes du terrain. |
| `/bb map lines preset\|style <full\|street\|minimal>` | Style des lignes. |
| `/bb map lines color\|material <white\|quartz\|brightwhite\|gold\|yellow\|cyan\|aqua\|blue\|red\|black\|dark\|concrete>` | Matériau des lignes. |
| `/bb map lines width\|thickness <0.09\|0.13\|0.16\|0.20>` | Épaisseur des lignes. |
| `/bb map jumbotron` | Écran géant. |
| `/bb map preview` | Aperçu du court. |
| `/bb map info` | Informations du court. |
| `/bb map save` | Enregistrer. |

---

## 8. Outils de développement (dépôt)

```bash
python3 tools/provision_build_env.py      # dépendances de compilation (une fois)
python3 tools/build_plugin.py             # compile + tests JUnit + jar dans releases/
PYTHONPATH=/tmp/pylibs python3 tools/check_server_configs.py   # 136 contrôles de config serveur
python3 tools/generate_resourcepack.py    # régénère resourcepack/ + zip + sha1
python3 tools/import_ref_icons.py         # importe assets/ref-icons/*.png dans le pack
python3 tools/serve_showcase.py           # sert docs/showcase/
PYTHONPATH=/tmp/pylibs python3 tools/optimize_resourcepack.py   # PNG lossless (--check pour vérifier)
```

Documentation utile : `docs/network-diagnostics.md` (ping d'un joueur isolé : `/bb net`,
lecture du verdict, checklist joueur/hôte), `docs/resource-pack-mirrors.md` (servir le pack
via jsDelivr + GitHub Releases, gratuitement), `docs/tooltips.md` (tooltips animés, styles par rareté, rubans de police),
`docs/tablist.md` (le `[TAB]` : carte de profil alignée, sans cadre doré depuis le Round 40),
`docs/palette.md` (la palette fermée : 13 jetons, la règle de l'accent unique, la surface de
commandes réduite à huit),
`docs/typography-and-ornaments.md` (le jeu d'ornements : `▪` `▸` `◆`, filet à segment doré,
flourish doré de la sidebar — et la règle « aucune étoile » du Round 44),
`docs/motd.md` (bannière de la liste des serveurs : 4 états ouvert / démarrage / maintenance / redémarrage),
`docs/tutorial.md` (tutoriel nouveau joueur reconstruit : 8 chapitres validés par le joueur),
`docs/showcase/brand-sheet.png` (planche de marque : tags, rangs, GUI, HUD, palette),
`docs/gui-layout.md` (grille des menus et icônes de hotbar), `docs/ping-sans-carte.md`
(tutoriel gratuit sans carte pour le ping des joueurs français), `docs/latency-europe.md`
(version technique du même sujet), `docs/display-pipeline.md`, `docs/permissions.md`,
`docs/hardening-42.md` (audit « jusqu'à l'os » de 10 473 lignes tirées au hasard : 30 fichiers,
chaque défaut trouvé, sa cause et sa correction), `docs/optimisations-plugins/`.
