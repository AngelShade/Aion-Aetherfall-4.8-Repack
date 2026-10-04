import java.lang.instrument.*;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.*;
import com.sun.tools.attach.VirtualMachine;
import com.alibaba.fastjson2.JSON;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.PlayerBotHttpService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.utils.chathandlers.*;
import com.aionemu.gameserver.world.World;

/** Atomic, schema-preserving update with explicit effective rollback definitions. */
public class PlayerBotEquipmentUpdateAgent11 {
 private static final List<JarFile> APPENDED=new ArrayList<>();
 private static final Map<PlayerBotSession,boolean[]> LAST_SETTINGS=new WeakHashMap<>();
 private static Object LAST_HELP;
 private static void verified(Path path,String expected)throws Exception {
  if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))).equals(expected))throw new IllegalStateException("Reviewed payload changed: "+path.getFileName());
 }
 private static List<String> inspect(boolean updated)throws Exception {
  List<String> lines=new ArrayList<>();Method snapshot=PlayerBotHttpService.class.getDeclaredMethod("snapshot",Player.class);snapshot.setAccessible(true);
  for(Player owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot()) {
   synchronized(PlayerBotService.getInstance()) {
    var sessions=PlayerBotService.getInstance().companions(owner);
    var state=JSON.parseObject(JSON.toJSONString(snapshot.invoke(null,owner)));
    if(state.getJSONArray("active").size()!=sessions.size())throw new IllegalStateException("Panel lost existing companions");
    lines.add("OWNER: "+owner.getName()+" companions="+sessions.size()+" roster="+state.getJSONArray("roster").size());
    for(int i=0;i<sessions.size();i++) {
     var row=state.getJSONArray("active").getJSONObject(i);
     if(updated)for(String field:List.of("questions","announcements","enchant","salvage","partySync","reserve","dailyBudget","gearMode","gearProfile","gearQuality","gearLevel","gearThreshold","gearWeapon","gearVendors","gearRolls"))if(!row.containsKey(field))throw new IllegalStateException("Panel omitted "+field);
     lines.add("COMPANION: "+sessions.get(i).bot().getName()+" spawned="+sessions.get(i).bot().isSpawned()+" inventoryRows="+row.getJSONArray("inventory").size()+" quests="+row.getJSONArray("quests").size()+(updated ? " catchUpQuestions="+row.getJSONArray("questions").size() : ""));
    }
   }
  }
  lines.add("OK: exact production companion panel and JSON serialization");return lines;
 }
 public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
  try{apply(argument,instrumentation);}catch(Throwable error){
   String[] parts=argument.split("\\|",-1);StringWriter buffer=new StringWriter();error.printStackTrace(new PrintWriter(buffer));
   if(parts.length==8 || parts.length==6)Files.writeString(Path.of(parts[parts.length-1]),"FAILED: "+buffer,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
   throw new IllegalStateException("Equipment patch failed; diagnostic written to its runtime receipt",error);
  }
 }
 private static void apply(String argument,Instrumentation instrumentation)throws Exception {
  String[] args=argument.split("\\|",-1);
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

  if(args.length==2 && args[0].equals("diagnose")){Files.write(Path.of(args[1]),inspect(false),StandardOpenOption.CREATE_NEW);return;}
  if(args.length==4 && args[0].equals("rollback")) {
   Path baseline=Path.of(args[1]);verified(baseline,args[2]);
   synchronized(PlayerBotService.getInstance()) {
    var command=ChatProcessor.getInstance().getCommandList().stream().filter(c->c.getAliasWithPrefix().equals(".bot")).findFirst().orElseThrow();
    List<ClassDefinition> restore=new ArrayList<>();
    try(var jar=new ZipFile(baseline.toFile())){for(var entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class")) {
     String name=entry.getName().substring(0,entry.getName().length()-6).replace('/','.');
     Class<?> type=name.equals("playercommands.Bot") ? command.getClass() : Class.forName(name,true,PlayerBotService.class.getClassLoader());
     restore.add(new ClassDefinition(type,jar.getInputStream(entry).readAllBytes()));
    }}
    instrumentation.redefineClasses(restore.toArray(ClassDefinition[]::new));
    for(var entry:LAST_SETTINGS.entrySet()){entry.getKey().setAutoGear(entry.getValue()[0]);entry.getKey().setQuesting(entry.getValue()[1]);}
    if(LAST_HELP!=null){Field help=ChatCommand.class.getDeclaredField("syntaxInfo");help.setAccessible(true);help.set(command,LAST_HELP);}
    Files.writeString(Path.of(args[3]),"OK: restored explicit effective class definitions, existing preferences and companion command help.\n",StandardOpenOption.CREATE_NEW);
   }
   return;
  }
  boolean generation=args.length==8 && args[0].equals("generation");
  if(args.length!=8 || !(args[0].equals("apply") || generation))throw new IllegalArgumentException("Invalid companion patch arguments");
  Path updated=Path.of(args[1]),rollback=Path.of(args[3]),helpers=Path.of(args[5]),report=Path.of(args[7]);
  verified(updated,args[2]);verified(rollback,args[4]);verified(helpers,args[6]);
  if(!instrumentation.isRedefineClassesSupported())throw new IllegalStateException("Native runtime redefinition unavailable");
  synchronized(PlayerBotService.getInstance()) {
   List<ClassDefinition> forward=new ArrayList<>(),backward=new ArrayList<>();
   var command=ChatProcessor.getInstance().getCommandList().stream().filter(c->c.getAliasWithPrefix().equals(".bot")).findFirst().orElseThrow();
   if(command.getLevel()!=0)throw new IllegalStateException("Normal-player companion command changed");
   try(var previous=new ZipFile(rollback.toFile());var replacement=new ZipFile(updated.toFile())) {
    for(var entry:Collections.list(previous.entries()))if(entry.getName().endsWith(".class")) {
     String name=entry.getName().substring(0,entry.getName().length()-6).replace('/','.');
     Class<?> type=name.equals("playercommands.Bot") ? command.getClass() : Class.forName(name,true,PlayerBotService.class.getClassLoader());
     if(!instrumentation.isModifiableClass(type))throw new IllegalStateException("Runtime class cannot be updated: "+name);
     byte[] old=previous.getInputStream(entry).readAllBytes(),next=replacement.getInputStream(replacement.getEntry(entry.getName())).readAllBytes();
     forward.add(new ClassDefinition(type,next));backward.add(new ClassDefinition(type,old));
    }
   }
   List<PlayerBotSession> sessions=new ArrayList<>();Map<PlayerBotSession,Map<String,Object>> oldSettings=new HashMap<>();
   for(Player owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot())for(var session:PlayerBotService.getInstance().companions(owner)){sessions.add(session);oldSettings.put(session,session.snapshot());}
   long humans=World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).count();
   JarFile appended=new JarFile(helpers.toFile());APPENDED.add(appended);instrumentation.appendToSystemClassLoaderSearch(appended);
   instrumentation.redefineClasses(forward.toArray(ClassDefinition[]::new));
   Field help=ChatCommand.class.getDeclaredField("syntaxInfo");help.setAccessible(true);Object oldHelp=help.get(command);
   LAST_HELP=oldHelp;LAST_SETTINGS.clear();for(var session:sessions){var before=oldSettings.get(session);LAST_SETTINGS.put(session,new boolean[]{Boolean.TRUE.equals(before.get("gear")),Boolean.TRUE.equals(before.get("questing"))});}
   try {
    // Update only this command's help, retaining registration/access and every other command.
    if(!generation){Object rebuilt=command.getClass().getConstructor().newInstance();help.set(command,help.get(rebuilt));}
    for(var session:sessions){var before=oldSettings.get(session);var after=session.snapshot();for(String key:List.of("questing","area","supplies","loot","questCombat","partySync","enchant","salvage","reserve","dailyBudget","gearMode","gearProfile","gearQuality","gearLevel","gearThreshold","gearWeapon","gearVendors","gearRolls"))if(!Objects.equals(before.get(key),after.get(key)))throw new IllegalStateException("Preference changed during Temporary Bot installation: "+key);
     if(!Boolean.TRUE.equals(after.get("temporary"))){if(!Objects.equals(before.get("inventory"),after.get("inventory")) || !Objects.equals(before.get("level"),after.get("level")) || !Objects.equals(before.get("playerClass"),after.get("playerClass")))throw new IllegalStateException("Owned alt modified during installation");if(Boolean.TRUE.equals(after.get("gear")))throw new IllegalStateException("Owned alt automatic gear still enabled");}
    }
    List<String> lines=inspect(!generation);
    lines.add("OK: "+forward.size()+" existing class definitions updated atomically; native characters, group, singleton and session state retained.");
    lines.add(generation ? "OK: generation repair retained all existing companion settings and command help." : "OK: existing gear, questing, enchanting and extraction preferences preserved on "+sessions.size()+" companions; new generation/vendor policies default to earned gear with purchases off.");
    lines.add("Human connections before="+humans+" after="+World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).count());
    Files.write(report,lines,StandardOpenOption.CREATE_NEW);
   }catch(Throwable error) {
    instrumentation.redefineClasses(backward.toArray(ClassDefinition[]::new));help.set(command,oldHelp);
    for(var session:sessions){var before=oldSettings.get(session);session.setAutoGear(Boolean.TRUE.equals(before.get("gear")));session.setQuesting(Boolean.TRUE.equals(before.get("questing")));}
    throw error;
   }
  }
 }
 public static void main(String[] args)throws Exception {
  VirtualMachine vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}
 }
}
