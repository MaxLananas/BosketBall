# Research: making 224 sprites read as one set

Written before the conformance pass, while the pass was still being designed.
The numbers below are measurements taken from this pack, not estimates, and the
command that produced each one is given so a later artist can re-run it.

---

## 1. What the pack actually contained

Two families, drawn in different sessions, by different hands, for different
reasons:

| family | count | where it lives | who drew it |
|---|---|---|---|
| `icon_*` | 57 | `textures/item/icon_*.png` | this repo, three separate painters (`cuboide_icons.py`, `premium_ui_overhaul.py`, `builtbybit_gui_and_skins.py`) |
| `ref_*` | 167 | `textures/item/icons/ref_*.png` | a licensed 16x16 reference sheet, imported verbatim |

All 224 are 16x16 RGBA with true 1-bit alpha, which is the one thing the
previous sessions got right everywhere. Everything else had drifted.

### The ink had drifted into 33 colours

Measured with `icon_language.ink_colour` — the most common dark colour on each
sprite's silhouette edge:

| ink colour | sprites |
|---|---|
| `#1A1C29` | 42 |
| `#000000` pure black | 42 |
| `#06090B` | 11 |
| `#131313` | 8 |
| `#1A1514` | 8 |
| *28 further colours* | ~60 |
| none (unoutlined glyph) | 7 |

Thirty-three ink colours in one set is the single loudest reason the pack reads
as a pile rather than a product. Two sprites on the same row can have a cool
slate outline and a neutral black one, and the eye reads that difference as
"these two things are not related" before it reads anything else.

### There were four stray pixels

`icon_defense` carried four lone opaque pixels with no neighbour at all. At
16x16 a pixel with no neighbour is never intentional; it is what survives a
resize or a stray paint call.

### The shading was lit from both sides

The first metric I tried — luminance-centroid offset, and then a regression of
mean row luminance — reported that most of the set was lit from the *right*.
That was wrong, and I nearly built a gate on it. The reason is worth recording:

> A regression over a symmetric subject is dominated by the subject's own
> symmetry. A trophy is brighter in the middle than at its edges, so a
> vertical regression reports "lit from the top" only if the middle happens to
> sit high. A medal with a bright ribbon on top and a bright disc below reports
> whatever is bigger.

What does agree with the eye is the position of the *brightest tone cluster* —
the specular — relative to the shape's bounding-box centre. Validated against
twelve sprites by eye before being used for anything. On that measure the set
was:

| verdict | sprites |
|---|---|
| lit from the top (agrees with the house light) | 159 |
| flat — too few tones to shade, legitimate | 45 |
| brightest tone is the subject, not a light | 6 |
| lit from below | 12 |

So the set was overwhelmingly lit from the top, and the defect was real but
narrow: twelve sprites, representing six distinct drawings, four of which are
duplicated between the two families.

### Silhouette weight was consistent; framing was not

83 of 224 sprites run to the canvas edge, and 31 are under 12px in one axis (all
of them horizontal bars, arrows, and a gem — legitimate). Optical centring was
already good: only 5 sprites sit more than half a pixel off centre, and all five
are arrows offset on purpose.

This is the finding that shaped the whole pass: **the set did not need to be
redrawn. It needed one ink and one light.**

---

## 2. What real icon systems do, and what transfers to 16x16

### Material's keyline grid

Google's icon grid fixes a keyline set — square 18dp, circle 20dp, vertical
rectangle 16x20, horizontal rectangle 20x16 on a 24dp canvas — so that a
circular subject and a square subject end up with the same optical size
instead of the square one looking bigger.

At 16x16 with a 1px margin the live area is 14x14 and the keyline set collapses
to a single rule: **the silhouette fills the live area**. That is what this pack
already did, which is why silhouette weight was the one thing that did not need
fixing. The rule is written down rather than discovered again.

- [Material Design — Icons style](https://m1.material.io/style/icons.html)
- [Material Design 3 — icon grids and keylines](https://m3.material.io/styles/icons)

### Stroke weight: the one instruction that transfers directly

Material's guidance is blunt about consistency: *"Maintain a 2dp width for all
stroke instances, including curves, angles, and both interior and exterior
strokes"*, and *"Use a consistent weight within a single UI theme."* Thirty-three
ink colours is the 16x16 equivalent of using eleven stroke widths.

At 16x16 there is only one stroke available, so "one stroke weight" becomes
"one ink colour, on every edge". That is the whole conformance pass.

### Material Symbols' weight/fill axes

Material Symbols exposes weight (100–700), fill (0/1), grade and optical size as
independent axes, which lets one glyph carry both a resting and an active state
without a second drawing. The lesson taken here is narrower than "support
variable weight": **a state change should be a change in one axis, not a
different glyph.** The pack's on/off pairs (`icon_sound_on`/`icon_sound_off`,
`icon_bot_on`/`icon_bot_off`) already follow this — same silhouette, different
tone. Keep doing that.

### The pixel-art lighting convention

Every pixel-art reference converges on the same sentence: pick a light
direction, top-left is conventional, and commit to it across the set. Two of
them say it almost identically: *"Pick a light direction and stick to it across
all your sprites. Top-left is the standard,"* and *"Using a shared palette
across the entire game creates visual consistency… Light Source Direction —
Upper-left is standard. Keep consistent across all assets."*

I chose **top** as the gate axis rather than top-left, on evidence from this
set:

- the median vertical shading slope is **−1.78** (a clear, consistent light
  from above);
- the median horizontal slope is **0.00**.

The horizontal axis carries no signal here because most subjects are
left-right symmetric — a basketball, a trophy, a medal. Gating on a horizontal
direction would fail half the set for being symmetric. Top-left stays the
*preference* (and every sprite authored in this PR uses it); **"not lit from
below" is the gate**, because that is the mistake that is actually visible.

- [16x16 pixel art guide — one light direction](https://www.sprite-ai.art/guides/how-to-create-16x16-pixel-art)
- [Pixel art game asset conventions](https://pixnote.net/en/learn/game-assets/)

### Why not auto-relight the twelve?

I tried it, twice, and show the results here because "I considered it and it was
wrong" is more useful to the next artist than silence.

**Attempt 1 — mirror the tone ramp per material.** For each hue family, permute
the existing colours so the brightest tone that sat low sits high. This
preserves the palette exactly and cannot invent a colour. It also broke the
drawings: `icon_home`, `icon_arena` and `icon_battle` all came back with their
forms inverted, because a glossy highlight is *part of a drawing*, not a
separate layer. A trophy's highlight must stay on the trophy's rim.

**Attempt 2 — nudge one ramp step on the top and bottom bands.** This fixed
`icon_discord` cleanly. It made `icon_training` worse — the racket's design
places its specular deliberately, and stepping it produced a bright band across
the middle of the strings.

The conclusion is the one in `docs/agents/icon-language.md`: **shading is a
drawing decision and mechanical passes must not make drawing decisions.** The
mechanical layer owns ink, strays and alpha. A human owns light.

### What I got wrong the first time, and how I found out

The first draft of the exception table said "two of the twelve are our own
drawings and are worth a human's five minutes". That was false. Checking each
sprite against `PURCHASED_ICON_MAP` and against the shared-body digests showed
that **all twelve are aliases of licensed purchased artwork**, including the two
I had put in a fix-it table and including `icon_training`, which I had assumed
was hand-drawn because `premium_ui_overhaul.py` still contains hand-painted
routines for a handful of ids.

Reading the pixels caught a second, worse error: I had written that
`icon_team_duo`'s bright band was "the glove cuff". It is a panda, and the bright
band is its muzzle. A cause written from the id rather than from the sprite is
worse than no cause, because it makes the exception look researched.

The remedy changed with the finding. Redrawing is not available (purchased art is
never redrawn, and two of the four are third-party brand marks), and it would not
work anyway: **17 of the 176 purchased sources are bottom-lit by this same
measure**, so the library contains the problem rather than a way out of it. What
is available is a **remap** to a better-lit source the library already holds —
measured candidates are in the language page. That is a visible design change and
belongs to Agent 3, so this pass records it, measures it, and gates it instead.

---

## 3. The rules this produced

| rule | value | enforced by |
|---|---|---|
| canvas | 16x16, 1px margin, 14x14 live area | `icon_inventory --check`, pack tests |
| ink | one colour, `INK = #161E2B` | `icon_conformance.py`, generator |
| alpha | 1-bit only | same |
| strays | none | same |
| light | not from below; top-left preferred | `icon_inventory --check` (gate), hand review |
| naming | `icon_<domain>_<thing>` for new icons | pack test; legacy names reported not renamed |
| palettes | <= 16 colours per sprite | pack test (existing invariant) |

`INK` is `#161E2B` — the house dim slate `#64748B` taken down to an ink — rather
than pure black, because 42 sprites used pure black and it reads harsh and
brownish beside a cool slate UI. The new value keeps the outline cool and lets
it sit inside the palette family instead of beside it.
