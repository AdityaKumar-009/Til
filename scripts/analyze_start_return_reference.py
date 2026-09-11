"""Analyze the short Start-menu return in the repository's test.mp4.

The source is 30 fps, so real observations are ~33.333 ms apart. This script measures every
encoded source frame around the Start return, saves lossless source crops (including the faint
pre-detection phase), then writes a 10 ms *interpolated* table only between real observations.
"""
from pathlib import Path
import csv
import json
import math

import cv2
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "test.mp4"
OUT = ROOT / "build" / "start-return-analysis"
FRAMES = OUT / "frames"
OUT.mkdir(parents=True, exist_ok=True)
FRAMES.mkdir(parents=True, exist_ok=True)

START_S = 55.80
END_S = 56.90
ROI_X2 = 720
ROI_Y1 = 100
ROI_Y2 = 480


def detect_mail(frame):
    hsv = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV)
    mask = cv2.inRange(hsv, (82, 70, 20), (108, 255, 255))
    mask[:ROI_Y1, :] = 0
    mask[ROI_Y2:, :] = 0
    mask[:, ROI_X2:] = 0
    kernel = np.ones((3, 3), np.uint8)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel)
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    contours = [c for c in contours if cv2.contourArea(c) >= 250]
    if not contours:
        return None
    contour = max(contours, key=cv2.contourArea)
    x, y, w, h = cv2.boundingRect(contour)
    if w < 20 or h < 15:
        return None
    return x, y, w, h, float(cv2.contourArea(contour))


def main():
    cap = cv2.VideoCapture(str(SOURCE))
    if not cap.isOpened():
        raise SystemExit("Cannot open test.mp4")
    fps = float(cap.get(cv2.CAP_PROP_FPS))
    count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    first = max(0, int(math.floor(START_S * fps)))
    last = min(count - 1, int(math.ceil(END_S * fps)))

    observations = []
    images = []
    previous = None
    for index in range(first, last + 1):
        cap.set(cv2.CAP_PROP_POS_FRAMES, index)
        ok, frame = cap.read()
        if not ok:
            continue
        detection = detect_mail(frame)
        seconds = index / fps
        change = None if previous is None else float(np.mean(cv2.absdiff(frame, previous)))
        row = {
            "frame": index,
            "seconds": round(seconds, 6),
            "relative_ms": round((seconds - START_S) * 1000.0, 3),
            "x": "", "y": "", "width": "", "height": "",
            "center_x": "", "center_y": "", "area": "",
            "mean_pixel_change": "" if change is None else round(change, 6),
        }
        display = frame.copy()
        if detection is not None:
            x, y, w, h, area = detection
            row.update({
                "x": x, "y": y, "width": w, "height": h,
                "center_x": round(x + w / 2.0, 3),
                "center_y": round(y + h / 2.0, 3),
                "area": round(area, 3),
            })
            cv2.rectangle(display, (x, y), (x + w, y + h), (255, 255, 255), 2)
        observations.append(row)
        previous = frame

        # Preserve the source pixels for the early low-opacity phase, where HSV segmentation is
        # intentionally conservative and cannot supply trustworthy geometry by itself.
        cv2.imwrite(str(FRAMES / f"frame_{index:04d}_{seconds:.3f}.png"), frame[:650, :900])

        rgb = cv2.cvtColor(display, cv2.COLOR_BGR2RGB)
        image = Image.fromarray(rgb)
        image.thumbnail((480, 270))
        images.append((index, seconds, row, image.copy()))
    cap.release()

    with (OUT / "recorded_frames.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=observations[0].keys())
        writer.writeheader()
        writer.writerows(observations)

    detected = [r for r in observations if r["x"] != ""]
    if not detected:
        raise SystemExit("Mail tile was not detected")
    # Pick the first stable 248x120 geometry after the entrance, rather than blindly trusting the
    # last frame if another cyan element later enters the broad ROI.
    stable = [r for r in detected if r["width"] == 248 and r["height"] == 120]
    settled = stable[-1] if stable else detected[-1]
    settled_cx = float(settled["center_x"])
    settled_w = float(settled["width"])
    settled_h = float(settled["height"])

    measured = []
    for r in detected:
        # Reject false detections that are not plausibly the Mail tile's aspect/size near this clip.
        w, h = float(r["width"]), float(r["height"])
        if not (0.75 <= (w / max(h, 1.0)) <= 2.35 and w <= 300 and h <= 180):
            continue
        measured.append({
            **r,
            "center_dx": round(float(r["center_x"]) - settled_cx, 4),
            "scale_x": round(w / settled_w, 6),
            "scale_y": round(h / settled_h, 6),
        })
    with (OUT / "measured_geometry.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=measured[0].keys())
        writer.writeheader()
        writer.writerows(measured)

    times_ms = np.array([float(r["seconds"]) * 1000.0 for r in measured])
    dx = np.array([float(r["center_dx"]) for r in measured])
    sx = np.array([float(r["scale_x"]) for r in measured])
    sy = np.array([float(r["scale_y"]) for r in measured])
    base_ms = times_ms[0]
    target = np.arange(times_ms[0], times_ms[-1] + 0.01, 10.0)
    interp_rows = []
    for t in target:
        interp_rows.append({
            "elapsed_from_first_detected_ms": round(t - base_ms, 3),
            "source_seconds": round(t / 1000.0, 6),
            "center_dx": round(float(np.interp(t, times_ms, dx)), 5),
            "scale_x": round(float(np.interp(t, times_ms, sx)), 6),
            "scale_y": round(float(np.interp(t, times_ms, sy)), 6),
        })
    with (OUT / "interpolated_10ms.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=interp_rows[0].keys())
        writer.writeheader()
        writer.writerows(interp_rows)

    cols = 4
    cell_w, cell_h = 480, 304
    rows = math.ceil(len(images) / cols)
    sheet = Image.new("RGB", (cols * cell_w, rows * cell_h), "#181818")
    draw = ImageDraw.Draw(sheet)
    for n, (index, seconds, row, image) in enumerate(images):
        x = (n % cols) * cell_w
        y = (n // cols) * cell_h
        sheet.paste(image, (x, y))
        geom = "not detected" if row["x"] == "" else f"x={row['x']} y={row['y']} w={row['width']} h={row['height']}"
        draw.text((x + 6, y + 273), f"f{index} {seconds:.3f}s | {geom}", fill="white")
    sheet.save(OUT / "every_frame_contact.jpg", quality=94)

    summary = {
        "fps": fps,
        "frame_interval_ms": 1000.0 / fps,
        "frames_scanned": len(observations),
        "first_detected_seconds": measured[0]["seconds"],
        "last_measured_seconds": measured[-1]["seconds"],
        "settled": {"x": settled["x"], "y": settled["y"], "width": settled["width"], "height": settled["height"]},
        "measured": measured,
    }
    (OUT / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
