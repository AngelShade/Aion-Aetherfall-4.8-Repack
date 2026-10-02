"""Read-only extraction of the native menu layout and candidate icon artwork."""
import sys
from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image,ImageDraw
sys.path.insert(0,str(Path(__file__).resolve().parent.parent/'expanded-warehouse'))
from codec import read_pak,binary_xml
root=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
out=Path('output/market-shortcut-inspection');out.mkdir(parents=True,exist_ok=True)
for rel,name in [('Data/ui/game/game.pak','system_menu_dialog.xml'),('L10N/enu/data/data.pak','ui/game/system_menu_dialog.xml')]:
    z=read_pak(root/rel);tree=binary_xml(z.read(name));ET.indent(tree)
    (out/('enu-' if rel.startswith('L10N') else '')/'system_menu_dialog.xml').parent.mkdir(parents=True,exist_ok=True)
    (out/('enu-' if rel.startswith('L10N') else '')/'system_menu_dialog.xml').write_bytes(ET.tostring(tree))
    print(rel);print(ET.tostring(tree).decode()[:22000])
z=read_pak(root/'Data/ui/ui.pak');tree=binary_xml(z.read('UI_Preload.xml'))
skins=[s for s in tree.iter('Skin') if any(k in s.get('name','').lower() for k in ('start_menu','npc_func','auction','broker','scale','balance')) and s.get('src_image')]
tex=read_pak(root/'Textures/ui/ui.pak');images={};canvas=Image.new('RGBA',(900,((len(skins)+4)//5)*95),(30,40,50,255));d=ImageDraw.Draw(canvas)
import io
for i,skin in enumerate(skins):
    name=skin.get('texture').split('/')[-1]+'.dds'
    if name not in images:images[name]=Image.open(io.BytesIO(tex.read(name))).convert('RGBA')
    x,y,w,h=map(int,skin.get('src_image').split(','));icon=images[name].crop((x,y,x+w,y+h));icon.thumbnail((58,58))
    px=(i%5)*180;py=(i//5)*95;canvas.alpha_composite(icon,(px+55,py));d.text((px+3,py+62),skin.get('name').replace('v5_',''),fill='white')
canvas.save(out/'native-icons.png');print('Candidate skins:',len(skins))
