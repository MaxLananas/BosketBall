# ✦ BosketBall — Standalone Competitive Basketball Server Engine & Resource Pack ✦

![BosketBall In-Game HUD & Stadium Preview](docs/previews/ingame_hud_preview.png)

**BosketBall** (`v3.4.0`) is an all-in-one competitive multiplayer basketball server plugin and embedded custom resource pack for **Paper / Purpur 26.2** (Java 21–25) — a **26.2-only** release, engineered to run out-of-the-box alongside **FAWE** and **Axiom**.

Drop **`BosketBall.jar`** into `plugins/` and start your server: the official Resource Pack is **embedded directly inside the plugin JAR**, automatically extracted, hashed, hosted via a built-in virtual-threaded HTTP server, and delivered to every player on join with **zero manual configuration**.

![BosketBall Resource Pack Showcase](docs/previews/resourcepack_showcase.png)

---

## 📦 Ready-to-Deploy Artifacts (`releases/`)

| File | Description |
| :--- | :--- |
| **`releases/BosketBall.jar`** *(and `BosketBall-26.2.jar`)* | Self-contained server plugin **with the official Resource Pack bundled inside** (`api-version: '26.2'`, Java 21+ bytecode) |
| **`releases/BosketBall-ResourcePack.zip`** | Standalone copy of the official BosketBall Resource Pack (`min_format: 65, max_format: 88`, strict JSON) |
| **`releases/BosketBall-ResourcePack.zip.sha1`** | Exact 40-char SHA-1 hash (automatically synced into `config.yml` on startup) |
| **`releases/server-icon.png`** | Official `64×64` 1:1 server list icon (auto-installed if missing) |

---

## ⚡ Zero-Setup Installation

1. Copy **`releases/BosketBall.jar`** into your Paper/Purpur server's `plugins/` folder *(optional: alongside **FastAsyncWorldEdit** and **Axiom**)*.
2. Start the server:
   - `BosketBall-ResourcePack.zip` is **automatically extracted** into `plugins/BosketBall/`.
   - `config.yml` is **automatically updated** with the exact SHA-1 hash and `embedded-http-server.enabled: true` (`public-host: "auto"`).
   - Every joining player's connection hostname/IP is auto-detected via `Player#getVirtualHost()` so both `localhost` testers and public players download the Resource Pack automatically *(make sure TCP port `8765` is open on public hosts, or set `resource-pack.external-url` if preferred)*.
3. As an Operator (`OP`), set your lobby spawn, generate or link a court, and place fixed wall leaderboards:
   ```text
   /setspawn
   /bb arena build center Bosket Center
   /bb holo create elo
   /bb admin
   ```

---

## 🏀 Core Gameplay, UI & Visual Systems

### 1. 80Hz Physics, Bow-Style Shooting, Manual Skill Dunks & Goal Replays
- **80Hz Sub-Stepped 3D Ballistics (`4` sub-steps/tick)**: gravity, air drag, `Quaternionf` backspin, zero floor tunneling, 3D rim torus rattles, tempered backboard bank shots, and a closed 40-panel octagonal nylon net.
- **Native Bow-Pullback Jump Shooting (`Slot 0`)**: hold **Right-Click** to draw and aim with live trajectory/release feedback; release in the **Perfect Green Window** for pure swishes without unintended auto-release interruptions.
- **Manual 2-Click Aerial Skill Dunks (`Slot 3`)**:
  1. Jump inside the paint and click once to **rise toward the rim**.
  2. Click a **second time inside the Green Zone** to slam it home (`Tomahawk`, `Windmill`, `360 Spin`, `Between-the-Legs`, `Meteor Jam`) — mistime it and rattle out!
- **Instant TV Goal Replay (`recap`)**: every valid scored basket (`2PT`, `3PT`, and `Slam Dunk`, including Practice baskets and `1v1` match-winners) triggers a live 3D `Mannequin` Instant Goal Replay with player names, real movement & arm swings, and `[Sneak]` to skip.
- **Passes, Alley-Oops, Crossovers, Steals & Blocks**:
  - `[Q]` or `Slot 1`: crisp chest pass or mid-air **Alley-Oop lob** toward the rim.
  - `[Sneak]`, `[F]`, or `Slot 2`: **V-Crossover** hand switch with ankle-breaker burst.
  - `Left-Click` on defense: active **steal** poke or **aerial block** (with goaltending rules).
- **Isolated Multi-Instance Practice Courts (`/practice`)**: `PracticeArenaPool` allocates private solo courts in `bosketball_practice` (`24s` live shot clock + toggleable `Mannequin` **Pro AI Defender Bot**).

### 2. Custom Cytooxien-Style GUIs, Cuboide 2D Icons & Hover Previews
- **Custom Sculpted Container Overlays**: custom negative-space bitmap container layouts (`Boutique Shop`, `Collector Chat Tags Vault`, `Basketballs & Dunk Styles`, `Play Modes`, `Main Hub`) with sculpted split-bays, section banners, rarity filter tabs, and color-coded slot bezels.
- **Cuboide Pixel-Art Icon Suite & 2D Hotbar**: crisp `16×16` 2D pixel-art hotbar & menu items (striped market stall `Boutique Shop`, leather wallet, stacked gold coins, royal guide tome, golden chalice, and 6 distinct non-squished collector tag silhouettes including the Golden Pineapple & Royal Crown).
- **Large 3D Basketball Hover Showcase**: hovering any Basketball Skin in **`Basketballs & Dunks`** or **`Boutique Shop`** renders a large `44×40px` full-color 3D isometric basketball showcase right inside the item tooltip.
- **Full-Screen Locker Room Menu (`/locker`)**: your character is **totally frozen** on a cinema camera in front of your Party (`1–4` players) while the whole menu is drawn **directly on your screen** by the resource pack (top tab bar `PLAY · PROFILE · SHOP · SOCIAL · SETTINGS`, slanted selection list, live info panel, player/party status). Keyboard only: `W/S` (or `Forward/Back`) navigate, `Q/D` (or `Left/Right`) switch tab or adjust the highlighted value (mode, ball, settings toggles…), `Space` selects, `Shift` exits. Footer always shows your real keybinds.

### 3. Fixed Interactive Wall Leaderboards & Lobby Stations (`/bb holo`)
- **Fixed Dark-Panel Wall Leaderboards (`ELO`, `POINTS`, `COINS`)**: architectural `Billboard.FIXED` multi-row leaderboard boards with a crowned header pill, **7 pixel-aligned individual dark row bars**, interactive **`◀` / `▶` side arrows** that live-cycle categories (`ELO` • `Points` • `Coins` • `Wins` • `Dunks`) right on the wall, a bottom filter bar, click-to-open Top 10 GUI, and a rotating 3D trophy/basketball floating `+1` block above the board.
- **Pinned Lobby Layout (zero setup)**: the official map layout ships in `config.yml` (`lobby:` block) — **every** player who joins is force-spawned at `89.5, 63, 61.5` facing the plaza, and the two clickable 3D NPC stations (superstar = **Play** at `119.5, 62, 56.5`, merchant = **Boutique Shop** at `119.5, 62, 66.5`) plus their floating holograms are re-created at their exact position & facing (`yaw`/`pitch`) on every start. Set `lobby.spawn.enforce` / `lobby.stations.enforce` to `false` to manage them by hand with `/setspawn` & `/bb holo` instead.

### 4. Competitive Ranks, Top `#1/#2/#3` Badges, Economy & Social Suite
- **Modernized HiDPI Rank Pills (`/ranks`)**: `Rookie (0)` → `Hooper (1050)` → `Pro (1200)` → `All-Star (1400)` → `Superstar (1650)` → `Legend (1900)` → `MVP (2200+)`, plus `Referee` & `Admin`, and exclusive `FOUNDER` (`MaxLananas`) & `MYPINEAPPLE` collector badges.
- **Live Server `#1 / #2 / #3` ELO Badges**: automatically awarded in Chat, `/gg`, `[TAB]`, Scoreboard, and Leaderboards.
- **Match Coin Economy (`/shop`)**: earn Coins for wins (`+40`), MVP (`+15`), buckets (`+5` 2PT / `+8` 3PT), assists/steals/blocks (`+4`), 3PT Contest (`up to +25`), and `/gg` (`+5`), and spend them on **8 Basketballs**, **5 Dunk Styles**, and **54 Collector Chat Tags**.
- **Party Custom Team Names (`/party name <Team>`)**: replace default `IGNIS`/`FROST` banners on the live Scorebug, Jumbotron, and chat with your squad's custom name!

### 5. Standalone World Protection, Strict Non-OP Lockdown & FAWE/Axiom Support
- **Strict Non-OP Command & UI Lockdown**: non-OP players only see and execute the **17 essential BosketBall player commands** (zero `/plugins`, `/version`, `/bukkit:*`, `/minecraft:*`, or `//wand` leaks, and `"Server Owner & Arena Studio"` is strictly hidden unless `player.isOp()`).
- **World Protection Engine**: freezes block gravity (`sand`, `gravel`, `concrete_powder`, `anvils`), fire/lava spread, explosions, weather, leaf decay, and illegal items while seamlessly allowing OP builders in `/build` mode to edit with **FAWE** and **Axiom**.

---

## 🎮 Complete Command Reference

### Player Commands (Available to All Players — `bosketball.play`)

| Command | Aliases | Description |
| :--- | :--- | :--- |
| **`/help`** | **`/?`** | Open the interactive clickable BosketBall Server Guide |
| **`/play [1v1\|2v2\|3v3\|5v5\|3pt\|practice]`** | — | Open the custom Game Modes selector or queue directly |
| **`/practice`** | — | Enter an instant isolated Solo Practice Court (`24s` clock + AI Defender) |
| **`/locker`** | — | Enter the Full-Body 3D Squad Locker Room stage |
| **`/shop [all\|balls\|dunks\|tags]`** | — | Open the Boutique Cosmetics & Collector Tags Shop |
| **`/ranks`** | — | Open the ELO Ranks (`Rookie` → `MVP`), `#1/#2/#3` Badges & Controls Guide |
| **`/friend [add\|remove\|decline] [player]`** | `/f`, `/friends` | Open your Friends GUI or manage friend requests |
| **`/party [name\|invite\|join\|leave] [value]`** | `/p` | Open the Party GUI, invite up to 4 players, or set your Custom Team Name |
| **`/duel <player>`** | *(or `Shift + Right-Click` player)* | Challenge an online player to an instant `1v1` Duel |
| **`/msg <player> <message>`** | `/tell`, `/w` | Send a private message to an online player |
| **`/r <message>`** | `/reply` | Reply to your most recent private message |
| **`/spawn`** | `/hub`, `/lobby` | Teleport safely back to the Lobby Spawn |
| **`/leave`** | — | Leave your active queue, match, spectator session, or 3D Locker Room |
| **`/gg`** | — | Send sportsmanship `GG` after a match (`+5 Coins`) |
| **`/discord`** | — | Display the official clickable Discord invite (`https://discord.gg/pnJhKuU2QK`) |
| **`/pack`** | — | Load or reload the official embedded BosketBall Resource Pack |

### Operator & Administration Commands (`OP` Only)

| Command | Description |
| :--- | :--- |
| **`/bb admin`** | Open the visual **Server Owner Control Center GUI** (Maintenance, Global Mute, `/build`, Spawn, Arena Studio, Holograms, Pack Push, Save & Reload) |
| **`/bb map`** *(or `/bb map <subcommand>`)* | Open the **1-Click Visual Arena Studio GUI** or run Custom Map Architect tools (`create`, `wand`, `pos1`, `pos2`, `barrier1`, `barrier2`, `ceiling`, `hoop`, `setspawn`, `lobbyexit`, `jumbotron`, `preview`, `info`, `save`) |
| **`/bb arena <build\|list\|tp\|delete>`** | Generate a complete stadium (`/bb arena build <id> <Name>`), list, teleport to, or delete arenas |
| **`/bb holo <create\|remove\|list\|clear> [elo\|points\|coins\|play\|shop]`** | Place or manage Fixed Interactive Wall Leaderboards and Lobby Hologram Stations |
| **`/bb maintenance [on\|off]`** | Toggle Maintenance Mode live (locks non-staff joins & updates server list MOTD) |
| **`/bb mutechat [on\|off]`** | Toggle Global Chat Mute live |
| **`/bb rank <set\|elo> <player> <value>`** | Assign a player's staff/override rank (`ADMIN`, `REFEREE`, `NONE`) or ELO rating |
| **`/bb coins <give\|take\|set> <player> <amount>`** | Manage player Coin balances |
| **`/bb tag <grant\|revoke\|equip\|list> <player> [tag]`** | Grant, revoke, or equip any Chat Tag (including `MYPINEAPPLE` & `FOUNDER`) |
| **`/bb 3d setcamera`** | Save custom 3D Locker Room camera & squad pedestal coordinates |
| **`/bb forcestart` / `/bb stop`** | Force-start or stop an active court session |
| **`/bb save` / `/bb reload`** | Flush dirty profiles/arenas to disk or reload `config.yml`, `shop.yml`, `permissions.yml` & embedded pack live |
| **`/build [player]`** | Toggle Creative Builder / Block Edit bypass mode (FAWE & Axiom compatible) |
| **`/setspawn`** | Save the exact Lobby Spawn location & view angle (`spawn.yml`) |
| **`/perm <user\|group\|list\|reload> ...`** | Manage standalone permission groups & user nodes (`permissions.yml`) |

---

## 📂 Configuration & Atomic Storage (`plugins/BosketBall/`)

| File / Folder | Purpose |
| :--- | :--- |
| **`config.yml`** | Branding, **forced lobby layout (`lobby:` — spawn + Play/Shop NPC poses)**, Server List MOTD, FAWE/Axiom protection, Welcome Card links *(Website & Store hidden by default when blank; Discord active)*, zero-setup embedded HTTP Resource Pack settings, match timers, Coin rewards, and physics — cached in memory (`O(1)`) and auto-upgraded |
| **`BosketBall-ResourcePack.zip`** | Automatically extracted & updated from inside `BosketBall.jar` on server startup |
| **`shop.yml`** | Customizable Coin prices for all **54 Chat Tags**, **8 Basketball Skins**, and **5 Dunk Styles** |
| **`permissions.yml`** | Standalone permission groups (`default`, `vip`, `builder`, `referee`, `admin`) |
| **`arenas.yml`** | Generated stadiums & custom Axiom/FAWE court definitions |
| **`holograms.yml`** | Saved Fixed Wall Leaderboards & Lobby Hologram Stations (exact position + `yaw`/`pitch`; the pinned `lobby_play`/`lobby_shop` poses are re-applied from `config.yml` while `lobby.stations.enforce` is on) |
| **`spawn.yml` / `locker-room.yml`** | Saved Lobby Spawn (`/setspawn`, used when `lobby.spawn.enforce` is off) and optional custom Locker Room stage poses |
| **`players/<uuid>.yml`** | Per-player atomic YAML profile (`identity`, `economy`, `cosmetics`, `stats`, `settings`, `social`, `recent-matches`) + `players/_name_index.yml` |

---

## 🛠 Building & Running the Test Suite from Source

The build runs **100% locally — no GitHub Actions, no Maven Central**. One-time setup installs a
JRE 25 + Eclipse ECJ via `pip` and compiles the whole Paper 26.2 / Adventure 5.2 dependency chain
from GitHub source tarballs (tiny compile-only stubs live in `tools/build-stubs/`):

```bash
python3 tools/provision_build_env.py   # once (auto-run by build_plugin.py when missing)
python3 tools/build_plugin.py          # compile + repack + JUnit suite + package releases/
python3 tools/generate_resourcepack.py # optional: regenerate all pack art from scratch
```

Maven / Gradle descriptors (`pom.xml`, `build.gradle.kts`) are kept in sync for IDE imports.
