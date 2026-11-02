#!/usr/bin/env python3
"""
BosketBall 26.2 — Ultra-Polished Custom Assets Overhaul Module:
  - image-4.png: Seamless beveled horizontal chat divider & bracket caps (\uE110..\uE112)
  - image-5.png: 18 Glossy 3D-beveled #1/#2/#3 leaderboard pills (\uE220..\uE231) + 20 Aura/Title tags (\uE240..\uE253)
  - image-6.png: Slanted mint-neon 3D Locker-Room menu highlight bar (\uE130..\uE131)
  - image-7.png: Concentric glowing team floor aura rings (ring_ignis, ring_frost, ring_gold)
  - image-8.png: Golden ornate filigree flourish (\uE260) + 10 colored sidebar section pills (\uE261..\uE26A)
  - image-9.png: 5-strip comic-book illustrated BOSKETBALL chat logo (\uE120..\uE124) + inline icons (\uE270..\uE274)
  - image-10.png: 18 bespoke 16x16 shaded pixel-art hotbar & GUI item icons
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
# 1. GLOSSY 3D-BEVELED BADGES (IMAGE-5.PNG) + 20 CUSTOM AURA/TITLE TAGS
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
        {"Y": (255, 232, 82, 255)},
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
}


def make_glossy_beveled_pill(label: str, base_rgb, icon_kind: str = None) -> Image.Image:
    """
    Renders an authentic image-5.png 3D glossy beveled rounded rectangular badge (height=9px):
      - Dark outer silhouette corners, bright top bevel specular shine, saturated body gradient,
        deep bottom shadow lip, optional 7x7 icon + crisp shadowed white pixel text!
    """
    tw = measure_text(label, scale=1)
    icon_w = 8 if icon_kind else 0
    w = icon_w + tw + 7
    h = 9
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    r0, g0, b0 = base_rgb
    border_dark = (max(0, int(r0 * 0.30)), max(0, int(g0 * 0.30)), max(0, int(b0 * 0.34)), 255)
    top_shine = (min(255, int(r0 * 1.32 + 48)), min(255, int(g0 * 1.32 + 48)), min(255, int(b0 * 1.32 + 48)), 255)
    bot_lip = (max(0, int(r0 * 0.52)), max(0, int(g0 * 0.52)), max(0, int(b0 * 0.56)), 255)

    for y in range(h):
        t = y / (h - 1)
        cr = int(min(255, r0 * (1.12 - 0.34 * t)))
        cg = int(min(255, g0 * (1.12 - 0.34 * t)))
        cb = int(min(255, b0 * (1.12 - 0.34 * t)))
        for x in range(w):
            dx, dy = min(x, w - 1 - x), min(y, h - 1 - y)
            if dx == 0 and dy == 0:
                continue  # Rounded 1px corner cutout
            if dx == 0 or dy == 0:
                px[x, y] = border_dark
            elif y == 1 and 2 <= x <= w - 3:
                px[x, y] = top_shine
            elif y == h - 2 and 2 <= x <= w - 3:
                px[x, y] = bot_lip
            else:
                px[x, y] = (cr, cg, cb, 255)

    tx = 3
    if icon_kind and icon_kind in MINI_ICONS_7X7:
        rows, cmap = MINI_ICONS_7X7[icon_kind]
        for iy, row in enumerate(rows):
            for ix, ch in enumerate(row):
                if ch in cmap and 1 + iy < h - 1:
                    px[tx + ix, 1 + iy] = cmap[ch]
        tx += 8

    draw_text(img, label, tx, 2, color=(255, 255, 255, 255), shadow=(max(0, int(r0 * 0.22)), max(0, int(g0 * 0.22)), max(0, int(b0 * 0.26)), 220), scale=1)
    return pin_width(img)


# ============================================================================
# 2. LUXURY SIDEBAR FLOURISH & COLORED SECTION PILLS (IMAGE-8.PNG)
# ============================================================================
def make_sidebar_flourish() -> Image.Image:
    """118x9 ornate golden diamond & curled filigree rule matching image-8.png under CYTOOXIEN.NET!"""
    w, h = 118, 9
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    gold_hi = (255, 232, 92, 255)
    gold_mid = (250, 184, 28, 255)
    gold_lo = (168, 98, 12, 255)

    # Center horizontal double-shaded golden bar [16 .. w-17]
    for x in range(15, w - 15):
        dist_c = abs(x - w // 2) / (w / 2.0)
        c = gold_hi if dist_c < 0.35 else gold_mid
        px[x, 4] = c
        px[x, 5] = gold_lo

    # Curled scrollwork hooks at x=10..15 and x=w-16..w-11
    scroll_pts = [
        (11, 3), (12, 2), (13, 2), (14, 3), (14, 4), (12, 5), (13, 6), (14, 6),
    ]
    for sx, sy in scroll_pts:
        px[sx, sy] = gold_hi
        px[w - 1 - sx, sy] = gold_hi

    # Ornate 5x5 golden diamond stars at both ends (x=5 and x=w-6, y=4)
    for cx in (5, w - 6):
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                m = abs(dx) + abs(dy)
                if m == 0:
                    px[cx + dx, 4 + dy] = (255, 255, 210, 255)
                elif m <= 2:
                    px[cx + dx, 4 + dy] = gold_hi if dy <= 0 else gold_mid
                elif m == 3 and (dx == 0 or dy == 0):
                    px[cx + dx, 4 + dy] = gold_lo

    # Subtle center jewel accent
    mc = w // 2
    for dx in (-1, 0, 1):
        px[mc + dx, 3] = gold_hi
        px[mc + dx, 6] = gold_lo
    px[mc, 4] = (255, 255, 224, 255)
    return pin_width(img)


def make_sidebar_section_pill(label: str, fill_rgb) -> Image.Image:
    """Crisp flat-modern colored rectangular pill matching image-8.png (PLAYER, RUBIS, KRISTALLE, TEMPS DE JEU, AMIS)."""
    tw = measure_text(label, scale=1)
    w = tw + 8
    h = 9
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    r, g, b = fill_rgb
    hi = (min(255, r + 28), min(255, g + 28), min(255, b + 28), 255)
    lo = (max(0, r - 34), max(0, g - 34), max(0, b - 34), 255)
    for y in range(1, h):
        for x in range(w):
            if y == 1:
                px[x, y] = hi
            elif y == h - 1:
                px[x, y] = lo
            else:
                px[x, y] = (r, g, b, 255)
    draw_text(img, label, 4, 2, color=(255, 255, 255, 255), shadow=(max(0, r // 3), max(0, g // 3), max(0, b // 3), 210), scale=1)
    return pin_width(img)


# ============================================================================
# 3. IMAGE-4.PNG SEAMLESS CHAT DIVIDERS & IMAGE-6.PNG SLANTED NEON SELECT BAR
# ============================================================================
def generate_chat_divider_sprites(font_tex_dir: Path) -> list:
    # 16x8 seamless metallic rule segment (advance 16; repeat with -1px shift for zero-gap line!)
    seg = Image.new("RGBA", (16, 8), (0, 0, 0, 0))
    sp = seg.load()
    for x in range(16):
        sp[x, 3] = (92, 102, 118, 255)   # Crisp steel top highlight
        sp[x, 4] = (46, 52, 62, 255)     # Dark iron core
        sp[x, 5] = (18, 20, 26, 235)     # Drop shadow
    seg.save(font_tex_dir / "divider_seg.png")

    # Left bracket cap `[` matching image-4.png
    cap_l = Image.new("RGBA", (7, 9), (0, 0, 0, 0))
    lp = cap_l.load()
    lp[0, 4] = (92, 102, 118, 255)
    lp[1, 4] = (92, 102, 118, 255)
    for y in range(1, 8):
        lp[2, y] = (108, 118, 136, 255)
        lp[3, y] = (34, 38, 48, 255)
    for x in (3, 4, 5):
        lp[x, 1] = (108, 118, 136, 255)
        lp[x, 7] = (64, 72, 86, 255)
    pin_width(cap_l).save(font_tex_dir / "divider_cap_l.png")

    # Right bracket cap `]` matching image-4.png
    cap_r = Image.new("RGBA", (7, 9), (0, 0, 0, 0))
    rp = cap_r.load()
    for x in (1, 2, 3):
        rp[x, 1] = (108, 118, 136, 255)
        rp[x, 7] = (64, 72, 86, 255)
    for y in range(1, 8):
        rp[3, y] = (108, 118, 136, 255)
        rp[4, y] = (34, 38, 48, 255)
    rp[5, 4] = (92, 102, 118, 255)
    rp[6, 4] = (92, 102, 118, 255)
    pin_width(cap_r).save(font_tex_dir / "divider_cap_r.png")

    return [
        {"type": "bitmap", "file": "bosketball:font/divider_seg.png", "height": 8, "ascent": 6, "chars": ["\uE110"]},
        {"type": "bitmap", "file": "bosketball:font/divider_cap_l.png", "height": 9, "ascent": 7, "chars": ["\uE111"]},
        {"type": "bitmap", "file": "bosketball:font/divider_cap_r.png", "height": 9, "ascent": 7, "chars": ["\uE112"]},
    ]


def generate_menu3d_highlight_sprites(font_tex_dir: Path) -> list:
    """Generates the slanted mint-emerald selection bar from image-6.png (Ranked ///////)!"""
    w, h = 128, 11
    bar = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bp = bar.load()
    for y in range(h):
        slant_right = w - 1 - (y // 2)  # Crisp angled right edge like image-6.png!
        for x in range(slant_right):
            # Subtle diagonal speed-stripes on the right half just like image-6.png!
            stripe = ((x + y * 2) % 10) < 5 and x > 58
            if y == 0:
                bp[x, y] = (162, 255, 212, 255)
            elif stripe:
                bp[x, y] = (126, 252, 192, 246)
            else:
                bp[x, y] = (78, 236, 158, 244)
    pin_width(bar).save(font_tex_dir / "menu3d_select_bar.png")

    # Dark indigo Discord/Patch button pill (116x11)
    card = Image.new("RGBA", (116, 11), (0, 0, 0, 0))
    cp = card.load()
    for y in range(11):
        for x in range(116):
            if y in (0, 10) or x in (0, 115):
                cp[x, y] = (118, 134, 248, 245)
            else:
                cp[x, y] = (78, 92, 214, 238)
    pin_width(card).save(font_tex_dir / "menu3d_patch_pill.png")

    return [
        {"type": "bitmap", "file": "bosketball:font/menu3d_select_bar.png", "height": 11, "ascent": 9, "chars": ["\uE130"]},
        {"type": "bitmap", "file": "bosketball:font/menu3d_patch_pill.png", "height": 11, "ascent": 9, "chars": ["\uE131"]},
    ]


# ============================================================================
# 4. IMAGE-9.PNG GIANT 5-STRIP ILLUSTRATED COMIC LOGO + INLINE PIXEL ICONS
# ============================================================================
def generate_illustrated_chat_logo_and_icons(font_tex_dir: Path) -> list:
    """
    Renders a full-color 184x45 comic-book sticker logo 'BOSKETBALL' (inspired by CYTOOXIEN in image-9.png):
      - White outer sticker outline -> Deep maroon-black outline -> Crimson 3D drop extrusion
      - Vibrant golden-yellow -> fiery-orange vertical gradient letters with dynamic wave baseline!
      - Sliced cleanly into 5 x (184x9) strips (\uE120..\uE124, height=9, ascent=8) for seamless multi-line chat!
    """
    W, H = 184, 45
    # Build crisp letter mask at scale=3, with dynamic wave & oversized center 'K' like CYTOOXIEN's 'X'!
    word = "BOSKETBALL"
    letter_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    extrude_mask = Image.new("L", (W, H), 0)
    face_mask = Image.new("L", (W, H), 0)

    # Compute x positions centered across 184px
    scales = [3, 3, 3, 4, 3, 3, 3, 3, 3, 3]
    widths = []
    for ch, sc in zip(word, scales):
        widths.append(len(EXT_GLYPHS_3X5[ch][0]) * sc)
    total_w = sum(widths) + (len(word) - 1) * 3
    cx = (W - total_w) // 2

    fp = face_mask.load()
    ep = extrude_mask.load()

    for idx, (ch, sc, lw) in enumerate(zip(word, scales, widths)):
        pat = EXT_GLYPHS_3X5[ch]
        # Subtle comic arc wave + tall center K!
        arc_y = int(round(2.5 * math.sin((idx / (len(word) - 1)) * math.pi)))
        base_y = (10 if sc == 4 else 14) - arc_y
        for ry, row in enumerate(pat):
            for rx, bit in enumerate(row):
                if bit == "1":
                    for sy in range(sc + (1 if sc == 3 and ry in (0, 4) else 0)):
                        for sx in range(sc):
                            px_x = cx + rx * sc + sx
                            px_y = base_y + ry * sc + sy
                            if 0 <= px_x < W and 0 <= px_y < H:
                                fp[px_x, px_y] = 255
                                # 3D Crimson Extrusion down-right (4px deep!)
                                for ex_d in range(1, 5):
                                    ex_x, ex_y = px_x + ex_d // 2, px_y + ex_d
                                    if 0 <= ex_x < W and 0 <= ex_y < H:
                                        ep[ex_x, ex_y] = 255
        cx += lw + 3

    # Dilate helper for crisp comic sticker borders
    def dilate(mask_img: Image.Image, radius: int) -> Image.Image:
        out = mask_img.copy()
        for _ in range(radius):
            out = out.filter(ImageFilter.MaxFilter(3))
        return out

    combined = Image.fromarray(
        [[max(fp[x, y], ep[x, y]) for x in range(W)] for y in range(H)] if False else None
    ) if False else Image.composite(Image.new("L", (W, H), 255), extrude_mask, face_mask)
    dark_outline = dilate(combined, 2)
    white_sticker = dilate(dark_outline, 2)

    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cp = canvas.load()
    wp = white_sticker.load()
    dp = dark_outline.load()

    for y in range(H):
        t = max(0.0, min(1.0, (y - 9) / 24.0))
        # Sunny lemon-gold at top -> rich tangerine-orange at bottom (exact image-9.png!)
        fr = 255
        fg = int(238 * (1 - t) + 148 * t)
        fb = int(52 * (1 - t) + 14 * t)
        for x in range(W):
            if fp[x, y] > 0:
                # Top inner gloss highlight
                if y > 0 and fp[x, y - 1] == 0:
                    cp[x, y] = (255, 252, 168, 255)
                else:
                    cp[x, y] = (fr, fg, fb, 255)
            elif ep[x, y] > 0:
                cp[x, y] = (196, 32, 38, 255)
            elif dp[x, y] > 0:
                cp[x, y] = (46, 12, 24, 255)
            elif wp[x, y] > 0:
                cp[x, y] = (252, 252, 255, 255)

    providers = []
    for strip_i in range(5):
        strip = canvas.crop((0, strip_i * 9, W, (strip_i + 1) * 9)).copy()
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
# 5. MASTER FONT SPRITE GENERATOR FOR IMAGE-5 & IMAGE-8 PILLS
# ============================================================================
AURA_TAGS_CATALOG = [
    ("aura",     "\uE240", "AURA",     (42, 168, 248)),
    ("goat",     "\uE241", "GOAT",     (238, 178, 28)),
    ("clutch",   "\uE242", "CLUTCH",   (224, 48, 62)),
    ("sniper",   "\uE243", "SNIPER",   (34, 196, 112)),
    ("ankle",    "\uE244", "ANKLE",    (156, 68, 242)),
    ("poster",   "\uE245", "POSTER",   (238, 84, 34)),
    ("splash",   "\uE246", "SPLASH",   (24, 198, 232)),
    ("lockdown", "\uE247", "LOCKDOWN", (76, 96, 132)),
    ("baller",   "\uE248", "BALLER",   (242, 132, 32)),
    ("phantom",  "\uE249", "PHANTOM",  (112, 52, 198)),
    ("king",     "\uE24A", "KING",     (228, 162, 24)),
    ("ace",      "\uE24B", "ACE",      (236, 48, 108)),
    ("flash",    "\uE24C", "FLASH",    (232, 196, 28)),
    ("glacier",  "\uE24D", "GLACIER",  (58, 184, 255)),
    ("inferno",  "\uE24E", "INFERNO",  (228, 52, 32)),
    ("sakura",   "\uE24F", "SAKURA",   (242, 108, 172)),
    ("cyber",    "\uE250", "CYBER",    (26, 212, 158)),
    ("titan",    "\uE251", "TITAN",    (184, 118, 54)),
    ("street",   "\uE252", "STREET",   (118, 212, 42)),
    ("champion", "\uE253", "CHAMPION", (244, 186, 42)),
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

    # 20 Unlockable & Selectable Glossy Beveled Aura / Title Tags (\uE240..\uE253)
    for tag_id, ch, label, rgb in AURA_TAGS_CATALOG:
        fname = f"tag_{tag_id}.png"
        make_glossy_beveled_pill(label, rgb, icon_kind=None).save(font_tex_dir / fname)
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
    half = (size - 1) / 2.0
    for ring_id, (outer_rgb, core_rgb) in ring_palettes.items():
        # Pixelate at 64x64 then nearest-upscale 2x so the ring has that crisp authentic Minecraft pixel-ring look in image-7.png!
        grid = 64
        gh = (grid - 1) / 2.0
        small = Image.new("RGBA", (grid, grid), (0, 0, 0, 0))
        sp = small.load()
        for y in range(grid):
            for x in range(grid):
                r = math.hypot(x - gh, y - gh) / gh  # 0..1.41
                # Outer concentric ring at r in [0.84..0.95], inner concentric ring at r in [0.66..0.74]
                if 0.85 <= r <= 0.94:
                    core = 0.88 <= r <= 0.91
                    c = core_rgb if core else outer_rgb
                    sp[x, y] = (c[0], c[1], c[2], 250 if core else 228)
                elif 0.67 <= r <= 0.74:
                    core = 0.695 <= r <= 0.715
                    c = core_rgb if core else outer_rgb
                    sp[x, y] = (c[0], c[1], c[2], 236 if core else 205)
                elif 0.56 <= r <= 0.99:
                    # Soft luminous floor aura between/around rings
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
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                "gui":   {"rotation": [55, 45, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
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
        # 64x64 UV sheet:
        # [0..15, 0..15] Main Jersey Fabric
        # [16..31, 0..15] Dark Side Panel / Shorts Shadow
        # [32..47, 0..15] Gold/Cyan Accent Trim & Collar
        # [48..63, 0..15] Warm Athletic Skin Tone
        # [0..15, 16..31] Crisp White Sneaker / Sock / Number Trim
        # [16..31, 16..31] Dark Rubber Outsole
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
        # Jersey Front/Back Number '#26' in [32..63, 16..47]
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

        # 1. TORSO (centered at [8, 8, 8], 8w x 12h x 4d = 0.50m x 0.75m x 0.25m at scale 1.0)
        torso_model = {
            "credit": "BosketBall 26.2 Articulated Hooper Torso",
            "textures": {"0": f"bosketball:item/hooper_kit_{kit_id}", "particle": f"bosketball:item/hooper_kit_{kit_id}"},
            "elements": [
                box("jersey_core", [4.0, 2.0, 6.0], [12.0, 14.0, 10.0], uv_main, front_uv=uv_jersey),
                box("collar_trim", [5.6, 13.4, 5.8], [10.4, 14.2, 10.2], uv_trim),
                box("side_stripe_l", [3.85, 2.0, 6.8], [4.1, 13.6, 9.2], uv_trim),
                box("side_stripe_r", [11.9, 2.0, 6.8], [12.15, 13.6, 9.2], uv_trim),
                box("shorts_waist", [3.9, 1.2, 5.9], [12.1, 2.4, 10.1], uv_white),
            ],
            "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]}},
        }

        # 2. SHOULDER-PIVOTED ARM (shoulder socket at EXACT [8, 8, 8], arm hangs down Y = -1.6 .. 9.6)
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

        # 3. HIP-PIVOTED LEG + HIGH-TOP SNEAKER (hip socket at EXACT [8, 8, 8], leg hangs down Y = -3.8 .. 8.8)
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
# 8. IMAGE-10.PNG HIGH-CRAFT 16x16 PIXEL-ART HOTBAR & MENU ITEM ICONS
# ============================================================================
def generate_pixel_art_item_icons(item_tex_dir: Path, model_dir: Path, item_def_dir: Path):
    """
    Generates 18 richly shaded 16x16 pixel-art item icons matching image-10.png's hand-crafted aesthetic!
    """
    PAL = {
        ".": (0, 0, 0, 0),
        "K": (20, 14, 28, 255),      # Deep warm outline
        "D": (44, 24, 76, 255),      # Dark violet frame
        "P": (96, 48, 168, 255),     # Mid royal purple
        "V": (154, 88, 236, 255),    # Bright violet
        "L": (212, 162, 255, 255),   # Lavender vortex glow
        "W": (250, 252, 255, 255),   # Specular white
        # Golds & Woods
        "O": (54, 32, 14, 255),      # Dark oak outline
        "B": (124, 76, 32, 255),     # Rich brown leather/oak shadow
        "C": (182, 118, 52, 255),    # Honey oak midtone
        "H": (224, 166, 88, 255),    # Warm oak highlight
        "G": (218, 148, 24, 255),    # Deep 24K gold
        "Y": (255, 214, 62, 255),    # Bright gold
        "Z": (255, 246, 158, 255),   # Pale gold shine
        # Silvers / Steels / Blues / Greens / Reds
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
        # 1. Purple Swirl Portal (exact image-10.png slot 1!)
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
        # 2. Gold-Beveled Purple Star Shield (exact image-10.png slot 2!)
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
        # 3. Shaded Honey-Oak Treasure Chest (exact image-10.png slot 5!)
        "icon_chest": [
            "................",
            "..OOOOOOOOOOOO..",
            ".OHHHHHHHHHHHHO.",
            ".OHCCCCCCCCCCBO.",
            ".OHCCCCCCCCCCBO.",
            ".OZZZZYYYYZZZZO.",
            ".OOOOOSIWOOOOOO.",
            ".OCCCCSIWCCCCBO.",
            ".OCCCCSMMCCCCBO.",
            ".OCCCCCCCCCCCBO.",
            ".OCCCCCCCCCCCBO.",
            ".OBBBBBBBBBBBBO.",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
            "................",
        ],
        # 4. Leather Scouting Tome with Bookmark & Player Silhouette (exact image-10.png slot 8!)
        "icon_book": [
            "................",
            "..OOOOOOOOOOOO..",
            ".OHHHHHHHHHWWBO.",
            ".OHCCCCCCCHWWBO.",
            ".OHCCCOOOCHWWBO.",
            ".OHCCOOOOCHWWBO.",
            ".OHCCCOOOCHWWBO.",
            ".OHCCOOOOOCHWBO.",
            ".OHCOOOOOOOCHBO.",
            ".OHCCCCCCCCWWBO.",
            ".OHCCCCCCCCWWBO.",
            ".OBBBBBBBBBBBBO.",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
            "................",
        ],
        # 5. Silver 3-Tier Championship Podium (exact image-10.png slot 9!)
        "icon_podium": [
            "................",
            "....SSSSSSSS....",
            "...SWWWWWWWMS...",
            "...SWIIIIIIIS...",
            "...SWIIIIIIIS...",
            "...SWIIIIIIIS...",
            ".SSSWIIIIIIMSSSS",
            "SWWWWWIIIIIMWWMS",
            "SWIIIIMIIIIMIIIS",
            "SWIIIIMIIIIMIIIS",
            "SWIIIIMIIIIMIIIS",
            "SMMMMMMMMMMMMMMS",
            ".SSSSSSSSSSSSSS.",
            "................",
            "................",
            "................",
        ],
        # 6. Winged Laser Pass Icon
        "icon_pass": [
            "........KKKK....",
            ".......KEEEEK...",
            "....KKKKEAAAEK..",
            "..KKEEEEEAAAAEK.",
            ".KEWWEEEEAAAAAEK",
            "KEWWWQAAAAAAAAEK",
            ".KKKKQQAAAAAAAEK",
            "...KKEEEAAAAAEK.",
            "..KEWWQQQAAAEK..",
            ".KEWWQ..KEEEK...",
            "..KKK....KKKK...",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        # 7. Electric Violet Crossover Lightning Bolt
        "icon_crossover": [
            ".......KKKKKK...",
            "......KVLLLLK...",
            ".....KVLLVVVK...",
            "....KVLLVKK.....",
            "...KVLLLVKKKK...",
            "..KVLLLLLLLLVK..",
            "..KKKKVLLLLVK...",
            ".....KVLLVKK....",
            "....KVLLVK......",
            "...KVLLVK.......",
            "..KVLVKK........",
            "..KKKK..........",
            "................",
            "................",
            "................",
            "................",
        ],
        # 8. Blazing Rim-Shatter Dunk Emblem
        "icon_dunk": [
            "......KKK.......",
            ".....KFZFK......",
            "....KFUZUFK.....",
            "...KFFUZUFFK....",
            "..KFRFUUUFRFK...",
            "..KFRUUUUUFRK...",
            ".KKKKKKKKKKKKK..",
            ".KUUUUUUUUUUUK..",
            "..KFFFFFFFFFK...",
            "..KW.WW.WW.WK...",
            "...W..W.W..W....",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        # 9. Cobalt-Cyan Lockdown Defense Shield
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
        # 10. AI Defender Visor ON (Neon Emerald)
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
        # 11. AI Defender Visor OFF (Slate Silver)
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
        # 12. Crimson-Gold Exit Door
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
        # 13. Friends & Party Twin Busts
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
        # 14. Precision Steel & Gold Settings Gear
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
        # 15. Golden Stopwatch & Match History Scroll
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
        # 16. Glossy Ruby-Gold #1 Crown Tag Icon
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
        # 17. Floodlit Stadium Bowl Icon
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
        # 18. Crisp Beveled Ruby-Gold Return Arrow
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
