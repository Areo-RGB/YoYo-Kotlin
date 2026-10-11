#!/usr/bin/env bash
# Build a release APK: app/build/outputs/apk/release/app-release.apk
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew :app:assembleRelease
