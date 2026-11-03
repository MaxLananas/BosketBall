#!/usr/bin/env python3
"""BosketBall GUI kit — "Court Console" (v3).

Every container overlay the server uses is generated here, from one coherent art language:

    dark slate panel  ·  bright chrome case  ·  floating title plaque  ·  accent-keyed modules

The language is lifted from the reference sheet the player supplied (dark slate windows with a
hanging plaque, chunky beveled blocks, diagonal stripes, bolts in the corners) and re-tinted into
the server's basketball DNA: burnt orange, court navy, gold, and a faint key/arc etched into the
floor.

GEOMETRY CONTRACT — measured against the live client, never change without re-measuring:

* image x = 0 is the left edge of the container, 176px wide;
* **image y = 7 is the top edge of the container**; pixels above are the band the plaque floats in;
* slot row ``r`` / column ``c`` has its 16x16 well at ``(8 + 18c, 25 + 18r)``;
* the runtime caption is laid out on the *default font* baseline: its glyph body occupies
  y 12..20, so the plaque is drawn empty exactly there (a baked title would be printed twice —
  that bug shipped once already);
* the texture height MUST equal the container height, i.e. ``7 + 18 + 18*rows + 4``:
  6 rows -> 137, 5 rows -> 119, 4 rows -> 101. A 54-slot menu drawn on a 101px overlay leaves
  its bottom two rows on vanilla grey, which is what made the confirmation screen look broken.

Everything is pure pixel art at 1x: no antialiasing, no gradients, crisp at every GUI scale.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

# ══════════════════════════════════════════════════════════════════════════════════════════
#  PALETTE  (sampled from the reference sheet, then pushed toward the court)
# ══════════════════════════════════════════════════════════════════════════════════════════
INK = (10, 12, 20)           # every outline
PANEL = (64, 78, 104)        # #404E68 slate panel
PANEL_DK = (48, 60, 84)
PANEL_LT = (82, 98, 126)
PANEL_HI = (104, 122, 152)

RIM = (188, 206, 226)        # light chrome rim
RIM_HI = (240, 247, 255)
RIM_DK = (112, 130, 156)

PLAQUE = (36, 44, 64)        # #242C40 plaque
PLAQUE_HI = (52, 62, 88)

GLASS = (86, 102, 132)       # slot glass
GLASS_DK = (56, 68, 94)
GLASS_LT = (104, 122, 154)

GOLD = (233, 190, 66)
GOLD_HI = (255, 226, 140)
GOLD_DK = (168, 126, 26)
ORANGE = (232, 122, 24)
ORANGE_HI = (255, 174, 70)
ORANGE_DK = (160, 74, 10)

RED = (214, 72, 78)
RED_HI = (255, 130, 130)
RED_DK = (140, 38, 48)
GREEN = (88, 184, 116)
GREEN_HI = (150, 232, 170)
GREEN_DK = (44, 130, 76)
BLUE = (78, 150, 226)
BLUE_HI = (150, 205, 255)
BLUE_DK = (44, 100, 168)
VIOLET = (150, 118, 220)
VIOLET_HI = (196, 176, 250)
VIOLET_DK = (104, 76, 176)
ROSE = (208, 122, 148)
ROSE_HI = (244, 178, 198)
ROSE_DK = (148, 74, 100)
SILVER = (176, 184, 196)
BRONZE = (198, 132, 48)

ACCENTS = {
    "orange": (ORANGE, ORANGE_HI, ORANGE_DK),
    "gold": (GOLD, GOLD_HI, GOLD_DK),
    "ice": (BLUE, BLUE_HI, BLUE_DK),
    "violet": (VIOLET, VIOLET_HI, VIOLET_DK),
    "rose": (ROSE, ROSE_HI, ROSE_DK),
    "mint": (GREEN, GREEN_HI, GREEN_DK),
    "red": (RED, RED_HI, RED_DK),
    "silver": (SILVER, (222, 230, 242), (118, 128, 142)),
}

# ══════════════════════════════════════════════════════════════════════════════════════════
#  GEOMETRY
# ══════════════════════════════════════════════════════════════════════════════════════════
WIDTH = 176
HEADER_PAD = 7          # pixels above the container top (plaque band)
CONTAINER_TOP = HEADER_PAD
ROW_PITCH = 18
WELL = 16


def slot_xy(slot: int) -> tuple[int, int]:
    row, col = divmod(slot, 9)
    return 8 + col * ROW_PITCH, CONTAINER_TOP + 18 + row * ROW_PITCH


def overlay_height(rows: int) -> int:
    return HEADER_PAD + 18 + rows * ROW_PITCH + 4


def _c(rgb, a: int = 255):
    return (rgb[0], rgb[1], rgb[2], a)


# ══════════════════════════════════════════════════════════════════════════════════════════
#  PRIMITIVES
# ══════════════════════════════════════════════════════════════════════════════════════════
def _rect(d: ImageDraw.ImageDraw, x0, y0, x1, y1, rgb) -> None:
    if x1 < x0 or y1 < y0:
        return
    d.rectangle([x0, y0, x1, y1], fill=_c(rgb))


def _bolt(img: Image.Image, cx: int, cy: int, accent_rgb=GOLD) -> None:
    img.putpixel((cx, cy), _c((255, 246, 210)))
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        img.putpixel((cx + dx, cy + dy), _c(accent_rgb))
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        img.putpixel((cx + dx, cy + dy), _c(INK))


def _court_lines(img: Image.Image, y0: int, y1: int, tint_plus: int = 9) -> None:
    """Faint key + three-point arc etched into the panel floor."""
    d = ImageDraw.Draw(img)
    tint = tuple(min(255, c + tint_plus) for c in PANEL)
    cx = WIDTH // 2
    cy = (y0 + y1) // 2
    d.ellipse([cx - 34, cy - 26, cx + 34, cy + 26], outline=_c(tint))
    d.rectangle([cx - 13, cy - 18, cx + 13, cy + 18], outline=_c(tint))
    d.ellipse([cx - 13, cy - 13, cx + 13, cy + 13], outline=_c(tint))
    for x in range(11, WIDTH - 11):
        yy = y1 - 4 - int(5 * abs((x - cx) / 42.0) ** 2)
        if y0 < yy < y1:
            img.putpixel((x, yy), _c(tint))


def _diag(img: Image.Image, x0, y0, x1, y1, base, hi, pitch=5) -> None:
    """Diagonal hatch inside a rectangle — the reference sheet's signature fill."""
    d = ImageDraw.Draw(img)
    h = y1 - y0
    for k in range(-h, (x1 - x0) + h, pitch):
        for step in range(h + 1):
            x = x0 + k + step
            y = y0 + step
            if x0 <= x <= x1 and y0 <= y <= y1:
                img.putpixel((x, y), _c(hi))
                if x + 1 <= x1:
                    img.putpixel((x + 1, y), _c(base))


def _well(img: Image.Image, slot: int, accent_rgb=ORANGE, hot: bool = False) -> None:
    """A recessed 16x16 slot, deliberately quiet: the items are the content, not the frame."""
    sx, sy = slot_xy(slot)
    d = ImageDraw.Draw(img)
    if hot:
        _rect(d, sx - 2, sy - 2, sx + WELL + 1, sy + WELL + 1, accent_rgb)
        _rect(d, sx - 2, sy - 2, sx + WELL + 1, sy - 2,
              tuple(min(255, c + 55) for c in accent_rgb))
    _rect(d, sx - 1, sy - 1, sx + WELL, sy + WELL, PANEL_DK)
    _rect(d, sx, sy, sx + WELL - 1, sy + WELL - 1, GLASS_DK)
    _rect(d, sx, sy, sx + WELL - 1, sy, PANEL_DK)
    _rect(d, sx, sy, sx, sy + WELL - 1, PANEL_DK)
    _rect(d, sx, sy + WELL - 1, sx + WELL - 1, sy + WELL - 1, GLASS_LT)
    _rect(d, sx + WELL - 1, sy, sx + WELL - 1, sy + WELL - 1, GLASS_LT)


def _group(img: Image.Image, r0: int, c0: int, rows: int, cols: int, accent_rgb) -> None:
    """Frame a block of slots *in the 2px gutters* so it reads as one module while every 16x16
    item stays exactly on its vanilla position."""
    x0, y0 = slot_xy(r0 * 9 + c0)
    x1, y1 = slot_xy((r0 + rows - 1) * 9 + c0 + cols - 1)
    x0, y0, x1, y1 = x0 - 2, y0 - 2, x1 + WELL + 1, y1 + WELL + 1
    d = ImageDraw.Draw(img)
    hi = tuple(min(255, c + 70) for c in accent_rgb)
    dk = tuple(max(0, c - 80) for c in accent_rgb)
    d.rectangle([x0, y0, x1, y1], outline=_c(accent_rgb))
    _rect(d, x0 + 1, y0 + 1, x1 - 1, y0 + 1, hi)
    _rect(d, x0 + 1, y1 - 1, x1 - 1, y1 - 1, dk)
    _rect(d, x0 + 1, y0 + 1, x0 + 1, y1 - 1, hi)
    _rect(d, x1 - 1, y0 + 1, x1 - 1, y1 - 1, dk)
    for cx, cy in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        img.putpixel((cx, cy), _c((255, 255, 255, 255)))


def _rail_tag(img: Image.Image, slot: int, accent_rgb) -> None:
    """Number tag riveted into the left rail beside a slot — used by the ledgers."""
    sx, sy = slot_xy(slot)
    d = ImageDraw.Draw(img)
    d.rectangle([3, sy + 4, 6, sy + 11], fill=_c(INK))
    d.rectangle([4, sy + 5, 5, sy + 10], fill=_c(accent_rgb))
    d.point((5, sy + 6), fill=_c((255, 250, 225)))


def _group_slots(img: Image.Image, slots, accent_rgb) -> None:
    """Frame the exact bounding box of a slot list. Hand-counted (row, col, span) triples were
    constantly one cell off, which put stray rings on the wrong column."""
    rows = [s // 9 for s in slots]
    cols = [s % 9 for s in slots]
    _group(img, min(rows), min(cols), max(rows) - min(rows) + 1, max(cols) - min(cols) + 1, accent_rgb)


def _base_slots(img: Image.Image, slots, accent_rgb) -> None:
    """Base bar under the exact bounding box of a slot list."""
    rows = [s // 9 for s in slots]
    cols = [s % 9 for s in slots]
    r = min(rows)
    x0 = slot_xy(r * 9 + min(cols))[0] - 3
    x1 = slot_xy(r * 9 + max(cols))[0] + 19
    for rr in range(min(rows), max(rows) + 1):
        if rr == max(rows):
            _base_bar(img, slot_xy(rr * 9)[1], x0, x1, accent_rgb)


def _button_row(img: Image.Image, slots, accent_rgb) -> None:
    """A fused run of slots painted as one button: the cells themselves are filled with the
    button colour, the gutter is lit on top and shadowed at the bottom, and the whole run gets a
    single keyline. The item placed on top is the label; the colour is the affordance."""
    x0, y0 = slot_xy(slots[0])
    x1, y1 = slot_xy(slots[-1])
    x1 += WELL - 1
    y1 += WELL - 1
    d = ImageDraw.Draw(img)
    hi = tuple(min(255, c + 42) for c in accent_rgb)
    dk = tuple(max(0, c - 82) for c in accent_rgb)
    # frame
    d.rounded_rectangle([x0 - 3, y0 - 3, x1 + 3, y1 + 3], radius=3, fill=_c(INK))
    # lane
    d.rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], fill=_c(dk))
    # cells
    for s in slots:
        sx, sy = slot_xy(s)
        d.rectangle([sx, sy, sx + WELL - 1, sy + WELL - 1], fill=_c(accent_rgb))
        _rect(d, sx, sy, sx + WELL - 1, sy + 1, hi)
        _rect(d, sx, sy + WELL - 2, sx + WELL - 1, sy + WELL - 1, dk)
    # top lip of the lane + bottom shadow
    _rect(d, x0 - 2, y0 - 2, x1 + 2, y0 - 2, hi)
    _rect(d, x0 - 2, y1 + 2, x1 + 2, y1 + 2, dk)


def _hot(img: Image.Image, slots, accent_rgb) -> None:
    for s in slots:
        _well(img, s, accent_rgb, hot=True)


def _plaque(img: Image.Image, accent_rgb, width_px: int = 0) -> None:
    """Floating title plaque straddling the case's top edge. Caption band left empty (y 12..20)."""
    d = ImageDraw.Draw(img)
    half = (width_px or 128) // 2
    cx = WIDTH // 2
    x0, x1 = cx - half, cx + half
    for i, a in ((4, 60), (3, 90), (2, 120)):
        d.rounded_rectangle([x0 - i, 3 + i, x1 + i, 21 + i], radius=5, fill=_c((0, 0, 0, a)))
    d.rounded_rectangle([x0 - 1, 1, x1 + 1, 22], radius=4, fill=_c(INK))
    d.rounded_rectangle([x0, 2, x1, 21], radius=4, fill=_c(PLAQUE_HI))
    d.rounded_rectangle([x0 + 1, 3, x1 - 1, 20], radius=3, fill=_c(PLAQUE))
    _rect(d, x0 + 2, 4, x1 - 2, 4, (78, 92, 122))
    _rect(d, x0 + 2, 5, x1 - 2, 5, GOLD_DK)
    _rect(d, x0 + 2, 21, x1 - 2, 21, accent_rgb)
    _bolt(img, x0 + 3, 13, GOLD)
    _bolt(img, x1 - 3, 13, GOLD)
    for wx in (x0 - 6, x1 + 6):
        _rect(d, wx - 1, 8, wx + 1, 21, INK)
        _rect(d, wx, 9, wx, 20, RIM)
        _rect(d, wx, 9, wx, 12, RIM_HI)
        img.putpixel((wx, 22), _c(GOLD))


def _case(img: Image.Image, rows: int, accent_rgb, floor_tint: int = 9) -> tuple[int, int]:
    """Chrome case + court floor + rails. Returns the floor's (y0, y1)."""
    w, h = WIDTH, overlay_height(rows)
    d = ImageDraw.Draw(img)
    top = CONTAINER_TOP

    d.rounded_rectangle([0, top, w - 1, h - 1], radius=5, fill=_c(INK))
    d.rounded_rectangle([1, top + 1, w - 2, h - 2], radius=4, fill=_c(RIM_DK))
    d.rounded_rectangle([1, top + 1, w - 2, top + 3], radius=3, fill=_c(RIM))
    d.rounded_rectangle([1, top + 1, w - 2, top + 2], radius=2, fill=_c(RIM_HI))
    _rect(d, 1, top + 1, 2, h - 2, RIM)
    _rect(d, 1, top + 1, 1, h - 2, RIM_HI)
    _rect(d, w - 3, top + 4, w - 2, h - 2, RIM_DK)
    _rect(d, w - 2, top + 4, w - 2, h - 2, RIM)
    _rect(d, 2, h - 3, w - 3, h - 2, RIM)
    _rect(d, 2, h - 3, w - 3, h - 3, RIM_HI)

    fy0, fy1 = top + 6, h - 6
    d.rounded_rectangle([4, fy0, w - 5, fy1], radius=3, fill=_c(INK))
    d.rounded_rectangle([5, fy0 + 1, w - 6, fy1 - 1], radius=3, fill=_c(PANEL))
    _rect(d, 5, fy0 + 1, w - 6, fy0 + 2, PANEL_DK)
    _court_lines(img, fy0 + 4, fy1 - 4, floor_tint)

    for x0 in (2, w - 4):
        _rect(d, x0, fy0 + 2, x0 + 1, fy1 - 2, RIM_DK)
        _rect(d, x0, fy0 + 2, x0, fy1 - 2, RIM)
    for yy in range(fy0 + 12, fy1 - 6, ROW_PITCH):
        _bolt(img, 3, yy, accent_rgb)
        _bolt(img, w - 4, yy, accent_rgb)
    return fy0, fy1


def _footer(img: Image.Image, rows: int, accent_rgb) -> None:
    """Recessed LED scoreboard strip + accent corner brackets on the bottom lip."""
    h = overlay_height(rows)
    d = ImageDraw.Draw(img)
    _rect(d, 12, h - 6, WIDTH - 13, h - 5, INK)
    for i, x in enumerate(range(14, WIDTH - 14, 4)):
        img.putpixel((x, h - 6), _c(ORANGE if i % 3 else GOLD))
        img.putpixel((x + 1, h - 6), _c(ORANGE_DK))
    for bx in (5, WIDTH - 10):
        _rect(d, bx, h - 8, bx + 4, h - 7, accent_rgb)
        _rect(d, bx, h - 9, bx, h - 7, accent_rgb)


# ══════════════════════════════════════════════════════════════════════════════════════════
#  GUTTER-SAFE ORNAMENTS  (a slot owns [sy, sy+15]; the next row starts at sy+18, so every
#  ornament lives in the 2px gutters — nothing may ever cover what the player is looking at)
# ══════════════════════════════════════════════════════════════════════════════════════════
def _shelf(img: Image.Image, sy: int, x0: int, x1: int, accent_rgb) -> None:
    d = ImageDraw.Draw(img)
    _rect(d, x0, sy + 16, x1, sy + 16, RIM)
    _rect(d, x0, sy + 17, x1, sy + 17, accent_rgb)


def _base_bar(img: Image.Image, sy: int, x0: int, x1: int, accent_rgb) -> None:
    d = ImageDraw.Draw(img)
    _rect(d, x0, sy + 16, x1, sy + 16, tuple(max(0, c - 60) for c in accent_rgb))
    _rect(d, x0, sy + 17, x1, sy + 17, accent_rgb)


def _rail_tag(img: Image.Image, slot: int, accent_rgb) -> None:
    sx, sy = slot_xy(slot)
    d = ImageDraw.Draw(img)
    d.rectangle([3, sy + 4, 6, sy + 11], fill=_c(INK))
    d.rectangle([4, sy + 5, 5, sy + 10], fill=_c(accent_rgb))
    d.point((5, sy + 6), fill=_c((255, 250, 225)))


def _group(img: Image.Image, r0: int, c0: int, rows: int, cols: int, accent_rgb) -> None:
    x0, y0 = slot_xy(r0 * 9 + c0)
    x1, y1 = slot_xy((r0 + rows - 1) * 9 + c0 + cols - 1)
    x0, y0, x1, y1 = x0 - 2, y0 - 2, x1 + WELL + 1, y1 + WELL + 1
    d = ImageDraw.Draw(img)
    hi = tuple(min(255, c + 70) for c in accent_rgb)
    dk = tuple(max(0, c - 80) for c in accent_rgb)
    d.rectangle([x0, y0, x1, y1], outline=_c(accent_rgb))
    _rect(d, x0 + 1, y0 + 1, x1 - 1, y0 + 1, hi)
    _rect(d, x0 + 1, y1 - 1, x1 - 1, y1 - 1, dk)
    _rect(d, x0 + 1, y0 + 1, x0 + 1, y1 - 1, hi)
    _rect(d, x1 - 1, y0 + 1, x1 - 1, y1 - 1, dk)
    for cx, cy in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        img.putpixel((cx, cy), _c((255, 255, 255, 255)))


def _group_slots(img: Image.Image, slots, accent_rgb) -> None:
    """Frame the exact bounding box of a slot list. Hand-counted (row, col, span) triples were
    constantly one cell off, which put stray rings on the wrong column."""
    rows = [s // 9 for s in slots]
    cols = [s % 9 for s in slots]
    _group(img, min(rows), min(cols), max(rows) - min(rows) + 1, max(cols) - min(cols) + 1, accent_rgb)


def _base_slots(img: Image.Image, slots, accent_rgb) -> None:
    """Base bar under the exact bounding box of a slot list."""
    rows = [s // 9 for s in slots]
    cols = [s % 9 for s in slots]
    r = min(rows)
    x0 = slot_xy(r * 9 + min(cols))[0] - 3
    x1 = slot_xy(r * 9 + max(cols))[0] + 19
    for rr in range(min(rows), max(rows) + 1):
        if rr == max(rows):
            _base_bar(img, slot_xy(rr * 9)[1], x0, x1, accent_rgb)


def _button_row(img: Image.Image, slots, accent_rgb) -> None:
    """A fused run of slots painted as one button: the cells themselves are filled with the
    button colour, the gutter is lit on top and shadowed at the bottom, and the whole run gets a
    single keyline. The item placed on top is the label; the colour is the affordance."""
    x0, y0 = slot_xy(slots[0])
    x1, y1 = slot_xy(slots[-1])
    x1 += WELL - 1
    y1 += WELL - 1
    d = ImageDraw.Draw(img)
    hi = tuple(min(255, c + 42) for c in accent_rgb)
    dk = tuple(max(0, c - 82) for c in accent_rgb)
    # frame
    d.rounded_rectangle([x0 - 3, y0 - 3, x1 + 3, y1 + 3], radius=3, fill=_c(INK))
    # lane
    d.rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], fill=_c(dk))
    # cells
    for s in slots:
        sx, sy = slot_xy(s)
        d.rectangle([sx, sy, sx + WELL - 1, sy + WELL - 1], fill=_c(accent_rgb))
        _rect(d, sx, sy, sx + WELL - 1, sy + 1, hi)
        _rect(d, sx, sy + WELL - 2, sx + WELL - 1, sy + WELL - 1, dk)
    # top lip of the lane + bottom shadow
    _rect(d, x0 - 2, y0 - 2, x1 + 2, y0 - 2, hi)
    _rect(d, x0 - 2, y1 + 2, x1 + 2, y1 + 2, dk)


def _hot(img: Image.Image, slots, accent_rgb) -> None:
    for s in slots:
        _well(img, s, accent_rgb, hot=True)


def _void(img: Image.Image, r0: int, c0: int, rows: int, cols: int, band=None) -> None:
    """A deliberately EMPTY zone. Painting a slot grid over cells the menu never fills is what
    made every screen read as '54 boxes, good luck'.

    Cells that DO hold an item are skipped: an empty zone must never eat a live slot (it did,
    and the item then floated on top of the hatching with no well under it)."""
    used = getattr(img, "_used_slots", set())
    d = ImageDraw.Draw(img)
    for r in range(r0, r0 + rows):
        for c in range(c0, c0 + cols):
            if r * 9 + c in used:
                continue
            sx, sy = slot_xy(r * 9 + c)
            d.rectangle([sx - 2, sy - 2, sx + WELL + 1, sy + WELL + 1], fill=_c(PANEL_DK))
            _rect(d, sx - 2, sy - 2, sx + WELL + 1, sy - 2, (40, 50, 72))
            _diag(img, sx - 1, sy, sx + WELL, sy + WELL, PANEL_DK, band or (56, 69, 94), pitch=6)


# ── art motifs ──────────────────────────────────────────────────────────────────────────
def _hoop(img: Image.Image, x0: int, y0: int, accent_rgb) -> None:
    """A backboard + orange rim, drawn in the empty space above the item grid."""
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x0 + 21, y0 + 13], fill=_c(INK))
    d.rectangle([x0 + 1, y0 + 1, x0 + 20, y0 + 12], fill=_c(RIM))
    d.rectangle([x0 + 2, y0 + 2, x0 + 19, y0 + 11], fill=_c((236, 243, 252)))
    d.rectangle([x0 + 8, y0 + 4, x0 + 13, y0 + 9], outline=_c(ORANGE_DK))
    for i in range(11):
        x = x0 + 5 + i
        y = y0 + 13 + (2 if 3 < i < 7 else (1 if 1 < i < 9 else 0))
        img.putpixel((x, y), _c(ORANGE))
        if i < 10:
            img.putpixel((x, y + 1), _c(ORANGE_DK))
    for x in range(x0 + 5, x0 + 16, 2):
        for y in range(y0 + 15, y0 + 19):
            if (y - y0 - 15) % 2 == 0:
                img.putpixel((x, y), _c((214, 222, 234)))


def _ball_mark(img: Image.Image, cx: int, cy: int, r: int = 7) -> None:
    """Engraved basketball: rim + the two seam curves, no fill, so it reads as a watermark."""
    tint = (96, 112, 140)
    d = ImageDraw.Draw(img)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=_c(tint))
    d.line([cx - r, cy, cx + r, cy], fill=_c(tint))
    d.line([cx, cy - r, cx, cy + r], fill=_c(tint))
    d.arc([cx - r - 2, cy - r, cx + r - 2, cy + r], 300, 60, fill=_c(tint))
    d.arc([cx - r + 2, cy - r, cx + r + 2, cy + r], 120, 240, fill=_c(tint))


def _net(img: Image.Image, x0: int, y0: int, x1: int, y1: int) -> None:
    for i in range(14):
        x = x0 + i * (x1 - x0) // 13
        img.putpixel((x, y0), _c((150, 164, 188)))
        img.putpixel((x, y0 + 1), _c((120, 134, 158)))
    for i in range(10):
        y = y0 + 2 + i
        span = (x1 - x0) // 2 - i
        d = ImageDraw.Draw(img)
        d.line([x0 + (x1 - x0) // 2 - span, y, x0 + (x1 - x0) // 2 + span, y],
               fill=_c((110, 124, 148)))


def _podium(img: Image.Image, base_y: int, accent_rgb) -> None:
    """Three plinths, middle tallest, chrome edges — the leaderboard signature."""
    for col, h, col_rgb in ((1, 7, BRONZE), (4, 11, GOLD), (7, 7, SILVER)):
        x0 = 8 + 18 * col - 2
        x1 = x0 + 19
        y0 = base_y - h
        _rect(img_draw(img), x0, y0, x1, base_y, INK)
        _rect(img_draw(img), x0 + 1, y0 + 1, x1 - 1, base_y - 1, col_rgb)
        _rect(img_draw(img), x0 + 1, y0 + 1, x1 - 1, y0 + 1,
              tuple(min(255, c + 70) for c in col_rgb))
        _diag(img, x0 + 2, y0 + 3, x1 - 2, base_y - 2,
              tuple(max(0, c - 55) for c in col_rgb), col_rgb, pitch=4)


def _stairs(img: Image.Image, base_y: int, accent_rgb) -> None:
    """Escalier of seven chrome steps under the ranks ladder."""
    for i in range(7):
        x0 = 8 + 18 * (i + 1) - 2
        h = 3 + (i * 2) % 6
        _rect(img_draw(img), x0, base_y - h, x0 + 19, base_y, INK)
        _rect(img_draw(img), x0 + 1, base_y - h + 1, x0 + 18, base_y - 1, accent_rgb)
        _rect(img_draw(img), x0 + 1, base_y - h + 1, x0 + 18, base_y - h + 1,
              tuple(min(255, c + 60) for c in accent_rgb))


def _awning(img: Image.Image, y: int, x0: int, x1: int, accent_rgb) -> None:
    """Scalloped shop awning: alternates accent and cream, hangs 4px into the frame."""
    d = ImageDraw.Draw(img)
    _rect(d, x0, y, x1, y + 1, INK)
    for i, x in enumerate(range(x0, x1 + 1, 6)):
        col = accent_rgb if i % 2 == 0 else (238, 244, 252)
        _rect(d, x, y + 1, min(x + 5, x1), y + 3, col)
        for k in range(3):
            _rect(d, x + k, y + 4, min(x + 5 - k, x1), y + 4, col)
    _rect(d, x0, y + 5, x1, y + 5, INK)


def _pedestal(img: Image.Image, slot: int, accent_rgb) -> None:
    """Spotlight plinth: glow ring plus brackets in the gutter around a single card."""
    sx, sy = slot_xy(slot)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([sx - 3, sy - 3, sx + 18, sy + 18], radius=3, outline=_c(accent_rgb))
    for cx, cy in ((sx - 3, sy - 3), (sx + 18, sy - 3), (sx - 3, sy + 18), (sx + 18, sy + 18)):
        img.putpixel((cx, cy), _c((255, 255, 255)))
    _base_bar(img, sy, sx - 4, sx + 19, accent_rgb)


def _tunnel(img: Image.Image, r0: int, c0: int, cols: int, accent_rgb) -> None:
    """Corridor of frames receding into the wall — used for the badge/rank showcase rows."""
    for i in range(cols):
        sx, sy = slot_xy(r0 * 9 + c0 + i)
        d = ImageDraw.Draw(img)
        d.rectangle([sx - 2, sy - 2, sx + 17, sy + 17], outline=_c(accent_rgb))


def _ticket(img: Image.Image, slot: int, accent_rgb) -> None:
    """Punched ticket hanging off the cart slot."""
    sx, sy = slot_xy(slot)
    d = ImageDraw.Draw(img)
    x0, y0 = sx - 5, sy - 6
    d.rounded_rectangle([x0, y0, x0 + 25, y0 + 27], radius=3, fill=_c(INK))
    d.rounded_rectangle([x0 + 1, y0 + 1, x0 + 24, y0 + 26], radius=3, fill=_c(accent_rgb))
    _diag(img, x0 + 2, y0 + 2, x0 + 23, y0 + 25,
          tuple(max(0, c - 60) for c in accent_rgb),
          tuple(min(255, c + 40) for c in accent_rgb), pitch=4)
    for yy in (y0 + 8, y0 + 18):
        img.putpixel((x0 + 12, yy), _c(INK))
    d.rectangle([x0 + 10, y0 + 24, x0 + 14, y0 + 27], fill=_c(INK))


def img_draw(image: Image.Image) -> ImageDraw.ImageDraw:
    return ImageDraw.Draw(image)


def _banner(img: Image.Image, y: int, accent_rgb, x0: int = 10, x1: int = WIDTH - 11) -> None:
    """Hanging cloth banner with a notched hem, for wide empty areas."""
    d = ImageDraw.Draw(img)
    _rect(d, x0, y, x1, y + 8, INK)
    _rect(d, x0 + 1, y + 1, x1 - 1, y + 6, accent_rgb)
    _rect(d, x0 + 1, y + 1, x1 - 1, y + 1, tuple(min(255, c + 60) for c in accent_rgb))
    for x in range(x0 + 2, x1 - 1, 4):
        d.line([x, y + 6, x + 1, y + 8], fill=_c(INK))
    _ball_mark(img, WIDTH // 2, y + 3, 2)


def _scoreboard(img: Image.Image, x0: int, y0: int, x1: int, y1: int, accent_rgb) -> None:
    """Recessed LED board: dot-matrix rows of dim, mostly-off lamps."""
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([x0, y0, x1, y1], radius=2, fill=_c(INK))
    d.rounded_rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], radius=2, fill=_c((24, 30, 44)))
    for yy in range(y0 + 3, y1 - 2, 3):
        for xx in range(x0 + 3, x1 - 2, 3):
            on = (xx * 7 + yy * 13) % 11 < 2
            img.putpixel((xx, yy), _c(ORANGE if on else (40, 48, 66)))



# ══════════════════════════════════════════════════════════════════════════════════════════
#  FRAME VARIANTS — the case is not the same object on every screen
# ══════════════════════════════════════════════════════════════════════════════════════════
def _frame_variant(img: Image.Image, rows: int, accent_rgb, style: str) -> None:
    """Decorates the case so two menus never read as the same window twice. Everything here
    stays on the chrome rim, the rails and the outer 4px of floor, so it can never touch an item.
    """
    w, h = WIDTH, overlay_height(rows)
    d = ImageDraw.Draw(img)
    top, floor = CONTAINER_TOP, CONTAINER_TOP + 5

    if style == "awning":
        _awning(img, 15, 3, w - 4, accent_rgb)
        for x in range(3, w - 3, 7):
            _rect(d, x, 20, x, 21, ORANGE_DK)

    elif style == "hazard":
        # diagonal stripes riveted on both rails
        for x0 in (3, w - 7):
            for y in range(floor + 4, h - 8):
                if ((y + x0) // 3) % 2 == 0:
                    _rect(d, x0, y, x0 + 3, y, GOLD)
                else:
                    _rect(d, x0, y, x0 + 3, y, (28, 32, 44))

    elif style == "blueprint":
        tint = (74, 92, 122)
        for x in range(6, w - 5, 8):
            _rect(d, x, floor + 3, x, h - 7, tint)
        for y in range(floor + 3, h - 7, 8):
            _rect(d, 6, y, w - 7, y, tint)
        for cx, cy in ((6, floor + 3), (w - 7, floor + 3), (6, h - 7), (w - 7, h - 7)):
            _rect(d, cx - 1, cy - 1, cx + 1, cy + 1, RIM)

    elif style == "film":
        for x0 in (3, w - 6):
            for y in range(floor + 3, h - 9, 7):
                d.rectangle([x0, y, x0 + 2, y + 3], fill=_c(INK))
                d.rectangle([x0 + 1, y + 1, x0 + 1, y + 2], fill=_c(accent_rgb))

    elif style == "clip":
        # clipboard: metal clip at the top centre, paper gutter down the left
        d.rectangle([w // 2 - 12, 0, w // 2 + 12, 2], fill=_c(INK))
        d.rectangle([w // 2 - 11, 0, w // 2 + 11, 1], fill=_c(RIM))
        _rect(d, w // 2 - 3, 0, w // 2 + 3, 1, RIM_DK)
        _rect(d, 5, floor + 2, 6, h - 6, (214, 222, 234))
        _rect(d, 7, floor + 2, 7, h - 6, (150, 164, 186))

    elif style == "arena":
        # spotlight beams from the two upper corners + an arc along the bottom
        for i in range(26):
            x = 6 + i
            _rect(d, x, floor + 3, x, floor + 3 + i // 2, (78, 92, 118))
            _rect(d, w - 7 - i, floor + 3, w - 7 - i, floor + 3 + i // 2, (78, 92, 118))
        for x in range(8, w - 8):
            yy = h - 9 - int(4 * abs((x - w / 2) / 60.0) ** 2)
            img.putpixel((x, yy), _c(tuple(min(255, c + 26) for c in accent_rgb)))

    elif style == "switch":
        # knob row across the top rail: reads as a mixing desk
        for x in range(12, w - 10, 9):
            img.putpixel((x, top + 2), _c(INK))
            _rect(d, x - 1, top + 2, x + 1, top + 3, RIM)
            img.putpixel((x, top + 3), _c(accent_rgb))
        for x in range(14, w - 12, 9):
            _rect(d, x, h - 8, x, h - 7, accent_rgb)


FRAME_STYLE = {
    "shop": "awning", "shop_grid": "awning", "tags": "rivet", "cosmetics": "rivet",
    "play": "arena", "play_bot": "blueprint", "hub": "arena", "hub_op": "hazard",
    "leaderboards": "arena", "settings": "switch", "confirm": "rivet", "guild": "clip",
    "quests": "clip", "achievements": "rivet", "mailbox": "clip", "history": "film",
    "friends": "rivet", "party": "rivet", "ranks": "arena", "spectate": "film",
    "player_card": "rivet", "admin": "hazard", "reports": "hazard", "warnings": "hazard",
    "studio": "blueprint",
}


# ══════════════════════════════════════════════════════════════════════════════════════════
#  THE BASE WINDOW
# ══════════════════════════════════════════════════════════════════════════════════════════
def build_window(rows: int = 6, accent: str = "orange", plaque_px: int = 128,
                 slots=None, footer: bool = True, style: str = "rivet") -> Image.Image:
    """Chrome case + court floor + plaque. Wells are drawn ONLY for these slots."""
    accent_rgb = ACCENTS.get(accent, ACCENTS["orange"])[0]
    img = Image.new("RGBA", (WIDTH, overlay_height(rows)), (0, 0, 0, 0))
    _case(img, rows, accent_rgb)
    _frame_variant(img, rows, accent_rgb, style)
    if slots is None:
        slots = range(rows * 9)
    for s in slots:
        _well(img, s, accent_rgb)
    _plaque(img, accent_rgb, plaque_px)
    if footer:
        _footer(img, rows, accent_rgb)
    return img


ROW = lambda r: [r * 9 + c for c in range(9)]
BLOCK = lambda r0, r1, c0=0, c1=8: [r * 9 + c for r in range(r0, r1 + 1) for c in range(c0, c1 + 1)]


# ══════════════════════════════════════════════════════════════════════════════════════════
#  SCENES — each one mirrors the slots the Java menu actually fills
# ══════════════════════════════════════════════════════════════════════════════════════════
def build_shop(rows: int = 6, grid: bool = False) -> Image.Image:
    """Market stall: awning over the filter tabs, three shelves, cart + pagination on chrome."""
    grid_slots = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                  28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43]
    if grid:
        used = grid_slots + [0, 8, 22, 45, 48, 49, 50, 53]
        img = build_window(rows, "orange", 128, used, style=FRAME_STYLE["shop"])
        _awning(img, 20, 6, WIDTH - 7, ORANGE)
        _group(img, 1, 1, 4, 7, PANEL_HI)
        for s in (48, 49, 50):
            _pedestal(img, s, GREEN)
        _hot(img, (0, 8), GOLD)
        _hot(img, (45, 53), BLUE)
        return img
    used = [0, 1, 2, 3, 4, 5, 6, 8, 10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43, 45, 48, 49, 50, 53]
    img = build_window(rows, "orange", 128, used, style=FRAME_STYLE["shop"])
    _awning(img, 20, 6, WIDTH - 7, ORANGE)
    for r in (1, 2, 3):
        _group(img, r, 1, 1, 7, ORANGE if r % 2 else GOLD)
        _shelf(img, slot_xy(r * 9 + 1)[1], slot_xy(r * 9 + 1)[0] - 3,
               slot_xy(r * 9 + 7)[0] + 19, ORANGE if r % 2 else GOLD)
    _group(img, 4, 1, 1, 7, BLUE)
    _base_bar(img, slot_xy(37)[1], slot_xy(37)[0] - 3, slot_xy(43)[0] + 19, BLUE)
    _ball_mark(img, WIDTH // 2, 118, 7)
    _ticket(img, 0, GOLD)
    for s in (48, 49, 50):
        _pedestal(img, s, GREEN)
    _hot(img, (0, 8), GOLD)
    _hot(img, (45, 53), BLUE)
    return img


def build_tags(rows: int = 6) -> Image.Image:
    """Vault wall: filter tabs, four display cases, vault door on the left."""
    used = [0, 1, 2, 3, 4, 5, 7, 8, 10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43, 45, 48, 49, 50, 53]
    img = build_window(rows, "violet", 120, used, style=FRAME_STYLE["tags"])
    _void(img, 4, 0, 2, 9)
    for r in (1, 2, 3, 4):
        sx, sy = slot_xy(r * 9 + 1)
        _group(img, r, 1, 1, 7, VIOLET if r % 2 else GOLD)
        _shelf(img, sy, sx - 3, sx + 18 * 6 + 19, VIOLET_DK if r % 2 else GOLD_DK)
    _ball_mark(img, WIDTH // 2, 106, 8)
    _hot(img, (0,), GOLD)
    _hot(img, (8,), GOLD)
    _hot(img, (45, 53), BLUE)
    return img


def build_cosmetics(rows: int = 6) -> Image.Image:
    """Locker bay: ball wall on the left, dunk rack on the right, an open hoop bay between them."""
    used = [0, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17, 29, 30, 31, 32, 33, 48, 49, 50, 53]
    img = build_window(rows, "rose", 132, used, style=FRAME_STYLE["cosmetics"])
    _group_slots(img, [9, 10, 11, 12], ROSE)
    _group_slots(img, [14, 15, 16, 17], BLUE)
    _base_slots(img, [9, 10, 11, 12], ROSE)
    _base_slots(img, [14, 15, 16, 17], BLUE)
    _void(img, 2, 0, 3, 9, band=(62, 56, 66))
    _hoop(img, WIDTH // 2 - 11, 58, ROSE)
    _net(img, WIDTH // 2 - 16, 74, WIDTH // 2 + 16, 84)
    _group_slots(img, [29, 30, 31, 32, 33], GOLD)
    _base_slots(img, [29, 30, 31, 32, 33], GOLD)
    _hot(img, (0, 7, 8), GOLD)
    _hot(img, (48, 49, 50), GREEN)
    return img


def build_play(rows: int = 6) -> Image.Image:
    """Four mode marquees, then the practice lane — a lot of air, on purpose."""
    used = [0, 8, 10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34, 38, 49]
    img = build_window(rows, "orange", 136, used, style=FRAME_STYLE["play"])
    _banner(img, 20, ORANGE, 8, WIDTH - 9)
    for s in (10, 12, 14, 16):
        _pedestal(img, s, ORANGE)
    _void(img, 2, 0, 2, 9)
    _group(img, 3, 1, 1, 7, BLUE)
    _plinth_lane = slot_xy(28)[1]
    _base_bar(img, _plinth_lane, 8 + 18 - 3, 8 + 18 * 7 + 19, BLUE)
    _hoop(img, WIDTH // 2 - 11, 100, BLUE)
    _hot(img, (28, 30, 32, 34), BLUE)
    _hot(img, (19, 21, 23, 25), GOLD)
    _hot(img, (0, 8, 49), GOLD)
    _pedestal(img, 38, VIOLET)
    return img


def build_play_bot(rows: int = 5) -> Image.Image:
    used = [4, 19, 22, 25, 30, 32, 34, 40]
    img = build_window(rows, "violet", 136, used, style=FRAME_STYLE["play_bot"])
    _banner(img, 20, VIOLET, 8, WIDTH - 9)
    for s in (19, 22, 25):
        _pedestal(img, s, VIOLET)
    _scoreboard(img, 30, 96, WIDTH - 31, 108, VIOLET)
    _hot(img, (19, 22, 25), VIOLET)
    _hot(img, (30, 32, 34), ORANGE)
    _hot(img, (4, 40), GOLD)
    return img


def build_hub(rows: int = 6, op: bool = False) -> Image.Image:
    """Lobby. Hero marquee (Locker Room), five-wide action bar, blue dashboard row, gold footer
    row — and the two remaining rows are left as open court, because the menu paints nothing
    there. Empty space is the point: it is what makes the four real rows read as four things."""
    used = [0, 8, 13, 20, 21, 22, 23, 24, 29, 30, 31, 33, 39, 40, 41, 43] + ([44] if op else [])
    acc = "red" if op else "orange"
    rgb = RED if op else ORANGE
    img = build_window(rows, acc, 124, used, footer=True,
                       style=FRAME_STYLE["hub_op" if op else "hub"])
    d = ImageDraw.Draw(img)
    # ── hero marquee
    d.rounded_rectangle([38, 16, 138, 42], radius=4, fill=_c(INK))
    d.rounded_rectangle([39, 17, 137, 41], radius=3, fill=_c(PANEL))
    _diag(img, 41, 19, 135, 39, PANEL_DK, (58, 71, 96), pitch=7)
    _rect(d, 39, 17, 137, 18, rgb)
    _rect(d, 39, 40, 137, 41, tuple(max(0, c - 70) for c in rgb))
    _hoop(img, 44, 21, rgb)
    _ball_mark(img, 126, 30, 6)
    _well(img, 13, GOLD, hot=True)
    # ── action bar, dashboard, footer
    _group_slots(img, [20, 21, 22, 23, 24], rgb)
    _base_slots(img, [20, 21, 22, 23, 24], rgb)
    _group_slots(img, [29, 30, 31, 33], BLUE)
    _base_slots(img, [29, 30, 31, 33], BLUE)
    _group_slots(img, [39, 40, 41, 43], GOLD)
    _base_slots(img, [39, 40, 41, 43], GOLD)
    # ── open court underneath
    _court_lines(img, 104, 130, 16)
    if op:
        _hot(img, (44,), RED)
    _hot(img, (0, 8), GOLD)
    return img


def build_leaderboards(rows: int = 6) -> Image.Image:
    """Hall of fame: a category rail on top, an open hall with the engraved ball, then the
    podium standing on the bottom row — which the menu leaves completely free."""
    used = [0, 1, 2, 3, 4, 5, 6, 7, 8, 13, 21, 23, 29, 30, 31, 32, 33, 39, 41, 49]
    img = build_window(rows, "gold", 140, used, style=FRAME_STYLE["leaderboards"])
    d = ImageDraw.Draw(img)
    _group_slots(img, [0, 1, 2, 3, 4, 5, 6, 7, 8], GOLD)
    _void(img, 1, 0, 3, 9, band=(58, 60, 52))
    _ball_mark(img, WIDTH // 2, 66, 13)
    _podium(img, slot_xy(36)[1] + 16, GOLD)
    _rail_tag(img, 39, GOLD)
    _rail_tag(img, 41, GOLD)
    _set_bonus = 0
    _hot(img, (13, 21, 23), GOLD)
    _hot(img, (29, 30, 31, 32, 33), GOLD)
    _hot(img, (0, 8, 49), GOLD)
    return img


def build_settings(rows: int = 5) -> Image.Image:
    """Switchboard: four big toggles on a rail, three console buttons, mute on its own card."""
    used = [8, 19, 20, 21, 22, 23, 24, 31, 40]
    img = build_window(rows, "mint", 128, used, style=FRAME_STYLE["settings"])
    _void(img, 0, 0, 1, 9)
    for s in (19, 20, 21, 22, 23, 24):
        _pedestal(img, s, GREEN if s % 2 else ORANGE)
    _void(img, 2, 0, 1, 9)
    _scoreboard(img, 30, 84, WIDTH - 31, 94, GREEN)
    _pedestal(img, 31, GOLD)
    _pedestal(img, 40, GREEN)
    _hot(img, (19, 20, 21, 22, 23, 24), GREEN)
    _hot(img, (31,), GOLD)
    _hot(img, (40,), GREEN)
    _hot(img, (8,), GOLD)
    return img


def build_confirm() -> Image.Image:
    """Purchase confirmation. Three green ACCEPT slots and three red DECLINE slots, each lane
    backed by its own colour, the preview on a gold cushion, and air everywhere else."""
    used = [0, 8, 13, 19, 20, 21, 22, 23, 24, 25, 40]
    img = build_window(6, "mint", 144, used, style=FRAME_STYLE["confirm"])
    d = ImageDraw.Draw(img)
    # ── preview cushion
    px, py = slot_xy(13)
    d.rounded_rectangle([px - 7, py - 6, px + 22, py + 21], radius=4, fill=_c(INK))
    d.rounded_rectangle([px - 6, py - 5, px + 21, py + 20], radius=4, fill=_c(GOLD))
    d.rounded_rectangle([px - 5, py - 4, px + 20, py + 19], radius=3, fill=_c(PANEL_DK))
    _rect(d, px - 5, py - 4, px + 20, py - 4, GOLD_HI)
    _ball_mark(img, px + 8, py + 8, 5)
    # ── the two buttons: filled cell, coloured lane, single frame around all three slots
    # 4 green (cols 1..4) + 3 red (cols 5..7): seven columns centred on column 4, the same column
    # the preview sits on, with one free column on each side of the frame.
    _button_row(img, [19, 20, 21, 22], GREEN)
    _button_row(img, [23, 24, 25], RED)
    _hot(img, (13,), GOLD)
    _hot(img, (0,), GOLD)
    _hot(img, (8,), GOLD)
    _hot(img, (40,), GOLD)
    _ball_mark(img, WIDTH // 2, 118, 8)
    return img


def build_guild(rows: int = 6) -> Image.Image:
    used = [0, 4, 8, 10, 12, 14, 16, 18, 19, 20, 21, 22, 23, 24, 25, 27, 28, 29, 30, 31, 32, 33,
            34, 36, 37, 38, 39, 40, 41, 42, 43, 45, 48, 49, 50, 53]
    img = build_window(rows, "mint", 128, used, style=FRAME_STYLE["settings"])
    _banner(img, 20, GREEN, 8, WIDTH - 9)
    _group(img, 2, 0, 4, 9, GREEN if True else GOLD)
    _void(img, 3, 0, 1, 9)
    _ball_mark(img, 30, 60, 7)
    _hot(img, (4,), GOLD)
    _hot(img, (0, 8, 48, 50), GOLD)
    return img


MODE_KEYS = None


def build_quests(rows: int = 6) -> Image.Image:
    """Quest board: pinned notes left and right, a claim rail at the bottom."""
    used = [4, 10, 11, 12, 13, 19, 20, 21, 22, 28, 29, 31, 49, 53]
    img = build_window(rows, "gold", 136, used, style=FRAME_STYLE["quests"])
    _banner(img, 20, GOLD, 8, WIDTH - 9)
    _void(img, 1, 0, 4, 9)
    for s in (10, 11, 12, 13):
        _pedestal(img, s, GOLD)
    for s in (19, 20, 21, 22):
        _pedestal(img, s, ORANGE)
    for s in (28, 29, 31):
        _pedestal(img, s, BLUE)
    _hoop(img, WIDTH // 2 - 11, 44, GOLD)
    _hot(img, (10, 11, 12, 13), GOLD)
    _hot(img, (19, 20, 21, 22), ORANGE)
    _hot(img, (28, 29, 31), BLUE)
    _hot(img, (4,), GOLD)
    _hot(img, (49, 53), GREEN)
    return img


def build_achievements(rows: int = 6) -> Image.Image:
    used = ([4] + [10, 11, 12, 13, 14, 15, 16] + [19, 20, 21, 22, 23, 24, 25]
            + [28, 29, 30, 31, 32, 33, 34] + [45, 46, 47, 48, 50, 51, 52, 53])
    img = build_window(rows, "violet", 144, used, style=FRAME_STYLE["achievements"])
    _group(img, 1, 1, 3, 7, VIOLET)
    for r in (1, 2, 3):
        _shelf(img, slot_xy(r * 9)[1], 8 + 18 - 3, 8 + 18 * 7 + 19, VIOLET_DK)
    _void(img, 4, 0, 2, 9)
    _hot(img, (4,), GOLD)
    _hot(img, (45, 46, 47, 48, 50, 51, 52, 53), GOLD)
    return img


def build_mailbox(rows: int = 6) -> Image.Image:
    used = [4] + [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                  28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 48, 50, 53]
    img = build_window(rows, "ice", 128, used, style=FRAME_STYLE["mailbox"])
    _banner(img, 20, BLUE, 8, WIDTH - 9)
    _group(img, 1, 1, 3, 7, BLUE)
    for r in (1, 2, 3):
        _shelf(img, slot_xy(r * 9)[1], 8 + 18 - 3, 8 + 18 * 7 + 19, BLUE_DK)
    _void(img, 4, 0, 2, 9)
    _hoop(img, WIDTH // 2 - 11, 58, BLUE)
    _hot(img, (4,), GOLD)
    _hot(img, (48,), GREEN)
    _hot(img, (50,), RED)
    _hot(img, (0, 8), GOLD)
    return img


def build_history(rows: int = 6) -> Image.Image:
    used = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 31, 49]
    img = build_window(rows, "ice", 144, used, style=FRAME_STYLE["history"])
    _void(img, 0, 0, 1, 9)
    _scoreboard(img, 8 + 18 * 2, 21, 8 + 18 * 7 + 15, 32, BLUE)
    _group(img, 2, 0, 2, 9, BLUE)
    for r in (2, 3):
        _rail_tag(img, r * 9, BLUE)
    _void(img, 4, 0, 2, 9)
    _hot(img, (31,), GOLD)
    _hot(img, (49,), GOLD)
    return img


def build_friends(rows: int = 6) -> Image.Image:
    used = [3, 5, 7] + [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                        28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 47, 48, 49, 51]
    img = build_window(rows, "mint", 128, used, style=FRAME_STYLE["settings"])
    _banner(img, 20, GREEN, 8, WIDTH - 9)
    _group(img, 1, 1, 1, 7, GREEN)
    _group(img, 2, 1, 2, 7, BLUE)
    _void(img, 4, 0, 1, 9)
    _hot(img, (3, 5, 7), GREEN)
    _hot(img, (47, 48, 51), GOLD)
    _hot(img, (49,), GOLD)
    return img


def build_party(rows: int = 6) -> Image.Image:
    used = [3, 4, 5, 13, 20, 21, 23, 24, 31, 47, 48, 49]
    img = build_window(rows, "orange", 120, used, style=FRAME_STYLE["party"])
    _void(img, 1, 0, 5, 9)
    _banner(img, 20, ORANGE, 8, WIDTH - 9)
    _pedestal(img, 13, GOLD)
    for s in (20, 21, 23, 24):
        _pedestal(img, s, ORANGE)
    _pedestal(img, 31, BLUE)
    _hoop(img, WIDTH // 2 - 11, 58, ORANGE)
    _net(img, 60, 92, 116, 104)
    _hot(img, (3, 4, 5), GREEN)
    _hot(img, (47, 48, 51), GOLD) if False else None
    _hot(img, (47, 48), GOLD)
    _hot(img, (49,), GOLD)
    return img


def build_ranks(rows: int = 6) -> Image.Image:
    used = [4, 19, 20, 21, 22, 23, 24, 25, 38, 40, 42, 49]
    img = build_window(rows, "gold", 144, used, style=FRAME_STYLE["ranks"])
    _void(img, 0, 0, 2, 9)
    _stairs(img, slot_xy(19)[1] + 16, GOLD)
    for s in (19, 20, 21, 22, 23, 24, 25):
        _pedestal(img, s, GOLD if s % 2 else ORANGE)
    _group(img, 3, 1, 1, 7, GOLD)
    _tunnel(img, 3, 1, 7, GOLD)
    _ball_mark(img, WIDTH // 2, 112, 7)
    _hot(img, (4,), GOLD)
    _hot(img, (38, 40, 42), ORANGE)
    _hot(img, (49,), GOLD)
    return img


def build_spectate(rows: int = 6) -> Image.Image:
    used = [0, 8, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 45, 48, 49, 50, 53]
    img = build_window(rows, "ice", 136, used, style=FRAME_STYLE["spectate"])
    _banner(img, 20, BLUE, 8, WIDTH - 9)
    for r in (1, 2, 3, 4):
        _group(img, r, 1, 1, 7, BLUE)
        _shelf(img, slot_xy(r * 9)[1], 8 + 18 - 3, 8 + 18 * 7 + 19, BLUE_DK)
    _tunnel(img, 4, 1, 7, VIOLET)
    _hot(img, (0, 8, 49), GOLD)
    _hot(img, (45, 53), BLUE)
    return img


def build_player_card(rows: int = 3) -> Image.Image:
    used = [4, 11, 13, 15]
    img = build_window(rows, "ice", 140, used, style=FRAME_STYLE["player_card"])
    _void(img, 0, 0, 3, 9)
    px, py = slot_xy(4)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([px - 6, py - 5, px + 21, py + 20], radius=4, fill=_c(INK))
    d.rounded_rectangle([px - 5, py - 4, px + 20, py + 19], radius=4, fill=_c(BLUE))
    d.rounded_rectangle([px - 4, py - 3, px + 19, py + 18], radius=3, fill=_c(PANEL_DK))
    _rect(d, px - 4, py - 3, px + 19, py - 3, BLUE_HI)
    _ball_mark(img, px + 8, py + 8, 6)
    for s in (11, 13, 15):
        _pedestal(img, s, ORANGE)
    _hot(img, (4,), BLUE)
    _hot(img, (11, 13, 15), ORANGE)
    return img


def build_admin(rows: int = 6) -> Image.Image:
    used = [10, 12, 14, 16, 22, 28, 30, 32, 34, 38, 40, 42, 44, 49]
    img = build_window(rows, "red", 148, used, style=FRAME_STYLE["admin"])
    _void(img, 0, 0, 1, 9)
    _banner(img, 20, RED, 8, WIDTH - 9)
    _scoreboard(img, 8, 38, WIDTH - 9, 52, RED)
    for s in (10, 12, 14, 16):
        _pedestal(img, s, RED)
    _group(img, 3, 0, 2, 9, GOLD)
    _void(img, 4, 1, 1, 7)
    _hot(img, (10, 12, 14, 16), RED)
    _hot(img, (22, 28, 30, 32, 34), GOLD)
    _hot(img, (38, 40, 42, 44), GREEN if False else BLUE)
    _hot(img, (49,), GOLD)
    return img


def build_reports(rows: int = 6) -> Image.Image:
    used = [4] + [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                  28, 29, 30, 31, 32, 33, 34, 49, 53]
    img = build_window(rows, "red", 136, used, style=FRAME_STYLE["reports"])
    _banner(img, 20, RED, 8, WIDTH - 9)
    _group(img, 1, 1, 3, 7, RED)
    for r in (1, 2, 3):
        _rail_tag(img, r * 9, RED)
    _void(img, 4, 0, 2, 9)
    _hot(img, (4,), GOLD)
    _hot(img, (49, 53), GREEN)
    return img


def build_warnings(rows: int = 6) -> Image.Image:
    used = [4] + [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                  28, 29, 30, 31, 32, 33, 34, 48, 53]
    img = build_window(rows, "red", 144, used, style=FRAME_STYLE["warnings"])
    _banner(img, 20, RED, 8, WIDTH - 9)
    _group(img, 1, 1, 3, 7, RED_DK)
    for r in (1, 2, 3):
        _rail_tag(img, r * 9, RED)
    _void(img, 4, 0, 2, 9)
    _hot(img, (4,), GOLD)
    _hot(img, (48,), GREEN)
    _hot(img, (53,), GOLD)
    return img


def build_studio(rows: int = 6) -> Image.Image:
    used = [4, 19, 21, 23, 25, 29, 31, 33, 47, 49, 51]
    img = build_window(rows, "ice", 144, used, style=FRAME_STYLE["history"])
    d = ImageDraw.Draw(img)
    grid = (PANEL[0] + 10, PANEL[1] + 10, PANEL[2] + 12)
    for x in range(10, WIDTH - 9, 9):
        _rect(d, x, 54, x, 118, grid)
    for y in range(54, 118, 9):
        _rect(d, 10, y, WIDTH - 11, y, grid)
    _void(img, 0, 0, 1, 9)
    for s in (19, 21, 23, 25):
        _pedestal(img, s, BLUE)
    for s in (29, 31, 33):
        _pedestal(img, s, GOLD)
    _hoop(img, WIDTH // 2 - 11, 58, BLUE)
    _hot(img, (19, 21, 23, 25), BLUE)
    _hot(img, (29, 31, 33), GOLD)
    _hot(img, (4, 47, 51), GOLD)
    _hot(img, (49,), GOLD)
    return img


# ══════════════════════════════════════════════════════════════════════════════════════════
#  PROVIDER OUTPUT
# ══════════════════════════════════════════════════════════════════════════════════════════
OVERLAYS = {
    0xE350: ("shop", "gui_overlay_shop.png", 6),
    0xE351: ("shop_grid", "gui_overlay_shop_grid.png", 6),
    0xE352: ("tags", "gui_overlay_tags.png", 6),
    0xE353: ("cosmetics", "gui_overlay_cosmetics.png", 6),
    0xE354: ("play", "gui_overlay_play.png", 6),
    0xE355: ("hub", "gui_overlay_hub.png", 6),
    0xE356: ("hub_op", "gui_overlay_hub_op.png", 6),
    0xE357: ("leaderboards", "gui_overlay_leaderboards.png", 6),
    0xE358: ("settings", "gui_overlay_settings.png", 5),
    0xE359: ("confirm", "gui_overlay_confirm.png", 6),
    0xE35A: ("play_bot", "gui_overlay_play_bot.png", 5),
    0xE35C: ("guild", "gui_overlay_guild.png", 6),
    0xE35D: ("quests", "gui_overlay_quests.png", 6),
    0xE340: ("achievements", "gui_overlay_achievements.png", 6),
    0xE341: ("mailbox", "gui_overlay_mailbox.png", 6),
    0xE342: ("history", "gui_overlay_history.png", 6),
    0xE343: ("friends", "gui_overlay_friends.png", 6),
    0xE344: ("party", "gui_overlay_party.png", 6),
    0xE345: ("ranks", "gui_overlay_ranks.png", 6),
    0xE346: ("spectate", "gui_overlay_spectate.png", 6),
    0xE347: ("player_card", "gui_overlay_player_card.png", 3),
    0xE348: ("admin", "gui_overlay_admin.png", 6),
    0xE349: ("reports", "gui_overlay_reports.png", 6),
    0xE34A: ("warnings", "gui_overlay_warnings.png", 6),
    0xE34B: ("studio", "gui_overlay_studio.png", 6),
}


def build_overlay(key: str) -> Image.Image:
    builders = {
        "shop": lambda: build_shop(6, grid=False),
        "shop_grid": lambda: build_shop(6, grid=True),
        "tags": lambda: build_tags(6),
        "cosmetics": lambda: build_cosmetics(6),
        "play": lambda: build_play(6),
        "play_bot": lambda: build_play_bot(5),
        "hub": lambda: build_hub(6, op=False),
        "hub_op": lambda: build_hub(6, op=True),
        "leaderboards": lambda: build_leaderboards(6),
        "settings": lambda: build_settings(5),
        "confirm": build_confirm,
        "guild": lambda: build_guild(6),
        "quests": lambda: build_quests(6),
        "achievements": lambda: build_achievements(6),
        "mailbox": lambda: build_mailbox(6),
        "history": lambda: build_history(6),
        "friends": lambda: build_friends(6),
        "party": lambda: build_party(6),
        "ranks": lambda: build_ranks(6),
        "spectate": lambda: build_spectate(6),
        "player_card": lambda: build_player_card(3),
        "admin": lambda: build_admin(6),
        "reports": lambda: build_reports(6),
        "warnings": lambda: build_warnings(6),
        "studio": lambda: build_studio(6),
    }
    return builders[key]()


def _pin_width(img: Image.Image) -> Image.Image:
    w, h = img.size
    if img.getpixel((w - 1, h - 1))[3] == 0:
        img.putpixel((w - 1, h - 1), (255, 255, 255, 3))
    return img


def generate_gui_overlays(font_tex_dir: Path) -> list[dict]:
    font_tex_dir.mkdir(parents=True, exist_ok=True)
    providers: list[dict] = []
    for codepoint, (key, filename, rows) in sorted(OVERLAYS.items()):
        img = _pin_width(build_overlay(key))
        expected = overlay_height(rows)
        assert img.height == expected, f"{filename}: {img.height}px for {rows} rows (want {expected})"
        assert img.width == WIDTH, f"{filename}: width {img.width} != {WIDTH}"
        img.save(font_tex_dir / filename)
        providers.append({
            "type": "bitmap",
            "file": f"bosketball:font/{filename}",
            "height": img.height,
            "ascent": 13 + HEADER_PAD,
            "chars": [chr(codepoint)],
        })
        print(f"  GUI overlay {hex(codepoint)} {filename:32s} {img.width}x{img.height}")
    return providers


if __name__ == "__main__":
    out = Path(__file__).resolve().parent.parent / "resourcepack/assets/bosketball/textures/font"
    generate_gui_overlays(out)
