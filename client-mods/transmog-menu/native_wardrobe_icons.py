"""Reference original item DDS sprites; do not extract or distribute artwork."""
from pathlib import Path
import json
import sys

def build(client):
    sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'expanded-warehouse'))
    from codec import read_pak, binary_xml
    mapping={}
    with read_pak(client/'Data/Items/Items.pak') as archive:
        assets={Path(name).stem.lower():name for name in archive.namelist() if name.lower().endswith('.dds')}
        for name in archive.namelist():
            if not name.lower().startswith('client_items_') or not name.lower().endswith('.xml'):continue
            for item in binary_xml(archive.read(name)):
                ident=item.findtext('id','')
                if not ident.isdigit() or int(ident) in mapping:continue
                # Match prepare_index's first original DDS reference and first
                # item definition, including templates with alternate icons.
                icon=next(((node.text or '').lower().removesuffix('.dds') for node in item.iter()
                           if (node.text or '').lower().removesuffix('.dds') in assets),None)
                if icon:
                    # The bridge index uses the 64px variant when present.
                    # Loading the 40px base with a 64px source rectangle leaves
                    # the equipment image small in the top-left of its slot.
                    selected=assets.get(icon+'_64',assets[icon])
                    mapping[int(ident)]='Data/Items/'+Path(selected).stem
    if len(mapping)<40000:raise ValueError('Incomplete original client icon catalogue')
    return ('-- Original client DDS paths, matching the bridge index 40/64px variants.\nWardrobeIcons = {\n'+
        ''.join(f'[{ident}]={json.dumps(path)},\n' for ident,path in sorted(mapping.items()))+'}\n').encode('utf-8')
