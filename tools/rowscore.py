"""Row flicker in a region of a frame sequence: how much each pixel row's brightness jumps around its own median
over the run. Smooth camera motion barely changes a far region's row averages; bands switching on and off do.

    python rowscore.py <frames dir> [x0 y0 x1 y1]   (default: the far mountain in the live view)
"""
import glob, sys
import numpy as np
from PIL import Image

d = sys.argv[1]
x0, y0, x1, y1 = map(int, sys.argv[2:6]) if len(sys.argv) >= 6 else (1150, 540, 1550, 720)
fs = sorted(glob.glob(d + '/strafe_*.png'))
rows = np.stack([np.asarray(Image.open(f).convert('L'), dtype=np.float32)[y0:y1, x0:x1].mean(1) for f in fs], 1)
dev = rows - np.median(rows, axis=1, keepdims=True)
jumps = np.abs(np.diff(dev, axis=1))
print(f'{d}: {len(fs)} frames, row deviation {np.abs(dev).mean():.2f}, frame-to-frame row jumps {jumps.mean():.2f} (max {jumps.max():.1f})')
