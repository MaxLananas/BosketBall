# Nameplates (Round 37)

Round 37 replaces the vanilla nametag **entirely**: no more vanilla plaque, no scoreboard prefix,
no leftover black box. Every player carries a pixel-art plate in the style of the owner's
`dedou3d_ranking_CustomNameplates.zip`, one per rank of the ladder.

## 1. Where the art comes from

The zip is kept verbatim in `assets/nameplates/source/` so the artwork can be rebuilt years later.
It ships six materials in three finishes:

| Piece | Size | Notes |
| --- | --- | --- |
| `{mat}_rank_{1,2,3}_left.png` | 16 × 20 | left cap |
| `{mat}_rank_{1,2,3}_middle.png` | 16 × 20 | tileable body |
| `{mat}_rank_{1,2,3}_right.png` | 16 × 20 | right cap |
| `{mat}_rank_3_top.png` | 18 × 9 | crest, only exists in the third finish |

Every piece is drawn for `height: 20, ascent: 16`; the visible bar is rows 3 … 18, which leaves three
transparent rows above and below for the outline to bleed into. The crest sits on a `height: 9,
ascent: 9` line. Those numbers are reused as is — nothing is re-scaled.

## 2. Seven plates for seven ranks

`tools/generate_nameplates.py` is the single source of truth. It recolours the pack's pixels onto
each rank's palette (hue/saturation/lightness remap, alpha untouched, so the pixel art never smears)
and writes 28 PNGs plus their font providers:

| Distinctif | Matériau source | Finition | Teinte | Aperçu |
| --- | --- | --- | --- | --- |
| Rookie | silver | 1 (simple) | `h219 s.16 L.34–.90` `#141A26` | plaque acier bleuté |
| Hooper | bronze | 1 (simple) | `h146 s.52` `#08210F` | plaque bronze verte |
| Pro | silver | 2 (studded) | `h204 s.58` `#071C2E` | plaque acier bleue |
| All-Star | gold | 2 (studded) | `h42 s.82` `#2A1B03` | plaque or |
| Superstar | diamond | 3 (ornate) | `h274 s.58` `#1B0733` | plaque violette à gemmes |
| Legend | netherite | 3 (ornate) | `h352 s.66` `#FFF1F2` | plaque écarlate |
| MVP | or + netherite | 3, hybride | rim `h30 s.88`, cœur `h268 s.45` | plaque or à cœur noir |

MVP is the seventh plate: the pack has no rarest finish, so the generator draws a champion hybrid
(gold rim from the gold plate, darkened netherite core, double pin-stripes on rows 7 and 18, 3 × 3
gems at 22 % brightness). It shares the ornament geometry of the tier-3 plates so it still reads as
part of the same set.

Regenerate and verify:

```bash
python3 tools/generate_nameplates.py          # write PNGs + providers
python3 tools/generate_nameplates.py --check  # pixel + JSON parity, no write
python3 tools/generate_nameplates.py --sheet   # docs/previews/nameplates.png
```

Codepoints are `U+E390 … U+E3AB` (7 ranks × 4 slots: left, middle, right, crest), well clear of the
tab-frame block `U+E370 … U+E383`.

## 3. What the player sees

```
   ╭─┬─┬─┬─┬─╮   rank pill · nickname · 1 450 ELO
   ╰─┴─┴─┴─┴─╯   (+ 🔥 when the player is hot)
```

* The bar is drawn with the bitmap caps and `tiles × middle`, the *sandwich* trick: left cap, n
  middles, right cap.
* The nickname is centred by negative space: `startPx` pushes the text into the bar and `tailPx`
  brings the pen back to the right edge, so the whole line advances by exactly `widthPx` and the
  centred `TextDisplay` is genuinely centred on the head.
* In the lobby the suffix is the ELO rating, on court it is the points of the match (plus the fire
  glyph above three baskets in a row).
* The colour of the nickname is measured on the plate: near black on the light plates, white on
  Legend red and on the black MVP core.

## 4. How it is mounted

Vanilla's nametag cannot be removed one player at a time, so the pack uses a team: every scoreboard
gets `bb_hidden_tag` with `NAME_TAG_VISIBILITY = NEVER`, and every online player is added to it
(`hideVanillaTags` re-adds after a reload, a join or a team change). That kills the vanilla plaque
and its translucent box for good.

The plates themselves are two `TextDisplay` entities per player:

| Display | Height above the body | Content |
| --- | --- | --- |
| plate | `height + 0.34` | the bar + name + suffix |
| crest | `height + 0.19` | the crest glyph, on its own 9 px line |

Both are non-persistent, non-glowing, with `setDefaultBackground(false)`, centred alignment and no
shadow… so nothing is saved in the world file and nothing survives a crash.

Visibility is recomputed every second and every tick for movement:

* the plate is **never shown to its own owner** (first person stays clean, as asked in Round 30);
* hidden when the owner is dead, invisible, vanished for that viewer, in another world, being
  filmed by the cinematic camera, or when the player turned nameplates off in their settings;
* `MOVE_EPSILON`/`setTeleportDuration(0)` keeps the plate glued to the head instead of sliding;
* a sweep every 10 s removes displays whose owner left without the quit event firing.

## 5. Configuration

```yaml
nameplates:
  enabled: true       # master switch; turning it off with /bb reload tears the plates down
  scale: 1.0          # display scale, rebuilt when it changes
  view-range: 1.6     # multiplier on the vanilla 64 block view distance
  show-elo: true      # lobby suffix
```

Per player: `/reglages` → *Nameplates* (`overheadNameplates()`), already used by the old system.

## 6. Tests

`BosketBallEngineTest` covers the chain end to end:

* `round37EveryRankHasItsOwnShippedNameplate` — 7 skins, ladder order, rank ↔ skin bijection,
  codepoint block integrity, the four PNGs of every rank (size + non-empty), the 28 font providers,
  the generator's constants, and the staff borrowing rule;
* `round37NameplateHugsTheNameAndStaysCentred` — 112 cases (7 ranks × 4 names × 4 suffixes):
  content fits, `startPx ≥ 0`, `tailPx ≥ 0`, advance == `widthPx`, no dangling separator, markup
  sanitising;
* `round37NameplateEntitiesAreHiddenFromTheirOwnerAndNeverLeak` — the runtime contract (transient
  displays, no vanilla box, team visibility `NEVER`, `forget()`/`shutdown()` teardown, sweep period,
  invisibility/vanish/opt-out, tick + config wiring).
