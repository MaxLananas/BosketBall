#!/usr/bin/env python3
"""Renders the in-game HUD strings offline, at one screen pixel per game pixel.

The plugin draws its HUD out of glyphs from the resource pack: a pill built from a nine-slice,
a charge bar sliding inside a rail. None of that can be seen without launching a client, and a
mistake in the advance maths does not throw -- it just looks wrong on screen, which is the worst
kind of bug to find late.

So this script re-implements the same three pieces of arithmetic as ``Glyphs.java``
(``textAdvancePx``, ``composePill``, ``composeShotBar``), walks the resulting glyph string the way
the client does -- including the negative-advance shift glyphs -- and writes a PNG.

Usage::

    python3 tools/preview_hud.py            # writes docs/hud-preview.png
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "resourcepack"
JAVA = ROOT / "src/main/java/fr/bosketball/util/Glyphs.java"
OUT = ROOT / "docs/hud-preview.png"

# --------------------------------------------------------------------------------------
# the same constants as Glyphs.java
# --------------------------------------------------------------------------------------
PILL_LEFT, PILL_BODY, PILL_RIGHT = "\uF019", "\uF01A", "\uF01B"
HUD_BAR_RAIL = "\uF01C"
PILL_CAPS_WIDTH, PILL_BODY_WIDTH = 12, 6
MAX_PILL_WIDTH = 176
HUD_BAR_FIRST = 0xF000
HUD_BAR_STEPS = 25
SLICE_WIDTHS = [4, 9, 14, 19, 24, 28, 33, 38, 43, 48, 52, 57, 62, 67, 72, 76, 81, 86, 91, 96,
                100, 105, 110, 115, 120]


def load_advances() -> dict[str, int]:
    """Reads putWidth("abc", 2) out of Glyphs.java so the preview cannot drift from the code."""
    src = JAVA.read_text()
    table: dict[str, int] = {}
    for chars, width in re.findall(r'putWidth\("((?:[^"\\]|\\.)*)",\s*(\d+)\);', src):
        decoded = chars.encode().decode("unicode_escape")
        for ch in decoded:
            table[ch] = int(width)
    if not table:
        sys.exit("could not read CHAR_ADVANCE from Glyphs.java")
    return table


ADV = load_advances()


def text_advance(plain: str, bold: bool = False) -> int:
    total = 0
    for ch in plain:
        if ch == " ":
            total += 4
            continue
        total += ADV.get(ch, 6) + (1 if bold else 0)
    return total


def shift(pixels: int) -> str:
    if pixels == 0:
        return ""
    out = []
    base = 0xE001 if pixels < 0 else 0xE011
    remaining = abs(pixels)
    for bit in range(8, -1, -1):
        val = 1 << bit
        while remaining >= val:
            out.append(chr(base + bit))
            remaining -= val
    return "".join(out)


def shot_bar_width(progress: float) -> int:
    clamped = max(0.0, min(1.0, progress))
    step = round(clamped * (HUD_BAR_STEPS - 1))
    return SLICE_WIDTHS[max(0, min(HUD_BAR_STEPS - 1, step))]


def shot_bar_glyph(progress: float) -> str:
    clamped = max(0.0, min(1.0, progress))
    step = round(clamped * (HUD_BAR_STEPS - 1))
    return chr(HUD_BAR_FIRST + max(0, min(HUD_BAR_STEPS - 1, step)))


def compose_shot_bar(progress: float) -> str:
    fill = shot_bar_width(progress)
    rail = SLICE_WIDTHS[-1]
    return HUD_BAR_RAIL + shift(-rail) + shot_bar_glyph(progress) + shift(rail - fill)


def compose_pill(text: str, bold: bool = False) -> tuple[str, int, int]:
    """Returns (glyph string, pill width, text advance). A pill of 0 means plain text."""
    adv = text_advance(text, bold)
    if adv + 20 > MAX_PILL_WIDTH:
        return text, 0, adv
    body = max(4, -(-(adv + 20 - PILL_CAPS_WIDTH) // PILL_BODY_WIDTH))
    width = PILL_CAPS_WIDTH + body * PILL_BODY_WIDTH
    pad_left = max(0, (width - adv) // 2)
    shape = PILL_LEFT + PILL_BODY * body + PILL_RIGHT + shift(-width) + shift(pad_left)
    return shape + text + shift(width - pad_left - adv), width, adv


# --------------------------------------------------------------------------------------
# rasteriser: walks the glyph string the way the client does
# --------------------------------------------------------------------------------------
class Font:
    def __init__(self) -> None:
        data = json.loads((PACK / "assets/minecraft/font/default.json").read_text())
        self.providers: dict[str, tuple[Path, int, int]] = {}
        self.images: dict[Path, Image.Image] = {}
        for prov in data["providers"]:
            if prov.get("type") != "bitmap":
                continue
            ns, _, rel = prov["file"].partition(":")
            path = PACK / "assets" / ns / "textures" / rel
            for ch in prov["chars"]:
                self.providers[ch] = (path, prov["ascent"], prov["height"])

    def image(self, path: Path) -> Image.Image:
        if path not in self.images:
            self.images[path] = Image.open(path).convert("RGBA")
        return self.images[path]

    def draw(self, canvas: Image.Image, pen: int, baseline: int, ch: str) -> int:
        """Draws one char and returns the new pen position."""
        cp = ord(ch)
        if 0xE001 <= cp <= 0xE009:
            return pen - (1 << (cp - 0xE001))
        if 0xE011 <= cp <= 0xE019:
            return pen + (1 << (cp - 0xE011))
        if ch in self.providers:
            path, ascent, height = self.providers[ch]
            img = self.image(path)
            scale = height / img.height
            w = max(1, round(img.width * scale))
            h = max(1, round(img.height * scale))
            if (w, h) != img.size:
                img = img.resize((w, h), Image.NEAREST)
            canvas.alpha_composite(img, (max(0, pen), baseline - ascent))
            return pen + round(img.width * (height / img.height)) or pen + 1
        # plain text: a stand-in block of the right width, so the geometry stays measurable
        adv = ADV.get(ch, 6)
        if ch != " ":
            d = ImageDraw.Draw(canvas)
            d.rectangle([pen, baseline - 7, pen + max(1, adv - 2), baseline - 1], fill=(150, 165, 190, 255))
        return pen + adv


def render(lines, width=320, scale=3):
    font = Font()
    row_h = 34
    height = 18 + row_h * len(lines)
    canvas = Image.new("RGBA", (width, height), (16, 18, 22, 255))
    d = ImageDraw.Draw(canvas)
    y = 10
    report = []
    for label, glyphs, meta in lines:
        baseline = y + 9
        pen = 6
        start = pen
        for ch in glyphs:
            pen = font.draw(canvas, pen, baseline, ch)
        report.append((label, meta, start, pen))
        d.text((6, y + 19), label, fill=(96, 108, 130, 255))
        # a hairline at the baseline of the row, to check vertical centring
        d.line([0, baseline, width, baseline], fill=(40, 45, 55, 255))
        y += row_h
    big = canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)
    return big, report


def main() -> None:
    cases = [
        ("Intercepted pass!", True, True, 0),
        ("Shot clock expired \u00b7 24s", True, True, 0),
        ("Dive \u00b7 ball recovered", True, True, 0),
        ("Defender on \u00b7 practice", True, True, 0),
        ("A line far too long to sit in a pill, so it stays plain text", True, True, 1),
    ]
    lines = []
    checks = []
    for text, bold, _, expect_plain in cases:
        glyphs, pill_w, adv = compose_pill(text, bold)
        plain = pill_w == 0
        checks.append((text, pill_w, adv, plain, expect_plain))
        lines.append((("plain (too wide)" if plain else "pill %dpx" % pill_w), glyphs, (pill_w, adv)))

    bars = []
    for pct in (0.0, 0.35, 0.70, 1.0):
        lines.append(("shot bar %d%%" % round(pct * 100), compose_shot_bar(pct), (120, shot_bar_width(pct))))
        bars.append((pct, shot_bar_width(pct)))

    img, report = render(lines)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)

    ok = True
    for text, pill_w, adv, plain, expect_plain in checks:
        if plain != bool(expect_plain):
            print("FAIL %r: pill=%s expected plain=%s" % (text, not plain, bool(expect_plain)))
            ok = False
        elif not plain:
            margin_left = (pill_w - adv) // 2
            margin_right = pill_w - margin_left - adv
            if margin_left < 4 or margin_right < 4 or abs(margin_left - margin_right) > 1:
                print("FAIL %r: text not centred (left %d, right %d)" % (text, margin_left, margin_right))
                ok = False
            print("ok   pill %3dpx  text %3dpx  margins %d/%d  %r" % (pill_w, adv, margin_left, margin_right, text))
        else:
            print("ok   plain     text %3dpx  %r" % (adv, text))
    for pct, w in bars:
        print("ok   bar %3d%%  fill %3dpx of 120" % (round(pct * 100), w))
    print("preview written to", OUT.relative_to(ROOT), img.size)
    if not ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
