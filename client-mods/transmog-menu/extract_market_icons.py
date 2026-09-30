"""Extract original client artwork once per DDS; map item IDs without duplicating PNGs."""
import argparse, csv, io, re, sys, xml.etree.ElementTree as ET, zipfile
from pathlib import Path
from PIL import Image
from normalize_market_icons import normalize

parser = argparse.ArgumentParser()
parser.add_argument('--archive', type=Path, required=True)
parser.add_argument('--templates', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--codec-directory',type=Path,required=True)
args = parser.parse_args()
sys.path.insert(0,str(args.codec_directory))
from fire_temple_probe import binary_xml
items = {e.get('id'): e.attrib for e in ET.parse(args.templates).getroot()}
output = args.output
(output / 'icons').mkdir(parents=True, exist_ok=True)
pattern = re.compile(rb'(?:[0-9]\x00){9}\x00\x00')
with zipfile.ZipFile(args.archive) as z:
    icons = {Path(n).stem.lower(): n for n in z.namelist() if n.lower().endswith('.dds')}
    records = {}
    for filename in z.namelist():
        if not filename.startswith('client_items_') or not filename.endswith('.xml'): continue
        root=binary_xml(z.read(filename))
        for record in root:
            item=record.findtext('id')
            if item not in items or item in records: continue
            words=[node.text or '' for node in record.iter()]
            icon = next((w.lower().removesuffix('.dds') for w in words if w.lower().removesuffix('.dds') in icons), None)
            if icon: records[item] = icon
    extracted = set()
    rows = []
    for item, icon in sorted(records.items()):
        original = icons.get(icon+'_64', icons[icon])
        name = Path(original).stem.lower()+'.png'
        if name not in extracted:
            with Image.open(io.BytesIO(z.read(original))) as image:
                normalize(image).resize((64,64), Image.Resampling.LANCZOS).save(output/'icons'/name, optimize=True)
            extracted.add(name)
        rows.append((item,name,'exact',original))
    with (output/'icon_sources.tsv').open('w',encoding='utf-8',newline='') as f:
        w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(('item_id','file','match','client_icon'));w.writerows(rows)
    missing=[(id,t['name'],t.get('item_group','')) for id,t in items.items() if id not in records]
    report=args.output.parents[2]/'target/central-market-validation/missing-client-items.tsv'
    report.parent.mkdir(parents=True,exist_ok=True)
    with report.open('w',encoding='utf-8',newline='') as f: csv.writer(f,delimiter='\t').writerows(missing)
    print(f'{len(records)} verified client item icons; {len(extracted)} distinct PNGs; {len(missing)} server templates absent from the client icon mapping.')
