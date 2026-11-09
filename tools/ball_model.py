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

# Texture layout: six 128 px tiles in a 3 x 2 atlas. One file per skin, six faces, no
# second source of truth for where a face's pixels live.
FACE_PX = 128
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
# 5. Texture style — every number a human tuned, in one place
# ---------------------------------------------------------------------------
TEXTURE_COLOURS = 128    # palette size of the shipped PNG; the previews stay true colour
GROOVE_PX = 2.0        # the flat floor of the channel, full seam colour
WALL_PX = 3.9          # the channel's wall: where the groove meets the leather
LIP_PX = 8.2           # the skived panel edge, which catches the light
STITCH_PX = 9.6        # the thread line, just outside the lip
EDGE_AO_PX = 12.0      # how far the rounded cube edge reaches into a face
EDGE_BEVEL_PX = 3.4    # the lit rim of a rounded cube edge
FIRE_FRAMES = 4

# A gentle key light, up and slightly north-west. Baked into the leather as form shading,
# through the pebble grain as bump shading, and through the bevel as a highlight line: the
# client lights the model too, so this is deliberately weak.
LIGHT = np.array([-0.40, 0.82, -0.41])
LIGHT = LIGHT / np.linalg.norm(LIGHT)
# The same light in a face's own 2D frame — u right, v down — as the direction the light
# *travels*. A wall whose normal points back at the light is the lit one.
LIGHT_2D = np.array([0.72, 0.69])

# ---- the eight-panel network, in angles on the ball the cube is standing in for --------
# A real basketball is two oval end panels and six side panels, which is what these two
# families of seams cut: two circles of latitude for the ends, and six meridians between
# them, bent so the side panels are the kidney shape a ball actually has. Working in
# direction space rather than in a face's pixels is what keeps the network continuous
# across all twelve cube edges — the same texel direction from either face is the same
# angle, so it cannot disagree with itself.
CAP_LAT = math.radians(38.0)     # latitude of the end panels' edge
SIDE_PANELS = 6
MERIDIAN_BEND = math.radians(7.5)  # how far the side seams wander off a straight meridian
RAD_PER_PX = (math.pi * 0.5) / FACE_PX

# Where the sheen sits. A ball is looked at from the front and a little above, so a soft
# highlight there reads as polish; a highlight baked dead-centre reads as a spotlight
# painted on the ball, which is why it is off-axis and weak.
SHEEN_VIEW = np.array([0.16, 0.40, 0.90])
SHEEN_VIEW = SHEEN_VIEW / np.linalg.norm(SHEEN_VIEW)


def _skin_seed(skin_id: str, salt: int) -> int:
    """A stable per-skin seed: two skins must not share a grain, and a rebuild must match."""
    total = salt * 7919
    for i, ch in enumerate(skin_id):
        total = (total * 131 + ord(ch) * (i + 3)) & 0xFFFFFF
    return total % 100003


# ---------------------------------------------------------------------------
# 6. Noise — value noise in the ball's own space
# ---------------------------------------------------------------------------
def _hash_u32(a: np.ndarray, b: np.ndarray, c: np.ndarray, seed: int) -> np.ndarray:
    h = (a * 374761393 + b * 668265263 + c * 1274126177 + seed * 2246822519) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF).astype(np.float64) / 65535.0


def value_noise(dirs: np.ndarray, frequency: float, seed: int = 0) -> np.ndarray:
    """Trilinear value noise over a field of directions, in 0..1.

    Sampled at the texel's *direction* rather than at its place on a face, which is what
    makes the leather one continuous hide across the twelve cube edges: two texels that
    meet at an edge are the same direction, so they are the same grain.
    """
    q = dirs * frequency
    base = np.floor(q).astype(np.int64)
    f = q - base
    f = f * f * (3.0 - 2.0 * f)
    x, y, z = base[..., 0], base[..., 1], base[..., 2]
    fx, fy, fz = f[..., 0], f[..., 1], f[..., 2]
    corners = {}
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                corners[(dx, dy, dz)] = _hash_u32(x + dx, y + dy, z + dz, seed)
    x00 = corners[(0, 0, 0)] + (corners[(1, 0, 0)] - corners[(0, 0, 0)]) * fx
    x10 = corners[(0, 1, 0)] + (corners[(1, 1, 0)] - corners[(0, 1, 0)]) * fx
    x01 = corners[(0, 0, 1)] + (corners[(1, 0, 1)] - corners[(0, 0, 1)]) * fx
    x11 = corners[(0, 1, 1)] + (corners[(1, 1, 1)] - corners[(0, 1, 1)]) * fx
    y0 = x00 + (x10 - x00) * fy
    y1 = x01 + (x11 - x01) * fy
    return y0 + (y1 - y0) * fz


def worley(dirs: np.ndarray, frequency: float, seed: int = 0) -> np.ndarray:
    """Distance to the nearest of one jittered point per lattice cell, in cell units.

    This is the shape of a pebbled hide and value noise is not: the nearest-point field of
    a jittered lattice makes domes of roughly one size that sit next to each other, which
    is what tanning does. Folded value noise gives worm tracks, however it is warped.
    """
    q = dirs * frequency
    base = np.floor(q).astype(np.int64)
    f = q - base
    best = np.full(f.shape[:2], np.inf)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = base[..., 0] + dx, base[..., 1] + dy, base[..., 2] + dz
                vx = dx + _hash_u32(cx, cy, cz, seed) - f[..., 0]
                vy = dy + _hash_u32(cx, cy, cz, seed + 1) - f[..., 1]
                vz = dz + _hash_u32(cx, cy, cz, seed + 2) - f[..., 2]
                best = np.minimum(best, vx * vx + vy * vy + vz * vz)
    return np.sqrt(best)


def _smoothstep(edge0: float, edge1: float, x: np.ndarray) -> np.ndarray:
    if edge1 == edge0:
        return (x >= edge1).astype(np.float64)
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def _ramp(dark, base, bright, t: np.ndarray) -> np.ndarray:
    """The leather ramp: shadow to base to highlight, three stops, no banding."""
    dark = np.array(dark, np.float64)
    base = np.array(base, np.float64)
    bright = np.array(bright, np.float64)
    t = np.clip(t, 0.0, 1.0)[..., None]
    lo = dark + (base - dark) * np.clip(t / 0.645, 0.0, 1.0)
    hi = base + (bright - base) * np.clip((t - 0.645) / 0.355, 0.0, 1.0)
    return np.where(t < 0.645, lo, hi)


# ---------------------------------------------------------------------------
# 7. One face tile
# ---------------------------------------------------------------------------
def _face_geometry(face: str):
    """Texel grid, world direction and normalised direction for one face."""
    n = FACE_PX
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
    """The eight-panel network, as a distance field over the ball's directions.

    Returns the angular distance to the nearest seam (radians), which family that seam
    came from, and how far along that seam the texel sits — everything the painter needs
    to give a channel a floor, two walls and a thread line.
    """
    lat = np.arcsin(np.clip(unit[..., 1], -1.0, 1.0))
    lon = np.arctan2(unit[..., 2], unit[..., 0])

    # Two end panels: the circles of latitude that bound them.
    d_cap = np.minimum(np.abs(lat - CAP_LAT), np.abs(lat + CAP_LAT))

    # Six side panels: meridians between the two circles, bent by a sine so they curve.
    bend = MERIDIAN_BEND * np.sin(np.pi * lat / CAP_LAT)
    step = 2.0 * math.pi / SIDE_PANELS
    offsets = np.stack([np.abs((lon - index * step - bend + math.pi) % (2.0 * math.pi) - math.pi)
                        for index in range(SIDE_PANELS)])
    d_meridian = np.min(offsets, axis=0) * np.abs(np.cos(lat))
    inside_band = np.abs(lat) < CAP_LAT
    d_meridian = np.where(inside_band, d_meridian, np.inf)

    use_cap = d_cap <= d_meridian
    dist = np.where(use_cap, d_cap, d_meridian)
    along = np.where(use_cap, lon, lat)
    dist = np.where(np.isfinite(dist), dist, 1.0e3)
    return {"dist": dist, "cap": use_cap, "along": along}


def render_face_tile(skin_id: str, face: str, frame: float = 0.0) -> Image.Image:
    """One face of one skin: leather, eight-panel network, groove, lip, stitching, sheen."""
    spec = SKINS[skin_id]
    n = FACE_PX
    u_grid, v_grid, dirs, unit = _face_geometry(face)
    seed = _skin_seed(skin_id, 1)
    grain = spec["pebble"]

    # ---- leather: rounded pebbles, not a cloudy noise field ----------------------------
    # A raw value noise reads as fog. `1 - |2n - 1|` folds it into ridges, and a ridge
    # field shaded as a height map is what a pebbled hide actually looks like: bright on
    # the side of each bump that faces the light, dark on the other.
    # The pebbles are the nearest-point field of a jittered lattice: domes of one size that
    # sit against each other, which is what tanned leather looks like. A second, finer
    # lattice at a quarter of the strength is the grain inside each pebble.
    near = worley(unit, 12.5, seed)
    grain_field = worley(unit, 29.0, seed + 3)
    pebbles = np.clip(1.0 - near / 0.95, 0.0, 1.0) ** 0.8
    micro = np.clip(1.0 - grain_field / 0.75, 0.0, 1.0) ** 0.9
    dust = value_noise(unit, 120.0, seed + 2) - 0.5
    height = 0.78 * pebbles + 0.22 * micro
    gy, gx = np.gradient(height)
    bump = -(gx * LIGHT_2D[0] + gy * LIGHT_2D[1]) * grain * 2.6
    wear = value_noise(unit, 3.0, seed + 4) - 0.5

    # ---- panels: the eight octants, so the tone never steps at a cube edge --------------
    sign_bits = ((dirs[..., 0] >= 0.0).astype(np.int64)
                 + 2 * (dirs[..., 1] >= 0.0).astype(np.int64)
                 + 4 * (dirs[..., 2] >= 0.0).astype(np.int64))
    panel_tone = np.array([(_hash_u32(np.int64(k), np.int64(7), np.int64(3), seed) - 0.5) * 0.06
                           for k in range(8)])
    tone_map = panel_tone[sign_bits]

    # ---- form: the sphere-ish light term, continuous across every edge -----------------
    lambert = 0.5 + 0.5 * (unit @ LIGHT)
    value = (0.14 + 0.68 * lambert + tone_map + 0.11 * wear * grain
             + 0.26 * (height - 0.55) * grain + 0.45 * bump + 0.05 * dust * grain)

    # ---- the rounded cube edge: a lit rim, a short shadow, and a little occlusion ------
    edge_left, edge_right = u_grid * n, (1.0 - u_grid) * n
    edge_top, edge_bottom = v_grid * n, (1.0 - v_grid) * n
    edge_distance = np.minimum(np.minimum(edge_left, edge_right),
                               np.minimum(edge_top, edge_bottom))
    frame_dirs = FACE_FRAME[face]
    normal = np.array(frame_dirs["normal"], np.float64)
    right = np.array(frame_dirs["right"], np.float64)
    up = np.array(frame_dirs["up"], np.float64)
    nearest = np.argmin(np.stack([edge_left, edge_right, edge_top, edge_bottom]), axis=0)
    outward = np.empty(dirs.shape, np.float64)
    for k, vector in enumerate((-right, right, -up, up)):
        outward[nearest == k] = vector
    bevel_normal = normal[None, None, :] + outward
    bevel_normal /= np.linalg.norm(bevel_normal, axis=2, keepdims=True)
    bevel_lit = np.clip(bevel_normal @ LIGHT, 0.0, 1.0)

    occlusion = np.clip(1.0 - edge_distance / EDGE_AO_PX, 0.0, 1.0) ** 1.6
    value = value * (1.0 - 0.16 * occlusion)
    rim = np.clip(1.0 - edge_distance / EDGE_BEVEL_PX, 0.0, 1.0) ** 1.3
    value = value + 0.30 * rim * bevel_lit - 0.16 * rim * (1.0 - bevel_lit)

    # ---- the channel network ----------------------------------------------------------
    field = seam_field(unit)
    dist = field["dist"] / RAD_PER_PX          # in pixels, for the profile widths
    # The wall of a groove faces away from the seam; it is lit when it faces the light.
    gv, gu = np.gradient(field["dist"])
    norm = np.hypot(gu, gv)
    lit = np.where(norm > 1e-12, -(gu * LIGHT_2D[0] + gv * LIGHT_2D[1]) / np.maximum(norm, 1e-12), 0.0)

    groove = 1.0 - _smoothstep(GROOVE_PX, WALL_PX + 0.8, dist)
    lip = _smoothstep(WALL_PX, WALL_PX + 1.8, dist) * (1.0 - _smoothstep(LIP_PX - 1.8, LIP_PX, dist))
    value = value * (1.0 + 0.28 * lip * np.clip(lit, 0.0, 1.0)) \
                  * (1.0 - 0.22 * lip * np.clip(-lit, 0.0, 1.0))
    wall_rim = np.exp(-((dist - (WALL_PX + 0.45)) / 0.9) ** 2) * np.clip(lit, 0.0, 1.0)
    value = value + 0.14 * wall_rim

    # ---- colour -----------------------------------------------------------------------
    if spec["split_axis"] is None:
        colour = _ramp(spec["panel_dark"], spec["panel_base"], spec["panel_bright"], value)
    else:
        axis = spec["split_axis"]
        accent = np.where(unit[..., axis] > 0.0, 1.0, 0.0)[..., None]
        plain = _ramp(spec["panel_dark"], spec["panel_base"], spec["panel_bright"], value)
        second = _ramp(spec["accent_dark"], spec["accent_base"], spec["accent_bright"], value)
        colour = plain * (1.0 - accent) + second * accent

    # ---- sheen ------------------------------------------------------------------------
    half = LIGHT + SHEEN_VIEW
    half = half / np.linalg.norm(half)
    facing = np.clip(unit @ half, 0.0, 1.0)
    gloss = spec["sheen"]
    sheen = (facing ** 30) * 0.42 * gloss + (facing ** 7) * 0.085 * gloss
    colour = colour + sheen[..., None] * np.array([255.0, 250.0, 240.0])
    # Pebble tops catch a point of light: the cheapest way to tell leather from a gradient.
    sparkle = np.clip((height - 0.86) * 7.0, 0.0, 1.0) * 0.34 * gloss
    colour = colour + sparkle[..., None] * np.array([255.0, 244.0, 226.0])

    # ---- the channel itself -----------------------------------------------------------
    seam_dark = np.array(spec["seam_dark"], np.float64)
    seam_base = np.array(spec["seam_base"], np.float64)
    seam_bright = np.array(spec["seam_bright"], np.float64)
    depth = np.clip(dist / GROOVE_PX, 0.0, 1.0)
    seam_colour = seam_dark + (seam_base - seam_dark) * (depth ** 1.5)[..., None]
    if spec["animated"]:
        # Fire: the molten seam rolls along its own length, one cycle per strip.
        pulse = 0.5 + 0.5 * np.sin(2.0 * math.pi * (field["along"] * (4.0 / math.pi) - frame))
        seam_colour = seam_colour + (seam_bright - seam_colour) * (pulse * 0.7)[..., None]
    colour = colour * (1.0 - groove[..., None]) + seam_colour * groove[..., None]
    colour = colour + (wall_rim * 0.85)[..., None] * (seam_bright * 0.75 + 30.0)

    # ---- stitching: one thread line each side of the channel --------------------------
    if spec.get("stitch", 1.0) > 0.0:
        along_px = field["along"] / RAD_PER_PX
        dash = (np.floor(along_px + 0.5) % 6) < 3
        thread = (np.abs(dist - STITCH_PX) < 0.75) & dash & (dist < STITCH_PX + 1.0)
        thread_colour = np.where(lit[..., None] > 0.0,
                                 seam_bright * 0.62 + seam_base * 0.38,
                                 seam_dark * 0.6 + seam_base * 0.4)
        colour = np.where(thread[..., None], colour * 0.62 + thread_colour * 0.38, colour)

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
                light=None, zoom: float = 0.96, flat: bool = False) -> Image.Image:
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
    origin = (centre[None, None, :]
              + right[None, None, :] * sx[..., None]
              + up[None, None, :] * sy[..., None])
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
                sign = 1 if normal[ax] > 0 else 0
                # The slab that was entered is the one on the side the normal points to.
                entered_from_positive = (axis == ax) & (near[..., ax] == entry) & \
                                        ((origin[..., ax] > hi[ax]) == bool(sign))
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
            sampled = _bilinear(texture, tx, ty)
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
    return Image.fromarray(rgba, "RGBA").resize((size, size), Image.Resampling.LANCZOS)


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
            # The full-colour atlas is 150 KB and the grain survives 128 colours untouched,
            # so the shipped copy is a palette PNG — a third of the size for no visible loss.
            image.quantize(colors=TEXTURE_COLOURS, method=Image.MEDIANCUT,
                           dither=Image.NONE).save(texture_dir / name, optimize=True)
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
