# Le `[TAB]` — la carte de profil (Round 40)

Le `[TAB]` est construit par `fr.bosketball.ui.TabListManager`. Il tient en trois morceaux :

1. **l'en-tête** : le logo (les trois bandes bitmap du pack) puis, selon la situation, les compteurs
   du réseau ou le score du match en cours ;
2. **les lignes de joueurs** : le rang, les badges de classement, le tag, le pseudo, l'ELO et le
   statut de chacun (c'est le `playerListName`, une ligne par joueur) ;
3. **le pied** : la carte de profil, onze lignes alignées, sous la liste.

## 1. Ce qui a disparu (et pourquoi)

Le cadre doré du kit `tablist.zip` est **supprimé à 100 %** : le glyphe `U+E370…U+E383`, les vingt
PNG `tab_frame_rNN.png`, ses vingt providers dans `font/default.json`, le namespace
`assets/tablist/`, `tools/tablist_frame.py`, `tools/tablist_source/`, la section `tab-list:` de
`config.yml`, le `record TabList` et le câblage `setPanel(...)`.

Raison : en 26.2 le client ne dessinait pas le glyphe. Le propriétaire voyait à la place un
rectangle gris avec un liseré doré — c'est-à-dire le fond de liste natif — donc l'image ne rendait
tout simplement pas. Après deux tentatives, la décision a été d'arrêter les frais et de revenir à
du texte pur, qui ne peut pas « ne pas s'afficher ».

## 2. La carte de profil

```
                          (logo, 3 bandes)
              ● 12 online  ·  ● 4 in match  ·  ● 2 in queue
   ──────────────────────────────────────────────────────
   … les joueurs …
   ───────── ────── ─────────
   Coins    12,480              Rank    [MVP] Mvp
   Playtime 42h 18m             XP          24,300
   ───────── ────── ─────────
   Record   128 W · 54 L · 70%   Streak  6 (top 14)
   Career   3,412 PTS · 88 DUNKS · 421 ASSISTS
   Shooting 212 3PTS · 48% FG
   Defense  190 STEALS · 96 BLOCKS
   ───────── ────── ─────────
   Guild   Showtime [SHW] · 1,420 ELO
   Badges  #1 ELO · #12 WINS · 17 FRIENDS · 3 MAIL
              ping 38ms · bosketball.net · /stats
```

Tout ce qui est demandé par le propriétaire y est nommé (coins, rank, playtime) **et** tout le
reste vient du profil : record et win rate, streak et best, totaux de carrière, guilde, podiums de
classement, amis, messages non lus, ping et domaine.

> **Round 42 — la carte est en anglais.** Le propriétaire a demandé « traduire le tab en anglais » :
> en-tête, libellés de sections et valeurs de repli (`none`, `/guild create`, `no top 3`) ont été
> traduits. C'est la **seule** exception à la règle « le texte en jeu reste en français » ; la
> sidebar, les messages et tout le reste de l'interface ne changent pas. Les tests R40 vérifient
> désormais l'anglais et refusent la moindre chaîne française dans la carte. Le libellé de saison, le bloc
« Placements » et la progression ELO restent sur la **sidebar** uniquement : le `[TAB]` ne répète
pas la sidebar.

### 2.1 L'alignement : `CARD_PX`

Le client centre chaque ligne d'en-tête/pied **individuellement**. Pour obtenir un bloc aligné à
gauche comme sur Hypixel Skyblock, chaque ligne est complétée à exactement **300 px de police**
(`TabListManager.CARD_PX`) avec les glyphes de décalage du pack (`Glyphs.shift`) :

```java
int missing = CARD_PX - cardWidthPx(line);          // mesure réelle, tags retirés
line + "<!shadow><white>" + Glyphs.shift(missing) + "</white></!shadow>"
```

`cardWidthPx` mesure ce que le client reçoit vraiment : balises MiniMessage retirées, majuscules
déjà passées en petites capitales, et **glyphes de décalage comptés à leur avance signée** (jamais
6 px). Deux règles en découlent :

* **aucun `<bold>` dans la carte** — le gras ajoute 1 px par glyphe et décalerait les colonnes ;
* **aucune valeur non bornée** — `wideRow` retire les éléments de la fin puis élide le texte restant
  (`Glyphs.fitTextToWidth`) pour qu'une guilde de 16 caractères ne puisse pas dépasser.

Les tests `round40TabCardIsAlignedWhateverTheNumbers` (36 combinaisons pièces/ping/domaine) et
`round40TabCardShowsWhatTheSidebarDoesNot` vérifient ces deux propriétés, sans serveur.

### 2.2 Le cache

Une seule signature par joueur (`lastHeaderFooterSig`) contient l'état des compteurs, le score quand
un match est en cours, **et les onze lignes de la carte**. Le paquet n'est renvoyé que si la
signature change : un ELO, une pièce ou un message non lu suffisent, un simple tick non.

### 2.3 L'ordre des lignes

`enforceTabOrder` impose un ordre **total** aux lignes de joueurs (`Player#setPlayerListOrder`,
1.21.2+) : en match d'abord, triés par équipe puis par points, puis les autres par pseudo, l'UUID
départageant les égalités. Aucun échange entre deux joueurs n'est possible (Round 34).

## 3. En match

L'en-tête remplace les compteurs par le score, dans les couleurs des deux camps, avec l'horloge :

```
   IGNIS 12  —  8 FROST  •  3:24
```

Les deux noms d'équipe (16 caractères possibles) sont mesurés et tronqués à la moitié de la largeur
restante, comme tout le reste du bandeau. Le pied garde la carte : les stats de carrière ne bougent
pas d'un match à l'autre.

## 4. Vérifier

```bash
python3 tools/build_plugin.py     # compile + 178 tests + pack + jar
```

Les tests du Round 40 :

* `round40TabCardIsAlignedWhateverTheNumbers` — 36 combinaisons, chaque ligne fait exactement
  300 px, et une guilde décorée ne casse rien ;
* `round40TabCardShowsWhatTheSidebarDoesNot` — les sections demandées, les stats absentes de la
  sidebar, l'absence de saison/placements, l'absence de `<bold>`, **et** la disparition complète du
  cadre (aucun glyphe, aucun PNG, aucun provider, aucun outil, aucune clé de config).

## 5. Round 44 — colonnes fixes, zéro étoile

L'écran du propriétaire montrait deux défauts : des lignes décalées, et des « problèmes de texte ».
Les deux venaient de la même ligne : la ligne de carrière avait deux rangées sans étiquette, dont
une valeur qui débordait de sa cellule et poussait la colonne de droite hors de la carte.

* **Cellules fixes.** `LABEL_PX` 62, `VALUE_PX` 120, `SUB_LABEL_PX` 40, `RIGHT_VALUE_PX` 78
  (soit exactement 300 px). Toute valeur trop large est tronquée par `Glyphs.fitTextToWidth`
  (`fitToCell`), donc plus aucune ligne ne peut dépasser la carte.
* **Colonne de droite alignée à droite** (`rightCell`) : un rang, un total d'XP et une série
  partagent enfin le même bord droit.
* **Trois lignes nommées** à la place de la ligne orpheline : `Career`, `Shooting`, `Defense`, avec
  leurs unités (`PTS`, `DUNKS`, `ASSISTS`, `3PTS`, `FG`, `STEALS`, `BLOCKS`).
* **`W` / `L`** : le record s'écrit `128 W · 54 L · 70%`, plus de `V`/`D` français dans une carte
  anglaise.
* **Aucune étoile** : le filet de section (`cardRule`) est un trait gris coupé d'un segment doré, et
  tout le reste du plugin suit la règle du document
  [`typography-and-ornaments.md`](typography-and-ornaments.md).

Les tests du Round 44 : `round43TabCardColumnsAreColumns` (colonnes à 62 px, bord droit partagé,
aucune ligne orpheline, profil hostile toujours à 300 px avec `…`) et
`round43NoStarGlyphCanReachAScreen` (aucune étoile dans les sources livrées).
