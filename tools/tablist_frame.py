#!/usr/bin/env python3
"""Tab-list background, taken from the `tablist.zip` the owner uploaded.

The zip is a config bundle for a font-image tablist plugin:

    tablist/configs/config.yml            -> namespace "tablist", font_images with
                                             y_position/scale_ratio per rank
    tablist/textures/tablist/tabsponsor.png -> 256x256, the gold frame + 25% black backdrop

Nothing is redrawn: the texture is copied into the pack byte for byte under the zip's own
namespace (`tablist:tablist/tabsponsor`), and the sizes the server needs are cut out of *those*
pixels:

  * the frame band lives on rows 107..205 of the square canvas (99 rows tall, 256 wide);
  * a tab list is not a fixed height, so the band is nine-sliced vertically: the top cap
    (corners + top emblem) and the bottom cap (corners + bottom emblem) are kept 1:1, and only the
    two straight side rails + the flat backdrop between them are stretched. No ornament is ever
    distorted, and the 1px gold rail stays 1px.

Outputs:
  resourcepack/assets/tablist/textures/tablist/tabsponsor.png       (verbatim, the zip's file)
  resourcepack/assets/bosketball/textures/font/tab_frame_r<NN>.png  (one per tab row count)

    python3 tools/tablist_frame.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools/tablist_source/tabsponsor.png"
PACK_NAMESPACE = ROOT / "resourcepack/assets/tablist/textures/tablist"
FONT_DIR = ROOT / "resourcepack/assets/bosketball/textures/font"

BAND_TOP, BAND_BOTTOM = 107, 206      # measured alpha bbox of the frame band
# 5 columns x 3 rows, measured on the band: the corner ornaments and the two crests are kept 1:1,
# only the four straight rail segments are stretched (in x for the top/bottom rails, in y for the
# side rails). That is what lets the same 256x99 frame become any width the tab list needs.
COLUMNS = [(0, 17), (17, 111), (111, 144), (144, 240), (240, 256)]
ROWS = [(0, 22), (22, 77), (77, 99)]
FLEX_COLUMNS = (1, 3)
FLEX_ROW = 1

FRAME_WIDTH = 384
# Round 28 — the frame is now generated for *every* row count instead of four coarse buckets.
#
# A tab list is laid out in 9 px client lines: 1 frame line + the logo block + the counter line,
# then one 9 px line per online player, then the two footer lines. The old buckets (4/10/15/20
# rows -> 200/250/292/332) were computed with a wrong per-row pitch (8.3 px) and a base that was
# ~65 px too tall, so a 1-player tab list showed a huge empty band under the footer.
#
# Measured on a real 2x screenshot of a 1-player tab list (frame rail at rows 10 and 189 of a
# 200 px frame): the last footer line ends at row 119.5, i.e. 69.5 px above the bottom rail.
# Keeping an 8 px lip above the rail gives the exact height of a 1-row frame:
#   119.5 + 8 + 11 (rail + bottom emblem) = 139 px
# and every extra player row adds exactly one 9 px client line.
LINE_HEIGHT = 9
ROWS_1_HEIGHT = 139
TAB_ROWS = range(1, 21)


def height_for(rows: int) -> int:
    """Pixel height of the frame that hugs a tab list with `rows` player rows."""
    return ROWS_1_HEIGHT + LINE_HEIGHT * (max(1, rows) - 1)


def frame_band() -> Image.Image:
    with Image.open(SOURCE) as img:
        return img.convert("RGBA").crop((0, BAND_TOP, img.width, BAND_BOTTOM))


# Round 29 — the backdrop is now opaque.
#
# The owner kept seeing a two-tone panel: the frame's fill was a 25% black veil, so whatever was
# behind the tab list (bright lobby wall on top, dark floor at the bottom) showed through and the
# box looked half-dark, with a bright seam above the top rail where the frame is transparent.
#
# The draw order of the vanilla tab overlay is: header (our frame) -> player rows -> footer, so the
# frame is *behind* the names: the owner's screenshot proves it (the nick stays at its full
# #F1F5F9 while a 25% veil would have pushed it down to ~#B5B8BB). An opaque backdrop therefore
# hides nothing, and the panel finally looks identical whatever the player is looking at.
BACKDROP_TOP = (18, 34, 44)
BACKDROP_BOTTOM = (8, 16, 22)
TOP_RAIL_ROW = 10
BOTTOM_RAIL_MARGIN = 11


def paint_backdrop(img: Image.Image, rail_top: int = TOP_RAIL_ROW,
                   bottom_margin: int = BOTTOM_RAIL_MARGIN) -> Image.Image:
    """Replaces the 25% black veil with an opaque vertical gradient (ornaments are untouched)."""
    w, h = img.size
    rail_bottom = h - bottom_margin
    px = img.load()
    span = max(1, rail_bottom - rail_top - 1)
    for y in range(rail_top + 1, rail_bottom):
        t = (y - rail_top - 1) / span
        t = t * t * (3.0 - 2.0 * t)                      # smoothstep
        col = tuple(round(BACKDROP_TOP[i] + (BACKDROP_BOTTOM[i] - BACKDROP_TOP[i]) * t) for i in range(3))
        for x in range(1, w - 1):
            r, g, b, a = px[x, y]
            if a <= 96 and r < 24 and g < 24 and b < 24:   # the veil pixel only
                px[x, y] = (col[0], col[1], col[2], 255)
    # 1 px bevel: a light line right under the top rail, a shadow line right above the bottom rail
    for x in range(1, w - 1):
        r, g, b, a = px[x, rail_top + 1]
        if a <= 96 and r < 24:
            px[x, rail_top + 1] = (38, 62, 76, 255)
        r, g, b, a = px[x, rail_bottom - 1]
        if a <= 255 and r < 24 and g < 24 and b < 40 and a > 0:
            px[x, rail_bottom - 1] = (4, 9, 12, 255)
    return img


def build(width: int = FRAME_WIDTH, height: int = ROWS_1_HEIGHT) -> Image.Image:
    """Nine-slice the band to (width, height) without ever touching an ornament."""
    band = frame_band()
    fixed_w = sum(c[1] - c[0] for i, c in enumerate(COLUMNS) if i not in FLEX_COLUMNS)
    flex_w = width - fixed_w
    col_w = {}
    share = flex_w // len(FLEX_COLUMNS)
    for i in FLEX_COLUMNS[:-1]:
        col_w[i] = share
    col_w[FLEX_COLUMNS[-1]] = flex_w - share * (len(FLEX_COLUMNS) - 1)

    fixed_h = sum(r[1] - r[0] for i, r in enumerate(ROWS) if i != FLEX_ROW)
    mid_h = max(1, height - fixed_h)

    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    y_out = 0
    for r_index, (y0, y1) in enumerate(ROWS):
        row_h = mid_h if r_index == FLEX_ROW else y1 - y0
        x_out = 0
        for c_index, (x0, x1) in enumerate(COLUMNS):
            cell = band.crop((x0, y0, x1, y1))
            target_w = col_w[c_index] if c_index in FLEX_COLUMNS else x1 - x0
            if cell.size != (target_w, row_h):
                cell = cell.resize((max(1, target_w), max(1, row_h)), Image.NEAREST)
            out.alpha_composite(cell, (x_out, y_out))
            x_out += max(1, target_w)
        y_out += max(1, row_h)
    return out


def write() -> list[tuple[Path, tuple[int, int]]]:
    import shutil

    PACK_NAMESPACE.mkdir(parents=True, exist_ok=True)
    shipped = PACK_NAMESPACE / "tabsponsor.png"
    shutil.copyfile(SOURCE, shipped)          # the zip's bytes, untouched

    FONT_DIR.mkdir(parents=True, exist_ok=True)
    for stale in FONT_DIR.glob("tab_frame_*.png"):
        stale.unlink()
    written: list[tuple[Path, tuple[int, int]]] = [(shipped, (256, 256))]
    for rows in TAB_ROWS:
        img = paint_backdrop(build(FRAME_WIDTH, height_for(rows)))
        path = FONT_DIR / f"tab_frame_r{rows:02d}.png"
        img.save(path)
        written.append((path, img.size))
    return written


if __name__ == "__main__":
    for path, size in write():
        print(f"tab frame  {path.relative_to(ROOT)!s:64s} {size[0]}x{size[1]}")
