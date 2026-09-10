"""Measure each encoded frame of the Windows 8.1 half of the supplied comparison.

The input is the user's Rbn0z4ylKvc video, saved in the ignored build folder.
Coordinates refer to the right 960-pixel crop, not a complete Windows desktop.
Opacity is an RGB estimate from the yellow Desktop tile, not an OS property.
"""
import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".tools/python"))
import cv2
import numpy as np
from PIL import Image, ImageDraw


def main():
    folder = ROOT / "build/animation-analysis/start-refinement"
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else folder / "comparison.mp4"
    cap = cv2.VideoCapture(str(source))
    if not cap.isOpened():
        raise SystemExit(f"Cannot open {source}")
    fps = cap.get(cv2.CAP_PROP_FPS)
    cap.set(cv2.CAP_PROP_POS_MSEC, 34500)
    ok, settled = cap.read()
    if not ok:
        raise SystemExit("Missing settled reference frame")
    foreground = settled[225:245, 1075:1095].mean(axis=(0, 1))
    cap.set(cv2.CAP_PROP_POS_MSEC, 32000)
    ok, first = cap.read()
    background = first[225:245, 1075:1095].mean(axis=(0, 1))
    color_delta = foreground - background
    rows = []
    previous = None
    cap.set(cv2.CAP_PROP_POS_FRAMES, round(32 * fps))
    for index in range(round(32 * fps), round(34.8 * fps) + 1):
        ok, frame = cap.read()
        if not ok:
            raise SystemExit(f"Missing encoded frame {index}")
        right = frame[:, 960:1920]
        mask = cv2.inRange(cv2.cvtColor(right, cv2.COLOR_BGR2HSV),
                           (12, 90, 45), (40, 255, 255))
        mask[650:] = 0
        contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        contours = [c for c in contours if cv2.contourArea(c) > 1000]
        bounds = cv2.boundingRect(max(contours, key=cv2.contourArea)) if contours else None
        row = dict(frame=index, source_seconds=round(index / fps, 6),
                   elapsed_ms=round((index / fps - 32) * 1000, 3),
                   x="", y="", width="", height="", scale_x="", center_dx="",
                   estimated_opacity="",
                   mean_pixel_change="" if previous is None else
                       round(float(np.abs(right.astype(float) - previous).mean()), 6))
        if bounds:
            x, y, w, h = bounds
            sample = right[y + 12:y + 22, x + 12:x + 22].mean(axis=(0, 1))
            alpha = float(np.dot(sample - background, color_delta) / np.dot(color_delta, color_delta))
            row.update(x=x, y=y, width=w, height=h, scale_x=round(w / 248, 6),
                       center_dx=round(x + w / 2 - 224, 3),
                       estimated_opacity=round(max(0, min(1, alpha)), 4))
        rows.append(row)
        previous = right.astype(float)
        cv2.imwrite(str(folder / f"measured-{index:04}.png"), right)
    cap.release()
    target = ROOT / "docs/start-comparison-recorded-frames.csv"
    with target.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=rows[0])
        writer.writeheader()
        writer.writerows(rows)
    for page in range((len(rows) + 29) // 30):
        sheet = Image.new("RGB", (1200, 5 * 250), "#202020")
        draw = ImageDraw.Draw(sheet)
        for i, row in enumerate(rows[page * 30:(page + 1) * 30]):
            with Image.open(folder / f"measured-{row['frame']:04}.png") as img:
                img.thumbnail((200, 225))
                x, y = i % 6 * 200, i // 6 * 250
                sheet.paste(img, (x, y))
                draw.text((x + 4, y + 228), f"{row['source_seconds']:.3f}s", fill="white")
        sheet.save(folder / f"measured-sheet-{page + 1}.jpg", quality=90)
    print(json.dumps(dict(fps=fps, measured_frames=len(rows), measurements=str(target))))


if __name__ == "__main__":
    main()
