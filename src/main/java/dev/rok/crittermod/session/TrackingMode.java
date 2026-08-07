package dev.rok.crittermod.session;

import dev.rok.crittermod.data.Critter;

import java.util.HashSet;
import java.util.Set;

/**
 * Whether a species counts as done at one catch, or only once every spawn has been
 * taken.
 *
 * <p>By default the target is every shard the run can give: a species with a known
 * spawn quota stays outstanding until all of them are caught, so a run that took one
 * of the three Gemzies is not finished with Gemzies. Turning on unique-only reverts to
 * counting a species as done at the first catch.
 *
 * <p>Kept here rather than read from the config directly so {@link SafariSession} stays
 * free of Minecraft and client imports and can still be replayed against logs.
 */
public final class TrackingMode {

	private static boolean uniqueOnly;
	private static boolean countSpawns = true;
	private static final Set<Critter> unavailable = new HashSet<>();

	private TrackingMode() {
	}

	public static void setUniqueOnly(boolean value) {
		uniqueOnly = value;
	}

	public static boolean uniqueOnly() {
		return uniqueOnly;
	}

	public static void setCountSpawns(boolean value) {
		countSpawns = value;
	}

	/** Whether spawns seen in the world are used as the target for unquotaed species. */
	public static boolean countSpawns() {
		return countSpawns;
	}

	/**
	 * Marks the species this run can no longer produce, so they stop being reported as
	 * outstanding. Snoozle comes from the breakable Cavern walls: once every wall is
	 * confirmed broken without one appearing, none can.
	 */
	public static void setUnavailable(Set<Critter> species) {
		unavailable.clear();
		unavailable.addAll(species);
	}

	/** True when the run cannot produce {@code critter} any more. */
	public static boolean isUnavailable(Critter critter) {
		return unavailable.contains(critter);
	}

	/** How many of {@code critter} are needed before it counts as done. */
	public static int required(Critter critter) {
		if (uniqueOnly || !critter.hasQuota()) return 1;
		return critter.spawnQuota();
	}
}
