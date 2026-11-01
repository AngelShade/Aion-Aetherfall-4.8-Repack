package quest.aetherfall;

import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.services.playerbot.PlayerBotHelpObjectives;

/** The journal and tracker entry of an accepted bot help request. PlayerBotHelpObjectives owns it; this only clears a stale one. */
public class _14996HelpRequest extends AbstractQuestHandler {

	public _14996HelpRequest() {
		super(PlayerBotHelpObjectives.HELP_QUEST);
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
