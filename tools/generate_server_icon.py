#!/usr/bin/env python3
"""Generate the BosketBall server icon: a native 16x16 basketball, upscaled x4 to 64x64.

Minecraft requires a 64x64 PNG for `server-icon.png`; the art itself is drawn on a
16x16 grid with hard alpha (no anti-aliasing) so it stays perfectly sharp.

    python3 tools/generate_server_icon.py                 # modern (default)
    python3 tools/generate_server_icon.py --style classic
    python3 tools/generate_server_icon.py --style retro
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TARGETS = [ROOT / "src" / "main" / "resources" / "server-icon.png",
           ROOT / "releases" / "server-icon.png"]

CENTER = 8.0        # pixel-centre coordinates: the ball spans the whole 16x16 canvas
RADIUS = 7.9
SEAM_CENTER = 7.5   # seams land on integer pixels
SIZE = 16
SCALE = 4

STYLES = {
    # soft 4-tone shading, no outline: reads well at 64px and in the server list
    "modern": {
        "tones": [(255, 208, 143, 255), (246, 150, 56, 255), (198, 90, 18, 255), (140, 57, 9, 255)],
        "seam": (74, 32, 9, 255),
        "outline": (68, 30, 9, 255),
        "highlight": False,
    },
    # darker outline all around, stronger contrast
    "classic": {
        "tones": [(255, 176, 84, 255), (233, 122, 32, 255), (206, 96, 20, 255), (163, 70, 12, 255)],
        "seam": (58, 26, 8, 255),
        "outline": (72, 33, 10, 255),
        "highlight": False,
    },
    # flat retro look: single orange, thick dark seams
    "retro": {
        "tones": [(226, 111, 26, 255)] * 4,
        "seam": (46, 20, 6, 255),
        "outline": (46, 20, 6, 255),
        "highlight": False,
    },
}


def inside(px: int, py: int) -> bool:
    return math.hypot(px + 0.5 - CENTER, py + 0.5 - CENTER) <= RADIUS


def shade(px: int, py: int, tones: list) -> tuple[int, int, int, int]:
    """Light from the top-left, shadow towards the bottom-right."""
    nx = (px + 0.5 - CENTER) / RADIUS
    ny = (py + 0.5 - CENTER) / RADIUS
    light = -(nx * 0.75 + ny * 0.75)
    if light > 0.55:
        return tones[0]
    if light > 0.0:
        return tones[1]
    if light > -0.55:
        return tones[2]
    return tones[3]


def seam_pixels() -> set[tuple[int, int]]:
    """Equator, meridian and the two curved side seams of a basketball."""
    seams: set[tuple[int, int]] = set()
    for py in range(SIZE):
        for px in range(SIZE):
            if not inside(px, py):
                continue
            sx = px + 0.5 - SEAM_CENTER
            sy = py + 0.5 - SEAM_CENTER
            if abs(sx) < 0.5 or abs(sy) < 0.5:
                seams.add((px, py))
                continue
            t = (py + 0.5) / SIZE
            bulge = 4.25 * math.sin(math.pi * t)
            if abs(abs(sx) - bulge) < 0.48:
                seams.add((px, py))
    return seams


def draw(style: str) -> Image.Image:
    spec = STYLES[style]
    seams = seam_pixels()
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out = img.load()
    for py in range(SIZE):
        for px in range(SIZE):
            if not inside(px, py):
                continue
            dist = math.hypot(px + 0.5 - CENTER, py + 0.5 - CENTER)
            if dist > RADIUS - 1.0:
                # 1px rim: keeps the ball readable on any background, all styles
                out[px, py] = spec["outline"] or spec["tones"][3]
            elif (px, py) in seams:
                out[px, py] = spec["seam"]
            else:
                out[px, py] = shade(px, py, spec["tones"])
    if spec["highlight"]:
        # tiny specular dot, top-left, like a real ball under arena lights
        for px, py in ((4, 4), (5, 4), (4, 5)):
            if inside(px, py):
                out[px, py] = (255, 226, 176, 255)
    return img


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--style", choices=sorted(STYLES), default="classic")
    args = ap.parse_args()

    art = draw(args.style)
    docs = ROOT / "docs"
    docs.mkdir(parents=True, exist_ok=True)
    art.save(docs / f"server-icon-16x16-{args.style}.png")
    art.resize((64, 64), Image.NEAREST).save(docs / f"server-icon-64x64-{args.style}.png")

    big = art.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST)
    for target in TARGETS:
        target.parent.mkdir(parents=True, exist_ok=True)
        big.save(target)
        print(f"wrote {target.relative_to(ROOT)} — 64x64 from native 16x16 ({args.style})")


if __name__ == "__main__":
    main()
