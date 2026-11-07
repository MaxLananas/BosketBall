# MOTD de la liste des serveurs (Rounds 29 → 40)

Le MOTD est la bannière 2 lignes que le client affiche sous le nom du serveur. Il est construit par
`MotdManager` à chaque *ping* (`PaperServerListPingEvent`) et centré au pixel près sur les **268 px**
de largeur du bloc de texte de la liste des serveurs (`MotdManager.centerLine`).

Tout vit dans `config.yml` → section `motd:`.

## 1. Quatre états, quatre bannières

| État | Quand | Copie utilisée |
|---|---|---|
| `ONLINE` | serveur ouvert | `motd.header` + une ligne de `motd.subtitles` (rotation à chaque ping) |
| `STARTING` | le serveur démarre (`BosketBallPlugin.isStartingUp()`) | `motd.starting-header` + `motd.starting-subtitle` |
| `MAINTENANCE` | `motd.maintenance-mode: true` | `motd.maintenance-header` + `motd.maintenance-subtitle` |
| `CLOSED` | le serveur s'arrête / redémarre (`BosketBallPlugin.isShuttingDown()`) | `motd.closed-header` + `motd.closed-subtitle` |

`MotdManager.State` porte ces quatre états. La résolution est une fonction pure, testable sans
serveur :

```java
State state = MotdManager.resolveState(shuttingDown, startingUp, maintenance);
// arrêt > démarrage > maintenance > ouvert
```

| `shuttingDown` | `startingUp` | `maintenance` | État |
|---|---|---|---|
| ✔ | ✔ | ✔ | `CLOSED` |
| ✘ | ✔ | ✔ | `STARTING` |
| ✘ | ✘ | ✔ | `MAINTENANCE` |
| ✘ | ✘ | ✘ | `ONLINE` |

Un arrêt gagne donc toujours, puis le démarrage (un serveur qui boote n'ouvre pas ses portes, même si
`maintenance-mode` est resté allumé), puis la maintenance.

Pendant un arrêt, le serveur répond encore quelques *pings* : ils reçoivent désormais la bannière
`RESTARTING · SEE YOU IN A MOMENT` (version `✦ Restarting`, protocole `-1`) au lieu du « Server
closed » par défaut. Pendant un démarrage, c'est `WARMING UP · ALMOST READY` (version
`✦ Starting`, protocole `-1`) : le client affiche la bannière authentifiée au lieu de « Old server »
ou « Incompatible ».

### 1.1 La porte de démarrage (`motd.startup-gate-seconds`)

Un serveur Paper accepte les connexions **avant** que les mondes et les courts soient prêts : le
joueur tombe alors dans le vide, ou se fait éjecter par le premier plugin mécontent. La porte ferme
les joins pendant le boot.

| Clé | Défaut | Effet |
|---|---|---|
| `motd.startup-gate-seconds` | `8` | secondes de refus après `onEnable` — `0` désactive la porte |

* bornée **0 → 120** par le constructeur compact de `BosketConfig.Motd` (une faute de frappe `800`
  ne peut pas verrouiller le serveur pendant une heure) ;
* le staff (`bosketball.staff` / `bosketball.build`) passe toujours, comme en maintenance ;
* un joueur refusé voit `ServerScreens.starting(...)`, jamais le « Server closed » vanilla ;
* journal : `[MOTD] Startup gate: joins refused for N second(s)…` puis
  `[MOTD] Startup complete — joins are open.` (`markServerReady()` est idempotent).

La porte s'ouvre toute seule via le scheduler (`gateSeconds × 20` ticks) à la fin de `onEnable`, une
fois le resource-pack host initialisé. `gateSeconds = 0` appelle `markServerReady()` immédiatement.

## 2. Règles de style (ce qui fait la différence avec « arc-en-ciel »)

* **aucune URL** dans la bannière : le nom suffit, l'icône 64×64 porte déjà la marque ;
* **un seul dégradé saturé par ligne** ; tout le reste en ardoise / or, pour que les deux lignes ne
  se battent pas pour l'attention ;
* le même **losange d'ornement** (◆, 40 % d'opacité) encadre chaque ligne : l'œil va au titre ;
* trois tons distincts : or chaud (ouvert), ambre (travaux), ardoise froide (redémarrage) ;
* chaque ligne reste **bien sous les 268 px** pour que le centrage pixel ne coupe jamais ;
* glyphes BMP uniquement (`✦`, `◆`, `·`) : ils existent dans la police vanilla, contrairement aux
  emojis hors BMP qui affichent un carré vide.

Textes par défaut (source unique : `fr.bosketball.config.MotdText`) :

```
ONLINE        ✦ BOSKETBALL ✦          (dégradé #FFE9A8 → #FFC53D → #FF8C1A)
              ◆ STREET BASKETBALL, PERFECTED ◆
              ◆ RANKED 1V1 · 2V2 · 3V3 · 5V5 ◆
              ◆ DUNKS · REPLAYS · COINS SHOP ◆
              ◆ A FRESH SEASON, A FRESH COURT ◆
              ◆ JOIN THE COURT, OWN THE GAME ◆

STARTING      ✦ BOSKETBALL ✦          (or chaud)
              ◆ WARMING UP · ALMOST READY ◆

MAINTENANCE   ✦ BOSKETBALL ✦          (dégradé ardoise)
              ◆ COURT UNDER REFIT · BACK SOON ◆

CLOSED        ✦ BOSKETBALL ✦          (dégradé ardoise froid)
              ◆ RESTARTING · SEE YOU IN A MOMENT ◆
```

## 3. Liste des joueurs au survol

`onServerListPing` remplit aussi la liste affichée au survol, selon l'état :

| État | Lignes |
|---|---|
| `ONLINE` | `✦ BOSKETBALL ✦`, `◆ In Match: n`, `◆ In Queue: n`, `◆ Discord: /discord` |
| `STARTING` | `✦ BOSKETBALL ✦`, `◆ Server starting up`, `◆ Courts are being prepared`, `◆ Joins open in a few seconds` |
| `MAINTENANCE` | `✦ BOSKETBALL ✦`, `◆ Court under refit`, `◆ Back online very soon`, `◆ Updates: /discord` |
| `CLOSED` | `✦ BOSKETBALL ✦`, `◆ Server restarting`, `◆ See you in a moment` |

Le domaine n'y apparaît plus non plus.

## 4. Migration des anciennes configs

Une installation existante gardait `BOSKETBALL.NET` en ligne 1 et les sous-titres arc-en-ciel.
`BosketConfig.loadAndUpgrade` (config-version **7**) remplace ces valeurs **uniquement si elles sont
restées les anciennes par défaut** :

* `motd.header` contenant `BOSKETBALL.NET` → nouveau titre ;
* sous-titres contenant `COMPETITIVE 3D BASKETBALL` ou l'ancien dégradé `#FF5E5E` → nouvelle série ;
* `motd.maintenance-subtitle` contenant `SCHEDULED MAINTENANCE IN PROGRESS` → bannières
  maintenance **et** redémarrage réécrites.

Une bannière personnalisée par le propriétaire n'est jamais écrasée.

## 5. Écran de kick (hors MOTD)

Les écrans plein écran viennent de `ServerScreens` : `maintenance(...)` pour les joueurs non
whitelistés en maintenance, `starting(...)` pendant la porte de démarrage, `shutdown(...)` pour
l'arrêt. Ils n'utilisent aucun glyphe du resource-pack (la police custom n'est pas chargée sur
l'écran de déconnexion) et restent sous 58 caractères par ligne.

`starting(...)` est en **anglais** comme `shutdown(...)` — la langue du client n'est pas connue de
façon fiable à ce moment-là — et rappelle le chemin de retour :

```
              ✦ BOSKETBALL ✦
           S E R V E R  S T A R T I N G
     The lights are coming on — the courts are
              being prepared.
         Reconnect in a moment to join.
        ──────────── ✦ ────────────
        ✦ Discord  discord.gg/…
        ✦ Site     bosketball.net
        Tip-off in a few seconds!
```

## 5.1 Quand il n'y a plus de serveur pour répondre

Aucun plugin ne tourne quand la machine est éteinte : il faut donc écrire la marque dans les fichiers
que Paper lit **avant** le plugin. Trois fichiers livrés dans `docs/server-optimization/` portent la
bannière :

| Fichier | Clé | Valeur |
|---|---|---|
| `server.properties` | `motd` | `§6✦ BOSKETBALL ✦ §8· §7WARMING UP · BACK IN A MOMENT` |
| `spigot.yml` | `messages.restart` | `✦ BOSKETBALL ✦ - Server restarting, back in a moment` |
| `paper-global.yml` | `messages.kick.authentication-servers-down` | `✦ BOSKETBALL ✦ — Session service hiccup. Reconnect in a moment.` |
| `paper-global.yml` | `messages.kick.connection-throttle` | `✦ BOSKETBALL ✦ — Too fast! Wait a second and reconnect.` |
| `paper-global.yml` | `messages.kick.server-full` | `✦ BOSKETBALL ✦ — Courts are full. Try again in a moment.` |

Le `motd` de `server.properties` n'est visible que pendant le boot (Paper prend le relais dès le
premier ping) : c'est exactement la fenêtre où le joueur voit sinon un « Can't connect to server ».

## 6. Vérifier

```bash
python3 tools/build_plugin.py       # compile + 167 tests + pack + jar
python3 tools/check_server_configs.py   # bannières hors ligne (nécessite PyYAML)
```

Le test `testRound29PremiumMotdWithDedicatedMaintenanceAndRestartBanners` vérifie :

* qu'aucune bannière (6 lignes possibles) ne contient `.net`, `.xyz`, `.com` ou `http` ;
* que les 3 états produisent 3 bannières **centrées**, 2 lignes, et **différentes** ;
* que `currentState` suit `maintenance-mode` et que le drapeau d'arrêt existe côté plugin ;
* que le gestionnaire de ping ne lit plus `general().serverDomain()`.

Le test `round40StartingServerHasItsOwnBannerAndScreen` couvre le Round 40 :

* les 4 combinaisons de `resolveState` (arrêt > démarrage > maintenance > ouvert) ;
* une bannière `STARTING` à 2 lignes, centrée, marquée, **différente** des trois autres ;
* la borne de la porte (`-3 → 0`, `9 999 → 120`) et `startupGateEnabled()` ;
* `ServerScreens.starting(null, null)` : titre en petites capitales, texte anglais, ≤ 58 caractères
  par ligne, ≤ 20 lignes — donc pas besoin d'un serveur pour le tester ;
* la présence de `starting-header`, `startup-gate-seconds`, `isStartingUp()`, `markServerReady()` et
  `ServerScreens.starting(` dans les sources.

`check_server_configs.py` (`check_offline_banners`) vérifie en plus les bannières hors ligne :
marque `BOSKETBALL` présente, code couleur `\u00a7` échappé, et une ligne de MOTD sous 120
caractères pour que la liste des serveurs ne la coupe pas.
