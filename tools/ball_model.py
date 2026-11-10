#!/usr/bin/env python3
"""
BosketBall 26.2 — the ball model system.

A cube, and a texture that does all the work.

Why a cube again
----------------
The ball shipped as an icosphere for one round: 320 boxes of flat palette colour and a rib
network on three great circles. It was round and it was correct and nobody liked it — the
owner of the game asked for the cube back, and he was right about the reason the cube read
better: a cube is *legible*. Six faces, one silhouette, a hard edge that survives being
six pixels wide in an inventory cell, and room for a real texture.

So the silhouette is one element again. Everything that made the old cube read as a
spinning die is fixed here instead of in geometry:

* **the network is traced in direction space.** A texel is not painted at its place on a
  face; it is painted for the *direction* it looks at from the centre of the ball. The
  seams are the ones a real basketball has — two end panels bounded by circles of latitude,
  six side panels split by meridians bent into curves — and because the seams are a function
  of the direction, two texels that meet on a cube edge are the same direction and cannot
  disagree. The seams cross all twelve edges without a joint, which is exactly what the old
  cube's painted crosses could not do.
* **the grain is world-space, on the same directions.** The pebble field is sampled at the
  texel's direction too, so the leather is one hide wrapped around the cube rather than six
  independent faces. The eight octants still carry eight slightly different tones.
* **the eight panels are the real eight.** Eight panels is what the ball has always
  claimed; here they are the two caps and six sides of the thing itself.
* **the roundness is baked.** A sphere-ish light term, an ambient-occlusion ramp into every
  cube edge and a bevel line on the lit edges are what make a cube read as a ball without
  adding a single box.

The face convention
-------------------
Minecraft maps a face's UV rectangle with the texture's *u to the right and v downward as
the face is seen from outside*, top of the texture being the top of the face: for the four
side faces that is world +Y up; for the top face it is north up; for the bottom face, south.
`FACE_FRAME` below is that table, written once, and the preview renderer, the tile painter
and the packing test all read it. Getting it wrong mirrors one face and the seam jumps at
every edge, which is exactly the failure this file exists to not have.

Units
-----
Model space is Minecraft's: 16 units per block, elements centred on (8, 8, 8). The physics
ball in `BasketballEntity` draws a unit-radius model at `VISUAL_BALL_SCALE`, so the cube is
authored with a half-extent of ``BALL_RADIUS_BLOCKS / VISUAL_BALL_SCALE * 16`` — it is
inscribed in the radius the simulation actually collides at, which is 0.285 blocks: what a
size-7 ball measures against a 9.2-block rim.
"""

from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw

# ---------------------------------------------------------------------------
# 1. Palette — mirrors fr.bosketball.util.Palette. Keep the two in sync.
# ---------------------------------------------------------------------------
TEXT = "#F8FAFC"
MUTED = "#94A3B8"
DIM = "#64748B"
GOLD = "#FBBF24"
GOLD_SOFT = "#FDE047"
WARN = "#FB923C"
SUCCESS = "#4ADE80"
DANGER = "#F87171"
ICE = "#38BDF8"
VIOLET = "#C084FC"


def _hex(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def _mix(a, b, t: float):
    tt = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * tt for i in range(3))


def _clamp255(color):
    return tuple(int(max(0, min(255, round(c)))) for c in color)


# ---------------------------------------------------------------------------
# 3. Skins
# ---------------------------------------------------------------------------
# `label` and `describe` are English and player-facing. `light_emission` is block light
# baked into the elements: it is what makes Glow and Fire read as lit objects in a dark
# arena instead of as pale textures.
SKINS: dict[str, dict] = {
    "classic": {
        "label": "Game Ball",
        "accent": WARN,
        "describe": "Full-grain leather, black channels",
        "light_emission": 0,
        "panel_dark": _hex("#8C3A12"),
        "panel_base": _hex("#DD7C34"),
        "panel_bright": _hex("#F5A05A"),
        "accent_dark": _hex("#8C3A12"),
        "accent_base": _hex("#DD7C34"),
        "accent_bright": _hex("#F5A05A"),
        "seam_dark": _hex("#0C0806"),
        "seam_base": _hex("#191311"),
        "seam_bright": _hex("#4A3527"),
        "pebble": 1.00,
        "sheen": 0.55,
        "split_axis": None,
        "animated": False,
    },
    "street": {
        "label": "Street Worn",
        "accent": MUTED,
        "describe": "Matte asphalt, bone channels",
        "light_emission": 0,
        "panel_dark": _hex("#181B21"),
        "panel_base": _hex("#2F343D"),
        "panel_bright": _hex("#4C5361"),
        "accent_dark": _hex("#181B21"),
        "accent_base": _hex("#2F343D"),
        "accent_bright": _hex("#4C5361"),
        "seam_dark": _hex("#6B5732"),
        "seam_base": _hex("#C9A96B"),
        "seam_bright": _hex("#EBD7A6"),
        "pebble": 1.45,
        "sheen": 0.12,
        "split_axis": None,
        "animated": False,
    },
    "blacktop": {
        "label": "Blacktop",
        "accent": GOLD_SOFT,
        "describe": "Outdoor rubber, cream seams",
        "light_emission": 0,
        "panel_dark": _hex("#1F2228"),
        "panel_base": _hex("#383E47"),
        "panel_bright": _hex("#59616F"),
        "accent_dark": _hex("#1F2228"),
        "accent_base": _hex("#383E47"),
        "accent_bright": _hex("#59616F"),
        "seam_dark": _hex("#8A7E62"),
        "seam_base": _hex("#F1E4C3"),
        "seam_bright": _hex("#FFF8E4"),
        "pebble": 1.70,
        "sheen": 0.30,
        "split_axis": None,
        "animated": False,
    },
    "glow": {
        "label": "Glow",
        "accent": ICE,
        "describe": "Self-lit ice-cyan channels",
        "light_emission": 14,
        "panel_dark": _hex("#081727"),
        "panel_base": _hex("#0F3352"),
        "panel_bright": _hex("#1B5C89"),
        "accent_dark": _hex("#08202E"),
        "accent_base": _hex("#123F55"),
        "accent_bright": _hex("#1E5C78"),
        "seam_dark": _hex("#2FA6CE"),
        "seam_base": _hex("#5FE6FF"),
        "seam_bright": _hex("#D6FAFF"),
        "pebble": 0.55,
        "sheen": 0.85,
        "split_axis": None,
        "animated": False,
    },
    "team_home": {
        "label": "Team Home",
        "accent": ICE,
        "describe": "Court blue, ivory channels",
        "light_emission": 0,
        "panel_dark": _hex("#13225F"),
        "panel_base": _hex("#2A4DAE"),
        "panel_bright": _hex("#4C7EE4"),
        "accent_dark": _hex("#13225F"),
        "accent_base": _hex("#2A4DAE"),
        "accent_bright": _hex("#4C7EE4"),
        "seam_dark": _hex("#B4AF9F"),
        "seam_base": _hex("#F2EFE5"),
        "seam_bright": _hex("#FFFFFF"),
        "pebble": 0.95,
        "sheen": 0.48,
        "split_axis": None,
        "animated": False,
    },
    "team_away": {
        "label": "Team Away",
        "accent": DANGER,
        "describe": "Signal red, ivory channels",
        "light_emission": 0,
        "panel_dark": _hex("#6C0F1D"),
        "panel_base": _hex("#B42336"),
        "panel_bright": _hex("#E44E5E"),
        "accent_dark": _hex("#6C0F1D"),
        "accent_base": _hex("#B42336"),
        "accent_bright": _hex("#E44E5E"),
        "seam_dark": _hex("#B4AF9F"),
        "seam_base": _hex("#F2EFE5"),
        "seam_bright": _hex("#FFFFFF"),
        "pebble": 0.95,
        "sheen": 0.48,
        "split_axis": None,
        "animated": False,
    },
    "two_tone": {
        "label": "Two-Tone",
        "accent": VIOLET,
        "describe": "Leather, ABA blue half",
        "light_emission": 0,
        "panel_dark": _hex("#8C3A12"),
        "panel_base": _hex("#DD7C34"),
        "panel_bright": _hex("#F5A05A"),
        "accent_dark": _hex("#0D2154"),
        "accent_base": _hex("#1F4FA8"),
        "accent_bright": _hex("#4180DC"),
        "seam_dark": _hex("#0C0806"),
        "seam_base": _hex("#191311"),
        "seam_bright": _hex("#4A3527"),
        "pebble": 1.00,
        "sheen": 0.58,
        "split_axis": 1,
        "animated": False,
    },
    "fire": {
        "label": "Fire",
        "accent": "#FF6A2B",
        "describe": "Animated molten channels",
        "light_emission": 15,
        "panel_dark": _hex("#2E1408"),
        "panel_base": _hex("#7A3410"),
        "panel_bright": _hex("#B4561A"),
        "accent_dark": _hex("#2A0E05"),
        "accent_base": _hex("#6E2408"),
        "accent_bright": _hex("#8C3206"),
        "seam_dark": _hex("#C4521A"),
        "seam_base": _hex("#FFA53C"),
        "seam_bright": _hex("#FFE7A8"),
        "pebble": 1.10,
        "sheen": 0.70,
        "split_axis": None,
        "animated": True,
    },
}

# Ship order: the UI enumerates BallSkin in this order.
SKIN_ORDER = ["classic", "street", "blacktop", "glow", "team_home", "team_away", "two_tone", "fire"]


# ---------------------------------------------------------------------------
# 4. Geometry — one cube, inscribed in the radius the physics uses
# ---------------------------------------------------------------------------
VISUAL_BALL_SCALE = 0.52      # BasketballEntity.VISUAL_BALL_SCALE
BALL_RADIUS_BLOCKS = 0.285    # == PhysicsMath.BALL_RADIUS
BALL_HALF_UNITS = BALL_RADIUS_BLOCKS / VISUAL_BALL_SCALE * 16.0   # 8.77 model units
CENTRE = 8.0

FACE_ORDER = ("up", "down", "north", "south", "west", "east")

# Texture layout: six 16 px tiles in a 3 x 2 atlas, one file per skin. Sixteen pixels is
# the pack's icon grid (`tools/icon_language.py`), so the ball is drawn at the same scale
# as the 57 other sprites it sits beside instead of at a resolution nobody else uses.
FACE_PX = 16
TILE_COLS, TILE_ROWS = 3, 2
ATLAS_W, ATLAS_H = FACE_PX * TILE_COLS, FACE_PX * TILE_ROWS
FACE_TILES = {
    "up": (0, 0), "down": (1, 0), "north": (2, 0),
    "south": (0, 1), "west": (1, 1), "east": (2, 1),
}

FACE_NORMALS = {
    "north": (0.0, 0.0, -1.0),
    "south": (0.0, 0.0, 1.0),
    "west": (-1.0, 0.0, 0.0),
    "east": (1.0, 0.0, 0.0),
    "up": (0.0, 1.0, 0.0),
    "down": (0.0, -1.0, 0.0),
}

# The Minecraft face convention: `right` and `up` are the texture's axes as the face is
# seen from outside. u runs along `right`, v runs *down*, so the top of the tile is `up`.
FACE_FRAME = {
    "north": {"normal": (0.0, 0.0, -1.0), "right": (-1.0, 0.0, 0.0), "up": (0.0, 1.0, 0.0)},
    "south": {"normal": (0.0, 0.0, 1.0), "right": (1.0, 0.0, 0.0), "up": (0.0, 1.0, 0.0)},
    "west": {"normal": (-1.0, 0.0, 0.0), "right": (0.0, 0.0, 1.0), "up": (0.0, 1.0, 0.0)},
    "east": {"normal": (1.0, 0.0, 0.0), "right": (0.0, 0.0, -1.0), "up": (0.0, 1.0, 0.0)},
    "up": {"normal": (0.0, 1.0, 0.0), "right": (1.0, 0.0, 0.0), "up": (0.0, 0.0, -1.0)},
    "down": {"normal": (0.0, -1.0, 0.0), "right": (1.0, 0.0, 0.0), "up": (0.0, 0.0, 1.0)},
}

# ---------------------------------------------------------------------------
# 5. Pixel-art style — the house icon language, on a cube face
# ---------------------------------------------------------------------------
# `tools/icon_language.py` is the rule book for every sprite in this pack, and
# `docs/agents/research/icons.md` holds the measurements behind it. Read off the 57
# hand-drawn 16 px icons that ship: one ink (#161E2B) on 56 of them, one key light from
# above with a top-left bias, binary alpha, not one isolated pixel, and 9 colours at the
# median. A ball shaded in smooth gradients next to that set reads as a render pasted into
# a pixel-art pack. So the ball is painted in the same language: four flat leather tones
# plus a specular, ordered dithering where the tones meet, and exactly one ink for the
# cube's edges and for every seam.
INK = (22, 30, 43)          # == icon_language.INK, the single outline of the whole pack
RAMP_SATURATION = 1.12      # the icons are vivid; a client-lit face is not
RAMP_STEPS = 6              # six flat tones: enough steps for a sphere, few enough
                            # to stay readable as pixel art
SPEC_LIFT = 0.42            # how far the specular is pushed toward white
SEAM_HALF_TEXEL = 0.5       # half a texel: exactly one pixel of channel
PANEL_TONE = 0.08           # the eight octants are eight panels: one step between them
DOME = 0.18                 # how much a face bulges towards its middle
LIGHT_FACING = 0.62         # the key light sits between three faces, so the specular
                            # lands where they meet: a small spot, not a lit face
CREASE = 0.13               # the one texel of shadow a face's edge sits in
EDGE_AO = 0.30              # the cube's edge, in one texel of shadow
SPECKLE_DASHES = 5          # two-texel grain dashes per face; never a lone pixel
DITHER = 0.42               # how strongly the ordered dither softens a tone boundary
FIRE_FRAMES = 4

#: Ordered dithering. A 4x4 Bayer matrix tiled over the face is what turns four flat tones
#: into a gradient the eye reads as a round ball, with no blended pixel anywhere: it is the
#: one trick that separates pixel art from a downscaled render.
BAYER4 = (np.array([[0, 8, 2, 10],
                    [12, 4, 14, 6],
                    [3, 11, 1, 9],
                    [15, 7, 13, 5]], np.float64) + 0.5) / 16.0

#: The pack's key light: from above, biased top-left, exactly like every icon in the set
#: (measured median vertical shading slope -1.78, horizontal 0.00 — see icons.md).
LIGHT = np.array([-0.42, 0.80, -0.42])
LIGHT = LIGHT / np.linalg.norm(LIGHT)

# ---- the seam network, in angles on the ball the cube stands in for --------------------
# A basketball is read by its channels, and a cube face is only 16 px, so the network has
# to be legible at one texel per line. Three families, all defined in *direction* space so
# that two texels meeting on a cube edge are the same texel of the same seam: an equator
# that waves, four meridians bent into an S, and two cap rings near the poles. Every side
# face therefore carries one curved vertical and one wavy horizontal — the cross a
# basketball has — and each pole carries a ring with a cross inside it.
MERIDIANS = 4
MERIDIAN_PHASE = math.radians(0.0)   # one meridian down the middle of each side face: on a
                                     # 16 px face, one seam and one equator is a ball; two
                                     # verticals and an equator is a brick wall
S_BEND = math.radians(0.0)      # straight: a bowed 1-px line zigzags at this size
EQ_WOBBLE = math.radians(0.0)   # level, for the same reason
CAP_LAT = math.radians(56.0)    # where the ring each pole sits in runs

#: Half a texel, in radians at the middle of a face. The seams are nudged onto texel
#: *centres* with it: a line that lands exactly between two rows covers half of each and is
#: painted two pixels wide, which is the difference between a clean seam and a thick one.
HALF_TEXEL = (math.pi * 0.5) / FACE_PX * 0.5


def _skin_seed(skin_id: str, salt: int) -> int:
    """A stable per-skin seed: two skins must not share a grain, and a rebuild must match."""
    total = salt * 7919
    for i, ch in enumerate(skin_id):
        total = (total * 131 + ord(ch) * (i + 3)) & 0xFFFFFF
    return total % 100003


# ---------------------------------------------------------------------------
# 6. Colour: the ramps a face is painted from
# ---------------------------------------------------------------------------
def _hash_u32(a: np.ndarray, b: np.ndarray, c: np.ndarray, seed: np.ndarray) -> np.ndarray:
    """A cheap, deterministic hash in 0..1 — the only randomness in the texture."""
    h = (a * 374761393 + b * 668265263 + c * 1274126177 + seed * 2246822519) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF).astype(np.float64) / 65535.0


def _luma(rgb) -> float:
    rgb = np.asarray(rgb, np.float64)
    return float(0.2126 * rgb[..., 0].mean() + 0.7152 * rgb[..., 1].mean()
                 + 0.0722 * rgb[..., 2].mean())


def _shift_hue(rgb: np.ndarray, degrees: float) -> np.ndarray:
    """Rotate a colour's hue. Used to warm the lights and cool the darks of a ramp."""
    import colorsys
    r, g, b = (float(v) / 255.0 for v in rgb)
    h, l, sat = colorsys.rgb_to_hls(r, g, b)
    h = (h + degrees / 360.0) % 1.0
    r, g, b = colorsys.hls_to_rgb(h, l, sat)
    return np.array([r, g, b], np.float64) * 255.0


def _saturate(rgb: np.ndarray, factor: float) -> np.ndarray:
    """Scale a colour's saturation, leaving its lightness alone."""
    import colorsys
    r, g, b = (float(v) / 255.0 for v in rgb)
    h, l, sat = colorsys.rgb_to_hls(r, g, b)
    sat = min(1.0, sat * factor)
    r, g, b = colorsys.hls_to_rgb(h, l, sat)
    return np.array([r, g, b], np.float64) * 255.0


def _ramp_between(dark, base, light) -> np.ndarray:
    """`RAMP_STEPS` flat tones anchored on a skin's own three colours.

    A straight line from the darkest tone to the brightest one is the obvious ramp and the
    wrong one: it passes under the colour the skin was designed around, so most of the ball
    ends up in the muddy bottom half of the line. The ramp therefore passes *through* the
    base tone, which keeps the leather the colour it was picked to be, and the ends are
    extrapolated from it.

    The stops are then hue-shifted: the shadows drift a few degrees toward red and the
    lights a few degrees toward yellow. That is pixel art's oldest shading rule, and it is
    the difference between six steps that read as tanned leather and six that read as
    plastic.
    """
    dark = np.asarray(dark, np.float64)
    base = np.asarray(base, np.float64)
    light = np.asarray(light, np.float64)
    stops = np.stack([
        np.clip(dark * 0.70, 0.0, 255.0),
        dark,
        dark + (base - dark) * 0.60,
        base,
        base + (light - base) * 0.75,
        np.clip(light + (255.0 - light) * 0.18, 0.0, 255.0),
    ])
    if len(stops) != RAMP_STEPS:                      # a ramp of another length: resample
        pos = np.linspace(0.0, 1.0, len(stops))
        want = np.linspace(0.0, 1.0, RAMP_STEPS)
        stops = np.stack([np.interp(want, pos, stops[:, c]) for c in range(3)]).T
    amount = np.linspace(-5.0, 5.0, len(stops))
    shifted = np.stack([_shift_hue(c, a) for c, a in zip(stops, amount)])
    # The pack's icons are drawn in vivid orange, and a texture that is only the skin's own
    # palette reads pale beside them: a face lit by the client loses more saturation than a
    # flat sprite does. Ten per cent buys the leather back without leaving the palette.
    return np.clip(np.stack([_saturate(c, RAMP_SATURATION) for c in shifted]), 0.0, 255.0)


def skin_ramp(spec: dict) -> np.ndarray:
    """Four flat leather tones, deep to light, from the skin's own three-stop palette.

    Four and not two because a ball is round and two tones read as a sticker; four and not
    eight because at 16 px each extra tone costs a texel of clarity. The dither in
    `render_face_tile` is what buys the in-between steps.
    """
    return _ramp_between(spec["panel_dark"], spec["panel_base"], spec["panel_bright"])


def accent_ramp(spec: dict) -> np.ndarray:
    """The same four tones for a skin that splits its leather in two."""
    return _ramp_between(spec["accent_dark"], spec["accent_base"], spec["accent_bright"])


def channel_ramp(spec: dict) -> np.ndarray:
    """The seam's colours, dark to bright.

    A channel darker than the leather it cuts is drawn in the pack's ink — that is what
    makes the Game Ball's seams read as black at 16 px. A channel *brighter* than the
    leather (Street's bone, Glow's ice, Fire's molten) is the skin's signature and is kept
    in its own colour instead, which is the only reason those three skins are recognisable
    at icon size.
    """
    seam = np.array(spec["seam_base"], np.float64)
    leather = np.array(spec["panel_base"], np.float64)
    if _luma(seam) < _luma(leather) * 0.75:
        # A dark channel is drawn in the skin's own black rather than the pack's ink: the
        # ink is a cool slate that suits a silhouette, and a channel is not a silhouette —
        # on leather it read as a wireframe drawn over the ball.
        dark = np.array(spec["seam_dark"], np.float64)
        return np.stack([dark, dark, dark])
    return np.stack([np.array(spec["seam_dark"], np.float64), seam,
                     np.array(spec["seam_bright"], np.float64)])


def spec_colour(spec: dict) -> np.ndarray:
    """The one punched highlight: the leather's lightest tone pushed toward white."""
    light = np.array(spec["panel_bright"], np.float64)
    return light + (255.0 - light) * SPEC_LIFT


def _bayer(n: int) -> np.ndarray:
    """The 4x4 ordered-dither matrix tiled over an n x n face."""
    reps = (n + 3) // 4
    return np.tile(BAYER4, (reps, reps))[:n, :n]


def speckle_dashes(seed: int, face: str) -> list[tuple[int, int]]:
    """Where the grain dashes go on one face.

    Leather grain at 16 px is not noise, it is a handful of deliberate marks. Each mark is
    two texels side by side and never one: a lone dark pixel at this size reads as dust on
    the item, which is the one thing `icon_language.drop_isolation` exists to delete.
    """
    mixed = int(_hash_u32(np.int64(ord(face[0])), np.int64(len(face)), np.int64(3),
                          np.int64(seed)) * 1_000_003.0) % (2 ** 32)
    rng = np.random.default_rng(mixed)
    low, high = 2, FACE_PX - 3
    return [(int(rng.integers(low, high)), int(rng.integers(low, high)))
            for _ in range(SPECKLE_DASHES)]


# ---------------------------------------------------------------------------
# 7. One face tile
# ---------------------------------------------------------------------------
def _face_geometry(face: str, n: int | None = None):
    """Texel grid, world direction and normalised direction for one face."""
    n = FACE_PX if n is None else n
    idx = (np.arange(n) + 0.5) / n
    u_grid, v_grid = np.meshgrid(idx, idx)
    frame = FACE_FRAME[face]
    normal = np.array(frame["normal"], np.float64)
    right = np.array(frame["right"], np.float64)
    up = np.array(frame["up"], np.float64)
    a = u_grid * 2.0 - 1.0          # -1 left .. +1 right
    b = 1.0 - v_grid * 2.0          # +1 top  .. -1 bottom
    dirs = (normal[None, None, :]
            + right[None, None, :] * a[..., None]
            + up[None, None, :] * b[..., None])
    length = np.linalg.norm(dirs, axis=2, keepdims=True)
    return u_grid, v_grid, dirs, dirs / length


def seam_field(unit: np.ndarray) -> dict:
    """Angular distance to the nearest channel, and how far along it a texel sits."""
    lat = np.arcsin(np.clip(unit[..., 1], -1.0, 1.0))
    lon = np.arctan2(unit[..., 2], unit[..., 0])

    # The equator, waving once per face. A function of longitude, so it cannot break at an
    # edge; a quarter-turn period, so all four side faces get the same wave.
    d_equator = np.abs(lat - HALF_TEXEL - EQ_WOBBLE * np.sin(4.0 * lon))

    # Four meridians. One falls down the middle of each side face, and all four meet at
    # each pole, so every face of the cube carries the same cross — the pattern a
    # basketball has had since the first one was stitched, and the only one that still
    # reads when a face is sixteen pixels across.
    step = 2.0 * math.pi / MERIDIANS
    wrapped = np.stack([np.abs((lon - HALF_TEXEL - MERIDIAN_PHASE - index * step + math.pi)
                        % (2.0 * math.pi) - math.pi)
                        for index in range(MERIDIANS)])
    d_meridian = np.min(wrapped, axis=0) * np.abs(np.cos(lat))

    use_equator = d_equator <= d_meridian
    dist = np.where(use_equator, d_equator, d_meridian)
    along = np.where(use_equator, lon, lat)
    return {"dist": np.where(np.isfinite(dist), dist, 1.0e3), "along": along}


def texel_scale(unit: np.ndarray) -> np.ndarray:
    """How much angle one texel spans, locally, in radians.

    A seam is one texel wide *on the face*, not one fixed angle: near a cube edge the same
    angle covers fewer pixels, so a fixed angular width would thin the line there and break
    it. Measuring the local step keeps every channel exactly one pixel from edge to edge,
    which is also what keeps it continuous across the twelve cube edges.
    """
    du = np.linalg.norm(np.diff(unit, axis=1), axis=2)   # (n, n-1) step to the right
    dv = np.linalg.norm(np.diff(unit, axis=0), axis=2)   # (n-1, n) step down
    right = np.concatenate([du[:, :1], du], axis=1)
    left = np.concatenate([du, du[:, -1:]], axis=1)
    down = np.concatenate([dv[:1, :], dv], axis=0)
    up = np.concatenate([dv, dv[-1:, :]], axis=0)
    return 0.25 * (right + left + down + up)


def _seam_mask(face: str, field: dict) -> np.ndarray:
    """Which texels of one face are channel, decided on a 4x supersampled grid."""
    sub = 4
    _, _, _, fine = _face_geometry(face, FACE_PX * sub)
    fine_field = seam_field(fine)
    hit = (fine_field["dist"] / texel_scale(fine)) < SEAM_HALF_TEXEL * sub
    coverage = hit.reshape(FACE_PX, sub, FACE_PX, sub).mean(axis=(1, 3))
    return coverage >= 0.45


def render_face_tile(skin_id: str, face: str, frame: float = 0.0) -> Image.Image:
    """One face of one skin, painted at 16 px in the pack's icon language."""
    spec = SKINS[skin_id]
    n = FACE_PX
    u_grid, v_grid, dirs, unit = _face_geometry(face)

    # ---- form: one key light, four tones, ordered dithering ---------------------------
    # A face spans a quarter turn, so a full Lambert term would run from black to white
    # across it and the ball would look lit by a torch. The range is deliberately narrow:
    # the tones do the rounding, the light only biases them.
    lambert = 0.5 + 0.5 * (unit @ LIGHT)
    # Quantise the *face* first: a face is one plane, so it wants one tone, with the corners
    # drifting a step either way. Shading a face texel by texel puts most of the face on a
    # tone boundary and the ordered dither turns it into a halftone screen, which is exactly
    # what made the first draft look like a crate of checkers.
    facing = float(np.array(FACE_NORMALS[face], np.float64) @ LIGHT)
    face_lambert = 0.5 + 0.5 * facing
    face_tone = int(np.clip(round(0.36 + 0.60 * face_lambert) * RAMP_STEPS - 0.5, 0,
                            RAMP_STEPS - 1))
    shade = (face_tone + 0.5) / RAMP_STEPS + 0.55 * (lambert - face_lambert)
    sign_bits = ((dirs[..., 0] >= 0.0).astype(np.int64)
                 + 2 * (dirs[..., 1] >= 0.0).astype(np.int64)
                 + 4 * (dirs[..., 2] >= 0.0).astype(np.int64))
    panel = PANEL_TONE * np.array(
        [(_hash_u32(np.int64(k), np.int64(11), np.int64(5), 97) - 0.5) * 2.0 for k in range(8)])
    # The cube's edge, one texel of shadow inside the ink line, so a face does not read as
    # a flat card glued to its neighbours.
    edge = np.minimum(np.minimum(u_grid, 1.0 - u_grid), np.minimum(v_grid, 1.0 - v_grid)) * n
    ao = np.clip(1.0 - edge / 2.0, 0.0, 1.0)
    # The limb. A sphere darkens all the way round its edge, and on a cube that edge is the
    # one texel inside the ink line: without it six flat squares read as a crate, with it
    # they read as one round thing. This is the term a ball icon gets from its circular
    # silhouette, rebuilt inside a square face.
    radial = np.hypot(u_grid * 2.0 - 1.0, v_grid * 2.0 - 1.0)
    crease = np.clip(1.0 - edge / 2.0, 0.0, 1.0)     # the edge of a face recedes
    value = np.clip(shade - DOME * radial ** 2 - CREASE * crease + panel[sign_bits], 0.0, 1.0)
    # Hard steps, no dither: four flat tones whose boundaries follow the sphere. A dither
    # here scatters two tones across a whole band and the face reads as fabric; a hard step
    # reads as a painted pixel-art form, which is what the rest of the pack does.
    index = np.clip(np.round(value * RAMP_STEPS - 0.5), 0, RAMP_STEPS - 1).astype(np.int64)

    # ---- grain: five two-texel dashes, one tone down ---------------------------------
    for x, y in speckle_dashes(_skin_seed(skin_id, 5), face):
        index[y, x] = max(index[y, x] - 1, 0)
        index[y, x + 1] = max(index[y, x + 1] - 1, 0)

    # ---- colour ----------------------------------------------------------------------
    colour = skin_ramp(spec)[index]
    if spec["split_axis"] is not None:
        axis = spec["split_axis"]
        accent = (unit[..., axis] > 0.0)[..., None]
        colour = np.where(accent, accent_ramp(spec)[index], colour)

    # ---- the channels ------------------------------------------------------------------
    # The channels are drawn on a 4x supersampled grid and then thresholded per texel. A
    # one-texel line sampled only at texel centres falls between two rows half the time and
    # vanishes, which is how a face ends up with no equator at all; supersampling picks the
    # row the seam actually crosses, and keeps it exactly one pixel wide everywhere.
    field = seam_field(unit)
    seam = _seam_mask(face, field)
    channel = channel_ramp(spec)
    if spec["animated"]:
        # Fire: the molten channel rolls along its own length, one cycle per strip.
        phase = np.floor((field["along"] + math.pi) / (2.0 * math.pi) * 9.0)
        tone = ((phase + frame * FIRE_FRAMES * 2.0).astype(np.int64) % 3)
        seam_colour = channel[tone]
    else:
        seam_colour = np.broadcast_to(channel[1], (n, n, 3))
    colour = np.where(seam[..., None], seam_colour, colour)

    # ---- the specular, then the ink ----------------------------------------------------
    # The specular is one 2x2 block at the texel that faces the key light hardest. A
    # threshold instead lights a whole quadrant of the lit face, which reads as a blown
    # highlight; a fixed block reads as a drawn one, the way a ball icon does it.
    facing = unit @ LIGHT
    spark = np.zeros((n, n), bool)
    if float(facing.max()) > LIGHT_FACING:
        iy, ix = np.unravel_index(int(np.argmax(facing)), facing.shape)
        spark[iy:iy + 2, ix:ix + 2] = True
    spark &= ~seam
    colour = np.where(spark[..., None], spec_colour(spec), colour)

    # The cube's edge, drawn in the pack's ink on the shadow side and left lit on the light
    # side: an outline that is the same weight all the way round reads as a drawn box, and
    # an edge that catches the key light reads as a rounded one.
    # No ink frame around a face. A cube face has no silhouette to outline — the client
    # already shades the six facets apart — and at 16 px a frame plus a limb ring eats
    # three quarters of the leather, which is what turned the first drafts into a crate.
    # The pack's ink belongs on the channels, which is where it is.

    return Image.fromarray(np.clip(colour, 0.0, 255.0).astype(np.uint8), "RGB")


# ---------------------------------------------------------------------------
# 8. The atlas
# ---------------------------------------------------------------------------
def render_atlas(skin_id: str, frame: float = 0.0) -> Image.Image:
    """One atlas per skin: six face tiles, laid out exactly as `FACE_TILES` says."""
    atlas = Image.new("RGB", (ATLAS_W, ATLAS_H), (0, 0, 0))
    for face in FACE_ORDER:
        col, row = FACE_TILES[face]
        atlas.paste(render_face_tile(skin_id, face, frame), (col * FACE_PX, row * FACE_PX))
    return atlas


# Minecraft maps *every* texture onto a 0..16 UV grid, whatever its pixel size: a 384 x 256
# atlas is addressed exactly like a 16 x 16 one. The wiki calls anything outside 0..16
# "inconsistent", and `texture_size` — the field that looks like it changes this — is a
# Blockbench hint the client never reads. Authoring UVs in atlas pixels here would ship a
# ball painted with the top-left 16 pixels of its atlas: one flat colour.
UV_UNITS = 16.0


def face_uv(face: str):
    """The model UV rectangle of one face's tile, in Minecraft's 0..16 UV space."""
    col, row = FACE_TILES[face]
    return [col * UV_UNITS / TILE_COLS, row * UV_UNITS / TILE_ROWS,
            (col + 1) * UV_UNITS / TILE_COLS, (row + 1) * UV_UNITS / TILE_ROWS]


def uv_pixels(uv):
    """A model UV rectangle in atlas pixels — what the preview renderer samples."""
    return [uv[0] / UV_UNITS * ATLAS_W, uv[1] / UV_UNITS * ATLAS_H,
            uv[2] / UV_UNITS * ATLAS_W, uv[3] / UV_UNITS * ATLAS_H]


def render_textures(skin_id: str) -> dict[str, Image.Image]:
    """Every PNG a skin needs, keyed by model texture slot."""
    if not SKINS[skin_id]["animated"]:
        return {"0": render_atlas(skin_id)}
    strip = Image.new("RGB", (ATLAS_W, ATLAS_H * FIRE_FRAMES), (0, 0, 0))
    for f in range(FIRE_FRAMES):
        strip.paste(render_atlas(skin_id, frame=f / FIRE_FRAMES), (0, f * ATLAS_H))
    return {"0": strip}


def texture_filename(skin_id: str, key: str) -> str:
    return f"ball_{skin_id}.png"


def animation_meta(skin_id: str):
    """`.mcmeta` only for a ball that is genuinely animated."""
    if not SKINS[skin_id]["animated"]:
        return None
    return {"animation": {"frametime": 3, "interpolate": True,
                          "width": ATLAS_W, "height": ATLAS_H}}


# ---------------------------------------------------------------------------
# 9. Model geometry
# ---------------------------------------------------------------------------
def build_elements(spec: dict | None = None):
    """The silhouette: one cube, six faces, one tile each.

    The cube is inscribed in the radius the simulation collides at, so the flat face of the
    ball is exactly where the physics ball's surface is. Its corners reach 1.41x that, which
    is the price of a cube and is what the old ball looked like too.
    """
    low = CENTRE - BALL_HALF_UNITS
    high = CENTRE + BALL_HALF_UNITS
    element = {
        "name": "ball",
        "from": [round(low * 16.0) / 16.0] * 3,
        "to": [round(high * 16.0) / 16.0] * 3,
        "faces": {face: {"uv": face_uv(face), "texture": "#0"} for face in FACE_ORDER},
    }
    if spec is not None and spec.get("light_emission"):
        element["light_emission"] = int(spec["light_emission"])
        # An emissive ball shades itself; letting the client darken four of its faces on top
        # of the baked glow is what made Glow look like a dirty grey cube.
        element["shade"] = False
    return [element]


def display_transforms() -> dict:
    """Inventory, hand, ground and display-entity presentation.

    `ItemDisplay` entities are drawn through `fixed`, and the physics ball sets its own
    transform to NONE, so `fixed` has to present the ball at final size and upright. The
    hand slots stay at zero scale: the ball is the projectile and the rig draws the hands.
    A test keeps a floating ball from coming back to the first-person hand.
    """
    return {
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]},
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2.4, 0], "scale": [0.4, 0.4, 0.4]},
        "gui": {"rotation": [16, 34, 0], "translation": [0, 1.1, 0], "scale": [0.46, 0.46, 0.46]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 12.0, 0], "scale": [1.0, 1.0, 1.0]},
        # ItemDisplay draws through `fixed`; a 45 degree yaw shows a corner, which is the
        # angle where three faces and the channel network between them are all visible.
        "fixed": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    }


def build_model(skin_id: str) -> dict:
    spec = SKINS[skin_id]
    return {
        "credit": "BosketBall 26.2 ball system - one cube, eight panels, six painted faces",
        "ambientocclusion": True,
        "gui_light": "front",
        "textures": {"0": f"bosketball:item/ball_{skin_id}",
                     "particle": f"bosketball:item/ball_{skin_id}"},
        "elements": build_elements(spec),
        "display": display_transforms(),
    }


def strip_meta(model: dict) -> dict:
    """Drops preview bookkeeping so the shipped JSON holds only client keys."""
    out = dict(model)
    out["elements"] = [{k: v for k, v in el.items() if not k.startswith("_")} for el in model["elements"]]
    return out


def element_count() -> int:
    return len(build_elements())


def face_count() -> int:
    """Kept for the preview copy: the ball is six faces, not a pile of boxes."""
    return len(FACE_ORDER)


# ---------------------------------------------------------------------------
# 10. Preview renderer
# ---------------------------------------------------------------------------
# A per-pixel ray cast against the same boxes the client receives, sampling the same
# textures through the same face convention. It is not Minecraft — but it answers the only
# two questions that matter here: does the channel network read as one continuous ball, and
# do two skins look like one product.
_LIGHT = LIGHT
_FACE_SHADE = {"up": 1.00, "down": 0.55, "north": 0.82, "south": 0.82, "west": 0.68, "east": 0.68}


def _camera(yaw_deg: float, pitch_deg: float):
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    forward = np.array([math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    right = np.array([math.cos(yaw), 0.0, -math.sin(yaw)])
    up = np.cross(forward, right)
    return forward, right, up


def _element_faces(el):
    x1, y1, z1 = el["from"]
    x2, y2, z2 = el["to"]
    return {
        "north": ((x1, y1, z1), (x1, y2, z1), (x2, y2, z1), (x2, y1, z1)),
        "south": ((x2, y1, z2), (x1, y1, z2), (x1, y2, z2), (x2, y2, z2)),
        "west": ((x1, y1, z2), (x1, y1, z1), (x1, y2, z1), (x1, y2, z2)),
        "east": ((x2, y1, z1), (x2, y1, z2), (x2, y2, z2), (x2, y2, z1)),
        "up": ((x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)),
        "down": ((x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)),
    }


def _face_uv_from_point(face: str, point, lo, hi):
    """Texture (u, v) of a point on a face, in the Minecraft convention."""
    x, y, z = point[..., 0], point[..., 1], point[..., 2]
    fx = (x - lo[0]) / max(hi[0] - lo[0], 1e-9)
    fy = (y - lo[1]) / max(hi[1] - lo[1], 1e-9)
    fz = (z - lo[2]) / max(hi[2] - lo[2], 1e-9)
    if face == "up":
        return fx, fz
    if face == "down":
        return fx, 1.0 - fz
    if face == "north":
        return 1.0 - fx, 1.0 - fy
    if face == "south":
        return fx, 1.0 - fy
    if face == "west":
        return fz, 1.0 - fy
    return 1.0 - fz, 1.0 - fy


def _bilinear(image: np.ndarray, x: np.ndarray, y: np.ndarray) -> np.ndarray:
    h, w = image.shape[:2]
    x = np.clip(x - 0.5, 0.0, w - 1.0)
    y = np.clip(y - 0.5, 0.0, h - 1.0)
    x0 = np.floor(x).astype(np.int64)
    y0 = np.floor(y).astype(np.int64)
    x1 = np.minimum(x0 + 1, w - 1)
    y1 = np.minimum(y0 + 1, h - 1)
    tx = (x - x0)[..., None]
    ty = (y - y0)[..., None]
    top = image[y0, x0] * (1 - tx) + image[y0, x1] * tx
    bottom = image[y1, x0] * (1 - tx) + image[y1, x1] * tx
    return top * (1 - ty) + bottom * ty


def render_ball(model: dict, textures: dict, size: int = 128, yaw: float = 34.0,
                pitch: float = 18.0, background=(0, 0, 0, 0), ss: int = 2,
                light=None, zoom: float = 0.96, flat: bool = False,
                smooth: bool = False) -> Image.Image:
    """Orthographic ray cast of one ball."""
    forward, right, up = _camera(yaw, pitch)
    light = _LIGHT if light is None else np.asarray(light, np.float64)
    light = light / (np.linalg.norm(light) or 1.0)
    w = h = size * ss
    centre = np.array([CENTRE, CENTRE, CENTRE])
    scale = (min(w, h) * 0.5 * zoom) / BALL_HALF_UNITS

    gx = (np.arange(w) + 0.5 - w * 0.5) / scale
    gy = -(np.arange(h) + 0.5 - h * 0.5) / scale
    sx, sy = np.meshgrid(gx, gy)
    # Every ray starts *outside* the box, well behind it along the view axis. Starting at the
    # ball's centre plane instead makes the entry slab fall behind the ray, and the renderer
    # then picks the face on the far side of the model: invisible while all six faces are the
    # same orange, and a wrong-face ball the moment they are not.
    origin = (centre[None, None, :]
              + right[None, None, :] * sx[..., None]
              + up[None, None, :] * sy[..., None]
              - forward * (4.0 * BALL_HALF_UNITS))
    direction = forward

    colour = np.zeros((h, w, 3), np.float64)
    if background[3] > 0:
        colour[:, :] = background[:3]
    depth = np.full((h, w), np.inf)
    covered = np.zeros((h, w), bool)

    for el in model["elements"]:
        lo = np.array(el["from"], np.float64)
        hi = np.array(el["to"], np.float64)
        with np.errstate(divide="ignore", invalid="ignore"):
            t1 = (lo - origin) / direction
            t2 = (hi - origin) / direction
        near = np.minimum(t1, t2)
        far = np.maximum(t1, t2)
        # A ray parallel to a slab has inf/-inf bounds, which the min/max already handle.
        entry = np.max(near, axis=2)
        exit_ = np.min(far, axis=2)
        hit = (exit_ >= np.maximum(entry, 0.0)) & (entry > -1e9)
        closer = hit & (entry < depth)
        if not closer.any():
            continue
        axis = np.argmax(np.where(np.isnan(near), -np.inf, near), axis=2)
        point = origin + direction * np.where(np.isfinite(entry), entry, 0.0)[..., None]
        u = np.zeros((h, w))
        v = np.zeros((h, w))
        face_of_pixel = np.full((h, w), -1, np.int64)
        for index, face in enumerate(FACE_ORDER):
            normal = np.array(FACE_NORMALS[face], np.float64)
            for ax in range(3):
                if abs(normal[ax]) < 0.5:
                    continue
                sign = bool(normal[ax] > 0)
                # The face facing the camera is the one whose slab the ray enters, from the
                # side its normal points to.
                entered_from_positive = (axis == ax) & (near[..., ax] == entry) & \
                                        ((origin[..., ax] > hi[ax]) == sign)
                if not entered_from_positive.any():
                    continue
                mask = closer & entered_from_positive & (face_of_pixel < 0)
                if not mask.any():
                    continue
                face_of_pixel[mask] = index
                fu, fv = _face_uv_from_point(face, point[mask], lo, hi)
                u[mask], v[mask] = fu, fv

        for index, face in enumerate(FACE_ORDER):
            mask = closer & (face_of_pixel == index)
            if not mask.any():
                continue
            data = el["faces"].get(face)
            if data is None:
                continue
            texture = np.asarray(textures[data["texture"].lstrip("#")].convert("RGB"), np.float64)
            if texture.shape[0] > ATLAS_H:      # an animation strip previews its first frame
                texture = texture[:ATLAS_H]
            u0, v0, u1, v1 = uv_pixels(data["uv"])
            tx = u0 + u[mask] * (u1 - u0)
            ty = v0 + v[mask] * (v1 - v0)
            if smooth:
                sampled = _bilinear(texture, tx, ty)
            else:
                # The art is 16 px, so the preview samples it the way the game does: nearest
                # texel. Blurring a 16 px texture to 200 px invents detail that is not there
                # and hides the very dithering the art depends on.
                th, tw = texture.shape[:2]
                sampled = texture[np.clip(np.round(ty), 0, th - 1).astype(np.int64),
                                  np.clip(np.round(tx), 0, tw - 1).astype(np.int64)]
            normal = np.array(FACE_NORMALS[face], np.float64)
            unit_point = point[mask] - centre
            lengths = np.linalg.norm(unit_point, axis=1, keepdims=True)
            unit_point = unit_point / np.maximum(lengths, 1e-9)
            if flat:
                shade = np.ones(u[mask].shape)
            else:
                lambert = np.clip(unit_point @ light, 0.0, 1.0)
                shade = 0.80 + 0.30 * lambert + 0.12 * (_FACE_SHADE[face] - 0.75)
            colour[mask] = np.clip(sampled * shade[:, None], 0.0, 255.0)
            depth[mask] = entry[mask]
            covered[mask] = True

    if background[3] == 0:
        rgba = np.dstack([np.clip(colour, 0, 255), np.where(covered, 255.0, 0.0)]).astype(np.uint8)
    else:
        rgba = np.dstack([np.clip(colour, 0, 255), np.full((h, w), 255.0)]).astype(np.uint8)
    resample = Image.Resampling.LANCZOS if smooth else Image.Resampling.NEAREST
    return Image.fromarray(rgba, "RGBA").resize((size, size), resample)


# ---------------------------------------------------------------------------
# 11. Preview sheets
# ---------------------------------------------------------------------------
def contact_sheet(models: dict, texture_sets: dict, path, columns=4) -> Image.Image:
    """Every skin side by side, so a human can judge them without joining the server."""
    from PIL import ImageFont

    try:
        title = ImageFont.truetype("DejaVuSans-Bold.ttf", 17)
        label = ImageFont.truetype("DejaVuSans-Bold.ttf", 14)
        small = ImageFont.truetype("DejaVuSans.ttf", 11)
        # `describe` mirrors BallSkin.description, the line the player reads in the item
        # tooltip. The copy is kept short enough to fit the fixed 182 px card at this size;
        # shrinking the font instead would hide an overflow rather than remove it.
        FONT_CARD_DESCRIBE = ImageFont.truetype("DejaVuSans.ttf", 11)
    except OSError:  # pragma: no cover - the sandbox image always has DejaVu
        title = label = small = FONT_CARD_DESCRIBE = ImageFont.load_default()

    cell_w, cell_h, pad = 204, 244, 28
    rows = (len(SKIN_ORDER) + columns - 1) // columns
    sheet = Image.new("RGBA", (pad * 2 + columns * cell_w, 150 + rows * cell_h), (11, 15, 23, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((pad, 20), "BOSKETBALL 26.2  \u00b7  BALL SYSTEM", font=title, fill=(248, 250, 252, 255))
    draw.text((pad, 46), f"One cube, eight skins: the same {face_count()}-face silhouette, the same eight panels, "
                         f"{FACE_PX} px of leather per face.",
              font=small, fill=(148, 163, 184, 255))
    draw.text((pad, 64), "Each card: hero at 132 px, the real 32 px inventory size, and an arena pass with "
                         "the house key light off.",
              font=small, fill=(100, 116, 139, 255))

    for idx, skin in enumerate(SKIN_ORDER):
        col, row = idx % columns, idx // columns
        x, y = pad + col * cell_w, 108 + row * cell_h
        model, textures = models[skin], texture_sets[skin]
        accent = _hex(SKINS[skin]["accent"])
        # The card is as wide as the widest thing it has to hold: a 34-character
        # description is 165 px at 10 px DejaVu, and the shorter name/glow line sits on the
        # same row. Sizing the card to a round number instead clipped the copy mid-word,
        # which reads as a broken build, not as a long sentence.
        card = Image.new("RGBA", (cell_w - 14, cell_h - 14), (16, 21, 31, 255))
        cd = ImageDraw.Draw(card)
        cd.rectangle([0, 0, card.width - 1, card.height - 1], outline=accent + (100,))
        cd.rectangle([0, 0, card.width - 1, 3], fill=accent + (235,))
        card.alpha_composite(render_ball(model, textures, size=132, yaw=34, pitch=18), (17, 12))
        card.alpha_composite(render_ball(model, textures, size=32, yaw=34, pitch=18, ss=4), (10, 158))
        card.alpha_composite(
            render_ball(model, textures, size=32, yaw=206, pitch=10, ss=4,
                        background=(6, 8, 13, 255), light=np.array([0.12, 0.22, 0.96])),
            (50, 158))
        card.alpha_composite(render_ball(model, textures, size=32, yaw=34, pitch=18, ss=4, flat=True), (90, 158))
        cd.text((10, 198), SKINS[skin]["label"], font=label, fill=accent + (255,))
        cd.text((10, 218), SKINS[skin]["describe"], font=FONT_CARD_DESCRIBE, fill=(148, 163, 184, 255))
        cd.text((140, 199), f"glow {SKINS[skin]['light_emission']}", font=small, fill=(100, 116, 139, 255))
        sheet.alpha_composite(card, (x + 7, y))

    # Below the last card, not on top of it: the card bottom is at 108 + rows * cell_h - 14.
    footer = 108 + rows * cell_h + 6
    draw.text((pad, footer),
              "Third thumbnail is the same 32 px ball with flat lighting: that is the worst case a player "
              "will ever see it.",
              font=small, fill=(100, 116, 139, 255))
    draw.text((pad, footer + 15),
              "If two cards are hard to tell apart there, that skin is not finished.",
              font=small, fill=(100, 116, 139, 255))
    sheet.convert("RGB").save(path)
    return sheet


_model_cache: dict[str, dict] = {}
_texture_cache: dict[str, dict] = {}


def skin_model(skin_id: str) -> dict:
    """The shipped model for one skin, built once and reused."""
    if skin_id not in _model_cache:
        _model_cache[skin_id] = build_model(skin_id)
    return _model_cache[skin_id]


def skin_textures(skin_id: str) -> dict:
    """The shipped textures for one skin, rendered once and reused."""
    if skin_id not in _texture_cache:
        _texture_cache[skin_id] = render_textures(skin_id)
    return _texture_cache[skin_id]


def render_skin_card(skin_id: str, size: int = 160) -> Image.Image:
    """One skin at hero size — what the banner, the hover cards and the docs use."""
    return render_ball(skin_model(skin_id), skin_textures(skin_id), size=size, yaw=34, pitch=18, ss=2)


def write_pack_files(model_dir, texture_dir) -> dict:
    """Writes every ball model, texture and `.mcmeta` the pack ships.

    Returns the file names written, so the build can assert that what the code asks for is
    what landed on disk — the invariant that keeps a renamed skin from shipping as a
    magenta-and-black placeholder.
    """
    import json
    from pathlib import Path

    model_dir = Path(model_dir)
    texture_dir = Path(texture_dir)
    written = {"models": [], "textures": [], "meta": []}
    for skin_id in SKIN_ORDER:
        model_path = model_dir / f"basketball_{skin_id}.json"
        model_path.write_text(json.dumps(strip_meta(build_model(skin_id)), indent=2), encoding="utf-8")
        written["models"].append(model_path.name)

        for key, image in render_textures(skin_id).items():
            name = texture_filename(skin_id, key)
            image.save(texture_dir / name, optimize=True)
            written["textures"].append(name)
            meta = animation_meta(skin_id)
            meta_path = texture_dir / f"{name}.mcmeta"
            if meta is not None:
                meta_path.write_text(json.dumps(meta, indent=2), encoding="utf-8")
                written["meta"].append(meta_path.name)
    return written


def spin_strip(model: dict, textures: dict, frames: int = 12, size: int = 72) -> Image.Image:
    """The ball turned on its vertical axis: proof the spin has a visible axis."""
    strip = Image.new("RGBA", (frames * size, size), (13, 18, 27, 255))
    for i in range(frames):
        strip.alpha_composite(
            render_ball(model, textures, size=size, yaw=360.0 * i / frames, pitch=12, ss=2),
            (i * size, 0))
    return strip
