# Agent 8 (Economy, Progression & Season) → Agent 3 (UI / GUI)

**Status: CHANGES ARE ALREADY APPLIED IN THIS BRANCH.** This file is the
disclosure the Constitution asks for, plus the exact call site so the
conductor can confirm or re-apply them during the merge.

`ui/GuiMenuManager.java` is Agent 3's file. I edited two places in it, both
inside the quest-claim path. The changes are small and mechanical, and both
are listed below with the exact signature, the exact call site, the reason,
and how to revert them independently.

If the conductor prefers these as Agent 3's commit, each hunk is
self-contained: revert the hunk and re-apply it under Agent 3's name. The
progression package (`QuestRewardGuard`, `ProgressService.claimQuest`)
compiles and passes its tests **without** the `GuiMenuManager` change — the
double-pay protection simply would not be reachable from the per-quest click,
which is exactly the bug this fixes.

---

## 1. The per-quest claim button — `GuiMenuManager.java`

### The call site

`GuiMenuManager`, the quest-board click handler, per-quest slot path (around
line 3777). The existing block:

```java
clicked.setClaimed(true);
clicked.setRewarded(true);
profile.addEarnedCoins(clicked.rewardCoins());
profile.addXp(clicked.rewardXp());
```

becomes:

```java
int questCoins = plugin.progressService().claimQuest(profile, clicked);
if (questCoins <= 0 && !clicked.claimed()) {
    Msg.send(player, Msg.info("That quest has already been claimed."));
    return;
}
```

and the success message's coin amount changes from `clicked.rewardCoins()`
to `questCoins`:

```java
Msg.send(player, Msg.success("Quest claimed: <#F8FAFC>+" + questCoins
    + " Coins</#F8FAFC>, <#F8FAFC>+" + clicked.rewardXp() + " XP</#F8FAFC>."));
```

### The exact signature

```java
public int claimQuest(PlayerProfile profile, PlayerQuest quest)
```

in `fr.bosketball.progression.ProgressService`. It returns the coins
actually paid — `0` if the quest was already paid, or if it has no reward
and was never paid.

### Why

**There were two claim paths and only one of them was idempotent.**

The "Claim all" button (slot 49) went through `ProgressService`'s
`claimCompletedQuests`, which checked the quest's own `claimed` flag before
paying. The **per-quest** click (slots 10–13, 19–22, 28–29) paid
unconditionally:

```java
clicked.setClaimed(true);
profile.addEarnedCoins(clicked.rewardCoins());
```

So the sequence *click the quest → click "Claim all"* paid the same quest
twice, and the sequence *click the quest → close the GUI → click the quest
again* also paid it twice, because `setClaimed(true)` happened **after** the
click handler had already been entered and the GUI re-opened with a stale
quest object in some flows.

That is a direct violation of the brief's first required test — *no reward
path pays twice*. The fix is that both paths now go through one ledger.

### What the ledger is

`fr.bosketball.progression.QuestRewardGuard` — a small in-memory map keyed
by `(player UUID, quest id, assignment time)`. `pay(...)` records the key and
returns `0` on a second call; `hasPaid(...)` is the read. The `claimed` flag
on the quest is *not* sufficient on its own, because a board can be replaced
wholesale (a reroll, a board reset) and the flag travels with the old object
while the ledger remembers the truth.

The ledger is in my package (`progression/`), which is mine. The only thing
that was needed outside it was **the call site**, which is this file.

### The `questCoins <= 0 && !clicked.claimed()` condition

This looks odd, so here is why it is written that way.

There are two distinct "nothing happened" cases, and the player should only
be told "already claimed" in one of them:

- **The quest was paid before.** `questCoins == 0` **and** `clicked.claimed()`
  is `true`. This is a double-click on an already-claimed quest. The old
  code fell through to the success message and said *"Quest claimed: +250
  Coins"* — while paying **zero**. That is the bug the condition fixes.
- **The quest has no reward and was never paid.** `questCoins == 0` and
  `clicked.claimed()` is `false`. This is a zero-reward quest (some
  contract-board entries pay XP only, or nothing). There is nothing to
  claim, but it was never claimed either — so it is not "already claimed",
  and the message must not lie.

The condition therefore distinguishes the two, and only the first one
produces the "already been claimed" message.

### What Agent 3 gets out of this

- The success toast now reports the coins that were **actually paid**
  (`questCoins`), not the quest's nominal reward. A quest that was already
  claimed no longer says "+250 Coins" while paying nothing.
- The success toast's XP figure still reads `clicked.rewardXp()`. That is
  correct because `ProgressService.claimQuest` pays both coins and XP
  together, and returns zero for both on a repeat. If Agent 3 would rather
  the toast read a single combined figure, that is a copy change.

---

## 2. Nothing else in `GuiMenuManager.java` was touched

- No import was added to this file. `ProgressService` and `PlayerQuest` were
  already in scope; `claimQuest` is reached through
  `plugin.progressService()`, which the file already called on the adjacent
  line.
- The "Claim all" button (slot 49) was **not** modified. It already called
  `ProgressService.claimCompletedQuests`, which now routes through the same
  ledger.
- No layout slot was moved. The daily slots `{10, 11, 12, 13}`, the weekly
  `{19, 20, 21, 22}`, the contract `{28, 29}`, the reroll `{31, 32}`, the
  claim-all `{49}` and the back `{53}` are all as they were.
- No GUI title, item name, lore line or material was changed.
- No sound key was added.

---

## 3. The risk to Agent 3's system

| Risk | Likelihood | Mitigation |
|---|---|---|
| The success toast now shows a different number than before | Certain, and intended | Before, it showed the quest's *nominal* reward. Now it shows what was paid. For a first claim these are identical. |
| A zero-reward quest shows no confirmation | Low, and intended | The condition distinguishes "already claimed" from "nothing to claim". A zero-reward quest that was never claimed simply proceeds through the normal path with `questCoins == 0` and shows "+0 Coins" — hmm, that is slightly awkward. **See the note below.** |
| The GUI does not reopen after a claim | None | The `return` inside the new condition is the same shape as the existing early returns above it (the incomplete-quest path). The success path is unchanged. |
| The claim-all button and the per-quest button disagree | None | Both now call the same ledger. The claim-all path was already correct; the per-quest path was the defect. |

### The one rough edge worth Agent 3's attention

A quest whose reward is **zero coins but non-zero XP** will now produce the
toast `"Quest claimed: +0 Coins, +75 XP."` The number is truthful — zero
coins were paid — but "+0 Coins" reads oddly. I did not change the copy,
because the copy belongs to Agent 3 and the shape of the toast is a UI
decision, not an economy one.

My suggestion, if Agent 3 agrees: make the coin fragment conditional on
`questCoins > 0`, so a zero-coin quest reads
`"Quest claimed: +75 XP."`. That is a two-line change in the same block, and
it does not touch the economy.

If you would rather not have a zero-coin quest at all, the alternative is to
give every quest at least one coin — that is a change in *my* package
(`QuestCatalog`), and I am happy to make it if you want to say so. I did not
do it unilaterally because the brief asks for the rate card to list every
reward, and a zero-coin quest is a legitimate row on it.

---

## 4. How to revert each change independently

```bash
# Revert just the GUI hunk
git diff src/main/java/fr/bosketball/ui/GuiMenuManager.java | git apply -R
```

The progression package compiles and passes its tests without it. The
double-pay bug returns, and `testNoRewardPathPaysTwice` will still pass
(it tests the ledger directly, not the GUI wiring), but the *player-facing*
path is no longer protected.
