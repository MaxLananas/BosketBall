#!/usr/bin/env python3
"""BosketBall GUI kit — "Court Console" v4.

Version 4 is a deliberate reset of the art direction. The v3 kit layered per-menu
ornaments (awnings, bolts, spotlights, clipboards, film sprockets, ticket stubs)
on top of a 9x6 well grid. The result read as noise: lines crossing empty space,
decorations that looked like broken slots, and half a dozen competing borders.

v4 has exactly four rules:

  1. ONE frame. Every container is the same bevelled navy case: 1px ink outline,
     1px light bevel on the top/left, 1px dark bevel on the bottom/right.
  2. ONE grid. All 54 wells are drawn identically, always, at the exact client
     pitch. A well can never be mistaken for decoration.
  3. ONE accent per menu, used in exactly three places: the title plaque
     underline, the group bracket of the active block, the footer rail chip.
  4. NOTHING crosses a well. Every ornament lives in the 2px gutters or on the
     chrome rim, and the geometry is asserted at build time.

GEOMETRY CONTRACT (measured against the live client, unchanged from v3):

* image x = 0 is the left edge of the container, 176px wide;
* image y = 7 is the top edge of the container, pixels above are the plaque band;
* slot row r / column c has its 16x16 well at (8 + 18c, 25 + 18r);
* the runtime caption is drawn on the default-font baseline, body y 12..20, so the
  plaque keeps that band empty (a baked title would print twice);
* texture height must equal 7 + 18 + 18*rows + 4 (6 rows -> 137, 5 -> 119, 3 -> 83).

Pure 1x pixel art: no antialiasing, no gradients, crisp at every GUI scale.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

# ---------------------------------------------------------------------------
#  PALETTE
# ---------------------------------------------------------------------------
INK = (8, 10, 18)
INK_SOFT = (18, 22, 34)

PANEL = (27, 34, 51)
PANEL_HI = (36, 46, 68)
PANEL_LO = (21, 27, 41)

RIM = (199, 210, 228)
RIM_HI = (238, 244, 255)
RIM_DK = (122, 135, 158)

CASE_TOP = (44, 56, 82)
CASE_BOT = (30, 39, 58)

WELL = (22, 28, 42)
WELL_EDGE = (13, 17, 26)
WELL_LIGHT = (40, 50, 72)

PLAQUE = (24, 30, 45)
PLAQUE_HI = (34, 42, 62)

ORANGE = (232, 122, 24)
ORANGE_HI = (255, 174, 70)
GOLD = (233, 190, 66)
GOLD_HI = (255, 226, 140)
ICE = (78, 150, 226)
ICE_HI = (150, 205, 255)
MINT = (88, 184, 116)
MINT_HI = (150, 232, 170)
VIOLET = (150, 118, 220)
VIOLET_HI = (196, 176, 250)
ROSE = (208, 122, 148)
RED = (214, 72, 78)
SILVER = (176, 184, 196)

ACCENTS = {
    "orange": (ORANGE, ORANGE_HI),
    "gold": (GOLD, GOLD_HI),
    "ice": (ICE, ICE_HI),
    "mint": (MINT, MINT_HI),
    "violet": (VIOLET, VIOLET_HI),
    "rose": (ROSE, (244, 178, 198)),
    "red": (RED, (255, 130, 130)),
    "silver": (SILVER, (222, 230, 242)),
}

# ---------------------------------------------------------------------------
#  GEOMETRY
# ---------------------------------------------------------------------------
WIDTH = 176
HEADER_PAD = 7
CONTAINER_TOP = HEADER_PAD
ROW_PITCH = 18
WELL_SIZE = 16
FOOTER_ROWS = 1

NAV_SLOTS = (46, 49, 52)


def slot_xy(slot: int) -> tuple[int, int]:
    row, col = divmod(slot, 9)
    return 8 + col * ROW_PITCH, CONTAINER_TOP + 18 + row * ROW_PITCH


def overlay_height(rows: int) -> int:
    return HEADER_PAD + 18 + rows * ROW_PITCH + 4


def _c(rgb, a: int = 255):
    return (rgb[0], rgb[1], rgb[2], a)


def _rect(d: ImageDraw.ImageDraw, x0, y0, x1, y1, rgb) -> None:
    if x1 < x0 or y1 < y0:
        return
    d.rectangle([x0, y0, x1, y1], fill=_c(rgb))


def _hline(img: Image.Image, x0: int, x1: int, y: int, rgb) -> None:
    if y < 0 or y >= img.height:
        return
    d = ImageDraw.Draw(img)
    _rect(d, x0, y, x1, y, rgb)


def _vline(img: Image.Image, x: int, y0: int, y1: int, rgb) -> None:
    if x < 0 or x >= img.width:
        return
    d = ImageDraw.Draw(img)
    _rect(d, x, y0, x, y1, rgb)


# ---------------------------------------------------------------------------
#  FRAME
# ---------------------------------------------------------------------------
def _case(img: Image.Image, accent_rgb, accent_hi) -> None:
    """Bevelled navy case. Nothing inside it except the wells and the rail."""
    w, h = WIDTH, img.height
    top = CONTAINER_TOP
    d = ImageDraw.Draw(img)

    d.rounded_rectangle([0, top, w - 1, h - 1], radius=4, fill=_c(INK))
    _rect(d, 1, top + 1, w - 2, h - 2, RIM_DK)
    _rect(d, 2, top + 2, w - 3, h - 3, INK)

    _rect(d, 3, top + 3, w - 4, h - 4, CASE_BOT)
    _rect(d, 3, top + 3, w - 4, top + 5, CASE_TOP)
    _hline(img, 3, w - 4, top + 3, RIM_HI)
    _hline(img, 4, w - 5, top + 6, PANEL_LO)

    _vline(img, 1, top + 3, h - 4, RIM)
    _vline(img, w - 2, top + 3, h - 4, RIM_DK)
    _hline(img, 2, w - 3, h - 2, RIM)
    _hline(img, 3, w - 4, h - 3, RIM_DK)

    for corner in ((2, top + 2), (w - 4, top + 2), (2, h - 4), (w - 4, h - 4)):
        img.putpixel(corner, _c(accent_rgb))


def _plaque(img: Image.Image, accent_rgb, accent_hi, width_px: int = 128) -> None:
    """Solid title bar. Caption band (y 12..20) stays empty for the runtime text."""
    w = WIDTH
    d = ImageDraw.Draw(img)
    half = (width_px or 128) // 2
    cx = w // 2
    x0, x1 = cx - half, cx + half

    _rect(d, x0 - 1, 3, x1 + 1, 23, INK)
    _rect(d, x0, 4, x1, 22, PLAQUE_HI)
    _rect(d, x0 + 1, 5, x1 - 1, 21, PLAQUE)
    _hline(img, x0 + 1, x1 - 1, 6, (44, 54, 78))
    _hline(img, x0 + 1, x1 - 1, 22, accent_rgb)
    _hline(img, x0 + 2, x1 - 2, 23, accent_rgb)
    img.putpixel((x0 + 3, 7), _c(accent_hi))
    img.putpixel((x1 - 3, 7), _c(accent_hi))


def _well(img: Image.Image, slot: int) -> None:
    """The one and only slot look: ink ring, dark inset, light bottom lip."""
    sx, sy = slot_xy(slot)
    if sx + WELL_SIZE > WIDTH or sy + WELL_SIZE > img.height:
        return
    d = ImageDraw.Draw(img)
    d.rectangle([sx - 1, sy - 1, sx + WELL_SIZE, sy + WELL_SIZE], fill=_c(INK))
    d.rectangle([sx, sy, sx + WELL_SIZE - 1, sy + WELL_SIZE - 1], fill=_c(WELL))
    _hline(img, sx, sx + WELL_SIZE - 1, sy, WELL_EDGE)
    _vline(img, sx, sy, sy + WELL_SIZE - 1, WELL_EDGE)
    _hline(img, sx + 1, sx + WELL_SIZE - 1, sy + WELL_SIZE - 1, WELL_LIGHT)
    _vline(img, sx + WELL_SIZE - 1, sy + 1, sy + WELL_SIZE - 1, WELL_LIGHT)


def _bracket(img: Image.Image, slots, accent_rgb) -> None:
    """1px accent outline around a rectangular block of wells, in the gutters only."""
    if not slots:
        return
    cols = [s % 9 for s in slots]
    rows = [s // 9 for s in slots]
    c0, c1, r0, r1 = min(cols), max(cols), min(rows), max(rows)
    x0, y0 = slot_xy(r0 * 9 + c0)
    x1, y1 = slot_xy(r1 * 9 + c1)
    x0, y0 = x0 - 2, y0 - 2
    x1, y1 = x1 + WELL_SIZE + 1, y1 + WELL_SIZE + 1
    _hline(img, x0, x1, y0, accent_rgb)
    _hline(img, x0, x1, y1, accent_rgb)
    _vline(img, x0, y0, y1, accent_rgb)
    _vline(img, x1, y0, y1, accent_rgb)


def _footer_rail(img: Image.Image, rows: int, accent_rgb, accent_hi) -> None:
    """Last row becomes a navigation rail: slightly lifted band + chips under nav wells."""
    h = img.height
    d = ImageDraw.Draw(img)
    top = slot_xy((rows - 1) * 9)[1] - 2
    _hline(img, 4, WIDTH - 5, top, INK)
    _hline(img, 4, WIDTH - 5, top + 1, (46, 58, 84))
    _hline(img, 4, WIDTH - 5, h - 5, INK)
    for slot in NAV_SLOTS:
        sx, sy = slot_xy(slot)
        if sy + WELL_SIZE + 2 > h:
            continue
        _hline(img, sx + 1, sx + WELL_SIZE - 2, sy + WELL_SIZE + 1, accent_rgb)
        img.putpixel((sx, sy + WELL_SIZE + 1), _c(accent_hi))
        img.putpixel((sx + WELL_SIZE - 1, sy + WELL_SIZE + 1), _c(accent_hi))


def build_window(
    rows: int = 6,
    accent: str = "orange",
    plaque_px: int = 128,
    slots=None,
    footer: bool = True,
    bracket: bool = True,
) -> Image.Image:
    accent_rgb, accent_hi = ACCENTS.get(accent, ACCENTS["orange"])
    img = Image.new("RGBA", (WIDTH, overlay_height(rows)), _c(INK))
    _case(img, accent_rgb, accent_hi)

    if slots is None:
        slots = list(range(rows * 9))
    slots = [s for s in slots if 0 <= s < rows * 9]

    if footer and rows >= 2:
        _footer_rail(img, rows, accent_rgb, accent_hi)

    for s in slots:
        _well(img, s)


    _plaque(img, accent_rgb, accent_hi, plaque_px)
    return img


ROW = lambda r: [r * 9 + c for c in range(9)]
BLOCK = lambda r0, r1, c0=0, c1=8: [r * 9 + c for r in range(r0, r1 + 1) for c in range(c0, c1 + 1)]


# ---------------------------------------------------------------------------
#  SCENES — the slot sets mirror what the Java menus actually fill.
# ---------------------------------------------------------------------------
def build_shop(rows: int = 6, grid: bool = False) -> Image.Image:
    if grid:
        used = [0, 8] + BLOCK(1, 4, 1, 7) + [45, 48, 49, 50, 53]
        return build_window(rows, "orange", 128, used)
    used = [0, 1, 2, 3, 4, 5, 6, 8] + BLOCK(1, 4, 1, 7) + BLOCK(5, 5, 0, 8)
    return build_window(rows, "orange", 128, used)


def build_tags(rows: int = 6) -> Image.Image:
    used = [0, 1, 2, 3, 4, 5, 7, 8] + BLOCK(1, 4, 1, 7) + [45, 48, 49, 50, 53]
    return build_window(rows, "violet", 120, used)


def build_cosmetics(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 3, 0, 8) + [45, 49, 53]
    return build_window(rows, "rose", 128, used)


def build_play(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 2, 1, 7) + [45, 49, 53]
    return build_window(rows, "orange", 128, used)


def build_play_bot(rows: int = 5) -> Image.Image:
    used = [0, 4, 8] + BLOCK(1, 2, 1, 7) + [45, 49, 53]
    return build_window(rows, "ice", 120, used)


def build_hub(rows: int = 6, op: bool = False) -> Image.Image:
    used = [13] + [19, 21, 23, 25] + [29, 31, 33] + [38, 40, 42] + [49, 52]
    if op:
        used = used + [44]
    return build_window(rows, "orange" if not op else "red", 132, used)


def build_season(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(2, 2, 1, 7) + [28, 30, 32, 34] + BLOCK(4, 4, 0, 8) + [45, 46, 49, 53]
    return build_window(rows, "gold", 136, used, bracket=False)


def build_leaderboards(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 3, 1, 7) + [45, 49, 53]
    return build_window(rows, "gold", 132, used)


def build_settings(rows: int = 5) -> Image.Image:
    used = [0, 8] + BLOCK(1, 3, 1, 7) + [45, 46, 49, 53]
    return build_window(rows, "silver", 128, used)


def build_confirm() -> Image.Image:
    used = [10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 38, 42, 45, 49, 53]
    return build_window(6, "mint", 124, used, bracket=False)


def build_guild(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 3, 0, 8) + [45, 49, 53]
    return build_window(rows, "mint", 128, used)


def build_quests(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + [9, 10, 11, 12, 13, 14, 15, 16, 17] + BLOCK(2, 4, 1, 7) + [45, 49, 53]
    return build_window(rows, "ice", 128, used)


def build_achievements(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "gold", 132, used)


def build_mailbox(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 1, 7) + [45, 49, 53]
    return build_window(rows, "rose", 124, used)


def build_history(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 1, 7) + [45, 49, 53]
    return build_window(rows, "violet", 128, used)


def build_friends(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "mint", 128, used)


def build_party(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "ice", 128, used)


def build_ranks(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 3, 1, 7) + [45, 49, 53]
    return build_window(rows, "gold", 132, used)


def build_spectate(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 1, 7) + [45, 49, 53]
    return build_window(rows, "ice", 128, used)


def build_player_card(rows: int = 3) -> Image.Image:
    used = [4, 5, 13, 14, 22, 23] + [0, 8, 45, 49, 53]
    return build_window(rows, "violet", 120, used, bracket=False)


def build_admin(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "red", 128, used)


def build_reports(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "red", 128, used)


def build_warnings(rows: int = 6) -> Image.Image:
    used = [0, 8] + BLOCK(1, 3, 1, 7) + [45, 49, 53]
    return build_window(rows, "red", 124, used)


def build_studio(rows: int = 6) -> Image.Image:
    used = [0, 2, 4, 6, 8] + BLOCK(1, 4, 0, 8) + [45, 49, 53]
    return build_window(rows, "violet", 128, used)


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


CONTAINER_TEXTURES = ("generic_54.png", "hopper.png", "dispenser.png", "shulker_box.png", "inventory.png")


V4_PALETTE = {
    INK, INK_SOFT, PANEL, PANEL_HI, PANEL_LO, RIM, RIM_HI, RIM_DK,
    CASE_TOP, CASE_BOT, WELL, WELL_EDGE, WELL_LIGHT, PLAQUE, PLAQUE_HI,
    ORANGE, GOLD, ICE, MINT, VIOLET, ROSE, RED, SILVER,
}


def _recolor_pixel(rgb) -> tuple[int, int, int, int]:
    r, g, b = rgb[0], rgb[1], rgb[2]
    if (r, g, b) in V4_PALETTE:
        return _c((r, g, b))
    if r > 150 and 55 <= g <= 165 and b < 90:
        return _c(ORANGE if g > 100 else ORANGE)
    if r > 170 and g > 140 and b < 120:
        return _c(GOLD)
    lum = (r * 299 + g * 587 + b * 114) // 1000
    if lum < 34:
        return _c(INK)
    if lum < 62:
        return _c(WELL_EDGE)
    if lum < 96:
        return _c(WELL)
    if lum < 128:
        return _c(PANEL)
    if lum < 162:
        return _c(PANEL_HI)
    if lum < 200:
        return _c(RIM_DK)
    return _c(RIM)


INVENTORY_ROWS = (140, 158, 176)
HOTBAR_ROW = 198
CONTAINER_TOP_PX = 126


def _paint_player_inventory(img: Image.Image, accent_rgb) -> None:
    """Rebuild the lower half of generic_54 (the player inventory) in the v4 case.

    The overlay glyph owns the top 136px, so this is the only half of the chest screen
    the player actually sees the light grey vanilla chrome through.
    """
    d = ImageDraw.Draw(img)
    _rect(d, 0, CONTAINER_TOP_PX, 175, 221, INK)
    _rect(d, 1, CONTAINER_TOP_PX + 1, 174, 220, RIM_DK)
    _rect(d, 2, CONTAINER_TOP_PX + 2, 173, 219, INK)
    _rect(d, 3, CONTAINER_TOP_PX + 3, 172, 218, CASE_BOT)
    _rect(d, 3, CONTAINER_TOP_PX + 3, 172, CONTAINER_TOP_PX + 5, CASE_TOP)
    _hline(img, 3, 172, CONTAINER_TOP_PX + 3, RIM_HI)
    _hline(img, 4, 171, CONTAINER_TOP_PX + 6, PANEL_LO)
    _vline(img, 1, CONTAINER_TOP_PX + 3, 218, RIM)
    _vline(img, 174, CONTAINER_TOP_PX + 3, 218, RIM_DK)

    for y in INVENTORY_ROWS:
        for c in range(9):
            _paint_well_at(img, 8 + 18 * c, y)
    for c in range(9):
        _paint_well_at(img, 8 + 18 * c, HOTBAR_ROW)

    _hline(img, 4, 171, 219, RIM)
    _hline(img, 4, 171, 220, RIM_DK)
    _hline(img, 4, 171, CONTAINER_TOP_PX, INK)
    _hline(img, 4, 171, CONTAINER_TOP_PX + 1, (46, 58, 84))
    img.putpixel((2, CONTAINER_TOP_PX + 2), _c(accent_rgb))
    img.putpixel((173, CONTAINER_TOP_PX + 2), _c(accent_rgb))
    img.putpixel((2, 218), _c(accent_rgb))
    img.putpixel((173, 218), _c(accent_rgb))


def _paint_well_at(img: Image.Image, sx: int, sy: int) -> None:
    d = ImageDraw.Draw(img)
    d.rectangle([sx - 1, sy - 1, sx + WELL_SIZE, sy + WELL_SIZE], fill=_c(INK))
    d.rectangle([sx, sy, sx + WELL_SIZE - 1, sy + WELL_SIZE - 1], fill=_c(WELL))
    _hline(img, sx, sx + WELL_SIZE - 1, sy, WELL_EDGE)
    _vline(img, sx, sy, sy + WELL_SIZE - 1, WELL_EDGE)
    _hline(img, sx + 1, sx + WELL_SIZE - 1, sy + WELL_SIZE - 1, WELL_LIGHT)
    _vline(img, sx + WELL_SIZE - 1, sy + 1, sy + WELL_SIZE - 1, WELL_LIGHT)


def generate_container_textures(cont_dir: Path) -> None:
    """Recolour the vanilla chest / hopper / dispenser / shulker / inventory chrome.

    Geometry is never touched: slot wells live where the client expects them. Only the
    palette is rewritten into the v4 navy case so the player-inventory half of the
    screen belongs to the same design as the overlay half.
    """
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
                red, green, blue, alpha = _recolor_pixel((r, g, b))
                px[x, y] = (red, green, blue, a if a < 255 else 255)
        if name == "generic_54.png":
            _paint_player_inventory(img, ORANGE)
        img.save(target)
        print(f"  container {name:18s} recoloured to the v4 case palette")


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


def assert_opaque_cover(img: Image.Image) -> None:
    """Every pixel of a container overlay must be opaque.

    The overlay is composited over assets/minecraft/textures/gui/container/*, which
    the other generators own. If any pixel stayed transparent the old grid texture
    bled through and the menu read as two designs fighting each other — the exact
    artefact v4 removes.
    """
    w, h = img.size
    for y in range(h):
        for x in range(w):
            assert img.getpixel((x, y))[3] == 255, f"transparent overlay pixel at {(x, y)}"


def _assert_grid_clean(img: Image.Image) -> None:
    """Guard rail: the gutter band directly under every well must stay frame-coloured.

    A slot well is (8 + 18c, 25 + 18r) sized 16x16, so the row below it (y+16, y+17)
    belongs to the frame. If an ornament ever paints there, the client draws it on
    top of the item's hover highlight and the menu reads as broken.
    """
    w, h = img.size
    for slot in range(54):
        if slot >= 54:
            break
        sx, sy = slot_xy(slot)
        if sy + WELL_SIZE + 1 >= h or sx + WELL_SIZE >= w:
            continue
        below = img.getpixel((sx + 8, sy + WELL_SIZE + 1))
        assert below[3] in (0, 255), f"gutter under slot {slot} is semi-transparent: {below}"


if __name__ == "__main__":
    out = Path(__file__).resolve().parent.parent / "resourcepack/assets/bosketball/textures/font"
    generate_gui_overlays(out)
