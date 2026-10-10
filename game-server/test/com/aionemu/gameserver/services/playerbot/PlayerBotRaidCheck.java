package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.reflect.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.group.*;
import com.aionemu.gameserver.model.team.alliance.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import sun.misc.Unsafe;

/** Actual policy/admission/formation/preset paths with unregistered synthetic actors. No native World/DB/IDs. */
public final class PlayerBotRaidCheck {
 static Unsafe unsafe;static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void rejects(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){check(true,why);}}
 static class Group extends PlayerBotTravelFormationCheck.Group {
  TeamType type=TeamType.GROUP;Group(){super();}
  @Override public TeamType getTeamType(){return type;}
  @Override public List<Player> getMembers(){return members.values().stream().map(PlayerGroupMember::getObject).toList();}
 }
 static class Alliance extends PlayerAlliance {
  TeamType type=TeamType.ALLIANCE;Alliance(){super(null,null);}
  @Override public TeamType getTeamType(){return type;}
 }
 static class Actor extends PlayerBotTravelFormationCheck.Actor {
  Alliance alliance;
  Actor(){super();}
  @Override public boolean isInAlliance(){return alliance!=null;}
  @Override public PlayerAlliance getPlayerAlliance(){return alliance;}
 }
 static Actor actor(int id)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.online=true;a.spawned=true;a.tasks=new PlayerBotTravelFormationCheck.Tasks();return a;
 }
 static Group group(Actor leader,int count)throws Exception {
  var group=(Group)unsafe.allocateInstance(Group.class);group.type=TeamType.GROUP;
  var map=new LinkedHashMap<Integer,PlayerGroupMember>();map.put(leader.id,new PlayerGroupMember(leader));
  for(int i=1;i<count;i++){var peer=actor(leader.id+i);peer.group=group;map.put(peer.id,new PlayerGroupMember(peer));}
  PlayerBotTravelFormationCheck.field(group,"members",map);PlayerBotTravelFormationCheck.field(group,"leader",map.get(leader.id));
  PlayerBotTravelFormationCheck.field(group,"teamLock",new ReentrantLock());leader.group=group;return group;
 }
 static Alliance alliance(Actor leader,List<Player> players)throws Exception {
  var alliance=(Alliance)unsafe.allocateInstance(Alliance.class);alliance.type=TeamType.ALLIANCE;
  var map=new LinkedHashMap<Integer,PlayerAllianceMember>();
  for(var player:players){var a=(Actor)player;a.group=null;a.alliance=alliance;map.put(a.id,new PlayerAllianceMember(a));}
  PlayerBotTravelFormationCheck.field(alliance,"members",map);PlayerBotTravelFormationCheck.field(alliance,"leader",map.get(leader.id));
  PlayerBotTravelFormationCheck.field(alliance,"viceCaptainIds",new ArrayList<Integer>());PlayerBotTravelFormationCheck.field(alliance,"teamLock",new ReentrantLock());return alliance;
 }
 static Object service()throws Exception{var c=PlayerBotService.class.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();}
 static void guard(Object service,Player owner)throws Exception {
  var m=PlayerBotService.class.getDeclaredMethod("ensureOwner",Player.class);m.setAccessible(true);
  try{m.invoke(service,owner);}catch(InvocationTargetException e){throw (IllegalArgumentException)e.getCause();}
 }
 static void admission()throws Exception {
  var owner=actor(1999970000);var group=group(owner,6);var before=group.getMembers().stream().map(Player::getObjectId).sorted().toList();
  check(PlayerBotRaidPolicy.convert(owner),"Full ordinary native party selects alliance expansion");
  check(PlayerBotRaidPolicy.remaining(owner,5,5)==18,"Configured 23 allows recruitment past five");
  guard(service(),owner);check(true,"Actual owned-recruitment guard accepts a full ordinary party for expansion");
  var bot=actor(owner.id+100);int[] calls={0,0};
  var adapter=new PlayerBotRaidPolicy.Admission(){
   public void group(Player a,Player b){calls[0]++;throw new AssertionError("Sixth companion must use native alliance invitation");}
   public void alliance(Player a,Player b){calls[1]++;try{var players=new ArrayList<>(a.getCurrentTeam().getMembers());players.add(b);PlayerBotRaidCheck.alliance(owner,players);}catch(Exception e){throw new RuntimeException(e);}}
  };
  PlayerBotRaidPolicy.join(owner,bot,adapter);
  check(calls[0]==0 && calls[1]==1 && PlayerBotTeams.same(owner,bot),"Actual admission dispatch and membership confirmation use alliance branch");
  check(owner.alliance.size()==7 && owner.alliance.getMembers().stream().map(Player::getObjectId).toList().containsAll(before),"Conversion retains every prior bot/human identity");
  guard(service(),owner);check(true,"Owned recruitment works in an existing alliance");
  check(PlayerBotTeams.members(owner).size()==7,"Shared support/follow recipients retain the entire native alliance");
  var other=actor(owner.id+200);other.alliance=owner.alliance;
  rejects(()->PlayerBotRaidPolicy.authority(other),"Ordinary alliance member cannot recruit");
  owner.alliance.getViceCaptainIds().add(other.id);PlayerBotRaidPolicy.authority(other);check(true,"Native vice captain authority retained");
  var occupied=new ArrayList<Player>(owner.alliance.getMembers());for(int i=0;occupied.size()<24;i++)occupied.add(actor(owner.id+300+i));alliance(owner,occupied);
  check(PlayerBotRaidPolicy.remaining(owner,6,6)==0,"Other human members consume native alliance slots");
  rejects(()->PlayerBotRaidPolicy.join(owner,actorUnchecked(owner.id+800),adapter),"Full native alliance refuses before admission");
  owner.alliance=null;group=group(owner,6);PlayerBotConfig.MAX_PER_OWNER=5;
  check(!PlayerBotRaidPolicy.convert(owner) && PlayerBotRaidPolicy.remaining(owner,5,5)==0,"Configured five preserves ordinary party quota");
  PlayerBotConfig.MAX_PER_OWNER=23;group.type=TeamType.AUTO_GROUP;
  rejects(()->PlayerBotRaidPolicy.authority(owner),"Automatic/special party cannot be force-converted");
  group.type=TeamType.GROUP;check(PlayerBotRaidPolicy.remaining(owner,5,100)==0,"Global companion cap remains separate");
  rejects(()->PlayerBotRaidPolicy.join(owner,actorUnchecked(owner.id+801),new PlayerBotRaidPolicy.Admission(){public void group(Player a,Player b){}public void alliance(Player a,Player b){}}),"Native admission refusal never registers a successful companion");
 }
 static Actor actorUnchecked(int id){try{return actor(id);}catch(Exception e){throw new RuntimeException(e);}}
 static void formations(){
  for(String shape:List.of("circle","box","line","spread"))for(int count=1;count<=23;count++){
   var points=new HashSet<String>();
   for(int slot=0;slot<count;slot++){
    var p=PlayerBotFormationLayout.point(0,0,4,0,slot,count,shape);points.add(p.x()+":"+p.y());
    check(Float.isFinite(p.x()) && Float.isFinite(p.y()) && p.z()==4 && Math.hypot(p.x(),p.y())<16,"Raid formations stay finite and within local follow envelope");
   }
   check(points.size()==count,"Every raid slot is distinct, including each row of five");
  }
  rejects(()->PlayerBotFormationLayout.point(0,0,0,0,23,24,"line"),"Native 24th controlled companion cannot get a formation slot");
  check(PlayerBotFormationLayout.lineExtent(23)<16,"Raid line uses bounded rows rather than a 37-metre wing");
 }
 static void presets()throws Exception {
  var members=new ArrayList<PlayerBotPresets.Member>();for(int i=1;i<=23;i++)members.add(new PlayerBotPresets.Member(i,"Bot"+i,i%2==0,Role.HEALER,Order.FOLLOW));
  var preset=new PlayerBotPresets.Preset("0123456789abcdef0123456789abcdef","Raid",members);
  var document=new PlayerBotPresets.Document(7,Set.of(2),List.of(preset));
  check(PlayerBotRepository.decode(7,PlayerBotRepository.encode(document)).equals(document),"Native preset roundtrip keeps 23 roles/orders/IDs and mixed owned builds");
  members.add(new PlayerBotPresets.Member(24,"Overflow",true,Role.TANK,Order.GUARD));
  rejects(()->new PlayerBotPresets.Preset(preset.id(),"Overflow",members),"Preset cannot exceed native alliance capacity");
  PlayerBotPresets.checkRoom(5,18,6,23,5,100,24);check(true,"Raid preset can preflight a party expansion");
  rejects(()->PlayerBotPresets.checkRoom(5,18,7,23,5,100,24),"Other humans are kept instead of overfilling native team");
  rejects(()->PlayerBotPresets.checkRoom(5,18,6,23,83,100,24),"Preset does not bypass global cap");
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  PlayerBotConfig.ENABLED=true;PlayerBotConfig.MAX_PER_OWNER=23;PlayerBotConfig.MAX_ACTIVE=100;
  if(args.length>0){var owner=actor(1999979000);var players=List.<Player>of(owner,actor(owner.id+1));alliance(owner,players);
   try{guard(service(),owner);throw new AssertionError("Installed baseline unexpectedly admitted owned alliance companion");}
   catch(IllegalArgumentException e){check(e.getMessage().contains("alliance membership is not implemented"),"Installed baseline reproduces owned-alliance refusal");}
   System.out.println("OK: baseline owned alliance rejection reproduced; no world/DB/native IDs");return;
  }
  check(PlayerBotRaidPolicy.limit()==23,"Configured maximum follows native 24-member alliance");
  PlayerBotConfig.MAX_PER_OWNER=100;check(PlayerBotRaidPolicy.limit()==23,"Configuration cannot overfill native alliance");
  PlayerBotConfig.MAX_PER_OWNER=-1;check(PlayerBotRaidPolicy.limit()==0,"Invalid negative quota refuses new recruitment");PlayerBotConfig.MAX_PER_OWNER=23;
  admission();formations();presets();
  System.out.println("OK: "+checks+" actual raid admission/capacity/formation/preset checks; no world/DB/native IDs");
 }
}
