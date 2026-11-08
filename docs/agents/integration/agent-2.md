# Agent 8 (Economy, Progression & Season) → Agent 2 (Game & Match)

**Status: CHANGES ARE ALREADY APPLIED IN THIS BRANCH.** This file is the
disclosure the Constitution asks for, plus the exact call sites so the
conductor can confirm or re-apply them during the merge.

`MatchSession.java` is Agent 2's file and I did not edit it out of
ignorance — I edited it because the economy cannot be correct without it, and
because four of the five changes are *one-line substitutions at a call site*
rather than new machinery in the middle of the game loop. Every one of them
is listed below with its exact signature, its exact call site, its reason, and
how to revert it independently if the conductor disagrees.

If the conductor prefers to land these as Agent 2's commit instead of mine,
each change below is self-contained: revert the hunk, re-apply it under
Agent 2's name, and the build stays green. The economy tests do not depend on
`MatchSession` directly — they depend on `EconomyBalance`, `RateCard` and
`RankProgress`, all of which are in my package.

---

## 1. The bot-match multiplier — `MatchSession.java`

### The call site

`MatchSession.settleMatch()` (the settlement method, around line 2955). The
existing line:

```java
double rewardScale = ranked ? 1.0 : Math.max(0.0, econ.casualRewardMultiplier());
```

becomes:

```java
double rewardScale = fr.bosketball.economy.EconomyBalance.matchMultiplier(ranked, hadBots);
```

with one line inserted immediately above it:

```java
boolean hadBots = hasBots();
```

### The exact signature

```java
public static double matchMultiplier(boolean ranked, boolean hadBots)
```

in `fr.bosketball.economy.EconomyBalance`.

### Why

`matchmaking.fill-bots-on-launch: true` is the shipped default, and the
matchmaking window grows to 600 ms. A player who queues ranked at 03:00
against one CPU gets the **full ranked reward**: 40 coins for the win, 15 for
the MVP, the entire on-court economy, and **+15 ELO**. There is no opponent,
so there is no risk, and there is no skill, so there is no reason not to. It
was the cleanest farm on the server.

`hasBots()` already exists on `MatchSession` (around line 3513) and the bots
are still alive at settlement time — they are cleared in `clearBots()` around
line 3538, after the ceremony — so the check is accurate at the moment it is
made. No new state was needed.

### The bot-match ELO rule

Immediately after the existing ELO swing is computed:

```java
if (swingDelta == 0 && rawDelta != 0) {
    swingDelta = rawDelta > 0 ? 1 : -1;
}
```

add:

```java
if (hadBots) {
    swingDelta = 0;
}
```

A bot match moves **no** rating at all. Not a smaller swing — zero. A ladder
that a player can climb by playing the CPU is not a ladder.

### The multiplier's value

`economy.bot-match-multiplier: 0.50` in `config.yml`. It is a live knob:
`EconomyBalance.setBotMatchMultiplier(...)` is called from
`BosketBallPlugin.reloadBosketConfig()`, and the value is clamped to
`[0.0, 1.0]`.

---

## 2. The loss now pays — `MatchSession.java`

### The call site

`MatchSession.settleMatch()`, the coin-payout block (around line 3014). The
existing line:

```java
} else if (!drawn) {
    int lost = prof.removeCoins((int) Math.round(lossCoins * rewardScale));
    myBox.coinsDelta -= lost;
}
```

becomes:

```java
} else {
    if (scaledLossCoins > 0) {
        prof.addEarnedCoins(scaledLossCoins);
        myBox.coinsDelta += scaledLossCoins;
    }
}
```

with `int scaledLossCoins = (int) Math.round(lossCoins * rewardScale);` added
next to the existing `scaledWinCoins` and `scaledMvpCoins`.

### The exact signature

No new signature — this uses the existing
`PlayerProfile.addEarnedCoins(int)` and `PlayerProfile.removeCoins(int)`.

### Why

`economy.loss-coins` was `0`. With a zero, `removeCoins(0)` removed nothing,
so **a loss paid nothing at all** and the server only ever told the player
who succeeded. The brief is explicit: *a loss must always pay something*.

The floor is now 18 coins against a 40-coin win — a 2.22:1 ratio. Enough
that a bad game is a bad game, not a punishment; not enough that throwing is
rational. The symmetric proof is in `EconomyBalance.auditSymmetry()` and the
test `testTheResultEconomyIsSymmetricAndALossAlwaysPays`.

### Why the sign flipped rather than the value

The old code *subtracted*. It was written as a penalty, not a reward. The
value `0` made the subtraction invisible. Rather than change `0` to `18` and
leave the sign wrong, the direction was corrected: the loser is **paid**
their floor. `removeCoins` is now unused in this method — grep confirms the
only remaining uses in the codebase are the admin command, the forfeit path,
and guild creation, which are genuine spends.

---

## 3. The draw branch — `MatchSession.java`

### The call site

`MatchSession.settleMatch()`, the draw branch of the payout block:

```java
if (drawn) {
    rewardScale = 0.0;
}
```

becomes:

```java
if (drawn) {
    scaledWinCoins = 0;
    scaledMvpCoins = 0;
    scaledLossCoins = 0;
}
```

### Why

The old line was **dead code**. The three scaled amounts were computed
*above* it, so zeroing `rewardScale` afterwards changed nothing — the draw
paid the win bonus to nobody and the loss penalty from nobody, but the code
claimed it was scaling. The three amounts are now zeroed directly, which is
what the comment always claimed the branch did.

This is a no-op on behaviour and a fix on intent. It is in this list only
because it sits in the same hunk.

---

## 4. The rank signal on the final-score card — `MatchSession.java`

### The call site

`MatchSession`, the final-score card (around line 3187). The existing line:

```java
Msg.sendFramedCard(p, "Final Score · " + mode.id().toUpperCase(java.util.Locale.ROOT), List.of(
    ...
));
```

becomes:

```java
java.util.List<String> finalCardLines = new java.util.ArrayList<>(List.of(
    ...
));
if (pSide != TeamSide.SPECTATOR) {
    String rankSignal = fr.bosketball.stats.RankProgress.signal(
        statsManager.getOrCreate(p).elo());
    if (!rankSignal.isEmpty()) {
        finalCardLines.add(rankSignal);
    }
}
Msg.sendFramedCard(p, "Final Score · " + mode.id().toUpperCase(java.util.Locale.ROOT),
    finalCardLines);
```

### The exact signature

```java
public static String signal(int elo)
```

in `fr.bosketball.stats.RankProgress`. It returns `""` when there is no
signal to give (a player at or above the top rank, or one more than ten
games away), so the card is unchanged for those players.

### Why

The server celebrates a rank-up loudly and never says how close the *next*
one is. A player at 1035 ELO has no way to tell whether the promotion is one
game away or fifty.

The signal rides the **final-score card as one extra line** — no second
card, no second title, no action-bar line. The Constitution says: *never
invent a share of the screen you cannot take back*. The card is already the
moment, so the signal joins it.

Copy: `2 more games to Hooper (at best)`. The "at best" is the honesty
clause — at +15 ELO per win, two games is the *floor*, not the promise. A
player who wins two in a row gets it; a player who goes 1-1 does not. The
research behind the wording is in `docs/agents/research/economy.md` §3.9.

The rate is a flat `ELO_PER_WIN = 15`, matching the existing `+15 / −5`
ladder. No K-factor was introduced — see §1.3 of the research document for
why the flat swing stays.

### Note on the private-use glyph

The MVP line in this card contains a real `\ue200` Private-Use character and
`\u00b7` escapes. **Do not attempt a naive string replace on this block** —
it will not match. This is why the edit was made by line index. If the
conductor re-applies it, use the same approach.

---

## 5. What I did NOT change in `MatchSession.java`

For the avoidance of doubt, and because this file is 3,900 lines long:

- The physics, the shot clock, the possession model, the fouls, the
  substitutions, the rules — untouched.
- The ELO numbers themselves (`+15 / −5`, the `[100, 99_999]` clamp) — the
  Round 26/27 decision, Agent 2's and the community's. I did not add a
  K-factor ladder. The only ELO change is that a **bot** match now moves zero
  instead of +15.
- The forfeit path — unchanged (it already paid the stayer 15 and took 15
  from the quitter).
- The three-point contest — unchanged.
- The GG reward — unchanged.
- The `statsManager` reference used by the rank signal already existed in
  scope; no new field was introduced.
- No new import was added to `MatchSession.java`. Every new class is
  referenced by its fully-qualified name, so this file's import block is
  byte-identical to before.

---

## 6. The risk to Agent 2's system

| Risk | Likelihood | Mitigation |
|---|---|---|
| The loss payment changes what a player expects to lose | **Certain** | It is the point. `economy.loss-coins` was `0`; no player was losing anything before, because nothing was being taken. |
| `hasBots()` is `false` at settlement time for a bot match | Low | Verified against the source: `clearBots()` runs after the ceremony, settlement runs before it. The test `testABotMatchPaysDifferentlyFromAHumanMatch` covers the multiplier; the wiring is a one-line read of existing state. |
| The rank signal collides with another subsystem's message | **None by construction** | It is a *line inside* the final-score card, not a separate message. It cannot be overwritten, because it is not a separate send. |
| `finalCardLines` changes the card's shape for spectators | None | The signal is skipped for `TeamSide.SPECTATOR`. |
| The draw branch change alters a payout | **None** | The old line was dead. The new code does what the old comment claimed. |
| `hasBots()` is called once per match, not once per player | None | It is hoisted above the participant loop. |

---

## 7. How to revert each change independently

```bash
# 1. Bot multiplier (two hunks: the hadBots line, the rewardScale line, the ELO zeroing)
git checkout d44e011 -- src/main/java/fr/bosketball/game/MatchSession.java   # reverts everything

# or, by hunk only:
git diff src/main/java/fr/bosketball/game/MatchSession.java | git apply -R --include='*'
```

The economy package (`RateCard`, `EconomyBalance`), the season package and
the progression package compile and pass their tests **without** the
`MatchSession` changes. The only test that touches `MatchSession` is the
invariants suite in `BosketBallEngineTest`, which was retargeted only for
`loss-coins` (from `== 0` to `> 0 && < win-coins`) and the season anchor
(from `1200` to `1000`) — both are assertions about the *intended* new
behaviour, and both are listed in the PR body.

---

## 8. What Agent 2 gets out of this

- The rank signal is implemented and waiting. It is Agent 2's card, and
  Agent 2 may want to place it differently (above the rewards line rather
  than below it), to rename the ranks, or to drop it for casual matches.
  `RankProgress.signal(int elo)` is a pure function — call it with whatever
  ELO the player has and put the string wherever it reads best.
- The settlement block now has a single `rewardScale` source of truth
  (`EconomyBalance.matchMultiplier`) rather than an inline ternary, so
  adding a fourth match type later is a one-line change in my package.
- The loss payment is a **new** line of code in the payout block. If Agent 2
  prefers the loss to be paid at a different point (for example, before the
  ceremony rather than during settlement), that is a move, not a rewrite.
