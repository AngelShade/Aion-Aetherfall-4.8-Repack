import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.zip.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.services.playerbot.*;

/** Observe currently loaded methods without replacing bytecode or executing AI. */
public final class PlayerBotSpacingCaptureAgent1 {
 public static void agentmain(String directory,Instrumentation instrumentation)throws Exception{
  Path output=Path.of(directory);Files.createDirectories(output);List<Class<?>> types=new ArrayList<>();
  var loader=PlayerBotService.class.getClassLoader();
  for(String name:List.of("services.playerbot.PlayerBotSession","services.playerbot.PlayerBotFormationLayout","services.playerbot.PlayerBotFormation","services.playerbot.PlayerBotCombatPosition","services.playerbot.PlayerBotSpacing","services.PlayerBotHttpService")) {
   var type=Class.forName("com.aionemu.gameserver."+name,false,loader);types.add(type);
   if(name.endsWith("PlayerBotSpacing"))types.addAll(List.of(type.getDeclaredClasses()));
  }
  Map<String,byte[]> captured=new LinkedHashMap<>();Set<Class<?>> wanted=Set.copyOf(types);
  var transformer=new ClassFileTransformer(){public byte[] transform(ClassLoader l,String name,Class<?> type,ProtectionDomain d,byte[] bytes){if(wanted.contains(type))captured.put(name,bytes.clone());return null;}};
  instrumentation.addTransformer(transformer,true);
  try{instrumentation.retransformClasses(types.toArray(Class<?>[]::new));}finally{instrumentation.removeTransformer(transformer);}
  if(captured.size()!=types.size())throw new AssertionError("Missing loaded definition");
  try(var archive=new ZipOutputStream(Files.newOutputStream(output.resolve("effective-loaded.jar"),StandardOpenOption.CREATE_NEW))){for(var e:captured.entrySet()){archive.putNextEntry(new ZipEntry(e.getKey()+".class"));archive.write(e.getValue());archive.closeEntry();}}
  Files.writeString(output.resolve("capture-check.txt"),"OK: "+captured.size()+" loaded definitions captured; transformer returned null, no replacement bytecode, tick, movement, cast, character or DB writes.\n",StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}
