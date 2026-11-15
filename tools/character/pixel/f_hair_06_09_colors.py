import os
import sys
import numpy as np
from PIL import Image

RAW, DST = sys.argv[1:3]
LUM = np.array([0.299, 0.587, 0.114])
OUTLINE = np.array([56, 12, 6])

def load(f):
    return np.asarray(Image.open(f).convert("RGBA")).astype(float)

luts = {}
for c in range(8):
    sums = np.zeros((256, 3)); cnt = np.zeros(256)
    for h in range(6):
        for part in ("front", "back"):
            f1 = f"{DST}/hair_{h}_color_1_{part}.webp"
            if not os.path.exists(f1):
                continue
            a, b = load(f1), load(f"{DST}/hair_{h}_color_{c}_{part}.webp")
            m = (a[..., 3] > 250) & (b[..., 3] > 250)
            l = np.clip((a[m][:, :3] @ LUM).round(), 0, 255).astype(int)
            np.add.at(sums, l, b[m][:, :3]); np.add.at(cnt, l, 1)
    have = cnt > 3
    xs = np.arange(256)
    lut = np.stack([np.interp(xs, xs[have], sums[have, k] / cnt[have]) for k in range(3)], 1)
    luts[c] = lut

for name in sorted(os.listdir(RAW)):
    if not (name.startswith("raw_") and name.endswith(".png")):
        continue
    stem = name[4:-4]
    h, part = (stem.split("_")[0], "back") if stem.endswith("_back") else (stem, "front")
    a = load(f"{RAW}/{name}")
    outline = np.abs(a[..., :3] - OUTLINE).sum(2) < 30
    l = np.clip((a[..., :3] @ LUM).round(), 0, 255).astype(int)
    for c in range(8):
        o = a.copy()
        if c != 1:
            rec = luts[c][l]
            o[..., :3] = np.where(outline[..., None], a[..., :3], rec)
        Image.fromarray(o.round().astype("uint8"), "RGBA").save(f"{DST}/hair_{h}_color_{c}_{part}.webp", "WEBP", quality=85, method=6)
    print(h, part, "8 colors")
