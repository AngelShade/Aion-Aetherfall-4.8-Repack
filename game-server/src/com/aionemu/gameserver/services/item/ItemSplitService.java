package com.aionemu.gameserver.services.item;

import static com.aionemu.gameserver.services.item.ItemPacketService.sendStorageUpdatePacket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.IStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_CUBE_UPDATE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.LegionService;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemUpdateType;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/**
 * @author ATracer
 */
public class ItemSplitService {

	private static final Logger log = LoggerFactory.getLogger(ItemSplitService.class);

	/**
	 * Move part of stack into different slot
	 */
	public static void splitItem(Player player, int itemObjId, int destinationObjId, long splitAmount, short slotNum, byte sourceStorageType,
		byte destinationStorageType) {
        if (sourceStorageType == 3 || destinationStorageType == 3) {
            synchronized (com.aionemu.gameserver.model.team.legion.LegionWarehouse.CUSTODY_LOCK) {
                if (player.getLegion()==null || com.aionemu.gameserver.model.team.legion.LegionWarehouse.custodyHeld(player.getLegion().getLegionId())) return;
                splitItemNative(player,itemObjId,destinationObjId,splitAmount,slotNum,sourceStorageType,destinationStorageType);
            }
        } else splitItemNative(player,itemObjId,destinationObjId,splitAmount,slotNum,sourceStorageType,destinationStorageType);
    }
    private static void splitItemNative(Player player, int itemObjId, int destinationObjId, long splitAmount, short slotNum, byte sourceStorageType,
        byte destinationStorageType) {
		if (splitAmount <= 0) {
			return;
		}
		if (player.isTrading()) {
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_INVENTORY_SPLIT_DURING_TRADE());
			IStorage source = player.getStorage(sourceStorageType);
			if (source != null && source.getItemByObjId(itemObjId) != null)
				ItemMoveService.restoreSource(player, source.getItemByObjId(itemObjId));
			return;
		}

		IStorage sourceStorage = player.getStorage(sourceStorageType);
		IStorage destStorage = player.getStorage(destinationStorageType);
		if (sourceStorage == null || destStorage == null) {
			log.warn(String.format("storage null playerName sourceStorage destStorage %s %d %d", player.getName(), sourceStorageType,
				destinationStorageType));
			return;
		}
		Item sourceItem = sourceStorage.getItemByObjId(itemObjId);
		Item targetItem = destStorage.getItemByObjId(destinationObjId);

		if (sourceItem == null) {
			sourceItem = sourceStorage.getKinahItem();
			if (sourceItem == null || sourceItem.getObjectId() != itemObjId) {
				log.warn(String.format("CHECKPOINT: attempt to split null item %d %d %d", itemObjId, splitAmount, slotNum));
				return;
			}
		}

		if (sourceStorageType != destinationStorageType
			&& (ItemRestrictionService.isItemRestrictedTo(player, sourceItem, destStorage.getStorageType()) || ItemRestrictionService.isItemRestrictedFrom(
				player, sourceItem, sourceStorage.getStorageType()))) {
			ItemMoveService.restoreSource(player, sourceItem);
			return;
		}

		// To move kinah from inventory to warehouse and vice versa client using split item packet
		if (sourceItem.getItemTemplate().isKinah()) {
			moveKinah(player, sourceStorage, splitAmount);
			return;
		}

		if (targetItem == null) {
			if (splitAmount == sourceItem.getItemCount() && sourceStorageType != destinationStorageType) {
				ItemMoveService.moveItem(player, itemObjId, sourceStorageType, destinationStorageType, slotNum);
				return;
			}
			slotNum = ItemMoveService.warehouseSlot(destStorage, null, slotNum);
			if (destStorage.isFull() || slotNum == -2) {
				ItemMoveService.refuseMove(player, destStorage, sourceItem);
				return;
			}
			long oldItemCount = sourceItem.getItemCount() - splitAmount;
			if (sourceItem.getItemCount() < splitAmount || oldItemCount == 0) {
				ItemMoveService.restoreSource(player, sourceItem);
				return;
			}
			if (sourceStorageType != destinationStorageType) {
				LegionService.getInstance().addWHItemHistory(player, sourceItem.getItemId(), splitAmount, sourceStorage, destStorage);
			}
			Item newItem;
			try {
				newItem = ItemFactory.newItem(sourceItem.getItemTemplate().getTemplateId(), splitAmount);
			} catch (RuntimeException error) {
				log.error("Split allocation refused; retaining source object {}", itemObjId, error);
				ItemMoveService.refuseMove(player, destStorage, sourceItem);
				return;
			}
			if (newItem == null) {
				ItemMoveService.refuseMove(player, destStorage, sourceItem);
				return;
			}
			if (sourceStorageType == destinationStorageType || destinationStorageType == 1 || destinationStorageType == 2)
				newItem.setEquipmentSlot(slotNum);
			boolean added = false;
			try {
				added = destStorage.add(newItem) != null;
			} catch (RuntimeException error) {
				log.error("Split deposit refused; retaining source object {}", itemObjId, error);
			}
			if (!added) {
				if (destStorage.getItemByObjId(newItem.getObjectId()) == newItem)
					destStorage.delete(newItem, ItemPacketService.ItemDeleteType.MOVE);
				IDFactory.getInstance().releaseId(newItem.getObjectId());
				ItemMoveService.refuseMove(player, destStorage, sourceItem);
				return;
			}
			// Never debit the source before the destination has accepted the split.
			sourceStorage.decreaseItemCount(sourceItem, splitAmount, sourceStorageType == destinationStorageType ? ItemUpdateType.DEC_ITEM_SPLIT
				: ItemUpdateType.DEC_ITEM_SPLIT_MOVE);
			PacketSendUtility.sendPacket(player, SM_CUBE_UPDATE.cubeSize(sourceStorage.getStorageType(), player));
		} else if (targetItem.getItemId() == sourceItem.getItemId()) {
			if (sourceStorageType != destinationStorageType) {
				LegionService.getInstance().addWHItemHistory(player, sourceItem.getItemId(), splitAmount, sourceStorage, destStorage);
			}
			mergeStacks(sourceStorage, destStorage, sourceItem, targetItem, splitAmount);
		} else {
			ItemMoveService.restoreSource(player, sourceItem);
		}
	}

	/**
	 * Merge 2 stacks with simple validation
	 */
	public static void mergeStacks(IStorage sourceStorage, IStorage destStorage, Item sourceItem, Item targetItem, long count) {
        if (sourceStorage.getStorageType()==StorageType.LEGION_WAREHOUSE || destStorage.getStorageType()==StorageType.LEGION_WAREHOUSE) {
            synchronized (com.aionemu.gameserver.model.team.legion.LegionWarehouse.CUSTODY_LOCK) {
                for (IStorage storage : java.util.List.of(sourceStorage,destStorage)) {
                    if (storage instanceof com.aionemu.gameserver.model.items.storage.LegionStorageProxy proxy
                        && com.aionemu.gameserver.model.team.legion.LegionWarehouse.custodyHeld(proxy.getLegionId())) return;
                }
                mergeStacksNative(sourceStorage,destStorage,sourceItem,targetItem,count);
            }
        } else mergeStacksNative(sourceStorage,destStorage,sourceItem,targetItem,count);
    }
    private static void mergeStacksNative(IStorage sourceStorage, IStorage destStorage, Item sourceItem, Item targetItem, long count) {
		if (sourceItem.getItemCount() >= count) {
			long freeCount = targetItem.getFreeCount();
			count = count > freeCount ? freeCount : count;
			long leftCount = destStorage.increaseItemCount(targetItem, count,
				sourceStorage.getStorageType() == destStorage.getStorageType() ? ItemUpdateType.INC_ITEM_MERGE : ItemUpdateType.INC_ITEM_COLLECT);
			sourceStorage.decreaseItemCount(sourceItem, count - leftCount,
				sourceStorage.getStorageType() == destStorage.getStorageType() ? ItemUpdateType.DEC_ITEM_SPLIT : ItemUpdateType.DEC_ITEM_SPLIT_MOVE);
		}

	}

	private static void moveKinah(Player player, IStorage source, long splitAmount) {
		if (source.getKinah() < splitAmount)
			return;
		switch (source.getStorageType()) {
			case CUBE: {
				IStorage destination = player.getStorage(StorageType.ACCOUNT_WAREHOUSE.getId());
				long chksum = (source.getKinah() - splitAmount) + (destination.getKinah() + splitAmount);

				if (chksum != source.getKinah() + destination.getKinah())
					return;

				updateKinahCount(source, splitAmount, destination);
				break;
			}

			case ACCOUNT_WAREHOUSE: {
				IStorage destination = player.getStorage(StorageType.CUBE.getId());
				long chksum = (source.getKinah() - splitAmount) + (destination.getKinah() + splitAmount);

				if (chksum != source.getKinah() + destination.getKinah())
					return;

				updateKinahCount(source, splitAmount, destination);
				break;
			}
		}
	}

	private static void updateKinahCount(IStorage source, long splitAmount, IStorage destination) {
		source.decreaseKinah(splitAmount, ItemUpdateType.DEC_ITEM_SPLIT);
		destination.increaseKinah(splitAmount, ItemUpdateType.INC_KINAH_MERGE);
	}
}
