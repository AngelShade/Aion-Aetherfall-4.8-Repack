package instance;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * Indratu Fortress: reports the finished run when Brigadier Indratu dies.
 */
@InstanceID(310090000)
public class IndratuFortressInstance extends GeneralInstanceHandler {

	public IndratuFortressInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public void onDie(Npc npc) {
		if (npc.getNpcId() == 214159) completeRun(npc);
		super.onDie(npc);
	}
}
