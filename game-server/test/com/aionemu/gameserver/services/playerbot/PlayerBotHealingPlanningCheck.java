package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.geoEngine.collision.IgnoreProperties;
import com.aionemu.gameserver.geoEngine.models.GeoMap;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.world.knownlist.KnownList;

/** Real healing planner, counted native geometry endpoint and live rights rechecks.
 * Inert actors only: no casts, World, native IDs, tasks, SQL or paid resources. */
public final class PlayerBotHealingPlanningCheck {
 private static int checks,scans;
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static final class Probe extends GeoMap {
  int calls;boolean clear=true;
  Probe(){super(1);}
  @Override public boolean canSee(float x,float y,float z,float tx,float ty,float tz,int instance,IgnoreProperties ignore){calls++;return clear;}
 }
 public static void main(String[] args)throws Exception{
  var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(Unsafe)field.get(null);
  var oldData=DataManager.SKILL_DATA;var oldMaterial=DataManager.MATERIAL_DATA;boolean oldCanSee=GeoDataConfig.CANSEE_ENABLE;
  var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new StringReader("""
   <skill_data>
   <skill_template skill_id="991751" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGETORME" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="250" e="1"/></effects></skill_template>
   <skill_template skill_id="991752" activation="ACTIVE" duration="0" skilltype="MAGICAL"><properties first_target="TARGET" target_relation="MYPARTY" target_type="ONLYONE"/><effects><healinstant value="250" e="1"/></effects></skill_template>
   </skill_data>
   """));
  DataManager.SKILL_DATA=data;DataManager.MATERIAL_DATA=new MaterialData();
  PlayerBotSupportCooperationCheck.unsafe=unsafe;PlayerBotSupportCooperationCheck.data=data;
  var bot=PlayerBotSupportCooperationCheck.actor(1999999251);var target=PlayerBotSupportCooperationCheck.actor(1999999252);target.x=5;
  bot.nearby.add(target);bot.seen=new KnownList(bot){
   @Override public boolean sees(VisibleObject o){return bot.nearby.contains(o);}
   @Override public void forEachPlayer(java.util.function.Consumer<Player> c){scans++;List.copyOf(bot.nearby).forEach(c);}
  };
  var heal=PlayerBotSupportCooperationCheck.entry(991751);var partyHeal=PlayerBotSupportCooperationCheck.entry(991752);
  List<Player> snapshot=List.of(bot,target);var probe=new Probe();
  var mapsField=GeoService.class.getDeclaredField("geoMaps");mapsField.setAccessible(true);
  @SuppressWarnings("unchecked") var maps=(Map<Integer,GeoMap>)mapsField.get(GeoService.getInstance());
  GeoMap oldMap=maps.put(1,probe);GeoDataConfig.CANSEE_ENABLE=true;
  try{
   check(PlayerBotHealing.recipient(bot,heal,snapshot)==null,"native full-health heal selection has no useful recipient");
   if(args.length>0&&args[0].equals("old")){
    check(probe.calls>0,"REPRO: old full-health planning performed unnecessary native geometry probes");
    target.life.hp=250;check(PlayerBotHealing.useful(bot,heal,target),"old live support heal remains useful");
    check(scans>0,"REPRO: old useful recheck rescans known allies and rebuilds support membership");
    System.out.println("OK: "+checks+" installed050 redundant support and geometry work reproduced");return;
   }
   check(probe.calls==0,"full-health single-target healing invokes zero native geometry probes");
   target.life.hp=250;check(PlayerBotHealing.recipient(bot,heal,snapshot)==target,"wounded legal ally retains actual native recipient choice");
   check(probe.calls>0,"useful recipient still proves native line of sight");
   int hp=target.life.hp,mp=bot.life.mp;
   for(int i=0;i<100;i++)check(PlayerBotHealing.useful(bot,heal,target,snapshot),"provided membership retains useful heal while reading fresh mutable state");
   check(scans==0,"provided membership never rescans or sorts known allies during repeated cast usefulness");
   check(target.life.hp==hp&&bot.life.mp==mp,"planning never heals or pays native spell costs");
   target.life.hp=1000;check(!PlayerBotHealing.useful(bot,heal,target,snapshot),"ally healed since discovery is rejected by fresh health recheck");
   target.life.hp=250;target.hostile=true;check(!PlayerBotHealing.useful(bot,heal,target,snapshot),"hostility changed after discovery rejects stale supplied target");target.hostile=false;
   target.spawned=false;check(!PlayerBotHealing.useful(bot,heal,target,snapshot),"despawned supplied target is rejected");target.spawned=true;
   target.playing=false;check(!PlayerBotHealing.useful(bot,heal,target,snapshot),"offline supplied target is rejected");target.playing=true;
   target.world=2;check(!PlayerBotHealing.useful(bot,heal,target,snapshot),"transferred supplied target is rejected");target.world=1;
   target.instance=2;check(!PlayerBotHealing.useful(bot,heal,target,snapshot),"changed instance rejects supplied target");target.instance=1;
   target.x=41;check(!PlayerBotHealing.useful(bot,heal,target,snapshot),"public support range is rechecked");target.x=5;
   bot.nearby.clear();check(!PlayerBotHealing.useful(bot,heal,target,snapshot),"removed known public target cannot be authorized by old membership");bot.nearby.add(target);
   check(!PlayerBotHealing.useful(bot,heal,target,List.of(bot)),"a supplied list cannot invent a missing beneficiary");
   var group=(PlayerBotClericCheck.Group)unsafe.allocateInstance(PlayerBotClericCheck.Group.class);group.members=List.of(bot,target);bot.group=target.group=group;
   check(PlayerBotHealing.useful(bot,partyHeal,target,snapshot),"native party-only heal remains useful for actual member");
   bot.group=target.group=null;check(!PlayerBotHealing.useful(bot,partyHeal,target,snapshot),"native party membership removal rejects stale party-only cast");
   probe.clear=false;check(PlayerBotHealing.recipient(bot,heal,snapshot)==null,"useful but obstructed ally retains native LOS rejection");probe.clear=true;
   check(scans==0,"new planning and admission checks do not hide a known-list rescan");
   var session=PlayerBotSupportCooperationCheck.session(bot,bot);
   var castType=Class.forName(PlayerBotSession.class.getName()+"$CastAction");
   var castCtor=castType.getDeclaredConstructor(PlayerBotSession.class,PlayerBotSkills.Entry.class,Creature.class,List.class);castCtor.setAccessible(true);
   var action=(PlayerBotEngine.Action)castCtor.newInstance(session,heal,target,List.of());
   var bind=castType.getDeclaredMethod("withSupport",List.class);bind.setAccessible(true);
   check(bind.invoke(action,snapshot)==action,"actual current CastAction retains its original constructor and binds decision membership");
   check(action.isUseful()&&scans==0,"actual Session cast usefulness consumes bound support without rediscovery");
   target.life.hp=1000;check(!action.isUseful(),"actual bound CastAction rejects ally healed since planning");target.life.hp=250;
   target.hostile=true;check(!action.isUseful(),"actual bound CastAction rejects changed hostility");target.hostile=false;
   check(scans==0,"actual cast mutable-state rechecks retain zero known-list rescans");
   check(PlayerBotHealing.useful(bot,heal,target),"legacy original three-argument usefulness remains callable and functional");
   check(scans==1,"legacy entry retains its fresh discovery contract");
  }finally{
   if(oldMap==null)maps.remove(1);else maps.put(1,oldMap);GeoDataConfig.CANSEE_ENABLE=oldCanSee;DataManager.SKILL_DATA=oldData;DataManager.MATERIAL_DATA=oldMaterial;
   PlayerBotRecoveryIntent.clear(bot);PlayerBotRecoveryIntent.clear(target);
  }
  System.out.println("OK: "+checks+" native healing membership reuse, geometry admission and stale-state checks");
 }
}
