import sys
from pathlib import Path
import xml.etree.ElementTree as ET
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'expanded-warehouse'))
from codec import read_pak,binary_xml
root=(Path.home() / 'Downloads/aion-4.8-na/Aion 4.8 NA')
out=Path(__file__).parent/'research'/'graphics-ui';out.mkdir(parents=True,exist_ok=True)
for p in [root/'bin32/bin32.pak',root/'Data/ui/ui.pak',*sorted((root/'Data/ui/game').glob('*.pak'))]:
    z=read_pak(p)
    names=z.namelist()
    print(p.relative_to(root),len(names))
    for name in names:
        if any(s in name.lower() for s in ('option','widget.lua','system.lua','config')):
            b=z.read(name)
            if b and b[0]==128:b=ET.tostring(binary_xml(b),encoding='utf-8')
            target=out/p.stem/name.replace('\\','/');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b)
            print(' ',name,len(b))
