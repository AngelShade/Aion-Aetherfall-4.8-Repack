package ai.instance.beshmundirTemple;

import com.aionemu.gameserver.ai.AttackIntention;
import com.aionemu.gameserver.ai.event.AIEventType;
import com.aionemu.gameserver.ai.manager.SkillAttackManager;
import com.aionemu.gameserver.configs.main.AIConfig;
import com.aionemu.gameserver.controllers.attack.AggroTarget;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.skill.NpcSkillEntry;
import com.aionemu.gameserver.model.skill.NpcSkillTemplateEntry;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.model.templates.npcskill.QueuedNpcSkillTemplate;

import ai.AggressiveNpcAI;

/** Shared native cast admission for the Macunbello encounter, without automatic random skills. */
public abstract class MacunbelloCombatAI extends AggressiveNpcAI {

	protected final boolean enabled = AIConfig.MACUNBELLO_ENABLE;

	protected MacunbelloCombatAI(Npc owner) {
		super(owner);
	}

	@Override
	public AttackIntention chooseAttackIntention() {
		if (!enabled)
			return super.chooseAttackIntention();
		if (!(getTarget() instanceof Creature target) || !getAggroList().isHating(target)) {
			Creature mostHated = getAggroList().getTarget(AggroTarget.MOST_HATED);
			if (mostHated == null)
				return AttackIntention.FINISH_ATTACK;
			onCreatureEvent(AIEventType.TARGET_CHANGED, mostHated);
		}
		NpcSkillEntry next;
		while ((next = getOwner().getNextQueuedSkill()) != null && next.getTargetOverride() != null
			&& !validTarget(next.getTargetOverride()))
			getOwner().removeNextQueuedSkill(next);
		NpcSkillEntry skill = SkillAttackManager.chooseNextSkill(this, true);
		if (skill == null)
			return AttackIntention.SIMPLE_ATTACK;
		getOwner().getGameStats().setLastSkill(skill);
		getOwner().removeNextQueuedSkill(skill);
		return AttackIntention.SKILL_ATTACK;
	}

	protected final boolean validTarget(Creature target) {
		return target != null && target.isSpawned() && !target.isDead() && !target.getLifeStats().isAboutToDie()
			&& target.getWorldMapInstance() == getOwner().getWorldMapInstance();
	}

	protected final void queue(int skillId, NpcSkillTargetAttribute target) {
		queue(skillId, target, null, null);
	}

	protected final void queue(int skillId, Creature target, Runnable onCompleted) {
		queue(skillId, NpcSkillTargetAttribute.NONE, target, onCompleted);
	}

	private void queue(int skillId, NpcSkillTargetAttribute targetType, Creature target, Runnable onCompleted) {
		// A blocked cast must not accumulate an unbounded timer backlog.
		if (getOwner().hasQueuedSkill(entry -> entry.getSkillId() == skillId))
			return;
		int level = 1; // The pinned donor's QueueAtDataLevel fallback, verified for 19049/19051.
		for (int i = 0; i < getSkillList().size(); i++) {
			NpcSkillEntry entry = getSkillList().getSkillOnPosition(i);
			if (entry.getSkillId() == skillId) {
				level = entry.getSkillLevel();
				break;
			}
		}
		getOwner().queueSkill(new NpcSkillTemplateEntry(new QueuedNpcSkillTemplate(skillId, level, 0, targetType)) {
			@Override
			public Creature getTargetOverride() {
				return target;
			}

			@Override
			public void fireOnEndCastEvents(Npc npc) {
				super.fireOnEndCastEvents(npc);
				if (onCompleted != null && validTarget(target) && !isDead())
					onCompleted.run();
			}
		});
	}
}
