# Petites capitales, bug de balises et calibration du terrain

> Livré le 5 novembre 2026 sur `arena/a1145fcf-bosketball`.
> Build de référence : **224 classes, 0 erreur · 92 tests OK · pack 3 796 641 B · jar 4 580 978 B**.

---

## 1. Petites capitales (`ᴀ ʙ ᴄ ᴅ ᴇ ꜰ ɢ ʜ ɪ ᴊ ᴋ ʟ ᴍ ɴ ᴏ ᴘ ǫ ʀ ꜱ ᴛ ᴜ ᴠ ᴡ x ʏ ᴢ`)

Deux utilitaires dans `fr.bosketball.util.Glyphs` :

| Méthode | Effet |
|---|---|
| `smallCaps(String)` | convertit **toutes** les lettres ASCII en petites capitales |
| `smallCapsLabels(String)` | convertit uniquement les **séries de 2 majuscules ou plus**, en **ignorant tout ce qui est entre `<` et `>`** |

`smallCapsLabels` est la version utilisée partout : elle laisse intacts les codes couleur
(`<#FDE047>`), les balises (`<bold>`, `<!shadow>`…) et les noms de joueurs (`MaxLananas`,
`M` et `L` étant des séries d'une seule lettre). `HOOPER` → `ʜᴏᴏᴘᴇʀ`, `ELO` → `ᴇʟᴏ`,
`MaxLananas` → inchangé.

Points d'application (police de base, aucun pack de police custom nécessaire — les glyphes
existent déjà dans la police Minecraft) :

| Endroit | Détail |
|---|---|
| **Noms d'items de tous les menus** | `GuiMenuManager.customIcon(...)` : un seul point d'entrée pour ~95 % du texte de menu |
| **Titres de GUI** | `Glyphs.customGuiOverlayTitle(glyph, caption)` — la légende de la plaque passe en petites capitales **avant** le calcul de largeur, donc l'ajustement à 124 px reste juste |
| **Barre latérale** | chaque ligne rendue par `ScoreboardSidebarManager.applyLines` + l'en-tête `ʙᴏꜱᴋᴇᴛʙᴀʟʟ` |
| **Tab list** | `TabListManager.parseMultiline` (en-tête, pied, lignes de statut) |
| **Hologrammes du lobby** | `LobbyHologramManager` (stations, classements, pied de plaque) |
| **Boss bar** | titre de file d'attente |
| **Modes de jeu** | `GameModeType.smallCapsTitle()` renvoie enfin des petites capitales (la méthode portait déjà ce nom sans le faire) |

Test de garde : `smallCapsLabelsKeepMiniMessageTagsIntact` vérifie l'alphabet complet,
la préservation des balises, l'immunité des noms de joueurs et des couleurs hexadécimales.

## 2. Bug de syntaxe dans les noms de grade (`HOOPER<\/#FDE047>`)

**Cause exacte** — dans le menu Saison, le nom du palier était construit ainsi :

```java
(current ? "<#FDE047><bold>▶ " : reached ? "<" + accent + "><bold>" : "<#64748B><bold>")
    + rank.plainDisplayName().toUpperCase(Locale.ROOT)
    + "</bold></#FDE047>"
```

La couleur d'ouverture était **dynamique** (l'accent du rang), mais la fermeture restait
codée en dur sur `</#FDE047>`. Pour un palier atteint mais pas courant (`HOOPER`, accent vert),
MiniMessage recevait une fermeture orpheline et la ré-échappait en texte littéral :
`HOOPER<\/#FDE047>`.

**Correction** — ouverture et fermeture sont désormais calculées ensemble :

```java
String tierOpen  = current ? "<#FDE047><bold>▶ " : reached ? "<" + accent + "><bold>" : "<#64748B><bold>";
String tierClose = current ? "</#FDE047>" : reached ? "</" + accent + ">" : "</#64748B>";
```

(Les deux autres lignes visibles sur la capture — `minecraft:paper` et `13 component(s)` —
sont les info-bulles avancées de F3+H, pas un bug du plugin.)

## 3. `terrain.schem` : pourquoi les paniers étaient sur le toit

Le fichier livré sur `main` a été décodé (Sponge v3, `DataVersion 4903`) :

```
49 × 21 × 52   ·   18 246 blocs non-air   ·   palette 69 entrées   ·   offset [-25,-3,-25]

Y  0-1   vide
Y  2     andesite (574) + anneau orange + 510 light            <- LE PLANCHER
Y  3-12  murs (white_wool / stripped_pale_oak_log) + ~1 200 light/layer
Y 13     coal_block plein (1 517 blocs)                        <- LE TOIT
Y 14-20  vide
```

Composition : **12 698 blocs `minecraft:light`** (éclairage invisible) et **780 `white_wool`
uniquement dans les murs** — donc **aucune ligne blanche peinte au sol**.

### Le diagnostic

L'ancienne calibration (`floorOffsetAtCenter`) comptait **tous** les blocs non-air, blocs de
lumière compris, puis choisissait la couche la plus haute dépassant 90 % de la couche la plus
large. Ici la couche la plus large est **le toit** (1 517) : le « deck » était donc le toit,
soit `floorOffset = 14`.

Conséquences mesurées :

| | avant | après |
|---|---|---|
| Deck détecté | Y 13 (toit) | Y 2 (plancher) |
| `floorOffsetAtCenter` | 14 | **3** |
| Origine de collage (`origin-y: 64`) | 50 | **61** |
| Plancher réel | Y 52 | **Y 63** |
| Paniers (`floor + rim-relative-y`) | **Y 67 = au-dessus du toit** | **Y 67, sous le toit (Y 74)** |
| Lignes de cour | aucune (`bakedCourtLines` forcé à `true`) | tracées par le plugin |

### Les correctifs

1. **Les blocs de lumière ne sont plus structurels** (`isDecorationHelper`) : 12 698 blocs
   invisibles ne faussent plus la vote des couches.
2. **Nouveau détecteur de deck** (`CourtSchematic.deckOffset`) : on descend depuis le bas et on
   retient la **première plateforme contiguë** (couche ≥ 50 % de la couche la plus large), en
   s'arrêtant dès que la couche suivante décroche. Un toit, même plus large que le plancher,
   n'est jamais atteint ; un plancher multi-couches résout sur sa couche supérieure.
   Fonction pure, testée directement.
3. **Détection des lignes peintes** (`deckLineMarkings`) : sur la couche du deck, on compte les
   blocs `white_*`, `quartz`, `snow_block`, `smooth_stone`, `polished_diorite`. Seuil : 16.
4. **Plus de court nu** : `bakedCourtLines = flush-floor && lignes détectées`. Si le build n'a
   pas de lignes peintes, `Arena` trace son propre anneau de lignes (BlockDisplay) au lieu de
   ne rien faire — c'était la régression du passage aux courts schématiques.
5. **Journal de calibration** au chargement :
   `[Courts] Calibration: deck surface = local Y 3 (floor blocks at Y 2), line blocks on the deck = 0.`
6. **Court par défaut embarqué** : `src/main/resources/courts/terrain.schem` est extrait vers
   `plugins/BosketBall/schematics/terrain.schem` si aucun fichier n'existe sur le disque, pour
   que les courts fonctionnent dès le premier démarrage.

`config.yml` documente maintenant les deux modes de `flush-floor` (lignes du build vs lignes
tracées par le plugin).

### Réglages conseillés pour ce terrain

```yaml
schematic-courts:
  origin-y: 64          # le plancher (Y local 2) atterrit à Y 63, la surface du deck à 64
  rim-relative-y: 3     # paniers à Y 67, toit du build à Y 74
  half-width: 9.0       # le terrain fait 49x52, la salle utile 37x41
  half-length: 15.0
  flush-floor: true     # aucune ligne peinte détectée -> le plugin trace
```

## 4. Nouveaux tests (89 → 92)

| Test | Ce qu'il prouve |
|---|---|
| `roofedBuildPutsTheDeckOnTheFloorNotOnTheRoof` | toit au-dessus + intérieur rempli de `light` → deck sur le plancher ; `deckOffset` testé sur 4 profils de couches |
| `paintedCourtLinesAreDetectedOnTheDeck` | lignes blanches détectées (≥ 16 blocs), deck non peint → pas de lignes « baked » |
| `bundledTerrainSchemCalibratesOnTheDeckNotTheRoof` | le `terrain.schem` livré : 49×21×52, deck local Y 3, 0 ligne peinte, 18 246 blocs |
| `smallCapsLabelsKeepMiniMessageTagsIntact` | alphabet complet, balises et noms de joueurs préservés |

## 5. Reste ouvert

- Le terrain livré est un **hall fermé** (murs + toit + éclairage) sans tracé au sol : le
  plugin ajoute les lignes, mais un tracé blanc peint dans le build serait plus joli et
  éviterait le rendu BlockDisplay.
- Les blocs de lumière comptent pour ~12 700 écritures par court (≈ 18 000 au total) : le
  budget `max-blocks-per-tick` absorbe, mais on pourrait ne pas re-coller les `light` si le
  monde est déjà éclairé en ambiance.
