#!/usr/bin/env python3
"""
BosketBall — Cuboide "Iconic Shops" / "Icenary" Pixel-Art Icon Suite,
Modernized HiDPI Glossy Rank Badges, 48x40 Large Basketball Hover Previews,
and Cytooxien-Style Custom Sculpted Container GUI Overlays.
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw


def pin_width(img: Image.Image) -> Image.Image:
    """Preserves exact glyph pixel width in Minecraft's BitmapProvider by pinning alpha=3 on rightmost column."""
    r, g, b, a = img.getpixel((img.width - 1, 0))
    if a == 0:
        img.putpixel((img.width - 1, 0), (255, 255, 255, 3))
    return img


# ============================================================================
# 1. CUBOIDE "ICONIC SHOPS" / "ICENARY" HUE-SHIFTED PIXEL-ART PALETTES
#    Rules from image-38.png & image-39.png:
#      - Never pure black (#000000) outlines: use deep plum, midnight indigo, or pine
#      - Top-left -45 deg specular cluster -> warm midtone -> saturated shadow -> rim
#      - Chunky 14x14..15x15 readable silhouettes on a 16x16 grid
# ============================================================================
PALETTES = {
    "gold": {
        "O": (46, 20, 36, 255),     # Deep plum-maroon outline
        "S": (176, 88, 22, 255),    # Warm amber shadow
        "M": (236, 154, 28, 255),   # Rich 24K gold mid
        "H": (255, 214, 68, 255),   # Bright gold highlight
        "W": (255, 250, 204, 255),  # Ivory specular gleam
    },
    "red": {
        "O": (48, 16, 34, 255),     # Dark wine-plum outline
        "S": (152, 26, 46, 255),    # Crimson shadow
        "M": (222, 50, 62, 255),    # Vibrant scarlet mid
        "H": (255, 104, 96, 255),   # Warm coral highlight
        "W": (255, 228, 220, 255),  # Soft rose specular
    },
    "cream": {
        "O": (62, 34, 44, 255),
        "S": (188, 166, 152, 255),
        "M": (234, 222, 208, 255),
        "H": (252, 248, 240, 255),
        "W": (255, 255, 255, 255),
    },
    "wood": {
        "O": (42, 20, 30, 255),
        "S": (108, 56, 36, 255),
        "M": (158, 92, 52, 255),
        "H": (204, 134, 78, 255),
        "W": (238, 182, 122, 255),
    },
    "green": {
        "O": (16, 42, 36, 255),     # Deep pine-teal outline
        "S": (24, 122, 72, 255),    # Emerald shadow
        "M": (52, 188, 96, 255),    # Lush emerald mid
        "H": (122, 238, 132, 255),  # Lime-mint highlight
        "W": (224, 255, 220, 255),  # Mint specular
    },
    "blue": {
        "O": (18, 26, 56, 255),     # Midnight indigo outline
        "S": (30, 88, 178, 255),    # Cobalt shadow
        "M": (54, 148, 238, 255),   # Sky-sapphire mid
        "H": (118, 212, 255, 255),  # Ice-cyan highlight
        "W": (228, 248, 255, 255),  # White-ice gleam
    },
    "purple": {
        "O": (32, 16, 54, 255),
        "S": (96, 38, 168, 255),
        "M": (148, 72, 232, 255),
        "H": (204, 136, 255, 255),
        "W": (246, 224, 255, 255),
    },
    "orange": {
        "O": (48, 20, 32, 255),
        "S": (178, 64, 20, 255),
        "M": (242, 112, 28, 255),
        "H": (255, 172, 62, 255),
        "W": (255, 236, 184, 255),
    },
    "steel": {
        "O": (22, 28, 46, 255),
        "S": (86, 100, 128, 255),
        "M": (142, 158, 188, 255),
        "H": (204, 218, 240, 255),
        "W": (246, 250, 255, 255),
    },
    "pink": {
        "O": (52, 16, 40, 255),
        "S": (176, 48, 112, 255),
        "M": (236, 92, 162, 255),
        "H": (255, 158, 208, 255),
        "W": (255, 232, 246, 255),
    },
}


def _grid_icon(rows: list[str], cmap: dict[str, tuple[int, int, int, int]]) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows[:16]):
        for x, ch in enumerate(row[:16]):
            if ch != "." and ch in cmap:
                px[x, y] = cmap[ch]
    return img


def build_cuboide_icon_sprites() -> dict[str, Image.Image]:
    """
    Hand-crafted 16x16 pixel-art icons in the exact Cuboide Iconic Shops / Icenary aesthetic
    (image-35..39). Every hotbar & menu item uses crisp 2D pixel art!
    """
    G = PALETTES["gold"]
    R = PALETTES["red"]
    C = PALETTES["cream"]
    WD = PALETTES["wood"]
    GR = PALETTES["green"]
    BL = PALETTES["blue"]
    PR = PALETTES["purple"]
    OR = PALETTES["orange"]
    ST = PALETTES["steel"]
    PK = PALETTES["pink"]

    sprites: dict[str, Image.Image] = {}

    # 1. CUBOIDE MARKET STALL ("icon_shop", "icon_shelves", "prop_shop_crate") — image-38.png top-left!
    # Scalloped red & cream striped awning + warm wooden counter + cozy glowing interior!
    shop_stall = _grid_icon(
        [
            "..OOOOOOOOOOOO..",
            ".ORRWCCRWWCRRSO.",
            "ORHHRWCHHRWCHRSO",
            "ORMMRCCMMRCCMRSO",
            "ORSSRCCSSRCCSRSO",
            ".OO.OO.OO.OO.OO.",
            ".OWGGGGGGGGGGDO.",
            ".OWGYYYYYYYYGDO.",
            ".OWGYOOOOOOYGDO.",
            ".OWHHHHHHHHHHDO.",
            "OOWWWWWWWWWWWWOO",
            "OHHHHHHHHHHHHHHO",
            "OMMMMMMMMMMMMMMO",
            "OSSSSSSSSSSSSSSO",
            ".OOOOOOOOOOOOOO.",
            "................",
        ],
        {
            "O": WD["O"],
            "R": R["M"], "H": R["H"], "S": R["S"],
            "C": C["H"], "W": C["W"],
            "G": (68, 40, 36, 255),
            "Y": (255, 206, 86, 255),
            "D": WD["S"],
            "M": WD["M"],
        },
    )
    # Refine wooden counter lower half with rich Cuboide plank grain & highlight!
    spx = shop_stall.load()
    for x in range(1, 15):
        spx[x, 10] = WD["O"]
        spx[x, 11] = WD["W"] if x < 6 else WD["H"]
        spx[x, 12] = WD["H"] if x < 5 else WD["M"]
        spx[x, 13] = WD["S"]
    for y in (6, 7, 8, 9):
        spx[2, y] = WD["H"]
        spx[13, y] = WD["S"]
    for k in ("icon_shop", "icon_shelves", "prop_shop_crate"):
        sprites[k] = shop_stall.copy()

    # 2. CUBOIDE DIAGONAL PRICE TAG ("icon_tags", "prop_tag_plaque") — image-38.png row 2 col 1!
    tag_icon = _grid_icon(
        [
            ".......OOOOOO...",
            "......OHHHHHHO..",
            ".....OHWOOOOHMO.",
            "....OHHO..OOHMO.",
            "...OHHHHHHHHMMO.",
            "..OHHHOOOMMMMMO.",
            ".OHHHO..OMMMSSO.",
            "OHHHHOOOOMMSSOO.",
            ".OHHHHHHMMSSOO..",
            "..OHHHHMMSSOO...",
            "...OHHMMSSOO....",
            "....OHMSSOO.....",
            ".....OSSOO......",
            "......OOO.......",
            "................",
            "................",
        ],
        {
            "O": G["O"],
            "W": C["W"],
            "H": (255, 218, 108, 255),
            "M": (236, 166, 52, 255),
            "S": (184, 104, 32, 255),
        },
    )
    # Add crisp crimson decorative stripe & string loop like Cuboide price tag!
    tpx = tag_icon.load()
    for i in range(4):
        tpx[4 + i, 8 + i] = R["M"]
        tpx[5 + i, 7 + i] = R["H"]
    tpx[13, 1] = ST["H"]
    tpx[14, 0] = ST["W"]
    for k in ("icon_tags", "prop_tag_plaque"):
        sprites[k] = tag_icon.copy()

    # 3. CUBOIDE TWIN STACKED GOLD COINS ("icon_coin_stack") — image-38.png row 1 col 3!
    coin_icon = _grid_icon(
        [
            "......OOOOOO....",
            "....OOHWWWWHOO..",
            "...OHWWMMMMHHMO.",
            "..OHWMOOOOOOMMO.",
            "..OHWMO.WW.OMSO.",
            ".OOOOOO.HH.OMSO.",
            "OHWWWWHOOOOMSSO.",
            "OHWMMMMHHHOOSSO.",
            "OHMOOOOOOMMMOOO.",
            "OHMO.WW.OMMSO...",
            "OHMO.HH.OMMSO...",
            "OHMMOOOOOMSSO...",
            ".OHMMMMMMSSOO...",
            "..OSSSSSSSOO....",
            "...OOOOOOOO.....",
            "................",
        ],
        {"O": G["O"], "W": G["W"], "H": G["H"], "M": G["M"], "S": G["S"]},
    )
    sprites["icon_coin_stack"] = coin_icon

    # 4. CUBOIDE LEATHER WALLET WITH GREEN BANKNOTES ("icon_chest") — image-38.png row 2 col 2!
    wallet_icon = _grid_icon(
        [
            "....OOOOOOOO....",
            "...OGGGGGGGGO...",
            "..OGKWWWWWWKGO..",
            ".OGGGGGGGGGGGGO.",
            "OOOOOOOOOOOOOOO.",
            "OWHHHHHHHHHHHSO.",
            "OHMMMMMMMMMMMSO.",
            "OHMMMMOOOOOOOSO.",
            "OHMMMOHGGGGHOSO.",
            "OHMMMOHGWWGHOSO.",
            "OHMMMMOOOOOOOSO.",
            "OHMMMMMMMMMMMSO.",
            "OSSSSSSSSSSSSSO.",
            ".OOOOOOOOOOOOOO.",
            "................",
            "................",
        ],
        {
            "O": WD["O"],
            "W": WD["W"], "H": WD["H"], "M": WD["M"], "S": WD["S"],
            "G": GR["M"], "K": GR["H"],
        },
    )
    # Gold clasp button on wallet strap
    wpx = wallet_icon.load()
    wpx[10, 9] = G["W"]
    wpx[11, 9] = G["H"]
    sprites["icon_chest"] = wallet_icon

    # 5. CUBOIDE CHUNKY GOLDEN ARROWS ("icon_prev", "icon_next") — image-38.png row 1 col 2 & image-35!
    next_arrow = _grid_icon(
        [
            "......OOO.......",
            "......OHHO......",
            "......OHWHO.....",
            "..OOOOOHWWHO....",
            ".OHHHHHHWWWHO...",
            "OHWWWWWWWWWWHO..",
            "OHWWWWWWWWWWMMO.",
            "OHHHHHHHHHMMMMMO",
            "OMMMMMMMMMMMSSSO",
            "OSSSSSSSSSSSSOO.",
            ".OOOOOOOMMMSSO..",
            "......OOMMSSO...",
            "......OOMSSO....",
            "......OOSSO.....",
            "......OOOO......",
            "................",
        ],
        {"O": G["O"], "W": G["W"], "H": G["H"], "M": G["M"], "S": G["S"]},
    )
    sprites["icon_next"] = next_arrow
    sprites["icon_prev"] = next_arrow.transpose(Image.Transpose.FLIP_LEFT_RIGHT)

    # 6. ICENARY CRIMSON CLOSE / BACK / LEAVE BADGE ("icon_leave", "icon_back") — image-36.png!
    close_x = _grid_icon(
        [
            "..OOO......OOO..",
            ".OHWHO....OHHHO.",
            "OHWWWHO..OHHHMMO",
            "OHWWWWHHOHHHMMMO",
            ".OHWWWWWWWWMMMO.",
            "..OHHWWWWWWMMO..",
            "...OHHWWWWMMO...",
            "...OHHWWWWMMO...",
            "..OHHHWWWWMMMO..",
            ".OHHHMMMMMMSSSO.",
            "OHHHMMMOOOMMSSSO",
            "OHHMMMO..OOMSSSO",
            ".OSSSSO..OOSSSO.",
            "..OOOO....OOOO..",
            "................",
            "................",
        ],
        {"O": R["O"], "W": R["W"], "H": R["H"], "M": R["M"], "S": R["S"]},
    )
    sprites["icon_leave"] = close_x
    sprites["icon_back"] = close_x.copy()

    # 7. CUBOIDE WINGED FLAMING PLAY BASKETBALL ("prop_play_emblem", "icon_arena")
    play_emblem = _grid_icon(
        [
            "....OOOOOOOO....",
            "..OOHWWHOOOHHO..",
            ".OHWWHHOOOOHMMO.",
            ".OHWHOOOOOOOMMO.",
            "OOHHOOHHHHHHOMOO",
            "OGGGOHWWOMMMOGGO",
            "OYYYOHOOOOOOOGGO",
            "OYYYOOOOOOOOOSSO",
            ".OYYOHMMOMSSOSO.",
            ".OO.OHMMOMSSO.O.",
            "....OOMMMSSOO...",
            ".....OOSSSOO....",
            "......OOOOO.....",
            "................",
            "................",
            "................",
        ],
        {
            "O": OR["O"], "W": OR["W"], "H": OR["H"], "M": OR["M"], "S": OR["S"],
            "Y": G["W"], "G": G["H"],
        },
    )
    # Clean crisp 14x14 Cuboide basketball with golden wings!
    play_emblem = _grid_icon(
        [
            ".....OOOOOO.....",
            "...OOHWWWWHOO...",
            "..OHWWOOOOOHMO..",
            ".OOHWOHHHHOMMOO.",
            "OYGOOOOOOOOOOGYO",
            "OYYOHHHHHHHMOYYO",
            "OYGOHWWOMMMMOYGO",
            ".OOOOOOOOOOOOOO.",
            "..OOHMMOMMMSSO..",
            "...OHMMOOOOSSO..",
            "...OOMMMMMSSOO..",
            "....OOSSSSSOO...",
            ".....OOOOOOO....",
            "................",
            "................",
            "................",
        ],
        {
            "O": OR["O"], "W": OR["W"], "H": OR["H"], "M": OR["M"], "S": OR["S"],
            "Y": G["W"], "G": G["M"],
        },
    )
    sprites["prop_play_emblem"] = play_emblem
    sprites["icon_arena"] = play_emblem.copy()

    # 8. CUBOIDE #23 PRO JERSEY ("prop_locker_jersey", "icon_portal")
    jersey = _grid_icon(
        [
            "..OOOO....OOOO..",
            ".OHWWHO..OHHHHO.",
            "OHWWWWHOOHHHHHMO",
            "OHWYYYYYYYYYYHMO",
            "OHHOOOOOOOOOOMMO",
            ".OOOHHHHHHHHMOO.",
            "..OHHWWOWWWHMO..",
            "..OHHWWOOWWHMO..",
            "..OHHWWOWWWHMO..",
            "..OHHWWOOWWHMO..",
            "..OHMMMMMMMMMO..",
            "..OHYYYYYYYYMO..",
            "..OSSSSSSSSSSO..",
            "...OOOOOOOOOO...",
            "................",
            "................",
        ],
        {
            "O": BL["O"], "W": BL["W"], "H": BL["H"], "M": BL["M"], "S": BL["S"],
            "Y": G["H"],
        },
    )
    sprites["prop_locker_jersey"] = jersey
    sprites["icon_portal"] = jersey.copy()

    # 9. CUBOIDE SQUAD / FRIENDS BUSTS ("icon_friends", "prop_social_megaphone")
    friends = _grid_icon(
        [
            "...OOOO..OOOO...",
            "..OHWWHOOHHHHO..",
            "..OHWWHOOHHHMO..",
            "..OHMMHOOMMMMO..",
            "...OOOO..OOOO...",
            ".OOGGGGOOBBBBOO.",
            "OOGKKKKGOBLBBBMO",
            "OGKGGGGKGBLLBBMO",
            "OGGGGGGGGBBBBBMO",
            "OGGGGGGGSBBBBBSSO",
            "OSSSSSSSOBSSSSSO",
            ".OOOOOOOOOOOOOO.",
            "................",
            "................",
            "................",
            "................",
        ],
        {
            "O": BL["O"],
            "W": (255, 226, 196, 255), "H": (240, 192, 152, 255), "M": (198, 142, 104, 255),
            "K": GR["W"], "G": GR["M"], "S": GR["S"],
            "L": BL["H"], "B": BL["M"],
        },
    )
    # Fix exact 16-char row length on row 9
    friends = _grid_icon(
        [
            "...OOOO..OOOO...",
            "..OHWWHOOHHHHO..",
            "..OHWWHOOHHHMO..",
            "..OHMMHOOMMMMO..",
            "...OOOO..OOOO...",
            ".OOGGGGOOBBBBOO.",
            "OOGKKKKGOBLBBBMO",
            "OGKGGGGKOBLLBBMO",
            "OGGGGGGGOBBBBBMO",
            "OGGGGGGSSBBBBSSO",
            "OSSSSSSSOBSSSSSO",
            ".OOOOOOOOOOOOOO.",
            "................",
            "................",
            "................",
            "................",
        ],
        {
            "O": BL["O"],
            "W": (255, 226, 196, 255), "H": (240, 192, 152, 255), "M": (198, 142, 104, 255),
            "K": GR["W"], "G": GR["M"], "S": GR["S"],
            "L": BL["H"], "B": BL["M"],
        },
    )
    sprites["icon_friends"] = friends
    sprites["prop_social_megaphone"] = friends.copy()

    # 10. CUBOIDE ROYAL GUIDE TOME ("icon_book", "prop_guide_clipboard") — image-36 & image-39!
    book = _grid_icon(
        [
            "..OOOOOOOOOOO...",
            ".OYHHHHHHHHHHO..",
            "OYYHWWWWWWWWHMO.",
            "OYGHWOOOOOOWHMO.",
            "OYGHWHHHHHHWHMO.",
            "OYGHWHYYYYHWHMO.",
            "OYGHWHYYYYHWHMO.",
            "OYGHWHHHHHHWHMO.",
            "OYGHMMMMMMMMMMO.",
            "OYGHMMMMMMMMSSO.",
            "OYGOCCCCCCCCCOO.",
            "OYYOCCCCCCCCCOO.",
            ".OYOOOOOOOOOOO..",
            "..OOOOOOOOOOO...",
            "................",
            "................",
        ],
        {
            "O": R["O"], "W": R["W"], "H": R["H"], "M": R["M"], "S": R["S"],
            "Y": G["H"], "G": G["M"], "C": C["H"],
        },
    )
    sprites["icon_book"] = book
    sprites["prop_guide_clipboard"] = book.copy()

    # 11. CUBOIDE GOLDEN CHAMPIONSHIP TROPHY ("icon_podium", "prop_podium_cup") — image-36!
    trophy = _grid_icon(
        [
            "..OOOOOOOOOOOO..",
            ".OHWWWWWWWWWWHO.",
            "OHHOHWMMMMMMHOHO",
            "OHMOHWMMMMMMHOMO",
            "OOMOHWWMMMMMHOMO",
            ".OO.OHWMMMMMO.OO",
            ".....OHMMMMO....",
            "......OHMMO.....",
            "......OHMMO.....",
            ".....OOHMMOO....",
            "....OHWWWWWHO...",
            "...OHMMMMMMMSSO.",
            "...OOOOOOOOOOOO.",
            "................",
            "................",
            "................",
        ],
        {"O": G["O"], "W": G["W"], "H": G["H"], "M": G["M"], "S": G["S"]},
    )
    sprites["icon_podium"] = trophy
    sprites["prop_podium_cup"] = trophy.copy()

    # 12. CUBOIDE GOLDEN HOURGLASS ("icon_history") — image-38.png row 3 col 2!
    hourglass = _grid_icon(
        [
            "..OOOOOOOOOOOO..",
            ".OGWWWWWWWWWWGO.",
            "..OOOOOOOOOOOO..",
            "...OCCCCCCCCO...",
            "...OCYYYYYYCO...",
            "....OCYYYYCO....",
            ".....OCYYCO.....",
            "......OCCO......",
            ".....OCYCCO.....",
            "....OCCYYCCO....",
            "...OCCYYYYCCO...",
            "...OYYYYYYYYO...",
            "..OOOOOOOOOOOO..",
            ".OGWWWWWWWWWWGO.",
            "..OOOOOOOOOOOO..",
            "................",
        ],
        {
            "O": WD["O"], "G": WD["H"], "W": G["H"],
            "C": BL["W"], "Y": G["M"],
        },
    )
    sprites["icon_history"] = hourglass

    # 13. ICENARY STEEL & GOLD GEAR ("icon_settings") — image-36.png!
    gear = _grid_icon(
        [
            ".....OOOOOO.....",
            "....OHWWWWHO....",
            "..OOOHWMMWHOOO..",
            ".OHWWHMMMMHWWHO.",
            ".OHWMOOOOOOMMMO.",
            "OOHMOOGGGGOOMMOO",
            "OHWMOOGWWGOOMMSO",
            "OHMMOOGWWGOOMSSO",
            "OOMMOOGGGGOOMSOO",
            ".OHMMOOOOOOMSSO.",
            ".OSSSSMMMMSSSSO.",
            "..OOOSMSSSSOOO..",
            "....OSSSSSSO....",
            ".....OOOOOO.....",
            "................",
            "................",
        ],
        {"O": ST["O"], "W": ST["W"], "H": ST["H"], "M": ST["M"], "S": ST["S"], "G": G["H"]},
    )
    sprites["icon_settings"] = gear

    # 14. CUBOIDE RADIANT GOLDEN STAR ("icon_star_shield") — image-39.png!
    star = _grid_icon(
        [
            "......OOOO......",
            ".....OHWWHO.....",
            ".....OHWWHO.....",
            ".OOOOOHWWHOOOOO.",
            "OHWWWWWWWWWWWWHO",
            "OHWWWWWWWWWWWWMO",
            ".OHHWWWWWWWWMMO.",
            "..OOHWWWWWWMMO..",
            "...OHWMMMMMMO...",
            "..OHWMMOOMMMMO..",
            "..OHMMO..OMMSO..",
            ".OHMSO....OSSO..",
            ".OOOO......OOOO.",
            "................",
            "................",
            "................",
        ],
        {"O": G["O"], "W": G["W"], "H": G["H"], "M": G["M"], "S": G["S"]},
    )
    sprites["icon_star_shield"] = star

    # 15. ICENARY CROSSED WRENCH & HAMMER ("court_wand") — OP Studio / Arena Architect!
    tools_icon = _grid_icon(
        [
            "..OOOO.....OOO..",
            ".OHWWHO...OGGGO.",
            "OHWOOHO..OGWWWGO",
            "OHWO.OO.OGWGGGGO",
            ".OHO..OOGWGGOOO.",
            "..OO..OGWGGGO...",
            ".....OGWGGHOO...",
            "....OGWGGOHWHO..",
            "...OGWGGO.OHWHO.",
            "..OGWGGO...OHMO.",
            ".OGWGGO.....OMO.",
            "OGWGGO.......OO.",
            "OGGGO...........",
            ".OOO............",
            "................",
            "................",
        ],
        {
            "O": ST["O"], "W": ST["W"], "H": ST["H"], "M": ST["M"],
            "G": G["M"],
        },
    )
    sprites["court_wand"] = tools_icon

    # 16. COURT GAMEPLAY 2D CUBOIDE ICONS ("icon_pass", "icon_crossover", "icon_defense", "icon_dunk", "icon_bot_on", "icon_bot_off")
    sprites["icon_pass"] = _grid_icon(
        [
            ".......OOOOOO...",
            ".....OOHWWWWHO..",
            "..OOOHWWOOOOOMO.",
            ".OCCCOWOHHHHOMMO",
            "OCCCCCWOOOOOOOOO",
            ".OCCCOWOHMMOMMSO",
            "..OOOOOOMMMSSSO.",
            ".......OOSSSSO..",
            "........OOOOO...",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        {"O": BL["O"], "C": BL["H"], "W": OR["W"], "H": OR["H"], "M": OR["M"], "S": OR["S"]},
    )

    sprites["icon_crossover"] = _grid_icon(
        [
            ".......OOOOO....",
            "......OYYYYO....",
            ".....OYYYYO.....",
            "....OYYYYOO.....",
            "...OYYYYYYYO....",
            "....OOOYYYYO....",
            "..OOOOOYYYOO....",
            ".OHWWWHYYOOOO...",
            "OHWWWWMMMMMMHO..",
            "OHHHHHHHHHHHMMO.",
            "OCCCCCCCCCCCCCO.",
            ".OOOOOOOOOOOOOO.",
            "................",
            "................",
            "................",
            "................",
        ],
        {"O": R["O"], "Y": G["W"], "W": R["W"], "H": R["H"], "M": R["M"], "C": C["W"]},
    )

    sprites["icon_defense"] = _grid_icon(
        [
            "..OOOOOOOOOOOO..",
            ".OHWWWWWWWWWWHO.",
            "OHWWBBBBBBBBWWMO",
            "OHWBBWWWWWWBBMMO",
            "OHWBBWHHHHHBBMMO",
            "OHWBBWHHHHHBBMMO",
            ".OHBBWHHHHHBBMO.",
            ".OHBBBHHHHBBBMO.",
            "..OHBBBBBBBBMO..",
            "...OHBBBBBBSSO..",
            "....OHBBBBSSO...",
            ".....OSSSSSO....",
            "......OOOOO.....",
            "................",
            "................",
            "................",
        ],
        {"O": BL["O"], "W": ST["W"], "H": BL["H"], "B": BL["M"], "M": ST["M"], "S": ST["S"]},
    )

    # Dunk styles & icon_dunk: 5 distinct vibrant Cuboide Aerial Dunk Medallions!
    dunk_specs = [
        ("icon_dunk",         OR, G["W"]),
        ("dunk_tomahawk",     R,  G["H"]),
        ("dunk_windmill",     BL, C["W"]),
        ("dunk_spin_360",     PR, G["W"]),
        ("dunk_between_legs", GR, G["W"]),
        ("dunk_meteor_jam",   OR, R["H"]),
    ]
    for d_id, pal, spark in dunk_specs:
        sprites[d_id] = _grid_icon(
            [
                "....OOOOOO......",
                "...OKWWWWKO.....",
                "..OKWOOOOOKO....",
                "..OKOOOOOOKO....",
                "...OKMMMMKO.....",
                ".OOOOOOOOOOOOOO.",
                "ORRRRRRRRRRRRRRO",
                ".OOOWWWWWWWWOOO.",
                "..OW.W.WW.W.WO..",
                "...OW.WWWW.WO...",
                "...OW.W..W.WO...",
                "....OWWWWWWO....",
                "................",
                "................",
                "................",
                "................",
            ],
            {
                "O": pal["O"], "W": C["W"], "K": spark,
                "M": pal["M"], "R": R["H"],
            },
        )

    # Bot ON / Bot OFF Cuboide Cyber-Visor Heads
    for b_id, visor_col, rim_pal in (("icon_bot_on", GR["H"], GR), ("icon_bot_off", ST["M"], ST)):
        sprites[b_id] = _grid_icon(
            [
                "...OOOOOOOOOO...",
                "..OHWWWWWWWWHO..",
                ".OHWMMMMMMMMMMO.",
                ".OHMOOOOOOOOOMO.",
                ".OHMOVVVVVVVOMO.",
                ".OHMOVVVVVVVOMO.",
                ".OHMOOOOOOOOOMO.",
                ".OHMMMMMMMMMMSO.",
                "..OHMMOOOOOMSO..",
                "...OSSSSSSSSO...",
                "....OOOOOOOO....",
                "................",
                "................",
                "................",
                "................",
                "................",
            ],
            {"O": rim_pal["O"], "W": ST["W"], "H": ST["H"], "M": ST["M"], "S": ST["S"], "V": visor_col},
        )

    # Extra Cuboide / Icenary Utility Icons for filter tabs & controls!
    sprites["icon_check"] = _grid_icon(
        [
            "...........OOO..",
            "..........OHWHO.",
            ".........OHWWWHO",
            "........OHWWWWMO",
            "..OOO..OHWWWWMO.",
            ".OHWHOOHWWWWMO..",
            "OHWWWHHWWWWMO...",
            "OHWWWWWWWWMO....",
            ".OHWWWWWWMO.....",
            "..OHWWWWSSO.....",
            "...OHWWSSO......",
            "....OSSSO.......",
            ".....OOO........",
            "................",
            "................",
            "................",
        ],
        {"O": GR["O"], "W": GR["W"], "H": GR["H"], "M": GR["M"], "S": GR["S"]},
    )

    sprites["icon_gem_ruby"] = _grid_icon(
        [
            "....OOOOOOOO....",
            "..OOHWWWWWWHOO..",
            ".OHWWHHHHHHHMMO.",
            "OHWWHWWWWWWWHMMO",
            "OHHHHHHHHHHHHMMO",
            ".OHMMMMMMMMMMSO.",
            "..OHMMMMMMMMSO..",
            "...OHMMMMMMSO...",
            "....OHMMMMSSO...",
            ".....OHMMSSO....",
            "......OSSSO.....",
            ".......OOO......",
            "................",
            "................",
            "................",
            "................",
        ],
        {"O": PR["O"], "W": PR["W"], "H": PR["H"], "M": PR["M"], "S": PR["S"]},
    )

    return sprites


# ============================================================================
# 2. CUBOIDE NON-SQUISHED 16x16 COLLECTOR TAG EMBLEMS (Requirement #8!)
#    Replaces all 56 ugly squished tag_card_<id> icons from image-30.png with
#    crisp 16x16 Cuboide-style Collector Tag Ribbons & Emblem Seals!
# ============================================================================
TAG_MOTIF_PATTERNS = {
    "crown": [
        ".Y...Y...Y.",
        ".YY.YYY.YY.",
        ".YYYYYYYYY.",
        ".YWRYYYRGY.",
        "..GGGGGGG..",
    ],
    "flame": [
        ".....W.....",
        "....WYW....",
        "...WYYYW...",
        "..YYYYYYY..",
        "...GGGGGG..",
    ],
    "star": [
        ".....W.....",
        "....WWW....",
        ".WWWWWWWWW.",
        "...YYYYY...",
        "..YY...YY..",
    ],
    "bolt": [
        ".....WWWW..",
        "....WWWW...",
        "...WWWWWW..",
        "....YYYY...",
        "...YY......",
    ],
    "gem": [
        "...WWWWW...",
        "..WYYYYYW..",
        "..YYYYYYY..",
        "...GGGGG...",
        "....GGG....",
    ],
    "shield": [
        "..WWWWWWW..",
        "..WYYYYYW..",
        "..WYYYYYW..",
        "...GGGGG...",
        "....GGG....",
    ],
}


def generate_cuboide_tag_emblem_items(font_tex_dir: Path, item_tex_dir: Path, model_dir: Path, item_def_dir: Path):
    """
    Generates crisp 16x16 Cuboide-style Collector Icons for every tag_card_<id> using
    6 distinct Cuboide silhouettes (Shield Crest, Diagonal Boutique Tag, Faceted Gem,
    Royal Crown, Flaming Seal, Radiant Star + Custom Golden Pineapple for MYPINEAPPLE!).
    Zero squished text — full unsquished pill renders cleanly in the hover tooltip!
    """
    SHAPES = {
        "pineapple": [
            "......OOOO......",
            "....OOGKKGOO....",
            "...OGKGGGGKGO...",
            "....OOGGGGOO....",
            "....OOOOOOOO....",
            "...OHWYYYYWHO...",
            "..OHWYOOOOYWHO..",
            "..OHYYOYYOYYMO..",
            "..OHYOOOOOOYMO..",
            "..OHYYOYYOYYMO..",
            "..OHMMOOOOOSSO..",
            "...OHMMMMMSSO...",
            "....OSSSSSSO....",
            ".....OOOOOO.....",
            "................",
            "................",
        ],
        "crown": [
            "................",
            "..OO...OO...OO..",
            ".OWHO.OWHO.OHMO.",
            ".OWWHOHWWHHHMMO.",
            ".OWWWHWWWWWHMMO.",
            ".OHWWWWWWWWWMMSO",
            ".OHWRRWWWGGWMMSO",
            ".OHWWWWWWWWWMMSO",
            "..OHMMMMMMMMSSO.",
            "..OYYYYYYYYYYSO.",
            "..OSSSSSSSSSSSO.",
            "...OOOOOOOOOOO..",
            "................",
            "................",
            "................",
            "................",
        ],
        "shield": [
            "..OOOOOOOOOOOO..",
            ".OHWWWWWWWWWWHO.",
            "OHWWYYYYYYYYWWMO",
            "OHWYYHHHHHHYYMMO",
            "OHWYHHWWWWWHYMMSO",
            "OHWYHHWYYWWHYMMSO",
            ".OHYHHWWWWWHYMMSO",
            ".OHYYHHHHHHYYMSO.",
            "..OHYYYYYYYYMSO..",
            "...OHMMMMMMMSSO..",
            "....OHMMMMMSSO...",
            ".....OSSSSSSO....",
            "......OOOOOO.....",
            "................",
            "................",
            "................",
        ],
        "gem": [
            "....OOOOOOOO....",
            "..OOHWWWWWWHOO..",
            ".OHWWHHHHHHHMMO.",
            "OHWWHWWWWWWWHMMO",
            "OHWWWHHHHHHHMMMO",
            ".OHHMMMMMMMMMSO.",
            "..OHMMMMMMMMSO..",
            "...OHMMMMMMSSO..",
            "....OHMMMMSSO...",
            ".....OHMMSSO....",
            "......OSSSO.....",
            ".......OOO......",
            "................",
            "................",
            "................",
            "................",
        ],
        "tag": [
            ".......OOOOOO...",
            "......OHWWWWHO..",
            ".....OHWOOOOHMO.",
            "....OHWHO..OOHMO",
            "...OHWWWHHHHMMMO",
            "..OHWWYYYYYMMMMO",
            ".OHWWYYYYYMMSSSO",
            "OHWWWHHHHHMMSSOO",
            ".OHWWMMMMMMSSOO.",
            "..OHWMMMMMSSOO..",
            "...OHMMMMSSOO...",
            "....OMMMSSOO....",
            ".....OSSSOO.....",
            "......OOOO......",
            "................",
            "................",
        ],
        "star": [
            "......OOOO......",
            ".....OHWWHO.....",
            ".....OHWWHO.....",
            ".OOOOOHWWHOOOOO.",
            "OHWWWWWWWWWWWWHO",
            "OHWWWWYYYYWWWWMO",
            ".OHHWWYYYYWWMMO.",
            "..OOHWYYYYWMMO..",
            "...OHWMMMMMMO...",
            "..OHWMMOOMMMMO..",
            "..OHMMO..OMMSO..",
            ".OHMSO....OSSO..",
            ".OOOO......OOOO.",
            "................",
            "................",
            "................",
        ],
        "flame": [
            ".......OO.......",
            "......OWHO......",
            ".....OWWWHO.....",
            "....OWWYYWHO....",
            "...OWWYYYYWHO...",
            "..OHWYYYYYYHMO..",
            "..OHYYYYYYYYMO..",
            ".OHHYWWWWWWYMMO.",
            ".OHHYWWWWWWYMMSO",
            "..OHYYYYYYYYMSO.",
            "..OHMMMMMMMMSSO.",
            "...OSSSSSSSSSO..",
            "....OOOOOOOOO...",
            "................",
            "................",
            "................",
        ],
    }

    shape_cycle = ["tag", "shield", "gem", "star", "flame", "crown"]

    for idx, pill_path in enumerate(sorted(font_tex_dir.glob("tag_*.png"))):
        tag_id = pill_path.stem[len("tag_"):]
        item_id = f"tag_card_{tag_id}"

        src = Image.open(pill_path).convert("RGBA")
        spx = src.load()
        samples = []
        for y in range(src.height):
            for x in range(src.width):
                r, g, b, a = spx[x, y]
                if a > 80:
                    sat = max(r, g, b) - min(r, g, b)
                    if sat > 35 and max(r, g, b) > 85:
                        samples.append((sat + max(r, g, b), r, g, b))
        if samples:
            samples.sort(reverse=True)
            top = samples[: max(1, len(samples) // 5)]
            ar = sum(c[1] for c in top) // len(top)
            ag = sum(c[2] for c in top) // len(top)
            ab = sum(c[3] for c in top) // len(top)
        else:
            ar, ag, ab = 242, 168, 44

        out_c = (max(14, int(ar * 0.20)), max(12, int(ag * 0.16)), max(28, int(ab * 0.26)), 255)
        shd_c = (max(24, int(ar * 0.56)), max(22, int(ag * 0.52)), max(34, int(ab * 0.60)), 255)
        mid_c = (ar, ag, ab, 255)
        hi_c = (min(255, ar + 54), min(255, ag + 54), min(255, ab + 54), 255)
        spc_c = (min(255, ar + 115), min(255, ag + 115), min(255, ab + 115), 255)

        if tag_id == "mypineapple":
            chosen = "pineapple"
        elif tag_id in ("founder", "king", "royal", "dynasty", "champion", "mvp", "goat"):
            chosen = "crown"
        elif tag_id in ("inferno", "poster", "unstoppable", "supernova", "nova", "ballgod", "splash"):
            chosen = "flame"
        elif tag_id in ("lockdown", "clamp", "eclipse", "titan", "alpha", "omega"):
            chosen = "shield"
        elif tag_id in ("glacier", "iceveins", "frostbite", "galaxy", "prodigy", "sakura", "cyber"):
            chosen = "gem"
        elif tag_id in ("flash", "blitz", "star", "legend", "immortal", "showtime", "zenith"):
            chosen = "star"
        else:
            chosen = shape_cycle[idx % len(shape_cycle)]

        cmap = {
            "O": out_c,
            "S": shd_c,
            "M": mid_c,
            "H": hi_c,
            "W": spc_c,
            "Y": (255, 224, 92, 255),
            "G": (46, 188, 92, 255),
            "K": (132, 242, 142, 255),
            "R": (255, 72, 96, 255),
        }
        im = _grid_icon(SHAPES[chosen], cmap)

        im.save(item_tex_dir / f"{item_id}.png")
        model_json = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"bosketball:item/{item_id}"},
            "display": {
                "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
            },
        }
        (model_dir / f"{item_id}.json").write_text(json.dumps(model_json, indent=2))
        (item_def_dir / f"{item_id}.json").write_text(
            json.dumps(
                {
                    "model": {"type": "minecraft:model", "model": f"bosketball:item/{item_id}"},
                    "hand_animation_on_swap": False,
                },
                indent=2,
            )
        )


# ============================================================================
# 3. MODERNIZED GLOSSY HiDPI RANK PILL BADGES (Requirement #5 — image-28.png!)
#    Matches the exact glossy multi-stop collector quality of FOUNDER!
# ============================================================================
RANK_MINI_EMBLEMS = {
    "rookie": [
        "...W...",
        "..WWW..",
        "WWWWWWW",
        ".WWWWW.",
        ".WW.WW.",
        "W.....W",
        ".......",
    ],
    "hooper": [
        ".WWWWW.",
        "WOOOOOW",
        "WOOOOOW",
        "WWWWWWW",
        "WOOOOOW",
        ".WWWWW.",
        ".......",
    ],
    "pro": [
        "W.....W",
        "WW...WW",
        "WWW.WWW",
        ".WWWWW.",
        "..WWW..",
        "...W...",
        ".......",
    ],
    "allstar": [
        "...Y...",
        "..YYY..",
        "YYYYYYY",
        ".YWWWY.",
        ".YY.YY.",
        "Y.....Y",
        ".......",
    ],
    "superstar": [
        "..WWW..",
        ".WYYYW.",
        "WYYYYYW",
        ".YYYYY.",
        "..YYY..",
        "...Y...",
        ".......",
    ],
    "legend": [
        "...W...",
        "..WYW..",
        ".WYYYW.",
        "WYYYYYW",
        "WYYYYYW",
        ".YYYYY.",
        ".......",
    ],
    "mvp": [
        "W..W..W",
        "WW.W.WW",
        "WWWWWWW",
        "WYYYYYW",
        ".YYYYY.",
        ".......",
        ".......",
    ],
    "referee": [
        ".WWWWW.",
        "WYYYYYW",
        "WYYWWWW",
        ".WWWWW.",
        ".......",
        ".......",
        ".......",
    ],
    "admin": [
        "Y..Y..Y",
        "YY.Y.YY",
        "YYYYYYY",
        "YWWWWWY",
        ".YYYYY.",
        "..YYY..",
        ".......",
    ],
}


def generate_modern_hidpi_rank_pills(font_tex_dir: Path, draw_text_fn, measure_text_fn) -> dict[str, int]:
    """
    Renders all 9 Rank Pills (ROOKIE..ADMIN) + IGNIS/FROST Team Pills at 2x HiDPI (18px tall -> height: 9, ascent: 8)
    with a recessed emblem cap, bright glass top highlight, metallic trim & shadowed crisp typography
    matching FOUNDER's exact level of craft! Returns {filename: gui_advance_px}.
    """
    specs = [
        ("rank_rookie.png",    "ROOKIE",    "rookie",    (96, 110, 136), (52, 62, 82),   (168, 184, 212)),
        ("rank_hooper.png",    "HOOPER",    "hooper",    (38, 196, 108), (18, 104, 58),  (118, 248, 168)),
        ("rank_pro.png",       "PRO",       "pro",       (44, 152, 242), (20, 78, 152),  (132, 218, 255)),
        ("rank_allstar.png",   "ALL-STAR",  "allstar",   (242, 168, 32), (148, 82, 14),  (255, 228, 102)),
        ("rank_superstar.png", "SUPERSTAR", "superstar", (168, 72, 246), (88, 28, 154),  (228, 164, 255)),
        ("rank_legend.png",    "LEGEND",    "legend",    (238, 52, 82),  (132, 18, 42),  (255, 148, 168)),
        ("rank_mvp.png",       "MVP",       "mvp",       (250, 158, 24), (156, 68, 12),  (255, 238, 112)),
        ("rank_referee.png",   "REFEREE",   "referee",   (148, 166, 196),(74, 88, 114),  (236, 244, 255)),
        ("rank_admin.png",     "ADMIN",     "admin",     (228, 36, 58),  (118, 14, 32),  (255, 212, 72)),
        ("team_ignis.png",     "IGNIS",     "legend",    (234, 62, 42),  (128, 22, 18),  (255, 164, 96)),
        ("team_frost.png",     "FROST",     "superstar", (38, 162, 244), (18, 78, 148),  (148, 232, 255)),
    ]

    advances: dict[str, int] = {}
    for fname, label, emb_key, top_c, bot_c, rim_c in specs:
        tw = measure_text_fn(label, scale=2)
        emb_box_w = 18
        w = emb_box_w + tw + 14
        if w % 2 == 1:
            w += 1
        h = 18
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        px = img.load()

        out_dark = (max(10, bot_c[0] // 3), max(8, bot_c[1] // 3), max(16, bot_c[2] // 3), 255)
        for y in range(h):
            t = y / (h - 1)
            r = int(top_c[0] * (1.0 - t) + bot_c[0] * t)
            g = int(top_c[1] * (1.0 - t) + bot_c[1] * t)
            b = int(top_c[2] * (1.0 - t) + bot_c[2] * t)
            for x in range(w):
                dx = min(x, w - 1 - x)
                dy = min(y, h - 1 - y)
                if dx + dy < 2:
                    continue
                if dx == 0 or dy == 0 or (dx == 1 and dy == 1):
                    px[x, y] = out_dark
                elif y == 1:
                    px[x, y] = (*rim_c, 255)
                elif y == 2 and x > emb_box_w + 2:
                    px[x, y] = (min(255, top_c[0] + 55), min(255, top_c[1] + 55), min(255, top_c[2] + 55), 255)
                elif y == h - 2:
                    px[x, y] = (max(12, bot_c[0] - 22), max(12, bot_c[1] - 22), max(18, bot_c[2] - 22), 255)
                elif x < emb_box_w + 2:
                    px[x, y] = (max(14, int(r * 0.52)), max(14, int(g * 0.52)), max(22, int(b * 0.56)), 255)
                elif x == emb_box_w + 2:
                    px[x, y] = (*rim_c, 210)
                else:
                    px[x, y] = (r, g, b, 255)

        # Draw crisp 2x HiDPI 7x6 mini emblem inside left socket [3..17, 3..15] (matches FOUNDER 2x icon scale!)
        pat = RANK_MINI_EMBLEMS[emb_key]
        ec = {
            "W": (255, 255, 255, 255),
            "Y": (*rim_c, 255),
            "O": (244, 128, 36, 255),
        }
        for py_i, row in enumerate(pat[:6]):
            for px_i, ch in enumerate(row[:7]):
                if ch in ec:
                    for sy in range(2):
                        for sx in range(2):
                            gx = 3 + px_i * 2 + sx
                            gy = 3 + py_i * 2 + sy
                            if 2 <= gx < emb_box_w + 1 and 2 <= gy < h - 2:
                                px[gx, gy] = ec[ch]

        draw_text_fn(
            img,
            label,
            x=emb_box_w + 6,
            y=4,
            color=(255, 255, 255, 255),
            shadow=(max(8, bot_c[0] // 3), max(8, bot_c[1] // 3), max(14, bot_c[2] // 3), 235),
            scale=2,
        )
        pin_width(img).save(font_tex_dir / fname)
        advances[fname] = (w // 2) + 1
    return advances


# ============================================================================
# 4. LARGE 48x40 3D BASKETBALL HOVER PREVIEW STRIPS (Requirement #6!)
#    8 skins x 4 seamless 10px-tall rows (\uE320..\uE33F) for Tooltip Hover!
# ============================================================================
BALL_PREVIEW_ORDER = [
    ("classic", (244, 124, 34)),
    ("street",  (148, 163, 184)),
    ("neon",    (38, 224, 255)),
    ("gold",    (255, 206, 54)),
    ("sakura",  (255, 132, 192)),
    ("money",   (74, 222, 128)),
    ("galaxy",  (168, 85, 247)),
    ("fire",    (255, 92, 36)),
]


def generate_large_ball_hover_previews(item_tex_dir: Path, font_tex_dir: Path, render_iso_fn) -> list[dict]:
    """
    Renders a large 48x40px collector showcase portrait of every 3D cubic Basketball skin,
    sliced into 4 seamless 10px-tall bitmap strips (\uE320..\uE33F) for mouse-hover lore!
    """
    providers = []
    base_cp = 0xE320

    for s_idx, (skin, accent) in enumerate(BALL_PREVIEW_ORDER):
        if skin == "fire":
            so = Image.open(item_tex_dir / "ball_fire_south.png").crop((0, 0, 128, 128))
            ea = Image.open(item_tex_dir / "ball_fire_east.png").crop((0, 0, 128, 128))
            up = Image.open(item_tex_dir / "ball_fire_up.png").crop((0, 0, 128, 128))
        else:
            so = Image.open(item_tex_dir / f"ball_{skin}_south.png")
            ea = Image.open(item_tex_dir / f"ball_{skin}_east.png")
            up = Image.open(item_tex_dir / f"ball_{skin}_up.png")

        card = Image.new("RGBA", (44, 40), (0, 0, 0, 0))
        d = ImageDraw.Draw(card)
        # Sleek dark-obsidian showcase pedestal frame with skin-colored neon rim
        d.rectangle([1, 1, 42, 38], fill=(12, 16, 26, 245), outline=(*accent, 235), width=1)
        d.rectangle([2, 2, 41, 37], outline=(28, 36, 56, 220), width=1)
        # Soft radial spotlight behind the 3D basketball
        cpx = card.load()
        for y in range(3, 37):
            for x in range(3, 41):
                dist = math.hypot(x - 21.5, y - 19.5)
                if dist < 16.5:
                    glow = (1.0 - dist / 16.5) ** 1.8 * 0.42
                    r0, g0, b0, a0 = cpx[x, y]
                    cpx[x, y] = (
                        min(255, int(r0 + accent[0] * glow)),
                        min(255, int(g0 + accent[1] * glow)),
                        min(255, int(b0 + accent[2] * glow)),
                        245,
                    )

        iso = render_iso_fn(so, ea, up, out_size=34)
        card.alpha_composite(iso, (5, 3))

        # Corner collector rivets
        for rx, ry in ((3, 3), (40, 3), (3, 36), (40, 36)):
            cpx[rx, ry] = (255, 226, 118, 255)

        for row in range(4):
            strip = card.crop((0, row * 10, 44, (row + 1) * 10))
            fname = f"ball_preview_{skin}_{row}.png"
            pin_width(strip).save(font_tex_dir / fname)
            cp_ch = chr(base_cp + s_idx * 4 + row)
            providers.append({
                "type": "bitmap",
                "file": f"bosketball:font/{fname}",
                "height": 10,
                "ascent": 9,
                "chars": [cp_ch],
            })

    return providers


# ============================================================================
# 5. CYTOOXIEN-GRADE CUSTOM SCULPTED CONTAINER GUI OVERLAYS (image-32..34!)
#    Rendered at (0, 0) of generic_54 (176px wide x 136px tall) via title shift(-8):
#      - Solid sculpted sections, tabs, banners & dividers over unused/separator slots
#      - 100% transparent 16x16 slot windows + custom beveled rims on interactive slots!
# ============================================================================
def _build_cytooxien_overlay(
    active_slots: dict[int, tuple[int, int, int]],
    banners: list[tuple[int, int, int, int, str, tuple[int, int, int]]],
    top_title: str,
    top_accent: tuple[int, int, int],
    draw_text_fn,
    measure_text_fn,
    rows_count: int = 6,
) -> Image.Image:
    """
    Constructs a 176x(18 + rows_count*18 + 6) custom container overlay:
      - Covers non-interactive vanilla slots completely with sleek Cytooxien sculpted slate panels
      - Carves exact 16x16 transparent item windows [8+c*18 .. 23+c*18, 18+r*18 .. 33+r*18]
        framed by 1px color-coded beveled bezels!
      - Draws custom horizontal section banners & top architectural title bar!
    """
    w = 176
    h = 18 + rows_count * 18 + 4
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 1. Architectural sculpted outer deck covering header + chest area [0..175, 0..h-1]
    d.rectangle([0, 0, w - 1, h - 1], fill=(15, 19, 30, 255), outline=(7, 9, 16, 255), width=1)
    d.rectangle([1, 1, w - 2, h - 2], outline=(52, 66, 96, 255), width=1)
    # Golden corner studs (Cytooxien / Modern Look signature detail!)
    for cx_s, cy_s in ((2, 2), (w - 4, 2), (2, h - 4), (w - 4, h - 4)):
        d.rectangle([cx_s, cy_s, cx_s + 1, cy_s + 1], fill=(*top_accent, 255))
    # Recessed dark interior bay [5..170, 15..h-4]
    d.rectangle([5, 15, w - 6, h - 4], fill=(10, 13, 21, 255), outline=(36, 48, 72, 255), width=1)
    # Sculpted vertical side trim rails along left & right walls
    d.line([(3, 18), (3, h - 6)], fill=(*top_accent, 140))
    d.line([(w - 4, 18), (w - 4, h - 6)], fill=(*top_accent, 140))

    # 2. Top Sculpted Title Header Bar [3..172, 2..14]
    d.rectangle([3, 2, w - 4, 14], fill=(20, 26, 42, 255), outline=(54, 68, 98, 255), width=1)
    d.line([(4, 2), (w - 5, 2)], fill=(*top_accent, 255))
    # Centered crisp header title
    tw = measure_text_fn(top_title, scale=1)
    tx = (w - tw) // 2
    draw_text_fn(img, top_title, tx, 6, color=(255, 248, 228, 255), shadow=(8, 10, 18, 240), scale=1)
    # Accent flanking diamonds
    d.rectangle([tx - 6, 7, tx - 4, 9], fill=(*top_accent, 255))
    d.rectangle([tx + tw + 4, 7, tx + tw + 6, 9], fill=(*top_accent, 255))

    # 3. Custom Section Banners / Sub-Panels (Cytooxien split-panel dividers!)
    for x0, y0, x1, y1, label, accent in banners:
        d.rectangle([x0, y0, x1, y1], fill=(18, 24, 38, 255), outline=(44, 56, 82, 255), width=1)
        d.line([(x0 + 1, y0), (x1 - 1, y0)], fill=(*accent, 220))
        if label:
            lw = measure_text_fn(label, scale=1)
            lx = x0 + ((x1 - x0 + 1) - lw) // 2
            ly = y0 + ((y1 - y0 + 1) - 5) // 2
            draw_text_fn(img, label, lx, ly, color=(*accent, 255), shadow=(8, 10, 18, 240), scale=1)

    # 4. Carve 16x16 transparent item windows + custom beveled slot bezels for EVERY active slot!
    px = img.load()
    for slot, rim_rgb in active_slots.items():
        col = slot % 9
        row = slot // 9
        if row >= rows_count:
            continue
        sx = 8 + col * 18
        sy = 18 + row * 18
        # Outer 18x18 beveled slot bezel [sx-1..sx+16, sy-1..sy+16]
        hi_rim = (min(255, rim_rgb[0] + 35), min(255, rim_rgb[1] + 35), min(255, rim_rgb[2] + 35), 255)
        lo_rim = (max(16, int(rim_rgb[0] * 0.55)), max(18, int(rim_rgb[1] * 0.55)), max(28, int(rim_rgb[2] * 0.60)), 255)
        for bx in range(sx - 1, sx + 17):
            px[bx, sy - 1] = hi_rim
            px[bx, sy + 16] = lo_rim
        for by in range(sy - 1, sy + 17):
            px[sx - 1, by] = rim_rgb + (255,)
            px[sx + 16, by] = lo_rim
        # Carve 100% transparent 16x16 window so item icon, stack count & hover highlight shine cleanly!
        for wy in range(sy, sy + 16):
            for wx in range(sx, sx + 16):
                px[wx, wy] = (0, 0, 0, 0)

    return pin_width(img)


def generate_cytooxien_gui_overlays(font_tex_dir: Path, draw_text_fn, measure_text_fn) -> list[dict]:
    """
    Generates 6 Cytooxien-Grade Custom Sculpted Container Overlays (\uE350..\uE355):
      - \uE350: Shop (Organized Shelves)
      - \uE351: Shop (Category Grid Showcase)
      - \uE352: Collector Chat Tags Vault (Rarity Tabs + 7x4 Framed Pedestals)
      - \uE353: Basketballs & Dunks Studio (Split Upper Ball Bay + Lower Dunk Bay)
      - \uE354: Game Modes Selector (Split Ranked Bay + Solo/3PT/Live Bay)
      - \uE355: BosketBall Main Hub (Spotlight Locker + Action Bay + Squad Dock)
    """
    GOLD = (245, 184, 46)
    CYAN = (56, 189, 248)
    MINT = (74, 222, 128)
    ORANGE = (255, 140, 26)
    PINK = (236, 102, 178)
    PURPLE = (168, 92, 246)
    SLATE = (74, 92, 128)

    overlays = []

    # 1. \uE350 — SHOP (SHELVES OVERVIEW, 54 slots)
    shop_shelves_slots: dict[int, tuple[int, int, int]] = {
        0: GOLD, 2: GOLD, 3: ORANGE, 4: PINK, 5: CYAN, 8: PURPLE,
        9: ORANGE, 18: PINK, 27: CYAN,
        45: GOLD, 48: PINK, 49: (238, 72, 82), 50: CYAN, 53: GOLD,
    }
    for s in (10, 11, 12, 13, 14, 15, 16):
        shop_shelves_slots[s] = ORANGE
    for s in (19, 20, 21, 22, 23, 24, 25):
        shop_shelves_slots[s] = PINK
    for s in (28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43):
        shop_shelves_slots[s] = CYAN

    im_shop = _build_cytooxien_overlay(
        active_slots=shop_shelves_slots,
        banners=[
            (6, 16, 169, 35, "", GOLD),        # Top Tab & Wallet Bar
            (6, 35, 169, 53, "", ORANGE),      # Basketballs Shelf Rail
            (6, 53, 169, 71, "", PINK),        # Signature Dunks Shelf Rail
            (6, 71, 169, 107, "", CYAN),       # Collector Tags Double Shelf
            (6, 107, 169, 125, "", SLATE),     # Bottom Navigation Dock
        ],
        top_title="SHOP  -  ORGANIZED SHELVES",
        top_accent=GOLD,
        draw_text_fn=draw_text_fn,
        measure_text_fn=measure_text_fn,
        rows_count=6,
    )
    im_shop.save(font_tex_dir / "gui_overlay_shop.png")
    overlays.append(("gui_overlay_shop.png", "\uE350", im_shop.height))

    # 2. \uE351 — SHOP (CATEGORY SHOWCASE GRID, 54 slots)
    shop_grid_slots: dict[int, tuple[int, int, int]] = {
        0: GOLD, 2: GOLD, 3: ORANGE, 4: PINK, 5: CYAN, 8: PURPLE,
        45: GOLD, 48: PINK, 49: (238, 72, 82), 50: CYAN, 53: GOLD,
    }
    for r in (1, 2, 3, 4):
        for c in range(1, 8):
            shop_grid_slots[r * 9 + c] = GOLD
    im_shop_grid = _build_cytooxien_overlay(
        active_slots=shop_grid_slots,
        banners=[
            (6, 16, 169, 35, "", GOLD),
            (23, 35, 152, 107, "", GOLD),
            (6, 107, 169, 125, "", SLATE),
        ],
        top_title="SHOP  -  COLLECTION VAULT",
        top_accent=GOLD,
        draw_text_fn=draw_text_fn,
        measure_text_fn=measure_text_fn,
        rows_count=6,
    )
    im_shop_grid.save(font_tex_dir / "gui_overlay_shop_grid.png")
    overlays.append(("gui_overlay_shop_grid.png", "\uE351", im_shop_grid.height))

    # 3. \uE352 — COLLECTOR CHAT TAGS VAULT (image-33.png Cytooxien "Tes objets" style! 54 slots)
    # Row 0: Rarity Filter Folder Tabs (slots 1, 2, 3, 4, 5, 6) + Shop (8) + Wallet (0)
    # Rows 1..4: Framed 7x4 Collector Pedestal Grid (slots 10..16, 19..25, 28..34, 37..43 = 28 tags/page!)
    # Row 5: Prev (45), Unequip (48), Back (49), Balls & Dunks (50), Next (53)
    tag_vault_slots: dict[int, tuple[int, int, int]] = {
        0: GOLD,
        1: MINT, 2: SLATE, 3: CYAN, 4: PURPLE, 5: GOLD, 6: ORANGE,
        8: GOLD,
        45: GOLD, 48: (238, 72, 82), 49: (238, 72, 82), 50: PINK, 53: GOLD,
    }
    for r in (1, 2, 3, 4):
        for c in range(1, 8):
            tag_vault_slots[r * 9 + c] = CYAN
    im_tags = _build_cytooxien_overlay(
        active_slots=tag_vault_slots,
        banners=[
            (6, 16, 169, 35, "", CYAN),
            (23, 35, 152, 107, "", PURPLE),
            (6, 107, 169, 125, "", SLATE),
        ],
        top_title="COLLECTOR CHAT TAGS VAULT",
        top_accent=CYAN,
        draw_text_fn=draw_text_fn,
        measure_text_fn=measure_text_fn,
        rows_count=6,
    )
    im_tags.save(font_tex_dir / "gui_overlay_tags.png")
    overlays.append(("gui_overlay_tags.png", "\uE352", im_tags.height))

    # 4. \uE353 — BASKETBALLS & DUNKS LOCKER (54 slots with full-width sculpted section divider banner on Row 2!)
    # Row 0: Wallet (7), Shop (8) + left banner "3D SQUARE BASKETBALLS"
    # Row 1: All 8 Basketball Skins on 9, 10, 11, 12, 14, 15, 16, 17 (with center emblem on Row 1 col 4 covered!)
    # Row 2: Solid sculpted horizontal Cytooxien banner "MANUAL 2-CLICK SIGNATURE DUNKS" (zero items!)
    # Row 3: All 5 Dunk Styles on 29, 30, 31, 32, 33
    # Row 5: Chat Tags (48), Back (49), Shop (50)
    cos_slots: dict[int, tuple[int, int, int]] = {
        7: GOLD, 8: MINT,
        9: ORANGE, 10: ORANGE, 11: ORANGE, 12: ORANGE,
        14: ORANGE, 15: ORANGE, 16: ORANGE, 17: GOLD,
        29: PINK, 30: PINK, 31: PINK, 32: PINK, 33: PINK,
        48: CYAN, 49: (238, 72, 82), 50: MINT,
    }
    im_cos = _build_cytooxien_overlay(
        active_slots=cos_slots,
        banners=[
            (8, 19, 130, 33, "3D BASKETBALLS (HOVER!)", ORANGE),
            (8, 55, 167, 69, "MANUAL SIGNATURE DUNKS", PINK),
            (6, 107, 169, 125, "", SLATE),
        ],
        top_title="BASKETBALLS  &  DUNK STYLES",
        top_accent=PINK,
        draw_text_fn=draw_text_fn,
        measure_text_fn=measure_text_fn,
        rows_count=6,
    )
    im_cos.save(font_tex_dir / "gui_overlay_cosmetics.png")
    overlays.append(("gui_overlay_cosmetics.png", "\uE353", im_cos.height))

    # 5. \uE354 — PLAY MODES SELECTOR (image-32.png Cytooxien Split-Bay! 54 slots = 6 rows)
    # Row 0: Solid sculpted banner "COMPETITIVE RANKED QUEUES"
    # Row 1: 1v1 (10), 2v2 (12), 3v3 (14), 5v5 (16)
    # Row 2: Solid sculpted banner "TRAINING  -  3PT CONTEST  -  LIVE TV"
    # Row 3: Practice (28), 3PT Contest (30), Spectate Live (32), 3D Locker (34)
    # Row 5: Back (49)
    play_slots: dict[int, tuple[int, int, int]] = {
        10: ORANGE, 12: ORANGE, 14: ORANGE, 16: ORANGE,
        28: MINT, 30: GOLD, 32: CYAN, 34: PURPLE,
        49: (238, 72, 82),
    }
    im_play = _build_cytooxien_overlay(
        active_slots=play_slots,
        banners=[
            (8, 19, 167, 33, "COMPETITIVE RANKED QUEUES", ORANGE),
            (8, 55, 167, 69, "TRAINING  -  3PT  -  LIVE TV", CYAN),
            (6, 107, 169, 125, "", SLATE),
        ],
        top_title="PLAY BOSKETBALL  -  MODES",
        top_accent=ORANGE,
        draw_text_fn=draw_text_fn,
        measure_text_fn=measure_text_fn,
        rows_count=6,
    )
    im_play.save(font_tex_dir / "gui_overlay_play.png")
    overlays.append(("gui_overlay_play.png", "\uE354", im_play.height))

    # 6. \uE355 — MAIN HUB DASHBOARD (image-34.png Cytooxien Dashboard! 54 slots)
    # Note: slot 44 kept transparent bezel so OP studio renders cleanly when OP, and dark slot looks sleek for non-OP!
    hub_slots: dict[int, tuple[int, int, int]] = {
        13: CYAN,
        20: ORANGE, 21: CYAN, 22: GOLD, 23: PINK, 24: GOLD,
        29: PURPLE, 31: GOLD, 33: MINT,
        39: PURPLE, 40: SLATE, 41: GOLD, 44: SLATE,
    }
    im_hub = _build_cytooxien_overlay(
        active_slots=hub_slots,
        banners=[
            (8, 19, 74, 33, "3D LOCKER", CYAN),
            (101, 19, 167, 33, "PLAYER HUB", GOLD),
            (6, 89, 169, 107, "", SLATE),
        ],
        top_title="BOSKETBALL  -  MAIN MENU",
        top_accent=ORANGE,
        draw_text_fn=draw_text_fn,
        measure_text_fn=measure_text_fn,
        rows_count=6,
    )
    im_hub.save(font_tex_dir / "gui_overlay_hub.png")
    overlays.append(("gui_overlay_hub.png", "\uE355", im_hub.height))

    return [
        {
            "type": "bitmap",
            "file": f"bosketball:font/{fname}",
            "height": h_px,
            "ascent": 13,
            "chars": [cp],
        }
        for fname, cp, h_px in overlays
    ]
