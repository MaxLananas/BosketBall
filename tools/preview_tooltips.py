#!/usr/bin/env python3
"""Render the shipped tooltips so they can be reviewed without launching the game.

Minecraft 1.21.2 draws tooltips from GUI sprites, and an item can pick its own style with the
`minecraft:tooltip_style` component: style `<ns>:<id>` is drawn with
`assets/<ns>/textures/gui/sprites/tooltip/<id>_background.png` and `<id>_frame.png`.
Both are nine-slice sprites (`gui.scaling` in their `.mcmeta`, border 30, stretch_inner), and the
legendary/artifact frames are also animated (a vertical strip of 100x100 frames).

This tool rebuilds that pipeline in Pillow: nine-slice both layers to a tooltip-sized box, overlay
the rarity ribbon glyph from the font folder, and stack them on one sheet.

    python3 tools/preview_tooltips.py        # docs/previews/tooltips.png
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SPRITES = ROOT / "resourcepack/assets/minecraft/textures/gui/sprites/tooltip"
RIBBONS = ROOT / "resourcepack/assets/bosketball/textures/font/tooltips"
OUT = ROOT / "docs/previews/tooltips.png"

SCALE = 2
BOX = (150, 74)          # tooltip size in GUI pixels, before SCALE
STYLES = [
    ("default", "background", "frame"),
    ("uncommon", "uncommon_background", "uncommon_frame"),
    ("rare", "rare_background", "rare_frame"),
    ("epic", "epic_background", "epic_frame"),
    ("legendary", "legendary_background", "legendary_frame"),
    ("artifact", "artifact_background", "artifact_frame"),
]


def nine_slice(path: Path, size: tuple[int, int], frame_index: int = 0) -> Image.Image:
    """Stretch a nine-slice sprite whose .mcmeta declares border=30, stretch_inner."""
    meta = json.loads(path.with_suffix(".png.mcmeta").read_text(encoding="utf-8"))
    scaling = meta["gui"]["scaling"]
    src = Image.open(path).convert("RGBA")
    border = scaling["border"] if isinstance(scaling["border"], int) else 10
    tile_h = scaling.get("height", src.height)
    if frame_index:
        src = src.crop((0, frame_index * tile_h, src.width, (frame_index + 1) * tile_h))
    w, h = size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    b = border
    # corners
    corners = [(0, 0, 0, 0), (w - b, 0, 1, 0), (0, h - b, 0, 1), (w - b, h - b, 1, 1)]
    for dx, dy, cx, cy in corners:
        piece = src.crop((cx * (src.width - b), cy * (src.height - b),
                          cx * (src.width - b) + b, cy * (src.height - b) + b))
        out.alpha_composite(piece, (dx, dy))
    # edges
    top = src.crop((b, 0, src.width - b, b))
    out.alpha_composite(top.resize((w - 2 * b, b), Image.NEAREST), (b, 0))
    bottom = src.crop((b, src.height - b, src.width - b, src.height))
    out.alpha_composite(bottom.resize((w - 2 * b, b), Image.NEAREST), (b, h - b))
    left = src.crop((0, b, b, src.height - b))
    out.alpha_composite(left.resize((b, h - 2 * b), Image.NEAREST), (0, b))
    right = src.crop((src.width - b, b, src.width, src.height - b))
    out.alpha_composite(right.resize((b, h - 2 * b), Image.NEAREST), (w - b, b))
    # centre
    centre = src.crop((b, b, src.width - b, src.height - b))
    out.alpha_composite(centre.resize((w - 2 * b, h - 2 * b), Image.NEAREST), (b, b))
    return out


def build_tile(label: str, background: str, frame: str) -> Image.Image:
    w, h = BOX
    tile = Image.new("RGBA", (w, h), (14, 16, 20, 255))
    bg = nine_slice(SPRITES / f"{background}.png", (w, h))
    tile.alpha_composite(bg, (0, 0))
    ribbon_path = RIBBONS / f"{label}.png"
    draw = ImageDraw.Draw(tile)
    if ribbon_path.is_file():
        ribbon = Image.open(ribbon_path).convert("RGBA")
        tile.alpha_composite(ribbon, (8, 7))
        text_top = 7 + ribbon.height + 5
    else:
        text_top = 10
    # placeholder lines: real tooltips hold text, this only shows the frame/background colours
    for i in range(3):
        y = text_top + i * 11
        if y + 5 > h - 8:
            break
        draw.rounded_rectangle([8, y, 8 + (86 - i * 18), y + 4], radius=1, fill=(214, 222, 236, 90))
    frame_img = nine_slice(SPRITES / f"{frame}.png", (w, h), frame_index=1 if "legendary" in frame or "artifact" in frame else 0)
    tile.alpha_composite(frame_img, (0, 0))
    return tile


def main() -> int:
    pad = 14
    tiles = [(label, build_tile(label, bg, fr)) for label, bg, fr in STYLES]
    tw, th = BOX[0] * SCALE, BOX[1] * SCALE
    sheet = Image.new("RGBA", (len(tiles) * (tw + pad) + pad, th + 2 * pad + 14), (16, 18, 24, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (label, tile) in enumerate(tiles):
        x = pad + i * (tw + pad)
        sheet.paste(tile.resize((tw, th), Image.NEAREST), (x, pad), tile.resize((tw, th), Image.NEAREST))
        draw.text((x, pad + th + 3), label, fill=(200, 214, 235))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("tooltips ->", OUT.relative_to(ROOT), sheet.size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
