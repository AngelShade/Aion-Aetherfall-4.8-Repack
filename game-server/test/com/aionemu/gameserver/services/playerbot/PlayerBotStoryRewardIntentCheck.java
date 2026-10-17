package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;

/** Real planner/native registry/shared action consumer; cold transient intent snapshots, no AI/World/SQL. */
public final class PlayerBotStoryRewardIntentCheck {
    static int checks; static Object unsafe;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static <T>T allocate(Class<T> type)throws Exception{return type.cast(unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type));}
    static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static final class Actor extends Player {
        QuestStateList quests; PlayerClass profession; boolean world;
        Actor(){super(null,null);}
        @Override public QuestStateList getQuestStateList(){return quests;}
        @Override public PlayerClass getPlayerClass(){return profession;}
        @Override public int getObjectId(){return 1999982241;}
        @Override public boolean isPlayerBot(){return true;}
        @Override public boolean isWorldBot(){return world;}
        @Override public boolean isOnline(){return false;}
    }
    static Actor actor()throws Exception{
        var actor=allocate(Actor.class);actor.quests=new QuestStateList();actor.profession=PlayerClass.GLADIATOR;return actor;
    }
    static final class NativeGraph implements PlayerBotQuestPrerequisites.View {
        final Actor actor;NativeGraph(Actor actor){this.actor=actor;}
        public QuestTemplate template(int quest){return DataManager.QUEST_DATA.getQuestById(quest);}
        public QuestState state(int quest){return actor.quests.getQuestState(quest);}
        public List<PlayerBotQuestPrerequisites.Blocker> eligibility(int quest){return List.of();}
        public List<PlayerBotQuestPrerequisites.Blocker> capability(int quest){return List.of();}
        public QuestBotRewardChoices rewardChoices(int quest){return QuestEngine.getInstance().getPlayerBotRewardChoices(actor,quest);}
        public int title(){return 0;}
    }
    @SuppressWarnings("unchecked")
    static void sharedConsumer()throws Exception{
        var bot=actor();var state=new QuestState(1114,QuestStatus.START);state.setQuestVarById(0,2);bot.quests.addQuest(1114,state);
        var graph=new NativeGraph(bot);var zero=PlayerBotQuestPrerequisites.plan(graph,1917);var one=PlayerBotQuestPrerequisites.plan(graph,1918);
        check(zero.reachable()&&zero.requiredRewards().equals(Map.of(1114,0)),"Actual1917 XML and registered native declaration produce required story group0");
        check(one.reachable()&&one.requiredRewards().equals(Map.of(1114,1)),"Actual1918 XML and registered native declaration produce required story group1");
        // Only read the consumer of already selected/approved transient intent; no native acceptance or persistence is executed.
        var worldStates=(Map<Integer,Object>)field(PlayerBotWorldActivities.class,"STATES").get(null);
        var worldType=Class.forName(PlayerBotWorldActivities.class.getName()+"$State");var worldState=allocate(worldType);
        field(worldType,"prerequisiteRoot").setInt(worldState,1917);field(worldType,"requiredRewards").set(worldState,zero.requiredRewards());
        worldStates.put(bot.getObjectId(),worldState);bot.world=true;
        check(PlayerBotQuestSync.requiredReward(bot,1114)==0,"Independent world intent exposes actual selected root's required reward");
        var action=PlayerBotQuestActions.actions(bot,1114).getFirst();
        check(action.dialogAction()==DialogAction.SETPRO2&&action.page()==1693&&action.targetId()==203075,
            "Actual shared action consumer forwards world group0 to registered native Namus refusal route");
        field(worldType,"prerequisiteRoot").setInt(worldState,1918);field(worldType,"requiredRewards").set(worldState,one.requiredRewards());
        action=PlayerBotQuestActions.actions(bot,1114).getFirst();
        check(action.dialogAction()==DialogAction.SELECT_QUEST_REWARD&&action.targetId()==203075,
            "Actual shared action consumer forwards world group1 to registered native Namus turn-in");
        var syncStates=(Map<Integer,PlayerBotQuestSync.State>)field(PlayerBotQuestSync.class,"STATES").get(null);
        var session=allocate(PlayerBotSession.class);field(PlayerBotSession.class,"bot").set(session,bot);
        field(PlayerBotSession.class,"owner").set(session,actor());bot.world=false;
        var approved=allocate(PlayerBotQuestSync.State.class);field(PlayerBotQuestSync.State.class,"session").set(approved,session);
        var choices=new HashMap<Integer,Integer>(zero.requiredRewards());field(PlayerBotQuestSync.State.class,"requiredRewards").set(approved,choices);
        syncStates.put(bot.getObjectId(),approved);
        action=PlayerBotQuestActions.actions(bot,1114).getFirst();
        check(action.dialogAction()==DialogAction.SETPRO2&&action.targetId()==203075,
            "Approved companion transient group0 uses the same registered native story route");
        choices.put(1114,1);action=PlayerBotQuestActions.actions(bot,1114).getFirst();
        check(action.dialogAction()==DialogAction.SELECT_QUEST_REWARD&&action.page()==1693,
            "Approved companion transient group1 uses the same native story turn-in");
        choices.put(1114,-2);
        check(PlayerBotQuestActions.actions(bot,1114).isEmpty(),"Conflicting approved story intents cannot silently choose a branch");
        check(PlayerBotQuestActions.custody(bot,1114).stream().allMatch(a->a.requiredItems().equals(Map.of(182200217,1L))),
            "Conflicting automation intent retains original owned-gown custody requirements");
        choices.put(1114,0);PlayerBotConfig.QUESTS_ENABLED=false;
        check(PlayerBotQuestActions.actions(bot,1114).isEmpty()&&!PlayerBotQuestActions.custody(bot,1114).isEmpty(),
            "Feature-off prevents new story action admission while preserving native item custody");
        PlayerBotConfig.QUESTS_ENABLED=true;
        state.setQuestVarById(0,4);state.setRewardGroup(1);state.setStatus(QuestStatus.REWARD);
        check(PlayerBotQuestActions.actions(bot,1114).getFirst().kind()==QuestBotAction.Kind.EVENT&&state.getRewardGroup()==1,
            "Shared consumer cannot rewrite opposite already committed REWARD group");
        choices.put(1114,1);action=PlayerBotQuestActions.actions(bot,1114).getFirst();
        check(action.kind()==QuestBotAction.Kind.REWARD&&action.targetId()==203075,
            "Matching shared intent retains earned native reward recipient");
        syncStates.remove(bot.getObjectId());bot.world=true;
        field(worldType,"prerequisiteRoot").setInt(worldState,1917);field(worldType,"requiredRewards").set(worldState,zero.requiredRewards());
        bot.quests.addQuest(1917,new QuestState(1917,QuestStatus.START));
        check(PlayerBotQuestSync.requiredReward(bot,1114)==-1&&(int)field(worldType,"prerequisiteRoot").get(worldState)==0,
            "World prerequisite intent clears after the actual requested root is acquired");
        check(((Map<?,?>)field(worldType,"requiredRewards").get(worldState)).isEmpty(),"World root cleanup leaves no stale reward preference");
        worldStates.remove(bot.getObjectId());
        check(state.getRewardGroup()==1&&state.getStatus()==QuestStatus.REWARD&&state.getCompleteCount()==0,
            "Shared discovery never claims actual final reward or changes saved native completion");
        var ceremony=new QuestState(1007,QuestStatus.START);ceremony.setQuestVar(2);bot.quests.addQuest(1007,ceremony);bot.profession=PlayerClass.RANGER;
        // Native declaration enters the real finite planner rather than a guessed XML group count.
        var permitted=PlayerBotQuestPrerequisites.plan(graph,1914);
        check(permitted.reachable()&&permitted.requiredRewards().get(1007)==1,"Actual Scout ceremony group admits matching native dispatch prerequisite");
        var forbidden=PlayerBotQuestPrerequisites.plan(graph,1913);
        check(!forbidden.reachable()&&forbidden.blockers().stream().anyMatch(b->b.code().equals("NATIVE_REWARD_ROUTE")),
            "Actual Ranger declaration blocks Warrior story group in real planner");
        choices.clear();choices.put(1007,1);syncStates.put(bot.getObjectId(),approved);bot.world=false;
        action=PlayerBotQuestActions.actions(bot,1007).getFirst();
        check(action.targetId()==203752&&action.dialogAction()==DialogAction.SETPRO3,
            "Shared approved ceremony intent reaches actual class-owned native report");
        choices.put(1007,0);
        check(PlayerBotQuestActions.actions(bot,1007).getFirst().kind()==QuestBotAction.Kind.EVENT&&ceremony.getRewardGroup()==null,
            "Wrong class intent cannot masquerade as a selectable reward-item index");
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception{
        var unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");unsafeField.setAccessible(true);unsafe=unsafeField.get(null);
        var engine=QuestEngine.getInstance();var registry=(Map<Integer,AbstractQuestHandler>)field(QuestEngine.class,"questHandlers").get(engine);
        var original=new HashMap<>(registry);var oldQuest=DataManager.QUEST_DATA;var oldXml=DataManager.XML_QUESTS;
        boolean oldEnabled=PlayerBotConfig.QUESTS_ENABLED,oldCustom=PlayerBotConfig.QUEST_CUSTOM;
        var world=(Map<Integer,Object>)field(PlayerBotWorldActivities.class,"STATES").get(null);
        var sync=(Map<Integer,Object>)field(PlayerBotQuestSync.class,"STATES").get(null);
        Object oldWorld=world.get(1999982241),oldSync=sync.get(1999982241);
        try{
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(Path.of(args[0]).resolve("data/static_data/quest_data/quest_data.xml").toFile());
            DataManager.XML_QUESTS=null;PlayerBotConfig.QUESTS_ENABLED=true;PlayerBotConfig.QUEST_CUSTOM=true;
            for(String name:List.of("quest.poeta._1114TheNymphsGown","quest.ascension._1007ACeremonyinSanctum")){
                var handler=(AbstractQuestHandler)Class.forName(name).getConstructor().newInstance();registry.put(handler.getQuestId(),handler);
            }
            sharedConsumer();System.out.println("OK: "+checks+" real planner/registered story/shared world and approved companion intent checks; no World/SQL/IDs/actions/rewards");
        }finally{
            registry.clear();registry.putAll(original);DataManager.QUEST_DATA=oldQuest;DataManager.XML_QUESTS=oldXml;
            PlayerBotConfig.QUESTS_ENABLED=oldEnabled;PlayerBotConfig.QUEST_CUSTOM=oldCustom;
            if(oldWorld==null)world.remove(1999982241);else world.put(1999982241,oldWorld);
            if(oldSync==null)sync.remove(1999982241);else sync.put(1999982241,oldSync);
        }
    }
}
