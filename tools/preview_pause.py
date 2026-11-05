#!/usr/bin/env python3
"""Render the pause-menu skin so it can be reviewed without launching the game.

Two panels, both built from the shipped files:

  left  — the real ESC screen: `gui/menu_background.png` tiled, plus our `widget/button`
          sprites nine-sliced to 200x20 with the vanilla labels on top (the client draws those, the
          textures never contain text);
  right — the server menu that stands in for ESC: `gui_overlay_hub.png` at 1:1, slot wells on the
          contract grid, the server logo in the plaque band.

    python3 tools/preview_pause.py        # docs/previews/pause_menu.png
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
MC = ROOT / "resourcepack/assets/minecraft/textures/gui"
FONT = ROOT / "resourcepack/assets/bosketball/textures/font"
OUT = ROOT / "docs/previews/pause_menu.png"
SCALE = 3
LABELS = ["Back to Game", "Advancements", "Statistics", "Options...", "Report Bugs", "Leave Server"]


def nine_slice(path: Path, size: tuple[int, int]) -> Image.Image:
    """Render a GUI sprite: stretch, tile or nine-slice, exactly as its .mcmeta declares."""
    meta = path.with_suffix(".png.mcmeta")
    src = Image.open(path).convert("RGBA")
    kind, border = "stretch", 3
    if meta.is_file():
        scaling = json.loads(meta.read_text(encoding="utf-8"))["gui"]["scaling"]
        kind = scaling.get("type", "stretch")
        border = scaling.get("border", 3)
    if kind == "stretch":
        return src.resize(size, Image.NEAREST)
    if kind == "tile":
        out = Image.new("RGBA", size)
        for y in range(0, size[1], src.height):
            for x in range(0, size[0], src.width):
                out.alpha_composite(src, (x, y))
        return out
    if isinstance(border, dict):
        border = border.get("left", 3)
    w, h = size
    out = Image.new("RGBA", (w, h))
    b = max(1, min(border, w // 2, h // 2))
    out.paste(src.crop((0, 0, b, b)), (0, 0))
    out.paste(src.crop((src.width - b, 0, src.width, b)), (w - b, 0))
    out.paste(src.crop((0, src.height - b, b, src.height)), (0, h - b))
    out.paste(src.crop((src.width - b, src.height - b, src.width, src.height)), (w - b, h - b))
    out.paste(src.crop((b, 0, src.width - b, b)).resize((w - 2 * b, b), Image.NEAREST), (b, 0))
    out.paste(src.crop((b, src.height - b, src.width - b, src.height)).resize((w - 2 * b, b), Image.NEAREST), (b, h - b))
    out.paste(src.crop((0, b, b, src.height - b)).resize((b, h - 2 * b), Image.NEAREST), (0, b))
    out.paste(src.crop((src.width - b, b, src.width, src.height - b)).resize((b, h - 2 * b), Image.NEAREST), (w - b, b))
    out.paste(src.crop((b, b, src.width - b, src.height - b)).resize((w - 2 * b, h - 2 * b), Image.NEAREST), (b, b))
    return out


def build_esc_screen(width: int = 176, height: int = 166) -> Image.Image:
    # the in-world pause screen (ESC) uses inworld_menu_background.png, not the title one
    screen = nine_slice(MC / "inworld_menu_background.png", (width, height))
    # a dim veil like the client's blur/darken pass
    screen.alpha_composite(Image.new("RGBA", (width, height), (0, 0, 0, 70)), (0, 0))
    d = ImageDraw.Draw(screen)
    try:
        font = ImageFont.load_default()
    except Exception:
        font = None
    top = 24
    for i, label in enumerate(LABELS):
        btn = nine_slice(MC / "sprites/widget/button.png", (200 if i == 0 else 150, 20))
        x = (width - btn.width) // 2
        screen.alpha_composite(btn, (x, top + i * 24))
        if font is not None:
            tw = d.textlength(label, font=font)
            d.text((x + (btn.width - tw) / 2, top + i * 24 + 5), label, fill=(255, 255, 255), font=font)
    return screen


def main() -> int:
    esc = build_esc_screen()
    hub = Image.open(FONT / "gui_overlay_hub.png").convert("RGBA")
    pad = 16
    w = (esc.width + hub.width) * SCALE + pad * 3
    h = max(esc.height, hub.height) * SCALE + pad * 2 + 18
    sheet = Image.new("RGBA", (w, h), (18, 18, 24, 255))
    d = ImageDraw.Draw(sheet)
    sheet.alpha_composite(esc.resize((esc.width * SCALE, esc.height * SCALE), Image.NEAREST), (pad, pad))
    sheet.alpha_composite(hub.resize((hub.width * SCALE, hub.height * SCALE), Image.NEAREST),
                          (pad * 2 + esc.width * SCALE, pad))
    d.text((pad, pad + esc.height * SCALE + 4), "ESC screen (pack skin)", fill=(200, 214, 235))
    d.text((pad * 2 + esc.width * SCALE, pad + hub.height * SCALE + 4),
           "server menu (logo on top)", fill=(200, 214, 235))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("pause menu ->", OUT.relative_to(ROOT), sheet.size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
