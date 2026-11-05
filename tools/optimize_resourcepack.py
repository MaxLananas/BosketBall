#!/usr/bin/env python3
"""Lossless resource-pack size optimisation.

Every texture of ``resourcepack/`` is re-encoded **without touching a single
pixel**: nothing is resized, cropped, quantised to fewer colours than it already
has, or converted to a lossy format.  Only the *container* changes.

What is applied, in order, and kept only when the decoded image is bit-for-bit
identical to the original RGBA data:

1. **Colour-type reduction** - an RGBA file whose alpha is either absent
   (255 everywhere) or purely binary (0/255) is stored as ``RGB`` or as an
   indexed ``P`` image.  Same pixels, far fewer bytes per pixel.
2. **Indexed palette** - a texture with <= 256 distinct colours is stored as an
   indexed PNG with an *exact* palette (no adaptive/quantised palette, so the
   colours are literally the same values).
3. **Grayscale** - ``R == G == B`` textures are stored as ``L`` / ``LA``.
4. **zlib / Zopfli deflate** - the IDAT stream is recompressed at maximum effort.
   Deflate is lossless by definition, but every candidate is still decoded and
   compared against the original pixels before it is accepted.

Usage::

    python3 tools/optimize_resourcepack.py            # optimise in place
    python3 tools/optimize_resourcepack.py --check    # exit 1 if anything can shrink
    python3 tools/optimize_resourcepack.py --no-zopfli

``--check`` is wired into ``tools/build_plugin.py`` so a fresh build never ships
a fattened pack.  Requires Pillow; Zopfli is optional (``pip install zopfli``).
"""

from __future__ import annotations

import argparse
import binascii
import io
import os
import sys
import zlib
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - Pillow is present in the build sandbox
    Image = None

try:
    import zopfli.zlib as _zopfli_zlib
except ImportError:  # pragma: no cover - optional dependency
    _zopfli_zlib = None

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


# --------------------------------------------------------------------------- #
# PNG helpers
# --------------------------------------------------------------------------- #
def _iter_chunks(data: bytes):
    """Yield ``(type, payload)`` for every chunk of a PNG file."""
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG")
    pos = len(PNG_SIGNATURE)
    while pos + 8 <= len(data):
        length = int.from_bytes(data[pos:pos + 4], "big")
        ctype = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        yield ctype, payload


def _chunk(ctype: bytes, payload: bytes) -> bytes:
    return (
        len(payload).to_bytes(4, "big")
        + ctype
        + payload
        + binascii.crc32(ctype + payload).to_bytes(4, "big")
    )


def recompress_idat(png: bytes, use_zopfli: bool = True, iterations: int = 15) -> bytes:
    """Deflate the IDAT stream with maximum effort. Pixels are never re-filtered."""
    idat = b"".join(payload for ctype, payload in _iter_chunks(png) if ctype == b"IDAT")
    if not idat:
        return png
    raw = zlib.decompress(idat)
    if use_zopfli and _zopfli_zlib is not None:
        comp = _zopfli_zlib.compress(raw, numiterations=iterations)
    else:
        compressor = zlib.compressobj(9, zlib.DEFLATED, 15, 9, zlib.Z_FILTERED)
        comp = compressor.compress(raw) + compressor.flush()
    if len(comp) >= len(idat):
        return png
    out = bytearray(PNG_SIGNATURE)
    written = False
    for ctype, payload in _iter_chunks(png):
        if ctype == b"IDAT":
            if not written:
                out += _chunk(b"IDAT", comp)
                written = True
            continue
        if ctype == b"IEND":
            out += _chunk(b"IEND", b"")
            continue
        out += _chunk(ctype, payload)
    return bytes(out)


def _pixels(image: "Image.Image") -> list:
    """Flat list of pixels (RGBA tuples). Pillow 14 renamed ``getdata``."""
    getter = getattr(image, "get_flattened_data", None)
    return list(getter() if getter is not None else image.getdata())


def _png_bytes(image: "Image.Image", transparency: int | None = None) -> bytes:
    buf = io.BytesIO()
    kwargs = {"optimize": True, "compress_level": 9}
    if transparency is not None:
        kwargs["transparency"] = transparency
    image.save(buf, "PNG", **kwargs)
    return buf.getvalue()


def _decode(png: bytes) -> bytes:
    with Image.open(io.BytesIO(png)) as im:
        im.load()
        return im.convert("RGBA").tobytes()


# --------------------------------------------------------------------------- #
# Candidate encodings
# --------------------------------------------------------------------------- #
def _indexed_candidate(rgba: "Image.Image"):
    """Exact-palette indexed copy when the pixels allow it, else ``None``."""
    pixels = _pixels(rgba)
    alphas = {p[3] for p in pixels}
    if not alphas <= {0, 255}:
        return None
    colours = sorted({(r, g, b) for r, g, b, a in pixels if a != 0})
    has_transparency = 0 in alphas
    if len(colours) > (255 if has_transparency else 256):
        return None
    palette = [0] * 768
    for index, (r, g, b) in enumerate(colours):
        palette[index * 3:index * 3 + 3] = [r, g, b]
    transparent_index = 255
    lookup = {colour: index for index, colour in enumerate(colours)}
    data = [
        lookup[(r, g, b)] if a != 0 else transparent_index
        for r, g, b, a in pixels
    ]
    indexed = Image.new("P", rgba.size)
    indexed.putpalette(palette)
    indexed.putdata(data)
    return indexed, (transparent_index if has_transparency else None)


def _greyscale_candidate(rgba: "Image.Image"):
    """``L`` / ``LA`` copy for perfectly grey textures, else ``None``."""
    pixels = _pixels(rgba)
    if any(p[0] != p[1] or p[1] != p[2] for p in pixels):
        return None
    opaque = all(p[3] == 255 for p in pixels)
    mode = "L" if opaque else "LA"
    return rgba.convert(mode), None


def _candidates(rgba: "Image.Image"):
    """Every lossless re-encoding worth trying, richest first."""
    seen = set()
    for make in (_indexed_candidate, _greyscale_candidate):
        made = make(rgba)
        if made is None:
            continue
        image, transparency = made
        yield image, transparency
    if all(p[3] == 255 for p in _pixels(rgba)):
        yield rgba.convert("RGB"), None
    yield rgba, None


def optimise_file(path: Path, use_zopfli: bool = True, iterations: int = 15) -> tuple[int, int, str]:
    """Return ``(before, after, strategy)``; leaves the file untouched on failure."""
    original = path.read_bytes()
    with Image.open(io.BytesIO(original)) as im:
        im.load()
        reference = im.convert("RGBA")
    reference_bytes = reference.tobytes()

    best = original
    best_strategy = "unchanged"
    for image, transparency in _candidates(reference):
        try:
            encoded = _png_bytes(image, transparency)
        except (ValueError, OSError):
            continue
        for strategy, payload in (
            ("re-encode", encoded),
            ("zopfli", recompress_idat(encoded, use_zopfli, iterations)),
        ):
            if len(payload) >= len(best):
                continue
            try:
                if _decode(payload) != reference_bytes:
                    continue
            except Exception:  # noqa: BLE001 - a broken candidate must never be shipped
                continue
            best = payload
            best_strategy = strategy

    if best is not original:
        path.write_bytes(best)
    return len(original), len(best), best_strategy


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Lossless PNG optimisation for the resource pack")
    parser.add_argument("--root", default="resourcepack", help="pack directory (default: resourcepack)")
    parser.add_argument("--check", action="store_true", help="do not write, exit 1 when a texture can shrink")
    parser.add_argument("--no-zopfli", action="store_true", help="skip Zopfli, use zlib level 9 only")
    parser.add_argument("--iterations", type=int, default=15, help="Zopfli iterations (default 15)")
    args = parser.parse_args(argv)

    if Image is None:
        print("Pillow is required: pip install pillow", file=sys.stderr)
        return 2

    root = Path(args.root)
    files = sorted(p for p in root.rglob("*.png") if p.is_file())
    if not files:
        print(f"no PNG found under {root}", file=sys.stderr)
        return 2

    use_zopfli = not args.no_zopfli and _zopfli_zlib is not None
    if args.no_zopfli or _zopfli_zlib is not None:
        print(f"deflate backend: {'zopfli' if use_zopfli else 'zlib-9'}")
    else:
        print("deflate backend: zlib-9 (install 'zopfli' for the last few percent)")

    before_total = after_total = 0
    touched = []
    for path in files:
        if args.check:
            data = path.read_bytes()
            with Image.open(io.BytesIO(data)) as im:
                im.load()
                reference = im.convert("RGBA")
            before = len(data)
            after = before
            ref_bytes = reference.tobytes()
            for image, transparency in _candidates(reference):
                try:
                    encoded = _png_bytes(image, transparency)
                except (ValueError, OSError):
                    continue
                for payload in (encoded, recompress_idat(encoded, use_zopfli, args.iterations)):
                    if len(payload) < after:
                        try:
                            if _decode(payload) == ref_bytes:
                                after = len(payload)
                        except Exception:  # noqa: BLE001
                            continue
            strategy = "shrinkable"
        else:
            before, after, strategy = optimise_file(path, use_zopfli, args.iterations)
        before_total += before
        after_total += after
        if after < before:
            touched.append((path, before, after, strategy))

    touched.sort(key=lambda row: row[1] - row[2], reverse=True)
    for path, before, after, strategy in touched[:12]:
        print(f"  {before - after:>7d} B  {strategy:<10s} {path}")
    if len(touched) > 12:
        print(f"  ... and {len(touched) - 12} more")

    saved = before_total - after_total
    percent = (saved / before_total * 100.0) if before_total else 0.0
    verb = "shrinkable" if args.check else "optimised"
    print(
        f"{len(touched)}/{len(files)} textures {verb}: "
        f"{before_total:,} -> {after_total:,} bytes "
        f"({'-' if saved > 0 else '+'}{saved:,} bytes, {percent:.1f}%)"
    )
    if args.check and saved > 0:
        print("Run `python3 tools/optimize_resourcepack.py` to apply.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
