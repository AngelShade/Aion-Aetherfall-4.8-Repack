package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.world.WorldMapType;
import quest.ishalgen._2002WheresRae;

/** Inert native Rae dialog/kill/owned-object checks, never instance creation or flight execution. */
public final class NativeRaeQuestCheck {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var old=DataManager.QUEST_DATA; var oldNpcs=DataManager.NPC_DATA;
        try{
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller()
                .unmarshal(Path.of(args[0],"data/static_data/quest_data/quest_data.xml").toFile());
            var handler=new _2002WheresRae();
            for(QuestStatus status:new QuestStatus[]{null,QuestStatus.START,QuestStatus.REWARD,QuestStatus.COMPLETE})for(int stage=0;stage<=35;stage++){
                var bot=NativeSielQuestCheck.bot(2002,status,stage);var actions=handler.getPlayerBotActions(bot);
                check(status==null?bot.quests.getQuestState(2002)==null:bot.quests.getQuestState(2002).getStatus()==status
                    && bot.quests.getQuestState(2002).getQuestVarById(0)==stage && bot.quests.getQuestState(2002).getQuestVarById(1)==5,"Discovery preserves native enrollment/progress");
                check(bot.cube.counts.isEmpty(),"No invented owned quest items");
                if(status==null||status==QuestStatus.COMPLETE)check(actions.isEmpty(),"Native campaign prerequisites alone enroll Rae");
                if(status==QuestStatus.REWARD)check(actions.getFirst().kind()==QuestBotAction.Kind.REWARD&&actions.getFirst().targetId()==203516,"Actual native reward recipient");
            }
            for(int stage:new int[]{0,1,2,10,13}){
                var bot=NativeSielQuestCheck.bot(2002,QuestStatus.START,stage);var action=handler.getPlayerBotActions(bot).getFirst();
                check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,action.targetId(),DialogAction.QUEST_SELECT),action.page()),"Actual native offered Rae page");
                NativeSielQuestCheck.offered(handler,bot,action.targetId(),action.dialogAction());
                check(bot.quests.getQuestState(2002).getQuestVarById(0)==stage+1&&bot.quests.getQuestState(2002).getQuestVarById(1)==5,"Original native dialog advances exactly its own stage");
            }
            for(int stage=3;stage<10;stage++)for(int npc:new int[]{210377,210378}){
                var bot=NativeSielQuestCheck.bot(2002,QuestStatus.START,stage);check(handler.getPlayerBotActions(bot).size()==2,"Both native Sprigg variants");
                check(!handler.onKillEvent(NativeDaevanionQuestCheck.kill(bot,2002,999999)),"Unrelated enemy cannot earn credit");
                check(handler.onKillEvent(NativeDaevanionQuestCheck.kill(bot,2002,npc))&&bot.quests.getQuestState(2002).getQuestVarById(0)==stage+1,"Actual native Sprigg kill progresses");
            }
            var bot=NativeSielQuestCheck.bot(2002,QuestStatus.START,11);
            check(handler.getPlayerBotActions(bot).getFirst().kind()==QuestBotAction.Kind.OBJECT&&handler.getPlayerBotActions(bot).getFirst().targetId()==700045,"Missing owned item selects real native mushroom");
            bot.cube.counts.put(182203003,1L);var held=handler.getPlayerBotActions(bot).getFirst();
            check(held.kind()==QuestBotAction.Kind.CONVERSATION&&held.dialogAction()==DialogAction.CHECK_USER_HAS_QUEST_ITEM
                &&held.requiredItems().equals(Map.of(182203003,1L)),"Native owned item enables original collection dialog");
            var instance=NativeSielQuestCheck.bot(2002,QuestStatus.START,12);var entry=handler.getPlayerBotActions(instance).getFirst();
            check(entry.targetId()==790002&&entry.dialogAction()==DialogAction.SETPRO5&&entry.page()==2462,"Native instance initiating dialog, no fixture transport");
            instance.quests.getQuestState(2002).setQuestVar(99);instance.world=WorldMapType.ATAXIAR.getId();var flight=handler.getPlayerBotActions(instance).getFirst();
            check(flight.kind()==QuestBotAction.Kind.FLIGHT&&flight.targetId()==205020&&flight.dialogAction()==DialogAction.QUEST_SELECT,"Exact packed native instance marker maps to its own original flight");
            instance.world=220010000;check(handler.getPlayerBotActions(instance).getFirst().targetId()==0,"Different world cannot replay Ataxiar flight");
            var reward=NativeSielQuestCheck.bot(2002,QuestStatus.REWARD,15);
            check(Objects.equals(NativeSielQuestCheck.offered(handler,reward,203516,DialogAction.SELECT_QUEST_REWARD),5),"Generic reward action offers actual native reward page");
            for(String type:new String[]{"poeta._1001TheKerubThreat","poeta._1002RequestoftheElim","poeta._1003IllegalLogging",
                "poeta._1004NeutralizingOdium","poeta._1005BarringtheGate","ishalgen._2001ThinkingAhead","ishalgen._2003TreasureOfTheDeceased",
                "ishalgen._2004ACharmedCube","ishalgen._2005TeachingaLesson","ishalgen._2006HitThemWhereitHurts","ishalgen._2007WheresRaeThisTime",
                "eltnen._1038TheShadowsCommand","heiron._18602NightmareinShiningArmor"}){
                var nativeHandler=(com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler)Class.forName("quest."+type).getConstructor().newInstance();
                var actor=NativeSielQuestCheck.bot(nativeHandler.getQuestId(),QuestStatus.REWARD,0);
                var action=nativeHandler.getPlayerBotActions(actor).getFirst();
                check(Objects.equals(NativeSielQuestCheck.offered(nativeHandler,actor,action.targetId(),DialogAction.SELECT_QUEST_REWARD),
                    com.aionemu.gameserver.model.DialogPage.getRewardPageByIndex(actor.quests.getQuestState(nativeHandler.getQuestId()).getRewardGroup()).id()),"Each declared campaign reward offers actual native index page");
                check(actor.cube.counts.isEmpty()&&actor.quests.getQuestState(nativeHandler.getQuestId()).getStatus()==QuestStatus.REWARD,"Reward opening preserves custody and pending native completion");
            }
            DataManager.NPC_DATA=new NpcData(){@Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getNpcTemplate(int id){
                return new com.aionemu.gameserver.model.templates.npc.NpcTemplate();
            }};
            handler.register();
            check(QuestEngine.getInstance().getQuestNpc(790002).getOnTalkEvent().contains(2002)
                &&QuestEngine.getInstance().getQuestNpc(205020).getOnTalkEvent().contains(2002),"Original omitted Rae progression and flight NPCs now register native talks");
            System.out.println("OK: "+checks+" actual Rae native dialog/kill/instance-intent checks; no World/SQL/IDs/transport/grants");
        }finally{DataManager.QUEST_DATA=old;DataManager.NPC_DATA=oldNpcs;}
    }
}
