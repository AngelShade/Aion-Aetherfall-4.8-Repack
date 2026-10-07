"""Build a uniquely named runtime agent that preserves all existing bot settings."""
from pathlib import Path
import argparse,subprocess,zipfile
from stage_companion_update import DEV_ROOT,java_tool
ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--revision',type=int,default=2);args=parser.parse_args()
assert args.revision>=2
name='PlayerBotEquipmentUpdateAgent'+str(args.revision)
source=java_tool('PlayerBotCompanionUpdateAgent.java').read_text()
source=source.replace('PlayerBotCompanionUpdateAgent',name)
source=source.replace('"dailyBudget"))','"dailyBudget","gearMode","gearProfile","gearQuality","gearLevel","gearThreshold","gearWeapon","gearVendors","gearRolls"))')
source='\n'.join(line for line in source.splitlines() if 'Equipment spending must remain opt-in during installation' not in line and 'for(var session:sessions){session.setAutoGear(true);session.setQuesting(true);}' not in line)+'\n'
source=source.replace('"OK: requested auto equipment and questing enabled on "+sessions.size()+" existing companions; enchanting/extraction remain off."','"OK: existing gear, questing, enchanting and extraction preferences preserved on "+sessions.size()+" companions; new generation/vendor policies default to earned gear with purchases off."')
signature=' public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {'
assert signature in source
source=source.replace(signature,''' public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
  try{apply(argument,instrumentation);}catch(Throwable error){
   String[] parts=argument.split("\\\\|",-1);StringWriter buffer=new StringWriter();error.printStackTrace(new PrintWriter(buffer));
   if(parts.length==8 || parts.length==6)Files.writeString(Path.of(parts[parts.length-1]),"FAILED: "+buffer,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
   throw new IllegalStateException("Equipment patch failed; diagnostic written to its runtime receipt",error);
  }
 }
 private static void apply(String argument,Instrumentation instrumentation)throws Exception {''')
source=source.replace('import java.lang.instrument.*;','import java.lang.instrument.*;\nimport java.io.StringWriter;\nimport java.io.PrintWriter;')
needle='  String[] args=argument.split("\\\\|",-1);'
assert needle in source
source=source.replace(needle,needle+'''
  if(args.length==6 && args[0].equals("prepare")) {
   Path rollback=Path.of(args[1]),installed=Path.of(args[3]);verified(rollback,args[2]);verified(installed,args[4]);
   List<String> lines=new ArrayList<>();
   synchronized(PlayerBotService.getInstance()) {
    var command=ChatProcessor.getInstance().getCommandList().stream().filter(c->c.getAliasWithPrefix().equals(".bot")).findFirst().orElseThrow();
    Set<String> names=new LinkedHashSet<>();
    for(Path path:List.of(installed,rollback))try(var archive=new ZipFile(path.toFile())){for(var entry:Collections.list(archive.entries()))if(entry.getName().endsWith(".class"))names.add(entry.getName().substring(0,entry.getName().length()-6).replace('/','.'));}
    for(String name:names) {
     Class<?> type=name.equals("playercommands.Bot") ? command.getClass() : Class.forName(name,false,PlayerBotService.class.getClassLoader());
     if(!instrumentation.isModifiableClass(type))throw new IllegalStateException("Existing override class cannot be updated: "+name);
     lines.add("PRELOADED: "+name);
    }
   }
   lines.add("OK: every existing override class loaded before replacing the JAR; no character state changed.");
   Files.write(Path.of(args[5]),lines,StandardOpenOption.CREATE_NEW);return;
  }
''',1)
path=DEV_ROOT/'tooling/generated-agents'/(name+'.java');path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source)
output=DEV_ROOT/'staging/target/playerbots-equipment-check';classes=output/'tools';classes.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','--release','25','-encoding','UTF-8','-cp',str(ROOT/'target-deploy/game-server/libs/*'),'-d',str(classes),str(path)],check=True)
with zipfile.ZipFile(output/('equipment-agent-v'+str(args.revision)+'.jar'),'w') as jar:
    jar.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nAgent-Class: '+name+'\nCan-Redefine-Classes: true\nCan-Retransform-Classes: true\n\n')
    for item in classes.glob(name+'*.class'):jar.write(item,item.name)
print('OK: equipment agent built; no installed files or live state changed')
