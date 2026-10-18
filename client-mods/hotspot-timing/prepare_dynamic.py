"""Build one-time native support for the server's statue cast duration."""
import argparse,json,struct,sys
from pathlib import Path
from prepare import ROOT,PE,sha,SITES,FUNCTION,FUNCTION_SHA,PRESERVED,prepare_incremental,compatibility_paths
sys.path.insert(0,str(ROOT/'client-mods/transmog-menu'))
from patch_game_dll import Assembler

DISPATCH=0x381e55
DISPATCH_BYTES=bytes.fromhex('e9f691deff')
SLACK_RVA=0x190a440
SLACK_SIZE=0x1c0

def build_patch(source):
    data=bytearray(source);pe=PE(data)
    # Verify the complete native cast function, allowing only the previous
    # fixed one-second correction as an alternative supported input.
    normalized=bytearray(source)
    for rva,original,unit in SITES:
        pos=pe.offset(rva);actual=source[pos:pos+10]
        fixed=original[:6]+struct.pack('<I',1000 if unit=='milliseconds' else 1)
        if actual not in (original,fixed):raise ValueError('Unsupported native timer instruction')
        normalized[pos:pos+10]=original
    start,end=FUNCTION
    if sha(normalized[pe.offset(start):pe.offset(end)])!=FUNCTION_SHA:raise ValueError('Native cast function changed')
    dispatcher=bytes.fromhex('40534883ec204c634a204c034a18410fbe0184c08bc8745283e901743883e901741e83f901757f458b4109418b5101488d0daa1af9004883c4205be99094deff418b5101488d0d951af9004883c4205be95b97deff418b5101488d0d801af9004883c4205be9f691deff')
    if source[pe.offset(0x381df0):pe.offset(0x381df0)+len(dispatcher)]!=dispatcher:
        raise ValueError('Unsupported native action-1 dispatcher')
    section=next(s for s in pe.sections if s[0].rstrip(b'\0')==b'.aicons')
    # Use only the original bridge section's zero raw-alignment tail. Do not
    # relocate sections, imports or any graphics/cursor-owned code.
    if section[1]!=0x43b or section[2]!=0x190a000 or section[3]!=0x600 or section[-1]&0xe0000000!=0xe0000000:
        raise ValueError('Unsupported bridge alignment tail')
    raw=pe.offset(SLACK_RVA)
    if any(source[raw:raw+SLACK_SIZE]) or len(source[raw:raw+SLACK_SIZE])!=SLACK_SIZE:
        raise ValueError('Bridge tail is already owned')
    millis=SLACK_RVA;seconds=millis+4;entry=SLACK_RVA+16
    a=Assembler(entry)
    a.emit(bytes.fromhex('5052418b410585c0')) # preserve RAX/RDX; read unused action-1 D
    a.branch(b'\x0f\x89','legacy') # positive location IDs belong to an older server
    a.emit(bytes.fromhex('f7d885c0'));a.branch(b'\x0f\x8e','legacy') # reject zero/INT_MIN
    a.emit(bytes.fromhex('4189c34531d241bae803000031d241f7f2')) # r11d=ms; divide by 1000
    a.emit(bytes.fromhex('85d27402ffc0')) # seconds = ceil(ms / 1000), without overflow
    a.relative(b'\x89\x05',seconds)
    a.relative(b'\x44\x89\x1d',millis)
    a.branch(b'\xe9','finish')
    a.label('legacy');a.emit(bytes.fromhex('b8e8030000'));a.relative(b'\x89\x05',millis)
    a.emit(bytes.fromhex('b801000000'));a.relative(b'\x89\x05',seconds)
    a.label('finish');a.emit(bytes.fromhex('5a58'));a.relative(b'\xe9',FUNCTION[0])
    payload=bytearray(struct.pack('<II',1000,1)+bytes(8)+a.finish())
    hooks=[dict(site=DISPATCH,hook=entry,before=DISPATCH_BYTES.hex())]
    pos=pe.offset(DISPATCH)
    data[pos:pos+5]=b'\xe9'+struct.pack('<i',entry-DISPATCH-5)
    for rva,original,unit in SITES:
        while len(payload)%16:payload.append(0)
        hook=SLACK_RVA+len(payload);b=Assembler(hook)
        b.emit(b'\x50');b.relative(b'\x8b\x05',millis if unit=='milliseconds' else seconds)
        target=rva+10+struct.unpack_from('<i',original,2)[0]
        b.relative(b'\x89\x05',target);b.emit(b'\x58');b.relative(b'\xe9',rva+10)
        code=b.finish();payload+=code
        pos=pe.offset(rva);data[pos:pos+10]=b'\xe9'+struct.pack('<i',hook-rva-5)+b'\x90'*5
        hooks.append(dict(site=rva,hook=hook,size=len(code),unit=unit,nativeTarget=target))
    if len(payload)>SLACK_SIZE:raise ValueError('Bridge tail capacity exceeded')
    data[raw:raw+len(payload)]=payload
    # Expose the added raw-backed tail in every matching recovery image.
    table=pe.optional+pe.u16(pe.optional-4)
    header=table+pe.sections.index(section)*40
    struct.pack_into('<I',data,header+8,SLACK_RVA-section[2]+len(payload))
    allowed=set(range(raw,raw+len(payload)))|set(range(header+8,header+12))
    for h in hooks:allowed.update(range(pe.offset(h['site']),pe.offset(h['site'])+(5 if h['site']==DISPATCH else 10)))
    if len(data)!=len(source) or any(x!=y and i not in allowed for i,(x,y) in enumerate(zip(source,data))):
        raise ValueError('Unrelated native byte changed')
    return bytes(data),dict(hooks=hooks,millisecondsRva=millis,secondsRva=seconds,payloadRva=SLACK_RVA,payloadSize=len(payload))

def prepare(client,output):
    client=client.resolve();output=output.resolve()
    if output.exists() or output==client or client in output.parents or ROOT in output.parents:raise ValueError('Use fresh external staging')
    original=(client/'bin64/Game.dll').read_bytes();patched,proof=build_patch(original)
    output.mkdir(parents=True)
    combined,compat=prepare_incremental(client,output,patched)
    if combined!=patched:raise ValueError('Compatibility helper changed patch bytes')
    (output/'bin64').mkdir(exist_ok=True);(output/'bin64/Game.dll').write_bytes(combined)
    staged={p.relative_to(output).as_posix():p for p in output.rglob('*') if p.is_file()}
    if set(staged)!={'bin64/Game.dll'}|compatibility_paths(compat):raise ValueError('Unexpected compatibility cohort')
    files=[]
    for rel,path in sorted(staged.items()):
        live=client/rel;before=sha(live.read_bytes()) if live.is_file() else None;after=sha(path.read_bytes())
        if before!=after:files.append(dict(path=rel,original=before,installed=after))
    sources=[Path(__file__).resolve(),ROOT/'game-server/src/com/aionemu/gameserver/network/aion/serverpackets/SM_BIND_POINT_TELEPORT.java',ROOT/'game-server/src/com/aionemu/gameserver/services/teleport/BindPointTeleportService.java',ROOT/'game-server/src/com/aionemu/gameserver/configs/main/CustomConfig.java']
    record=dict(feature='native-hotspot-server-timing',clientRoot=str(client),originalGameSha256=sha(original),gameSha256=sha(combined),
        protocol='Action 1 unused second D carries negative cast milliseconds; positive legacy location ID uses existing 1000 ms fallback',
        nativeProof=proof,compatibility=compat,files=files,sourceHashes={str(p):sha(p.read_bytes()) for p in sources},
        preserved=[dict(path=rel,sha256=sha((client/rel).read_bytes())) for rel in PRESERVED],
        fullSuitesRun=False,newTestFiles=False,gameStarted=False,gameplayAcceptance='User statue progress bar and configured teleport delay pending')
    (output/'manifest.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print('Prepared one-time server-driven hotspot bar; three guarded hooks; matching graphics/cursor recovery;',len(files),'affected files.')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();prepare(a.client,a.output)
