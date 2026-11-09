package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotDetailTier.*;

/** Phase 2: level of detail inside an occupied map. Pure selection, rotation, SmartScale and pacing; no World/SQL/tasks. */
public final class PlayerBotDetailTierCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  selection();
  rotation();
  smartScale();
  classification();
  pacing();
  lagWindows();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" detail tier checks; no World/SQL/tasks");
 }

 static Bot bot(int id,float x){return new Bot(id,7L,x,0,0);}
 static void selection() {
  var human=new Human(7L,0,0,0,Set.of(1));
  var bots=new ArrayList<Bot>();
  bots.add(bot(1,90));             // known by the human: always visible
  for(int i=0;i<5;i++)bots.add(bot(10+i,100+i*10)); // 100..140 m
  bots.add(bot(20,230));           // outside enter, inside leave
  bots.add(new Bot(30,8L,5,0,0));  // another map or instance
  var picked=select(List.of(human),bots,Set.of(),200,250,3);
  check(picked.visible().contains(1),"A bot the human can see is always full detail");
  check(picked.visible().containsAll(List.of(10,11,12)) && !picked.visible().contains(13),"The out-of-view buffer keeps the nearest up to the cap: "+picked.visible());
  check(picked.capped()==2,"Buffer bots over the cap are counted: "+picked.capped());
  check(!picked.visible().contains(20),"A newcomer beyond the enter radius is near");
  check(!picked.visible().contains(30),"Another map or instance never counts");
  var kept=select(List.of(human),bots,Set.of(20),200,250,10);
  check(kept.visible().contains(20),"A bot already visible stays until the leave radius (hysteresis)");
  check(!select(List.of(human),List.of(bot(20,260)),Set.of(20),200,250,10).visible().contains(20),"Past the leave radius it drops to near");
  var favoured=select(List.of(human),List.of(bot(40,120),bot(41,150)),Set.of(41),200,250,1);
  check(favoured.visible().contains(41) && !favoured.visible().contains(40),"At the cap, a bot already in the buffer is not pushed out by a slightly closer newcomer");
  var two=select(List.of(human,new Human(7L,1000,0,0,Set.of())),List.of(bot(50,1100),bot(51,100)),Set.of(),200,250,5);
  check(two.visible().containsAll(List.of(50,51)),"Each human gets its own buffer");
  check(select(List.of(),bots,Set.of(),200,250,30).visible().isEmpty(),"No human, nothing visible");
  check(select(List.of(human),bots,Set.of(),200,250,0).visible().equals(Set.of(1)),"Cap 0 keeps only what the human can see");
 }

 static void rotation() {
  long now=1_000_000_000L;int on=0;
  for(int id=0;id<10000;id++)if(rotating(id,now,10,30))on++;
  check(on>800 && on<1200,"About 10% of bots rotate active: "+on);
  boolean same=true;for(int id=0;id<200;id++)same&=rotating(id,now,10,30)==rotating(id,now+1000,10,30);
  check(same,"Within one window the active set is stable");
  int changed=0;for(int id=0;id<1000;id++)if(rotating(id,now,10,30)!=rotating(id,now+30000,10,30))changed++;
  check(changed>50,"The next window picks a different set: "+changed);
  check(!rotating(5,now,0,30) && rotating(5,now,100,30),"0% and 100% are exact");
 }

 static void smartScale() {
  check(share(10,0,50,200)==10 && share(10,50,50,200)==10,"No lag: full share");
  check(share(10,125,50,200)==5,"Halfway: half the share");
  check(share(10,200,50,200)==0 && share(10,5000,50,200)==0,"At the ceiling the rotating share is gone");
  check(share(10,60,100,100)==10 && share(10,150,100,100)==0,"Equal floor and ceiling act as one threshold");
 }

 static void classification() {
  java.util.function.BooleanSupplier yes=()->true,no=()->false;
  check(classify(false,true,false,yes,true,true)==Tier.FAR,"A map without humans stays dormant, whatever else");
  check(classify(true,true,false,yes,false,false)==Tier.FORCED,"Combat, death, tasks, a human's party or legion force full detail");
  check(classify(true,true,true,no,false,false)==Tier.FORCED,"A human-controlled bot is always full detail");
  check(classify(true,false,false,no,false,false)==Tier.FORCED,"Tiers off: map-wide full detail as before");
  check(classify(true,true,false,no,true,false)==Tier.SEEN,"A visible bot is full detail");
  check(classify(true,true,false,no,false,true)==Tier.ROTATING,"The rotating share is full detail");
  check(classify(true,true,false,no,false,false)==Tier.NEAR,"Everyone else on the map is near");
  check(Tier.SEEN.full() && Tier.FORCED.full() && Tier.ROTATING.full() && !Tier.NEAR.full() && !Tier.FAR.full(),"Only near and far are reduced");
  int[] asked={0};java.util.function.BooleanSupplier counted=()->{asked[0]++;return false;};
  classify(true,true,false,counted,true,false);classify(true,false,false,counted,false,false);classify(false,true,false,counted,false,false);
  check(asked[0]==0,"Seen, disabled and dormant bots never run the force checks");
  classify(true,true,false,counted,false,false);
  check(asked[0]==1,"An unseen bot runs them once per recheck");
  check(applies(true,true,true,true),"An autonomous, spawned, positioned world bot gets a tier");
  check(!applies(false,true,true,true) && !applies(true,false,true,true),"Tiers off or a controlled bot: native pacing");
  check(!applies(true,true,false,true) && !applies(true,true,true,false),"Not spawned or no position yet (map transfer): native pacing, no tier checks");
 }

 static void pacing() {
  check(nearReaction(2000,false,3,600)==6000,"An idle near bot thinks three times less often");
  check(nearReaction(20000,false,3,600)==20000,"Idle near thinking is capped at 20 s");
  check(nearReaction(2000,true,3,600)==600,"A walking near bot checks its path every 600 ms, not 200");
  check(nearReaction(100,true,3,600)==300,"Never slower than the multiplier allows");
  check(nearReaction(2000,true,3,50)==200,"The walking check never drops below the native 200 ms");
  check(nearReaction(2000,false,0,600)==2000,"A multiplier below 1 is treated as 1");
 }

 static void lagWindows() {
  resetLag();
  late(40,10_000);late(60,11_000);
  check(lag()==0,"The current window does not count yet");
  late(500,15_100);
  check(lag()==50,"After the window closes, its average drives SmartScale: "+lag());
  late(-5,16_000);late(120_000,16_000);
  late(10,20_200);
  check(lag()==500,"Negative and absurd samples are ignored: "+lag());
  resetLag();
 }

 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var activity=Files.readString(src.resolve("PlayerBotWorldActivity.java"));
  var activities=Files.readString(src.resolve("PlayerBotWorldActivities.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var config=Files.readString(root.resolve("config/playerbots/features.properties"));
  check(session.contains("if(autonomous() && !active && detail()==PlayerBotDetailTier.Tier.NEAR)return PlayerBotDetailTier.nearReaction(ordinary,moving);"),
   "Only idle, out-of-sight world bots slow down; combat and quest targets keep 500 ms");
  check(session.contains("bot.getController().isInCombat() || bot.isCasting() || bot.isTrading() || bot.isLooting()"),
   "Native tasks force full detail");
  check(session.contains("bot.isInInstance()") && session.contains("PlayerBotTeams.members(bot).stream().anyMatch(member->!member.isPlayerBot())") && session.contains("PlayerBotDetailTier.humanLegionHere(bot)"),
   "Instances, a human's party and a human's legion force full detail");
  check(session.contains("if(!PlayerBotWorldActivity.allowed(this))return detail=PlayerBotDetailTier.Tier.FAR;"),"Map dormancy is read live, never from the cache");
  check(session.contains("PlayerBotDetailTier.applies(PlayerBotDetailTier.enabled(),autonomous(),bot.isSpawned(),bot.getPosition()!=null)") && session.contains("PlayerBotDetailTier.of(this,this::forcedDetail,now)"),
   "The gate runs before any tier check, and force checks are lazy");
  check(activity.contains("PlayerBotDetailTier.observe(humans,bots,legions);") && activity.contains("player.getKnownList().forEachPlayer"),
   "One shared observation per refresh, using each human's real known list");
  check(activities.contains("PlayerBotDetailTier.near(bot.getObjectId())") && activities.contains("boolean upgrade=solo.isEmpty() && !deferred;"),
   "The upgrade-hunt search waits out of sight");
  check(service.contains("PlayerBotDetailTier.late(admission-candidate.due(),admission)") && service.contains("\"Detail tiers: \"+PlayerBotDetailTier.stats(active)"),
   "SmartScale reads real decision lateness and the report shows the tiers");
  for(var key:List.of("detail_tiers","detail.visible_radius","detail.visible_leave_radius","detail.visible_per_human","detail.near_reaction_multiplier",
   "detail.near_moving_ms","detail.rotation_percent","detail.rotation_seconds","detail.smartscale_floor_ms","detail.smartscale_ceiling_ms","detail.legion_force"))
   check(config.contains("gameserver.playerbots.world."+key+" = "),"Config documents "+key);
 }
 private PlayerBotDetailTierCheck(){}
}
