/* Native party/alliance integration mapped from pinned GroupInviteOperation and
 * ReadyCheckAction (037c01418b5d01506917a3db9b44fd56ac5f965c), GPL-2.0-or-later.
 * Aion's native four-party alliance limits, events and ownership remain authoritative. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.*;
import com.aionemu.gameserver.model.team.group.*;
import com.aionemu.gameserver.model.team.alliance.*;
import com.aionemu.gameserver.model.team.alliance.events.AssignViceCaptainEvent.AssignType;
import com.aionemu.gameserver.model.team.common.events.TeamCommand;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;

/** Native team management and owner-scoped bot commands for the companion panel. */
public final class PlayerBotAllianceControl {
 private PlayerBotAllianceControl(){}
 static boolean canLead(Player owner){return owner.getCurrentTeam()!=null && owner.getCurrentTeam().isLeader(owner);}
 static boolean canManage(Player owner){return canLead(owner) || owner.isInAlliance() && owner.getPlayerAlliance().isViceCaptain(owner);}
 static boolean canConvert(Player owner){return owner.isInGroup() && owner.getPlayerGroup().getTeamType()==TeamType.GROUP && canLead(owner) && owner.getPlayerGroup().size()>=2;}
 static void expected(Player owner,int id){if(owner.getCurrentTeamId()!=id)throw new IllegalArgumentException("Your team changed. Refresh before managing it.");}
 static void authority(Player owner,boolean leader){
  if(owner.getCurrentTeam()==null || !PlayerBotTeams.allowed(owner.getCurrentTeam()) || !(leader?canLead(owner):canManage(owner)))
   throw new IllegalArgumentException(leader?"Only the native party/alliance leader can do that.":"Only the native party leader or alliance captain can do that.");
 }
 public static Map<String,Object> snapshot(Player owner){return snapshot(owner,PlayerBotService.getInstance().companions(owner));}
 static Map<String,Object> snapshot(Player owner,List<PlayerBotSession> controlled){
  var result=new LinkedHashMap<String,Object>();var team=owner.getCurrentTeam();
  Runnable capture=()->{
   var controlledIds=new HashSet<Integer>();controlled.forEach(s->controlledIds.add(s.bot().getObjectId()));
   result.put("id",owner.getCurrentTeamId());result.put("kind",owner.isInAlliance()?"Alliance":owner.isInGroup()?"Party":"Solo");
   result.put("leaderId",team==null?owner.getObjectId():team.getLeaderObject().getObjectId());
   result.put("canManage",canManage(owner));result.put("canLead",canLead(owner));result.put("canConvert",canConvert(owner));
   result.put("capacity",owner.isInAlliance()?24:6);result.put("memberCount",team==null?1:team.size());
   var groups=new ArrayList<Map<String,Object>>();
   if(owner.isInAlliance())for(int id=1000;id<=1003;id++){var g=owner.getPlayerAlliance().getAllianceGroup(id);groups.add(group(owner,controlledIds,id,id-999,g.getMembers()));}
   else groups.add(group(owner,controlledIds,0,1,team==null?List.of(owner):team.getMembers()));
   result.put("groups",groups);
   result.put("ready",owner.isInAlliance()?owner.getPlayerAlliance().getReadyCheck().snapshot():Map.of("active",false,"waiting",0,"states",List.of()));
  };
  if(team==null)capture.run();else team.executeLocked(()->{if(owner.getCurrentTeam()!=team)throw new IllegalArgumentException("Your team changed; refresh its roster.");capture.run();});
  return result;
 }
 private static Map<String,Object> group(Player owner,Set<Integer> controlled,int id,int number,List<Player> players){
  var members=players.stream().sorted(Comparator.comparingInt(Player::getObjectId)).map(p->{
   var row=new LinkedHashMap<String,Object>();row.put("id",p.getObjectId());row.put("name",p.getName());row.put("level",p.getLevel());row.put("playerClass",p.getPlayerClass().name());
   row.put("online",p.isPlaying());row.put("dead",p.isDead());row.put("controlled",controlled.contains(p.getObjectId()));row.put("self",p==owner);
   row.put("leader",p.getCurrentTeam()!=null && p.getCurrentTeam().isLeader(p));row.put("viceCaptain",p.isInAlliance() && p.getPlayerAlliance().isViceCaptain(p));
   row.put("health",p.getLifeStats().getHpPercentage());row.put("mana",p.getLifeStats().getMpPercentage());return row;
  }).toList();return Map.of("id",id,"number",number,"capacity",6,"members",members);
 }
 interface Conversion {void convert(Player owner,PlayerGroup group,List<Player> members);}
 private static final Conversion NATIVE=(owner,group,members)->{
  PlayerBotService.getInstance().convertTeam(members,()->{
  var loot=group.getLootGroupRules();
  for(var member:members)PlayerGroupService.removePlayer(member);
  var other=members.stream().filter(p->p!=owner).findFirst().orElseThrow();
  var alliance=PlayerAllianceService.createAlliance(owner,other,TeamType.ALLIANCE);
  for(var member:members)if(member!=owner && member!=other)PlayerAllianceService.addPlayer(alliance,member);
  if(loot!=null)PlayerAllianceService.changeGroupRules(alliance,loot);
  });
 };
 static void convert(Player owner,Conversion operation){
  convert(owner,owner.getCurrentTeamId(),operation);
 }
 static void convert(Player owner,int expectedTeam,Conversion operation){
  var group=owner.getPlayerGroup();if(group==null)throw new IllegalArgumentException("Recruit or invite a party member before converting.");
  group.executeLocked(()->{
   expected(owner,expectedTeam);
   if(!canConvert(owner) || owner.getPlayerGroup()!=group)throw new IllegalArgumentException("Lead an ordinary party with at least two members to convert it.");
   var members=List.copyOf(group.getMembers());
   if(members.size()>6 || members.stream().anyMatch(p->p.getPlayerGroup()!=group || p.isInAlliance()))throw new IllegalArgumentException("Party membership changed; refresh before converting.");
   operation.convert(owner,group,members);
   if(!owner.isInAlliance() || members.stream().anyMatch(p->p.getPlayerAlliance()!=owner.getPlayerAlliance()))throw new IllegalStateException("Native conversion did not retain every party member.");
  });
 }
 public static String action(Player owner,Map<String,String> values){
  int teamId=Integer.parseInt(values.getOrDefault("team","-1"));expected(owner,teamId);
  String op=values.getOrDefault("operation","");
  if(op.equals("convert")){convert(owner,teamId,NATIVE);return "Party converted to a native alliance. All members and loot rules retained.";}
  if(op.equals("invite")){
   if(owner.getCurrentTeam()!=null)authority(owner,false);
   var target=World.getInstance().getPlayer(values.getOrDefault("name",""));
   if(target==null || target==owner)throw new IllegalArgumentException("Choose a player or world bot currently in the world.");
   if(owner.isInAlliance())PlayerAllianceService.inviteToAlliance(owner,target);else PlayerGroupService.inviteToGroup(owner,target);
   return "Native invitation requested. The recipient's normal eligibility and acceptance apply.";
  }
  var team=owner.getCurrentTeam();if(team==null)throw new IllegalArgumentException("You have no native team to manage.");
  // Native membership operations use the team lock, never a bot session/save lock.
  team.executeLocked(()->{
   expected(owner,teamId);
   switch(op){
    case "move", "swap" -> {
     authority(owner,false);if(!owner.isInAlliance())throw new IllegalArgumentException("Group arrangement requires an alliance.");
     var member=member(owner,values,"member");int second=op.equals("swap")?member(owner,values,"second").getObjectId():0;
     int group=Integer.parseInt(values.getOrDefault("group","0"));
     if(second==0){if(group<1000 || group>1003)throw new IllegalArgumentException("Choose alliance group 1 to 4.");var target=owner.getPlayerAlliance().getAllianceGroup(group);
      if(member.getPlayerAllianceGroup()!=target && target.size()>=6)throw new IllegalArgumentException("That group is full. Swap two members instead.");}
     PlayerAllianceService.changeMemberGroup(owner,member.getObjectId(),second,group);
    }
    case "leader" -> {authority(owner,true);var member=member(owner,values,"member");if(member==owner)throw new IllegalArgumentException("You are already the leader.");if(!member.isPlaying())throw new IllegalArgumentException("The new leader must be in the world.");
     if(owner.isInAlliance())PlayerAllianceService.changeLeader(member);else PlayerGroupService.changeLeader(member);}
    case "vice" -> {authority(owner,true);if(!owner.isInAlliance())throw new IllegalArgumentException("Vice captains require an alliance.");var member=member(owner,values,"member");
     if(owner.getPlayerAlliance().isLeader(member) || !member.isPlaying())throw new IllegalArgumentException("Choose an active alliance member other than the leader.");String raw=values.getOrDefault("enabled","");if(!raw.equals("true") && !raw.equals("false"))throw new IllegalArgumentException("Choose promote or demote.");
     if(raw.equals("true") && !owner.getPlayerAlliance().isViceCaptain(member) && owner.getPlayerAlliance().getViceCaptainIds().size()>=4)throw new IllegalArgumentException("The alliance already has four vice captains.");
     PlayerAllianceService.changeViceCaptain(member,raw.equals("true")?AssignType.PROMOTE:AssignType.DEMOTE);}
    case "kick" -> {authority(owner,true);confirmed(values);var member=member(owner,values,"member");if(member==owner)throw new IllegalArgumentException("Use Leave to leave your team.");
     if(owner.isInAlliance())PlayerAllianceService.banPlayer(member,owner);else PlayerGroupService.banPlayer(member,owner);}
    case "leave" -> {confirmed(values);if(!PlayerBotTeams.allowed(team))throw new IllegalArgumentException("Special teams retain their native leave controls.");PlayerBotTeams.remove(owner);}
    case "ready", "cancelready" -> {authority(owner,false);if(!owner.isInAlliance())throw new IllegalArgumentException("Ready checks require an alliance.");PlayerAllianceService.checkReady(owner,op.equals("ready")?TeamCommand.ALLIANCE_CHECKREADY_START:TeamCommand.ALLIANCE_CHECKREADY_CANCEL);}
    default -> throw new IllegalArgumentException("Unknown native team action.");
   }
  });
  return "Native team updated.";
 }
 private static void confirmed(Map<String,String> values){if(!"yes".equals(values.get("confirm")))throw new IllegalArgumentException("Confirm this native team action first.");}
 private static Player member(Player owner,Map<String,String> values,String key){int id=Integer.parseInt(values.getOrDefault(key,"0"));var member=owner.getCurrentTeam().getMember(id);if(member==null)throw new IllegalArgumentException("That member left the team. Refresh its roster.");return member.getObject();}
 public static List<PlayerBotSession> scoped(Player owner,String name,Map<String,String> values){
  var service=PlayerBotService.getInstance();var sessions=name.equals("all")?service.companions(owner):List.of(service.find(owner,name));
  return scoped(owner,values,sessions);
 }
 static List<PlayerBotSession> scoped(Player owner,Map<String,String> values,List<PlayerBotSession> sessions){
  if(!values.containsKey("group"))return sessions;
  var team=owner.getPlayerAlliance();if(team==null)throw new IllegalArgumentException("Group-scoped orders require an alliance.");
  var selected=new ArrayList<PlayerBotSession>();
  team.executeLocked(()->{
   expected(owner,Integer.parseInt(values.getOrDefault("team","-1")));
   if(owner.getPlayerAlliance()!=team)throw new IllegalArgumentException("Your alliance changed; refresh its roster.");
   int group=Integer.parseInt(values.get("group"));if(group<1000 || group>1003)throw new IllegalArgumentException("Choose alliance group 1 to 4.");
   var target=team.getAllianceGroup(group);
   sessions.stream().filter(s->PlayerBotTeams.same(owner,s.bot()) && s.bot().getPlayerAllianceGroup()==target).forEach(selected::add);
  });
  return List.copyOf(selected);
 }
 public static void respondReady(PlayerAlliance alliance,Player initiator){
  for(var bot:alliance.getOnlineMembers())if(bot!=initiator && bot.isPlayerBot()){
   String problem=PlayerBotService.getInstance().headlessHealth(bot);
   boolean ready=problem==null && !bot.isDead();
   PacketSendUtility.sendMessage(initiator,bot.getName()+": HP "+bot.getLifeStats().getHpPercentage()+"%, MP "+bot.getLifeStats().getMpPercentage()+"%"+(ready?"; ready.":"; not ready: "+(problem==null?"needs resurrection":problem)+"."));
   alliance.onEvent(new com.aionemu.gameserver.model.team.alliance.events.CheckAllianceReadyEvent(alliance,bot,ready?TeamCommand.ALLIANCE_CHECKREADY_READY:TeamCommand.ALLIANCE_CHECKREADY_NOTREADY));
  }
 }
}
