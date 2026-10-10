package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import sun.misc.Unsafe;

/** World-free persistence, route planning, pricing and identity regressions. No native actors/DB/IDs. */
public final class PlayerBotWorldCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 interface Attempt {void run()throws Exception;}
 static void refused(Attempt a,String why)throws Exception {try{a.run();throw new AssertionError(why);}catch(IllegalArgumentException|java.io.IOException e){checks++;}}
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory(Path.of("."),"world-check-");
  try {
   var roster=PlayerBotPopulationTestStorage.roster(dir.resolve("world.properties"));check(roster.load().isEmpty(),"Missing roster is empty, never implicit population");
   var r=new PlayerBotWorldRoster.Resident(7,81,210010000,12,23,34,(byte)5);roster.save(Map.of(81,r));
   check(PlayerBotPopulationTestStorage.roster(dir.resolve("world.properties")).load().equals(Map.of(81,r)),"Residence survives fresh loader");
   for(float n:List.of(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY))refused(()->new PlayerBotWorldRoster.Resident(7,81,210010000,n,1,1,(byte)0),"Nonfinite residence");
   refused(()->new PlayerBotWorldRoster.Resident(0,81,1,1,1,1,(byte)0),"No invented account");
   Files.writeString(dir.resolve("world.properties"),"81=7,210010000,1,2,3,0,999\n");
   refused(()->PlayerBotPopulationLegacy.residents(dir.resolve("world.properties")),"Future roster version must not be overwritten");check(Files.readString(dir.resolve("world.properties")).contains("999"),"Corrupt roster retained");
   var population=PlayerBotPopulationTestStorage.population(dir.resolve("population.json"));check(population.load().isEmpty(),"Population requires provisioning");
   var appearance=new PlayerAppearance();var pool=new PlayerBotWorldPopulation.Pool(7,"account",(byte)0,123L,Race.ELYOS,Gender.MALE,appearance,3,List.of(81,82));population.put(pool);
   var restored=PlayerBotPopulationTestStorage.population(dir.resolve("population.json")).load().get(7);
   check(restored!=null && restored.account()==7 && restored.desired()==3 && restored.characters().equals(List.of(81,82)),"JSON pool round trip with actual serializer");
   check(restored.race()==Race.ELYOS && restored.gender()==Gender.MALE && restored.appearance()!=null,"Race/appearance creation template survives restart");
   population.put(pool.desired(0));check(population.load().get(7).characters().size()==2,"Pause retains character IDs and progress");
   population.put(pool.append(83));check(population.load().get(7).characters().equals(List.of(81,82,83)),"Creation intent persists before native creation");
   refused(()->pool.append(81),"Duplicate character cannot enter pool");refused(()->pool.desired(-1),"Negative population");refused(()->pool.desired(101),"Bounded population");
   for(int id:List.of(1,26,27,106628,Integer.MAX_VALUE)) {
    check(PlayerBotWorldPopulation.name(id).matches("[A-Z][a-z]+"),"Native-style generated name");
    check(!PlayerBotWorldPopulation.playerClass(id).isStartingClass(),"Specialization resolves to advanced class");
   }
   Files.writeString(dir.resolve("population.json"),"{broken");
   refused(()->PlayerBotPopulationLegacy.pools(dir.resolve("population.json")),"Corrupt pool refuses generation");
   check(Files.readString(dir.resolve("population.json")).equals("{broken"),"Corrupt pool not silently reset");
   var a=new PlayerBotWorldTravel.Place(1,11,1,2,3);var b=new PlayerBotWorldTravel.Place(2,12,4,5,6);
   var ab=new PlayerBotWorldTravel.Edge(a,2,101);var bc=new PlayerBotWorldTravel.Edge(b,3,102);var ba=new PlayerBotWorldTravel.Edge(b,1,103);
   check(PlayerBotWorldTravel.route(1,3,List.of(ab,bc,ba)).equals(List.of(ab,bc)),"Directed multi-map native route");
   check(PlayerBotWorldTravel.route(3,1,List.of(ab,bc,ba))==null,"No invented reverse route");
   check(PlayerBotWorldTravel.route(1,4,List.of(ab,bc,ba))==null,"Cycles terminate; unreachable remains unreachable");
   check(PlayerBotWorldTravel.route(1,1,List.of()).isEmpty(),"Same-map movement requires no teleport");
   var direct=new PlayerBotWorldTravel.Edge(a,3,104);check(PlayerBotWorldTravel.route(1,3,List.of(ab,bc,direct)).equals(List.of(direct)),"Fewer native hops preferred");
   var reward=new PlayerBotWorldActivities.Goal(4,PlayerBotQuestRoutes.Kind.REWARD,a,4000);
   var hunt=new PlayerBotWorldActivities.Goal(5,PlayerBotQuestRoutes.Kind.HUNT,b,10);
   var start=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.CONVERSATION,a,1);
   check(PlayerBotWorldActivities.select(List.of(start,hunt,reward))==reward,"Complete earned quests before starting more");
   check(PlayerBotWorldActivities.select(List.of(start,hunt))==hunt,"Active objective before another quest");
   check(PlayerBotWorldActivities.select(List.of(new PlayerBotWorldActivities.Goal(4,PlayerBotQuestRoutes.Kind.REWARD,a,Double.POSITIVE_INFINITY)))==null,"Unreachable objectives not selected");
   for(int level:List.of(1,9,10,30,65)) {
    check(PlayerBotWorldActivities.levelSuitable(level,level),"Same-level grind");check(!PlayerBotWorldActivities.levelSuitable(level,level+5),"Pinned solo over-level pull refused");
    check(!PlayerBotWorldActivities.levelSuitable(level,Math.max(0,level-11)),"Native no-XP or invalid grind refused");
   }
   check(!PlayerBotWorldActivities.stalled(29999,0),"Transient movement delay allowed");check(PlayerBotWorldActivities.stalled(30000,0),"Stalled goal eventually released");
   check(PlayerBotWorldTrade.price(5,3,true)==30,"Player sees complete sale quote");check(PlayerBotWorldTrade.price(5,3,false)==15,"Bot purchase pays native vendor value");
   for(long bad:List.of(0L,-1L))refused(()->PlayerBotWorldTrade.price(5,bad,true),"Nonpositive item count refused");
   refused(()->PlayerBotWorldTrade.price(Long.MAX_VALUE,2,true),"Sale overflow refused");refused(()->PlayerBotWorldTrade.price(Long.MAX_VALUE,2,false),"Purchase overflow refused");
   for(boolean elyos:List.of(false,true)) {
    Set<Integer> choices=new HashSet<>();for(var pc:PlayerClass.values())if(!pc.isStartingClass()) {
     int action=PlayerBotWorldAscension.choice(elyos,pc);check(action>0 && choices.add(action),"Each advanced class has a unique native Ascension choice");
    }
    check(PlayerBotWorldAscension.choice(elyos,PlayerClass.WARRIOR)==0,"Starting class cannot masquerade as a final choice");
    check(PlayerBotWorldAscension.step(elyos,99).action()==DialogAction.QUEST_SELECT,"Native scripted flight begins only at its offered stage");
    for(int stage:List.of(50,51,52,53,54))check(PlayerBotWorldAscension.step(elyos,stage)==null,"Flight/combat stages are not skipped with a dialog");
   }
   check(PlayerBotWorldAscension.step(true,1)==null,"Poeta water objective requires native item use");
   check(PlayerBotWorldAscension.step(false,4).npc()==203550,"Asmodian stones return to Munin");
   var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe unsafe=(Unsafe)f.get(null);
   Player human=(Player)unsafe.allocateInstance(Player.class);refused(()->human.setWorldBotController(0),"Ordinary player cannot become world bot through control transfer");
   var field=Player.class.getDeclaredField("worldBot");field.setAccessible(true);field.setBoolean(human,true);
   check(human.isPlayerBot() && human.isWorldBot() && human.getPlayerBotOwnerId()==0,"Independent identity remains a bot without owner");
   human.setWorldBotController(123);check(human.getPlayerBotOwnerId()==123,"Invitation changes controller");human.setWorldBotController(0);
   check(human.isWorldBot() && human.getPlayerBotOwnerId()==0,"Release preserves independent identity");
   refused(()->human.setWorldBotController(-1),"Invalid controller rejected");
   System.out.println("OK: "+checks+" independent world lifecycle, persistence, route and trade checks; no world/DB/IDs");
  }finally{try(var files=Files.list(dir)){for(var file:files.toList())Files.delete(file);}Files.delete(dir);}
 }
}
