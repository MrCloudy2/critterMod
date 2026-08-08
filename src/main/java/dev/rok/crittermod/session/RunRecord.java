package dev.rok.crittermod.session;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A finished run, flattened to what is worth keeping.
 *
 * <p>Deliberately a plain class of plain fields rather than the live
 * {@link SafariSession}: this is what gets written to disk, so it should hold names and
 * numbers only. Species are stored by name, which means a saved run survives the roster
 * being edited — an unknown name is simply skipped when it is read back.
 *
 * <p>What is dropped is everything that only matters live: who caught what (only the
 * counts survive), what was loaded nearby, and the sparkling flags.
 */
public class RunRecord {

	public long started;
	public long ended;
	public String self;
	/** Species name -> times you caught it. */
	public Map<String, Integer> own = new LinkedHashMap<>();
	/** Species name -> times a partymate caught it, summed across the party. */
	public Map<String, Integer> shared = new LinkedHashMap<>();
	/** Species name -> capsules thrown at it. */
	public Map<String, Integer> attempts = new LinkedHashMap<>();
	public int ownShards;
	public int totalShards;

	/** Gson needs this; nothing else should use it. */
	public RunRecord() {
	}

	public static RunRecord of(SafariSession session) {
		RunRecord record = new RunRecord();
		record.started = session.startedAtMillis();
		record.ended = session.startedAtMillis() + session.durationMillis();
		record.self = session.selfName();
		session.ownCatchCounts().forEach((critter, count) -> record.own.put(critter.name(), count));
		session.sharedCatchCounts().forEach((critter, count) -> record.shared.put(critter.name(), count));
		session.attemptCounts().forEach((critter, count) -> record.attempts.put(critter.name(), count));
		record.ownShards = session.ownShards();
		record.totalShards = session.totalShards();
		return record;
	}

	public long durationMillis() {
		return Math.max(0, ended - started);
	}

	/** How many times anyone in the party caught {@code critter} in this run. */
	public int caught(Critter critter) {
		return own.getOrDefault(critter.name(), 0) + shared.getOrDefault(critter.name(), 0);
	}

	/** Distinct species the party caught. */
	public int partyUnique() {
		return (int) Critters.all().stream().filter(c -> caught(c) > 0).count();
	}

	/** Distinct species you caught yourself. */
	public int ownUnique() {
		return (int) Critters.all().stream()
			.filter(c -> own.getOrDefault(c.name(), 0) > 0).count();
	}

	/** Every catch in the run, duplicates included. */
	public int partyTotal() {
		return sum(own) + sum(shared);
	}

	public int ownTotal() {
		return sum(own);
	}

	private static int sum(Map<String, Integer> counts) {
		return counts == null ? 0 : counts.values().stream().mapToInt(Integer::intValue).sum();
	}
}
