import os, socket, sys, time

monitor, out = sys.argv[1:]
os.makedirs(out, exist_ok=True)
for _ in range(100):
    if os.path.exists(monitor):
        break
    time.sleep(0.1)
else:
    raise SystemExit("QEMU monitor socket did not appear")
sock = socket.socket(socket.AF_UNIX)
sock.settimeout(5)
sock.connect(monitor)
try:
    sock.recv(4096)
except socket.timeout:
    pass
last = 0
for target in (10, 30, 60, 100, 160):
    time.sleep(target - last)
    last = target
    path = os.path.join(out, "screen-%03d.ppm" % target)
    sock.sendall(("screendump " + path + "\n").encode())
    time.sleep(1)
    try:
        data = sock.recv(4096)
        print("capture", target, data.decode(errors="replace"))
    except socket.timeout:
        print("capture", target, "monitor timed out")
sock.sendall(b"quit\n")
sock.close()
