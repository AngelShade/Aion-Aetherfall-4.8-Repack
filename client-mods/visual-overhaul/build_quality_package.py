"""Build the maximum-quality native scene/material pass against current installed assets."""
import argparse,copy,hashlib,io,json,math,re,sys,zipfile
from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'expanded-warehouse'))
from codec import read_pak,encode_pak,binary_xml,encode_binary_xml
from build_package import make_dds

def sha(data):return hashlib.sha256(data).hexdigest()

def edit_scene(data,profile):
    binary=data[:1]==b'\x80'
    root=binary_xml(data) if binary else ET.fromstring(data)
    original=copy.deepcopy(root); changes=[]
    for env_index,env in enumerate(root.iter('Environment')):
        for tag,keys in [('HeightMap',('GeometryLodRatio','TextureLodRatio')),
                         ('EnvState',('AmbientAmplify','SunAmplify')),
                         ('MRTOcean',('ReflectAmount','RefractAmount'))]:
            node=env.find(tag)
            if node is None:continue
            for key in keys:
                before=node.get(key)
                if before is None:continue
                try:value=float(before)
                except ValueError:continue
                if not math.isfinite(value) or value<=0:continue
                value*=profile[key]
                if tag=='MRTOcean':value=min(1,value)
                after=f'{value:.8g}'
                if after==before:continue
                node.set(key,after)
                changes.append(dict(environment=env_index,tag=tag,key=key,before=before,after=after))
    if not changes:return data,[]
    # Exact structural reversal is a packaging invariant, not a gameplay test.
    check=copy.deepcopy(root);envs=list(check.iter('Environment'))
    for c in changes:envs[c['environment']].find(c['tag']).set(c['key'],c['before'])
    if ET.tostring(check)!=ET.tostring(original):raise ValueError('Scene edit exceeded its rendering fields')
    result=encode_binary_xml(root) if binary else ET.tostring(root,encoding='utf-16')
    decoded=binary_xml(result) if binary else ET.fromstring(result)
    if ET.tostring(decoded)!=ET.tostring(root):raise ValueError('Scene serialization differs')
    return result,changes

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    args=p.parse_args();client=args.client.resolve();out=args.output.resolve()
    if out.exists() or client==out or client in out.parents:raise ValueError('Use fresh output outside the client')
    profile=json.loads((HERE/'quality-profile.json').read_text())
    groups={path.relative_to(client).as_posix():[] for path in sorted((client/'Levels').glob('*/Level.pak'))}
    for entry in profile['assets']:groups.setdefault(entry['archive'],[]).append(entry)
    out.mkdir(parents=True);manifest=dict(schema=1,name=profile['name'],clientRoot=str(client),files=[],scenes={},textures=[],limits=profile['limits'])
    for index,(relative,assets) in enumerate(sorted(groups.items())):
        src=client/relative;original=src.read_bytes();changed={}
        with read_pak(src) as z:
            infos=z.infolist()
            if len({i.filename for i in infos})!=len(infos):raise ValueError('Duplicate archive entries: '+relative)
            if relative.lower().endswith('/level.pak'):
                for name in z.namelist():
                    if re.fullmatch(r'mission_[^/\\]+\.xml',name,re.I):
                        payload,changes=edit_scene(z.read(name),profile['level'])
                        if changes:
                            changed[name]=payload;manifest['scenes'][relative+'/'+name]=changes
            for asset in assets:
                native=z.read(asset['entry'])
                changed[asset['entry']]=make_dds(Image.open(HERE/'assets-quality'/(asset['asset']+'.png')),asset['size'],native)
                manifest['textures'].append(dict(**asset,original=sha(native),staged=sha(changed[asset['entry']])))
            if not changed:continue
            buffer=io.BytesIO()
            with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED) as dest:
                for info in infos:dest.writestr(copy.copy(info),changed.get(info.filename,z.read(info)))
        encoded=encode_pak(buffer.getvalue());dest=out/relative;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(encoded)
        if src.read_bytes()!=original:raise ValueError('Client changed during preparation: '+relative)
        manifest['files'].append(dict(path=relative,original=sha(original),staged=sha(encoded),changedEntries=sorted(changed)))
        if index%20==0:print(f'Prepared {index+1}/{len(groups)} archive candidates',flush=True)
    manifest['packageId']=sha(json.dumps(manifest,sort_keys=True).encode())[:16]
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print(json.dumps(dict(packageId=manifest['packageId'],archives=len(manifest['files']),scenes=len(manifest['scenes']),textures=len(manifest['textures'])),indent=2))

if __name__=='__main__':main()
