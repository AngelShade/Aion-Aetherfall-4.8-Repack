package com.aionemu.gameserver.questEngine;

import java.nio.file.*;
import java.io.StringReader;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.CraftConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.craft.MasterQuestsList;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.questEngine.handlers.template.CraftingRewards;
import com.aionemu.gameserver.questEngine.model.*;

/** Actual fourteen final qualification XML chains and native 499 -> 500 report on inert actors. */
public final class NativeMasterFinalQualificationCheck {
    static int checks;
    static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static int number(Object object,String name)throws Exception{
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{
            var field=type.getDeclaredField(name);field.setAccessible(true);return field.getInt(object);
        }catch(NoSuchFieldException ignored){}
        throw new AssertionError("Missing actual native metadata "+name);
    }
    static void chain(int potential,Path root)throws Exception{
        int quest=potential+1;boolean asmo=quest>20000;var race=asmo?Race.ASMODIANS:Race.ELYOS;
        var capital=Files.readString(root.resolve("data/static_data/quest_script_data/"+(asmo?"pandaemonium":"sanctum")+".xml"));
        var match=java.util.regex.Pattern.compile("<crafting_rewards\\b(?=[^>]*\\bid=\""+quest+"\")[^>]*/>").matcher(capital);
        check(match.find(),"Actual final qualification XML exists for native potential "+potential);
        var models=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts>"+match.group()+"</quest_scripts>"));
        var model=models.getQuest(quest);int start=number(model,"startNpcId"),end=number(model,"endNpcId"),skill=number(model,"skillId"),rank=number(model,"levelReward"),movie=number(model,"questMovie");
        check(rank==500&&end==(asmo?798800:798600),"Actual native rank and final qualification NPC");
        check(Arrays.equals(MasterQuestsList.getQuestIds(skill,race),new int[]{quest,potential}),"Native profession enum agrees with complete paired chain");
        var template=DataManager.QUEST_DATA.getQuestById(quest);var handler=new CraftingRewards(quest,start,skill,rank,end,movie);
        check(handler.isBotCraftingQuest()&&!handler.isBotGatheringQuest(),"Final native qualification has explicit crafting capability");
        var conditions=template.getXMLStartConditions();check(conditions.size()==1&&template.getRequiredConditionCount()==1
            &&conditions.getFirst().getFinishedPreconditions().size()==1
            &&conditions.getFirst().getFinishedPreconditions().getFirst().getQuestId()==potential,"Actual XML requires this precise completed potential");
        var bot=NativeSielQuestCheck.bot(quest,null,0);bot.race=race;
        bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(skill,499,0,PersistentState.NOACTION)));
        check(!conditions.getFirst().check(bot,false),"Absent potential cannot satisfy native final qualification");
        var prerequisite=new QuestState(potential,QuestStatus.REWARD);bot.quests.addQuest(potential,prerequisite);
        check(!conditions.getFirst().check(bot,false),"Unclaimed potential reward cannot bypass native chain");
        prerequisite.setStatus(QuestStatus.COMPLETE);prerequisite.setCompleteCount(1);
        check(conditions.getFirst().check(bot,false),"Actually completed potential satisfies native prerequisite");
        var accept=handler.getPlayerBotActions(bot).getFirst();
        check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,start,DialogAction.QUEST_SELECT),accept.page()),"Actual native acceptance page");
        check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,start,DialogAction.ASK_QUEST_ACCEPT),4)
            &&bot.quests.getQuestState(quest)==null&&bot.skills.getSkillLevel(skill)==499,"Acceptance window does not grant rank or start a synthetic quest");
        var active=new QuestState(quest,QuestStatus.START);bot.quests.addQuest(quest,active);var report=handler.getPlayerBotActions(bot).getFirst();
        check(report.targetId()==end&&report.dialogAction()==DialogAction.SELECT_QUEST_REWARD
            &&Objects.equals(NativeSielQuestCheck.offered(handler,bot,end,DialogAction.QUEST_SELECT),report.page()),"Actual final native report action/page");
        check(bot.skills.getSkillLevel(skill)==499&&active.getStatus()==QuestStatus.START,"Opening report alone grants no rank");
        var existingRecipes=new HashSet<Integer>();
        for(var recipe:DataManager.RECIPE_DATA.getAutolearnRecipes(race,skill,500))existingRecipes.add(recipe.getId());
        // A saved learned-recipe snapshot prevents native autoLearnRecipes from calling its DAO.
        bot.recipes=new com.aionemu.gameserver.model.gameobjects.player.RecipeList(existingRecipes);
        check(existingRecipes.stream().allMatch(bot.recipes::isRecipePresent),"All actual native auto-learned recipes already belong to this inert actor; no SQL admission");
        check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,end,report.dialogAction()),5)
            &&bot.skills.getSkillLevel(skill)==500&&active.getStatus()==QuestStatus.REWARD,"Original native earned report grants rank500 at its legitimate report moment");
        var learned=bot.skills.getSkillEntry(skill);
        check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,end,DialogAction.SELECT_QUEST_REWARD),5)
            &&bot.skills.getSkillEntry(skill)==learned&&bot.skills.getSkillLevel(skill)==500&&active.getCompleteCount()==0,"Reward reopening neither duplicates rank nor fabricates final completion");
        check(bot.cube.counts.isEmpty()&&bot.recipes.getRecipeList().equals(existingRecipes),"Native report preserves existing item/recipe custody without offline grants");
        check(handler.getPlayerBotActions(bot).getFirst().kind()==QuestBotAction.Kind.REWARD,"Earned native reward remains separately actionable");
        active.setStatus(QuestStatus.COMPLETE);active.setCompleteCount(1);check(handler.getPlayerBotActions(bot).isEmpty(),"Completed native qualification cannot replay");
    }
    public static void main(String[] args)throws Exception{
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var oldQuest=DataManager.QUEST_DATA;var oldRecipe=DataManager.RECIPE_DATA;int slots=CraftConfig.MAX_MASTER_CRAFTING_SKILLS;
        try{
            CraftConfig.MAX_MASTER_CRAFTING_SKILLS=1;var root=Path.of(args[0]);
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(root.resolve("data/static_data/quest_data/quest_data.xml").toFile());
            DataManager.RECIPE_DATA=(RecipeData)JAXBContext.newInstance(RecipeData.class).createUnmarshaller().unmarshal(root.resolve("data/static_data/recipe/recipe_templates.xml").toFile());
            for(int potential:new int[]{19008,19014,19020,19026,19032,19038,19057,29008,29014,29020,29026,29032,29038,29057})chain(potential,root);
            System.out.println("OK: "+checks+" actual paired master prerequisite/native rank500 checks on inert actors; no World/SQL/IDs/casts/crafts/items/rewards");
        }finally{DataManager.QUEST_DATA=oldQuest;DataManager.RECIPE_DATA=oldRecipe;CraftConfig.MAX_MASTER_CRAFTING_SKILLS=slots;}
    }
}
