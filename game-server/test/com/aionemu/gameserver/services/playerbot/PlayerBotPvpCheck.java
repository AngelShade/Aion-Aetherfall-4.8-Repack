package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.services.playerbot.PlayerBotLeanings.Leaning;

/** PvP-1: leanings, shared-map enemies, rift invaders and seeking. Pure rules plus wiring; no World/SQL/tasks. */
public final class PlayerBotPvpCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static boolean refused(Runnable r){try{r.run();return false;}catch(IllegalArgumentException expected){return true;}}

 public static void main(String[] args) throws Exception {
  leanings();
  seeking();
  landings();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" PvP checks; no World/SQL/tasks");
 }

 static void leanings() {
  var weights=PlayerBotLeanings.weights(PlayerBotConfig.PVP_LEANING_WEIGHTS);
  check(weights.keySet().equals(EnumSet.allOf(Leaning.class)) && weights.get(Leaning.PVP)>0,"The shipped weights name every leaning and include PVP");
  check(refused(()->PlayerBotLeanings.weights("PVP:1,PVP:2")),"A leaning listed twice is refused");
  check(refused(()->PlayerBotLeanings.weights("PIRATE:5")) && refused(()->PlayerBotLeanings.weights("PVP:x")),"Unknown names and non-numbers are refused");
  check(refused(()->PlayerBotLeanings.weights("PVP:-1")) && refused(()->PlayerBotLeanings.weights("PVP:0,QUESTER:0")) && refused(()->PlayerBotLeanings.weights(" ")),
   "Negative, all-zero and empty weights are refused");
  var counts=new EnumMap<Leaning,Integer>(Leaning.class);
  for(int id=1;id<=20000;id++)counts.merge(PlayerBotLeanings.choose(weights,id),1,Integer::sum);
  int total=weights.values().stream().mapToInt(Integer::intValue).sum();
  for(var e:weights.entrySet()) {
   double expected=20000.0*e.getValue()/total,got=counts.getOrDefault(e.getKey(),0);
   check(Math.abs(got-expected)<=expected*0.15+50,"Leanings follow their weights: "+e.getKey()+" "+got+" vs "+expected);
  }
  check(PlayerBotLeanings.choose(weights,4242)==PlayerBotLeanings.choose(weights,4242),"A character always gets the same leaning");
  var onlyPvp=PlayerBotLeanings.weights("QUESTER:0,PVP:5");boolean all=true;for(int id=1;id<500;id++)all&=PlayerBotLeanings.choose(onlyPvp,id)==Leaning.PVP;
  check(all,"A zero weight is never chosen");
  var good=Map.of("version","1","account","5","character","7","leaning","PVP");
  PlayerBotLeanings.validate(good);check(true,"A complete saved leaning validates");
  check(refused(()->PlayerBotLeanings.validate(Map.of("version","2","account","5","character","7","leaning","PVP")))
   && refused(()->PlayerBotLeanings.validate(Map.of("version","1","account","5","character","7","leaning","PIRATE")))
   && refused(()->PlayerBotLeanings.validate(Map.of("version","1","account","5","character","7"))),"Wrong version, unknown leaning or missing field is refused");
  PlayerBotLeanings.validateUpdate(Map.of(),good);PlayerBotLeanings.validateUpdate(good,good);check(true,"A first save and an identical save are allowed");
  check(refused(()->PlayerBotLeanings.validateUpdate(good,Map.of("version","1","account","5","character","7","leaning","QUESTER"))),"A saved leaning never changes");
  PlayerBotConfig.validatePvp();check(true,"The shipped PvP settings pass their own range checks");
 }

 static void seeking() {
  check(PlayerBotPvpSeek.wanted(true,true,true,false,false,true,false,false),"A PVP-leaning bot on a shared map with a human seeks");
  check(PlayerBotPvpSeek.wanted(true,false,false,false,true,true,false,false),"Any rift visitor seeks in the enemy's lands");
  check(PlayerBotPvpSeek.wanted(true,true,false,true,false,true,false,false) && !PlayerBotPvpSeek.wanted(true,false,false,true,false,true,false,false),
   "PvP-2: PVP bots defend their homeland against invaders; ordinary bots only defend when attacked");
  check(!PlayerBotPvpSeek.wanted(true,true,true,false,false,false,false,false),"No human on the map: the map sleeps, nothing to seek");
  check(!PlayerBotPvpSeek.wanted(false,true,true,true,true,true,false,false),"Companions follow their owner");
  check(!PlayerBotPvpSeek.wanted(true,true,true,true,true,true,true,false) && !PlayerBotPvpSeek.wanted(true,true,true,true,true,true,false,true),"Not in instances or parties");
  long now=1_000_000L;var claims=new HashMap<Integer,Long>(Map.of(1,now-1000,2,now-PlayerBotPvpSeek.GIVE_UP_MS));
  check(PlayerBotPvpSeek.seekers(claims,now)==1 && !claims.containsKey(2),"A claim older than the give-up time expires, so an unloaded seeker never blocks a target");
  check(PlayerBotPvpSeek.seekers(null,now)==0,"No claims, no seekers");
  check(PlayerBotPvpSeek.GIVE_UP_MS>=60000 && PlayerBotPvpSeek.MOVED>=20 && PlayerBotPvpSeek.GUARD_RADIUS>=40,"Seeking gives up, re-plans rarely and keeps clear of guards");
 }

 static PlayerBotWorldPlacement.Location camp(Race race,int map,float x,int min,int max,boolean city){return new PlayerBotWorldPlacement.Location(race,map,x,0,0,min,max,city,city);}
 static void landings() {
  check(PlayerBotPvpPresence.enemy(Race.ELYOS)==Race.ASMODIANS && PlayerBotPvpPresence.enemy(Race.ASMODIANS)==Race.ELYOS && PlayerBotPvpPresence.enemy(Race.NONE)==Race.NONE,"Each faction's enemy");
  check(PlayerBotPvpPresence.levelFits(40,45,5) && !PlayerBotPvpPresence.levelFits(40,46,5),"Enemies are within the configured level range");
  int map=400010000;var A=Race.ASMODIANS;
  var camps=List.of(camp(A,map,100,30,50,false),camp(A,map,350,30,50,true),camp(A,map,420,30,50,false),camp(A,map,500,30,50,false),
   camp(A,map,450,51,60,false),camp(A,210020000,320,30,50,false),camp(A,map,1500,30,50,false),camp(Race.ELYOS,map,330,30,50,false));
  var chosen=PlayerBotPvpPresence.landing(camps,A,40,map,0,0,0);
  check(chosen!=null && chosen.x()==420,"The nearest own-faction field camp at least 300 m away is chosen: "+chosen);
  check(PlayerBotPvpPresence.landing(camps,A,55,map,0,0,0).x()==450,"Only camps that fit the bot's level");
  check(PlayerBotPvpPresence.landing(List.of(camp(A,map,100,30,50,false),camp(A,map,1500,30,50,false)),A,40,map,0,0,0)==null,"Nothing between 300 m and the seek distance, no landing");
  check(PlayerBotPvpPresence.MIN_LANDING>=PlayerBotConfig.POPULATION_UNSEEN_DISTANCE,"Landings are never closer than the unseen distance");
  check(PlayerBotPvpPresence.INVITE_MS<PlayerBotPvpPresence.REST_MS && PlayerBotPvpPresence.PLAN_MS<=PlayerBotPvpPresence.INVITE_MS,"Invites expire quickly and a bot rests long between trips");
 }

 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var pvp=Files.readString(src.resolve("PlayerBotPvp.java"));
  var rifts=Files.readString(src.resolve("PlayerBotRifts.java"));
  var presence=Files.readString(src.resolve("PlayerBotPvpPresence.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var manager=Files.readString(src.resolve("PlayerBotPopulationManager.java"));
  var metadata=Files.readString(src.resolve("PlayerBotMetadata.java"));
  var offscreen=Files.readString(src.resolve("PlayerBotOffscreenTurnIns.java"));
  var pvpService=Files.readString(root.resolve("src/com/aionemu/gameserver/services/PvpService.java"));
  var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(pvpService.contains("com.aionemu.gameserver.services.playerbot.PlayerBotPvpStats.killed(winner, victim);"),"Kills are counted where the native kill credit is decided");
  check(pvp.contains("(PlayerBotLeanings.pvp(bot) || session.autonomous() && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotPvpSeek.battleground(bot)?100:PlayerBotConfig.PVP_AGGRESSION)") && pvp.contains("PlayerBotPvpStats.engaged(bestAnswer);"),
   "A PVP bot that walked to a fight starts it; engagements are counted");
  check(session.contains("|| PlayerBotPvpPresence.due(this);}") && session.contains("if(worldDormant)PlayerBotPvpPresence.turn(this,System.currentTimeMillis());"),"An invited sleeping bot wakes for its own turn");
  check(session.contains("&& pvp.seek.tick(this,navigation,now)) {") && session.contains("status=\"seeking enemy players\""),"Seeking runs after the rift trip behind the same safety guards");
  check(rifts.contains("boolean invite(Player bot,Npc rift,long now)") && rifts.contains("boolean crossed=rift.enterPlayerBot(bot);"),"Invaders cross through the unchanged native rift entry");
  check(rifts.contains("PlayerBotPvpStats.arrived(bot,exit,now);"),"PvP-2: a wave out of a rift alerts enemy humans nearby");
  check(presence.contains("s.dormant()") && presence.contains("PlayerBotWorldActivity.occupiedMap(source)"),"Only sleeping bots are invited, and never staged where a human could see");
  check(presence.contains("PlayerBotWorldPlacement.placeAt(session,landing)") && presence.contains("PlayerBotOffscreenTurnIns.moveBeside(session,rift)"),"Every move uses the normal placement guards");
  check(offscreen.contains("session.standUp();") && presence.contains("session.standUp();"),"A bot that fell asleep sitting stands before an unseen move");
  check(metadata.contains("\"focus\",\"leaning\").contains(section)") && metadata.contains("PlayerBotLeanings.validate(map);") && metadata.contains("PlayerBotLeanings.validateUpdate(e.current,map);"),
   "The leaning section is validated and immutable like trade and growth");
  check(service.contains("PlayerBotLeanings.initialize(session);") && service.contains("PlayerBotPvpPresence.plan(sessions.values(),System.currentTimeMillis());") && service.contains("lines.add(\"PvP: \"+PlayerBotPvpStats.stats()"),
   "Leanings load with the resident, the planner runs with presence maintenance, and the report shows PvP");
  check(manager.contains("PlayerBotLeanings.closed(id);PlayerBotPvpPresence.closed(id);"),"A closed bot's leaning and invites are dropped");
  for(var key:List.of("rifts.invaders_per_rift","leaning_weights","leaning_min_level","shared_enemies_per_human","shared_level_range","seek_distance","victory_emote_percent"))
   check(config.contains("gameserver.playerbots.pvp."+key+" = "),"Documented setting "+key);
 }
 private PlayerBotPvpCheck(){}
}
