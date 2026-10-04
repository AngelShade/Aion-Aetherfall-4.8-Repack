package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import sun.misc.Unsafe;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.knownlist.KnownList;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Continuous destination refresh regression and measured travel trajectories. */
public final class PlayerBotFormationCheck {
 static int checks;
 static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
 static void set(Object target,String name,Object value)throws Exception {for(Class<?> t=target.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static final class Bot extends Player {
  KnownList list;
  Bot(){super(null,null);}
  @Override public boolean canPerformMove(){return true;}
  @Override public boolean isCasting(){return false;}
  @Override public KnownList getKnownList(){return list;}
  @Override public float getX(){return 0;}
  @Override public float getY(){return 0;}
  @Override public float getZ(){return 0;}
  @Override public byte getHeading(){return 0;}
 }
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe u=(Unsafe)f.get(null);
  Bot bot=(Bot)u.allocateInstance(Bot.class);bot.list=new KnownList(bot);
  var controller=new PlayerBotMoveController(bot);set(bot,"moveController",controller);set(controller,"started",new AtomicBoolean(true));set(controller,"lastMoveUpdate",123456L);
  for(int i=0;i<100;i++){controller.setNewDirection(i+10,0,0,(byte)0);controller.startMovingToDestination();check(controller.getLastMoveUpdate()==123456L,"Destination refresh must not discard accumulated movement time");}
  for(Role role:Role.values())for(double heading:new double[]{0,Math.PI/2,Math.PI,Math.PI*2-.05}) {
   List<PlayerBotNavigation.Point> points=new ArrayList<>();for(int slot=0;slot<5;slot++)points.add(PlayerBotFormation.offset(100,100,10,heading,slot,role,false,6));
   for(int a=0;a<5;a++)for(int b=a+1;b<5;b++)check(Math.hypot(points.get(a).x()-points.get(b).x(),points.get(a).y()-points.get(b).y())>2.5,"Stable separate formation slots");
  }
  check(Math.abs(PlayerBotFormation.turn(Math.PI*2-.05,.05,.1)-(Math.PI*2+.05))<.001,"Heading wrap takes the short turn");
  check(Math.abs(PlayerBotFormation.turn(0,Math.PI,.1))<=.211,"Sudden reverse is damped");
  for(float speed:new float[]{6,9,12})for(float nativeSpeed:new float[]{4,6}) {
   double x=-12,y=0,angle=0,maxGap=0;int pauses=0;
   for(int tick=0;tick<1000;tick++) {
    double t=tick*.2,leaderX=t<60 ? t*speed : 60*speed,leaderY=t<60 ? 0 : (t-60)*speed;
    angle=PlayerBotFormation.turn(angle,t<60?0:Math.PI/2,.2);
    var goal=PlayerBotFormation.offset((float)leaderX,(float)leaderY,0,angle,0,Role.RANGED,true,speed);
    double d=Math.hypot(goal.x()-x,goal.y()-y),step=Math.min(d,nativeSpeed*PlayerBotFormation.speed(nativeSpeed,speed,Math.hypot(leaderX-x,leaderY-y),true)*.2);
    if(tick>100){maxGap=Math.max(maxGap,Math.hypot(leaderX-x,leaderY-y));if(step<.01)pauses++;}
    if(d>0){x+=(goal.x()-x)*step/d;y+=(goal.y()-y)*step/d;}
   }
   check(pauses==0,"Travel remains continuous with a faster owner");check(maxGap<17,"Faster owner does not leave companions permanently behind");
   System.out.println("TRAJECTORY: leader="+speed+" bot="+nativeSpeed+" stop ticks="+pauses+" max gap="+Math.round(maxGap*100)/100.0);
  }
  System.out.println("OK: "+checks+" movement-clock, formation separation and measured trajectory checks; geodata/client interpolation need live acceptance");
 }
}
