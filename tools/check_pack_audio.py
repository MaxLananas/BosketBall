#!/usr/bin/env python3
"""Fails the build when the plugin asks the pack for a sound nobody shipped.

Round 71 found ui.confirm: a key GgCommand played on every reward, defined in
no sounds.json, so the reward arrived in silence for as long as it existed. A
missing sound is invisible -- nothing logs it, nothing throws -- which is why
it needs a machine to look for it.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOUNDS = ROOT / "resourcepack/assets/bosketball/sounds.json"
CALL = re.compile(r'play(?:Personal)?PackSound\([^;]*?"([a-z0-9_.]+)"')


def main() -> int:
    if not SOUNDS.is_file():
        print(f"[audio] {SOUNDS} is missing")
        return 1
    defined = set(json.loads(SOUNDS.read_text(encoding="utf-8")))

    used: dict[str, str] = {}
    for source in sorted((ROOT / "src/main/java").rglob("*.java")):
        text = source.read_text(encoding="utf-8", errors="replace")
        for key in CALL.findall(text):
            used.setdefault(key, str(source.relative_to(ROOT)))

    missing = {k: v for k, v in used.items() if k not in defined}
    if missing:
        for key, where in sorted(missing.items()):
            print(f"[audio] FAIL {where} plays \"{key}\", which sounds.json does not define")
        return 1
    print(f"[audio] ✓ {len(used)} sound keys played by the plugin, all defined by the pack")
    return 0


if __name__ == "__main__":
    sys.exit(main())
