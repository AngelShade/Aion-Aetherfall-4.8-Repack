package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.questEngine.model.*;

/** Transition, discontinuity, completion and spending regressions without live characters. */
public final class PlayerBotCompanionCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static PlayerBotTravel.Position position(int map,int instance,float x,long time){return new PlayerBotTravel.Position(map,instance,new PlayerBotNavigation.Point(x,0,100),time);}
 public static void main(String[] args)throws Exception {
  for(boolean follow:new boolean[]{false,true})for(boolean ownerFlying:new boolean[]{false,true})for(boolean ownerGliding:new boolean[]{false,true})
   for(boolean botFlying:new boolean[]{false,true})for(boolean botGliding:new boolean[]{false,true})for(boolean grounded:new boolean[]{false,true}) {
    var result=PlayerBotFlight.transition(follow,ownerFlying,ownerGliding,botFlying,botGliding,grounded);
    if(!follow)check(result==PlayerBotFlight.Transition.NONE,"Stay/guard does not initiate flight");
    else if(ownerGliding && !botGliding)check(result==PlayerBotFlight.Transition.GLIDE,"Leader glide is mirrored");
    else if(ownerFlying && !ownerGliding && (!botFlying || botGliding))check(result==PlayerBotFlight.Transition.TAKE_OFF,"Powered flight is mirrored");
    else if(!ownerFlying && (botFlying || botGliding) && grounded)check(result==PlayerBotFlight.Transition.LAND,"Land only near real ground");
    else check(result==PlayerBotFlight.Transition.NONE,"Stable/airborne state retained");
  }
  var from=new PlayerBotNavigation.Point(0,0,100);var to=new PlayerBotNavigation.Point(0,0,200);
  check(PlayerBotFlight.step(from,to,4).equals(new PlayerBotNavigation.Point(0,0,104)),"Flight preserves vertical progress instead of ground snapping");
  check(PlayerBotFlight.step(from,new PlayerBotNavigation.Point(0,0,101),4).z()==101,"Air step never overshoots");
  check(PlayerBotFlight.step(from,new PlayerBotNavigation.Point(Float.NaN,0,0),4)==null,"Invalid air coordinate rejected");
  check(PlayerBotFlight.step(from,to,Float.POSITIVE_INFINITY)==null,"Unbounded step rejected");
  check(!PlayerBotTravel.jumped(null,position(1,1,1000,1000),7),"Initial sample is not teleport");
  check(!PlayerBotTravel.jumped(position(1,1,0,0),position(1,1,50,1000),7),"Normal short displacement is not teleport");
  check(PlayerBotTravel.jumped(position(1,1,0,0),position(1,1,1000,1000),7),"Same-map teleport detected");
  check(!PlayerBotTravel.jumped(position(1,1,0,0),position(2,1,1000,1000),7),"Map changes use native map-transfer path");
  check(!PlayerBotTravel.jumped(position(1,1,0,0),position(1,2,1000,1000),7),"Instance changes use native map-transfer path");
  check(!PlayerBotTravel.jumped(position(1,1,0,0),position(1,1,1000,11000),7),"Stale sample does not summon walking bot");
  check(!PlayerBotTravel.jumped(position(1,1,0,1000),position(1,1,1000,0),7),"Clock regression ignored");
  for(boolean enabled:new boolean[]{false,true})for(boolean together:new boolean[]{false,true})for(boolean skipped:new boolean[]{false,true})
   for(QuestStatus leader:QuestStatus.values())for(QuestStatus bot:QuestStatus.values())for(int old=0;old<3;old++)for(int complete=0;complete<3;complete++) {
    boolean result=PlayerBotQuestMirror.shouldComplete(enabled,together,skipped,old,complete,leader,bot);
    check(result==(enabled && together && !skipped && complete>old && leader==QuestStatus.COMPLETE && bot==QuestStatus.START),"No historical, duplicate, skipped or already rewarded party completion");
   }
  for(long money:new long[]{0,9999,10000,50000,100000,Long.MAX_VALUE})for(long budget:new long[]{0,10000,50000})for(long spent:new long[]{0,10000,50000}) {
   long available=PlayerBotCare.allowance(money,10000,budget,spent);
   check(available>=0 && available<=Math.max(0,budget-spent),"Daily spending remains bounded");
   check(available<=Math.max(0,money-Math.max(10000,money/4)),"Reserve and quarter balance retained");
  }
  check(PlayerBotCare.allowance(-1,0,10000,0)==0,"Invalid balance cannot authorize spending");
  for(ItemQuality quality:ItemQuality.values()) {
   ItemTemplate template=new ItemTemplate(){@Override public boolean isArmor(){return true;}@Override public ItemQuality getItemQuality(){return quality;}@Override public boolean isWeapon(){return false;}};
   Item item=new Item(1,template,1,false,0){@Override public boolean isIdentified(){return true;}};
   boolean expendable=quality==ItemQuality.COMMON || quality==ItemQuality.RARE || quality==ItemQuality.LEGEND;
   check(PlayerBotCare.disposable(item,false,false,false,false)==expendable,"Preserve valuable quality "+quality);
   check(!PlayerBotCare.disposable(item,true,false,false,false),"Original possessions protected");
   check(!PlayerBotCare.disposable(item,false,true,false,false),"Upgrades protected");
   check(!PlayerBotCare.disposable(item,false,false,true,false),"Quest items protected");
   check(!PlayerBotCare.disposable(item,false,false,false,true),"Future-level gear protected");
   item.setEnchantLevel(1);check(!PlayerBotCare.disposable(item,false,false,false,false),"Enhanced gear protected");
  }
  QuestState quest=new QuestState(1,QuestStatus.START);quest.setQuestVarById(1,6);quest.setQuestVarById(2,1);
  check(PlayerBotQuestJournal.count(quest,1,100)==70,"Native six-bit multi-variable quest counter decoded");
  check(PlayerBotQuestJournal.count(quest,1,5)==5,"Quest counter display capped at required count");
  check(PlayerBotQuestMetadata.startPage(4) && PlayerBotQuestMetadata.startPage(1011) && PlayerBotQuestMetadata.startPage(4762),"Native generic acceptance pages supported");
  check(!PlayerBotQuestMetadata.startPage(1001) && !PlayerBotQuestMetadata.startPage(5),"Story/reward page is not acceptance");
  Path directory=Files.createTempDirectory("companion-settings-check-");
  var preferences=new PlayerBotPreferences(directory);var defaults=preferences.load(1,2,PlayerBotRules.Role.HEALER);
  check(defaults.gear() && defaults.questing() && !defaults.area() && !defaults.loot(),"Requested gear/quest defaults without AoE or loot changes");
  var manual=new PlayerBotPreferences.Values(PlayerBotRules.Role.TANK,false,true,false,false,false);preferences.save(1,2,manual);
  check(preferences.load(1,2,PlayerBotRules.Role.HEALER).equals(manual),"Existing player's manual choices preserved");
  for(Path path:Files.list(directory).toList())Files.delete(path);Files.delete(directory);
  System.out.println("OK: "+checks+" companion regressions: flight, same-map teleport, quest completion ownership/repeats, gear protection, spending and settings");
 }
}
