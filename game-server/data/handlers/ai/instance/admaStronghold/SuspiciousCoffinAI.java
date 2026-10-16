package ai.instance.admaStronghold;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.model.gameobjects.Npc;
import ai.GeneralNpcAI;

/** Destructible native NoAction_Coffin actors, not walking or melee enemies. */
@AIName("adma_coffin")
public class SuspiciousCoffinAI extends GeneralNpcAI {
    public SuspiciousCoffinAI(Npc owner) { super(owner); }
    @Override public boolean canThink() { return false; }
}
