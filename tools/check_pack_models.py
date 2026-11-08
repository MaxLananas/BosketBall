#!/usr/bin/env python3
"""Check literal customIcon calls and pack-owned item/model/texture references.

This deliberately does not claim to resolve dynamically constructed Java model IDs,
font providers, vanilla assets, or assets supplied by another resource pack.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ICON_CALL = re.compile(r'\bcustomIcon\s*\(\s*"([a-z0-9_./]+)"')
IDENTIFIER = re.compile(r'[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*\Z')


def check(root: Path) -> list[str]:
    assets = root / "resourcepack/assets/bosketball"
    errors: list[str] = []
    if not (assets / "items").is_dir() or not (assets / "models").is_dir():
        return ["missing bosketball item or model directory"]

    def exists(identifier: str, directory: str, suffix: str, source: Path) -> None:
        if not identifier.startswith("bosketball:"):
            return  # vanilla and other namespaces are not shipped here
        name = identifier.removeprefix("bosketball:")
        if not IDENTIFIER.fullmatch(name) or any(part in (".", "..") for part in name.split("/")):
            errors.append(f"{source}: invalid resource identifier {identifier}")
        elif not (assets / directory / (name + suffix)).is_file():
            errors.append(f"{source}: missing {directory} reference {identifier}")

    java = root / "src/main/java"
    if not java.is_dir():
        errors.append("missing Java source directory")
    for source in sorted(java.rglob("*.java")):
        for name in ICON_CALL.findall(source.read_text(encoding="utf-8")):
            exists("bosketball:" + name, "items", ".json", source.relative_to(root))

    def walk(node: object, source: Path, textures: bool = False) -> None:
        if isinstance(node, list):
            for value in node:
                walk(value, source, textures)
        elif isinstance(node, dict):
            for key, value in node.items():
                if isinstance(value, str):
                    if key in ("model", "parent"):
                        exists(value, "models", ".json", source)
                    elif textures and not value.startswith("#"):
                        exists(value, "textures", ".png", source)
                else:
                    walk(value, source, textures or key == "textures")

    for directory in (assets / "items", assets / "models"):
        for source in sorted(directory.rglob("*.json")):
            try:
                document = json.loads(source.read_text(encoding="utf-8"))
            except (OSError, UnicodeError, ValueError) as ex:
                errors.append(f"{source.relative_to(root)}: invalid JSON: {ex}")
                continue
            walk(document, source.relative_to(root))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help="project root (also supports fixtures)")
    args = parser.parse_args()
    errors = check(args.root.resolve())
    if errors:
        for error in errors:
            print(f"[models] FAIL {error}")
        return 1
    print("[models] literal customIcon calls and pack-owned item/model/texture references verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
