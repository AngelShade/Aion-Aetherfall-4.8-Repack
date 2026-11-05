package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.skillengine.properties.*;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.skillengine.effect.EffectType.*;

/** Native tactical adapters. Immutable metadata is shared; deployment leases belong to the session. */
final class PlayerBotSkillAdapters {
 enum Family { NONE,TRAP,TOTEM,SERVANT,AREA,MOVEMENT,HIDE,PROC,ONETIME,EXCHANGE,RESET }
 private static final Map<SkillTemplate,Family> FAMILIES=new ConcurrentHashMap<>();
 private static final Map<SkillTemplate,List<SkillTemplate>> PAYLOADS=new ConcurrentHashMap<>();
 private static final Map<SkillTemplate,Float> RADII=new ConcurrentHashMap<>();
 static Family family(SkillTemplate t) { return FAMILIES.computeIfAbsent(t,PlayerBotSkillAdapters::detect); }
 private static Family detect(SkillTemplate t) {
  if(t.hasAnyEffect(DASH,BACKDASH,MOVEBEHIND,RANDOMMOVELOC))return Family.MOVEMENT;
  if(t.hasAnyEffect(SUMMONTRAP))return Family.TRAP;
  if(t.hasAnyEffect(SUMMONTOTEM))return Family.TOTEM;
  if(t.hasAnyEffect(SUMMONSERVANT,SUMMONHOMING))return Family.SERVANT;
  if(t.hasAnyEffect(SUMMONSKILLAREA))return Family.AREA;
  if(t.hasAnyEffect(HIDE))return Family.HIDE;
  if(t.getProperties()!=null && t.getProperties().getTargetRelation()==TargetRelationAttribute.FRIEND) {
   if(t.hasAnyEffect(PROVOKER) && !t.hasAnyEffect(SHIELD,MPSHIELD,LIMITEDREDUCEDAMAGE,PROTECT,ALWAYSDODGE,ALWAYSPARRY,ALWAYSBLOCK,ALWAYSRESIST,
    STATUP,STATBOOST,WEAPONSTATUP,WEAPONSTATBOOST,AURA,BOOSTHEAL,BOOSTSPELLATTACK,BOOSTHATE,BOOSTSKILLCASTINGTIME,REFLECTOR,REBIRTH))return Family.PROC;
   if(t.hasAnyEffect(ONETIMEBOOSTSKILLATTACK,ONETIMEBOOSTSKILLCRITICAL))return Family.ONETIME;
   if(t.hasAnyEffect(SWITCHHPMP))return Family.EXCHANGE;
   if(t.hasAnyEffect(SKILLCOOLTIMERESET))return Family.RESET;
  }
  return Family.NONE;
 }
 static SkillKind classify(SkillTemplate t) {
  return switch(family(t)) {
   case TRAP -> t.getProperties().getFirstTarget()==FirstTargetAttribute.POINT && t.getProperties().getTargetRelation()==TargetRelationAttribute.FRIEND ? SkillKind.UNSUPPORTED : SkillKind.DEPLOY;
   case TOTEM,SERVANT,AREA -> SkillKind.DEPLOY;
   case MOVEMENT -> SkillKind.MOBILITY;
   // Cloaking Word snares the whole party. It is not a solo opener or a following aid.
   case HIDE -> t.hasAnyEffect(SNARE) ? SkillKind.UNSUPPORTED : SkillKind.STEALTH;
   case PROC,ONETIME,EXCHANGE,RESET -> SkillKind.COMBAT_BUFF;
   default -> SkillKind.UNSUPPORTED;
  };
 }
 static SkillKind delayed(SkillTemplate t) {
  if(DataManager.SKILL_DATA==null || t.getEffects()==null)return SkillKind.UNSUPPORTED;
  for(var effect:t.getEffects().getEffects())if(effect instanceof DelayedSkillEffect delayed) {
   var next=DataManager.SKILL_DATA.getSkillTemplate(delayed.getSkillId());
   if(next!=null && next!=t && !next.hasAnyEffect(DELAYEDSKILL))return next.hasAnyEffect(PROCATKINSTANT)?SkillKind.DAMAGE:PlayerBotSkills.classify(next);
  }
  return SkillKind.UNSUPPORTED;
 }
 static boolean managed(PlayerBotSkills.Entry e) { return !(e.kind()==SkillKind.BUFF && PlayerBotAethertech.chargingActivation(e.template())) && family(e.template())!=Family.NONE; }
 static boolean deploy(SkillTemplate t) { return t.hasAnyEffect(SUMMONTRAP,SUMMONTOTEM,SUMMONSERVANT,SUMMONHOMING,SUMMONSKILLAREA); }
 static String key(SkillTemplate t) { return t.getGroup()==null ? Integer.toString(t.getSkillId()) : t.getGroup(); }
 static long lifetime(SkillTemplate t) {
  return t.getEffects().getEffects().stream().filter(e->e instanceof SummonEffect).mapToLong(e->
   e instanceof SummonHomingEffect ? 15000 : Math.max(0,((SummonEffect)e).getTime())*1000L+3000).max().orElse(0);
 }
 /** Child NPC skills determine area safety even when the stone itself targets FRIEND. */
 static List<SkillTemplate> payloads(SkillTemplate t) {
  if(DataManager.NPC_SKILL_DATA==null || DataManager.SKILL_DATA==null)return List.of();
  return PAYLOADS.computeIfAbsent(t,s->s.getEffects().getEffects().stream().filter(e->e instanceof SummonEffect)
   .map(e->(SummonEffect)e).flatMap(e->DataManager.NPC_SKILL_DATA.getNpcSkillTemplates(e.getNpcId()).stream())
   .map(e->DataManager.SKILL_DATA.getSkillTemplate(e.getSkillId())).filter(Objects::nonNull).distinct().toList());
 }
 static float radius(SkillTemplate t) {
  return RADII.computeIfAbsent(t,k->(float)payloads(k).stream().mapToDouble(s->{var p=s.getProperties();return p==null?0:
   Math.max(p.getFirstTargetRange(),Math.max(p.getEffectiveRange(),Math.max(p.getEffectiveDist(),p.getTargetDistance())));}).max().orElse(0));
 }
 static boolean offensive(SkillTemplate t) {
  return payloads(t).stream().anyMatch(s->s.getProperties()!=null && s.getProperties().getTargetRelation()==TargetRelationAttribute.ENEMY);
 }
 static boolean useful(Family family,boolean fight,boolean duty,double health,double mana,double targetHealth,double distance,boolean pressure,boolean ranged) {
  if(family==Family.NONE)return true;
  if(!fight || !Double.isFinite(distance) || distance<0)return false;
  return switch(family) {
   case TRAP -> targetHealth>=25 && distance<=8;
   case TOTEM,SERVANT,AREA -> targetHealth>=40 && distance<=30;
   case MOVEMENT -> ranged ? pressure && distance<8 : health>=35 && distance>5 && distance<=25;
   case HIDE -> !duty && (ranged ? pressure && health<45 : !pressure && health>=55 && targetHealth>=65);
   case PROC,ONETIME -> health>=55 && mana>=35 && targetHealth>=30 && distance<=(ranged?25:8);
   case EXCHANGE -> true; // Raw values and caps, not percentages, are checked by exchange().
   case RESET -> targetHealth>=40 && mana>=35;
   default -> true;
  };
 }
 static boolean reserve(SkillTemplate t,boolean fight,boolean emergency,boolean durable,double targetHealth) {
  return t.getCooldown()*100L>=Math.max(1,PlayerBotConfig.SKILL_LONG_COOLDOWN_SECONDS)*1000L
   && (!fight || !emergency && (!durable || targetHealth<40));
 }
 static boolean exchange(int hp,int maxHp,int mp,int maxMp) {
  if(maxHp<=0 || maxMp<=0)return false;
  int nextHp=Math.min(maxHp,mp),nextMp=Math.min(maxMp,hp);
  return nextHp>=maxHp*.55 && nextMp>=maxMp*.25
   && (hp<maxHp*.35 && nextHp-hp>=maxHp*.2 || mp<maxMp*.2 && nextMp-mp>=maxMp*.2);
 }
 static boolean reset(Player bot,SkillTemplate t,List<PlayerBotSkills.Entry> skills) {
  long now=System.currentTimeMillis();
  for(var effect:t.getEffects().getEffects())if(effect instanceof SkillCooltimeResetEffect reset)
   for(var e:skills)if((e.kind()==SkillKind.DAMAGE || e.kind()==SkillKind.MOBILITY) && e.template()!=t
    && e.template().getCooldownId()>=reset.getFirstCd() && e.template().getCooldownId()<=reset.getLastCd()
    && bot.getSkillCoolDown(e.template().getCooldownId())-now>3000)return true;
  return false;
 }
 static boolean proc(Player bot,SkillTemplate t,Creature enemy) {
  for(var effect:t.getEffects().getEffects())if(effect instanceof ProvokerEffect p) {
   return switch(p.getHitType()) {
    case NMLATK -> PositionUtil.isInAttackRange(bot,enemy,Math.max(2,bot.getGameStats().getAttackRange().getCurrent()/1000f));
    case BACKATK -> PositionUtil.isBehind(bot,enemy);
    case MAHIT -> enemy.getTarget()==bot && enemy.isCasting();
    default -> enemy.getTarget()==bot;
   };
  }
  return true;
 }
 static boolean allowed(Player bot,PlayerBotSkills.Entry e,Creature enemy,boolean duty,List<PlayerBotSkills.Entry> skills) {
  var t=e.template();var f=family(t);
  if(f==Family.NONE && !t.hasAnyEffect(MAGICCOUNTERATK,HEALCASTORONATTACKED))return true;
  boolean fight=enemy!=null && enemy.isSpawned() && !enemy.isDead() && bot.isEnemy(enemy)
   && enemy.getWorldId()==bot.getWorldId() && enemy.getInstanceId()==bot.getInstanceId() && bot.getKnownList().sees(enemy);
  boolean emergency=PlayerBotDefense.health(bot)<35;
  boolean durable=fight && (enemy instanceof Player || enemy.getLifeStats().getMaxHp()>=bot.getLifeStats().getMaxHp()*1.5);
  if(f!=Family.NONE && reserve(t,fight,emergency,durable,fight?PlayerBotDefense.health(enemy):0))return false;
  if(f==Family.NONE && !t.hasAnyEffect(MAGICCOUNTERATK,HEALCASTORONATTACKED))return true;
  boolean ranged=PlayerBotCombatPosition.ranged(bot.getPlayerClass(),PlayerBotRules.roleFor(bot.getPlayerClass()));
  if(f==Family.MOVEMENT && ranged && !PlayerBotSkillPosition.retreat(t))return false;
  if(!useful(f,fight,duty,PlayerBotDefense.health(bot),100.0*bot.getLifeStats().getCurrentMp()/Math.max(1,bot.getLifeStats().getMaxMp()),
   fight?PlayerBotDefense.health(enemy):0,fight?PositionUtil.getDistance(bot,enemy):0,fight && enemy.getTarget()==bot,f==Family.MOVEMENT?PlayerBotSkillPosition.retreat(t):ranged))return false;
  if(f==Family.EXCHANGE)return exchange(bot.getLifeStats().getCurrentHp(),bot.getLifeStats().getMaxHp(),bot.getLifeStats().getCurrentMp(),bot.getLifeStats().getMaxMp());
  if(f==Family.RESET)return reset(bot,t,skills);
  if(f==Family.PROC)return proc(bot,t,enemy);
  if(t.hasAnyEffect(MAGICCOUNTERATK))return fight && enemy.isCasting();
  if(t.hasAnyEffect(HEALCASTORONATTACKED))return fight && PlayerBotDefense.health(bot)<85;
  return true;
 }
 static double priority(Player bot,Role role,PlayerBotSkills.Entry e,Creature enemy) {
  if(!managed(e)) {
   if(e.template().hasAnyEffect(MAGICCOUNTERATK))return 22;
   if(e.template().hasAnyEffect(HEALCASTORONATTACKED))return 23;
   if(e.template().hasAnyEffect(BIND,BLIND,CURSE,SLOW))return enemy.getTarget()==bot?22:16;
   return Double.NaN;
  }
  double fallback=switch(family(e.template())) {
   case TRAP,TOTEM,SERVANT,AREA -> 26;
   case MOVEMENT -> PlayerBotEngine.MOVE+3;
   case HIDE -> 28;
   case PROC,ONETIME -> 25;
   case EXCHANGE -> PlayerBotEngine.EMERGENCY+2;
   case RESET -> 24;
   default -> 0;
  };
  double score=switch(bot.getPlayerClass()) {
   case ASSASSIN -> PlayerBotAssassin.adapter(bot,role,e,enemy,fallback);
   case GLADIATOR -> PlayerBotGladiator.adapter(bot,role,e,enemy,fallback);
   case TEMPLAR -> PlayerBotTemplar.adapter(bot,role,e,enemy,fallback);
   case SORCERER -> PlayerBotSorcerer.adapter(bot,role,e,enemy,fallback);
   case SPIRIT_MASTER -> PlayerBotSpiritmaster.adapter(bot,role,e,enemy,fallback);
   case CLERIC -> PlayerBotCleric.adapter(bot,role,e,enemy,fallback);
   case CHANTER -> PlayerBotChanter.adapter(bot,role,e,enemy,fallback);
   default -> PlayerBotClassRotations.adapter(bot,role,e,enemy,fallback);
  };
  if(!Double.isNaN(score))return score;
  return fallback;
 }
 private PlayerBotSkillAdapters() {}
}
