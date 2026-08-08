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
	/**
	 * How many of each species are loaded right now, replaced wholesale each scan.
	 *
	 * <p>Deliberately not cumulative. Counting distinct entity ids over time measures
	 * how many entity instances have been observed, not how many exist: a critter that
	 * escapes a capsule comes back as a new entity, so the total climbs forever.
	 */
	private final Map<Critter, Integer> nearbyCounts = new LinkedHashMap<>();

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

	/** True once anyone in the party has caught {@code critter} at least once. */
	public boolean caughtByParty(Critter critter) {
		return caughtByYou(critter) || sharedCatches.containsKey(critter);
	}

	/** Replaces the live nearby counts with a fresh scan of what is loaded. */
	public void setNearby(Map<Critter, Integer> counts) {
		nearbyCounts.clear();
		nearbyCounts.putAll(counts);
	}

	/** How many of {@code critter} are loaded near the player right now. */
	public int nearby(Critter critter) {
		return nearbyCounts.getOrDefault(critter, 0);
	}

	/**
	 * How many of {@code critter} the run is considered to hold.
	 *
	 * <p>Only a fixed quota can answer this. The client cannot see the whole map, and a
	 * partymate catching something out of render distance is never observed at all, so
	 * nothing counted locally is a valid target — it would be wrong in exactly the
	 * four-player runs this mod exists for.
	 */
	public int required(Critter critter) {
		if (TrackingMode.uniqueOnly()) return 1;
		return critter.hasQuota() ? critter.spawnQuota() : 1;
	}

	/**
	 * True once the run is finished with {@code critter}, either by catching enough or
	 * because the run can no longer produce it. Treating the impossible as settled is
	 * what lets a biome read as complete instead of stalling forever on a species that
	 * is never coming.
	 */
	public boolean isComplete(Critter critter) {
		if (TrackingMode.isUnavailable(critter) && partyCatches(critter) == 0) return true;
		return partyCatches(critter) >= required(critter);
	}

	/** True when the run cannot produce {@code critter} and none was caught. */
	public boolean isUnavailable(Critter critter) {
		return TrackingMode.isUnavailable(critter) && partyCatches(critter) == 0;
	}

	/** How many more of {@code critter} are known to be left, never negative. */
	public int remaining(Critter critter) {
		return Math.max(0, required(critter) - partyCatches(critter));
	}

	/**
	 * Species finished with, out of {@link Critters#total()} — the number the progress
	 * bars and completion alerts work from, so it follows the tracking mode.
	 */
	public int partyUnique() {
		return (int) Critters.all().stream().filter(this::isComplete).count();
	}

	public int partyUnique(SafariBiome biome) {
		return (int) Critters.inBiome(biome).stream().filter(this::isComplete).count();
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

	/**
	 * True when every species except {@code exception} has been caught by someone,
	 * whether or not the exception itself has been.
	 */
	public boolean allCaughtExcept(Critter exception) {
		return Critters.all().stream().allMatch(c -> c.equals(exception) || isComplete(c));
	}

	/** True once all 37 species have been caught by someone this run. */
	public boolean dexComplete() {
		return partyUnique() == Critters.total();
	}

	/** Species in {@code biome} the run is not finished with yet. */
	public List<Critter> missing(SafariBiome biome) {
		return Critters.inBiome(biome).stream().filter(c -> !isComplete(c)).toList();
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

	/**
	 * How long the run lasted, start to last event.
	 *
	 * <p>This is the span of a <em>finished</em> run, and deliberately not "how long ago
	 * it started": a run that ended twenty minutes ago should not still be counting, and
	 * a replayed run has no now to measure against. A live run wants
	 * {@link #elapsedMillis(long)} instead.
	 */
	public long durationMillis() {
		return Math.max(0, lastEventMillis - startedAtMillis);
	}

	/** How long the run has been going at {@code now} — the figure for a live timer. */
	public long elapsedMillis(long now) {
		return Math.max(0, now - startedAtMillis);
	}

	/** True when nothing has been recorded yet — used to suppress an empty HUD. */
	public boolean isEmpty() {
		return ownCatches.isEmpty() && sharedCatches.isEmpty() && attempts.isEmpty();
	}
}
