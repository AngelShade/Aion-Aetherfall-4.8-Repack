package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.IntPredicate;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

/** Native Aion class/mastery restrictions precede the PlayerbotFactory gear score. */
public final class PlayerBotBuildRules {
 public static List<com.aionemu.gameserver.model.templates.item.ItemTemplate> sortStarter(List<com.aionemu.gameserver.model.templates.item.ItemTemplate> choices,com.aionemu.gameserver.model.gameobjects.player.Player bot){return choices.stream().sorted(Comparator.<com.aionemu.gameserver.model.templates.item.ItemTemplate>comparingDouble(t->PlayerBotEquipment.score(t,PlayerBotRules.roleFor(bot.getPlayerClass()),bot.getPlayerClass())).reversed().thenComparingInt(com.aionemu.gameserver.model.templates.item.ItemTemplate::getTemplateId)).toList();}
 public static String armor(PlayerClass pc,IntPredicate learned) {
  String desired=switch(pc){case WARRIOR,GLADIATOR,TEMPLAR,RIDER->"PL_";case PRIEST,CLERIC,CHANTER->"CH_";case SCOUT,ASSASSIN,RANGER,ENGINEER,GUNNER->"LT_";default->"RB_";};
  List<String> order=switch(desired){case "PL_"->List.of("PL_","CH_","LT_","RB_");case "CH_"->List.of("CH_","LT_","RB_");case "LT_"->List.of("LT_","RB_");default->List.of("RB_");};
  for(String prefix:order){var group=ItemGroup.valueOf(prefix+"TORSO");var skills=DataManager.SKILL_DATA.getMasterySkills(group);if(skills.isEmpty() || skills.stream().anyMatch(learned::test))return prefix;}
  return "RB_";
 }
 public static boolean armorAllowed(ItemGroup group,PlayerClass pc,IntPredicate learned) {
  String name=group.name();if(!name.matches("(PL|CH|LT|RB)_(TORSO|GLOVE|SHOULDER|PANTS|SHOES)"))return true;
  return name.startsWith(armor(pc,learned));
 }
 public static boolean weaponAllowed(ItemGroup group,PlayerClass pc,Role role) {
  return switch(pc){
   case WARRIOR,TEMPLAR->role==Role.TANK ? group==ItemGroup.SWORD || group==ItemGroup.MACE : group==ItemGroup.SWORD || group==ItemGroup.GREATSWORD;
   case GLADIATOR->role==Role.TANK ? group==ItemGroup.SWORD || group==ItemGroup.MACE : group==ItemGroup.POLEARM || group==ItemGroup.GREATSWORD;
   case SCOUT,ASSASSIN->group==ItemGroup.DAGGER || group==ItemGroup.SWORD;
   case RANGER->group==ItemGroup.BOW;
   case MAGE,SORCERER,SPIRIT_MASTER->group==ItemGroup.ORB || group==ItemGroup.SPELLBOOK;
   case PRIEST,CLERIC->group==ItemGroup.MACE;case CHANTER->group==ItemGroup.STAFF;
   case ENGINEER,GUNNER->group==ItemGroup.GUN || group==ItemGroup.CANNON;
   case RIDER->group==ItemGroup.KEYBLADE;case ARTIST,BARD->group==ItemGroup.HARP;
  };
 }
 private PlayerBotBuildRules(){}
}
