/* StoreLootAction/LootObjectStack, mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md.
 * Native Aion ownership and queued group distribution are inputs, never bypassed. */
package com.aionemu.gameserver.services.playerbot;

import java.util.List;

final class PlayerBotLootPolicy {
 enum Work { NONE, TAKE, OPEN_ROLL }
 record Slot(boolean visible,long count,boolean allowedLooter,boolean distributed,boolean winner,
             boolean queued,boolean rollRequired,boolean useful,boolean canStore) {}
 static Work work(Slot slot) {
  if(!slot.visible() || slot.count()<=0)return Work.NONE;
  if(slot.distributed())return slot.winner() && slot.canStore() ? Work.TAKE : Work.NONE;
  if(!slot.allowedLooter() || slot.queued())return Work.NONE;
  if(slot.rollRequired())return Work.OPEN_ROLL;
  return slot.useful() && slot.canStore() ? Work.TAKE : Work.NONE;
 }
 static boolean rollRequired(boolean kinah,boolean freeForAll,boolean distributed,int inRange,int mode,boolean quality) {
  return !kinah && !freeForAll && !distributed && inRange>1 && mode>1 && quality;
 }
 static boolean canStore(boolean kinah,boolean full,boolean autonomous,boolean special,int limit,int free,
                         boolean stackable,long max,List<Long> stacks,long count) {
  if(kinah)return true;
  boolean partial=stackable && stacks.stream().anyMatch(current->current<max);
  if(full && !partial)return false;
  if(autonomous && !special && limit>0 && (limit-free)*100.0/limit>80)
   return stackable && stacks.stream().anyMatch(current->count<max-current);
  return true;
 }
 private PlayerBotLootPolicy(){}
}
