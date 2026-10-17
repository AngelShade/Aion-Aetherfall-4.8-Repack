package com.aionemu.gameserver.questEngine;

import java.nio.file.*;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import javax.xml.stream.*;
import com.aionemu.gameserver.configs.main.CraftConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.items.ItemId;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Actual master handlers: cold native pages, owned requirements, recipe metadata, retries and cost rejection. */
public final class NativeMasterQualificationCheck {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static final int[][] PROFESSIONS={
        {19008,203788,203789,182206764,152201706,155001744,152201707,155001745},
        {19014,203790,203791,182206765,152201806,155001844,152201807,155001845},
        {19020,203793,203794,182206766,152201962,155002000,152201963,155002001},
        {19026,203792,798013,182206767,152202049,155002087,152202050,155002088},
        {19032,203786,203787,182206768,152202147,155002185,152202148,155002186},
        {29008,204104,204105,182207898,152206708,155006746,152206709,155006747},
        {29014,204106,204107,182207899,152206808,155006846,152206809,155006847},
        {29020,204110,204111,182207900,152206964,155007002,152206965,155007003},
        {29026,204108,798060,182207901,152207051,155007089,152207052,155007090},
        {29032,204102,204103,182207902,152207149,155007187,152207150,155007188}};
    static final Map<Integer,String> NAMES=Map.of(19008,"MasterWeaponsmithsPotential",19014,"MasterArmorsmithsPotential",
        19020,"MasterTailorsPotential",19026,"MasterHandicraftersPotential",19032,"MasterAlchemistsPotential",
        19038,"MasterCooksPotential",19057,"MasterConstructorsPotential");
    static AbstractQuestHandler handler(int quest)throws Exception{
        return (AbstractQuestHandler)Class.forName("quest.crafting._"+quest+NAMES.get(quest>20000?quest-10000:quest)).getConstructor().newInstance();
    }
    static Map<Integer,Integer> designRecipes(Path source)throws Exception{
        var result=new HashMap<Integer,Integer>();var factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
        try(var input=Files.newInputStream(source)){
            var xml=factory.createXMLStreamReader(input);int item=0;
            while(xml.hasNext())if(xml.next()==XMLStreamConstants.START_ELEMENT){
                if(xml.getLocalName().equals("item_template"))item=Integer.parseInt(xml.getAttributeValue(null,"id"));
                if(xml.getLocalName().equals("craftlearn"))result.put(item,Integer.parseInt(xml.getAttributeValue(null,"recipeid")));
            }
            xml.close();
        }
        return result;
    }
    static void admission(AbstractQuestHandler handler,int npc)throws Exception{
        var bot=NativeSielQuestCheck.bot(handler.getQuestId(),null,0);var intent=handler.getPlayerBotActions(bot).getFirst();
        check(handler.isBotCraftingQuest()&&!handler.isBotGatheringQuest(),"Explicit registered native crafting capability");
        check(intent.targetId()==npc&&intent.page()==4762&&intent.continuation().getFirst().action()==DialogAction.QUEST_ACCEPT,"Native master acceptance intent");
        check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,npc,DialogAction.QUEST_SELECT),4762)
            &&Objects.equals(NativeSielQuestCheck.offered(handler,bot,npc,intent.dialogAction()),4),"Actual native acceptance pages");
        check(bot.quests.getQuestState(handler.getQuestId())==null&&bot.cube.counts.isEmpty(),"Discovery/acceptance probe grants nothing");
        bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40001,500,0,PersistentState.NOACTION)));
        var blocked=handler.getPlayerBotActions(bot).getFirst();
        check(blocked.kind()==QuestBotAction.Kind.EVENT&&blocked.targetId()==0&&blocked.reason().contains("profession limit"),"Actual native master profession slot limit");
        bot=NativeSielQuestCheck.bot(handler.getQuestId(),QuestStatus.COMPLETE,0);check(handler.getPlayerBotActions(bot).isEmpty(),"Completed master quest does not replay");
    }
    static void profession(int[] row,Map<Integer,Integer> recipes)throws Exception{
        int quest=row[0],npc=row[1],supplier=row[2],product=row[3];var handler=handler(quest);admission(handler,npc);
        var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,0);var choices=handler.getPlayerBotActions(bot);
        check(choices.size()==2&&choices.get(0).dialogAction()==DialogAction.SETPRO10&&choices.get(1).dialogAction()==DialogAction.SETPRO20,"Both actual native recipe alternatives remain declared");
        for(var choice:choices)check(choice.targetId()==supplier&&Objects.equals(NativeSielQuestCheck.offered(handler,bot,supplier,DialogAction.QUEST_SELECT),choice.page()),"Actual design supplier page, no native grant executed");
        for(int offset:new int[]{4,6}){
            check(Objects.equals(recipes.get(row[offset]),row[offset+1]),"Actual item XML craftlearn recipe mapping");
            bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,1);bot.cube.counts.put(row[offset],1L);
            var owned=handler.getPlayerBotActions(bot).getFirst();check(owned.kind()==QuestBotAction.Kind.ITEM&&owned.targetId()==row[offset],"Owned native design is learned instead of requested again");
            bot.recipes.getRecipeList().add(row[offset+1]);var turnIn=handler.getPlayerBotActions(bot).getFirst();
            check(turnIn.targetId()==npc&&turnIn.requiredItems().equals(Map.of(product,1L)),"Learned native recipe demands actual crafted product");
            NativeSielQuestCheck.offered(handler,bot,npc,DialogAction.QUEST_SELECT);
            check(bot.quests.getQuestState(quest).getStatus()==QuestStatus.START&&bot.cube.removals==0,"Missing crafted product cannot grant native reward");
            bot.cube.counts.put(product,3L);turnIn=handler.getPlayerBotActions(bot).getFirst();
            check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,npc,turnIn.dialogAction()),1352)
                &&bot.quests.getQuestState(quest).getStatus()==QuestStatus.REWARD&&bot.cube.getItemCountByItemId(product)==2,"Actual native turn-in consumes one and preserves surplus");
            check(bot.quests.getQuestState(quest).getQuestVarById(1)==5,"Unrelated saved quest variables preserved");
            check(handler.getPlayerBotActions(bot).getFirst().kind()==QuestBotAction.Kind.REWARD
                &&Objects.equals(NativeSielQuestCheck.offered(handler,bot,npc,DialogAction.SELECT_QUEST_REWARD),5),"Actual standard native reward page");
        }
    }
    static void construction(int quest,Map<Integer,Integer> recipes)throws Exception{
        boolean asmo=quest>20000;int npc=asmo?798452:798450,supplier=asmo?798453:798451;
        int[] designs=asmo?new int[]{152208541,152208542}:new int[]{152203543,152203544};
        int[] ids=asmo?new int[]{155008541,155008542}:new int[]{155003543,155003544};var handler=handler(quest);admission(handler,npc);
        for(int stage:new int[]{0,2}){
            var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,stage);var choices=handler.getPlayerBotActions(bot);
            check(choices.size()==2&&choices.get(0).requiredItems().equals(Map.of(ItemId.KINAH,167500L))
                &&choices.get(1).requiredItems().equals(Map.of(ItemId.KINAH,223000L)),"Native construction alternatives retain actual prices");
            check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,supplier,DialogAction.QUEST_SELECT),choices.getFirst().page()),"Actual initial/retry design page");
        }
        var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,1);var retry=handler.getPlayerBotActions(bot).getFirst();
        check(retry.kind()==QuestBotAction.Kind.EVENT&&retry.targetId()==npc,"Missing design resumes actual native retry callback");
        check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,npc,retry.dialogAction()),3398)
            &&bot.quests.getQuestState(quest).getQuestVarById(0)==2,"Original handler alone owns construction retry stage");
        for(int i=0;i<2;i++){
            check(Objects.equals(recipes.get(designs[i]),ids[i]),"Actual construction item recipe mapping");
            bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,1);bot.cube.counts.put(designs[i],1L);
            check(handler.getPlayerBotActions(bot).getFirst().targetId()==designs[i],"Owned construction design gets native learning intent");
            bot.recipes.getRecipeList().add(ids[i]);var turnIn=handler.getPlayerBotActions(bot).getFirst();var required=new HashMap<Integer,Long>();
            for(var item:DataManager.QUEST_DATA.getQuestById(quest).getCollectItems().getCollectItem())required.put(item.getItemId(),item.getCount().longValue());
            check(turnIn.requiredItems().equals(required),"Actual native construction collection requirements");
            check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,npc,turnIn.dialogAction()),2716)
                &&bot.quests.getQuestState(quest).getStatus()==QuestStatus.START,"Learned design alone earns no crafted-product credit");
            for(var entry:required.entrySet())bot.cube.counts.put(entry.getKey(),entry.getValue()+2);
            check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,npc,turnIn.dialogAction()),5)
                &&bot.quests.getQuestState(quest).getStatus()==QuestStatus.REWARD,"Original construction collection admits reward");
            for(var id:required.keySet())check(bot.cube.getItemCountByItemId(id)==2,"Native construction preserves surplus products");
        }
    }
    static void cookingCost(int quest)throws Exception{
        var handler=handler(quest);int supplier=quest>20000?204101:203785;
        int[] actions={DialogAction.SETPRO10,DialogAction.SETPRO20,DialogAction.SETPRO30,DialogAction.SETPRO40};
        for(int i=0;i<actions.length;i++){
            var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,2);bot.cube.counts.put(ItemId.KINAH,20000L);
            NativeSielQuestCheck.offered(handler,bot,supplier,actions[i]);
            check(bot.cube.getKinah()==20000&&bot.cube.removals==0,"Wrong-stage native cooking reply must not debit Kinah");
            bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,i*3);bot.cube.counts.put(ItemId.KINAH,6499L);
            NativeSielQuestCheck.offered(handler,bot,supplier,actions[i]);
            check(bot.cube.getKinah()==6499&&bot.quests.getQuestState(quest).getQuestVarById(0)==i*3,"Insufficient native funds cannot grant cooking design/progress");
        }
    }
    static void cooking(int quest,Map<Integer,Integer> recipes)throws Exception{
        boolean asmo=quest>20000;int npc=asmo?204100:203784,supplier=asmo?204101:203785;
        int product=asmo?182207907:182206773,design=asmo?152207202:152202200,recipe=asmo?155007241:155002239;
        var handler=handler(quest);admission(handler,npc);cookingCost(quest);
        int[] purchases={DialogAction.SETPRO10,DialogAction.SETPRO20,DialogAction.SETPRO30,DialogAction.SETPRO40};
        int[] reports={DialogAction.SETPRO11,DialogAction.SETPRO21,DialogAction.SETPRO31,DialogAction.SETPRO41};
        for(int step=0;step<4;step++){
            check(Objects.equals(recipes.get(design+step),recipe+step),"Actual cooking item recipe mapping");
            for(int offset:new int[]{0,1}){
                var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,step*3+offset);bot.cube.counts.put(ItemId.KINAH,20000L);
                var action=handler.getPlayerBotActions(bot).getFirst();check(action.dialogAction()==purchases[step]
                    &&action.requiredItems().equals(Map.of(ItemId.KINAH,6500L))
                    &&Objects.equals(NativeSielQuestCheck.offered(handler,bot,supplier,DialogAction.QUEST_SELECT),action.page()),"Actual cooking initial/retry purchase page and price");
            }
            var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,step*3+2);bot.cube.counts.put(design+step,1L);
            check(handler.getPlayerBotActions(bot).getFirst().targetId()==design+step,"Owned cooking design gets native item-learning intent");
            bot.recipes.getRecipeList().add(recipe+step);var action=handler.getPlayerBotActions(bot).getFirst();
            check(action.requiredItems().equals(Map.of(product+step,1L))&&action.dialogAction()==reports[step]
                &&Objects.equals(NativeSielQuestCheck.offered(handler,bot,npc,DialogAction.QUEST_SELECT),action.page()),"Only the current native cooking product is required");
            NativeSielQuestCheck.offered(handler,bot,npc,action.dialogAction());check(bot.quests.getQuestState(quest).getQuestVarById(0)==step*3+2,"Missing actual cooking product earns no stage credit");
            bot.cube.counts.put(product+step,3L);NativeSielQuestCheck.offered(handler,bot,npc,action.dialogAction());
            check(bot.cube.getItemCountByItemId(product+step)==2&&bot.quests.getQuestState(quest).getStatus()==(step==3?QuestStatus.REWARD:QuestStatus.START)
                &&bot.quests.getQuestState(quest).getQuestVarById(0)==(step==3?11:step*3+3),"Original native cooking turn-in owns sequential progress/reward and consumes one product");
            bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,step*3+2);
            check(handler.onFailCraftEvent(new QuestEnv(null,bot,quest),product+step)
                &&bot.quests.getQuestState(quest).getQuestVarById(0)==step*3+1,"Original failed-craft callback owns retry state");
        }
    }
    public static void main(String[] args)throws Exception{
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var old=DataManager.QUEST_DATA;int limit=CraftConfig.MAX_MASTER_CRAFTING_SKILLS;
        try{
            CraftConfig.MAX_MASTER_CRAFTING_SKILLS=1;var source=Path.of(args[0]);
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(source.resolve("data/static_data/quest_data/quest_data.xml").toFile());
            if(args.length>1&&args[1].equals("--cost-only")){cookingCost(19038);return;}
            var recipes=designRecipes(source.resolve("data/static_data/items/item_templates.xml"));
            for(var row:PROFESSIONS)profession(row,recipes);construction(19057,recipes);construction(29057,recipes);cooking(19038,recipes);cooking(29038,recipes);
            System.out.println("OK: "+checks+" actual master native dialog/metadata/custody/retry/cost checks; no World/SQL/IDs/crafts/design grants/rewards");
        }finally{DataManager.QUEST_DATA=old;CraftConfig.MAX_MASTER_CRAFTING_SKILLS=limit;}
    }
}
