"""Bind the checked statue client payload to the new Zirius server package."""
import argparse,hashlib,json,re,shutil,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
NPC='game-server/data/static_data/npcs/npc_templates.xml'
QUEST_SOURCES=('game-server/data/handlers/quest/theobomos/_3057CursedZirius.java',
 'game-server/data/handlers/ai/worlds/theobomos/CursedZiriusAI.java',
 'game-server/data/static_data/spawns/Npcs/210060000_Theobomos.xml')
def sha(data):return hashlib.sha256(data).hexdigest()
def prepare(client_package,previous_build,build,output):
    output=output.resolve()
    if output.exists() or ROOT in output.parents:raise ValueError('Use fresh external staging')
    m=json.loads((client_package/'manifest.json').read_text())
    normal=json.loads((build/'build-manifest.json').read_text())
    if m['feature']!='native-hotspot-server-timing' or 'statue' not in m:raise ValueError('Expected the checked statue/timing cohort')
    with zipfile.ZipFile(previous_build/'game-server/game-server.zip') as z:prior=z.read('game-server/data/static_data/npcs/npc_templates.xml')
    source=str((ROOT/NPC).resolve());current=(ROOT/NPC).read_bytes()
    if sha(prior)!=m['sourceHashes'][source]:raise ValueError('Previous statue NPC source does not match')
    pattern=rb'(<npc_template npc_id="214576"[^>]*\bai=")aggressive(")'
    expected,count=re.subn(pattern,rb'\1cursed_zirius\2',prior)
    if count!=1 or expected!=current:raise ValueError('NPC extension includes an unreviewed change')
    for name,expected in m['sourceHashes'].items():
        if name!=source and sha(Path(name).read_bytes())!=expected:raise ValueError('Other prepared source changed: '+name)
    for name in (NPC,)+QUEST_SOURCES:
        if normal['inputs'][name]!=sha((ROOT/name).read_bytes()):raise ValueError('Quest source differs from normal build')
    for entry in m['files']:
        if sha((client_package/entry['path']).read_bytes())!=entry['installed']:raise ValueError('Native payload changed')
    shutil.copytree(client_package,output)
    before=m['sourceHashes'][source];m['sourceHashes'][source]=sha(current)
    for name in QUEST_SOURCES:m['sourceHashes'][str((ROOT/name).resolve())]=normal['inputs'][name]
    m['sourceHashes'][str(Path(__file__).resolve())]=sha(Path(__file__).read_bytes())
    m['questRepair']=dict(questId=3057,serverBuild=str(build),clientPayloadChanged=False,
        sourceRebindings=[dict(source=source,before=before,after=sha(current),verifiedDifference='Only NPC214576 ai aggressive -> cursed_zirius; statue definition retained')],
        night='22:00-09:00 game time',day='09:00-22:00 game time',displayedHealthThresholdPercent=20,
        healthThresholdEvidence='Contemporary walkthrough reports 15-20 percent; exact retail AI constant was not extracted',
        cure='Native human Zirius at spring for five minutes; native reward step, no corpse/kill XP/loot',
        installed=False,gameplayVerified=False)
    m['gameplayAcceptance']='User statue/timing acceptance plus Zirius night transition, low-HP lure, arrival cure and human reward dialogue pending'
    (output/'manifest.json').write_text(json.dumps(m,indent=2)+'\n')
    print('Prepared Zirius extension with unchanged checked native client payload and exact reviewed server-source rebind; no live writes.')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('client-package','previous-build','build','output'):p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();prepare(a.client_package,a.previous_build,a.build,a.output)
