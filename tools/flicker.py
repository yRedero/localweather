"""Finds flicker in a sequence of frames from `./gradlew runClient -PdevScene=strafe`.

A pixel flickers when it jumps one way and straight back between consecutive frames. Smooth camera movement
changes pixels in one direction at a time; flicker flips back and forth. This counts such flips per pixel.

    python tools/flicker.py <frames dir> <out dir> [<reference frames dir>] [--region x0 y0 x1 y1]

With a reference run (same camera path, e.g. without Better Clouds), it also measures flicker in the difference
between the two runs, which isolates what the clouds add.

Writes heat.png (flips over the middle frame), crop.png (the worst spot over three frames, magnified) and prints
statistics per horizontal band of the screen.
"""
import glob
import os
import sys

import numpy as np
from PIL import Image

SCALE = 2          # analyse at half resolution
THRESHOLD = 18.0   # brightness jump (0-255) that counts
MIN_FLIPS = 3      # pixels with at least this many flips count as flickering


def load(directory):
    files = sorted(glob.glob(os.path.join(directory, "strafe_*.png")))
    if len(files) < 3:
        sys.exit(f"need at least 3 frames in {directory}, found {len(files)}")
    frames = []
    for f in files:
        img = Image.open(f).convert("L")
        img = img.resize((img.width // SCALE, img.height // SCALE), Image.BILINEAR)
        frames.append(np.asarray(img, dtype=np.float32))
    return np.stack(frames), files


def flips(stack):
    d = np.diff(stack, axis=0)
    big = np.abs(d) > THRESHOLD
    opposite = np.sign(d[1:]) != np.sign(d[:-1])
    return (big[1:] & big[:-1] & opposite).sum(axis=0)


def report(name, count):
    h = count.shape[0]
    print(f"{name}: pixels with >= {MIN_FLIPS} flips: {(count >= MIN_FLIPS).mean() * 100:.2f}%")
    for i, label in enumerate(["top", "upper middle", "lower middle", "bottom"]):
        band = count[i * h // 4:(i + 1) * h // 4]
        print(f"  {label:13s} {(band >= MIN_FLIPS).mean() * 100:6.2f}%  max {band.max()}")


def heat(count, base, path):
    rgb = np.stack([base] * 3, axis=-1) * 0.6
    level = np.clip(count / 8.0, 0, 1)
    rgb[..., 0] = np.maximum(rgb[..., 0], level * 255)
    rgb[..., 1] *= 1 - level
    rgb[..., 2] *= 1 - level
    Image.fromarray(rgb.astype(np.uint8)).save(path)


def crop(count, files, path, size=80):
    blurred = count.astype(np.float32)
    # Densest spot: sum flips over a window.
    k = size // 2
    integral = blurred.cumsum(0).cumsum(1)
    best, at = -1, (0, 0)
    for y in range(k, count.shape[0] - k, 8):
        for x in range(k, count.shape[1] - k, 8):
            s = integral[y + k, x + k] - integral[y - k, x + k] - integral[y + k, x - k] + integral[y - k, x - k]
            if s > best:
                best, at = s, (y, x)
    y, x = at
    mid = len(files) // 2
    tiles = []
    for f in files[mid - 1:mid + 2]:
        img = Image.open(f).convert("RGB")
        box = ((x - k) * SCALE, (y - k) * SCALE, (x + k) * SCALE, (y + k) * SCALE)
        tiles.append(img.crop(box).resize((size * 4, size * 4), Image.NEAREST))
    out = Image.new("RGB", (size * 4 * 3 + 20, size * 4), (255, 0, 255))
    for i, t in enumerate(tiles):
        out.paste(t, (i * (size * 4 + 10), 0))
    out.save(path)
    print(f"worst spot at ({x * SCALE}, {y * SCALE}) full-resolution pixels, {best:.0f} flips in the window")


def main():
    region = None
    if "--region" in sys.argv:
        i = sys.argv.index("--region")
        region = [int(v) // SCALE for v in sys.argv[i + 1:i + 5]]
        del sys.argv[i:i + 5]
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    frames, files = load(sys.argv[1])
    os.makedirs(sys.argv[2], exist_ok=True)
    count = flips(frames)
    report("frames", count)
    if region:
        x0, y0, x1, y1 = region
        part = count[y0:y1, x0:x1]
        print(f"region: pixels with >= {MIN_FLIPS} flips: {(part >= MIN_FLIPS).mean() * 100:.2f}%, total flips {part.sum()}")
    heat(count, frames[len(frames) // 2], os.path.join(sys.argv[2], "heat.png"))
    crop(count, files, os.path.join(sys.argv[2], "crop.png"))
    if len(sys.argv) > 3:
        ref, _ = load(sys.argv[3])
        n = min(len(ref), len(frames))
        ref_count = flips(ref[:n])
        report("reference", ref_count)
        diff_count = flips(frames[:n] - ref[:n])
        report("difference (what the clouds add)", diff_count)
        heat(diff_count, frames[n // 2], os.path.join(sys.argv[2], "heat_diff.png"))


if __name__ == "__main__":
    main()
