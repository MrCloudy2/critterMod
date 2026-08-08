package dev.rok.crittermod.client;

import dev.rok.crittermod.data.Critter;
import dev.rok.crittermod.session.SafariSession;
import dev.rok.crittermod.session.SessionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts how many of each species actually spawned, by watching the world.
 *
 * <p>Most species spawn a randomised number per run, so no fixed table can say how
 * many there are to catch. Hypixel labels each critter with its species name, so they
 * can simply be counted — the sweep lives in {@link CritterEntities} and this tallies
 * what it found, by species.
 *
 * <p>This reports only what is loaded <em>right now</em>. It is not a total and cannot
 * be: the client never sees the far side of the map, and a partymate catching something
 * out of render distance is never observed. Counting distinct entity ids over time does
 * not fix that and adds its own error — a critter that escapes a capsule returns as a
 * new entity, so such a total only ever climbs.
 *
 * <p>What it is good for is "there is one of these next to you that nobody has caught".
 */
public final class CritterSpotter {

	private CritterSpotter() {
	}

	public static void tick() {
		if (!ConfigManager.get().display.countSpawns) return;

		SafariSession session = SessionManager.current();
		if (session == null || !SafariLocation.inSafari()) return;

		Map<Critter, Integer> present = new HashMap<>();
		for (CritterEntities.Sighting sighting : CritterEntities.all()) {
			present.merge(sighting.critter(), 1, Integer::sum);
		}
		// Replaced wholesale rather than merged, so anything caught or despawned since
		// the last scan simply drops out.
		session.setNearby(present);
	}
}
