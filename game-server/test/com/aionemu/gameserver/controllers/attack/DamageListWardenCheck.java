package com.aionemu.gameserver.controllers.attack;

import java.lang.reflect.Field;
import java.util.*;
import com.aionemu.commons.configuration.Property;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.controllers.NpcController;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.alliance.PlayerAlliance;
import com.aionemu.gameserver.model.team.legion.Legion;
import com.aionemu.gameserver.model.team.league.League;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.world.knownlist.KnownList;
import sun.misc.Unsafe;

/** Exercises the native final-damage entry point with inert actors; no world, threads or SQL. */
public final class DamageListWardenCheck {
 private static int checks;
 private static final Unsafe UNSAFE;
 static {try {var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);UNSAFE=(Unsafe)f.get(null);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 private static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 private static Field field(Class<?> type,String name)throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 private static <T> T actor(Class<T> type,int id)throws Exception {T actor=type.cast(UNSAFE.allocateInstance(type));field(AionObject.class,"objectId").setInt(actor,id);return actor;}
 private static void defaults(Class<?> type)throws Exception {for(var f:type.getFields()){var p=f.getAnnotation(Property.class);if(p==null)continue;if(f.getType()==int.class)f.setInt(null,Integer.parseInt(p.defaultValue()));else if(f.getType()==boolean.class)f.setBoolean(null,Boolean.parseBoolean(p.defaultValue()));}}
 private static final class Traveller extends Player {
  PlayerAlliance alliance;Legion company;
  private Traveller(){super(null,null);}
  @Override public PlayerAlliance getPlayerAlliance(){return alliance;}
  @Override public boolean isLegionMember(){return company!=null;}
  @Override public Legion getLegion(){return company;}
  @Override public Race getRace(){return Race.ELYOS;}
 }
 private static final class Alliance extends PlayerAlliance {
  List<Player> travellers;
  private Alliance(){super(null,null);}
  @Override public List<Player> getMembers(){return travellers;}
 }
 private static final class SharedLeague extends League {
  List<PlayerAlliance> alliances;
  private SharedLeague(){super(null);}
  @Override public List<PlayerAlliance> getMembers(){return alliances;}
 }
 private static final class PetDamage extends Npc {
  Creature master;
  private PetDamage(){super(null,null,null);}
  @Override public Creature getMaster(){return master;}
 }
 private static AggroInfo damage(Creature actor,int amount){var info=new AggroInfo(actor);info.addDamage(amount,1000);return info;}
 private static DamageList list(Npc owner,AggroInfo... entries){return new DamageList(List.of(entries),owner);}
 @SuppressWarnings({"unchecked","rawtypes"})
 private static void warden(Traveller warden)throws Exception {
  var type=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotRoadDuty$State");var state=UNSAFE.allocateInstance(type);
  var career=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotRoadDuty$Career");field(type,"career").set(state,Enum.valueOf((Class)career,"WARDEN"));
  var pointType=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotNavigation$Point");
  var pointConstructor=pointType.getDeclaredConstructor(float.class,float.class,float.class);pointConstructor.setAccessible(true);var point=pointConstructor.newInstance(500f,500f,100f);
  var routeType=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotPatrolRoutes$Route");
  var routeConstructor=routeType.getDeclaredConstructor(String.class,int.class,Race.class,int.class,int.class,String.class,List.class,Map.class,boolean.class);routeConstructor.setAccessible(true);
  var route=routeConstructor.newInstance("beat:check",210020000,Race.ELYOS,25,1,"Check",List.of(point,point),Map.of(),true);
  field(type,"route").set(state,route);
  ((Map)field(PlayerBotRoadDuty.class,"STATES").get(null)).put(warden.getObjectId(),state);
  warden.company=actor(Legion.class,900);
  ((Map)field(Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotWardenCompanies"),"REGIONS").get(null)).put(900,"210020000:ELYOS");
 }
 public static void main(String[] args)throws Exception {
  if(!DamageListWardenCheck.class.desiredAssertionStatus())throw new IllegalArgumentException("Use -ea");
  defaults(PlayerBotWardenConfig.class);defaults(PlayerBotMerchantConfig.class);PlayerBotConfig.ROAD_DUTY=true;
  var human=actor(Traveller.class,1);var friend=actor(Traveller.class,2);var observer=actor(Traveller.class,3);var warden=actor(Traveller.class,4);warden(warden);
  var monster=actor(Npc.class,5);monster.setKnownlist(new KnownList(monster){@Override public boolean knows(VisibleObject ignored){return true;}});
  check(PlayerBotRoadDuty.publicWarden(warden),"Native road-duty owner recognizes assigned Warden");
  var unfair=list(monster,damage(human,10),damage(warden,90));
  check(unfair.getTotalDamage()==10 && unfair.getCreatureDamages().size()==1,"Warden claims excluded while traveller damage retained");
  check(!unfair.isExperienceEligible(human),"Ten percent effort withholds XP/bounty");
  check(unfair.getMostDamage().getAttacker()==human,"Unfair traveller remains native loot winner");
  var team=unfair.toTeamDamages();
  check(team.getMostDamage().getAttacker()==human && team.getFirstDamage().getAttacker()==human && team.getTotalDamage()==10,"Loot winner/first-hit/denominator survive XP exclusion");
  check(!team.isExperienceEligible(human),"Team reward owner receives XP exclusion without losing loot entry");
  check(list(monster,damage(human,20),damage(warden,80)).isExperienceEligible(human),"Exact twenty percent boundary earns XP");
  var alliance=actor(Alliance.class,100);alliance.travellers=List.of(human,friend,observer);human.alliance=friend.alliance=observer.alliance=alliance;
  var shared=list(monster,damage(human,10),damage(friend,10),damage(warden,80));
  check(shared.isExperienceEligible(human) && shared.isExperienceEligible(friend) && shared.isExperienceEligible(observer),"Native alliance members combine damage including passive recipients");
  team=shared.toTeamDamages();check(team.getCreatureOrTeamDamages().size()==1 && team.getTotalDamage()==20 && team.getMostDamage().getAttacker()==alliance,"Native alliance damage and loot remain combined");
  check(team.getMostDamageByTeam(alliance)!=null,"Alliance retains native most-damage looter");
  var low=list(monster,damage(human,2),damage(friend,3),damage(warden,95));
  check(!low.isExperienceEligible(human) && !low.isExperienceEligible(friend) && !low.isExperienceEligible(observer),"Under-threshold alliance excludes all member XP/bounty");
  check(low.toTeamDamages().getMostDamage().getAttacker()==alliance,"Under-threshold alliance retains loot winner");
  var other=actor(Alliance.class,101);other.travellers=List.of(friend,observer);alliance.travellers=List.of(human);friend.alliance=observer.alliance=other;
  var league=actor(SharedLeague.class,102);league.alliances=List.of(alliance,other);alliance.setLeague(league);other.setLeague(league);
  var leagueLow=list(monster,damage(human,10),damage(warden,90));
  check(!leagueLow.isExperienceEligible(human) && !leagueLow.isExperienceEligible(friend) && !leagueLow.isExperienceEligible(observer),"Unfair contributing alliance cannot grant XP/bounty to passive league alliances");
  check(!leagueLow.toTeamDamages().isExperienceEligible(alliance) && !leagueLow.toTeamDamages().isExperienceEligible(other),"Every native league reward call uses shared fair eligibility");
  var leagueFair=list(monster,damage(human,10),damage(friend,10),damage(warden,80));
  check(leagueFair.isExperienceEligible(human) && leagueFair.isExperienceEligible(friend) && leagueFair.isExperienceEligible(observer),"Native league combines all allied effort at twenty percent boundary");
  check(leagueFair.toTeamDamages().getCreatureOrTeamDamages().size()==2,"League fairness preserves native per-alliance loot entries");
  alliance.setLeague(null);other.setLeague(null);human.alliance=friend.alliance=observer.alliance=null;
  var pet=actor(PetDamage.class,20);pet.master=human;var wardenPet=actor(PetDamage.class,21);wardenPet.master=warden;
  var summoned=list(monster,damage(human,5),damage(pet,15),damage(wardenPet,80));
  check(summoned.getTotalDamage()==20 && summoned.getCreatureDamages().size()==1 && summoned.getMostDamage().getAttacker()==human,"Native master aggregation includes human summons and excludes Warden summons");
  check(summoned.isExperienceEligible(human),"Summon damage satisfies fair threshold under master");
  var historic=damage(warden,80);
  var roadType=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotRoadDuty$State");
  var states=(Map<?,?>)field(PlayerBotRoadDuty.class,"STATES").get(null);var duty=states.get(warden.getObjectId());
  field(roadType,"suspended").setBoolean(duty,true);
  check(!PlayerBotRoadDuty.publicWarden(warden),"Suspension really releases duty before reward construction");
  check(list(monster,damage(human,20),historic).getTotalDamage()==20,"Earlier Warden damage remains excluded after duty suspension");
  historic.addDamage(20,2000);
  var mixed=list(monster,damage(human,20),historic);
  check(mixed.getTotalDamage()==40 && historic.getWardenDamage()==80,"Later ordinary activity retains only its own damage claim");
  check(mixed.getCreatureDamages().stream().filter(d->d.getAttacker()==warden).findFirst().orElseThrow().getFirstDamageTime()==2000,"Earlier free aid never gives later ordinary claims first-hit priority");
  field(roadType,"suspended").setBoolean(duty,false);
  var disconnected=damage(warden,90);states.remove(warden.getObjectId());
  check(list(monster,damage(human,10),disconnected).getTotalDamage()==10,"Recorded Warden aid remains excluded after logout removes state");
  warden(warden);
  var alone=list(monster,damage(warden,100));
  check(alone.getCreatureDamages().isEmpty() && alone.toTeamDamages().getMostDamage()==null && alone.toTeamDamages().getTotalDamage()==0,"Warden-only kill leaves safe empty native claims");
  field(Creature.class,"aggroList").set(monster,new AggroList(monster){@Override public DamageList getFinalDamageList(){return alone;}});
  var controller=new NpcController();controller.setOwner(monster);
  check(controller.markRoadBountyCredit(1) && !controller.markRoadBountyCredit(1),"Duplicate native team callbacks count bounty once per corpse");
  controller.doReward();check(controller.markRoadBountyCredit(1),"Real doReward empty-claims path returns before drop registration/division and resets per-death credit");
  var normal=list(monster,damage(human,100));check(normal.isExperienceEligible(human) && normal.getTotalDamage()==100,"Unassisted rewards unchanged");
  PlayerBotWardenConfig.ENABLED=false;check(list(monster,damage(human,1),damage(warden,99)).isExperienceEligible(human),"Disabled fair-kill service preserves previous XP behavior");
  System.out.println("OK: "+checks+" native DamageList Warden reward checks");
 }
}
