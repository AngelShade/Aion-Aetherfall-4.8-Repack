package com.aionemu.gameserver.services.drop;

import java.util.*;
import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.gameobjects.DropNpc;
import com.aionemu.gameserver.model.gameobjects.player.InRoll;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.team.common.legacy.LootGroupRules;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_GROUP_LOOT;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.playerbot.PlayerBotLoot;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;

/** Per-item custody; Player.inRoll is an oldest-pending compatibility view. */
public class DropDistributionService {
 public static final int PASS=0, NEED=1, GREED=2;
 private static final int MAX_ACTIVE=5;
 private final Map<DropItem,Round> active=new IdentityHashMap<>();
 private final Map<Integer,LinkedHashSet<Round>> pending=new HashMap<>();
 private final Set<LootGroupRules> queuedTeams=Collections.newSetFromMap(new IdentityHashMap<>());
 private boolean draining;
 private static final class Round {
  final DropNpc corpse;final DropItem item;final LootGroupRules rules;final int mode,itemId;
  final boolean needGreed;
  final List<Player> members;final Map<Integer,Player> awaiting=new LinkedHashMap<>();int winningTier,tiedWinners;
  Round(DropNpc corpse,DropItem item,LootGroupRules rules,int mode,List<Player> members){
   this.corpse=corpse;this.item=item;this.rules=rules;this.mode=mode;itemId=item.getDropTemplate().getItemId();this.members=List.copyOf(members);
   needGreed=mode==2&&GroupConfig.LOOT_NEED_GREED;
   members.forEach(p->awaiting.put(p.getObjectId(),p));
  }
 }
 public static DropDistributionService getInstance(){return SingletonHolder.instance;}
 /** Called after native corpse/item loot-right checks; repeat requests never restart a timer. */
 public synchronized void queue(DropItem item,DropNpc corpse,LootGroupRules rules,int mode){
  if(mode!=2&&mode!=3||item.isFreeForAll()||item.isDistributeItem())return;
  if(!rules.containDropItem(item)){item.setDistributionMode(mode);item.setHighestValue(0);item.setWinningPlayer(null);rules.addItemToBeDistributed(item);}
  queuedTeams.add(rules);drain();
 }
 private boolean current(Round r){
  var registration=DropRegistrationService.getInstance();
  if(registration.getDropRegistrationMap().get(r.corpse.getObjectId())!=r.corpse)return false;
  Set<DropItem> items=registration.getCurrentDropMap().get(r.corpse.getObjectId());if(items==null)return false;
  synchronized(items){return items.contains(r.item)&&r.item.getCount()>0;}
 }
 private boolean member(Player p,DropNpc corpse,DropItem item){
  if(p==null||!p.isPlaying()||!corpse.getInRangePlayers().contains(p)||!item.canViewDropItem(p.getObjectId())
   ||p.getCurrentTeam()==null||p.getCurrentTeam().getTeamId()!=corpse.getLootingTeamId())return false;
  var npc=World.getInstance().findVisibleObject(corpse.getObjectId());
  return npc!=null&&npc.isSpawned()&&npc.getWorldId()==p.getWorldId()&&npc.getInstanceId()==p.getInstanceId()
   &&PositionUtil.isInRange(p,npc,GroupConfig.GROUP_MAX_DISTANCE);
 }
 private void compatibility(Player p){
  Set<Round> rolls=pending.get(p.getObjectId());
  if(rolls==null||rolls.isEmpty()){pending.remove(p.getObjectId());p.inRoll=null;return;}
  Round r=rolls.iterator().next();p.inRoll=new InRoll(r.corpse.getObjectId(),r.itemId,r.item.getIndex(),r.mode);
 }
 public synchronized List<InRoll> getPendingRolls(Player p){
  Set<Round> rolls=pending.get(p.getObjectId());if(rolls==null)return List.of();
  return rolls.stream().map(r->new InRoll(r.corpse.getObjectId(),r.itemId,r.item.getIndex(),r.mode)).toList();
 }
 private void drain(){
  if(draining)return;draining=true;
  try{
   for(LootGroupRules rules:new ArrayList<>(queuedTeams)){
    for(DropItem item:new ArrayList<>(rules.getItemsToBeDistributed())){
     if(active.containsKey(item))continue;
     DropNpc corpse=DropRegistrationService.getInstance().getDropRegistrationMap().get(item.getNpcObj());
     Set<DropItem> items=DropRegistrationService.getInstance().getCurrentDropMap().get(item.getNpcObj());
     boolean present=false;if(items!=null)synchronized(items){present=items.contains(item);}
     if(corpse==null||!present||item.getCount()<=0||item.isFreeForAll()||item.isDistributeItem()){rules.removeItemToBeDistributed(item);continue;}
     int mode=item.getDistributionMode();
     List<Player> members=corpse.getInRangePlayers().stream().filter(p->member(p,corpse,item)).distinct().toList();
     if(members.isEmpty()){item.isFreeForAll(true);rules.removeItemToBeDistributed(item);continue;}
     long teamActive=active.values().stream().filter(r->r.rules==rules).count();
     // Native bids stay serialized. Roll windows are bounded per team and per recipient, including different corpses.
     if(teamActive>=MAX_ACTIVE||mode==3&&teamActive>0||active.values().stream().anyMatch(r->r.rules==rules&&r.mode==3)
      ||members.stream().anyMatch(p->pending.getOrDefault(p.getObjectId(),new LinkedHashSet<>()).size()>=MAX_ACTIVE))continue;
     Round r=new Round(corpse,item,rules,mode,members);active.put(item,r);corpse.setCurrentIndex(item.getIndex());
     for(Player p:members){pending.computeIfAbsent(p.getObjectId(),id->new LinkedHashSet<>()).add(r);compatibility(p);PacketSendUtility.sendPacket(p,packet(r,0,1));}
     ThreadPoolManager.getInstance().schedule(()->expire(r),mode==2?17000:32000);
    }
    if(rules.getItemsToBeDistributed().isEmpty())queuedTeams.remove(rules);
   }
  }finally{draining=false;}
 }
 public synchronized void handleRollOrBid(Player p,int mode,int roll,long bid,int itemId,int npcId,int index){
  if(p==null||mode!=2&&mode!=3||mode==2&&roll!=PASS&&roll!=NEED&&roll!=GREED)return;
  Round r=find(p,npcId,index,itemId,mode);if(r==null)return;
  if(!current(r)){discard(r);drain();return;}
  if(!member(p,r.corpse,r.item))return;
  var template=DataManager.ITEM_DATA.getItemTemplate(itemId);
  if(template==null)return;
  if(template.hasLimitOne()&&(p.getInventory().getFirstItemByItemId(itemId)!=null
   ||p.getEquipment().getEquippedItems().stream().anyMatch(i->i.getItemId()==itemId)
   ||p.getStorage(StorageType.REGULAR_WAREHOUSE.getId()).getFirstItemByItemId(itemId)!=null)){
   roll=PASS;bid=0;
  }
  if(r.needGreed&&roll==NEED&&!canNeed(p,template)){
   roll=GREED;
   PacketSendUtility.sendMessage(p,"Need requires an item you can use or a required quest objective. Rolling Greed instead.");
  }
  respond(r,p,roll,bid);drain();
 }
 /** Same class, level, race, rank, gender and mastery limits as native item
  * use/equipment. Quest custody is checked separately, before rejecting Need. */
 private boolean canNeed(Player p,ItemTemplate item){
  int level=item.getRequiredLevel(p.getPlayerClass()),maximum=item.getMaxLevelRestrict(p.getPlayerClass());
  var limits=item.getUseLimits();
  boolean usable=item.isClassSpecific(p.getPlayerClass())&&level>=0&&level<=p.getLevel()&&(maximum==0||p.getLevel()<=maximum)
   &&(item.getItemSlot()!=0||item.getActions()!=null&&!item.getActions().getItemActions().isEmpty())
   &&(item.getRace()==Race.PC_ALL||item.getRace()==p.getRace())
   &&(limits.getGenderPermitted()==null||limits.getGenderPermitted()==p.getGender())
   &&limits.verifyRank(p.getAbyssRank().getRank().getId())
   &&limits.getGuildLevelPermitted()<=(p.getLegion()==null?0:p.getLegion().getLegionLevel())
   &&(!item.hasWorldRestrictions()||item.isItemRestrictedToWorld(p.getWorldId()));
  if(usable&&(item.isWeapon()||item.isArmor())){
   Set<Integer> skills=DataManager.SKILL_DATA.getMasterySkills(item.getItemGroup());
   usable=skills.isEmpty()||skills.stream().anyMatch(p.getSkillList()::isSkillPresent);
  }
  return usable||PlayerBotLoot.needsObjective(p,item.getTemplateId());
 }
 private Round find(Player p,int npcId,int index,int itemId,int mode){
  Set<Round> rolls=pending.get(p.getObjectId());if(rolls==null)return null;
  for(Round r:rolls)if(r.corpse.getObjectId()==npcId&&r.item.getIndex()==index&&r.itemId==itemId&&r.mode==mode&&r.awaiting.get(p.getObjectId())==p)return r;
  return null;
 }
 private synchronized void expire(Round r){
  if(active.get(r.item)!=r)return;
  if(!current(r))discard(r);else for(Player p:new ArrayList<>(r.awaiting.values()))respond(r,p,PASS,0);
  drain();
 }
 /** Legacy timeout adapters resolve exact per-item custody, never Player.inRoll's next item. */
 public synchronized void timeout(Player p,int npcId,int index){
  for(InRoll roll:getPendingRolls(p))if(roll.getNpcId()==npcId&&roll.getIndex()==index){
   Round r=find(p,npcId,index,roll.getItemId(),roll.getRollType());
   if(r!=null){if(current(r))respond(r,p,PASS,0);else discard(r);}break;
  }
  drain();
 }
 private SM_GROUP_LOOT packet(Round r,int player,long value){
  return new SM_GROUP_LOOT(r.corpse.getLootingTeamId(),player,r.itemId,(int)r.item.getCount(),r.corpse.getObjectId(),r.mode,value,r.item.getIndex());
 }
 private void respond(Round r,Player p,int vote,long bid){
  if(r.awaiting.remove(p.getObjectId())==null)return;
  Set<Round> rolls=pending.get(p.getObjectId());if(rolls!=null)rolls.remove(r);compatibility(p);
  long value;int tier;
  if(r.mode==2){tier=vote==PASS?0:r.needGreed&&vote==NEED?2:1;value=tier==0?0:Rnd.get(1,r.corpse.getMaxRoll());
   PacketSendUtility.sendPacket(p,tier==0?SM_SYSTEM_MESSAGE.STR_MSG_DICE_GIVEUP_ME():SM_SYSTEM_MESSAGE.STR_MSG_DICE_RESULT_ME((int)value,r.corpse.getMaxRoll()));
  }else{value=bid<0||bid>999999999||bid>p.getInventory().getKinah()?0:bid;tier=value>0?1:0;
   PacketSendUtility.sendPacket(p,value>0?SM_SYSTEM_MESSAGE.STR_MSG_PAY_RESULT_ME():SM_SYSTEM_MESSAGE.STR_MSG_PAY_GIVEUP_ME());}
  for(Player other:r.members){
   PacketSendUtility.sendPacket(other,packet(r,p.getObjectId(),value));if(other.equals(p)||!other.isOnline())continue;
   if(r.mode==2)PacketSendUtility.sendPacket(other,value==0?SM_SYSTEM_MESSAGE.STR_MSG_DICE_GIVEUP_OTHER(p.getName()):SM_SYSTEM_MESSAGE.STR_MSG_DICE_RESULT_OTHER(p.getName(),(int)value,r.corpse.getMaxRoll()));
   else PacketSendUtility.sendPacket(other,value==0?SM_SYSTEM_MESSAGE.STR_MSG_PAY_GIVEUP_OTHER(p.getName()):SM_SYSTEM_MESSAGE.STR_MSG_PAY_RESULT_OTHER(p.getName()));
  }
  if(tier>0&&(tier>r.winningTier||tier==r.winningTier&&value>r.item.getHighestValue())){
   r.winningTier=tier;r.tiedWinners=1;r.item.setHighestValue(value);r.item.setWinningPlayer(p);
  }else if(tier>0&&tier==r.winningTier&&value==r.item.getHighestValue()&&Rnd.get(1,++r.tiedWinners)==1)r.item.setWinningPlayer(p);
  if(r.awaiting.isEmpty())finish(r);
 }
 private void finish(Round r){
  active.remove(r.item);r.rules.removeItemToBeDistributed(r.item);Player winner=r.item.getWinningPlayer();
  for(Player p:r.members){if(winner==null)PacketSendUtility.sendPacket(p,SM_SYSTEM_MESSAGE.STR_MSG_PAY_ALL_GIVEUP());PacketSendUtility.sendPacket(p,packet(r,winner==null?1:winner.getObjectId(),0xFFFFFFFFL));}
  if(winner==null)r.item.isFreeForAll(true);
  else{r.item.isDistributeItem(true);DropService.getInstance().requestDropItem(winner,r.corpse.getObjectId(),r.item.getIndex());}
 }
 private void discard(Round r){
  active.remove(r.item);r.rules.removeItemToBeDistributed(r.item);
  for(Player p:r.awaiting.values()){Set<Round> rolls=pending.get(p.getObjectId());if(rolls!=null)rolls.remove(r);compatibility(p);}r.awaiting.clear();
 }
 public synchronized void unregister(int npcId){
  for(Round r:new ArrayList<>(active.values()))if(r.corpse.getObjectId()==npcId)discard(r);
  for(LootGroupRules rules:queuedTeams)rules.getItemsToBeDistributed().removeIf(item->item.getNpcObj()==npcId);
  queuedTeams.removeIf(r->r.getItemsToBeDistributed().isEmpty());
  drain();
 }
 private static class SingletonHolder{protected static final DropDistributionService instance=new DropDistributionService();}
}
