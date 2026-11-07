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

    python3 tools/preview_tablist.py [rows]

Round 32: the frame has no fill any more, so the preview paints a mock lobby behind it — that is
what the panel really looks like in game now.
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "resourcepack/assets/bosketball/textures/font"
SCALE = 2
ROWS = int(sys.argv[1]) if len(sys.argv) > 1 else 10
OUT = ROOT / ("docs/previews/tablist.png" if ROWS == 10 else f"docs/previews/tablist-{ROWS}row.png")
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
    # A mock lobby behind the panel: the frame is transparent now, the world shows through.
    canvas = Image.new("RGBA", (w, h), (16, 78, 88, 255))
    bg = ImageDraw.Draw(canvas)
    for y in range(h):
        t = y / max(1, h - 1)
        bg.line([(0, y), (w, y)], fill=(int(14 + 26 * t), int(96 + 26 * t), int(104 + 18 * t), 255))
    for x in range(0, w, 96):
        bg.rectangle([x, 0, x + 2, h], fill=(10, 60, 70, 255))
    # Round 29 - the frame is opaque and is painted *before* the rows (verified in game: the nick
    # keeps its full #F1F5F9, a 25% veil would have dimmed it to ~#B5B8BB), so the preview must
    # composite it first, exactly like the client does.
    canvas.alpha_composite(frame, (0, 0))
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

    if ROWS == 1:
        # The owner's situation: a single player online.
        NAMES[:] = [("MVP", "MaxLananas", "2200")]
    # Round 36: keep filling the panel past the 10 sample names so the 11..20 row previews show a
    # full list instead of a half-empty frame.
    for i in range(ROWS):
        tag, name, elo = NAMES[i % len(NAMES)]
        y = line_top(FIRST_ROW_LINE + i)
        centered(f"{tag.rjust(9)}  {name}", y, (240, 244, 252))
        if font is not None:
            d.text((w - 42, y), elo, fill=(251, 191, 36), font=font)

    # Two empty lines stay reserved for the frame's bottom rail and crest (Round 32).
    footer_line = FIRST_ROW_LINE + ROWS + 1
    centered("RANK: MVP   -   2410 ELO   -   5469 COINS", line_top(footer_line), (200, 214, 235))
    centered("SEASON 1   -   RANKED LADDER", line_top(footer_line + 1), (150, 160, 190))

    canvas.resize((w * SCALE, h * SCALE), Image.NEAREST).save(OUT)
    print(f"tablist -> {OUT.relative_to(ROOT)} ({w * SCALE}, {h * SCALE})  frame {w}x{h} for {ROWS} rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
