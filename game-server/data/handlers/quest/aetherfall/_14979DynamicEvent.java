package quest.aetherfall;

import com.aionemu.gameserver.questEngine.handlers.AbstractQuestHandler;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.services.dynamicevents.EventJournal;

/** The journal and tracker entry of a world event the player takes part in (config/dynamic-events/README.md). EventJournal owns it; this only clears a stale one at login. */
public class _14979DynamicEvent extends AbstractQuestHandler {

	public _14979DynamicEvent() {
		super(EventJournal.QUEST);
	}

	@Override
	public void register() {
		qe.registerOnEnterWorld(questId);
	}

	@Override
	public boolean onEnterWorldEvent(QuestEnv env) {
		EventJournal.entered(env.getPlayer());
		return false;
	}
}
