#!/usr/bin/env python3
"""Validates the BosketBall server optimisation bundle.

Parses every shipped config and asserts the invariants the optimisation relies on,
so a bad edit can never silently ship (e.g. an aggressive value that disables the
whole feature it was supposed to tune).

    python3 tools/check_server_configs.py

Requires PyYAML (pip install pyyaml). Exits non-zero on the first broken invariant.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUNDLE = ROOT / "docs" / "server-optimization"

FAILURES: list[str] = []
CHECKS = 0


def check(condition: bool, message: str) -> None:
    global CHECKS
    CHECKS += 1
    if not condition:
        FAILURES.append(message)


def load_yaml(name: str) -> dict:
    try:
        import yaml
    except ModuleNotFoundError:  # pragma: no cover - developer machine guard
        print("PyYAML missing: pip install pyyaml", file=sys.stderr)
        raise SystemExit(2)
    path = BUNDLE / name
    check(path.exists(), f"missing file: {name}")
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as handle:
        data = yaml.safe_load(handle)
    check(isinstance(data, dict), f"{name} is not a YAML mapping")
    return data or {}


def get(data: dict, dotted: str, default=None):
    node = data
    for part in dotted.split("."):
        if not isinstance(node, dict) or part not in node:
            return default
        node = node[part]
    return node


# --------------------------------------------------------------------------- properties
def check_properties() -> None:
    path = BUNDLE / "server.properties"
    check(path.exists(), "missing file: server.properties")
    if not path.exists():
        return
    props: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition("=")
        props[key.strip()] = value.strip()

    expected = {
        "view-distance": lambda v: v.isdigit() and int(v) <= 7,
        "simulation-distance": lambda v: v.isdigit() and int(v) <= 5,
        "sync-chunk-writes": lambda v: v == "false",
        "region-file-compression": lambda v: v in {"lz4", "deflate"},
        "max-players": lambda v: v.isdigit() and int(v) >= 50,
        "pause-when-empty-seconds": lambda v: v.isdigit() and int(v) > 0,
        "spawn-protection": lambda v: v.isdigit() and int(v) <= 16,
        "entity-broadcast-range-percentage": lambda v: v.isdigit() and 50 <= int(v) <= 100,
        "generate-structures": lambda v: v == "false",
        "online-mode": lambda v: v == "true",
        "enable-rcon": lambda v: v == "false",
        "management-server-enabled": lambda v: v == "false",
    }
    for key, predicate in expected.items():
        value = props.get(key)
        check(value is not None, f"server.properties: missing key {key}")
        if value is not None:
            check(predicate(value), f"server.properties: {key}={value} is out of range")

    generator = props.get("generator-settings", "")
    check("minecraft" in generator and "the_void" in generator,
          "server.properties: the void generator settings must be kept intact")


# --------------------------------------------------------------------------- bukkit
def check_bukkit() -> None:
    data = load_yaml("bukkit.yml")
    limits = get(data, "spawn-limits", {})
    for key, value in (limits or {}).items():
        check(value == 0, f"bukkit.yml: spawn-limits.{key} should be 0, got {value}")
    check(get(data, "settings.allow-end") is False, "bukkit.yml: allow-end should be false")
    check(get(data, "settings.query-plugins") is False, "bukkit.yml: query-plugins should be false")
    period = get(data, "chunk-gc.period-in-ticks", 0)
    check(isinstance(period, int) and 100 <= period <= 600,
          f"bukkit.yml: chunk-gc.period-in-ticks={period} out of range")


# --------------------------------------------------------------------------- spigot
def check_spigot() -> None:
    data = load_yaml("spigot.yml")
    check(get(data, "settings.netty-threads") in (1, 2),
          "spigot.yml: netty-threads must be 1 or 2 on a 1.5 vCPU host")
    check(get(data, "settings.save-user-cache-on-stop-only") is True,
          "spigot.yml: save-user-cache-on-stop-only should be true")
    check(get(data, "settings.restart-on-crash") is True,
          "spigot.yml: restart-on-crash should stay true")

    world = get(data, "world-settings.default", {}) or {}
    check(world.get("view-distance") == 6, "spigot.yml: default view-distance should be 6")
    check(world.get("simulation-distance") == 4, "spigot.yml: default simulation-distance should be 4")
    check(world.get("mob-spawn-range") in (1, 2, 3), "spigot.yml: mob-spawn-range should be <= 3")
    check(world.get("item-despawn-rate", 99999) <= 1200, "spigot.yml: item-despawn-rate should be <= 1200")
    check(world.get("arrow-despawn-rate", 99999) <= 400, "spigot.yml: arrow-despawn-rate should be <= 400")
    check(world.get("enable-zombie-pigmen-portal-spawns") is False,
          "spigot.yml: zombie pigmen portal spawns should be off")
    check(world.get("nerf-spawner-mobs") is True, "spigot.yml: nerf-spawner-mobs should be true")
    check(world.get("merge-radius", {}).get("item", 0) >= 2.0,
          "spigot.yml: merge-radius.item should be >= 2.0")
    check(world.get("max-tnt-per-tick", 999) <= 32, "spigot.yml: max-tnt-per-tick should be <= 32")

    activation = world.get("entity-activation-range", {}) or {}
    for key in ("animals", "monsters", "misc", "water", "villagers", "flying-monsters"):
        value = activation.get(key, 999)
        check(value <= 32, f"spigot.yml: entity-activation-range.{key}={value} is too wide")

    tracking = world.get("entity-tracking-range", {}) or {}
    for key in ("players", "animals", "monsters", "misc", "display", "other"):
        value = tracking.get(key, 999)
        check(value <= 128, f"spigot.yml: entity-tracking-range.{key}={value} is too wide")

    courts = get(data, "world-settings.bosketball_courts", {}) or {}
    check(courts.get("simulation-distance") == 4,
          "spigot.yml: the courts world needs its own simulation-distance of 4")


# --------------------------------------------------------------------------- paper global
def check_paper_global() -> None:
    data = load_yaml("paper-global.yml")
    check(get(data, "_version") == 31, "paper-global.yml: unexpected _version")
    check(get(data, "misc.compression-level") in (3, 4, 5),
          "paper-global.yml: compression-level should be 3-5 to save CPU")
    check(get(data, "misc.enable-nether") is False, "paper-global.yml: the nether must stay disabled")
    check(get(data, "misc.max-joins-per-tick") in (2, 3, 4, 5),
          "paper-global.yml: max-joins-per-tick should be limited")
    check(get(data, "misc.region-file-cache-size", 999) <= 256,
          "paper-global.yml: region-file-cache-size should stay <= 256")
    check(get(data, "scoreboards.track-plugin-scoreboards") is True,
          "paper-global.yml: track-plugin-scoreboards must be true (the plugin uses sidebars)")
    check(get(data, "collisions.enable-player-collisions") is True,
          "paper-global.yml: player collisions must stay on for basketball body blocking")
    check(get(data, "chunk-loading-basic.player-max-chunk-send-rate", 0) <= 100,
          "paper-global.yml: player-max-chunk-send-rate should stay <= 100")
    check(get(data, "packet-limiter.all-packets.max-packet-rate", 0) >= 500,
          "paper-global.yml: the packet limiter must stay lenient during pack download")
    check(get(data, "update-checker.enabled") is False, "paper-global.yml: update-checker should be off")
    check(get(data, "unsupported-settings.perform-username-validation") is True,
          "paper-global.yml: username validation must stay on (online-mode)")


# --------------------------------------------------------------------------- paper world
def check_paper_world() -> None:
    data = load_yaml("paper-world-defaults.yml")
    check(get(data, "_version") == 31, "paper-world-defaults.yml: unexpected _version")
    limits = get(data, "entities.spawning.spawn-limits", {}) or {}
    for key, value in limits.items():
        check(value == 0, f"paper-world-defaults.yml: spawn-limits.{key} should be 0, got {value}")
    check(get(data, "entities.spawning.per-player-mob-spawns") is False,
          "paper-world-defaults.yml: per-player-mob-spawns should be false")
    check(get(data, "entities.markers.tick") is False,
          "paper-world-defaults.yml: marker entities should not tick")
    check(get(data, "entities.armor-stands.tick") is True,
          "paper-world-defaults.yml: armor stands must keep ticking (plugin rigs)")
    check(get(data, "misc.redstone-implementation") == "ALTERNATE_CURRENT",
          "paper-world-defaults.yml: redstone should use ALTERNATE_CURRENT")
    check(get(data, "misc.update-pathfinding-on-block-update") is False,
          "paper-world-defaults.yml: pathfinding updates should be off (no mobs)")
    check(get(data, "environment.optimize-explosions") is True,
          "paper-world-defaults.yml: optimize-explosions should be on")
    check(get(data, "environment.disable-thunder") is True,
          "paper-world-defaults.yml: thunder should be disabled")
    check(get(data, "environment.disable-ice-and-snow") is True,
          "paper-world-defaults.yml: ice/snow should be disabled")
    check(get(data, "chunks.max-auto-save-chunks-per-tick", 999) <= 8,
          "paper-world-defaults.yml: max-auto-save-chunks-per-tick should be <= 8")
    check(get(data, "collisions.max-entity-collisions", 999) <= 4,
          "paper-world-defaults.yml: max-entity-collisions should be <= 4")
    check(get(data, "unsupported-settings.disable-world-ticking-when-empty") is True,
          "paper-world-defaults.yml: empty worlds must stop ticking")
    check(get(data, "hopper.disable-move-event") is True,
          "paper-world-defaults.yml: hopper move events should be disabled")
    check(get(data, "scoreboards.allow-non-player-entities-on-scoreboards") is False,
          "paper-world-defaults.yml: entity scoreboards should be off")
    ticks = get(data, "environment.max-block-ticks", 0)
    check(isinstance(ticks, int) and 1024 <= ticks <= 65536,
          "paper-world-defaults.yml: max-block-ticks out of range")


# --------------------------------------------------------------------------- JVM flags
def check_flags() -> None:
    path = BUNDLE / "java-flags.txt"
    script = BUNDLE / "start.sh"
    check(path.exists(), "missing file: java-flags.txt")
    check(script.exists(), "missing file: start.sh")
    if not path.exists():
        return
    flags = path.read_text(encoding="utf-8").strip()
    tokens = flags.split()
    check(flags.startswith("java "),
          "java-flags.txt must start with the 'java' executable: panels run it through env, "
          "and a line starting with '-Xms' dies with \"env: unexpected argument '-X' found\"")

    def value_of(prefix: str) -> str | None:
        for token in tokens:
            if token.startswith(prefix):
                return token[len(prefix):]
        return None

    xms, xmx = value_of("-Xms"), value_of("-Xmx")
    check(xms is not None and xmx is not None, "flags: -Xms/-Xmx missing")
    check(xms == xmx, f"flags: -Xms ({xms}) and -Xmx ({xmx}) must be equal")
    if xmx and xmx.endswith("M"):
        check(2560 <= int(xmx[:-1]) <= 4096, f"flags: heap {xmx} is out of the 6 GB container budget")

    required = [
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseCompactObjectHeaders",
        "-XX:ActiveProcessorCount=2",
        "-XX:MaxDirectMemorySize=",
        "-XX:MaxMetaspaceSize=",
        "-XX:+ExitOnOutOfMemoryError",
        "-Dusing.aikars.flags=",
        "-jar server.jar",
        "--nogui",
    ]
    for token in required:
        check(token in flags, f"flags: {token} is missing")

    check("-XX:+ZGenerational" not in flags,
          "flags: -XX:+ZGenerational is obsolete on Java 25 (and refused later)")
    check("-XX:+UseZGC" not in flags,
          "flags: ZGC needs spare cores; G1 is the right choice at ~1.5 vCPU")
    check("-XX:+UseConcMarkSweepGC" not in flags, "flags: CMS was removed from the JVM")

    if script.exists():
        body = script.read_text(encoding="utf-8")
        for token in required:
            check(token in body, f"start.sh: {token} is missing")
        check("cd \"$(dirname \"$0\")\"" in body, "start.sh: must cd into its own directory")


def check_json_and_yaml_syntax() -> None:
    """Every shipped JSON and YAML file must parse: a single bad comma breaks the pack silently."""
    import json

    try:
        import yaml
    except ImportError:  # pragma: no cover - documented dependency
        return

    skip = {".git", "build", "releases", "node_modules", "target"}
    json_files = 0
    yaml_files = 0
    broken: list[str] = []
    for path in ROOT.rglob("*.json"):
        if skip & set(path.parts):
            continue
        json_files += 1
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001 - report anything malformed
            broken.append(f"{path.relative_to(ROOT)}: {str(exc).splitlines()[0][:80]}")
    for path in ROOT.rglob("*.yml"):
        if skip & set(path.parts):
            continue
        yaml_files += 1
        try:
            list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
        except Exception as exc:  # noqa: BLE001
            broken.append(f"{path.relative_to(ROOT)}: {str(exc).splitlines()[0][:80]}")
    check(json_files > 500, f"json: only {json_files} files scanned")
    check(yaml_files >= 6, f"yaml: only {yaml_files} files scanned")
    check(not broken, "syntax: " + "; ".join(broken[:5]))


def main() -> int:
    check_properties()
    check_bukkit()
    check_spigot()
    check_paper_global()
    check_paper_world()
    check_flags()
    check_json_and_yaml_syntax()

    if FAILURES:
        print(f"FAILED - {len(FAILURES)} problem(s) out of {CHECKS} checks:")
        for failure in FAILURES:
            print("  x " + failure)
        return 1
    print(f"OK - {CHECKS} checks passed across 7 files + every shipped JSON/YAML")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
