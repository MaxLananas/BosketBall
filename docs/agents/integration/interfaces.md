# Interfaces — integration notes

Agent 3 (interfaces). Reviewed 2026-11-08. Base branch `arena/a1145fcf-bosketball`.
Companion research: [`../research/interfaces.md`](../research/interfaces.md).

## Ownership boundary

This change touches **only** `src/main/java/fr/bosketball/ui/**`, the new
`ui/kit` package, `src/test/java/fr/bosketball/InterfacesKitTest.java`, the
JUnit registration in `tools/build_plugin.py`, the retargeted source-literal
assertions in `BosketBallEngineTest.java`, the regenerated canonical
`releases/BosketBall.jar`, and these docs.

**`MatchSession.java` (Agent 2) and `Msg.java` (Agent 4) were not edited.**
The interface layer consumes their existing public APIs read-only and needs
**no new hook**. There is no call site for another agent to insert.

## Read-only API contract (do not break these signatures)

The menus call the following existing methods. Agents 2 and 4 may refactor
internals freely, but these signatures are load-bearing for the UI:

- **`MatchSession`** (Agent 2): `arena()`, `mode()`, `score()`,
  `formattedClock()`, `participantCount()`, `teamDisplayName(...)`. Used by the
  spectate and play-mode rows. All are pure reads; the UI never mutates a live
  session from a menu.
- **`Msg`** (Agent 4): `send`, `info`, `warn`, `success`, `error`, `sendAlert`,
  `parse`, `prefixed`, `coinsBadge`, `count`, `playPersonalPackSound`,
  `sendDiscordCard`. `Msg.count` is relied on for the exact `Need N more coins`
  shortfall copy (`String.format(Locale.US, "%,d", value)`).
- **Services** (read-only): `MatchService` (session lookup, bot session,
  invites), `GuildService` (roster, invites, ladder, browse, role gates),
  `ProfileService` / wallet, `QuestService`, `LeaderboardService`. The UI gates
  officer-only controls on `Guild.Role` and never assumes a permission the
  service does not grant.

If any of these change shape, update the call sites in `ui/**` in the same PR;
do not add a compatibility shim in `MatchSession` or `Msg`.

## Contract for any agent adding a menu

1. Build with `MenuSpec.builder(6, title)`; declare immutable `Item`s and let
   the single renderer (`GuiMenuManager.show` / `openFrame`) own inventory
   creation, invisible fill, footer and click dispatch. **Never hand-place an
   item** or call `Bukkit.createInventory` from a screen builder.
2. Copy and state come from `MenuItems`: clickable names start with a verb
   (`actionLabel`), information cards are noun labels (`plain`), unaffordable
   purchases use `unaffordable(...)` (disabled model + exact shortfall), and
   empty/error states use `empty(...)` / `error(...)` with a real next action.
3. The footer rail is reserved: **45** previous, **46** home, **48** close,
   **49** back, **52** season, **53** next (`GuiChrome.isNavigationSlot`). Put
   secondary content at 40/44/47/50/51, never on a reserved slot. Every screen
   declares back + close and at least one forward action; the builder rejects
   a dead-end screen at build time.
4. Pagination uses `Page.of(list, requested, capacity)` + `.pagination(...)`;
   it clamps the index and never silently drops the tail. Back restores the
   previous screen's category, filter, page and target via `NavigationStack`
   (bounded at 16, loop-free).
5. Colours are `Palette` tokens only — no raw `#RRGGBB` in new interface code.
   The only in-line separator is the `Palette.DIM` middot. No stars, pipes,
   emoji or generated small-cap English in player-facing strings.

## What changed (file map)

- **New** `ui/kit/MenuSpec.java` — declarative 6-row spec, `Item` record,
  `Page`, validation (duplicate/out-of-range slots, name rules, dead-end and
  disabled-without-reason rejection).
- **New** `ui/kit/MenuItems.java` — copy/state formatters; visible default row
  icon; `actionLabel` verb + ornament handling.
- **New** `ui/kit/NavigationStack.java` — bounded, loop-free back stack.
- **New** `ui/kit/GuiChrome.java` — reserved footer slots.
- `ui/GuiMenuManager.java` — 19 builders / 18 handlers migrated to specs;
  single renderer + `refreshSlot`; real pagination for friends / add-friend /
  party-invite / history / spectate; honest empty states; removed dead helpers
  (`fillBackdrop`, `navigateBackFromSubMenu`, `isFiller`, `applyStandardRail`,
  `backButton`).
- `ui/GuildGuis.java`, `ui/ProgressionGuis.java`, `ui/PurchaseConfirmMenu.java`
  — rebuilt on the kit; fixed zero-forward-action dead ends (quests slot 33
  "Play a match"; non-officer guild settings slot 40 "View roster").

## Inventory audit (against the live pack)

- **59 distinct icons referenced; all resolve** to a live
  `bosketball:models/item/*.json`. Zero missing textures.
- The 302 pack models with no *literal* UI reference are dynamic assets
  (ball skins, dunk styles, gather/mannequin/setpoint animation frames,
  courts) resolved at runtime from profile state — not unused.
- Cross-file icon reuse is intentional semantic consistency
  (`ref_check` = confirm/claim, `ref_cross` = cancel/close, `ref_friends` =
  roster). Ambiguous reuse was removed: empty shop → `ref_compass`,
  leaderboards → `icon_podium`, reports → `ref_alert`.
- Content slots 1/17/26/35 are deliberate margin filler; the reserved rail is
  never overwritten.

## Build & test evidence

- `python3 tools/build_plugin.py` → `Plugin compiled cleanly (260 classes,
  0 errors)` → `OK (199 tests)` (187 baseline + 12 new `InterfacesKitTest`).
- Resource pack unchanged: SHA-1
  `93113530f92ab4ca09f149d4284a1d6c74755758`, CDN mirrors pinned
  `@pack-93113530`; config synced.
- `MatchSession.java` and `Msg.java` are byte-identical to the base.
