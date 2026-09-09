"""Extract every launch frame and its colored card outline from the reference.

Usage: python scripts/analyze_launch_video.py path/to/reference.mp4
Requires opencv-python-headless, numpy and Pillow. Outputs stay in build/.
"""
import argparse
import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".tools" / "python"))
import cv2
import numpy as np
from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/animation-analysis/reference")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    capture = cv2.VideoCapture(str(args.video))
    if not capture.isOpened():
        raise SystemExit(f"Cannot open {args.video}")
    fps = capture.get(cv2.CAP_PROP_FPS)
    metadata = dict(source=str(args.video), fps=fps,
                    frames=int(capture.get(cv2.CAP_PROP_FRAME_COUNT)),
                    width=int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)),
                    height=int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)))
    (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2))
    # These are absolute times in the user-supplied recording. Include a resting
    # frame and the settled splash to distinguish press, turn, and app handoff.
    sequences = [("mail", 6.10, 6.84), ("reading_list", 18.30, 19.10),
                 ("money", 33.90, 34.64), ("all_apps_help", 59.40, 60.07),
                 ("start_entrance", 56.033, 56.700)]
    rows = []
    for name, start, end in sequences:
        indices = list(range(round(start * fps), round(end * fps) + 1))
        sheet = Image.new("RGB", (4 * 480, ((len(indices) + 3) // 4) * 294), "#191919")
        draw = ImageDraw.Draw(sheet)
        previous = None
        for n, index in enumerate(indices):
            capture.set(cv2.CAP_PROP_POS_FRAMES, index)
            ok, frame = capture.read()
            if not ok:
                raise RuntimeError(f"Missing frame {index}")
            cv2.imwrite(str(args.output / f"{name}_{index:04}.png"), frame)
            b, g, r = cv2.split(frame)
            if name == "start_entrance":
                hsv = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV)
                mask = cv2.inRange(hsv, (85, 100, 20), (105, 255, 255)) > 0
                # Only the first Mail tile; other tiles share the cyan accent.
                mask[:150, :] = False
                mask[400:, :] = False
                mask[:, 500:] = False
            elif name == "mail":
                mask = (b > 170) & (b < 230) & (g > 95) & (g < 165) & (r < 30)
            elif name == "money":
                mask = (g > 100) & (r < 40) & (b < 40)
            elif name == "reading_list":
                mask = (r > 115) & (g < 45) & (b > 35) & (b < 130)
            else:
                mask = (r > 140) & (g > 25) & (g < 100) & (b < 80)
            contours, _ = cv2.findContours(mask.astype(np.uint8) * 255,
                                          cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            contour = max(contours, key=cv2.contourArea) if contours else None
            polygon = cv2.approxPolyDP(contour, 4, True).reshape(-1, 2).tolist() if contour is not None else []
            difference = float(np.mean(cv2.absdiff(frame, previous))) if previous is not None else None
            rows.append(dict(sequence=name, frame=index, seconds=round(index / fps, 6),
                             mean_pixel_change=difference,
                             area_px=cv2.contourArea(contour) if contour is not None else 0,
                             polygon=json.dumps(polygon)))
            previous = frame
            image = Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
            image.thumbnail((480, 270))
            x, y = n % 4 * 480, n // 4 * 294
            sheet.paste(image, (x, y))
            draw.text((x + 8, y + 273), f"{index} | {index/fps:.3f} s", fill="white")
        sheet.save(args.output / f"{name}_every_frame.jpg", quality=92)
    capture.release()
    with (args.output / "measurements.csv").open("w", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    print(json.dumps(metadata))
    print(f"Extracted {len(rows)} launch frames to {args.output}")


if __name__ == "__main__":
    main()
