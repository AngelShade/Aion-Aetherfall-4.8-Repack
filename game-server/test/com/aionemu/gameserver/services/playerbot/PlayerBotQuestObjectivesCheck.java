package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import java.io.StringReader;
import java.util.*;
import java.util.function.Consumer;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.world.knownlist.KnownList;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestRoutes.*;

/** Production planner/executor gates on world-free actors and real quest metadata.
 * Constructors are bypassed: no ID allocation, registration, DB, native casts or human writes. */
public final class PlayerBotQuestObjectivesCheck {
 static Unsafe unsafe;static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void field(Object o,String name,Object v)throws Exception {
  for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{var f=t.getDeclaredField(name);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 static class Seen extends KnownList {
  List<Npc> actors=new ArrayList<>();Set<Integer> hidden=new HashSet<>();Seen(){super(null);}
  @Override public void forEachNpc(Consumer<Npc> consumer){actors.forEach(consumer);}
  @Override public boolean sees(VisibleObject o){return actors.contains(o) && !hidden.contains(o.getObjectId());}
 }
 static class Cube extends PlayerStorage {Cube(){super(null,StorageType.CUBE);}@Override public long getItemCountByItemId(int id){return 0;}@Override public int getFreeSlots(){return 0;}}
 static class Actor extends Player {
  int id,world,instance;float x;QuestStateList quests;Seen seen;Account account;PlayerGroup group;Cube cube;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}@Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}
  @Override public float getX(){return x;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public boolean isInInstance(){return true;}@Override public boolean isSpawned(){return true;}@Override public boolean isDead(){return false;}
  @Override public boolean isEnemy(Creature n){return false;}@Override public boolean isPlayerBot(){return true;}
  @Override public QuestStateList getQuestStateList(){return quests;}@Override public KnownList getKnownList(){return seen;}
  @Override public Account getAccount(){return account;}@Override public PlayerGroup getPlayerGroup(){return group;}@Override public Storage getInventory(){return cube;}
 }
 static class Contact extends Npc {
  int id,template,world,instance;float x;boolean spawned=true;Contact(){super(null,null,null);}
  @Override public int getObjectId(){return id;}@Override public int getNpcId(){return template;}
  @Override public int getWorldId(){return world;}@Override public int getInstanceId(){return instance;}
  @Override public float getX(){return x;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public boolean isDead(){return false;}@Override public boolean isSpawned(){return spawned;}
  @Override public NpcTemplate getObjectTemplate(){return new NpcTemplate(){@Override public int getTemplateId(){return template;}};}
 }
 static Actor actor(int id,PlayerGroup group)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.world=210030000;a.instance=1;a.quests=new QuestStateList();a.seen=new Seen();a.cube=new Cube();
  a.account=(Account)unsafe.allocateInstance(Account.class);field(a.account,"id",1999999081);a.group=group;return a;
 }
 static Contact npc(int id,int template,float x)throws Exception {
  var n=(Contact)unsafe.allocateInstance(Contact.class);n.id=id;n.template=template;n.x=x;n.world=210030000;n.instance=1;n.spawned=true;return n;
 }
 static PlayerBotSession session(Actor owner,Actor bot)throws Exception {
  var s=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);field(s,"owner",owner);field(s,"bot",bot);return s;
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  var uf=Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(Unsafe)uf.get(null);
  var oldQ=DataManager.QUEST_DATA;var oldX=DataManager.XML_QUESTS;var oldN=DataManager.NPC_DATA;var oldSpawns=DataManager.SPAWNS_DATA;
  var group=(PlayerGroup)unsafe.allocateInstance(PlayerGroup.class);var owner=actor(1999999081,group);var leader=actor(1999999082,group);var follower=actor(1999999083,group);
  var a=session(owner,leader);var b=session(owner,follower);long now=System.currentTimeMillis();var third=actor(1999999084,group);var c=session(owner,third);
  var engine=com.aionemu.gameserver.questEngine.QuestEngine.getInstance();var registryField=engine.getClass().getDeclaredField("questNpcs");registryField.setAccessible(true);
  var registry=(Map<Integer,com.aionemu.gameserver.model.templates.quest.QuestNpc>)registryField.get(engine);check(!registry.containsKey(203003),"Reward fixture cannot overwrite existing native NPC registration");
  try {
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests><quest id='900101' name='Three conversations' category='QUEST'><rewards exp='10'/></quest></quests>"));
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts><report_to_many id='900101' start_npc_ids='203000'><npc_infos npc_ids='203001'/><npc_infos npc_ids='203002'/><npc_infos npc_ids='203003'/></report_to_many></quest_scripts>"));
   DataManager.NPC_DATA=new NpcData(){@Override public NpcTemplate getNpcTemplate(int id){return new NpcTemplate(){@Override public int getTemplateId(){return id;}@Override public String getName(){return "Contact";}};}};DataManager.SPAWNS_DATA=null;
   var q1=new QuestState(900101,QuestStatus.START);var q2=new QuestState(900101,QuestStatus.START);leader.quests.addQuest(900101,q1);follower.quests.addQuest(900101,q2);
   var near=npc(1999999091,203001,2);var far=npc(1999999092,203001,10);var next=npc(1999999093,203002,5);
   owner.seen.actors.addAll(List.of(near,far,next));leader.seen.actors.addAll(owner.seen.actors);follower.seen.actors.addAll(owner.seen.actors);leader.x=10;
   var first=PlayerBotQuestRoutes.choose(a,now);check(first.kind()==Kind.CONVERSATION && first.point().x()==10,"Leader selects actual nearest actor, not just NPC template");
   var df=PlayerBotQuestObjectives.class.getDeclaredField("DECISIONS");df.setAccessible(true);var decisions=(Map<Integer,PlayerBotQuestObjectives.Decision>)df.get(null);
   var hint=decisions.get(leader.id);hint.goal=new Goal(900101,203001,Kind.CONVERSATION,first.point(),false,false,0);hint.actor=0;
   first=PlayerBotQuestRoutes.choose(a,now+50);check(first.visible() && PlayerBotQuestObjectives.matches(leader,Kind.CONVERSATION,900101,far),"Static hint resolves actual visible actor before executing conversation");
   var copied=PlayerBotQuestRoutes.choose(b,now);check(copied.grouped() && copied.point().equals(first.point()),"Follower copies exact active peer destination instead of nearer same-template actor");
   third.quests.addQuest(900101,new QuestState(900101,QuestStatus.START));PlayerBotQuestObjectives.prepare(a,false,false);
   var unchained=PlayerBotQuestRoutes.choose(c,now+100);check(!unchained.grouped() && unchained.point().x()==2,"Copied destinations are not copied again when original peer is paused");
   PlayerBotQuestRoutes.choose(a,now+200);PlayerBotQuestRoutes.close(third);
   check(PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,far),"Conversation executor admits committed actor");
   check(!PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,near),"Conversation executor rejects other instance of same NPC template");
   check(!PlayerBotQuestObjectives.matches(follower,Kind.OBJECT,900101,far),"Object executor cannot replace conversation destination");
   check(!PlayerBotQuestObjectives.job(follower,new PlayerBotQuests.Job(near,900102,false)),"Acceptance cannot preempt active objective");
   check(!PlayerBotQuestObjectives.job(follower,new PlayerBotQuests.Job(far,900101,true)),"Turn-in cannot replace intermediate conversation");
   check(!PlayerBotQuestObjectives.pull(follower,far),"New pull cannot preempt conversation");
   far.x=12;var moved=PlayerBotQuestRoutes.choose(b,now+500);check(moved.point().x()==12 && moved.grouped(),"Cached destination tracks same moving actor without selecting another spawn");
   owner.seen.hidden.add(far.id);check(!PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,far),"Hidden actor invalidates executor before interaction");
   var visible=PlayerBotQuestRoutes.choose(b,now+1000);check(visible.point().x()==2 && !visible.grouped(),"Invalid hidden peer is discarded in favor of actual visible NPC");owner.seen.hidden.clear();
   q2.setQuestVarById(1,1);check(!PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,near),"Secondary quest variables invalidate committed action even when main stage matches");
   var different=PlayerBotQuestRoutes.choose(b,now+1500);check(different.grouped(),"Different variables may share a destination when native follower objective remains active");
   check(PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,far),"Copied destination records follower's own variables for native action validation");q2.setQuestVarById(1,0);
   // Real native ReportToMany handler advances one step; no invented quest completion.
   var infos=(List<com.aionemu.gameserver.questEngine.handlers.models.NpcInfos>)(List<?>)PlayerBotQuestConversations.list(DataManager.XML_QUESTS.getQuest(900101),"npcInfos");
   var report=new PlayerBotConversationCheck.Report(infos);var env=new QuestEnv(near,follower,900101);env.setDialogActionId(com.aionemu.gameserver.model.DialogAction.SETPRO1);
   check(report.onDialogEvent(env) && q2.getQuestVarById(0)==1 && q2.getStatus()==QuestStatus.START,"Native handler advances only intermediate objective");
   check(!PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,near),"Native progression invalidates old objective immediately");
   var advanced=PlayerBotQuestRoutes.choose(b,now+2000);check(advanced.npc()==203002 && !advanced.grouped(),"Next native conversation selected; old-stage peer not copied");
   PlayerBotQuestObjectives.prepare(b,false,false);check(!PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,next),"Combat/rest/travel pause disables automatic action");
   var resumed=PlayerBotQuestRoutes.choose(b,now+62000);check(resumed.npc()==203002,"Long pause preserves valid destination without stuck-route exclusion");
   PlayerBotQuestObjectives.attempted(follower,false);PlayerBotQuestObjectives.attempted(follower,false);check(PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,next),"Two failed native interactions retain retryable objective");
   PlayerBotQuestObjectives.attempted(follower,false);next.x=6;check(PlayerBotQuestRoutes.choose(b,System.currentTimeMillis())==null,"Third failure backs off same actor even after it moves");
   q2.setQuestVarById(0,0);check(PlayerBotQuestRoutes.choose(b,now+64000)!=null,"Different quest stage does not inherit previous objective exclusion");
   follower.instance=2;check(!PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,near),"Bot instance change invalidates cached action");follower.instance=1;
   owner.instance=2;check(PlayerBotQuestRoutes.choose(b,now+65000)==null,"Owner instance transition discards old-map destination");owner.instance=1;
   var reward=npc(1999999094,203003,6);owner.seen.actors.add(reward);follower.seen.actors.add(reward);engine.registerQuestNpc(203003).addOnTalkEvent(900101);q2.setQuestVarById(0,2);
   var turnIn=PlayerBotQuestRoutes.choose(b,now+66000);check(turnIn.kind()==Kind.REWARD && turnIn.npc()==203003,"Ready native quest selects its actual reward NPC");
   var job=new PlayerBotQuests().choose(owner,follower);check(job!=null && job.turnIn() && job.quest()==900101 && job.npc()==reward,"Production NPC job chooser consumes exact committed turn-in destination");
   check(!PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,next),"Intermediate executor cannot consume ready reward destination");
   q2.setStatus(QuestStatus.COMPLETE);check(!PlayerBotQuestObjectives.job(follower,job),"Native completion invalidates pending reward action before replay");
   check(PlayerBotQuestRoutes.choose(b,now+67000)==null,"Completed quest produces no remaining destination");
   check(PlayerBotQuestObjectives.job(follower,new PlayerBotQuests.Job(near,900102,false)),"Acceptance fallback resumes only after active objective finishes");
   PlayerBotQuestObjectives.prepare(b,false,true);check(PlayerBotQuestObjectives.matches(follower,Kind.CONVERSATION,900101,near),"Explicit mission retains its original executor contract");
   check(PlayerBotQuestRoutes.leash(Kind.HUNT)==25,"Planner hunting leash matches actual native initiation radius");
   System.out.println("OK: "+checks+" production quest arbitration, exact peer/actor identity, moving visibility/stage/map invalidation, native intermediate transition, pause and retry checks; no DB/world/ID changes");
  }finally{registry.remove(203003);PlayerBotQuestRoutes.close(leader);PlayerBotQuestRoutes.close(follower);PlayerBotQuestRoutes.close(third);PlayerBotPartyBehavior.close(a);PlayerBotPartyBehavior.close(b);PlayerBotPartyBehavior.close(c);DataManager.QUEST_DATA=oldQ;DataManager.XML_QUESTS=oldX;DataManager.NPC_DATA=oldN;DataManager.SPAWNS_DATA=oldSpawns;}
 }
}
