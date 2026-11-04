package instance;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * Nochsana Training Camp: reports the finished run when the Nochsana General dies.
 */
@InstanceID(300030000)
public class NochsanaTrainingCampInstance extends GeneralInstanceHandler {

	public NochsanaTrainingCampInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public void onDie(Npc npc) {
		if (npc.getNpcId() == 256693) completeRun(npc);
		super.onDie(npc);
	}
}
