package instance;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * Azoturan Fortress: reports the finished run when Icaronix the Betrayer dies (he changes form at half health).
 */
@InstanceID(310100000)
public class AzoturanFortressInstance extends GeneralInstanceHandler {

	public AzoturanFortressInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public void onDie(Npc npc) {
		if (npc.getNpcId() == 214599) completeRun(npc);
		super.onDie(npc);
	}
}
