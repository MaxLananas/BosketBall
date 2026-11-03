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
#  THE BASE WINDOW
# ══════════════════════════════════════════════════════════════════════════════════════════
def build_window(rows: int = 6, accent: str = "orange", plaque_px: int = 128) -> Image.Image:
    accent_rgb = ACCENTS.get(accent, ACCENTS["orange"])[0]
    img = Image.new("RGBA", (WIDTH, overlay_height(rows)), (0, 0, 0, 0))
    _case(img, rows, accent_rgb)
    for r in range(rows):
        for c in range(9):
            _well(img, r * 9 + c, accent_rgb)
    _plaque(img, accent_rgb, plaque_px)
    _footer(img, rows, accent_rgb)
    return img


# ── structural motifs, drawn on top of a base window ─────────────────────────────────────
def _shelf(img: Image.Image, sy: int, x0: int, x1: int, accent_rgb) -> None:
    """Chrome shelf lip under a row of slots. A slot occupies [sy, sy+15] and the next row starts
    at sy+18, so the shelf gets exactly the two gutter rows sy+16 and sy+17 — drawing it any lower
    paints over the items of the row below (that mistake shipped once)."""
    d = ImageDraw.Draw(img)
    _rect(d, x0, sy + 16, x1, sy + 16, RIM)
    _rect(d, x0, sy + 17, x1, sy + 17, accent_rgb)


def _base_bar(img: Image.Image, sy: int, x0: int, x1: int, accent_rgb) -> None:
    """Group base: a flat two-tone bar in the gutter, so a block of slots reads as standing on
    something without stealing a single pixel from the items."""
    d = ImageDraw.Draw(img)
    _rect(d, x0, sy + 16, x1, sy + 16, tuple(max(0, c - 60) for c in accent_rgb))
    _rect(d, x0, sy + 17, x1, sy + 17, accent_rgb)


def _ticket(img: Image.Image, slot: int, accent_rgb) -> None:
    """A punched ticket / cart badge hanging off a slot (shop cart, claimables)."""
    sx, sy = slot_xy(slot)
    d = ImageDraw.Draw(img)
    x0, y0 = sx - 4, sy - 5
    d.rounded_rectangle([x0, y0, x0 + 23, y0 + 25], radius=3, fill=_c(INK))
    d.rounded_rectangle([x0 + 1, y0 + 1, x0 + 22, y0 + 24], radius=3, fill=_c(accent_rgb))
    _diag(img, x0 + 2, y0 + 2, x0 + 21, y0 + 23,
          tuple(max(0, c - 60) for c in accent_rgb),
          tuple(min(255, c + 40) for c in accent_rgb), pitch=4)
    for yy in (y0 + 7, y0 + 17):
        img.putpixel((x0 + 12, yy), _c(INK))


def _ribbon(img: Image.Image, y: int, accent_rgb, x0: int = 8, x1: int = WIDTH - 9) -> None:
    d = ImageDraw.Draw(img)
    _rect(d, x0, y, x1, y + 1, INK)
    _rect(d, x0 + 1, y + 1, x1 - 1, y + 2, accent_rgb)
    _rect(d, x0 + 1, y + 3, x1 - 1, y + 3, tuple(max(0, c - 70) for c in accent_rgb))


def _bays(img: Image.Image, fy0: int, fy1: int, accent_rgb) -> None:
    """Two vertical bays + a central chrome pillar: the 'locker room' silhouette."""
    _pillar(img, WIDTH // 2, fy0 + 2, fy1 - 2, accent_rgb)
    d = ImageDraw.Draw(img)
    for x in (WIDTH // 2 - 4, WIDTH // 2 + 4):
        _rect(d, x, fy0 + 4, x, fy1 - 4, PANEL_DK)


# ══════════════════════════════════════════════════════════════════════════════════════════
#  SCENES — one per menu the server actually opens
# ══════════════════════════════════════════════════════════════════════════════════════════
def build_shop(rows: int = 6, grid: bool = False) -> Image.Image:
    """Market stall. The "all" category gets shelves and the cart; a single category is a plain
    stock wall (a shelf under every row would fight the paginated item grid)."""
    img = build_window(rows, "orange")
    _ribbon(img, 21, ORANGE)
    if not grid:
        for r in (1, 2, 3):
            _group(img, r, 1, 1, 7, ORANGE if r % 2 else GOLD)
            sx0, sy = slot_xy(r * 9 + 1)
            sx1, _ = slot_xy(r * 9 + 7)
            _shelf(img, sy, sx0 - 3, sx1 + 18, ORANGE if r % 2 else GOLD)
        _group(img, 4, 1, 1, 4, BLUE)
        _group(img, 4, 5, 1, 3, GREEN)
        _ticket(img, 0, GOLD)
        _hot(img, (48, 49, 50), GREEN)
    else:
        _group(img, 1, 0, rows - 2, 9, PANEL_HI)
    _hot(img, (0, 8), GOLD)
    _hot(img, (45, 53), BLUE)
    return img


def build_tags(rows: int = 6) -> Image.Image:
    """Vault wall: the tag cards hang in two framed vitrines."""
    img = build_window(rows, "violet")
    for r in (1, 2, 3):
        _group(img, r, 0, 1, 9, VIOLET)
        _, sy = slot_xy(r * 9)
        _shelf(img, sy, 6, WIDTH - 7, VIOLET_DK)
    _group(img, 4, 2, 1, 5, GOLD)
    _hot(img, (0, 8), GOLD)
    _hot(img, (45, 53), BLUE)
    _hot(img, (47, 48, 49, 50), VIOLET)
    return img


def build_cosmetics(rows: int = 6) -> Image.Image:
    """Locker bay: balls on the left, dunks on the right, pedestals under each row."""
    img = build_window(rows, "rose")
    for r in (1, 2):
        _group(img, r, 0, 1, 4, ROSE)
        _group(img, r, 5, 1, 4, BLUE)
        _, sy = slot_xy(r * 9)
        _base_bar(img, sy, 4, 79, ROSE)
        _base_bar(img, sy, 84, WIDTH - 5, BLUE)
    _group(img, 3, 2, 1, 5, GOLD)
    _hot(img, (0,), GOLD)
    _hot(img, (8,), GOLD)
    _hot(img, (45, 53), GOLD)
    _hot(img, (48, 49, 50), GREEN)
    return img


def build_play(rows: int = 6) -> Image.Image:
    """Four mode cards on a stand, then the practice row and the training bay."""
    img = build_window(rows, "orange")
    _group(img, 1, 1, 1, 7, ORANGE)
    _group(img, 2, 1, 1, 7, BLUE)
    _group(img, 3, 1, 2, 7, GOLD)
    _hot(img, (10, 12, 14, 16), ORANGE)
    _hot(img, (28, 30, 32, 34), BLUE)
    _hot(img, (0, 8), GOLD)
    _hot(img, (49,), RED)
    return img


def build_play_bot(rows: int = 5) -> Image.Image:
    """Training panel: difficulty tiers on a plinth, the AI roster underneath."""
    img = build_window(rows, "violet", plaque_px=132)
    _group(img, 0, 1, 1, 7, VIOLET)
    _group(img, 1, 1, 1, 7, ORANGE)
    _group(img, 2, 1, 1, 7, GOLD)
    _base_bar(img, slot_xy(9)[1], 8 + 18 - 3, 8 + 18 * 8 + 19, VIOLET)
    _hot(img, (4, 40), GOLD)
    _hot(img, (30, 32, 34), ORANGE)
    return img


def build_hub(rows: int = 6, op: bool = False) -> Image.Image:
    """Lobby: a hero stage (locker room), a toolbar of five main actions, then the dashboard rows.
    The sockets follow the slots GuiMenuManager actually fills, so art and code cannot disagree."""
    img = build_window(rows, "red" if op else "orange", plaque_px=124)
    acc = RED if op else ORANGE
    _group(img, 0, 2, 1, 5, acc)          # stage behind slot 13
    _hot(img, (13,), GOLD)
    _group(img, 1, 1, 1, 7, acc)          # toolbar: slots 20..24
    _hot(img, (20, 21, 22, 23, 24), acc)
    _group(img, 2, 1, 1, 7, BLUE)         # dashboard: slots 29,30,31,33
    _hot(img, (29, 30, 31, 33), BLUE)
    _group(img, 3, 1, 1, 7, GOLD)         # footer row: 39,40,41,43 (+44 OP)
    _hot(img, (39, 40, 41, 43), GOLD)
    if op:
        _hot(img, (44,), RED)
    _hot(img, (0, 8), GOLD)
    return img


def build_leaderboards(rows: int = 6) -> Image.Image:
    """Podium stands under the top three, ledger pips down the rail, category wheel below."""
    img = build_window(rows, "gold")
    _, sy = slot_xy(12)
    _base_bar(img, sy, 8 + 18 * 3 - 3, 8 + 18 * 5 + 19, GOLD)
    _group(img, 5, 1, 1, 3, GOLD)
    _group(img, 5, 5, 1, 3, GOLD)
    for r in range(1, 5):
        _rail_tag(img, r * 9, GOLD)
    _hot(img, (12, 13, 14), GOLD)
    _hot(img, (0, 8), GOLD)
    _hot(img, (45, 53), BLUE)
    return img


def build_settings(rows: int = 5) -> Image.Image:
    """Switchboard: each setting is a channel on its own rail, mute/back on the console row."""
    img = build_window(rows, "mint", plaque_px=120)
    for c in range(9):
        _group(img, 1, c, 2, 1, GREEN if c % 2 == 0 else ORANGE)
    for c in range(3, 6):
        _group(img, 3, c, 1, 1, GOLD)
    _hot(img, (0, 8), GOLD)
    _hot(img, (36, 44), RED)
    _hot(img, (40, 41), GREEN)
    return img


def build_confirm() -> Image.Image:
    """A 54-slot sheet: preview in a gold frame, ACCEPT and DECLINE on their own cards."""
    rows = 6
    img = build_window(rows, "mint", plaque_px=140)
    px, py = slot_xy(13)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([px - 6, py - 5, px + 21, py + 20], radius=3, fill=_c(INK))
    d.rounded_rectangle([px - 5, py - 4, px + 20, py + 19], radius=3, fill=_c(GOLD))
    d.rounded_rectangle([px - 4, py - 3, px + 19, py + 18], radius=2, fill=_c(PANEL_DK))
    _rect(d, px - 4, py - 3, px + 19, py - 3, GOLD_HI)
    _hot(img, (13,), GOLD)
    _group(img, 2, 2, 1, 2, GREEN)
    _group(img, 2, 5, 1, 2, RED)
    _hot(img, (20, 21), GREEN)
    _hot(img, (24, 25), RED)
    sx, sy = slot_xy(20)
    _base_bar(img, sy, sx - 3, sx + 41, GREEN)
    sx, sy = slot_xy(24)
    _base_bar(img, sy, sx - 3, sx + 41, RED)
    _hot(img, (0, 8), GOLD)
    _hot(img, (40, 41), GOLD)
    return img


def build_guild(rows: int = 6) -> Image.Image:
    img = build_window(rows, "mint", plaque_px=124)
    _group(img, 0, 2, 1, 5, GREEN)
    _group(img, 1, 1, 2, 4, GREEN)
    _group(img, 1, 5, 2, 4, GOLD)
    _hot(img, (4,), GOLD)
    _hot(img, (22,), GREEN)
    _hot(img, (0, 8, 45, 53), GOLD)
    return img


def build_quests(rows: int = 6) -> Image.Image:
    """Quest board: pinned notes in two columns, a progress rail underneath."""
    img = build_window(rows, "gold", plaque_px=130)
    _ribbon(img, 21, GOLD)
    for r in (1, 2, 3):
        _group(img, r, 0, 1, 4, GOLD if r % 2 else ORANGE)
        _group(img, r, 5, 1, 4, GOLD if r % 2 else ORANGE)
    _hot(img, (0, 8), GOLD)
    _hot(img, (45, 53), BLUE)
    _hot(img, (49,), GREEN)
    return img


def build_achievements(rows: int = 6) -> Image.Image:
    img = build_window(rows, "violet", plaque_px=140)
    for r in (1, 2, 3, 4):
        _group(img, r, 0, 1, 9, VIOLET if r % 2 else GOLD)
    _hot(img, (0, 8), GOLD)
    _hot(img, (45, 53), BLUE)
    return img


def build_mailbox(rows: int = 6) -> Image.Image:
    img = build_window(rows, "ice", plaque_px=120)
    _group(img, 0, 3, 1, 3, BLUE)
    for r in (1, 2, 3):
        _group(img, r, 1, 1, 7, BLUE if r % 2 else GOLD)
    _hot(img, (4,), GOLD)
    _hot(img, (48, 50), GREEN if True else BLUE)
    _hot(img, (0, 8), GOLD)
    return img


def build_history(rows: int = 6) -> Image.Image:
    """Ledger: numbered rows, alternating tint bands, a summary card up top."""
    img = build_window(rows, "ice", plaque_px=136)
    for r in range(1, 5):
        _group(img, r, 0, 1, 9, BLUE if r % 2 else PANEL_HI)
    for r in range(1, 5):
        _rail_tag(img, r * 9, BLUE)
    _hot(img, (22,), GOLD)
    _hot(img, (0, 8), GOLD)
    _hot(img, (45, 53), BLUE)
    return img


def build_friends(rows: int = 6) -> Image.Image:
    img = build_window(rows, "mint", plaque_px=124)
    _group(img, 0, 0, 1, 4, GREEN)
    _group(img, 0, 5, 1, 4, GOLD)
    for r in (1, 2, 3):
        _group(img, r, 1, 1, 7, GREEN if r % 2 else BLUE)
    _hot(img, (3, 5, 7), GREEN)
    _hot(img, (0, 8, 53), GOLD)
    return img


def build_party(rows: int = 6) -> Image.Image:
    img = build_window(rows, "orange", plaque_px=120)
    _group(img, 0, 3, 1, 3, ORANGE)
    for r in (1, 2):
        _group(img, r, 1, 1, 3, ORANGE)
        _group(img, r, 5, 1, 3, BLUE)
    _group(img, 3, 2, 1, 5, GOLD)
    _hot(img, (13, 31), GOLD)
    _hot(img, (3, 4, 5), ORANGE)
    _hot(img, (0, 8, 53), GOLD)
    return img


def build_ranks(rows: int = 6) -> Image.Image:
    """The ladder: seven rank plinths climbing left to right (slots 19..25), the badge tunnel
    underneath, and the back button on its own card."""
    img = build_window(rows, "gold", plaque_px=144)
    for i, c in enumerate(range(0, 7)):
        _group(img, 2, c + 1, 1, 1, GOLD if i % 2 else ORANGE)
        _base_bar(img, slot_xy(19 + i)[1], slot_xy(19 + i)[0] - 3, slot_xy(19 + i)[0] + 19,
                  GOLD if i % 2 else ORANGE)
    _group(img, 3, 1, 1, 7, GOLD)
    _hot(img, (19, 20, 21, 22, 23, 24, 25), GOLD)
    _hot(img, (38, 40, 42), ORANGE)
    _hot(img, (4,), GOLD)
    _hot(img, (0, 8, 49), GOLD)
    return img


def build_spectate(rows: int = 6) -> Image.Image:
    """Arena stands: two banks of live matches with a court stripe between."""
    img = build_window(rows, "ice", plaque_px=130)
    _ribbon(img, 21, BLUE)
    for r in (1, 2, 3):
        _group(img, r, 0, 1, 4, BLUE)
        _group(img, r, 5, 1, 4, VIOLET)
        _, sy = slot_xy(r * 9)
        _shelf(img, sy, 6, WIDTH - 7, BLUE_DK)
    _hot(img, (22,), GOLD)
    _hot(img, (0, 8, 49), GOLD)
    return img


def build_player_card(rows: int = 3) -> Image.Image:
    """The 27-slot player card: a portrait plate on slot 4, three action cards underneath."""
    img = build_window(rows, "ice", plaque_px=136)
    px, py = slot_xy(4)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([px - 5, py - 4, px + 20, py + 19], radius=3, fill=_c(INK))
    d.rounded_rectangle([px - 4, py - 3, px + 19, py + 18], radius=3, fill=_c(BLUE))
    d.rounded_rectangle([px - 3, py - 2, px + 18, py + 17], radius=2, fill=_c(PANEL_DK))
    _rect(d, px - 3, py - 2, px + 18, py - 2, BLUE_HI)
    _hot(img, (4,), BLUE)
    for s in (11, 13, 15):
        _group(img, 1, s % 9, 1, 1, ORANGE)
    _base_bar(img, slot_xy(11)[1], slot_xy(11)[0] - 3, slot_xy(15)[0] + 19, ORANGE)
    _hot(img, (11, 13, 15), ORANGE)
    _hot(img, (0, 8), GOLD)
    return img


def build_admin(rows: int = 6) -> Image.Image:
    """Server control: red-keyed vitals on top, two banks of switches below."""
    img = build_window(rows, "red", plaque_px=144)
    _ribbon(img, 21, RED)
    _group(img, 1, 0, 1, 4, RED)
    _group(img, 1, 5, 1, 4, ORANGE)
    _group(img, 2, 0, 1, 4, GOLD)
    _group(img, 2, 5, 1, 4, GREEN)
    _group(img, 3, 0, 1, 4, BLUE)
    _group(img, 3, 5, 1, 4, VIOLET)
    _hot(img, (10, 12, 14, 16), RED)
    _hot(img, (22, 28, 30, 32, 34), GOLD)
    _hot(img, (0, 8, 49), GOLD)
    return img


def build_reports(rows: int = 6) -> Image.Image:
    img = build_window(rows, "red", plaque_px=128)
    for r in (1, 2, 3):
        _group(img, r, 1, 1, 7, RED if r % 2 else ORANGE)
    _hot(img, (0, 8, 53), GOLD)
    _hot(img, (45, 49), GREEN)
    return img


def build_studio(rows: int = 6) -> Image.Image:
    """Arena studio: a blueprint grid over the floor, tool rail on the left."""
    img = build_window(rows, "ice", plaque_px=140)
    d = ImageDraw.Draw(img)
    grid = (PANEL[0] + 10, PANEL[1] + 10, PANEL[2] + 12)
    for x in range(8, WIDTH - 8, 9):
        _rect(d, x, 24, x, overlay_height(rows) - 20, grid)
    for y in range(24, overlay_height(rows) - 18, 9):
        _rect(d, 8, y, WIDTH - 9, y, grid)
    for r in (1, 2):
        _group(img, r, 2, 1, 5, BLUE)
    _group(img, 3, 1, 1, 3, GOLD)
    _group(img, 3, 5, 1, 3, GREEN)
    _hot(img, (4,), GOLD)
    _hot(img, (19, 21, 23, 25), BLUE)
    _hot(img, (0, 8, 49), GOLD)
    return img


def build_warnings(rows: int = 6) -> Image.Image:
    img = build_window(rows, "red", plaque_px=140)
    for r in (1, 2, 3):
        _group(img, r, 1, 1, 7, RED if r % 2 else RED_DK)
    _hot(img, (0, 8, 53), GOLD)
    _hot(img, (45,), GREEN)
    _hot(img, (49,), RED)
    return img


# ══════════════════════════════════════════════════════════════════════════════════════════
#  PROVIDER OUTPUT
# ══════════════════════════════════════════════════════════════════════════════════════════
# Codepoints are allocated in one table so a collision is impossible by construction. U+E350..E359
# are the original ten (kept, so every existing menu title keeps working). U+E35A..E35D and
# U+E340..E34F were verified free in default.json before use; the locker plates own U+E35E..E362.
OVERLAYS = {
    # ── original ladder ──
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
    # ── added in v3: the rest of the server ──
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
    """A 1px near-transparent corner stops the renderer from guessing a width for the glyph."""
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
            # ascent = 13 + HEADER_PAD -> image y = 7 lands exactly on the container's top edge.
            "height": img.height,
            "ascent": 13 + HEADER_PAD,
            "chars": [chr(codepoint)],
        })
        print(f"  GUI overlay {hex(codepoint)} {filename:32s} {img.width}x{img.height} ({rows} rows)")
    return providers


if __name__ == "__main__":
    out = Path(__file__).resolve().parent.parent / "resourcepack/assets/bosketball/textures/font"
    generate_gui_overlays(out)
