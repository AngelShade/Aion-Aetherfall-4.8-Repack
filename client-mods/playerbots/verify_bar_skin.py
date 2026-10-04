"""Check the actual base/English skin and DDS divider behind command rows."""
import argparse
import io
from pathlib import Path
import sys
import xml.etree.ElementTree as E
from PIL import Image, ImageStat
from prepare_bar import HERE, read_pak
sys.path.insert(0, str(HERE.parent/'expanded-warehouse'))
from codec import binary_xml


def main():
    p=argparse.ArgumentParser();p.add_argument('--client',type=Path,required=True);a=p.parse_args()
    dialog=E.parse(HERE/'PlayerBotBar.xml').find('Dialog')
    assert dialog.get('preset')=='v5_dialog'
    texture=read_pak(a.client/'Textures/ui/ui.pak')
    image=Image.open(io.BytesIO(texture.read('v5_common.dds'))).convert('RGB')
    controls={n.get('name'):tuple(map(float,n.get('frame').split(','))) for n in dialog.findall('Widget')}
    for rel,name in [('Data/ui/ui.pak','UI_Preload.xml'),('L10N/enu/Data/data.pak','ui/ui_preload.xml')]:
        tree=binary_xml(read_pak(a.client/rel).read(name))
        skins={n.get('name'):n for n in tree.iter('Skin')}
        def edge_contrast(skin):
            x,y,w,h=map(int,skin.get('src_middle_bottom').split(','))
            rows=[ImageStat.Stat(image.crop((x,y+i,x+w,y+i+1))).mean for i in range(2)]
            return max(abs(c-d) for c,d in zip(*rows))
        old=skins['v5_dialog2'];current=skins[dialog.get('preset')]
        assert edge_contrast(old)>30,'Reproduce the old visible footer separator'
        # Expanded frame height 128, native content starts at title + border.
        divider=128-int(old.get('size_middle_bottom').split(',')[1])
        for n in ('Guard','Passive','Manage'):
            x,y,w,h=controls['PlayerBotBar'+n]
            assert y+25<=divider<=y+25+h,'Reproduce separator crossing secondary commands'
        assert edge_contrast(current)<4,'No footer divider behind any command row'
        assert current.get('border')=='3','Keep native content/title geometry'
        preset=next(n for n in tree.iter('Preset') if n.get('name')==dialog.get('preset'))
        assert any(n.get('name')=='v5_dialog' and n.get('state')=='frame' for n in preset)
    print('OK: base and active English native skin; old footer-line overlap reproduced; divider-free frame and original title margins verified.')


if __name__=='__main__':main()
