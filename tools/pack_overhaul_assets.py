#!/usr/bin/env python3
"""
BosketBall 26.2 — Century-Edition Custom Assets Overhaul Module:
  - image-4.png: Seamless beveled horizontal chat divider & bracket caps (\uE110..\uE112)
  - image-5.png: 18 Glossy 3D-beveled #1/#2/#3 leaderboard pills (\uE220..\uE231)
                 + 28 Selectable Glossy Beveled Aura/Title tags with inline mini pixel icons (\uE240..\uE25B)
  - image-6.png: Slanted mint-neon 3D Locker-Room menu highlight bar (\uE130..\uE132)
  - image-7.png: Concentric glowing team floor aura rings (ring_ignis, ring_frost, ring_gold)
  - image-8.png: Golden ornate filigree flourish (\uE260) + 10 colored sidebar section pills (\uE261..\uE26A)
  - image-9.png: 2x HiDPI 5-strip vector-comic BOSKETBALL chat logo (\uE120..\uE124) + inline icons (\uE270..\uE274)
  - image-10.png: 19 bespoke 16x16 shaded pixel-art hotbar & GUI item icons + bosketball:ui_invisible
  - Custom Dark-Obsidian & Brushed-Gold NBA Container UI (assets/minecraft/textures/gui/container/generic_54.png)
  - 3D Articulated 6-Bone Hooper Rig (torso, shoulder-pivoted arm, hip-pivoted leg + sneaker) for Walk/Jump/Throw!
"""

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter


EXT_GLYPHS_3X5 = {
    "A": ["010", "101", "111", "101", "101"],
    "B": ["110", "101", "110", "101", "110"],
    "C": ["011", "100", "100", "100", "011"],
    "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"],
    "F": ["111", "100", "110", "100", "100"],
    "G": ["011", "100", "101", "101", "011"],
    "H": ["101", "101", "111", "101", "101"],
    "I": ["111", "010", "010", "010", "111"],
    "J": ["001", "001", "001", "101", "010"],
    "K": ["1001", "1010", "1100", "1010", "1001"],
    "L": ["100", "100", "100", "100", "111"],
    "M": ["10001", "11011", "10101", "10001", "10001"],
    "N": ["1001", "1101", "1011", "1001", "1001"],
    "O": ["0110", "1001", "1001", "1001", "0110"],
    "P": ["1110", "1001", "1110", "1000", "1000"],
    "Q": ["0110", "1001", "1001", "0110", "0011"],
    "R": ["1110", "1001", "1110", "1010", "1001"],
    "S": ["0111", "1000", "0110", "0001", "1110"],
    "T": ["111", "010", "010", "010", "010"],
    "U": ["1001", "1001", "1001", "1001", "0110"],
    "V": ["101", "101", "101", "101", "010"],
    "W": ["10001", "10001", "10101", "11011", "10001"],
    "X": ["101", "101", "010", "101", "101"],
    "Y": ["101", "101", "010", "010", "010"],
    "Z": ["111", "001", "010", "100", "111"],
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "011", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["011", "100", "111", "101", "111"],
    "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "110"],
    "#": ["01010", "11111", "01010", "11111", "01010"],
    "&": ["010", "101", "010", "101", "011"],
    "+": ["000", "010", "111", "010", "000"],
    "-": ["000", "000", "111", "000", "000"],
    "!": ["1", "1", "1", "0", "1"],
    ":": ["0", "1", "0", "1", "0"],
    ".": ["0", "0", "0", "0", "1"],
    " ": ["00", "00", "00", "00", "00"],
}


def pin_width(img: Image.Image) -> Image.Image:
    r, g, b, a = img.getpixel((img.width - 1, 0))
    if a == 0:
        img.putpixel((img.width - 1, 0), (255, 255, 255, 3))
    return img


def measure_text(text: str, scale: int = 1) -> int:
    total = 0
    for ch in text.upper():
        pat = EXT_GLYPHS_3X5.get(ch, EXT_GLYPHS_3X5[" "])
        total += (len(pat[0]) + 1) * scale
    return max(0, total - scale)


def draw_text(img: Image.Image, text: str, x: int, y: int, color=(255, 255, 255, 255), shadow=None, scale: int = 1) -> int:
    px = img.load()
    passes = [(1, 1, shadow), (0, 0, color)] if shadow else [(0, 0, color)]
    end_x = x
    for ox, oy, col in passes:
        cx = x
        for ch in text.upper():
            pat = EXT_GLYPHS_3X5.get(ch, EXT_GLYPHS_3X5[" "])
            w = len(pat[0])
            for ry, row in enumerate(pat):
                for rx, bit in enumerate(row):
                    if bit == "1":
                        for sy in range(scale):
                            for sx in range(scale):
                                dx, dy = cx + rx * scale + sx + ox, y + ry * scale + sy + oy
                                if 0 <= dx < img.width and 0 <= dy < img.height:
                                    px[dx, dy] = col
            cx += (w + 1) * scale
        end_x = cx
    return end_x - x


# ============================================================================
# 1. GLOSSY 3D-BEVELED BADGES (IMAGE-5.PNG) + 28 CUSTOM AURA/TITLE TAGS
# ============================================================================
MINI_ICONS_7X7 = {
    "trophy": (
        [
            ".YYYYY.",
            "GGGGGGG",
            "G.YYY.G",
            "..GGG..",
            "...G...",
            "..YYY..",
            ".BBBBB.",
        ],
        {"Y": (255, 238, 96, 255), "G": (245, 178, 32, 255), "B": (180, 112, 24, 255)},
    ),
    "crown": (
        [
            ".......",
            "W..W..W",
            "WW.W.WW",
            "WWWWWWW",
            "WYWYWYW",
            "GGGGGGG",
            ".......",
        ],
        {"W": (255, 252, 230, 255), "Y": (255, 212, 54, 255), "G": (218, 152, 22, 255)},
    ),
    "star": (
        [
            "...Y...",
            "..YYY..",
            "YYYYYYY",
            ".YYYYY.",
            "..YYY..",
            ".YY.YY.",
            "Y.....Y",
        ],
        {"Y": (255, 238, 92, 255)},
    ),
    "wings": (
        [
            "..YYY..",
            ".Y...Y.",
            "WW...WW",
            "WWW.WWW",
            ".WWWWW.",
            "..W.W..",
            ".......",
        ],
        {"Y": (255, 232, 88, 255), "W": (248, 252, 255, 255)},
    ),
    "ball": (
        [
            "SSSSSSS",
            "SHOOSOS",
            "SOSOSOS",
            "SSSSSSS",
            "SOSOSOS",
            "SOOOSOS",
            "SSSSSSS",
        ],
        {"O": (255, 134, 38, 255), "H": (255, 196, 98, 255), "S": (42, 28, 22, 255)},
    ),
    "fire": (
        [
            "...R...",
            "..RYR..",
            ".RYYYR.",
            "RYYWYYR",
            "RYYYYYR",
            ".OYYYO.",
            "..OOO..",
        ],
        {"R": (255, 82, 48, 255), "O": (255, 148, 34, 255), "Y": (255, 232, 78, 255), "W": (255, 255, 240, 255)},
    ),
    "bolt": (
        [
            "...WWW.",
            "..WYY..",
            ".WYY...",
            "WWWWWW.",
            "..YYW..",
            ".YYW...",
            "YY.....",
        ],
        {"W": (255, 255, 235, 255), "Y": (255, 222, 64, 255)},
    ),
    "shield": (
        [
            ".WWWWW.",
            "WCCCCWW",
            "WCCWCCW",
            "WCCCCWW",
            ".WCCCW.",
            "..WCW..",
            "...W...",
        ],
        {"W": (244, 250, 255, 255), "C": (86, 212, 255, 255)},
    ),
    "diamond": (
        [
            "..WWW..",
            ".WCCCW.",
            "WCCWCCW",
            "WCCCCCW",
            ".WCCCW.",
            "..WCW..",
            "...W...",
        ],
        {"W": (248, 254, 255, 255), "C": (98, 238, 255, 255)},
    ),
    "pineapple": (
        [
            ".G.L.G.",
            "..GGG..",
            ".YYYYY.",
            "YOYOYOY",
            "YYYYYYY",
            "YOYOYOY",
            ".YYYYY.",
        ],
        {
            "L": (122, 252, 116, 255),
            "G": (36, 198, 78, 255),
            "Y": (255, 228, 72, 255),
            "O": (218, 132, 18, 255),
        },
    ),
    "target": (
        [
            ".WWWWW.",
            "W..R..W",
            "W.RRR.W",
            "WRRWRRW",
            "W.RRR.W",
            "W..R..W",
            ".WWWWW.",
        ],
        {"W": (248, 252, 255, 255), "R": (255, 92, 92, 255)},
    ),
}


def make_glossy_beveled_pill(label: str, base_rgb, icon_kind: str = None) -> Image.Image:
    """
    Renders a 2x HiDPI (height=18px -> rendered at 9 GUI px!) glossy 3D beveled rounded pill badge (exact image-5.png!):
      - Smooth anti-aliased rounded 3px corners, crisp dark outer bezel, glassy top specular shine band,
        rich vertical body gradient, bottom shadow lip, 2x shaded mini icon + razor-sharp shadowed white text!
    """
    tw = measure_text(label, scale=2)
    icon_w = 16 if icon_kind else 0
    w = icon_w + tw + 14
    if w % 2 == 1:
        w += 1  # Even width guarantees exact integer GUI advance width!
    h = 18
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    r0, g0, b0 = base_rgb
    border_dark = (max(0, int(r0 * 0.26)), max(0, int(g0 * 0.26)), max(0, int(b0 * 0.30)), 255)
    top_shine   = (min(255, int(r0 * 1.38 + 56)), min(255, int(g0 * 1.38 + 56)), min(255, int(b0 * 1.38 + 56)), 255)
    top_soft    = (min(255, int(r0 * 1.18 + 26)), min(255, int(g0 * 1.18 + 26)), min(255, int(b0 * 1.18 + 26)), 255)
    bot_lip     = (max(0, int(r0 * 0.48)), max(0, int(g0 * 0.48)), max(0, int(b0 * 0.54)), 255)

    for y in range(h):
        t = y / (h - 1)
        cr = int(min(255, r0 * (1.14 - 0.36 * t)))
        cg = int(min(255, g0 * (1.14 - 0.36 * t)))
        cb = int(min(255, b0 * (1.14 - 0.36 * t)))
        for x in range(w):
            dx, dy = min(x, w - 1 - x), min(y, h - 1 - y)
            # Crisp 2x rounded bevel corner silhouette
            if (dx == 0 and dy <= 2) or (dx == 1 and dy <= 1) or (dx == 2 and dy == 0):
                continue
            is_border = (
                dx <= 1 or dy <= 1
                or (dx == 2 and dy <= 2)
                or (dx <= 2 and dy == 2)
            )
            if is_border:
                px[x, y] = border_dark
            elif y == 2 and 4 <= x <= w - 5:
                px[x, y] = top_shine
            elif y == 3 and 3 <= x <= w - 4:
                px[x, y] = top_soft
            elif y in (h - 3, h - 4) and 3 <= x <= w - 4:
                px[x, y] = bot_lip
            else:
                # Subtle diagonal glass sheen near left-top like image-5.png
                sheen = 16 if (x + y * 2) % 26 in (4, 5, 6) and y < 10 else 0
                px[x, y] = (min(255, cr + sheen), min(255, cg + sheen), min(255, cb + sheen), 255)

    tx = 6
    if icon_kind and icon_kind in MINI_ICONS_7X7:
        rows, cmap = MINI_ICONS_7X7[icon_kind]
        for iy, row in enumerate(rows):
            for ix, ch in enumerate(row):
                if ch in cmap:
                    col = cmap[ch]
                    for sy in range(2):
                        for sx in range(2):
                            py = 2 + iy * 2 + sy
                            px_x = tx + ix * 2 + sx
                            if 2 <= py < h - 2 and 2 <= px_x < w - 2:
                                px[px_x, py] = col
        tx += 16

    shadow_c = (max(0, int(r0 * 0.20)), max(0, int(g0 * 0.20)), max(0, int(b0 * 0.24)), 235)
    draw_text(img, label, tx, 4, color=(255, 255, 255, 255), shadow=shadow_c, scale=2)
    return pin_width(img)


# ============================================================================
# 2. LUXURY SIDEBAR FLOURISH & COLORED SECTION PILLS (IMAGE-8.PNG — 2x HiDPI!)
# ============================================================================
def make_sidebar_flourish() -> Image.Image:
    """236x18 (2x HiDPI -> 118x9 GUI px) pixel-exact CYTOOXIEN-style golden separator (exact 203241.png!):
    - Continuous uninterrupted golden-amber line across the center
    - Crisp square pixel-art scroll hooks [⊏ ─── ⊐] at both ends
    - Pixel-art 4-point golden cross-diamond finials (✦) flanking left & right
    - Positioned at y = 0..9 in 2x space so there is clean dark air above [JOUEUR] below!"""
    w, h = 236, 18
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gold_pale = (255, 248, 178, 255)
    gold_hi   = (255, 214, 48, 255)
    gold_mid  = (242, 162, 18, 255)
    gold_lo   = (148, 84, 10, 255)

    # Continuous uninterrupted golden horizontal rule [24 .. w-25] at y = 5..6
    d.rectangle([24, 5, w - 25, 6], fill=gold_hi)
    d.line([(24, 7), (w - 25, 7)], fill=gold_mid, width=1)

    for side in (0, 1):
        def sx(x_val):
            return x_val if side == 0 else (w - 1 - x_val)

        # Crisp square pixel-art inward scroll hook (exact 203241.png!)
        x_a, x_b = min(sx(18), sx(24)), max(sx(18), sx(24))
        d.rectangle([x_a, 5, x_b, 6], fill=gold_hi)
        y_col = min(sx(18), sx(19))
        d.rectangle([y_col, 1, y_col + 1, 6], fill=gold_hi)
        hx_a, hx_b = min(sx(18), sx(23)), max(sx(18), sx(23))
        d.rectangle([hx_a, 1, hx_b, 2], fill=gold_pale)
        tip_x = min(sx(22), sx(23))
        d.rectangle([tip_x, 2, tip_x + 1, 3], fill=gold_mid)

        # Crisp pixel-art 4-point golden cross-diamond finial (✦) at x = 9, y = 4
        cx, cy = sx(9), 4
        d.rectangle([cx - 1, cy - 4, cx + 1, cy + 4], fill=gold_mid)
        d.rectangle([cx - 4, cy - 1, cx + 4, cy + 1], fill=gold_mid)
        d.rectangle([cx - 2, cy - 2, cx + 2, cy + 2], fill=gold_hi)
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=gold_pale)
        img.putpixel((cx, cy + 5), gold_lo)

    return pin_width(img)


def make_sidebar_section_pill(label: str, fill_rgb) -> Image.Image:
    """2x HiDPI vibrant flat-modern colored tag pill matching 203241.png (PLAYER, RUBIS, KRISTALLE, TEMPS DE JEU, AMIS)."""
    tw = measure_text(label, scale=2)
    w = tw + 12
    if w % 2 == 1:
        w += 1
    h = 18
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    r, g, b = fill_rgb
    hi = (min(255, r + 26), min(255, g + 26), min(255, b + 26), 255)
    lo = (max(0, r - 28), max(0, g - 28), max(0, b - 28), 255)
    for y in range(2, h - 1):
        for x in range(w):
            if y == 2:
                px[x, y] = hi
            elif y == h - 2:
                px[x, y] = lo
            else:
                px[x, y] = (r, g, b, 255)
    draw_text(img, label, 6, 4, color=(255, 255, 255, 255), shadow=(max(0, int(r * 0.42)), max(0, int(g * 0.42)), max(0, int(b * 0.42)), 210), scale=2)
    return pin_width(img)


# ============================================================================
# 3. IMAGE-4.PNG SEAMLESS CHAT DIVIDERS & IMAGE-6.PNG SLANTED NEON SELECT BAR
# ============================================================================
def generate_chat_divider_sprites(font_tex_dir: Path) -> list:
    # 16x8 seamless metallic rule segment (advance 16; repeat with -1px shift for zero-gap line!)
    seg = Image.new("RGBA", (16, 8), (0, 0, 0, 0))
    sp = seg.load()
    for x in range(16):
        sp[x, 3] = (102, 114, 132, 255)  # Crisp steel top highlight
        sp[x, 4] = (52, 58, 70, 255)     # Dark iron core
        sp[x, 5] = (20, 22, 30, 235)     # Drop shadow
    seg.save(font_tex_dir / "divider_seg.png")

    cap_l = Image.new("RGBA", (7, 9), (0, 0, 0, 0))
    lp = cap_l.load()
    lp[0, 4] = (102, 114, 132, 255)
    lp[1, 4] = (102, 114, 132, 255)
    for y in range(1, 8):
        lp[2, y] = (118, 130, 148, 255)
        lp[3, y] = (36, 40, 52, 255)
    for x in (3, 4, 5):
        lp[x, 1] = (118, 130, 148, 255)
        lp[x, 7] = (68, 76, 92, 255)
    pin_width(cap_l).save(font_tex_dir / "divider_cap_l.png")

    cap_r = Image.new("RGBA", (7, 9), (0, 0, 0, 0))
    rp = cap_r.load()
    for x in (1, 2, 3):
        rp[x, 1] = (118, 130, 148, 255)
        rp[x, 7] = (68, 76, 92, 255)
    for y in range(1, 8):
        rp[3, y] = (118, 130, 148, 255)
        rp[4, y] = (36, 40, 52, 255)
    rp[5, 4] = (102, 114, 132, 255)
    rp[6, 4] = (102, 114, 132, 255)
    pin_width(cap_r).save(font_tex_dir / "divider_cap_r.png")

    return [
        {"type": "bitmap", "file": "bosketball:font/divider_seg.png", "height": 8, "ascent": 6, "chars": ["\uE110"]},
        {"type": "bitmap", "file": "bosketball:font/divider_cap_l.png", "height": 9, "ascent": 7, "chars": ["\uE111"]},
        {"type": "bitmap", "file": "bosketball:font/divider_cap_r.png", "height": 9, "ascent": 7, "chars": ["\uE112"]},
    ]


def generate_menu3d_highlight_sprites(font_tex_dir: Path) -> list:
    """Generates the 2x HiDPI slanted mint-emerald selection bar, side-by-side indigo buttons (image-6.png),
    AND custom graphical Center-Screen Tab header & player row cards (\uE140..\uE143)!"""
    # 248x22 PNG rendered at height=11 -> <254px vanilla FontTexture limit!
    w, h = 248, 22
    bar = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bp = bar.load()
    for y in range(h):
        slant_right = w - 2 - (y // 2)  # Crisp angled right edge like image-6.png!
        for x in range(slant_right):
            stripe = ((x + y * 2) % 18) < 9 and x > 116
            if y in (0, 1):
                bp[x, y] = (182, 255, 224, 255)
            elif y == h - 1:
                bp[x, y] = (38, 178, 112, 248)
            elif stripe:
                bp[x, y] = (132, 254, 198, 248)
            else:
                bp[x, y] = (82, 240, 162, 246)
    pin_width(bar).save(font_tex_dir / "menu3d_select_bar.png")

    # 2x HiDPI Compact Side-by-Side Indigo-Blurple Discord/Feedback button pill (168x22 -> 84x11 GUI px, exact image-6.png!)
    card = Image.new("RGBA", (168, 22), (0, 0, 0, 0))
    cp = card.load()
    for y in range(22):
        for x in range(168):
            dx, dy = min(x, 167 - x), min(y, 21 - y)
            if dx <= 1 and dy <= 1:
                continue
            if dy <= 1 or dx <= 1:
                cp[x, y] = (138, 152, 255, 248)
            elif y in (2, 3):
                cp[x, y] = (112, 126, 248, 244)
            else:
                cp[x, y] = (80, 94, 222, 242)
    pin_width(card).save(font_tex_dir / "menu3d_patch_pill.png")

    # Custom 2x HiDPI Center-Screen Tab Esports Header Plate (\uE140: 448x26 -> 224x13 GUI px)
    th_w, th_h = 448, 26
    th = Image.new("RGBA", (th_w, th_h), (0, 0, 0, 0))
    tp = th.load()
    for y in range(th_h):
        for x in range(th_w):
            dx, dy = min(x, th_w - 1 - x), min(y, th_h - 1 - y)
            if dx <= 1 and dy <= 1:
                continue
            if dy <= 1 or dx <= 1:
                tp[x, y] = (246, 194, 62, 255) if 126 <= x <= 322 else (68, 82, 112, 250)
            elif x < 126:
                tp[x, y] = (126, 26, 34, 242) if y > 4 else (192, 48, 54, 248)
            elif x > 322:
                tp[x, y] = (18, 72, 134, 242) if y > 4 else (36, 128, 212, 248)
            else:
                tp[x, y] = (20, 24, 36, 248)
    # Gold vertical dividers between IGNIS | BOSKETBALL TAB | FROST
    for y in range(2, th_h - 2):
        for sx_div in (126, 127, 321, 322):
            tp[sx_div, y] = (246, 194, 62, 255)
    draw_text(th, "IGNIS", 32, 8, color=(255, 224, 216, 255), shadow=(48, 12, 16, 230), scale=2)
    tw_mid = measure_text("BOSKETBALL TAB", scale=2)
    draw_text(th, "BOSKETBALL TAB", (th_w - tw_mid) // 2, 8, color=(255, 226, 82, 255), shadow=(36, 24, 8, 240), scale=2)
    tw_fr = measure_text("FROST", scale=2)
    draw_text(th, "FROST", th_w - tw_fr - 32, 8, color=(212, 244, 255, 255), shadow=(10, 28, 52, 230), scale=2)
    pin_width(th).save(font_tex_dir / "tab_header_card.png")

    # Custom 2x HiDPI Center-Screen Tab Graphical Player Row Bars (\uE141..\uE143: 448x20 -> 224x10 GUI px)
    row_specs = [
        ("tab_row_ignis.png", "\uE141", (255, 68, 58),  (72, 22, 28)),
        ("tab_row_frost.png", "\uE142", (46, 184, 255), (18, 46, 80)),
        ("tab_row_gold.png",  "\uE143", (255, 198, 52), (56, 42, 20)),
    ]
    row_providers = []
    for r_fname, r_ch, accent, tint in row_specs:
        rw, rh = 448, 20
        r_im = Image.new("RGBA", (rw, rh), (0, 0, 0, 0))
        rp = r_im.load()
        for y in range(rh):
            for x in range(rw):
                dx, dy = min(x, rw - 1 - x), min(y, rh - 1 - y)
                if dx <= 1 and dy <= 1:
                    continue
                if x < 8:
                    rp[x, y] = (*accent, 255)
                elif dy == 0 or dx == 0:
                    rp[x, y] = (62, 74, 98, 242)
                else:
                    mix = max(0.0, 1.0 - (x / 220.0))
                    rr = int(15 * (1.0 - mix) + tint[0] * mix)
                    rg = int(19 * (1.0 - mix) + tint[1] * mix)
                    rb = int(30 * (1.0 - mix) + tint[2] * mix)
                    rp[x, y] = (rr, rg, rb, 232)
        pin_width(r_im).save(font_tex_dir / r_fname)
        row_providers.append({"type": "bitmap", "file": f"bosketball:font/{r_fname}", "height": 10, "ascent": 8, "chars": [r_ch]})

    # Custom 2x HiDPI Center-Screen Tab Matching Footer Plate (\uE144: 448x20 -> 224x10 GUI px)
    tf = Image.new("RGBA", (448, 20), (0, 0, 0, 0))
    fp_px = tf.load()
    for y in range(20):
        for x in range(448):
            dx, dy = min(x, 447 - x), min(y, 19 - y)
            if dx <= 1 and dy <= 1:
                continue
            if y <= 1:
                fp_px[x, y] = (238, 186, 56, 250)
            elif dy == 0 or dx == 0:
                fp_px[x, y] = (58, 70, 96, 244)
            else:
                fp_px[x, y] = (16, 20, 32, 242)
    ft_txt = "BOSKETBALL  -  OFFICIAL SERVER"
    ft_w = measure_text(ft_txt, scale=2)
    draw_text(tf, ft_txt, (448 - ft_w) // 2, 5, color=(196, 212, 236, 255), shadow=(10, 14, 22, 230), scale=2)
    pin_width(tf).save(font_tex_dir / "tab_footer_card.png")

    return [
        {"type": "bitmap", "file": "bosketball:font/menu3d_select_bar.png", "height": 11, "ascent": 9,  "chars": ["\uE130"]},
        {"type": "bitmap", "file": "bosketball:font/menu3d_patch_pill.png", "height": 11, "ascent": 9,  "chars": ["\uE131"]},
        {"type": "bitmap", "file": "bosketball:font/tab_header_card.png",   "height": 13, "ascent": 11, "chars": ["\uE140\uE145"]},
        *[
            {**rp, "chars": [rp["chars"][0] + chr(ord(rp["chars"][0]) + 5)]}
            for rp in row_providers
        ],
        {"type": "bitmap", "file": "bosketball:font/tab_footer_card.png",   "height": 10, "ascent": 8,  "chars": ["\uE144\uE149"]},
    ]


# ============================================================================
# 4. IMAGE-9.PNG 2x HiDPI VECTOR-COMIC ILLUSTRATED LOGO + SHADED PIXEL ICONS
# ============================================================================
def _draw_vector_comic_letter(draw: ImageDraw.ImageDraw, ch: str, x: float, y: float, w: float, h: float, slant: float = 4.0):
    """
    Draws bold, clean, smooth-edged comic-book block letters using polygon strokes & cutouts
    at 2x HiDPI resolution so diagonals on K, A, B, O, S are razor-smooth (zero staircase blocks!).
    """
    t = max(5.5, w * 0.30)  # Bold comic stroke thickness

    def quad(x0, y0, x1, y1):
        # Apply slight athletic forward slant (top leans right by +slant)
        s0 = slant * (1.0 - (y0 - y) / max(1.0, h))
        s1 = slant * (1.0 - (y1 - y) / max(1.0, h))
        draw.polygon([(x0 + s0, y0), (x1 + s0, y0), (x1 + s1, y1), (x0 + s1, y1)], fill=255)

    def poly(pts):
        out = []
        for px_pt, py_pt in pts:
            s = slant * (1.0 - (py_pt - y) / max(1.0, h))
            out.append((px_pt + s, py_pt))
        draw.polygon(out, fill=255)

    def hole(pts):
        out = []
        for px_pt, py_pt in pts:
            s = slant * (1.0 - (py_pt - y) / max(1.0, h))
            out.append((px_pt + s, py_pt))
        draw.polygon(out, fill=0)

    mid_y = y + h * 0.48
    if ch == "B":
        poly([
            (x, y), (x + w * 0.84, y), (x + w, y + h * 0.14), (x + w, mid_y - 2),
            (x + w * 0.86, mid_y + 1), (x + w, mid_y + 4), (x + w, y + h * 0.86),
            (x + w * 0.84, y + h), (x, y + h),
        ])
        hole([(x + t, y + t * 0.82), (x + w - t * 0.95, y + t * 0.82), (x + w - t * 0.95, mid_y - t * 0.42), (x + t, mid_y - t * 0.42)])
        hole([(x + t, mid_y + t * 0.48), (x + w - t * 0.92, mid_y + t * 0.48), (x + w - t * 0.92, y + h - t * 0.82), (x + t, y + h - t * 0.82)])
    elif ch == "O":
        c = w * 0.18
        poly([
            (x + c, y), (x + w - c, y), (x + w, y + c), (x + w, y + h - c),
            (x + w - c, y + h), (x + c, y + h), (x, y + h - c), (x, y + c),
        ])
        hole([(x + t, y + t * 0.9), (x + w - t, y + t * 0.9), (x + w - t, y + h - t * 0.9), (x + t, y + h - t * 0.9)])
    elif ch == "S":
        c = w * 0.15
        # Top bar + left upper stem + middle bar + right lower stem + bottom bar
        poly([(x + c, y), (x + w, y), (x + w, y + t * 0.95), (x, y + t * 0.95), (x, y + c)])
        quad(x, y + c, x + t, mid_y + t * 0.42)
        quad(x, mid_y - t * 0.48, x + w, mid_y + t * 0.48)
        quad(x + w - t, mid_y - t * 0.42, x + w, y + h - c)
        poly([(x, y + h - t * 0.95), (x + w, y + h - t * 0.95), (x + w, y + h - c), (x + w - c, y + h), (x, y + h)])
    elif ch == "K":
        quad(x, y, x + t * 1.05, y + h)
        poly([(x + t * 0.8, mid_y - t * 0.2), (x + w - t * 0.95, y), (x + w + 1, y), (x + t * 1.45, mid_y + t * 0.55)])
        poly([(x + t * 1.15, mid_y - t * 0.35), (x + w + 1, y + h), (x + w - t * 1.0, y + h), (x + t * 0.8, mid_y + t * 0.45)])
    elif ch == "E":
        quad(x, y, x + t, y + h)
        quad(x, y, x + w, y + t * 0.92)
        quad(x, mid_y - t * 0.45, x + w * 0.86, mid_y + t * 0.45)
        quad(x, y + h - t * 0.92, x + w, y + h)
    elif ch == "T":
        quad(x, y, x + w, y + t * 0.96)
        quad(x + (w - t * 1.06) * 0.5, y, x + (w + t * 1.06) * 0.5, y + h)
    elif ch == "A":
        poly([(x + w * 0.24, y), (x + w * 0.76, y), (x + w, y + h), (x + w - t * 1.02, y + h), (x + w * 0.5, y + t * 1.1), (x + t * 1.02, y + h), (x, y + h)])
        quad(x + w * 0.16, y + h * 0.56, x + w * 0.84, y + h * 0.56 + t * 0.88)
    elif ch == "L":
        quad(x, y, x + t * 1.02, y + h)
        quad(x, y + h - t * 0.95, x + w, y + h)


def generate_illustrated_chat_logo_and_icons(font_tex_dir: Path) -> list:
    """
    Renders a studio-grade 2x HiDPI (368x90 PNG -> 184x45 GUI px) illustrated comic-book logo 'BOSKETBALL'
    matching image-9.png's hand-painted sticker aesthetic:
      - Crowned 3D Isometric Square Basketball crest rising above the center letters
      - Smooth anti-aliased vector-polygon comic lettering with dynamic arch & oversized center 'K'
      - White die-cut sticker outline -> Maroon ink border -> Deep 3D crimson shadow extrusion ->
        Fiery lemon-gold-to-tangerine gradient + diagonal comic gloss reflections!
    """
    W, H = 368, 90
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cd = ImageDraw.Draw(canvas)

    # 1. Draw Illustrated Crowned Square Basketball Crest behind top-center (x=184, y=22)
    bx, by = 184, 23
    # White sticker & dark ink halo behind crest
    cd.polygon([(bx, 2), (bx + 26, 13), (bx + 26, 37), (bx, 48), (bx - 26, 37), (bx - 26, 13)], fill=(252, 252, 255, 255))
    cd.polygon([(bx, 5), (bx + 23, 15), (bx + 23, 35), (bx, 45), (bx - 23, 35), (bx - 23, 15)], fill=(44, 14, 24, 255))
    # 3-face isometric orange leather cube ball
    cd.polygon([(bx, 9), (bx + 19, 17), (bx, 25), (bx - 19, 17)], fill=(255, 184, 62, 255))     # Top face
    cd.polygon([(bx - 19, 17), (bx, 25), (bx, 42), (bx - 19, 33)], fill=(242, 118, 28, 255))   # Left face
    cd.polygon([(bx, 25), (bx + 19, 17), (bx + 19, 33), (bx, 42)], fill=(198, 76, 18, 255))    # Right face
    # Cross seams on cube ball
    cd.line([(bx - 9, 13), (bx + 9, 21), (bx + 9, 37)], fill=(46, 18, 20, 255), width=2)
    cd.line([(bx + 9, 13), (bx - 9, 21), (bx - 9, 37)], fill=(46, 18, 20, 255), width=2)
    # Mini Golden Crown perched on top of the cube ball!
    crown_pts = [(bx - 13, 12), (bx - 15, 3), (bx - 6, 8), (bx, 1), (bx + 6, 8), (bx + 15, 3), (bx + 13, 12)]
    cd.polygon(crown_pts, fill=(255, 224, 64, 255), outline=(44, 14, 24, 255))

    # 2. Build 2x HiDPI Vector Comic Lettering Mask + 3D Crimson Extrusion
    word = "BOSKETBALL"
    char_widths = [29, 29, 28, 35, 27, 27, 29, 30, 25, 25]
    gap = 4
    total_w = sum(char_widths) + (len(word) - 1) * gap
    cur_x = (W - total_w) / 2.0

    face_mask = Image.new("L", (W, H), 0)
    fdraw = ImageDraw.Draw(face_mask)

    for idx, (ch, cw) in enumerate(zip(word, char_widths)):
        arch = 5.5 * math.sin((idx / (len(word) - 1)) * math.pi)
        ch_h = 46.0 if ch == "K" else 39.0
        ch_y = (28.0 if ch == "K" else 33.0) - arch
        _draw_vector_comic_letter(fdraw, ch, cur_x, ch_y, cw, ch_h, slant=3.5)
        cur_x += cw + gap

    # Build 8px deep 3D block extrusion down-right
    extrude_mask = Image.new("L", (W, H), 0)
    for d_step in range(1, 9):
        extrude_mask = Image.composite(
            Image.new("L", (W, H), 255),
            extrude_mask,
            face_mask.transform((W, H), Image.Transform.AFFINE, (1, 0, -d_step * 0.45, 0, 1, -d_step)),
        )

    def dilate(m: Image.Image, r: int) -> Image.Image:
        out = m.copy()
        for _ in range(r):
            out = out.filter(ImageFilter.MaxFilter(3))
        return out

    union_mask = Image.composite(Image.new("L", (W, H), 255), extrude_mask, face_mask)
    ink_mask = dilate(union_mask, 3)
    sticker_mask = dilate(ink_mask, 3)

    fp = face_mask.load()
    ep = extrude_mask.load()
    ip = ink_mask.load()
    sp = sticker_mask.load()
    cp = canvas.load()

    for y in range(H):
        t = max(0.0, min(1.0, (y - 24.0) / 46.0))
        # Sunny lemon-gold at top -> rich tangerine-amber -> fiery crimson-orange at bottom (exact image-9.png!)
        fr = 255
        fg = int(246 * (1.0 - t) + 128 * t)
        fb = int(68 * (1.0 - t) + 14 * t)
        for x in range(W):
            if fp[x, y] > 128:
                top_rim = (y >= 2 and fp[x, y - 2] <= 128)
                diag_sheen = ((x + y * 2) % 44) in (8, 9, 10, 11) and y < 52
                if top_rim:
                    cp[x, y] = (255, 254, 196, 255)
                elif diag_sheen:
                    cp[x, y] = (255, min(255, fg + 32), min(255, fb + 42), 255)
                else:
                    cp[x, y] = (fr, fg, fb, 255)
            elif ep[x, y] > 128:
                # Shaded crimson-to-burgundy 3D extrusion
                et = max(0.0, min(1.0, (y - 30.0) / 50.0))
                cp[x, y] = (int(214 - 52 * et), int(36 - 14 * et), int(44 - 12 * et), 255)
            elif ip[x, y] > 128:
                cp[x, y] = (42, 12, 24, 255)
            elif sp[x, y] > 128:
                cp[x, y] = (252, 253, 255, 255)

    # Sparkle stars on left & right flanks of logo
    for sx, sy, sr in [(20, 42, 6), (348, 40, 6), (146, 16, 4), (222, 16, 4)]:
        cd.polygon([(sx, sy - sr), (sx + 2, sy - 2), (sx + sr, sy), (sx + 2, sy + 2), (sx, sy + sr), (sx - 2, sy + 2), (sx - sr, sy), (sx - 2, sy - 2)], fill=(255, 248, 168, 255))

    providers = []
    half_w = W // 2  # 184px per glyph half -> strictly <= 254px vanilla FontTexture atlas limit!
    for strip_i in range(5):
        strip = canvas.crop((0, strip_i * 18, W, (strip_i + 1) * 18)).copy()
        # Pin right edge of BOTH 184px halves so vanilla BitmapProvider never trims transparent right columns!
        for pin_x in (half_w - 1, W - 1):
            r0, g0, b0, a0 = strip.getpixel((pin_x, 0))
            if a0 == 0:
                strip.putpixel((pin_x, 0), (255, 255, 255, 3))
        fname = f"chat_logo_strip_{strip_i}.png"
        strip.save(font_tex_dir / fname)
        providers.append({
            "type": "bitmap",
            "file": f"bosketball:font/{fname}",
            "height": 9,
            "ascent": 8,
            "chars": [chr(0xE120 + strip_i) + chr(0xE125 + strip_i)],
        })

    # 5 Inline 10x10 Shaded Broadcast Icons (\uE270..\uE274) matching image-9.png!
    inline_specs = {
        "chat_web.png": (
            "\uE270",
            [
                "...KKKK...",
                ".KKBBBBKK.",
                "KBGGGBBBBK",
                "KBGGGGBBBK",
                "KBBGGBBGGK",
                "KBBBBBBGGK",
                "KBBGGGBBBK",
                ".KKBBBBKK.",
                "...KKKK...",
                "..........",
            ],
            {"K": (18, 32, 58, 255), "B": (38, 152, 255, 255), "G": (82, 232, 112, 255)},
        ),
        "chat_shop.png": (
            "\uE271",
            [
                "...KKKK...",
                "..KWWWWK..",
                ".KKKKKKKK.",
                "KRRRRRRRRK",
                "KRRWWWWWRK",
                "KKKKKKKKKK",
                "KRRRRRRRRK",
                "KDDDDDDDDK",
                ".KKKKKKKK.",
                "..........",
            ],
            {"K": (36, 16, 20, 255), "R": (232, 54, 62, 255), "D": (158, 28, 36, 255), "W": (238, 244, 255, 255)},
        ),
        "chat_megaphone.png": (
            "\uE273",
            [
                "......KKK.",
                "....KKRRK.",
                "..KKRRRRK.",
                "KKWWWRRRKY",
                "KWWWWRRRK.",
                "KKWWWRRRKY",
                "..KKRRRRK.",
                "..KWWKRRK.",
                "..KK..KKK.",
                "..........",
            ],
            {"K": (32, 20, 24, 255), "R": (238, 48, 48, 255), "W": (228, 236, 248, 255), "Y": (255, 212, 64, 255)},
        ),
        "chat_mail.png": (
            "\uE274",
            [
                "..........",
                "KKKKKKKKKK",
                "KYYYYYYYYK",
                "KYKYYYYKYK",
                "KYYKYYKYYK",
                "KYYYKKYYYK",
                "KWWWWWWWWK",
                "KKKKKKKKKK",
                "..........",
                "..........",
            ],
            {"K": (42, 32, 18, 255), "Y": (255, 214, 68, 255), "W": (255, 248, 224, 255)},
        ),
    }
    for fname, (ch, rows, cmap) in inline_specs.items():
        ic = Image.new("RGBA", (10, 10), (0, 0, 0, 0))
        ip = ic.load()
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if c in cmap:
                    ip[x, y] = cmap[c]
        pin_width(ic).save(font_tex_dir / fname)
        providers.append({"type": "bitmap", "file": f"bosketball:font/{fname}", "height": 9, "ascent": 8, "chars": [ch]})

    # High-Resolution 36x36 HiDPI Authentic Discord Clyde Squircle Icon (\uE272)
    disc = Image.new("RGBA", (36, 36), (0, 0, 0, 0))
    dd = ImageDraw.Draw(disc)
    # Dark outer border + Blurple #5865F2 squircle + subtle top inner highlight
    dd.rounded_rectangle([1, 2, 34, 33], radius=9, fill=(24, 28, 62, 255))
    dd.rounded_rectangle([3, 4, 32, 31], radius=7, fill=(88, 101, 242, 255))
    dd.rounded_rectangle([5, 5, 30, 11], radius=4, fill=(114, 128, 252, 255))
    dd.rectangle([5, 9, 30, 14], fill=(88, 101, 242, 255))
    # Crisp white Discord Clyde silhouette (curved top shoulders, rounded cheeks, controller cutout & oval eyes)
    dd.polygon([(11, 10), (15, 10), (16, 12), (19, 12), (20, 10), (24, 10), (28, 15), (29, 22), (25, 26), (21, 26), (20, 23), (15, 23), (14, 26), (10, 26), (6, 22), (7, 15)], fill=(255, 255, 255, 255))
    dd.ellipse([11, 15, 15, 20], fill=(88, 101, 242, 255))
    dd.ellipse([20, 15, 24, 20], fill=(88, 101, 242, 255))
    pin_width(disc).save(font_tex_dir / "chat_discord.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/chat_discord.png", "height": 9, "ascent": 8, "chars": ["\uE272"]})

    # High-Resolution 36x36 HiDPI Gold Coin Icon (\uE275) for Sidebar, Tab & Shop!
    coin = Image.new("RGBA", (36, 36), (0, 0, 0, 0))
    cdraw = ImageDraw.Draw(coin)
    cdraw.ellipse([2, 2, 33, 33], fill=(48, 28, 8, 255))
    cdraw.ellipse([4, 4, 31, 31], fill=(245, 168, 24, 255))
    cdraw.ellipse([6, 5, 29, 28], fill=(255, 214, 58, 255))
    cdraw.ellipse([9, 8, 26, 25], outline=(218, 134, 16, 255), width=2)
    cdraw.rectangle([16, 11, 19, 24], fill=(255, 248, 182, 255))
    pin_width(coin).save(font_tex_dir / "chat_coin.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/chat_coin.png", "height": 9, "ascent": 8, "chars": ["\uE275"]})

    return providers


# ============================================================================
# 5. MASTER FONT SPRITE GENERATOR FOR IMAGE-5 (46 PILLS!) & IMAGE-8 PILLS
# ============================================================================
# (tag_id, glyph_char, label, rgb, optional_mini_icon)
AURA_TAGS_CATALOG = [
    ("aura",     "\uE240", "AURA",     (42, 168, 248),  "diamond"),
    ("goat",     "\uE241", "GOAT",     (238, 178, 28),  "crown"),
    ("clutch",   "\uE242", "CLUTCH",   (224, 48, 62),   "bolt"),
    ("sniper",   "\uE243", "SNIPER",   (34, 196, 112),  "target"),
    ("ankle",    "\uE244", "ANKLE",    (156, 68, 242),  "bolt"),
    ("poster",   "\uE245", "POSTER",   (238, 84, 34),   "fire"),
    ("splash",   "\uE246", "SPLASH",   (24, 198, 232),  "diamond"),
    ("lockdown", "\uE247", "LOCKDOWN", (76, 96, 132),   "shield"),
    ("baller",   "\uE248", "BALLER",   (242, 132, 32),  "ball"),
    ("phantom",  "\uE249", "PHANTOM",  (112, 52, 198),  "wings"),
    ("king",     "\uE24A", "KING",     (228, 162, 24),  "crown"),
    ("ace",      "\uE24B", "ACE",      (236, 48, 108),  "star"),
    ("flash",    "\uE24C", "FLASH",    (232, 196, 28),  "bolt"),
    ("glacier",  "\uE24D", "GLACIER",  (58, 184, 255),  "diamond"),
    ("inferno",  "\uE24E", "INFERNO",  (228, 52, 32),   "fire"),
    ("sakura",   "\uE24F", "SAKURA",   (242, 108, 172), "star"),
    ("cyber",    "\uE250", "CYBER",    (26, 212, 158),  "bolt"),
    ("titan",    "\uE251", "TITAN",    (184, 118, 54),  "shield"),
    ("street",   "\uE252", "STREET",   (118, 212, 42),  "ball"),
    ("champion", "\uE253", "CHAMPION", (244, 186, 42),  "trophy"),
    # 8 Brand-New Century-Edition Prestige Tags (\uE254..\uE25B)!
    ("mvp",      "\uE254", "MVP",      (250, 146, 24),  "trophy"),
    ("legend",   "\uE255", "LEGEND",   (186, 52, 238),  "wings"),
    ("swish",    "\uE256", "SWISH",    (36, 212, 182),  "target"),
    ("hooper",   "\uE257", "HOOPER",   (52, 156, 248),  "ball"),
    ("clamp",    "\uE258", "CLAMP",    (216, 44, 86),   "shield"),
    ("dime",     "\uE259", "DIME",     (168, 92, 250),  "star"),
    ("buckets",  "\uE25A", "BUCKETS",  (242, 102, 28),  "fire"),
    ("chaos",    "\uE25B", "CHAOS",    (234, 42, 142),  "crown"),
    # 8 Ultra-Prestige Multi-Stack Tags (\uE280..\uE287)
    ("royal",     "\uE280", "ROYAL",     (128, 72, 246),  "crown"),
    ("alpha",     "\uE281", "ALPHA",     (236, 56, 56),   "bolt"),
    ("omega",     "\uE282", "OMEGA",     (36, 174, 246),  "diamond"),
    ("galaxy",    "\uE283", "GALAXY",    (96, 62, 228),   "star"),
    ("viper",     "\uE284", "VIPER",     (38, 212, 92),   "target"),
    ("blitz",     "\uE285", "BLITZ",     (248, 178, 24),  "bolt"),
    ("frostbite", "\uE286", "FROSTBITE", (48, 206, 244),  "wings"),
    ("supernova", "\uE287", "SUPERNOVA", (246, 78, 128),  "fire"),
    # 9 Hall-of-Fame Edition Tags (\uE288..\uE290) -> 45 Selectable Tags (63 Total Glossy Beveled Pills)!
    ("venom",     "\uE288", "VENOM",     (28, 194, 84),   "fire"),
    ("zenith",    "\uE289", "ZENITH",    (52, 168, 250),  "wings"),
    ("eclipse",   "\uE28A", "ECLIPSE",   (92, 64, 186),   "diamond"),
    ("nova",      "\uE28B", "NOVA",      (248, 126, 36),  "star"),
    ("ronin",     "\uE28C", "RONIN",     (208, 44, 68),   "shield"),
    ("mamba",     "\uE28D", "MAMBA",     (154, 58, 236),  "crown"),
    ("air",         "\uE28E", "AIR",         (232, 48, 48),   "wings"),
    ("chef",        "\uE28F", "CHEF",        (36, 146, 246),  "target"),
    ("immortal",    "\uE290", "IMMORTAL",    (246, 188, 32),  "trophy"),
    # 9 Extra Century-of-Hoops Prestige Tags (\uE291..\uE299) -> 54 Selectable Tags (72 Total Glossy Beveled Pills!!)!
    ("prodigy",     "\uE291", "PRODIGY",     (142, 68, 250),  "star"),
    ("dynasty",     "\uE292", "DYNASTY",     (224, 152, 28),  "crown"),
    ("ballgod",     "\uE293", "BALLGOD",     (248, 108, 26),  "ball"),
    ("deadeye",     "\uE294", "DEADEYE",     (218, 38, 64),   "target"),
    ("skyhook",     "\uE295", "SKYHOOK",     (44, 178, 248),  "wings"),
    ("crossgod",    "\uE296", "CROSSGOD",    (216, 52, 192),  "bolt"),
    ("iceveins",    "\uE297", "ICEVEINS",    (28, 206, 232),  "diamond"),
    ("showtime",    "\uE298", "SHOWTIME",    (244, 176, 38),  "trophy"),
    ("unstoppable", "\uE299", "UNSTOPPABLE", (236, 56, 32),   "fire"),
    # Staff honour tag (\uE29C = MODS): same glossy beveled pill as every grade, unlocked by bosketball.staff
    ("mods",        "\uE29C", "MODS",        (59, 130, 246), "shield"),
    # Exclusive Custom Tags requested by MaxLananas (\uE29A = FOUNDER, \uE29B = MYPINEAPPLE)
    ("founder",     "\uE29A", "FOUNDER",     (228, 36, 58),   "crown"),
    ("mypineapple", "\uE29B", "MYPINEAPPLE", (245, 184, 24),  "pineapple"),
]


def generate_all_overhaul_font_providers(font_tex_dir: Path) -> list:
    providers = []
    providers.extend(generate_chat_divider_sprites(font_tex_dir))
    providers.extend(generate_menu3d_highlight_sprites(font_tex_dir))
    providers.extend(generate_illustrated_chat_logo_and_icons(font_tex_dir))

    # 18 Glossy 3D-Beveled Top-#1/#2/#3 Leaderboard Badges (Gold #1, Silver #2, Bronze #3 for ELO!)
    top_badge_groups = [
        ("top_wins",    0xE220, "trophy", (206, 42, 48)),   # Crimson Trophy #1..#3
        ("top_elo",     0xE223, "crown",  (214, 152, 28)),  # Gold/Silver/Bronze Crown #1..#3
        ("top_mvp",     0xE226, "star",   (142, 54, 224)),  # Royal Purple Star #1..#3
        ("top_assists", 0xE229, "wings",  (38, 148, 232)),  # Sky-Blue Halo Wings #1..#3
        ("top_points",  0xE22C, "ball",   (228, 106, 28)),  # Burnt Orange Ball #1..#3
        ("top_dunks",   0xE22F, "fire",   (218, 54, 36)),   # Magma Fire #1..#3
    ]
    elo_podium_colors = {
        1: (232, 168, 24),   # #1 Gold
        2: (146, 164, 192),  # #2 Platinum-Silver
        3: (192, 112, 48),   # #3 Burnished Bronze
    }
    for prefix, base_cp, icon_kind, rgb in top_badge_groups:
        for place in (1, 2, 3):
            ch = chr(base_cp + place - 1)
            fname = f"{prefix}_{place}.png"
            pill_rgb = elo_podium_colors[place] if prefix == "top_elo" else rgb
            make_glossy_beveled_pill(f"#{place}", pill_rgb, icon_kind=icon_kind).save(font_tex_dir / fname)
            providers.append({"type": "bitmap", "file": f"bosketball:font/{fname}", "height": 9, "ascent": 8, "chars": [ch]})

    # 28 Unlockable & Selectable Glossy Beveled Aura / Title Tags (\uE240..\uE25B) with inline mini icons!
    for tag_id, ch, label, rgb, mini_icon in AURA_TAGS_CATALOG:
        fname = f"tag_{tag_id}.png"
        make_glossy_beveled_pill(label, rgb, icon_kind=mini_icon).save(font_tex_dir / fname)
        providers.append({"type": "bitmap", "file": f"bosketball:font/{fname}", "height": 9, "ascent": 8, "chars": [ch]})

    # Luxury Sidebar Golden Filigree Flourish (\uE260) + 10 Colored Section Pills (\uE261..\uE26A, exact image-8.png!)
    make_sidebar_flourish().save(font_tex_dir / "sidebar_flourish.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/sidebar_flourish.png", "height": 9, "ascent": 8, "chars": ["\uE260"]})

    sidebar_pills = [
        ("sb_player.png",     "\uE261", "PLAYER",    (138, 150, 164)),
        ("sb_elo.png",        "\uE262", "RANKED",    (242, 42, 82)),
        ("sb_points.png",     "\uE263", "STATS",     (0, 168, 248)),
        ("sb_playtime.png",   "\uE264", "PLAYTIME",  (20, 202, 130)),
        ("sb_friends.png",    "\uE265", "PARTY",     (122, 90, 248)),
        ("sb_match.png",      "\uE266", "SCORE",     (242, 74, 52)),
        ("sb_clock.png",      "\uE267", "CLOCK",     (236, 168, 28)),
        ("sb_stats.png",      "\uE268", "BOX SCORE", (20, 202, 130)),
        ("sb_possession.png", "\uE269", "BALL",      (0, 168, 248)),
        ("sb_streak.png",     "\uE26A", "STREAK",    (138, 86, 248)),
        ("sb_coins.png",      "\uE26B", "COINS",     (245, 168, 24)),
    ]
    for fname, ch, label, rgb in sidebar_pills:
        make_sidebar_section_pill(label, rgb).save(font_tex_dir / fname)
        providers.append({"type": "bitmap", "file": f"bosketball:font/{fname}", "height": 9, "ascent": 8, "chars": [ch]})

    # 9 Glossy 3D-Beveled System & Broadcast Chat Pill Badges (\uE2A0..\uE2A8, MCC Island / Origin Realms / Cytooxien style!)
    system_chat_pills = [
        ("sys_server.png", "\uE2A0", "BOSKET", (242, 128, 28),  "ball"),
        ("sys_court.png",  "\uE2A1", "COURT",  (24, 196, 116),  "trophy"),
        ("sys_social.png", "\uE2A2", "SOCIAL", (38, 168, 248),  "star"),
        ("sys_shop.png",   "\uE2A3", "SHOP",   (236, 174, 28),  "crown"),
        ("sys_alert.png",  "\uE2A4", "ALERT",  (228, 52, 68),   "shield"),
        ("sys_join.png",   "\uE2A5", "+",      (28, 206, 112),  None),
        ("sys_quit.png",   "\uE2A6", "-",      (196, 62, 76),   None),
        ("sys_pm.png",     "\uE2A7", "PM",     (164, 78, 248),  "bolt"),
        ("sys_guide.png",  "\uE2A8", "GUIDE",  (68, 126, 250),  "diamond"),
    ]
    for fname, ch, label, rgb, mini_icon in system_chat_pills:
        make_glossy_beveled_pill(label, rgb, icon_kind=mini_icon).save(font_tex_dir / fname)
        providers.append({"type": "bitmap", "file": f"bosketball:font/{fname}", "height": 9, "ascent": 8, "chars": [ch]})

    return providers


# ============================================================================
# 6. IMAGE-7.PNG CONCENTRIC GLOWING TEAM FLOOR AURA RINGS
# ============================================================================
def generate_team_floor_rings(item_tex_dir: Path, model_dir: Path, item_def_dir: Path):
    ring_palettes = {
        "ring_ignis": ((255, 62, 58), (255, 196, 178)),
        "ring_frost": ((38, 178, 255), (188, 242, 255)),
        "ring_gold":  ((255, 196, 42), (255, 248, 192)),
    }
    size = 128
    for ring_id, (outer_rgb, core_rgb) in ring_palettes.items():
        grid = 64
        gh = (grid - 1) / 2.0
        small = Image.new("RGBA", (grid, grid), (0, 0, 0, 0))
        sp = small.load()
        for y in range(grid):
            for x in range(grid):
                r = math.hypot(x - gh, y - gh) / gh
                if 0.85 <= r <= 0.94:
                    core = 0.88 <= r <= 0.91
                    c = core_rgb if core else outer_rgb
                    sp[x, y] = (c[0], c[1], c[2], 250 if core else 228)
                elif 0.67 <= r <= 0.74:
                    core = 0.695 <= r <= 0.715
                    c = core_rgb if core else outer_rgb
                    sp[x, y] = (c[0], c[1], c[2], 236 if core else 205)
                elif 0.56 <= r <= 0.99:
                    sp[x, y] = (outer_rgb[0], outer_rgb[1], outer_rgb[2], 44)
        tex = small.resize((size, size), Image.Resampling.NEAREST)
        tex.save(item_tex_dir / f"{ring_id}.png")

        ring_model = {
            "credit": "BosketBall 26.2 Team Floor Aura Ring (image-7.png)",
            "ambientocclusion": False,
            "textures": {
                "0": f"bosketball:item/{ring_id}",
                "particle": f"bosketball:item/{ring_id}",
            },
            "elements": [
                {
                    "name": "floor_aura_disc",
                    "from": [0.0, 7.92, 0.0],
                    "to": [16.0, 8.08, 16.0],
                    "shade": False,
                    "light_emission": 15,
                    "faces": {
                        "up":   {"uv": [0, 0, 16, 16], "texture": "#0"},
                        "down": {"uv": [0, 16, 16, 0], "texture": "#0"},
                    },
                }
            ],
            "display": {
                "gui": {"rotation": [30, 45, 0], "scale": [0.65, 0.65, 0.65]},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            },
        }
        (model_dir / f"{ring_id}.json").write_text(json.dumps(ring_model, indent=2))
        (item_def_dir / f"{ring_id}.json").write_text(
            json.dumps({"model": {"type": "minecraft:model", "model": f"bosketball:item/{ring_id}"}}, indent=2)
        )


# ============================================================================
# 7. 3D ARTICULATED 6-BONE HOOPER RIG FOR CUSTOM WALK, JUMP & THROW ANIMATIONS
# ============================================================================
def generate_hooper_3d_rig_models(item_tex_dir: Path, model_dir: Path, item_def_dir: Path):
    kits = {
        "ignis": ((214, 42, 46), (142, 20, 26), (255, 204, 58)),   # Crimson Jersey + Gold Trim
        "frost": ((32, 138, 228), (18, 78, 152), (124, 238, 255)),  # Cobalt-Sky Jersey + Cyan Trim
    }
    for kit_id, (main_c, dark_c, trim_c) in kits.items():
        sheet = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        d = ImageDraw.Draw(sheet)
        for y in range(16):
            for x in range(16):
                grain = ((x * 5 + y * 9) % 10) - 5
                sheet.putpixel((x, y), (max(0, min(255, main_c[0] + grain)), max(0, min(255, main_c[1] + grain)), max(0, min(255, main_c[2] + grain)), 255))
                sheet.putpixel((16 + x, y), (max(0, min(255, dark_c[0] + grain)), max(0, min(255, dark_c[1] + grain)), max(0, min(255, dark_c[2] + grain)), 255))
                sheet.putpixel((32 + x, y), (*trim_c, 255))
                sheet.putpixel((48 + x, y), (228 + grain // 2, 178 + grain // 2, 142 + grain // 2, 255))
        d.rectangle([0, 16, 15, 31], fill=(245, 248, 255, 255))
        d.rectangle([16, 16, 31, 31], fill=(28, 32, 42, 255))
        d.rectangle([32, 16, 63, 47], fill=(*main_c, 255))
        d.rectangle([32, 16, 63, 19], fill=(*trim_c, 255))
        draw_text(sheet, "26", 39, 25, color=(255, 255, 255, 255), shadow=(*dark_c, 255), scale=2)
        sheet.save(item_tex_dir / f"hooper_kit_{kit_id}.png")

        uv_main   = [0.5, 0.5, 3.5, 3.5]
        uv_dark   = [4.5, 0.5, 7.5, 3.5]
        uv_trim   = [8.5, 0.5, 11.5, 3.5]
        uv_skin   = [12.5, 0.5, 15.5, 3.5]
        uv_white  = [0.5, 4.5, 3.5, 7.5]
        uv_sole   = [4.5, 4.5, 7.5, 7.5]
        uv_jersey = [8.0, 4.0, 16.0, 12.0]

        def box(name, frm, to, uv, front_uv=None):
            faces = {f: {"uv": uv, "texture": "#0"} for f in ("north", "south", "east", "west", "up", "down")}
            if front_uv:
                faces["north"] = {"uv": front_uv, "texture": "#0"}
                faces["south"] = {"uv": front_uv, "texture": "#0"}
            return {"name": name, "from": frm, "to": to, "faces": faces}

        torso_model = {
            "credit": "BosketBall 26.2 Articulated Hooper Torso",
            "textures": {"0": f"bosketball:item/hooper_kit_{kit_id}", "particle": f"bosketball:item/hooper_kit_{kit_id}"},
            "elements": [
                box("jersey_core", [4.0, 2.0, 6.0], [12.0, 13.8, 10.0], uv_main, front_uv=uv_jersey),
                box("collar_trim", [5.6, 13.2, 5.8], [10.4, 14.0, 10.2], uv_trim),
                box("side_stripe_l", [3.85, 2.0, 6.8], [4.1, 13.5, 9.2], uv_trim),
                box("side_stripe_r", [11.9, 2.0, 6.8], [12.15, 13.5, 9.2], uv_trim),
                box("shorts_waist", [3.9, 1.2, 5.9], [12.1, 2.4, 10.1], uv_white),
            ],
            "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]}},
        }

        arm_model = {
            "credit": "BosketBall 26.2 Shoulder-Pivoted Articulated Arm",
            "textures": {"0": f"bosketball:item/hooper_kit_{kit_id}", "particle": f"bosketball:item/hooper_kit_{kit_id}"},
            "elements": [
                box("shoulder_deltoid", [6.2, 5.2, 6.2], [9.8, 9.6, 9.8], uv_skin),
                box("shooter_sleeve",   [6.1, 1.4, 6.1], [9.9, 5.4, 9.9], uv_dark),
                box("wristband",        [6.05, 0.6, 6.05], [9.95, 1.5, 9.95], uv_trim),
                box("hand",             [6.25, -1.6, 6.25], [9.75, 0.7, 9.75], uv_skin),
            ],
            "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]}},
        }

        leg_model = {
            "credit": "BosketBall 26.2 Hip-Pivoted Articulated Leg & Air Sneaker",
            "textures": {"0": f"bosketball:item/hooper_kit_{kit_id}", "particle": f"bosketball:item/hooper_kit_{kit_id}"},
            "elements": [
                box("shorts_thigh",  [5.8, 3.6, 5.8], [10.2, 8.8, 10.2], uv_main),
                box("shorts_hem",    [5.7, 3.2, 5.7], [10.3, 3.9, 10.3], uv_trim),
                box("calf_skin",     [6.2, 0.4, 6.2], [9.8, 3.3, 9.8], uv_skin),
                box("crew_sock",     [6.1, -1.4, 6.1], [9.9, 0.6, 9.9], uv_white),
                box("sneaker_upper", [5.9, -3.1, 5.2], [10.1, -1.2, 10.3], uv_main),
                box("sneaker_toe",   [6.0, -3.1, 4.3], [10.0, -2.0, 5.4], uv_white),
                box("sneaker_sole",  [5.8, -3.8, 4.2], [10.2, -3.0, 10.4], uv_sole),
            ],
            "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]}},
        }

        for part_name, m_json in [
            (f"hooper_torso_{kit_id}", torso_model),
            (f"hooper_arm_{kit_id}", arm_model),
            (f"hooper_leg_{kit_id}", leg_model),
        ]:
            (model_dir / f"{part_name}.json").write_text(json.dumps(m_json, indent=2))
            (item_def_dir / f"{part_name}.json").write_text(
                json.dumps({"model": {"type": "minecraft:model", "model": f"bosketball:item/{part_name}"}}, indent=2)
            )


# ============================================================================
# 8. IMAGE-10.PNG HIGH-CRAFT 16x16 PIXEL-ART ICONS + DARK-GOLD CONTAINER UI
# ============================================================================
def generate_custom_container_gui(rp_dir: Path):
    """
    Generates a sleek Dark-Obsidian & Brushed-Gold NBA Pro container screen
    (assets/minecraft/textures/gui/container/generic_54.png) so /bb menus look 100% custom!
    Exact vanilla GenericContainerScreen coordinates:
      - Top blit: [0..175, 0 .. rows*18+16] (header 0..16, chest slot bezels at x=7+c*18, y=17+r*18)
      - Bottom blit: [0..175, 126..221] (player inv bezels at x=7+c*18, y=139+r*18; hotbar at y=197; bottom frame 215..221)
    """
    out_dir = rp_dir / "assets" / "minecraft" / "textures" / "gui" / "container"
    out_dir.mkdir(parents=True, exist_ok=True)

    tex = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(tex)

    def paint_panel(y0: int, y1: int):
        # Rich dark obsidian-navy body
        d.rectangle([0, y0, 175, y1], fill=(18, 22, 34, 250))
        # Outer 2px dark steel + 1px warm brushed gold trim
        d.rectangle([0, y0, 175, y1], outline=(10, 12, 20, 255), width=1)
        d.rectangle([1, y0 + 1, 174, y1 - 1], outline=(218, 162, 46, 235), width=1)
        d.rectangle([2, y0 + 2, 173, y1 - 2], outline=(38, 46, 68, 255), width=1)

    def paint_slot_bezel(sx: int, sy: int, accent_slot: bool = False):
        # 18x18 recessed dark slot well [sx..sx+17, sy..sy+17]
        d.rectangle([sx, sy, sx + 17, sy + 17], fill=(12, 15, 24, 255))
        # Top & left inner shadow
        d.line([(sx, sy), (sx + 16, sy)], fill=(6, 8, 14, 255))
        d.line([(sx, sy), (sx, sy + 16)], fill=(6, 8, 14, 255))
        # Bottom & right subtle slate/gold rim highlight
        rim = (118, 94, 42, 220) if accent_slot else (44, 54, 78, 255)
        d.line([(sx + 1, sy + 17), (sx + 17, sy + 17)], fill=rim)
        d.line([(sx + 17, sy + 1), (sx + 17, sy + 17)], fill=rim)

    # Top container region [0..175, 0..124]
    paint_panel(0, 124)
    # Illuminated Title Header Ribbon [3..172, 3..15] (warm platinum-amber gradient on left so even dark vanilla titles pop, deep navy on right!)
    for hy in range(3, 16):
        for hx in range(3, 173):
            tex.putpixel((hx, hy), (26, 32, 48, 255))
    d.line([(3, 15), (172, 15)], fill=(238, 178, 48, 225))
    # 6 rows of 9 chest slots at y = 17 + r*18
    for r in range(6):
        for c in range(9):
            paint_slot_bezel(7 + c * 18, 17 + r * 18, accent_slot=(r in (1, 2) and 1 <= c <= 7))

    # Bottom player inventory region [0..175, 126..221]
    paint_panel(126, 221)
    d.line([(3, 136), (172, 136)], fill=(64, 78, 112, 200))
    for r in range(3):
        for c in range(9):
            paint_slot_bezel(7 + c * 18, 139 + r * 18)
    for c in range(9):
        paint_slot_bezel(7 + c * 18, 197, accent_slot=True)

    tex.save(out_dir / "generic_54.png")

    # 2. Transparent-Viewport 3D Locker Room HUD Overlay (shulker_box.png, 176x166 blit):
    #    - Center & upper viewport [x=28..147, y=0..136] is 100% transparent so players stand clearly on stage!
    #    - Left quick-rail [slots 0, 9, 18 at x=7, y=17,35,53] & Right quick-rail [slots 8, 17, 26 at x=151, y=17,35,53]
    #    - Bottom 9-slot Esports Action Dock [hotbar 0..8 at x=7+c*18, y=141]
    shulker = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shulker)

    # Left floating glass-gold rail (around slots 0, 9, 18 -> x=4..27, y=14..73)
    sd.rounded_rectangle([4, 14, 27, 73], radius=3, fill=(14, 18, 30, 228), outline=(218, 162, 46, 235), width=1)
    # Right floating glass-gold rail (around slots 8, 17, 26 -> x=148..171, y=14..73)
    sd.rounded_rectangle([148, 14, 171, 73], radius=3, fill=(14, 18, 30, 228), outline=(218, 162, 46, 235), width=1)

    for r in range(3):
        sy = 17 + r * 18
        for sx in (7, 151):
            sd.rectangle([sx, sy, sx + 17, sy + 17], fill=(10, 13, 22, 245))
            sd.line([(sx, sy), (sx + 16, sy)], fill=(5, 7, 12, 255))
            sd.line([(sx, sy), (sx, sy + 16)], fill=(5, 7, 12, 255))
            sd.line([(sx + 1, sy + 17), (sx + 17, sy + 17)], fill=(128, 102, 44, 225))
            sd.line([(sx + 17, sy + 1), (sx + 17, sy + 17)], fill=(128, 102, 44, 225))

    # Bottom 9-slot Esports Command Dock (hotbar row at y=141..158 -> panel y=137..163)
    sd.rounded_rectangle([3, 137, 172, 163], radius=3, fill=(14, 18, 30, 238), outline=(10, 12, 20, 255), width=1)
    sd.rounded_rectangle([4, 138, 171, 162], radius=2, outline=(228, 172, 48, 240), width=1)
    for c in range(9):
        sx, sy = 7 + c * 18, 141
        accent = c in (0, 1, 2, 3, 8)
        sd.rectangle([sx, sy, sx + 17, sy + 17], fill=(10, 13, 22, 250))
        sd.line([(sx, sy), (sx + 16, sy)], fill=(5, 7, 12, 255))
        sd.line([(sx, sy), (sx, sy + 16)], fill=(5, 7, 12, 255))
        rim_col = (158, 118, 42, 235) if accent else (52, 64, 92, 240)
        sd.line([(sx + 1, sy + 17), (sx + 17, sy + 17)], fill=rim_col)
        sd.line([(sx + 17, sy + 1), (sx + 17, sy + 17)], fill=rim_col)

    shulker.save(out_dir / "shulker_box.png")

    # 3. Clean vanilla 'Inventory' container label override in assets/minecraft/lang/
    mc_lang_dir = rp_dir / "assets" / "minecraft" / "lang"
    mc_lang_dir.mkdir(parents=True, exist_ok=True)
    for locale_code in ("en_us", "en_gb", "fr_fr", "fr_ca", "de_de", "es_es"):
        (mc_lang_dir / f"{locale_code}.json").write_text(
            json.dumps({"container.inventory": "", "container.crafting": ""}, indent=2),
            encoding="utf-8",
        )

    # 4. Pixel-Exact Dark-Obsidian & Brushed-Gold Survival/Adventure Inventory (inventory.png, 256x256, active [0..175, 0..165])
    inv = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    idraw = ImageDraw.Draw(inv)
    idraw.rectangle([0, 0, 175, 165], fill=(18, 22, 34, 250))
    idraw.rectangle([0, 0, 175, 165], outline=(10, 12, 20, 255), width=1)
    idraw.rectangle([1, 1, 174, 164], outline=(218, 162, 46, 235), width=1)
    idraw.rectangle([2, 2, 173, 163], outline=(38, 46, 68, 255), width=1)

    def paint_inv_slot(sx: int, sy: int, accent_slot: bool = False):
        idraw.rectangle([sx, sy, sx + 17, sy + 17], fill=(12, 15, 24, 255))
        idraw.line([(sx, sy), (sx + 16, sy)], fill=(6, 8, 14, 255))
        idraw.line([(sx, sy), (sx, sy + 16)], fill=(6, 8, 14, 255))
        rim = (138, 106, 44, 225) if accent_slot else (44, 54, 78, 255)
        idraw.line([(sx + 1, sy + 17), (sx + 17, sy + 17)], fill=rim)
        idraw.line([(sx + 17, sy + 1), (sx + 17, sy + 17)], fill=rim)

    # 4 Armor slots [(7,7), (7,25), (7,43), (7,61)] + Offhand [(76,61)]
    for ay in (7, 25, 43, 61):
        paint_inv_slot(7, ay)
    paint_inv_slot(76, 61, accent_slot=True)

    # Character 3D preview stage [25..75, 7..78] (interior [26..74, 8..77])
    idraw.rectangle([25, 7, 75, 78], fill=(8, 10, 18, 255), outline=(6, 8, 14, 255))
    idraw.line([(26, 78), (75, 78)], fill=(128, 98, 42, 220))
    idraw.line([(75, 8), (75, 78)], fill=(128, 98, 42, 220))
    # Subtle golden spotlight floor line under player feet inside preview box
    idraw.line([(32, 73), (68, 73)], fill=(218, 162, 46, 95))

    # 2x2 Crafting grid [(97,17), (115,17), (97,35), (115,35)] + arrow + output [(153,27)]
    for cy in (17, 35):
        for cx in (97, 115):
            paint_inv_slot(cx, cy)
    # Sleek golden-slate crafting arrow around x=136..149, y=34..37
    idraw.rectangle([136, 35, 146, 36], fill=(148, 163, 184, 230))
    idraw.polygon([(145, 32), (150, 35), (145, 39)], fill=(238, 178, 48, 240))
    paint_inv_slot(153, 27, accent_slot=True)

    # Subtle divider above storage
    idraw.line([(7, 80), (168, 80)], fill=(52, 64, 92, 200))
    # 3x9 Storage [x=7+c*18, y=83+r*18] & 9-slot Hotbar [x=7+c*18, y=141]
    for r in range(3):
        for c in range(9):
            paint_inv_slot(7 + c * 18, 83 + r * 18)
    for c in range(9):
        paint_inv_slot(7 + c * 18, 141, accent_slot=True)
    inv.save(out_dir / "inventory.png")

    # 5. AAA Custom Luxury Dark-Gold Item Tooltips (sprites/tooltip/background.png & frame.png, 100x100 nine-slice)
    sprites_dir = rp_dir / "assets" / "minecraft" / "textures" / "gui" / "sprites"
    tt_dir = sprites_dir / "tooltip"
    tt_dir.mkdir(parents=True, exist_ok=True)

    tt_bg = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
    tbd = ImageDraw.Draw(tt_bg)
    tbd.rounded_rectangle([1, 1, 98, 98], radius=4, fill=(10, 14, 24, 242))
    # Subtle top ambient sheen inside tooltip background
    for y in range(2, 18):
        alpha = max(0, 242 - (18 - y))
        tbd.line([(3, y), (96, y)], fill=(16, 22, 38, alpha))
    tt_bg.save(tt_dir / "background.png")
    (tt_dir / "background.png.mcmeta").write_text(
        json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 100, "height": 100, "border": 9}}}, indent=2)
    )

    tt_fr = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
    tfd = ImageDraw.Draw(tt_fr)
    # Crisp outer dark steel shadow + inner brushed championship gold rim + corner accents
    tfd.rounded_rectangle([0, 0, 99, 99], radius=5, outline=(6, 9, 16, 250), width=1)
    tfd.rounded_rectangle([1, 1, 98, 98], radius=4, outline=(228, 172, 48, 235), width=1)
    tfd.rounded_rectangle([2, 2, 97, 97], radius=3, outline=(46, 58, 86, 210), width=1)
    # Bright gold corner jewels
    for cx, cy in ((2, 2), (97, 2), (2, 97), (97, 97)):
        tt_fr.putpixel((cx, cy), (255, 228, 118, 255))
    tt_fr.save(tt_dir / "frame.png")
    (tt_dir / "frame.png.mcmeta").write_text(
        json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 100, "height": 100, "border": 10, "stretch_inner": True}}}, indent=2)
    )

    # 6. Custom Esports Carbon-Slate HUD Hotbar, Gold Active Slot, Offhand, XP Bar & Competitive Crosshair
    hud_dir = sprites_dir / "hud"
    hud_dir.mkdir(parents=True, exist_ok=True)

    # hotbar.png (182x22: 1px outer frame + 9 x 20px cells)
    hb = Image.new("RGBA", (182, 22), (0, 0, 0, 0))
    hbd = ImageDraw.Draw(hb)
    hbd.rectangle([0, 0, 181, 21], fill=(12, 16, 26, 228), outline=(8, 10, 18, 248))
    hbd.rectangle([1, 1, 180, 20], outline=(48, 60, 88, 235))
    for i in range(9):
        sx = 1 + i * 20
        hbd.rectangle([sx + 2, 3, sx + 17, 18], fill=(16, 21, 34, 232))
        hbd.line([(sx + 2, 3), (sx + 17, 3)], fill=(8, 11, 18, 245))
        hbd.line([(sx + 2, 3), (sx + 2, 18)], fill=(8, 11, 18, 245))
        hbd.line([(sx + 3, 18), (sx + 17, 18)], fill=(54, 66, 96, 225))
        if i > 0:
            hbd.line([(sx, 2), (sx, 19)], fill=(34, 44, 66, 235))
    hb.save(hud_dir / "hotbar.png")

    # hotbar_selection.png (exact 24x23 in 1.20.2..26.2!)
    hbs = Image.new("RGBA", (24, 23), (0, 0, 0, 0))
    hsd = ImageDraw.Draw(hbs)
    hsd.rectangle([0, 0, 23, 22], outline=(12, 14, 22, 250), width=1)
    hsd.rectangle([1, 1, 22, 21], outline=(255, 194, 52, 255), width=1)
    hsd.rectangle([2, 2, 21, 20], outline=(238, 134, 28, 245), width=1)
    hsd.line([(2, 1), (21, 1)], fill=(255, 238, 138, 255))
    hbs.save(hud_dir / "hotbar_selection.png")

    # hotbar_offhand_left.png (29x24, dock at [0..21, 1..22]) & hotbar_offhand_right.png (29x24, dock at [7..28, 1..22])
    for fname, ox in (("hotbar_offhand_left.png", 0), ("hotbar_offhand_right.png", 7)):
        oh = Image.new("RGBA", (29, 24), (0, 0, 0, 0))
        ohd = ImageDraw.Draw(oh)
        ohd.rectangle([ox, 1, ox + 21, 22], fill=(12, 16, 26, 228), outline=(8, 10, 18, 248))
        ohd.rectangle([ox + 1, 2, ox + 20, 21], outline=(218, 162, 46, 215))
        ohd.rectangle([ox + 3, 4, ox + 18, 19], fill=(16, 21, 34, 235))
        oh.save(hud_dir / fname)

    # experience_bar_background.png & experience_bar_progress.png (182x5)
    xp_bg = Image.new("RGBA", (182, 5), (0, 0, 0, 0))
    xbd = ImageDraw.Draw(xp_bg)
    xbd.rectangle([0, 0, 181, 4], fill=(12, 16, 26, 220), outline=(38, 48, 72, 235))
    xp_bg.save(hud_dir / "experience_bar_background.png")

    xp_pr = Image.new("RGBA", (182, 5), (0, 0, 0, 0))
    for x in range(1, 181):
        t = x / 180.0
        r = int(42 + (250 - 42) * t)
        g = int(222 - (222 - 196) * t)
        b = int(128 - (128 - 44) * t)
        xp_pr.putpixel((x, 1), (min(255, r + 28), min(255, g + 24), min(255, b + 28), 255))
        xp_pr.putpixel((x, 2), (r, g, b, 255))
        xp_pr.putpixel((x, 3), (max(0, r - 36), max(0, g - 36), max(0, b - 24), 255))
    xp_pr.save(hud_dir / "experience_bar_progress.png")

    # crosshair.png (15x15 competitive precision gap-dot crosshair — crisp rim aiming!)
    ch_im = Image.new("RGBA", (15, 15), (0, 0, 0, 0))
    # Center dot (7,7) + 4 directional arms (gap of 2px) with subtle dark shadow outline for visibility on white court lines
    ch_im.putpixel((7, 7), (255, 255, 255, 255))
    arm_coords = [(7, 2), (7, 3), (7, 4), (7, 10), (7, 11), (7, 12), (2, 7), (3, 7), (4, 7), (10, 7), (11, 7), (12, 7)]
    for ax, ay in arm_coords:
        ch_im.putpixel((ax, ay), (255, 255, 255, 245))
    ch_im.save(hud_dir / "crosshair.png")

    # 7. Container Hover Slot Highlights (sprites/container/slot_highlight_back & front, 24x24 nine-slice border 4)
    sc_dir = sprites_dir / "container"
    sc_dir.mkdir(parents=True, exist_ok=True)
    sh_back = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    ImageDraw.Draw(sh_back).rectangle([4, 4, 19, 19], fill=(255, 204, 72, 36))
    sh_back.save(sc_dir / "slot_highlight_back.png")
    (sc_dir / "slot_highlight_back.png.mcmeta").write_text(
        json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 24, "height": 24, "border": 4}}}, indent=2)
    )
    sh_front = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    ImageDraw.Draw(sh_front).rectangle([3, 3, 20, 20], outline=(255, 214, 86, 215), width=1)
    sh_front.save(sc_dir / "slot_highlight_front.png")
    (sc_dir / "slot_highlight_front.png.mcmeta").write_text(
        json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 24, "height": 24, "border": 4}}}, indent=2)
    )

    # 8. Modern Dark-Slate & Gold Pause/Menu Buttons (sprites/widget/button*.png, 200x20 nine-slice)
    wd_dir = sprites_dir / "widget"
    wd_dir.mkdir(parents=True, exist_ok=True)
    for bname, fill_c, rim_c, border_w in (
        ("button.png",             (22, 28, 44, 245), (58, 72, 104, 255),  3),
        ("button_highlighted.png", (30, 38, 58, 250), (245, 184, 46, 255), 3),
        ("button_disabled.png",    (14, 18, 28, 210), (34, 42, 60, 220),   1),
    ):
        bim = Image.new("RGBA", (200, 20), (0, 0, 0, 0))
        bd = ImageDraw.Draw(bim)
        bd.rectangle([0, 0, 199, 19], fill=fill_c, outline=(8, 10, 18, 255), width=1)
        bd.rectangle([1, 1, 198, 18], outline=rim_c, width=1)
        bim.save(wd_dir / bname)
        (wd_dir / f"{bname}.mcmeta").write_text(
            json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 200, "height": 20, "border": border_w}}}, indent=2)
        )


def generate_lobby_3d_props(item_tex_dir: Path, model_dir: Path, item_def_dir: Path):
    """
    Generates 7 True 3D Multi-Element Blockbench-Grade Handheld & Hologram Props for the Lobby Hotbar & Stations:
      - prop_play_emblem      (Slot 0: 3D Golden Winged Basketball Crest & Mini Hoop)
      - prop_locker_jersey    (Slot 1: 3D Sculpted #23 Pro Jersey on Golden Hanger)
      - prop_shop_crate       (Slot 2: 3D Luxury Gold-Trimmed Boutique Cosmetics Crate)
      - prop_tag_plaque       (Slot 3: 3D Beveled Obsidian & Gold Collector Plaque)
      - prop_social_megaphone (Slot 6: 3D Arena Hype Megaphone with Ignis & Frost Stripes)
      - prop_guide_clipboard  (Slot 7: 3D Hardwood Coach's Playbook Clipboard & Silver Clip)
      - prop_podium_cup       (Slot 8: 3D #1/#2/#3 Stepped Marble & Gold Podium Crown)
    """
    atlas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(atlas)
    # Quadrant [0..15, 0..15]: Brushed 24K Gold
    for y in range(16):
        for x in range(16):
            g = 186 + int(44 * math.sin((x + y) * 0.42))
            atlas.putpixel((x, y), (255, min(255, g), 44, 255))
    # [16..31, 0..15]: Deep Obsidian Slate
    for y in range(16):
        for x in range(16, 32):
            v = 22 + ((x * 5 + y * 9) % 12)
            atlas.putpixel((x, y), (v, v + 5, v + 14, 255))
    # [32..47, 0..15]: Ignis Crimson-Orange
    d.rectangle([32, 0, 47, 15], fill=(236, 72, 38, 255))
    d.rectangle([34, 2, 45, 13], outline=(255, 146, 56, 255), width=1)
    # [48..63, 0..15]: Frost Electric Sky-Cyan
    d.rectangle([48, 0, 63, 15], fill=(42, 168, 248, 255))
    d.rectangle([50, 2, 61, 13], outline=(142, 228, 255, 255), width=1)
    # [0..15, 16..31]: Polished Chrome Silver
    for y in range(16, 32):
        for x in range(16):
            c = 204 + int(34 * math.cos((x - y) * 0.4))
            atlas.putpixel((x, y), (c, c + 4, min(255, c + 10), 255))
    # [16..31, 16..31]: Rich Mahogany / Crate Wood
    for y in range(16, 32):
        for x in range(16, 32):
            w = 128 + ((x * 3 + y * 11) % 24)
            atlas.putpixel((x, y), (w, int(w * 0.62), int(w * 0.34), 255))
    # [32..63, 16..47]: Hardwood Tactics Court Diagram (for prop_guide_clipboard)
    d.rectangle([32, 16, 63, 47], fill=(214, 162, 94, 255))
    d.rectangle([34, 18, 61, 45], outline=(248, 250, 255, 255), width=1)
    d.arc([38, 18, 57, 36], start=0, end=180, fill=(248, 250, 255, 255), width=1)
    d.line([(38, 38), (48, 28)], fill=(255, 68, 68, 255), width=2)
    d.ellipse([45, 21, 50, 26], outline=(255, 128, 32, 255), width=1)
    # [0..31, 32..63]: Royal Purple-Indigo Velvet (for tag plaque & jersey accents)
    d.rectangle([0, 32, 31, 63], fill=(92, 48, 196, 255))
    d.rectangle([2, 34, 29, 61], outline=(186, 118, 255, 255), width=1)
    atlas.save(item_tex_dir / "lobby_props_atlas.png")

    uv_gold   = [0.5, 0.5, 3.5, 3.5]
    uv_slate  = [4.5, 0.5, 7.5, 3.5]
    uv_ignis  = [8.5, 0.5, 11.5, 3.5]
    uv_frost  = [12.5, 0.5, 15.5, 3.5]
    uv_silver = [0.5, 4.5, 3.5, 7.5]
    uv_wood   = [4.5, 4.5, 7.5, 7.5]
    uv_court  = [8.0, 4.0, 16.0, 12.0]
    uv_velvet = [0.5, 8.5, 7.5, 15.5]

    def b(name, frm, to, uv, rot=None, emit=0, tex="#0"):
        el = {
            "name": name,
            "from": frm,
            "to": to,
            "faces": {f: {"uv": uv, "texture": tex} for f in ("north", "south", "east", "west", "up", "down")},
        }
        if rot:
            el["rotation"] = rot
        if emit:
            el["light_emission"] = emit
        return el

    std_display = {
        "gui": {"rotation": [24, 145, 0], "translation": [0, 0.0, 0], "scale": [0.88, 0.88, 0.88]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2.0, 0], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_righthand": {"rotation": [12, 45, 0], "translation": [0, 2.6, 0.8], "scale": [0.58, 0.58, 0.58]},
        "thirdperson_lefthand": {"rotation": [12, 225, 0], "translation": [0, 2.6, 0.8], "scale": [0.58, 0.58, 0.58]},
        "firstperson_righthand": {"rotation": [8, 140, -6], "translation": [1.2, 2.6, 0.4], "scale": [0.56, 0.56, 0.56]},
        "firstperson_lefthand": {"rotation": [8, -40, 6], "translation": [1.2, 2.6, 0.4], "scale": [0.56, 0.56, 0.56]},
        "head": {"translation": [0, 14.0, 0], "scale": [1.05, 1.05, 1.05]},
        "fixed": {"scale": [0.95, 0.95, 0.95]},
    }

    props = {
        "prop_play_emblem": [
            b("pedestal", [5.0, 1.5, 6.5], [11.0, 3.0, 9.5], uv_slate),
            b("gold_trim", [4.6, 2.8, 6.2], [11.4, 3.6, 9.8], uv_gold, emit=10),
            b("shield_core", [4.8, 3.6, 6.8], [11.2, 11.2, 9.2], uv_slate),
            b("wing_l1", [1.6, 6.2, 7.2], [5.2, 8.2, 8.8], uv_gold, rot={"origin": [5.0, 7.2, 8.0], "axis": "z", "angle": 22.5}, emit=10),
            b("wing_l2", [2.2, 8.4, 7.3], [5.4, 10.0, 8.7], uv_gold, rot={"origin": [5.0, 9.0, 8.0], "axis": "z", "angle": 22.5}, emit=12),
            b("wing_r1", [10.8, 6.2, 7.2], [14.4, 8.2, 8.8], uv_gold, rot={"origin": [11.0, 7.2, 8.0], "axis": "z", "angle": -22.5}, emit=10),
            b("wing_r2", [10.6, 8.4, 7.3], [13.8, 10.0, 8.7], uv_gold, rot={"origin": [11.0, 9.0, 8.0], "axis": "z", "angle": -22.5}, emit=12),
            b("center_ball", [6.0, 5.4, 5.8], [10.0, 9.4, 10.2], [2.75, 2.75, 13.25, 13.25], emit=12, tex="#ball"),
            b("crown_top", [6.2, 11.2, 6.8], [9.8, 13.0, 9.2], uv_gold, emit=12),
        ],
        "prop_locker_jersey": [
            b("hanger_hook", [7.5, 13.2, 7.5], [8.5, 15.0, 8.5], uv_gold, emit=8),
            b("hanger_bar", [3.5, 12.2, 7.3], [12.5, 13.2, 8.7], uv_gold, emit=8),
            b("jersey_torso", [4.5, 2.5, 6.4], [11.5, 12.2, 9.6], uv_frost),
            b("jersey_side_l", [4.3, 2.5, 6.6], [5.1, 11.8, 9.4], uv_gold, emit=6),
            b("jersey_side_r", [10.9, 2.5, 6.6], [11.7, 11.8, 9.4], uv_gold, emit=6),
            b("collar", [6.2, 10.8, 6.2], [9.8, 12.3, 9.8], uv_slate),
            b("number_badge", [6.5, 5.8, 6.1], [9.5, 9.4, 9.9], uv_gold, emit=10),
        ],
        "prop_shop_crate": [
            b("crate_body", [3.2, 2.0, 4.0], [12.8, 9.5, 12.0], uv_slate),
            b("crate_lid", [2.8, 9.5, 3.6], [13.2, 12.2, 12.4], uv_wood),
            b("gold_band_l", [3.6, 1.8, 3.7], [5.2, 12.5, 12.3], uv_gold, emit=8),
            b("gold_band_r", [10.8, 1.8, 3.7], [12.4, 12.5, 12.3], uv_gold, emit=8),
            b("front_clasp", [6.8, 7.8, 3.2], [9.2, 10.6, 12.8], uv_gold, emit=12),
            b("top_handle", [6.2, 12.2, 7.2], [9.8, 13.6, 8.8], uv_silver),
        ],
        "prop_tag_plaque": [
            b("chain_l", [5.2, 11.5, 7.5], [6.2, 14.2, 8.5], uv_gold, rot={"origin": [5.7, 11.5, 8.0], "axis": "z", "angle": -22.5}, emit=8),
            b("chain_r", [9.8, 11.5, 7.5], [10.8, 14.2, 8.5], uv_gold, rot={"origin": [10.3, 11.5, 8.0], "axis": "z", "angle": 22.5}, emit=8),
            b("plaque_frame", [2.4, 4.0, 6.8], [13.6, 11.6, 9.2], uv_gold, emit=10),
            b("plaque_velvet", [3.2, 4.8, 6.5], [12.8, 10.8, 9.5], uv_velvet),
            b("plaque_emblem", [6.2, 6.2, 6.1], [9.8, 9.4, 9.9], uv_gold, emit=12),
        ],
        "prop_social_megaphone": [
            b("handle", [6.8, 2.2, 8.2], [9.0, 6.4, 10.0], uv_slate, rot={"origin": [7.9, 6.4, 9.1], "axis": "x", "angle": 22.5}),
            b("mouthpiece", [6.5, 6.2, 10.4], [9.5, 9.2, 12.6], uv_silver),
            b("cone_mid", [5.5, 5.2, 6.6], [10.5, 10.2, 10.4], uv_ignis),
            b("cone_front", [4.2, 4.0, 3.2], [11.8, 11.4, 6.6], uv_frost),
            b("gold_rim", [3.8, 3.6, 2.4], [12.2, 11.8, 3.4], uv_gold, emit=10),
        ],
        "prop_guide_clipboard": [
            b("board", [3.6, 1.6, 7.2], [12.4, 13.6, 8.6], uv_wood),
            {
                "name": "court_sheet",
                "from": [4.2, 2.2, 6.95],
                "to": [11.8, 12.4, 8.85],
                "faces": {
                    "north": {"uv": uv_court, "texture": "#0"},
                    "south": {"uv": uv_court, "texture": "#0"},
                    "east": {"uv": uv_silver, "texture": "#0"},
                    "west": {"uv": uv_silver, "texture": "#0"},
                    "up": {"uv": uv_silver, "texture": "#0"},
                    "down": {"uv": uv_silver, "texture": "#0"},
                },
            },
            b("silver_clip", [6.0, 12.6, 6.6], [10.0, 14.4, 9.1], uv_silver),
            b("gold_pen", [12.5, 3.4, 7.3], [13.5, 11.4, 8.3], uv_gold, emit=8),
        ],
        "prop_podium_cup": [
            b("step_base", [2.2, 1.5, 5.2], [13.8, 3.0, 10.8], uv_slate),
            b("step_2nd", [2.6, 3.0, 5.8], [6.2, 6.8, 10.2], uv_silver),
            b("step_1st", [6.2, 3.0, 5.5], [9.8, 9.4, 10.5], uv_gold, emit=10),
            b("step_3rd", [9.8, 3.0, 5.8], [13.4, 5.4, 10.2], uv_ignis),
            b("crown_jewel", [6.8, 9.8, 6.8], [9.2, 12.4, 9.2], uv_gold, emit=14),
        ],
    }

    for prop_id, elements in props.items():
        mjson = {
            "credit": f"BosketBall 3D Prop ({prop_id})",
            "textures": {
                "0": "bosketball:item/lobby_props_atlas",
                "ball": "bosketball:item/ball_classic_south",
                "particle": "bosketball:item/lobby_props_atlas",
            },
            "elements": elements,
            "display": std_display,
        }
        (model_dir / f"{prop_id}.json").write_text(json.dumps(mjson, indent=2))
        (item_def_dir / f"{prop_id}.json").write_text(
            json.dumps(
                {
                    "model": {"type": "minecraft:model", "model": f"bosketball:item/{prop_id}"},
                    "hand_animation_on_swap": False,
                },
                indent=2,
            )
        )


def generate_1x1_server_logos(root_dir: Path):
    """
    Renders the official 1:1 Square BosketBall Logo in 3 crisp resolutions:
      - releases/BosketBall-Logo-1x1.png (512x512 HD server/Discord emblem)
      - releases/server-icon.png         (64x64 Minecraft server list icon)
      - src/main/resources/server-icon.png (64x64 bundled inside BosketBall.jar)
      - resourcepack/pack.png            (128x128 resource pack icon)
    """
    S = 512
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 1. Rounded Obsidian-Indigo Stadium Shield Background with Radial Spotlight Glow & 24K Gold Double Bezel
    bg_mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(bg_mask).rounded_rectangle([10, 10, S - 11, S - 11], radius=88, fill=255)

    bg_fill = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bp = bg_fill.load()
    cx_f, cy_f = S * 0.5, S * 0.42
    for y in range(S):
        for x in range(S):
            dist = math.hypot(x - cx_f, y - cy_f) / (S * 0.62)
            t = max(0.0, min(1.0, dist))
            # Warm royal-amber center aura fading into deep obsidian-midnight navy
            ang = math.atan2(y - cy_f, x - cx_f)
            ray = 0.08 * max(0.0, math.cos(ang * 8.0)) * (1.0 - t)
            r = int(58 * (1.0 - t) + 14 * t + 110 * ray)
            g = int(34 * (1.0 - t) + 16 * t + 65 * ray)
            b = int(86 * (1.0 - t) + 30 * t + 25 * ray)
            bp[x, y] = (min(255, r), min(255, g), min(255, b), 255)

    img = Image.composite(bg_fill, img, bg_mask)
    d = ImageDraw.Draw(img)
    # Outer dark ink + gleaming 24K gold double-bezel frame
    d.rounded_rectangle([10, 10, S - 11, S - 11], radius=88, outline=(18, 10, 24, 255), width=10)
    d.rounded_rectangle([18, 18, S - 19, S - 19], radius=80, outline=(255, 206, 56, 255), width=10)
    d.rounded_rectangle([26, 26, S - 27, S - 27], radius=73, outline=(255, 244, 168, 220), width=3)
    d.rounded_rectangle([30, 30, S - 31, S - 31], radius=70, outline=(42, 22, 54, 235), width=4)

    # 2. Heroic Crowned 3D Isometric Square Basketball Emblem (Center-Top: x=256, y=196)
    bx, by = 256, 198
    R_w, R_h = 118, 56  # Isometric cube diamond half-width & half-height
    cube_h = 118

    # White die-cut comic sticker halo + deep maroon ink border behind Crown + Cube Ball
    crown_outer = [
        (bx - 88, by - R_h - 8),
        (bx - 102, by - R_h - 74),
        (bx - 42, by - R_h - 38),
        (bx, by - R_h - 92),
        (bx + 42, by - R_h - 38),
        (bx + 102, by - R_h - 74),
        (bx + 88, by - R_h - 8),
    ]
    cube_hex = [
        (bx, by - R_h),
        (bx + R_w, by),
        (bx + R_w, by + cube_h),
        (bx, by + cube_h + R_h),
        (bx - R_w, by + cube_h),
        (bx - R_w, by),
    ]

    crest_mask = Image.new("L", (S, S), 0)
    cm_draw = ImageDraw.Draw(crest_mask)
    cm_draw.polygon(cube_hex, fill=255)
    cm_draw.polygon(crown_outer, fill=255)

    def dilate_mask(m: Image.Image, steps: int) -> Image.Image:
        out = m.copy()
        for _ in range(steps):
            out = out.filter(ImageFilter.MaxFilter(5))
        return out

    crest_ink = dilate_mask(crest_mask, 5)
    crest_sticker = dilate_mask(crest_ink, 4)
    img.paste((252, 253, 255, 255), (0, 0), crest_sticker)
    img.paste((38, 12, 24, 255), (0, 0), crest_ink)
    d = ImageDraw.Draw(img)

    # 3-face shaded isometric leather cube basketball
    top_face = [(bx, by - R_h), (bx + R_w, by), (bx, by + R_h), (bx - R_w, by)]
    left_face = [(bx - R_w, by), (bx, by + R_h), (bx, by + cube_h + R_h), (bx - R_w, by + cube_h)]
    right_face = [(bx, by + R_h), (bx + R_w, by), (bx + R_w, by + cube_h), (bx, by + cube_h + R_h)]

    d.polygon(top_face, fill=(255, 192, 62, 255))
    d.polygon(left_face, fill=(246, 122, 26, 255))
    d.polygon(right_face, fill=(196, 72, 16, 255))

    # Crisp specular highlight & seam lines on cube ball
    d.line([(bx, by - R_h + 4), (bx - R_w + 6, by + 2)], fill=(255, 236, 152, 255), width=6)
    d.line([(bx - R_w // 2, by - R_h // 2), (bx + R_w // 2, by + R_h // 2), (bx + R_w // 2, by + R_h // 2 + cube_h)], fill=(44, 16, 22, 255), width=10)
    d.line([(bx + R_w // 2, by - R_h // 2), (bx - R_w // 2, by + R_h // 2), (bx - R_w // 2, by + R_h // 2 + cube_h)], fill=(44, 16, 22, 255), width=10)
    d.line([(bx - R_w, by + cube_h // 2), (bx, by + R_h + cube_h // 2), (bx + R_w, by + cube_h // 2)], fill=(44, 16, 22, 255), width=9)
    d.polygon(cube_hex, outline=(38, 12, 24, 255), width=8)
    d.line([(bx - R_w, by), (bx, by + R_h), (bx + R_w, by)], fill=(54, 20, 26, 255), width=7)
    d.line([(bx, by + R_h), (bx, by + cube_h + R_h)], fill=(54, 20, 26, 255), width=7)

    # Golden Royal Crown perched on top of the cube basketball!
    d.polygon(crown_outer, fill=(255, 218, 52, 255), outline=(38, 12, 24, 255), width=8)
    # Crown shading band & jewel highlights
    d.polygon([
        (bx - 84, by - R_h - 22), (bx + 84, by - R_h - 22),
        (bx + 86, by - R_h - 8), (bx - 86, by - R_h - 8),
    ], fill=(228, 156, 22, 255), outline=(38, 12, 24, 255), width=5)
    # Center ruby & side cyan diamonds on crown
    d.polygon([(bx, by - R_h - 54), (bx + 12, by - R_h - 40), (bx, by - R_h - 26), (bx - 12, by - R_h - 40)], fill=(255, 62, 78, 255), outline=(38, 12, 24, 255), width=4)
    for jx in (bx - 48, bx + 48):
        d.polygon([(jx, by - R_h - 44), (jx + 9, by - R_h - 34), (jx, by - R_h - 24), (jx - 9, by - R_h - 34)], fill=(56, 224, 255, 255), outline=(38, 12, 24, 255), width=3)

    # 3. Stacked 2-Line Comic-Extruded Banner: "BOSKET" over "BALL" (Ultra-readable even at 64x64!)
    text_face = Image.new("L", (S, S), 0)
    td = ImageDraw.Draw(text_face)

    def layout_word(word_str: str, base_y: float, ch_h: float, cw_list: list, gap_px: float):
        tot_w = sum(cw_list) + (len(word_str) - 1) * gap_px
        cx_cur = (S - tot_w) * 0.5
        for idx_c, (ch_c, cw_c) in enumerate(zip(word_str, cw_list)):
            arch_dy = 7.0 * math.sin((idx_c / max(1, len(word_str) - 1)) * math.pi)
            _draw_vector_comic_letter(td, ch_c, cx_cur, base_y - arch_dy, cw_c, ch_h, slant=4.5)
            cx_cur += cw_c + gap_px

    layout_word("BOSKET", base_y=320.0, ch_h=72.0, cw_list=[58, 58, 56, 64, 54, 54], gap_px=8.0)
    layout_word("BALL", base_y=402.0, ch_h=68.0, cw_list=[62, 64, 54, 54], gap_px=9.0)

    text_extrude = Image.new("L", (S, S), 0)
    for step_i in range(1, 13):
        text_extrude = Image.composite(
            Image.new("L", (S, S), 255),
            text_extrude,
            text_face.transform((S, S), Image.Transform.AFFINE, (1, 0, -step_i * 0.48, 0, 1, -step_i)),
        )

    t_union = Image.composite(Image.new("L", (S, S), 255), text_extrude, text_face)
    t_ink = dilate_mask(t_union, 4)
    t_sticker = dilate_mask(t_ink, 3)

    tfp = text_face.load()
    tep = text_extrude.load()
    tip = t_ink.load()
    tsp = t_sticker.load()
    ipx = img.load()

    for y in range(295, S - 14):
        local_t = ((y - 314) % 84) / 70.0
        local_t = max(0.0, min(1.0, local_t))
        fr = 255
        fg = int(248 * (1.0 - local_t) + 124 * local_t)
        fb = int(72 * (1.0 - local_t) + 16 * local_t)
        for x in range(14, S - 14):
            if tfp[x, y] > 128:
                top_edge = (y >= 4 and tfp[x, y - 4] <= 128)
                sheen = ((x + y * 2) % 64) in (10, 11, 12, 13, 14)
                if top_edge:
                    ipx[x, y] = (255, 254, 202, 255)
                elif sheen:
                    ipx[x, y] = (255, min(255, fg + 30), min(255, fb + 40), 255)
                else:
                    ipx[x, y] = (fr, fg, fb, 255)
            elif tep[x, y] > 128:
                ipx[x, y] = (196, 30, 40, 255)
            elif tip[x, y] > 128:
                ipx[x, y] = (38, 10, 22, 255)
            elif tsp[x, y] > 128:
                ipx[x, y] = (252, 253, 255, 255)

    # Four comic 4-point sparkle stars around the crown & ball!
    d = ImageDraw.Draw(img)
    for sx, sy, sr in [(76, 148, 18), (436, 148, 18), (96, 266, 13), (416, 266, 13)]:
        d.polygon([
            (sx, sy - sr), (sx + 5, sy - 5), (sx + sr, sy), (sx + 5, sy + 5),
            (sx, sy + sr), (sx - 5, sy + 5), (sx - sr, sy), (sx - 5, sy - 5),
        ], fill=(255, 248, 168, 255), outline=(38, 12, 24, 255), width=3)

    releases_dir = root_dir / "releases"
    releases_dir.mkdir(parents=True, exist_ok=True)
    img.save(releases_dir / "BosketBall-Logo-1x1.png")

    icon64 = img.resize((64, 64), Image.Resampling.LANCZOS)
    icon64.save(releases_dir / "server-icon.png")

    res_icon_dir = root_dir / "src" / "main" / "resources"
    res_icon_dir.mkdir(parents=True, exist_ok=True)
    icon64.save(res_icon_dir / "server-icon.png")

    rp_dir = root_dir / "resourcepack"
    if rp_dir.exists():
        img.resize((128, 128), Image.Resampling.LANCZOS).save(rp_dir / "pack.png")



def generate_pixel_art_item_icons(item_tex_dir: Path, model_dir: Path, item_def_dir: Path, rp_dir: Path = None):
    """
    Generates 19 richly shaded 16x16 pixel-art item icons (exact image-10.png craft!)
    + bosketball:ui_invisible (100% transparent slot filler so zero stained-glass panes ever show!).
    """
    if rp_dir is not None:
        generate_custom_container_gui(rp_dir)

    # Pure 16x16 transparent item for invisible GUI padding!
    Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(item_tex_dir / "ui_invisible.png")
    (model_dir / "ui_invisible.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "bosketball:item/ui_invisible"},
        "display": {"gui": {"scale": [0.0, 0.0, 0.0]}},
    }, indent=2))
    (item_def_dir / "ui_invisible.json").write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": "bosketball:item/ui_invisible"},
    }, indent=2))

    PAL = {
        ".": (0, 0, 0, 0),
        "K": (18, 12, 26, 255),      # Deep obsidian ink outline
        "D": (42, 24, 78, 255),      # Dark violet portal frame
        "P": (92, 46, 172, 255),     # Mid royal purple
        "V": (148, 84, 242, 255),    # Bright violet glow
        "L": (198, 152, 255, 255),   # Lavender vortex swirl
        "X": (234, 206, 255, 255),   # Pale lilac core highlight
        "W": (248, 250, 255, 255),   # Specular white
        "O": (58, 38, 20, 255),      # Warm dark walnut outline (chest & book!)
        "B": (128, 84, 42, 255),     # Rich oak shadow
        "C": (188, 132, 68, 255),    # Honey-oak midtone
        "H": (228, 178, 102, 255),   # Warm golden-oak highlight
        "J": (212, 168, 114, 255),   # Parchment tan leather cover (203500.png book!)
        "j": (168, 124, 76, 255),    # Tan leather bevel shadow
        "k": (72, 48, 32, 255),      # Embossed player bust dark brown
        "G": (214, 146, 22, 255),    # Deep 24K gold
        "Y": (255, 214, 58, 255),    # Bright 24K gold
        "Z": (255, 248, 168, 255),   # Pale gold specular shine
        "S": (62, 68, 84, 255),      # Dark slate steel outline
        "m": (118, 128, 148, 255),   # Cool steel shadow
        "M": (162, 172, 192, 255),   # Brushed silver midtone
        "I": (208, 216, 232, 255),   # Bright silver highlight
        "Q": (22, 92, 168, 255),     # Cobalt blue
        "A": (56, 188, 255, 255),    # Sky cyan
        "E": (142, 238, 255, 255),   # Ice cyan highlight
        "N": (22, 148, 86, 255),     # Deep emerald
        "T": (62, 232, 138, 255),    # Neon mint
        "R": (168, 28, 38, 255),     # Deep crimson
        "F": (242, 68, 58, 255),     # Bright scarlet/flame
        "U": (255, 138, 38, 255),    # Blaze orange
    }

    ICONS_16 = {
        # 1. Slot 0: Glowing Obsidian-Violet Vortex Portal (exact 203500.png!)
        "icon_portal": [
            "..KKKKKKKKKKKK..",
            "..KDDDDDDDDDDK..",
            "..KDPPPPPPPPDK..",
            "..KDPVVLLVVPDK..",
            "..KDPVLXXLVPDK..",
            "..KDPLXPPXLPDK..",
            "..KDPLXPXLLPDK..",
            "..KDPLXXPLLPDK..",
            "..KDPLLPXXLPDK..",
            "..KDPVLXXLVPDK..",
            "..KDPVVLLVVPDK..",
            "..KDPVVPPVVPDK..",
            "..KDPPPPPPPPDK..",
            "..KDDDDDDDDDDK..",
            "..KKKKKKKKKKKK..",
            "................",
        ],
        # 2. Slot 1: Scalloped 24K Gold & Royal-Amethyst Star Shield (exact 203500.png!)
        "icon_star_shield": [
            "................",
            ".....OOOOOO.....",
            "...OOZZYYZZOO...",
            ".OOZYGGGGGGYZOO.",
            ".OZYGPPVVPPGYZO.",
            ".OYGPPVZZVPPGYO.",
            "OZYGPVYYYYVPGYZO",
            "OYGGPZYZZYZPGGYO",
            "OYGPPVYYYYVPGGYO",
            "OZYGPPYGGYPPGYZO",
            ".OYGPPVPPVPPGYO.",
            ".OOZYGGGGGGYZOO.",
            "...OOYYYYYYOO...",
            ".....OOOOOO.....",
            "................",
            "................",
        ],
        # 3. Slot 4: Warm Domed Honey-Oak Treasure Chest w/ Brushed Silver Lock Clasp (exact 203500.png!)
        "icon_chest": [
            "................",
            "..OOOOOOOOOOOO..",
            ".OHHHHHHHHHHHHO.",
            ".OHCCCCCCCCCCBO.",
            ".OHCCCCCCCCCCBO.",
            ".OBBBBBWWBBBBBO.",
            ".OHHHHHIIMHHHHO.",
            ".OHCCCCIMMCCCBO.",
            ".OHCCCCmSMCCCBO.",
            ".OHCCCCCCCCCCBO.",
            ".OCBBBBBBBBBBCO.",
            ".OBBBBBBBBBBBBO.",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
            "................",
        ],
        # 4. Slot 7: Tan Leather Career/Friends Journal w/ Embossed Player Bust & Crisp White Page Edge (exact 203500.png!)
        "icon_book": [
            "................",
            "..OOOOOOOOOOOO..",
            ".OJJJJJJJJJJWBO.",
            ".OJjjjjjjjJJWBO.",
            ".OJjJJkkJJjJWBO.",
            ".OJjJJkkJJjJWBO.",
            ".OJjJJJJJJjJWBO.",
            ".OJjJkkkkJjJWBO.",
            ".OJjkkkkkkjJWBO.",
            ".OJjJJJJJJjJWBO.",
            ".OJjjjjjjjJJWBO.",
            ".OBBBBBBBBBBWBO.",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
            "................",
        ],
        # 5. Slot 8: Brushed Silver & Slate-Steel 2-Tier Stepped Podium (exact 203500.png!)
        "icon_podium": [
            "................",
            "..SSSSSSSSSS....",
            "..SWWWWWIIIS....",
            "..SWIIIIIMMS....",
            "..SWIIIIIMMS....",
            "..SWIIIIIMMSSSS.",
            "..SSSSSSSSSIMMS.",
            ".SSWWIIIIMMIIMS.",
            ".SWIIIIIIMMMMMS.",
            ".SWIIIIIIMMMMMS.",
            ".SWIIIIIIMMMMMS.",
            ".SWMMMMMMmmmmMS.",
            ".SSSSSSSSSSSSSS.",
            "................",
            "................",
            "................",
        ],
        "icon_pass": [
            "........KKKK....",
            ".......KAEEAK...",
            "..KKKKKKAEEEAK..",
            ".KAEEEEEEEEEEAK.",
            "KWWAAAAAAAAAAEAK",
            ".KQQQQQQQAAAAAK.",
            ".......KQAEEAK..",
            "......KQQAEEAK..",
            ".......KKKKKK...",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_crossover": [
            ".......KKKKK....",
            "......KZZZZK....",
            ".....KYYYYK.....",
            "....KYYYYK......",
            "...KYYYYKKKKK...",
            "..KZZZZZZZZYK...",
            "..KKKKKYYYYK....",
            ".....KYYYYK.....",
            "....KYYYYK......",
            "...KYYGGK.......",
            "..KYYKK.........",
            "..KKK...........",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_dunk": [
            "......KK..KK....",
            ".....KFFKKFFK...",
            "....KFFUYYUFFK..",
            "...KFFUYYYYUFFK.",
            "..KFFUYYZZYYUFFK",
            "..KFUYYZWWZYYUFK",
            "..KFUYYZZZZYYUFK",
            "..KFFUYYYYYYUFFK",
            "...KFFUUUUUUFFK.",
            "..KKKKKKKKKKKKKK",
            "..KUUUUUUUUUUUUK",
            "...KKKKKKKKKKKK.",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_defense": [
            "..KKKKKKKKKKKK..",
            ".KEEEEEEEEEEEEK.",
            "KEAAQQQQQQQQAAEK",
            "KEAQAAYYYYAAQAEK",
            "KEAQAQYYYYQAQAEK",
            "KEAQAAYYYYAAQAEK",
            ".KEAAQQYYQQAAEK.",
            ".KEAAAQQQQAAAEK.",
            "..KEAAAAAAAAEK..",
            "...KEAAAAAAEK...",
            "....KEAAAAEK....",
            ".....KEAAEK.....",
            "......KKKK......",
            "................",
            "................",
            "................",
        ],
        "icon_bot_on": [
            "....KKKKKKKK....",
            "..KKIIIIIIIIKK..",
            ".KIIWWWWWWWWIIK.",
            ".KIMSSSSSSSSMIK.",
            "KIMSTTTTTTTTSMIK",
            "KIMSTWWTTWWTSMIK",
            "KIMSTTTTTTTTSMIK",
            "KIMSSSSSSSSSSMIK",
            ".KIMMIIIIIIIMIK.",
            "..KKMMMMMMMMKK..",
            "....KKKKKKKK....",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_bot_off": [
            "....KKKKKKKK....",
            "..KKMMMMMMMMKK..",
            ".KMMIIIIIIIIMMK.",
            ".KMSSSSSSSSSSMK.",
            "KMSSMMMMMMMMSSMK",
            "KMSSMKKMMKKMSSMK",
            "KMSSMMMMMMMMSSMK",
            "KMSSSSSSSSSSSSMK",
            ".KSSSSSSSSSSSSK.",
            "..KKSSSSSSSSKK..",
            "....KKKKKKKK....",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_leave": [
            "...KKKKKKKKKK...",
            "..KFFFFFFFFFRK..",
            "..KFRRRRRRRRPK..",
            "..KFRWWWWWWFRK..",
            "..KFRWFFFFWFRK..",
            "..KFRWWWWWWFRK..",
            "..KFRRRRRRZYRK..",
            "..KFRRRRRRGYRK..",
            "..KFRRRRRRRRRK..",
            "..KFRRRRRRRRRK..",
            "..KRRRRRRRRRRK..",
            ".KKKKKKKKKKKKKK.",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_friends": [
            "....KKKK..KKKK..",
            "...KTTTTKKAAAAK.",
            "...KTWWTKKAEEAK.",
            "...KTTTTKKAAAAK.",
            "....KKKK..KKKK..",
            "..KKNNNNKKQQQQKK",
            ".KNTTTTTTTTAAAEK",
            "KNTTTTTTTTTAAAAK",
            "KNNNNNNNNNNQQQQK",
            ".KKKKKKKKKKKKKK.",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_settings": [
            "......KKKK......",
            ".....KIIIIK.....",
            "..KK.KIMMII.KK..",
            ".KIIKKIMMMKKIIK.",
            ".KIMIIIYYIIIMIK.",
            "..KMMIYYYYIMMK..",
            "KKIMMIYKKYIMMIKK",
            "KIIMMIYKKYIMMIIK",
            "KKIMMIYYYYIMMIKK",
            "..KMMIIYYIIMMK..",
            ".KIMIIIMMMIIMIK.",
            ".KIIKKMMMMKKIIK.",
            "..KK..KMMK..KK..",
            "......KKKK......",
            "................",
            "................",
        ],
        "icon_history": [
            ".....KKKKKK.....",
            "....KYYYYYYK....",
            ".....KKGGKK.....",
            "...KKYYYYYYKK...",
            "..KYZZZZZZZZYK..",
            ".KYZWWKWWWWWZYK.",
            ".KYZWWKWWWWWZYK.",
            ".KYZWWKKKKWWZYK.",
            ".KYZWWWWWWWWZYK.",
            "..KYGGGGGGGGYK..",
            "...KKGGGGGGKK...",
            ".....KKKKKK.....",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_tags": [
            "......KKKK......",
            "....KKYYYYKK....",
            "...KYYKWWKYYK...",
            "..KYYYKWWKYYYYK.",
            "..KYYGGGGZYYYYYK",
            "...KYGPPPPYYYYK.",
            "....KGPZZPPYYK..",
            "...KGPZYYZPPGK..",
            "..KGPPYYYYPPGK..",
            "..KGPZYYYYZPGK..",
            "...KGPPPPPPGK...",
            "....KGGGGGGK....",
            ".....KKKKKK.....",
            "................",
            "................",
            "................",
        ],
        "icon_coin_stack": [
            "................",
            "....KKKKKKKK....",
            "..KKZZZZZZZZKK..",
            ".KZZYYYYYYYYZZK.",
            ".KYYGGGGGGGGYYK.",
            "..KKYYYYYYYYKK..",
            ".KZZKKKKKKKKZZK.",
            ".KYYZZZZZZZZYYK.",
            "..KKYYYYYYYYKK..",
            ".KZZKKKKKKKKZZK.",
            ".KYYZZZZZZZZYYK.",
            ".KGGGGYYYYGGGGK.",
            "..KKGGGGGGGGKK..",
            "....KKKKKKKK....",
            "................",
            "................",
        ],
        "icon_shelves": [
            ".KKKKKKKKKKKKKK.",
            "KZZZZZZZZZZZZZZK",
            "KYYYYYYYYYYYYYYK",
            "KSFFSSAAWWSSVVSK",
            "KSFFSSAAWWSSVVSK",
            "KGGGGGGGGGGGGGGK",
            "KSDDSSUUYYSSNNTK",
            "KSDDSSUUYYSSNNTK",
            "KGGGGGGGGGGGGGGK",
            "KSPPSSAACCSSZZYK",
            "KSPPSSAACCSSZZYK",
            "KGGGGGGGGGGGGGGK",
            ".KKKKKKKKKKKKKK.",
            "................",
            "................",
            "................",
        ],
        "dunk_tomahawk": [
            ".........KKKK...",
            "........KUUYYK..",
            ".......KUFFUUK..",
            "......KKUUUUUK..",
            ".....KYYKKKKK...",
            "....KYYZK.......",
            "...KYYZK.KKKK...",
            "..KYYGK.KFFFFK..",
            "..KYGK..KRRRRK..",
            "...KK....KKKK...",
            "..KRRRRRRRRRRK..",
            ".KFFFFFFFFFFFFK.",
            "..KWWWWWWWWWWK..",
            "...KWWWWWWWWK...",
            "....KKKKKKKK....",
            "................",
        ],
        "dunk_windmill": [
            "....KKKKKK......",
            "..KKEEEEEEKK....",
            ".KEAAKKKKAAEK...",
            "KEAK......KAEK..",
            "KAK...KK...KUUUK",
            "KAK..KYYK..KUFFK",
            "KAK...KK...KUUUK",
            "KEAK......KAEK..",
            ".KEAAKKKKAAEK...",
            "..KKAAAAAAAQK...",
            "...KKKKKKKKK....",
            "..KRRRRRRRRRRK..",
            ".KFFFFFFFFFFFFK.",
            "..KWWWWWWWWWWK..",
            "...KKKKKKKKKK...",
            "................",
        ],
        "dunk_spin_360": [
            "...KKKKKKKKKK...",
            ".KKLLLLLLLLLLKK.",
            "KLLVVKKKKKKVVLLK",
            "KLVK..KKKK..KVLK",
            "KLK..KUUUUUK.KLK",
            "KVK..KUFFUUK..KK",
            "KVK...KUUUK.....",
            "KLK....KKK..KKKK",
            "KLVK......KKZZYK",
            "KLLVVKKKKVVYYYYK",
            ".KKLLLLLLYYYYKK.",
            "...KKKKKKKKKK...",
            "..KRRRRRRRRRRK..",
            ".KFFFFFFFFFFFFK.",
            "..KWWWWWWWWWWK..",
            "...KKKKKKKKKK...",
        ],
        "dunk_between_legs": [
            ".....KKKKKK.....",
            "....KYYYYYYK....",
            "....KYZZZZYK....",
            ".....KYYYYK.....",
            "...KKKKKKKKKK...",
            "..KAAQQQQQQAAK..",
            "..KAK.KKKK.KAK..",
            "..KK.KUUUUK.KK..",
            ".....KUFFUK.....",
            "...KKKUUUUKKK...",
            "..KQQK.KK.KQQK..",
            "..KQQK....KQQK..",
            ".KRRRRRRRRRRRRK.",
            "..KWWWWWWWWWWK..",
            "...KKKKKKKKKK...",
            "................",
        ],
        "dunk_meteor_jam": [
            "..........KKKK..",
            "........KKZZWWK.",
            ".......KZZYYYYWK",
            "......KZYYUUUYYK",
            ".....KYYUUFFFUUK",
            "....KYUUFFZZFFUK",
            "...KYUFFZWWWWZFK",
            "..KUUFFZWWUUWWFK",
            "..KUFFZWUUUUUWFK",
            "...KFFUUUUUUFFK.",
            "....KFFFFFFFFK..",
            "..KRRRRRRRRRRRRK",
            ".KFFFFFFFFFFFFFF",
            "..KWWWWWWWWWWWWK",
            "...KKKKKKKKKKKK.",
            "................",
        ],
        "icon_arena": [
            "..KK........KK..",
            ".KZZK......KZZK.",
            "..KK..KKKK..KK..",
            "....KKAAAAKK....",
            "..KKAEEEEEEAKK..",
            ".KAAHHHHHHHHAAK.",
            "KAAHCCCCCCCCHAAK",
            "KQQHCCCCCCCCHOQK",
            "KQQBBBBBBBBBBQQK",
            ".KSSSSSSSSSSSSK.",
            "..KKKKKKKKKKKK..",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_back": [
            "................",
            "......KKKK......",
            ".....KFFRFK.....",
            "....KFFRRFK.....",
            "...KFFRFFKKKKKK.",
            "..KFFRFFFFFFFFK.",
            ".KFFRZZZZZZZZFK.",
            "..KFFRGGGGGGGFK.",
            "...KFFRRRRRRRKK.",
            "....KFFRRFKKKK..",
            ".....KFFRFK.....",
            "......KKKK......",
            "................",
            "................",
            "................",
            "................",
        ],
        # 19. Center-Screen Tab Scoreboard Card Icon (Ignis vs Frost + Gold Header!)
        "icon_tab": [
            ".KKKKKKKKKKKKKK.",
            "KYYYYYYYYYYYYYYK",
            "KGGGGGGGGGGGGGGK",
            "KSSSSSSSSSSSSSSK",
            "KSFFWWWWSSAAWWSK",
            "KSFFWWWWSSAAWWSK",
            "KSSSSSSSSSSSSSSK",
            "KSFFWWWWSSAAWWSK",
            "KSFFWWWWSSAAWWSK",
            "KSSSSSSSSSSSSSSK",
            ".KKKKKKKKKKKKKK.",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        # 20 & 21. Custom Page Navigation Arrows (icon_prev / icon_next)
        "icon_prev": [
            "................",
            ".......KKKK.....",
            "......KYYYYK....",
            ".....KYYZZYK....",
            "....KYYZZKKKKKK.",
            "...KYYZZZZZZZZK.",
            "..KYYZZZZZZZZZK.",
            "...KYYGGGGGGGGK.",
            "....KYYGGKKKKKK.",
            ".....KYYGGYK....",
            "......KGGGGK....",
            ".......KKKK.....",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_next": [
            "................",
            ".....KKKK.......",
            "....KYYYYK......",
            "....KYZZYYK.....",
            ".KKKKKKZZYYK....",
            ".KZZZZZZZZYYK...",
            ".KZZZZZZZZZYYK..",
            ".KGGGGGGGGYYK...",
            ".KKKKKKGGYYK....",
            "....KYGGYYK.....",
            "....KGGGGK......",
            ".....KKKK.......",
            "................",
            "................",
            "................",
            "................",
        ],
    }

    for icon_id, rows in ICONS_16.items():
        pts = []
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in PAL and PAL[ch][3] > 0:
                    pts.append((x, y, PAL[ch]))
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        if pts:
            min_x = min(p[0] for p in pts)
            max_x = max(p[0] for p in pts)
            min_y = min(p[1] for p in pts)
            max_y = max(p[1] for p in pts)
            bw = max_x - min_x + 1
            bh = max_y - min_y + 1
            ox = (16 - bw) // 2 - min_x
            oy = (16 - bh) // 2 - min_y
            for x, y, col in pts:
                nx, ny = x + ox, y + oy
                if 0 <= nx < 16 and 0 <= ny < 16:
                    px[nx, ny] = col
        img.save(item_tex_dir / f"{icon_id}.png")

        # Court action/stance icons stay invisible in 1st/3rd-person hands so players show real bare basketball hands on court!
        court_hand_hidden = icon_id in {
            "icon_pass", "icon_crossover", "icon_dunk", "icon_defense",
            "icon_bot_on", "icon_bot_off", "icon_leave",
            "dunk_tomahawk", "dunk_windmill", "dunk_spin_360", "dunk_between_legs", "dunk_meteor_jam",
        }
        fp_display = (
            {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]}
            if court_hand_hidden
            else {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}
        )
        tp_display = (
            {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]}
            if court_hand_hidden
            else {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]}
        )
        model_json = {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": f"bosketball:item/{icon_id}",
            },
            "display": {
                "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
                "firstperson_righthand": fp_display,
                "firstperson_lefthand": fp_display,
                "thirdperson_righthand": tp_display,
                "thirdperson_lefthand": tp_display,
            },
        }
        (model_dir / f"{icon_id}.json").write_text(json.dumps(model_json, indent=2))
        (item_def_dir / f"{icon_id}.json").write_text(
            json.dumps(
                {
                    "model": {"type": "minecraft:model", "model": f"bosketball:item/{icon_id}"},
                    "hand_animation_on_swap": False,
                },
                indent=2,
            )
        )


def generate_custom_hooper_player_skins(rp_dir: Path):
    """
    Generates 4 custom 64x64 Minecraft 26.2 Player Skins for:
      - star_ignis   (Refortorr: Dark Crimson-Gold Hooded #23 Ignis Champion, matching 202328.png!)
      - star_frost   (A_Koop: Winter Penguin-Scarf Sky-Frost #7 All-Star, matching 202328.png & 202456.webp!)
      - star_mvp     (DiAM0ndD0g15: Obsidian Stealth #15 MVP Legend, matching 202328.png!)
      - defender_bot (Pro AI Lockdown Defender #99 with glowing cyan cyber-visor!)
    Saved in both entity/player/wide/ and entity/player/ so ResolvableProfile.SkinPatch & ObjectContents.playerHead resolve cleanly!
    """
    wide_dir = rp_dir / "assets" / "bosketball" / "textures" / "entity" / "player" / "wide"
    base_dir = rp_dir / "assets" / "bosketball" / "textures" / "entity" / "player"
    wide_dir.mkdir(parents=True, exist_ok=True)
    base_dir.mkdir(parents=True, exist_ok=True)

    specs = [
        # (id, skin_rgb, hood_rgb, jersey_main, jersey_trim, eye_rgb, penguin_mode, visor_mode)
        ("star_ignis",   (218, 168, 134), (28, 26, 34),   (186, 34, 42),  (250, 194, 48),  (255, 76, 58),  False, False),
        ("star_frost",   (242, 246, 252), (42, 48, 62),   (36, 136, 218), (118, 232, 212), (28, 36, 52),   True,  False),
        ("star_mvp",     (224, 228, 236), (24, 26, 32),   (38, 42, 54),   (246, 198, 56),  (32, 34, 42),   False, False),
        ("defender_bot", (62, 72, 92),    (26, 32, 46),   (24, 88, 158),  (82, 246, 196),  (64, 238, 255), False, True),
    ]

    for skin_id, face_c, hood_c, j_main, j_trim, eye_c, is_penguin, is_visor in specs:
        im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)

        # 1. Head base [0..31, 0..15]
        d.rectangle([8, 0, 15, 7], fill=(*hood_c, 255))
        d.rectangle([16, 0, 23, 7], fill=(*face_c, 255))
        d.rectangle([0, 8, 31, 15], fill=(*hood_c, 255))
        d.rectangle([8, 9, 15, 15], fill=(*face_c, 255))  # Front face

        if is_penguin:
            # Cute winter penguin face + red Santa/beanie cap + mint scarf (exact A_Koop vibe!)
            d.rectangle([8, 8, 15, 10], fill=(214, 38, 48, 255))
            d.rectangle([8, 10, 15, 11], fill=(248, 250, 255, 255))
            d.rectangle([9, 12, 10, 12], fill=(24, 28, 38, 255))
            d.rectangle([13, 12, 14, 12], fill=(24, 28, 38, 255))
            d.rectangle([11, 13, 12, 14], fill=(248, 162, 36, 255))  # Beak!
        elif is_visor:
            # Cyber lockdown visor across eyes
            d.rectangle([8, 11, 15, 12], fill=(*eye_c, 255))
            d.rectangle([9, 14, 14, 14], fill=(38, 46, 62, 255))
        else:
            # Expressive eyes + brow shadow
            d.rectangle([8, 8, 15, 9], fill=(*hood_c, 255))
            d.rectangle([9, 11, 10, 12], fill=(*eye_c, 255))
            d.rectangle([13, 11, 14, 12], fill=(*eye_c, 255))

        # Hat / Hood 3D outer layer rim [32..63, 0..15]
        d.rectangle([40, 0, 47, 7], fill=(*hood_c, 255))
        d.rectangle([32, 8, 39, 15], fill=(*hood_c, 255))
        d.rectangle([48, 8, 63, 15], fill=(*hood_c, 255))
        d.rectangle([40, 8, 47, 9], fill=(*j_trim, 255) if is_penguin else (*hood_c, 255))

        # 2. Torso Jersey [16..39, 16..31]
        d.rectangle([20, 16, 27, 19], fill=(*j_main, 255))
        d.rectangle([28, 16, 35, 19], fill=(24, 28, 38, 255))
        d.rectangle([16, 20, 39, 31], fill=(*j_main, 255))
        # Collar V-neck + side trim stripes + gold number bar on chest [20..27, 20..31]
        d.rectangle([22, 20, 25, 21], fill=(*j_trim, 255))
        d.rectangle([20, 20, 20, 31], fill=(*j_trim, 255))
        d.rectangle([27, 20, 27, 31], fill=(*j_trim, 255))
        # Crisp pixel jersey number / crest on chest & back
        d.rectangle([22, 23, 25, 28], fill=(248, 250, 255, 255))
        d.rectangle([23, 24, 24, 27], fill=(*j_main, 255))
        d.rectangle([34, 23, 37, 28], fill=(*j_trim, 255))

        # 3. Right & Left Arms (sleeveless pro shoulders + shooting sleeve + hands!)
        # Right Arm [40..55, 16..31]
        d.rectangle([44, 16, 47, 19], fill=(*face_c, 255))
        d.rectangle([48, 16, 51, 19], fill=(*face_c, 255))
        d.rectangle([40, 20, 55, 23], fill=(*face_c, 255))
        d.rectangle([40, 24, 55, 29], fill=(*j_main, 255))  # Compression shooting sleeve!
        d.rectangle([40, 29, 55, 29], fill=(*j_trim, 255))
        d.rectangle([40, 30, 55, 31], fill=(*face_c, 255))

        # Left Arm [32..47, 48..63]
        d.rectangle([36, 48, 39, 51], fill=(*face_c, 255))
        d.rectangle([40, 48, 43, 51], fill=(*face_c, 255))
        d.rectangle([32, 52, 47, 59], fill=(*face_c, 255))
        d.rectangle([32, 59, 47, 60], fill=(*j_trim, 255))  # Sweatband!
        d.rectangle([32, 61, 47, 63], fill=(*face_c, 255))

        # 4. Right & Left Legs (Basketball Shorts + High-Top Court Sneakers!)
        for bx, by in [(0, 16), (16, 48)]:
            d.rectangle([bx + 4, by, bx + 7, by + 3], fill=(24, 28, 38, 255))
            d.rectangle([bx + 8, by, bx + 11, by + 3], fill=(244, 246, 252, 255))
            # Shorts upper half
            d.rectangle([bx, by + 4, bx + 15, by + 9], fill=(24, 28, 38, 255))
            d.rectangle([bx, by + 9, bx + 15, by + 9], fill=(*j_trim, 255))
            # Calves / socks
            d.rectangle([bx, by + 10, bx + 15, by + 12], fill=(238, 242, 250, 255))
            # High-top sneakers!
            d.rectangle([bx, by + 13, bx + 15, by + 14], fill=(*j_main, 255))
            d.rectangle([bx, by + 15, bx + 15, by + 15], fill=(248, 250, 255, 255))

        im.save(wide_dir / f"{skin_id}.png")
        im.save(base_dir / f"{skin_id}.png")


def generate_tag_collector_card_items(font_tex_dir: Path, item_tex_dir: Path, model_dir: Path, item_def_dir: Path):
    """
    Generates a bespoke 32x32 Collector Tag Card item icon (tag_card_<id>) for every font/tag_<id>.png
    so every single ChatTag in the Shop & Tags Vault displays its own custom colors & pixel badge!
    """
    for pill_path in sorted(font_tex_dir.glob("tag_*.png")):
        tag_id = pill_path.stem[len("tag_"):]
        item_id = f"tag_card_{tag_id}"
        src = Image.open(pill_path).convert("RGBA")
        sw, sh = src.size
        spx = src.load()

        # Find tight opaque bounding box (ignore alpha <= 8 edge-pin pixel)
        min_x, min_y, max_x, max_y = sw, sh, 0, 0
        accent_samples = []
        for y in range(sh):
            for x in range(sw):
                r, g, b, a = spx[x, y]
                if a > 20:
                    if x < min_x: min_x = x
                    if y < min_y: min_y = y
                    if x > max_x: max_x = x
                    if y > max_y: max_y = y
                    sat = max(r, g, b) - min(r, g, b)
                    if sat > 45 and max(r, g, b) > 90:
                        accent_samples.append((sat + max(r, g, b), r, g, b))

        if max_x <= min_x or max_y <= min_y:
            cropped = src
        else:
            cropped = src.crop((min_x, min_y, max_x + 1, max_y + 1))

        if accent_samples:
            accent_samples.sort(reverse=True)
            top_n = accent_samples[:max(1, len(accent_samples) // 5)]
            ar = sum(c[1] for c in top_n) // len(top_n)
            ag = sum(c[2] for c in top_n) // len(top_n)
            ab = sum(c[3] for c in top_n) // len(top_n)
        else:
            ar, ag, ab = 251, 191, 36

        hi_col = (min(255, ar + 42), min(255, ag + 42), min(255, ab + 42), 255)
        mid_col = (ar, ag, ab, 255)
        shd_col = (max(18, int(ar * 0.52)), max(18, int(ag * 0.52)), max(24, int(ab * 0.52)), 255)

        card = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        d = ImageDraw.Draw(card)

        # Sleek chamfered collector ticket plaque [1..30, 6..25]
        d.rectangle([2, 5, 29, 26], fill=(12, 15, 24, 255))
        d.rectangle([1, 6, 30, 25], fill=(12, 15, 24, 255))
        # Glowing metallic accent trim
        d.rectangle([2, 6, 29, 25], fill=shd_col)
        d.line([(2, 6), (29, 6)], fill=hi_col)
        d.line([(2, 7), (2, 24)], fill=mid_col)
        d.line([(29, 7), (29, 24)], fill=mid_col)
        # Inner dark velvet recess
        d.rectangle([3, 8, 28, 23], fill=(17, 22, 35, 255))
        # Subtle top gold corner rivets
        card.putpixel((4, 9), (255, 220, 92, 255))
        card.putpixel((27, 9), (255, 220, 92, 255))

        # Fit cropped pill cleanly inside [4..27, 11..20] (24x10 area)
        cw, ch = cropped.size
        target_w = min(24, cw)
        target_h = min(10, max(7, ch))
        fitted = cropped.resize((target_w, target_h), Image.Resampling.BILINEAR)
        fx = 4 + (24 - target_w) // 2
        fy = 11 + (10 - target_h) // 2
        card.alpha_composite(fitted, (fx, fy))

        card.save(item_tex_dir / f"{item_id}.png")

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


