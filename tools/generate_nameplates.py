#!/usr/bin/env python3
"""Rank nameplates built from the owner's `dedou3d_ranking_CustomNameplates.zip`.

The zip is a CustomNameplates content pack. Nothing in it is a NineSlice or a model: every
nameplate is **three bitmap glyphs** drawn in the default font, 16x20 px each, declared as
`height: 20, ascent: 16`:

    left    the left cap (ornament), 3 / 8 / 12 px of visible art depending on the tier
    middle  a 16x20 tile that repeats and lines up seamlessly
    right   the right cap, mirrored

The reference config stacks two text displays: `tag_image_part` (the three parts above) at
y=0.7 and `tag_image_top` (an 18x7 crest) at y=1.25, with the player name drawn on top of the
plate in a gradient (`%np_switch_nameplate_color_prefix%`).

The pack ships **six** materials (wood, bronze, silver, gold, diamond, netherite) in three
finish tiers. BosketBall has **seven** ranks, so this tool:

  1. maps every rank to one of the shipped shapes (tier 1 for the two entry ranks, tier 2 for
     the middle of the ladder, tier 3 - the ornate one - for the three top ranks) and recolours
     its pixels onto the rank's own palette, so the plate above a player's head always matches
     the rank pill in the tab list;
  2. builds the missing seventh plate (`champion`): the ornate shape plus two new details that
     only exist on it - a lit gem in both caps and a double highlight pinstripe along the rim -
     drawn by this tool, never copied from a rank that already has a plate;
  3. re-cuts the crest (`_top`) of every rank into a 18x9 glyph that sits right on top of the
     plate in the same text display, instead of as a floating hologram line.

Recolouring keeps the source pixel art intact: alpha and geometry are never touched, only the
HLS of each opaque pixel is remapped (hue = rank hue, saturation = rank saturation, lightness
stretched over the shape's own range so the bevels and the gem highlights survive).

Outputs (one per rank, 4 glyphs each):
  resourcepack/assets/bosketball/textures/font/np_<rank>_{left,middle,right,crest}.png
  resourcepack/assets/minecraft/font/default.json   (providers upserted, codepoints U+E390+)

    python3 tools/generate_nameplates.py            # write
    python3 tools/generate_nameplates.py --check    # verify only, exit 1 on drift
    python3 tools/generate_nameplates.py --sheet    # also write docs/previews/nameplates.png
"""

from __future__ import annotations

import argparse
import colorsys
import json
import zipfile
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE_ZIP = ROOT / "assets/nameplates/source/dedou3d_ranking_CustomNameplates.zip"
FONT_DIR = ROOT / "resourcepack/assets/bosketball/textures/font"
FONT_JSON = ROOT / "resourcepack/assets/minecraft/font/default.json"

#: First codepoint of the nameplate block. 0xE384..0xE38F stay free, the tab frames end at 0xE383.
FIRST_CODEPOINT = 0xE390
#: left / middle / right / crest, in that order, one block of four per rank.
GLYPH_SLOTS = ("left", "middle", "right", "crest")

PLATE_SIZE = (16, 20)
PLATE_HEIGHT = 20
PLATE_ASCENT = 16
CREST_SIZE = (18, 9)
CREST_ASCENT = 9

#: The 18x7 crest lives in the bottom rows of the 32x32 `_top` images of the source pack.
CREST_CROP = (0, 25, 18, 32)


@dataclass(frozen=True)
class RankPlate:
    """One rank -> one recoloured three-part nameplate."""

    rank: str
    #: Shape to borrow from the pack: material + finish tier (1 = plain caps, 3 = ornate caps).
    source: str
    tier: int
    #: Target colourimetry, taken from `fr.bosketball.model.Rank` (primary colour).
    hue: float
    saturation: float
    lightness: tuple[float, float]
    #: Colour of the nickname inside the plate (contrast against the bar fill).
    text: str
    #: Extra ornamentation that only the generated seventh plate has.
    champion: bool = False


#: Rookie -> MVP. Tiers grow with the ladder, so a Legend plate is visibly fancier than a Rookie
#: one even before the colour is read, exactly like the reference pack's rank_1/2/3 finishes.
RANKS: tuple[RankPlate, ...] = (
    # Rank.primaryColor(): #9CA7BD
    RankPlate("rookie", "silver", 1, 219, 0.16, (0.34, 0.90), "#141A26"),
    # #2ECC71
    RankPlate("hooper", "bronze", 1, 146, 0.52, (0.20, 0.88), "#08210F"),
    # #3498DB
    RankPlate("pro", "silver", 2, 204, 0.58, (0.20, 0.90), "#071C2E"),
    # #F5B928
    RankPlate("all_star", "gold", 2, 42, 0.82, (0.24, 0.94), "#2A1B03"),
    # #A855F7
    RankPlate("superstar", "diamond", 3, 274, 0.58, (0.20, 0.92), "#1B0733"),
    # #FF3E55
    RankPlate("legend", "netherite", 3, 352, 0.66, (0.22, 0.92), "#FFF1F2"),
    # #FF9B1E - the plate the pack does not ship: same ornate shape, new ornaments.
    RankPlate("mvp", "netherite", 3, 30, 0.88, (0.24, 0.97), "#FFF7E4", champion=True),
)

# Staff ranks are not part of the ELO ladder and get no plate of their own: the referee wears
# the Pro plate and the admin the Legend one, decided by ``NameplateSkin.of`` on the plugin side
# (and pinned by the Round 37 test, not duplicated here).

#: Rows of the middle tile that are part of the raised rim: the champion pinstripe goes right
#: under the top rim and right over the bottom rim, on the tile *and* on both caps, so it keeps
#: lining up when the bar is repeated any number of times.
CHAMPION_PINSTRIPE_ROWS = (7, 18)


# ---------------------------------------------------------------------------------------------
# source extraction
# ---------------------------------------------------------------------------------------------
def load_source_parts(material: str, tier: int) -> dict[str, Image.Image]:
    """Reads left/middle/right/crest of one shipped plate straight out of the zip."""
    root = "dedou3d_ranking_CustomNameplates/CustomNameplates/contents"
    parts: dict[str, Image.Image] = {}
    with zipfile.ZipFile(SOURCE_ZIP) as zf:
        for part in ("left", "middle", "right"):
            name = f"{root}/nameplates/{material}_rank_{tier}_{part}.png"
            with zf.open(name) as fh:
                img = Image.open(BytesIO(fh.read())).convert("RGBA")
            if img.size != PLATE_SIZE:
                raise SystemExit(f"{name}: expected {PLATE_SIZE}, got {img.size}")
            parts[part] = img
        # The pack only ships a crest for the rank_3 finish; the shape is identical on every
        # material, so the tier-1 and tier-2 plates borrow it (and recolour it like their own).
        top_name = f"{root}/images/{material}_rank_3_top.png"
        with zf.open(top_name) as fh:
            top = Image.open(BytesIO(fh.read())).convert("RGBA")
    crest = Image.new("RGBA", CREST_SIZE, (0, 0, 0, 0))
    crest.alpha_composite(top.crop(CREST_CROP), (0, CREST_SIZE[1] - CREST_CROP[3] + CREST_CROP[1]))
    parts["crest"] = crest
    return parts


# ---------------------------------------------------------------------------------------------
# recolour
# ---------------------------------------------------------------------------------------------
def recolor(img: Image.Image, plate: RankPlate) -> Image.Image:
    """Remaps every opaque pixel onto the rank's hue/saturation, keeping the pixel art's shading."""
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src = img.load()
    dst = out.load()
    lo, hi = plate.lightness
    hue = (plate.hue % 360.0) / 360.0
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            _, light, _ = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
            # Keep the shape's own build-up of highlights and shadows, only re-span it.
            new_light = lo + light * (hi - lo)
            nr, ng, nb = colorsys.hls_to_rgb(hue, min(1.0, max(0.0, new_light)), plate.saturation)
            dst[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


#: Ramp of the champion plate's core (the part that is neither rim nor gem).
CHAMPION_CORE = (268.0, 0.45, (0.055, 0.20))
#: Above this source lightness the gold plate's pixel is the inner fill, not the rim.
CHAMPION_FILL_LIGHTNESS = 0.70


def compose_champion(parts_gold: dict[str, Image.Image], parts_dark: dict[str, Image.Image]) -> dict[str, Image.Image]:
    """The seventh plate: the ornate shape, gold rim over an obsidian core.

    Both source plates are the same 16x20 geometry, so every pixel exists in both: the light inner
    fill is taken from the netherite plate (a dark, almost black core) and everything else - rails,
    bevels, cap ornaments - keeps the gold plate. The two halves are then recoloured onto the MVP
    ramp (rim) and a deep violet black (core), which is what makes this plate read as a *new* one
    instead of a recolour of an existing rank.
    """
    out: dict[str, Image.Image] = {}
    rim = RankPlate("mvp", "gold", 3, 30, 0.88, (0.24, 0.97), "#FFF7E4")
    core = RankPlate("mvp", "netherite", 3, CHAMPION_CORE[0], CHAMPION_CORE[1], CHAMPION_CORE[2], "#FFF7E4")
    for slot in GLYPH_SLOTS:
        gold = parts_gold[slot]
        dark = parts_dark[slot]
        rim_img = recolor(gold, rim)
        core_img = recolor(dark, core)
        out_img = rim_img.copy()
        gp, dp, op = gold.load(), core_img.load(), out_img.load()
        core_pixels: list[tuple[int, int]] = []
        for y in range(gold.height):
            for x in range(gold.width):
                r, g, b, a = gp[x, y]
                if a == 0:
                    continue
                _, light, _ = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
                if light >= CHAMPION_FILL_LIGHTNESS and dp[x, y][3]:
                    op[x, y] = dp[x, y]      # the *recoloured* obsidian core, not the raw source
                    core_pixels.append((x, y))
        # A flat black core would look hollow: give it the same inner glow the shipped plates get
        # from their fill, as a vertical gradient between the two rails.
        if core_pixels:
            ys = [y for _, y in core_pixels]
            top, bottom = min(ys), max(ys)
            span = max(1, bottom - top)
            for x, y in core_pixels:
                r, g, b, a = op[x, y]
                _, light, _ = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
                glow = 0.085 * (1.0 - abs((y - top) / span - 0.5) * 2.0)
                nr, ng, nb = colorsys.hls_to_rgb(268.0 / 360.0, min(0.32, light + glow), 0.45)
                op[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
        out[slot] = out_img
    return out


def add_champion_details(parts: dict[str, Image.Image]) -> None:
    """The three details that make the seventh plate a real new design, not a recolour."""
    # 1) double pinstripe along the rim (middle + caps, so it keeps lining up when tiled)
    for key in ("left", "middle", "right"):
        img = parts[key]
        px = img.load()
        for row in CHAMPION_PINSTRIPE_ROWS:
            for x in range(img.width):
                r, g, b, a = px[x, row]
                if a == 0:
                    continue
                px[x, row] = _lighten((r, g, b), 0.34) + (a,)
                if row + 1 < img.height:
                    r2, g2, b2, a2 = px[x, row + 1]
                    if a2:
                        px[x, row + 1] = _lighten((r2, g2, b2), 0.16) + (a2,)
    # 2) a lit gem in both caps: 3x3 diamond around the cap centre of mass
    for key in ("left", "right"):
        img = parts[key]
        bbox = img.split()[3].getbbox()
        if not bbox:
            continue
        cx = (bbox[0] + bbox[2] - 1) // 2
        cy = (bbox[1] + bbox[3] - 1) // 2
        px = img.load()
        gem = {
            (0, -1): (255, 246, 210, 255),
            (-1, 0): (255, 226, 150, 255),
            (0, 0): (255, 255, 245, 255),
            (1, 0): (255, 208, 110, 255),
            (0, 1): (196, 132, 40, 255),
        }
        for (dx, dy), color in gem.items():
            gx, gy = cx + dx, cy + dy
            if 0 <= gx < img.width and 0 <= gy < img.height and px[gx, gy][3]:
                px[gx, gy] = color


def _lighten(rgb: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    return tuple(min(255, round(c + (255 - c) * amount)) for c in rgb)


# ---------------------------------------------------------------------------------------------
# font providers
# ---------------------------------------------------------------------------------------------
def provider_entries() -> list[dict]:
    """One bitmap provider per glyph, in rank order, starting at `FIRST_CODEPOINT`."""
    entries = []
    for rank_index, plate in enumerate(RANKS):
        base = FIRST_CODEPOINT + rank_index * len(GLYPH_SLOTS)
        for slot_index, slot in enumerate(GLYPH_SLOTS):
            if slot == "crest":
                height, ascent = CREST_SIZE[1], CREST_ASCENT
            else:
                height, ascent = PLATE_HEIGHT, PLATE_ASCENT
            entries.append(
                {
                    "type": "bitmap",
                    "file": f"bosketball:font/np_{plate.rank}_{slot}.png",
                    "height": height,
                    "ascent": ascent,
                    "chars": [chr(base + slot_index)],
                }
            )
    return entries


def upsert_font_providers(check: bool) -> bool:
    """Adds/replaces the nameplate providers in default.json. Returns True when the file changed."""
    data = json.loads(FONT_JSON.read_text())
    providers = data["providers"]
    wanted = provider_entries()
    wanted_files = {p["file"] for p in wanted}

    kept = [p for p in providers if p.get("file") not in wanted_files]
    # Codepoints must stay unique: drop any stray provider that already owns one of ours.
    ours = {ord(p["chars"][0]) for p in wanted}
    kept = [
        p
        for p in kept
        if not any(ord(c) in ours for row in (p.get("chars") or []) for c in row if c)
    ]
    merged = kept + wanted
    if merged == providers:
        return False
    if check:
        return True
    FONT_JSON.write_text(json.dumps({"providers": merged}, indent=2, ensure_ascii=True))
    return True


# ---------------------------------------------------------------------------------------------
# writing
# ---------------------------------------------------------------------------------------------
def build_recolored() -> dict[str, dict[str, Image.Image]]:
    plates: dict[str, dict[str, Image.Image]] = {}
    for plate in RANKS:
        if plate.champion:
            gold = load_source_parts("gold", plate.tier)
            dark = load_source_parts("netherite", plate.tier)
            parts = compose_champion(gold, dark)
            add_champion_details(parts)
            plates[plate.rank] = parts
            continue
        parts = load_source_parts(plate.source, plate.tier)
        plates[plate.rank] = {slot: recolor(parts[slot], plate) for slot in GLYPH_SLOTS}
    return plates


def write(plates: dict[str, dict[str, Image.Image]]) -> list[Path]:
    FONT_DIR.mkdir(parents=True, exist_ok=True)
    for stale in FONT_DIR.glob("np_*.png"):
        stale.unlink()
    written: list[Path] = []
    for rank, parts in plates.items():
        for slot, img in parts.items():
            path = FONT_DIR / f"np_{rank}_{slot}.png"
            img.save(path)
            written.append(path)
    upsert_font_providers(check=False)
    return written


def inner_fill(plates: dict[str, dict[str, Image.Image]], rank: str) -> tuple[int, int, int]:
    """Average colour of the bar's inner fill, used to sanity-check the nickname contrast."""
    mid = plates[rank]["middle"]
    px = mid.load()
    samples = [px[x, 12] for x in range(mid.width) if px[x, 12][3] > 200]
    if not samples:
        return (0, 0, 0)
    return tuple(round(sum(c[i] for c in samples) / len(samples)) for i in range(3))  # type: ignore[return-value]


def sheet(plates: dict[str, dict[str, Image.Image]]) -> Path:
    """QA sheet: every rank's plate assembled for a sample nickname, plus the crest above it."""
    from PIL import ImageDraw, ImageFont

    zoom = 4
    pad = 14
    label_w = 168
    sample = "MaxLananas"
    text_px = 6 * len(sample) + 2  # rough, the sheet is a visual check only
    tiles = max(3, (text_px + 24) // 16)
    row_h = PLATE_HEIGHT + CREST_SIZE[1] + 10
    width = label_w + 16 * (2 + tiles) * zoom + pad * 2
    height = (row_h * zoom + pad) * len(plates) + pad
    canvas = Image.new("RGBA", (width, height), (18, 20, 26, 255))
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default()
    y = pad
    for rank, parts in plates.items():
        bar = Image.new("RGBA", (16 * (2 + tiles), PLATE_HEIGHT), (0, 0, 0, 0))
        bar.alpha_composite(parts["left"], (0, 0))
        for i in range(tiles):
            bar.alpha_composite(parts["middle"], (16 * (1 + i), 0))
        bar.alpha_composite(parts["right"], (16 * (1 + tiles), 0))
        crest = parts["crest"]
        draw.text((pad // 2, y + zoom * row_h // 2), rank.upper(), fill=(230, 234, 244), font=font)
        x0 = label_w
        canvas.alpha_composite(
            crest.resize((crest.width * zoom, crest.height * zoom), Image.NEAREST),
            (x0 + (bar.width * zoom - crest.width * zoom) // 2, y),
        )
        canvas.alpha_composite(
            bar.resize((bar.width * zoom, bar.height * zoom), Image.NEAREST),
            (x0, y + crest.height * zoom),
        )
        # nickname, drawn like the client will: 8 px tall, 1 px per pixel of the bar
        ty = y + crest.height * zoom + PLATE_ASCENT * zoom - 8 * zoom
        tx = x0 + (bar.width * zoom - len(sample) * 6 * zoom) // 2
        for ch in sample:
            draw.text((tx, ty), ch, fill=(24, 20, 12), font=font)
            tx += 6 * zoom
        y += row_h * zoom + pad
    out = ROOT / "docs/previews/nameplates.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify the pack is up to date")
    parser.add_argument("--sheet", action="store_true", help="write docs/previews/nameplates.png")
    args = parser.parse_args()

    plates = build_recolored()
    if args.check:
        problems = []
        for rank, parts in plates.items():
            for slot, img in parts.items():
                path = FONT_DIR / f"np_{rank}_{slot}.png"
                if not path.is_file():
                    problems.append(f"missing {path.relative_to(ROOT)}")
                    continue
                if Image.open(path).convert("RGBA").tobytes() != img.tobytes():
                    problems.append(f"stale {path.relative_to(ROOT)}")
        if upsert_font_providers(check=True):
            problems.append("default.json is missing nameplate providers")
        for p in problems:
            print("  !", p)
        print("nameplates: OK" if not problems else f"nameplates: {len(problems)} problem(s)")
        return 1 if problems else 0

    written = write(plates)
    for path in written:
        print(f"nameplate  {path.relative_to(ROOT)!s:66s} {Image.open(path).width}x{Image.open(path).height}")
    for rank in plates:
        r, g, b = inner_fill(plates, rank)
        print(f"  {rank:10s} inner fill #{r:02X}{g:02X}{b:02X}")
    if args.sheet:
        print("sheet ->", sheet(plates).relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
