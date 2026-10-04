import java.lang.instrument.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.ai.AIEngine;
import com.aionemu.gameserver.utils.chathandlers.ChatProcessor;

/** Schema-preserving core update; no character, connection, account or preference changes. */
public class KromedeRuntimeUpdateAgent23 {
 private static final List<JarFile> appended=new ArrayList<>();
 private static void verify(Path p,String hash)throws Exception{if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))).equals(hash))throw new IllegalStateException("Hash changed: "+p);}
 private static Class<?> resolve(String path)throws Exception{
  String name=path.replace('/','.').replaceAll("\\.class$","");
  if(name.equals("admincommands.Reload"))return ChatProcessor.getInstance().getCommandList().stream().filter(c->c.getAliasWithPrefix().equals("//reload")).findFirst().orElseThrow().getClass();
  if(name.equals("instance.FireTempleInstance")){var field=com.aionemu.gameserver.instance.InstanceEngine.class.getDeclaredField("instanceHandlers");field.setAccessible(true);return (Class<?>)((Map<?,?>)field.get(com.aionemu.gameserver.instance.InstanceEngine.getInstance())).get(320100000);}
  return Class.forName(name,false,AIEngine.class.getClassLoader());
 }
 public static void agentmain(String argument,Instrumentation inst)throws Exception{
  String[] a=argument.split("\\|",-1);Path report=Path.of(a[a.length-1]);
  try{
   if(a[0].equals("prepare")){
    verify(Path.of(a[1]),a[2]);verify(Path.of(a[3]),a[4]);List<String> lines=new ArrayList<>();
    for(Path p:List.of(Path.of(a[1]),Path.of(a[3])))try(var jar=new ZipFile(p.toFile())){for(var e:Collections.list(jar.entries()))if(e.getName().endsWith(".class")){var type=resolve(e.getName());if(!inst.isModifiableClass(type))throw new IllegalStateException("Unmodifiable "+type);lines.add("PRELOADED: "+type.getName());}}
    Files.write(report,lines,StandardOpenOption.CREATE_NEW);return;
   }
   boolean rollback=a[0].equals("rollback");verify(Path.of(a[1]),a[2]);
   if(!rollback){verify(Path.of(a[3]),a[4]);verify(Path.of(a[5]),a[6]);var jar=new JarFile(a[5]);appended.add(jar);inst.appendToSystemClassLoaderSearch(jar);}
   List<ClassDefinition> defs=new ArrayList<>(),old=new ArrayList<>();
   try(var zip=new ZipFile(a[1]);var previous=rollback?null:new ZipFile(a[3])){for(var e:Collections.list(zip.entries()))if(e.getName().endsWith(".class")){var type=resolve(e.getName());defs.add(new ClassDefinition(type,zip.getInputStream(e).readAllBytes()));if(previous!=null)old.add(new ClassDefinition(type,previous.getInputStream(previous.getEntry(e.getName())).readAllBytes()));}}
   inst.redefineClasses(defs.toArray(ClassDefinition[]::new));
   try{Files.writeString(report,"OK: "+defs.size()+" reviewed effective classes atomically redefined; singleton, scripts, native characters and connections retained.\n",StandardOpenOption.CREATE_NEW);}
   catch(Throwable error){if(!rollback)inst.redefineClasses(old.toArray(ClassDefinition[]::new));throw error;}
  }catch(Throwable e){var text=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(text));Files.writeString(report,"FAIL: "+text,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);throw e;}
 }
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}
