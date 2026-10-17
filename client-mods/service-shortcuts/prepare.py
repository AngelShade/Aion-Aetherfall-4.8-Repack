"""Stage Companion/Journey native HUD shortcuts from the current installed client."""
import argparse,copy,hashlib,json,os,struct,subprocess,sys
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image,ImageEnhance

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'speech-bubbles'))
from artwork import read_pak,rewrite,binary_xml,encode_binary_xml
RESOURCE_PATHS=['Data/ui/ui.pak','Textures/ui/ui.pak','L10N/enu/Data/data.pak',
 'Data/ui/game_hud_s1/game_hud_s1.pak','Data/ui/game_hud_s2/game_hud_s2.pak']
ICONS=[('companion_menu','Player Companions','/companions',-40,990100003,'companions-outlined.png'),
 ('journey_menu','Choose Your Journey','/journey',40,990100004,'journey-outlined.png')]
TEXTURE='companion_journey_shortcuts.dds'
def sha(data):return hashlib.sha256(data).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def artwork(out):
 atlas=Image.new('RGBA',(128,128));defs=[]
 for row,(name,label,command,dx,string_id,asset) in enumerate(ICONS):
  image=Image.open(HERE/'assets'/asset).convert('RGBA')
  if image.getchannel('A').getextrema()!=(0,255):raise ValueError('Artwork requires genuine transparency')
  image=image.crop(image.getbbox());image.thumbnail((32,30),Image.Resampling.LANCZOS)
  for index,(state,brightness) in enumerate([('up',1),('over',1.28),('down',.72)]):
   icon=ImageEnhance.Brightness(image).enhance(brightness)
   atlas.alpha_composite(icon,(index*40+(34-icon.width)//2,row*40+(32-icon.height)//2))
   defs.append(ET.Element('Skin',name=name+'_'+state,src_image=f'{index*40},{row*40},34,32',texture='Textures/UI/companion_journey_shortcuts'))
  preset=ET.Element('Preset',name=name+'_button',type='button')
  for state in ('up','over','down'):ET.SubElement(preset,'SkinRef',main_state='0',name=name+'_'+state,sub_state=state)
  defs.append(preset)
 atlas.save(out/'icon-states.png')
 header=[124,0x100f,128,128,512,0,0]+[0]*11+[32,0x41,0,32,0xff,0xff00,0xff0000,0xff000000,0x1000,0,0,0,0]
 return defs,b'DDS '+struct.pack('<31I',*header)+atlas.tobytes()
def library(data,defs):
 tree=binary_xml(data);category=tree.find(".//Category[@name='version5']")
 if category is None or tree.find(".//Skin[@name='season_ticket_up']") is None:raise ValueError('Current Market/Season Pass artwork required')
 if any(tree.find(f".//*[@name='{name}_up']") is not None for name,*rest in ICONS):raise ValueError('Service shortcut already installed')
 category.extend(copy.deepcopy(defs));return encode_binary_xml(tree)
def hud(data):
 tree=binary_xml(data);market=tree.find("Widget[@name='central_market_button']");ticket=tree.find("Widget[@name='season_pass_button']")
 if tree.get('name')!='start_dialog' or market is None or ticket is None:raise ValueError('Current native Market/ticket HUD required')
 x,y,w,h=map(int,market.get('frame').split(','));width,height=map(int,tree.get('frame').split(',')[2:])
 for name,label,command,dx,string_id,asset in ICONS:
  if tree.find(f"Widget[@name='{name}_button']") is not None:raise ValueError('HUD shortcut already present')
  if x+dx<0 or y-34<0 or x+dx+34>width or y-2>height:raise ValueError('Shortcut outside native HUD')
  ET.SubElement(tree,'Widget',name=name+'_button',type='button',frame=f'{x+dx},{y-34},34,32',flag='visible',preset=name+'_button',tooltip='STR_PRIVATE_'+name.upper()+'_HUD')
 return encode_binary_xml(tree)
def strings(data):
 tree=binary_xml(data)
 for name,label,command,dx,string_id,asset in ICONS:
  key='STR_PRIVATE_'+name.upper()+'_HUD'
  if any(e.findtext('name')==key or e.findtext('id')==str(string_id) for e in tree):raise ValueError('Tooltip identity collision')
  e=ET.SubElement(tree,'string');ET.SubElement(e,'id').text=str(string_id);ET.SubElement(e,'name').text=key;ET.SubElement(e,'body').text=label
 return encode_binary_xml(tree)
def transform(path,rel,defs,dds):
 archive=read_pak(path)
 if rel=='Data/ui/ui.pak':changes={'UI_Preload.xml':library(archive.read('UI_Preload.xml'),defs)}
 elif rel=='Textures/ui/ui.pak':
  if TEXTURE in archive.namelist():raise ValueError('Texture already exists')
  changes={TEXTURE:dds}
 elif rel=='L10N/enu/Data/data.pak':
  changes={'ui/ui_preload.xml':library(archive.read('ui/ui_preload.xml'),defs),'strings/client_strings_ui.xml':strings(archive.read('strings/client_strings_ui.xml'))}
  for style in (1,2):
   entry=f'ui/game_hud_s{style}/start_dialog.xml';changes[entry]=hud(archive.read(entry))
 elif rel in RESOURCE_PATHS:changes={'start_dialog.xml':hud(archive.read('start_dialog.xml'))}
 else:raise ValueError('Unexpected resource')
 return rewrite(path,changes)
def menu(data):
 for name,label,command,dx,string_id,asset in ICONS:
  variable='SLASH_PRIVATECOMPANIONS1' if command=='/companions' else 'SLASH_PRIVATEJOURNEY1'
  old=f'RegisterMenu("{label}", {variable}, "v5_start_menu_relic_up")'.encode()
  if data.count(old)!=1:raise ValueError('Unexpected current menu registration: '+label)
  data=data.replace(old,f'RegisterMenu("{label}", {variable}, "{name}_up")'.encode())
 return data
def compile_extension(root,out):
 native=out/'native'
 subprocess.run([sys.executable,str(HERE/'verify_native.py'),'--client',str(root),'--out',str(native)],check=True)
 evidence=read(native/'evidence.json');source=HERE.parent/'market-shortcut/market_shortcut.cpp'
 if evidence['status']!='PASS' or evidence['sourceSha256']!=sha(source.read_bytes()):raise ValueError('Native evidence is stale')
 product=Path(evidence['productDll'])
 if sha(product.read_bytes())!=evidence['productDllSha256']:raise ValueError('Compiled native artifact changed')
 (out/'native-verification.json').write_text(json.dumps(evidence,indent=2))
 return product.read_bytes()
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--client',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
 root=args.client.resolve();out=args.output.resolve()
 if out.exists() or out==root or root in out.parents or HERE.parents[1] in out.parents:raise ValueError('Use new external output outside client/source')
 out.mkdir(parents=True);staged={}
 def stage(rel,data):
  rel=Path(rel).as_posix();target=out/rel
  if rel in staged and staged[rel]!=data:raise ValueError('Conflicting payload')
  target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);staged[rel]=data
 defs,dds=artwork(out)
 for rel in RESOURCE_PATHS:
  original=out/'originals'/rel;original.parent.mkdir(parents=True,exist_ok=True);original.write_bytes((root/rel).read_bytes())
  stage(rel,transform(root/rel,rel,defs,dds))
 pak='Plugin/RelicCalc/RelicCalc.pak';stage(pak,rewrite(root/pak,{'PrivateMenus.lua':menu(read_pak(root/pak).read('PrivateMenus.lua'))}))
 original=out/'originals'/pak;original.parent.mkdir(parents=True,exist_ok=True);original.write_bytes((root/pak).read_bytes())
 stage('bin64/AionMarketShortcut.dll',compile_extension(root,out))
 subprocess.run(['java',str(HERE.parent/'transmog-menu/SignClientPackages.java'),str(root),str(out)],check=True)
 (out/'Pub.key').unlink()
 for rel in ['Addon.key','bin32/bin32.pak.sig','Data/func_pet/func_pet.pak.sig',pak+'.sig']:stage(rel,(out/rel).read_bytes())
 # Compose only the active locale graphics recovery. Native Game.dll/cursor hooks stay unchanged.
 graphics=read(root/'Mods/GraphicsMenu/installed.json');package=read(root/'Mods/GraphicsMenu/package/manifest.json')
 if graphics['files']!=package['files']:raise ValueError('Graphics recovery metadata differs')
 locale=next(e for e in graphics['files'] if e['path']=='L10N/enu/Data/data.pak');baseline=Path(graphics['backupRoot'])/locale['path']
 if not baseline.resolve().is_relative_to(root/'Mods/Backups/Renderer') or sha(baseline.read_bytes())!=locale['original'] or sha((root/locale['path']).read_bytes())!=locale['installed']:raise ValueError('Graphics locale guards differ')
 for rel in ['Mods/GraphicsMenu/installed.json','Mods/GraphicsMenu/package/manifest.json',baseline.relative_to(root).as_posix()]:
  original=out/'originals'/rel;original.parent.mkdir(parents=True,exist_ok=True);original.write_bytes((root/rel).read_bytes())
 stage(baseline.relative_to(root),transform(baseline,locale['path'],defs,dds))
 locale.update(original=sha(staged[baseline.relative_to(root).as_posix()]),installed=sha(staged[locale['path']]))
 package['files']=copy.deepcopy(graphics['files'])
 stage('Mods/GraphicsMenu/installed.json',json.dumps(graphics,indent=2).encode());stage('Mods/GraphicsMenu/package/manifest.json',json.dumps(package,indent=2).encode());stage('Mods/GraphicsMenu/package/'+locale['path'],staged[locale['path']])
 sys.path.insert(0,str(HERE.parent/'season-pass'));from prepare import market_hooks
 preserved=['Pub.key','bin64/Game.dll','bin64/AionIconBridge.dll','bin64/AionGraphicsMenu.dll','bin64/AionRememberLogin.dll','bin64/AionSpeechBubbles.dll','bin64/crysystem.dll','bin64/XRenderD3D9.dll','bin32/bin32.pak','Data/func_pet/func_pet.pak','Data/ui/game/game.pak','Data/Items/Items.pak','bin64/AionIconBridge.index']
 files=[dict(path=rel,original=sha((root/rel).read_bytes()) if (root/rel).exists() else None,installed=sha(data)) for rel,data in staged.items()]
 inputs=[HERE/'prepare.py',HERE/'verify_package.py',HERE/'verify_native.py',HERE/'install.ps1',
  HERE.parent/'market-shortcut/market_shortcut.cpp',HERE.parent/'transmog-menu/SignClientPackages.java',
  HERE.parent/'season-pass/VerifySignatures.java',HERE.parent/'speech-bubbles/artwork.py',
  *[HERE/'assets'/r[-1] for r in ICONS]]
 receipt=dict(feature='companion-journey-hud',clientRoot=str(root),sourceKey=sha((root/'Pub.key').read_bytes()),files=files,preservedFiles=[dict(path=rel,sha256=sha((root/rel).read_bytes())) for rel in preserved],hooks=market_hooks(root),sourceHashes={str(p):sha(p.read_bytes()) for p in inputs})
 (out/'manifest.json').write_text(json.dumps(receipt,indent=2));print('OK: staged',len(files),'guarded files; current client untouched')
if __name__=='__main__':main()
