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
  resourcepack/assets/tablist/textures/tablist/tabsponsor.png   (verbatim, the zip's file)
  resourcepack/assets/bosketball/textures/font/tab_frame_<h>.png (nine-sliced sizes)

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
CAP_TOP = 22                          # top cap: corners + top emblem, kept 1:1
CAP_BOTTOM = 22                       # bottom cap: corners + bottom emblem, kept 1:1

# One size per row bucket: header (9 lines) + rows + footer + a 4px lip.
SIZES = {4: 150, 10: 208, 15: 240, 20: 292}


def frame_band() -> Image.Image:
    with Image.open(SOURCE) as img:
        return img.convert("RGBA").crop((0, BAND_TOP, img.width, BAND_BOTTOM))


def nine_slice(height: int) -> Image.Image:
    """Top cap 1:1, bottom cap 1:1, side rails + backdrop stretched in between."""
    band = frame_band()
    w = band.width
    top = band.crop((0, 0, w, CAP_TOP))
    bottom = band.crop((0, band.height - CAP_BOTTOM, w, band.height))
    middle_src = band.crop((0, CAP_TOP, w, band.height - CAP_BOTTOM))
    middle_h = max(1, height - CAP_TOP - CAP_BOTTOM)
    middle = middle_src.resize((w, middle_h), Image.NEAREST)
    out = Image.new("RGBA", (w, height), (0, 0, 0, 0))
    out.alpha_composite(top, (0, 0))
    out.alpha_composite(middle, (0, CAP_TOP))
    out.alpha_composite(bottom, (0, height - CAP_BOTTOM))
    return out


def write() -> list[tuple[Path, tuple[int, int]]]:
    import shutil

    PACK_NAMESPACE.mkdir(parents=True, exist_ok=True)
    shipped = PACK_NAMESPACE / "tabsponsor.png"
    shutil.copyfile(SOURCE, shipped)          # the zip's bytes, untouched

    FONT_DIR.mkdir(parents=True, exist_ok=True)
    written: list[tuple[Path, tuple[int, int]]] = [(shipped, (256, 256))]
    for height in SIZES.values():
        img = nine_slice(height)
        path = FONT_DIR / f"tab_frame_{height}.png"
        img.save(path)
        written.append((path, img.size))
    return written


if __name__ == "__main__":
    for path, size in write():
        print(f"tab frame  {path.relative_to(ROOT)!s:64s} {size[0]}x{size[1]}")
