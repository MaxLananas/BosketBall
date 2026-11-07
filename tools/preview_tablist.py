#!/usr/bin/env python3
"""Preview of the in-game tab list, drawn from the same geometry the plugin uses.

Round 28: the frame is generated per player count (139 px for one row, +9 px per row). This preview
lays the header, the rows and the footer on the client's 9 px line grid so the picture shows exactly
how tight the gold frame is around the content:

    line 1        frame glyph (nothing else)
    lines 2..6    BOSKETBALL logo strip
    line 8        ONLINE / IN MATCH / QUEUE counters
    line 10 + i   player row i (one 9 px line each)
    then          footer: rank line + season/site line

    python3 tools/preview_tablist.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "resourcepack/assets/bosketball/textures/font"
OUT = ROOT / "docs/previews/tablist.png"
SCALE = 2
ROWS = 10
LINE = 9
FRAME = FONT_DIR / f"tab_frame_r{ROWS:02d}.png"
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

# content anchors, in client pixels, relative to the top of the frame glyph
LOGO_LINE = 2          # first logo strip line
COUNTERS_LINE = 7      # 0-based line index -> 8th line
FIRST_ROW_LINE = 9     # 0-based -> 10th line (measured on the owner's screenshot)


def main() -> int:
    frame = Image.open(FRAME).convert("RGBA")
    w, h = frame.size
    canvas = Image.new("RGBA", (w * SCALE, h * SCALE), (24, 26, 32, 255))
    canvas = canvas.resize((w, h), Image.NEAREST)
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

    def line_top(index: int) -> int:
        return LINE * index + 1

    centered("B O S K E T B A L L", line_top(LOGO_LINE), (255, 226, 97))
    centered("- - - - - - - - - - - -", line_top(LOGO_LINE + 1), (120, 132, 158))
    centered("ONLINE: 10   IN MATCH: 2   QUEUE: 1", line_top(COUNTERS_LINE), (200, 214, 235))

    for i, (tag, name, elo) in enumerate(NAMES[:ROWS]):
        y = line_top(FIRST_ROW_LINE + i)
        centered(f"{tag.rjust(9)}  {name}", y, (240, 244, 252))
        if font is not None:
            d.text((w - 42, y), elo, fill=(251, 191, 36), font=font)

    footer_line = FIRST_ROW_LINE + ROWS + 1
    centered("RANK: MVP   -   2410 ELO   -   5469 COINS", line_top(footer_line), (200, 214, 235))
    centered("SEASON 1   -   BOSKETBALL.MC-HOSTING.XYZ", line_top(footer_line + 1), (150, 160, 190))

    # the client paints the header (and therefore our frame) AFTER the rows: composite it last so the
    # preview shows the same 25% backdrop over the names that players see
    canvas.alpha_composite(frame, (0, 0))
    canvas.resize((w * SCALE, h * SCALE), Image.NEAREST).save(OUT)
    print(f"tablist -> {OUT.relative_to(ROOT)} ({w * SCALE}, {h * SCALE})  frame {w}x{h} for {ROWS} rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
