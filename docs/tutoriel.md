# Tutoriel — fonctionnement

8 chapitres, entièrement automatisés. `/tuto` ouvre le menu, `/tuto start` reprend, `/tuto restart` recommence.

## Démarrage

- **Nouveau joueur** : le tutoriel démarre tout seul 3 s après la carte de bienvenue
  (`tutorial.force-on-first-join: true`). Le joueur est prévenu et peut l'arrêter avec `/tuto skip`.
- **Joueur déjà avancé** : `/tuto` **reprend** à l'étape sauvegardée (l'intro cinématique ne
  rejoue que sur un nouveau départ). `/tuto restart` rejoue tout depuis le chapitre 1.
- **Fin** : `/tuto finish` (ou la dernière étape validée) termine le tour et **téléporte le joueur
  au spawn du lobby** (`protectionManager().spawnLocation()`), jamais sur le court d'entraînement.

## Ce qui existe en jeu

- **Anneau doré réel** : 16 `BlockDisplay` de blocs d'or (aucun bloc posé dans le monde), texte
  flottant « STEP n/8 » + consigne, particules END_ROD, son de balise.
- L'anneau devient **émeraude** au passage du joueur, puis disparaît.
- **Boss bar** de progression + **action bar** permanente : étape, objectif, état.
- **Validation automatique** par le gameplay : entrer dans l'anneau, tirer, dunker (le dunk valide
  aussi le chapitre tir), se défendre, ouvrir `/shop`, rejoindre une file.
- **Filet de sécurité** : chaque étape se valide seule (20 s pour l'accueil, 45 s pour les autres) —
  impossible de rester bloqué, même si un événement de jeu est manqué.
- **Menu** (`/tuto`, `/tuto menu`) : 8 chapitres cliquables, barre de progression, récompenses,
  `RESTART THE TOUR`, `NEXT STEP`, `PAUSE TUTORIAL`.
- **Robustesse** : chaque rendu (anneau, HUD, boss bar, intro, hologramme) est encapsulé. Une erreur
  d'affichage est journalisée et n'interrompt plus la tâche périodique — c'était la cause des
  « éléments du tuto qui n'apparaissent pas ».
- `/tuto diag` affiche l'état complet : config, étape, marqueurs, boss bar, hologramme, court
  disponible, cible de spawn. Idéal pour un rapport de bug.

## Chapitres

| # | Titre | Objectif | Validation | Attente max |
|---|---|---|---|---|
| 1 | Welcome | lire la carte | clic `ᴄᴏɴᴛɪɴᴜᴇ` | 20 s |
| 2 | Move & look | entrer dans l'anneau doré | `PlayerMoveEvent` < 2,6 blocs | 45 s |
| 3 | Shooting | tirer | `notifyAction("shoot")` | 45 s |
| 4 | Dunking | dunker | `notifyAction("dunk")` | 45 s |
| 5 | Defense | sneak / steal | `notifyAction("defense")` | 45 s |
| 6 | Economy | ouvrir la boutique | `GuiMenuManager.openShop` | 45 s |
| 7 | Queue | rejoindre une file | `GameManager.enqueueSinglePlayer` | 45 s |
| 8 | Complete | finir | prime de fin + **retour au spawn** | — |

Récompenses par étape : 10 / 15 / 25 / 25 / 20 / 25 / 40 / 50 coins (210 au total) + prime de fin
(`tutorial.completion-coins` = 250, `completion-xp`).

## Configuration

`config.yml` → `tutorial.enabled`, `force-on-first-join`, `hologram-enabled`, `completion-coins`,
`completion-xp`, `use-practice-court`.

Le court d'entraînement est instancié quand il est libre ; sinon le chapitre continue là où se trouve
le joueur (l'anneau se pose à ses pieds) et aucun blocage n'est possible.

## Commandes

```
/tuto                      menu du tutoriel
/tuto start                reprendre à l'étape sauvegardée
/tuto restart              recommencer depuis le chapitre 1
/tuto next | skip          étape suivante (skip = sans validation)
/tuto show                 réafficher la carte de l'étape en cours
/tuto stop                 pause (progression sauvegardée)
/tuto finish               terminer + téléportation au spawn
/tuto diag                 diagnostic complet
```
