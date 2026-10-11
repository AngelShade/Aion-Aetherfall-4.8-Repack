package com.aionemu.gameserver.model.team.alliance;

import java.util.*;

/** Transient native-team ready responses; never a file or durable character state. */
public final class AllianceReadyCheck {
 private final Map<Integer,String> responses=new LinkedHashMap<>();
 private int initiator;
 private long started;
 public synchronized void start(int owner,Collection<Integer> online,long now){
  responses.clear();initiator=owner;started=now;
  for(int id:online)responses.put(id,id==owner?"READY":"WAITING");
 }
 public synchronized boolean reply(int id,boolean ready){
  if(!"WAITING".equals(responses.get(id)))return false;
  responses.put(id,ready?"READY":"NOT_READY");return true;
 }
 public synchronized void departed(int id){if("WAITING".equals(responses.get(id)))responses.put(id,"LEFT");}
 public synchronized int waiting(){return (int)responses.values().stream().filter("WAITING"::equals).count();}
 public synchronized void cancel(){responses.clear();initiator=0;started=0;}
 public synchronized Map<String,Object> snapshot(){
  return Map.of("active",waiting()>0,"waiting",waiting(),"initiatorId",initiator,"started",started,
   "states",responses.entrySet().stream().map(e->Map.of("id",e.getKey(),"status",e.getValue())).toList());
 }
}
