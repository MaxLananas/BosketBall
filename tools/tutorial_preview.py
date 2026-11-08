#!/usr/bin/env python3
"""Renders a faithful mock of the Round 31 tutorial: chat card, boss bar, action bar and the
/new-player guide GUI. It reuses the real resource-pack textures and the real palette so the
mock cannot drift from what the plugin prints.

Usage: PYTHONPATH=/tmp/pylibs python3 tools/tutorial_preview.py
Output: docs/previews/tutorial-preview.png
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resourcepack" / "assets/bosketball"
FONT_DIR = PACK / "textures" / "font"
ITEM_DIR = PACK / "textures" / "item"
OUT = ROOT / "docs" / "previews" / "tutorial-preview.png"

GOLD = "#FFD43B"
ORANGE = "#FF8C1A"
CORAL = "#FF6B4A"
GREEN = "#4ADE80"
SKY = "#38BDF8"
INDIGO = "#818CF8"
PINK = "#FF5EA8"
WHITE = "#F1F5F9"
SLATE = "#94A3B8"
DIM = "#64748B"
NIGHT = "#0B1020"
PANEL = "#121A2E"
PANEL_2 = "#0E1626"
BORDER = "#2A3550"
SLOT = "#1B2438"

W, H = 1740, 1252
PAD = 56

TITLE = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 40)
H2 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
LEAD = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 46)
SUB = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 22)
SUB_I = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf", 22)
BODY = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 20)
BODY_B = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 20)
TINY = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 16)
TINY_B = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
MICRO = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 13)
MONO = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 15)


def icon(name: str, size: int) -> Image.Image:
    path = ITEM_DIR / f"{name}.png"
    if not path.exists():  # ref_* icons live one folder deeper
        path = ITEM_DIR / "icons" / f"{name}.png"
    im = Image.open(path).convert("RGBA")
    return im.resize((size, size), Image.NEAREST)


def rounded(size, radius, fill, outline=None, width=1):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=fill,
                        outline=outline, width=width)
    return layer


def chip(img, box, radius, fill, outline=None, width=1):
    """Translucent rounded rectangle: Pillow's ImageDraw overwrites alpha, so composite a layer."""
    x0, y0, x1, y1 = [int(v) for v in box]
    layer = Image.new("RGBA", (max(1, x1 - x0), max(1, y1 - y0)), (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle([0, 0, layer.width - 1, layer.height - 1],
                                            radius, fill=fill, outline=outline, width=width)
    img.alpha_composite(layer, (x0, y0))


def shadow(size, radius, offset=(0, 8), alpha=110):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius, fill=(0, 0, 0, alpha))
    return layer


def gradient(size, start, end):
    layer = Image.new("RGBA", size, start)
    d = ImageDraw.Draw(layer)
    for x in range(size[0]):
        t = x / max(1, size[0] - 1)
        d.line([(x, 0), (x, size[1])],
               fill=tuple(int(start[i] + (end[i] - start[i]) * t) for i in range(3))
               + (start[3],))
    return layer


def rule(img, x, y, width, scale=2, reverse=False):
    """Draws the plugin's real divider glyph: cap + N segments + cap."""
    cap_l = Image.open(FONT_DIR / "divider_cap_l.png").convert("RGBA")
    cap_r = Image.open(FONT_DIR / "divider_cap_r.png").convert("RGBA")
    seg = Image.open(FONT_DIR / "divider_seg.png").convert("RGBA")
    if reverse:
        cap_l, cap_r = cap_r.transpose(Image.FLIP_LEFT_RIGHT), cap_l.transpose(Image.FLIP_LEFT_RIGHT)
    cap_l = cap_l.resize((cap_l.width * scale, cap_l.height * scale), Image.NEAREST)
    cap_r = cap_r.resize((cap_r.width * scale, cap_r.height * scale), Image.NEAREST)
    seg = seg.resize((seg.width * scale, seg.height * scale), Image.NEAREST)
    base = y + cap_l.height - 2
    # fade out: the glyph is a solid line, so we feather towards the centre.
    strip = Image.new("RGBA", (width, cap_l.height + 2), (0, 0, 0, 0))
    x0 = 0
    strip.alpha_composite(cap_l, (0, 0))
    x0 += cap_l.width
    while x0 < width - cap_r.width:
        strip.alpha_composite(seg, (x0, 0))
        x0 += seg.width
    strip.alpha_composite(cap_r, (width - cap_r.width, 0))
    img.alpha_composite(strip, (x, base - strip.height))


def text_run(d, x, y, runs, font=BODY, bold_font=BODY_B, spacing=0):
    """Draws [(text, colour, bold)] and returns the end x."""
    cursor = x
    for text, colour, bold in runs:
        f = bold_font if bold else font
        d.text((cursor, y), text, font=f, fill=colour)
        cursor += d.textlength(text, font=f) + spacing
    return cursor


def wrap(runs, font, bold_font, max_width, d):
    lines, current, width = [], [], 0.0
    for text, colour, bold in runs:
        f = bold_font if bold else font
        words = text.split(" ")
        for i, word in enumerate(words):
            piece = word if i == len(words) - 1 else word + " "
            w = d.textlength(piece, font=f)
            if width + w > max_width and current:
                lines.append(current)
                current, width = [], 0.0
                piece = piece.lstrip()
                w = d.textlength(piece, font=f)
            current.append((piece, colour, bold))
            width += w
    if current:
        lines.append(current)
    return lines


# ---------------------------------------------------------------------------------------------
# content mirroring TutorialManager.STEPS (chapter 3 / 8)
# ---------------------------------------------------------------------------------------------
CH_INDEX = "CHAPTER 3 / 8"
CH_TITLE = "SHOOTING"
CH_SUB = "The green release window"
CH_LINES = [
    [("Hold ", SLATE, False), ("right-click", WHITE, False),
     (" with the ball to draw a shot.", SLATE, False)],
    [("Release when the meter crosses the ", SLATE, False), ("green zone", GREEN, True),
     (" — that's a swish.", SLATE, False)],
    [("The marker above the rim shows where to aim.", SLATE, False)],
]
REWARD = "20"
NEXT = [("NEXT", DIM, False), ("  DUNKING", WHITE, True),
        ("   One click, one slam", SLATE, False)]

CHAPTERS = [
    ("ᴡᴇʟᴄᴏᴍᴇ", "ref_medal_gold", "done", "10"),
    ("ᴍᴏᴠᴇ & ʟᴏᴏᴋ", "ref_calendar_check", "done", "15"),
    ("ꜱʜᴏᴏᴛɪɴɢ", "ref_calendar_check", "current", "20"),
    ("ᴅᴜɴᴋɪɴɢ", "icon_dunk", "todo", "20"),
    ("ᴅᴇꜰᴇɴꜱᴇ", "icon_defense", "todo", "20"),
    ("ᴇᴄᴏɴᴏᴍʏ", "icon_shop", "locked", "25"),
    ("Qᴜᴇᴜᴇ ᴜᴘ", "icon_trophy_gold", "locked", "30"),
    ("ꜰɪɴᴀʟ ʙᴜᴢᴢᴇʀ", "trophy_gold", "locked", "0"),
]
GUI_SLOTS = [10, 12, 14, 16, 19, 21, 23, 25]
STATUS_COLOUR = {"done": GREEN, "current": GOLD, "todo": SKY, "locked": DIM}
STATUS_GLYPH = {"done": "OK", "current": "HERE", "todo": "NEW", "locked": "LOCK"}


def build() -> Image.Image:
    img = Image.new("RGBA", (W, H), NIGHT)
    d = ImageDraw.Draw(img)

    # soft court background: three concentric arcs + centre circle, very low alpha
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W - 620, -260, W + 320, 680], outline=(255, 140, 26, 26), width=3)
    gd.ellipse([W - 520, -160, W + 220, 580], outline=(255, 212, 59, 18), width=2)
    gd.ellipse([-300, H - 520, 520, H + 300], outline=(56, 189, 248, 16), width=2)
    img.alpha_composite(glow)

    d.text((PAD, 46), "BOSKETBALL", font=TITLE, fill=ORANGE)
    x = PAD + d.textlength("BOSKETBALL", font=TITLE) + 18
    d.text((x, 58), "// NEW PLAYER GUIDE", font=H2, fill=DIM)
    d.text((PAD, 100), "Ce que le joueur voit vraiment — rendu des vrais textes, glyphes et icônes du pack.",
           font=TINY, fill=SLATE)
    tag = "ROUND 31  ·  8 CHAPTERS  ·  /tuto"
    tw = d.textlength(tag, font=MICRO)
    chip(img, [W - PAD - tw - 28, 52, W - PAD, 82], 15, (255, 212, 59, 26), (255, 212, 59, 70))
    d.text((W - PAD - tw - 14, 60), tag, font=MICRO, fill=GOLD)

    # ---------------------------------------------------------------- chat card
    card_x, card_y = PAD, 152
    card_w, card_h = 900, 372
    img.alpha_composite(shadow((card_w, card_h), 22), (card_x, card_y + 10))
    img.alpha_composite(rounded((card_w, card_h), 22, PANEL, BORDER, 2), (card_x, card_y))
    accent = gradient((6, card_h - 44), (255, 212, 59, 255), (255, 107, 74, 255))
    img.alpha_composite(accent, (card_x, card_y + 22))

    inner = card_x + 40
    rule(img, inner, card_y + 26, card_w - 80, scale=2)
    d.text((inner, card_y + 52), CH_INDEX, font=MICRO, fill=GOLD)
    d.text((inner, card_y + 74), CH_TITLE, font=LEAD, fill=WHITE)
    d.text((inner + d.textlength(CH_TITLE, font=LEAD) + 16, card_y + 96), CH_SUB,
           font=SUB_I, fill=SLATE)

    y = card_y + 146
    for runs in CH_LINES:
        for line in wrap(runs, BODY, BODY_B, card_w - 110, d):
            d.ellipse([inner - 16, y + 9, inner - 9, y + 16], fill=(255, 140, 26, 200))
            text_run(d, inner, y, line)
            y += 30

    # reward pill + skip pill
    pill_y = card_y + 268
    pill = f"+{REWARD} COINS"
    pw = 52 + d.textlength(pill, font=TINY_B) + 16
    chip(img, [inner, pill_y, inner + pw, pill_y + 34], 17, (255, 212, 59, 30), (255, 212, 59, 110))
    img.alpha_composite(icon("icon_coin_stack", 20), (inner + 13, pill_y + 7))
    d.text((inner + 41, pill_y + 8), pill, font=TINY_B, fill=GOLD)

    skip = "SKIP AFTER 2 MIN"
    sw = d.textlength(skip, font=TINY) + 34
    chip(img, [inner + pw + 12, pill_y, inner + pw + 12 + sw, pill_y + 34], 17,
         (27, 36, 56, 200), BORDER)
    d.text((inner + pw + 29, pill_y + 9), skip, font=TINY, fill=SLATE)

    rule(img, inner, card_y + 306, card_w - 80, scale=2, reverse=True)
    text_run(d, inner, card_y + 322, NEXT, font=TINY, bold_font=TINY_B)

    # ---------------------------------------------------------------- boss bar
    bar_x, bar_y, bar_w, bar_h = PAD, 566, 900, 46
    img.alpha_composite(shadow((bar_w, bar_h), 12, alpha=80), (bar_x, bar_y + 6))
    img.alpha_composite(rounded((bar_w, bar_h), 12, (10, 16, 30, 235), BORDER, 2), (bar_x, bar_y))
    fill_w = int((bar_w - 8) * 0.375)
    img.alpha_composite(rounded((max(fill_w, 24), bar_h - 8), 8,
                                (255, 212, 59, 210)), (bar_x + 4, bar_y + 4))
    d.text((bar_x + 22, bar_y + 13), "CHAPTER 3 / 8  ·  SHOOTING", font=TINY_B, fill=NIGHT)
    d.text((bar_x + bar_w - 150, bar_y + 14), "37 %", font=TINY_B, fill=SLATE)

    # ---------------------------------------------------------------- action bar
    ab_y = 632
    parts = [("HOLD RIGHT-CLICK TO CHARGE", WHITE, True), ("|", DIM, False),
             ("RELEASE IN THE GREEN", GREEN, True), ("|", DIM, False),
             ("/tuto FOR THE GUIDE", SLATE, False)]
    total = sum(d.textlength(t, font=TINY_B if b else TINY) for t, c, b in parts) + 20 * (len(parts) - 1)
    chip(img, [PAD, ab_y, PAD + total + 44, ab_y + 36], 18, (16, 24, 40, 235), (42, 53, 80, 220))
    text_run(d, PAD + 22, ab_y + 9, parts, font=TINY, bold_font=TINY_B, spacing=20)

    # ---------------------------------------------------------------- world hints
    hint_y = ab_y + 58
    d.text((PAD, hint_y), "IN-WORLD CUE", font=MICRO, fill=DIM)
    d.text((PAD, hint_y + 20),
           "Gold ring on the floor  →  turns GREEN on entry  →  particle arrow above the rim.",
           font=BODY, fill=SLATE)

    # ---------------------------------------------------------------- guide GUI
    gui_x, gui_y = 1010, 152
    cell, gap = 62, 6
    gui_w = 9 * cell + 8 * gap + 56
    gui_h = 6 * cell + 5 * gap + 150
    img.alpha_composite(shadow((gui_w, gui_h), 20), (gui_x, gui_y + 10))
    img.alpha_composite(rounded((gui_w, gui_h), 20, PANEL_2, BORDER, 2), (gui_x, gui_y))
    d.text((gui_x + 28, gui_y + 22), "NEW PLAYER GUIDE", font=H2, fill=WHITE)
    d.text((gui_x + 28, gui_y + 52), "CHAPTER 3 / 8  ·  SHOOTING", font=MICRO, fill=GOLD)
    d.text((gui_x + 28 + d.textlength("CHAPTER 3 / 8  ·  SHOOTING", font=MICRO) + 18, gui_y + 52),
           "REWARDS 10 · 15 · 20 · 20 · 20 · 25 · 30" , font=MICRO, fill=DIM)

    grid_x, grid_y = gui_x + 28, gui_y + 84
    for row in range(6):
        for col in range(9):
            sx = grid_x + col * (cell + gap)
            sy = grid_y + row * (cell + gap)
            img.alpha_composite(rounded((cell, cell), 10, SLOT, (38, 50, 74), 1), (sx, sy))

    for slot, (name, icon_name, status, coins) in zip(GUI_SLOTS, CHAPTERS):
        row, col = divmod(slot, 9)
        sx = grid_x + col * (cell + gap)
        sy = grid_y + row * (cell + gap)
        colour = STATUS_COLOUR[status]
        if status == "current":
            img.alpha_composite(rounded((cell + 4, cell + 4), 12, (255, 212, 59, 40),
                                        GOLD, 2), (sx - 2, sy - 2))
        elif status == "done":
            img.alpha_composite(rounded((cell + 4, cell + 4), 12, (0, 0, 0, 0), (74, 222, 128, 110), 2),
                                (sx - 2, sy - 2))
        img.alpha_composite(icon(icon_name, 30), (sx + 16, sy + 7))
        label = STATUS_GLYPH[status]
        lw = d.textlength(label, font=MICRO)
        d.text((sx + (cell - lw) / 2, sy + 43), label, font=MICRO, fill=colour)
        d.ellipse([sx + 6, sy + 6, sx + 13, sy + 13], fill=colour)

    buttons = [(45, "icon_leave", "PAUSE"), (47, "icon_reload", "DIAG"),
               (51, "icon_prev", "RESTART"), (49, "icon_back", "CLOSE")]
    for slot, icon_name, label in buttons:
        row, col = divmod(slot, 9)
        sx = grid_x + col * (cell + gap)
        sy = grid_y + row * (cell + gap)
        img.alpha_composite(rounded((cell, cell), 10, (32, 44, 66), BORDER, 1), (sx, sy))
        img.alpha_composite(icon(icon_name, 30), (sx + 16, sy + 8))
        d.text((sx + 5, sy + 44), label, font=MICRO, fill=SLATE)

    legend_y = grid_y + 6 * (cell + gap) + 8
    x = grid_x
    for status in ("done", "current", "todo", "locked"):
        colour = STATUS_COLOUR[status]
        d.ellipse([x, legend_y + 5, x + 9, legend_y + 14], fill=colour)
        label = {"done": "validated", "current": "in progress", "todo": "open",
                 "locked": "locked"}[status]
        d.text((x + 16, legend_y + 1), label, font=TINY, fill=SLATE)
        x += 22 + d.textlength(label, font=TINY) + 22

    # ---------------------------------------------------------------- rules footer
    fy = 748
    d.text((PAD, fy), "REGLES DE PARCOURS (TESTEES EN SITUATION)", font=MICRO, fill=DIM)
    rules = [
        ("/tuto", "reimprime le chapitre en cours"),
        ("/tuto diag", "progression, chrono, court, ledger"),
        ("/tuto restart", "rejoue sans repayer un Coin"),
        ("/tuto stop", "pause ; la carte revient au /tuto"),
        ("/leave", "l'anneau se recale sous 5 s"),
        ("File / match", "reprise auto, fin propre au dernier chapitre"),
        ("Aucun court", "shoot/dunk passes apres 6 s"),
        ("Reload", "hologramme + verre redessines"),
    ]
    col_w = 620
    for i, (cmd, desc) in enumerate(rules):
        cx = PAD + (i // 4) * (col_w + 40)
        cy = fy + 30 + (i % 4) * 44
        cw = d.textlength(cmd, font=MONO) + 24
        chip(img, [cx, cy, cx + cw, cy + 28], 8, (255, 212, 59, 26), (255, 212, 59, 80))
        d.text((cx + 12, cy + 6), cmd, font=MONO, fill=GOLD)
        d.text((cx + cw + 14, cy + 7), desc, font=TINY, fill=SLATE)

    note_y = 956
    chip(img, [PAD, note_y, W - PAD, note_y + 96], 16, (74, 222, 128, 22), (74, 222, 128, 70))
    d.text((PAD + 24, note_y + 16), "CE QUI A CHANGE", font=MICRO, fill=GREEN)
    d.text((PAD + 24, note_y + 38),
           "Le paiement est un ledger persistant : rejouer, quitter, se reconnecter ou spammer SKIP ne peut plus payer deux fois.",
           font=BODY, fill=WHITE)
    d.text((PAD + 24, note_y + 68),
           "Toutes les transitions du tour passent par TutorialFlow (regles pures) — 10 parcours joueur couverts par les tests.",
           font=TINY, fill=SLATE)

    # ---------------------------------------------------------------- scenario chips
    sy = note_y + 122
    d.text((PAD, sy), "10 PARCOURS JOUEUR VALIDES EN TEST", font=MICRO, fill=DIM)
    scenarios = ["/tuto x2", "replay complet", "quit + reconnect", "spam SKIP", "/leave au milieu",
                 "match pendant le tuto", "aucun court dispo", "reload a chaud",
                 "/tuto en file d'attente", "staff /tuto goto"]
    x, y = PAD, sy + 22
    for label in scenarios:
        w = d.textlength(label, font=TINY) + 30
        if x + w > W - PAD:
            x, y = PAD, y + 40
        chip(img, [x, y, x + w, y + 30], 15, (56, 189, 248, 22), (56, 189, 248, 70))
        d.text((x + 15, y + 7), label, font=TINY, fill=SKY)
        x += w + 10

    d.text((W - PAD - d.textlength("BOSKETBALL · ROUND 31", font=MICRO), H - 34),
           "BOSKETBALL · ROUND 31", font=MICRO, fill=DIM)
    return img


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    build().convert("RGB").save(OUT, optimize=True)
    print(f"wrote {OUT.relative_to(ROOT)}")
