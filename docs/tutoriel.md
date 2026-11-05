# Tutoriel — fonctionnement

8 chapitres, entièrement automatisés. `/tuto` ouvre le menu, `/tuto start` lance l'intro.

## Ce qui existe maintenant en jeu

- **Anneau doré réel** : 16 `BlockDisplay` de blocs d'or (aucun bloc posé dans le monde),
  texte flottant « STEP n/8 » + « Walk into the ring », colonne de particules et trait de
  `GLOW` qui relie le joueur à l'objectif.
- L'anneau devient **émeraude** au passage du joueur, puis disparaît.
- **Boss bar** de progression + barre d'action permanente : étape, objectif, état.
- **Validation automatique** par le gameplay : entrer dans l'anneau, tirer, dunker, sneaker,
  ouvrir `/shop`, rejoindre une file. Aucune commande n'est nécessaire.
- **Filet de sécurité** : si une action n'est pas détectée, l'étape se valide d'elle-même au
  bout de 45 s (`SOFT_SKIP_MS`) — impossible de rester bloqué.
- **Menu** (`/tuto` ou `/tuto menu`) : 8 chapitres cliquables, barre de progression, récompenses,
  `START/RESTART`, `NEXT`, `PAUSE`.
- Nettoyage complet : `clearPlayer` (quit), `stop`, `finish` et `purgeMarkers()` retirent
  marqueurs, barre et entités ; tout est marqué `bosketball_tutorial_marker`.

## Chapitres

| # | Titre | Objectif | Validation |
|---|---|---|---|
| 1 | Welcome | lire la carte | clic / 45 s |
| 2 | Move & look | entrer dans l'anneau doré | `PlayerMoveEvent` < 2,6 blocs |
| 3 | Shooting | tirer | `notifyAction("shoot")` (MatchSession) |
| 4 | Dunking | dunker | `notifyAction("dunk")` |
| 5 | Defense | crossover / steal | sneak ou `notifyAction("defense")` |
| 6 | Economy | ouvrir la boutique | `GuiMenuManager.openShop` |
| 7 | Queue | rejoindre une file | `GameManager.enqueueSinglePlayer` |
| 8 | Complete | finir | prime de fin (`tutorial.completion-coins` / `-xp`) |

Récompenses par étape : 10 / 15 / 25 / 25 / 20 / 25 / 40 / 50 coins (total 210) + prime de fin.

## Configuration

`config.yml` → `tutorial.enabled`, `hologram-enabled`, `completion-coins`, `completion-xp`,
`use-practice-court`. Le tutoriel utilise le court d'entraînement instancié ; s'il est occupé,
le joueur reste sur place et le chapitre continue (aucun blocage).

## Limites connues

- Les marqueurs sont des entités d'affichage : visibles jusqu'à 96 blocs, coût réseau nul hors
  visibilité (`setViewRange`).
- Sur un serveur sans arène disponible, l'anneau est posé au sol sous le joueur.
