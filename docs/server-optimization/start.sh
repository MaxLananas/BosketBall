#!/usr/bin/env sh
# BosketBall server launcher - Paper 26.2 / Java 25
# Target: 6 GB RAM container, ~1.5 dedicated vCPU, 50+ concurrent players.
#
# Heap budget on a 6 GB container:
#   3584 MB heap + 512 MB direct + 320 MB metaspace + 192 MB code cache  ~= 4.6 GB
#   -> ~1.4 GB left for the kernel page cache (chunk files) and thread stacks.
# If the panel ever reports an OOM kill, lower -Xms/-Xmx to 3072M (keep them equal).
set -eu
cd "$(dirname "$0")"

exec java \
  -Xms3584M -Xmx3584M \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -XX:+UseCompactObjectHeaders \
  -XX:ActiveProcessorCount=2 \
  -XX:MaxMetaspaceSize=320M \
  -XX:ReservedCodeCacheSize=192M \
  -XX:MaxDirectMemorySize=512M \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+OmitStackTraceInFastThrow \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -Dterminal.jline=false \
  -Dterminal.ansi=true \
  -Djava.awt.headless=true \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=UTC \
  --enable-native-access=ALLOW \
  --add-modules=jdk.incubator.vector \
  -jar server.jar --nogui "$@"
