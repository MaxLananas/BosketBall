#!/usr/bin/env python3
"""
Builds BosketBall 26.2 (Java 25) against Paper 26.2 + Adventure 5.2.0,
packages releases/BosketBall-26.2.jar, and executes the JUnit 4 verification suite.
"""
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = "/tmp/tools/jdk25_raw/jdk4py/java-runtime/bin/java"
JDTLS_PLUGINS = Path("/tmp/tools/jdtls/server/plugins")
ECJ = str(next(JDTLS_PLUGINS.glob("org.eclipse.jdt.core.compiler.batch_*.jar")))
PAPER_API = "/tmp/tools/paper-api-26.2-all.jar"
M2E_JARS = Path("/tmp/tools/m2e_jars/jars")


def build_classpath(*extra_dirs) -> str:
    entries = [PAPER_API]
    for p in JDTLS_PLUGINS.glob("*.jar"):
        if any(k in p.name for k in ("guava", "failureaccess", "gson", "slf4j", "commons.lang3", "annotations", "junit_4", "hamcrest")):
            entries.append(str(p))
    for p in M2E_JARS.glob("*.jar"):
        entries.append(str(p))
    entries.extend(str(d) for d in extra_dirs)
    return ":".join(entries)


def main():
    main_out = Path("/tmp/bosketball_build/classes")
    test_out = Path("/tmp/bosketball_build/test-classes")
    if main_out.parent.exists():
        shutil.rmtree(main_out.parent)
    main_out.mkdir(parents=True)
    test_out.mkdir(parents=True)

    java_sources = sorted(str(p) for p in (ROOT / "src/main/java").rglob("*.java"))
    print(f"[1/4] Compiling {len(java_sources)} Java plugin source files (Java 21 bytecode for Paper 1.21.10..26.2)...")
    cp = build_classpath()
    cmd = [
        JAVA, "-jar", ECJ,
        "-21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
        "-cp", cp,
        "-d", str(main_out),
        *java_sources,
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(res.stdout)
        print(res.stderr, file=sys.stderr)
        sys.exit(res.returncode)

    # Also cross-check against Paper 1.21.10 API if present
    paper_12110 = Path("/tmp/tools/paper-api-1.21.10-all.jar")
    if paper_12110.exists():
        check_out = Path("/tmp/bosketball_build/classes-12110")
        check_out.mkdir(parents=True, exist_ok=True)
        cp_12110 = cp.replace(PAPER_API, str(paper_12110), 1)
        res_12110 = subprocess.run([
            JAVA, "-jar", ECJ,
            "-21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
            "-cp", cp_12110,
            "-d", str(check_out),
            *java_sources,
        ], capture_output=True, text=True)
        if res_12110.returncode != 0:
            print(res_12110.stdout)
            print(res_12110.stderr, file=sys.stderr)
            sys.exit(res_12110.returncode)
        print("      ✓ Verified 0 errors on BOTH Paper 1.21.10 (Adventure 4.25) & Paper 26.2 (Adventure 5.2)!")
    else:
        print("      ✓ Main plugin sources compiled cleanly (Java 21 bytecode, 0 errors)!")

    # Package releases/BosketBall.jar + releases/BosketBall-26.2.jar (drop-in upgrade compatibility)
    releases_dir = ROOT / "releases"
    releases_dir.mkdir(parents=True, exist_ok=True)
    jar_path = releases_dir / "BosketBall.jar"
    legacy_jar_path = releases_dir / "BosketBall-26.2.jar"
    print(f"[2/4] Packaging {jar_path.relative_to(ROOT)} (with embedded BosketBall-ResourcePack.zip)...")
    pack_zip = releases_dir / "BosketBall-ResourcePack.zip"
    pack_sha1_file = releases_dir / "BosketBall-ResourcePack.zip.sha1"
    with zipfile.ZipFile(jar_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nCreated-By: BosketBall Builder\r\n\r\n")
        for cls in sorted(main_out.rglob("*.class")):
            zf.write(cls, cls.relative_to(main_out).as_posix())
        res_root = ROOT / "src/main/resources"
        for res_file in sorted(res_root.rglob("*")):
            if res_file.is_file():
                zf.write(res_file, res_file.relative_to(res_root).as_posix())
        if pack_zip.exists():
            zf.write(pack_zip, "META-INF/BosketBall-ResourcePack.zip", compress_type=zipfile.ZIP_STORED)
        if pack_sha1_file.exists():
            zf.write(pack_sha1_file, "META-INF/BosketBall-ResourcePack.sha1")
    shutil.copy2(jar_path, legacy_jar_path)
    print(f"      ✓ Packaged {jar_path.name} & {legacy_jar_path.name} ({jar_path.stat().st_size:,} bytes)!")

    # Compile & run JUnit 4 tests
    test_sources = sorted(str(p) for p in (ROOT / "src/test/java").rglob("*.java"))
    print(f"[3/4] Compiling {len(test_sources)} JUnit test suite files...")
    test_cp = build_classpath(main_out)
    res_test_c = subprocess.run([
        JAVA, "-jar", ECJ,
        "-21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
        "-cp", test_cp,
        "-d", str(test_out),
        *test_sources,
    ], capture_output=True, text=True)
    if res_test_c.returncode != 0:
        print(res_test_c.stdout)
        print(res_test_c.stderr, file=sys.stderr)
        sys.exit(res_test_c.returncode)

    print("[4/4] Running JUnit 4 verification suite on OpenJDK 25.0.2...")
    run_cp = build_classpath(main_out, ROOT / "src/main/resources", test_out)
    res_run = subprocess.run([
        JAVA, "-cp", run_cp,
        "org.junit.runner.JUnitCore",
        "fr.bosketball.BosketBallEngineTest",
    ], cwd=str(ROOT), capture_output=True, text=True)
    print(res_run.stdout.strip())
    if res_run.returncode != 0:
        print(res_run.stderr, file=sys.stderr)
        sys.exit(res_run.returncode)


if __name__ == "__main__":
    main()
