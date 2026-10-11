/* Native dependency/eligibility adaptation of pinned QuestAction.cpp,
 * AcceptQuestAction.cpp and ChooseTravelTargetAction.cpp, 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.quest.XMLStartCondition;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;

/** Read-only finite native prerequisite graph. Planning never accepts, rewards or rewrites saved choices. */
final class PlayerBotQuestPrerequisites {
 enum Goal { ACQUIRED, FINISHED }
 record Blocker(int quest,String code,boolean permanent,String reason) {}
 record Step(int quest,Goal goal,int reward,int completions) {}
 record Plan(int quest,boolean reachable,List<Integer> direct,List<Integer> chain,List<Integer> starters,
             Map<Integer,Integer> requiredRewards,Map<Integer,Step> steps,List<Blocker> blockers) {
  Plan {direct=List.copyOf(direct);chain=List.copyOf(chain);starters=List.copyOf(starters);requiredRewards=Map.copyOf(requiredRewards);steps=Map.copyOf(steps);blockers=List.copyOf(blockers);}
 }
 /** Snapshot seam for offline checks; production reads the real actor and registered native handlers. */
 interface View {
  QuestTemplate template(int quest);
  QuestState state(int quest);
  List<Blocker> eligibility(int quest);
  List<Blocker> capability(int quest);
  default QuestBotRewardChoices rewardChoices(int quest){return QuestBotRewardChoices.unknown();}
  int title();
 }
 private record Key(int quest,Goal goal,int reward) {}
 private record Group(List<Key> needed,Set<Integer> unfinished,Set<Integer> noacquired,int title) {}
 private record Node(Key key,QuestTemplate template,boolean satisfied,List<Group> groups,List<Blocker> blockers) {}
 private record Guard(int owner,Set<Integer> unfinished,Set<Integer> noacquired) {}
 private record Candidate(LinkedHashMap<Integer,Step> steps,Set<Integer> unfinished,Set<Integer> noacquired,Set<Integer> direct,Set<Guard> guards,Map<Integer,Set<Integer>> before) {
  Candidate(LinkedHashMap<Integer,Step> steps,Set<Integer> unfinished,Set<Integer> noacquired,Set<Integer> direct){this(steps,unfinished,noacquired,direct,Set.of(),Map.of());}
 }
 private static volatile Object GROUP_SOURCE;
 private static final Map<QuestTemplate,List<Group>> GROUPS=Collections.synchronizedMap(new IdentityHashMap<>());
 private static Candidate empty(){return new Candidate(new LinkedHashMap<>(),Set.of(),Set.of(),Set.of());}
 static Plan plan(Player bot,int quest){return plan(new NativeView(bot),quest);}
 static List<Integer> missing(Player bot,int quest){return plan(bot,quest).direct();}
 static List<Integer> chain(Player bot,int quest){return plan(bot,quest).chain();}
 static List<Blocker> admission(Player bot,int quest){return new NativeView(bot).eligibility(quest);}

 static Plan plan(View view,int quest) {
  Key root=new Key(quest,Goal.ACQUIRED,-1);var nodes=new LinkedHashMap<Key,Node>();var parents=new HashMap<Key,Set<Key>>();var discovery=new ArrayDeque<Key>();discovery.add(root);
  // Visit every distinct native requirement, with no depth/count truncation and no recursion stack.
  while(!discovery.isEmpty()) {
   var key=discovery.removeFirst();if(nodes.containsKey(key))continue;var node=node(view,key);nodes.put(key,node);
   if(node.satisfied())continue;
   for(var group:node.groups())for(var required:group.needed()){parents.computeIfAbsent(required,k->new LinkedHashSet<>()).add(key);if(!nodes.containsKey(required))discovery.addLast(required);}
  }
  var solved=new HashMap<Key,Set<Candidate>>();var pending=new ArrayDeque<Key>(nodes.keySet());var queued=new HashSet<Key>(nodes.keySet());
  // Least fixed point: an unseeded cycle cannot become a fabricated completed prerequisite.
  // A real OR escape can seed a cycle member and then unlock its dependents normally.
  while(!pending.isEmpty()) {
   var key=pending.removeFirst();queued.remove(key);var candidates=solve(view,nodes.get(key),solved);var previous=solved.get(key);
   if(candidates.isEmpty() || candidates.equals(previous))continue;solved.put(key,candidates);
   for(var parent:parents.getOrDefault(key,Set.of()))if(queued.add(parent))pending.addLast(parent);
  }
  var alternatives=solved.get(root);if(alternatives==null)return new Plan(quest,false,List.of(),List.of(),List.of(),Map.of(),Map.of(),explain(view,root,nodes,solved));
  var chosen=alternatives.stream().min(PlayerBotQuestPrerequisites::compare).orElseThrow();
  var steps=new LinkedHashMap<>(chosen.steps());steps.remove(quest);var rewards=new TreeMap<Integer,Integer>();steps.values().forEach(s->{if(s.reward()>=0)rewards.put(s.quest(),s.reward());});
  var direct=chosen.direct().stream().filter(id->id!=quest).sorted().toList();var starters=new ArrayList<Integer>();
  for(int id:steps.keySet()) {
   var state=view.state(id);if(state!=null && state.getStatus()!=QuestStatus.LOCKED && state.getStatus()!=QuestStatus.COMPLETE)continue;
   // A FINISHED requirement already includes its prerequisite plan; the next actor can
   // start only when the actual saved condition groups are fulfilled now.
   var template=view.template(id);if(template!=null && view.eligibility(id).isEmpty() && view.capability(id).isEmpty() && actualGroups(view,template)>=template.getRequiredConditionCount()
    && ordering(chosen).entrySet().stream().noneMatch(e->steps.containsKey(e.getKey()) && e.getValue().contains(id)))starters.add(id);
  }
  return new Plan(quest,true,direct,new ArrayList<>(steps.keySet()),starters,rewards,steps,List.of());
 }
 private static boolean satisfied(View view,Key key,QuestTemplate template) {
  var state=view.state(key.quest());if(state==null)return false;
  if(key.goal()==Goal.ACQUIRED)return state.getStatus()!=QuestStatus.LOCKED;
  return state.getStatus()==QuestStatus.COMPLETE && (key.reward()<0 || Objects.equals(state.getRewardGroup(),key.reward()))
   && (template==null || !template.isRepeatable() || template.getMaxRepeatCount()==255 || state.getCompleteCount()==template.getMaxRepeatCount());
 }
 private static Node node(View view,Key key) {
  var template=view.template(key.quest());if(satisfied(view,key,template))return new Node(key,template,true,List.of(),List.of());
  var blockers=new ArrayList<Blocker>();if(template==null)return new Node(key,null,false,List.of(),List.of(new Blocker(key.quest(),"MISSING_TEMPLATE",true,"Native prerequisite quest "+key.quest()+" has no template")));
  var state=view.state(key.quest());boolean active=state!=null && (state.getStatus()==QuestStatus.START || state.getStatus()==QuestStatus.REWARD);
  if(key.reward()>=0 && (key.reward()>=template.getRewards().size()))blockers.add(new Blocker(key.quest(),"REWARD_GROUP_UNAVAILABLE",true,"Prerequisite "+key.quest()+" requires native reward group "+key.reward()+" but only "+template.getRewards().size()+" groups exist"));
  if(key.reward()>=0) {
   var choices=view.rewardChoices(key.quest());
   if(choices.declared() && !choices.groups().contains(key.reward()))blockers.add(new Blocker(key.quest(),"NATIVE_REWARD_ROUTE",true,
    "Prerequisite "+key.quest()+" requires native story group "+key.reward()+", but this actor can reach "+choices.groups()+": "+choices.reason()));
  }
  if(state!=null && state.getStatus()==QuestStatus.COMPLETE) {
   boolean exhausted=template.getMaxRepeatCount()!=255 && state.getCompleteCount()>=template.getMaxRepeatCount();
   if(exhausted)blockers.add(new Blocker(key.quest(),key.reward()>=0 && !Objects.equals(state.getRewardGroup(),key.reward())?"SAVED_REWARD_CHOICE":"REPEAT_EXHAUSTED",true,
    "Prerequisite "+key.quest()+" requires reward group "+key.reward()+" and native completion count "+template.getMaxRepeatCount()+"; saved group="+state.getRewardGroup()+", completions="+state.getCompleteCount()+". Native repetition is exhausted; saved progress cannot be rewritten"));
   else if(template.isTimeBased() && state.getNextRepeatTime()!=null && state.getNextRepeatTime().getTime()>System.currentTimeMillis())blockers.add(new Blocker(key.quest(),"REPEAT_COOLDOWN",false,"Native prerequisite "+key.quest()+" can repeat after "+state.getNextRepeatTime()));
  }
  if(state!=null && state.getStatus()==QuestStatus.REWARD && key.reward()>=0 && state.getRewardGroup()!=null && !Objects.equals(state.getRewardGroup(),key.reward())) {
   boolean exhausted=template.getMaxRepeatCount()!=255 && state.getCompleteCount()+1>=template.getMaxRepeatCount();
   blockers.add(new Blocker(key.quest(),"COMMITTED_REWARD_CHOICE",exhausted,"Prerequisite "+key.quest()+" requires native reward group "+key.reward()+", but current REWARD state has committed group "+state.getRewardGroup()+"; native hand-in must preserve that choice"+(exhausted?" and no later repetition remains":" before a later native repetition can select another branch")));
  }
  if(!active)blockers.addAll(view.eligibility(key.quest()));blockers.addAll(view.capability(key.quest()));
  var groups=active?List.<Group>of():groups(template);return new Node(key,template,false,groups,List.copyOf(blockers));
 }
 private static List<Group> groups(QuestTemplate template) {
  synchronized(GROUPS){if(GROUP_SOURCE!=DataManager.QUEST_DATA){GROUPS.clear();GROUP_SOURCE=DataManager.QUEST_DATA;}var cached=GROUPS.get(template);if(cached!=null)return cached;}
  var groups=new ArrayList<Group>();for(var condition:template.getXMLStartConditions()) {
   var needed=new ArrayList<Key>();if(condition.getFinishedPreconditions()!=null)for(var finished:condition.getFinishedPreconditions())needed.add(new Key(finished.getQuestId(),Goal.FINISHED,finished.getReward()));
   for(int id:integers(condition,"acquired"))needed.add(new Key(id,Goal.ACQUIRED,-1));
   groups.add(new Group(List.copyOf(needed),integers(condition,"unfinished"),integers(condition,"noacquired"),PlayerBotQuestConversations.number(condition,"requiredTitle")));
  }var result=List.copyOf(groups);GROUPS.put(template,result);return result;
 }
 private static Set<Integer> integers(XMLStartCondition condition,String name){var value=PlayerBotQuestConversations.field(condition,name);return value instanceof List<?> list?Set.copyOf(list.stream().map(v->(Integer)v).toList()):Set.of();}
 private static List<Blocker> guards(View view,int quest,Group group) {
  var blockers=new ArrayList<Blocker>();for(int id:group.unfinished()){var state=view.state(id);if(state!=null && state.getStatus()==QuestStatus.COMPLETE){var template=view.template(id);boolean repeatable=template!=null && template.isRepeatable() && (template.getMaxRepeatCount()==255 || state.getCompleteCount()<template.getMaxRepeatCount());blockers.add(new Blocker(quest,"FORBIDDEN_COMPLETION",!repeatable,"Native start group requires quest "+id+" unfinished, but it is already COMPLETE"+(repeatable?"; a permitted native repetition could change its status, but no status reset is authorized by discovery":" and native repetition is exhausted")));}}
  for(int id:group.noacquired()){var state=view.state(id);if(state!=null && (state.getStatus()==QuestStatus.START || state.getStatus()==QuestStatus.REWARD))blockers.add(new Blocker(quest,"FORBIDDEN_ACTIVE",false,"Native start group requires quest "+id+" not active, but saved status is "+state.getStatus()));}
  if(group.title()!=0 && group.title()!=view.title())blockers.add(new Blocker(quest,"REQUIRED_TITLE",false,"Native start group requires displayed title "+group.title()+"; current displayed title="+view.title()));return blockers;
 }
 private static int actualGroups(View view,QuestTemplate template) {
  int count=0;for(var group:groups(template))if(guards(view,template.getId(),group).isEmpty() && group.needed().stream().allMatch(k->satisfied(view,k,view.template(k.quest()))))count++;return count;
 }
 private static Set<Candidate> solve(View view,Node node,Map<Key,Set<Candidate>> solved) {
  if(node.satisfied())return Set.of(empty());if(!node.blockers().isEmpty())return Set.of();
  int required=node.groups().isEmpty()?0:Math.max(0,node.template().getRequiredConditionCount());if(required>node.groups().size())return Set.of();
  var choices=new ArrayList<List<Candidate>>();for(var group:node.groups()) {
   if(!guards(view,node.key().quest(),group).isEmpty()){choices.add(List.of());continue;}
   var candidates=new LinkedHashSet<Candidate>();candidates.add(new Candidate(new LinkedHashMap<>(),group.unfinished(),group.noacquired(),Set.of()));
   for(var dependency:group.needed()) {
    var alternatives=solved.get(dependency);if(alternatives==null){candidates.clear();break;}
    var joined=new LinkedHashSet<Candidate>();for(var candidate:candidates)for(var next:alternatives){
     // Only this node's immediate requirements belong in direct(); nested plans
     // retain their entire native chain but cannot promote ancestors to direct edges.
     var combined=merge(candidate,new Candidate(next.steps(),next.unfinished(),next.noacquired(),Set.of(),next.guards(),next.before()));if(combined==null)continue;
     if(!next.steps().isEmpty()){var direct=new TreeSet<>(combined.direct());direct.add(dependency.quest());combined=new Candidate(combined.steps(),combined.unfinished(),combined.noacquired(),Set.copyOf(direct),combined.guards(),combined.before());}
     joined.add(combined);
    }candidates=new LinkedHashSet<>(pruned(joined));if(candidates.isEmpty())break;
   }
   choices.add(List.copyOf(candidates));
  }
  // Retain compatible alternatives instead of pruning to the shortest immediate
  // group: a later AND dependency may need another reward branch or exclude a quest.
  var frontier=new ArrayList<List<Candidate>>();for(int i=0;i<=required;i++)frontier.add(new ArrayList<>());frontier.get(0).add(empty());
  for(var groupChoices:choices)for(int count=required;count>0;count--) {
   var previous=List.copyOf(frontier.get(count-1));for(var candidate:previous)for(var choice:groupChoices){var joined=merge(candidate,choice);if(joined!=null)retain(frontier.get(count),joined);}
  }
  var results=new LinkedHashSet<Candidate>();for(var candidate:frontier.get(required)) {
   if(candidate.steps().containsKey(node.key().quest()))continue;
   var saved=view.state(node.key().quest());
   int completions=node.key().goal()==Goal.FINISHED?(node.template().isRepeatable() && node.template().getMaxRepeatCount()!=255?node.template().getMaxRepeatCount():saved==null?1:saved.getCompleteCount()+1):0;
   var step=new Step(node.key().quest(),node.key().goal(),node.key().reward(),completions);var entries=new LinkedHashMap<>(candidate.steps());entries.put(step.quest(),step);
   var pendingGuards=new HashSet<>(candidate.guards());if(!candidate.unfinished().isEmpty() || !candidate.noacquired().isEmpty())pendingGuards.add(new Guard(step.quest(),candidate.unfinished(),candidate.noacquired()));
   var before=new HashMap<Integer,Set<Integer>>(candidate.before());for(int dependency:candidate.direct())edge(before,dependency,step.quest());
   var result=ordered(new Candidate(entries,Set.of(),Set.of(),candidate.direct(),Set.copyOf(pendingGuards),before));if(result!=null)results.add(result);
  }return Set.copyOf(pruned(results));
 }
 private static Candidate merge(Candidate a,Candidate b) {
  var steps=new LinkedHashMap<>(a.steps());for(var entry:b.steps().entrySet()) {
   var previous=steps.get(entry.getKey());var next=entry.getValue();if(previous==null){steps.put(entry.getKey(),next);continue;}
   if(previous.reward()>=0 && next.reward()>=0 && previous.reward()!=next.reward())return null;
   steps.put(entry.getKey(),new Step(entry.getKey(),previous.goal()==Goal.FINISHED || next.goal()==Goal.FINISHED?Goal.FINISHED:Goal.ACQUIRED,
    Math.max(previous.reward(),next.reward()),Math.max(previous.completions(),next.completions())));
  }
  var unfinished=new HashSet<>(a.unfinished());unfinished.addAll(b.unfinished());var noacquired=new HashSet<>(a.noacquired());noacquired.addAll(b.noacquired());var direct=new HashSet<>(a.direct());direct.addAll(b.direct());
  var guards=new HashSet<>(a.guards());guards.addAll(b.guards());var before=new HashMap<Integer,Set<Integer>>(a.before());b.before().forEach((from,targets)->targets.forEach(to->edge(before,from,to)));
  var result=new Candidate(steps,Set.copyOf(unfinished),Set.copyOf(noacquired),Set.copyOf(direct),Set.copyOf(guards),before);return valid(result)?ordered(result):null;
 }
 private static boolean valid(Candidate candidate){return candidate.steps().values().stream().noneMatch(s->s.goal()==Goal.FINISHED?candidate.unfinished().contains(s.quest()):candidate.noacquired().contains(s.quest()));}
 /** Safe dominance: retain every distinct reward/goal/negative constraint. A
  * strict superset of identical steps cannot rescue a parent that its subset
  * cannot rescue; no numerical branch/depth cap is imposed. */
 private static boolean dominates(Candidate a,Candidate b){return b.steps().entrySet().containsAll(a.steps().entrySet()) && b.unfinished().containsAll(a.unfinished()) && b.noacquired().containsAll(a.noacquired()) && b.guards().containsAll(a.guards()) && a.before().entrySet().stream().allMatch(e->b.before().getOrDefault(e.getKey(),Set.of()).containsAll(e.getValue()));}
 private static void edge(Map<Integer,Set<Integer>> before,int from,int to){var targets=new HashSet<>(before.getOrDefault(from,Set.of()));targets.add(to);before.put(from,Set.copyOf(targets));}
 /** Negative conditions apply at their own native acceptance moment, not forever
  * after an ancestor completes. Sequence actual quest goals accordingly. */
 private static Candidate ordered(Candidate candidate){
  var edges=ordering(candidate);
  var incoming=new HashMap<Integer,Integer>();candidate.steps().keySet().forEach(id->incoming.put(id,0));edges.forEach((from,targets)->{if(incoming.containsKey(from))for(int to:targets)if(incoming.containsKey(to))incoming.merge(to,1,Integer::sum);});
  var ordered=new LinkedHashMap<Integer,Step>();var ready=new TreeSet<Integer>();incoming.forEach((id,count)->{if(count==0)ready.add(id);});
  while(!ready.isEmpty()){int id=ready.pollFirst();ordered.put(id,candidate.steps().get(id));for(int to:edges.getOrDefault(id,Set.of()))if(incoming.containsKey(to) && incoming.merge(to,-1,Integer::sum)==0)ready.add(to);}
  if(ordered.size()!=candidate.steps().size())return null;return new Candidate(ordered,candidate.unfinished(),candidate.noacquired(),candidate.direct(),candidate.guards(),candidate.before());
 }
 private static Map<Integer,Set<Integer>> ordering(Candidate candidate){
  var edges=new HashMap<Integer,Set<Integer>>(candidate.before());for(var guard:candidate.guards()) {
   for(int forbidden:guard.unfinished()){var step=candidate.steps().get(forbidden);if(step!=null && step.goal()==Goal.FINISHED)edge(edges,guard.owner(),forbidden);}
   for(int forbidden:guard.noacquired()){var step=candidate.steps().get(forbidden);if(step!=null){if(step.goal()==Goal.FINISHED)edge(edges,forbidden,guard.owner());else edge(edges,guard.owner(),forbidden);}}
  }
  return edges;
 }
 private static List<Candidate> pruned(Collection<Candidate> choices){var result=new ArrayList<Candidate>();for(var candidate:choices)retain(result,candidate);return result;}
 private static void retain(List<Candidate> choices,Candidate candidate){
  for(var existing:choices)if(dominates(existing,candidate) && (!dominates(candidate,existing) || compare(existing,candidate)<=0))return;
  choices.removeIf(existing->dominates(candidate,existing));choices.add(candidate);
 }
 private static int compare(Candidate a,Candidate b) {
  int size=Integer.compare(a.steps().size(),b.steps().size());if(size!=0)return size;
  int steps=a.steps().values().stream().sorted(Comparator.comparingInt(Step::quest)).toList().toString().compareTo(b.steps().values().stream().sorted(Comparator.comparingInt(Step::quest)).toList().toString());
  return steps!=0?steps:a.direct().stream().sorted().toList().toString().compareTo(b.direct().stream().sorted().toList().toString());
 }
 private static List<Blocker> explain(View view,Key root,Map<Key,Node> nodes,Map<Key,Set<Candidate>> solved) {
  var result=new LinkedHashSet<Blocker>();var queue=new ArrayDeque<Key>();var visited=new LinkedHashSet<Key>();queue.add(root);
  while(!queue.isEmpty()) {
   var key=queue.removeFirst();if(!visited.add(key) || solved.containsKey(key))continue;var node=nodes.get(key);if(node==null)continue;result.addAll(node.blockers());
   if(node.template()!=null && node.template().getRequiredConditionCount()>node.groups().size() && !node.groups().isEmpty())result.add(new Blocker(key.quest(),"CONDITION_THRESHOLD",true,"Native quest "+key.quest()+" requires "+node.template().getRequiredConditionCount()+" fulfilled groups but only "+node.groups().size()+" exist"));
   for(var group:node.groups()){result.addAll(guards(view,key.quest(),group));for(var dep:group.needed())if(!solved.containsKey(dep))queue.add(dep);}
  }
  if(result.isEmpty())result.add(new Blocker(root.quest(),"CYCLE_OR_CONFLICT",true,"No finite compatible native prerequisite branch exists for quest "+root.quest()+"; unresolved requirements="+visited+" (cycle, incompatible reward choices or forbidden planned completion)"));
  return List.copyOf(result);
 }
 private static final class NativeView implements View {
  private final Player bot;private final Map<Integer,List<Blocker>> eligibility=new HashMap<>(),capability=new HashMap<>();
  NativeView(Player bot){this.bot=bot;}
  public QuestTemplate template(int quest){return DataManager.QUEST_DATA.getQuestById(quest);}
  public QuestState state(int quest){return bot.getQuestStateList().getQuestState(quest);}
  public int title(){return bot.getCommonData().getTitleId();}
  public QuestBotRewardChoices rewardChoices(int quest){return QuestEngine.getInstance().getPlayerBotRewardChoices(bot,quest);}
  public List<Blocker> eligibility(int quest){return eligibility.computeIfAbsent(quest,id->{
   var t=template(id);if(t==null)return List.of();var result=new ArrayList<Blocker>();
   if(t.getRacePermitted()!=null && t.getRacePermitted()!=Race.PC_ALL && t.getRacePermitted()!=bot.getRace())result.add(new Blocker(id,"RACE",true,"Native quest "+id+" requires race "+t.getRacePermitted()+"; actor race="+bot.getRace()));
   if(!t.getClassPermitted().isEmpty() && !t.getClassPermitted().contains(bot.getPlayerClass()))result.add(new Blocker(id,"CLASS",true,"Native quest "+id+" permits classes "+t.getClassPermitted()+"; actor class="+bot.getPlayerClass()));
   if(t.getGenderPermitted()!=null && t.getGenderPermitted()!=bot.getGender())result.add(new Blocker(id,"GENDER",true,"Native quest "+id+" requires gender "+t.getGenderPermitted()+"; actor gender="+bot.getGender()));
   if(bot.getLevel()<t.getMinlevelPermitted())result.add(new Blocker(id,"MIN_LEVEL",false,"Native quest "+id+" requires level "+t.getMinlevelPermitted()+"; actor level="+bot.getLevel()));
   if(t.getMaxlevelPermitted()!=0 && bot.getLevel()>t.getMaxlevelPermitted())result.add(new Blocker(id,"MAX_LEVEL",true,"Native quest "+id+" permits maximum level "+t.getMaxlevelPermitted()+"; actor level="+bot.getLevel()));
   if(t.getRequiredRank()!=0 && bot.getAbyssRank().getRank().getId()<t.getRequiredRank())result.add(new Blocker(id,"ABYSS_RANK",false,"Native quest "+id+" requires Abyss rank "+t.getRequiredRank()));
   var env=new QuestEnv(null,bot,id);if(!QuestService.checkCombineSkill(env,false))result.add(new Blocker(id,"PROFESSION",false,"Native quest "+id+" requires profession "+t.getCombineSkill()+" at "+t.getCombineSkillPoint()+" (native any-profession/faction/task forty-point rules apply)"));
   var model=DataManager.XML_QUESTS.getQuest(id);if(model instanceof com.aionemu.gameserver.questEngine.handlers.models.CraftingRewardsData && !PlayerBotQuestCrafting.qualificationAllowed(bot,PlayerBotQuestConversations.number(model,"levelReward")))result.add(new Blocker(id,"PROFESSION_CAPACITY",false,"Native crafting qualification slot limit rejects quest "+id));
   if(!QuestService.inventoryItemCheck(env,false))result.add(new Blocker(id,"INVENTORY",false,"Native inventory acceptance requirement rejects quest "+id));
   if(t.getNpcFactionId()!=0){var faction=bot.getNpcFactions().getFactionById(t.getNpcFactionId());if(faction==null || !faction.isActive())result.add(new Blocker(id,"FACTION",false,"Native NPC faction "+t.getNpcFactionId()+" is not active for quest "+id));else if(!t.isTimeBased() && !bot.getNpcFactions().canStartQuest(t))result.add(new Blocker(id,"FACTION_DAILY",false,"Native faction daily eligibility rejects quest "+id));}
   return List.copyOf(result);
  });}
  public List<Blocker> capability(int quest){return capability.computeIfAbsent(quest,id->{
   String disabled=PlayerBotQuestFeatures.disabled(id);if(!disabled.isEmpty())return List.of(new Blocker(id,"FEATURE_DISABLED",false,disabled));
   if(!QuestEngine.getInstance().isHaveHandler(id))return List.of(new Blocker(id,"NATIVE_HANDLER_MISSING",false,"No registered native handler can execute prerequisite quest "+id));
   var state=state(id);if(state!=null && (state.getStatus()==QuestStatus.START || state.getStatus()==QuestStatus.REWARD)){
    var report=PlayerBotQuestCapabilities.describe(bot,id,DataManager.XML_QUESTS.getQuest(id));String action=String.valueOf(report.get("action"));
    if(Set.of("UNSUPPORTED_STAGE","UNAVAILABLE","DISABLED").contains(action))return List.of(new Blocker(id,"CURRENT_STAGE_UNSUPPORTED",false,"Native prerequisite "+id+": "+report.get("reason")));
   }else {
    // Empty discovery is not proof of missing support: native level/enter-world
    // starts can legitimately have no manual action. Only an explicit handler
    // declaration of unavailable adapter support blocks this preaccept branch.
    var declared=QuestEngine.getInstance().getPlayerBotActions(bot,id);
    if(!declared.isEmpty() && declared.stream().allMatch(a->a.kind()==QuestBotAction.Kind.EVENT && a.targetId()==0)
     && declared.stream().anyMatch(a->{String reason=a.reason().toLowerCase(Locale.ROOT);return reason.contains("adapter is missing") || reason.contains("unsupported") || reason.contains("handler is unavailable");}))
     return List.of(new Blocker(id,"DECLARED_PREACCEPT_UNSUPPORTED",false,"Native prerequisite "+id+" explicitly declares unavailable support: "+declared.stream().map(QuestBotAction::reason).toList()));
   }
   return List.of();
  });}
 }
 private PlayerBotQuestPrerequisites() {}
}
