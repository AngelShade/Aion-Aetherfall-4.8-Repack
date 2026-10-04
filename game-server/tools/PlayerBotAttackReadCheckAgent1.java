import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;

/** Reads final production attack eligibility without ticking AI or executing any action. */
public final class PlayerBotAttackReadCheckAgent1 {
 static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 public static void agentmain(String path,Instrumentation ignored)throws Exception {
  List<String> lines=new ArrayList<>();
  Method valid=PlayerBotSession.class.getDeclaredMethod("validEnemy",Npc.class,List.class,boolean.class);valid.setAccessible(true);
  Method explicit=PlayerBotSession.class.getDeclaredMethod("explicitTarget",Npc.class);explicit.setAccessible(true);
  Method choose=PlayerBotSession.class.getDeclaredMethod("chooseTarget",List.class,List.class);choose.setAccessible(true);
  Method priority=PlayerBotSession.class.getDeclaredMethod("priority",PlayerBotSkills.Entry.class,Creature.class,boolean.class,List.class);priority.setAccessible(true);
  var castType=Class.forName(PlayerBotSession.class.getName()+"$CastAction");
  var constructor=castType.getDeclaredConstructor(PlayerBotSession.class,PlayerBotSkills.Entry.class,Creature.class,List.class);constructor.setAccessible(true);
  var approachType=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotCombatPosition");
  var approach=approachType.getDeclaredMethod("allowSpellApproach",Player.class,PlayerBotRules.Role.class,PlayerBotSkills.Entry.class,Creature.class,List.class);approach.setAccessible(true);
  synchronized(PlayerBotService.getInstance()) {
   for(Player owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot())for(var session:PlayerBotService.getInstance().companions(owner)) {
    if(!session.bot().getName().equalsIgnoreCase("RangeDps"))continue;
    synchronized(session) {
     var bot=session.bot();var party=owner.getPlayerGroup()==null?List.of(owner,bot):owner.getPlayerGroup().getMembers();
     var skills=(List<PlayerBotSkills.Entry>)field(session,"skills");var enemies=new ArrayList<Npc>();
     bot.getKnownList().forEachNpc(n->{try{if((boolean)valid.invoke(session,n,party,explicit.invoke(session,n)))enemies.add(n);}catch(Exception e){throw new IllegalStateException(e);}});
     var target=(Npc)choose.invoke(session,enemies,party);
     lines.add("STATE: "+bot.getName()+" "+session.snapshot().entrySet().stream().filter(e->Set.of("order","status","action","dead","closing","health","mana").contains(e.getKey())).toList());
     lines.add("NATIVE: canAttack="+bot.canAttack()+" canMove="+bot.canPerformMove()+" moving="+bot.getMoveController().isInMove()+" casting="+bot.isCasting()+" stance="+bot.getState()+" weaponRange="+bot.getGameStats().getAttackRange().getCurrent()/1000f+" nextDecisionRemaining="+((long)field(session,"nextDecision")-System.currentTimeMillis())+" withhold="+field(session,"withholdDamage"));
     lines.add("EQUIPMENT: "+bot.getEquipment().getEquippedItems().stream().map(i->i.getItemId()+":"+i.getItemTemplate().getName()+" slot="+i.getItemTemplate().getItemSlot()).toList());
     lines.add("TARGET: eligible="+enemies.size()+" selected="+(target==null?"none":target.getObjectId()+":"+target.getName()+" distance="+PositionUtil.getDistance(bot,target)+" visible="+GeoService.getInstance().canSee(bot,target))+" ownerTarget="+(owner.getTarget()==null?"none":owner.getTarget().getObjectId()));
     if(target==null)continue;
     for(var e:skills)if(e.kind()==PlayerBotRules.SkillKind.DAMAGE) {
      var action=(PlayerBotEngine.Action)constructor.newInstance(session,e,target,enemies);
      var nativeSkill=com.aionemu.gameserver.skillengine.SkillEngine.getInstance().getSkillFor(bot,e.template(),target);
      var conditions=e.template().getStartconditions();var actions=e.template().getActions();
      var failures=new ArrayList<String>();
      if(nativeSkill!=null){if(conditions!=null)for(var c:conditions.getConditions())if(!c.validate(nativeSkill))failures.add(c.getClass().getSimpleName());if(actions!=null)for(var a:actions.getActions())if(!a.canAct(nativeSkill))failures.add(a.getClass().getSimpleName());}
      lines.add("SKILL: "+e.template().getSkillId()+" range="+e.range()+" score="+priority.invoke(session,e,target,true,party)+" disabled="+bot.isSkillDisabled(e.template())+" chain="+PlayerBotSkills.chainAvailable(bot,e)+" useful="+action.isUseful()+" possible="+action.isPossible()+" approach="+approach.invoke(null,bot,session.combatRole(),e,target,skills)+" inRange="+PositionUtil.isInRange(bot,target,e.range(),false)+" prerequisites="+action.prerequisites().stream().map(PlayerBotEngine.Action::name).toList()+" nativeFailures="+failures);
     }
    }
   }
  }
  lines.add("OK: read-only production target/skill gates; no action execution, movement, casts, character or DB writes.");
  Files.write(Path.of(path),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}}
}
