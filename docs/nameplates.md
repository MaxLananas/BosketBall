# Nameplates (Round 37)

Round 37 replaces the vanilla nametag **entirely**: no more vanilla plaque, no scoreboard prefix,
no leftover black box. Every player carries a pixel-art plate in the style of the owner's
`dedou3d_ranking_CustomNameplates.zip`, one per rank of the ladder.

Round 38 fixed what the first version got wrong on a real server: the plate now carries **the
nickname and nothing else** (the rank pill, the ELO and the crest are gone — the rank and the ELO
already live in the tab list and the sidebar), the bar is sized against the caps' *visible* art
instead of their 16 px cells, and the ink is chosen to contrast with each plate's fill.

## 1. Where the art comes from

The zip is kept verbatim in `assets/nameplates/source/` so the artwork can be rebuilt years later.
It ships six materials in three finishes:

| Piece | Size | Notes |
| --- | --- | --- |
| `{mat}_rank_{1,2,3}_left.png` | 16 × 20 | left cap |
| `{mat}_rank_{1,2,3}_middle.png` | 16 × 20 | tileable body |
| `{mat}_rank_{1,2,3}_right.png` | 16 × 20 | right cap |

Every piece is drawn for `height: 20, ascent: 16`; the visible body is rows 6 … 19, which leaves six
transparent rows above for the cap ornaments to use. Those numbers are reused as is — nothing is
re-scaled.

The caps do not fill their 16 px cell: a tier-1 cap draws three pixels at the outer edge, a tier-2
eight, a tier-3 twelve. The layout subtracts that margin (`NameplateSkin.edgeLeft/edgeRight`), and
`round37EveryRankHasItsOwnShippedNameplate` measures it back out of the PNGs so the constants can
never drift from the art.

The pack also ships an 18 × 9 crest above the third finish. Round 38 does not use it: one ornament
over the plate was one more thing to line up, for no information.

## 2. Seven plates for seven ranks

`tools/generate_nameplates.py` is the single source of truth. It recolours the pack's pixels onto
each rank's palette (hue/saturation/lightness remap, alpha untouched, so the pixel art never smears)
and writes 28 PNGs plus their font providers:

| Distinctif | Matériau source | Finition | Teinte | Remplissage | Encre du pseudo |
| --- | --- | --- | --- | --- | --- |
| Rookie | silver | 1 (simple) | `h219 s.16 L.34–.90` | `#CFD3DC` clair | `#141A26` |
| Hooper | bronze | 1 (simple) | `h146 s.52` | `#95DDB4` clair | `#0A2E19` |
| Pro | silver | 2 (studded) | `h204 s.58` | `#B7D7EC` clair | `#071C2E` |
| All-Star | gold | 2 (studded) | `h42 s.82` | `#F3D285` clair | `#2A1B03` |
| Superstar | diamond | 3 (ornate) | `h274 s.58` | `#CFACE9` clair | `#1B0733` |
| Legend | netherite | 3 (ornate) | `h352 s.66` | `#C3283D` sombre | `#FFF1F2` |
| MVP | or + netherite | 3, hybride | rim `h30 s.88`, cœur `h268 s.45` | `#28173C` sombre | `#FFF7E4` |

The five lowest ranks are polished metal, i.e. a **light** fill: their nickname is a near-black ink,
not the near-white one Round 37 used (that was the "on voit mal le pseudo" screenshot). The two top
plates are dark and take a cream ink. `round38NameplateInkContrastsWithItsPlate` computes the WCAG
contrast of every pair and requires at least 4.5:1.

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

Codepoints are `U+E390 … U+E3A4` (7 ranks × 3 slots: left, middle, right), well clear of the
tab-frame block `U+E370 … U+E383`.

## 3. What the player sees

```
   ╭─┬─┬─┬─┬─╮
   │ MaxLananas │   nickname only, no rank, no ELO
   ╰─┴─┴─┴─┴─╯
```

* The bar is drawn with the bitmap caps and `tiles × middle`, the *sandwich* trick: left cap, n
  middles, right cap.
* The number of tiles is the smallest one that fits `nickname + 2 × 12 px` **inside the caps' art**,
  so the bar always covers the name.
* The nickname is centred by negative space: `startPx` pushes the text into the bar and `tailPx`
  brings the pen back to the right edge, so the whole line advances by exactly `widthPx` and the
  centred `TextDisplay` is genuinely centred on the head.
* The ink is the one the plate can carry: near black on the five metal plates, cream on Legend and
  MVP.

## 4. How it is mounted

Vanilla's nametag cannot be removed one player at a time, so the pack uses a team: every scoreboard
gets `bb_hidden_tag` with `NAME_TAG_VISIBILITY = NEVER`, and every online player is added to it
(`hideVanillaTags` re-adds after a reload, a join or a team change). That kills the vanilla plaque
and its translucent box for good.

The plate itself is **one** `TextDisplay` per player (`height + 0.30` above the feet), non-persistent,
with `setDefaultBackground(false)`, centred alignment, no shadow and no interpolation, so nothing is
saved in the world file and nothing survives a crash.

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
  scale: 1.15         # display scale, rebuilt when it changes
  view-range: 1.6     # multiplier on the vanilla 64 block view distance
```

Per player: `/reglages` → *Nameplates* (`overheadNameplates()`), already used by the old system.

## 6. Tests

`BosketBallEngineTest` covers the chain end to end:

* `round37EveryRankHasItsOwnShippedNameplate` — 7 skins, ladder order, rank ↔ skin bijection,
  codepoint block integrity, the three PNGs of every rank (size + non-empty), the 21 font providers,
  the cap margins measured back out of the PNGs, the generator's constants, and the staff rule;
* `round38NameplateInkContrastsWithItsPlate` — WCAG contrast of every ink against the median fill
  of the tile it is drawn on (≥ 4.5:1);
* `round38NameplateHugsTheNameAndStaysCentred` — 35 cases (7 ranks × 5 names): the name fits inside
  the caps' art, `startPx ≥ edgeLeft`, `tailPx ≥ 0`, the remaining room is split evenly to within a
  pixel, advance == `widthPx`, the ink is the skin's, markup sanitising, blank names;
* `round38NameplateEntitiesAreHiddenFromTheirOwnerAndNeverLeak` — the runtime contract (one
  transient display, no vanilla box, team visibility `NEVER`, `forget()`/`shutdown()` teardown,
  sweep period, invisibility/vanish/opt-out, tick + config wiring).

## 7. Round 42 — the holes between the tiles

The owner kept sending screenshots of a plate that read as a bar cut into four blocks, with a
transparent gap at every tile junction. The art was not to blame: every `np_*_middle.png` is
16×20 and opaque on all sixteen columns, and the cap margins measured back out of the PNGs match
`NameplateSkin.edgeLeft()/edgeRight()` exactly.

The cause is the client. Two neighbouring bitmap glyphs are two quads, and the display is scaled by
`nameplates.scale` (1.15 by default). At a fractional scale the client rounds each quad onto its own
position, so a fraction of a pixel of transparency opens at every junction — invisible in a whole
number scale, obvious at 1.15, and worse the further away the camera is.

Every tile is therefore pulled `NameplateLayout.TILE_OVERLAP_PX` (1 px) into the next one with a
negative space glyph:

```java
for (int i = 0; i < tiles; i++) {
    bar.append(Component.text(overlap, Glyphs.BITMAP_STYLE));   // Glyphs.shift(-1)
    bar.append(Component.text(String.valueOf(skin.middle()), Glyphs.BITMAP_STYLE));
}
```

The middle tiles are a uniform fill, so one pixel of overlap changes nothing visible; the seam lands
on opaque art and cannot open, whatever the scale, the GUI size or the distance. The overlap is
subtracted from the advance (`barWidthPx(tiles) = 16 * (2 + tiles) - (1 + tiles)`), so the plate is
still exactly as wide as the bar it draws and stays centred on the head. The resource pack is
untouched: no new glyph, no new provider, so the `pack-…` SHA is unchanged.

* `round42NameplateTilesOverlapSoNoSeamCanOpen` — the width identity for 2…18 tiles, strictly
  growing art per rank, one middle glyph per tile, at least one overlap glyph per junction, and
  `startPx + contentPx + tailPx == widthPx` for the 21 sampled plates.
