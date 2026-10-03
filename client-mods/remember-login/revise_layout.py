"""Stage only the requested account-button placement and shorter checkbox label."""
import argparse,copy,io,json,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
from prepare import read_pak,binary_xml,encode_binary_xml,encode_pak,sha,read,LABEL,LABEL_TEXT,PRESERVED,place_account_buttons
def revise(path,rel):
    z=read_pak(path);entry='UI_Login.xml' if rel=='Data/ui/ui.pak' else 'ui/ui_login.xml';tree=binary_xml(z.read(entry));dialog=tree.find(".//Dialog[@name='login_dialog']")
    if dialog is None or dialog.find(".//*[@name='remember_login']") is None:raise ValueError('Remembered login is not installed')
    before=copy.deepcopy(tree);place_account_buttons(dialog)
    # Check only the two requested widgets change.
    restored=copy.deepcopy(tree)
    for name in ['login_new','login_password']:
        old=before.find(".//*[@name='"+name+"']");new=restored.find(".//*[@name='"+name+"']");new.attrib.clear();new.attrib.update(old.attrib)
    assert ET.tostring(before)==ET.tostring(restored)
    changes={entry:encode_binary_xml(tree)}
    if rel=='L10N/enu/Data/data.pak':
        key='strings/client_strings_ui.xml';strings=binary_xml(z.read(key));old=copy.deepcopy(strings);matches=[e for e in strings if e.findtext('name')==LABEL]
        if len(matches)!=1 or matches[0].findtext('body')!='Remember account and password':raise ValueError('Checkbox text differs from installed version')
        matches[0].find('body').text=LABEL_TEXT;changes[key]=encode_binary_xml(strings)
        for e in strings:
            if e.findtext('name')==LABEL:e.find('body').text='Remember account and password'
        assert ET.tostring(old)==ET.tostring(strings)
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as out:
        for e in z.infolist():out.writestr(copy.copy(e),changes.get(e.filename,z.read(e)))
    result=encode_pak(stream.getvalue());updated=read_pak_bytes(result)
    assert updated.namelist()==z.namelist() and updated.testzip() is None
    for e in z.namelist():assert updated.read(e)==changes.get(e,z.read(e)),e
    return result,len(z.namelist())
def read_pak_bytes(data):
    import tempfile
    with tempfile.TemporaryDirectory(prefix='aion-login-layout-') as work:
        path=Path(work)/'ui.pak';path.write_bytes(data);return read_pak(path)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',required=True,type=Path);p.add_argument('--output',required=True,type=Path);a=p.parse_args();root=a.client.resolve();out=a.output.resolve()
    if out.exists() or out.is_relative_to(root):raise ValueError('Use a fresh output outside the client')
    staged={};checks=0
    def stage(rel,data):
        rel=Path(rel).as_posix();dest=out/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data);staged[rel]=data
    for rel in ['Data/ui/ui.pak','L10N/enu/Data/data.pak']:
        data,n=revise(root/rel,rel);checks+=n;stage(rel,data)
    g=read(root/'DXVK/graphics-menu/installed.json');m=read(root/'DXVK/graphics-menu/package/manifest.json')
    if g['files']!=m['files']:raise ValueError('Graphics recovery metadata differs')
    e=next(e for e in g['files'] if e['path']=='L10N/enu/Data/data.pak');baseline=Path(g['backupRoot'])/e['path']
    if not baseline.resolve().is_relative_to(root/'DXVK-backups') or sha(baseline.read_bytes())!=e['original'] or sha((root/e['path']).read_bytes())!=e['installed']:raise ValueError('Graphics locale guards differ')
    data,n=revise(baseline,e['path']);checks+=n;stage(baseline.relative_to(root),data);e.update(original=sha(data),installed=sha(staged[e['path']]))
    stage('DXVK/graphics-menu/package/'+e['path'],staged[e['path']]);m['files']=g['files'];stage('DXVK/graphics-menu/installed.json',json.dumps(g,indent=2).encode());stage('DXVK/graphics-menu/package/manifest.json',json.dumps(m,indent=2).encode())
    preserved=PRESERVED+['bin64/Game.dll','bin64/AionRememberLogin.dll']
    receipt=dict(feature='remember-login',revision='layout-2',clientRoot=str(root),sourceKey=sha((root/'Pub.key').read_bytes()),files=[dict(path=rel,original=sha((root/rel).read_bytes()),installed=sha(data)) for rel,data in staged.items()],preservedFiles=[dict(path=rel,sha256=sha((root/rel).read_bytes())) for rel in preserved])
    (out/'manifest.json').write_text(json.dumps(receipt,indent=2));print('OK staged',len(staged),'layout files;',checks,'archive entries checked. Only two button layouts and one label change.')
if __name__=='__main__':main()
