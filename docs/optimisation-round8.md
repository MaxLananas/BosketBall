# Optimisation round 8 — tes chiffres après le round 7

Mesures console du 5 nov. 2026, 12:35, serveur vide, Paper 26.2-135, Temurin 25, 2 threads,
**AxiomPaper + FAWE conservés** (ta décision, plus jamais remise en cause par l'audit).

## 1. Ce que le round 7 a réellement rapporté

| Poste (`/bb perf`, 0 joueur) | Avant (11:57) | Après (12:36) | Effet |
|---|---|---|---|
| `schematic` | 0,055 ms · **max 1,85** | **0,009 ms · max 0,018** | ×6 plus léger, pic ×100 plus court |
| `halloffame` | 0,025 ms · **max 2,32** | **0,001 ms · max 0,002** | podium muet sans spectateur |
| `sidebar` | 0,014 ms | 0,002 ms | inchangé par ailleurs, juste moins de bruit |
| **total plugin** | 0,194 ms · **worst 2,40 ms** | **0,119 ms · worst 0,58 ms** | le pire tick du plugin est divisé par 4 |
| Tranche de paste | 40 000 blocs · **214 ms** | 4 000 blocs · **18 ms** puis ~2 ms | plus de gel d'1/5 s |
| Heap au boot | 813 Mo | **429 Mo** | démarrage propre |
| `stress 50` (warm-up exclu) | 0,217 ms/tick (JIT froid) | **0,078 ms/tick** | projection honnête |

Le `max 10,6 ms` de spark venait des tranches de 40 000 blocs : elles ont disparu.

## 2. Ce qui restait en tête à 0 joueur, et qui est corrigé ici

| Poste | Mesure | Pourquoi c'était du gaspillage | Correctif |
|---|---|---|---|
| `crate` | **0,037 ms** (max 0,143) | L'anneau d'ouverture téléporte 12 `ItemDisplay` + reconstruit 12 `Transformation` **chaque tick**, dans un lobby vide | `Viewers.anyWithin(baseLocation, 48)` : pas d'animation si personne à 48 blocs |
| `tutorial` | **0,028 ms** (max 0,366) | `new ArrayList<>(introTicks.entrySet())` alloué à chaque tick + téléport du hologramme toutes les 10 ticks, sans joueur | sortie immédiate si personne en ligne ; bob seulement si un joueur est à 48 blocs |
| `holo-anim` | **0,016 ms** (max 0,120) | Transformations + rebuild du texte classement pour 2 hologrammes que personne ne regarde | `anyHologramWatched()` : passe complète seulement si un joueur est à 64 blocs d'un hologramme |
| Première tranche de paste | **18 ms** à 4000 blocs | La première tranche payait le plafond avant de connaître le débit du CPU | Première tranche à **512 blocs** (~3 ms) : elle mesure, puis la taille monte à ce que 2 ms permettent |

Attendu au prochain `/bb perf` serveur vide : `crate`, `tutorial` et `holo-anim` sous **0,005 ms**,
et un total plugin sous ~0,02 ms/tick. La marge devient telle que le plugin ne sera plus jamais
le facteur limitant, même à 50 joueurs.

## 3. Axiom + FAWE : conservés, et l'audit se tait

Tu en as besoin pour construire des cartes : c'est acté.

- L'audit ne les classe **plus** comme des problèmes et n'écrit plus « RETIRER ».
- Il les **signale une fois, sans consigne** : « conserves (choix du serveur). BosketBall ne s'en
  sert pas pour ses courts ».
- Conséquence technique à connaître, pas une critique : FAWE garde des chunks et des schematics en
  mémoire, et Axiom expose des commandes de world-edit. Comme ils tickent sur le **même thread
  serveur**, leur coût apparaît dans `/spark` sous leurs propres frames, pas dans `/bb perf`. Pour
  les mesurer : `/spark profiler start --timeout 30` puis `/bb stress 100 1200`.
- Aucun code du plugin ne dépend d'eux : si tu les retires un jour, les courts continuent de se
  coller exactement pareil.

## 4. Protocole de vérification

```
/bb audit            # doit dire "RAS" ou ne lister que de vrais points ; Axiom/FAWE en note
/bb perf reset
/bb perf             # crate / tutorial / holo-anim doivent être sous 0.005 ms
/bb stress 50        # ~0.08 ms/tick, projection honnête (40 ticks de warm-up exclus)
/bb stress 100 1200  # la vraie projection 100 joueurs, comparable d'un run à l'autre
```

Seuils (inchangés) : MSPT médian < 15 ms, p95 < 30 ms, TPS 20.00, 0 GC vieille génération,
aucune étape `/bb perf` > 5 ms.
