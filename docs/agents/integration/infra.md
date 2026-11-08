# Infrastructure integration and remaining validation

## Tick samples

Start the server with `-Dbosketball.debug.tick-budget=true` and keep `/bb profiler` enabled. Every 1,200 plugin scheduler ticks, the console reports per-tick rolling p50, p99, maximum, count of samples at or above 50 ms and sample count (nearest rank, last 1,024 ticks). All sessions and all invocations in the same plugin tick are aggregated before sampling; after a category is first observed, idle plugin ticks contribute zero. An unobserved category is explicitly reported as `not sampled`.

* `match` is the inclusive scheduled game-manager step. `bots` includes squad and sparring bot work inside sessions. `hud` includes measured bossbar calls, shot-charge/aim processing, court displays and jumbotron, plus sidebar and tab-list steps. HUD and bots overlap match, so these are not additive totals. They are targeted measurements, not a measurement of every possible HUD update triggered by commands, events, or other scheduler tasks.
* `schematic` is the inclusive pool step, including queue bookkeeping and the idle watchdog when it runs. It is not the cost of block placement alone.
* These are durations of the plugin's scheduled work, not full Paper MSPT. They exclude most server work, unrelated plugins, GC pauses outside the step and event work between invocations. Sampling and logging add their own small overhead. The debug flag is off by default.

## Paste and assets

Staff can use `/bb arena schematic cancel <court-id>` and `resume <court-id>` (permission `bosketball.admin.arena`); repaste-all also resumes cancelled slots. Cancellation preserves the exact in-memory pass/cursor, removes the job from the queue, and keeps the court unavailable. Partial blocks remain in the world. Progress is not persisted across server restarts. The tests cover cursor preservation and mock-world main, attachable, repair and block-entity slices; real Paper cancellation, unload/reload, restart, lighting, block-entity and entity side effects remain unverified. The 4,000-index cap is not an MSPT guarantee: bootstrap, final display creation, chunk loads, cleanup and lighting are not bounded by it.

`icon_fire` now points at the existing fire font texture; no artwork was redrawn. The stale `locker_hud_*` uniform providers belonged to the removed 3D locker, and the uniform font's custom providers were brought back in sync with the existing default font. The checker covers direct icon calls, known literal item-ID families (including `Achievement` values), derived ball mannequin names, item/model JSON, and bitmap font references. It is not a Java data-flow analyser: future dynamically generated IDs or references outside those patterns still require explicit coverage.

## Remaining release blockers

* Real Paper 26.2 validation with 16 players and spark is still required. Run active matches with bots, frequent HUD changes, concurrent staged pastes and a restart/cancel/resume cycle; record p50/p99, max, full server MSPT and missed 50 ms ticks. No representative server/client workload was available in this environment.
* Config type checks and 114 numeric ranges plus selected strings, lists, enums, dates and cross-field checks are present, but a complete semantic audit and a one-line comment for **every** key have not been proven. Historical pre-v7 numeric clamping remains for migration compatibility; certain legacy paste-budget values are still automatically capped. Runtime-dependent constraints such as actual world heights and schematic dimensions need server validation.
* Pack hash changed to `e98cc48a3f67001a251daf6639cabc982c2e9b2f`. The generated mirror URLs reference `pack-e98cc48a`; publish that tag according to the release process before relying on the CDN mirrors. The build does not publish it.
