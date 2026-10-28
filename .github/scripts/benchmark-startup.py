#!/usr/bin/env python3
"""Compare old/new optimized APKs, first drawn launcher surface, and warm reopen behavior."""
import json
import argparse
import os
from pathlib import Path
import re
import statistics
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.flivoro.tile8auncher"
COMPONENT = f"{PACKAGE}/{PACKAGE}.MainActivity"
OUT = Path("app/build/startup-benchmark")


def adb(*args):
    # A wedged emulator must fail this check rather than consume the entire CI job.
    timeout = 120 if "compile" in args or args[0] == "install" else 60
    try:
        return subprocess.check_output(["adb", *args], text=True,
                                       stderr=subprocess.STDOUT, timeout=timeout)
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(f"Emulator command timed out after {timeout}s: adb {' '.join(args)}") from error


def checkpoint(results):
    (OUT / "timings.json").write_text(json.dumps(results, indent=2))


def startup_log():
    return adb("logcat", "-d", "-s", "MosaicStartup:I", "ActivityTaskManager:I", "AndroidRuntime:E")


def fully_drawn_ms(log):
    match = re.search(r"Fully drawn " + re.escape(PACKAGE) + r"/[^:]+:\s*\+([0-9a-z]+)", log)
    if not match:
        return None
    units = {"ms": 1, "s": 1000, "m": 60_000}
    return sum(int(value) * units[unit] for value, unit in re.findall(r"(\d+)(ms|s|m)", match[1]))


def wait_for_phone_render_confirmation(mode):
    if mode == "DESKTOP":
        return
    # On a busy software renderer, the phone's stable-frame confirmation can lag
    # reportFullyDrawn. Killing it earlier correctly triggers crash recovery on the
    # next launch. Wait for the real confirmation; never clear the safety marker.
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        try:
            prefs = ET.fromstring(adb("shell", "cat",
                f"/data/user/0/{PACKAGE}/shared_prefs/tile8_launcher_prefs_v2.xml"))
        except (ET.ParseError, subprocess.CalledProcessError):
            # SharedPreferences may be atomically replacing the file during this read.
            time.sleep(.1)
            continue
        selected = prefs.find("string[@name='launcher_ui_mode']")
        if selected is None or selected.text != mode:
            raise RuntimeError(f"Launcher changed mode while settling {mode}")
        if not any(item.get("name") == "phone_render_pending" for item in prefs):
            return
        time.sleep(.1)
    raise RuntimeError(f"{mode} did not persist its stable-render confirmation")


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
            if fully_drawn_ms(log) is None:
                raise RuntimeError(f"{mode} did not report a fully drawn launcher:\n{log}")
            wait_for_phone_render_confirmation(mode)
            return {"apps": int(ready[1]), "ready_ms": int(ready[2]),
                    "initial_drawn_ms": int(re.search(r"TotalTime: (\d+)", output)[1]),
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


def set_mode(mode):
    """Select a mode on an isolated rooted emulator, preserving normal production entry points."""
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


def install_and_compile_profile():
    # Test the profile shipped in each APK, without training either version with extra launches.
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "pm", "art", "clear-app-profiles", PACKAGE)
    installed = adb("shell", "am", "broadcast", "-a", "androidx.profileinstaller.action.INSTALL_PROFILE",
                    "-n", f"{PACKAGE}/androidx.profileinstaller.ProfileInstallReceiver")
    if not re.search(r"result=1\b", installed):
        raise RuntimeError(f"Bundled profile was not installed:\n{installed}")
    adb("shell", "am", "force-stop", PACKAGE)
    compiled = adb("shell", "cmd", "package", "compile", "-f", "-m", "speed-profile", PACKAGE)
    if "Success" not in compiled:
        raise RuntimeError(compiled)
    return {"install": installed, "compile": compiled}


def measure(apk, label, iterations=3, compile_profile=False, all_results=None,
            modes=("DESKTOP", "PHONE_8", "MOBILE_10")):
    adb("install", "-r", apk)
    results = {}
    if all_results is None:
        all_results = {}
    all_results[label] = results
    for mode in modes:
        print(f"Measuring {label} {mode}, profile compilation={compile_profile}", flush=True)
        set_mode(mode)
        # Keep ART compilation conditions equal across APKs and modes.
        adb("shell", "cmd", "package", "compile", "-f", "-m", "verify", PACKAGE)
        adb("shell", "pm", "art", "clear-app-profiles", PACKAGE)
        first = launch(mode)
        current = results[mode] = {"first": first, "cached": [], "reopen": []}
        checkpoint(all_results)
        current["compilation"] = install_and_compile_profile() if compile_profile else None
        cached = current["cached"]
        for _ in range(iterations):
            cached.append(launch(mode))
            checkpoint(all_results)
        drawn = [item["fully_drawn_ms"] for item in cached if item["fully_drawn_ms"] is not None]
        current.update({"cached_median_ms": statistics.median(item["ready_ms"] for item in cached),
                         "cached_initial_drawn_median_ms": statistics.median(item["initial_drawn_ms"] for item in cached),
                         "cached_drawn_median_ms": statistics.median(drawn)})
        checkpoint(all_results)
        for _ in range(3):
            current["reopen"].append(reopen(label == "after"))
            checkpoint(all_results)
        print(f"{label} {mode}: cached preparation={results[mode]['cached_median_ms']}ms, "
              f"first frame={results[mode]['cached_initial_drawn_median_ms']}ms, "
              f"fully drawn={results[mode]['cached_drawn_median_ms']}ms", flush=True)
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("before")
    parser.add_argument("after")
    parser.add_argument("--iterations", type=int, default=3)
    parser.add_argument("--compile-profile", action="store_true")
    parser.add_argument("--modes", nargs="+", choices=("DESKTOP", "PHONE_8", "MOBILE_10"),
                        default=("DESKTOP", "PHONE_8", "MOBILE_10"))
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    adb("root")
    adb("wait-for-device")
    adb("shell", "wm", "size", "1080x2160")
    adb("shell", "wm", "density", "480")
    adb("shell", "settings", "put", "global", "stay_on_while_plugged_in", "7")
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    results = {}
    for label, apk in zip(("before", "after"), (args.before, args.after), strict=True):
        measure(apk, label, args.iterations, args.compile_profile, results, args.modes)
    lines = ["### Launcher startup on the same API 34 emulator", "",
             f"Fresh data once; saved cold starts {args.iterations} times. Fully drawn includes process and initial UI rendering, before the entrance animation finishes. Preparation timings exclude process/Compose initialization. Emulator timings are not device guarantees.",
             "Both APKs use their bundled Baseline Profile, installed and AOT-compiled before cached measurements." if args.compile_profile else "Both APKs start with ART compilation reset; no forced ahead-of-time compilation.", "",
             "| Mode | Apps | Fresh prep before / after | Cached prep median before / after | Cached first frame before / after | Cached fully drawn before / after | Warm reuse after |", "|---|---:|---:|---:|---:|---:|---:|"]
    for mode, after in results["after"].items():
        before = results["before"][mode]
        lines.append(f"| {mode} | {after['first']['apps']} | {before['first']['ready_ms']} / {after['first']['ready_ms']} ms | {before['cached_median_ms']} / {after['cached_median_ms']} ms | {before['cached_initial_drawn_median_ms']} / {after['cached_initial_drawn_median_ms']} ms | {before['cached_drawn_median_ms']} / {after['cached_drawn_median_ms']} ms | {sum(item['reused'] for item in after['reopen'])}/3 |")
    report = "\n".join(lines) + "\n"
    (OUT / "summary.md").write_text(report)
    print(report)
    if path := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(path, "a") as file:
            file.write(report)


if __name__ == "__main__":
    main()
