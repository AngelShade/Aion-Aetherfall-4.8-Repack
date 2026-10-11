package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Exact report sequences, return dialogs and premature native reward rejection. */
public final class NativeReportCircuitCheck {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final String[] TYPES={"beluslan._2514TheRedManeReport","beluslan._2583APoliteRequest","reshanta._2767AFruitfulPartnership",
        "theobomos._3023TheSpiceMustFlow","theobomos._3035EnhancingtheStone","theobomos._3076BolsteringtheAethericField",
        "theobomos._3083SearchingfortheElderofWisdom","beluslan._2553TheBrigadeGeneralsCall","brusthonin._4020ParicasSpecialOrder",
        "pandaemonium._2914ATokenofLostLove","pandaemonium._2928PowerofLove","theobomos._3020AgrintAfire",
        "theobomos._3037WanderersStaff","theobomos._3091TheLostDrakie"};
    static final int[] SENDERS={204702,204715,279004,798138,798155,798155,798156,798119,205120,204147,204261,798143,798166,798191};
    static final int[][] REPORTS={{204700,204763},{204805,204361},{279024,279022},{203785,798222},{203830,279029},
        {278503,278556},{730024,798215},{204702},{205141},{204236},{204235},{798149},{798199},{798176}};
    static void premature(AbstractQuestHandler handler,int sender)throws Exception{
        var bot=NativeSielQuestCheck.bot(handler.getQuestId(),QuestStatus.START,0);
        NativeSielQuestCheck.offered(handler,bot,sender,DialogAction.SELECT_QUEST_REWARD);
        check(bot.quests.getQuestState(handler.getQuestId()).getStatus()==QuestStatus.START,"Native reward request must not skip required reports");
    }
    public static void main(String[] args)throws Exception{
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var old=DataManager.QUEST_DATA;
        try{
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller()
                .unmarshal(Path.of(args[0],"data/static_data/quest_data/quest_data.xml").toFile());
            if(args.length>1&&args[1].equals("--premature-only")){
                premature((AbstractQuestHandler)Class.forName("quest."+TYPES[0]).getConstructor().newInstance(),SENDERS[0]);return;
            }
            for(int i=0;i<TYPES.length;i++){
                var handler=(AbstractQuestHandler)Class.forName("quest."+TYPES[i]).getConstructor().newInstance();int quest=handler.getQuestId();
                premature(handler,SENDERS[i]);var bot=NativeSielQuestCheck.bot(quest,null,0);var acceptance=handler.getPlayerBotActions(bot).getFirst();
                check(acceptance.targetId()==SENDERS[i]&&Objects.equals(NativeSielQuestCheck.offered(handler,bot,SENDERS[i],DialogAction.QUEST_SELECT),1011),"Actual native sender/acceptance page");
                check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,SENDERS[i],acceptance.dialogAction()),4),"Real native acceptance window");
                bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,0);
                for(int stage=0;stage<REPORTS[i].length;stage++){
                    var action=handler.getPlayerBotActions(bot).getFirst();
                    check(action.targetId()==REPORTS[i][stage]&&action.kind()==QuestBotAction.Kind.CONVERSATION,"Actual next report NPC");
                    check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,action.targetId(),DialogAction.QUEST_SELECT),action.page()),"Actual current native offered report page");
                    NativeSielQuestCheck.offered(handler,bot,SENDERS[i],DialogAction.SELECT_QUEST_REWARD);
                    check(bot.quests.getQuestState(quest).getStatus()==QuestStatus.START,"Incomplete report circuit cannot admit reward");
                    NativeSielQuestCheck.offered(handler,bot,action.targetId(),action.dialogAction());
                    check(bot.quests.getQuestState(quest).getQuestVarById(0)==stage+1&&bot.quests.getQuestState(quest).getQuestVarById(1)==5,
                        "Original native report advances once preserving other progress: quest="+quest+" stage="+stage+" vars="+bot.quests.getQuestState(quest).getQuestVars().getQuestVars());
                }
                var returned=handler.getPlayerBotActions(bot).getFirst();
                check(returned.targetId()==SENDERS[i]&&returned.dialogAction()==DialogAction.SELECT_QUEST_REWARD,"Completed circuit returns through its native initiating action");
                check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,SENDERS[i],DialogAction.QUEST_SELECT),returned.page()),"Fresh actual offered return page");
                NativeSielQuestCheck.offered(handler,bot,SENDERS[i],returned.dialogAction());
                check(bot.quests.getQuestState(quest).getStatus()==QuestStatus.REWARD&&bot.cube.counts.isEmpty(),"Original completed native circuit opens reward without grants");
                check(handler.getPlayerBotActions(bot).getFirst().kind()==QuestBotAction.Kind.REWARD
                    &&Objects.equals(NativeSielQuestCheck.offered(handler,bot,SENDERS[i],DialogAction.SELECT_QUEST_REWARD),5),"Actual native standard reward page");
                var invalid=NativeSielQuestCheck.bot(quest,QuestStatus.START,REPORTS[i].length+1);
                NativeSielQuestCheck.offered(handler,invalid,SENDERS[i],DialogAction.SELECT_QUEST_REWARD);
                check(handler.getPlayerBotActions(invalid).getFirst().kind()==QuestBotAction.Kind.EVENT
                    &&invalid.quests.getQuestState(quest).getStatus()==QuestStatus.START,"Unexpected saved report stage cannot fabricate reward");
            }
            System.out.println("OK: "+checks+" actual native report-circuit and reward-guard checks; no World/SQL/IDs/grants");
        }finally{DataManager.QUEST_DATA=old;}
    }
}
