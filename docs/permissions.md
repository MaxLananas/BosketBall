# Permissions — arbre complet et groupes

Tous les nœuds sont déclarés dans `plugin.yml` **et** dans `Perms.nodes()` (le test
`everyPermissionNodeIsDeclaredAndGranular` échoue si les deux divergent).

`Perms.has()` résout la hiérarchie pointée : accorder `bosketball.admin` accorde
`bosketball.admin.elo`, `bosketball.admin.coins`, etc. Un refus explicite (`-node`) l'emporte.

## Nœuds

| Nœud | Défaut | Rôle |
|---|---|---|
| `bosketball.play` | true | Jouer (files, courts, menus). |
| `bosketball.build` | op | `/build`, FAWE, Axiom. |
| `bosketball.map` | op | `/bb map`, studio d'arène. |
| `bosketball.staff` | op | Parapluie : tous les `staff.*`. |
| `bosketball.staff.reports` | op | Panneau `/reports`. |
| `bosketball.staff.warn` | op | Avertir / retirer / lister. |
| `bosketball.staff.mute` | op | Mute chat global et contournement. |
| `bosketball.staff.vanish` | op | Vanish. |
| `bosketball.staff.freeze` | op | Freeze joueur et `freezematch`. |
| `bosketball.staff.spectate` | op | Spectateur libre. |
| `bosketball.staff.match` | op | Supervision : forcestart, stop, requeue. |
| `bosketball.staff.bypass` | op | Contourne la protection du lobby et les filtres. |
| `bosketball.admin` | op | Parapluie : tous les `admin.*`. |
| `bosketball.admin.coins` | op | Donner / retirer / fixer des coins. |
| `bosketball.admin.elo` | op | `/bb elo`, `/bb giveelo`. |
| `bosketball.admin.rank` | op | `/bb rank`. |
| `bosketball.admin.tags` | op | Attribuer / révoquer un tag. |
| `bosketball.admin.season` | op | Saison : start, end, days. |
| `bosketball.admin.holo` | op | Hologrammes et stations NPC. |
| `bosketball.admin.arena` | op | Arènes : build, repaste, delete, tp. |
| `bosketball.admin.reload` | op | `/bb reload`, push du pack. |
| `bosketball.admin.maintenance` | op | Mode maintenance. |
| `bosketball.admin.mutechat` | op | Mute global. |
| `bosketball.admin.stress` | op | `/bb stress`. |
| `bosketball.admin.backup` | op | Backup et `/bb save`. |
| `bosketball.admin.audit` | op | `/bb audit`, `/bb status`. |
| `bosketball.admin.displays` | op | `/bb displays`, réparation HUD. |
| `bosketball.admin.items` | op | `/bb giveball`, bypass cosmétiques. |
| `bosketball.admin.match` | op | forcestart, stop, bots. |
| `bosketball.admin.profiler` | op | Profiler interne. |
| `bosketball.admin.perms` | op | `/perm`. |
| `bosketball.cosmetics.all` | false | Tous les ballons et dunks. |
| `bosketball.tags.all` | false | Tous les tags (hors exclusifs). |
| `bosketball.vip` | false | Marqueur VIP. |
| `bosketball.founder` | false | Badge et cosmétiques fondateur. |

Le tag **MODS** n'est pas un nœud : il est débloqué par `bosketball.staff`.

## Groupes livrés

| Groupe | Hérite de | Contenu |
|---|---|---|
| `default` | — | `bosketball.play`, `-axiom.*`, `-fawe.*`, `-worldedit.*` |
| `vip` | default | `bosketball.vip` |
| `helper` | vip | `staff.reports`, `staff.warn`, `staff.spectate` |
| `builder` | vip | `bosketball.build`, `bosketball.map`, FAWE / Axiom |
| `mod` | helper | `bosketball.staff`, `staff.bypass`, commandes de modération |
| `admin` | builder + mod | `bosketball.admin`, `tags.all`, `cosmetics.all` |

`permissions.yml` existant : les nœuds manquants sont ajoutés au chargement
(`seedMissingDefaults()`), rien n'est jamais supprimé.

## Commandes

```
/perm list                                  groupes, parents, nœuds, membres
/perm nodes [filtre]                        tous les nœuds et leur défaut
/perm check <joueur> <nœud>                 pourquoi c'est accordé / refusé
/perm user <joueur> info|nodes              groupe principal et nœuds personnels
/perm user <joueur> group <groupe>          changer de groupe
/perm user <joueur> add|remove <nœud>       nœud personnel (+ ou -)
/perm group <groupe> info|create            créer / inspecter
/perm group <groupe> add|remove <nœud>      nœuds du groupe
/perm group <groupe> inherit add|remove <g> héritage
/perm group <groupe> users                  membres
/perm group <groupe> delete                 supprimer (refusé si membres ou hérité)
/perm reload                                recharger
```
