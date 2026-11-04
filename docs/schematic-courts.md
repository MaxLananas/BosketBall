# Terrains instanciés depuis `terrain.schem` (50 arènes privées)

Chaque match 1v1 / 2v2 / 3v3 / 5v5 **et** chaque session solo (entraînement, concours à 3 points)
reçoit **sa propre copie** de ton terrain, collée dans un monde vide dédié, très loin du spawn.
Aucun terrain n'est jamais partagé entre deux joueurs.

## Pourquoi ça ne marchait pas avant (corrigé)

Le plugin ne lisait que l'ancien format Sponge **v1** (`Palette` directement à la racine du fichier).
FastAsyncWorldEdit **2.11 et plus récent** — donc ton 2.16.1 — écrit du Sponge **v3**, où tout est
imbriqué dans `Blocks` → `Palette` + `Data`. Résultat : `Unsupported schematic (no Palette)`, le pool
restait à l'arrêt, et `/practice` te renvoyait sur l'ancienne arène générée.

Trois choses ont changé :

1. **Les trois formats sont lus** (v1, v2, v3 — et l'ancien `.schematic` MCEdit est détecté pour
   renvoyer un message clair). Plus besoin de convertir quoi que ce soit.
2. **Plus aucun repli sur l'ancienne arène.** Dès que `schematic-courts.enabled: true`, l'arène
   générée `main` et le monde `bosketball_practice` ne sont plus jamais distribués. Si `terrain.schem`
   n'est pas encore lisible, tu reçois un message explicite au lieu d'une vieille arène.
3. **Le monde `bosketball_practice` est supprimé** au démarrage (dossier compris) —
   `retire-legacy-practice-world: false` si tu veux le garder.

## Installation en 4 étapes

1. Ton build est terminé dans le monde de ton choix.
2. `//copy` — le plugin compense automatiquement l'origine « centrée » d'un copy au milieu.
3. `//schem save terrain` (FastAsyncWorldEdit), **ou** dépose le fichier ici :
   `plugins/BosketBall/schematics/terrain.schem`
   Le plugin cherche aussi tout seul dans `plugins/FastAsyncWorldEdit/schematics/`
   et `plugins/WorldEdit/schematics/` : tu n'as rien à déplacer si FAWE les a déjà sauvegardés là.
4. `/bb arena schematic reload` (ou redémarre le serveur).

## Ce que le plugin calcule tout seul

| Élément | Comment |
|---|---|
| Coins du terrain jouable | `half-width` / `half-length` ne font que **réduire** le rectangle auto-ajusté au build — jamais le dépasser |
| Centre exact du terrain | le point où tu te tenais au `//copy` (tag `Offset`), pas le coin du collage |
| `floorY` (sol sur lequel rebondit la balle) | la couche pleine la plus haute qui couvre ≥ 90 % de l'emprise (un mât, un panneau ou un toit ne la déplace plus) |
| Orientation des paniers | `hoop-facing: auto` = le **côté long** du build (un terrain est toujours plus long que large) ; `east-west` / `north-south` pour forcer |
| `origin-y` | c'est le **niveau du sol** (`64` par défaut) : le build est décalé pour que son plancher tombe pile dessus |
| Paniers 3D + shot clock | posés par le plugin (`rim-relative-y`, `rim-inset`), aucun modèle dans ton schematic — c'est ce que tu voulais |
| Lignes de terrain | déjà dans ton build → le plugin ne rajoute pas ses 80 BlockDisplays |
| Cage de collision (balle + joueurs) | ajustée au rectangle jouable |
| Hauteur du plafond de jeu | 16,5 blocs au-dessus du sol |

Rien de tout ça n'est « au hasard » : si un réglage est faux, `/bb arena schematic info` te donne les
valeurs réellement calculées, et tout se règle dans `config.yml` → `schematic-courts:`.

## Performance (le point important pour 25 joueurs en même temps)

- **Le fichier n'est lu qu'une fois.** `terrain.schem` est décodé en tableaux plats d'indices de
  palette (~4 octets par bloc). Coller une arène = un simple parcours de tableau, **aucune I/O,
  aucun parsing, aucune dépendance FAWE**.
- **Budget par tick.** Aucun tick n'écrit plus de `max-blocks-per-tick` blocs (40 000 par défaut,
  ≈ 1 ms). Une arène peut donc s'étaler sur plusieurs ticks sans jamais créer de lag spike.
- **Pré-chauffage.** Deux arènes propres sont maintenues prêtes en permanence : le joueur suivant
  ne paie pas le collage.
- **Nettoyage.** 30 s après la fin d'un match (`unpaste-delay-seconds`), l'arène est recollée à
  l'identique : un terrain modifié ne peut jamais être réutilisé tel quel.
- **Pool partagé.** 50 arènes pour 25 joueurs, avec priorité aux matchs : `busy` dans
  `/bb arena schematic info` te dit combien sont occupées.
- **Recherche automatique du fichier.** Le plugin fouille `plugins/BosketBall/schematics/`,
  `plugins/FastAsyncWorldEdit/schematics/`, `plugins/WorldEdit/schematics/` **et leurs
  sous-dossiers** : un `//schem save terrain` suffit, rien à déplacer.
- **Rechargement automatique.** Si le fichier apparaît (ou devient lisible) plus tard, le pool se
  lance tout seul dans les 10 secondes — plus besoin de redémarrer.

## Vérifier en jeu

```
/bb arena schematic info
```
Tu dois voir `Schematic: terrain … (N blocks, Sponge v3)`, la position du terrain #1, l'axe des
paniers et la taille du rectangle jouable. Si quelque chose cloche, c'est ici que ça se voit avant
de lancer un match.

## Commandes

```
/bb arena schematic info        état du pool, taille du schematic, floorY calculé, axes
/bb arena schematic reload      relit le .schem et réaligne les 50 arènes
/bb arena schematic repaste     recolle toutes les arènes libres
/bb arena schematic goto <n>    se téléporter sur l'arène n° n (calibrage)
```

## Réglages utiles (`config.yml`)

```yaml
schematic-courts:
  instances: 50            # nombre d'arènes privées
  spacing-blocks: 384      # écart entre deux arènes (doit dépasser la taille du build)
  origin-x: 100000         # premier terrain, très loin du spawn
  origin-z: 100000
  world-name: bosketball_courts
  floor-relative-y: 0      # +1 pour monter la physique d'un bloc si le sol paraît enterré
  rim-relative-y: 3        # hauteur du cerceau au-dessus du sol (3 = 3.05 m)
  rim-inset: 1.25          # distance du cerceau à la ligne de fond
  hoop-facing: auto        # auto | north-south | east-west
  max-blocks-per-tick: 40000
```

Si tu changes la taille/forme du build : `//schem save terrain` puis
`/bb arena schematic reload`. Aucun redémarrage n'est nécessaire.

## Ce que les tests couvrent

La suite JUnit (60 tests) écrit un **vrai fichier `.schem` gzippé au format FAWE** puis le décode
avec le lecteur de production, et vérifie : dimensions, offsets d'un copy centré, détection du sol,
palette, comptage des blocs pleins, rejet propre d'un fichier corrompu (message d'erreur explicite),
valeurs par défaut de la config, et absence de chevauchement des 50 emplacements.
