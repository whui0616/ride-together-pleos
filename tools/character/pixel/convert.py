import pathlib, re, sys
from PIL import Image

src, dst = pathlib.Path(sys.argv[1]) / "png", pathlib.Path(sys.argv[2])
n = 0
for f in sorted(src.rglob("*.png")):
    if re.search(r"hair_\d_color_8", f.name):
        continue
    out = dst / f.relative_to(src).with_suffix(".webp")
    out.parent.mkdir(parents=True, exist_ok=True)
    im = Image.open(f).convert("RGBA")
    assert im.size == (800, 1200), f
    im.resize((400, 600), Image.LANCZOS).save(out, "WEBP", quality=85, method=6)
    n += 1
print(n, "files")
