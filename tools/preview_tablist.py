#!/usr/bin/env python3
"""Render the tab list backdrop so it can be reviewed without launching the game.

The frame comes from the owner's `tablist.zip` (nine-sliced by `tools/tablist_frame.py`); the rows
below are a mock of what the plugin writes: one glyph line for the frame, then the header block,
the player rows and the footer, all centred like the client centres them.

    python3 tools/preview_tablist.py        # docs/previews/tablist.png
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "resourcepack/assets/bosketball/textures/font"
OUT = ROOT / "docs/previews/tablist.png"
SCALE = 2
ROWS = 4
# Round 28: the frame is generated per player count, so the preview uses the real 4-row one.
FRAME = FONT_DIR / "tab_frame_r04.png"
NAMES = [
    ("MVP", "Akashhi_", "2410"),
    ("LEGEND", "MaxLananas", "1980"),
    ("SUPERSTAR", "Nico_Ball", "1740"),
    ("SUPERSTAR", "KwameJumps", "1690"),
    ("ALL-STAR", "ZoeTrey", "1510"),
    ("ALL-STAR", "MikaDunk", "1470"),
    ("PRO", "SamPick", "1290"),
    ("PRO", "TaoScreen", "1255"),
    ("HOOPER", "LeaFast", "1105"),
    ("ROOKIE", "NewGuy", "980"),
]


def main() -> int:
    frame = Image.open(FRAME).convert("RGBA")
    w, h = frame.size
    canvas = Image.new("RGBA", (w, h), (24, 26, 32, 255))
    d = ImageDraw.Draw(canvas)
    try:
        font = ImageFont.load_default()
    except Exception:
        font = None

    def centered(text: str, y: int, fill=(236, 240, 250)) -> None:
        if font is None:
            return
        tw = d.textlength(text, font=font)
        d.text(((w - tw) / 2, y), text, fill=fill, font=font)

    # the client draws the header first: frame line, logo block, then the live counters
    y = 10
    centered("B O S K E T B A L L", y, (255, 226, 97))
    y += 12
    centered("- - - - - - - - - - - -", y, (120, 132, 158))
    y += 12
    centered("ONLINE: 10   IN MATCH: 0   QUEUE: 1", y, (200, 214, 235))
    y += 16

    for tag, name, elo in NAMES[:ROWS]:
        left = tag.rjust(9)
        centered(f"{left}  {name}", y, (240, 244, 252))
        if font is not None:
            d.text((w - 44, y), elo, fill=(251, 191, 36), font=font)
        y += 9

    y += 6
    centered("SEASON 4   -   BOSKETBALL.GG   -   /SEASON", y, (150, 160, 190))

    # the client paints the header (and therefore our frame) AFTER the rows: composite it last so the
    # preview shows the same 25% backdrop over the names that players see
    canvas.alpha_composite(frame, (0, 0))

    sheet = Image.new("RGBA", (w * SCALE + 24, h * SCALE + 24), (14, 15, 20, 255))
    sheet.alpha_composite(canvas.resize((w * SCALE, h * SCALE), Image.NEAREST), (12, 12))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("tablist ->", OUT.relative_to(ROOT), sheet.size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
