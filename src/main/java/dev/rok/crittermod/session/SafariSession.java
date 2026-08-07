package dev.rok.crittermod.session;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.data.Critters;
import dev.rok.crittermod.data.SafariBiome;
import dev.rok.crittermod.parse.CritterEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Tally for one Critter Safari run, from entry until the player leaves.
 *
 * <p>Everything is derived from a single table of "who caught what, how many
 * times". Your own catches come from {@code CAPTURE!} lines; partymates' catches
 * come from the {@code LOOT SHARE!} line that names them. Because loot share
 * identifies the catcher, per-player and per-biome progress fall out for free —
 * which is what makes the "four players, one biome each" split checkable.
 *
 * <p>Not thread-safe; all mutation happens on the client thread.
 */
public final class SafariSession {

	/** Name used for the local player in per-player views. */
	private final String selfName;
	private final long startedAtMillis;

	private final Map<Critter, Integer> ownCatches = new LinkedHashMap<>();
	private final Map<Critter, Integer> attempts = new LinkedHashMap<>();
	private final Map<Critter, Integer> failures = new LinkedHashMap<>();
	/** critter -> partymate name -> how many times they caught it. */
	private final Map<Critter, Map<String, Integer>> sharedCatches = new LinkedHashMap<>();
	private final Set<Critter> sparklings = new LinkedHashSet<>();

	private int ownShards;
	private int sharedShards;
	private long lastEventMillis;

	public SafariSession(String selfName, long startedAtMillis) {
		this.selfName = selfName == null ? "You" : selfName;
		this.startedAtMillis = startedAtMillis;
		this.lastEventMillis = startedAtMillis;
	}

	public void record(CritterEvent event, long atMillis) {
		lastEventMillis = atMillis;
		Critter critter = event.critter();
		if (critter == null) return;

		switch (event.type()) {
			case OWN_CATCH -> {
				ownCatches.merge(critter, 1, Integer::sum);
				ownShards += event.shards();
			}
			case SHARED_CATCH -> {
				sharedCatches.computeIfAbsent(critter, c -> new TreeMap<>())
					.merge(event.catcher(), 1, Integer::sum);
				sharedShards += event.shards();
			}
			case ATTEMPT -> attempts.merge(critter, 1, Integer::sum);
			case FAILED -> failures.merge(critter, 1, Integer::sum);
			case ENTERED_SAFARI -> {
				return;
			}
		}

		if (event.sparkling()) sparklings.add(critter);
	}

	// --- your progress -------------------------------------------------------

	public boolean caughtByYou(Critter critter) {
		return ownCatches.getOrDefault(critter, 0) > 0;
	}

	/** Distinct species you personally caught, out of {@link Critters#total()}. */
	public int ownUnique() {
		return ownCatches.size();
	}

	public int ownUnique(SafariBiome biome) {
		return (int) ownCatches.keySet().stream().filter(c -> c.biome() == biome).count();
	}

	/** Every catch you made, duplicates included. */
	public int ownTotal() {
		return ownCatches.values().stream().mapToInt(Integer::intValue).sum();
	}

	public int ownTotal(SafariBiome biome) {
		return ownCatches.entrySet().stream()
			.filter(e -> e.getKey().biome() == biome)
			.mapToInt(Map.Entry::getValue).sum();
	}

	// --- party progress (yours + loot share) ---------------------------------

	public boolean caughtByParty(Critter critter) {
		return caughtByYou(critter) || sharedCatches.containsKey(critter);
	}

	/** Distinct species caught by anyone in the party, out of {@link Critters#total()}. */
	public int partyUnique() {
		return (int) Critters.all().stream().filter(this::caughtByParty).count();
	}

	public int partyUnique(SafariBiome biome) {
		return (int) Critters.inBiome(biome).stream().filter(this::caughtByParty).count();
	}

	/** Every catch by anyone in the party, duplicates included. */
	public int partyTotal() {
		return ownTotal() + sharedTotal();
	}

	public int partyTotal(SafariBiome biome) {
		return ownTotal(biome) + sharedCatches.entrySet().stream()
			.filter(e -> e.getKey().biome() == biome)
			.flatMap(e -> e.getValue().values().stream())
			.mapToInt(Integer::intValue).sum();
	}

	private int sharedTotal() {
		return sharedCatches.values().stream()
			.flatMap(m -> m.values().stream())
			.mapToInt(Integer::intValue).sum();
	}

	/** How many times {@code critter} was caught this run by anyone in the party. */
	public int partyCatches(Critter critter) {
		int shared = sharedCatches.getOrDefault(critter, Map.of()).values().stream()
			.mapToInt(Integer::intValue).sum();
		return ownCatches.getOrDefault(critter, 0) + shared;
	}

	/** Who caught {@code critter} this run, local player included, in catch order. */
	public List<String> catchersOf(Critter critter) {
		List<String> names = new ArrayList<>();
		if (caughtByYou(critter)) names.add(selfName);
		names.addAll(sharedCatches.getOrDefault(critter, Map.of()).keySet());
		return names;
	}

	/** True once every species in {@code biome} has been caught by someone. */
	public boolean biomeComplete(SafariBiome biome) {
		return partyUnique(biome) == Critters.totalIn(biome);
	}

	/** True once all 37 species have been caught by someone this run. */
	public boolean dexComplete() {
		return partyUnique() == Critters.total();
	}

	/** Species in {@code biome} that nobody has caught yet this run. */
	public List<Critter> missing(SafariBiome biome) {
		return Critters.inBiome(biome).stream().filter(c -> !caughtByParty(c)).toList();
	}

	// --- per-player breakdown ------------------------------------------------

	/**
	 * Unique-species count per player per biome, with the local player included
	 * under their own name. This is the "who is covering which biome" view.
	 */
	public Map<String, Map<SafariBiome, Integer>> uniquePerPlayer() {
		Map<String, Map<SafariBiome, Integer>> result = new LinkedHashMap<>();

		Map<SafariBiome, Integer> mine = new EnumMap<>(SafariBiome.class);
		for (Critter critter : ownCatches.keySet()) {
			mine.merge(critter.biome(), 1, Integer::sum);
		}
		if (!mine.isEmpty()) result.put(selfName, mine);

		Map<String, Map<SafariBiome, Integer>> others = new TreeMap<>();
		for (Map.Entry<Critter, Map<String, Integer>> entry : sharedCatches.entrySet()) {
			SafariBiome biome = entry.getKey().biome();
			for (String player : entry.getValue().keySet()) {
				others.computeIfAbsent(player, p -> new EnumMap<>(SafariBiome.class))
					.merge(biome, 1, Integer::sum);
			}
		}
		result.putAll(others);
		return result;
	}

	/** Every player seen this run, local player first. */
	public List<String> players() {
		return new ArrayList<>(uniquePerPlayer().keySet());
	}

	// --- misc ----------------------------------------------------------------

	public int attempts(Critter critter) {
		return attempts.getOrDefault(critter, 0);
	}

	public int failures(Critter critter) {
		return failures.getOrDefault(critter, 0);
	}

	public int totalAttempts() {
		return attempts.values().stream().mapToInt(Integer::intValue).sum();
	}

	public int totalFailures() {
		return failures.values().stream().mapToInt(Integer::intValue).sum();
	}

	public Set<Critter> sparklings() {
		return sparklings;
	}

	public int ownShards() {
		return ownShards;
	}

	public int totalShards() {
		return ownShards + sharedShards;
	}

	public String selfName() {
		return selfName;
	}

	public long startedAtMillis() {
		return startedAtMillis;
	}

	public long durationMillis() {
		return Math.max(0, lastEventMillis - startedAtMillis);
	}

	/** True when nothing has been recorded yet — used to suppress an empty HUD. */
	public boolean isEmpty() {
		return ownCatches.isEmpty() && sharedCatches.isEmpty() && attempts.isEmpty();
	}
}
