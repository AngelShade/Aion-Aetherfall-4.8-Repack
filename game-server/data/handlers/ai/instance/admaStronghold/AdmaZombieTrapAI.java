package ai.instance.admaStronghold;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.event.AIEventType;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureVisualState;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * Native stationary ZombieTrap sensor and ZombieTrapSum actor.
 * A single ambush per sensor is a bounded reconstruction; the original wave count is not available.
 */
@AIName("adma_zombie_trap")
public class AdmaZombieTrapAI extends NpcAI {
    private boolean spent;
    private boolean removed;
    private Npc zombie;

    public AdmaZombieTrapAI(Npc owner) { super(owner); }

    @Override protected void handleCreatureSee(Creature creature) { activate(creature); }
    @Override protected void handleCreatureMoved(Creature creature) { activate(creature); }

    private synchronized void activate(Creature creature) {
        // No polling or recurring tasks. Both real clients and headless companions use normal awareness events.
        if (spent || removed || getOwner().getWorldId() != 320130000 || !getOwner().isSpawned() || isDead()
            || !(creature.getMaster() instanceof Player player) || !creature.isSpawned() || creature.isDead()
            || !player.isSpawned() || player.isDead() || creature.getWorldMapInstance() != getOwner().getWorldMapInstance()
            || creature.isInVisualState(CreatureVisualState.BLINKING) || !getOwner().getPosition().isMapRegionActive()
            || getOwner().getEffectController().isAbnormalSet(AbnormalState.SANCTUARY)
            || !getOwner().isEnemy(creature) || !getOwner().getKnownList().sees(creature) || !getOwner().canSee(creature)
            || !PositionUtil.isInRange(getOwner(),creature,getOwner().getObjectTemplate().getAggroRange())
            || !GeoService.getInstance().canSee(getOwner(),creature)) return;
        spent = true; // Claim before spawning: nested visibility callbacks must not create another ambush.
        // Keep the claim if a spawn callback throws after partially registering an actor.
        zombie = (Npc) spawn(281028,getOwner().getX(),getOwner().getY(),getOwner().getZ(),getOwner().getHeading());
        if (zombie == null) { spent = false; return; }
        if (creature.isSpawned() && !creature.isDead() && creature.getWorldMapInstance() == zombie.getWorldMapInstance()
            && zombie.isEnemy(creature)) zombie.getAggroList().addHate(creature,1);
    }

    private synchronized void removeEncounter() {
        removed = true;
        if (zombie != null && zombie.getCreatorId() == getObjectId()) zombie.getController().deleteIfAliveOrCancelRespawn();
        zombie = null;
    }

    @Override protected void handleDied() { removeEncounter(); super.handleDied(); }
    @Override protected void handleDespawned() { removeEncounter(); super.handleDespawned(); }
    @Override protected boolean canHandleEvent(AIEventType event) {
        return event == AIEventType.CREATURE_MOVED || super.canHandleEvent(event);
    }
    @Override public boolean isMoveSupported() { return false; }
    @Override public boolean ask(AIQuestion question) {
        if (question == AIQuestion.REWARD_AP_XP_DP_LOOT || question == AIQuestion.REWARD_AP || question == AIQuestion.REWARD_LOOT)
            return false;
        return super.ask(question);
    }
}
