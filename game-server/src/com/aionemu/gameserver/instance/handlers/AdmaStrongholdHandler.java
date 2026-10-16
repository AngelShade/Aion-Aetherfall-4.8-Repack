package com.aionemu.gameserver.instance.handlers;

import com.aionemu.gameserver.model.gameobjects.Npc;

/** Shared encounter contract: AI and instance scripts have separate class loaders. */
public interface AdmaStrongholdHandler extends InstanceHandler {

    void resetCoffins(Npc boss);

    Npc revealBetrayer(Npc boy);

    void resetBetrayer(Npc real);
}
