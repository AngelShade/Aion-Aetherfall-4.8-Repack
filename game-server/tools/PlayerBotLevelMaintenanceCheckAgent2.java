import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Exercise the actual maintenance tick using unsaved characters and exclusively their isolated files. */
public class PlayerBotLevelMaintenanceCheckAgent2 {
 static Player player(int id,PlayerClass pc,int level,Race race,int botOwner)throws Exception{
  var common=new PlayerCommonData(id);common.setName("MaintenanceFixture");common.setRace(race);common.setGender(Gender.FEMALE);common.setPlayerClass(pc);common.setDaeva(!pc.isStartingClass());common.setLevel(level);
  var data=new PlayerAccountData(common,new PlayerAppearance());var account=new Account(2000000000);account.setName("MaintenanceFixture");account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));var player=PlayerService.newPlayer(data,account);if(botOwner!=0)player.setPlayerBotOwner(botOwner);PlayerBotGenerationOptions.initialize(player);return player;
 }
 static List<String> gear(Player player){return player.getEquipment().getEquippedItemsWithoutStigma().stream().map(i->i.getObjectId()+":"+i.getItemId()).sorted().toList();}
 public static void agentmain(String argument,Instrumentation ignored)throws Exception{
  List<String> lines=new ArrayList<>();Method tick=PlayerBotTemporary.class.getDeclaredMethod("tick",PlayerBotSession.class,boolean.class);tick.setAccessible(true);Method close=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotQuestSync").getDeclaredMethod("close",PlayerBotSession.class);close.setAccessible(true);
  Field states=PlayerBotTemporary.class.getDeclaredField("STATES");states.setAccessible(true);
  try{
   for(Race race:List.of(Race.ELYOS,Race.ASMODIANS)){
    int ownerId=IDFactory.getInstance().nextId(),botId=IDFactory.getInstance().nextId();Player owner=null,bot=null;PlayerBotSession session=null;Set<Integer> allocated=new HashSet<>();Path path=Path.of("config/playerbots/gear-character-"+botId+".properties");
    if(Files.exists(path))throw new AssertionError("Fixture will not overwrite an existing gear file");
    try{
     owner=player(ownerId,PlayerClass.TEMPLAR,20,race,0);bot=player(botId,PlayerClass.WARRIOR,9,race,ownerId);bot.getLifeStats().setCurrentHpPercent(50);
     Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
     for(var pair:Map.of("owner",owner,"bot",bot,"generated",true,"role",PlayerBotRules.Role.TANK).entrySet()){Field field=PlayerBotSession.class.getDeclaredField(pair.getKey());field.setAccessible(true);field.set(session,pair.getValue());}
     List<String> previous=null;int count=0;
     for(int level:new int[]{20,24,30,31,40,50,60,65}){
      owner.getCommonData().setLevel(level);owner.getGameStats().updateStatsTemplate();owner.getLifeStats().synchronizeWithMaxStats();
      // Only this unsaved fixture's cadence is advanced; never reset live bot caches.
      Object state=((Map<?,?>)states.get(null)).get(botId);if(state!=null){Field next=state.getClass().getDeclaredField("next");next.setAccessible(true);next.setLong(state,0);}
      tick.invoke(null,session,true);
      if(bot.getLevel()!=level || bot.getPlayerClass()!=PlayerClass.TEMPLAR)throw new AssertionError("Owner-level scaling or tank class promotion failed");
      if(Math.abs(bot.getLifeStats().getCurrentHp()/(double)bot.getLifeStats().getMaxHp()-.5)>.01)throw new AssertionError("Scaling healed or damaged the companion");
      int skills=bot.getSkillList().getAllSkills().size();if(level==24 && skills<=count)throw new AssertionError("New skills were not learned between gear tiers");
      var current=gear(bot);if(level==24 || level==31 || level==65){if(!current.equals(previous))throw new AssertionError("Gear churned between tier levels");}else if(previous!=null && current.equals(previous))throw new AssertionError("Gear tier failed to advance");
      if(bot.getEquipment().getEquippedItemsRegularStigma().size()!=PlayerBotTemporary.regularSlots(level) || bot.getEquipment().getEquippedItemsAdvancedStigma().size()!=PlayerBotTemporary.advancedSlots(level))throw new AssertionError("Level-up Stigma maintenance failed");
      for(var item:bot.getAllItems())allocated.add(item.getObjectId());count=skills;previous=current;lines.add("OK: "+race+" actual maintenance tick owner/bot="+level+" class="+bot.getPlayerClass()+" role=TANK skills="+skills+" hpRatio="+bot.getLifeStats().getCurrentHp()/(double)bot.getLifeStats().getMaxHp());
     }
     if(!Files.exists(path))throw new AssertionError("Generated gear provenance was not persisted");
    }finally{
     if(session!=null)close.invoke(null,session);
     for(var p:Arrays.asList(owner,bot))if(p!=null){for(var item:p.getAllItems())allocated.add(item.getObjectId());p.getEffectController().removeAllEffects(true);p.getLifeStats().cancelAllTasks();}
     Files.deleteIfExists(path);for(int id:allocated)IDFactory.getInstance().releaseId(id);IDFactory.getInstance().releaseId(ownerId);IDFactory.getInstance().releaseId(botId);
    }
   }
   lines.add("SUCCESS: 16 actual maintenance ticks, scaling, tank promotion, skills between tiers, Stigma unlocks, HP preservation, tier upgrades and provenance. No live player, world or database changes; isolated files removed.");
  }catch(Throwable error){var trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));lines.add("FAIL: "+trace);Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);throw error;}
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
