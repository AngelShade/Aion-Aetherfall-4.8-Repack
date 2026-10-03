import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
/** One-time read-only validation of the installed browse path. No client diagnostic/hook. */
public final class MarketBrowseVerifyAgent {
 public static void agentmain(String arg,Instrumentation instrumentation)throws Exception {
  Path receipt=Path.of(arg);if(Files.exists(receipt))throw new IllegalStateException("Receipt exists");
  var service=Class.forName("com.aionemu.gameserver.services.CentralMarketService");
  var method=Arrays.stream(service.getDeclaredMethods()).filter(m->m.getName().equals("catalogView")).findFirst().orElseThrow();method.setAccessible(true);
  var db=Class.forName("com.aionemu.commons.database.DatabaseFactory");List<Map<String,Object>> runs=new ArrayList<>();
  try(var connection=(Connection)db.getMethod("getConnection").invoke(null)) {
   connection.setReadOnly(true);connection.setAutoCommit(false);
   for(var args:List.of(Map.of("filter","changed","sort","change"),Map.of("category","All Items","sort","name"),Map.of("category","Weapons","sub","Aether Keys","minLevel","50","maxLevel","65"),Map.of("minPrice","1000000","maxPrice","10000000","sort","price"),Map.of("filter","stock","sort","traded"))) {
    for(int n=0;n<3;n++) {Map<String,Object> result=new LinkedHashMap<>();long start=System.nanoTime();method.invoke(null,connection,null,args,List.of(),result);double ms=(System.nanoTime()-start)/1e6;
     var tree=(List<?>)result.get("categoryTree");if(tree.size()<10)throw new IllegalStateException("Missing categories");if(((List<?>)result.get("catalog")).size()>24)throw new IllegalStateException("Oversized page");
     runs.add(Map.of("query",args,"iteration",n,"milliseconds",ms,"total",result.get("total"),"categories",tree));
    }
   }
   connection.rollback();
  }
  Class.forName("com.aionemu.gameserver.configs.Config").getDeclaredMethods();
  try {Class.forName("com.aionemu.gameserver.services.AfkKeepAliveService");throw new IllegalStateException("AFK restored");}catch(ClassNotFoundException expected){}
  var json=Class.forName("com.alibaba.fastjson2.JSON");Files.writeString(receipt,(String)json.getMethod("toJSONString",Object.class).invoke(null,Map.of("scope","read-only live catalogue; no player operations","runs",runs)),StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
