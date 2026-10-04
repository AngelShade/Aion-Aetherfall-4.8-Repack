package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Request-local creation choice; existing command/API callers retain level-matched generation. */
public final class PlayerBotGenerationOptions {
 private static final ThreadLocal<Integer> LEVEL = new ThreadLocal<>();
 private PlayerBotGenerationOptions() {}

 public static int level(int ownerLevel) { return LEVEL.get() == null ? ownerLevel : LEVEL.get(); }

 public static void initialize(Player player) {
  if(player.getAbyssRank()==null)player.setAbyssRank(new com.aionemu.gameserver.model.gameobjects.player.AbyssRank(0,0,0,1,0,0,0,1,0,0,System.currentTimeMillis(),0,0,0,0));
  if(player.getEffectController()!=null){player.getLifeStats().synchronizeWithMaxStats();return;}
  var common = player.getCommonData();
  player.setPosition(com.aionemu.gameserver.world.World.getInstance().createPosition(common.getMapId(), common.getX(), common.getY(), common.getZ(), common.getHeading(), 0));
  player.setKnownlist(new com.aionemu.gameserver.world.knownlist.KnownList(player));
  player.setEffectController(new com.aionemu.gameserver.controllers.effect.PlayerEffectController(player));
  player.setFlyController(new com.aionemu.gameserver.controllers.FlyController(player));
  com.aionemu.gameserver.model.stats.calc.functions.PlayerStatFunctions.addPredefinedStatFunctions(player);
  player.getEquipment().onLoadApplyEquipmentStats();
  for (var entry : player.getSkillList().getAllSkills()) {
   var template = com.aionemu.gameserver.dataholders.DataManager.SKILL_DATA.getSkillTemplate(entry.getSkillId());
   if (template != null && template.isPassive()) com.aionemu.gameserver.skillengine.SkillEngine.getInstance().applyEffectDirectly(template, entry.getSkillLevel(), player, player);
  }
  player.getLifeStats().synchronizeWithMaxStats();
 }

 public static int choose(String option, int ownerLevel) {
  return switch (option) { case "matched" -> ownerLevel; case "level1" -> 1; default -> throw new IllegalArgumentException("Choose your current level or level 1."); };
 }

 public static void generate(Player owner, String name, PlayerClass playerClass, String option) {
  if (LEVEL.get() != null) throw new IllegalStateException("Nested companion creation");
  LEVEL.set(choose(option, owner.getLevel()));
  try { PlayerBotService.getInstance().generate(owner, name, playerClass); }
  finally { LEVEL.remove(); }
 }
}
