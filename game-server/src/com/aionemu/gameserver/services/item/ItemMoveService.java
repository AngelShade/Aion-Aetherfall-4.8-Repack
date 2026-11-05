package com.aionemu.gameserver.services.item;

import static com.aionemu.gameserver.services.item.ItemPacketService.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.IStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.LegionService;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author ATracer
 */
public class ItemMoveService {

	private static final Logger log = LoggerFactory.getLogger(ItemMoveService.class);

	public static void moveItem(Player player, int itemObjId, byte sourceStorageType, byte destinationStorageType, short slot) {
        if (sourceStorageType == 3 || destinationStorageType == 3) {
            synchronized (com.aionemu.gameserver.model.team.legion.LegionWarehouse.CUSTODY_LOCK) {
                if (player.getLegion() == null || com.aionemu.gameserver.model.team.legion.LegionWarehouse.custodyHeld(player.getLegion().getLegionId())) return;
                moveItemNative(player, itemObjId, sourceStorageType, destinationStorageType, slot);
            }
        } else moveItemNative(player, itemObjId, sourceStorageType, destinationStorageType, slot);
    }
    private static void moveItemNative(Player player, int itemObjId, byte sourceStorageType, byte destinationStorageType, short slot) {
		IStorage sourceStorage = player.getStorage(sourceStorageType);
		if (sourceStorage == null) {
			log.error("{} tried to move itemObjId {} from unknown sourceStorageType: {}", player, itemObjId, sourceStorageType);
			return;
		}
		Item item = sourceStorage.getItemByObjId(itemObjId);
		if (item == null)
			return;

		IStorage targetStorage = player.getStorage(destinationStorageType);
		if (targetStorage == null) {
			log.error("{} tried to move itemObjId {} to unknown destinationStorageType: {}", player, itemObjId, destinationStorageType);
			restoreSource(player, item);
			return;
		}

		if (sourceStorageType == destinationStorageType) {
			slot = warehouseSlot(targetStorage, item, slot);
			if (slot == -2) {
				refuseMove(player, targetStorage, item);
				return;
			}
			if (item.getEquipmentSlot() != slot)
				moveInSameStorage(sourceStorage, item, slot);
			return;
		}
		if (ItemRestrictionService.isItemRestrictedTo(player, item, targetStorage.getStorageType())
			|| ItemRestrictionService.isItemRestrictedFrom(player, item, sourceStorage.getStorageType())
			|| player.isTrading()
			|| GameServer.isShuttingDownSoon()) {
			restoreSource(player, item);
			if (GameServer.isShuttingDownSoon())
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_DISABLE("Shutdown Progress"));
			return;
		}

		if (sourceStorageType == StorageType.LEGION_WAREHOUSE.getId() || destinationStorageType == StorageType.LEGION_WAREHOUSE.getId()) {
			LegionService.getInstance().addWHItemHistory(player, item.getItemId(), item.getItemCount(), sourceStorage, targetStorage);
		}
		if (slot == -1) {
			if (item.getItemTemplate().isStackable()) {
				for (Item targetStack : targetStorage.getItemsByItemId(item.getItemId())) {
					ItemSplitService.mergeStacks(sourceStorage, targetStorage, item, targetStack, item.getItemCount());
					if (item.getItemCount() == 0) {
						return;
					}
				}
			}
		}
		slot = warehouseSlot(targetStorage, item, slot);
		if (targetStorage.isFull() || slot == -2) {
			refuseMove(player, targetStorage, item);
			return;
		}
		long sourceSlot = item.getEquipmentSlot();
		if (sourceStorage.remove(item) != item) {
			restoreSource(player, item);
			return;
		}
		item.setEquipmentSlot(slot);
		try {
			// Native UI requires delete before add. A refusal explicitly recreates the source slot.
			sendItemDeletePacket(player, sourceStorage.getStorageType(), item, ItemDeleteType.MOVE);
			if (targetStorage.add(item) != null)
				return;
		} catch (RuntimeException error) {
			log.error("Item transfer refused; restoring object {} to {}", itemObjId, sourceStorage.getStorageType(), error);
		}
		boolean published = targetStorage.getItemByObjId(itemObjId) == item;
		if (published) {
			targetStorage.remove(item);
		}
		item.setEquipmentSlot(sourceSlot);
		item.setItemLocation(sourceStorageType);
		// Restore custody before attempting any corrective client I/O.
		sourceStorage.onLoadHandler(item);
		sourceStorage.setPersistentState(PersistentState.UPDATE_REQUIRED);
		if (sourceStorage.getItemByObjId(itemObjId) != item)
			throw new IllegalStateException("Could not restore refused item " + itemObjId);
		if (published)
			sendItemDeletePacket(player, targetStorage.getStorageType(), item, ItemDeleteType.MOVE);
		refuseMove(player, targetStorage, item);
	}

	/** Resolve real warehouse slots, including the unsigned 0xffff automatic-slot sentinel. */
	static short warehouseSlot(IStorage storage, Item moving, short requested) {
		if (storage.getStorageType() != StorageType.REGULAR_WAREHOUSE && storage.getStorageType() != StorageType.ACCOUNT_WAREHOUSE)
			return requested;
		if (requested < -1 || requested >= storage.getLimit())
			return -2;
		boolean[] occupied = new boolean[storage.getLimit()];
		for (Item item : storage.getItems()) {
			long slot = item.getEquipmentSlot();
			if (item != moving && slot >= 0 && slot < occupied.length)
				occupied[(int) slot] = true;
		}
		if (requested >= 0)
			return occupied[requested] ? -2 : requested;
		for (short slot = 0; slot < occupied.length; slot++)
			if (!occupied[slot])
				return slot;
		return -2;
	}

	static void refuseMove(Player player, IStorage destination, Item item) {
		PacketSendUtility.sendPacket(player, destination.getStorageIsFullMessage());
		restoreSource(player, item);
	}

	static void restoreSource(Player player, Item item) {
		StorageType source = StorageType.getStorageTypeById(item.getItemLocation());
		if (source != null) {
			// ALL_SLOT alone can leave the client's optimistic drag/lock in place.
			sendItemDeletePacket(player, source, item, ItemDeleteType.MOVE);
			sendStorageUpdatePacket(player, source, item, ItemAddType.PARTIAL_WITH_SLOT);
		}
	}

	private static void moveInSameStorage(IStorage storage, Item item, short slot) {
		storage.setPersistentState(PersistentState.UPDATE_REQUIRED);
		item.setEquipmentSlot(slot);
		item.setPersistentState(PersistentState.UPDATE_REQUIRED);
	}

	public static void switchItemsInStorages(Player player, byte sourceStorageType, int sourceItemObjId, byte replaceStorageType, int replaceItemObjId) {
        if (sourceStorageType == 3 || replaceStorageType == 3) {
            synchronized (com.aionemu.gameserver.model.team.legion.LegionWarehouse.CUSTODY_LOCK) {
                if (player.getLegion() == null || com.aionemu.gameserver.model.team.legion.LegionWarehouse.custodyHeld(player.getLegion().getLegionId())) return;
                switchItemsInStoragesNative(player, sourceStorageType, sourceItemObjId, replaceStorageType, replaceItemObjId);
            }
        } else switchItemsInStoragesNative(player, sourceStorageType, sourceItemObjId, replaceStorageType, replaceItemObjId);
    }
    private static void switchItemsInStoragesNative(Player player, byte sourceStorageType, int sourceItemObjId, byte replaceStorageType, int replaceItemObjId) {
		IStorage sourceStorage = player.getStorage(sourceStorageType);
		IStorage replaceStorage = player.getStorage(replaceStorageType);
		if (sourceStorage == null || replaceStorage == null)
			return;

		Item sourceItem = sourceStorage.getItemByObjId(sourceItemObjId);
		if (sourceItem == null)
			return;

		Item replaceItem = replaceStorage.getItemByObjId(replaceItemObjId);
		if (replaceItem == null)
			return;

		// restrictions checks
		if (ItemRestrictionService.isItemRestrictedFrom(player, sourceItem, sourceStorage.getStorageType())
			|| ItemRestrictionService.isItemRestrictedFrom(player, replaceItem, replaceStorage.getStorageType())
			|| ItemRestrictionService.isItemRestrictedTo(player, sourceItem, replaceStorage.getStorageType())
			|| ItemRestrictionService.isItemRestrictedTo(player, replaceItem, sourceStorage.getStorageType())
			|| player.isTrading() 
			|| GameServer.isShuttingDownSoon()) {
			sendItemUnlockPacket(player, sourceItem);
			sendItemUnlockPacket(player, replaceItem);
			if (GameServer.isShuttingDownSoon())
				PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_MSG_DISABLE("Shutdown Progress"));
			return;
		}

		long sourceSlot = sourceItem.getEquipmentSlot();
		long replaceSlot = replaceItem.getEquipmentSlot();

		sourceItem.setEquipmentSlot(replaceSlot);
		replaceItem.setEquipmentSlot(sourceSlot);

		sourceStorage.remove(sourceItem);
		replaceStorage.remove(replaceItem);

		// correct UI update order is 1)delete items 2) add items
		sendItemDeletePacket(player, sourceStorage.getStorageType(), sourceItem, ItemDeleteType.MOVE);
		sendItemDeletePacket(player, replaceStorage.getStorageType(), replaceItem, ItemDeleteType.MOVE);
		try {
			if (sourceStorage.add(replaceItem) != null && replaceStorage.add(sourceItem) != null)
				return;
		} catch (RuntimeException error) {
			log.error("Item swap refused; restoring objects {} and {}", sourceItemObjId, replaceItemObjId, error);
		}
		// Remove any partial publication before restoring both original identities.
		if (sourceStorage.getItemByObjId(replaceItemObjId) == replaceItem)
			sourceStorage.remove(replaceItem);
		if (replaceStorage.getItemByObjId(sourceItemObjId) == sourceItem)
			replaceStorage.remove(sourceItem);
		sourceItem.setEquipmentSlot(sourceSlot);
		sourceItem.setItemLocation(sourceStorageType);
		replaceItem.setEquipmentSlot(replaceSlot);
		replaceItem.setItemLocation(replaceStorageType);
		// Loading restores membership without running the destination admission that just failed.
		sourceStorage.onLoadHandler(sourceItem);
		replaceStorage.onLoadHandler(replaceItem);
		sourceStorage.setPersistentState(PersistentState.UPDATE_REQUIRED);
		replaceStorage.setPersistentState(PersistentState.UPDATE_REQUIRED);
		sendItemDeletePacket(player, sourceStorage.getStorageType(), replaceItem, ItemDeleteType.MOVE);
		sendItemDeletePacket(player, replaceStorage.getStorageType(), sourceItem, ItemDeleteType.MOVE);
		refuseMove(player, replaceStorage, sourceItem);
		restoreSource(player, replaceItem);
	}
}
