package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.alliance.events.PlayerAllianceInvite;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;

/** Pinned GroupInviteOperation: ordinary full party expands through native alliance acceptance. */
public final class PlayerBotRaidPolicy {
 public static final int MAX_COMPANIONS = 23; // Native alliance: 24 total players, including its human controller.
 interface Admission { void group(Player owner,Player bot); void alliance(Player owner,Player bot); }
 private static final Admission NATIVE = new Admission() {
  public void group(Player owner,Player bot) {
   if(owner.isInGroup())PlayerGroupService.addPlayer(owner.getPlayerGroup(),bot);
   else PlayerGroupService.createGroup(owner,bot,TeamType.GROUP,0);
  }
  public void alliance(Player owner,Player bot) {new PlayerAllianceInvite(owner).acceptRequest(owner,bot);}
 };
 private PlayerBotRaidPolicy() {}
 public static int limit() { return Math.max(0, Math.min(MAX_COMPANIONS, PlayerBotConfig.MAX_PER_OWNER)); }
 static int capacity(Player owner) { return owner.isInAlliance() || limit()>5 ? 24 : 6; }
 static boolean convert(Player owner) { return !owner.isInAlliance() && owner.getPlayerGroup()!=null && owner.getPlayerGroup().size()>=6 && limit()>5; }
 static void authority(Player owner) {
  var team=owner.getCurrentTeam();
  if(team==null)return;
  if(!PlayerBotTeams.allowed(team) || !team.isLeader(owner) && !(owner.isInAlliance() && owner.getPlayerAlliance().isViceCaptain(owner)))
   throw new IllegalArgumentException("Lead an ordinary party or be an alliance captain to recruit companions.");
 }
 public static int remaining(Player owner,int controlled,int global) {
  try{authority(owner);}catch(IllegalArgumentException e){return 0;}
  int size=owner.getCurrentTeam()==null ? 1 : owner.getCurrentTeam().size();
  return Math.max(0,Math.min(limit()-controlled,Math.min(capacity(owner)-size,PlayerBotConfig.MAX_ACTIVE-global)));
 }
 static void room(Player owner,int controlled,int global) {
  authority(owner);
  if(remaining(owner,controlled,global)==0)throw new IllegalArgumentException("The configured companion limit or native party/alliance capacity has been reached.");
 }
 static void join(Player owner,Player bot) {
  join(owner,bot,NATIVE);
 }
 static void join(Player owner,Player bot,Admission admission) {
  if(bot.getCurrentTeam()!=null)throw new IllegalArgumentException("This companion already belongs to a team.");
  var team=owner.getCurrentTeam();
  Runnable operation=()->{
   // Recheck native capacity/leadership at actual admission, including other human members.
   authority(owner);
   if(owner.getCurrentTeam()!=team)throw new IllegalArgumentException("Your team changed; recruit again.");
   if(team!=null && team.size()>=capacity(owner))throw new IllegalArgumentException("Your native team is full.");
   if(owner.isInAlliance() || convert(owner))admission.alliance(owner,bot);
   else admission.group(owner,bot);
   if(!PlayerBotTeams.same(owner,bot))throw new IllegalArgumentException("Native party/alliance admission refused this companion. Check its invitation eligibility.");
  };
  if(team==null)operation.run();else team.executeLocked(operation);
 }
}
