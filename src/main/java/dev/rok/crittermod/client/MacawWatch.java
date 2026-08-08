package dev.rok.crittermod.client;

import dev.rok.crittermod.data.SafariBiome;
import net.minecraft.core.BlockPos;

/**
 * Calls out a Macaw the moment one turns up.
 *
 * <p>The Macaw is the one species a run is not guaranteed to produce at all — it is
 * pure RNG, which is why "Everything except Macaw done!" is usually the real finish
 * line. So a spawn is worth telling the party about: whoever is in another biome wants
 * to know there is something to come back for.
 *
 * <p>Two ways of noticing, because neither covers the other:
 *
 * <ul>
 * <li>the Birdfeeder announces it — {@code Two Macaws were attracted to the Birdfeeder!}
 *     — which arrives whether or not you are anywhere near;
 * <li>a Macaw label appearing in the world, which covers one that turns up without the
 *     Birdfeeder line and comes with coordinates to send.
 * </ul>
 *
 * <p>A cooldown covers the overlap, so a Birdfeeder spawn you then walk up to is
 * announced once rather than twice.
 */
public final class MacawWatch {

	private static final String NAME = "Macaw";
	/** Long enough that the two ways of noticing the same spawn cannot both fire. */
	private static final long COOLDOWN_MILLIS = 60_000;

	private static boolean wasLoaded;
	private static long lastAnnounced;

	private MacawWatch() {
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		// "Two Macaws were attracted to the Birdfeeder!" is the wording seen; matched on
		// the two halves so a singular or a different count still lands.
		if (line.contains(NAME) && line.contains("attracted to the Birdfeeder")) {
			announce(null);
		}
	}

	public static void tick() {
		BlockPos where = null;
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			if (NAME.equals(sighting.critter().name())) {
				where = sighting.body().blockPosition();
				break;
			}
		}

		// Edge-triggered: one announcement when it appears, not one per scan while it is
		// standing there.
		boolean loaded = where != null;
		if (loaded && !wasLoaded) announce(where);
		wasLoaded = loaded;
	}

	private static void announce(BlockPos where) {
		long now = System.currentTimeMillis();
		if (now - lastAnnounced < COOLDOWN_MILLIS) return;
		lastAnnounced = now;

		EncounterAlerts.onMacawSpawn(where == null ? null : "%s %d %d %d".formatted(
			SafariBiome.FOREST.displayName(), where.getX(), where.getY(), where.getZ()));
	}

	/** A Macaw from the last run says nothing about this one. */
	public static void reset() {
		wasLoaded = false;
		lastAnnounced = 0;
	}
}
