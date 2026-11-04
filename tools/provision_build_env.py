#!/usr/bin/env python3
"""
Provisions a fully-local BosketBall (Paper 26.2) build environment with NO GitHub Actions
and NO Maven Central access:

  * Python packages provide a JRE 25 runtime (jdk4py) and an Eclipse JDT installation
    (karellen-jdtls) whose ECJ batch compiler (org.eclipse.jdt.core.compiler.batch)
    compiles Java 21 bytecode.
  * All dependency sources are fetched as codeload.github.com tarballs and compiled
    from source: paper-api 26.2, Adventure 5.2.0 (+examination/option), SnakeYAML, JOML,
    Brigadier, Netty (+JCTools), log4j-api, commons-logging, jspecify/JetBrains/checker/
    error-prone/j2objc annotations.
  * jars bundled inside JDTLS (guava, gson, slf4j, JUnit 4, hamcrest, commons-lang3,
    Eclipse m2e's embedded Maven Resolver) are reused as compile-only classpath entries.
  * A handful of compile-only stubs (tools/build-stubs/) replace artifacts that cannot be
    built from a plain source checkout (fastutil generated classes, Lombok-based
    BungeeCord chat, JCTools VarHandle queues, log4j 1.2, @AutoService). They are never
    packaged: the Paper server supplies the real classes at runtime.

Output: /tmp/tools/dep-classes/* + /tmp/tools/dep-classpath.txt (consumed by build_plugin.py)

Usage:  python3 tools/provision_build_env.py [--force]
"""
import io
import subprocess
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = Path("/tmp/tools")
SRC = TOOLS / "src"
DEP = TOOLS / "dep-classes"
DL = TOOLS / "dl"
STUBS = ROOT / "tools" / "build-stubs"

# (repo, ref, kind) — exact upstream refs matching Paper 26.2's dependency versions.
SOURCES = [
    ("PaperMC/Paper", "26.2", "tags"),
    ("PaperMC/adventure", "v5.2.0", "tags"),
    ("jspecify/jspecify", "v1.0.0", "tags"),
    ("JetBrains/java-annotations", "26.0.2", "tags"),
    ("typetools/checker-framework", "checker-framework-3.49.2", "tags"),
    ("google/error-prone", "v2.42.0", "tags"),
    ("google/j2objc", "master", "heads"),
    ("snakeyaml/snakeyaml", "master", "heads"),
    ("JOML-CI/JOML", "1.10.9", "tags"),
    ("Mojang/brigadier", "master", "heads"),
    ("apache/commons-logging", "rel/commons-logging-1.3.5", "tags"),
    ("apache/logging-log4j2", "rel/2.26.1", "tags"),
    ("KyoriPowered/examination", "v1.3.0", "tags"),
    ("KyoriPowered/option", "v1.1.0", "tags"),
    ("netty/netty", "netty-4.2.20.Final", "tags"),
    ("JCTools/JCTools", "v4.0.7", "tags"),
]
# Snippets of `pip` output are noisy; we only hard-fail on real errors below.
PY_PACKAGES = ["pillow", "numpy", "jdk4py", "karellen-jdtls"]


def run(cmd, **kw):
    print("  $", " ".join(str(c) for c in cmd)[:160])
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def find_java():
    try:
        from jdk4py import JAVA
        return str(JAVA)
    except Exception:
        return None


def find_jdtls_plugins():
    import site, sysconfig
    candidates = []
    for base in {
        sysconfig.get_path("data"),
        site.getusersitepackages().rsplit("/lib/", 1)[0] if "/lib/" in site.getusersitepackages() else "",
        "/usr/local", "/usr",
    }:
        if base:
            candidates.append(Path(base) / "lib" / "karellen-jdtls-kotlin" / "plugins")
    link = TOOLS / "jdtls" / "server" / "plugins"
    if link.exists():
        return link
    for cand in candidates:
        if cand.is_dir() and list(cand.glob("org.eclipse.jdt.core.compiler.batch_*.jar")):
            (TOOLS / "jdtls").mkdir(parents=True, exist_ok=True)
            lnk = TOOLS / "jdtls" / "server"
            if not lnk.exists():
                lnk.symlink_to(cand.parent, target_is_directory=True)
            return cand
    return None


def ensure_python_packages():
    if find_java() and find_jdtls_plugins():
        return
    print("[provision] Installing Python build packages (Pillow, numpy, jdk4py, karellen-jdtls)...")
    res = run([sys.executable, "-m", "pip", "install", "--break-system-packages", "-q", *PY_PACKAGES])
    if res.returncode != 0:
        res = run(["pip", "install", "--break-system-packages", "-q", *PY_PACKAGES])
    if res.returncode != 0:
        print(res.stdout[-2000:], res.stderr[-2000:], file=sys.stderr)
        sys.exit("pip install failed — see output above")
    if not find_java():
        sys.exit("jdk4py installed but JAVA runtime not found?")


def fetch_sources(force=False):
    SRC.mkdir(parents=True, exist_ok=True)
    DL.mkdir(parents=True, exist_ok=True)
    for repo, ref, kind in SOURCES:
        name = repo.replace("/", "-")
        dest = SRC / name
        if dest.exists() and any(dest.iterdir()) and not force:
            print(f"  = {repo} @ {ref} (cached)")
            continue
        url = f"https://codeload.github.com/{repo}/tar.gz/refs/{kind}/{ref}"
        tar_path = DL / f"{name}.tar.gz"
        print(f"  ↓ {repo} @ {ref}")
        req = urllib.request.Request(url, headers={"User-Agent": "bosketball-provision/1.0"})
        with urllib.request.urlopen(req, timeout=600) as resp:
            tar_path.write_bytes(resp.read())
        if dest.exists():
            subprocess.run(["rm", "-rf", str(dest)])
        dest.mkdir(parents=True)
        with tarfile.open(tar_path) as tf:
            for member in tf.getmembers():
                parts = Path(member.name).parts
                if len(parts) <= 1:
                    continue
                member.name = str(Path(*parts[1:]))
                tf.extract(member, dest)


def java_files(root, exclude_names=(), exclude_paths=("module-info.java",)):
    out = []
    for p in sorted(Path(root).rglob("*.java")):
        if p.name in exclude_names or p.name in exclude_paths:
            continue
        if "/svm/" in p.as_posix():
            continue
        out.append(str(p))
    return out


class Compiler:
    def __init__(self, java, ecj_jar):
        self.java = java
        self.ecj = ecj_jar

    def compile(self, out_dir, classpath, files, label, allow_errors_in=()):
        out = Path(out_dir)
        if out.exists():
            subprocess.run(["rm", "-rf", str(out)])
        out.mkdir(parents=True)
        list_file = TOOLS / "ecj-files.txt"
        list_file.write_text("\n".join(files))
        res = subprocess.run(
            [self.java, "-jar", self.ecj, "-21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
             "-cp", classpath, "-d", str(out), f"@{list_file}"],
            capture_output=True, text=True,
        )
        errors = [l for l in (res.stdout + res.stderr).splitlines() if "ERROR in" in l]
        unexpected = [e for e in errors if not any(a in e for a in allow_errors_in)]
        count = len(list(out.rglob("*.class")))
        print(f"  [{label}] {count} classes" + (f"  ({len(unexpected)} unexpected errors)" if unexpected else ""))
        if unexpected:
            print("\n".join(unexpected[:12]), file=sys.stderr)
            sys.exit(f"ECJ compilation failed for {label}")
        return out

    def compile_into(self, out_dir, classpath, files, label):
        out = Path(out_dir)
        out.mkdir(parents=True, exist_ok=True)
        list_file = TOOLS / "ecj-files.txt"
        list_file.write_text("\n".join(files))
        res = subprocess.run(
            [self.java, "-jar", self.ecj, "-21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
             "-cp", classpath, "-d", str(out), f"@{list_file}"],
            capture_output=True, text=True,
        )
        errors = [l for l in (res.stdout + res.stderr).splitlines() if "ERROR in" in l]
        print(f"  [{label}] merged" + (f"  ({len(errors)} errors)" if errors else ""))
        if errors:
            print("\n".join(errors[:12]), file=sys.stderr)
            sys.exit(f"ECJ compilation failed for {label}")


def make_jar(src_dir, jar_path, module_name, exclude_prefix=None):
    jar_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF",
                   f"Manifest-Version: 1.0\r\nAutomatic-Module-Name: {module_name}\r\n\r\n")
        for fp in sorted(Path(src_dir).rglob("*")):
            if not fp.is_file():
                continue
            arc = fp.relative_to(src_dir).as_posix()
            if arc == "module-info.class":
                continue
            if exclude_prefix and arc.startswith(exclude_prefix):
                continue
            z.write(fp, arc)


def main():
    force = "--force" in sys.argv
    if (TOOLS / "dep-classpath.txt").exists() and (DEP / "paper-api").is_dir() and not force:
        print("[provision] dep-classes already provisioned (use --force to rebuild).")
        return

    ensure_python_packages()
    java = find_java()
    plugins = find_jdtls_plugins()
    if not plugins:
        sys.exit("Could not locate karellen-jdtls plugins directory")
    ecj_jar = str(sorted(plugins.glob("org.eclipse.jdt.core.compiler.batch_*.jar"))[-1])
    print(f"[provision] java={java}\n[provision] ecj={ecj_jar}")
    cc = Compiler(java, ecj_jar)

    print("[provision] Fetching dependency sources via codeload.github.com ...")
    fetch_sources(force)

    jars = []
    for pattern in (
        "com.google.guava_*.jar", "com.google.guava.failureaccess_*.jar", "com.google.gson_*.jar",
        "slf4j.api_*.jar", "org.eclipse.jdt.annotation_*.jar", "org.apache.commons.lang3_*.jar",
        "org.eclipse.osgi_*.jar", "org.hamcrest_*.jar", "org.junit_4*.jar",
    ):
        jars += [str(p) for p in plugins.glob(pattern)]
    m2e = sorted(str(p) for p in plugins.glob("org.eclipse.m2e.maven.runtime_*/**/*.jar"))
    cp = ":".join(jars + m2e)

    S = SRC
    D = DEP
    D.mkdir(parents=True, exist_ok=True)

    def add(path):
        nonlocal cp
        cp = cp + ":" + str(path)

    print("[provision] Compiling dependency chain from source ...")
    add(cc.compile(D / "jspecify", cp, java_files(S / "jspecify-jspecify/src/main/java"), "jspecify"))
    add(cc.compile(D / "jetbrains", cp, java_files(S / "JetBrains-java-annotations/src/jvmMain/java"), "jetbrains-annotations"))
    add(cc.compile(D / "checker", cp, java_files(S / "typetools-checker-framework/checker-qual/src/main/java"), "checker-qual"))
    add(cc.compile(D / "j2objc", cp, java_files(S / "google-j2objc/annotations/src/main/java"), "j2objc-annotations"))
    add(cc.compile(D / "errorprone", cp, java_files(S / "google-error-prone/annotations/src/main/java"), "errorprone-annotations"))
    add(cc.compile(D / "snakeyaml", cp, java_files(S / "snakeyaml-snakeyaml/src/main/java"), "snakeyaml"))
    # JvmciCode.java imports GraalVM's jdk.vm.ci (absent from a stock JRE) and the error
    # cascades into MemUtil/Matrix classes that reference it. paper-api only needs JOML's
    # core math types, so tolerate per-unit errors but hard-verify those classes exist.
    joml_out = cc.compile(D / "joml", cp, java_files(S / "JOML-CI-JOML/src/main/java"), "joml",
                          allow_errors_in=("org/joml/",))
    for needed in ("Matrix4f", "Matrix3f", "Vector3f", "Vector4f", "Quaternionf", "AxisAngle4f"):
        if not (joml_out / f"org/joml/{needed}.class").exists():
            sys.exit(f"JOML sanity check failed: org.joml.{needed} was not compiled")
    add(joml_out)
    add(cc.compile(D / "brigadier", cp, java_files(S / "Mojang-brigadier/src/main/java"), "brigadier"))
    # A few peripheral log4j-api units error out (removed @InlineMe annotation, OSGi helpers);
    # only the core Logger API is needed downstream — tolerate & verify.
    log4j_out = cc.compile(D / "log4j", cp, java_files(S / "apache-logging-log4j2/log4j-api/src/main/java",
                                                       exclude_paths=("module-info.java", "package-info.java")),
                           "log4j-api", allow_errors_in=("org/apache/logging/log4j/",))
    for needed in ("Logger", "LogManager", "Level", "Marker"):
        if not (log4j_out / f"org/apache/logging/log4j/{needed}.class").exists():
            sys.exit(f"log4j-api sanity check failed: {needed} was not compiled")
    add(log4j_out)
    # log4j 1.2 stubs first: commons-logging's Log4JLogger compiles against them;
    # its ancient AvalonLogger (org.apache.avalon) is excluded — nothing references it directly.
    add(cc.compile(D / "log4j12", cp, java_files(STUBS / "org/apache"), "log4j12-stubs"))
    # Log4jApiLogFactory (commons-logging 1.3.x) needs log4j-api internals that partially
    # failed above; Netty only needs the classic Log/LogFactory surface — tolerate & verify.
    jcl_out = cc.compile(D / "commons-logging", cp,
                         java_files(S / "apache-commons-logging/src/main/java", exclude_names=("AvalonLogger.java",)),
                         "commons-logging", allow_errors_in=("org/apache/commons/logging/impl/",))
    for needed in ("Log.class", "LogFactory.class", "impl/Log4JLogger.class", "impl/Jdk14Logger.class",
                   "impl/SimpleLog.class", "impl/NoOpLog.class"):
        if not (jcl_out / f"org/apache/commons/logging/{needed}").exists():
            sys.exit(f"commons-logging sanity check failed: {needed} was not compiled")
    add(jcl_out)
    add(cc.compile(D / "examination", cp,
                   java_files(S / "KyoriPowered-examination/api/src/main/java")
                   + java_files(S / "KyoriPowered-examination/string/src/main/java"), "examination"))
    add(cc.compile(D / "option", cp, java_files(S / "KyoriPowered-option/src/main/java"), "option"))
    add(cc.compile(D / "autoservice", cp, [str(STUBS / "com/google/auto/service/AutoService.java")], "autoservice-stub"))

    adv = S / "PaperMC-adventure"
    adv_files = []
    # NOTE: text-serializer-ansi is intentionally skipped — Paper 26.2 does not bundle it
    # (paper-api only shades gson/legacy/plain/minimessage serializers) and its sealed
    # cross-package types would need the same JPMS module treatment as MiniMessage.
    for module in ("api", "key", "nbt", "text-serializer-commons", "text-serializer-gson", "text-serializer-json",
                   "text-serializer-json-legacy-impl", "text-serializer-legacy", "text-serializer-plain",
                   "text-logger-slf4j"):
        adv_files += java_files(adv / module / "src/main/java")
    add(cc.compile(D / "adventure", cp, adv_files, "adventure (sans minimessage)"))

    # MiniMessage uses sealed types across packages -> must be compiled as a JPMS module.
    print("  [minimessage] JPMS module compile ...")
    modjars = TOOLS / "modjars"
    make_jar(D / "adventure", modjars / "adv-api.jar", "net.kyori.adventure.api")
    make_jar(D / "examination", modjars / "examination.jar", "net.kyori.examination.api")
    make_jar(D / "option", modjars / "option.jar", "net.kyori.option")
    make_jar(D / "jspecify", modjars / "jspecify.jar", "org.jspecify")
    make_jar(D / "jetbrains", modjars / "jetbrains.jar", "org.jetbrains.annotations")
    make_jar(D / "errorprone", modjars / "errorprone.jar", "com.google.errorprone.annotations")
    make_jar(D / "checker", modjars / "checker.jar", "org.checkerframework.checker.qual")
    mm_src = TOOLS / "mm-src"
    mm_src.mkdir(parents=True, exist_ok=True)
    (mm_src / "module-info.java").write_text(
        "module net.kyori.adventure.text.minimessage {\n"
        "  requires transitive net.kyori.adventure.api;\n"
        "  requires net.kyori.examination.api;\n"
        "  requires net.kyori.option;\n"
        "  requires static org.jspecify;\n"
        "  requires static org.jetbrains.annotations;\n"
        "  requires static com.google.errorprone.annotations;\n"
        "  requires static org.checkerframework.checker.qual;\n"
        "  exports net.kyori.adventure.text.minimessage;\n"
        "  exports net.kyori.adventure.text.minimessage.tag;\n"
        "  exports net.kyori.adventure.text.minimessage.tag.standard;\n"
        "  exports net.kyori.adventure.text.minimessage.tag.resolver;\n"
        "  exports net.kyori.adventure.text.minimessage.translation;\n"
        "  exports net.kyori.adventure.text.minimessage.tree;\n"
        "  uses net.kyori.adventure.text.minimessage.MiniMessage.Provider;\n"
        "}\n"
    )
    mm_files = [str(mm_src / "module-info.java")] + java_files(adv / "text-minimessage/src/main/java")
    mm_list = TOOLS / "ecj-files.txt"
    mm_list.write_text("\n".join(mm_files))
    mm_out = TOOLS / "mm-out"
    subprocess.run(["rm", "-rf", str(mm_out)])
    mm_out.mkdir(parents=True)
    res = subprocess.run(
        [java, "-jar", ecj_jar, "-21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
         "--module-path", str(modjars), "-d", str(mm_out), f"@{mm_list}"],
        capture_output=True, text=True,
    )
    mm_errors = [l for l in (res.stdout + res.stderr).splitlines() if "ERROR in" in l]
    copied = 0
    for fp in mm_out.rglob("*.class"):
        if fp.name == "module-info.class":
            continue
        target = D / "adventure" / fp.relative_to(mm_out)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(fp.read_bytes())
        copied += 1
    print(f"  [minimessage] {copied} classes merged" + (f"  ({len(mm_errors)} errors)" if mm_errors else ""))
    if mm_errors:
        print("\n".join(mm_errors[:12]), file=sys.stderr)
        sys.exit("MiniMessage module compilation failed")

    add(cc.compile(D / "jctools", cp, java_files(S / "JCTools-JCTools/jctools-core/src/main/java",
                                                  exclude_paths=("module-info.java", "package-info.java")), "jctools"))
    cc.compile_into(D / "jctools", cp, java_files(STUBS / "org/jctools"), "jctools-varhandle-stubs")
    netty_files = []
    for module in ("common", "buffer", "transport", "transport-native-unix-common", "resolver"):
        netty_files += java_files(S / "netty-netty" / module / "src/main/java",
                                  exclude_names=("NetUtilSubstitutions.java", "Hidden.java"))
    add(cc.compile(D / "netty", cp, netty_files, "netty"))
    add(cc.compile(D / "bungee", cp, java_files(STUBS / "net"), "bungee-stubs"))
    add(cc.compile(D / "fastutil", cp, java_files(STUBS / "it"), "fastutil-stubs"))
    add(cc.compile(D / "paper-api", cp,
                   java_files(S / "PaperMC-Paper/paper-api/src/main/java")
                   + java_files(S / "PaperMC-Paper/paper-api/src/generated/java"), "paper-api 26.2"))

    (TOOLS / "dep-classpath.txt").write_text(cp)
    checks = [
        D / "paper-api/org/bukkit/Bukkit.class",
        D / "adventure/net/kyori/adventure/text/minimessage/tree/Node$Root.class",
        D / "adventure/net/kyori/adventure/text/serializer/gson/GsonComponentSerializer.class",
        D / "netty/io/netty/channel/Channel.class",
    ]
    missing = [str(c) for c in checks if not c.exists()]
    if missing:
        sys.exit(f"Provision sanity check failed, missing: {missing}")
    print("[provision] Done — Paper 26.2 + Adventure 5.2 dependency chain ready in /tmp/tools/dep-classes")


if __name__ == "__main__":
    main()
