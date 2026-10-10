package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.configs.main.LegionConfig;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.team.legion.*;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import sun.misc.Unsafe;

/** Production native request consumption, lifecycle/ownership gates and capacity on world-free actors. */
public final class PlayerBotLegionInvitationCheck {
 static int checks;
 static final int QUESTION=SM_QUESTION_WINDOW.STR_GUILD_INVITE_DO_YOU_ACCEPT_INVITATION;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static class Actor extends PlayerBotTradeCheck.Actor {
  ResponseRequester requests;PlayerSettings settings;boolean world,online=true,spawned=true,dead;
  Race race=Race.ELYOS;
  @Override public ResponseRequester getResponseRequester(){return requests;}
  @Override public PlayerSettings getPlayerSettings(){return settings;}
  @Override public String getName(boolean displayCustomTag){return "Synthetic"+id;}
  @Override public Race getRace(){return race;}
  @Override public boolean isWorldBot(){return world;}
  @Override public boolean isOnline(){return online;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isDead(){return dead;}
  @Override public boolean isInGroup(){return true;}
  @Override public boolean isCasting(){return true;}
 }
 static Actor actor(int id,boolean bot,boolean world)throws Exception {
  Actor a=(Actor)PlayerBotTradeCheck.unsafe.allocateInstance(Actor.class);a.id=id;a.companion=bot;a.owner=1;a.world=world;
  a.online=true;a.spawned=true;a.race=Race.ELYOS;a.account=new Account(17);a.settings=new PlayerSettings();a.requests=new ResponseRequester(a);
  return a;
 }
 static void set(Object object,String field,Object value)throws Exception {
  Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);f.set(object,value);
 }
 static PlayerBotSession session(Actor owner,Actor bot)throws Exception {
  PlayerBotSession s=(PlayerBotSession)PlayerBotTradeCheck.unsafe.allocateInstance(PlayerBotSession.class);
  set(s,"owner",owner);set(s,"bot",bot);return s;
 }
 static class Harness {
  Actor inviter,bot;Legion legion;PlayerBotSession session;PlayerBotService service;int accepted,denied;
  Harness(boolean world)throws Exception {
   inviter=actor(1,false,false);bot=actor(2,true,world);legion=new Legion(700,"Synthetic Legion");
   legion.setMemberIds(new java.util.concurrent.CopyOnWriteArrayList<>(List.of(1)));
   LegionMember leader=new LegionMember(1,legion);leader.setRank(LegionRank.BRIGADE_GENERAL);inviter.setLegionMember(leader);
   session=session(world?bot:inviter,bot);service=(PlayerBotService)PlayerBotTradeCheck.unsafe.allocateInstance(PlayerBotService.class);
   set(service,"sessions",new ConcurrentHashMap<>(Map.of(2,session)));set(service,"retiringWorldBots",new HashSet<Integer>());
  }
  boolean queue(){return bot.requests.putRequest(QUESTION,new RequestResponseHandler<Player>(inviter) {
   @Override public void acceptRequest(Player requester,Player responder) {
    accepted++;check(!Thread.holdsLock(service),"Native membership persistence does not hold population monitor");
    check(Thread.holdsLock(session),"Native membership response serialized with companion operations");
    if(legion.addLegionMember(responder.getObjectId()))responder.setLegionMember(new LegionMember(responder.getObjectId(),legion));
   }
   @Override public void denyRequest(Player requester,Player responder){denied++;}
  });}
  void accept() {
   check(queue(),"Fresh native legion request registered");
   check(service.respondLegionInvitation(inviter,bot,legion),"Bot request handled without a client packet");
   check(accepted==1 && denied==0 && bot.getLegion()==legion && legion.isMember(bot.id),"Actual native membership accepted");
   check(!bot.requests.respond(QUESTION,1),"Accepted native request removed exactly once");
  }
  void refuse(String reason)throws Exception {
   check(PlayerBotLegionInvitation.blocker(session,inviter,bot,legion,PlayerBotConfig.ENABLED,PlayerBotConfig.WORLD_ENABLED,
     !serviceUnavailable()).contains(reason),"Explicit native/lifecycle refusal: "+reason);
   check(queue(),"Refused native request registered");
   check(service.respondLegionInvitation(inviter,bot,legion),"Refused bot request consumed");
   check(accepted==0 && denied==1,"Native decline callback used once");
   check(!bot.requests.respond(QUESTION,1),"Declined request cannot later join");
  }
  @SuppressWarnings("unchecked") boolean serviceUnavailable()throws Exception {
   Field f=PlayerBotService.class.getDeclaredField("shuttingDown");f.setAccessible(true);
   Field r=PlayerBotService.class.getDeclaredField("retiringWorldBots");r.setAccessible(true);
   return f.getBoolean(service) || ((Set<Integer>)r.get(service)).contains(2);
  }
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);PlayerBotTradeCheck.unsafe=(Unsafe)f.get(null);
  boolean enabled=PlayerBotConfig.ENABLED,worldEnabled=PlayerBotConfig.WORLD_ENABLED,cross=LegionConfig.LEGION_INVITEOTHERFACTION;
  int capacity=LegionConfig.LEGION_LEVEL1_MAX_MEMBERS;
  try {
   PlayerBotConfig.ENABLED=true;PlayerBotConfig.WORLD_ENABLED=true;LegionConfig.LEGION_LEVEL1_MAX_MEMBERS=4;LegionConfig.LEGION_INVITEOTHERFACTION=false;
   Harness autonomous=new Harness(true);autonomous.accept();check(autonomous.session.owner()==autonomous.bot,"Legion join leaves autonomous owner intact");
   check(autonomous.bot.getLegionMember().getRank()==LegionRank.VOLUNTEER,"Native volunteer rank retained without warehouse-right grants");
   Harness owned=new Harness(false);owned.accept();check(owned.session.owner()==owned.inviter,"Private ownership unchanged");
   Harness controlled=new Harness(true);Actor controller=actor(9,false,false);set(controlled.session,"owner",controller);controlled.accept();
   check(controlled.session.owner()==controller,"Guild permission ignores existing group control like upstream ignoreGroup=true");
   Harness missing=new Harness(true);((Map<Integer,PlayerBotSession>)field(missing.service,"sessions")).clear();
   check(missing.queue(),"Unmanaged bot request queued");check(missing.service.respondLegionInvitation(missing.inviter,missing.bot,missing.legion),"Missing managed session consumes request");check(missing.denied==1 && missing.accepted==0,"Unmanaged bot declined");
   Harness closing=new Harness(true);set(closing.session,"closing",true);closing.refuse("unavailable");
   Harness shutdown=new Harness(true);set(shutdown.service,"shuttingDown",true);shutdown.refuse("unavailable");
   Harness retiring=new Harness(true);((Set<Integer>)field(retiring.service,"retiringWorldBots")).add(2);retiring.refuse("unavailable");
   Harness disabled=new Harness(true);PlayerBotConfig.ENABLED=false;disabled.refuse("unavailable");PlayerBotConfig.ENABLED=true;
   Harness worldOff=new Harness(true);PlayerBotConfig.WORLD_ENABLED=false;worldOff.refuse("disabled");
   Harness ownedWorldOff=new Harness(false);ownedWorldOff.accept();PlayerBotConfig.WORLD_ENABLED=true;
   Harness foreignOwner=new Harness(false);set(foreignOwner.session,"owner",actor(9,false,false));foreignOwner.refuse("Only the owner");
   Harness wrongOwnerId=new Harness(false);wrongOwnerId.bot.owner=9;wrongOwnerId.refuse("Only the owner");
   Harness wrongAccount=new Harness(false);wrongAccount.bot.account=new Account(18);wrongAccount.refuse("Only the owner");
   Harness unspawned=new Harness(true);unspawned.bot.spawned=false;unspawned.refuse("unavailable");
   Harness disconnected=new Harness(true);disconnected.inviter.online=false;disconnected.refuse("connected player");
   Harness dead=new Harness(true);dead.inviter.dead=true;dead.refuse("living connected");
   Harness botInviter=new Harness(true);botInviter.inviter.companion=true;botInviter.refuse("connected player");
   Harness wrongSession=new Harness(true);set(wrongSession.session,"bot",actor(8,true,true));wrongSession.refuse("unavailable");
   Harness already=new Harness(true);already.bot.setLegionMember(new LegionMember(2,new Legion(701,"Existing")));already.refuse("already belongs");
   Harness left=new Harness(true);left.inviter.setLegionMember(null);left.refuse("membership has changed");
   Harness removed=new Harness(true);removed.legion.getMemberIds().clear();removed.refuse("membership has changed");
   Harness disband=new Harness(true);disband.legion.setDisbandTime(1);disband.refuse("disbanding");
   Harness rights=new Harness(true);rights.inviter.getLegionMember().setRank(LegionRank.VOLUNTEER);rights.legion.setLegionPermissions((short)0,(short)0,(short)0,(short)0);rights.refuse("invitation rights");
   Harness denied=new Harness(true);denied.bot.settings.setDeny(DeniedStatus.GUILD.getId());denied.refuse("native settings");
   Harness faction=new Harness(true);faction.bot.race=Race.ASMODIANS;faction.refuse("other faction");
   Harness crossFaction=new Harness(true);crossFaction.bot.race=Race.ASMODIANS;LegionConfig.LEGION_INVITEOTHERFACTION=true;crossFaction.accept();LegionConfig.LEGION_INVITEOTHERFACTION=false;
   Harness full=new Harness(true);LegionConfig.LEGION_LEVEL1_MAX_MEMBERS=1;check(full.queue(),"Full legion request registered");full.service.respondLegionInvitation(full.inviter,full.bot,full.legion);
   check(full.accepted==1 && full.bot.getLegion()==null && full.legion.getMemberIds().equals(List.of(1)),"Native capacity refuses insertion without a fabricated successful join");
   check(!full.bot.requests.respond(QUESTION,1),"Capacity failure still consumes native request");LegionConfig.LEGION_LEVEL1_MAX_MEMBERS=4;
   Harness duplicate=new Harness(true);check(duplicate.queue(),"First pending request queued");check(!duplicate.queue(),"Native busy handling rejects duplicate invitation");
   duplicate.service.respondLegionInvitation(duplicate.inviter,duplicate.bot,duplicate.legion);duplicate.service.respondLegionInvitation(duplicate.inviter,duplicate.bot,duplicate.legion);
   check(duplicate.accepted==1 && duplicate.legion.getMemberIds().equals(List.of(1,2)),"Repeated dispatch cannot add duplicate member");
   Harness absent=new Harness(true);check(absent.queue(),"Request queued before removal");absent.bot.requests.remove(QUESTION);absent.service.respondLegionInvitation(absent.inviter,absent.bot,absent.legion);
   check(absent.accepted==0 && absent.denied==0 && absent.bot.getLegion()==null,"Absent request cannot synthesize acceptance");
   Harness human=new Harness(true);human.bot.companion=false;check(human.queue(),"Human native request queued");
   check(!human.service.respondLegionInvitation(human.inviter,human.bot,human.legion),"Human stays on client-driven native path");
   check(human.bot.requests.respond(QUESTION,0) && human.denied==1,"Human request retained for actual client response");
   Harness held=new Harness(true);LegionWarehouse.holdCustody(held.legion.getLegionId());held.refuse("custody verification");
   System.out.println("OK: "+checks+" native legion request/ownership/lifecycle/capacity checks; no World/DB/IDs");
  } finally {PlayerBotConfig.ENABLED=enabled;PlayerBotConfig.WORLD_ENABLED=worldEnabled;LegionConfig.LEGION_INVITEOTHERFACTION=cross;LegionConfig.LEGION_LEVEL1_MAX_MEMBERS=capacity;}
 }
 static Object field(Object object,String field)throws Exception {Field f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);}
}
