#!/usr/bin/env python3
"""Compare old/new optimized APKs, first drawn launcher surface, and warm reopen behavior."""
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import sys
import tempfile
import time

PACKAGE = "com.flivoro.tile8auncher"
COMPONENT = f"{PACKAGE}/{PACKAGE}.MainActivity"
OUT = Path("app/build/startup-benchmark")


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT)


def startup_log():
    return adb("logcat", "-d", "-s", "MosaicStartup:I", "ActivityTaskManager:I", "AndroidRuntime:E")


def fully_drawn_ms(log):
    match = re.search(r"Fully drawn " + re.escape(PACKAGE) + r"/[^:]+:\s*\+([0-9a-z]+)", log)
    if not match:
        return None
    units = {"ms": 1, "s": 1000, "m": 60_000}
    return sum(int(value) * units[unit] for value, unit in re.findall(r"(\d+)(ms|s|m)", match[1]))


def launch(mode):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    output = adb("shell", "am", "start", "-W", "-n", COMPONENT)
    if "Status: ok" not in output:
        raise RuntimeError(output)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        log = startup_log()
        if "FATAL EXCEPTION" in log or "VerifyError" in log:
            raise RuntimeError(log)
        ready = re.search(rf"Ready {mode}, (\d+) apps, (\d+)ms", log)
        if ready:
            if not adb("shell", "pidof", PACKAGE).strip():
                raise RuntimeError("Launcher exited after readiness")
            # Let its entrance and deferred writes settle before killing the process again.
            time.sleep(2)
            log = startup_log()
            if "FATAL EXCEPTION" in log:
                raise RuntimeError(log)
            return {"apps": int(ready[1]), "ready_ms": int(ready[2]),
                    "fully_drawn_ms": fully_drawn_ms(log), "am_start": output, "log": log}
        time.sleep(.2)
    raise RuntimeError(f"{mode} never became ready:\n{log}")


def reopen(require_reuse):
    pid = adb("shell", "pidof", PACKAGE).strip()
    adb("shell", "am", "start", "-a", "android.settings.SETTINGS")
    adb("logcat", "-c")
    output = adb("shell", "am", "start", "-W", "-n", COMPONENT)
    time.sleep(1)
    adb("shell", "input", "keyevent", "4")
    adb("shell", "am", "start", "-W", "-n", COMPONENT)
    time.sleep(1)
    log = startup_log()
    reused = (pid == adb("shell", "pidof", PACKAGE).strip() and
              "Ready " not in log and "Loading indicator shown" not in log)
    if "FATAL EXCEPTION" in log or (require_reuse and not reused):
        raise RuntimeError(f"Warm reopen restarted launcher preparation:\n{log}")
    return {"reused": reused, "am_start": output, "log": log}


def measure(apk, label):
    adb("install", "-r", apk)
    results = {}
    for mode in ("DESKTOP", "PHONE_8", "MOBILE_10"):
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "pm", "clear", PACKAGE)
        uid = re.search(r"uid:(\d+)", adb("shell", "pm", "list", "packages", "-U", PACKAGE))[1]
        pref = f"/data/user/0/{PACKAGE}/shared_prefs/tile8_launcher_prefs_v2.xml"
        with tempfile.NamedTemporaryFile(mode="w", suffix=".xml") as file:
            file.write(f'<map><string name="launcher_ui_mode">{mode}</string></map>')
            file.flush()
            adb("shell", "mkdir", "-p", str(Path(pref).parent))
            adb("push", file.name, pref)
        adb("shell", "chown", f"{uid}:{uid}", pref)
        adb("shell", "chown", f"{uid}:{uid}", str(Path(pref).parent))
        adb("shell", "chmod", "600", pref)
        adb("shell", "restorecon", "-R", str(Path(pref).parent))
        first = launch(mode)
        cached = [launch(mode) for _ in range(3)]
        drawn = [item["fully_drawn_ms"] for item in cached if item["fully_drawn_ms"] is not None]
        results[mode] = {"first": first, "cached": cached,
                         "cached_median_ms": statistics.median(item["ready_ms"] for item in cached),
                         "cached_drawn_median_ms": statistics.median(drawn) if drawn else None,
                         "reopen": [reopen(label == "after") for _ in range(3)]}
        print(f"{label} {mode}: first={first['ready_ms']}ms, cached median={results[mode]['cached_median_ms']}ms", flush=True)
    return results


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    adb("root")
    adb("wait-for-device")
    adb("shell", "wm", "size", "1080x2160")
    adb("shell", "wm", "density", "480")
    results = {}
    for label, apk in zip(("before", "after"), sys.argv[1:], strict=True):
        results[label] = measure(apk, label)
        (OUT / "timings.json").write_text(json.dumps(results, indent=2))
    lines = ["### Launcher startup on the same API 34 emulator", "",
             "Fresh data once; saved cold starts three times. Fully drawn includes process and initial UI rendering, before the entrance animation finishes. Preparation timings exclude process/Compose initialization; the new build loads artwork after showing Start. Emulator timings are not device guarantees.", "",
             "| Mode | Apps | Fresh prep before / after | Cached prep median before / after | Cached fully drawn before / after | Warm reuse after |", "|---|---:|---:|---:|---:|---:|"]
    for mode, after in results["after"].items():
        before = results["before"][mode]
        lines.append(f"| {mode} | {after['first']['apps']} | {before['first']['ready_ms']} / {after['first']['ready_ms']} ms | {before['cached_median_ms']} / {after['cached_median_ms']} ms | {before['cached_drawn_median_ms']} / {after['cached_drawn_median_ms']} ms | {sum(item['reused'] for item in after['reopen'])}/3 |")
    report = "\n".join(lines) + "\n"
    (OUT / "summary.md").write_text(report)
    print(report)
    if path := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(path, "a") as file:
            file.write(report)


if __name__ == "__main__":
    main()
