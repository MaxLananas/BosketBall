# BosketBall — Audit #16

Scope: continuation from `e49676d` (`Rend les filtres de succès accessibles`), covering the
remaining asynchronous chat path, the Paper renderer, storage sync on match completion, atomic
YAML writes, resource-pack URL discovery, and untrusted MiniMessage inputs. The review also
rechecked command arguments that flow into shared messages and the remaining locale-sensitive
formatters.

Method: inspected the relevant call paths in the 94-source plugin and Paper 26.2's local
`ChatProcessor` implementation. Changes were made only where the path showed a concrete unsafe
Bukkit access, synchronous database write on the tick thread, unsafe interpolation, or
non-atomic persistence. No hot-path optimization was accepted without before/after measurements.

---

## 1. Confirmed findings fixed

| Area | Evidence | Correction |
|---|---|---|
| `AsyncChatEvent` moderation and gameplay | Paper can post an asynchronous chat event and call its renderer during chat completion without switching to the primary thread. The moderation handler used player/profile/game APIs; the gameplay handler checked permissions and consumed chat prompts. | Both handlers capture the plain message and immutable player UUID, then run player/Bukkit-dependent work using `callSyncMethod(...).get()` only for asynchronous events. Synchronous events keep the direct path. Decisions and message changes are applied back on the event thread. |
| `BosketChatRenderer` | `render()` read player state, permissions, profiles, matches, and guilds, although Paper may call it from the async chat path. | The renderer is `ViewerUnaware`, so Paper renders once per message rather than once per viewer. Its Bukkit-dependent rendering is marshalled to the primary thread; a safe display-name/message fallback is used if scheduling fails. |
| Match-finish database sync | `MatchSession` reaches `IntegrationService.onMatchFinished()` from the gameplay match flow. `StorageService.syncNow()` iterated profiles and called synchronous JDBC `upsertAll()` on the caller, which could stall a server tick. | `IntegrationService` now calls `requestSync()`. The request is queued on the service's single-thread storage executor, serializing it with scheduled storage work; synchronous `syncNow()` is private. The executor also exists when database mirroring is enabled but periodic backups are disabled. |
| Resource-pack IP probe | The public-IP worker called `resolveServerGamePort()`, which reads Bukkit/server state from a raw worker thread. | Resolve and capture the game port before starting the IP-probe thread; the worker uses only the captured value. |
| Resource-pack candidate URLs | Candidate construction could advertise the Minecraft game port even when the HTTP mux was not active. | The game port is included only when the mux is active. |
| Mailbox and warning persistence | Mailbox and warning YAML saves bypassed the atomic writer and wrote directly to the target file. | Both write paths now use `AtomicYamlWriter.writeYamlAtomically`; no direct `yaml.save(file)` remains in these services. |
| Staff moderation GUI text | Report names/reasons, warning reasons/staff names, mailbox subjects, and friend names were interpolated into MiniMessage-backed GUI text without escaping. | Escape those values at the render boundary. `Msg.escapeMini()` now delegates to Adventure's `MiniMessage.escapeTags()`, which the regression test verifies preserves literal text without a click event. |
| Arena identifiers and names | `/bb arena build` accepted a raw ID, and custom-map display names could contain MiniMessage tags. Those values are persisted and later rendered in messages, match cards, and the spectate browser. | `Arena` normalizes IDs to lowercase `[a-z0-9_-]` and stores display names as plain text in both its constructor and setter, covering command-created and YAML-loaded arenas. |
| Guild fields | Guild creation accepted arbitrary tag characters even though tags are interpolated in MiniMessage; guild name/tag/MOTD/description fields loaded from YAML also reached rendered components. | Guild creation validates tags as uppercase ASCII letters/digits. The `Guild` model strips MiniMessage tags, angle delimiters, and control characters from names, tags, MOTDs, and descriptions on construction/set, protecting both new input and persisted values. |
| Locale-dependent output | Sixteen existing `String.format` calls across eleven classes relied on the JVM default locale for timers, hexadecimal tags, JSON escapes, and report output. | Existing format calls now explicitly use `Locale.ROOT`. No new per-tick formatting call or algorithm was introduced. A regression test prevents future default-locale `String.format` calls. |

`AfkListener.onChat()` was reviewed separately: it reads the player's immutable UUID and updates
`SocialService`'s concurrent maps/sets; it does not access world, location, inventory, or other
mutable Bukkit state from the async callback, so it was left unchanged.

---

## 2. Other reviewed paths

- `/msg` builds its body with `Component.text`, so a player's message is literal rather than a
  MiniMessage template.
- Guild MOTD and description input already passes through the service's plain-text path; the
  model now also sanitizes on load/set, including malformed angle delimiters.
- Report reasons and warning data are escaped in staff-facing GUI/render paths. Discord output
  uses its own Markdown/JSON escaping.
- The earlier checks found no config key to remove and no argument interpolation that justified
  a broader change after examining its actual destination. Self-only command error messages
  were not treated as a shared-message vulnerability.
- The reviewed mailbox and warning YAML writes use `AtomicYamlWriter`; their existing load
  paths were not changed. No direct `yaml.save(file)` remains in those services.
- No tick-loop or visual optimization was retained. The locale changes only supply an explicit
  locale to existing formatter calls; they are not an optimization claim.

---

## 3. Regression tests

The suite now includes the following audit coverage (in addition to the existing engine tests):

- `testAsyncChatPlayerOperationsAreMarshalledToMainThread`
- `testMatchFinishDatabaseSyncIsQueuedOffMainThread`
- `testArenaIdentifiersAndDisplayNamesAreMiniMessageSafe`
- `testGuildUserFieldsArePlainAndGuildTagsAreValidated`
- `testStringFormattingUsesRootLocale`
- `testPublicIpProbeUsesMainThreadPortSnapshot`
- `testPackCandidatesDoNotAdvertiseGamePortWithoutMux`
- `testPersistentMailboxAndWarningYamlWritesUseAtomicWriter`
- `testStaffModerationGuisEscapeUntrustedMiniMessageText`
- `testAchievementCategoryButtonsDoNotOverlapPaginationSlots` (committed in `e49676d`)

Some tests assert focused source-level invariants because they protect Bukkit-thread routing and
persistence wiring that cannot be exercised by the standalone engine harness; behavioral tests
cover the actual MiniMessage escaping/sanitization helpers.

---

## 4. Final verification

Two consecutive `python3 tools/build_plugin.py` runs completed successfully with identical
artifacts:

- ECJ: **94 sources, 207 classes, 0 compilation errors**.
- JUnit: **OK (79 tests)** on both runs.
- `BosketBall.jar` and `BosketBall-26.2.jar`: **4,414,741 bytes** each; SHA-256
  `84c8f9bc1c51d04e5c8579a14b5f2a2957479c0c7bbc78b9f5d15ea544809857`.
- Resource pack: **3,588,665 bytes**; SHA-1
  `a2f1a883970ceb11db3164e0a40ce31fee9fe164`; SHA-256
  `29ee51dd46343303f47cb379071f0bd6e3b71b694285b82412aaa77fbe3bbce6`.
- The two release pack ZIPs and the two JARs are byte-identical. The pack SHA-1 matches both
  `.sha1` sidecars, `config.yml`'s `external-sha1`, the JAR's embedded SHA-1, and the actual
  embedded pack bytes.
- `git diff --check` passes.

The Python build prints a pre-existing `.pth` warning (`No module named 'pip'`) while starting,
but both builds exit successfully and finish all compile, test, and packaging steps.

---

## 5. Not verified / live-server follow-up

There is no Minecraft server or client in this environment, so no in-game acceptance test was
possible. In particular, actual chat delivery and database latency were not measured. An async
message may wait for primary-thread work in the moderation handler, gameplay handler, and (when
the custom renderer is enabled) renderer; verify perceived chat latency under a live server load.
Also exercise chat prompts, match/party chat, a guild tag containing MiniMessage syntax, and a
custom arena ID/name before release. Database mirroring should be checked with both SQLite and
MySQL if those backends are deployed.
