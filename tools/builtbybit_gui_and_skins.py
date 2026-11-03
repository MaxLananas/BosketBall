#!/usr/bin/env python3
"""
BuiltByBit / Pixie Studios / Colourful Containers / PraserStore Visual Suite for BosketBall:
  1. Dual-layer 64x64 Lobby NPC Skins (npc_play_superstar & npc_shop_merchant)
  2. Warm Off-White / Creamy Linen / Latte-Sand Beige Container Screens & Widgets
  3. 32x32 Hand-Shaded Pixel-Art Menu & Hotbar Icons (35 bespoke sprites)
  4. 9 Warm Off-White / Cream / Pastel Custom Menu Overlays (U+E350..U+E358)
     - Main Menu (U+E355 non-OP / U+E356 OP) has ZERO "3D Locker" & ZERO "Player Hub" text!
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw


def _pin_glyph_width(im: Image.Image) -> Image.Image:
    w, h = im.size
    r, g, b, a = im.getpixel((w - 1, h - 1))
    if a == 0:
        im.putpixel((w - 1, h - 1), (255, 255, 255, 3))
    return im


def _clamp(v: float) -> int:
    return max(0, min(255, int(round(v))))


def _tint(rgb: tuple[int, int, int], delta: int) -> tuple[int, int, int, int]:
    return (_clamp(rgb[0] + delta), _clamp(rgb[1] + delta), _clamp(rgb[2] + delta), 255)


def _mix(a: tuple[int, int, int], b: tuple[int, int, int], t: float, alpha: int = 255) -> tuple[int, int, int, int]:
    return (
        _clamp(a[0] * (1.0 - t) + b[0] * t),
        _clamp(a[1] * (1.0 - t) + b[1] * t),
        _clamp(a[2] * (1.0 - t) + b[2] * t),
        alpha,
    )


# ==============================================================================
# 1. DUAL-LAYER 64x64 LOBBY NPC SKINS (PLAY SUPERSTAR #24 & BOUTIQUE MERCHANT)
# ==============================================================================

def _paint_shaded_box_face(
    im: Image.Image,
    x0: int,
    y0: int,
    w: int,
    h: int,
    base_rgb: tuple[int, int, int],
    top_boost: int = 10,
    edge_shade: int = -12,
):
    for dy in range(h):
        v_bias = top_boost * (1.0 - (dy / max(1, h - 1)) * 1.4)
        for dx in range(w):
            edge = 1.0 if (dx == 0 or dx == w - 1) else 0.0
            d = int(round(v_bias + edge * edge_shade + (((dx + dy) % 2) * 2 - 1)))
            im.putpixel((x0 + dx, y0 + dy), _tint(base_rgb, d))


def _build_play_superstar_skin() -> Image.Image:
    """
    NameMC Championship #24 Basketball All-Star (64x64 wide + full 3D outer layer):
      - Warm athletic golden-tan face, dark taper-fade curls, 3D purple & gold headband
      - Championship Gold & Royal-Purple #24 sleeveless jersey (front & back '24')
      - 3D purple shooting sleeve (right arm) + gold/purple sweatband (left wrist)
      - Championship purple/gold shorts + 3D high-top court sneakers
    """
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    skin = (198, 142, 102)
    skin_hi = (214, 158, 118)
    skin_lo = (172, 116, 80)
    hair = (32, 24, 22)
    hair_hi = (52, 40, 36)
    gold = (246, 186, 42)
    gold_lo = (212, 146, 24)
    purple = (92, 42, 164)
    purple_hi = (122, 66, 198)
    white = (248, 248, 252)

    # --- HEAD BASE (8x8x8) ---
    _paint_shaded_box_face(im, 8, 0, 8, 8, hair, 8, -8)      # Top
    _paint_shaded_box_face(im, 16, 0, 8, 8, skin_lo, 4, -6)  # Bottom chin
    _paint_shaded_box_face(im, 0, 8, 8, 8, skin, 8, -10)     # Right cheek
    _paint_shaded_box_face(im, 8, 8, 8, 8, skin, 10, -10)    # Front face
    _paint_shaded_box_face(im, 16, 8, 8, 8, skin, 8, -10)    # Left cheek
    _paint_shaded_box_face(im, 24, 8, 8, 8, hair, 6, -10)    # Back hair

    # Taper-fade hairline on sides & top of forehead
    d.rectangle([0, 8, 7, 10], fill=(*hair, 255))
    d.rectangle([16, 8, 23, 10], fill=(*hair, 255))
    d.rectangle([8, 8, 15, 9], fill=(*hair, 255))
    for hx in (9, 11, 13, 15):
        im.putpixel((hx, 8), (*hair_hi, 255))
    # Sideburns & back fade
    d.rectangle([24, 14, 31, 15], fill=(*skin_lo, 255))
    im.putpixel((6, 11), (*hair, 255))
    im.putpixel((17, 11), (*hair, 255))

    # Crisp NameMC 2x2 eyes + eyebrows + athletic nose/smile
    d.rectangle([9, 11, 10, 11], fill=(*hair, 255))
    d.rectangle([13, 11, 14, 11], fill=(*hair, 255))
    im.putpixel((9, 12), (*white, 255))
    im.putpixel((10, 12), (42, 28, 22, 255))
    im.putpixel((13, 12), (42, 28, 22, 255))
    im.putpixel((14, 12), (*white, 255))
    im.putpixel((11, 13), (*skin_lo, 255))
    im.putpixel((12, 13), (*skin_lo, 255))
    d.line([(10, 14), (13, 14)], fill=(*skin_lo, 255))
    im.putpixel((11, 14), (236, 228, 222, 255))  # Confident grin flash

    # --- HEAD 3D HAT OVERLAY: 360-DEGREE CHAMPIONSHIP HEADBAND ---
    d.rectangle([32, 9, 63, 10], fill=(*purple, 255))
    d.line([(32, 9), (63, 9)], fill=(*gold, 255))
    # Mini gold crown/logo tab on headband front center
    im.putpixel((43, 9), (*white, 255))
    im.putpixel((44, 10), (*gold, 255))

    # --- TORSO BASE: CHAMPIONSHIP GOLD #24 JERSEY [16..39, 16..31] ---
    _paint_shaded_box_face(im, 20, 16, 8, 4, gold, 8, -8)
    _paint_shaded_box_face(im, 28, 16, 8, 4, gold_lo, 2, -8)
    _paint_shaded_box_face(im, 16, 20, 4, 12, purple, 8, -8)   # Side stripe right
    _paint_shaded_box_face(im, 28, 20, 4, 12, purple, 8, -8)   # Side stripe left
    _paint_shaded_box_face(im, 20, 20, 8, 12, gold, 10, -12)   # Front chest
    _paint_shaded_box_face(im, 32, 20, 8, 12, gold, 8, -12)    # Back jersey

    # Athletic skin collarbone + purple/white V-neck collar
    d.rectangle([22, 20, 25, 20], fill=(*skin_hi, 255))
    d.rectangle([23, 21, 24, 21], fill=(*skin, 255))
    for px, py in [(21, 20), (22, 21), (23, 22), (24, 22), (25, 21), (26, 20)]:
        im.putpixel((px, py), (*purple, 255))
    for px, py in [(20, 20), (27, 20), (33, 20), (38, 20)]:
        im.putpixel((px, py), (*white, 255))
    d.rectangle([34, 20, 37, 20], fill=(*purple, 255))

    # Stitched "24" on front chest (x=21..26, y=24..28) AND back (x=33..38, y=24..28)
    digit_2 = [(0, 0), (1, 0), (2, 0), (2, 1), (0, 2), (1, 2), (2, 2), (0, 3), (0, 4), (1, 4), (2, 4)]
    digit_4 = [(0, 0), (2, 0), (0, 1), (2, 1), (0, 2), (1, 2), (2, 2), (2, 3), (2, 4)]
    for ox in (21, 33):
        for dx, dy in digit_2:
            if ox + dx < 64 and 24 + dy + 1 < 32:
                im.putpixel((ox + dx, 24 + dy + 1), (*white, 255))
            im.putpixel((ox + dx, 24 + dy), (*purple, 255))
        for dx, dy in digit_4:
            if ox + 3 + dx < 64 and 24 + dy + 1 < 32:
                im.putpixel((ox + 3 + dx, 24 + dy + 1), (*white, 255))
            im.putpixel((ox + 3 + dx, 24 + dy), (*purple, 255))

    # --- JACKET 3D OVERLAY: SIDE PIPING & HEM TAG ---
    d.line([(20, 47), (27, 47)], fill=(*purple_hi, 255))
    d.line([(32, 47), (39, 47)], fill=(*purple_hi, 255))
    d.rectangle([25, 45, 26, 46], fill=(*white, 255))  # Authentic jock-tag patch!

    # --- RIGHT ARM (40..55, 16..31) + 3D SHOOTING SLEEVE (40..55, 32..47) ---
    _paint_shaded_box_face(im, 44, 16, 4, 4, skin_hi, 6, -6)
    _paint_shaded_box_face(im, 48, 16, 4, 4, skin_lo, 2, -6)
    _paint_shaded_box_face(im, 40, 20, 16, 12, skin, 8, -10)
    # 3D Purple Compression Shooting Sleeve on right arm overlay
    _paint_shaded_box_face(im, 40, 39, 16, 6, purple, 10, -10)
    d.line([(40, 39), (55, 39)], fill=(*gold, 255))

    # --- LEFT ARM (32..47, 48..63) + 3D SWEATBAND (48..63, 48..63) ---
    _paint_shaded_box_face(im, 36, 48, 4, 4, skin_hi, 6, -6)
    _paint_shaded_box_face(im, 40, 48, 4, 4, skin_lo, 2, -6)
    _paint_shaded_box_face(im, 32, 52, 16, 12, skin, 8, -10)
    # 3D Gold & Purple Wrist Sweatband on left sleeve overlay
    d.rectangle([48, 60, 63, 61], fill=(*purple, 255))
    d.line([(48, 60), (63, 60)], fill=(*gold, 255))

    # --- LEGS & 3D SHORTS + HIGH-TOP COURT SNEAKERS ---
    for bx, by, ox, oy in [(0, 16, 0, 32), (16, 48, 0, 48)]:
        _paint_shaded_box_face(im, bx + 4, by, 4, 4, purple, 6, -6)
        _paint_shaded_box_face(im, bx + 8, by, 4, 4, white, 4, -6)
        # Upper shorts (rows +4..+8)
        _paint_shaded_box_face(im, bx, by + 4, 16, 5, gold, 8, -10)
        d.rectangle([bx, by + 4, bx + 3, by + 8], fill=(*purple, 255))
        d.rectangle([bx + 8, by + 4, bx + 11, by + 8], fill=(*purple, 255))
        # Athletic calves + white crew socks (rows +9..+12)
        _paint_shaded_box_face(im, bx, by + 9, 16, 2, skin, 4, -8)
        d.rectangle([bx, by + 11, bx + 15, by + 12], fill=(*white, 255))
        d.line([(bx, by + 11), (bx + 15, by + 11)], fill=(*purple, 255))
        # High-top purple/gold/white court sneakers (rows +13..+15)
        d.rectangle([bx, by + 13, bx + 15, by + 14], fill=(*purple, 255))
        d.rectangle([bx + 4, by + 13, bx + 7, by + 14], fill=(*white, 255))
        im.putpixel((bx + 4, by + 14), (*purple, 255))
        im.putpixel((bx + 7, by + 14), (*purple, 255))
        im.putpixel((bx + 5, by + 13), (*gold, 255))
        im.putpixel((bx + 6, by + 13), (*gold, 255))
        d.line([(bx, by + 15), (bx + 15, by + 15)], fill=(*gold, 255))
        # 3D Pants overlay: baggy championship shorts hem + 3D sneaker tongue!
        d.rectangle([ox, oy + 7, ox + 15, oy + 8], fill=(*purple_hi, 255))
        d.line([(ox, oy + 8), (ox + 15, oy + 8)], fill=(*gold, 255))
        d.rectangle([ox + 5, oy + 13, ox + 6, oy + 14], fill=(*gold, 255))

    return im


def _build_shop_merchant_skin() -> Image.Image:
    """
    SkinsMC Boutique Streetwear Shopkeeper (64x64 wide + full 3D outer layer):
      - Cheerful merchant with chestnut hair, emerald eyes, 3D striped boutique visor cap & glasses
      - Crisp cream collared dress shirt, coral-red bow tie, mahogany waistcoat & tan leather apron
      - 3D leather suspender straps, gleaming gold coin chest badge, pocket-watch chain & front pocket
      - Rolled cream shirt cuffs + gold wristwatch + tailored charcoal trousers & leather boots
    """
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    skin = (238, 194, 162)
    skin_lo = (212, 164, 132)
    hair = (92, 54, 34)
    hair_hi = (122, 76, 48)
    cream = (250, 241, 224)
    cream_lo = (224, 210, 186)
    coral = (218, 66, 62)
    vest = (96, 46, 38)
    apron = (182, 126, 74)
    apron_hi = (206, 148, 92)
    gold = (246, 194, 56)
    pants = (58, 52, 56)
    boots = (88, 52, 32)

    # --- HEAD BASE ---
    _paint_shaded_box_face(im, 8, 0, 8, 8, hair, 10, -8)
    _paint_shaded_box_face(im, 16, 0, 8, 8, skin_lo, 4, -6)
    _paint_shaded_box_face(im, 0, 8, 8, 8, skin, 8, -10)
    _paint_shaded_box_face(im, 8, 8, 8, 8, skin, 10, -10)
    _paint_shaded_box_face(im, 16, 8, 8, 8, skin, 8, -10)
    _paint_shaded_box_face(im, 24, 8, 8, 8, hair, 8, -10)

    # Textured chestnut hair & sideburns
    d.rectangle([0, 8, 7, 10], fill=(*hair, 255))
    d.rectangle([16, 8, 23, 10], fill=(*hair, 255))
    d.rectangle([8, 8, 15, 9], fill=(*hair, 255))
    im.putpixel((10, 9), (*hair_hi, 255))
    im.putpixel((13, 9), (*hair_hi, 255))
    im.putpixel((7, 11), (*hair, 255))
    im.putpixel((16, 11), (*hair, 255))

    # Cheerful emerald eyes + warm blush + welcoming smile
    d.rectangle([9, 11, 10, 11], fill=(*hair, 255))
    d.rectangle([13, 11, 14, 11], fill=(*hair, 255))
    im.putpixel((9, 12), (250, 250, 252, 255))
    im.putpixel((10, 12), (42, 138, 88, 255))
    im.putpixel((13, 12), (42, 138, 88, 255))
    im.putpixel((14, 12), (250, 250, 252, 255))
    im.putpixel((8, 13), (236, 154, 142, 255))
    im.putpixel((15, 13), (236, 154, 142, 255))
    d.line([(10, 14), (13, 14)], fill=(168, 104, 84, 255))

    # --- HEAD 3D HAT OVERLAY: STRIPED BOUTIQUE CAP + GOLD RIM SPECTACLES ---
    for cx in range(8):
        stripe_c = coral if (cx % 2 == 0) else cream
        d.line([(40 + cx, 0), (40 + cx, 7)], fill=(*stripe_c, 255))
        im.putpixel((40 + cx, 8), (*stripe_c, 255))
    for cx in range(32, 64):
        if cx < 40 or cx >= 48:
            stripe_c = coral if (cx % 2 == 0) else cream
            im.putpixel((cx, 8), (*stripe_c, 255))
            im.putpixel((cx, 9), (*vest, 255))
    d.line([(39, 9), (48, 9)], fill=(*gold, 255))  # Cap visor brim with gold trim!
    # Cute gold half-rim merchant spectacles on front hat layer
    for gx, gy in [(41, 12), (42, 12), (43, 12), (44, 12), (45, 12), (46, 12)]:
        if gx in (41, 46):
            im.putpixel((gx, gy), (*gold, 255))
    im.putpixel((43, 11), (*gold, 255))
    im.putpixel((44, 11), (*gold, 255))

    # --- TORSO BASE: CREAM SHIRT + BOW TIE + VEST + APRON ---
    _paint_shaded_box_face(im, 20, 16, 8, 4, cream, 6, -6)
    _paint_shaded_box_face(im, 28, 16, 8, 4, vest, 2, -6)
    _paint_shaded_box_face(im, 16, 20, 24, 12, vest, 8, -10)
    # Front shirt V-placket + coral bow tie
    d.rectangle([22, 20, 25, 24], fill=(*cream, 255))
    im.putpixel((22, 21), (*coral, 255))
    im.putpixel((25, 21), (*coral, 255))
    d.rectangle([23, 21, 24, 21], fill=(*gold, 255))
    # Tan leather boutique apron across lower front torso
    _paint_shaded_box_face(im, 21, 24, 6, 8, apron, 10, -10)
    # Apron waist tie wrapping around back
    d.line([(16, 26), (39, 26)], fill=(*apron_hi, 255))

    # --- JACKET 3D OVERLAY: LEATHER STRAPS + GOLD COIN PIN + POCKET WATCH ---
    d.line([(21, 36), (21, 41)], fill=(*apron_hi, 255))
    d.line([(26, 36), (26, 41)], fill=(*apron_hi, 255))
    # Gleaming gold boutique coin badge on left lapel
    d.rectangle([25, 38, 26, 39], fill=(*gold, 255))
    # 3D front apron pocket + golden pocket-watch chain
    _paint_shaded_box_face(im, 22, 43, 4, 4, apron_hi, 6, -8)
    im.putpixel((21, 43), (*gold, 255))
    im.putpixel((22, 44), (*gold, 255))

    # --- RIGHT & LEFT ARMS: ROLLED CREAM DRESS SLEEVES + HANDS ---
    for ax, ay, sx, sy, has_watch in [(40, 16, 40, 32, False), (32, 48, 48, 48, True)]:
        _paint_shaded_box_face(im, ax + 4, ay, 4, 4, cream, 6, -6)
        _paint_shaded_box_face(im, ax + 8, ay, 4, 4, skin_lo, 2, -6)
        _paint_shaded_box_face(im, ax, ay + 4, 16, 7, cream, 8, -10)
        _paint_shaded_box_face(im, ax, ay + 11, 16, 5, skin, 6, -8)
        # 3D rolled shirt cuff on sleeve overlay
        d.rectangle([sx, sy + 9, sx + 15, sy + 10], fill=(*cream_lo, 255))
        if has_watch:
            d.line([(sx, sy + 13), (sx + 15, sy + 13)], fill=(*boots, 255))
            d.rectangle([sx + 5, sy + 13, sx + 6, sy + 13], fill=(*gold, 255))

    # --- LEGS: TAILORED CHARCOAL TROUSERS + 3D LEATHER BOOTS ---
    for bx, by, ox, oy in [(0, 16, 0, 32), (16, 48, 0, 48)]:
        _paint_shaded_box_face(im, bx + 4, by, 4, 4, pants, 4, -6)
        _paint_shaded_box_face(im, bx + 8, by, 4, 4, boots, 2, -6)
        _paint_shaded_box_face(im, bx, by + 4, 16, 9, pants, 8, -10)
        _paint_shaded_box_face(im, bx, by + 13, 16, 3, boots, 8, -10)
        d.line([(ox, oy + 12), (ox + 15, oy + 12)], fill=(*apron, 255))
        im.putpixel((ox + 5, oy + 12), (*gold, 255))

    return im


def generate_lobby_npc_skins(rp_dir: Path):
    wide_dir = rp_dir / "assets" / "bosketball" / "textures" / "entity" / "player" / "wide"
    base_dir = rp_dir / "assets" / "bosketball" / "textures" / "entity" / "player"
    wide_dir.mkdir(parents=True, exist_ok=True)
    base_dir.mkdir(parents=True, exist_ok=True)

    for skin_id, builder in (
        ("npc_play_superstar", _build_play_superstar_skin),
        ("npc_shop_merchant", _build_shop_merchant_skin),
    ):
        im = builder()
        im.save(wide_dir / f"{skin_id}.png")
        im.save(base_dir / f"{skin_id}.png")


# ==============================================================================
# 2. WARM OFF-WHITE / CREAMY LINEN / LATTE-SAND BEIGE CONTAINER SCREENS
# ==============================================================================

def _draw_warm_slot_well(im: Image.Image, sx: int, sy: int, well_rgb=(226, 214, 194)):
    """Draws an 18x18 recessed warm sand-beige slot well with soft rounded inner corners."""
    d = ImageDraw.Draw(im)
    sh_dark = _tint(well_rgb, -40)
    sh_mid = _tint(well_rgb, -18)
    hi_light = (253, 250, 243, 255)
    d.rectangle([sx, sy, sx + 17, sy + 17], fill=(*well_rgb, 255))
    d.line([(sx, sy), (sx + 16, sy)], fill=sh_dark)
    d.line([(sx, sy), (sx, sy + 16)], fill=sh_dark)
    d.line([(sx + 1, sy + 17), (sx + 17, sy + 17)], fill=hi_light)
    d.line([(sx + 17, sy + 1), (sx + 17, sy + 17)], fill=hi_light)
    # Softened inner corners (Pixie Studios rounded feel)
    im.putpixel((sx + 1, sy + 1), sh_mid)
    im.putpixel((sx + 16, sy + 1), _tint(well_rgb, -8))
    im.putpixel((sx + 1, sy + 16), _tint(well_rgb, -8))


def _paint_warm_cream_panel(im: Image.Image, x0: int, y0: int, x1: int, y1: int, header_ribbon: bool = False):
    """Paints a warm off-white / creamy linen panel (#F6EFE3) with cocoa outline & cream/sand bevels."""
    d = ImageDraw.Draw(im)
    outline = (84, 64, 50, 255)
    hi_bevel = (254, 251, 245, 255)
    lo_bevel = (208, 191, 166, 255)
    body = (246, 239, 227)

    # Subtle tactile linen weave (never flat #FFFFFF!)
    for py in range(y0 + 2, y1 - 1):
        for px in range(x0 + 2, x1 - 1):
            grain = ((px * 3 + py * 5) % 5) - 2
            im.putpixel((px, py), _tint(body, grain))

    # Crisp rounded-corner pixel border
    d.rectangle([x0, y0, x1, y1], outline=outline)
    d.line([(x0 + 1, y0 + 1), (x1 - 2, y0 + 1)], fill=hi_bevel)
    d.line([(x0 + 1, y0 + 1), (x0 + 1, y1 - 2)], fill=hi_bevel)
    d.line([(x0 + 2, y1 - 1), (x1 - 1, y1 - 1)], fill=lo_bevel)
    d.line([(x1 - 1, y0 + 2), (x1 - 1, y1 - 1)], fill=lo_bevel)
    for cx, cy in [(x0, y0), (x1, y0), (x0, y1), (x1, y1)]:
        im.putpixel((cx, cy), (0, 0, 0, 0))
    for cx, cy in [(x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)]:
        im.putpixel((cx, cy), outline)

    if header_ribbon:
        # Warm toasted walnut-caramel title plaque [x0+4..x1-4, y0+3..y0+15] so pastel/yellow titles pop!
        d.rectangle([x0 + 4, y0 + 3, x1 - 4, y0 + 15], fill=(82, 58, 42, 255), outline=(218, 168, 86, 255))
        d.line([(x0 + 5, y0 + 4), (x1 - 5, y0 + 4)], fill=(104, 76, 56, 255))


def generate_warm_cream_container_gui(rp_dir: Path):
    """
    Styles vanilla containers, tooltips, and widgets in a cohesive Warm Off-White / Creamy Linen /
    Latte-Sand Beige palette (ZERO pitch-black panels, ZERO flat white containers!).
    """
    gui_dir = rp_dir / "assets" / "minecraft" / "textures" / "gui"
    cont_dir = gui_dir / "container"
    slot_dir = gui_dir / "sprites" / "container"
    tip_dir = gui_dir / "sprites" / "tooltip"
    wid_dir = gui_dir / "sprites" / "widget"
    for p in (cont_dir, slot_dir, tip_dir, wid_dir):
        p.mkdir(parents=True, exist_ok=True)

    # 1. generic_54.png (256x256, active 176x222)
    g54 = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    _paint_warm_cream_panel(g54, 0, 0, 175, 221, header_ribbon=True)
    gd = ImageDraw.Draw(g54)
    # Soft warm-sand divider above player inventory
    gd.line([(7, 126), (168, 126)], fill=(214, 198, 174, 255))
    gd.line([(7, 127), (168, 127)], fill=(252, 249, 242, 255))
    for r in range(6):
        for c in range(9):
            _draw_warm_slot_well(g54, 7 + c * 18, 17 + r * 18)
    for r in range(3):
        for c in range(9):
            _draw_warm_slot_well(g54, 7 + c * 18, 139 + r * 18)
    for c in range(9):
        _draw_warm_slot_well(g54, 7 + c * 18, 197, well_rgb=(218, 204, 180))
    g54.save(cont_dir / "generic_54.png")

    # 2. dispenser.png / hopper.png (3x3 and 5-slot warm cream containers so NO vanilla gray leaks!)
    disp = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    _paint_warm_cream_panel(disp, 0, 0, 175, 165, header_ribbon=True)
    for r in range(3):
        for c in range(3):
            _draw_warm_slot_well(disp, 61 + c * 18, 16 + r * 18)
    for r in range(3):
        for c in range(9):
            _draw_warm_slot_well(disp, 7 + c * 18, 83 + r * 18)
    for c in range(9):
        _draw_warm_slot_well(disp, 7 + c * 18, 141, well_rgb=(218, 204, 180))
    disp.save(cont_dir / "dispenser.png")

    hop = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    _paint_warm_cream_panel(hop, 0, 0, 175, 132, header_ribbon=True)
    for c in range(5):
        _draw_warm_slot_well(hop, 43 + c * 18, 19)
    for r in range(3):
        for c in range(9):
            _draw_warm_slot_well(hop, 7 + c * 18, 50 + r * 18)
    for c in range(9):
        _draw_warm_slot_well(hop, 7 + c * 18, 108, well_rgb=(218, 204, 180))
    hop.save(cont_dir / "hopper.png")

    # 3. inventory.png (256x256, active 176x166)
    inv = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    _paint_warm_cream_panel(inv, 0, 0, 175, 165, header_ribbon=False)
    idraw = ImageDraw.Draw(inv)
    # Cozy warm espresso-velvet 3D character stage [25..75, 7..77] with gold floor spotlight
    idraw.rectangle([25, 7, 75, 77], fill=(54, 41, 33, 255), outline=(198, 152, 78, 255))
    idraw.line([(29, 73), (71, 73)], fill=(238, 186, 94, 255))
    for r in range(4):
        _draw_warm_slot_well(inv, 7, 7 + r * 18)
    _draw_warm_slot_well(inv, 76, 61)
    for r in range(2):
        for c in range(2):
            _draw_warm_slot_well(inv, 97 + c * 18, 17 + r * 18)
    _draw_warm_slot_well(inv, 153, 27, well_rgb=(242, 224, 182))
    idraw.line([(137, 36), (149, 36)], fill=(184, 140, 76, 255))
    idraw.polygon([(146, 33), (150, 36), (146, 39)], fill=(184, 140, 76, 255))
    for r in range(3):
        for c in range(9):
            _draw_warm_slot_well(inv, 7 + c * 18, 83 + r * 18)
    for c in range(9):
        _draw_warm_slot_well(inv, 7 + c * 18, 141, well_rgb=(218, 204, 180))
    inv.save(cont_dir / "inventory.png")

    # 4. shulker_box.png — 3D Locker Room Transparent-Center Viewport with Warm Cream & Gold Rails!
    sb = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    # Left rail (col 0), Right rail (col 8), Bottom dock (row 2)
    _paint_warm_cream_panel(sb, 4, 14, 27, 73, header_ribbon=False)
    _paint_warm_cream_panel(sb, 148, 14, 171, 73, header_ribbon=False)
    _paint_warm_cream_panel(sb, 4, 136, 171, 161, header_ribbon=False)
    for r in range(3):
        _draw_warm_slot_well(sb, 7, 17 + r * 18)
        _draw_warm_slot_well(sb, 151, 17 + r * 18)
    for c in range(9):
        _draw_warm_slot_well(sb, 7 + c * 18, 139, well_rgb=(222, 208, 184))
    sb.save(cont_dir / "shulker_box.png")

    # 5. Warm Sunny Butter-Gold Slot Hover Highlight (24x24)
    sh_back = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    ImageDraw.Draw(sh_back).rectangle([4, 4, 19, 19], fill=(255, 214, 88, 64))
    sh_back.save(slot_dir / "slot_highlight_back.png")

    sh_front = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    sfd = ImageDraw.Draw(sh_front)
    sfd.rectangle([3, 3, 20, 20], outline=(228, 152, 34, 238))
    sfd.rectangle([4, 4, 19, 19], outline=(255, 244, 196, 175))
    sh_front.save(slot_dir / "slot_highlight_front.png")

    # 6. Warm Espresso-Parchment Tooltips with Cream & Champagne-Gold Double Border
    tb = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
    tbd = ImageDraw.Draw(tb)
    tbd.rectangle([1, 1, 98, 98], fill=(40, 30, 24, 244), outline=(74, 56, 44, 252))
    tb.save(tip_dir / "background.png")
    (tip_dir / "background.png.mcmeta").write_text(
        json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 100, "height": 100, "border": {"left": 9, "top": 9, "right": 9, "bottom": 9}}}}, indent=2)
    )

    tf = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
    tfd = ImageDraw.Draw(tf)
    tfd.rectangle([0, 0, 99, 99], outline=(228, 176, 86, 252))
    tfd.rectangle([1, 1, 98, 98], outline=(252, 242, 218, 215))
    for cx, cy in [(0, 0), (99, 0), (0, 99), (99, 99)]:
        tf.putpixel((cx, cy), (0, 0, 0, 0))
    tf.save(tip_dir / "frame.png")
    (tip_dir / "frame.png.mcmeta").write_text(
        json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 100, "height": 100, "border": 10, "stretch_inner": True}}}, indent=2)
    )

    # 7. Warm Caramel-Oak & Honey-Gold Pause/Options Buttons (white text stays 100% crisp!)
    btn_specs = [
        ("button.png",             (156, 122, 86, 250), (92, 68, 48, 255),  (204, 170, 130, 255)),
        ("button_highlighted.png", (206, 148, 62, 254), (118, 76, 28, 255), (252, 224, 152, 255)),
        ("button_disabled.png",    (188, 176, 160, 215), (132, 120, 106, 225), (212, 202, 188, 220)),
    ]
    for bname, bfill, bout, bhi in btn_specs:
        bim = Image.new("RGBA", (200, 20), (0, 0, 0, 0))
        bd = ImageDraw.Draw(bim)
        bd.rectangle([0, 0, 199, 19], fill=bfill, outline=bout)
        bd.line([(1, 1), (198, 1)], fill=bhi)
        for cx, cy in [(0, 0), (199, 0), (0, 19), (199, 19)]:
            bim.putpixel((cx, cy), (0, 0, 0, 0))
        bim.save(wid_dir / bname)
        (wid_dir / f"{bname}.mcmeta").write_text(
            json.dumps({"gui": {"scaling": {"type": "nine_slice", "width": 200, "height": 20, "border": {"left": 3, "top": 3, "right": 3, "bottom": 3}}}}, indent=2)
        )


# ==============================================================================
# 3. 32x32 HAND-SHADED BUILTBYBIT PRASERSTORE / PIXIE STUDIOS PIXEL-ART ICONS
# ==============================================================================

OUTLINE_ESPRESSO = (48, 32, 24, 255)


def _auto_pixel_outline_32(im: Image.Image, outline_rgba=OUTLINE_ESPRESSO) -> Image.Image:
    """Adds a crisp 1px hand-drawn warm espresso pixel outline + soft drop shadow to 32x32 art."""
    w, h = im.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    src = im.load()
    # Soft 1px warm shadow underneath opaque silhouette
    for y in range(h):
        for x in range(w):
            if src[x, y][3] > 180 and y + 2 < h:
                out.putpixel((x, y + 2), (32, 22, 18, 72))
    # 4-neighbor crisp pixel outline
    for y in range(h):
        for x in range(w):
            if src[x, y][3] <= 20:
                has_n = False
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 180:
                        has_n = True
                        break
                if has_n:
                    out.putpixel((x, y), outline_rgba)
    out.alpha_composite(im)
    return out


def _draw_shaded_ball_circle(im: Image.Image, cx: int, cy: int, r: int, c_hi, c_mid, c_lo, seams: bool = True):
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            if 0 <= x < im.width and 0 <= y < im.height:
                dist = math.hypot(x - cx, y - cy)
                if dist <= r + 0.25:
                    lx = (x - (cx - r * 0.35)) / max(1.0, r * 1.5)
                    ly = (y - (cy - r * 0.35)) / max(1.0, r * 1.5)
                    t = min(1.0, math.hypot(lx, ly))
                    col = _mix(c_hi, c_mid, min(1.0, t * 1.25)) if t < 0.65 else _mix(c_mid, c_lo, (t - 0.65) / 0.35)
                    if seams:
                        dx, dy = x - cx, y - cy
                        on_seam = (
                            abs(dy) <= 0.65
                            or abs(dx) <= 0.65
                            or abs(abs(dx) - (r * 0.56 - (dy * dy) / max(1.0, r * 2.1))) <= 0.68
                        )
                        if on_seam:
                            col = (56, 34, 24, 255)
                    im.putpixel((x, y), col)
    # Glossy specular highlight dot
    if 0 <= cx - r // 2 < im.width and 0 <= cy - r // 2 < im.height:
        im.putpixel((cx - r // 2, cy - r // 2), (255, 248, 226, 255))


def build_builtbybit_32x32_icons() -> dict[str, Image.Image]:
    """Builds 35 crisp 32x32 hand-shaded BuiltByBit PraserStore / Pixie Studios pixel-art icons."""
    sprites: dict[str, Image.Image] = {}

    # 1. SHOP: Boutique Market Stall with Scalloped Red/Cream Awning, Wood Counter, Ball & Coins
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Wooden side posts
    d.rectangle([5, 11, 7, 25], fill=(164, 108, 58, 255))
    d.rectangle([24, 11, 26, 25], fill=(142, 90, 46, 255))
    # Interior warm recessed shadow
    d.rectangle([8, 12, 23, 19], fill=(82, 56, 40, 255))
    # Mini golden basketball + gold coin stack on counter
    _draw_shaded_ball_circle(im, 13, 16, 3, (255, 186, 68), (236, 118, 34), (178, 72, 20), seams=False)
    d.rectangle([18, 15, 21, 18], fill=(250, 204, 58, 255))
    d.line([(18, 15), (21, 15)], fill=(255, 244, 168, 255))
    # Scalloped 6-stripe coral-red & warm-cream awning [3..28, 4..12]
    for s in range(6):
        ax0 = 4 + s * 4
        c_top = (228, 66, 58, 255) if s % 2 == 0 else (252, 244, 226, 255)
        c_bot = (188, 44, 42, 255) if s % 2 == 0 else (226, 212, 188, 255)
        d.polygon([(ax0 + 1, 4), (ax0 + 4, 4), (ax0 + 4, 10), (ax0, 10)], fill=c_top)
        d.rectangle([ax0, 10, ax0 + 3, 12], fill=c_bot)
    # Honey-oak wooden counter front with bevel planks
    d.rectangle([4, 19, 27, 21], fill=(232, 182, 118, 255))
    d.rectangle([5, 21, 26, 27], fill=(188, 128, 68, 255))
    for px in (10, 15, 21):
        d.line([(px, 21), (px, 27)], fill=(148, 96, 48, 255))
    shop_32 = _auto_pixel_outline_32(im)
    for k in ("icon_shop", "prop_shop_crate", "icon_shelves"):
        sprites[k] = shop_32.copy()

    # 2. ARENA / PLAY: Winged Championship Basketball with Golden Angel-Wings & Crown Spark
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Left & Right sculpted golden wings
    wing_pts_l = [(2, 10), (9, 12), (11, 18), (6, 20), (3, 16), (2, 13)]
    wing_pts_r = [(29, 10), (22, 12), (20, 18), (25, 20), (28, 16), (29, 13)]
    d.polygon(wing_pts_l, fill=(250, 204, 62, 255))
    d.polygon(wing_pts_r, fill=(250, 204, 62, 255))
    for wy in (12, 15, 18):
        d.line([(4, wy), (9, wy + 1)], fill=(255, 242, 164, 255))
        d.line([(27, wy), (22, wy + 1)], fill=(255, 242, 164, 255))
    # Rich shaded center basketball (r=8)
    _draw_shaded_ball_circle(im, 16, 17, 8, (255, 172, 64), (234, 108, 32), (168, 62, 18), seams=True)
    # Mini gold crown atop ball
    d.polygon([(12, 7), (13, 4), (16, 6), (19, 4), (20, 7)], fill=(255, 214, 64, 255))
    d.line([(12, 7), (20, 7)], fill=(224, 156, 32, 255))
    arena_32 = _auto_pixel_outline_32(im)
    sprites["icon_arena"] = arena_32
    sprites["prop_play_emblem"] = arena_32.copy()

    # 3. LOCKER / PORTAL: Championship #24 Jersey on Gold Hanger
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Gold hook & hanger shoulders
    d.line([(15, 3), (17, 3), (17, 5), (16, 6)], fill=(246, 198, 68, 255))
    d.line([(10, 8), (16, 6), (22, 8)], fill=(236, 182, 52, 255))
    # Sleeveless basketball jersey silhouette [7..24, 8..27]
    d.polygon([(8, 8), (12, 8), (14, 12), (18, 12), (20, 8), (24, 8), (23, 14), (24, 27), (8, 27), (9, 14)], fill=(248, 188, 42, 255))
    # Purple & cream armhole & V-neck trim + side stripes
    d.line([(12, 8), (16, 13), (20, 8)], fill=(96, 44, 172, 255), width=2)
    d.rectangle([8, 14, 9, 27], fill=(96, 44, 172, 255))
    d.rectangle([23, 14, 24, 27], fill=(96, 44, 172, 255))
    d.line([(8, 26), (24, 26)], fill=(96, 44, 172, 255))
    # Crisp stitched "24" on jersey chest!
    for dx, dy in [(0,0),(1,0),(2,0),(2,1),(2,2),(1,2),(0,2),(0,3),(0,4),(0,5),(1,5),(2,5)]:
        im.putpixel((12 + dx, 16 + dy), (255, 252, 245, 255))
        im.putpixel((12 + dx, 15 + dy), (96, 44, 172, 255))
    for dx, dy in [(0,0),(2,0),(0,1),(2,1),(0,2),(1,2),(2,2),(2,3),(2,4),(2,5)]:
        im.putpixel((17 + dx, 16 + dy), (255, 252, 245, 255))
        im.putpixel((17 + dx, 15 + dy), (96, 44, 172, 255))
    jersey_32 = _auto_pixel_outline_32(im)
    sprites["icon_portal"] = jersey_32
    sprites["prop_locker_jersey"] = jersey_32.copy()

    # 4. PODIUM / TROPHY: Gleaming Championship Gold Cup on Mahogany Pedestal
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Twin curved handles
    d.arc([4, 7, 13, 17], 90, 270, fill=(238, 178, 42, 255), width=2)
    d.arc([18, 7, 27, 17], 270, 90, fill=(214, 150, 30, 255), width=2)
    # Sculpted cup bowl [9..22, 5..18]
    for cy in range(5, 19):
        half = int(round(7.0 - max(0, cy - 11) * 0.65))
        for cx in range(16 - half, 16 + half):
            t = (cx - (16 - half)) / max(1, 2 * half - 1)
            col = _mix((255, 244, 162), (246, 188, 42), min(1.0, t * 1.4)) if t < 0.6 else _mix((246, 188, 42), (194, 126, 22), (t - 0.6) / 0.4)
            im.putpixel((cx, cy), col)
    d.line([(9, 5), (22, 5)], fill=(255, 250, 206, 255))
    # Stem + collar + mahogany base with gold plaque
    d.rectangle([14, 19, 17, 22], fill=(228, 166, 36, 255))
    d.rectangle([11, 22, 20, 23], fill=(250, 206, 68, 255))
    d.rectangle([9, 24, 22, 28], fill=(116, 64, 38, 255))
    d.rectangle([12, 25, 19, 27], fill=(250, 206, 68, 255))
    # Radiant star crest on cup
    for sx, sy in [(15, 10), (16, 9), (16, 10), (17, 10), (16, 11)]:
        im.putpixel((sx, sy), (255, 255, 245, 255))
    trophy_32 = _auto_pixel_outline_32(im)
    sprites["icon_podium"] = trophy_32
    sprites["prop_podium_cup"] = trophy_32.copy()

    # 5. FRIENDS & PARTY: Dual 32x32 Player Busts + Online Emerald Dot
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Back teammate (right, sky-blue jersey)
    d.rectangle([17, 7, 24, 14], fill=(226, 178, 144, 255))
    d.rectangle([17, 7, 24, 9], fill=(82, 52, 36, 255))
    im.putpixel((19, 11), (38, 32, 28, 255))
    im.putpixel((22, 11), (38, 32, 28, 255))
    d.polygon([(14, 24), (16, 16), (25, 16), (28, 24)], fill=(72, 168, 238, 255))
    # Front player (left-center, emerald-mint jersey)
    d.rectangle([8, 9, 16, 17], fill=(242, 198, 166, 255))
    d.rectangle([8, 9, 16, 11], fill=(48, 34, 28, 255))
    im.putpixel((10, 13), (38, 32, 28, 255))
    im.putpixel((14, 13), (38, 32, 28, 255))
    d.polygon([(4, 27), (7, 18), (17, 18), (20, 27)], fill=(76, 212, 138, 255))
    d.line([(10, 18), (12, 21), (14, 18)], fill=(252, 248, 238, 255))
    # Glowing emerald online badge (bottom-right)
    _draw_shaded_ball_circle(im, 25, 24, 3, (152, 255, 186), (58, 216, 112), (28, 148, 72), seams=False)
    friends_32 = _auto_pixel_outline_32(im)
    sprites["icon_friends"] = friends_32
    sprites["prop_social_megaphone"] = friends_32.copy()

    # 6. TAGS: Collector Ribbon Price-Tag with Brass Eyelet & Star
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.arc([4, 3, 13, 12], 140, 350, fill=(228, 198, 148, 255), width=2)
    d.polygon([(8, 8), (16, 8), (27, 19), (19, 27), (8, 16)], fill=(248, 122, 98, 255))
    d.polygon([(10, 10), (15, 10), (24, 19), (19, 24), (10, 15)], fill=(254, 238, 214, 255))
    # Brass eyelet ring
    d.ellipse([10, 10, 13, 13], fill=(92, 68, 52, 255), outline=(246, 196, 62, 255))
    # Gold star inside tag
    for sx, sy in [(18, 16), (17, 17), (18, 17), (19, 17), (18, 18), (16, 17), (20, 17), (18, 15), (18, 19)]:
        im.putpixel((sx, sy), (244, 178, 38, 255))
    tag_32 = _auto_pixel_outline_32(im)
    sprites["icon_tags"] = tag_32
    sprites["prop_tag_plaque"] = tag_32.copy()

    # 7. CHEST / WALLET: Velvet Coin Pouch Overflowing with Gold Coins
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(11, 5), (21, 5), (19, 10), (13, 10)], fill=(196, 132, 74, 255))
    # Gold coins peeking out of top!
    _draw_shaded_ball_circle(im, 14, 9, 2, (255, 244, 156), (246, 192, 46), (196, 134, 24), seams=False)
    _draw_shaded_ball_circle(im, 18, 9, 2, (255, 244, 156), (246, 192, 46), (196, 134, 24), seams=False)
    # Plump pouch body
    _draw_shaded_ball_circle(im, 16, 19, 8, (222, 156, 94), (184, 118, 62), (134, 78, 38), seams=False)
    d.rectangle([11, 10, 21, 12], fill=(248, 198, 58, 255))
    # Gold 'B' coin crest on pouch front
    _draw_shaded_ball_circle(im, 16, 19, 4, (255, 242, 158), (244, 186, 42), (194, 132, 22), seams=False)
    sprites["icon_chest"] = _auto_pixel_outline_32(im)

    # 8. COIN STACK: Gleaming Stack of Gold BosketCoins
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for idx, cy in enumerate((23, 19, 15, 11)):
        ox = 9 + (idx % 2)
        d.ellipse([ox, cy, ox + 12, cy + 5], fill=(214, 150, 28, 255))
        d.ellipse([ox, cy - 2, ox + 12, cy + 3], fill=(252, 208, 64, 255), outline=(255, 244, 168, 255))
    _draw_shaded_ball_circle(im, 21, 21, 6, (255, 246, 168), (246, 192, 44), (192, 128, 22), seams=False)
    sprites["icon_coin_stack"] = _auto_pixel_outline_32(im)

    # 9. HISTORY: Brass & Sky-Crystal Hourglass with Golden Sand
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([7, 4, 24, 6], fill=(224, 168, 56, 255))
    d.rectangle([7, 25, 24, 27], fill=(204, 148, 42, 255))
    d.line([(9, 6), (9, 25)], fill=(184, 128, 38, 255), width=2)
    d.line([(22, 6), (22, 25)], fill=(184, 128, 38, 255), width=2)
    d.polygon([(11, 7), (20, 7), (16, 15), (15, 15)], fill=(198, 238, 252, 255))
    d.polygon([(15, 16), (16, 16), (20, 24), (11, 24)], fill=(198, 238, 252, 255))
    # Flowing golden sand
    d.polygon([(13, 11), (18, 11), (16, 15), (15, 15)], fill=(246, 194, 62, 255))
    d.line([(15, 15), (15, 21)], fill=(246, 194, 62, 255))
    d.polygon([(12, 24), (15, 20), (16, 20), (19, 24)], fill=(246, 194, 62, 255))
    sprites["icon_history"] = _auto_pixel_outline_32(im)

    # 10. SETTINGS: Brushed Silver & Brass Precision Cogwheel
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for deg in range(0, 360, 45):
        rad = math.radians(deg)
        tx = int(round(16 + math.cos(rad) * 10))
        ty = int(round(16 + math.sin(rad) * 10))
        d.rectangle([tx - 2, ty - 2, tx + 2, ty + 2], fill=(194, 204, 218, 255))
    _draw_shaded_ball_circle(im, 16, 16, 8, (238, 244, 252), (186, 198, 214), (136, 148, 168), seams=False)
    _draw_shaded_ball_circle(im, 16, 16, 4, (252, 216, 98), (222, 162, 42), (156, 108, 28), seams=False)
    im.putpixel((16, 16), (76, 58, 46, 255))
    sprites["icon_settings"] = _auto_pixel_outline_32(im)

    # 11. DISCORD: Official Blurple Rounded Badge with Crisp White Clyde Silhouette
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([4, 5, 27, 26], radius=6, fill=(88, 101, 242, 255))
    d.rounded_rectangle([5, 6, 26, 13], radius=5, fill=(112, 124, 252, 255))
    # White Clyde controller visor + eyes
    d.polygon([(9, 12), (12, 10), (14, 11), (17, 11), (19, 10), (22, 12), (23, 19), (20, 21), (18, 19), (13, 19), (11, 21), (8, 19)], fill=(252, 252, 255, 255))
    d.rectangle([12, 14, 13, 16], fill=(88, 101, 242, 255))
    d.rectangle([18, 14, 19, 16], fill=(88, 101, 242, 255))
    sprites["icon_discord"] = _auto_pixel_outline_32(im)

    # 12. GUIDE / PLAYBOOK: Hardwood Coach's Clipboard with X & O Court Diagram
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([6, 5, 25, 28], radius=3, fill=(182, 122, 64, 255))
    d.rectangle([8, 8, 23, 26], fill=(252, 246, 232, 255))
    d.rectangle([12, 3, 19, 7], fill=(224, 178, 64, 255))
    # Mini half-court key + plays on parchment sheet
    d.arc([11, 8, 20, 16], 0, 180, fill=(216, 142, 78, 255))
    d.line([(10, 19), (15, 14)], fill=(226, 78, 68, 255), width=1)
    im.putpixel((10, 20), (64, 148, 224, 255))
    im.putpixel((19, 18), (64, 148, 224, 255))
    im.putpixel((15, 13), (226, 78, 68, 255))
    book_32 = _auto_pixel_outline_32(im)
    for k in ("icon_book", "prop_guide_clipboard", "tactics_playbook"):
        sprites[k] = book_32.copy()

    # 13. STAR SHIELD: Radiant Sapphire & Gold Emblem Shield
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(6, 5), (16, 3), (25, 5), (24, 17), (16, 28), (7, 17)], fill=(246, 192, 52, 255))
    d.polygon([(8, 7), (16, 5), (23, 7), (22, 16), (16, 25), (9, 16)], fill=(74, 162, 238, 255))
    d.polygon([(16, 8), (18, 13), (22, 13), (19, 16), (20, 20), (16, 18), (12, 20), (13, 16), (10, 13), (14, 13)], fill=(255, 242, 148, 255))
    sprites["icon_star_shield"] = _auto_pixel_outline_32(im)

    # 14. COURT WAND / STUDIO: Golden Architect Wrench & Blueprint Compass
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.line([(7, 25), (23, 9)], fill=(238, 182, 56, 255), width=4)
    _draw_shaded_ball_circle(im, 23, 9, 5, (255, 238, 142), (236, 176, 44), (178, 118, 24), seams=False)
    d.polygon([(23, 5), (27, 9), (24, 11), (21, 8)], fill=(0, 0, 0, 0))
    d.line([(8, 8), (24, 24)], fill=(124, 196, 246, 255), width=3)
    sprites["court_wand"] = _auto_pixel_outline_32(im)

    # 15. PAGER ARROWS (icon_next / icon_prev): Beveled Pastel Emerald Pill Arrow
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([5, 6, 26, 25], radius=6, fill=(84, 208, 136, 255))
    d.rounded_rectangle([6, 7, 25, 14], radius=5, fill=(122, 232, 168, 255))
    d.polygon([(11, 13), (17, 13), (17, 10), (22, 15), (17, 21), (17, 18), (11, 18)], fill=(255, 252, 244, 255))
    arrow_r = _auto_pixel_outline_32(im)
    sprites["icon_next"] = arrow_r
    sprites["icon_prev"] = arrow_r.transpose(Image.Transpose.FLIP_LEFT_RIGHT)

    # 16. BACK / LEAVE: Beveled Pastel Coral-Rose Return Badge
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([5, 6, 26, 25], radius=6, fill=(236, 98, 92, 255))
    d.rounded_rectangle([6, 7, 25, 14], radius=5, fill=(252, 138, 132, 255))
    d.line([(11, 11), (20, 20)], fill=(255, 252, 246, 255), width=3)
    d.line([(20, 11), (11, 20)], fill=(255, 252, 246, 255), width=3)
    leave_32 = _auto_pixel_outline_32(im)
    sprites["icon_leave"] = leave_32
    sprites["icon_back"] = leave_32.copy()

    # 17. CHECKMARK MEDALLION & RUBY GEM
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    _draw_shaded_ball_circle(im, 16, 16, 10, (132, 244, 172), (62, 204, 118), (34, 142, 76), seams=False)
    d = ImageDraw.Draw(im)
    d.line([(11, 16), (14, 20), (21, 12)], fill=(255, 253, 246, 255), width=3)
    sprites["icon_check"] = _auto_pixel_outline_32(im)

    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(11, 6), (20, 6), (26, 13), (16, 26), (5, 13)], fill=(232, 58, 78, 255))
    d.polygon([(12, 8), (19, 8), (22, 13), (9, 13)], fill=(255, 152, 168, 255))
    d.polygon([(9, 13), (22, 13), (16, 23)], fill=(186, 32, 54, 255))
    sprites["icon_gem_ruby"] = _auto_pixel_outline_32(im)

    # 18. 2D COURT HOTBAR ACTION ICONS (Pass, Crossover, Defense, Dunk, AI Bot On/Off)
    # Pass: Sky-cyan speed-streaked basketball
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for sy in (11, 16, 21):
        d.line([(3, sy), (13, sy)], fill=(118, 224, 255, 255), width=2)
    _draw_shaded_ball_circle(im, 19, 16, 7, (255, 182, 74), (236, 114, 34), (172, 68, 20), seams=True)
    sprites["icon_pass"] = _auto_pixel_outline_32(im)

    # Crossover: Lightning-winged high-top court sneaker
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(7, 10), (14, 10), (16, 17), (26, 19), (27, 24), (6, 24)], fill=(246, 192, 48, 255))
    d.rectangle([6, 23, 27, 25], fill=(252, 250, 244, 255))
    d.polygon([(12, 6), (8, 13), (13, 13), (10, 19), (18, 11), (13, 11)], fill=(92, 228, 255, 255))
    sprites["icon_crossover"] = _auto_pixel_outline_32(im)

    # Defense: Cobalt & Gold Lockdown Shield
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(6, 5), (16, 3), (25, 5), (24, 17), (16, 27), (7, 17)], fill=(238, 184, 52, 255))
    d.polygon([(8, 7), (16, 5), (23, 7), (22, 16), (16, 24), (9, 16)], fill=(58, 128, 218, 255))
    d.line([(16, 6), (16, 23)], fill=(122, 194, 255, 255), width=2)
    sprites["icon_defense"] = _auto_pixel_outline_32(im)

    # Dunk + all 5 Signature Dunk Styles: Blazing Rim-Rocking Dunk Emblem
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(10, 4), (13, 9), (16, 3), (19, 9), (22, 5), (21, 14), (11, 14)], fill=(255, 116, 42, 255))
    _draw_shaded_ball_circle(im, 16, 13, 6, (255, 194, 78), (238, 118, 34), (178, 68, 20), seams=True)
    # Orange rim + crisp white net below ball!
    d.rectangle([7, 19, 24, 21], fill=(236, 76, 38, 255))
    for nx in (9, 13, 18, 22):
        d.line([(nx, 21), (11 + (nx - 9) * 2 // 3, 27)], fill=(250, 248, 242, 255))
    dunk_32 = _auto_pixel_outline_32(im)
    for d_id in ("icon_dunk", "dunk_tomahawk", "dunk_windmill", "dunk_spin_360", "dunk_between_legs", "dunk_meteor_jam"):
        sprites[d_id] = dunk_32.copy()

    # Pro Defender AI Bot On / Off
    for b_id, visor_c, body_c in (
        ("icon_bot_on",  (86, 248, 168, 255), (56, 138, 198, 255)),
        ("icon_bot_off", (226, 108, 104, 255), (128, 122, 118, 255)),
    ):
        im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.line([(16, 4), (16, 8)], fill=(196, 204, 214, 255), width=2)
        im.putpixel((16, 4), visor_c)
        d.rounded_rectangle([7, 8, 24, 25], radius=4, fill=body_c)
        d.rectangle([9, 12, 22, 16], fill=(38, 34, 42, 255))
        d.rectangle([10, 13, 21, 15], fill=visor_c)
        d.line([(12, 21), (19, 21)], fill=(238, 238, 244, 255), width=2)
        sprites[b_id] = _auto_pixel_outline_32(im)

    return sprites


# ==============================================================================
# 4. 9 WARM OFF-WHITE / CREAM / BEIGE / PASTEL BUILTBYBIT MENU OVERLAYS
# ==============================================================================

TOP_HEADER_PAD = 7  # Protruding 7px Pixie-Studios raised top ribbon plaque (ascent = 20)!


def _slot_xy(slot_idx: int) -> tuple[int, int]:
    """Top-left pixel (sx, sy) of the 16x16 item window inside our TOP_HEADER_PAD=7 overlay."""
    r, c = divmod(slot_idx, 9)
    return 8 + c * 18, TOP_HEADER_PAD + 18 + r * 18


def _draw_pastel_bento_card(
    im: Image.Image,
    x0: int,
    y0: int,
    x1: int,
    y1: int,
    fill_rgb: tuple[int, int, int],
    border_rgb: tuple[int, int, int],
):
    d = ImageDraw.Draw(im)
    hi = _tint(fill_rgb, 8)
    d.rounded_rectangle([x0, y0, x1, y1], radius=3, fill=(*fill_rgb, 255), outline=(*border_rgb, 255))
    d.line([(x0 + 2, y0 + 1), (x1 - 2, y0 + 1)], fill=hi)


def _draw_overlay_slot_bezel(
    im: Image.Image,
    slot_idx: int,
    well_rgb: tuple[int, int, int] = (232, 222, 204),
    rim_rgb: tuple[int, int, int] | None = None,
):
    sx, sy = _slot_xy(slot_idx)
    d = ImageDraw.Draw(im)
    border = (*rim_rgb, 255) if rim_rgb else _tint(well_rgb, -38)
    d.rectangle([sx - 1, sy - 1, sx + 16, sy + 16], fill=(*well_rgb, 255), outline=border)
    d.line([(sx, sy + 16), (sx + 15, sy + 16)], fill=(254, 252, 246, 255))
    d.line([(sx + 16, sy), (sx + 16, sy + 15)], fill=(254, 252, 246, 255))


def _draw_wooden_boutique_shelf(im: Image.Image, row_idx: int, col_start: int = 1, col_end: int = 7):
    """Draws a cozy Pixie-Studios 3D honey-oak wooden market shelf beneath a row of shop items!"""
    sx0, sy = _slot_xy(row_idx * 9 + col_start)
    sx1, _ = _slot_xy(row_idx * 9 + col_end)
    x0 = sx0 - 3
    x1 = sx1 + 18
    plank_y = sy + 15
    d = ImageDraw.Draw(im)
    # Warm recessed parchment display backing behind items
    d.rounded_rectangle([x0, sy - 2, x1, plank_y], radius=2, fill=(249, 243, 230, 255), outline=(218, 198, 168, 255))
    for c in range(col_start, col_end + 1):
        _draw_overlay_slot_bezel(im, row_idx * 9 + c, well_rgb=(238, 228, 210), rim_rgb=(204, 178, 142))
    # 3D Honey-Oak shelf top + front lip + subtle brackets
    d.rectangle([x0 - 1, plank_y, x1 + 1, plank_y + 1], fill=(238, 196, 138, 255))
    d.rectangle([x0 - 1, plank_y + 2, x1 + 1, plank_y + 3], fill=(192, 136, 78, 255))
    d.line([(x0, plank_y + 4), (x1, plank_y + 4)], fill=(216, 198, 174, 255))


def _draw_crisp_dark_header(
    im: Image.Image,
    text: str,
    cy: int,
    draw_fn,
    measure_fn,
    fg=(62, 40, 26, 255),
    hi=(255, 253, 248, 255),
    cx: int = 88,
):
    tw = measure_fn(text, scale=1)
    tx = cx - tw // 2
    draw_fn(im, text, tx, cy + 1, color=hi, shadow=None, scale=1)
    draw_fn(im, text, tx, cy, color=fg, shadow=None, scale=1)


def _new_warm_pixie_overlay(rows_count: int, title_text: str, accent_rgb: tuple[int, int, int], draw_fn, measure_fn) -> Image.Image:
    """
    Creates a 176px wide BuiltByBit Pixie-Studios Warm Off-White / Creamy Linen overlay:
      - Rows y = 0..6 outside the centered Raised Ribbon Plaque are 100% transparent!
      - Body [0..175, 7..h-1] is warm off-white (#F7F1E5) with soft warm parchment bay (#EFE6D5).
      - Raised top plaque [22..153, 0..18] frames crisp dark-espresso header text (14:1 contrast!).
    """
    w = 176
    h = TOP_HEADER_PAD + 18 + rows_count * 18 + 4
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    # Outer warm off-white window body [0..175, TOP_HEADER_PAD..h-1]
    _paint_warm_cream_panel(im, 0, TOP_HEADER_PAD, w - 1, h - 1, header_ribbon=False)

    # Soft warm-parchment inner canvas [5..170, TOP_HEADER_PAD+14..h-4]
    d.rounded_rectangle(
        [5, TOP_HEADER_PAD + 14, w - 6, h - 4],
        radius=3,
        fill=(240, 231, 215, 255),
        outline=(214, 198, 174, 255),
    )

    # Cute ribbon tails behind raised header plaque
    tail_c = _tint(accent_rgb, -14)
    d.polygon([(14, 6), (24, 4), (24, 15), (14, 15), (18, 10)], fill=tail_c, outline=(88, 64, 48, 255))
    d.polygon([(161, 6), (151, 4), (151, 15), (161, 15), (157, 10)], fill=tail_c, outline=(88, 64, 48, 255))

    # Protruding Raised Pixie-Studios Header Plaque [22..153, 0..18]
    d.rounded_rectangle([22, 0, 153, 18], radius=4, fill=(255, 249, 235, 255), outline=(88, 64, 48, 255))
    d.rounded_rectangle([24, 2, 151, 16], radius=3, fill=(255, 252, 244, 255), outline=(*accent_rgb, 255))
    # Little pastel diamond studs on left & right of header text
    for sx in (30, 145):
        for dx, dy in [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)]:
            im.putpixel((sx + dx, 9 + dy), (*accent_rgb, 255))

    _draw_crisp_dark_header(im, title_text, 6, draw_fn, measure_fn)
    return im


def _build_main_hub_overlay(include_op_slot: bool, draw_fn, measure_fn) -> Image.Image:
    """
    Main Menu (U+E355 non-OP / U+E356 OP):
      - Warm Off-White / Cream / Pastel Bento Cards
      - ZERO "3D Locker" text and ZERO "Player Hub" text (100% clean per user directive!)
      - Non-OP overlay has ZERO orphan box at slot 44!
    """
    im = _new_warm_pixie_overlay(6, "BOSKETBALL", (228, 164, 72), draw_fn, measure_fn)
    d = ImageDraw.Draw(im)

    # Row 0: Pastel Sky-Blue Hero Medallion Card around Slot 13 ONLY (no corner text boxes!)
    sx13, sy13 = _slot_xy(13)
    _draw_pastel_bento_card(im, sx13 - 14, sy13 - 3, sx13 + 29, sy13 + 18, (222, 242, 252), (118, 182, 224))
    _draw_overlay_slot_bezel(im, 13, well_rgb=(242, 250, 255), rim_rgb=(104, 174, 222))
    # Cute decorative pixel stars flanking slot 13
    for cx in (sx13 - 8, sx13 + 23):
        cy = sy13 + 7
        for dx, dy in [(0, -2), (0, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (0, 1), (0, 2)]:
            im.putpixel((cx + dx, cy + dy), (238, 178, 56, 255))

    # Row 2: Warm Pastel Peach-Apricot 5-Slot Feature Deck (slots 20..24)
    sx20, sy20 = _slot_xy(20)
    sx24, _ = _slot_xy(24)
    _draw_pastel_bento_card(im, sx20 - 5, sy20 - 3, sx24 + 20, sy20 + 18, (252, 235, 214), (222, 162, 104))
    row2_tints = [
        (20, (254, 226, 208), (226, 132, 86)),   # Play BosketBall (Pastel Coral-Peach)
        (21, (220, 242, 252), (112, 178, 218)),  # Practice Court (Pastel Sky)
        (22, (255, 242, 198), (222, 172, 62)),   # Boutique Shop (Pastel Butter-Gold)
        (23, (248, 226, 242), (196, 134, 186)),  # Cosmetics & Tags (Pastel Rose-Lilac)
        (24, (252, 238, 204), (214, 162, 74)),   # Leaderboards (Pastel Honey-Gold)
    ]
    for s_idx, w_rgb, r_rgb in row2_tints:
        _draw_overlay_slot_bezel(im, s_idx, well_rgb=w_rgb, rim_rgb=r_rgb)

    # Row 3: 3 Pastel Mini Bento Cards for History (29), Career Profile (31), Friends & Party (33)
    for s_idx, card_rgb, border_rgb in [
        (29, (237, 228, 250), (168, 142, 212)),  # Pastel Lavender
        (31, (255, 243, 206), (218, 174, 72)),   # Pastel Buttercream
        (33, (221, 245, 229), (112, 188, 142)),  # Pastel Sage-Mint
    ]:
        sx, sy = _slot_xy(s_idx)
        _draw_pastel_bento_card(im, sx - 5, sy - 2, sx + 20, sy + 17, card_rgb, border_rgb)
        _draw_overlay_slot_bezel(im, s_idx, well_rgb=_mix(card_rgb, (255, 255, 255), 0.45)[:3], rim_rgb=border_rgb)

    # Row 4: Symmetrical Warm Sand-Cream Bottom Utility Dock (39 Discord, 40 Settings, 41 Guide)
    sx39, sy39 = _slot_xy(39)
    sx41, _ = _slot_xy(41)
    _draw_pastel_bento_card(im, sx39 - 5, sy39 - 2, sx41 + 20, sy39 + 17, (234, 226, 212), (188, 170, 144))
    _draw_overlay_slot_bezel(im, 39, well_rgb=(228, 232, 252), rim_rgb=(128, 138, 228))
    _draw_overlay_slot_bezel(im, 40, well_rgb=(244, 240, 232), rim_rgb=(168, 156, 138))
    _draw_overlay_slot_bezel(im, 41, well_rgb=(250, 234, 224), rim_rgb=(208, 142, 122))

    if include_op_slot:
        sx44, sy44 = _slot_xy(44)
        _draw_pastel_bento_card(im, sx44 - 3, sy44 - 2, sx44 + 18, sy44 + 17, (224, 240, 252), (116, 174, 218))
        _draw_overlay_slot_bezel(im, 44, well_rgb=(240, 248, 255), rim_rgb=(108, 166, 214))

    return _pin_glyph_width(im)


def generate_builtbybit_pastel_gui_overlays(font_tex_dir: Path, draw_fn, measure_fn) -> list[dict]:
    """
    Generates all 9 Warm Off-White / Cream / Beige / Pastel Custom Menu Overlays (U+E350..U+E358):
      - U+E350 gui_overlay_shop.png         (3D Honey-Oak Boutique Shelves!)
      - U+E351 gui_overlay_shop_grid.png    (Pastel Buttercream 7x4 Showcase!)
      - U+E352 gui_overlay_tags.png         (Pastel Lavender-Cream Chat Tags Vault!)
      - U+E353 gui_overlay_cosmetics.png    (Pastel Apricot & Rose Ball/Dunk Wardrobe!)
      - U+E354 gui_overlay_play.png         (Pastel Peach & Sky Play Modes Selector!)
      - U+E355 gui_overlay_hub.png          (Symmetrical Non-OP Main Menu — ZERO "3D Locker"/"Player Hub"!)
      - U+E356 gui_overlay_hub_op.png       (OP Main Menu — ZERO "3D Locker"/"Player Hub"!)
      - U+E357 gui_overlay_leaderboards.png (3D Gold/Silver/Bronze Championship Podium!)
      - U+E358 gui_overlay_settings.png     (45-Slot 5-Row Pastel Sage-Cream Settings!)
    """
    providers: list[dict] = []

    # --- U+E350: BOUTIQUE SHOP (3D Honey-Oak Wooden Market Shelves!) ---
    shop = _new_warm_pixie_overlay(6, "BOUTIQUE SHOP", (228, 162, 68), draw_fn, measure_fn)
    # Row 0 filter ribbon: Wallet (0), All (2), Balls (3), Dunks (4), Tags (5), My Locker (8)
    _draw_overlay_slot_bezel(shop, 0, well_rgb=(255, 242, 198), rim_rgb=(218, 168, 58))
    for tab_s, tab_w, tab_r in [
        (2, (252, 236, 212), (214, 154, 88)),
        (3, (254, 228, 206), (224, 134, 78)),
        (4, (248, 226, 238), (198, 126, 168)),
        (5, (236, 228, 250), (164, 136, 208)),
    ]:
        _draw_overlay_slot_bezel(shop, tab_s, well_rgb=tab_w, rim_rgb=tab_r)
    _draw_overlay_slot_bezel(shop, 8, well_rgb=(222, 242, 252), rim_rgb=(112, 176, 218))
    # Rows 1..4: 4 Wooden Boutique Shelves!
    for r in (1, 2, 3, 4):
        _draw_wooden_boutique_shelf(shop, r, 1, 7)
    # Row 5: Bottom Pager & Navigation Bar (45, 48, 49, 50, 53)
    for nav_s, nw, nr in [
        (45, (250, 228, 224), (212, 132, 124)),
        (48, (222, 244, 230), (108, 186, 138)),
        (49, (255, 244, 206), (214, 170, 68)),
        (50, (222, 244, 230), (108, 186, 138)),
        (53, (222, 240, 252), (112, 174, 218)),
    ]:
        _draw_overlay_slot_bezel(shop, nav_s, well_rgb=nw, rim_rgb=nr)
    _pin_glyph_width(shop).save(font_tex_dir / "gui_overlay_shop.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_shop.png", "height": shop.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE350"]})

    # --- U+E351: BOUTIQUE SHOP CATEGORY GRID (7x4 Pastel Buttercream Case) ---
    sgrid = _new_warm_pixie_overlay(6, "BOUTIQUE VAULT", (228, 162, 68), draw_fn, measure_fn)
    _draw_overlay_slot_bezel(sgrid, 0, well_rgb=(255, 242, 198), rim_rgb=(218, 168, 58))
    for tab_s in (2, 3, 4, 5):
        _draw_overlay_slot_bezel(sgrid, tab_s, well_rgb=(252, 238, 214), rim_rgb=(214, 158, 86))
    _draw_overlay_slot_bezel(sgrid, 8, well_rgb=(222, 242, 252), rim_rgb=(112, 176, 218))
    gx0, gy0 = _slot_xy(10)
    gx1, gy1 = _slot_xy(43)
    _draw_pastel_bento_card(sgrid, gx0 - 4, gy0 - 3, gx1 + 19, gy1 + 18, (252, 245, 226), (214, 178, 112))
    for r in (1, 2, 3, 4):
        for c in range(1, 8):
            _draw_overlay_slot_bezel(sgrid, r * 9 + c, well_rgb=(242, 232, 212), rim_rgb=(202, 178, 138))
    for nav_s in (45, 48, 49, 50, 53):
        _draw_overlay_slot_bezel(sgrid, nav_s, well_rgb=(236, 228, 214), rim_rgb=(188, 168, 140))
    _pin_glyph_width(sgrid).save(font_tex_dir / "gui_overlay_shop_grid.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_shop_grid.png", "height": sgrid.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE351"]})

    # --- U+E352: CHAT TAGS VAULT (Pastel Lavender-Cream Collector Case) ---
    tags = _new_warm_pixie_overlay(6, "CHAT TAGS VAULT", (168, 134, 212), draw_fn, measure_fn)
    _draw_overlay_slot_bezel(tags, 0, well_rgb=(255, 242, 198), rim_rgb=(218, 168, 58))
    rarity_tabs = [
        (1, (224, 245, 232), (114, 188, 144)),  # All
        (2, (242, 238, 230), (178, 168, 152)),  # Common
        (3, (220, 240, 252), (108, 172, 218)),  # Rare
        (4, (238, 226, 252), (168, 132, 214)),  # Epic
        (5, (255, 242, 198), (222, 172, 62)),   # Legendary
        (6, (254, 228, 224), (222, 132, 122)),  # Exclusive
    ]
    for ts, tw, tr in rarity_tabs:
        _draw_overlay_slot_bezel(tags, ts, well_rgb=tw, rim_rgb=tr)
    _draw_overlay_slot_bezel(tags, 8, well_rgb=(250, 228, 224), rim_rgb=(214, 132, 124))
    _draw_pastel_bento_card(tags, gx0 - 4, gy0 - 3, gx1 + 19, gy1 + 18, (246, 239, 252), (182, 152, 216))
    for r in (1, 2, 3, 4):
        for c in range(1, 8):
            _draw_overlay_slot_bezel(tags, r * 9 + c, well_rgb=(238, 230, 246), rim_rgb=(188, 166, 212))
    for nav_s in (45, 47, 48, 49, 50, 53):
        _draw_overlay_slot_bezel(tags, nav_s, well_rgb=(238, 232, 222), rim_rgb=(184, 168, 146))
    _pin_glyph_width(tags).save(font_tex_dir / "gui_overlay_tags.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_tags.png", "height": tags.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE352"]})

    # --- U+E353: MY COSMETICS (Basketballs Row 1 + Signature Dunks Row 3) ---
    cosm = _new_warm_pixie_overlay(6, "MY COSMETICS", (226, 142, 92), draw_fn, measure_fn)
    sx9, sy9 = _slot_xy(9)
    sx17, _ = _slot_xy(17)
    _draw_pastel_bento_card(cosm, sx9 - 2, sy9 - 3, sx17 + 17, sy9 + 18, (253, 236, 216), (222, 158, 102))
    for bs in (9, 10, 11, 12, 14, 15, 16, 17):
        _draw_overlay_slot_bezel(cosm, bs, well_rgb=(246, 232, 212), rim_rgb=(212, 154, 102))
    # Cute mini orange basketball medallion centered in the slot 13 gap!
    cx13, cy13 = _slot_xy(13)
    _draw_shaded_ball_circle(cosm, cx13 + 8, cy13 + 8, 5, (255, 182, 74), (236, 114, 34), (172, 68, 20), seams=True)
    # Clean warm pastel-rose section banner for Signature Dunks (Row 2 gap -> Row 3 slots 29..33)
    _, sy22 = _slot_xy(22)
    _draw_pastel_bento_card(cosm, 34, sy22 + 3, 141, sy22 + 14, (250, 228, 238), (204, 138, 168))
    _draw_crisp_dark_header(cosm, "SIGNATURE DUNKS", sy22 + 6, draw_fn, measure_fn)
    sx29, sy29 = _slot_xy(29)
    sx33, _ = _slot_xy(33)
    _draw_pastel_bento_card(cosm, sx29 - 5, sy29 - 3, sx33 + 20, sy29 + 18, (248, 232, 242), (198, 140, 176))
    for ds in (29, 30, 31, 32, 33):
        _draw_overlay_slot_bezel(cosm, ds, well_rgb=(244, 228, 238), rim_rgb=(196, 142, 174))
    for nav_s, nw, nr in [
        (48, (238, 228, 250), (168, 138, 212)),
        (49, (250, 230, 226), (212, 136, 126)),
        (50, (255, 242, 202), (218, 168, 64)),
    ]:
        _draw_overlay_slot_bezel(cosm, nav_s, well_rgb=nw, rim_rgb=nr)
    _pin_glyph_width(cosm).save(font_tex_dir / "gui_overlay_cosmetics.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_cosmetics.png", "height": cosm.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE353"]})

    # --- U+E354: PLAY MODES SELECTOR (Pastel Peach Ranked Card + Pastel Sky Training Card) ---
    play = _new_warm_pixie_overlay(6, "PLAY MODES", (232, 148, 76), draw_fn, measure_fn)
    sx10, sy10 = _slot_xy(10)
    sx16, _ = _slot_xy(16)
    _draw_pastel_bento_card(play, sx10 - 6, sy10 - 3, sx16 + 21, sy10 + 18, (253, 235, 214), (222, 154, 88))
    for ms in (10, 12, 14, 16):
        _draw_overlay_slot_bezel(play, ms, well_rgb=(255, 244, 226), rim_rgb=(218, 148, 82))
    _, sy22 = _slot_xy(22)
    _draw_pastel_bento_card(play, 36, sy22 + 3, 139, sy22 + 14, (224, 244, 250), (114, 182, 208))
    _draw_crisp_dark_header(play, "PRACTICE & LIVE", sy22 + 6, draw_fn, measure_fn)
    sx28, sy28 = _slot_xy(28)
    sx34, _ = _slot_xy(34)
    _draw_pastel_bento_card(play, sx28 - 6, sy28 - 3, sx34 + 21, sy28 + 18, (226, 244, 250), (118, 182, 208))
    for ts in (28, 30, 32, 34):
        _draw_overlay_slot_bezel(play, ts, well_rgb=(240, 250, 254), rim_rgb=(112, 178, 206))
    _draw_overlay_slot_bezel(play, 49, well_rgb=(250, 230, 226), rim_rgb=(212, 136, 126))
    _pin_glyph_width(play).save(font_tex_dir / "gui_overlay_play.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_play.png", "height": play.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE354"]})

    # --- U+E355 (Non-OP) & U+E356 (OP): MAIN MENU (ZERO "3D Locker" & ZERO "Player Hub"!) ---
    hub_non_op = _build_main_hub_overlay(False, draw_fn, measure_fn)
    hub_non_op.save(font_tex_dir / "gui_overlay_hub.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_hub.png", "height": hub_non_op.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE355"]})

    hub_op = _build_main_hub_overlay(True, draw_fn, measure_fn)
    hub_op.save(font_tex_dir / "gui_overlay_hub_op.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_hub_op.png", "height": hub_op.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE356"]})

    # --- U+E357: LEADERBOARDS (Top 8 Category Tabs + 3D Gold/Silver/Bronze Podium!) ---
    lb = _new_warm_pixie_overlay(6, "LEADERBOARDS", (226, 174, 62), draw_fn, measure_fn)
    for c in (0, 1, 2, 3, 5, 6, 7, 8):
        _draw_overlay_slot_bezel(lb, c, well_rgb=(252, 242, 214), rim_rgb=(212, 168, 82))
    # Decorative golden star medallion centered in the slot 4 gap!
    sx4, sy4 = _slot_xy(4)
    for dx, dy in [(0, -2), (0, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (0, 1), (0, 2)]:
        lb.putpixel((sx4 + 8 + dx, sy4 + 8 + dy), (238, 178, 48, 255))
    # #1 Gold Champion Pedestal (slot 13)
    sx13, sy13 = _slot_xy(13)
    _draw_pastel_bento_card(lb, sx13 - 5, sy13 - 2, sx13 + 20, sy13 + 17, (255, 242, 192), (222, 168, 48))
    _draw_overlay_slot_bezel(lb, 13, well_rgb=(255, 249, 222), rim_rgb=(224, 172, 48))
    # #2 Silver Pedestal (slot 21) & #3 Bronze Pedestal (slot 23)
    sx21, sy21 = _slot_xy(21)
    _draw_pastel_bento_card(lb, sx21 - 4, sy21 - 2, sx21 + 19, sy21 + 17, (232, 239, 246), (148, 164, 182))
    _draw_overlay_slot_bezel(lb, 21, well_rgb=(244, 248, 252), rim_rgb=(146, 162, 180))
    sx23, sy23 = _slot_xy(23)
    _draw_pastel_bento_card(lb, sx23 - 4, sy23 - 2, sx23 + 19, sy23 + 17, (250, 228, 206), (196, 136, 86))
    _draw_overlay_slot_bezel(lb, 23, well_rgb=(253, 240, 226), rim_rgb=(194, 134, 84))
    # Contenders #4..#10 (slots 29..33, 39, 41)
    for cs in (29, 30, 31, 32, 33, 39, 41):
        _draw_overlay_slot_bezel(lb, cs, well_rgb=(238, 230, 214), rim_rgb=(194, 176, 148))
    _draw_overlay_slot_bezel(lb, 49, well_rgb=(250, 230, 226), rim_rgb=(212, 136, 126))
    _pin_glyph_width(lb).save(font_tex_dir / "gui_overlay_leaderboards.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_leaderboards.png", "height": lb.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE357"]})

    # --- U+E358: PLAYER SETTINGS (5-Row 45-Slot Pastel Sage-Mint Card!) ---
    st = _new_warm_pixie_overlay(5, "SETTINGS", (112, 186, 142), draw_fn, measure_fn)
    sx19, sy19 = _slot_xy(19)
    sx24, _ = _slot_xy(24)
    _draw_pastel_bento_card(st, sx19 - 5, sy19 - 4, sx24 + 20, sy19 + 19, (226, 245, 232), (118, 186, 144))
    for ss in (19, 20, 21, 22, 23, 24):
        _draw_overlay_slot_bezel(st, ss, well_rgb=(244, 252, 246), rim_rgb=(122, 188, 146))
    _draw_overlay_slot_bezel(st, 40, well_rgb=(250, 230, 226), rim_rgb=(212, 136, 126))
    _pin_glyph_width(st).save(font_tex_dir / "gui_overlay_settings.png")
    providers.append({"type": "bitmap", "file": "bosketball:font/gui_overlay_settings.png", "height": st.height, "ascent": 13 + TOP_HEADER_PAD, "chars": ["\uE358"]})

    return providers
