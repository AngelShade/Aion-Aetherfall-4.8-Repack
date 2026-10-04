package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.skillengine.SkillEngine;

/** Recover owned active companions after a wipe or an explicit out-of-combat Summon. */
public final class PlayerBotRecovery {
 private static final Set<Integer> WAITING=ConcurrentHashMap.newKeySet();
 static boolean ready(PlayerBotSession s) {
  Player owner=s.owner(),bot=s.bot();
  return !s.closing() && owner.isOnline() && owner.isSpawned() && !owner.isDead() && owner.getWorldMapInstance()!=null
   && bot.isPlayerBot() && bot.getPlayerBotOwnerId()==owner.getObjectId() && owner.getPlayerGroup()!=null && bot.getPlayerGroup()==owner.getPlayerGroup()
   && !owner.getController().isInCombat() && (bot.isDead() || !bot.getController().isInCombat() && bot.getAggroList().stream().findAny().isEmpty());
 }
 static void revive(PlayerBotSession s) {
  if(!ready(s))throw new IllegalArgumentException("Wait until you are alive and the party is out of combat before reviving and summoning companions.");
  var bot=s.bot();if(!bot.isDead())return;
  bot.getController().cancelCurrentSkill(null);bot.getMoveController().abortMove();
  PlayerReviveService.revive(bot,25,25,true,0);bot.unsetResPosState();bot.setIsFlyingBeforeDeath(false);bot.getGameStats().updateStatsAndSpeedVisually();
  for(var learned:bot.getSkillList().getAllSkills()) {
   var t=com.aionemu.gameserver.dataholders.DataManager.SKILL_DATA.getSkillTemplate(learned.getSkillId());
   if(t!=null && t.isPassive() && !bot.getEffectController().hasAbnormalEffect(t.getSkillId()))SkillEngine.getInstance().applyEffectDirectly(t,learned.getSkillLevel(),bot,bot);
  }
 }
 static void tick(PlayerBotSession s) {
  if(s.owner().isDead()){WAITING.add(s.owner().getObjectId());return;}
  if(!s.bot().isDead())return;
  var party=PlayerBotService.getInstance().companions(s.owner());
  if(!WAITING.contains(s.owner().getObjectId()) && (party.isEmpty() || party.stream().anyMatch(member->!member.bot().isDead())))return;
  if(party.stream().anyMatch(member->!ready(member)))return;
  PlayerBotTravel.summonAll(party);WAITING.remove(s.owner().getObjectId());
  com.aionemu.gameserver.utils.PacketSendUtility.sendMessage(s.owner(),"Your companions have revived and regrouped after the wipe. They are recovering and preparing their builds.");
 }
 public static List<PlayerBotSession> selected(Player owner,String name) {
  var service=PlayerBotService.getInstance();if(!name.equalsIgnoreCase("all") && !name.equalsIgnoreCase("selected"))return List.of(service.find(owner,name));
  if(owner.getTarget()==null)return service.companions(owner);
  var selected=service.companions(owner).stream().filter(s->s.bot()==owner.getTarget()).findFirst();
  if(selected.isPresent())return List.of(selected.get());
  throw new IllegalArgumentException("Select one of your active companions, or clear your target to summon the whole party.");
 }
 static void close(Player owner){if(PlayerBotService.getInstance().companions(owner).stream().allMatch(PlayerBotSession::closing))WAITING.remove(owner.getObjectId());}
 private PlayerBotRecovery() {}
}
