"""Stage a native remembered-login checkbox and compose existing recovery guards."""
import argparse, copy, hashlib, io, json, os, subprocess, sys, zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent/'expanded-warehouse'))
from codec import read_pak,encode_pak,binary_xml,encode_binary_xml
from patch_binary import patch
LABEL='STR_PRIVATE_REMEMBER_LOGIN'
LABEL_TEXT='Remember Account & Password'
PRESERVED=['Pub.key','Addon.key','bin64/crysystem.dll','bin64/AionMarketShortcut.dll','bin64/AionIconBridge.dll','bin64/AionGraphicsMenu.dll','bin64/XRenderD3D9.dll','bin32/bin32.pak','Data/func_pet/func_pet.pak','Data/ui/game/game.pak','Data/Items/Items.pak','Textures/ui/ui.pak','Plugin/RelicCalc/RelicCalc.pak','Plugin/RelicCalc/RelicCalc.pak.sig']
def sha(data):return hashlib.sha256(data).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def login(data):
    tree=binary_xml(data);dialog=tree.find(".//Dialog[@name='login_dialog']")
    if dialog is None or dialog.find(".//*[@name='remember_login']") is not None:raise ValueError('Unexpected native login layout')
    box=dialog.find(".//Widget[@name='login_box']")
    if box is None or dialog.find(".//Widget[@name='password']").get('flag')!='password':raise ValueError('Native password field changed')
    box.set('frame','440,370,380,160')
    ET.SubElement(box,'Widget',name='remember_login',type='button',style='checkbox',preset='v5_check',frame='117,95,255,22',font='default',font_size='14',halign='left',valign='middle',text=LABEL,text_offset='5,0',text_color='0.95,1.0,0.90,1.0')
    ET.SubElement(box,'Widget',name='remember_login_status',type='static',flag='transparent',frame='122,119,255,18',font='default',font_size='11',text_color='0.66,0.72,0.72,1.0',halign='left',valign='middle')
    place_account_buttons(dialog)
    return encode_binary_xml(tree)
def place_account_buttons(dialog):
    # Match the login form's vertical anchor so the row stays beneath the
    # checkbox and its status line at different screen heights.
    for name in ['login_new','login_password']:
        button=dialog.find(".//Widget[@name='"+name+"']")
        if button is None:raise ValueError('Missing account button: '+name)
        x,y,w,h=button.get('frame').split(',');button.set('frame',f'{x},518,{w},{h}');button.set('v_pos_align','middle')
def strings(data):
    tree=binary_xml(data)
    if any(e.findtext('name')==LABEL or e.findtext('id')=='990100010' for e in tree):raise ValueError('Remember login label already exists')
    e=ET.SubElement(tree,'string');ET.SubElement(e,'id').text='990100010';ET.SubElement(e,'name').text=LABEL;ET.SubElement(e,'body').text=LABEL_TEXT
    return encode_binary_xml(tree)
def archive(path,rel):
    z=read_pak(path);changes={'UI_Login.xml':login(z.read('UI_Login.xml'))} if rel=='Data/ui/ui.pak' else {'ui/ui_login.xml':login(z.read('ui/ui_login.xml')),'strings/client_strings_ui.xml':strings(z.read('strings/client_strings_ui.xml'))}
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as out:
        for entry in z.infolist():out.writestr(copy.copy(entry),changes.get(entry.filename,z.read(entry)))
    return encode_pak(stream.getvalue())
def compile_dll(out,test=False):
    work=out.parent/(out.name+'-compile');work.mkdir(parents=True,exist_ok=True);dll=out/'bin64/AionRememberLogin.dll';dll.parent.mkdir(parents=True,exist_ok=True)
    vcvars=Path(os.environ.get('ProgramFiles(x86)',r'C:\Program Files (x86)'))/'Microsoft Visual Studio/2022/BuildTools/VC/Auxiliary/Build/vcvars64.bat'
    script=work/'compile.cmd';script.write_text(f'@echo off\ncall "{vcvars}" >nul\ncl /nologo /std:c++17 /EHsc /O2 /MT /LD {"/DREMEMBER_TEST" if test else ""} /Fo:"{work / "remember.obj"}" "{HERE / "remember_login.cpp"}" /link /OUT:"{dll}" /IMPLIB:"{work / "remember.lib"}"\n')
    subprocess.run(f'cmd.exe /d /s /c ""{script}""',check=True);return dll.read_bytes()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();root=a.client.resolve();out=a.output.resolve()
    if out.exists() or out==root or root in out.parents:raise ValueError('Use a fresh staging directory outside the client')
    staged={}
    def stage(rel,data):
        rel=Path(rel).as_posix();destination=out/rel
        if rel in staged and staged[rel]!=data:raise ValueError('Conflicting replacement')
        staged[rel]=data;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(data)
    new_game,hooks=patch((root/'bin64/Game.dll').read_bytes());stage('bin64/Game.dll',new_game)
    for rel in ['Data/ui/ui.pak','L10N/enu/Data/data.pak']:stage(rel,archive(root/rel,rel))
    stage('bin64/AionRememberLogin.dll',compile_dll(out))
    graphics=read(root/'DXVK/graphics-menu/installed.json');manifest=read(root/'DXVK/graphics-menu/package/manifest.json')
    if graphics['files']!=manifest['files']:raise ValueError('Graphics recovery metadata differs')
    for rel in ['bin64/Game.dll','L10N/enu/Data/data.pak']:
        entry=next(e for e in graphics['files'] if e['path']==rel);baseline=Path(graphics['backupRoot'])/rel
        if not baseline.resolve().is_relative_to(root/'DXVK-backups') or sha(baseline.read_bytes())!=entry['original'] or sha((root/rel).read_bytes())!=entry['installed']:raise ValueError('Graphics recovery guard differs')
        changed=patch(baseline.read_bytes())[0] if rel.endswith('.dll') else archive(baseline,rel)
        stage(baseline.relative_to(root),changed);entry.update(original=sha(changed),installed=sha(staged[rel]))
        stage('DXVK/graphics-menu/package/'+rel,staged[rel])
    manifest['files']=graphics['files'];stage('DXVK/graphics-menu/installed.json',json.dumps(graphics,indent=2).encode());stage('DXVK/graphics-menu/package/manifest.json',json.dumps(manifest,indent=2).encode())
    cursor=read(root/'DXVK/installed.json');entry=next(e for e in cursor['nativeCursorPatch']['files'] if e['path']=='bin64/Game.dll');baseline=Path(entry['backupPath'])
    if not baseline.resolve().is_relative_to(root/'DXVK-backups') or sha(baseline.read_bytes())!=entry['original'] or sha((root/entry['path']).read_bytes())!=entry['installed']:raise ValueError('Cursor recovery guard differs')
    changed=patch(baseline.read_bytes())[0];stage(baseline.relative_to(root),changed);entry.update(original=sha(changed),installed=sha(staged['bin64/Game.dll']));stage('DXVK/installed.json',json.dumps(cursor,indent=2).encode())
    receipt=dict(feature='remember-login',clientRoot=str(root),sourceKey=sha((root/'Pub.key').read_bytes()),hooks=hooks,
        files=[dict(path=rel,original=sha((root/rel).read_bytes()) if (root/rel).is_file() else None,installed=sha(data)) for rel,data in staged.items()],
        preservedFiles=[dict(path=rel,sha256=sha((root/rel).read_bytes())) for rel in PRESERVED])
    (out/'manifest.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');print('OK staged',len(staged),'files; live client unchanged')
if __name__=='__main__':main()
