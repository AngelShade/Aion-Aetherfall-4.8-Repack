package com.aionemu.gameserver.services.playerbot;

import java.util.*;

/** Whole-population admission/login bounds under map hops, pending loads and target reductions. No World/DB/IDs. */
public final class PlayerBotPopulationAdmissionCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args) {
  // The captured worldstats snapshot: 1308/1289 while geographic demand was short.
  int oldSlots=Math.max(0,Math.min(120,Math.min(1289-82,1500-1308)));
  check(oldSlots==120,"Captured off-map shortage previously admitted 120 additional actors above the global target");
  check(PlayerBotPopulationFocus.slots(1289,1308,1308,1500,120)==0,"All 1308 global reservations count regardless of player map");
  check(PlayerBotPopulationRotation.plan(1500,1500,true,2,23,1289,3352,1308,1308,120).admissionSlots()==0,
   "Native manager uses complete admitted set and retains the older saved reserve");
  check(!PlayerBotPopulationPolicy.mayEnter(true,true,1289,1289,1289,1500),"Final native publication refuses a pending global load at target");
  check(!PlayerBotPopulationPolicy.mayEnter(true,true,1289,1308,1308,1500),"Existing excess cannot grow while safely retiring");
  check(!PlayerBotPopulationPolicy.mayEnter(true,false,1500,0,0,1500),"Disabled population blocks an already loaded global candidate");
  check(PlayerBotPopulationPolicy.mayEnter(false,false,0,0,1499,1500),"Manual world pool remains independent of global enable and target");
  check(!PlayerBotPopulationPolicy.mayEnter(false,true,1500,0,1500,1500),"Manual publication still shares hard world cap");
  int adjusted=PlayerBotPopulationPolicy.target(1289,1500,250);
  check(adjusted==1250 && !PlayerBotPopulationPolicy.mayEnter(true,true,adjusted,1250,1499,1500),
   "Manual capacity added during loading reduces current global target before publication");
  check(!PlayerBotPopulationPolicy.mayEnter(true,true,1200,1288,1288,1500),"A target reduction during native loading cannot publish stale admission");
  check(PlayerBotPopulationPolicy.mayEnter(true,true,1289,1288,1288,1500),"One real released slot permits one replacement");
  // Closing or unspawned save-held actors are supplied as occupied sessions, never subtracted for new admission.
  check(!PlayerBotPopulationPolicy.mayEnter(true,true,1289,1289,1289,1500),"Save-held lease continues to occupy the last slot");
  for(int target=0;target<=3000;target++) {
   for(int delta:new int[]{-1,0,1,120}) {
    int admitted=Math.max(0,target+delta),manual=7,cap=3000;
    int bounded=PlayerBotPopulationPolicy.target(target,cap,manual),total=admitted+manual;
    int slots=PlayerBotPopulationFocus.slots(bounded,admitted,total,cap,120);
    check(slots>=0 && slots<=120 && (slots==0 || admitted+slots<=bounded && total+slots<=cap),
     "Every supported global target bounds geographic admission including off-map/manual/pending actors");
    check(slots==PlayerBotPopulationRotation.plan(3000,cap,true,2,23,bounded,6003,admitted,total,120).admissionSlots(),
     "Focused and worldwide managers use identical complete-population arithmetic");
    boolean enter=PlayerBotPopulationPolicy.mayEnter(true,true,bounded,admitted,total,cap);
    check(!enter || admitted+1<=bounded && total+1<=cap,"Publication cannot cross configured global/world bounds");
   }
  }
  // Moving geography changes demand, not occupied slots. No new IDs or reserve growth across 1000 rapid map hops.
  var reserve=Set.of(1,2,3,4,5,6,7,8);var active=new HashSet<>(Set.of(1,2,3,4));
  int target=4;
  for(int hop=0;hop<1000;hop++) {
   var demand=new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(hop%2+1,500,500,100)),900);
   check(demand.active() && PlayerBotPopulationFocus.slots(target,active.size(),active.size(),8,120)==0,
    "A full population on another map must relocate/retire before admitting replacements");
   int departing=active.iterator().next();active.remove(departing);
   int slots=PlayerBotPopulationFocus.slots(target,active.size(),active.size(),8,120);
   check(slots==1,"Only a completed native retirement releases a reserve slot");
   for(int id:reserve)if(!active.contains(id) && PlayerBotPopulationPolicy.mayEnter(true,true,target,active.size(),active.size(),8))active.add(id);
   check(active.size()==target && reserve.containsAll(active),"Finite saved reserve reuses identities without exceeding target during rotation");
  }
  System.out.println("OK: "+checks+" whole-population admission, pending-load, manual-capacity, target-shrink and rapid-map rotation checks; no World/DB/IDs");
 }
}
