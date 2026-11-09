package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owner, 9 Nov 2026: travelling bots stepped a few feet in every direction, bounced back, and stopped and started
 * instead of walking. Guards the three causes so a later change cannot bring them back:
 * the pacing watch, sideways hops on flat ground, and the stop at the end of every short step.
 */
public final class PlayerBotSmoothTravelCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  pacing();
  hops();
  handover();
  lostBots();
  traceBudget();
  journeys();
  starterCamps();
  hiringReasons();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" smooth travel checks; no World/SQL/tasks");
 }

 /** Replays a track: one position every 250 ms; returns true if the watch fired. */
 static boolean replay(float[][] track,double startGoal,double goalStep) {
  var watch=new PlayerBotMotionWatch();boolean fired=false;long now=1_000_000L;double goal=startGoal;
  for(var p:track){fired|=watch.observe(now,220030000,p[0],p[1],goal);now+=250;goal-=goalStep;}
  return fired;
 }
 static float[][] bounce(int samples,float amplitude) {
  var track=new float[samples][];
  for(int i=0;i<samples;i++){int phase=i%8;float offset=phase<4?phase*amplitude/4:(8-phase)*amplitude/4;track[i]=new float[]{100+offset,200};}
  return track;
 }
 static void pacing() {
  check(replay(bounce(40,3),50,0),"Stepping 3 m forward and back for 10 s with no goal progress is pacing");
  float[][] zigzag=new float[40][];
  for(int i=0;i<40;i++)zigzag[i]=new float[]{100+(i%2==0?0:1.5f)*(i%4<2?1:-1),200+(i%4<2?0:1.5f)};
  check(replay(zigzag,50,0),"Hopping in every direction around one spot is pacing");
  float[][] straight=new float[40][];
  for(int i=0;i<40;i++)straight[i]=new float[]{100+i*1.5f,200};
  check(!replay(straight,80,1.5),"A bot walking straight on is never pacing");
  float[][] standing=new float[40][];
  for(int i=0;i<40;i++)standing[i]=new float[]{100+(i%2)*.1f,200};
  check(!replay(standing,50,0),"Standing still with tiny jitter is not pacing (idle is handled elsewhere)");
  float[][] uturn=new float[40][];
  for(int i=0;i<40;i++)uturn[i]=new float[]{100+(i<20?i*.3f:(40-i)*.3f),200+(i<20?0:2)};
  check(!replay(uturn,50,0),"One U-turn round a wall is a single reversal, not pacing");
  check(!replay(bounce(40,3),50,.3),"Back and forth that still gains on the goal (switchbacks) is not pacing");
  check(!replay(bounce(16,3),50,0),"Under the watch window nothing is decided");
  var watch=new PlayerBotMotionWatch();long now=0;int fires=0;
  for(var p:bounce(80,3)){if(watch.observe(now,1,p[0],p[1],50))fires++;now+=250;}
  check(fires>=2 && fires<=3,"After firing the watch starts a fresh window instead of firing every turn: "+fires);
  watch=new PlayerBotMotionWatch();now=0;boolean fired=false;
  var track=bounce(40,3);for(int i=0;i<track.length;i++){fired|=watch.observe(now,i<20?1:2,track[i][0],track[i][1],50);now+=250;}
  check(!fired,"Changing map clears the history");
 }

 static void hops() {
  // usefulHop(offset, startZ, endZ, distance to goal before, distance after)
  check(PlayerBotNavigation.usefulHop(0,10,10.2f,20,18),"A flat jump straight at the goal (a gap) still counts");
  check(PlayerBotNavigation.usefulHop(30,10,10,20,18.5),"A flat jump slightly to the side that still gains 1.5 m counts");
  check(!PlayerBotNavigation.usefulHop(60,10,10,20,19.5),"A flat sideways hop that gains half a metre is a sidestep, not a jump");
  check(!PlayerBotNavigation.usefulHop(90,10,10.3f,20,19.4),"A flat 90 degree hop is never a jump");
  check(!PlayerBotNavigation.usefulHop(180,10,10,20,19),"A flat backwards hop is never a jump");
  check(!PlayerBotNavigation.usefulHop(0,10,10,20,19.6),"A flat jump must gain at least a metre");
  check(PlayerBotNavigation.usefulHop(60,10,6,20,19.5),"A real drop to the side is still allowed (descending to a lower goal)");
  check(PlayerBotNavigation.usefulHop(135,10,5,20,21),"A real drop backwards is still allowed when the goal is below");
  check(PlayerBotNavigation.usefulHop(30,10,11.5f,20,19.5),"A real climb onto a ledge is still allowed");
 }

 static void handover() {
  check(PlayerBotNavigation.handoverMetres(6)>=3 && PlayerBotNavigation.handoverMetres(6)<=4,"At running speed the next step is planned 3-4 m before the current one ends");
  check(PlayerBotNavigation.handoverMetres(0)>=1.5,"Even slowed, at least 1.5 m of the step remains when the next is planned");
  check(PlayerBotNavigation.handoverMetres(30)<=4,"Fast travel never replans a whole step early");
  check(PlayerBotNavigation.handoverDue(false,0,10,3,6),"Three metres before the end of a short step the next one is planned while still walking");
  check(!PlayerBotNavigation.handoverDue(false,0,10,8,6),"Far from the end of the step the current walk is kept");
  check(!PlayerBotNavigation.handoverDue(false,0,.5,3,6),"At the final destination the bot simply arrives");
  check(!PlayerBotNavigation.handoverDue(true,5,10,3,6),"A long corridor with more corners keeps walking without replanning at each corner");
  check(PlayerBotNavigation.handoverDue(true,1,10,3,6),"The last corner of a corridor hands over like a short step");
 }

 static void starterCamps() {
  check(PlayerBotWorldPlacement.starterMaximum(16)==9,"A Poeta camp for level-6 mobs no longer takes ascended level 10-16 bots");
  check(PlayerBotWorldPlacement.starterMaximum(5)==5,"Low starter camps keep their own limit");
  check(PlayerBotWorldPlacement.starterMaximum(9)==9,"Level 9 bots still finish their Ascension in the starting zone");
 }

 static void journeys() {
  var npcSpot=new PlayerBotNavigation.Point(1156.5f,1054.25f,131.97f);var npcLive=new PlayerBotNavigation.Point(1159,1052,132);
  var T=PlayerBotNavigation.Intent.TRAVEL;var C=PlayerBotNavigation.Intent.TRACK;
  check(PlayerBotNavigation.sameJourney(T,C,npcSpot,npcLive,10),"Walking to an NPC's place and stepping up to the NPC itself is one journey (Selorina, 9 Nov)");
  check(PlayerBotNavigation.sameJourney(C,T,npcLive,npcSpot,10),"The same holds when the quest step went first");
  check(!PlayerBotNavigation.sameJourney(T,C,npcSpot,npcLive,2),"In the last few metres the newest request is followed exactly, so the bot still reaches the NPC");
  check(!PlayerBotNavigation.sameJourney(T,C,npcSpot,new PlayerBotNavigation.Point(1180,1054,132),30),"A request 20 m away is a different destination");
  check(!PlayerBotNavigation.sameJourney(T,C,npcSpot,new PlayerBotNavigation.Point(1157,1054,140),30),"Another floor is a different destination");
  check(!PlayerBotNavigation.sameJourney(T,T,npcSpot,npcLive,10),"One walk changing its goal is a real new goal");
  check(!PlayerBotNavigation.sameJourney(C,C,npcSpot,npcLive,10),"A chase keeps its own target tracking");
  check(!PlayerBotNavigation.sameJourney(PlayerBotNavigation.Intent.EVADE,T,npcSpot,npcLive,10),"Evading danger is never merged into a walk");
 }

 static void hiringReasons() {
  check(PlayerBotHiring.unavailableReason(false,false,false,false,false,false,false,"Eliaven")==null,"A free bot can be hired");
  String road=PlayerBotHiring.unavailableReason(false,false,false,false,false,true,false,"Eliaven");
  check(road!=null && road.startsWith("Eliaven") && road.contains("road"),"A Road Warden says it is on road duty: "+road);
  check(PlayerBotHiring.unavailableReason(false,true,false,false,false,true,false,"Eliaven").contains("faction"),"Faction is explained first");
  check(PlayerBotHiring.unavailableReason(false,false,false,true,false,false,false,"Eliaven").contains("already in a group"),"A grouped bot says so");
  check(PlayerBotHiring.unavailableReason(false,false,false,false,false,false,true,"Eliaven").contains("another group"),"A Find Group helper says it promised another group");
  for(boolean[] f:new boolean[][]{{true,false,false,false,false,false,false},{false,false,true,false,false,false,false},{false,false,false,false,true,false,false}}){
   String r=PlayerBotHiring.unavailableReason(f[0],f[1],f[2],f[3],f[4],f[5],f[6],"Eliaven");
   check(r!=null && !r.toLowerCase().contains("resident") && !r.toLowerCase().contains("committed"),"No developer wording: "+r);
  }
 }

 static void traceBudget() {
  long t=5_000_000_000L;int allowed=0;
  for(int i=0;i<20;i++)if(com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.autoTraceAllowed(t+i))allowed++;
  check(allowed==6,"Pacing traces at most six bots every ten minutes server-wide: "+allowed);
  check(com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.autoTraceAllowed(t+600_000),"The trace budget refills after ten minutes");
 }

 static void lostBots() {
  long lost=1_000_000L;
  check(PlayerBotWorldActivities.lostHold(lost+60_000,lost,false),"Out of sight a lost bot still waits for its field-camp move");
  check(!PlayerBotWorldActivities.lostHold(lost+130_000,lost,false),"The out-of-sight wait still ends after two minutes");
  check(PlayerBotWorldActivities.lostHold(lost+10_000,lost,true),"Near a player a lost bot pauses briefly");
  check(!PlayerBotWorldActivities.lostHold(lost+25_000,lost,true),"Near a player it does not stand idle for two minutes");
  check(!PlayerBotWorldActivities.lostHold(lost+1,0,true),"A bot that was never lost is never held");
 }

 static void wiring(Path root) throws Exception {
  var activities=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotWorldActivities.java"));
  int release=activities.indexOf("boolean lost=navigation.takeLost();");
  check(release>0 && activities.indexOf("if(lost)s.why=\"lost: waiting to move to a field camp\";",release)>activities.indexOf("goal(s,null);",release),
   "The lost reason is written after the goal release, so the log says why the bot stands");
  var nav=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotNavigation.java"));
  check(nav.contains("motionWatch.observe("),"Navigation feeds the pacing watch every travel turn");
  check(nav.contains("mover.traceAfterPacing(now)"),"A bot caught pacing traces its next moves in the log");
  var placement=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotWorldPlacement.java"));
  check(placement.contains("p.minimum(),starterMaximum(p.maximum())"),"Every camp on the starting map is capped below Ascension");
  check(nav.contains("sameJourney(routeIntent,intent,routeGoal,new Point(targetX,targetY,targetZ),"),"Move requests for the same spot keep one route");
  check(nav.contains("!PlayerBotRoadDuty.permitsGoal(warden,targetX,targetY,targetZ)"),"A warden is never sent off its beat to be stopped step by step (Eliaven, 9 Nov)");
  check(nav.contains("&& !handoverDue(walkRouteOwned,route.size(),"),"A published step is not kept to its very end");
  check(nav.contains("!usefulHop(HEIGHT_DIRECTIONS[heightCursor/HEIGHT_REACHES.length]")||nav.contains("!usefulHop(offset,"),"The height fan rejects flat sideways hops");
  var mover=Files.readString(root.resolve("src/com/aionemu/gameserver/controllers/movement/PlayerBotMoveController.java"));
  int follow=mover.indexOf("private void followStep()");
  int start=mover.indexOf("started.set(true);owner.getController().onStartMove();setAndSendStartMove(owner);followPacket=now;",follow);
  int speed=mover.indexOf("PlayerBotFollowSpeed.update(owner,step.x(),step.y(),step.z());",follow);
  check(follow>0 && start>follow && speed>start,"Following publishes its speed only after the start packet marks it moving, so clients get the catch-up speed");
 }
 private PlayerBotSmoothTravelCheck(){}
}
