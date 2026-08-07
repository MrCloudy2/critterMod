package dev.rok.crittermod.session;

import dev.rok.crittermod.data.Critter;

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

	private TrackingMode() {
	}

	public static void setUniqueOnly(boolean value) {
		uniqueOnly = value;
	}

	public static boolean uniqueOnly() {
		return uniqueOnly;
	}

	/** How many of {@code critter} are needed before it counts as done. */
	public static int required(Critter critter) {
		if (uniqueOnly || !critter.hasQuota()) return 1;
		return critter.spawnQuota();
	}
}
