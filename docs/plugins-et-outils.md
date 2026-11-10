# Faut-il ajouter des plugins pour optimiser ? La reponse honnete

Verdict court : **sur ce serveur, non.** Pas par paresse : parce que la mesure dit deja que le
plugin coute ~0,02 ms/tick et que les deux seules grosses depenses restantes (paquets des display
entities, Axiom/FAWE) ne se soignent pas avec un plugin de plus.

Ce document liste quand meme les vrais candidats, et surtout ceux qu'il faut refuser — avec la
raison technique, pas « c'est mieux ».

## 1. Les seuls projets qui meritent d'etre nommes

| Projet | Etat | Pour toi |
|---|---|---|
| **spark** (`lucko/spark`) | deja **inclus dans Paper** | Le seul outil qui repond a « pourquoi ca lag ». `/spark profiler start --timeout 30`. Rien a installer. |
| **Chunky** (`pop4959/Chunky`, 761★, push du 4 nov. 2026) | actif | **Inutile ici** : `bosketball_courts` est cree avec un `VoidGenerator` (zero terrain a generer) et le lobby est pre-construit (187 chunks persistants). Chunky ne fera que remplir du vide. A installer **le jour ou tu ouvres un monde survie/exploration**. |
| **ChunkyBorder** (`pop4959/ChunkyBorder`) | actif | Seulement si tu veux une bordure personnalisee. Aucun gain de perf. |
| **EntityDetection** | utilitaire ponctuel | Affiche les entites par chunk. Ton audit en voit deja 197, toutes legitimes. A garder en cas de doute, pas en permanence. |
| **squaremap** | actif | Carte 2D legere — si un jour tu veux une carte. Ne rapporte aucune perf. |
| **Purpur** (fork, pas un plugin) | a verifier pour 26.2 | N'apporte rien a un serveur sans redstone/fermes. Et un fork en retard sur Paper est un risque, pas un gain. |

## 2. A ne pas installer, avec la raison

| Plugin | Pourquoi c'est une mauvaise idee **chez toi** |
|---|---|
| **ClearLag, LagAssist, « lag removers »** | Ils suppriment des entites pour faire baisser un chiffre. Ton serveur **est** fait de display entities : hologrammes du lobby, anneau de la caisse, decorations de courts, jumbotron, particules du podium. Une option « remove entities » mal reglee les efface, puis ton watchdog de decoration les re-paste en boucle : tu fabriques exactement le lag que tu voulais supprimer, avec des trous visuels en prime. |
| **EntityTrackerFixer (ETF)** | Abandonne, et connu pour laisser des entites **definitivement** cassees (IA retiree) meme apres desinstallation. |
| **StackMob / Stacker** | Concu pour les fermes a mobs. Casse les armor stands et les display entities. |
| **VillagerOptimiser, FarmLimiter, TreeAssist** | Rien a optimiser : pas de villageois, pas de fermes, pas d'arbres. |
| **PlaceholderAPI / MiniPlaceholders dans des boucles** | Chaque placeholder est un parse en plus, par joueur, par tick. Ton plugin formate deja tout en interne. |
| **ViaVersion / ViaBackwards** | +CPU par paquet, utile seulement si tu acceptes des clients anciens. |
| **Tout « TPS booster » paye** | Le probleme est toujours dans une config ou un bout de code, jamais dans un plugin miracle. |

## 3. Ce qui reste vraiment a gratter (config, pas plugin)

Tu as deja `view-distance=6`, `simulation-distance=4`, `network-compression-threshold=256`,
Aikar + `ActiveProcessorCount=2`. Les trois leviers restants :

1. **FAWE garde son historique en memoire.** Puisque tu le conserves, ouvre
   `plugins/FastAsyncWorldEdit/config.yml` et regarde la section `history` : si l'historique est
   en RAM, reduis sa taille (ou passe-le sur disque). C'est de la memoire que tu ne rends jamais
   entre deux sessions de build.
2. **Axiom a son propre buffer d'undo.** Meme logique : tant que tu ne construis pas, il ne sert a
   rien de garder des historiques profonds.
3. **`tracking-range-y` (Paper 26)** dans `paper-world-defaults.yml` : `enabled: true` +
   `display: 32` limite l'envoi des display entities a la verticale. A tester **une fois** avec
   `/spark profiler` avant/apres — gain probablement faible, mais c'est le seul reglage qui touche
   directement ton type d'entites. Retour arriere : `enabled: false`.

## 4. Ce qu'il faudra regarder a 50 vrais joueurs

Avec `/spark profiler start --timeout 60` pendant un pic, les frames a surveiller ne sont pas les
tiennes : ce sont `EntityTracker`, `ChunkMap` et les threads Netty. Si elles montent alors que ton
`/bb perf` reste sous 1 ms, la depense est dans **l'envoi des paquets**, et la seule reponse
efficace est de reduire ce qui est envoye (moins d'entites visibles, `tracking-range`, distance de
vue), pas d'ajouter un plugin.
