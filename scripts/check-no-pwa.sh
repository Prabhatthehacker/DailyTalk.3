#!/usr/bin/env bash
# Fails the build if the website contains anything that would make it an installable PWA.
set -euo pipefail
dir="${1:-web/dist}"
fail=0
for f in manifest.webmanifest manifest.json sw.js service-worker.js registerSW.js workbox-*.js; do
  if compgen -G "$dir/$f" > /dev/null; then echo "PWA artefact found: $f"; fail=1; fi
done
if grep -rIlE 'rel="manifest"|serviceWorker\.register|beforeinstallprompt' "$dir" >/dev/null 2>&1; then
  echo "PWA code found in build output:"; grep -rIlE 'rel="manifest"|serviceWorker\.register|beforeinstallprompt' "$dir"; fail=1
fi
[ "$fail" -eq 0 ] && echo "OK: no PWA files or install hooks in $dir"
exit "$fail"
