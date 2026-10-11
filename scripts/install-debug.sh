#!/usr/bin/env bash
# Install the debug APK onto all connected devices (reinstall keeping data).
set -euo pipefail
cd "$(dirname "$0")/.."

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "Debug APK not found, building it first..."
  ./gradlew :app:assembleDebug
fi

DEVICES=$(adb devices | awk '$2 == "device" {print $1}')

if [ -z "$DEVICES" ]; then
  echo "Error: No connected Android devices found."
  exit 1
fi

for device in $DEVICES; do
  echo "Installing onto $device..."
  adb -s "$device" install -r "$APK"
done

echo "Done installing on all connected devices."
