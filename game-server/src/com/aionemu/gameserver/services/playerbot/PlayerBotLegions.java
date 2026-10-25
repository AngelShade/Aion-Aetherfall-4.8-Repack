/* PlayerbotGuildMgr / PlayerbotFactory::InitGuild, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.PlayerBotPopulationConfig;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.legion.LegionRank;
import com.aionemu.gameserver.services.LegionService;
import com.aionemu.gameserver.services.NameRestrictionService;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** One admitted actor at a time. Planning snapshots are disposable; SQL owns membership. */
final class PlayerBotLegions {
 private final Random random=new Random();
 private PlayerBotLegionRepository.Snapshot snapshot;
 private long nextSnapshot,nextAttempt;
 private List<String> names;
 private final Map<Integer,Long> retries=new java.util.concurrent.ConcurrentHashMap<>();
 private final Map<Integer,Integer> projections=new java.util.concurrent.ConcurrentHashMap<>();
 private List<String> names()throws IOException {
  if(names==null) {
   var loaded=Files.readAllLines(Path.of("config/playerbots/legion-names.txt")).stream().map(String::trim)
    .filter(s->!s.isEmpty() && !s.startsWith("#")).distinct().filter(NameRestrictionService::isValidLegionName)
    .filter(s->!NameRestrictionService.isForbidden(s)).toList();
   if(loaded.isEmpty())throw new IOException("No native-valid generated legion names; creation deferred");
   var shuffled=new ArrayList<>(loaded);Collections.shuffle(shuffled,random);names=List.copyOf(shuffled);
  }return names;
 }
 static PlayerBotLegionRepository.Guild choose(PlayerBotLegionRepository.Snapshot state,Race race,int maximum,Random random) {
  var candidates=state.guilds().stream().filter(g->g.eligible(race,maximum)).toList();return candidates.isEmpty()?null:candidates.get(random.nextInt(candidates.size()));
 }
 private PlayerBotLegionRepository.Snapshot snapshot(long now)throws SQLException {
  if(snapshot==null || now>=nextSnapshot) {
   try(var c=DatabaseFactory.getConnection()){c.setReadOnly(true);snapshot=PlayerBotLegionRepository.snapshot(c);}
   nextSnapshot=now+15000;
  }return snapshot;
 }
 boolean available(Race race,long now)throws SQLException,IOException {
  if(!PlayerBotPopulationConfig.BOT_LEGIONS || PlayerBotPopulationConfig.BOT_LEGION_COUNT==0)return false;
  var state=snapshot(now);
  return choose(state,race,PlayerBotPopulationConfig.BOT_LEGION_SIZE,random)!=null
   || state.guilds().size()<PlayerBotPopulationConfig.BOT_LEGION_COUNT && names().stream().anyMatch(s->!state.names().contains(s.toLowerCase(Locale.ROOT)));
 }
 PlayerBotSession candidate(List<PlayerBotSession> actors,long now)throws SQLException,IOException {
  if(now<nextAttempt)return null;nextAttempt=now+1000;
  var ids=new HashSet<Integer>();actors.forEach(s->ids.add(s.bot().getObjectId()));retries.keySet().retainAll(ids);
  var shuffled=new ArrayList<>(actors);Collections.shuffle(shuffled,random);
  for(var session:shuffled)if(!session.bot().isLegionMember() && retries.getOrDefault(session.bot().getObjectId(),0L)<=now
   && (projections.containsKey(session.bot().getObjectId()) || available(session.bot().getRace(),now)))return session;
  return null;
 }
 void assign(Player bot)throws SQLException,IOException {
  long now=System.currentTimeMillis();retries.put(bot.getObjectId(),now+30000);
  if(!PlayerBotPopulationManager.global(bot) || !PlayerBotLease.isReserved(bot.getObjectId()))return;
  if(bot.isLegionMember()){projections.remove(bot.getObjectId());return;}
  // A legacy load can return no cached member after a transient SQL failure.
  // Recover native membership even when the automatic pool is full/disabled.
  int actual;try(var c=DatabaseFactory.getConnection()){c.setReadOnly(true);actual=PlayerBotLegionRepository.membership(c,bot.getObjectId());}
  if(actual>0){projections.put(bot.getObjectId(),actual);LegionService.getInstance().publishPopulationMembership(bot,actual);projections.remove(bot.getObjectId());return;}
  projections.remove(bot.getObjectId());
  if(!PlayerBotPopulationConfig.BOT_LEGIONS || PlayerBotPopulationConfig.BOT_LEGION_COUNT==0)return;
  var state=snapshot(now);var selected=choose(state,bot.getRace(),PlayerBotPopulationConfig.BOT_LEGION_SIZE,random);
  String name=null;int allocated=0;
  if(selected==null) {
   if(state.guilds().size()>=PlayerBotPopulationConfig.BOT_LEGION_COUNT)return;
   name=names().stream().filter(s->!state.names().contains(s.toLowerCase(Locale.ROOT)))
    .filter(NameRestrictionService::isValidLegionName).filter(s->!NameRestrictionService.isForbidden(s)).findFirst().orElse(null);
   if(name==null)return;
   allocated=IDFactory.getInstance().nextId();
  }
  var rank=LegionRank.values()[1+random.nextInt(LegionRank.values().length-1)];
  var request=new PlayerBotLegionRepository.Request(bot.getAccount().getId(),bot.getObjectId(),bot.getRace(),bot.getName(),selected==null?0:selected.id(),allocated,name,rank,
   random.nextInt(50),(byte)random.nextInt(256),(byte)random.nextInt(256),(byte)random.nextInt(256),PlayerBotPopulationConfig.BOT_LEGION_COUNT,PlayerBotPopulationConfig.BOT_LEGION_SIZE);
  boolean release=allocated>0;PlayerBotLegionRepository.Assignment result=null;
  try {
   try(var c=DatabaseFactory.getConnection()) {
    try {
     release=false;result=PlayerBotLegionRepository.commit(c,request);release=allocated>0 && !result.created();
     if(result.legion()>0)projections.put(bot.getObjectId(),result.legion());
    }catch(PlayerBotLegionRepository.TransactionFailure e){release=e.allocationReusable;if(!e.allocationReusable)projections.put(bot.getObjectId(),0);throw e;}
   }
  }finally {
   // An uncertain commit keeps the allocated ID reserved, including a failed
   // rollback. Native SQL membership makes the next attempt idempotent.
   if(allocated>0 && release)IDFactory.getInstance().releaseId(allocated);
   snapshot=null;nextSnapshot=0;
  }
  if(result!=null && result.legion()>0) {
   projections.put(bot.getObjectId(),result.legion());LegionService.getInstance().publishPopulationMembership(bot,result.legion());projections.remove(bot.getObjectId());
  }
 }
 String summary(){return "bot legions="+PlayerBotPopulationConfig.BOT_LEGIONS+" (target "+PlayerBotPopulationConfig.BOT_LEGION_COUNT+", size "+PlayerBotPopulationConfig.BOT_LEGION_SIZE+")";}
 void closed(int character){retries.remove(character);projections.remove(character);}
}
