package instance;

import java.util.concurrent.Future;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.TheobomosLabHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.RespawnService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldPosition;

/**
 * @author xTz, Ritsu, Tibald
 */
@InstanceID(310110000)
public class TheobomosLabInstance extends GeneralInstanceHandler implements TheobomosLabHandler {

	private boolean isInstanceDestroyed;
	private boolean isDead1 = false;
	private boolean isDead2 = false;
	private boolean silikorUnsealed;
	private Future<?> unsealTask;

	public TheobomosLabInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public synchronized void onDie(Npc npc) {
		if (isInstanceDestroyed)
			return;

		if (npc.getMaster() instanceof Player)
			return;

		switch (npc.getNpcId()) {
			case 214669: // Triroan
				spawn(730178, 571.15f, 490.6607f, 196.7324f, (byte) 60);
				break;
			case 280971:
			case 280972:
				if (guardDie(npc))
					removeBuff();
		}
	}

	@Override
	public synchronized void onInstanceDestroy() {
		isInstanceDestroyed = true;
		if (unsealTask != null)
			unsealTask.cancel(false);
	}

	@Override
	public synchronized boolean isSilikorSealed() {
		return !silikorUnsealed;
	}


	private boolean guardDie(Npc npc) {
		WorldPosition p = npc.getPosition();
		int npcId = npc.getNpcId();
		// The native controller schedules respawn before calling the instance's onDie.
		RespawnService.cancelRespawn(npc);
		if (silikorUnsealed)
			return false;
		Npc orb = getNpc(280973);
		if (orb != null && PositionUtil.getDistance(orb, npc) <= 7) {
			switch (npcId) {
				case 280971 -> isDead1 = true;
				case 280972 -> isDead2 = true;
			}
			return true;
		} else {
			npc.getController().delete();
			spawn(npcId, p.getX(), p.getY(), p.getZ(), (byte) 41);
			return false;
		}
	}

	private void removeBuff() {
		if (!isDead1 || !isDead2 || silikorUnsealed)
			return;
		silikorUnsealed = true;
		unsealTask = ThreadPoolManager.getInstance().schedule(() -> {
			synchronized (TheobomosLabInstance.this) {
				if (!isInstanceDestroyed) {
					Npc silikor = getNpc(214668);
					if (silikor != null)
						silikor.getEffectController().removeEffect(18481);
					deleteAliveNpcs(280973);
				}
			}
		}, 1000);
	}

	@Override
	public void onInstanceCreate() {
		// The 4.8 client defines one shared chest position with 6000/3000/1000 weights.
		int chestRoll = Rnd.get(1, 10000);
		spawn(chestRoll <= 6000 ? 700826 : chestRoll <= 9000 ? 700827 : 700828,
			455.78845f, 774.0474f, 157.89963f, (byte) 0);
		if (Rnd.nextBoolean()) {
			switch (Rnd.get(1, 3)) {
				case 1 -> spawn(798223, 256.26215f, 512.9742f, 187.79453f, (byte) 0);
				case 2 -> spawn(798223, 360.16098f, 526.9446f, 186.20251f, (byte) 105);
				case 3 -> spawn(798223, 476.77145f, 541.5697f, 187.79453f, (byte) 90);
			}
		}
	}
}
