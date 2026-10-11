package com.aionemu.gameserver.services.playerbot;

import java.nio.*;
import java.util.*;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;

/** Recording native state bridge and real packet serialization; no Player/World/SQL/native IDs. */
public final class PlayerBotWeaponStanceCheck {
 static int count;
 static void check(boolean value,String reason){count++;if(!value)throw new AssertionError(reason);}
 static final class View implements PlayerBotWeaponStance.View {
  int state=CreatureState.ACTIVE.getId();boolean spawned=true,dead,moving,casting,combat;
  long skillReadyAt,boostExpiresAt=-1;
  final List<EmotionType> sent=new ArrayList<>();final List<Integer> sentState=new ArrayList<>();
  public int state(){return state;}public void state(int s){state=s;}
  public boolean spawned(){return spawned;}public boolean dead(){return dead;}public boolean moving(){return moving;}
  public boolean casting(){return casting;}public boolean combat(){return combat;}
  public long skillReadyAt(){return skillReadyAt;}public boolean animationPending(long now){return now<=boostExpiresAt;}
  public void notify(EmotionType e){sent.add(e);sentState.add(state);}
 }
 public static void main(String[] args)throws Exception {
  int flag=CreatureState.WEAPON_EQUIPPED.getId();
  check(flag==32,"Matching native creature draw flag");
  check(EmotionType.ATTACKMODE_IN_STANDING.getTypeId()==38 && EmotionType.ATTACKMODE_IN_MOVE.getTypeId()==24,"Matching native draw emotion IDs");
  check(EmotionType.NEUTRALMODE_IN_STANDING.getTypeId()==39 && EmotionType.NEUTRALMODE_IN_MOVE.getTypeId()==25,"Matching native neutral emotion IDs");
  // The shared path has no class-specific selection; test every native class, melee/ranged/support alike.
  for(var pc:PlayerClass.values()) {
   var v=new View();
   check(PlayerBotWeaponStance.transition(v,true) && v.state==33,pc+" first cast/attack publishes drawn state");
   check(v.sent.equals(List.of(EmotionType.ATTACKMODE_IN_STANDING)) && v.sentState.equals(List.of(33)),pc+" draw packet sees updated state before action");
   check(!PlayerBotWeaponStance.transition(v,true) && v.sent.size()==1,pc+" repeated actions do not spam draw animations");
   v.casting=true;check(!PlayerBotWeaponStance.transition(v,false) && v.state==33,pc+" support/charged cast keeps weapon in hand");
   v.casting=false;v.combat=true;check(!PlayerBotWeaponStance.transition(v,false) && v.state==33,pc+" combat/aggro prevents neutral reset");
   v.combat=false;check(PlayerBotWeaponStance.transition(v,false) && v.state==1 && v.sent.get(1)==EmotionType.NEUTRALMODE_IN_STANDING,pc+" quiet idle restores native noncombat interaction state");
   check(!PlayerBotWeaponStance.transition(v,false) && v.sent.size()==2,pc+" repeated idle does not spam put-away");
  }
  for(int bits:new int[]{1,1|64,1|128,1|256,1|2|512,1|64|128|256}) {
   var v=new View();v.state=bits;v.moving=true;
   check(PlayerBotWeaponStance.transition(v,true) && v.state==(bits|flag),"Draw preserves unrelated flight/walk/powershard/treatment bits "+bits);
   check(v.sent.get(0)==EmotionType.ATTACKMODE_IN_MOVE,"Native moving draw rather than standing animation "+bits);
   check(PlayerBotWeaponStance.transition(v,false) && v.state==bits && v.sent.get(1)==EmotionType.NEUTRALMODE_IN_MOVE,"Moving neutral preserves unrelated bits "+bits);
  }
  var dead=new View();dead.dead=true;check(!PlayerBotWeaponStance.transition(dead,true) && dead.sent.isEmpty(),"No draw for dead actor");
  dead.state|=flag;check(!PlayerBotWeaponStance.transition(dead,false) && dead.state==33,"No neutral action during death");
  var offline=new View();offline.spawned=false;check(!PlayerBotWeaponStance.transition(offline,true) && offline.sent.isEmpty(),"No draw packet during transfer/offline");
  offline.state|=flag;check(!PlayerBotWeaponStance.transition(offline,false) && offline.state==33,"No neutral packet during transfer/offline");
  var nativePending=new View();nativePending.state=33;nativePending.skillReadyAt=System.currentTimeMillis()+60000;
  check(!PlayerBotWeaponStance.transition(nativePending,false) && nativePending.sent.isEmpty(),"Instant-support regression: native action pending after casting clears cannot emit put-away");
  var instant=new View();instant.state=33;
  check(!PlayerBotWeaponStance.transition(instant,false,1000,1800) && instant.sent.isEmpty(),"Instant friendly spell: casting/combat cleared but existing AI action deadline retains weapon");
  check(instant.state==33,"Pending friendly hit/animation cannot put weapon away");
  check(!PlayerBotWeaponStance.transition(instant,false,1799,1800),"Regular cast tail retained through existing remainingLock");
  check(PlayerBotWeaponStance.transition(instant,false,1800,1800) && instant.state==1,"AI deadline equality allows quiet neutral without arbitrary extra hold");
  check(!PlayerBotWeaponStance.transition(instant,false,1801,1800) && instant.sent.size()==1,"Expired action does not repeat neutral packet");
  var reset=new View();reset.state=33;reset.skillReadyAt=1700;
  check(!PlayerBotWeaponStance.transition(reset,false,1000,0) && reset.state==33,"Manual Follow resets AI deadline but native nextSkillUse still protects emitted instant spell");
  reset.boostExpiresAt=1850;
  check(!PlayerBotWeaponStance.transition(reset,false,1700,0),"Native full-duration animation survives nextSkillUse expiry");
  check(!PlayerBotWeaponStance.transition(reset,false,1850,0),"Native hit-time boost equality retains its exact inclusive deadline");
  check(PlayerBotWeaponStance.transition(reset,false,1851,0) && reset.state==1,"After all native deadlines expire quiet item/equipment stance resumes");
  var charge=new View();charge.state=33;
  check(!PlayerBotWeaponStance.transition(charge,false,4999,5000),"Released charge callback extension retains stance after casting clears");
  check(PlayerBotWeaponStance.transition(charge,false,5000,5000),"Released charge extension expires normally");
  var requested=new View();requested.skillReadyAt=9000;requested.boostExpiresAt=9000;
  check(PlayerBotWeaponStance.transition(requested,true,1000,9000) && requested.state==33,"Native animation retention cannot suppress an explicit draw transition");
  var write=SM_EMOTION.class.getDeclaredMethod("writeImpl",AionConnection.class);write.setAccessible(true);
  for(var emotion:List.of(EmotionType.ATTACKMODE_IN_STANDING,EmotionType.ATTACKMODE_IN_MOVE,EmotionType.NEUTRALMODE_IN_STANDING,EmotionType.NEUTRALMODE_IN_MOVE)) {
   int state=emotion==EmotionType.ATTACKMODE_IN_STANDING || emotion==EmotionType.ATTACKMODE_IN_MOVE?33:1;
   var packet=new SM_EMOTION(12345,emotion,state);var bytes=ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);packet.setBuf(bytes);write.invoke(packet,(AionConnection)null);bytes.flip();
   check(bytes.remaining()==11,"Native stance packet shape "+emotion);
   check(bytes.getInt()==12345 && Byte.toUnsignedInt(bytes.get())==emotion.getTypeId(),"Native object/emotion encoded "+emotion);
   check(Short.toUnsignedInt(bytes.getShort())==state,"Native drawn state visible to observers/newcomers "+emotion);
  }
  System.out.println("OK: "+count+" class-independent weapon draw/idle/state preservation/native packet checks; no native actors/World/SQL/IDs");
 }
}
