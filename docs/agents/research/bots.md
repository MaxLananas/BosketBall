# Bot research and current audit

## Sources and implications

- [NBA 2K slider discussion](https://forums.operationsports.com/forums/forum/basketball/nba-2k-basketball/nba-2k-basketball-sliders/939609-nba-2k24-the-eccentric-edition-realistic-slider-set) separates action tendencies from success rates. A drive-and-kick player is not merely a more accurate shooter. This is community tuning, not a verified description of 2K internals.
- [Human behavior in 3D shooters](https://lacuna.tiptreesystems.com/work/imitation-of-human-behavior-in-3d-shooter-game/wrk_3f74ac3b99417fba6d517fbb740b1fb5) discusses perception, reaction inertia and motor correction instead of perfect engine information. A basketball bot should commit to a decision, then recover, with sight and delay before a steal.
- [Minecraft AI Bot mod](https://player.games/en-US/creator-hub/minecraft/community/aibot) describes individual names, personalities and proximity-limited replies. This is a mod's self-description, not evidence of a major server's practice; reliable top-server bot presentation examples were not found. Do not imply players are human; make mistakes legible and chatter sparse.

## Audit of the starting implementation

- Mannequin subtitle explicitly says AI; all bots wear the same sneaker model and use a small, conspicuous basketball-nickname roster. They rotate instantly, move at fixed speeds and never use a running pose.
- Every difficulty is largely the same decision tree with different numeric thresholds. Shoot bias is unused; pass/drive biases only affect some choices. Off-ball destinations are randomly rerolled on each decision, and driving shoots on a fresh 50% roll every tick.
- Shots aim directly at the rim. The host then uses difficulty accuracy and random physics rather than a visible early/late decision. Dunks ignore difficulty. Block success, jump-shot ratio and dribble mistake chance are unused; mistakeTicks is never assigned.
- The watchdog teleports after 45 stationary ticks, including when intentionally standing at an anchor. It does not detect back-and-forth decisions and has no pathfinding.
- Ball and human carrier coordinates are available globally; tracking, pickup and deflection have no field-of-view or block visibility test. A steal can be attempted without reaction to a newly visible carrier. Defense only considers human carriers, not opposing bot carriers.
- `isTeamPossession` treats every human carrier as an ally, irrespective of team, so defenders do not select defense against human carriers. Defender distance likewise includes a human teammate. The host interface does not expose a human's team.
- The squad constructs every avatar with a null session. When chatter fires, `viewers()` dereferences it; chatter draws from shared generic phrases, can repeat, and has no possession-wide coordination. The host broadcasts a system-style steal message.
- Passing chooses the first human teammate or a random bot teammate in the host rather than a visible target chosen by the brain. No target visibility or interception planning is checked.
- Invalid bots respawn at Ignis slot zero regardless of their original team, changing entity IDs without updating the session team map. The squad is ticked only in the match animation path; full live play needs integration testing.

## Remaining integration work

The current changes add cone and block-ray guards with a continuous reaction counter for loose-ball pursuit and visible human or bot carriers, limit rotation speed, and only request a pass when all potential recipients are visible. They add weighted offensive preferences, a squad-wide chatter selector, and a short-ray sidestep recovery. The squad now looks up its session by arena, rather than always passing null.

This remains incomplete. Possession decisions still read global ball ownership; off-ball choices and steal outcomes still use arbitrary rolls. The shot host still derives release quality from difficulty and random physics rather than visible timing or footing, and it chooses the recipient itself. The short recovery rays are not full-width pathfinding and can fail to find a legal route. An invalid avatar still respawns with the wrong spawn and stale team ID. Chatter possession boundaries are inferred from team changes and do not distinguish a new same-team possession. The host's steal broadcast is still a system message. Real head-to-head match simulation, collision integration tests and live Paper playtests are required before claiming monotonic difficulty, complete visibility fairness or human indistinguishability.
