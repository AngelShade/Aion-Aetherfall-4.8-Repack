import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.AccountService;
import com.aionemu.gameserver.services.item.ItemFactory;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** End-to-end native persistence on a verified empty isolated fixture account.
 * Fixture rows/config/world actors are removed in finally; no human data changes.
 */
public class PlayerBotPresetPersistenceCheckAgent5 {
 static final int ACCOUNT=2000000000;
 static void set(Object o,String name,Object value)throws Exception {for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static Player make(Account account,PlayerClass pc)throws Exception {int id=IDFactory.getInstance().nextId();var common=new PlayerCommonData(id);common.setName("PresetFixture"+id);common.setRace(Race.ELYOS);common.setGender(Gender.FEMALE);common.setPlayerClass(pc);common.setDaeva(true);common.setLevel(20);var data=new PlayerAccountData(common,new PlayerAppearance());account.addPlayerAccountData(data);var p=PlayerService.newPlayer(data,account);PlayerBotGenerationOptions.initialize(p);p.setMotions(new com.aionemu.gameserver.model.gameobjects.player.motion.MotionList(p));return p;}
 static String gearSkills(Player p){return p.getLevel()+"|"+p.getPlayerClass()+"|"+p.getAllItems().stream().map(i->i.getObjectId()+":"+i.getItemId()+":"+i.getEquipmentSlot()+":"+i.getItemCount()+":"+i.getEnchantLevel()).sorted().toList()+"|"+p.getSkillList().getAllSkills().stream().map(s->s.getSkillId()+":"+s.getSkillLevel()).sorted().toList();}
 static void checkpoint(Player p)throws Exception {Class<?> c=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotPersistence");Class<?> h=Class.forName(c.getName()+"$Home");Method home=h.getDeclaredMethod("of",Player.class);home.setAccessible(true);Method save=c.getDeclaredMethod("save",Player.class,h);save.setAccessible(true);save.invoke(null,p,home.invoke(null,p));}
 static String temporaryName(int id){StringBuilder s=new StringBuilder("Presettemp");while(id>0){s.append((char)('a'+id%26));id/=26;}return s.toString();}
 static boolean emptyAccount()throws Exception {try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT id FROM players WHERE account_id=? UNION ALL SELECT player_id FROM playerbot_roster WHERE account_id=?")){s.setInt(1,ACCOUNT);s.setInt(2,ACCOUNT);try(var r=s.executeQuery()){return !r.next();}}}
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  List<String> lines=new ArrayList<>();var service=PlayerBotService.getInstance();Player owner=null,altSetup=null;WorldMapInstance instance=null;Set<Integer> ids=new HashSet<>(),itemIds=new HashSet<>();Path partyFile=Path.of("config/playerbots/saved-parties/account-"+ACCOUNT+".json");boolean checked=false;
  synchronized(service){try {
   if(!emptyAccount() || Files.exists(partyFile) || World.getInstance().getAllPlayers().stream().anyMatch(p->p.getAccount().getId()==ACCOUNT))throw new AssertionError("Fixture account is not empty; no mutation allowed");checked=true;
   var account=new Account(ACCOUNT);account.setName("PresetFixtureAccount");account.setCreationDate(System.currentTimeMillis());account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));
   owner=make(account,PlayerClass.CLERIC);ids.add(owner.getObjectId());owner.setPlayerBotOwner(Integer.MAX_VALUE);owner.getCommonData().setOnline(true);
   Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);var sink=(com.aionemu.gameserver.network.aion.AionConnection)unsafe.allocateInstance(com.aionemu.gameserver.network.aion.AionConnection.class);set(sink,"guard",new Object());set(sink,"closed",true);owner.setClientConnection(sink);
   var world=World.getInstance();var map=world.getWorldMap(300040000);instance=WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,6);world.setPosition(owner,map.getMapId(),instance.getInstanceId(),500,500,100,(byte)0);world.storeObject(owner);world.spawn(owner);
   altSetup=make(account,PlayerClass.CLERIC);ids.add(altSetup.getObjectId());
   var template=DataManager.ITEM_DATA.getItemTemplates().stream().filter(t->t.getItemGroup()==ItemGroup.CH_TORSO && t.getItemQuality()==ItemQuality.COMMON && t.getMaxTuneCount()==0 && t.getRequiredLevel(PlayerClass.CLERIC)>=0 && t.getRequiredLevel(PlayerClass.CLERIC)<=20 && t.getItemSlot()==ItemSlot.TORSO.getSlotIdMask()).findFirst().orElseThrow();
   var item=ItemFactory.newItem(template.getTemplateId(),1);item.setSoulBound(true);altSetup.getInventory().onLoadHandler(item);if(altSetup.getEquipment().equipItem(item.getObjectId(),ItemSlot.TORSO.getSlotIdMask())==null)throw new AssertionError("Fixture native manual alt gear rejected");
   if(!PlayerService.storeNewPlayer(altSetup,account.getName(),ACCOUNT))throw new AssertionError("Native fixture alt creation failed");altSetup.setPlayerBotOwner(owner.getObjectId());checkpoint(altSetup);String altName=altSetup.getName();account.addPlayerAccountData(AccountService.loadPlayerAccountData(altSetup.getObjectId()));
   String tempName=temporaryName(owner.getObjectId());if(PlayerService.isNameUsedOrReserved(null,tempName))throw new AssertionError("Fixture temporary name already exists");service.generate(owner,tempName,PlayerClass.TEMPLAR);var temporary=service.find(owner,tempName);ids.add(temporary.bot().getObjectId());
   service.recruit(owner,altName);var alt=service.find(owner,altName);alt.setRole(PlayerBotRules.Role.HEALER);alt.order(PlayerBotRules.Order.STAY);String altBefore=gearSkills(alt.bot());
   var quest=new QuestState(1136,QuestStatus.START);quest.setQuestVarById(1,2);temporary.bot().getQuestStateList().addQuest(1136,quest);
   long kinah=temporary.bot().getInventory().getKinah();temporary.bot().getInventory().increaseKinah(777);String temporaryBefore=gearSkills(temporary.bot());
   PlayerBotPresets.saveBot(owner,tempName);PlayerBotPresets.saveParty(owner,"Native mixed fixture");
   var state=PlayerBotPresets.snapshot(owner);var preset=(PlayerBotPresets.Preset)((List<?>)state.get("presets")).getFirst();if(preset.members().size()!=2 || preset.members().stream().filter(PlayerBotPresets.Member::temporary).count()!=1)throw new AssertionError("Mixed identity lost");
   lines.add("OK: native Temporary Bot checkpoint and mixed alt/Temporary preset persisted.");
   service.dismissAll(owner);if(!service.companions(owner).isEmpty())throw new AssertionError("Native dismissal did not finish saves");
   try(var held=PlayerBotLease.acquire(altSetup.getObjectId())) {try{PlayerBotPresets.activate(owner,preset.id());throw new AssertionError("Reserved alt accepted");}catch(IllegalArgumentException expected){if(!service.companions(owner).isEmpty())throw new AssertionError("Partial party recruited before complete preflight");}}
   lines.add("OK: an unavailable alt rejects the whole preset before any recruitment.");
   PlayerBotPresets.activate(owner,preset.id());if(service.companions(owner).size()!=2)throw new AssertionError("Mixed party was not restored");alt=service.find(owner,altName);temporary=service.find(owner,tempName);
   if(!altBefore.equals(gearSkills(alt.bot())) || alt.combatRole()!=PlayerBotRules.Role.HEALER || !alt.snapshot().get("order").equals("STAY"))throw new AssertionError("Owned-alt equipment/skills/level/class or role/order lost");
   if(!temporaryBefore.equals(gearSkills(temporary.bot())) || temporary.bot().getInventory().getKinah()!=kinah+777 || temporary.bot().getQuestStateList().getQuestState(1136).getQuestVarById(1)!=2)throw new AssertionError("Temporary saved gear/skills/Kinah/quest progress lost");
   lines.add("OK: save/dismiss/summon loads the same two IDs, Temporary gear/skills/Kinah/quest progress and the owned-alt setup, role and Stay order.");
   PlayerBotPresets.activate(owner,preset.id());if(service.companions(owner).size()!=2)throw new AssertionError("Loading active preset duplicated bots");lines.add("OK: summoning an already-active preset is idempotent.");
   set(owner.getController(),"lastAttackedMillis",System.currentTimeMillis());try{PlayerBotPresets.activate(owner,preset.id());throw new AssertionError("Combat recruitment permitted");}catch(IllegalArgumentException expected){if(service.companions(owner).size()!=2)throw new AssertionError("Combat rejection changed party");}set(owner.getController(),"lastAttackedMillis",0L);lines.add("OK: native owner combat timer rejects preset recruitment without changing the party.");
   PlayerBotPresets.remove(owner,preset.id());if(!((List<?>)PlayerBotPresets.snapshot(owner).get("savedBots")).contains(temporary.bot().getObjectId()))throw new AssertionError("Preset removal deleted saved bot");lines.add("OK: removing a preset retains saved character progress.");
   service.dismissAll(owner);lines.add("SUCCESS: native creation/checkpoint/persistence, complete mixed-party reload, unavailable-alt preflight, idempotency, combat guard and owned-alt preservation.");
  }catch(Throwable e){var trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));lines.add("FAIL: "+trace);}
  finally {
   if(checked) {
    if(owner!=null)service.dismissAll(owner);
    // Collect only IDs in the fixture account, which was verified completely empty.
    try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT id FROM players WHERE account_id=? UNION ALL SELECT player_id FROM playerbot_roster WHERE account_id=?")){s.setInt(1,ACCOUNT);s.setInt(2,ACCOUNT);try(var r=s.executeQuery()){while(r.next())ids.add(r.getInt(1));}}
    // Force-release only fixture sessions if a failed persistence check left them reserved.
    Field sessionsField=PlayerBotService.class.getDeclaredField("sessions"),homesField=PlayerBotService.class.getDeclaredField("homes");sessionsField.setAccessible(true);homesField.setAccessible(true);var sessions=(Map<Integer,PlayerBotSession>)sessionsField.get(service);var homes=(Map<Integer,Object>)homesField.get(service);
    for(int id:ids){var session=sessions.remove(id);homes.remove(id);if(session!=null){Method mark=PlayerBotSession.class.getDeclaredMethod("markClosing");mark.setAccessible(true);mark.invoke(session);Method lease=PlayerBotSession.class.getDeclaredMethod("lease");lease.setAccessible(true);((PlayerBotLease)lease.invoke(session)).close();}}
    try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT item_unique_id FROM inventory WHERE item_owner=? AND item_location NOT IN (2,3,125)")){for(int id:ids){s.setInt(1,id);try(var r=s.executeQuery()){while(r.next())itemIds.add(r.getInt(1));}}}
    for(int id:ids){var p=World.getInstance().getPlayer(id);if(p!=null){if(p.getPlayerGroup()!=null)com.aionemu.gameserver.model.team.group.PlayerGroupService.removePlayer(p);for(var i:p.getAllItems())itemIds.add(i.getObjectId());p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);World.getInstance().removeObject(p);}}
    for(var p:Arrays.asList(owner,altSetup))if(p!=null){for(var i:p.getAllItems())itemIds.add(i.getObjectId());p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);World.getInstance().removeObject(p);}
    // Inventory has no player-owner FK: explicitly delete only this verified
    // fixture's private rows before releasing its IDs. Never rely on cascade.
    try(var c=DatabaseFactory.getConnection()){c.setAutoCommit(false);try(var inv=c.prepareStatement("DELETE FROM inventory WHERE item_owner=? AND item_location NOT IN (2,3,125)");var s=c.prepareStatement("DELETE FROM playerbot_roster WHERE account_id=? AND player_id=?");var p=c.prepareStatement("DELETE FROM players WHERE account_id=? AND id=?")){for(int id:ids){inv.setInt(1,id);inv.addBatch();s.setInt(1,ACCOUNT);s.setInt(2,id);s.addBatch();p.setInt(1,ACCOUNT);p.setInt(2,id);p.addBatch();}inv.executeBatch();s.executeBatch();p.executeBatch();}c.commit();}
    try(var c=DatabaseFactory.getConnection();var q=c.prepareStatement("SELECT COUNT(*) FROM inventory WHERE item_owner=? AND item_location NOT IN (2,3,125)")){for(int id:ids){q.setInt(1,id);try(var r=q.executeQuery()){r.next();if(r.getInt(1)!=0)throw new AssertionError("Private fixture inventory remains before ID release");}}}lines.add("CLEANUP: private fixture inventory removed and verified before ID release; shared storage excluded.");Files.deleteIfExists(partyFile);for(int id:ids){for(String prefix:List.of("character-","gear-character-","care-character-","party-character-"))Files.deleteIfExists(Path.of("config/playerbots/"+prefix+id+".properties"));IDFactory.getInstance().releaseId(id);}for(int id:itemIds)IDFactory.getInstance().releaseId(id);
    if(!emptyAccount())throw new AssertionError("Fixture rows remain after cleanup");lines.add("CLEANUP: fixture character/roster rows and private settings removed; no human/account rows changed.");
   }
   if(instance!=null)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());
  }}
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);
  if(lines.stream().anyMatch(s->s.startsWith("FAIL:")))throw new AssertionError("Native preset fixture failed; diagnostic saved");
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}

