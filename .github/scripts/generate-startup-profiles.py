#!/usr/bin/env python3
"""Record real ART startup rules on the rooted API 34 CI emulator; no production test hooks.

Implements Android's documented manual capture protocol, automated over all launcher modes:
https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure
Capture APK: non-debuggable, non-minified. Distribution APK: R8-optimized.
"""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import time

spec = importlib.util.spec_from_file_location("startup", Path(__file__).with_name("benchmark-startup.py"))
startup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(startup)
adb, PACKAGE = startup.adb, startup.PACKAGE
OUT = Path("app/build/startup-profiles")
SOURCE = Path("app/src/main/baselineProfiles")
RECEIVER = f"{PACKAGE}/androidx.profileinstaller.ProfileInstallReceiver"


def broadcast(action, *extra):
    return adb("shell", "am", "broadcast", "-a", f"androidx.profileinstaller.action.{action}",
               *extra, "-n", RECEIVER)


def dump_rules():
    result = broadcast("SAVE_PROFILE")
    if not re.search(r"result=12\b", result):
        raise RuntimeError(f"ART profile was not flushed:\n{result}")
    time.sleep(1)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "pm", "dump-profiles", "--dump-classes-and-methods", PACKAGE)
    raw = adb("shell", "cat", f"/data/misc/profman/{PACKAGE}-primary.prof.txt")
    # Same compatibility exclusions as AndroidX BaselineProfiles.kt: array-qualified class
    # rules and inline-cache specifiers cannot be consumed by the API 28 profile format.
    rules = {line.strip() for line in raw.splitlines()
             if re.match(r"^[HSP]*L[^;]+;", line) and "+" not in line}
    if not rules:
        raise RuntimeError("ART returned no usable profile rules")
    return rules


def open_drawer(mode):
    # The benchmark's display size/density is fixed below; these are real user gestures.
    if mode == "DESKTOP":
        adb("shell", "input", "swipe", "540", "1750", "540", "500", "400")
    else:
        adb("shell", "input", "swipe", "940", "1100", "120", "1100", "400")
    time.sleep(1)
    for _ in range(2):
        if mode == "DESKTOP":
            adb("shell", "input", "swipe", "940", "1100", "120", "1100", "400")
        else:
            adb("shell", "input", "swipe", "540", "1750", "540", "500", "400")
        time.sleep(.5)
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    SOURCE.mkdir(parents=True, exist_ok=True)
    adb("root")
    adb("wait-for-device")
    if int(adb("shell", "getprop", "ro.build.version.sdk").strip()) < 34:
        raise RuntimeError("This capture pipeline requires API 34 or newer")
    adb("shell", "wm", "size", "1080x2160")
    adb("shell", "wm", "density", "480")
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    adb("install", "-r", sys.argv[1])
    all_startup, all_baseline, counts = set(), set(), {}
    for mode in ("DESKTOP", "PHONE_8", "MOBILE_10"):
        startup.set_mode(mode)
        skipped = broadcast("SKIP_FILE", "--es", "EXTRA_SKIP_FILE_OPERATION", "WRITE_SKIP_FILE")
        if not re.search(r"result=10\b", skipped):
            raise RuntimeError(f"Could not disable profile bootstrapping during capture:\n{skipped}")
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "cmd", "package", "compile", "-f", "-m", "verify", PACKAGE)
        adb("shell", "pm", "art", "clear-app-profiles", PACKAGE)
        mode_rules = set()
        # Fresh setup plus repeated saved-state launches include both valid startup paths.
        # Union observed rules instead of broad package wildcards or guessed method names.
        for iteration in range(3):
            startup.launch(mode)
            time.sleep(5)
            mode_rules |= dump_rules()
            print(f"{mode} launch {iteration + 1}: {len(mode_rules)} startup rules", flush=True)
        all_startup |= mode_rules
        startup.launch(mode)
        open_drawer(mode)
        time.sleep(5)
        drawer_rules = dump_rules()
        all_baseline |= mode_rules | drawer_rules
        counts[mode] = {"startup_rules": len(mode_rules), "baseline_rules": len(mode_rules | drawer_rules)}
        (OUT / f"{mode}-startup-prof.txt").write_text("\n".join(sorted(mode_rules)) + "\n")
    expected = ("MainActivity", "ui/start/StartScreenKt", "ui/phone/PhoneLauncherKt")
    for class_name in expected:
        if not any(f"com/flivoro/tile8auncher/{class_name};" in line for line in all_startup):
            raise RuntimeError(f"Unobfuscated startup class missing: {class_name}")
    SOURCE.joinpath("startup-prof.txt").write_text("\n".join(sorted(all_startup)) + "\n")
    SOURCE.joinpath("baseline-prof.txt").write_text("\n".join(sorted(all_baseline)) + "\n")
    summary = {"source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
               "api": 34, "modes": counts, "startup_rules": len(all_startup),
               "baseline_rules": len(all_baseline), "capture_build_type": "profileGeneration"}
    OUT.joinpath("capture.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2), flush=True)


if __name__ == "__main__":
    main()
