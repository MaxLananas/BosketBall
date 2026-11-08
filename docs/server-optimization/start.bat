@echo off
rem BosketBall - Windows test launcher (same flags as start.sh, 3.5 GB heap)
cd /d "%~dp0"
java -Xms3584M -Xmx3584M -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:+UseCompactObjectHeaders -XX:ActiveProcessorCount=2 -XX:MaxMetaspaceSize=320M -XX:ReservedCodeCacheSize=192M -XX:MaxDirectMemorySize=512M -XX:+ExitOnOutOfMemoryError -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -Dfile.encoding=UTF-8 -jar server.jar --nogui
pause
