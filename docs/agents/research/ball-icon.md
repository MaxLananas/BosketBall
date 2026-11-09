# Research: a 16x16 basketball, and why the cube stayed

Written for the pass that replaced the 128 px painted leather with 16 px pixel
art. The cube silhouette was a standing decision from the owner of the project —
an icosphere had already been built, shipped to a preview, and rejected in one
line: *"regarde ce que tu as fait, on voit un cube"*. This pass keeps the cube
and rebuilds everything inside it, at the resolution the rest of the pack is
drawn at.

Every number below was measured in this repository. The command that produced it
is given so a later artist can re-run it rather than trust this file.

---

## 1. What "16x16 pixel art" means for a model texture

The pack's sprites are 16x16. A model's face texture is *also* normally 16x16 —
that is the resolution Minecraft's art has been drawn at since 2009, and it is
the resolution at which a texel is roughly one screen pixel at normal viewing
distance. The previous pass drew the leather at 128 px per face, which is a
rendering resolution, not an art resolution: it produces grain that no player
can see at 32 px and that the client then resamples into mush.

What changes when a face goes from 128 px to 16 px:

| | 128 px face | 16 px face |
|---|---|---|
| texels per face | 16 384 | **256** |
| a 1 px seam | 0.8 % of the face | **6.3 %** — a real line, like a real channel |
| shading | smooth Lambert, dither, AO | **six flat tones, hard steps** |
| grain | noise + Worley pebbles | **five 2-texel dashes** |
| detail budget | whatever fits | 256 texels, of which ~40 are seam |

The last row is the whole design problem. On a 16 px face there is no room for a
sphere's worth of detail, so the face has to be organised, not painted.

## 2. The inventory: what a BosketBall sprite actually is

`PYTHONPATH=/home/user/pydeps python3 tools/icon_inventory.py` reports, over the
57 hand-drawn `icon_*.png`:

| measurement | value |
|---|---|
| distinct colours per icon | min **4**, median **9**, max **108** (`duel`) |
| ink colour `#161E2B` | on **56 / 57** |
| isolated pixels (a texel with no 4-neighbour) | **0** |
| partially transparent pixels | **0** |
| bounding box | mostly `(1, 1, 14, 14)` — 1 px margin, 14 px live area |
| key light direction | top **15**, top-left **12**, flat **10**, top-right **10**, left 5, bottom 3, right 1, bottom-left 1 |
| median vertical shading slope | **−1.78** (top of the icon is lighter than its base) |

Read as a specification, that is unusually strict and unusually consistent: one
ink, one light from above (top or top-left, 47 of 57), about nine colours, no
stray pixels, no soft edges. A new ball that ignores any of those reads as an
import.

**Median nine colours is the number that decides the ramp.** Six leather tones
plus ink plus a specular colour is eight — inside the house style, and one tone
more than the four-step ramps the older sprites use, which is the licence the
16 px face needs to hold a sphere.

### The reference construction

The 167 licensed `ref_*.png`, rendered side by side at 8x, share a construction:

1. a thick near-black outline, one texel, closing the whole silhouette;
2. a **4–5 step orange ramp**, evenly spaced, never a gradient;
3. seams drawn 1 texel wide that **kink** — they step sideways as they cross the
   face, which is what makes them read as curves on a sphere;
4. an off-white specular of two or three texels, upper left;
5. on the shaded side, a darker tone applied in a **dithered** patchwork rather
   than as a band.

Nothing in that list survives the direct transplant to a cube face, and the
reasons are in §5.

## 3. What the format says (Minecraft)

* `uv` is on a **0..16** grid, `[0,0]` top-left, whatever the texture's pixel
  size — <https://minecraft.wiki/w/Model>. `texture_size` looks like it changes
  that grid and does not: it is a Blockbench field, read only by the modelling
  tool. Writing atlas pixels into `uv` produces a ball in one flat colour. This
  is why the 48x32 atlas still has six `uv` rectangles on 0..16, and why
  `BallModelContractTest#theSixFaceTilesTileTheAtlasWithNothingLeftOver` re-scales
  them to the shipped image and demands exact coverage.
* Face orientation follows the table in Blockbench's `js/uv/uv.js` — north/south
  flip `v` against the block's `y`, `up` runs `v` along +z, `down` along −z.
  `_face_uv_from_point` implements the same table for the preview renderer, so
  the contact sheet shows what the client draws rather than a mirrored guess.
* `display` scale is clamped to 4 and translation to ±80; the model stays at
  `scale 0.52` so the cube's silhouette is 0.547 block and the simulated radius
  `PhysicsMath.BALL_RADIUS = 0.285` is the ball's *inscribed* radius.

## 4. What the pixel-art references say (16x16 balls)

Searching 16x16 basketball sprites (`image_search`, "basketball pixel art 16x16
sprite") returns two families, and the difference between them is the whole
lesson:

* **The good ones** spend their texels on *shape*: a near-black ring, four
  orange tones arranged as a sphere, and two or three seams that curve. The
  orange is never pure; it is a ramp, and the darkest tone appears in the
  bottom-right quadrant as a *dithered* patch.
* **The cheap ones** ("16-bit icon bundles", most r/PixelArt studies) use a flat
  saturated orange, a black cross, and a white dot. They read as clip art, and
  the reason is measurable: 3–4 colours with no ramp between them.

The transferable rule is not "add a gradient" — it is that **contrast has to be
spent on the read, not on the outline**: a 16x16 ball with a 1 px outline has 196
interior texels to describe a sphere, and every one of them should carry either
tone or structure.

## 5. Deriving the ball: five decisions

### 5.1 The network is three great circles, in direction space

A cube face cannot hold a sphere's seam layout. What it can hold is the cross: an
equator plus four meridians, which is exactly the layout of a real basketball
(it is stitched from a ring and four panels meeting at each pole). Traced in
**direction space** — each texel is classified by the direction it represents, not
by its coordinates on its own face — the same cross appears on all six faces, and
the twelve cube edges carry no discontinuity, because two texels meeting on an
edge *are the same direction*.

Two earlier networks were rejected, and both are worth recording:

* **8 meridians** (two per side face): at 16 px this gives a brick wall. Six
  panels per face is a crate; four quadrants is a ball.
* **cap rings at ±56° latitude** (the "eight panel" ball): the ring becomes a
  jagged diamond about 5 texels across at this resolution, and with four meridian
  rays inside it the top face reads as a crown.

The final network is `MERIDIANS 4`, one meridian down the middle of each side
face, plus the equator, and nothing else.

### 5.2 A 1 px line must be *placed* on the texel grid, and supersampled

Two failures that cost a whole iteration each:

* **Sampling at texel centres loses the line.** A straight meridian at `u = 4.0`
  falls exactly between columns 3 and 4, and a point-sampled field finds neither
  inside the threshold: every seam vanished.
* **A line that lands between two rows is painted two pixels wide.** At
  `lat = 0` the equator covers exactly half of row 7 and half of row 8; a
  coverage threshold of 0.5 accepts both, and the seam becomes a 2 px band.

Both are fixed by the same two rules, which are now in `ball_model`:

1. evaluate the seam field on a **4x supersampled** grid and threshold on
   coverage (`>= 0.45`), so a 1 px channel cannot fall through the 16 px grid;
2. nudge the network by **half a texel** (`HALF_TEXEL`) so the lines land on
   texel *centres*.

Measured after the fix, on the north face: seam texels `31` of 256, one column
at `x = 7` and one row at `y = 7` — a clean cross, one texel wide.

### 5.3 The ramp is anchored on the skin, and hue-shifted

A straight interpolation from `panel_dark` to `panel_bright` is the obvious ramp
and the wrong one: it passes *under* `panel_base`, the colour the skin was
designed around, so most of the ball sits in the muddy bottom half of the line.
The ramp therefore passes through the base tone:

```
dark*0.70, dark, 60 % toward base, base, 75 % toward bright, bright+18 % white
```

and each stop is hue-shifted −5°…+5° — shadows toward red, lights toward yellow.
That shift is pixel art's oldest shading rule and the difference between six
steps that read as tanned leather and six that read as plastic.

### 5.4 The light is baked, but only a little

The client already shades a model's six faces differently; baking a full Lambert
term on top double-shades the ball and turns the two visible faces into flat
plates. What is baked is the part the client cannot do:

* a **limb** term (`DOME 0.18`, falling off as the squared distance from the face
  centre) — this is what makes a flat face read as the skin of a sphere;
* a **crease** of one tone along each face's border, 2 texels wide;
* one **2x2 specular** at the texel that faces the key light hardest.

The specular was first written as a threshold (`unit · L > 0.88`), which lights a
whole quadrant of the lit face and reads as a blown highlight. Selecting the
single brightest texel and stamping a 2x2 block at it reads as a *drawn* highlight,
which is what the reference sprites do.

### 5.5 Eight panel tones survive

Each octant of the ball carries a ±0.05 tone bias, so the cube still has eight
panels in eight slightly different leathers — the one thing the 128 px pass got
right and worth keeping. It is what stops the four quadrants of a face from being
four identical fills.

## 6. Dead ends, kept so they are not retried

| what was tried | what happened |
|---|---|
| bent meridians (`S_BEND 9°`) | a bowed 1 px line becomes a 1 px zigzag |
| wobbling equator (`EQ_WOBBLE 5.2°`) | same, and it breaks the cross |
| 2-texel speckle dashes | 2x2 black blobs at 16 px, not grain |
| full-face Bayer dither + Lambert | a checkerboard crate |
| a meridian through the face centre *and* 8 meridians | four squares per face |
| bright top/left border + ink bottom/right | a painted outline around the cube |
| ink border all round + a dark limb ring | 65 % of the face is not leather: a crate |
| quantising the whole atlas to 128 colours | at 16 px the atlas has six colours; it only blurs the specular |

## 7. What is verifiable without the game

* `docs/previews/ball_before_after.png` — the same cube, the same skin, the same baked
  light, rendered from the 128 px atlas shipped at `c93d174` and from the 48x32 one:
  the old seams are invisible at 32 px, which is the whole argument for the rewrite.
* `docs/previews/ball_contact_sheet.png` — eight skins, 132 px hero, 32 px
  inventory strip, and a flat-lit thumbnail (the worst case: a player with the
  house key light off).
* `docs/previews/ball_icon_sheet.png` — the new ball beside every `icon_*`, at
  1x, 4x and 8x.
* `BallModelContractTest` — 9 tests over the shipped bytes: one element, the same
  silhouette for eight skins, six tiles covering the atlas exactly, one atlas per
  skin with only Fire animated, the display transforms, the thumbnail codes, the
  shop copy, the price ramp, and that only Two-Tone splits its leather.
* `icon_language._check_ball_ramp()` — refuses to write a pack whose 16x16
  inventory sprite has drifted from the colours the model is painted with.
