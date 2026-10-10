/* RandomPlayerbotMgr::ProcessBot and RandomPlayerbotFactory, pinned mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; see third-party/playerbots/AUTHORS.md.
 * Native Aion account/roster/lease/character persistence replaces WoW login sessions. */
package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.*;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;

/** A deliberately provisioned hidden account pool, maintained without an online human. */
final class PlayerBotWorldPopulation {
 record Pool(int account,String name,byte membership,long created,Race race,Gender gender,
             PlayerAppearance appearance,int desired,List<Integer> characters) {
  Pool {
   if(!PlayerBotPopulationPolicy.account(account) || name==null || name.isBlank() || race==null || gender==null || appearance==null
    || desired<0 || desired>100 || characters==null || characters.size()>100 || characters.stream().anyMatch(id->id<=0)
    || characters.stream().distinct().count()!=characters.size())throw new IllegalArgumentException("Invalid world population pool");
   characters=List.copyOf(characters);appearance=appearance.copy();
  }
  Pool desired(int n){return new Pool(account,name,membership,created,race,gender,appearance,n,characters);}
  Pool append(int id){var ids=new ArrayList<>(characters);ids.add(id);return new Pool(account,name,membership,created,race,gender,appearance,desired,ids);}
 }
 private final PlayerBotPopulationStorage repository;
 private final boolean server;
 PlayerBotWorldPopulation(PlayerBotPopulationStorage repository,boolean server){this.repository=repository;this.server=server;}
 /** Binary compatibility; the legacy name maps scope only, never filesystem storage. */
 PlayerBotWorldPopulation(java.nio.file.Path path){this(new PlayerBotPopulationRepository(),path.getFileName().toString().equals("world-server-population.json"));if(!Set.of("world-server-population.json","world-population.json").contains(path.getFileName().toString()))throw new IllegalArgumentException("Use native population storage");}
 synchronized Map<Integer,Pool> load()throws IOException {
  return repository.pools(server);
 }
 synchronized void put(Pool pool)throws IOException {
  repository.pool(server,pool);
 }
 synchronized void configure(Player owner,int desired,int maximum)throws IOException {
  if(desired<0 || desired>maximum)throw new IllegalArgumentException("World population must be between 0 and "+maximum+".");
  var existing=load().get(owner.getAccount().getId());
  if(existing!=null){put(existing.desired(desired));return;}
  var a=owner.getAccount();put(new Pool(a.getId(),a.getName(),a.getMembership(),a.getCreationDate(),owner.getRace(),owner.getGender(),owner.getPlayerAppearance(),desired,List.of()));
 }
 static PlayerClass playerClass(int index) {
  var classes=Arrays.stream(PlayerClass.values()).filter(c->!c.isStartingClass()).toList();
  return classes.get(Math.floorMod(index,classes.size()));
 }
 static PlayerClass planned(Pool pool,int id) {
  if(!PlayerBotPopulationPolicy.serverAccount(pool.account()))return playerClass(id);
  int index=pool.characters().indexOf(id);if(index<0)throw new IllegalArgumentException("Character not in server pool");
  int shard=(-pool.account()-1000000)/2;
  return playerClass(shard*pool.desired()+index);
 }
 static String name(int id) {
  return PlayerBotNames.candidate(id,Gender.MALE,0);
 }
}
