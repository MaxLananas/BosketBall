#!/usr/bin/env python3
"""Apply the BosketBall icon language to every sprite in the pack.

This is the mechanical half of the conformance pass.  It cannot invent art, so
it is safe to run at any time and safe to run twice:

  * one ink colour on every silhouette edge (``icon_language.INK``),
  * no stray lone pixels,
  * binary alpha only.

It does NOT touch shading, silhouettes or colour ramps.  Those are drawings,
and drawings are the artist's business; see ``docs/agents/icon-language.md``.

Run:
    python3 tools/icon_conformance.py           # rewrite the pack in place
    python3 tools/icon_conformance.py --check   # report drift, change nothing
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

import icon_language as lang


def main(argv: list[str]) -> int:
    check_only = "--check" in argv
    verbose = "--verbose" in argv or "-v" in argv

    textures = lang.icon_texture_paths()
    if not textures:
        print("[icons] no icon textures found — is the resource pack generated?")
        return 1

    totals = {"alpha": 0, "stray": 0, "ink": 0}
    touched = []

    for path in textures:
        image = Image.open(path).convert("RGBA")
        conforming, report = lang.conform(image)
        changed = sum(report.values())
        if changed:
            touched.append((path, report))
            for key in totals:
                totals[key] += report[key]
            if not check_only:
                conforming.save(path)

    print(f"[icons] {len(textures)} sprites checked against the icon language")
    if touched:
        verb = "would change" if check_only else "changed"
        print(
            f"[icons] {verb} {len(touched)} sprites — "
            f"ink {totals['ink']}px, stray {totals['stray']}px, alpha {totals['alpha']}px"
        )
        if verbose:
            for path, report in touched:
                print(f"          {path.name}: {report}")
        if check_only:
            print("[icons] re-run without --check to apply")
            return 1
    else:
        print("[icons] every sprite already conforms")

    stray_after = sum(len(lang.isolated_pixels(Image.open(p).convert("RGBA"))) for p in textures)
    if stray_after:
        print(f"[icons] {stray_after} stray pixel(s) survived conformance — investigate")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
