#!/usr/bin/env python3
"""Open, scroll, select and reopen Animation style in the installed optimized APK."""
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "com.flivoro.tile8auncher"
PREF = f"/data/user/0/{PKG}/shared_prefs/tile8_launcher_prefs_v2.xml"
OUT = Path("app/build/motion-performance/animation-picker")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT)


def alive():
    crashes = adb("logcat", "-d", "-s", "AndroidRuntime:E")
    if "FATAL EXCEPTION" in crashes or "VerifyError" in crashes or not adb("shell", "pidof", PKG).strip():
        raise RuntimeError(f"Optimized picker crashed:\n{crashes}")


def tree():
    alive()
    adb("shell", "uiautomator", "dump", "/sdcard/til-picker-ui.xml")
    xml = adb("shell", "cat", "/sdcard/til-picker-ui.xml")
    (OUT / "last-ui.xml").write_text(xml)
    return ET.fromstring(xml)


def find(label):
    for node in tree().iter("node"):
        text = node.attrib.get("text", "").strip().splitlines()
        if (text and text[0] == label) or node.attrib.get("content-desc") == label:
            bounds = re.findall(r"\d+", node.attrib["bounds"])
            if len(bounds) == 4:
                x1, y1, x2, y2 = map(int, bounds)
                if x2 > x1 and y2 > y1:
                    return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(label, scroll=False):
    for attempt in range(8 if scroll else 4):
        point = find(label)
        if point:
            adb("shell", "input", "tap", *map(str, point))
            time.sleep(.25)
            alive()
            return
        if scroll:
            adb("shell", "input", "swipe", "800", "1650", "800", "650", "250")
        else:
            time.sleep(.25)
    raise RuntimeError(f"Could not reach {label!r}")


choices = {
    "DEFAULT": "Default (follow interface)", "DESKTOP": "Windows 8.1 Desktop",
    "PHONE_8": "Windows Phone 8.1", "MOBILE_10": "Windows 10 Mobile",
    "DOOR": "Door", "RIPPLE": "Ripple", "FOCUS": "Focus", "UNFOLD": "Uncover",
    "PASSAGE": "Passage", "CASCADE": "Cascade",
}
results = []
for mode in ("DESKTOP", "PHONE_8", "MOBILE_10"):
    try:
        adb("shell", "am", "force-stop", PKG)
        adb("shell", "pm", "clear", PKG)
        uid = re.search(r"uid:(\d+)", adb("shell", "pm", "list", "packages", "-U", PKG)).group(1)
        xml = f'<map><string name="launcher_ui_mode">{mode}</string></map>'
        adb("shell", "mkdir", "-p", PREF.rsplit("/", 1)[0])
        subprocess.run(["adb", "shell", f"cat > {PREF}"], input=xml, text=True, check=True)
        adb("shell", "chown", "-R", f"{uid}:{uid}", PREF.rsplit("/", 1)[0])
        adb("shell", "restorecon", "-R", PREF.rsplit("/", 1)[0])
        adb("logcat", "-c")
        adb("shell", "am", "start", "-W", "-n", f"{PKG}/{PKG}.MainActivity")
        deadline = time.monotonic() + 20
        while f"Ready {mode}," not in adb("logcat", "-d", "-s", "MosaicStartup:I"):
            alive()
            if time.monotonic() > deadline:
                raise RuntimeError(f"{mode} did not finish startup")
            time.sleep(.2)
        time.sleep(2 if mode == "DESKTOP" else 1.2)
        if mode == "DESKTOP":
            tap("Open charms")
            tap("Settings")
            tap("Change PC settings")
        else:
            # Same public Action Center entry used by the optimized story smoke.
            adb("shell", "input", "tap", "540", "108")
            tap("All settings")
        tap("Start & motion", scroll=True)
        for style, label in choices.items():
            tap("Animation style", scroll=True)
            if not find("App-opening animation"):
                raise RuntimeError("Animation dialog did not open")
            tap(label, scroll=True)
            deadline = time.monotonic() + 3
            while True:
                saved = ET.fromstring(adb("shell", "cat", PREF))
                actual = next((node.text for node in saved if node.attrib.get("name") == "master_app_launch_animation"), None)
                if actual == style:
                    break
                if time.monotonic() > deadline:
                    raise RuntimeError(f"Selected {style}, persisted {actual}")
                time.sleep(.2)
            results.append({"mode": mode, "style": style, "passed": True})
        tap("Animation style", scroll=True)
        adb("shell", "input", "keyevent", "4")
        tap("Animation style", scroll=True)
        alive()
        subprocess.run(["adb", "exec-out", "screencap", "-p"],
            stdout=(OUT / f"{mode}.png").open("wb"), check=True)
        print(f"Optimized {mode}: all ten animation choices, dismissal and reopening passed.", flush=True)
    finally:
        (OUT / "results.json").write_text(json.dumps(results, indent=2))
        (OUT / f"{mode}-runtime.txt").write_text(adb("logcat", "-d", "-s", "AndroidRuntime:E"))
