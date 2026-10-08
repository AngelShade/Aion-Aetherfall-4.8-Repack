package ai.instance.rakes;

import java.util.*;
import com.aionemu.gameserver.ai.*;
import com.aionemu.gameserver.controllers.attack.AggroTarget;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import ai.AggressiveNpcAI;

/** Captain's full native amplifier, stair retreat, add waves and capture/blast encounter. */
@AIName("brasseyegrogget")
public class BrassEyeGroggetAI extends AggressiveNpcAI {
 private final SteelRakeTasks tasks=new SteelRakeTasks(this);
 private final SteelRakeCaptainPlan plan=new SteelRakeCaptainPlan();
 private final Set<Npc> helpers=new HashSet<>(),displays=new HashSet<>();
 private final Deque<SteelRakeCaptainPlan.Point> movement=new ArrayDeque<>();
 private Skill pending;
 private Npc amplifier;
 private long moveDeadline,missingEnemy;
 private boolean enhancePending;
 private int epoch;
 public BrassEyeGroggetAI(Npc owner){super(owner);}
 @Override public boolean canThink(){return !plan.paused();}
 @Override protected synchronized void handleSpawned(){super.handleSpawned();ensureTowers();}
 @Override protected synchronized void handleAttack(Creature creature){
  if(creature==null || creature.isDead() || isInState(AIState.RETURNING))return;
  getOwner().getGameStats().renewLastAttackedTime();
  if(plan.stage()==SteelRakeCaptainPlan.Stage.IDLE){
   getOwner().getGameStats().setFightStartingTime();getOwner().setTarget(creature);
   com.aionemu.gameserver.ai.manager.EmoteManager.emoteStartAttacking(getOwner(),creature);
   com.aionemu.gameserver.ai.handler.ShoutEventHandler.onAttackBegin(this);
   plan.start();ensureTowers();move(false);tasks.later(this::pulse,250);
  }
  if(!plan.paused())super.handleAttack(creature);
 }
 private void ensureTowers(){
  amplifier=getPosition().getWorldMapInstance().getNpc(281180);
  displays.removeIf(n->!n.isSpawned());if(!displays.isEmpty())return;
  // todo 4 towers in the room center and fix coordinates of monsters
  // need snif
  // Implemented from the native display IDs, amplifier anchor and verified room floor.
  // Four native display variants around the native amplifier anchor; Z is derived from the room floor.
  float cx=403.184f,cy=510.165f;int i=0;
  for(float[] offset:new float[][]{{-6,-6},{6,-6},{6,6},{-6,6}}){
   float x=cx+offset[0],y=cy+offset[1];float z=GeoService.getInstance().getZ(getOwner().getWorldId(),x,y,1072.8f,1070.5f,getOwner().getInstanceId());
   if(!Float.isFinite(z))throw new IllegalStateException("Captain display has no native floor at "+x+","+y);
   var object=spawn(281191+i++,x,y,z,(byte)0);if(object instanceof Npc n){n.getEffectController().setAbnormal(AbnormalState.SANCTUARY);displays.add(n);}
  }
 }
 // to do move boss to initial position and set pause move and atack
 // Implemented by the owned forced-movement phase and native staircase nodes.
 private void move(boolean home){
  pending=null;getOwner().getController().cancelCurrentSkill(null);getMoveController().abortMove();movement.clear();
  var route=new ArrayList<>(SteelRakeCaptainPlan.STAIRS);if(home)Collections.reverse(route);
  // Join the authored staircase at the nearest node and walk the remaining nodes in order.
  int nearest=0;double best=Double.MAX_VALUE;
  for(int i=0;i<route.size();i++){var p=route.get(i);double d=PositionUtil.getDistance(getOwner(),p.x(),p.y(),p.z());if(d<best){best=d;nearest=i;}}
  movement.addAll(route.subList(nearest,route.size()));moveNext();
 }
 private void moveNext(){
  while(!movement.isEmpty() && PositionUtil.isInRange(getOwner(),movement.peekFirst().x(),movement.peekFirst().y(),movement.peekFirst().z(),.4f))movement.removeFirst();
  if(movement.isEmpty()){setStateIfNot(AIState.IDLE);plan.arrived(System.currentTimeMillis());moveDeadline=0;return;}
  var p=movement.removeFirst();moveDeadline=System.currentTimeMillis()+20000;setStateIfNot(AIState.FORCED_WALKING);setSubStateIfNot(AISubState.NONE);getOwner().unsetState(com.aionemu.gameserver.model.gameobjects.state.CreatureState.WALK_MODE);getOwner().setState(com.aionemu.gameserver.model.gameobjects.state.CreatureState.ACTIVE,true);getMoveController().forcedMoveToPoint(p.x(),p.y(),p.z());
 }
 @Override protected synchronized void handleMoveArrived(){if(plan.paused()){getMoveController().abortMove();moveNext();}else super.handleMoveArrived();}
 @Override protected void handleMoveValidate(){if(!plan.paused())super.handleMoveValidate();}
 @Override protected void handleTargetTooFar(){if(!plan.paused())super.handleTargetTooFar();}
 @Override protected void handleTargetGiveup(){if(!plan.paused())super.handleTargetGiveup();}
 private synchronized void pulse(){
  if(plan.stage()==SteelRakeCaptainPlan.Stage.IDLE || isInState(AIState.RETURNING))return;
  try{
   long now=System.currentTimeMillis();Creature enemy=victim();
   if(enemy==null || enemy.isDead() || !enemy.isSpawned()){
    if(missingEnemy==0)missingEnemy=now;
    if(now-missingEnemy>5000){resetEncounter();getOwner().getController().loseAggro(true);return;}
   }else missingEnemy=0;
   if(moveDeadline!=0 && now>moveDeadline){resetEncounter();getOwner().getController().loseAggro(true);return;}
   if(pending!=null){if(getOwner().isCasting())return;pending=null;} // interrupted casts retry the same stage
   int hp=getLifeStats().getHpPercentage();
   if(plan.retreat(hp)){protect();if(amplifier!=null)amplifier.getAi().onCustomEvent(0);move(true);return;}
   int wave=plan.wave(now);if(wave!=0)spawnWave(plan.phase(),wave);
   // to do move boss in the room center and remove pause
   // Arrival and completed activation release the pause; it cannot expire mid-staircase.
   if(plan.returning(now)){move(false);return;}
   switch(plan.stage()){
    case ACTIVATING->{if(amplifier==null || !amplifier.isSpawned() || amplifier.isDead()){resetEncounter();getOwner().getController().loseAggro(true);return;}cast(enhancePending?18203:18192,amplifier);}
    case COMBAT->{
     plan.curse(hp);
     if(plan.enhance(hp)){enhancePending=true;getOwner().getEffectController().removeEffect(18191);plan.start();move(false);return;}
     if(plan.rootPending() && amplifier!=null)amplifier.getAi().onCustomEvent(3,getOwner(),epoch);
     if(plan.combo(hp,now)){move(false);return;}
     if(enemy!=null){getOwner().setTarget(enemy);if(!isInState(AIState.FIGHT)){setStateIfNot(AIState.FIGHT);setSubStateIfNot(AISubState.NONE);think();}}
    }
    case PULL->{if(amplifier!=null)amplifier.getAi().onCustomEvent(2,getOwner(),epoch,plan.comboSequence());}
    case BLAST->cast(18195,getOwner());
    default->{}
   }
  }finally{if(plan.stage()!=SteelRakeCaptainPlan.Stage.IDLE)tasks.later(this::pulse,250);}
 }
 // to do some skill boss use
 // Native activation/despair, root, capture and precision cut are completion-driven below.
 private Creature victim(){var enemy=getAggroList().getTarget(AggroTarget.MOST_HATED);return enemy!=null?enemy:getAggroList().stream().map(com.aionemu.gameserver.controllers.attack.AggroInfo::getAttacker).filter(c->c.isSpawned()&&!c.isDead()&&c.getWorldId()==getOwner().getWorldId()&&c.getInstanceId()==getOwner().getInstanceId()&&PositionUtil.isInRange(getOwner(),c,70)).findFirst().orElse(null);}
 private void protect(){getOwner().getController().cancelCurrentSkill(null);SkillEngine.getInstance().getSkill(getOwner(),18191,37,getOwner()).useNoAnimationSkill();}
 private void cast(int id,Creature target){
  if(getOwner().isCasting())return;getMoveController().abortMove();Skill skill=SkillEngine.getInstance().getSkill(getOwner(),id,37,target);
  pending=skill;if(skill==null || !skill.useSkill())pending=null;
 }
 @Override public synchronized void onEndUseSkill(SkillTemplate template,int level){
  super.onEndUseSkill(template,level);if(pending==null || pending.getSkillId()!=template.getSkillId())return;pending=null;
  if(template.getSkillId()==18192 || template.getSkillId()==18203){
   if(plan.stage()!=SteelRakeCaptainPlan.Stage.ACTIVATING)return;
   amplifier.getAi().onCustomEvent(1,getOwner(),epoch,plan.enhanced());
   if(plan.rootPending())amplifier.getAi().onCustomEvent(3,getOwner(),epoch);
   enhancePending=false;SkillEngine.getInstance().getSkill(getOwner(),18190,37,getOwner()).useNoAnimationSkill();getEffectController().removeEffect(18191);plan.activated();resume();
  }else if(template.getSkillId()==18195 && plan.stage()==SteelRakeCaptainPlan.Stage.BLAST){plan.blasted();resume();}
 }
 @Override protected synchronized void handleCustomEvent(int event,Object... args){
  if(args.length<2 || args[0]!=amplifier || !Integer.valueOf(epoch).equals(args[1]) || plan.stage()==SteelRakeCaptainPlan.Stage.IDLE)return;
  if(event==2 && args.length>2 && Integer.valueOf(plan.comboSequence()).equals(args[2]) && plan.stage()==SteelRakeCaptainPlan.Stage.PULL)plan.pulled();
  else if(event==3)plan.rooted();
 }
 // Skill.endCast dispatches ATTACK_COMPLETE after this callback; it owns the next attack scheduling.
 private void resume(){Creature enemy=victim();if(enemy==null)return;getOwner().setTarget(enemy);setStateIfNot(AIState.FIGHT);setSubStateIfNot(AISubState.NONE);}
 private void spawnWave(int phase,int wave){
  int[] ids=SteelRakeCaptainPlan.waveNpcs(phase,wave);float baseX=wave%2==1?379.4199f:381.26767f,baseY=wave%2==1?495.36453f:526.40845f;
  for(int i=0;i<ids.length;i++){
   float x=baseX+i*1.5f,y=baseY;float z=GeoService.getInstance().getZ(getOwner().getWorldId(),x,y,1073.0f,1070.5f,getOwner().getInstanceId());
   if(!Float.isFinite(z))throw new IllegalStateException("Captain summon has no floor at "+x+","+y);
   var object=spawn(ids[i],x,y,z,(byte)(wave%2==1?13:100));if(object instanceof Npc n){helpers.add(n);Creature enemy=victim();if(enemy!=null)n.getAggroList().addHate(enemy,1);}
  }
 }
 private synchronized void resetEncounter(){
  tasks.reset();epoch++;plan.reset();pending=null;enhancePending=false;movement.clear();moveDeadline=missingEnemy=0;getMoveController().abortMove();getOwner().getController().cancelCurrentSkill(null);getEffectController().removeEffect(18191);
  if(amplifier!=null && amplifier.isSpawned())amplifier.getAi().onCustomEvent(0);
  for(Npc n:helpers)if(n.isSpawned())n.getController().delete();helpers.clear();for(Npc n:displays)if(n.isSpawned())n.getController().delete();displays.clear();
 }
 @Override protected void handleBackHome(){resetEncounter();super.handleBackHome();ensureTowers();}
 @Override protected void handleDied(){resetEncounter();super.handleDied();}
 @Override protected void handleDespawned(){resetEncounter();super.handleDespawned();}
}
