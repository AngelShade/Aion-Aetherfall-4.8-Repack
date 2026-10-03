import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
/** One-time read-only installation check. No transforms, player operations or retained timers. */
public final class MarketQuantityVerifyAgent {
 public static void agentmain(String arg,Instrumentation instrumentation)throws Exception {
  Path out=Path.of(arg);if(Files.exists(out))throw new IllegalStateException("Receipt exists");
  Class<?> service=Class.forName("com.aionemu.gameserver.services.CentralMarketService");
  var ready=service.getDeclaredField("ready");ready.setAccessible(true);if(!ready.getBoolean(null))throw new IllegalStateException("Market unavailable");
  Class<?> prefs=Class.forName("com.aionemu.gameserver.services.CentralMarketPreferences");
  var load=prefs.getDeclaredMethod("load",Connection.class,int.class);load.setAccessible(true);
  Map<String,Object> result=new LinkedHashMap<>();
  try(Connection c=(Connection)Class.forName("com.aionemu.commons.database.DatabaseFactory").getMethod("getConnection").invoke(null)) {
   c.setReadOnly(true);c.setAutoCommit(false);
   if((boolean)load.invoke(null,c,-1))throw new IllegalStateException("New accounts must default off");
   try(var s=c.prepareStatement("SELECT always_max FROM central_market_preferences WHERE account_id=?")) {
    s.setInt(1,1);try(var r=s.executeQuery()){boolean expected=r.next()&&r.getInt(1)!=0;boolean actual=(boolean)load.invoke(null,c,1);if(actual!=expected)throw new IllegalStateException("Preference mismatch");result.put("accountSetting",actual);}
   }
   try(var s=c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='central_market_preferences'")) {
    try(var r=s.executeQuery()){if(!r.next()||!r.getString(1).equalsIgnoreCase("InnoDB"))throw new IllegalStateException("Preference table unavailable");}
   }
   c.rollback();
  }
  Class.forName("com.aionemu.gameserver.configs.Config").getDeclaredMethods();
  try{Class.forName("com.aionemu.gameserver.services.AfkKeepAliveService");throw new IllegalStateException("AFK restored");}catch(ClassNotFoundException expected){}
  result.put("marketReady",true);result.put("preferenceTable","InnoDB");result.put("scope","read-only live database and class loading; no item or balance changes");
  Files.writeString(out,(String)Class.forName("com.alibaba.fastjson2.JSON").getMethod("toJSONString",Object.class).invoke(null,result),StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
