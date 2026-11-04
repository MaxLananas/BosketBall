#!/usr/bin/env python3
"""BosketBall match-balance study.

Runs hundreds of fast, headless possessions through the REAL shot model
(PhysicsMath.solveSkillShotVelocity + BasketballEntity's integrator, mirrored in
tools/shot_sim.py) and reports win rates, scoring pace and every degenerate case
the user reported: matches that never end, a side that cannot lose, scores that
explode, contests that do nothing.

Usage:  python3 tools/match_study.py [--matches 300]
"""
import argparse
import importlib.util
import math
import random
import statistics
import sys

SIM = "/home/user/BosketBall/tools/shot_sim.py"

spec = importlib.util.spec_from_file_location("shot_sim", SIM)
shot = importlib.util.module_from_spec(spec)
# shot_sim prints a demo table when executed; we only want its functions.
src = open(SIM).read().split("rng=random.Random(7)")[0]
ns = {"__name__": "shot_sim_study"}
exec(compile(src, SIM, "exec"), ns)

RIM = ns["rim"]
FWD = ns["fwd"]
FLOOR = 64.0


def make_shot(distance, contest, charge, quality_green, rng):
    eye = (0.5, 65.62, RIM[2] - distance)
    dx, dz = RIM[0] - eye[0], RIM[2] - eye[2]
    d = math.hypot(dx, dz)
    pitch_deg = -9.5 - min(34.0 - 9.5, d * 1.35)
    p = -math.radians(pitch_deg)
    look = (dx / d * math.cos(p), math.sin(p), dz / d * math.cos(p))
    return ns["solve"](eye, look, RIM, quality_green, charge, contest, rng)


def make_rate(distance, contest, charge=0.95, green=True, quality=None, n=120, rng=None):
    rng = rng or random.Random(11)
    made = 0
    for _ in range(n):
        v, _ok = make_shot(distance, contest, charge, green, rng)
        if ns["simulate"]((0.5, 65.62, RIM[2] - distance), v, RIM, FWD, FLOOR) == "SCORE":
            made += 1
    return made / n


class Player:
    def __init__(self, name, skill, rng):
        self.name = name
        self.skill = skill          # 0..1: quality of the release
        self.rng = rng
        self.points = 0
        self.attempts = 0
        self.makes = 0
        self.threes = 0
        self.steals = 0
        self.rebounds = 0

    def shoot(self, distance, contest):
        """One shot: returns points scored."""
        self.attempts += 1
        # Skill decides whether the release is in the window; bad releases are graded out.
        green = self.rng.random() < self.skill
        if green:
            charge = 0.95
            quality_green = True
        else:
            charge = self.rng.uniform(0.55, 1.25)
            quality_green = False
        v, ok = make_shot(distance, contest, charge, quality_green, self.rng)
        result = ns["simulate"]((0.5, 65.62, RIM[2] - distance), v, RIM, FWD, FLOOR)
        if result != "SCORE":
            return 0
        self.makes += 1
        pts = 3 if distance >= 7.25 else 2
        if pts == 3:
            self.threes += 1
        self.points += pts
        return pts


def simulate_match(p_a, p_b, rng, quarter_seconds=90, quarters=4, shot_clock=24):
    """Fast match: both sides alternate possessions for the configured number of seconds."""
    total_seconds = quarter_seconds * quarters
    # Possession length: how long a possession actually takes with a shot clock.
    poss_len = rng.uniform(6.0, 11.0)
    possessions = int(total_seconds / poss_len) // 2
    for _ in range(possessions):
        for shooter, defender in ((p_a, p_b), (p_b, p_a)):
            distance = rng.gauss(6.2, 2.4)
            distance = max(1.8, min(13.5, distance))
            contest = min(0.9, max(0.0, rng.gauss(0.32, 0.22)))
            pts = shooter.shoot(distance, contest)
            if pts == 0 and rng.random() < 0.30:
                # Offensive rebound battle decides who gets the second chance.
                if rng.random() < 0.5:
                    shooter.rebounds += 1
                else:
                    defender.rebounds += 1
    return p_a.points, p_b.points


def study_possessions(skill, rng):
    """Per-distance and per-contest make rates for a given skill level."""
    rows = []
    for dist in (2.5, 4.0, 6.0, 8.0, 10.0, 12.0):
        for contest in (0.0, 0.3, 0.6, 0.9):
            if dist >= 7.25 and contest > 0.6:
                continue
            player = Player("probe", skill, rng)
            n = 300
            made = sum(1 for _ in range(n) if player.shoot(dist, contest) > 0)
            rows.append((dist, contest, made / n))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--matches", type=int, default=300)
    ap.add_argument("--seed", type=int, default=20261103)
    args = ap.parse_args()
    rng = random.Random(args.seed)

    print("=" * 78)
    print(" BOSKETBALL MATCH STUDY — shot model probability per situation")
    print("=" * 78)
    for skill, label in ((0.90, "PRO (90% green releases)"), (0.70, "AVERAGE (70%)"), (0.45, "ROOKIE (45%)")):
        rows = study_possessions(skill, rng)
        print(f"\n{label}")
        print("  distance |  open | c0.3  | c0.6  | c0.9")
        by_dist = {}
        for dist, contest, rate in rows:
            by_dist.setdefault(dist, {})[contest] = rate
        for dist in sorted(by_dist):
            cells = []
            for c in (0.0, 0.3, 0.6, 0.9):
                r = by_dist[dist].get(c)
                cells.append("  -  " if r is None else f"{100 * r:4.0f}%")
            print(f"  {dist:5.1f} m  | " + " | ".join(cells))

    print()
    print("=" * 78)
    print(" MATCH SIMULATIONS")
    print("=" * 78)
    for skill_a, skill_b, label in (
        (0.80, 0.80, "even matchup (80 vs 80)"),
        (0.90, 0.45, "smurf game (90 vs 45)"),
        (0.60, 0.60, "two weak players (60 vs 60)"),
    ):
        a_wins = 0
        scores = []
        blowouts = 0
        zeros = 0
        for _ in range(args.matches):
            pa = Player("A", skill_a, random.Random(rng.random()))
            pb = Player("B", skill_b, random.Random(rng.random()))
            sa, sb = simulate_match(pa, pb, rng)
            scores.append((sa, sb))
            if sa >= sb:
                a_wins += 1
            if sa + sb == 0:
                zeros += 1
            if abs(sa - sb) >= 20:
                blowouts += 1
        totals = [s for pair in scores for s in pair]
        print(f"\n{label} — {args.matches} matches")
        print(f"  avg total score      {statistics.mean(totals):5.1f} pts per side")
        print(f"  median               {statistics.median(totals):5.1f}")
        print(f"  scoreless matches    {zeros}")
        print(f"  blowouts (>=20 pts)  {blowouts}  ({100 * blowouts / args.matches:.0f}%)")
        print(f"  A win rate           {100 * a_wins / args.matches:5.1f}%")
        max_score = max(totals)
        print(f"  highest single side  {max_score} pts")

    # Degenerate-case probes: things a match engine must never do.
    print()
    print("=" * 78)
    print(" DEGENERATE CASES")
    print("=" * 78)
    pawn = Player("pawn", 0.80, random.Random(5))
    zero_contest = sum(1 for _ in range(200) if pawn.shoot(2.0, 0.0) > 0) / 200
    print(f"  unmissable layup at 2m, wide open : {100 * zero_contest:5.0f}%  (expect >95%)")
    deep = Player("deep", 0.90, random.Random(6))
    halfcourt = sum(1 for _ in range(200) if deep.shoot(13.0, 0.4) > 0) / 200
    print(f"  logo three at 13m, contested      : {100 * halfcourt:5.0f}%  (expect <35%)")
    frozen = Player("frozen", 0.80, random.Random(7))
    smothered = sum(1 for _ in range(200) if frozen.shoot(6.0, 0.95) > 0) / 200
    print(f"  midrange with a hand in the face  : {100 * smothered:5.0f}%  (expect <45%)")

    green_rate = make_rate(6.0, 0.0)
    print(f"  raw perfect release, 6m, open     : {100 * green_rate:5.0f}%")
    late = make_rate(6.0, 0.0, charge=1.20, green=False)
    print(f"  raw late release, 6m, open        : {100 * late:5.0f}%  (must be punished)")


if __name__ == "__main__":
    sys.exit(main())
