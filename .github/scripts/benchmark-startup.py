#!/usr/bin/env python3
"""Compare old/new optimized APKs on the SAME emulator; never treat am-start TTID as ready."""
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


def launch(mode):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("logcat", "-c")
    output = adb("shell", "am", "start", "-W", "-n", COMPONENT)
    if "Status: ok" not in output:
        raise RuntimeError(output)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        log = adb("logcat", "-d", "-s", "MosaicStartup:I", "AndroidRuntime:E")
        if "FATAL EXCEPTION" in log or "VerifyError" in log:
            raise RuntimeError(log)
        ready = re.search(rf"Ready {mode}, (\d+) apps, (\d+)ms", log)
        if ready:
            if not adb("shell", "pidof", PACKAGE).strip():
                raise RuntimeError("Launcher exited after readiness")
            # Let its entrance and deferred writes settle before killing the process again.
            time.sleep(2)
            return {"apps": int(ready[1]), "ready_ms": int(ready[2]), "log": log}
        time.sleep(.2)
    raise RuntimeError(f"{mode} never became ready:\n{log}")


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
        results[mode] = {"first": first, "cached": cached,
                         "cached_median_ms": statistics.median(item["ready_ms"] for item in cached)}
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
    lines = ["### Launcher preparation on the same API 34 emulator", "",
             "Fresh data once; cold process with saved disk caches three times. Timings end at Start/All Apps readiness; they exclude process/Compose initialization. Emulator timings are not device guarantees.", "",
             "| Mode | Apps | Fresh before / after | Cached median before / after |", "|---|---:|---:|---:|"]
    for mode, after in results["after"].items():
        before = results["before"][mode]
        lines.append(f"| {mode} | {after['first']['apps']} | {before['first']['ready_ms']} / {after['first']['ready_ms']} ms | {before['cached_median_ms']} / {after['cached_median_ms']} ms |")
    report = "\n".join(lines) + "\n"
    (OUT / "summary.md").write_text(report)
    print(report)
    if path := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(path, "a") as file:
            file.write(report)


if __name__ == "__main__":
    main()
