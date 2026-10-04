package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.handlers.models.*;
import com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.conditions.QuestConditions;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.model.DialogAction.*;

/** Native intermediate objectives, separate from acceptance and reward actions. */
public final class PlayerBotQuestConversations {
 record Step(int quest,int npc,int action,int page,int variables) {}
 record Witness(Step step,long expires) {}
 private static final Map<String,Witness> WITNESSES=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> RETRY=new ConcurrentHashMap<>();
 static Object field(Object object,String name) {
  if(object==null)return null;for(Class<?> t=object.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException e){}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}return null;
 }
 static List<?> list(Object object,String name){Object value=field(object,name);return value instanceof List<?> l ? l : List.of();}
 static int number(Object object,String name){Object value=field(object,name);return value instanceof Number n ? n.intValue() : 0;}
 public static Integer before(Player player,int quest) {var q=player.getQuestStateList().getQuestState(quest);return !player.isPlayerBot() && q!=null && q.getStatus()==QuestStatus.START ? q.getQuestVars().getQuestVars() : null;}
 public static void observed(Player player,Npc npc,int quest,int action,int page,Integer before) {
  try {
   if(before==null || player.isPlayerBot())return;var q=player.getQuestStateList().getQuestState(quest);
   if(q==null || q.getStatus()!=QuestStatus.START || q.getQuestVars().getQuestVars()==before)return;
   WITNESSES.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());if(WITNESSES.size()>1024)return;
   WITNESSES.put(player.getObjectId()+":"+quest+":"+before,new Witness(new Step(quest,npc.getNpcId(),action,page,before),System.currentTimeMillis()+1800000));
  }catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(PlayerBotQuestConversations.class).warn("Companion conversation observation failed for quest {}",quest,error);}
 }
 static List<Step> steps(Player owner,Player bot,int quest) {
  var q=bot.getQuestStateList().getQuestState(quest);if(q==null || q.getStatus()!=QuestStatus.START)return List.of();int variables=q.getQuestVars().getQuestVars(),stage=q.getQuestVarById(0);
  Witness witnessed=owner==null ? null : WITNESSES.get(owner.getObjectId()+":"+quest+":"+variables);
  if(witnessed!=null && witnessed.expires>System.currentTimeMillis())return List.of(witnessed.step);
  var model=DataManager.XML_QUESTS==null ? null : DataManager.XML_QUESTS.getQuest(quest);List<Step> result=new ArrayList<>();
  if(model instanceof ItemCollectingData && number(model,"nextNpcId")>0 && stage==0)result.add(new Step(quest,number(model,"nextNpcId"),SETPRO1,1352,variables));
  if(model instanceof ReportToManyData) {
   var infos=list(model,"npcInfos");if(stage>=0 && stage<infos.size()-1) {
    boolean driven=DataManager.QUEST_DATA.getQuestById(quest).isDataDriven();for(int id:((NpcInfos)infos.get(stage)).getNpcIds())result.add(new Step(quest,id,SETPRO1,(driven ? 1011 : 1352)+stage*341,variables));
   }
  }
  if(model instanceof XmlQuestData)for(var event:list(model,"onTalkEvents"))for(var variable:list(event,"var"))if(number(variable,"value")==variables)
   for(var npc:list(variable,"npc")) {
    List<Integer> actions=new ArrayList<>();for(var dialog:list(npc,"dialog")) {
     int action=number(dialog,"id");if(action==QUEST_SELECT || action==USE_OBJECT || action==SELECT_QUEST_REWARD || action>=SELECTED_QUEST_REWARD1 && action<=SELECTED_QUEST_REWARD1+14 || action==SELECTED_QUEST_NOREWARD)continue;
     var operations=list(field(dialog,"operations"),"operations");
     boolean progress=operations.stream().anyMatch(o->o instanceof com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.SetQuestVarOperation);
     boolean unsafe=operations.stream().anyMatch(o->o instanceof com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.StartQuestOperation || o instanceof com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.operations.SetQuestStatusOperation && field(o,"status")!=QuestStatus.START);
     if(!progress || unsafe)continue;
     actions.add(action);
    }
    // Branching story choices require a successfully observed owner's choice.
    if(actions.size()==1)result.add(new Step(quest,number(npc,"id"),actions.getFirst(),0,variables));
   }
  return List.copyOf(result);
 }
 static Set<Integer> objectiveIds(Player owner,Player bot,int quest){Set<Integer> ids=new TreeSet<>();steps(owner,bot,quest).forEach(s->ids.add(s.npc));return ids;}
 static boolean tick(PlayerBotSession session,PlayerBotNavigation navigation,boolean allowed) {
  if(!allowed)return false;Player owner=session.owner(),bot=session.bot();long now=System.currentTimeMillis();if(now<RETRY.getOrDefault(bot.getObjectId(),0L))return false;
  List<Step> candidates=new ArrayList<>();for(var q:bot.getQuestStateList().getUncompletedQuests())if(!PlayerBotQuestSync.skipped(bot,q.getQuestId()) && PlayerBotQuestSync.wanted(owner,bot,q.getQuestId()))candidates.addAll(steps(owner,bot,q.getQuestId()));
  List<Npc> npcs=new ArrayList<>();owner.getKnownList().forEachNpc(npc->{if(npc.isSpawned() && !npc.isDead() && !bot.isEnemy(npc) && npc.getWorldId()==owner.getWorldId() && npc.getInstanceId()==owner.getInstanceId() && PositionUtil.isInRange(owner,npc,25) && DialogService.isInteractionAllowed(bot,npc))npcs.add(npc);});
  for(Npc npc:npcs.stream().sorted(Comparator.comparingDouble(n->PositionUtil.getDistance(bot,n))).toList())for(Step step:candidates)if(step.npc==npc.getNpcId() && owner.getKnownList().sees(npc) && bot.getKnownList().sees(npc) && PlayerBotQuestObjectives.matches(bot,PlayerBotQuestRoutes.Kind.CONVERSATION,step.quest,npc)) {
   if(!PositionUtil.isInTalkRange(bot,npc) || !GeoService.getInstance().canSee(bot,npc))return navigation.approach(npc,2);
   navigation.stop();boolean success=interact(bot,npc,step);PlayerBotQuestObjectives.attempted(bot,success);RETRY.put(bot.getObjectId(),now+(success ? 800 : 5000));
   if(success){PlayerBotQuestSync.notice(bot,"I spoke to "+npc.getName()+" and advanced "+com.aionemu.gameserver.utils.ChatUtil.quest(step.quest)+". My next objective is in the party quest journal.","quest-step:"+step.quest+":"+step.variables,0);PlayerBotQuestSync.returnToOwner(bot);}return success;
  }
  return false;
 }
 static boolean interact(Player bot,Npc npc,Step step) {
  var q=bot.getQuestStateList().getQuestState(step.quest);if(q==null || q.getStatus()!=QuestStatus.START || q.getQuestVars().getQuestVars()!=step.variables)return false;
  try(var dialog=PlayerBotQuestDialog.open(bot.getObjectId(),npc.getObjectId(),step.quest)) {
   npc.getController().onDialogSelect(QUEST_SELECT,0,bot,step.quest,0);
   if(q.getQuestVars().getQuestVars()!=step.variables)return q.getStatus()==QuestStatus.START;
   Object page=field(dialog,"page");if(!(page instanceof Integer offered) || offered<=0 || step.page>0 && offered!=step.page)return false;
   npc.getController().onDialogSelect(step.action,offered,bot,step.quest,0);
   return q.getStatus()==QuestStatus.START && q.getQuestVars().getQuestVars()!=step.variables;
  }finally{DialogService.onCloseDialog(bot,npc);}
 }
 static void close(Player bot){RETRY.remove(bot.getObjectId());}
 private PlayerBotQuestConversations(){}
}
