import java.net.*;
import java.nio.file.*;
import java.util.*;
public final class VerifyCleanup {
 public static void main(String[] args)throws Exception {
  List<URL> urls=new ArrayList<>();urls.add(Path.of(args[0]).toUri().toURL());
  try(var files=Files.list(Path.of(args[1]))) {for(var file:files.filter(p->p.toString().endsWith(".jar")&&!p.getFileName().toString().equals("game-server-4.8-SNAPSHOT.jar")).toList())urls.add(file.toUri().toURL());}
  try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())) {
   var config=Class.forName("com.aionemu.gameserver.configs.Config",true,loader);
   var classes=(List<?>)config.getMethod("getClasses").invoke(null);
   if(classes.size()!=37||classes.stream().anyMatch(c->((Class<?>)c).getName().contains("Afk")))throw new IllegalStateException("Config registry mismatch");
   for(String name:List.of("com.aionemu.gameserver.services.AfkKeepAliveService","com.aionemu.gameserver.services.AfkKeepAliveService$SingletonHolder","com.aionemu.gameserver.configs.main.AfkKeepAliveConfig")) {
    try{Class.forName(name,false,loader);throw new IllegalStateException("AFK class remains: "+name);}catch(ClassNotFoundException expected){}
   }
   Class.forName("com.aionemu.gameserver.GameServer",false,loader).getDeclaredMethods();Class.forName("com.aionemu.gameserver.services.CentralMarketService",false,loader).getDeclaredMethods();
   System.out.println("PASS: actual staged JAR loads all edited classes; 37 config classes; all AFK classes absent");
  }
 }
}
