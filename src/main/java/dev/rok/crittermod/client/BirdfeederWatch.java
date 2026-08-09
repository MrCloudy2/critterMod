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
 * <p>The Birdfeeder attracts three species — Bluebird, Parakeet and, rarely, a pair of
 * Macaws. Only the Macaw is called out by default; the other two are behind a setting,
 * since they arrive often enough to be wallpaper.
 *
 * <p>Two ways of noticing a Macaw, because neither covers the other:
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
public final class BirdfeederWatch {

	private static final String NAME = "Macaw";
	/** Long enough that the two ways of noticing the same spawn cannot both fire. */
	private static final long COOLDOWN_MILLIS = 60_000;

	/**
	 * The Birdfeeder's own line, from its start, with the count and the species left
	 * open: {@code A Bluebird was attracted to the Birdfeeder!}, {@code Two Macaws were
	 * attracted to the Birdfeeder!}. Anchored, so a player quoting it cannot set it off.
	 */
	private static final java.util.regex.Pattern BIRDFEEDER = java.util.regex.Pattern.compile(
		"^\\S+ ([A-Za-z ]+?)s? (?:were|was) attracted to the Birdfeeder!$");

	private static boolean wasLoaded;
	private static long lastAnnounced;

	private BirdfeederWatch() {
	}

	/** Feeds one cleaned chat line. */
	public static void onChatMessage(String line) {
		java.util.regex.Matcher matcher = BIRDFEEDER.matcher(line);
		if (!matcher.matches()) return;

		dev.rok.crittermod.data.Critter bird =
			dev.rok.crittermod.data.Critters.byName(matcher.group(1).trim());
		// A wording this loose will match anything shaped like the sentence, so the
		// species has to be one this mod knows before it is announced as a spawn.
		if (bird == null) return;

		if (NAME.equals(bird.name())) {
			announce(null);
		} else if (ConfigManager.get().alerts.birdfeederAlert) {
			// The commoner birds get the same call in their own rarity's colour, so a
			// Bluebird cannot be mistaken for the thing you were waiting for.
			FullScreenAlert.show(bird.name().toUpperCase() + "!", "at the Birdfeeder",
				bird.biome().displayName(), bird.rarity().colour());
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
