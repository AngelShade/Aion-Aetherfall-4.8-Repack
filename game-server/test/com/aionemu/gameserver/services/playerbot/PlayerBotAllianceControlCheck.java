package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.nio.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.alliance.*;
import com.aionemu.gameserver.model.team.alliance.events.*;
import com.aionemu.gameserver.model.team.common.events.TeamCommand;
import com.aionemu.gameserver.model.team.common.legacy.*;
import com.aionemu.gameserver.network.aion.*;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ALLIANCE_MEMBER_INFO;
import com.aionemu.gameserver.world.WorldPosition;

/** Actual native groups/events, HTTP scope policy and wire bytes on inert actors;
 * no World initialization, native ID allocation, database or client connection. */
public final class PlayerBotAllianceControlCheck {
 static Unsafe unsafe;static int checks;
 static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static void reject(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){check(true,why);}}
 static class Life extends PlayerLifeStats {
  Life(){super(null);}
  @Override public int getHpPercentage(){return 87;}@Override public int getMpPercentage(){return 64;}
  @Override public int getMaxHp(){return 1000;}@Override public int getCurrentHp(){return 870;}
  @Override public int getMaxMp(){return 500;}@Override public int getCurrentMp(){return 320;}
  @Override public int getMaxFp(){return 200;}@Override public int getCurrentFp(){return 100;}
 }
 static class Actor extends PlayerBotRaidCheck.Actor {
  PlayerCommonData data;Life life;
  Actor(){super();}
  @Override public PlayerCommonData getCommonData(){return data;}
  @Override public String getName(boolean tags){return data.getName();}
  @Override public PlayerClass getPlayerClass(){return PlayerClass.CLERIC;}
  @Override public byte getLevel(){return 65;}
  @Override public PlayerLifeStats getLifeStats(){return life;}
  @Override public com.aionemu.gameserver.controllers.effect.PlayerEffectController getEffectController(){return new com.aionemu.gameserver.controllers.effect.PlayerEffectController(this);}
  @Override public int getFlyState(){return 0;}
 }
 static class Alliance extends PlayerBotRaidCheck.Alliance {
  int wirePackets;
  Alliance(){super();}
  @Override public int getObjectId(){return 700;}
  // Packet delivery alone is inert. Events and native membership run unchanged.
  @Override public void forEach(Consumer<Player> sink){}
  @Override public void sendPackets(AionServerPacket... packets){wirePackets+=packets.length;}
 }
 static Actor actor(int id,boolean bot)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.owner=bot?999:0;a.online=!bot;a.spawned=true;
  a.data=new PlayerCommonData(id);a.data.setName("Synthetic"+id);a.data.setPlayerClass(PlayerClass.CLERIC);a.data.setGender(com.aionemu.gameserver.model.Gender.MALE);
  a.life=(Life)unsafe.allocateInstance(Life.class);PlayerBotTravelFormationCheck.field(a,"position",new WorldPosition(210010000,1,2,3,(byte)0));
  return a;
 }
 static Alliance alliance(List<Actor> actors)throws Exception {
  var a=(Alliance)unsafe.allocateInstance(Alliance.class);a.type=TeamType.ALLIANCE;
  PlayerBotTravelFormationCheck.field(a,"members",new LinkedHashMap<Integer,PlayerAllianceMember>());
  PlayerBotTravelFormationCheck.field(a,"teamLock",new ReentrantLock());PlayerBotTravelFormationCheck.field(a,"viceCaptainIds",new ArrayList<Integer>());
  PlayerBotTravelFormationCheck.field(a,"readyCheck",new AllianceReadyCheck());
  PlayerBotTravelFormationCheck.field(a,"lootGroupRules",new LootGroupRules());
  var groups=new LinkedHashMap<Integer,PlayerAllianceGroup>();for(int i=1000;i<=1003;i++)groups.put(i,new PlayerAllianceGroup(a,i));PlayerBotTravelFormationCheck.field(a,"groups",groups);
  for(var p:actors){p.group=null;p.alliance=a;a.addMember(new PlayerAllianceMember(p));}
  PlayerBotTravelFormationCheck.field(a,"leader",a.getMember(actors.getFirst().id));return a;
 }
 static List<Actor> actors(int count)throws Exception {var list=new ArrayList<Actor>();for(int i=0;i<count;i++)list.add(actor(100+i,i!=0));return list;}
 static int group(Actor p){return p.getPlayerAllianceGroup().getTeamId();}
 static void membership()throws Exception {
  var players=actors(24);var a=alliance(players);
  check(a.size()==24 && a.groupSize()==4,"Native alliance contains four actual groups and 24 retained identities");
  for(var g:a.getGroups())check(g.size()==6,"Native subgroup capacity is six");
  var original=a.getMember(players.get(1).id);
  reject(()->a.addMember(new PlayerAllianceMember(players.get(1))),"Duplicate admission refuses before native member overwrite");
  check(a.getMember(players.get(1).id)==original,"Original native member retained on rejected duplicate");
  var extra=actor(999,true);reject(()->a.addMember(new PlayerAllianceMember(extra)),"25th member refuses before native publication");
  check(a.size()==24 && extra.getPlayerAllianceGroup()==null,"Refused admission owns no subgroup slot");
  var first=players.get(0);int originalGroup=group(first);
  a.onEvent(new ChangeMemberGroupEvent(a,first.id,0,9999));check(group(first)==originalGroup && a.size()==24,"Invalid destination does not remove native membership");
  a.onEvent(new ChangeMemberGroupEvent(a,first.id,0,1001));check(group(first)==originalGroup && a.getAllianceGroup(1001).size()==6,"Full destination does not overfill or orphan member");
  a.onEvent(new ChangeMemberGroupEvent(a,first.id,first.id,0));check(group(first)==originalGroup,"Self swap is inert");
  a.onEvent(new ChangeMemberGroupEvent(a,first.id,players.get(1).id,0));check(group(first)==originalGroup,"Same-group swap retains both members");
  var second=players.get(6);a.onEvent(new ChangeMemberGroupEvent(a,first.id,second.id,0));
  check(group(first)==1001 && group(second)==1000 && a.size()==24,"Native swap exchanges subgroup positions and preserves alliance");
  for(var g:a.getGroups())check(g.size()==6,"Full group swap retains six members each");
  a.removeMember(players.get(7).id);a.onEvent(new ChangeMemberGroupEvent(a,second.id,0,1001));
  check(group(second)==1001 && a.getAllianceGroup(1001).size()==6 && a.getAllianceGroup(1000).size()==5,"Valid native move updates both subgroup counts");
  check(a.wirePackets==3,"Only successful group changes publish native roster packets");
 }
 static void ready()throws Exception {
  var players=actors(4);var a=alliance(players);var owner=players.getFirst();var peer=players.get(1);
  a.onEvent(new CheckAllianceReadyEvent(a,peer,TeamCommand.ALLIANCE_CHECKREADY_START));check(a.getReadyCheck().waiting()==0,"Ordinary member cannot start readiness");
  a.onEvent(new CheckAllianceReadyEvent(a,owner,TeamCommand.ALLIANCE_CHECKREADY_START));check(a.getAllianceReadyStatus()==3,"Actual native start includes connected human and all spawned headless bots");
  a.onEvent(new CheckAllianceReadyEvent(a,peer,TeamCommand.ALLIANCE_CHECKREADY_READY));check(a.getAllianceReadyStatus()==2,"Native bot reply consumes exactly one waiter");
  a.onEvent(new CheckAllianceReadyEvent(a,peer,TeamCommand.ALLIANCE_CHECKREADY_NOTREADY));check(a.getAllianceReadyStatus()==2,"Duplicate contradictory response cannot decrement count");
  var outsider=actor(333,true);a.onEvent(new CheckAllianceReadyEvent(a,outsider,TeamCommand.ALLIANCE_CHECKREADY_READY));check(a.getAllianceReadyStatus()==2,"Outsider cannot consume readiness");
  a.onEvent(new CheckAllianceReadyEvent(a,players.get(2),TeamCommand.ALLIANCE_CHECKREADY_NOTREADY));check(a.getAllianceReadyStatus()==1,"Not ready response is recorded separately");
  a.removeMember(players.get(3).id);check(a.getAllianceReadyStatus()==0 && !(Boolean)a.getReadyCheck().snapshot().get("active"),"Departure resolves a pending waiter without going negative");
  check(a.getReadyCheck().snapshot().get("states").toString().contains("NOT_READY") && a.getReadyCheck().snapshot().get("states").toString().contains("LEFT"),"Ready report retains exact refusal/departure outcomes");
  a.onEvent(new CheckAllianceReadyEvent(a,peer,TeamCommand.ALLIANCE_CHECKREADY_CANCEL));check(!((List<?>)a.getReadyCheck().snapshot().get("states")).isEmpty(),"Ordinary member cannot erase captain report");
  a.onEvent(new CheckAllianceReadyEvent(a,owner,TeamCommand.ALLIANCE_CHECKREADY_CANCEL));check(((List<?>)a.getReadyCheck().snapshot().get("states")).isEmpty(),"Captain cancel clears transient state");
  peer.spawned=false;a.onEvent(new CheckAllianceReadyEvent(a,owner,TeamCommand.ALLIANCE_CHECKREADY_START));check(a.getAllianceReadyStatus()==1,"Save-held despawned bot is excluded from new ready check");
 }
 static void management()throws Exception {
  var players=actors(7);var a=alliance(players);var owner=players.getFirst();var peer=players.get(1);
  check(new ChangeAllianceLeaderEvent(a,peer).checkCondition(),"Headless spawned bot can receive native leadership");
  peer.spawned=false;check(!new ChangeAllianceLeaderEvent(a,peer).checkCondition(),"Despawned bot cannot receive native leadership");peer.spawned=true;
  owner.online=false;check(!new ChangeAllianceLeaderEvent(a,owner).checkCondition(),"Disconnected human leadership predicate remains unchanged");owner.online=true;
  for(int i=1;i<=4;i++)a.onEvent(new AssignViceCaptainEvent(a,players.get(i),AssignViceCaptainEvent.AssignType.PROMOTE));
  check(a.getViceCaptainIds().size()==4,"Native bot vice captains accepted at native limit");
  a.onEvent(new AssignViceCaptainEvent(a,peer,AssignViceCaptainEvent.AssignType.PROMOTE));check(a.getViceCaptainIds().size()==4,"Repeated promotion cannot consume another vice slot");
  a.onEvent(new AssignViceCaptainEvent(a,peer,AssignViceCaptainEvent.AssignType.DEMOTE));check(a.getViceCaptainIds().size()==3 && !a.isViceCaptain(peer),"Native demotion releases exact vice role");
  reject(()->PlayerBotAllianceControl.action(peer,Map.of("team","700","operation","move","member","100","group","1001")),"Ordinary member cannot move native members");
  reject(()->PlayerBotAllianceControl.action(owner,Map.of("team","699","operation","move","member","100","group","1001")),"Stale team ID rejects before mutation");
  reject(()->PlayerBotAllianceControl.action(owner,Map.of("team","700","operation","kick","member","101")),"Kick requires explicit confirmation");
  reject(()->PlayerBotAllianceControl.action(peer,Map.of("team","700","operation","leave")),"Leave requires explicit confirmation");
  reject(()->PlayerBotAllianceControl.action(owner,Map.of("team","700","operation","move","member","100","group","9999")),"HTTP validates native group range");
  reject(()->PlayerBotAllianceControl.action(owner,Map.of("team","700","operation","swap","member","100","second","333")),"Stale member cannot be swapped");
  var sessions=new ArrayList<PlayerBotSession>();for(int i=1;i<7;i++)sessions.add(PlayerBotTravelFormationCheck.session(owner,players.get(i)));
  var scoped=PlayerBotAllianceControl.scoped(owner,Map.of("team","700","group","1000"),sessions);
  check(scoped.size()==5 && scoped.stream().noneMatch(s->s.bot()==owner || s.bot()==players.get(6)),"Group scope contains only actual owned companions in exact subgroup");
  check(PlayerBotAllianceControl.scoped(owner,Map.of(),sessions).size()==6,"Existing all-companion scope retained");
  var dto=PlayerBotAllianceControl.snapshot(owner,sessions);
  check(dto.get("memberCount").equals(7) && dto.get("capacity").equals(24) && dto.get("canManage").equals(true),"Team DTO derives real native capacity/member authority");
  var groups=(List<?>)dto.get("groups");check(groups.size()==4,"Empty and occupied native groups all remain visible");
  var rows=(List<?>)((Map<?,?>)groups.getFirst()).get("members");var row=(Map<?,?>)rows.get(1);
  check(row.get("online").equals(true) && row.get("controlled").equals(true) && row.get("health").equals(87),"Headless native status and caller control derive from actual session");
  check(((Map<?,?>)rows.getFirst()).get("controlled").equals(false),"Human owner is never a bot command recipient");
  peer.spawned=false;check(((Map<?,?>)((List<?>)((Map<?,?>)((List<?>)PlayerBotAllianceControl.snapshot(owner,sessions).get("groups")).getFirst()).get("members")).get(1)).get("online").equals(false),"Native DTO marks despawned retained identity offline");
  peer.spawned=true;a.onEvent(new ChangeAllianceLeaderEvent(a,peer));
  check(a.getLeaderObject()==peer && !a.isViceCaptain(peer),"Actual native leadership event promotes active headless member");
  check(!PlayerBotAllianceControl.canLead(owner),"Former human captain loses management authority through native event");
 }
 static void conversion()throws Exception {
  var owner=actor(200,false);var group=PlayerBotRaidCheck.group(owner,4);var identities=group.getMembers().stream().map(Player::getObjectId).sorted().toList();
  int[] calls={0};PlayerBotAllianceControl.Conversion adapter=(p,g,members)->{calls[0]++;try{var list=new ArrayList<Actor>();for(var member:members){var copy=actor(member.getObjectId(),member!=p);if(member==p)copy=owner;list.add(copy);}alliance(list);}catch(Exception e){throw new RuntimeException(e);}};
  reject(()->PlayerBotAllianceControl.convert(owner,group.getTeamId()+1,adapter),"Stale conversion rejected under native team lock");
  check(calls[0]==0 && group.size()==4,"Rejected conversion retains all prior party members");
  PlayerBotAllianceControl.convert(owner,(p,g,members)->{calls[0]++;try{PlayerBotRaidCheck.alliance(owner,members);}catch(Exception e){throw new RuntimeException(e);}});
  check(calls[0]==1 && owner.getPlayerAlliance().getMembers().stream().map(Player::getObjectId).sorted().toList().equals(identities),"Actual conversion dispatch verifies every old member retains alliance identity");
  reject(()->PlayerBotAllianceControl.convert(owner,adapter),"Already allied team cannot reconvert");
 }
 static void wire()throws Exception {
  var bot=actor(500,true);var member=new PlayerAllianceMember(bot);member.setAllianceId(1000);
  for(boolean spawned:new boolean[]{true,false})for(var event:List.of(PlayerAllianceEvent.MOVEMENT,PlayerAllianceEvent.ENTER)){
   bot.spawned=spawned;var p=new SM_ALLIANCE_MEMBER_INFO(member,event);var b=ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);p.setBuf(b);
   var write=SM_ALLIANCE_MEMBER_INFO.class.getDeclaredMethod("writeImpl",AionConnection.class);write.setAccessible(true);write.invoke(p,new Object[]{null});
   check(b.getInt(0)==1000 && b.getInt(4)==500,"Native alliance wire retains exact subgroup/identity");
   check(b.getInt(8)==(spawned?1000:0) && b.getInt(12)==(spawned?870:0),"Actual wire includes live headless HP and excludes despawned HP");
   if(event==PlayerAllianceEvent.ENTER)check(Byte.toUnsignedInt(b.get(59))==(spawned?PlayerAllianceEvent.ENTER.getId():PlayerAllianceEvent.ENTER_OFFLINE.getId()),"Native entry event shows active bots online without a fabricated client");
  }
 }
 static void transition()throws Exception {
  var ctor=PlayerBotService.class.getDeclaredConstructor();ctor.setAccessible(true);var service=ctor.newInstance();
  var owner=actor(800,false);var first=actor(801,true);var second=actor(802,true);var members=List.<Player>of(owner,first,second);
  var field=PlayerBotService.class.getDeclaredField("sessions");field.setAccessible(true);
  @SuppressWarnings("unchecked") var sessions=(Map<Integer,PlayerBotSession>)field.get(service);
  sessions.put(first.id,PlayerBotTravelFormationCheck.session(owner,first));sessions.put(second.id,PlayerBotTravelFormationCheck.session(owner,second));
  field=PlayerBotService.class.getDeclaredField("execution");field.setAccessible(true);var execution=(PlayerBotExecution)field.get(service);
  execution.claim(second.id,"checkpoint",System.currentTimeMillis());int[] calls={0};
  reject(()->service.convertTeam(members,()->calls[0]++),"Busy native checkpoint refuses conversion before handoff");
  check(calls[0]==0 && !execution.busy(first.id) && execution.busy(second.id),"Failed multi-member reservation releases only its own claims");execution.complete(second.id);
  service.convertTeam(List.of(owner,first,first,second),()->check(execution.busy(first.id) && execution.busy(second.id),"Native invited-party snapshots with repeated leader/member reserve each actor exactly once"));
  check(!execution.busy(first.id) && !execution.busy(second.id),"Repeated-party snapshot releases both actual actors after handoff");
  try{service.convertTeam(members,()->{throw new IllegalStateException("fixture conversion failure");});throw new AssertionError("Missing propagated failure");}
  catch(IllegalStateException expected){check(expected.getMessage().contains("fixture"),"Native conversion failure propagates without fabricated success");}
  check(!execution.busy(first.id) && !execution.busy(second.id),"Exceptional conversion releases all actor execution claims");
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
  try {
   var job=pool.submit(()->service.convertTeam(members,()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}}));
   check(entered.await(3,java.util.concurrent.TimeUnit.SECONDS),"Actual synchronous native conversion is admitted");
   check(!execution.claim(first.id,"decision",0) && !execution.claim(second.id,"checkpoint",0),"AI and saves cannot observe transient removed-team identity");
   check(execution.claim(9999,"decision",0),"Unrelated actor work remains available during conversion");execution.complete(9999);
   reject(()->service.convertTeam(members,()->{throw new AssertionError("Concurrent handoff");}),"Concurrent conversion refuses without backlog");
   release.countDown();job.get(3,java.util.concurrent.TimeUnit.SECONDS);
   check(!execution.busy(first.id) && !execution.busy(second.id),"Completed native handoff resumes member decisions and saves");
  }finally{release.countDown();pool.shutdown();check(pool.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS),"Synthetic conversion worker terminates");}
  // Exercise actual shutdown drain with no registered actors to avoid native saves.
  sessions.clear();var start=new java.util.concurrent.CountDownLatch(1);var finish=new java.util.concurrent.CountDownLatch(1);pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var convert=pool.submit(()->service.convertTeam(List.of(),()->{start.countDown();try{finish.await();}catch(InterruptedException e){throw new AssertionError(e);}}));
   check(start.await(3,java.util.concurrent.TimeUnit.SECONDS),"Conversion admitted before shutdown");
   var stopped=pool.submit(service::shutdown);Thread.sleep(100);
   check(!stopped.isDone(),"Actual shutdown waits for admitted native team conversion");
   finish.countDown();convert.get(3,java.util.concurrent.TimeUnit.SECONDS);stopped.get(3,java.util.concurrent.TimeUnit.SECONDS);
   reject(()->service.convertTeam(List.of(),()->{throw new AssertionError("Post-shutdown handoff");}),"Shutdown closes new team conversion admission");
  }finally{finish.countDown();pool.shutdown();check(pool.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS),"Synthetic shutdown drain worker terminates");}
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);PlayerBotRaidCheck.unsafe=unsafe;PlayerBotTravelFormationCheck.unsafe=unsafe;
  if(args.length>0){try{wire();throw new AssertionError("Baseline unexpectedly projected headless alliance health");}catch(AssertionError expected){if(!expected.getMessage().contains("Actual wire includes live headless HP"))throw expected;System.out.println("OK: installed baseline reproduces zero-HP headless alliance roster packet; no World/DB/native IDs");return;}}
  membership();ready();management();conversion();wire();transition();
  System.out.println("OK: "+checks+" actual native alliance groups/readiness/authority/scope/conversion/packet checks; no World/DB/native IDs");
 }
}
