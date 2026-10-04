package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.*;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.configs.main.GeoDataConfig;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Production formation destinations and final cast prerequisites on world-free actors. */
public final class PlayerBotPositionCheck {
 static Unsafe u; static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void set(Object o,String name,Object value)throws Exception{for(var c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static class Stats extends PlayerGameStats {
  Stats(){super(null);}
  @Override public Stat2 getAttackRange(){return new AdditionStat(StatEnum.ATTACK_RANGE,1500,null);}
 }
 static final VisibleObjectTemplate TEMPLATE=new VisibleObjectTemplate(){public int getTemplateId(){return 0;}public int getL10nId(){return 0;}public String getName(){return "fixture";}public BoundRadius getBoundRadius(){return new BoundRadius(.5f,.5f,1);}};
 static class Actor extends Player {
  int id;float x,y,z;byte heading;PlayerClass pc;Account account;Stats stats;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public Account getAccount(){return account;}
  @Override public PlayerGameStats getGameStats(){return stats;}
  @Override public float getX(){return x;} @Override public float getY(){return y;} @Override public float getZ(){return z;}
  @Override public byte getHeading(){return heading;}
  @Override public int getWorldId(){return 1;} @Override public int getInstanceId(){return 1;}
  @Override public VisibleObjectTemplate getObjectTemplate(){return TEMPLATE;}
  @Override public boolean isEnemy(Creature target){return target instanceof Enemy;}
  @Override public boolean isFlying(){return false;}
  @Override public boolean canAttack(){return true;}
  @Override public boolean isSkillDisabled(com.aionemu.gameserver.skillengine.model.SkillTemplate t){return false;}
 }
 static class Enemy extends Npc {
  float x,y,z;NpcTemplate template;
  Enemy(){super(null,null,null);}
  @Override public float getX(){return x;} @Override public float getY(){return y;} @Override public float getZ(){return z;}
  @Override public int getWorldId(){return 1;} @Override public int getInstanceId(){return 1;}
  @Override public NpcTemplate getObjectTemplate(){return template;}
 }
 static Actor actor(int id,PlayerClass pc)throws Exception{var p=(Actor)u.allocateInstance(Actor.class);p.id=id;p.pc=pc;p.account=new Account(id);p.stats=(Stats)u.allocateInstance(Stats.class);set(p,"moveController",new PlayerBotMoveController(p));return p;}
 static PlayerBotSession session(Actor owner,Actor bot,Role role)throws Exception{var s=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);set(s,"owner",owner);set(s,"bot",bot);set(s,"role",role);set(s,"order",Order.FOLLOW);return s;}
 public static void main(String[] ignored)throws Exception {
  var access=Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);u=(Unsafe)access.get(null);
  var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new java.io.StringReader("""
   <skill_data>
    <skill_template skill_id="991501" activation="ACTIVE" lvl="1"><properties first_target="TARGET" first_target_range="25" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="100" e="1"/></effects></skill_template>
    <skill_template skill_id="991502" activation="ACTIVE" lvl="1"><properties first_target="TARGET" first_target_range="3" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="100" e="1"/></effects></skill_template>
    <skill_template skill_id="991503" activation="ACTIVE" lvl="1"><properties first_target="TARGET" first_target_range="25" target_relation="FRIEND" target_type="ONLYONE"/><effects><healinstant value="100" e="1"/></effects></skill_template>
   </skill_data>
   """));
  var longSpell=new PlayerBotSkills.Entry(data.getSkillTemplate(991501),1,SkillKind.DAMAGE,25);
  var shortSpell=new PlayerBotSkills.Entry(data.getSkillTemplate(991502),1,SkillKind.DAMAGE,3);
  var heal=new PlayerBotSkills.Entry(data.getSkillTemplate(991503),1,SkillKind.HEAL,25);var skills=List.of(longSpell,shortSpell,heal);
  var specialty=new PlayerBotSkills.Entry(longSpell.template(),1,SkillKind.DAMAGE,29);
  var utility=new PlayerBotSkills.Entry(longSpell.template(),1,SkillKind.DAMAGE,15);
  check(PlayerBotSpacing.nativeDistance(PlayerClass.CLERIC,Role.HEALER,1.5f,List.of(longSpell,longSpell,specialty))==23.5f,"Rare longer spell cannot strand ordinary attacks outside range");
  check(PlayerBotSpacing.nativeDistance(PlayerClass.SORCERER,Role.RANGED,1.5f,List.of(longSpell,longSpell,utility))==23.5f,"Rare shorter ranged utility cannot pull the main rotation forward");
  check(PlayerBotSpacing.nativeDistance(PlayerClass.SORCERER,Role.RANGED,1.5f,List.of(longSpell,utility))==23.5f,"Equal range bands prefer the longer casting distance");
  for(var pc:new PlayerClass[]{PlayerClass.SORCERER,PlayerClass.SPIRIT_MASTER,PlayerClass.RANGER,PlayerClass.GUNNER,PlayerClass.BARD,PlayerClass.CLERIC}){
   Role role=roleFor(pc);check(PlayerBotCombatPosition.desired(pc,role,1.5f,skills)==10f,"Ranged role uses compact default within actual 25m skill: "+pc);
  }
  check(PlayerBotCombatPosition.desired(PlayerClass.TEMPLAR,Role.TANK,1.5f,skills)==1.5f,"Tank retains melee range despite ranged utility");
  check(PlayerBotCombatPosition.desired(PlayerClass.CHANTER,Role.SUPPORT,2.5f,skills)==2.5f,"Support Chanter retains melee build");
  var owner=actor(1999999100,PlayerClass.SORCERER);var caster=actor(1999999101,PlayerClass.SORCERER);var tank=actor(1999999199,PlayerClass.TEMPLAR);
  var support=actor(1999999102,PlayerClass.BARD);var healer=actor(1999999103,PlayerClass.CLERIC);
  var service=PlayerBotService.getInstance();var sf=PlayerBotService.class.getDeclaredField("sessions");sf.setAccessible(true);var sessions=(Map<Integer,PlayerBotSession>)sf.get(service);check(sessions.isEmpty(),"World-free service fixture must be empty");
  var cs=session(owner,caster,Role.RANGED);for(var s:List.of(cs,session(owner,tank,Role.TANK),session(owner,support,Role.SUPPORT),session(owner,healer,Role.HEALER)))sessions.put(s.bot().getObjectId(),s);
  boolean previous=GeoDataConfig.CANSEE_ENABLE;GeoDataConfig.CANSEE_ENABLE=false;
  try {
   for(String shape:PlayerBotFormationLayout.NAMES){PlayerBotFormationLayout.configure(owner.id,owner.id,shape);
    for(byte heading:new byte[]{0,30,60,90}){owner.heading=heading;var p=PlayerBotFormationLayout.destination(owner,tank,heading*Math.PI/60);
     check((p.x()-owner.x)*Math.cos(heading*Math.PI/60)+(p.y()-owner.y)*Math.sin(heading*Math.PI/60)>3.39,"Highest-ID tank stays in front in "+shape+" heading="+heading);}
   }
   owner.heading=0;PlayerBotFormation.destination(owner,tank,0);var st=PlayerBotFormation.class.getDeclaredField("STATES");st.setAccessible(true);var state=((Map<?,?>)st.get(null)).get(tank.id);set(state,"time",System.currentTimeMillis()-1000);owner.heading=30;PlayerBotFormation.destination(owner,tank,0);var angle=state.getClass().getDeclaredField("angle");angle.setAccessible(true);check(Math.abs(angle.getDouble(state)-Math.PI/2)<.01,"Stationary owner turning updates real formation heading");
   var target=(Enemy)u.allocateInstance(Enemy.class);target.x=20;target.template=new NpcTemplate();set(target.template,"boundRadius",new BoundRadius(1,1,1));
   set(cs,"skills",skills);var type=Class.forName(PlayerBotSession.class.getName()+"$CastAction");var ctor=type.getDeclaredConstructor(PlayerBotSession.class,PlayerBotSkills.Entry.class,Creature.class,List.class);ctor.setAccessible(true);
   var action=(PlayerBotEngine.Action)ctor.newInstance(cs,longSpell,target,List.of());check(action.prerequisites().isEmpty(),"Actual 25m CastAction does not approach an enemy at 20m");
   check(!PlayerBotCombatPosition.allowSpellApproach(caster,Role.RANGED,shortSpell,target,skills),"Short hostile spell cannot pull caster into melee");
   var shortAction=(PlayerBotEngine.Action)ctor.newInstance(cs,shortSpell,target,List.of());check(!shortAction.isPossible(),"Actual final CastAction gate rejects a melee approach from 20m");
   target.x=3;check(PlayerBotCombatPosition.allowSpellApproach(caster,Role.RANGED,shortSpell,target,skills),"Short spell still available when enemy comes close");
   check(PlayerBotCombatPosition.tooClose(caster,target,23.5f),"Caster retreats from close enemies regardless of enemy focus");
   target.x=35;check(!action.prerequisites().isEmpty(),"Actual cast prerequisite moves only outside native range");
   var p=PlayerBotCombatPosition.point(caster,target,23.5f);check(p.x()>caster.x && p.x()<target.x-20,"Production approach waypoint stops at spell range with native body bounds");
   var friend=actor(1999999104,PlayerClass.CLERIC);friend.x=35;check(PlayerBotCombatPosition.allowSpellApproach(caster,Role.RANGED,heal,friend,skills),"Friendly emergency support can still approach its recipient");
   var aerial=PlayerBotCombatPosition.point(0,0,0,0,0,40,25,(byte)0,true);check(Math.abs(aerial.z()-15)<.001,"Flight waypoint preserves three-dimensional spell range");
  }finally{GeoDataConfig.CANSEE_ENABLE=previous;sessions.clear();PlayerBotFormation.close(tank);Files.deleteIfExists(PlayerBotFormationLayout.path(owner.id));}
  System.out.println("OK: "+checks+" production formation, stationary turning, ranged class/build, native bounds and final cast-prerequisite checks. No native world/DB/ID writes; geodata/client acceptance pending.");
 }
}
