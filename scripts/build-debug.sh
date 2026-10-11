#!/usr/bin/env bash
# Build a debug APK: app/build/outputs/apk/debug/app-debug.apk
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew :app:assembleDebug
