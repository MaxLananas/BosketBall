# Hook — Match Director (Agent 2)

**Status: published.** The phase model, the tension curve and the ceremony beats are live in
`MatchSession`. Nothing below is a request for someone else to change their file; it is the
contract this agent now offers, and the two places where another agent's work can plug in.

---

## 1. What exists now (consume this instead of the `State` enum)

`fr.bosketball.game.MatchPhase` — the match as a directed sequence. Every phase declares a
`Surface` (what is on screen), a `Bed` (what it sounds like), a `holdTicks` and its legal
successors. `State` still answers "is this session finished", which is all the queues and court
pools need. **If you want to know what the player is watching, ask `session.phase()`.**

```java
MatchPhase phase = session.phase();                 // null before the first tip-off
boolean tense   = session.clutchTime();             // last two minutes, one possession
String  best    = session.bestPlayLine();           // "Kobe · three at the horn, and the lead with it"
```

`fr.bosketball.game.MatchDirector` — the ledger. `session.director().steps()` is the whole match
as a list of timestamped transitions, and `deadAir(now)` returns the transitions that were entered
with nothing on screen for longer than `MatchPhase.DEAD_AIR_BUDGET_MS` (750 ms). If you add a
subsystem that puts something on screen on a delay, this is the object that will tell you it was
late.

**The rule that will bite you:** `MatchSession` writes `state` in exactly one place
(`enterState`), and that method always emits a cue synchronously. If you need a new state, you add
a `MatchPhase` row and a case in `cuePhase` — you do not assign `state` from your own file. The
suite fails on a third assignment.

## 2. If you own the HUD (Agent 4)

The scorebug is the surface for `WAITING`, `LIVE` and `CLUTCH`, and it must stay *identical* in
all three: the reader is dribbling. The director's tension curve is deliberately carried elsewhere:

- the jumbotron status reads `CLUTCH` instead of the quarter label (`arena.updateJumbotron`),
- the gold `▪ CLUTCH` line in chat carries the margin and the clock,
- the inbound rail names the time left instead of the bare "inbound it".

**Offer:** if you want a scorebug variant, it must fit the existing 228 px block and add no bar.
Send me the exact signature in `docs/agents/integration/hud.md` and I will wire it with a
three-line call site + a comment naming you. Do not edit `MatchSession`.

## 3. If you own the sound bed (Agent 6 / pack)

The director plays exactly these keys, all already defined by the pack:

| When | Key | Volume / pitch |
|---|---|---|
| Tip-off horn, per player | `game.whistle` | 0.85 / 1.12 |
| Tip-off horn, arena | `game.whistle` | 1.20 / 1.00 |
| Period break | `game.whistle` | 1.05 / 1.02 |
| Momentum timeout | `game.whistle` | 1.00 / 1.06 |
| Clutch opens (once per match) | `crowd.cheer` | 1.05 / **1.14** |
| Back in play, per player | `game.whistle` | 0.55 / 1.00–1.18 |
| Ceremony outro | `game.whistle` | 0.70 / 0.92 |
| Hand-off to lobby | `ui.confirm` | 0.60 / 1.05 |

The only key whose meaning changed is `crowd.cheer` at pitch **1.14**: it is the clutch bed. If
the pack ever ships a dedicated `crowd.tight`, say so in `docs/agents/integration/pack.md` and it
is a one-line swap in `cueClutchBed()`.

## 4. If you own the ceremony screens (Agent 3 / 8)

The ceremony is four fixed beats and never starts twice (a flag **and** the ledger guard, both
tested). If you want a screen in the outro, it goes between `CEREMONY` entry and the `FINAL`
hand-off (`CEREMONY_OUTRO_AT_TICKS`), and it must not be a new inventory window — the players are
in spectator camera at that point.

## 5. What I will not do

- No new config keys. `BosketConfig` is not mine, and an unparsed key is an unused key.
- No new palette token, no new boss bar, no second title during a quarter.
- No change to `formattedClock()`: the tab header budgets team-name width from that string.
