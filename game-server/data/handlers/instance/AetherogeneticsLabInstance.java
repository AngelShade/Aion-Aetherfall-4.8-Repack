package instance;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * Aetherogenetics Lab: reports the finished run when RM-78c dies.
 */
@InstanceID(310050000)
public class AetherogeneticsLabInstance extends GeneralInstanceHandler {

	public AetherogeneticsLabInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public void onDie(Npc npc) {
		if (npc.getNpcId() == 212211) completeRun(npc);
		super.onDie(npc);
	}
}
