import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.world.World;

/** Reads real companion roles/ranges and invokes pure geometry; no movement or character writes. */
public final class PlayerBotPositionReadCheckAgent3 {
 public static void agentmain(String path,Instrumentation ignored)throws Exception {
  var helper=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotCombatPosition");
  var layout=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotFormationLayout");
  Method order=helper.getDeclaredMethod("formationOrder",Player.class);order.setAccessible(true);
  Method desired=helper.getDeclaredMethod("desired",Player.class,PlayerBotRules.Role.class,List.class);desired.setAccessible(true);
  Method ranged=helper.getDeclaredMethod("ranged",com.aionemu.gameserver.model.PlayerClass.class,PlayerBotRules.Role.class);ranged.setAccessible(true);
  Method point=layout.getDeclaredMethod("point",float.class,float.class,float.class,double.class,int.class,int.class,String.class);point.setAccessible(true);
  Field skills=PlayerBotSession.class.getDeclaredField("skills");skills.setAccessible(true);
  List<String> lines=new ArrayList<>();int checks=0,actors=0;
  synchronized(PlayerBotService.getInstance()) {
   for(Player owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot()) {
    var sessions=PlayerBotService.getInstance().companions(owner);if(sessions.isEmpty())continue;
    var ids=(List<Integer>)order.invoke(null,owner);
    var tanks=sessions.stream().filter(s->s.combatRole()==PlayerBotRules.Role.TANK).toList();
    if(!tanks.isEmpty()) {
     if(tanks.stream().noneMatch(s->s.bot().getObjectId()==ids.getFirst()))throw new AssertionError("Tank did not get first formation slot");checks++;
     for(String shape:List.of("Circle","Box","Line","Spread"))for(double angle:new double[]{0,Math.PI/2,Math.PI,Math.PI*1.5}) {
      Object p=point.invoke(null,0f,0f,0f,angle,0,ids.size(),shape.toLowerCase(Locale.ROOT));
      Method x=p.getClass().getDeclaredMethod("x"),y=p.getClass().getDeclaredMethod("y");x.setAccessible(true);y.setAccessible(true);
      if((float)x.invoke(p)*Math.cos(angle)+(float)y.invoke(p)*Math.sin(angle)<3.39)throw new AssertionError("Tank not forward: "+shape);checks++;
     }
    }
    lines.add("OWNER: "+owner.getName()+" tankFirst="+(!tanks.isEmpty())+" formationOrder="+ids);
    for(var session:sessions) {
     var bot=session.bot();lines.add("SPACING: "+bot.getName()+" "+session.snapshot().entrySet().stream().filter(e->Set.of("rangedSpacing","ownerSpacing","attackSpacing").contains(e.getKey())).toList());var entries=(List<PlayerBotSkills.Entry>)skills.get(session);
     lines.add("DAMAGE RANGES: "+bot.getName()+" "+entries.stream().filter(e->e.kind()==PlayerBotRules.SkillKind.DAMAGE).collect(java.util.stream.Collectors.groupingBy(PlayerBotSkills.Entry::range,java.util.TreeMap::new,java.util.stream.Collectors.counting()))); float reach=(float)desired.invoke(null,bot,session.combatRole(),entries);
     boolean isRanged=(boolean)ranged.invoke(null,bot.getPlayerClass(),session.combatRole());
     if(!Float.isFinite(reach)||reach<1.5f)throw new AssertionError("Invalid reach");checks++;actors++;
     lines.add("COMPANION: "+bot.getName()+" class="+bot.getPlayerClass()+" role="+session.combatRole()+" ranged="+isRanged+" desiredNativeRange="+reach+" learnedSkills="+entries.size());
    }
   }
  }
  lines.add("OK: "+checks+" read-only live role/range/geometry checks across "+actors+" companions; no movement, casts, character, DB or ID writes. Client/geodata acceptance pending.");
  Files.write(Path.of(path),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception {
  var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]);}finally{vm.detach();}
 }
}


