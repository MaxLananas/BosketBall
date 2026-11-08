# BosketBall — the 30 selected features

Status of the 30 items picked out of the 100-item proposal list.

Legend: **DONE** = shipped, builds green · **EXISTED** = already in `LeVraiMain`, verified, nothing
sensible to add · **PARTIAL** = half shipped · **TODO** = not started.

| # | Item | Status | Where / how |
|---|------|--------|-------------|
| 1 | Audible shot clock | DONE | `MatchSession.tickPossessionShotClock`: tick every second of the last 5 s with rising pitch + volume, whistle at 5 s, buzzer on violation. The beep existed but was drowned by the pack mix (see #11). |
| 2 | Real assists | EXISTED | `BasketballEntity.lastPasserId` → `activeAssister` → `MatchSession` credits AST, coins, XP, `ProgressStat.ASSISTS`. Already correct. |
| 3 | Fatigue / endurance | DONE | `MatchSession.tickFatigue()`: sprinting builds fatigue up to 1.0 (+0.0018/tick), recovery −0.0012/tick; caps shot charge by 16 % and adds up to 18 % contest. Action-bar warning at 55 %. |
| 4 | Bucket replay | EXISTED | `CinematicCameraManager` + `recordReplayTick()`; untouched (no measurable win available this pass). |
| 5 | Anti-stuck ball | DONE | `MatchSession.checkStuckBall()`: ball immobile within 0.5 block for 3 s, or under the floor −1.5, → `resetToCenterTipOff()` + shot clock reset + whistle + action bar. |
| 6 | Paid quest reroll | DONE | Contracts already had a paid reroll; added `ProgressService.rerollDailyQuest()` (75 Coins) wired to slot 32 of the quests GUI. |
| 7 | Guild chat `/g` | DONE | `/g <message>` sends, `/g` toggles the mode (persisted as `settings.guild-chat`), routed in `GameplayListener.onAsyncChat`. `/guild` gained the aliases `g`, `gc`. |
| 8 | Guild hierarchy | EXISTED | `Guild.Role` (LEADER / OFFICER / MEMBER) with `canInvite` / `canKick` / `canEditSettings`, enforced in `GuildService.invite|kick|promote|demote|setMotd`. |
| 9 | Shop sorting | DONE | `GuiMenuManager.ShopSort` (default / price ↑ / price ↓ / name / owned first), persisted per player, cycled by clicking the wallet tile, or `/bb shop sort <mode>`. |
| 10 | Shop search | DONE | `/bb shop search <text>` (matches display name **or** price), `/bb shop search clear`. Shown on the wallet tile. |
| 11 | Per-player UI volume + real sound mix | DONE | Sound bank remastered by `tools/remaster_sounds.py` (per-role RMS targets, DC removal, 60 Hz high-pass, soft limiter, −1 dBFS ceiling); `sounds.json` normalised and the missing `ui.confirm` event added; `PlayerSettings.uiSoundVolume` (0–100 %, GUI slot 30); `Sound.Source.PLAYER`; attenuation fixed past 16 blocks (was hard-cutting every court event). |
| 12 | Client locale detection | DONE | `LangService.applyClientLocale()` on join, only while the player never chose a language (`settings.language-auto`); explicit choice pins it. |
| 13 | Maintenance whitelist + MOTD | DONE | The login kick already existed, but bypass was a hard-coded name. Now `motd.maintenance-whitelist` (names or UUIDs) + `motd.maintenance-kick-message`. |
| 14 | Vanish + freeze | DONE | `StaffToolsManager`: `/bb vanish` (hidden from players *and* tab, staff still see you), `/bb freeze <player>` (rotation allowed, translation blocked). |
| 15 | Freeze a live match | DONE | `/bb freezematch` freezes every participant of your current session; `/bb freeze all` releases everyone. Score, clock and state machine untouched. |
| 16 | `/bb pack push` | DONE | `/bb pack push <player|all>` (staff) re-sends the pack without kicking. |
| 17 | Daily Discord report | DONE | `DailyDigestService` + `MatchReporter.sendDailyDigest`: population, 24 h matches, coins in circulation, TPS, top ELO, top wins, open reports, quarantined profiles. `discord.daily-report-enabled` / `daily-report-hour`. |
| 18 | `/bb profiler` | DONE | `util/TickProfiler` instruments 14 tick subsystems; `/bb profiler [reset|on|off|cache]` prints avg, worst, share of the 50 ms budget and TPS. This is the measuring stick for items 21–27. |
| 19 | `Msg.parse` cache | DONE | Bounded concurrent cache (4 096 entries, ≤192-char keys), `prefixed`, `sendCourt/Social/Shop/Alert`, framed cards and titles all go through it. `/bb profiler cache` shows the size. |
| 20 | Crate ring at 2 ticks | DONE | `RewardCrateStation.RING_STRIDE = 2` + `setTeleportDuration(2)` on orbit/shard displays, spin and bob doubled to keep the same visual speed. Halves the display packet rate. |
| 21 | GUI `ItemStack` cache | TODO | Build once, clone on open. Needs a per-menu model pass; the profiler should be used before/after. |
| 22 | Hot-loop `new ArrayList<>(getOnlinePlayers())` | EXISTED | Only two sites (`NametagManager` 1×/s, `ProgressionGuis` on GUI open); both are cold paths and both intentionally iterate a stable copy. Not worth a change — measure with `/bb profiler` first. |
| 23 | Sidebar throttle | EXISTED | `ScoreboardSidebarManager` already refreshes every 10 ticks and diffs every line against `lastRenderedLines`, sending only changed teams. Nothing to gain. |
| 24 | Batched DB writes | TODO | One transaction per tick instead of N. |
| 25 | Incremental YAML writes | TODO | Only modified profile keys. |
| 26 | Async chunk load before a court paste | TODO | |
| 27 | Incremental repaste | TODO | Block diff instead of a full paste. |
| 28 | Behavioural tests instead of source-grep | PARTIAL | Added `CodecFuzzTest` (7 behavioural tests). The source-grep suite is still there; converting it is a separate refactor. |
| 29 | Codec fuzzing | DONE | `CodecFuzzTest` hammers `QuestCodec.decode/decodeList`, `MatchHistoryEntry.deserializeCompact/fromYamlMap` with garbage, truncated and bit-flipped payloads. Result: both codecs are already defensive — the suite now proves it. |
| 30 | Startup integrity | DONE | `StatsManager.quarantineProfileFile()`: unreadable or corrupt profiles move to `players/_quarantine/<uuid>.<epoch>.yml`, the reason is logged as SEVERE, loading continues, a fresh profile is created on the next join. |

## Build

```
python3 tools/provision_build_env.py     # once per machine/session
python3 tools/build_plugin.py            # 211 classes, 0 errors, 76 tests
```

Two consecutive builds leave `git status --porcelain` clean.

## New commands

```
/bb profiler [reset|on|off|cache]   tick cost per subsystem, Msg cache size
/bb vanish                          staff invisibility (players + tab)
/bb freeze <player> | all           freeze / release one player, or everyone
/bb freezematch                     freeze your live match without touching the score
/bb pack push <player|all>          re-send the resource pack without kicking
/bb shop sort <default|price-asc|price-desc|name|owned>
/bb shop search <text> | clear
/g [message]                        guild chat, or toggle it
```

## New config keys

```
motd.maintenance-whitelist: []      # names or UUIDs allowed in during maintenance
motd.maintenance-kick-message: "..." # kick screen (MiniMessage)
discord.daily-report-enabled: true
discord.daily-report-hour: 20
```

Per-player, stored in `plugins/BosketBall/players/<uuid>.yml`:

```
settings.ui-sound-volume    0.00 – 1.00
settings.guild-chat         true/false
settings.language-auto      true until the player picks a language
```
