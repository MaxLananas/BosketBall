# BosketBall — Audit #15

Scope: the whole plugin (94 Java sources), `plugin.yml`, `config.yml`, the storage/build
tooling and the JUnit suite, on top of `318ec2b`.

Method: local toolchain only (no Maven Central, no CI): `tools/provision_build_env.py` builds
paper-api 26.2 + Adventure 5.2 from GitHub source tarballs, then `tools/build_plugin.py`
compiles with ECJ and runs the suite. Every finding below was **reproduced by reading the code
path**, not inferred from a pattern match. ECJ was additionally run with `-warn:all` (null
dereference analysis, resource leaks, dead stores) to get an objective list instead of guesses.

Verification: **206 classes, 0 errors**, JUnit **OK (69 tests)**, jar + pack rebuilt and
byte-reproducible.

---

## 1. Comment removal

Every line, block and Javadoc comment was stripped from `src/main/java` and `src/test/java`
(**≈3 880 lines across 95 files**) with a character-level scanner that understands string
literals, char literals and text blocks, so URLs (`"https://…"`), MiniMessage payloads and
`"""` blocks are untouched. Two tests were *asserting comment text* and therefore were proving
nothing; they now assert real code invariants:

| Test | Was | Now |
|---|---|---|
| `testDunkZone…UnownedShop` | `shopSrc.contains("Owned catalog cards remain in place and act as equip buttons")` (a comment) | the owned-cosmetics branch is located **before** `profile.coins() < price`, and it equips |
| `testPinnedLobbyLayout…` | `lockerHudSrc.contains("guiHeight / 3")` (a comment) | reads `HUD_BAR_COUNT` / `HUD_ROWS_PER_PAGE` by reflection and asserts `2 + rows <= bars` |

---

## 2. Bugs fixed

| # | Area | Symptom (player-side) | Fix |
|---|---|---|---|
| 1 | `ServerProtectionManager` command lockdown | `/quests`, `/accept`, `/decline`, `/crate`, `/guild`, `/report`, `/tuto`, `/achievements`, `/pc`, `/ignore`, `/afk` answered **"This command is disabled"** to every non-OP player: the whitelist predated 3.7 and dropped 11 player commands before Bukkit could route them. | Lockdown lets BosketBall's own commands through (executors keep their own permission checks) and exempts staff/map/build holders. Whitelist extended as a second line of defence. |
| 2 | same | Any **non-OP staff/builder** lost `/bb`, `/build`, `//wand`, `/gamemode`: every plugin and WorldEdit command was filtered out. | Same fix: `Perms.staff/map/build` holders get unrestricted commands. |
| 3 | `plugin.yml` | `/mail` is documented in the README, announced in-game ("📩 New mail … /mail") and handled in code, but was **never declared**: `Unknown command`. | Declared with `inbox`/`mailbox` aliases, routed, opens the real Mailbox GUI. |
| 4 | `BosketCommand` router | `/giveelo` and `/addelo` had an executor and a `case`, but `normalizeShortcutArgs` never mapped their label, so `args[0]` stayed the *player name* and the switch fell to `default -> sendHelp`: the command printed help and changed nothing. | Labels mapped to the `giveelo` subcommand. |
| 5 | `BosketCommand` router | `/afk` was unrouted too: with zero arguments it fell into the "open the Server Control Panel" branch and answered *"requires bosketball.admin"*. | `/afk` mapped; the zero-argument branch now only opens the panel for the `bosketball` root and sends help otherwise — the whole class of bug is closed. |
| 6 | Admin gating (7 sites) | Server Control Panel, Arena Studio, `/build`, `/setspawn`, `/perm`, hologram placement were gated on bare `isOp()`, while `plugin.yml` advertises `bosketball.admin` / `bosketball.map` / `bosketball.build`. A rank holding the node was locked out; the GUI button was not even painted for them. | New `util/Perms` (`admin/staff/map/build`) used everywhere, including the help card and the hub overlay, so the button and its guard agree. |
| 7 | `CourtSchematic.load` | The gzip/inflater wrapper was never closed: zlib **native** memory was only reclaimed by the GC, once per court load / repaste. | Nested try-with-resources on the decoded stream. |
| 8 | `BotAvatar.spawn` | NPE when called with a null location on a valid arena (`at.clone()` after a null-tolerant guard). | Explicit null guard; the spawn world is taken from the location first, then from the arena. |
| 9 | `BosketConfig` / `StatsManager` | `general.language` is documented in `config.yml` but was **never read**: every new profile got a hard-coded `en`. | Read, exposed as `General.language`, and used as the fallback when a profile has no saved choice. |
| 10 | `ChatModerationService`, `Glyphs` | Dead locals (`reason`, `headline`) — computed and discarded, i.e. features whose intent drifted. | Removed. |

---

## 3. Optimisations & build

| Area | Before | After |
|---|---|---|
| 1 Hz playtime tick | `getOrCreate(player)` for **every online player every second** → re-read of the player profile and a skin-texture capture (a `PlayerProfile` copy) per player per second, forever. | `getIfPresent` first, creation only as a fallback. The capture now happens on join/name change, where it belongs. |
| Release build | `zipfile.write()`/`writestr()` stamped the current time into every entry, so two builds of identical sources produced **different SHA-1s**: `git status` never came back clean, and the pack hash changed on every rebuild (forcing clients to re-download). | Entries written with a frozen timestamp (1980-01-01) and fixed mode bits. Two consecutive builds now produce identical bytes: jar `f3d064f0…`, pack `a2f1a883…`. |
| Jar size | — | 4 568 331 → 4 410 630 bytes (pack entry now deflated instead of stored, deterministic manifest). |

---

## 4. Regression tests added

* `testCommandLockdownLetsPlayersReachTheirOwnCommands` — player commands stay whitelisted, own
  commands reach their executor, staff/build bypass the lockdown, `/gamemode` stays blocked.
* `testEloAdminShortcutsAreRouted` — `/giveelo`, `/addelo`, `/mailbox` and the `/bb` passthrough.
* `testEveryPluggedCommandIsRoutedToItsOwnSubcommand` — walks `plugin.yml` and fails if any
  command that owns an executor does not route to a named subcommand. This is the test that
  would have caught `/giveelo` and `/afk`.

---

## 5. What was verified — and what was not

Verified: compilation (ECJ, Java 21 bytecode, paper-api 26.2), 69 unit/engine tests, jar and
pack packaging, SHA consistency between `config.yml`, the pack and the jar, and byte-level
build reproducibility.

**Not verified — this environment has no Minecraft server and no client.** No headless test can
prove that an inventory renders, that a mannequin animates, or that a block is really protected.
Everything touching packets, entities and worlds was reasoned about from the code and the API,
not observed. Treat the in-game checklist in §7 as the acceptance test.

---

## 6. Known remaining items (deliberately not changed)

| Severity | Item |
|---|---|
| Low | A hard-coded `"MaxLananas"` name bypasses maintenance mode (`MotdManager`). Fine for this server, a hole if the plugin is redistributed. Should become a config key. |
| Low | `RewardCrateStation.tick()` teleports the 8-item ring **every tick**. Moving to a 2-tick cadence with `setTeleportDuration(2)` would halve the entity/packet cost with no visible change. Not changed because it is a visual call, not a bug — it needs your eye in game. |
| Low | Cosmetic `isOp()` reads remain (`GameManager` start-now button now uses `Perms.staff`, `GameplayListener` wand, `MotdManager`). Reviewed one by one; none of them grants a capability. |
| Low | ECJ still reports five advisory warnings, all reviewed as false positives (nullness flow across `session != null && …` guards in `NametagManager`/`TabListManager`/`BotBrain`, and one `new Inflater()` that is now ended by the surrounding stream close). |
| Info | A large part of the suite is still *source-grep* testing (`src.contains(...)`). It caught real regressions, but it is brittle: it breaks on renames and it cannot prove behaviour. Converting them to behavioural tests is the next quality step. |
| Info | Version stays `3.7.0` in `pom.xml`, `build.gradle.kts` and `plugin.yml`. Bump it if you ship this as a distinct release. |

---

## 7. In-game acceptance checklist (5 minutes)

1. As a **plain player** (no op): `/quests`, `/achievements`, `/crate`, `/guild`, `/mail`,
   `/tuto`, `/afk`, `/accept` — all must answer, none must say "command is disabled".
2. As a **plain player**: `/gamemode creative`, `/op`, `//wand` — must still be refused.
3. As a **non-OP account holding `bosketball.admin`**: the hub slot 44 button must appear, open
   the control panel, and `/setspawn`, `/perm`, `/bb admin`, `/giveelo <you> 10` must work.
4. As a **builder holding `bosketball.build`**: `/build`, then `//wand` must work; `/build` off
   must restore adventure mode.
5. `/mail` from the README flow: get a friend request while offline, the notification points to
   `/mail`, and the GUI opens with the request.
6. Join with `general.language: fr` and a **fresh** UUID: the interface must start in French;
   an existing account keeps its own choice.
