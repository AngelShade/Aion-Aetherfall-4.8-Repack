import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.main.CustomConfig;
import com.aionemu.gameserver.model.account.PlayerAccountData;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.serverpackets.SM_CUBE_UPDATE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INVENTORY_INFO;
import com.aionemu.gameserver.services.CubeExpandService;

/** Isolated packet and reward regression; no server, database, or live character. */
public class InventoryExpansionCheck {
    static Unsafe unsafe;

    static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    static int get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(object);
    }

    static void require(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }

    static void packets(Player player) throws Exception {
        SM_CUBE_UPDATE update = SM_CUBE_UPDATE.cubeSize(StorageType.CUBE, player);
        int npc = get(update, "npcExpands"), quest = get(update, "questExpands"), item = get(update, "itemExpands");
        require(27 + 9 * (npc + quest + item) == player.getInventory().getLimit(), "Update capacity matches storage");
        SM_INVENTORY_INFO login = (SM_INVENTORY_INFO) unsafe.allocateInstance(SM_INVENTORY_INFO.class);
        set(login, "isFirstPacket", true);
        set(login, "items", new ArrayList<>());
        set(login, "player", player);
        Class<?> parent = login.getClass();
        Field field = null;
        while (parent != null) {
            try { field = parent.getDeclaredField("buf"); break; }
            catch (NoSuchFieldException ignored) { parent = parent.getSuperclass(); }
        }
        field.setAccessible(true);
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        field.set(login, buffer);
        var writer = SM_INVENTORY_INFO.class.getDeclaredMethod("writeImpl", AionConnection.class);
        writer.setAccessible(true);
        writer.invoke(login, new Object[]{null});
        byte[] bytes = buffer.array();
        require(buffer.position() == 6 && bytes[0] == 1 && Byte.toUnsignedInt(bytes[1]) == npc
            && Byte.toUnsignedInt(bytes[2]) == quest && Byte.toUnsignedInt(bytes[3]) == item,
            "Login and update expansion headers agree");
    }

    public static void main(String[] args) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        Player player = (Player) unsafe.allocateInstance(Player.class);
        PlayerCommonData data = (PlayerCommonData) unsafe.allocateInstance(PlayerCommonData.class);
        PlayerAccountData account = (PlayerAccountData) unsafe.allocateInstance(PlayerAccountData.class);
        set(account, "playerCommonData", data);
        set(player, "playerAccountData", account);
        set(player, "inventory", new PlayerStorage(player, StorageType.CUBE));
        CustomConfig.UNIFIED_INVENTORY = true;
        CustomConfig.CUBE_EXPANSION_LIMIT = 11;
        player.setCubeLimit();
        require(player.getInventory().getLimit() == 180, "Starting capacity");
        packets(player);
        CubeExpandService.questExpand(player);
        require(data.getQuestExpands() == 1 && player.getInventory().getLimit() == 189, "Quest grants nine slots");
        require(CubeExpandService.canExpandByTicket(player, 1), "First ticket allowed");
        CubeExpandService.itemExpand(player);
        require(data.getItemExpands() == 1 && player.getInventory().getLimit() == 198, "Ticket grants nine slots");
        require(!CubeExpandService.canExpandByTicket(player, 1), "Used ticket level rejected");
        require(CubeExpandService.canExpandByTicket(player, 2), "Next ticket level allowed");
        packets(player);
        data.setNpcExpands(5); data.setQuestExpands(3); data.setItemExpands(2);
        player.setCubeLimit();
        require(player.getInventory().getLimit() == 270, "Previous expansion counters credited");
        CubeExpandService.itemExpand(player);
        require(player.getInventory().getLimit() == 279, "Last expansion reaches client maximum");
        require(!CubeExpandService.canExpandByTicket(player, 4), "Tickets rejected at maximum");
        CubeExpandService.questExpand(player);
        require(data.getQuestExpands() == 3, "Reward does not exceed maximum");
        packets(player);
        CustomConfig.CUBE_EXPANSION_LIMIT = 100;
        require(!CubeExpandService.canExpand(player), "Config cannot exceed native cells");
        CustomConfig.UNIFIED_INVENTORY = false;
        player.setCubeLimit();
        require(player.getInventory().getLimit() == 126, "Legacy capacity unchanged");
        packets(player);
        System.out.println("PASS: 180 base, nine-slot quest/ticket rewards, previous credits, ticket levels, 279 cap, login/update agreement, legacy capacity.");
    }
}
