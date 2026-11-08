#!/usr/bin/env python3
"""
BosketBall native 16x16 pixel-art icon overhaul.

Most menu, dunk, prop and chat-tag textures now use the owner's purchased
16x16 icon library in ``tools/assets/purchased-icons`` directly.  Purchased
artwork is never redrawn or given a second outline: the loader only centers
the few smaller source files on a 16x16 canvas and normalizes stray
semi-transparent pixels to BosketBall's binary-alpha item format.

A small number of basketball-specific sprites for which the purchased library
has no hoop/ball equivalent (the hoop itself, between-the-legs and windmill)
retain the hand-drawn native 16x16 painters below.  Those painters still use
no supersampling, no downscaling, no anti-aliased paint and no drop shadow.
"""

from __future__ import annotations

import colorsys
import math
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 16
PURCHASED_ICON_DIR = Path(__file__).resolve().parent / "assets" / "purchased-icons"

# Small flat palette.  Every entry is fully opaque; transparency is reserved
# for the empty canvas so the output is genuine 1-bit-alpha pixel art.
K = (32, 22, 34, 255)        # near-black plum outline
W = (248, 244, 232, 255)     # warm white
H = (255, 246, 190, 255)     # hard highlight
SILVER = (190, 198, 207, 255)
STEEL = (126, 137, 151, 255)
STEEL_D = (78, 88, 103, 255)
ORANGE_L = (255, 178, 82, 255)
ORANGE = (243, 116, 32, 255)
ORANGE_D = (176, 66, 16, 255)
SEAM = (91, 39, 24, 255)
GOLD_L = (255, 224, 99, 255)
GOLD = (244, 177, 39, 255)
GOLD_D = (166, 103, 14, 255)
RED_L = (255, 122, 104, 255)
RED = (231, 52, 54, 255)
RED_D = (145, 28, 45, 255)
GREEN_L = (126, 239, 132, 255)
GREEN = (48, 190, 88, 255)
GREEN_D = (20, 116, 55, 255)
CYAN = (116, 229, 255, 255)
SKY = (55, 166, 232, 255)
BLUE = (37, 99, 190, 255)
PURPLE_L = (205, 164, 255, 255)
PURPLE = (139, 92, 246, 255)
PURPLE_D = (76, 42, 137, 255)
PINK_L = (255, 178, 211, 255)
PINK = (241, 95, 164, 255)
BROWN_L = (190, 122, 66, 255)
BROWN = (129, 76, 38, 255)
BROWN_D = (73, 40, 23, 255)
SKIN_L = (244, 186, 128, 255)
SKIN = (190, 116, 66, 255)
PAPER = (248, 244, 220, 255)


def _canvas() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def _finish(im: Image.Image) -> Image.Image:
    """Add exactly one stepped dark outline around the opaque silhouette."""
    out = im.copy()
    src = im.load()
    dst = out.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if src[x, y][3] != 0:
                continue
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and src[nx, ny][3] != 0:
                    dst[x, y] = K
                    break
    return out


def _purchased_icon(stem: str) -> Image.Image:
    """Load one purchased source icon as a native 16x16 binary-alpha sprite.

    The purchased pixels are preserved.  Sources smaller than 16x16 are only
    centered; the two larger odd-sized sources are fitted with nearest-neighbor
    sampling so their hard pixel edges remain hard.  Alpha below 32 is treated
    as the near-invisible export fringe present in parts of the source pack,
    while all real artwork at or above that threshold becomes fully opaque.
    """
    path = PURCHASED_ICON_DIR / f"{stem}.png"
    src = Image.open(path).convert("RGBA")
    if src.width > SIZE or src.height > SIZE:
        scale = min(SIZE / src.width, SIZE / src.height)
        fitted = src.resize(
            (max(1, round(src.width * scale)), max(1, round(src.height * scale))),
            Image.Resampling.NEAREST,
        )
    else:
        fitted = src

    out = _canvas()
    out.alpha_composite(fitted, ((SIZE - fitted.width) // 2, (SIZE - fitted.height) // 2))
    px = out.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255 if a >= 32 else 0)
    return out


# Purchased artwork mapped to the long-standing BosketBall item-model IDs.
# Keeping the model IDs stable means every existing GUI, hotbar item and lobby
# prop upgrades automatically without breaking saved player inventories.
PURCHASED_ICON_MAP: dict[str, str] = {
    "icon_arena": "bina",
    "icon_back": "back",
    "icon_book": "quest_log",
    "icon_bot_off": "emote_off",
    "icon_bot_on": "emote_on",
    "icon_check": "accept",
    "icon_chest": "sandik",
    "icon_coin_stack": "coins",
    "icon_crossover": "requeue_random",
    "icon_defense": "mod_shield",
    "icon_discord": "discord",
    "icon_friends": "social",
    "icon_gem_ruby": "gem",
    "icon_history": "clock",
    "icon_leave": "leave",
    "icon_next": "emote_triangle_right",
    "icon_pass": "invite",
    "icon_podium": "championscore",
    "icon_portal": "fast_travel",
    "icon_prev": "emote_triangle_left",
    "icon_settings": "settings",
    "icon_shelves": "infinibag",
    "icon_shop": "shop",
    "icon_star_shield": "star",
    "icon_tab": "instance_selected",
    "icon_tags": "catalogue",
    "dunk_meteor_jam": "circle",
    "dunk_spin_360": "requeue",
    "dunk_tomahawk": "blacksmith",
    "tactics_playbook": "quest_daily_common",
    "court_wand": "accessories",
    "prop_guide_clipboard": "quest_daily_weekly",
    "prop_locker_jersey": "shop_clothing",
    "prop_play_emblem": "play",
    "prop_podium_cup": "trophy",
    "prop_shop_crate": "quest_chest",
    "prop_social_megaphone": "duyuru",
    "prop_tag_plaque": "profile",
    # Additional action-specific icons wired into Play, Friends/Party,
    # Settings, Leaderboards and the OP Admin panel.
    "icon_duel": "kilic",
    "icon_team_duo": "friends",
    "icon_team": "emote_social",
    "icon_battle": "game_battle_box",
    "icon_training": "game_parkour_tag",
    "icon_target": "archery",
    "icon_spectate": "spectator",
    "icon_sound_on": "emote_sound",
    "icon_sound_off": "emote_sound_off",
    "icon_camera": "fotograf",
    "icon_replay": "kameraa",
    "icon_party_invites": "party_invites",
    "icon_friend_add": "friend_add",
    "icon_friend_remove": "friend_remove",
    "icon_party_remove": "party_remove",
    "icon_party_unavailable": "party_unavailable",
    "icon_lock": "emote_lock",
    "icon_home": "home",
    "icon_message": "chat_bubble_white",
    "icon_delete": "copkutusu",
    "icon_reload": "emote_returnt",
    "icon_trophy_gold": "altinkupa",
    "icon_medal_gold": "altinmadalya",
}

# Collector tags use purchased artwork wherever the library has a readable
# thematic match.  MyPineapple and Sakura keep their exact custom motifs.
PURCHASED_TAG_ICON_MAP: dict[str, str] = {
    "ace": "gumusmadalya",
    "air": "emote_up",
    "alpha": "game_survival_games",
    "ankle": "point_left",
    "aura": "auras",
    "baller": "gamepad",
    "ballgod": "uzayli",
    "blitz": "planor",
    "buckets": "rewards",
    "champion": "kupa",
    "chaos": "angry",
    "chef": "hats",
    "clamp": "gadgets",
    "clutch": "saat",
    "crossgod": "leave_queue",
    "cyber": "gear",
    "deadeye": "buyutec",
    "dime": "trade",
    "dynasty": "ah",
    "eclipse": "compass_00",
    "flash": "upgrade",
    "founder": "anahtar",
    "frostbite": "8",
    "galaxy": "profile_2",
    "glacier": "azrail",
    "goat": "winner",
    "hooper": "uye",
    "iceveins": "heart_blue",
    "immortal": "3",
    "inferno": "game_sky_battle",
    "king": "kupab",
    "legend": "bronzkupa",
    "lockdown": "iptaletmek",
    "mamba": "dragon",
    "mvp": "gumuskupa",
    "nova": "emote_star",
    "omega": "skull",
    "phantom": "carneval",
    "poster": "map_kill_contract",
    "prodigy": "grin",
    "ronin": "asker",
    "royal": "crown",
    "showtime": "trampet",
    "skyhook": "point_right",
    "sniper": "durbun",
    "splash": "ada",
    "street": "telefonkulubesi",
    "supernova": "game_hole_in_the_wall",
    "swish": "music_note",
    "titan": "heykel",
    "unstoppable": "checkpoint",
    "venom": "zombi",
    "viper": "dinazor",
    "zenith": "wisdom",
}


def purchased_source_stems() -> set[str]:
    """All purchased source files actually mapped to an in-game item model."""
    return set(PURCHASED_ICON_MAP.values()) | set(PURCHASED_TAG_ICON_MAP.values())


def _px(d: ImageDraw.ImageDraw, xy, color):
    d.point(xy, fill=color)


def _line(d: ImageDraw.ImageDraw, pts, color, width=1):
    d.line(pts, fill=color, width=width, joint="curve")


def _rect(d: ImageDraw.ImageDraw, box, color):
    d.rectangle(box, fill=color)


def _poly(d: ImageDraw.ImageDraw, pts, color):
    d.polygon(pts, fill=color)


def _ellipse(d: ImageDraw.ImageDraw, box, color):
    d.ellipse(box, fill=color)


def _ball(im: Image.Image, cx: int, cy: int, r: int):
    d = ImageDraw.Draw(im)
    box = [cx - r, cy - r, cx + r, cy + r]
    _ellipse(d, box, ORANGE)
    if r >= 3:
        # Lower shade and the four classic seams.
        d.chord(box, start=25, end=155, fill=ORANGE_D)
        _line(d, [(cx, cy - r + 1), (cx, cy + r - 1)], SEAM)
        _line(d, [(cx - r + 1, cy), (cx + r - 1, cy)], SEAM)
        d.arc([cx - r * 2, cy - r, cx, cy + r], start=-55, end=55, fill=SEAM)
        d.arc([cx, cy - r, cx + r * 2, cy + r], start=125, end=235, fill=SEAM)
        _px(d, (cx - max(1, r // 2), cy - max(1, r // 2)), H)
        _px(d, (cx - max(1, r // 2) + 1, cy - max(1, r // 2)), ORANGE_L)
    else:
        _line(d, [(cx, cy - r), (cx, cy + r)], SEAM)
        _line(d, [(cx - r, cy), (cx + r, cy)], SEAM)
        _px(d, (cx - 1, cy - 1), H)


def _coin(im: Image.Image, cx: int, cy: int, r: int, star=True):
    d = ImageDraw.Draw(im)
    box = [cx - r, cy - r, cx + r, cy + r]
    _ellipse(d, box, GOLD)
    if r >= 3:
        d.chord(box, start=25, end=155, fill=GOLD_D)
        d.arc([cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1], start=180, end=330, fill=GOLD_L)
        if star:
            for xy in ((cx, cy - 1), (cx - 1, cy), (cx, cy), (cx + 1, cy), (cx, cy + 1)):
                _px(d, xy, H)
        _px(d, (cx - 1, cy - r + 1), H)


def _star(im: Image.Image, cx: int, cy: int, r: int, fill=GOLD):
    d = ImageDraw.Draw(im)
    pts = []
    for i in range(10):
        radius = r if i % 2 == 0 else max(1, round(r * 0.48))
        ang = -math.pi / 2 + i * math.pi / 5
        pts.append((round(cx + math.cos(ang) * radius), round(cy + math.sin(ang) * radius)))
    _poly(d, pts, fill)
    _px(d, (cx - 1, cy - 1), H)
    _px(d, (cx, cy), GOLD_L if fill == GOLD else H)


def _shield(im: Image.Image, fill=SKY, inner=W):
    d = ImageDraw.Draw(im)
    _poly(d, [(7, 1), (12, 3), (12, 8), (7, 14), (3, 8), (3, 3)], fill)
    _poly(d, [(7, 3), (10, 4), (10, 8), (7, 12), (5, 8), (5, 4)], inner)
    _px(d, (5, 3), H)


def _check(im: Image.Image, color=GREEN, light=GREEN_L):
    d = ImageDraw.Draw(im)
    pts = [(2, 8), (6, 12), (13, 3)]
    _line(d, pts, color, 3)
    _line(d, [(3, 7), (6, 10), (12, 3)], light, 1)


def _cross(im: Image.Image, cx=7, cy=7, r=4, color=RED):
    d = ImageDraw.Draw(im)
    _line(d, [(cx - r, cy - r), (cx + r, cy + r)], color, 3)
    _line(d, [(cx + r, cy - r), (cx - r, cy + r)], color, 3)
    _px(d, (cx - r + 1, cy - r + 1), RED_L)


def _heart(im: Image.Image, cx=7, cy=7, fill=RED):
    d = ImageDraw.Draw(im)
    _ellipse(d, [cx - 4, cy - 4, cx, cy], fill)
    _ellipse(d, [cx, cy - 4, cx + 4, cy], fill)
    _poly(d, [(cx - 4, cy - 1), (cx + 4, cy - 1), (cx, cy + 5)], fill)
    _px(d, (cx - 2, cy - 3), PINK_L)


def _gem(im: Image.Image, cx=7, cy=7, light=PINK_L, base=PINK, dark=RED_D):
    d = ImageDraw.Draw(im)
    # Classic RPG diamond: short crown, wide girdle and a long pavilion.
    _poly(d, [(cx - 3, cy - 5), (cx + 3, cy - 5), (cx + 5, cy - 1), (cx, cy + 6), (cx - 5, cy - 1)], base)
    _poly(d, [(cx - 3, cy - 5), (cx + 1, cy - 5), (cx - 1, cy - 1), (cx - 5, cy - 1)], light)
    _poly(d, [(cx + 1, cy - 1), (cx + 5, cy - 1), (cx, cy + 6)], dark)
    _line(d, [(cx - 5, cy - 1), (cx + 5, cy - 1)], K)
    _line(d, [(cx - 1, cy - 1), (cx, cy + 5)], light)
    _line(d, [(cx + 1, cy - 1), (cx, cy + 5)], dark)
    _px(d, (cx - 2, cy - 4), W)
    _px(d, (cx - 3, cy + 1), light)


def _arrow(im: Image.Image, direction="right", light=GOLD_L, base=GOLD):
    d = ImageDraw.Draw(im)
    if direction == "right":
        pts = [(2, 5), (8, 5), (8, 2), (14, 7), (8, 12), (8, 9), (2, 9)]
        _poly(d, pts, base)
        _poly(d, [(2, 5), (8, 5), (8, 2), (11, 5), (8, 7), (2, 7)], light)
    else:
        pts = [(13, 5), (7, 5), (7, 2), (1, 7), (7, 12), (7, 9), (13, 9)]
        _poly(d, pts, base)
        _poly(d, [(13, 5), (7, 5), (7, 2), (4, 5), (7, 7), (13, 7)], light)


def _sparkle(d: ImageDraw.ImageDraw, x: int, y: int, color=GOLD_L):
    # A 3x3 diamond is the largest sparkle that stays clean at native 16x16.
    for xy in ((x, y - 1), (x - 1, y), (x, y), (x + 1, y), (x, y + 1)):
        if 0 <= xy[0] < SIZE and 0 <= xy[1] < SIZE:
            _px(d, xy, color)


def _hoop(im: Image.Image, with_ball=True):
    d = ImageDraw.Draw(im)
    _rect(d, [2, 1, 13, 5], W)
    _rect(d, [3, 2, 12, 4], CYAN)
    _rect(d, [5, 2, 10, 5], W)
    _line(d, [(5, 5), (10, 5)], ORANGE_D)
    if with_ball:
        _ball(im, 7, 5, 3)
        d = ImageDraw.Draw(im)
    _ellipse(d, [4, 7, 11, 9], RED)
    _px(d, (5, 7), RED_L)
    # Pixel net: short white diagonals under the rim.
    for a, b in (((5, 9), (6, 12)), ((7, 9), (7, 13)), ((10, 9), (9, 12)), ((5, 12), (10, 9)), ((10, 12), (5, 9))):
        _line(d, [a, b], W)


def _trophy(im: Image.Image, cx=7, top=2):
    d = ImageDraw.Draw(im)
    # Handles first, then cup.
    d.arc([cx - 6, top + 1, cx - 1, top + 6], start=90, end=270, fill=GOLD_D)
    d.arc([cx + 2, top + 1, cx + 7, top + 6], start=270, end=90, fill=GOLD_D)
    _poly(d, [(cx - 4, top), (cx + 4, top), (cx + 3, top + 5), (cx + 1, top + 7), (cx - 1, top + 7), (cx - 3, top + 5)], GOLD)
    _line(d, [(cx - 3, top + 1), (cx, top + 1)], GOLD_L)
    _rect(d, [cx - 1, top + 7, cx + 1, top + 9], GOLD_D)
    _rect(d, [cx - 3, top + 10, cx + 3, top + 11], BROWN)
    _star(im, cx, top + 3, 2, W)


def _clipboard(im: Image.Image, tactics=True):
    d = ImageDraw.Draw(im)
    _rect(d, [4, 2, 11, 14], BROWN)
    _rect(d, [5, 4, 10, 13], PAPER)
    _rect(d, [6, 1, 9, 3], SILVER)
    _px(d, (7, 2), W)
    if tactics:
        d.rectangle([5, 6, 10, 12], outline=STEEL)
        _line(d, [(5, 9), (10, 9)], STEEL)
        _line(d, [(6, 11), (7, 8), (9, 10), (10, 6)], RED)
        _px(d, (6, 6), SKY)
        _px(d, (9, 11), SKY)
        _px(d, (8, 7), ORANGE)
    else:
        for y in (6, 9, 12):
            _line(d, [(5, y), (6, y + 1), (8, y - 1)], GREEN)
            _line(d, [(8, y), (10, y)], STEEL)


def build_premium_icon_sprites() -> dict[str, Image.Image]:
    """Build the original 41 IDs plus purchased action-specific icon IDs."""
    sprites: dict[str, Image.Image] = {}

    def add(name, painter):
        im = _canvas()
        painter(im)
        sprites[name] = _finish(im)

    def arena(im):
        d = ImageDraw.Draw(im)
        # Stadium bowl seen from the front: rim, two stand tiers, court.
        _poly(d, [(1, 4), (14, 4), (13, 7), (2, 7)], PURPLE)
        _line(d, [(2, 4), (8, 4)], PURPLE_L)
        for x in (3, 5, 7, 9, 11):
            _px(d, (x, 5), W)
            _px(d, (x + 1, 6), GOLD_L)
        _poly(d, [(2, 7), (13, 7), (12, 10), (3, 10)], SKY)
        _line(d, [(3, 8), (12, 8)], CYAN)
        _poly(d, [(3, 10), (12, 10), (14, 14), (1, 14)], BROWN_L)
        d.ellipse([5, 11, 10, 13], outline=W)
        _line(d, [(1, 12), (5, 12)], W)
        _line(d, [(10, 12), (14, 12)], W)
        _px(d, (1, 2), GOLD_L)
        _px(d, (14, 2), GOLD_L)

    def book(im):
        d = ImageDraw.Draw(im)
        _rect(d, [4, 2, 12, 13], BROWN)
        _rect(d, [4, 2, 5, 13], BROWN_D)
        _rect(d, [6, 3, 11, 10], (168, 82, 42, 255))
        _line(d, [(8, 5), (11, 5)], GOLD_L)
        _line(d, [(8, 7), (10, 7)], GOLD)
        _rect(d, [5, 11, 12, 12], PAPER)
        _px(d, (10, 13), RED)
        _px(d, (6, 3), BROWN_L)

    def bot(enabled):
        def paint(im):
            d = ImageDraw.Draw(im)
            accent = CYAN if enabled else RED
            _line(d, [(7, 2), (7, 4)], STEEL)
            _ellipse(d, [6, 0, 8, 2], accent)
            _rect(d, [1, 7, 2, 10], STEEL)
            _rect(d, [13, 7, 14, 10], STEEL)
            d.rounded_rectangle([3, 4, 12, 13], radius=2, fill=SILVER)
            _line(d, [(5, 5), (10, 5)], W)
            _rect(d, [4, 6, 11, 9], K)
            if enabled:
                _px(d, (6, 7), CYAN)
                _px(d, (9, 7), CYAN)
                _line(d, [(6, 11), (9, 11)], GREEN_D)
                _line(d, [(10, 12), (11, 13), (13, 10)], GREEN)
            else:
                for cx in (6, 9):
                    _line(d, [(cx - 1, 6), (cx + 1, 8)], RED)
                    _line(d, [(cx + 1, 6), (cx - 1, 8)], RED)
                _line(d, [(6, 11), (9, 11)], RED_D)
                _line(d, [(10, 10), (13, 13)], RED)
                _line(d, [(13, 10), (10, 13)], RED)
        return paint

    def chest(im):
        d = ImageDraw.Draw(im)
        d.pieslice([2, 1, 13, 9], start=180, end=360, fill=BROWN_L)
        _rect(d, [2, 6, 13, 13], BROWN)
        _line(d, [(2, 6), (13, 6)], BROWN_D)
        for x in (4, 10):
            _rect(d, [x, 3, x + 1, 13], GOLD)
        _rect(d, [6, 6, 9, 9], GOLD_L)
        _px(d, (7, 7), K)
        _px(d, (7, 8), K)
        _coin(im, 12, 2, 2, star=False)

    def coin_stack(im):
        d = ImageDraw.Draw(im)
        for y in (9, 7, 5):
            _ellipse(d, [1, y, 8, y + 4], GOLD)
            d.arc([2, y + 1, 7, y + 3], start=180, end=340, fill=GOLD_L)
            _line(d, [(2, y + 3), (7, y + 3)], GOLD_D)
        _coin(im, 10, 5, 4)

    def crossover(im):
        d = ImageDraw.Draw(im)
        pts = [(1, 3), (5, 3), (2, 7), (7, 7), (4, 11), (9, 11)]
        _line(d, pts, CYAN, 2)
        _poly(d, [(8, 9), (12, 11), (8, 13)], CYAN)
        _ball(im, 10, 4, 4)
        d = ImageDraw.Draw(im)
        _poly(d, [(2, 13), (9, 13), (11, 14), (2, 14)], W)
        _line(d, [(3, 14), (10, 14)], RED)

    def defense(im):
        _shield(im, SKY, W)
        _ball(im, 7, 7, 3)

    def discord(im):
        d = ImageDraw.Draw(im)
        d.rounded_rectangle([2, 4, 13, 11], radius=3, fill=(88, 101, 242, 255))
        _poly(d, [(5, 11), (8, 11), (6, 14)], (88, 101, 242, 255))
        _ellipse(d, [4, 6, 6, 9], W)
        _ellipse(d, [9, 6, 11, 9], W)
        _px(d, (5, 8), K)
        _px(d, (10, 8), K)
        d.arc([5, 7, 10, 11], start=15, end=165, fill=W)
        _line(d, [(4, 5), (8, 5)], (170, 180, 255, 255))

    def friends(im):
        d = ImageDraw.Draw(im)
        # Two simple busts, like the people glyph in the reference sheets.
        _ellipse(d, [8, 2, 12, 6], SKIN_L)
        _poly(d, [(7, 7), (13, 7), (14, 12), (6, 12)], SKY)
        _ellipse(d, [2, 3, 7, 8], SKIN)
        d.arc([2, 2, 7, 6], start=180, end=360, fill=K)
        _poly(d, [(1, 9), (8, 9), (9, 14), (0, 14)], ORANGE)
        _px(d, (3, 4), SKIN_L)
        _px(d, (9, 3), W)
        _line(d, [(2, 9), (5, 9)], ORANGE_L)

    def history(im):
        d = ImageDraw.Draw(im)
        d.arc([1, 1, 14, 14], start=35, end=330, fill=SKY)
        _poly(d, [(1, 4), (4, 1), (5, 5)], SKY)
        _ellipse(d, [2, 2, 13, 13], SKY)
        _ellipse(d, [3, 3, 12, 12], W)
        for xy in ((7, 4), (11, 7), (7, 11), (4, 7)):
            _px(d, xy, STEEL)
        _line(d, [(7, 7), (7, 4)], K)
        _line(d, [(7, 7), (10, 9)], ORANGE_D)
        _px(d, (7, 7), GOLD)

    def leave(im):
        d = ImageDraw.Draw(im)
        _poly(d, [(3, 1), (8, 2), (8, 13), (3, 12)], BROWN)
        _poly(d, [(4, 3), (7, 4), (7, 11), (4, 10)], BROWN_L)
        _px(d, (6, 7), GOLD_L)
        _poly(d, [(7, 5), (11, 5), (11, 3), (14, 7), (11, 11), (11, 9), (7, 9)], RED)
        _line(d, [(8, 6), (11, 6)], RED_L)

    def pass_ball(im):
        d = ImageDraw.Draw(im)
        for y, x1 in ((3, 5), (6, 6), (9, 5), (12, 4)):
            _line(d, [(1, y), (x1, y)], CYAN)
        _ball(im, 9, 7, 5)
        d = ImageDraw.Draw(im)
        _poly(d, [(13, 5), (15, 7), (13, 9)], GREEN)

    def podium(im):
        d = ImageDraw.Draw(im)
        _star(im, 7, 2, 2, GOLD)
        d = ImageDraw.Draw(im)
        # Three clean medal blocks; no trophy details competing at 16x16.
        _rect(d, [6, 6, 9, 14], GOLD)
        _rect(d, [2, 9, 5, 14], SILVER)
        _rect(d, [10, 11, 13, 14], (205, 128, 72, 255))
        _line(d, [(6, 6), (9, 6)], GOLD_L)
        _line(d, [(2, 9), (5, 9)], W)
        _line(d, [(10, 11), (13, 11)], BROWN_L)
        # Only the winning "1" is legible at 16x16; side numerals become noise.
        _line(d, [(7, 8), (7, 12)], K)
        _px(d, (6, 9), K)

    def portal(im):
        d = ImageDraw.Draw(im)
        _ellipse(d, [4, 1, 11, 14], PURPLE)
        _ellipse(d, [5, 3, 10, 12], PURPLE_D)
        d.arc([5, 3, 10, 12], start=80, end=315, fill=PINK)
        d.arc([6, 5, 9, 10], start=240, end=120, fill=CYAN)
        _poly(d, [(0, 6), (4, 6), (4, 4), (7, 7), (4, 10), (4, 8), (0, 8)], GOLD)
        _sparkle(d, 12, 2, CYAN)
        _sparkle(d, 12, 12, PINK_L)

    def settings(im):
        d = ImageDraw.Draw(im)
        pts = []
        for i in range(16):
            r = 6 if i % 2 == 0 else 4
            a = math.radians(i * 22.5)
            pts.append((round(7 + math.cos(a) * r), round(7 + math.sin(a) * r)))
        _poly(d, pts, SILVER)
        _ellipse(d, [4, 4, 11, 11], STEEL_D)
        _ellipse(d, [6, 6, 9, 9], W)
        _px(d, (5, 4), W)

    def shelves(im):
        d = ImageDraw.Draw(im)
        _rect(d, [2, 2, 13, 14], BROWN_D)
        _rect(d, [3, 3, 12, 13], BROWN)
        for y in (6, 10, 13):
            _line(d, [(3, y), (12, y)], BROWN_L)
        _ball(im, 4, 4, 2)
        _rect(d, [7, 3, 9, 5], SKY)
        _rect(d, [10, 3, 12, 5], PINK)
        for x, c in ((3, RED), (6, GOLD), (9, GREEN)):
            _rect(d, [x, 7, x + 2, 9], c)
        _rect(d, [3, 11, 6, 12], PURPLE)
        _rect(d, [7, 11, 9, 12], SKY)
        _ball(im, 11, 11, 2)

    def shop(im):
        d = ImageDraw.Draw(im)
        _rect(d, [2, 2, 13, 3], GOLD)
        for i, x in enumerate(range(2, 13, 2)):
            _rect(d, [x, 3, x + 1, 5], RED if i % 2 == 0 else W)
        _rect(d, [3, 5, 12, 14], STEEL_D)
        _rect(d, [4, 7, 7, 11], CYAN)
        _ball(im, 5, 9, 2)
        d = ImageDraw.Draw(im)
        _rect(d, [9, 8, 12, 14], BROWN)
        _px(d, (11, 11), GOLD_L)
        _coin(im, 10, 5, 2, star=False)

    def star_shield(im):
        d = ImageDraw.Draw(im)
        _poly(d, [(7, 1), (12, 3), (12, 8), (7, 14), (3, 8), (3, 3)], GOLD)
        _poly(d, [(7, 3), (10, 4), (7, 12), (7, 8)], GOLD_D)
        _line(d, [(4, 3), (7, 2)], GOLD_L)
        # Hand-placed broad star so it reads inside the shield at 16x16.
        _poly(d, [(7, 3), (8, 6), (11, 6), (9, 8), (10, 11), (7, 9), (4, 11), (5, 8), (3, 6), (6, 6)], ORANGE)

    def tab(im):
        d = ImageDraw.Draw(im)
        # Plain folder glyph: gold tab, blue body, one white document line.
        _poly(d, [(2, 5), (6, 5), (8, 7), (13, 7), (13, 13), (2, 13)], SKY)
        _poly(d, [(2, 3), (6, 3), (8, 5), (2, 5)], GOLD)
        _line(d, [(3, 4), (6, 4)], GOLD_L)
        _rect(d, [4, 8, 11, 11], W)
        _line(d, [(5, 9), (10, 9)], STEEL)
        _line(d, [(5, 10), (8, 10)], STEEL)

    def tags(im):
        d = ImageDraw.Draw(im)
        _poly(d, [(2, 3), (8, 2), (12, 6), (7, 11), (3, 9)], PINK)
        _ellipse(d, [3, 4, 5, 6], W)
        _poly(d, [(7, 8), (11, 6), (14, 9), (10, 14), (6, 11)], CYAN)
        _ellipse(d, [11, 7, 13, 9], W)
        _star(im, 6, 7, 2, GOLD)
        _line(d, [(4, 4), (2, 1)], GOLD_D)

    def court_wand(im):
        d = ImageDraw.Draw(im)
        _line(d, [(2, 13), (9, 7)], BROWN, 2)
        _line(d, [(3, 12), (6, 9)], GOLD)
        _ball(im, 10, 5, 3)
        d = ImageDraw.Draw(im)
        _sparkle(d, 3, 4, GOLD_L)
        _sparkle(d, 13, 10, CYAN)

    def dunk_tomahawk(im):
        d = ImageDraw.Draw(im)
        # A clear diagonal axe behind the ball, plus the rim below.
        _line(d, [(4, 12), (12, 3)], BROWN, 2)
        _poly(d, [(9, 1), (14, 2), (13, 6), (10, 5), (11, 3)], SILVER)
        _line(d, [(10, 2), (13, 3)], W)
        _ball(im, 5, 6, 4)
        d = ImageDraw.Draw(im)
        _ellipse(d, [3, 11, 10, 13], RED)
        _line(d, [(4, 13), (5, 15)], W)
        _line(d, [(9, 13), (8, 15)], W)
        _line(d, [(10, 8), (13, 9)], RED_L)

    def dunk_windmill(im):
        d = ImageDraw.Draw(im)
        for pts in (((7, 1), (10, 3), (8, 5)), ((14, 7), (12, 10), (10, 8)), ((8, 14), (5, 12), (7, 10)), ((1, 8), (3, 5), (5, 7))):
            _poly(d, pts, CYAN)
        d.arc([1, 1, 14, 14], start=20, end=330, fill=SKY)
        _ball(im, 7, 7, 4)

    def dunk_between_legs(im):
        d = ImageDraw.Draw(im)
        # Shorts at the top, two separated legs, and the ball visibly between them.
        _poly(d, [(4, 1), (11, 1), (12, 5), (9, 5), (8, 3), (7, 3), (6, 5), (3, 5)], PURPLE)
        _line(d, [(4, 2), (10, 2)], PURPLE_L)
        _line(d, [(5, 5), (3, 9), (2, 12)], SKIN, 2)
        _line(d, [(10, 5), (12, 9), (13, 12)], SKIN, 2)
        _poly(d, [(0, 12), (3, 12), (4, 14), (0, 14)], W)
        _poly(d, [(12, 12), (15, 12), (15, 14), (11, 14)], W)
        _line(d, [(0, 14), (4, 14)], RED)
        _line(d, [(11, 14), (15, 14)], RED)
        _ball(im, 7, 9, 3)

    def dunk_spin(im):
        d = ImageDraw.Draw(im)
        d.arc([1, 1, 14, 14], start=35, end=325, fill=CYAN)
        _poly(d, [(11, 2), (14, 5), (9, 5)], CYAN)
        _ball(im, 7, 7, 4)
        d = ImageDraw.Draw(im)
        _sparkle(d, 3, 12, GOLD_L)

    def dunk_meteor(im):
        d = ImageDraw.Draw(im)
        _poly(d, [(2, 1), (6, 3), (3, 5), (7, 6), (2, 9), (4, 5), (1, 6), (4, 3)], RED)
        _poly(d, [(4, 4), (7, 6), (4, 8)], GOLD)
        _ball(im, 8, 7, 4)
        d = ImageDraw.Draw(im)
        _ellipse(d, [4, 11, 12, 13], RED)
        _line(d, [(5, 13), (6, 15)], W)
        _line(d, [(11, 13), (10, 15)], W)
        _sparkle(d, 13, 3, GOLD_L)

    def jersey(im):
        d = ImageDraw.Draw(im)
        _poly(d, [(5, 2), (7, 4), (8, 4), (10, 2), (13, 5), (11, 7), (11, 13), (4, 13), (4, 7), (2, 5)], PURPLE)
        _line(d, [(5, 2), (7, 4), (8, 4), (10, 2)], GOLD)
        _line(d, [(4, 7), (2, 5)], GOLD)
        _line(d, [(11, 7), (13, 5)], GOLD)
        _line(d, [(4, 12), (11, 12)], GOLD)
        _poly(d, [(7, 6), (9, 7), (8, 7), (8, 11), (7, 11), (7, 8), (6, 8)], W)
        _px(d, (5, 5), PURPLE_L)

    def play_emblem(im):
        d = ImageDraw.Draw(im)
        _poly(d, [(5, 9), (7, 9), (6, 14), (4, 12)], RED)
        _poly(d, [(9, 9), (11, 9), (12, 12), (10, 14)], SKY)
        _coin(im, 7, 6, 5, star=False)
        d = ImageDraw.Draw(im)
        _poly(d, [(6, 3), (6, 9), (10, 6)], GREEN)
        _px(d, (6, 4), GREEN_L)

    def shop_crate(im):
        _ball(im, 5, 4, 3)
        _coin(im, 11, 3, 3)
        d = ImageDraw.Draw(im)
        _poly(d, [(2, 7), (13, 7), (12, 14), (3, 14)], BROWN)
        _rect(d, [1, 6, 14, 8], BROWN_L)
        for x in (5, 7, 10):
            _line(d, [(x, 8), (x, 13)], BROWN_D)
        _line(d, [(3, 11), (12, 11)], BROWN_D)
        _star(im, 7, 11, 2, GOLD)

    def megaphone(im):
        d = ImageDraw.Draw(im)
        _poly(d, [(3, 6), (7, 6), (12, 3), (12, 11), (7, 9), (3, 9)], SKY)
        _rect(d, [1, 5, 3, 10], W)
        _line(d, [(7, 9), (9, 13), (6, 13), (4, 9)], STEEL, 1)
        _ellipse(d, [11, 3, 13, 11], W)
        d.arc([9, 4, 15, 10], start=-50, end=50, fill=GOLD_D)
        _line(d, [(4, 6), (10, 4)], CYAN)

    def tag_plaque(im):
        d = ImageDraw.Draw(im)
        d.rounded_rectangle([1, 4, 14, 11], radius=2, fill=PURPLE)
        d.rounded_rectangle([3, 5, 12, 10], radius=1, fill=W)
        _star(im, 5, 7, 2, GOLD)
        d = ImageDraw.Draw(im)
        for y in (6, 8, 9):
            _line(d, [(8, y), (11, y)], STEEL)

    add("icon_arena", arena)
    add("icon_back", lambda im: _arrow(im, "left", GOLD_L, GOLD))
    add("icon_prev", lambda im: _arrow(im, "left", W, SILVER))
    add("icon_next", lambda im: _arrow(im, "right", GREEN_L, GREEN))
    add("icon_book", book)
    add("icon_bot_on", bot(True))
    add("icon_bot_off", bot(False))
    add("icon_check", lambda im: _check(im))
    add("icon_chest", chest)
    add("icon_coin_stack", coin_stack)
    add("icon_crossover", crossover)
    add("icon_defense", defense)
    add("icon_discord", discord)
    add("icon_dunk", lambda im: _hoop(im, True))
    add("icon_friends", friends)
    add("icon_gem_ruby", lambda im: _gem(im, 7, 7, PINK_L, (244, 63, 111, 255), RED_D))
    add("icon_history", history)
    add("icon_leave", leave)
    add("icon_pass", pass_ball)
    add("icon_podium", podium)
    add("icon_portal", portal)
    add("icon_settings", settings)
    add("icon_shelves", shelves)
    add("icon_shop", shop)
    add("icon_star_shield", star_shield)
    add("icon_tab", tab)
    add("icon_tags", tags)

    add("dunk_tomahawk", dunk_tomahawk)
    add("dunk_windmill", dunk_windmill)
    add("dunk_between_legs", dunk_between_legs)
    add("dunk_spin_360", dunk_spin)
    add("dunk_meteor_jam", dunk_meteor)

    add("tactics_playbook", lambda im: _clipboard(im, True))
    add("court_wand", court_wand)
    add("prop_guide_clipboard", lambda im: _clipboard(im, False))
    add("prop_locker_jersey", jersey)
    add("prop_play_emblem", play_emblem)
    add("prop_podium_cup", lambda im: _trophy(im, 7, 2))
    add("prop_shop_crate", shop_crate)
    add("prop_social_megaphone", megaphone)
    add("prop_tag_plaque", tag_plaque)

    # Purchased library override: the hand-drawn painters above remain only as
    # basketball-specific fallbacks for icon_dunk, dunk_between_legs and
    # dunk_windmill.  Extra keys in the map are first-class item models and
    # are emitted by generate_resourcepack.py exactly like the original 41.
    for item_id, purchased_stem in PURCHASED_ICON_MAP.items():
        sprites[item_id] = _purchased_icon(purchased_stem)
    return sprites


# ---------------------------------------------------------------------------
# Native 16x16 standalone collector motifs for all 56 tag_card_<id> items.
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


def _accent_shades(rgb):
    def mix(target, t):
        return tuple(round(rgb[i] * (1.0 - t) + target[i] * t) for i in range(3)) + (255,)
    return mix((255, 255, 255), 0.42), tuple(rgb) + (255,), mix((0, 0, 0), 0.42)


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


def _draw_tag_motif(im: Image.Image, motif: str, accent_rgb):
    d = ImageDraw.Draw(im)
    light, accent, dark = _accent_shades(accent_rgb)

    if motif == "crown":
        _poly(d, [(2, 10), (2, 5), (5, 8), (7, 3), (10, 8), (13, 5), (13, 10)], GOLD)
        _rect(d, [2, 10, 13, 12], GOLD_D)
        _line(d, [(3, 6), (3, 9)], GOLD_L)
        for x, c in ((4, RED), (7, accent), (11, GREEN)):
            _px(d, (x, 9), c)
    elif motif == "shield":
        _shield(im, accent, light)
        d = ImageDraw.Draw(im)
        _line(d, [(5, 7), (7, 9), (10, 4)], W, 1)
    elif motif == "flame":
        _poly(d, [(7, 1), (9, 5), (12, 8), (11, 12), (8, 14), (5, 13), (3, 10), (4, 6), (6, 8), (6, 4)], RED)
        _poly(d, [(7, 6), (9, 9), (8, 12), (6, 11), (5, 8)], GOLD)
        _px(d, (6, 10), GOLD_L)
    elif motif == "snow":
        for a, b in (((7, 1), (7, 14)), ((1, 7), (14, 7)), ((3, 3), (12, 12)), ((12, 3), (3, 12))):
            _line(d, [a, b], W)
        for xy in ((7, 1), (7, 14), (1, 7), (14, 7), (3, 3), (12, 12), (12, 3), (3, 12)):
            _px(d, xy, CYAN)
        _ellipse(d, [6, 6, 9, 9], CYAN)
    elif motif == "moon":
        _ellipse(d, [2, 2, 12, 13], GOLD)
        # Erase an offset disc to leave a hard-edged crescent.
        d.ellipse([6, 1, 15, 11], fill=(0, 0, 0, 0))
        _px(d, (4, 4), GOLD_L)
        _sparkle(d, 12, 11, W)
    elif motif == "bolt":
        _poly(d, [(9, 1), (3, 9), (6, 9), (5, 14), (12, 6), (8, 6)], GOLD)
        _line(d, [(8, 3), (5, 8)], GOLD_L)
    elif motif == "star":
        _star(im, 7, 7, 6, GOLD)
        d = ImageDraw.Draw(im)
        _px(d, (7, 7), accent)
    elif motif == "ball":
        _ball(im, 7, 7, 6)
    elif motif == "target":
        for r, c in ((6, W), (5, RED), (3, W), (1, RED)):
            _ellipse(d, [7 - r, 7 - r, 7 + r, 7 + r], c)
        _line(d, [(12, 2), (8, 6)], STEEL_D)
        _poly(d, [(11, 1), (14, 2), (12, 5)], SKY)
    elif motif == "snake":
        pts = [(3, 12), (6, 9), (10, 11), (11, 7), (7, 5), (4, 7), (5, 3), (9, 2)]
        _line(d, pts, GREEN, 2)
        _ellipse(d, [8, 1, 12, 4], GREEN)
        _px(d, (10, 2), K)
        _line(d, [(12, 3), (14, 2)], RED)
        _line(d, [(12, 3), (14, 4)], RED)
    elif motif == "petal":
        for cx, cy in ((7, 3), (11, 6), (10, 11), (4, 11), (3, 6)):
            _ellipse(d, [cx - 2, cy - 2, cx + 2, cy + 2], PINK)
            _px(d, (cx - 1, cy - 1), PINK_L)
        _ellipse(d, [5, 5, 9, 9], GOLD)
    elif motif == "cross":
        pts = [(1, 3), (6, 3), (3, 7), (8, 7), (5, 11), (10, 11)]
        _line(d, pts, CYAN, 1)
        _poly(d, [(9, 9), (13, 11), (9, 13)], CYAN)
        _ball(im, 11, 4, 3)
    elif motif == "wave":
        _poly(d, [(1, 9), (4, 6), (7, 8), (10, 5), (14, 8), (14, 13), (1, 13)], SKY)
        _line(d, [(2, 9), (4, 7), (7, 9), (10, 6), (13, 8)], CYAN)
        _line(d, [(3, 11), (6, 11)], W)
        _line(d, [(9, 12), (12, 12)], W)
        _px(d, (5, 3), CYAN)
        _px(d, (9, 2), CYAN)
        _px(d, (12, 4), W)
    elif motif == "gem":
        _gem(im, 7, 7, light, accent, dark)
    elif motif == "pineapple":
        for pts in (((7, 0), (6, 4), (8, 4)), ((4, 1), (5, 5), (7, 3)), ((11, 1), (8, 3), (10, 5))):
            _poly(d, pts, GREEN)
        d.rounded_rectangle([4, 4, 11, 14], radius=3, fill=GOLD)
        for x in (6, 8, 10):
            _line(d, [(x, 5), (x - 1, 13)], GOLD_D)
            _line(d, [(x, 5), (x + 1, 13)], GOLD_D)
        _px(d, (5, 6), GOLD_L)
    else:
        _check(im)


def generate_premium_tag_emblem_items(font_tex_dir: Path, item_tex_dir: Path):
    """Overwrite tag-card PNGs with purchased or exact custom 16x16 motifs."""
    item_tex_dir.mkdir(parents=True, exist_ok=True)
    for idx, pill_path in enumerate(sorted(font_tex_dir.glob("tag_*.png"))):
        tag_id = pill_path.stem.removeprefix("tag_")
        purchased_stem = PURCHASED_TAG_ICON_MAP.get(tag_id)
        if purchased_stem is not None:
            _purchased_icon(purchased_stem).save(item_tex_dir / f"tag_card_{tag_id}.png")
            continue
        accent = _dominant_saturated_color(pill_path)
        im = _canvas()
        _draw_tag_motif(im, _tag_motif(tag_id, idx), accent)
        _finish(im).save(item_tex_dir / f"tag_card_{tag_id}.png")
