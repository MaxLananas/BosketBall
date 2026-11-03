# BosketBall — Balance & Bug Study

A headless study of the real game code. Every number below was produced by running the
plugin's own shot model and its own possession/turnover rules over thousands of
situations and hundreds of simulated matches, not by guessing.

Reproduce with:

```bash
python3 tools/match_study.py --matches 250     # per-situation make rates + match sims
python3 tools/possession_study.py --games 400  # steals, blocks, shot clock, overtime
python3 tools/timing_study.py                  # release-timing sweep
```

`tools/shot_sim.py` mirrors `PhysicsMath.solveSkillShotVelocity` + the `BasketballEntity`
integrator (gravity 0.046/tick, drag 0.991, rim torus 0.285+0.042, score radius 0.39).
When the Java shot model changes, the mirror must change with it or these numbers lie.

---

## 1. Shot probability by situation

Columns are contest pressure `c` (0 = wide open, 0.9 = a hand in the face).
`PRO` = 90 % of releases hit the green window, `AVERAGE` = 70 %, `ROOKIE` = 45 %.

### PRO
| distance | open | c0.3 | c0.6 | c0.9 |
|---|---|---|---|---|
| 2.5 m | 99 % | 96 % | 76 % | 55 % |
| 4.0 m | 98 % | 97 % | 73 % | 50 % |
| 6.0 m | 96 % | 97 % | 71 % | 46 % |
| 8.0 m | 97 % | 76 % | 52 % | — |
| 10.0 m | 78 % | 58 % | 39 % | — |
| 12.0 m | 50 % | 36 % | 27 % | — |

### AVERAGE
| distance | open | c0.3 | c0.6 | c0.9 |
|---|---|---|---|---|
| 2.5 m | 93 % | 90 % | 78 % | 57 % |
| 4.0 m | 91 % | 91 % | 72 % | 49 % |
| 6.0 m | 90 % | 88 % | 63 % | 45 % |
| 8.0 m | 88 % | 77 % | 55 % | — |
| 10.0 m | 73 % | 48 % | 43 % | — |
| 12.0 m | 48 % | 39 % | 35 % | — |

### ROOKIE
| distance | open | c0.3 | c0.6 | c0.9 |
|---|---|---|---|---|
| 2.5 m | 93 % | 89 % | 76 % | 62 % |
| 4.0 m | 85 % | 82 % | 73 % | 55 % |
| 6.0 m | 84 % | 77 % | 60 % | 42 % |
| 8.0 m | 85 % | 64 % | 56 % | — |
| 10.0 m | 70 % | 51 % | 40 % | — |
| 12.0 m | 56 % | 42 % | 33 % | — |

**Fix shipped:** a defender standing on the shooter's toes used to cost almost nothing
(a smothered 4 m look still went in 71 %, a 2.5 m look 83 %). The green-release contest
pressure in `PhysicsMath` now scales *up* as the shot gets shorter, where a body really does
block the release: `contest * (0.50 + max(0, 6.0 - dist) * 0.12)`. Close-range contests now
cost 30–45 points of accuracy.

**Open item (needs a design call, not a code change):** release timing is close to worth­less.
`tools/timing_study.py` shows a 0.80 charge (0.045 early) scoring *better* than a green at
6 m and 9 m, and a 1.08 (late) release throwing a **higher** percentage than a green at every
contested distance. Two reasons: the depth offset a mistimed release adds is smaller than the
0.39-block scoring radius, and the backboard sits behind the rim, so a long shot banks in.

Three candidate fixes were measured:

| candidate | result |
|---|---|
| widen the timing→depth penalty | every non-green release misses outright (punitive, rejected) |
| add a large timing→lateral spread | same, and it also killed good releases |
| require a clean entry angle / make a banked bucket worth 1 point | not measured end-to-end; changes game feel |

The first two violate the "playable, not punishing" rule, so nothing was shipped. This is the
single biggest remaining balance lever.

---

## 2. Release-timing sweep (perfect crosshair, n = 200 per cell)

Green window = 0.845 … 1.025. A healthy model is a plateau across the window and a graded
fall-off outside it.

```
contest 0.0
 3.0 m  0.55:  0%  0.65:  0%  0.75:100%  0.80:100%  0.84:100%  0.90:100%  1.02:100%  1.08:100%  1.15:100%  1.20:  0%
 6.0 m  0.55:  0%  0.65:  0%  0.75:  0%  0.80:100%  0.84:100%  0.90:100%  1.02:100%  1.08:100%  1.15:100%  1.20:  0%
 9.0 m  0.55:  0%  0.65:  0%  0.75:100%  0.80:100%  0.84: 92%  0.90: 92%  1.02: 92%  1.08:100%  1.15:  0%  1.20:  0%

contest 0.5
 3.0 m  0.55:  0%  0.65: 40%  0.75: 99%  0.80: 95%  0.84: 78%  0.90: 78%  1.02: 78%  1.08: 96%  1.15: 65%  1.20: 64%
 6.0 m  0.55:  0%  0.65: 10%  0.75: 53%  0.80: 69%  0.84: 76%  0.90: 76%  1.02: 76%  1.08: 90%  1.15: 46%  1.20: 16%
 9.0 m  0.55:  0%  0.65:  2%  0.75: 42%  0.80: 80%  0.84: 46%  0.90: 46%  1.02: 46%  1.08: 76%  1.15: 40%  1.20: 10%
```

The curve is not monotonic and the green plateau is not the best cell — a 0.80 release at
9 m contested scores 80 % against a green's 46 %. The output is close to binary (0 % or
100 % at short range) because the sample is deterministic apart from the release scatter.

---

## 3. Whole-match behaviour (250 matches per profile)

| profile | avg points / side | scoreless | blowouts (±20) | A win rate |
|---|---|---|---|---|
| even, 80 vs 80 | 38.0 | 0 | 1 % | 55 % |
| smurf, 90 vs 45 | 37.1 | 0 | 0 % | **74 %** |
| weak, 60 vs 60 | 36.7 | 0 | 1 % | 52 % |

Scoring pace is stable across skill levels (36–38 pts), which is the signature of the timing
problem above: the *skill* term barely moves the score. A 90-vs-45 smurf game is still a 74 %
win, so matchmaking does separate players, but the margin is much smaller than the skill gap.

No scoreless match and no runaway blowout was ever observed.

---

## 4. Steals, blocks, shot clock, overtime (400 games per skill)

**Steal spam was the biggest gameplay break found.** Mashing the steal key was strictly
correct: a flat 42 % strip chance on a 1.15 s cooldown is ~22 steals per minute of defence,
and real box scores showed 15 steals in a 3-minute game.

| model | attempts / 60 s | steals / 60 s |
|---|---|---|
| before (flat 42 %, 1.15 s cooldown) | 53 | **22.4** |
| after (hand fatigue: 0.60ⁿ, cooldown ×1.45ⁿ, streak resets after 3 s) | 25 | **3.0** |

Match macro after the fix (6-minute games, 64 possessions per game):

| skill | avg pts / side | steals | blocks | shot-clock violations | games needing OT |
|---|---|---|---|---|---|
| 0.85 | 31.1 | 7.0 | 6.7 | 0.0 | 5 % |
| 0.65 | 29.9 | 6.8 | 6.9 | 0.0 | 4.5 % |

Shot-clock violations essentially never happen because a possession always resolves before
24 s; overtime stays exceptional (< 25 % of games), and the new 3-overtime cap guarantees a
stalemate always terminates.

---

## 5. Bugs found and fixed by this study

| # | bug | evidence | fix |
|---|---|---|---|
| 1 | Quest/achievement progress was paid **twice** per bucket: once on the basket, once in the end-of-match box-score sweep | `MatchSession.onBasketScored` recorded `POINTS_SCORED`, `DUNKS`, `THREE_POINTERS`, then the ceremony replayed the whole box score | removed the per-bucket records; the ceremony is the single source |
| 2 | Steal spam (22 steals/minute) | box scores + `possession_study.py` | hand-fatigue ramp + cooldown growth + 0.42 strip cap |
| 3 | Defence did nothing at close range | 71 % on a smothered 4 m look | contest pressure scaled by proximity |
| 4 | Made buckets could disagree with the shot meter (2 vs 3) | `threePointer` flag came from the ball, not from the shooter's feet | `resolveShotValue()` re-derives 2/3 from `CourtGeometry.isThreePointShot` and refuses 3 on a dunk |
| 5 | Tied matches could run unlimited overtimes | `handlePeriodEndBuzzer` looped with no cap | 3-overtime cap, then decided on aggregate box score |
| 6 | The practice out-of-bounds fallback handed the ball to any nearby player, including non-participants and tutorial guests | free-ball branch used `onlineParticipants().first()` | restricted to non-spectator participants |
| 7 | Steals were not counted toward quests in the on-ball path | only the interception path recorded `STEALS` | both paths record now |

---

## 6. AFK & TAB (reported by the player)

* **AFK never fired.** `SocialService.setStatus()` called `touch()`, and the presence tick calls
  `setStatus()` once a second for every online player — so the idle clock was reset 20×/second
  and no player could ever be idle. Nothing called `touch()` from player input either.
  Fixed with `AfkListener` (movement/click/chat/command), a `/afk` toggle, no decay for
  match/queue/spectate/locker/tutorial states, and idle players being pulled out of the queue.
* **TAB rows were too long.** The guild tag is no longer appended to the row; it stays in the
  hover card (rank pill + top-3 badge + custom tag + name + ELO + presence already overflow
  a TAB column). A JUnit test guards the row from re-growing the guild tag.

---

## 7. Remaining backlog

1. Release timing worth something without becoming punitive (design call, see §1).
2. Banked buckets counting as full buckets is a large part of why late releases score.
3. Rookie greens score like pro greens at open range — the skill term needs a wider spread.
4. Tournament-hall building (unchanged backlog item).
