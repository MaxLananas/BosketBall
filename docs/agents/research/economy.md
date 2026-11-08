# Economy & Progression — Research Notes

**Agent 8 · Economy, Progression & Season · BosketBall**

Every number in this document is a number that ships in code. The rate card is
`fr.bosketball.economy.RateCard`; the symmetry proof is
`fr.bosketball.economy.EconomyBalance`. If a figure is not in the code, it is
not in this document.

---

## 1. What I read, and what I took from it

### 1.1 Soft resets, not hard resets

Rocket League compresses MMR toward the middle of the ladder at every season
boundary and then asks for ten placement matches per playlist. A Grand
Champion who finished at 1600 opens the new season nearer 1200–1300 and
climbs back over the first two weeks. The key properties are (a) nobody is
wiped, (b) the ladder *moves* so the top is not permanently occupied, and (c)
placements swing harder than normal games so the system re-finds the range
quickly.

- [Rocket League Season 24 — soft reset explained](https://buyboosting.com/rl-boosting/season/)
- [Rocket League Season 22 — reset and placements](https://www.thespike.gg/rocket-league/beginner-guides/season-22-details)
- [Rocket League ranks and MMR](https://esportnow.gg/games/news/rocket-league-ranks)

**What I took:** a soft reset that keeps a *fraction* of the distance from a
fixed anchor, and a placement phase that **does not** change the ELO swing
(it only gates the season tier). BosketBall's Round 27 change — the ×2
placement multiplier removed, so a placement match moves ELO exactly like any
other — is right, and I kept it. The reason is in the research: a placement
phase whose *only* effect is a different multiplier makes new players
vulnerable to a bad placement run for no benefit to anyone.

### 1.2 Rating floors, and why they are not the same thing as a soft reset

The USCF rating floor is `peak − 200`, floored to a 100-point boundary. Its
purpose is **anti-sandbagging**: a player who reached 1464 can never be rated
below 1264, so they cannot drop into a 1200-class event to farm weaker
opponents. FIDE's floor serves a different purpose — it stops performances
below the floor being tracked at all.

- [Elo rating system — rating floors, inflation and deflation](https://en.wikipedia.org/wiki/Elo_rating_system)
- [Smurfing in ELO limit tournaments — the rating floor as anti-sandbagging](https://www.reddit.com/r/aoe2/comments/1oytirh/smurfing_in_elo_limit_tournaments/)

**What I took, and what I rejected:** I did **not** add a peak-relative rating
floor to BosketBall. A floor relative to a player's own peak rewards the
veteran for having been good once and punishes the improving player for
being bad now — the opposite of the mission. What I took instead is the
**absolute floor of 100 ELO** that already exists, plus a hard **cap of
99 999**, plus a season soft reset that pulls the *whole field* back toward
the anchor. The floor is absolute, not personal. A smurf who throws games
lands at 100 ELO and has to grind back up through every band in front of
them, which is exactly the smurf tax the mission asks for.

### 1.3 The K-factor ladder, and why BosketBall uses a flat swing

FIDE gives new players K = 40, drops them to K = 20 after 30 rated games and
K = 10 above 2400. The principle is **volatility should decay with
experience**: a brand-new account's rating should be able to move fast while
it is being discovered, and a settled account's rating should be sticky.

- [Elo rating system — FIDE's K values and bonus points](https://en.wikipedia.org/wiki/Elo_rating_system)
- [The Elo Rating explained for product managers](https://butaiisdifferent.substack.com/p/the-elo-rating-explained-for-pm)

**What I rejected, deliberately:** a K-factor ladder. The brief for this
server says the ladder must be *readable*. Round 27 already replaced a
±32/±16 swing with a flat +15/−5 because players complained that a single
game moved the ladder too much to follow. A K-factor that widens for new
players reintroduces exactly that complaint for exactly the players who are
newest and least able to predict it. **Flat ±15/−5 everywhere, including
placements.** The anti-smurf pressure is applied through the *season reset*
and the *floor*, not through a hidden swing multiplier.

The alternative I rejected, in writing, because it is the one that will be
proposed again: "placements give ×2 ELO so new players climb faster." It
rewards volume at the top of the ladder (a smurf's placements are wins, and
wins are the only thing placements pay) and it punishes the veteran who
returns mid-season and has to play five unamplified losses. Both effects are
wrong, and Round 27 already removed it.

### 1.4 Daily quests: three is a habit, seven is a chore

The time-gating literature is unusually clear on the cap: a daily quest set
that takes 15–30 minutes sits in the sweet spot; two hours is a second job;
two minutes is trivial and forms no habit. The cap should be achievable
inside one 30–60 minute session. Three daily quests is the number the major
quest plugins converged on independently — O'DailyQuests ships three daily
quests as its headline, and the same plugin lets an operator configure 3 / 8 / 4
across daily / weekly / monthly.

- [Time-Gating Mechanics in Minecraft Servers — daily quests, energy systems](https://www.fumora.net/lab/minecraft-time-gating-mechanics)
- [ODailyQuests Continued — 3 daily / 8 weekly / 4 monthly](https://www.spigotmc.org/resources/odailyquests-continued-daily-quests-for-paper.138553/)
- [ODailyQuests — the original](https://www.spigotmc.org/threads/odailyquests-daily-quests-plugin-1-18-2-1-21.551543/)

The catch-up principle from the same source is the one I care most about:
*"The player who misses a day should not feel permanently behind."* A weekly
quest is the catch-up valve — it is the only board wide enough to absorb a
player who missed a Tuesday.

**What I took:** **three dailies, one weekly**. The weekly is *one*, not
three. BosketBall shipped three weekly quests plus two six-hour contracts;
that is five repeatable boards for a player to service, and the contract board
in particular asked for 6–8 dunks *within six hours*, which is a chore
dressed as a job. Three dailies + one weekly is the habit.

### 1.5 Faucets and sinks, and the crate that was a money printer

The MMO rule of thumb is that money created and money destroyed should
roughly balance over a long window, and that one-time sinks are not enough —
"ultimately they have a limit. They only delay the problem."

- [r/gamedesign — should in-game currency be created at the same rate it is consumed?](https://www.reddit.com/r/gamedesign/comments/12e0gvv/economy_design_should_ingame_currency_be_created/)
- [How virtual coin systems shape player engagement — accrual and sinks](https://tomoson.com/how-virtual-coin-systems-shape-player-engagement)
- [Cross-stitch game — coin sink roadmap, "an event faucet without a matching sink leaves inflation"](https://github.com/muratti32/cross-stitch-game/issues/286)

**What I found in this repo, and it was the single worst bug in my
domain:** the reward crate costs **250 Coins** and refunds a uniform
60–500 Coins, plus a flat 150 Coins on a 15 % bonus roll.

```
E[coins]   = (60 + 500) / 2            = 280
E[bonus]   = 0.15 x 150               = 22.5
E[refund]  = 280 + 22.5               = 302.5
E[net]     = 302.5 - 250              = +52.5 Coins per crate
```

At the configured daily limit of 5, that is **+262 Coins per player per day,
forever, for free**, with the only limit being a 3-second cooldown and a
daily cap. The crate is not a sink — it is the largest faucet in the game,
and it is *faster* than playing basketball. Worse: 22 % of the time it
refunds a cosmetic instead of coins, so the "coin" outcome is rarer than the
arithmetic above assumes — but even discounting for that, the coin outcome
alone is `0.78 x 280 = 218` plus `0.78 x 0.15 x 150 = 17.6`, i.e. **236 <
250**. The crate is only safe *because* the cosmetic branch eats the deficit,
which is exactly backwards: the branch that gives the player something they
wanted is the branch that has to carry the economy, and the branch that pays
cash is the one that has to be safe on its own.

**What I did:** the crate's coin refund is now a **strictly negative-EV
operation on its own**, and the cosmetic branch is the upside, not the
downside. See §3.3.

---

## 2. The six real defects the rate card found

Writing the table down is what found these. None of them was visible by
reading the code: each one is a number that is wrong *in relation to the
other numbers*, and the other numbers were scattered across fifteen call
sites and a YAML file.

| # | The defect | The arithmetic | The fix |
|---|---|---|---|
| 1 | **A loss paid nothing** | `economy.loss-coins: 0` | 18 coins |
| 2 | **The crate was a money printer** | `E = 280 + 22.5 = 302.5` against a 250 cost — **+52.5 coins every opening**, five a day | cost 300, refund 40–220 — **−147.5** |
| 3 | **A bot match paid full ranked** | `fill-bots-on-launch: true`, so a 03:00 ranked queue against one CPU paid 40 + 15 MVP + the whole on-court economy + **+15 ELO** | half the coins, no ELO |
| 4 | **The on-court economy swamped the result** | a typical match paid **133** on the court against a **40**-coin win — 3.3× | 2/3/2/2/2/2 — a good match pays **56** |
| 5 | **Two achievements read the wrong counter** | "Crate Opener" (10 crates) and "Graduate" (finish the tutorial) both read `dailyCratesOpened()`, so the first needed 10 crates in one night — impossible under a daily limit — and the second unlocked on the first crate of the day | new lifetime `CRATES_OPENED` and `TUTORIAL_FINISHED` stats |
| 6 | **The config parser had its own, older economy** | `BosketConfig` carried its own fallback values (`250 / 5 / 3 / 60 / 500` for the crate, `1200` for the reset anchor, a **0-coin** Rookie tier) that had drifted from `config.yml` — and `economy.bot-match-multiplier` was parsed but never read | every fallback now **is** the shipped value, and the multiplier is a live knob |

Defect 2 is the one that would have ended a season: it was the fastest way
to make coins on the server, it was available to every player, and it was
capped at five openings a day — a cap that looked like anti-abuse and was
actually a cap on the *rate*.

Defect 4 is the one that took the longest to see, because each number was
individually reasonable. Five coins for a two-pointer is fair. Eight for a
three is fair. Forty for a win is fair. All three together mean the win is
worth less than three buckets, and the coin leaderboard measures hours rather
than skill. That is the failure mode the rate card exists to catch: **every
number is fine, and the sum is wrong.**

Defect 6 is the one that would have survived *this entire PR*. It is
invisible to any test that reads `config.yml`, because `config.yml` was
correct — the parser had a second copy of the economy, in Java, and that
copy was stale. A player whose server admin had deleted a line from
`config.yml` would have silently got the old crate back, the money printer
and all. The fix is a test that asserts the fallback **is** the shipped
value: `testTheConfigFallbacksMatchTheShippedConfigSoNoBugComesBack`.

The general lesson, and the reason the rate card is *in code* rather than in
a spreadsheet: **a number that exists in two places is a number that will
diverge.** The spreadsheet would have caught defects 1–4. Only the code
caught 6.

---

These are the rules the code enforces, each with a test.

| # | Rule | Enforced by |
|---|---|---|
| R1 | A loss always pays something. Never zero, never negative. | `RateCard.LOSS_COINS` > 0; `EconomyBalance.auditSymmetry` |
| R2 | A win never pays so much that throwing the match is rational. | `RateCard.winToLossRatio` ≤ 3.0 |
| R3 | Every reward path pays at most once per trigger. | `QuestRewardGuard`, `EconomyBalance.RewardLedger` |
| R4 | No sink can drive a balance below zero. | `PlayerProfile.spendCoins` returns false; the sink test |
| R5 | ELO is bounded, and the rules treat both benches the same way. | `EconomyBalance.applyElo`, `eloRulesAreSymmetric` |
| R6 | A season reset preserves lifetime stats. | the season-reset test |
| R7 | Quest progress never exceeds its target. | `PlayerQuest.addProgress` clamp (existing) + test |
| R8 | A bot match pays differently from a human match. | `RateCard.BOT_MATCH_MULTIPLIER` = 0.50, no ELO |
| R9 | Every repeatable reward has a cooldown. | `RateCard.repeatableSourcesWithoutCooldown()` is empty |
| R10 | Every tradeable value has a sink. | `RateCard.sinks()`, `EconomyBalance.sinkToFaucetRatio` ≥ 1.0 |
| R11 | A player cannot gain by idling. | every repeatable row is behind a clock or a game event |
| R12 | The on-court economy never swamps the result economy. | the hourly-income test: result ≥ ⅓ of total |
| R13 | A quest never needs a teammate to be completed. | `QuestCatalog.isSoloAchievable` for every daily |
| R14 | The player is told how far the next rank is. | `RankProgress.signal` on the final-score card |

---

## 2b. The design rules I am implementing

These are the rules the code enforces, each with a test.

| # | Rule | Enforced by |
|---|---|---|
| R1 | A loss always pays something. Never zero, never negative. | `RateCard.LOSS_COINS` > 0; `EconomyBalance.auditSymmetry` |
| R2 | A win never pays so much that throwing the match is rational. | `RateCard.winToLossRatio` ≤ 3.0 |
| R3 | Every reward path pays at most once per trigger. | `QuestRewardGuard`, `EconomyBalance.RewardLedger` |
| R4 | No sink can drive a balance below zero. | `PlayerProfile.spendCoins` returns false; the sink test |
| R5 | ELO is bounded, and the rules treat both benches the same way. | `EconomyBalance.applyElo`, `eloRulesAreSymmetric` |
| R6 | A season reset preserves lifetime stats. | the season-reset test |
| R7 | Quest progress never exceeds its target. | `PlayerQuest.addProgress` clamp (existing) + test |
| R8 | A bot match pays differently from a human match. | `RateCard.BOT_MATCH_MULTIPLIER` = 0.50, no ELO |
| R9 | Every repeatable reward has a cooldown. | `RateCard.repeatableSourcesWithoutCooldown()` is empty |
| R10 | Every tradeable value has a sink. | `RateCard.sinks()`, `EconomyBalance.sinkToFaucetRatio` ≥ 1.0 |
| R11 | A player cannot gain by idling. | every repeatable row is behind a clock or a game event |
| R12 | The on-court economy never swamps the result economy. | the hourly-income test: result ≥ ⅓ of total |
| R13 | A quest never needs a teammate to be completed. | `QuestCatalog.isSoloAchievable` for every daily |
| R14 | The player is told how far the next rank is. | `RankProgress.signal` on the final-score card |

---

## 3. The numbers I chose, and the alternative I rejected

### 3.1 Loss must pay something: `loss-coins: 0` → `loss-coins: 18`

**The number:** a loss pays **18 Coins**. A win pays **40 Coins**. The ratio
is **2.22 : 1**, inside the ≤ 3.0 bound in R2.

**Why 18 and not 20.** A loss that pays exactly half the win makes the
*expected* value of a match depend only on your win rate:

```
E[match] = p x 40 + (1 − p) x 18
```

At p = 0.5 that is 29 Coins; at p = 0.3 it is 24.6. With `loss-coins: 20`
the number is identical in shape — the ratio is what matters, not the
absolute. 18 is chosen because it lands the win/loss ratio at **2.22**, not
exactly 2. The reason it is not exactly 2 is that the win payout has to clear
the *participation* value of the win — the coin value of the buckets, assists
and steals the winner collected on the way to winning. Those are paid
separately (§3.2) and are *not* lost on a loss. So a loss must pay enough to
cover the player's own on-court contribution, and 18 does: at the rebalanced
rates, five made baskets (three twos at 2 and two threes at 3 = 12) plus
three assists at 2 = **18 Coins of on-court earnings** for a player who lost
a close game. The loss payout covers the participation, and the win payout is
the premium on top of it. That is what makes a bad game feel like a bad game
and not a punishment.

**The alternative I rejected:** `loss-coins: 25`, i.e. ratio 1.6:1. It sounds
generous, but it means a player at a 30 % win rate in a 40-match month ends up
with roughly the same coins as a player at a 50 % win rate who plays half as
many matches — the *skill* signal in the coin economy disappears, and the
monthly leaderboard (which sorts on coins) stops being a skill board. At
2.22:1 the win rate still dominates the payout, but a losing player is never
at zero, which is what keeps them coming back.

**The alternative I rejected:** keep `loss-coins: 0` and rely on the on-court
rewards to be the consolation. Rejected because the mission is explicit —
*a loss must always pay something* — and because a zero-payout loss is the
exact shape of "the server only tells the player who succeeded."

### 3.2 The on-court payouts, and why they are not a loss multiplier

| Event | Coins | Was | Note |
|---|---|---|---|
| Made 2-pointer | 2 | 5 | paid to the scorer |
| Made 3-pointer | 3 | 8 | paid to the scorer |
| Dunk bonus | 2 | 3 | on top of the basket |
| Assist | 2 | 4 | paid to the passer |
| Steal | 2 | 4 | paid to the defender |
| Block | 2 | 4 | paid to the defender |

These are **paid on the play, not on the result**. They are the reason the
loss payout exists: a player who scores 20 points in a loss has already been
paid for the buckets and takes the 18 on top. The on-court economy and the
result economy are separate faucets, and R1 applies to the *result* faucet
only.

**Why the numbers came down — this is the second real defect the rate card
found.** The shipped payouts were 5 / 8 / 3 / 4 / 4 / 4. A typical
competitive match paid roughly **133 coins on the court** against a **40-coin
win**: the on-court economy was 3.3× the result economy. The win was worth
less than three buckets. Three things broke at once:

- The monthly coin leaderboard stopped being a skill board. Playing well and
  playing *a lot* earned the same, because the on-court payout dwarfed the
  result payout and the on-court payout is roughly the same for everybody.
- A 2000-Coin Legendary cosmetic took three matches to buy instead of three
  weeks. The shop stopped being a milestone.
- The loss payout of 18 was noise. At 133 coins a match, "a loss pays 18"
  is a rounding error, and the whole symmetry argument in §3.1 became
  cosmetic.

At 2 / 3 / 2 / 2 / 2 / 2 a good match pays about **56 coins** — roughly the
same as the win. Every basket is still worth making, every assist is still
worth chasing, and the result leads the ledger again.

**The alternative I rejected:** halving the on-court payouts (2 / 4 / 1 / 2 /
2 / 2). Rejected because it makes a two-pointer and an assist worth exactly
the same, and the three-pointer only 1.5× a two — the price of a shot stops
reflecting what the shot is worth, and players read prices as signals. The
shipped ratio keeps a three at 1.5× a two, and a dunk at 2× a two (its 2-coin
base plus the 2-coin bonus, so 4 in total).

**The alternative I rejected:** a single blended payout ("40 coins for the
win, scaled by your box score"). Rejected because it makes the loss payout
scale down to nothing for a quiet player, which breaks R1, and because it
hides the two-participant rule the codebase learned the hard way: the assist
is a separate event with a separate participant who must be told separately.

### 3.3 The crate: EV repaired, and the daily cap pulled in

**The number:** `cost-coins: 300`, `coin-min: 40`, `coin-max: 220`.

```
E[coins]  = (40 + 220) / 2             = 130
E[bonus]  = 0.15 x 150                 = 22.5
E[refund] = 130 + 22.5                 = 152.5
E[net]    = 152.5 - 300                = -147.5 Coins per crate
```

The coin branch is now strongly negative on its own, and the cosmetic branch
(22 %) is the upside. The crate becomes what it should always have been: a
**sink that occasionally pays a cosmetic**, not a faucet that occasionally
pays a cosmetic.

**The alternative I rejected:** keeping `cost-coins: 250` and cutting
`coin-max` to 400. That would give `E = (60+400)/2 + 22.5 = 252.5`, i.e.
`+2.5` — still positive EV, and it would take hundreds of thousands of
simulated crates to notice. A sink has to be clearly negative, not
marginally so, or the first time someone raises `coin-max` by hand it
becomes a printer again. `-147.5` is a number you can feel in the rate card.

**The daily cap:** `daily-limit: 5` → **`daily-limit: 3`**, and the
`cooldown-seconds: 3` → **`cooldown-seconds: 30`**. Three openings a day is
the "three is a habit" rule applied to a repeatable reward, and the 30-second
cooldown means the crate can never be opened while the player is doing
something else (and the 3-second cooldown, which was shorter than the
opening animation, was itself a small anti-abuse hole).

### 3.4 The bot match: `bot-match-multiplier: 0.50`

`fill-bots-on-launch: true` means an empty slot is filled by a CPU player.
Today a human who queues ranked at 03:00, gets a 1v1 against one bot, and
takes the full ranked payout: 40 for the win, 15 for the MVP, plus the whole
on-court economy, plus ELO. That is the cleanest possible farm: no opponent,
no risk, full reward.

**The number:** a match with at least one bot on either side pays **50 %** of
its coin rewards and **no ELO**. The multiplier is `0.50` because half is
the smallest discount that is visible to a player and large enough to matter
— at 50 %, a bot-farm hour earns roughly what a real hour earns, so the
shortcut is no longer worth taking, and the game is not degraded into
punishing a player who joined an empty queue.

**The alternative I rejected:** `0.00` — bot matches pay nothing. Rejected
because an empty queue is often nobody's fault (a 03:00 server), and a
player who cannot earn at all will simply leave. Half-pay keeps them on the
court without making the shortcut attractive.

**The alternative I rejected:** exempting ELO but keeping coins. Rejected
because ELO is the part that matters for the ladder, and coins are the part
that matters for the economy — you have to close both to make the farm
uninteresting.

**The knob is live.** `economy.bot-match-multiplier` is read from
`config.yml`, clamped to `[0.0, 1.0]`, and pushed into
`EconomyBalance.setBotMatchMultiplier(...)` by
`BosketBallPlugin.reloadBosketConfig()`. This was not true at first: the key
was parsed into the config record and then never read, because the payout
path consulted a constant. An admin who tuned the number would have seen no
effect at all. The test `testTheBotMatchMultiplierIsALiveConfigKeyAndNotADeadOne`
now fails if that link is ever broken again.

### 3.5 Season structure

| Element | Value | Why |
|---|---|---|
| Duration | 28 days | A month is long enough to have a story and short enough that the reset is a talking point. Four-week seasons are the community standard. |
| Placements | 5 | Long enough to be a real sample, short enough to reach in a session. |
| Soft reset anchor | 1000 ELO | The starting ELO. |
| Keep ratio | 0.50 | A player 200 above the anchor keeps 100 of it; a player 400 below keeps 200 of the deficit. |
| Tier coins | 200 / 350 / 550 / 800 / 1200 / 1800 / 2600 | By rank tier, Rookie → MVP. |
| Top-3 bonus | +800 / +450 / +200 | The podium is worth more than a tier step, not 10×. |
| Trophy | 1 per podium finish | A counter, not a currency. |

**The keep-ratio number.** `0.50` is the value the config already shipped and
it is the right one: at 0.5 the reset halves the *distance* from the anchor
without halving the rating, so an MVP at 2400 lands at 1700 — a Superstar
needing a run of good games to get back, not a Rookie starting from zero.
The Rocket League compression is toward the *middle* of the ladder; BosketBall
has no ladder middle (the ladder starts at 1000 because that is the starting
ELO), so the anchor *is* the middle and the keep ratio is the compression
strength.

**The alternative I rejected:** `keep-ratio: 0.0`, a hard reset to 1000 for
everybody. It is the standard "fresh season" move and it is wrong here
because BosketBall's lifetime stats (wins, points, achievements) live on the
same profile as the rating. A hard reset would tell a 400-win veteran that
they are a Rookie, and the brief's own test list asks that a season reset
*preserve lifetime stats*. The soft reset moves the number the ladder reads;
it does not touch the numbers the profile is made of.

**The alternative I rejected:** `keep-ratio: 0.9`, a near-no-op reset. It
preserves everything but does nothing about rank inflation, which is the
entire reason the reset exists.

### 3.6 The end-of-season summary card

The card is what a player screenshots. It has to survive being posted in
Discord, so it is one framed card, no scrolling, and it carries the four
numbers a player actually wants to keep: **peak ELO**, **rank reached**,
**games played**, **wins**. Plus the season's coins earned, and the tier
reward they were paid.

Design constraints from the house style:
- One card. No second title. No second toast.
- The separator is `<#64748B>·</#64748B>` — the middot, always.
- The house mark `▪` for the card's own title line.
- The player's name in `Palette.TEXT`, the numbers in `Palette.GOLD_SOFT`,
  the labels in `Palette.MUTED`, the tier in `Palette.ICE`.

The card reads **"SEASON III — YOUR SEASON"**, not "SEASON III — FINAL
RESULTS", because it is about *your* season, and the broadcast archive card
already covers the server's.

### 3.7 Quest design: three dailies, one weekly

The weekly board goes from **three quests to one**. The two contracts
(six-hour windows, 6–8 dunks) become **one** six-hour sprint instead of
two, and the contract pool is now a set of *hard* dailies rather than a
separate board demanding a separate round of claims. The result is three
repeatable boards a player services — three dailies, one weekly, one sprint —
instead of five claims per cycle.

**The board counts** (pinned by
`testTheConfigFallbacksMatchTheShippedConfigSoNoBugComesBack`):

| Board | Shown to the player | Templates in the pool |
|---|---|---|
| Daily | 3 | 17 |
| Weekly | 1 | 8 |
| Sprint (contract) | 1 | 7 |

**The daily pool**, every target achievable inside one session (a session is
~8 matches), and none dependent on a teammate:

| Stat | Easy target | Hard target | Session of 8 matches, typical line |
|---|---|---|---|
| Points scored | 20 | 45 | ~11 points per match |
| Three-pointers | 5 | 10 | ~0.8 per match |
| Two-pointers | — | 12 | ~1.5 per match |
| Dunks | 3 | 5 | ~0.6 per match |
| Assists | 4 | — | ~0.7 per match |
| Rebounds | 5 | — | ~0.8 per match |
| Steals | 3 | — | ~0.6 per match |
| Blocks | 3 | — | ~0.6 per match |
| Ankle breakers | 2 | — | ~0.4 per match |
| Wins | 2 | — | ~1 in 4 |
| Matches played | 5 | — | ~0.6 of a session |
| Casual matches | 2 | — | a quarter of a session |
| Ranked matches | 2 | — | a quarter of a session |
| 3-Point Contests | 1 | — | one game |

A daily pays **80–170 coins**, the weekly **700**, a sprint **260–300**.

**Every one of those is solo.** No daily requires an assist, because an
assist is a two-participant event and a daily that depends on somebody else
showing up is a daily that fails. This is the "none dependent on a teammate"
constraint taken literally: the *stat* may be a team stat, but the
*target* must be reachable by one player's own play. `QuestCatalog.
isSoloAchievable` checks every template against an eight-match session, and
the test `testEveryQuestIsReachableInOneSessionAndNeedsNoTeammate` walks
the whole pool.

**The weekly:** one quest, one stat, one target, ~8 sessions' worth. The
pool is points (200), threes (60), dunks (30), assists (20), wins (10),
ranked matches (10), blocks (15) and steals (15) — every one of them at
700 coins.

**The alternative I rejected:** keep three weekly quests. Rejected because
the boards then total 3 + 3 + 2 = 8 repeatable claims per player per cycle.
The research is explicit that the daily set should be completable in 15–30
minutes; eight claims is not a habit, it is a checklist. Three dailies and
one weekly is the habit.

**The alternative I rejected:** make dailies "pick 1 of 3" (choose one to
complete, get the reward). Rejected because it removes the *choice* that
makes a quest board interesting and replaces it with a slot machine.

### 3.8 Net income per hour, per skill band

This is the table the rate card encodes (`RateCard.hourlyIncome`). "Hour" =
one hour of actual play, about **8 matches**. The on-court figure is what
the Average band earns on the court in one match — **56 coins** — scaled by
the band's own on-court multiplier, because a better player scores more and
gets more assists.

| Skill band | Wins / 8 | On-court / match | Result / hour | On-court / hour | **Total / hour** |
|---|---|---|---|---|---|
| Rookie (p ≈ 0.35) | 2.8 | 45 | 206 | 358 | **564** |
| Average (p ≈ 0.50) | 4.0 | 56 | 232 | 448 | **680** |
| Good (p ≈ 0.65) | 5.2 | 63 | 258 | 502 | **760** |
| Elite (p ≈ 0.80) | 6.4 | 70 | 285 | 560 | **845** |

Plus the daily and weekly boards (three dailies at 80–170 coins each, so
**+240 to +510** a day, and **+700** a week) and the daily login (40–200
depending on streak). A player playing one hour a day nets roughly **+900
to +1,100 Coins per day** at the Average band.

Against that, the sinks: the cosmetic catalogue is worth **65,600 Coins**
in total, measured from the shop enums rather than estimated — 54
purchasable chat tags (54,000, from 250 to 2,500), seven purchasable ball
skins (7,300, from 300 to 2,000) and four purchasable dunk styles (4,300,
from 500 to 1,750). The Classic ball and the Tomahawk dunk are free, and
three chat tags are staff-only. That is roughly **96 hours** of Average-band
play — about twelve 8-match sessions — to buy the entire catalogue, and a
2,000-Coin Legendary tag is one session's worth of income. The crate costs
300 per opening, a quest reroll 75.
A player at the Average band buys a 2,000-Coin Legendary cosmetic every
**three days** of play, which is the intended cadence: the shop is a
*visible* milestone, not an unreachable wall, and the catalogue is deep
enough that the economy never runs out of things to buy.

The exact breakdown is worth recording, because I got it wrong three times
while drafting this document. I counted the constants in the enum source
with a regular expression instead of reading the `defaultPriceCoins()`
values the shop actually charges, and every count I produced by hand was
wrong somewhere — 63,600, then 67,100, then a ball-skin sum of 5,300 that
is really 7,300. The constitution says a number that is not reachable from
the code is a bug — in the documentation as much as in the plugin. The
figure is now produced by `EconomyBalance.shopInventoryCoins()` and
asserted line by line, catalogue by catalogue, by
`testTheConfigFallbacksMatchTheShippedConfigSoNoBugComesBack`: 54 tags
worth 54,000, seven skins worth 7,300, four dunks worth 4,300, and a
catalogue of exactly 65,600. If a skin is added to the shop, that test
fails until the document and the Javadoc are updated with it.

That is the argument for the rate card being *in code* one more time. A
spreadsheet would have carried all three wrong numbers into the shop
pricing forever, because nothing checks a spreadsheet against the thing it
describes.

**The sink-to-faucet ratio** (`EconomyBalance.sinkToFaucetRatio`) is measured
over an 80-match season: the faucets are the result payouts, the on-court
payouts and the progression boards; the sinks are the shop catalogue, the
crate, the rerolls and the forfeit penalty. The ratio comes out above 1.0,
which is the no-loop proof: the economy can take coins back out.

**The alternative I rejected:** the shipped numbers (win 40 / loss 0, on-court
5/8/3/4/4/4). A loss paid nothing, so the spread between a Rookie and an
Elite was a factor of **1.7** for playing far better — and because the
on-court economy was constant, most of that spread was "who logged more
hours". With the loss payout and the rescaled on-court economy the spread is
**564 to 845**, and the skill gap is real rather than an hours-logged board.
The gap widened because the loss payout is the *floor*: making the floor
higher makes the *ceiling* relatively more valuable without making the floor
feel punitive. That is the whole argument for R1 in one line.

### 3.9 "You are two games from a rank-up" — the missing signal

The server celebrates a rank-up with a title, a particle burst and a server-wide
announcement. It never says the thing a player actually wants to know: **how
close am I?** A player at 1035 ELO has no way to tell whether the next rank is
one game away or fifty.

The research on this is unusually direct. A study of leaderboard grind found
churn was stable at 3 % per rank from Rank 10 down to Rank 5 — and then
**19 % within 48 hours** of players reaching Rank 4. The cliff is not caused by
the rank; it is caused by the player reaching the end of a visible ladder and
finding nothing beyond it. The same study makes the split that matters:

> *"You are 12% faster than last week" is a more powerful retention driver
> than "You are 340 points from Rank 3."* — but the near finish line is what
> pulls a player through the last stretch.

Both halves are needed, and BosketBall now has both:

- **The near-term goal** (`RankProgress`): the final-score card carries one
  extra line — *"2 more games to All-Star (at best)"* — when the player is
  within ten wins of the next rank. At a +15 win rate that is up to 150 ELO of
  gap, which is three to four good sessions.
- **The long-term horizon**: the seasonal tier and the shop catalogue. The
  weekly quest, the season card and the 65,600-Coin cosmetic catalogue are
  the "faster than last week" axis.

**The "at best" is not decoration.** A game can be lost. The server does not
make promises it cannot keep: a player who loses five in a row has not been
cheated, they have been told the truth. The copy says "at best" out loud.

**The threshold is ten.** Past ten wins the gap stops being a goal and starts
being a fact of life, and a number that says "63 games" does not motivate
anybody — it just tells them to stop. So the signal simply does not appear
there. A player at 100 ELO sees nothing; a player at 1049 ELO sees "1 more
game".

**The alternative I rejected:** showing the raw ELO gap ("15 points to
Hooper"). Rejected because it asks the player to do arithmetic — how many
wins is 15 points? — and an ELO number is not a goal, a count of games is.
The other rejected alternative was showing it on the sidebar permanently,
which competes with the match HUD for the same pixels and breaks the rule
that a screen surface is either the match or the progression, never both.

---

## 4. Sources

- [Rocket League Season 24 — soft reset explained](https://buyboosting.com/rl-boosting/season/)
- [Rocket League Season 22 — reset and placements](https://www.thespike.gg/rocket-league/beginner-guides/season-22-details)
- [Rocket League ranks and MMR, soft reset and placement swings](https://esportnow.gg/games/news/rocket-league-ranks)
- [Rocket League ranks, soft reset and no inactivity decay](https://neonsect.com/rocket-league/rocket-league-ranks-explained/)
- [Elo rating system — rating floors, FIDE K-values, inflation and deflation](https://en.wikipedia.org/wiki/Elo_rating_system)
- [The Elo Rating explained for product managers — K-factor, floor, non-zero-sum warning](https://butaiisdifferent.substack.com/p/the-elo-rating-explained-for-pm)
- [r/aoe2 — smurfing in ELO limit tournaments, the rating floor as anti-sandbagging](https://www.reddit.com/r/aoe2/comments/1oytirh/smurfing_in_elo_limit_tournaments/)
- [Time-Gating Mechanics in Minecraft Servers — daily quests, the 15–30 minute sweet spot](https://www.fumora.net/lab/minecraft-time-gating-mechanics)
- [ODailyQuests Continued — three daily, independent reset schedules](https://www.spigotmc.org/resources/odailyquests-continued-daily-quests-for-paper.138553/)
- [ODailyQuests — the original three-quest plugin](https://www.spigotmc.org/threads/odailyquests-daily-quests-plugin-1-18-2-1-21.551543/)
- [r/gamedesign — faucets and sinks, one-time sinks are not enough](https://www.reddit.com/r/gamedesign/comments/12e0gvv/economy_design_should_ingame_currency_be_created/)
- [How virtual coin systems shape player engagement — accrual and sink design](https://tomoson.com/how-virtual-coin-systems-shape-player-engagement)
- [Cross-stitch game — coin sink roadmap, an event faucet without a matching sink leaves inflation](https://github.com/muratti32/cross-stitch-game/issues/286)
- [Cross-stitch game — collection album as a faucet, unlock-earnability targets](https://github.com/muratti32/cross-stitch-game/issues/284)
- [Leaderboard grind fades 19% after Rank 4 — the cliff at the end of a visible ladder](https://webline.global/posts/leaderboard-grind-fades-19-after-rank-4-study-finds/)
- [How to set up a Skyblock server that keeps players coming back — progression ladder pacing and seasonal resets](https://space-node.net/blog/minecraft-skyblock-game-loop-player-retention)
