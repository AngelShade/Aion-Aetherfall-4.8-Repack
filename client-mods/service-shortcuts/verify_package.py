"""Independent archive, native tooltip, menu, signature and preservation checks."""
import argparse,io,json,sys,subprocess
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image
from prepare import HERE,ICONS,RESOURCE_PATHS,TEXTURE,sha,read_pak,binary_xml,menu
def verify(out,installed=False):
 m=json.loads((out/'manifest.json').read_text());root=Path(m['clientRoot']);checks=0
 def check(value,why):
  nonlocal checks
  if not value:raise AssertionError(why)
  checks+=1
 check(m['feature']=='companion-journey-hud','Wrong package')
 for e in m['files']:
  check(sha((out/e['path']).read_bytes())==e['installed'],'Prepared file changed: '+e['path'])
  check(sha((root/e['path']).read_bytes())==(e['installed'] if installed else e['original']),'Current file changed: '+e['path'])
 for e in m['preservedFiles']:check(sha((root/e['path']).read_bytes())==e['sha256'],'Preserved file changed: '+e['path'])
 for p,digest in m['sourceHashes'].items():check(sha(Path(p).read_bytes())==digest,'Maintained source changed after staging')
 # Original archives are copied into evidence during preparation for stable,
 # independent before/after comparison after installation too.
 expected={'Data/ui/ui.pak':{'UI_Preload.xml'},'Textures/ui/ui.pak':{TEXTURE},
  'L10N/enu/Data/data.pak':{'ui/ui_preload.xml','strings/client_strings_ui.xml','ui/game_hud_s1/start_dialog.xml','ui/game_hud_s2/start_dialog.xml'},
  'Data/ui/game_hud_s1/game_hud_s1.pak':{'start_dialog.xml'},'Data/ui/game_hud_s2/game_hud_s2.pak':{'start_dialog.xml'}}
 names={name+'_'+s for name,*rest in ICONS for s in ('up','over','down','button')}
 for rel,allowed in expected.items():
  old=read_pak(out/'originals'/rel);new=read_pak(out/rel)
  check(new.testzip() is None,'Corrupt native archive')
  check(set(new.namelist())-set(old.namelist())==({TEXTURE} if rel.startswith('Textures') else set()),'Unexpected archive additions')
  check(not set(old.namelist())-set(new.namelist()),'Removed native archive entry')
  for entry in new.namelist():
   if entry not in allowed:check(old.read(entry)==new.read(entry),'Unrelated archive entry changed: '+rel+'/'+entry);continue
   if entry==TEXTURE:
    image=Image.open(io.BytesIO(new.read(entry))).convert('RGBA');check(image.size==(128,128) and image.getchannel('A').getextrema()==(0,255),'DDS shape/alpha')
    check(image.tobytes()==Image.open(out/'icon-states.png').convert('RGBA').tobytes(),'Packed atlas differs')
    for row in (0,40):check(len({image.crop((x,row,x+34,row+32)).tobytes() for x in (0,40,80)})==3,'Missing state variants')
    continue
   before=binary_xml(old.read(entry));after=binary_xml(new.read(entry))
   if entry.lower().endswith('ui_preload.xml'):
    found=set()
    for parent in after.iter():
     for child in list(parent):
      if child.get('name') in names:
       found.add(child.get('name'));parent.remove(child)
    check(found==names,'Missing/extra skins or presets')
   elif entry.endswith('client_strings_ui.xml'):
    for name,label,cmd,dx,sid,asset in ICONS:
     found=[e for e in after if e.findtext('name')=='STR_PRIVATE_'+name.upper()+'_HUD']
     check(len(found)==1 and found[0].findtext('id')==str(sid) and found[0].findtext('body')==label,'English native tooltip differs');after.remove(found[0])
   else:
    market=after.find("Widget[@name='central_market_button']");mx,my,_,_=map(int,market.get('frame').split(','))
    for name,label,cmd,dx,sid,asset in ICONS:
     button=after.find(f"Widget[@name='{name}_button']");check(button is not None,'Missing native HUD button')
     check(button.get('preset')==name+'_button' and button.get('tooltip')=='STR_PRIVATE_'+name.upper()+'_HUD','Native control binding differs')
     check(tuple(map(int,button.get('frame').split(',')))==(mx+dx,my-34,34,32),'HUD placement differs');after.remove(button)
   check(ET.tostring(before)==ET.tostring(after),'Unrelated XML/widget/string/skin changed: '+entry)
 pak='Plugin/RelicCalc/RelicCalc.pak';old=read_pak(out/'originals'/pak);new=read_pak(out/pak)
 check(new.namelist()==old.namelist() and new.testzip() is None,'Plugin archive entries changed')
 for entry in old.namelist():check(new.read(entry)==(menu(old.read(entry)) if entry=='PrivateMenus.lua' else old.read(entry)),'Plugin function/bar/pet content changed: '+entry)
 subprocess.run(['java',str(HERE.parent/'season-pass/VerifySignatures.java'),str(out),str(root)],check=True)
 check((out/'native-verification.json').is_file(),'Missing compiled native callback verification')
 native=json.loads((out/'native-verification.json').read_text())
 check(native['status']=='PASS' and native['checks']==286,'Incomplete actual native hook/callback checks')
 check(sha((out/'bin64/AionMarketShortcut.dll').read_bytes())==native['productDllSha256'],'Staged DLL differs from verified normal source build')
 check(sha(Path(native['source']).read_bytes())==native['sourceSha256'] and sha(Path(native['fixture']).read_bytes())==native['fixtureSha256'],'Native proof source/fixture changed')
 print(f'OK: {checks} complete archive, native HUD/English tooltip, state, menu, source/hash and preserved-mod checks; three addon signatures pass')
 return checks
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('package',type=Path);p.add_argument('--installed',action='store_true');a=p.parse_args();verify(a.package.resolve(),a.installed)
