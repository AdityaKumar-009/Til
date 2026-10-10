#!/usr/bin/env python3
"""Encode the instrumented WP8.1 All Apps-to-Start frames as a review video."""

from pathlib import Path
import re
import shutil
import subprocess


ROOT = Path("app/build/motion-frames")
OUTPUT_DIR = Path("app/build/motion-preview")
SEQUENCE_DIR = OUTPUT_DIR / "sequence"
OUTPUT = OUTPUT_DIR / "Til-WP81-Home-Return-Preview.mp4"


def main() -> None:
    before = next(ROOT.rglob("wp81-apps-home-return-before.png"), None)
    settled = next(ROOT.rglob("wp81-home-return-settled.png"), None)
    start = next(ROOT.rglob("wp81-home-return-000.png"), None)
    if before is None or settled is None or start is None:
        raise SystemExit("WP8.1 Home-return captures are missing; preview was not rendered.")

    frame_pattern = re.compile(r"wp81-home-return-(\d{3,4})\.png$")
    transition_frames = sorted(
        (path for path in ROOT.rglob("wp81-home-return-*.png")
         if frame_pattern.match(path.name)),
        key=lambda path: int(frame_pattern.match(path.name).group(1)),
    )
    # Include the four-digit timestamps of the native-stagger entrance.
    if len(transition_frames) < 20:
        raise SystemExit(f"Only {len(transition_frames)} WP8.1 transition frames were captured.")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    if SEQUENCE_DIR.exists():
        shutil.rmtree(SEQUENCE_DIR)
    SEQUENCE_DIR.mkdir(parents=True)

    # Hold the incoming All Apps screen and the settled Start screen long enough
    # to compare them while keeping the captured animation at 60 fps.
    sequence = [before] * 30 + transition_frames + [settled] * 30
    for index, source in enumerate(sequence):
        shutil.copyfile(source, SEQUENCE_DIR / f"frame-{index:05d}.png")

    subprocess.run(
        [
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-framerate", "60",
            "-i", str(SEQUENCE_DIR / "frame-%05d.png"),
            "-c:v", "libx264", "-crf", "18", "-preset", "medium",
            "-pix_fmt", "yuv420p", "-movflags", "+faststart",
            str(OUTPUT),
        ],
        check=True,
    )
    print(f"Rendered {OUTPUT} from {len(transition_frames)} animation frames.")


if __name__ == "__main__":
    main()
