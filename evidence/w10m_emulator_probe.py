import json, os, socket, sys, time

monitor, out = sys.argv[1:]
os.makedirs(out, exist_ok=True)
for _ in range(150):
    if os.path.exists(monitor):
        break
    time.sleep(0.1)
else:
    raise SystemExit("QEMU QMP socket did not appear")
sock = socket.socket(socket.AF_UNIX)
sock.settimeout(30)
sock.connect(monitor)
f = sock.makefile("rwb", buffering=0)
print("greeting", f.readline().decode().strip())
def execute(name, args=None):
    request = {"execute": name}
    if args is not None:
        request["arguments"] = args
    f.write((json.dumps(request) + "\n").encode())
    while True:
        response = json.loads(f.readline())
        if "error" in response:
            raise RuntimeError(response)
        if "return" in response:
            return response["return"]
execute("qmp_capabilities")
print("mouse devices:", execute("query-mice"))
def event(events):
    execute("input-send-event", {"events": events})
def move(x, y):
    event([{"type": "abs", "data": {"axis": "x", "value": round(x * 32767 / 1023)}},
           {"type": "abs", "data": {"axis": "y", "value": round(y * 32767 / 767)}}])
def button(pressed):
    event([{"type": "btn", "data": {"button": "left", "down": pressed}}])
def shot(name):
    execute("screendump", {"filename": os.path.join(out, name + ".ppm")})
def click(x, y):
    move(x, y)
    time.sleep(.1)
    button(True)
    time.sleep(.08)
    button(False)
def capture_tap(name, x, y):
    move(x, y)
    time.sleep(.1)
    button(True)
    time.sleep(.08)
    start = time.monotonic()
    button(False)
    timings = []
    for i in range(25):
        target = start + i * .04
        if time.monotonic() < target:
            time.sleep(target - time.monotonic())
        now = time.monotonic()
        shot("%s-%02d" % (name, i))
        timings.append((i, round((now - start) * 1000, 2)))
    print(name, "sample times ms:", timings)
print("Waiting for first-boot setup")
time.sleep(140)
shot("01-start")
move(85, 280)
button(True)
time.sleep(1.4)
shot("02-held")
for i in range(1, 13):
    move(85 + (170 * i / 12), 280)
    time.sleep(.07)
shot("03-dragging")
button(False)
time.sleep(2)
shot("04-after-drop")
capture_tap("05-first-tap", 255, 280)
time.sleep(1)
shot("06-after-first-tap")
capture_tap("07-second-tap", 255, 280)
time.sleep(1)
shot("08-after-second-tap")
execute("quit")
