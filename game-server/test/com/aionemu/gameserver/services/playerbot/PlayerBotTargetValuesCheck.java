package com.aionemu.gameserver.services.playerbot;
import java.util.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotTargetValues.Candidate;

public final class PlayerBotTargetValuesCheck {
 static int checks;
 static Candidate c(int id,boolean range,int interval,int hate,int hp,double distance){return new Candidate(id,false,false,range,interval,hate,hp,distance,Double.POSITIVE_INFINITY,false);}
 static void check(int expected,boolean tank,Candidate... candidates){checks++;int actual=PlayerBotTargetValues.select(List.of(candidates),tank);if(actual!=expected)throw new AssertionError("Expected "+expected+", got "+actual+" in case "+checks);}
 public static void main(String[] args) {
  check(0,false);check(0,true);
  check(2,false,c(1,true,0,0,900,1),c(2,true,0,0,100,4));
  check(1,false,c(1,true,0,0,900,4),c(2,false,0,0,100,6));
  check(2,false,c(1,false,0,0,100,20),c(2,false,0,0,900,10));
  check(1,false,new Candidate(1,true,false,false,0,0,900,30,Double.POSITIVE_INFINITY,false),c(2,true,0,0,100,1));
  check(2,false,c(1,true,0,0,100,1),new Candidate(2,false,true,false,0,0,900,30,Double.POSITIVE_INFINITY,false));
  check(1,false,new Candidate(1,true,false,false,0,0,900,30,Double.POSITIVE_INFINITY,false),new Candidate(2,false,true,true,0,0,100,1,Double.POSITIVE_INFINITY,false));
  check(2,true,c(1,true,1,1,100,1),c(2,false,2,100,900,20));
  check(1,true,c(1,true,1,900,100,1),c(2,false,0,1,900,10));
  check(2,true,c(1,true,1,900,100,1),c(2,true,1,100,900,4));
  check(1,true,c(1,false,2,900,100,4),c(2,false,2,1,900,10));
  check(2,true,new Candidate(1,true,true,true,-1,1,100,1,Double.POSITIVE_INFINITY,false),c(2,true,1,900,900,4));
  check(2,true,c(1,true,2,1,100,1),new Candidate(2,false,false,false,2,900,900,20,10,false));
  check(1,true,new Candidate(1,false,false,false,2,900,900,20,10,false),new Candidate(2,true,true,true,2,1,100,1,80,false));
  check(2,false,c(1,true,0,0,100,1),new Candidate(2,false,false,true,0,0,100,4,Double.POSITIVE_INFINITY,true));
  check(1,false,c(2,true,0,0,100,1),c(1,true,0,0,100,1));
  check(2,true,c(1,true,1,100,100,1),new Candidate(2,false,false,true,1,100,900,4,Double.POSITIVE_INFINITY,true));
  System.out.println("OK: "+checks+" role target selection cases: range/health, nearest reach, explicit assist, native-hate pickup, second-tank protection and stable ties");
 }
}
