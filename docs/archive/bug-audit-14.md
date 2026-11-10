# BosketBall — Bug Audit #14

Scope: the whole plugin (90 Java sources, ~45 400 lines), the bundled `config.yml` / `plugin.yml`,
the storage layer and every GUI surface. Every entry below was reproduced by reading the actual
code path (not by pattern matching), then fixed.

Verification: `tools/build_plugin.py` → **197 classes, 0 errors**, JUnit suite **OK (56 tests)**,
resource pack re-signed (SHA-1 `e94065be4c84c3b95cd69236c68fbeab7a7bf10f` (changed only because `config.yml` is embedded in the pack and gained the `practice:` section)), jars packaged.
Live-server behaviour beyond that is *not* simulated — the tests are source + engine level.

Legend: **Area** = file/feature. All 62 items are fixed in this batch.

| # | Area | Symptom | Fix |
|---|------|---------|-----|
| 1 | `moderation/ChatFilter` | Multi-word insults (`fils de pute`, `nique ta mere`, …) were detected, shown to staff as a SEVERE hit and scored 2 warn points — but the message was broadcast **verbatim**. Three such messages earned a wrongful auto-ban. | Phrase hits now mask the message; phrase entries honour the allow-list; a hit is only scored once it is actually censored. |
| 2 | `moderation/ChatFilter` | False-positive phrase entries: `gueule` (as in "ferme ta gueule" only) also silenced the everyday word, plus `eat shit`, `kill yourself`, `go die`, `yourmother`, `suckmy` fired on innocent sentence fragments. | Pruned to unambiguous phrases; `ta gueule` added as the real phrase. |
| 3 | `game/GameManager.handleDisconnect` | The matchmaking-queue entry was never removed. `/queue` after a disconnect silently *left* the queue instead of joining it. | Queue + session index + GG epoch are dropped on quit. |
| 4 | `game/GameManager.handleDisconnect` | `sessionByPlayer` kept a dead session after disconnect, so `/leave`, queue guards and spectate checks answered from a stale match. | Entry removed. |
| 5 | `game/GameManager` | `lastClaimedGgEpoch` grew one entry per player forever. | Pruned on quit, bulk-cleared past 4 096 entries. |
| 6 | `game/GameManager` | Dead `lastGgChatMs` field (never read, never written) suggesting a throttle that did not exist. | Removed. |
| 7 | `command/GgCommand` | `/gg` was a race: only the **first** finisher got the reward, everyone else was told "you already said GG recently", and `completedMatchEpoch` meant nobody could ever claim again. | Per-player epoch claim, correct cooldown message, reward for every finisher. |
| 8 | `progression/ProgressService.careerValue` | `QUEUE_JOINS` was answered with `dailyCratesOpened()`. | Correct projection. |
| 9 | `progression/ProgressService` | `PARTY_MATCHES` was answered with `bestWinStreak()`. | Correct projection. |
| 10 | `progression/ProgressService` | `CASUAL_MATCHES` and `RANKED_MATCHES` both returned `wins + losses`, so casual games completed the **Ranked Grinder** achievement. | Separate counters. |
| 11 | `model/PlayerProfile` + `stats/StatsManager` | No durable casual/ranked/party match counters existed at all, so #9/#10 could not be fixed without persistence. | Three new fields, written to `progression.*-matches` and read back. |
| 12 | `game/MatchSession` | Mode counters were only in memory, lost on restart. | Incremented next to `progress.record()`. |
| 13 | `ui/ProgressionGuis.openMailbox` | Opening the inbox called `markAllRead()` before anything was read: the unread badge was always 0 and the "Mark all read" button was a no-op. | Removed; opening only reads. |
| 14 | `social/SocialService` | The mailbox lived **only in memory** and the cache was dropped on quit, so every unread message was destroyed when the player logged out (only a counter survived). | Mailboxes persist to `mailbox/<uuid>.yml`; flushed on quit. |
| 15 | `social/SocialService` | `unreadMailCount` was set to `box.size()` (total), so a fully-read inbox showed a full badge. | Unread count only. |
| 16 | `social/SocialService.box` | A synthetic "Pending notifications" mail was injected whenever the cache was cold, so players saw a fake message from "Server". | Removed — the real mailbox is loaded instead. |
| 17 | `social/SocialService` + `ui/ProgressionGuis` | Read/clear/deliver mutated prefs but never saved the profile, so the badge reverted after a relog. | `markDirty` + `saveProfileAsync` (plus `markRead(profile, index)` for a single message). |
| 18 | `ui/GuiMenuManager` | Friends menu slots 48/50 were painted but unrouted: "Invite to party" and "Mailbox" were dead buttons. | Routed per menu variant, `slot 53` back handled. |
| 19 | `ui/GuiMenuManager` / `ui/ProgressionGuis` | Two completely different friends layouts shared `MenuType.FRIENDS_MENU`, so slot routing could not be correct for both. | Rich panel gets `FRIENDS_RICH`. |
| 20 | `ui/ProgressionGuis.openFriends` | Heads were tagged `FRIEND:<uuid>` but nothing consumed the tag: clicking any player did nothing. | Click = interact, shift-click = party invite, right-click = remove, non-friend = request. |
| 21 | `ui/ProgressionGuis.openFriends` | Skipping the viewer with `continue` still consumed a grid slot, shifting every head by one. | Viewer removed before the placement loop. |
| 22 | `ui/TabListManager` | `showStatusInTab` was persisted but never read. | Honoured. |
| 23 | `progression/ProgressService` | `showQuestNotifications` was persisted but never read. | Honoured by the quest toast. |
| 24 | `BosketBallPlugin` | `chatAnnouncements` was persisted but never read. | Honoured. |
| 25 | `ui/GuiMenuManager` (BOT_PANEL) | "Remember difficulty" called `settings().matchmaking()` and discarded the result — a silent no-op. | New `setDefaultBotDifficultyLive()` persists the choice. |
| 26 | `ui/GuiMenuManager` (SPECTATE_BROWSER) | Match ended between render and click → null session → the player was yanked to the Locker Room and told it was gone. | Warns and reopens the browser. |
| 27 | `BosketBallPlugin` tick loop | Playtime `markDirty` fired **every second per player**, re-queueing a full YAML rewrite for every online profile. | Throttled to every 300 s (still flushed on quit). |
| 28 | `lobby/RewardCrateStation` | Coins were debited and the prize rolled, but the persist happened 1.5 s later in `reveal()`: a player who logged out during the animation lost both the coins and the item. | Persist immediately after the roll. |
| 29 | `lobby/RewardCrateStation` | The bonus roll was skipped entirely if the player was offline at reveal time. | Bonus is granted regardless; only the fireworks are skipped. |
| 30 | `game/GameManager.joinQueue` | `matchmakingBanUntilMillis` was written by `applyDodgePenalty()` but **never read**, so declining a found match was free — the "queue lockout" message lied. | Lockout enforced with a countdown. |
| 31 | `game/MatchSession.finishThreePointContest` | Only `onlineParticipants().findFirst()` was paid, so in a shared contest one player got the coins/XP/personal best and everyone else got nothing. | Every contestant is paid. |
| 32 | `game/MatchSession` | The contest prize used `addCoins`, so it never counted towards the "earn N Coins" achievements. | `addEarnedCoins`. |
| 33 | `game/MatchSession` (5 sites) + `command/GgCommand` | Block/steal/assist/GG payouts used `addCoins`, undercounting lifetime earnings. | `addEarnedCoins`. |
| 34 | `ui/LobbyHologramManager` | Rank placeholder closing tag was built with `placeCol.substring(1, 8)` → `</#FDE04>` (invalid MiniMessage), leaking the colour across the rest of the line. | Hex stored separately, correct close tag. |
| 35 | `ui/LobbyHologramManager` | Same bug in `valClose` (`"</" + valColor.substring(1)` → `</#FDE047>` twice-broken). | Fixed. |
| 36 | plugin-wide (39 sites) | `toLowerCase()` / `toUpperCase()` without a locale: Turkish-Azeri dotted-i and German ß break player-name and subcommand matching. | `Locale.ROOT` everywhere. |
| 37 | plugin-wide (35 sites) | Default-locale `String.format`: French/German clients rendered `3,5m`, `45,0% FG`, `1,0 blocks` — and worse, `%f` output is then re-parsed by the GUI. | `Locale.ROOT`. |
| 38 | `game/MatchSession.SavedPlayerState` | Restoring a player hard-coded movement speed `0.10` and never restored fly speed, silently nerfing anyone whose lobby speed came from a modifier. | Walk + fly speed captured and restored. |
| 39 | `model/QuestCodec.decode` | The `rewarded` flag was derived from the *claim* slot, which was never encoded separately → a quest could come back claimed-but-unpaid (paid twice) or paid-but-unclaimed. | One durable bit, both flags derived from it. |
| 40 | `moderation/WarnService`, `ChatModerationService`, `GuildService`, `command/BosketBall/BosketCommand` | Player- and staff-authored free text (`reason`, guild name) was interpolated straight into MiniMessage: `<red>` in a warn reason recoloured the sanction notice, and `<click:run_command:...>` was a carrier for click-execute payloads. | `Msg.escapeMini(...)` at every site. |
| 41 | `game/MatchSession.finishMatchAndStartMvpCeremony` | State flip, `notifyMatchCompleted()`, MVP title and the Discord report all ran **before** the `resultsApplied` guard, so a forfeit racing the buzzer played the ceremony twice. | New `ceremonyStarted` guard at method entry. |
| 42 | `game/MatchSession` | MVP was picked with a `max()` over a `ConcurrentHashMap` stream → nondeterministic winner on ties; the "fallback" branch was a byte-identical copy of the primary query (dead code). | Explicit points → assists → name tie-break, dead branch removed. |
| 43 | `game/MatchSession.decideByMvpScore` | An exact tie always gave the win (and the ELO/coins) to IGNIS. | Seeded coin flip on a true tie. |
| 44 | `command/BosketCommand.handleRankAdmin` | The `elo` branch accepted any integer (negative, 10⁹) with no clamp and no `markDirty`, and rejected the `give/take/set` verbs the sibling `/giveelo` accepts. | Clamped to 100..99 999, verbs supported, dirty-then-save ordering fixed, sidebar refreshed. |
| 45 | `command/BosketCommand` (`/bb coins`) | Refused offline players although the profile store is fully offline-aware. | Offline targets resolved through the name index. |
| 46 | `command/BosketCommand` | `Math.max(0, parseInt(...))` silently absorbed negative amounts and still reported success. | Explicit error. |
| 47 | `command/BosketCommand` | Balance edits saved without `markDirty`, so the next autosave could overwrite them. | `markDirty` before the async save. |
| 48 | `command/BosketCommand` | Every admin gate was `isOp()`, so a LuckPerms administrator holding `bosketball.admin` was locked out of reports, warnings, holograms, arena args and tab-complete. | Central `isStaff()`; the Arena Studio GUI stays strictly OP-only by design (asserted by the test suite). |
| 49 | `resources/plugin.yml` | `bosketball.map` was checked in code but never declared, so it was invisible in permission listings. | Declared. |
| 50 | `resources/config.yml` | `practice.world-name`, `practice.max-instances`, `practice.grid-spacing-blocks` are read by `BosketConfig` but absent from the bundled file — a fresh install could not discover or tune them. | Documented section added. |
| 51 | `stats/StatsManager` | Two keys for one feature: `social.party-chat` (default false) and `settings.party-chat` (default true). `/partychat` wrote one, the settings GUI the other. | Single canonical key, legacy key honoured only as a fallback. |
| 52 | `stats/StatsManager.writeNameIndexToDisk` | Returned early on an empty index, leaving a stale `players-index.yml` that resolved names to deleted profiles. | Empty index is written. |
| 53 | `storage/StorageService.backupNow` | Flushed with `flushDirtyAsync()` and immediately zipped, so backups routinely contained the **previous** copy of every dirty profile. | Synchronous flush, failure logged instead of swallowed. |
| 54 | `storage/StorageService.backupNow` | Filename had minute resolution: two backups in the same minute overwrote each other, then `pruneBackups` deleted the survivor. | Second resolution + collision suffix. |
| 55 | `game/MatchSession` AFK watchdog | Warning trigger was `idle == 900`, exact equality. A starved or skipped tick jumped past it and the "move in 15s" warning never fired — the player was just removed. | `idle >= 900 && idle < 1200`. |
| 56 | `game/MatchSession` AFK watchdog | A fresh `Location` was cloned for every participant 20×/second, changed or not. | Clone only on movement. |
| 57 | `progression/ProgressService.evaluateAchievements` | 2 s per-profile rate limit also swallowed the **end-of-match** evaluation (final stat writes land inside the window), so unlocks only appeared next session. | `force` overload used at match end and by the crate. |
| 58 | `ui/GuiMenuManager` (QUESTS) | Daily (10-13), weekly (19-22) and contract (28-29) items were painted but not routed: clicking a finished quest did nothing, "Claim all" was the only way. | Per-quest claim with feedback. |
| 59 | `animation/PlayerVisualRig` | The animation tick called `getOrCreate(owner)` (2× per tick per player): it created placeholder profiles and marked them dirty for a full rewrite on every read. | Read-only `getIfPresent` access. |
| 60 | `util/Msg.send(Audience, …)` | No null guard: a null audience (≈20 call sites pass a possibly-absent player) threw out of the event handler and cancelled the rest of the tick. | Guard. |
| 61 | `social/SocialService` | No service-level single-message `markRead`, so the GUI flipped the flag on the detached entity and the durable counter never moved. | `markRead(profile, index)`. |
| 62 | `command/BosketCommand` tab-complete | `play`, `practice`, `leave`, `shop`, `ranks`, `friends`, `party`, `duel`, `history`, `settings`, `stats`, `top`, `accept`, `decline`, `tuto`, `ignore`, `pack` all worked but were missing from `SUBCOMMANDS`, so `/bb <tab>` hid them. | Added. |

## Notes / intentional behaviour

* The Server Owner & Arena Studio GUI stays **strictly OP-only** (no permission node unlocks it);
  the command layer was aligned with `isStaff()` because it also gates moderation tools.
* `settings.party-chat` is still *written* as a legacy mirror so a downgrade sees a sane value.

## Cleared during this audit (checked, not bugs)

`spectateSession(player, null)`, `averageMemberElo`, `Lang.byCode`, the 38/38 command wiring,
`extractRootCommand`, `SERVER_ADMIN` / `PLAY_MODES` routing, `openBotPanel`,
`PurchaseConfirmMenu` charge/refund, `ArenaManager.parsePose`, `GuildService.loadGuilds`,
`claimDailyLogin`, the `crates.*` keys, the reports-panel id match, `PlayerSettings.floorAuraRings()`
(an alias of `teamFloorRings`).
