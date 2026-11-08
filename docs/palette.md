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

## 7. Ce qui fait « fait main » plutôt que « généré » (round 47)

Le pack et les couleurs ne suffisent pas : ce qui trahit un serveur généré, c'est l'incohérence
systémique. Trois choses ont été trouvées en auditant les 4 755 balises de couleur et les 292
messages du plugin.

### 7.1 Une seule langue de couleur

`Palette` existait depuis le round 45, mais **90 % de l'interface parlait encore en couleurs
vanilla** : `<gray>`, `<white>`, `<yellow>`. Deux systèmes de couleur cohabitaient, et ils ne
rendent pas la même chose — `<white>` c'est `#FFFFFF`, la palette c'est `#F8FAFC`. Écran par écran,
ça donnait une impression de collage.

Les 4 755 balises ont été converties vers les treize couleurs de la palette. Le test
`round45ColoursOnlyComeFromThePalette` ne vérifie plus « aucun hexadécimal hors de `Palette.java` »
mais « **aucune quatorzième couleur** » : un hexadécimal est autorisé là où il sert, à condition
d'être l'un des treize.

### 7.2 Le ton : instruction ≠ exclamation

Un serveur fait main réserve le `!` à ce qui est réellement une bonne nouvelle. Une ligne de lore
qui explique quoi faire n'est pas une exclamation :

| Avant | Après |
|---|---|
| `Play matches to earn Coins!` | `Play matches to earn coins` |
| `Send a friend request in 1 click!` | `Send a friend request` |
| `Click to immediately queue another match!` | `Click to queue another match` |
| `Create your own Team Name (2–16 chars)!` | `Team name, 2 to 16 characters` |
| `Get ready for Tip-Off!` | `Starts the match` |

Trois règles :
1. **Les majuscules de milieu de phrase disparaissent** (`Coins`, `Chat Tags`, `Tip-Off` → minuscule).
2. **Les parenthèses explicatives sortent** : `(Blocks + Bleachers + Hoops + Scoreboard)!` devient
   `(blocks, bleachers, hoops, scoreboard)`, et `(Or fine-tune … individually below!)` devient
   `Or fine-tune each part below`.
3. **Le marketing sort** : « in 1 click », « Get ready for », « right where you stand ».

Le `!` reste sur les vraies célébrations (panier, MVP, montée de rang) et sur les toasts de match
courts — pas sur les instructions.

### 7.3 Les marques

- Une seule coche `✔` et une seule croix `✖` pour tout le plugin (il y en avait trois de chaque).
- Les messages système ne portent pas de `[BosketBall]` en texte : ils portent le glyphe du pack
  (`Glyphs.SYS_SERVER`), comme les gros serveurs.
- Les séparateurs sont `·`, jamais `—` dans une ligne courte : le tiret cadratin est une signature
  de texte généré.

### 7.4 Ce qui reste volontairement

Les `Click to …` sur les vrais boutons restent : c'est une convention de serveur, pas une faute. Ce
qui a été retiré, c'est le `Click to …` sur les lignes qui ne sont pas cliquables.

### 7.5 Puces et séparateurs (round 48)

Deux caractères pour un seul travail, c'est une incohérence de plus. La règle est maintenant
explicite :

| Usage | Caractère |
|---|---|
| Séparateur dans une ligne (`A · B`) | `·` |
| Puce en début de ligne de lore | `•` |

66 séparateurs en ligne qui utilisaient `•` sont passés en `·`. Les puces de lore restent des
puces : c'est leur fonction.

### 7.6 Un seul nom par chose

- Le mode s'appelle **3-Point Contest** partout (il s'écrivait `three-point contest` en prose).
- Un badge s'appelle **Badge:** (`Full Badge:` dans un menu, `Badge:` dans l'autre).
- Le rang s'appelle **Rank:** (`Current Rank:`, `Your rank:`, `Rank:` → un seul).

### 7.7 Les nombres

`Msg.count()` groupe les milliers : `12,480`, jamais `12480`. Il n'y a plus qu'un seul formateur
dans le plugin — le tab en avait un privé, la sidebar aucun.

### 7.8 Ce qui a été testé puis refusé

- **Le hotbar recolorié sur la rampe de la palette** : écrasait les cadres de slots, l'art d'origine
  est meilleur. Gardé tel quel.
- **La barre vanilla du boss bar** : les sprites du pack sont transparents, la célébration est déjà
  du texte seul. Rien à faire.
- **Le hover** : les 21 `hoverEvent` du plugin sont tous sur des éléments cliquables. Rien à faire.
