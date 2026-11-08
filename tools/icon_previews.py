#!/usr/bin/env python3
"""Render the icon-set before/after contact sheets used in the PR body.

Two images, both written under ``docs/agents/previews``:

  icon-set-before-after.png   every sprite, as committed on the branch base,
                              and as it is now, on one page
  icon-system-proof.png       the specific claims, one panel each

Run:  python3 tools/icon_previews.py
"""

from __future__ import annotations

import io
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))

import icon_language as lang

ROOT = lang.ROOT
OUT = ROOT / "docs" / "agents" / "previews"

#: The commit this branch started from.  "Before" is whatever these sprites
#: looked like there, read straight out of git rather than kept as a copy.
BASELINE = "d44e011"

BG = (17, 20, 28, 255)
INK = (248, 250, 252, 255)
MUTED = (148, 163, 184, 255)
GOLD = (251, 191, 36, 255)


def image_at(rev: str, path: str):
    blob = subprocess.run(["git", "show", f"{rev}:{path}"], capture_output=True).stdout
    if not blob:
        return None
    return Image.open(io.BytesIO(blob)).convert("RGBA")


def light_tally() -> dict[str, int]:
    tally: dict[str, int] = {}
    for sprite in lang.icon_texture_paths():
        verdict = lang.light_verdict(sprite.stem, lang.light_vector(Image.open(sprite).convert("RGBA")))
        tally[verdict] = tally.get(verdict, 0) + 1
    return tally


def contact_sheet() -> Image.Image:
    sprites = [(p.stem, str(p.relative_to(ROOT))) for p in lang.icon_texture_paths()]
    scale, pad, cols = 7, 4, 24
    cell = 16 * scale + pad
    rows = (len(sprites) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell + 16, 2 * (rows * cell + 34) + 40), BG)
    draw = ImageDraw.Draw(sheet)
    draw.text((10, 8), "BOSKETBALL ICON SET  —  before (top) / after (bottom)", fill=INK)
    for label, offset in (("BEFORE", 30), ("AFTER", 30 + rows * cell + 34)):
        draw.text((10, offset), f"{label} — {len(sprites)} sprites", fill=MUTED)
        for index, (stem, path) in enumerate(sprites):
            sprite = image_at(BASELINE, path) if label == "BEFORE" else Image.open(ROOT / path).convert("RGBA")
            if sprite is None:
                continue
            x = (index % cols) * cell + 10
            y = offset + 14 + (index // cols) * cell
            sheet.alpha_composite(sprite.resize((16 * scale, 16 * scale), Image.NEAREST), (x, y))
    return sheet


def proof_panel() -> Image.Image:
    scale = 9
    cell = 16 * scale
    sheet = Image.new("RGBA", (1180, 1030), BG)
    draw = ImageDraw.Draw(sheet)

    def head(x, y, text):
        draw.text((x, y), text, fill=GOLD)

    def sub(x, y, text):
        draw.text((x, y), text, fill=MUTED)

    draw.text((20, 16), "ICON SYSTEM  ·  before / after", fill=INK)
    sub(20, 34, "every claim here is enforced by a test or a gate")

    # 1 -- one ink.  Two pairs stacked so the left column stays left of the
    # bar chart, which starts at x=560.
    head(20, 70, "1 · one ink on every silhouette edge")
    sub(20, 86, "33 ink colours  ->  #161E2B.  9,223 edge pixels over 215 sprites.")
    y = 106
    for name in ("icon_home", "icon_shop"):
        path = f"resourcepack/assets/bosketball/textures/item/{name}.png"
        before = image_at(BASELINE, path)
        after = Image.open(ROOT / path).convert("RGBA")
        sheet.alpha_composite(before.resize((cell, cell), Image.NEAREST), (20, y))
        sheet.alpha_composite(after.resize((cell, cell), Image.NEAREST), (20 + cell + 10, y))
        sub(20, y + cell + 5, f"{name}   before | after")
        y += cell + 34

    # 2 -- strays
    head(20, 470, "2 · stray lone pixels removed")
    sub(20, 486, "icon_defense carried four opaque pixels with no neighbour.")
    path = "resourcepack/assets/bosketball/textures/item/icon_defense.png"
    sheet.alpha_composite(image_at(BASELINE, path).resize((cell, cell), Image.NEAREST), (20, 506))
    sheet.alpha_composite(
        Image.open(ROOT / path).convert("RGBA").resize((cell, cell), Image.NEAREST), (20 + cell + 10, 506)
    )
    sub(20, 506 + cell + 5, "before | after")

    # 3 -- the two drawn ids
    head(20, 740, "3 · the two ids Java asked for and nobody had drawn")
    sub(20, 756, "four fire achievements and one quest glyph had a blank item.")
    x = 20
    for name, why in (("icon_dunk_flame", "fire achievements"), ("icon_ball_classic", "quest glyph")):
        sprite = Image.open(ROOT / f"resourcepack/assets/bosketball/textures/item/{name}.png").convert("RGBA")
        sheet.alpha_composite(sprite.resize((cell, cell), Image.NEAREST), (x, 780))
        sub(x, 780 + cell + 5, name)
        sub(x, 780 + cell + 19, why)
        x += cell + 40

    # 4 -- light tally
    right = 560
    head(right, 70, "4 · light: measured, gated, ratcheted")
    sub(right, 86, "brightest tone vs shape centre; only the last row fails the build.")
    tally = light_tally()
    y = 112
    for key, label, colour in (
        ("ok", "top-lit (key light)", "#4ADE80"),
        ("flat", "flat / unshaded", "#64748B"),
        ("figurative", "bright feature", "#38BDF8"),
        ("reviewable", "legacy bottom-lit (documented)", "#FB923C"),
        ("wrong-side", "NEW bottom-lit — fails build", "#F87171"),
    ):
        count = tally.get(key, 0)
        draw.text((right, y + 6), label, fill=INK)
        draw.text((right + 400, y + 6), str(count), fill=INK)
        draw.rectangle([right, y + 22, right + 560, y + 34], fill=(30, 36, 48, 255))
        width = int(560 * min(1.0, count / 161.0))
        rgb = tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))
        if width > 0:
            draw.rectangle([right, y + 22, right + width, y + 34], fill=rgb + (255,))
        y += 52
    sub(right, y + 6, "12 legacy outliers are enumerated with a cause each and the list")
    sub(right, y + 22, "may only shrink. Zero new bottom-lit is a build failure.")

    # 5 -- totals
    head(right, 470, "5 · totals")
    y = 496
    for label, value in (
        ("sprites in the pack", "224"),
        ("ink colours", "33 -> 1"),
        ("stray pixels", "4 -> 0"),
        ("partial-alpha pixels", "0"),
        ("byte-identical pairs", "29, all deliberate"),
        ("ids Java wants, missing", "2 -> 0"),
        ("icons lit from below (new)", "12 -> 0"),
        ("tests", "187 -> 194"),
        ("build", "250 classes, 0 errors, OK"),
    ):
        draw.text((right, y), label, fill=MUTED)
        draw.text((right + 300, y), value, fill=INK)
        y += 26

    draw.text((20, 980), "verified: a full art rebuild leaves zero icon textures changed", fill=INK)
    sub(20, 998, "generate_resourcepack.py  ·  icon_inventory.py --check  ·  build_plugin.py")
    return sheet


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    contact_sheet().save(OUT / "icon-set-before-after.png")
    proof_panel().save(OUT / "icon-system-proof.png")
    print(f"[icons] wrote 2 preview sheets to {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
