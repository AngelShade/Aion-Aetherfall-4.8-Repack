package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.utils.Json;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;

/** Versioned generated-bot traits. Existing actors are never assigned a recruitment roll by a read. */
public final class PlayerBotGrowth {
 public enum Origin { LEGACY, GACHA, WORLD }
 public enum GearRarity { POOR, ORDINARY, GOOD, GREAT, EXCELLENT }
 public enum CombatRank {
  POOR(30), ORDINARY(20), GOOD(10), GREAT(4), EXCELLENT(0);
  private final int mistakePercent;
  CombatRank(int mistakePercent) { this.mistakePercent=mistakePercent; }
  public int mistakePercent() { return mistakePercent; }
 }

 public record Profile(int version,Origin origin,PlayerClass rolledAdvancedClass,GearRarity autoEquipRarity,
                       CombatRank initialCombatRank,CombatRank combatRank,long earnedLearningXp) {
  public Profile {
   Objects.requireNonNull(origin);
   if(version!=1 || earnedLearningXp<0)throw new IllegalArgumentException("Invalid bot growth version/learning");
   if(origin==Origin.LEGACY) {
    if(rolledAdvancedClass!=null || autoEquipRarity!=null || initialCombatRank!=null || combatRank!=null || earnedLearningXp!=0)
     throw new IllegalArgumentException("Legacy bots have no fabricated recruitment traits");
   } else {
    Objects.requireNonNull(rolledAdvancedClass);Objects.requireNonNull(autoEquipRarity);
    Objects.requireNonNull(initialCombatRank);Objects.requireNonNull(combatRank);
    if(rolledAdvancedClass.isStartingClass() || combatRank.ordinal()<initialCombatRank.ordinal())
     throw new IllegalArgumentException("Invalid generated bot class/rank");
   }
  }
  public boolean traitsAssigned() { return origin!=Origin.LEGACY; }
 }

 private static final Profile LEGACY=new Profile(1,Origin.LEGACY,null,null,null,null,0);
 private static final Set<String> FIELDS=Set.of("version","account","character","origin","rolledAdvancedClass",
  "autoEquipRarity","initialCombatRank","combatRank","earnedLearningXp");

 static Profile decode(Map<String,String> values) {
  if(!values.keySet().equals(FIELDS))throw new IllegalArgumentException("Invalid growth fields");
  var origin=Origin.valueOf(values.get("origin"));
  return new Profile(Integer.parseInt(values.get("version")),origin,
   values.get("rolledAdvancedClass").isEmpty()?null:PlayerClass.valueOf(values.get("rolledAdvancedClass")),
   values.get("autoEquipRarity").isEmpty()?null:GearRarity.valueOf(values.get("autoEquipRarity")),
   values.get("initialCombatRank").isEmpty()?null:CombatRank.valueOf(values.get("initialCombatRank")),
   values.get("combatRank").isEmpty()?null:CombatRank.valueOf(values.get("combatRank")),
   Long.parseLong(values.get("earnedLearningXp")));
 }

 static void validate(int account,Map<String,String> values) {
  var profile=decode(values);
  if(profile.origin()==Origin.GACHA && account<=0 || profile.origin()==Origin.WORLD && !PlayerBotPopulationPolicy.serverAccount(account))
   throw new IllegalArgumentException("Generated growth origin does not match native account custody");
 }

 /** Called under the native metadata cache lock. Baseline traits never change after assignment. */
 static void validateUpdate(Map<String,String> previous,Map<String,String> next) {
  if(previous.isEmpty())return;
  var before=decode(previous);var after=decode(next);
  if(before.origin()!=after.origin() || before.rolledAdvancedClass()!=after.rolledAdvancedClass()
   || before.autoEquipRarity()!=after.autoEquipRarity() || before.initialCombatRank()!=after.initialCombatRank()
   || after.earnedLearningXp()<before.earnedLearningXp()
   || before.combatRank()!=null && (after.combatRank()==null || after.combatRank().ordinal()<before.combatRank().ordinal()))
   throw new IllegalArgumentException("Saved recruitment traits and earned growth cannot be rerolled or reduced");
 }

 private static Properties encode(int account,int character,Profile profile) {
  var values=new Properties();
  values.setProperty("version",Integer.toString(profile.version()));values.setProperty("account",Integer.toString(account));
  values.setProperty("character",Integer.toString(character));values.setProperty("origin",profile.origin().name());
  values.setProperty("rolledAdvancedClass",profile.rolledAdvancedClass()==null?"":profile.rolledAdvancedClass().name());
  values.setProperty("autoEquipRarity",profile.autoEquipRarity()==null?"":profile.autoEquipRarity().name());
  values.setProperty("initialCombatRank",profile.initialCombatRank()==null?"":profile.initialCombatRank().name());
  values.setProperty("combatRank",profile.combatRank()==null?"":profile.combatRank().name());
  values.setProperty("earnedLearningXp",Long.toString(profile.earnedLearningXp()));return values;
 }

 /** Stages an explicit result only. Ticket debit/claim and randomness belong to the recruitment transaction owner. */
 public static void prepareCreation(Player player,Origin origin,PlayerClass advancedClass,GearRarity rarity,CombatRank rank)throws IOException {
  if(origin==Origin.LEGACY || !player.isPlayerBot() || player.isSpawned() || !PlayerBotLease.isReserved(player.getObjectId())
   || !PlayerBotRoster.contains(player.getObjectId()))throw new IllegalArgumentException("Growth creation requires an unpublished dedicated generated bot");
  if(advancedClass==null || advancedClass.isStartingClass()
   || player.getPlayerClass()!=advancedClass && player.getPlayerClass()!=advancedClass.getStartingClass())
   throw new IllegalArgumentException("Rolled advanced class does not match generated character");
  if(origin==Origin.GACHA && player.isWorldBot() || origin==Origin.WORLD && !player.isWorldBot())
   throw new IllegalArgumentException("Growth origin does not match generated actor");
  // A saved legacy character must never acquire a gacha label merely because it
  // is currently offline and leased. New recruitment is staged before its player row.
  try(var connection=DatabaseFactory.getConnection();var statement=connection.prepareStatement(
   "SELECT b.account_id,b.ready,p.id FROM playerbot_roster b LEFT JOIN players p ON p.id=b.player_id WHERE b.player_id=?")) {
   statement.setInt(1,player.getObjectId());try(var rows=statement.executeQuery()) {
    if(!rows.next() || rows.getInt(1)!=player.getAccount().getId() || rows.getBoolean(2) || rows.getObject(3)!=null)
     throw new IOException("Growth creation requires a new pending roster identity, never an existing legacy character");
   }
  }catch(SQLException e){throw new IOException("Cannot verify new growth identity; no traits staged",e);}
  var profile=new Profile(1,origin,advancedClass,rarity,rank,rank,0);
  PlayerBotCreationMetadata.queue(player,"growth",encode(player.getAccount().getId(),player.getObjectId(),profile));
 }

 /** A world bot's first full rebuild is its creation: class and gear are replaced anyway, so nothing earned is rerolled.
  * The caller commits this with the rebuild's checkpoint. Traits already stored are never touched. */
 static void assignWorld(Player player,PlayerClass advancedClass)throws IOException {
  int account=player.getAccount().getId(),character=player.getObjectId();
  if(!player.isWorldBot() || !PlayerBotMetadata.load(account,character,"growth",null).isEmpty())return;
  var roll=PlayerBotRecruitmentPolicy.worldRoll(advancedClass);
  PlayerBotMetadata.save(account,character,"growth",encode(account,character,new Profile(1,Origin.WORLD,roll.advancedClass(),roll.autoEquipRarity(),roll.combatRank(),roll.combatRank(),0)));
 }

 /** Active actor lookup joins the existing metadata checkpoint cache; an absent row remains unmodified. */
 public static Profile profile(Player player)throws IOException {
  if(!player.isPlayerBot())throw new IllegalArgumentException("Growth belongs to dedicated generated bots");
  int account=player.getAccount().getId(),character=player.getObjectId();
  var staged=PlayerBotCreationMetadata.preview(character,"growth");
  if(staged!=null)return decode(staged);
  var cached=PlayerBotMetadata.cached(account,character,"growth");
  if(cached!=null)return cached.isEmpty()?LEGACY:decode(cached);
  if(!PlayerBotRoster.contains(character))throw new IllegalArgumentException("Growth belongs to dedicated generated bots");
  var properties=PlayerBotMetadata.load(account,character,"growth",null);
  return properties.isEmpty()?LEGACY:decode(PlayerBotMetadata.values(properties));
 }
 public static boolean giveOnly(Player player) {
  if(!player.isPlayerBot() || !PlayerBotRoster.contains(player.getObjectId()))return false;
  try{return profile(player).origin()==Origin.GACHA;}
  catch(IOException e){throw new IllegalStateException("Cannot verify companion custody",e);}
 }

 private static final int[] RANK_COST={1000,3000,6000,10000};
 static long remainingLearningXp(Profile profile) {
  if(profile.origin()!=Origin.GACHA || profile.combatRank()==CombatRank.EXCELLENT)return 0;
  long total=0;for(int n=profile.initialCombatRank().ordinal();n<RANK_COST.length;n++)total+=RANK_COST[n];
  return Math.max(0,total-profile.earnedLearningXp());
 }
 /** Learning XP buys rank steps in order from the initial rank; the working costs are per step. */
 public static CombatRank rank(CombatRank initial,long earned) {
  var rank=initial;
  while(rank!=CombatRank.EXCELLENT && earned>=RANK_COST[rank.ordinal()]) {
   earned-=RANK_COST[rank.ordinal()];rank=CombatRank.values()[rank.ordinal()+1];
  }
  return rank;
 }
 /** What the Companions window shows, or null for a bot with no stored traits. Learning is progress within the current step. */
 static Map<String,Object> identity(Profile profile) {
  if(!profile.traitsAssigned())return null;
  long learning=profile.earnedLearningXp();var rank=profile.initialCombatRank();
  while(rank!=CombatRank.EXCELLENT && learning>=RANK_COST[rank.ordinal()]) {
   learning-=RANK_COST[rank.ordinal()];rank=CombatRank.values()[rank.ordinal()+1];
  }
  return Map.of("origin",profile.origin().name(),"rarity",profile.autoEquipRarity().name(),"rank",rank.name(),"mistakeChance",rank.mistakePercent(),
   "learning",rank==CombatRank.EXCELLENT ? 0L : learning,"needed",rank==CombatRank.EXCELLENT ? 0 : RANK_COST[rank.ordinal()]);
 }
 public static Map<String,Object> identity(Player bot) {
  if(!bot.isPlayerBot() || !PlayerBotRoster.contains(bot.getObjectId()))return null;
  try{return identity(profile(bot));}
  catch(IOException e){throw new IllegalStateException("Cannot read companion traits",e);}
 }
 /** Real completed activity only. Stops at Excellent, and a bot with no stored traits earns nothing. */
 public static void learn(Player bot,int xp) {
  synchronized(bot) {
  if(!bot.isPlayerBot() || xp<=0)return;
  int account=bot.getAccount().getId(),character=bot.getObjectId();
  try {
   var cached=PlayerBotMetadata.cached(account,character,"growth");
   if(cached==null || cached.isEmpty())return;
   var profile=decode(cached);
   if(!profile.traitsAssigned() || profile.combatRank()==CombatRank.EXCELLENT)return;
   long earned=profile.earnedLearningXp()+xp;
   var next=new Profile(1,profile.origin(),profile.rolledAdvancedClass(),profile.autoEquipRarity(),profile.initialCombatRank(),rank(profile.initialCombatRank(),earned),earned);
   PlayerBotMetadata.save(account,character,"growth",encode(account,character,next));
  } catch(IOException | RuntimeException e) {
   org.slf4j.LoggerFactory.getLogger(PlayerBotGrowth.class).warn("Cannot retain learning for companion {}",character,e);
  }
  }
 }
 public static void learnKill(Player bot,com.aionemu.gameserver.model.gameobjects.Npc npc) {
  PlayerBotWorldOutcomes.killed(bot);
  if(!bot.isPlayerBot() || !PlayerBotRunRewards.learningEligible(npc,bot))return;
  learn(bot,switch(npc.getRating()){case HERO,LEGENDARY->50;case ELITE->20;default->5;});
  practice(bot,npc);
  PlayerBotHelpObjectives.kill(bot,npc);
 }
 private static boolean open(Player bot,int quest) {
  var state=bot.getQuestStateList().getQuestState(quest);
  return state!=null && (state.getStatus()==QuestStatus.START || state.getStatus()==QuestStatus.REWARD);
 }
 /** Native objectives only: this NPC's registered kill events and quest drops, on quests the bot holds open. */
 static Set<Integer> objectives(Player bot,com.aionemu.gameserver.model.gameobjects.Npc npc) {
  var quests=new TreeSet<>(QuestEngine.getInstance().getQuestNpc(npc.getNpcId()).getOnKillEvent());
  for(var drop:QuestService.getQuestDrop(npc.getNpcId()))quests.add(drop.getQuestId());
  quests.removeIf(quest->!open(bot,quest));
  return quests;
 }
 /** A kill that earned practice marks its quests. The mark waits in the bot's checkpoint for the turn-in. */
 static void practice(Player bot,com.aionemu.gameserver.model.gameobjects.Npc npc) {
  synchronized(bot) {
  int account=bot.getAccount().getId(),character=bot.getObjectId();
  try {
   var quests=objectives(bot,npc);if(quests.isEmpty())return;
   var cached=PlayerBotMetadata.cached(account,character,"growth");
   if(cached==null || cached.isEmpty())return;
   var profile=decode(cached);
   if(!profile.traitsAssigned() || profile.combatRank()==CombatRank.EXCELLENT)return;
   var marks=PlayerBotMetadata.load(account,character,"quest-practice",null);
   // Abandoned quests must not leave marks behind.
   boolean changed=marks.keySet().removeIf(key->key.toString().startsWith("quest.") && !open(bot,Integer.parseInt(key.toString().substring(6))));
   for(int quest:quests)changed|=marks.setProperty("quest."+quest,"1")==null;
   if(!changed)return;
   marks.setProperty("account",Integer.toString(account));marks.setProperty("character",Integer.toString(character));
   PlayerBotMetadata.save(account,character,"quest-practice",marks);
  } catch(IOException | RuntimeException e) {
   org.slf4j.LoggerFactory.getLogger(PlayerBotGrowth.class).warn("Cannot retain quest practice for companion {}",character,e);
  }
  }
 }
 /** QuestService calls this only when a turn-in changes REWARD to COMPLETE. The cleared mark, the XP and the quest
  * state commit in one checkpoint, so a retry cannot pay twice and a lost checkpoint restores all three. */
 public static void learnQuest(Player bot,int quest) {
  if(!bot.isPlayerBot())return;
  boolean helped=PlayerBotHelpObjectives.completed(bot,quest);
  synchronized(bot) {
  int account=bot.getAccount().getId(),character=bot.getObjectId();
  try {
   var cached=PlayerBotMetadata.cached(account,character,"growth");
   if(cached==null || cached.isEmpty())return;
   var marks=PlayerBotMetadata.load(account,character,"quest-practice",null);
   if(marks.remove("quest."+quest)==null)return;
   PlayerBotMetadata.save(account,character,"quest-practice",marks);
   if(!helped)learn(bot,25);
  } catch(IOException | RuntimeException e) {
   org.slf4j.LoggerFactory.getLogger(PlayerBotGrowth.class).warn("Cannot retain quest learning for companion {}",character,e);
  }
  }
 }
 private static final int HELP_CLAIMS=32;
 /** One 50 per recipient per objective. The claim commits with the XP in this bot's checkpoint; false when not paid. */
 static boolean learnHelp(Player bot,String claim) {
  synchronized(bot) {
  int account=bot.getAccount().getId(),character=bot.getObjectId();
  try {
   var cached=PlayerBotMetadata.cached(account,character,"growth");
   if(cached==null || cached.isEmpty())return false;
   var profile=decode(cached);
   if(!profile.traitsAssigned() || profile.combatRank()==CombatRank.EXCELLENT)return false;
   var marks=PlayerBotMetadata.load(account,character,"quest-practice",null);
   var claims=new ArrayList<>(Arrays.asList(marks.getProperty("help","").split(",")));claims.remove("");
   if(claims.contains(claim))return false;
   claims.add(claim);if(claims.size()>HELP_CLAIMS)claims.removeFirst();
   marks.setProperty("help",String.join(",",claims));
   marks.setProperty("account",Integer.toString(account));marks.setProperty("character",Integer.toString(character));
   PlayerBotMetadata.save(account,character,"quest-practice",marks);
   learn(bot,50);
   return true;
  } catch(IOException | RuntimeException e) {
   org.slf4j.LoggerFactory.getLogger(PlayerBotGrowth.class).warn("Cannot retain help learning for companion {}",character,e);
   return false;
  }
  }
 }
 static int mistakePercent(Player bot) {
  try {
   var rank=profile(bot).combatRank();
   return rank==null?0:rank.mistakePercent();
  } catch(IOException | RuntimeException e) { return 0; }
 }
 private static String title(Enum<?> value){String name=value.name();return name.charAt(0)+name.substring(1).toLowerCase(java.util.Locale.ROOT);}
 static String identityLine(String name,GearRarity rarity,CombatRank rank){return name+"|"+title(rarity)+"|"+title(rank);}
 /** "Great gear, Good rank" for a bot's hire question, or empty if it has no growth record. */
 static String identityText(Player bot){try{var p=profile(bot);return p.traitsAssigned()?title(p.autoEquipRarity())+" gear, "+title(p.combatRank())+" rank":"";}catch(IOException | RuntimeException none){return "";}}
 /** Lines of name|rarity|rank for the bots among these comma-separated names. Reads only the live world and each bot's cached profile. */
 public static String identities(String names) {
  var out=new StringBuilder();int asked=0;
  for(String name:names.split(",")) {
   if(name.isBlank() || name.length()>64 || ++asked>24)continue;
   var player=com.aionemu.gameserver.world.World.getInstance().getPlayer(name);
   if(player==null || !player.isPlayerBot())continue;
   try{var profile=profile(player);if(profile.traitsAssigned())out.append(identityLine(player.getName(),profile.autoEquipRarity(),profile.combatRank())).append('\n');}
   catch(IOException | RuntimeException noIdentity){/* A bot with no growth record simply shows no label. */}
  }
  return out.toString();
 }
 static GearRarity rarity(Player bot) {
  try { return profile(bot).autoEquipRarity(); } catch(IOException | RuntimeException e) { return null; }
 }

 private static Map<String,Object> project(Profile profile) {
  var result=new LinkedHashMap<String,Object>();
  result.put("growthVersion",profile.version());result.put("growthOrigin",profile.origin().name());
  result.put("growthTraitsAssigned",profile.traitsAssigned());
  if(profile.traitsAssigned()) {
   result.put("rolledAdvancedClass",profile.rolledAdvancedClass().name());result.put("autoEquipRarity",profile.autoEquipRarity().name());
   result.put("initialCombatRank",profile.initialCombatRank().name());result.put("combatRank",profile.combatRank().name());
   result.put("mistakePercent",profile.combatRank().mistakePercent());result.put("earnedLearningXp",profile.earnedLearningXp());
  }
  return Collections.unmodifiableMap(result);
 }

 public static Map<String,Object> snapshot(Player player) {
  try{return project(profile(player));}catch(IOException e){throw new IllegalStateException("Cannot read native companion growth",e);}
 }

 public static Map<String,Object> snapshot(int account,int character) {
  return snapshots(account,List.of(character)).get(character);
 }

 /** Account-scoped roster projection in bounded queries; offline reads neither write nor populate mutable actor state. */
 public static Map<Integer,Map<String,Object>> snapshots(int account,Collection<Integer> characters) {
  var ids=new ArrayList<>(new TreeSet<>(characters));
  if(!PlayerBotMetadata.validAccount(account) || ids.stream().anyMatch(id->id<=0))throw new IllegalArgumentException("Invalid growth snapshot owner/identity");
  if(ids.isEmpty())return Map.of();
  var profiles=new HashMap<Integer,Profile>();var uncached=new ArrayList<Integer>();
  try {
   for(int id:ids) {
    var cached=PlayerBotMetadata.cached(account,id,"growth");
    if(cached==null)uncached.add(id);else profiles.put(id,cached.isEmpty()?LEGACY:decode(cached));
   }
   if(!uncached.isEmpty())try(var connection=DatabaseFactory.getConnection()) {
    for(int start=0;start<uncached.size();start+=256) {
     var chunk=uncached.subList(start,Math.min(start+256,uncached.size()));
     try(var statement=connection.prepareStatement("SELECT p.id,m.account_id,m.settings,m.revision FROM players p JOIN playerbot_roster b ON b.player_id=p.id AND b.account_id=p.account_id "
      +"LEFT JOIN playerbot_metadata m ON m.player_id=p.id AND m.section='growth' WHERE p.account_id=? AND p.id IN ("+String.join(",",Collections.nCopies(chunk.size(),"?"))+")")) {
      statement.setInt(1,account);for(int i=0;i<chunk.size();i++)statement.setInt(i+2,chunk.get(i));
      try(var rows=statement.executeQuery()) {while(rows.next()) {
       int id=rows.getInt(1);if(!chunk.contains(id) || profiles.containsKey(id))throw new SQLException("Duplicate native growth identity");
       String text=rows.getString(3);Profile profile=LEGACY;
       if(rows.getObject(2)!=null) {
        if(rows.getInt(2)!=account || text==null || rows.getLong(4)<=0)throw new SQLException("Native growth account/revision mismatch");
        var json=Json.read(text);var values=new TreeMap<String,String>();
        if(json==null)throw new SQLException("Null growth metadata");
        for(var entry:json.entrySet()) {
         var value=entry.getValue();if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new SQLException("Invalid growth metadata value");
         values.put(entry.getKey(),value.getAsString());
        }
        PlayerBotMetadata.validate(account,id,"growth",values);profile=decode(values);
       } else if(text!=null)throw new SQLException("Growth metadata lacks native owner");
       profiles.put(id,profile);
      }}
     }
    }
   }
   if(profiles.size()!=ids.size())throw new SQLException("Companion growth identity unavailable to this account");
   var result=new LinkedHashMap<Integer,Map<String,Object>>();for(int id:ids)result.put(id,project(profiles.get(id)));
   return Collections.unmodifiableMap(result);
  } catch(SQLException|IOException|RuntimeException e) {throw new IllegalStateException("Cannot read native companion growth; no traits assigned",e);}
 }

 private PlayerBotGrowth() {}
}
