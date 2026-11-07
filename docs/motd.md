# MOTD de la liste des serveurs (Round 29)

Le MOTD est la bannière 2 lignes que le client affiche sous le nom du serveur. Il est construit par
`MotdManager` à chaque *ping* (`PaperServerListPingEvent`) et centré au pixel près sur les **268 px**
de largeur du bloc de texte de la liste des serveurs (`MotdManager.centerLine`).

Tout vit dans `config.yml` → section `motd:`.

## 1. Trois états, trois bannières

| État | Quand | Copie utilisée |
|---|---|---|
| `ONLINE` | serveur ouvert | `motd.header` + une ligne de `motd.subtitles` (rotation à chaque ping) |
| `MAINTENANCE` | `motd.maintenance-mode: true` | `motd.maintenance-header` + `motd.maintenance-subtitle` |
| `CLOSED` | le serveur s'arrête / redémarre (`BosketBallPlugin.isShuttingDown()`) | `motd.closed-header` + `motd.closed-subtitle` |

`MotdManager.State` porte ces trois états et `MotdManager.currentState(cfg)` les résout : un serveur
en cours d'arrêt gagne toujours, puis la maintenance, sinon `ONLINE`.

Pendant un arrêt, le serveur répond encore quelques *pings* : ils reçoivent désormais la bannière
`RESTARTING · SEE YOU IN A MOMENT` (version `✦ Restarting`, protocole `-1`) au lieu du « Server
closed » par défaut. Une fois la machine éteinte, plus aucun plugin ne peut répondre : c'est le
propre du serveur « down ».

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

Les écrans plein écran restent ceux du Round 28 (`ServerScreens`) : `maintenance(...)` pour les
joueurs non whitelistés en maintenance, `shutdown(...)` pour l'arrêt. Ils n'utilisent aucun glyphe du
resource-pack (la police custom n'est pas chargée sur l'écran de déconnexion) et restent sous 58
caractères par ligne.

## 6. Vérifier

```bash
python3 tools/build_plugin.py       # compile + 143 tests + pack + jar
```

Le test `testRound29PremiumMotdWithDedicatedMaintenanceAndRestartBanners` vérifie :

* qu'aucune bannière (6 lignes possibles) ne contient `.net`, `.xyz`, `.com` ou `http` ;
* que les 3 états produisent 3 bannières **centrées**, 2 lignes, et **différentes** ;
* que `currentState` suit `maintenance-mode` et que le drapeau d'arrêt existe côté plugin ;
* que le gestionnaire de ping ne lit plus `general().serverDomain()`.
