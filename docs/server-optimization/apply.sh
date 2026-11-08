#!/usr/bin/env bash
# Installs the BosketBall optimisation files into a Paper server directory.
#
#   ./apply.sh /home/container            # applies everything
#   ./apply.sh /home/container --dry-run  # shows what would change, touches nothing
#
# A timestamped backup of every replaced file is written to <server>/pre-opt-<timestamp>/.
set -euo pipefail

SERVER_DIR="${1:-}"
MODE="${2:-}"

if [ -z "$SERVER_DIR" ] || [ ! -d "$SERVER_DIR" ]; then
    echo "usage: $0 <server-directory> [--dry-run]" >&2
    exit 1
fi

SRC="$(cd "$(dirname "$0")" && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$SERVER_DIR/pre-opt-$STAMP"

map_file() {  # source-relative-name  destination-relative-path
    printf '%s|%s\n' "$1" "$2"
}

FILES="$(cat <<'EOF'
server.properties|server.properties
bukkit.yml|bukkit.yml
spigot.yml|spigot.yml
paper-global.yml|config/paper-global.yml
paper-world-defaults.yml|config/paper-world-defaults.yml
EOF
)"

echo "== BosketBall server optimisation =="
echo "server : $SERVER_DIR"
echo "backup : $BACKUP"
[ "$MODE" = "--dry-run" ] && echo "mode   : DRY RUN (nothing is written)"

mkdir -p "$BACKUP"

while IFS='|' read -r name dest; do
    [ -z "$name" ] && continue
    target="$SERVER_DIR/$dest"
    echo "  - $dest"
    if [ "$MODE" = "--dry-run" ]; then
        continue
    fi
    mkdir -p "$(dirname "$target")"
    if [ -f "$target" ]; then
        mkdir -p "$(dirname "$BACKUP/$dest")"
        cp -a "$target" "$BACKUP/$dest"
    fi
    cp -a "$SRC/$name" "$target"
done <<< "$FILES"

if [ "$MODE" != "--dry-run" ]; then
    cp -a "$SRC/start.sh" "$SERVER_DIR/start.sh" 2>/dev/null || true
    chmod +x "$SERVER_DIR/start.sh" 2>/dev/null || true
    mkdir -p "$SERVER_DIR/plugins-disabled"
    for jar in "$SERVER_DIR"/plugins/AxiomPaper*.jar "$SERVER_DIR"/plugins/FastAsyncWorldEdit*.jar; do
        [ -e "$jar" ] || continue
        echo "  - moving $(basename "$jar") to plugins-disabled/"
        mv "$jar" "$SERVER_DIR/plugins-disabled/"
    done
    echo
    echo "Done. Next steps:"
    echo "  1. Update the panel startup command with docs/server-optimization/java-flags.txt"
    echo "  2. Restart the server, then check: /spark tps  and  /spark health"
    echo "  3. Rollback: copy $BACKUP/* back over $SERVER_DIR/"
fi
