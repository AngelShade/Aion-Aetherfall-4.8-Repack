package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.controllers.effect.EffectController;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.geoEngine.collision.IgnoreProperties;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.world.geo.GeoService;

/** Count actual Session/native-planner work on inert actors; no casts, World, tasks, SQL or IDs. */
public final class PlayerBotUtilityPlanningCheck {
 static int checks;
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static final class Probe extends GeoMap {
  int calls;boolean clear=true;
  Probe(){super(1);}
  @Override public boolean canSee(float x,float y,float z,float tx,float ty,float tz,int instance,IgnoreProperties ignore){calls++;return clear;}
 }
 static final class Enemy extends PlayerBotQuestObjectivesCheck.Contact {
  EffectController effects;
  @Override public EffectController getEffectController(){return effects;}
  @Override public boolean isCasting(){return false;}
  @Override public NpcTemplate getObjectTemplate(){throw new AssertionError("An enemy with no purge/interrupt benefit requested native range/sight admission");}
 }
 static Creature recipient(PlayerBotSession session,int id,List<Player> party,boolean combat)throws Exception {
  var m=PlayerBotSession.class.getDeclaredMethod("recipient",PlayerBotSkills.Entry.class,List.class,Npc.class,boolean.class);m.setAccessible(true);
  return (Creature)m.invoke(session,PlayerBotSupportCooperationCheck.entry(id),party,null,combat);
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);
  var oldData=DataManager.SKILL_DATA;var oldMaterial=DataManager.MATERIAL_DATA;boolean oldSight=GeoDataConfig.CANSEE_ENABLE;
  var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
    <skill_template skill_id="991801" activation="ACTIVE" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><mphealinstant value="250" e="1"/></effects></skill_template>
    <skill_template skill_id="991802" activation="ACTIVE" skilltype="MAGICAL" stack="UTILITY_BUFF"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><endconditions><mp value="100"/></endconditions><effects><statup e="1" duration2="10000"><change stat="PHYSICAL_ATTACK" value="10"/></statup></effects></skill_template>
    <skill_template skill_id="991803" activation="ACTIVE" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><resurrect value="50" e="1"/></effects></skill_template>
    <skill_template skill_id="991804" activation="ACTIVE" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="ENEMY" target_type="ONLYONE" first_target_range="25"/><effects><dispelbuff value="1" power="100" dispel_level="65" e="1"/></effects></skill_template>
   </skill_data>
   """));
  DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();
  PlayerBotSupportCooperationCheck.unsafe=u;PlayerBotSupportCooperationCheck.data=data;
  var bot=PlayerBotSupportCooperationCheck.actor(1999999281);var ally=PlayerBotSupportCooperationCheck.actor(1999999282);ally.x=5;bot.nearby.add(ally);
  var session=PlayerBotSupportCooperationCheck.session(bot,bot);List<Player> party=List.of(bot,ally);var probe=new Probe();
  f=GeoService.class.getDeclaredField("geoMaps");f.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)f.get(GeoService.getInstance());var oldMap=maps.put(1,probe);GeoDataConfig.CANSEE_ENABLE=true;
  try {
   check(recipient(session,991801,party,false)==null,"Full mana has no restoration recipient");
   check(probe.calls==0,"Full mana rejects before native geometry; installed055 repeats sight here");
   check(recipient(session,991803,party,false)==null && probe.calls==0,"Living roster rejects resurrection before geometry");
   ally.life.mp=200;
   check(recipient(session,991801,party,false)==ally && probe.calls==1,"Useful mana restoration retains one fresh native sight probe");
   probe.clear=false;
   check(recipient(session,991801,party,false)==null && probe.calls==2,"A newly closed obstacle rejects useful assistance");probe.clear=true;
   ally.life.mp=1000;bot.life.mp=900;PlayerBotRecoveryIntent.start(bot);
   check(recipient(session,991801,party,false)==bot,"Standing self recovery retains95 percent hysteresis");PlayerBotRecoveryIntent.clear(bot);bot.life.mp=1000;
   var buff=PlayerBotSupportCooperationCheck.entry(991802);
   bot.effects.observed.add(new Effect(bot,bot,buff.template(),1));ally.effects.observed.add(new Effect(bot,ally,buff.template(),1));
   int before=probe.calls;
   check(recipient(session,991802,party,false)==null && probe.calls==before,"Existing buffs reject before geometry");
   ally.effects.observed.clear();
   check(recipient(session,991802,party,false)==ally && probe.calls==before+1,"Missing buff retains legal native recipient");
   bot.life.mp=0;before=probe.calls;
   check(recipient(session,991802,party,false)==null && probe.calls==before,"Unaffordable buff rejects before geometry");bot.life.mp=1000;
   ally.dead=true;
   check(recipient(session,991803,party,false)==ally,"Actual friendly corpse remains eligible");ally.dead=false;
   for(int i=0;i<25;i++) {
    before=probe.calls;check(recipient(session,991801,party,false)==null && probe.calls==before,"Repeated healthy decisions do not repeat terrain work or retain a prior useful result");
   }
   var enemy=(Enemy)u.allocateInstance(Enemy.class);enemy.spawned=true;enemy.effects=new EffectController(enemy){@Override public List<Effect> getAbnormalEffects(){return List.of();}};
   check(PlayerBotEnemyUtility.utility(bot,PlayerBotSupportCooperationCheck.entry(991804),enemy)==0,"Enemy with no removable aura skips native admission");
   check(bot.life.mp==1000 && ally.life.mp==1000,"Planning pays no native resources");
   check(bot.effects.observed.size()==1 && ally.effects.observed.isEmpty(),"Planning changes no native effect custody");
  } finally {
   PlayerBotRecoveryIntent.clear(bot);DataManager.SKILL_DATA=oldData;DataManager.MATERIAL_DATA=oldMaterial;GeoDataConfig.CANSEE_ENABLE=oldSight;
   if(oldMap==null)maps.remove(1);else maps.put(1,oldMap);
  }
  System.out.println("OK: "+checks+" useful-first actual Session planning, fresh visibility and native custody checks");
 }
 private PlayerBotUtilityPlanningCheck(){}
}
