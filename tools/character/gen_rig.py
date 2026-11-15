import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "character", "rig.json")

LINE = "$line"
LW = 1.6

def mirror_d(d):
    toks = re.findall(r"[MLQCZ]|-?\d+(?:\.\d+)?", d)
    out, idx = [], 0
    for t in toks:
        if t in "MLQCZ":
            out.append(t)
            idx = 0
        else:
            v = float(t)
            if idx % 2 == 0:
                v = -v
            out.append(("%g" % v))
            idx += 1
    return " ".join(out)

parts = []

def part(bone, z, shape, fill=None, stroke=LINE, sw=LW, **cond):
    p = {"bone": bone, "z": z}
    p.update(shape)
    if fill:
        p["fill"] = fill
    if stroke:
        p["stroke"] = stroke
        p["sw"] = sw
    for k, v in cond.items():
        if k == "opacity":
            p["opacity"] = v
        else:
            p[k] = v
    parts.append(p)
    return p

def path(d):
    return {"d": d}

def ell(cx, cy, rx, ry):
    return {"ellipse": [cx, cy, rx, ry]}

def both(bone_l, z, shape, **kw):
    part(bone_l, z, shape, **kw)
    bone_r = bone_l[:-1] + "R"
    if "d" in shape:
        part(bone_r, z, {"d": mirror_d(shape["d"])}, **kw)
    else:
        cx, cy, rx, ry = shape["ellipse"]
        part(bone_r, z, ell(-cx, cy, rx, ry), **kw)

def sym(bone, z, shape, **kw):
    part(bone, z, shape, **kw)
    if "d" in shape:
        part(bone, z, {"d": mirror_d(shape["d"])}, **kw)
    else:
        cx, cy, rx, ry = shape["ellipse"]
        part(bone, z, ell(-cx, cy, rx, ry), **kw)

def capsule(w, y0, y1, w2=None):
    a = w / 2
    b = (w2 if w2 else w) / 2
    return path(
        f"M {-a} {y0} L {-b} {y1} Q {-b} {y1 + b} 0 {y1 + b} Q {b} {y1 + b} {b} {y1} "
        f"L {a} {y0} Q {a} {y0 - a} 0 {y0 - a} Q {-a} {y0 - a} {-a} {y0} Z"
    )

BONES = [
    ["root", None, 100, 214, 0, False],
    ["torso", "root", 0, 0, 0, False],
    ["neck", "torso", 0, -48, 0, False],
    ["head", "neck", 0, -4, 0, False],
    ["upperArmL", "torso", -16, -43, 28, False],
    ["forearmL", "upperArmL", 0, 21, 14, False],
    ["handL", "forearmL", 0, 19, 0, False],
    ["upperArmR", "torso", 16, -43, 28, True],
    ["forearmR", "upperArmR", 0, 21, 14, True],
    ["handR", "forearmR", 0, 19, 0, True],
    ["thighL", "root", -9, 2, 3, False],
    ["shinL", "thighL", 0, 26, -3, False],
    ["footL", "shinL", 0, 24, 0, False],
    ["thighR", "root", 9, 2, 3, True],
    ["shinR", "thighR", 0, 26, -3, True],
    ["footR", "shinR", 0, 24, 0, True],
]

both("shinL", 5, capsule(11, 0, 22, 9), fill="$skin")
both("footL", 6, path("M -6 -2 Q -9 6 -2 8 Q 6 9 8 5 Q 8 -1 4 -3 Q -1 -4 -6 -2 Z"), fill="$skin", slot="shoes", option="none")
both("thighL", 8, capsule(13, 0, 26, 11), fill="$skin")

part("neck", 11, path("M -5 -6 L 5 -6 L 5 6 L -5 6 Z"), fill="$skin")
part("neck", 11.5, path("M -5 -2 Q 0 2 5 -2 L 5 2 Q 0 5 -5 2 Z"), fill="$skinShade", stroke=None)
part("torso", 12, path(
    "M -14 -47 Q 0 -51 14 -47 Q 18 -40 17 -30 Q 15 -19 15 -10 L 16 4 L -16 4 "
    "L -15 -10 Q -15 -19 -17 -30 Q -18 -40 -14 -47 Z"), fill="$skin")

both("forearmL", 20, capsule(9, 0, 19, 8), fill="$skin")
both("handL", 22, path("M -6 0 Q -8 8 -3 11 Q 2 13 6 9 Q 8 4 6 0 Q 0 -3 -6 0 Z"), fill="$skin")
both("handL", 22.5, path("M 5 1 Q 10 2 9 6 Q 8 8 6 6 Z"), fill="$skin")
both("upperArmL", 23, capsule(10, 0, 21, 9), fill="$skin")

part("torso", 13, path(
    "M -12 -47 L -8 -47 Q -6 -37 0 -36 Q 6 -37 8 -47 L 12 -47 Q 13 -38 16 -31 "
    "Q 15 -19 15 -7 L -15 -7 Q -15 -19 -16 -31 Q -13 -38 -12 -47 Z"), fill="$tank")
for x in (-10, -5, 0, 5, 10):
    part("torso", 13.2, path(f"M {x} -30 L {x} -9"), stroke="$tankRib", sw=0.7)
part("torso", 13.3, path("M -8 -47 Q -6 -37 0 -36 Q 6 -37 8 -47"), stroke="$tankRib", sw=1.4)

part("root", 14, path(
    "M -16 -9 L 16 -9 L 17 7 Q 17 13 12 14 L 3 14 Q 0 10 -3 14 L -12 14 Q -17 13 -17 7 Z"), fill="$briefs")
part("root", 14.2, path("M -16 -9 L 16 -9 L 16 -5 L -16 -5 Z"), fill="$briefsBand", sw=1.2)
part("root", 14.3, path("M 0 -5 L 0 9"), stroke="$briefsSeam", sw=0.9)

part("head", 30, path(
    "M -40 -50 Q -40 -86 0 -86 Q 40 -86 40 -50 Q 40 -22 22 -8 Q 10 0 0 0 "
    "Q -10 0 -22 -8 Q -40 -22 -40 -50 Z"), fill="$skin")
sym("head", 31, ell(-25, -22, 6, 3.4), fill="$blush", stroke=None, opacity=0.55)

sym("head", 32, ell(-16, -35, 10, 12), fill="#FFFFFF", sw=1.2, eyes="open")
sym("head", 32.1, ell(-16, -34, 8.5, 10.5), fill="$eye", stroke=None, eyes="open")
sym("head", 32.2, ell(-16, -36, 4.6, 6), fill="$eyeDeep", stroke=None, eyes="open")
sym("head", 32.3, ell(-16, -28.5, 5.5, 3.6), fill="$eyeGlow", stroke=None, opacity=0.9, eyes="open")
sym("head", 32.4, ell(-12.5, -39.5, 3.2, 3.8), fill="#FFFFFF", stroke=None, eyes="open")
sym("head", 32.5, ell(-19.5, -30, 1.5, 1.5), fill="#FFFFFF", stroke=None, eyes="open")
sym("head", 32.6, path("M -27 -38 Q -17 -55 -5 -41"), stroke="$line", sw=3.4, eyes="open")
sym("head", 32.7, path("M -27 -38 L -30 -35"), stroke="$line", sw=2.2, eyes="open")
sym("head", 32, path("M -26 -34 Q -16 -43 -6 -34"), stroke="$line", sw=2.8, eyes="closed")

part("head", 33, path("M -5 -17 Q 0 -13 5 -17"), stroke="$line", sw=1.6, mouth="smile")
part("head", 33, path("M -7 -18 L 7 -18 Q 6 -8 0 -8 Q -6 -8 -7 -18 Z"), fill="$mouth", sw=1.4, mouth="open")
part("head", 33.1, path("M -4 -11 Q 0 -14 4 -11 Q 3 -8.5 0 -8.5 Q -3 -8.5 -4 -11 Z"), fill="$tongue", stroke=None, mouth="open")
part("head", 33, ell(0, -14, 3, 3.6), fill="$mouth", sw=1.4, mouth="o")
part("head", 33, path("M -4 -15 L 4 -15"), stroke="$line", sw=1.6, mouth="flat")

BANGS = ("M -44 -50 Q -46 -94 0 -95 Q 46 -94 44 -50 Q 39 -60 34 -46 Q 30 -63 20 -54 "
         "Q 14 -70 4 -51 Q -2 -70 -12 -53 Q -20 -66 -26 -47 Q -32 -62 -40 -43 Z")
SIDE = "M -44 -52 Q -50 -28 -38 -10 Q -34 -26 -36 -46 Z"
AHOGE = "M -2 -94 Q -10 -120 10 -124 Q 20 -116 8 -108 Q 14 -116 6 -116 Q -2 -112 4 -95 Z"
TIP = "M -42 -20 Q -40 -8 -34 -6 Q -34 -14 -36 -22 Z"

def hair_front(opt, bangs=BANGS, side=True, ahoge=True, tips=True):
    part("head", 35, path(bangs), fill="$hair", slot="hair", option=opt)
    part("head", 35.1, path("M -20 -86 Q -8 -92 6 -88 Q -6 -84 -20 -78 Z"), fill="#FFFFFF", stroke=None, opacity=0.55, slot="hair", option=opt)
    if side:
        sym("head", 35.2, path(SIDE), fill="$hair", slot="hair", option=opt)
    if tips:
        sym("head", 35.3, path(TIP), fill="$hairTip", stroke=None, opacity=0.9, slot="hair", option=opt)
    if ahoge:
        part("head", 35.4, path(AHOGE), fill="$hair", slot="hair", option=opt)

part("head", 0, path("M -48 -50 Q -52 -97 0 -98 Q 52 -97 48 -50 Q 50 -18 38 -4 Q 30 0 26 -10 "
                     "L -26 -10 Q -30 0 -38 -4 Q -50 -18 -48 -50 Z"), fill="$hair", slot="hair", option="bob")
sym("head", 0.1, path("M -46 -24 Q -44 -6 -38 -4 Q -36 -14 -40 -26 Z"), fill="$hairTip", stroke=None, slot="hair", option="bob")
hair_front("bob")

part("head", 0, path("M -45 -54 Q -48 -97 0 -98 Q 48 -97 45 -54 Q 46 -38 40 -30 L -40 -30 Q -46 -38 -45 -54 Z"),
     fill="$hair", slot="hair", option="short")
hair_front("short", bangs=("M -45 -46 Q -48 -95 0 -96 Q 48 -95 45 -46 Q 40 -56 36 -44 Q 32 -62 22 -52 "
                            "Q 16 -68 6 -50 Q 0 -68 -10 -52 Q -18 -66 -24 -48 Q -30 -60 -36 -44 Q -40 -56 -45 -46 Z"),
           side=False, tips=False)
sym("head", 35.2, path("M -44 -48 Q -48 -36 -42 -26 Q -40 -36 -38 -44 Z"), fill="$hair", slot="hair", option="short")

part("head", 0, path("M -48 -50 Q -52 -97 0 -98 Q 52 -97 48 -50 Q 56 0 46 34 Q 36 26 30 10 "
                     "L -30 10 Q -36 26 -46 34 Q -56 0 -48 -50 Z"), fill="$hair", slot="hair", option="long")
sym("head", 0.1, path("M -52 8 Q -50 26 -46 34 Q -40 30 -36 20 Q -44 18 -52 8 Z"), fill="$hairTip", stroke=None, slot="hair", option="long")
hair_front("long")

part("head", 0, path("M -45 -54 Q -48 -97 0 -98 Q 48 -97 45 -54 Q 46 -30 38 -18 L -38 -18 Q -46 -30 -45 -54 Z"),
     fill="$hair", slot="hair", option="twin")
sym("head", -0.5, path("M -42 -66 Q -66 -64 -66 -34 Q -66 -6 -54 10 Q -58 -14 -48 -30 Q -44 -48 -42 -66 Z"),
    fill="$hair", slot="hair", option="twin")
sym("head", -0.4, path("M -64 -20 Q -64 -2 -54 10 Q -56 -6 -58 -18 Z"), fill="$hairTip", stroke=None, slot="hair", option="twin")
sym("head", 35.5, ell(-42, -66, 4, 4), fill="$accent", slot="hair", option="twin")
hair_front("twin", tips=False)

COAT = ("M -17 -48 Q 0 -44 17 -48 Q 23 -24 30 12 Q 20 19 8 13 L 3 -26 L -3 -26 "
        "L -8 13 Q -20 19 -30 12 Q -23 -24 -17 -48 Z")
part("torso", 18, path(COAT), fill="$coat", slot="top", option="coat")
sym("torso", 18.1, path("M -3 -26 L -8 13 Q -14 16 -20 15 Q -12 -6 -3 -26 Z"), fill="$coatLining", stroke=None, opacity=0.85, slot="top", option="coat")
part("torso", 18.2, path(COAT), stroke="$trim", sw=2.2, slot="top", option="coat")
part("torso", 18.3, path("M -14 -46 Q 0 -40 14 -46 L 12 -50 Q 0 -45 -12 -50 Z"), fill="$coatDark", slot="top", option="coat")
both("upperArmL", 24, capsule(15, -1, 21, 14), fill="$coat", slot="top", option="coat")
both("upperArmL", 24.1, path("M -7 9 L 7 9 L 7 13 L -7 13 Z"), fill="$coatDark", stroke=None, slot="top", option="coat")
both("forearmL", 21, capsule(14, -1, 15, 15), fill="$coat", slot="top", option="coat")
both("forearmL", 21.1, path("M -8 12 L 8 12 L 8 19 Q 0 21 -8 19 Z"), fill="$coatDark", slot="top", option="coat")
both("forearmL", 21.2, path("M -8 12 L 8 12"), stroke="$trim", sw=1.6, slot="top", option="coat")

TEE = ("M -15 -48 Q 0 -44 15 -48 Q 18 -38 17 -30 Q 15 -18 16 -4 L -16 -4 Q -15 -18 -17 -30 Q -18 -38 -15 -48 Z")
part("torso", 18, path(TEE), fill="$tee", slot="top", option="tee")
part("torso", 18.1, path("M -7 -47 Q 0 -40 7 -47"), stroke="$line", sw=1.2, slot="top", option="tee")
part("torso", 18.2, path("M -6 -28 Q -6 -33 -2 -33 Q 0 -33 0 -30 Q 0 -33 2 -33 Q 6 -33 6 -28 Q 4 -24 0 -21 Q -4 -24 -6 -28 Z"),
     fill="$accent", stroke=None, slot="top", option="tee")
both("upperArmL", 24, capsule(14, -1, 10, 13), fill="$tee", slot="top", option="tee")

part("torso", 18, path("M -16 -48 Q 0 -44 16 -48 Q 20 -36 19 -24 Q 18 -10 18 2 L -18 2 Q -18 -10 -19 -24 Q -20 -36 -16 -48 Z"),
     fill="$hoodie", slot="top", option="hoodie")
part("torso", 18.1, path("M -10 -14 L 10 -14 L 12 -2 L -12 -2 Z"), fill="$hoodieDark", sw=1.2, slot="top", option="hoodie")
part("torso", 18.2, path("M -4 -46 L -4 -32 M 4 -46 L 4 -32"), stroke="#FFFFFF", sw=1.4, slot="top", option="hoodie")
both("upperArmL", 24, capsule(14, -1, 21, 13), fill="$hoodie", slot="top", option="hoodie")
both("forearmL", 21, capsule(13, -1, 16, 12), fill="$hoodie", slot="top", option="hoodie")
both("forearmL", 21.1, path("M -6 15 L 6 15 L 6 19 Q 0 20 -6 19 Z"), fill="$hoodieDark", slot="top", option="hoodie")

part("root", 16, path("M -17 -10 L 17 -10 Q 26 8 28 22 Q 21 26 14 22 Q 7 27 0 23 Q -7 27 -14 22 Q -21 26 -28 22 Q -26 8 -17 -10 Z"),
     fill="$skirt", slot="bottom", option="skirt")
part("root", 16.1, path("M -28 22 Q -21 26 -14 22 Q -7 27 0 23 Q 7 27 14 22 Q 21 26 28 22"), stroke="$trim", sw=1.6, slot="bottom", option="skirt")
part("root", 16, path("M -17 -10 L 17 -10 L 19 8 L 2 8 L 0 2 L -2 8 L -19 8 Z"), fill="$shorts", slot="bottom", option="shorts")
part("root", 16.1, path("M -17 -10 L 17 -10 L 17 -6 L -17 -6 Z"), fill="$coatDark", stroke=None, slot="bottom", option="shorts")
both("thighL", 9, capsule(16, -2, 17, 16), fill="$shorts", slot="bottom", option="shorts")
both("thighL", 9.1, path("M -8.5 14 L 8.5 14 L 8.5 19 L -8.5 19 Z"), fill="$shortsCuff", slot="bottom", option="shorts")
part("root", 16, path("M -17 -10 L 17 -10 L 18 6 L -18 6 Z"), fill="$pants", slot="bottom", option="pants")
both("thighL", 9, capsule(15, -2, 26, 14), fill="$pants", slot="bottom", option="pants")
both("shinL", 7, capsule(13, -2, 20, 13), fill="$pants", slot="bottom", option="pants")

SNEAKER = "M -8 -6 L 8 -6 L 9 4 Q 12 6 11 10 L -9 10 Q -12 8 -10 4 Z"
both("footL", 7, path(SNEAKER), fill="$shoe", slot="shoes", option="sneakers")
both("footL", 7.1, path("M -10 7 L 11 7 L 11 11 Q 0 13 -10 11 Z"), fill="$sole", slot="shoes", option="sneakers")
both("footL", 7.2, path("M -8 -2 L 8 -2 L 8 1 L -8 1 Z"), fill="$trim", stroke=None, slot="shoes", option="sneakers")
both("footL", 7.3, ell(0, 4, 2.6, 2.2), fill="$sole", stroke=None, slot="shoes", option="sneakers")
both("shinL", 7, path("M -7 10 L 7 10 L 8 25 L -8 25 Z"), fill="$sock", slot="shoes", option="sneakers")
both("shinL", 7, path("M -8 6 L 8 6 L 9 25 L -9 25 Z"), fill="$boot", slot="shoes", option="boots")
both("footL", 7, path(SNEAKER), fill="$boot", slot="shoes", option="boots")
both("shinL", 7.1, path("M -8 9 L 8 9 L 8 12 L -8 12 Z"), fill="$trim", stroke=None, slot="shoes", option="boots")
both("footL", 7.1, path("M -10 7 L 11 7 L 11 11 Q 0 13 -10 11 Z"), fill="$coatDark", slot="shoes", option="boots")

part("head", -1, path("M -54 -46 Q -58 -110 0 -112 Q 58 -110 54 -46 Q 52 -8 40 4 L -40 4 Q -52 -8 -54 -46 Z"),
     fill="$coatLining", slot="acc", option="hood")
part("head", 38, path("M -54 -50 Q -58 -112 0 -114 Q 58 -112 54 -50 Q 48 -56 44 -66 Q 30 -94 0 -94 "
                      "Q -30 -94 -44 -66 Q -48 -56 -54 -50 Z"), fill="$coat", slot="acc", option="hood")
part("head", 38.1, path("M -54 -50 Q -48 -56 -44 -66 Q -30 -94 0 -94 Q 30 -94 44 -66 Q 48 -56 54 -50"),
     stroke="$trim", sw=2.4, slot="acc", option="hood")
sym("head", 38.2, ell(-42, -84, 10, 10), fill="$coatDark", slot="acc", option="hood")
sym("head", 38.3, ell(-42, -84, 6.5, 6.5), fill="$lamp", stroke="$trim", sw=1.4, slot="acc", option="hood")
sym("head", 38.4, ell(-44, -86, 2.2, 2.2), fill="#FFFFFF", stroke=None, slot="acc", option="hood")
part("head", 38, path("M -44 -82 L 44 -82 Q 46 -76 40 -75 L -40 -75 Q -46 -76 -44 -82 Z"), fill="$hat", slot="acc", option="fedora")
part("head", 38.1, path("M -26 -82 L -24 -108 Q 0 -114 24 -108 L 26 -82 Z"), fill="$hat", slot="acc", option="fedora")
part("head", 38.2, path("M -25 -90 L 25 -90 L 26 -82 L -26 -82 Z"), fill="$accent", stroke=None, slot="acc", option="fedora")
part("head", 38, path("M -22 -88 L -24 -110 L -12 -98 L 0 -116 L 12 -98 L 24 -110 L 22 -88 Z"), fill="$gold", slot="acc", option="crown")
part("head", 38.1, ell(0, -96, 3, 3), fill="$accent", stroke=None, slot="acc", option="crown")
part("head", 38, path("M -42 -76 Q -44 -116 0 -116 Q 44 -116 42 -76 Z"), fill="$cap", slot="acc", option="cap")
part("head", 38.1, path("M 20 -80 L 62 -80 Q 64 -72 56 -72 L 20 -74 Z"), fill="$cap", slot="acc", option="cap")
part("head", 38.2, ell(0, -116, 4, 2.5), fill="$cap", slot="acc", option="cap")
part("head", 38, path("M 30 -88 L 14 -100 L 14 -76 Z"), fill="$ribbon", slot="acc", option="ribbon")
part("head", 38, path("M 30 -88 L 46 -100 L 46 -76 Z"), fill="$ribbon", slot="acc", option="ribbon")
part("head", 38.1, ell(30, -88, 4, 4), fill="$ribbonDark", slot="acc", option="ribbon")
sym("head", 36, path("M -28 -42 L -4 -42 L -5 -30 Q -16 -24 -27 -30 Z"), fill="#1B1E26", sw=1.2, opacity=0.92, slot="acc", option="glasses")
part("head", 36.1, path("M -4 -40 L 4 -40"), stroke="#1B1E26", sw=2, slot="acc", option="glasses")

part("torso", 19, path("M -9 -47 Q 0 -36 9 -47"), stroke="$gold", sw=1.4, slot="neck", option="heart")
part("torso", 19.1, path("M -6 -36 Q -6 -42 -2 -42 Q 0 -42 0 -39 Q 0 -42 2 -42 Q 6 -42 6 -36 Q 4 -31 0 -28 Q -4 -31 -6 -36 Z"),
     fill="$gem", stroke="$gold", sw=1.4, slot="neck", option="heart")
part("torso", 19.2, ell(-2.5, -38, 1.4, 1.4), fill="#FFFFFF", stroke=None, slot="neck", option="heart")

SLOTS = {
    "hair":   [["bob", "보브"], ["short", "숏컷"], ["long", "롱"], ["twin", "트윈테일"]],
    "top":    [["coat", "후드 코트"], ["tee", "반팔 티"], ["hoodie", "후드티"]],
    "bottom": [["skirt", "스커트"], ["shorts", "반바지"], ["pants", "긴바지"]],
    "shoes":  [["none", "맨발"], ["sneakers", "스니커즈"], ["boots", "부츠"]],
    "acc":    [["none", "없음"], ["hood", "라이트 후드"], ["fedora", "중절모"], ["crown", "왕관"],
               ["cap", "캡모자"], ["ribbon", "리본"], ["glasses", "선글라스"]],
    "neck":   [["none", "없음"], ["heart", "하트 코어"]],
}

HAIR_COLORS = [
    ["silver", "실버민트", "#EEF2F3", "#9FE6DC"],
    ["pink", "핑크", "#FFC6D9", "#FF8FB6"],
    ["brown", "브라운", "#9A6A4B", "#C99470"],
    ["black", "블랙", "#2C2F3A", "#4D6BA8"],
    ["blue", "블루", "#8EC5FF", "#4C8BF5"],
    ["lavender", "라벤더", "#E2D4FF", "#9B7BE8"],
    ["maple", "단풍 오렌지", "#FFC48A", "#E8572A"],
    ["snow", "눈꽃 화이트", "#FFFFFF", "#B9D7F5"],
    ["gold", "골드", "#F7DE8C", "#D7A83A"],
]

PALETTE = {
    "line": "#3A2F3D", "skin": "#FFE6D6", "skinShade": "#F3C6B1", "blush": "#FF9BA8",
    "eye": "#27A6B8", "eyeDeep": "#123D57", "eyeGlow": "#F7D36A",
    "mouth": "#B8475A", "tongue": "#FF8C98",
    "hair": "#EEF2F3", "hairTip": "#9FE6DC", "accent": "#38C6E0",
    "tank": "#FBFBF7", "tankRib": "#D9DBD6", "briefs": "#BFE3F7", "briefsBand": "#FFFFFF", "briefsSeam": "#8DBAD3",
    "coat": "#F6F0E2", "coatDark": "#1F2338", "coatLining": "#53C9D6", "trim": "#D7A83A",
    "tee": "#8ED9D2", "hoodie": "#3C4A78", "hoodieDark": "#2A3456",
    "skirt": "#1F2338", "shorts": "#24262E", "shortsCuff": "#3A3D49", "pants": "#3F4E73",
    "shoe": "#F6F0E2", "sole": "#5FE3EA", "sock": "#1F2338", "boot": "#2A2C36",
    "hat": "#20242C", "gold": "#F2C14E", "cap": "#1F86C7", "ribbon": "#FF6C9D", "ribbonDark": "#D93C6B",
    "lamp": "#FFD45C", "gem": "#4CC9F0",
}

BASE_LOOK = {"hair": "bob", "hairColor": "silver", "top": "coat", "bottom": "pants",
             "shoes": "none", "acc": "none", "neck": "none"}

PRESETS = [
    ["base", "기본", BASE_LOOK],
    ["lumi_cute", "루미 큐트", {**BASE_LOOK, "top": "coat", "bottom": "skirt", "shoes": "sneakers", "acc": "hood", "neck": "heart"}],
    ["luon_sporty", "루온 스포티", {**BASE_LOOK, "hair": "short", "top": "coat", "bottom": "shorts", "shoes": "sneakers", "acc": "hood", "neck": "heart"}],
    ["lumi_chic", "루미 시크", {**BASE_LOOK, "hair": "long", "top": "coat", "bottom": "pants", "shoes": "boots", "acc": "hood", "neck": "heart"}],
]

POSES = {
    "idle": {},
    "welcome": {"upperArmL": 62, "forearmL": 22, "upperArmR": 62, "forearmR": 22, "head": -4},
    "wave": {"upperArmR": 150, "forearmR": -40, "head": 5},
    "cheer": {"upperArmL": 155, "forearmL": -20, "upperArmR": 155, "forearmR": -20},
    "point": {"upperArmR": 95, "forearmR": 0, "upperArmL": 15, "forearmL": 10, "head": 6},
    "think": {"upperArmR": 20, "forearmR": 150, "head": -8, "upperArmL": 22, "forearmL": 30},
}

ANIMS = {
    "breathe": {"head": [2, 4, 0], "upperArmL": [2, 4, 0.25], "upperArmR": [2, 4, 0.25], "_bobY": [1.0, 4, 0]},
    "wave": {"forearmR": [22, 0.9, 0], "head": [3, 1.8, 0]},
    "talk": {"head": [3, 1.2, 0], "forearmL": [8, 1.2, 0.5]},
}

rig = {
    "version": 1,
    "canvas": [200, 300],
    "bones": [{"name": n, "parent": p, "x": x, "y": y, "rot": r, **({"mirror": True} if m else {})}
              for n, p, x, y, r, m in BONES],
    "parts": sorted(parts, key=lambda p: p["z"]),
    "slots": SLOTS,
    "hairColors": HAIR_COLORS,
    "palette": PALETTE,
    "presets": [{"id": i, "label": l, "look": look} for i, l, look in PRESETS],
    "poses": POSES,
    "anims": ANIMS,
}

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(rig, f, ensure_ascii=False, separators=(",", ":"))
print(f"wrote {OUT}: {len(rig['bones'])} bones, {len(parts)} parts")

HERE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(HERE, "preview_body.html"), encoding="utf-8") as f:
    body = f.read().replace("/*RIG_JSON*/null", json.dumps(rig, ensure_ascii=False, separators=(",", ":")))
PREVIEW = os.path.join(ROOT, "docs", "character", "rig_preview.html")
with open(PREVIEW, "w", encoding="utf-8") as f:
    f.write('<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n'
            '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
            + body.replace("<div class=\"wrap\">", "</head>\n<body>\n<div class=\"wrap\">", 1)
            + "\n</body>\n</html>\n")
print(f"wrote {PREVIEW}")
if os.environ.get("RIG_ARTIFACT_OUT"):
    with open(os.environ["RIG_ARTIFACT_OUT"], "w", encoding="utf-8") as f:
        f.write(body)
