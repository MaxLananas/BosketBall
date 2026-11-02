# ✦ ʙᴏꜱᴋᴇᴛʙᴀʟʟ 26.2 — ᴘʟᴜɢɪɴ & ʀᴇꜱᴏᴜʀᴄᴇ ᴘᴀᴄᴋ 3ᴅ ᴄᴏᴍᴘʟᴇᴛ ✦

![Aperçu In-Game HUD & Stade BosketBall 26.2](docs/previews/ingame_hud_preview.png)

**BosketBall 26.2** est une suite complète nouvelle génération de **Basket-Ball 3D compétitif** conçue pour **Minecraft Java Edition 26.2 ("Chaos Cubed")** et **100% rétro-compatible dès Paper / Purpur `1.21.10` jusqu'à `26.2`** (Java 21 à Java 25 • Pack Formats `65` à `88`).

Elle combine un **Plugin complet compilé en bytecode Java 21+** (compatible Paper `1.21.10` Adventure `4.25` et Paper `26.2` Adventure `5.2`) et un **Resource Pack 3D haute précision** (**Balle Carrée 3D sculptée en relief** dans l'esprit cubique *Chaos Cubed* 26.2, paniers en verre trempé animés, bannières BossBar Broadcast sans ombre portée via `ShadowColor.none()`, badges de rangs & classements en chat, typographie *small-caps* `ᴀ ʙ ᴄ ᴅ ᴇ ꜰ ɢ ʜ ɪ ᴊ ᴋ ʟ ᴍ ɴ ᴏ ᴘ ǫ ʀ ꜱ ᴛ ᴜ ᴠ ᴡ x ʏ ᴢ`, et 12 effets sonores `.ogg` Vorbis spatialisés).

![Vitrine Resource Pack 26.2](docs/previews/resourcepack_showcase.png)

---

## 📦 ʟɪᴠʀᴀʙʟᴇꜱ ᴘʀᴇᴛꜱ ᴀ ʟ'ᴇᴍᴘʟᴏɪ (`releases/`)

| Fichier | Description |
| :--- | :--- |
| **`releases/BosketBall-26.2.jar`** | Plugin universel **Paper / Purpur `1.21.10` → `26.2`** (`api-version: '1.21'`, bytecode Java 21+, validé sur Adventure `4.25` & `5.2`) |
| **`releases/BosketBall-ResourcePack-26.2.zip`** | Resource Pack universel **Minecraft `1.21.10` → `26.2`** (`min_format: 65, max_format: 88`, strict JSON) |
| **`releases/BosketBall-ResourcePack-26.2.zip.sha1`** | Empreinte SHA-1 exacte pour distribution automatique au join |
| **`resourcepack/blockbench/basketball_26_2.bbmodel`** | Fichier source **Blockbench** éditable de la **Balle Carrée 3D** sculptée en relief |

---

## 🏀 ꜰᴏɴᴄᴛɪᴏɴɴᴀʟɪᴛᴇꜱ ᴍᴀᴊᴇᴜʀᴇꜱ

### 1. Moteur Physique 3D 80Hz & Gameplay NBA
- **Physique 3D sous-échantillonnée à 80Hz (4 sous-pas/tick)** : gravité réaliste, traînée aérodynamique, rotation 3D `Quaternionf` (backspin sur les tirs, roulement au sol) et interpolation fluide `ItemDisplay` + hitbox `Interaction`.
- **Collisions Toriques 3D sur l'Anneau & Planche en Verre Trempé** : détection exacte sur tore d'acier (`R = 0.45m`, ratio NBA authentique `1.85:1`), tirs avec la planche (*Shooter's Square*), *rattle-in* dramatique et **Swish pur** freiné par le filet en nylon.
- **Jauge de Tir NBA 2K (Green Release)** : maintenez **Clic-Droit** pour charger la jauge en ActionBar (`\uE300`..`\uE306`) et relâchez dans la fenêtre verte pour un **100% Green Release** !
- **5 Styles de Slam Dunk 3D Aériens** : `⚡ Tomahawk Power`, `🌀 Windmill 360`, `💫 Hélicoptère 360°`, `🌙 Reverse Acrobatique` et `☄ Meteor Posterizer` (avec onde de choc repoussant les défenseurs sous le cercle !).
- **Passes Laser, Lobs Alley-Oop, Crossovers Ankle-Breaker, Interceptions & Contres Aériens** :
  - `Touche Q (Jeter)` ou `Slot 1` : Passe directe vers le coéquipier visé, ou **Lob Alley-Oop** en visant au-dessus du cercle (rattrapable en plein saut pour un dunk aérien !).
  - `Shift (Accroupir)` ou `Slot 2` : **Crossover éclair** changeant le ballon de main et brisant les chevilles (`Ankles Broken`) des défenseurs proches.
  - `Clic-Gauche` en défense : **Interception active** sur le porteur ou **Contre aérien (`Block`)** en sautant face à un tir montant (avec protection *Goaltending*).
- **Mode NBA Jam "ON FIRE 🔥"** : à partir de 3 paniers consécutifs sans encaisser, le ballon bascule automatiquement sur le modèle animé magma **`basketball_fire`** avec aura de flammes et fenêtre verte élargie.

### 2. Modes de Jeu, Architecte de Stade & Créateur de Map Sur-Mesure (`/bb map`)
- **Modes compétitifs & solo (avec Horloge des 24s active partout !)** : `Duel 1v1 (21 pts)`, `Street 2v2`, `Pro 3v3`, `Ligue 5v5`, **`Concours à 3 Points All-Star`** (5 racks × 5 ballons dont les *Money Balls* tricolores à 2 pts en 60s !) et **`Entraînement Libre & Dunk Lab`** avec **Défenseur IA Pro (`/bb bot`)**.
- **Visée Directionnelle Skill & Viseur 3D Dynamique** : aucune visée magnétique automatique lorsque vous regardez vers les gradins ! Boussole de verrouillage d'arceau `⟦····◈····⟧` + réticules pixel-art (`\uE310`..`\uE313`), ruban de trajectoire 3D temps réel, télémetrie de contestation défensive (`OUVERT` / `CONTESTÉ` / `ÉTOUFFÉ`), jauge aérienne de timing Dunk, interceptions côté main exposée & ballons pokés au sol.
- **Créateur de Map Basket Sur-Mesure (`/bb map ...`)** : construisez votre propre terrain sans qu'aucun bloc ne soit modifié ! Définissez avec la **Baguette Architecte Laser 3D** (`court_wand`) les limites de sortie (`pos1`/`pos2`), la **cage anti-sortie ballon & joueurs** (`barrier1`/`barrier2`/`ceiling`), les **2 paniers orientables à 360°** (`hoop ignis` / `hoop frost` en modèle 3D ou blocs custom) et les **spawns d'équipes & lobby** (`setspawn ignis|frost|spectator|lobby`).
- **Générateur de Stade Complet (`/bb arena build <id>`)** : construit instantanément un parquet bicolore NBA (`31×17`), lignes en quartz lisse, arcs à 3 points, raquettes peintes Ignis/Frost, table de marque LED, **tribunes mosaïques sur 4 niveaux**, **4 pylônes projecteurs** et **Jumbotron central 4 faces** + les **2 Paniers 3D animés** avec horloges des 24s !

### 3. UI Broadcast, BossBars Superposées, Rangs, TabList & Chat
- **Double BossBar Broadcast sans cadre vanilla** (`white_background.png` transparent + glyphes `ShadowColor.none()` à largeur verrouillée au pixel près) :
  - En jeu : **Scorebug Live 5 colonnes** (`IGNIS | 21 | Q4 01:12 14s | 19 | FROST`).
  - Après chaque panier : **Bannière Buteur** (`REFORTORR • ꜱᴘʟᴀꜱʜ 3 ᴘᴏɪɴᴛꜱ` / `ꜱʟᴀᴍ ᴅᴜɴᴋ`) + **Sous-Bannière Passeur** (`ᴀꜱꜱɪꜱᴛ: A_Koop`) juste en dessous !
- **Salle d'Attente & Vestiaire 3D Interactif Plein Écran (`/bb 3d`) + File d'Attente Live (`● QUEUED 2V2 0:01`) & Menu Custom Noir-Or (`/bb`)** : scène 3D à 4 alcôves en pierre sombre et parquet chêne avec **4 statues 3D `Mannequin` en formation V** (vous avec `👑` + ballon carré 3D, coéquipiers de groupe / joueurs en file / amis / champions All-Star dotés de skins basket 64×64 custom), **file d'attente compétitive en direct dans la salle d'attente** (badge `● QUEUED <MODE> 0:01` qui défile chaque seconde en haut de l'écran + notification verte en chat + lancement automatique dès que la file est pleine ou instantané via `▶ LANCER MAINTENANT`), **support complet de votre propre salle d'attente custom** (`/bb 3d setcamera`, `/bb 3d setslot 1..4`, `/bb 3d resetstage` sans modifier aucun bloc !), caméra verrouillée zéro-secousse, panneaux HUD orthographiques `Billboard.FIXED`, barre de sélection vert menthe (`[Z/S/Q/D]` + `[ESPACE]`), *Patch Notes* + 2 pilules Indigo côte-à-côte (`Discord` & `Suggestions`), et interface coffre `/bb` **100% custom noir-obsidienne & or brossé** (zéro vitre grise vanilla, bouton resource pack supprimé) !
- **Tab Central 100% Custom Graphique (`[F]`, `/tab` ou `[TAB]`)** : overlay centré à l'écran construit sur de vraies cartes graphiques 2× HiDPI (`224px` : `\uE140`..`\uE144`) affichant les **vraies têtes de joueurs 8×8 inline** (`ObjectContents.playerHead`), barres vitrées d'équipes `IGNIS` / `FROST`, badges `#1`, points en direct, passes, rebonds et ping !
- **Animations Joueur & Bot 3D Articulées à 6 Os (24/7 Lobby + Terrain, 1P & F5) + Glow & Cercles d'Équipe** : rig 3D articulé (`Tête`, `Torse`, `Bras D/G`, `Jambes D/G` avec sneakers, actif dès le lobby en vue **`F5`** pour la marche, l'accroupissement et le saut, et sur le terrain + **Défenseur IA Pro**) animant marche, sprint, posture défensive accroupie (`Shift`), dribble synchronisé, saut, armé de tir, fouetté du poignet (*Gooseneck*), interceptions et Slam Dunks 360°/Windmill, accompagné de **contours lumineux d'équipe 3D (`setGlowColorOverride`)**, **cercles d'équipe lumineux sous les pieds** (`ring_ignis`, `ring_frost`, `ring_gold`) et nameplates d'équipe encadrés !
- **72 Badges & Tags Glossy Biseautés 2× HiDPI (`/bb tags` • 2 pages • jusqu'à 4 cumulables !)** : 18 pilules glossy 3D pour le Top 3 serveur (`Victoires`, `ELO`, `MVP`, `Passes`, `Points`, `Dunks`) + **54 tags d'aura biseautés avec mini-icônes** (`AURA`, `GOAT`, `CLUTCH`, `SNIPER`, `ANKLE`, `POSTER`, `MVP`, `LEGEND`, `SWISH`, `HOOPER`, `CLAMP`, `DIME`, `BUCKETS`, `CHAOS`, `ROYAL`, `ALPHA`, `OMEGA`, `GALAXY`, `VIPER`, `BLITZ`, `FROSTBITE`, `SUPERNOVA`, `VENOM`, `ZENITH`, `ECLIPSE`, `NOVA`, `RONIN`, `MAMBA`, `AIR`, `CHEF`, `IMMORTAL`, `PRODIGY`, `DYNASTY`, `BALLGOD`, `DEADEYE`, `SKYHOOK`, `CROSSGOD`, `ICEVEINS`, `SHOWTIME`, `UNSTOPPABLE`, etc. — cumulables par 4 en chat !), séparateurs métalliques encadrés `288px` `[ Header ]`, **logo comic-art `BOSKETBALL` haute définition en 5 bandes** et **Sidebar de luxe** 15 lignes à frise dorée `\uE260` !
- **Panier Pro 3D Ancré au Sol + Filet Conique 22.5° Tressé Sans Blocs Blancs Flottants, 8 Ballons Carrés (`0.36x`) & 21 Icônes Pixel-Art** : mât hydraulique continu du sol jusqu'à l'horloge 24s intégrée, mousse de protection inférieure, 12 crochets acier + filet conique incliné à `22.5°` directement accroché à l'arceau, marquages NBA affleurants (arc zone restreinte, lancers-francs, hachures de raquette sans aucun débordement blanc), **8 skins de Ballons Carrés 3D** réduits à `0.36x` en inventaire/menus, et **21 icônes pixel-art custom (`bosketball:icon_*`)** !

---

## ⚡ ɪɴꜱᴛᴀʟʟᴀᴛɪᴏɴ ʀᴀᴘɪᴅᴇ (ᴘᴀᴘᴇʀ 26.2)

1. Copiez **`releases/BosketBall-26.2.jar`** dans le dossier `plugins/` de votre serveur **Paper / Purpur 26.2** (Java 25).
2. *(Option A — Client / Solo / Test rapide)* Copiez **`releases/BosketBall-ResourcePack-26.2.zip`** dans `.minecraft/resourcepacks/` et activez-le.
3. *(Option B — Distribution automatique multi-joueurs)* Placez `BosketBall-ResourcePack-26.2.zip` dans `plugins/BosketBall/` et activez `resource-pack.embedded-http-server.enabled: true` (en renseignant l'IP publique dans `public-host`) ou renseignez `external-url` dans `plugins/BosketBall/config.yml`.
4. En jeu (avec OP ou `bosketball.admin`), exécutez :
   ```text
   /bb arena build centre Bosket Center
   /bb
   ```

---

## 🎮 ᴄᴏᴍᴍᴀɴᴅᴇꜱ & ᴘᴇʀᴍɪꜱꜱɪᴏɴꜱ

| Commande | Description | Permission |
| :--- | :--- | :--- |
| `/bb` ou `/bb menu` | Ouvrir le Hub BosketBall custom pixel-art (sans bouton inutile) | `bosketball.play` |
| `/bb 3d` | Entrer dans la **Salle d'Attente & Vestiaire 3D** (file `● QUEUED 0:01`, 4 statues 3D, `[Z/S/Q/D]`) | `bosketball.play` |
| `/bb 3d setcamera` / `setslot <1..4>` | Lier votre propre **Salle d'Attente custom** (caméra + 4 emplacements joueurs, `/bb 3d resetstage` pour réinitialiser) | `bosketball.admin` |
| `/tab` ou `/bb tab` *(ou `[F]`)* | Afficher / masquer le **Tab Central 3D** (têtes de joueurs, équipes & points) | `bosketball.play` |
| `/bb tags [id\|off]` | Ouvrir la galerie des **54 Tags Glossy (2 pages • cumulables x4)** ou équiper un tag | `bosketball.play` |
| `/bb history` | Consulter l'historique détaillé des 15 derniers matchs | `bosketball.play` |
| `/bb friends` & `/bb party` | Gérer sa liste d'amis et son Groupe (`1` à `4` joueurs) | `bosketball.play` |
| `/bb settings` | Activer / désactiver animations 3D, cercles au sol, sons & caméra | `bosketball.play` |
| `/bb play [1v1\|2v2\|3v3\|5v5]` | Rejoindre ou lancer un match compétitif (en solo ou en groupe) | `bosketball.play` |
| `/bb 3pt` | Lancer le Concours à 3 Points (5 racks, Money Balls) | `bosketball.play` |
| `/bb practice [map]` | Entrer dans le terrain d'entraînement 24s & Dunk Lab | `bosketball.play` |
| `/bb bot` | Activer / retirer le Défenseur IA Pro en entraînement | `bosketball.play` |
| `/bb duel <joueur>` | Envoyer ou accepter un défi Duel 1v1 | `bosketball.play` |
| `/bb locker` | Ouvrir la collection des **8 Ballons Carrés (`0.36x`)** & 5 Styles de Dunk | `bosketball.play` |
| `/bb stats [joueur]` | Afficher la carte encadrée de statistiques détaillées | `bosketball.play` |
| `/bb top` | Ouvrir le Panthéon des classements Top 10 (7 catégories) | `bosketball.play` |
| `/bb mail` / `/bb welcome` | Ouvrir la boîte aux lettres / revoir la carte illustrée de bienvenue | `bosketball.play` |
| `/gg` | Message fair-play stylisé en fin de match (`+10 XP`) | `bosketball.play` |
| `/bb map <create\|wand\|pos1\|pos2\|barrier1\|barrier2\|ceiling\|hoop\|setspawn\|jumbotron\|preview\|info\|save>` | Créer sa propre map basket (limites, barrières anti-sortie ballon/joueurs, paniers 360°, spawns Ignis/Frost/Lobby) | `bosketball.admin` |
| `/bb arena <build\|list\|tp\|delete>` | Construire et gérer les stades 3D générés | `bosketball.admin` |
| `/bb rank <set\|elo> <joueur> <val>` | Définir le rang ou l'ELO d'un joueur | `bosketball.admin` |
| `/bb giveball [classic\|money\|neon\|fire]` | Obtenir les objets 3D (Ballon, Trophée, Sifflet, Sneakers) | `bosketball.admin` |
| `/bb forcestart` / `/bb stop` / `/bb reload` | Contrôle de match et rechargement de la configuration | `bosketball.admin` |

---

## 🛠 ᴄᴏᴍᴘɪʟᴀᴛɪᴏɴ & ʀᴇɢᴇɴᴇʀᴀᴛɪᴏɴ ᴅᴇꜱ ᴀꜱꜱᴇᴛꜱ

- **Compiler avec Gradle (Java 25)** : `./gradlew build`
- **Compiler avec Maven (Java 25)** : `mvn clean package`
- **Régénérer tous les modèles 3D, textures PBR, glyphes & sons `.ogg` du Resource Pack 26.2** :
  ```bash
  python3 tools/generate_resourcepack.py
  ```
