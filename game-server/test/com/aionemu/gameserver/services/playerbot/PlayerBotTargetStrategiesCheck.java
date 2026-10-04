package com.aionemu.gameserver.services.playerbot;
import java.util.List;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
public final class PlayerBotTargetStrategiesCheck {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static PlayerBotTargetStrategies.Candidate c(int id,boolean range,double seconds,boolean current,boolean rune){return new PlayerBotTargetStrategies.Candidate(id,false,false,range,seconds,10,current,rune);}
 public static void main(String[] args) {
  check(PlayerBotTargetStrategies.interval(true,5)==12,"Five seconds starts efficient caster lifetime interval");
  check(PlayerBotTargetStrategies.interval(true,30)==12,"Thirty seconds ends efficient interval inclusively");
  check(PlayerBotTargetStrategies.interval(true,4.9)==11,"Dying target ranks below efficient lifetime");
  check(PlayerBotTargetStrategies.interval(true,31)==10,"Long target ranks below dying target");
  check(PlayerBotTargetStrategies.interval(false,5)==2,"Distance is reflected in original interval");
  check(PlayerBotTargetStrategies.select(List.of(c(1,true,3,true,false),c(2,true,8,false,false)),false)==2,"Caster avoids losing its cast on a dying target");
  check(PlayerBotTargetStrategies.select(List.of(c(1,true,20,true,false),c(2,true,8,false,false)),false)==2,"Efficient targets favor shortest lifetime");
  check(PlayerBotTargetStrategies.select(List.of(c(1,true,2,true,false),c(2,true,4,false,false)),false)==1,"Caster does not oscillate when all targets are dying");
  check(PlayerBotTargetStrategies.select(List.of(c(1,true,40,true,false),c(2,true,31,false,false)),false)==2,"Long targets favor smaller lifetime");
  check(PlayerBotTargetStrategies.select(List.of(c(1,true,40,false,true),c(2,true,8,true,false)),true)==1,"Assassin retains own native rune target");
  check(PlayerBotTargetStrategies.select(List.of(c(1,false,40,false,true),c(2,true,8,true,false)),true)==2,"Distant rune target does not override a reachable enemy");
  var command=new PlayerBotTargetStrategies.Candidate(3,true,false,false,40,30,false,false);
  check(PlayerBotTargetStrategies.select(List.of(c(1,true,8,true,false),command),false)==3,"Explicit owner command wins over automatic caster choice");
  check(PlayerBotTargetStrategies.select(List.of(c(1,true,8,true,false),command),true)==3,"Explicit command wins over rune retention");
  var assist=new PlayerBotTargetStrategies.Candidate(4,false,true,false,40,30,false,false);
  check(PlayerBotTargetStrategies.select(List.of(c(1,true,8,true,false),assist),false)==4,"Owner assist remains authoritative");
  check(PlayerBotTargetStrategies.select(List.of(c(2,true,8,false,false),c(1,true,8,false,false)),false)==1,"Equal targets are stable by native ID");
  check(PlayerBotTargetStrategies.select(List.of(),false)==0,"No target stays no target");
  check(PlayerBotTargetStrategies.caster(PlayerClass.SORCERER,Role.RANGED),"Native magical ranged role enables lifetime strategy");
  check(!PlayerBotTargetStrategies.caster(PlayerClass.SORCERER,Role.TANK),"Assigned tank preserves tank hate strategy");
  check(!PlayerBotTargetStrategies.caster(PlayerClass.RANGER,Role.RANGED),"Bow users keep ordinary target strategy");
  System.out.println("OK: "+checks+" pinned caster lifetime/combo-target interval and explicit-command comparisons");
 }
}
