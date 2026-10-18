"""Stage native hotspot cast timing to match the maintained server; no live writes."""
import argparse,hashlib,json,re,struct,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'client-mods/browser-replacement'))
sys.path.insert(0,str(ROOT/'client-mods/transmog-menu'))
from audit import PE
from graphics_compat import prepare_incremental,compatibility_paths

STOCK_SHA='5334cf2164468678e45fe1a5decf58a0fbc4fd7f22cfdcbb87d28edce8d2c11c'
FUNCTION=(0x16b050,0x16b2b5)
FUNCTION_SHA='1f414b4f01dd41842b0ec151df20e078e4a3eb30be148d8f795a16544a33744c'
SITES=((0x16b1be,bytes.fromhex('c70508b11a0110270000'),'milliseconds'),
       (0x16b1d5,bytes.fromhex('c705f9b01a010a000000'),'seconds'))
PRESERVED=('Pub.key','Addon.key','Plugin/RelicCalc/RelicCalc.pak','Plugin/RelicCalc/RelicCalc.pak.sig',
 'Data/func_pet/func_pet.pak','Data/func_pet/func_pet.pak.sig','bin32/bin32.pak','bin32/bin32.pak.sig',
 'Data/ui/game/game.pak','L10N/enu/Data/data.pak')
def sha(data):return hashlib.sha256(data).hexdigest()
def server_timing():
 path=ROOT/'game-server/src/com/aionemu/gameserver/services/teleport/BindPointTeleportService.java'
 data=path.read_bytes();delays=re.findall(rb'},\s*(\d+)\)\);',data)
 if delays!=[b'1000']:raise ValueError('Review the native cast timing before changing the server delay')
 return path,data,int(delays[0])
def patch(data,milliseconds):
 pe=PE(data);start,end=FUNCTION
 if sha(data[pe.offset(start):pe.offset(end)])!=FUNCTION_SHA:
  raise ValueError('Unsupported or already changed native hotspot cast function')
 if milliseconds<=0 or milliseconds%1000:raise ValueError('Native paired millisecond/second timer requires whole positive seconds')
 result=bytearray(data);changes=[]
 for rva,expected,unit in SITES:
  at=pe.offset(rva)
  if data[at:at+len(expected)]!=expected:raise ValueError('Unexpected native hotspot instruction')
  value=milliseconds if unit=='milliseconds' else milliseconds//1000
  struct.pack_into('<I',result,at+6,value)
  changes.append(dict(rva=rva,offset=at+6,before=expected[6:].hex(),after=struct.pack('<I',value).hex(),unit=unit))
 allowed={c['offset']+i for c in changes for i in range(4)}
 if any(a!=b and i not in allowed for i,(a,b) in enumerate(zip(data,result))):raise ValueError('Unrelated native bytes changed')
 return bytes(result),changes
def prepare(client,output):
 client=client.resolve();output=output.resolve()
 if output.exists() or output==client or client in output.parents:raise ValueError('Use fresh external staging')
 stock=(client/'bin64/game.dll.orig').read_bytes()
 if sha(stock)!=STOCK_SHA:raise ValueError('Unsupported original Aion 4.8 NA client')
 source,server,delay=server_timing();original=(client/'bin64/Game.dll').read_bytes();patched,changes=patch(original,delay)
 output.mkdir(parents=True)
 combined,compat=prepare_incremental(client,output,patched)
 if combined!=patched:raise ValueError('Compatibility helper unexpectedly changed native patch bytes')
 (output/'bin64').mkdir(exist_ok=True);(output/'bin64/Game.dll').write_bytes(combined)
 expected={'bin64/Game.dll'}|compatibility_paths(compat)
 staged={p.relative_to(output).as_posix():p for p in output.rglob('*') if p.is_file()}
 if set(staged)!=expected:raise ValueError('Unexpected compatibility payload cohort')
 files=[]
 for rel,path in sorted(staged.items()):
  live=client/rel
  before=sha(live.read_bytes()) if live.is_file() else None;after=sha(path.read_bytes())
  if before!=after:files.append(dict(path=rel,original=before,installed=after))
 record=dict(feature='native-hotspot-timing',clientRoot=str(client),castMilliseconds=delay,castSeconds=delay//1000,
  originalGameSha256=sha(original),gameSha256=sha(combined),patches=changes,compatibility=compat,files=files,
  sourceHashes={str(Path(__file__).resolve()):sha(Path(__file__).read_bytes()),str(source):sha(server)},
  preserved=[dict(path=rel,sha256=sha((client/rel).read_bytes())) for rel in PRESERVED],
  fullSuitesRun=False,newTestFiles=False,gameStarted=False,gameplayAcceptance='User statue cast bar and actual one-second teleport pending')
 (output/'manifest.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
 print('Prepared hotspot bar:',delay,'ms; two guarded native immediates; matching graphics/cursor recovery;',len(files),'affected files; client untouched.')
 return record
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();prepare(a.client,a.output)
