package com.aionemu.gameserver.taskmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author lord_rex and MrPoke based on l2j-free engines. This can be used for periodic calls.
 */
public abstract class AbstractPeriodicTaskManager {

	protected static final Logger log = LoggerFactory.getLogger(AbstractPeriodicTaskManager.class);
	private static final long SLOW_RUN_MILLIS = 5000;

	public AbstractPeriodicTaskManager(int period) {
		log.info("{} initialized", getClass().getSimpleName());
		ThreadPoolManager.getInstance().scheduleAtFixedRate(this::timedRun, Rnd.get(500, 550), period);
	}

	private void timedRun() {
		long begin = System.nanoTime();
		try {
			run();
		} finally {
			long millis = (System.nanoTime() - begin) / 1_000_000L;
			if (millis >= SLOW_RUN_MILLIS) // the thread pool's warning only names the shared lambda
				log.warn("{} took {} ms", getClass().getSimpleName(), millis);
		}
	}

	protected abstract void run();
}
