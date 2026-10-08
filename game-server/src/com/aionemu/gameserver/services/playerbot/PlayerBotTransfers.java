package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.geo.GeoService;

/** Transfer the complete existing native party; never reload partial character data. */
public final class PlayerBotTransfers {
 private static final Map<Integer,Long> RETRY=new ConcurrentHashMap<>();
 static boolean different(Player owner,Player bot){return owner.getWorldId()!=bot.getWorldId() || owner.getInstanceId()!=bot.getInstanceId();}
 static boolean follow(PlayerBotSession session) {
  Player owner=session.owner(),bot=session.bot();
  if(!owner.isOnline() || !owner.isSpawned() || !different(owner,bot))return false;
  // Snapshot before moving any member: destination combat must not strand later members.
  var party=List.copyOf(PlayerBotService.getInstance().companions(owner));boolean waiting=false;
  for(var member:party) synchronized(member) {
   if(member.closing() || !different(owner,member.bot()))continue;waiting=true;
   long now=System.currentTimeMillis();if(now<RETRY.getOrDefault(member.bot().getObjectId(),0L))continue;
   try {
    if(!PlayerBotService.getInstance().relocate(member))RETRY.put(member.bot().getObjectId(),now+1000);
    else RETRY.remove(member.bot().getObjectId());
   }catch(RuntimeException error){RETRY.put(member.bot().getObjectId(),now+3000);org.slf4j.LoggerFactory.getLogger(PlayerBotTransfers.class).error("Companion {} transfer is retained for retry",member.bot().getName(),error);PlayerBotQuestSync.notice(member.bot(),"I am retrying the party's map transfer; my character has been retained.","transfer-retry",30000);}
  }
  return waiting;
 }
 public static boolean relocate(PlayerBotSession session) {
  Player owner=session.owner(),bot=session.bot();boolean transfer=different(owner,bot);
  if(session.closing() || !owner.isOnline() || !owner.isSpawned() || owner.getWorldMapInstance()==null || (!transfer && !PlayerBotRecall.recalling(session) && !PlayerBotPartyBehavior.canRelocate(session)))return false;
  session.releasePet();bot.getController().cancelCurrentSkill(null);bot.getMoveController().abortMove();bot.getObserveController().notifyMoveObservers();
  if(bot.isFlying() && !owner.isFlying())bot.getFlyController().endFly(false);
  if(bot.isSpawned()) {
   if(transfer && bot.getPosition().getMapRegion()!=null)InstanceService.onLeaveInstance(bot);
   World.getInstance().despawn(bot);
  }
  // No health/level/items/skills/role/order changes. Native despawn clears old-map hate.
  if(owner.getPlayerGroup()==null) {
   if(bot.getPlayerGroup()!=null)PlayerGroupService.removePlayer(bot);
   PlayerGroupService.createGroup(owner,bot,TeamType.GROUP,0);
  }else if(bot.getPlayerGroup()!=owner.getPlayerGroup()) {
   if(bot.getPlayerGroup()!=null)PlayerGroupService.removePlayer(bot);
   PlayerGroupService.addPlayer(owner.getPlayerGroup(),bot);
  }
  var point=PlayerBotFormation.destination(owner,bot,0);float x=point.x(),y=point.y(),z=owner.getZ();
  if(!owner.isFlying()) {
   z=GeoService.getInstance().getZ(owner.getWorldId(),x,y,z,owner.getInstanceId());
   if(!Float.isFinite(z) || Math.abs(z-owner.getZ())>2 || !GeoService.getInstance().canSee(owner,x,y,z,com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)){x=owner.getX();y=owner.getY();z=owner.getZ();}
  }
  if(owner.getWorldMapInstance().getRegion(x,y,z)==null){x=owner.getX();y=owner.getY();z=owner.getZ();}
  if(!World.getInstance().setPosition(bot,owner.getWorldId(),owner.getInstanceId(),x,y,z,owner.getHeading()))return false;
  if(owner.isInInstance())owner.getWorldMapInstance().register(bot.getObjectId());
  World.getInstance().spawn(bot);bot.getController().updateZone();
  if(transfer && owner.isInInstance())InstanceService.onEnterInstance(bot);
  if("GUARD".equals(session.snapshot().get("order")))session.order(PlayerBotRules.Order.GUARD);
  return true;
 }
 static void close(Player bot){RETRY.remove(bot.getObjectId());}
 private PlayerBotTransfers(){}
}
