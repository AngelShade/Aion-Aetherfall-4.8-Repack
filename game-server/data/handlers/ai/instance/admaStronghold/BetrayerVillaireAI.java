package ai.instance.admaStronghold;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.instance.handlers.AdmaStrongholdHandler;
import ai.AggressiveNpcAI;

/** Native fake/real actors; defeat reveals the required real form without inventing an HP threshold. */
@AIName("adma_betrayer")
public class BetrayerVillaireAI extends AggressiveNpcAI {
    private boolean revealed;
    public BetrayerVillaireAI(Npc owner) { super(owner); }
    @Override protected synchronized void handleDied() {
        if (getNpcId() == 214700 && !revealed) {
            revealed = true;
            var attackers = getAggroList().stream().filter(info -> info.getHate() > 0)
                .filter(info -> !info.getAttacker().isDead() && info.getAttacker().isSpawned()).toList();
            Npc real = getPosition().getWorldMapInstance().getInstanceHandler() instanceof AdmaStrongholdHandler adma
                ? adma.revealBetrayer(getOwner()) : null;
            if (real != null) for (var info : attackers) real.getAggroList().addHate(info.getAttacker(),info.getHate());
        }
        super.handleDied();
    }
    @Override protected synchronized void handleBackHome() {
        if (isDead() || !getOwner().isSpawned() || getLifeStats().isAboutToDie()) return;
        if (getNpcId() == 214701 && getPosition().getWorldMapInstance().getInstanceHandler() instanceof AdmaStrongholdHandler adma)
            adma.resetBetrayer(getOwner());
        else super.handleBackHome();
    }
    @Override public boolean ask(AIQuestion question) {
        if (getNpcId() == 214700 && (question == AIQuestion.REWARD_AP_XP_DP_LOOT || question == AIQuestion.REWARD_LOOT
            || question == AIQuestion.ALLOW_DECAY)) return false;
        return super.ask(question);
    }
}
