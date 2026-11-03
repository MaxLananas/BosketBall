# ✦ BosketBall — Standalone Competitive Basketball Server Engine & Resource Pack ✦

![BosketBall In-Game HUD & Stadium Preview](docs/previews/ingame_hud_preview.png)

**BosketBall** (`v3.7.0`) is an all-in-one competitive multiplayer basketball server plugin and embedded custom resource pack for **Paper / Purpur 26.2** (Java 21–25) — a **26.2-only** release, engineered to run out-of-the-box alongside **FAWE** and **Axiom**.

Drop **`BosketBall.jar`** into `plugins/` and start your server: the official Resource Pack is **embedded directly inside the plugin JAR**, automatically extracted, hashed, hosted via a built-in virtual-threaded HTTP server, and delivered to every player on join with **zero manual configuration**.

![BosketBall Resource Pack Showcase](docs/previews/resourcepack_showcase.png)

---


## 🆕 What's new in 3.7 — the "real server" update

Twenty-one systems that turn BosketBall from a mini-game into a complete platform. Everything
below is built **inside the plugin** — no extra dependency plugins, ever.

| # | Feature | Where it lives |
|---|---------|----------------|
| 1 | **Ranked / Casual queues** — casual never touches ELO and pays `casual-reward-multiplier` (0.40x) | `/play casual <mode>`, Play GUI |
| 2 | **Match accept prompt** with countdown + escalating dodge penalty | `/accept` `/decline` |
| 3 | **Remake** — a leave inside `match.remake-window-seconds` cancels the game with zero stakes | automatic |
| 4 | **Daily / weekly quests + contracts** | `/quests` |
| 5 | **55 achievements** ("hauts faits") with coin payouts | `/achievements`, `/locker` |
| 6 | **Physical reward crate** at `120 62 61` facing West, bought with coins | walk up + right-click, `/crate` |
| 7 | **Optional guided tutorial** (7 steps), hologram at `101 61 61` raised above ground | `/tuto` |
| 8 | **Complete AI rewrite** — mannequin bodies, archetypes, EASY/NORMAL/PRO brains, real shots, passes, steals and trash talk | `/bb bot <easy\|normal\|pro>`, auto-fill on launch |
| 9 | **Match-only chat** — on court, your messages never leak to the lobby | automatic |
| 10 | **Chat moderation** — FR+EN insult filter with leet/separator/stretch evasion resistance, 3 warnings → ban with the Discord link in the reason | automatic |
| 11 | **Persistent guilds** with tag, own ELO and leaderboard | `/guild create\|invite\|join\|leave\|top` |
| 12 | **Mailbox GUI** — friend requests & party invites reach you while offline | `/mail` |
| 13 | **Rich friend presence** — Queued / On Court / Spectating / AFK in TAB and the Friends GUI | `/friend` |
| 14 | **Discord webhook** on match start, score updates, results, rank ups and crate openings | `discord.webhook-url` |
| 15 | **Storage backends** — YAML (default) + mirror to SQLite/MySQL when a JDBC driver is present | `storage.backend` |
| 16 | **SQL-style leaderboards** with a whitelisted `topBy` query | Leaderboard GUIs |
| 17 | **In-memory leaderboard cache** refreshed asynchronously; holograms repaint every 5 s | `leaderboards.holo-refresh-seconds` |
| 18 | **Light anti-cheat** — macro (perfectly regular click) detection, fly/speed rollback during matches | `/status` |
| 19 | **Real i18n** — English is the complete base language, with FR / ES / DE overrides and graceful fallback | `/settings` → Language |
| 20 | **Physical Hall of Fame** — real 3D podium with the season's #1/#2/#3 skins, placed by an operator from the admin panel | OP Control Center → 🏛 |
| 21 | **`/report`** — staff panel + Discord relay | `/report <player> <reason>`, `/reports` |

### AI players (item 8) in detail

The old sparring bot was a single "run at the ball carrier" routine. It is now a full player
simulation:

* **Real bodies.** Each AI is a Paper 26.2 `Mannequin` with a full second skin layer, a
  dedicated 64×64 skin (`bot_easy`, `bot_normal`, `bot_pro`), a jersey number, a nickname and
  an archetype subtitle — the same rig pipeline the lobby NPCs already use.
* **Real brains.** `BotBrain` runs a state machine over OFF_BALL, SPOT_UP, CUT, DRIVE, SHOOT,
  DEFEND and LOOSE_BALL, re-evaluated every `reactionTicks` (9 / 5 / 2 depending on difficulty).
* **Real basketball.** Bots pick up loose balls, dribble through the normal ball physics
  (`bindToVirtualHolder`), shoot with the same projectile solver humans use, dunk, pass to the
  open man, poke the ball loose and contest shots. Every existing rule — shot clock, green
  release window, contest ratio, box score — applies unchanged.
* **Real personality.** Twelve roster archetypes (Splash, Crossover, Wall, Rimwrecker…) each
  bias shooting, driving and passing, and they talk: `[Reaper] Posterised!`
* **Never alone.** `matchmaking.fill-bots-on-launch` fills empty slots the moment a match
  starts, so one queued player always gets a real game.

### Chat moderation (item 10) in detail

`ChatFilter` is layered so **real words are never punished**:

1. normalisation — accent folding, lower-casing, leet (`4→a`, `0→o`, `$→s`…), separator
   stripping (`s.a.l.o.p.e`, `s a l o p e`) and stretch squeezing (`fuuuck`, `saaaloooope`);
2. whole-token matching for short words (`con`, `cul`, `pd`) so `concert`, `culture`,
   `concept` or `compass` are safe;
3. distinctive-stem matching only for long unambiguous stems (`connard`, `salope`…);
4. phrase matching on the collapsed line (`fils de pute`, `nique ta mere`, `ta gueule`);
5. an explicit allow-list for every legitimate word sharing a stem (`assist`, `class`,
   `bass`, `salopette`, `retard`…).

A hit **never blocks** the message: the offending word is masked with `███`, the author alone
sees the censored term and the warning counter, and three warnings trigger a temporary ban
whose reason carries the Discord invite. 42 unit tests cover the dictionary, the evasions and
the false positives.

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
- **Custom Sculpted Container Overlays**: custom negative-space bitmap container layouts (`Shop`, `Collector Chat Tags Vault`, `Basketballs & Dunk Styles`, `Play Modes`, `Main Hub`) with sculpted split-bays, section banners, rarity filter tabs, and color-coded slot bezels.
- **Purchased Native 16×16 Icon Suite & 2D Hotbar**: 115 unique icons from the owner's purchased 180-icon library drive 61 premium menu, mode, social, settings, admin, dunk and prop item sprites plus 54 of the 56 collector tag emblems — always native `16×16` with binary alpha and no 32×32 downsampling. The basketball-specific hoop, between-the-legs and windmill sprites, plus the signature MyPineapple & Sakura emblems, remain custom.
- **Large 3D Basketball Hover Showcase**: hovering any Basketball Skin in **`Basketballs & Dunks`** or **`Shop`** renders a large `44×40px` full-color 3D isometric basketball showcase right inside the item tooltip.
- **Full-Screen Neon Locker Showroom (`/locker`)**: your real character is **hard-frozen at zero walk/fly speed** behind an invisible spectator cinema camera — no drift and no snap-back — while the whole menu is drawn **directly on your screen** by the resource pack (top tab bar `PLAY · PROFILE · SHOP · SOCIAL · SETTINGS`, slanted selection list, live info panel, player/party status). The generated showroom surrounds your Party (`1–4` players) with elevated podiums, counter-rotating neon rings, orbiting basketballs, a reactive marquee, court runway lighting and viewer-only particles. Keyboard only: `W/S` (or `Forward/Back`) navigate, `Q/D` (or `Left/Right`) switch tab or adjust the highlighted value (mode, ball, settings toggles…), `Space` selects, `Shift` exits. Footer always shows your real keybinds.

### 3. Fixed Interactive Wall Leaderboards & Lobby Stations (`/bb holo`)
- **Fixed Dark-Panel Wall Leaderboards (`ELO`, `POINTS`, `COINS`)**: architectural `Billboard.FIXED` multi-row leaderboard boards with a crowned header pill, **7 pixel-aligned individual dark row bars**, interactive **`◀` / `▶` side arrows** that live-cycle categories (`ELO` • `Points` • `Coins` • `Wins` • `Dunks`) right on the wall, a bottom filter bar, click-to-open Top 10 GUI, and a rotating 3D trophy/basketball floating `+1` block above the board.
- **Pinned Lobby Layout (zero setup)**: the official map layout ships in `config.yml` (`lobby:` block) — **every** player who joins is force-spawned at `89.5, 63, 61.5` facing the plaza, and the two clickable 3D NPC stations (superstar = **Play** at `119.5, 62, 56.5`, merchant = **Shop** at `119.5, 62, 66.5`) plus their floating holograms are re-created at their exact position & facing (`yaw`/`pitch`) on every start. Set `lobby.spawn.enforce` / `lobby.stations.enforce` to `false` to manage them by hand with `/setspawn` & `/bb holo` instead.

### 4. Competitive Ranks, Top `#1/#2/#3` Badges, Economy & Social Suite
- **Modernized HiDPI Rank Pills (`/ranks`)**: `Rookie (0)` → `Hooper (1050)` → `Pro (1200)` → `All-Star (1400)` → `Superstar (1650)` → `Legend (1900)` → `MVP (2200+)`, plus `Referee` & `Admin`, and exclusive `FOUNDER` (`MaxLananas`) & `MYPINEAPPLE` collector badges.
- **Live Server `#1 / #2 / #3` ELO Badges**: automatically awarded in Chat, `/gg`, `[TAB]`, Scoreboard, and Leaderboards.
- **Match Coin Economy (`/shop`)**: earn Coins for wins (`+40`), MVP (`+15`), buckets (`+5` 2PT / `+8` 3PT), assists/steals/blocks (`+4`), 3PT Contest (`up to +25`), and `/gg` (`+5`), and spend them on **8 Basketballs**, **5 Dunk Styles**, and **54 Collector Chat Tags**. The Shop is a **stable full catalog**: purchased products stay in their original shelf/grid slot as **Owned** (or glowing **Owned & Equipped**) and can be re-equipped directly without being charged twice.
- **Real Player Heads Everywhere**: authenticated Mojang skin textures are cached locally in each BosketBall profile, so Leaderboards, Friends and Party menus show the players' real heads even while they are offline, with rate-limited background backfill for older profiles.
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
| **`/shop [all\|balls\|dunks\|tags]`** | — | Open the Cosmetics & Collector Tags Shop |
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

### New in 3.7 — Player Commands

| Command | What it does |
|---------|--------------|
| `/play casual <mode>` | Join the casual queue (no ELO, reduced coins) |
| `/accept` · `/decline` | Accept / refuse a found match (decline = queue penalty) |
| `/pc` | Toggle party-only chat |
| `/tuto` | Start, advance, skip or stop the optional tutorial |
| `/quests` | Quest board (daily / weekly / contracts, reroll, claim all) |
| `/achievements` | Achievement browser with category filters and paging |
| `/mail` | Mailbox GUI (offline friend requests & party invites) |
| `/report <player> <reason>` | Report a player (staff panel + Discord relay) |
| `/ignore <player>` | Stop seeing a player's messages |
| `/guild <create\|invite\|join\|leave\|kick\|promote\|motd\|top\|disband>` | Persistent guilds with their own ELO |
| `/crate` | Reward crate info, or open it directly |
| `/friend` | Rich friends list with live presence |

### New in 3.7 — Staff / OP

| Command | What it does |
|---------|--------------|
| `/bb bot <easy\|normal\|pro\|fill\|add\|off>` | Spawn and tune AI players in your match |
| `/reports` | Report + anti-cheat panel |
| `/status` | TPS, storage backend, caches, queue and flag diagnostics |
| `/backup` | Write an immediate profile backup archive |
| OP Control Center → 🏛 | Build / refresh / remove the physical Hall of Fame podium |

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
