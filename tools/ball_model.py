#!/usr/bin/env python3
"""
BosketBall 26.2 — the ball model system.

One silhouette, eight skins. Everything a basketball is on screen is decided here: the
sphere, the channel network, the leather, the texture atlas, the `display` transforms,
and the renderer that lets a human judge the result without joining the server.

Why it exists
-------------
The shipped ball was a cube: 25 boxes, six independent 128 px faces, a cross painted on
each one. It read as a spinning die, the seams died at every cube edge, and the pivot of
every spin sat off-centre.

This module replaces it with an icosphere whose channels are computed from the surface
position instead of being drawn by hand. Three great circles — the xy, yz and zx planes —
are the network a real basketball's panels are cut along, so a texel belongs to a channel
exactly when ``min(|x|, |y|, |z|)`` is small for that texel's own direction on the
sphere. The rule is one line, it is the same line for all eight skins, and it is checked
by a unit test, which is what makes "no seam drift between skins" a property rather than
a promise.

The colour of a face is decided the same way: one analytic function of the direction its
centroid looks at, quantised into a 16x16 palette atlas. That is the only approach that
can be exact here. Minecraft gives every box face a single affine UV rectangle, and an
arbitrarily oriented face cannot be projected onto a texture without shearing it — every
attempt to map a seam pattern through that rectangle smeared, and no amount of
subdivision fixed it. A palette entry is one texel, so interpolation has nothing to
smear: the seam is where the geometry says it is, and the texels cannot disagree.

Subdivision buys the seam its width. At 320 faces a face is about 1.4 px on a 32 px
inventory icon, so a channel drawn two faces wide reads as a clean line instead of a
staircase, and every pixel of it is one palette entry with no filter touching it.

Units
-----
Model space is Minecraft's: 16 units per block, elements centred on (8, 8, 8). The physics
ball in `BasketballEntity` draws a unit-radius model at `VISUAL_BALL_SCALE`, so the
silhouette is authored at ``SPHERE_RADIUS = SPHERE_RADIUS_BLOCKS / VISUAL_BALL_SCALE *
16`` to land on the radius the simulation actually collides at (0.598 blocks: what a
size-7 ball measures against a 9.2-block rim). The old cube came out at 55% of its own
display scale, which is why it looked toy-sized next to the hoop.
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

# Palette layout: rows 0-1 cover ramp (32 entries), row 2 channels (16), rows 3-4 the
# second colourway (32). One texture, one draw call per face, and a two-tone ball costs
# exactly nothing extra at render time.
PALETTE_COLS, PALETTE_ROWS = 16, 16
RAMP_STEPS = 32                # leather: two rows, so the form shading has no banding
CHANNEL_STEPS_RAMP = 16        # channels: one row, deliberately shorter and darker
ACCENT_ROW = 3                 # first row of the second colourway

# ---------------------------------------------------------------------------
# 2. Silhouette constants
# ---------------------------------------------------------------------------
SUBDIV = 2                    # 0 = 20 faces, 1 = 80, 2 = 320. The inventory icon is the
                              # size that settles this: at 80 faces a 32 px ball is visibly
                              # octagonal; at 320 the silhouette is round. Costs about 130 KB
                              # of JSON per skin, which a resource pack can carry.
VISUAL_BALL_SCALE = 0.52      # BasketballEntity.VISUAL_BALL_SCALE
SPHERE_RADIUS_BLOCKS = 0.285  # == PhysicsMath.BALL_RADIUS: the ball now looks like what it
                              # collides with. The old cube was authored at 0.55 units and
                              # its corners reached 0.95, so the thing on screen was a third
                              # bigger than the thing the physics moved.
SPHERE_RADIUS = SPHERE_RADIUS_BLOCKS / VISUAL_BALL_SCALE * 16.0   # 8.77 model units
CENTER = 8.0
# Detail sizes are fractions of the ball, not absolute model units: the silhouette has to
# look the same when the ball is resized against the rim, and absolute numbers is exactly
# how the first attempt at a smaller ball grew a channel as thick as its own panels.
OVERLAP_RATIO = 0.010         # every box grows by this fraction of the radius
CULL_DOT = -0.12              # drop faces pointing back into the ball
BEVEL_REACH = 0.085           # how far from a channel plane the darkening wall reaches
FIRE_FRAMES = 8

# A gentle key light, top-left and slightly in front, baked in as form shading. Kept
# deliberately weak: the client lights the model itself, and a strong baked highlight
# turns into a spotlight painted on the ball the moment it spins.
KEY = np.array([-0.42, 0.72, 0.55])
KEY = KEY / np.linalg.norm(KEY)


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
# 4. Sphere
# ---------------------------------------------------------------------------
_ico_cache: dict[int, tuple[list, list]] = {}


def icosphere(subdiv: int = SUBDIV):
    """Vertices (unit) and triangular faces of a subdivided icosahedron, cached."""
    if subdiv in _ico_cache:
        return _ico_cache[subdiv]

    p = (1.0 + math.sqrt(5.0)) / 2.0
    verts = [(-1, p, 0), (1, p, 0), (-1, -p, 0), (1, -p, 0),
             (0, -1, p), (0, 1, p), (0, -1, -p), (0, 1, -p),
             (p, 0, -1), (p, 0, 1), (-p, 0, -1), (-p, 0, 1)]
    verts = [tuple(c / math.sqrt(sum(v * v for v in vv)) for c in vv) for vv in verts]
    faces = [(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11),
             (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6), (7, 1, 8),
             (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9),
             (4, 9, 5), (2, 4, 11), (6, 2, 10), (8, 6, 7), (9, 8, 1)]

    def midpoint(a, b):
        m = tuple((verts[a][i] + verts[b][i]) * 0.5 for i in range(3))
        ln = math.sqrt(sum(c * c for c in m))
        m = tuple(c / ln for c in m)
        for idx, existing in enumerate(verts):
            if abs(existing[0] - m[0]) < 1e-9 and abs(existing[1] - m[1]) < 1e-9 and abs(existing[2] - m[2]) < 1e-9:
                return idx
        verts.append(m)
        return len(verts) - 1

    for _ in range(subdiv):
        cache: dict[tuple[int, int], int] = {}
        subdivided = []
        for a, b, c in faces:
            ab = cache.setdefault((min(a, b), max(a, b)), midpoint(a, b))
            bc = cache.setdefault((min(b, c), max(b, c)), midpoint(b, c))
            ca = cache.setdefault((min(c, a), max(c, a)), midpoint(c, a))
            subdivided.extend([(a, ab, ca), (b, bc, ab), (c, ca, bc), (ab, bc, ca)])
        faces = subdivided

    _ico_cache[subdiv] = (verts, faces)
    return verts, faces


def face_count(subdiv: int = SUBDIV) -> int:
    return len(icosphere(subdiv)[1])


def palette_size() -> tuple[int, int]:
    return (PALETTE_COLS, PALETTE_ROWS)


def palette_slot(step: int, channel: bool, accent: bool = False) -> int:
    """Palette entry for one face: 0..31 cover, 32..47 channel, 48..79 accent."""
    if channel:
        return RAMP_STEPS + max(0, min(CHANNEL_STEPS_RAMP - 1, step * CHANNEL_STEPS_RAMP // RAMP_STEPS))
    step = max(0, min(RAMP_STEPS - 1, step))
    if accent:
        return ACCENT_ROW * PALETTE_COLS + step
    return step


def slot_uv(slot: int):
    """The UV rectangle of one palette entry, in the model's 0..16 space.

    A whole texel, never a fraction of one: the client interpolates across the rectangle,
    and this way every pixel of it is the same entry.
    """
    col = slot % PALETTE_COLS
    row = slot // PALETTE_COLS
    return [float(col), float(row), float(col + 1), float(row + 1)]


def ramp_colours(spec: dict, channel: bool, accent: bool = False):
    """A ramp from shadow to highlight: 32 rungs of leather, 16 of channel."""
    if channel:
        dark, base, bright = spec["seam_dark"], spec["seam_base"], spec["seam_bright"]
        count = CHANNEL_STEPS_RAMP
    elif accent:
        dark, base, bright = spec["accent_dark"], spec["accent_base"], spec["accent_bright"]
        count = RAMP_STEPS
    else:
        dark, base, bright = spec["panel_dark"], spec["panel_base"], spec["panel_bright"]
        count = RAMP_STEPS
    out = []
    for step in range(count):
        t = step / (count - 1)
        out.append(_mix(dark, base, t * 1.55) if t < 0.645 else _mix(base, bright, (t - 0.645) / 0.355))
    return out


def face_step(direction, spec: dict, jitter: float) -> int:
    """Which rung of the ramp one face sits on.

    Three things add up: the key light (so the ball has form even with lighting off), the
    pebble grain (per-face noise, which at this density reads as micro-texture) and a
    little extra pop for the skins that are meant to shine.
    """
    lambert = float(np.dot(direction, KEY)) * 0.5 + 0.5
    value = 0.56 + lambert * 0.40 * (0.72 + spec["sheen"] * 0.45) + jitter
    return int(max(0.0, min(1.0, value)) * (RAMP_STEPS - 1) + 0.5)


PLANE_EPS = 1e-9


def channel_face(verts, tri) -> bool:
    """True when a face straddles one of the three channel planes.

    This is the whole seam network, and it is a membership test rather than a width. A
    triangle crosses the xy, yz or zx plane exactly when it has a corner on each side of
    it; a triangle lying *in* the plane is a channel too. Corners sitting exactly on the
    plane — the icosahedron has twelve of them, which are the six rib caps and six panel
    centres — are neither side, and treating them as "negative" is what once turned 39%
    of the ball into channel.

    Because the rule is topological, it follows the surface wherever the sphere is turned,
    holds identically for all eight skins, and cannot drift: there is no width to tune and
    nothing to keep in sync with the texture.
    """
    for axis in range(3):
        sides = set()
        for i in tri:
            k = verts[i][axis]
            sides.add(0 if abs(k) <= PLANE_EPS else (1 if k > 0.0 else -1))
        if 1 in sides and -1 in sides:
            return True
        if sides == {0}:
            return True
    return False


def plane_distance(verts, tri) -> float:
    """How close a face sits to its nearest channel plane, in |axis| units."""
    return min(abs(sum(verts[i][axis] for i in tri) / 3.0) for axis in range(3))


# ---------------------------------------------------------------------------
# 5. Model geometry
# ---------------------------------------------------------------------------
FACE_NORMALS = {
    "north": (0.0, 0.0, -1.0),
    "south": (0.0, 0.0, 1.0),
    "west": (-1.0, 0.0, 0.0),
    "east": (1.0, 0.0, 0.0),
    "up": (0.0, 1.0, 0.0),
    "down": (0.0, -1.0, 0.0),
}


def _uv_window(slot: int):
    """The palette rectangle for one face — the same on all six sides of a box.

    A box shows one flat colour from every angle, which is exactly right: the colour was
    chosen from the direction that box looks at, and it must not change when the player
    walks around it.
    """
    return slot_uv(slot)


def _element(lo, hi, *, name, slot, light_emission=0, detail=0, kind="leather"):
    """One model element from a world-space AABB, snapped to the client's 1/16 grid.

    Snapping to 1/16 — the grid the client itself rounds element bounds to — is what keeps
    neighbouring boxes exactly coincident instead of a fraction of a sub-pixel apart;
    those slivers used to survive the client's rounding and show up as dark speckle.

    Faces pointing back into the ball are dropped, so a 320-face sphere still ships a
    small model without changing what the ball looks like.
    """
    snapped_from = [round(c * 16.0) / 16.0 for c in lo]
    snapped_to = [round(c * 16.0) / 16.0 for c in hi]
    for i in range(3):
        if snapped_to[i] - snapped_from[i] < 1.0 / 16.0:
            snapped_to[i] = snapped_from[i] + 1.0 / 16.0
    mid = [(snapped_from[i] + snapped_to[i]) * 0.5 - CENTER for i in range(3)]
    ln = math.sqrt(sum(c * c for c in mid)) or 1.0
    outward = [c / ln for c in mid]
    faces = {}
    for face, normal in FACE_NORMALS.items():
        if sum(normal[i] * outward[i] for i in range(3)) <= CULL_DOT:
            continue
        faces[face] = {"uv": _uv_window(slot), "texture": "#0"}
    element = {"name": name, "from": snapped_from, "to": snapped_to, "faces": faces}
    if light_emission > 0:
        element["light_emission"] = int(light_emission)
        element["shade"] = False
    # Preview bookkeeping; `strip_meta` removes it before the model ships.
    element["_kind"] = kind
    element["_detail"] = int(detail)
    return element


CHANNEL_STEPS = 96            # boxes per great circle: 3.75 degrees of arc each
CHANNEL_LIFT_RATIO = 0.050   # clears the leather, whose own boxes stand OVERLAP proud
CHANNEL_WIDTH_RATIO = 0.062  # rib width: about 3 mm of a real size-7 ball
CHANNEL_THICK_RATIO = 0.045

OVERLAP = OVERLAP_RATIO * SPHERE_RADIUS
CHANNEL_LIFT = CHANNEL_LIFT_RATIO * SPHERE_RADIUS
CHANNEL_WIDTH = CHANNEL_WIDTH_RATIO * SPHERE_RADIUS
CHANNEL_THICK = CHANNEL_THICK_RATIO * SPHERE_RADIUS


def channel_rib_boxes():
    """The channels, as chains of small boxes along the three great circles.

    A rib rather than a painted line, for a reason that shows the moment the ball is
    small: a groove cut into a box sphere closes up as the boxes interpenetrate, whereas
    a rib that stands CHANNEL_LIFT proud of the leather is unambiguous at every size. It
    is also what a real ball does — the panels are skived down to the rib, so the rib is
    the raised element relative to the channel floor.
    """
    boxes = []
    axes = ((0, 1), (1, 2), (2, 0))
    for circle, (i, j) in enumerate(axes):
        radius = SPHERE_RADIUS + CHANNEL_LIFT
        span = 2.0 * math.pi / CHANNEL_STEPS
        for step in range(CHANNEL_STEPS):
            t = (step + 0.5) * span
            centre = [0.0, 0.0, 0.0]
            centre[i] = math.cos(t)
            centre[j] = math.sin(t)
            tangent = [0.0, 0.0, 0.0]
            tangent[i] = -math.sin(t)
            tangent[j] = math.cos(t)
            normal = centre
            side = [normal[1] * tangent[2] - normal[2] * tangent[1],
                    normal[2] * tangent[0] - normal[0] * tangent[2],
                    normal[0] * tangent[1] - normal[1] * tangent[0]]
            half_len = math.sin(span * 0.5) * radius * 1.45
            # Eight corners of the oriented box, then the axis-aligned hull of them.
            corners = []
            for a in (-1.0, 1.0):
                for b in (-1.0, 1.0):
                    for c in (-1.0, 1.0):
                        point = [0.0, 0.0, 0.0]
                        for k in range(3):
                            # + CENTER, because every element in a Minecraft model is
                            # authored in absolute 0..16 space; the circle is generated
                            # around the origin and without this the whole network sits
                            # eight units off the sphere it is meant to lie on.
                            point[k] = (CENTER
                                        + centre[k] * radius
                                        + tangent[k] * half_len * a
                                        + side[k] * CHANNEL_WIDTH * 0.5 * b
                                        + normal[k] * CHANNEL_THICK * 0.5 * c)
                        corners.append(point)
            lo = [min(p[k] for p in corners) for k in range(3)]
            hi = [max(p[k] for p in corners) for k in range(3)]
            boxes.append((circle, step, lo, hi))
    return boxes


def build_elements(spec: dict, subdiv: int = SUBDIV):
    """The silhouette. Identical for every skin, in every byte but the palette slot.

    Two layers:

    * the leather — one box per icosphere face, each grown by OVERLAP so neighbours
      interpenetrate. Overlapping rather than abutting is what keeps the joins from
      z-fighting: two boxes sharing a plane exactly would flicker, while an overlapping
      pair always has one face clearly nearer, so the winner is deterministic and the
      silhouette has no pinpricks;
    * the channels — chains of small ribs along the three great circles that cut a real
      basketball into its eight panels (see CHANNEL_RIB_BOXES' docstring for why rib
      rather than groove).
    """
    verts, faces = icosphere(subdiv)
    elements = []
    for idx, tri in enumerate(faces):
        pts = [tuple(CENTER + c * SPHERE_RADIUS for c in verts[i]) for i in tri]
        lo = [min(p[i] for p in pts) - OVERLAP for i in range(3)]
        hi = [max(p[i] for p in pts) + OVERLAP for i in range(3)]
        direction = [sum(verts[i][k] for i in tri) / 3.0 for k in range(3)]
        ln = math.sqrt(sum(c * c for c in direction)) or 1.0
        direction = [c / ln for c in direction]
        jitter = (_hash_noise(idx, idx * 7 + 1, seed=11) - 0.5) * 0.045 * spec["pebble"]
        step = face_step(direction, spec, jitter)
        accent = spec["split_axis"] is not None and direction[spec["split_axis"]] > 0.0
        elements.append(_element(
            lo, hi, name=f"panel_{idx:03d}", slot=palette_slot(step, channel=False, accent=accent),
            light_emission=spec["light_emission"], detail=0, kind="leather",
        ))

    for circle, step, lo, hi in channel_rib_boxes():
        # The rib keeps a little of the form shading so it bends with the ball, but none
        # of the pebble: a rib is smooth at this scale.
        mid = [(lo[k] + hi[k]) * 0.5 - CENTER for k in range(3)]
        ln = math.sqrt(sum(c * c for c in mid)) or 1.0
        ramp = int(6 + (float(np.dot([c / ln for c in mid], KEY)) * 0.5 + 0.5) * 7)
        elements.append(_element(
            lo, hi, name=f"seam_{circle}_{step:02d}", slot=palette_slot(ramp, channel=True),
            light_emission=spec["light_emission"], detail=1, kind="seam",
        ))
    return elements


def display_transforms() -> dict:
    """Inventory, hand, ground and display-entity presentation.

    `ItemDisplay` entities are drawn through `fixed`, and the physics ball sets its own
    transform to NONE, so `fixed` has to present the ball at final size and upright. The
    hand slots stay at zero scale: the ball is the projectile and the rig draws the
    hands. A test keeps a floating ball from coming back to the first-person hand.
    """
    return {
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]},
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.0, 0.0, 0.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2.4, 0], "scale": [0.4, 0.4, 0.4]},
        "gui": {"rotation": [16, 34, 0], "translation": [0, 1.1, 0], "scale": [0.46, 0.46, 0.46]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 12.0, 0], "scale": [1.0, 1.0, 1.0]},
        # ItemDisplay draws through `fixed`; a 45 degree yaw rests the ball on a seam
        # corner instead of down a pole, so the network reads from the player's default
        # angle rather than the ball looking plain until it happens to spin.
        "fixed": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    }


def build_model(skin_id: str) -> dict:
    spec = SKINS[skin_id]
    return {
        "credit": "BosketBall 26.2 ball system - icosphere with great-circle channels",
        "ambientocclusion": True,
        "gui_light": "front",
        "texture_size": [PALETTE_COLS, PALETTE_ROWS],
        "textures": {"0": f"bosketball:item/ball_{skin_id}", "particle": f"bosketball:item/ball_{skin_id}"},
        "elements": build_elements(spec),
        "display": display_transforms(),
    }


def strip_meta(model: dict) -> dict:
    """Drops preview bookkeeping so the shipped JSON holds only client keys."""
    out = dict(model)
    out["elements"] = [{k: v for k, v in el.items() if not k.startswith("_")} for el in model["elements"]]
    return out


# ---------------------------------------------------------------------------
# 6. Textures — one atlas per skin, one tile per face
# ---------------------------------------------------------------------------
def _hash_noise(a: int, b: int, seed: int = 0) -> float:
    """A cheap deterministic value in 0..1. No RNG state, so packs rebuild byte-identical."""
    h = (a * 374761393 + b * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def _palette_for(spec: dict, direction):
    """Cover colourway, or the second one on whichever hemisphere a two-tone ball splits."""
    if spec["split_axis"] is None:
        return spec["panel_dark"], spec["panel_base"], spec["panel_bright"]
    if float(direction[spec["split_axis"]]) > 0.0:
        return spec["accent_dark"], spec["accent_base"], spec["accent_bright"]
    return spec["panel_dark"], spec["panel_base"], spec["panel_bright"]


def render_atlas(skin_id: str, frame: float = 0.0) -> Image.Image:
    """The palette: two sixteen-step ramps, leather and channel, in one 16x16 texture.

    A two-tone skin uses both halves of the leather ramp for its two colourways and the
    channel ramp for the grooves, so the whole ball still needs exactly one texture and
    exactly one draw call per face.
    """
    spec = SKINS[skin_id]
    img = Image.new("RGBA", palette_size(), (0, 0, 0, 255))
    px = img.load()
    leather = ramp_colours(spec, channel=False)
    channel = ramp_colours(spec, channel=True)
    if spec["animated"]:
        # The Fire ball's channels move: the flame is a bias along the channel ramp that
        # rolls downward each frame, which is why the strip runs vertically.
        leather = ramp_colours(dict(spec, sheen=spec["sheen"]), channel=False)
        channel = []
        for step in range(CHANNEL_STEPS_RAMP):
            t = step / (CHANNEL_STEPS_RAMP - 1)
            pulse = 0.5 + 0.5 * math.sin(2.0 * math.pi * (t - frame))
            channel.append(_mix(spec["seam_dark"], spec["seam_bright"], max(0.0, min(1.0, t * 0.55 + pulse * 0.45))))
    for step in range(RAMP_STEPS):
        px[step % PALETTE_COLS, step // PALETTE_COLS] = _clamp255(leather[step]) + (255,)
    for step in range(CHANNEL_STEPS_RAMP):
        px[step, 2] = _clamp255(channel[step]) + (255,)
    if spec["split_axis"] is not None:
        for step, colour in enumerate(ramp_colours(spec, channel=False, accent=True)):
            px[step % PALETTE_COLS, ACCENT_ROW + step // PALETTE_COLS] = _clamp255(colour) + (255,)
    return img


def render_textures(skin_id: str) -> dict[str, Image.Image]:
    """Every PNG a skin needs, keyed by model texture slot."""
    if not SKINS[skin_id]["animated"]:
        return {"0": render_atlas(skin_id)}
    strip = Image.new("RGBA", (PALETTE_COLS, PALETTE_ROWS * FIRE_FRAMES), (0, 0, 0, 255))
    for f in range(FIRE_FRAMES):
        strip.paste(render_atlas(skin_id, frame=f / FIRE_FRAMES), (0, f * PALETTE_ROWS))
    return {"0": strip}


def texture_filename(skin_id: str, key: str) -> str:
    return f"ball_{skin_id}.png"


def animation_meta(skin_id: str):
    """`.mcmeta` only for a ball that is genuinely animated."""
    if not SKINS[skin_id]["animated"]:
        return None
    return {"animation": {"frametime": 3, "interpolate": True,
                          "width": PALETTE_COLS, "height": PALETTE_ROWS}}


# ---------------------------------------------------------------------------
# 7. Preview renderer
# ---------------------------------------------------------------------------
# A z-buffered orthographic rasteriser over the same elements the client receives. It is
# not Minecraft — but it answers the only two questions that matter here: does the channel
# network read, and do two skins look like one product.
_LIGHT = np.array([-0.42, 0.74, 0.52])
_LIGHT = _LIGHT / np.linalg.norm(_LIGHT)


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


def _camera(yaw_deg: float, pitch_deg: float):
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    forward = np.array([math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    right = np.array([math.cos(yaw), 0.0, -math.sin(yaw)])
    # up = forward x right, not right x forward: the wrong order yields a vector that
    # points at the floor, and because the screen y axis is -dot(p, up) that silently
    # flipped every preview upside down into a bowl of the ball's underside.
    up = np.cross(forward, right)
    return forward, right, up


def _sample_uv(tex: Image.Image, u: np.ndarray, v: np.ndarray):
    """Texel sample for a UV in the model's 0..16 space.

    Floor, not round: a palette entry addressed as [col, row, col + 1, row + 1] samples at
    its centre, ``col + 0.5``, and rounding that lands on the entry below it — which is how
    the leather ramp once ended up reading the channel row.
    """
    # UV space is 0..16 in both axes whatever the texture size, and a stacked animation
    # strip is sampled at its first frame here: a still preview shows one frame.
    w, h = tex.size
    xs = np.mod(np.floor(u * (PALETTE_COLS / 16.0)).astype(np.int64), w)
    ys = np.mod(np.floor(v * (PALETTE_ROWS / 16.0)).astype(np.int64), h)
    return np.asarray(tex.convert("RGB"), dtype=np.float32)[ys, xs]


def render_ball(model: dict, textures: dict, size: int = 128, yaw: float = 34.0,
                pitch: float = 18.0, background=(0, 0, 0, 0), ss: int = 2,
                light=None, zoom: float = 0.96, flat: bool = False) -> Image.Image:
    """Orthographic render of one ball through a z-buffer."""
    forward, right, up = _camera(yaw, pitch)
    light = _LIGHT if light is None else light
    w = h = size * ss
    colour_buf = np.zeros((h, w, 3), dtype=np.float32)
    depth_buf = np.full((h, w), np.inf, dtype=np.float64)
    if background[3] > 0:
        colour_buf[:, :] = background[:3]

    centre = np.array([CENTER, CENTER, CENTER])
    scale = (min(w, h) * 0.5 * zoom) / (SPHERE_RADIUS + OVERLAP * 2)

    for el in model["elements"]:
        mid = (np.array(el["from"]) + np.array(el["to"])) * 0.5 - centre
        el_out = mid / (float(np.linalg.norm(mid)) or 1.0)
        for face, quad in _element_faces(el).items():
            if face not in el["faces"]:
                continue
            normal = np.array(FACE_NORMALS[face], dtype=np.float64)
            # `forward` points from the camera into the scene, so a face is visible to the
            # camera when its normal points back at it: dot < 0. Getting this sign wrong
            # culls every upward face and the render comes out as a bowl.
            if float(np.dot(normal, forward)) >= 0.0:
                continue
            if float(np.dot(normal, el_out)) <= CULL_DOT:
                continue
            pts = [np.array(p, dtype=np.float64) for p in quad]
            centroid = sum(pts) / 4.0
            radial = centroid - centre
            radial = radial / (float(np.linalg.norm(radial)) or 1.0)
            u0, v0, u1, v1 = el["faces"][face]["uv"]
            tex = textures[el["faces"][face]["texture"].lstrip("#")]
            face_bias = {"up": 1.0, "north": 0.90, "south": 0.90, "east": 0.82,
                         "west": 0.82, "down": 0.74}[face]
            lambert = max(0.0, float(np.dot(radial, light)))
            # Weight the surface normal far above the box's own face direction: the ball is
            # a sphere, so two neighbouring boxes should differ by how they *face the light*,
            # not by which side of their cube happens to be showing. Bias-heavy shading is
            # what made a round ball read as a pile of boulders at hero size.
            shade = 1.0 if flat else 0.46 + 0.70 * (0.82 * lambert + 0.18 * face_bias)
            screen = np.array([(float(np.dot(p - centre, right)) * scale + w * 0.5,
                                -float(np.dot(p - centre, up)) * scale + h * 0.5) for p in pts])
            depth = np.array([float(np.dot(p - centre, forward)) for p in pts])
            # Every face of a box reads a single palette entry, so the painter samples
            # that entry once instead of interpolating across the rectangle's corners —
            # which are the edges of three *neighbouring* entries, and were painting every
            # box corner in the wrong colour.
            colour = _sample_uv(tex, np.array([(u0 + u1) * 0.5]), np.array([(v0 + v1) * 0.5]))[0]
            for tri in ((0, 1, 2), (0, 2, 3)):
                _raster_flat(colour_buf, depth_buf, screen[list(tri)], depth[list(tri)],
                             colour * shade, w, h)

    if background[3] == 0:
        alpha = np.where(np.isinf(depth_buf), 0.0, 255.0)
        rgba = np.dstack([np.clip(colour_buf, 0, 255), alpha]).astype(np.uint8)
    else:
        rgba = np.dstack([np.clip(colour_buf, 0, 255), np.full((h, w), 255.0)]).astype(np.uint8)
    return Image.fromarray(rgba, mode="RGBA").resize((size, size), Image.Resampling.LANCZOS)


def _raster_flat(colour_buf, depth_buf, screen, depth, rgb, w, h):
    """Fills one screen triangle at a flat colour, keeping the nearest sample per pixel."""
    x0 = max(0, int(math.floor(min(p[0] for p in screen))))
    x1 = min(w - 1, int(math.ceil(max(p[0] for p in screen))))
    y0 = max(0, int(math.floor(min(p[1] for p in screen))))
    y1 = min(h - 1, int(math.ceil(max(p[1] for p in screen))))
    if x1 < x0 or y1 < y0:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    ax, ay = screen[0]
    bx, by = screen[1]
    cx, cy = screen[2]
    det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(det) < 1e-9:
        return
    l1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / det
    l2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / det
    l3 = 1.0 - l1 - l2
    inside = (l1 >= -0.003) & (l2 >= -0.003) & (l3 >= -0.003)
    if not inside.any():
        return
    z = l1 * depth[0] + l2 * depth[1] + l3 * depth[2]
    window = depth_buf[y0:y1 + 1, x0:x1 + 1]
    better = inside & (z < window - 1e-9)
    if not better.any():
        return
    target = colour_buf[y0:y1 + 1, x0:x1 + 1]
    target[better] = rgb
    window[better] = z[better]


def contact_sheet(models: dict, texture_sets: dict, path, columns=4) -> Image.Image:
    """Every skin side by side, so a human can judge them without joining the server."""
    from PIL import ImageFont

    try:
        title = ImageFont.truetype("DejaVuSans-Bold.ttf", 17)
        label = ImageFont.truetype("DejaVuSans-Bold.ttf", 14)
        small = ImageFont.truetype("DejaVuSans.ttf", 11)
        desc = ImageFont.truetype("DejaVuSans.ttf", 10)
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
    draw.text((pad, 46), f"One silhouette, eight skins: the same {face_count()}-box sphere, the same three channels, "
                         "the same 16 px texel grid.",
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
            image.save(texture_dir / name)
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
