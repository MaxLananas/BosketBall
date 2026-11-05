#!/usr/bin/env python3
"""BosketBall — « pause menu » skin taken from the Ultimate pause-menu kit.

The kit the owner uploaded (`textures.zip`) is a set of Minecraft pause-screen widgets:
wide labelled buttons, a news card and a placeholder logo. The look is unmistakable, so it is
reproduced pixel-for-pixel as a *material* and applied to the server menu that stands in for the
ESC menu (the lobby hub), and to the client-side ESC screen itself.

Measured material (sampled from `options_button.png`, 100x22):

    y=0  transparent      <- 1px breathing room
    y=1  #000000          <- hard black outline
    y=2  #161322          <- inner shade, reads as depth
    y=3  #56506A          <- single light bevel on the TOP edge only
    y=4..18  #262137      <- flat violet body, no gradient
    y=19 #161322
    y=20 #000000
    y=21 transparent
    x:  0 transparent, 1 black, 2 #161322, 3.. body

Panels (`bottom_news.png`) use the same ink but a deeper body: #211B37 lit, #17103 0 shaded,
#494560 hairline. Everything is flat 1x pixel art: no antialiasing, no gradients beyond ordered
dithering, which is what makes it read as vanilla-adjacent rather than as a modern UI.

GEOMETRY CONTRACT — unchanged from `bosket_gui_kit.py`, the client geometry has not moved:

* image x = 0 is the left edge of the container, 176px wide;
* image y = 7 is the top edge of the container, pixels above are the plaque band;
* slot row r / column c has its 16x16 well at (8 + 18c, 25 + 18r);
* texture height = 7 + 18 + 18*rows + 4 (6 rows -> 137).

The only visible substitution: the plaque band now carries the**server logo** instead of a
runtime caption, so `Glyphs.customGuiOverlayTitle(glyph)` is used without a caption for these two
overlays (the logo *is* the title).
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "resourcepack/assets/bosketball/textures/font"
MC_GUI = ROOT / "resourcepack/assets/minecraft/textures/gui"

WIDTH = 176
HEADER_PAD = 7
CONTAINER_TOP = HEADER_PAD
ROW_PITCH = 18
WELL_SIZE = 16
NAV_SLOTS = (46, 49, 52)

# ---------------------------------------------------------------- kit material
INK = (0, 0, 0)                 # hard outline
SHADE = (22, 19, 34)            # inner shade, the "depth" line
BEVEL = (86, 80, 106)           # single top/left light edge
BODY = (38, 33, 55)             # flat violet button body
PANEL_LIT = (41, 32, 72)        # news-card violet
PANEL_MID = (33, 27, 55)
PANEL_DEEP = (23, 16, 48)
HAIRLINE = (73, 69, 96)
WELL_INK = (16, 12, 28)
WELL_TOP = (9, 7, 17)
WELL_LIP = (58, 50, 80)

ACCENTS = {
    "violet": ((150, 138, 205), (203, 196, 235)),
    "gold": ((226, 178, 52), (255, 226, 140)),
    "ember": ((196, 87, 58), (231, 186, 174)),
}


def slot_xy(slot: int) -> tuple[int, int]:
    row, col = divmod(slot, 9)
    return 8 + col * ROW_PITCH, CONTAINER_TOP + 18 + row * ROW_PITCH


def overlay_height(rows: int = 6) -> int:
    return HEADER_PAD + 18 + rows * ROW_PITCH + 4


def _c(rgb, a: int = 255):
    return (rgb[0], rgb[1], rgb[2], a)


def _mix(a, b, t: float):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _hline(img: Image.Image, x0: int, x1: int, y: int, rgb) -> None:
    if 0 <= y < img.height:
        ImageDraw.Draw(img).rectangle([x0, y, x1, y], fill=_c(rgb))


def _vline(img: Image.Image, x: int, y0: int, y1: int, rgb) -> None:
    if 0 <= x < img.width:
        ImageDraw.Draw(img).rectangle([x, y0, x, y1], fill=_c(rgb))


def _body(img: Image.Image) -> None:
    """Deep violet plate: vertical ramp + 8px banding + a 1px deterministic dither."""
    h = img.height
    for y in range(h):
        t = y / (h - 1)
        base = _mix(PANEL_LIT, PANEL_DEEP, t)
        band = 3 if (y // 8) % 2 == 0 else 0
        for x in range(img.width):
            grain = 2 if (x * 7 + y * 11) % 53 == 0 else 0
            img.putpixel((x, y), _c(_mix(base, PANEL_MID, 0.25) if grain else
                                    tuple(min(255, ch + band) for ch in base)))


def _frame(img: Image.Image) -> None:
    w, h = img.width, img.height
    _hline(img, 0, w - 1, 0, INK)
    _hline(img, 0, w - 1, h - 1, INK)
    _vline(img, 0, 0, h - 1, INK)
    _vline(img, w - 1, 0, h - 1, INK)
    _hline(img, 1, w - 2, 1, BEVEL)
    _vline(img, 1, 1, h - 2, BEVEL)
    _hline(img, 1, w - 2, 2, SHADE)
    _vline(img, 2, 2, h - 3, SHADE)
    _hline(img, 1, w - 2, h - 2, INK)
    _vline(img, w - 2, 1, h - 2, INK)
    _hline(img, 2, w - 3, h - 3, SHADE)
    _vline(img, w - 3, 2, h - 3, SHADE)
    # corner studs, the kit's only ornament
    for cx, cy in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        img.putpixel((cx, cy), _c(HAIRLINE))


def _logo_plate(img: Image.Image) -> None:
    """The server logo, framed by the kit's black outline, sits in the plaque band."""
    logo_path = FONT_DIR / "logo_header.png"
    logo = Image.open(logo_path).convert("RGBA")
    x0, y0 = (WIDTH - logo.width) // 2, 2
    x1, y1 = x0 + logo.width - 1, y0 + logo.height - 1
    ImageDraw.Draw(img).rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], fill=_c(INK))
    # the logo plate ships with alpha 232, which would tint the gold over our violet: flatten it
    # pixel-exactly instead, so the wordmark keeps the exact colours it was drawn with
    src = logo.load()
    for ly in range(logo.height):
        for lx in range(logo.width):
            r, g, b, a = src[lx, ly]
            if a > 0:
                img.putpixel((x0 + lx, y0 + ly), (r, g, b, 255))
    _hline(img, x0 - 1, x1 + 1, y1 + 2, SHADE)


def _card(img: Image.Image, rows: int) -> None:
    """Inset card behind the slot grid — the news card's inner panel, at container scale."""
    top = CONTAINER_TOP + 18 - 1
    bottom = slot_xy((rows - 1) * 9)[1] + WELL_SIZE + 3
    x0, x1 = 5, WIDTH - 6
    d = ImageDraw.Draw(img)
    d.rectangle([x0 - 1, top - 1, x1 + 1, bottom + 1], fill=_c(INK))
    d.rectangle([x0, top, x1, bottom], fill=_c(PANEL_DEEP))
    _hline(img, x0, x1, top, HAIRLINE)
    _vline(img, x0, top, bottom, _mix(HAIRLINE, PANEL_DEEP, 0.45))
    _hline(img, x0, x1, bottom, _mix(HAIRLINE, INK, 0.55))
    _vline(img, x1, top, bottom, _mix(HAIRLINE, INK, 0.55))
    # corner brackets, the kit's quiet ornament
    for cx, cy, dx, dy in ((x0 + 1, top + 1, 1, 1), (x1 - 1, top + 1, -1, 1),
                           (x0 + 1, bottom - 1, 1, -1), (x1 - 1, bottom - 1, -1, -1)):
        for i in range(4):
            img.putpixel((cx + dx * i, cy), _c(HAIRLINE))
            img.putpixel((cx, cy + dy * i), _c(HAIRLINE))


def _well(img: Image.Image, slot: int) -> None:
    sx, sy = slot_xy(slot)
    d = ImageDraw.Draw(img)
    d.rectangle([sx - 1, sy - 1, sx + WELL_SIZE, sy + WELL_SIZE], fill=_c(INK))
    d.rectangle([sx, sy, sx + WELL_SIZE - 1, sy + WELL_SIZE - 1], fill=_c(WELL_INK))
    _hline(img, sx, sx + WELL_SIZE - 1, sy, WELL_TOP)
    _vline(img, sx, sy, sy + WELL_SIZE - 1, WELL_TOP)
    _hline(img, sx + 1, sx + WELL_SIZE - 1, sy + WELL_SIZE - 1, WELL_LIP)
    _vline(img, sx + WELL_SIZE - 1, sy + 1, sy + WELL_SIZE - 2, WELL_LIP)
    img.putpixel((sx + WELL_SIZE - 1, sy + WELL_SIZE - 1), _c(_mix(WELL_LIP, PANEL_LIT, 0.5)))


def _pill(img: Image.Image, slot: int, accent_rgb, accent_hi) -> None:
    """A navigation socket drawn with the kit's button frame instead of a plain well."""
    sx, sy = slot_xy(slot)
    x0, y0, x1, y1 = sx - 1, sy - 1, sx + WELL_SIZE, sy + WELL_SIZE
    d = ImageDraw.Draw(img)
    d.rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], fill=_c(INK))
    d.rectangle([x0, y0, x1, y1], fill=_c(SHADE))
    _hline(img, x0 + 1, x1 - 1, y0, BEVEL)
    _vline(img, x0, y0, y1, BEVEL)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=_c(BODY))
    _hline(img, x0 + 1, x1 - 1, y1 - 1, accent_rgb)
    _hline(img, x0 + 2, x1 - 2, y1 - 2, _mix(accent_rgb, BODY, 0.45))
    _vline(img, x1 - 1, y0 + 1, y1 - 1, _mix(accent_rgb, BODY, 0.6))
    img.putpixel((x0 + 1, y1 - 1), _c(accent_hi))
    img.putpixel((x1 - 1, y0 + 1), _c(accent_hi))


def _tray_seam(img: Image.Image, rows: int) -> None:
    if rows < 6:
        return
    row_y = slot_xy((rows - 1) * 9)[1]
    _hline(img, 3, WIDTH - 4, row_y - 5, INK)
    _hline(img, 3, WIDTH - 4, row_y - 4, BEVEL)
    _hline(img, 3, WIDTH - 4, row_y - 3, _mix(BEVEL, PANEL_DEEP, 0.5))
    _hline(img, 3, WIDTH - 4, row_y - 2, PANEL_DEEP)


def build_pause(rows: int = 6, op: bool = False, accent: str | None = None) -> Image.Image:
    accent = accent or ("gold" if op else "violet")
    accent_rgb, accent_hi = ACCENTS[accent]
    img = Image.new("RGBA", (WIDTH, overlay_height(rows)), _c(PANEL_DEEP))
    _body(img)
    _frame(img)
    _card(img, rows)

    if op:
        for slot in (38, 40, 42, 44):
            _well(img, slot)
    _tray_seam(img, rows)

    used = [13] + [19, 21, 23, 25] + [29, 31, 33] + [38, 40, 42] + list(range(45, 54))
    if op:
        used = used + [44]
    for slot in used:
        if slot in NAV_SLOTS:
            continue
        _well(img, slot)
    for slot in NAV_SLOTS:
        _pill(img, slot, accent_rgb, accent_hi)

    # the logo is stamped last: nothing may ever cover the wordmark
    _logo_plate(img)

    # accent ticks on the frame, the only colour besides the logo
    mid = img.height // 2
    for x in (1, WIDTH - 2):
        _vline(img, x, mid - 5, mid + 4, accent_rgb)
    img.putpixel((1, mid - 6), _c(accent_hi))
    img.putpixel((WIDTH - 2, mid + 5), _c(_mix(accent_rgb, INK, 0.35)))
    return img


# ------------------------------------------------------- client-side ESC screen
KIT_SOURCE = ROOT / "tools/pause_kit_source"
# the kit's own two-frame button: 100x22, flat interior, no distortion when nine-sliced to 200x20
KIT_BUTTON = KIT_SOURCE / "options_button.png"
KIT_BUTTON_HOVER = KIT_SOURCE / "back_button_yellow.png"
KIT_NEWS = KIT_SOURCE / "bottom_news.png"

YELLOW = (255, 188, 0)          # sampled from back_button_yellow.png, the kit's hover ink
LABEL_INK = {(255, 255, 255), (64, 65, 66)}   # white label + its 1px shadow


def kit_palette() -> dict:
    """Body/shade/bevel read at their known offsets in the kit's button, never guessed.

    `options_button.png` is 100x22: (50, 10) is flat body, (50, 3) is the single top bevel,
    (50, 2) is the inner shade. Sampling beats a frequency count here, because the largest colour
    cluster includes the white label ink.
    """
    img = Image.open(KIT_BUTTON).convert("RGBA")
    body = img.getpixel((50, 10))[:3]
    bevel = img.getpixel((50, 3))[:3]
    shade = img.getpixel((50, 2))[:3]
    assert body != bevel != shade, "unexpected kit button layout"
    return {"body": body, "bevel": bevel, "shade": shade}


def kit_button(variant: str = "normal") -> Image.Image:
    """The kit's `options_button.png`, label pixels stripped, ready to be nine-sliced.

    Pass 1 — vanilla draws the button label itself, so the baked "Options..." must go: every label
    pixel (white ink + its 1px shadow) is repainted with the kit's own body colour, which leaves the
    black outline, the inner shade and the single top bevel untouched.
    Pass 2 — the state is expressed with the kit's own hover ink (the #FFBC00 sampled from
    `back_button_yellow.png`) painted onto that top bevel, so hover reads instantly and nothing
    else moves.
    """
    pal = kit_palette()
    body, bevel, shade = pal["body"], pal["bevel"], pal["shade"]
    img = Image.open(KIT_BUTTON).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a != 255:
                continue
            if (r, g, b) in LABEL_INK:
                px[x, y] = (*body, 255)
    if variant == "normal":
        return img
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a != 255:
                continue
            if variant == "highlighted" and (r, g, b) == tuple(bevel):
                px[x, y] = (*YELLOW, 255)
            elif variant == "highlighted" and (r, g, b) != (0, 0, 0) and y <= 4:
                px[x, y] = (*_mix((r, g, b), YELLOW, 0.35), 255)
            elif variant == "disabled":
                if (r, g, b) == tuple(body):
                    px[x, y] = (*_mix(body, INK, 0.35), 255)
                elif (r, g, b) == tuple(bevel):
                    px[x, y] = (*_mix(bevel, INK, 0.45), 255)
    return img


def kit_background_colors() -> tuple[tuple[int, int, int], tuple[int, int, int]]:
    """The screen tint is the news card's own two violets, so the ESC backdrop matches the panels."""
    from collections import Counter
    img = Image.open(KIT_NEWS).convert("RGBA")
    counts = Counter(p[:3] for p in img.getdata() if p[3] == 255 and sum(p[:3]) > 60)
    top = counts.most_common(3)
    lit = max((c for c, _ in top), key=sum)
    deep = min((c for c, _ in top), key=sum)
    return lit, deep


def build_menu_background(size: int = 16) -> Image.Image:
    """Seamless tile behind the vanilla ESC screen: the kit's news-card violets."""
    lit, deep = kit_background_colors()
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            base = _mix(lit, deep, (((x % 8) + (y % 8)) / 14))
            grain = 3 if (x * 7 + y * 5) % 61 == 0 else 0
            img.putpixel((x, y), _c(tuple(min(255, ch + grain) for ch in base)))
    for x in range(size):
        y = (x * 5) % size
        img.putpixel((x, y), _c(lit))
        img.putpixel(((x + 1) % size, y), _c(_mix(lit, deep, 0.55)))
    return img


BUTTON_MC_META = """{{
  "gui": {{
    "scaling": {{
      "type": "nine_slice",
      "width": {w},
      "height": {h},
      "border": {{
        "left": 3,
        "top": 3,
        "right": 3,
        "bottom": 3
      }},
      "stretch_inner": true
    }}
  }}
}}
"""


def write_client_skin(out_dir: Path = MC_GUI) -> list[Path]:
    """Ship the ESC screen skin into the pack: background tiles + the kit's own button frame."""
    out_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for name in ("menu_background.png", "inworld_menu_background.png"):
        tile = build_menu_background(16)
        tile.save(out_dir / name)
        written.append(out_dir / name)

    widget = out_dir / "sprites" / "widget"
    widget.mkdir(parents=True, exist_ok=True)
    for variant, suffix in (("normal", ""), ("highlighted", "_highlighted"), ("disabled", "_disabled")):
        img = kit_button(variant)
        path = widget / f"button{suffix}.png"
        img.save(path)
        written.append(path)
        meta = widget / f"button{suffix}.png.mcmeta"
        meta.write_text(BUTTON_MC_META.format(w=img.width, h=img.height), encoding="utf-8")
        written.append(meta)
    return written


def write_overlays(font_dir: Path = FONT_DIR) -> list[Path]:
    written = []
    for op, name in ((False, "gui_overlay_hub.png"), (True, "gui_overlay_hub_op.png")):
        img = build_pause(6, op)
        img.save(font_dir / name)
        written.append(font_dir / name)
    return written


if __name__ == "__main__":
    for p in write_overlays():
        im = Image.open(p)
        print(f"overlay   {p.name:26s} {im.width}x{im.height}")
    for p in write_client_skin():
        if p.suffix != ".png":
            print(f"client    {p.relative_to(ROOT)!s:56s} (meta)")
            continue
        im = Image.open(p)
        print(f"client    {p.relative_to(ROOT)!s:56s} {im.width}x{im.height}")
