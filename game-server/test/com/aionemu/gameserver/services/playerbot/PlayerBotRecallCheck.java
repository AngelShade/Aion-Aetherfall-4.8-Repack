package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.world.WorldMap2DInstance;
import static com.aionemu.gameserver.services.playerbot.PlayerBotTravelFormationCheck.*;

/** Actual recall eligibility and scoped native transition gates; no world/DB/ID activity. */
public final class PlayerBotRecallCheck {
 static int assertions;
 static void require(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  var owner=actor(1999980020);var bot=actor(1999980021);bot.owner=owner.id;
  var group=(Group)unsafe.allocateInstance(Group.class);group.players=List.of(owner,bot);owner.group=bot.group=group;
  owner.map=(WorldMap2DInstance)unsafe.allocateInstance(WorldMap2DInstance.class);
  var session=session(owner,bot);var other=session(owner,bot);
  for(boolean casting:new boolean[]{false,true})for(boolean trading:new boolean[]{false,true})
   for(boolean looting:new boolean[]{false,true})for(boolean combat:new boolean[]{false,true})
    for(boolean item:new boolean[]{false,true})for(boolean interaction:new boolean[]{false,true}) {
     bot.casting=casting;bot.trading=trading;bot.looting=looting;bot.tasks.combat=combat;bot.tasks.item=item;bot.tasks.interaction=interaction;
     require(PlayerBotRecall.eligible(session),"Busy or fighting bot remains recall eligible");
     PlayerBotSummonPolicy.preflight(List.of(session));assertions++;
    }
  owner.tasks.combat=true;
  require(PlayerBotRecall.eligible(session),"Owner combat does not prevent automatic recall");
  rejected(()->PlayerBotSummonPolicy.preflight(List.of(session)),"Owner combat still prevents manual summon");assertions++;
  owner.tasks.combat=false;
  for(double distance:new double[]{0,18,59.999,60,60.001,100,1000,Double.NaN,Double.POSITIVE_INFINITY}) {
   require(PlayerBotRecall.needed(distance,false)==(Double.isFinite(distance) && distance>60),"Finite 60 metre boundary");
   require(PlayerBotRecall.needed(distance,true),"Native map/instance transfer also recalls");
  }
  require(!PlayerBotRecall.recalling(session) && !PlayerBotRecall.recalling(bot),"No force context leaks before transition");
  require(PlayerBotRecall.scoped(session,()->{
   require(PlayerBotRecall.recalling(session) && PlayerBotRecall.recalling(bot),"Force context matches exact owned session/actor");
   require(!PlayerBotRecall.recalling(owner),"Human owner is never forced");
   require(!PlayerBotRecall.recalling(other),"Other session is not authorized by current force context");
   return true;
  }),"Actual scoped transition runs");
  require(!PlayerBotRecall.recalling(session),"Success clears force context");
  try{PlayerBotRecall.scoped(session,()->{throw new IllegalStateException("fixture");});throw new AssertionError("Exception swallowed");}
  catch(IllegalStateException expected){require(!PlayerBotRecall.recalling(session),"Failure clears force context");}
  bot.casting=false;field(bot.mover,"failed",true);
  bot.mover.abortMove();require(bot.mover.hasFailed(),"Ordinary stop never conceals a movement failure");
  PlayerBotRecall.scoped(session,()->{bot.mover.abortMove();return true;});
  require(!bot.mover.hasFailed(),"Owned native recall restores failed mover for FOLLOW");
  bot.owner++;require(!PlayerBotRecall.eligible(session),"Foreign bot blocked");bot.owner--;
  bot.group=null;require(!PlayerBotRecall.eligible(session),"Different party blocked");bot.group=group;
  owner.dead=true;require(!PlayerBotRecall.eligible(session),"Dead owner cannot be destination");owner.dead=false;
  owner.online=false;require(!PlayerBotRecall.eligible(session),"Offline owner blocked");owner.online=true;
  owner.spawned=false;require(!PlayerBotRecall.eligible(session),"Unspawned owner blocked");owner.spawned=true;
  owner.map=null;require(!PlayerBotRecall.eligible(session),"Missing native instance blocked");owner.map=(WorldMap2DInstance)unsafe.allocateInstance(WorldMap2DInstance.class);
  field(session,"closing",true);require(!PlayerBotRecall.eligible(session),"Dismissal/custody hold blocked");
  require(!PlayerBotRecall.scoped(session,()->{throw new AssertionError("Held session moved");}),"Held session cannot enter native recall");
  System.out.println("OK: "+assertions+" recall gates, action-state combinations, distance boundaries, scoped cleanup and actual mover reset; no world/DB/IDs");
 }
}
