# Typographie et ornements

Ce document fige les règles d'écriture visibles en jeu : quels caractères ont le droit d'apparaître
dans une chaîne destinée au joueur, ce qu'ils remplacent, et pourquoi. Il est la référence des
tours R44 et suivants ; le test `round43NoStarGlyphCanReachAScreen` le vérifie automatiquement.

## 1. Règle zéro : aucune étoile

Le propriétaire a tranché en regardant son écran : « enlève les étoiles de partout dans le plugin,
ces étoiles font tellement IA ».

Les quatre caractères suivants sont **interdits** dans toute chaîne visible (chat, sidebar, `[TAB]`,
GUI, MOTD, écrans `/stats`, `/help`, tutoriel, docs joueur) :

| Caractère | Nom | Statut |
| --- | --- | --- |
| U+2726 | étoile à quatre branches | **banni** — c'est celui qui revenait partout (titres, séparateurs, `MVP`) |
| U+2605 / U+2606 | étoile pleine / creuse | **banni** |
| U+2727 | étoile à quatre branches creuse | **banni** |

Remplacer, jamais supprimer : un titre qui perdait son ornement devenait nu, et le propriétaire a
demandé un **remplacement**. Le jeu d'ornements ci-dessous couvre tous les cas qui utilisaient une
étoile.

## 2. Le jeu d'ornements

| Ornement | Codet | Usage | Où |
| --- | --- | --- | --- |
| `▪` | U+25AA | puce de titre et de libellé | `▪ BOSKETBALL ▪`, version du MOTD, annonces de match (`MVP`, `RANK UP`, record personnel), paliers de release, menus GUI, tutoriel |
| `▸` | U+25B8 | pointeur de libellé, dans une fiche | `▸ Rank:`, `▸ Record:`, `▸ Shooting:`, `▸ World:` d'une fiche `/stats` et `/bb arena` |
| `◆` | U+25C6 | puce de la liste des joueurs du MOTD | `◆ In Match: 4`, `◆ In Queue: 2`, `◆ Discord: /discord` |
| `●` | U+25CF | compteur vivant de l'en-tête du `[TAB]` | `● 12 online · ● 4 in match · ● 2 in queue` |
| `·` | U+00B7 | séparateur interne d'une ligne | `128 W · 54 L · 70%`, `[SHW] · 1,420 ELO` |
| `────────` | U+2500 | filet de section | règles du `[TAB]`, séparateurs de chat |
| flourish doré | U+E260 | cadre de la sidebar | première **et** dernière ligne de la sidebar de lobby, dernière ligne de la sidebar en match |

### 2.1 Le filet, avec un segment doré

Un filet n'est plus une suite d'étoiles. `TabListManager.cardRule()` compose une ligne de tirets
gris foncé (`<#64748B>`) coupée au milieu par un segment doré (`<#FBBF24>`), ce qui donne un
séparateur calme, lisible sur fond sombre, et qui reste un vrai séparateur optique.

### 2.2 Le flourish doré : « le truc jaune en haut »

C'est l'ornement que le propriétaire a réclamé au Round 44 (« il est passé où le truc jaune en
haut qui servait de délimitation ? »). Il vivait déjà dans le pack, il avait simplement disparu du
lobby parce que la limite de 15 lignes était mangée par des séparateurs vides.

- Constante : `Glyphs.SB_FLOURISH` = `'\uE260'`
- Texture : `resourcepack/assets/bosketball/textures/font/sidebar_flourish.png` (236×18)
- Provider : `bosketball:font/sidebar_flourish.png`, hauteur 9, `ascent` 8, un seul caractère
- Rendu : `<!shadow><white>` + glyphe + `</white></!shadow>` (le shadow est coupé, sinon le trait
  doré bave d'un pixel)

Il ne se dessine correctement que **si le pack est accepté** : il est donc toujours accompagné de
texte lisible, jamais seul dans une ligne qui n'aurait aucun sens sans lui.

La sidebar de lobby fait exactement 14 lignes, flourish compris :

```
flourish
[PLAYER]  → rang + pseudo
[COINS]   → solde
[ELO]     → progression vers le rang suivant
[PLAYTIME]→ temps de jeu
[CLOCK]   → saison
[FRIENDS] → party / file / amis en ligne
flourish
```

Six blocs de deux lignes (étiquette du pack, puis valeur) encadrés par deux flourishs : c'est la
forme que le propriétaire a validée, et elle ne dépasse pas la limite de 15 lignes du client.

## 3. La carte du `[TAB]` : une vraie grille

`TabListManager` mesure tout en pixels de police, pas en caractères, parce que les glyphes du pack
et la police par défaut n'ont pas la même largeur.

| Constante | Valeur | Rôle |
| --- | --- | --- |
| `CARD_PX` | 300 | largeur totale, chaque ligne est complétée à exactement 300 px |
| `LABEL_PX` | 62 | colonne des libellés (`Coins`, `Playtime`, `Record`, `Career`…) |
| `VALUE_PX` | 120 | colonne des valeurs de gauche, assez large pour `128 W · 54 L · 70%` |
| `SUB_LABEL_PX` | 40 | seconde colonne de libellés (`Rank`, `XP`, `Streak`) |
| `RIGHT_VALUE_PX` | 78 | colonne des valeurs de droite, **collée au bord droit** de la carte |

Deux règles en découlent :

1. **Aucune valeur ne pousse la carte.** Avant le Round 44, un record plus large que sa cellule
   décalait la colonne de droite, la ligne dépassait 300 px, et le client la recentrait tout seul :
   une ligne sur douze apparaissait décalée. Désormais chaque valeur passe par
   `Glyphs.fitTextToWidth` et se termine par `…` si elle ne tient pas (`fitToCell`).
2. **La colonne de droite est alignée à droite.** Un rang, un total d'XP et une série n'ont pas la
   même largeur : alignés à gauche, le bord droit de la carte était en dents de scie.

Chaque ligne porte un libellé. La ligne orpheline du Round 42 (une valeur sans étiquette, qui
commençait dans la colonne des libellés) a été remplacée par trois lignes nommées :
`Career`, `Shooting` et `Defense`, avec leurs unités réelles (`PTS`, `DUNKS`, `ASSISTS`, `3PTS`,
`FG`, `STEALS`, `BLOCKS`).

Le `V/D` français du record est devenu `W/L` au Round 44 : la carte est en anglais (seule exception
à la règle « le texte en jeu reste en français »), et un `V/D` au milieu de `Record`, `Streak` et
`Coins` jurait.

## 4. Petites capitales : une seule fois, à un seul endroit

`Glyphs.smallCapsLabels` transforme les suites de majuscules en petites capitales (`PTS` → `ᴘᴛs`).
Cette transformation est appliquée **une seule fois**, dans `TabListManager.renderLine`, quand la
carte est convertie en composant. Elle n'est **pas** appliquée à la sidebar : c'était la source des
« problèmes de texte » de l'écran du propriétaire (des libellés à moitié en petites capitales, à
moitié en capitales, avec des hauteurs de ligne inégales). La sidebar n'utilise les petites
capitales que pour le préfixe de l'équipe locale, sur sa propre ligne.

## 5. Ce que le test vérifie

`BosketBallEngineTest.round43NoStarGlyphCanReachAScreen` :

- parcourt tous les `.java` de `src/main/java`, tous les `.yml` / `.json` / `.txt` de
  `src/main/resources`, plus `docs/tablist.md` et `docs/motd.md` ;
- échoue sur le premier U+2726, U+2605, U+2727 ou U+2606, en donnant le fichier et l'offset ;
- exige que le remplacement soit réel (`▪ BOSKETBALL ▪`, `\u25aa` dans le payload MOTD,
  `[ GREEN RELEASE ]` sans étoile, `SB_FLOURISH` toujours lié).

`round43TabCardColumnsAreColumns` vérifie les colonnes (62 px, bord droit partagé, aucune ligne
orpheline, profil hostile toujours à 300 px) et `round43SidebarRestoresTheGoldenFlourish` vérifie
les 14 lignes de la sidebar, flourish en tête et en queue.
