#!/usr/bin/env python3
"""BosketBall guild crest — 16x16 pixel art in the same art direction as the
purchased icon library: deep-plum outline (never pure black), a warm specular
cluster on the top-left, a saturated mid tone and a darker rim shadow.

Writes the item texture, the item model and the item definition so the pack and
the GUI can both use it. Run:  python3 tools/guild_icon.py
"""

from __future__ import annotations

import json
import struct
import zlib
from pathlib import Path

RP = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "bosketball"

# Art-direction palette (identical rules to tools/cuboide_icons.py)
O = (46, 20, 36, 255)      # deep plum outline
S = (16, 84, 58, 255)      # emerald rim shadow
M = (34, 168, 96, 255)     # emerald mid
L = (98, 226, 140, 255)    # emerald highlight
D = (150, 74, 18, 255)     # gold shadow
G = (236, 154, 28, 255)    # gold mid
H = (255, 214, 68, 255)    # gold highlight
W = (255, 250, 214, 255)   # ivory specular
T = (0, 0, 0, 0)

# Heraldic shield silhouette: half-width per row, symmetric about the seam
# between columns 7 and 8 so the outline never wobbles.
HALF_WIDTH = [0, 5, 6, 7, 7, 7, 7, 7, 7, 7, 7, 6, 5, 4, 3, 2]  # rows 1..15, row 0 empty

# Crown emblem — three tips, a jewelled band and a wide base. Drawn with explicit
# coordinates instead of a character grid so the symmetry is exact.
# Crown emblem as a tiny bitmap. Gold on emerald already carries enough contrast,
# so it gets no plum outline (that would eat the shield field at 16x16) — only a
# darker gold base row, exactly like the trophy icons in the library.
CROWN_MASK = [
    "H.H.HH.H.H",
    "HHHHHHHHHH",
    "HGHGHGHGHG",
    "HHHHHHHHHH",
    "DDDDDDDDDD",
]
CROWN_ORIGIN = (3, 7)  # 10 px wide, centred exactly on the shield seam


def _filled(y: int, x: int) -> bool:
    hw = HALF_WIDTH[y]
    return hw > 0 and 8 - hw <= x <= 7 + hw


def _shield_rows() -> list[str]:
    grid = [["." for _ in range(16)] for _ in range(16)]

    for y in range(16):
        for x in range(16):
            if _filled(y, x):
                grid[y][x] = "M"

    # Gold banner band hugging the top of the shield (rows 2-4) with a drop shadow,
    # the same two-tone treatment the trophy and crown icons use.
    for x in range(16):
        if _filled(2, x):
            grid[2][x] = "H"
        if _filled(3, x):
            grid[3][x] = "G" if x != 2 and x != 13 else "H"
        if _filled(4, x):
            grid[4][x] = "D"

    # Specular cluster on the upper-left of the emerald field.
    for x, y in [(1, 5), (2, 5), (1, 6), (2, 6), (3, 6), (1, 7)]:
        if _filled(y, x):
            grid[y][x] = "L"

    # Crown emblem
    ox, oy = CROWN_ORIGIN
    for dy, line in enumerate(CROWN_MASK):
        for dx, ch in enumerate(line):
            if ch != ".":
                grid[oy + dy][ox + dx] = ch

    # Rim shadow hugging the taper, lower-right.
    for y in range(9, 16):
        for x in range(8, 16):
            if grid[y][x] == "M":
                below = _filled(y + 1, x) if y + 1 < 16 else False
                right = _filled(y, x + 1) if x + 1 < 16 else False
                if not below or not right:
                    grid[y][x] = "S"

    # Outline pass: any filled pixel touching the outside becomes plum.
    for y in range(16):
        for x in range(16):
            if not _filled(y, x):
                continue
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if ny < 0 or ny > 15 or nx < 0 or nx > 15 or not _filled(ny, nx):
                    grid[y][x] = "O"
                    break

    return ["".join(row) for row in grid]


CREST = _shield_rows()

CMAP = {"O": O, "S": S, "M": M, "L": L, "D": D, "G": G, "H": H, "W": W, ".": T}


def render(rows: list[str], extra: dict[tuple[int, int], tuple[int, int, int, int]] | None = None):
    px = [[T for _ in range(16)] for _ in range(16)]
    for y, row in enumerate(rows[:16]):
        for x, ch in enumerate(row[:16]):
            px[y][x] = CMAP.get(ch, T)
    for (x, y), colour in (extra or {}).items():
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = colour
    return px


def write_png(path: Path, px, scale: int = 1) -> None:
    raw = b""
    for y in range(16):
        line = b""
        for x in range(16):
            line += bytes(px[y][x]) * scale
        raw += (b"\x00" + line) * scale

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", 16 * scale, 16 * scale, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


DISP = {
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
}


def emit(icon_id: str, px) -> None:
    write_png(RP / "textures" / "item" / f"{icon_id}.png", px)
    (RP / "models" / "item" / f"{icon_id}.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated",
         "textures": {"layer0": f"bosketball:item/{icon_id}"},
         "display": DISP}, indent=2))
    (RP / "items" / f"{icon_id}.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": f"bosketball:item/{icon_id}"},
         "hand_animation_on_swap": False}, indent=2))


# Recruitment badge: a small ivory "+" in a plum ring, sitting on the lower-right
# of the field so it reads as a sticker instead of clipping the banner.
PLUS_MASK = [
    "OOOOO",
    "O.W.O",
    "OWWWO",
    "O.W.O",
    "OOOOO",
]
PLUS_ORIGIN = (9, 10)


def _with_plus(rows: list[str]) -> list[str]:
    grid = [list(r) for r in rows]
    ox, oy = PLUS_ORIGIN
    for dy, line in enumerate(PLUS_MASK):
        for dx, ch in enumerate(line):
            x, y = ox + dx, oy + dy
            if 0 <= x < 16 and 0 <= y < 16:
                grid[y][x] = ch
    return ["".join(r) for r in grid]


def main() -> None:
    crest = render(CREST)
    emit("icon_guild", crest)
    emit("icon_new_guild", render(_with_plus(CREST)))

    # Large previews for humans (open tools/_guild_preview.png).
    here = Path(__file__).resolve().parent
    write_png(here / "_guild_preview.png", crest, scale=20)
    write_png(here / "_guild_new_preview.png", render(_with_plus(CREST)), scale=20)
    print("icon_guild + icon_new_guild written")


if __name__ == "__main__":
    main()
