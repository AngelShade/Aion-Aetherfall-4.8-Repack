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

/** Native unsaved creation, sequential gear/build updates and owned-alt fingerprints. */
public class PlayerBotTemporaryRuntimeCheckAgent7 {
 static String fingerprint(Player player){return player.getPlayerClass()+":"+player.getLevel()+":"+player.getAllItems().stream().map(i->i.getObjectId()+"/"+i.getItemId()+"/"+i.getEquipmentSlot()).sorted().toList()+":"+player.getSkillList().getAllSkills().stream().map(s->s.getSkillId()+"/"+s.getSkillLevel()).sorted().toList();}
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  List<String> lines=new ArrayList<>();int cases=0;Class<?> type=PlayerBotTemporary.class;
  Method build=type.getDeclaredMethod("build",Player.class,PlayerBotRules.Role.class,boolean.class);build.setAccessible(true);
  Method release=type.getDeclaredMethod("release",Player.class);release.setAccessible(true);
  Method weight=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotEquipment").getDeclaredMethod("weight",com.aionemu.gameserver.model.stats.container.StatEnum.class,PlayerBotRules.Role.class,boolean.class);weight.setAccessible(true);
  Object[][] weights={{"MAXHP",PlayerBotRules.Role.TANK,false,.5},{"MAXHP",PlayerBotRules.Role.HEALER,true,.15},{"HEAL_BOOST",PlayerBotRules.Role.HEALER,true,2.0},{"BOOST_HATE",PlayerBotRules.Role.TANK,false,5.0},{"BOOST_HATE",PlayerBotRules.Role.RANGED,true,-1.0},{"BLOCK",PlayerBotRules.Role.TANK,false,1.0},{"PHYSICAL_ATTACK",PlayerBotRules.Role.MELEE,false,2.0},{"PHYSICAL_ATTACK",PlayerBotRules.Role.RANGED,true,0.0},{"BOOST_MAGICAL_SKILL",PlayerBotRules.Role.RANGED,true,1.0},{"PHYSICAL_CRITICAL",PlayerBotRules.Role.MELEE,false,.75},{"MAGICAL_ACCURACY",PlayerBotRules.Role.RANGED,true,.3},{"BOOST_CASTING_TIME",PlayerBotRules.Role.HEALER,true,4.0}};
  for(var row:weights){double actual=(double)weight.invoke(null,com.aionemu.gameserver.model.stats.container.StatEnum.valueOf((String)row[0]),row[1],row[2]);if(Math.abs(actual-(double)row[3])>1e-9)throw new AssertionError("Installed stat weight differs: "+Arrays.toString(row)+" actual="+actual);}
  lines.add("OK: 12 installed tank/healer/physical/caster native stat weights");
  try {
   for(Race race:List.of(Race.ELYOS,Race.ASMODIANS))for(PlayerClass pc:PlayerClass.values()) {
    int id=IDFactory.getInstance().nextId();Player player=null;Set<Integer> allocated=new HashSet<>();
    try {
     var common=new PlayerCommonData(id);common.setName("TemporaryFixture");common.setRace(race);common.setGender(Gender.FEMALE);common.setPlayerClass(pc);common.setDaeva(!pc.isStartingClass());common.setLevel(pc.isStartingClass()?1:12);
     var data=new PlayerAccountData(common,new PlayerAppearance());var account=new Account(2000000000);account.setName("TemporaryFixture");account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));
     player=PlayerService.newPlayer(data,account);player.setPlayerBotOwner(Integer.MAX_VALUE);PlayerBotGenerationOptions.initialize(player);
     var role=PlayerBotRules.roleFor(pc);double previousGear=0;
     for(int level:pc.isStartingClass()?new int[]{1}:new int[]{12,20,30,40,45,50,55,60,65}) {
      common.setLevel(level);player.getGameStats().updateStatsTemplate();player.getLifeStats().synchronizeWithMaxStats();
      build.invoke(null,player,role,true);
      for(var item:player.getAllItems())allocated.add(item.getObjectId());
      if(player.getEquipment().getMainHandWeapon()==null || player.getSkillList().getAllSkills().isEmpty() || player.getLifeStats().getCurrentHp()<=0)throw new AssertionError("Incomplete build: "+pc+" level "+level);
      for(var item:player.getEquipment().getEquippedItemsWithoutStigma())if(item.getItemTemplate().getRequiredLevel(pc)>level || !PlayerBotBuildRules.armorAllowed(item.getItemTemplate().getItemGroup(),pc,player.getSkillList()::isSkillPresent))throw new AssertionError("Invalid equipment "+pc+" "+item.getItemTemplate().getItemGroup());
      int regular=player.getEquipment().getEquippedItemsRegularStigma().size(),advanced=player.getEquipment().getEquippedItemsAdvancedStigma().size();
      if(regular!=PlayerBotTemporary.regularSlots(level) || advanced!=PlayerBotTemporary.advancedSlots(level))throw new AssertionError("Incomplete native Stigma sockets "+pc+" level="+level+" regular="+regular+" advanced="+advanced);
      Player current=player;
      for(var item:player.getEquipment().getEquippedItemsAllStigma())for(String group:item.getItemTemplate().getStigma().getGainSkillGroups())if(com.aionemu.gameserver.dataholders.DataManager.SKILL_DATA.getSkillTemplatesByGroup(group).stream().noneMatch(t->playerSkills(current,t.getSkillId())))throw new AssertionError("Stigma skill missing");
      double gearScore=player.getEquipment().getEquippedItemsWithoutStigma().stream().mapToDouble(i->i.getItemTemplate().getLevel()+i.getItemTemplate().getItemQuality().getQualityId()*10).sum();
      if(level==20 || level==30 || level==40 || level==50 || level==60)if(gearScore<=previousGear)throw new AssertionError("No meaningful native tier improvement: "+pc+" level="+level);
      previousGear=gearScore;
      lines.add("OK: "+race+" "+pc+" level="+level+" skills="+player.getSkillList().getAllSkills().size()+" gear="+player.getEquipment().getEquippedItemsWithoutStigma().size()+" stigmas="+regular+"+"+advanced+" tierScore="+gearScore);cases++;
     }
     release.invoke(null,player);
     // Same real native character now stands for an owned alt: maintenance must return without a mutation.
     Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var u=(sun.misc.Unsafe)unsafeField.get(null);var session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);
     for(String key:List.of("owner","bot")){Field f=PlayerBotSession.class.getDeclaredField(key);f.setAccessible(true);f.set(session,player);}
     String before=fingerprint(player);Method tick=type.getDeclaredMethod("tick",PlayerBotSession.class,boolean.class);tick.setAccessible(true);tick.invoke(null,session,true);
     if(!before.equals(fingerprint(player)) || PlayerBotTemporary.managed(player))throw new AssertionError("Owned alt was modified");
     lines.add("ALT PRESERVED: "+race+" "+pc+" equipment, skills, Stigmas, class and level");
    } finally {
     if(player!=null){release.invoke(null,player);for(var item:player.getAllItems())allocated.add(item.getObjectId());if(player.getEffectController()!=null)player.getEffectController().removeAllEffects(true);player.getLifeStats().cancelAllTasks();}
     for(int itemId:allocated)IDFactory.getInstance().releaseId(itemId);IDFactory.getInstance().releaseId(id);
    }
   }
   lines.add("SUCCESS: "+cases+" native build/tier cases and 34 owned-alt fingerprints; no database characters, roster changes or world spawn.");
  }catch(Throwable error){java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));lines.add("FAIL: "+trace);Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);throw error;}
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);
 }
 static boolean playerSkills(Player player,int skill){return player.getSkillList().isSkillPresent(skill);}
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
