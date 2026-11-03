#!/usr/bin/env python3
"""
Builds BosketBall for Minecraft 26.2 (Paper 26.2 + Adventure 5.2.0) — 100% locally,
no GitHub Actions, no Maven Central. Produces:
  * releases/BosketBall.jar (+ identical releases/BosketBall-26.2.jar)
  * releases/BosketBall-ResourcePack.zip (+ -26.2.zip + .sha1 files, config.yml SHA-synced)
and runs the JUnit 4 verification suite.

Prerequisite (once):  python3 tools/provision_build_env.py
  (auto-provisions: JRE 25 + Eclipse ECJ via pip, and compiles the whole paper-api 26.2
   dependency chain from GitHub source tarballs into /tmp/tools/dep-classes)
If the toolchain is missing, this script runs the provisioning for you.

Pipeline (full art rebuild, optional):  python3 tools/generate_resourcepack.py
"""
import hashlib
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS_TMP = Path("/tmp/tools")
BUILD_DIR = Path("/tmp/bosketball_build")


def locate_toolchain():
    try:
        from jdk4py import JAVA
        java = str(JAVA)
    except Exception:
        java = None
    candidates = [
        TOOLS_TMP / "jdtls" / "server" / "plugins",
        Path("/usr/local/lib/karellen-jdtls-kotlin/plugins"),
    ]
    ecj = None
    for plugins in candidates:
        hits = sorted(plugins.glob("org.eclipse.jdt.core.compiler.batch_*.jar")) if plugins.is_dir() else []
        if hits:
            ecj = str(hits[-1])
            break
    cp_file = TOOLS_TMP / "dep-classpath.txt"
    classpath = cp_file.read_text().strip() if cp_file.exists() else None
    return java, ecj, classpath


def ecj_compile(java, ecj, out_dir, classpath, sources):
    out_dir.mkdir(parents=True, exist_ok=True)
    list_file = BUILD_DIR / "sources.txt"
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    list_file.write_text("\n".join(str(s) for s in sources))
    res = subprocess.run(
        [java, "-jar", ecj, "-21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
         "-cp", classpath, "-d", str(out_dir), f"@{list_file}"],
        capture_output=True, text=True,
    )
    if res.returncode != 0:
        print(res.stdout)
        print(res.stderr, file=sys.stderr)
        sys.exit(res.returncode)


def repack_resource_pack():
    """Repackages resourcepack/ into the release zips and syncs config.yml's SHA-1 to match
    the exact bytes that will be embedded into the plugin JAR."""
    rp_dir = ROOT / "resourcepack"
    releases = ROOT / "releases"
    releases.mkdir(parents=True, exist_ok=True)
    zip_path = releases / "BosketBall-ResourcePack.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for fp in sorted(rp_dir.rglob("*")):
            if fp.is_file() and "blockbench" not in fp.parts:
                zf.write(fp, fp.relative_to(rp_dir).as_posix())
    sha1 = hashlib.sha1(zip_path.read_bytes()).hexdigest()
    (releases / "BosketBall-ResourcePack.zip.sha1").write_text(sha1 + "\n")
    shutil.copyfile(zip_path, releases / "BosketBall-ResourcePack-26.2.zip")
    (releases / "BosketBall-ResourcePack-26.2.zip.sha1").write_text(sha1 + "\n")
    cfg = ROOT / "src/main/resources/config.yml"
    text = cfg.read_text()
    text, n = re.subn(r'external-sha1: "[0-9a-fA-F]{40}"', f'external-sha1: "{sha1}"', text)
    if n:
        cfg.write_text(text)
    print(f"      ✓ Resource pack: {zip_path.stat().st_size:,} bytes, SHA-1 {sha1} (config.yml synced)")
    return zip_path, sha1


def main():
    java, ecj, classpath = locate_toolchain()
    if not (java and ecj and classpath):
        print("[build] Local toolchain not provisioned yet — running tools/provision_build_env.py ...")
        res = subprocess.run([sys.executable, str(ROOT / "tools" / "provision_build_env.py")])
        if res.returncode != 0:
            sys.exit(res.returncode)
        java, ecj, classpath = locate_toolchain()
        if not (java and ecj and classpath):
            sys.exit("Toolchain still incomplete after provisioning (need jdk4py, karellen-jdtls, /tmp/tools/dep-classes)")

    if BUILD_DIR.exists():
        shutil.rmtree(BUILD_DIR)
    main_out = BUILD_DIR / "classes"
    test_out = BUILD_DIR / "test-classes"
    main_out.mkdir(parents=True)
    test_out.mkdir(parents=True)

    java_sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    print(f"[1/5] Compiling {len(java_sources)} plugin sources (Java 21 bytecode, Paper 26.2 only)...")
    ecj_compile(java, ecj, main_out, classpath, java_sources)
    print(f"      ✓ Plugin compiled cleanly ({len(list(main_out.rglob('*.class')))} classes, 0 errors)!")

    print("[2/5] Repackaging official resource pack (SHA-1 synced into config.yml)...")
    pack_zip, _sha1 = repack_resource_pack()

    # Compile & run the test suite BEFORE packaging the JAR so a broken build never ships.
    test_sources = sorted((ROOT / "src/test/java").rglob("*.java"))
    print(f"[3/5] Compiling {len(test_sources)} JUnit test suite files...")
    ecj_compile(java, ecj, test_out, classpath + ":" + str(main_out), test_sources)

    print("[4/5] Running JUnit 4 verification suite...")
    run_cp = ":".join([classpath, str(main_out), str(test_out), str(ROOT / "src/main/resources")])
    res_run = subprocess.run(
        [java, "-cp", run_cp, "org.junit.runner.JUnitCore", "fr.bosketball.BosketBallEngineTest"],
        cwd=str(ROOT), capture_output=True, text=True,
    )
    print(res_run.stdout.strip())
    if res_run.returncode != 0:
        print(res_run.stderr, file=sys.stderr)
        sys.exit(res_run.returncode)

    jar_path = ROOT / "releases" / "BosketBall.jar"
    versioned_jar_path = ROOT / "releases" / "BosketBall-26.2.jar"
    print(f"[5/5] Packaging {jar_path.relative_to(ROOT)} (with embedded BosketBall-ResourcePack.zip)...")
    pack_sha1_file = ROOT / "releases" / "BosketBall-ResourcePack.zip.sha1"
    with zipfile.ZipFile(jar_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nCreated-By: BosketBall Builder\r\n\r\n")
        for cls in sorted(main_out.rglob("*.class")):
            zf.write(cls, cls.relative_to(main_out).as_posix())
        res_root = ROOT / "src/main/resources"
        for res_file in sorted(res_root.rglob("*")):
            if res_file.is_file():
                zf.write(res_file, res_file.relative_to(res_root).as_posix())
        zf.write(pack_zip, "META-INF/BosketBall-ResourcePack.zip", compress_type=zipfile.ZIP_STORED)
        if pack_sha1_file.exists():
            zf.write(pack_sha1_file, "META-INF/BosketBall-ResourcePack.sha1")
    shutil.copy2(jar_path, versioned_jar_path)
    print(f"      ✓ Packaged {jar_path.name} & {versioned_jar_path.name} ({jar_path.stat().st_size:,} bytes)! ")


if __name__ == "__main__":
    main()
