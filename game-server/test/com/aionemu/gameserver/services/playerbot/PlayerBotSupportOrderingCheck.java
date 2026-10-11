package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.knownlist.KnownList;
import com.aionemu.gameserver.utils.PositionUtil;

/** Mutable native health while the production public support list is ordered. No World/SQL/IDs/casts. */
public final class PlayerBotSupportOrderingCheck {
 static int checks;static Unsafe unsafe;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static class Life extends PlayerBotSorcererCheck.Life {
  int reads,initial;Random changes;
  @Override public int getCurrentHp(){return reads++==0 ? initial : changes.nextInt(1000);}
 }
 static PlayerBotSupportCooperationCheck.Actor actor(int id,int health)throws Exception {
  var a=(PlayerBotSupportCooperationCheck.Actor)unsafe.allocateInstance(PlayerBotSupportCooperationCheck.Actor.class);
  a.id=id;a.race=Race.ELYOS;a.pc=PlayerClass.CLERIC;a.spawned=a.playing=a.worldBot=true;a.world=a.instance=1;
  var life=(Life)unsafe.allocateInstance(Life.class);life.initial=health;life.changes=new Random(id);a.life=life;
  a.nearby=new ArrayList<>();
  a.seen=new KnownList(a){@Override public void forEachPlayer(java.util.function.Consumer<Player> c){a.nearby.forEach(c);}};
  return a;
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  var bot=actor(890000,1000);var allies=new ArrayList<PlayerBotSupportCooperationCheck.Actor>();
  for(int i=0;i<1000;i++){var a=actor(890001+i,(i*73)%1000);a.x=i%39;allies.add(a);}
  var expected=new ArrayList<>(allies);
  expected.sort(Comparator.<PlayerBotSupportCooperationCheck.Actor>comparingInt(a->((Life)a.life).initial).thenComparingDouble(a->PositionUtil.getDistance(bot,a)).thenComparingInt(Player::getObjectId));
  Collections.shuffle(allies,new Random(9183));bot.nearby.addAll(allies);
  var actual=PlayerBotSupport.members(bot,List.of(bot));
  check(actual.size()==17 && actual.getFirst()==bot,"Self and the existing sixteen-public-ally limit remain");
  for(var a:allies)check(((Life)a.life).reads==1,"Production comparator never rereads changing HP");
  for(int i=0;i<16;i++)check(actual.get(i+1)==expected.get(i),"Injury/distance/identity ordering uses captured values");
  try{actual.clear();throw new AssertionError("Mutable support result");}catch(UnsupportedOperationException e){check(true,"Selected list remains immutable");}
  boolean reproduced=false;
  for(int run=0;run<2000 && !reproduced;run++) {
   var legacy=new ArrayList<>(allies);Collections.shuffle(legacy,new Random(run));
   try{legacy.sort(Comparator.<Player>comparingDouble(PlayerBotHealing::health).thenComparingDouble(p->PositionUtil.getDistance(bot,p)).thenComparingInt(Player::getObjectId));}
   catch(IllegalArgumentException e){reproduced=e.getMessage().contains("general contract");}
  }
  check(reproduced,"Previous live native-HP comparator reproduces the reported TimSort contract failure");
  System.out.println("OK: "+checks+" captured support ordering and reproduced mutable-comparator checks; no World/SQL/IDs/casts");
 }
}
