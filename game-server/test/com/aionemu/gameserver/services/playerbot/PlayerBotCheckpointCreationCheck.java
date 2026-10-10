package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.lang.reflect.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import sun.misc.Unsafe;

/** Production deadlines, configured capacity and creation guards; no native actors in World or SQL. */
public final class PlayerBotCheckpointCreationCheck {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void denied(Method method,Object service,Object owner,String why)throws Exception{
  try{method.invoke(service,owner);throw new AssertionError(why);}catch(InvocationTargetException e){check(e.getCause() instanceof IllegalArgumentException,why);}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  check(PlayerBotCheckpointSchedule.interval(Integer.MAX_VALUE)==2147483647000L,"Save interval cannot overflow integer seconds");
  check(PlayerBotCheckpointSchedule.interval(-1)==30000L,"Native minimum interval preserved");
  var schedule=new PlayerBotCheckpointSchedule();var ids=new HashSet<Integer>();
  for(int i=1;i<=1000;i++)ids.add(i);
  check(schedule.next(ids,0,120000)==null,"First checkpoint deadlines stagger instead of a population burst");
  var last=new HashMap<Integer,Long>();var counts=new HashMap<Integer,Integer>();long now=0;
  while(now<370000){
   Integer id=schedule.next(ids,now,120000);
   if(id!=null){
    if(last.containsKey(id) && now-last.get(id)<120000)throw new AssertionError("Checkpoint repeated before completed interval");
    // A slow complete transaction remains indivisible. No callback admits a second actor.
    now+=60;last.put(id,now);counts.merge(id,1,Integer::sum);schedule.complete(id,now,120000);
   }
   now+=schedule.delay(now);
  }
  check(counts.size()==1000 && counts.values().stream().allMatch(n->n>=2),"1000 characters retain fair periodic checkpoints with realistic transaction cost");
  check(schedule.delay(now)>=25 && schedule.delay(now)<=1000,"Every complete callback yields before the next checkpoint");
  ids.clear();check(schedule.next(ids,now,120000)==null && schedule.delay(now)==1000,"Departed identities lose deadlines without a save or ID release");
  ids.add(2001);check(schedule.next(ids,now,120000)==null,"New identity receives a fresh initial deadline");
  long due=now+120000;Integer selected=schedule.next(ids,due,120000);check(selected!=null && selected==2001,"New identity reaches its checkpoint");
  schedule.complete(2001,due,120000);check(schedule.next(ids,due+119999,120000)==null,"Failed save retry can use the same deadline without discarding session progress");
  check(schedule.next(ids,due+120000,120000)==2001,"Checkpoint failure remains eligible next interval");
  var properties=new Properties();properties.setProperty("gameserver.playerbots.generated.max_per_account","50");
  properties.setProperty("gameserver.playerbots.max_per_owner","5");
  check(ConfigurableProcessor.process(properties,PlayerBotConfig.class).isEmpty(),"Real configuration parses the personal roster capacity");
  check(PlayerBotCreationPolicy.limit()==50 && PlayerBotCreationPolicy.room(49) && !PlayerBotCreationPolicy.room(50),"Configured capacity supports more saved bots than five active companions");
  PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT=0;check(!PlayerBotCreationPolicy.room(0),"Zero capacity prevents new creation");
  PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT=-3;check(PlayerBotCreationPolicy.limit()==0 && !PlayerBotCreationPolicy.room(-1),"Invalid limits/counts cannot open unlimited creation");
  PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT=20;check(!PlayerBotCreationPolicy.room(25),"Lowering capacity retains existing identities and prevents new creation");
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);PlayerBotTravelFormationCheck.unsafe=(Unsafe)f.get(null);
  var owner=PlayerBotTravelFormationCheck.actor(1999990000);
  var constructor=PlayerBotService.class.getDeclaredConstructor();constructor.setAccessible(true);var service=constructor.newInstance();
  var field=PlayerBotService.class.getDeclaredField("sessions");field.setAccessible(true);var sessions=(Map<Integer,PlayerBotSession>)field.get(service);
  for(int i=1;i<=5;i++)sessions.put(owner.id+i,PlayerBotTravelFormationCheck.session(owner,PlayerBotTravelFormationCheck.actor(owner.id+i)));
  var creation=PlayerBotService.class.getDeclaredMethod("ensureCreation",com.aionemu.gameserver.model.gameobjects.player.Player.class);creation.setAccessible(true);
  var recruit=PlayerBotService.class.getDeclaredMethod("ensureOwner",com.aionemu.gameserver.model.gameobjects.player.Player.class);recruit.setAccessible(true);
  creation.invoke(service,owner);check(true,"Actual creation guard permits a full companion party");
  denied(recruit,service,owner,"Actual recruitment guard still enforces five native party companions");
  owner.online=false;denied(creation,service,owner,"Offline owner cannot create");owner.online=true;
  owner.spawned=false;denied(creation,service,owner,"Owner outside native world cannot create");owner.spawned=true;
  owner.tasks.combat=true;denied(creation,service,owner,"Combat timer still blocks creation");owner.tasks.combat=false;
  PlayerBotConfig.ENABLED=false;denied(creation,service,owner,"Disabled module cannot create");PlayerBotConfig.ENABLED=true;
  var shutdown=PlayerBotService.class.getDeclaredField("shuttingDown");shutdown.setAccessible(true);shutdown.setBoolean(service,true);
  denied(creation,service,owner,"Shutdown draining cannot acquire another creation");
  System.out.println("OK: "+checks+" paced checkpoint/menu creation checks; no world/DB/native IDs");
 }
}
