package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.services.instance.InstanceScaler;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.WorldMapInstance;

/** Native combat evidence and verified instance results. No inferred boss endings. */
public final class PlayerBotRunRewards {
 private static final Map<WorldMapInstance,Run> RUNS=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<Npc,Set<Integer>> ACTIVITY=Collections.synchronizedMap(new WeakHashMap<>());
 private static final class Fight {
  final Set<Integer> first;final Set<Integer> participants=new HashSet<>();boolean active=true;
  Fight(List<Player> players){first=new HashSet<>();for(var p:players)first.add(p.getObjectId());}
 }
 private static final class Run {
  final Map<Integer,Fight> fights=new HashMap<>();final Map<Integer,Integer> levels=new HashMap<>();final Map<Integer,Double> setups=new HashMap<>();
  synchronized void pull(int boss,List<Player> players) {
   var fight=fights.get(boss);
   if(fight!=null && fight.active)return;
   if(fight==null){fight=new Fight(players);fights.put(boss,fight);}else{fight.active=true;fight.participants.clear();}
   for(var player:players)record(player);
  }
  void record(Player player) {
   double setup=PlayerBotChallenge.gearFactor(PlayerBotGearBaseline.actual(player),PlayerBotGearBaseline.expected(player.getPlayerClass(),player.getLevel()))*PlayerBotChallenge.rankFactor(rank(player));
   setups.merge(player.getObjectId(),setup,Math::max);levels.merge(player.getObjectId(),(int)player.getLevel(),Math::max);
  }
  synchronized void contribute(int boss,Player player) {
   var fight=fights.get(boss);if(fight!=null && fight.active && fight.participants.add(player.getObjectId()))record(player);
  }
  synchronized void reset(int boss){var fight=fights.get(boss);if(fight!=null)fight.active=false;}
  synchronized double strength(int bossLevel) {double total=0;for(var setup:setups.entrySet())total+=PlayerBotChallenge.levelFactor(levels.get(setup.getKey()),bossLevel)*setup.getValue();return total;}
  synchronized int highestLevel(){return levels.values().stream().mapToInt(Integer::intValue).max().orElse(0);}
  synchronized Set<Integer> eligible(int boss){var f=fights.get(boss);if(f==null)return Set.of();var result=new HashSet<>(f.first);result.retainAll(f.participants);return Set.copyOf(result);}
 }
 private static PlayerBotGrowth.CombatRank rank(Player player) {
  if(!player.isPlayerBot())return null;
  try{return PlayerBotGrowth.profile(player).combatRank();}catch(IOException|RuntimeException e){return null;}
 }
 /** First native engagement establishes membership; every real repull updates strongest setups. */
 public static void onPull(Npc npc) {
  if(npc.getRating().ordinal()<NpcRating.ELITE.ordinal())return;
  var instance=npc.getWorldMapInstance();if(!instance.getParent().isInstanceType())return;
  RUNS.computeIfAbsent(instance,i->new Run()).pull(npc.getObjectId(),instance.getPlayersInside());
 }
 /** Positive damage, substantive hate, effective support or taking damage. Proximity aggro is not practice. */
 public static void contribution(Npc npc,Player player) {
  if(npc.getWorldMapInstance()!=player.getWorldMapInstance() || !PositionUtil.isInRange(npc,player,100) || !npc.isEnemyFrom(player))return;
  ACTIVITY.computeIfAbsent(npc,n->java.util.concurrent.ConcurrentHashMap.newKeySet()).add(player.getObjectId());
  var run=RUNS.get(npc.getWorldMapInstance());if(run!=null)run.contribute(npc.getObjectId(),player);
 }
 public static void support(Creature effector,Creature recipient) {
  if(!(effector instanceof Player helper) || !(recipient instanceof Player target) || helper==target || !PlayerBotTeams.same(helper,target))return;
  // Only the recipient's actual attackers; no known-list or population scan.
  target.getAggroList().stream().filter(a->a.getDamage()>0).forEach(a->{if(a.getAttacker().getMaster() instanceof Npc npc && !npc.isDead())contribution(npc,helper);});
 }
 public static void reset(Npc npc){ACTIVITY.remove(npc);var run=RUNS.get(npc.getWorldMapInstance());if(run!=null)run.reset(npc.getObjectId());}
 public static boolean participated(Npc npc,Player player){var actors=ACTIVITY.get(npc);return actors!=null && actors.contains(player.getObjectId());}
 /** Same relative challenge inputs as tickets, evaluated before character XP clamping. */
 public static boolean learningEligible(Npc npc,Player bot) {
  if(!participated(npc,bot))return false;
  var members=PlayerBotTeams.members(bot);if(members.isEmpty())members=List.of(bot);
  double strength=0;int count=0,highest=0;
  for(var member:members)if(member.getWorldMapInstance()==npc.getWorldMapInstance() && PositionUtil.isInRange(member,npc,100)) {
   strength+=PlayerBotChallenge.levelFactor(member.getLevel(),npc.getLevel())*PlayerBotChallenge.gearFactor(PlayerBotGearBaseline.actual(member),PlayerBotGearBaseline.expected(member.getPlayerClass(),member.getLevel()))*PlayerBotChallenge.rankFactor(rank(member));count++;highest=Math.max(highest,member.getLevel());
  }
  double demand=InstanceScaler.demand(npc.getWorldMapInstance())*com.aionemu.gameserver.custom.mobscaling.WorldMobScaler.demand(npc);
  return PlayerBotChallenge.practice(highest,npc.getLevel(),PlayerBotChallenge.challenge(strength,Math.max(1,count),demand),demand);
 }
 /** Called only by a verified native ending. Missing first-pull evidence never becomes fabricated presence at death. */
 public static void completed(WorldMapInstance instance,Npc boss) {
  var run=RUNS.get(instance);
  if(run==null){for(var p:instance.getPlayersInside())PlayerBotRunBriefing.unrewarded(p,"Its fights were not tracked from the first pull, so it pays no Recruitment Ticket.");return;}
  var eligible=run.eligible(boss.getObjectId());
  double challenge=PlayerBotChallenge.challenge(run.strength(boss.getLevel()),instance.getMaxPlayers(),InstanceScaler.demand(instance));
  int highest=run.highestLevel();
  if(PlayerBotChallenge.practice(highest,boss.getLevel(),challenge,InstanceScaler.demand(instance)))for(var player:instance.getPlayersInside())if(eligible.contains(player.getObjectId()))PlayerBotGrowth.learn(player,200);
  if(PlayerBotConfig.TICKETS_ENABLED)pay(instance,PlayerBotChallenge.tickets(challenge),p->eligible.contains(p.getObjectId()),"Run");
  for(var p:instance.getPlayersInside())if(!eligible.contains(p.getObjectId()))PlayerBotRunBriefing.unrewarded(p,"You were not in its fights, so it pays you no Recruitment Ticket.");
  RUNS.remove(instance);
 }
 public static void won(WorldMapInstance instance,Predicate<Player> winners){if(PlayerBotConfig.TICKETS_ENABLED)pay(instance,PlayerBotConfig.TICKET_PVP_WIN,winners,"Battle");}
 /** Pays the native ending and tells each paid player what they earned (or, after a briefed run, why nothing). */
 private static void pay(WorldMapInstance instance,int tickets,Predicate<Player> eligible,String label) {
  var accounts=new HashSet<Integer>();
  for(var player:instance.getPlayersInside())if(!player.isPlayerBot() && eligible.test(player)) {
   int account=player.getAccount().getId();
   if(tickets>0 && accounts.add(account))PlayerBotTickets.award(account,receiptKey(instance.getRunId(),account),tickets);
   PlayerBotRunBriefing.rewarded(player,Math.max(0,tickets),label);
  }
 }
 public static String receiptKey(String run,int account){return "run:"+run+":"+account;}
 private PlayerBotRunRewards(){}
}
