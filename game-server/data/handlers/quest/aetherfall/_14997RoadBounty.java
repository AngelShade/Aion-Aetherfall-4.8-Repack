package quest.aetherfall;

import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.services.playerbot.PlayerBotRoadBounty;

/** Native daily journal and tracker. Rewarded kill admission is shared by solo/group kills. */
public class _14997RoadBounty extends AbstractQuestHandler {

	public _14997RoadBounty() {
		super(14997);
	}

	@Override
	public void register() {
		qe.registerOnEnterWorld(questId);
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		PlayerBotRoadBounty.entered(env.getPlayer());
		return false;
	}
}
