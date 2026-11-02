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
    """236x18 (2x HiDPI -> 118x9 GUI px) ornate golden diamond & curled filigree rule matching image-8.png!
    Positioned high in its 9px cell (y = 0..12 in 2x space) so Row 15 leaves crisp breathing room above Row 14 [JOUEUR]
    without spending an extra blank scoreboard row, fitting all 5 sections + 5 values cleanly in 15 lines!"""
    w, h = 236, 18
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gold_pale = (255, 252, 205, 255)
    gold_hi   = (255, 228, 86, 255)
    gold_mid  = (244, 174, 26, 255)
    gold_lo   = (156, 88, 12, 255)

    # Double-beveled main horizontal golden bar [30 .. w-31] shifted high (cy = 6)
    d.line([(30, 5), (w - 31, 5)], fill=gold_hi, width=2)
    d.line([(30, 7), (w - 31, 7)], fill=gold_lo, width=2)

    # Curled Baroque filigree scrollwork hooks on left & right
    for side in (0, 1):
        def sx(x_val):
            return x_val if side == 0 else (w - 1 - x_val)

        # Upper curl & lower curl
        curl_pts_top = [(sx(32), 5), (sx(27), 2), (sx(23), 1), (sx(20), 3), (sx(22), 6)]
        curl_pts_bot = [(sx(34), 7), (sx(28), 10), (sx(24), 11), (sx(21), 9)]
        d.line(curl_pts_top, fill=gold_hi, width=2)
        d.line(curl_pts_bot, fill=gold_mid, width=2)

        # Ornate 4-point diamond star finial at x=11
        cx, cy = sx(11), 6
        d.polygon([(cx, cy - 5), (cx + 6, cy), (cx, cy + 5), (cx - 6, cy)], fill=gold_mid, outline=gold_lo)
        d.polygon([(cx, cy - 3), (cx + 4, cy), (cx, cy + 3), (cx - 4, cy)], fill=gold_hi)
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=gold_pale)

    # Center royal crest jewel
    mc = w // 2
    d.polygon([(mc, 0), (mc + 7, 6), (mc, 12), (mc - 7, 6)], fill=gold_lo)
    d.polygon([(mc, 2), (mc + 5, 6), (mc, 10), (mc - 5, 6)], fill=gold_hi)
    d.rectangle([mc - 1, 5, mc + 1, 7], fill=gold_pale)
    return pin_width(img)


def make_sidebar_section_pill(label: str, fill_rgb) -> Image.Image:
    """2x HiDPI crisp beveled colored pill matching image-8.png (PLAYER, RUBIS, KRISTALLE, TEMPS DE JEU, AMIS)."""
    tw = measure_text(label, scale=2)
    w = tw + 16
    if w % 2 == 1:
        w += 1
    h = 18
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    r, g, b = fill_rgb
    hi = (min(255, r + 38), min(255, g + 38), min(255, b + 38), 255)
    lo = (max(0, r - 42), max(0, g - 42), max(0, b - 42), 255)
    bdr = (max(0, r // 3), max(0, g // 3), max(0, b // 3), 255)
    for y in range(2, h):
        for x in range(w):
            dx, dy = min(x, w - 1 - x), min(y - 2, h - 1 - y)
            if dx == 0 and dy == 0:
                continue
            if dx == 0 or dy == 0:
                px[x, y] = bdr
            elif y in (3, 4):
                px[x, y] = hi
            elif y in (h - 2, h - 3):
                px[x, y] = lo
            else:
                px[x, y] = (r, g, b, 255)
    draw_text(img, label, 8, 5, color=(255, 255, 255, 255), shadow=(max(0, r // 3), max(0, g // 3), max(0, b // 3), 225), scale=2)
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
    # 256x22 PNG rendered at height=11 -> exact 128 GUI px wide!
    w, h = 256, 22
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

    # Custom 2x HiDPI Center-Screen Tab Esports Header Plate (\uE140: 384x24 -> 192x12 GUI px)
    th = Image.new("RGBA", (384, 24), (0, 0, 0, 0))
    tp = th.load()
    for y in range(24):
        for x in range(384):
            dx, dy = min(x, 383 - x), min(y, 23 - y)
            if dx <= 1 and dy <= 1:
                continue
            if dy <= 1 or dx <= 1:
                tp[x, y] = (238, 184, 56, 252) if 110 <= x <= 274 else (58, 70, 96, 248)
            elif x < 110:
                tp[x, y] = (118, 26, 32, 236) if y > 3 else (178, 42, 48, 242)
            elif x > 274:
                tp[x, y] = (18, 68, 124, 236) if y > 3 else (32, 118, 198, 242)
            else:
                tp[x, y] = (22, 26, 38, 244)
    draw_text(th, "IGNIS", 24, 7, color=(255, 216, 208, 255), shadow=(48, 12, 16, 220), scale=2)
    tw_mid = measure_text("BOSKETBALL TAB", scale=2)
    draw_text(th, "BOSKETBALL TAB", (384 - tw_mid) // 2, 7, color=(255, 222, 76, 255), shadow=(36, 24, 8, 235), scale=2)
    tw_fr = measure_text("FROST", scale=2)
    draw_text(th, "FROST", 384 - tw_fr - 24, 7, color=(204, 242, 255, 255), shadow=(10, 28, 52, 220), scale=2)
    pin_width(th).save(font_tex_dir / "tab_header_card.png")

    # Custom 2x HiDPI Center-Screen Tab Graphical Player Row Bars (\uE141..\uE143: 384x20 -> 192x10 GUI px)
    row_specs = [
        ("tab_row_ignis.png", "\uE141", (255, 68, 58),  (64, 20, 26)),
        ("tab_row_frost.png", "\uE142", (46, 184, 255), (16, 42, 72)),
        ("tab_row_gold.png",  "\uE143", (255, 196, 48), (48, 38, 20)),
    ]
    row_providers = []
    for r_fname, r_ch, accent, tint in row_specs:
        rw, rh = 384, 20
        r_im = Image.new("RGBA", (rw, rh), (0, 0, 0, 0))
        rp = r_im.load()
        for y in range(rh):
            for x in range(rw):
                dx, dy = min(x, rw - 1 - x), min(y, rh - 1 - y)
                if dx <= 1 and dy <= 1:
                    continue
                if x < 8:
                    rp[x, y] = (*accent, 252)
                elif dy == 0 or dx == 0:
                    rp[x, y] = (54, 64, 86, 235)
                else:
                    mix = max(0.0, 1.0 - (x / 180.0))
                    rr = int(16 * (1.0 - mix) + tint[0] * mix)
                    rg = int(20 * (1.0 - mix) + tint[1] * mix)
                    rb = int(30 * (1.0 - mix) + tint[2] * mix)
                    rp[x, y] = (rr, rg, rb, 226)
        pin_width(r_im).save(font_tex_dir / r_fname)
        row_providers.append({"type": "bitmap", "file": f"bosketball:font/{r_fname}", "height": 10, "ascent": 8, "chars": [r_ch]})

    return [
        {"type": "bitmap", "file": "bosketball:font/menu3d_select_bar.png", "height": 11, "ascent": 9, "chars": ["\uE130"]},
        {"type": "bitmap", "file": "bosketball:font/menu3d_patch_pill.png", "height": 11, "ascent": 9, "chars": ["\uE131"]},
        {"type": "bitmap", "file": "bosketball:font/tab_header_card.png",   "height": 12, "ascent": 10, "chars": ["\uE140"]},
        *row_providers,
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
    for strip_i in range(5):
        strip = canvas.crop((0, strip_i * 18, W, (strip_i + 1) * 18)).copy()
        pin_width(strip)
        fname = f"chat_logo_strip_{strip_i}.png"
        strip.save(font_tex_dir / fname)
        providers.append({
            "type": "bitmap",
            "file": f"bosketball:font/{fname}",
            "height": 9,
            "ascent": 8,
            "chars": [chr(0xE120 + strip_i)],
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
        "chat_discord.png": (
            "\uE272",
            [
                "..KK..KK..",
                ".KBBBBBBK.",
                "KBBBBBBBBK",
                "KBBWBBWBBK",
                "KBBBBBBBBK",
                "KBBBBBBBBK",
                "KBKBBBBKBK",
                ".KK.KK.KK.",
                "..........",
                "..........",
            ],
            {"K": (22, 26, 58, 255), "B": (88, 101, 242, 255), "W": (255, 255, 255, 255)},
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
    ("air",       "\uE28E", "AIR",       (232, 48, 48),   "wings"),
    ("chef",      "\uE28F", "CHEF",      (36, 146, 246),  "target"),
    ("immortal",  "\uE290", "IMMORTAL",  (246, 188, 32),  "trophy"),
]


def generate_all_overhaul_font_providers(font_tex_dir: Path) -> list:
    providers = []
    providers.extend(generate_chat_divider_sprites(font_tex_dir))
    providers.extend(generate_menu3d_highlight_sprites(font_tex_dir))
    providers.extend(generate_illustrated_chat_logo_and_icons(font_tex_dir))

    # 18 Glossy 3D-Beveled Top-#1/#2/#3 Leaderboard Badges (exact image-5.png!)
    top_badge_groups = [
        ("top_wins",    0xE220, "trophy", (206, 42, 48)),   # Crimson Trophy #1..#3
        ("top_elo",     0xE223, "crown",  (214, 152, 28)),  # Gold Crown #1..#3
        ("top_mvp",     0xE226, "star",   (142, 54, 224)),  # Royal Purple Star #1..#3
        ("top_assists", 0xE229, "wings",  (38, 148, 232)),  # Sky-Blue Halo Wings #1..#3 (exact image-5!)
        ("top_points",  0xE22C, "ball",   (228, 106, 28)),  # Burnt Orange Ball #1..#3
        ("top_dunks",   0xE22F, "fire",   (218, 54, 36)),   # Magma Fire #1..#3
    ]
    for prefix, base_cp, icon_kind, rgb in top_badge_groups:
        for place in (1, 2, 3):
            ch = chr(base_cp + place - 1)
            fname = f"{prefix}_{place}.png"
            make_glossy_beveled_pill(f"#{place}", rgb, icon_kind=icon_kind).save(font_tex_dir / fname)
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
        ("sb_player.png",     "\uE261", "JOUEUR",       (138, 150, 164)),  # Cool Slate-Silver (PLAYER in image-8)
        ("sb_elo.png",        "\uE262", "ELO & RANG",   (242, 42, 82)),    # Vivid Ruby-Pink (RUBIS in image-8)
        ("sb_points.png",     "\uE263", "POINTS",       (0, 168, 248)),    # Electric Sky-Cyan (KRISTALLE in image-8)
        ("sb_playtime.png",   "\uE264", "TEMPS DE JEU", (20, 202, 130)),   # Mint-Emerald (TEMPS DE JEU in image-8)
        ("sb_friends.png",    "\uE265", "AMIS",         (122, 90, 248)),   # Lavender-Violet (AMIS in image-8)
        ("sb_match.png",      "\uE266", "SCORE",        (242, 74, 52)),
        ("sb_clock.png",      "\uE267", "CHRONO",       (236, 168, 28)),
        ("sb_stats.png",      "\uE268", "TES STATS",    (20, 202, 130)),
        ("sb_possession.png", "\uE269", "BALLON",       (0, 168, 248)),
        ("sb_streak.png",     "\uE26A", "SERIE",        (138, 86, 248)),
    ]
    for fname, ch, label, rgb in sidebar_pills:
        make_sidebar_section_pill(label, rgb).save(font_tex_dir / fname)
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
    # Subtle warm gold-ivory badge behind vanilla 'Inventory' label at (8, 128..136) so dark label text is 100% readable!
    d.rounded_rectangle([6, 128, 72, 137], radius=2, fill=(224, 184, 88, 245), outline=(128, 92, 24, 255))
    for r in range(3):
        for c in range(9):
            paint_slot_bezel(7 + c * 18, 139 + r * 18)
    for c in range(9):
        paint_slot_bezel(7 + c * 18, 197, accent_slot=True)

    tex.save(out_dir / "generic_54.png")


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
        "K": (20, 14, 28, 255),      # Deep warm outline
        "D": (44, 24, 76, 255),      # Dark violet frame
        "P": (96, 48, 168, 255),     # Mid royal purple
        "V": (154, 88, 236, 255),    # Bright violet
        "L": (212, 162, 255, 255),   # Lavender vortex glow
        "W": (250, 252, 255, 255),   # Specular white
        "O": (54, 32, 14, 255),      # Dark oak outline
        "B": (124, 76, 32, 255),     # Rich brown leather/oak shadow
        "C": (182, 118, 52, 255),    # Honey oak midtone
        "H": (224, 166, 88, 255),    # Warm oak highlight
        "G": (218, 148, 24, 255),    # Deep 24K gold
        "Y": (255, 214, 62, 255),    # Bright gold
        "Z": (255, 246, 158, 255),   # Pale gold shine
        "S": (58, 66, 82, 255),      # Dark slate steel
        "M": (132, 144, 166, 255),   # Brushed silver shadow
        "I": (196, 206, 224, 255),   # Bright silver
        "Q": (22, 92, 168, 255),     # Cobalt blue
        "A": (56, 188, 255, 255),    # Sky cyan
        "E": (138, 238, 255, 255),   # Ice cyan highlight
        "N": (22, 148, 86, 255),     # Deep emerald
        "T": (62, 232, 138, 255),    # Neon mint
        "R": (168, 28, 38, 255),     # Deep crimson
        "F": (242, 68, 58, 255),     # Bright scarlet/flame
        "U": (255, 138, 38, 255),    # Blaze orange
    }

    ICONS_16 = {
        "icon_portal": [
            "..KKKKKKKKKKKK..",
            "..KDDDDDDDDDDK..",
            "..KDPPPPPPPPDK..",
            "..KDPVVVVVVPDK..",
            "..KDPVLLLLVPDK..",
            "..KDPVLWWLVPDK..",
            "..KDPVLWPLVPDK..",
            "..KDPVLPVLVPDK..",
            "..KDPVLLLLVPDK..",
            "..KDPVVPPVVPDK..",
            "..KDPVLLLLVPDK..",
            "..KDPVVVVVVPDK..",
            "..KDPPPPPPPPDK..",
            "..KDDDDDDDDDDK..",
            "..KKKKKKKKKKKK..",
            "................",
        ],
        "icon_star_shield": [
            ".....OOOOOO.....",
            "...OOZZYYZZOO...",
            "..OZYGGGGGGYZO..",
            ".OZYGPPPPPPGYZO.",
            ".OYGPPVYYVPPGYO.",
            "OZYGPVYYYYVPGYZO",
            "OYGGPYYYYYYPGGYO",
            "OYGPPVYYYYVPGGYO",
            "OZYGPPVYYVPPGYZO",
            ".OYGPPPYYPPPGYO.",
            ".OZYGPPPPPPGYZO.",
            "..OZYGGGGGGYZO..",
            "...OOYYYYYYOO...",
            ".....OOOOOO.....",
            "................",
            "................",
        ],
        "icon_chest": [
            "..OOOOOOOOOOOO..",
            ".OHHHHHHHHHHHHO.",
            "OHCCCCCCCCCCCCBO",
            "OHCCCCCCCCCCCCBO",
            "OOOOOOYYYYOOOOOO",
            "OHCCCCYZZYCCCCBO",
            "OCBBBBYGGYBBBBCO",
            "OCBBBBOOOObbbbCO".upper().replace("B", "B"),
            "OHCCCCCCCCCCCCBO",
            "OHCCCCCCCCCCCCBO",
            "OCBBBBBBBBBBBBCO",
            ".OOOOOOOOOOOOOO.",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_book": [
            "...KKKKKKKKKK...",
            "..KFFFFFFFFFRK..",
            ".KFFZZZZZZZZFRK.",
            ".KFRFFFFFFFFFRK.",
            ".KFRFFYYYYFFFRK.",
            ".KFRFFYZZYFFFRK.",
            ".KFRFFYYYYFFFRK.",
            ".KFRFFFFFFFFFRK.",
            ".KFRRRRRRRRRRRK.",
            ".KFFWWWWWWWWWKK.",
            "..KKWWWWWWWWWK..",
            "...KKKKKKKKKKK..",
            "................",
            "................",
            "................",
            "................",
        ],
        "icon_podium": [
            "......KKKK......",
            ".....KZZZZK.....",
            "....KYGGGGYK....",
            ".....KYYYYK.....",
            "....KKKKKKKK....",
            "....KYYYYYYK....",
            ".KKKKYZZZZYKKKK.",
            "KIIIIYGGGGYMMMMK",
            "KIMMMYYYYYYMSMMK",
            "KIMMMGGGGGGSSSSK",
            "KKKKKKKKKKKKKKKK",
            "................",
            "................",
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
            "................",
            "..KKKKKKKKKKKK..",
            ".KFFFFFFFFFFFFK.",
            "KFFZYYZFFFFWWFFK",
            "KFFYYYYFFFWWWFFK",
            "KFFGGGGFFFFWWFFK",
            "KFFFFFFFFFFWWFFK",
            "KFFFFFFFFFWWWWFK",
            ".KRRRRRRRRRRRRK.",
            "..KKKKKKKKKKKK..",
            "................",
            "................",
            "................",
            "................",
            "................",
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
    }

    for icon_id, rows in ICONS_16.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in PAL:
                    px[x, y] = PAL[ch]
        img.save(item_tex_dir / f"{icon_id}.png")

        model_json = {
            "parent": "minecraft:item/generated",
            "textures": {
                "layer0": f"bosketball:item/{icon_id}",
            },
            "display": {
                "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
                "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
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
