# Match direction — what a broadcast does that a match loop does not

Research note for the match-director pass. Everything below either became a rule in code
(`MatchPhase`, `MatchDirector`, `MatchSession`) or was deliberately rejected with a reason.

---

## 1. The run of a quarter

A broadcast quarter is not 90 seconds of continuous play with a number in the corner. It is a
sequence of *units*, and each unit has its own voice:

1. **The horn and the tip.** The arena is told what it is about to watch before it watches it.
   The scorebug appears *before* the first possession, empty, so the viewer's eye has already
   found where the score will be.
2. **Play.** The clock is a background instrument. A broadcaster does not read it out while the
   ball is live except at the ends: under five, under two, under one.
3. **A stoppage.** A whistle produces an *address*, not silence: who has the ball, why it
   stopped, what happens next. This is where the scoreboard becomes the subject.
4. **The last two minutes of the half.** Only then does the clock become the narrator.

The single most repeated complaint about real NBA endings is that the last two minutes of clock
take nine minutes of wall time
([Chicago Tribune](https://www.chicagotribune.com/2015/05/13/end-of-an-nba-game-dullest-2-minutes-in-sports/), via Inpredictable data).
The league has fought this directly — fewer timeouts, a fixed 15-minute halftime, delay-of-game
penalties for a team that is not ready when intermission ends — explicitly to protect "pace and
flow" ([Fox Sports](https://www.foxsports.com/stories/nba/nba-eliminates-timeouts-to-keep-the-ends-of-games-moving)).

**Rule taken.** A stoppage is a *beat with a budget*, not a gap. Every stop in BosketBall has a
declared length, a surface and an exit, and none of them may take the clock hostage the way a
televised timeout does: our dead ball is 0.75 s, our quarter break is 3 s, our auto-timeout is 2 s.

## 2. When a timeout is called

Coaches do not call timeout to rest. The dominant trigger is a scoring run: the standard threshold
in the literature is **six unanswered points** ("the opponent is on a 6-0 run"). The measured
effect is real but modest — a timeout during a run reduces the following scoring by roughly 6.5 %
with no substitutions, more with them
([Weimer, Steinert-Threlkeld & Coltin, *J. Sports Analytics*, 2023](https://journals.sagepub.com/doi/10.3233/JSA-220592)).
Coaches' timeouts beat TV timeouts as momentum stoppers because the stoppage *and* the tactical
change land together.

**Rule taken.** The director calls the timeout itself, at six unanswered points, once per quarter,
for 2 s. It is the cheapest drama in the game: the run is the story, and the whistle is the
paragraph break. Its copy is not "Timeout called by the server" — it is one line for the team that
is on the run and a different line for the team that is bleeding.

## 3. "Buzzer beater" is not "routine make"

Broadcast vocabulary separates two things that a scoreboard renders identically:

- a **buzzer-beater** is defined by *timing* — the ball leaves the hand before the horn;
- a **game-winner** is defined by *consequence* — it produces the final lead.

A shot at the horn that ties the game is a buzzer-beater and not a game-winner
([Fiveable, *Buzzer-Beater in Sports Reporting*](https://fiveable.me/sports-reporting-and-production/key-terms/buzzer-beater)).
Announcers mark the difference by *dropping the incidental detail*: a routine make gets the
caller's full sentence ("Davis drives, pulls up, good"), a horn-beater gets three words and the
crowd.

**Rule taken.** Moments are graded, not narrated equally. `MatchPhase` carries a leverage score,
and the ceremony card names the one play whose leverage was highest — a basket that takes the
lead inside the last two minutes outranks a longer, prettier basket in the second quarter. The
routine make keeps its one line in chat and gives the screen back.

## 4. How a good Minecraft server ends a session

Two patterns, observed across Hypixel and Cytooxien:

- **The end is announced before it arrives.** Hypixel's own players ask for exactly this: show
  *who is going to win and when*, `BLUE Wins in 3:00`, rather than a bare `Game End in 3:00`
  ([Hypixel forums](https://hypixel.net/threads/important-fix-the-game-end-logic.5984093/)).
  Cytooxien's round games likewise run the scoreboard as the story — the ranking is live during
  the round, and the reveal is the reward
  ([Cytooxien, Random Item Battle](https://www.cytooxien.net/en/random-item-battle-release)).
- **The end is a fixed ceremony, not a chat message.** A "standard YOU WIN screen" is a promise:
  the same three beats every time — result, name, exit — so a player learns the rhythm once and
  can screenshot any match and have it read the same way
  ([Hypixel minigame design thread](https://hypixel.net/threads/megathread-minigame-suggestion-disasters.4784653/page-5)).

**Rule taken.** The ceremony is four beats with fixed lengths: result card, MVP, play of the
match, outro camera — then the hand-off to the lobby. Same order, every match, no exceptions.

## 5. The dead-air rule

The two-second rule as applied to games: after a decisive event the player must be looking at
*something that means something* within about two seconds; a blank screen (or a frozen world with
no words) reads as a crash. Our codebase is stricter than the rule that produced it.

**Rule taken (750 ms).** Every phase transition in `MatchSession` must produce a player-facing
consequence within 750 ms — one world tick later is already too late, so the cue is emitted
*synchronously, inside* the transition call. This is enforced structurally: there is exactly one
assignment to `state` in `MatchSession`, inside `enterState(...)`, and that method always asks
`MatchDirector` for a cue and always applies it. A new `state =` anywhere else fails the suite.

## 6. What was deliberately not done

| Considered | Rejected because |
|---|---|
| A visible countdown of **timeouts remaining** | There is no player-callable timeout; showing a resource nobody can spend is chrome. |
| A new bossbar for the clutch period | The scorebug already owns that bar. A second bar is a second thing to read while dribbling. |
| Redefining `formattedClock()` to say `CLUTCH 1:32` | The tab header budgets name width from the clock string; a longer clock silently eats a team name. The clutch voice is the rail and the bed instead. |
| New config keys (`match.clutch-seconds`, ...) | An unparsed key is exactly the "unused config key" the Definition of Done forbids, and the settings loader is outside this agent's files. The constants live in `MatchSession` and are pinned by tests instead. |
| Freezing play for realistic 60-second timeouts | The measured cost of real stoppages is the reason the NBA changed its rules. Two seconds buys the beat; sixty would buy a complaint. |
| A separate `TIMEOUT` match state that stops the clock | It would reproduce the real stoppage the NBA is legislating away. The timeout is a 2 s *beat*: the run resets, both benches are addressed, play never stops. |
| A `FINAL_POSSESSION` gate that scripts the last possession | A basketball ending is decided by the shot, not by a choreography. The phase exists in the document and the ledger accepts the edge; nothing forces it, because forcing it would take the last shot out of the player's hands. |
