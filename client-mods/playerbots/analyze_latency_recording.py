"""Summarize bounded read-only JFR evidence; samples are not exact method durations."""
import collections,json
from pathlib import Path
root=Path(__file__).resolve().parents[2]/'target/playerbots-latency-diagnosis'
events=json.loads((root/'profile-events.json').read_text())['recording']['events']
def seconds(duration):return float(duration.removeprefix('PT').removesuffix('S'))
def frames(values):return [f['method']['type']['name']+'.'+f['method']['name'] for f in (values.get('stackTrace') or {}).get('frames',[])]
counts=collections.Counter();threads=collections.Counter();bot=collections.Counter();tops=collections.Counter();service=collections.Counter();io=[];locks=[];pauses=[];cpu=[]
for event in events:
 kind=event['type'];v=event['values'];counts[kind]+=1;stack=frames(v)
 if kind in {'jdk.ExecutionSample','jdk.NativeMethodSample'}:
  threads[(v.get('sampledThread') or {}).get('javaName','unknown')]+=1
  if stack:tops[stack[0]]+=1
  related=[f for f in stack if '/services/playerbot/' in f]
  if related:bot[related[0]]+=1
  service_stack=tuple(f for f in stack if 'PlayerBotService' in f)
  if service_stack:service[service_stack]+=1
 elif kind in {'jdk.SocketRead','jdk.SocketWrite'}:
  io.append(dict(durationMs=seconds(v['duration'])*1000,thread=(v.get('eventThread') or {}).get('javaName'),port=v.get('port'),caller=next((f for f in stack if 'com/aionemu/' in f),None)))
 elif kind=='jdk.JavaMonitorEnter':
  locks.append(dict(durationMs=seconds(v['duration'])*1000,monitor=v['monitorClass']['name'],thread=(v.get('eventThread') or {}).get('javaName'),stack=stack[:8]))
 elif kind=='jdk.GCPhasePause':pauses.append(seconds(v['duration'])*1000)
 elif kind=='jdk.CPULoad':cpu.append(v)
report=dict(window='2026-10-04 23:57:24 through 23:58:09 Europe/Bucharest; 45 seconds',eventCounts=dict(counts),
 sampledThreads=threads.most_common(12),sampledTopFrames=tops.most_common(15),sampledInnermostBotFrames=bot.most_common(15),sampledServiceStacks=service.most_common(8),
 longestSocketEvents=sorted(io,key=lambda r:r['durationMs'],reverse=True)[:8],monitorContention=locks,
 gcPauseCount=len(pauses),maxGcPauseMs=max(pauses,default=0),totalGcPauseMs=sum(pauses),
 meanJvmCpu=sum(v['jvmUser']+v['jvmSystem'] for v in cpu)/max(1,len(cpu)),meanMachineCpu=sum(v['machineTotal'] for v in cpu)/max(1,len(cpu)),
 limits='Later bounded sample, not a recording of the 23:44:40 incident. Sample counts do not establish exact method durations or root cause.')
(root/'profile-summary.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
