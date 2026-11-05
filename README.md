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

## 6. `/bb` — administration serveur

| Commande | Alias | Effet |
|---|---|---|
| `/bb admin` | `panel` | Centre de contrôle visuel. |
| `/bb map` | — | Voir section 7 (studio d'arène). |
| `/bb arena <build\|create\|edit\|list\|tp\|goto\|delete\|reload\|repaste\|info>` | — | Arènes schématiques (`reload\|rescan`, `repaste\|refresh`). |
| `/bb rank <give\|take>` | — | Rangs de permission. |
| `/bb holo <elo\|points\|coins\|play\|shop\|remove\|clear>` | `hologram` | Hologrammes / NPC. |
| `/bb profiler <on\|off\|reset\|cache>` | `perf`, `ticks` | Profiler interne. |
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
lecture du verdict, checklist joueur/hôte), `docs/display-pipeline.md`,
`docs/permissions.md`, `docs/optimisations-plugins/`.
