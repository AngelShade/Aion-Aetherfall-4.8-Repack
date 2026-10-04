/* PlayerbotFactory equipment/skill initialization and incremental upgrades are
 * adapted from mod-playerbots at 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * Aion skill trees, Stigma sockets, masteries and item effects are authoritative.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.item.enums.*;
import com.aionemu.gameserver.services.SkillLearnService;
import com.aionemu.gameserver.services.StigmaService;
import com.aionemu.gameserver.services.item.ItemFactory;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

/** Only dedicated roster characters can enter the automatic maintenance path. */
public final class PlayerBotTemporary {
 private static final class State {int level=-1,tier=-1;Role role;long next;final Set<Integer> created=new HashSet<>();}
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 private static final ThreadLocal<Integer> BUILDING=new ThreadLocal<>();
 private static final List<ItemSlot> GEAR=List.of(ItemSlot.MAIN_HAND,ItemSlot.SUB_HAND,ItemSlot.TORSO,ItemSlot.SHOULDER,ItemSlot.GLOVES,ItemSlot.PANTS,ItemSlot.BOOTS,ItemSlot.HELMET,ItemSlot.NECKLACE,ItemSlot.WAIST,ItemSlot.EARRINGS_LEFT,ItemSlot.EARRINGS_RIGHT,ItemSlot.RING_LEFT,ItemSlot.RING_RIGHT);
 public static boolean managed(Player player){return player.isPlayerBot() && STATES.containsKey(player.getObjectId());}
 public static boolean building(Player player){return managed(player) && Objects.equals(BUILDING.get(),player.getObjectId());}
 public static int regularSlots(int level){return level<20 ? 0 : level<30 ? 1 : level<40 ? 2 : 3;}
 public static int advancedSlots(int level){return level<45 ? 0 : level<50 ? 1 : level<55 ? 2 : 3;}
 public static int tier(int level){return level<20 ? 0 : level/10*10;}
 public static ItemQuality quality(int level){return level<20 ? ItemQuality.RARE : level<30 ? ItemQuality.LEGEND : level<40 ? ItemQuality.UNIQUE : level<60 ? ItemQuality.EPIC : ItemQuality.MYTHIC;}
 public static String description(Player player){return "Temporary Bot · "+player.getPlayerClass()+" · "+quality(player.getLevel())+" tier "+tier(player.getLevel());}
 public static void initialize(Player player,Role role){
  if(!player.isPlayerBot() || !PlayerBotRoster.contains(player.getObjectId()))throw new IllegalArgumentException("Automatic builds require a dedicated Temporary Bot, never an owned alt.");
  build(player,role,true);
  persistCreation(player,STATES.get(player.getObjectId()));
 }
 private static void persistCreation(Player player,State state){
  java.nio.file.Path path=java.nio.file.Path.of("config/playerbots/gear-character-"+player.getObjectId()+".properties");var p=new Properties();
  try{
   if(java.nio.file.Files.exists(path))try(var in=java.nio.file.Files.newInputStream(path)){p.load(in);}
   p.putIfAbsent("account",Integer.toString(player.getAccount().getId()));p.putIfAbsent("character",Integer.toString(player.getObjectId()));
   var s=PlayerBotGearPolicy.DEFAULT;String[] keys={"mode","profile","quality","level","threshold","weapon","vendors","rolls","starterSlots"};String[] values={s.mode().name(),s.profile().name(),s.quality().name(),"0",Double.toString(s.threshold()),s.weapon(),"false",s.rolls().name(),""};for(int i=0;i<keys.length;i++)p.putIfAbsent(keys[i],values[i]);
   Set<Integer> ids=new HashSet<>(state.created);for(String value:p.getProperty("generated","").split(","))if(!value.isBlank())ids.add(Integer.parseInt(value));p.setProperty("generated",ids.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
   java.nio.file.Files.createDirectories(path.getParent());var tmp=java.nio.file.Files.createTempFile(path.getParent(),"temporary-gear-",".tmp");try{try(var out=java.nio.file.Files.newOutputStream(tmp)){p.store(out,"Temporary Bot generated gear provenance");}try{java.nio.file.Files.move(tmp,path,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException e){java.nio.file.Files.move(tmp,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}}finally{java.nio.file.Files.deleteIfExists(tmp);}
  }catch(java.io.IOException e){throw new IllegalStateException("Cannot preserve Temporary Bot equipment provenance",e);}
 }
 private static void build(Player player,Role role,boolean gear){
  var state=STATES.computeIfAbsent(player.getObjectId(),id->new State());
  if(BUILDING.get()!=null)throw new IllegalStateException("Nested temporary build");
  BUILDING.set(player.getObjectId());
  double hp=player.getLifeStats().getCurrentHp()/(double)Math.max(1,player.getLifeStats().getMaxHp()),mp=player.getLifeStats().getCurrentMp()/(double)Math.max(1,player.getLifeStats().getMaxMp());
  try {
   learn(player);
   if(gear)gear(player,role,state);
   stigmas(player,role,state);
   player.getLifeStats().updateCurrentStats();
   player.getLifeStats().setCurrentHp((int)Math.round(player.getLifeStats().getMaxHp()*hp));
   player.getLifeStats().setCurrentMp((int)Math.round(player.getLifeStats().getMaxMp()*mp));
   state.level=player.getLevel();state.tier=tier(player.getLevel());state.role=role;
  }finally{BUILDING.remove();}
 }
 private static void learn(Player player){
  var allowed=new HashMap<Integer,Integer>();
  var pc=player.getPlayerClass();
  for(int level=player.getLevel();level>=1;level--)for(var cls:pc.isStartingClass() ? List.of(pc) : List.of(pc,pc.getStartingClass())) {
   if(cls.isStartingClass() && !pc.isStartingClass() && level>=10)continue;
   for(var template:DataManager.SKILL_TREE_DATA.getTemplatesFor(cls,level,player.getRace())) {
    if(template.isStigma() || !template.isAutolearn() && template.getSkillId()>=30000)continue;
    allowed.merge(template.getSkillId(),template.getSkillLevel(),Math::max);
   }
  }
  // Down-scaling or changing the system build cannot retain over-level combat skills.
  for(var entry:List.copyOf(player.getSkillList().getAllSkills()))if(!entry.isStigmaSkill() && entry.getSkillId()<30000 && !allowed.containsKey(entry.getSkillId()))SkillLearnService.removeSkill(player,entry.getSkillId());
  allowed.forEach((id,level)->player.getSkillList().addSkill(player,id,level));
 }
 private static Role gearRole(Player player,Role requested){return PlayerBotGearPolicy.role(player,requested);}
 private static ItemTemplate choice(Player player,Role role,ItemSlot slot){
  ItemTemplate best=null;double score=-Double.MAX_VALUE;
  String weapon=PlayerBotGearPolicy.settings(player).weapon();
  var settings=new PlayerBotGearPolicy.Settings(PlayerBotGearPolicy.Mode.GENERATED,PlayerBotGearPolicy.Profile.AUTO,quality(player.getLevel()),0,1,weapon,false,PlayerBotGearPolicy.Rolls.UPGRADES);
  ItemGroup preferred=role==Role.TANK && Set.of(PlayerClass.WARRIOR,PlayerClass.GLADIATOR,PlayerClass.TEMPLAR).contains(player.getPlayerClass()) ? ItemGroup.SWORD : PlayerBotGenerated.weapon(player.getPlayerClass());
  for(var template:DataManager.ITEM_DATA.getItemTemplates()) {
   if(!PlayerBotGearPolicy.generatedTemplate(player,template,settings,player.getLevel()))continue;
   if(template.isWeapon() && weapon.equals("AUTO") && template.getItemGroup()!=preferred)continue;
   long target=slot.getSlotIdMask();
   if(slot==ItemSlot.MAIN_HAND){if(!template.isWeapon())continue;}
   else if(slot==ItemSlot.SUB_HAND){
    var main=player.getEquipment().getMainHandWeapon();if(main!=null && main.getItemTemplate().isTwoHandWeapon())continue;
    boolean shield=role==Role.TANK || player.getPlayerClass()==PlayerClass.CLERIC || player.getPlayerClass()==PlayerClass.PRIEST;
    if(shield ? template.getItemGroup()!=ItemGroup.SHIELD : !template.isOneHandWeapon() || !com.aionemu.gameserver.skillengine.effect.WeaponDualEffect.hasDualWieldEffect(player))continue;
   }else if(template.isWeapon() || (template.getItemSlot() & target)==0 || Long.bitCount(template.getItemSlot())>1 && template.getItemGroup()!=ItemGroup.EARRING && template.getItemGroup()!=ItemGroup.RING)continue;
   double value=PlayerBotGearPolicy.score(player,template,role);
   // Choose the best native tier first, then build stats, never template ordering.
   if(best==null || template.getItemQuality().getQualityId()>best.getItemQuality().getQualityId() || template.getItemQuality()==best.getItemQuality() && (value>score || value==score && template.getTemplateId()<best.getTemplateId())){best=template;score=value;}
  }
  return best;
 }
 private static void gear(Player player,Role requested,State state){
  Role role=gearRole(player,requested);
  for(var slot:GEAR) {
   ItemTemplate selected=choice(player,role,slot);if(selected==null)continue;
   long mask=selected.isTwoHandWeapon() ? ItemSlot.MAIN_OR_SUB.getSlotIdMask() : slot.getSlotIdMask();
   List<Item> current=player.getEquipment().getEquippedItems().stream().filter(i->(i.getEquipmentSlot() & mask)!=0).toList();
   boolean valid=!current.isEmpty() && current.stream().allMatch(i->PlayerBotGearPolicy.eligible(player,i.getItemTemplate()) && (!i.getItemTemplate().isWeapon() || PlayerBotBuildRules.weaponAllowed(i.getItemTemplate().getItemGroup(),player.getPlayerClass(),role)));
   if(valid && current.stream().allMatch(i->i.getItemTemplate().getItemQuality().getQualityId()>=selected.getItemQuality().getQualityId()) && current.stream().mapToDouble(i->PlayerBotGearPolicy.score(player,i,role)).sum()>=PlayerBotGearPolicy.score(player,selected,role))continue;
   if(player.getInventory().isFull())return;
   Item item=player.getInventory().getItems().stream().filter(i->i.getItemId()==selected.getTemplateId() && i.isIdentified()).findFirst().orElse(null);
   if(item==null){item=ItemFactory.newItem(selected.getTemplateId(),1);if(item==null)throw new IllegalStateException("Missing Temporary gear template: "+selected.getTemplateId());
    // Factory-created items use the same identification rolls as ItemActionService.
    if(!item.isIdentified()){item.setOptionalSockets(com.aionemu.commons.utils.Rnd.get(0,selected.getOptionSlotBonus()));item.setBonusStats(com.aionemu.gameserver.model.templates.item.actions.TuningAction.getRandomStatBonusIdFor(item),true);item.setEnchantBonus(com.aionemu.commons.utils.Rnd.get(0,selected.getMaxEnchantBonus()));item.setTuneCount(item.getTuneCount()+1);}
    item.setSoulBound(true);state.created.add(item.getObjectId());player.getInventory().onLoadHandler(item);}
   if(player.getEquipment().equipItem(item.getObjectId(),mask)==null)throw new IllegalStateException("Native Temporary Bot equip rejected: "+selected.getTemplateId()+" slot="+slot);
   for(var old:current)if(!old.isEquipped() && state.created.contains(old.getObjectId())){player.getInventory().decreaseByObjectId(old.getObjectId(),old.getItemCount(),com.aionemu.gameserver.services.item.ItemPacketService.ItemUpdateType.DEC_ITEM_USE);state.created.remove(old.getObjectId());}
  }
 }
 private static double stigmaScore(Player player,Role role,ItemTemplate item){
  double result=0;
  for(String group:item.getStigma().getGainSkillGroups())for(var template:DataManager.SKILL_DATA.getSkillTemplatesByGroup(group)){
   boolean available=DataManager.SKILL_TREE_DATA.getTemplatesForSkill(template.getSkillId(),player.getPlayerClass(),player.getRace()).stream().anyMatch(s->s.getMinLevel()<=player.getLevel());if(!available)continue;
   double value=switch(PlayerBotSkills.classify(template)){
    case TAUNT->role==Role.TANK && PlayerBotTank.enmity(template,1)>0 ? 100+Math.log1p(PlayerBotTank.enmity(template,1)) : 0;
    case DEFENSE,RECOVERY->role==Role.TANK ? 90 : 25;
    case HEAL,CLEANSE,RESURRECT->role==Role.HEALER ? 100 : role==Role.SUPPORT ? 80 : 15;
    case BUFF->role==Role.SUPPORT ? 100 : 40;
    case SUMMON,PET_ORDER->player.getPlayerClass()==PlayerClass.SPIRIT_MASTER ? 100 : 30;
    case DAMAGE->role==Role.MELEE || role==Role.RANGED ? 90 : 30;
    case CONTROL->40;case MANA->role==Role.HEALER ? 90 : 25;case MODE->60;default->template.isPassive() ? 50 : 0;
   };
   result=Math.max(result,value);
  }
  return result;
 }
 private static void stigmas(Player player,Role role,State state){
  List<ItemSlot> slots=new ArrayList<>();slots.addAll(List.of(ItemSlot.STIGMA1,ItemSlot.STIGMA2,ItemSlot.STIGMA3).subList(0,regularSlots(player.getLevel())));slots.addAll(List.of(ItemSlot.ADV_STIGMA1,ItemSlot.ADV_STIGMA2,ItemSlot.ADV_STIGMA3).subList(0,advancedSlots(player.getLevel())));
  Set<String> used=new HashSet<>();var choices=DataManager.ITEM_DATA.getItemTemplates().stream().filter(t->t.isStigma() && t.getStigma().isChargeable() && t.isClassSpecific(player.getPlayerClass()) && t.getRequiredLevel(player.getPlayerClass())>=0 && t.getRequiredLevel(player.getPlayerClass())<=player.getLevel() && (t.getRace()==Race.PC_ALL || t.getRace()==player.getRace()) && stigmaScore(player,role,t)>0)
   .sorted(Comparator.<ItemTemplate>comparingDouble(t->stigmaScore(player,role,t)).reversed().thenComparingInt(ItemTemplate::getTemplateId)).toList();
  Map<Long,ItemTemplate> plan=new LinkedHashMap<>();
  for(var slot:slots)for(var t:choices)if((t.getItemSlot() & slot.getSlotIdMask())!=0 && Arrays.stream(t.getStigma().getGainSkillGroups()).noneMatch(used::contains)){plan.put(slot.getSlotIdMask(),t);used.addAll(Arrays.asList(t.getStigma().getGainSkillGroups()));break;}
  for(var old:List.copyOf(player.getEquipment().getEquippedItemsAllStigma())){var expected=plan.get(old.getEquipmentSlot());if(expected==null || expected.getTemplateId()!=old.getItemId())player.getEquipment().unEquipItem(old.getObjectId(),false);}
  for(var entry:plan.entrySet()) {
   if(player.getEquipment().getEquippedItemsAllStigma().stream().anyMatch(i->i.getEquipmentSlot()==entry.getKey() && i.getItemId()==entry.getValue().getTemplateId()))continue;
   if(player.getInventory().isFull())break;
   Item item=player.getInventory().getItems().stream().filter(i->i.getItemId()==entry.getValue().getTemplateId()).findFirst().orElse(null);
   if(item==null){item=ItemFactory.newItem(entry.getValue().getTemplateId(),1);if(item==null)throw new IllegalStateException("Missing Stigma template");state.created.add(item.getObjectId());player.getInventory().onLoadHandler(item);}
   if(player.getEquipment().equipItem(item.getObjectId(),entry.getKey())==null)throw new IllegalStateException("Native Temporary Stigma equip rejected: "+item.getItemId());
  }
  StigmaService.onPlayerLogin(player);
 }
 static void tick(PlayerBotSession session,boolean gear){
  if(!session.generated())return;
  Player bot=session.bot(),owner=session.owner();State state=STATES.computeIfAbsent(bot.getObjectId(),id->new State());long now=System.currentTimeMillis();if(now<state.next)return;state.next=now+2000;
  if(bot.isDead() || owner.isDead() || bot.isCasting() || bot.isLooting() || bot.getController().isInCombat() || owner.getController().isInCombat())return;
  int level=Math.min(65,Math.max(1,owner.getLevel()));boolean changed=state.level!=level || bot.getLevel()!=level || state.role!=session.combatRole();boolean updateGear=gear && (state.tier!=tier(level) || state.role!=session.combatRole());
  if(!changed && !updateGear)return;
  if(bot.getPlayerClass().isStartingClass() && level>=10){bot.getCommonData().setPlayerClass(switch(bot.getPlayerClass()){case WARRIOR->session.combatRole()==Role.TANK ? PlayerClass.TEMPLAR : PlayerClass.GLADIATOR;case SCOUT->session.combatRole()==Role.RANGED ? PlayerClass.RANGER : PlayerClass.ASSASSIN;case MAGE->PlayerClass.SORCERER;case PRIEST->session.combatRole()==Role.SUPPORT ? PlayerClass.CHANTER : PlayerClass.CLERIC;case ENGINEER->session.combatRole()==Role.TANK || session.combatRole()==Role.MELEE ? PlayerClass.RIDER : PlayerClass.GUNNER;case ARTIST->PlayerClass.BARD;default->bot.getPlayerClass();});bot.getCommonData().setDaeva(true);updateGear=gear;}
  double hp=bot.getLifeStats().getCurrentHp()/(double)Math.max(1,bot.getLifeStats().getMaxHp()),mp=bot.getLifeStats().getCurrentMp()/(double)Math.max(1,bot.getLifeStats().getMaxMp());
  if(bot.getLevel()!=level){bot.getCommonData().setLevel(level);bot.getGameStats().updateStatsTemplate();bot.getLifeStats().setCurrentHp((int)Math.round(bot.getLifeStats().getMaxHp()*hp));bot.getLifeStats().setCurrentMp((int)Math.round(bot.getLifeStats().getMaxMp()*mp));}
  state.created.addAll(PlayerBotGearPolicy.state(session).generated);
  build(bot,session.combatRole(),updateGear);
  var policy=PlayerBotGearPolicy.state(session);policy.generated.clear();policy.generated.addAll(state.created);policy.save();
  PlayerBotQuestSync.notice(bot,"Temporary Bot maintained: level "+bot.getLevel()+", "+session.combatRole()+", gear tier "+tier(level)+" (quality ceiling "+quality(level)+"); native skills and Stigmas updated.","temporary-build",10000);
 }
 static void close(PlayerBotSession session){STATES.remove(session.bot().getObjectId());}
 static void release(Player player){STATES.remove(player.getObjectId());}
 private PlayerBotTemporary(){}
}
