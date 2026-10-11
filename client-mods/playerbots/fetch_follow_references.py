"""Add unchanged Follow/Flee command sources at the installed Playerbots pin."""
import hashlib, json, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'third-party/playerbots/scope-references'
PATHS = ['src/Ai/Base/Strategy/FleeStrategy.cpp', 'src/Ai/Base/Strategy/FleeStrategy.h',
         'src/Ai/Base/Strategy/FollowMasterStrategy.cpp', 'src/Ai/Base/Strategy/FollowMasterStrategy.h',
         'src/Ai/Base/Actions/StayActions.cpp', 'src/Ai/Base/ChatActionContext.h',
         'src/Ai/Base/Actions/ChatShortcutActions.cpp', 'src/Ai/Base/Actions/ChatShortcutActions.h',
         'src/Ai/Base/Actions/MovementActions.h', 'src/Ai/Base/Value/LastMovementValue.h',
         'src/Ai/Base/Value/PositionValue.h', 'src/Bot/Engine/WorldPacket/Event.h']

if __name__ == '__main__':
    manifest = json.loads((ROOT / 'manifest.json').read_text())
    tree = json.loads((ROOT / 'tree.json').read_text())
    assert manifest['pin'] == tree['sha'] == '037c01418b5d01506917a3db9b44fd56ac5f965c'
    assert not tree['truncated']
    blobs = {x['path']: x['sha'] for x in tree['tree'] if x['type'] == 'blob'}
    refs = {x['path']: x for x in manifest['references']}
    for path in PATHS:
        request = urllib.request.Request('https://raw.githubusercontent.com/mod-playerbots/mod-playerbots/' + manifest['pin'] + '/' + path,
                                         headers={'User-Agent': 'Aion-Playerbots-source-review'})
        with urllib.request.urlopen(request, timeout=40) as response:
            data = response.read()
        assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == blobs[path]
        target = ROOT / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        refs[path] = dict(path=path, gitBlob=blobs[path], sha256=hashlib.sha256(data).hexdigest())
        print(path)
    manifest['references'] = list(refs.values())
    (ROOT / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
