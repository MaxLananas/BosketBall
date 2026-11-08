# Conductor's merge guide — PR #11 (Agent 8, Economy)

**PR:** [#11 — Agent 8 — Economy, Progression & Season](https://github.com/MaxLananas/BosketBall/pull/11)
**Base:** `arena/a1145fcf-bosketball` · **Head:** `agent/economy` · **Status when written:** OPEN,
CLEAN, MERGEABLE, 1 commit, 25 files, +4088/−100.

This file is the merge plan. **Everything in it was verified by actually performing the merges
and running the build** — not predicted. Where a step has a trap, the trap is named.

---

## 1. The order that works

PR #11 is **independent of the code in every other open PR**. It only overlaps on files that
every PR in this wave touches. The recommended order is the one the conductor already has
(#6 → #7 → #8 → #9 → #10 → #11 → #12 → #13), with one addition: **merge #13 (interfaces) after
#11**, which the existing order already does, and resolve #13's conflict using §3 below.

### Measured merge results

Every open PR was merged into the base, then PR #11 was merged on top, in a scratch branch.
These are the actual results:

| PR | Branch | Merged on top of | Unmerged files after the merge |
|---|---|---|---|
| #10 — HUD | `arena/c56cf515-bosketball` | base | `releases/BosketBall.jar` only |
| #12 — onboarding | `arena/c8b6dca0-bosketball` | base | `releases/BosketBall.jar` only |
| #13 — interfaces | `arena/7e1eb467-bosketball` | base | `releases/BosketBall.jar`, `ui/GuiMenuManager.java`, `tools/build_plugin.py` |

**So: PR #11 lands on top of #10 and #12 with a single trivial conflict — the compiled jar.
PR #11 needs a small manual step on top of #13.**

The jar conflict is not a real conflict. Every PR rebuilds `releases/BosketBall.jar`, so the
binary necessarily differs on both sides. Resolve it by taking **either** side and then
re-running the build, which regenerates the correct jar:

```bash
git checkout --ours releases/BosketBall.jar   # or --theirs; it does not matter
python3 tools/build_plugin.py
```

### Verified build results

Every combination below was actually merged, resolved and built:

| Combination | Result |
|---|---|
| PR #11 alone | 261 classes, 0 errors · **OK (206 tests)** |
| PR #10 + PR #11 | 269 classes, 0 errors · **OK (219 tests)** |
| PR #12 + PR #11 | 279 classes, 0 errors · **OK (214 tests)** |
| PR #13 + PR #11, resolved per §3 | 271 classes, 0 errors · **OK (218 tests)** |

In all four cases the resource pack SHA-1 stayed
`93113530f92ab4ca09f149d4284a1d6c74755758` and all 14 sound keys still resolved.

---

## 2. The one thing to re-check after merging #11

PR #11 **retargets two assertions** in `BosketBallEngineTest`. They are not weakened — they are
changed to test the new invariants — but the conductor should know they changed, because a
reviewer scanning that file will see a diff:

| Was | Is now | Why |
|---|---|---|
| `assertEquals("...0 loss-coins penalty by default!", 0, defCfg.economy().lossCoins())` | `assertTrue("...a loss that pays nothing is the server only telling the player who succeeded!", lossCoins > 0)` **and** `assertTrue("...throwing a match would be rational!", lossCoins < winCoins)` | The old assertion *required* the bug. A loss now pays 18 against a 40-coin win. |
| `assertEquals(1200, season.softResetAnchor())` | `assertEquals("...the reset compresses the field toward the middle of the ladder...", 1000, season.softResetAnchor())` | The soft-reset anchor is now the starting ELO. |

Both are listed in the PR body under "The new tests, and what each one prevents".

---

## 3. Resolving PR #13 on top of PR #11

### 3a. `tools/build_plugin.py` — keep BOTH test classes

Both PRs add a class to the JUnit runner list. The resolution is to keep both entries:

```python
[java, "-cp", run_cp, "org.junit.runner.JUnitCore", "fr.bosketball.BosketBallEngineTest",
 "fr.bosketball.CodecFuzzTest", "fr.bosketball.InterfacesKitTest",
 "fr.bosketball.EconomyBalanceTest"],
```

**This one matters more than it looks.** `tools/build_plugin.py` lists the test classes
explicitly, so a new test file that is not listed here **compiles but never runs**. PR #11's
nineteen economy tests are silent unless `fr.bosketball.EconomyBalanceTest` is on that list.
If the resolution drops either entry, the build will still report green — it will just be
running fewer tests than it claims.

For this file, take **the side that already lists `EconomyBalanceTest`** and add
`InterfacesKitTest` to it.

### 3b. `ui/GuiMenuManager.java` — PR #13 deletes the block I edited

PR #13 moves the quest board out of `GuiMenuManager` and into a new declarative menu kit
(`ui/ProgressionGuis.java`). The per-quest claim click I edited **no longer exists in that
file** — PR #13 replaces the whole region.

**Resolve by taking PR #13's version of `GuiMenuManager.java` in full.** My two-line change
there was in the block PR #13 deletes, so there is nothing to carry over into that file.

> ### ⚠ The `--ours` / `--theirs` direction depends on how the merge is run
>
> This is the one step where it is easy to take the wrong side and get **34 compile errors**.
> The names are relative to **whose HEAD you are standing on**, not to the PR number.
>
> | How the conductor merges | The command that takes PR #13's version | The wrong one |
> |---|---|---|
> | GitHub's "Merge pull request" (base = PR #13's branch, head = `agent/economy`) | `git checkout --ours src/main/java/fr/bosketball/ui/GuiMenuManager.java` | `--theirs` — takes **my** 4,757-line version and leaves PR #13's new `ProgressionGuis` and `PurchaseConfirmMenu` calling methods that no longer exist → **34 compile errors** |
> | `git merge origin/agent/economy` while standing on PR #13's branch | `git checkout --ours …` | `--theirs` — same failure |
> | `git merge origin/arena/7e1eb467-bosketball` while standing on `agent/economy` | `git checkout --theirs …` | `--ours` — same failure |
>
> **The safe rule: take the side whose `GuiMenuManager.java` is 4,407 lines.** PR #13's is
> 4,407; mine is 4,757. Check with
> `git show :2:src/main/java/fr/bosketball/ui/GuiMenuManager.java | wc -l`
> before you resolve, and take whichever stage matches.
>
> I verified this end to end: merging PR #13's branch and PR #11 together, resolving as
> above, applying §3c, gives **271 classes, 0 errors, OK (218 tests)** — PR #13's twelve
> `InterfacesKitTest` tests and PR #11's nineteen economy tests, all running.

### 3c. `ui/ProgressionGuis.java` — PR #13 introduces the bug PR #11 fixes

**This is the one real conflict, and it is worth a reviewer's attention.**

PR #13 adds a new private method:

```java
private void claimQuest(Player player, PlayerQuest quest) {
    if (quest.claimed() || !quest.isComplete()) {
        return;
    }
    PlayerProfile profile = plugin.statsManager().getOrCreate(player);
    quest.setClaimed(true);
    quest.setRewarded(true);
    profile.addEarnedCoins(quest.rewardCoins());   // <-- pays directly
    profile.addXp(quest.rewardXp());                // <-- pays directly
    ...
}
```

That is **exactly the double-pay bug** PR #11 fixes in `GuiMenuManager`: the payout is made
directly, with the quest's own `claimed` flag as the only guard. The flag lives on an object
that is replaced whenever the board rolls over, and a corrupted profile can carry a completed
quest with the flag back at `false` — so the same quest can pay twice. It also duplicates the
"Claim all" path's payout logic in a second place, which is how the two paths drifted apart
in the first place.

**The fix is to route PR #13's method through the ledger PR #11 ships.** Replace the body with:

```java
private void claimQuest(Player player, PlayerQuest quest) {
    if (quest.claimed() || !quest.isComplete()) {
        return;
    }
    PlayerProfile profile = plugin.statsManager().getOrCreate(player);
    // Round 84 (economy): the payout goes through the progression ledger, so this path and
    // the "Claim all" button cannot pay the same quest twice.
    int questCoins = plugin.progressService().claimQuest(profile, quest);
    if (questCoins <= 0 && !quest.claimed()) {
        Msg.send(player, Msg.info("That quest has already been claimed."));
        return;
    }
    plugin.statsManager().markDirty(player.getUniqueId());
    plugin.statsManager().saveProfileAsync(profile);
    plugin.progressService().evaluateAchievements(profile, true);
    Msg.playPersonalPackSound(player, "ui.click", 1.0f, 1.45f);
    Msg.send(player, Msg.success("Quest claimed: +" + questCoins
        + " coins, +" + quest.rewardXp() + " XP."));
}
```

The exact signature it calls is:

```java
public int claimQuest(PlayerProfile profile, PlayerQuest quest)
```

in `fr.bosketball.progression.ProgressService`. It returns the coins **actually paid** — `0`
when the quest was incomplete or already paid — and it pays the coins and the XP together, so
the `+quest.rewardXp()` in the toast stays correct.

### 3d. The one rough edge worth a look

A quest whose reward is **zero coins but non-zero XP** will show `"+0 coins"`. The number is
truthful — zero coins were paid — but it reads oddly. Agent 3's call: make the coin fragment
conditional on `questCoins > 0`, so a zero-coin quest reads `"Quest claimed: +75 XP."`. That is
a copy change in Agent 3's file, and I did not make it unilaterally because the copy is theirs.

The alternative, which is mine, is to give every quest at least one coin so the case never
arises. That is a change in `progression/QuestCatalog.java`. **Either is fine; the important
part is that the payout goes through the ledger either way.**

---

## 4. The conflicts PR #11 does *not* have

PR #11 edits `game/MatchSession.java`, which PR #10 also edits. **The hunks are disjoint** —
PR #10 touches the HUD surfaces and the sidebar, PR #11 touches the settlement block and the
final-score card — and the measured merge confirms it: no unmerged files.

The same is true of `BosketBallPlugin.java` and `ProgressService.java` against PR #10.

PR #12 (onboarding) and PR #11 both touch `config.yml`. The hunks are disjoint — PR #12 is in
the onboarding/tutorial section, PR #11 is in the `crates`, `economy` and `season` blocks. The
measured merge confirms it.

---

## 5. Files PR #11 owns outright

These are new and do not exist on the base, so nothing can conflict with them:

```
docs/agents/research/economy.md
docs/agents/integration/agent-2.md
docs/agents/integration/agent-3.md
src/main/java/fr/bosketball/economy/EconomyBalance.java
src/main/java/fr/bosketball/economy/RateCard.java
src/main/java/fr/bosketball/progression/QuestRewardGuard.java
src/main/java/fr/bosketball/season/SeasonSummary.java
src/main/java/fr/bosketball/stats/RankProgress.java
src/test/java/fr/bosketball/EconomyBalanceTest.java
```

---

## 6. The ownership disclosure, in one place

PR #11 edits two files owned by other agents, and discloses both in full:

| File | Owner | What PR #11 changes | Disclosure |
|---|---|---|---|
| `game/MatchSession.java` | Agent 2 | the settlement block (bot multiplier, loss payout, draw branch) and the final-score card (the rank signal) | `docs/agents/integration/agent-2.md` — exact call site, exact signature, reason, risk, and how to revert each hunk independently |
| `ui/GuiMenuManager.java` | Agent 3 | the per-quest claim click, routed through the new ledger | `docs/agents/integration/agent-3.md` — same |

Every new class is referenced by its **fully-qualified name**, so neither file's import block
changed and neither file grew a dependency. The economy, season and progression packages
compile and pass their tests **without** those edits; each hunk is self-contained and can be
re-landed under its owner's name.

---

## 7. What to check after the merge

The conductor's standard checklist, with this PR's specifics:

1. **Build green, and the test count moved.** Baseline 187 → **206**. The build must print:
   ```
   ✓ Plugin compiled cleanly (261 classes, 0 errors)!
   OK (206 tests)
   ```
   If the count is 187 or 195, the JUnit runner list lost an entry — see §3a.
2. **The resource pack SHA-1 is unchanged:** `93113530f92ab4ca09f149d4284a1d6c74755758`, tag
   `pack-93113530`. PR #11 touches no pack asset.
3. **All 14 sound keys still resolve.** The build prints this as the first line.
4. **The new quest icons all exist.** PR #11's `QuestCatalog` references 12 icon models
   (`icon_battle`, `icon_crossover`, `icon_defense`, `icon_dunk`, `icon_medal_gold`,
   `icon_star_shield`, `icon_team_duo`, `ref_arrow_big_right`, `ref_calendar_check`,
   `ref_check`, `ref_dice`, `ref_trophy`). All twelve were verified to exist as both an item
   texture and an item model in `resourcepack/assets/bosketball/`.
5. **The config parser's fallbacks still equal the shipped config.** PR #11 fixed a class of
   bug where `BosketConfig` carried a second, older copy of the economy. The test
   `testTheConfigFallbacksMatchTheShippedConfigSoNoBugComesBack` fails if the two drift apart
   again — including the crate (300/40–220/3 a day/30 s), the loss payout (18), the bot
   multiplier (0.50) and the season anchor (1000).
6. **The config keys are all live.** `economy.bot-match-multiplier` in particular used to be
   parsed and then ignored; `testTheBotMatchMultiplierIsALiveConfigKeyAndNotADeadOne` fails if
   that link is broken again.
