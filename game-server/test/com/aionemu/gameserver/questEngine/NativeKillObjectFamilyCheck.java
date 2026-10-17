package com.aionemu.gameserver.questEngine;

import java.nio.file.Path;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.controllers.NpcController;
import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;

/** Real handler stages/callbacks, with recorded object-death endpoint; no World/IDs/SQL/spawns/grants. */
public final class NativeKillObjectFamilyCheck {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static final class ObjectController extends NpcController {
        int deaths;
        @Override public boolean die(Creature killer){deaths++;return true;}
    }
    static final class ObjectNpc extends Npc {
        int id;ObjectController controller;
        ObjectNpc(){super(null,null,null);}
        @Override public int getNpcId(){return id;}
        @Override public int getObjectId(){return 1999982262;}
        @Override public ObjectController getController(){return controller;}
        @Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getObjectTemplate(){
            return new com.aionemu.gameserver.model.templates.npc.NpcTemplate(){@Override public int getTemplateId(){return id;}};
        }
    }
    static ObjectNpc object(int id)throws Exception{
        var npc=NativeSielQuestCheck.allocate(ObjectNpc.class);npc.id=id;npc.controller=NativeSielQuestCheck.allocate(ObjectController.class);return npc;
    }
    static final class Group extends com.aionemu.gameserver.model.team.group.PlayerGroup {
        List<com.aionemu.gameserver.model.gameobjects.player.Player> actors;
        Group(){super(null,null,1);}
        @Override public List<com.aionemu.gameserver.model.gameobjects.player.Player> getMembers(){return actors;}
    }
    static final class MentorActor extends com.aionemu.gameserver.model.gameobjects.player.Player {
        QuestStateList quests;Group group;float x;int map,instance;
        MentorActor(){super(null,null);}
        @Override public QuestStateList getQuestStateList(){return quests;}
        @Override public boolean isInGroup(){return group!=null;}
        @Override public Group getPlayerGroup(){return group;}
        @Override public int getWorldId(){return map;}
        @Override public int getInstanceId(){return instance;}
        @Override public float getX(){return x;}
        @Override public float getY(){return 0;}
        @Override public float getZ(){return 0;}
    }
    static void mentorAdmission()throws Exception{
        var bot=NativeSielQuestCheck.allocate(MentorActor.class);var member=NativeSielQuestCheck.allocate(MentorActor.class);
        bot.quests=new QuestStateList();bot.quests.addQuest(47106,new QuestState(47106,QuestStatus.START));
        bot.map=member.map=220010000;bot.instance=member.instance=1;
        bot.group=NativeSielQuestCheck.allocate(Group.class);bot.group.actors=List.of(bot,member);
        check(!QuestBotKillObjectFamilies.hasNearbyNativeMentor(bot),"Ordinary nearby party member cannot satisfy mentor authority");
        member.setMentor(true);
        check(QuestBotKillObjectFamilies.hasNearbyNativeMentor(bot),"Actual native mentor flag/party/range admits object discovery");
        var actions=handler(14).getPlayerBotActions(bot);
        check(actions.stream().anyMatch(a->a.kind()==QuestBotAction.Kind.EVENT&&a.targetId()==700972&&a.dialogAction()==DialogAction.QUEST_SELECT)
            &&actions.getFirst().equals(QuestBotAction.hunt(217175)),"Authorized mentor discovery retains both original object initiation and correct enemy");
        member.x=com.aionemu.gameserver.configs.main.GroupConfig.GROUP_MAX_DISTANCE+1;
        check(!QuestBotKillObjectFamilies.hasNearbyNativeMentor(bot),"Distant mentor does not authorize original spawn interaction");
        member.x=0;member.instance=2;check(!QuestBotKillObjectFamilies.hasNearbyNativeMentor(bot),"Mentor in another instance cannot authorize spawn");
        member.instance=1;member.map=210010000;check(!QuestBotKillObjectFamilies.hasNearbyNativeMentor(bot),"Mentor in another map cannot authorize spawn");
        bot.group=null;check(!QuestBotKillObjectFamilies.hasNearbyNativeMentor(bot),"Stale membership cannot authorize mentor interaction");
    }
    @SuppressWarnings("unchecked")
    static void registration(AbstractQuestHandler handler)throws Exception{
        var engine=QuestEngine.getInstance();var field=QuestEngine.class.getDeclaredField("questNpcs");field.setAccessible(true);
        var map=(Map<Integer,com.aionemu.gameserver.model.templates.quest.QuestNpc>)field.get(engine);var prior=new HashMap<>(map);
        var old=DataManager.NPC_DATA;
        try{
            map.clear();DataManager.NPC_DATA=new NpcData(){@Override public com.aionemu.gameserver.model.templates.npc.NpcTemplate getNpcTemplate(int id){return new com.aionemu.gameserver.model.templates.npc.NpcTemplate();}};
            handler.register();
            check(map.get(217175)!=null&&map.get(217175).getOnKillEvent().contains(47106),"Actual47106 registration must match its original spawned/credited217175 enemy");
            check(map.get(217173)==null||!map.get(217173).getOnKillEvent().contains(47106),"Unrelated217173 cannot receive47106 kill dispatch");
        }finally{map.clear();map.putAll(prior);DataManager.NPC_DATA=old;}
    }
    static void target(com.aionemu.gameserver.model.gameobjects.player.Player bot,VisibleObject npc)throws Exception{
        var field=VisibleObject.class.getDeclaredField("target");field.setAccessible(true);field.set(bot,npc);
    }
    static final String[] TYPES={"haramel._18510MurderMyShugo","haramel._28510DestroytheHaramelFacilities",
        "inggison._11063QuellMastarius","gelkmaros._21063VanquishVeille","reshanta._4205SmackTheShulack",
        "kaisinel_academy._37003CamouflageKillers","kaisinel_academy._37006NowYouSeeThem",
        "marchutan_priory._47003AGlobalProblem","marchutan_priory._47006AmplifiersWithIssues",
        "orichalcum_key._37103CamoAndCarnage","orichalcum_key._37106AsmoHunt",
        "orichalcum_key._37110MyYoungApprentice","orichalcum_key._37113AsmoICU",
        "the_circle._47103AGlobeTrottingLesson","the_circle._47106TurningUpTheAmplifiers",
        "the_circle._47110AGlobeTrottingLesson","the_circle._47113TurningUpTheAmplifiers"};
    static AbstractQuestHandler handler(int index)throws Exception{return NativeStoryRewardChoicesCheck.handler("quest."+TYPES[index]);}
    static void haramel(int index,int sender,int recipient,int work)throws Exception{
        var h=handler(index);int quest=h.getQuestId();var absent=NativeStoryRewardChoicesCheck.actor(quest,null,0);
        var accept=h.getPlayerBotActions(absent).getFirst();
        check(accept.targetId()==sender&&accept.page()==4762&&accept.dialogAction()==DialogAction.ASK_QUEST_ACCEPT,"Reviewed Haramel sender/native page");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(h,absent,sender,DialogAction.QUEST_SELECT),4762)
            &&Objects.equals(NativeStoryRewardChoicesCheck.offered(h,absent,sender,accept.dialogAction()),4),"Actual Haramel acceptance pages, no native grant executed");
        check(absent.quests.getQuestState(quest)==null&&absent.cube.counts.isEmpty(),"Acceptance preview starts or grants nothing");
        var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,0);var state=bot.quests.getQuestState(quest);bot.cube.counts.put(work,2L);
        for(int stage=0;stage<3;stage++){
            check(h.getPlayerBotActions(bot).equals(List.of(QuestBotAction.hunt(700950))),"Exact cart objective until three actual kills");
            check(!h.onKillEvent(NativeDaevanionQuestCheck.kill(bot,quest,999999)),"Unrelated kill cannot credit Haramel");
            check(h.onKillEvent(NativeDaevanionQuestCheck.kill(bot,quest,700950)),"Native cart kill callback advances");
            check(state.getQuestVarById(0)==stage+1&&state.getStatus()==QuestStatus.START,"Native kill counter advances without early reward");
        }
        check(!h.onKillEvent(NativeDaevanionQuestCheck.kill(bot,quest,700950)),"Fourth cart kill cannot bypass object stages");
        var npc=object(700953);target(bot,npc);
        for(int stage=3;stage<=5;stage++){
            check(h.getPlayerBotActions(bot).equals(List.of(QuestBotAction.object(700953))),"Actual processed Odella object stage");
            var wrong=object(700952);
            check(!h.onDialogEvent(new QuestEnv(wrong,bot,quest,DialogAction.USE_OBJECT)),"Unrelated object grants no credit");
            target(bot,wrong);
            check(!h.onDialogEvent(new QuestEnv(npc,bot,quest,DialogAction.USE_OBJECT))&&state.getQuestVarById(0)==stage,"Native object identity requires actual selected target");
            target(bot,npc);
            check(h.onDialogEvent(new QuestEnv(npc,bot,quest,DialogAction.USE_OBJECT)),"Actual native object operation commits its stage");
            check(npc.controller.deaths==stage-2,"Native object-death endpoint receives exactly one call per valid action");
            check(stage==5?state.getStatus()==QuestStatus.REWARD:state.getStatus()==QuestStatus.START&&state.getQuestVarById(0)==stage+1,"Reward only after final native object use");
            check(state.getQuestVarById(1)==5&&bot.cube.getItemCountByItemId(work)==2,"Object callback preserves other counters/owned surplus");
        }
        var reward=h.getPlayerBotActions(bot).getFirst();check(reward.kind()==QuestBotAction.Kind.REWARD&&reward.targetId()==recipient,"Correct faction reward recipient");
        var preview=NativeStoryRewardChoicesCheck.actor(quest,QuestStatus.REWARD,state.getQuestVarById(0));preview.cube.counts.put(work,2L);
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(h,preview,recipient,DialogAction.SELECT_QUEST_REWARD),5),"Native Haramel earned reward page");
        QuestService.removeQuestWorkItems(bot,state);check(bot.cube.getItemCountByItemId(work)==1,"Native work cleanup removes one and retains surplus");
        state.setStatus(QuestStatus.COMPLETE);state.setCompleteCount(1);check(h.getPlayerBotActions(bot).isEmpty(),"Completed Haramel does not replay");
        check(h.getPlayerBotActions(NativeSielQuestCheck.bot(quest,QuestStatus.START,7)).getFirst().kind()==QuestBotAction.Kind.EVENT,"Invalid Haramel stage remains explicit");
    }
    static void spy(int index,int item,int proof,int report,int enemy,int recipient)throws Exception{
        var h=handler(index);int quest=h.getQuestId();var absent=NativeStoryRewardChoicesCheck.actor(quest,null,0);
        check(h.getPlayerBotActions(absent).getFirst().equals(QuestBotAction.item(item)),"Native spy enrollment uses owned item");
        var bot=NativeStoryRewardChoicesCheck.actor(quest,QuestStatus.START,0);var state=bot.quests.getQuestState(quest);
        var first=h.getPlayerBotActions(bot).getFirst();check(first.targetId()==report&&first.page()==1011&&first.dialogAction()==DialogAction.SETPRO1,"Native initial spy report");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(h,bot,report,DialogAction.QUEST_SELECT),1011),"Actual initial spy report page");
        NativeStoryRewardChoicesCheck.offered(h,bot,report,first.dialogAction());check(state.getQuestVarById(0)==1&&state.getStatus()==QuestStatus.START,"Native spy report commits hunt stage");
        check(h.getPlayerBotActions(bot).getFirst().equals(QuestBotAction.hunt(enemy)),"Exact native league enemy");
        var victim=object(enemy);check(h.onKillEvent(new QuestEnv(victim,bot,quest))&&state.getQuestVarById(0)==2,"Actual boss callback advances report stage");
        check(!h.onKillEvent(new QuestEnv(victim,bot,quest)),"Boss credit cannot duplicate");
        var next=h.getPlayerBotActions(bot).getFirst();check(next.dialogAction()==DialogAction.SET_SUCCEED&&next.page()==1693&&next.targetId()==report,"Earned proof uses original native report, no synthetic grant");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(h,bot,report,DialogAction.QUEST_SELECT),1693)&&bot.cube.counts.isEmpty(),"Actual proof report page grants nothing; native item allocation remains unexecuted");
        var reward=NativeStoryRewardChoicesCheck.actor(quest,QuestStatus.REWARD,3);reward.cube.counts.put(item,2L);reward.cube.counts.put(proof,2L);
        check(h.getPlayerBotActions(reward).getFirst().requiredItems().equals(Map.of(item,1L,proof,1L)),"Reward planning retains both native owned requirements");
        for(int action:new int[]{DialogAction.USE_OBJECT,DialogAction.SELECT_QUEST_REWARD,DialogAction.SELECT_QUEST_REWARD}){
            check(NativeStoryRewardChoicesCheck.offered(h,reward,recipient,action)!=null,"Actual spy reward preview opens");
            check(reward.cube.getItemCountByItemId(item)==2&&reward.cube.getItemCountByItemId(proof)==2&&reward.cube.removals==0,"Opening/reopening native reward must not debit either token");
        }
        var missing=NativeStoryRewardChoicesCheck.actor(quest,QuestStatus.REWARD,3);missing.cube.counts.put(item,1L);
        check(NativeStoryRewardChoicesCheck.offered(h,missing,recipient,DialogAction.SELECT_QUEST_REWARD)==null,"Missing proof cannot authorize reward preview");
        QuestService.removeQuestWorkItems(reward,reward.quests.getQuestState(quest));
        check(reward.cube.getItemCountByItemId(item)==1&&reward.cube.getItemCountByItemId(proof)==1&&reward.cube.removals==2,"Actual native work cleanup consumes exactly one pair and preserves surplus");
    }
    static void mentor(int index,int object,int enemy,int recipient)throws Exception{
        var h=handler(index);int quest=h.getQuestId();var absent=NativeStoryRewardChoicesCheck.actor(quest,null,0);
        check(h.getPlayerBotActions(absent).getFirst().kind()==QuestBotAction.Kind.EVENT,"Daily enrollment is an explicit native prerequisite");
        var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,0);var state=bot.quests.getQuestState(quest);
        for(int stage=0;stage<5;stage++){
            var actions=h.getPlayerBotActions(bot);check(actions.getFirst().equals(QuestBotAction.hunt(enemy)),"Exact registered mentor enemy at current native stage");
            check(actions.stream().noneMatch(a->a.targetId()==object)&&actions.stream().anyMatch(a->a.kind()==QuestBotAction.Kind.EVENT),"Absent native party mentor cannot authorize enemy creation");
            check(!h.onDialogEvent(NativeDaevanionQuestCheck.kill(bot,quest,object)),"Actual native object handler refuses absent party mentor without spawning");
            check(h.onKillEvent(NativeDaevanionQuestCheck.kill(bot,quest,enemy))&&state.getQuestVarById(0)==stage+1,"Actual mentor kill callback advances exactly once");
        }
        check(!h.onKillEvent(NativeDaevanionQuestCheck.kill(bot,quest,enemy)),"Sixth mentor kill is not credited");
        var ready=NativeStoryRewardChoicesCheck.actor(quest,QuestStatus.START,5);var report=h.getPlayerBotActions(ready).getFirst();
        check(report.targetId()==recipient&&report.page()==1352&&report.dialogAction()==DialogAction.SELECT_QUEST_REWARD,"Native mentor final report page/action");
        check(Objects.equals(NativeStoryRewardChoicesCheck.offered(h,ready,recipient,DialogAction.QUEST_SELECT),1352),"Actual mentor report offers declared page");
        NativeStoryRewardChoicesCheck.offered(h,ready,recipient,report.dialogAction());check(ready.quests.getQuestState(quest).getStatus()==QuestStatus.REWARD,"Native report alone commits earned reward");
        check(h.getPlayerBotActions(ready).getFirst().equals(QuestBotAction.reward(recipient)),"Actual mentor reward recipient");
        check(h.getPlayerBotActions(NativeStoryRewardChoicesCheck.actor(quest,QuestStatus.START,7)).getFirst().kind()==QuestBotAction.Kind.EVENT,"Invalid mentor counter has no invented action");
    }
    static void shulack()throws Exception{
        var h=handler(4);int quest=4205;var bot=NativeSielQuestCheck.bot(quest,QuestStatus.START,0);var state=bot.quests.getQuestState(quest);
        for(int stage=0;stage<15;stage++){check(h.getPlayerBotActions(bot).getFirst().equals(QuestBotAction.hunt(218972)),"Actual Shulack hunt stages");check(h.onKillEvent(NativeDaevanionQuestCheck.kill(bot,quest,218972))&&state.getQuestVarById(0)==stage+1,"Native fifteen-kill counter");}
        var report=NativeStoryRewardChoicesCheck.actor(quest,QuestStatus.START,15);
        for(int[] step:new int[][]{{279010,DialogAction.SETPRO2,1352,16},{204202,DialogAction.SET_SUCCEED,1693,16}}){
            var action=h.getPlayerBotActions(report).getFirst();check(action.targetId()==step[0]&&action.dialogAction()==step[1]&&action.page()==step[2],"Exact native Shulack report route");
            check(Objects.equals(NativeStoryRewardChoicesCheck.offered(h,report,step[0],DialogAction.QUEST_SELECT),step[2]),"Native offered Shulack report page");
            NativeStoryRewardChoicesCheck.offered(h,report,step[0],step[1]);check(report.quests.getQuestState(quest).getQuestVarById(0)==step[3],"Native Shulack report transition");
        }
        check(report.quests.getQuestState(quest).getStatus()==QuestStatus.REWARD&&h.getPlayerBotActions(report).getFirst().equals(QuestBotAction.reward(204285)),"Separate actual Shulack reward recipient");
    }
    public static void main(String[] args)throws Exception{
        var field=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");field.setAccessible(true);NativeSielQuestCheck.unsafe=field.get(null);
        var old=DataManager.QUEST_DATA;
        try{
            DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(Path.of(args[0],"data/static_data/quest_data/quest_data.xml").toFile());
            if(args.length>1&&args[1].equals("--registration-only")){registration(handler(14));return;}
            haramel(0,203166,203166,182212009);haramel(1,804605,203560,182212021);
            spy(2,182206849,182206850,799049,258220,798926);spy(3,182207852,182207853,799354,281433,799225);shulack();
            int[][] rows={{700968,217169,799836},{700969,217171,799836},{700971,217173,799872},{700972,217175,799872},
                {700968,217169,799906},{700969,217171,799906},{700968,217170,799906},{700969,217172,799906},
                {700971,217173,799921},{700972,217175,799921},{700971,217174,799921},{700972,217176,799921}};
            for(int i=0;i<rows.length;i++)mentor(i+5,rows[i][0],rows[i][1],rows[i][2]);
            int oldRange=com.aionemu.gameserver.configs.main.GroupConfig.GROUP_MAX_DISTANCE;
            try{com.aionemu.gameserver.configs.main.GroupConfig.GROUP_MAX_DISTANCE=100;mentorAdmission();}
            finally{com.aionemu.gameserver.configs.main.GroupConfig.GROUP_MAX_DISTANCE=oldRange;}
            registration(handler(14));
            System.out.println("OK: "+checks+" actual native 17-handler kill/object/report/mentor/custody checks; object death endpoint recorded; no World/SQL/IDs/spawn/item allocation/final rewards");
        }finally{DataManager.QUEST_DATA=old;}
    }
}
