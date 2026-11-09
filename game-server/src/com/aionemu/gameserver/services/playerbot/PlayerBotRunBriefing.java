package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;

/**
 * Find Group dungeon runs the player can follow (owner play test, 8 Nov): on joining, what the run is, its named bosses
 * and the reward rule; each named boss as it falls; and at the native final-boss completion what the run actually paid.
 * The native ending (GeneralInstanceHandler.completeRun) and PlayerBotRunRewards still decide everything; this only tells.
 */
public final class PlayerBotRunBriefing {
 private record Run(int map,String dungeon,Map<Integer,String> bosses,Set<Integer> down,long until) {}
 static final long RUN_MS=3*3600_000L,BALANCE_DELAY_MS=3000;
 static final int SHOWN_BOSSES=6;
 static final AtomicLong BRIEFED=new AtomicLong(),BOSSES_DOWN=new AtomicLong(),PAID=new AtomicLong(),UNPAID=new AtomicLong();
 private static final Map<Integer,Run> RUNS=new ConcurrentHashMap<>();
 private static final Map<Integer,Map<Integer,String>> BOSSES=new ConcurrentHashMap<>();

 static boolean enabled(){return PlayerBotConfig.FIND_GROUP_RUN_BRIEFING;}
 private static void tell(Player human,String text,boolean centre) {
  if(human==null || !human.isOnline())return;
  PacketSendUtility.sendMessage(human,"[Run] "+text);
  if(centre)PacketSendUtility.sendMessage(human,text,ChatType.BRIGHT_YELLOW_CENTER);
 }
 /** Named bosses of a dungeon from its static spawns: legendary ones, or heroic ones when it has none; by level. */
 static Map<Integer,String> bosses(int map) {
  return BOSSES.computeIfAbsent(map,m->{
   var legendary=new TreeMap<Integer,List<Integer>>();var heroic=new TreeMap<Integer,List<Integer>>();var seen=new HashSet<Integer>();
   for(var group:DataManager.SPAWNS_DATA.getSpawnsByWorldId(m)) {
    var t=DataManager.NPC_DATA.getNpcTemplate(group.getNpcId());if(t==null || !seen.add(t.getTemplateId()))continue;
    int level=t.getLevel();
    if(t.getRating()==NpcRating.LEGENDARY)legendary.computeIfAbsent(level,l->new ArrayList<>()).add(t.getTemplateId());
    else if(t.getRating()==NpcRating.HERO)heroic.computeIfAbsent(level,l->new ArrayList<>()).add(t.getTemplateId());
   }
   var chosen=legendary.isEmpty()?heroic:legendary;var result=new LinkedHashMap<Integer,String>();
   for(var ids:chosen.values())for(int id:ids)result.putIfAbsent(id,DataManager.NPC_DATA.getNpcTemplate(id).getName());
   return Collections.unmodifiableMap(result);
  });
 }
 static String names(Collection<String> names,int shown) {
  var list=new ArrayList<>(new LinkedHashSet<>(names));
  if(list.isEmpty())return "";
  String text=String.join(", ",list.subList(0,Math.min(shown,list.size())));
  return list.size()>shown?text+" and "+(list.size()-shown)+" more":text;
 }
 static String rewardLine(long tickets,long balance) {
  return tickets<=0?"It counted as easy for your group, so it pays no Recruitment Ticket this time."
   :"You earned "+tickets+" Recruitment Ticket"+(tickets==1?"":"s")+"!"+(balance<0?"":" Tickets now: "+balance+".");
 }
 /** Joining a Find Group dungeon party: what the run is, who stands at the end and what it can pay. */
 static void joined(Player bot,Player human,PlayerBotGroupNeeds.Need need) {
  if(!enabled() || !need.instance() || human.isPlayerBot())return;
  try {
   String dungeon=need.title();var bosses=bosses(need.map());
   var quest=need.quest()>0 && DataManager.QUEST_DATA!=null?DataManager.QUEST_DATA.getQuestById(need.quest()):null;
   String list=names(bosses.values(),SHOWN_BOSSES);
   tell(human,dungeon+": the party clears it together, and the bots stay until the final boss falls."
    +(quest==null?"":" Quest: "+quest.getName()+".")+(list.isEmpty()?"":" Named bosses in here: "+list+".")
    +" Reward: up to 3 Recruitment Tickets, depending on how hard it is for your group.",false);
   tell(human,"Run: "+dungeon,true);
   PacketSendUtility.sendPacket(human,new SM_MESSAGE(bot,"Let's clear "+dungeon+"! See you at the entrance.",ChatType.GROUP));
   RUNS.put(human.getObjectId(),new Run(need.map(),dungeon,bosses,ConcurrentHashMap.newKeySet(),System.currentTimeMillis()+RUN_MS));
   BRIEFED.incrementAndGet();
  }catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(PlayerBotRunBriefing.class).warn("Run briefing skipped: {}",e.toString());}
 }
 /** NpcController.onDie: a named boss of a run falls in the player's own instance. Never throws into a death. */
 public static void npcDied(Npc npc) {
  if(RUNS.isEmpty())return;
  try {
   if(!npc.isInInstance() || npc.getObjectTemplate().getRating()!=NpcRating.LEGENDARY && npc.getObjectTemplate().getRating()!=NpcRating.HERO)return;
   long now=System.currentTimeMillis();RUNS.values().removeIf(r->now>r.until());
   for(var entry:RUNS.entrySet()) {
    var run=entry.getValue();if(run.map()!=npc.getWorldId())continue;
    String name=run.bosses().get(npc.getNpcId());if(name==null)continue;
    var human=World.getInstance().getPlayer(entry.getKey());
    if(human==null || !human.isOnline() || human.getWorldMapInstance()!=npc.getWorldMapInstance() || !run.down().add(npc.getNpcId()))continue;
    BOSSES_DOWN.incrementAndGet();
    tell(human,"Boss down: "+name+" ("+run.down().size()+"/"+run.bosses().size()+")",npc.getObjectTemplate().getRating()==NpcRating.LEGENDARY);
   }
  }catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(PlayerBotRunBriefing.class).warn("Run boss notice skipped: {}",e.toString());}
 }
 /** What a finished run (or a won battleground) actually paid, with the balance once the ticket outbox delivered it. */
 public static void rewarded(Player human,long tickets,String label) {
  if(!enabled() || human==null || human.isPlayerBot())return;
  boolean briefed=RUNS.remove(human.getObjectId())!=null;
  if(tickets<=0 && !briefed)return;
  PAID.incrementAndGet();
  ThreadPoolManager.getInstance().schedule(()->{
   long balance=-1;
   try(var c=DatabaseFactory.getConnection()){balance=com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO.wallet(c,human.getAccount().getId()).tickets();}
   catch(java.sql.SQLException|RuntimeException e){/* the line still says what was earned */}
   String line=rewardLine(tickets,balance);
   tell(human,label+" complete! "+line,false);tell(human,line,true);
  },BALANCE_DELAY_MS);
 }
 public static void unrewarded(Player human,String why) {
  if(!enabled() || human==null || human.isPlayerBot() || RUNS.remove(human.getObjectId())==null)return;
  UNPAID.incrementAndGet();
  tell(human,"Run complete. "+why,false);
 }
 static String stats(){return "enabled="+enabled()+", runs briefed="+BRIEFED.get()+", named bosses announced="+BOSSES_DOWN.get()+", run rewards shown="+PAID.get()+", unpaid runs explained="+UNPAID.get();}
 private PlayerBotRunBriefing(){}
}
