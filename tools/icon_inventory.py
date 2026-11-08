#!/usr/bin/env python3
"""BosketBall icon inventory — the pack's icon contract, generated from disk.

Answers, for every icon in the resource pack: is it on disk, how big is it,
what palette does it use, which side is it lit from, and is anything in the
plugin actually asking for it?  Then it does the reverse sweep: which ids does
the Java ask for that no sprite answers, and which sprites does nothing ask for.

It is deliberately boring and re-runnable in one command:

    python3 tools/icon_inventory.py              # human table
    python3 tools/icon_inventory.py --markdown   # rewrite docs/agents/pack-inventory.md
    python3 tools/icon_inventory.py --sync       # create missing model/item JSON
    python3 tools/icon_inventory.py --baseline   # print the legacy light outliers
    python3 tools/icon_inventory.py --json       # machine-readable, for tooling

Exit status is 0 when the pack is coherent and 1 when it is not, so it can be
wired into a build the same way ``check_pack_audio.py`` is.  ``--sync`` and
``--markdown`` are the only modes that write anything.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

import icon_language as lang

ROOT = lang.ROOT
SRC_MAIN = ROOT / "src" / "main" / "java"
SRC_TEST = ROOT / "src" / "test" / "java"
DOC = ROOT / "docs" / "agents" / "pack-inventory.md"

#: An icon id: ``icon_<domain>_<thing>`` or ``ref_<thing>``.
#: The reference family predates the convention; it is reported, not renamed.
ID_PATTERN = re.compile(r'"(icon_[a-z0-9_]+|ref_[a-z0-9_]+)"')

#: Naming rule for icons authored in this repo.  ``icon_defense_stance``
#: conforms; ``icon_defense`` does not (no domain segment).
NAME_RULE = re.compile(r"^icon_[a-z0-9]+_[a-z0-9_]+$")

RESERVED_DOC = ROOT / "docs" / "agents" / "reserved-icons.md"


def reserved_icons() -> dict[str, str]:
    """Icons deliberately on disk with no Java caller yet, and why.

    An orphan is only a bug when nobody can say what it is for.  These are the
    ones a human has claimed: a menu that is coming, a state that exists in the
    pack before the code that will read it.  The file is plain
    ``- `icon_x` — reason`` so it stays reviewable in a diff.
    """
    if not RESERVED_DOC.is_file():
        return {}
    reserved: dict[str, str] = {}
    global_reserved: tuple[str, ...] = ()
    in_fence = False
    for line in RESERVED_DOC.read_text(encoding="utf-8").splitlines():
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue  # examples inside code blocks are not claims
        whole = re.match(r"^\s*[-*]\s+`(icon|ref)_`\s*[—:-]+\s*(.+)$", line)
        if whole:
            global_reserved = global_reserved + (whole.group(1),)
            continue
        match = re.match(r"^\s*[-*]\s+`((?:icon|ref)_[a-z0-9_]{2,})`\s*[—:-]+\s*(.+)$", line)
        if match:
            reserved[match.group(1)] = match.group(2).strip()
    # A whole-family claim (``- `ref_` — ...``) reserves every sprite in it.
    for family in global_reserved:
        for sprite in lang.icon_texture_paths():
            if sprite.stem.startswith(family + "_"):
                reserved.setdefault(sprite.stem, f"reserved as part of the {family}_ library")
    return reserved


# ---------------------------------------------------------------------------
# Reading the code
# ---------------------------------------------------------------------------


def code_references() -> dict[str, dict]:
    """Every icon id mentioned in Java, with where and how often."""
    found: dict[str, dict] = {}
    for tree, label in ((SRC_MAIN, "main"), (SRC_TEST, "test")):
        for source in sorted(tree.rglob("*.java")):
            text = source.read_text(encoding="utf-8", errors="replace")
            for ident in ID_PATTERN.findall(text):
                entry = found.setdefault(ident, {"main": 0, "test": 0, "files": set()})
                entry[label] += 1
                entry["files"].add(str(source.relative_to(ROOT)))
    return found


# ---------------------------------------------------------------------------
# Building the inventory
# ---------------------------------------------------------------------------


def on_disk() -> dict[str, dict]:
    """Every icon sprite present in the pack, keyed by id."""
    sprites: dict[str, dict] = {}
    for path in lang.icon_texture_paths():
        image = Image.open(path).convert("RGBA")
        info = lang.describe(image, path.stem)
        verdict = info["light_verdict"]
        if path.stem in lang.FIGURATIVE_BRIGHT:
            note = lang.FIGURATIVE_BRIGHT[path.stem]
        elif verdict == "reviewable":
            note = lang.LEGACY_BOTTOM_LIT.get(path.stem, "")
        else:
            note = ""
        info.update(
            {
                "path": str(path.relative_to(ROOT)),
                "family": "icon" if path.stem.startswith("icon_") else "ref",
                "model": (lang.ITEM_MODELS / f"{path.stem}.json").is_file(),
                "item": (lang.ITEM_DEFS / f"{path.stem}.json").is_file(),
                "light_note": note,
            }
        )
        sprites[path.stem] = info
    return sprites


def build() -> dict:
    sprites = on_disk()
    refs = code_references()

    reserved = reserved_icons()
    missing = sorted(k for k in refs if k not in sprites)
    # "Used" means mentioned anywhere — main source OR the test suite.  The
    # test suite enumerates the pack deliberately (it asserts every icon ships
    # at 16x16 with a model and an item), so an icon it names is accounted for
    # even when no screen has picked it up yet.  An orphan is a sprite nobody
    # mentions at all: not the plugin, not the tests, not this document.
    def _uses(ident):
        entry = refs.get(ident, {})
        return entry.get("main", 0) + entry.get("test", 0)
    orphans = sorted(k for k in sprites if _uses(k) == 0)
    undocumented = [k for k in orphans if k not in reserved]
    stale_reservations = sorted(k for k in reserved if k not in sprites)

    # Two ids pointing at byte-identical art is either deliberate (an alias) or
    # a copy-paste mistake.  Both need to be visible, so the inventory names the
    # pair and the count of shared bytes rather than guessing.
    by_bytes: dict[int, list[str]] = defaultdict(list)
    for ident, info in sprites.items():
        by_bytes[(ROOT / info["path"]).stat().st_size].append(ident)
    shared = []
    for size, group in by_bytes.items():
        if len(group) < 2:
            continue
        buckets: dict[bytes, list[str]] = defaultdict(list)
        for ident in group:
            buckets[(ROOT / sprites[ident]["path"]).read_bytes()].append(ident)
        for blob, ids in buckets.items():
            if len(ids) > 1:
                shared.append(sorted(ids))
    shared.sort()

    naming_violations = sorted(
        k for k in sprites if k.startswith("icon_") and not NAME_RULE.match(k)
    )
    unwired = sorted(k for k in sprites if not (sprites[k]["model"] and sprites[k]["item"]))

    light_tally = Counter(info["light_verdict"] for info in sprites.values())

    # Icons the plugin's own screens ask for, as opposed to icons only the pack
    # test suite names.  Agent 3 wants the first group; the second is inventory.
    main_called = sorted(k for k in refs if refs[k].get("main", 0) > 0 and k in sprites)

    return {
        "sprites": sprites,
        "main_called": main_called,
        "references": {k: {"main": v["main"], "test": v["test"], "files": sorted(v["files"])} for k, v in refs.items()},
        "missing": missing,
        "orphans": orphans,
        "shared_textures": shared,
        "naming_violations": naming_violations,
        "reserved": reserved,
        "undocumented_orphans": undocumented,
        "stale_reservations": stale_reservations,
        "unwired": unwired,
        "light_tally": dict(light_tally),
    }


# ---------------------------------------------------------------------------
# Repair
# ---------------------------------------------------------------------------


def wire_missing(inventory: dict) -> int:
    """Create the model and item JSON for any sprite that lacks them.

    An icon is only usable when three files agree: the texture, the model that
    points at it, and the item definition that points at the model.  A sprite
    with two out of three is invisible to the game, which is exactly the kind
    of gap that survives for months because nothing errors.
    """
    made = 0
    for ident in inventory["unwired"]:
        info = inventory["sprites"][ident]
        if not info["model"]:
            model = {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"bosketball:item/{info['texture_ref']}"},
                "display": {
                    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
                    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                    "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
                    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
                },
            }
            (lang.ITEM_MODELS / f"{ident}.json").write_text(json.dumps(model, indent=2), encoding="utf-8")
            made += 1
        if not info["item"]:
            item = {
                "model": {"type": "minecraft:model", "model": f"bosketball:item/{ident}"},
                "hand_animation_on_swap": False,
            }
            (lang.ITEM_DEFS / f"{ident}.json").write_text(json.dumps(item, indent=2), encoding="utf-8")
            made += 1
    return made


def emit_authored() -> int:
    """Write every sprite ``icon_language`` draws, with its model and item JSON.

    ``generate_resourcepack.py`` writes the same files as part of a full art
    rebuild; this entry point exists so a change to one icon does not drag the
    other 200-odd sprites' PNG encoding into the diff.
    """
    written = 0
    for ident, image in lang.authored_icon_sprites().items():
        image.save(lang.ITEM_TEXTURES / f"{ident}.png")
        written += 1
        model_path = lang.ITEM_MODELS / f"{ident}.json"
        if not model_path.is_file():
            model = {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"bosketball:item/{ident}"},
                "display": {
                    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
                    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
                    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                    "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
                    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
                },
            }
            # No trailing newline: byte-for-byte the same file the generator
            # writes, so the two entry points cannot disagree in a diff.
            model_path.write_text(json.dumps(model, indent=2), encoding="utf-8")
            written += 1
        item_path = lang.ITEM_DEFS / f"{ident}.json"
        if not item_path.is_file():
            item = {
                "model": {"type": "minecraft:model", "model": f"bosketball:item/{ident}"},
                "hand_animation_on_swap": False,
            }
            item_path.write_text(json.dumps(item, indent=2), encoding="utf-8")
            written += 1
    return written


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------


def human_report(inv: dict) -> str:
    sprites = inv["sprites"]
    lines = []
    lines.append(f"BosketBall icon inventory — {len(sprites)} sprites")
    lines.append("")
    lines.append(f"{'id':26}{'kind':6}{'size':7}{'col':5}{'light':16}{'code':6}{'files'}")
    lines.append("-" * 78)
    for ident in sorted(sprites):
        info = sprites[ident]
        ref = inv["references"].get(ident, {})
        lines.append(
            f"{ident:26}{info['family']:6}{info['size'][0]:<2}x{info['size'][1]:<2} "
            f"{info['colours']:<5}{info['light'] + ' (' + info['light_verdict'] + ')':16}"
            f"{ref.get('main', 0):<6}{len(ref.get('files', []))}"
        )
    lines.append("")
    lines.append("Summary")
    lines.append(f"  sprites on disk        {len(sprites)}")
    lines.append(f"  ids referenced in Java {len(inv['references'])}")
    lines.append(f"  light verdicts         {inv['light_tally']}")
    lines.append("")
    for label, key in (
        ("Referenced in Java but missing on disk", "missing"),
        ("On disk but referenced nowhere", "orphans"),
        ("  ...of which documented as reserved", "reserved"),
        ("  ...of which undocumented (a real gap)", "undocumented_orphans"),
        ("Reserved entries with no sprite on disk", "stale_reservations"),
        ("Naming rule violations", "naming_violations"),
        ("Sprites missing model or item JSON", "unwired"),
    ):
        items = inv[key]
        if isinstance(items, dict):
            items = sorted(items)
        lines.append(f"  {label}: {len(items)}")
        if len(items) <= 40:
            for item in items:
                lines.append(f"      {item}")
    lines.append(f"  Byte-identical sprite pairs: {len(inv['shared_textures'])}")
    for pair in inv["shared_textures"]:
        lines.append(f"      {' == '.join(pair)}")
    return "\n".join(lines)


def markdown_report(inv: dict) -> str:
    sprites = inv["sprites"]
    refs = inv["references"]
    out = []
    out.append("# Pack inventory — icons")
    out.append("")
    out.append("Generated by `python3 tools/icon_inventory.py --markdown`. Do not edit by hand:")
    out.append("re-run the command. This file is the contract Agent 5 (ball models) and")
    out.append("Agent 3 (interfaces) build against.")
    out.append("")
    out.append("## How to read this")
    out.append("")
    out.append("| column | meaning |")
    out.append("|---|---|")
    out.append("| `code` | how many times the id appears in `src/main/java` |")
    out.append("| `files` | how many main-source files mention it |")
    out.append("| `light` | where the brightest tone sits, measured by `icon_language.light_vector` |")
    out.append("| `ink` | the silhouette-edge ink colour |")
    out.append("")
    out.append("An icon is usable only when the texture, `models/item/<id>.json` and")
    out.append("`items/<id>.json` all exist. Anything else is invisible to the game.")
    out.append("")
    out.append("## Totals")
    out.append("")
    out.append("| metric | value |")
    out.append("|---|---|")
    out.append(f"| sprites on disk | {len(sprites)} |")
    out.append(f"| `icon_*` hand-drawn | {sum(1 for i in sprites.values() if i['family'] == 'icon')} |")
    out.append(f"| `ref_*` reference sheet | {sum(1 for i in sprites.values() if i['family'] == 'ref')} |")
    out.append(f"| ids referenced in Java | {len(refs)} |")
    out.append(f"| lit from the key light | {inv['light_tally'].get('ok', 0)} |")
    out.append(f"| flat / unshaded | {inv['light_tally'].get('flat', 0)} |")
    out.append(f"| figurative bright feature | {inv['light_tally'].get('figurative', 0)} |")
    out.append(f"| legacy bottom-lit (documented) | {inv['light_tally'].get('reviewable', 0)} |")
    out.append(f"| **new bottom-lit (defect)** | **{inv['light_tally'].get('wrong-side', 0)}** |")
    out.append("")
    out.append("## Gaps")
    out.append("")
    out.append("### Referenced in Java, missing on disk")
    out.append("")
    if inv["missing"]:
        for ident in inv["missing"]:
            out.append(f"- `{ident}` — referenced by {', '.join(refs[ident]['files'])}")
    else:
        out.append("None.")
    out.append("")
    out.append("### On disk, referenced nowhere")
    out.append("")
    out.append(f"{len(inv['orphans'])} sprites have no caller in the plugin. That is not")
    out.append("automatically wrong: the pack is allowed to hold art before the screen that")
    out.append("reads it exists. What is not allowed is an orphan nobody can account for.")
    out.append("")
    out.append(f"- **documented as reserved** ({len(inv['reserved'])}): listed in")
    out.append("  [`reserved-icons.md`](reserved-icons.md) with the reason.")
    undoc = inv["undocumented_orphans"]
    out.append(f"- **undocumented** ({len(undoc)}): either wire them up or claim them in")
    out.append("  `reserved-icons.md`.")
    out.append("")
    if undoc:
        out.append("Undocumented:")
        out.append("")
        for ident in undoc:
            out.append(f"- `{ident}`")
    else:
        out.append("Every orphan is documented.")
    if inv["stale_reservations"]:
        out.append("")
        out.append("Reserved but not on disk (stale entries):")
        out.append("")
        for ident in inv["stale_reservations"]:
            out.append(f"- `{ident}`")
    out.append("")
    out.append("### Naming rule violations (`icon_<domain>_<thing>`)")
    out.append("")
    if inv["naming_violations"]:
        out.append("These predate the rule and are already referenced from Java. They are")
        out.append("reported rather than renamed: a rename at this point would touch five")
        out.append("Java files and a config for a cosmetic gain. New icons must conform.")
        out.append("")
        out.append(", ".join(f"`{n}`" for n in inv["naming_violations"]))
    else:
        out.append("None.")
    out.append("")
    out.append("### Byte-identical sprite pairs")
    out.append("")
    if inv["shared_textures"]:
        out.append("Each pair below is the same art under two ids. All of them are deliberate:")
        out.append("`ref_*` is the imported reference sheet and `icon_*` is the hand-drawn")
        out.append("alias the plugin actually uses. Any NEW pair is a mistake.")
        out.append("")
        for pair in inv["shared_textures"]:
            out.append(f"- {' == '.join(f'`{p}`' for p in pair)}")
    else:
        out.append("None.")
    out.append("")
    out.append("## Every icon")
    out.append("")
    out.append("| id | family | size | colours | light | verdict | code | files | ink |")
    out.append("|---|---|---|---|---|---|---|---|---|")
    for ident in sorted(sprites):
        info = sprites[ident]
        ref = refs.get(ident, {})
        ink = info["ink"]
        ink_txt = f"#{ink[0]:02X}{ink[1]:02X}{ink[2]:02X}" if ink else "none"
        out.append(
            f"| `{ident}` | {info['family']} | {info['size'][0]}×{info['size'][1]} | {info['colours']} | "
            f"{info['light']} | {info['light_verdict']} | {ref.get('main', 0)} | {len(ref.get('files', []))} | {ink_txt} |"
        )
    out.append("")
    notes = {k: v for k, v in lang.FIGURATIVE_BRIGHT.items()}
    notes.update(lang.LEGACY_BOTTOM_LIT)
    out.append("## Documented light outliers")
    out.append("")
    out.append("Sprites whose brightest tone is a feature or a pre-existing legacy trait,")
    out.append("not a light source. Reviewed by hand; the reason is recorded so a future")
    out.append("artist can disagree with a line rather than with the rule.")
    out.append("")
    out.append("| id | why it is not a specular |")
    out.append("|---|---|")
    for ident in sorted(notes):
        if ident in sprites:
            out.append(f"| `{ident}` | {notes[ident]} |")
    out.append("")
    return "\n".join(out) + "\n"


def baseline_report(inv: dict) -> str:
    """Print the literal to paste into ``icon_language.LEGACY_BOTTOM_LIT``."""
    outliers = sorted(
        ident for ident, info in inv["sprites"].items() if info["light_verdict"] == "reviewable"
    )
    lines = ["LEGACY_BOTTOM_LIT: dict[str, str] = {"]
    for ident in outliers:
        note = inv["sprites"][ident]["light_note"] or "measured as bottom-lit; cause not yet described"
        lines.append(f'    "{ident}": "{note}",')
    lines.append("}")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Inventory the BosketBall icon set.")
    parser.add_argument("--json", action="store_true", help="machine-readable output")
    parser.add_argument("--markdown", action="store_true", help="rewrite docs/agents/pack-inventory.md")
    parser.add_argument("--sync", action="store_true", help="create missing model and item JSON")
    parser.add_argument("--baseline", action="store_true", help="print the legacy light-outlier table")
    parser.add_argument("--check", action="store_true", help="exit 1 if the pack is not coherent")
    parser.add_argument("--emit", action="store_true",
                        help="write the sprites this repo authors, plus their model and item JSON")
    args = parser.parse_args(argv)

    inv = build()

    if args.baseline:
        print(baseline_report(inv))
        return 0

    if args.emit:
        written = emit_authored()
        print(f"[icons] wrote {written} file(s) for {len(lang.authored_icon_sprites())} authored sprite(s)")
        inv = build()

    if args.sync:
        made = wire_missing(inv)
        print(f"[icons] wrote {made} JSON file(s) for {len(inv['unwired'])} sprite(s)")
        inv = build()

    if args.json:
        print(json.dumps(inv, indent=2, default=list))
        return 0

    if args.markdown:
        DOC.parent.mkdir(parents=True, exist_ok=True)
        DOC.write_text(markdown_report(inv), encoding="utf-8")
        print(f"[icons] wrote {DOC.relative_to(ROOT)}")

    print(human_report(inv))

    defects = (
        len(inv["missing"])
        + inv["light_tally"].get("wrong-side", 0)
        + len(inv["unwired"])
        + len(inv["stale_reservations"])
    )
    if defects and args.check:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
