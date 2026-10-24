#!/usr/bin/env python3
"""Small review assets from real Compose captures; no full-frame artifact upload."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

ROOT = Path('app/build/motion-frames')
OUT = Path('app/build/w10m-review')
PHASES = [
    ('start-back-return', 10, 520),
    ('w10-apps-back-return', 10, 360),
    ('w10-apps-launch', 16, 400),
]


def ffmpeg(*args):
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-hide_banner', '-loglevel', 'error', *map(str, args)], check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    notes = ['Rendered Compose captures. Timestamps are model time, not native video timestamps.',
             'Native reference: WinFuture Lumia 950/950 XL, 25 fps (40 ms source intervals).',
             'Back was sampled at 10 ms; launch was captured every 16 ms. No optical interpolation.']
    for name, step, end in PHASES:
        pattern = re.compile(re.escape(name) + r'-(\d{3,4})\.png$')
        frames = {}
        for path in ROOT.rglob(name + '-*.png'):
            match = pattern.fullmatch(path.name)
            if match and int(match[1]) <= end:
                frames[int(match[1])] = path
        if len(frames) < 20 or 0 not in frames:
            raise SystemExit(f'Missing review captures for {name}: {len(frames)} frames')
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            ordered = [frames[t] for t in sorted(frames)]
            sequence = [ordered[0]] * round(400 / step) + ordered + [ordered[-1]] * round(600 / step)
            for i, path in enumerate(sequence):
                shutil.copyfile(path, work / f'video-{i:04d}.png')
            ffmpeg('-framerate', f'1000/{step}', '-i', work / 'video-%04d.png',
                   '-vf', 'scale=270:540', '-c:v', 'libx264', '-crf', '20',
                   '-pix_fmt', 'yuv420p', '-movflags', '+faststart', OUT / f'{name}.mp4')
            selected = sorted({min(frames, key=lambda t: abs(t-target)) for target in range(0, end+1, 40)})
            rows = (len(selected)+4)//5
            sheet = Image.new('RGB', (216*5, 458*rows), '#101010')
            draw = ImageDraw.Draw(sheet)
            font = ImageFont.load_default(size=16)
            for i, ms in enumerate(selected):
                x, y = (i % 5)*216, (i // 5)*458
                with Image.open(frames[ms]) as frame:
                    sheet.paste(frame.convert('RGB').resize((216, 432), Image.Resampling.LANCZOS), (x, y+26))
                draw.text((x+8, y+6), f'{ms} ms', font=font, fill='white')
            sheet.save(OUT / f'{name}.png')
            notes.append(name + ': ' + ', '.join(map(str, selected)) + ' ms')
    (OUT / 'capture-times.txt').write_text('\n'.join(notes) + '\n')
    print(f'Compact W10M review written to {OUT}')


if __name__ == '__main__':
    main()
