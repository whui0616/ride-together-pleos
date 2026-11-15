import sys
import numpy as np
from PIL import Image, ImageFilter

PREV, REF, OUT = sys.argv[1:4]
S, OFF = 0.78, 1
CELLS = {6: (0, 0), 7: (627, 0), 8: (0, 627), 9: (627, 627)}
CELL = round(800 * S)
EAR_TOP = 330
CHIN_Y = 290

P = Image.open(PREV).convert("RGBA")
R = Image.open(REF).convert("RGBA").crop((0, 0, 800, 800)).resize((CELL, CELL), Image.LANCZOS)
r = np.asarray(R).astype(int)
_op = r[..., 3] >= 128
_rows = [np.where(_op[y])[0] for y in range(CELL)]
_w = [(xs[-1] - xs[0]) if len(xs) else 0 for xs in _rows]
_skull_y = int(np.argmax(_w[:300]))
_sl, _sr = _rows[_skull_y][0], _rows[_skull_y][-1]
ears = np.zeros_like(_op); ears[:, :_sl - 1] = _op[:, :_sl - 1]; ears[:, _sr + 2:] = _op[:, _sr + 2:]
ears = np.asarray(Image.fromarray((ears * 255).astype("uint8")).filter(ImageFilter.MaxFilter(5))) > 0
skin = np.median(r[(r[..., 3] > 250) & (r[..., :3].sum(2) > 600)][:, :3], axis=0)

def components(mask):
    h, w = mask.shape
    lab = np.zeros((h, w), int); n = 0; sizes = [0]
    for y in range(h):
        for x in range(w):
            if mask[y, x] and not lab[y, x]:
                n += 1; st = [(y, x)]; lab[y, x] = n; c = 0
                while st:
                    cy, cx = st.pop(); c += 1
                    for yy, xx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                        if 0 <= yy < h and 0 <= xx < w and mask[yy, xx] and not lab[yy, xx]:
                            lab[yy, xx] = n; st.append((yy, xx))
                sizes.append(c)
    return lab, np.array(sizes)

for hid, (ox, oy) in CELLS.items():
    cell = P.crop((ox + OFF, oy + OFF, ox + OFF + CELL, oy + OFF + CELL))
    p = np.asarray(cell).astype(int)
    pa, ra = p[..., 3], r[..., 3]
    def prem(a):
        return np.concatenate([a[..., :3] * a[..., 3:4] / 255, a[..., 3:4]], axis=2)
    pp, rp = prem(p), prem(r)
    dmin = np.full(pa.shape, 1e9)
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            sh = np.roll(np.roll(rp, dy, 0), dx, 1)
            dmin = np.minimum(dmin, np.abs(pp - sh).sum(2))
    cand = (pa > 40) & (dmin > 110)
    skinlike = np.abs(p[..., :3] - skin).sum(2) < 70
    cand &= ~skinlike
    lab, sizes = components(cand)
    keep = sizes >= 40; keep[0] = False
    mask = keep[lab]
    m_img = Image.fromarray((mask * 255).astype("uint8"))
    closed = np.asarray(m_img.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(7))) > 0
    mask = mask | (closed & (pa > 40) & ~skinlike)
    inside = Image.fromarray(((ra >= 128) * 255).astype("uint8"))
    edge = (np.asarray(inside.filter(ImageFilter.MaxFilter(3))) > 0) & ~(np.asarray(inside.filter(ImageFilter.MinFilter(3))) > 0)
    ring = np.asarray(Image.fromarray((edge * 255).astype("uint8")).filter(ImageFilter.MaxFilter(23))) > 0
    ring &= ~ears
    ring[EAR_TOP:] = False
    mask = mask | (ring & (pa > 40) & ~skinlike)
    hairish = (p[..., 0] > p[..., 1]) & (p[..., 1] > p[..., 2] + 6)
    mask &= ~(ears & ~hairish)
    lab, _ = components(mask)
    face_envelope = np.asarray(inside.filter(ImageFilter.MaxFilter(23))) > 0
    attached = set(np.unique(lab[:EAR_TOP]))
    for label in np.unique(lab[EAR_TOP:]):
        if label and label not in attached:
            piece = lab == label
            if np.all(face_envelope[piece]):
                mask[piece] = False
    out = p.copy(); out[..., 3] = np.where(mask, pa, 0)
    img = Image.fromarray(out.astype("uint8"), "RGBA")
    small = img.convert("RGBa").resize((400, 400), Image.LANCZOS).convert("RGBA")
    canvas = Image.new("RGBA", (400, 600), (0, 0, 0, 0)); canvas.paste(small, (0, 0))
    a = np.asarray(canvas).copy()
    face = Image.open(REF).convert("RGBA").crop((0, 0, 800, 800)).resize((400, 400), Image.Resampling.NEAREST)
    face_solid = np.zeros((600, 400), dtype=bool)
    face_solid[:400] = np.asarray(face)[..., 3] >= 128
    hair_solid = a[..., 3] >= 128
    def expand(m):
        return np.asarray(Image.fromarray((m * 255).astype("uint8")).filter(ImageFilter.MaxFilter(7))) > 0
    union = Image.fromarray(((face_solid | hair_solid) * 255).astype("uint8"))
    joined = np.asarray(union.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(7))) > 0
    gaps = joined & ~face_solid & ~hair_solid & expand(face_solid) & expand(hair_solid)
    offsets = sorted(((dx, dy) for dy in range(-3, 4) for dx in range(-3, 4)), key=lambda v: v[0]**2 + v[1]**2)
    original = a.copy()
    for dx, dy in offsets:
        neighbor = np.roll(np.roll(original, dy, 0), dx, 1)
        fill = gaps & (neighbor[..., 3] >= 128)
        a[fill, :3] = neighbor[fill, :3]
        a[fill, 3] = 255
        gaps[fill] = False
    assert not gaps.any(), "Unfilled face/hair seam"
    if hid == 6:
        back = a.copy(); back[:CHIN_Y, :, 3] = 0
        a[CHIN_Y:, :, 3] = 0
        Image.fromarray(back).save(f"{OUT}/raw_{hid}_back.png")
    Image.fromarray(a).save(f"{OUT}/raw_{hid}.png")
    print(hid, "hair px", int(mask.sum()))
