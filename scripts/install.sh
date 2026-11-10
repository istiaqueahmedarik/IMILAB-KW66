#!/usr/bin/env bash
# Explicit device selection prevents installing onto the wrong phone or an emulator.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
if [[ $# -lt 1 || $# -gt 2 ]]; then echo 'Usage: scripts/install.sh PHONE_SERIAL [core|gemma]' >&2; adb devices -l; exit 2; fi
serial="$1";flavor="${2:-core}"
case "$flavor" in core|gemma);; *) echo 'Choose core or gemma' >&2; exit 2;; esac
apk="app/build/outputs/apk/$flavor/debug/app-$flavor-debug.apk"
if [[ ! -f "$apk" ]]; then echo "Build first: bash scripts/build.sh $flavor" >&2; exit 1; fi
if [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]; then echo 'Refusing a physical-watch install onto an emulator; use adb manually for emulator UI testing.' >&2; exit 1; fi
adb -s "$serial" install -r "$apk"
adb -s "$serial" shell am start -n dev.kw66.glass/.MainActivity
