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

import ball_model as bm  # noqa: E402


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Render the BosketBall skin contact sheet.")
    parser.add_argument("--out", default=str(ROOT / "docs" / "previews" / "ball_contact_sheet.png"),
                        help="where to write the contact sheet")
    parser.add_argument("--skins", default="", help="comma-separated subset of skins (default: all)")
    parser.add_argument("--rotate", action="store_true",
                        help="also write a spin strip per skin, which is how the roll is judged")
    parser.add_argument("--columns", type=int, default=4, help="cards per row")
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

    if args.rotate:
        for skin in wanted:
            strip = bm.spin_strip(models[skin], textures[skin])
            strip_path = out.with_name(f"{out.stem}_{skin}_spin.png")
            strip.save(strip_path)
        print(f"wrote {len(wanted)} spin strips next to {out.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
