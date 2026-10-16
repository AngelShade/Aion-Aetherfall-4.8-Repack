package com.aionemu.gameserver.instance.handlers;

/** Shared seal state without a dependency on the separately loaded instance script. */
public interface TheobomosLabHandler extends InstanceHandler {

    boolean isSilikorSealed();
}
