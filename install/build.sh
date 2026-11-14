#!/bin/bash
# Builds install/site/, the page at ell.ing/nyt-games/: index.html, install.js (Tango bundled) and the APK it installs.
# Run after ../build.sh. Deploy: rsync -a install/site/ helpyy:/var/www/nyt-games/
set -euo pipefail
cd "$(dirname "$0")/src"
[ -d node_modules ] || npm ci --ignore-scripts --no-audit --no-fund
# The Tango library is MIT: its notice rides at the top of the bundle.
npx esbuild install.js --banner:js="/*! NYT Games installer, ell.ing/nyt-games. Uses ya-webadb (Tango): $(sed 's/\*\//* \//g' node_modules/@yume-chan/adb/LICENSE) */" --bundle --minify --format=iife --target=chrome100 --legal-comments=eof --outfile=../site/install.js
cp ../../NYT-Games.apk ../site/NYT-Games.apk
