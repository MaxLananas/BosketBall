# Compile-only stubs (local sandbox build)

These tiny sources exist **only** so the plugin can be compiled in a fully offline /
sandboxed environment where Maven Central is unreachable (see `tools/provision_build_env.py`).

- `it/unimi/dsi/fastutil/...` — paper-api references two fastutil collections; fastutil's
  GitHub repo only ships generating templates, so we stub the exact used surface.
- `net/md_5/bungee/...` — paper-api references two BungeeCord chat types; the real module
  needs Lombok code generation, so we stub the exact used API surface.
- `org/apache/log4j/...` — log4j 1.2 API surface used by Netty's optional logger.
- `org/jctools/queues/varhandle/...` — JCTools VarHandle queues are build-generated upstream;
  these subclass the equivalent Unsafe queues (identical public API).
- `com/google/auto/service/AutoService.java` — annotation used by Adventure's Gson serializer.

**None of these classes are ever packaged into BosketBall.jar** — at runtime the Paper 26.2
server provides the real implementations. The plugin itself never calls them.
