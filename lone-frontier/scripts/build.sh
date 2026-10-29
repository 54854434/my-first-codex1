#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_ROOT="$(dirname "$PROJECT_ROOT")"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$TASK_ROOT/tools}}"
BUILD_TOOLS="${FRONTIER_BUILD_TOOLS:-$SDK_ROOT/build-tools/35.0.0}"
ANDROID_JAR="${FRONTIER_ANDROID_JAR:-$SDK_ROOT/platforms/android-35/android.jar}"
if [[ ! -d "$BUILD_TOOLS" && -d "$TASK_ROOT/tools/android-15" ]]; then BUILD_TOOLS="$TASK_ROOT/tools/android-15"; fi
if [[ ! -f "$ANDROID_JAR" && -f "$TASK_ROOT/tools/android-35/android.jar" ]]; then ANDROID_JAR="$TASK_ROOT/tools/android-35/android.jar"; fi
GAME_JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export JAVA_HOME="$GAME_JAVA_HOME"
export PATH="$GAME_JAVA_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$BUILD_TOOLS/lib64:$GAME_JAVA_HOME/lib:$GAME_JAVA_HOME/lib/server:${LD_LIBRARY_PATH:-}"
GAME_KEYSTORE="${FRONTIER_KEYSTORE:-$PROJECT_ROOT/.signing/frontier.jks}"
GAME_OUTPUT="${FRONTIER_OUTPUT:-$PROJECT_ROOT/dist}"
mkdir -p "$PROJECT_ROOT/build/classes" "$PROJECT_ROOT/build/dex" "$GAME_OUTPUT" "$(dirname "$GAME_KEYSTORE")"
node --check "$PROJECT_ROOT/app/src/main/assets/game.js"
node --test "$PROJECT_ROOT/tests/engine.test.cjs"
if command -v javac >/dev/null 2>&1; then
 javac -source 8 -target 8 -Xlint:-options -cp "$ANDROID_JAR" -d "$PROJECT_ROOT/build/classes" "$PROJECT_ROOT/app/src/main/java/com/lonefrontier/game/MainActivity.java"
else
 java -jar "${FRONTIER_ECJ_JAR:-$TASK_ROOT/downloads/ecj.jar}" -8 -nowarn -cp "$ANDROID_JAR" -d "$PROJECT_ROOT/build/classes" "$PROJECT_ROOT/app/src/main/java/com/lonefrontier/game/MainActivity.java"
fi
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$PROJECT_ROOT/build/dex" "$PROJECT_ROOT"/build/classes/com/lonefrontier/game/*.class
"$BUILD_TOOLS/aapt" package -f -M "$PROJECT_ROOT/app/src/main/AndroidManifest.xml" -S "$PROJECT_ROOT/app/src/main/res" -A "$PROJECT_ROOT/app/src/main/assets" -I "$ANDROID_JAR" -F "$PROJECT_ROOT/build/unsigned.apk"
(cd "$PROJECT_ROOT/build/dex" && zip -q -u "$PROJECT_ROOT/build/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$PROJECT_ROOT/build/unsigned.apk" "$PROJECT_ROOT/build/aligned.apk"
if [[ ! -f "$GAME_KEYSTORE" ]]; then
 keytool -genkeypair -keystore "$GAME_KEYSTORE" -alias frontier -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname 'CN=Lone Frontier Personal Game,O=Independent,C=CN' >/dev/null
fi
"$BUILD_TOOLS/apksigner" sign --ks "$GAME_KEYSTORE" --ks-key-alias frontier --ks-pass pass:android --key-pass pass:android --out "$GAME_OUTPUT/LoneFrontier-v1.0.0.apk" "$PROJECT_ROOT/build/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$GAME_OUTPUT/LoneFrontier-v1.0.0.apk"
"$BUILD_TOOLS/zipalign" -c 4 "$GAME_OUTPUT/LoneFrontier-v1.0.0.apk"
"$BUILD_TOOLS/aapt" dump badging "$GAME_OUTPUT/LoneFrontier-v1.0.0.apk"
