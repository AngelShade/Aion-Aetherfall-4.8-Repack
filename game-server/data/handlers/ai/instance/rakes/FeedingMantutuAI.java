package ai.instance.rakes;

import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.WorldMapInstance;

import ai.ShifterAI;

/**
 * @author xTz
 */
@AIName("feeding_mantutu")
public class FeedingMantutuAI extends ShifterAI {

	public FeedingMantutuAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleDialogStart(Player player) {
		WorldMapInstance instance = getPosition().getWorldMapInstance();
		Npc boss=instance.getNpc(219033);
  int required=getNpcId()==701386?20489:20490;
  if (boss!=null && !boss.isDead() && boss.getEffectController().hasAbnormalEffect(required)
   && instance.getNpc(281128) == null && instance.getNpc(281129) == null) {
			super.handleDialogStart(player);
		}
	}

	@Override
	protected void handleUseItemFinish(Player player) {
		Npc boss = getPosition().getWorldMapInstance().getNpc(219033);
		if (boss != null && boss.isSpawned() && !boss.isDead()) {
   synchronized(boss){
   int required=getNpcId()==701386?20489:20490;
   if(!boss.getEffectController().hasAbnormalEffect(required) || !getOwner().isSpawned()
    || getPosition().getWorldMapInstance().getNpc(281128)!=null || getPosition().getWorldMapInstance().getNpc(281129)!=null)return;
   super.handleUseItemFinish(player);
			Npc npc = null;
			switch (getNpcId()) {
				case 701387: // water supply
					npc = (Npc) spawn(281129, 712.042f, 490.5559f, 939.7027f, (byte) 0);
					break;
				case 701386: // feed supply
					npc = (Npc) spawn(281128, 714.62634f, 504.4552f, 939.60675f, (byte) 0);
					break;
			}
			boss.getAi().onCustomEvent(1, npc);
			AIActions.deleteOwner(this);
   }
		}
	}

}
