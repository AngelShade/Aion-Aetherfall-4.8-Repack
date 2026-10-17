package com.aionemu.gameserver.questEngine;

import java.nio.file.*;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;

/** Actual 1917 reports/story replies and 1918 native owned-axe custody; no World/SQL/grants/rewards. */
public final class NativeStoryFollowUpCheck {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static void axeCustody(AbstractQuestHandler handler)throws Exception{
        var bot=NativeStoryRewardChoicesCheck.actor(1918,QuestStatus.REWARD,1);bot.cube.counts.put(182206002,2L);
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,bot,203835,DialogAction.USE_OBJECT),2375),"Original axe recipient opens native report page");
        check(bot.cube.getItemCountByItemId(182206002)==2&&bot.cube.removals==0,"Initial native axe conversation preserves owned surplus");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,bot,203835,DialogAction.SELECT_QUEST_REWARD),5),"Original axe recipient opens actually earned native reward page");
        check(bot.cube.getItemCountByItemId(182206002)==2&&bot.cube.removals==0,"Opening an axe reward page must not debit an owned quest item");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,bot,203835,DialogAction.SELECT_QUEST_REWARD),5),"Reopening actual axe reward page remains available");
        check(bot.cube.getItemCountByItemId(182206002)==2&&bot.cube.removals==0,"Reward retries must not repeatedly consume the owned axe");
        var state=bot.quests.getQuestState(1918);
        check(state.getStatus()==QuestStatus.REWARD&&state.getCompleteCount()==0,"Reward dialog probes do not claim native final completion");
        QuestService.removeQuestWorkItems(bot,state);
        check(bot.cube.getItemCountByItemId(182206002)==1&&bot.cube.removals==1,"Actual native successful-finish cleanup owns exactly one axe debit and preserves surplus");
        // Exercise cleanup directly on inert owned stacks; this does not pretend that a final reward transaction ran.
        var missing=NativeStoryRewardChoicesCheck.actor(1918,QuestStatus.REWARD,1);
        check(NativeStoryRewardChoicesCheck.offered(handler,missing,203835,DialogAction.USE_OBJECT)==null
            &&NativeStoryRewardChoicesCheck.offered(handler,missing,203835,DialogAction.SELECT_QUEST_REWARD)==null,
            "Missing owned axe cannot authorize native reward/report interaction");
        check(missing.quests.getQuestState(1918).getStatus()==QuestStatus.REWARD&&missing.cube.counts.isEmpty(),"Rejected axe interaction grants or mutates nothing");
    }
    static void mystery()throws Exception{
        var handler=NativeStoryRewardChoicesCheck.handler("quest.sanctum._1917ALingeringMystery");
        var absent=NativeStoryRewardChoicesCheck.actor(1917,null,0);
        var accept=handler.getPlayerBotActions(absent).getFirst();
        check(accept.targetId()==203835&&accept.dialogAction()==DialogAction.ASK_QUEST_ACCEPT&&accept.page()==1011,"Original Seirenia follow-up enrollment route");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,absent,203835,DialogAction.QUEST_SELECT),1011)
            &&Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,absent,203835,DialogAction.ASK_QUEST_ACCEPT),4),"Original follow-up enrollment offers actual1011/4 native pages");
        check(absent.quests.getQuestState(1917)==null,"Opening follow-up acceptance pages does not start or reward quest");
        check(handler.getPlayerBotRewardChoices(absent).groups().equals(Set.of(0,1,2)),"Reviewed mystery exposes exactly three original story replies");
        for(int group=0;group<3;group++){
            var bot=NativeStoryRewardChoicesCheck.actor(1917,QuestStatus.START,0);var state=bot.quests.getQuestState(1917);
            var first=handler.getPlayerBotActions(bot,group).getFirst();
            check(first.targetId()==203075&&first.dialogAction()==DialogAction.SETPRO1&&first.page()==1352,"Actual mystery begins with Namus report before branch");
            check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,bot,first.targetId(),DialogAction.QUEST_SELECT),first.page()),"Native Namus report offers declared1352 page");
            NativeStoryRewardChoicesCheck.offered(handler,bot,first.targetId(),first.dialogAction());
            check(state.getQuestVarById(0)==1&&state.getStatus()==QuestStatus.START&&state.getRewardGroup()==null,"Original Namus report advances real stage without committing story group");
            var second=handler.getPlayerBotActions(bot,group).getFirst();
            check(second.targetId()==203835&&second.page()==1693&&second.dialogAction()==new int[]{DialogAction.SETPRO1,DialogAction.SETPRO2,DialogAction.SETPRO3}[group],"Required mystery group selects its actual native reply");
            check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,bot,second.targetId(),DialogAction.QUEST_SELECT),1693),"Actual Seirenia report offers its declared page");
            check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,bot,second.targetId(),second.dialogAction()),5+group),"Actual native story reply emits matching reward group page");
            check(state.getStatus()==QuestStatus.REWARD&&Objects.equals(state.getRewardGroup(),group)&&state.getCompleteCount()==0,"Only original native handler commits mystery group; final reward remains separate");
            check(handler.getPlayerBotRewardChoices(bot).groups().equals(Set.of(group)),"Committed mystery group is preserved in discovery");
            check(handler.getPlayerBotActions(bot,group).getFirst().kind()==QuestBotAction.Kind.REWARD,"Mystery earned reward remains actionable");
            check(handler.getPlayerBotActions(bot,(group+1)%3).getFirst().kind()==QuestBotAction.Kind.EVENT&&Objects.equals(state.getRewardGroup(),group),"Opposite requested mystery group cannot rewrite committed state");
            check(bot.cube.counts.isEmpty(),"Mystery native reports do not fabricate items");
            state.setStatus(QuestStatus.COMPLETE);
            check(handler.getPlayerBotActions(bot,group).isEmpty()&&handler.getPlayerBotRewardChoices(bot).groups().equals(Set.of(group)),"Completed mystery cannot replay its native story rewards");
        }
        var invalid=NativeStoryRewardChoicesCheck.actor(1917,QuestStatus.START,7);
        check(handler.getPlayerBotActions(invalid,0).getFirst().kind()==QuestBotAction.Kind.EVENT
            &&handler.getPlayerBotRewardChoices(invalid).groups().isEmpty(),"Inconsistent mystery stage cannot invent a report");
    }
    static void axe()throws Exception{
        var handler=NativeStoryRewardChoicesCheck.handler("quest.sanctum._1918AnAxForNamus");axeCustody(handler);
        var absent=NativeStoryRewardChoicesCheck.actor(1918,null,0);var accept=handler.getPlayerBotActions(absent).getFirst();
        check(accept.targetId()==203835&&accept.page()==1011&&accept.dialogAction()==DialogAction.ASK_QUEST_ACCEPT,"Original axe enrollment route");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,absent,203835,DialogAction.QUEST_SELECT),1011)
            &&Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,absent,203835,DialogAction.ASK_QUEST_ACCEPT),4),"Original axe enrollment offers real1011/4 native pages");
        var active=NativeStoryRewardChoicesCheck.actor(1918,QuestStatus.START,0);var report=handler.getPlayerBotActions(active).getFirst();
        check(report.targetId()==203788&&report.page()==1352&&report.dialogAction()==DialogAction.SETPRO1,"Declared axe acquisition uses original native Phomona report");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(handler,active,report.targetId(),DialogAction.QUEST_SELECT),1352)
            &&active.cube.counts.isEmpty()&&active.quests.getQuestState(1918).getStatus()==QuestStatus.START,"Actual acquisition page grants no item; native new-item operation remains gameplay boundary");
        var reward=NativeStoryRewardChoicesCheck.actor(1918,QuestStatus.REWARD,1);
        check(handler.getPlayerBotActions(reward).getFirst().requiredItems().equals(Map.of(182206002,1L)),"Shared axe reward intent protects actual required owned work item");
        reward.quests.getQuestState(1918).setStatus(QuestStatus.COMPLETE);
        check(handler.getPlayerBotActions(reward).isEmpty(),"Completed axe quest cannot replay its reward");
        var invalid=NativeStoryRewardChoicesCheck.actor(1918,QuestStatus.START,7);
        check(handler.getPlayerBotActions(invalid).getFirst().kind()==QuestBotAction.Kind.EVENT,"Inconsistent axe stage has specific unmet native transition");
    }
    static AbstractQuestHandler oldHandler(Path classFile)throws Exception{
        var name="quest.sanctum._1918AnAxForNamus";byte[] bytes=Files.readAllBytes(classFile);
        var loader=new ClassLoader(NativeStoryFollowUpCheck.class.getClassLoader()){
            @Override protected Class<?> loadClass(String type,boolean resolve)throws ClassNotFoundException{
                if(!type.equals(name))return super.loadClass(type,resolve);
                var loaded=findLoadedClass(type);if(loaded==null)loaded=defineClass(type,bytes,0,bytes.length);
                if(resolve)resolveClass(loaded);return loaded;
            }
        };
        return (AbstractQuestHandler)loader.loadClass(name).getConstructor().newInstance();
    }
    public static void main(String[] args)throws Exception{
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var oldQuest=DataManager.QUEST_DATA;
        try{
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(Path.of(args[0]).resolve("data/static_data/quest_data/quest_data.xml").toFile());
            if(args.length>1){axeCustody(oldHandler(Path.of(args[1])));throw new AssertionError("Old normal handler unexpectedly passed custody regression");}
            mystery();axe();System.out.println("OK: "+checks+" actual native mystery/axe report/story-group/custody checks; no World/SQL/IDs/new items/final rewards");
        }finally{DataManager.QUEST_DATA=oldQuest;}
    }
}
