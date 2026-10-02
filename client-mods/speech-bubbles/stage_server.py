"""Merge only the compiled speech-bubble classes into the current deployment JAR."""
import copy,hashlib,json,shutil,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
runtime=ROOT/'target-deploy/game-server';output=ROOT/'output/speech-bubbles/server-package';output.mkdir(parents=True,exist_ok=True)
classes=ROOT/'output/speech-bubbles/server-classes'
replacements={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class') if p.relative_to(classes).as_posix().startswith('com/')}
expected=('model/gameobjects/player/PlayerSettings','dao/PlayerSettingsDAO','network/aion/AionConnection','network/aion/serverpackets/SM_MESSAGE')
assert replacements and all(any(n=='com/aionemu/gameserver/'+e+'.class' or n.startswith('com/aionemu/gameserver/'+e+'$') for e in expected) for n in replacements)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
jar=runtime/'libs/game-server-4.8-SNAPSHOT.jar';before=sha(jar);target=output/'libs'/jar.name;target.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(jar) as old,zipfile.ZipFile(target,'w') as new:
 for entry in old.infolist():new.writestr(copy.copy(entry),replacements.get(entry.filename,old.read(entry)))
 for n,b in replacements.items():
  if n not in old.namelist():new.writestr(n,b,compress_type=zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(jar) as old,zipfile.ZipFile(target) as new:
 for n in old.namelist():
  if n not in replacements:assert old.read(n)==new.read(n),n
 for n,b in replacements.items():assert new.read(n)==b,n
assert sha(jar)==before,'Another deployment changed the JAR during preparation'
files=[{'path':'libs/'+jar.name,'original':before,'installed':sha(target)}]
command=Path('data/handlers/playercommands/Speechbubble.java');(output/command).parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/'game-server'/command,output/command)
files.append({'path':command.as_posix(),'original':sha(runtime/command) if (runtime/command).exists() else None,'installed':sha(output/command)})
permission=Path('config/administration/commands.properties');raw=(runtime/permission).read_bytes()
assert not any(line.strip().startswith(b'speechbubble') for line in raw.splitlines()),'Command already configured'
(output/permission).parent.mkdir(parents=True,exist_ok=True);(output/permission).write_bytes(raw+b'\n# Native Chat Options speech bubble styles\nspeechbubble = 0\n')
files.append({'path':permission.as_posix(),'original':sha(runtime/permission),'installed':sha(output/permission)})
(output/'manifest.json').write_text(json.dumps({'files':files,'classes':sorted(replacements)},indent=2))
print('Staged',len(replacements),'scoped class replacements; all other JAR entries preserved.')
