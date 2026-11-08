#!/usr/bin/env python3
"""Check literal customIcon calls and pack-owned item/model/texture references.

Checks literal item IDs, derived basketball mannequin IDs, item/model JSON and bitmap fonts.
Vanilla assets, other namespaces, and arbitrary dynamically constructed Java IDs remain outside
static analysis.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ICON_CALL = re.compile(r'\bcustomIcon\s*\(\s*"([a-z0-9_./]+)"')
ITEM_LITERAL = re.compile(r'"((?:icon|ref|basketball|hoop|prop|dunk|ui|ring|trophy|sneakers|tactics|court)_[a-z0-9_]+)"')
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
        text = source.read_text(encoding="utf-8")
        for name in sorted(set(ICON_CALL.findall(text)) | set(ITEM_LITERAL.findall(text))):
            exists("bosketball:" + name, "items", ".json", source.relative_to(root))
            if name.startswith("basketball_") and source.name == "BallSkin.java":
                exists("bosketball:" + name + "_mannequin", "items", ".json", source.relative_to(root))

    font_dir = root / "resourcepack/assets/minecraft/font"
    for filename in ("default.json", "uniform.json"):
        font = font_dir / filename
        try:
            doc = json.loads(font.read_text(encoding="utf-8"))
            providers = doc["providers"]
            if not isinstance(providers, list):
                raise ValueError("providers must be a list")
            for provider in providers:
                if provider.get("type") == "bitmap":
                    exists(provider.get("file", ""), "textures", "", font.relative_to(root))
        except (OSError, UnicodeError, ValueError, KeyError, AttributeError) as ex:
            errors.append(f"{font.relative_to(root)}: invalid font JSON: {ex}")

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
    print("[models] literal item IDs, derived ball mannequins, models and bitmap-font textures verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
