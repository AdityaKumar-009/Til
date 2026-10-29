#!/usr/bin/env bash
set -euo pipefail

# Capture export is a required part of this check, including on test failure.
collect_frames() {
    result=$?
    if adb shell test -d /sdcard/Pictures/Til-motion-frames; then
        adb pull /sdcard/Pictures/Til-motion-frames app/build/motion-frames || result=1
    elif [[ "$result" -eq 0 ]]; then
        echo "Motion frame captures are missing."
        result=1
    fi
    if adb shell test -d /sdcard/Android/data/com.flivoro.tile8auncher/files/motion-frames; then
        adb pull /sdcard/Android/data/com.flivoro.tile8auncher/files/motion-frames app/build/motion-frames/metadata || result=1
    fi
    exit "$result"
}
trap collect_frames EXIT
chmod +x gradlew
adb shell wm size 1080x2160
adb shell wm density 480
# Android 10 shows a first-time immersive navigation warning over screenshots.
# The test emulator can acknowledge that system-only tutorial ahead of capture.
adb shell settings put secure immersive_mode_confirmations confirmed
# Fail quickly on unlock recovery, folder and desktop-input interactions before the longer
# optimized smoke/render matrix. Captures survive AGP cleanup through MediaStore.
./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.flivoro.tile8auncher.WidgetRenderingTest,com.flivoro.tile8auncher.WidgetPlacementTest,com.flivoro.tile8auncher.LauncherUnlockRecoveryTest,com.flivoro.tile8auncher.InlinePhoneFolderTest,com.flivoro.tile8auncher.DesktopInputTest \
    --stacktrace
./gradlew assemblePerformance --stacktrace
adb install -r app/build/outputs/apk/performance/app-performance.apk

# The user's VerifyError occurs in the R8-optimized APK. Debug instrumentation alone misses it,
# so launch that exact optimized package with each persisted phone mode before the debug tests.
adb root
adb wait-for-device
app_uid="$(adb shell pm list packages -U com.flivoro.tile8auncher \
    | sed -n 's/.*uid:\([0-9][0-9]*\).*/\1/p' | head -n 1 | tr -d '\r')"
if [[ -z "$app_uid" ]]; then
    adb shell pm list packages -U com.flivoro.tile8auncher
    echo "Could not determine the optimized launcher package UID."
    exit 1
fi
mode_pref_file="$(mktemp)"
verify_phone_mode_startup() {
    local mode="$1"
    local pref_path="/data/user/0/com.flivoro.tile8auncher/shared_prefs/tile8_launcher_prefs_v2.xml"
    adb shell am force-stop com.flivoro.tile8auncher || true
    adb shell pm clear com.flivoro.tile8auncher >/dev/null
    cat > "$mode_pref_file" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="launcher_ui_mode">$mode</string>
</map>
EOF
    adb shell mkdir -p "$(dirname "$pref_path")"
    adb push "$mode_pref_file" "$pref_path"
    adb shell chown "$app_uid:$app_uid" "$pref_path"
    adb shell chown "$app_uid:$app_uid" "$(dirname "$pref_path")"
    adb shell chmod 600 "$pref_path"
    adb shell restorecon -R "$(dirname "$pref_path")"
    adb logcat -c
    local launch_output
    launch_output="$(adb shell am start -W -n \
        com.flivoro.tile8auncher/com.flivoro.tile8auncher.MainActivity 2>&1)"
    printf '%s\n' "$launch_output"
    # Process alive is not enough: a regression can leave HOME stuck behind the splash.
    local ready=false
    for attempt in $(seq 1 100); do
        if adb logcat -d -s MosaicStartup:I | grep -q "Ready $mode,"; then
            ready=true
            break
        fi
        sleep 0.2
    done
    adb logcat -d -s MosaicStartup:I
    if [[ "$ready" != true ]]; then
        adb logcat -d -s AndroidRuntime:E
        echo "Optimized launcher did not prepare $mode within 20 seconds."
        exit 1
    fi
    sleep 2
    local crash_log
    crash_log="$(adb logcat -d -s AndroidRuntime:E)"
    if grep -Eq 'FATAL EXCEPTION|VerifyError' <<<"$crash_log"; then
        printf '%s\n' "$crash_log"
        echo "Optimized launcher failed to start in $mode."
        exit 1
    fi
    if ! grep -q 'Status: ok' <<<"$launch_output" || \
        ! adb shell pidof com.flivoro.tile8auncher >/dev/null 2>&1; then
        printf '%s\n' "$crash_log"
        echo "Optimized launcher did not remain alive in $mode."
        exit 1
    fi
    echo "Optimized launcher remained alive in $mode."

    # Keep a small visual snapshot for each public APK build. These PNGs are
    # independent emulator renders, not motion-frame archives.
    local sdk
    sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
    mkdir -p app/build/phone-ui-snapshots
    adb exec-out screencap -p > "app/build/phone-ui-snapshots/${mode}-start-api${sdk}.png"
    if [[ "$mode" == "PHONE_8" ]]; then
        # Visual regression snapshots of the ACTUAL optimized APK. A held
        # first tile exposes the resize arrow; scrolling to the end exposes
        # the right-arrow banner. Keep these tiny PNGs alongside startup shots.
        adb shell input swipe 250 330 250 330 900
        sleep 1
        adb exec-out screencap -p > "app/build/phone-ui-snapshots/${mode}-tile-edit-api${sdk}.png"
        adb shell input keyevent 4
        sleep 1
        adb shell input swipe 520 1670 520 390 430
        sleep 1
        adb exec-out screencap -p > "app/build/phone-ui-snapshots/${mode}-all-apps-arrow-api${sdk}.png"
    fi
    adb shell input swipe 900 1100 150 1100 390
    sleep 1
    adb exec-out screencap -p > "app/build/phone-ui-snapshots/${mode}-all-apps-api${sdk}.png"
}
verify_phone_mode_startup PHONE_8
verify_phone_mode_startup MOBILE_10
rm -f "$mode_pref_file"

# Unlike debug-only frame fixtures, this calls every new motion compositor
# after R8 optimization and verifies internal Settings returns on the device.
python3 .github/scripts/verify-story-launch.py

./gradlew connectedDebugAndroidTest --stacktrace
# AGP replaces connected-test reports on the final persisted-mode smoke below.
# Preserve the full matrix so the downloadable XML still proves all 100 cases.
mkdir -p app/build/full-matrix-androidTest-results
cp -a app/build/outputs/androidTest-results/connected/. app/build/full-matrix-androidTest-results/
./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.flivoro.tile8auncher.ExampleInstrumentedTest#persistWp81ModeForOptimizedStartupSmoke \
    --stacktrace
adb install -r app/build/outputs/apk/performance/app-performance.apk
adb shell pm clear com.flivoro.tile8auncher >/dev/null
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
