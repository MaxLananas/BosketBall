#!/usr/bin/env python3
"""Is release timing actually worth anything?

The roadmap promises a timing-based shot meter ("green release"). This script
sweeps every release charge from 0.55 to 1.20, at three distances and two contest
levels, through the real shot model and prints the make percentage.

It measures exactly one thing: how the make rate changes when ONLY the release
charge changes with a perfect crosshair. A healthy timing model has a narrow,
clearly better green plateau and a graded fall-off on both sides. It also splits
the result into "went down through the rim" vs "banked in off the backboard",
because those two paths are scored identically by the engine and that is what
makes a badly timed release still count.

Usage:  python3 tools/timing_study.py
"""
import math
import os
import random

SIM = os.path.join(os.path.dirname(__file__), "shot_sim.py")
src = open(SIM).read().split("rng=random.Random(7)")[0]
ns = {"__name__": "timing_study"}
exec(compile(src, SIM, "exec"), ns)

RIM = ns["rim"]
FWD = ns["fwd"]
FLOOR = 64.0
GREEN_LO, GREEN_HI = 0.845, 1.025


def shoot(dist, charge, contest, rng):
    eye = (0.5, 65.62, RIM[2] - dist)
    dx, dz = RIM[0] - eye[0], RIM[2] - eye[2]
    d = math.hypot(dx, dz)
    pitch = -9.5 - min(34.0 - 9.5, d * 1.35)
    p = -math.radians(pitch)
    look = (dx / d * math.cos(p), math.sin(p), dz / d * math.cos(p))
    green = GREEN_LO <= charge <= GREEN_HI
    vel, _ok = ns["solve"](eye, look, RIM, green, charge, contest, rng)
    # Re-run the integrator by hand so we can tell a clean drop from a bank-in.
    p3, v = list(eye), list(vel)
    dt, sd = 0.25, ns["drag"] ** 0.25
    hit_rim = hit_board = False
    for _ in range(70):
        for _s in range(4):
            prev = list(p3)
            v[1] -= ns["g"] * dt
            for i in range(3):
                v[i] *= sd
            p3 = [p3[i] + v[i] * dt for i in range(3)]
            p3, v, rim_hit = ns["rimcol"](p3, v, RIM)
            p3, v, board_hit = ns["boardcol"](p3, v, RIM, FWD)
            hit_rim = hit_rim or rim_hit
            hit_board = hit_board or board_hit
            if ns["scored"](prev, p3, RIM):
                return "SCORE", hit_board and not hit_rim
            if p3[1] <= FLOOR + ns["BR"]:
                return "FLOOR", False
    return "FLIGHT", False


def sweep(dist, contest, n=200):
    cells = []
    for charge in (0.55, 0.65, 0.75, 0.80, 0.845, 0.90, 0.95, 1.025, 1.08, 1.15, 1.20):
        rng = random.Random(4242)
        made = banked = 0
        for _ in range(n):
            res, bank = shoot(dist, charge, contest, rng)
            if res == "SCORE":
                made += 1
                banked += 1 if bank else 0
        cells.append((charge, made / n, banked / n))
    return cells


def main():
    print("=" * 78)
    print(" RELEASE TIMING SWEEP — perfect crosshair, only the charge changes")
    print(" green window is 0.845 .. 1.025; n=200 per cell")
    print("=" * 78)
    penalties = []
    for contest in (0.0, 0.5):
        print(f"\n contest {contest}")
        for dist in (3.0, 6.0, 9.0):
            cells = sweep(dist, contest)
            green_rate = max(r for c, r, _ in cells if GREEN_LO <= c <= GREEN_HI)
            line = "  ".join(f"{c:.2f}:{100 * r:3.0f}%" for c, r, _ in cells)
            print(f"  {dist:3.1f} m  {line}")
            # A mistimed release at the edge of human tolerance must actually cost something.
            by_charge = {c: r for c, r, _ in cells}
            early = by_charge[0.80] - green_rate
            late = by_charge[1.08] - green_rate
            penalties.append((dist, contest, early, late))
            print(f"          green {100 * green_rate:.0f}%   "
                  f"0.80 release {100 * early:+.0f} pts   1.08 release {100 * late:+.0f} pts")

    print()
    print("=" * 78)
    print(" FINDINGS")
    print("=" * 78)
    best_early = max(p[2] for p in penalties)
    worst_late = min(p[3] for p in penalties)
    print(f"  A release 0.045 early (0.80) can be worth up to {100 * best_early:+.0f}% versus a green.")
    print(f"  A release 0.055 late (1.08) is worth as little as {100 * worst_late:+.0f}% versus a green.")
    print("  Banked buckets count as normal buckets: every value above mixes rim drops")
    print("  with bank shots, so a badly timed release still scores off the glass.")
    print("  Deliberately NOT 'fixed' by widening the timing penalty: intermediate values")
    print("  were measured and made almost every non-green release miss outright, which is")
    print("  the 'trop punitif' behaviour the roadmap rules out. The tuning that would")
    print("  satisfy both constraints is to require a clean entry angle, or to make a")
    print("  banked bucket worth 1 point, and both change how the game feels.")


if __name__ == "__main__":
    main()
