package com.aionemu.gameserver.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.scripting.ScriptManager;
import com.aionemu.commons.scripting.classlistener.AggregatedClassListener;
import com.aionemu.commons.scripting.classlistener.OnClassLoadUnloadListener;
import com.aionemu.gameserver.configs.main.AIConfig;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Compile and validate separately, then publish one immutable registry. */
public final class AIRegistryReload {

	private static final Logger log = LoggerFactory.getLogger(AIRegistryReload.class);
	private static final AtomicBoolean requested = new AtomicBoolean();
	private static final ThreadLocal<Map<String, Class<? extends AbstractAI<? extends Creature>>>> staging = new ThreadLocal<>();
	private static volatile Map<String, Class<? extends AbstractAI<? extends Creature>>> active;
	private static ScriptManager activeManager;

	private AIRegistryReload() {
	}

	static Map<String, Class<? extends AbstractAI<? extends Creature>>> handlers(
		Map<String, Class<? extends AbstractAI<? extends Creature>>> baseline) {
		var snapshot = active;
		return snapshot == null ? baseline : snapshot;
	}

	static Map<String, Class<? extends AbstractAI<? extends Creature>>> validationHandlers(
		Map<String, Class<? extends AbstractAI<? extends Creature>>> baseline) {
		var pending = staging.get();
		return pending == null ? handlers(baseline) : pending;
	}

	static synchronized Class<?> register(String name, Class<? extends AbstractAI<? extends Creature>> type,
		Map<String, Class<? extends AbstractAI<? extends Creature>>> baseline) {
		var pending = staging.get();
		if (pending != null)
			return pending.putIfAbsent(name, type);
		var next = new HashMap<>(handlers(baseline));
		var previous = next.putIfAbsent(name, type);
		if (previous == null)
			active = Map.copyOf(next);
		return previous;
	}

	static synchronized void load(ScriptManager original, Map<String, Class<? extends AbstractAI<? extends Creature>>> baseline,
		Runnable validate) {
		ScriptManager replacement = new ScriptManager();
		var listener = new AggregatedClassListener();
		listener.addClassListener(new OnClassLoadUnloadListener());
		listener.addClassListener(new AIHandlerClassListener());
		replacement.setGlobalClassListener(listener);
		var next = new HashMap<String, Class<? extends AbstractAI<? extends Creature>>>();
		staging.set(next);
		try {
			replacement.load(AIConfig.HANDLER_DIRECTORY);
			validate.run();
		} catch (RuntimeException | Error failure) {
			for (var context : replacement.getScriptContexts())
				if (context.isInitialized())
					context.shutdown();
			throw failure;
		} finally {
			staging.remove();
		}
		ScriptManager previous = activeManager == null ? original : activeManager;
		active = Map.copyOf(next);
		activeManager = replacement;
		log.info("Loaded {} AI handlers (atomic registry replacement).", next.size());
		try {
			previous.shutdown();
		} catch (RuntimeException failure) {
			log.error("New AI registry active; previous script context cleanup failed", failure);
		}
	}

	/** Called after normal admin authorization; compilation never occupies a packet worker. */
	public static void request(Player admin) {
		if (!requested.compareAndSet(false, true)) {
			PacketSendUtility.sendMessage(admin, "An AI reload is already in progress.");
			return;
		}
		PacketSendUtility.sendMessage(admin, "AI reload started. Existing handlers remain available while compiling.");
		try {
			ThreadPoolManager.getInstance().executeLongRunning(() -> {
				try {
					AIEngine.getInstance().reload();
					PacketSendUtility.sendMessage(admin, "AI successfully reloaded! Respawn NPCs to use their new AI.");
				} catch (RuntimeException | Error failure) {
					log.error("AI reload failed; existing handlers retained", failure);
					PacketSendUtility.sendMessage(admin, "AI reload failed; existing handlers retained. See the server log.");
				} finally {
					requested.set(false);
				}
			});
		} catch (RuntimeException failure) {
			requested.set(false);
			throw failure;
		}
	}
}
