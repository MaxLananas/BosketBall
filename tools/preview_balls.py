#!/usr/bin/env python3
"""
Renders every basketball skin to a PNG contact sheet.

    python3 tools/preview_balls.py            # docs/previews/ball_contact_sheet.png
    python3 tools/preview_balls.py --out x.png --skins classic,glow --rotate

Why a contact sheet exists
--------------------------
The ball is the object a player looks at for a whole match, and it is the one asset nobody
can review in a diff. Judging eight skins means seeing them side by side at the size they
are actually used: a 32 px inventory icon is where "these two look identical" is decided,
and it is where a seam that is too thin, a palette that is too dark, or two skins that
only differ by a hue becomes obvious in a way a 512 px hero render never shows.

The renderer is `ball_model.render_ball`: the same box geometry and the same palette the
client receives, z-buffered, so a skin that renders badly here renders badly in game.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from PIL import Image, ImageDraw  # noqa: E402

import ball_model as bm  # noqa: E402


def icon_sheet(out: Path, skins=None) -> Image.Image:
    """Write the sheet that answers one question: does the new ball belong with the icons?

    The pack is judged as a set, so the only useful comparison is the ball at the sizes it
    is actually drawn at — 16 px in an inventory slot, 32 px underfoot — placed among the
    57 hand-drawn ``icon_*`` sprites at the same 4x magnification as all of them. A ball
    that is prettier alone but louder than every icon beside it has failed.
    """
    from PIL import ImageFont

    item_dir = ROOT / "resourcepack" / "assets" / "bosketball" / "textures" / "item"
    icons = sorted(item_dir.glob("icon_*.png"))
    if not icons:
        raise SystemExit(f"no icons under {item_dir}")

    try:
        title = ImageFont.truetype("DejaVuSans-Bold.ttf", 17)
        caption = ImageFont.truetype("DejaVuSans.ttf", 11)
        label = ImageFont.truetype("DejaVuSans.ttf", 9)
    except OSError:  # pragma: no cover - the build image always has DejaVu
        title = caption = label = ImageFont.load_default()

    background = (13, 18, 27, 255)
    ink = (232, 238, 246, 255)
    dim = (126, 140, 160, 255)

    skin = (skins or ["classic"])[0]
    model, textures = bm.skin_model(skin), bm.skin_textures(skin)
    sizes = [16, 24, 32, 48, 64, 96, 128]
    ball_tiles = [bm.render_ball(model, textures, size=n, yaw=32, pitch=18, ss=2,
                                 background=background) for n in sizes]

    columns = 10
    cell = 74
    icon_scale = 4
    width = columns * cell + 24
    header = 300
    wall_height = ((len(icons) + columns - 1) // columns) * cell
    sheet = Image.new("RGBA", (width, header + wall_height + 16), background)
    draw = ImageDraw.Draw(sheet)

    draw.text((14, 12), "BOSKETBALL 26.2  ·  THE BALL, AT 16 PX, BESIDE THE 57 ICONS", font=title,
              fill=ink)
    draw.text((14, 36), "One cube, one cross per face, six flat tones. Every texel below is a "
                        "texel the client draws.", font=caption, fill=dim)

    # The size strip is bottom-aligned on one baseline: that is how a ball at 16 px and a
    # ball at 128 px can be compared for the thing that matters at both, the read.

    x = 14
    baseline = 196
    for size, tile in zip(sizes, ball_tiles):
        sheet.alpha_composite(tile, (x, baseline - size))
        draw.text((x, baseline + 4), f"{size} px", font=label, fill=dim)
        x += size + 22

    icon = item_dir / "icon_ball_classic.png"
    if icon.exists():
        sprite = Image.open(icon).convert("RGBA")
        sheet.alpha_composite(sprite.resize((16 * icon_scale, 16 * icon_scale), Image.NEAREST),
                              (14, 218))
        draw.text((88, 240), "inventory sprite, 4x", font=label, fill=dim)
        draw.text((88, 254), "drawn from the same six tones,", font=label, fill=dim)
        draw.text((88, 268), "so icon and model cannot drift", font=label, fill=dim)

    for index, path in enumerate(icons):
        sprite = Image.open(path).convert("RGBA")
        cx = 12 + (index % columns) * cell
        cy = header + (index // columns) * cell
        sheet.alpha_composite(sprite.resize((16 * icon_scale, 16 * icon_scale), Image.NEAREST),
                              (cx, cy))
        draw.text((cx, cy + 64), path.stem[len("icon_"):][:12], font=label, fill=dim)

    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)
    return sheet


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Render the BosketBall skin contact sheet.")
    parser.add_argument("--out", default=str(ROOT / "docs" / "previews" / "ball_contact_sheet.png"),
                        help="where to write the contact sheet")
    parser.add_argument("--skins", default="", help="comma-separated subset of skins (default: all)")
    parser.add_argument("--rotate", action="store_true",
                        help="also write a spin strip per skin, which is how the roll is judged")
    parser.add_argument("--columns", type=int, default=4, help="cards per row")
    parser.add_argument("--icons", action="store_true",
                        help="also write docs/previews/ball_icon_sheet.png (ball beside every icon)")
    args = parser.parse_args(argv)

    wanted = [s for s in (args.skins.split(",") if args.skins else bm.SKIN_ORDER) if s]
    unknown = [s for s in wanted if s not in bm.SKINS]
    if unknown:
        print(f"unknown skin(s): {', '.join(unknown)}", file=sys.stderr)
        print(f"known: {', '.join(bm.SKIN_ORDER)}", file=sys.stderr)
        return 2

    models = {s: bm.skin_model(s) for s in wanted}
    textures = {s: bm.skin_textures(s) for s in wanted}

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    bm.contact_sheet(models, textures, out, columns=args.columns)
    print(f"wrote {out} ({out.stat().st_size:,} bytes, {len(wanted)} skins)")

    if args.icons:
        sheet_path = ROOT / "docs" / "previews" / "ball_icon_sheet.png"
        icon_sheet(sheet_path, wanted)
        print(f"wrote {sheet_path} ({sheet_path.stat().st_size:,} bytes)")

    if args.rotate:
        for skin in wanted:
            strip = bm.spin_strip(models[skin], textures[skin])
            strip_path = out.with_name(f"{out.stem}_{skin}_spin.png")
            strip.save(strip_path)
        print(f"wrote {len(wanted)} spin strips next to {out.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
