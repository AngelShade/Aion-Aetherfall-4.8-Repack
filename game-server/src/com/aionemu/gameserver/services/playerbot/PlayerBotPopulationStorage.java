package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.Map;

/** Population repositories, independent of World/session/character initialization. */
interface PlayerBotPopulationStorage {
 Map<Integer,PlayerBotWorldRoster.Resident> residents() throws IOException;
 void residents(Map<Integer,PlayerBotWorldRoster.Resident> next) throws IOException;
 Map<Integer,PlayerBotWorldPopulation.Pool> pools(boolean server) throws IOException;
 void pool(boolean server,PlayerBotWorldPopulation.Pool next) throws IOException;
 PlayerBotPopulationSchedule.State schedule() throws IOException;
 /** Null while cold or invalidated; decisions leave reload to maintenance. */
 default PlayerBotPopulationSchedule.State cachedSchedule(){return null;}
 void schedule(PlayerBotPopulationSchedule.State next) throws IOException;
 void event(int id,PlayerBotPopulationSchedule.Events next) throws IOException;
}
