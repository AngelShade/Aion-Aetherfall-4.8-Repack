package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO;
import com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO.Operation;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotGrowth.Origin;
import com.aionemu.gameserver.services.playerbot.PlayerBotRecruitmentPolicy.Roll;

/** The earned route: one ticket, one stored roll, one new bot. A failed creation leaves the roll unfinished, so a retry resumes it with no second charge. */
public final class PlayerBotRecruitment {
 private static final Logger log=LoggerFactory.getLogger(PlayerBotRecruitment.class);
 private static final ThreadLocal<Operation> ACTIVE=new ThreadLocal<>();

 public static Operation recruit(Player owner,String name,String requestKey,String start) {
  if(!PlayerBotConfig.TICKETS_ENABLED)throw new IllegalArgumentException("Recruitment Tickets are not open yet.");
  if(!requestKey.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Recruitment request is invalid.");
  int level=PlayerBotGenerationOptions.choose(start,owner.getLevel());
  var operation=PlayerBotTickets.begin(owner.getAccount().getId(),requestKey);
  if(operation.completed())return operation;
  var advanced=operation.roll().advancedClass();
  operation=PlayerBotTickets.reserve(operation);
  if(operation.completed())return operation;
  ACTIVE.set(operation);
  // A blank name is drawn from the reserved identity, so a retried roll gets the same one.
  String chosen=name==null || name.isBlank()?PlayerBotNames.available(operation.character(),owner.getGender()):name;
  try { PlayerBotGenerationOptions.generate(owner,chosen,level<10?advanced.getStartingClass():advanced,start); }
  finally { ACTIVE.remove(); }
  return operation;
 }
 static boolean active() { return ACTIVE.get()!=null; }
 static boolean alreadyCompleted() {var op=ACTIVE.get();return op!=null && PlayerBotTickets.begin(op.account(),op.requestKey()).completed();}
 static int identity() {var op=ACTIVE.get();if(op==null || op.character()<=0)throw new IllegalStateException("Paid identity not reserved");return op.character();}
 /** The stored class path a starting-class recruit must take at level 10, or null for any other bot. */
 static PlayerClass rolledClass(Player bot) {
  try {
   var rolled=PlayerBotGrowth.profile(bot).rolledAdvancedClass();
   return rolled!=null && rolled.getStartingClass()==bot.getPlayerClass()?rolled:null;
  } catch(IOException | RuntimeException e) { return null; }
 }
 private static Map<String,Object> roll(Roll roll) {
  var result=new LinkedHashMap<String,Object>();
  result.put("advancedClass",roll.advancedClass().name());result.put("gear",roll.autoEquipRarity().name());result.put("rank",roll.combatRank().name());
  return result;
 }
 /** Balance, stored odds and any roll to show. Reads nothing while tickets are switched off. */
 public static Map<String,Object> snapshot(Player owner) {
  var state=new LinkedHashMap<String,Object>();
  state.put("enabled",PlayerBotConfig.TICKETS_ENABLED);state.put("cost",1);state.put("classCount",11);state.put("odds",PlayerBotRecruitmentPolicy.odds());
  if(!PlayerBotConfig.TICKETS_ENABLED)return state;
  int account=owner.getAccount().getId();
  try {
   var wallet=PlayerBotTickets.wallet(account);
   state.put("tickets",wallet.tickets());state.put("credit",wallet.credit());
   var unfinished=PlayerBotTickets.unfinished(account);
   if(unfinished!=null){var pending=roll(unfinished.roll());pending.put("request",unfinished.requestKey());state.put("unfinished",pending);}
   var completed=PlayerBotTickets.latest(account);
   if(completed!=null){var last=roll(completed.roll());last.put("request",completed.requestKey());last.put("id",completed.character());
    try(var c=com.aionemu.commons.database.DatabaseFactory.getConnection();var q=c.prepareStatement("SELECT name,race,gender FROM players WHERE id=? AND account_id=?")){
     q.setInt(1,completed.character());q.setInt(2,account);
     try(var r=q.executeQuery()){
      if(!r.next())throw new java.sql.SQLException("Completed recruit unavailable");
      last.put("name",r.getString(1));last.put("gender",r.getString(3));
      try { PlayerBotRecruitLook.add(owner,completed.character(),r.getString(2),r.getString(3),last); }
      catch(IOException | RuntimeException e) {
       last.remove("model");last.remove("look");last.remove("items");
       log.warn("Cannot load recruitment reveal look for companion {}; sending the saved result without a model",completed.character(),e);
      }
     }
    }
    state.put("last",last);
   }
  } catch(RuntimeException | SQLException e) {
   state.put("enabled",false);
  }
  return state;
 }
 /** Stages the stored roll on the new identity before its first checkpoint. Legacy generation has no active operation and passes straight through. */
 static void stage(Player generated)throws IOException {
  var operation=ACTIVE.get();if(operation==null)return;
  var roll=operation.roll();
  PlayerBotGrowth.prepareCreation(generated,Origin.GACHA,roll.advancedClass(),roll.autoEquipRarity(),roll.combatRank());

 }
 /** Joins the first checkpoint, so the identity and its roll commit together. */
 static void complete(Connection connection,Player bot)throws SQLException {
  var op=ACTIVE.get();
  if(op!=null && op.character()==bot.getObjectId())PlayerBotRecruitmentDAO.complete(connection,bot.getAccount().getId(),op.id(),bot.getObjectId());
 }
 private PlayerBotRecruitment() {}
}
