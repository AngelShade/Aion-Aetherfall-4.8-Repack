package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

import java.nio.file.Files;
import java.nio.file.Path;

/** Owner 8 Nov: PvP bots stood still and went defensive. Fight craft by combat rank: movement, cast reading, defense,
 * control and burst timing, and its wiring into the faction fight. No World/SQL. */
public final class PlayerBotPvpTacticsCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static boolean near(double a,double b){return Math.abs(a-b)<1e-6;}

 public static void main(String[] args) throws Exception {
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  wits();
  reading();
  defense();
  control();
  damage();
  movement();
  wiring(root);
  System.out.println("OK: "+checks+" PvP tactics checks; no World/SQL/tasks");
 }
 static void wits() {
  check(PlayerBotPvpTactics.wits(30)==0 && PlayerBotPvpTactics.wits(20)==1 && PlayerBotPvpTactics.wits(10)==2 && PlayerBotPvpTactics.wits(4)==3 && PlayerBotPvpTactics.wits(0)==4,
   "Wits follow the combat rank's mistake chance");
  boolean all=true;for(var rank:PlayerBotGrowth.CombatRank.values())all&=PlayerBotPvpTactics.wits(rank.mistakePercent())==rank.ordinal();
  check(all,"Every combat rank maps to its own wits, POOR 0 to EXCELLENT 4");
 }
 static void reading() {
  check(PlayerBotPvpTactics.interruptNow(4,1000,2000,1200,1,2),"An excellent bot answers a 2 s cast after 180 ms");
  check(!PlayerBotPvpTactics.interruptNow(4,1000,2000,1100,1,2),"Nobody answers before reacting");
  check(!PlayerBotPvpTactics.interruptNow(4,1000,2000,3000,1,2),"A finished cast is no longer answered");
  check(!PlayerBotPvpTactics.interruptNow(4,1000,400,1300,1,2),"A cast under half a second cannot be read");
  check(!PlayerBotPvpTactics.interruptNow(0,1000,3000,2000,1,2) && PlayerBotPvpTactics.interruptNow(4,1000,3000,2000,1,2),"A poor bot reacts later than an excellent one");
  int poor=0,excellent=0;
  for(int i=1;i<=2000;i++){long start=i*7919L;if(PlayerBotPvpTactics.interruptNow(0,start,3000,start+1500,77,i))poor++;if(PlayerBotPvpTactics.interruptNow(4,start,3000,start+1500,77,i))excellent++;}
  check(poor>500 && poor<900,"A poor bot notices about a third of the casts: "+poor+"/2000");
  check(excellent==2000,"An excellent bot notices every cast");
 }
 static void defense() {
  check(PlayerBotPvpTactics.defense(4,20,false,false)==0,"No pressure, no defensive skill for the skilled");
  check(PlayerBotPvpTactics.defense(4,25,true,false)==ENCOUNTER,"Under pressure and low, the excellent defend");
  check(PlayerBotPvpTactics.defense(4,33,true,false)==0,"The excellent hold it until 30%");
  check(PlayerBotPvpTactics.defense(3,60,true,true)==ENCOUNTER+5,"An incoming burst is met early");
  check(PlayerBotPvpTactics.defense(0,55,false,false)==ENCOUNTER,"A poor bot panics early, pressure or not");
  check(PlayerBotPvpTactics.defense(4,0,true,true)==0,"A dead bot defends nothing");
 }
 static void control() {
  check(PlayerBotPvpTactics.control(2,38,true,false,false,true,false,false,false,false,false)==0,"No stun on a stunned target");
  check(PlayerBotPvpTactics.control(0,38,true,false,false,true,false,false,false,false,false)==38,"A poor bot still overwrites it");
  check(PlayerBotPvpTactics.control(3,38,true,true,true,false,false,false,false,false,false)==INTERRUPT+8,"A read cast is interrupted");
  check(PlayerBotPvpTactics.control(3,40,true,true,false,false,false,false,false,false,false)==0,"The skilled wait for their read");
  check(PlayerBotPvpTactics.control(0,40,true,true,false,false,false,false,false,false,false)==NORMAL,"A poor bot that did not see the cast carries on");
  check(PlayerBotPvpTactics.control(4,38,true,false,false,false,false,false,false,false,false)==NORMAL,"An interrupt is kept for a cast");
  check(PlayerBotPvpTactics.control(4,38,true,false,false,false,false,false,true,false,false)==38,"It is spent on a kill");
  check(PlayerBotPvpTactics.control(4,38,true,false,false,false,false,false,false,true,false)==38,"Or a rescue");
  check(PlayerBotPvpTactics.control(2,38,false,false,false,false,true,true,false,false,false)==0,"No second root on a rooted target");
  check(PlayerBotPvpTactics.control(2,20,false,false,false,false,true,false,false,false,true)==INTERRUPT-4,"A fleeing target is snared");
  check(PlayerBotPvpTactics.control(4,0,true,true,true,false,false,false,false,false,false)==0,"An unusable skill stays unusable");
 }
 static void damage() {
  check(PlayerBotPvpTactics.damage(4,20,false,false,false,true,false,false,false,false,false)==32,"Burst when it matters");
  check(PlayerBotPvpTactics.damage(3,20,false,false,false,false,true,false,false,false,false)==DEFAULT,"A big cooldown waits for the window");
  check(PlayerBotPvpTactics.damage(3,20,false,false,false,false,true,true,false,false,false)==20,"A desperate bot spends it");
  check(PlayerBotPvpTactics.damage(1,20,false,false,false,false,true,false,false,false,false)==20,"A plain bot spends it at once");
  check(PlayerBotPvpTactics.damage(4,20,false,false,false,false,false,false,true,false,false)==5,"No attacks into Evasion or a resist charge");
  check(PlayerBotPvpTactics.damage(0,45,true,true,false,false,false,false,false,false,false)==HIGH+5,"An unread cast earns no interrupt bonus");
  check(PlayerBotPvpTactics.damage(2,20,true,true,true,false,false,false,false,false,false)==INTERRUPT+6,"A read cast is answered with a stun attack");
  check(PlayerBotPvpTactics.damage(2,20,false,false,false,false,false,false,false,true,true)==10,"A cast that plants the feet is worth less while kited");
  check(PlayerBotPvpTactics.window(30,false,false) && !PlayerBotPvpTactics.window(80,false,false) && PlayerBotPvpTactics.window(80,true,false) && PlayerBotPvpTactics.window(80,false,true),
   "The window: low, held or healing");
  check(PlayerBotPvpTactics.bigCooldown(300) && !PlayerBotPvpTactics.bigCooldown(299),"Big cooldowns start at 30 s (tenths)");
 }
 static void movement() {
  check(PlayerBotPvpTactics.kiteDistance(0)==0 && PlayerBotPvpTactics.kiteDistance(2)==5 && PlayerBotPvpTactics.kiteDistance(4)==8,"Kiting grows with wits; a poor bot stands");
  check(PlayerBotPvpTactics.kitePriority(3)>INTERRUPT && PlayerBotPvpTactics.kitePriority(3)<INTERRUPT+8,"Kiting beats rotation, not a read interrupt");
  check(PlayerBotPvpTactics.strafeEvery(2)==0 && PlayerBotPvpTactics.strafeEvery(3)==4500 && PlayerBotPvpTactics.strafeEvery(4)==3000,"Only skilled melee circle");
  check(PlayerBotPvpTactics.STRAFE_PRIORITY>HIGH+5 && PlayerBotPvpTactics.STRAFE_PRIORITY<INTERRUPT-2,"A flank step beats a routine attack, not a control");
  check(PlayerBotPvpTactics.kiteStep(2,9)==7 && PlayerBotPvpTactics.kiteStep(6,7)==4 && PlayerBotPvpTactics.kiteStep(10,7)==3,"Kite steps of 3 to 7 m");
  check(PlayerBotPvpTactics.keepsMoving(0,false) && !PlayerBotPvpTactics.keepsMoving(800,false) && !PlayerBotPvpTactics.keepsMoving(0,true),"Instants keep the stride, casts and charges do not");
  var away=PlayerBotPvpTactics.away(10,0,0,0,(byte)0,5);
  check(near(away[0],15) && near(away[1],0),"A kite step leads away from the enemy");
  var flank=PlayerBotPvpTactics.flank(5,0,0,0,5,true);
  check(Math.abs(Math.hypot(flank[0],flank[1])-5)<1e-3 && flank[1]>4,"A flank step keeps the reach and turns about 70 degrees");
 }
 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var s=Files.readString(src.resolve("PlayerBotSession.java")).replace("\r\n","\n");
  check(s.contains("int wits=PlayerBotPvpTactics.wits(mistakePercent());"),"Wits come from the bot's rank");
  check(s.contains("var read=PlayerBotPvpTactics.read(bot,target,wits,reach,rangedRole,now);"),"The fight is read once per decision");
  check(s.contains("value=pvpTactic(entry,actual,kind,recipient,read,value);"),"Every faction-fight skill passes the tactics");
  check(s.contains("case DEFENSE -> recipient==bot && value>0 ? PlayerBotPvpTactics.defense("),"Own defense is timed, ally shields keep their rule");
  check(s.contains(".onTheMove(PlayerBotPvpTactics.keepsMoving(entry.template().getDuration(),entry.template().isCharge()))"),"Faction-fight instants are cast on the move");
  check(s.contains("if(onTheMove && bot.getMoveController().isInMove()){/* keep the stride */}"),"An instant no longer stops the bot");
  check(s.split("\\.onTheMove\\(",-1).length==2,"Only the faction fight casts on the move; PvE keeps its stops");
  check(s.contains("()->kite(target,wits)),PlayerBotPvpTactics.kitePriority(wits)));"),"Ranged bots kite melee");
  check(s.contains("()->strafe(target,wits,now)),PlayerBotPvpTactics.STRAFE_PRIORITY));"),"Skilled melee circle");
  check(s.contains("if(tactic != null) PlayerBotPvpStats.tactic(tactic);".replace("if(","if (")),"Tactics used are counted");
  check(Files.readString(src.resolve("PlayerBotPvpStats.java")).contains(", tactics=\"+tactics();"),"And reported");
 }
 private PlayerBotPvpTacticsCheck(){}
}
