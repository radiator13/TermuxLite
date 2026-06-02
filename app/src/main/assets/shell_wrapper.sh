#!/system/bin/sh
# TermuxLite shell: try rish for ADB shell, fallback to system sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RISH="$SCRIPT_DIR/rish"
DEX="$SCRIPT_DIR/rish_shizuku.dex"
HOME_DIR="${HOME:-/data/data/com.termux.lite/files}"

export RISH_APPLICATION_ID="com.termux.lite"

# Ensure DEX not writable (Android 14+)
if [ -f "$DEX" ] && [ -w "$DEX" ]; then
    chmod 400 "$DEX" 2>/dev/null
fi

# Try rish if available
if [ -x "$RISH" ] && [ -f "$DEX" ]; then
    exec "$RISH"
fi

# Fallback: system shell
export TERM=xterm-256color
export PATH=/system/bin:/system/xbin
export TMPDIR="$HOME_DIR/tmp"
cd "$HOME_DIR" 2>/dev/null
exec /system/bin/sh
