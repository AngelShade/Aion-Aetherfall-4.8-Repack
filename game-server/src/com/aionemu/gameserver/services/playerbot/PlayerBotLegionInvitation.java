/* GuildAcceptAction::Execute and PlayerbotSecurity::CheckLevelFor(ignoreGroup=true),
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later;
 * third-party/playerbots/AUTHORS.md. Native Aion invitation response adapter. */
package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.LegionConfig;
import com.aionemu.gameserver.model.gameobjects.player.DeniedStatus;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.legion.Legion;
import com.aionemu.gameserver.model.team.legion.LegionPermissionsMask;
import com.aionemu.gameserver.model.team.legion.LegionWarehouse;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Answers an already registered native request; membership and SQL stay in LegionService. */
final class PlayerBotLegionInvitation {
 static String blocker(PlayerBotSession session, Player inviter, Player bot, Legion invitedLegion,
                       boolean enabled, boolean worldEnabled, boolean available) {
  if(!enabled || !available || session==null || session.bot()!=bot || session.closing() || !bot.isSpawned())
   return "The bot is unavailable or waiting for dismissal/custody verification.";
  if(inviter==null || inviter.isPlayerBot() || !inviter.isOnline() || !inviter.isSpawned() || inviter.isDead())
   return "A living connected player must send the legion invitation.";
  if(session.world()) {
   if(!worldEnabled)return "World bots are disabled.";
  } else if(session.owner()!=inviter || !PlayerBotWarehouse.privateOwner(session))
   return "Only the owner can enroll a private companion in a legion.";
  if(bot.isLegionMember())return "The bot already belongs to a legion.";
  if(invitedLegion==null || inviter.getLegion()!=invitedLegion || !invitedLegion.isMember(inviter.getObjectId()))
   return "The inviter's legion membership has changed.";
  if(invitedLegion.isDisbanding() || LegionWarehouse.custodyHeld(invitedLegion.getLegionId()))
   return "The legion is disbanding or waiting for custody verification.";
  if(!inviter.getLegionMember().hasRights(LegionPermissionsMask.INVITE))return "You do not have legion invitation rights.";
  if(bot.getPlayerSettings().isInDeniedStatus(DeniedStatus.GUILD))return "The bot has declined legion invitations in its native settings.";
  if(inviter.getRace()!=bot.getRace() && !LegionConfig.LEGION_INVITEOTHERFACTION)return "This legion cannot invite the other faction.";
  return "";
 }

 static boolean respond(PlayerBotSession session, Player inviter, Player bot, Legion invitedLegion,
                        boolean enabled, boolean worldEnabled, boolean available) {
  if(!bot.isPlayerBot())return false;
  if(session==null)return answer(null,inviter,bot,invitedLegion,enabled,worldEnabled,available);
  synchronized(session) {return answer(session,inviter,bot,invitedLegion,enabled,worldEnabled,available);}
 }

 private static boolean answer(PlayerBotSession session, Player inviter, Player bot, Legion invitedLegion,
                               boolean enabled, boolean worldEnabled, boolean available) {
  String reason=blocker(session,inviter,bot,invitedLegion,enabled,worldEnabled,available);
  // Consume the native request exactly once, as CM_QUESTION_RESPONSE does.
  boolean answered=bot.getResponseRequester().respond(SM_QUESTION_WINDOW.STR_GUILD_INVITE_DO_YOU_ACCEPT_INVITATION,reason.isEmpty()?1:0);
  if(answered && !reason.isEmpty())notify(inviter,"Legion invitation declined: "+reason);
  else if(answered && bot.getLegion()==invitedLegion && invitedLegion.isMember(bot.getObjectId()))
   notify(inviter,bot.getName()+" joined your legion "+invitedLegion.getName()+".");
  return true;
 }

 private static void notify(Player player,String message) {
  if(player!=null && player.getClientConnection()!=null)PacketSendUtility.sendMessage(player,message);
 }
 private PlayerBotLegionInvitation(){}
}
