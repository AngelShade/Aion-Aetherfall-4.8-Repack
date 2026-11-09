package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.Equipment;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.skill.PlayerSkillList;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.item.enums.*;
import com.aionemu.gameserver.model.templates.npc.*;
import com.aionemu.gameserver.model.templates.recipe.RecipeTemplate;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.model.templates.spawns.*;
import com.aionemu.gameserver.model.templates.globaldrops.GlobalRule;
import com.aionemu.gameserver.model.templates.world.WorldMapTemplate;
import com.aionemu.gameserver.services.LootCodexService;

/** Real saved-goal -> codex acquisition -> world catalogue -> hunt decision, without World/IDs/SQL/tasks. */
public final class PlayerBotGoalHuntCheck {
 private static int checks;
 private static final int MAP=210010000,INSTANCE=310010000,ITEM=100000001;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static String computed(){var m=java.util.regex.Pattern.compile("computed=(\\d+)").matcher(PlayerBotUpgradeHunts.stats());return m.find()?m.group(1):"?";}
 private static Field field(Class<?> type,String name)throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 private static void set(Object object,String name,Object value)throws Exception {field(object.getClass(),name).set(object,value);}
 private static final class Bot extends Player {
  int level=20,map=MAP;boolean spawned,world;Outfit outfit;Cube cube;PlayerSkillList skills;Bot(){super(null,null);}
  @Override public int getObjectId(){return 123;}
  @Override public byte getLevel(){return (byte)level;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public TribeClass getTribe(){return TribeClass.PC;}
  @Override public int getWorldId(){return map;}
  @Override public float getX(){return 0;}
  @Override public float getY(){return 0;}
  @Override public float getZ(){return 0;}
  @Override public boolean isInInstance(){return false;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public PlayerClass getPlayerClass(){return PlayerClass.GLADIATOR;}
  @Override public Gender getGender(){return Gender.MALE;}
  @Override public Equipment getEquipment(){return outfit;}
  @Override public Storage getInventory(){return cube;}
  @Override public PlayerSkillList getSkillList(){return skills;}
  @Override public int getInstanceId(){return 1;}
  @Override public boolean isWorldBot(){return world;}
  @Override public int getPlayerBotOwnerId(){return 0;}
  @Override public com.aionemu.gameserver.model.account.Account getAccount(){return new com.aionemu.gameserver.model.account.Account(-1000001);}
 }
 private static final class Outfit extends Equipment {
  List<Item> items=new ArrayList<>();Outfit(){super(null);}
  @Override public List<Item> getEquippedItems(){return items;}
 }
 private static final class Cube extends PlayerStorage {
  List<Item> items=new ArrayList<>();Cube(){super(null,StorageType.CUBE);}
  @Override public List<Item> getItems(){return items;}
 }
 private static final Map<Integer,ItemTemplate> ITEMS=new HashMap<>();
 private static final List<GlobalRule> RULES=new ArrayList<>();
 private static void warmUpgrades()throws Exception {
  long until=System.nanoTime()+10000000000L;while(!PlayerBotUpgradeHunts.warm() && System.nanoTime()<until)Thread.sleep(5);
  check(PlayerBotUpgradeHunts.warm(),"Upgrade index warms off the bot path");
 }
 private static ItemTemplate item(int id,int required,int damage)throws Exception {
  var t=new ItemTemplate();set(t,"itemId",id);set(t,"name","Hunt upgrade "+id);set(t,"itemGroup",ItemGroup.GREATSWORD);set(t,"level",20);set(t,"itemQuality",ItemQuality.RARE);
  set(t,"useLimits",new ItemUseLimits());var levels=new byte[PlayerClass.values().length];Arrays.fill(levels,(byte)required);set(t,"levelRestrictions",levels);
  var weapon=new WeaponStats();set(weapon,"minDamage",damage);set(weapon,"maxDamage",damage);set(t,"weaponStats",weapon);set(t,"maxTuneCount",0);ITEMS.put(id,t);return t;
 }
 @SuppressWarnings("unchecked") private static PlayerBotWorldActivities.Goal choose(Bot bot)throws Exception {
  return choose(bot,true);
 }
 private static PlayerBotWorldActivities.Goal choose(Bot bot,boolean clearClaims)throws Exception {
  if(clearClaims)((Map<?,?>)field(PlayerBotWorldActivities.class,"GOAL_CLAIMS").get(null)).clear();
  Class<?> type=Class.forName(PlayerBotWorldActivities.class.getName()+"$State");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
  var hunt=PlayerBotWorldActivities.class.getDeclaredMethod("goalHunt",Player.class,type,long.class,PlayerBotWorldTravel.Distances.class);hunt.setAccessible(true);
  var edge=new PlayerBotWorldTravel.Edge(new PlayerBotWorldTravel.Place(MAP,1,0,0,0),MAP+1,1);
  return (PlayerBotWorldActivities.Goal)hunt.invoke(null,bot,constructor.newInstance(),1000L,new PlayerBotWorldTravel.Distances(MAP,0,0,0,Map.of(MAP+1,List.of(edge))));
 }
 @SuppressWarnings("unchecked") private static void upgrades(Bot bot,PlayerBotGearPolicy.State state,Unsafe unsafe)throws Exception {
  bot.level=20;bot.outfit=new Outfit();bot.cube=new Cube();bot.skills=new PlayerSkillList();
  bot.setGameStats((com.aionemu.gameserver.model.stats.container.PlayerGameStats)unsafe.allocateInstance(com.aionemu.gameserver.model.stats.container.PlayerGameStats.class));
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);set(session,"role",PlayerBotRules.Role.MELEE);set(state,"session",session);
  set(state,"settings",PlayerBotGearPolicy.DEFAULT.withWeapon("2H_SWORD"));set(state,"references",new HashMap<>());set(state,"build",PlayerBotStigmaBuilds.AUTO);
  var worn=new Item(901,item(ITEM+20,1,10),1,false,0);worn.setEquipmentSlot(ItemSlot.MAIN_OR_SUB.getSlotIdMask());bot.outfit.items.add(worn);
  for(int id=11;id<=18;id++) {
   npc(id,id==14?25:20,NpcRating.NORMAL,TribeClass.MONSTER);
   spawn(id==15 || id==18?MAP+1:MAP,id,id==11?1500:id==16?30:1);
   item(ITEM+id,id==13?21:1,id==12 || id==18?1:id==13 || id==14?200:id==15?300:id==17?20:30);
  }
  index();warmUpgrades();
  var possible=PlayerBotUpgradeHunts.candidates(bot).hunts();var ids=possible.stream().map(PlayerBotUpgradeHunts.Opportunity::npc).toList();
  check(ids.containsAll(List.of(11,15,16,17)),"Wearable improving solo world drops are indexed: "+ids+"; "+PlayerBotUpgradeHunts.stats()+"; "+PlayerBotLoadouts.key(bot,PlayerBotRules.Role.MELEE));
  check(!ids.contains(12),"Non-upgrade item ignored");
  String computedBefore=computed();var again=PlayerBotUpgradeHunts.candidates(bot).hunts();
  check(again.equals(possible) && computed().equals(computedBefore),"An unchanged bot reuses its cached upgrade list: "+PlayerBotUpgradeHunts.stats());
  check(!ids.contains(13),"Required level above bot ignored even when template level is low");
  check(!ids.contains(14),"A monster failing fit cannot supply a near-term hunt");
  state.goals.clear();state.goals.put("MAIN_HAND",(ITEM+3)+":npc:3");
  var before=new HashMap<>(state.goals);var chosen=choose(bot);
  check(chosen!=null && chosen.place().npc()==16,"No saved solo source selects local upgrade: map then gain then distance");
  chosen=choose(bot,false);check(chosen!=null && chosen.place().npc()==11,"Claimed upgrade prey stays exclusive; greater gain wins beyond the near radius");
  check(state.goals.equals(before),"Hunt fallback never changes persisted goal choices");
  // Local gain beats proximity even when the best remaining drop is outside the 900m near query.
  bot.outfit.items.clear();var current=new Item(902,item(ITEM+21,1,25),1,false,0);current.setEquipmentSlot(ItemSlot.MAIN_OR_SUB.getSlotIdMask());bot.outfit.items.add(current);
  check(PlayerBotUpgradeHunts.candidates(bot).hunts().stream().noneMatch(h->h.npc()==17),"Worn gear is re-read on every check");
  state.goals.put("MAIN_HAND",ITEM+":recipe:1");chosen=choose(bot);
  check(chosen!=null && chosen.place().npc()==2,"Saved solo goal wins over a much larger near-term upgrade");
  state.goals.put("MAIN_HAND",(ITEM+18)+":npc:18");chosen=choose(bot);
  check(chosen!=null && chosen.place().npc()==18,"Nonresident keeps its allowed cross-map saved solo goal");
  for(String setting:List.of("ENABLED","WORLD_ENABLED","POPULATION_ENABLED","POPULATION_PLAYER_PRESENCE"))field(PlayerBotConfig.class,setting).set(null,true);
  field(PlayerBotPlayerPresence.class,"observed").set(null,new PlayerBotPlayerPresence.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(MAP,0,0,0)),40,600));
  bot.world=true;bot.spawned=true;
  check(PlayerBotPlayerPresence.resident(bot),"Fixture enters the real resident policy");
  chosen=choose(bot);check(chosen!=null && chosen.place().map()==MAP && chosen.place().npc()==16,"Resident with only a cross-map saved solo source takes a local upgrade");
  check(PlayerBotUpgradeHunts.candidates(bot).hunts().stream().allMatch(h->h.map()==MAP),"Resident upgrade catalogue remains on its own map");
  var loot=new Item(903,ITEMS.get(ITEM+16),1,false,0);bot.cube.items.add(loot);
  var rank=(com.aionemu.gameserver.model.gameobjects.player.AbyssRank)unsafe.allocateInstance(com.aionemu.gameserver.model.gameobjects.player.AbyssRank.class);
  field(com.aionemu.gameserver.model.gameobjects.player.Player.class,"abyssRank").set(bot,rank);
  set(rank,"rank",com.aionemu.gameserver.utils.stats.AbyssRankEnum.GRADE9_SOLDIER);
  check(PlayerBotEquipment.upgrades(bot,PlayerBotRules.Role.MELEE).stream().anyMatch(u->u.item()==loot),"Normal inventory gear check selects the looted improving item");
  item(ITEM+19,1,500);npc(19,20,NpcRating.NORMAL,TribeClass.MONSTER);spawn(MAP,19,1);spawn(MAP+1,19,1);
  String xml="<global_rules><gd_rule chance=\"100\"><gd_maps><gd_map map_id=\""+(MAP+1)+"\"/></gd_maps><gd_items><gd_item id=\""+(ITEM+19)+"\"/></gd_items></gd_rule></global_rules>";
  var nativeRules=(GlobalDropData)jakarta.xml.bind.JAXBContext.newInstance(GlobalDropData.class).createUnmarshaller().unmarshal(new java.io.StringReader(xml));
  RULES.addAll(nativeRules.getAllRules());set(RULES.getFirst(),"restrictionRace",GlobalRule.RestrictionRace.ASMODIANS);
  index();warmUpgrades();bot.world=false;bot.spawned=false;
  check(PlayerBotUpgradeHunts.candidates(bot).hunts().stream().noneMatch(h->h.npc()==19),"Native world-rule killer race restriction is honored");
  set(RULES.getFirst(),"restrictionRace",null);index();warmUpgrades();
  var worldLoot=PlayerBotUpgradeHunts.candidates(bot).hunts().stream().filter(h->h.npc()==19).toList();
  check(!worldLoot.isEmpty() && worldLoot.stream().allMatch(h->h.map()==MAP+1 && h.places().stream().allMatch(p->p.map()==MAP+1)),"Broad world loot keeps only its native rule's matching spawn points");
  state.goals.put("MAIN_HAND",(ITEM+3)+":npc:3");chosen=choose(bot);check(chosen!=null && chosen.place().map()==MAP,"Own-map upgrade wins over a greater cross-map world drop");
  bot.world=true;bot.spawned=true;check(PlayerBotUpgradeHunts.candidates(bot).hunts().stream().noneMatch(h->h.npc()==19),"Resident excludes a world-rule drop available only on another map");
  check(PlayerBotWorldActivities.huntStats().contains("upgrade hunts started="),"Live log identifies near-term hunts");
 }
 private static final Map<Integer,NpcTemplate> NPCS=new HashMap<>();
 private static final Map<Integer,Map<Integer,List<SpawnGroup>>> SPAWNS=new HashMap<>();
 private static NpcTemplate npc(int id,int level,NpcRating rating,TribeClass tribe)throws Exception {
  var n=new NpcTemplate();set(n,"npcId",id);set(n,"level",(byte)level);set(n,"rating",rating);set(n,"tribe",tribe);set(n,"name","Monster "+id);set(n,"npcTemplateType",NpcTemplateType.MONSTER);set(n,"groupDrop",GroupDropType.AGRINT);NPCS.put(id,n);return n;
 }
 private static void spawn(int map,int npc,float x)throws Exception {
  var group=new SpawnGroup(map,npc,60,null);new SpawnTemplate(group,x,0,0,(byte)0,0,null,0);
  SPAWNS.computeIfAbsent(map,k->new HashMap<>()).computeIfAbsent(npc,k->new ArrayList<>()).add(group);
 }
 private static void index()throws Exception {
  var recipe=new RecipeTemplate();set(recipe,"race",Race.ELYOS);
  DataManager.RECIPE_DATA=new RecipeData(){@Override public RecipeTemplate getRecipeTemplateById(int id){return recipe;}};
  DataManager.SKILL_DATA=new SkillData(){@Override public SkillTemplate getSkillTemplate(int id){return null;}};
  DataManager.ITEM_DATA=new ItemData(){@Override public ItemTemplate getItemTemplate(int id){return ITEMS.get(id);}@Override public Collection<ItemTemplate> getItemTemplates(){return ITEMS.values();}};
  DataManager.GLOBAL_DROP_DATA=new GlobalDropData(){@Override public List<GlobalRule> getAllRules(){return RULES;}};
  DataManager.GLOBAL_EXCLUSION_DATA=new GlobalNpcExclusionData();
  DataManager.NPC_DATA=new NpcData(){@Override public NpcTemplate getNpcTemplate(int id){return NPCS.get(id);}};
  var world=new WorldMapTemplate();set(world,"mapId",MAP);set(world,"dropWorldType",com.aionemu.gameserver.world.WorldDropType.ELYSEA);var other=new WorldMapTemplate();set(other,"mapId",MAP+1);set(other,"dropWorldType",com.aionemu.gameserver.world.WorldDropType.ELYSEA);var instance=new WorldMapTemplate();set(instance,"mapId",INSTANCE);set(instance,"instance",true);
  DataManager.WORLD_MAPS_DATA=new WorldMapsData(){@Override public Iterator<WorldMapTemplate> iterator(){return List.of(world,other,instance).iterator();}@Override public WorldMapTemplate getTemplate(int map){return map==MAP?world:map==MAP+1?other:instance;}};
  DataManager.SPAWNS_DATA=new SpawnsData(){@Override public List<SpawnGroup> getSpawnsForNpc(int map,int npc){return SPAWNS.getOrDefault(map,Map.of()).getOrDefault(npc,List.of());}@Override public List<SpawnGroup> getSpawnsByWorldId(int map){return SPAWNS.getOrDefault(map,Map.of()).values().stream().flatMap(Collection::stream).toList();}};
  DataManager.TRIBE_RELATIONS_DATA=new TribeRelationsData(){
   @Override public boolean isHostileRelation(TribeClass a,TribeClass b){return a==TribeClass.MONSTER;}
   @Override public boolean isAggressiveRelation(TribeClass a,TribeClass b){return false;}
   @Override public boolean isFriendlyRelation(TribeClass a,TribeClass b){return false;}
  };
  Class<?> source=Class.forName(LootCodexService.class.getName()+"$Source"),index=Class.forName(LootCodexService.class.getName()+"$Index");
  var sourceConstructor=source.getDeclaredConstructors()[0];sourceConstructor.setAccessible(true);
  var sources=new HashMap<Integer,List<Object>>();
  sources.put(ITEM,List.of(sourceConstructor.newInstance("recipe",1,"Recipe first","Craft",-1f,1L,1L),sourceConstructor.newInstance("npc",1,"Normal","Loot",100f,1L,1L),sourceConstructor.newInstance("npc",2,"Another normal","Loot",100f,1L,1L)));
  for(int id=3;id<=10;id++)sources.put(ITEM+id,List.of(sourceConstructor.newInstance("npc",id,"Monster","Loot",100f,1L,1L)));
  for(int id=11;id<=18;id++)sources.put(ITEM+id,List.of(sourceConstructor.newInstance("npc",id,"Upgrade monster","Loot",100f,1L,1L)));
  var data=index.getDeclaredConstructors()[0];data.setAccessible(true);
  var maps=new HashMap<Integer,Set<Integer>>();for(int id:NPCS.keySet())maps.put(id,Set.of(MAP));
  field(LootCodexService.class,"index").set(null,data.newInstance(DataManager.ITEM_DATA,DataManager.CUSTOM_NPC_DROP,DataManager.GLOBAL_DROP_DATA,DataManager.SPAWNS_DATA,List.of(),List.of(),sources,Map.of(),Map.of(),maps,Map.of(),Map.of(),Map.of()));
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  com.aionemu.commons.configuration.ConfigurableProcessor.process(new Properties(),PlayerBotConfig.class,com.aionemu.gameserver.configs.main.PlayerBotMerchantConfig.class);
  var uf=field(Unsafe.class,"theUnsafe");var unsafe=(Unsafe)uf.get(null);var bot=(Bot)unsafe.allocateInstance(Bot.class);bot.level=20;bot.map=MAP;
  npc(1,22,NpcRating.NORMAL,TribeClass.MONSTER);npc(2,20,NpcRating.NORMAL,TribeClass.MONSTER);npc(3,20,NpcRating.NORMAL,TribeClass.MONSTER);
  npc(4,22,NpcRating.ELITE,TribeClass.MONSTER);npc(5,25,NpcRating.NORMAL,TribeClass.MONSTER);npc(6,20,NpcRating.NORMAL,TribeClass.GENERAL);
  npc(7,9,NpcRating.NORMAL,TribeClass.MONSTER);npc(8,20,NpcRating.HERO,TribeClass.MONSTER);npc(9,20,NpcRating.NORMAL,TribeClass.MONSTER);npc(10,31,NpcRating.NORMAL,TribeClass.MONSTER);
  spawn(MAP,1,200);spawn(MAP,2,20);spawn(INSTANCE,3,10);spawn(MAP,4,50);for(int id=5;id<=8;id++)spawn(MAP,id,100);spawn(MAP,10,100);
  index();
  var state=(PlayerBotGearPolicy.State)unsafe.allocateInstance(PlayerBotGearPolicy.State.class);set(state,"goals",new HashMap<String,String>());
  ((Map<Integer,PlayerBotGearPolicy.State>)field(PlayerBotGearPolicy.class,"STATES").get(null)).put(bot.getObjectId(),state);
  state.goals.put("MAIN_HAND",ITEM+":recipe:1");
  check(LootCodexService.acquisitions(ITEM,Race.ELYOS).getFirst().kind().equals("recipe"),"Fixture really has a recipe before monster sources");
  check(PlayerBotGearPolicy.goalDrops(bot).equals(List.of(1,2)),"Saved recipe goal resolves every codex monster source");
  var cached=PlayerBotGearPolicy.goalSources(bot).get(ITEM);check(PlayerBotGearPolicy.goalSources(bot).get(ITEM)==cached,"Warm item/race lookup reuses immutable source list");
  state.goals.put("OFF_HAND",ITEM+":quest:99");check(PlayerBotGearPolicy.goalSources(bot).size()==1,"Duplicate slots share one item lookup");
  var candidates=PlayerBotGoalHunts.candidates(bot);check(candidates.solo().equals(List.of(1,2)),"Both valid normal world sources are solo candidates");
  Class<?> type=Class.forName(PlayerBotWorldActivities.class.getName()+"$State");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);var huntState=constructor.newInstance();
  var hunt=PlayerBotWorldActivities.class.getDeclaredMethod("goalHunt",Player.class,type,long.class,PlayerBotWorldTravel.Distances.class);hunt.setAccessible(true);
  // Avoid player-presence/runtime inspection; the normal presence policy is tested separately.
  var presence=field(PlayerBotConfig.class,"POPULATION_PLAYER_PRESENCE");presence.set(null,false);
  var chosen=(PlayerBotWorldActivities.Goal)hunt.invoke(null,bot,huntState,1000L,new PlayerBotWorldTravel.Distances(MAP,0,0,0,Map.of()));
  check(chosen!=null && chosen.place().npc()==2,"Real hunt entry point chooses the nearer reachable normal monster");
  var visitors=new PlayerBotWorldActivities.Visitors();var farther=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(MAP,1,200,0,0),200);
  check(visitors.nearest(List.of(farther,chosen))==chosen,"Nearest gear destination keeps existing visitor admission");
  for(int i=0;i<PlayerBotWorldActivities.visitorLimit(chosen);i++)visitors.replace(null,chosen);
  check(visitors.nearest(List.of(farther,chosen))==farther,"Saturated destination still observes its native visitor cap");
  state.goals.clear();state.goals.put("MAIN_HAND",(ITEM+3)+":npc:3");candidates=PlayerBotGoalHunts.candidates(bot);
  check(candidates.solo().isEmpty() && candidates.rejected().contains(PlayerBotGoalHunts.Fit.ONLY_INSTANCES),"Instance-only source is skipped explicitly");
  state.goals.put("MAIN_HAND",(ITEM+4)+":npc:4");candidates=PlayerBotGoalHunts.candidates(bot);
  check(candidates.solo().isEmpty() && candidates.help().equals(List.of(4)),"Elite above level within +4 goes only to help path");
  var help=PlayerBotGroupNeeds.gear(bot);check(help!=null && help.gear() && help.npc()==4 && help.map()==MAP,"Hard local item source uses native field help-request terms");
  bot.map=MAP+1;check(PlayerBotGroupNeeds.gear(bot)==null,"Cross-map hard source is deferred rather than advertised as a local field request");bot.map=MAP;
  check(PlayerBotFindGroup.goalHelpRoom(false,false,0,3),"First request fits normal tab cap");
  check(PlayerBotFindGroup.goalHelpRoom(true,true,0,3),"A free helper offer may become a real request");
  check(!PlayerBotFindGroup.goalHelpRoom(true,false,0,3),"An open request or instance run is preserved, never duplicated");
  check(!PlayerBotFindGroup.goalHelpRoom(false,false,3,3),"Existing per-faction recruitment cap still applies");
  check(PlayerBotGroupNeeds.unfinished(bot,help),"Wanted hard gear request stays open");
  state.goals.clear();check(!PlayerBotGroupNeeds.unfinished(bot,help),"Obtained/removed goal closes its request");
  var mixed=PlayerBotGoalHunts.candidates(Map.of(ITEM,List.of(1,4)),id->PlayerBotGoalHunts.fit(bot,id));check(mixed.help().isEmpty(),"An item with a solo alternative does not request help");
  check(PlayerBotGoalHunts.fit(bot,5)==PlayerBotGoalHunts.Fit.ABOVE_SOLO_LEVEL,"Normal above +4 rejected");
  // Hard sources beyond elites above level still ask a group, as the help request did before the split.
  state.goals.clear();state.goals.put("MAIN_HAND",(ITEM+8)+":npc:8");
  help=PlayerBotGroupNeeds.gear(bot);check(help!=null && help.npc()==8,"A hero monster on the map asks for a group");
  state.goals.clear();state.goals.put("MAIN_HAND",(ITEM+5)+":npc:5");
  help=PlayerBotGroupNeeds.gear(bot);check(help!=null && help.npc()==5,"A normal monster five levels above asks for a group");
  check(PlayerBotGroupNeeds.unfinished(bot,help),"That request stays open while the item is wanted");
  state.goals.clear();state.goals.put("MAIN_HAND",(ITEM+10)+":npc:10");
  check(PlayerBotGroupNeeds.gear(bot)==null,"A monster eleven levels above is beyond any party of the bot's range");
  check(PlayerBotGoalHunts.fit(bot,6)==PlayerBotGoalHunts.Fit.NOT_HOSTILE,"Friendly source rejected");
  check(PlayerBotGoalHunts.fit(bot,7)==PlayerBotGoalHunts.Fit.NO_XP,"No-XP source rejected separately");
  check(PlayerBotGoalHunts.fit(bot,8)==PlayerBotGoalHunts.Fit.UNSUPPORTED,"Hero cannot become solo prey or an elite help request");
  check(PlayerBotGoalHunts.fit(bot,9)==PlayerBotGoalHunts.Fit.NO_WORLD_PLACE,"Unspawned monster is not mislabeled as instance-only");
  check(PlayerBotGoalHunts.candidates(Map.of(),id->PlayerBotGoalHunts.Fit.SOLO).rejected().contains(PlayerBotGoalHunts.Fit.NO_MONSTER_SOURCE),"No sources have their own reason");
  bot.level=22;check(PlayerBotGoalHunts.fit(bot,4)==PlayerBotGoalHunts.Fit.SOLO,"Elite at bot level retains existing solo admission");
  String stats=PlayerBotWorldActivities.huntStats();check(stats.contains("help requests opened=") && stats.contains("already claimed=") && !stats.contains("no monster it can fight alone"),"Live report has split rejection and help fields");
  // Arriving on the hunting ground starts a stay: the goal ends but the prey and its claim remain until time is up.
  {
   var stay=constructor.newInstance();var place=new PlayerBotWorldTravel.Place(MAP,2,20,0,0);var huntGoal=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,place,20);
   set(stay,"goal",huntGoal);set(stay,"goalHunt",huntGoal);
   var start=PlayerBotWorldActivities.class.getDeclaredMethod("startHuntStay",type,long.class);start.setAccessible(true);
   var keep=PlayerBotWorldActivities.class.getDeclaredMethod("huntStay",Player.class,type,long.class);keep.setAccessible(true);
   var claims=(Map<Integer,Object>)field(PlayerBotWorldActivities.class,"GOAL_CLAIMS").get(null);
   check((boolean)start.invoke(null,stay,5000L),"Arrival at the hunt's spawn starts a stay");
   check(field(type,"goal").get(stay)==null && field(type,"goalHunt").get(stay)==huntGoal && claims.get(2)==stay,"The trip ends but the prey and its claim remain");
   check((long)field(type,"huntUntil").get(stay)==5000L+com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_HUNT_STAY_SECONDS*1000L,"Stay lasts the configured time");
   check((boolean)keep.invoke(null,bot,stay,6000L) && String.valueOf(field(type,"why").get(stay)).startsWith("hunting "),"Inside the leash and the time, the bot keeps hunting");
   bot.map=MAP+1;check(!(boolean)keep.invoke(null,bot,stay,6000L) && field(type,"goalHunt").get(stay)==null && !claims.containsKey(2),"Leaving the map ends the stay and frees the claim");bot.map=MAP;
   set(stay,"goal",huntGoal);set(stay,"goalHunt",huntGoal);start.invoke(null,stay,5000L);
   check(!(boolean)keep.invoke(null,bot,stay,5000L+com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_HUNT_STAY_SECONDS*1000L) && field(type,"goalHunt").get(stay)==null,"Time up ends the stay");
   set(stay,"goal",huntGoal);set(stay,"goalHunt",huntGoal);start.invoke(null,stay,5000L);
   var far=new PlayerBotWorldTravel.Place(MAP,2,200,0,0);var farGoal=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,far,200);set(stay,"goalHunt",farGoal);
   check(!(boolean)keep.invoke(null,bot,stay,6000L),"A chase past the leash ends the stay");
   var quest=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(MAP,1,200,0,0),200);set(stay,"goal",quest);set(stay,"goalHunt",huntGoal);
   check(!(boolean)start.invoke(null,stay,5000L),"Arriving on an ordinary trip does not start a hunting stay");
   claims.remove(2);
  }
  upgrades(bot,state,unsafe);
  System.out.println("OK: "+checks+" goal acquisition/world spawn/solo selection/local help checks; no World/IDs/SQL/tasks");
 }
}
