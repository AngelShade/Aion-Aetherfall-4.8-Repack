package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Real native tapping item custody and qualification probes; never execute native rewards/grants. */
public final class NativeTappingQuestCheck {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final String[] TESTS={"_19000ExpertEssencetappersTest","_19002ExpertAethertappersTest","_29000ExpertEssencetappersTest","_29002ExpertAethertappersTest"};
    static final int[] SENDERS={203780,203782,204096,204257}, EQUIPMENT_NPC={203781,203783,204097,204099}, EQUIPMENT={122001250,122001251,122001250,122001251};
    static final String[] QUALIFICATIONS={"_19001ExpertEssencetappingExpert","_19003ExpertAethertappingExpert","_29001ExpertEssencetappingExpert","_29003ExpertAethertappingExpert"};
    static final int[] TOKENS={182206127,182206128,182207141,182207142}, SKILLS={30002,30003,30002,30003}, RECIPIENTS={203700,203700,204052,204052};
    static AbstractQuestHandler handler(String name)throws Exception{return (AbstractQuestHandler)Class.forName("quest.crafting."+name).getConstructor().newInstance();}
    static void qualificationProbe(int index)throws Exception{
        var handler=handler(QUALIFICATIONS[index]);var bot=NativeSielQuestCheck.bot(handler.getQuestId(),QuestStatus.REWARD,0);
        bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(SKILLS[index],399,0,PersistentState.NOACTION)));bot.cube.counts.put(TOKENS[index],2L);
        Integer page=null;
        try{page=NativeSielQuestCheck.offered(handler,bot,RECIPIENTS[index],DialogAction.SELECT_QUEST_REWARD);}
        catch(RuntimeException failure){check(bot.skills.getSkillLevel(SKILLS[index])==399,"Opening native qualification reward must not grant the profession rank");throw failure;}
        check(bot.skills.getSkillLevel(SKILLS[index])==399,"Opening native qualification reward must not grant the profession rank");
        check(Objects.equals(page,5)&&bot.cube.getItemCountByItemId(TOKENS[index])==2&&bot.cube.removals==0,"Native qualification reward opening preserves owned token and offered page");
        bot.cube.counts.clear();check(NativeSielQuestCheck.offered(handler,bot,RECIPIENTS[index],DialogAction.SELECTED_QUEST_REWARD1)==null
            &&bot.quests.getQuestState(handler.getQuestId()).getStatus()==QuestStatus.REWARD&&bot.skills.getSkillLevel(SKILLS[index])==399,"Missing native work item cannot grant completion or qualification");
    }
    static void collectionCustody(int index)throws Exception{
        var handler=handler(TESTS[index]);int quest=handler.getQuestId();var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,1);
        var template=DataManager.QUEST_DATA.getQuestById(quest);
        for(var item:template.getCollectItems().getCollectItem())bot.cube.counts.put(item.getItemId(),3L);
        NativeSielQuestCheck.offered(handler,bot,SENDERS[index],index%2==0?DialogAction.CHECK_USER_HAS_QUEST_ITEM:DialogAction.QUEST_SELECT);
        check(bot.quests.getQuestState(quest).getStatus()==QuestStatus.REWARD,"Original tapping collection turn-in admits reward");
        for(var item:template.getCollectItems().getCollectItem())check(bot.cube.getItemCountByItemId(item.getItemId())==2,"Native tapping turn-in must preserve surplus owned collection items");
    }
    public static void main(String[] args)throws Exception{
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var old=DataManager.QUEST_DATA;
        try{
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller()
                .unmarshal(Path.of(args[0],"data/static_data/quest_data/quest_data.xml").toFile());
            if(args.length>1&&args[1].equals("--rank-probe-only")){qualificationProbe(0);return;}
            if(args.length>1&&args[1].equals("--surplus-only")){collectionCustody(0);return;}
            for(int i=0;i<4;i++){
                qualificationProbe(i);collectionCustody(i);var test=handler(TESTS[i]);int quest=test.getQuestId();
                var bot=NativeSielQuestCheck.bot(quest,null,0);var acceptance=test.getPlayerBotActions(bot).getFirst();
                check(acceptance.targetId()==SENDERS[i]&&Objects.equals(NativeSielQuestCheck.offered(test,bot,SENDERS[i],DialogAction.QUEST_SELECT),4762),"Actual tapping test native start page");
                bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,0);var equipment=test.getPlayerBotActions(bot).getFirst();
                check(equipment.targetId()==EQUIPMENT_NPC[i]&&equipment.dialogAction()==DialogAction.SETPRO1
                    &&Objects.equals(NativeSielQuestCheck.offered(test,bot,EQUIPMENT_NPC[i],DialogAction.QUEST_SELECT),1011),"Actual native equipment conversation, no fixture item grant");
                check(!test.onEnterWorldEvent(new QuestEnv(null,bot,quest)),"Missing owned equipment cannot recover acquisition");
                bot.cube.counts.put(EQUIPMENT[i],1L);check(test.getPlayerBotActions(bot).getFirst().kind()==QuestBotAction.Kind.EVENT,"Already owned equipment is not requested twice");
                check(test.onEnterWorldEvent(new QuestEnv(null,bot,quest))&&bot.quests.getQuestState(quest).getQuestVarById(0)==1
                    &&bot.cube.getItemCountByItemId(EQUIPMENT[i])==1,"Actual native owned equipment entry resumes original acquisition transition");
                check(!test.onEnterWorldEvent(new QuestEnv(null,bot,quest)),"Owned equipment recovery cannot replay a progressed stage");
                var objective=test.getPlayerBotActions(bot).getFirst();var requirements=new HashMap<Integer,Long>();
                for(var item:DataManager.QUEST_DATA.getQuestById(quest).getCollectItems().getCollectItem())requirements.put(item.getItemId(),item.getCount().longValue());
                check(objective.requiredItems().equals(requirements),"Exact actual tapping collection requirements");
                var qualification=handler(QUALIFICATIONS[i]);var applicant=NativeSielQuestCheck.bot(qualification.getQuestId(),null,0);
                var accept=qualification.getPlayerBotActions(applicant).getFirst();check(accept.targetId()==SENDERS[i]&&accept.continuation().getFirst().action()==DialogAction.QUEST_ACCEPT_1,
                    "Exceptional native qualification accepts only its declared ACCEPT_1 action");
                check(Objects.equals(NativeSielQuestCheck.offered(qualification,applicant,SENDERS[i],DialogAction.QUEST_SELECT),1011)
                    &&Objects.equals(NativeSielQuestCheck.offered(qualification,applicant,SENDERS[i],accept.dialogAction()),4),"Actual qualification acceptance pages");
                applicant=NativeSielQuestCheck.bot(qualification.getQuestId(),QuestStatus.START,0);applicant.cube.counts.put(TOKENS[i],1L);
                var report=qualification.getPlayerBotActions(applicant).getFirst();check(report.targetId()==RECIPIENTS[i]&&report.requiredItems().equals(Map.of(TOKENS[i],1L)),"Actual qualification report requires its owned native work item");
                check(Objects.equals(NativeSielQuestCheck.offered(qualification,applicant,RECIPIENTS[i],report.dialogAction()),2375)
                    &&applicant.quests.getQuestState(qualification.getQuestId()).getStatus()==QuestStatus.REWARD,"Original native recipient alone opens qualification reward");
            }
            System.out.println("OK: "+checks+" actual tapping native chain/custody/rank-probe checks; no World/SQL/IDs/grants/rewards");
        }finally{DataManager.QUEST_DATA=old;}
    }
}
