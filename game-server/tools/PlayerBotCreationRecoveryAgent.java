import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.services.AccountService;
import com.aionemu.gameserver.services.item.ItemFactory;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.World;

/** Bounded recovery of the reported pending character; never overwrites the foreign item. */
public class PlayerBotCreationRecoveryAgent {
 static String rows(String sql)throws Exception{var list=new ArrayList<Map<String,String>>();try(var c=DatabaseFactory.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)){while(r.next()){var row=new LinkedHashMap<String,String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getString(i));list.add(row);}}return com.alibaba.fastjson2.JSON.toJSONString(list);}
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void invoke(Class<?> c,String name,Class<?>[] types,Object... args)throws Exception{var m=c.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(null,args);}
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  Path receipt=Path.of(argument);Files.createDirectory(receipt);var lines=new ArrayList<String>();
  synchronized(PlayerBotService.getInstance()) {
   String foreign=rows("SELECT * FROM inventory WHERE item_unique_id=191241");if(foreign.equals("[]"))throw new IllegalStateException("Reported conflicting row is absent");Files.writeString(receipt.resolve("foreign-item-before.json"),foreign);
   // Force only the reported stale bit under the native allocator lock. Real newItem must skip it.
   var factory=IDFactory.getInstance();var lock=(ReentrantLock)field(factory,"lock");lock.lock();try {
    var bits=(BitSet)field(factory,"idList");var next=IDFactory.class.getDeclaredField("nextMinId");next.setAccessible(true);int before=next.getInt(factory);boolean reserved=bits.get(191241);bits.clear(191241);next.setInt(factory,191241);
    com.aionemu.gameserver.model.gameobjects.Item test=null;
    try{test=ItemFactory.newItem(162000001,1);if(test==null || test.getObjectId()==191241 || !rows("SELECT * FROM inventory WHERE item_unique_id="+test.getObjectId()).equals("[]"))throw new AssertionError("Native allocator reused persisted ID");lines.add("OK: effective ItemFactory skips deliberately stale persisted ID 191241; allocated "+test.getObjectId());}
    finally{bits.set(191241);if(test!=null)factory.releaseId(test.getObjectId());next.setInt(factory,Math.min(before,next.getInt(factory)));}
   }finally{lock.unlock();}
   if(!foreign.equals(rows("SELECT * FROM inventory WHERE item_unique_id=191241")))throw new AssertionError("Allocator changed foreign row");
   Player owner=World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot() && p.getAccount().getId()==1).findFirst().orElseThrow();
   try(var c=DatabaseFactory.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT p.name,p.account_id,b.ready,(SELECT COUNT(*) FROM inventory WHERE item_owner=p.id) AS items FROM players p JOIN playerbot_roster b ON b.player_id=p.id WHERE p.id=106628")){if(!r.next() || !r.getString(1).equals("Bardoca") || r.getInt(2)!=owner.getAccount().getId() || r.getBoolean(3) || r.getInt(4)!=0)throw new IllegalStateException("Pending Bardoca recovery preconditions changed");}
   for(String table:List.of("players","playerbot_roster","player_appearance","player_skills","player_recipes","player_life_stats")){String key=table.equals("players") ? "id" : "player_id";Files.writeString(receipt.resolve(table+"-before.json"),rows("SELECT * FROM "+table+" WHERE "+key+"=106628"));}
   Path gear=Path.of("config/playerbots/gear-character-106628.properties");if(Files.exists(gear))Files.copy(gear,receipt.resolve("gear-before.properties"));
   try(var lease=PlayerBotLease.acquire(106628)) {
    if(lease==null)throw new IllegalStateException("Pending character is already held");
    var data=AccountService.loadPlayerAccountData(106628);var account=new Account(owner.getAccount().getId());account.setName(owner.getAccount().getName());account.setMembership(owner.getAccount().getMembership());account.setCreationDate(owner.getAccount().getCreationDate());account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));
    Player bot=PlayerService.getPlayer(106628,account);bot.setPlayerBotOwner(owner.getObjectId());
    try {
     bot.getCommonData().setLevel(owner.getLevel());bot.getGameStats().updateStatsTemplate();PlayerBotTemporary.initialize(bot,PlayerBotRules.roleFor(bot.getPlayerClass()));bot.getLifeStats().synchronizeWithMaxStats();
     Class<?> persistence=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotPersistence"),home=Class.forName(persistence.getName()+"$Home");var homeOf=home.getDeclaredMethod("of",Player.class);homeOf.setAccessible(true);invoke(persistence,"save",new Class<?>[]{Player.class,home},bot,homeOf.invoke(null,bot));
     for(var item:bot.getAllItems())try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT item_owner,item_id FROM inventory WHERE item_unique_id=?")){s.setInt(1,item.getObjectId());try(var row=s.executeQuery()){if(!row.next() || row.getInt(1)!=106628 || row.getInt(2)!=item.getItemId())throw new AssertionError("Recovery inventory custody failed");}}
     if(!foreign.equals(rows("SELECT * FROM inventory WHERE item_unique_id=191241")))throw new AssertionError("Recovery changed foreign row");
     invoke(PlayerBotRoster.class,"ready",new Class<?>[]{int.class,int.class},account.getId(),106628);
     lines.add("OK: Bardoca recovered under native ID 106628 at owner level "+bot.getLevel()+"; inventory="+bot.getAllItems().size()+", skills="+bot.getSkillList().getAllSkills().size()+"; ready only after committed private progress/inventory.");
    }finally{invoke(PlayerBotTemporary.class,"release",new Class<?>[]{Player.class},bot);bot.getController().cancelCurrentSkill(null);bot.getEffectController().removeAllEffects(true);bot.getLifeStats().cancelAllTasks();}
   }
   Files.writeString(receipt.resolve("foreign-item-after.json"),rows("SELECT * FROM inventory WHERE item_unique_id=191241"));Files.writeString(receipt.resolve("bardoca-after.json"),rows("SELECT p.id,p.name,p.exp,b.ready FROM players p JOIN playerbot_roster b ON b.player_id=p.id WHERE p.id=106628"));Files.writeString(receipt.resolve("inventory-after.json"),rows("SELECT * FROM inventory WHERE item_owner=106628 AND item_location NOT IN (2,3,125)"));
   lines.add("OK: foreign persisted item 191241 unchanged; no account, legion or market storage written.");
  }
  Files.write(receipt.resolve("runtime-verification.txt"),lines);Files.writeString(receipt.resolve("installed.json"),"{\"feature\":\"playerbot-pending-creation-recovery\",\"character\":106628}");
 }
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
