package ai.instance.rakes;

import com.aionemu.gameserver.ai.*;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Dormant until its captain activates it; all damage uses the native amplifier skill templates. */
@AIName("steel_rake_stigma_amplifier")
public final class SteelRakeStigmaAmplifierAI extends NpcAI {
 private final SteelRakeTasks tasks=new SteelRakeTasks(this);
 private Npc captain;
 private int epoch,rotation,pullSequence,completedPull;
 private boolean enhanced,root,pull,rootDone;
 private long ready;
 private Skill pending;
 public SteelRakeStigmaAmplifierAI(Npc owner){super(owner);}
 @Override public boolean canThink(){return false;}
 @Override protected void handleSpawned(){super.handleSpawned();getEffectController().setAbnormal(com.aionemu.gameserver.skillengine.effect.AbnormalState.SANCTUARY);}
 @Override protected synchronized void handleCustomEvent(int event,Object... args){
  if(event==0){reset();return;}
  if(args.length<2 || !(args[0] instanceof Npc boss) || !(args[1] instanceof Integer token)
   || !boss.isSpawned() || boss.isDead() || boss.getWorldId()!=getOwner().getWorldId() || boss.getInstanceId()!=getOwner().getInstanceId() || !"brasseyegrogget".equals(boss.getAi().getName()))return;
  if(event==1){reset();captain=boss;epoch=token;enhanced=args.length>2 && Boolean.TRUE.equals(args[2]);ready=System.currentTimeMillis()+4000;tasks.later(this::pulse,250);}
  else if(captain==boss && epoch==token){
   if(event==2 && args.length>2 && args[2] instanceof Integer sequence && sequence>completedPull){pullSequence=sequence;pull=true;}
   else if(event==3 && !rootDone)root=true;
  }
 }
 private synchronized void pulse(){
  if(captain==null || captain.isDead() || !captain.isSpawned()){reset();return;}
  try{
   if(pending!=null){if(getOwner().isCasting())return;pending=null;}
   long now=System.currentTimeMillis();if(getOwner().isCasting())return;
   if(pull){cast(18202);return;}
   if(!captain.getAi().canThink() || now<ready)return;
   int id=root?18200:new int[]{18197,18198,18199}[rotation%3];cast(id);
  }finally{if(captain!=null)tasks.later(this::pulse,250);}
 }
 private void cast(int id){pending=SkillEngine.getInstance().getSkill(getOwner(),id,36,getOwner());if(pending==null || !pending.useSkill())pending=null;}
 @Override public synchronized void onEndUseSkill(SkillTemplate template,int level){
  super.onEndUseSkill(template,level);if(pending==null || pending.getSkillId()!=template.getSkillId())return;pending=null;
  if(template.getSkillId()==18202){
   pull=false;completedPull=pullSequence;notifyCaptain(2,completedPull);
  }else{if(template.getSkillId()==18200){root=false;rootDone=true;notifyCaptain(3,0);}else rotation++;ready=System.currentTimeMillis()+(enhanced?8000:12000);}
 }
 private void notifyCaptain(int event,int sequence){
  Npc boss=captain;int token=epoch;
  // Dispatch outside this actor's monitor. The captain rejects stale encounter/sequence tokens.
  ThreadPoolManager.getInstance().schedule(()->{if(boss!=null && boss.isSpawned()&&!boss.isDead())boss.getAi().onCustomEvent(event,getOwner(),token,sequence);},0);
 }
 private synchronized void reset(){tasks.reset();captain=null;pending=null;rotation=pullSequence=completedPull=0;enhanced=root=pull=rootDone=false;ready=0;getOwner().getController().cancelCurrentSkill(null);}
 @Override protected void handleBackHome(){reset();super.handleBackHome();}
 @Override protected void handleDied(){reset();super.handleDied();}
 @Override protected void handleDespawned(){reset();super.handleDespawned();}
}
