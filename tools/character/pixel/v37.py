import os
import sys

from PIL import Image

SKINS = ["FFE8D8", "F4CDAE", "E3B28E", "C18B63", "955F42", "633D2E", "FFF3EA", "4C2E22", "3A231B"]

def tint(im, hex_):
    c = [int(hex_[i:i + 2], 16) for i in (0, 2, 4)]
    hi_to = (255, 244, 233)
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a and r > g + 20 and b > g + 20 and r + b > 100:
                sh = .52 + .48 * max(r, b) / 255
                hi = min(1, g / 255 * .35)
                px[x, y] = tuple(max(0, min(255, round(c[i] * sh + (hi_to[i] - c[i] * sh) * hi))) for i in range(3)) + (a,)
    return im

def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.resize((im.width // 2, im.height // 2), Image.BOX).save(path, "WEBP", quality=85, method=6)

def main(src, dst):
    t = os.path.join(src, "templates")
    for g in "fm":
        body = Image.open(os.path.join(t, f"{g}_body_base.png")).convert("RGBA")
        assert body.size == (800, 1200), body.size
        for s, hex_ in enumerate(SKINS):
            save(tint(body.copy(), hex_), os.path.join(dst, g, "body", f"body_skin_{s}.webp"))
        for kind in ("top", "bottom", "shoes"):
            for n in range(20):
                im = Image.open(os.path.join(t, f"{g}_{kind}_{n:02d}.png")).convert("RGBA")
                assert im.size == (800, 1200), (kind, n, im.size)
                save(im, os.path.join(dst, g, kind, f"{kind}_{n:02d}.webp"))
        print(g, "done")

if __name__ == "__main__":
    main(*sys.argv[1:3])
