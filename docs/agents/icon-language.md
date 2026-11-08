# The BosketBall icon language

Read this before you draw an icon. It is one page on purpose; the reasoning
behind each rule is in [`research/icons.md`](research/icons.md), and the current
state of every sprite is in [`pack-inventory.md`](pack-inventory.md).

## The five rules

### 1. Grid — 16x16, 1px margin, 14x14 live area

The canvas is 16x16 pixels. Nothing is drawn in the outer 1px ring. Full-bleed
bars and arrows may grow to the edge, because a bar that stops before the edge
looks broken rather than framed.

Keep the silhouette inside the live area and **fill it**. An icon that uses
half the canvas reads as a smaller icon next to its neighbours, not as a
quieter one. Only five sprites in the set are off optical centre, and all five
are arrows offset on purpose — that is the standard to hold.

### 2. Ink — one colour, `#161E2B`

`icon_language.INK`. Every silhouette edge, every sprite, no exceptions.

Not pure black. The UI is cool slate (`#F8FAFC` down to `#64748B`); pure black
next to it reads harsh and slightly brown. 42 sprites used to use pure black and
42 used `#1A1C29`; both now use one value.

If you are adding a *new* icon, you do not have to ink it by hand:
`tools/icon_conformance.py` will do it, and `generate_resourcepack.py` runs the
same pass on every rebuild, so it cannot be lost.

### 3. Light — from above, top-left preferred

The key light is above the subject. A highlight sits high; the shadow tone sits
low. This is checked: `tools/icon_inventory.py` reports every sprite's light
direction and fails the build if a sprite is lit from **below**.

Top-left is the preferred diagonal and every icon authored in this PR uses it.
It is not a hard gate, because most subjects here are left-right symmetric (a
ball, a trophy, a medal) and a symmetric subject has no horizontal light
reading. Requiring it would fail half the set for being symmetric.

**Draw the highlight, do not let a script infer it.** Two mechanical attempts to
re-light the twelve legacy offenders are written up in the research page: one
inverted the artwork, the other put a bright band across a racket's strings.
Shading is a drawing decision.

### 4. Alpha — 1-bit, no fringe

A pixel is either fully opaque or fully transparent. No anti-aliased edge, no
semi-transparent drop shadow. At 16x16 a partial-alpha pixel is a smudge, and
Minecraft's item renderer will show it as one.

`sprite.save(path)` after drawing on an RGBA canvas gives you this for free as
long as you never blend.

### 5. No stray pixels

A pixel with no neighbour in its 3x3 block is always an accident. It is what
survives a resize. Check with `tools/icon_conformance.py --check`.

## Palette and ramp

Draw from the house palette, and give every material a three-step ramp:

| step | role |
|---|---|
| highlight | one or two steps above the base; where the key light lands |
| base | the material's actual colour |
| shadow | one or two steps below the base, pushed slightly toward blue/violet |

Keep a sprite to **16 colours or fewer** (a pack invariant that already has a
test). A 16x16 sprite with more than about ten tones is usually over-detailed:
256 pixels cannot show that many steps without looking noisy.

Use `Palette` constants in Java and the matching hex values in art. Never
invent a fourteenth palette colour for an icon.

## Silhouette first, detail never

Block the shape in one mid tone and look at it at 1x. If you cannot tell what it
is at 1x, no amount of shading will save it. Most bad 16x16 icons are not
badly shaded; they are badly silhouetted.

Test at both 1x (hotbar) and 4x (GUI slot). The GUI shows sprites at 2x and
often inside a 3x tooltip; a detail that only reads at 8x is wasted work.

## Naming

New icons: `icon_<domain>_<thing>`.

| good | bad | why |
|---|---|---|
| `icon_dunk_flame` | `icon_fire` | no domain, and "fire" is ambiguous with the ball skin |
| `icon_ball_classic` | `icon_ball` | which ball? |
| `icon_defense_stance` | `icon_defense` | "defense" is a whole domain |

The 39 pre-existing two-segment names (`icon_home`, `icon_shop`, …) are reported
by the inventory and **not** renamed: each is already referenced from up to five
Java files and renaming buys nothing a reader can see. The rule applies to icons
added from now on.

## Wiring an icon up

Three files must agree or the item is invisible:

```
textures/item/<id>.png          the drawing
models/item/<id>.json           {"parent":"minecraft:item/generated",
                                 "textures":{"layer0":"bosketball:item/<id>"}}
items/<id>.json                 {"model":{"type":"minecraft:model",
                                 "model":"bosketball:item/<id>"}}
```

Textures under `textures/item/icons/` are addressed as
`bosketball:item/icons/<id>`.

`python3 tools/icon_inventory.py --sync` creates any missing model or item
definition, and `--check` fails if one is missing. Do not hand-write the JSON.

## Commands

```bash
python3 tools/icon_inventory.py            # every icon: size, palette, light, usage
python3 tools/icon_inventory.py --check    # exit 1 if anything is unaccounted for
python3 tools/icon_inventory.py --markdown # regenerate pack-inventory.md
python3 tools/icon_inventory.py --sync     # create missing model/item JSON
python3 tools/icon_conformance.py          # apply ink / strays / alpha
python3 tools/icon_conformance.py --check  # report drift without changing files
```

## Known exceptions

Two lists in `tools/icon_language.py` record reviewed exceptions. Both are
greppable and both require a written reason per entry, so a reviewer can
disagree with one line rather than with a rule.

- `FIGURATIVE_BRIGHT` — six sprites whose brightest tone is the *subject*, not a
  light (teeth on a grinning face, a compass needle).
- `LEGACY_BOTTOM_LIT` — twelve sprites measured as lit from below.

### The twelve, honestly

All twelve are the same **four licensed drawings** — glasses, panda, discord
mark, racket — plus the twitter mark and the calendar/lock week-status pair,
each shipped under two ids. **None of them is our own artwork**, which means the
obvious remedy is not on the table: this pack does not redraw purchased art, and
restyling someone else's brand mark is not ours to do.

That is also why the list cannot be shortened by drawing. **17 of the 176
purchased sources are bottom-lit by the same measure** — the library itself
contains them, so replacing one means choosing a sprite, not drawing one.

A **remap** — pointing the id at a better-lit source the library already holds —
is the only lever left, so I measured the candidates. **I then looked at them, and
they are wrong**: they are better-lit cartons of a different meaning. Do not use
them. The table is kept so nobody re-derives it and reaches the same false
conclusion I did:

| id | current source | measured better-lit candidates | verdict |
|---|---|---|---|
| `icon_spectate` / `ref_spectator` | glasses | `sunglasses` (−1.88), `heart_eyes` (−1.59), `eyes` (flat) | **rejected** — all three are emoji *faces*, not eyewear. A smiley does not mean "spectate". |
| `icon_team_duo` / `ref_friends` | panda | `emote_social` (−1.93), `social` (−4.25) | **rejected** — `social` is a crab; `emote_social` is a different gesture, not two players |

So the honest position is that these four ids are **not fixable without either
redrawing licensed art or making the icon mean something it does not mean**. The
measurement stands, the gate stands, the documented exception stands. A future
artist with a licence to draw should replace them; a future artist without one
should leave them alone and read this table first.

Not remappable, and not ours to change:

| id | why |
|---|---|
| `icon_discord` / `ref_discord` | Discord's brand mark. Redrawing it is a brand-guideline problem, not an art problem. |
| `ref_twitter` | X/Twitter's brand mark. Same, and the id is also stale. |
| `ref_calendar_check` / `ref_week_open` | a distinct weekday-status pair; no better-lit equivalent carries the meaning |
| `ref_locked_week` / `ref_week_locked` | same |

The gate treats all twelve as recorded exceptions and
**fails the build if a thirteenth appears**. Verified, not assumed: deleting one
line from `LEGACY_BOTTOM_LIT` makes `icon_inventory.py --check` exit 1.
