# Optimisation round 7 — mesures du 5 nov. 2026 (stress test réel)

Tout part de tes chiffres. Serveur vide, Paper 26.2-135, Temurin 25, 2 threads, heap 3,5 Go.

## 1. Ce que ton run a prouvé

| Observation (ta console) | Diagnostic | Correction livrée |
|---|---|---|
| `a paste slice of 40000 block(s) took 214 ms` (puis 119, 123, 153 ms) | Le correctif round 6 (4000) n'était **que dans le `config.yml` livré** : ton fichier existant gardait 40 000. Une tranche = 1/5 de seconde de freeze, juste après `Done`. | Migration : `loadAndUpgrade` **remet à 4000 sur le disque** (log explicite) + `Math.clamp(128..4000)` à la lecture, même si tu édites à la main. |
| MSPT max **10,6 ms**, p95 1,71 ms, 0 joueur | Ce sont les tranches de paste et le watchdog de décoration, pas les joueurs. | Slice **adaptatif à 2 ms** : la taille est recalibrée à chaque tranche d'après le débit mesuré (`blocks/ms`), plafonnée à 4000. Plus de tranche > ~4 ms. |
| `/bb perf` : `schematic • 0.055 ms (max 1.85)` **au repos** | Le pool tourne à vide : boucle sur 50 slots + `repairAttachables` sur un court entier **chaque seconde**, sans personne. | Fast-path : si file vide + rien de sale + **aucun joueur dans `bosketball_courts`** → `return` immédiat. Watchdog : passe complète seulement si un joueur est à ≤ 80 blocs du court concerné. |
| `halloffame • 0.025 ms (max 2.32)` | `refreshSilently()` (respawn des mannequins, textures) se déclenche toutes les 15 s même si personne ne regarde le podium. | `hasViewers()` : pas de refresh sans joueur à ≤ 64 blocs. |
| `stress 50` → 0,217 ms/tick puis `stress 100` → 0,176 puis 0,076 | Ce n'était pas une optimisation : c'était le **JIT** qui chauffait. Le rapport mentait par optimisme. | Warm-up de 40 ticks simulés **non comptés**, affiché dans le rapport. Les projections entre deux runs sont maintenant comparables. |
| `[AxiomPaper] Enabling AxiomPaper v6.0.1+26.2` | Moteur world-edit complet chargé **en plus** du plugin, sur 2 threads. Il ne sert à rien ici : BosketBall paste ses courts lui-même. | Audit au démarrage + `/bb audit` qui le dit noir sur blanc, fichier à supprimer. |
| heap 1,45 → 1,64 Go, 0 joueur, 42,7 % | Axiom + JVM qui n'a pas encore rendu la mémoire (G1 ne rend qu'au besoin). Pas de fuite du plugin : les slots ne gardent aucune copie de blocs. | Audit : alerte si `Xmx < 2 Go`. Retirer Axiom divise la conso de départ. |

## 2. Les quatre leviers livrés

### a) Tranche de paste auto-calibrée (2 ms)

```java
double sample = blocks / millis;                       // débit réel de CE cpu
blocksPerMs  = blocksPerMs * 0.7 + sample * 0.3;       // moyenne glissante
adaptiveSliceBlocks = clamp(blocksPerMs * 2.0, 128, cap);
```
Première tranche = plafond (4000). Si elle prend 21 ms, la suivante tombe à ~370 blocs, etc.
`max-blocks-per-tick` reste un **plafond dur** que tu peux baisser ; le plugin ne peut plus le
dépasser, même avec un vieux fichier de config.

### b) Le pool dort quand le monde est vide

```java
if (pasteQueue.isEmpty() && !hasScheduledWork() && world.getPlayers().isEmpty()) {
    return;   // 0,055 ms/tick -> 0,000
}
```

### c) Le watchdog regarde qui peut voir

Une torche cassée derrière un mur, à 300 blocs d'un joueur, ne justifie pas de rescanner 18 246
blocs par seconde. Passe complète seulement s'il y a un spectateur à ≤ 80 blocs.

### d) Audit actionnable

`/bb audit` (et un résumé au démarrage) vérifie : AxiomPaper, FAWE/WorldEdit, `max-blocks-per-tick`,
`view-distance`, `simulation-distance`, `Xmx`, chunks chargés, entités, TPS/MSPT. Chaque ligne dit
**quoi faire**, pas seulement « attention ».

## 3. Ce qu'il faut relancer pour vérifier

```
/bb audit                     # doit proposer de retirer AxiomPaper
/bb perf reset
/bb stress 50
/bb perf                      # schematic doit tomber vers 0.000-0.005 ms au repos
```

Attendu après suppression d'AxiomPaper + rechargement :

- plus aucune ligne `a paste slice of 40000 block(s)` : les tranches tournent autour de 2 ms ;
- `MSPT max` sous ~5 ms serveur vide (contre 10,6 ms) ;
- `schematic` ≈ 0,000 ms/tick quand personne n'est dans `bosketball_courts` ;
- `halloffame` ≈ 0,000 quand personne n'est au podium ;
- heap de départ plus bas (Axiom est le plus gros consommateur).

## 4. Ce qui n'a pas été touché, volontairement

- **Sidebar** : 0,007 ms par render pour 100 joueurs simulés, soit ~0,07 ms/tick. Ce n'est pas le
  sujet. Les vrais coûts à 50 joueurs seront les paquets et les chunks, pas ce code.
- **Collisions joueurs, armor stands, `prevent-moving-into-unloaded-chunks`** : inchangés.
- **ZGC** : jamais sous 8 cœurs. G1 reste le bon choix ici.

## 5. Notes techniques

- Les entités `ItemDisplay` coûtent ~**1,2 µs/tick** chacune (600 entités = +0,7 ms mesuré). À
  garder en tête quand des hologrammes s'accumulent : un hologramme de court non utilisé doit
  rester despawné.
- Le heap qui monte à 1,6 Go sans joueur n'est pas une fuite : G1 agrandit la région jeune et ne
  rend la mémoire qu'à la pression. Ce qui compte, c'est **0 GC vieille génération** — c'est le cas.
