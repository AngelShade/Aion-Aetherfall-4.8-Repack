package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import sun.misc.Unsafe;
import static com.aionemu.gameserver.services.playerbot.PlayerBotTravelFormationCheck.*;

/** Find Group entrance waits, late joiners and Summon targeting; no world or DB activity. */
public final class PlayerBotEntranceSummonCheck {
 static int assertions;
 static void require(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
 static PlayerBotFindGroup.EntranceMeeting meeting(Actor bot,Actor human){return new PlayerBotFindGroup.EntranceMeeting(bot,human,null,null,1);}
 static void seats() {
  require(PlayerBotFindGroup.seatLimit(false,true,6)==5,"An all-bot dungeon party keeps one seat free for a human");
  require(PlayerBotFindGroup.seatLimit(true,true,6)==6,"Bots fill the last dungeon seat once a human has joined");
  require(PlayerBotFindGroup.seatLimit(false,false,6)==3,"An all-bot field party stays small");
  require(PlayerBotFindGroup.seatLimit(true,false,6)==6,"A human's field party can fill");
  require(PlayerBotFindGroup.seatLimit(true,true,3)==3,"A run's own size still caps the party");
  require(PlayerBotFindGroup.seatLimit(true,true,12)==6,"A native group never exceeds six");
 }
 static void targets(Actor owner,PlayerBotSession first,PlayerBotSession second)throws Exception {
  var party=List.of(first,second);
  require(PlayerBotRecovery.targeted(null,party)==party,"No target summons the party");
  require(PlayerBotRecovery.targeted(owner,party)==party,"Targeting yourself summons the party");
  require(PlayerBotRecovery.targeted(actor(1999980099),party)==party,"Targeting someone else summons the party");
  require(PlayerBotRecovery.targeted(new Object(),party)==party,"Targeting a monster or object summons the party");
  require(PlayerBotRecovery.targeted(second.bot(),party).equals(List.of(second)),"A targeted companion answers alone");
 }
 static void runHold() {
  require(PlayerBotFindGroup.holdsRun(true,true,true,true,false),"A human-led party inside its dungeon stays, even if the poster died or finished its own quest");
  require(!PlayerBotFindGroup.holdsRun(true,true,true,true,true),"The run's final boss ends the term");
  require(!PlayerBotFindGroup.holdsRun(true,true,false,true,false),"The human leaving the party ends the term");
  require(!PlayerBotFindGroup.holdsRun(true,false,true,true,false),"No human leading (or offline): the normal rules apply");
  require(!PlayerBotFindGroup.holdsRun(true,true,true,false,false),"Outside the dungeon, the normal rules apply");
  require(!PlayerBotFindGroup.holdsRun(false,true,true,true,false),"Field and quest-help posts keep their objective-based end");
  var emotes=new java.util.HashSet<Integer>();for(int bot=0;bot<8;bot++)emotes.add(PlayerBotFindGroup.farewellEmote(bot));
  require(emotes.size()==4 && emotes.contains(com.aionemu.gameserver.model.EmotionId.VICTORY.id()) && emotes.contains(com.aionemu.gameserver.model.EmotionId.THANK.id()),
   "A cleared party says goodbye with a mix of native victory, clap, thank and salute emotes");
  require(PlayerBotFindGroup.RUN_FAREWELL_MS>=5000 && PlayerBotFindGroup.RUN_FAREWELL_MS<=30000,"The goodbye lasts seconds, then the bots leave normally");
 }
 static void messages() {
  String one=PlayerBotSummonPolicy.failure(1,List.of("Terewen"),null);
  require(one.startsWith("Terewen could not be summoned.") && one.contains("safe spot"),"A lone refusal names the companion and the reason: "+one);
  String some=PlayerBotSummonPolicy.failure(5,List.of("Terewen","Galinaris"),"The companion trade is still committing; retry Summon.");
  require(some.startsWith("Summoned 3 of 5 companions. Terewen and Galinaris could not be summoned."),"A partial result reports who arrived: "+some);
  require(some.endsWith("retry Summon."),"A specific refusal keeps its own reason");
  String three=PlayerBotSummonPolicy.failure(3,List.of("A","B","C"),null);
  require(three.startsWith("A, B and C could not"),"Several names read naturally: "+three);
  require(!three.contains("Summoned"),"A total failure claims no arrivals");
 }
 static void waits(Actor human,Actor waiting,Actor late,Actor stranger,PlayerBotSession session,PlayerBotSession lateSession) {
  var meetings=PlayerBotFindGroup.MEETINGS;
  meetings.clear();
  require(!PlayerBotFindGroup.waitingAtEntrance(session),"No meeting, no wait");
  require(!PlayerBotFindGroup.releaseFromEntranceWait(session),"No meeting, nothing to release");
  require(PlayerBotFindGroup.partyRun(human,late)==null,"No run to join without a meeting");
  require(!PlayerBotFindGroup.awaitingHuman(human),"Nobody is waiting without a meeting");
  var run=meeting(waiting,human);run.accepted=true;run.arrived=true;
  meetings.put(waiting.getObjectId(),run);
  require(PlayerBotFindGroup.waitingAtEntrance(session),"A meeting holds its bot back from automatic travel");
  require(PlayerBotFindGroup.awaitingHuman(human),"The party is known to be waiting for this human");
  require(PlayerBotFindGroup.partyRun(human,late)==run,"A late joiner finds the party's dungeon run");
  require(PlayerBotFindGroup.partyRun(human,waiting)==null,"A bot's own meeting is not another run to join");
  require(PlayerBotFindGroup.partyRun(stranger,late)==null,"Another player's run is never joined");
  var joined=PlayerBotFindGroup.joinRun(run,late);
  require(joined.bot==late && joined.human==human && joined.channel==run.channel,"The late joiner waits for the same human on the same channel");
  require(joined.accepted && joined.arrived,"A human already at the entrance is not asked to travel again");
  require(!joined.summoned,"A late joiner stays put until the player summons");
  meetings.put(late.getObjectId(),joined);
  require(PlayerBotFindGroup.waitingAtEntrance(lateSession),"The late joiner is held like the rest of the party, so nothing teleports it");
  require(PlayerBotFindGroup.releaseFromEntranceWait(session),"Summon releases the waiting bot");
  require(!PlayerBotFindGroup.waitingAtEntrance(session),"A summoned bot is free to relocate");
  require(PlayerBotFindGroup.waitingAtEntrance(lateSession),"Summoning one companion leaves the others waiting");
  require(meetings.containsKey(waiting.getObjectId()),"The run stays recorded until the party has entered");
  require(!PlayerBotFindGroup.releaseFromEntranceWait(session),"A second release changes nothing, so a failed retry cannot undo the first");
  PlayerBotFindGroup.restoreEntranceWait(session);
  require(PlayerBotFindGroup.waitingAtEntrance(session),"A failed Summon returns the bot to its wait");
  meetings.clear();
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  var human=actor(1999980040);var waiting=actor(1999980041);var late=actor(1999980042);var stranger=actor(1999980043);
  var group=(Group)unsafe.allocateInstance(Group.class);group.players=List.of(human,waiting,late);human.group=waiting.group=late.group=group;
  var session=session(human,waiting);var lateSession=session(human,late);
  seats();
  runHold();
  targets(human,session,lateSession);
  messages();
  waits(human,waiting,late,stranger,session,lateSession);
  System.out.println("OK: "+assertions+" entrance summon checks");
 }
 private PlayerBotEntranceSummonCheck(){}
}
