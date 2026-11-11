package com.aionemu.gameserver.services.playerbot;

import java.util.List;

/** Bounded native queue recruitment; admission and rebuild protection stay with the actor's owners. */
final class PlayerBotPopulationQueues {
 record Candidate(int character,boolean elyos,int level,boolean ready,boolean rebuildable) {}
 /** A zero level means join unchanged; otherwise use the normal durable factory transition. */
 record Admission(int character,int level) {}
 static Admission plan(List<Candidate> candidates,int minimum,int maximum,int elyosNeeded,int asmodiansNeeded) {
  if(minimum<1 || maximum>65 || minimum>maximum)return null;
  for(var candidate:candidates)if(candidate.ready() && needed(candidate,elyosNeeded,asmodiansNeeded)
   && candidate.level()>=minimum && candidate.level()<=maximum)return new Admission(candidate.character(),0);
  Candidate selected=null;int target=0,distance=Integer.MAX_VALUE;
  for(var candidate:candidates)if(candidate.rebuildable() && needed(candidate,elyosNeeded,asmodiansNeeded)) {
   int level=Math.clamp(candidate.level(),minimum,maximum),delta=Math.abs(candidate.level()-level);
   if(delta>0 && delta<distance){selected=candidate;target=level;distance=delta;}
  }
  return selected==null?null:new Admission(selected.character(),target);
 }
 private static boolean needed(Candidate candidate,int elyos,int asmodians){return (candidate.elyos()?elyos:asmodians)>0;}
 private PlayerBotPopulationQueues() {}
}
