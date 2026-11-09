#!/usr/bin/env python3
"""
BosketBall 26.2 — Studio-Grade 3D Model, PBR Sphere Texture, Custom HUD/Font & Sound Generator
Targets Minecraft Java Edition 26.2 ("Chaos Cubed", pack format 88.0).
"""

import hashlib
import json
import math
import os
import struct
import subprocess
import wave
import zipfile
from pathlib import Path

try:
    import imageio_ffmpeg
except ImportError:
    imageio_ffmpeg = None
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from cuboide_icons import (
    build_cuboide_icon_sprites,
    generate_cuboide_tag_emblem_items,
    generate_cytooxien_gui_overlays,
    generate_large_ball_hover_previews,
    generate_modern_hidpi_rank_pills,
)
from bosket_gui_kit import generate_container_textures, generate_gui_overlays
from builtbybit_gui_and_skins import (
    build_builtbybit_32x32_icons,
    generate_builtbybit_pastel_gui_overlays,
    generate_lobby_npc_skins,
    generate_warm_cream_container_gui,
)
from premium_ui_overhaul import (
    build_premium_icon_sprites,
    generate_premium_tag_emblem_items,
)
import ball_model
from generate_nameplates import provider_entries as generate_nameplate_font_providers

from icon_language import authored_icon_sprites, conform as apply_icon_language


def conform_icons_to_language(item_tex_dir):
    """Re-apply the icon language to every icon sprite just written.

    This is what makes the conformance pass durable.  Without it the next art
    rebuild silently restores every legacy ink colour and stray pixel that
    ``tools/icon_conformance.py`` removed, and the set drifts back apart one
    sprite at a time with nothing to show for it in a diff.
    """
    touched = 0
    for sprite in sorted(item_tex_dir.glob("icon_*.png")):
        image = Image.open(sprite).convert("RGBA")
        fixed, report = apply_icon_language(image)
        if any(report.values()):
            fixed.save(sprite)
            touched += 1
    ref_dir = item_tex_dir / "icons"
    if ref_dir.is_dir():
        for sprite in sorted(ref_dir.glob("ref_*.png")):
            image = Image.open(sprite).convert("RGBA")
            fixed, report = apply_icon_language(image)
            if any(report.values()):
                fixed.save(sprite)
                touched += 1
    if touched:
        print(f"Conformed {touched} icon sprites to the icon language")
    return touched


from pack_overhaul_assets import (
    draw_text as overhaul_draw_text,
    generate_1x1_server_logos,
    generate_all_overhaul_font_providers,
    generate_custom_container_gui,
    generate_custom_hooper_player_skins,
    generate_hooper_3d_rig_models,
    generate_lobby_3d_props,
    generate_pixel_art_item_icons,
    generate_team_floor_rings,
    measure_text as overhaul_measure_text,
)

ROOT = Path(__file__).resolve().parent.parent
RP_DIR = ROOT / "resourcepack"
RELEASE_DIR = ROOT / "releases"
PREVIEW_DIR = ROOT / "docs" / "previews"


# ============================================================================
# 1. TINY PIXEL FONT (3x5 & 4x6) FOR CRISP MINECRAFT RANK PILLS & LOGO BADGES
# ============================================================================
GLYPHS_3X5 = {
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
    ":": ["0", "1", "0", "1", "0"],
    "%": ["101", "001", "010", "100", "101"],
    "-": ["000", "000", "111", "000", "000"],
    "*": ["010", "111", "010", "101", "000"],
    ".": ["0", "0", "0", "0", "1"],
    " ": ["00", "00", "00", "00", "00"],
}


def draw_pixel_text(img: Image.Image, text: str, x: int, y: int, color=(255, 255, 255, 255), shadow=None, scale=1):
    px = img.load()
    passes = [(max(1, scale // 2), max(1, scale // 2), shadow), (0, 0, color)] if shadow else [(0, 0, color)]
    end_x = x
    for ox, oy, col in passes:
        cx = x
        for ch in text.upper():
            pat = GLYPHS_3X5.get(ch, GLYPHS_3X5[" "])
            w = len(pat[0])
            for ry, row in enumerate(pat):
                for rx, bit in enumerate(row):
                    if bit == "1":
                        for sy in range(scale):
                            for sx in range(scale):
                                dx = cx + rx * scale + sx + ox
                                dy = y + ry * scale + sy + oy
                                if 0 <= dx < img.width and 0 <= dy < img.height:
                                    px[dx, dy] = col
            cx += (w + 1) * scale
        end_x = cx
    return end_x - x


def measure_pixel_text(text: str, scale=1) -> int:
    total = 0
    for ch in text.upper():
        pat = GLYPHS_3X5.get(ch, GLYPHS_3X5[" "])
        total += (len(pat[0]) + 1) * scale
    return max(0, total - scale)


# ============================================================================
# 2. BALL SYSTEM — see tools/ball_model.py
# ============================================================================
# The ball used to be a cube with a cross painted independently on each of its six faces:
# the seams died at every cube edge and the leather was six unrelated hides. It is now one
# cube whose six faces are painted from the *direction* each texel looks at: the eight-panel
# network of a real basketball crosses every edge unbroken, and the pebble grain is one hide
# wrapped round the whole ball. Geometry, atlas painting, `display` transforms and the
# contact-sheet renderer all live in `tools/ball_model.py`; this file only wires them in.

# Face-size previews of the eight balls, used by the branding sheet and the icon cards.
BALL_PREVIEW_SKINS = ball_model.SKIN_ORDER
BALL_PREVIEW_LABELS = {
    "classic": "GAME BALL",
    "street": "STREET WORN",
    "blacktop": "BLACKTOP",
    "glow": "GLOW",
    "team_home": "TEAM HOME",
    "team_away": "TEAM AWAY",
    "two_tone": "TWO-TONE",
    "fire": "FIRE",
}


def render_ball_preview(skin: str, out_size: int = 160) -> Image.Image:
    """One skin at hero size, for the branding sheet and the collector cards."""
    return ball_model.render_ball(
        ball_model.skin_model(skin), ball_model.skin_textures(skin),
        size=out_size, yaw=34, pitch=18, ss=2,
    )


# ============================================================================
# 3. 3D HOOP + TEMPERED GLASS BACKBOARD + 26.2 MULTI-AXIS CIRCULAR RIM & NET
# ============================================================================
def generate_hoop_textures(item_tex_dir: Path):
    # 1. Sleek Dark-Smoke Tempered Arena Glass Backboard (128x128 RGBA — front face with Shooter's Square & Crunch-Time LED!)
    glass = Image.new("RGBA", (128, 128), (16, 22, 34, 156))
    draw = ImageDraw.Draw(glass)
    for i in range(-64, 160, 28):
        draw.line([(i, 0), (i + 64, 128)], fill=(112, 168, 228, 34), width=4)
    # Brushed dark gunmetal outer frame + glowing NBA crunch-time amber-crimson LED strip + crisp thin shooter's square
    draw.rectangle([0, 0, 127, 127], outline=(26, 32, 44, 255), width=4)
    draw.rectangle([4, 4, 123, 123], outline=(255, 118, 36, 240), width=2)
    draw.rectangle([43, 66, 85, 104], outline=(255, 198, 72, 232), width=2)
    glass.save(item_tex_dir / "hoop_glass.png")

    # 1b. Anti-Parallax Smoked Rear Glass Texture (zero double-wall white lines from courtside angles!)
    glass_back = Image.new("RGBA", (128, 128), (14, 18, 30, 148))
    bd = ImageDraw.Draw(glass_back)
    bd.rectangle([0, 0, 127, 127], outline=(26, 32, 44, 255), width=4)
    bd.rectangle([4, 4, 123, 123], outline=(218, 92, 28, 210), width=2)
    glass_back.save(item_tex_dir / "hoop_glass_back.png")

    # 2. Hoop Metal / Rim / Braided Net / Padded Stanchion atlas sheet (64x64)
    atlas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    ad = ImageDraw.Draw(atlas)
    # [0..31, 0..31]: Powder-coated Arena Orange Steel Rim
    for y in range(32):
        for x in range(32):
            v = int(18 * math.sin(y * 0.35))
            atlas.putpixel((x, y), (min(255, 242 + v // 2), max(0, 88 + v), 20, 255))
    # [32..63, 0..31]: Brushed Anodized Obsidian-Gunmetal Stanchion & Frame
    for y in range(32):
        for x in range(32, 64):
            v = int(8 * math.cos(x * 0.6))
            atlas.putpixel((x, y), (34 + v, 40 + v, 52 + v, 255))
    # [0..31, 32..63]: Subtle Silver-Gray Braided Nylon Cord (never looks like raw white blocks!)
    for y in range(32, 64):
        for x in range(32):
            braid = int(14 * math.sin((x + y) * 1.1))
            c = min(242, 206 + braid)
            atlas.putpixel((x, y), (c, c + 4, min(255, c + 12), 232))
    # [32..63, 32..63]: Sleek Obsidian Shot-Clock Bezel & Gold-Red Arena Cushion Accent
    ad.rectangle([32, 32, 63, 63], fill=(18, 22, 32, 255))
    ad.rectangle([34, 34, 61, 61], outline=(255, 148, 38, 255), width=2)
    atlas.save(item_tex_dir / "hoop_parts.png")

    # 3. Seamless Full-Width Edge-to-Edge 45° Braided White Nylon Net Texture (64x64 RGBA — ZERO side triangle holes!)
    # Fills 100% of [0..63] horizontally so all 8 octant panels meet edge-to-edge around the entire circumference!
    net_tex = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    np_px = net_tex.load()
    for y in range(64):
        for x in range(64):
            d1 = (x + y) % 12
            d2 = (x - y) % 12
            on_diag_1 = d1 in (0, 1, 2)
            on_diag_2 = d2 in (0, 1, 2)
            top_loop = y <= 3
            bot_loop = y >= 60
            mid_tie = y in (30, 31) and (x % 6 in (0, 1, 2))
            side_seam = x in (0, 1, 62, 63) and (y % 6 in (0, 1, 2, 3))
            if top_loop and y <= 1 and (x % 16 in (6, 7, 8, 9)):
                # Welded orange-steel rim hook attachment
                np_px[x, y] = (238, 96, 26, 255)
            elif on_diag_1 or on_diag_2 or (top_loop and (x % 4 != 0)) or (bot_loop and (x % 4 != 0)) or mid_tie or side_seam:
                knot = (on_diag_1 and on_diag_2) or top_loop or bot_loop
                shade = 248 if knot else (234 if (x + y) % 2 == 0 else 218)
                np_px[x, y] = (shade, min(255, shade + 2), min(255, shade + 6), 255)
    net_tex.save(item_tex_dir / "hoop_net_mesh.png")


def build_hoop_model_json(state: str = "idle") -> dict:
    """
    Unified Floor-to-ShotClock 3D NBA Pro Hydraulic Stanchion + Tempered Glass Backboard + Circular Rim
    + Seamless Alpha-Cutout Diamond-Weave Net!
    Exact 1:1 Physics-to-Visual calibration at HOOP_SCALE = 2.00f (0.125 blocks per model unit):
      - Rim center sits at EXACT [8.0, 8.0, 8.0] (y = floorY + 3.05m)
      - Floor stanchion base reaches Y = -16.0 (3.00m below rim -> sits flush on court apron!) and Z = -15.8
      - Glass front face sits at Z = 2.80 (0.650 blocks behind rim; BACKBOARD_OFFSET = 0.650)
      - Diamond-weave net clips directly onto the steel rim at Y = 8.02 with ZERO gaps or floating white sticks!
    """
    elements = []

    def uv_orange():
        return [1, 1, 7, 7]

    def uv_metal():
        return [9, 1, 15, 7]

    def uv_net():
        return [1, 9, 7, 15]

    def uv_led():
        return [9, 9, 15, 15]

    def all_faces(tex, uv):
        return {f: {"uv": uv, "texture": tex} for f in ("north", "south", "east", "west", "up", "down")}

    # 1. Floor-Anchored Hydraulic NBA Stanchion Base, Angled Mast, Overhead Boom & Rear Backboard Mount Plate
    elements.append({
        "name": "stanchion_floor_pad",
        "from": [3.6, -16.0, -15.8],
        "to":   [12.4, -14.4, -7.8],
        "faces": all_faces("#parts", uv_metal()),
    })
    elements.append({
        "name": "stanchion_padded_column",
        "from": [5.0, -14.4, -14.8],
        "to":   [11.0, 2.4, -9.8],
        "faces": all_faces("#parts", uv_led()),
    })
    elements.append({
        "name": "stanchion_upper_mast",
        "from": [5.8, 2.4, -13.6],
        "to":   [10.2, 10.6, -9.8],
        "faces": all_faces("#parts", uv_metal()),
    })
    elements.append({
        "name": "stanchion_main_boom",
        "from": [6.3, 8.2, -11.4],
        "to":   [9.7, 10.8, 2.25],
        "faces": all_faces("#parts", uv_metal()),
    })
    elements.append({
        "name": "stanchion_hydraulic_strut",
        "from": [6.9, 4.4, -10.8],
        "to":   [9.1, 6.0, 2.2],
        "rotation": {"origin": [8.0, 5.2, -4.3], "axis": "x", "angle": 22.5},
        "faces": all_faces("#parts", uv_led()),
    })
    elements.append({
        "name": "backboard_mount_plate",
        "from": [5.6, 6.8, 1.92],
        "to":   [10.4, 12.2, 2.34],
        "faces": all_faces("#parts", uv_metal()),
    })

    # 2. Anodized Gunmetal Backboard Frame + Rubberized NBA Bottom Safety Pad (-4..20 in X = 3.0m wide, 5.6..20.6 in Y = 1.88m tall)
    for name, frm, to in [
        ("frame_bottom",  [-4.0, 5.6, 2.05], [20.0, 6.45, 2.88]),
        ("frame_top",     [-4.0, 19.9, 2.05], [20.0, 20.65, 2.88]),
        ("frame_left",    [-4.0, 6.4, 2.05], [-3.15, 19.95, 2.88]),
        ("frame_right",   [19.15, 6.4, 2.05], [20.0, 19.95, 2.88]),
        ("frame_cross_l", [-3.2, 9.8, 2.02], [5.8, 10.4, 2.32]),
        ("frame_cross_r", [10.2, 9.8, 2.02], [19.2, 10.4, 2.32]),
    ]:
        elements.append({"name": name, "from": frm, "to": to, "faces": all_faces("#parts", uv_metal())})
    elements.append({
        "name": "backboard_bottom_safety_pad",
        "from": [-4.15, 5.05, 1.96],
        "to":   [20.15, 5.75, 2.96],
        "faces": all_faces("#parts", uv_led()),
    })

    # Tinted Dark-Smoke Tempered Glass Backboard (front face at Z = 2.80 -> exact 0.650m behind rim center!)
    elements.append({
        "name": "tempered_glass",
        "from": [-3.15, 6.45, 2.32],
        "to":   [19.15, 19.9, 2.80],
        "faces": {
            "north": {"uv": [16, 0, 0, 16], "texture": "#glass_back"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#glass"},
            "east":  {"uv": [9, 1, 10, 7], "texture": "#parts"},
            "west":  {"uv": [9, 1, 10, 7], "texture": "#parts"},
            "up":    {"uv": [9, 1, 15, 2], "texture": "#parts"},
            "down":  {"uv": [9, 1, 15, 2], "texture": "#parts"},
        },
    })

    # Integrated Shot-Clock Neck & Bezel Hood atop Backboard (Y = 20.6..25.9)
    elements.append({
        "name": "shot_clock_neck",
        "from": [7.2, 20.6, 2.15],
        "to":   [8.8, 21.6, 2.75],
        "faces": all_faces("#parts", uv_metal()),
    })
    elements.append({
        "name": "shot_clock_housing",
        "from": [4.6, 21.5, 1.95],
        "to":   [11.4, 25.9, 3.05],
        "light_emission": 10,
        "faces": all_faces("#parts", uv_led()),
    })

    # Breakaway Hinge Box (connects glass at Z=2.80 to back of rim at Z=4.16)
    rim_tilt = -13.0 if state == "dunk_flex" else 0.0
    elements.append({
        "name": "breakaway_box",
        "from": [6.6, 7.4, 2.80],
        "to":   [9.4, 8.6, 4.25],
        "faces": all_faces("#parts", uv_orange()),
    })

    # 3. 16-Segment True Circular Steel Rim centered at EXACT [8.0, 8.0, 8.0], radius = 3.84 (0.480m)!
    rcx, rcy, rcz = 8.0, 8.0, 8.0
    rim_r = 3.84
    seg_count = 16
    seg_half_len = rim_r * math.tan(math.pi / seg_count) * 1.06
    for i in range(seg_count):
        deg = i * 22.5
        ang = math.radians(deg)
        sx = rcx + rim_r * math.sin(ang)
        sz = rcz + rim_r * math.cos(ang)
        dy = (sz - 4.16) * math.sin(math.radians(rim_tilt)) if rim_tilt != 0.0 else 0.0
        q_idx = int(round(deg / 90.0))
        rem_angle = deg - q_idx * 90.0
        hx, hz = (seg_half_len, 0.30) if (q_idx % 2 == 0) else (0.30, seg_half_len)
        seg_el = {
            "name": f"rim_seg_{i}",
            "from": [round(sx - hx, 4), round(rcy - 0.30 + dy, 4), round(sz - hz, 4)],
            "to":   [round(sx + hx, 4), round(rcy + 0.30 + dy, 4), round(sz + hz, 4)],
            "faces": all_faces("#parts", uv_orange()),
        }
        if abs(rem_angle) > 1e-3:
            seg_el["rotation"] = {
                "origin": [round(sx, 4), round(rcy + dy, 4), round(sz, 4)],
                "axis": "y",
                "angle": float(rem_angle),
            }
        elements.append(seg_el)
        # Welded orange-steel net hook loop right beneath each rim segment (zero floating white collars!)
        elements.append({
            "name": f"rim_hook_{i}",
            "from": [round(sx - 0.16, 4), round(rcy - 0.54 + dy, 4), round(sz - 0.16, 4)],
            "to":   [round(sx + 0.16, 4), round(rcy - 0.26 + dy, 4), round(sz + 0.16, 4)],
            "faces": all_faces("#parts", uv_orange()),
        })

    # 4. Gap-Free 5-Tier Closed Octagonal Tapered NBA Braided Net (#net_mesh):
    #    5 stacked closed 8-sided octagonal bands (5 x 8 = 40 seamless edge-to-edge panels)
    #    tapering smoothly from r = 3.50 down to r = 2.30 (100% inside rim_r = 3.84, ZERO triangular holes!)
    y_top = 7.94 if state != "dunk_flex" else 7.66
    if state == "idle":
        ring_specs = [
            (3.50, y_top - 0.95, y_top + 0.04, 0,  4),
            (3.22, y_top - 1.90, y_top - 0.85, 3,  7),
            (2.92, y_top - 2.85, y_top - 1.80, 6,  10),
            (2.62, y_top - 3.75, y_top - 2.75, 9,  13),
            (2.36, y_top - 4.60, y_top - 3.65, 12, 16),
        ]
    elif state == "swish_1":
        ring_specs = [
            (3.50, y_top - 0.90, y_top + 0.04, 0,  4),
            (3.42, y_top - 1.80, y_top - 0.80, 3,  7),
            (3.32, y_top - 2.70, y_top - 1.70, 6,  10),
            (3.20, y_top - 3.55, y_top - 2.60, 9,  13),
            (3.08, y_top - 4.35, y_top - 3.45, 12, 16),
        ]
    elif state == "swish_2":
        ring_specs = [
            (3.50, y_top - 0.72, y_top + 0.04, 0,  4),
            (3.28, y_top - 1.40, y_top - 0.64, 3,  7),
            (3.08, y_top - 2.08, y_top - 1.32, 6,  10),
            (2.92, y_top - 2.72, y_top - 2.00, 9,  13),
            (2.80, y_top - 3.32, y_top - 2.64, 12, 16),
        ]
    else:  # dunk_flex
        ring_specs = [
            (3.50, y_top - 1.00, y_top + 0.04, 0,  4),
            (3.20, y_top - 2.00, y_top - 0.90, 3,  7),
            (2.88, y_top - 3.00, y_top - 1.90, 6,  10),
            (2.58, y_top - 3.95, y_top - 2.90, 9,  13),
            (2.32, y_top - 4.85, y_top - 3.85, 12, 16),
        ]

    tan_oct = math.tan(math.pi / 8.0) * 1.05  # Exact edge-to-edge overlap so zero seams ever open!
    for tier_idx, (r_band, y0, y1, v0, v1) in enumerate(ring_specs):
        w_band = r_band * tan_oct
        ns_faces = {
            "north": {"uv": [0, v0, 16, v1], "texture": "#net_mesh"},
            "south": {"uv": [0, v0, 16, v1], "texture": "#net_mesh"},
        }
        ew_faces = {
            "east": {"uv": [0, v0, 16, v1], "texture": "#net_mesh"},
            "west": {"uv": [0, v0, 16, v1], "texture": "#net_mesh"},
        }
        oct_defs = [
            ("n",  [rcx - w_band, y0, rcz - r_band - 0.02], [rcx + w_band, y1, rcz - r_band + 0.02], ns_faces, False),
            ("s",  [rcx - w_band, y0, rcz + r_band - 0.02], [rcx + w_band, y1, rcz + r_band + 0.02], ns_faces, False),
            ("e",  [rcx + r_band - 0.02, y0, rcz - w_band], [rcx + r_band + 0.02, y1, rcz + w_band], ew_faces, False),
            ("w",  [rcx - r_band - 0.02, y0, rcz - w_band], [rcx - r_band + 0.02, y1, rcz + w_band], ew_faces, False),
            ("ne", [rcx - w_band, y0, rcz - r_band - 0.02], [rcx + w_band, y1, rcz - r_band + 0.02], ns_faces, True),
            ("sw", [rcx - w_band, y0, rcz + r_band - 0.02], [rcx + w_band, y1, rcz + r_band + 0.02], ns_faces, True),
            ("se", [rcx + r_band - 0.02, y0, rcz - w_band], [rcx + r_band + 0.02, y1, rcz + w_band], ew_faces, True),
            ("nw", [rcx - r_band - 0.02, y0, rcz - w_band], [rcx - r_band + 0.02, y1, rcz + w_band], ew_faces, True),
        ]
        for side_id, cfrom, cto, cfaces, rot45 in oct_defs:
            cel = {
                "name": f"net_t{tier_idx}_{side_id}",
                "from": [round(v, 4) for v in cfrom],
                "to":   [round(v, 4) for v in cto],
                "faces": cfaces,
            }
            if rot45:
                cel["rotation"] = {"origin": [rcx, round((y0 + y1) * 0.5, 4), rcz], "axis": "y", "angle": 45.0}
            elements.append(cel)

    return {
        "credit": "BosketBall 3D Hoop & Seamless Braided Net Assembly",
        "ambientocclusion": False,
        "gui_light": "front",
        "textures": {
            "glass": "bosketball:item/hoop_glass",
            "glass_back": "bosketball:item/hoop_glass_back",
            "parts": "bosketball:item/hoop_parts",
            "net_mesh": "bosketball:item/hoop_net_mesh",
            "particle": "bosketball:item/hoop_parts",
        },
        "elements": elements,
        "display": {
            "gui": {"rotation": [18, 152, 0], "translation": [0, -1.8, 0], "scale": [0.46, 0.46, 0.46]},
            "ground": {"translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "thirdperson_righthand": {"rotation": [65, 35, 0], "translation": [0, 2, 0], "scale": [0.35, 0.35, 0.35]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 1.5, 0], "scale": [0.35, 0.35, 0.35]},
        },
    }


# ============================================================================
# 4. EXTRA 3D PROPS: MVP TROPHY, REFEREE WHISTLE, AIR SNEAKERS, TACTICS PLAYBOOK
# ============================================================================
def generate_prop_models_and_textures(item_tex_dir: Path, model_dir: Path):
    # Shared prop palette sheet (64x64)
    pal = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(pal)
    # [0..15, 0..15] 24K Gold metallic gradient
    for y in range(16):
        for x in range(16):
            g = 188 + int(45 * math.sin((x + y) * 0.4))
            pal.putpixel((x, y), (255, min(255, g), 42, 255))
    # [16..31, 0..15] Dark Obsidian Marble pedestal
    for y in range(16):
        for x in range(16, 32):
            v = 24 + ((x * 7 + y * 13) % 14)
            pal.putpixel((x, y), (v, v + 4, v + 12, 255))
    # [32..47, 0..15] Mirror Chrome Silver (whistle / clip)
    for y in range(16):
        for x in range(32, 48):
            c = 195 + int(42 * math.cos((x - y) * 0.45))
            pal.putpixel((x, y), (c, c + 4, min(255, c + 12), 255))
    # [48..63, 0..15] Crimson Red accent (lanyard / sneaker upper)
    d.rectangle([48, 0, 63, 15], fill=(224, 38, 52, 255))
    # [0..15, 16..31] Crisp White leather (sneaker midsole / laces)
    d.rectangle([0, 16, 15, 31], fill=(244, 246, 252, 255))
    # [16..63, 16..63] Coach's Hardwood Playbook Diagram!
    d.rectangle([16, 16, 63, 63], fill=(208, 154, 88, 255))
    d.rectangle([18, 18, 61, 61], outline=(248, 250, 255, 255), width=2)
    d.arc([24, 18, 55, 48], start=0, end=180, fill=(248, 250, 255, 255), width=2)
    d.ellipse([36, 21, 43, 28], outline=(255, 92, 28, 255), width=2)
    draw_pixel_text(pal, "X", 26, 38, color=(255, 62, 62, 255), scale=1)
    draw_pixel_text(pal, "O", 46, 34, color=(62, 210, 255, 255), scale=1)
    pal.save(item_tex_dir / "props_atlas.png")

    def box(name, frm, to, uv, rot=None, emit=0):
        el = {
            "name": name,
            "from": frm,
            "to": to,
            "faces": {f: {"uv": uv, "texture": "#0"} for f in ("north", "south", "east", "west", "up", "down")},
        }
        if rot:
            el["rotation"] = rot
        if emit:
            el["light_emission"] = emit
        return el

    uv_gold = [0.5, 0.5, 3.5, 3.5]
    uv_marble = [4.5, 0.5, 7.5, 3.5]
    uv_chrome = [8.5, 0.5, 11.5, 3.5]
    uv_red = [12.5, 0.5, 15.5, 3.5]
    uv_white = [0.5, 4.5, 3.5, 7.5]

    # 1. 3D MVP Championship Trophy (Larry O'Brien inspired, crowned with a 3D Golden Cubic Basketball!)
    trophy = {
        "credit": "BosketBall 3D Championship Trophy (26.2)",
        "textures": {"0": "bosketball:item/props_atlas", "ball": "bosketball:item/ball_two_tone", "particle": "bosketball:item/props_atlas"},
        "elements": [
            box("base_tier_1", [4.0, 0.0, 4.0], [12.0, 2.0, 12.0], uv_marble),
            box("base_tier_2", [5.0, 2.0, 5.0], [11.0, 3.4, 11.0], uv_marble),
            box("gold_plaque", [5.8, 0.5, 3.8], [10.2, 1.6, 4.1], uv_gold, emit=10),
            box("stem_left", [6.6, 3.0, 7.0], [7.8, 10.6, 9.0], uv_gold, rot={"origin": [7.2, 3.4, 8.0], "axis": "z", "angle": -22.5}, emit=10),
            box("stem_right", [8.2, 3.0, 7.0], [9.4, 10.6, 9.0], uv_gold, rot={"origin": [8.8, 3.4, 8.0], "axis": "z", "angle": 22.5}, emit=10),
            box("crown_ring", [5.8, 9.8, 5.8], [10.2, 10.6, 10.2], uv_gold, emit=12),
            {
                "name": "trophy_cube_ball",
                "from": [5.4, 10.6, 5.4],
                "to": [10.6, 15.8, 10.6],
                "light_emission": 14,
                "faces": {f: {"uv": [2.75, 2.75, 13.25, 13.25], "texture": "#ball"} for f in ("north", "south", "east", "west", "up", "down")},
            },
        ],
        "display": {
            "gui": {"rotation": [22, 145, 0], "translation": [0, -0.5, 0], "scale": [0.92, 0.92, 0.92]},
            "thirdperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3.0, 0], "scale": [0.75, 0.75, 0.75]},
            "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [1.2, 2.2, 0], "scale": [0.68, 0.68, 0.68]},
            "head": {"translation": [0, 14.2, 0], "scale": [1.1, 1.1, 1.1]},
            "fixed": {"scale": [1.0, 1.0, 1.0]},
        },
    }
    (model_dir / "trophy_mvp.json").write_text(json.dumps(trophy, indent=2))

    # 2. 3D Referee Whistle
    whistle = {
        "credit": "BosketBall 3D Referee Whistle (26.2)",
        "textures": {"0": "bosketball:item/props_atlas", "particle": "bosketball:item/props_atlas"},
        "elements": [
            box("chamber", [6.0, 5.5, 6.0], [10.0, 9.5, 10.0], uv_chrome),
            box("mouthpiece", [6.7, 6.8, 2.8], [9.3, 8.6, 6.2], uv_chrome),
            box("lanyard_ring", [7.3, 6.8, 10.0], [8.7, 8.2, 11.2], uv_gold),
            box("lanyard_left", [6.6, 7.2, 11.0], [7.2, 7.8, 15.2], uv_red, rot={"origin": [7.0, 7.5, 11.0], "axis": "y", "angle": -22.5}),
            box("lanyard_right", [8.8, 7.2, 11.0], [9.4, 7.8, 15.2], uv_red, rot={"origin": [9.0, 7.5, 11.0], "axis": "y", "angle": 22.5}),
        ],
        "display": {
            "gui": {"rotation": [28, 142, 0], "scale": [1.15, 1.15, 1.15]},
            "thirdperson_righthand": {"rotation": [45, 0, 0], "translation": [0, 2.5, 1.0], "scale": [0.65, 0.65, 0.65]},
            "firstperson_righthand": {"rotation": [12, 165, 0], "translation": [1.5, 3.0, 0], "scale": [0.7, 0.7, 0.7]},
            "fixed": {"scale": [1.0, 1.0, 1.0]},
        },
    }
    (model_dir / "referee_whistle.json").write_text(json.dumps(whistle, indent=2))

    # 3. 3D High-Top Air Bosket Sneakers
    sneakers = {
        "credit": "BosketBall 3D Air Bosket Sneakers (26.2)",
        "textures": {"0": "bosketball:item/props_atlas", "particle": "bosketball:item/props_atlas"},
        "elements": [
            # Left shoe
            box("l_outsole", [2.6, 2.0, 3.5], [7.2, 3.1, 12.5], uv_red),
            box("l_midsole", [2.7, 3.1, 3.6], [7.1, 4.3, 12.4], uv_white),
            box("l_upper",   [2.9, 4.3, 5.2], [6.9, 8.8, 12.1], uv_red),
            box("l_toebox",  [2.9, 4.3, 3.7], [6.9, 6.2, 6.2], uv_white),
            box("l_collar",  [2.8, 8.2, 7.6], [7.0, 9.8, 12.2], uv_marble),
            box("l_swoosh",  [2.75, 5.6, 6.2], [7.05, 6.6, 11.2], uv_gold),
            # Right shoe (angled stylishly for display!)
            box("r_outsole", [8.8, 2.0, 3.5], [13.4, 3.1, 12.5], uv_red, rot={"origin": [11.1, 2.5, 8.0], "axis": "y", "angle": -22.5}),
            box("r_midsole", [8.9, 3.1, 3.6], [13.3, 4.3, 12.4], uv_white, rot={"origin": [11.1, 2.5, 8.0], "axis": "y", "angle": -22.5}),
            box("r_upper",   [9.1, 4.3, 5.2], [13.1, 8.8, 12.1], uv_red, rot={"origin": [11.1, 2.5, 8.0], "axis": "y", "angle": -22.5}),
            box("r_toebox",  [9.1, 4.3, 3.7], [13.1, 6.2, 6.2], uv_white, rot={"origin": [11.1, 2.5, 8.0], "axis": "y", "angle": -22.5}),
            box("r_swoosh",  [8.95, 5.6, 6.2], [13.25, 6.6, 11.2], uv_gold, rot={"origin": [11.1, 2.5, 8.0], "axis": "y", "angle": -22.5}),
        ],
        "display": {
            "gui": {"rotation": [28, 145, 0], "translation": [0, 0.5, 0], "scale": [0.95, 0.95, 0.95]},
            "thirdperson_righthand": {"rotation": [45, 35, 0], "translation": [0, 2.5, 0], "scale": [0.65, 0.65, 0.65]},
            "firstperson_righthand": {"rotation": [12, 145, 0], "translation": [1.2, 2.5, 0], "scale": [0.65, 0.65, 0.65]},
            "fixed": {"scale": [0.95, 0.95, 0.95]},
        },
    }
    (model_dir / "sneakers_air.json").write_text(json.dumps(sneakers, indent=2))

    # 4. 3D Coach's Tactics Playbook Clipboard
    playbook = {
        "credit": "BosketBall 26.2 3D Coach Tactics Playbook",
        "textures": {"0": "bosketball:item/props_atlas", "particle": "bosketball:item/props_atlas"},
        "elements": [
            {
                "name": "board",
                "from": [3.0, 1.5, 7.3],
                "to": [13.0, 14.5, 8.4],
                "faces": {
                    "north": {"uv": [4, 4, 16, 16], "texture": "#0"},
                    "south": {"uv": [4, 4, 16, 16], "texture": "#0"},
                    "east":  {"uv": [4.5, 0.5, 5.5, 3.5], "texture": "#0"},
                    "west":  {"uv": [4.5, 0.5, 5.5, 3.5], "texture": "#0"},
                    "up":    {"uv": [4.5, 0.5, 5.5, 3.5], "texture": "#0"},
                    "down":  {"uv": [4.5, 0.5, 5.5, 3.5], "texture": "#0"},
                },
            },
            box("brass_clip", [6.0, 13.6, 6.9], [10.0, 15.2, 8.8], uv_chrome),
        ],
        "display": {
            "gui": {"rotation": [16, 162, 0], "scale": [0.96, 0.96, 0.96]},
            "thirdperson_righthand": {"rotation": [55, 180, 0], "translation": [0, 2.5, 1.0], "scale": [0.72, 0.72, 0.72]},
            "firstperson_righthand": {"rotation": [-18, 175, 0], "translation": [1.0, 2.2, 0], "scale": [0.76, 0.76, 0.76]},
            "fixed": {"scale": [1.0, 1.0, 1.0]},
        },
    }
    (model_dir / "tactics_playbook.json").write_text(json.dumps(playbook, indent=2))

    # 5. 3D Map Architect Laser Caliper Wand (/bb map wand)
    court_wand = {
        "credit": "BosketBall 26.2 3D Map Architect Laser Wand",
        "textures": {"0": "bosketball:item/props_atlas", "ball": "bosketball:item/ball_glow", "particle": "bosketball:item/props_atlas"},
        "elements": [
            box("grip_handle", [7.1, 1.0, 7.1], [8.9, 6.8, 8.9], uv_marble),
            box("gold_collar_low", [6.8, 2.0, 6.8], [9.2, 2.7, 9.2], uv_gold, emit=10),
            box("gold_collar_high", [6.7, 6.6, 6.7], [9.3, 7.4, 9.3], uv_gold, emit=12),
            box("chrome_shaft", [7.35, 7.4, 7.35], [8.65, 11.4, 8.65], uv_chrome, emit=8),
            box("caliper_prong_l", [5.8, 10.6, 7.5], [7.3, 14.2, 8.5], uv_chrome, rot={"origin": [7.2, 10.8, 8.0], "axis": "z", "angle": 18.0}, emit=10),
            box("caliper_prong_r", [8.7, 10.6, 7.5], [10.2, 14.2, 8.5], uv_chrome, rot={"origin": [8.8, 10.8, 8.0], "axis": "z", "angle": -18.0}, emit=10),
            {
                "name": "laser_holo_cube",
                "from": [6.7, 11.8, 6.7],
                "to": [9.3, 14.4, 9.3],
                "light_emission": 15,
                "rotation": {"origin": [8.0, 13.1, 8.0], "axis": "y", "angle": 45.0},
                "faces": {f: {"uv": [2.0, 2.0, 14.0, 14.0], "texture": "#ball"} for f in ("north", "south", "east", "west", "up", "down")},
            },
        ],
        "display": {
            "gui": {"rotation": [24, 140, 20], "translation": [0, -0.4, 0], "scale": [0.96, 0.96, 0.96]},
            "thirdperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2.8, 0.4], "scale": [0.78, 0.78, 0.78]},
            "firstperson_righthand": {"rotation": [-12, 140, 8], "translation": [1.1, 2.6, 0], "scale": [0.72, 0.72, 0.72]},
            "fixed": {"scale": [1.0, 1.0, 1.0]},
        },
    }
    (model_dir / "court_wand.json").write_text(json.dumps(court_wand, indent=2))


# ============================================================================
# 5. CUSTOM HUD BANNERS (IMAGE-1 STYLE!), RANK PILLS, STAT BADGES & SHOT METER
# ============================================================================
def pin_width(img: Image.Image):
    """Places an invisible alpha=3 pixel at top-right so Minecraft's bitmap font loader preserves exact pixel width!"""
    r, g, b, a = img.getpixel((img.width - 1, 0))
    if a == 0:
        img.putpixel((img.width - 1, 0), (255, 255, 255, 3))
    return img


def make_beveled_banner(w: int, h: int, bg_top, bg_bot, border_col, accent_col, icon_mode="ball") -> Image.Image:
    """
    Creates a studio-quality dark translucent broadcast banner modeled after the user's screenshot (image-1.png):
    - Rounded 2px chamfered corners
    - Smooth vertical dark-slate glass gradient (#121824 -> #1B2538)
    - Crisp 1px inner highlight + colored top accent strip + left emblem slot!
    """
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        r = int(bg_top[0] * (1 - t) + bg_bot[0] * t)
        g = int(bg_top[1] * (1 - t) + bg_bot[1] * t)
        b = int(bg_top[2] * (1 - t) + bg_bot[2] * t)
        for x in range(w):
            # Chamfer corners (2px radius)
            dx = min(x, w - 1 - x)
            dy = min(y, h - 1 - y)
            if dx + dy < 2:
                continue
            if dx == 0 or dy == 0 or (dx == 1 and dy == 1):
                px[x, y] = border_col
            elif y == 1 and 3 <= x <= w - 4:
                px[x, y] = accent_col
            else:
                px[x, y] = (r, g, b, 232)

    if icon_mode != "none":
        # Left emblem badge box (18x18 centered vertically)
        eb_s = h - 6
        ex, ey = 4, 3
        draw = ImageDraw.Draw(img)
        draw.rectangle([ex, ey, ex + eb_s - 1, ey + eb_s - 1], fill=(12, 16, 24, 245), outline=accent_col)
        # A real ball in the emblem box, at the size the banner actually uses.
        mini_skin = "fire" if icon_mode == "dunk" else ("glow" if icon_mode == "three" else "classic")
        ball_mini = ball_model.render_ball(
            ball_model.skin_model(mini_skin), ball_model.skin_textures(mini_skin),
            size=eb_s - 4, yaw=34, pitch=18, ss=4,
        )
        img.alpha_composite(ball_mini, (ex + 2, ey + 2))

    return pin_width(img)


def make_scorebug_banner() -> Image.Image:
    """
    228x20 Live ESPN/NBA Broadcast Scorebug Background:
    [ IGNIS RED WING (58px) | SCORE (24px) | CENTER CLOCK/SHOT (64px) | SCORE (24px) | FROST BLUE WING (58px) ]
    """
    w, h = 228, 20
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        for x in range(w):
            dx, dy = min(x, w - 1 - x), min(y, h - 1 - y)
            if dx + dy < 2:
                continue
            if dx == 0 or dy == 0 or (dx == 1 and dy == 1):
                px[x, y] = (46, 58, 82, 248)
                continue
            if x < 56:  # Home Team Ignis Crimson Wing
                px[x, y] = (int(165 - 45 * t), int(28 - 10 * t), int(38 - 10 * t), 236)
            elif x < 82:  # Home Score dark well
                px[x, y] = (14, 18, 28, 244)
            elif x < 146:  # Center Clock & Shot-Clock capsule
                px[x, y] = (24, 31, 46, 244)
                if y == 1:
                    px[x, y] = (255, 196, 46, 255)
            elif x < 172:  # Away Score dark well
                px[x, y] = (14, 18, 28, 244)
            else:  # Away Team Frost Cobalt/Cyan Wing
                px[x, y] = (int(22 - 8 * t), int(108 - 32 * t), int(188 - 42 * t), 236)
            if x in (56, 82, 146, 172):
                px[x, y] = (54, 68, 96, 255)
    return pin_width(img)


def make_logo_emblem() -> Image.Image:
    """156x22 Stadium Header Crest for TabList & Match Announcements."""
    w, h = 156, 22
    img = make_beveled_banner(w, h, (20, 26, 42), (10, 14, 24), (255, 142, 36, 255), (255, 210, 68, 255), icon_mode="ball")
    draw_pixel_text(
        img,
        "BOSKETBALL",
        x=32,
        y=6,
        color=(255, 222, 92, 255),
        shadow=(165, 58, 14, 255),
        scale=2,
    )
    return pin_width(img)


def make_rank_pill(label: str, top_col, bot_col, border_col, text_col=(255, 255, 255, 255)) -> Image.Image:
    """Creates a crisp 9px-tall rounded pixel-art rank badge pill for Chat & TabList (exact image-1.png aesthetic!)."""
    tw = measure_pixel_text(label, scale=1)
    w = tw + 8
    h = 9
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        r = int(top_col[0] * (1 - t) + bot_col[0] * t)
        g = int(top_col[1] * (1 - t) + bot_col[1] * t)
        b = int(top_col[2] * (1 - t) + bot_col[2] * t)
        for x in range(w):
            dx, dy = min(x, w - 1 - x), min(y, h - 1 - y)
            if dx == 0 and dy == 0:
                continue
            if dx == 0 or dy == 0:
                px[x, y] = border_col
            else:
                px[x, y] = (r, g, b, 255)
    draw_pixel_text(img, label, x=4, y=2, color=text_col, shadow=(15, 18, 26, 190), scale=1)
    return pin_width(img)


def make_stat_icon(kind: str) -> Image.Image:
    """9x9 crisp pixel-art chat stat icons matching image-1.png's Trophy, Star, Basketball, Fire, Crown, Shield, Bolt!"""
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    patterns = {
        "trophy": (
            [
                "..YYYYY..",
                ".GGGGGGG.",
                "GGYYYYYGG",
                "G.YYYYY.G",
                "..GGGGG..",
                "...GGG...",
                "...GGG...",
                "..YYYYY..",
                ".BBBBBBB.",
            ],
            {"Y": (255, 232, 82, 255), "G": (235, 162, 24, 255), "B": (148, 92, 22, 255)},
        ),
        "star": (
            [
                "....P....",
                "...PPP...",
                ".PPPPPPP.",
                "PPLLLLPPP",
                "..PLLLP..",
                "..PPLPP..",
                ".PP...PP.",
                ".P.....P.",
                ".........",
            ],
            {"P": (186, 92, 255, 255), "L": (244, 205, 255, 255)},
        ),
        "ball": (
            [
                "SSSSSSSSS",
                "SHOOOSOOS",
                "SOSOOSOOS",
                "SOOSOSOSS",
                "SSSSSSSSS",
                "SOOSOSOSS",
                "SOSOOSOOS",
                "SOOOOSOOS",
                "SSSSSSSSS",
            ],
            {"O": (244, 112, 28, 255), "H": (255, 168, 76, 255), "S": (34, 26, 24, 255)},
        ),
        "fire": (
            [
                "....R....",
                "...RRO...",
                "..ROYO...",
                ".ROYYOR..",
                "ROYYYYOR.",
                "ROYYWYOR.",
                ".OYYYYO..",
                "..OOOO...",
                ".........",
            ],
            {"R": (245, 58, 32, 255), "O": (255, 138, 24, 255), "Y": (255, 224, 62, 255), "W": (255, 255, 235, 255)},
        ),
        "crown": (
            [
                ".........",
                "Y...Y...Y",
                "YY.YYY.YY",
                "YYYYYYYYY",
                "YCRYYYGYC",
                "YYYYYYYYY",
                ".GGGGGGG.",
                ".........",
                ".........",
            ],
            {"Y": (255, 222, 58, 255), "G": (214, 142, 18, 255), "R": (255, 58, 76, 255), "C": (62, 232, 255, 255)},
        ),
        "shield": (
            [
                ".CCCCCCC.",
                "CBBBBBBBC",
                "CBWWWWBWC",
                "CBBBBBBBC",
                ".CBBBBBC.",
                ".CBBBBBC.",
                "..CBBBC..",
                "...CBC...",
                "....C....",
            ],
            {"C": (110, 235, 255, 255), "B": (32, 128, 224, 255), "W": (235, 250, 255, 255)},
        ),
        "bolt": (
            [
                "....YYYY.",
                "...YYYY..",
                "..YYYY...",
                ".YYYYYY..",
                "...YYYY..",
                "..YYYY...",
                ".YYY.....",
                "YY.......",
                ".........",
            ],
            {"Y": (255, 238, 68, 255)},
        ),
    }
    rows, cmap = patterns[kind]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in cmap:
                img.putpixel((x, y), cmap[ch])
    return pin_width(img)


def generate_shot_meter_sheet(font_tex_dir: Path):
    """Generates a 7-cell x 8pxx10px shot-meter sprite strip for ActionBar NBA 2K Green Release!"""
    cell_w, cell_h = 8, 10
    sheet = Image.new("RGBA", (cell_w * 7, cell_h), (0, 0, 0, 0))
    colors = [
        ("cap_left",   (36, 44, 62, 255), (82, 98, 134, 255)),
        ("empty",      (22, 28, 40, 235), (52, 64, 88, 255)),
        ("fill_warm",  (255, 178, 38, 255), (255, 224, 98, 255)),
        ("green_zone", (24, 92, 52, 245), (62, 242, 122, 255)),
        ("green_hit",  (48, 250, 118, 255), (205, 255, 220, 255)),
        ("late_red",   (238, 52, 58, 255), (255, 128, 128, 255)),
        ("cap_right",  (36, 44, 62, 255), (82, 98, 134, 255)),
    ]
    d = ImageDraw.Draw(sheet)
    for idx, (_, fill_c, border_c) in enumerate(colors):
        ox = idx * cell_w
        d.rectangle([ox, 1, ox + cell_w - 2, cell_h - 2], fill=fill_c, outline=border_c)
        sheet.putpixel((ox + cell_w - 1, 0), (255, 255, 255, 3))
    sheet.save(font_tex_dir / "shot_meter.png")


# ============================================================================
# 6. PROCEDURAL REALISTIC BASKETBALL 3D MONO .OGG VORBIS SOUND SYNTHESIZER
# ============================================================================
def synth_sounds(sounds_dir: Path):
    if imageio_ffmpeg is None:
        existing = list(sounds_dir.glob("*.ogg"))
        if len(existing) >= 14:
            print(f"Preserving {len(existing)} existing synthesized .ogg sound assets.")
            return
        raise RuntimeError("imageio_ffmpeg required to synthesize missing .ogg sounds")
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    sr = 44100
    rng = np.random.default_rng(2602)

    def save_ogg(name: str, samples: np.ndarray):
        peak = np.max(np.abs(samples)) + 1e-8
        pcm = np.clip(samples / peak * 0.88, -1.0, 1.0)
        pcm16 = (pcm * 32767.0).astype(np.int16)
        wav_tmp = Path(f"/tmp/{name}.wav")
        with wave.open(str(wav_tmp), "wb") as wf:
            wf.setnchannels(1)  # Mono is REQUIRED for 3D spatial attenuation in Minecraft OpenAL!
            wf.setsampwidth(2)
            wf.setframerate(sr)
            wf.writeframes(pcm16.tobytes())
        out_ogg = sounds_dir / f"{name}.ogg"
        subprocess.run(
            [ffmpeg_exe, "-y", "-loglevel", "error", "-i", str(wav_tmp), "-c:a", "libvorbis", "-q:a", "5", str(out_ogg)],
            check=True,
        )
        wav_tmp.unlink(missing_ok=True)

    # 1. Hardwood Basketball Bounce (rubber air-cavity pitch drop + crisp wood slap)
    t = np.linspace(0, 0.28, int(sr * 0.28), endpoint=False)
    freq_env = 118.0 + 185.0 * np.exp(-t * 36.0)
    phase = 2 * np.pi * np.cumsum(freq_env) / sr
    ring = np.sin(phase) * np.exp(-t * 14.0) + 0.42 * np.sin(2.04 * phase) * np.exp(-t * 22.0)
    wood_slap = rng.normal(0, 1, len(t)) * np.exp(-t * 110.0) * 0.65
    save_ogg("bounce", ring + wood_slap)

    # 2. Pure Nylon Net Swish (band-swept airy nylon friction + soft cord whip)
    t = np.linspace(0, 0.38, int(sr * 0.38), endpoint=False)
    noise = rng.normal(0, 1, len(t))
    # Smooth moving-average high-mid texture + amplitude swoosh envelope
    env = np.sin(np.pi * np.clip(t / 0.34, 0, 1)) ** 1.6 * np.exp(-t * 3.2)
    whip = np.sin(2 * np.pi * (420 + 680 * t) * t) * np.exp(-((t - 0.14) ** 2) / 0.0025) * 0.25
    diff_noise = np.diff(noise, prepend=0)
    save_ogg("swish", diff_noise * env + whip)

    # 3. Iron Rim Clank (inharmonic steel ring modes + impact transient)
    t = np.linspace(0, 0.42, int(sr * 0.42), endpoint=False)
    clank = (
        np.sin(2 * np.pi * 312 * t) * np.exp(-t * 13.0)
        + 0.7 * np.sin(2 * np.pi * 587 * t) * np.exp(-t * 18.0)
        + 0.45 * np.sin(2 * np.pi * 1142 * t) * np.exp(-t * 28.0)
        + rng.normal(0, 0.7, len(t)) * np.exp(-t * 95.0)
    )
    save_ogg("rim", clank)

    # 4. Tempered Glass Backboard Thud
    t = np.linspace(0, 0.30, int(sr * 0.30), endpoint=False)
    thud = (
        np.sin(2 * np.pi * (82 + 90 * np.exp(-t * 45)) * t) * np.exp(-t * 16.0)
        + 0.35 * np.sin(2 * np.pi * 245 * t) * np.exp(-t * 26.0)
        + rng.normal(0, 0.5, len(t)) * np.exp(-t * 80.0)
    )
    save_ogg("backboard", thud)

    # 5. Thunderous Slam Dunk + Rim Flex Boom!
    t = np.linspace(0, 0.75, int(sr * 0.75), endpoint=False)
    boom = (
        1.2 * np.sin(2 * np.pi * (58 + 75 * np.exp(-t * 22)) * t) * np.exp(-t * 5.2)
        + 0.65 * np.sin(2 * np.pi * 286 * t) * np.exp(-t * 11.0)
        + 0.5 * rng.normal(0, 1, len(t)) * np.exp(-t * 9.5)
    )
    save_ogg("dunk", boom)

    # 6. Authentic NBA Arena Shot-Clock / Quarter Buzzer Horn!
    t = np.linspace(0, 1.15, int(sr * 1.15), endpoint=False)
    horn = np.zeros_like(t)
    for f0 in (146.8, 185.0, 220.0, 293.6):
        saw = 2.0 * ((t * f0) % 1.0) - 1.0
        horn += saw
    gate = np.clip(np.minimum(t / 0.02, (1.15 - t) / 0.06), 0, 1)
    save_ogg("buzzer", np.tanh(horn * 0.9) * gate)

    # 7. Referee Pea-Whistle Trill
    t = np.linspace(0, 0.52, int(sr * 0.52), endpoint=False)
    pea = 0.65 + 0.35 * np.sin(2 * np.pi * 38.0 * t)
    whistle = pea * (np.sin(2 * np.pi * 2840 * t) + 0.35 * np.sin(2 * np.pi * 2915 * t))
    w_env = np.clip(np.minimum(t / 0.015, (0.52 - t) / 0.05), 0, 1)
    save_ogg("whistle", whistle * w_env)

    # 8. NBA 2K Green-Release Sparkle Chime!
    t = np.linspace(0, 0.48, int(sr * 0.48), endpoint=False)
    chime = (
        np.sin(2 * np.pi * 1046.5 * t) * np.exp(-t * 8.0)
        + 0.8 * np.sin(2 * np.pi * 1318.5 * np.maximum(0, t - 0.045)) * (t > 0.045) * np.exp(-np.maximum(0, t - 0.045) * 7.5)
        + 0.9 * np.sin(2 * np.pi * 1568.0 * np.maximum(0, t - 0.09)) * (t > 0.09) * np.exp(-np.maximum(0, t - 0.09) * 6.5)
    )
    save_ogg("green_release", chime)

    # 10. Hardwood Sneaker Squeak!
    t = np.linspace(0, 0.22, int(sr * 0.22), endpoint=False)
    sq_freq = 2150.0 + 480.0 * np.sin(2 * np.pi * 16.0 * t)
    sq = np.sin(2 * np.pi * np.cumsum(sq_freq) / sr) * np.sin(np.pi * t / 0.22) ** 1.8
    save_ogg("sneaker_squeak", sq)

    # 11. Stadium Crowd Cheer Roar!
    t = np.linspace(0, 1.35, int(sr * 1.35), endpoint=False)
    crowd_env = np.clip(t / 0.14, 0, 1) * np.exp(-t * 1.25)
    raw_c = rng.normal(0, 1, len(t))
    smooth_c = np.convolve(raw_c, np.ones(18) / 18.0, mode="same")
    save_ogg("crowd_cheer", smooth_c * crowd_env)

    # 12. Crisp UI Menu Click & Block Swat!
    t = np.linspace(0, 0.08, int(sr * 0.08), endpoint=False)
    click = np.sin(2 * np.pi * 1420.0 * t) * np.exp(-t * 68.0)
    save_ogg("ui_click", click)

    t = np.linspace(0, 0.26, int(sr * 0.26), endpoint=False)
    swat = (np.sin(2 * np.pi * (145 + 160 * np.exp(-t * 50)) * t) + rng.normal(0, 0.9, len(t))) * np.exp(-t * 20.0)
    save_ogg("block_swat", swat)

    # 13. Silent clip for smooth Consumable Jump-Shot Charge (zero eating noise!)
    silent = np.zeros(int(sr * 0.05), dtype=np.float64)
    wav_empty = Path("/tmp/empty.wav")
    with wave.open(str(wav_empty), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(np.zeros(int(sr * 0.05), dtype=np.int16).tobytes())
    subprocess.run(
        [ffmpeg_exe, "-y", "-loglevel", "error", "-i", str(wav_empty), "-c:a", "libvorbis", "-q:a", "3", str(sounds_dir / "empty.ogg")],
        check=True,
    )
    wav_empty.unlink(missing_ok=True)


# ============================================================================
# 7. BLOCKBENCH (.bbmodel) PROJECT EXPORT + CONTACT SHEET PREVIEWS
# ============================================================================
def export_blockbench_bbmodel(bb_dir: Path, model_json: dict, tex_dir: Path):
    """Exports the ball as a Blockbench 4.x project so a modeller can open and edit it.

    One cube, one atlas: whoever opens this sees the actual construction — the six face
    faces of the element pointing at the six tiles of `ball_classic.png` — not a bake of it.
    """
    import base64

    bb_elements = []
    for i, el in enumerate(model_json["elements"]):
        bb_faces = {
            face: {"uv": data["uv"], "texture": 0} for face, data in el["faces"].items()
        }
        element = {
            "name": el["name"],
            "type": "cube",
            "from": el["from"],
            "to": el["to"],
            "origin": [8, 8, 8],
            "faces": bb_faces,
            "uuid": f"00000000-0000-4000-8000-{i:012d}",
        }
        if el.get("light_emission"):
            element["light_emission"] = el["light_emission"]
        bb_elements.append(element)

    png_bytes = (tex_dir / "ball_classic.png").read_bytes()
    bb_textures = [{
        "name": "ball_classic.png",
        "id": "0",
        "source": "data:image/png;base64," + base64.b64encode(png_bytes).decode("ascii"),
        "uv_width": ball_model.ATLAS_W,
        "uv_height": ball_model.ATLAS_H,
    }]

    bb = {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": "basketball_classic",
        # Blockbench's project resolution is the 0..16 UV grid, not the texture: the atlas
        # size belongs on the texture entry (`uv_width`/`uv_height`), which is what makes the
        # six tiles show up at their real size when a modeller opens the file.
        "resolution": {"width": 16, "height": 16},
        "elements": bb_elements,
        "textures": bb_textures,
        "display": model_json["display"],
    }
    (bb_dir / "basketball_26_2.bbmodel").write_text(json.dumps(bb, indent=2))



def _provider_texture_exists(file_ref) -> bool:
    """True when a font provider's `namespace:font/name.png` resolves to a real file."""
    if not isinstance(file_ref, str) or ":" not in file_ref:
        return True  # not a texture reference (e.g. a space provider): leave it alone
    namespace, _, path = file_ref.partition(":")
    candidate = RP_DIR / "assets" / namespace / "textures" / path
    if candidate.is_file():
        return True
    # Providers may omit the extension when the namespace default applies.
    return candidate.with_suffix(".png").is_file()


def carry_over_bound_providers(providers, default_json):
    """Appends providers already bound in ``default_json`` but missing from ``providers``.

    Several round-specific generators (nav icons, tooltip rarity, tab frames, nameplates) bind
    their glyphs through their own upsert. Regenerating the font here must not unbind them.
    """
    if not default_json.is_file():
        return providers
    try:
        existing = json.loads(default_json.read_text(encoding="utf-8-sig")).get("providers", [])
    except (ValueError, OSError, UnicodeDecodeError):
        return providers
    seen = {(p.get("file"), str(p.get("chars"))) for p in providers}
    kept = 0
    dropped = 0
    for provider in existing:
        key = (provider.get("file"), str(provider.get("chars")))
        if key in seen:
            continue
        # A provider is only worth carrying over if its texture still exists. Retiring a skin
        # deletes its preview strips, but the old binding survived in default.json and was
        # copied straight back into both font files: a missing glyph in game, and a
        # "missing texture reference" failure in tools/check_pack_models.py.
        if not _provider_texture_exists(provider.get("file")):
            dropped += 1
            continue
        providers.append(provider)
        seen.add(key)
        kept += 1
    if dropped:
        print(f"Dropped {dropped} bitmap providers whose texture no longer exists")
    if kept:
        print(f"Kept {kept} bitmap providers bound by the round-specific generators")
    return providers


def main():
    print("Building BosketBall 26.2 Resource Pack (pack format 88)...")
    item_def_dir = RP_DIR / "assets" / "bosketball" / "items"
    model_dir = RP_DIR / "assets" / "bosketball" / "models" / "item"
    item_tex_dir = RP_DIR / "assets" / "bosketball" / "textures" / "item"
    font_tex_dir = RP_DIR / "assets" / "bosketball" / "textures" / "font"
    sounds_dir = RP_DIR / "assets" / "bosketball" / "sounds"
    lang_dir = RP_DIR / "assets" / "bosketball" / "lang"
    mc_font_dir = RP_DIR / "assets" / "minecraft" / "font"
    mc_bossbar_dir = RP_DIR / "assets" / "minecraft" / "textures" / "gui" / "sprites" / "boss_bar"
    bb_dir = RP_DIR / "blockbench"

    for d in (item_def_dir, model_dir, item_tex_dir, font_tex_dir, sounds_dir, lang_dir, mc_font_dir, mc_bossbar_dir, bb_dir, RELEASE_DIR, PREVIEW_DIR):
        d.mkdir(parents=True, exist_ok=True)

    # 1. The ball system: eight skins, one silhouette. Models, palette textures and their
    # `.mcmeta` come straight from tools/ball_model.py so the silhouette constants live in
    # exactly one place.
    # Prune before writing. A retired skin leaves six 128 px cube faces per skin on disk
    # (and 20 preview strips); nothing reads them, but every client downloads them and the
    # pack grows past its size budget. This script owns these two directories for the ball,
    # so it is this script's job to delete what it no longer writes.
    keep_models = {f"basketball_{skin}" for skin in ball_model.SKIN_ORDER}
    keep_textures = {f"ball_{skin}.png" for skin in ball_model.SKIN_ORDER}
    keep_textures |= {f"ball_{skin}.png.mcmeta" for skin in ball_model.SKIN_ORDER}
    pruned = 0
    for stale in model_dir.glob("basketball_*.json"):
        if not any(stale.stem == keep or stale.stem.startswith(keep + "_")
                   for keep in keep_models):
            stale.unlink()
            pruned += 1
    for stale in item_tex_dir.glob("ball_*"):
        if stale.name not in keep_textures:
            stale.unlink()
            pruned += 1
    if pruned:
        print(f"      pruned {pruned} ball file(s) from a retired skin or the old cube faces")

    written = ball_model.write_pack_files(model_dir, item_tex_dir)
    print(f"      8 ball skins: {len(written['models'])} models, {len(written['textures'])} textures, "
          f"{len(written['meta'])} animation(s)")

    # The remaining per-skin variants are display-only: the gather keyframes (the shooting
    # pocket), the setpoint, and the mannequin cradle. They share the exact same geometry,
    # so a skin can never drift between its held and its displayed form.
    gather_stages = [
        ("gather_0", [8, 22, 0],   [1.6, -3.2, -2.4], [0.38, 0.38, 0.38]),
        ("gather_1", [-6, 20, 0],  [1.1, -0.8, -2.8], [0.40, 0.40, 0.40]),
        ("gather_2", [-16, 18, 0], [0.7,  1.2, -3.1], [0.41, 0.41, 0.41]),
        ("setpoint", [-26, 16, 0], [0.4,  2.4, -3.3], [0.42, 0.42, 0.42]),
    ]
    for skin in ball_model.SKIN_ORDER:
        model_json = ball_model.strip_meta(ball_model.build_model(skin))
        model_name = f"basketball_{skin}"
        for stage_suffix, fp_rot, fp_tr, fp_sc in gather_stages:
            stage_model = json.loads(json.dumps(model_json))
            # The gather keyframes ARE the first-person hand pose: the ball has to be
            # visible there, unlike the neutral model where the rig draws the hands.
            stage_model["display"]["firstperson_righthand"] = {
                "rotation": fp_rot, "translation": fp_tr, "scale": fp_sc
            }
            stage_model["display"]["firstperson_lefthand"] = {
                "rotation": fp_rot, "translation": [-fp_tr[0], fp_tr[1], fp_tr[2]], "scale": fp_sc
            }
            (model_dir / f"{model_name}_{stage_suffix}.json").write_text(json.dumps(stage_model, indent=2))

        mannequin_ball = json.loads(json.dumps(model_json))
        mannequin_ball["display"]["thirdperson_righthand"] = {
            "rotation": [18, 32, 0], "translation": [0.0, 2.6, 1.8], "scale": [0.42, 0.42, 0.42]
        }
        mannequin_ball["display"]["thirdperson_lefthand"] = {
            "rotation": [18, -32, 0], "translation": [0.0, 2.6, 1.8], "scale": [0.42, 0.42, 0.42]
        }
        (model_dir / f"{model_name}_mannequin.json").write_text(json.dumps(mannequin_ball, indent=2))
        (item_def_dir / f"{model_name}_mannequin.json").write_text(
            json.dumps(
                {
                    "model": {"type": "minecraft:model", "model": f"bosketball:item/{model_name}_mannequin"},
                    "hand_animation_on_swap": False,
                },
                indent=2,
            )
        )

        # Native 26.2 dynamic item definition: the ball switches to the gather keyframes
        # while the player holds right-click, which is how the shot charge reads in first
        # person without a client mod.
        (item_def_dir / f"{model_name}.json").write_text(
            json.dumps(
                {
                    "model": {
                        "type": "minecraft:condition",
                        "property": "minecraft:using_item",
                        "on_false": {"type": "minecraft:model", "model": f"bosketball:item/{model_name}"},
                        "on_true": {
                            "type": "minecraft:range_dispatch",
                            "property": "minecraft:use_duration",
                            "scale": 0.05,
                            "fallback": {"type": "minecraft:model", "model": f"bosketball:item/{model_name}_gather_0"},
                            "entries": [
                                {"threshold": 0.12, "model": {"type": "minecraft:model", "model": f"bosketball:item/{model_name}_gather_1"}},
                                {"threshold": 0.25, "model": {"type": "minecraft:model", "model": f"bosketball:item/{model_name}_gather_2"}},
                                {"threshold": 0.40, "model": {"type": "minecraft:model", "model": f"bosketball:item/{model_name}_setpoint"}},
                            ],
                        },
                    },
                    "hand_animation_on_swap": False,
                },
                indent=2,
            )
        )
        if skin == "classic":
            export_blockbench_bbmodel(bb_dir, model_json, item_tex_dir)

    # The contact sheet is the only way a human can review eight skins without the server.
    contact_path = PREVIEW_DIR / "ball_contact_sheet.png"
    contact_path.parent.mkdir(parents=True, exist_ok=True)
    ball_model.contact_sheet(
        {s: ball_model.skin_model(s) for s in ball_model.SKIN_ORDER},
        {s: ball_model.skin_textures(s) for s in ball_model.SKIN_ORDER},
        contact_path,
    )
    print(f"      contact sheet: {contact_path.relative_to(ROOT)}")

    # 2. Generate 3D Hoop + Backboard + Animated Net states
    generate_hoop_textures(item_tex_dir)
    for h_state in ("idle", "swish_1", "swish_2", "dunk_flex"):
        m_name = f"hoop_{h_state}"
        (model_dir / f"{m_name}.json").write_text(json.dumps(build_hoop_model_json(h_state), indent=2))
        (item_def_dir / f"{m_name}.json").write_text(
            json.dumps({"model": {"type": "minecraft:model", "model": f"bosketball:item/{m_name}"}}, indent=2)
        )

    # 3. Generate 3D Props (MVP Trophy, Referee Whistle, Air Sneakers, Tactics Playbook + Java key aliases)
    generate_prop_models_and_textures(item_tex_dir, model_dir)
    prop_aliases = {
        "trophy_mvp": "trophy_mvp",
        "referee_whistle": "referee_whistle",
        "whistle_ref": "referee_whistle",
        "sneakers_air": "sneakers_air",
        "sneakers_pro": "sneakers_air",
        "tactics_playbook": "tactics_playbook",
        "court_wand": "court_wand",
    }
    for item_id, target_model in prop_aliases.items():
        (item_def_dir / f"{item_id}.json").write_text(
            json.dumps({"model": {"type": "minecraft:model", "model": f"bosketball:item/{target_model}"}}, indent=2)
        )

    # 3b. Generate Base Icons (incl. ui_invisible), Warm Off-White/Cream/Beige Container UI, Floor Aura Rings, 4 Hooper + 2 NPC Skins & 3D Hooper Limb Rig!
    generate_pixel_art_item_icons(item_tex_dir, model_dir, item_def_dir, rp_dir=RP_DIR)
    generate_custom_container_gui(RP_DIR)
    generate_warm_cream_container_gui(RP_DIR)
    generate_lobby_3d_props(item_tex_dir, model_dir, item_def_dir)

    # 3c. Legacy icon layers, then the premium reference-style override:
    # chunky black outlines, saturated two-tone fills and hard white highlights.
    cuboide_sprites = build_cuboide_icon_sprites()
    cuboide_sprites.update(build_builtbybit_32x32_icons())
    cuboide_sprites.update(build_premium_icon_sprites())
    # Icons this repo draws itself, on the shared grid.  Merged last so an
    # authored icon always wins over a legacy painter of the same id.
    cuboide_sprites.update(authored_icon_sprites())
    court_hand_hidden = {
        "icon_pass", "icon_crossover", "icon_dunk", "icon_defense",
        "icon_bot_on", "icon_bot_off", "icon_leave",
        "dunk_tomahawk", "dunk_windmill", "dunk_spin_360", "dunk_between_legs", "dunk_meteor_jam",
    }
    for icon_id, sprite_im in cuboide_sprites.items():
        sprite_im.save(item_tex_dir / f"{icon_id}.png")
        fp_disp = (
            {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]}
            if icon_id in court_hand_hidden
            else {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}
        )
        tp_disp = (
            {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]}
            if icon_id in court_hand_hidden
            else {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]}
        )
        icon_model_json = {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"bosketball:item/{icon_id}"},
            "display": {
                "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
                "firstperson_righthand": fp_disp,
                "firstperson_lefthand": fp_disp,
                "thirdperson_righthand": tp_disp,
                "thirdperson_lefthand": tp_disp,
            },
        }
        (model_dir / f"{icon_id}.json").write_text(json.dumps(icon_model_json, indent=2))
        (item_def_dir / f"{icon_id}.json").write_text(
            json.dumps(
                {
                    "model": {"type": "minecraft:model", "model": f"bosketball:item/{icon_id}"},
                    "hand_animation_on_swap": False,
                },
                indent=2,
            )
        )

    conform_icons_to_language(item_tex_dir)

    generate_custom_hooper_player_skins(RP_DIR)
    generate_lobby_npc_skins(RP_DIR)
    generate_team_floor_rings(item_tex_dir, model_dir, item_def_dir)
    generate_hooper_3d_rig_models(item_tex_dir, model_dir, item_def_dir)

    # 4. Generate Custom Broadcast Banners, Scorebug, Rank Pills, Stat Badges & Shot Meter
    banners = {
        "banner_scorer.png": ("\uE100", 24, 15, make_beveled_banner(196, 24, (19, 26, 40), (12, 16, 26), (52, 74, 112, 248), (64, 218, 255, 255), "ball")),
        "banner_three.png":  ("\uE101", 24, 15, make_beveled_banner(196, 24, (16, 34, 42), (10, 20, 28), (48, 128, 138, 248), (58, 255, 168, 255), "three")),
        "banner_dunk.png":   ("\uE102", 24, 15, make_beveled_banner(196, 24, (44, 18, 24), (22, 11, 16), (146, 56, 48, 248), (255, 122, 36, 255), "dunk")),
        "banner_assist.png": ("\uE103", 13, 10, make_beveled_banner(136, 13, (22, 29, 44), (14, 18, 30), (58, 76, 108, 242), (108, 168, 235, 255), "none")),
        "scorebug.png":      ("\uE104", 20, 14, make_scorebug_banner()),
        "logo_header.png":   ("\uE105", 22, 16, make_logo_emblem()),
    }
    for fname, (_, _, _, im) in banners.items():
        im.save(font_tex_dir / fname)

    stat_icons = [
        ("icon_trophy.png", "\uE200", "trophy"),
        ("icon_star.png",   "\uE201", "star"),
        ("icon_ball.png",   "\uE202", "ball"),
        ("icon_fire.png",   "\uE203", "fire"),
        ("icon_crown.png",  "\uE204", "crown"),
        ("icon_shield.png", "\uE205", "shield"),
        ("icon_bolt.png",   "\uE206", "bolt"),
    ]
    for fname, _, kind in stat_icons:
        make_stat_icon(kind).save(font_tex_dir / fname)

    # The achievement icon reuses the existing fire glyph, rather than redrawing a second asset.
    (model_dir / "icon_fire.json").write_text(json.dumps({
        "parent": "minecraft:item/generated", "textures": {"layer0": "bosketball:font/icon_fire"}
    }, indent=2) + "\n")
    (item_def_dir / "icon_fire.json").write_text(json.dumps({
        "model": {"type": "minecraft:model", "model": "bosketball:item/icon_fire"},
        "hand_animation_on_swap": False
    }, indent=2) + "\n")

    rank_defs = [
        ("rank_rookie.png",    "\uE210", "ROOKIE"),
        ("rank_hooper.png",    "\uE211", "HOOPER"),
        ("rank_pro.png",       "\uE212", "PRO"),
        ("rank_allstar.png",   "\uE213", "ALL-STAR"),
        ("rank_superstar.png", "\uE214", "SUPERSTAR"),
        ("rank_legend.png",    "\uE215", "LEGEND"),
        ("rank_mvp.png",       "\uE216", "MVP"),
        ("rank_referee.png",   "\uE217", "REFEREE"),
        ("rank_admin.png",     "\uE218", "ADMIN"),
        ("team_ignis.png",     "\uE219", "IGNIS"),
        ("team_frost.png",     "\uE21A", "FROST"),
    ]
    rank_gui_advances = generate_modern_hidpi_rank_pills(font_tex_dir, overhaul_draw_text, overhaul_measure_text)

    generate_shot_meter_sheet(font_tex_dir)

    # Generate 4 crisp 11x11 custom HUD Aiming Reticle icons (U+E310..U+E313)
    reticle_sheet = Image.new("RGBA", (12 * 4, 11), (0, 0, 0, 0))
    reticle_palettes = [
        ((58, 255, 142, 255), (255, 232, 92, 255)),   # U+E310: Deadeye Green Lock
        ((72, 224, 255, 255), (242, 250, 255, 255)),  # U+E311: Lined-Up Cyan Target
        ((255, 174, 48, 255), (255, 238, 168, 255)),  # U+E312: Angled / Contested Amber
        ((255, 82, 96, 255),  (255, 192, 198, 255)),  # U+E313: Off-Rim / Heavy Contest / Free Throw
    ]
    for idx, (outer_c, core_c) in enumerate(reticle_palettes):
        cell = Image.new("RGBA", (12, 11), (0, 0, 0, 0))
        cd = ImageDraw.Draw(cell)
        # Sleek dark drop-outline + precision bracket corners + glowing center diamond
        cd.rectangle([2, 2, 8, 8], outline=(12, 16, 26, 225), width=1)
        for px, py in [(1, 1), (2, 1), (1, 2), (9, 1), (8, 1), (9, 2), (1, 9), (2, 9), (1, 8), (9, 9), (8, 9), (9, 8)]:
            cell.putpixel((px, py), outer_c)
        for px, py in [(5, 0), (5, 10), (0, 5), (10, 5)]:
            cell.putpixel((px, py), outer_c)
        for px, py in [(5, 4), (4, 5), (6, 5), (5, 6), (5, 5)]:
            cell.putpixel((px, py), core_c if (px, py) == (5, 5) else outer_c)
        pin_width(cell)
        reticle_sheet.paste(cell, (idx * 12, 0))
    reticle_sheet.save(font_tex_dir / "aim_reticles.png")

    # Build assets/minecraft/font/default.json keeping vanilla 26.2 references + negative spaces + custom bitmaps!
    advances = {}
    for i in range(9):
        advances[chr(0xE001 + i)] = -(1 << i)  # -1, -2, -4, -8, -16, -32, -64, -128, -256
        advances[chr(0xE011 + i)] = +(1 << i)  # +1, +2, +4, +8, +16, +32, +64, +128, +256

    providers = [
        {"type": "reference", "id": "minecraft:include/space"},
        {"type": "reference", "id": "minecraft:include/default", "filter": {"uniform": False}},
        {"type": "reference", "id": "minecraft:include/unifont"},
        {"type": "space", "advances": advances},
    ]
    for fname, (ch, h, asc, _) in banners.items():
        providers.append({"type": "bitmap", "file": f"bosketball:font/{fname}", "height": h, "ascent": asc, "chars": [ch]})
    for fname, ch, _ in stat_icons:
        providers.append({"type": "bitmap", "file": f"bosketball:font/{fname}", "height": 9, "ascent": 8, "chars": [ch]})
    for fname, ch, *_ in rank_defs:
        providers.append({"type": "bitmap", "file": f"bosketball:font/{fname}", "height": 9, "ascent": 8, "chars": [ch]})
    providers.append({
        "type": "bitmap",
        "file": "bosketball:font/shot_meter.png",
        "height": 9,
        "ascent": 8,
        "chars": ["".join(chr(0xE300 + i) for i in range(7))],
    })
    providers.append({
        "type": "bitmap",
        "file": "bosketball:font/aim_reticles.png",
        "height": 9,
        "ascent": 8,
        "chars": ["".join(chr(0xE310 + i) for i in range(4))],
    })
    providers.extend(generate_all_overhaul_font_providers(font_tex_dir))
    providers.extend(generate_large_ball_hover_previews(item_tex_dir, font_tex_dir, render_ball_preview))
    # GUI container overlays come exclusively from the Court Console kit: it owns U+E350..U+E359,
    # and registering a second provider for the same codepoint would silently win or lose depending
    # on provider order — that is exactly how the old double-title bug happened.
    providers.extend(generate_gui_overlays(font_tex_dir))
    # Round 37: the rank nameplates own the U+E390..U+E3AB block.
    providers.extend(generate_nameplate_font_providers())
    # Rounds 33 to 37 also bind bitmap providers from their own generators (lobby nav icons,
    # rarity tooltip frames, tab-list cell frames). Rewriting default.json wholesale used to
    # drop them on the floor and blank those glyphs in game, so anything already bound but
    # unknown to this file is carried over untouched, in its original order.
    providers = carry_over_bound_providers(providers, mc_font_dir / "default.json")
    generate_container_textures(RP_DIR / "assets/minecraft/textures/gui/container")

    (mc_font_dir / "default.json").write_text(json.dumps({"providers": providers}, indent=2, ensure_ascii=True))
    uniform_providers = [
        {"type": "reference", "id": "minecraft:include/space"},
        {"type": "reference", "id": "minecraft:include/unifont"},
        *providers[3:],
    ]
    (mc_font_dir / "uniform.json").write_text(json.dumps({"providers": uniform_providers}, indent=2, ensure_ascii=True))

    # Print exact pixel widths for Java HUD centering verification!
    print("=== Exact Glyph Widths (px) ===")
    for fname, (ch, h, _, im) in banners.items():
        print(f"  U+{ord(ch):04X} {fname:20s}: width={im.width}px, height={h}px")
    for fname, ch, label, *_ in rank_defs:
        im = Image.open(font_tex_dir / fname)
        print(f"  U+{ord(ch):04X} {label:10s}: width={im.width}px")

    # 5. Transparent White BossBar Sprites (so WHITE BossBars act as pure floating custom HUDs!)
    # Vanilla boss_bar sprites are 182x5 px; transparent 182x5 ensures exact vanilla nine-slice/blit dimensions with 0 alpha!
    trans_bar = Image.new("RGBA", (182, 5), (0, 0, 0, 0))
    trans_bar.save(mc_bossbar_dir / "white_background.png")
    trans_bar.save(mc_bossbar_dir / "white_progress.png")

    # Sleek modern neon-styled BossBars for colored bars (red, blue, green, yellow, purple, pink)
    bar_palettes = {
        "red":    ((238, 52, 62), (142, 22, 32)),
        "blue":   ((46, 168, 255), (22, 86, 168)),
        "green":  ((52, 238, 122), (22, 134, 64)),
        "yellow": ((255, 208, 48), (178, 118, 16)),
        "purple": ((182, 78, 255), (98, 34, 168)),
        "pink":   ((255, 86, 196), (168, 32, 118)),
    }
    for cname, (hi, lo) in bar_palettes.items():
        bg = Image.new("RGBA", (182, 5), (0, 0, 0, 0))
        bd = ImageDraw.Draw(bg)
        bd.rectangle([1, 0, 180, 4], fill=(16, 20, 30, 220), outline=(44, 54, 76, 240))
        bg.save(mc_bossbar_dir / f"{cname}_background.png")

        pr = Image.new("RGBA", (182, 5), (0, 0, 0, 0))
        pd = ImageDraw.Draw(pr)
        pd.rectangle([1, 0, 180, 4], fill=(*lo, 255), outline=(*hi, 255))
        pd.line([(2, 1), (179, 1)], fill=(min(255, hi[0] + 35), min(255, hi[1] + 35), min(255, hi[2] + 35), 255))
        pr.save(mc_bossbar_dir / f"{cname}_progress.png")

    # Complete all 8 notched BossBar overlays (notched_6, 10, 12, 20) in sleek carbon-slate segments
    for segs in (6, 10, 12, 20):
        for suffix, alpha in (("background", 145), ("progress", 175)):
            nb = Image.new("RGBA", (182, 5), (0, 0, 0, 0))
            nd = ImageDraw.Draw(nb)
            for s_idx in range(1, segs):
                nx = int(round(s_idx * 182.0 / segs))
                if 1 <= nx <= 180:
                    nd.line([(nx, 0), (nx, 4)], fill=(8, 12, 20, alpha))
            nb.save(mc_bossbar_dir / f"notched_{segs}_{suffix}.png")

    # 6. Synthesize custom 3D Mono .ogg Vorbis sound effects & sounds.json
    synth_sounds(sounds_dir)
    sounds_json = {
        "ball.bounce":        {"subtitle": "bosketball.sub.bounce",    "sounds": [{"name": "bosketball:bounce", "volume": 0.9}]},
        "ball.swish":         {"subtitle": "bosketball.sub.swish",     "sounds": [{"name": "bosketball:swish", "volume": 1.0}]},
        "ball.rim":           {"subtitle": "bosketball.sub.rim",       "sounds": [{"name": "bosketball:rim", "volume": 0.95}]},
        "ball.rim_hit":       {"subtitle": "bosketball.sub.rim",       "sounds": [{"name": "bosketball:rim", "volume": 0.95}]},
        "ball.backboard":     {"subtitle": "bosketball.sub.backboard", "sounds": [{"name": "bosketball:backboard", "volume": 0.9}]},
        "ball.dunk":          {"subtitle": "bosketball.sub.dunk",      "sounds": [{"name": "bosketball:dunk", "volume": 1.0}]},
        "game.dunk_boom":     {"subtitle": "bosketball.sub.dunk",      "sounds": [{"name": "bosketball:dunk", "volume": 1.0}]},
        "ball.block":         {"subtitle": "bosketball.sub.rim",       "sounds": [{"name": "bosketball:block_swat", "volume": 1.0}]},
        "defense.block":      {"subtitle": "bosketball.sub.rim",       "sounds": [{"name": "bosketball:block_swat", "volume": 1.0}]},
        "player.sneaker":     {"subtitle": "bosketball.sub.bounce",    "sounds": [{"name": "bosketball:sneaker_squeak", "volume": 0.85}]},
        "shoe.squeak":        {"subtitle": "bosketball.sub.bounce",    "sounds": [{"name": "bosketball:sneaker_squeak", "volume": 0.85}]},
        "crowd.cheer":        {"subtitle": "bosketball.sub.on_fire",   "sounds": [{"name": "bosketball:crowd_cheer", "volume": 0.95}]},
        "ui.click":           {"sounds": [{"name": "bosketball:ui_click", "volume": 0.8}]},
        # Played when a reward actually lands. It used to be requested by GgCommand and defined
        # nowhere, so the reward arrived in silence.
        "ui.confirm":         {"sounds": [{"name": "bosketball:green_release", "volume": 0.85}]},
        "buzzer":             {"subtitle": "bosketball.sub.buzzer",    "sounds": [{"name": "bosketball:buzzer", "volume": 1.0}]},
        "whistle":            {"subtitle": "bosketball.sub.whistle",   "sounds": [{"name": "bosketball:whistle", "volume": 0.95}]},
        "game.buzzer":        {"subtitle": "bosketball.sub.buzzer",    "sounds": [{"name": "bosketball:buzzer", "volume": 1.0}]},
        "game.whistle":       {"subtitle": "bosketball.sub.whistle",   "sounds": [{"name": "bosketball:whistle", "volume": 0.95}]},
        "game.green_release": {"subtitle": "bosketball.sub.green",     "sounds": [{"name": "bosketball:green_release", "volume": 1.0}]},
        "game.on_fire":       {"subtitle": "bosketball.sub.on_fire",   "sounds": [{"name": "bosketball:on_fire", "volume": 1.0}]},
        "empty":              {"sounds": [{"name": "bosketball:empty", "volume": 0.0}]},
    }
    # Trailing newline on purpose: every other JSON this script writes ends with one, and
    # without it a regeneration rewrites the last line of the file and leaves a spurious
    # "No newline at end of file" in the diff of a file nothing changed.
    (RP_DIR / "assets" / "bosketball" / "sounds.json").write_text(
        json.dumps(sounds_json, indent=2) + "\n", encoding="utf-8")

    # 7. English-only language files. The same English subtitles are written
    # to fr_fr as an override so French-language clients also stay 100% English.
    english_subtitles = {
        "bosketball.sub.bounce": "ʙᴀꜱᴋᴇᴛʙᴀʟʟ ʙᴏᴜɴᴄᴇꜱ",
        "bosketball.sub.swish": "ɴᴇᴛ ꜱᴡɪꜱʜᴇꜱ!",
        "bosketball.sub.rim": "ʀɪᴍ ᴄʟᴀɴᴋꜱ",
        "bosketball.sub.backboard": "ʙᴀᴄᴋʙᴏᴀʀᴅ ᴛʜᴜᴅ",
        "bosketball.sub.dunk": "ᴛʜᴜɴᴅᴇʀᴏᴜꜱ ꜱʟᴀᴍ ᴅᴜɴᴋ!",
        "bosketball.sub.buzzer": "ᴀʀᴇɴᴀ ʙᴜᴢᴢᴇʀ",
        "bosketball.sub.whistle": "ʀᴇꜰᴇʀᴇᴇ ᴡʜɪꜱᴛʟᴇ",
        "bosketball.sub.green": "ᴘᴇʀꜰᴇᴄᴛ ɢʀᴇᴇɴ ʀᴇʟᴇᴀꜱᴇ!",
        "bosketball.sub.on_fire": "ʜᴇ'ꜱ ᴏɴ ꜰɪʀᴇ!",
    }
    for locale_code in ("en_us", "fr_fr"):
        (lang_dir / f"{locale_code}.json").write_text(
            json.dumps(english_subtitles, indent=2, ensure_ascii=False)
        )

    # 8. pack.mcmeta for Minecraft 26.2 (BosketBall is a 26.2-only plugin, NO supported_formats!)
    pack_mcmeta = {
        "pack": {
            "description": "§6§lBOSKETBALL §8• §fOfficial Server Resource Pack",
            "min_format": 65,
            "max_format": 88,
        }
    }
    (RP_DIR / "pack.mcmeta").write_text(json.dumps(pack_mcmeta, indent=2, ensure_ascii=False))

    # 9. Official 1:1 Square BosketBall Logos (512x512 HD, 128x128 pack.png & 64x64 server-icon.png)
    generate_1x1_server_logos(ROOT)

    # 9b. Create tag item models/definitions, then replace their PNGs with
    # 32x32 colored verified-style collector badges (same IDs and models).
    generate_cuboide_tag_emblem_items(font_tex_dir, item_tex_dir, model_dir, item_def_dir)
    generate_premium_tag_emblem_items(font_tex_dir, item_tex_dir)

    # 9c. Render Visual QA Inspection Sheet for Cuboide Icons, Rank Badges, Hover Balls & Cytooxien GUIs!
    qa = Image.new("RGBA", (960, 620), (12, 16, 26, 255))
    qx, qy = 20, 14
    for icon_id, sp in sorted(cuboide_sprites.items()):
        sp3 = sp.resize((48, 48), Image.Resampling.NEAREST)
        qa.alpha_composite(sp3, (qx, qy))
        qx += 54
        if qx > 890:
            qx = 20
            qy += 52
    # Sample tag emblems
    qx = 20
    qy += 58
    for tag_s in ("founder", "mypineapple", "goat", "aura", "sakura", "galaxy", "inferno", "glacier", "viper", "king", "lockdown", "flash"):
        tp = item_tex_dir / f"tag_card_{tag_s}.png"
        if tp.exists():
            tim = Image.open(tp).resize((48, 48), Image.Resampling.NEAREST)
            qa.alpha_composite(tim, (qx, qy))
            qx += 54
    # Modernized HiDPI Rank Pills + FOUNDER comparison
    rx = 20
    ry = qy + 58
    for fname, _, _ in rank_defs:
        rim = Image.open(font_tex_dir / fname)
        if rx + rim.width * 2 > 930:
            rx = 20
            ry += 42
        qa.alpha_composite(rim.resize((rim.width * 2, rim.height * 2), Image.Resampling.NEAREST), (rx, ry))
        rx += rim.width * 2 + 8
    f_im = Image.open(font_tex_dir / "tag_founder.png")
    if rx + f_im.width * 2 > 930:
        rx = 20
        ry += 42
    qa.alpha_composite(f_im.resize((f_im.width * 2, f_im.height * 2), Image.Resampling.NEAREST), (rx, ry))

    # Reconstructed 4-row Large Ball Hover Previews + 2 Cytooxien GUI Overlays!
    bx = 20
    by = 430
    for bskin in BALL_PREVIEW_SKINS[:5]:
        for r_i in range(4):
            st = Image.open(font_tex_dir / f"ball_preview_{bskin}_{r_i}.png")
            qa.alpha_composite(st.resize((st.width * 2, st.height * 2), Image.Resampling.NEAREST), (bx, by + r_i * 20))
        bx += 96
    ov_shop = Image.open(font_tex_dir / "gui_overlay_tags.png")
    qa.alpha_composite(ov_shop, (530, 440))
    ov_play = Image.open(font_tex_dir / "gui_overlay_cosmetics.png")
    qa.alpha_composite(ov_play, (730, 440))
    qa.save(PREVIEW_DIR / "cuboide_and_gui_qa.png")
    print("Rank GUI advances:", rank_gui_advances)

    # 10. Package deterministic BosketBall-ResourcePack.zip + compute SHA-1!
    zip_path = RELEASE_DIR / "BosketBall-ResourcePack.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for file_path in sorted(RP_DIR.rglob("*")):
            if file_path.is_file() and "blockbench" not in file_path.parts:
                rel = file_path.relative_to(RP_DIR).as_posix()
                zf.write(file_path, rel)

    sha1 = hashlib.sha1(zip_path.read_bytes()).hexdigest()
    (RELEASE_DIR / "BosketBall-ResourcePack.zip.sha1").write_text(sha1 + "\n")
    import shutil
    shutil.copyfile(zip_path, RELEASE_DIR / "BosketBall-ResourcePack-26.2.zip")
    (RELEASE_DIR / "BosketBall-ResourcePack-26.2.zip.sha1").write_text(sha1 + "\n")
    print(f"Packaged {zip_path} ({zip_path.stat().st_size:,} bytes) | SHA-1: {sha1}")

    # 11. Render a Visual QA Showcase Sheet
    sheet = Image.new("RGBA", (960, 560), (14, 18, 28, 255))
    sd = ImageDraw.Draw(sheet)
    draw_pixel_text(sheet, "BOSKETBALL - OFFICIAL SERVER RESOURCE PACK", 24, 18, color=(255, 218, 86, 255), scale=3)

    # Four ball skins, at hero size
    for idx, skin in enumerate(["classic", "two_tone", "glow", "fire"]):
        bx = 44 + idx * 224
        by = 56
        ball_img = render_ball_preview(skin, out_size=160)
        sheet.alpha_composite(ball_img, (bx, by))
        label = BALL_PREVIEW_LABELS[skin]
        lw = measure_pixel_text(label, scale=2)
        draw_pixel_text(sheet, label, bx + (160 - lw) // 2, by + 166, color=(220, 235, 255, 255), scale=2)

    # Show scaled 2x Broadcast Banners & Scorebug (with sample overlaid player text just like image-1.png!)
    scorer_b = Image.open(font_tex_dir / "banner_scorer.png").resize((392, 48), Image.Resampling.NEAREST)
    sheet.paste(scorer_b, (44, 268), scorer_b)
    draw_pixel_text(sheet, "REFORTORR", 118, 276, color=(86, 224, 255, 255), shadow=(18, 36, 52, 255), scale=3)
    draw_pixel_text(sheet, "BASKET SCORER", 136, 298, color=(255, 198, 62, 255), scale=2)

    assist_b = Image.open(font_tex_dir / "banner_assist.png").resize((272, 26), Image.Resampling.NEAREST)
    sheet.paste(assist_b, (104, 322), assist_b)
    draw_pixel_text(sheet, "ASSIST  A KOOP", 152, 329, color=(158, 212, 255, 255), scale=2)

    sbug = Image.open(font_tex_dir / "scorebug.png").resize((456, 40), Image.Resampling.NEAREST)
    sheet.paste(sbug, (468, 276), sbug)
    draw_pixel_text(sheet, "IGNIS", 494, 289, color=(255, 240, 240, 255), scale=2)
    draw_pixel_text(sheet, "21", 590, 288, color=(255, 226, 92, 255), scale=2)
    draw_pixel_text(sheet, "Q4 01:12 14S", 638, 290, color=(255, 255, 255, 255), scale=2)
    draw_pixel_text(sheet, "19", 776, 288, color=(255, 226, 92, 255), scale=2)
    draw_pixel_text(sheet, "FROST", 830, 289, color=(235, 248, 255, 255), scale=2)

    # Show Rank Pills & Stat Badges scaled 3x (exact chat preview!)
    sd.rectangle([36, 378, 924, 534], fill=(8, 11, 18, 235), outline=(42, 54, 78, 255), width=2)
    rx = 52
    for fname, _, *_ in rank_defs:
        rp = Image.open(font_tex_dir / fname)
        rp3 = rp.resize((rp.width * 2, rp.height * 2), Image.Resampling.NEAREST)
        sheet.paste(rp3, (rx, 396), rp3)
        rx += rp3.width + 10

    # Simulated Chat line matching image-1.png!
    cx = 52
    cy = 442
    for icon_f, num_txt, col in [
        ("icon_trophy.png", "1", (255, 198, 58, 255)),
        ("icon_star.png",   "2", (205, 138, 255, 255)),
        ("icon_ball.png",   "1", (92, 242, 168, 255)),
    ]:
        ic = Image.open(font_tex_dir / icon_f).resize((18, 18), Image.Resampling.NEAREST)
        sheet.paste(ic, (cx, cy), ic)
        cx += 20
        cx += draw_pixel_text(sheet, num_txt, cx, cy + 4, color=col, scale=2) + 10
    mvp_pill = Image.open(font_tex_dir / "rank_mvp.png")
    mvp2 = mvp_pill.resize((mvp_pill.width * 2, mvp_pill.height * 2), Image.Resampling.NEAREST)
    sheet.paste(mvp2, (cx, cy), mvp2)
    cx += mvp2.width + 10
    draw_pixel_text(sheet, "A KOOP   NICE SHOT SPLASH 3PT", cx, cy + 4, color=(98, 224, 255, 255), scale=2)

    meter_im = Image.open(font_tex_dir / "shot_meter.png")
    m3 = meter_im.resize((meter_im.width * 3, meter_im.height * 3), Image.Resampling.NEAREST)
    sheet.paste(m3, (52, 488), m3)
    draw_pixel_text(sheet, "SHOT METER   98 PERFECT GREEN RELEASE", 240, 496, color=(82, 255, 138, 255), scale=2)

    sheet.save(PREVIEW_DIR / "resourcepack_showcase.png")
    print("Saved visual QA sheet to docs/previews/resourcepack_showcase.png")


if __name__ == "__main__":
    main()
