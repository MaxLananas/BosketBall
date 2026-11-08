#!/usr/bin/env python3
"""BosketBall icon language — the one grid, the one light, the one ink.

Every 16x16 sprite in ``textures/item`` is authored by hand elsewhere in
``tools/``.  This module is the shared *finishing* layer that makes the
individual drawings read as a single set:

  * GRID          16x16 canvas, 1px outer margin, 14x14 live area.
  * INK           exactly one outline colour for the whole pack.
  * LIGHT         exactly one key light, top-left at 45 degrees.
  * ALPHA         binary alpha only (no anti-aliased fringe at 16x16).

Why each rule exists, and the research behind the numbers, is written up in
``docs/agents/research/icons.md``.  The short version:

  * Material Design's icon grid fixes a keyline set and a stroke weight so
    that icons of different subjects share optical weight.  At 16x16 the only
    "stroke" available is one pixel, so the keyline set collapses into
    "silhouette fills the live area".
  * Material Symbols guidance is explicit that a stroke weight may not vary
    within a set ("Use a consistent weight within a single UI theme").  One
    ink colour, one outline thickness, is the 16x16 expression of that.
  * Every serious pixel-art guide gives the same lighting rule: pick one
    direction, top-left is the convention, and commit to it across the set.
    A set lit from both sides reads as clip art rather than as a product.

The functions here are pure and idempotent: running the finishing pass twice
produces the same bytes, which is what lets ``generate_resourcepack.py`` call
it on every rebuild.
"""

from __future__ import annotations


from pathlib import Path

from PIL import Image

# ---------------------------------------------------------------------------
# Geometry
# ---------------------------------------------------------------------------

GRID = 16
"""Canvas edge, in pixels.  Microsoft's own item art is 16x16; so is ours."""

MARGIN = 1
LIVE_MIN_X = MARGIN
LIVE_MIN_Y = MARGIN
LIVE_MAX_X = GRID - 1 - MARGIN
LIVE_MAX_Y = GRID - 1 - MARGIN

#: How far a sprite may deviate from the live-area rule before the inventory
#: flags it.  Full-bleed arrows and bars are legitimate; an icon that is only
#: half the live area is a drawing mistake.
MIN_SILHOUETTE_EXTENT = 12

# ---------------------------------------------------------------------------
# Ink
# ---------------------------------------------------------------------------

#: The single outline colour.  Deliberately not pure black: the house palette
#: is a cool slate (#F8FAFC .. #64748B), and a neutral-black outline reads
#: harsh and brownish beside it.  This is that slate taken down to an ink.
INK = (22, 30, 43, 255)

#: Outline colours found in the drawings that predate the language.  Kept as a
#: named list so the conformance pass knows what to re-ink and the inventory
#: can report how much legacy ink was left on disk.
LEGACY_DARK_INK_MAX_LUMA = 62


def _luma(rgb) -> float:
    r, g, b = rgb[0], rgb[1], rgb[2]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


# ---------------------------------------------------------------------------
# Reading a sprite
# ---------------------------------------------------------------------------


def opaque_pixels(image: Image.Image):
    """Yield ``(x, y, rgba)`` for every pixel with non-zero alpha."""
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            pixel = px[x, y]
            if pixel[3]:
                yield x, y, pixel


def silhouette_bounds(image: Image.Image):
    """Bounding box of the opaque silhouette, or ``None`` if the sprite is empty."""
    xs = []
    ys = []
    for x, y, _ in opaque_pixels(image):
        xs.append(x)
        ys.append(y)
    if not xs:
        return None
    return min(xs), min(ys), max(xs), max(ys)


def is_isolated(image: Image.Image, x: int, y: int) -> bool:
    """True when an opaque pixel has no neighbour in its 3x3 block.

    A lone opaque pixel at 16x16 is always an accident: it survives a resize
    or a stray paint operation and reads as dust on the item.
    """
    px = image.load()
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dx == 0 and dy == 0:
                continue
            nx, ny = x + dx, y + dy
            if 0 <= nx < image.width and 0 <= ny < image.height and px[nx, ny][3]:
                return False
    return True


def isolated_pixels(image: Image.Image):
    return [(x, y) for x, y, _ in opaque_pixels(image) if is_isolated(image, x, y)]


def has_partial_alpha(image: Image.Image) -> bool:
    """True when any pixel is neither fully transparent nor fully opaque."""
    px = image.load()
    return any(0 < px[x, y][3] < 255 for y in range(image.height) for x in range(image.width))


def outline_pixels(image: Image.Image):
    """Opaque pixels with at least one transparent 4-neighbour: the silhouette edge."""
    px = image.load()
    w, h = image.size
    edge = []
    for y in range(h):
        for x in range(w):
            if not px[x, y][3]:
                continue
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < w and 0 <= ny < h) or not px[nx, ny][3]:
                    edge.append((x, y))
                    break
    return edge


def ink_colour(image: Image.Image):
    """The most common dark colour on the silhouette edge.

    Returns ``None`` for an outline-free sprite (some fully-saturated glyphs
    such as the check mark are drawn without an outline on purpose).
    """
    px = image.load()
    counts: dict[tuple[int, int, int], int] = {}
    for x, y in outline_pixels(image):
        r, g, b, a = px[x, y]
        if _luma((r, g, b)) > LEGACY_DARK_INK_MAX_LUMA:
            continue
        key = (r, g, b)
        counts[key] = counts.get(key, 0) + 1
    if not counts:
        return None
    return max(counts.items(), key=lambda kv: kv[1])[0]


# ---------------------------------------------------------------------------
# Light
# ---------------------------------------------------------------------------

#: The house key light comes from ABOVE.  See docs/agents/research/icons.md for
#: the measurement that chose the vertical axis over Material's top-left
#: diagonal: on this set the median horizontal shading slope is 0.00 (subjects
#: are left-right symmetric, so horizontal shading carries no signal) while the
#: median vertical slope is -1.78, a clear and consistent light from the top.
KEY_LIGHT_Y = -1
KEY_LIGHT_X = -1  # top-left; horizontal is a soft preference, not a gate.

#: How far the brightest-cluster centroid may drift from the shape centre
#: before the sprite reads as lit from a particular side.  Below this the
#: shading is too flat to judge, which is a legitimate look and not a defect.
NEUTRAL_BAND = 1.2

#: The brightest tone is defined as every pixel within this many luma steps of
#: the sprite's peak.  Small enough to find the specular, wide enough to
#: survive a 2-tone ramp.
SPECULAR_LUMA_TOLERANCE = 6.0

#: Sprites whose brightest tone is the subject rather than a specular.
#: A grinning face is not "lit from below" because its teeth are white; the
#: mouth is the thing the icon is about.  Each entry names the real cause, so a
#: reviewer can disagree with a specific line rather than with a rule.
FIGURATIVE_BRIGHT: dict[str, str] = {
    "ref_face_joy": "grinning mouth is the brightest feature",
    "ref_face_laugh": "teeth are the brightest feature",
    "ref_face_grin": "teeth are the brightest feature",
    "ref_face_nerd": "glasses catch the light on the lower half of the face",
    "ref_face_angry": "gritted teeth are the brightest feature",
    "ref_compass": "needle is the brightest element and points down-left by design",
}

#: Legacy sprites measured as lit from below that are NOT repainted.
#:
#: All twelve are aliases of licensed drawings from the purchased library in
#: ``tools/assets/purchased-icons`` -- glasses, panda, discord mark, racket --
#: plus the twitter mark and the week-status calendar and lock pair.  None of
#: them is our own artwork, so "just draw it correctly" is not an available
#: remedy: the pack's standing rule is that purchased art is never redrawn, and
#: restyling someone else's brand mark is not ours to do either.
#:
#: Two earlier drafts of these notes guessed at the cause instead of reading the
#: sprite: "the glove cuff" on what is a panda's muzzle, and "the racket face
#: carries a specular" on a sprite whose bright tone is the entire racket head.
#: Both were wrong, and wrong in the direction that makes an exception look more
#: considered than it is.  Every line below is read off the pixels.
#:
#: Because 17 of the 176 purchased sources are bottom-lit by the same measure,
#: this list cannot shrink by redrawing.  It shrinks by REMAPPING a sprite to a
#: better-lit source the library already holds -- a visible design change, so it
#: belongs to Agent 3 rather than to this pass.  The measured candidates are
#: listed in docs/agents/icon-language.md.  The build refuses to let it grow.
#:
#: Regenerate with ``python3 tools/icon_inventory.py --baseline``.
LEGACY_BOTTOM_LIT: dict[str, str] = {
    "icon_spectate": "spectator (glasses): the lens white is the brightest mass and fills the lower half",
    "ref_spectator": "glasses: same licensed drawing as icon_spectate",
    "icon_team_duo": "friends (panda): the white muzzle is the brightest mass and sits low in the face",
    "ref_friends": "panda: same licensed drawing as icon_team_duo",
    "icon_discord": "discord (brand mark): the face plate is the brightest area and fills the lower half",
    "ref_discord": "brand mark: same licensed drawing as icon_discord",
    "icon_training": "game_parkour_tag (racket): the bright green head is the whole upper mass, so it reads below the blue",
    "ref_twitter": "twitter (brand mark): the wing highlight runs along the underside",
    "ref_calendar_check": "calendar: the white page is the brightest area and sits under the header",
    "ref_week_open": "calendar: same licensed drawing as ref_calendar_check",
    "ref_locked_week": "lock: the bright body sits below the darker shackle",
    "ref_week_locked": "lock: same licensed drawing as ref_locked_week",
}


def light_vector(image: Image.Image):
    """Where the brightest tone cluster sits, relative to the shape centre.

    Returns ``(dx, dy, tones)``: negative ``dy`` means the light comes from
    above, negative ``dx`` from the left.  ``tones`` is the number of distinct
    colours, used to recognise sprites too flat to shade.

    Measured on the specular cluster rather than on a luminance regression:
    a regression over a symmetric subject (a trophy, a medal) is dominated by
    the subject's own symmetry and reports the opposite of what the eye sees.
    The specular position agrees with the eye, which is the only thing that
    matters for a judgement an artist will trust.
    """
    pixels = [(x, y, p) for x, y, p in opaque_pixels(image)]
    if not pixels:
        return None
    xs = [p[0] for p in pixels]
    ys = [p[1] for p in pixels]
    cx = (min(xs) + max(xs)) / 2.0
    cy = (min(ys) + max(ys)) / 2.0
    peak = max(_luma(p[2]) for p in pixels)
    top = [p for p in pixels if _luma(p[2]) >= peak - SPECULAR_LUMA_TOLERANCE]
    dx = sum(p[0] for p in top) / len(top) - cx
    dy = sum(p[1] for p in top) / len(top) - cy
    return dx, dy, len({p[2][:3] for p in pixels})


def light_label(vector) -> str:
    """Human word for the measured light, as an artist would describe it."""
    if vector is None:
        return "unlit"
    dx, dy, _ = vector
    if abs(dy) <= NEUTRAL_BAND and abs(dx) <= NEUTRAL_BAND:
        return "flat"
    if abs(dy) <= NEUTRAL_BAND:
        return "left" if dx < 0 else "right"
    vertical = "top" if dy < 0 else "bottom"
    if abs(dx) <= NEUTRAL_BAND:
        return vertical
    return f"{vertical}-{'left' if dx < 0 else 'right'}"


def light_verdict(stem: str, vector) -> str:
    """``ok``, ``flat``, ``figurative``, ``reviewable`` or ``wrong-side``.

    ``wrong-side`` is the only defect and is what the integrity gate counts.
    ``reviewable`` marks a known legacy outlier, so the gate can tell a
    pre-existing sprite apart from one an artist just drew the wrong way round.
    """
    if vector is None:
        return "flat"
    dx, dy, tones = vector
    if tones < 3 or (abs(dy) <= NEUTRAL_BAND and abs(dx) <= NEUTRAL_BAND):
        return "flat"
    if dy >= NEUTRAL_BAND:
        if stem in FIGURATIVE_BRIGHT:
            return "figurative"
        if stem in LEGACY_BOTTOM_LIT:
            return "reviewable"
        return "wrong-side"
    return "ok"


def agrees_with_key_light(stem: str, image: Image.Image) -> bool:
    """True when the sprite is not shaded from the wrong side of the key light."""
    return light_verdict(stem, light_vector(image)) != "wrong-side"


# ---------------------------------------------------------------------------
# Finishing
# ---------------------------------------------------------------------------


def ink_silhouette(image: Image.Image) -> int:
    """Re-ink every dark edge pixel to the single house ink colour.

    Only pixels that are *already* dark and sit on the silhouette edge are
    touched, so interior fills, highlights and light-coloured rims survive.
    Returns the number of pixels changed.
    """
    px = image.load()
    changed = 0
    for x, y in outline_pixels(image):
        r, g, b, a = px[x, y]
        if a != 255:
            continue
        if _luma((r, g, b)) > LEGACY_DARK_INK_MAX_LUMA:
            continue
        if (r, g, b, a) != INK:
            px[x, y] = INK
            changed += 1
    return changed


def drop_isolation(image: Image.Image) -> int:
    """Remove stray lone pixels.  Returns the number removed."""
    px = image.load()
    strays = isolated_pixels(image)
    for x, y in strays:
        px[x, y] = (0, 0, 0, 0)
    return len(strays)


def binarise_alpha(image: Image.Image, threshold: int = 128) -> int:
    """Collapse partial alpha to 1-bit.  Returns the number of pixels changed."""
    px = image.load()
    changed = 0
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = px[x, y]
            if 0 < a < 255:
                px[x, y] = (r, g, b, 255) if a >= threshold else (0, 0, 0, 0)
                changed += 1
    return changed


def conform(image: Image.Image) -> tuple[Image.Image, dict]:
    """Apply every mechanical rule.  Returns the sprite and a change report.

    Idempotent: a sprite that already conforms comes back unchanged.
    """
    if image.size != (GRID, GRID):
        raise ValueError(f"icon must be {GRID}x{GRID}, got {image.size[0]}x{image.size[1]}")
    out = image.convert("RGBA")
    report = {
        "alpha": binarise_alpha(out),
        "stray": drop_isolation(out),
        "ink": ink_silhouette(out),
    }
    return out, report


def describe(image: Image.Image, stem: str = "") -> dict:
    """Everything the inventory and the integrity gate need to know."""
    bounds = silhouette_bounds(image)
    vector = light_vector(image)
    ink = ink_colour(image)
    return {
        "size": image.size,
        "bounds": bounds,
        "light": light_label(vector),
        "light_vector": vector,
        "light_verdict": light_verdict(stem, vector),
        "ink": ink,
        "strays": len(isolated_pixels(image)),
        "partial_alpha": has_partial_alpha(image),
        "colours": len({p[:3] for _, _, p in opaque_pixels(image)}),
    }


# ---------------------------------------------------------------------------
# Pack paths — shared by every tool so they cannot drift apart
# ---------------------------------------------------------------------------

ROOT = Path(__file__).resolve().parents[1]
RESOURCEPACK = ROOT / "resourcepack"
BOSKETBALL = RESOURCEPACK / "assets" / "bosketball"
ITEM_TEXTURES = BOSKETBALL / "textures" / "item"
REF_TEXTURES = ITEM_TEXTURES / "icons"
ITEM_MODELS = BOSKETBALL / "models" / "item"
ITEM_DEFS = BOSKETBALL / "items"


def icon_texture_paths():
    """Every icon texture in the pack, sorted, both families.

    ``icon_*`` are the hand-drawn BosketBall set; ``ref_*`` are the curated
    reference sheet imported by ``import_ref_icons.py``.  They share one grid,
    one ink and one light, so they are one set for every purpose that matters.
    """
    icons = sorted(ITEM_TEXTURES.glob("icon_*.png"))
    refs = sorted(REF_TEXTURES.glob("ref_*.png")) if REF_TEXTURES.is_dir() else []
    return icons + refs


# ---------------------------------------------------------------------------
# Icons authored here
# ---------------------------------------------------------------------------

#: A small shared ramp for the sprites this module owns.  Values are the house
#: palette pushed to pixel-art extremes: ``K`` is the ink, ``W`` the 15% tint
#: used for speculars, and the rest are even steps so a 3-tone read survives at
#: 1x on a dark GUI background.
FLAME_RAMP = {
    "K": INK,
    "D": (138, 45, 12, 255),
    "M": (226, 92, 20, 255),
    "L": (255, 150, 40, 255),
    "Y": (255, 214, 60, 255),
    "W": (255, 246, 190, 255),
}

#: ``icon_dunk_flame`` — the "on fire" mark, used by four streak and dunk
#: achievements (``Achievement.DUNK_500``, ``STREAK_3``, ``STREAK_8``,
#: ``STREAK_15``).  It had been referenced from Java since those achievements
#: were written and had never been drawn, so those four rows showed a blank
#: item.  One dominant tongue with a smaller lick to its left, a hot core low
#: and left, and the whole thing lit top-left like the rest of the set.
FIRE_GRID = [
    "................",
    "................",
    ".......K........",
    "......KWK.......",
    "......KWLK......",
    ".....KWLLK......",
    "..K..KWLLMK.....",
    ".KWK.KWLLMMK....",
    ".KWLKKWLLMMK....",
    ".KWLLKWLLMMMK...",
    ".KWYLKLLLMMMK...",
    "KWYYLLLLLMMDK...",
    "KWYYYLLLMMMDK...",
    ".KLLLYMMMMDK....",
    "..KLLMMMMDK.....",
    "...KKKKKKK......",
]

#: ``icon_ball_classic`` — the plain basketball, referenced from ``CodecFuzzTest`` as a
#: representative achievement icon.  Drawn on the same 14x14 live area with the
#: classic four-seam layout so it sits beside ``ball_classic_*`` without
#: pretending to be one of Agent 5's 3D skins.
BALL_RAMP = {
    "K": INK,
    "S": (91, 39, 24, 255),
    "D": (176, 66, 16, 255),
    "M": (226, 92, 20, 255),
    "L": (255, 150, 40, 255),
    "W": (255, 214, 60, 255),
}

BALL_GRID = [
    "................",
    ".....KKKKKK.....",
    "...KKWWLLLLKK...",
    "..KWWLLLLLMMDK..",
    ".KWLLLLLKMMMMDK.",
    ".KWLLLLKSMMMMDK.",
    "KWLKLLLKSDMMMMMK",
    "KWLKLLLKSDMMMMMK",
    "KWLKLLLKSDMMMMMK",
    "KWLKLLLKSDMMMMMK",
    ".KLLKLLKSMMMMDK.",
    ".KLLLKLLSMMMDDK.",
    "..KLLKLLSMMMDK..",
    "...KKMMMKSMDKK..",
    ".....KKKKKK.....",
    "................",
]


def _sprite_from_grid(grid: list[str], ramp: dict) -> Image.Image:
    """Paint a 16-row ASCII grid into a 16x16 sprite using one ramp."""
    image = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    px = image.load()
    if len(grid) != GRID:
        raise ValueError(f"grid must have {GRID} rows, got {len(grid)}")
    for y, row in enumerate(grid):
        if len(row) != GRID:
            raise ValueError(f"row {y} must have {GRID} cells, got {len(row)}")
        for x, cell in enumerate(row):
            if cell == ".":
                continue
            if cell not in ramp:
                raise ValueError(f"row {y} uses undefined ramp key {cell!r}")
            px[x, y] = ramp[cell]
    return image


def authored_icon_sprites() -> dict[str, Image.Image]:
    """Every sprite this module draws, conformed and ready to write.

    ``generate_resourcepack.py`` merges this into the same dictionary as the
    hand-drawn and purchased sets, so an icon added here gets its texture, its
    model and its item definition for free.
    """
    sprites = {
        "icon_dunk_flame": _sprite_from_grid(FIRE_GRID, FLAME_RAMP),
        "icon_ball_classic": _sprite_from_grid(BALL_GRID, BALL_RAMP),
    }
    return {name: conform(image)[0] for name, image in sprites.items()}
