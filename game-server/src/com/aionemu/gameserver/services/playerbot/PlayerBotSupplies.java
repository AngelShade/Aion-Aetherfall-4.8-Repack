package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.services.item.ItemFactory;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;

/** Native consumable effects, build scoring, and bounded supplies for Temporary Bots only. */
public final class PlayerBotSupplies {
 private static final Map<Integer,Long> NEXT=new ConcurrentHashMap<>();
 static SkillTemplate skill(ItemTemplate item) {
  var actions=item.getActions();if(actions==null || actions.getItemActions().size()!=1 || actions.getSkillUseAction()==null)return null;
  return DataManager.SKILL_DATA.getSkillTemplate(actions.getSkillUseAction().getSkillId());
 }
 static boolean safe(SkillTemplate skill) {
  if(skill==null || skill.isPassive() || skill.isToggle() || skill.getProperties()==null || skill.getProperties().getFirstTarget()!=FirstTargetAttribute.ME
   || skill.getEffects()==null || skill.getEffects().getEffects().isEmpty())return false;
  return skill.getEffects().getEffects().stream().allMatch(e->e.getValue()>=0 && (e instanceof HealEffect || e instanceof HealInstantEffect || e instanceof ProcHealInstantEffect || e instanceof MPHealEffect || e instanceof MPHealInstantEffect || e instanceof ProcMPHealInstantEffect
   || e instanceof DispelDebuffEffect || e instanceof DispelDebuffMentalEffect || e instanceof DispelDebuffPhysicalEffect
   || e instanceof StatupEffect && e.getChange()!=null && e.getChange().stream().allMatch(c->c.getStat()!=null && beneficial(c.getStat(),c.getValue()+c.getDelta()*skill.getLvl()))));
 }
 static boolean beneficial(StatEnum stat,int value){return value!=0 && (stat.getSign()<0 ? value<0 : value>0);}
 static boolean physical(Player bot) {
  var weapon=bot.getEquipment().getMainHandWeapon();
  return weapon!=null ? !Set.of("ORB","SPELLBOOK","HARP","GUN","CANNON","KEYBLADE").contains(weapon.getItemTemplate().getItemGroup().name())
   : Set.of("WARRIOR","GLADIATOR","TEMPLAR","SCOUT","ASSASSIN","RANGER","CHANTER").contains(bot.getPlayerClass().name());
 }
 static double buffScore(Player bot,SkillTemplate skill) {
  if(!safe(skill))return 0;var role=PlayerBotService.getInstance().combatRole(bot);boolean physical=physical(bot);double score=0;
  for(var e:skill.getEffects().getEffects())if(e instanceof StatupEffect)for(var c:e.getChange()) {
   String stat=c.getStat().name();int value=c.getValue()+c.getDelta()*skill.getLvl();if(!beneficial(c.getStat(),value))continue;
   double weight=Set.of("MAXHP","MAXMP","REGEN_HP","REGEN_MP","SPEED","PHYSICAL_DEFENSE","MAGICAL_DEFEND","MAGICAL_RESIST","PHYSICAL_CRITICAL_RESIST","MAGICAL_CRITICAL_RESIST","FIRE_RESISTANCE","WATER_RESISTANCE","WIND_RESISTANCE","EARTH_RESISTANCE").contains(stat) ? 1 : 0;
   if(physical && Set.of("PHYSICAL_ATTACK","PHYSICAL_CRITICAL","PHYSICAL_ACCURACY","ATTACK_SPEED").contains(stat))weight=3;
   if(!physical && Set.of("BOOST_MAGICAL_SKILL","MAGICAL_CRITICAL","MAGICAL_ACCURACY","BOOST_CASTING_TIME").contains(stat))weight=3;
   if((role==PlayerBotRules.Role.HEALER || role==PlayerBotRules.Role.SUPPORT) && Set.of("HEAL_BOOST","MAXMP","REGEN_MP","BOOST_CASTING_TIME").contains(stat))weight=4;
   if(role==PlayerBotRules.Role.TANK && Set.of("PHYSICAL_DEFENSE","MAGICAL_DEFEND","BLOCK","PARRY","MAXHP","BOOST_HATE").contains(stat))weight=4;
   score+=weight*Math.log1p(Math.abs(value));
  }
  return score;
 }
 static boolean available(Player bot,ItemTemplate t) {
  int min=t.getRequiredLevel(bot.getPlayerClass()),max=t.getMaxLevelRestrict(bot.getPlayerClass());var limits=t.getUseLimits();
  return t.isClassSpecific(bot.getPlayerClass()) && min>=0 && min<=bot.getLevel() && (max==0 || max>=bot.getLevel())
   && (t.getRace()==com.aionemu.gameserver.model.Race.PC_ALL || t.getRace()==bot.getRace())
   && (limits.getGenderPermitted()==null || limits.getGenderPermitted()==bot.getGender()) && safe(skill(t));
 }
 static boolean buffFree(Player bot,SkillTemplate skill) {
  return PlayerBotBuffs.canAdd(bot.getPlayerClass(),skill,bot.getEffectController().getAbnormalEffects().stream().map(Effect::getSkillTemplate).toList());
 }
 static boolean needed(Player bot,SkillTemplate skill) {
  if(!safe(skill) || bot.isDead())return false;
  boolean combat=bot.getController().isInCombat();var owner=com.aionemu.gameserver.world.World.getInstance().getPlayer(bot.getPlayerBotOwnerId());combat|=owner!=null && owner.getController().isInCombat();
  double hp=100.0*bot.getLifeStats().getCurrentHp()/Math.max(1,bot.getLifeStats().getMaxHp()),mp=100.0*bot.getLifeStats().getCurrentMp()/Math.max(1,bot.getLifeStats().getMaxMp());
  boolean recovery=skill.hasAnyEffect(EffectType.HEAL,EffectType.HEALINSTANT,EffectType.PROCHEALINSTANT) && hp<(combat ? 55 : 90)
   || skill.hasAnyEffect(EffectType.MPHEAL,EffectType.MPHEALINSTANT,EffectType.PROCMPHEALINSTANT) && mp<(combat ? 40 : 85)
   || skill.hasAnyEffect(EffectType.DISPELDEBUFF,EffectType.DISPELDEBUFFMENTAL,EffectType.DISPELDEBUFFPHYSICAL)
    && bot.getEffectController().getAbnormalEffects().stream().anyMatch(e->e.getSkillTemplate().getTargetSlot()==SkillTargetSlot.DEBUFF);
  if(recovery)return buffFree(bot,skill);
  return !combat && hp>=55 && mp>=35 && buffScore(bot,skill)>0 && buffFree(bot,skill);
 }
 static double priority(Player bot,Item item) {
  var s=skill(item.getItemTemplate());return (s.hasAnyEffect(EffectType.HEAL,EffectType.HEALINSTANT,EffectType.PROCHEALINSTANT,EffectType.MPHEAL,EffectType.MPHEALINSTANT,EffectType.PROCMPHEALINSTANT,EffectType.DISPELDEBUFF,EffectType.DISPELDEBUFFMENTAL,EffectType.DISPELDEBUFFPHYSICAL) ? 1000 : 0)
   +buffScore(bot,s)+item.getItemTemplate().getLevel();
 }
 static List<Item> candidates(Player bot) {
  provision(bot);
  return bot.getInventory().getItems().stream().filter(i->!i.isEquipped() && i.getItemCount()>0 && !bot.hasCooldown(i) && available(bot,i.getItemTemplate()) && needed(bot,skill(i.getItemTemplate())))
   .sorted(Comparator.<Item>comparingDouble(i->priority(bot,i)).reversed().thenComparingInt(Item::getItemId)).toList();
 }
 static boolean use(Player bot,Item item) {
  if(bot.isDead() || bot.isCasting() || bot.getController().hasTask(TaskId.ITEM_USE) || item!=bot.getInventory().getItemByObjId(item.getObjectId())
   || bot.hasCooldown(item) || !available(bot,item.getItemTemplate()) || !needed(bot,skill(item.getItemTemplate())) || !PlayerRestrictions.canUseItem(bot,item))return false;
  var old=bot.getTarget();bot.setTarget(bot);
  try {
   var action=item.getItemTemplate().getActions().getSkillUseAction();if(!action.canAct(bot,item,null))return false;
   var nativeSkill=SkillEngine.getInstance().getSkill(bot,action.getSkillId(),action.getLevel(),bot,item.getItemTemplate());if(nativeSkill==null)return false;
   bot.getMoveController().abortMove();bot.getObserveController().notifyItemuseObservers(item);nativeSkill.setItemObjectId(item.getObjectId());
   nativeSkill.setClientHitTime(Math.max(0,Math.round(DataManager.MOTION_DATA.calculateAnimationTimeUntilFirstHit(bot,nativeSkill))));return nativeSkill.useSkill();
  }finally{bot.setTarget(old);}
 }
 static String category(Player bot,ItemTemplate t) {
  var s=skill(t);if(!available(bot,t))return "";
  if(s.hasAnyEffect(EffectType.HEAL))return "hp-regen";
  if(s.hasAnyEffect(EffectType.MPHEAL))return "mp-regen";
  if(s.hasAnyEffect(EffectType.HEALINSTANT,EffectType.PROCHEALINSTANT))return "hp";
  if(s.hasAnyEffect(EffectType.MPHEALINSTANT,EffectType.PROCMPHEALINSTANT))return "mp";
  if(s.hasAnyEffect(EffectType.DISPELDEBUFF,EffectType.DISPELDEBUFFMENTAL,EffectType.DISPELDEBUFFPHYSICAL))return "cleanse";
  if(buffScore(bot,s)<=0)return "";
  return "buff:"+(s.getConflictId()!=0 ? "conflict:"+s.getConflictId() : s.getStack()!=null && !s.getStack().isEmpty() ? "stack:"+s.getStack() : "cooldown:"+s.getCooldownId());
 }
 static void provision(Player bot) {
  if(!PlayerBotTemporary.managed(bot) || bot.isDead() || bot.isCasting() || bot.getController().isInCombat())return;
  long now=System.currentTimeMillis();if(now<NEXT.getOrDefault(bot.getObjectId(),0L))return;NEXT.put(bot.getObjectId(),now+60000);
  var best=new TreeMap<String,ItemTemplate>();
  for(var t:DataManager.ITEM_DATA.getItemTemplates()) {
   if(!PlayerBotSupplyCatalog.ordinary(t) || t.getItemType()!=ItemType.NORMAL || t.getItemSlot()!=0 || t.getAcquisition()!=null
    || t.hasAreaRestriction() || t.hasWorldRestrictions() || t.getExpireTime()!=0 || t.getExtraInventoryId()>0 || t.getLevel()>bot.getLevel()
    || t.getUseLimits().getMinRank()>1 || t.getUseLimits().getGuildLevelPermitted()>0 || t.getActions()==null
    || t.getActions().getSkillUseAction()==null || t.getActions().getSkillUseAction().getMapId()!=0)continue;
   String category=category(bot,t);if(category.isEmpty())continue;
   var old=best.get(category);double value=potency(bot,t),before=old==null ? -1 : potency(bot,old);
   if(old==null || value>before || value==before && t.getTemplateId()<old.getTemplateId())best.put(category,t);
  }
  int buffs=0;
  var plan=new ArrayList<>(best.entrySet());
  // Rank native categories by build value before bounding the number of buff stacks.
  for(int i=0;i<plan.size();i++){int strongest=i;for(int j=i+1;j<plan.size();j++)if(potency(bot,plan.get(j).getValue())>potency(bot,plan.get(strongest).getValue()))strongest=j;Collections.swap(plan,i,strongest);}
  for(var entry:plan) {
   if(entry.getKey().startsWith("buff:") && ++buffs>5)continue;
   var t=entry.getValue();long desired=entry.getKey().startsWith("buff:") ? 10 : 20;
   if(bot.getInventory().getItems().stream().anyMatch(i->category(bot,i.getItemTemplate()).equals(entry.getKey()) && i.getItemCount()>=5))continue;
   long existing=bot.getInventory().getItemCountByItemId(t.getTemplateId());long count=Math.min(t.getMaxStackCount(),desired-existing);if(count<=0 || bot.getInventory().isFull())continue;
   // A native, finite stack. Item skill costs consume it; replenish only the dedicated actor.
   var item=ItemFactory.newItem(t.getTemplateId(),count);if(item==null)continue;item.setSoulBound(true);PlayerBotSupplyCatalog.record(bot,item);bot.getInventory().onLoadHandler(item);
  }
 }
 static double potency(Player bot,ItemTemplate t) {
  var s=skill(t);double value=buffScore(bot,s);for(var e:s.getEffects().getEffects())value+=Math.max(0,e.getValue()+e.getDelta()*s.getLvl());return value+t.getLevel()*.01;
 }
 static void close(Player bot){NEXT.remove(bot.getObjectId());}
 private PlayerBotSupplies() {}
}
