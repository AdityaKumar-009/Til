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
./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.flivoro.tile8auncher.ExampleInstrumentedTest#persistWp81ModeForOptimizedStartupSmoke \
    --stacktrace
saved_preferences="$(adb shell run-as com.flivoro.tile8auncher cat \
    shared_prefs/tile8_launcher_prefs_v2.xml 2>&1)"
if ! grep -q 'launcher_ui_mode.*PHONE_8' <<<"$saved_preferences"; then
    printf '%s\n' "$saved_preferences"
    exit 1
fi
./gradlew assemblePerformance --stacktrace
adb install -r app/build/outputs/apk/performance/app-performance.apk
adb shell am force-stop com.flivoro.tile8auncher
adb logcat -c
launch_output="$(adb shell am start -W -n \
    com.flivoro.tile8auncher/com.flivoro.tile8auncher.MainActivity 2>&1)"
printf '%s\n' "$launch_output"
sleep 2
if ! grep -q 'Status: ok' <<<"$launch_output" || ! adb shell pidof com.flivoro.tile8auncher >/dev/null 2>&1; then
    adb logcat -d -s AndroidRuntime:E
    exit 1
fi
crash_log="$(adb logcat -d -s AndroidRuntime:E)"
if grep -q 'FATAL EXCEPTION' <<<"$crash_log"; then
    printf '%s\n' "$crash_log"
    exit 1
fi
