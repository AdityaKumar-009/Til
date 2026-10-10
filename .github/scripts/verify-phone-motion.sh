#!/usr/bin/env bash
set -euo pipefail

# Capture export is a required part of this check, including on test failure.
collect_frames() {
    result=$?
    if ! adb pull /sdcard/Pictures/Til-motion-frames app/build/motion-frames; then
        exit 1
    fi
    exit "$result"
}
trap collect_frames EXIT
chmod +x gradlew
adb shell wm size 1080x2160
adb shell wm density 480
./gradlew connectedDebugAndroidTest --stacktrace
