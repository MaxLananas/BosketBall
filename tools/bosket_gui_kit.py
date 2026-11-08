#!/usr/bin/env python3
"""BosketBall GUI kit — "Court Console" v5 (light).

v4 unified the frame and the grid. v5 changes the *material*: the container is now a
bright brushed-aluminium plate with dark inset wells, the direction premium GUI packs
use because saturated 16x16 icons pop instantly against it and the menu stops looking
like a cave. The drawing budget doubled: machined body grain, double keyline, chiselled
bevels, corner screws, a riveted header ribbon, stitched rail and recessed wells with a
two-pixel shadow.

Four rules survive from v4, they are what keeps the extra detail readable:

  1. ONE frame for every container.
  2. ONE grid: all 54 wells are identical, at the exact client pitch.
  3. ONE accent per menu, used only on the title underline, the door lips and the rail chips.
  4. NOTHING crosses a well, and every pixel is opaque (the vanilla chest texture is
     never allowed to bleed through).

GEOMETRY CONTRACT (measured against the live client, never change without re-measuring):

* image x = 0 is the left edge of the container, 176px wide;
* image y = 7 is the top edge of the container, pixels above are the plaque band;
* slot row r / column c has its 16x16 well at (8 + 18c, 25 + 18r);
* the runtime caption is laid out on the default-font baseline, body y 12..20, so the
  plaque keeps that band clear (a baked title would be printed twice);
* texture height must equal 7 + 18 + 18*rows + 4 (6 rows -> 137, 5 -> 119, 3 -> 83).

Pure 1x pixel art: no antialiasing, no gradients beyond ordered dithering.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

# ---------------------------------------------------------------------------
#  PALETTE — brushed aluminium & gunmetal, warm orange as the house accent
# ---------------------------------------------------------------------------
KEY = (16, 22, 31)
KEY_SOFT = (58, 70, 88)

BODY_HI = (232, 239, 247)
BODY_MID = (203, 213, 226)
BODY_LO = (172, 185, 203)
BODY_KEY = (120, 134, 152)

BEVEL_HI = (252, 254, 255)
BEVEL_LO = (88, 102, 121)

WELL = (52, 62, 78)
WELL_DEEP = (38, 47, 61)
WELL_SHADOW = (21, 27, 38)
WELL_LIP = (132, 148, 168)

CELL_RIM = (150, 164, 182)
CELL_RIM_HI = (226, 234, 244)
PLAQUE_TOP = (62, 75, 94)
PLAQUE_BOT = (38, 47, 61)
CAPTION_INK = (26, 33, 44)

RIBBON = (241, 246, 252)
RIBBON_KEY = (72, 86, 105)

ORANGE = (232, 122, 24)
ORANGE_HI = (255, 174, 70)
GOLD = (226, 178, 52)
GOLD_HI = (255, 226, 140)
ICE = (64, 138, 216)
ICE_HI = (150, 205, 255)
MINT = (74, 172, 106)
MINT_HI = (150, 232, 170)
VIOLET = (140, 108, 210)
VIOLET_HI = (196, 176, 250)
ROSE = (206, 110, 138)
ROSE_HI = (244, 178, 198)
RED = (206, 62, 70)
RED_HI = (255, 130, 130)
SILVER = (176, 184, 196)

ACCENTS = {
    "orange": (ORANGE, ORANGE_HI),
    "gold": (GOLD, GOLD_HI),
    "ice": (ICE, ICE_HI),
    "mint": (MINT, MINT_HI),
    "violet": (VIOLET, VIOLET_HI),
    "rose": (ROSE, ROSE_HI),
    "red": (RED, RED_HI),
    "silver": (SILVER, (226, 232, 242)),
}

# ---------------------------------------------------------------------------
#  GEOMETRY
# ---------------------------------------------------------------------------
WIDTH = 176
HEADER_PAD = 7
CONTAINER_TOP = HEADER_PAD
ROW_PITCH = 18
WELL_SIZE = 16

NAV_SLOTS = (46, 49, 52)


def slot_xy(slot: int) -> tuple[int, int]:
    row, col = divmod(slot, 9)
    return 8 + col * ROW_PITCH, CONTAINER_TOP + 18 + row * ROW_PITCH


def overlay_height(rows: int) -> int:
    return HEADER_PAD + 18 + rows * ROW_PITCH + 4


def _c(rgb, a: int = 255):
    return (rgb[0], rgb[1], rgb[2], a)


def _tint(rgb, delta: int):
    return tuple(max(0, min(255, ch + delta)) for ch in rgb[:3])


def _mix(a, b, t: float):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _rect(d: ImageDraw.ImageDraw, x0, y0, x1, y1, rgb) -> None:
    if x1 < x0 or y1 < y0:
        return
    d.rectangle([x0, y0, x1, y1], fill=_c(rgb))


def _hline(img: Image.Image, x0: int, x1: int, y: int, rgb) -> None:
    if y < 0 or y >= img.height:
        return
    _rect(ImageDraw.Draw(img), x0, y, x1, y, rgb)


def _vline(img: Image.Image, x: int, y0: int, y1: int, rgb) -> None:
    if x < 0 or x >= img.width:
        return
    _rect(ImageDraw.Draw(img), x, y0, x, y1, rgb)


# ---------------------------------------------------------------------------
#  FRAME
# ---------------------------------------------------------------------------
def _body(img: Image.Image, x0: int, y0: int, x1: int, y1: int) -> None:
    """Brushed aluminium plate: vertical gradient + deterministic machining grain."""
    span = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        base = _mix(BODY_HI, BODY_LO, (y - y0) / span)
        if y % 3 == 0:
            base = _tint(base, -4)
        elif y % 3 == 2:
            base = _tint(base, 2)
        for x in range(x0, x1 + 1):
            grain = 1 if (x * 13 + y * 7) % 61 == 0 else 0
            img.putpixel((x, y), _c(_tint(base, grain)))


def _screw(img: Image.Image, cx: int, cy: int) -> None:
    """3x3 rivet head: lit top-left quadrant, shadowed bottom-right, dark pin centre."""
    img.putpixel((cx - 1, cy - 1), _c(BEVEL_HI))
    img.putpixel((cx, cy - 1), _c(BEVEL_HI))
    img.putpixel((cx - 1, cy), _c(BEVEL_HI))
    img.putpixel((cx, cy), _c(KEY_SOFT))
    img.putpixel((cx + 1, cy - 1), _c(CELL_RIM))
    img.putpixel((cx - 1, cy + 1), _c(CELL_RIM))
    img.putpixel((cx + 1, cy), _c(CELL_RIM))
    img.putpixel((cx, cy + 1), _c(CELL_RIM))
    img.putpixel((cx + 1, cy + 1), _c(BEVEL_LO))


def _case(img: Image.Image, accent_rgb, accent_hi) -> None:
    w, h = WIDTH, img.height
    top = CONTAINER_TOP
    d = ImageDraw.Draw(img)

    d.rounded_rectangle([0, top, w - 1, h - 1], radius=3, fill=_c(KEY))
    _rect(d, 1, top + 1, w - 2, h - 2, BODY_KEY)
    _rect(d, 2, top + 2, w - 3, h - 3, KEY)
    _body(img, 3, top + 3, w - 4, h - 4)

    _hline(img, 3, w - 4, top + 3, BEVEL_HI)
    _hline(img, 3, w - 4, top + 4, (240, 245, 252))
    _vline(img, 3, top + 3, h - 4, BEVEL_HI)
    _vline(img, 4, top + 5, h - 5, (216, 226, 239))
    _hline(img, 4, w - 5, h - 5, (200, 211, 226))
    _hline(img, 4, w - 5, h - 4, BEVEL_LO)
    _vline(img, w - 4, top + 4, h - 4, BEVEL_LO)
    _vline(img, w - 5, top + 5, h - 5, (196, 207, 222))
    _hline(img, 2, w - 3, h - 2, BEVEL_HI)
    _hline(img, 3, w - 4, h - 3, BODY_KEY)

    for cx, cy in ((6, top + 6), (w - 7, top + 6), (6, h - 7), (w - 7, h - 7)):
        _screw(img, cx, cy)

    for vx in (5, WIDTH - 15):
        for vy in (9, 13, 17):
            _hline(img, vx, vx + 9, vy, BEVEL_LO)
            _hline(img, vx, vx + 9, vy + 1, BODY_LO)
            _hline(img, vx + 1, vx + 8, vy + 2, BEVEL_HI)

    for y in range(top + 15, h - 15, 14):
        for rx in (4, w - 5):
            img.putpixel((rx, y), _c(BODY_KEY))
            img.putpixel((rx, y + 1), _c(BEVEL_HI))

    img.putpixel((2, top + 2), _c(accent_rgb))
    img.putpixel((w - 3, top + 2), _c(accent_rgb))
    img.putpixel((2, h - 3), _c(accent_rgb))
    img.putpixel((w - 3, h - 3), _c(accent_rgb))

    mid = (top + h) // 2
    for side in (4, w - 5):
        _vline(img, side, mid - 4, mid + 3, accent_rgb)
        img.putpixel((side, mid - 4), _c(accent_hi))
        img.putpixel((side, mid + 3), _c(accent_hi))


def _plaque(img: Image.Image, accent_rgb, accent_hi, width_px: int = 128) -> None:
    """Dark slate title plaque: the y 12..20 caption band stays flat ink for white text."""
    w = WIDTH
    d = ImageDraw.Draw(img)
    half = (width_px or 128) // 2
    cx = w // 2
    x0, x1 = cx - half, cx + half

    _rect(d, x0 - 3, 2, x1 + 3, 25, KEY)
    _rect(d, x0 - 2, 3, x1 + 2, 24, CELL_RIM_HI)
    _rect(d, x0 - 1, 4, x1 + 1, 23, CELL_RIM)
    for y in range(5, 23):
        t = (y - 5) / 17
        _hline(img, x0, x1, y, _mix(PLAQUE_TOP, PLAQUE_BOT, t))

    _hline(img, x0, x1, 5, (104, 120, 142))
    _hline(img, x0, x1, 6, (86, 101, 122))
    _rect(d, x0, 12, x1, 20, CAPTION_INK)
    _hline(img, x0 + 1, x1 - 1, 7, _tint(PLAQUE_TOP, -22))
    _vline(img, x0 + 1, 7, 20, _tint(PLAQUE_TOP, -22))
    _hline(img, x0 + 1, x1 - 1, 20, _tint(PLAQUE_BOT, 8))
    _vline(img, x1 - 1, 7, 20, _tint(PLAQUE_BOT, 8))
    _hline(img, x0 + 1, x1 - 1, 21, _tint(PLAQUE_BOT, 14))
    _hline(img, x0, x1, 22, accent_rgb)
    _hline(img, x0 + 1, x1 - 1, 23, _tint(accent_rgb, -34))
    _hline(img, x0 + 2, x1 - 2, 24, KEY)

    for gx in (x0 + 4, x1 - 4):
        img.putpixel((gx, 8), _c(GOLD_HI))
        img.putpixel((gx, 9), _c(GOLD))
        img.putpixel((gx + 1, 9), _c(_tint(GOLD, -40)))
    img.putpixel((x0 + 4, 18), _c(_tint(accent_hi, -30)))
    img.putpixel((x1 - 4, 18), _c(_tint(accent_hi, -30)))

    for ex in (x0 - 2, x1 + 2):
        _vline(img, ex, 5, 23, CELL_RIM)
        img.putpixel((ex, 4), _c(CELL_RIM_HI))
        img.putpixel((ex, 24), _c(KEY))


def _well_at(img: Image.Image, sx: int, sy: int) -> None:
    """18x18 metal socket with a 16x16 dark recess: neighbouring sockets never merge."""
    if sx < 2 or sy < 2 or sx + WELL_SIZE + 1 >= img.width or sy + WELL_SIZE + 1 >= img.height:
        return
    d = ImageDraw.Draw(img)
    x0, y0 = sx - 1, sy - 1
    x1, y1 = sx + WELL_SIZE, sy + WELL_SIZE
    _rect(d, x0, y0, x1, y1, CELL_RIM)
    _hline(img, x0, x1, y0, _tint(CELL_RIM, -22))
    _vline(img, x0, y0, y1, _tint(CELL_RIM, -22))
    _hline(img, x0 + 1, x1 - 1, y1, CELL_RIM_HI)
    _vline(img, x1, y0 + 1, y1 - 1, CELL_RIM_HI)
    img.putpixel((x1, y1), _c(BEVEL_HI))
    img.putpixel((x1, y0), _c(_tint(CELL_RIM, -12)))
    img.putpixel((x0, y1), _c(_tint(CELL_RIM, -12)))

    for y in range(sy, sy + WELL_SIZE):
        t = (y - sy) / (WELL_SIZE - 1)
        _hline(img, sx, sx + WELL_SIZE - 1, y, _mix(WELL, WELL_DEEP, t))
    _hline(img, sx, sx + WELL_SIZE - 1, sy, WELL_SHADOW)
    _vline(img, sx, sy, sy + WELL_SIZE - 1, WELL_SHADOW)
    _hline(img, sx + 1, sx + WELL_SIZE - 1, sy + 1, _tint(WELL, -12))
    _vline(img, sx + 1, sy + 1, sy + WELL_SIZE - 1, _tint(WELL, -12))
    _hline(img, sx + 1, sx + WELL_SIZE - 1, sy + WELL_SIZE - 1, _tint(WELL_DEEP, 12))
    _vline(img, sx + WELL_SIZE - 1, sy + 1, sy + WELL_SIZE - 2, _tint(WELL_DEEP, 12))
    img.putpixel((sx + WELL_SIZE - 1, sy + WELL_SIZE - 1), _c(_tint(WELL_LIP, -10)))


def _well(img: Image.Image, slot: int) -> None:
    sx, sy = slot_xy(slot)
    _well_at(img, sx, sy)


def _accent_well(img: Image.Image, slot: int, accent_rgb, accent_hi) -> None:
    """A socket that reads as a pressed button: accent socket rim + accent key face."""
    _well(img, slot)
    sx, sy = slot_xy(slot)
    if sx < 2 or sy < 2 or sx + WELL_SIZE + 1 >= img.width or sy + WELL_SIZE + 1 >= img.height:
        return
    x0, y0 = sx - 1, sy - 1
    x1, y1 = sx + WELL_SIZE, sy + WELL_SIZE
    _hline(img, x0, x1, y0, _tint(accent_rgb, -30))
    _vline(img, x0, y0, y1, _tint(accent_rgb, -30))
    _hline(img, x0 + 1, x1 - 1, y1, accent_hi)
    _vline(img, x1, y0 + 1, y1 - 1, accent_hi)
    img.putpixel((x0, y1), accent_rgb)
    img.putpixel((x1, y0), accent_rgb)
    _hline(img, sx + 1, sx + WELL_SIZE - 2, sy + WELL_SIZE - 1, accent_rgb)
    _hline(img, sx + 2, sx + WELL_SIZE - 3, sy + WELL_SIZE - 2, _tint(accent_rgb, 26))
    img.putpixel((sx + 1, sy + WELL_SIZE - 1), accent_hi)
    img.putpixel((sx + WELL_SIZE - 2, sy + WELL_SIZE - 1), _tint(accent_hi, -40))
    _hline(img, sx + 3, sx + WELL_SIZE - 4, sy + 1, _tint(accent_rgb, 40))
    img.putpixel((sx + 2, sy + 1), accent_hi)


def _nav_cells(img: Image.Image, rows: int, accent_rgb, accent_hi) -> None:
    """Last row is the navigation tray: a shadowed seam above it, accent sockets on 45/49/53."""
    if rows < 6:
        return
    row_y = slot_xy((rows - 1) * 9)[1]
    _hline(img, 4, WIDTH - 5, row_y - 4, _tint(BODY_LO, -26))
    _hline(img, 4, WIDTH - 5, row_y - 3, BEVEL_HI)
    _hline(img, 5, WIDTH - 6, row_y - 2, _tint(BODY_LO, -10))
    for slot in NAV_SLOTS:
        _accent_well(img, slot, accent_rgb, accent_hi)


def build_window(
    rows: int = 6,
    accent: str = "orange",
    plaque_px: int = 140,
    slots=None,
    footer: bool = True,
    bracket: bool = True,
) -> Image.Image:
    accent_rgb, accent_hi = ACCENTS.get(accent, ACCENTS["orange"])
    img = Image.new("RGBA", (WIDTH, overlay_height(rows)), _c(KEY))
    _case(img, accent_rgb, accent_hi)

    if slots is None:
        slots = list(range(rows * 9))
    slots = [s for s in slots if 0 <= s < rows * 9]

    if footer:
        _nav_cells(img, rows, accent_rgb, accent_hi)

    for s in slots:
        _well(img, s)

    _plaque(img, accent_rgb, accent_hi, plaque_px)
    return img


ROW = lambda r: [r * 9 + c for c in range(9)]
BLOCK = lambda r0, r1, c0=0, c1=8: [r * 9 + c for r in range(r0, r1 + 1) for c in range(c0, c1 + 1)]


# ---------------------------------------------------------------------------
#  SCENES — slot sets mirror what the Java menus actually fill.
# ---------------------------------------------------------------------------
def build_shop(rows: int = 6, grid: bool = False) -> Image.Image:
    if grid:
        used = [0, 8] + BLOCK(1, 4, 1, 7) + [45, 48, 49, 50, 53]
        return build_window(rows, "orange", slots=used)
    used = [0, 1, 2, 3, 4, 5, 6, 8] + BLOCK(1, 4, 1, 7) + BLOCK(5, 5, 0, 8)
    return build_window(rows, "orange", slots=used)


def build_tags(rows: int = 6) -> Image.Image:
    used = [0, 1, 2, 3, 4, 5, 7, 8] + BLOCK(1, 4, 1, 7) + [45, 48, 49, 50, 53]
    return build_window(rows, "violet", slots=used)


def build_cosmetics(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 3, 0, 8) + [45, 49, 53]
    return build_window(rows, "rose", slots=used)


def build_play(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 2, 1, 7) + [45, 49, 53]
    return build_window(rows, "orange", slots=used)


def build_play_bot(rows: int = 5) -> Image.Image:
    used = [4] + BLOCK(2, 2, 1, 7) + [30, 32, 34, 40]
    return build_window(rows, "ice", slots=used)


def build_hub(rows: int = 6, op: bool = False) -> Image.Image:
    used = [13] + [19, 21, 23, 25] + [29, 31, 33] + [38, 40, 42] + list(range(45, 54))
    if op:
        used = used + [44]
    img = build_window(rows, "orange" if not op else "red", slots=used)
    accent_rgb, accent_hi = ACCENTS["red" if op else "orange"]
    for slot in (19, 21, 23, 25, 29, 31, 33):
        _accent_well(img, slot, accent_rgb, accent_hi)
    return img


def build_season(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(2, 2, 1, 7) + [28, 30, 32] + BLOCK(4, 4, 0, 8) + [46, 49, 53]
    img = build_window(rows, "gold", slots=used)
    for slot in BLOCK(2, 2, 1, 7):
        _accent_well(img, slot, GOLD, GOLD_HI)
    return img


def build_leaderboards(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 3, 1, 7) + [45, 49, 53]
    return build_window(rows, "gold", slots=used)


def build_settings(rows: int = 5) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 1, 7)
    return build_window(rows, "ice", slots=used)


def build_confirm() -> Image.Image:
    used = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 38, 42, 45, 49, 53]
    return build_window(6, "mint", slots=used)


def build_guild(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 3, 0, 8) + [45, 49, 53]
    return build_window(rows, "mint", slots=used)


def build_quests(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 1, 0, 8) + BLOCK(2, 4, 1, 7) + [45, 49, 53]
    return build_window(rows, "ice", slots=used)


def build_achievements(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "gold", slots=used)


def build_mailbox(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 1, 7) + [45, 49, 53]
    return build_window(rows, "rose", slots=used)


def build_history(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 1, 7) + [45, 49, 53]
    return build_window(rows, "violet", slots=used)


def build_friends(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "mint", slots=used)


def build_party(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "ice", slots=used)


def build_ranks(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 3, 1, 7) + [45, 49, 53]
    return build_window(rows, "gold", slots=used)


def build_spectate(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 1, 7) + [45, 49, 53]
    return build_window(rows, "ice", slots=used)


def build_player_card(rows: int = 3) -> Image.Image:
    used = [4, 5, 13, 14, 22, 23] + [0, 8, 45, 49, 53]
    return build_window(rows, "violet", slots=used, bracket=False)


def build_admin(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "red", slots=used)


def build_reports(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "red", slots=used)


def build_warnings(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 3, 1, 7) + [45, 49, 53]
    return build_window(rows, "red", slots=used)


def build_studio(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "violet", slots=used)


# ---------------------------------------------------------------------------
#  REGISTRY — codepoints are frozen: the Java constants point at them.
# ---------------------------------------------------------------------------
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
    0xE34C: ("season", "gui_overlay_season.png", 6),
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
        "season": lambda: build_season(6),
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


# ---------------------------------------------------------------------------
#  VANILLA CONTAINERS — same material, so both halves of a chest screen match.
# ---------------------------------------------------------------------------
CONTAINER_TEXTURES = ("generic_54.png", "hopper.png", "dispenser.png", "shulker_box.png", "inventory.png")

INVENTORY_ROWS = (140, 158, 176)
HOTBAR_ROW = 198
CONTAINER_TOP_PX = 126


def _paint_player_inventory(img: Image.Image, accent_rgb) -> None:
    d = ImageDraw.Draw(img)
    _rect(d, 0, CONTAINER_TOP_PX, 175, 221, KEY)
    _rect(d, 1, CONTAINER_TOP_PX + 1, 174, 220, BODY_KEY)
    _rect(d, 2, CONTAINER_TOP_PX + 2, 173, 219, KEY)
    _body(img, 3, CONTAINER_TOP_PX + 3, 172, 218)
    _hline(img, 3, 172, CONTAINER_TOP_PX + 3, BEVEL_HI)
    _hline(img, 3, 172, CONTAINER_TOP_PX + 4, (240, 245, 252))
    _vline(img, 3, CONTAINER_TOP_PX + 3, 218, BEVEL_HI)
    _hline(img, 4, 171, 218, BEVEL_LO)
    _vline(img, 172, CONTAINER_TOP_PX + 4, 218, BEVEL_LO)
    _hline(img, 2, 173, 220, BEVEL_HI)

    for y in INVENTORY_ROWS:
        for c in range(9):
            _well_at(img, 8 + 18 * c, y)
    for c in range(9):
        _well_at(img, 8 + 18 * c, HOTBAR_ROW)

    _hline(img, 4, 171, CONTAINER_TOP_PX, KEY)
    _hline(img, 4, 171, CONTAINER_TOP_PX + 1, BEVEL_HI)
    for cx, cy in ((6, CONTAINER_TOP_PX + 6), (169, CONTAINER_TOP_PX + 6), (6, 213), (169, 213)):
        _screw(img, cx, cy)
    img.putpixel((2, CONTAINER_TOP_PX + 2), _c(accent_rgb))
    img.putpixel((173, CONTAINER_TOP_PX + 2), _c(accent_rgb))
    img.putpixel((2, 219), _c(accent_rgb))
    img.putpixel((173, 219), _c(accent_rgb))


LIGHT_STOPS = (
    (34, KEY),
    (62, WELL_SHADOW),
    (96, WELL),
    (128, BODY_KEY),
    (168, BODY_LO),
    (206, BODY_MID),
    (232, BODY_HI),
)


def _recolor_pixel(rgb) -> tuple[int, int, int, int]:
    r, g, b = rgb[0], rgb[1], rgb[2]
    if (r, g, b) in {c for _, c in LIGHT_STOPS}:
        return _c((r, g, b))
    if r > 150 and 55 <= g <= 168 and b < 90:
        return _c(ORANGE)
    if r > 170 and g > 140 and b < 120:
        return _c(GOLD)
    lum = (r * 299 + g * 587 + b * 114) // 1000
    previous = LIGHT_STOPS[0]
    for threshold, colour in LIGHT_STOPS:
        if lum <= threshold:
            return _c(colour)
        previous = (threshold, colour)
    return _c(BODY_HI)


def generate_container_textures(cont_dir: Path) -> None:
    """Rewrite the vanilla chrome so the player-inventory half belongs to the same plate."""
    cont_dir.mkdir(parents=True, exist_ok=True)
    for name in CONTAINER_TEXTURES:
        target = cont_dir / name
        if not target.exists():
            continue
        img = Image.open(target).convert("RGBA")
        px = img.load()
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                red, green, blue, _ = _recolor_pixel((r, g, b))
                px[x, y] = (red, green, blue, 255)
        if name == "generic_54.png":
            _paint_player_inventory(img, ORANGE)
        img.save(target)
        print(f"  container {name:18s} rebuilt on the v5 light plate")


# ---------------------------------------------------------------------------
#  EXPORT + CONTRACTS
# ---------------------------------------------------------------------------
def assert_opaque_cover(img: Image.Image) -> None:
    """A container overlay must be fully opaque or the vanilla chest bleeds through."""
    w, h = img.size
    for y in range(h):
        for x in range(w):
            assert img.getpixel((x, y))[3] == 255, f"transparent overlay pixel at {(x, y)}"


def _assert_grid_clean(img: Image.Image) -> None:
    """The gutter under every well must stay plate-coloured (never a semi-transparent pixel)."""
    w, h = img.size
    for slot in range(54):
        sx, sy = slot_xy(slot)
        if sy + WELL_SIZE + 1 >= h or sx + WELL_SIZE >= w:
            continue
        below = img.getpixel((sx + 8, sy + WELL_SIZE + 1))
        assert below[3] == 255, f"gutter under slot {slot} is not opaque: {below}"


def generate_gui_overlays(font_tex_dir: Path) -> list[dict]:
    font_tex_dir.mkdir(parents=True, exist_ok=True)
    providers: list[dict] = []
    for codepoint, (key, filename, rows) in sorted(OVERLAYS.items()):
        img = _pin_width(build_overlay(key))
        expected = overlay_height(rows)
        assert img.height == expected, f"{filename}: {img.height}px for {rows} rows (want {expected})"
        assert img.width == WIDTH, f"{filename}: width {img.width} != {WIDTH}"
        _assert_grid_clean(img)
        assert_opaque_cover(img)
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
