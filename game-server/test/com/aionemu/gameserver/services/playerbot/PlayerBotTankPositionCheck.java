package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.SkillData;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.BoundRadius;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.skillengine.model.Skill;

/** World-free regression: a boss following its tank cannot move the tank's goal. */
public final class PlayerBotTankPositionCheck {
 static Unsafe u; static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void set(Object o,String name,Object value)throws Exception{for(var c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static class Actor extends Player {
  float x,y; PlayerClass pc;
  Actor(){super(null,null);}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public float getX(){return x;} @Override public float getY(){return y;} @Override public float getZ(){return 0;}
  @Override public int getWorldId(){return 1;} @Override public int getInstanceId(){return 1;}
  @Override public byte getHeading(){return 0;}
  @Override public boolean isDead(){return false;}
 }
 static class Enemy extends Npc {
  float x,y; NpcTemplate template; Skill cast;
  Enemy(){super(null,null,null);}
  @Override public float getX(){return x;} @Override public float getY(){return y;} @Override public float getZ(){return 0;}
  @Override public int getWorldId(){return 1;} @Override public int getInstanceId(){return 1;}
  @Override public byte getHeading(){return 0;}
  @Override public NpcTemplate getObjectTemplate(){return template;}
  @Override public Skill getCastingSkill(){return cast;}
  @Override public boolean isCasting(){return cast!=null;}
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);u=(Unsafe)f.get(null);
  var tank=(Actor)u.allocateInstance(Actor.class);tank.pc=PlayerClass.TEMPLAR;tank.x=2;
  var ally=(Actor)u.allocateInstance(Actor.class);ally.pc=PlayerClass.RANGER;ally.x=-4;
  var boss=(Enemy)u.allocateInstance(Enemy.class);boss.template=new NpcTemplate();set(boss.template,"boundRadius",new BoundRadius(1,1,1));set(boss,"target",tank);
  // Includes party motion, a boss already displaced and a very large model.
  for(float radius:new float[]{1,12}){set(boss.template,"boundRadius",new BoundRadius(radius,radius,1));
   for(int step=0;step<8;step++){boss.x=step*5;tank.x=boss.x+2;ally.x=boss.x-4;ally.y=step%2*4;
    check(PlayerBotCoordination.tankFacing(tank,boss,List.of(tank,ally))==null,"No moving destination beyond a following boss, radius="+radius+" step="+step);
   }
  }
  tank.x=2;boss.x=0;ally.x=0;ally.y=0;
  var data=(SkillData)JAXBContext.newInstance(SkillData.class).createUnmarshaller().unmarshal(new java.io.StringReader("""
   <skill_data><skill_template skill_id="991601" activation="ACTIVE" lvl="1"><properties first_target="TARGET" first_target_range="25" target_relation="ENEMY" target_type="AREA" effective_range="6"/><effects><skillatk value="100" e="1"/></effects></skill_template>
   <skill_template skill_id="991602" activation="ACTIVE" lvl="1"><properties first_target="TARGET" first_target_range="25" target_relation="ENEMY" target_type="ONLYONE"/><effects><skillatk value="100" e="1"/></effects></skill_template></skill_data>
   """));
  boss.cast=(Skill)u.allocateInstance(Skill.class);set(boss.cast,"skillTemplate",data.getSkillTemplate(991601));set(boss.cast,"firstTarget",tank);
  check(PlayerBotCoordination.spread(tank,List.of(tank,ally),List.of(boss))==null,"Tank holding aggro does not drag boss to spread a targeted cast");
  set(boss,"target",ally);
  check(PlayerBotCoordination.spread(tank,List.of(tank,ally),List.of(boss))!=null,"Tank targeted by secondary cast retains spread when not holding its caster");
  set(boss,"target",ally);set(boss.cast,"firstTarget",ally);
  check(PlayerBotCoordination.spread(ally,List.of(tank,ally),List.of(boss))!=null,"Ranged victim retains targeted-area spread");
  set(boss.cast,"skillTemplate",data.getSkillTemplate(991602));
  check(PlayerBotCoordination.spread(ally,List.of(tank,ally),List.of(boss))==null,"Single-target casts do not request spread");
  boss.cast=null;
  check(PlayerBotCoordination.spread(tank,List.of(tank,ally),List.of(boss))==null,"No stale spread after casting ends");
  check(PlayerBotCoordination.tankFacing(tank,null,List.of())==null,"No stale facing goal after target loss");
  System.out.println("OK: "+checks+" tank hold, moving-boss/party, large-body and targeted-cast policy checks; no world/DB actors or movement writes");
 }
}
