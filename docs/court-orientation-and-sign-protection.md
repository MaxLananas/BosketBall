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

## 2. Sens du terrain : rotation au collage

Le build importé était collé à l'envers. Nouvelle option, `config.yml` :

```yaml
schematic-courts:
  rotation: 180   # 0 = tel quel, 180 = retourné (défaut)
```

* La rotation est appliquée **au collage**, pas au fichier : `terrain.schem` reste intact.
* Seuls `0` et `180` sont acceptés (`SchematicCourts.normalizeRotation`) : l'empreinte du build
  ne change pas, donc la calibration auto (hauteur du plancher, paniers, rectangle de jeu) reste
  **exactement** la même — c'est ce qui était déjà validé.
* Les coordonnées sont retournées (`x → width-1-x`, `z → length-1-z`) et chaque blockstate
  directionnel est tourné via `BlockData#rotate(StructureRotation.CLOCKWISE_180)` — escaliers,
  panneaux muraux, rails, torches murales… se retrouvent dans le bon sens.
* Les block entities (panneaux) suivent la même transformation de position.

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
[Courts] Orientation: 180 deg rotation applied on paste - attachables (signs, torches, ...)
         placed in a second pass: 360 - block entities to restore: 216 (216 sign(s)).
[Courts] Calibration: deck surface = local Y 3 …
```

Si un jour le terrain est de nouveau à l'envers, `rotation: 0` suffit à revenir en arrière
(aucun rebuild nécessaire).

## 5. Tests ajoutés (95 au total)

| Test | Vérifie |
|---|---|
| `bundledTerrainShipsItsSignsAndAttachables` | 216 panneaux conservés, block entities non perdus, indices attachables triés |
| `rotationTurnsTheCourtWithoutMovingItsCalibration` | rotation 180 = empreinte et hauteur du plancher inchangées, 90 → repli sur 180 |
| `signTextIsUnwrappedFromJson` | `{"text":"Hello arena"}` → `Hello arena`, texte brut conservé |
