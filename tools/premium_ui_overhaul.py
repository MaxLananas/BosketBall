#!/usr/bin/env python3
"""
BosketBall premium item-icon overhaul.

The icons intentionally follow the visual grammar of the supplied references:
chunky near-black outlines, saturated two-tone fills, hard white highlights,
simple silhouettes that remain legible when Minecraft renders a 32x32 item
texture inside a 16x16 GUI slot, plus colored collector badges for chat tags.

This module is a final override layer in the resource-pack pipeline.  Keeping
it separate from the older generators makes the art direction reproducible
without changing item IDs, models, slot coordinates or server behaviour.
"""

from __future__ import annotations

import colorsys
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

S = 4
PX = 32 * S

INK = (13, 16, 24, 255)
INK_SOFT = (35, 42, 56, 255)
WHITE = (255, 255, 255, 255)
PAPER = (248, 251, 255, 255)
SILVER = (190, 202, 218, 255)
STEEL = (105, 122, 143, 255)
ORANGE = (255, 128, 18, 255)
ORANGE_D = (197, 72, 0, 255)
GOLD = (255, 194, 35, 255)
GOLD_D = (198, 124, 0, 255)
RED = (239, 55, 64, 255)
RED_D = (160, 25, 42, 255)
GREEN = (48, 205, 91, 255)
GREEN_D = (13, 132, 58, 255)
SKY = (48, 169, 255, 255)
SKY_D = (19, 91, 190, 255)
CYAN = (45, 226, 230, 255)
PURPLE = (139, 91, 246, 255)
PINK = (255, 91, 170, 255)
BROWN = (139, 82, 43, 255)
BROWN_D = (82, 45, 25, 255)


def _mix(a, b, t: float):
    return tuple(max(0, min(255, round(a[i] * (1.0 - t) + b[i] * t))) for i in range(3)) + (255,)


def _alpha(color, alpha: int):
    return (color[0], color[1], color[2], alpha)


def _canvas() -> Image.Image:
    return Image.new("RGBA", (PX, PX), (0, 0, 0, 0))


def _finish(im: Image.Image) -> Image.Image:
    """Adds the reference pack's short hard drop shadow and downsamples to 32x32."""
    alpha = im.getchannel("A").point(lambda a: 68 if a > 28 else 0)
    shadow = Image.new("RGBA", im.size, (5, 7, 12, 255))
    shadow.putalpha(alpha)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.alpha_composite(shadow, (0, 5))
    out.alpha_composite(im)
    return out.resize((32, 32), Image.Resampling.BOX)


def _vertical_gradient(size: tuple[int, int], top, bottom) -> Image.Image:
    w, h = size
    strip = Image.new("RGBA", (1, h))
    for y in range(h):
        strip.putpixel((0, y), _mix(top, bottom, y / max(1, h - 1)))
    return strip.resize(size)


def _masked_gradient(im: Image.Image, box, mask_draw, top, bottom):
    x0, y0, x1, y1 = [int(v) for v in box]
    w, h = max(1, x1 - x0 + 1), max(1, y1 - y0 + 1)
    layer = _vertical_gradient((w, h), top, bottom)
    mask = Image.new("L", (w, h), 0)
    mask_draw(ImageDraw.Draw(mask), w, h)
    layer.putalpha(ImageChops.multiply(layer.getchannel("A"), mask))
    im.alpha_composite(layer, (x0, y0))


def _star_points(cx: float, cy: float, r: float, inner_ratio: float = 0.48, points: int = 5):
    pts = []
    for i in range(points * 2):
        radius = r if i % 2 == 0 else r * inner_ratio
        ang = -math.pi / 2 + i * math.pi / points
        pts.append((cx + math.cos(ang) * radius, cy + math.sin(ang) * radius))
    return pts


def _draw_star(d: ImageDraw.ImageDraw, cx, cy, r, fill=GOLD, outline=INK, width=6):
    d.polygon(_star_points(cx, cy, r), fill=fill, outline=outline, width=width)
    d.polygon(_star_points(cx - r * 0.12, cy - r * 0.16, r * 0.36), fill=_alpha(WHITE, 120))


def _draw_heart(d: ImageDraw.ImageDraw, cx, cy, w, h, fill=RED):
    def heart(scale, color):
        ww, hh = w * scale, h * scale
        d.ellipse([cx - ww / 2, cy - hh / 2, cx, cy + hh * 0.05], fill=color)
        d.ellipse([cx, cy - hh / 2, cx + ww / 2, cy + hh * 0.05], fill=color)
        d.polygon([(cx - ww / 2 + 3, cy - 2), (cx + ww / 2 - 3, cy - 2), (cx, cy + hh / 2)], fill=color)

    heart(1.13, INK)
    heart(1.0, fill)
    d.ellipse([cx - w * 0.32, cy - h * 0.34, cx - w * 0.12, cy - h * 0.14], fill=_alpha(WHITE, 150))


def _shield_points(cx, cy, w, h):
    return [
        (cx, cy - h / 2),
        (cx + w / 2, cy - h * 0.35),
        (cx + w * 0.43, cy + h * 0.12),
        (cx, cy + h / 2),
        (cx - w * 0.43, cy + h * 0.12),
        (cx - w / 2, cy - h * 0.35),
    ]


def _draw_shield(d, cx, cy, w, h, fill=SKY, inner=PAPER):
    d.polygon(_shield_points(cx, cy, w, h), fill=fill, outline=INK, width=7)
    d.polygon(_shield_points(cx, cy - 2, w * 0.72, h * 0.72), outline=inner, width=5)


def _draw_check(d, cx, cy, scale=1.0, color=GREEN):
    pts = [(cx - 34 * scale, cy + 2 * scale), (cx - 10 * scale, cy + 27 * scale), (cx + 38 * scale, cy - 29 * scale)]
    d.line(pts, fill=INK, width=max(7, int(22 * scale)), joint="curve")
    d.line(pts, fill=color, width=max(5, int(13 * scale)), joint="curve")


def _draw_cross(d, cx, cy, r=32, color=RED):
    for a, b in [((cx - r, cy - r), (cx + r, cy + r)), ((cx + r, cy - r), (cx - r, cy + r))]:
        d.line([a, b], fill=INK, width=22)
        d.line([a, b], fill=color, width=12)


def _basketball(im: Image.Image, cx: int, cy: int, r: int, top=(255, 170, 54, 255), bottom=(218, 82, 0, 255), seam=(69, 31, 16, 255)):
    size = r * 2
    layer = _vertical_gradient((size, size), top, bottom)
    d = ImageDraw.Draw(layer)
    # Classic seams, clipped by the circular mask below.
    d.line([(r, 2), (r, size - 3)], fill=seam, width=max(4, r // 7))
    d.line([(2, r), (size - 3, r)], fill=seam, width=max(4, r // 7))
    d.arc([-int(r * 0.72), 2, int(r * 0.78), size - 3], start=-72, end=72, fill=seam, width=max(4, r // 7))
    d.arc([int(r * 1.22), 2, int(r * 2.72), size - 3], start=108, end=252, fill=seam, width=max(4, r // 7))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    layer.putalpha(mask)
    im.alpha_composite(layer, (cx - r, cy - r))
    d = ImageDraw.Draw(im)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=INK, width=max(5, r // 6))
    shine = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(shine).ellipse([cx - r * 0.58, cy - r * 0.66, cx - r * 0.18, cy - r * 0.28], fill=(255, 255, 255, 105))
    im.alpha_composite(shine)


def _hoop(im: Image.Image, cx=64, top=8, with_ball=False, ball_y=48):
    d = ImageDraw.Draw(im)
    # Mount and chunky arena backboard.
    d.rectangle([cx - 7, top - 4, cx + 7, top + 9], fill=STEEL, outline=INK, width=4)
    d.rounded_rectangle([cx - 48, top + 5, cx + 48, top + 53], radius=5, fill=PAPER, outline=INK, width=7)
    d.rounded_rectangle([cx - 39, top + 13, cx + 39, top + 45], radius=3, outline=SKY_D, width=5)
    d.rectangle([cx - 20, top + 25, cx + 20, top + 48], outline=ORANGE_D, width=6)
    if with_ball:
        _basketball(im, cx, ball_y, 21)
        d = ImageDraw.Draw(im)
    rim_y = top + 55
    # Net first, then the rim lip in front of it.
    top_pts = [(cx - 26 + i * 13, rim_y + 5) for i in range(5)]
    bot_pts = [(cx - 18 + i * 12, rim_y + 43) for i in range(4)]
    for i, p in enumerate(top_pts):
        for q in (bot_pts[max(0, i - 1)], bot_pts[min(3, i)]):
            d.line([p, q], fill=INK_SOFT, width=6)
            d.line([p, q], fill=PAPER, width=3)
    d.line([(cx - 23, rim_y + 24), (cx + 23, rim_y + 24)], fill=INK_SOFT, width=5)
    d.line([(cx - 23, rim_y + 24), (cx + 23, rim_y + 24)], fill=PAPER, width=2)
    d.ellipse([cx - 29, rim_y - 7, cx + 29, rim_y + 8], fill=(255, 82, 62, 255), outline=INK, width=6)
    d.arc([cx - 22, rim_y - 4, cx + 22, rim_y + 6], start=15, end=165, fill=WHITE, width=3)


def _arrow(im: Image.Image, direction: str, fill=GREEN):
    d = ImageDraw.Draw(im)
    if direction == "right":
        pts = [(18, 45), (70, 45), (70, 24), (111, 64), (70, 104), (70, 83), (18, 83)]
    elif direction == "up":
        pts = [(45, 110), (45, 58), (24, 58), (64, 17), (104, 58), (83, 58), (83, 110)]
    elif direction == "down":
        pts = [(45, 18), (83, 18), (83, 70), (104, 70), (64, 111), (24, 70), (45, 70)]
    else:
        pts = [(110, 45), (58, 45), (58, 24), (17, 64), (58, 104), (58, 83), (110, 83)]
    d.polygon(pts, fill=fill, outline=INK, width=7)
    # Hard top-light copied from the chunky reference arrows.
    if direction in ("left", "right"):
        d.line([(28, 52), (66, 52)] if direction == "right" else [(62, 52), (100, 52)], fill=_alpha(WHITE, 145), width=6)


def _coin(im: Image.Image, cx, cy, r, symbol="star"):
    _masked_gradient(
        im,
        (cx - r, cy - r, cx + r, cy + r),
        lambda d, w, h: d.ellipse([0, 0, w - 1, h - 1], fill=255),
        (255, 226, 92, 255),
        (225, 132, 0, 255),
    )
    d = ImageDraw.Draw(im)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=INK, width=6)
    d.ellipse([cx - r * 0.68, cy - r * 0.68, cx + r * 0.68, cy + r * 0.68], outline=(146, 78, 0, 255), width=4)
    if symbol == "star":
        _draw_star(d, cx, cy, r * 0.38, fill=(255, 244, 166, 255), outline=(146, 78, 0, 255), width=3)
    d.ellipse([cx - r * 0.55, cy - r * 0.62, cx - r * 0.18, cy - r * 0.30], fill=_alpha(WHITE, 125))


def _gem(im: Image.Image, cx, cy, r, top=PINK, bottom=RED_D):
    pts = [(cx - r, cy - r * 0.45), (cx - r * 0.55, cy - r), (cx + r * 0.55, cy - r), (cx + r, cy - r * 0.45), (cx, cy + r)]
    _masked_gradient(im, (cx - r, cy - r, cx + r, cy + r), lambda d, w, h: d.polygon([(0, h * 0.275), (w * 0.225, 0), (w * 0.775, 0), (w, h * 0.275), (w / 2, h)], fill=255), top, bottom)
    d = ImageDraw.Draw(im)
    d.polygon(pts, outline=INK, width=7)
    d.line([(cx - r, cy - r * 0.45), (cx + r, cy - r * 0.45)], fill=INK, width=5)
    d.line([(cx - r * 0.55, cy - r), (cx - r * 0.20, cy - r * 0.45), (cx, cy + r)], fill=_alpha(WHITE, 155), width=4)
    d.line([(cx + r * 0.55, cy - r), (cx + r * 0.20, cy - r * 0.45), (cx, cy + r)], fill=_alpha(INK, 150), width=4)
    d.polygon([(cx - r * 0.72, cy - r * 0.68), (cx - r * 0.38, cy - r * 0.82), (cx - r * 0.28, cy - r * 0.48)], fill=_alpha(WHITE, 150))


def _sparkles(d, points, color=GOLD, r=8):
    for x, y in points:
        d.polygon([(x, y - r), (x + r * 0.28, y - r * 0.28), (x + r, y), (x + r * 0.28, y + r * 0.28), (x, y + r), (x - r * 0.28, y + r * 0.28), (x - r, y), (x - r * 0.28, y - r * 0.28)], fill=color, outline=INK, width=2)


def _jersey(im: Image.Image, fill=PURPLE, trim=GOLD, number=True):
    d = ImageDraw.Draw(im)
    pts = [(38, 18), (55, 28), (64, 34), (73, 28), (90, 18), (103, 48), (88, 55), (88, 108), (40, 108), (40, 55), (25, 48)]
    d.polygon(pts, fill=fill, outline=INK, width=7)
    d.arc([50, 18, 78, 48], start=0, end=180, fill=trim, width=7)
    d.line([(40, 55), (25, 48)], fill=trim, width=7)
    d.line([(88, 55), (103, 48)], fill=trim, width=7)
    d.rectangle([40, 99, 88, 108], fill=trim)
    if number:
        # A bold block "1" reads at 16px without needing a font.
        d.polygon([(58, 61), (70, 53), (70, 90), (61, 90), (61, 67), (54, 72)], fill=PAPER, outline=INK, width=3)
    d.line([(47, 42), (47, 96)], fill=_alpha(WHITE, 95), width=6)


def _robot(im: Image.Image, enabled: bool):
    d = ImageDraw.Draw(im)
    accent = CYAN if enabled else RED
    d.line([(64, 12), (64, 30)], fill=INK, width=12)
    d.line([(64, 12), (64, 30)], fill=SILVER, width=6)
    d.ellipse([55, 3, 73, 21], fill=accent, outline=INK, width=5)
    d.rectangle([15, 55, 27, 82], fill=STEEL, outline=INK, width=5)
    d.rectangle([101, 55, 113, 82], fill=STEEL, outline=INK, width=5)
    d.rounded_rectangle([25, 28, 103, 108], radius=14, fill=(203, 214, 228, 255), outline=INK, width=7)
    d.rounded_rectangle([36, 45, 92, 76], radius=8, fill=(14, 22, 34, 255), outline=INK, width=4)
    if enabled:
        d.ellipse([46, 54, 58, 67], fill=CYAN)
        d.ellipse([70, 54, 82, 67], fill=CYAN)
        d.arc([48, 78, 80, 101], start=15, end=165, fill=GREEN_D, width=6)
        _draw_check(d, 96, 99, 0.36, GREEN)
    else:
        for cx in (52, 76):
            d.line([(cx - 6, 54), (cx + 6, 67)], fill=RED, width=5)
            d.line([(cx + 6, 54), (cx - 6, 67)], fill=RED, width=5)
        d.line([(49, 93), (79, 93)], fill=RED_D, width=6)
        _draw_cross(d, 97, 99, 13, RED)
    d.line([(35, 36), (86, 36)], fill=_alpha(WHITE, 130), width=5)


def _dunker(im: Image.Image, jersey, effect: str):
    d = ImageDraw.Draw(im)
    # Right-side hoop behind the athlete.
    d.rounded_rectangle([78, 7, 122, 43], radius=4, fill=PAPER, outline=INK, width=6)
    d.rectangle([91, 19, 111, 39], outline=ORANGE_D, width=5)
    d.ellipse([87, 40, 117, 52], fill=(255, 82, 62, 255), outline=INK, width=5)
    for x0, x1 in [(91, 96), (98, 101), (105, 106), (112, 110)]:
        d.line([(x0, 49), (x1, 72)], fill=INK_SOFT, width=5)
        d.line([(x0, 49), (x1, 72)], fill=PAPER, width=2)
    skin = (173, 105, 64, 255)
    # Motion effects behind the body.
    if effect == "spin":
        d.arc([12, 25, 91, 104], start=35, end=310, fill=CYAN, width=9)
        d.polygon([(83, 35), (98, 45), (79, 52)], fill=CYAN, outline=INK, width=3)
    elif effect == "windmill":
        d.arc([38, 3, 112, 72], start=100, end=430, fill=SKY, width=9)
    elif effect == "meteor":
        d.polygon([(18, 12), (48, 20), (30, 32), (58, 35), (28, 55), (42, 29), (14, 40), (30, 22)], fill=(255, 92, 24, 255), outline=INK, width=4)
        d.polygon([(35, 23), (58, 32), (38, 44)], fill=GOLD)
    elif effect == "tomahawk":
        d.arc([28, 0, 116, 76], start=105, end=355, fill=RED, width=10)
    # Legs and shoes.
    leg_sets = {
        "between": [([(48, 78), (29, 94), (16, 91)], [(58, 78), (75, 93), (90, 89)])],
        "spin": [([(47, 79), (28, 90), (17, 106)], [(59, 79), (76, 88), (91, 82)])],
    }.get(effect, [([(47, 78), (31, 92), (18, 108)], [(58, 78), (73, 91), (88, 100)])])[0]
    for leg in leg_sets:
        d.line(leg, fill=INK, width=15, joint="curve")
        d.line(leg, fill=skin, width=8, joint="curve")
        ex, ey = leg[-1]
        d.line([(ex - 4, ey), (ex + 13, ey)], fill=PAPER, width=8)
        d.line([(ex - 4, ey + 4), (ex + 13, ey + 4)], fill=RED, width=3)
    # Shorts, torso, head.
    d.polygon([(34, 67), (67, 64), (72, 84), (57, 84), (51, 76), (43, 85), (31, 82)], fill=(28, 38, 56, 255), outline=INK, width=5)
    d.polygon([(36, 38), (63, 34), (68, 68), (34, 69), (29, 49)], fill=jersey, outline=INK, width=6)
    d.ellipse([38, 12, 65, 39], fill=skin, outline=INK, width=6)
    d.arc([39, 9, 64, 29], start=180, end=360, fill=INK, width=7)
    # Arms and ball placement per dunk.
    ball = {
        "between": (53, 89, 17),
        "windmill": (83, 20, 18),
        "meteor": (82, 31, 20),
        "tomahawk": (84, 24, 19),
        "spin": (82, 32, 18),
    }.get(effect, (84, 28, 18))
    arm_end = (ball[0] - 8, ball[1] + 4)
    d.line([(61, 43), (72, 34), arm_end], fill=INK, width=14, joint="curve")
    d.line([(61, 43), (72, 34), arm_end], fill=skin, width=7, joint="curve")
    d.line([(37, 45), (22, 59), (13, 72)], fill=INK, width=13, joint="curve")
    d.line([(37, 45), (22, 59), (13, 72)], fill=skin, width=7, joint="curve")
    if effect == "between":
        d.line([(61, 43), (58, 66), (55, 80)], fill=INK, width=13)
        d.line([(61, 43), (58, 66), (55, 80)], fill=skin, width=7)
    _basketball(im, ball[0], ball[1], ball[2])
    d = ImageDraw.Draw(im)
    if effect == "meteor":
        _sparkles(d, [(34, 61), (104, 82), (68, 12)], GOLD, 7)


def build_premium_icon_sprites() -> dict[str, Image.Image]:
    sprites: dict[str, Image.Image] = {}

    def add(name, painter):
        im = _canvas()
        painter(im)
        sprites[name] = _finish(im)

    add("icon_arena", lambda im: _arena(im))
    add("icon_back", lambda im: _arrow(im, "left", ORANGE))
    add("icon_next", lambda im: _arrow(im, "right", GREEN))
    add("icon_prev", lambda im: _arrow(im, "left", SKY))
    add("icon_book", lambda im: _book(im))
    add("icon_bot_on", lambda im: _robot(im, True))
    add("icon_bot_off", lambda im: _robot(im, False))
    add("icon_check", lambda im: _check_badge(im))
    add("icon_chest", lambda im: _chest(im))
    add("icon_coin_stack", lambda im: _coin_stack(im))
    add("icon_crossover", lambda im: _crossover(im))
    add("icon_defense", lambda im: _defense(im))
    add("icon_discord", lambda im: _discord(im))
    add("icon_dunk", lambda im: _hoop(im, 64, 7, True, 50))
    add("icon_friends", lambda im: _friends(im))
    add("icon_gem_ruby", lambda im: _gem(im, 64, 65, 47, (255, 126, 166, 255), (191, 18, 58, 255)))
    add("icon_history", lambda im: _history(im))
    add("icon_leave", lambda im: _leave(im))
    add("icon_pass", lambda im: _pass(im))
    add("icon_podium", lambda im: _podium(im, trophy=True))
    add("icon_portal", lambda im: _portal(im))
    add("icon_settings", lambda im: _settings(im))
    add("icon_shelves", lambda im: _shelves(im))
    add("icon_shop", lambda im: _shop(im))
    add("icon_star_shield", lambda im: _star_shield(im))
    add("icon_tab", lambda im: _tab(im))
    add("icon_tags", lambda im: _tags(im))

    add("dunk_tomahawk", lambda im: _dunker(im, RED, "tomahawk"))
    add("dunk_windmill", lambda im: _dunker(im, SKY, "windmill"))
    add("dunk_between_legs", lambda im: _dunker(im, PURPLE, "between"))
    add("dunk_spin_360", lambda im: _dunker(im, CYAN, "spin"))
    add("dunk_meteor_jam", lambda im: _dunker(im, ORANGE, "meteor"))

    add("tactics_playbook", lambda im: _clipboard(im, tactics=True))
    add("court_wand", lambda im: _court_wand(im))
    add("prop_guide_clipboard", lambda im: _clipboard(im, tactics=False))
    add("prop_locker_jersey", lambda im: _jersey(im, (124, 78, 246, 255), GOLD, True))
    add("prop_play_emblem", lambda im: _play_emblem(im))
    add("prop_podium_cup", lambda im: _trophy(im))
    add("prop_shop_crate", lambda im: _shop_crate(im))
    add("prop_social_megaphone", lambda im: _megaphone(im))
    add("prop_tag_plaque", lambda im: _tag_plaque(im))
    return sprites


def _arena(im):
    d = ImageDraw.Draw(im)
    for x in (18, 110):
        d.line([(x, 45), (x, 18)], fill=INK, width=7)
        d.rectangle([x - 10, 7, x + 10, 22], fill=GOLD, outline=INK, width=4)
    d.polygon([(12, 48), (116, 48), (104, 72), (24, 72)], fill=PURPLE, outline=INK, width=7)
    d.polygon([(20, 70), (108, 70), (98, 94), (30, 94)], fill=SKY, outline=INK, width=6)
    d.polygon([(31, 92), (97, 92), (108, 112), (20, 112)], fill=(219, 138, 62, 255), outline=INK, width=6)
    d.ellipse([50, 91, 78, 110], outline=PAPER, width=4)
    for x in range(25, 108, 12):
        d.rectangle([x, 54, x + 5, 59], fill=PAPER)
    d.line([(18, 51), (110, 51)], fill=_alpha(WHITE, 120), width=4)


def _book(im):
    d = ImageDraw.Draw(im)
    d.polygon([(64, 34), (88, 22), (112, 29), (112, 101), (88, 94), (64, 104), (40, 94), (16, 101), (16, 29), (40, 22)], fill=SKY_D, outline=INK, width=7)
    d.polygon([(64, 42), (85, 31), (104, 36), (104, 92), (85, 86), (64, 96), (43, 86), (24, 92), (24, 36), (43, 31)], fill=PAPER, outline=INK, width=4)
    d.line([(64, 42), (64, 96)], fill=STEEL, width=4)
    for y in (50, 62, 74):
        d.line([(33, y), (55, y - 3)], fill=STEEL, width=4)
        d.line([(73, y - 3), (95, y)], fill=STEEL, width=4)
    d.polygon([(83, 89), (95, 86), (95, 111), (89, 103), (83, 111)], fill=RED, outline=INK, width=3)
    _basketball(im, 64, 43, 16)


def _check_badge(im):
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([14, 14, 114, 114], radius=18, fill=GREEN, outline=INK, width=8)
    d.rounded_rectangle([23, 23, 105, 105], radius=13, outline=PAPER, width=5)
    _draw_check(d, 64, 64, 1.0, PAPER)
    d.line([(25, 27), (75, 27)], fill=_alpha(WHITE, 140), width=6)


def _chest(im):
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([16, 45, 112, 108], radius=8, fill=BROWN, outline=INK, width=7)
    d.pieslice([16, 12, 112, 82], start=180, end=360, fill=(174, 105, 54, 255), outline=INK, width=7)
    d.line([(18, 61), (110, 61)], fill=INK, width=6)
    for x in (31, 91):
        d.rectangle([x, 28, x + 10, 106], fill=GOLD, outline=INK, width=4)
    d.rectangle([52, 55, 76, 84], fill=GOLD, outline=INK, width=6)
    d.ellipse([59, 64, 69, 74], fill=INK)
    d.line([(64, 72), (64, 79)], fill=INK, width=4)
    _coin(im, 91, 31, 17)
    _sparkles(ImageDraw.Draw(im), [(24, 25)], GOLD, 8)


def _coin_stack(im):
    d = ImageDraw.Draw(im)
    for y in (85, 70, 55):
        d.ellipse([18, y, 78, y + 28], fill=GOLD, outline=INK, width=6)
        d.arc([24, y + 4, 72, y + 23], start=15, end=165, fill=(146, 78, 0, 255), width=3)
    _coin(im, 89, 48, 31)


def _crossover(im):
    d = ImageDraw.Draw(im)
    path = [(18, 28), (45, 28), (29, 57), (58, 57), (42, 88), (76, 88)]
    d.line(path, fill=INK, width=16, joint="curve")
    d.line(path, fill=CYAN, width=8, joint="curve")
    d.polygon([(72, 76), (96, 88), (72, 100)], fill=CYAN, outline=INK, width=4)
    _basketball(im, 78, 40, 30)
    d = ImageDraw.Draw(im)
    d.polygon([(22, 104), (66, 104), (76, 114), (18, 114)], fill=PAPER, outline=INK, width=5)
    d.line([(24, 110), (72, 110)], fill=RED, width=4)


def _defense(im):
    d = ImageDraw.Draw(im)
    _draw_shield(d, 64, 64, 92, 104, SKY, PAPER)
    _basketball(im, 64, 63, 29)
    d = ImageDraw.Draw(im)
    d.line([(39, 27), (64, 18), (89, 27)], fill=_alpha(WHITE, 130), width=5)


def _discord(im):
    d = ImageDraw.Draw(im)
    # Chunky original chat/controller mascot in Discord blurple.
    d.rounded_rectangle([16, 31, 112, 99], radius=25, fill=(88, 101, 242, 255), outline=INK, width=7)
    d.polygon([(43, 94), (66, 94), (51, 114)], fill=(88, 101, 242, 255), outline=INK, width=5)
    d.ellipse([37, 52, 57, 76], fill=PAPER)
    d.ellipse([71, 52, 91, 76], fill=PAPER)
    d.ellipse([44, 62, 53, 72], fill=INK)
    d.ellipse([78, 62, 87, 72], fill=INK)
    d.arc([43, 68, 85, 94], start=15, end=165, fill=PAPER, width=5)
    d.line([(28, 39), (72, 36)], fill=_alpha(WHITE, 120), width=6)


def _friends(im):
    d = ImageDraw.Draw(im)
    # Back friend.
    d.ellipse([63, 24, 99, 60], fill=(239, 184, 128, 255), outline=INK, width=6)
    d.pieslice([55, 54, 107, 116], start=180, end=360, fill=SKY, outline=INK, width=6)
    # Front friend.
    d.ellipse([27, 30, 69, 72], fill=(165, 100, 61, 255), outline=INK, width=6)
    d.arc([28, 25, 68, 51], start=180, end=360, fill=INK, width=8)
    d.pieslice([13, 66, 83, 128], start=180, end=360, fill=ORANGE, outline=INK, width=6)
    _draw_heart(d, 91, 91, 30, 28, RED)


def _history(im):
    d = ImageDraw.Draw(im)
    d.arc([18, 18, 110, 110], start=35, end=330, fill=INK, width=17)
    d.arc([18, 18, 110, 110], start=35, end=330, fill=SKY, width=9)
    d.polygon([(25, 28), (48, 20), (38, 45)], fill=SKY, outline=INK, width=4)
    d.ellipse([31, 31, 97, 97], fill=PAPER, outline=INK, width=6)
    for ang in (0, 90, 180, 270):
        x = 64 + math.cos(math.radians(ang)) * 24
        y = 64 + math.sin(math.radians(ang)) * 24
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=STEEL)
    d.line([(64, 64), (64, 43)], fill=INK, width=7)
    d.line([(64, 64), (79, 73)], fill=ORANGE_D, width=7)
    d.ellipse([59, 59, 69, 69], fill=GOLD, outline=INK, width=3)


def _leave(im):
    d = ImageDraw.Draw(im)
    d.polygon([(29, 15), (76, 24), (76, 112), (29, 103)], fill=(114, 72, 43, 255), outline=INK, width=7)
    d.polygon([(38, 27), (67, 33), (67, 100), (38, 94)], fill=(166, 104, 58, 255), outline=INK, width=4)
    d.ellipse([58, 62, 68, 72], fill=GOLD, outline=INK, width=3)
    pts = [(61, 49), (91, 49), (91, 36), (114, 63), (91, 90), (91, 77), (61, 77)]
    d.polygon(pts, fill=RED, outline=INK, width=6)


def _pass(im):
    d = ImageDraw.Draw(im)
    for y, x1 in [(35, 47), (58, 61), (81, 47), (102, 34)]:
        d.line([(10, y), (x1, y)], fill=INK, width=13)
        d.line([(10, y), (x1, y)], fill=CYAN, width=6)
    _basketball(im, 83, 64, 36)
    d = ImageDraw.Draw(im)
    d.polygon([(101, 49), (118, 64), (101, 79)], fill=GREEN, outline=INK, width=4)


def _podium(im, trophy=False):
    d = ImageDraw.Draw(im)
    if trophy:
        _trophy_shape(d, 64, 38, 0.62)
    blocks = [(46, 65, 82, 112, GOLD), (14, 80, 46, 112, SILVER), (82, 88, 114, 112, (205, 128, 72, 255))]
    for x0, y0, x1, y1, c in blocks:
        d.rectangle([x0, y0, x1, y1], fill=c, outline=INK, width=6)
        d.line([(x0 + 4, y0 + 4), (x1 - 4, y0 + 4)], fill=_alpha(WHITE, 130), width=4)
    # Block numerals as simple pixel strokes.
    d.line([(64, 76), (64, 101)], fill=INK, width=6)
    d.line([(24, 88), (35, 88), (24, 103), (36, 103)], fill=INK, width=5)
    d.arc([91, 91, 106, 101], start=270, end=130, fill=INK, width=4)
    d.arc([91, 98, 106, 109], start=270, end=160, fill=INK, width=4)


def _portal(im):
    d = ImageDraw.Draw(im)
    d.ellipse([24, 8, 104, 120], fill=PURPLE, outline=INK, width=8)
    d.ellipse([36, 20, 92, 108], fill=(43, 24, 91, 255), outline=CYAN, width=5)
    d.arc([43, 32, 86, 96], start=80, end=330, fill=PINK, width=7)
    d.arc([50, 45, 78, 84], start=250, end=130, fill=CYAN, width=6)
    pts = [(8, 51), (47, 51), (47, 38), (73, 64), (47, 90), (47, 77), (8, 77)]
    d.polygon(pts, fill=GOLD, outline=INK, width=5)
    _sparkles(d, [(104, 24), (100, 105), (25, 104)], CYAN, 7)


def _settings(im):
    d = ImageDraw.Draw(im)
    pts = []
    for i in range(16):
        outer = i % 2 == 0
        r = 48 if outer else 37
        a1 = math.radians(i * 22.5 - (7 if outer else 4))
        a2 = math.radians(i * 22.5 + (7 if outer else 4))
        pts.append((64 + math.cos(a1) * r, 64 + math.sin(a1) * r))
        pts.append((64 + math.cos(a2) * r, 64 + math.sin(a2) * r))
    d.polygon(pts, fill=SILVER, outline=INK, width=7)
    d.ellipse([39, 39, 89, 89], fill=(71, 85, 104, 255), outline=INK, width=6)
    d.ellipse([52, 52, 76, 76], fill=PAPER, outline=INK, width=4)
    _basketball(im, 64, 64, 12)


def _shelves(im):
    d = ImageDraw.Draw(im)
    d.rectangle([12, 14, 116, 114], fill=(69, 48, 35, 255), outline=INK, width=7)
    for y in (48, 82):
        d.rectangle([17, y, 111, y + 8], fill=(184, 119, 65, 255), outline=INK, width=4)
    _basketball(im, 35, 32, 15)
    # Folded jerseys and shoe boxes make this unmistakably a locker shelf.
    for x, c in [(58, SKY), (82, PINK)]:
        d.polygon([(x, 22), (x + 8, 28), (x + 16, 22), (x + 21, 34), (x + 16, 37), (x + 16, 47), (x, 47), (x, 37), (x - 5, 34)], fill=c, outline=INK, width=3)
    for x, y, c in [(25, 58, RED), (52, 58, GOLD), (79, 58, GREEN), (27, 92, PURPLE), (61, 92, SKY)]:
        d.rectangle([x, y, x + 24, y + 17], fill=c, outline=INK, width=4)
        d.line([(x + 3, y + 4), (x + 21, y + 4)], fill=_alpha(WHITE, 130), width=3)
    _basketball(im, 96, 99, 12)


def _shop(im):
    d = ImageDraw.Draw(im)
    # Striped arena-boutique awning.
    for i, x in enumerate(range(14, 114, 10)):
        d.polygon([(x, 25), (x + 10, 25), (x + 7, 43), (x + 3, 43)], fill=RED if i % 2 == 0 else PAPER, outline=INK, width=2)
    d.rectangle([14, 18, 114, 28], fill=GOLD, outline=INK, width=5)
    d.rectangle([22, 43, 106, 110], fill=(58, 72, 91, 255), outline=INK, width=7)
    d.rectangle([31, 55, 57, 91], fill=CYAN, outline=INK, width=4)
    _basketball(im, 44, 73, 14)
    d = ImageDraw.Draw(im)
    d.rectangle([72, 62, 96, 110], fill=(166, 104, 58, 255), outline=INK, width=5)
    d.ellipse([89, 83, 95, 89], fill=GOLD, outline=INK, width=2)
    _coin(im, 82, 48, 15)


def _star_shield(im):
    d = ImageDraw.Draw(im)
    _draw_shield(d, 64, 64, 94, 106, GOLD, PAPER)
    _draw_star(d, 64, 61, 31, ORANGE, INK, 5)


def _tab(im):
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([18, 34, 110, 108], radius=7, fill=SKY, outline=INK, width=7)
    d.polygon([(18, 39), (18, 24), (55, 24), (66, 39)], fill=GOLD, outline=INK, width=6)
    d.rounded_rectangle([29, 51, 99, 96], radius=5, fill=PAPER, outline=INK, width=4)
    _basketball(im, 48, 73, 15)
    d = ImageDraw.Draw(im)
    for y in (61, 74, 87):
        d.line([(69, y), (91, y)], fill=STEEL, width=5)


def _tags(im):
    d = ImageDraw.Draw(im)
    def tag(points, color):
        d.polygon(points, fill=color, outline=INK, width=6)
    tag([(22, 31), (67, 31), (91, 55), (55, 91), (22, 91)], PINK)
    d.ellipse([34, 43, 48, 57], fill=PAPER, outline=INK, width=4)
    tag([(55, 55), (91, 43), (111, 63), (84, 104), (55, 91)], CYAN)
    d.ellipse([91, 57, 103, 69], fill=PAPER, outline=INK, width=3)
    _draw_star(d, 48, 70, 16, GOLD, INK, 3)
    d.line([(38, 42), (22, 18)], fill=GOLD_D, width=5)
    d.line([(98, 55), (111, 31)], fill=GOLD_D, width=5)


def _clipboard(im, tactics=True):
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([24, 14, 104, 116], radius=8, fill=BROWN, outline=INK, width=7)
    d.rounded_rectangle([33, 25, 95, 107], radius=4, fill=PAPER, outline=INK, width=4)
    d.rounded_rectangle([49, 7, 79, 29], radius=5, fill=SILVER, outline=INK, width=5)
    if tactics:
        # Half-court play diagram.
        d.rectangle([39, 43, 89, 96], outline=STEEL, width=4)
        d.line([(39, 69), (89, 69)], fill=STEEL, width=3)
        d.ellipse([55, 58, 73, 80], outline=STEEL, width=3)
        d.line([(45, 88), (58, 70), (75, 76), (82, 52)], fill=RED, width=5, joint="curve")
        d.polygon([(82, 48), (88, 60), (75, 57)], fill=RED, outline=INK, width=2)
        for x, y in [(47, 51), (76, 87)]:
            d.line([(x - 5, y - 5), (x + 5, y + 5)], fill=SKY_D, width=4)
            d.line([(x + 5, y - 5), (x - 5, y + 5)], fill=SKY_D, width=4)
        d.ellipse([63, 45, 75, 57], outline=ORANGE_D, width=4)
    else:
        for y in (47, 65, 83):
            _draw_check(d, 47, y, 0.28, GREEN)
            d.line([(60, y), (86, y)], fill=STEEL, width=5)
    d.line([(41, 34), (87, 34)], fill=GOLD_D, width=5)


def _court_wand(im):
    d = ImageDraw.Draw(im)
    d.line([(27, 108), (83, 44)], fill=INK, width=17)
    d.line([(27, 108), (83, 44)], fill=(125, 72, 35, 255), width=9)
    d.line([(31, 100), (53, 76)], fill=GOLD, width=4)
    _basketball(im, 91, 37, 27)
    d = ImageDraw.Draw(im)
    _sparkles(d, [(39, 32), (61, 18), (111, 76), (35, 64)], GOLD, 8)


def _play_emblem(im):
    d = ImageDraw.Draw(im)
    d.polygon([(39, 66), (55, 66), (48, 111), (31, 98)], fill=RED, outline=INK, width=5)
    d.polygon([(73, 66), (89, 66), (97, 98), (80, 111)], fill=SKY, outline=INK, width=5)
    _coin(im, 64, 55, 43, symbol="none")
    d = ImageDraw.Draw(im)
    d.polygon([(54, 36), (54, 75), (86, 55)], fill=GREEN, outline=INK, width=6)
    d.line([(58, 42), (58, 63)], fill=_alpha(WHITE, 120), width=4)


def _trophy_shape(d, cx, cy, scale=1.0):
    def p(x, y):
        return (cx + x * scale, cy + y * scale)
    d.arc([p(-42, -28), p(-8, 10)], start=90, end=270, fill=GOLD_D, width=max(4, int(8 * scale)))
    d.arc([p(8, -28), p(42, 10)], start=270, end=90, fill=GOLD_D, width=max(4, int(8 * scale)))
    d.polygon([p(-29, -35), p(29, -35), p(22, 8), p(8, 21), p(-8, 21), p(-22, 8)], fill=GOLD, outline=INK, width=max(4, int(7 * scale)))
    d.rectangle([p(-7, 20), p(7, 36)], fill=GOLD_D, outline=INK, width=max(3, int(4 * scale)))
    d.rectangle([p(-24, 36), p(24, 48)], fill=(93, 55, 29, 255), outline=INK, width=max(3, int(5 * scale)))
    _draw_star(d, cx, cy - 10 * scale, 14 * scale, PAPER, GOLD_D, max(2, int(3 * scale)))


def _trophy(im):
    d = ImageDraw.Draw(im)
    _trophy_shape(d, 64, 58, 1.0)
    _sparkles(d, [(20, 22), (108, 22), (104, 91)], GOLD, 8)


def _shop_crate(im):
    d = ImageDraw.Draw(im)
    _basketball(im, 49, 38, 25)
    _coin(im, 87, 35, 19)
    d = ImageDraw.Draw(im)
    d.polygon([(18, 58), (110, 58), (100, 110), (28, 110)], fill=BROWN, outline=INK, width=7)
    d.rectangle([14, 50, 114, 66], fill=(190, 122, 66, 255), outline=INK, width=6)
    for x in (38, 61, 84):
        d.line([(x, 66), (x - 3, 108)], fill=BROWN_D, width=5)
    d.line([(24, 88), (104, 88)], fill=BROWN_D, width=5)
    _draw_star(d, 64, 87, 13, GOLD, INK, 3)


def _megaphone(im):
    d = ImageDraw.Draw(im)
    d.polygon([(25, 51), (61, 51), (101, 25), (101, 91), (61, 69), (25, 69)], fill=SKY, outline=INK, width=7)
    d.rectangle([18, 45, 31, 75], fill=PAPER, outline=INK, width=5)
    d.line([(61, 69), (72, 96), (57, 104), (43, 70)], fill=INK, width=12, joint="curve")
    d.line([(61, 69), (72, 96), (57, 104), (43, 70)], fill=STEEL, width=6, joint="curve")
    d.ellipse([94, 25, 108, 91], fill=PAPER, outline=INK, width=6)
    for r in (16, 27):
        d.arc([106 - r, 58 - r, 106 + r, 58 + r], start=-55, end=55, fill=GOLD_D, width=5)
    d.line([(33, 55), (83, 38)], fill=_alpha(WHITE, 125), width=5)


def _tag_plaque(im):
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([13, 30, 115, 98], radius=10, fill=PURPLE, outline=INK, width=8)
    d.rounded_rectangle([23, 40, 105, 88], radius=7, fill=PAPER, outline=INK, width=4)
    _draw_star(d, 42, 64, 16, GOLD, INK, 3)
    for y, w in [(54, 39), (68, 30), (81, 22)]:
        d.line([(63, y), (63 + w, y)], fill=STEEL, width=5)
    for x in (25, 103):
        d.ellipse([x - 4, 35, x + 4, 43], fill=GOLD, outline=INK, width=2)


def enhance_basketball_face_textures(item_tex_dir: Path):
    """
    Gives every existing basketball face a consistent premium finish without
    moving a seam or UV: top-left key light, edge vignette, fine pebble grain
    and a small saturation lift.  Animated fire strips are processed frame by
    frame so their GPU animation metadata and layout remain unchanged.
    """
    for path in sorted(item_tex_dir.glob("ball_*.png")):
        im = Image.open(path).convert("RGBA")
        frame_h = im.width
        if frame_h <= 0 or im.height % frame_h != 0:
            continue
        px = im.load()
        for y in range(im.height):
            fy = y % frame_h
            ny = fy / max(1, frame_h - 1)
            for x in range(im.width):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                nx = x / max(1, im.width - 1)
                dx, dy = nx - 0.5, ny - 0.5
                dist2 = min(1.0, (dx * dx + dy * dy) * 2.0)
                pebble = (((x * 13 + fy * 7) % 9) - 4) / 255.0
                factor = 1.08 - 0.20 * dist2 + 0.07 * (1.0 - (nx + ny) * 0.5) + pebble
                lum = (r * 0.299 + g * 0.587 + b * 0.114) * factor
                px[x, y] = tuple(
                    max(0, min(255, round(lum + (channel * factor - lum) * 1.10)))
                    for channel in (r, g, b)
                ) + (a,)
        im.save(path)


# ---------------------------------------------------------------------------
# 32x32 verified-style collector badges for all 56 tag_card_<id> items.
# ---------------------------------------------------------------------------

def _dominant_saturated_color(path: Path):
    im = Image.open(path).convert("RGBA")
    total = [0.0, 0.0, 0.0]
    weight_sum = 0.0
    for r, g, b, a in im.getdata():
        if a < 180:
            continue
        mx, mn = max(r, g, b), min(r, g, b)
        if mx < 55 or (mx > 238 and mn > 205):
            continue
        w = 0.25 + (mx - mn) / 255.0
        total[0] += r * w
        total[1] += g * w
        total[2] += b * w
        weight_sum += w
    if weight_sum == 0:
        return (255, 140, 26)
    rgb = tuple(min(255, max(0, round(v / weight_sum))) for v in total)
    h, s, v = colorsys.rgb_to_hsv(*(c / 255.0 for c in rgb))
    s = max(s, 0.62)
    v = max(v, 0.72)
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v))


def _tag_motif(tag_id: str, idx: int) -> str:
    if tag_id == "mypineapple":
        return "pineapple"
    groups = {
        "crown": {"founder", "king", "royal", "dynasty", "champion", "mvp", "goat", "legend", "immortal", "zenith"},
        "shield": {"alpha", "clamp", "lockdown", "omega", "titan", "ronin", "street"},
        "flame": {"inferno", "chaos", "unstoppable", "poster"},
        "snow": {"frostbite", "glacier", "iceveins"},
        "moon": {"eclipse", "phantom"},
        "bolt": {"flash", "blitz", "ace", "air", "showtime"},
        "star": {"nova", "supernova", "aura", "prodigy"},
        "ball": {"baller", "ballgod", "buckets", "hooper", "swish", "skyhook"},
        "target": {"deadeye", "sniper", "dime", "clutch"},
        "snake": {"mamba", "venom", "viper"},
        "petal": {"sakura", "chef"},
        "cross": {"ankle", "crossgod"},
        "wave": {"splash"},
        "gem": {"cyber", "galaxy"},
    }
    for motif, ids in groups.items():
        if tag_id in ids:
            return motif
    return ("star", "shield", "gem", "check")[idx % 4]


def _draw_tag_motif(im: Image.Image, motif: str, accent):
    d = ImageDraw.Draw(im)
    cx = cy = 64
    light = _mix(accent, (255, 255, 255), 0.38)
    dark = _mix(accent, (0, 0, 0), 0.38)
    if motif == "crown":
        pts = [(25, 82), (25, 43), (45, 62), (64, 34), (83, 62), (103, 43), (103, 82)]
        d.polygon(pts, fill=GOLD, outline=INK, width=7)
        d.rectangle([25, 82, 103, 95], fill=GOLD_D, outline=INK, width=5)
        for x, c in [(42, RED), (64, SKY), (86, GREEN)]:
            d.ellipse([x - 7, 67, x + 7, 81], fill=c, outline=INK, width=3)
    elif motif == "shield":
        _draw_shield(d, cx, cy, 72, 82, light, PAPER)
        _draw_check(d, cx, cy - 2, 0.55, PAPER)
    elif motif == "flame":
        pts = [(64, 18), (79, 43), (97, 61), (91, 89), (73, 104), (52, 102), (35, 87), (35, 61), (49, 43), (51, 65), (63, 51), (59, 33)]
        d.polygon(pts, fill=(255, 92, 24, 255), outline=INK, width=6)
        d.polygon([(64, 54), (79, 72), (74, 91), (61, 97), (49, 87), (52, 68)], fill=GOLD)
    elif motif == "snow":
        for ang in range(0, 180, 30):
            x = math.cos(math.radians(ang)) * 34
            y = math.sin(math.radians(ang)) * 34
            d.line([(cx - x, cy - y), (cx + x, cy + y)], fill=INK, width=13)
            d.line([(cx - x, cy - y), (cx + x, cy + y)], fill=PAPER, width=7)
        d.ellipse([55, 55, 73, 73], fill=CYAN, outline=INK, width=4)
    elif motif == "moon":
        layer = Image.new("RGBA", im.size, GOLD)
        mask = Image.new("L", im.size, 0)
        md = ImageDraw.Draw(mask)
        md.ellipse([28, 24, 104, 100], fill=255)
        md.ellipse([57, 17, 117, 91], fill=0)
        layer.putalpha(mask)
        outline = Image.new("RGBA", im.size, (0, 0, 0, 0))
        ImageDraw.Draw(outline).ellipse([28, 24, 104, 100], outline=INK, width=6)
        im.alpha_composite(layer)
        im.alpha_composite(outline)
        _sparkles(ImageDraw.Draw(im), [(91, 88)], PAPER, 8)
    elif motif == "bolt":
        pts = [(72, 15), (35, 70), (58, 70), (47, 111), (94, 53), (67, 53)]
        d.polygon(pts, fill=GOLD, outline=INK, width=7)
    elif motif == "star":
        _draw_star(d, cx, cy, 40, GOLD, INK, 7)
    elif motif == "ball":
        _basketball(im, cx, cy, 38)
    elif motif == "target":
        for r, c in [(39, PAPER), (29, RED), (19, PAPER), (9, RED)]:
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c, outline=INK, width=4)
        d.line([(88, 32), (68, 57)], fill=INK, width=8)
        d.polygon([(86, 22), (104, 26), (94, 42)], fill=SKY, outline=INK, width=3)
    elif motif == "snake":
        pts = [(35, 88), (58, 73), (84, 83), (91, 61), (69, 48), (43, 57), (40, 35), (61, 24), (83, 35)]
        d.line(pts, fill=INK, width=22, joint="curve")
        d.line(pts, fill=GREEN, width=13, joint="curve")
        d.ellipse([75, 22, 99, 46], fill=GREEN, outline=INK, width=5)
        d.ellipse([87, 29, 93, 35], fill=INK)
        d.line([(98, 36), (111, 31)], fill=RED, width=4)
        d.line([(98, 36), (111, 42)], fill=RED, width=4)
    elif motif == "petal":
        for ang in range(0, 360, 72):
            x = cx + math.cos(math.radians(ang)) * 22
            y = cy + math.sin(math.radians(ang)) * 22
            d.ellipse([x - 17, y - 17, x + 17, y + 17], fill=PINK, outline=INK, width=4)
        d.ellipse([52, 52, 76, 76], fill=GOLD, outline=INK, width=5)
    elif motif == "cross":
        path = [(28, 31), (55, 31), (39, 58), (66, 58), (49, 91), (84, 91)]
        d.line(path, fill=INK, width=20, joint="curve")
        d.line(path, fill=PAPER, width=11, joint="curve")
        _basketball(im, 84, 42, 22)
    elif motif == "wave":
        for yy, col in [(47, CYAN), (65, SKY), (83, SKY_D)]:
            pts = [(24 + i * 8, yy + (8 if i % 2 else -4)) for i in range(11)]
            d.line(pts, fill=INK, width=15, joint="curve")
            d.line(pts, fill=col, width=8, joint="curve")
    elif motif == "gem":
        _gem(im, cx, cy, 39, light, dark)
    elif motif == "pineapple":
        for pts in [[(64, 8), (55, 34), (70, 34)], [(43, 17), (51, 38), (62, 29)], [(85, 17), (66, 29), (77, 38)]]:
            d.polygon(pts, fill=GREEN, outline=INK, width=4)
        d.rounded_rectangle([38, 34, 90, 108], radius=18, fill=GOLD, outline=INK, width=7)
        for x in (50, 64, 78):
            d.line([(x, 36), (x - 5, 106)], fill=GOLD_D, width=3)
            d.line([(x, 36), (x + 8, 106)], fill=GOLD_D, width=3)
    else:
        _draw_check(d, cx, cy, 1.0, PAPER)


def generate_premium_tag_emblem_items(font_tex_dir: Path, item_tex_dir: Path):
    """Overwrites all tag-card PNGs with 32x32 colored verified-style badges."""
    item_tex_dir.mkdir(parents=True, exist_ok=True)
    for idx, pill_path in enumerate(sorted(font_tex_dir.glob("tag_*.png"))):
        tag_id = pill_path.stem.removeprefix("tag_")
        accent = _dominant_saturated_color(pill_path)
        top = _mix(accent, (255, 255, 255), 0.30)
        bottom = _mix(accent, (0, 0, 0), 0.34)
        im = _canvas()
        _masked_gradient(
            im,
            (8, 8, 120, 120),
            lambda d, w, h: d.rounded_rectangle([0, 0, w - 1, h - 1], radius=18, fill=255),
            top,
            bottom,
        )
        d = ImageDraw.Draw(im)
        d.rounded_rectangle([8, 8, 120, 120], radius=18, outline=INK, width=8)
        d.rounded_rectangle([17, 17, 111, 111], radius=13, outline=_alpha(WHITE, 150), width=4)
        d.line([(24, 20), (78, 20)], fill=_alpha(WHITE, 145), width=6)
        _draw_tag_motif(im, _tag_motif(tag_id, idx), accent)
        _finish(im).save(item_tex_dir / f"tag_card_{tag_id}.png")
