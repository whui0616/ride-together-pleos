from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

ROOT = Path(__file__).resolve().parent
APP = ROOT.parents[3] / 'app/src/main/assets/character/pixel'
PALETTE = ['FFE8D8', 'F4CDAE', 'E3B28E', 'C18B63', '955F42',
           '633D2E', 'FFF3EA', '4C2E22', '3A231B']
SPECS = {
    ('f', 20): (324, 38, 250, 477, [(284,39,365,101), (195,225,245,274), (401,225,450,274)]),
    ('f', 21): (768, 39, 250, 477, [(718,39,817,103), (659,168,711,204), (826,168,878,204),
                                            (640,231,698,275), (834,231,892,275), (715,380,757,405), (777,380,819,405)]),
    ('f', 22): (1214, 39, 250, 477, [(1189,39,1242,85), (1085,237,1143,275), (1286,237,1347,275)]),
    ('m', 20): (323, 533, 753, 986, [(299,533,346,580), (194,738,245,784), (395,738,447,784)]),
    ('m', 21): (769, 533, 753, 986, [(743,533,794,582), (641,735,697,785), (840,735,895,785)]),
    ('m', 22): (1214, 533, 753, 986, [(1188,533,1240,581), (1091,730,1147,786), (1293,730,1349,786)]),
}

source = np.array(Image.open(ROOT / 'approved.png').convert('RGBA'))
yy, xx = np.indices(source.shape[:2])
rgb = source[..., :3].astype(float)
r,g,b = rgb.transpose(2,0,1)
ratio = (g-b) / np.maximum(r-b,1)
skin_color = (r > 90) & (r-b > 14) & (r-b < 155) & (ratio > .12) & (ratio < .53)
font = ImageFont.truetype('C:/Windows/Fonts/malgun.ttf', 16)
report = []
overview = Image.new('RGB', (6*300, 9*450), '#d7e9ed')
mask_sheet = Image.new('RGB', (6*300, 450), '#dddddd')
for col, ((gender, number), (cx, top, hand, sole, zones)) in enumerate(SPECS.items()):
    cell = (abs(xx-cx) < 210) & (yy >= (0 if gender=='f' else 512)) & (yy < (512 if gender=='f' else 1024))
    silhouette = cell & (source[...,3] >= 128)
    labels, count = ndimage.label(silhouette)
    sizes = np.bincount(labels.ravel()); sizes[0]=0
    silhouette = labels == sizes.argmax()
    regions = np.zeros(silhouette.shape, bool)
    for x0,y0,x1,y1 in zones:
        regions[y0:y1,x0:x1] = True
    skin = regions & silhouette & skin_color
    labs,n = ndimage.label(skin)
    sizes=np.bincount(labs.ravel()); sizes[0]=0
    skin = np.isin(labs, np.flatnonzero(sizes>=5))
    assert skin.sum()>1500, (gender,number,'skin mask unexpectedly empty')

    oy,ox=np.indices((600,400))
    sx=np.rint(cx+(ox-200)/.72).astype(int)
    target_hand=429 if gender=='f' else 436
    sy=np.rint(np.interp(oy, [272,target_hand,583], [top,hand,sole])).astype(int)
    valid=(oy>=272)&(oy<=583)&(sx>=0)&(sx<1536)
    sx=np.clip(sx,0,1535); sy=np.clip(sy,0,1023)
    body=source[sy,sx].copy()
    body[...,3]=np.where(valid & silhouette[sy,sx],255,0)
    body[body[...,3]==0]=0
    mask=valid & skin[sy,sx] & (body[...,3]>0)
    Image.fromarray(mask.astype('uint8')*255).save(ROOT/f'{gender}_{number}_skin_mask.png')
    Image.fromarray(body).save(ROOT/f'{gender}_{number}_aligned.png')
    marked=body.copy();marked[mask,:3]=[0,220,80]
    mb=Image.new('RGBA',(400,600),'#dddddd');mb.alpha_composite(Image.fromarray(marked))
    mask_sheet.paste(mb.resize((300,450),Image.Resampling.NEAREST),(col*300,0))
    luminance=body[...,:3].astype(float) @ np.array([.2126,.7152,.0722])
    base=np.percentile(luminance[mask],75)
    shade=np.clip(luminance/base,0,1)
    highlight=np.clip((luminance/base-1)*.4,0,.055)
    (ROOT/'staged'/gender/'outfits').mkdir(parents=True,exist_ok=True)
    (ROOT/'masters'/gender).mkdir(parents=True,exist_ok=True)
    reference=None
    for tone,hx in enumerate(PALETTE):
        a=body.copy(); color=np.array([int(hx[i:i+2],16) for i in (0,2,4)])
        shaded=color[None,None,:]*shade[...,None]
        shaded=shaded*(1-highlight[...,None])+np.array([255,244,233])*highlight[...,None]
        a[mask,:3]=np.rint(shaded[mask]).astype('uint8')
        image=Image.fromarray(a)
        dest=ROOT/'staged'/gender/'outfits'/f'outfit_{number:02d}_skin_{tone}.webp'
        image.save(dest,'WEBP',lossless=True,exact=True,method=6)
        image.resize((800,1200),Image.Resampling.NEAREST).save(ROOT/'masters'/gender/f'outfit_{number:02d}_skin_{tone}.png')
        decoded=np.array(Image.open(dest).convert('RGBA'))
        assert np.array_equal(decoded,a)
        assert set(np.unique(a[...,3])) == {0,255}
        if reference is not None:
            assert np.array_equal(a[~mask],reference[~mask]), 'Garment changed with skin tone'
        reference=a
        face=np.array(Image.open(APP/f'{gender}/faces/face_0_skin_{tone}.webp').convert('RGBA'))
        assert np.any((face[272:294,190:210,3]>128)&(a[272:294,190:210,3]>128)), 'No neck/face overlap'
        composite=Image.new('RGBA',(400,600),'#d7e9ed')
        hair=7 if gender=='f' else 1
        for layer in [image, Image.open(APP/f'{gender}/faces/face_0_skin_{tone}.webp'),
                      Image.open(APP/f'{gender}/eyes/eyes_0_color_0.webp'),
                      Image.open(APP/'common/brows/brows_0_color_1.webp'),
                      Image.open(APP/'common/mouth/mouth_0.webp'),
                      Image.open(APP/f'{gender}/hair/hair_{hair}_color_1_front.webp')]:
            composite.alpha_composite(layer.convert('RGBA'))
        overview.paste(composite.resize((300,450),Image.Resampling.NEAREST),(col*300,tone*450))
        report.append({'gender':gender,'outfit':number,'skin':tone,'size':[400,600],
                       'skinPixels':int(mask.sum()),'binaryAlpha':True,'garmentInvariant':True,'neckOverlap':True})
mask_sheet.save(ROOT/'skin_masks_review.png')
overview.save(ROOT/'all_skins_review.png')
(ROOT/'validation.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print(f'Exported {len(report)} verified outfits')
