"""Validate custom skin/texture resources and preservation in both locales."""
import argparse,io,json,struct
from pathlib import Path
from PIL import Image
from build_package import read_pak,binary_xml

def main():
    p=argparse.ArgumentParser();p.add_argument('package',type=Path);a=p.parse_args();out=a.package.resolve()
    manifest=json.loads((out/'manifest.json').read_text());root=Path(manifest['clientRoot'])
    texture=read_pak(out/'Textures/ui/ui.pak');oldtexture=read_pak(root/'Textures/ui/ui.pak')
    names=['asb_ornate','asb_amber','asb_thought']
    assert set(texture.namelist())-set(oldtexture.namelist())=={n+'.dds' for n in names}
    assert all(texture.read(n)==oldtexture.read(n) for n in oldtexture.namelist()),'stock textures preserved'
    libraries=[]
    for rel,entry in [('Data/ui/ui.pak','UI_Preload.xml'),('L10N/enu/Data/data.pak','ui/ui_preload.xml')]:
        pak=read_pak(out/rel);prior=read_pak(root/rel)
        assert pak.namelist()==prior.namelist()
        assert all(pak.read(n)==prior.read(n) for n in prior.namelist() if n!=entry)
        library=binary_xml(pak.read(entry));old=binary_xml(prior.read(entry))
        for skin in old.findall('.//Skin'):
            actual=library.find(".//Skin[@name='"+skin.get('name')+"']")
            assert actual.attrib==skin.attrib,'existing skin changed: '+skin.get('name')
        for name in names:
            skin=library.find(".//Skin[@name='"+name+"']");assert skin is not None
            assert skin.get('texture')=='Textures/UI/'+name
            data=texture.read(name+'.dds');assert data[:4]==b'DDS ' and len(data)==128+512*256*4
            image=Image.open(io.BytesIO(data));assert image.size==(512,256) and image.mode=='RGBA'
            assert image.getextrema()[3][0]==0 and image.getextrema()[3][1]>240
            for key,value in skin.attrib.items():
                if not key.startswith('src_'):continue
                x,y,w,h=map(int,value.split(','));assert w>0 and h>0 and x+w<=512 and y+h<=256
                assert image.crop((x,y,x+w,y+h)).getextrema()[3][1]>0
            assert len([k for k in skin.attrib if k.startswith('src_')])==11
        libraries.append([library.find(".//Skin[@name='"+n+"']").attrib for n in names])
    assert libraries[0]==libraries[1],'base and active English use identical frames'
    assert manifest['styles']==['Classic','Wings','Crystal','Cloud','Paws']
    graphics=json.loads((out/'DXVK/graphics-menu/installed.json').read_text())
    baseline=Path(graphics['backupRoot']).relative_to(root)
    for rel in ['DXVK/graphics-menu/package/L10N/enu/Data/data.pak',(baseline/'L10N/enu/Data/data.pak').as_posix()]:
        library=binary_xml(read_pak(out/rel).read('ui/ui_preload.xml'))
        assert all(library.find(".//Skin[@name='"+n+"']") is not None for n in names)
    print('PASS: distinct eleven-part skins, valid alpha DDS tiles, matching English/base resources, graphics restore compatibility, and every unrelated stock skin/texture/archive entry preserved.')
if __name__=='__main__':main()
