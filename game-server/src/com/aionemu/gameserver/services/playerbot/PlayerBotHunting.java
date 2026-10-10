/* GrindTargetValue::FindTargetForGrinding (XP targets) and AttackersValue,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; AUTHORS.md. Training classification is native NoActionAI. */
package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.TribeClass;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;

/** Opportunistic hunting eligibility, also used for population field level bands. */
final class PlayerBotHunting {
 static boolean training(TribeClass tribe) {
  if(tribe==null)return false;
  return switch(tribe) {
   case TARGETBASFELT_DF1,TARGETBASFELT2_DF1,DUMMY,DUMMY2,LF5_DUMMY1,LF5_DUMMY2,DF5_DUMMY1,DF5_DUMMY2 -> true;
   default -> false;
  };
 }
 static boolean template(NpcTemplate npc){return npc!=null && !training(npc.getTribe()) && npc.getNpcTemplateType()!=com.aionemu.gameserver.model.templates.npc.NpcTemplateType.FLAG
  && npc.getLevel()>0 && npc.getLevel()<=69 && (npc.getRating()==NpcRating.NORMAL || npc.getRating()==NpcRating.JUNK);}
 static boolean opportunistic(PlayerBotWorldActivities.Goal goal,int npc) {
  return goal==null || goal.kind()==PlayerBotQuestRoutes.Kind.HUNT && (goal.quest()==0 || goal.place().npc()==npc);
 }
 private PlayerBotHunting(){}
}
