package com.aionemu.gameserver.services.playerbot;

import java.util.*;

/** Actual retained visitor index, cross-quest region occupancy and native priority contracts. */
public final class PlayerBotActivityRegionCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static PlayerBotWorldActivities.Goal goal(int quest,PlayerBotQuestRoutes.Kind kind,int npc,float x,float z){
  return new PlayerBotWorldActivities.Goal(quest,kind,new PlayerBotWorldTravel.Place(110010000,npc,x,10,z),10);
 }
 public static void main(String[] args){
  var v=new PlayerBotWorldActivities.Visitors();var random=new Random(11);
  var crowded=goal(0,PlayerBotQuestRoutes.Kind.HUNT,1,10,20);var nearby=goal(0,PlayerBotQuestRoutes.Kind.HUNT,2,30,20);
  var open=goal(0,PlayerBotQuestRoutes.Kind.HUNT,3,200,20);v.replace(null,crowded);
  check(v.choose(List.of(nearby,open),random).equals(open),"Different native NPCs in the same region share actual visitor load");
  var q=goal(99,PlayerBotQuestRoutes.Kind.OBJECT,4,35,20);v.replace(null,q);
  check(v.choose(List.of(nearby,open),random).equals(open),"Different quest families cannot conceal regional congestion");
  var reward=goal(99,PlayerBotQuestRoutes.Kind.REWARD,5,35,20);
  check(v.choose(List.of(open,reward),random).equals(reward),"Earned reward still outranks an empty grind region");
  check(v.choose(List.of(open,q),random)==open,"Exclusive claimed quest point is still excluded");
  var objective=goal(99,PlayerBotQuestRoutes.Kind.HUNT,6,35,20);
  check(v.choose(List.of(open,objective),random).equals(objective),"Available native quest progress retains priority over optional grind");
  var upper=goal(0,PlayerBotQuestRoutes.Kind.HUNT,7,10,805);
  check(v.choose(List.of(nearby,upper),random).equals(upper),"Stacked native capital floors are separate regions");
  v.replace(crowded,null);v.replace(q,null);
  int occupiedChoices=0;for(int i=0;i<100;i++)if(v.choose(List.of(nearby,open),random).equals(nearby))occupiedChoices++;
  check(occupiedChoices>25 && occupiedChoices<75,"Goal departure releases regional occupancy immediately");
  var goals=new ArrayList<PlayerBotWorldActivities.Goal>();for(int region=0;region<20;region++)for(int npc=0;npc<5;npc++)
   goals.add(goal(0,PlayerBotQuestRoutes.Kind.HUNT,100+region*5+npc,region*100+5,20));
  int[] distribution=new int[20];var claims=new ArrayList<PlayerBotWorldActivities.Goal>();
  for(int i=0;i<600;i++){var chosen=v.choose(goals,random);if(chosen==null)throw new AssertionError("Premature artificial population limit");
   distribution[(int)chosen.place().x()/100]++;claims.add(chosen);v.replace(null,chosen);}
  check(Arrays.stream(distribution).allMatch(n->n==30),"600 commitments spread over 20 native regions without lowering any visitor limit");
  check(claims.stream().map(g->g.place().npc()).distinct().count()==100,"Within equally used regions native points remain randomized");
  for(var g:claims)v.replace(g,null);
  check(v.choose(List.of(nearby,open),new Random(3))!=null,"All regional claims release without leaking reservations");
  for(int i=0;i<100;i++)v.replace(null,crowded);
  check(v.choose(List.of(crowded),random)==null,"Original 100-visitor grind destination cap remains");
  for(int i=0;i<100;i++)v.replace(crowded,null);
  var dialog=goal(0,PlayerBotQuestRoutes.Kind.CONVERSATION,8,35,20);
  for(int i=0;i<15;i++)v.replace(null,dialog);
  check(v.choose(List.of(dialog),random)==null,"Original 15-visitor conversation cap remains");
  check(PlayerBotWorldActivities.spread(List.of(nearby,open),List.of(crowded),new Random(1)).equals(open),"Diagnostic helper exercises the same production regional selection");
  System.out.println("OK: "+checks+" native activity region spread checks; no World/DB/IDs");
 }
}
