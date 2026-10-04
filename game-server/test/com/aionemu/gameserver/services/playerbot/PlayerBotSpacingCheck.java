package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.templates.BoundRadius;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Uses world-free existing fixture actors; no native registration, DB or IDs. */
public final class PlayerBotSpacingCheck {
 static int checks;
 static class Named extends PlayerBotPositionCheck.Actor { @Override public String getName(boolean customTag){return "FixtureRange";} }
 static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
 static void rejected(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] ignored)throws Exception {
  var access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);PlayerBotPositionCheck.u=(sun.misc.Unsafe)access.get(null);
  var owner=PlayerBotPositionCheck.actor(1999999200,PlayerClass.SORCERER);
  var bot=(Named)PlayerBotPositionCheck.u.allocateInstance(Named.class);bot.id=1999999201;bot.pc=PlayerClass.RANGER;bot.account=owner.account;bot.stats=owner.stats;
  var session=PlayerBotPositionCheck.session(owner,bot,Role.RANGED);
  var path=PlayerBotSpacing.path(bot.id);check(!Files.exists(path),"Private fixture path absent");
  try {
   check(PlayerBotSpacing.values(bot).follow()==4&&PlayerBotSpacing.values(bot).attack()==10,"Compact default owner/target spacing");
   for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,1,13})rejected(()->PlayerBotSpacing.validate(bad,10),"Reject owner spacing");
   for(float bad:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,-1,3,19})rejected(()->PlayerBotSpacing.validate(4,bad),"Reject attack spacing");
   PlayerBotSpacing.configure(session,3,12);check(Files.exists(path),"Explicit save persists spacing");
   PlayerBotSpacing.close(bot);check(PlayerBotSpacing.values(bot).follow()==3&&PlayerBotSpacing.values(bot).attack()==12,"Reload exact account-owned settings");
   check(PlayerBotSpacing.attack(bot,23.5f)==12,"User distance overrides longer native spells");
   check(PlayerBotSpacing.attack(bot,7)==7,"Native range remains authoritative");
   var sessionsField=PlayerBotService.class.getDeclaredField("sessions");sessionsField.setAccessible(true);
   var sessions=(Map<Integer,PlayerBotSession>)sessionsField.get(PlayerBotService.getInstance());check(sessions.isEmpty(),"World-free service registry empty");
   var action=com.aionemu.gameserver.services.PlayerBotHttpService.class.getDeclaredMethod("action",com.aionemu.gameserver.model.gameobjects.player.Player.class,Map.class);action.setAccessible(true);
   var request=Map.of("action","spacing","name","FixtureRange","ownerSpacing","3","attackSpacing","12");sessions.put(bot.id,session);
   try {
    check("Companion request completed.".equals(action.invoke(null,owner,request)),"Production HTTP action saves selected companion spacing");
    var stranger=PlayerBotPositionCheck.actor(1999999204,PlayerClass.SORCERER);
    try{action.invoke(null,stranger,request);throw new AssertionError("Foreign owner HTTP request accepted");}catch(java.lang.reflect.InvocationTargetException e){check(e.getCause() instanceof IllegalArgumentException,"Production route rejects another owner's companion");}
    PlayerBotPositionCheck.set(session,"closing",true);rejected(()->PlayerBotSpacing.configure(session,4,10),"Closing companion cannot change spacing");PlayerBotPositionCheck.set(session,"closing",false);
   }finally{sessions.clear();}
   var geometry=new PlayerBotNavigation.Point(6,8,0);var point=PlayerBotSpacing.formation(owner,bot,geometry);
   check(Math.abs(Math.hypot(point.x(),point.y())-3)<.001,"Owner spacing radius preserves formation bearing");
   check(Math.abs(point.x()/point.y()-.75)<.001,"Formation direction preserved");
   var tank=PlayerBotPositionCheck.actor(1999999202,PlayerClass.TEMPLAR);tank.account=owner.account;
   check(PlayerBotSpacing.formation(owner,tank,geometry)==geometry,"Tank front geometry unchanged");PlayerBotSpacing.close(tank);
   check(!Files.exists(PlayerBotFormationLayout.path(owner.id)),"Private formation fixture path absent");
   var outer=PlayerBotPositionCheck.actor(1999999204,PlayerClass.RANGER);outer.account=owner.account;
   var middle=PlayerBotPositionCheck.actor(1999999203,PlayerClass.CLERIC);middle.account=owner.account;
   var last=PlayerBotPositionCheck.actor(1999999206,PlayerClass.BARD);last.account=owner.account;
   for(var member:List.of(session,PlayerBotPositionCheck.session(owner,tank,Role.TANK),PlayerBotPositionCheck.session(owner,middle,Role.HEALER),PlayerBotPositionCheck.session(owner,outer,Role.RANGED),PlayerBotPositionCheck.session(owner,last,Role.SUPPORT)))sessions.put(member.bot().getObjectId(),member);
   try{
    PlayerBotFormationLayout.configure(owner.account.getId(),owner.id,"line");
    var innerPoint=PlayerBotFormationLayout.destination(owner,bot,0);var outerPoint=PlayerBotFormationLayout.destination(owner,outer,0);
    check(Math.abs(innerPoint.y()+1.5)<.001,"Inner Line slot scales within owner's spread");
    check(Math.abs(outerPoint.y()+4)<.001,"Outer Line slot keeps full owner spread");
    check(Math.abs(innerPoint.y()-outerPoint.y())>2,"Same-side Line companions retain separate slots");
   }finally{sessions.clear();Files.deleteIfExists(PlayerBotFormationLayout.path(owner.id));PlayerBotSpacing.close(outer);PlayerBotSpacing.close(middle);PlayerBotSpacing.close(last);}
   var target=(PlayerBotPositionCheck.Enemy)PlayerBotPositionCheck.u.allocateInstance(PlayerBotPositionCheck.Enemy.class);target.template=new NpcTemplate();PlayerBotPositionCheck.set(target.template,"boundRadius",new BoundRadius(1,1,1));target.x=1;
   PlayerBotSpacing.observe(bot,target,10000);check(PlayerBotSpacing.canRetreat(bot,target,12),"One emergency retreat available");
   owner.x=100;check(!PlayerBotSpacing.retreat(owner,bot,target,null),"Owner leash prevents further retreat");
   check(!PlayerBotSpacing.canRetreat(bot,target,12),"Consumed retreat cannot loop when enemy follows");
   target.x=2;PlayerBotSpacing.observe(bot,target,12000);check(!PlayerBotSpacing.canRetreat(bot,target,12),"Moving/changing pursuit cannot reset retreat");
   PlayerBotSpacing.observe(bot,null,13000);PlayerBotSpacing.observe(bot,target,14000);check(!PlayerBotSpacing.canRetreat(bot,target,12),"Brief target gap cannot renew kiting");
   PlayerBotSpacing.observe(bot,null,15000);PlayerBotSpacing.observe(bot,null,20000);check(PlayerBotSpacing.canRetreat(bot,target,12),"Five seconds out of combat resets engagement");
   var foreign=PlayerBotPositionCheck.actor(1999999203,PlayerClass.RANGER);
   var invalid=PlayerBotPositionCheck.session(owner,foreign,Role.RANGED);rejected(()->PlayerBotSpacing.configure(invalid,4,10),"Foreign account cannot save");
   bot.account=foreign.account;try{PlayerBotSpacing.values(bot);throw new AssertionError("Cached owner mismatch accepted");}catch(IllegalStateException expected){checks++;}finally{bot.account=owner.account;}
  }finally{PlayerBotSpacing.close(bot);Files.deleteIfExists(path);}
  System.out.println("OK: "+checks+" compact spacing, range cap, owner identity, persistence, formation and finite-retreat checks; no world/DB/ID writes.");
 }
}
