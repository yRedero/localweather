"""Flicker restricted to Voxy terrain: per-pixel brightness jumps that reverse between consecutive frames,
counted only where the frame's lod_depth.png says Voxy drew terrain (grey). Near terrain moves too much
while strafing to tell flicker from motion; far LOD terrain barely moves.

    python lodflick.py <frames dir> [<out heat png>] [--mask <lod_depth.png>]

--mask uses another run's lod_depth.png (same camera path), e.g. for a run without Better Clouds, which saves none.
"""
import glob, sys
import numpy as np
from PIL import Image

mask = None
if '--mask' in sys.argv:
    i = sys.argv.index('--mask')
    mask = sys.argv[i + 1]
    del sys.argv[i:i + 2]
d = sys.argv[1]
fs = sorted(glob.glob(d + '/strafe_*.png'))
lod = np.asarray(Image.open(mask or d + '/lod_depth.png').convert('RGB')).astype(int)
grey = (np.abs(lod[..., 0] - lod[..., 1]) < 8) & (np.abs(lod[..., 1] - lod[..., 2]) < 8) & (lod[..., 0] > 20)
st = np.stack([np.asarray(Image.open(f).convert('L'), dtype=np.float32) for f in fs])
if st.shape[1:] != grey.shape:
    sys.exit(f'frame size {st.shape[1:]} differs from lod_depth {grey.shape}')
df = np.diff(st, axis=0)
big = np.abs(df) > 18
opp = np.sign(df[1:]) != np.sign(df[:-1])
fl = (big[1:] & big[:-1] & opp).sum(0)
print(f'{d}: {len(fs)} frames, LOD pixels {grey.sum()}, flickering on LOD terrain: {(fl[grey] >= 3).mean() * 100:.2f}%')
if len(sys.argv) > 2:
    Image.fromarray(np.where(grey, np.clip(fl * 30, 0, 255), 0).astype(np.uint8)).save(sys.argv[2])
