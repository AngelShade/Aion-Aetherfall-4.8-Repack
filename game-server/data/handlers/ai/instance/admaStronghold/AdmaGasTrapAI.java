package ai.instance.admaStronghold;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.event.AIEventType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Static native six-metre gas emitters. No creator requirement, walking, melee attack or periodic polling. */
@AIName("adma_gas_trap")
public class AdmaGasTrapAI extends NpcAI {
    public AdmaGasTrapAI(Npc owner) { super(owner); }
    @Override protected void handleCreatureSee(Creature creature) { super.handleCreatureSee(creature); activate(creature); }
    @Override protected void handleCreatureMoved(Creature creature) { super.handleCreatureMoved(creature); activate(creature); }
    private synchronized void activate(Creature creature) {
        int skill = getNpcId() == 281026 ? 18106 : 18115;
        if (!(creature.getMaster() instanceof Player) || creature.isDead() || isDead() || !getOwner().isSpawned()
            || getOwner().isCasting() || !getOwner().isEnemy(creature) || creature.getEffectController().hasAbnormalEffect(skill)
            || !PositionUtil.isInRange(getOwner(), creature, 6) || !GeoService.getInstance().canSee(getOwner(),creature)) return;
        SkillEngine.getInstance().getSkill(getOwner(),skill,41,getOwner()).useSkill();
    }
    @Override protected boolean canHandleEvent(AIEventType event) {
        return event == AIEventType.CREATURE_MOVED || super.canHandleEvent(event);
    }
    @Override public boolean isMoveSupported() { return false; }
}
