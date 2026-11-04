package instance;

import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.world.WorldMapInstance;

/**
 * Alquimia Research Center: reports the finished run when High Priest Esras dies.
 */
@InstanceID(320110000)
public class AlquimiaResearchCenterInstance extends GeneralInstanceHandler {

	public AlquimiaResearchCenterInstance(WorldMapInstance instance) {
		super(instance);
	}

	@Override
	public void onDie(Npc npc) {
		if (npc.getNpcId() == 214028) completeRun(npc);
		super.onDie(npc);
	}
}
