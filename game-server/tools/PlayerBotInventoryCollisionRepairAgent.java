import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.services.item.ItemFactory;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** One bounded recovery: preserve the foreign row; reissue only Tanku's never-saved item ID. */
public class PlayerBotInventoryCollisionRepairAgent {
 static Object field(Object o,String name)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static String row(int id)throws Exception {try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT * FROM inventory WHERE item_unique_id=?")){s.setInt(1,id);try(var r=s.executeQuery()){if(!r.next())return "missing";var values=new LinkedHashMap<String,String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)values.put(r.getMetaData().getColumnLabel(i),r.getString(i));return com.alibaba.fastjson2.JSON.toJSONString(values);}}}
 static String attributes(Item item)throws Exception {var result=new TreeMap<String,String>();for(var f:Item.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers()) && !Set.of("currentModifiers","enchantEffect","temperingEffect").contains(f.getName())){f.setAccessible(true);result.put(f.getName(),String.valueOf(f.get(item)));}return result.toString();}
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  var lines=new ArrayList<String>();Path receipt=Path.of(argument);Files.createDirectory(receipt);
  var service=PlayerBotService.getInstance();synchronized(service){
   var sessions=(Map<Integer,PlayerBotSession>)field(service,"sessions");var session=sessions.get(106596);
   if(session==null || !session.bot().getName().equals("Tanku") || !Boolean.TRUE.equals(session.snapshot().get("generated")))throw new IllegalStateException("Expected Temporary Bot session missing");
   synchronized(session){var bot=session.bot();if(bot.isCasting() || bot.getController().isInCombat())throw new IllegalStateException("Companion is busy; no changes made");
    var old=bot.getAllItems().stream().filter(i->i.getObjectId()==191267).findFirst().orElseThrow();
    if(old.getPersistentState()!=Persistable.PersistentState.NEW || old.getItemId()!=112600367 || old.getItemCount()!=1 || !old.getItemStones().isEmpty() || !old.getFusionStones().isEmpty() || old.getGodStone()!=null || old.getIdianStone()!=null || old.getEnchantLevel()!=0 || old.getTempering()!=0)throw new IllegalStateException("Unsaved-item recovery precondition changed");
    try(var c=DatabaseFactory.getConnection();var q=c.prepareStatement("SELECT item_owner,item_id FROM inventory WHERE item_unique_id=?")){q.setInt(1,old.getObjectId());try(var r=q.executeQuery()){if(!r.next() || r.getInt(1)!=191098 || r.getInt(2)!=140001127)throw new IllegalStateException("Conflicting row changed");}}
    String foreign=row(old.getObjectId());Files.writeString(receipt.resolve("foreign-row-before.json"),foreign);String before=attributes(old);Files.writeString(receipt.resolve("unsaved-item-before.txt"),before);
    // Exercise the installed guard with an isolated unsaved actor and a changed
    // foreign ID. Always rollback, even if the guard were unexpectedly absent.
    var fixture=(Player)Class.forName("PlayerBotHealingCheckAgent11").getDeclaredMethod("make").invoke(null);
    try {var conflicting=new Item(old.getObjectId(),old.getItemTemplate(),1,false,0);fixture.getInventory().onLoadHandler(conflicting);conflicting.setPersistentState(Persistable.PersistentState.UPDATED);conflicting.setPersistentState(Persistable.PersistentState.UPDATE_REQUIRED);InventoryDAO.markCompanionInventoryDirty(fixture);
     try(var c=DatabaseFactory.getConnection()){c.setAutoCommit(false);try {InventoryDAO.storeCompanionInventory(c,fixture);throw new AssertionError("Installed custody guard accepted a foreign item");}catch(java.sql.SQLException expected){if(!expected.getMessage().startsWith("Companion item custody mismatch:"))throw expected;}finally{c.rollback();}}
     if(!foreign.equals(row(old.getObjectId())))throw new AssertionError("Guard fixture changed persisted row");lines.add("OK: installed custody guard rejects a changed foreign item before writes; rollback verified.");
    }finally{fixture.getLifeStats().cancelAllTasks();fixture.getEffectController().removeAllEffects(true);IDFactory.getInstance().releaseId(fixture.getObjectId());}
    Path gear=Path.of("config/playerbots/gear-character-"+bot.getObjectId()+".properties");Files.copy(gear,receipt.resolve("gear-before.properties"));
    // Re-lock persisted IDs before allocating. Preserve every existing row,
    // including orphan rows; never release the old ID belonging to that row.
    var factory=IDFactory.getInstance();var lock=(ReentrantLock)field(factory,"lock");lock.lock();try{var bits=(BitSet)field(factory,"idList");for(int id:InventoryDAO.getUsedIDs())bits.set(id);for(int id:PlayerDAO.getUsedIDs())bits.set(id);}finally{lock.unlock();}
    long slot=old.getEquipmentSlot();boolean equipped=old.isEquipped();int hp=bot.getLifeStats().getCurrentHp(),mp=bot.getLifeStats().getCurrentMp();
    if(equipped && bot.getEquipment().unEquipItem(old.getObjectId(),false)!=old)throw new IllegalStateException("Native unequip failed");
    var replacement=ItemFactory.newItem(old.getItemId(),old.getItemCount());if(!row(replacement.getObjectId()).equals("missing"))throw new IllegalStateException("Allocated ID is persisted");
    for(var f:Item.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()) && !Set.of("currentModifiers","enchantEffect","temperingEffect").contains(f.getName())){f.setAccessible(true);f.set(replacement,f.get(old));}
    replacement.setPersistentState(Persistable.PersistentState.NEW);if(bot.getInventory().remove(old)!=old)throw new IllegalStateException("Native cube removal failed");bot.getInventory().onLoadHandler(replacement);
    if(equipped && bot.getEquipment().equipItem(replacement.getObjectId(),slot)!=replacement)throw new IllegalStateException("Native re-equip failed");
    bot.getLifeStats().setCurrentHp(Math.min(hp,bot.getLifeStats().getMaxHp()));bot.getLifeStats().setCurrentMp(Math.min(mp,bot.getLifeStats().getMaxMp()));
    if(!before.equals(attributes(replacement)))throw new IllegalStateException("Replacement item attributes changed");
    Class<?> policy=PlayerBotGearPolicy.class;var states=(Map<Integer,Object>)fieldStatic(policy,"STATES");var state=states.get(bot.getObjectId());if(state==null){var method=policy.getDeclaredMethod("state",PlayerBotSession.class);method.setAccessible(true);state=method.invoke(null,session);}
    var generated=(Set<Integer>)field(state,"generated");if(generated.remove(old.getObjectId()))generated.add(replacement.getObjectId());var save=state.getClass().getDeclaredMethod("save");save.setAccessible(true);save.invoke(state);
    var builds=(Map<Integer,Object>)fieldStatic(PlayerBotTemporary.class,"STATES");var build=builds.get(bot.getObjectId());if(build!=null){var created=(Set<Integer>)field(build,"created");if(created.remove(old.getObjectId()))created.add(replacement.getObjectId());}
    InventoryDAO.markCompanionInventoryDirty(bot);var home=((Map<Integer,Object>)field(service,"homes")).get(bot.getObjectId());Class<?> persistence=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotPersistence");var checkpoint=persistence.getDeclaredMethod("save",Player.class,home.getClass());checkpoint.setAccessible(true);checkpoint.invoke(null,bot,home);
    if(!foreign.equals(row(old.getObjectId())) || row(replacement.getObjectId()).equals("missing") || replacement.getPersistentState()!=Persistable.PersistentState.UPDATED)throw new IllegalStateException("Persistence verification failed");
    Files.writeString(receipt.resolve("foreign-row-after.json"),row(old.getObjectId()));Files.writeString(receipt.resolve("replacement-row.json"),row(replacement.getObjectId()));Files.writeString(receipt.resolve("unsaved-item-after.txt"),attributes(replacement));Files.copy(gear,receipt.resolve("gear-after.properties"));
    lines.add("OK: Temporary Bot Tanku item ID 191267 -> "+replacement.getObjectId()+"; identical attributes/equipment slot, native stats and gear provenance preserved.");lines.add("OK: native companion inventory/progress transaction committed; foreign row unchanged; all persisted IDs remain reserved.");
   }
  }Files.write(receipt.resolve("runtime-verification.txt"),lines);
 }
 static Object fieldStatic(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}


