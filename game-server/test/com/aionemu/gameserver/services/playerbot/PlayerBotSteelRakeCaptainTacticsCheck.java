package com.aionemu.gameserver.services.playerbot;

public final class PlayerBotSteelRakeCaptainTacticsCheck {
 public static void main(String[] args){
  int checks=0;
  for(int id:new int[]{281180,281191,281192,281193,281194}){if(!PlayerBotSteelRakeCaptainTactics.scenery(id))throw new AssertionError("scenery "+id);checks++;}
  for(int id=281181;id<=281188;id++){if(PlayerBotSteelRakeCaptainTactics.scenery(id)||PlayerBotSteelRakeCaptainTactics.priority(id)==Integer.MAX_VALUE)throw new AssertionError("add "+id);checks++;}
  if(PlayerBotSteelRakeCaptainTactics.priority(281187)>=PlayerBotSteelRakeCaptainTactics.priority(281185))throw new AssertionError("shaman first");checks++;
  if(PlayerBotSteelRakeCaptainTactics.priority(281188)>=PlayerBotSteelRakeCaptainTactics.priority(281186))throw new AssertionError("healer before melee");checks++;
  if(PlayerBotSteelRakeCaptainTactics.priority(215081)!=Integer.MAX_VALUE || PlayerBotSteelRakeCaptainTactics.scenery(215081))throw new AssertionError("boss separate");checks++;
  System.out.println("OK: "+checks+" captain tactic checks");
 }
}
