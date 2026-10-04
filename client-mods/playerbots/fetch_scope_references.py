"""Cache a bounded set of unchanged sources for the full subsystem inventory."""
import hashlib,json,urllib.request
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

ROOT=Path(__file__).resolve().parents[2]
PIN='037c01418b5d01506917a3db9b44fd56ac5f965c'
PATHS=[
 'src/Bot/Factory/AiFactory.cpp','src/Bot/RandomPlayerbotMgr.h',
 'src/Bot/PlayerbotMgr.h','src/Ai/Base/StrategyContext.h',
 'src/Ai/Base/Actions/AcceptInvitationAction.cpp','src/Ai/Base/Actions/TradeStatusAction.cpp',
 'src/Ai/Base/Actions/TradeAction.cpp','src/Ai/Base/Actions/ChangeStrategyAction.cpp',
 'src/Ai/Base/Strategy/LootNonCombatStrategy.cpp','src/Ai/Base/Strategy/ThreatStrategy.cpp',
 'src/Ai/Base/Strategy/NonCombatStrategy.cpp','src/Ai/Base/Strategy/DeadStrategy.cpp',
 'src/Ai/World/Rpg/Action/RpgSubActions.cpp','src/Mgr/Travel/TravelMgr.h',
 'src/Mgr/Guild/PlayerbotGuildMgr.h','src/Ai/Base/Actions/TrainerAction.cpp',
 'src/Bot/Engine/Engine.h',
]

if __name__=='__main__':
 tree=json.loads((ROOT/'target/playerbots-upstream-tree.json').read_text(encoding='utf-8'))
 assert tree['sha']==PIN and tree['truncated'] is False
 blobs={x['path']:x['sha'] for x in tree['tree'] if x['type']=='blob'}
 assert all(p in blobs for p in PATHS),[p for p in PATHS if p not in blobs]
 output=ROOT/'third-party/playerbots/scope-references'
 output.mkdir(exist_ok=True)
 (output/'tree.json').write_text(json.dumps(tree,indent=2),encoding='utf-8')
 def fetch(path):
  request=urllib.request.Request('https://raw.githubusercontent.com/mod-playerbots/mod-playerbots/'+PIN+'/'+path,headers={'User-Agent':'Aion-Playerbots-source-review'})
  with urllib.request.urlopen(request,timeout=40) as response:content=response.read()
  assert hashlib.sha1(b'blob '+str(len(content)).encode()+b'\0'+content).hexdigest()==blobs[path],path
  target=output/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content)
  return dict(path=path,gitBlob=blobs[path],sha256=hashlib.sha256(content).hexdigest())
 with ThreadPoolExecutor(max_workers=4) as executor:refs=list(executor.map(fetch,PATHS))
 refs.append(dict(path='tree.json',sha256=hashlib.sha256((output/'tree.json').read_bytes()).hexdigest()))
 (output/'manifest.json').write_text(json.dumps(dict(pin=PIN,references=refs),indent=2),encoding='utf-8')
 print('OK: complete untruncated pinned tree and',len(PATHS),'bounded unchanged source references')
