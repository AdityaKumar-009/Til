"""Smoke-check Start, charms, and drawer on the connected device.

Does not change pins, send data to other apps, or change system settings.
Captures and UI dumps are kept under the ignored build directory.
"""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:\Users\adity\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = Path("build/animation-analysis/start-refinement/device")
PACKAGE = "com.flivoro.tile8auncher"
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, check=True, timeout=30).stdout


def capture(name):
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/tile8-charms-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/tile8-charms-ui.xml")
    return ET.fromstring(xml)


def matches(label):
    return [n for n in tree().iter("node")
            if label in (n.get("text"), n.get("content-desc"))]


def tap(label, rightmost=False):
    nodes = matches(label)
    assert nodes, f"Missing control: {label}"
    bounds = [list(map(int, re.findall(r"\d+", n.get("bounds", "")))) for n in nodes]
    bounds = [b for b in bounds if len(b) == 4 and b[2] > b[0] and b[3] > b[1]]
    assert bounds, f"No visible bounds: {label}"
    b = max(bounds, key=lambda b: b[0]) if rightmost else bounds[0]
    adb("shell", "input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
    time.sleep(.5)


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(.5)


def home():
    adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c",
        "android.intent.category.HOME", "-n", PACKAGE + "/.MainActivity")
    time.sleep(3.2)


adb("shell", "input", "keyevent", "224")
adb("shell", "wm", "dismiss-keyguard")
home()
capture("start-settled")
tap("Open charms")
for label in ("Search", "Share", "Start", "Devices", "Settings"):
    assert matches(label), f"Missing charm: {label}"
capture("charms-rail")
tap("Settings", rightmost=True)
assert matches("Change PC settings"), "Missing Settings footer"
capture("charms-settings")
back()
assert matches("Share"), "Back did not return from panel to rail"
tap("Share", rightmost=True)
capture("charms-share")
back()
tap("Search", rightmost=True)
assert matches("Search apps"), "Missing app-search field"
adb("shell", "input", "text", "cal")
time.sleep(.7)
capture("charms-search")
# Home must close the panel and keyboard regardless of their back-stack state.
home()
assert not matches("Change PC settings"), "Home left the Settings pane visible"
size = adb("shell", "wm", "size").decode()
w, h = map(int, re.findall(r"(\d+)x(\d+)", size)[-1])
density = adb("shell", "wm", "density").decode()
scale = int(re.findall(r"\d+", density)[-1]) / 160
edge_x = w - round(26 * scale)
adb("shell", "input", "swipe", str(edge_x), str(h // 2), str(w // 2), str(h // 2), "350")
time.sleep(.7)
assert matches("Share"), "Inward right-edge gesture did not open charms"
capture("charms-edge")
back()
adb("shell", "input", "swipe", str(w // 2), str(h * 4 // 5), str(w // 2), str(h // 4), "600")
time.sleep(.8)
assert matches("Apps"), "Vertical drawer gesture failed after charms"
capture("apps-after-charms")
back()
time.sleep(3)
capture("start-after-back")
print(f"Start/charms/drawer smoke checks passed. Captures: {OUT.resolve()}")
