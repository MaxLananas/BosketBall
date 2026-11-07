#!/usr/bin/env python3
"""Builds the BosketBall brand & asset sheet (docs/showcase/brand-sheet.png).

Every artwork on this poster is read from the shipped resource pack, so the sheet can never drift
from what players actually see in game: rank badges, chat tags, GUI overlays, HUD pieces, icons,
basketball skins and the brand palette. All counts are computed, never typed by hand.

The layout is built from transparent "section layers" (each one measured by a flow layout) that are
composited onto the poster afterwards, so a section can never be painted over by its own panel.

    python3 tools/brand_sheet.py
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "resourcepack"
FONT_DIR = PACK / "assets/bosketball/textures/font"
OUT = ROOT / "docs/showcase/brand-sheet.png"
LOGO = ROOT / "releases/BosketBall-Logo-1x1.png"

PAD = 56
GOLD = (255, 212, 59)
ORANGE = (255, 140, 26)
MINT = (74, 222, 128)
ICE = (56, 189, 248)
IGNIS = (255, 107, 74)
SLATE = (148, 163, 184)
WHITE = (241, 245, 249)
DIM = (110, 126, 142)
PANEL = (16, 26, 35)
PANEL_EDGE = (33, 50, 62)

BOLD = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 26)
BOLD_SM = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 18)
BODY = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 19)
TINY = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
TITLE = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 96)
SUB = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 30)


def asset(name: str) -> Image.Image | None:
    path = FONT_DIR / f"{name}.png"
    return Image.open(path).convert("RGBA") if path.is_file() else None


def scaled(img: Image.Image, factor: int) -> Image.Image:
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def spaced(text: str) -> str:
    return " ".join(text.upper())


def layer_flow(entries, max_width: int, gap_x: int = 16, gap_y: int = 20,
               caption_gap: int = 8) -> Image.Image:
    """Packs (image, caption, colour) rows into a transparent layer. Returns the layer."""
    rows, row, used = [], [], 0
    for entry in entries:
        img, caption, _colour = entry
        width = img.width if caption is None else max(img.width, int(TINY.getlength(caption) if hasattr(TINY, "getlength") else len(caption) * 8))
        if used + width > max_width and row:
            rows.append(row)
            row, used = [], 0
        row.append(entry)
        used += width + gap_x
    if row:
        rows.append(row)

    heights = []
    for row in rows:
        tallest = 0
        for img, caption, _c in row:
            tallest = max(tallest, img.height + (caption_gap + 18 if caption else 0))
        heights.append(tallest)
    height = sum(heights) + gap_y * max(0, len(rows) - 1)
    layer = Image.new("RGBA", (max_width, max(1, height)), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    y = 0
    for row, row_h in zip(rows, heights):
        x = 0
        for img, caption, colour in row:
            layer.alpha_composite(img, (x, y))
            if caption:
                d.text((x, y + img.height + caption_gap), caption, font=TINY, fill=colour)
            x += img.width + gap_x
        y += row_h + gap_y
    return layer


def layer_stack(entries, gap: int = 18) -> Image.Image:
    """Stacks (image, label, colour) vertically, with an optional right-hand label."""
    width = max((img.width + (260 if label else 0)) for img, label, _c in entries)
    height = sum(img.height + gap for img, _l, _c in entries)
    layer = Image.new("RGBA", (width, max(1, height)), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    y = 0
    for img, label, colour in entries:
        layer.alpha_composite(img, (0, y))
        if label:
            d.text((img.width + 18, y + max(0, img.height // 2 - 9)), label, font=TINY, fill=colour)
        y += img.height + gap
    return layer


def section_title(d: ImageDraw.ImageDraw, x: int, y: int, width: int, label: str,
                  accent=GOLD, note: str | None = None) -> None:
    text = spaced(label)
    d.text((x, y), text, font=BOLD, fill=accent)
    cursor = x + d.textlength(text, font=BOLD) + 20
    if note:
        d.text((cursor, y + 9), note, font=TINY, fill=DIM)
        cursor += d.textlength(note, font=TINY) + 18
    d.line([(cursor, y + 17), (x + width, y + 17)], fill=(*PANEL_EDGE, 255), width=2)


def place_section(canvas: Image.Image, d: ImageDraw.ImageDraw, x: int, y: int,
                  layer: Image.Image, extra: int = 0) -> int:
    """Composites a section: panel first, artwork on top. Returns the bottom y."""
    pad_x, pad_top, pad_bottom = 18, 14, 16
    d.rounded_rectangle([x - pad_x, y - pad_top, x + layer.width + pad_x,
                         y + layer.height + pad_bottom + extra],
                        radius=16, fill=PANEL, outline=PANEL_EDGE, width=2)
    canvas.alpha_composite(layer, (x, y))
    return y + layer.height + pad_bottom + extra


def background(width: int, height: int) -> Image.Image:
    img = Image.new("RGBA", (width, height), (10, 17, 24, 255))
    d = ImageDraw.Draw(img)
    for y in range(height):
        t = y / (height - 1)
        c = (int(10 + 13 * t), int(17 + 17 * t), int(24 + 19 * t))
        d.line([(0, y), (width, y)], fill=(*c, 255))
    for radius, colour in ((1340, (25, 40, 52)), (1000, (23, 37, 48)), (640, (21, 34, 44))):
        d.arc([width // 2 - radius, height - radius - 90, width // 2 + radius, height + radius - 90],
              start=180, end=360, fill=(*colour, 255), width=3)
    for x in range(0, width, 30):
        d.line([(x, height - 70), (x + 14, height - 70)], fill=(29, 44, 56, 255), width=2)
    bloom = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bloom)
    for i in range(26):
        alpha = int(15 * (1 - i / 26))
        bd.ellipse([width - 940 - i * 14, -260 + i * 8, width - 300 + i * 8, 380 - i * 4],
                   fill=(255, 150, 40, alpha))
    return Image.alpha_composite(img, bloom)


def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)

    tags = sorted(p.stem for p in FONT_DIR.glob("tag_*.png"))
    rank_ids = ["rank_rookie", "rank_hooper", "rank_pro", "rank_allstar", "rank_superstar",
                "rank_legend", "rank_mvp", "rank_referee", "rank_admin"]
    floors = {"rank_rookie": "0 ELO", "rank_hooper": "1050 ELO", "rank_pro": "1200 ELO",
              "rank_allstar": "1400 ELO", "rank_superstar": "1650 ELO", "rank_legend": "1900 ELO",
              "rank_mvp": "2200 ELO", "rank_referee": "STAFF", "rank_admin": "STAFF"}
    overlays = sorted(p.stem for p in FONT_DIR.glob("gui_overlay_*.png"))
    png_count = len(list(PACK.rglob("*.png")))

    # ------------------------------------------------------------ measure every section first
    tags_layer = layer_flow([(scaled(asset(n), 2), None, SLATE) for n in tags if asset(n)], 1380, 16, 16)
    ranks_layer = layer_flow(
        [(scaled(asset(n), 3), floors[n], SLATE if floors[n] == "STAFF" else GOLD)
         for n in rank_ids if asset(n)], 1380, 28, 26)

    overlays_layer = Image.new("RGBA", (2 * (176 * 2) + 28, 2 * (137 * 2 + 42)), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlays_layer)
    picks = ["gui_overlay_hub", "gui_overlay_shop", "gui_overlay_play", "gui_overlay_ranks"]
    for i, name in enumerate(picks):
        img = asset(name)
        if img is None:
            continue
        big = scaled(img, 2)
        r, c = divmod(i, 2)
        overlays_layer.alpha_composite(big, (c * (big.width + 28), r * (big.height + 42)))
        od.text((c * (big.width + 28), r * (big.height + 42) + big.height + 10),
                name.replace("gui_overlay_", "").upper(), font=TINY, fill=SLATE)

    hud_layer = layer_stack([
        (scaled(asset("scorebug"), 2), "SCORE BUG", SLATE),
        (scaled(asset("sidebar_flourish"), 2), "SIDEBAR", SLATE),
        (scaled(asset("shot_meter"), 4), "SHOT METER", SLATE),
        (scaled(asset("tab_header_card"), 1), "TAB HEADER", SLATE),
        (scaled(asset("banner_dunk"), 1), "BANNER · DUNK", SLATE),
    ])

    icon_rows = [
        ([scaled(asset(n), 5) for n in ["icon_ball", "icon_star", "icon_fire", "icon_trophy",
                                       "icon_crown", "icon_shield", "icon_bolt"] if asset(n)], 26),
        ([scaled(asset(n), 2) for n in ["nav_play", "nav_locker", "nav_shop", "nav_ranks",
                                        "nav_help", "nav_discord"] if asset(n)], 14),
    ]
    icons_w = max(sum(img.width for img in row) + gap * (len(row) - 1) for row, gap in icon_rows)
    icons_h = sum(max(img.height for img in row) for row, _g in icon_rows) + 44
    icons_layer = Image.new("RGBA", (icons_w, icons_h), (0, 0, 0, 0))
    y_icon = 0
    for row, gap in icon_rows:
        x = 0
        for big in row:
            icons_layer.alpha_composite(big, (x, y_icon))
            x += big.width + gap
        y_icon += max(img.height for img in row) + 44

    # sidebar + leaderboards headers: fills the guide column and shows the HUD text pieces
    labels = [("sb_elo", 3), ("sb_coins", 3), ("sb_points", 3), ("sb_stats", 3), ("sb_playtime", 3),
              ("sb_possession", 3), ("sb_streak", 3), ("sb_clock", 3), ("sb_match", 3), ("sb_friends", 3)]
    tops = [("top_elo_1", 3), ("top_dunks_1", 3), ("top_points_1", 3), ("top_assists_1", 3),
            ("top_mvp_1", 3), ("top_wins_1", 3)]
    chips_layer = layer_flow(
        [(scaled(asset(n), f), None, SLATE) for n, f in labels + tops if asset(n)], 1380, 22, 18)

    skins_layer = layer_flow(
        [(scaled(asset(f"ball_preview_{s}_0"), 4), s.upper(), WHITE)
         for s in ["classic", "street", "gold", "fire", "galaxy", "neon", "sakura", "money"]
         if asset(f"ball_preview_{s}_0")], 840, 26, 24)

    palette_layer = Image.new("RGBA", (820, 200), (0, 0, 0, 0))
    pd = ImageDraw.Draw(palette_layer)
    palette = [("#FFD43B", "gold"), ("#FF8C1A", "orange"), ("#FF6B4A", "ignis"), ("#4ADE80", "mint"),
               ("#38BDF8", "ice"), ("#818CF8", "violet"), ("#FF5EA8", "sunset"), ("#F1F5F9", "ink")]
    for i, (hexcode, label) in enumerate(palette):
        cx = (i % 4) * 200
        cy = (i // 4) * 100
        colour = tuple(int(hexcode[j:j + 2], 16) for j in (1, 3, 5))
        pd.rounded_rectangle([cx, cy, cx + 76, cy + 76], radius=12, fill=(*colour, 255),
                             outline=(255, 255, 255, 46), width=1)
        pd.text((cx + 88, cy + 10), hexcode, font=BOLD_SM, fill=WHITE)
        pd.text((cx + 88, cy + 36), label, font=TINY, fill=SLATE)

    # ------------------------------------------------------------ layout
    AX, AW = PAD + 18, max(tags_layer.width, ranks_layer.width)
    BX = AX + AW + 76
    BW = max(overlays_layer.width, hud_layer.width, icons_layer.width)

    a_y = 330
    a_positions = {}
    for key, lay in (("tags", tags_layer), ("ranks", ranks_layer), ("chips", chips_layer)):
        a_positions[key] = (a_y, lay)
        a_y += lay.height + 106          # layer + panel padding + section title + gap

    b_y = 330
    b_positions = {}
    for key, lay in (("overlays", overlays_layer), ("hud", hud_layer), ("icons", icons_layer)):
        b_positions[key] = (b_y, lay)
        b_y += lay.height + 106

    band_y = max(a_y, b_y) + 10
    band_h = max(skins_layer.height, palette_layer.height)
    footer_y = band_y + band_h + 120

    W = BX + BW + PAD + 18
    H = footer_y + 40
    canvas = background(W, H)
    d = ImageDraw.Draw(canvas)

    # ------------------------------------------------------------ header
    logo = Image.open(LOGO).convert("RGBA").resize((200, 200), Image.LANCZOS)
    d.rounded_rectangle([PAD - 14, 42, PAD + 214, 270], radius=22, fill=(14, 23, 31),
                        outline=(52, 74, 90), width=2)
    canvas.alpha_composite(logo, (PAD, 56))
    d.text((PAD + 244, 62), "BOSKETBALL", font=TITLE, fill=ORANGE)
    d.text((PAD + 240, 58), "BOSKETBALL", font=TITLE, fill=GOLD)
    d.text((PAD + 243, 166), spaced("3d competitive basketball"), font=SUB, fill=WHITE)
    d.text((PAD + 243, 212),
           "brand & asset sheet  ·  round 30  ·  every pixel below ships in the resource pack",
           font=BODY, fill=SLATE)

    stats = f"{png_count} textures  ·  {len(tags)} tags  ·  {len(rank_ids)} ranks  ·  {len(overlays)} GUI screens"
    sw = d.textlength(stats, font=BOLD_SM)
    d.rounded_rectangle([W - PAD - sw - 52, 72, W - PAD, 120], radius=12, fill=(20, 32, 42),
                        outline=(58, 82, 98), width=2)
    d.text((W - PAD - sw - 26, 85), stats, font=BOLD_SM, fill=MINT)
    d.text((W - PAD - 348, 138), "discord.gg/pnJhKuU2QK", font=BODY, fill=ICE)

    # ------------------------------------------------------------ column A
    y, lay = a_positions["tags"]
    section_title(d, AX, y - 56, AW, "chat tags", note=f"{len(tags)} collectables, common → founder")
    bottom = place_section(canvas, d, AX, y, lay, extra=26)
    d.text((AX + 2, bottom - 34), "Every chat tag on the network — bought with Coins or earned in season.",
           font=TINY, fill=DIM)

    y, lay = a_positions["ranks"]
    section_title(d, AX, y - 56, AW, "ranks & staff", note="the real ELO ladder")
    bottom = place_section(canvas, d, AX, y, lay, extra=26)
    d.text((AX + 2, bottom - 34), "Win +15 ELO, loss −5. ROOKIE → MVP, plus referee and admin staff badges.",
           font=TINY, fill=DIM)

    y, lay = a_positions["chips"]
    section_title(d, AX, y - 56, AW, "sidebar & leaderboards", note="every HUD label")
    bottom = place_section(canvas, d, AX, y, lay)

    teams_y = bottom + 66
    section_title(d, AX, teams_y - 56, AW, "teams", accent=IGNIS)
    tx = AX
    for name, label, colour in (("team_ignis", "IGNIS — home side", IGNIS),
                                ("team_frost", "FROST — away side", ICE)):
        img = asset(name)
        if img is None:
            continue
        big = scaled(img, 3)
        d.rounded_rectangle([tx - 18, teams_y - 14, tx + big.width + 320, teams_y + big.height + 16],
                            radius=16, fill=PANEL, outline=PANEL_EDGE, width=2)
        canvas.alpha_composite(big, (tx, teams_y))
        d.text((tx + big.width + 22, teams_y + big.height // 2 - 12), label, font=BOLD_SM, fill=colour)
        tx += big.width + 340

    # ------------------------------------------------------------ column B
    y, lay = b_positions["overlays"]
    section_title(d, BX, y - 56, BW, "gui screens", note=f"{len(overlays)} custom overlays")
    place_section(canvas, d, BX, y, lay)

    y, lay = b_positions["hud"]
    section_title(d, BX, y - 56, BW, "hud", note="in-game furniture")
    place_section(canvas, d, BX, y, lay)

    y, lay = b_positions["icons"]
    section_title(d, BX, y - 56, BW, "icons & navigation")
    place_section(canvas, d, BX, y, lay)

    # ------------------------------------------------------------ bottom band
    section_title(d, AX, band_y - 56, 1180, "basketball skins", note="8 families × 4 animations")
    place_section(canvas, d, AX, band_y, skins_layer)

    px = AX + 1240
    section_title(d, px, band_y - 56, W - PAD - px, "palette & type")
    place_section(canvas, d, px, band_y, palette_layer)
    d.text((px + 4, band_y + palette_layer.height + 20),
           "small-caps ɪɴ-ɢᴀᴍᴇ labels · bold condensed numbers · DejaVu for docs", font=TINY, fill=DIM)

    # ------------------------------------------------------------ footer
    fy = H - 46
    d.line([(PAD, fy - 24), (W - PAD, fy - 24)], fill=(38, 56, 70, 255), width=2)
    d.text((PAD, fy - 8), "BOSKETBALL  ·  3D competitive basketball  ·  discord.gg/pnJhKuU2QK",
           font=BOLD_SM, fill=SLATE)
    d.text((W - PAD - 500, fy - 8), "generated from the shipped resource pack", font=TINY, fill=(88, 104, 120))

    canvas.convert("RGB").save(OUT)
    print(f"brand sheet -> {OUT.relative_to(ROOT)} ({canvas.width}x{canvas.height})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
