#!/usr/bin/env python3
"""Lobby hotbar icon family — one glossy badge per menu door.

The lobby hotbar used to lean on 16x16 pixel-art props (a crate for the shop, a
megaphone for friends, a clipboard for the guide) plus emoji in the item names.
This tool builds a single, consistent family of 32x32 badges: same silhouette,
same outer stroke, same gloss, one accent colour per door, and a bold white glyph
that stays readable when the client renders the icon at 16x16 in a hotbar slot.

Ids (also used as item-model keys in the resource pack):

    hub_modes    orange    play triangle      -> Play modes
    hub_shop     gold      shopping cart      -> Shop
    hub_locker   sky       jersey             -> Locker & tags
    hub_menu     violet    four-square grid   -> Lobby menu / hub
    hub_friends  emerald   two busts          -> Friends & party
    hub_ranks    pink      medal              -> Ranks guide
    hub_top      yellow    crown              -> Leaderboards

Run:
    python3 tools/hotbar_icons.py            # write textures + models + item defs
    python3 tools/hotbar_icons.py --preview  # also write docs/previews/hotbar-icons.png
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "resourcepack/assets/bosketball"
TEX_DIR = PACK / "textures/item/hub"
MODEL_DIR = PACK / "models/item"
ITEM_DIR = PACK / "items"
PREVIEW = ROOT / "docs/previews/hotbar-icons.png"

SIZE = 32
SS = 8                     # supersampling factor
C = SIZE * SS

# id: (top colour, bottom colour, glyph colour)
PALETTE = {
    "hub_modes":   ((255, 168, 74), (232, 108, 12), (255, 255, 255)),
    "hub_shop":    ((253, 224, 71), (217, 155, 6), (255, 255, 255)),
    "hub_locker":  ((125, 211, 252), (14, 148, 233), (255, 255, 255)),
    "hub_menu":    ((196, 181, 253), (124, 88, 237), (255, 255, 255)),
    "hub_friends": ((110, 231, 183), (5, 150, 105), (255, 255, 255)),
    "hub_ranks":   ((249, 168, 212), (219, 39, 119), (255, 255, 255)),
    "hub_top":     ((254, 240, 138), (234, 179, 8), (255, 255, 255)),
}


def _shade(rgb, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in rgb)


def _rounded_mask(box, radius):
    mask = Image.new("L", (C, C), 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius=radius, fill=255)
    return mask


def _badge(top, bottom):
    """Base plate: vertical gradient, dark outer stroke, glossy top half."""
    plate = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    grad = Image.new("RGBA", (C, C))
    gd = ImageDraw.Draw(grad)
    for y in range(C):
        t = y / (C - 1)
        col = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        gd.line([(0, y), (C, y)], fill=col + (255,))

    inset = 2 * SS
    box = [inset, inset, C - inset - 1, C - inset - 1]
    radius = 8 * SS
    mask = _rounded_mask(box, radius)
    plate.paste(grad, (0, 0), mask)

    d = ImageDraw.Draw(plate)
    # outer stroke: a touch wider and much darker, so the badge reads on any slot colour
    d.rounded_rectangle(box, radius=radius, outline=_shade(bottom, 0.42) + (255,), width=max(1, int(1.3 * SS)))
    # inner top highlight
    d.rounded_rectangle(
        [box[0] + SS, box[1] + SS, box[2] - SS, box[3] - SS],
        radius=radius - SS, outline=(255, 255, 255, 46), width=max(1, int(0.7 * SS)))

    # gloss: upper third, soft
    gloss = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    ImageDraw.Draw(gloss).rounded_rectangle(box, radius=radius, fill=(255, 255, 255, 52))
    gloss = gloss.filter(ImageFilter.GaussianBlur(SS * 1.2))
    keep = Image.new("L", (C, C), 0)
    ImageDraw.Draw(keep).rectangle([0, 0, C, int(C * 0.52)], fill=255)
    gloss.putalpha(Image.composite(gloss.getchannel("A"), Image.new("L", (C, C), 0), keep))
    plate = Image.alpha_composite(plate, Image.composite(
        gloss, Image.new("RGBA", (C, C), (0, 0, 0, 0)), mask))
    return plate


def _star(cx, cy, outer, inner, points=5, rotation=-90.0):
    pts = []
    for i in range(points * 2):
        r = outer if i % 2 == 0 else inner
        a = math.radians(rotation + i * 180.0 / points)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def _glyph(name, colour, accent):
    """Glyph drawn in 32x32 units, then scaled up by SS for clean antialiasing."""
    g = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    u = C / 32.0
    col = colour + (255,)
    soft = (255, 255, 255, 150)

    def sc(points):
        return [(x * u, y * u) for x, y in points]

    def poly(points, fill):
        d.polygon(sc(points), fill=fill)

    def circle(cx, cy, r, fill):
        d.ellipse([(cx - r) * u, (cy - r) * u, (cx + r) * u, (cy + r) * u], fill=fill)

    def rrect(x0, y0, x1, y1, radius, fill):
        d.rounded_rectangle([x0 * u, y0 * u, x1 * u, y1 * u], radius=radius * u, fill=fill)

    def line(points, width, fill):
        d.line(sc(points), fill=fill, width=int(width * u), joint="curve")

    if name == "hub_modes":
        poly([(11.4, 7.6), (25.8, 16.0), (11.4, 24.4)], col)

    elif name == "hub_shop":
        line([(4.8, 6.8), (8.4, 6.8), (10.4, 15.6)], 2.2, col)
        poly([(11.0, 9.8), (27.2, 9.8), (24.4, 19.2), (12.6, 19.2)], col)
        circle(14.6, 23.2, 2.2, col)
        circle(22.4, 23.2, 2.2, col)

    elif name == "hub_locker":
        poly([(11.6, 6.6), (20.4, 6.6), (26.4, 11.4), (23.2, 15.6), (21.0, 13.7),
              (21.0, 25.6), (11.0, 25.6), (11.0, 13.7), (8.8, 15.6), (5.6, 11.4)], col)
        circle(16.0, 6.6, 3.0, (0, 0, 0, 0))

    elif name == "hub_menu":
        for gx in (6.6, 16.9):
            for gy in (6.6, 16.9):
                rrect(gx, gy, gx + 8.5, gy + 8.5, 1.9, col)

    elif name == "hub_friends":
        circle(20.4, 11.6, 3.5, soft)
        rrect(16.4, 16.8, 26.6, 25.4, 2.8, soft)
        circle(12.0, 10.6, 4.3, col)
        rrect(5.4, 16.6, 18.6, 25.8, 3.6, col)

    elif name == "hub_ranks":
        poly([(10.6, 5.6), (15.0, 5.6), (13.2, 14.6), (9.4, 14.6)], soft)
        poly([(21.4, 5.6), (17.0, 5.6), (18.8, 14.6), (22.6, 14.6)], soft)
        circle(16.0, 19.4, 6.7, col)
        poly(_star(16.0, 19.4, 4.2, 1.75), _shade(accent, 0.95) + (255,))

    elif name == "hub_top":
        poly([(5.8, 22.6), (5.8, 10.0), (11.4, 16.0), (16.0, 7.4),
              (20.6, 16.0), (26.2, 10.0), (26.2, 22.6)], col)
        rrect(5.8, 22.6, 26.2, 26.0, 1.3, col)

    shadow = g.filter(ImageFilter.GaussianBlur(SS * 0.55))
    shadow.putalpha(shadow.getchannel("A").point(lambda a: int(a * 0.42)))
    out = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    out.paste(shadow, (0, max(1, int(SS * 0.7))), shadow)
    return Image.alpha_composite(out, g)


def build_icon(name):
    top, bottom, glyph_colour = PALETTE[name]
    plate = _badge(top, bottom)
    glyph = _glyph(name, glyph_colour, bottom)
    icon = Image.alpha_composite(plate, glyph)
    return icon.resize((SIZE, SIZE), Image.LANCZOS)


def write_assets():
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)

    model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "bosketball:item/hub/{id}"},
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        },
    }
    definition = {"model": {"type": "minecraft:model", "model": "bosketball:item/{id}"}, "hand_animation_on_swap": False}

    written = []
    for name in PALETTE:
        icon = build_icon(name)
        icon.save(TEX_DIR / f"{name}.png")
        (MODEL_DIR / f"{name}.json").write_text(
            json.dumps(json.loads(json.dumps(model).replace("{id}", name)), indent=2) + "\n", encoding="utf-8")
        (ITEM_DIR / f"{name}.json").write_text(
            json.dumps(json.loads(json.dumps(definition).replace("{id}", name)), indent=2) + "\n", encoding="utf-8")
        written.append(name)
    return written


HOTBAR_ORDER = ["hub_modes", "hub_shop", "hub_locker", "hub_menu", "hub_friends", "hub_ranks", "hub_top"]
HOTBAR_SLOTS = (1, 2, 3, 4, 5, 6, 7)     # mirrors BallSkin.LOBBY_HOTBAR_SLOTS
ROW_PREVIEW = ROOT / "docs/previews/hotbar-row.png"


def write_preview():
    scale = 8
    cols = len(PALETTE)
    pad = 12
    cell = SIZE * scale
    sheet = Image.new("RGBA", (cols * (cell + pad) + pad, cell + 2 * pad + 8 * scale), (18, 22, 33, 255))
    for i, name in enumerate(PALETTE):
        icon = Image.open(TEX_DIR / f"{name}.png").convert("RGBA").resize((cell, cell), Image.NEAREST)
        sheet.paste(icon, (pad + i * (cell + pad), pad), icon)
    sheet.save(PREVIEW)
    return PREVIEW


def write_row_preview(scale=4):
    """Nine hotbar slots, in order, so the centring can be checked at a glance."""
    slot_px = 20 * scale
    pad = 6
    width = 9 * slot_px + 8 * pad + 4 * pad
    height = slot_px + 4 * pad
    row = Image.new("RGBA", (width, height), (22, 26, 36, 255))
    d = ImageDraw.Draw(row)
    for slot in range(9):
        x = 2 * pad + slot * (slot_px + pad)
        filled = slot in HOTBAR_SLOTS
        d.rounded_rectangle([x, 2 * pad, x + slot_px, 2 * pad + slot_px], radius=2 * scale,
                            fill=(58, 66, 84, 255) if filled else (36, 41, 54, 255),
                            outline=(18, 22, 33, 255), width=1)
    for name, slot in zip(HOTBAR_ORDER, HOTBAR_SLOTS):
        icon = Image.open(TEX_DIR / f"{name}.png").convert("RGBA").resize(
            (slot_px - 4 * scale, slot_px - 4 * scale), Image.LANCZOS)
        x = 2 * pad + slot * (slot_px + pad) + 2 * scale
        row.paste(icon, (x, 2 * pad + 2 * scale), icon)
    row.save(ROW_PREVIEW)
    return ROW_PREVIEW


def main(argv):
    names = write_assets()
    print(f"wrote {len(names)} hotbar icons: " + ", ".join(names))
    if "--preview" in argv:
        print("preview ->", write_preview())
        print("hotbar  ->", write_row_preview())
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
