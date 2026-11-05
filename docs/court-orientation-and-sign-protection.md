# Sens du terrain, panneaux protégés et nametag du lobby

> Livré sur `arena/a1145fcf-bosketball`. Build de référence : **226 classes, 0 erreur · 95 tests OK ·
> pack 3 796 641 B · jar 4 589 845 B**.

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
   ré-appliqués. `CourtSchematic` conserve désormais chaque block entity (position + `Data`) et
   `applyBlockEntities(...)` restaure côté avant/arrière, couleur, texte brillant et `is_waxed`
   juste après le collage (les messages JSON `{"text":"…"}` sont déballés en texte simple).
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
