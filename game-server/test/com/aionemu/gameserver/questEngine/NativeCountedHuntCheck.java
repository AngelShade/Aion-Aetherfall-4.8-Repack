package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Real counted hunt callbacks, including the two previously stalled Asmodian finales. */
public final class NativeCountedHuntCheck {
    static int checks;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    static final String[] TYPES = {"beluslan._80226ATestinDarkPoeta", "beluslan._80227ASecondTestinDarkPoeta",
        "beluslan._80228FinalTestinDarkPoeta", "beluslan._80229FinalTestinDarkPoeta",
        "heiron._80217ToDarkPoeta", "heiron._80218DarkPoetaEncore", "heiron._80219DarkPoetaFinale",
        "heiron._80220DarkPoetaFinale", "inggison._11110KillingTime", "udas_temple._30005HealMeKillMe", "verteron._1169LightningfootTuka"};
    static final int[] LAST = {0,2,5,5,0,2,5,5,9,24,0};
    static final int[] NPC = {831027,831027,831027,831027,831024,831024,831024,831024,799075,799029,203126};
    static final int[][] ENEMIES = {{214904},{214904},{214904},{214904},{214904},{214904},{214904},{214904},
        {217039,217040},{215857,215814,215858,215815},{210317}};
    static void finaleRegression() throws Exception {
        for (int index : new int[] {2,3}) {
            var handler = (AbstractQuestHandler) Class.forName("quest." + TYPES[index]).getConstructor().newInstance();
            var bot = NativeSielQuestCheck.bot(handler.getQuestId(), QuestStatus.START, 2);
            check(handler.onKillEvent(NativeDaevanionQuestCheck.kill(bot, handler.getQuestId(), 214904)), "Asmodian finale must continue at its third required kill");
            check(bot.quests.getQuestState(handler.getQuestId()).getQuestVarById(0) == 3, "Actual native finale counter advances through the former deadlock");
        }
    }
    public static void main(String[] args) throws Exception {
        var field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var old=DataManager.QUEST_DATA;
        try {
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller()
                .unmarshal(Path.of(args[0],"data/static_data/quest_data/quest_data.xml").toFile());
            finaleRegression(); if(args.length>1 && args[1].equals("--finale-only")) return;
            for(int i=0;i<TYPES.length;i++) {
                var handler=(AbstractQuestHandler)Class.forName("quest."+TYPES[i]).getConstructor().newInstance();int quest=handler.getQuestId();
                var bot=NativeSielQuestCheck.bot(quest,null,0);var acceptance=handler.getPlayerBotActions(bot).getFirst();
                check(acceptance.targetId()==NPC[i] && Objects.equals(NativeSielQuestCheck.offered(handler,bot,NPC[i],DialogAction.QUEST_SELECT),1011),"Native sender/page matches declaration");
                check(Objects.equals(NativeSielQuestCheck.offered(handler,bot,NPC[i],acceptance.dialogAction()),4),"Actual native acceptance window");
                for(int enemy:ENEMIES[i]) {
                    var actor=NativeSielQuestCheck.bot(quest,QuestStatus.START,0);var state=actor.quests.getQuestState(quest);
                    for(int count=0;count<=LAST[i];count++) {
                        var actions=handler.getPlayerBotActions(actor);
                        check(actions.stream().mapToInt(QuestBotAction::targetId).boxed().collect(java.util.stream.Collectors.toSet())
                            .equals(Arrays.stream(ENEMIES[i]).boxed().collect(java.util.stream.Collectors.toSet())),"Exact native current enemy variants");
                        check(!handler.onKillEvent(NativeDaevanionQuestCheck.kill(actor,quest,999999)),"Unrelated kill is rejected");
                        check(handler.onKillEvent(NativeDaevanionQuestCheck.kill(actor,quest,enemy)),"Every actual required kill advances its native counter");
                        check(count==LAST[i]?state.getStatus()==QuestStatus.REWARD:state.getStatus()==QuestStatus.START && state.getQuestVarById(0)==count+1,"Reward only after final actual kill");
                        check(state.getQuestVarById(1)==5 && actor.cube.counts.isEmpty(),"Native credit preserves unrelated progress and ownership");
                    }
                    check(handler.getPlayerBotActions(actor).getFirst().kind()==QuestBotAction.Kind.REWARD,"Native reward is next action");
                    check(Objects.equals(NativeSielQuestCheck.offered(handler,actor,NPC[i],DialogAction.SELECT_QUEST_REWARD),5),"Generic reward request offers actual standard reward page");
                    check(!handler.onKillEvent(NativeDaevanionQuestCheck.kill(actor,quest,enemy)),"Reward cannot repeat kill credit");
                }
                var invalid=NativeSielQuestCheck.bot(quest,QuestStatus.START,LAST[i]+1);
                check(handler.getPlayerBotActions(invalid).getFirst().kind()==QuestBotAction.Kind.EVENT
                    && !handler.onKillEvent(NativeDaevanionQuestCheck.kill(invalid,quest,ENEMIES[i][0])),"Invalid saved counter cannot invent credit");
            }
            System.out.println("OK: "+checks+" actual counted-hunt native transition checks; no World/SQL/IDs/grants");
        }finally{DataManager.QUEST_DATA=old;}
    }
}
