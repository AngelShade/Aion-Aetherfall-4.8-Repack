package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.Race;

/** Reproduces lobby placement, local hunting suppression and finite reserve boundaries without World/DB/IDs. */
public final class PlayerBotFieldActivityCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args) {
  var lobby=new PlayerBotWorldPlacement.Location(Race.ASMODIANS,120080000,547,341,93,10,65,true,true);
  var service=new PlayerBotWorldPlacement.Location(Race.ASMODIANS,220080000,100,100,20,55,65,false,true);
  var field=new PlayerBotWorldPlacement.Location(Race.ASMODIANS,220080000,500,500,20,55,65,false,false);
  var catalog=List.of(lobby,service,field);
  var selected=PlayerBotWorldPlacement.select(catalog,Race.ASMODIANS,65,Set.of(),Set.of(),false,0,new Random(1));
  check(selected.equals(List.of(field)),"Field request must select actual monster camps instead of service hubs");
  check(PlayerBotWorldPlacement.select(List.of(lobby),Race.ASMODIANS,65,Set.of(),Set.of(),false,0,new Random(1)).isEmpty(),"Arena lobby alone cannot be advertised as field recovery");
  check(PlayerBotWorldPlacement.select(catalog,Race.ASMODIANS,65,Set.of(),Set.of(),false,100,new Random(1)).equals(List.of(lobby)),"Explicit city placement remains available");
  for(var race:List.of(Race.ELYOS,Race.ASMODIANS))for(int level=1;level<=65;level++) {
   int low=Math.max(1,level-10),high=level+4;
   check(PlayerBotWorldActivities.levelSuitable(level,low),"Native XP-bearing lower target retained "+level);
   check(PlayerBotWorldActivities.levelSuitable(level,high),"Pinned normal target upper bound retained "+level);
   check(!PlayerBotWorldActivities.levelSuitable(level,level+5),"Solo +5 target refused "+level);
   check(!PlayerBotWorldActivities.levelSuitable(level,Math.max(0,level-11)),"No-XP/nonpositive target refused "+level);
  }
  var grind=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(220080000,201001,500,500,20),10);
  var quest=new PlayerBotWorldActivities.Goal(31,PlayerBotQuestRoutes.Kind.HUNT,grind.place(),10);
  check(PlayerBotHunting.opportunistic(grind,201002),"Traveling grinder can attack another eligible nearby monster");
  check(!PlayerBotHunting.opportunistic(quest,201002),"Unrelated target does not displace an active quest objective");
  check(PlayerBotHunting.opportunistic(quest,201001),"Required quest monster remains eligible");
  int capacity=PlayerBotPopulationRotation.poolCapacity(2500,2500,true,2,23);
  check(capacity==5014,"5000 reserve rounds once to 23-character account capacity");
  for(int registered:List.of(0,2358,2500,capacity-1,capacity,capacity+1,capacity+23)) {
   var plan=PlayerBotPopulationRotation.plan(2500,2500,true,2,23,2358,registered,2358,2358,120);
   check(plan.admissionSlots()==0 && plan.provisionSlots()==(registered<capacity?1:0),"Finite saved reserve never grows the full active target: "+registered);
  }
  System.out.println("OK: "+checks+" field activity/native XP/local hunt/finite reserve checks; no World/DB/IDs");
 }
}
