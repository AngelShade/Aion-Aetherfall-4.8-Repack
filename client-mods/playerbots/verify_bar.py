"""Verify command-bar package against the current installation; never installs."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as E
from prepare_bar import HERE,sha,read_pak,normalized_pe

def verify(root,out):
    manifest=json.loads((out/'manifest.json').read_text())
    assert manifest['feature']=='playerbot-party-bar' and Path(manifest['clientRoot']).resolve()==root.resolve()
    expected={'bin64/AionIconBridge.dll','Plugin/RelicCalc/RelicCalc.pak','Addon.key','Plugin/RelicCalc/RelicCalc.pak.sig','bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig'}
    revision=manifest.get('revision') in ('layout-2','layout-3','layout-4','formations-1')
    if manifest.get('revision') in ('layout-2','layout-4'):expected.remove('bin64/AionIconBridge.dll')
    if manifest.get('revision') in ('layout-3','formations-1'):
        live=(root/'bin64/AionIconBridge.dll').read_bytes()
        assert sha(live)==manifest['bridgeSourceBaseline']
        assert sha(normalized_pe(live))==manifest['baselineRebuildNormalized']
    assert {e['path'] for e in manifest['files']}==expected
    assert {p.relative_to(out).as_posix() for p in out.rglob('*') if p.is_file()}==expected|{'manifest.json'}
    for e in manifest['files']:
        assert sha((root/e['path']).read_bytes())==e['original'],e['path']+' changed since staging'
        assert sha((out/e['path']).read_bytes())==e['staged'],e['path']+' staged hash mismatch'
    for e in manifest['preservedFiles']:assert sha((root/e['path']).read_bytes())==e['sha256'],e['path']
    s=manifest['serverBaseline'];server=Path(s['root'])
    assert sha((server/'libs/game-server-4.8-SNAPSHOT.jar').read_bytes())==s['jar']
    assert sha((server/'start.bat').read_bytes())==s['launcher']
    for e in s['overrides']:assert sha((server/e['path']).read_bytes())==e['sha256']
    old=read_pak(root/'Plugin/RelicCalc/RelicCalc.pak');new=read_pak(out/'Plugin/RelicCalc/RelicCalc.pak')
    assert new.namelist()==(old.namelist() if revision else old.namelist()+['PlayerBotBar.lua','PlayerBotBar.xml']) and new.testzip() is None
    for entry in old.namelist():
        if entry not in (['PlayerBotBar.lua','PlayerBotBar.xml'] if revision else ['RelicCalc.toc']):assert old.read(entry)==new.read(entry),entry
    if revision:
        for name in ('PlayerBotBar.lua','PlayerBotBar.xml'):assert new.read(name)==(HERE/name).read_bytes(),name
    else:assert new.read('RelicCalc.toc')==old.read('RelicCalc.toc')+b'\r\nPlayerBotBar.lua\r\nPlayerBotBar.xml\r\n'
    tree=E.fromstring(new.read('PlayerBotBar.xml'))
    names=[n.get('name') for n in tree.iter() if n.tag in ('Dialog','Widget')]
    assert len(set(names))==len(names)==int(tree.get('widget_cnt'))
    assert 'not_movable' not in tree.find('Dialog').get('flag')
    assert 'PlayerBotBarSummon' in names
    if manifest.get('revision') in ('layout-4','formations-1'):assert tree.find('Dialog').get('preset')=='v5_dialog','Keep footer divider away from the command rows'
    if manifest.get('revision')=='formations-1':
        assert all('PlayerBotBar'+n in names for n in ('Circle','Box','Line','Spread'))
    subprocess.run(['java',str(HERE.parent/'season-pass/VerifySignatures.java'),str(out),str(root)],check=True)
    print('OK: party bar payload, all three signatures, every previous addon entry, client hashes and effective server override preserved.')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--client',type=Path,required=True);p.add_argument('--staged',type=Path,required=True);a=p.parse_args();verify(a.client,a.staged)
