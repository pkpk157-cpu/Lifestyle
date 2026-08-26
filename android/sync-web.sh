#!/usr/bin/env bash
# Copy the web app into the Android project's assets.
# Run this after every change to index.html, then rebuild the APK.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
DEST="$HERE/app/src/main/assets/web"

mkdir -p "$DEST"
rm -f "$DEST"/*

for f in index.html manifest.json icon-192.png icon-512.png icon-maskable-512.png; do
  if [ -f "$ROOT/$f" ]; then
    cp "$ROOT/$f" "$DEST/$f"
    echo "  + $f"
  fi
done

# The service worker is for the PWA build. Inside the WebView the page is
# already local, so registering it only adds a stale-cache failure mode.
echo "  (sw.js deliberately not copied - see README)"
echo "Synced web assets into $DEST"
