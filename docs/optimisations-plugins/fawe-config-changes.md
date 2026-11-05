# FastAsyncWorldEdit — les 12 valeurs a changer, et pourquoi

Ton `plugins/FastAsyncWorldEdit/config.yml` est deja bien regle sur l'essentiel
(`clipboard.use-disk: true`, `history.use-disk: true`, `parallel-threads: 2`, `lighting.async: true`).
Ce qui reste, ce sont des **garde-fous memoire** : sur 3,5 Go de heap, FAWE est la seule chose
capable de tout manger en une commande. FAWE ne coute rien quand personne n'edite.

Edite les valeurs une par une (garde tes commentaires), puis `/reload confirm` ou redemarre.

| Cle | Actuel | Nouveau | Pourquoi |
|---|---|---|---|
| `max-memory-percent` | `95` | **`75`** | A 95 % de 3,5 Go, FAWE ne coupe qu'au bord de l'OOM — le serveur est deja en train de mourir. 75 % laisse de la marge au plugin et aux joueurs. |
| `slower-memory-percent` | `80` | **`65`** | Doit rester sous le precedent. FAWE ralentit ses chargements asynchrones des 65 % au lieu d'attendre 80. |
| `region-restrictions` | `true` | **`false`** | Aucun plugin de regions installe (pas de WorldGuard, pas de PlotSquared). Chaque bloc edite paye quand meme un test de permission : du travail pour rien. |
| `queue.preload-chunk-count` | `512` | **`128`** | 512 chunks precharges = des centaines de Mo sur un heap de 3,5 Go. 128 suffit tres largement pour des edits de la taille d'un court (49x21x52). |
| `queue.target-size` | `16` | **`8`** | Recommandation FAWE : 4 x `parallel-threads` (= 2). Moins de memoire, meme vitesse. |
| `history.small-edits` | `false` | **`true`** | Tous tes edits tiennent dans 4096x256x4096 (tes courts font 49x21x52) : l'historique devient ~10 % plus petit. |
| `history.use-database` | `true` | **`false`** | Une base SQLite ecrite a chaque edit pour permettre l'inspection/rollback. Tu n'en as pas besoin : ca ne fait que de l'I/O. |
| `history.store-redo` | `true` | **`false`** *(optionnel)* | −20 % de taille d'historique. A ne pas faire si tu utilises `//redo` en construction. |
| `limits.default.max-changes` | `50000000` | **`20000000`** | 50 M de blocs modifies peut allouer plusieurs Go. 20 M = 40 fois un court entier, largement suffisant. |
| `limits.default.max-history-mb` | `-1` | **`1024`** | Plafonne l'historique par joueur : plus de croissance illimitee sur une longue session de build. |
| `enabled-components.snapshot-update-notifications` | `true` | **`false`** | Chaque demarrage fait un appel reseau et te sort le `A new release ... is available` que tu as dans tes logs. |
| `enabled-components.release-update-notifications` + `notify-update-ingame` | `true` | **`false`** | Idem, et plus de message a la connexion. |
| `extent.debug` | `true` | **`false`** | Log de debug sur les extents tiers : du bruit dans la console. |
| `experimental.use-vector-api` | `false` | **`true`** *(optionnel)* | Ta ligne `java` charge deja `jdk.incubator.vector` (`--add-modules`). Accelere certaines operations. Si un edit se comporte bizarrement, remets `false`. |

## A ne pas toucher

- `queue.parallel-threads: 2` — c'est exactement ton nombre de cœurs. Ne monte pas a 4 : tu n'as pas
  4 threads, tu auras juste plus de context switching.
- `lighting.async: true` / `mode: 1` / `remove-first: true` — c'est le bon reglage (relight optimal,
  pas « tout relight »).
- `clipboard.use-disk: true` et `history.use-disk: true` — c'est ce qui te sauve la RAM.
- `tunnel-split-size` et `maximum-tunnel-packet-size` — necessaires aux gros pastes client.
- `limits.default.fast-placement: true` et `speed-reduction: 0` — un `speed-reduction` non nul fait
  exactement l'inverse de ce que tu veux (il ajoute un delai par bloc).

## `worldedit-config.yml`

Rien a optimiser la dedans : les limites qui comptent sont lues dans la config FAWE
(les commentaires du fichier le disent eux-memes). Tu peux laisser tel quel.

Seule exception si tu veux reduire le bruit : `debugging.trace-unflushed-sessions: false` (deja le cas)
et `logging.log-commands: false` (deja le cas).
