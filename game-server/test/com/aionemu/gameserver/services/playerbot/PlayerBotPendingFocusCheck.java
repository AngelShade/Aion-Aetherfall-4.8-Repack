package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.Race;

/** Actual pending-batch custody with isolated durable storage; no World or production SQL. */
public final class PlayerBotPendingFocusCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static Field field(String name)throws Exception{var f=PlayerBotService.class.getDeclaredField(name);f.setAccessible(true);return f;}
 private static final class Store implements PlayerBotPopulationStorage {
  final PlayerBotPopulationTestStorage data=new PlayerBotPopulationTestStorage();boolean fail;int writes;
  public Map<Integer,PlayerBotWorldRoster.Resident> residents(){return data.residents();}
  public void residents(Map<Integer,PlayerBotWorldRoster.Resident> rows)throws IOException{if(fail)throw new IOException("Synthetic admission save failure");data.residents(rows);writes++;}
  public Map<Integer,PlayerBotWorldPopulation.Pool> pools(boolean server){return data.pools(server);}
  public void pool(boolean server,PlayerBotWorldPopulation.Pool next){data.pool(server,next);}
  public PlayerBotPopulationSchedule.State schedule(){return data.schedule();}
  public void schedule(PlayerBotPopulationSchedule.State next){data.schedule(next);}
  public void event(int id,PlayerBotPopulationSchedule.Events next){data.event(id,next);}
 }
 private static PlayerBotWorldRoster.Resident row(int id,int map){return new PlayerBotWorldRoster.Resident(-1000000,id,map,500,500,100,(byte)0);}
 private static PlayerBotPopulationFocus.Snapshot focus(int map){return new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(map,500,500,100)),900);}
 private static PlayerBotService service(Store store,Map<Integer,PlayerBotWorldRoster.Resident> rows,int map)throws Exception {
  var c=PlayerBotService.class.getDeclaredConstructor();c.setAccessible(true);var s=c.newInstance();
  store.residents(rows);field("worldRoster").set(s,new PlayerBotWorldRoster(store));field("populationSchedule").set(s,new PlayerBotPopulationSchedule(store));field("residents").set(s,new TreeMap<>(rows));field("playerFocus").set(s,focus(map));return s;
 }
 private static void batches()throws Exception {
  var rows=new TreeMap<Integer,PlayerBotWorldRoster.Resident>();
  for(int i=1;i<=83;i++)rows.put(i,row(i,i<=80?220030000:210060000));
  var store=new Store();var s=service(store,rows,210060000);
  store.event(1,new PlayerBotPopulationSchedule.Events(60000,72000,90000,43));var events=store.schedule();
  check(s.automaticPendingAdmissions()==83,"Reproduce full old-map pending batch independent of active population");
  s.releaseSurplusPendingAdmissions(1385);
  check(store.residents().keySet().equals(Set.of(81,82,83)),"Unloading old-map pending intent cannot block current-map reserve admission");
  check(s.automaticPendingAdmissions()==3,"Only current eligible batch remains to drain");
  check(store.schedule().equals(events),"Geographic intent cleanup retains saved timers/cooldown/progress references");
  int writes=store.writes;s.releaseSurplusPendingAdmissions(1385);check(store.writes==writes,"Unchanged current-map intents do not create recurring writes");
  field("playerFocus").set(s,focus(220030000));s.releaseSurplusPendingAdmissions(1385);
  check(s.automaticPendingAdmissions()==0 && store.residents().isEmpty(),"Another player map change releases remaining stale batch in one durable save");
  var absent=new Store();var noFocus=service(absent,rows,210060000);field("playerFocus").set(noFocus,new PlayerBotPopulationFocus.Snapshot(List.of(),900));
  noFocus.releaseSurplusPendingAdmissions(1385);check(absent.residents().equals(rows),"No focused player retains ordinary worldwide pending logins");
  var failed=new Store();var held=service(failed,rows,210060000);failed.fail=true;
  try{held.releaseSurplusPendingAdmissions(1385);throw new AssertionError("Uncommitted cleanup published");}catch(IOException expected){checks++;}
  check(failed.residents().equals(rows) && held.automaticPendingAdmissions()==83,"Native persistence failure holds all admission slots and the manager snapshot");
  failed.fail=false;held.releaseSurplusPendingAdmissions(1385);check(held.automaticPendingAdmissions()==3,"Successful durable retry releases exactly the obsolete intents");
 }
 @SuppressWarnings("unchecked") private static void custody()throws Exception {
  var rows=new TreeMap<Integer,PlayerBotWorldRoster.Resident>();for(int id=91;id<=97;id++)rows.put(id,row(id,220030000));
  rows.put(90,new PlayerBotWorldRoster.Resident(7,90,220030000,500,500,100,(byte)0));rows.put(98,row(98,210060000));
  var store=new Store();var s=service(store,rows,210060000);
  ((Set<Integer>)field("retiringWorldBots").get(s)).add(91);
  ((Map<Integer,Long>)field("retireQueue").get(s)).put(92,0L);
  field("focusJobId").setInt(s,93);
  var execution=(PlayerBotExecution)field("execution").get(s);execution.claim(94,"synthetic held save",0);
  try(var lease=PlayerBotLease.acquire(95)){
   check(lease!=null,"Isolated synthetic lease supplies real native custody guard without character loading");
   s.releaseSurplusPendingAdmissions(1385);
   check(store.residents().keySet().equals(Set.of(90,91,92,93,94,95,98)),"Manual/retiring/save-queued/executing/focus-job/leased custody remains while ordinary stale intents release");
   execution.complete(94);field("focusJobId").setInt(s,0);
   ((Set<Integer>)field("retiringWorldBots").get(s)).clear();((Map<Integer,Long>)field("retireQueue").get(s)).clear();
   s.releaseSurplusPendingAdmissions(1385);check(store.residents().keySet().equals(Set.of(90,95,98)),"Completed custody permits cleanup while in-flight lease still holds its saved intent");
  }
  s.releaseSurplusPendingAdmissions(1385);check(store.residents().keySet().equals(Set.of(90,98)),"Native lease settlement releases obsolete geographic intent");
  s.releaseSurplusPendingAdmissions(0);check(store.residents().keySet().equals(Set.of(90)),"Zero target retains manual residents and releases ordinary pending automatic entries");
 }
 private static void eligibility()throws Exception {
  var type=PlayerBotWorldPlacement.class;var cache=type.getDeclaredField("cache");cache.setAccessible(true);var choices=type.getDeclaredField("FOCUS_CHOICES");choices.setAccessible(true);
  Object oldCache=cache.get(null);@SuppressWarnings("unchecked") var memo=(Map<String,Map<Integer,List<PlayerBotWorldPlacement.Location>>>)choices.get(null);var oldMemo=new HashMap<>(memo);
  var config=PlayerBotConfig.class.getField("POPULATION_MAPS");Object oldMaps=config.get(null);
  try {
   config.set(null,Set.of());memo.clear();
   var elyos=new PlayerBotWorldPlacement.Location(Race.ELYOS,210060000,500,500,100,35,50,false,false);
   var asmo=new PlayerBotWorldPlacement.Location(Race.ASMODIANS,220030000,500,500,100,35,50,false,false);cache.set(null,List.of(elyos,asmo));
   check(PlayerBotWorldPlacement.focusCompatible(Race.ELYOS,43,focus(210060000)),"Same-faction/native-level catalog can satisfy captured Heiron focus");
   check(!PlayerBotWorldPlacement.focusCompatible(Race.ASMODIANS,43,focus(210060000)),"Opposite-faction character has no compatible focused camp; refusal remains truthful");
   check(PlayerBotWorldPlacement.focusChoices(Race.ASMODIANS,43,focus(210060000),new Random(1)).isEmpty(),"Focused placement cannot secretly fall back to another map");
   check(!PlayerBotWorldPlacement.focusCompatible(Race.ELYOS,5,focus(210060000)),"Cleanup never relaxes native level suitability");
   config.set(null,Set.of(220030000));check(!PlayerBotWorldPlacement.focusCompatible(Race.ELYOS,43,focus(210060000)),"Owner map exclusions remain authoritative");
  }finally{cache.set(null,oldCache);memo.clear();memo.putAll(oldMemo);config.set(null,oldMaps);}
 }
 public static void main(String[] args)throws Exception{batches();custody();eligibility();System.out.println("OK: "+checks+" actual stale focus admission, durable custody and faction/level eligibility checks; no World/production SQL/native ID allocation");}
 private PlayerBotPendingFocusCheck(){}
}
