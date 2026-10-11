package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.gather.*;
import com.aionemu.gameserver.model.templates.globaldrops.*;
import com.aionemu.gameserver.world.WorldDropType;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestMaterials.*;

/** Real parsed-model snapshots and custody boundaries; no World, native IDs, services or SQL. */
public final class PlayerBotQuestMaterialsCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void set(Object object,String name,Object value)throws Exception {
  var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
 }
 private static Material material(int id,int rate)throws Exception {var m=new Material();set(m,"itemid",id);set(m,"rate",rate);return m;}
 private static boolean matches(Rule rule,Race actor,int itemLevel,int npcLevel,int npc,int map,Object world,Object rating,Object race,Object tribe,Object group,Set<String> zones) {
  return rule.matches(actor,itemLevel,npcLevel,npc,map,world,rating,race,tribe,group,zones);
 }
 private static Rule constrained(Selector maps,Selector worlds,Selector ratings,Selector races,Selector tribes,Selector npcs,Selector groups,Selector zones,Set<Integer> excluded,Race restriction,int min,int max) {
  return new Rule(20,true,min,max,restriction,maps,worlds,ratings,races,tribes,npcs,groups,excluded,zones,1);
 }
 public static void main(String[] args)throws Exception {
  var requirements=new HashMap<>(Map.of(100,8L,200,2L));
  var active=new HashMap<Integer,Long>();acquireRequirements(active,requirements,false);
  check(deficit(active,id->0).isEmpty(),"Disabled quest family cannot create hunt/gather acquisition deficits");
  check(requirements.equals(Map.of(100,8L,200,2L)),"Disabled acquisition retains full owned material custody requirements");
  acquireRequirements(active,Map.of(300,3L),true);
  check(deficit(active,id->0).equals(Map.of(300,3L)),"An enabled independent quest still supplies its exact targets while another family is disabled");
  acquireRequirements(active,Map.of(300,1L,400,0L,-1,9L),true);
  check(active.equals(Map.of(300,3L)),"Actionable native requirements preserve max quantity and reject malformed targets");
  check(deficit(requirements,id->0).equals(requirements),"No custody never becomes virtual items");
  check(deficit(requirements,id->id==100?7:2).equals(Map.of(100,1L)),"Exact owned count controls remaining native need");
  check(deficit(requirements,id->99).isEmpty(),"Owned surplus stops acquisition");
  check(deficit(Map.of(100,Long.MAX_VALUE),id->Long.MAX_VALUE-1).equals(Map.of(100,1L)),"Large native counts retain exact deficit");
  check(deficit(Map.of(100,8L),id->-5).get(100)==8,"Malformed negative ownership cannot inflate material request");
  boolean frozen=false;try{deficit(requirements,id->0).put(300,1L);}catch(UnsupportedOperationException expected){frozen=true;}
  check(frozen,"Inventory deficit snapshots remain immutable");
  var normal=new Materials();set(normal,"material",new ArrayList<>(List.of(material(100,10000000),material(101,0))));
  var extra=new ExMaterials();set(extra,"material",new ArrayList<>(List.of(material(200,10000000))));
  var nativeTemplate=new GatherableTemplate();set(nativeTemplate,"id",400001);set(nativeTemplate,"harvestSkill",30002);set(nativeTemplate,"skillLevel",99);set(nativeTemplate,"lvlLimit",10);
  set(nativeTemplate,"materials",normal);set(nativeTemplate,"exmaterials",extra);set(nativeTemplate,"reqItem",500);set(nativeTemplate,"checkType",1);set(nativeTemplate,"eraseValue",2);
  var source=gatherSource(nativeTemplate);
  var zeroFirst=new Materials();set(zeroFirst,"material",new ArrayList<>(List.of(material(103,0),material(104,10000000))));set(nativeTemplate,"materials",zeroFirst);
  check(gatherSource(nativeTemplate).normal().contains(103),"Native zero draw retains first zero-rate row's one-in-ten-million outcome");set(nativeTemplate,"materials",normal);
  check(source.id()==400001 && source.skill()==30002 && source.points()==99 && source.level()==10,"Snapshot exact native object/profession/point/level metadata");
  check(source.outputs(false,99).equals(Set.of(100)),"Native equipment check ignores cube holdings");
  check(source.outputs(true,0).equals(Set.of(200)),"Actually equipped prerequisite selects native extra materials");
  check(source.declared().equals(Set.of(100,200)),"Equipment-switch source metadata retains both reachable branches");
  check(!source.normal().contains(101),"Zero-rate gather outcome is not a source");
  normal.getMaterial().add(material(102,10000000));check(!gatherSource(nativeTemplate).normal().contains(102),"Native saturated cumulative rate cannot expose unreachable later outputs");
  set(nativeTemplate,"checkType",2);var consumable=gatherSource(nativeTemplate);
  check(consumable.outputs(false,1).isEmpty(),"Native consumed prerequisite needs the entire erase quantity");
  check(consumable.outputs(false,2).equals(Set.of(200)),"Exact cube prerequisite selects extra materials");
  check(consumable.outputs(true,0).isEmpty(),"Equipped item does not satisfy native cube consumption");
  check(consumable.declared().equals(Set.of(200)),"Consumed prerequisite source never declares unreachable normal materials");
  set(nativeTemplate,"checkType",3);check(gatherSource(nativeTemplate).outputs(false,0).equals(Set.of(100)),"Other native check types retain normal material branch");
  check(gatherSource(nativeTemplate).declared().equals(Set.of(100)),"Other native check types do not declare extra materials");
  set(nativeTemplate,"reqItem",0);check(gatherSource(nativeTemplate).outputs(true,0).equals(Set.of(100)),"No required item cannot select extra branch");
  set(material(1,1),"rate",10);normal.getMaterial().clear();extra.getMaterial().clear();
  check(source.outputs(false,0).equals(Set.of(100)) && source.outputs(true,0).equals(Set.of(200)),"Catalog snapshot survives native mutable-model changes");
  var mutable=new HashSet<Object>(Set.of(10));var map=new Selector(true,mutable);mutable.add(20);
  check(map.accepts(10) && !map.accepts(20),"Selector copies native whitelist immutably");
  check(!map.accepts(null),"Null native tribe/selector never throws or broadens whitelist");
  var any=new Selector(false,Set.of());var empty=new Selector(true,Set.of());
  check(any.accepts(null) && !empty.accepts(10),"Absent restriction differs from explicit empty native whitelist");
  var all=constrained(any,any,any,any,any,any,any,any,Set.of(),null,-5,5);
  check(matches(all,Race.ELYOS,10,10,1,10,WorldDropType.NONE,"rating",Race.PC_ALL,null,"group",Set.of()),"Unrestricted native rule allows null tribe");
  for(int itemLevel=1;itemLevel<25;itemLevel++)for(int npcLevel=1;npcLevel<25;npcLevel++)
   check(matches(all,Race.ELYOS,itemLevel,npcLevel,1,10,WorldDropType.NONE,"rating",Race.PC_ALL,null,"group",Set.of())==(npcLevel-itemLevel>=-5 && npcLevel-itemLevel<=5),"Native item/NPC level-difference range "+itemLevel+"/"+npcLevel);
  var constrained=constrained(new Selector(true,Set.of(10)),new Selector(true,Set.of("world")),new Selector(true,Set.of("rating")),new Selector(true,Set.of(Race.PC_ALL)),new Selector(true,Set.of("tribe")),
   new Selector(true,Set.of(1)),new Selector(true,Set.of("group")),new Selector(true,Set.of("zone")),Set.of(2),Race.ELYOS,-5,5);
  check(matches(constrained,Race.ELYOS,10,10,1,10,"world","rating",Race.PC_ALL,"tribe","group",Set.of("zone")),"All native global restrictions must agree");
  check(!matches(constrained,Race.ASMODIANS,10,10,1,10,"world","rating",Race.PC_ALL,"tribe","group",Set.of("zone")),"Actor race restriction");
  check(!matches(constrained,Race.ELYOS,10,10,2,10,"world","rating",Race.PC_ALL,"tribe","group",Set.of("zone")),"NPC whitelist and exclusion");
  check(!matches(constrained,Race.ELYOS,10,10,1,11,"world","rating",Race.PC_ALL,"tribe","group",Set.of("zone")),"Map restriction");
  check(!matches(constrained,Race.ELYOS,10,10,1,10,"other","rating",Race.PC_ALL,"tribe","group",Set.of("zone")),"World drop type restriction");
  check(!matches(constrained,Race.ELYOS,10,10,1,10,"world","other",Race.PC_ALL,"tribe","group",Set.of("zone")),"NPC rating restriction");
  check(!matches(constrained,Race.ELYOS,10,10,1,10,"world","rating",Race.ASMODIANS,"tribe","group",Set.of("zone")),"NPC race restriction");
  check(!matches(constrained,Race.ELYOS,10,10,1,10,"world","rating",Race.PC_ALL,null,"group",Set.of("zone")),"NPC tribe restriction including missing tribe");
  check(!matches(constrained,Race.ELYOS,10,10,1,10,"world","rating",Race.PC_ALL,"tribe","other",Set.of("zone")),"Native drop-group restriction");
  check(!matches(constrained,Race.ELYOS,10,10,1,10,"world","rating",Race.PC_ALL,"tribe","group",Set.of("other")),"Native current zone restriction");
  var exclude=constrained(any,any,any,any,any,any,any,any,Set.of(1),null,-5,5);
  check(!matches(exclude,Race.ELYOS,10,10,1,10,"world","rating",Race.PC_ALL,null,"group",Set.of()),"Excluded NPC cannot be a global source");
  check(race(Race.PC_ALL,Race.ELYOS) && race(Race.ELYOS,Race.ELYOS) && !race(Race.ASMODIANS,Race.ELYOS),"Custom/global item native race eligibility");
  for(int delta=-20;delta<=20;delta++) {
   check(reduction(true,30,30-delta)==(delta>-10),"Reduced native drop chance boundary "+delta);
   check(reduction(false,30,30-delta),"Non-reduced native drops preserve chance "+delta);
  }
  check(!practiceAllowed(30001,49,40,false),"Native human gathering cap cannot produce profession progress");
  check(!practiceAllowed(30002,399,390,false),"Gathering qualification cap blocks pointless practice");
  check(practiceAllowed(30002,449,440,false),"Native harvesting449 keeps XP despite crafting artisan boundary");
  check(!practiceAllowed(30003,499,490,false),"Native capped aethertapping499 requires qualification");
  check(practiceAllowed(30003,499,490,true),"Native configured unbounded tapping remains earnable");
  check(practiceAllowed(30003,550,530,true),"Native unbounded tapping beyond549 remains earnable");
  check(!practiceAllowed(30002,70,29,false),"More than40points low grants no native XP");
  check(practiceAllowed(30002,70,30,false),"Exact40point practice boundary remains eligible");
  check(!practiceAllowed(30002,70,71,false),"Unlearned higher node cannot serve as native practice");
  check(flightBudget(0,5)==12,"Native flight retains explicit landing/gather reserve even at same point");
  check(flightBudget(100,5)==52,"Budget accounts for native outside-zone two-point-per-second reduction");
  check(flightBudget(2*100,5)==92,"Round-trip flight budget covers both outward and landing movement");
  check(flightBudget(1,3)==13,"Fractional flight travel rounds up before reserve");
  check(flightBudget(Double.NaN,5)==Integer.MAX_VALUE,"Invalid flight distance cannot authorize takeoff");
  check(flightBudget(Double.POSITIVE_INFINITY,5)==Integer.MAX_VALUE,"Unreachable flight path cannot authorize takeoff");
  check(flightBudget(10,0)==Integer.MAX_VALUE,"Immobilized or invalid-speed flight cannot authorize takeoff");
  check(flightBudget(10,-1)==Integer.MAX_VALUE,"Negative native speed cannot authorize takeoff");
  check(flightBudget(10,Double.NaN)==Integer.MAX_VALUE,"Invalid native speed cannot authorize takeoff");
  check(flightBudget(Double.MAX_VALUE,0.1)==Integer.MAX_VALUE,"Flight arithmetic cannot overflow into cheap admission");
  check(ground(100,100) && ground(101.5f,100),"Actual floor support admits grounded native landing boundary");
  check(!ground(101.51f,100),"Aerial state cannot clear before actual supported floor arrival");
  check(!ground(100,Float.NaN) && !ground(Float.NaN,100),"Missing geodata never becomes safe landing");
  check(!ground(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY),"Invalid positions cannot become grounded");
  var nativeRule=new GlobalRule();set(nativeRule,"chance",15f);set(nativeRule,"minDiff",-3);set(nativeRule,"maxDiff",4);set(nativeRule,"restrictionRace",GlobalRule.RestrictionRace.ELYOS);
  var snapshot=rule(nativeRule);set(nativeRule,"chance",0f);check(snapshot.chance()==15 && snapshot.minimum()==-3 && snapshot.maximum()==4 && snapshot.restriction()==Race.ELYOS,"Global rule snapshot reads exact native fields without native service/RNG");
  System.out.println("OK: "+checks+" native material custody/gather/global-source checks; no World/SQL/IDs/awards");
 }
}
