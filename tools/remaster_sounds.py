#!/usr/bin/env python3
"""Remaster the BosketBall resource-pack sound bank.

The shipped bank was wildly unbalanced: the buzzer peaked above 0 dBFS while rim,
backboard and block impacts sat around -21 dBFS RMS, i.e. inaudible in game. This
script rewrites every OGG to a fixed per-role loudness target, removes DC offset,
high-passes the rumble out of the impact samples, soft-limits the peaks and keeps
the true peak at -1 dBFS. `resourcepack/assets/bosketball/sounds.json` is then
regenerated so every event plays at unity gain.

Run with:
    python3 tools/remaster_sounds.py [--check]
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
SOUNDS = ROOT / "resourcepack" / "assets" / "bosketball" / "sounds"
SOUNDS_JSON = ROOT / "resourcepack" / "assets" / "bosketball" / "sounds.json"

TRUE_PEAK_CEILING = 0.891

TARGET_RMS_DBFS = {
    "bounce": -13.0,
    "rim": -12.5,
    "backboard": -13.0,
    "block_swat": -12.0,
    "swish": -14.0,
    "dunk": -11.5,
    "buzzer": -11.0,
    "whistle": -12.0,
    "crowd_cheer": -17.0,
    "green_release": -13.5,
    "on_fire": -13.5,
    "ui_click": -16.5,
    "sneaker_squeak": -20.0,
}

EVENTS = {
    "ball.bounce": ("bounce", "bosketball.sub.bounce"),
    "ball.swish": ("swish", "bosketball.sub.swish"),
    "ball.rim": ("rim", "bosketball.sub.rim"),
    "ball.rim_hit": ("rim", "bosketball.sub.rim"),
    "ball.backboard": ("backboard", "bosketball.sub.backboard"),
    "ball.dunk": ("dunk", "bosketball.sub.dunk"),
    "game.dunk_boom": ("dunk", "bosketball.sub.dunk"),
    "ball.block": ("block_swat", "bosketball.sub.rim"),
    "defense.block": ("block_swat", "bosketball.sub.rim"),
    "player.sneaker": ("sneaker_squeak", "bosketball.sub.bounce"),
    "shoe.squeak": ("sneaker_squeak", "bosketball.sub.bounce"),
    "crowd.cheer": ("crowd_cheer", "bosketball.sub.on_fire"),
    "ui.click": ("ui_click", None),
    "ui.confirm": ("green_release", None),
    "buzzer": ("buzzer", "bosketball.sub.buzzer"),
    "whistle": ("whistle", "bosketball.sub.whistle"),
    "game.buzzer": ("buzzer", "bosketball.sub.buzzer"),
    "game.whistle": ("whistle", "bosketball.sub.whistle"),
    "game.green_release": ("green_release", "bosketball.sub.green"),
    "game.on_fire": ("on_fire", "bosketball.sub.on_fire"),
    "empty": ("empty", None),
}


def highpass(data: np.ndarray, sample_rate: int, cutoff_hz: float = 60.0) -> np.ndarray:
    rc = 1.0 / (2.0 * np.pi * cutoff_hz)
    dt = 1.0 / sample_rate
    alpha = rc / (rc + dt)
    out = np.empty_like(data)
    prev_x = 0.0
    prev_y = 0.0
    for i, x in enumerate(data):
        y = alpha * (prev_y + x - prev_x)
        out[i] = y
        prev_x = x
        prev_y = y
    return out


def soft_limit(data: np.ndarray, knee: float = 0.72) -> np.ndarray:
    absd = np.abs(data)
    over = absd > knee
    if not np.any(over):
        return data
    headroom = TRUE_PEAK_CEILING - knee
    scaled = knee + headroom * np.tanh((absd[over] - knee) / headroom)
    out = data.copy()
    out[over] = np.sign(data[over]) * scaled
    return out


def process(name: str, check_only: bool) -> tuple[float, float]:
    path = SOUNDS / f"{name}.ogg"
    data, rate = sf.read(path, always_2d=True, dtype="float64")
    if data.size == 0 or float(np.max(np.abs(data))) == 0.0:
        print(f"  {name:<16s} silent sample — left untouched")
        return 0.0, 0.0

    mono = data.mean(axis=1)
    mono = mono - float(np.mean(mono))
    mono = highpass(mono, rate)

    target = 10.0 ** (TARGET_RMS_DBFS[name] / 20.0)
    rms = float(np.sqrt(np.mean(mono ** 2)))
    if rms <= 0.0:
        return 0.0, 0.0
    mono = mono * (target / rms)

    mono = soft_limit(mono)
    peak = float(np.max(np.abs(mono)))
    if peak > TRUE_PEAK_CEILING:
        mono = mono * (TRUE_PEAK_CEILING / peak)

    fade = max(1, int(rate * 0.004))
    if len(mono) > 2 * fade:
        ramp = np.linspace(0.0, 1.0, fade)
        mono[:fade] *= ramp
        mono[-fade:] *= ramp[::-1]

    before_rms = 20.0 * np.log10(max(rms, 1e-9))
    after_rms = 20.0 * np.log10(max(float(np.sqrt(np.mean(mono ** 2))), 1e-9))
    before_peak = float(np.max(np.abs(data)))
    after_peak = float(np.max(np.abs(mono)))

    if not check_only:
        sf.write(path, mono.astype(np.float32), rate, format="OGG", subtype="VORBIS")

    print(
        f"  {name:<16s} rms {before_rms:6.1f} → {after_rms:6.1f} dBFS"
        f"   peak {before_peak:.3f} → {after_peak:.3f}"
    )
    return after_rms, after_peak


def write_sounds_json() -> None:
    document: dict[str, object] = {}
    for event, (sample, subtitle) in EVENTS.items():
        entry: dict[str, object] = {}
        if subtitle:
            entry["subtitle"] = subtitle
        entry["sounds"] = [{"name": f"bosketball:{sample}", "volume": 0.0 if sample == "empty" else 1.0}]
        document[event] = entry
    SOUNDS_JSON.write_text(json.dumps(document, indent=2) + "\n")
    print(f"  sounds.json rewritten — {len(document)} events")


def main() -> int:
    check_only = "--check" in sys.argv[1:]
    print(f"Remastering {len(TARGET_RMS_DBFS)} samples (check_only={check_only})")
    for name in TARGET_RMS_DBFS:
        process(name, check_only)
    if not check_only:
        write_sounds_json()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
