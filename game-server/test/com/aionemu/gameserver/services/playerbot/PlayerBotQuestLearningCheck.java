package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.model.templates.quest.QuestDrop;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.playerbot.PlayerBotGrowth.*;

/** The 25 Learning XP combat quest award on fixtures, no live accounts or database. */
public final class PlayerBotQuestLearningCheck {
 private static final int ACCOUNT=7,KILL_NPC=999001,DROP_NPC=999002,OTHER_NPC=999003,KILL_QUEST=99001,DROP_QUEST=99002,DELIVERY_QUEST=99003;
 private static int checks,next=1999999100;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static Unsafe unsafe;
 private static class Actor extends Player {
  int id;boolean bot;Account account;QuestStateList quests;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public Account getAccount(){return account;}
  @Override public QuestStateList getQuestStateList(){return quests;}
 }
 private static class Target extends Npc {
  int npcId;Target(){super(null,null,null);}
  @Override public int getNpcId(){return npcId;}
 }
 private static Npc npc(int id)throws Exception{var npc=(Target)unsafe.allocateInstance(Target.class);npc.npcId=id;return npc;}
 private static Map<String,String> growth(int character,Origin origin,CombatRank rank,long earned) {
  boolean traits=origin!=Origin.LEGACY;var map=new HashMap<String,String>();
  map.put("version","1");map.put("account",Integer.toString(ACCOUNT));map.put("character",Integer.toString(character));map.put("origin",origin.name());
  map.put("rolledAdvancedClass",traits ? PlayerClass.TEMPLAR.name() : "");map.put("autoEquipRarity",traits ? GearRarity.GOOD.name() : "");
  map.put("initialCombatRank",traits ? CombatRank.POOR.name() : "");map.put("combatRank",traits ? rank.name() : "");map.put("earnedLearningXp",Long.toString(earned));return map;
 }
 /** Real cache entries, so the real checkpoint section is what the award reads and writes. */
 @SuppressWarnings("unchecked") private static void cache(int character,String section,Map<String,String> values)throws Exception {
  Field f=PlayerBotMetadata.class.getDeclaredField("CACHE");f.setAccessible(true);
  ((Map<Object,Object>)f.get(null)).put(new PlayerBotMetadata.Key(character,section),new PlayerBotMetadata.Entry(ACCOUNT,values.isEmpty() ? 0 : 1,values,values));
 }
 private static Actor actor(boolean bot)throws Exception {
  var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.id=next++;actor.bot=bot;actor.quests=new QuestStateList();
  actor.account=(Account)unsafe.allocateInstance(Account.class);Field f=Account.class.getDeclaredField("id");f.setAccessible(true);f.set(actor.account,ACCOUNT);return actor;
 }
 private static Actor generated(CombatRank rank,long earned)throws Exception {
  var bot=actor(true);cache(bot.id,"growth",growth(bot.id,Origin.GACHA,rank,earned));cache(bot.id,"quest-practice",Map.of());return bot;
 }
 private static long earned(Actor bot)throws Exception{return Long.parseLong(PlayerBotMetadata.cached(ACCOUNT,bot.id,"growth").get("earnedLearningXp"));}
 private static Map<String,String> marks(Actor bot)throws Exception{return PlayerBotMetadata.cached(ACCOUNT,bot.id,"quest-practice");}
 private static void take(Actor bot,int quest,QuestStatus status){bot.quests.addQuest(quest,new QuestState(quest,status));}
 private static void classification()throws Exception {
  var bot=generated(CombatRank.POOR,0);take(bot,KILL_QUEST,QuestStatus.START);take(bot,DROP_QUEST,QuestStatus.REWARD);take(bot,DELIVERY_QUEST,QuestStatus.START);
  check(PlayerBotGrowth.objectives(bot,npc(KILL_NPC)).equals(Set.of(KILL_QUEST)),"A registered kill objective is a combat objective");
  check(PlayerBotGrowth.objectives(bot,npc(DROP_NPC)).equals(Set.of(DROP_QUEST)),"A native quest drop is a combat objective");
  check(PlayerBotGrowth.objectives(bot,npc(OTHER_NPC)).isEmpty(),"A quest with no objective on this NPC is not combat practice");
  bot.quests.getQuestState(KILL_QUEST).setStatus(QuestStatus.COMPLETE);
  check(PlayerBotGrowth.objectives(bot,npc(KILL_NPC)).isEmpty(),"A quest that is not open gets no practice");
 }
 private static void award()throws Exception {
  var bot=generated(CombatRank.POOR,100);take(bot,KILL_QUEST,QuestStatus.START);
  PlayerBotGrowth.practice(bot,npc(KILL_NPC));check(marks(bot).containsKey("quest."+KILL_QUEST) && earned(bot)==100,"A practice kill marks the quest and pays nothing for the quest yet");
  PlayerBotMetadata.validate(ACCOUNT,bot.id,"quest-practice",marks(bot));check(true,"The marked document is valid");
  PlayerBotGrowth.practice(bot,npc(KILL_NPC));PlayerBotGrowth.practice(bot,npc(KILL_NPC));
  PlayerBotGrowth.learnQuest(bot,KILL_QUEST);check(earned(bot)==125 && !marks(bot).containsKey("quest."+KILL_QUEST),"Several kills pay 25 once at the turn-in");
  PlayerBotGrowth.learnQuest(bot,KILL_QUEST);check(earned(bot)==125,"A repeated completion call pays nothing");
  check(marks(bot).keySet().equals(Set.of("account","character")) && !PlayerBotMetadata.pending(ACCOUNT,bot.id).isEmpty(),"The cleared mark and the XP are both waiting for the same checkpoint");

  var first=List.copyOf(PlayerBotMetadata.pending(ACCOUNT,bot.id));
  var sections=new TreeSet<String>();for(var row:first)sections.add(row.section());check(sections.contains("growth") && sections.contains("quest-practice"),"XP and claim travel in one checkpoint write");

  var repeat=generated(CombatRank.POOR,0);take(repeat,KILL_QUEST,QuestStatus.START);PlayerBotGrowth.practice(repeat,npc(KILL_NPC));PlayerBotGrowth.learnQuest(repeat,KILL_QUEST);
  PlayerBotGrowth.learnQuest(repeat,KILL_QUEST);check(earned(repeat)==25,"A repeatable quest pays again only after new practice");
  PlayerBotGrowth.practice(repeat,npc(KILL_NPC));PlayerBotGrowth.learnQuest(repeat,KILL_QUEST);check(earned(repeat)==50,"A real repeat with new practice pays its own 25");

  var shared=generated(CombatRank.POOR,40);take(shared,KILL_QUEST,QuestStatus.REWARD);PlayerBotGrowth.learnQuest(shared,KILL_QUEST);check(earned(shared)==40,"A shared or delivery completion with no practice pays nothing");

  var two=generated(CombatRank.POOR,0);take(two,KILL_QUEST,QuestStatus.START);take(two,DROP_QUEST,QuestStatus.START);PlayerBotGrowth.practice(two,npc(KILL_NPC));PlayerBotGrowth.practice(two,npc(DROP_NPC));
  PlayerBotGrowth.learnQuest(two,DROP_QUEST);check(earned(two)==25 && marks(two).containsKey("quest."+KILL_QUEST),"One quest's turn-in leaves the other quest's mark alone");

  var stale=generated(CombatRank.POOR,0);take(stale,KILL_QUEST,QuestStatus.START);PlayerBotGrowth.practice(stale,npc(KILL_NPC));stale.quests.deleteQuest(KILL_QUEST);
  take(stale,DROP_QUEST,QuestStatus.START);PlayerBotGrowth.practice(stale,npc(DROP_NPC));check(!marks(stale).containsKey("quest."+KILL_QUEST),"An abandoned quest's mark is dropped");
 }
 private static void guards()throws Exception {
  var human=actor(false);take(human,KILL_QUEST,QuestStatus.REWARD);PlayerBotGrowth.learnQuest(human,KILL_QUEST);check(true,"A human turn-in reads no bot state");

  var legacy=actor(true);cache(legacy.id,"growth",Map.of());take(legacy,KILL_QUEST,QuestStatus.START);PlayerBotGrowth.practice(legacy,npc(KILL_NPC));PlayerBotGrowth.learnQuest(legacy,KILL_QUEST);
  check(PlayerBotMetadata.cached(ACCOUNT,legacy.id,"quest-practice")==null && PlayerBotMetadata.cached(ACCOUNT,legacy.id,"growth").isEmpty(),"A legacy bot or owned alt with no traits gets no mark and no document");

  var unloaded=actor(true);take(unloaded,KILL_QUEST,QuestStatus.START);PlayerBotGrowth.practice(unloaded,npc(KILL_NPC));PlayerBotGrowth.learnQuest(unloaded,KILL_QUEST);
  check(PlayerBotMetadata.cached(ACCOUNT,unloaded.id,"growth")==null,"A bot with no loaded growth earns nothing");

  var top=generated(CombatRank.EXCELLENT,20000);take(top,KILL_QUEST,QuestStatus.START);PlayerBotGrowth.practice(top,npc(KILL_NPC));
  check(marks(top).isEmpty(),"Excellent has stopped learning and writes no marks");

  var climbing=generated(CombatRank.POOR,990);take(climbing,KILL_QUEST,QuestStatus.START);PlayerBotGrowth.practice(climbing,npc(KILL_NPC));PlayerBotGrowth.learnQuest(climbing,KILL_QUEST);
  check(earned(climbing)==1015 && PlayerBotMetadata.cached(ACCOUNT,climbing.id,"growth").get("combatRank").equals(CombatRank.ORDINARY.name()),"Quest XP buys rank steps like any other learning");

  var bystander=generated(CombatRank.POOR,0);take(bystander,KILL_QUEST,QuestStatus.START);
  PlayerBotGrowth.learnKill(bystander,npc(KILL_NPC));check(marks(bystander).isEmpty() && earned(bystander)==0,"A bot that did not fight the NPC gets no mark, through the same guard as kill XP");

  for(var bad:List.of(Map.of("account","7","character","12001","quest.0","1"),Map.of("account","7","character","12001","quest.99001","2"),Map.of("account","7","character","12001","quest.x","1")))
   try{PlayerBotMetadata.validate(7,12001,"quest-practice",bad);throw new AssertionError("Accepted "+bad);}catch(java.io.IOException expected){check(true,"Rejected");}
 }
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  com.aionemu.commons.configuration.transformers.PropertyTransformers.register(new com.aionemu.gameserver.services.cron.CronExpressionTransformer());
  Config.load();
  try {
   QuestEngine.getInstance().registerQuestNpc(KILL_NPC).getOnKillEvent().add(KILL_QUEST);
   var drop=new QuestDrop();Field npc=QuestDrop.class.getDeclaredField("npcId");npc.setAccessible(true);npc.set(drop,DROP_NPC);drop.setQuestId(DROP_QUEST);
   QuestService.addQuestDrop(DROP_NPC,drop);
   classification();award();guards();
   System.out.println("OK: "+checks+" quest learning checks; nothing was granted, and no account or database was used");
  }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
  System.exit(0);
 }
 private PlayerBotQuestLearningCheck(){}
}
