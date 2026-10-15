#!/bin/bash
# Builds Crossword.apk with the bare SDK tools (no Gradle).
set -euo pipefail
cd "$(dirname "$0")"

# Paths default to a macOS Android Studio install; override any of them from the environment.
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="${BUILD_TOOLS:-$SDK/build-tools/35.0.0}"
JAR="${ANDROID_JAR:-$SDK/platforms/android-34/android.jar}"
JBR="${JAVA_BIN:-/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin}"
# Signing key. Updates only install over a copy signed with the same key, so keep yours.
# Without KEYSTORE set, a key of your own is made on the first build.
KEY="${KEYSTORE:-$HOME/.android/supernote-nyt.keystore}"
PASS="${KEYSTORE_PASS:-android}"
ALIAS="${KEY_ALIAS:-nyt}"
export PATH="$JBR:$PATH"   # d8 and apksigner call `java` from PATH

rm -rf build && mkdir -p build/gen build/classes

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml --java build/gen build/res.zip

"$JBR/javac" -source 8 -target 8 -bootclasspath "$JAR" -classpath "$JAR" -Xlint:-options \
  -d build/classes build/gen/app/helpyy/nyt/R.java src/app/helpyy/nyt/*.java
"$BT/d8" --lib "$JAR" --min-api 26 --output build $(find build/classes -name '*.class')
(cd build && zip -qj unsigned.apk classes.dex)

[ -f "$KEY" ] || "$JBR/keytool" -genkeypair -keystore "$KEY" -storepass "$PASS" -keypass "$PASS" \
  -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Supernote NYT" >/dev/null 2>&1
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KEY" --ks-pass "pass:$PASS" --out Crossword.apk build/aligned.apk
echo "built $(pwd)/Crossword.apk"
