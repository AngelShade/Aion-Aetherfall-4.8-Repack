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
import com.aionemu.gameserver.world.WorldMapInstance;

/** Native help-event guards and durable row projections on fixtures. SQL and native UI are separate checks. */
public final class PlayerBotHelpObjectiveCheck {
 private static final int BOTS=7,TARGET=999101,OTHER=999102,QUEST=99101;
 private static int checks,next=1999999300,teams=1999999900,humans=100;
 private static Unsafe unsafe;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void field(Object object,String name,Object value)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static class Actor extends Player {
  int id,enemyChecks;boolean bot,online;Account account;QuestStateList quests;float x;WorldMapInstance world;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public byte getLevel(){return 65;}
  @Override public String getName(boolean tag){return "Actor"+id;}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public Account getAccount(){return account;}
  @Override public QuestStateList getQuestStateList(){return quests;}
  @Override public boolean isOnline(){return online;}
  @Override public WorldMapInstance getWorldMapInstance(){return world;}
  @Override public int getWorldId(){return 1;}
  @Override public int getInstanceId(){return world==null ? 1 : world.getInstanceId();}
  @Override public float getX(){return x;}
  @Override public float getY(){return 0;}
  @Override public float getZ(){return 0;}
  // Npc.isEnemyFrom(Player) delegates here. Supply the fixture's relation without loading tribe/template data.
  @Override public boolean isEnemyFrom(Npc npc){enemyChecks++;return !((Target)npc).friendly;}
 }
 private static class Target extends Npc {
  int npcId,object;boolean friendly;WorldMapInstance world;Target(){super(null,null,null);}
  @Override public int getNpcId(){return npcId;}
  @Override public int getObjectId(){return object;}
  @Override public WorldMapInstance getWorldMapInstance(){return world;}
  @Override public int getWorldId(){return 1;}
  @Override public int getInstanceId(){return world==null ? 1 : world.getInstanceId();}
  @Override public float getX(){return 0;}
  @Override public float getY(){return 0;}
  @Override public float getZ(){return 0;}
 }
 private static Npc npc(int id,int object)throws Exception{var npc=(Target)unsafe.allocateInstance(Target.class);npc.npcId=id;npc.object=object;return npc;}
 private static WorldMapInstance world(int id)throws Exception{var world=(WorldMapInstance)unsafe.allocateInstance(com.aionemu.gameserver.world.WorldMap2DInstance.class);field(world,"instanceId",id);return world;}
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
 /** Fixture starts with the SQL row that real acceptance stores. Acceptance constraints
  * and SQL atomicity belong to the isolated repository check, not this in-memory fixture. */
 @SuppressWarnings("unchecked") private static PlayerBotHelpObjectives.Binding bind(Actor bot,Actor human,Need need)throws Exception {
  var c=new PlayerBotHelpObjectives.Contract();c.account=human.account.getId();c.helper=human.id;c.bot=bot.id;c.botName=bot.getName();c.need=need.title();c.quest=need.quest();c.npc=need.npc();c.minLevel=1;c.maxLevel=65;c.map=1;
  c.objectives=c.quest==0 ? List.of(new PlayerBotHelpObjectives.Counter("target","Target","TARGET",0,0,0,1)) : List.of(new PlayerBotHelpObjectives.Counter("kill","Native kill","KILL",1,0,0,3),new PlayerBotHelpObjectives.Counter("stage","Native stage","STAGE",0,0,0,-1),new PlayerBotHelpObjectives.Counter("turnin","Bot turn-in","TURNIN",0,0,0,1));
  var row=new com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row("h"+c.account+":"+c.bot+":"+c.quest+":"+c.npc,c.account,c.helper,c.bot,0,"ACTIVE",com.aionemu.commons.utils.Json.write(c));
  Method remember=PlayerBotHelpObjectives.class.getDeclaredMethod("remember",com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row.class);remember.setAccessible(true);
  var b=(PlayerBotHelpObjectives.Binding)remember.invoke(null,row);b.requester=bot;b.helper=human;return b;
 }
 private static void commit(PlayerBotHelpObjectives.Pending p) {
  var old=p.before();var c=p.contract();
  PlayerBotHelpObjectives.committed(p,new com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row(old.key(),old.account(),old.helper(),old.bot(),old.revision()+1,c.status,com.aionemu.commons.utils.Json.write(c)));
 }
 private static void participation()throws Exception {
  Method bound=PlayerBotHelpObjectives.class.getDeclaredMethod("bound",Player.class);bound.setAccessible(true);
  String[] conditions={"no contribution","offline","out of range","another instance","real contributor","friendly target"};
  for(int condition=0;condition<conditions.length;condition++) {
   String label=conditions[condition];
   var requester=bot(CombatRank.POOR);var human=actor(false,humans++);take(requester,QuestStatus.START);team(requester,human);
   bind(requester,human,quest());var npc=(Target)npc(TARGET,50+condition);npc.friendly=condition==5;
   human.world=requester.world=npc.world=world(1);human.online=true;
   var binding=(PlayerBotHelpObjectives.Binding)bound.invoke(null,requester);
   check(binding!=null && !binding.helperParticipated,"Accepting alone gives no helper credit: "+label);
   if(condition!=0)PlayerBotRunRewards.contribution(npc,human);
   check(human.enemyChecks==(condition==0 ? 0 : 1),"Contribution uses Npc's player-side enemy test: "+label);
   check(PlayerBotRunRewards.participated(npc,human)==(condition!=0 && condition!=5),"Only hostile contribution records evidence: "+label);
   // Record genuine evidence first, then invalidate just the helper's presence at the objective kill.
   if(condition==1)human.online=false;
   if(condition==2)human.x=101;
   if(condition==3)human.world=world(2);
   PlayerBotHelpObjectives.kill(requester,npc);
   check(binding.helperParticipated==(condition==4),"Only an online nearby contributor in the same instance earns helper credit: "+label);
   team(human);PlayerBotHelpObjectives.title(human);PlayerBotRunRewards.reset(npc);
   check(!PlayerBotRunRewards.participated(npc,human),"Reset clears contribution evidence: "+label);
  }
 }
 private static void durable()throws Exception {
  var bot=bot(CombatRank.POOR);var human=actor(false,humans++);team(bot,human);var b=bind(bot,human,target());
  var original=b.row;PlayerBotHelpObjectives.kill(bot,npc(TARGET,701));
  check(b.defeated==701 && b.committed.defeated==0,"A real eligible-kill callback queues work without publishing it");
  check(PlayerBotHelpObjectives.snapshot(human).get("status").equals("ACTIVE"),"An uncommitted death exposes no reward");
  var pending=PlayerBotHelpObjectives.pending(bot);
  check(pending.contract().objectives.getFirst().current()==1,"Checkpoint captures the bound target defeat");
  check(pending.contract().status.equals("RESOLVED"),"A helper without native participation earns no entitlement");
  check(b.row==original,"Preparing a checkpoint does not publish a counter");
  commit(pending);check(b.committed.defeated==701,"Only the post-commit callback publishes target progress");
  team(human);check(!PlayerBotHelpObjectives.title(human).isEmpty(),"Leaving the party keeps the committed objective");
  var saved=b.row;
  for(String name:List.of("BY_ACCOUNT","BY_REQUESTER")){Field f=PlayerBotHelpObjectives.class.getDeclaredField(name);f.setAccessible(true);((Map<?,?>)f.get(null)).clear();}
  Method remember=PlayerBotHelpObjectives.class.getDeclaredMethod("remember",com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row.class);remember.setAccessible(true);
  var restored=(PlayerBotHelpObjectives.Binding)remember.invoke(null,saved);
  check(restored.committed.defeated==701 && restored.request().equals(saved.key()),"The real row decoder restores the same request identity and target");
  check(PlayerBotHelpObjectives.progress(human).contains("1/1"),"Relog journal objectives use persisted counters, with no party requirement");
  check(restored.committed.need.equals("target"),"Reopening never substitutes a new need");
 }
 private static void nativeStages()throws Exception {
  var bot=bot(CombatRank.POOR);var human=actor(false,humans++);take(bot,QuestStatus.START);team(bot,human);var b=bind(bot,human,quest());var q=bot.quests.getQuestState(QUEST);
  q.setQuestVarById(0,2);q.setQuestVarById(1,2);
  var p=PlayerBotHelpObjectives.pending(bot);
  check(p.contract().objectives.get(0).current()==2 && p.contract().objectives.get(1).current()==2,"Counters and stage come from native variables");
  check(b.committed.stage==0,"A changed native stage stays unpublished until commit");commit(p);
  check(b.committed.stage==2,"Post-commit state publishes the native stage");
  q.setStatus(QuestStatus.REWARD);p=PlayerBotHelpObjectives.pending(bot);
  check(p.contract().status.equals("ACTIVE") && p.contract().objectives.get(2).current()==0,"Ready objectives do not replace the bot's own turn-in");
  check(!PlayerBotHelpObjectives.completed(bot,QUEST),"A premature completion callback pays no Learning XP");
  b.participants.add(bot.id);b.helperParticipated=true;q.setStatus(QuestStatus.COMPLETE);
  check(PlayerBotHelpObjectives.completed(bot,QUEST),"The native COMPLETE transition pays the participating requester");
  check(!PlayerBotHelpObjectives.completed(bot,QUEST) && earned(bot)==50,"Repeated completion cannot repeat Learning XP");
  p=PlayerBotHelpObjectives.pending(bot);check(p.contract().status.equals("COMPLETE"),"The native turn-in plus real participation produces a pending entitlement");
  check(b.row.status().equals("ACTIVE"),"Pending completion cannot be collected");commit(p);
  check(b.row.status().equals("COMPLETE") && b.committed.objectives.get(2).current()==1,"Checkpoint confirmation publishes turn-in and reward readiness together");
  var changed=bot(CombatRank.POOR);var h=actor(false,humans++);take(changed,QuestStatus.START);team(changed,h);var revised=bind(changed,h,quest());changed.quests.getQuestState(QUEST).setCompleteCount(2);
  p=PlayerBotHelpObjectives.pending(changed);check(p.contract().status.equals("REVISED") && p.contract().need.equals(revised.committed.need),"A different native quest round is revised, never silently rebound");
 }
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  com.aionemu.commons.configuration.transformers.PropertyTransformers.register(new com.aionemu.gameserver.services.cron.CronExpressionTransformer());
  Config.load();
  try {
   QuestEngine.getInstance().registerQuestNpc(TARGET).getOnKillEvent().add(QUEST);
   participation();durable();nativeStages();
   var social=PlayerBotFindGroup.class.getDeclaredField("SOCIAL");social.setAccessible(true);
   check(!((java.util.concurrent.ScheduledThreadPoolExecutor)social.get(null)).getExecuteExistingDelayedTasksAfterShutdownPolicy(),"Delayed Find Group follow-ups cannot hold server shutdown");
   check(PlayerBotGroupNeeds.display("eyvindr sailor").equals("Eyvindr Sailor"),"Template keys read as names");
   check(PlayerBotGroupNeeds.level(40,25,30)==false && PlayerBotGroupNeeds.level(28,25,30) && PlayerBotGroupNeeds.level(40,25,0),"Help eligibility uses the applicant's level rule");
   System.out.println("OK: "+checks+" help objective checks; fixtures only; SQL acceptance, actual checkpoint transactions and native UI not exercised");
  }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
  System.exit(0);
 }
 private PlayerBotHelpObjectiveCheck(){}
}
