# ✦ BosketBall — Multiplayer Basketball Plugin & Resource Pack ✦

![BosketBall In-Game HUD & Stadium Preview](docs/previews/ingame_hud_preview.png)

**BosketBall** is a competitive multiplayer basketball plugin and custom resource pack for **Paper / Purpur** (`1.21.10` through the latest release, Java 21–25, Pack Formats `65`–`88`).

It combines an **80Hz sub-stepped 3D physics engine**, **native real-player basketball animations**, a **server Coin economy** (earned and risked in matches, displayed in the Sidebar, and spent in the Tag & Cosmetics Shop), **closed octagonal hoop nets**, **live matchmaking queues**, and an **in-game Custom Map Architect** (`/bb map`).

![BosketBall Resource Pack Showcase](docs/previews/resourcepack_showcase.png)

---

## 📦 Ready-to-Deploy Artifacts (`releases/`)

| File | Description |
| :--- | :--- |
| **`releases/BosketBall.jar`** *(and `BosketBall-26.2.jar`)* | Server plugin for **Paper / Purpur** (`api-version: '1.21'`, Java 21+ bytecode) |
| **`releases/BosketBall-ResourcePack.zip`** | Official BosketBall Resource Pack (`min_format: 65, max_format: 88`, strict JSON) |
| **`releases/BosketBall-ResourcePack.zip.sha1`** | Exact SHA-1 hash for automatic resource pack delivery on join |

---

## 🏀 Core Features

### 1. 80Hz Basketball Physics & Skill Gameplay
- **80Hz Sub-Stepped Ballistics (4 sub-steps/tick)**: gravity, air drag, `Quaternionf` backspin/roll, floor bounce without tunneling, and `ItemDisplay` + `Interaction` collision hitboxes.
- **3D Steel Rim Torus, Tempered Backboard & Closed Octagonal Net**: realistic rim rattles, bank shots off the shooter's square, swish damping, and a 40-panel closed octagonal diamond-weave nylon net with zero side-seam gaps.
- **Skill-Aimed Jump Shots & Green Release Meter**: hold **Right-Click** to gather and aim; release inside the green window for a swish.
- **5 Unlockable Slam Dunk Styles**: `Tomahawk`, `Windmill`, `360 Spin`, `Reverse Baseline`, and `Meteor Jam`.
- **Passes, Alley-Oops, Crossovers, Steals & Blocks**:
  - `[Q]` or `Slot 1`: laser pass to a teammate or **Alley-Oop lob** toward the rim.
  - `[Sneak]`, `[F]`, or `Slot 2`: **Crossover** to switch dribble hands and break nearby defenders' ankles.
  - `Left-Click` on defense: active **steal** on exposed dribbles or **aerial block** (with goaltending protection).

### 2. Match Coin Economy, Sidebar & Shop (`/bb shop`)
- **Live Match Stakes**: earn Coins for buckets (`+5` 2PT, `+8` 3PT/Dunk), assists/steals/blocks (`+4`), match wins (`+40`), MVP (`+15`), and `/gg` (`+5`) — and risk Coins on match losses (`-20`) or early forfeits (`-15`, clamped at `0`).
- **Dedicated Sidebar `COINS` Category**: real-time Coin balance in the lobby and court sidebar alongside Rank, ELO, Stats, and Party.
- **Tag & Cosmetics Shop (`/bb shop`, `/bb tags`, `/bb locker`)**: spend Coins to unlock **54 Chat Tags** (`250`–`2,500` Coins, 1 clean active tag in chat), **8 Basketball Skins** (`0.36x` compact inventory scale), and **5 Dunk Styles**.

### 3. Matchmaking Queues, Clean UI & Custom Maps
- **Instant Non-Blocking Matchmaking (`1v1`, `2v2`, `3v3`, `5v5`)**: queue from `/bb play` or the Play item, roam the lobby freely with a live BossBar queue timer and hotbar **Leave Queue** item, or launch solo **Practice** (`/bb practice` + `/bb bot` AI Defender) and **3-Point Contest** (`/bb 3pt`).
- **Crisp `[TAB]` List, Single-Line Chat & Post-Game Chat Clear**: zero overlapping glyphs in `[TAB]`, compact `[Rank] [Tag] Player » message` chat formatting, ActionBar minor notices, calm play-by-play lines, and automatic match chat buffer clear when a game ends.
- **Official Discord Integration (`/discord`)**: crowned `BOSKETBALL` welcome banner with HiDPI Discord Clyde icon (`https://discord.gg/pnJhKuU2QK`).
- **Custom Map Architect (`/bb map`) & Stadium Builder (`/bb arena build`)**: turn any custom build into a playable court without modifying blocks, or generate a full stadium with bleachers, floodlights, hoops, 24s shot clocks, and a 4-sided Jumbotron.

---

## ⚡ Quick Installation

1. Copy **`releases/BosketBall.jar`** into your Paper/Purpur server's `plugins/` folder.
2. Copy **`releases/BosketBall-ResourcePack.zip`** into `.minecraft/resourcepacks/` (or place it inside `plugins/BosketBall/` and enable `resource-pack.embedded-http-server.enabled: true` in `config.yml`).
3. Start the server and run:
   ```text
   /bb arena build center Bosket Center
   /bb
   ```

---

## 🎮 Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/bb` | Open the Main Hub menu | `bosketball.play` |
| `/bb play [1v1\|2v2\|3v3\|5v5]` | Join a competitive matchmaking queue | `bosketball.play` |
| `/bb leave` | Leave your current queue or court | `bosketball.play` |
| `/bb practice [arena]` / `/bb bot` | Enter Solo Practice & toggle the AI Defender Bot | `bosketball.play` |
| `/bb 3pt [arena]` | Play the 60-second 3-Point Contest | `bosketball.play` |
| `/bb shop` / `/bb tags` / `/bb locker` | Open the Coin Shop for Tags, Basketballs & Dunk Styles | `bosketball.play` |
| `/bb coins [player]` | Check Coin balance (`give\|take\|set` for admins) | `bosketball.play` |
| `/discord` | View the official Discord invite (`https://discord.gg/pnJhKuU2QK`) | `bosketball.play` |
| `/gg` | Send end-of-match sportsmanship GG (`+5 Coins`) | `bosketball.play` |
| `/bb 3d` | Enter the interactive Locker Room stage | `bosketball.play` |
| `/bb stats [player]` / `/bb top [elo\|coins]` | View player stats or Top 10 leaderboards | `bosketball.play` |
| `/bb friends` / `/bb party` / `/bb duel <player>` | Friends, Party (`1–4` players) & 1v1 Duels | `bosketball.play` |
| `/bb settings` | Toggle trajectory guide, floor rings, sounds & replay camera | `bosketball.play` |
| `/bb map <create\|wand\|pos1\|pos2\|barrier1\|barrier2\|ceiling\|hoop\|setspawn\|jumbotron\|preview\|info\|save>` | Custom Map Architect tools | `bosketball.admin` |
| `/bb arena <build\|list\|tp\|delete>` | Build and manage generated stadiums | `bosketball.admin` |
| `/bb rank <set\|elo> <player> <value>` | Set a player's rank or ELO | `bosketball.admin` |
| `/bb forcestart` / `/bb stop` / `/bb reload` | Match control & config reload | `bosketball.admin` |

---

## 🛠 Building from Source

```bash
python3 tools/generate_resourcepack.py
python3 tools/build_plugin.py
```
