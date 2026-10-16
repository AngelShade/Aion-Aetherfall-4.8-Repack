package ai.instance.admaStronghold;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Future;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.controllers.attack.AggroTarget;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import ai.AggressiveNpcAI;
import com.aionemu.gameserver.instance.handlers.AdmaStrongholdHandler;

/** Native coffin actors and pages; historical 30-second waves, not a claimed retail 4.8 timer trace. */
@AIName("lord_lannok")
public class LordLannokAI extends AggressiveNpcAI {
    private Future<?> waveTask;
    private int generation;

    public LordLannokAI(Npc owner) { super(owner); }

    @Override
    protected synchronized void handleAttack(Creature creature) {
        super.handleAttack(creature);
        if (getLifeStats().getHpPercentage() <= 75 && waveTask == null) summonWave(generation);
    }

    private synchronized void summonWave(int epoch) {
        if (epoch != generation) return;
        waveTask = null;
        if (isDead() || !getOwner().isSpawned() || !isInState(AIState.FIGHT)) return;
        var target = getAggroList().getTarget(AggroTarget.RANDOM);
        if (target == null) return;
        var coffins = new ArrayList<>(getPosition().getWorldMapInstance().getNpcs(280942, 280950, 281055, 281056, 281057, 281058));
        coffins.removeIf(npc -> npc.isDead() || !npc.isSpawned());
        Collections.shuffle(coffins);
        // Three native coffin sources per wave; surviving coffins remain the source after players destroy others.
        for (var coffin : coffins.subList(0, Math.min(3, coffins.size()))) {
            boolean caster = getLifeStats().getHpPercentage() <= 50
                && (coffin.getNpcId() == 280950 || coffin.getNpcId() == 281056 || coffin.getNpcId() == 281058);
            Npc page = (Npc) spawn(caster ? 280949 : 280933, coffin.getX(), coffin.getY(), coffin.getZ(), coffin.getHeading());
            page.getAggroList().addHate(target, 1);
        }
        waveTask = ThreadPoolManager.getInstance().schedule(() -> summonWave(epoch), 30000);
    }

    private synchronized void clearEncounter() {
        generation++;
        if (waveTask != null) { waveTask.cancel(false); waveTask = null; }
        getPosition().getWorldMapInstance().getNpcs(280933, 280949).stream()
            .filter(npc -> npc.getCreatorId() == getObjectId())
            .forEach(npc -> npc.getController().deleteIfAliveOrCancelRespawn());
    }

    @Override protected synchronized void handleBackHome() {
        if (isDead() || !getOwner().isSpawned() || getLifeStats().isAboutToDie()) return;
        clearEncounter();
        if (getPosition().getWorldMapInstance().getInstanceHandler() instanceof AdmaStrongholdHandler adma) adma.resetCoffins(getOwner());
        super.handleBackHome();
    }
    @Override protected synchronized void handleDied() { clearEncounter(); super.handleDied(); }
    @Override protected synchronized void handleDespawned() { clearEncounter(); super.handleDespawned(); }
}
