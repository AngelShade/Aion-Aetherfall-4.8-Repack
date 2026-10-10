package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.sql.*;
import com.aionemu.gameserver.model.Race;

/** Demand and native XP reuse with synthetic JDBC. No World/actors/native IDs/database. */
public final class PlayerBotPopulationFocusCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static PlayerBotPopulationFocus.Anchor anchor(int map,float x){return new PlayerBotPopulationFocus.Anchor(map,x,500,100);}
 private static PlayerBotPopulationFocus.Snapshot focus(int map){return new PlayerBotPopulationFocus.Snapshot(List.of(anchor(map,500)),900);}
 private static PlayerBotWorldPlacement.Location camp(int map,float x){return new PlayerBotWorldPlacement.Location(Race.ELYOS,map,x,500,100,35,50,false,false);}
 private static final class Jdbc {
  List<long[]> rows=new ArrayList<>();Map<Integer,Integer> parameters=new TreeMap<>();int queries;boolean fail;String sql;
  Connection connection(){return PlayerBotMetadataCheck.proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("prepareStatement")) {
    sql=(String)a[0];parameters.clear();
    return PlayerBotMetadataCheck.proxy(PreparedStatement.class,(s,sm,sa)->{
     if(sm.getName().equals("setInt")){parameters.put((Integer)sa[0],(Integer)sa[1]);return null;}
     if(sm.getName().equals("executeQuery")) {
      queries++;if(fail)throw new SQLException("Synthetic XP failure");int[] index={-1};
      var bound=rows.stream().filter(r->parameters.containsValue((int)r[0])).toList();
      return PlayerBotMetadataCheck.proxy(ResultSet.class,(r,rm,ra)->switch(rm.getName()) {
       case "next"->++index[0]<bound.size();case "getInt"->(int)bound.get(index[0])[(Integer)ra[0]-1];
       case "getLong"->bound.get(index[0])[(Integer)ra[0]-1];default->PlayerBotMetadataCheck.zero(rm.getReturnType());
      });
     }
     if(sm.getName().startsWith("execute"))throw new AssertionError("XP selection cannot write characters");
     return PlayerBotMetadataCheck.zero(sm.getReturnType());
    });
   }
   if(Set.of("commit","rollback","setAutoCommit").contains(m.getName()))throw new AssertionError("XP selection cannot alter transactions");
   return PlayerBotMetadataCheck.zero(m.getReturnType());
  });}
 }
 public static void main(String[] args)throws Exception {
  var a=focus(210040000);var b=focus(210030000);
  check(a.target(2995)==2995,"One occupied map keeps the entire configured target");
  check(a.target(80)==80,"Global configured ceiling remains authoritative");
  check(a.contains(210040000) && !a.contains(210030000),"Off-map residents are distinguishable without deleting identity");
  check(!a.prefer(List.of(camp(210030000,700)),Map.of()).iterator().hasNext(),"Focused admission cannot fall back to another map");
  check(a.prefer(List.of(camp(210040000,3000),camp(210040000,700)),Map.of()).getFirst().x()==700,"Player-area camp precedes distant camp");
  check(a.nearby(210040000,700,500,100) && !a.nearby(210040000,1500,500,100),"Area radius is native spatial demand");
  check(Double.isInfinite(a.distance(210030000,500,500,100)),"Same coordinates on another map cannot satisfy demand");
  var two=new PlayerBotPopulationFocus.Snapshot(List.of(anchor(1,500),anchor(2,500),anchor(1,900)),900);
  check(two.target(2995)==2995,"Several players and maps distribute the configured population without reducing it");
  check(two.prefer(List.of(camp(1,700),camp(2,700)),Map.of(1,160,2,0)).getFirst().map()==2,"Least occupied map wins instead of starving second player");
  check(two.mapTarget(1,161)==81 && two.mapTarget(2,161)==80,"Reduced global ceiling apportioned across occupied maps");
  check(two.available(Map.of(1,160,2,0),320).maps().equals(Set.of(2)),"Full first map cannot consume a second player's admission slots");
  check(!two.available(Map.of(1,160,2,160),320).active(),"Full map quotas stop focused recruitment");
  check(two.mapTarget(3,320)==0 && two.mapTarget(1,0)==0,"Unoccupied map and disabled global ceiling allocate no residents");
  var empty=new PlayerBotPopulationFocus.Snapshot(List.of(),900);
  check(empty.target(2995)==2995 && !empty.active(),"No players restores independent worldwide life");
  check(empty.prefer(List.of(camp(1,700)),Map.of()).size()==1,"Disabled/no-player demand preserves worldwide candidates");
  check(PlayerBotPopulationFocus.slots(160,160,1490,3000,120)==0,"Local shortage cannot admit replacements while all global slots are occupied off-map");
  check(PlayerBotPopulationFocus.slots(160,34,2995,3000,120)==5,"Manual and global residents share the native world cap");
  check(PlayerBotPopulationFocus.slots(160,160,300,3000,120)==0,"Filled local target stops new admissions");
  for(int mask=0;mask<32;mask++) {
   boolean autonomous=(mask&1)!=0,closing=(mask&2)!=0,idle=(mask&4)!=0,near=(mask&8)!=0,seen=(mask&16)!=0;
   check(PlayerBotPopulationFocus.protectedActor(autonomous,closing,idle,near,seen)==(!autonomous || closing || !idle || near || seen),
    "Native control, custody/closing, combat/activity, 150m and actual visibility prevent retirement");
  }
  for(int hop=0;hop<1000;hop++) {
   var snapshot=hop%2==0?a:b;
   check(snapshot.prefer(List.of(camp(210040000,700),camp(210030000,700)),Map.of()).getFirst().map()==(hop%2==0?210040000:210030000),
    "Rapid map hops use fresh demand and never reuse old-map selection");
   check(snapshot.target(2995)==2995,"Rapid teleports never shrink the configured population or grow the persistent factory bound");
  }
  for(int radius:List.of(150,199,5001,6000)) {
   try{new PlayerBotPopulationFocus.Snapshot(List.of(),radius);throw new AssertionError("Invalid focus settings accepted");}
   catch(IllegalArgumentException expected){check(true,"Invalid configured radius fails closed");}
  }
  // Reproduce the reported 465/160 regression and verify every supported target.
  check(PlayerBotPopulationFocus.slots(a.target(1200),465,465,1500,120)==120,
   "465 active actors must continue admitting toward 1200 instead of being retired to 160");
  for(int target=0;target<=3000;target++) {
   check(a.target(target)==target && two.target(target)==target && empty.target(target)==target,
    "Player login, logout or map change cannot override any configured population target");
   check(two.mapTarget(1,target)+two.mapTarget(2,target)==target,
    "Occupied-map allocations preserve the complete configured population");
  }
  check(a.mapTarget(210040000,1500)==1500 && two.mapTarget(1,1500)==750 && two.mapTarget(2,1500)==750,
   "One map receives the full 1500 target; two maps share it without a fixed per-map cap");
  check(new PlayerBotPopulationFocus.Snapshot(List.of(anchor(1,500)),160,900).target(1500)==1500,
   "Legacy helper callers passing 160 cannot reduce the configured global target");
  check(new PlayerBotPopulationFocus.Snapshot(List.of(anchor(1,500)),0,900).perMap()==0,
   "Retired quota is ignored and reported as inactive");
  var db=new Jdbc();for(int id=1;id<=3000;id++)db.rows.add(new long[]{id,id%65+1});
  var ids=java.util.stream.IntStream.rangeClosed(1,3000).boxed().toList();
  var levels=PlayerBotRoster.populationLevels(db.connection(),ids,exp->(int)exp);
  check(levels.size()==3000 && db.queries==3,"Three bounded XP queries cover 3000 verified reserve IDs, not 3000 character loads");
  check(levels.get(42)==43 && db.sql.startsWith("SELECT id,exp FROM players WHERE id IN"),"Existing native XP supplies compatible levels without writes/rerolls");
  check(PlayerBotRoster.populationLevels(db.connection(),List.of(),exp->1).isEmpty() && db.queries==3,"Empty reserve issues no query");
  try{levels.put(3001,43);throw new AssertionError("Mutable XP snapshot");}catch(UnsupportedOperationException expected){check(true,"Saved level snapshot immutable");}
  db.fail=true;
  try{PlayerBotRoster.populationLevels(db.connection(),List.of(42),exp->43);throw new AssertionError("SQL failure hidden");}
  catch(SQLException expected){check(true,"Read failure cannot silently reroll/provision replacement identities");}db.fail=false;
  db.rows=List.of(new long[]{42,43},new long[]{42,43});
  try{PlayerBotRoster.populationLevels(db.connection(),List.of(42),exp->43);throw new AssertionError("Duplicate accepted");}
  catch(SQLException expected){check(true,"Duplicate native identity fails closed");}
  try{PlayerBotRoster.populationLevels(db.connection(),List.of(0),exp->43);throw new AssertionError("Invalid ID accepted");}
  catch(IllegalArgumentException expected){check(true,"Invalid identities refused before native read");}
  System.out.println("OK: "+checks+" player-area targets, rapid map changes, visibility/custody protection and batched saved-XP checks; no World/DB/IDs");
 }
}
