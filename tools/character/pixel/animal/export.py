from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage

ROOT = Path(__file__).resolve().parent
APP = ROOT.parents[3] / 'app/src/main/assets/character/pixel'
PALETTE = ['FFE8D8', 'F4CDAE', 'E3B28E', 'C18B63', '955F42',
           '633D2E', 'FFF3EA', '4C2E22', '3A231B']
SPECS = {}
for gender, top, hand, sole in [('f',275,472,660),('m',964,1167,1373)]:
    for number,cx in zip(range(23,26),[194,544,894]):
        SPECS[(gender,number)] = (cx,top,hand,sole,[
            (cx-31,top+2,cx+31,top+50),
            (cx-115,hand-21,cx-60,hand+24),
            (cx+60,hand-21,cx+115,hand+24)])

ears_source=np.array(Image.open(ROOT/'ears.png').convert('RGBA'))
anchors={}
for row,gender in enumerate('fm'):
    for column,number in enumerate(range(23,26)):
        region=ears_source[row*512:(row+1)*512,column*512:(column+1)*512].copy()
        labels,n=ndimage.label(region[...,3]>=128)
        sizes=np.bincount(labels.ravel()); sizes[0]=0
        labs=np.argsort(sizes)[-2:]
        pieces=[]
        for lab in labs:
            ys,xs=np.where(labels==lab)
            x0,y0,x1,y1=int(xs.min()),int(ys.min()),int(xs.max()+1),int(ys.max()+1)
            piece=region[y0:y1,x0:x1].copy()
            piece[...,3]=np.where(labels[y0:y1,x0:x1]==lab,255,0)
            piece[piece[...,3]==0]=0
            pieces.append((x0,Image.fromarray(piece)))
        pieces.sort(key=lambda it:it[0])
        width=78 if number==23 else 64
        scaled=[im.resize((width,round(im.height*width/im.width)),Image.Resampling.NEAREST) for _,im in pieces]
        for hair in range(10):
            ha=np.array(Image.open(APP/f'{gender}/hair/hair_{hair}_color_1_front.webp'))[...,3]>128
            tops=[]
            for x in [130,270]:
                ys=np.where(ha[:180,x-5:x+6].sum(axis=1)>=6)[0]
                assert len(ys), (gender,hair,x)
                tops.append(int(ys[0]))
            top=max(20,max(tops)-35)
            overlay=Image.new('RGBA',(400,600))
            left=94 if number==23 else 100
            for image,x in zip(scaled,[left,400-left-width]):
                overlay.alpha_composite(image,(x,top))
            ar=np.array(overlay)
            for x in [left,400-left-width]:
                assert ((ar[top:top+110,x:x+width,3]>0)&ha[top:top+110,x:x+width]).sum()>70
            assert not ar[170:,:,3].any()
            assert not ar[:14,:,3].any()
            dest=ROOT/f'staged/{gender}/outfit_overlays/outfit_{number}_hair_{hair}.webp'
            dest.parent.mkdir(parents=True,exist_ok=True)
            overlay.save(dest,'WEBP',lossless=True,exact=True,method=4)
            anchors[f'{gender}:{number}:{hair}']={'top':top,'width':width,'hairOverlap':True}
(ROOT/'ear_alignment.json').write_text(json.dumps(anchors,indent=2),encoding='utf8')

source = np.array(Image.open(ROOT / 'bodies.png').convert('RGBA'))
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
    cell = (abs(xx-cx) < 155) & (yy >= (0 if gender=='f' else 724)) & (yy < (724 if gender=='f' else 1448))
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
    skin = np.isin(labs, np.argsort(sizes)[-3:])
    assert np.all(np.sort(sizes)[-3:] > 100), (gender,number,"Neck and two hands required")
    assert skin.sum()>1000, (gender,number,'skin mask unexpectedly empty')

    oy,ox=np.indices((600,400))
    sx=np.rint(cx+(ox-200)/.84).astype(int)
    target_hand=429 if gender=='f' else 436
    sy=np.rint(np.interp(oy, [272,target_hand,583], [top,hand,sole])).astype(int)
    valid=(oy>=272)&(oy<=583)&(sx>=0)&(sx<1086)
    sx=np.clip(sx,0,1085); sy=np.clip(sy,0,1447)
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
                      Image.open(APP/f'{gender}/hair/hair_{hair}_color_1_front.webp'),
                      Image.open(ROOT/f'staged/{gender}/outfit_overlays/outfit_{number}_hair_{hair}.webp')]:
            composite.alpha_composite(layer.convert('RGBA'))
        overview.paste(composite.resize((300,450),Image.Resampling.NEAREST),(col*300,tone*450))
        report.append({'gender':gender,'outfit':number,'skin':tone,'size':[400,600],
                       'skinPixels':int(mask.sum()),'binaryAlpha':True,'garmentInvariant':True,'neckOverlap':True})
mask_sheet.save(ROOT/'skin_masks_review.png')
overview.save(ROOT/'all_skins_review.png')
(ROOT/'validation.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print(f'Exported {len(report)} verified outfits')

overview.crop((0,450,1800,900)).save(ROOT/'preview.png')
hairstyles=Image.new('RGB',(6*240,10*360),'#d7e9ed')
for col,(gender,number) in enumerate(SPECS):
    for hair in range(10):
        comp=Image.new('RGBA',(400,600),'#d7e9ed')
        back=APP/f'{gender}/hair/hair_{hair}_color_1_back.webp'
        if back.exists(): comp.alpha_composite(Image.open(back).convert('RGBA'))
        paths=[ROOT/f'staged/{gender}/outfits/outfit_{number}_skin_1.webp',
               APP/f'{gender}/faces/face_0_skin_1.webp',APP/f'{gender}/eyes/eyes_0_color_0.webp',
               APP/'common/brows/brows_0_color_1.webp',APP/'common/mouth/mouth_0.webp',
               APP/f'{gender}/hair/hair_{hair}_color_1_front.webp',
               ROOT/f'staged/{gender}/outfit_overlays/outfit_{number}_hair_{hair}.webp']
        for path in paths: comp.alpha_composite(Image.open(path).convert('RGBA'))
        hairstyles.paste(comp.resize((240,360),Image.Resampling.NEAREST),(col*240,hair*360))
hairstyles.save(ROOT/'all_hair_review.png')
