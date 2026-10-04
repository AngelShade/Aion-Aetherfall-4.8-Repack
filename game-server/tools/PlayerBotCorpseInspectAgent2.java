import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.services.playerbot.*;
public class PlayerBotCorpseInspectAgent2 {
 public static void agentmain(String report,Instrumentation instrumentation)throws Exception {
  var lines=new ArrayList<String>();
  for(var owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot()){
   lines.add("OWNER "+owner.getName());
   for(var session:PlayerBotService.getInstance().companions(owner))synchronized(session){
    var b=session.bot();
    var t=b.getTarget(); if(t instanceof com.aionemu.gameserver.model.gameobjects.Creature c)lines.add("TARGET "+c.getName()+" pos="+c.getX()+","+c.getY()+","+c.getZ()+" botpos="+b.getX()+","+b.getY()+","+b.getZ()+" distance="+com.aionemu.gameserver.utils.PositionUtil.getDistance(b,c)+" see="+com.aionemu.gameserver.world.geo.GeoService.getInstance().canSee(b,c)+" range="+b.getGameStats().getAttackRange().getCurrent()+" atkRange="+com.aionemu.gameserver.utils.PositionUtil.isInAttackRange(b,c,Math.max(1.5f,b.getGameStats().getAttackRange().getCurrent()/1000f))); lines.add("SEES owner="+b.getKnownList().sees(owner)+" known="+b.getKnownList().knows(owner)); lines.add("BOT "+b.getName()+" id="+b.getObjectId()+" hp="+b.getLifeStats().getCurrentHp()+"/"+b.getLifeStats().getMaxHp()+" state="+b.getState()+" fly="+b.getFlyState()+" beforeDeath="+b.getIsFlyingBeforeDeath()+" dead="+b.isDead()+" res="+b.getResStatus()+" rebirth="+b.canUseRebirthRevive()+" attack="+b.canAttack()+" move="+b.canPerformMove()+" cast="+(b.getCastingSkill()==null?"none":b.getCastingSkill().getSkillTemplate().getSkillId())+" "+session.describe());
   }
  }
  Files.write(Path.of(report),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] a)throws Exception{var vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(Path.of(a[1]).toAbsolutePath().toString(),Path.of(a[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
