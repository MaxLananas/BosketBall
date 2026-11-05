# FastAsyncWorldEdit — fichier complet pret a coller

**Le fichier pret a l'emploi est `fawe-config.yml`** (dans ce meme dossier) : remplace
`plugins/FastAsyncWorldEdit/config.yml` par celui-ci, puis redemarre.

Il reprend **tous tes commentaires d'origine** et marque chaque valeur modifiee par
`# (BOSKETBALL) ...` avec la raison. Le YAML a ete verifie : il se parse sans erreur.

## Les valeurs changees (14)

| Cle | Avant | Apres | Pourquoi |
|---|---|---|---|
| `region-restrictions` | `true` | **`false`** | Aucun plugin de regions installe (pas de WorldGuard, pas de PlotSquared) : chaque bloc edite payait un test de permission pour rien. |
| `max-memory-percent` | `95` | **`75`** | A 95 % de 3,5 Go, FAWE ne coupe qu'au bord de l'OOM — le serveur est deja en train de mourir. |
| `slower-memory-percent` | `80` | **`65`** | Doit rester sous le precedent : FAWE ralentit ses chargements des 65 %, pas a 80. |
| `snapshot-update-notifications` | `true` | **`false`** | Un appel reseau et du bruit console a chaque demarrage. |
| `release-update-notifications` | `true` | **`false`** | C'est la ligne `A new release ... is available` de tes logs. |
| `notify-update-ingame` | `true` | **`false`** | Plus de rappel de mise a jour en jeu. |
| `extent.debug` | `true` | **`false`** | Log de debug sur les extents tiers : du bruit pur. |
| `queue.target-size` | `16` | **`8`** | Recommandation FAWE : 4 x `parallel-threads` (= 2). Moins de memoire, meme vitesse. |
| `queue.preload-chunk-count` | `512` | **`128`** | 512 chunks precharges = des centaines de Mo sur un heap de 3,5 Go. |
| `history.small-edits` | `false` | **`true`** | Tes edits tiennent tous dans 4096x256x4096 (courts 49x21x52) : −10 % d'historique. |
| `limits.default.max-changes` | `50000000` | **`20000000`** | 50 M de blocs peut allouer plusieurs Go. 20 M ≈ 400 courts entiers. |
| `limits.default.max-history-mb` | `-1` | **`1024`** | Plafond par joueur au lieu d'une croissance illimitee sur une longue session. |
| `experimental.use-vector-api` | `false` | **`true`** | *(optionnel)* Ta ligne `java` charge deja `jdk.incubator.vector`. Si un edit se comporte bizarrement, remets `false`. |

## Ce que j'ai volontairement laisse tel quel

| Cle | Valeur | Raison |
|---|---|---|
| `history.use-database` | `true` | FAWE dit lui-meme dans le fichier « Does not impact performance ». Le passer a `false` ferait perdre l'inspection/rollback de l'historique pour rien. |
| `history.store-redo` | `true` | Le passer a `false` reduit l'historique de 20 % mais **supprime `//redo`**. Tu construis : je ne touche pas a ton outil. Mets-le a `false` si tu ne t'en sers jamais. |
| `queue.parallel-threads` | `2` | C'est exactement ton nombre de cœurs. Monter a 4 ne cree pas de CPU, juste du context switching. |
| `queue.pool` / `queue.async-chunk-load-write` | `true` | Deja le bon compromis ; avec `preload-chunk-count` a 128, plus de risque d'OOM. |
| `lighting.async` / `mode: 1` / `remove-first` | `true` / `1` / `true` | Relight optimal asynchrone : c'est le bon reglage, ne pas toucher. |
| `clipboard.use-disk` / `history.use-disk` | `true` | C'est ce qui te sauve la RAM. |
| `limits.default.fast-placement` / `speed-reduction` | `true` / `0` | Un `speed-reduction` non nul ajoute un delai **par bloc** : exactement l'inverse du but. |
| `tick-limiter.enabled` | `false` | Tu n'as pas de machines a lag ; l'activer ajouterait des checks pour rien. |

## `worldedit-config.yml`

Rien a optimiser dedans : les limites qui comptent sont lues dans la config FAWE
(le fichier le dit lui-meme en commentaire). Laisse-le tel quel.

## Ordre d'application

1. Sauvegarde ton `config.yml` actuel.
2. Copie `fawe-config.yml` a la place.
3. Redemarre le serveur (pas `/reload` : FAWE recharge mal ses files d'attente).
4. Si tu veux revenir en arriere, remets ta sauvegarde : rien d'autre n'a ete touche.
