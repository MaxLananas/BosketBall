# Reserved icons

Every sprite in `resourcepack/assets/bosketball/textures/item` and its `icons/`
subfolder is either used by the plugin, named by the pack test suite, or listed
here. An icon that is none of those three is a real gap — art drift, a typo in
a model id, or a texture nobody remembered to wire — and
`python3 tools/icon_inventory.py --check` fails on it.

This file is deliberately a plain list, one entry per line, so that claiming or
releasing an icon shows up as a one-line diff in review:

```
- `icon_thing` — why it exists and what will use it
```

The reason matters more than the icon. "Reserved" with no explanation is how a
pack rots: six months later nobody can tell whether an unused sprite is
deliberate or forgotten.

Regenerate the current state with:

```
python3 tools/icon_inventory.py            # human summary
python3 tools/icon_inventory.py --check    # exit 1 if anything is unaccounted for
```

## The reference sheet is a library, not a menu

The 167 `ref_*` sprites are a curated 16x16 GUI icon pack imported verbatim by
`tools/import_ref_icons.py`. It is stocked the way a real icon library is
stocked: it contains more glyphs than any one screen uses, so that the next
screen has vocabulary ready instead of forcing a new drawing.

Keeping the whole library is the point. Deleting the spares to make the
orphan count look tidy would mean the next designer draws a worse version of a
glyph that already exists — the single most expensive mistake available in this
pack, and the reason Agent 6 owns synchronisation in the first place.

So the sheet is reserved as a set, and the bookkeeping is honest about it.

- `ref_` — the whole imported reference library (167 sprites). Reserved as vocabulary for Agent 3 (interfaces). Individual entries graduate into use when a screen adopts them; they are never deleted to reduce this count.

## Authored and waiting for a screen

These are hand-drawn in this repo and have a specific future, not a vague one.

- `icon_history` — the match-history ledger. Drawn and shelved; the screen is Agent 3's, and the icon is ready for it.
- `icon_home` — return-to-lobby affordance for screens that are not the locker. The locker uses `icon_back` instead.
- `icon_lock` — generic locked-state badge for reward rows that are locked without being season-gated (season gating uses `ref_locked_week`).
- `icon_settings` — the settings screen entry point, held for Agent 3's menu rebuild; `ref_emote_settings` covers the in-match toggles today.
- `icon_shop` — shop entry point for the hub rail. `ref_shop` is the current shop glyph, so this is a deliberate second drawing awaiting the rail rework.
- `icon_chest` — crate reveal screen. Reserved: crates were withdrawn from the design, and this glyph is the one piece of that work kept.
- `icon_coin_stack` — the wallet summary card. `ref_coins` is the current single-coin glyph; this is the stack used when the wallet shows a total.
- `icon_check` — confirmation tick for two-step actions, reserved for the handler rework.
- `icon_friends` — social panel header. `icon_team_duo` covers the party tile; this is the header mark.
- `icon_leave` — leave-match affordance for the pause card. The hotbar uses `ref_exit`; this is the tile version.
- `icon_pass` — season pass entry point, held for Agent 8 (season).
- `icon_tab` — tablist toggle. Listed for completeness: the tablist is driven by the HUD layer, not by an item icon.
- `icon_target` — free-throw targeting overlay mark.
- `icon_tags` — chat-tag picker. The picker screen is live but uses `ref_tag_plaque`; this is the compact tile.
- `icon_arena` — arena select tile, awaiting the arena browser.
- `icon_podium` — podium ceremony card, awaiting the post-match ceremony screen.
- `icon_training` — training hub tile. The training menu is live and reaches difficulty through `icon_easy`/`icon_normal`/`icon_pro`; this is the hub mark.
- `icon_ball_classic` — achievement and quest glyph for the classic ball, used where a basketball is the subject rather than a menu action. Drawn for this PR because `CodecFuzzTest` already asked for it and nothing answered; now used by that test's fixture.
