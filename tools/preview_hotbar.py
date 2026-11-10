#!/usr/bin/env python3
"""Render the lobby hotbar exactly as the plugin lays it out.

The hotbar uses sprites from the pack's own icon library (`textures/item/icons/ref_*.png`,
167 sprites imported from assets/ref-icons), not hand-drawn badges: the toolbar, the hub and
the menus then share one visual language.

The slot contract lives in `BallSkin.LOBBY_HOTBAR_SLOTS` / `LOBBY_HOTBAR_MODELS`; this tool
mirrors it and fails loudly if the two drift apart.

    python3 tools/preview_hotbar.py           # docs/previews/hotbar-row.png
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ICON_DIR = ROOT / "resourcepack/assets/bosketball/textures/item/icons"
OUT = ROOT / "docs/previews/hotbar-row.png"
BALLSKIN = ROOT / "src/main/java/fr/bosketball/model/BallSkin.java"

EXPECTED_SLOTS = [1, 2, 3, 4, 5, 6, 7]


def read_contract() -> tuple[list[int], list[str]]:
    src = BALLSKIN.read_text(encoding="utf-8")
    slots = re.search(r"LOBBY_HOTBAR_SLOTS\s*=\s*\{([^}]*)\}", src)
    models = re.search(r"LOBBY_HOTBAR_MODELS\s*=\s*\{([^}]*)\}", src)
    if not slots or not models:
        raise SystemExit("could not read the hotbar contract from BallSkin.java")
    slot_list = [int(v.strip()) for v in slots.group(1).split(",") if v.strip()]
    model_list = re.findall(r'"([^"]+)"', models.group(1))
    if slot_list != EXPECTED_SLOTS:
        raise SystemExit(f"BallSkin.LOBBY_HOTBAR_SLOTS changed: {slot_list} (the row must stay 1..7)")
    if len(model_list) != len(slot_list):
        raise SystemExit("slot and model lists disagree, the hotbar would render half-empty")
    return slot_list, model_list


def main() -> int:
    slots, models = read_contract()
    scale = 4
    slot_px = 20 * scale
    pad = 6
    width = 9 * slot_px + 8 * pad + 4 * pad
    height = slot_px + 4 * pad
    row = Image.new("RGBA", (width, height), (22, 26, 36, 255))
    draw = ImageDraw.Draw(row)

    for slot in range(9):
        x = 2 * pad + slot * (slot_px + pad)
        filled = slot in slots
        draw.rounded_rectangle(
            [x, 2 * pad, x + slot_px, 2 * pad + slot_px],
            radius=2 * scale,
            fill=(58, 66, 84, 255) if filled else (36, 41, 54, 255),
            outline=(18, 22, 33, 255),
            width=1,
        )

    for model, slot in zip(models, slots):
        path = ICON_DIR / f"{model}.png"
        if not path.is_file():
            raise SystemExit(f"missing sprite for hotbar slot {slot}: {path.relative_to(ROOT)}")
        icon = Image.open(path).convert("RGBA").resize((slot_px - 4 * scale, slot_px - 4 * scale), Image.NEAREST)
        x = 2 * pad + slot * (slot_px + pad) + 2 * scale
        row.paste(icon, (x, 2 * pad + 2 * scale), icon)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    row.save(OUT)
    print("hotbar ->", OUT.relative_to(ROOT))
    print("   " + "  ".join(f"{slot}:{model}" for slot, model in zip(slots, models)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
