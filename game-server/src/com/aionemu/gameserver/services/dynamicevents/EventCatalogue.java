package com.aionemu.gameserver.services.dynamicevents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.utils.Json;

/** Loads and validates chain files. A broken file is rejected with its reason; the others still load. */
public final class EventCatalogue {

	private static final Logger log = LoggerFactory.getLogger(EventCatalogue.class);
	public static final Path FOLDER = Path.of("config/dynamic-events/chains");
	private static volatile List<EventChain> chains = List.of();

	public static List<EventChain> chains() {
		return chains;
	}

	public static void load() {
		chains = load(FOLDER);
		log.info("Dynamic events: {} chain(s) loaded from {}", chains.size(), FOLDER);
	}

	static List<EventChain> load(Path folder) {
		if (!Files.isDirectory(folder))
			return List.of();
		var result = new ArrayList<EventChain>();
		var ids = new HashSet<String>();
		try (Stream<Path> files = Files.list(folder)) {
			for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
				try {
					EventChain chain = parse(Files.readString(file, StandardCharsets.UTF_8));
					if (!ids.add(chain.id))
						throw new IllegalArgumentException("duplicate chain id " + chain.id);
					result.add(chain);
				} catch (IOException | RuntimeException e) {
					log.warn("Rejected dynamic event chain {}: {}", file.getFileName(), e.getMessage());
				}
			}
		} catch (IOException e) {
			log.warn("Dynamic event chains unavailable in {}: {}", folder, e.toString());
		}
		return List.copyOf(result);
	}

	static EventChain parse(String json) {
		EventChain chain = Json.read(json, EventChain.class);
		if (chain == null)
			throw new IllegalArgumentException("empty file");
		validate(chain);
		return chain;
	}

	static void validate(EventChain chain) {
		require(chain.id != null && chain.id.matches("[a-z0-9-]{1,40}"), "id must be 1-40 lower-case letters, digits or dashes");
		require(chain.title != null && !chain.title.isBlank() && chain.title.length() <= 60, "title must be 1-60 characters");
		require(chain.minLevel >= 1 && chain.maxLevel <= 65 && chain.minLevel <= chain.maxLevel, "level band must lie in 1-65");
		require(chain.weight >= 1 && chain.weight <= 100, "weight must be 1-100");
		require(EventChain.WARDEN.equals(chain.trigger) || EventChain.OCCUPIED.equals(chain.trigger), "trigger must be warden or occupied");
		require(!chain.occupyOnFailure || EventChain.WARDEN.equals(chain.trigger), "occupyOnFailure is for chains a Warden starts");
		if (chain.schedule != null)
			require(EventChain.WARDEN.equals(chain.trigger) && chain.schedule.everyMinutes >= 30 && chain.schedule.everyMinutes <= 1440 && 1440 % chain.schedule.everyMinutes == 0
				&& chain.schedule.atMinute >= 0 && chain.schedule.atMinute < chain.schedule.everyMinutes, "schedule: everyMinutes 30-1440 dividing a day, atMinute below it, Warden chains only");
		require(chain.stages != null && !chain.stages.isEmpty() && chain.stages.size() <= 16, "1-16 stages");
		var ids = new HashSet<String>();
		for (var s : chain.stages) {
			require(s.id != null && s.id.matches("[a-z0-9-]{1,30}") && !s.id.equals(EventChain.END) && !s.id.equals(EventChain.FAIL), "stage id " + s.id);
			require(ids.add(s.id), "duplicate stage " + s.id);
			EventChain.Type type;
			EventChain.Rating rating;
			try {
				type = s.kind();
				rating = s.monsters();
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("stage " + s.id + ": unknown type or rating");
			}
			require(s.say != null, "stage " + s.id + ": say must be an object");
			require(s.title != null && !s.title.isBlank() && s.title.length() <= 60, "stage " + s.id + ": title must be 1-60 characters");
			require(s.objective != null && !s.objective.isBlank() && s.objective.length() <= 80, "stage " + s.id + ": objective must be 1-80 characters");
			require(s.count >= 1 && s.count <= 40 && s.wave >= 1 && s.wave <= 10, "stage " + s.id + ": count 1-40, wave 1-10");
			require(s.perParticipant >= 0 && s.perParticipant <= 300, "stage " + s.id + ": perParticipant 0-300");
			require(s.seconds >= 30 && s.seconds <= 1800, "stage " + s.id + ": seconds 30-1800");
			require(type != EventChain.Type.BOSS || rating == EventChain.Rating.BOSS, "stage " + s.id + ": a BOSS stage spawns the BOSS rating");
			require(type == EventChain.Type.BOSS || rating != EventChain.Rating.BOSS, "stage " + s.id + ": only a BOSS stage spawns the boss");
			if (type == EventChain.Type.BOSS) {
				require(s.phases.size() <= 4 && s.say.phase.size() == s.phases.size(), "stage " + s.id + ": one phase line per phase, at most 4");
				int last = 100;
				for (int p : s.phases) {
					require(p > 0 && p < last, "stage " + s.id + ": phases must fall from 99 to 1");
					last = p;
				}
			} else
				require(s.phases.isEmpty() && s.say.phase.isEmpty(), "stage " + s.id + ": phases are for BOSS stages");
			for (var line : lines(s))
				narration(line, s.id);
		}
		require(ids.contains(chain.first), "first stage " + chain.first + " does not exist");
		for (var s : chain.stages)
			for (String next : List.of(s.success, s.failure))
				require(next.equals(EventChain.END) || next.equals(EventChain.FAIL) || ids.contains(next), "stage " + s.id + " leads to unknown " + next);
		reachable(chain);
	}

	static List<String> lines(EventChain.Stage s) {
		var all = new ArrayList<String>();
		for (var list : List.of(s.say.start, s.say.progress, s.say.success, s.say.failure, s.say.phase))
			all.addAll(list);
		return all;
	}

	/** "speaker|text" with a known speaker and only known placeholders. */
	static void narration(String line, String stage) {
		int bar = line == null ? -1 : line.indexOf('|');
		require(bar > 0 && EventChain.SPEAKERS.contains(line.substring(0, bar)), "stage " + stage + ": narration needs speaker|text: " + line);
		String text = line.substring(bar + 1);
		require(!text.isBlank() && text.length() <= 200, "stage " + stage + ": narration text must be 1-200 characters");
		String rest = text.replaceAll("\\{(warden|road|boss|enemy|place|count)\\}", "");
		require(rest.indexOf('{') < 0 && rest.indexOf('}') < 0, "stage " + stage + ": unknown placeholder in " + text);
	}

	/** Every stage is reachable from the first, and every stage can still reach an ending. */
	static void reachable(EventChain chain) {
		var seen = new HashSet<String>();
		var queue = new ArrayDeque<String>(List.of(chain.first));
		while (!queue.isEmpty()) {
			String id = queue.poll();
			var s = chain.stage(id);
			if (s == null || !seen.add(id))
				continue;
			queue.add(s.success);
			queue.add(s.failure);
		}
		for (var s : chain.stages)
			require(seen.contains(s.id), "stage " + s.id + " is never reached");
		var ending = new HashSet<String>(List.of(EventChain.END, EventChain.FAIL));
		boolean grew = true;
		while (grew) {
			grew = false;
			for (var s : chain.stages)
				if (!ending.contains(s.id) && (ending.contains(s.success) || ending.contains(s.failure)))
					grew = ending.add(s.id);
		}
		for (var s : chain.stages)
			require(ending.contains(s.id), "stage " + s.id + " loops without an ending");
	}

	private static void require(boolean value, String reason) {
		if (!value)
			throw new IllegalArgumentException(reason);
	}

	private EventCatalogue() {
	}
}
