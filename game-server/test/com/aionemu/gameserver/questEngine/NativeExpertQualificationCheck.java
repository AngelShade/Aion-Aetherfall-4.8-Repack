package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.CraftConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Real expert qualification handlers: native slots, owned crafted requirements and native turn-in. */
public final class NativeExpertQualificationCheck {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final String[] TYPES={"_1972ExpertPassionforWeaponsmithing","_1973ExpertExpertofWeaponsmithing",
        "_1974ExpertPassionforArmorsmithing","_1975ExpertExpertofArmorsmithing","_1976ExpertPassionforHandicrafting",
        "_1977ExpertExpertofHandicrafting","_1980ExpertPassionforAlchemy","_1981ExpertExpertofAlchemy",
        "_1982ExpertAPassionForTailoring","_1983ExpertExpertofTailoring","_2972ExpertWeaponsmithsTest",
        "_2973ExpertWeaponsmithsFinalExam","_2974ExpertArmorsmithsTest","_2975ExpertArmorsmithsFinalExam",
        "_2976ExpertHandicraftersTest","_2977ExpertHandicraftersFinalExam","_2980ExpertAlchemistsTest",
        "_2981ExpertAlchemistsFinalExam","_2982ExpertTailorsTest","_2983ExpertTailorsFinalExam",
        "_19052ExpertPassionforConstruction","_19053ExpertExpertofConstruction","_19055ExpertConstructorsFinalExam",
        "_29052ExpertPassionforConstruction","_29053ExpertExpertofConstruction","_29055ExpertConstructorsFinalExam", "_1978ExpertPassionforCooking", "_1979ExpertExpertofCooking", "_2978ExpertCooksTest", "_2979ExpertCooksFinalExam"};
    static final int[] NPC={203788,203788,203790,203790,203792,203792,203786,203786,203793,203793,
        204104,204104,204106,204106,204108,204108,204102,204102,204110,204110,798450,798450,798450,798452,798452,798452,203784,203784,204100,204100};
    static final int[] PRODUCT={182206911,182206893,182206912,182206894,182206914,182206896,182206915,182206897,182206913,182206895,
        182207964,182207946,182207965,182207947,182207967,182207949,182207968,182207950,182207966,182207948,0,0,0,0,0,0,182206919,182206902,182207972,182207955};
    public static void main(String[] args)throws Exception{
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var old=DataManager.QUEST_DATA;int slots=CraftConfig.MAX_EXPERT_CRAFTING_SKILLS;
        try{
            CraftConfig.MAX_EXPERT_CRAFTING_SKILLS=2;
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller()
                .unmarshal(Path.of(args[0],"data/static_data/quest_data/quest_data.xml").toFile());
            for(int i=0;i<TYPES.length;i++){
                var handler=(AbstractQuestHandler)Class.forName("quest.crafting."+TYPES[i]).getConstructor().newInstance();int quest=handler.getQuestId();
                check(handler.isBotCraftingQuest()&&!handler.isBotGatheringQuest(),"Explicit crafting capability without class name inference");
                var bot=NativeSielQuestCheck.bot(quest,null,0);var action=handler.getPlayerBotActions(bot).getFirst();
                check(action.targetId()==NPC[i]&&Objects.equals(NativeSielQuestCheck.offered(handler,bot,NPC[i],DialogAction.QUEST_SELECT),1011),"Actual native qualification sender/page");
                check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,NPC[i],action.dialogAction()),4),"Actual native acceptance window");
                check(bot.quests.getQuestState(quest)==null&&bot.cube.counts.isEmpty(),"Read-only preparation never grants native qualification");
                var blocked=NativeSielQuestCheck.bot(quest,null,0);
                blocked.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40001,400,0,PersistentState.NOACTION),new PlayerSkillEntry(40002,500,0,PersistentState.NOACTION)));
                var limit=handler.getPlayerBotActions(blocked).getFirst();
                check(limit.kind()==QuestBotAction.Kind.EVENT&&limit.targetId()==0&&limit.reason().contains("profession limit"),"Actual expert/master totals respect native expert slot limit");
                bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,0);var objective=handler.getPlayerBotActions(bot).getFirst();
                var template=DataManager.QUEST_DATA.getQuestById(quest);var requirements=new HashMap<Integer,Long>();
                for(var item:template.getCollectItems().getCollectItem())requirements.put(item.getItemId(),item.getCount().longValue());
                check(objective.requiredItems().equals(requirements),"Exact native owned crafted product/collection requirements");
                NativeSielQuestCheck.offered(handler,bot,NPC[i],DialogAction.QUEST_SELECT);
                check(bot.quests.getQuestState(quest).getStatus()==QuestStatus.START&&bot.cube.removals==0,"Missing owned crafted product cannot advance native qualification");
                for(var requirement:requirements.entrySet())bot.cube.counts.put(requirement.getKey(),requirement.getValue()+1);
                var owned=Map.copyOf(bot.cube.counts);objective=handler.getPlayerBotActions(bot).getFirst();
                check(bot.cube.counts.equals(owned)&&bot.quests.getQuestState(quest).getStatus()==QuestStatus.START,"Planning preserves native ownership and status");
                Integer offered=NativeSielQuestCheck.offered(handler,bot,NPC[i],objective.dialogAction());
                check(Objects.equals(offered,PRODUCT[i]>0?2375:5)&&bot.quests.getQuestState(quest).getStatus()==QuestStatus.REWARD,"Original native turn-in alone admits actual reward");
                for(var requirement:requirements.entrySet())check(bot.cube.getItemCountByItemId(requirement.getKey())==1,"Original native turn-in consumes exactly the required owned amount");
                check(bot.quests.getQuestState(quest).getQuestVarById(1)==5,"Other native saved progress remains");
                var reward=handler.getPlayerBotActions(bot).getFirst();check(reward.kind()==QuestBotAction.Kind.REWARD&&reward.targetId()==NPC[i],"Actual native reward destination");
                check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,NPC[i],DialogAction.SELECT_QUEST_REWARD),5),"Actual standard native reward page");
                var complete=NativeSielQuestCheck.bot(quest,QuestStatus.COMPLETE,0);check(handler.getPlayerBotActions(complete).isEmpty(),"Completed qualification cannot replay");
            }
            System.out.println("OK: "+checks+" actual expert qualification/native custody checks; no World/SQL/IDs/recipes/rewards");
        }finally{DataManager.QUEST_DATA=old;CraftConfig.MAX_EXPERT_CRAFTING_SKILLS=slots;}
    }
}
