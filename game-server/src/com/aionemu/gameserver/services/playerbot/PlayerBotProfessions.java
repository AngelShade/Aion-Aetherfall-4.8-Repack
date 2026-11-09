package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.controllers.GatherableController;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.craft.Profession;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.gather.GatherableTemplate;
import com.aionemu.gameserver.model.templates.recipe.RecipeTemplate;
import com.aionemu.gameserver.services.craft.CraftService;
import com.aionemu.gameserver.services.craft.CraftSkillUpdateService;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.skillengine.task.GatheringTask;
import com.aionemu.gameserver.services.trade.EconomyPrices;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestCrafting.*;

/** One saved world trade; native inventory/skills own every outcome. */
public final class PlayerBotProfessions {
 static final class State {
  final Player bot;final Properties values;int primary,cursor,recipe,failures,zeroXp;String style="",reason="Awaiting trade assignment";
  long next,planAt,activeUntil,nextAwake,marketWait,lastGather;volatile boolean admitted;int zeroAt;
  volatile Plan plan;volatile Map<Integer,Long> gather=Map.of(),inputs=Map.of(),escrow=Map.of();
  boolean awakePending;long pendingSince,stationTick;PlayerBotProfessionTravel.Trip trip;
  PlayerBotProfessionQualification.Requirement qualification;
  State(Player bot,Properties values){this.bot=bot;this.values=values;primary=Integer.parseInt(values.getProperty("primary","0"));style=values.getProperty("style","");
   next=number("next");nextAwake=number("nextAwake");recipe=(int)number("recipe");marketWait=number("marketWait");}
  long number(String key){return Long.parseLong(values.getProperty(key,"0"));}
  void number(String key,long value){values.setProperty(key,Long.toString(value));}
  void save(){values.setProperty("version","1");values.setProperty("account",Integer.toString(bot.getAccount().getId()));values.setProperty("character",Integer.toString(bot.getObjectId()));
   values.setProperty("primary",Integer.toString(primary));values.setProperty("style",style);number("next",next);number("nextAwake",nextAwake);number("recipe",recipe);number("marketWait",marketWait);
   try{PlayerBotMetadata.save(bot.getAccount().getId(),bot.getObjectId(),"profession",values);}catch(java.io.IOException e){throw new IllegalStateException("Cannot queue world profession",e);}}
 }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 private static final Map<Race,Map<Integer,Integer>> COUNTS=new EnumMap<>(Race.class);
 private static final Set<Integer> CENSUS=new HashSet<>();
 private static final Set<String> RESTORED=ConcurrentHashMap.newKeySet();
 private static final ThreadLocal<Player> BACKGROUND=new ThreadLocal<>();
 private static Map<Integer,Integer> WEIGHTS=Map.of();private static List<PlayerBotProfessionPolicy.Style> GATHER_STYLES=List.of(),CRAFT_STYLES=List.of();
 private static long planSecond,turnSecond;private static int plans,turns;private static volatile boolean prepared;
 private static final java.util.concurrent.atomic.AtomicLong ERRORS=new java.util.concurrent.atomic.AtomicLong();
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PlayerBotProfessions.class);
 private static final LongAdder TURNS=new LongAdder(),TRAINED=new LongAdder(),DESIGNS=new LongAdder(),ATTEMPTS=new LongAdder(),FAILURES=new LongAdder(),BLOCKED=new LongAdder(),SPENT=new LongAdder();
 private static final LongAdder PLAN_NANOS=new LongAdder();
 private static final LongAdder WALKED=new LongAdder(),TRIP_FALLBACKS=new LongAdder(),TRIP_STALLS=new LongAdder(),QUALIFIED=new LongAdder();
 /** A bad setting or data set pauses this feature and says why; it never stops the server starting. */
 static void prepare(){if(!enabled())return;try{validateConfig();PlayerBotProfessionCatalog.prepare();PlayerBotProfessionQualification.prepare();PlayerBotProfessionTravel.prepare();prepared=true;}catch(RuntimeException error){prepared=false;log.error("World bot professions are paused",error);}}
 static void validateConfig() {
  WEIGHTS=PlayerBotProfessionPolicy.weights(PlayerBotEconomyConfig.PROFESSIONS_PRIMARY_WEIGHTS);
  GATHER_STYLES=PlayerBotProfessionPolicy.styles(PlayerBotEconomyConfig.PROFESSIONS_GATHERER_STYLES,true);CRAFT_STYLES=PlayerBotProfessionPolicy.styles(PlayerBotEconomyConfig.PROFESSIONS_CRAFTER_STYLES,false);
  if(PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_TURNS_PER_SECOND<1 || PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_TURNS_PER_SECOND>20
   || PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_INTERVAL_SECONDS<60 || PlayerBotEconomyConfig.PROFESSIONS_GATHER_SECONDS<30
   || PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_GATHER_BATCH<1 || PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_GATHER_BATCH>20
   || PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_CRAFT_BATCH<1 || PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_CRAFT_BATCH>10
   || PlayerBotEconomyConfig.PROFESSIONS_PLANS_PER_SECOND<1 || PlayerBotEconomyConfig.PROFESSIONS_PLANS_PER_SECOND>20
   || PlayerBotEconomyConfig.PROFESSIONS_RECIPES_PER_PLAN<1 || PlayerBotEconomyConfig.PROFESSIONS_RECIPES_PER_PLAN>64
   || PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN<1 || PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN>64
   || PlayerBotEconomyConfig.PROFESSIONS_NEEDS_PER_PLAN<1 || PlayerBotEconomyConfig.PROFESSIONS_NEEDS_PER_PLAN>16
   || PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS<1 || PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS>PlayerBotEconomyConfig.PROFESSIONS_WORK_SLICE_SECONDS
   || PlayerBotEconomyConfig.PROFESSIONS_TRIP_STALL_SECONDS<1 || PlayerBotEconomyConfig.PROFESSIONS_TRIP_STALL_SECONDS>PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS
   || PlayerBotEconomyConfig.PROFESSIONS_MARKET_CACHE_ENTRIES<1 || PlayerBotEconomyConfig.PROFESSIONS_MARKET_CACHE_ENTRIES>1024
   || PlayerBotEconomyConfig.PROFESSIONS_MARKET_VARIANTS_PER_MINUTE<1 || PlayerBotEconomyConfig.PROFESSIONS_MARKET_VARIANTS_PER_MINUTE>120
   || PlayerBotEconomyConfig.PROFESSIONS_PROFIT_SUCCESS_PERCENT<1 || PlayerBotEconomyConfig.PROFESSIONS_PROFIT_SUCCESS_PERCENT>100)throw new IllegalArgumentException("Invalid bounded profession configuration");
 }
 public static void validate(Map<String,String> values) {
  if(!"1".equals(values.get("version")))throw new IllegalArgumentException("Invalid profession version");int primary=Integer.parseInt(values.get("primary"));
  if(Profession.getBySkillId(primary)==null)throw new IllegalArgumentException("Invalid native primary trade");
  // A style renamed in settings falls back when used; it must never stop a saved character loading.
  if(!values.getOrDefault("style","").matches("[a-z_]{1,24}"))throw new IllegalArgumentException("Invalid saved trade style");
  int entries=0;for(var e:values.entrySet())if(!Set.of("version","account","character","primary","style","day").contains(e.getKey())){
   if(e.getKey().startsWith("offered.") || e.getKey().startsWith("cost.")){if(Integer.parseInt(e.getKey().substring(e.getKey().indexOf('.')+1))<=0 || ++entries>64)throw new IllegalArgumentException("Invalid trade item ledger");}
   else if(!Set.of("next","nextAwake","recipe","marketWait","spent","trainingSpent","trainingCount","loss","attemptHour","attempts","assignedAt").contains(e.getKey()))throw new IllegalArgumentException("Unexpected profession field");
   if(Long.parseLong(e.getValue())<0)throw new IllegalArgumentException("Negative trade ledger");
  }
 }
 static void validateUpdate(Map<String,String> before,Map<String,String> after){if(!before.isEmpty() && (!Objects.equals(before.get("primary"),after.get("primary")) || !Objects.equals(before.get("style"),after.get("style"))))throw new IllegalArgumentException("Saved world trade/style is immutable");}
 /** Runs where road duty initializes, on the resident loader. Bad saved data means ordinary play with no trade work. */
 static void initialize(PlayerBotSession session) {
  var bot=session.bot();if(!enabled() || !prepared || !session.autonomous() || !bot.isWorldBot() || PlayerBotRoadDuty.hasCareer(bot) || STATES.containsKey(bot.getObjectId()))return;
  try {
   var values=PlayerBotMetadata.load(bot.getAccount().getId(),bot.getObjectId(),"profession",null);var s=new State(bot,values);
   if(s.primary!=0)census(bot.getObjectId(),bot.getRace(),PlayerBotMetadata.values(values));
   // Spread first turns over the whole interval so a server start does not ask for them all at once.
   if(s.next==0)s.next=System.currentTimeMillis()+Math.floorMod(PlayerBotProfessionPolicy.mix(bot.getObjectId()),PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_INTERVAL_SECONDS*1000L);
   STATES.put(bot.getObjectId(),s);if(bot.getLevel()>=10)assign(s);
  }catch(java.io.IOException|RuntimeException error){log.warn("World bot {} has no trade work this run",bot.getObjectId(),error);}
 }
 static State state(Player bot){return STATES.get(bot.getObjectId());}
 static boolean enabled(){return PlayerBotEconomyConfig.ECONOMY_ENABLED && PlayerBotEconomyConfig.PROFESSIONS_ENABLE;}
 static boolean eligible(PlayerBotSession s){return enabled() && prepared && s!=null && s.autonomous() && !s.closing() && !PlayerBotRoadDuty.hasCareer(s.bot()) && s.bot().getLevel()>=10 && !s.bot().isInInstance();}
 static boolean safe(PlayerBotSession s){var b=s.bot();return eligible(s) && PlayerBotWarehouse.idle(b) && b.isSpawned() && !b.isGatherRestricted()
  && !b.isInTeam()
  && !PlayerBotHelpObjectives.committed(b) && !PlayerBotFindGroup.committed(b) && (b.getInteractionTask()==null || !b.getInteractionTask().isInProgress())
  && !b.getController().isUnderStance() && !b.getController().hasScheduledTask(com.aionemu.gameserver.model.TaskId.ACTION_ITEM_NPC);}
 public static boolean backgroundAllowed(Player bot){return BACKGROUND.get()==bot && bot.isWorldBot();}
 static <T> T backgroundContext(Player bot,Supplier<T> work){if(BACKGROUND.get()!=null)throw new IllegalStateException("Nested profession mutation");BACKGROUND.set(bot);try{return work.get();}finally{BACKGROUND.remove();}}
 static void assign(State s){if(s.primary!=0)return;
  synchronized(COUNTS){var counts=COUNTS.computeIfAbsent(s.bot.getRace(),k->new HashMap<>());int learned=Arrays.stream(Profession.values()).filter(p->p.isCrafting() && PlayerBotProfessionCatalog.points(s.bot,p.getSkillId())>0)
   .max(Comparator.comparingInt(p->PlayerBotProfessionCatalog.points(s.bot,p.getSkillId()))).map(Profession::getSkillId).orElse(0);
   s.primary=learned>0?learned:PlayerBotProfessionPolicy.choose(WEIGHTS,counts,s.bot.getObjectId());if(CENSUS.add(s.bot.getObjectId()))counts.merge(s.primary,1,Integer::sum);}
  s.style=PlayerBotProfessionPolicy.style(Profession.getBySkillId(s.primary).isCrafting()?CRAFT_STYLES:GATHER_STYLES,((long)s.bot.getObjectId()<<32)^s.primary).name();s.number("assignedAt",System.currentTimeMillis());s.save();
 }
 static void census(int character,Race race,Map<String,String> values){if(values.isEmpty())return;validate(values);synchronized(COUNTS){if(CENSUS.add(character))COUNTS.computeIfAbsent(race,k->new HashMap<>()).merge(Integer.parseInt(values.get("primary")),1,Integer::sum);}
  String today=java.time.LocalDate.now().toString();
  // A bot that sleeps, closes and reloads has already been counted today.
  if(today.equals(values.get("day")) && RESTORED.add(character+":"+today))values.forEach((k,v)->{if(k.startsWith("offered."))PlayerBotProfessionMarket.restore(Integer.parseInt(k.substring(8)),Long.parseLong(v));});}
 private static void day(State s){String day=java.time.LocalDate.now().toString();if(day.equals(s.values.getProperty("day")))return;s.values.setProperty("day",day);
  s.values.keySet().removeIf(k->k.toString().startsWith("offered."));for(String key:List.of("spent","trainingSpent","trainingCount","loss","attempts"))s.number(key,0);}
 static long held(Player bot,int item){long count=bot.getInventory().getItemCountByItemId(item);var bank=bot.getStorage(1);if(bank!=null)count=Math.addExact(count,bank.getItemCountByItemId(item));var s=state(bot);return s==null?count:Math.addExact(count,s.escrow.getOrDefault(item,0L));}
 static PlayerBotProfessionPolicy.Style style(State s){var styles=Profession.getBySkillId(s.primary).isCrafting()?CRAFT_STYLES:GATHER_STYLES;return styles.stream().filter(v->v.name().equals(s.style)).findFirst().orElse(styles.get(0));}
 static long limit(State s){return Profession.getBySkillId(s.primary).isCrafting()?PlayerBotEconomyConfig.PROFESSIONS_CRAFTER_MATERIAL_LIMIT:style(s).limit();}
 static long reserve(State s){return Profession.getBySkillId(s.primary).isCrafting()?0:style(s).reserve();}
 static long allowance(PlayerBotSession session,boolean training){var s=state(session.bot());if(s==null || s.primary==0)return 0;day(s);var care=PlayerBotQuestSync.state(session);var bot=session.bot();
  long reserve=Math.max(care.reserve,PlayerBotMarket.reserve(bot.getLevel(),PlayerBotEconomyConfig.MARKET_RESERVE_KINAH));
  if(training)return s.number("trainingCount")>=PlayerBotEconomyConfig.PROFESSIONS_TRAINING_PER_DAY?0:Math.min(Math.max(0,bot.getInventory().getKinah()-reserve),Math.max(0,PlayerBotEconomyConfig.PROFESSIONS_TRAINING_DAILY_KINAH-s.number("trainingSpent")));
  long daily=PlayerBotMarket.reserve(bot.getLevel(),PlayerBotEconomyConfig.PROFESSIONS_OPERATING_DAILY_KINAH),market=PlayerBotMarket.dailyAllowance(true,bot.getLevel(),care.dailyBudget,care.spent);
  return PlayerBotProfessionPolicy.allowance(bot.getInventory().getKinah(),reserve,Math.min(Math.max(0,daily-s.number("spent")),market),0,PlayerBotEconomyConfig.MARKET_BUDGET_PERCENT);
 }
 static void paid(PlayerBotSession session,long amount,boolean training){if(amount<=0)return;var s=state(session.bot());day(s);s.number("spent",Math.addExact(s.number("spent"),amount));if(training){s.number("trainingSpent",s.number("trainingSpent")+amount);s.number("trainingCount",s.number("trainingCount")+1);}
  var care=PlayerBotQuestSync.state(session);String today=java.time.LocalDate.now().toString();if(!today.equals(care.spentDay)){care.spentDay=today;care.spent=0;}care.spent=Math.addExact(care.spent,amount);care.save();s.save();SPENT.add(amount);}
 private static synchronized boolean planSlot(long now){if(planSecond!=now/1000){planSecond=now/1000;plans=0;}return plans++<PlayerBotEconomyConfig.PROFESSIONS_PLANS_PER_SECOND;}
 static void plan(PlayerBotSession session,long now) {
  var s=state(session.bot());if(s==null || !eligible(session) || s.awakePending || now<s.planAt)return;if(s.primary==0){if(s.bot.getLevel()<10)return;assign(s);}if(!planSlot(now))return;long began=System.nanoTime();
  try{day(s);s.planAt=now+PlayerBotEconomyConfig.PROFESSIONS_PLAN_REFRESH_SECONDS*1000L;s.plan=null;s.gather=Map.of();s.inputs=Map.of();s.qualification=null;var b=s.bot;s.cursor++;
   int points=PlayerBotProfessionCatalog.points(b,s.primary);var profession=Profession.getBySkillId(s.primary);
   // Unaffordable training never stops work: that work is how the bot earns the fee.
   Integer fee=profession.getUpgradeCost(points);
   if(fee!=null && fee<=allowance(session,true)){s.plan=new Plan(Stage.TRAIN,0,0,s.primary,0,Map.of(),Map.of(),"Train "+profession);s.reason="Native trainer fee required";return;}
   s.qualification=PlayerBotProfessionQualification.plan(session);
   if(s.qualification!=null && qualificationPlan(session,s,now))return;
   if(!profession.isCrafting()){gatherPlan(s,s.primary,Map.of(),true);return;}
   RecipeTemplate selected=null;boolean gear=false;
   if(!s.style.equals("skill_grinder"))for(var goal:PlayerBotGearPolicy.state(session).goals.values()){var parts=goal.split(":");if(parts.length!=3)continue;int item=Integer.parseInt(parts[0]);if(held(b,item)>0 || !b.getEquipment().getEquippedItemsByItemId(item).isEmpty())continue;
    selected=PlayerBotProfessionCatalog.products(item).stream().filter(r->r.getSkillId()==s.primary && r.getSkillpoint()<=points && !r.isMorph() && r.getRace()!=null && (r.getRace()==b.getRace() || r.getRace()==Race.PC_ALL) && r.getMaxProductionCount()==null).findFirst().orElse(null);if(selected!=null){gear=true;break;}}
   var recipes=PlayerBotProfessionCatalog.recipes(b,s.primary,PlayerBotEconomyConfig.PROFESSIONS_RECIPES_PER_PLAN,s.cursor);
   if(selected==null && s.recipe>0)selected=recipes.stream().filter(r->r.getId()==s.recipe && held(b,r.getProductId())<(long)r.getQuantity()*PlayerBotEconomyConfig.PROFESSIONS_PRODUCT_STOCK_BATCHES).findFirst().orElse(null);
   if(selected==null){var choices=new ArrayList<>(recipes);choices.sort(Comparator.<RecipeTemplate>comparingInt(r->recipeScore(s,r,now)).thenComparingInt(RecipeTemplate::getId));
    for(var r:choices){if(held(b,r.getProductId())>=(long)r.getQuantity()*PlayerBotEconomyConfig.PROFESSIONS_PRODUCT_STOCK_BATCHES)continue;
     if(!b.getRecipeList().isRecipePresent(r.getId()) && designs(r.getId()).stream().noneMatch(id->b.getInventory().getItemCountByItemId(id)>0 || !PlayerBotProfessionCatalog.sellers(b.getRace(),id).isEmpty()))continue;
     selected=r;break;}}
   if(selected==null){s.reason="No useful native recipe; retaining trade and ordinary activity";return;}
   s.recipe=selected.getId();var intent=recipePlan(b,0,selected);
   // Nothing this bot can do right now advances this recipe, so it must not hold every later plan.
   if(intent.stage()==Stage.BLOCKED || intent.stage()==Stage.NONE || intent.stage()==Stage.TRAIN){s.recipe=0;s.reason="Recipe out of reach for now: "+intent.reason();return;}
   s.plan=intent;
   var missing=new TreeMap<Integer,Long>();intent.missing().forEach((id,count)->{long bank=Math.max(0,held(b,id)-b.getInventory().getItemCountByItemId(id));if(count>bank)missing.put(id,count-bank);});
   s.inputs=Map.copyOf(missing);s.reason=gear?"Crafting a real equipment goal":s.style+": "+intent.reason();
   // The market gets first chance at the inputs. Once the wait is over the bot also fetches them itself.
   if(intent.stage()!=Stage.MATERIALS)s.marketWait=0;
   else {if(s.marketWait==0)s.marketWait=now+PlayerBotEconomyConfig.PROFESSIONS_MARKET_WAIT_SECONDS*1000L;if(PlayerBotProfessionPolicy.fallback(s.marketWait,now))gatherPlan(s,0,missing,false);}
  }finally{if(s.qualification!=null)s.reason=s.qualification.reason();PLAN_NANOS.add(System.nanoTime()-began);if(s.primary!=0)s.save();PlayerBotQuestCraftingDriver.invalidate(s.bot);PlayerBotQuestMaterials.invalidate(s.bot);}
 }
 private static boolean qualificationPlan(PlayerBotSession session,State s,long now){var q=s.qualification;s.reason=q.reason();
  if(!q.readable())return true;var absent=PlayerBotProfessionQualification.missing(s.bot,q);
  if(absent.isEmpty())return q.kinah()<=allowance(session,true);
  s.plan=new Plan(Stage.MATERIALS,q.quest(),0,s.primary,0,q.items(),absent,q.reason());var inputs=new TreeMap<Integer,Long>(absent);
  int checked=0;for(var e:new TreeMap<>(absent).entrySet()){
   if(++checked>PlayerBotEconomyConfig.PROFESSIONS_NEEDS_PER_PLAN)break;
   var candidate=productPlan(s.bot,q.quest(),e.getKey(),q.items().get(e.getKey()));
   var recipe=candidate.recipeId()==0?null:DataManager.RECIPE_DATA.getRecipeTemplateById(candidate.recipeId());
   if(recipe!=null && recipe.getMaxProductionCount()==null && Set.of(Stage.CRAFT,Stage.MATERIALS,Stage.LEARN_RECIPE).contains(candidate.stage())){
    s.plan=candidate;candidate.missing().forEach((id,count)->inputs.merge(id,count,Math::max));break;
   }
  }
  var missing=new TreeMap<Integer,Long>();inputs.forEach((id,count)->{long bank=Math.max(0,held(s.bot,id)-s.bot.getInventory().getItemCountByItemId(id));if(count>bank)missing.put(id,count-bank);});
  s.inputs=Map.copyOf(missing);if(s.marketWait==0)s.marketWait=now+PlayerBotEconomyConfig.PROFESSIONS_MARKET_WAIT_SECONDS*1000L;
  if(fallback(s,now))gatherPlan(s,0,missing,false);return true;
 }
 private static int recipeScore(State s,RecipeTemplate recipe,long now){var item=DataManager.ITEM_DATA.getItemTemplate(recipe.getProductId());int score=s.bot.getRecipeList().isRecipePresent(recipe.getId())?0:20;
  if(item!=null && s.style.equals("bulk_intermediates") && com.aionemu.gameserver.services.CentralMarketService.material(item))score-=10;
  if(s.style.equals("market_crafter") && PlayerBotProfessionMarket.demand(PlayerBotProfessionMarket.quote(recipe.getProductId(),now),s.bot.getObjectId(),s.bot.getAccount().getId(),recipe.getProductId())>0)score-=30;
  if(item!=null && PlayerBotSupplies.available(s.bot,item))score-=5;return score;}
 static boolean fallback(State s,long now){return PlayerBotProfessionPolicy.fallback(s.marketWait,now);}
 private static void gatherPlan(State s,int skill,Map<Integer,Long> missing,boolean specialist) {
  var b=s.bot;
  if(!specialist){for(var e:missing.entrySet())if(!PlayerBotProfessionCatalog.nodesFor(b,e.getKey()).isEmpty()){s.gather=Map.of(e.getKey(),b.getInventory().getItemCountByItemId(e.getKey())+e.getValue());s.reason="Gathering a missing ingredient";return;}
   s.reason="Market first; awaiting real ingredient source";return;}
  // Gathering follows the bot's own holding limit, never market demand: this is what keeps the market supplied.
  for(var node:PlayerBotProfessionCatalog.nodes(b,skill,PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN,s.cursor,true)){var materials=GatherableController.getMaterials(b,node);if(materials==null)continue;
   for(var m:materials)if(m.getRate()>0 && DataManager.ITEM_DATA.getItemTemplate(m.getItemId())!=null && held(b,m.getItemId())<limit(s)){
    s.gather=Map.of(m.getItemId(),b.getInventory().getItemCountByItemId(m.getItemId())+1L);s.reason="Gathering native "+node.getName();return;}}
  s.reason="No legal node or holding limit reached";
 }
 static boolean active(Player bot){var s=state(bot);return s!=null && s.activeUntil>System.currentTimeMillis();}
 /** Quest work or a party can hold the trip's action back, and the world planner waits while work is pending. A held trip gives up rather than stall the bot. */
 static boolean pending(Player bot){var s=state(bot);if(s==null || !s.awakePending)return false;
  if(System.currentTimeMillis()-s.pendingSince<=(PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS+60)*1000L)return true;
  s.awakePending=false;s.trip=null;TRIP_FALLBACKS.increment();s.save();return false;}
 static boolean travelling(Player bot){var s=state(bot);return s!=null && s.awakePending && s.trip!=null;}
 /** For diagnostics only: no expiry or other side effect. */
 static boolean stationPending(Player bot){var s=STATES.get(bot.getObjectId());return s!=null && s.awakePending;}
 /** A pending station visit holds world planning only while its own step keeps running. The step can lose every
  * turn (a lined-up quest step, a party, a busy NPC), and then the bot stood still for the whole trip limit and lost
  * the work. Now the work runs where the bot stands, exactly as after a failed trip, and planning resumes. */
 /** Decisions run about once a second; a station step that has not run within this has lost to higher work. */
 static final long FIRST_STATION_TICK_MS=5000;
 static boolean holding(PlayerBotSession session,long now){var s=state(session.bot());if(s==null || !s.awakePending)return false;
  boolean started=s.stationTick>=s.pendingSince;
  long quiet=started?now-s.stationTick:now-s.pendingSince,limit=started?PlayerBotEconomyConfig.PROFESSIONS_TRIP_STALL_SECONDS*1000L:FIRST_STATION_TICK_MS;
  if(quiet<limit && now-s.pendingSince<=(PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS+60)*1000L)return true;
  s.awakePending=false;s.trip=null;TRIP_STALLS.increment();
  if(safe(session) && !session.dormant()){
   try{backgroundContext(s.bot,()->work(session,s,now,false));}
   catch(RuntimeException error){if(ERRORS.getAndIncrement()%100==0)log.warn("World bot {} trade turn failed; goods stay where they are",s.bot.getObjectId(),error);}
  }
  s.save();return false;}
 static boolean offer(PlayerBotSession session,PlayerBotNavigation navigation,long now){var s=state(session.bot());if(s==null || !safe(session) || session.dormant())return false;
  if(s.awakePending || s.activeUntil>now)return true;if(now<s.nextAwake || s.plan==null && s.gather.isEmpty() || PlayerBotWorldActivities.committedTravel(s.bot))return false;
  boolean station=s.plan!=null && (s.plan.stage()==Stage.TRAIN || s.plan.stage()==Stage.CRAFT);if(station && !planSlot(now))return false;
  // Phase 4: a TRADE-leaning bot gets its awake trade turn more often.
  s.activeUntil=now+PlayerBotEconomyConfig.PROFESSIONS_WORK_SLICE_SECONDS*1000L;s.nextAwake=now+(long)(PlayerBotEconomyConfig.PROFESSIONS_WORK_INTERVAL_SECONDS*1000L*PlayerBotLeanings.pace(s.bot,PlayerBotLeanings.Activity.TRADE));
  PlayerBotLeanings.did(s.bot,PlayerBotLeanings.Activity.TRADE);
  s.awakePending=true;s.pendingSince=now;s.trip=station?PlayerBotProfessionTravel.begin(session,navigation,s.plan,now):null;s.save();return true;}
 static boolean awakeTick(PlayerBotSession session,PlayerBotNavigation navigation,long now){var s=state(session.bot());
  if(s==null || !s.awakePending || !safe(session) || session.dormant() || PlayerBotWorldActivities.committedTravel(s.bot))return false;
  s.stationTick=now;
  if(s.plan!=null && (s.plan.stage()==Stage.TRAIN || s.plan.stage()==Stage.CRAFT)){
   var result=PlayerBotProfessionTravel.step(session,navigation,s.trip,true,now);
   if(result.result()==PlayerBotProfessionTravel.Result.CANCELLED){s.awakePending=false;s.trip=null;s.activeUntil=0;s.save();return false;}
   if(result.result()==PlayerBotProfessionTravel.Result.MOVING)return true;
   if(result.result()==PlayerBotProfessionTravel.Result.FALLBACK)TRIP_FALLBACKS.increment();else if(result.walked())WALKED.increment();
  }
  s.awakePending=false;s.trip=null;
  try{backgroundContext(s.bot,()->work(session,s,now,false));}
  catch(RuntimeException error){if(ERRORS.getAndIncrement()%100==0)log.warn("World bot {} trade turn failed; goods stay where they are",s.bot.getObjectId(),error);}
  s.save();return true;}
 static Map<Integer,Long> gatheringGoals(PlayerBotSession session){var s=state(session.bot());return eligible(session) && active(session.bot()) && s!=null?s.gather:Map.of();}
 static Map<Integer,Long> inputs(Player bot){var s=state(bot);return s==null?Map.of():s.inputs;}
 static boolean consumer(int actor,int item){var s=STATES.get(actor);if(s==null)return false;return s.inputs.getOrDefault(item,0L)>0 || PlayerBotGearPolicy.goal(s.bot,DataManager.ITEM_DATA.getItemTemplate(item));}
 static void ledger(Player bot,com.aionemu.gameserver.services.CentralMarketService.CompanionLedger ledger){var s=state(bot);if(s==null)return;var stock=new HashMap<Integer,Long>();
  for(var item:ledger.stock())stock.merge(item.itemId(),item.count(),Math::addExact);for(var order:ledger.orders()){long held=order.uncollected()+(order.side().equals("S") && order.live()?order.remaining():0);if(held>0)stock.merge(order.itemId(),held,Math::addExact);}s.escrow=Map.copyOf(stock);}
 static boolean protectedItem(Player bot,int item){var s=state(bot);return s!=null && s.primary!=0 && (s.qualification!=null && s.qualification.items().containsKey(item) || s.plan!=null && (s.plan.materials().containsKey(item) || s.plan.missing().containsKey(item)) || s.gather.containsKey(item));}
 static long saleQuantity(Player bot,Item item){var s=state(bot);if(s==null || s.primary==0)return -1;day(s);int id=item.getItemId();
  if(protectedItem(bot,id) || s.inputs.containsKey(id))return 0;boolean material=com.aionemu.gameserver.services.CentralMarketService.material(item.getItemTemplate());
  if(!material && !PlayerBotProfessionCatalog.craftItem(id))return -1;
  long reserved=material?reserve(s):0,lot=Profession.getBySkillId(s.primary).isCrafting()?PlayerBotEconomyConfig.PROFESSIONS_SALE_DAILY_UNITS:style(s).lot();
  long daily=item.getItemTemplate().isWeapon() || item.getItemTemplate().isArmor()?PlayerBotEconomyConfig.PROFESSIONS_SALE_GEAR_DAILY_UNITS:PlayerBotEconomyConfig.PROFESSIONS_SALE_DAILY_UNITS;
  return Math.min(item.getItemCount(),Math.min(PlayerBotProfessionMarket.remaining(id),PlayerBotProfessionPolicy.sale(held(bot,id),reserved,lot,daily,s.number("offered."+id))));
 }
 static void offered(Player bot,int item,long quantity){var s=state(bot);if(s==null || s.primary==0)return;day(s);if(entry(s,"offered."+item))s.number("offered."+item,s.number("offered."+item)+quantity);s.save();PlayerBotProfessionMarket.offered(item,quantity);}
 /** Cost basis stays with the goods through a listing or a cancellation and only goes when they sell. */
 static void sold(Player bot,int item,long quantity){var s=state(bot);if(s==null || !s.values.containsKey("cost."+item) || quantity<=0)return;long left=PlayerBotProfessionPolicy.costAfterOffer(s.number("cost."+item),Math.max(quantity,held(bot,item)),quantity);if(left==0)s.values.remove("cost."+item);else s.number("cost."+item,left);s.save();}
 /** Trade inputs bought on the market count against the trade's own budget. The market has already charged the shared one. */
 static void marketSpent(Player bot,long amount){var s=state(bot);if(s==null || s.primary==0 || amount<=0)return;day(s);s.number("spent",Math.addExact(s.number("spent"),amount));s.save();SPENT.add(amount);}
 /** Saved ledgers stay within what the metadata validator accepts. A full ledger skips tracking rather than failing the save. */
 private static boolean entry(State s,String key){return s.values.containsKey(key) || s.values.stringPropertyNames().stream().filter(k->k.startsWith("offered.") || k.startsWith("cost.")).count()<60;}
 static long saleFloor(Player bot,int item){var s=state(bot);return s==null?0:PlayerBotProfessionPolicy.floor(s.number("cost."+item),held(bot,item));}
 static boolean forSale(Player bot,int item){var s=state(bot);return s!=null && s.values.containsKey("cost."+item);}
 static void invalidate(Player bot){var s=state(bot);if(s!=null)s.planAt=0;}
 static String reason(Player bot){var s=state(bot);return s==null?"":s.primary+" "+s.style+": "+s.reason;}
 static boolean keep(Item item,Set<Integer> generated){return enabled() && prepared && !generated.contains(item.getObjectId()) && !item.getItemTemplate().isKinah() && !PlayerBotProfessionCatalog.supplied(item.getItemId()) && (PlayerBotProfessionCatalog.craftItem(item.getItemId()) || item.isStorableInWarehouse() && item.getItemId()>=152000000 && item.getItemId()<153000000);}
 static boolean keepSkill(int skill){return enabled() && Profession.getBySkillId(skill)!=null;}
 static boolean keepRecipes(){return enabled() && prepared;}
 static void close(Player bot){STATES.remove(bot.getObjectId());PlayerBotProfessionMarket.release(bot.getObjectId());}
 private static synchronized boolean turnSlot(long now){if(turnSecond!=now/1000){turnSecond=now/1000;turns=0;}return turns++<PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_TURNS_PER_SECOND;}
 /** Sleeping bots get no decisions, so a due turn asks for one. A slot is claimed here: O(1), and the population-wide budget limits how many wake the scheduler. */
 static boolean dormantDue(PlayerBotSession session,long now) {
  var s=STATES.get(session.bot().getObjectId());if(s==null)return false;if(s.admitted)return true;
  if(now<s.next || !turnSlot(now))return false;s.admitted=true;return true;
 }
 /** Called from a dormant bot's decision under its actor lease and session lock. Normal periodic checkpoints save the results atomically. */
 static void dormantTurn(PlayerBotSession session,long now) {
  var s=state(session.bot());if(s==null || !s.admitted)return;s.admitted=false;
  s.awakePending=false;s.trip=null;s.activeUntil=0;
  // Offscreen trade turns follow the same leaning, so a sleeping bot keeps the pace it has awake.
  s.next=now+(long)(PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_INTERVAL_SECONDS*1000L*PlayerBotLeanings.pace(s.bot,PlayerBotLeanings.Activity.TRADE))+Math.floorMod(s.bot.getObjectId(),60000);
  try{backgroundTurn(session,now);}
  catch(RuntimeException error){if(ERRORS.getAndIncrement()%100==0)log.warn("World bot {} background trade turn failed; goods stay where they are",s.bot.getObjectId(),error);}
  finally{if(s.primary!=0)s.save();}
 }
 static boolean backgroundTurn(PlayerBotSession session,long now) {
  var s=state(session.bot());if(s==null || !safe(session) || !session.dormant() || PlayerBotWorldActivity.allowed(session))return false;
  try{PlayerBotProfessionMarket.refresh(now);}catch(java.sql.SQLException error){if(ERRORS.getAndIncrement()%100==0)log.warn("Trade market evidence deferred",error);}
  return backgroundContext(s.bot,()->{plan(session,now);return work(session,s,now,true);});
 }
 private static boolean work(PlayerBotSession session,State s,long now,boolean dormant) {
  if(s.primary==0)return false;TURNS.increment();
  if(dormant && s.qualification!=null && PlayerBotProfessionQualification.finish(session,s.qualification)){
   if(s.qualification.reward()>0)QUALIFIED.increment();invalidate(s.bot);s.next=Math.min(s.next,now+PlayerBotEconomyConfig.PROFESSIONS_SETUP_RETRY_SECONDS*1000L);return true;
  }
  if(dormant && PlayerBotMarket.idle(session,true))return true;
  if(PlayerBotWarehouse.profession(session))return true;
  if(s.plan!=null && s.plan.stage()==Stage.TRAIN && train(session,s.plan.skillId()))return true;
  if(s.plan!=null && s.plan.stage()==Stage.LEARN_RECIPE){int design=designItem(s.bot,s.plan.recipeId());if(learnRecipe(s.bot,s.plan.recipeId())){DESIGNS.increment();invalidate(s.bot);learned(session);setupDone(s);return true;}
   if(design>0)return shop(session,design,1);return false;}
  if(s.plan!=null && s.plan.stage()==Stage.CRAFT)return craft(session,s.plan,now);
  if(dormant && !s.gather.isEmpty())return gather(session,now);
  if(s.plan!=null && s.plan.stage()==Stage.MATERIALS && fallback(s,now))for(var e:s.inputs.entrySet())if(shop(session,e.getKey(),e.getValue()))return true;
  if(dormant){s.reason=s.qualification==null?"Waiting for native materials, funds, qualification or storage":s.qualification.reason();BLOCKED.increment();}return false;
 }
 /** Learning a recipe commits at once through the native list, so save the used design straight away or a crash could give it back. */
 private static void learned(PlayerBotSession session){try{PlayerBotService.getInstance().checkpointForOwnerAction(session);}catch(java.sql.SQLException error){log.warn("World bot {} recipe checkpoint deferred to the next save",session.bot().getObjectId(),error);}}
 static boolean train(PlayerBotSession session,int skill){var s=state(session.bot());int points=PlayerBotProfessionCatalog.points(s.bot,skill);Integer cost=Profession.getBySkillId(skill).getUpgradeCost(points);
  if(cost==null || cost>allowance(session,true) || !PlayerBotProfessionCatalog.trainer(s.bot.getRace(),skill))return false;
  if(!CraftSkillUpdateService.getInstance().learnBackground(s.bot,skill))return false;paid(session,cost,true);TRAINED.increment();invalidate(s.bot);setupDone(s);return true;}
 /** Training, learning and buying only prepare the real work, so the next turn comes soon. Otherwise a new crafter waits about an hour for its first attempt. */
 private static void setupDone(State s){s.next=Math.min(s.next,System.currentTimeMillis()+PlayerBotEconomyConfig.PROFESSIONS_SETUP_RETRY_SECONDS*1000L);}
 static boolean shop(PlayerBotSession session,int item,long quantity){var s=state(session.bot());if(quantity<=0 || held(s.bot,item)>=limit(s))return false;
  long before=s.bot.getInventory().getKinah();for(var seller:PlayerBotProfessionCatalog.sellers(s.bot.getRace(),item))if(com.aionemu.gameserver.services.TradeService.backgroundPurchase(s.bot,seller.npc(),seller.rate(),item,quantity,allowance(session,false))){paid(session,before-s.bot.getInventory().getKinah(),false);invalidate(s.bot);setupDone(s);return true;}return false;}
 static boolean legalInputs(Player bot,Map<Integer,Long> materials){for(var e:materials.entrySet()){long eligible=0;for(var item:bot.getInventory().getItemsByItemId(e.getKey()))if(!PlayerBotGearPolicy.generated(bot,item) && !PlayerBotRoadMerchants.merchandise(bot,item) && !PlayerBotCare.worldQuestItem(bot,item.getItemId()))eligible+=item.getItemCount();if(eligible<e.getValue())return false;}return true;}
 static long cost(Map<Integer,Long> materials){long cost=0;for(var e:materials.entrySet()){var t=DataManager.ITEM_DATA.getItemTemplate(e.getKey());if(t==null)return Long.MAX_VALUE;try{cost=Math.addExact(cost,Math.multiplyExact(e.getValue(),Math.max(1,EconomyPrices.replacement(t))));}catch(ArithmeticException overflow){return Long.MAX_VALUE;}}return cost;}
 static boolean craft(PlayerBotSession session,Plan plan,long now){var s=state(session.bot());var recipe=DataManager.RECIPE_DATA.getRecipeTemplateById(plan.recipeId());if(recipe==null)return false;
  long hour=now/3600000;if(s.number("attemptHour")!=hour){s.number("attemptHour",hour);s.number("attempts",0);}long cost=cost(plan.materials());
  boolean useful=s.qualification!=null && plan.questId()==s.qualification.quest() || PlayerBotGearPolicy.goal(s.bot,DataManager.ITEM_DATA.getItemTemplate(recipe.getProductId()));
  var quote=PlayerBotProfessionMarket.quote(recipe.getProductId(),now);long demand=PlayerBotProfessionMarket.demand(quote,s.bot.getObjectId(),s.bot.getAccount().getId(),recipe.getProductId());
  boolean sale=demand>=recipe.getQuantity() && quote!=null && PlayerBotProfessionPolicy.profit(cost,quote.bids().stream().filter(p->!p.simulated()).mapToLong(p->p.price()*recipe.getQuantity()).max().orElse(0),PlayerBotEconomyConfig.PROFESSIONS_PROFIT_SUCCESS_PERCENT,PlayerBotEconomyConfig.PROFESSIONS_MINIMUM_MARGIN_PERCENT)
   && PlayerBotProfessionMarket.claim(s.bot.getObjectId(),recipe.getProductId(),recipe.getQuantity(),demand,now);
  boolean practice=!useful && !sale;boolean done=false;
  for(int attempt=0;attempt<PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_CRAFT_BATCH && legalInputs(s.bot,plan.materials());attempt++) {
   int points=PlayerBotProfessionCatalog.points(s.bot,s.primary);if(practice && PlayerBotProfessionPolicy.zeroXpStop(s.zeroXp,s.zeroAt,points,PlayerBotEconomyConfig.PROFESSIONS_ZERO_XP_COMPLETIONS))break;
   var entry=s.bot.getSkillList().getSkillEntry(recipe.getSkillId());int xpBefore=entry==null?0:entry.getCurrentXp();
   if(practice && !PlayerBotProfessionPolicy.practice(points,recipe.getSkillpoint(),s.number("attempts"),PlayerBotEconomyConfig.PROFESSIONS_PRACTICE_ATTEMPTS_PER_HOUR,cost,PlayerBotMarket.reserve(s.bot.getLevel(),PlayerBotEconomyConfig.PROFESSIONS_SKILL_LOSS_DAILY_KINAH)-s.number("loss")))break;
   var result=CraftService.backgroundCraft(s.bot,recipe,plan.materials());if(result==null)break;done=true;
   ATTEMPTS.increment();s.number("attempts",s.number("attempts")+1);if(practice)s.number("loss",Math.addExact(s.number("loss"),cost));
   if(result.success()) {
    Integer critical=result.critical()>0?recipe.getComboProduct(result.critical()):null;int product=critical!=null?critical:recipe.getProductId();
    if(entry(s,"cost."+product))s.number("cost."+product,Math.addExact(s.number("cost."+product),cost));s.failures=0;PlayerBotWorldOutcomes.backgroundCrafted();
    var after=s.bot.getSkillList().getSkillEntry(recipe.getSkillId());boolean earned=PlayerBotProfessionCatalog.points(s.bot,s.primary)!=points || after!=null && after.getCurrentXp()!=xpBefore;
    if(earned)s.zeroXp=0;else if(practice){s.zeroXp=s.zeroAt==points?s.zeroXp+1:1;s.zeroAt=points;}
   }else{FAILURES.increment();if(++s.failures>=PlayerBotEconomyConfig.PROFESSIONS_FAILED_ATTEMPTS){s.next=Math.max(s.next,now+PlayerBotEconomyConfig.PROFESSIONS_RETRY_SECONDS*1000L);break;}}
   s.next=Math.max(s.next,now+result.millis());
  }
  if(done)invalidate(s.bot);return done;
 }
 static boolean gather(PlayerBotSession session,long now){var s=state(session.bot());var b=s.bot;int item=s.gather.keySet().iterator().next();boolean crafter=Profession.getBySkillId(s.primary).isCrafting();
  var nodes=crafter?PlayerBotProfessionCatalog.nodesFor(b,item):PlayerBotProfessionCatalog.nodes(b,s.primary,PlayerBotEconomyConfig.PROFESSIONS_SOURCES_PER_PLAN,s.cursor,true);
  long rolls=PlayerBotProfessionPolicy.rolls(s.lastGather==0?Long.MAX_VALUE:now-s.lastGather,PlayerBotEconomyConfig.PROFESSIONS_GATHER_SECONDS*1000L,PlayerBotEconomyConfig.PROFESSIONS_BACKGROUND_GATHER_BATCH);if(rolls<=0)return false;
  for(var node:nodes){if(PlayerBotProfessionCatalog.points(b,node.getHarvestSkill())<=0)continue;var materials=GatherableController.getMaterials(b,node);if(materials==null || materials.stream().noneMatch(m->m.getItemId()==item))continue;
   s.lastGather=now;boolean worked=false;
   for(long roll=0;roll<rolls;roll++) {
    var material=GatherableController.selectMaterial(materials);if(material==null)continue;long count=com.aionemu.gameserver.model.gameobjects.player.Rates.GATHERING_COUNT.calcResult(b,1);
    if(PlayerBotProfessionPolicy.harvest(held(b,material.getItemId()),limit(s),count)==0 || b.getInventory().isFull())break;
    if(node.getCheckType()==2 && b.getInventory().getItemCountByItemId(node.getRequiredItemId())<node.getEraseValue())break;
    worked=true;if(!GatheringTask.backgroundSuccess(b,node)){FAILURES.increment();continue;}
    if(node.getEraseValue()>0 && node.getCheckType()==2 && !b.getInventory().decreaseByItemId(node.getRequiredItemId(),node.getEraseValue()))break;
    ItemService.addItem(b,material.getItemId(),count);GatherableController.rewardPlayer(b,node);PlayerBotWorldOutcomes.backgroundGathered(b);
   }
   if(worked)invalidate(b);return worked;
  }return false;
 }
 static String stats(){return "professions: "+PlayerBotWorldOutcomes.background()+", turns="+TURNS.sum()+", trained="+TRAINED.sum()+", designs="+DESIGNS.sum()+", attempts="+ATTEMPTS.sum()+", failed="+FAILURES.sum()+", blocked="+BLOCKED.sum()+", spent="+SPENT.sum()+", trips walked="+WALKED.sum()+", trip fallbacks="+TRIP_FALLBACKS.sum()+", trip stalls released="+TRIP_STALLS.sum()+", qualifications finished="+QUALIFIED.sum()+", planning ms="+PLAN_NANOS.sum()/1000000;}
 private PlayerBotProfessions(){}
}
