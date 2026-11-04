#!/usr/bin/env python3
"""BosketBall possession-level study: steals, blocks, shot clock, overtime.

The shot model (tools/shot_sim.py) answers "does this shot go in". This script
answers the macroscopic questions instead: how often does a defender actually rip
the ball, how long does a possession last, can a match stall, and does the
overtime cap ever trigger?

Every rate below is transcribed from MatchSession/PhysicsMath so the study fails
when the game drifts away from it.

Usage:  python3 tools/possession_study.py [--games 400]
"""
import argparse
import random
import statistics

STEAL_COOLDOWN_MS = 1150          # settings.physics().stealCooldownMs() default
STEAL_WINDOW_MS = 3000            # stealStreak reset window (MatchSession)
STEAL_BASE = 0.42                 # baseStrip before modifiers
STEAL_CLEAN_SHARE = 0.58          # roll <= baseStrip * 0.58 is a clean steal
STEAL_CAP = 0.42                  # post-fatigue clamp
POSSESSION_CAP_S = 24             # shot clock
GAME_SECONDS = 4 * 90             # 4 quarters x 90s
STEAL_ATTEMPT_PERIOD_MS = 700     # a human mashing the key


def defender_steal_chance(streak):
    """Mirrors MatchSession: 0.60**streak, clamped to [0.04, 0.42]."""
    return max(0.04, min(STEAL_CAP, STEAL_BASE * (0.60 ** streak)))


def simulate_steal_spam(seconds=60, rng=None):
    """How many steals one defender gets while mashing the steal key for N seconds."""
    rng = rng or random.Random(1)
    steals = 0
    attempts = 0
    now = 0
    next_allowed = 0
    last_attempt = -10 ** 9
    streak = 0
    while now < seconds * 1000:
        now += STEAL_ATTEMPT_PERIOD_MS
        if now < next_allowed:
            continue
        if now - last_attempt > STEAL_WINDOW_MS:
            streak = 0
        cooldown = STEAL_COOLDOWN_MS * (1.0 + 0.45 * streak)
        next_allowed = now + cooldown
        last_attempt = now
        streak = min(4, streak + 1)
        attempts += 1
        if rng.random() <= defender_steal_chance(streak):
            steals += 1
    return attempts, steals


def simulate_game(pa, pb, rng, event_rate=1.0):
    """One match: alternating possessions, steals/blocks, shot clock, overtime."""
    score = {pa: 0, pb: 0}
    stats = {"possessions": 0, "steals": 0, "blocks": 0, "violations": 0, "overtimes": 0}
    offense, defense = (pa, pb) if rng.random() < 0.5 else (pb, pa)
    remaining = GAME_SECONDS
    while True:
        shot_clock = POSSESSION_CAP_S
        # Possession: dribble, maybe shoot, maybe get stripped.
        while shot_clock > 0:
            stats["possessions"] += 1
            elapsed = rng.uniform(2.5, 9.0)
            steal_roll = rng.random()
            if steal_roll < 0.11 * event_rate:
                stats["steals"] += 1
                remaining -= min(elapsed, shot_clock)
                offense, defense = defense, offense
                break
            shot_clock -= elapsed
            if shot_clock <= 0:
                stats["violations"] += 1
                remaining -= 0.5
                offense, defense = defense, offense
                break
            remaining -= elapsed
            # Take the shot.
            if rng.random() < 0.12 * event_rate:
                stats["blocks"] += 1
                offense, defense = defense, offense
                break
            pts = offense.shoot(rng.gauss(6.0, 2.4) if rng.random() < 0.35 else rng.uniform(1.8, 7.0),
                                min(0.9, max(0.0, rng.gauss(0.32, 0.22))))
            score[offense] += pts
            if pts == 0:
                # Missed shot: 70% defensive rebound, 30% second chance.
                if rng.random() < 0.30:
                    continue
            offense, defense = defense, offense
            break
        if remaining <= 0:
            if score[pa] != score[pb]:
                break
            stats["overtimes"] += 1
            remaining = 60
            if stats["overtimes"] > 3:
                # MAX_OVERTIMES in MatchSession: settle on box score instead of looping.
                break
    return score[pa], score[pb], stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--games", type=int, default=400)
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()
    rng = random.Random(args.seed)

    print("=" * 78)
    print(" STEAL SPAM — one defender mashing the steal key for 60s")
    print("=" * 78)
    for label, cfg in (("before (flat 42%, 1.15s cd)", None), ("after (hand fatigue)", "f")):
        total_attempts = total_steals = 0
        for i in range(200):
            if cfg is None:
                # Old behaviour: no fatigue, fixed cooldown, flat 0.42 strip.
                attempts = steals = 0
                now = 0
                while now < 60_000:
                    now += STEAL_COOLDOWN_MS
                    attempts += 1
                    if random.Random(i * 31 + now).random() <= 0.42:
                        steals += 1
            else:
                attempts, steals = simulate_steal_spam(60, random.Random(i))
            total_attempts += attempts
            total_steals += steals
        print(f"  {label:28s} attempts/game {total_attempts / 200:5.1f}   steals {total_steals / 200:5.1f}")

    print()
    print("=" * 78)
    print(" MATCH MACRO — possessions, turnovers, overtimes")
    print("=" * 78)

    class P:
        def __init__(self, skill):
            self.skill = skill

        def shoot(self, distance, contest):
            make = max(0.02, min(0.97, 0.92 - 0.045 * distance - 0.35 * contest + 0.10 * (self.skill - 0.7)))
            if random.random() > make:
                return 0
            return 3 if distance >= 7.25 else 2

    for skill in (0.85, 0.65):
        a, b = P(skill), P(skill)
        games = [simulate_game(a, b, rng) for _ in range(args.games)]
        totals = [g[0] + g[1] for g in games]
        ot = sum(g[2]["overtimes"] for g in games)
        viol = sum(g[2]["violations"] for g in games) / args.games
        steals = sum(g[2]["steals"] for g in games) / args.games
        blocks = sum(g[2]["blocks"] for g in games) / args.games
        poss = sum(g[2]["possessions"] for g in games) / args.games
        print(f"\n  skill {skill}: {args.games} games over {GAME_SECONDS // 60} min")
        print(f"    avg points per side     {statistics.mean(totals) / 2:5.1f}")
        print(f"    possessions per game    {poss:5.0f}")
        print(f"    steals / blocks         {steals:4.1f} / {blocks:4.1f}")
        print(f"    shot clock violations   {viol:4.1f}")
        print(f"    overtime periods        {ot}  (matches that needed one: "
              f"{sum(1 for g in games if g[2]['overtimes']):d})")
        assert statistics.mean(totals) / 2 > 12, "matches must not be scoreless"
        assert ot / args.games < 0.25, "overtime must stay exceptional"


if __name__ == "__main__":
    main()
