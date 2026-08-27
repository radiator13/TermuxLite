#!/usr/bin/env bash
# ==============================================================================
# sync-upstream.sh
# Synchronizes the terminal-emulator component from official upstream Termux
# (or a custom fork) directly into the TermuxLite in-tree source tree.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

UPSTREAM_REPO="${1:-https://github.com/termux/termux-app.git}"
UPSTREAM_BRANCH="${2:-master}"

echo "🔄 Syncing terminal-emulator from: $UPSTREAM_REPO (branch: $UPSTREAM_BRANCH)"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "⬇️  Cloning shallow upstream repository..."
git clone --depth 1 --branch "$UPSTREAM_BRANCH" "$UPSTREAM_REPO" "$TMP_DIR/termux-app"

EMULATOR_SRC="$TMP_DIR/termux-app/terminal-emulator"

if [[ ! -d "$EMULATOR_SRC" ]]; then
    echo "❌ Error: Could not find terminal-emulator module in upstream repository." >&2
    exit 1
fi

echo "📦 Copying Java source files to app/src/main/java/com/termux/terminal/..."
mkdir -p "$REPO_ROOT/app/src/main/java/com/termux/terminal"
cp -r "$EMULATOR_SRC/src/main/java/com/termux/terminal/"* "$REPO_ROOT/app/src/main/java/com/termux/terminal/"

echo "🧪 Copying unit tests to app/src/test/java/com/termux/terminal/..."
mkdir -p "$REPO_ROOT/app/src/test/java/com/termux/terminal"
cp -r "$EMULATOR_SRC/src/test/java/com/termux/terminal/"* "$REPO_ROOT/app/src/test/java/com/termux/terminal/"

echo "⚙️  Copying C JNI source to app/src/main/jni/termux.c..."
mkdir -p "$REPO_ROOT/app/src/main/jni"
cp "$EMULATOR_SRC/src/main/jni/termux.c" "$REPO_ROOT/app/src/main/jni/termux.c"

echo "📊 Updating SLOC and README..."
python3 "$REPO_ROOT/scripts/track-sloc.py" --update-readme

echo "✅ Upstream sync completed successfully!"
