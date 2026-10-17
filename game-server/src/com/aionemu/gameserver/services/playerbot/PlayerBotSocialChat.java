package com.aionemu.gameserver.services.playerbot;

import java.util.Locale;
import java.util.regex.Pattern;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.LegionConfig;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.player.DeniedStatus;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.legion.LegionPermissionsMask;
import com.aionemu.gameserver.model.team.legion.LegionWarehouse;
import com.aionemu.gameserver.services.LegionService;
import com.aionemu.gameserver.utils.PacketSendUtility;

/** Name-addressed requests; native LegionService owns the invitation and acceptance. */
public final class PlayerBotSocialChat {
 private static final Pattern JOIN=Pattern.compile("\\b(?:invit(?:e|es|ed|ing)|inv|join(?:ing)?|add(?:ed)?|recruit(?:ed)?|enrol|enroll|let\\s+me\\s+(?:in|into)|become\\s+a\\s+member)\\b");
 private static final Pattern LEGION=Pattern.compile("\\b(?:legion|guild)\\b");
 private static final Pattern SELF=Pattern.compile("\\b(?:me|i|i'd|i'm|myself)\\b");
 private static final Pattern NEGATIVE=Pattern.compile("\\b(?:not|never|don't|dont|do not|cancel|stop|no)\\b");
 static boolean request(String message,String name,boolean whisper){
  if(message==null || name==null || message.length()>512)return false;
  String text=message.toLowerCase(Locale.ROOT).replace('\u2019','\'');
  if(!JOIN.matcher(text).find() || !LEGION.matcher(text).find() || !SELF.matcher(text).find() || NEGATIVE.matcher(text).find())return false;
  return whisper || Pattern.compile("(?<![\\p{L}\\p{N}_])"+Pattern.quote(name.toLowerCase(Locale.ROOT))+"(?![\\p{L}\\p{N}_])").matcher(text).find();
 }
 public static void heard(Player sender,ChatType type,String message,Player receiver){
  if(!PlayerBotConfig.ENABLED || !PlayerBotConfig.CHAT_LEGION_INVITES || sender==null || sender.isPlayerBot())return;
  if(type==ChatType.WHISPER){if(receiver!=null && request(message,receiver.getName(),true))invite(sender,receiver);return;}
  if(type!=ChatType.NORMAL && type!=ChatType.SHOUT && type!=ChatType.GROUP && type!=ChatType.ALLIANCE)return;
  // Visit only players who could hear this channel; no population-wide scan.
  sender.getKnownList().forEachPlayer(bot->{
   if(bot.isPlayerBot() && bot.getKnownList().sees(sender) && !bot.getBlockList().contains(sender.getObjectId())
    && (type!=ChatType.GROUP || sender.isInTeam() && bot.getCurrentGroup()==sender.getCurrentGroup())
    && (type!=ChatType.ALLIANCE || sender.isInAlliance() && bot.getPlayerAlliance()==sender.getPlayerAlliance())
    && request(message,bot.getName(),false))invite(sender,bot);
  });
 }
 private static void invite(Player sender,Player bot){
  if(!PlayerBotService.getInstance().socialAvailable(bot) || sender.isDead() || !sender.isOnline())return;
  String reason=sender.isLegionMember()?"You already belong to a legion.":!bot.isLegionMember()?"I don't belong to a legion.":
   !bot.getLegionMember().hasRights(LegionPermissionsMask.INVITE)?"I don't have permission to invite legion members.":
   sender.getPlayerSettings().isInDeniedStatus(DeniedStatus.GUILD)?"Your settings are blocking legion invitations.":
   bot.getRace()!=sender.getRace() && !LegionConfig.LEGION_INVITEOTHERFACTION?"My legion cannot invite your faction.":
   bot.getLegion().isDisbanding() || LegionWarehouse.custodyHeld(bot.getLegion().getLegionId())?"My legion is unavailable for invitations.":"";
  if(!reason.isEmpty()){PacketSendUtility.sendMessage(sender,bot.getName()+": "+reason);return;}
  LegionService.getInstance().invitePlayerToLegion(bot,sender.getName());
 }
 private PlayerBotSocialChat(){}
}
