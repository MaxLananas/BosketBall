# La palette

Ce document est la référence couleur du plugin. Il accompagne `fr.bosketball.util.Palette`, qui est
le **seul** endroit où une couleur est écrite dans les sources, et le test
`round45ColoursOnlyComeFromThePalette`, qui refuse tout `#RRGGBB` ailleurs.

## 1. Pourquoi (round 45)

Avant le round 45, `src/main/java` contenait **47 couleurs différentes**, dont :

* **4 jaunes** (`#FBBF24`, `#FDE047`, `#FFD43B`, `#FFD700`) pour la même idée : « c'est de l'or » ;
* **6 rouges** (`#F87171`, `#FF5C4D`, `#FF4D4D`, `#FF4D3D`, `#F43F5E`, `#FF5722`) ;
* **7 oranges**, **8 gris**, **6 violets**…

Aucune ligne n'était fausse. Mais le solde de pièces de la sidebar, celui de la carte `[TAB]` et
celui de la boutique n'avaient pas la même teinte ; les menus n'avaient pas le même gris que les
messages. C'est exactement ce qui fait dire d'une interface qu'elle a été assemblée plutôt que
dessinée.

## 2. Les treize jetons

| Jeton | Valeur | Rôle exact |
|---|---|---|
| `TEXT` | `#F8FAFC` | texte principal : noms, valeurs, tout ce qui doit être lu |
| `MUTED` | `#94A3B8` | texte secondaire : libellés, aides, la moitié discrète d'une phrase |
| `DIM` | `#64748B` | tertiaire : séparateurs, lignes vides, ce qui doit disparaître |
| `GOLD` | `#FBBF24` | **l'accent unique** d'un écran : argent, rang, l'action attendue |
| `GOLD_SOFT` | `#FDE047` | le chiffre pour lequel on est venu (solde, score d'un classement) |
| `AMBER` | `#D97706` | or profond : bordures, bas d'un dégradé doré |
| `WARN` | `#FB923C` | avertissement chaud : maintenance, démarrage, série en cours |
| `SUCCESS` | `#4ADE80` | positif : ami en ligne, victoire, file ouverte |
| `DANGER` | `#F87171` | négatif : ami hors ligne, défaite, erreur |
| `EMBER` | `#FF5C4D` | l'équipe ignis |
| `ICE` | `#38BDF8` | information, et l'équipe frost |
| `VIOLET` | `#C084FC` | cosmétiques, collectibles, rareté |
| `BLURPLE` | `#5865F2` | la couleur de Discord, et **uniquement** pour nommer Discord |

## 3. La règle de l'accent unique

> Un écran a le droit à **un** accent, et à trois neutres.

Concrètement :

* un écran = `TEXT` + `MUTED` + `DIM` + **un** jeton coloré ;
* `GOLD` est réservé à l'argent, au rang et à l'action principale (le classement, la boutique, le
  bouton qu'on veut voir cliqué) ;
* `SUCCESS` / `DANGER` / `WARN` sont **sémantiques**, jamais décoratifs : un joueur en ligne, une
  défaite, une maintenance. On ne les utilise pas pour « égayer » ;
* si un écran a besoin d'un deuxième accent, c'est qu'il n'a pas de hiérarchie : on enlève quelque
  chose au lieu d'ajouter une couleur.

Ce que ça a changé au round 45 :

| Écran | Avant | Après |
|---|---|---|
| MOTD banner | 5 bold English slogan lines, each in its own gradient, 9 colours | gold + slate, 3 useful lines, 0 gradient |
| Classement (hologrammes) | valeur bleue/verte/orange selon la catégorie, titre or, pastille active verte, lien bleu, plaque dorée pour le 1er | or uniquement : titre, valeur, pastille active. Plaques identiques |
| Carte d'aide `/help` | 4 sections, 12 commandes, une couleur par commande, verbes anglais (`Unlock`, `Browse`, `Challenge`) | 3 sections, **8 commandes**, une seule couleur |
| Fiche `/stats` | 5 couleurs, `▸ Rank:` en anglais | libellés gris, valeurs blanches, ELO en or |

## 4. La surface de commandes

`/help` n'annonce plus que huit commandes : `/play`, `/practice`, `/leave`, `/shop`, `/locker`,
`/stats`, `/friend`, `/discord`. Les autres (`/duel`, `/party`, `/msg`, `/mail`, `/guild`, `/quests`,
`/season`, `/afk`, `/gg`…) fonctionnent toujours mais ne sont plus affichées : la carte d'aide dit ce
qu'il faut faire d'abord, pas tout ce que le serveur sait faire.

`/stats` (alias `/profil`) est **nouveau** : le pied de la carte `[TAB]` l'annonçait depuis le
round 40 alors que la commande n'existait pas — il fallait passer par `/bb stats`, réservé au staff.

Les descriptions de `plugin.yml` ont été réécrites en une ligne d'anglais chacune (elles
s'affichent dans l'aide du client et au survol).

## 5. Vérifier

```bash
python3 tools/build_plugin.py     # compile + 179 tests + pack + jar
```

* `round45ColoursOnlyComeFromThePalette` : aucun `#RRGGBB` hors de `Palette.java`, 13 jetons
  uniques, le bloc `motd:` de `config.yml` n'utilise que la palette, aucune formule de
  publireportage dans la bannière, huit commandes exactement sur la carte d'aide, `/stats` déclaré
  et routé ;
* `leaderboardRowsAreAPixelGridWithIdenticalPlates` : les plaques du classement font toutes 216 px,
  y compris les lignes vides.

## 6. Le HUD PrimeHUD (round 46)

Le pack du propriétaire (`PrimeHUD v2.zip`, branche `main`) est un HUD de serveur mini-jeux / RPG.
La bonne question n'est pas « quels assets je copie » mais « qu'est-ce que son *système de dessin*
m'apprend ». Réponse : il dessine ses panneaux en **9-slice** — deux bouts arrondis de 6 px et une
tuile de corps qui se répète tous les 6 px. C'est trois glyphes, et ça suffit à dessiner un panneau
arrondi de n'importe quelle largeur.

### 6.1 La pastille des notifications

Tous les messages de match (« Intercepted pass! », « Shot clock expired · 24s », « Dive · ball
recovered ») passent par `Msg.rail(player, ...)`. Le texte est entouré dans une **vraie pastille
dessinée** :

1. on dessine `bout gauche + n × corps + bout droit` (largeur = 12 + 6n) ;
2. on rembobine le curseur au début de la pastille avec les glyphes de décalage négatif ;
3. on écrit le texte **par-dessus**, centré.

C'est la technique qu'utilisent les gros packs pour poser du texte dans une image : le texte est
dessiné après, donc il passe au-dessus. Résultat : plus une seule ligne de texte coloré qui flotte
au-dessus de la hotbar, mais une surface unique, dessinée, dont la largeur suit le texte.

Deux garde-fous : la pastille s'arrête à 176 px, et **au-delà elle n'est pas dessinée** (le texte
reste nu) plutôt que de déborder de l'écran. C'est aussi pour ça que les toasts de match ont été
raccourcis : un message de quarante caractères ne rentre pas, c'est une notification, pas un
paragraphe.

### 6.2 La barre de tir

Les 25 tranches `image_image_xpbar_1..25.png` (4 px → 120 px) sont la barre de charge : elle pousse
d'environ 5 px par palier, une vraie barre de jeu qui se remplit.

Round 46 lui a ajouté un **rail** : `hud_shotbar_rail.png`, 120 × 9, dessiné dans la langue du pack
(bordure `#434343`, intérieur noir à 60 %). La tranche se dessine par-dessus le rail, donc la barre
a un début et une fin visibles dès le premier tick du tir au lieu de pousser dans le vide.

### 6.3 Les couleurs

Réticule, arc de visée, halo du cercle et poussière de particules prennent leurs couleurs dans
`Palette` (`Palette.rgb(Palette.SUCCESS)`). Plus une seule valeur ARGB en dur dans le HUD.

### 6.4 Vérifier sans lancer un client

`tools/preview_hud.py` réimplémente les trois calculs de `Glyphs.java` (`textAdvancePx`,
`composePill`, `composeShotBar`), parcourt la chaîne de glyphes comme le client — décalages négatifs
compris — et écrit `docs/hud-preview.png`. Une pastille décentrée d'un pixel ne lève aucune
exception : elle se voit juste à l'écran, trop tard.

| Asset PrimeHUD | Ce qu'on en fait |
|---|---|
| `backgrounds/background/{left,body,right}.png` | la pastille des notifications (9-slice) |
| `image_image_xpbar_1..25.png` | la barre de charge (4 px → 120 px) |
| — (dérivé) | `hud_shotbar_rail.png`, le rail de cette barre |

Ce qui reste volontairement de côté : les barres de vie / faim / armure / air, le panneau admin, les
widgets RAM/TPS/jobs/gems — aucun sens sur un parquet — et les JSON de police du pack (459 Ko
chacun), puisqu'on a la nôtre.
