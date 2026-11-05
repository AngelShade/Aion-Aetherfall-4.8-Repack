package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.PlayerBotWardenConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.legion.LegionWarehouse;
import com.aionemu.gameserver.services.LegionService;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Regional native legions, outside the generated pool. The registry is a SQL index, never a shadow roster. */
public final class PlayerBotWardenCompanies {
 private static final Set<Integer> IDS=ConcurrentHashMap.newKeySet(),BUSY=ConcurrentHashMap.newKeySet();
 private static final Map<Integer,String> REGIONS=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> NEXT=new ConcurrentHashMap<>();
 public static void initialize(Connection c)throws SQLException {
  try(var s=c.prepareStatement("SELECT legion_id,map_id,race FROM playerbot_warden_companies WHERE company_no>0");var r=s.executeQuery()) {
   while(r.next()){IDS.add(r.getInt(1));REGIONS.put(r.getInt(1),r.getInt(2)+":"+r.getString(3));}
  }
 }
 public static boolean company(int legion){return IDS.contains(legion);}
 static String name(String base,int company) {
  if(company<1 || company>100)throw new IllegalArgumentException("Invalid company number");
  String suffix=company==1?"":switch(company){case 2->" Second Company";case 3->" Third Company";case 4->" Fourth Company";case 5->" Fifth Company";case 6->" Sixth Company";case 7->" Seventh Company";case 8->" Eighth Company";case 9->" Ninth Company";case 10->" Tenth Company";default->" Company "+(char)('A'+(company-11)/26)+(char)('A'+(company-11)%26);};
  String stem=(company==1?base:base.replaceAll(" Road Wardens$| Wardens$","")).replaceAll("[^a-zA-Z ]","").trim();
  if(stem.length()+suffix.length()>32)stem=stem.substring(0,32-suffix.length()).trim();String result=stem+suffix;
  if(!result.matches("[a-zA-Z ]{2,32}"))throw new IllegalArgumentException("Invalid native company name");return result;
 }
 /** Wardens who belong to a player's legion. A human legion is never commandeered, so they serve under its name. */
 private static final Set<Integer> IN_PLAYER_LEGION=java.util.concurrent.ConcurrentHashMap.newKeySet();
 static boolean matches(Player bot,PlayerBotPatrolRoutes.Route route){return route!=null && bot.isLegionMember() && (IN_PLAYER_LEGION.contains(bot.getObjectId()) || (route.map()+":"+bot.getRace()).equals(REGIONS.get(bot.getLegion().getLegionId())));}
 static void maintain(PlayerBotRoadDuty.State s) {
  var bot=s.session.bot();if(!PlayerBotWardenConfig.ENABLED || s.career!=PlayerBotRoadDuty.Career.WARDEN)return;
  if(s.route==null) {
   clearTitle(bot);int actor=bot.getObjectId();long now=System.currentTimeMillis();
   if(!bot.isLegionMember() || !company(bot.getLegion().getLegionId()) || now<NEXT.getOrDefault(actor,0L) || !BUSY.add(actor))return;
   NEXT.put(actor,now+30000);ThreadPoolManager.getInstance().execute(()->{try{synchronized(s.session){if(s.route==null && PlayerBotService.getInstance().current(s.session))reserve(bot);}}
    catch(SQLException error){org.slf4j.LoggerFactory.getLogger(PlayerBotWardenCompanies.class).warn("Warden reserve release awaits storage: {}",error.toString());}finally{BUSY.remove(actor);}});return;
  }
  if(matches(bot,s.route)) {
   int wanted=bot.getRace()==Race.ELYOS?PlayerBotWardenConfig.TITLE_ELYOS:PlayerBotWardenConfig.TITLE_ASMODIANS;
   int actor=bot.getObjectId();long now=System.currentTimeMillis();
   if(wanted>0 && bot.getCommonData().getTitleId()!=wanted && now>=NEXT.getOrDefault(actor,0L) && BUSY.add(actor)) {
    NEXT.put(actor,now+30000);ThreadPoolManager.getInstance().execute(()->{try{synchronized(s.session){if(matches(bot,s.route))title(bot);}}finally{BUSY.remove(actor);}});
   }return;
  }
  long now=System.currentTimeMillis();int actor=bot.getObjectId();if(now<NEXT.getOrDefault(actor,0L) || !BUSY.add(actor))return;NEXT.put(actor,now+30000);
  var route=s.route;
  ThreadPoolManager.getInstance().execute(()->{
   int allocated=0;boolean release=false;
   try {
    synchronized(s.session) {synchronized(LegionWarehouse.CUSTODY_LOCK) {
     if(!PlayerBotService.getInstance().current(s.session) || !s.session.autonomous() || s.route!=route || !PlayerBotPopulationManager.global(bot)
      || bot.isLegionMember() && LegionWarehouse.custodyHeld(bot.getLegion().getLegionId()))return;
     allocated=IDFactory.getInstance().nextId();release=true;
     String map=DataManager.WORLD_MAPS_DATA.getTemplate(route.map()).getName();
     boolean shared=PlayerBotWardenBeats.all().values().stream().anyMatch(b->b.route().map()==route.map() && b.route().race()!=bot.getRace());
     String base=(shared?(bot.getRace()==Race.ELYOS?"Elysea ":"Asmodae "):"")+map+" Road Wardens";
     var q=new PlayerBotLegionRepository.CompanyRequest(bot.getAccount().getId(),actor,bot.getRace(),bot.getName(),route.map(),allocated,base,
      Math.clamp(PlayerBotWardenConfig.COMPANY_SIZE,1,PlayerBotLegionRepository.capacity(8)),bot.getRace()==Race.ELYOS?16:17,(byte)(bot.getRace()==Race.ELYOS?220:95),(byte)(bot.getRace()==Race.ELYOS?190:60),(byte)(bot.getRace()==Race.ELYOS?80:160));
     PlayerBotLegionRepository.Assignment result;
     try(var c=DatabaseFactory.getConnection()) {
      try{release=false;result=PlayerBotLegionRepository.commitCompany(c,q);release=!result.created();}
      catch(PlayerBotLegionRepository.TransactionFailure e){release=e.allocationReusable;throw e;}
     }
     IDS.add(result.legion());REGIONS.put(result.legion(),route.map()+":"+bot.getRace());
     LegionService.getInstance().publishWardenMembership(bot,result.legion());title(bot);
    }}
   }catch(SQLException|RuntimeException error){
    if(error.getCause() instanceof PlayerBotLegionRepository.PlayerLegion){IN_PLAYER_LEGION.add(actor);return;}
    org.slf4j.LoggerFactory.getLogger(PlayerBotWardenCompanies.class).warn("Warden company projection retained for retry: {} (cause: {})",error,error.getCause());
   }
   finally{if(allocated>0 && release)IDFactory.getInstance().releaseId(allocated);BUSY.remove(actor);}
  });
 }
 /** Loader callers finish this before spawning. Active callers hold the native session. */
 static void reserve(Player bot)throws SQLException {
  clearTitle(bot);
  synchronized(LegionWarehouse.CUSTODY_LOCK) {
   if(!bot.isLegionMember() || !company(bot.getLegion().getLegionId()))return;
   int old=bot.getLegion().getLegionId();if(LegionWarehouse.custodyHeld(old))throw new SQLException("Reserve company warehouse custody held");
   try(var c=DatabaseFactory.getConnection()){PlayerBotLegionRepository.releaseCompany(c,bot.getAccount().getId(),bot.getObjectId(),bot.getName(),old);}
   LegionService.getInstance().publishWardenReserve(bot);
  }
 }
 private static void clearTitle(Player bot) {
  int selected=bot.getCommonData().getTitleId();
  if(selected>0 && (selected==PlayerBotWardenConfig.TITLE_ELYOS || selected==PlayerBotWardenConfig.TITLE_ASMODIANS))bot.getTitleList().setDisplayTitle(-1);
 }
 private static void title(Player bot) {
  int title=bot.getRace()==Race.ELYOS?PlayerBotWardenConfig.TITLE_ELYOS:PlayerBotWardenConfig.TITLE_ASMODIANS;
  if(bot.getCommonData().getTitleId()==title)return;
  var template=DataManager.TITLE_DATA.getTitleTemplate(title);if(template==null || template.getRace()!=bot.getRace())return;
  if(!bot.getTitleList().contains(title))bot.getTitleList().addTitle(title,false,0);bot.getTitleList().setDisplayTitle(title);
 }
 private PlayerBotWardenCompanies(){}
}
