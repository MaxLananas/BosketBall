# Sens du terrain, panneaux protégés, têtes custom et nametag du lobby

> Livré sur `arena/a1145fcf-bosketball`. Build de référence (round 39) : **249 classes, 0 erreur ·
> 166 tests OK · pack 3 453 666 B · jar 4 330 345 B**.

> Les sections 1 à 5 datent du round 21 (arène intérieure `terrain.schem`). La section 6 est le
> round 39 : deuxième terrain, têtes custom, palette blindée.

---

## 1. Nametag : un seul pseudo, plus de « LOBBY »

Le nametag Minecraft s'affiche **toujours** sous la forme `préfixe + pseudo + suffixe`.
La ligne 2 du `NametagManager` commençait par `<white><bold>pseudo</bold></white>` : le pseudo
apparaissait donc **deux fois** (`PLANCH LOBBY` + *Akashhi_* + *Akashhi_ 1121 ELO*).

| Avant | Après |
|---|---|
| `line1` = grade/tag + `LOBBY` | `line1` = grade/tag, **rien** hors terrain |
| `line2` = **pseudo** + ELO | `line2` = ELO (ou PTS) uniquement |

Sur le terrain, `IGNIS` / `FROST` reste affiché après le grade (il ne double jamais le pseudo),
et le suffixe garde les points de la partie (`12 PTS`, série de feu).

## 2. Le terrain, c'est le sol coloré du build (pas sa boîte)

`terrain.schem` (49×21×52) contient énormément de choses qui ne sont **pas** le terrain :
tribunes en laine blanche + rondins, toit de charbon, murs d'andésite. La boîte englobante fait
49×52 alors que **le vrai terrain, c'est le rectangle orange** (laine + béton orange) :

```
couche y=2  ->  terrain orange : x 12..38  (27 blocs)   z 18..32  (15 blocs)
                centre         : x 25.5                z 25.5
                lignes peintes : 126 blocs (pale oak, terracotta bleue/rose = clés)
```

Nouvelle détection `CourtSchematic#detectCourt()` : sur la couche du plancher, on cherche la
**famille de couleur** (laine / béton / béton en poudre / terre cuite / tapis / planches) dont la
boîte a une proportion de terrain (1,15 → 2,6) et un remplissage ≥ 30 %. Résultat :

| Ce que le plugin utilise maintenant | Valeur pour `terrain.schem` |
|---|---|
| axe des paniers | **X** (côté long du terrain : 27 > 15) |
| demi-longueur (basket à basket) | **13,5** (au lieu de 15,0 → les paniers tombaient 1,25 bloc **hors** du terrain) |
| demi-largeur (ligne de touche) | **7,5** (au lieu de 9,0 → on courait dans les tribunes) |
| centre du terrain | **25,5 / 25,5** (au lieu de 25 / 25 → le décalage « entre 4 blocs ») |
| lignes | **dessinées par le plugin** sur le rectangle 27×15 (le sol orange n'a pas de peinture blanche) |

En jeu, court n°1 (origine `100000`) :
* centre = **100025.5 / 64 / 100025.5** ;
* paniers à **x = 100013.25** et **x = 100037.75**, **z = 100025.5**, **y = 67** (3 au-dessus du sol) ;
* rectangle de jeu = **27 × 15**, exactement le rectangle orange.

`rotation` reste disponible (0 / 90 / 180 / 270) mais **le défaut repasse à 0** : plus besoin de
tourner quoi que ce soit, l'axe vient du terrain lui-même. Les valeurs `half-width` /
`half-length` du `config.yml` ne peuvent plus qu'**rétrécir** ce rectangle.

## 2 bis. Portes fermées, décorations qui ne tombent plus

* **Portes ouvertes** : toutes les `_door` / `_trapdoor` / `_fence_gate` du `.schem` sont écrites
  `open=false` + `powered=false` dès la palette résolue, puis un dernier passage
  (`CourtSchematic#closeDoors`) force la fermeture des blocs déjà posés (≈225 blocs par court).
* **Signs et leviers ramassables** : dans le monde des terrains,
  `BlockDropItemEvent` vide la liste des drops et `EntityPickupItemEvent` annule (et supprime)
  le ramassage de tout panneau / levier / bouton / torche / rail / porte / plaque. Le décor reste
  décor : il ne part jamais dans un inventaire.

## 2 ter. États de blocs : collage fidèle

* **Aucun état n'est réécrit** sauf les *portes* (`_door`) qui reviennent fermées. Les trappes
  gardent leur `open=true/false` d'origine (celles du build sont des trappes **ouvertes** en
  décoration murale : les fermer les couchait au sol), et les leviers/boutons gardent leur
  `powered`.
* **Dernier passage de réparation** (`CourtSchematic#repairAttachables`) : panneaux, leviers,
  boutons, torches, échelles, rails, tapis, trappes et barrières sont revérifiés bloc par bloc ;
  tout ce qui a disparu pendant l'écriture des voisins est remis avec son matériau exact.
* **Physique neutralisée** dans le monde des courts pour ce décor : `BlockPhysicsEvent` est
  annulé, plus rien ne « pop » quand un voisin change.
* Si `rotation` n'est pas à 0, un avertissement explicite est écrit au démarrage : un build tourné
  fait pointer panneaux et leviers vers le mauvais mur.

## 3. Les panneaux « illégal » ne cassent plus

Trois causes possibles, les trois sont traitées :

1. **Ordre de collage** — un panneau mural était écrit avant le mur qui le porte et pouvait
   tomber pendant le collage. Les blocs « attachables » (panneaux, torches, échelles, rails,
   boutons, plaques, bannières, tapis, fleurs…) sont maintenant collés dans un **second passage**,
   une fois toute la structure en place (`CourtSchematic#pasteAttachables`).
2. **Contenu perdu** — les 216 `minecraft:sign` du `.schem` étaient comptés mais jamais
   ré-appliqués. `CourtSchematic` conserve désormais chaque block entity (position + sa charge
   utile) et `applyBlockEntities(...)` restaure côté avant/arrière, couleur, texte brillant et
   `is_waxed` juste après le collage (les messages JSON `{"text":"…"}` sont déballés en texte
   simple). La charge utile est lue sous `Data` (Sponge v3) **ou à plat** dans le compound
   (Sponge v2, voir section 6).
3. **Cassage / édition** — `ServerProtectionManager` bloque `BlockBreakEvent` et
   `SignChangeEvent` sur tout panneau du monde des terrains, **sauf** pour un joueur en mode
   build avec la permission admin. Message FR : « Ces panneaux font partie du terrain importé… ».

## 4. Journal au démarrage

```
[Courts] Loaded terrain.schem — 49x21x52, 18246 solid blocks…
[Courts] Orientation: 90 deg rotation applied on paste - footprint 52x49 - hoop axis east-west
         - attachables placed in a second pass: 360 - block entities to restore: 216 (216 sign(s)).
[Courts] Court centre: offset from the paste origin = 26.00, 24.50 (middle of the footprint ...).
[Courts] Calibration: deck surface = local Y 3 …
```

Si un jour le terrain est de nouveau à l'envers, `rotation: 0` suffit à revenir en arrière
(aucun rebuild nécessaire) ; `90` et `270` sont interchangeables (l'un est l'autre à 180°).

## 5. Tests ajoutés (95 au total)

| Test | Vérifie |
|---|---|
| `bundledTerrainShipsItsSignsAndAttachables` | 216 panneaux conservés, block entities non perdus, indices attachables triés |
| `rotationTurnsTheCourtWithoutMovingItsCalibration` | rotation 180 = empreinte et hauteur du plancher inchangées, 90 → repli sur 180 |
| `signTextIsUnwrappedFromJson` | `{"text":"Hello arena"}` → `Hello arena`, texte brut conservé |

---

## 6. Têtes custom : le bloc **et** la peau

### Le bug du format Sponge v2

`terrainsseize.schem` vient d'Axiom 6.1.3 : c'est du Sponge **v2**, où la charge utile des block
entities est écrite **à plat** dans le compound, sans le sous-compound `Data` du Sponge v3.

```
v3 (FAWE 2.16.1)                    v2 (Axiom / FAWE 2.6)
minecraft:sign {                    minecraft:sign {
  Pos: [37, 2, 30]                    Pos: [37, 2, 30]
  Data: { front_text: {…} }           front_text: {…}        <- pas de Data
}                                   }
```

`CourtSchematic` ne lisait que `Data` : sur ce fichier il récupérait une carte **vide**. Résultat :
le panneau revenait **blanc** et la tête revenait en **Steve**, correctement orientée mais sans sa
peau. `blockEntityPayload(...)` lit désormais `Data` s'il existe, sinon **tous les champs du
compound sauf** `Id`, `Pos`, `x`, `y`, `z` et `keepPacked`.

### Les deux moitiés d'une tête

| Moitié | Où elle vit | Ce que le collage fait |
|---|---|---|
| le **bloc** | `player_head[powered=true,rotation=11]` | écrit tel quel par la palette → l'orientation et l'état `powered` sont conservés |
| la **peau** | `profile` du block entity | ré-injectée par `SchematicHeads` après le collage |

`SchematicHeads` reconstruit le profil dans cet ordre, du plus sûr au plus lent :

1. **la texture du fichier** (`profile.properties[textures].value` + `signature`) → profil monté
   directement, **aucun appel réseau**, la peau exacte du build ;
2. **le nom** (`profile.name`) → `getOfflinePlayerIfCached` puis `createProfile(name)
   .completeFromCache()` : le `usercache.json` du serveur répond hors ligne ;
3. **en dernier recours** le nom est résolu **une seule fois, en tâche de fond** (jamais sur le
   thread principal) et la passe de décoration suivante ré-applique la peau
   (`applyBlockEntities` est rejoué à chaque passage du watchdog, ce qui couvre aussi les panneaux
   remis en place qui repartent vierges).

Le skin est posé via `Skull#setProfile(ResolvableProfile.resolvableProfile(profil))`, puis
`BlockState#update(true, false)` — l'état du bloc n'est pas recalculé, donc la rotation ne bouge
pas.

### Résultat sur `terrainsseize.schem`

| Position | Bloc | Profil |
|---|---|---|
| `[57, 3, 50]` | `player_head[powered=true, rotation=11]` | `maxlananas` |
| `[56, 3, 54]` | `player_head[powered=false, rotation=14]` | `friandise` |

## 7. Palette blindée : plus de mur de pierre au milieu du terrain

Avant : une entrée de palette impossible à créer (bloc d'une version plus récente, propriété
supprimée, faute de frappe dans un fichier édité à la main) tombait dans un `catch
(IllegalArgumentException)` et devenait **`STONE`**. Un seul bloc inconnu posait donc un bloc de
pierre plein dans le terrain — exactement le « bloc illégal qui casse le terrain ».

Maintenant, `CourtSchematic#resolutionCandidates` produit une **cascade** :

| # | Candidat | Exemple |
|---|---|---|
| 1 | l'état exact | `oak_stairs[facing=north,bogus=1]` |
| 2 | une propriété retirée (une par une) | `oak_stairs[bogus=1]`, `oak_stairs[facing=north]` |
| 3 | le matériau nu (état par défaut du serveur) | `oak_stairs` |
| 4 | **air** + une ligne de log avec le nom exact | — |

Un seul `setBlockData` peut encore échouer à l'exécution : il est dans un `try/catch`
(`writeFailures()`), donc une entrée hostile coûte **un** bloc et jamais l'arène. `/bb arena
schematic info` et `paletteNotes()` donnent la liste de tout ce qui a été réécrit ou abandonné.

### Tests ajoutés (round 39)

| Test | Vérifie |
|---|---|
| `streetCourtKeepsItsSignsAndItsCustomHeads` | charge utile **à plat** : texte `@sseize`, light_blue brillant, `friandise` + `maxlananas` |
| `flatSpongeV2PayloadsSurviveTheLoad` | fichier v2 synthétique : texte du panneau + valeur/signature de texture |
| `anIllegalBlockCanNeverAbortACourtPaste` | l'ordre de la cascade de résolution |
| `everyBlockOfBothTerrainsExistsOnThisServer` | les 85 matériaux des deux fichiers existent |
| `bundledStreetCourtIsTheSecondTerrainOfThePool` | calibrage complet du second terrain |
