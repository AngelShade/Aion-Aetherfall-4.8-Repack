"""Prepare a broad texture/environment pass on the existing modified client.

Only format conversion, resolution normalization, mipmaps and inspection previews
are done here. Replacement artwork is authored separately with image_gen.
"""
import argparse, copy, hashlib, io, json, math, struct, zipfile, sys
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT.parent/'expanded-warehouse'))
from codec import read_pak,encode_pak,binary_xml,encode_binary_xml
from build_package import make_dds

ART={0:'shared-grass',1:'shared-leaflitter',2:'shared-sand',3:'shared-cliff',
     4:'shared-basalt',5:'shared-ruins',7:'shared-mossgravel',8:'shared-earth',
     10:'shared-sandstone',11:'shared-blueforest',22:'shared-lava'}
def digest(b): return hashlib.sha256(b).hexdigest()

def environment(data):
    binary=data[0]==128
    tree=binary_xml(data) if binary else ET.fromstring(data)
    original=copy.deepcopy(tree);changes=[]
    adjustments={'EnvState':{'AmbientAmplify':(0.94,None),'SunAmplify':(1.045,None)},
                 'MRTSky':{'MieScattering':(0.96,None)},
                 'MRTOcean':{'ReflectAmount':(1.08,1),'RefractAmount':(1.04,1),'SunColorMultiplier':(0.9,None)}}
    for index,env in enumerate(tree.iter('Environment')):
        for tag,attrs in adjustments.items():
            node=env.find(tag)
            if node is None: continue
            for key,(factor,maximum) in attrs.items():
                if key not in node.attrib:continue
                before=node.get(key)
                try: value=float(before)
                except ValueError:continue
                if not math.isfinite(value) or value<=0:continue
                value=value*factor
                if maximum is not None:value=min(value,maximum)
                after=f'{value:.6g}'
                if after==before:continue
                node.set(key,after);changes.append({'environment':index,'tag':tag,'key':key,'before':before,'after':after})
    if not changes:return data,[]
    # Prove every other element, text, child and attribute stays unchanged.
    checked=copy.deepcopy(tree)
    envs=list(checked.iter('Environment'))
    for change in changes:envs[change['environment']].find(change['tag']).set(change['key'],change['before'])
    assert ET.tostring(checked)==ET.tostring(original)
    encoded=encode_binary_xml(tree) if binary else ET.tostring(tree,encoding='utf-16')
    actual=binary_xml(encoded) if binary else ET.fromstring(encoded)
    assert ET.tostring(actual)==ET.tostring(tree)
    return encoded,changes

def particle_dds(image,original,size=(512,512)):
    """Preserve BC3 alpha and encode a complete mip chain for the smoke sprite."""
    assert original[84:88]==b'DXT5'
    image=image.convert('RGBA').resize(size,Image.Resampling.LANCZOS)
    assert image.getextrema()[3][0]==0 and image.getextrema()[3][1]>200
    payload=[];header=None;levels=0
    while True:
        b=io.BytesIO();image.save(b,format='DDS',pixel_format='DXT5');encoded=b.getvalue()
        if header is None:header=bytearray(encoded[:128])
        payload.append(encoded[128:]);levels+=1
        if image.size==(1,1):break
        image=image.resize((max(1,image.width//2),max(1,image.height//2)),Image.Resampling.LANCZOS)
    struct.pack_into('<I',header,8,struct.unpack_from('<I',header,8)[0]|0x20000)
    struct.pack_into('<I',header,28,levels);struct.pack_into('<I',header,108,0x401008)
    result=bytes(header)+b''.join(payload)
    width,height=size;offset=128
    for level in range(levels):
        length=((width+3)//4)*((height+3)//4)*16
        h=bytearray(header);struct.pack_into('<II',h,12,height,width);struct.pack_into('<I',h,28,1)
        decoded=Image.open(io.BytesIO(bytes(h)+result[offset:offset+length])).convert('RGBA')
        assert decoded.size==(width,height)
        offset+=length;width=max(1,width//2);height=max(1,height//2)
    assert offset==len(result) and decoded.size==(1,1)
    assert Image.open(io.BytesIO(result)).convert('RGBA').getextrema()[3][0]==0
    return result

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--client-path',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    c=args.client_path.resolve();out=args.output.resolve()
    if out.exists() or out==c or c in out.parents:raise ValueError('Output must be new and outside client')
    selections=json.loads((ROOT/'research/terrain-selection.json').read_text())
    groups=defaultdict(list)
    for index,name in ART.items():
        for t in selections[index]:groups[t['archive']].append({**t,'art':name})
    for t in json.loads((ROOT/'research/snow-selection.json').read_text()):groups[t['archive']].append({**t,'art':'shared-snow'})
    for p in c.glob('Levels/*/Level.pak'):groups[p.relative_to(c).as_posix()]
    groups['effects/effects_Textures.pak']
    manifest={'schema':1,'name':'Aion broad visual upgrade','clientRoot':str(c),'files':[],'textures':[],'environments':{},'sources':{'art':'Built-in image_gen; prompts-global.json','ReShade':'https://reshade.me/downloads/ReShade_Setup_6.8.0.exe','iMMERSE':'f57d3afa1ebe5d1fd6152d4f6fb9a2e75bd1d1cb','ReShadeIncludes':'fd0022170615ce0d8162d219bff07232fa6dd84f'}}
    out.mkdir(parents=True);previews=[];cache={}
    for rel,entries in sorted(groups.items()):
        src=c/rel;original_archive=src.read_bytes()
        with read_pak(src) as z:infos=z.infolist();original={info.filename:z.read(info) for info in infos}
        assert len(infos)==len(original),'Duplicate entry names'
        contents=dict(original);allowed=set()
        for t in entries:
            n=t['entry'];b=original[n];assert digest(b)==t['sha256'],'Source texture changed'
            size=tuple(min(2048,x*2) for x in t['size']);key=(t['sha256'],t['art'],size)
            if key not in cache:
                artwork=ROOT/'assets-global'/(t['art']+'.png')
                dds=make_dds(Image.open(artwork),size,b);cache[key]=dds
                previews.append((t['art'],Image.open(io.BytesIO(b)).convert('RGB'),Image.open(io.BytesIO(dds)).convert('RGB')))
            contents[n]=cache[key];allowed.add(n)
            manifest['textures'].append({'archive':rel,'entry':n,'art':t['art'],'originalSize':t['size'],'size':size,'format':b[84:88].decode(),'sha256':digest(contents[n])})
        if rel.endswith('/Level.pak'):
            n='mission_mission0.xml'
            if n in original:
                contents[n],changes=environment(original[n])
                if changes:manifest['environments'][src.parent.name]=changes;allowed.add(n)
        if rel=='effects/effects_Textures.pak':
            n='env/smoke_heavy.dds';contents[n]=particle_dds(Image.open(ROOT/'assets-global/shared-smoke.png'),original[n]);allowed.add(n)
            manifest['textures'].append({'archive':rel,'entry':n,'art':'shared-smoke','size':[512,512],'format':'DXT5','sha256':digest(contents[n])})
        if not allowed:continue
        buf=io.BytesIO()
        with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as z:
            for info in infos:
                clone=copy.copy(info);clone.compress_type=zipfile.ZIP_DEFLATED;z.writestr(clone,contents[info.filename])
        encoded=bytes(encode_pak(buf.getvalue()));dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(encoded)
        with read_pak(dest) as z:
            assert z.namelist()==list(original) and z.testzip() is None
            for n in original:
                assert z.read(n)==contents[n]
                if n not in allowed:assert z.read(n)==original[n]
        assert src.read_bytes()==original_archive,'Client changed during build'
        manifest['files'].append({'path':rel,'original':digest(original_archive),'staged':digest(encoded),'changedEntries':sorted(allowed),'unchangedEntries':len(original)-len(allowed)})
    manifest['packageId']=digest(json.dumps(manifest,sort_keys=True).encode())[:16]
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    sheet=Image.new('RGB',(720,len(previews)*210),(25,27,32));draw=ImageDraw.Draw(sheet)
    for i,(label,before,after) in enumerate(previews):
        draw.text((10,i*210),label+' - original / compressed replacement',fill='white')
        for j,im in enumerate([before,after]):im.thumbnail((340,182));sheet.paste(im,(10+j*360,i*210+22))
    sheet.save(ROOT/'comparison-global.png')
    print(json.dumps({'packageId':manifest['packageId'],'archiveCount':len(manifest['files']),'textureEntries':len(manifest['textures']),'terrainZones':len(set(t['archive'].split('/')[1] for t in manifest['textures'] if t['archive'].startswith('Levels/'))),'lightingZones':len(manifest['environments']),'lightingAttributes':sum(map(len,manifest['environments'].values())),'unchangedEntries':sum(t['unchangedEntries'] for t in manifest['files'])},indent=2))
if __name__=='__main__':main()
