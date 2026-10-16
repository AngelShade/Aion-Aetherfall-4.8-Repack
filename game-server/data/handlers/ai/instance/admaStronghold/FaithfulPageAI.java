package ai.instance.admaStronghold;

import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import ai.AggressiveBossSummonNpcAI;

/** The native low-health explosion finishes the summoned page after its damage is committed. */
@AIName("adma_faithful_page")
public class FaithfulPageAI extends AggressiveBossSummonNpcAI {
    public FaithfulPageAI(Npc owner) { super(owner); }
    @Override public void onEndUseSkill(SkillTemplate skill, int level) {
        super.onEndUseSkill(skill, level);
        if (skill.getSkillId() == 16991 && !isDead()) AIActions.die(this);
    }
}
