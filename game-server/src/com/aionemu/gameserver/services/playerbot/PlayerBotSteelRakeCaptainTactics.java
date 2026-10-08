package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.Comparator;
import com.aionemu.gameserver.model.gameobjects.Npc;

/** Only already admitted enemies are considered; amplifier scenery is never a kill target. */
final class PlayerBotSteelRakeCaptainTactics {
 static boolean scenery(int id){return id==281180 || id>=281191 && id<=281194;}
 static int priority(int id){return switch(id){case 281187,281183->0;case 281188,281184->1;case 281181,281182,281185,281186->2;default->Integer.MAX_VALUE;};}
 static Npc adds(List<Npc> enemies){return enemies.stream().filter(n->priority(n.getNpcId())!=Integer.MAX_VALUE&&PlayerBotSteelRake.attackable(n)).min(Comparator.comparingInt((Npc n)->priority(n.getNpcId())).thenComparingInt(Npc::getObjectId)).orElse(null);}
 private PlayerBotSteelRakeCaptainTactics(){}
}
