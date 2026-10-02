"""Rebuild the native submenu from verified pre-speech sources, stage only."""
import argparse,copy,json,os,shutil,subprocess
from datetime import datetime
from pathlib import Path
from build_package import patch,sha,ui,HERE,read_pak
from graphics_compatibility import read
from artwork import ASSETS,patch_resources,make_skins,rewrite,skins_xml
from build_package import binary_xml
from xml.etree import ElementTree as ET

def main():
    p=argparse.ArgumentParser();p.add_argument('--backup',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    backup=a.backup.resolve();receipt=read(backup/'manifest.json');root=Path(receipt['clientRoot']).resolve();out=a.output.resolve()
    if not backup.is_relative_to(root/'SpeechBubbles-backups') or out.is_relative_to(root):raise ValueError('Invalid source/staging paths')
    for e in receipt['files']:
        if sha((root/e['path']).read_bytes())!=e['installed']:raise ValueError('Later client change: '+e['path'])
        if e['original'] is not None and sha((backup/e['path']).read_bytes())!=e['original']:raise ValueError('Original backup changed: '+e['path'])
    state=read(root/'DXVK/graphics-menu/installed.json');graphics=read(root/'DXVK/graphics-menu/package/manifest.json');dxvk=read(root/'DXVK/installed.json')
    if state['files']!=graphics['files']:raise ValueError('Graphics tracking differs')
    original_state=read(backup/'DXVK/graphics-menu/installed.json');original_dxvk=read(backup/'DXVK/installed.json')
    baseline=Path(state['backupRoot']).resolve();original_baseline=Path(original_state['backupRoot']).resolve()
    cursor=next(e for e in dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')
    original_cursor=next(e for e in original_dxvk['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll')
    for path in (baseline,original_baseline,Path(cursor['backupPath']),Path(original_cursor['backupPath'])):
        if not path.resolve().is_relative_to(root/'DXVK-backups'):raise ValueError('Baseline outside client backups')
    staged={};baseline_hooks={}
    core=next(e for e in receipt['files'] if e['path']=='bin64/Game.dll')
    staged['bin64/Game.dll'],hooks=patch((backup/'bin64/Game.dll').read_bytes(),core['original'])
    # Restore original layout without touching unrelated archive contents.
    for rel,name in [('Data/ui/game/game.pak','chat_option_dialog.xml'),('L10N/enu/Data/data.pak','ui/game/chat_option_dialog.xml')]:
        live=read_pak(root/rel);prior=read_pak(backup/rel)
        owned={name}
        if rel.startswith('L10N/') and receipt.get('artworkArchives'):
            owned.add('ui/ui_preload.xml')
            library=binary_xml(live.read('ui/ui_preload.xml'))
            category=library.find(".//Category[@name='free_version']")
            for child in list(category):
                if child.get('name') in ('asb_ornate','asb_amber','asb_thought'):category.remove(child)
            if ET.tostring(library)!=ET.tostring(binary_xml(prior.read('ui/ui_preload.xml'))):
                raise ValueError('Unrelated native skin definitions changed')
        if live.namelist()!=prior.namelist() or any(live.read(n)!=prior.read(n) for n in prior.namelist() if n not in owned):
            raise ValueError('Unrelated archive contents changed: '+rel)
        staged[rel],_=ui(backup/rel,name,out/rel)
    # New asset archives are added to the same recoverable speech receipt.
    # Preserve originals in its existing backup tree before any live write.
    asset_sources=[];new_originals=[]
    for rel in ASSETS:
        old=next((e for e in receipt['files'] if e['path']==rel),None)
        source=backup/rel if old else root/rel
        asset_sources.append(source)
        if old is None:
            original=source.read_bytes();saved=(backup/rel).relative_to(root).as_posix()
            if (root/saved).exists():raise ValueError('Unrecorded artwork backup exists')
            staged[saved]=original;new_originals.append(saved)
            receipt['files'].append({'path':rel,'original':sha(original),'installed':None})
    artout=out/'artwork';artout.mkdir(parents=True,exist_ok=True)
    staged.update(patch_resources(*asset_sources,out/'L10N/enu/Data/data.pak',artout))
    definitions,_=make_skins(artout)
    for e in graphics['files']:
        rel=e['path']
        if rel=='bin64/AionGraphicsMenu.dll':continue
        prior=next(v for v in original_state['files'] if v['path']==rel)
        source=original_baseline/rel
        if sha(source.read_bytes())!=prior['original']:raise ValueError('Pre-speech graphics baseline changed')
        dest=(baseline/rel).relative_to(root).as_posix()
        if rel=='bin64/Game.dll':staged[dest],baseline_hooks['graphics']=patch(source.read_bytes(),prior['original'])
        else:
            name='chat_option_dialog.xml' if rel.startswith('Data/') else 'ui/game/chat_option_dialog.xml'
            staged[dest],_=ui(source,name,out/dest)
            if rel=='L10N/enu/Data/data.pak':
                staged[dest]=rewrite(out/dest,{'ui/ui_preload.xml':skins_xml(read_pak(out/dest).read('ui/ui_preload.xml'),definitions)})
        staged['DXVK/graphics-menu/package/'+rel]=staged[rel]
        e['original']=sha(staged[dest]);e['installed']=sha(staged[rel])
    prior_cursor=Path(original_cursor['backupPath'])
    if sha(prior_cursor.read_bytes())!=original_cursor['original'].lower():raise ValueError('Pre-speech cursor baseline changed')
    dest=Path(cursor['backupPath']).relative_to(root).as_posix()
    staged[dest],baseline_hooks['cursor']=patch(prior_cursor.read_bytes(),original_cursor['original'].lower())
    cursor.update(original=sha(staged[dest]),installed=sha(staged['bin64/Game.dll']))
    state.update(files=copy.deepcopy(graphics['files']),speechMenuRevisedAt=datetime.now().isoformat())
    for rel,value in [('DXVK/graphics-menu/installed.json',state),('DXVK/graphics-menu/package/manifest.json',graphics),('DXVK/installed.json',dxvk)]:
        staged[rel]=json.dumps(value,indent=2).encode()
    work=out.parent/(out.name+'-compile');work.mkdir(parents=True,exist_ok=True)
    dll=out/'bin64/AionSpeechBubbles.dll';dll.parent.mkdir(parents=True,exist_ok=True)
    vcvars=Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    lines=['@echo off',f'call "{vcvars}" >nul']
    for test,target in [(False,dll),(True,work/'AionSpeechBubblesTest.dll')]:
        lines.append(f'cl /nologo /std:c++17 /EHsc /O2 /MT /LD {"/DASB_TEST" if test else ""} /Fo:"{work / ("test.obj" if test else "speech.obj")}" "{HERE / "speech_bubbles.cpp"}" /link /OUT:"{target}" /IMPLIB:"{work / ("test.lib" if test else "speech.lib")}"')
        lines.append('if errorlevel 1 exit /b 1')
    script=work/'compile.cmd';script.write_text('\n'.join(lines)+'\n');subprocess.run(f'cmd.exe /d /s /c ""{script}""',check=True)
    staged['bin64/AionSpeechBubbles.dll']=dll.read_bytes()
    for e in receipt['files']:
        if e['path'] in staged:e['installed']=sha(staged[e['path']])
    receipt.update(hooks=hooks,speechMenuRevision=5,unchangedEntries={},artworkArchives=list(ASSETS),styles=['Classic','Wings','Crystal','Cloud','Paws'])
    rel_receipt=(backup/'manifest.json').relative_to(root).as_posix();staged[rel_receipt]=json.dumps(receipt,indent=2).encode()
    files=[]
    for rel,data in staged.items():
        target=out/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        files.append({'path':rel,'original':sha((root/rel).read_bytes()) if (root/rel).exists() else None,'installed':sha(data)})
    manifest={'clientRoot':str(root),'speechReceipt':rel_receipt,'files':files,'hooks':hooks,'styles':receipt['styles'],'graphicsCompatibility':receipt['graphicsCompatibility'],'artworkArchives':list(ASSETS),'newOriginals':new_originals}
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2));(out/'graphics-baseline-hooks.json').write_text(json.dumps(baseline_hooks,indent=2))
    print('Staged native Chat Bubble submenu, automatic character synchronization, original Chat Options, and composed launcher records. Client untouched.')
if __name__=='__main__':main()
