# Baseline de performance — 5 nov. 2026, 11:36 (spark)

Relevé fourni par la console (`/spark tps` + `/spark profiler`), Paper `26.2-135`, Temurin 25.0.5,
2 threads CPU visibles, heap 3,5 Go (nouveaux flags actifs), uptime 3 min, **aucun joueur en jeu**.

## 1. Ce que disent les chiffres

| Métrique | Valeur | Lecture |
|---|---|---|
| TPS | 20.00 / 19.98 / 19.99 (1/5/15 min) | parfait |
| MSPT min / méd / p95 / max | 0.15 / **0.24** / 2.46 / **284 ms** | médiane à 0,24 ms = serveur au repos ; le p95 à 2,46 ms montre que seuls quelques ticks travaillent |
| CPU process | 3.19 % (1 min) / 5.46 % (15 min) | sur 2 threads = ~1,1 % d'un cœur |
| Mémoire heap | **813 Mo / 3,5 Go** (22,7 %) | énorme marge |
| GC | 10 jeunes (49,5 ms cumulés), **0 vieille génération** | pression mémoire quasi nulle |
| Entités | 198 (133 block_display, 31 item_display, 10 text_display, 5 mannequin, 15 item) | cohérent : 4 courts pré-chauffés + lobby |
| Chunks chargés | 13 | 9 dans `bosketball_courts`, 4 dans `world` |
| Réseau | ~0 | aucun joueur connecté |

**Profiler (1200 ticks, 61 s)** : le thread serveur passe **99,72 % du temps parké** (inactif).
Le travail plugin représente **0,22 %**, réparti ainsi :

| Poste | Part | Soit |
|---|---|---|
| `SchematicCourtPool.tick()` | 0,14 % | watchdog décorations + boucle des 50 slots |
| `HallOfFamePodium.tick()` | 0,08 % | particules du podium |
| `AxiomPaper.tick()` | 0,01 % | plugin de build encore chargé |

Conclusion : **le plugin n’est pas le goulot d’étranglement, et la config serveur est saine.**
À 0 joueur, il n’y a simplement rien à optimiser côté code : les 0,22 % sont du bruit de mesure.

## 2. Trois anomalies relevées dans le rapport

### a) `AxiomPaper` est toujours chargé
Le profiler affiche `com.moulberry.axiom.AxiomPaper.tick()`. Le script `apply.sh` déplace les jars
dans `plugins-disabled/` — soit il n’a pas été lancé, soit les jars ont été re-uploadés.
→ Déplace à la main `plugins/AxiomPaper*.jar` **et** `plugins/FastAsyncWorldEdit*.jar` hors de
`plugins/`. C’est le gain n°1 (RAM, boot, threads), et Axiom n’a aucun rôle en jeu.

### b) Le pic à 284 ms = le paste d’un court
Un court fait ~18 246 blocs et `max-blocks-per-tick` était à **40 000** : un court entier était
écrit **en un seul tick**. D’où le unique pic à 284 ms (invisible ici car TPS resté à 20).
→ Corrigé par défaut dans le plugin : **`max-blocks-per-tick: 4000`** (~5 ticks par court, ~60 ms
chacun). Sur ton install existante, édite `plugins/BosketBall/config.yml` :

```yaml
schematic-courts:
  max-blocks-per-tick: 4000
```

Un avertissement console apparaît désormais si une tranche de paste dépasse 100 ms, avec la valeur
à baisser.

### c) 15 entités `item` dans `bosketball_courts`
Ce sont des items lâchés sur les terrains pré-chauffés. Le watchdog ne supprime que les items de
décoration connus. Rien de grave (ils despawn), mais si tu en vois encore après une soirée :
`/kill @e[type=item]` ciblé sur le monde `bosketball_courts`.

## 3. Le vrai test : 50 joueurs

Ce relevé ne prouve **qu’une chose** : au repos, tout est propre. Les 50 joueurs se jouent sur
trois postes : entités/joueur, envoi de chunks au join, et le tick des matchs.

### Protocole

```
/bb perf reset                                  # compteurs du profiler interne à zéro
# … laisse tourner pendant le pic (lobby rempli + 3-4 matchs en cours, 5 minutes) …
/bb perf                                        # coût par sous-système, moyenne et max
/spark profiler --timeout 120                   # puis /spark profiler open
/spark tps
```

`/bb perf` liste maintenant chaque étape isolée du pipeline : `match`, `schematic`, `sidebar`,
`tablist`, `nametag`, `holo-anim`, `playtime`, `autosave`… C’est la vue la plus utile pour
trancher « c’est le plugin ou le serveur ».

### Seuils d’acceptation (à 50 joueurs)

| Indicateur | OK | À surveiller | Il faut agir |
|---|---|---|---|
| MSPT médiane | < 15 ms | 15–30 ms | > 30 ms |
| MSPT p95 | < 30 ms | 30–45 ms | > 45 ms |
| TPS | 20.00 | 19.9 | < 19.8 |
| CPU process | < 60 % | 60–85 % | > 85 % |
| Heap utilisé | < 2,4 Go | 2,4–3,0 Go | > 3,0 Go (monte le heap à 4096M) |
| GC vieille génération | 0 | < 5 collectes/h | > 5/h |
| Étapes `/bb perf` | aucune > 5 ms | une entre 5 et 10 ms | une > 10 ms |

### Leviers, dans l’ordre, si ça chauffe

1. `view-distance: 5` (server.properties) — 169 → 121 chunks/joueur.
2. `schematic-courts.max-blocks-per-tick: 2000` — paste encore plus étalé.
3. `chat.match-chat-isolated` inchangé ; baisser `leaderboards.holo-refresh-seconds` (rafraîchit les
   hologrammes moins souvent, ex. 10 → 20 s).
4. Passer le heap à `-Xms4096M -Xmx4096M` (il reste de la place : 6 Go de conteneur).
5. Demander un 3e vCPU — à 1,5 vCPU, 50 joueurs tiennent, mais sans marge de pic.

### À ne PAS toucher

`simulation-distance: 4` (le ballon sortirait du rayon de tick), `enable-player-collisions: true`
(défense au contact), `entities.armor-stands.tick: true` (rigs du plugin), `pause-when-empty-seconds`.

## 4. Baseline à conserver

| Date | Joueurs | MSPT méd | MSPT p95 | Heap | CPU | Note |
|---|---|---|---|---|---|---|
| 2026-11-05 11:36 | 0 | 0.24 ms | 2.46 ms | 813 Mo / 3,5 Go | 3,2 % | référence au repos |
| à remplir | 50 | | | | | test de charge |

---

Pour fabriquer cette charge sans joueur : `/bb stress 50` (voir `docs/stress-test.md`).
