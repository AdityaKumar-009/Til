#!/usr/bin/env bash
set -euo pipefail

# Preserve screenshots on test failure while retaining Gradle's exit status.
trap 'adb pull /sdcard/Android/data/com.flivoro.tile8auncher/files/motion-frames app/build/motion-frames || true' EXIT
chmod +x gradlew
adb shell wm size 1080x2160
adb shell wm density 480
./gradlew connectedDebugAndroidTest --stacktrace
