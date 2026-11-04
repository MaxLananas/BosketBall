#!/usr/bin/env python3
"""Compose a client-side preview of the Court Console menus.

The overlay glyph is what the client paints behind a menu; icons are drawn on top
by the inventory itself. This tool renders both layers together so the menus can
be reviewed without launching a server:

  * overlay 176x137 (or shorter) from tools/bosket_gui_kit.py,
  * 16x16 item icons pasted at slot_xy + (0, 7), exactly where Minecraft puts them,
  * a 3x nearest-neighbour upscale, six menus to a sheet.

Run: python3 tools/preview_gui_light.py
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

import bosket_gui_kit as kit  # noqa: E402

ICON_DIR = ROOT / "resourcepack/assets/bosketball/textures/item/icons"
LEGACY_DIR = ROOT / "resourcepack/assets/bosketball/textures/item"
OUT = ROOT / "docs/previews/gui_v5_light_menus.png"

SCALE = 3


def grid(start_row: int, end_row: int, start_col: int = 0, end_col: int = 8) -> list[int]:
    return [r * 9 + c for r in range(start_row, end_row + 1) for c in range(start_col, end_col + 1)]


HUB = {
    13: "ref_profile_alt",
    19: "ref_play",
    21: "ref_trophy",
    23: "ref_bag",
    25: "ref_friends",
    29: "ref_cup_gold",
    31: "ref_clock",
    33: "ref_quest_chest",
    38: "ref_dice",
    40: "ref_gear",
    42: "ref_discord",
    44: "ref_admin",
    45: "ref_leave",
    46: "ref_home",
    49: "ref_arrow_left",
    52: "ref_medal_gold",
    53: "ref_cross",
}

SEASON = {
    0: "ref_trophy",
    2: "ref_calendar_check",
    4: "ref_cup_gold",
    6: "ref_coins",
    8: "ref_check",
    19: "ref_star",
    20: "ref_star",
    21: "ref_star",
    22: "ref_lock",
    23: "ref_lock",
    24: "ref_lock",
    25: "ref_lock",
    28: "ref_clock_blue",
    30: "ref_cup_silver",
    32: "ref_upgrade",
    38: "ref_medal_silver",
    40: "ref_medal_gold",
    42: "ref_cup_gold",
    44: "ref_trophy",
    46: "ref_home",
    49: "ref_arrow_left",
    52: "ref_rewards",
    53: "ref_cross",
}

SHOP = {
    0: "ref_shop",
    1: "ref_clothing",
    2: "ref_hats",
    3: "ref_auras",
    4: "ref_gadgets",
    5: "ref_bag",
    6: "ref_gem",
    8: "ref_cross",
    10: "ref_gift",
    11: "ref_gift",
    12: "ref_gift",
    13: "ref_gift",
    14: "ref_gift",
    15: "ref_gift",
    16: "ref_gift",
    19: "ref_chest",
    20: "ref_chest",
    21: "ref_chest",
    22: "ref_chest",
    23: "ref_chest",
    24: "ref_chest",
    25: "ref_chest",
    28: "ref_key",
    29: "ref_key",
    30: "ref_key",
    31: "ref_key",
    32: "ref_key",
    33: "ref_key",
    34: "ref_key",
    37: "ref_coins",
    38: "ref_coins",
    39: "ref_coins",
    40: "ref_coins",
    41: "ref_coins",
    42: "ref_coins",
    43: "ref_coins",
    45: "ref_shop",
    46: "ref_home",
    49: "ref_arrow_left",
    52: "ref_bank",
    53: "ref_cross",
}

SETTINGS = {
    0: "ref_info",
    8: "ref_cross",
    10: "ref_emote_on",
    11: "ref_emote_sound",
    12: "ref_camera",
    13: "ref_music",
    14: "ref_heart",
    15: "ref_gear",
    16: "ref_chat",
    19: "ref_hats",
    20: "ref_region",
    21: "ref_emote_sound",
    22: "ref_camera",
    23: "ref_emote_play",
    24: "ref_party_invites",
    25: "ref_friends",
    28: "ref_trophy",
    29: "ref_emote_question",
    30: "ref_emote_sound",
    31: "ref_medal_gold",
    40: "ref_arrow_left",
}

CONFIRM = {
    19: "ref_claim",
    21: "ref_info",
    22: "ref_rewards",
    23: "ref_lock",
    24: "ref_gift",
    25: "ref_quest_chest",
    28: "ref_emote_alert",
    29: "ref_emote_minus",
    30: "ref_check",
    32: "ref_cross",
    45: "ref_leave",
    49: "ref_arrow_left",
    53: "ref_cross",
}

LEADERBOARDS = {
    0: "ref_cup_gold",
    2: "ref_cup_silver",
    4: "ref_cup_bronze",
    6: "ref_star",
    8: "ref_cross",
    10: "ref_medal_silver",
    11: "ref_medal_silver",
    12: "ref_medal_silver",
    19: "ref_medal_gold",
    20: "ref_medal_gold",
    21: "ref_medal_gold",
    28: "ref_cup_bronze",
    29: "ref_cup_bronze",
    30: "ref_cup_bronze",
    37: "ref_profile",
    38: "ref_profile",
    39: "ref_profile",
    45: "ref_trophy",
    46: "ref_home",
    49: "ref_arrow_left",
    53: "ref_cross",
}

SHEET = [
    ("LOBBY", "hub", HUB),
    ("SEASON", "season", SEASON),
    ("SHOP", "shop", SHOP),
    ("SETTINGS", "settings", SETTINGS),
    ("CONFIRM", "confirm", CONFIRM),
    ("LEADERBOARDS", "leaderboards", LEADERBOARDS),
]


def load_icon(name: str) -> Image.Image | None:
    for base in (ICON_DIR, LEGACY_DIR):
        path = base / f"{name}.png"
        if path.is_file():
            return Image.open(path).convert("RGBA")
    return None


def compose(key: str, icons: dict[int, str]) -> Image.Image:
    overlay = kit.build_overlay(key).copy()
    for slot, icon_name in icons.items():
        icon = load_icon(icon_name)
        if icon is None:
            continue
        sx, sy = kit.slot_xy(slot)
        overlay.alpha_composite(icon, (sx, sy))
    return overlay


def main() -> None:
    tiles = [(label, compose(key, icons)) for label, key, icons in SHEET]
    cell_w = max(t.width for _, t in tiles) * SCALE + 24
    cell_h = max(t.height for _, t in tiles) * SCALE + 34
    sheet = Image.new("RGB", (cell_w * 3, cell_h * 2), (14, 16, 20))
    draw = ImageDraw.Draw(sheet)
    for index, (label, tile) in enumerate(tiles):
        col, row = index % 3, index // 3
        ox, oy = col * cell_w + 12, row * cell_h + 26
        sheet.paste(tile.resize((tile.width * SCALE, tile.height * SCALE), Image.NEAREST), (ox, oy))
        draw.text((ox, oy - 16), label, fill=(214, 226, 244))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"Saved GUI preview sheet: {OUT.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


if __name__ == "__main__":
    main()
