"""Refresh an already staged package after recompiling only its extension."""
import argparse,json
from pathlib import Path
from build_package import sha

def main():
    p=argparse.ArgumentParser();p.add_argument('package',type=Path);a=p.parse_args();out=a.package.resolve()
    m=json.loads((out/'manifest.json').read_text());rel=m['speechReceipt']
    if not rel.startswith('SpeechBubbles-backups/') or Path(m['clientRoot']).resolve()==out:raise ValueError('Expected staged revision')
    for e in m['files']:
        if e['path']!='bin64/AionSpeechBubbles.dll' and sha((out/e['path']).read_bytes())!=e['installed']:raise ValueError('Unrelated staged file changed')
    r=json.loads((out/rel).read_text());entry=next(e for e in r['files'] if e['path']=='bin64/AionSpeechBubbles.dll')
    entry['installed']=sha((out/entry['path']).read_bytes());(out/rel).write_text(json.dumps(r,indent=2))
    for e in m['files']:
        if e['path'] in ('bin64/AionSpeechBubbles.dll',rel):e['installed']=sha((out/e['path']).read_bytes())
    (out/'manifest.json').write_text(json.dumps(m,indent=2))
    print('Staged extension and removal receipt hashes refreshed; client untouched.')
if __name__=='__main__':main()
