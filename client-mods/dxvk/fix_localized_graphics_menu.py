"""Add the English override to an existing prepared graphics-menu package."""
import json,sys
from pathlib import Path
from build_graphics_menu import HERE,sha,ui
client=Path(sys.argv[1]);package=HERE/'build/graphics-menu'
manifest=json.loads((package/'manifest.json').read_text())
rel='L10N/enu/Data/data.pak'
if any(e['path']==rel for e in manifest['files']):raise ValueError('Localized layout already prepared')
b,preserved=ui(client/rel,'ui/game/global_option_dialog.xml')
target=package/rel;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(b)
entry={'path':rel,'original':sha((client/rel).read_bytes()),'installed':sha(b)}
manifest['files'].append(entry);manifest['unchangedLocalizedEntries']=preserved
(package/'manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps({'file':entry,'unchangedLocalizedEntries':preserved},indent=2))
