package instance;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * Udas Temple: reports the finished run when Devoted Anurati dies.
 */
@InstanceID(300150000)
public class UdasTempleInstance extends GeneralInstanceHandler {

	public UdasTempleInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public void onDie(Npc npc) {
		if (npc.getNpcId() == 215793) completeRun(npc);
		super.onDie(npc);
	}
}
