package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

/** Reproduce mutable reaction deadlines and overdue-world starvation without World/DB/native IDs. */
public final class PlayerBotResponsivenessCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args) {
  var actors=new ArrayList<Integer>();for(int i=0;i<3005;i++)actors.add(i);
  Collections.shuffle(actors,new Random(47));
  var reads=new AtomicIntegerArray(3005);
  var ready=PlayerBotDecisionSchedule.ready(actors,Integer::intValue,id->{
   int calls=reads.incrementAndGet(id);
   // Native combat/rest timers may change between reads. Old comparator reevaluates these.
   return calls==1?(id<3000?1000L:10000L):Long.MAX_VALUE;
  },id->id>=3000,10000);
  check(ready.size()==3005,"Every due world bot remains scheduled; no population cap or discarded work");
  for(int i=0;i<5;i++)check(ready.get(i).id()==3000+i,"Real-player companions precede thousands of overdue autonomous actors");
  for(int id:actors)check(reads.get(id)==1,"Capture each mutable actor deadline once instead of reading state from comparisons");
  for(int i=1;i<3000;i++)check(ready.get(i+5).id()==i,"Autonomous actors retain deterministic earliest-deadline/ID order");
  check(PlayerBotDecisionSchedule.ready(List.of(1,2),Integer::intValue,id->id==1?100:101,id->true,100).size()==1,
   "Controlled priority does not execute a not-yet-due actor");
  check(PlayerBotDecisionSchedule.reaction(true,true,false,30)==100 && PlayerBotDecisionSchedule.reaction(true,false,true,30)==100,
   "Pinned game-client-master reaction remains 100ms in combat, noncombat and rest");
  try{ready.clear();throw new AssertionError("Mutable decision snapshot");}catch(UnsupportedOperationException expected){check(true,"Comparator inputs and selected ordering are immutable");}
  System.out.println("OK: "+checks+" companion priority, mutable-deadline and preserved world scheduling checks; no World/DB/IDs");
 }
}
