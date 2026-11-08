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

Le pack du propriétaire (`PrimeHUD v2.zip`, branche `main`) contient un HUD de serveur mini-jeux /
RPG. Tout n'est pas à prendre — les barres de vie, de faim, d'armure et d'air n'ont aucun sens sur
un terrain de basket, et l'horloge/un panneau admin encore moins. Ce qui a été importé :

| Asset PrimeHUD | Ce qu'on en fait |
|---|---|
| `image_image_xpbar_1..25.png` (25 tranches, 4 px → 120 px) | **la barre de tir** : elle pousse d'environ 5 px par palier, c'est un vrai meter dessiné, plus douze cases de texte |

C'est la barre qui s'affiche pendant qu'on charge un tir (`Glyphs.composeProAimHud`) et pendant un
dunk (`composeDunkSlamHud`). Elle remplace une barre faite de caractères-blocs entre deux capuchons,
celle qui « faisait script » : des cellules qui ne s'alignaient pas, et une couleur par élément.

Les tranches vivent dans `resourcepack/assets/bosketball/textures/font/hud_shotbar_*.png`, sont
déclarées comme bitmap providers sur `U+F000`…`U+F018` dans
`resourcepack/assets/minecraft/font/default.json`, et se choisissent avec
`Glyphs.shotBarGlyph(progress)`.

Les couleurs du HUD (réticule, arc de visée, halo du cercle, texture de particules) passent
désormais par la palette : `Palette.rgb(Palette.SUCCESS)` & co, plus une seule valeur ARGB en dur.

Ce qui reste à faire dans PrimeHUD : le cadre 9-slice (`backgrounds/background/{left,body,right}`)
pour les panneaux de score, et les icônes (`money`, `online`, `ping_*`) pour le tab et la sidebar.
