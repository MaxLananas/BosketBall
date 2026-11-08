# Onboarding research — the first ten minutes on a basketball server

*Community and onboarding designer, BosketBall. Written before the code, kept as the
measurement the implementation is judged against.*

## What the top tier actually does

**Hypixel puts one decision in the hotbar and hides the rest.** On a first join the
player gets a five-item hotbar: compass = game menu, player head = profile, chest =
collectibles, dyes = visibility, nether star = lobby picker. Nothing explains
basketball, monetisation, ranks, quests or guilds on the join screen. The compass is
the first thing held, and right-clicking it is the only thing a new player needs to
discover; everything else is one level deeper, reachable when it becomes relevant.
([Hypixel support, "How to Join the Hypixel Server"](https://support.hypixel.net/hc/en-us/articles/360019495360-How-to-Join-the-Hypixel-Server),
[Hypixel forums, "The Compass and Mini Games Explained"](https://hypixel.net/threads/guide-the-compass-and-mini-games-explained.1981034/),
[Hypixel forums, "Server Navigation for New Members"](https://hypixel.net/threads/guide-hypixel-server-navigation-for-new-members.1437089/)).
A separate "Tutorial" NPC exists in the main lobby, but it is opt-in: the lobby
itself is the tutorial.

**Top-tier lobbies teach by placement.** A game selector is a physical object in the
world (a villager, a sign, a portal), placed on the path from spawn, with the game's
logo behind it. The player walks toward the thing that looks like the game. This is
the same idea level designers call an affordance: colour, light, geometry and
sightlines tell the player what is interactive before any sentence does; immediate
feedback (a glow, a sound, a ring turning green) confirms the action. The canonical
example is Super Mario Bros. 1-1, where an unavoidable enemy teaches jumping without
a line of text. ([Affordances teach mechanics without tutorials](https://salivity.github.io/game-development/article/how-affordances-teach-game-mechanics-without-tutorials),
[Wayline, "Teach Through Play: Ditch the Walls of Text"](https://www.wayline.io/blog/teach-through-play-ditch-the-walls-of-text),
[Level design: wayfinding toolkit](https://lobehub.com/skills/slayumind-agents-skills-level-design)).

**A Discord-style MOTD reads like a place, not a changelog.** The server list row
owns only two short lines — the name above it is the player's own local label — so
the useful content is "what is happening here right now" and "what can I do". MOTD
tools converge on ~45 visible characters per line and two lines total; a hosting
panel caps the description at 150 characters including codes. ([Minecraft Server List
Appearance: MOTD, icon and player count](https://www.gameserverkings.com/knowledge-base/minecraft/server-list-appearance/),
[MOTD Generator FAQ: 45 chars per line](https://xgamingserver.com/tools/minecraft/motd-generator),
[How to change your server icon and MOTD](https://cheapestminecrafthosting.com/guides/minecraft-server-icon-and-motd)).
Event-driven MOTDs are only worth it when they answer "should I join right now?"
(live counts, open courts); a frame-animation or a patch note does not.
([Animated MOTDs and dynamic server list text](https://www.fumora.net/lab/minecraft-motd-animation-tricks)).

## This server, before the change: minute by minute on paper

Measured from `GameplayListener.onJoin`, `TutorialManager.maybeStartOnJoin`, the
eight tutorial chapters, the lobby hotbar and the join cards. "Nothing to click"
means no surface offered a next action that minute.

| Minute | What the server did | What the player could do | Gap |
| --- | --- | --- | --- |
| 0 | Join, permissions, profile, welcome card at +0.6 s, pack prompt at +1 s, tutorial auto-start at +2 s | Read a card; wait | The card is marketing copy ("Queue 1v1–5v5, earn Coins"). No identity, no first action, no held item selected. |
| 1 | Cinematic tutorial intro (110 ticks), teleport to practice court | Watch | Nothing to click for ~5 s, and no fallback if the cinematic is skipped. |
| 2 | Chapter 1 welcome card, then chapters 2–4 (ring, shot, dunk) | Walk into a gold ring; hold right-click | Good: the world teaches. But a player who typed `/tuto stop` got "No tour is running" and nothing else. |
| 3 | Chapters 4–6 (defense, shop, queue) as text cards | Type `/shop`, `/play` | These chapters are pure prose on a practice court where the lobby hotbar is not equipped. The next action is a command from memory. |
| 4 | Watchdog hints after 60 s of inactivity | Wait for a hint | A player who skipped a chapter had no cue for up to a minute. |
| 5 | Tour ends, completion card, teleport to spawn | `/play 1v1`, `/quests`, `/tuto menu` | **The dangerous minute.** Three commands on a card, no selected hotbar slot, no reason to use party/friends/invite/spectate, no clue what a report is for. A first-timer who ignored the card had nothing. |
| 6–7 | First queue / first match | Play | Social surfaces were reachable only by typing `/friend`, `/party`; the empty states said "You are not in a party" — a system describing a state, not a person offering a next step. No two-sided copy on friend requests or invites. |
| 8 | First Coins from a bucket or the tour | Spend in `/shop` | "You have been awarded 250 coins" energy: no card that says what the coins are for, and no fallback if the reward claim was skipped. |
| 9 | Lobby again | Sidebar, holograms, crates | Reporting required knowing `/report <player> <reason>` by heart; staff got a `[OPEN]` button that only opened a panel. |
| 10 | Nothing scheduled | Whatever the player found | No script at all. |

**Where the player had nothing to do:** minute 0 (identity), minute 3 (shop/queue
chapters), minute 5 (post-tour), minute 8 (reward), minute 9 (reporting). The
rejection rule is explicit: *no first-time player may reach minute five with
nothing to click* — and this baseline fails it.

## What the implementation does about it

1. **`OnboardingScript`** is the ten-minute script as data: five beats (join →
   identity → first shot → first match → first reward), contiguous 0–10 minute
   windows, each with a surface, a **visual cue** (held hotbar slot, gold ring, rim
   marker, nameplate), a primary action and a **fallback action for a player who
   skips**. `validate()` refuses a beat with a blank fallback or a broken window.
   `stageFor(progress)` reads real profile state (shot attempts, matches, coins,
   tour flag), so skipping a step never skips the player.
2. **`OnboardingCues`** / the director hold one action-bar line and re-issue it at
   most every 20 s while a stage is unresolved. `RailGuard` yields to a live shot
   clock / HUD toast (`Msg.railOnScreen`) and uses `Msg.rail`, which holds the line
   for 1.6 s so the aim HUD cannot erase it in the same tick. One card per stage
   change, never a second card on top of the join card.
3. **Teaching by design** is the primary channel: the held hotbar slot is the next
   action (`slot 1` play, `slot 2` shop), the practice court ring and rim marker
   already do the shooting chapter, and text is only the fallback.
4. **MOTD / screens** are rewritten to read like a place ("Court's open"), keep the
   `▪` mark, use the single `<#64748B>·</#64748B>` separator, stay inside 45 visible
   characters and 268 px per line, and drop the last French strings from the
   maintenance / restart screens.
5. **`SocialSurfaces`** gives party, friend, invite and spectate an honest empty
   state ("No party yet · bring someone from the court", never a fake count), a
   reason to use it inside a match, and two-sided copy so the actor and the target
   both hear what happened in their own words.
6. **`ReportGate`** makes the report path two clicks (sneak + right-click a player,
   then click a reason) and the staff action one click (`[TAKE]` in the staff
   broadcast). Cooldown, duplicate-target, open-report cap and reason length are
   pure decisions, so spam cannot be filed from an alt-tab macro.

## Copy rules applied to every new line

English only; separator always `<#64748B>·</#64748B>`; no `•`, no `|`, no stars;
`▪` reserved for titles and the MOTD; colours only from `Palette`; one action-bar
line, one title, one card; short and active ("Court's open", "Stuffed · Kobe got a
hand to it", never "Operation failed"). Every action has two participants, and both
get told.
