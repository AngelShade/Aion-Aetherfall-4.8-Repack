"""Stage native Wardrobe without rebuilding any installed Game.dll patches."""
import argparse,hashlib,io,json,re,shutil,subprocess,sys,xml.etree.ElementTree as E,zipfile
from pathlib import Path
from native_wardrobe_layout import build as layout
from native_wardrobe_icons import build as icons
from native_wardrobe_theme import stage as theme,assets as theme_assets

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--client',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--bridge',type=Path,required=True)
    parser.add_argument('--java',type=Path,required=True)
    parser.add_argument('--codec-directory',type=Path,required=True)
    parser.add_argument('--icon-map',type=Path)
    args=parser.parse_args();client=args.client.resolve();out=args.output.resolve();source=Path(__file__).resolve().parent
    if out.exists() or out==client or client in out.parents:raise ValueError('Use a fresh output directory outside the client')
    sys.path.insert(0,str(args.codec_directory.resolve()))
    from fire_temple_probe import read_pak,binary_xml
    from patch_client_world import encode_pak
    xml=layout();tree=E.fromstring(xml)
    names=[n.get('name') for n in tree.iter() if n.get('name')]
    if len(names)!=len(set(names)):raise ValueError('Duplicate native widget names')
    if any(n.get('type','').lower()=='browser' for n in tree.iter()):raise ValueError('Native Wardrobe must not create a web view')
    for relative,entry in [('Data/ui/ui.pak','UI_Preload.xml'),('L10N/enu/data/data.pak','ui/ui_preload.xml')]:
        with read_pak(client/relative) as archive:payload=archive.read(entry)
        skins=binary_xml(payload) if payload[0]==128 else E.fromstring(payload)
        available={n.get('name') for n in skins.iter()}
        for n in tree.iter():
            if n.get('preset') and n.get('preset') not in available:raise ValueError('Missing native preset '+n.get('preset'))
    with read_pak(client/'Plugin/RelicCalc/RelicCalc.pak') as archive:content={name:archive.read(name) for name in archive.namelist()}
    original=dict(content)
    lua=content['PrivateMenus.lua'].decode('utf-8-sig')
    replacements={'PrivateWardrobe_OnLoad':'WardrobeNative_OnLoad();','PrivateWardrobe_Open':'WardrobeNative_Open();'}
    for name,body in replacements.items():
        lua,count=re.subn(r'function '+name+r'\(\).*?\nend',f'function {name}()\n    {body}\nend',lua,flags=re.S)
        if count!=1:raise ValueError('Unexpected installed Wardrobe menu functions')
    content['PrivateMenus.lua']=lua.encode('utf-8')
    content['Wardrobe.xml']=xml.replace("encoding='utf-8'","encoding='utf-16'").encode('utf-16')
    for name in ('WardrobeJSON.lua','WardrobeTheme.lua','WardrobeNative.lua'):content[name]=(source/name).read_bytes()
    content['WardrobeIcons.lua']=args.icon_map.read_bytes() if args.icon_map else icons(client)
    toc=content['RelicCalc.toc'].decode('utf-8-sig').replace('\r','').splitlines()
    native=['WardrobeJSON.lua','WardrobeIcons.lua','WardrobeTheme.lua','WardrobeNative.lua']
    toc=[name for name in toc if name not in native];index=toc.index('Wardrobe.xml');toc[index:index]=native
    content['RelicCalc.toc']=('\r\n'.join(toc)+'\r\n').encode('utf-8')
    changed={name for name in content if content[name]!=original.get(name)}
    expected={'Wardrobe.xml','PrivateMenus.lua','RelicCalc.toc',*native}
    if not changed.issubset(expected):raise ValueError('Unexpected addon changes')
    packed=io.BytesIO()
    with zipfile.ZipFile(packed,'w',compression=zipfile.ZIP_DEFLATED) as archive:
        for name,payload in content.items():archive.writestr(name,payload)
    package=out/'Plugin/RelicCalc/RelicCalc.pak';package.parent.mkdir(parents=True);package.write_bytes(encode_pak(packed.getvalue()))
    with read_pak(package) as archive:
        assert archive.testzip() is None
        for name,payload in content.items():assert archive.read(name)==payload,name
    subprocess.run([str(args.java),str(source/'SignClientPackages.java'),str(client),str(out)],check=True)
    for name in ('bin64/game.dll','bin64/crysystem.dll'):
        dest=out/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(client/name,dest)
    shutil.copyfile(args.bridge,out/'bin64/AionIconBridge.dll')
    theme(out)
    preserved=['bin32/bin32.pak','Data/func_pet/func_pet.pak','Data/Items/Items.pak','bin64/AionIconBridge.index',
        'Data/ui/game/game.pak','L10N/enu/data/data.pak','DXVK/graphics-menu/package/manifest.json',
        'DXVK/graphics-menu/installed.json','DXVK/installed.json','Aion Start.bat']
    manifest=dict(clientRoot=str(client),signatureIsolation='archive-v2',nativeWardrobe=True,nativeWardrobeTheme='blue-v1',legacyAddon=[],retiredFiles=[],
        files=[dict(path=path.relative_to(out).as_posix(),original=digest(client/path.relative_to(out)) if (client/path.relative_to(out)).exists() else None,staged=digest(path)) for path in sorted(out.rglob('*')) if path.is_file()],
        preservedFiles=[dict(path=name,sha256=digest(client/name)) for name in preserved if (client/name).exists()],
        nativeWardrobeWidgets=len(names),nativeWardrobeChangedEntries=sorted(changed))
    assert len(manifest['files'])==9+len(theme_assets())
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('Prepared native Wardrobe:',len(names),'widgets;',len(changed),'addon entries;',len(manifest['files']),'signed/verified files including opaque theme surfaces')
    print('Installed Game.dll, CrySystem.dll, item data, inventory UI, and other addon entries preserved.')

if __name__=='__main__':main()
