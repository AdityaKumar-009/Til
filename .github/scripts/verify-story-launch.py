#!/usr/bin/env python3
"""Exercise all optional compositors in the actual R8 optimized APK, including Settings Back."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "com.flivoro.tile8auncher"
PREF = f"/data/user/0/{PKG}/shared_prefs/tile8_launcher_prefs_v2.xml"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT)


def wait_for(predicate, description, timeout=20):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        if predicate():
            return
        time.sleep(.2)
    raise RuntimeError(f"Timed out: {description}\n{adb('logcat', '-d', '-s', 'MosaicStory:I', 'AndroidRuntime:E')}")


def tap_settings():
    adb("shell", "uiautomator", "dump", "/sdcard/story-ui.xml")
    root = ET.fromstring(adb("shell", "cat", "/sdcard/story-ui.xml"))
    for node in root.iter("node"):
        if node.attrib.get("text", "").strip() == "All settings":
            values = list(map(int, re.findall(r"\d+", node.attrib["bounds"])))
            x1, y1, x2, y2 = values
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            return True
    return False


for style in ("LIFT", "DOOR", "RIPPLE", "PAGE_TURN", "GLIDE", "FOCUS", "ARC", "UNFOLD"):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "pm", "clear", PKG)
    uid = re.search(r"uid:(\d+)", adb("shell", "pm", "list", "packages", "-U", PKG)).group(1)
    xml = f'''<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name="launcher_ui_mode">PHONE_8</string>
<string name="master_app_launch_animation">{style}</string></map>'''
    adb("shell", "mkdir", "-p", PREF.rsplit("/", 1)[0])
    subprocess.run(["adb", "shell", f"cat > {PREF}"], input=xml, text=True, check=True)
    adb("shell", "chown", "-R", f"{uid}:{uid}", PREF.rsplit("/", 1)[0])
    adb("shell", "restorecon", "-R", PREF.rsplit("/", 1)[0])
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/{PKG}.MainActivity")
    wait_for(lambda: "Ready PHONE_8," in adb("logcat", "-d", "-s", "MosaicStartup:I"), f"{style} startup")
    time.sleep(1.2)  # Wait for the native cold-start entrance to settle.
    adb("shell", "input", "tap", "540", "108")  # Existing Action Center shortcut.
    wait_for(tap_settings, f"{style} All settings shortcut")
    wait_for(lambda: f"{style} forward complete" in adb("logcat", "-d", "-s", "MosaicStory:I"), f"{style} forward")
    # Hold the completed internal window briefly before exercising Back.
    time.sleep(.8)
    adb("shell", "input", "keyevent", "4")
    wait_for(lambda: f"{style} reverse complete" in adb("logcat", "-d", "-s", "MosaicStory:I"), f"{style} reverse")
    crashes = adb("logcat", "-d", "-s", "AndroidRuntime:E")
    if "FATAL EXCEPTION" in crashes or "VerifyError" in crashes:
        raise RuntimeError(f"{style} optimized runtime failed:\n{crashes}")
    print(f"Optimized {style}: Settings opening and Back return passed.", flush=True)
