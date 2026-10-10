package com.aionemu.gameserver.model.items.storage;

import java.util.List;
import java.util.Queue;

import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.legion.LegionWarehouse;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemAddType;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemDeleteType;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemUpdateType;

/**
 * @author ATracer
 */
public class LegionStorageProxy extends Storage {

	private final Player actor;
	private final int legionId;
	public int getLegionId() { return legionId; }
	private void checkCustody() { if (LegionWarehouse.custodyHeld(legionId)) throw new IllegalStateException("Legion custody held"); }
	private final Storage storage;

	public LegionStorageProxy(LegionWarehouse storage, Player actor) {
		super(storage.getStorageType(), false);
		this.actor = actor;
		this.legionId = storage.getLegionId();
		this.storage = storage;
	}

	@Override
	public void increaseKinah(long amount) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		storage.increaseKinah(amount, actor);
		}
	}

	@Override
	public void increaseKinah(long amount, ItemUpdateType updateType) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		storage.increaseKinah(amount, updateType, actor);
		}
	}

	@Override
	public boolean tryDecreaseKinah(long amount) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.tryDecreaseKinah(amount, actor);
		}
	}

	@Override
	public boolean tryDecreaseKinah(long amount, ItemUpdateType updateType) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.tryDecreaseKinah(amount, updateType, actor);
		}
	}

	@Override
	public void decreaseKinah(long amount) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		storage.decreaseKinah(amount, actor);
		}
	}

	@Override
	public void decreaseKinah(long amount, ItemUpdateType updateType) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		storage.decreaseKinah(amount, updateType, actor);
		}
	}

	@Override
	public long increaseItemCount(Item item, long count) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.increaseItemCount(item, count, actor);
		}
	}

	@Override
	public long increaseItemCount(Item item, long count, ItemUpdateType updateType) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.increaseItemCount(item, count, updateType, actor);
		}
	}

	@Override
	public long decreaseItemCount(Item item, long count) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.decreaseItemCount(item, count, actor);
		}
	}

	@Override
	public long decreaseItemCount(Item item, long count, ItemUpdateType updateType) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.decreaseItemCount(item, count, updateType, actor);
		}
	}

	@Override
	public long decreaseItemCount(Item item, long count, ItemUpdateType updateType, QuestStatus questStatus) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		throw new UnsupportedOperationException("Quests should not update LWH!");
		}
	}

	@Override
	public Item add(Item item) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.add(item, actor);
		}
	}

	@Override
	public Item add(Item item, ItemAddType addType) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.add(item, addType, actor);
		}
	}

	@Override
	public Item put(Item item) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.put(item, actor);
		}
	}

	@Override
	public Item delete(Item item) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.delete(item, actor);
		}
	}

	@Override
	public Item delete(Item item, ItemDeleteType deleteType) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.delete(item, deleteType, actor);
		}
	}

	@Override
	public boolean decreaseByItemId(int itemId, long count) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.decreaseByItemId(itemId, count, actor);
		}
	}

	@Override
	public boolean decreaseByItemId(int itemId, long count, QuestStatus questStatus) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		throw new UnsupportedOperationException("Quests should not update LWH!");
		}
	}

	@Override
	public boolean decreaseByObjectId(int itemObjId, long count) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.decreaseByObjectId(itemObjId, count, actor);
		}
	}

	@Override
	public boolean decreaseByObjectId(int itemObjId, long count, ItemUpdateType updateType) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.decreaseByObjectId(itemObjId, count, updateType, actor);
		}
	}

	@Override
	public boolean decreaseByObjectId(int itemObjId, long count, QuestStatus questStatus) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		throw new UnsupportedOperationException("Quests should not update LWH!");
		}
	}

	@Override
	public long getKinah() {
		return storage.getKinah();
	}

	@Override
	public Item getKinahItem() {
		return storage.getKinahItem();
	}

	@Override
	public StorageType getStorageType() {
		return storage.getStorageType();
	}

	@Override
	public void onLoadHandler(Item item) {
		storage.onLoadHandler(item);
	}

	@Override
	public Item remove(Item item) {
		synchronized (LegionWarehouse.CUSTODY_LOCK) { checkCustody();
		return storage.remove(item);
		}
	}

	@Override
	public Item getFirstItemByItemId(int itemId) {
		return storage.getFirstItemByItemId(itemId);
	}

	@Override
	public List<Item> getItemsWithKinah() {
		return storage.getItemsWithKinah();
	}

	@Override
	public List<Item> getItems() {
		return storage.getItems();
	}

	@Override
	public List<Item> getItemsByItemId(int itemId) {
		return storage.getItemsByItemId(itemId);
	}

	@Override
	public Queue<Item> getDeletedItems() {
		return storage.getDeletedItems();
	}

	@Override
	public Item getItemByObjId(int itemObjId) {
		return storage.getItemByObjId(itemObjId);
	}

	@Override
	public boolean isFull() {
		return storage.isFull();
	}

	@Override
	public int getFreeSlots() {
		return storage.getFreeSlots();
	}

	@Override
	public void setLimit(int limit) {
		storage.setLimit(limit);
	}

	@Override
	public int getLimit() {
		return storage.getLimit();
	}

	@Override
	public int size() {
		return storage.size();
	}

	@Override
	public void setOwner(Player player) {
		throw new UnsupportedOperationException("LWH doesnt have owner");
	}

}
