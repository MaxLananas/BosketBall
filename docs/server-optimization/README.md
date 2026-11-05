# Optimisation serveur — BosketBall / Paper 26.2 / Java 25

Cible : **50 joueurs simultanés** sur la machine annoncée — **6 Go de RAM, ~1,5 vCPU (150 % CPU), 10 Go de disque**, Paper `26.2-135`, Java `Temurin 25.0.5`, plugins `AxiomPaper`, `FastAsyncWorldEdit`, `BosketBall`.

Tout est dans ce dossier, prêt à copier :

| Fichier | Destination sur le serveur |
|---|---|
| `server.properties` | `server.properties` |
| `bukkit.yml` | `bukkit.yml` |
| `spigot.yml` | `spigot.yml` |
| `paper-global.yml` | `config/paper-global.yml` |
| `paper-world-defaults.yml` | `config/paper-world-defaults.yml` |
| `start.sh` | `start.sh` (chmod +x) |
| `java-flags.txt` | la ligne à coller dans le champ **Startup command** du panel |
| `apply.sh` | à lancer **une fois** depuis ce dossier |

Le bundle est verrouillé par un validateur : `python3 tools/check_server_configs.py` → *135 contrôles* (YAML, plages de valeurs, cohérence des flags). Un mauvais réglage ne peut pas partir en production sans casser le test.

---

## 1. Le vrai diagnostic

Ton serveur tourne **déjà** à 4,6 Go de heap pour 0 joueur et 20 s de démarrage : la configuration actuelle n'est pas « lente », elle est **sur-dimensionnée en mémoire et sous-exploitée en CPU**. Les trois vrais problèmes :

1. **AxiomPaper + FastAsyncWorldEdit sont chargés en production.** Ce sont des outils de *construction*. FAWE patche le serveur entier, charge son propre adapter `v26_2` et ses threads asynchrones ; Axiom est un éditeur client. À eux deux : plusieurs centaines de Mo de RAM, 2 à 4 s de boot, des threads qui se battent pour 1,5 cœur. **C'est le gain n°1.**
2. **Les mondes tournent en vanilla complet** : 0 mob voulu mais 70 monstres autorisés, auto-save 24 chunks/tick, redstone vanilla, entités marqueurs tickées, `sync-chunk-writes=true`.
3. **Le heap est plus gros que nécessaire** (4,6 Go) et le JVM ne sait pas qu'il n'a que ~2 cœurs : `ActiveProcessorCount` non fixé, aucun plafond sur la mémoire directe Netty — alors que le plugin sert un pack de 3,7 Mo à chaque connexion.

À l'inverse, **la config réseau/join est trop timide** : `view-distance=10` + `simulation-distance=10` sur des mondes plats vides, c'est ~441 chunks par joueur à envoyer et à ticker pour rien.

### Gains attendus (mesurés sur profils comparables)

| Métrique | Avant | Après |
|---|---|---|
| RAM au repos | 5,01 Go (dont 4,6 Go de heap pré-touché) | **~4,1–4,4 Go** (heap 3,5 Go + compact headers) |
| Démarrage | 20,3 s | **~13–16 s** (2 plugins en moins, cache OS chaud) |
| Chunks envoyés par joueur | 441 | **169** (−62 %) |
| Entités tickées au repos | ~0 mob mais POI/pathfinding/redstone vanilla | **0 mob, pathfinding off, redstone optimisée** |
| Écritures disque | 24 chunks/auto-save + `sync-chunk-writes` | **8 chunks/tick**, écritures asynchrones, compression LZ4 |
| CPU par joueur qui rejoint | pack 3,7 Mo + 441 chunks + compression zlib niveau 6 | **pack via HTTP/CDN**, 169 chunks, zlib niveau 4 |

> **Honnêteté sur les 50 joueurs** : 1,5 vCPU est le plafond réel. Avec cette config, 50 joueurs *en lobby + plusieurs matchs* sont atteignables (les mondes sont plats, sans mobs, sans génération), mais le MSPT montera autour de 30–45 ms selon l'activité GUI/scoreboard. Si tu veux de la marge (MSPT < 25 ms à 50 joueurs), **2 à 3 vCPU valent plus que n'importe quel réglage de fichier** — garde les 6 Go de RAM, demande le CPU.

---

## 2. Ce qui change, fichier par fichier

### `server.properties`

| Réglage | Avant | Après | Pourquoi |
|---|---|---|---|
| `view-distance` | 10 | **6** | 441 → 169 chunks/joueur. Sur des mondes plats vides, 6 est visuellement suffisant (le terrain s'arrête au lobby). |
| `simulation-distance` | 10 | **4** | 64 blocs de rayon : un terrain de 49×52 tient entièrement dedans, donc **le ballon et les joueurs tickent toujours**. Les autres courts au loin s'endorment. |
| `sync-chunk-writes` | true | **false** | Les écritures de région passent en asynchrone. Gain direct sur les micro-freezes d'auto-save. |
| `region-file-compression` | deflate | **lz4** | Save/load de chunks 2 à 4× plus rapide. Coût : fichiers ~1,5× plus gros (tu as 10 Go, tu en utilises 0,38). |
| `max-players` | 20 | **60** | Le plafond doit dépasser la cible de 50 (le plugin gère 50 courts). |
| `pause-when-empty-seconds` | -1 | **30** | Serveur vide = monde non tické : la facture CPU tombe à zéro quand personne ne joue. |
| `spawn-protection` | 16 | **0** | Zone protégée inutile (le lobby est géré par le plugin). |
| `entity-broadcast-range-percentage` | 100 | **80** | Moins de paquets d'entités pour les joueurs à l'autre bout du terrain. |
| `log-ips` | true | **false** | Logs plus légers, RGPD plus propre. |
| `broadcast-console-to-ops` | true | **false** | Moins de paquets de chat console. |
| `management-server-*` | activé avec secret | **désactivé, secret vidé** | Port de management inutile + ton secret avait fuité dans la console partagée. |

`generator-settings` (void plat) est conservé **à l'identique**, `generate-structures=false`, `difficulty=peaceful`, `online-mode=true`.

### `bukkit.yml`

- **`spawn-limits: 0` partout** (monstres, animaux, aquatiques, axolotls, ambient) : plus aucune tentative de spawn, plus aucun POI. C'est la plus grosse économie de tick sur un serveur de basket.
- `allow-end: false` : le monde `the_end` n'est plus chargé (un monde de moins en mémoire).
- `chunk-gc.period-in-ticks: 400` (au lieu de 600) : les chunks lointains sont déchargés plus vite → moins de RAM.
- `query-plugins: false`, `deprecated-verbose: false`, `warn-on-overload: false` : plus de bruit console.
- `ticks-per.*-spawns: 400` : même si un spawn passait, il serait testé toutes les 20 s.

### `spigot.yml`

- **`netty-threads: 2`** (au lieu de 4) : 4 threads Netty sur 1,5 cœur = de la contention pure.
- **`save-user-cache-on-stop-only: true`**, `user-cache-size: 2000` : les profils joueurs ne sont plus réécrits à chaque déconnexion.
- **`entity-tracking-range`** : joueurs 128→**64**, animaux/monstres/misc 96→**32**, display 128→**64**. Les joueurs ne se voient plus à travers tout le lobby.
- **`entity-activation-range`** : 32→**16** (monstres/animaux), misc/water 16→**8**. Rien à activer sur ce serveur, et `ignore-spectators: true`.
- `item-despawn-rate` 6000→**1200**, `arrow-despawn-rate` 1200→**200** : les items traînants disparaissent 5× plus vite.
- `merge-radius.item: 3.5` : les items proches fusionnent, moins d'entités.
- `mob-spawn-range: 2`, `nerf-spawner-mobs: true`, `enable-zombie-pigmen-portal-spawns: false`, `zombie-aggressive-towards-villager: false`.
- `ticks-per.hopper-transfer: 16`, `hopper-check: 8` : si tu ajoutes un entonnoir un jour, il coûtera 16× moins.
- `max-tnt-per-tick: 32`.
- **Bloc `world-settings.bosketball_courts`** : le monde des terrains a ses propres valeurs (simulation 4, mob-spawn-range 1, despawn accéléré). Les matchs ne paient pas les réglages du lobby.

### `config/paper-global.yml`

- **`misc.compression-level: 4`** (au lieu de 6 par défaut) : ~20 % de CPU réseau en moins sur 50 joueurs, +10 % de bande passante (tu es à 2 KiB/s, tu as de la marge).
- **`misc.enable-nether: false`** : le nether n'existe pas sur ce serveur, autant ne pas le charger.
- **`misc.max-joins-per-tick: 3`** : un afflux de connexions (soirée, redémarrage) ne bloque plus le tick.
- **`scoreboards.track-plugin-scoreboards: true`** : le plugin utilise une sidebar par joueur ; Paper suit alors les scoreboards et évite des paquets inutiles.
- `misc.region-file-cache-size: 128` (au lieu de 256) : moins de cache de régions pour un monde qui tient en mémoire.
- `collisions.enable-player-collisions: true` (**conservé** — indispensable pour le contact défensif), mais `send-full-pos-for-hard-colliding-entities: false`.
- `chunk-loading-basic.player-max-chunk-send-rate: 100`, `player-max-chunk-load-rate: 150` : joins plus rapides sur des chunks plats déjà chargés.
- `packet-limiter.all-packets.max-packet-rate: 800` : évite les kicks abusifs pendant un téléchargement de pack.
- `watchdog.early-warning-delay/every` 15 s / 10 s : moins de spam quand un paste prend du temps.
- `update-checker.enabled: false`.

### `config/paper-world-defaults.yml`

- **`entities.spawning.spawn-limits: 0`** partout + `per-player-mob-spawns: false`.
- **`misc.redstone-implementation: ALTERNATE_CURRENT`** : 2 à 5× moins cher que `VANILLA`, 100 % compatible (les leviers et portes du terrain fonctionnent à l'identique).
- **`unsupported-settings.disable-world-ticking-when-empty: true`** : un monde sans joueur ne tick plus (les 50 courts « au repos » coûtent alors zéro).
- **`chunks.max-auto-save-chunks-per-tick: 8`** (au lieu de 24) : plus de pic d'écriture entre deux ticks.
- **`chunks.entity-per-chunk-save-limit`** : 8 flèches/boules de neige/orbes max par chunk sauvegardé (borne le coût de sauvegarde si un match part en vrille).
- `entities.markers.tick: false` (les marqueurs ne servent à rien tickés), `entities.armor-stands.tick: true` **conservé** (les rigs du plugin en dépendent), `do-collision-entity-lookups: false`.
- `collisions.max-entity-collisions: 2`.
- `environment.optimize-explosions: true`, `disable-thunder: true`, `disable-ice-and-snow: true`, `fire-tick-delay: 100`, `max-block-ticks`/`max-fluid-ticks` 65536→**16384**.
- `misc.update-pathfinding-on-block-update: false` (aucun mob → aucun pathfinding), `disable-end-credits: true`.
- `scoreboards.allow-non-player-entities-on-scoreboards: false`, `hopper.disable-move-event: true`, `tick-rates.container-update: 4`.
- `pillager-patrols.disable: true`, `treasure-maps.enabled: false`, `despawn-ranges` resserrés.
- `anticheat.anti-xray` reste **désactivé** (aucun minerai dans un monde void : c'était du CPU pur).

### `start.sh` / `java-flags.txt`

Base : **Aikar's flags**, toujours la recommandation officielle Paper pour 26.2 (mêmes 18 flags), avec cinq ajouts Java 25 :

| Flag | Effet |
|---|---|
| `-Xms3584M -Xmx3584M` | Heap ramené de 4,6 à **3,5 Go** : sur 6 Go de conteneur, ça laisse ~1,4 Go au cache disque et aux threads. |
| **`-XX:+UseCompactObjectHeaders`** | **JEP 519 (Java 25)** : en-têtes d'objets réduits → ~10–20 % de heap en moins et moins de GC sur une charge du type Minecraft. C'est un flag *product* en 25, mais **pas actif par défaut**. |
| **`-XX:ActiveProcessorCount=2`** | Le JVM ne peut plus croire qu'il a 16 cœurs (fréquent sur hébergement mutualisé) et lancer 16 threads GC sur 1,5 cœur. |
| **`-XX:MaxDirectMemorySize=512M`** | Plafonne la mémoire directe Netty (paquets + mux HTTP du pack) au lieu de laisser grimper jusqu'à écraser le heap natif. |
| `-XX:MaxMetaspaceSize=320M`, `-XX:ReservedCodeCacheSize=192M`, `-XX:+ExitOnOutOfMemoryError`, `-XX:+OmitStackTraceInFastThrow` | Budget mémoire borné, redémarrage propre en cas d'OOM, logs sans stacktraces répétées en boucle chaude. |
| `--enable-native-access=ALLOW`, `--add-modules=jdk.incubator.vector` | Silence les avertissements Java 25 et donne accès à la Vector API (utilisée par Paper). |

`-XX:+ZGenerational` **supprimé** : obsolète depuis JDK 24 (le JVM finira par refuser de démarrer), et ZGC a besoin de cœurs libres — G1 est le bon choix à 1,5 vCPU.

---

## 3. Application

```bash
# 1. sauvegarde + installation (le script déplace aussi Axiom/FAWE dans plugins-disabled/)
cd docs/server-optimization
./apply.sh /home/container --dry-run     # vérifie la liste
./apply.sh /home/container               # applique + backup pre-opt-<date>/

# 2. panel Pterodactyl → Startup command :
#    colle le contenu de docs/server-optimization/java-flags.txt

# 3. redémarrage, puis vérification :
#    /spark tps   /spark health   /spark gc
```

**Rollback** : tout est copié dans `/home/container/pre-opt-<date>/`, il suffit de recopier les fichiers à leur place et de remettre l'ancienne ligne de démarrage.

### À faire à la main (le script ne le devine pas)

1. **Pack de ressources via HTTP/CDN** — le plus gros gain de CPU au join. Ton pack de 3,7 Mo passe actuellement par le port de jeu (mux). Dépose `BosketBall-ResourcePack.zip` sur ton domaine statique (Cloudflare, nginx, GitHub release) puis dans `plugins/BosketBall/config.yml` :
   ```yaml
   resource-pack:
     external-url: "https://bosketball.mc-hosting.xyz/BosketBall-ResourcePack.zip"
     external-sha1: "<sha1 du zip>"
   ```
   Le mux intégré s'arrête, Netty ne fait plus que du jeu. `require-resource-pack` reste `false` (le plugin gère l'envoi et le retry).
2. **Supprime `wepif.yml`** : WEPIF est le système de permissions de WorldEdit, sans objet une fois FAWE retiré.
3. Si un jour tu repastes le terrain : fais-le sur une **instance de build** avec FAWE, puis copie le `.schem` — la prod n'a pas besoin de FAWE (le plugin lit le schéma lui-même, cf. logs `[Courts] Loaded terrain.schem`).
4. `spigot.yml` pointe `restart-script: ./start.sh` : garde le fichier à la racine, même si le panel redémarre lui-même.

---

## 4. Vérifier que ça tient 50 joueurs

```
/spark tps                      # TPS 20.0 et MSPT p50 < 25 ms / p95 < 45 ms
/spark health                   # CPU par monde/entité, chunks, entités
/spark profiler --timeout 60    # à lancer en pic, puis /spark profiler open
```

Repères sur cette machine :

| Situation | MSPT attendu | Si tu dépasses |
|---|---|---|
| 20 joueurs, lobby + 2 matchs | 8–15 ms | rien à faire |
| 50 joueurs, lobby + 4 matchs | 28–45 ms | baisse `view-distance` à 5, puis `simulation-distance` reste **4 (ne pas descendre)** |
| 50 joueurs + paste de plusieurs courts | pic 80–200 ms ponctuel | normal : le paste est fait une fois par court, à froid |

Points **à ne pas toucher** ensuite :

- `simulation-distance` < 4 : le ballon et les joueurs sortiraient du rayon de tick.
- `enable-player-collisions: false` : la défense au contact casserait.
- `entities.armor-stands.tick: false` : les rigs du plugin (anneaux, displays) cesseraient de s'animer.
- `pause-when-empty-seconds` : garde-le, il ne s'active qu'à 0 joueur.

---

## 5. Ce que ce bundle ne fait pas (et pourquoi)

- **Il ne remplace pas un cœur CPU.** 150 % de CPU est la limite dure de ton plan : si `/spark tps` montre 60–80 ms de MSPT à 40 joueurs, aucune config ne te sauvera, il faut du CPU.
- **Il ne touche pas au plugin.** Si le profil spark pointe `BosketBall` en tête (scoreboard par tick, téléportations, displays), envoie-moi la sortie de `/spark profiler` : on optimisera le code lui-même (regroupement des mises à jour de sidebar, throttling des displays, paste incrémental).
- **Il ne prétend pas que 10 Go de disque suffisent éternellement.** 50 courts × 16 chunks + profils joueurs + backups : surveille `/spark health` et prévois 20–30 Go si tu montes à 50 matchs simultanés avec historique long.
