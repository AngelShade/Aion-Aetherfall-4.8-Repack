"""Stage the native Protectors Hall statue with the prepared server timing patch."""
import argparse,copy,hashlib,json,math,struct,sys,xml.etree.ElementTree as ET
from pathlib import Path

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
sys.path[:0]=[str(ROOT/'client-mods/expanded-warehouse'),str(ROOT/'client-mods/bundle-opening')]
from codec import read_pak,binary_xml,encode_binary_xml
from prepare_client import rebuild,parts

SPEC=json.loads((HERE/'statue.json').read_text())
SERVER_FILES=('npcs/npc_templates.xml','spawns/Npcs/110010000_Sanctum.xml','npc_teleporter.xml','teleport_location.xml','hotspot_template.xml')
ARCHIVES=('Data/NPCs/NPCs.pak','Data/FlightPath/FlightPath.pak','Data/World/World.pak','Data/Strings/Strings.pak','L10N/enu/Data/data.pak')

def sha(b):return hashlib.sha256(b).hexdigest()
def semantic(e):return e.tag,tuple(e.attrib.items()),e.text,tuple(semantic(c) for c in e)
def require(condition,message):
    if not condition:raise ValueError(message)
def row(root,key,value):
    hits=[n for n in root if n.findtext(key)==str(value)]
    require(len(hits)==1,'Expected one native '+key+' '+str(value));return hits[0]
def settext(e,key,value):
    child=e.find(key)
    if child is None:child=ET.SubElement(e,key)
    child.text=str(value)
def landing():
    s=SPEC['spawn'];a=math.radians(s['heading']*3)
    return round(s['x']+2*math.cos(a),6),round(s['y']+2*math.sin(a),6),s['z']

def validate_server():
    d=ROOT/'game-server/data/static_data';trees={p:ET.parse(d/p).getroot() for p in SERVER_FILES}
    npc=trees[SERVER_FILES[0]].find("npc_template[@npc_id='990180']")
    require(npc is not None and npc.get('name_id')==str(SPEC['npcNameId']) and npc.get('ai')=='general','Missing native statue template')
    require(npc.find('talk_info').get('func_dialogs')=='44','Statue must keep native teleport interaction')
    spawns=trees[SERVER_FILES[1]].findall("spawn_map/spawn[@npc_id='990180']/spot")
    require(len(spawns)==1,'Expected one statue spawn');s=SPEC['spawn']
    require(all(float(spawns[0].get(k))==s[k] for k in ('x','y','z')) and int(spawns[0].get('h'))==s['heading'],'Statue coordinates changed')
    dest=trees[SERVER_FILES[3]].find("teleloc_template[@loc_id='9001']")
    require(dest is not None and tuple(float(dest.get(k)) for k in ('posX','posY','posZ'))==landing(),'Landing differs from server')
    hot=trees[SERVER_FILES[4]].find("hotspot_location[@id='86']")
    require(hot is not None and tuple(float(hot.get(k)) for k in ('x','y','z'))==landing() and int(hot.get('price'))==SPEC['hotspotBasePrice'],'Hotspot differs from server')
    for connection in SPEC['connections']:
        existing=trees[SERVER_FILES[2]].find("teleporter_template[@teleportId='%s']/locations/telelocation[@loc_id='9001']"%connection['airlineId'])
        outgoing=trees[SERVER_FILES[2]].find("teleporter_template[@npc_ids='990180']/locations/telelocation[@loc_id='%s']"%connection['airportId'])
        require(existing is not None and outgoing is not None and all(int(e.get('price'))==connection['fee'] and int(e.get('pricePvp'))==connection['fee'] and e.get('type')=='REGULAR' for e in (existing,outgoing)),'Bidirectional native fare differs')
    return {str(d/p):sha((d/p).read_bytes()) for p in SERVER_FILES}

def map_position(airports):
    # Calibrate the existing native city map against its three original stops;
    # the high floor shares X/Y with the city, without flattening world Z.
    locations=ET.parse(ROOT/'game-server/data/static_data/teleport_location.xml').getroot();points=[]
    for c in SPEC['connections']:
        a=row(airports,'id',c['airportId']);s=locations.find("teleloc_template[@loc_id='%s']"%c['airportId'])
        points.append((float(s.get('posX')),float(s.get('posY')),float(a.findtext('ui_map_pos_x')),float(a.findtext('ui_map_pos_y'))))
    x,y,_=landing();a,b,c=points
    det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);require(det!=0,'City map anchors are collinear')
    wa=((b[1]-c[1])*(x-c[0])+(c[0]-b[0])*(y-c[1]))/det
    wb=((c[1]-a[1])*(x-c[0])+(a[0]-c[0])*(y-c[1]))/det;wc=1-wa-wb
    return round(wa*a[2]+wb*b[2]+wc*c[2]),round(wa*a[3]+wb*b[3]+wc*c[3])

def npc(root):
    require(not any(n.findtext('id')==str(SPEC['npcId']) for n in root),'Custom NPC already exists')
    clone=copy.deepcopy(row(root,'id',SPEC['modelNpcId']))
    for k,v in dict(id=SPEC['npcId'],name='Aetherfall_ProtectorsHall_Statue',desc=SPEC['npcString'],airlines_name=SPEC['airline']).items():settext(clone,k,v)
    root.append(clone)

def hotspot(root):
    require(not any(n.findtext('id')==str(SPEC['hotspotId']) for n in root),'Hotspot ID collision')
    clone=copy.deepcopy(row(root,'id',79));x,y,z=landing()
    for k,v in dict(id=SPEC['hotspotId'],name=SPEC['hotspot'],desc=SPEC['destinationString'],x=x,y=y,z=z,dir=SPEC['spawn']['heading']*3).items():settext(clone,k,v)
    root.append(clone)

def airports(root):
    require(not any(n.findtext('id')==str(SPEC['airportId']) or n.findtext('name')==SPEC['airport'] for n in root),'Airport collision')
    clone=copy.deepcopy(row(root,'id',25));x,y=map_position(root)
    for k,v in dict(id=SPEC['airportId'],name=SPEC['airport'],desc=SPEC['destinationString'],ui_map_pos_x=x,ui_map_pos_y=y).items():settext(clone,k,v)
    root.append(clone)

def add_route(parent,name,fee):
    require(not any(n.findtext('airport_name')==name for n in parent),'Duplicate native route')
    e=ET.SubElement(parent,'data')
    for k,v in dict(airport_name=name,fee=fee,pvpon_fee=fee,required_quest=0,flight_path_group_id=0).items():settext(e,k,v)

def airlines(root):
    require(not any(n.findtext('id')==str(SPEC['airlineId']) or n.findtext('name')==SPEC['airline'] for n in root),'Airline collision')
    clone=copy.deepcopy(row(root,'id',102));settext(clone,'id',SPEC['airlineId']);settext(clone,'name',SPEC['airline']);settext(clone,'cur_airport_name',SPEC['airport'])
    destinations=clone.find('airline_list');destinations.clear()
    for connection in SPEC['connections']:
        add_route(destinations,connection['airport'],connection['fee'])
        add_route(row(root,'id',connection['airlineId']).find('airline_list'),SPEC['airport'],connection['fee'])
    root.append(clone)

def strings(root,is_npc):
    identity=SPEC['npcNameId'] if is_npc else SPEC['destinationNameId'];key=SPEC['npcString'] if is_npc else SPEC['destinationString'];body=SPEC['name'] if is_npc else SPEC['destinationName']
    require(not any(n.findtext('id')==str(identity) or n.findtext('name')==key for n in root),'Custom string collision')
    e=ET.SubElement(root,'string')
    for k,v in dict(id=identity,name=key,body=body).items():settext(e,k,v)

def transform(raw,fn):
    root=binary_xml(raw) if raw[:1]==b'\x80' else ET.fromstring(raw);fn(root)
    result=encode_binary_xml(root) if raw[:1]==b'\x80' else ET.tostring(root,encoding='utf-8')
    roundtrip=binary_xml(result) if raw[:1]==b'\x80' else ET.fromstring(result)
    require(semantic(root)==semantic(roundtrip),'Native XML roundtrip changed fields')
    return result

def archive(path,transforms):
    before=path.read_bytes()
    with read_pak(path) as z:
        infos={e.filename:e for e in z.infolist()};require(set(transforms).issubset(infos),'Missing native archive member')
        replacements={name:transform(z.read(name),fn) for name,fn in transforms.items()}
    after,_=rebuild(before,replacements,infos)
    old,_,_,v=parts(before);new,_,_,nv=parts(after)
    require(list(old)==list(new) and v==nv,'Archive structure changed')
    require(all(old[n][0]==new[n][0] for n in old if n not in transforms),'Unrelated compressed member changed')
    return after,dict(changedEntries=list(transforms),unchangedCompressedEntries=len(old)-len(transforms))

STRING_TRANSFORMS={'client_strings_npc.xml':lambda r:strings(r,True),'client_strings_etc.xml':lambda r:strings(r,False)}
LOCALE_TRANSFORMS={'strings/'+n:f for n,f in STRING_TRANSFORMS.items()}

def prepare(client,timing,output):
    client=client.resolve();timing=timing.resolve();output=output.resolve()
    require(not output.exists() and client not in output.parents and ROOT not in output.parents,'Use fresh external output')
    sources=validate_server();m=json.loads((timing/'manifest.json').read_text())
    require(m['feature']=='native-hotspot-server-timing' and Path(m['clientRoot'])==client,'Expected this original timing cohort')
    for e in m['files']:
        require(sha((timing/e['path']).read_bytes())==e['installed'],'Prepared timing payload changed')
        p=client/e['path'];require((sha(p.read_bytes()) if p.is_file() else None)==e['original'],'Current original changed')
    output.mkdir(parents=True)
    def stage(rel,data):
        p=output/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    for p in timing.rglob('*'):
        if p.is_file() and p.relative_to(timing)!=Path('manifest.json'):stage(p.relative_to(timing).as_posix(),p.read_bytes())
    evidence={}
    transforms={ARCHIVES[0]:{'client_npcs_npc.xml':npc},ARCHIVES[1]:{'client_airports.xml':airports,'client_airline.xml':airlines},ARCHIVES[2]:{'client_zonemap_hotspot.xml':hotspot},ARCHIVES[3]:STRING_TRANSFORMS,ARCHIVES[4]:LOCALE_TRANSFORMS}
    for rel,changes in transforms.items():
        payload,proof=archive(client/rel,changes);stage(rel,payload);evidence[rel]=proof
    # Compose the English labels into the already verified incremental recovery
    # cohort. Validate every ownership hash before changing any tracking record.
    rel=ARCHIVES[4];state=json.loads((output/'Mods/GraphicsMenu/installed.json').read_text());package=json.loads((output/'Mods/GraphicsMenu/package/manifest.json').read_text())
    require(state['files']==package['files'],'Prepared graphics tracking differs')
    entry=next(e for e in state['files'] if e['path']==rel)
    baseline=Path(state['backupRoot']).relative_to(client).as_posix()+'/'+rel
    require(sha((output/baseline).read_bytes())==entry['original'] and sha((output/'Mods/GraphicsMenu/package'/rel).read_bytes())==entry['installed'],'English graphics recovery changed')
    base_payload,proof=archive(output/baseline,LOCALE_TRANSFORMS);stage(baseline,base_payload)
    stage('Mods/GraphicsMenu/package/'+rel,(output/rel).read_bytes());entry.update(original=sha(base_payload),installed=sha((output/rel).read_bytes()));package['files']=state['files']
    stage('Mods/GraphicsMenu/installed.json',json.dumps(state,indent=2).encode());stage('Mods/GraphicsMenu/package/manifest.json',json.dumps(package,indent=2).encode())
    files=[]
    for p in sorted(output.rglob('*')):
        if not p.is_file() or p.name=='native-check.json':continue
        rel=p.relative_to(output).as_posix();prior=client/rel;old=sha(prior.read_bytes()) if prior.is_file() else None;new=sha(p.read_bytes())
        if old!=new:files.append(dict(path=rel,original=old,installed=new))
    m['files']=files;m['preserved']=[e for e in m['preserved'] if e['path']!=ARCHIVES[4]]
    m['sourceHashes'].update(sources);m['sourceHashes'].update({str(p):sha(p.read_bytes()) for p in HERE.iterdir() if p.is_file() and p.suffix in ('.py','.json')})
    m['statue']=dict(spec=SPEC,landing=landing(),archives=list(ARCHIVES),archiveEvidence=evidence,englishRecoveryPreserved=True,serverResources=list(sources))
    m['gameplayAcceptance']='User native Protectors Hall statue appearance, map labels/markers, outgoing/return trips, landing and configured timing pending'
    stage('manifest.json',(json.dumps(m,indent=2)+'\n').encode())
    print('Prepared combined original package:',len(files),'affected client/recovery files; named statue/model, map hotspot and bidirectional native routes; no live writes.')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client',type=Path,required=True);p.add_argument('--timing',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();prepare(a.client,a.timing,a.output)
