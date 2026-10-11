package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Actual story dialogs, saved native groups and native XML follow-up eligibility; inert owned stacks only. */
public final class NativeStoryRewardChoicesCheck {
    static int checks;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    static final class Actor extends Player {
        QuestStateList quests; NativeSielQuestCheck.Cube cube;
        NativeSielQuestCheck.Controller controller; PlayerClass profession;
        Actor() { super(null, null); }
        @Override public QuestStateList getQuestStateList() { return quests; }
        @Override public PlayerStorage getInventory() { return cube; }
        @Override public NativeSielQuestCheck.Controller getController() { return controller; }
        @Override public boolean isOnline() { return false; }
        @Override public boolean isPlayerBot() { return true; }
        @Override public int getObjectId() { return 1999982231; }
        @Override public PlayerClass getPlayerClass() { return profession; }
        @Override public Race getRace() { return Race.ELYOS; }
        @Override public byte getLevel() { return 20; }
    }
    static final class Contact extends com.aionemu.gameserver.model.gameobjects.Npc {
        int id; com.aionemu.gameserver.ai.NpcAI callback;
        Contact() { super(null,null,null); }
        @Override public int getObjectId() { return 1999982232; }
        @Override public int getNpcId() { return id; }
        @Override public com.aionemu.gameserver.ai.NpcAI getAi() { return callback; }
        @Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getObjectTemplate() {
            return new com.aionemu.gameserver.model.templates.npc.NpcTemplate() { @Override public int getTemplateId() { return id; } };
        }
    }
    static Actor actor(int quest, QuestStatus status, int stage) throws Exception {
        var actor=NativeSielQuestCheck.allocate(Actor.class);actor.quests=new QuestStateList();
        actor.cube=new NativeSielQuestCheck.Cube(actor);actor.controller=NativeSielQuestCheck.allocate(NativeSielQuestCheck.Controller.class);
        actor.profession=PlayerClass.WARRIOR;
        if(status!=null){var state=new QuestState(quest,status);state.setQuestVar(stage);actor.quests.addQuest(quest,state);}
        return actor;
    }
    static Integer offered(AbstractQuestHandler handler, Actor actor, int npc, int action) throws Exception {
        var contact=NativeSielQuestCheck.allocate(Contact.class);contact.id=npc;
        // Original defaultCloseDialog emits DIALOG_FINISH; native base callback remains inert and world-free.
        contact.callback=new com.aionemu.gameserver.ai.NpcAI(contact){};
        contact.callback.setStateIfNot(com.aionemu.gameserver.ai.AIState.IDLE);
        var capture=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotQuestDialog");
        var open=capture.getDeclaredMethod("open",int.class,int.class,int.class);open.setAccessible(true);
        var page=capture.getDeclaredField("page");page.setAccessible(true);
        try(var window=(AutoCloseable)open.invoke(null,actor.getObjectId(),contact.getObjectId(),handler.getQuestId())){
            handler.onDialogEvent(new QuestEnv(contact,actor,handler.getQuestId(),action));return (Integer)page.get(window);
        }
    }
    static AbstractQuestHandler handler(String name) throws Exception {
        return (AbstractQuestHandler)Class.forName(name).getConstructor().newInstance();
    }
    static void nymph() throws Exception {
        var nativeHandler=handler("quest.poeta._1114TheNymphsGown");
        var empty=actor(1114,null,0);
        check(nativeHandler.getPlayerBotRewardChoices(empty).declared()
            &&nativeHandler.getPlayerBotRewardChoices(empty).groups().equals(Set.of(0,1)),"Both reviewed native story branches exist before acceptance");
        check(nativeHandler.getPlayerBotActions(empty,0).getFirst().kind()==QuestBotAction.Kind.ITEM
            &&nativeHandler.getPlayerBotActions(empty,0).getFirst().targetId()==182200214,"Enrollment uses owned native diary rather than fabricated quest start");
        check(empty.quests.getQuestState(1114)==null&&empty.cube.counts.isEmpty(),"Branch discovery grants/accepts nothing");
        for(int group:new int[]{0,1})for(int stage:new int[]{2,3}){
            var bot=actor(1114,QuestStatus.START,stage);var state=bot.quests.getQuestState(1114);bot.cube.counts.put(182200217,2L);
            var before=Map.copyOf(bot.cube.counts);var action=nativeHandler.getPlayerBotActions(bot,group).getFirst();
            check(action.requiredItems().equals(Map.of(182200217,1L)),"Story hand-in keeps actual owned gown custody");
            check(state.getRewardGroup()==null&&state.getStatus()==QuestStatus.START&&state.getQuestVarById(0)==stage
                &&bot.cube.counts.equals(before),"Selecting a planned branch is read-only");
            check(Objects.equals(offered(nativeHandler,bot,action.targetId(),DialogAction.QUEST_SELECT),action.page()),"First branch action uses an actually offered native page");
            check(state.getRewardGroup()==null&&bot.cube.counts.equals(before),"Opening the story page does not commit group or consume gown");
            offered(nativeHandler,bot,action.targetId(),action.dialogAction());
            if(group==0&&stage==2){
                check(state.getStatus()==QuestStatus.START&&state.getQuestVarById(0)==3&&state.getRewardGroup()==null
                    &&bot.cube.counts.equals(before),"Native Namus refusal advances to Asteros without reward/group/item loss");
                action=nativeHandler.getPlayerBotActions(bot,group).getFirst();
                check(action.targetId()==203058&&action.dialogAction()==DialogAction.SETPRO3&&action.page()==2034,"Group0 follows the actual Asteros story reply");
                check(Objects.equals(offered(nativeHandler,bot,action.targetId(),DialogAction.QUEST_SELECT),action.page()),"Asteros actually offers its declared story page");
                offered(nativeHandler,bot,action.targetId(),action.dialogAction());
            }
            check(state.getStatus()==QuestStatus.REWARD&&Objects.equals(state.getRewardGroup(),group)
                &&state.getQuestVarById(0)==(group==0?3:4),"Original native dialogue commits precisely the requested story group/stage");
            check(bot.cube.getItemCountByItemId(182200217)==1&&bot.cube.removals==1,"Original native hand-in consumes one owned gown and preserves surplus");
            check(nativeHandler.getPlayerBotRewardChoices(bot).groups().equals(Set.of(group)),"Committed REWARD group is the only reachable saved branch");
            var reward=nativeHandler.getPlayerBotActions(bot,group).getFirst();
            check(reward.kind()==QuestBotAction.Kind.REWARD&&reward.targetId()==(group==0?203058:203075),"Earned reward uses actual committed story recipient");
            check(nativeHandler.getPlayerBotActions(bot,1-group).getFirst().kind()==QuestBotAction.Kind.EVENT
                &&Objects.equals(state.getRewardGroup(),group),"Opposite branch cannot rewrite committed reward state");
            check(Objects.equals(offered(nativeHandler,bot,reward.targetId(),DialogAction.SELECT_QUEST_REWARD),group==0?5:6),"Actual native reward page agrees with saved story group");
            check(bot.cube.getItemCountByItemId(182200217)==1&&bot.cube.removals==1,"Reward reopening preserves gown surplus");
            state.setStatus(QuestStatus.COMPLETE);
            check(nativeHandler.getPlayerBotActions(bot,group).isEmpty()&&nativeHandler.getPlayerBotRewardChoices(bot).groups().equals(Set.of(group)),"Completed saved branch cannot replay or change choice");
        }
        for(int stage:new int[]{0,1}){
            var bot=actor(1114,QuestStatus.START,stage);var action=nativeHandler.getPlayerBotActions(bot,0).getFirst();
            check(action.targetId()==(stage==0?203075:700008),"Early story retains native speaker/object");
            if(stage==0)check(Objects.equals(offered(nativeHandler,bot,203075,DialogAction.QUEST_SELECT),action.page()),"Original Namus introduction actually offers its stage page");
            else check(action.kind()==QuestBotAction.Kind.OBJECT,"Gown acquisition requires actual native world object; no offline item grant");
        }
        var invalid=actor(1114,QuestStatus.START,7);
        check(nativeHandler.getPlayerBotRewardChoices(invalid).groups().isEmpty()
            &&nativeHandler.getPlayerBotActions(invalid,0).getFirst().kind()==QuestBotAction.Kind.EVENT,"Inconsistent saved stage has a specific unmet route instead of fabricated progress");
    }
    static void ceremony() throws Exception {
        var nativeHandler=handler("quest.ascension._1007ACeremonyinSanctum");
        for(var profession:PlayerClass.values()){
            int group=switch(profession.getStartingClass()){
                case WARRIOR->0;case SCOUT->1;case MAGE->2;case PRIEST->3;case ENGINEER->4;case ARTIST->5;default->-1;
            };
            var bot=actor(1007,QuestStatus.START,2);bot.profession=profession;var state=bot.quests.getQuestState(1007);
            check(group>=0&&nativeHandler.getPlayerBotRewardChoices(bot).groups().equals(Set.of(group)),"Actual base/advanced class predicts only its native ceremony group");
            for(int other=0;other<6;other++)if(other!=group)
                check(nativeHandler.getPlayerBotActions(bot,other).getFirst().kind()==QuestBotAction.Kind.EVENT
                    &&state.getRewardGroup()==null,"Impossible class story reward cannot become a fabricated route");
            var action=nativeHandler.getPlayerBotActions(bot,group).getFirst();
            check(action.targetId()==203752&&action.dialogAction()==DialogAction.SETPRO3&&action.page()==1693,"Native class ceremony report route");
            check(Objects.equals(offered(nativeHandler,bot,203752,DialogAction.QUEST_SELECT),1693)
                &&state.getRewardGroup()==null&&state.getQuestVarById(0)==2,"Opening real ceremony page cannot assign class group");
            offered(nativeHandler,bot,203752,action.dialogAction());
            check(state.getStatus()==QuestStatus.REWARD&&Objects.equals(state.getRewardGroup(),group)
                &&state.getQuestVars().getQuestVars()==(group+1)*10,"Original native starting-class switch alone commits group and raw packed stage");
            check(bot.cube.counts.isEmpty(),"Native ceremony report does not generate reward items offline");
            var reward=nativeHandler.getPlayerBotActions(bot,group).getFirst();
            check(reward.kind()==QuestBotAction.Kind.REWARD
                &&reward.targetId()==new int[]{203758,203759,203760,203761,801212,801213}[group],"Native committed ceremony group selects original trainer recipient");
            check(Objects.equals(offered(nativeHandler,bot,reward.targetId(),DialogAction.USE_OBJECT),2034+341*group),"Actual class trainer offers the exact native reward dialog");
            check(nativeHandler.getPlayerBotRewardChoices(bot).groups().equals(Set.of(group)),"REWARD declaration reads committed native group");
            state.setStatus(QuestStatus.COMPLETE);
            check(nativeHandler.getPlayerBotActions(bot,group).isEmpty()&&nativeHandler.getPlayerBotRewardChoices(bot).groups().equals(Set.of(group)),"Completed ceremony cannot rerun report or reward selection");
        }
        var absent=actor(1007,null,0);
        check(nativeHandler.getPlayerBotActions(absent).getFirst().kind()==QuestBotAction.Kind.EVENT&&absent.quests.getQuestState(1007)==null,"Native campaign enrollment remains event-owned, without automatic acceptance");
        var first=actor(1007,QuestStatus.START,0);
        var teleport=nativeHandler.getPlayerBotActions(first).getFirst();
        check(teleport.targetId()==790001&&teleport.dialogAction()==DialogAction.SETPRO1
            &&Objects.equals(offered(nativeHandler,first,790001,DialogAction.QUEST_SELECT),1011),"Native Pernos travel initiation offered page; no offline teleport is executed");
        var second=actor(1007,QuestStatus.START,1);var report=nativeHandler.getPlayerBotActions(second).getFirst();
        check(report.targetId()==203725&&Objects.equals(offered(nativeHandler,second,203725,DialogAction.QUEST_SELECT),1352),"Native Leah stage page");
        offered(nativeHandler,second,203725,report.dialogAction());
        check(second.quests.getQuestState(1007).getQuestVarById(0)==2,"Original Leah reply advances the real saved stage");
        var wrong=actor(1007,QuestStatus.REWARD,20);wrong.quests.getQuestState(1007).setRewardGroup(0);
        check(nativeHandler.getPlayerBotActions(wrong,0).getFirst().kind()==QuestBotAction.Kind.EVENT
            &&wrong.quests.getQuestState(1007).getQuestVars().getQuestVars()==20,"Inconsistent saved raw stage/group cannot be silently repaired or handed to wrong trainer");
    }
    static void actualEligibility() throws Exception {
        for(int group:new int[]{0,1}){
            var bot=actor(1114,QuestStatus.COMPLETE,group==0?3:4);var state=bot.quests.getQuestState(1114);state.setRewardGroup(group);
            for(int quest:new int[]{1917,1918}){
                var template=DataManager.QUEST_DATA.getQuestById(quest);var conditions=template.getXMLStartConditions();
                check(conditions.size()==1&&conditions.getFirst().getFinishedPreconditions().getFirst().getQuestId()==1114
                    &&conditions.getFirst().getFinishedPreconditions().getFirst().getReward()==quest-1917,"Actual follow-up XML requires precise native story reward group");
                check(conditions.getFirst().check(bot,false)==(quest-1917==group),"Actual native condition accepts only the correctly completed story branch");
                state.setStatus(QuestStatus.REWARD);
                check(!conditions.getFirst().check(bot,false),"Unclaimed story reward cannot bypass follow-up eligibility");
                state.setStatus(QuestStatus.COMPLETE);
                var nativeHandler=handler(quest==1917?"quest.sanctum._1917ALingeringMystery":"quest.sanctum._1918AnAxForNamus");
                var unstarted=actor(quest,null,0);
                check(Objects.equals(offered(nativeHandler,unstarted,203835,DialogAction.QUEST_SELECT),1011),"Actual follow-up handler retains its original offered enrollment page");
            }
        }
        var original=new AbstractQuestHandler(1917){public void register(){}};
        check(!original.getPlayerBotRewardChoices(actor(1917,null,0)).declared(),"Unreviewed native handlers remain unknown rather than falsely declaring impossible groups");
        var values=new HashSet<>(Set.of(0,1));var immutable=QuestBotRewardChoices.nativeGroups(values,"reviewed");values.clear();
        check(immutable.groups().equals(Set.of(0,1)),"Native choice snapshots cannot alias mutable discovery inputs");
    }
    static void engineDelegation() throws Exception {
        // An isolated registry exercises real lookup/delegation without engine initialization or native registration side effects.
        var engine=NativeSielQuestCheck.allocate(QuestEngine.class);
        var registry=QuestEngine.class.getDeclaredField("questHandlers");registry.setAccessible(true);
        var nymph=handler("quest.poeta._1114TheNymphsGown");
        var ceremony=handler("quest.ascension._1007ACeremonyinSanctum");
        registry.set(engine,new HashMap<Integer,AbstractQuestHandler>(Map.of(1114,nymph,1007,ceremony)));
        var bot=actor(1114,QuestStatus.START,2);bot.cube.counts.put(182200217,1L);
        check(engine.getPlayerBotActions(bot,1114,0).equals(nymph.getPlayerBotActions(bot,0)),"Actual registered-engine overload forwards requested story group0");
        check(engine.getPlayerBotActions(bot,1114,1).equals(nymph.getPlayerBotActions(bot,1)),"Actual registered-engine overload forwards requested story group1");
        check(engine.getPlayerBotActions(bot,1114).equals(nymph.getPlayerBotActions(bot)),"Original registered-engine API retains ordinary reviewed story alternatives");
        check(engine.getPlayerBotRewardChoices(bot,1114).equals(nymph.getPlayerBotRewardChoices(bot)),"Registered-engine choice lookup uses the actual handler declaration");
        check(engine.getPlayerBotActions(bot,199999,0).isEmpty()&&!engine.getPlayerBotRewardChoices(bot,199999).declared(),"Absent handler cannot become synthetic story route authorization");
        bot.profession=PlayerClass.RANGER;bot.quests.addQuest(1007,new QuestState(1007,QuestStatus.START));
        check(engine.getPlayerBotRewardChoices(bot,1007).groups().equals(Set.of(1)),"Registered ceremony lookup predicts the actual advanced-class group");
        check(engine.getPlayerBotActions(bot,1007,0).getFirst().kind()==QuestBotAction.Kind.EVENT
            &&bot.quests.getQuestState(1007).getRewardGroup()==null,"Registered wrong-class intent cannot overwrite or bypass native class branch");
        check(bot.quests.getQuestState(1114).getQuestVarById(0)==2&&bot.cube.getItemCountByItemId(182200217)==1,"Engine discovery preserves original saved story and owned items");
    }
    public static void main(String[] args) throws Exception {
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var oldQuest=DataManager.QUEST_DATA;
        try{
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(Path.of(args[0]).resolve("data/static_data/quest_data/quest_data.xml").toFile());
            nymph();ceremony();actualEligibility();engineDelegation();
            System.out.println("OK: "+checks+" actual native story route/class-group/owned-custody/follow-up checks; no World/SQL/IDs/teleport/grants/rewards");
        }finally{DataManager.QUEST_DATA=oldQuest;}
    }
}
