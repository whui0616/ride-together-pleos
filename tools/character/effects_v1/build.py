from pathlib import Path
import json, math, shutil, hashlib
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parent
APP=ROOT.parents[2]/'app/src/main/assets/character/pixel'
W,H,N,MS=480,720,24,60
specs=json.loads((ROOT/'specs.json').read_text(encoding='utf8'))
for s in specs:
    path=ROOT/'sources'/f"{s['id']}.png";path.parent.mkdir(exist_ok=True)
    if not path.exists() and s.get('source'): shutil.copy2(s['source'],path)
font=ImageFont.truetype('C:/Windows/Fonts/malgun.ttf',19)
character=Image.new('RGBA',(400,600))
for name in ['m/outfits/outfit_21_skin_1.webp','m/faces/face_0_skin_1.webp',
             'm/eyes/eyes_0_color_0.webp','common/brows/brows_0_color_1.webp',
             'common/mouth/mouth_0.webp','m/hair/hair_1_color_1_front.webp']:
    character.alpha_composite(Image.open(APP/name).convert('RGBA'))
char=Image.new('RGBA',(W,H));char.alpha_composite(character,(40,55))
previews_dir=ROOT/'previews';previews_dir.mkdir(exist_ok=True)
gallery=[Image.new('RGB',(1200,780),'#101a30') for _ in range(N)]
catalog=[]; validations=[]
yy,xx=np.indices((H,W))

def animate(data,kind,t):
    phase=2*math.pi*t
    amplitude={'water':4,'fire':3,'electric':1,'rainbow':3,'ice':1,'wind':7,
               'nature':4,'shadow':5,'light':1,'cosmic':2}[kind]
    freq=2 if kind in ['fire','electric'] else 1
    sway=np.rint(amplitude*np.sin(phase*freq+yy/55)*(yy<620)).astype(int)
    sx=np.clip(xx+sway,0,W-1)
    rise=np.rint((2 if kind in ['water','wind','nature','cosmic'] else 1)*np.sin(phase+xx/70)*(yy<620)).astype(int)
    sy=np.clip(yy+rise,0,H-1)
    a=data[sy,sx].copy()
    if kind=='electric': energy=.62+.30*(.5+.5*np.cos(2*phase+yy/80))
    elif kind=='fire': energy=.74+.15*np.sin(2*phase+yy/44)+.07*np.sin(phase+xx/60)
    elif kind in ['ice','light']: energy=.80+.12*np.sin(phase+yy/90)
    elif kind=='cosmic': energy=.72+.18*np.sin(phase+yy/42+xx/60)
    elif kind=='shadow': energy=.70+.16*np.sin(phase-yy/68)
    else: energy=.76+.15*np.sin(phase+yy/70)+.05*np.sin(phase)
    fade=np.minimum.reduce([np.clip((H-1-yy)/20,0,1),np.clip((yy-12)/18,0,1),
                            np.clip(xx/16,0,1),np.clip((W-1-xx)/16,0,1)])
    a[...,3]=np.rint(a[...,3]*energy*fade).astype('uint8')
    a[a[...,3]<3]=0
    back=a.copy();front=a[640:688].copy();back[640:688]=0
    return Image.fromarray(back).resize((240,360),Image.Resampling.NEAREST),Image.fromarray(front).resize((240,24),Image.Resampling.NEAREST)

def save_gif(frames,path):
    sample=Image.new('RGB',(frames[0].width*3,frames[0].height))
    for j,i in enumerate([0,8,16]): sample.paste(frames[i],(j*frames[0].width,0))
    palette=sample.quantize(colors=256,method=Image.Quantize.MEDIANCUT)
    paletted=[f.quantize(palette=palette,dither=Image.Dither.NONE) for f in frames]
    paletted[0].save(path,save_all=True,append_images=paletted[1:],duration=MS,loop=0,disposal=2,optimize=False)
    with Image.open(path) as gif:
        assert gif.n_frames==N and gif.info['loop']==0
        assert sum((gif.seek(i) or gif.info['duration']) for i in range(N))==N*MS

for index,s in enumerate(specs):
    kind=s['id'];directory=ROOT/'runtime'/kind;directory.mkdir(parents=True,exist_ok=True)
    raw=Image.open(ROOT/'sources'/f'{kind}.png').convert('RGBA')
    base=Image.new('RGBA',(W,H));base.alpha_composite(raw.resize((420,660),Image.Resampling.NEAREST),(30,50))
    data=np.array(base)
    assert data[H//2,W//2,3]<20,(kind,'opaque center')
    backs=Image.new('RGBA',(1440,1440));fronts=Image.new('RGBA',(1440,96))
    frames=[]; hashes=[]
    for i in range(N):
        back,front=animate(data,kind,i/N)
        assert np.array(back)[0,:,3].max()==0
        for part,image in [('back',back),('front',front)]:
            p=ROOT/'frames'/kind/part;p.mkdir(parents=True,exist_ok=True)
            image.save(p/f'{i:02d}.png')
        backs.paste(back,((i%6)*240,(i//6)*360));fronts.paste(front,((i%6)*240,(i//6)*24))
        reconstructed=Image.new('RGBA',(W,H))
        reconstructed.alpha_composite(back.resize((W,H),Image.Resampling.NEAREST))
        foreground=front.resize((480,48),Image.Resampling.NEAREST)
        assert np.array(foreground)[...,3].max()>0,(kind,'empty foreground')
        preview=Image.new('RGBA',(W,H),'#101a30');preview.alpha_composite(reconstructed)
        preview.alpha_composite(char);preview.alpha_composite(foreground,(0,640))
        frames.append(preview.convert('RGB'))
        hashes.append(hashlib.sha256(back.tobytes()+front.tobytes()).hexdigest())
        xy=((index%5)*240,(index//5)*390)
        gallery[i].paste(preview.resize((240,360),Image.Resampling.NEAREST),xy)
        ImageDraw.Draw(gallery[i]).text((xy[0]+12,xy[1]+360),f'{index+1:02d} {s["name"]}',font=font,fill='#eaf5ff')
    backs.save(directory/'back.png');fronts.save(directory/'front.png')
    assert len(set(hashes))==N,(kind,'duplicate frames')
    assert all(a.tobytes()==b.tobytes() for a,b in zip(animate(data,kind,0),animate(data,kind,1)))
    for part,atlas,fh in [('back',backs,360),('front',fronts,24)]:
        for i in range(N):
            crop=atlas.crop(((i%6)*240,(i//6)*fh,(i%6+1)*240,(i//6+1)*fh))
            assert crop.tobytes()==Image.open(ROOT/'frames'/kind/part/f'{i:02d}.png').tobytes()
    save_gif(frames,previews_dir/f'{kind}.gif');frames[6].save(previews_dir/f'{kind}.png')
    meta={'id':kind,'name':s['name'],'frameCount':N,'frameDurationMs':MS,'loopDurationMs':N*MS,
          'loop':True,'logicalCanvas':[W,H],'characterCanvas':[400,600],'characterOffset':[40,55],
          'atlasColumns':6,'atlasRows':4,'blendMode':'sourceOver','filter':'nearest',
          'layers':[{'id':'back','file':'back.png','frameSize':[240,360],'drawRect':[0,0,480,720]},
                    {'id':'front','file':'front.png','frameSize':[240,24],'drawRect':[0,640,480,48]}],
          'layerOrder':['back','character','front']}
    (directory/'manifest.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf8')
    catalog.append({'id':kind,'name':s['name'],'manifest':f'{kind}/manifest.json'})
    validations.append({'id':kind,'frames':N,'uniqueFrames':N,'gifDurationMs':N*MS,
                        'periodicEndpointExact':True,'atlasMatchesFrames':True,'transparentCenter':True,
                        'decodedAtlasBytes':1440*1440*4+1440*96*4})
    print(f'{kind}: 24-frame GIF + 2 RGBA atlases validated',flush=True)

(ROOT/'runtime/catalog.json').write_text(json.dumps({'version':1,'effects':catalog},ensure_ascii=False,indent=2),encoding='utf8')
(ROOT/'validation.json').write_text(json.dumps(validations,indent=2),encoding='utf8')
save_gif(gallery,previews_dir/'all_effects.gif');gallery[6].save(previews_dir/'all_effects.png')
print('All ten effects complete; gallery generated',flush=True)
