#!/usr/bin/env bash
# Reusable local toolchain; no sudo and no changes to the BLE lab or its emulator SDK.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
flavor="${1:-core}"
case "$flavor" in core) task=assembleCoreDebug;; gemma) task=assembleGemmaDebug;; *) echo 'Usage: scripts/build.sh [core|gemma]' >&2; exit 2;; esac
mkdir -p .tools
export GRADLE_USER_HOME="$PWD/.tools/gradle-home"
if [[ -n "${JAVA_HOME:-}" ]]; then
    if [[ ! -x "$JAVA_HOME/bin/java" || ! -x "$JAVA_HOME/bin/javac" ]]; then echo 'JAVA_HOME must point to a JDK with bin/java and bin/javac.' >&2; exit 1; fi
    export PATH="$JAVA_HOME/bin:$PATH"
elif [[ -x .tools/jdk/bin/java ]]; then export JAVA_HOME="$PWD/.tools/jdk"; export PATH="$JAVA_HOME/bin:$PATH"; fi
java_output="$(java -version 2>&1 || true)"
java_major="$(python3 -c 'import sys,re; m=re.search(r"version \"(\d+)",sys.argv[1]); print(m.group(1) if m else 0)' "$java_output")"
if [[ "$java_major" -lt 17 || "$java_major" -gt 23 ]]; then
    echo 'Preparing an isolated JDK 21 for Gradle 8.13 (Temurin, with Corretto fallback)…'
    python3 scripts/toolchain.py jdk
    export JAVA_HOME="$PWD/.tools/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ ! -x .tools/gradle-8.13/bin/gradle ]]; then python3 scripts/toolchain.py gradle; fi
# An explicitly supplied SDK remains supported. Otherwise use this app's private SDK.
export ANDROID_HOME="${ANDROID_HOME:-$PWD/.tools/sdk}"
sdkmanager=""
if [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager";
elif command -v sdkmanager >/dev/null; then sdkmanager="$(command -v sdkmanager)";
elif [[ -x ../analysis/emulator_lab/.lab/sdk/cmdline-tools/lab/bin/sdkmanager ]]; then sdkmanager="$PWD/../analysis/emulator_lab/.lab/sdk/cmdline-tools/lab/bin/sdkmanager";
else python3 scripts/toolchain.py android; sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"; fi
if [[ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" || ! -d "$ANDROID_HOME/build-tools/35.0.0" ]]; then
    echo 'Android SDK packages are missing. Review Google’s SDK licenses if prompted.'
    "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
    "$sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
fi
bash scripts/test.sh
.tools/gradle-8.13/bin/gradle --no-daemon --console=plain ":app:$task" ":app:lint${task#assemble}"
echo "APK: $PWD/app/build/outputs/apk/$flavor/debug/app-$flavor-debug.apk"
