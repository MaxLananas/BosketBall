# NPC invisibles + Axiom/FAWE : ce qu'il faut changer

## 1. Les NPC disparaissent a ~32 blocs — oui, c'est ma config

Reponse franche : **c'est le `spigot.yml` que je t'ai livre au round 4.** Il contenait :

```yaml
entity-tracking-range:
  players: 64
  animals: 32
  monsters: 32
  misc: 32
  display: 64     # defaut Spigot : 128
  other: 32       # defaut Spigot : 64
```

Les mannequins du Hall of Fame (et tout armor stand / display) sont classes dans `misc`, `other`
ou `display`. Avec `32`, le client ne recoit l'entite qu'a 32 blocs : il faut effectivement
marcher dessus pour la voir. Le plugin n'y est pour rien, la distance de vue (6 chunks = 96 blocs)
non plus.

### Le correctif, dans ton `spigot.yml` (celui du serveur, pas du depot)

Cherche `entity-tracking-range:` sous `world-settings: default:` et mets :

```yaml
    entity-tracking-range:
      players: 96
      animals: 48
      monsters: 48
      misc: 96
      display: 128
      other: 96
```

96 blocs = la bulle complete de `view-distance: 6` (6 x 16). Au-dela, le chunk n'est meme pas
charge : c'est donc le maximum utile. Cout reel : quelques paquets de spawn en plus, sur ~5 entites.

Puis redemarre (cette cle n'est pas rechargee par `/reload`).

### Cote plugin (deja fait, de mon cote)

Les gardes « personne ne regarde » que j'avais posees au round 8 etaient calees sur 48-64 blocs :
un objet *visible* a 80 blocs se serait figé. Elles sont passees a **96 blocs** partout
(podium, caisse, hologrammes, tutoriel), donc coherentes avec la nouvelle portee.

## 2. AxiomPaper

Fichier pret a coller : **`axiom-paper-config.yml`** (remplace `plugins/AxiomPaper/config.yml`).

Les 4 changements qui comptent :

| Cle | Avant | Apres | Pourquoi |
|---|---|---|---|
| `max-chunk-sends-per-tick` | `0` (illimite) | `4` | Un client en train de pousser une zone envoyait un nombre illimite de chunks par tick, sur le thread serveur. |
| `max-chunk-relights-per-tick` | `0` | `4` | Le relight est l'operation la plus chere de Minecraft. |
| `max-chunk-load-distance` | `256` (4096 blocs) | `32` (512 blocs) | 256 chunks de rayon, c'est des milliers de chunks en RAM pour un seul builder. |
| `block-buffer-rate-limit` | `0` (defaut) | `40` | Empeche un client de vider son buffer d'un coup sur le thread principal. |

Plus : `blacklist-entities` protege tes entites du plugin (mannequins, `item_display`,
`block_display`, `text_display`) d'une manipulation client, et `log-core-protect-changes: false`
puisque CoreProtect n'est pas installe.

## 3. FastAsyncWorldEdit

Detail complet : **`fawe-config-changes.md`** (12 valeurs, une ligne par valeur).

Les deux plus importantes sur ton heap de 3,5 Go :

- `max-memory-percent: 95` → **`75`** : a 95 %, FAWE ne coupe qu'au bord de l'OOM.
- `queue.preload-chunk-count: 512` → **`128`** : 512 chunks precharges = des centaines de Mo.

Et un vrai gain de vitesse pur : `region-restrictions: true` → **`false`**, puisque tu n'as aucun
plugin de regions installe — aujourd'hui chaque bloc edite paye un test de permission inutile.

## 4. Ordre d'application

1. `spigot.yml` (NPC) → redemarrage.
2. `plugins/AxiomPaper/config.yml` → redemarrage.
3. `plugins/FastAsyncWorldEdit/config.yml` → redemarrage.

Aucune de ces trois modifications ne touche le plugin BosketBall : tu peux les tester separement.
