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
              ● 12 en ligne  ·  ● 4 en match  ·  ● 2 en file
   ──────────────────────────────────────────────────────
   … les joueurs …
   ────────── ✦ ──────────
   Pièces  12,480        Rang  [MVP] Mvp
   Temps   42h 18m       XP    24,300
   ────────── ✦ ──────────
   Bilan   128 V · 54 D · 70%        Série  6 (top 14)
   Carrière  3,412 PTS · 88 DUNKS · 421 PASSES
             190 INTS · 96 CTRS · 212 3PTS · 48% FG
   ────────── ✦ ──────────
   Guilde  Showtime [SHW] · 1,420 ELO
   Badges  #1 ELO · #12 VICTOIRES · 17 AMIS · 3 MESSAGES
              ping 38ms · bosketball.net · /stats
```

Tout ce qui est demandé par le propriétaire y est nommé (pièces, rang, temps de jeu) **et** tout le
reste vient du profil : bilan et taux de victoire, série et record, totaux de carrière, guilde,
podiums de classement, amis, messages non lus, ping et domaine. Le libellé de saison, le bloc
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
python3 tools/build_plugin.py     # compile + 163 tests + pack + jar
```

Les tests du Round 40 :

* `round40TabCardIsAlignedWhateverTheNumbers` — 36 combinaisons, chaque ligne fait exactement
  300 px, et une guilde décorée ne casse rien ;
* `round40TabCardShowsWhatTheSidebarDoesNot` — les sections demandées, les stats absentes de la
  sidebar, l'absence de saison/placements, l'absence de `<bold>`, **et** la disparition complète du
  cadre (aucun glyphe, aucun PNG, aucun provider, aucun outil, aucune clé de config).
