package quest.aetherfall;

import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.services.playerbot.PlayerBotHelpObjectives;

/** The journal and tracker entry of a second/third accepted bot help request (help slots, owner 8 Nov). PlayerBotHelpObjectives owns it; this only clears a stale one. */
public class _14999HelpRequest extends AbstractQuestHandler {

	public _14999HelpRequest() {
		super(14999);
	}

	@Override
	public void register() {
		qe.registerOnEnterWorld(questId);
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		PlayerBotHelpObjectives.entered(env.getPlayer());
		return false;
	}
}
