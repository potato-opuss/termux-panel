#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
DEFAULT_JDK="/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk"

if [ ! -d "$SDK" ]; then
    echo "Android SDK bulunamadi: $SDK" >&2
    echo "ANDROID_SDK_ROOT ortam degiskenini SDK dizinine ayarlayin." >&2
    exit 1
fi

printf 'sdk.dir=%s\n' "$SDK" > local.properties

if [ -z "${JAVA_HOME:-}" ] && [ -x "$DEFAULT_JDK/bin/java" ]; then
    export JAVA_HOME="$DEFAULT_JDK"
fi

GRADLE_ARGS=(assembleDebug --no-daemon --console=plain)

AAPT2="$SDK/build-tools/36.0.0/aapt2"
if [ ! -x "$AAPT2" ]; then
    AAPT2="$SDK/build-tools/35.0.0/aapt2"
fi
if [ -x "$AAPT2" ]; then
    GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=$AAPT2")
fi

echo "==> Debug APK derleniyor"
./gradlew "${GRADLE_ARGS[@]}"

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
    echo "APK olusturulamadi: $APK" >&2
    exit 1
fi

mkdir -p "$ROOT/dist"
cp "$APK" "$ROOT/dist/TermuxPanel-debug.apk"

echo
echo "APK hazir: $ROOT/dist/TermuxPanel-debug.apk"
ls -lh "$ROOT/dist/TermuxPanel-debug.apk"
