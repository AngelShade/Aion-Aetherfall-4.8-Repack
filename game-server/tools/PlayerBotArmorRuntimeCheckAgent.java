import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.PlayerBotGenerationOptions;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Exercise real creation, skill and equipment data in memory; never stores/spawns a character. */
public class PlayerBotArmorRuntimeCheckAgent {
 public static void agentmain(String argument, Instrumentation ignored)throws Exception {
  List<String> lines=new ArrayList<>();int cases=0;
  Method equip=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotGenerated").getDeclaredMethod("equipStarterSet",Player.class);equip.setAccessible(true);
  try {
   for(Race race:List.of(Race.ELYOS,Race.ASMODIANS))for(PlayerClass pc:PlayerClass.values())for(int level:pc.isStartingClass()?new int[]{1}:new int[]{12,65}) {
    int id=IDFactory.getInstance().nextId();Player player=null;
    try {
     var common=new PlayerCommonData(id);common.setName("CreationFixture");common.setRace(race);common.setGender(Gender.FEMALE);common.setPlayerClass(pc);common.setDaeva(!pc.isStartingClass());common.setLevel(level);
     var data=new PlayerAccountData(common,new PlayerAppearance());var account=new Account(2000000000);account.setName("CreationFixture");account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));
     player=PlayerService.newPlayer(data,account);player.setPlayerBotOwner(Integer.MAX_VALUE);
     if(player.getRecipeList()==null || player.getSkillList()==null)throw new AssertionError("Creation callback state missing");
     if(!pc.isStartingClass())equip.invoke(null,player);
     PlayerBotGenerationOptions.initialize(player);
     if(player.getEquipment().getMainHandWeapon()==null || player.getSkillList().getAllSkills().isEmpty() || player.getLifeStats().getCurrentHp()<=0)throw new AssertionError("Missing weapon, skills or healthy life stats");
     for(var item:player.getEquipment().getEquippedItemsWithoutStigma()){if(item.getItemTemplate().getRequiredLevel(pc)>level)throw new AssertionError("Above-level equipment");if(!com.aionemu.gameserver.services.playerbot.PlayerBotBuildRules.armorAllowed(item.getItemTemplate().getItemGroup(),pc,player.getSkillList()::isSkillPresent))throw new AssertionError("Wrong armor type: "+pc+" "+item.getItemTemplate().getItemGroup());}
     lines.add("OK: "+race+" "+pc+" level="+level+" skills="+player.getSkillList().getAllSkills().size()+" equipment="+player.getEquipment().getEquippedItemsWithoutStigma().size()+" startingRecipes="+player.getRecipeList().size());cases++;
    } finally {
     if(player!=null){if(player.getEffectController()!=null)player.getEffectController().removeAllEffects(true);player.getLifeStats().cancelAllTasks();Set<Integer> ids=new HashSet<>();for(var item:player.getAllItems())ids.add(item.getObjectId());for(int itemId:ids)IDFactory.getInstance().releaseId(itemId);}
     IDFactory.getInstance().releaseId(id);
    }
   }
   lines.add("SUCCESS: "+cases+" production in-memory creation cases; no character/roster/inventory/database writes or world spawn.");
  }catch(Throwable error){lines.add("FAIL: "+error);Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);throw error;}
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
