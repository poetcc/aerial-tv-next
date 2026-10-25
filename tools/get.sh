#!/usr/bin/env bash
# One-line installer for people without a checkout: fetches tools/install.sh (and the TCL helper)
# and runs it with the APK from the latest GitHub release.
#
#   curl -fsSL https://raw.githubusercontent.com/poetcc/aerial-tv-next/main/tools/get.sh | TV=<tv-ip> bash
#   curl -fsSL .../get.sh | KEEP_ADB=1 TV=<tv-ip> bash          # TVs that switch ADB off (Xiaomi)
#   curl -fsSL .../get.sh | TV=<tv-ip> bash -s guard            # firmwares that refuse launchers
#   curl -fsSL .../get.sh | TV=<tv-ip> bash -s restore          # give Home back to the TV's launcher
#
# Needs curl, unzip and python3 (TCL only). If adb is not installed, Google's platform-tools are
# downloaded into ~/.cache/aerial and used from there.
set -euo pipefail
: "${TV:?set TV to the TV IP address, e.g. curl ... | TV=192.168.1.20 bash}"
REPO="${AERIAL_REPO:-poetcc/aerial-tv-next}"
REF="${AERIAL_REF:-main}"
RAW="https://raw.githubusercontent.com/$REPO/$REF"

if ! command -v adb >/dev/null; then
  case "$(uname -s)" in
    Darwin) os=darwin ;;
    Linux) os=linux ;;
    *) echo "No adb found. Install Android platform-tools, then run again." >&2; exit 1 ;;
  esac
  cache="${XDG_CACHE_HOME:-$HOME/.cache}/aerial"
  if [ ! -x "$cache/platform-tools/adb" ]; then
    echo "adb not found; downloading Android platform-tools into $cache"
    mkdir -p "$cache"
    curl -fL --progress-bar -o "$cache/platform-tools.zip" "https://dl.google.com/android/repository/platform-tools-latest-$os.zip"
    unzip -q -o "$cache/platform-tools.zip" -d "$cache"
    rm -f "$cache/platform-tools.zip"
  fi
  export PATH="$cache/platform-tools:$PATH"
fi

dir=$(mktemp -d)
trap 'rm -rf "$dir"' EXIT
mkdir -p "$dir/tools"
for f in install.sh restore.sh tcast-push.py; do
  curl -fsSL "$RAW/tools/$f" -o "$dir/tools/$f"
done
chmod +x "$dir/tools/"*.sh "$dir/tools/tcast-push.py"

if [ "${1:-}" = restore ]; then
  shift
  "$dir/tools/restore.sh" "$@"
else
  "$dir/tools/install.sh" "$@"
fi
