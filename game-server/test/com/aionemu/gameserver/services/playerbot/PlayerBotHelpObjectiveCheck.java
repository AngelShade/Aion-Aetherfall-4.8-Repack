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
import com.aionemu.gameserver.model.team.group.*;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotGrowth.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotGroupNeeds.Need;

/** The 50 Learning XP help objective on fixtures, no live accounts or database. */
public final class PlayerBotHelpObjectiveCheck {
 private static final int BOTS=7,TARGET=999101,OTHER=999102,QUEST=99101;
 private static int checks,next=1999999300,teams=1999999900,humans=100;
 private static Unsafe unsafe;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void field(Object object,String name,Object value)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static class Actor extends Player {
  int id;boolean bot;Account account;QuestStateList quests;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public String getName(boolean tag){return "Actor"+id;}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public Account getAccount(){return account;}
  @Override public QuestStateList getQuestStateList(){return quests;}
 }
 private static class Target extends Npc {
  int npcId,object;Target(){super(null,null,null);}
  @Override public int getNpcId(){return npcId;}
  @Override public int getObjectId(){return object;}
 }
 private static Npc npc(int id,int object)throws Exception{var npc=(Target)unsafe.allocateInstance(Target.class);npc.npcId=id;npc.object=object;return npc;}
 private static Actor actor(boolean bot,int account)throws Exception {
  var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.id=next++;actor.bot=bot;actor.quests=new QuestStateList();
  actor.account=(Account)unsafe.allocateInstance(Account.class);Field f=Account.class.getDeclaredField("id");f.setAccessible(true);f.set(actor.account,account);return actor;
 }
 @SuppressWarnings("unchecked") private static void cache(int character,String section,Map<String,String> values)throws Exception {
  Field f=PlayerBotMetadata.class.getDeclaredField("CACHE");f.setAccessible(true);
  ((Map<Object,Object>)f.get(null)).put(new PlayerBotMetadata.Key(character,section),new PlayerBotMetadata.Entry(BOTS,values.isEmpty() ? 0 : 1,values,values));
 }
 private static Actor bot(CombatRank rank)throws Exception {
  var bot=actor(true,BOTS);var map=new HashMap<String,String>();
  map.put("version","1");map.put("account",Integer.toString(BOTS));map.put("character",Integer.toString(bot.id));map.put("origin",Origin.GACHA.name());map.put("rolledAdvancedClass",PlayerClass.TEMPLAR.name());
  map.put("autoEquipRarity",GearRarity.GOOD.name());map.put("initialCombatRank",CombatRank.POOR.name());map.put("combatRank",rank.name());map.put("earnedLearningXp","0");
  cache(bot.id,"growth",map);cache(bot.id,"quest-practice",Map.of());return bot;
 }
 private static PlayerGroup team(Actor... actors)throws Exception {
  var group=(PlayerGroup)unsafe.allocateInstance(PlayerGroup.class);field(group,"members",new java.util.concurrent.ConcurrentHashMap<Integer,PlayerGroupMember>());
  Field id=com.aionemu.gameserver.model.gameobjects.AionObject.class.getDeclaredField("objectId");long offset=unsafe.objectFieldOffset(id);unsafe.putInt(group,offset,teams++);
  for(var a:actors)join(group,a);return group;
 }
 @SuppressWarnings("unchecked") private static void join(PlayerGroup group,Actor actor)throws Exception {
  Field m=com.aionemu.gameserver.model.team.GeneralTeam.class.getDeclaredField("members");m.setAccessible(true);
  ((Map<Integer,PlayerGroupMember>)m.get(group)).put(actor.id,new PlayerGroupMember(actor));field(actor,"playerGroup",group);
 }
 private static long earned(Actor bot)throws Exception{return Long.parseLong(PlayerBotMetadata.cached(BOTS,bot.id,"growth").get("earnedLearningXp"));}
 private static void take(Actor bot,QuestStatus status){bot.quests.addQuest(QUEST,new QuestState(QUEST,status));}
 private static Need quest(){return new Need(QUEST,TARGET,1,0,6,1,65,"quest");}
 private static Need target(){return new Need(0,TARGET,1,0,6,1,65,"target");}
 private static void questHelp()throws Exception {
  var requester=bot(CombatRank.POOR);var ally=bot(CombatRank.POOR);var idle=bot(CombatRank.POOR);var human=actor(false,humans++);take(requester,QuestStatus.START);
  team(requester,ally,idle,human);PlayerBotHelpObjectives.accept(requester,human,quest());
  check(earned(requester)==0,"Accepting pays nothing");
  check(PlayerBotHelpObjectives.title(human).endsWith(": quest"),"The helper's quest is titled from the need");
  check(PlayerBotHelpObjectives.title(requester).isEmpty(),"Only the helper has a title");
  PlayerBotGrowth.practice(requester,npc(TARGET,1));PlayerBotHelpObjectives.kill(requester,npc(TARGET,1));PlayerBotHelpObjectives.kill(requester,npc(TARGET,2));PlayerBotHelpObjectives.kill(ally,npc(TARGET,1));
  PlayerBotHelpObjectives.kill(ally,npc(OTHER,3));check(earned(requester)==0 && earned(ally)==0,"Killing objective mobs pays no bonus before the turn-in");
  requester.quests.getQuestState(QUEST).setStatus(QuestStatus.COMPLETE);PlayerBotGrowth.learnQuest(requester,QUEST);
  check(earned(requester)==50,"The requester gets 50 in place of 25, for several kills");
  check(PlayerBotHelpObjectives.title(human).isEmpty(),"The title ends with the help");
  check(earned(ally)==50,"A party bot that fought gets the full 50");
  check(earned(idle)==0,"A party bot that did not fight gets nothing");
  PlayerBotGrowth.learnQuest(requester,QUEST);check(earned(requester)==50 && earned(ally)==50,"A repeated completion call pays nothing");
  PlayerBotHelpObjectives.accept(requester,human,quest());PlayerBotHelpObjectives.kill(ally,npc(TARGET,4));PlayerBotGrowth.learnQuest(requester,QUEST);
  check(earned(ally)==50,"The objective is closed: the finished quest binds nothing new");

  var replay=bot(CombatRank.POOR);var partner=bot(CombatRank.POOR);var h2=actor(false,humans++);take(replay,QuestStatus.START);team(replay,partner,h2);
  PlayerBotHelpObjectives.accept(replay,h2,quest());PlayerBotHelpObjectives.kill(partner,npc(TARGET,5));PlayerBotGrowth.learnQuest(replay,QUEST);
  PlayerBotHelpObjectives.accept(replay,h2,quest());PlayerBotHelpObjectives.kill(partner,npc(TARGET,6));PlayerBotGrowth.learnQuest(replay,QUEST);
  check(earned(partner)==50,"Reopening the same completion cannot pay a recipient twice");
 }
 private static void targetHelp()throws Exception {
  var requester=bot(CombatRank.POOR);var ally=bot(CombatRank.POOR);var human=actor(false,humans++);team(requester,ally,human);
  PlayerBotHelpObjectives.accept(requester,human,target());
  PlayerBotHelpObjectives.kill(ally,npc(OTHER,1));check(earned(ally)==0,"Another NPC is not the target");
  var dead=npc(TARGET,2);PlayerBotHelpObjectives.kill(requester,dead);PlayerBotHelpObjectives.kill(ally,dead);PlayerBotHelpObjectives.kill(ally,dead);
  check(earned(requester)==50 && earned(ally)==50,"Each eligible member gets 50 once for the one death");
  PlayerBotHelpObjectives.kill(ally,npc(TARGET,3));check(earned(ally)==50,"The next spawn is not the same objective");
 }
 private static void guards()throws Exception {
  var requester=bot(CombatRank.POOR);var human=actor(false,humans++);take(requester,QuestStatus.START);var group=team(requester,human);
  PlayerBotHelpObjectives.accept(requester,human,quest());PlayerBotGrowth.practice(requester,npc(TARGET,1));PlayerBotHelpObjectives.kill(requester,npc(TARGET,1));
  team(human);PlayerBotGrowth.learnQuest(requester,QUEST);check(earned(requester)==25,"If the helper left, the bot gets its ordinary 25");

  for(var need:List.of(new Need(0,-1,1,0,6,1,65,"offer"),new Need(0,0,1,0,6,1,65,"level"),new Need(0,0,1,4,6,1,65,"instance"))) {
   var bot=bot(CombatRank.POOR);var h=actor(false,humans++);team(bot,h);PlayerBotHelpObjectives.accept(bot,h,need);PlayerBotHelpObjectives.kill(bot,npc(TARGET,1));
   check(earned(bot)==0,"No objective is bound for "+need.title());
  }

  var first=bot(CombatRank.POOR);var second=bot(CombatRank.POOR);var solo=actor(false,humans);var same=actor(false,humans++);team(first,solo);team(second,same);
  PlayerBotHelpObjectives.accept(first,solo,target());PlayerBotHelpObjectives.accept(second,same,target());PlayerBotHelpObjectives.kill(second,npc(TARGET,9));
  check(earned(second)==0,"One account helps with one request at a time");

  var top=bot(CombatRank.EXCELLENT);var h3=actor(false,humans++);team(top,h3);PlayerBotHelpObjectives.accept(top,h3,target());PlayerBotHelpObjectives.kill(top,npc(TARGET,1));
  check(PlayerBotMetadata.cached(BOTS,top.id,"quest-practice").isEmpty(),"Excellent has stopped learning and writes no claim");

  var legacy=actor(true,BOTS);cache(legacy.id,"growth",Map.of());var h4=actor(false,humans++);team(legacy,h4);PlayerBotHelpObjectives.accept(legacy,h4,target());PlayerBotHelpObjectives.kill(legacy,npc(TARGET,1));
  check(PlayerBotMetadata.cached(BOTS,legacy.id,"quest-practice")==null,"A legacy bot or owned alt gets nothing");

  var many=bot(CombatRank.POOR);for(int i=0;i<40;i++)PlayerBotGrowth.learnHelp(many,"n1:2:"+i);
  check(earned(many)==2000 && PlayerBotMetadata.cached(BOTS,many.id,"quest-practice").get("help").split(",").length==32,"The claim list stays bounded");
  PlayerBotMetadata.validate(BOTS,many.id,"quest-practice",PlayerBotMetadata.cached(BOTS,many.id,"quest-practice"));check(true,"The claim document is valid");
  try{PlayerBotMetadata.validate(7,12001,"quest-practice",Map.of("account","7","character","12001","help","x1:2:3"));throw new AssertionError("Accepted a bad claim");}catch(java.io.IOException expected){check(true,"Rejected");}
 }
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  com.aionemu.commons.configuration.transformers.PropertyTransformers.register(new com.aionemu.gameserver.services.cron.CronExpressionTransformer());
  Config.load();
  try {
   QuestEngine.getInstance().registerQuestNpc(TARGET).getOnKillEvent().add(QUEST);
   questHelp();targetHelp();guards();
   System.out.println("OK: "+checks+" help objective checks; nothing was granted, and no account or database was used");
  }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
  System.exit(0);
 }
 private PlayerBotHelpObjectiveCheck(){}
}
