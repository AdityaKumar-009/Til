"""Connected-device smoke capture. Does not change pins or system animation settings."""
import pathlib
import subprocess
import time

ADB = r"C:\Users\adity\AppData\Local\Android\Sdk\platform-tools\adb.exe"
OUT = pathlib.Path("build/animation-analysis/interactions")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, check=True).stdout


def capture(name):
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p"))


def drag_capture(name, x1, y1, x2, y2):
    drag = subprocess.Popen([ADB, "shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), "2400"])
    time.sleep(.9)
    capture(name + "-held")
    drag.wait(timeout=10)
    time.sleep(.8)
    capture(name + "-settled")


adb("shell", "input", "keyevent", "224")
adb("shell", "wm", "dismiss-keyguard")
adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME",
    "-n", "com.flivoro.tile8auncher/.MainActivity")
time.sleep(1)
capture("start")
# Begin with the left edge without changing the user's saved tile arrangement.
for _ in range(5):
    adb("shell", "input", "swipe", "200", "1100", "950", "1100", "180")
time.sleep(.6)
drag_capture("start-elastic", 200, 1100, 1000, 1100)
drag_capture("drawer-up", 540, 1900, 540, 450)
drag_capture("apps-elastic", 180, 1100, 1000, 1100)
for _ in range(12):
    adb("shell", "input", "swipe", "950", "1100", "150", "1100", "140")
time.sleep(.8)
drag_capture("apps-right-elastic", 950, 1100, 100, 1100)
adb("shell", "input", "keyevent", "4")
time.sleep(1)
capture("back-to-start")
print(f"Saved captures to {OUT.resolve()}")
