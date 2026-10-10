package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.*;

/** Explicit world residency, separate from party control and native character progress. */
final class PlayerBotWorldRoster {
 record Resident(int account, int character, int map, float x, float y, float z, byte heading) {
  Resident {
   if(!PlayerBotPopulationPolicy.account(account) || character<=0 || map<=0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
    throw new IllegalArgumentException("Invalid world bot residence");
  }
  PlayerBotPersistence.Home home(){return new PlayerBotPersistence.Home(map,x,y,z,heading,0);}
 }
 private final PlayerBotPopulationStorage repository;
 PlayerBotWorldRoster(PlayerBotPopulationStorage repository){this.repository=repository;}
 /** Binary compatibility only: the former file argument no longer selects a file backend. */
 PlayerBotWorldRoster(java.nio.file.Path path){this(new PlayerBotPopulationRepository());if(!path.getFileName().toString().equals("world-bots.properties"))throw new IllegalArgumentException("Use native population storage");}
 Map<Integer,Resident> load()throws IOException {
  return repository.residents();
 }
 void save(Map<Integer,Resident> residents)throws IOException {
  repository.residents(residents);
 }
}
